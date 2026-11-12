"""Tests for infra/scripts/environment_config.py and the committed infra/environments/*.json (INF-006).

Requirement: INF-006 (Live environment). Standard library and pytest only; no AWS is contacted.
"""

from __future__ import annotations

import copy
import importlib.util
import json
import sys
from pathlib import Path

import pytest

_SCRIPT = Path(__file__).with_name("environment_config.py")
_spec = importlib.util.spec_from_file_location("environment_config", _SCRIPT)
cfg = importlib.util.module_from_spec(_spec)
sys.modules["environment_config"] = cfg
_spec.loader.exec_module(cfg)

COMMITTED = {env: cfg.load(env) for env in cfg.ENVIRONMENTS}


def complete_live():
    """A fully provisioned Live file, distinct from Test everywhere."""
    live = copy.deepcopy(COMMITTED["live"])
    live.update({
        "instanceId": "i-00000000000000000",
        "apiHostname": "api.example.org",
        "frontendUrl": "https://app.example.org",
        "frontendBucket": "air-concepts-live-infrast-bucket-abc123",
        "cloudFrontDistributionId": "E1234567890ABC",
        "supabaseProjectRef": "liveprojectref1234",
    })
    return live


def write(directory: Path, configs: dict) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    for env, data in configs.items():
        (directory / f"{env}.json").write_text(json.dumps(data), encoding="utf-8")
    return directory


# --- The committed files ------------------------------------------------------------------------

def test_committed_files_are_valid():
    assert cfg.validate_all() == []


def test_committed_files_have_exactly_the_same_keys():
    assert set(COMMITTED["test"]) == set(COMMITTED["live"]) == set(cfg.KEYS)


def test_live_placeholders_are_accepted_by_ci_but_refused_for_a_deploy():
    assert cfg.validate_all() == []
    refused = cfg.validate_all(require_complete=["live"])
    assert refused and all("placeholder" in line for line in refused)


def test_test_never_holds_placeholders():
    data = copy.deepcopy(COMMITTED["test"])
    data["instanceId"] = "REPLACE_ME_TEST_INSTANCE"
    assert any("placeholder" in line for line in cfg.problems("test", data))


def test_live_must_not_allow_test_only_features():
    data = complete_live()
    data["allowTestOnlyFeatures"] = True
    assert any("allowTestOnlyFeatures must be false" in line for line in cfg.problems("live", data))


def test_live_gets_its_own_sizing_not_tests():
    assert COMMITTED["live"]["apiMemoryLimit"] != COMMITTED["test"]["apiMemoryLimit"]


# --- Isolation invariants -----------------------------------------------------------------------

def test_the_only_shared_value_is_the_approved_ecr_repository():
    assert cfg.SHARED_ALLOWED == ("ecrRepositoryUri",)


@pytest.mark.parametrize("key", [k for k in cfg.ISOLATED if k not in cfg.SHARED_ALLOWED])
def test_no_isolating_value_may_be_shared_between_test_and_live(key):
    live = complete_live()
    test = copy.deepcopy(COMMITTED["test"])
    if not test[key]:
        test[key] = live[key] = "samevalue12345"
    else:
        live[key] = test[key]
    found = cfg.cross_problems({"test": test, "live": live})
    assert any(key in line for line in found)


def test_a_complete_live_file_shares_nothing_with_test():
    assert cfg.cross_problems({"test": COMMITTED["test"], "live": complete_live()}) == []


def test_a_test_value_pasted_into_live_is_caught_even_if_other_fields_differ(tmp_path):
    live = complete_live()
    live["instanceId"] = COMMITTED["test"]["instanceId"]
    directory = write(tmp_path / "environments", {"test": COMMITTED["test"], "live": live})
    assert any("instanceId" in line for line in cfg.validate_all(directory))


def test_resource_names_must_not_name_the_other_environment():
    live = complete_live()
    live["frontendBucket"] = COMMITTED["test"]["frontendBucket"]
    assert any("names the test environment" in line for line in cfg.problems("live", live))
    test = copy.deepcopy(COMMITTED["test"])
    test["frontendBucket"] = "air-concepts-live-infrast-bucket-abc123"
    assert any("names the live environment" in line for line in cfg.problems("test", test))


def test_ssm_prefix_and_stack_name_are_fixed_by_environment():
    live = complete_live()
    live["ssmPrefix"] = "/air-concepts/test/"
    live["stackName"] = "air-concepts-test-infrastructure"
    found = cfg.problems("live", live)
    assert any("ssmPrefix" in line for line in found)
    assert any("stackName" in line for line in found)


def test_file_must_declare_its_own_environment():
    data = copy.deepcopy(COMMITTED["test"])
    data["environment"] = "live"
    assert any("environment is 'live'" in line for line in cfg.problems("test", data))


def test_role_and_repository_must_belong_to_the_declared_account():
    live = complete_live()
    live["deployRoleArn"] = "arn:aws:iam::111111111111:role/airconcepts-live-deployer"
    live["ecrRepositoryUri"] = "111111111111.dkr.ecr.ap-southeast-2.amazonaws.com/air-concepts-live-backend"
    found = cfg.problems("live", live)
    assert any("deployRoleArn" in line for line in found)
    assert any("ecrRepositoryUri" in line for line in found)


# --- Shape checks -------------------------------------------------------------------------------

def test_missing_and_unknown_keys_are_reported():
    data = copy.deepcopy(COMMITTED["test"])
    del data["apiMemoryLimit"]
    data["surprise"] = "x"
    found = cfg.problems("test", data)
    assert any("missing keys: apiMemoryLimit" in line for line in found)
    assert any("unknown keys: surprise" in line for line in found)


@pytest.mark.parametrize("key,value", [
    ("apiMemoryLimit", "lots"),
    ("instanceId", "not-an-instance"),
    ("apiHostname", "Not A Host"),
    ("frontendUrl", "http://insecure.example.org"),
    ("frontendUrl", "https://app.example.org/path"),
    ("cloudFrontDistributionId", "abc"),
    ("supabaseProjectRef", "UPPER_case-bad"),
    ("awsAccountId", "12345"),
])
def test_malformed_values_are_rejected(key, value):
    data = complete_live()
    data[key] = value
    assert cfg.problems("live", data), f"{key}={value} was accepted"


def test_values_with_whitespace_or_newlines_are_rejected_so_nothing_can_inject_into_github_env():
    data = complete_live()
    data["apiHostname"] = "api.example.org\nINJECTED=1"
    assert cfg.problems("live", data)


def test_test_may_leave_the_supabase_project_unpinned_but_live_may_not():
    test = copy.deepcopy(COMMITTED["test"])
    test["supabaseProjectRef"] = ""
    assert cfg.problems("test", test) == []
    live = complete_live()
    live["supabaseProjectRef"] = ""
    assert any("supabaseProjectRef must not be empty" in line for line in cfg.problems("live", live))


def test_region_must_stay_sydney():
    data = complete_live()
    data["awsRegion"] = "us-east-1"
    assert any("awsRegion" in line for line in cfg.problems("live", data))


# --- export -------------------------------------------------------------------------------------

def test_export_prints_every_value_for_the_environment():
    lines = cfg.export_lines("test", require_complete=True)
    assert "ENV_NAME=test" in lines
    assert "API_MEMORY_LIMIT=512m" in lines
    assert "ALLOW_TEST_ONLY_FEATURES=true" in lines
    assert "SSM_PREFIX=/air-concepts/test/" in lines
    assert len(lines) == len(cfg.KEYS)


def test_export_for_live_is_refused_while_placeholders_remain():
    with pytest.raises(cfg.ConfigError, match="placeholder"):
        cfg.export_lines("live", require_complete=True)


def test_export_for_live_works_once_complete(tmp_path):
    directory = write(tmp_path / "environments", {"test": COMMITTED["test"], "live": complete_live()})
    lines = cfg.export_lines("live", require_complete=True, directory=directory)
    assert "ENV_NAME=live" in lines
    assert "ALLOW_TEST_ONLY_FEATURES=false" in lines
    assert "SSM_PREFIX=/air-concepts/live/" in lines
    assert "API_MEMORY_LIMIT=1024m" in lines
    # The approved shared ECR repository is the only Test name allowed in the Live export.
    assert not any("/air-concepts/test/" in line or "-test-" in line for line in lines if not line.startswith("ECR_REPOSITORY_URI="))


def test_export_refuses_when_live_shares_a_value_with_test(tmp_path):
    live = complete_live()
    live["cloudFrontDistributionId"] = COMMITTED["test"]["cloudFrontDistributionId"]
    directory = write(tmp_path / "environments", {"test": COMMITTED["test"], "live": live})
    with pytest.raises(cfg.ConfigError, match="share cloudFrontDistributionId"):
        cfg.export_lines("live", require_complete=True, directory=directory)


def test_unknown_environment_is_refused():
    with pytest.raises(cfg.ConfigError):
        cfg.config_path("staging")


def test_cli_validate_succeeds_and_export_refuses_incomplete_live(capsys):
    assert cfg.main(["validate"]) == 0
    assert cfg.main(["export", "live", "--require-complete"]) == 2
    assert "placeholder" in capsys.readouterr().err


# --- from-stack helpers (no AWS) ----------------------------------------------------------------

def test_stack_outputs_map_to_config_keys():
    values = cfg.stack_values({
        "ApiInstanceId": "i-00000000000000000",
        "FrontendBucketName": "bucket-name-here",
        "CloudFrontDistributionId": "E1234567890ABC",
        "FrontendUrl": "https://dxxxx.cloudfront.net",
        "EcrRepositoryUri": "1.dkr.ecr/x",
        "StackName": "ignored",
    })
    assert values == {
        "instanceId": "i-00000000000000000",
        "frontendBucket": "bucket-name-here",
        "cloudFrontDistributionId": "E1234567890ABC",
        "frontendUrl": "https://dxxxx.cloudfront.net",
        "ecrRepositoryUri": "1.dkr.ecr/x",
    }


def test_compare_reports_each_difference_and_nothing_when_equal():
    data = copy.deepcopy(COMMITTED["test"])
    same = {"instanceId": data["instanceId"]}
    assert cfg.compare_with_stack(data, same) == []
    differences = cfg.compare_with_stack(data, {"instanceId": "i-0000000000000000"})
    assert len(differences) == 1 and "instanceId" in differences[0]


# --- get ----------------------------------------------------------------------------------------

def test_get_returns_one_value_after_validating_both_environments(capsys):
    assert cfg.get_value("test", "ecrRepositoryUri").endswith("/air-concepts-test-backend")
    assert cfg.main(["get", "live", "ssmPrefix"]) == 0
    assert capsys.readouterr().out.strip() == "/air-concepts/live/"


def test_get_refuses_when_the_environments_overlap(tmp_path):
    live = complete_live()
    live["instanceId"] = COMMITTED["test"]["instanceId"]
    directory = write(tmp_path / "environments", {"test": COMMITTED["test"], "live": live})
    with pytest.raises(cfg.ConfigError):
        cfg.get_value("test", "instanceId", directory)
