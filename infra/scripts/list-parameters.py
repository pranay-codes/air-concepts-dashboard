#!/usr/bin/env python3
"""List Air Concepts Parameter Store entries for Test, Live or both. Read-only.

  python list-parameters.py --profile <name> --expected-account-id 923079246426 --environment test
  python list-parameters.py --profile <name> --expected-account-id 923079246426 --environment live
  python list-parameters.py --profile <name> --expected-account-id 923079246426 --environment all

What is printed:
  String and StringList parameters: name, type and value.
  SecureString parameters: name and type only. Their values are never requested, decrypted or printed.

Options:
  --format text|json   Table-style text (default) or JSON.

AWS calls made: sts:GetCallerIdentity and ssm:GetParametersByPath with WithDecryption=False. Nothing is written.
Test and Live share one AWS account, so the environment is selected by the /air-concepts/<env>/ prefix.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from typing import Callable, List, Optional, Sequence

import boto3
from botocore.exceptions import BotoCoreError, ClientError

REGION = "ap-southeast-2"
APP_ROOT = "/air-concepts/"
ENVIRONMENTS = ("test", "live")
EXIT_OK, EXIT_FAILED, EXIT_REFUSED = 0, 1, 2
SECURE_TYPE = "SecureString"
HIDDEN = "(SecureString: value not shown)"


class Refused(Exception):
    """A check failed before any read was made."""


@dataclass(frozen=True)
class Entry:
    name: str
    kind: str
    value: Optional[str]  # None for SecureString


def root_for(environment: str) -> str:
    if environment not in ENVIRONMENTS:
        raise Refused(f"unknown environment {environment!r}; use test, live or all")
    root = f"{APP_ROOT}{environment}/"
    if not root.startswith(APP_ROOT) or ".." in root or "//" in root:
        raise Refused(f"{root} is outside {APP_ROOT}")
    return root


def targets(choice: str) -> List[str]:
    return list(ENVIRONMENTS) if choice == "all" else [choice]


def list_environment(ssm, root: str) -> List[Entry]:
    """Read every parameter under root. SecureString values are never requested."""
    entries: List[Entry] = []
    paginator = ssm.get_paginator("get_parameters_by_path")
    pages = paginator.paginate(Path=root, Recursive=True, WithDecryption=False)
    for page in pages:
        for item in page.get("Parameters", []):
            name, kind = item["Name"], item["Type"]
            if kind == SECURE_TYPE:
                entries.append(Entry(name, kind, None))
            else:
                entries.append(Entry(name, kind, item.get("Value", "")))
    entries.sort(key=lambda entry: entry.name)
    return entries


def render_text(environment: str, root: str, entries: List[Entry]) -> str:
    lines = [f"== {environment} ({root}) : {len(entries)} parameter(s) =="]
    if not entries:
        lines.append("  (none)")
    for entry in entries:
        shown = HIDDEN if entry.value is None else entry.value
        lines.append(f"  {entry.name}  [{entry.kind}]  {shown}")
    return "\n".join(lines)


def render_json(results: dict) -> str:
    payload = {
        env: {
            "root": data["root"],
            "parameters": [
                {"name": e.name, "type": e.kind,
                 "value": None if e.value is None else e.value,
                 "valueHidden": e.value is None}
                for e in data["entries"]
            ],
        }
        for env, data in results.items()
    }
    return json.dumps(payload, indent=2)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="List Air Concepts Parameter Store entries (read-only).")
    parser.add_argument("--profile", help="AWS named profile.")
    parser.add_argument("--region", default=REGION, help=f"AWS region (default {REGION}).")
    parser.add_argument("--expected-account-id", required=True,
                        help="Stop if the caller is not in this 12-digit account.")
    parser.add_argument("--environment", required=True, choices=("test", "live", "all"),
                        help="Which environment to list, or all.")
    parser.add_argument("--format", default="text", choices=("text", "json"), help="Output format.")
    return parser


def run(argv: Sequence[str], *, session_factory: Callable, out=sys.stdout) -> int:
    args = build_parser().parse_args(list(argv))
    if args.region != REGION:
        raise Refused(f"region must be {REGION}")

    selected = targets(args.environment)
    roots = {env: root_for(env) for env in selected}

    session = session_factory(args.profile, args.region)
    account = session.client("sts").get_caller_identity()["Account"]
    if account != args.expected_account_id:
        raise Refused(f"caller account {account} is not {args.expected_account_id}. Nothing was read.")
    ssm = session.client("ssm")

    results = {env: {"root": root, "entries": list_environment(ssm, root)} for env, root in roots.items()}

    if args.format == "json":
        out.write(render_json(results) + "\n")
    else:
        blocks = [render_text(env, data["root"], data["entries"]) for env, data in results.items()]
        out.write("\n\n".join(blocks) + "\n")
    return EXIT_OK


def _session(profile: Optional[str], region: str):
    return boto3.Session(profile_name=profile, region_name=region)


def error_code(err: Exception) -> str:
    if isinstance(err, ClientError):
        return err.response.get("Error", {}).get("Code", "Unknown")
    return type(err).__name__


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        return run(sys.argv[1:] if argv is None else argv, session_factory=_session)
    except Refused as err:
        print(f"REFUSED: {err}", file=sys.stderr)
        return EXIT_REFUSED
    except (BotoCoreError, ClientError) as err:
        print(f"AWS error: {error_code(err)}", file=sys.stderr)
        return EXIT_FAILED


if __name__ == "__main__":
    sys.exit(main())
