"""Tests for setup-parameters.py. AWS is mocked: no test contacts AWS.

Requirement IDs: INF-006 (live environment); earlier cases predate it.
"""

from __future__ import annotations

import importlib.util
import re
import sys
from pathlib import Path

import pytest
from botocore.exceptions import ClientError

_SCRIPT = Path(__file__).with_name("setup-parameters.py")
_spec = importlib.util.spec_from_file_location("setup_parameters", _SCRIPT)
setup = importlib.util.module_from_spec(_spec)
sys.modules["setup_parameters"] = setup
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


def test_gl26_vwork_configuration_uses_separate_secure_key_and_retires_placeholder():
    keys = {key.path: key for key in setup.KEYS}
    assert keys["backend/vwork/credential-encryption-key"].kind == "SecureString"
    assert keys["backend/vwork/mode"].kind == "String"
    assert "backend/vwork/api-key" not in keys
    deployment = (REPO_ROOT / "infra" / "deploy" / "deploy-backend.sh").read_text(encoding="utf-8")
    assert "[vwork/mode]=DASHBOARD_VWORK_MODE" in deployment
    assert "[vwork/credential-encryption-key]=VWORK_CREDENTIAL_ENCRYPTION_KEY" in deployment


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


def run_cli(args, *, ssm=None, account=ACCOUNT, ask=None, custom=None):
    """Run the CLI in-process. Returns (exit code, fake SSM, refusal message)."""
    ssm = ssm if ssm is not None else FakeSSM()
    session = FakeSession(ssm, account)
    try:
        code = setup.run(args, session_factory=lambda profile, region: session,
                         ask_fn=ask if ask is not None else (lambda key: ""),
                         custom_key_fn=custom if custom is not None else nobody_asked_for_custom)
    except setup.Refused as err:
        return setup.EXIT_REFUSED, ssm, str(err)
    return code, ssm, ""


def nobody_asked_for_custom(_taken):
    raise AssertionError("the script asked for a custom parameter it should not have asked for")


def custom_keys(*entries):
    """Return a custom_key_fn that yields the given (name, kind) pairs in order, then finishes."""
    queue = list(entries)

    def fn(_taken):
        if not queue:
            return None
        name, kind = queue.pop(0)
        return setup.Key(name, kind, used_by="custom parameter", required=False)

    return fn


def puts_by_name(ssm):
    return {request["Name"]: request for request in ssm.puts}


@pytest.fixture(autouse=True)
def _reset_environment():
    """The script keeps the selected environment in module state. Every test starts on Test."""
    setup.configure("test")
    yield
    setup.configure("test")


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


# --- Custom parameters (--add-custom) ---------------------------------------------------------

CUSTOM_SECRET = "test-only-custom-SECRET-456"


def test_add_custom_writes_each_entered_key_with_its_type_and_value():
    entries = [("app/feature/export-limit", "String"), ("app/partner/api-token", "SecureString")]
    code, ssm, _ = run_cli(BASE + ["--apply", "--add-custom"], ask=lambda key: "test-custom-" + key.path,
                           custom=custom_keys(*entries))
    assert code == setup.EXIT_OK
    written = puts_by_name(ssm)
    assert written[setup.ROOT + "app/feature/export-limit"]["Type"] == "String"
    assert written[setup.ROOT + "app/feature/export-limit"]["Value"] == "test-custom-app/feature/export-limit"
    assert written[setup.ROOT + "app/partner/api-token"]["Type"] == "SecureString"
    assert written[setup.ROOT + "app/partner/api-token"]["Overwrite"] is False


def test_custom_secure_string_gets_the_kms_key_and_plain_string_does_not():
    code, ssm, _ = run_cli(BASE + ["--apply", "--add-custom", "--kms-key-id", "alias/test-ssm"],
                           ask=lambda key: "test-custom-value",
                           custom=custom_keys(("app/one", "String"), ("app/two", "SecureString")))
    written = puts_by_name(ssm)
    assert written[setup.ROOT + "app/two"]["KeyId"] == "alias/test-ssm"
    assert "KeyId" not in written[setup.ROOT + "app/one"]


def test_custom_secret_value_is_never_printed(capsys):
    code, ssm, _ = run_cli(BASE + ["--apply", "--add-custom"], ask=lambda key: CUSTOM_SECRET,
                           custom=custom_keys(("app/partner/api-token", "SecureString")))
    captured = capsys.readouterr()
    assert code == setup.EXIT_OK
    assert ssm.puts and CUSTOM_SECRET not in captured.out + captured.err


def test_custom_blank_value_is_skipped():
    code, ssm, _ = run_cli(BASE + ["--apply", "--add-custom"], ask=lambda key: "  ",
                           custom=custom_keys(("app/one", "String")))
    assert code == setup.EXIT_OK
    assert ssm.puts == []


def test_add_custom_in_a_dry_run_asks_nothing_and_writes_nothing(capsys):
    code, ssm, _ = run_cli(BASE + ["--add-custom"], ask=nobody_asked, custom=nobody_asked_for_custom)
    out = capsys.readouterr().out
    assert code == setup.EXIT_OK
    assert ssm.puts == []
    assert "--add-custom works with --apply" in out


def test_existing_custom_key_is_kept_without_overwrite(capsys):
    existing = {setup.ROOT + "app/one": "String"}
    code, ssm, _ = run_cli(BASE + ["--apply", "--add-custom"], ssm=FakeSSM(existing),
                           ask=lambda key: "test-custom-value", custom=custom_keys(("app/one", "String")))
    assert code == setup.EXIT_OK
    assert setup.ROOT + "app/one" not in puts_by_name(ssm)
    assert "kept" in capsys.readouterr().out


def test_existing_custom_key_is_replaced_with_overwrite():
    existing = {setup.ROOT + "app/one": "String"}
    code, ssm, _ = run_cli(BASE + ["--apply", "--add-custom", "--overwrite"], ssm=FakeSSM(existing),
                           ask=lambda key: "test-custom-value", custom=custom_keys(("app/one", "String")))
    assert puts_by_name(ssm)[setup.ROOT + "app/one"]["Overwrite"] is True


def test_custom_key_cannot_reuse_a_built_in_name():
    taken = {setup.ROOT + "backend/db/username"}
    with pytest.raises(setup.Refused):
        setup.check_custom_name(setup.ROOT + "backend/db/username", taken)


def test_custom_key_cannot_be_entered_twice_in_one_run(capsys):
    code, ssm, _ = run_cli(BASE + ["--apply", "--add-custom"], ask=lambda key: "test-custom-value",
                           custom=custom_keys(("app/one", "String"), ("app/one", "String")))
    assert code == setup.EXIT_OK
    names = [request["Name"] for request in ssm.puts]
    assert names.count(setup.ROOT + "app/one") == 1
    assert len(names) == len(setup.KEYS) + 1
    assert "refused" in capsys.readouterr().out


@pytest.mark.parametrize("name", [
    "live/app/token",
    "app/live/token",
    "app/prod/token",
    "app/production/token",
    "../app/token",
    "app/../token",
    "app//token",
    "/app/token",
    "app/token/",
    "app/token name",
    "app/token;rm",
    "",
])
def test_custom_name_rules_refuse_unsafe_or_non_test_names(name):
    with pytest.raises(setup.Refused):
        setup.check_custom_name(setup.ROOT + name, set())


def test_custom_name_rules_accept_a_plain_nested_name():
    setup.check_custom_name(setup.ROOT + "app/feature-flags/export.limit_v2", set())


def test_prompt_reprompts_after_a_bad_name_and_bad_type(capsys):
    answers_in = iter(["live/app/token", "app/token", "text", "securestring"])
    entered = setup.prompt_custom_key(set(), input_fn=lambda label: next(answers_in))
    assert entered.path == "app/token"
    assert entered.kind == "SecureString"
    assert entered.required is False
    out = capsys.readouterr().out
    assert "String or SecureString" in out


def test_prompt_defaults_type_to_string_and_blank_name_finishes():
    answers_in = iter(["app/token", ""])
    entered = setup.prompt_custom_key(set(), input_fn=lambda label: next(answers_in))
    assert entered.kind == "String"
    assert setup.prompt_custom_key(set(), input_fn=lambda label: "") is None


def test_prompt_treats_end_of_input_as_finish():
    def eof(_label):
        raise EOFError

    assert setup.prompt_custom_key(set(), input_fn=eof) is None


# --- Drift checks against the application code -----------------------------------------------

def _required_env_vars_in_application_yml():
    text = APPLICATION_YML.read_text(encoding="utf-8")
    return {m.group(1) for m in re.finditer(r"\$\{([A-Z_]+)(:[^}]*)?\}", text) if m.group(2) is None}


def _required_env_vars_in_startup_guard():
    text = REQUIRED_SETTINGS_JAVA.read_text(encoding="utf-8")
    block = re.search(r"REQUIRED = List\.of\((.*?)\);", text, re.S).group(1)
    names = set(re.findall(r'"([A-Z_]+)"', block))
    # The project ref is checked separately from the list, under one environment-neutral name.
    names.add(re.search(r'PROJECT_REF = "([A-Z_]+)"', text).group(1))
    return names


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


# --- INF-006: one script for Test and Live -----------------------------------------------------

def test_live_run_writes_only_under_the_live_root():
    values = {key.path: f"live-value-{i}" for i, key in enumerate(setup.keys_for("live"))}
    code, ssm, _ = run_cli(BASE + ["--environment", "live", "--apply"], ask=answers(values))
    assert code == setup.EXIT_OK
    assert ssm.puts, "nothing was written"
    assert all(request["Name"].startswith("/air-concepts/live/") for request in ssm.puts)
    assert not any("/air-concepts/test/" in request["Name"] for request in ssm.puts)


def test_live_run_does_not_offer_test_only_diagnostics_keys():
    asked = []

    def record(key):
        asked.append(key.path)
        return ""

    run_cli(BASE + ["--environment", "live", "--apply"], ask=record)
    assert asked, "no key was offered"
    assert not any(path.startswith("backend/diagnostics/") for path in asked)


def test_test_run_still_offers_diagnostics_keys():
    asked = []

    def record(key):
        asked.append(key.path)
        return ""

    run_cli(BASE + ["--apply"], ask=record)
    assert "backend/diagnostics/xero-enabled" in asked


@pytest.mark.parametrize("name", [
    "/air-concepts/test/backend/db/password",
    "/air-concepts/live/../test/db/password",
    "/air-concepts/live/test/db/password",
    "/air-concepts/prod/backend/db/password",
])
def test_live_refuses_names_that_belong_to_another_environment(name):
    setup.configure("live")
    with pytest.raises(setup.Refused):
        setup.check_name(name)


def test_live_accepts_its_own_names():
    setup.configure("live")
    setup.check_name("/air-concepts/live/backend/db/password")


def test_unknown_environment_is_refused():
    with pytest.raises(setup.Refused):
        setup.configure("staging")


def test_live_dry_run_writes_nothing():
    code, ssm, _ = run_cli(BASE + ["--environment", "live"])
    assert code == setup.EXIT_OK
    assert ssm.puts == []


def test_live_still_checks_the_account_before_asking():
    code, ssm, message = run_cli(BASE + ["--environment", "live", "--apply"], account="111111111111", ask=nobody_asked)
    assert code == setup.EXIT_REFUSED
    assert ssm.puts == []
