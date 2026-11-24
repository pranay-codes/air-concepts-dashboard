"""Tests for infra/scripts/check_migrations.py (INF-006, AGENTS.md section 8).

Requirement: INF-006. No database is contacted.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pytest

_SCRIPT = Path(__file__).with_name("check_migrations.py")
_spec = importlib.util.spec_from_file_location("check_migrations", _SCRIPT)
mig = importlib.util.module_from_spec(_spec)
sys.modules["check_migrations"] = mig
_spec.loader.exec_module(mig)

REAL_MIGRATIONS = Path(__file__).resolve().parents[2] / "supabase" / "migrations"


def reasons(sql):
    return [reason for reason, _ in mig.flagged_statements(sql)]


# --- What is flagged ----------------------------------------------------------------------------

@pytest.mark.parametrize("sql,expected", [
    ("drop table public.foo;", ["DROP"]),
    ("DROP TABLE IF EXISTS public.foo CASCADE;", ["DROP"]),
    ("drop policy p on public.foo;", ["DROP"]),
    ("alter table public.foo drop constraint foo_check;", ["ALTER TABLE ... DROP"]),
    ("alter table public.foo drop column bar;", ["ALTER TABLE ... DROP"]),
    ("truncate public.foo;", ["TRUNCATE"]),
    ("truncate table public.foo restart identity;", ["TRUNCATE"]),
    ("delete from public.foo;", ["DELETE without WHERE"]),
    ("DELETE FROM public.foo\n", ["DELETE without WHERE"]),
])
def test_destructive_statements_are_flagged(sql, expected):
    assert reasons(sql) == expected


@pytest.mark.parametrize("sql", [
    "create table public.foo (id int);",
    "alter table public.foo add column bar text;",
    "alter table public.foo enable row level security;",
    "delete from public.foo where id = 1;",
    "insert into public.foo values (1);",
    "grant select on public.foo to service_role;",
    "set lock_timeout = '5s';",
])
def test_safe_statements_are_not_flagged(sql):
    assert reasons(sql) == []


def test_comments_and_strings_do_not_trigger_flags():
    sql = """
    -- drop table public.old;
    /* truncate public.x; */
    insert into public.notes values ('please do not drop table; delete from here');
    create table public.ok (id int);
    """
    assert reasons(sql) == []


def test_destructive_statement_inside_a_function_body_is_not_split_wrongly():
    sql = """
    create function public.f() returns void language plpgsql as $$
    begin
      delete from public.tmp where id = 1;
    end $$;
    drop table public.old;
    """
    assert reasons(sql) == ["DROP"]


def test_each_flagged_statement_is_reported_once_with_its_text():
    flagged = mig.flagged_statements("truncate public.a;\nfoo bar;\ndrop table public.b;")
    assert [reason for reason, _ in flagged] == ["TRUNCATE", "DROP"]
    assert flagged[1][1] == "drop table public.b"


# --- Reading the dry-run output -----------------------------------------------------------------

def make_migrations(tmp_path, files):
    folder = tmp_path / "migrations"
    folder.mkdir()
    for name, body in files.items():
        (folder / name).write_text(body, encoding="utf-8")
    return folder


def test_pending_migrations_are_found_in_dry_run_output_in_order(tmp_path):
    folder = make_migrations(tmp_path, {
        "20261104030533_initial_schema.sql": "create table public.a (id int);",
        "20261106050000_second.sql": "alter table public.a add column b int;",
        "20261107010000_third.sql": "create table public.c (id int);",
    })
    output = "Would push these migrations:\n • 20261106050000_second.sql\n • 20261107010000_third.sql\nDRY RUN"
    pending, flagged = mig.check(output, folder)
    assert pending == ["20261106050000_second.sql", "20261107010000_third.sql"]
    assert flagged == []


def test_nothing_pending_is_reported_cleanly(tmp_path):
    folder = make_migrations(tmp_path, {"20261104030533_initial_schema.sql": "create table public.a (id int);"})
    pending, flagged = mig.check("Remote database is up to date.", folder)
    assert pending == [] and flagged == []


def test_a_pending_version_without_a_file_is_refused(tmp_path):
    folder = make_migrations(tmp_path, {"20261104030533_initial_schema.sql": "create table public.a (id int);"})
    with pytest.raises(mig.CheckError, match="20269999999999"):
        mig.check("Would push: 20269999999999_ghost.sql", folder)


def test_an_empty_migrations_folder_is_refused(tmp_path):
    folder = tmp_path / "migrations"
    folder.mkdir()
    with pytest.raises(mig.CheckError, match="no migrations"):
        mig.check("anything", folder)


def test_flagged_statements_come_with_the_migration_name(tmp_path):
    folder = make_migrations(tmp_path, {
        "20261104030533_initial_schema.sql": "create table public.a (id int);",
        "20261105000000_cleanup.sql": "drop table public.a;",
    })
    _, flagged = mig.check("20261105000000_cleanup.sql", folder)
    assert flagged == [("20261105000000_cleanup.sql", "DROP", "drop table public.a")]


# --- Command line -------------------------------------------------------------------------------

def run_cli(tmp_path, folder, output, *extra):
    pending = tmp_path / "pending.txt"
    pending.write_text(output, encoding="utf-8")
    return mig.main(["--migrations", str(folder), "--pending-file", str(pending), *extra])


def test_cli_fails_on_destructive_statements_until_acknowledged(tmp_path, capsys):
    folder = make_migrations(tmp_path, {"20261105000000_cleanup.sql": "truncate public.a;"})
    assert run_cli(tmp_path, folder, "20261105000000_cleanup.sql") == mig.EXIT_FLAGGED
    assert "FLAGGED [TRUNCATE]" in capsys.readouterr().out
    assert run_cli(tmp_path, folder, "20261105000000_cleanup.sql", "--acknowledge-destructive") == mig.EXIT_OK


def test_cli_passes_for_safe_migrations(tmp_path):
    folder = make_migrations(tmp_path, {"20261105000000_add.sql": "create table public.a (id int);"})
    assert run_cli(tmp_path, folder, "20261105000000_add.sql") == mig.EXIT_OK


def test_cli_refuses_unknown_versions(tmp_path, capsys):
    folder = make_migrations(tmp_path, {"20261105000000_add.sql": "create table public.a (id int);"})
    assert run_cli(tmp_path, folder, "20269999999999_ghost.sql") == mig.EXIT_BAD_INPUT
    assert "REFUSED" in capsys.readouterr().err


# --- The real migrations ------------------------------------------------------------------------

def test_the_first_live_push_needs_a_human_acknowledgement_for_the_constraint_drops():
    output = "\n".join(path.name for path in sorted(REAL_MIGRATIONS.glob("*.sql")))
    pending, flagged = mig.check(output, REAL_MIGRATIONS)
    assert len(pending) == len(list(REAL_MIGRATIONS.glob("*.sql")))
    # Documented in docs/live-environment-runbook.md: each drops check constraints that the same migration re-adds.
    # 20261123020000 drops them inside a DO block (dynamic SQL), so it is flagged as well.
    # 20261124010000 replaces work_items_stage_check and remaps existing rows; it needs review before a Live release.
    assert {name for name, _, _ in flagged} == {
        "20261106050000_uc5_staff_and_dynamic_entities.sql",
        "20261111010000_uc4_generated_forecast_months.sql",
        "20261123020000_gl_quote_accept.sql",
        "20261124010000_gl_vwork_stage_set.sql",
    }
    assert {reason for _, reason, _ in flagged} == {"ALTER TABLE ... DROP"}
