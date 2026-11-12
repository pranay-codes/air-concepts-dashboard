#!/usr/bin/env python3
"""Pre-flight for hosted database migrations (INF-006, AGENTS.md section 8).

Reads the output of `supabase db push --dry-run` (the migrations that would be applied), finds each migration
version in it, checks every one has a file in supabase/migrations/, and scans those files for statements that
need explicit human approval: DROP, TRUNCATE and a DELETE without a WHERE clause. It prints the pending list and
every flagged statement, and exits 1 when anything is flagged unless --acknowledge-destructive is given.

  supabase db push --remote --dry-run | python check_migrations.py --migrations supabase/migrations
  python check_migrations.py --migrations supabase/migrations --pending-file dry-run.txt --acknowledge-destructive

Never connects to a database. Standard library only.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

VERSION = re.compile(r"(?<![0-9])([0-9]{14})(?![0-9])")
EXIT_OK, EXIT_FLAGGED, EXIT_BAD_INPUT = 0, 1, 2


class CheckError(Exception):
    """The dry-run output or the migrations folder cannot be trusted."""


def strip_comments(sql: str) -> str:
    sql = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S)
    return re.sub(r"--[^\n]*", " ", sql)


def split_statements(sql: str) -> List[str]:
    """Split on semicolons that are not inside single-quoted strings or dollar-quoted bodies."""
    statements: List[str] = []
    current: List[str] = []
    i, n = 0, len(sql)
    dollar = None
    in_quote = False
    while i < n:
        ch = sql[i]
        if dollar:
            if sql.startswith(dollar, i):
                current.append(dollar)
                i += len(dollar)
                dollar = None
                continue
            current.append(ch)
            i += 1
            continue
        if in_quote:
            current.append(ch)
            if ch == "'":
                if i + 1 < n and sql[i + 1] == "'":
                    current.append("'")
                    i += 2
                    continue
                in_quote = False
            i += 1
            continue
        if ch == "'":
            in_quote = True
            current.append(ch)
            i += 1
            continue
        if ch == "$":
            match = re.match(r"\$[A-Za-z_]*\$", sql[i:])
            if match:
                dollar = match.group(0)
                current.append(dollar)
                i += len(dollar)
                continue
        if ch == ";":
            statements.append("".join(current).strip())
            current = []
            i += 1
            continue
        current.append(ch)
        i += 1
    tail = "".join(current).strip()
    if tail:
        statements.append(tail)
    return [s for s in statements if s]


def flagged_statements(sql: str) -> List[Tuple[str, str]]:
    """Return (reason, first line of the statement) for each statement that needs approval."""
    flagged: List[Tuple[str, str]] = []
    for statement in split_statements(strip_comments(sql)):
        collapsed = " ".join(statement.split())
        lowered = collapsed.lower()
        reason = None
        if re.match(r"drop\s", lowered):
            reason = "DROP"
        elif re.match(r"truncate\b", lowered):
            reason = "TRUNCATE"
        elif re.match(r"delete\s+from\b", lowered) and not re.search(r"\bwhere\b", lowered):
            reason = "DELETE without WHERE"
        elif re.search(r"\balter\s+table\b.*\bdrop\s+(column|constraint)\b", lowered):
            reason = "ALTER TABLE ... DROP"
        if reason:
            flagged.append((reason, collapsed[:140]))
    return flagged


def local_migrations(directory: Path) -> Dict[str, Path]:
    files: Dict[str, Path] = {}
    for path in sorted(directory.glob("*.sql")):
        match = re.match(r"^([0-9]{14})_", path.name)
        if match:
            files[match.group(1)] = path
    if not files:
        raise CheckError(f"no migrations found in {directory}")
    return files


def pending_versions(dry_run_output: str, known: Dict[str, Path]) -> List[str]:
    """Every 14-digit version mentioned in the dry-run output, in order, without repeats."""
    versions: List[str] = []
    for found in VERSION.findall(dry_run_output):
        if found not in versions:
            versions.append(found)
    unknown = [v for v in versions if v not in known]
    if unknown:
        raise CheckError("the dry run names migrations that have no file in this commit: " + ", ".join(unknown))
    return versions


def check(dry_run_output: str, directory: Path) -> Tuple[List[str], List[Tuple[str, str, str]]]:
    known = local_migrations(directory)
    versions = pending_versions(dry_run_output, known)
    flagged: List[Tuple[str, str, str]] = []
    for version in versions:
        text = known[version].read_text(encoding="utf-8")
        for reason, statement in flagged_statements(text):
            flagged.append((known[version].name, reason, statement))
    return [known[v].name for v in versions], flagged


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--migrations", default="supabase/migrations", type=Path)
    parser.add_argument("--pending-file", type=Path, help="Dry-run output to read instead of standard input.")
    parser.add_argument("--acknowledge-destructive", action="store_true",
                        help="A human reviewed the flagged statements; report them but do not fail.")
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        text = args.pending_file.read_text(encoding="utf-8") if args.pending_file else sys.stdin.read()
        pending, flagged = check(text, args.migrations)
    except (CheckError, OSError) as err:
        print(f"REFUSED: {err}", file=sys.stderr)
        return EXIT_BAD_INPUT

    if pending:
        print("Migrations that would be applied:")
        for name in pending:
            print(f"  {name}")
    else:
        print("No pending migrations.")
    for name, reason, statement in flagged:
        print(f"FLAGGED [{reason}] {name}: {statement}")
    if flagged and not args.acknowledge_destructive:
        print("Destructive statements need human approval. Review them, then re-run with the "
              "destructive_ack input.", file=sys.stderr)
        return EXIT_FLAGGED
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
