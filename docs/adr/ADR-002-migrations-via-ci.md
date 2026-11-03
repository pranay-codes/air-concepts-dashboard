# ADR-002: Migrations via CI

## Status
Accepted. Date: 2026-11-03. Decision directed by the project owner.

## Context
- Hosted Supabase Test and Live must not drift through ad hoc changes.
- Migration rules: AGENTS.md §8.

## Decision
- CI is the only path to hosted databases.
- Agents write migration files only.
- One tool and one migration history: Supabase CLI inside CI. No Flyway.

## Consequences
- Every schema change is a reviewed file in `supabase/migrations`.
- Agents apply nothing to hosted databases. The only hosted access is the read-only exception in CLAUDE.md.
- Live migrations run only inside the human-triggered release (ADR-001).
- Migration review is procedural on the current GitHub plan (ADR-001).
- Changes made outside CI would go unnoticed until drift checking exists.
- Follow-up: schema drift checking, see [DB-DRIFT-CHECK.md](../tasks/DB-DRIFT-CHECK.md).
