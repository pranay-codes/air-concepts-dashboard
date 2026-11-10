# UC4 slice 06: assumptions-api

Branch: `feat/fr-uc04-06-assumptions-api`. Base: `feat/fr-uc04-05-calculator`. This PR is stacked on the preceding open PR branch (slice 0 bases on main). Local body draft only: branch/commit/PR publication is not yet verified.

## Plan recorded before publication

ForecastAccess/validation, assumption repository/services/controllers and API contract.

Files and implementation plan: see `docs/plans/UC4.md` section 12 slice 6 and `docs/plans/UC4-delivery.md`. Test targets: ForecastPeriodServiceTest,ManualItemServiceTest,StaffingServiceTest,ForecastEndpointTest.

Requirements: FR-UC04-03,04,07,08. Acceptance criteria: AC-8,10,11,20,21. Full evidence and limitations: `docs/requirements/UC4-coverage.md`.

## Test evidence

The integrated worktree passed `./scripts/check-all.sh` with `MAVEN_ARGS` set to offline cache plus seven explicit container-class exclusions. Backend 225 tests; baseline frontend 90 tests; lint/typecheck/build and api:check passed. Follow-up frontend verification passed 92 tests plus lint/typecheck/build and api:check, including entity/period cancellation evidence. `node node_modules/@playwright/test/cli.js test --project=chromium`: 27 passed in a clean serial run, one existing local-API opt-in check skipped; failed-load Retry recovery and detail screenshots included. Slice 0 is documentation only; executable results describe the integrated tree. Individual stacked commit boundaries still need their own verification and CI; this draft does not claim those have run.

Docker/disposable-PostgreSQL tests, real Xero, hosted database, deployed Test, CI, screen-reader acceptance and representative field performance: not run. No Docker command or prohibited environment access used.

## Security checklist

Server role/entity checks; foreign records 404; Standard User denied before processing; staffing absent from aggregate record DTOs; systemAccess scheduler-only; server-only tokens; code-only provider diagnostics and changed-column audits. Upload size/OOXML/ZIP guards, memory-only processing and safe export headers apply where relevant. Shared security tests and implementation are reviewed against plan section 14; formal independent sign-off pending.

## Migration checklist

No schema file in this slice; migrations remain forward-only.

Reviewing agent: Claude Code **pending**; author did not self-approve. Human Test acceptance pending. Usage: sustained integrated implementation and acceptance work; exact per-slice token allocation unavailable. Human delegated routine decisions, authority A, forecast ID retention and approved defaults; no new dependency beyond POI 5.4.1.
