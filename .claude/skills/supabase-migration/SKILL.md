---
name: supabase-migration
description: Use when a task needs a database schema change, to write a migration file in supabase/migrations without touching any database.
---

# supabase-migration

Writes files only. Rules: AGENTS.md §8. Decision: [ADR-002](../../../docs/adr/ADR-002-migrations-via-ci.md).

## Scope
- Never connect to any hosted Supabase project. This includes read-only MCP.
- Never run `db push`, `link` or `migration repair`, or `db reset` against a linked project.
- Human contract review comes first (AGENTS.md §5).

## Steps
1. Confirm an approved spec pack and plan cover the change. If not, stop and ask.
2. Create `supabase/migrations/<timestamp>_<name>.sql` with `supabase migration new <name>`, which works locally. If the CLI is unavailable, name the file by hand: 14-digit UTC timestamp, for example `20261103120000_add_example.sql`.
3. Write forward-only SQL. Never edit an existing migration.
4. Apply every AGENTS.md §8 rule to new tables and risky statements.
5. Test only on a disposable PostgreSQL (AGENTS.md §6).
6. List destructive statements as below.

## Expand and contract example
Rename `bills.ref` to `bills.reference` over separate releases.

```sql
-- Migration 1, expand
set lock_timeout = '5s';
alter table public.bills add column reference text;

-- Migration 2, backfill after new code writes both columns
update public.bills set reference = ref where reference is null;

-- Migration 3, contract after no code reads ref (needs human approval)
set lock_timeout = '5s';
alter table public.bills drop column ref;
```

Example only. Not a real table.

## End every run with
```
Destructive statements for human approval:
- <file>:<line> <statement>
```
- Include every DROP, TRUNCATE and unscoped DELETE.
- Also include unscoped UPDATE and anything that loses data.
- If there are none, write "None".
