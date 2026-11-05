"""Static checks for the CI-002 Test deploy workflow and the backend image.

Spec: docs/specs/CI-002-deploy-test.md. Plan: docs/plans/CI-002.md.
Requirement IDs: TBD (spec open question 1). Runs without Docker and without AWS.
"""

import re
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/deploy-test.yml"
DOCKERFILE = ROOT / "backend/Dockerfile"
DOCKERIGNORE = ROOT / "backend/.dockerignore"
HEX64 = re.compile(r"^[0-9a-f]{64}$")


def _needs(job):
    value = job.get("needs", [])
    return [value] if isinstance(value, str) else list(value)


class DeployTestWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")
        cls.doc = yaml.safe_load(cls.text)
        # PyYAML reads the bare key `on` as the boolean True.
        cls.on = cls.doc.get(True, cls.doc.get("on"))
        cls.jobs = cls.doc["jobs"]

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

    def test_images_are_deployed_by_digest_never_by_latest(self):
        self.assertNotIn(":latest", self.text)
        self.assertIn("needs.build.outputs.image_digest", self.text)

    def test_caddy_digest_is_pinned_as_64_hex(self):
        self.assertRegex(str(self.doc["env"]["CADDY_IMAGE_DIGEST"]), HEX64)

    def test_no_live_environment_is_referenced(self):
        self.assertIsNone(re.search(r"\blive\b", self.text, re.IGNORECASE))
        for name, job in self.jobs.items():
            environment = job.get("environment")
            if environment:
                env_name = environment if isinstance(environment, str) else environment.get("name")
                with self.subTest(job=name):
                    self.assertEqual(env_name, "test")

    def test_no_static_aws_keys_or_ssh(self):
        lowered = self.text.lower()
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

    def test_no_shell_tracing_in_workflow(self):
        self.assertNotIn("set -x", self.text)
        self.assertNotIn("xtrace", self.text)

    def test_every_action_is_pinned_to_a_version(self):
        for name, job in self.jobs.items():
            for step in job["steps"]:
                if "uses" in step:
                    with self.subTest(job=name, uses=step["uses"]):
                        self.assertIn("@", step["uses"])
                        self.assertNotIn("@main", step["uses"])
                        self.assertNotIn("@master", step["uses"])

    def test_ssm_deploy_targets_only_the_test_instance_variable(self):
        self.assertIn("aws ssm send-command", self.text)
        self.assertIn("AWS-RunShellScript", self.text)
        self.assertIn("vars.TEST_INSTANCE_ID", self.text)
        self.assertIn("infra/deploy/deploy-test.sh", self.text)

    def test_frontend_cache_rules_and_invalidation(self):
        self.assertIn("index.html", self.text)
        self.assertIn("no-cache", self.text)
        self.assertIn("max-age=31536000", self.text)
        self.assertIn("immutable", self.text)
        self.assertIn("create-invalidation", self.text)
        self.assertIn("'/*'", self.text)
        self.assertIn("VITE_API_BASE_URL", self.text)
        self.assertIn("vars.TEST_BUCKET", self.text)

    def test_smoke_checks_frontend_and_api_health(self):
        self.assertIn("actuator/health", self.text)
        self.assertIn("vars.TEST_FRONTEND_URL", self.text)
        self.assertIn("vars.TEST_API_HOSTNAME", self.text)


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
