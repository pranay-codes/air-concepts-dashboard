# UC4 review remediation (PR #26)

Date: 10 November 2026. Scope: remaining findings from the UC4 code review, plus the Auckland date and time rule and infrastructure log time.

## Decisions

| # | Item | Decision |
|---|------|----------|
| T1 | Business dates and times | All calendar dates and displayed times use Pacific/Auckland. Stored instants and API timestamps stay UTC ISO-8601 (`Z`); the frontend renders them in Auckland. |
| T2 | Backend date helper | New `BusinessTime` holds the zone, `today(clock)` and `dateOf(instant)`. Forecast, invoices, workbook and Xero date parsers use it. |
| T3 | JVM default zone | Application sets `Pacific/Auckland` at start-up; the container gets `-Duser.timezone=Pacific/Auckland`. Spring logs then show Auckland time with offset. No timezone-less `timestamp` columns exist (all 28 are `timestamptz`), so the default change cannot shift stored values. |
| T4 | Bootstrap log and host zone | Bootstrap stamps use `TZ=Pacific/Auckland`; the host is set with `timedatectl set-timezone Pacific/Auckland`. Docker json-file timestamps stay UTC by Docker design (documented). |
| R2 | ACSL source workbook | Add `docs/ACSL*.xlsx` to `.gitignore`. The file is not deleted; the human should move it out of the repository. |
| R6 | Upload over 5 MiB | Spring rejects the file before the controller runs. Map `MaxUploadSizeExceededException` in the export controller to the same `FORECAST_TEMPLATE_INVALID` 422 body. |
| R7 | Xero date-only values | Covered by T2: the Auckland date is correct for UTC-midnight encodings (the common Xero form) and for NZ/AU local-midnight encodings. Spike S1 still needs Test-tenant confirmation. |
| R8 | Global BigDecimal schema | Remove the global replacement. Declare `format=decimal` on forecast DTO fields explicitly, as the invoice DTOs already do. Verify the regenerated snapshot still matches for forecast schemas. |
| R9 | Access resolution per call | No change. `XeroGrantService.access` locks the grant row and calls Xero identity only within 60 s of expiry, so the per-call cost is local database work, not Xero API budget. The per-request identity check guards mapping changes and is covered by `frUc04_17_mappingChangeBetweenPagesStopsBeforeNextProviderCall`. |
| R10 | Blob URL revocation | Revoke after 30 s instead of 0 ms. |

## Not changed (human gates retained)

- G5 / OQ-5 fixture confirmation: `Staffing!I12` still holds a pay note outside the approved scrub list. Left for the human (UC4 rule: retain the fixture-scrub confirmation gate).
- R7 spike S1 (Xero `/Date(...)` encoding on the Test tenant): not run. No Test-tenant access from this session.
- PR size (about 11,300 lines) and cross-review by the other agent: outside this change.

## Files

- Backend: `time/BusinessTime.java` (new), `invoices/InvoiceDates.java`, `forecast/ForecastMonths.java`, `forecast/excel/WorkbookPopulator.java`, `config/XeroConfig.java`, `DashboardApiApplication.java`, `xero/XeroForecastClient.java`, `xero/XeroInvoiceClient.java`, `web/ForecastExportController.java`, `forecast/calc/ForecastResult.java` (and other forecast DTOs with `BigDecimal`), `config/OpenApiConfig.java`.
- Tests: `time/BusinessTimeTest.java` (new), `xero/XeroForecastClientTest.java`, `xero/XeroInvoiceClientTest.java`, `web/ForecastExportEndpointTest.java`.
- Frontend: `forecast/ExcelExportDialog.tsx`; regenerated `api/generated/schema.d.ts` if the snapshot changes.
- Infra: `cloudformation/infrastructure.json` (bootstrap stamps and host zone), `scripts/test_infrastructure.py`, `deploy/compose.yaml`, `README.md`.
- Repo: `.gitignore`.

## Tests

Quote command and result for each in the PR. Not run: Docker-backed PostgreSQL tests and Playwright e2e.
