"""Tests for setup-test-parameters.py. AWS is mocked: no test contacts AWS.

Requirement IDs: none supplied for this task.
"""

from __future__ import annotations

import importlib.util
import re
import sys
from pathlib import Path

import pytest
from botocore.exceptions import ClientError

_SCRIPT = Path(__file__).with_name("setup-test-parameters.py")
_spec = importlib.util.spec_from_file_location("setup_test_parameters", _SCRIPT)
setup = importlib.util.module_from_spec(_spec)
sys.modules["setup_test_parameters"] = setup
_spec.loader.exec_module(setup)

REPO_ROOT = Path(__file__).resolve().parents[2]
APPLICATION_YML = REPO_ROOT / "backend" / "src" / "main" / "resources" / "application.yml"
REQUIRED_SETTINGS_JAVA = (REPO_ROOT / "backend" / "src" / "main" / "java" / "com" / "airconcepts" / "dashboard"
                          / "config" / "RequiredSettingsPostProcessor.java")

ACCOUNT = "923079246426"
BASE = ["--expected-account-id", ACCOUNT, "--profile", "test-admin"]
SECRET = "test-only-secret-VALUE-987"

# A value for every key. The script does not validate values, so these are simple placeholders.
VALUES = {key.path: f"test-value-{index}" for index, key in enumerate(setup.KEYS)}
VALUES["backend/db/password"] = SECRET


class FakeSSM:
    """Records calls. It has no get_parameter, so any attempt to read a value fails the test."""

    def __init__(self, existing=None, fail_on=()):
        self.parameters = dict(existing or {})
        self.puts = []
        self.fail_on = set(fail_on)

    def describe_parameters(self, ParameterFilters, MaxResults):
        name = ParameterFilters[0]["Values"][0]
        if name in self.parameters:
            return {"Parameters": [{"Name": name, "Type": self.parameters[name]}]}
        return {"Parameters": []}

    def put_parameter(self, **request):
        if request["Name"] in self.fail_on:
            raise ClientError({"Error": {"Code": "ThrottlingException", "Message": "Rate exceeded"}},
                              "PutParameter")
        self.puts.append(request)
        self.parameters[request["Name"]] = request["Type"]


class FakeSTS:
    def __init__(self, account):
        self._account = account

    def get_caller_identity(self):
        return {"Account": self._account}


class FakeSession:
    def __init__(self, ssm, account=ACCOUNT):
        self._clients = {"ssm": ssm, "sts": FakeSTS(account)}

    def client(self, service, **_kwargs):
        return self._clients[service]


def answers(values=VALUES):
    return lambda key: values.get(key.path, "")


def nobody_asked(_key):
    raise AssertionError("the script asked for a value it should not have asked for")


def run_cli(args, *, ssm=None, account=ACCOUNT, ask=None):
    """Run the CLI in-process. Returns (exit code, fake SSM, refusal message)."""
    ssm = ssm if ssm is not None else FakeSSM()
    session = FakeSession(ssm, account)
    try:
        code = setup.run(args, session_factory=lambda profile, region: session,
                         ask_fn=ask if ask is not None else (lambda key: ""))
    except setup.Refused as err:
        return setup.EXIT_REFUSED, ssm, str(err)
    return code, ssm, ""


def puts_by_name(ssm):
    return {request["Name"]: request for request in ssm.puts}


# --- Names and guards ------------------------------------------------------------------------

def test_every_key_is_under_the_test_root_and_unique():
    paths = [key.path for key in setup.KEYS]
    assert len(set(paths)) == len(paths)
    assert all(setup.full_name(key).startswith("/air-concepts/test/") for key in setup.KEYS)


@pytest.mark.parametrize("name", [
    "/air-concepts/live/backend/db/password",
    "/air-concepts/test/../live/db/password",
    "/air-concepts/test/live/db/password",
    "/air-concepts/prod/backend/db/password",
    "/other/app/db/password",
])
def test_refuses_names_outside_the_test_root_or_naming_live(name):
    with pytest.raises(setup.Refused):
        setup.check_name(name)


def test_refuses_region_other_than_ap_southeast_2():
    code, ssm, message = run_cli(BASE + ["--region", "us-east-1"])
    assert code == setup.EXIT_REFUSED
    assert ssm.puts == []


def test_refuses_when_caller_account_is_not_the_expected_account_before_asking():
    code, ssm, message = run_cli(BASE + ["--apply"], account="111111111111", ask=nobody_asked)
    assert code == setup.EXIT_REFUSED
    assert "is not" in message
    assert ssm.puts == []


# --- Dry run and apply ------------------------------------------------------------------------

def test_dry_run_lists_every_key_and_asks_for_nothing(capsys):
    code, ssm, _ = run_cli(BASE, ask=nobody_asked)
    out = capsys.readouterr().out
    assert code == setup.EXIT_OK
    assert ssm.puts == []
    assert "DRY RUN" in out
    for key in setup.KEYS:
        assert setup.full_name(key) in out


def test_apply_asks_for_every_key_and_writes_each_value_with_its_type():
    code, ssm, _ = run_cli(BASE + ["--apply"], ask=answers())
    assert code == setup.EXIT_OK
    written = puts_by_name(ssm)
    assert len(written) == len(setup.KEYS)
    for key in setup.KEYS:
        request = written[setup.full_name(key)]
        assert request["Value"] == VALUES[key.path]
        assert request["Type"] == key.kind
        assert request["Overwrite"] is False
    assert written[setup.ROOT + "backend/db/password"]["Type"] == "SecureString"
    assert written[setup.ROOT + "frontend/api-base-url"]["Type"] == "String"


def test_blank_answers_are_skipped_and_nothing_is_written(capsys):
    code, ssm, _ = run_cli(BASE + ["--apply"], ask=lambda key: "")
    assert code == setup.EXIT_OK
    assert ssm.puts == []
    assert "skipped" in capsys.readouterr().out


def test_whitespace_only_answer_counts_as_blank():
    code, ssm, _ = run_cli(BASE + ["--apply"], ask=lambda key: "   ")
    assert code == setup.EXIT_OK
    assert ssm.puts == []


def test_existing_keys_are_kept_and_not_asked_for_by_default(capsys):
    existing = {setup.ROOT + "backend/db/password": "SecureString"}
    asked = []

    def ask(key):
        asked.append(key.path)
        return VALUES[key.path]

    code, ssm, _ = run_cli(BASE + ["--apply"], ssm=FakeSSM(existing), ask=ask)
    assert code == setup.EXIT_OK
    assert "backend/db/password" not in asked
    assert setup.ROOT + "backend/db/password" not in puts_by_name(ssm)
    assert "exists" in capsys.readouterr().out


def test_overwrite_asks_for_existing_keys_and_replaces_them():
    existing = {setup.ROOT + "frontend/api-base-url": "String"}
    code, ssm, _ = run_cli(BASE + ["--apply", "--overwrite"], ssm=FakeSSM(existing), ask=answers())
    assert code == setup.EXIT_OK
    assert puts_by_name(ssm)[setup.ROOT + "frontend/api-base-url"]["Overwrite"] is True


def test_dry_run_with_overwrite_says_would_replace(capsys):
    existing = {setup.ROOT + "frontend/api-base-url": "String"}
    code, ssm, _ = run_cli(BASE + ["--overwrite"], ssm=FakeSSM(existing), ask=nobody_asked)
    assert code == setup.EXIT_OK
    assert "would replace" in capsys.readouterr().out
    assert ssm.puts == []


def test_kms_key_is_passed_only_for_securestring_values():
    code, ssm, _ = run_cli(BASE + ["--apply", "--kms-key-id", "alias/test-ssm"], ask=answers())
    written = puts_by_name(ssm)
    assert written[setup.ROOT + "backend/db/password"]["KeyId"] == "alias/test-ssm"
    assert "KeyId" not in written[setup.ROOT + "backend/db/username"]


# --- Output and failures ----------------------------------------------------------------------

def test_secret_value_is_never_printed(capsys):
    code, ssm, _ = run_cli(BASE + ["--apply"], ask=answers())
    captured = capsys.readouterr()
    assert code == setup.EXIT_OK
    assert SECRET not in captured.out + captured.err


def test_secret_value_is_not_printed_even_when_its_write_fails(capsys):
    ssm = FakeSSM(fail_on={setup.ROOT + "backend/db/password"})
    code, ssm, _ = run_cli(BASE + ["--apply"], ssm=ssm, ask=answers())
    captured = capsys.readouterr()
    assert code == setup.EXIT_FAILED
    assert SECRET not in captured.out + captured.err


def test_a_failed_write_is_reported_and_the_rest_still_run(capsys):
    ssm = FakeSSM(fail_on={setup.ROOT + "backend/db/username"})
    code, ssm, _ = run_cli(BASE + ["--apply"], ssm=ssm, ask=answers())
    out = capsys.readouterr().out
    assert code == setup.EXIT_FAILED
    assert "failed" in out and "ThrottlingException" in out
    assert setup.ROOT + "backend/db/password" in puts_by_name(ssm)


# --- Drift checks against the application code -----------------------------------------------

def _required_env_vars_in_application_yml():
    text = APPLICATION_YML.read_text(encoding="utf-8")
    return {m.group(1) for m in re.finditer(r"\$\{([A-Z_]+)(:[^}]*)?\}", text) if m.group(2) is None}


def _required_env_vars_in_startup_guard():
    text = REQUIRED_SETTINGS_JAVA.read_text(encoding="utf-8")
    block = re.search(r"REQUIRED = List\.of\((.*?)\);", text, re.S).group(1)
    return set(re.findall(r'"([A-Z_]+)"', block))


def test_every_setting_the_startup_guard_requires_has_a_backend_key():
    backend_text = " ".join(key.used_by for key in setup.KEYS if key.path.startswith("backend/"))
    for var in _required_env_vars_in_startup_guard():
        assert var in backend_text, f"{var} has no key"


def test_every_setting_application_yml_requires_has_a_backend_key():
    backend_text = " ".join(key.used_by for key in setup.KEYS if key.path.startswith("backend/"))
    for var in _required_env_vars_in_application_yml():
        assert var in backend_text, f"{var} has no key"


def test_frontend_keys_cover_the_vite_settings_the_build_reads():
    frontend_text = " ".join(key.used_by for key in setup.KEYS if key.path.startswith("frontend/"))
    for var in ("VITE_API_BASE_URL", "VITE_SUPABASE_URL", "VITE_SUPABASE_PUBLISHABLE_KEY"):
        assert var in frontend_text
