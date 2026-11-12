"""Behaviour tests for infra/deploy/deploy-backend.sh with stub aws and docker commands.

Spec: docs/specs/CI-002-deploy-test.md (AC-4, AC-5, AC-9, AC-10) and docs/specs/INF-006-live-environment.md.
Plans: docs/plans/CI-002.md, docs/plans/INF-006.md. Requirement IDs: INF-006 for the Live cases, TBD earlier. No Docker and no AWS are used; the stubs record their calls.

Bash is taken from TEST_BASH when set, otherwise from PATH. On Windows, point TEST_BASH at
Git Bash so that WSL's bash.exe is not used.
"""

import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "infra/deploy/deploy-backend.sh"

NEW = "a" * 64
PREV = "b" * 64
CADDY = "c" * 64
REPO = "123456789012.dkr.ecr.ap-southeast-2.amazonaws.com/air-concepts-backend"
DB_PASSWORD = "s3cret-value-XYZ"
PARAM_PREFIX = "/air-concepts/test/backend/"
PARAMS = {
    "db/jdbc-url": "jdbc:postgresql://db.example.test:5432/postgres?sslmode=require",
    "db/username": "app_user",
    "db/password": DB_PASSWORD,
    "supabase/project-ref": "testref123",
    "supabase/jwt-issuer-uri": "https://testref123.supabase.co/auth/v1",
    "supabase/jwks-uri": "https://testref123.supabase.co/auth/v1/.well-known/jwks.json",
    "cors/allowed-origins": "https://d111example.cloudfront.net",
    "unrelated/client-secret": "unrelated-secret-IGNORED",
}

AWS_STUB = """#!/usr/bin/env bash
echo "aws $*" >> "$STUB_LOG"
case "$1 $2" in
  "ecr get-login-password") echo "stub-token" ;;
  "ssm get-parameters")
    names=(); collecting=0
    for arg in "$@"; do
      if [ "$arg" = "--names" ]; then collecting=1; continue; fi
      if [ "$collecting" = 1 ] && [[ "$arg" == --* ]]; then collecting=0; fi
      if [ "$collecting" = 1 ]; then names+=("$arg"); fi
    done
    [ "${#names[@]}" -le 10 ] || { echo "stub: more than ten parameter names" >&2; exit 1; }
    while IFS=$'\\t' read -r name value; do
      [ -n "${STUB_OMIT:-}" ] && [[ "$name" == *"$STUB_OMIT"* ]] && continue
      for wanted in "${names[@]}"; do
        if [ "$name" = "$wanted" ]; then printf '%s\\t%s\\n' "$name" "$value"; fi
      done
    done < "$STUB_PARAMS" ;;
  *) echo "unexpected aws call: $*" >&2; exit 2 ;;
esac
"""

DOCKER_STUB = """#!/usr/bin/env bash
echo "config ${DOCKER_CONFIG:-default}" >> "$STUB_LOG"
echo "docker $* digest=${BACKEND_IMAGE_DIGEST:-none}" >> "$STUB_LOG"
case "$1" in
  login) cat > /dev/null; exit 0 ;;
  compose)
    case "$2" in
      ps)
        if [ "$3" = "-q" ]; then echo "stub-container-id"; else echo "stub-ps-table"; fi
        exit 0 ;;
      logs)
        if [ -n "${STUB_FAIL_LOGS:-}" ]; then echo "stub: logs unavailable" >&2; exit 1; fi
        echo "stub-api-log-line"; exit 0 ;;
    esac
    if [ -n "${STUB_FAIL_DIGEST:-}" ] && [ "${BACKEND_IMAGE_DIGEST:-}" = "$STUB_FAIL_DIGEST" ]; then
      echo "stub: container unhealthy" >&2; exit 1
    fi
    exit 0 ;;
  inspect) echo '{"Status":"unhealthy","FailingStreak":3}'; exit 0 ;;
  *) echo "unexpected docker call: $*" >&2; exit 2 ;;
esac
"""


def _bash():
    return os.environ.get("TEST_BASH") or shutil.which("bash") or "bash"


def _write_stub(directory, name, body):
    path = Path(directory) / name
    path.write_text(body, encoding="utf-8", newline="\n")
    path.chmod(path.stat().st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)


def _function_body(text, name):
    start = text.index(f"{name}() {{")
    end = text.index("\n}\n", start)
    return text[start:end]


class DeployTestScriptTests(unittest.TestCase):
    def setUp(self):
        # Without the script every refusal test would pass for the wrong reason.
        self.assertTrue(SCRIPT.exists(), f"missing {SCRIPT}")
        test_root = ROOT / "build" / "deploy-script-tests"
        test_root.mkdir(parents=True, exist_ok=True)
        self._tmp = tempfile.TemporaryDirectory(dir=test_root)
        self.tmp = Path(self._tmp.name)
        self.bin = self.tmp / "bin"
        self.bin.mkdir()
        _write_stub(self.bin, "aws", AWS_STUB)
        _write_stub(self.bin, "docker", DOCKER_STUB)
        self.air = self.tmp / "opt-air-concepts"
        (self.air / "runtime").mkdir(parents=True)
        self.log = self.tmp / "calls.log"
        self.params = self.tmp / "params.tsv"
        # newline="\n" keeps LF endings on Windows; text mode would add CR, which the script must refuse.
        self.params.write_text(
            "".join(f"{PARAM_PREFIX}{k}\t{v}\n" for k, v in PARAMS.items()),
            encoding="utf-8",
            newline="\n",
        )

    def tearDown(self):
        self.assertTrue(self.tmp.resolve().is_relative_to((ROOT / "build" / "deploy-script-tests").resolve()))
        self._tmp.cleanup()

    def env(self, **overrides):
        values = {
            "PATH": f"{self.bin}{os.pathsep}{os.environ.get('PATH', '')}",
            "ENVIRONMENT": "test",
            "AWS_REGION": "ap-southeast-2",
            "NEW_BACKEND_DIGEST": NEW,
            "CADDY_IMAGE_DIGEST": CADDY,
            "BACKEND_REPOSITORY_URI": REPO,
            "API_HOSTNAME": "api.test.example",
            "AIR_CONCEPTS_DIR": self.air.as_posix(),
            "STUB_LOG": self.log.as_posix(),
            "STUB_PARAMS": self.params.as_posix(),
            "TMPDIR": self.tmp.as_posix(),
        }
        values.update(overrides)
        return {k: v for k, v in values.items() if v is not None}

    def run_script(self, **overrides):
        env = self.env(**overrides)
        return subprocess.run(
            [_bash(), str(SCRIPT)],
            env=env,
            capture_output=True,
            text=True,
            timeout=60,
        )

    def calls(self):
        if not self.log.exists():
            return []
        return self.log.read_text(encoding="utf-8").splitlines()

    def test_unknown_environment_is_refused_before_any_aws_or_docker_call(self):
        for bad in ("staging", "prod", "", "Test", "test live"):
            with self.subTest(environment=bad):
                result = self.run_script(ENVIRONMENT=bad)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.calls(), [])
                self.assertIn("environment", (result.stdout + result.stderr).lower())

    def test_live_reads_only_the_live_parameter_path(self):
        live_prefix = "/air-concepts/live/backend/"
        self.params.write_text(
            "".join(f"{live_prefix}{k}\t{v}\n" for k, v in PARAMS.items()),
            encoding="utf-8",
            newline="\n",
        )
        result = self.run_script(ENVIRONMENT="live")
        self.assertEqual(result.returncode, 0, result.stderr)
        ssm_calls = [c for c in self.calls() if c.startswith("aws ssm get-parameters")]
        self.assertTrue(ssm_calls)
        for call in ssm_calls:
            self.assertIn(live_prefix, call)
            self.assertNotIn("/air-concepts/test/", call)
        self.assertIn("Deployed backend digest " + NEW + " to the live host", result.stdout)

    def test_live_does_not_find_test_parameters(self):
        # Parameter Store only holds Test values here, so a Live deploy must stop instead of using them.
        result = self.run_script(ENVIRONMENT="live")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any("compose" in call for call in self.calls()))
        self.assertIn("required setting", (result.stdout + result.stderr).lower())

    def test_project_ref_is_written_under_the_neutral_name(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = (self.air / "runtime" / "backend.env").read_text(encoding="utf-8").splitlines()
        self.assertIn("SUPABASE_PROJECT_REF=testref123", lines)
        self.assertFalse(any(line.startswith("SUPABASE_TEST_PROJECT_REF") for line in lines))

    def test_expected_project_ref_mismatch_is_refused_before_any_container_change(self):
        known_good = self.air / "runtime" / "backend.env"
        known_good.write_text("DATABASE_URL=known-good\n", encoding="utf-8", newline="\n")
        result = self.run_script(EXPECTED_SUPABASE_PROJECT_REF="otherproject123")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any("compose" in call for call in self.calls()))
        self.assertEqual(known_good.read_text(encoding="utf-8"), "DATABASE_URL=known-good\n")
        output = result.stdout + result.stderr
        self.assertIn("does not match", output)
        self.assertNotIn("testref123", output)

    def test_expected_project_ref_match_is_accepted(self):
        result = self.run_script(EXPECTED_SUPABASE_PROJECT_REF="testref123")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_malformed_expected_project_ref_is_refused(self):
        result = self.run_script(EXPECTED_SUPABASE_PROJECT_REF="BAD REF; rm -rf /")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls(), [])

    def test_memory_limit_reaches_compose_and_malformed_values_are_refused(self):
        result = self.run_script(API_MEMORY_LIMIT="1024m")
        self.assertEqual(result.returncode, 0, result.stderr)
        bad = self.run_script(API_MEMORY_LIMIT="lots")
        self.assertNotEqual(bad.returncode, 0)
        self.assertIn("API_MEMORY_LIMIT", bad.stdout + bad.stderr)

    def _add_diagnostics(self):
        with self.params.open("a", encoding="utf-8", newline="\n") as output:
            output.write(f"{PARAM_PREFIX}diagnostics/xero-enabled\ttrue\n")
            output.write(f"{PARAM_PREFIX}diagnostics/xero-include-bodies\ttrue\n")

    def test_diagnostics_reach_the_backend_only_when_test_only_features_are_allowed(self):
        self._add_diagnostics()
        result = self.run_script(ALLOW_TEST_ONLY_FEATURES="true")
        self.assertEqual(result.returncode, 0, result.stderr)
        runtime = (self.air / "runtime" / "backend.env").read_text(encoding="utf-8")
        self.assertIn("DASHBOARD_DIAGNOSTICS_XERO_ENABLED=true", runtime)

    def test_diagnostics_are_ignored_unless_test_only_features_are_allowed(self):
        # Live must never turn on Xero request or body logging, whatever Parameter Store holds.
        self._add_diagnostics()
        for flag in (None, "false", "TRUE", ""):
            with self.subTest(flag=flag):
                result = self.run_script(ALLOW_TEST_ONLY_FEATURES=flag)
                self.assertEqual(result.returncode, 0, result.stderr)
                runtime = (self.air / "runtime" / "backend.env").read_text(encoding="utf-8")
                self.assertNotIn("DIAGNOSTICS", runtime)

    def test_first_deploy_failure_with_no_previous_release_stops_the_new_containers(self):
        result = self.run_script(STUB_FAIL_DIGEST=NEW)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("first deploy", result.stdout + result.stderr)
        self.assertIn(f"docker compose down digest={NEW}", self.calls())
        self.assertFalse((self.air / "current-digest").exists())

    def test_invalid_digests_are_refused(self):
        for bad in ("sha256:" + NEW, "a" * 63, "A" * 64, "g" * 64):
            with self.subTest(digest=bad):
                result = self.run_script(NEW_BACKEND_DIGEST=bad)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("NEW_BACKEND_DIGEST", result.stdout + result.stderr)
                self.assertEqual(self.calls(), [])

    def test_invalid_caddy_digest_is_refused(self):
        result = self.run_script(CADDY_IMAGE_DIGEST="not-a-digest")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("CADDY_IMAGE_DIGEST", result.stdout + result.stderr)
        self.assertEqual(self.calls(), [])

    def test_uc5_admin_key_is_server_only_and_never_printed(self):
        admin_key = "synthetic-uc5-admin-key-private"
        with self.params.open("a", encoding="utf-8", newline="\n") as output:
            output.write(f"{PARAM_PREFIX}supabase/service-role-key\t{admin_key}\n")
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        runtime = (self.air / "runtime" / "backend.env").read_text(encoding="utf-8")
        self.assertIn("SUPABASE_SERVICE_ROLE_KEY=" + admin_key, runtime)
        self.assertNotIn(admin_key, result.stdout + result.stderr)

    def test_uc5_xero_settings_are_batched_and_only_reach_private_backend_runtime(self):
        settings = {
            "xero/client-id": "synthetic-client",
            "xero/client-secret": "synthetic-xero-private-secret",
            "xero/redirect-uri": "https://api.test.example/api/v1/xero/callback",
            "xero/frontend-callback-uri": "https://app.test.example/settings/entities",
            "xero/token-encryption-key": "synthetic-xero-private-key",
        }
        with self.params.open("a", encoding="utf-8", newline="\n") as output:
            for key, value in settings.items():
                output.write(f"{PARAM_PREFIX}{key}\t{value}\n")
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        runtime = (self.air / "runtime" / "backend.env").read_text(encoding="utf-8")
        self.assertIn("XERO_CLIENT_SECRET=synthetic-xero-private-secret", runtime)
        self.assertIn("XERO_TOKEN_ENCRYPTION_KEY=synthetic-xero-private-key", runtime)
        self.assertEqual(sum("ssm get-parameters" in call for call in self.calls()), 2)
        self.assertNotIn(settings["xero/client-secret"], result.stdout + result.stderr)
        self.assertNotIn(settings["xero/token-encryption-key"], result.stdout + result.stderr)

    def test_healthy_path_writes_state_and_reports_success(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.air / "current-digest").read_text(encoding="utf-8").strip(), NEW)
        self.assertIn(f"docker compose up -d --wait --wait-timeout 180 digest={NEW}", self.calls())

        env_file = self.air / "runtime" / "backend.env"
        lines = env_file.read_text(encoding="utf-8").splitlines()
        self.assertIn("DATABASE_URL=" + PARAMS["db/jdbc-url"], lines)
        self.assertIn("DATABASE_PASSWORD=" + DB_PASSWORD, lines)
        self.assertIn("CORS_ALLOWED_ORIGINS=" + PARAMS["cors/allowed-origins"], lines)
        self.assertFalse(any("XERO" in line.upper() for line in lines))
        if os.name != "nt":
            self.assertEqual(stat.S_IMODE(env_file.stat().st_mode), 0o600)

    def test_unhealthy_path_restores_previous_digest_and_config_then_fails(self):
        (self.air / "current-digest").write_text(PREV + "\n", encoding="utf-8", newline="\n")
        known_good = self.air / "runtime" / "backend.env"
        known_good.write_text("DATABASE_URL=known-good\n", encoding="utf-8", newline="\n")

        result = self.run_script(STUB_FAIL_DIGEST=NEW)

        self.assertNotEqual(result.returncode, 0)
        calls = self.calls()
        self.assertIn(f"docker compose up -d --wait --wait-timeout 180 digest={NEW}", calls)
        self.assertIn(f"docker compose up -d --wait --wait-timeout 180 digest={PREV}", calls)
        self.assertEqual((self.air / "current-digest").read_text(encoding="utf-8").strip(), PREV)
        self.assertEqual(known_good.read_text(encoding="utf-8"), "DATABASE_URL=known-good\n")

    def test_failed_deploy_prints_api_diagnostics_before_rollback(self):
        (self.air / "current-digest").write_text(PREV + "\n", encoding="utf-8", newline="\n")

        result = self.run_script(STUB_FAIL_DIGEST=NEW)

        self.assertNotEqual(result.returncode, 0)
        calls = self.calls()
        failed_up = calls.index(f"docker compose up -d --wait --wait-timeout 180 digest={NEW}")
        rollback_up = calls.index(f"docker compose up -d --wait --wait-timeout 180 digest={PREV}")
        logs = [i for i, c in enumerate(calls) if c.startswith("docker compose logs --no-color --tail 100 api")]
        inspect = [i for i, c in enumerate(calls) if c.startswith("docker inspect") and "stub-container-id" in c]
        self.assertTrue(logs, "api logs were not collected")
        self.assertTrue(inspect, "api health was not inspected")
        self.assertTrue(failed_up < logs[0] < rollback_up)
        self.assertTrue(failed_up < inspect[0] < rollback_up)
        output = result.stdout + result.stderr
        self.assertIn("stub-api-log-line", output)
        self.assertIn('"Status":"unhealthy"', output)

    def test_failing_diagnostics_do_not_stop_the_rollback(self):
        (self.air / "current-digest").write_text(PREV + "\n", encoding="utf-8", newline="\n")

        result = self.run_script(STUB_FAIL_DIGEST=NEW, STUB_FAIL_LOGS="1")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f"docker compose up -d --wait --wait-timeout 180 digest={PREV}", self.calls())
        self.assertEqual((self.air / "current-digest").read_text(encoding="utf-8").strip(), PREV)

    def test_failed_deploy_output_contains_no_secret_values(self):
        (self.air / "current-digest").write_text(PREV + "\n", encoding="utf-8", newline="\n")

        result = self.run_script(STUB_FAIL_DIGEST=NEW)

        output = result.stdout + result.stderr
        for value in PARAMS.values():
            with self.subTest(value=value[:12]):
                self.assertNotIn(value, output)

    def test_diagnostics_never_read_the_env_file_or_print_resolved_config(self):
        body = _function_body(SCRIPT.read_text(encoding="utf-8"), "show_api_diagnostics")
        self.assertNotIn("backend.env", body)
        self.assertNotIn("env_file", body)
        self.assertNotIn("compose config", body)
        self.assertNotIn("inspect --format '{{json .Config", body)

    def test_missing_required_setting_preserves_known_good_config(self):
        known_good = self.air / "runtime" / "backend.env"
        known_good.write_text("DATABASE_URL=known-good\n", encoding="utf-8", newline="\n")

        result = self.run_script(STUB_OMIT=PARAM_PREFIX + "db/password")

        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any("compose" in call for call in self.calls()))
        self.assertEqual(known_good.read_text(encoding="utf-8"), "DATABASE_URL=known-good\n")
        output = result.stdout + result.stderr
        self.assertIn("DATABASE_PASSWORD", output)
        self.assertNotIn(DB_PASSWORD, output)

    def test_empty_required_value_is_refused(self):
        self.params.write_text(
            self.params.read_text(encoding="utf-8").replace(
                PARAM_PREFIX + "db/username\tapp_user", PARAM_PREFIX + "db/username\t"
            ),
            encoding="utf-8",
            newline="\n",
        )
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any("compose" in call for call in self.calls()))

    def test_no_secret_values_appear_in_output(self):
        result = self.run_script()
        output = result.stdout + result.stderr
        for value in PARAMS.values():
            with self.subTest(value=value[:12]):
                self.assertNotIn(value, output)

    def test_reads_exact_parameter_names_because_the_host_role_cannot_list_a_path(self):
        text = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("ssm get-parameters --region", text)
        self.assertNotIn("get-parameters-by-path", text)

    def test_docker_login_uses_a_temporary_config_dir_removed_on_exit(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        configs = [c.split(" ", 1)[1] for c in self.calls() if c.startswith("config ")]
        self.assertTrue(configs, "docker was never called")
        for config in configs:
            with self.subTest(config=config):
                self.assertNotEqual(config, "default")
                self.assertFalse(os.path.exists(config), "ECR token directory was not removed")

    def test_script_never_enables_shell_tracing(self):
        text = SCRIPT.read_text(encoding="utf-8")
        self.assertNotIn("set -x", text)
        self.assertNotIn("xtrace", text)


if __name__ == "__main__":
    unittest.main()
