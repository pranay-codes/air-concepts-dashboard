"""Tests for list-parameters.py. AWS is faked: no test contacts AWS.

Requirement IDs: none supplied for this task.
"""

from __future__ import annotations

import importlib.util
import io
import json
import sys
from pathlib import Path

import pytest

_SCRIPT = Path(__file__).with_name("list-parameters.py")
_spec = importlib.util.spec_from_file_location("list_parameters", _SCRIPT)
lp = importlib.util.module_from_spec(_spec)
sys.modules["list_parameters"] = lp
_spec.loader.exec_module(lp)

ACCOUNT = "923079246426"
BASE = ["--expected-account-id", ACCOUNT, "--profile", "p"]
SECRET_CIPHERTEXT = "AQICAHh-not-a-real-secret-VALUE-987"


class FakeSSM:
    def __init__(self, parameters):
        self.parameters = parameters  # {path_prefix: [items]}
        self.requested = []  # (Path, WithDecryption)

    def get_paginator(self, name):
        assert name == "get_parameters_by_path"
        return self

    def paginate(self, Path, Recursive, WithDecryption):
        self.requested.append((Path, WithDecryption))
        assert Recursive is True
        items = [item for item in self.parameters.get(Path, [])]
        yield {"Parameters": items}


class FakeSession:
    def __init__(self, account, ssm):
        self._account = account
        self._ssm = ssm

    def client(self, service):
        if service == "sts":
            return _Sts(self._account)
        assert service == "ssm"
        return self._ssm


class _Sts:
    def __init__(self, account):
        self._account = account

    def get_caller_identity(self):
        return {"Account": self._account}


def make_factory(parameters, account=ACCOUNT):
    ssm = FakeSSM(parameters)
    return (lambda profile, region: FakeSession(account, ssm)), ssm


PARAMETERS = {
    "/air-concepts/test/": [
        {"Name": "/air-concepts/test/frontend/api-base-url", "Type": "String", "Value": "https://api.test.example"},
        {"Name": "/air-concepts/test/backend/db/password", "Type": "SecureString", "Value": SECRET_CIPHERTEXT},
    ],
    "/air-concepts/live/": [
        {"Name": "/air-concepts/live/frontend/api-base-url", "Type": "String", "Value": "https://api.live.example"},
        {"Name": "/air-concepts/live/backend/xero/token-encryption-key", "Type": "SecureString", "Value": SECRET_CIPHERTEXT},
    ],
}


def run_cli(args, parameters=PARAMETERS, account=ACCOUNT):
    factory, ssm = make_factory(parameters, account)
    out = io.StringIO()
    code = lp.run(args, session_factory=factory, out=out)
    return code, out.getvalue(), ssm


def test_test_environment_lists_only_test_prefix():
    code, text, ssm = run_cli(BASE + ["--environment", "test"])
    assert code == lp.EXIT_OK
    assert [path for path, _ in ssm.requested] == ["/air-concepts/test/"]
    assert "/air-concepts/test/frontend/api-base-url" in text
    assert "https://api.test.example" in text
    assert "/air-concepts/live/" not in text


def test_live_environment_lists_only_live_prefix():
    code, text, ssm = run_cli(BASE + ["--environment", "live"])
    assert code == lp.EXIT_OK
    assert [path for path, _ in ssm.requested] == ["/air-concepts/live/"]
    assert "https://api.live.example" in text
    assert "/air-concepts/test/" not in text


def test_all_lists_both_environments():
    code, text, ssm = run_cli(BASE + ["--environment", "all"])
    assert code == lp.EXIT_OK
    assert [path for path, _ in ssm.requested] == ["/air-concepts/test/", "/air-concepts/live/"]
    assert "https://api.test.example" in text
    assert "https://api.live.example" in text


def test_secure_string_values_are_never_printed_or_requested_decrypted():
    code, text, ssm = run_cli(BASE + ["--environment", "all"])
    assert SECRET_CIPHERTEXT not in text
    assert "value not shown" in text
    assert all(with_decryption is False for _, with_decryption in ssm.requested)


def test_secure_string_values_hidden_in_json_output_too():
    code, text, _ = run_cli(BASE + ["--environment", "all", "--format", "json"])
    assert code == lp.EXIT_OK
    assert SECRET_CIPHERTEXT not in text
    payload = json.loads(text)
    secure = [p for env in payload.values() for p in env["parameters"] if p["type"] == "SecureString"]
    assert secure and all(p["valueHidden"] and p["value"] is None for p in secure)


def test_json_output_lists_string_values():
    code, text, _ = run_cli(BASE + ["--environment", "test", "--format", "json"])
    payload = json.loads(text)
    strings = [p for p in payload["test"]["parameters"] if p["type"] == "String"]
    assert strings == [{"name": "/air-concepts/test/frontend/api-base-url", "type": "String",
                        "value": "https://api.test.example", "valueHidden": False}]


def test_empty_environment_reports_none():
    code, text, _ = run_cli(BASE + ["--environment", "live"], parameters={})
    assert code == lp.EXIT_OK
    assert "0 parameter(s)" in text and "(none)" in text


def test_wrong_account_refuses_before_any_read():
    factory, ssm = make_factory(PARAMETERS, account="111111111111")
    with pytest.raises(lp.Refused, match="not 923079246426"):
        lp.run(BASE + ["--environment", "all"], session_factory=factory, out=io.StringIO())
    assert ssm.requested == []


def test_missing_environment_is_rejected_by_argparse():
    with pytest.raises(SystemExit):
        lp.build_parser().parse_args(BASE)


@pytest.mark.parametrize("bad", ["prod", "production", "../live", "staging"])
def test_unknown_environment_refused(bad):
    with pytest.raises(lp.Refused):
        lp.root_for(bad)


def test_root_for_stays_under_app_root():
    assert lp.root_for("test") == "/air-concepts/test/"
    assert lp.root_for("live") == "/air-concepts/live/"


def test_wrong_region_refused():
    factory, _ = make_factory(PARAMETERS)
    with pytest.raises(lp.Refused, match="region must be"):
        lp.run(BASE + ["--environment", "test", "--region", "us-east-1"],
               session_factory=factory, out=io.StringIO())
