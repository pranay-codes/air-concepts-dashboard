# Spec addendum: UC4 generated monthly forecast and financial-year view

- Status: implemented on branch `feat/fr-uc04-22-generated-forecast-months` for review; decisions below are the agent's best judgement under the standing UC4 instruction (10 November 2026) and must be confirmed by the human.
- Requested by: human instruction, 11 November 2026 (chat).
- Supersedes, for the forecast screen only: the rolling 12-month window that starts at a selectable "start month" (AC-9, AC-12, AC-18, AC-19, AC-25, AC-27 as worded in [UC4.md](UC4.md) continue to apply to the month-level arithmetic, with the changes listed under "Changed acceptance").
- Source: the human's message and worked example, not Notion. No Notion page was read.

## Problem

1. The forecast had a selectable start month and recalculated a 12-month window from live data on every request, so a month's figures changed each time Xero data synced.
2. There was no way to keep a forecast once it was made, and no way to look back at a previously made forecast.
3. The view had no financial-year structure.

## Requirements

| ID | Requirement |
|---|---|
| FR-UC04-22 | The forecast screen shows one **financial year** at a time (12 calendar months). The user selects the financial year and can step back to earlier years. There is no forecast start-month selector. |
| FR-UC04-23 | Each calendar month is **generated** explicitly by an authorised user (Director or Financial Admin) with a *Generate forecast* action. Generation freezes that month's planned lines in PostgreSQL. Frozen lines are never updated or deleted by any later action. |
| FR-UC04-24 | Regenerating a month only **adds** lines that are not already in a forecast. An invoice already placed in any month stays where it was placed, whether it is later paid, part-paid, voided or still outstanding. Overdue invoices that have never been placed are added to the month that is current at regeneration time. |

FR-UC04-22 to 24 are new IDs allocated under the forecast use case, after FR-UC04-21. They do not touch the supplier-bill IDs. OQ-1 (the 01–07 collision) is unchanged.

## Decisions (best judgement; human to confirm)

| # | Decision | Reason |
|---|---|---|
| D1 | **Financial year starts 1 April** (NZ default), stored as `dashboard.forecast.financial-year-start-month=4`. Label is `2026-2027`. | The repository does not record the client's year-end. The workbook sheet name `Forecast 2026-2027` implies a year-range label. Change the property if Xero shows a different organisation year-end. |
| D2 | **Generatable months:** only the *current* month, or the month after the latest generated month. A month can be generated only when every month from the current month up to (not including) it is already generated. | Forces chronological generation, so the opening-balance chain never has a hole. |
| D3 | **Lock:** a generated month is regenerated only while the following month has not been generated. | Once the next month is generated, the chain has moved on. Late items fall into the newer current month as overdue. |
| D4 | **Opening balance:** for a month, opening = previous month's closing when the previous month is generated (chain of frozen lines); otherwise the manually entered opening for that month. Generation requires a resolved opening. | Closing of one month equals opening of the next (AC-12 unchanged). Manual entry is needed only at the first month of a chain. |
| D5 | **Placement is once per document per entity** (unique index on the first split line or the single line of each document). Manual items and staffing are placed once per (month, source item). | No double counting across months (FR-UC04-15). |
| D6 | **Overdue rule:** an outstanding document whose forecast date is before today is placed only in the month that is current at generation time (existing `OverduePolicy.CURRENT_MONTH`). A future-month generation never places overdue items. | Matches the worked example: Nov 25 regeneration adds overdue items to November. |
| D7 | **Unsettled items stay in their month.** A line placed in November that is still unpaid in December is not moved to December. The dashboard shows it as *Outstanding since generation* for information. | Moving it would change a frozen month. Left for the human to decide, see Open questions. |
| D8 | **Frozen line content:** amount, direction, category, number, contact, issue and due dates, forecast date, rule, overdue flag, split sequence, generation number, generated-by and generated-at. Live Xero status is read separately for display only. | Lines must not depend on later mirror changes. |
| D9 | **beyondWindow is kept.** It sums unplaced lines dated after the displayed financial year, so AC-12 still holds. | Keeps the existing response shape. Unplaced means not yet in any generated month. |
| D10 | **Excel export** uses the same resolved financial-year view as the dashboard (frozen lines for generated months, preview lines for the generatable month). Export adds a warning naming each month that is not generated, and the export history records totals over the months that have figures (`forecast_exports.receipts_total` is NOT NULL). | FR-UC04-21 parity. The export is still read-only apart from the existing `forecast_exports` insert. |
| D11 | **Audit:** each generation that adds lines, or creates the month, writes one `forecast_audit` row with `record_table = forecast_generated_months`. Action is `INSERT` for the first generation and `UPDATE` for later additions; the row carries the generation count, lines added, and receipt and payment totals added. | Keeps FR-UC04-08 audit coverage without a row per line. |
| D12 | **Opening balance override (requested 11 November 2026, chat):** an opening balance entered for a month always applies to that month and overrides the carried closing of the month before it (`ForecastChain`: explicit entry first, then carried closing, else none). Each month row carries `openingSource`: `MANUAL` (entered, previous month not generated), `OVERRIDE` (entered over a generated previous month), `CARRIED_FORWARD` (previous month's closing), or null. The table shows an Edit control on every row, shows "Not set" when no amount exists, and the dialog edits the row's own month (it previously always edited the financial year's first month). | Without this, an Edit on a month after a generated month was saved and then ignored. Overrides are stored in the existing `forecast_periods` table, so no migration is needed. |

## Data model (migration `20261111010000_uc4_generated_forecast_months.sql`)

- `forecast_generated_months(entity_id, forecast_month date (first day), first_generated_at, last_generated_at, generated_by, generation_count, primary key (entity_id, forecast_month))`. Insert and update only, no delete grant.
- `forecast_snapshot_lines(id bigserial, entity_id, forecast_month, source in DOCUMENT/MANUAL/STAFFING, source_id uuid, split_sequence, split_count, direction, category, document_number, label, issue_date, due_date, cash_date, amount >= 0, rule, overdue, generation_number, added_by, added_at)`. Insert only. Partial unique index on `(entity_id, source_id)` where `source='DOCUMENT' and coalesce(split_sequence,1)=1`. Unique index on `(entity_id, forecast_month, source, source_id, coalesce(split_sequence,0))` for non-document rows.
- RLS enabled on both tables. Revoke all from `public`, `anon`, `authenticated`. Grant insert and select to `service_role` on lines; select, insert and update to `service_role` on generated months. No delete.
- `forecast_audit.record_table` check extended with `forecast_generated_months` and `forecast_snapshot_lines`. The constraint is dropped and recreated (forward-only, new migration).
- `set lock_timeout = '5s'` at the top. No DROP, TRUNCATE or unscoped DELETE.
- Opening balances stay in `forecast_periods` (existing table, unchanged).

## API changes (contract, human review required before merge)

- `GET /entities/{id}/forecast?financialYearStart=2026` replaces `startMonth`. Default is the financial year containing today. Response carries `financialYearStart`, `financialYearLabel`, `months[]` with `state` (GENERATED, PREVIEW, NOT_GENERATED), `generatable`, `generatedAt`, `pendingNewItems`, `totals`, `beyondWindow`, `exceptions`, `exclusions`, sync fields. `receipts`, `payments` and `net` are null for NOT_GENERATED months.
- `POST /entities/{id}/forecast/months/{month}/generate`: Director or Financial Admin. Returns the month row plus `addedLines`, `addedReceipts`, `addedPayments`. Idempotent: no new candidates means `addedLines = 0`, no audit row, no change. `409 FORECAST_MONTH_LOCKED` when the following month is generated; `409 FORECAST_MONTH_NOT_GENERATABLE` when the month is not the next in sequence; `422 FORECAST_OPENING_REQUIRED` when opening cannot be resolved; `400 FORECAST_PERIOD_INVALID` for past months or months beyond the 11-month window.
- `GET /entities/{id}/forecast/months/{month}/items` returns frozen lines for generated months and preview lines for the generatable month; `state` in the response.
- `GET /forecast/combined?financialYearStart=` mirrors the entity view.
- Opening-balance endpoint unchanged in route and validation. Its meaning is documented as "used when the previous month is not generated".
- Excel export uses `financialYearStart` in place of `startMonth`.

## Changed acceptance (within UC4 acceptance IDs)

- AC-9: unchanged in meaning; a missing opening for the financial year's first month still renders "Not recorded".
- AC-12: closing = opening + receipts − payments holds for every month that has figures; the "exactly 12 rows" rule holds. `beyondWindow` is kept (D9).
- AC-18: unchanged.
- AC-19: unchanged; combined totals are null where any entity month is null.
- AC-25/AC-27: the same arithmetic over the financial-year view.

## New acceptance

- AC-30 (FR-UC04-22): a financial year shows 12 rows; `financialYearStart` defaults to the current financial year; years before the current year are read-only.
- AC-31 (FR-UC04-23): generating a generatable month inserts its lines once; a second generation with unchanged data inserts nothing; the frozen lines are unchanged after any sync, Xero status change, or exclusion added afterwards.
- AC-32 (FR-UC04-24): a document placed in month A is not placed again in month B; a paid invoice placed earlier remains as a line; a new invoice due on or after the regeneration date is added; an unplaced overdue invoice is added to the current month only.
- AC-33 (D4): closing of a generated month equals opening of the next month; the opening for the first ungenerated month uses the manual anchor.
- AC-34 (security): SU receives 403 on generate; a Director in another entity receives 404; generate for entity A never places a document from entity B (placement is keyed by entity and document ID).
- AC-35 (D3): regenerating a month when the next month is generated returns 409 and writes nothing.

## Test plan

Levels as in [UC4.md](UC4.md): U = unit, H = Spring MVC HTTP with mocked services, I = disposable PostgreSQL (Testcontainers, **not run locally**: Docker is not running and the agent does not start it), V = Vitest, P = Playwright (Chromium, optional locally).

| AC | Test | Level |
|---|---|---|
| AC-30 | `ForecastFinancialYearTest` (defaults, boundaries, label, April start, past years read-only) | U |
| AC-31 | `ForecastGenerationServiceTest.frUc04_23_generateOnceThenNoOp`, `..._frozenLinesIgnoreLaterChanges` | U |
| AC-32 | `ForecastGenerationServiceTest.frUc04_24_placedDocumentNotReplaced`, `..._overdueOnlyIntoCurrentMonth`, `..._futureGenerationNeverPlacesOverdue` | U |
| AC-33 | `ForecastChainTest.closingFeedsNextOpening`, `..._manualAnchorWhenPreviousNotGenerated` | U |
| AC-34 | `ForecastEndpointTest.frUc04_23_generateRoleMatrix`, `..._foreignEntityIs404`; `ForecastGenerationServiceTest.sec_crossEntityPlacementIsKeyed` | H, U |
| AC-35 | `ForecastGenerationServiceTest.frUc04_24_lockedWhenNextMonthGenerated` | U |
| Schema | `Uc4SchemaTest` extended: RLS, grants, partial unique index (placement once) | I (not run locally) |
| Existing | `ForecastCalculatorTest`, `ForecastServiceTest`, `ForecastEndpointTest`, `ForecastExportEndpointTest`, `WorkbookPopulatorTest`, `ForecastReconciliationTest` updated for the financial-year model; no assertion removed except the `beyondWindow` and `startMonth` fields that D9 and FR-UC04-22 replace. | U, H |

## Plan (files)

Backend: migration (new); `forecast/ForecastFinancialYear` (new); `forecast/ForecastMonthRepository` (new); `forecast/ForecastChain` (new, pure); `forecast/ForecastGenerationService` (new); `forecast/calc/ForecastCalculator` (candidates + aggregate); `forecast/ForecastService` (financial-year view, items, combined); `forecast/ForecastDatasetLoader` (all manual items and staffing, FY start); `forecast/ForecastMonths` (FY window validation); `forecast/ForecastExportService`; `forecast/excel/WorkbookPopulator` (line-based Xero rows); `web/ForecastController` (financialYearStart, generate route); tests as above.

Frontend: `frontend/src/forecast/Forecast.tsx`, `ForecastHeader.tsx` (financial-year selector, no start month), `ForecastTable.tsx` (state column, Generate and Regenerate buttons, Not generated and Preview labels), `MonthDetail.tsx` (state, frozen notice), `contracts.ts` / generated API types, `CombinedForecast.tsx`, tests.

## Rollback

Migration is forward-only and additive. Rolling back the application leaves the new tables in place; frozen lines persist. No Xero writes. Disable generation by reverting the controller route, not by editing lines.

## Open questions (for the human)

- **Q-A (D1):** confirm the financial-year start (1 April assumed).
- **Q-B (D7):** items placed in a month that stay unpaid. Options: (a) leave in their month, shown as outstanding (current choice); (b) carry into the next generated month as an overdue line, which would double-count against the earlier month's frozen closing unless the earlier closing is adjusted by a manual adjustment.
- **Q-C (D3):** should a locked month allow a manual correction line instead of silently excluding late items?
- **Q-D:** FR-UC04-22 to 24 IDs are allocated by this addendum. Confirm numbering.
- **Q-E:** the client's Xero financial year-end, to confirm D1 against the organisation settings (not read here: no live Xero access by the agent).
- **Q-F (requested 11 November 2026):** the request asks that an April closing becomes May's opening. Under D2 only the current month or the next month after the last generated month can be generated, so past months such as April never have a closing and cannot carry one. The delivered behaviour carries closings only between generated months and uses an entered opening (D12) elsewhere. Confirm whether past months may be generated, which would change D2 and the lock rule D3.
- **Q-G (requested 11 November 2026):** the empty-amount label is "Not set" as requested. AC-9 and the `formatMoney` helper still say "Not recorded" for the other figures. Confirm the label for the opening column.
- **Q-H (D12):** an override entered on a month can stop the carried chain from following a later generated closing. The row shows "Override" so it is visible; there is no reset action yet.

## Verification (this delivery)

Local only: `./mvnw -q -B test` with the Docker-backed test classes excluded (PostgresConnectivityTest, Uc4SchemaTest, Uc5SchemaTest, InvoiceAssignmentRepositoryTest, StaffRepositoryTest, XeroConnectionRepositoryTest, ForecastDocumentRepositoryTest) and the migration not applied to any database. Frontend: `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`, `npm run api:check`. Not run: disposable-PostgreSQL integration tests, Playwright journeys, CI, hosted Test deploy.

## Delivered in this branch

Backend unit and HTTP tests run locally with the seven Docker-backed classes excluded: 260 passing, 0 failing. Frontend: lint, typecheck, `npm test` (94 passing), build and `api:check` pass.

- Tests added: `ForecastCalendarTest`, `calc/ForecastChainTest`, `ForecastGenerationServiceTest` (generate once then no-op; placed documents never replaced; overdue only into the current month; lock; order; opening required), `ForecastServiceTest` (frozen lines ignore live documents; unfrozen past months show no figures), and the role matrix now includes the generate route.
- `Uc4SchemaTest` lists the two new tables, so RLS and browser-privilege checks run in CI.

## Not yet covered (report as not run)

- **Disposable-PostgreSQL placement test** (`ForecastMonthRepositoryTest`): the partial unique index that keeps a document placed once, and insert-only behaviour, are written in the migration but have no integration test yet. Docker is not running locally.
- **AC-34 cross-entity placement test**: the foreign-entity 404 on generate is not yet a named test. Access is checked through `ForecastAccess.requireWrite` and every placement query is keyed by entity.
- **Playwright journeys** (`uc4-forecast.spec.ts` at five widths): not run.
- **Migration on Test**: not applied anywhere. CI applies it after merge (ADR-003).
- **Client financial-year end**: D1 is unverified against the Xero organisation's year-end.

