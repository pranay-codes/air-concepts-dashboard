#!/usr/bin/env python3
"""Read, validate and export the per-environment configuration in infra/environments/<env>.json (INF-006).

Test and Live differ in data, not in code. Every workflow, composite action and script takes its environment
values from these files through this module, so a difference is one JSON edit and a mix-up is one validation
failure.

  python environment_config.py validate                       # both files, plus the Test-vs-Live invariants
  python environment_config.py export live --require-complete # KEY=VALUE lines for $GITHUB_ENV; refuses placeholders
  python environment_config.py get test ecrRepositoryUri      # one value, for a job that reads the other environment
  python environment_config.py from-stack live --profile p    # print values from the CloudFormation stack outputs
  python environment_config.py from-stack live --profile p --check

Only the standard library is needed for validate and export, so CI and deploy jobs run it without installing
anything. from-stack needs boto3 and is run by a human.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Sequence

ENVIRONMENTS = ("test", "live")
CONFIG_DIR = Path(__file__).resolve().parents[1] / "environments"
PLACEHOLDER = "REPLACE_ME"

REGION = "ap-southeast-2"
# JSON key -> variable name written for workflows and scripts.
EXPORTS = {
    "environment": "ENV_NAME",
    "awsRegion": "AWS_REGION",
    "awsAccountId": "AWS_ACCOUNT_ID",
    "stackName": "STACK_NAME",
    "deployRoleArn": "DEPLOY_ROLE_ARN",
    "instanceId": "INSTANCE_ID",
    "apiHostname": "API_HOSTNAME",
    "frontendUrl": "FRONTEND_URL",
    "frontendBucket": "FRONTEND_BUCKET",
    "cloudFrontDistributionId": "CLOUDFRONT_DISTRIBUTION_ID",
    "ecrRepositoryUri": "ECR_REPOSITORY_URI",
    "ssmPrefix": "SSM_PREFIX",
    "supabaseProjectRef": "SUPABASE_PROJECT_REF",
    "apiMemoryLimit": "API_MEMORY_LIMIT",
    "allowTestOnlyFeatures": "ALLOW_TEST_ONLY_FEATURES",
}
KEYS = tuple(EXPORTS)

# CloudFormation output -> config key, for from-stack.
STACK_OUTPUTS = {
    "ApiInstanceId": "instanceId",
    "FrontendBucketName": "frontendBucket",
    "CloudFrontDistributionId": "cloudFrontDistributionId",
    "FrontendUrl": "frontendUrl",
    "EcrRepositoryUri": "ecrRepositoryUri",
}

HOSTNAME = re.compile(r"^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
PATTERNS = {
    "awsAccountId": re.compile(r"^[0-9]{12}$"),
    "instanceId": re.compile(r"^i-[0-9a-f]{8,17}$"),
    "frontendBucket": re.compile(r"^[a-z0-9][a-z0-9.-]{2,62}$"),
    "cloudFrontDistributionId": re.compile(r"^E[A-Z0-9]{8,20}$"),
    "supabaseProjectRef": re.compile(r"^[a-z0-9]{10,30}$"),
    "apiMemoryLimit": re.compile(r"^[0-9]+[mg]$"),
}
# Values that identify one environment's resources. No value may be shared between Test and Live.
ISOLATED = ("stackName", "deployRoleArn", "instanceId", "apiHostname", "frontendUrl", "frontendBucket",
            "cloudFrontDistributionId", "ecrRepositoryUri", "ssmPrefix", "supabaseProjectRef")
# Fields whose value carries the environment name, so a Test value cannot be pasted into Live unnoticed.
NAMED = ("stackName", "deployRoleArn", "ecrRepositoryUri", "frontendBucket")


class ConfigError(Exception):
    """The configuration is invalid or incomplete."""


def is_placeholder(value) -> bool:
    return isinstance(value, str) and value.startswith(PLACEHOLDER)


def config_path(environment: str, directory: Path = CONFIG_DIR) -> Path:
    if environment not in ENVIRONMENTS:
        raise ConfigError(f"environment must be one of {', '.join(ENVIRONMENTS)}, got '{environment}'")
    return directory / f"{environment}.json"


def load(environment: str, directory: Path = CONFIG_DIR) -> Dict[str, object]:
    path = config_path(environment, directory)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise ConfigError(f"{path.name} is missing") from None
    except json.JSONDecodeError as err:
        raise ConfigError(f"{path.name} is not valid JSON: {err}") from None
    if not isinstance(data, dict):
        raise ConfigError(f"{path.name} must hold one JSON object")
    return data


def problems(environment: str, data: Dict[str, object], *, require_complete: bool = False) -> List[str]:
    """Return every problem found in one environment's configuration."""
    found: List[str] = []
    name = f"{environment}.json"

    missing = [key for key in KEYS if key not in data]
    unknown = [key for key in data if key not in KEYS]
    if missing:
        found.append(f"{name}: missing keys: {', '.join(missing)}")
    if unknown:
        found.append(f"{name}: unknown keys: {', '.join(unknown)}")
    if missing:
        return found

    for key in KEYS:
        value = data[key]
        if key == "allowTestOnlyFeatures":
            if not isinstance(value, bool):
                found.append(f"{name}: allowTestOnlyFeatures must be true or false")
            continue
        if not isinstance(value, str):
            found.append(f"{name}: {key} must be a string")
            continue
        if re.search(r"[\s\x00-\x1f]", value):
            found.append(f"{name}: {key} must not contain spaces or control characters")

    if found:
        return found

    if data["environment"] != environment:
        found.append(f"{name}: environment is '{data['environment']}', expected '{environment}'")
    if data["awsRegion"] != REGION:
        found.append(f"{name}: awsRegion must be {REGION}")
    if data["ssmPrefix"] != f"/air-concepts/{environment}/":
        found.append(f"{name}: ssmPrefix must be /air-concepts/{environment}/")
    if data["stackName"] != f"air-concepts-{environment}-infrastructure":
        found.append(f"{name}: stackName must be air-concepts-{environment}-infrastructure")
    if environment == "live" and data["allowTestOnlyFeatures"] is not False:
        found.append("live.json: allowTestOnlyFeatures must be false")

    for key in KEYS:
        value = data[key]
        if key == "allowTestOnlyFeatures" or key == "environment":
            continue
        if is_placeholder(value):
            if environment != "live":
                found.append(f"{name}: {key} is a placeholder; only live.json may hold placeholders")
            elif require_complete:
                found.append(f"{name}: {key} is still a placeholder ({value}); fill it before deploying")
            continue
        if key == "supabaseProjectRef" and value == "" and environment == "test":
            continue  # Test may leave the project unpinned
        if value == "":
            found.append(f"{name}: {key} must not be empty")
            continue
        pattern = PATTERNS.get(key)
        if pattern and not pattern.fullmatch(value):
            found.append(f"{name}: {key} is not in the expected format")

    host = data["apiHostname"]
    if not is_placeholder(host) and not HOSTNAME.fullmatch(str(host)):
        found.append(f"{name}: apiHostname is not a hostname")
    url = data["frontendUrl"]
    if not is_placeholder(url) and not re.fullmatch(r"https://[a-z0-9.-]+", str(url)):
        found.append(f"{name}: frontendUrl must be an https URL with no path")

    account = data["awsAccountId"]
    role = data["deployRoleArn"]
    if not is_placeholder(role) and not re.fullmatch(rf"arn:aws:iam::{account}:role/[A-Za-z0-9+=,.@_-]+", str(role)):
        found.append(f"{name}: deployRoleArn must be an IAM role ARN in account {account}")
    repo = data["ecrRepositoryUri"]
    if not is_placeholder(repo) and not re.fullmatch(
            rf"{account}\.dkr\.ecr\.{re.escape(REGION)}\.amazonaws\.com/[a-z0-9._/-]+", str(repo)):
        found.append(f"{name}: ecrRepositoryUri must be a repository in account {account}, {REGION}")

    other = "live" if environment == "test" else "test"
    for key in NAMED:
        value = str(data[key])
        if is_placeholder(value):
            continue
        if f"-{other}-" in value or f"/{other}-" in value or value.endswith(f"-{other}"):
            found.append(f"{name}: {key} names the {other} environment")
    return found


def cross_problems(configs: Dict[str, Dict[str, object]]) -> List[str]:
    """Invariants between environments: nothing that isolates one may be shared with another."""
    found: List[str] = []
    test, live = configs.get("test"), configs.get("live")
    if not test or not live:
        return found
    for key in ISOLATED:
        a, b = test.get(key), live.get(key)
        if not a or not b or is_placeholder(a) or is_placeholder(b):
            continue
        if a == b:
            found.append(f"test.json and live.json share {key}; each environment needs its own")
    return found


def validate_all(directory: Path = CONFIG_DIR, *, require_complete: Sequence[str] = ()) -> List[str]:
    found: List[str] = []
    configs: Dict[str, Dict[str, object]] = {}
    for environment in ENVIRONMENTS:
        try:
            data = load(environment, directory)
        except ConfigError as err:
            found.append(str(err))
            continue
        configs[environment] = data
        found.extend(problems(environment, data, require_complete=environment in require_complete))
    found.extend(cross_problems(configs))
    return found


def get_value(environment: str, key: str, directory: Path = CONFIG_DIR) -> str:
    """Return one value from an environment that is valid and distinct from the other one."""
    if key not in KEYS:
        raise ConfigError(f"unknown key '{key}'")
    configs = {env: load(env, directory) for env in ENVIRONMENTS}
    found = problems(environment, configs[environment])
    found.extend(cross_problems(configs))
    if found:
        raise ConfigError("\n".join(found))
    value = configs[environment][key]
    return ("true" if value else "false") if isinstance(value, bool) else str(value)


def export_lines(environment: str, *, require_complete: bool, directory: Path = CONFIG_DIR) -> List[str]:
    """Return KEY=VALUE lines for one environment, after the same checks a Live deploy relies on."""
    configs = {env: load(env, directory) for env in ENVIRONMENTS}
    found = problems(environment, configs[environment], require_complete=require_complete)
    found.extend(cross_problems(configs))
    if found:
        raise ConfigError("\n".join(found))
    data = configs[environment]
    lines = []
    for key, variable in EXPORTS.items():
        value = data[key]
        text = ("true" if value else "false") if isinstance(value, bool) else str(value)
        lines.append(f"{variable}={text}")
    return lines


# --- from-stack (human, needs boto3) -----------------------------------------------------------

def stack_values(outputs: Dict[str, str]) -> Dict[str, str]:
    return {STACK_OUTPUTS[name]: value for name, value in outputs.items() if name in STACK_OUTPUTS}


def compare_with_stack(data: Dict[str, object], values: Dict[str, str]) -> List[str]:
    differences = []
    for key, value in values.items():
        if data.get(key) != value:
            shown = data.get(key)
            differences.append(f"{key}: file has '{shown}', stack has '{value}'")
    return differences


def _from_stack(args) -> int:
    import boto3  # imported here so validate and export need only the standard library

    data = load(args.environment)
    session = boto3.Session(profile_name=args.profile, region_name=str(data["awsRegion"]))
    account = session.client("sts").get_caller_identity()["Account"]
    if account != data["awsAccountId"]:
        raise ConfigError(f"caller account {account} is not {data['awsAccountId']}. Nothing was read.")
    stacks = session.client("cloudformation").describe_stacks(StackName=str(data["stackName"]))["Stacks"]
    outputs = {item["OutputKey"]: item["OutputValue"] for item in stacks[0].get("Outputs", [])}
    values = stack_values(outputs)
    api_url = outputs.get("ApiUrl", "")
    if api_url.startswith("https://"):
        values["apiHostname"] = api_url[len("https://"):]
    if args.check:
        differences = compare_with_stack(data, values)
        if differences:
            print(f"{args.environment}.json does not match stack {data['stackName']}:", file=sys.stderr)
            for line in differences:
                print(f"  {line}", file=sys.stderr)
            return 1
        print(f"{args.environment}.json matches the {data['stackName']} stack outputs")
        return 0
    print(json.dumps(values, indent=2, sort_keys=True))
    return 0


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("validate", help="Check both files and the Test-vs-Live invariants.")
    export = sub.add_parser("export", help="Print KEY=VALUE lines for one environment.")
    export.add_argument("environment", choices=ENVIRONMENTS)
    export.add_argument("--require-complete", action="store_true", help="Refuse REPLACE_ME placeholders.")
    get = sub.add_parser("get", help="Print one value for one environment.")
    get.add_argument("environment", choices=ENVIRONMENTS)
    get.add_argument("key", choices=KEYS)
    stack = sub.add_parser("from-stack", help="Print values from the CloudFormation stack outputs.")
    stack.add_argument("environment", choices=ENVIRONMENTS)
    stack.add_argument("--profile")
    stack.add_argument("--check", action="store_true", help="Compare the file with the stack instead of printing.")
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        if args.command == "validate":
            found = validate_all()
            for line in found:
                print(f"INVALID: {line}", file=sys.stderr)
            if not found:
                print("environment configuration is valid")
            return 1 if found else 0
        if args.command == "export":
            for line in export_lines(args.environment, require_complete=args.require_complete):
                print(line)
            return 0
        if args.command == "get":
            print(get_value(args.environment, args.key))
            return 0
        return _from_stack(args)
    except ConfigError as err:
        print(f"REFUSED: {err}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
