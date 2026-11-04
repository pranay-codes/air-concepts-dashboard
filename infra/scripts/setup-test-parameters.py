#!/usr/bin/env python3
"""Enter Test values for the Air Concepts dashboard into AWS Parameter Store.

Without --apply: lists every key, whether it is already set, and writes nothing.
With --apply: asks for each key that is not set yet. Leave a prompt blank to skip it.
Secret values are hidden as you type. Values are not printed or saved to disk.

Only paths under /air-concepts/test/ are written. Live is not handled by this script.

  python setup-test-parameters.py --profile <name> --expected-account-id 923079246426
  python setup-test-parameters.py --profile <name> --expected-account-id 923079246426 --apply
"""

from __future__ import annotations

import argparse
import getpass
import sys
from collections import Counter
from dataclasses import dataclass
from typing import Optional, Sequence

import boto3
from botocore.exceptions import BotoCoreError, ClientError

ROOT = "/air-concepts/test/"
REGION = "ap-southeast-2"
BLOCKED_SEGMENTS = frozenset({"live", "prod", "production"})
EXIT_OK, EXIT_FAILED, EXIT_REFUSED = 0, 1, 2


class Refused(Exception):
    """A check failed before anything was written."""


@dataclass(frozen=True)
class Key:
    path: str  # relative to ROOT
    kind: str  # SSM type: "String" or "SecureString"
    used_by: str  # where the value goes
    required: bool = True


KEYS = (
    # Backend API. Each value is read as an environment variable at startup.
    Key("backend/db/jdbc-url", "String", "API DATABASE_URL"),
    Key("backend/db/username", "String", "API DATABASE_USER"),
    Key("backend/db/password", "SecureString", "API DATABASE_PASSWORD"),
    Key("backend/supabase/project-ref", "String", "API SUPABASE_TEST_PROJECT_REF"),
    Key("backend/supabase/jwt-issuer-uri", "String", "API SUPABASE_JWT_ISSUER_URI"),
    Key("backend/supabase/jwks-uri", "String", "API SUPABASE_JWKS_URI"),
    Key("backend/cors/allowed-origins", "String", "API CORS_ALLOWED_ORIGINS", required=False),
    Key("backend/xero/client-id", "String", "placeholder, no code reads it yet", required=False),
    Key("backend/xero/client-secret", "SecureString", "placeholder, no code reads it yet", required=False),
    Key("backend/xero/redirect-uri", "String", "placeholder, no code reads it yet", required=False),
    Key("backend/vwork/api-key", "SecureString", "placeholder, no code reads it yet", required=False),
    # Frontend. Vite inlines these at build time, so they reach the browser and are not secret.
    Key("frontend/api-base-url", "String", "frontend VITE_API_BASE_URL"),
    Key("frontend/supabase-url", "String", "frontend VITE_SUPABASE_URL"),
    Key("frontend/supabase-publishable-key", "String", "frontend VITE_SUPABASE_PUBLISHABLE_KEY"),
)


def full_name(key: Key) -> str:
    return ROOT + key.path


def check_name(name: str) -> None:
    if not name.startswith(ROOT) or ".." in name or "//" in name:
        raise Refused(f"{name} is outside {ROOT}")
    if BLOCKED_SEGMENTS & {segment.lower() for segment in name.split("/")}:
        raise Refused(f"{name} names a non-Test environment")


def existing_type(ssm, name: str) -> Optional[str]:
    """Return the SSM type if the name exists. This reads metadata only, never the value."""
    response = ssm.describe_parameters(
        ParameterFilters=[{"Key": "Name", "Option": "Equals", "Values": [name]}], MaxResults=1)
    for item in response.get("Parameters", []):
        if item.get("Name") == name:
            return item.get("Type")
    return None


def ask(key: Key) -> str:
    requirement = "required" if key.required else "optional"
    label = f"{full_name(key)}  [{key.kind}, {requirement}]  {key.used_by}\n  value (blank to skip): "
    try:
        return getpass.getpass(label) if key.kind == "SecureString" else input(label)
    except EOFError:
        return ""


def error_code(err: Exception) -> str:
    if isinstance(err, ClientError):
        return err.response.get("Error", {}).get("Code", "Unknown")
    return type(err).__name__


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Enter Test values for the Air Concepts dashboard into Parameter Store.")
    parser.add_argument("--profile", help="AWS named profile for the Test account.")
    parser.add_argument("--region", default=REGION, help=f"AWS region (default {REGION}).")
    parser.add_argument("--expected-account-id", required=True,
                        help="Stop if the caller is not in this 12-digit account.")
    parser.add_argument("--apply", action="store_true", help="Ask for each key that is not set and write it.")
    parser.add_argument("--overwrite", action="store_true",
                        help="Also ask for keys that already exist, and replace them. A dry run shows them as 'would replace'.")
    parser.add_argument("--kms-key-id",
                        help="Optional KMS key id, alias or ARN for SecureString values. "
                             "Omit to use the AWS default SSM key.")
    return parser


def run(argv: Sequence[str], *, session_factory, ask_fn) -> int:
    args = build_parser().parse_args(list(argv))
    if args.region != REGION:
        raise Refused(f"region must be {REGION}")

    session = session_factory(args.profile, args.region)
    account = session.client("sts").get_caller_identity()["Account"]
    if account != args.expected_account_id:
        raise Refused(f"caller account {account} is not {args.expected_account_id}. Nothing was changed.")
    ssm = session.client("ssm")

    if args.apply:
        print(f"APPLY to account {account}, paths {ROOT}*. Blank skips a key.")
    else:
        print(f"DRY RUN for account {account}, paths {ROOT}*. Nothing will be written. Add --apply to enter values.")

    counts: Counter = Counter()
    for key in KEYS:
        name = full_name(key)
        check_name(name)
        found = existing_type(ssm, name)
        requirement = "required" if key.required else "optional"

        if found and not args.overwrite:
            print(f"  exists        {name}  ({found})  kept")
            counts["exists"] += 1
            continue
        if not args.apply:
            status = "would replace" if found else "not set"
            print(f"  {status:<13} {name}  [{key.kind}, {requirement}]  {key.used_by}")
            counts[status] += 1
            continue

        value = ask_fn(key).strip()
        if not value:
            print(f"  skipped       {name}")
            counts["skipped"] += 1
            continue

        request = {"Name": name, "Value": value, "Type": key.kind, "Overwrite": found is not None}
        if key.kind == "SecureString" and args.kms_key_id:
            request["KeyId"] = args.kms_key_id
        try:
            ssm.put_parameter(**request)
        except (ClientError, BotoCoreError) as err:
            print(f"  failed        {name}  {error_code(err)}")
            counts["failed"] += 1
            continue
        print(f"  written       {name}")
        counts["written"] += 1

    summary = ", ".join(f"{status} {count}" for status, count in counts.items()) or "nothing to do"
    print(("Done. " if args.apply else "Dry run done. ") + summary)
    return EXIT_FAILED if counts["failed"] else EXIT_OK


def _session(profile: Optional[str], region: str):
    return boto3.Session(profile_name=profile, region_name=region)


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        return run(sys.argv[1:] if argv is None else argv, session_factory=_session, ask_fn=ask)
    except Refused as err:
        print(f"REFUSED: {err}", file=sys.stderr)
        return EXIT_REFUSED
    except (BotoCoreError, ClientError) as err:
        print(f"AWS error: {error_code(err)}", file=sys.stderr)
        return EXIT_FAILED


if __name__ == "__main__":
    sys.exit(main())
