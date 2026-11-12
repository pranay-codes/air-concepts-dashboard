"""Static checks for the Test deploy workflow, the shared actions it uses, and the backend image.

Spec: docs/specs/CI-002-deploy-test.md and docs/specs/INF-006-live-environment.md.
Plans: docs/plans/CI-002.md, docs/plans/INF-006.md.
Requirement IDs: TBD (CI-002 spec open question 1); INF-006 for the shared-action and isolation checks.
Runs without Docker and without AWS.
"""

import re
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/deploy-test.yml"
ACTIONS = ROOT / ".github/actions"
DOCKERFILE = ROOT / "backend/Dockerfile"
DOCKERIGNORE = ROOT / "backend/.dockerignore"
CADDY_DIGEST = ROOT / "infra/deploy/caddy-image.digest"
HEX64 = re.compile(r"^[0-9a-f]{64}$")


def _needs(job):
    value = job.get("needs", [])
    return [value] if isinstance(value, str) else list(value)


def _action_text(name):
    return (ACTIONS / name / "action.yml").read_text(encoding="utf-8")


def _all_action_docs():
    return {path.parent.name: yaml.safe_load(path.read_text(encoding="utf-8")) for path in ACTIONS.glob("*/action.yml")}


def _uses_of(steps):
    return [step["uses"] for step in steps if "uses" in step]


class DeployTestWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")
        cls.doc = yaml.safe_load(cls.text)
        # PyYAML reads the bare key `on` as the boolean True.
        cls.on = cls.doc.get(True, cls.doc.get("on"))
        cls.jobs = cls.doc["jobs"]
        cls.actions_text = "\n".join(
            path.read_text(encoding="utf-8") for path in sorted(ACTIONS.glob("*/action.yml")))
        cls.everything = cls.text + "\n" + cls.actions_text

    def test_triggers_only_on_main_push_and_manual_run(self):
        self.assertEqual(self.on["push"]["branches"], ["main"])
        self.assertIn("workflow_dispatch", self.on)
        self.assertNotIn("pull_request", self.on)
        self.assertNotIn("schedule", self.on)

    def test_permissions_are_read_only_except_oidc_token(self):
        self.assertEqual(self.doc["permissions"], {"contents": "read"})
        for name, job in self.jobs.items():
            for key, value in job.get("permissions", {}).items():
                with self.subTest(job=name, key=key):
                    if key == "id-token":
                        self.assertEqual(value, "write")
                    else:
                        self.assertEqual((key, value), ("contents", "read"))

    def test_one_deploy_at_a_time_and_no_cancellation(self):
        self.assertEqual(self.doc["concurrency"]["group"], "deploy-test")
        self.assertIs(self.doc["concurrency"]["cancel-in-progress"], False)

    def test_deploy_only_runs_from_main_even_when_started_by_hand(self):
        # workflow_dispatch can start the workflow on any branch; the guard keeps branches out of Test.
        self.assertEqual(self.jobs["build"].get("if"), "github.ref == 'refs/heads/main'")

    def test_job_order_is_build_then_backend_then_frontend_then_smoke(self):
        self.assertEqual(set(self.jobs), {"build", "deploy-backend", "deploy-frontend", "smoke"})
        self.assertEqual(_needs(self.jobs["build"]), [])
        self.assertEqual(_needs(self.jobs["deploy-backend"]), ["build"])
        self.assertEqual(_needs(self.jobs["deploy-frontend"]), ["deploy-backend"])
        self.assertEqual(_needs(self.jobs["smoke"]), ["deploy-frontend"])

    def test_backend_image_is_built_for_arm64_on_an_arm64_runner(self):
        self.assertEqual(self.jobs["build"]["runs-on"], "ubuntu-24.04-arm")
        self.assertIn("platforms: linux/arm64", self.text)

    def test_base_image_manifests_are_checked_for_arm64_before_push(self):
        self.assertIn("docker buildx imagetools inspect", self.text)
        self.assertIn("linux/arm64", self.text)
        self.assertLess(
            self.text.index("docker buildx imagetools inspect"),
            self.text.index("docker/build-push-action"),
        )

    def test_image_tag_is_unique_per_run_attempt(self):
        # ECR tags are immutable, and a re-run or a manual run on the same commit must not reuse a tag.
        tag_lines = [line for line in self.text.splitlines() if "tags:" in line and "ECR_REPOSITORY_URI" in line]
        self.assertEqual(len(tag_lines), 1)
        self.assertIn("github.run_id", tag_lines[0])
        self.assertIn("github.run_attempt", tag_lines[0])

    def test_the_build_job_prints_the_commit_and_image_tag_for_the_release_notes(self):
        # The release notes of a release tag must carry the commit and image tag this deploy pushed.
        steps = self.jobs["build"]["steps"]
        printers = [s for s in steps if "GITHUB_STEP_SUMMARY" in s.get("run", "")]
        self.assertEqual(len(printers), 1)
        printer = printers[0]
        self.assertEqual(printer["env"]["IMAGE_TAG"], "${{ github.sha }}-${{ github.run_id }}-${{ github.run_attempt }}")
        self.assertIn('echo "Commit: $GITHUB_SHA"', printer["run"])
        self.assertIn('echo "Image: $IMAGE_TAG"', printer["run"])
        self.assertIn("docs/release-notes-template.md", printer["run"])
        self.assertIn("${{ github.sha }}-${{ github.run_id }}-${{ github.run_attempt }}", self.text)
        self.assertLess(self.text.index("docker/build-push-action"), self.text.index(printer["name"]))

    def test_digest_prefix_is_removed_before_the_host_script(self):
        # docker/build-push-action outputs "sha256:<hex>"; the host script and Compose take bare hex.
        action = _action_text("deploy-backend")
        self.assertIn('digest="${NEW_BACKEND_DIGEST#sha256:}"', action)
        self.assertIn("NEW_BACKEND_DIGEST=$digest", action)
        self.assertNotIn("NEW_BACKEND_DIGEST=$NEW_BACKEND_DIGEST", action)

    def test_images_are_deployed_by_digest_never_by_latest(self):
        self.assertNotIn(":latest", self.everything)
        self.assertIn("needs.build.outputs.image_digest", self.text)

    def test_caddy_digest_is_pinned_as_64_hex_in_one_shared_file(self):
        self.assertRegex(CADDY_DIGEST.read_text(encoding="utf-8").strip(), HEX64)
        self.assertNotRegex(self.text, r"CADDY_IMAGE_DIGEST:\s*[0-9a-f]{64}")

    def test_workflow_can_only_target_test(self):
        # INF-006 replaces the old "no live word" check: Test may sit beside a Live workflow, but it must
        # never reach Live configuration, variables, roles, parameters or a Live GitHub environment.
        self.assertNotRegex(self.text, r"(?i)\blive\b")
        self.assertNotIn("LIVE_", self.text)
        self.assertNotIn("/air-concepts/live", self.everything)
        setups = [step for job in self.jobs.values() for step in job["steps"]
                  if step.get("uses") == "./.github/actions/setup-environment"]
        self.assertGreaterEqual(len(setups), 4)
        for step in setups:
            self.assertEqual(step["with"]["environment"], "test")
        for name, job in self.jobs.items():
            environment = job.get("environment")
            if environment:
                env_name = environment if isinstance(environment, str) else environment.get("name")
                with self.subTest(job=name):
                    self.assertEqual(env_name, "test")

    def test_every_job_sets_up_its_environment_before_using_other_shared_actions(self):
        for name, job in self.jobs.items():
            uses = _uses_of(job["steps"])
            with self.subTest(job=name):
                self.assertIn("./.github/actions/setup-environment", uses)
                for other in uses:
                    if other.startswith("./.github/actions/") and other != "./.github/actions/setup-environment":
                        self.assertLess(uses.index("./.github/actions/setup-environment"), uses.index(other))

    def test_aws_jobs_can_request_an_oidc_token_and_smoke_cannot(self):
        for name in ("build", "deploy-backend", "deploy-frontend"):
            with self.subTest(job=name):
                self.assertEqual(self.jobs[name]["permissions"]["id-token"], "write")
        self.assertNotIn("id-token", self.jobs["smoke"].get("permissions", {}))
        smoke_setup = [s for s in self.jobs["smoke"]["steps"] if s.get("uses") == "./.github/actions/setup-environment"]
        self.assertEqual(smoke_setup[0]["with"]["aws"], "false")

    def test_no_static_aws_keys_or_ssh(self):
        lowered = self.everything.lower()
        for forbidden in (
            "aws_access_key_id",
            "aws_secret_access_key",
            "secrets.aws",
            "ssh-agent",
            "ssh_private_key",
            "scp ",
            "ssh ",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, lowered)

    def test_no_shell_tracing_in_workflow_or_actions(self):
        self.assertNotIn("set -x", self.everything)
        self.assertNotIn("xtrace", self.everything)

    def test_every_external_action_is_pinned_to_a_version(self):
        steps = [step for job in self.jobs.values() for step in job["steps"]]
        for doc in _all_action_docs().values():
            steps.extend(doc["runs"]["steps"])
        for step in steps:
            if "uses" in step and not step["uses"].startswith("./"):
                with self.subTest(uses=step["uses"]):
                    self.assertIn("@", step["uses"])
                    self.assertNotIn("@main", step["uses"])
                    self.assertNotIn("@master", step["uses"])

    def test_ssm_deploy_targets_only_the_configured_instance(self):
        action = _action_text("deploy-backend")
        self.assertIn("aws ssm send-command", action)
        self.assertIn("AWS-RunShellScript", action)
        self.assertIn('--instance-ids "$INSTANCE_ID"', action)
        self.assertIn("infra/deploy/deploy-backend.sh", action)
        self.assertNotIn("vars.", action)

    def test_frontend_cache_rules_and_invalidation(self):
        publish = _action_text("publish-frontend")
        self.assertIn("index.html", publish)
        self.assertIn("no-cache", publish)
        self.assertIn("max-age=31536000", publish)
        self.assertIn("immutable", publish)
        self.assertIn("create-invalidation", publish)
        self.assertIn("'/*'", publish)
        self.assertIn("FRONTEND_BUCKET", publish)
        self.assertIn("VITE_API_BASE_URL", _action_text("build-frontend"))

    def test_frontend_build_stops_when_a_public_setting_is_missing(self):
        # Missing parameters used to build a blank bundle that failed at page load, with the run still green.
        steps = yaml.safe_load(_action_text("build-frontend"))["runs"]["steps"]
        names = [step.get("name", "") for step in steps]
        read_index = names.index("Read the public frontend settings from Parameter Store")
        build_index = names.index("Build the frontend")
        self.assertLess(read_index, build_index)
        script = steps[read_index]["run"]
        for key in ("VITE_API_BASE_URL", "VITE_SUPABASE_URL", "VITE_SUPABASE_PUBLISHABLE_KEY"):
            with self.subTest(key=key):
                self.assertIn(key, script)
        self.assertIn("Missing frontend setting", script)
        self.assertIn("exit 1", script)

    def test_smoke_checks_frontend_and_api_health(self):
        smoke = _action_text("smoke")
        self.assertIn("actuator/health", smoke)
        self.assertIn("FRONTEND_URL", smoke)
        self.assertIn("API_HOSTNAME", smoke)

    def test_smoke_can_prove_an_unauthenticated_call_is_rejected(self):
        smoke = _action_text("smoke")
        self.assertIn("/api/v1/me", smoke)
        self.assertIn('"401"', smoke)

    def test_environment_values_never_come_from_github_variables(self):
        # INF-006: one file per environment, reviewed in a PR, replaces per-environment GitHub variables.
        self.assertNotIn("vars.", self.everything)


class SharedActionTests(unittest.TestCase):
    """Checks that make the shared actions safe to use for both Test and Live."""

    @classmethod
    def setUpClass(cls):
        cls.docs = _all_action_docs()

    def test_every_action_is_composite_with_bash_steps(self):
        self.assertEqual(
            set(self.docs),
            {"setup-environment", "deploy-backend", "build-frontend", "publish-frontend", "smoke",
             "install-supabase-cli", "supabase-migrate"},
        )
        for name, doc in self.docs.items():
            with self.subTest(action=name):
                self.assertEqual(doc["runs"]["using"], "composite")
                for step in doc["runs"]["steps"]:
                    if "run" in step:
                        self.assertEqual(step["shell"], "bash")

    def test_setup_environment_checks_account_and_role_identity(self):
        text = _action_text("setup-environment")
        self.assertIn("aws sts get-caller-identity", text)
        self.assertIn("AWS_ACCOUNT_ID", text)
        self.assertIn("assumed-role/${role_name}", text)
        self.assertIn("exit 1", text)

    def test_setup_environment_takes_the_role_from_configuration(self):
        text = _action_text("setup-environment")
        self.assertIn("role-to-assume: ${{ env.DEPLOY_ROLE_ARN }}", text)
        self.assertIn("environment_config.py export", text)

    def test_supabase_migrate_never_resets_repairs_or_seeds(self):
        text = _action_text("supabase-migrate")
        for forbidden in ("db reset", "migration repair", "seed.sql", "--include-seed", "db push --remote --yes --include-all"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, text)

    def test_supabase_migrate_refuses_a_ref_that_differs_from_the_configured_one(self):
        text = _action_text("supabase-migrate")
        self.assertIn('"$PROJECT_REF" != "$EXPECTED_PROJECT_REF"', text)

    def test_supabase_cli_version_is_pinned_in_one_place(self):
        text = _action_text("install-supabase-cli")
        self.assertRegex(text, r'default: "\d+\.\d+\.\d+"')
        for workflow in (ROOT / ".github/workflows").glob("*.yml"):
            with self.subTest(workflow=workflow.name):
                self.assertNotIn("SUPABASE_CLI_VERSION:", workflow.read_text(encoding="utf-8"))


class BackendImageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = DOCKERFILE.read_text(encoding="utf-8")

    def test_base_image_is_pinned_by_digest(self):
        self.assertRegex(
            self.text,
            r"(?m)^FROM eclipse-temurin:25-jre-noble@sha256:[0-9a-f]{64}$",
        )

    def test_runs_as_non_root_uid_10001(self):
        self.assertRegex(self.text, r"(?m)^USER 10001:10001$")

    def test_exposes_port_8080_and_uses_no_latest_tag(self):
        self.assertRegex(self.text, r"(?m)^EXPOSE 8080$")
        self.assertNotIn("latest", self.text)

    def test_copies_only_the_packaged_jar(self):
        self.assertRegex(self.text, r"(?m)^COPY target/\*\.jar ")

    def test_no_secrets_in_image_metadata(self):
        for line in self.text.splitlines():
            if line.startswith("ENV ") or line.startswith("ARG "):
                with self.subTest(line=line):
                    self.assertIsNone(re.search(r"PASSWORD|SECRET|TOKEN|KEY", line, re.IGNORECASE))

    def test_dockerignore_excludes_env_files_and_git(self):
        lines = {line.strip() for line in DOCKERIGNORE.read_text(encoding="utf-8").splitlines()}
        self.assertIn(".env", lines)
        self.assertIn(".git", lines)

    def test_dockerignore_does_not_exclude_the_jar_directory(self):
        lines = {line.strip().rstrip("/") for line in DOCKERIGNORE.read_text(encoding="utf-8").splitlines()}
        self.assertNotIn("target", lines)


if __name__ == "__main__":
    unittest.main()
