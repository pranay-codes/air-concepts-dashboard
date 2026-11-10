# UC4 coverage and acceptance evidence

This ledger covers the approved `docs/specs/UC4.md`, technical plan and template map. Implementation is local and uncommitted. Evidence below distinguishes executable unit/HTTP/browser proof from database, provider and human acceptance. No Notion source was read.

## Verification contract

Done means every FR and AC is implemented, the named tests and required checks pass, coverage gaps are explicit, Claude Code has reviewed the changes, CI is green, the scrubbed workbook is confirmed, and human Test acceptance is recorded. Local synthetic tests alone do not satisfy the full definition of done.

Constraints: no Docker commands or container-backed execution locally, no hosted database connections, no real Xero organisation, no accounting writes, no deployments and no Live access. Failures must preserve prior data and must not expose another entity's records or provider secrets.

## Requirement evidence

| Requirement | Implementation and local evidence | Remaining evidence |
|---|---|---|
| FR-UC04-01 | XeroForecastClient ACCREC transport tests; ForecastSyncService; document upsert repository | Disposable PostgreSQL idempotence test not run |
| FR-UC04-02 | Shared ACCPAY paging/parser, amount and planned-date mapping | Disposable PostgreSQL persistence tests not run |
| FR-UC04-03 | ManualItemService, StaffingService, assumption endpoints and forms; recurrence/calculation tests; create/edit/deactivate/show-inactive Chromium journey | Database audit proof not run |
| FR-UC04-04 | Period service validation, decimal-string HTTP response, browser opening balance save | Database persistence/audit proof |
| FR-UC04-05 | Calculator carry-forward, missing balance, 12 rows; responsive table tests and five browser widths | Human Test acceptance |
| FR-UC04-06 | Header, sync metadata, stale indicator, exceptions, failed-run notice and failed-load Retry browser recovery | Human acceptance |
| FR-UC04-07 | Server role filter, ForecastAccess, every-route/method denial for Standard User/inactive accounts, D/FA positive reads and browser navigation | Human acceptance |
| FR-UC04-08 | Two forward-only migrations, nine backend-only tables, changed-column audit | Uc4SchemaTest and repository tests not run |
| FR-UC04-09 | Size/type/OOXML validation, compressed 51 MiB inflation rejection and in-memory multipart export | Human acceptance |
| FR-UC04-10 | Export loads stored canonical dataset; no provider dependency in populator | Database snapshot integration proof |
| FR-UC04-11 | Populator formula/style preservation, capacity/overflow tests, mapped staffing clear/write | Human fixture-scrub confirmation pending |
| FR-UC04-12 | WorkbookValidator and invalid-upload HTTP tests, safe sheet/cell problems | Database no-mutation integration proof |
| FR-UC04-13 | Combined DTO/service and response-boundary tests; combined browser journey; foreign IDs and unknown-entity HTTP rejection | Database isolation tests not run |
| FR-UC04-14 | Date precedence, overrides and planned bill dates tested | Xero writeback intentionally deferred by approved scope |
| FR-UC04-15 | Partial amount, overdue, split/exclusion calculator and service/UI tests; 422/409 HTTP regressions | Database transaction proof not run |
| FR-UC04-16 | Conflict policy, sync conflict columns and explicit keep/discard service/UI | Database conflict retention tests not run |
| FR-UC04-17 | Scheduler, safe manual sync, full paging, mandatory settlement re-read, health gate and per-type apply | Disposable database transactions not run; no real provider execution |
| FR-UC04-18 | GST-inclusive calculator and workbook reconciliation tests | Human acceptance |
| FR-UC04-19 | Export HTTP headers/audit-call test, Download/Discard component and browser flow | Insert-only database proof not run |
| FR-UC04-20 | Canonical date bucketing and month-items UI/API; HTTP verifies issued-month omission and due-month inclusion | Human acceptance |
| FR-UC04-21 | Complex fixed-data POI reconciliation and injected mismatch tests | Human workbook acceptance |

## Acceptance criteria evidence

U = JUnit/Mockito unit test, H = mock HTTP, V = Vitest, P = Chromium with synthetic API/auth boundaries, I = disposable PostgreSQL integration. A passed U/H/V/P does not imply I passed.

| AC | Evidence location | Limit or outstanding verification |
|---|---|---|
| AC-1 | XeroForecastClientTest; ForecastDocumentRepositoryTest upsert | I not run |
| AC-2 | ForecastSyncServiceTest missing-ID 404; repository settlement test | I not run |
| AC-3 | Repository adjustment/split retention test | I not run |
| AC-4 | Sync page-failure test; failed/stale metadata HTTP response and presentation | Per-type I rollback not run |
| AC-5 | Sync concurrent-run and pending Retry-After tests | Database advisory-lock concurrency not run |
| AC-6 | ForecastConflictPolicy, adjustment service, repository retention and both resolution HTTP requests | I not run |
| AC-7 | Sync architecture test rejects web systemAccess/runScheduled references | Real scheduler/provider not exercised |
| AC-8 | ForecastPeriodServiceTest; decimal HTTP and opening-balance browser journey | I audit/persistence not run |
| AC-9 | Calculator null-balance and ForecastTable tests | Local U/V proof |
| AC-10 | ManualItemServiceTest validation/partial merge; recurring calculator; CRUD browser journey | I not run |
| AC-11 | Staffing service; D/FA aggregate excludes private labels and record fields; SU staffing HTTP denial | I not run |
| AC-12 | Calculator twelve-row, carry-forward and beyond-window tests | Local U proof |
| AC-13 | Calculator override/expected/due/issue date precedence; Xero bill mapping | Local U proof |
| AC-14 | Calculator amountDue/exclusion/splits/overdue tests | Local U proof |
| AC-15 | Adjustment service mismatch/empty schedule; SplitDialog validation; mismatch 422 HTTP | I transaction proof not run |
| AC-16 | Adjustment service exclusion reason validation; empty/oversized reason HTTP rejection | I not run |
| AC-17 | Calculator inclusive cash and POI reconciliation | Local U proof |
| AC-18 | Calculator grouping; month-items HTTP due-month inclusion and issue-month omission; detail browser journey | Local U/H/P proof |
| AC-19 | ForecastService combined totals/completeness/unknown entity; boundary guards; archived-entity HTTP; combined browser | I not run |
| AC-20 | Role filter, all 21 forecast route/method pairs deny SU/inactive, D/FA positive forecast/combined/opening reads, Standard User browser denial | Positive mutation permissions additionally covered at service level |
| AC-21 | Entity-scoped repositories/services; document, manual-item, staffing and split foreign-ID HTTP matrix | I isolation proof not run |
| AC-22 | Adjustment outstanding guard and split/date-adjustment 409 HTTP tests | Local U/H proof |
| AC-23 | Uc4SchemaTest; explicit migration RLS/revokes/grants | I not run, no hosted database accessed |
| AC-24 | WorkbookValidatorTest and invalid-export HTTP test; compressed input exceeding 50 MiB rejected before POI | I not run |
| AC-25 | WorkbookPopulatorTest formula/style matrix, titles/dates/splits/exclusions/staffing | Scrub confirmation pending |
| AC-26 | Populator capacity and manual/staff aggregation warning tests | Local U proof |
| AC-27 | ForecastReconciliationTest complex dataset; injected opening mismatch returns file, false header, both-value warnings and false audit flag | Local U/H proof |
| AC-28 | ForecastExportEndpointTest headers/single audit call; ExcelExportDialog.test; browser explicit download | I insert-only persistence not run |
| AC-29 | Forecast/ForecastTable tests; entity/period cancellation with late-response rejection; Chromium five widths, failed-load Retry recovery and Standard User denial; mobile/desktop detail screenshots inspected | Human screen-reader and field performance evidence not collected |

## Current executable evidence (10 November 2026)

- Follow-up `node node_modules/vitest/vitest.mjs run --reporter=json --outputFile=../output/playwright/uc4-unit-report.json` in frontend: 92 tests passed, zero failures. This includes both new entity/period cancellation regressions, which passed against the existing implementation. No implementation change or pre-fix failure is claimed for them.
- `node node_modules/typescript/bin/tsc --noEmit -p tsconfig.json`: passed.
- `node node_modules/eslint/bin/eslint.js .`: passed.
- `node node_modules/@playwright/test/cli.js test --project=chromium`: final serial run 27 passed, one existing local-API opt-in test skipped. UC4 covers 320, 390, 768, 1024, 1440 px, Standard User denial, manual/staff create/edit/deactivate/show-inactive, combined mode and failed-load Retry recovery. Journey saves opening cash, excludes a document, returns focus, uploads fixture and explicitly downloads. Browser data is synthetic. Detail screenshots were captured at all five widths; 320 and 1440 px inspected visually.
- Production Vite build in Playwright setup passed. Forecast chunk approximately 33.9 kB minified / 9.1 kB gzip. Main chunk approximately 503 kB triggers the existing large-chunk warning; these sizes do not prove field performance targets.
- Final `./scripts/check-all.sh` passed with `MAVEN_ARGS='-o -Dmaven.repo.local=target/uc4-m2 -Dtest=*,!PostgresConnectivityTest,!Uc4SchemaTest,!Uc5SchemaTest,!InvoiceAssignmentRepositoryTest,!StaffRepositoryTest,!XeroConnectionRepositoryTest,!ForecastDocumentRepositoryTest'`. Backend: 225 tests in 50 suites, zero failures/errors/skips among executed classes. Frontend: 90 tests; lint/typecheck/build passed; api:check passed. Log: ignored `backend/target/uc4-check-all.log`.
- OpenAPI regeneration: `./mvnw -o -q -B -Dmaven.repo.local=target/uc4-m2 "-Dtest=*,!PostgresConnectivityTest,!Uc4SchemaTest,!Uc5SchemaTest,!InvoiceAssignmentRepositoryTest,!StaffRepositoryTest,!XeroConnectionRepositoryTest,!ForecastDocumentRepositoryTest" -Dopenapi.update=true verify` passed; `node scripts/api-types.mjs` and `node scripts/api-types.mjs --check` passed. Later changes added tests and internal sync access refresh, with no controller/schema changes; final snapshot check passed.
- Sync access-refresh and remap regression tests: observed two failures before implementation, then passed after refresh-before-request and identity-fencing changes. Mobile detail regression: observed visible overview while detail open, then all five widths passed with separate detail view below 1200 px and side pane above it.
- Follow-up `bash ./scripts/test-frontend.sh` and `npm run api:check --silent`: passed after the cancellation/recovery tests. Backend production code did not change after the 225-test check-all result. One browser verification attempt overlapped a second Vite build/preview run, producing a port collision and two timeouts; both processes terminated, and the fresh serial full run passed. No test or timeout was weakened.

## Not run and acceptance gates

- Docker/disposable PostgreSQL: PostgresConnectivityTest, Uc4SchemaTest, Uc5SchemaTest, InvoiceAssignmentRepositoryTest, StaffRepositoryTest, XeroConnectionRepositoryTest, ForecastDocumentRepositoryTest.
- Real Xero, hosted database, deployed Test, CI and Live checks: not run.
- Existing Chromium opt-in check `unauthenticated request to a running local API is rejected`: not run because `E2E_LOCAL_API=true` was not set and a local API was not started. Mock HTTP authentication/security tests did run.
- Manual screen-reader/accessibility acceptance and representative field performance measurements: not run.
- Claude Code independent review: pending. No self-approval.
- Human scrub confirmation: pending; exact 36 cells and hashes in `docs/plans/UC4-fixture-scrub.json`. Original workbook unchanged.
- Commit/PR publication: pending; earlier local index-lock permission denial and connector mutation approval rejection recorded in the delivery ledger. No open PR is claimed.
