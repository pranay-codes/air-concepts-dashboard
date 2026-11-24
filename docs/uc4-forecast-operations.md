# UC4 forecast operations

UC4 is a read-only Xero reconciliation plus dashboard-owned forecasting assumptions. It does not write accounting values to Xero, re-import edited workbooks, send email or fetch bank balances. Director and Financial Admin can review and edit; Standard User and inactive accounts cannot use forecast routes.

## Schedule and manual rerun

`ForecastSyncScheduler` runs at **04:30 Pacific/Auckland**, including daylight-saving changes. `dashboard.forecast.sync.enabled` defaults to true; test configuration sets it false. Scheduled runs process connected, non-archived entities sequentially and obtain access only through `XeroConnectionService.systemAccess`. Manual requests use the caller-authorised access path.

In the dashboard, choose a specific Entity, open Forecast and select **Sync now**. The equivalent authenticated endpoint is `POST /api/v1/entities/{entityId}/forecast/sync`. Check History after an uncertain client timeout before requesting another run. Runs started less than 30 minutes ago return `FORECAST_SYNC_IN_PROGRESS` (409); expired runs are fenced before a replacement is recorded. Never clear a live run by editing hosted data.

Every type is fetched and checked completely before its mirror transaction is applied. A failed page set cannot partly update or settle that type. Another type may already have completed; a failed run therefore remains visibly incomplete, and the last-success timestamp is not advanced. Settled/voided/missing documents stop contributing. Date overrides, exclusions and split schedules survive sync; changed dates or balances flag schedules for review.

`If-Modified-Since` is disabled as an optimisation until its settlement semantics are confirmed. Every stored outstanding ID missing from the full outstanding set is re-read; 404 becomes DELETED. Never remove this settlement check in favour of an incremental header.

## Too many settlement checks

`FORECAST_SYNC_TOO_MANY_CHANGES` means more than the configured 200 individual settlement checks would be needed in one run. Preserve the mirror and assumptions, inspect History and the selected organisation mapping, and have an administrator review the volume before a manual rerun. A rerun alone will hit the same bound if the missing set is still above 200; do not loop or silently mark those documents settled. Any temporary increase or chunked recovery requires a reviewed application change. Failed-type writes remain rolled back.

## Provider limits and recovery

Forecast requests are spaced by at least 1.25 seconds per tenant, below 60 starts per minute, with a single sync per entity. Interactive invoice reads share Xero's tenant budget. A 429 honours `Retry-After` through durable `XeroReadHealth`; further accounting calls stop while the delay is pending. The forecast manual endpoint returns 429 and the header. No automatic accounting write or retry is performed.

History exposes safe numeric values from `X-DayLimit-Remaining`, `X-MinLimit-Remaining` and `X-AppMinLimit-Remaining`, plus codes and upstream status. An absent header is Unknown, never zero. Starter applications have a documented 1,000 calls/day per tenant; higher tiers have 5,000. Confirm the applicable provider tier before operational capacity planning.

The documented uncertified starter-app limit is **five organisation connections**. The current three-entity setup fits, but an additional entity or environment can affect the total. A human must check the registered application's actual tier and connection count; moving beyond the allowance is an operational prerequisite, not a hard-coded three-entity limit.

401/403: ask a Director to review/reconnect that entity in Settings. Sync resolves fresh authorised/system access before every provider request, preserving the entity, tenant and grant identity for the run. A changed mapping stops with `FORECAST_SYNC_CONNECTION_CHANGED`, `FORECAST_TENANT_CHANGED` or `XERO_CONNECTION_CHANGED`; rerun only after reviewing the selected organisation. Do not mix an old organisation's mirror with a replacement organisation. `FORECAST_CURRENCY_UNSUPPORTED` prevents adding native foreign-currency balances to NZD totals until a conversion rule is approved.

## Forecast review

The forecast screen shows one financial year at a time (12 calendar months; the year starts in April by default, set by `dashboard.forecast.financial-year-start-month`). Previous and next year controls step through earlier and later years. Each month is **generated** with Generate forecast, after a review step that states the amounts. A month can be generated when it is the current month or the next month after the last generated month, and it is locked once the following month is generated. Generating freezes that month's planned lines. Regenerating adds only lines not already in a forecast, so a paid invoice already placed stays as it was, and an invoice is never placed twice. Overdue invoices are placed in the month that is current at generation. Months not yet generated show Not generated, or Preview for the one month that can be generated. Opening balances are entered manually for the first month of a chain, and are used only when the previous month is not generated. Missing balances stay Not recorded; zero is an explicit value. Manual items and staffing use inclusive start/end months. Cash is GST-inclusive where applicable, and tax is not added again. Combined views contain month totals and entity subtotals only; missing balances, ungenerated months and failed/stale syncs make them incomplete. See `docs/specs/UC4-generated-forecast.md`.

Overrides stay local. Split lines replace the single-document contribution; changed Xero dates/balances preserve the lines and flag a conflict. Reviewers explicitly keep or discard the schedule. Exclusions require a reason and persist until reversed. Audit history contains changed columns and actor metadata, not provider bodies or tokens.

## Excel and sensitive data

Upload one standard v1 `.xlsx`, maximum 5 MiB. Processing is in memory: no upload or generated workbook is stored. Multipart thresholds keep accepted files in memory; OOXML, ZIP expansion, formula and structure checks run before loading the dataset. Macros and external-link parts are rejected. The backend writes the calculator's bucket date to every Xero contribution and values/blanks to every Staffing D23:O29 cell; unused P19:BN30 cells are cleared.

Each Xero sheet supports 200 contributions, including split lines. Above that, export fails explicitly. More than ten manual payment items or six staffing lines are aggregated into the last available row with a warning. A v2 template is required to increase document capacity safely.

Formula evaluation compares receipts, payments, opening and closing with the dashboard. Mismatches return the file with `X-Forecast-Reconciled: false` and explicit warnings; review these before use. A missing opening balance also leaves reconciliation false rather than treating missing cash as confirmed zero. Download is explicit; Discard releases the in-memory file. Responses use no-store, nosniff and an attachment filename formed from validated entity code, period and NZ generation time. Only an insert-only `forecast_exports` record is written.

The test fixture is scrubbed; the original client workbook is not suitable for committing. See `docs/plans/UC4-fixture-scrub.json` for the 36 changed cells and hashes. Human confirmation is still required before marking the fixture's PR ready.

## Verification and release boundaries

Local verification uses unit tests, mock HTTP/provider boundaries and synthetic Chromium journeys. It is not hosted, database or real-Xero proof. Docker is not running and must not be started by the agent. Explicitly exclude the seven disposable-database test classes locally: PostgresConnectivityTest, Uc4SchemaTest, Uc5SchemaTest, InvoiceAssignmentRepositoryTest, StaffRepositoryTest, XeroConnectionRepositoryTest and ForecastDocumentRepositoryTest. CI is responsible for running these with a disposable database; agents never connect to hosted Supabase.

`docs/requirements/UC4-coverage.md` records requirement/acceptance evidence and gaps. Independent Claude Code review, CI and human acceptance on Test remain release gates. Verify one ACCREC ExpectedPaymentDate and one ACCPAY PlannedPaymentDate in paged summaryOnly Test-tenant results through an authorised human-led acceptance run; local transport fixtures do not prove real-tenant behaviour.

## Rollback

Migrations stay applied. Roll back the application artefact through the established human-controlled release process; no agent deploy command is authorised. Existing data, assumptions, audit history and exports remain. If a schema correction is needed, use a new compensating forward-only migration, never edit or repair applied migration history. Disable the forecast scheduler while investigating a faulty sync. No Xero accounting rollback is needed because UC4 makes no accounting writes.

## Invoice and bill forecast grid

The entity grid starts with the previous month, current month and next six months. Use the month arrows to move the window. Create forecast saves the current month if it has no snapshot, preserves current and past snapshots on later runs, and creates new versions for the next six months from the most recently synced forecast data. An existing generated current month is copied into the grid without changing its lines. Months without a saved snapshot are labelled Not created.

Only Xero invoice and bill contributions appear in this grid. The forecast mirror currently retains contact names rather than stable contact IDs, so rows with the same name are combined. The existing financial-year summary and Excel export use the separate generated-month forecast; their totals can therefore differ from this invoice-and-bill-only grid. Grid revisions are append-only, entity-scoped and audited. Reverting the application leaves revisions in place; hosted database changes are applied by CI.
