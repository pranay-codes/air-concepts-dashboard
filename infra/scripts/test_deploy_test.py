"""Behaviour tests for infra/deploy/deploy-test.sh with stub aws and docker commands.

Spec: docs/specs/CI-002-deploy-test.md (AC-4, AC-5, AC-9, AC-10). Plan: docs/plans/CI-002.md.
Requirement IDs: TBD. No Docker and no AWS are used; the stubs record their calls.

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
SCRIPT = ROOT / "infra/deploy/deploy-test.sh"

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
    "xero/client-secret": "xero-secret-IGNORED",
}

AWS_STUB = """#!/usr/bin/env bash
echo "aws $*" >> "$STUB_LOG"
case "$1 $2" in
  "ecr get-login-password") echo "stub-token" ;;
  "ssm get-parameters-by-path")
    if [ -n "${STUB_OMIT:-}" ]; then grep -v -F "$STUB_OMIT" "$STUB_PARAMS"; else cat "$STUB_PARAMS"; fi ;;
  *) echo "unexpected aws call: $*" >&2; exit 2 ;;
esac
"""

DOCKER_STUB = """#!/usr/bin/env bash
echo "docker $* digest=${BACKEND_IMAGE_DIGEST:-none}" >> "$STUB_LOG"
case "$1" in
  login) cat > /dev/null; exit 0 ;;
  compose)
    if [ -n "${STUB_FAIL_DIGEST:-}" ] && [ "${BACKEND_IMAGE_DIGEST:-}" = "$STUB_FAIL_DIGEST" ]; then
      echo "stub: container unhealthy" >&2; exit 1
    fi
    exit 0 ;;
  *) echo "unexpected docker call: $*" >&2; exit 2 ;;
esac
"""


def _bash():
    return os.environ.get("TEST_BASH") or shutil.which("bash") or "bash"


def _write_stub(directory, name, body):
    path = Path(directory) / name
    path.write_text(body, encoding="utf-8", newline="\n")
    path.chmod(path.stat().st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)


class DeployTestScriptTests(unittest.TestCase):
    def setUp(self):
        # Without the script every refusal test would pass for the wrong reason.
        self.assertTrue(SCRIPT.exists(), f"missing {SCRIPT}")
        self._tmp = tempfile.TemporaryDirectory()
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
            "AIR_CONCEPTS_DIR": str(self.air),
            "STUB_LOG": str(self.log),
            "STUB_PARAMS": str(self.params),
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

    def test_wrong_environment_is_refused_before_any_aws_or_docker_call(self):
        result = self.run_script(ENVIRONMENT="live")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls(), [])
        self.assertIn("environment", (result.stdout + result.stderr).lower())

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

    def test_script_never_enables_shell_tracing(self):
        text = SCRIPT.read_text(encoding="utf-8")
        self.assertNotIn("set -x", text)
        self.assertNotIn("xtrace", text)


if __name__ == "__main__":
    unittest.main()
