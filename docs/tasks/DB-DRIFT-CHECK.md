# Task: schema drift check

Status: not started. Follow-up from [ADR-002](../adr/ADR-002-migrations-via-ci.md).

## Why it is needed
- CI is the only permitted path to hosted databases, but nothing detects a change made another way, such as in the Supabase dashboard.
- Drift can make a later migration fail, or make Test and Live behave differently.
- GitHub plan limits leave few server-side controls (ADR-001), so detection matters more.

## Proposed approach
- Run from CI only, reporting without changing any database.
- Use `supabase db diff`: it compares a target database with a shadow database built by applying `supabase/migrations` in a separate container.
- Target Test first. Consider Live only after a human decides.
- Fail the job and report the differences. A human decides the fix, made as a new migration (AGENTS.md §8).

Source: https://supabase.com/docs/reference/cli/supabase-db-diff (read 2026-11-03).

## Open questions
- `--linked` implies `supabase link`, which AGENTS.md §8 forbids for agents. Should CI link, or use `--db-url`?
- Exit codes are not documented. How does the job detect drift? Test this, and consider checking the output.
- The migra engine is documented as missing some changes. Which engine, and is `--strict-coverage` needed?
- Which schemas are in scope? Supabase-managed schemas may add noise.
- Which read-only CI credential is used, and where is it stored given ADR-001?
- How often does it run: schedule, before each Live release, or both? What is the Actions cost?
- Who is told about drift, and how?
