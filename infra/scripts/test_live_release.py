"""Static checks for the Live release workflow, the Live deployer role and the Live stack policy.

Spec: docs/specs/INF-006-live-environment.md. Plan: docs/plans/INF-006.md. Requirement: INF-006.
Runs without Docker, AWS or GitHub.
"""

import json
import re
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/deploy-live.yml"
ROLE = ROOT / "infra/cloudformation/deployer-role.json"
STACK_POLICY = ROOT / "infra/cloudformation/live-stack-policy.json"
ACTIONS = ROOT / ".github/actions"


def _step_names(job):
    return [step.get("name", step.get("uses", "")) for step in job["steps"]]


def _index(job, fragment):
    for position, step in enumerate(job["steps"]):
        label = step.get("name", "") + " " + step.get("uses", "") + " " + step.get("run", "")
        if fragment in label:
            return position
    raise AssertionError(f"no step mentions {fragment!r}")


class LiveWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")
        cls.doc = yaml.safe_load(cls.text)
        cls.on = cls.doc.get(True, cls.doc.get("on"))
        cls.jobs = cls.doc["jobs"]
        cls.release = cls.jobs["release"]
        cls.rollback = cls.jobs["rollback"]
        cls.actions_text = "\n".join(p.read_text(encoding="utf-8") for p in ACTIONS.glob("*/action.yml"))

    # --- Trigger and gates ---------------------------------------------------------------------

    def test_it_can_only_be_started_by_hand(self):
        self.assertEqual(set(self.on), {"workflow_dispatch"})

    def test_inputs_force_a_deliberate_release(self):
        inputs = self.on["workflow_dispatch"]["inputs"]
        self.assertEqual(set(inputs), {"mode", "release_sha", "image_digest", "confirm", "backup_confirmed", "destructive_ack"})
        self.assertEqual(inputs["mode"]["options"], ["release", "rollback"])
        self.assertIs(inputs["backup_confirmed"]["default"], False)
        self.assertIs(inputs["destructive_ack"]["default"], False)
        self.assertEqual(inputs["confirm"]["default"], "")

    def test_one_release_at_a_time_and_never_cancelled(self):
        self.assertEqual(self.doc["concurrency"]["group"], "deploy-live")
        self.assertIs(self.doc["concurrency"]["cancel-in-progress"], False)

    def test_default_permissions_are_read_only(self):
        self.assertEqual(self.doc["permissions"], {"contents": "read"})

    def test_guard_refuses_other_branches_missing_confirmation_and_untested_releases(self):
        script = self.jobs["guard"]["steps"][1]["run"]
        self.assertIn('"$GITHUB_REF" = "refs/heads/main"', script)
        self.assertIn('"$CONFIRM" = "DEPLOY LIVE"', script)
        self.assertIn("^[0-9a-f]{40}$", script)
        self.assertIn("^sha256:[0-9a-f]{64}$", script)
        self.assertIn('"$BACKUP_CONFIRMED" = "true"', script)
        self.assertIn("git merge-base --is-ancestor", script)
        for workflow in ("deploy-test.yml", "supabase-migrations.yml"):
            self.assertIn(workflow, script)
        self.assertIn("status=success", script)

    def test_untrusted_inputs_reach_scripts_only_through_environment_variables(self):
        for name, job in self.jobs.items():
            for step in job["steps"]:
                run = step.get("run", "")
                with self.subTest(job=name, step=step.get("name", "")):
                    self.assertNotIn("${{ inputs.", run)
                    self.assertNotIn("${{ github.event.inputs", run)

    def test_both_deploy_jobs_need_the_guard_and_the_live_github_environment(self):
        for job in (self.release, self.rollback):
            self.assertEqual(job["needs"], "guard")
            self.assertEqual(job["environment"], "live")
            self.assertEqual(job["permissions"]["id-token"], "write")
        self.assertEqual(self.release["if"], "inputs.mode == 'release'")
        self.assertEqual(self.rollback["if"], "inputs.mode == 'rollback'")
        self.assertNotIn("id-token", self.jobs["guard"]["permissions"])

    # --- Isolation -----------------------------------------------------------------------------

    def test_it_only_ever_sets_up_the_live_environment_completely(self):
        setups = [step for job in (self.release, self.rollback) for step in job["steps"]
                  if step.get("uses") == "./.github/actions/setup-environment"]
        self.assertEqual(len(setups), 2)
        for step in setups:
            self.assertEqual(step["with"]["environment"], "live")
            self.assertEqual(step["with"]["require-complete"], "true")

    def test_it_never_uses_test_credentials_variables_or_parameters(self):
        self.assertNotIn("TEST_", self.text)
        self.assertNotIn("/air-concepts/test", self.text)
        self.assertNotIn("SUPABASE_TEST", self.text)
        self.assertNotIn("environment: test", self.text)
        self.assertNotIn("vars.", self.text)

    def test_the_only_use_of_test_is_reading_the_image_it_built_and_its_run_history(self):
        # Test may appear in comments, step names, the two Test-gate workflow files, and the lines that
        # read the Test image repository to promote it. Nothing else may mention it.
        allowed = (
            r"^\s*#",
            r"name: Confirm Test built this image",
            r"deploy-test\.yml supabase-migrations\.yml",
            r"no successful \$workflow run",
            r"passed the Test gates",
            r"test_uri=\"\$\(python3 infra/scripts/environment_config\.py get test ecrRepositoryUri\)\"",
            r"test_repo=",
            r"--repository-name \"\$test_repo\"",
            r"in \$test_repo is not tagged",
            r"\$\{test_uri\}@",
            r"description: .*(Test|tested)",
            r"deploy it to Test first",
        )
        for number, line in enumerate(self.text.splitlines(), start=1):
            if re.search(r"(?i)test(ed)?|test_(uri|repo)", line):
                with self.subTest(line=number, text=line.strip()):
                    self.assertTrue(any(re.search(pattern, line) for pattern in allowed), line)

    def test_the_live_instance_is_checked_by_tag_before_anything_is_changed(self):
        tag_check = _index(self.release, "Confirm the target is the Live instance")
        for later in ("Promote the tested image", "Apply the migrations", "deploy-backend"):
            self.assertLess(tag_check, _index(self.release, later))
        self.assertIn("environment=$tag, expected live", self.release["steps"][tag_check]["run"])

    # --- The backend is promoted, never rebuilt ------------------------------------------------

    def test_the_backend_is_never_built_in_this_workflow(self):
        for forbidden in ("build-push-action", "mvnw", "setup-java", "docker push"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.text)
        # `docker buildx imagetools create` copies an existing image; only an actual build is forbidden.
        self.assertIsNone(re.search(r"docker\s+(buildx\s+)?build\b", self.text))

    def test_promotion_copies_the_exact_digest_and_verifies_it(self):
        script = self.release["steps"][_index(self.release, "Promote the tested image")]["run"]
        self.assertIn("docker buildx imagetools create", script)
        self.assertIn('"${test_uri}@${IMAGE_DIGEST}"', script)
        self.assertIn('"$copied" != "$IMAGE_DIGEST"', script)
        self.assertIn("exit 1", script)

    def test_the_image_must_be_one_test_built_for_the_release_commit(self):
        script = self.release["steps"][_index(self.release, "Confirm Test built this image")]["run"]
        self.assertIn("aws ecr describe-images", script)
        self.assertIn('"${RELEASE_SHA}-"*', script)
        self.assertIn("exit 1", script)

    def test_images_are_referenced_by_digest_never_latest(self):
        self.assertNotIn(":latest", self.text)

    # --- Order of a release --------------------------------------------------------------------

    def test_release_order(self):
        order = [
            "Confirm the target is the Live instance",
            "Confirm Test built this image",
            "Promote the tested image",
            "List the migrations this release would apply",
            "Stop on destructive migration statements",
            "Apply the migrations",
            "Mark this release as in flight",
            "deploy-backend",
            "build-frontend",
            "upload-artifact",
            "publish-frontend",
            "smoke",
            "Record the release",
        ]
        positions = [_index(self.release, fragment) for fragment in order]
        self.assertEqual(positions, sorted(positions))
        self.assertEqual(len(set(positions)), len(positions))

    def test_the_release_runs_from_the_release_commit(self):
        checkout = self.release["steps"][0]
        self.assertEqual(checkout["with"]["ref"], "${{ inputs.release_sha }}")
        self.assertIs(checkout["with"]["persist-credentials"], False)

    def test_destructive_migrations_need_a_human_acknowledgement(self):
        step = self.release["steps"][_index(self.release, "Stop on destructive migration statements")]
        self.assertEqual(step["env"]["DESTRUCTIVE_ACK"], "${{ inputs.destructive_ack }}")
        self.assertIn("--acknowledge-destructive", step["run"])
        self.assertIn("check_migrations.py", step["run"])
        self.assertIn("migration-dry-run.txt", step["run"])

    def test_migrations_use_live_secrets_and_the_configured_project(self):
        for step in self.release["steps"]:
            if step.get("uses") == "./.github/actions/supabase-migrate":
                with self.subTest(mode=step["with"]["mode"]):
                    self.assertEqual(step["with"]["project-ref"], "${{ env.SUPABASE_PROJECT_REF }}")
                    self.assertEqual(step["with"]["expected-project-ref"], "${{ env.SUPABASE_PROJECT_REF }}")
                    self.assertEqual(step["with"]["access-token"], "${{ secrets.SUPABASE_LIVE_ACCESS_TOKEN }}")
                    self.assertEqual(step["with"]["db-password"], "${{ secrets.SUPABASE_LIVE_DB_PASSWORD }}")

    def test_the_dry_run_comes_before_the_real_migration(self):
        modes = [step["with"]["mode"] for step in self.release["steps"] if step.get("uses") == "./.github/actions/supabase-migrate"]
        self.assertEqual(modes, ["dry-run", "push"])

    def test_the_smoke_test_proves_unauthenticated_calls_are_rejected(self):
        for job in (self.release, self.rollback):
            smoke = [s for s in job["steps"] if s.get("uses") == "./.github/actions/smoke"][0]
            self.assertEqual(smoke["with"]["expect-unauthenticated-rejected"], "true")

    def test_the_frontend_is_kept_for_ninety_days_so_a_rollback_can_restore_it(self):
        step = self.release["steps"][_index(self.release, "upload-artifact")]
        self.assertEqual(step["with"]["name"], "frontend-dist")
        self.assertEqual(step["with"]["retention-days"], 90)

    def test_the_release_record_is_only_finalised_after_the_smoke_test(self):
        record = self.release["steps"][_index(self.release, "Record the release")]["run"]
        self.assertIn("release/current", record)
        self.assertIn("release/previous", record)
        in_flight = self.release["steps"][_index(self.release, "Mark this release as in flight")]["run"]
        self.assertIn("release/attempt", in_flight)
        self.assertNotIn("release/current", in_flight)

    # --- Rollback ------------------------------------------------------------------------------

    def test_rollback_never_migrates_promotes_or_touches_the_database(self):
        text = yaml.dump(self.rollback)
        for forbidden in ("supabase", "imagetools", "migration", "db push", "db reset", "ecr put-image"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, text)

    def test_rollback_restores_the_matching_backend_and_frontend(self):
        text = yaml.dump(self.rollback)
        self.assertIn("deploy-backend", text)
        self.assertIn("download-artifact", text)
        self.assertIn("ROLLBACK_RUN_ID", text)
        self.assertIn("publish-frontend", text)
        positions = [_index(self.rollback, f) for f in ("deploy-backend", "download-artifact", "publish-frontend", "smoke")]
        self.assertEqual(positions, sorted(positions))

    def test_rollback_checks_out_the_release_it_restores(self):
        checkouts = [s for s in self.rollback["steps"] if str(s.get("uses", "")).startswith("actions/checkout")]
        self.assertEqual(checkouts[-1]["with"]["ref"], "${{ env.ROLLBACK_SHA }}")

    def test_rollback_refuses_when_there_is_nothing_to_restore(self):
        script = self.rollback["steps"][_index(self.rollback, "Choose the last known-good release")]["run"]
        self.assertIn("no earlier release recorded", script)
        self.assertIn("exit 1", script)

    def test_rollback_reads_the_record_of_an_unfinished_release_as_current(self):
        script = self.rollback["steps"][_index(self.rollback, "Choose the last known-good release")]["run"]
        self.assertIn('if [ "$attempt" != "none" ]', script)
        self.assertIn('target="$current"', script)
        self.assertIn('target="$previous"', script)

    # --- Hygiene -------------------------------------------------------------------------------

    def test_no_static_keys_ssh_or_tracing(self):
        lowered = (self.text + self.actions_text).lower()
        for forbidden in ("aws_access_key_id", "aws_secret_access_key", "secrets.aws", "ssh-agent", "ssh_private_key",
                          "scp ", "ssh ", "set -x", "xtrace", "--no-verify"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, lowered)

    def test_database_resets_and_repairs_never_appear(self):
        for forbidden in ("db reset", "migration repair", "db push --include-all", "--include-seed", "seed.sql"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.text + self.actions_text)

    def test_every_external_action_is_pinned_to_a_version(self):
        for job in self.jobs.values():
            for step in job["steps"]:
                uses = step.get("uses", "")
                if uses and not uses.startswith("./"):
                    with self.subTest(uses=uses):
                        self.assertRegex(uses, r"@v\d")


class LiveDeployRoleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = json.loads(ROLE.read_text(encoding="utf-8"))
        cls.role = cls.template["Resources"]["AirConceptsLiveDeployRole"]["Properties"]
        cls.statements = {s["Sid"]: s for s in cls.role["Policies"][0]["PolicyDocument"]["Statement"]}
        cls.text = ROLE.read_text(encoding="utf-8")

    def test_the_role_is_named_in_the_live_configuration(self):
        live = json.loads((ROOT / "infra/environments/live.json").read_text(encoding="utf-8"))
        self.assertTrue(live["deployRoleArn"].endswith("/" + self.role["RoleName"]))

    def test_trust_requires_the_repository_environment_and_workflow_on_main(self):
        statement = self.role["AssumeRolePolicyDocument"]["Statement"]
        self.assertEqual(len(statement), 1)
        trust = statement[0]
        self.assertEqual(trust["Action"], "sts:AssumeRoleWithWebIdentity")
        condition = trust["Condition"]["StringEquals"]
        self.assertEqual(condition["token.actions.githubusercontent.com:aud"], "sts.amazonaws.com")
        self.assertEqual(
            condition["token.actions.githubusercontent.com:sub"],
            {"Fn::Sub": "repo:${GitHubRepository}:environment:${GitHubEnvironment}"},
        )
        self.assertEqual(
            condition["token.actions.githubusercontent.com:job_workflow_ref"],
            {"Fn::Sub": "${GitHubRepository}/${AllowedWorkflowFile}@refs/heads/main"},
        )
        self.assertNotIn("StringLike", trust["Condition"])
        self.assertNotIn("*", json.dumps(trust["Condition"]))

    def test_the_workflow_file_default_is_the_live_release_workflow(self):
        self.assertEqual(self.template["Parameters"]["AllowedWorkflowFile"]["Default"], ".github/workflows/deploy-live.yml")
        self.assertTrue(WORKFLOW.exists())

    def test_the_stack_only_creates_in_the_expected_account_and_sydney(self):
        rules = self.template["Rules"]
        self.assertIn("ExpectedAccount", rules)
        self.assertIn("SydneyOnly", rules)

    def test_it_creates_exactly_one_resource_and_it_is_a_role(self):
        self.assertEqual({r["Type"] for r in self.template["Resources"].values()}, {"AWS::IAM::Role"})
        self.assertEqual(len(self.template["Resources"]), 1)

    def test_only_two_actions_may_use_a_wildcard_resource_and_both_are_read_only(self):
        wildcard = {sid: s for sid, s in self.statements.items() if s["Effect"] == "Allow" and s["Resource"] == "*"}
        self.assertEqual(set(wildcard), {"EcrLogin", "ReadCommandResults", "CheckTheTargetIsTaggedLive"})
        for sid, statement in wildcard.items():
            actions = statement["Action"] if isinstance(statement["Action"], list) else [statement["Action"]]
            with self.subTest(statement=sid):
                for action in actions:
                    self.assertRegex(action, r":(Get|List|Describe)")

    def test_no_broad_or_privilege_escalating_actions_are_granted(self):
        for sid, statement in self.statements.items():
            if statement["Effect"] != "Allow":
                continue
            actions = statement["Action"] if isinstance(statement["Action"], list) else [statement["Action"]]
            for action in actions:
                with self.subTest(statement=sid, action=action):
                    self.assertFalse(action.endswith(":*"))
                    self.assertFalse(action.startswith("iam:"))
                    self.assertFalse(action.startswith("sts:"))
                    self.assertNotIn(action, {"ssm:StartSession", "ec2:RunInstances", "cloudformation:UpdateStack"})

    def test_deploy_commands_can_only_target_the_live_instance(self):
        resources = self.statements["RunDeployCommandOnTheLiveInstanceOnly"]["Resource"]
        self.assertIn("instance/${LiveInstanceId}", resources[0]["Fn::Sub"])
        self.assertIn("document/AWS-RunShellScript", resources[1]["Fn::Sub"])

    def test_test_is_read_only_and_its_parameters_are_off_limits(self):
        read_test = self.statements["ReadTestImagesToPromoteThem"]
        for action in read_test["Action"]:
            self.assertRegex(action, r"ecr:(BatchCheckLayerAvailability|BatchGetImage|DescribeImages|GetDownloadUrlForLayer)")
        self.assertIn("${TestRepositoryName}", read_test["Resource"]["Fn::Sub"])
        parameters = self.statements["NeverTouchTestParameters"]
        self.assertEqual((parameters["Effect"], parameters["Action"]), ("Deny", "ssm:*"))
        self.assertIn("/air-concepts/test/*", parameters["Resource"]["Fn::Sub"])
        writes = self.statements["NeverWriteTestImages"]
        self.assertEqual(writes["Effect"], "Deny")
        self.assertIn("ecr:PutImage", writes["Action"])

    def test_live_parameters_are_scoped_to_frontend_reads_and_release_records(self):
        self.assertNotIn("PutParameter", json.dumps(self.statements["ReadPublicFrontendSettings"]))
        records = self.statements["RecordReleases"]
        self.assertIn("/air-concepts/live/release/*", records["Resource"]["Fn::Sub"])
        self.assertNotIn("/backend", self.text)
        self.assertNotIn("DeleteParameter", self.text)

    def test_the_role_can_never_read_backend_secrets(self):
        # The host reads /air-concepts/live/backend/ with its own instance role. The deploy role has no path to it.
        for statement in self.statements.values():
            if statement["Effect"] == "Allow":
                resources = statement["Resource"]
                resources = resources if isinstance(resources, list) else [resources]
                for resource in resources:
                    with self.subTest(statement=statement["Sid"]):
                        self.assertNotIn("backend", json.dumps(resource).replace("backend-", ""))

    def test_s3_and_cloudfront_are_limited_to_the_live_bucket_and_distribution(self):
        self.assertIn("${LiveFrontendBucketName}", json.dumps(self.statements["PublishLiveFrontend"]["Resource"]))
        self.assertIn("${LiveDistributionId}", json.dumps(self.statements["InvalidateLiveDistribution"]["Resource"]))
        self.assertEqual(set(self.statements["PublishLiveFrontend"]["Action"]), {"s3:DeleteObject", "s3:GetObject", "s3:PutObject"})

    def test_repository_parameters_cannot_be_pointed_at_the_wrong_environment(self):
        self.assertRegex("air-concepts-live-backend", self.template["Parameters"]["LiveRepositoryName"]["AllowedPattern"])
        self.assertNotRegex("air-concepts-test-backend", self.template["Parameters"]["LiveRepositoryName"]["AllowedPattern"])
        self.assertRegex("air-concepts-test-backend", self.template["Parameters"]["TestRepositoryName"]["AllowedPattern"])
        self.assertNotRegex("air-concepts-live-backend", self.template["Parameters"]["TestRepositoryName"]["AllowedPattern"])
        self.assertNotRegex("air-concepts-test-infrastructure", self.template["Parameters"]["LiveStackName"]["AllowedPattern"])

    def test_tags_name_the_live_environment(self):
        tags = {tag["Key"]: tag["Value"] for tag in self.role["Tags"]}
        self.assertEqual(tags["environment"], "live")
        self.assertEqual(tags["requirement"], "INF-006")


class LiveStackPolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = json.loads(STACK_POLICY.read_text(encoding="utf-8"))

    def test_stateful_and_public_resources_cannot_be_replaced_or_deleted_by_an_update(self):
        deny = self.policy["Statement"][0]
        self.assertEqual(deny["Effect"], "Deny")
        self.assertEqual(set(deny["Action"]), {"Update:Replace", "Update:Delete"})
        self.assertEqual(
            set(deny["Condition"]["StringEquals"]["ResourceType"]),
            {"AWS::S3::Bucket", "AWS::ECR::Repository", "AWS::CloudFront::Distribution", "AWS::EC2::Instance", "AWS::EC2::EIP"},
        )

    def test_deny_comes_first_and_everything_else_stays_updatable(self):
        self.assertEqual([s["Effect"] for s in self.policy["Statement"]], ["Deny", "Allow"])
        self.assertEqual(self.policy["Statement"][1]["Action"], "Update:*")

    def test_every_protected_type_exists_in_the_infrastructure_template(self):
        template = json.loads((ROOT / "infra/cloudformation/infrastructure.json").read_text(encoding="utf-8"))
        types = {resource["Type"] for resource in template["Resources"].values()}
        for protected in self.policy["Statement"][0]["Condition"]["StringEquals"]["ResourceType"]:
            with self.subTest(type=protected):
                self.assertIn(protected, types)


if __name__ == "__main__":
    unittest.main()
