# ADR-003: Supabase Test project for migrations and integration tests

## Status
Accepted. Date: 2026-11-04. Decided by the project owner. Amends the consequences of [ADR-002](ADR-002-migrations-via-ci.md).

## Context
- ADR-002 says CI is the only path to hosted databases and agents apply nothing to them.
- The owner decided against a separate dev Supabase project. All migrations and integration tests run against the Supabase Test project.

## Decision
- There is no dev project. The Supabase Test project is the only hosted database used for migrations and integration tests.
- CI applies new migrations to Test automatically on merge to `main` (`supabase db push`), then checks `supabase migration list`.
- After deploy, CI runs schema integration tests against Test. They are read-only (`supabase db query --remote --read-only`) and write nothing.
- Pull requests run the same integration tests on a throwaway database in CI, before anything reaches Test.
- Credentials live only in GitHub environment secrets, never in the repo, logs or chat.
- Agents do not connect to Test or any other hosted database. CI does.
- Live is still changed only through the release workflow.

## Consequences
- A migration reaches Test after merge, so Test is always the first hosted database a migration touches. Migrations stay forward-only and reviewed (AGENTS.md §8).
- Integration tests on Test are read-only, so they cannot change data. Destructive tests stay on disposable databases (AGENTS.md §6).
- The `test` GitHub environment and its three secrets must exist before the deploy job can run.
- Only the schema is tested against Test. Seed data is not loaded there, so there are no synthetic rows to assert on.
