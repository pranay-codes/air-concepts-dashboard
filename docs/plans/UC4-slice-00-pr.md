Establish the approved UC4 forecasting scope and record the decisions delegated by the human on 10 November 2026. The forecast keeps FR-UC04-01..21; authorisation A applies to implementation. Existing Live, deployment, hosted-database and Xero accounting-write restrictions remain.

Plan: publish the supplied UC4 spec, technical plan, template map and spike notes; record gate decisions and the delivery contract. This documentation bootstrap exceeds the approximate 300-line guideline because these authoritative sources were previously untracked.

- Base: main, commit 981adc777af42aea8ecf133333f1ae5973a75e4e. First slice; subsequent PRs stack on this branch.
- Requirements: FR-UC04-01..21; AC-1..29 traced in the spec, implementation acceptance not yet claimed.
- Validation: git diff --check passed; named source/traceability check passed (21 requirements, 29 criteria). Documentation-only; no applicable failing implementation test.
- check-all.sh: failed before tests because Maven Central access was denied resolving Spring Boot parent 4.1.2. Frontend and API stages not run.
- Docker-backed tests: not run, Docker not running locally; human confirmed. CI and Test acceptance: not run.
- Security: no credentials, personal staffing values or unsanitised workbook included. No provider calls, Live, deployment or hosted database connections.
- Migration checklist: not applicable, no migrations in this slice.
- Reviewing agent: Claude Code pending; no self-approval.
- Usage: source and gate review plus documentation preparation; exact per-slice accounting unavailable.
- Decisions for review: preserve forecast IDs; authorisation A; existing stack; missing original source URLs treated as provenance gaps; AC-25 governs staffing clear ranges.

The original workbook and .claude/settings.local.json remain local and excluded. G5 still requires confirmation of the concrete scrubbed fixture before slice 9 is ready.
