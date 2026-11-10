# UC4 delivery contract and evidence ledger

Goal: implement FR-UC04-01..21 and AC-1..29 from UC4.md through ordered slices 0–16.
Constraints: approved sources only; no Notion reads, Live, deployments, hosted database connections or Xero accounting writes; no new dependency except POI 5.4.1.
Failure modes: entity leakage, stale responses, erased adjustments, silent workbook truncation, mismatched dashboard/Excel totals, false verification claims.
Definition of Done: every requirement/AC has evidence; named tests observed red then green; applicable checks pass; snapshot/types match; stacked PRs exist; Claude Code review and human Test acceptance recorded, with any unobserved check explicitly not run.

## Decisions (10 November 2026)

- Ruling: the human subsequently instructed "complete the dev work fully than commit". Complete local development before commits; per-slice publication no longer blocks implementation. Keep slice plans/evidence in this ledger and defer Git publication until development is complete. Earlier publication blockers remain historical evidence only.

## Slice 1 plan

Files: migration 20261110010000_uc4_forecast_documents_and_sync.sql and Uc4SchemaTest.java. Create mirror, sync runs and audit tables with explicit RLS/revokes/grants, numeric(18,2), restrictive FKs and code-only diagnostics. Add remaining-limit diagnostic columns per spike S6. Tests: FR-UC04-08 schema privileges, checks and FK restrictions on disposable PostgreSQL; Docker-backed execution not run while Docker is stopped. Run backend verify where dependency access allows; report infrastructure failure separately from test failure.

Tests written before migration. Native mvnw.cmd cannot start in this environment (wrapper error: Cannot index into a null array); Git Bash wrapper remains the intended runner. Docker-backed tests not run: Docker stopped. No destructive statements in this migration. Audit receives select/insert only (append-only) rather than the broader baseline update grant.

## Slice 2 plan

Files: xero/ForecastSource.java, XeroForecastClient.java, XeroConnectionService.java, config/XeroConfig.java and mock transport tests. Parse expected/planned ms-dates, validate all paging/counts/types/duplicate IDs and safe rate-limit diagnostics, with shared tenant pacing and no automatic retry. Manual access remains user-authorised; scheduler gets a separate internal systemAccess entry point checked against non-archived connected entities. Tests include 429, timeout, wrong types and malformed paging; no real organisation calls.

## Slice 3 plan

Files: forecast/sync repository, service, scheduler, configuration and ForecastSyncController, plus named service/HTTP tests. Fetch all pages and missing settlement reads before a per-type transaction; fence stale runs and remapped tenants; persist safe failures and numeric limits. No incremental optimisation until confirmed. OpenAPI/type regeneration after the controller is implemented. Reuse existing health, authorisedAccess and systemAccess. No provider calls in tests.

## Slice 4 plan

Extend Uc4SchemaTest before writing migration 20261110020000_uc4_forecast_assumptions.sql. Six backend-only tables: adjustments, splits, periods, manual items, staffing and exports. Restrict foreign keys, positive money, first-of-month dates, inclusive end bounds, explicit privileges; DELETE only on split lines. Docker-backed tests remain not run locally.

## Slice 5 plan and evidence

Pure forecast/calc records and calculator; ForecastCalculatorTest written first and observed RED (missing calculator/types), then implemented. Covers missing opening balance, carry-forward, 12 months, beyond-window documents, overrides, Xero dates, partial amounts, exclusions, splits, overdue and GST treatment. No Docker or provider needed.

Maven recovery: default wrapper could not resolve parent over restricted network; Windows wrapper failed at startup. Copied existing artifact cache into ignored backend/target/uc4-m2, with tools.jackson cache completed separately. Offline Git Bash invocation `./mvnw -o -q -B -Dmaven.repo.local=target/uc4-m2 -Dtest=XeroForecastClientTest,ForecastSyncServiceTest test` passed (exit 0). No dependency version changed. Earlier RED for slices 1–3 was blocked before test compilation, so no pre-implementation red result is claimed for those slices.

## Slice 6 plan

ForecastAccess, validated period/manual/staffing services and JDBC assumption repository; explicit DTOs, audit records and controller routes. Tests first: opening balance and first-day validation, item validation matrix, inactive and STANDARD_USER denial, foreign IDs return 404. Mutations and changed-column audits share one transaction; no staffing in aggregate DTOs. HTTP/API snapshot/type checks follow controller implementation. Local Maven runs explicitly exclude PostgresConnectivityTest,Uc4SchemaTest,Uc5SchemaTest,InvoiceAssignmentRepositoryTest,StaffRepositoryTest,XeroConnectionRepositoryTest; none will be executed without Docker.

## Slice 7 plan

Adjustment service/repository/controller, split replacement and conflict resolution; sync updates conflict fields only. Lock documents inside mutations; recheck outstanding and split sums under lock. Audit changed fields only and retain schedule lines on provider changes. Tests: exclusion reason, split sum/empty schedule, foreign IDs, settled conflict, and pure conflict policy. Preserve date override when omitted from exclusion requests; explicit null clears it.

## Slice 8 plan

ForecastDatasetLoader with a repeatable-read stored-data snapshot, ForecastService, forecast/month-items/documents/combined controllers. Every read checks role/entity. Combined DTOs expose only totals and entity summaries. Tests: authorised entity set, archived entity failure, incomplete aggregates, month detail and decimal strings. Ruling: reject non-NZD documents with FORECAST_CURRENCY_UNSUPPORTED until an approved conversion rule exists; summing native currencies would violate the UI specification. Default cash currency is a named constant. Beyond-window totals cover dated document contributions and one-off manual items; open-ended monthly assumptions have no finite lifetime total and are expanded only for the selected 12 months.

## Slice 9 plan

POI 5.4.1 only; in-memory WorkbookValidator, WorkbookPopulator and independent reconciliation. Tests first against the scrubbed v1 fixture: structural/header/formula rejection, size/type/OOXML/zip/external-link guards; formula/style preservation; 200/201 capacity; manual/staff overflow warnings; all staffing grid cells overwritten; numerical equivalence and deliberate mismatch. Fixture scrub: columns A/B/D rows 10–15 and 23–28 (36 cells), exact addresses and original/fixture SHA-256 in UC4-fixture-scrub.json; original unchanged. Human scrub confirmation requested asynchronously; ready-for-review gate remains pending.

## Slice 10 plan

In-memory multipart export service/controller and insert-only export audit. Validate before loading stored data; do not fetch Xero. Return no-store, nosniff, attachment filename from validated entity code and NZ generation time, reconciliation flag and encoded warnings for the download prompt. Tests first for headers, invalid upload with no repository mutation and STANDARD_USER denial. Keep multipart payload below in-memory threshold; reject oversize before parsing. Expose only required response headers through CORS.

Workbook population tests passed (5/5). Validator diagnostic found POI retained the original inline-string header when the test used setCellValue without clearing it; serialize/reopen proved the intended mutation was absent. The test now blanks then writes the changed header and asserts the mutation survived serialization before testing rejection. Production populator already blanks cells before writing.

## Slices 11–15 frontend plan

Reuse DashboardApi/ApiClient, useResource/useAction, Feedback, format and Modal. Add generated-type boundary guards, lazy Forecast route, responsive table/cards and shared entity context. Focused edits for opening balance, dates, splits/exclusions/conflicts, manual/staff lines; server-authoritative refresh after confirmed changes. Export stays in memory until Download, with Discard and reconciliation warnings. Combined selection exists only in Forecast, with no record actions in aggregate context. Protect dirty forms and key requests/dialogs by user, entity, generation and month. Component tests first; Chromium journey and widths in slice 16. No new frontend dependencies.

- Ruling: forecast retains IDs; preserves the approved pack's traceability. Supplier-bill numbering remains outside this delivery.
- Ruling: authorisation A follows the human's delegated decisions; slice plans/contracts remain reviewable in PR drafts. No new authority for prohibited environments or operations.
- Ruling: missing original Notion source URLs are provenance gaps; no Notion access is needed or permitted.
- Ruling: AC-25 clears Staffing P19:BN30; preserve D30:O30 total formulas. All D23:O29 cells are written or blanked.
- Ruling: try connected GitHub tools after local ref locks and CLI network are denied. The mutation was rejected by automatic approval review, so no publication route is available in this session. Stop at the slice 0 publication boundary; do not implement unverified later slices on main.

## Slice 0 PR body draft

Plan: publish the supplied spec, technical plan, template map and four spike notes; record delegated decisions in UC4.md and AGENTS.md. Preserve unrelated local settings and the original unsanitised workbook outside Git.
Tests: documentation-link/gate checks and git diff --check; run available check-all baseline. No production behaviour changes and no red/green test applies to this documentation slice.
Requirements: FR-UC04-01..21 planning; AC-1..29 traceability only, no implementation acceptance claimed.
Security: no credentials, source workbook or personal staffing data published; no hosted calls.
Reviewing agent: Claude Code pending. Usage: tool-reported exact per-slice usage unavailable; initial source/gate review only.

## Evidence

- Local branch creation: failed, cannot create .git/refs/heads/feat/fr-uc04-00-spec-gates.lock (permission denied).
- gh pr list: failed, network socket access denied.
- Connected GitHub: repository write permission confirmed; open-PR search returned none.
- Connected GitHub create_branch: rejected with "MCP tool call requires approval, but approval policy is never". No remote branch/commit/PR created.
- `bash ./scripts/check-all.sh` using Git for Windows Bash: FAILED in backend verify before tests; Spring Boot parent POM 4.1.2 unavailable because Maven Central socket access is denied. Frontend and api:check stages did not run.
- `docker info --format '{{.ServerVersion}}'`: FAILED because access to the Docker configuration is denied. The human subsequently confirmed Docker is not running locally (10 November 2026). Disposable PostgreSQL tests: not run, Docker not running; do not attempt Docker startup.
- `git diff --check`: passed for tracked edits. PowerShell source/traceability check: passed, all nine named source/ledger files exist and FR-UC04-01..21 plus AC-1..29 remain present. This is document completeness evidence only, not implementation acceptance.
- Source inspection: UC4 spec, plan, template map, all four UC4 spikes and UI/UX specification read; no Notion read.
- Initial slice 0 inspection: implementation had not started. See current implementation evidence below; independent review, CI and human Test acceptance remain pending.

## Publication handoff

Historical slice-0-only handoff below. Superseded for development scope by the human instruction to finish all local development before commits. Current implementation/acceptance evidence and the 17 local PR body drafts in `docs/plans/uc4-pr-drafts/` govern the current handoff. No old documentation-only body should be published as though it describes the integrated implementation.

The human created the local branch; verified current branch is feat/fr-uc04-00-spec-gates at 981adc777af42aea8ecf133333f1ae5973a75e4e. Existing uncommitted files are preserved. The earlier branch-creation failure above is historical; commit/push access still needs verification.
Staging the eleven explicitly named documentation files failed: cannot create .git/index.lock (permission denied). The conditional commit was not attempted; nothing was staged by that command. The human must stage/commit/push this concrete slice and open its PR in an unrestricted terminal before the ordered stack can continue.

Intended branch: feat/fr-uc04-00-spec-gates, base main at 981adc777af42aea8ecf133333f1ae5973a75e4e.
Intended commit: docs(FR-UC04-01): record UC4 scope and delegated gate decisions.
Intended PR title: docs(FR-UC04-01): establish UC4 forecast implementation contract.
Include only AGENTS.md, docs/specs/UC4.md, docs/specs/UC4-template-map.md, docs/plans/UC4.md, docs/plans/UC4-delivery.md, the four docs/spikes/uc4-*.md notes, and the existing UC4 index changes in docs/specs/README.md and docs/spikes/README.md after reviewing their diff.
Exclude .claude/settings.local.json and docs/ACSL Cashflow Forecast Template - Cleaned.xlsx (the latter is not scrubbed).
The documentation bootstrap exceeds the approximate 300-line PR guideline because the supplied approved sources are currently untracked; do not omit the authoritative requirements to meet a size target.

PR evidence: documentation-only; implementation criteria remain unproven; check-all failed before tests because dependency network access is denied; Docker-backed tests not run; no migration or API changes.
Security checklist: no hosted/Live operations, no provider calls, no credentials or unsanitised workbook published. Migration checklist: not applicable. Reviewing agent: Claude Code pending. CI and Test acceptance: not run.
Rulings to confirm at review: forecast retains IDs; authorisation A; current stack; provenance-only URL gap; staffing clearing precedence. These are delegated implementation decisions, not new approval pauses.

## Current implementation evidence

- The later human instruction to finish development before commits supersedes the initial publication stop. Slices 1–15 are implemented locally; slice 16 verification/documentation is underway. No commit or PR exists yet.
- Baseline no-Docker backend verify passed: 206 tests in 43 suites, zero failures/errors/skips, with six existing/new container-backed classes excluded at that point. The subsequently added ForecastDocumentRepositoryTest is also explicitly excluded locally and is intended for disposable CI only.
- Targeted workbook tests passed, including full formula/style preservation outside mapped ranges, 200/201 capacity, staffing/manual overflow warnings, cleared staffing cells and complex split/override/overdue/exclusion reconciliation. Export HTTP tests verify filename, cache/type/response headers, CORS access and a single export-audit call.
- Full frontend Vitest run passed: 88 tests in 19 files before the two additional response-boundary regressions. Those two tests were observed RED then GREEN. Targeted typecheck and ESLint pass; final lint/build/regression evidence is recorded in the coverage ledger.
- Chromium UC4 journey passed at 320,390,768,1024,1440 px plus Standard User navigation denial: 6/6. Synthetic browser boundaries prove UI flow, not real Xero/database integration. Screenshots are in ignored output/playwright/test-results.
- Manual/staff partial edits retain omitted fields, allow an explicit null end month, and lock current rows before merging. The manual sync rate-limit HTTP regression was observed RED (502 instead of required 429) before the forecast-specific response correction.
- Ruling: add GET /forecast/documents/{id} for bounded month-detail editing. It checks role/entity and returns 404 for foreign IDs; it avoids scanning all document pages to open an adjustment.
- Fixture scrub confirmation: requested; pending. Claude Code review, hosted Test acceptance and CI: pending/not run. No Docker command, hosted database connection, real Xero call, deployment or Live operation was performed during implementation.

## Acceptance pass, 10 November 2026

Follow-up AC-29 verification plan: extend `frontend/src/forecast/Forecast.test.tsx` to prove aborted entity/period requests cannot publish late results, and extend `frontend/e2e/uc4-forecast.spec.ts` for failed-load Retry recovery. These close the explicit switch/error evidence gap in the coverage ledger. Test existing generation/abort behaviour before changing implementation; no backend, database or provider access is needed. Re-run focused tests, frontend checks and Chromium after any correction.

- Definition of Done remains the full plan section 15, not local test success alone. Slice 16 coverage and operations artifacts now exist. Formal review, CI, human scrub and deployed Test acceptance remain unproven.
- Responsive month detail regression observed RED at 320 px; fixed separate view below 1200 px, desktop side pane and return focus. Chromium five-width journey passed.
- Sync access-refresh/remap regressions observed RED (2 failures), then GREEN: fresh access before every provider request and stable entity/tenant/grant identity throughout the run. No old-mapping request is sent after detecting a change. Safe code FORECAST_SYNC_CONNECTION_CHANGED documented.
- Expanded HTTP evidence covers all 21 route/method denial pairs for Standard User/inactive accounts, foreign document/manual/staff/split IDs, split/exclusion validation, settled-document conflict, stale/failed sync metadata, month bucketing, aggregate privacy/unknown entity and both conflict resolutions.
- Added compressed 51 MiB inflation rejection and real POI reconciliation mismatch HTTP flow: file still returned with false reconciliation header, warnings containing both values and one false export audit record.
- Final `./scripts/check-all.sh` passed with offline-cache MAVEN_ARGS and seven explicit Docker-backed class exclusions. Backend 225 tests / 50 suites, zero failures/errors/skips among executed classes; frontend 90 tests; lint/typecheck/build and api:check passed. No tests removed or weakened.
- `node node_modules/@playwright/test/cli.js test --project=chromium`: 26 passed, one existing local-API opt-in test skipped (local API not started). UC4 manual/staff create/edit/deactivate/show-inactive and combined-mode journey passed in addition to the five widths. Browser boundaries are synthetic, not database/Xero integration.
- `docs/requirements/UC4-coverage.md` records each of 21 FRs and 29 ACs, commands, limits and all not-run checks. `docs/uc4-forecast-operations.md` records schedule, rerun, change bound, rate limits, tier/connection limits, security and rollback.
- Local PR body drafts for every slice 0–16 are in `docs/plans/uc4-pr-drafts/`; each names intended stack base, requirements, ACs, plan, evidence, security/migration notes, usage and Claude Code pending. They do not claim individually verified commit boundaries or open PRs.
- Publication recheck after local development/checks: `git add -- docs/requirements/UC4-coverage.md` again failed with `.git/index.lock: Permission denied`. `git diff --cached --stat` was empty; no commit was attempted. Current blocker is actual Git index write access, not a missing branch. The connected GitHub mutation route had already been rejected by automatic approval review (`approval policy is never`); no alternate mutation bypass was attempted.
- Follow-up AC-29 evidence: two entity/period cancellation tests passed against existing code, proving aborted requests cannot publish their late responses. Failed-load Retry browser test passed. Frontend suite now 92/92; frontend script (lint/typecheck/tests/build) and api:check passed. Fresh serial full Chromium suite: 27 passed, one unchanged local-API opt-in skip. All five detail screenshots captured; 320/1440 inspected, confirming separate mobile view and desktop side pane. An overlapping preview attempt failed with a port collision and disturbed two browser tests; both processes ended before the clean serial rerun. No product change, test weakening or timeout increase used.
- Publication audit: current HEAD remains 981adc7 and staged diff is empty. `.git` ACL inspection shows explicit Deny entries consistent with the observed index-lock failure; ACLs were not changed. Read-only connected GitHub search for open PRs in pranay-codes/air-concepts-dashboard returned none. No commit/PR/review/CI result is inferred from the local PR drafts.
