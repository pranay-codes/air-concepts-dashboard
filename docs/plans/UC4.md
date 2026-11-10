# UC4 Technical Plan — Financial Forecast and Business Cash Position

Status: plan for implementation hand-off. Written 10 November 2026 from the Notion page
"Use Case 4 — Prepare and Review the Financial Forecast and Business Cash Position"
(last edited 9 November 2026, status "Draft for business review"), the linked decision
"Decision: store forecast data in Supabase" (21 October 2026) and the client's workbook
`docs/ACSL Cashflow Forecast Template - Cleaned.xlsx`, mapped in
[`docs/specs/UC4-template-map.md`](../specs/UC4-template-map.md). The human authorised reading
the Notion page for this plan.

This document tells an implementing agent what to build, in what order, and how to prove it.
Read it top to bottom once, then work through section 12 slice by slice.

---

## 1. What is being built, in one paragraph

Each entity (Services, HotAS, Commercial, and any entity added later) has its own Xero
organisation. Once a day the backend pulls every outstanding customer invoice (expected
income) and every unpaid supplier bill (expected payment) from each connected Xero organisation
and upserts them into PostgreSQL. Financial Admin adds the things Xero does not know: the
opening bank balance, recurring payments, salaries and one-off items, and per-invoice
adjustments (expected-date override, exclusion with reason, split-payment schedule). A pure
calculation module turns the stored data into a rolling 12-month table: opening balance,
expected receipts, expected payments, net movement, projected closing balance. The dashboard
shows that table. Optionally a user uploads the standard Excel workbook and the backend fills it
from the same stored data and returns it for download. Excel never writes back to the database.

Everything the dashboard shows and everything the workbook contains comes from one function
over one dataset. That is the central design rule (FR-UC04-10, FR-UC04-21).

---

## 2. Gates before any code is written

These follow AGENTS.md and are not optional.

| # | Gate | Who | Output |
| --- | --- | --- | --- |
| G1 | **Spec pack.** Create `docs/specs/UC4.md` from `docs/specs/TEMPLATE.md` using section 3 of this plan as the requirement list and section 11 as the test plan. A human approves it. | Claude Code writes, human approves | Approved spec pack |
| G2 | **Requirement ID conflict.** AGENTS.md §2 records that FR-UC04-01 to 07 are also used by the supplier bill use case. Do not resolve this silently. Ask the human which use case keeps the IDs; record the answer under Confirmed decisions in the spec pack. Until answered, this plan uses the IDs exactly as the forecast Notion page does. | Human | Decision recorded |
| G3 | **Spikes** (section 4). Each must have a note in `docs/spikes/` with status `confirmed` or `partial` before the code that depends on it is written. | Claude Code | Spike notes |
| G4 | **New dependency approval.** Apache POI (`org.apache.poi:poi-ooxml`) for reading and writing `.xlsx`. No other new backend or frontend dependency is proposed. | Human | Approval in spec pack |
| G5 | **Standard workbook fixture.** The client's template is `docs/ACSL Cashflow Forecast Template - Cleaned.xlsx` and its cell map is written in [`docs/specs/UC4-template-map.md`](../specs/UC4-template-map.md). Before slice 9, copy it to `backend/src/test/resources/forecast/template-v1.xlsx` **after scrubbing** the four staff names and the hourly rate on the `Staffing` sheet (rows 10–15 and 23–28); the "Cleaned" file still contains them. The human confirms the scrub. | Agent scrubs, human confirms | Committed fixture |
| G6 | **Contract review.** OpenAPI changes and every migration file are reviewed by a human before implementation of the slice that uses them (AGENTS.md §5 step 4). | Human | Review per slice |
| G7 | **Plan approval for sensitive areas.** Slices touching auth, Xero, migrations or entity isolation need the small plan (the slice entry in section 12) approved before editing. | Human | Approval per slice |

Open client questions (section 13) do not block the build. Each has a stated default
assumption; the implementation must make the assumption a single named constant or
configuration value so it can change without redesign.

---

## 3. Functional requirements, restated for the implementer

Source IDs are the forecast page's FR-UC04-01 to 21. The "Where it lands" column names the
code area in this plan. The Notion acceptance criteria AC-1 to AC-10 map onto these.

| ID | Requirement (plain words) | Where it lands |
| --- | --- | --- |
| 01 | Sync each entity's outstanding sales invoices (ACCREC) into PostgreSQL, keyed by Xero organisation + document ID. Keep unpaid balance, dates, status. No duplicates. | Sync §6, table `forecast_documents` |
| 02 | Same for unpaid supplier bills (ACCPAY). | Sync §6 |
| 03 | Authorised users maintain recurring direct debits, payments, salaries/wages and other manual items per entity. | `forecast_manual_items`, `forecast_staffing`, API §9 |
| 04 | Financial Admin enters the opening cash balance per entity and forecast period. | `forecast_periods` |
| 05 | Show 12 months: opening, receipts, payments, net movement, closing. | Calculator §7, Forecast page §10 |
| 06 | Show entity, period, last successful sync, exceptions. | `forecast_sync_runs`, Forecast page header |
| 07 | Director and Financial Admin review in the dashboard; no emailed Excel needed. | Roles §9, Forecast page |
| 08 | Store every assumption, manual value, adjustment, schedule and audit record in PostgreSQL, partitioned by entity and period. | Schema §5, `forecast_audit` |
| 09 | Per Excel generation: upload one `.xlsx` using the common template; capture entity + period; validate before processing. | Excel §8, `POST .../forecast/export` |
| 10 | Populate the uploaded workbook from the same stored dataset the dashboard uses; never a separate Xero fetch. | `ForecastDataset` is the only input to both renderers |
| 11 | Map data onto worksheets Assumptions, Forecast, Xero_Debtors, Xero_Creditors, Staffing; keep formulas and formatting; no hard-coded entity names; never truncate silently. | `WorkbookPopulator`, template map |
| 12 | Validate uploaded workbook (sheets, layout, mappings, supported formulas). Reject with clear errors; never touch stored data. | `WorkbookValidator` |
| 13 | Separate view per entity; combined view only for entities the user is authorised for, without exposing another entity's rows. | `GET /api/v1/forecast/combined` |
| 14 | Customer invoice forecast date = Xero ExpectedPaymentDate if present, else DueDate. Authorised changes audited and "reflected in Xero where supported". | Calculator §7.2; Xero write-back deferred, see §4 S4 and §13 Q6 |
| 15 | Handle partial payments (use AmountDue), overdue, optional split schedules, manual exclusions with reason; no double counting; exclusions persist until reversed. | `forecast_document_adjustments`, `forecast_split_lines`, Calculator |
| 16 | If Xero's expected date changes while a split schedule exists, keep the schedule, flag a conflict, require authorised review. | Sync §6.4 conflict flag |
| 17 | Daily idempotent reconciliation Xero → DB. Reflect new, settled, part-paid, changed dates/balances, sync errors. Never erase dashboard adjustments. Keep last-sync timestamp and audit. | Sync §6 |
| 18 | GST-inclusive treatment applied consistently to receipts and monthly totals. | Calculator §7.4 |
| 19 | On successful population, tell the user the file is ready and prompt to download. Filename carries entity, period, generation time. Export never alters stored records. | Excel §8.4, `ExcelExportDialog` |
| 20 | Monthly, entity-specific views of expected income and payments grouped by forecast date, not issue month. | `GET .../forecast/months/{month}/items` |
| 21 | Dashboard and Excel consume the same canonical data and produce matching totals. | Reconciliation check §8.3, test T-21 |

---

## 4. Spikes (write the note before relying on the capability)

Use the `spike` skill. Each note goes in `docs/spikes/` and is linked from
`docs/spikes/README.md`. Existing note `docs/spikes/uc5-xero.md` already confirms invoice list
paging, `where`/`order`, tenant header handling, and that `unitdp=4` is needed for detail reads.

| ID | Question | Why it matters | What to check |
| --- | --- | --- | --- |
| S1 | Does `GET /Invoices` return `ExpectedPaymentDate` (ACCREC) and `PlannedPaymentDate` (ACCPAY) in list responses, with and without `summaryOnly=true`? | FR-14 forecast date rule; sync cost | Official Invoices API docs; mock payloads. Record field names and date encoding. |
| S2 | Does `If-Modified-Since` on `GET /Invoices` return invoices whose `UpdatedDateUTC` changed because a payment or credit note was applied, or status changed to PAID/VOIDED? | Incremental sync must see settlements, not just edits | Docs for `UpdatedDateUTC` semantics. If unclear, the sync must fall back to the full-outstanding comparison in §6.3 and the note must say so. |
| S3 | Which statuses can carry `AmountDue > 0`? Confirm AUTHORISED is the only open status for approved documents and whether SUBMITTED (awaiting approval) should ever be included. | Which rows count as "outstanding" (client question Q3) | Status table in Invoices docs. Default: AUTHORISED only. |
| S4 | Writing `ExpectedPaymentDate` back to Xero: which scope (`accounting.transactions` write), which endpoint (`POST /Invoices/{id}`), and is it idempotent? | FR-14 "reflected in Xero where supported" is the first accounting **write** in the application | Record scope and endpoint. Do **not** implement in this use case's first delivery; see §13 Q6. |
| S5 | Apache POI: can it open the client's template, write values into cells, preserve all formulas and styles, rename the forecast sheet, and set "recalculate on open"? Does `FormulaEvaluator` evaluate the template's functions (`IF`, `OR`, `SUM`, `SUMIFS`, `MAX`, `DATE`, `YEAR`, `MONTH`, `EDATE`, `INDEX`, `MATCH`, `&`)? | FR-11, FR-12, FR-21 | Run against the fixture from G5. The template has no named ranges, data validation, macros or protection (template map §1), so the note only needs to prove population and evaluation. |
| S6 | Xero rate limits for a daily full-outstanding read across N organisations (60 calls/min, 5,000/day per tenant; app-wide minute limit). | Sync scheduling and page size | Official limits page; compute worst-case call count for 1,000 outstanding documents. |

---

## 5. Data model

One new forward-only migration per slice (AGENTS.md §8). All tables: RLS enabled, all
privileges revoked from `public`, `anon`, `authenticated`; `select, insert, update` granted
to `service_role`; `delete` granted to `service_role` only where this plan says a scoped
delete is used. Set `lock_timeout = '5s'` at the top of each file. Monetary columns are
`numeric(18,2)`; Xero stores two decimals on totals. Months are stored as the first day of the
month (`date`), named `*_month`.

### 5.1 Tables

**`forecast_documents`** — synced Xero mirror (sync-owned; the API never writes it).

| Column | Type | Notes |
| --- | --- | --- |
| entity_id | uuid FK entities | |
| xero_document_id | uuid | Xero InvoiceID |
| tenant_id | uuid | copy of the connection's tenant at sync time; a remap is detected by comparison |
| document_type | text check in ('ACCREC','ACCPAY') | |
| number, reference, contact_name | text | |
| status | text check in ('DRAFT','SUBMITTED','AUTHORISED','PAID','VOIDED','DELETED') | |
| issue_date, due_date | date | due_date nullable |
| xero_expected_date | date null | ExpectedPaymentDate (ACCREC) or PlannedPaymentDate (ACCPAY) |
| currency | text | |
| total, total_tax, amount_due, amount_paid, amount_credited | numeric(18,2) | amount_due is the forecast amount |
| outstanding | boolean | derived at sync: status = AUTHORISED and amount_due > 0 (constant, see S3) |
| xero_updated_at | timestamptz | |
| first_seen_at, last_synced_at, settled_at | timestamptz | settled_at set when outstanding flips to false |
| PK (entity_id, xero_document_id) | | |
| index (entity_id, outstanding, document_type) | | |

**`forecast_document_adjustments`** — dashboard-owned, one optional row per document. Sync
never writes here except the three `conflict_*` columns, which is how FR-17 "never erase
adjustments" is guaranteed structurally.

| Column | Type | Notes |
| --- | --- | --- |
| entity_id, xero_document_id | FK → forecast_documents composite | PK |
| expected_date_override | date null | |
| excluded | boolean default false | |
| exclusion_reason | text null; check (not excluded or length(trim(exclusion_reason)) between 1 and 500) | |
| date_conflict_since | timestamptz null | set by sync (§6.4); cleared by review action |
| conflict_previous_xero_date, conflict_new_xero_date | date null | shown to the reviewer |
| updated_by | uuid FK staff_users | |
| updated_at | timestamptz | |

**`forecast_split_lines`** — optional split schedule.

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| entity_id, xero_document_id | FK → forecast_documents | |
| sequence | smallint | unique (entity_id, xero_document_id, sequence) |
| expected_date | date | |
| amount | numeric(18,2) check (amount > 0) | |

Service rule: sum(amount) must equal `amount_due` at save time; if sync later changes
`amount_due`, the sync marks the conflict (§6.4) rather than editing lines. Replacing a schedule
is a scoped `delete ... where entity_id = ? and xero_document_id = ?` inside one transaction.
This is the only delete in this use case and is flagged here for human approval (AGENTS.md §8).

**`forecast_periods`** — one row per entity per 12-month window.

| Column | Type | Notes |
| --- | --- | --- |
| entity_id | uuid FK | |
| start_month | date check (extract(day from start_month) = 1) | |
| opening_balance | numeric(18,2) | |
| opening_balance_source | text null | free text, e.g. "ANZ statement 31 Oct" |
| updated_by, updated_at | | |
| PK (entity_id, start_month) | | |

**`forecast_manual_items`** — recurring and one-off items not in Xero.

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| entity_id | uuid FK | |
| direction | text check in ('IN','OUT') | |
| category | text check in ('DIRECT_DEBIT','LOAN','RENT','TAX','TRANSFER','OTHER_PAYMENT','OTHER_RECEIPT') | extend by migration |
| description | text (1–200) | |
| amount | numeric(18,2) check (amount > 0) | GST-inclusive cash amount |
| recurrence | text check in ('ONE_OFF','MONTHLY') | |
| day_of_month | smallint null check (1–31) | MONTHLY only; clamp to month end in calculator |
| start_month | date (day 1) | ONE_OFF: the month it lands |
| end_month | date null (day 1) | inclusive; null = open-ended |
| active | boolean default true | soft delete; never hard-delete |
| created_by, updated_by, updated_at | | |

**`forecast_staffing`** — salaried staff cost lines. Personal information: Director and
Financial Admin only; never returned to STANDARD_USER.

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| entity_id | uuid FK | |
| label | text (1–160) | role or name as the client prefers |
| monthly_cost | numeric(18,2) check (>= 0) | gross monthly cash cost as entered |
| pay_day_of_month | smallint null | |
| start_month, end_month | date | as above |
| active | boolean | soft delete |
| created_by, updated_by, updated_at | | |

**`forecast_sync_runs`** — one row per entity per run.

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| entity_id | uuid FK | |
| trigger | text check in ('SCHEDULED','MANUAL') | |
| triggered_by | uuid null FK staff_users | MANUAL only |
| started_at, finished_at | timestamptz | |
| status | text check in ('RUNNING','SUCCEEDED','FAILED') | |
| documents_seen, documents_inserted, documents_updated, documents_settled, conflicts_flagged | integer | |
| error_code | text null | ApiFailure code only, never provider body |
| upstream_status | integer null | |
| index (entity_id, started_at desc) | | |

**`forecast_exports`** — audit of workbook generations (no file bytes stored).

Columns: `id uuid PK, entity_id, start_month, generated_by, generated_at, template_version text,
receipts_total, payments_total, closing_total numeric(18,2), reconciled boolean, warnings jsonb`.

**`forecast_audit`** — change history for FR-08/14/15/17.

| Column | Type | Notes |
| --- | --- | --- |
| id | bigserial PK | |
| entity_id | uuid | |
| actor_id | uuid null | null for sync |
| at | timestamptz | |
| record_table | text | one of the tables above |
| record_key | text | e.g. `xero_document_id` or row id |
| action | text check in ('INSERT','UPDATE','SOFT_DELETE','SYNC') | |
| before, after | jsonb | only the changed columns; never tokens |

Write audit rows from the service layer in the same transaction as the change. Do not use
triggers; the service layer knows the actor.

### 5.2 Entity isolation in the schema

Every table has `entity_id`. Every repository method takes `entity_id` as its first argument
and includes it in every `where` clause. Combined views are computed in Java from per-entity
results, never from a cross-entity SQL query. Add a disposable-PostgreSQL test (new
`Uc4SchemaTest`, same pattern as `Uc5SchemaTest`) that proves browser roles have no privileges
and RLS is on for every new table.

---

## 6. Xero synchronisation

### 6.1 Where it lives

New package `com.airconcepts.dashboard.forecast.sync`:

- `ForecastSyncScheduler` — `@Scheduled(cron = "0 30 4 * * *", zone = "Pacific/Auckland")`, enabled by `@EnableScheduling` on a new `SchedulingConfig`, switchable with property `dashboard.forecast.sync.enabled` (default true; tests set false).
- `ForecastSyncService` — orchestrates one run for one entity. Public methods `runScheduled()` (all connected, non-archived entities, sequential) and `runManual(CurrentUser, UUID entity)`.
- `XeroForecastClient` (package `xero`) — new read client beside `XeroInvoiceClient`. Reuses `XeroReadErrors`, `TenantAccess` and the shared `RestClient` bean from `XeroConfig`. Returns `ForecastSource` records, not `InvoiceViews`.
- `ForecastDocumentRepository` — upsert and settlement queries.

### 6.2 System access without a user

`XeroConnectionService.authorisedAccess(CurrentUser, UUID)` requires a user. Add
`XeroConnectionService.systemAccess(UUID entity)` that performs the same connection lookup and
grant lease without a role check, with a Javadoc warning that it is for scheduled internal work
only and must never be reachable from a controller. The manual trigger goes through
`authorisedAccess` (Director or Financial Admin) and then calls the same internal run.

### 6.3 Algorithm for one entity (idempotent)

1. Insert a `forecast_sync_runs` row with status RUNNING. Take `pg_advisory_xact_lock(hashtext('forecast-sync:' || entity_id))` in a short transaction to record the run; if another run for the entity is RUNNING and started less than 30 minutes ago, exit with `FORECAST_SYNC_IN_PROGRESS` (HTTP 409 for manual).
2. Resolve `TenantAccess`. Call `XeroReadHealth.check` first; on a pending Retry-After, mark FAILED with `XERO_RATE_LIMITED` and stop.
3. **Fetch outstanding set.** For each type in (ACCREC, ACCPAY): page through `GET /Invoices?where=Type=="{type}" AND Status=="AUTHORISED" AND AmountDue>0&order=UpdatedDateUTC ASC,InvoiceID ASC&page=N&pageSize=100&summaryOnly=true` (drop `summaryOnly` if S1 shows it omits the expected-date fields). Validate pagination metadata exactly as `XeroInvoiceClient.list` does. Collect into a map keyed by InvoiceID.
4. **Fetch recently changed set.** If a previous SUCCEEDED run exists: `GET /Invoices?where=Type=="{type}"` with header `If-Modified-Since: <previous started_at minus 24 h>`, paged. This catches documents that left the outstanding set (paid, voided, credited). If S2 is not confirmed, skip this step and rely on step 6.
5. **Upsert** every fetched document into `forecast_documents` with `on conflict (entity_id, xero_document_id) do update set ... where forecast_documents.xero_updated_at is distinct from excluded.xero_updated_at or forecast_documents.amount_due is distinct from excluded.amount_due`. Set `outstanding = (status = 'AUTHORISED' and amount_due > 0)`. Count inserted vs updated using `xmax = 0`.
6. **Settle missing rows.** Every stored row with `outstanding = true` whose ID was **not** in step 3's set is re-read individually with `GET /Invoices/{id}` (bounded: at most 200 per run; if more, mark the run FAILED with `FORECAST_SYNC_TOO_MANY_CHANGES` and ask for a manual rerun; record the bound in spike S6). Upsert the result. If Xero returns 404, set `status = 'DELETED'`, `outstanding = false`.
7. **Conflict detection** (§6.4) inside the same transaction as step 5.
8. Mark the run SUCCEEDED with counts; call `XeroReadHealth.success`. On any `ProviderFailure`, mark FAILED with the failure code and call `XeroReadHealth.failure`; **do not** partially settle rows from a failed page set (steps 5–7 run in one transaction per type; a failure rolls back that type).
9. Write one `forecast_audit` row with action SYNC summarising counts.

Everything stored is a mirror. No stored value is ever authoritative over Xero; a future full
re-sync must be able to rebuild `forecast_documents` from Xero alone. Adjustments live in
other tables and survive.

### 6.4 Date-change conflict (FR-16)

Within the upsert transaction, for each document whose incoming `xero_expected_date` or
`amount_due` differs from the stored value **and** that has one or more `forecast_split_lines`
rows, set on `forecast_document_adjustments` (insert the row if absent):
`date_conflict_since = now()`, `conflict_previous_xero_date = old`, `conflict_new_xero_date = new`.
The calculator keeps using the split lines while the conflict is open. The Forecast page lists
open conflicts under Exceptions. The review action (§9) either keeps the schedule (clears the
flag) or discards it (deletes the split lines, clears the flag).

### 6.5 Freshness exposure

`GET .../forecast` returns `lastSuccessfulSyncAt`, `lastRun {status, finishedAt, errorCode}`
and `syncStale: boolean` (true when the last success is older than 36 hours). The UI shows
"Xero data as at …" separately from "page loaded at …" (UI spec §4).

---

## 7. Calculation rules (one pure module)

Package `com.airconcepts.dashboard.forecast.calc`. No Spring, no JDBC, no clock reads; the
caller passes `today` and the window. This is what makes dashboard and Excel identical.

### 7.1 Inputs: `ForecastDataset`

```
record ForecastDataset(
  UUID entityId, String entityName, YearMonth startMonth, LocalDate today,
  BigDecimal openingBalance, String openingBalanceSource,   // null when no period row exists
  List<Document> documents,        // forecast_documents joined with adjustments and split lines, outstanding = true only
  List<ManualItem> manualItems,    // active only
  List<StaffingLine> staffing,     // active only
  SyncInfo sync)
```

`ForecastDatasetLoader` (package `forecast`) is the **only** class that builds this record,
and both `ForecastService.view` and `WorkbookPopulator` consume it. Add a unit test asserting
that `WorkbookPopulator` has no dependency on any repository class.

### 7.2 Forecast date of a document

Decide in this order and record which rule fired (`DateRule` enum, shown in the UI):

1. `excluded = true` → not forecast at all (listed under "Excluded" with reason).
2. Split lines exist → each line is forecast on its own `expected_date` for its own `amount`. The document contributes nothing else.
3. `expected_date_override` → that date, full `amount_due`.
4. `xero_expected_date` → that date.
5. Otherwise `due_date`. If `due_date` is null, use `issue_date` and flag `MISSING_DUE_DATE` as an exception.

**Overdue rule (assumption, client question Q4):** if the resulting date is before `today`, the
amount is placed in the **current** month (the month containing `today`) and the row is flagged
`OVERDUE`. Implement as `OverduePolicy.CURRENT_MONTH` with the alternative
`KEEP_ORIGINAL_MONTH` available behind one constant.

The API rejects a `startMonth` after today's month, so a non-overdue date before the window
cannot occur.

### 7.3 Month buckets

For each of 12 months `m` from `startMonth`:

- `receipts(m)` = Σ ACCREC contributions dated in `m` + Σ manual items with direction IN landing in `m`.
- `payments(m)` = Σ ACCPAY contributions dated in `m` + Σ manual items with direction OUT landing in `m` + Σ staffing `monthly_cost` for lines active in `m`.
- A MONTHLY manual item lands in every month from `start_month` to `end_month` inclusive; a ONE_OFF lands in `start_month` only. `day_of_month` is informational for the detail list and Excel; it does not change the bucket.
- `net(m) = receipts(m) − payments(m)`.
- `opening(m0) = openingBalance`; `opening(m+1) = closing(m)`; `closing(m) = opening(m) + net(m)` (acceptance criterion 6).
- Amounts dated after month 12 are reported in `beyondWindow` totals so nothing is silently dropped.

Use `BigDecimal` with scale 2; sums of 2-dp values need no rounding. Round only at
presentation with `RoundingMode.HALF_UP` if a derived value ever needs it.

### 7.4 GST (FR-18, assumption pending Q10)

Xero `AmountDue`, `Total`, `AmountPaid` are GST-inclusive cash amounts. Use `amount_due`
unchanged. Manual items and staffing are entered as cash amounts (GST-inclusive where GST
applies). Never add `total_tax` on top. Document this in the Forecast page's "How this is
calculated" help text and in the spec pack. If the client later wants a GST-exclusive view,
add a toggle; do not change the stored amounts.

### 7.5 Output: `ForecastResult`

```
record ForecastResult(List<MonthRow> months, Totals totals, List<Exception> exceptions, BeyondWindow beyond)
record MonthRow(YearMonth month, BigDecimal opening, receipts, payments, net, closing,
                int receiptCount, int paymentCount, boolean containsOverdue)
record Exception(Kind kind, UUID documentId, String message)
// Kind: OVERDUE, MISSING_DUE_DATE, DATE_CONFLICT, SYNC_STALE, NO_OPENING_BALANCE, NO_SYNC_YET
```

If no `forecast_periods` row exists for (entity, startMonth), the service still returns the
movements with `opening = null`, `closing = null` and exception `NO_OPENING_BALANCE`, so the
UI can show receipts and payments and prompt for the balance. Never substitute zero for a
missing opening balance.

### 7.6 Combined view (FR-13)

`ForecastService.combined(user, List<UUID> entityIds, startMonth)`: for each requested entity
call `view(...)` (which re-checks access per entity), then sum month rows. The response lists
contributing entities, per-entity exceptions, and `complete: boolean` (false if any entity lacks
an opening balance or has a failed or stale sync). Document rows are never included in the
combined response; only month totals and per-entity subtotals. Today all active staff may read
all entities (UC5 FR-UC15-12), so "authorised" means non-archived entities plus the role rule in
§9. If per-entity permissions arrive later, this one method is where they plug in.

---

## 8. Excel pipeline

### 8.1 Flow

```
POST /api/v1/entities/{entityId}/forecast/export?startMonth=YYYY-MM   (multipart: file)
  → size/type checks → WorkbookValidator → ForecastDatasetLoader → ForecastCalculator
  → WorkbookPopulator → reconciliation check → forecast_exports row
  → 200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

Synchronous. Bound the request: multipart max 5 MiB (`spring.servlet.multipart.max-file-size`),
POI `ZipSecureFile.setMinInflateRatio(0.01)` and `setMaxEntrySize(50 MiB)` against zip bombs,
`XSSFWorkbook` opened from the uploaded bytes only. No file is written to disk or S3. The
response carries `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` and
`Content-Disposition: attachment; filename="forecast-<entityCode>-<YYYY-MM>-<yyyyMMdd-HHmm>.xlsx"`
with an RFC 5987 `filename*` for non-ASCII entity codes.

### 8.2 Template map

The real template has been inspected and is mapped cell by cell in
[`docs/specs/UC4-template-map.md`](../specs/UC4-template-map.md). The populator follows that
document exactly. The points that shape the design:

- **The workbook does its own month bucketing.** `Xero_Debtors!G` and `Xero_Creditors!G` compute a forecast month from the due date, a delay in `Assumptions!B6:B7`, and an optional override date in column `F`. The `Forecast` sheet then sums by contact and month with `SUMIFS`. To make the sheet reproduce the dashboard's numbers, the backend writes `0` into both delay cells and writes the **calculator's bucket date into column `F` of every row**. The sheet's formulas then land every amount in the same month the dashboard used, including overdue items and split lines.
- **One row per contribution, not per document.** A split schedule produces one row per split line (invoice number suffixed `(k/n)`); an excluded document produces no row.
- **Fixed capacity.** Every `Forecast` formula references `$5:$204`, so each Xero sheet holds at most 200 contributions. Exceeding it is `FORECAST_TEMPLATE_CAPACITY` with counts; the file is not returned. Widening the ranges is a template v2 change for the human (map §8).
- **Named-contact rows.** `Forecast!A8:A22` (15 debtors) and `A29:A48` (20 creditors) are manual contact names; the backend writes the largest contacts by forecast total and lets the "Other … (not listed above)" catch-all formula absorb the rest.
- **Manual items.** Receipts go into the single `Forecast!B25:M25` row as monthly sums. Payments go one item per row into `A56:M65`; with more than 10 items the smallest are aggregated into row 65 with an explicit label and an export warning.
- **Staffing is written as values.** The dashboard model is one `monthly_cost` per line. The backend writes the 12 forecast months into `Staffing!D19:O19`, the per-line monthly costs as values into `D23:O29`, clears the remaining 51 month columns, and leaves the `D30:BN30` total formula that `Forecast!B53:M53` looks up. With more than 6 lines the smallest are aggregated into one "Other staff" row with a warning.
- **Entity name.** `Assumptions!A1`, `Forecast!A1`, `Staffing!A1`, `A9` and `A22` carry the sample entity's name; the backend writes the selected entity's `display_name` upper-cased. The forecast sheet is renamed `Forecast <startYear>-<endYear>`.

No rows are inserted and no formulas are rewritten; the populator only writes values into
mapped cells and clears mapped ranges. That keeps POI usage simple and keeps the client's
formatting intact (FR-11).

### 8.3 Validation and reconciliation

`WorkbookValidator` rejects (HTTP 422 `FORECAST_TEMPLATE_INVALID`, with a `problems[]` list)
when any check in template map §6 fails: not a valid OOXML zip; VBA or external-link parts
present; a required sheet missing; header or key formula cells differ from the v1 fingerprint;
or a formula uses a function outside the supported set. The template has no version marker
cell, so v1 is recognised by its fingerprint; a marker is proposed for v2.

After populating, run `XSSFFormulaEvaluator.evaluateAllFormulaCells` and compare the
reconciliation cells in template map §7 (`Forecast!B26:M26`, `B67:M67`, `B68:M68`, `B5`) with
the `ForecastResult` month rows. If any differ by more than 0.005, set `reconciled = false`,
add a warning naming the month and both values, and **still return the file** with response
header `X-Forecast-Reconciled: false` and the warnings stored in the `forecast_exports` row;
the UI shows them before offering download. If evaluation throws for an unsupported function,
report `FORECAST_FORMULA_UNSUPPORTED` naming the cell and do not return a file.

Always call `workbook.setForceFormulaRecalculation(true)` so Excel recalculates on open.

### 8.4 Download prompt (FR-19)

The frontend posts the file, shows "Generating…", and on success holds the blob in memory and
shows a persistent notice: "Forecast ready for <entity>, <period>, generated <time>. Download
completed workbook?" with Download and Discard buttons. Nothing is saved until the user clicks
Download. Discard releases the blob. Generation never changes stored forecast data; the only
write is the `forecast_exports` audit row.

---

## 9. API contract

All routes under `/api/v1`, bearer JWT, roles resolved from `staff_users` per request (existing
`CurrentUserResolver`). Add every endpoint to the OpenAPI snapshot with a stable `operationId`,
then run the `api-contract` skill. Money is serialised as decimal strings, as in UC5. Months use
`YYYY-MM`.

| Method & path | Roles | Purpose | Notes |
| --- | --- | --- | --- |
| GET `/entities/{e}/forecast?startMonth=` | D, FA | `ForecastView`: 12 `MonthRow`s, totals, exceptions, sync info, opening balance info | `no-store`. `startMonth` defaults to the current month; at most 12 months in the past, never in the future. |
| GET `/entities/{e}/forecast/months/{YYYY-MM}/items?direction=IN\|OUT` | D, FA | `MonthItems`: document contributions (rule, overdue, split sequence), manual items, staffing lines landing that month | One month at a time. |
| GET `/entities/{e}/forecast/documents?type=&includeSettled=false&page=&pageSize=` | D, FA | Stored document list with adjustment state, for the "Xero items" tab | Server-side paging 1..100. |
| PUT `/entities/{e}/forecast/documents/{id}/adjustment` | D, FA | Body `{expectedDateOverride?: date\|null, excluded?: bool, exclusionReason?: string}` | Reason required when excluded. Audited. 409 if the document is not outstanding. |
| PUT `/entities/{e}/forecast/documents/{id}/splits` | D, FA | Body `{lines: [{expectedDate, amount}]}`; empty array removes the schedule | Sum must equal `amount_due` → 422 `FORECAST_SPLIT_SUM_MISMATCH`. Clears any conflict. |
| POST `/entities/{e}/forecast/documents/{id}/conflict` | D, FA | Body `{resolution: "KEEP_SCHEDULE" \| "DISCARD_SCHEDULE"}` | 409 if no open conflict. |
| GET / PUT `/entities/{e}/forecast/periods/{YYYY-MM}/opening-balance` | D, FA | `{openingBalance, source?}` | Audited. |
| GET / POST `/entities/{e}/forecast/manual-items`; PATCH `.../{id}`; POST `.../{id}/deactivate` | D, FA | CRUD with soft delete | Validation per §5.1. |
| GET / POST `/entities/{e}/forecast/staffing`; PATCH `.../{id}`; POST `.../{id}/deactivate` | D, FA | CRUD with soft delete | Never exposed to STANDARD_USER; keep out of any aggregate DTO. |
| GET `/entities/{e}/forecast/sync` | D, FA | Last 10 runs | |
| POST `/entities/{e}/forecast/sync` | D, FA | Manual run; returns the run row | 409 `FORECAST_SYNC_IN_PROGRESS`; 429 passthrough with Retry-After. |
| POST `/entities/{e}/forecast/export?startMonth=` (multipart) | D, FA | Populated `.xlsx` | §8. |
| GET `/entities/{e}/forecast/audit?page=&pageSize=` | D, FA | Audit rows | |
| GET `/forecast/combined?startMonth=&entityIds=a,b,c` | D, FA | `CombinedForecastView` | Every ID must be a non-archived entity; one failure fails the request (no partial aggregate presented as complete). |

Role shorthand: D = DIRECTOR, FA = FINANCIAL_ADMIN. STANDARD_USER receives 403 on every
forecast route because bills and staffing are included (consistent with UC5's ACCPAY rule).
Director write access is an assumption (Q7); implement the check as a single
`ForecastAccess.requireWrite(user)` so it can tighten to FA-only in one place.

Error codes follow the existing `ApiFailure` pattern: `FORECAST_PERIOD_INVALID`,
`FORECAST_DOCUMENT_NOT_OUTSTANDING`, `FORECAST_SPLIT_SUM_MISMATCH`,
`FORECAST_EXCLUSION_REASON_REQUIRED`, `FORECAST_TEMPLATE_INVALID`,
`FORECAST_TEMPLATE_CAPACITY`, `FORECAST_FORMULA_UNSUPPORTED`, `FORECAST_SYNC_IN_PROGRESS`,
`FORECAST_SYNC_TOO_MANY_CHANGES`, `FORECAST_NO_SYNC_YET`, `FORECAST_COMBINED_INCOMPLETE`.

---

## 10. Frontend

Follow `docs/ui-ux-design-specification.md`. Feature folder `frontend/src/forecast/`. Use the
existing `DashboardApi`, `useResource`, `useAction`, `Feedback`, `format` and `Modal`
primitives; add methods to `DashboardApi` and type guards to `api/contracts.ts` from the
regenerated schema. No new frontend dependency.

### 10.1 Navigation

Add **Forecast** to the sidebar in `Workspace.tsx` after Invoices (UI spec order ends
"…, Payables, Forecast"). Visible to DIRECTOR and FINANCIAL_ADMIN only. Route state becomes
`"invoices" | "forecast" | "settings"`. Lazy-load the Forecast feature like Settings.

### 10.2 Components

| Component | Responsibility |
| --- | --- |
| `Forecast.tsx` | Feature root. Owns `startMonth` state (default current month) and tabs: **Forecast**, **Xero items**, **Manual items**, **Staffing**, **History**. Resource key includes `subject:entityId:startMonth:tab`. |
| `ForecastHeader.tsx` | Entity name, period selector (month picker limited to the allowed range), "Xero data as at <lastSuccessfulSyncAt>" with stale badge, "Page loaded at", **Sync now** button (busy state, 409/429 messages), **Export to Excel** button. |
| `ForecastTable.tsx` | 12-month table: Month, Opening, Receipts, Payments, Net, Closing. Money right-aligned via the existing formatter; `null` opening renders "Not recorded" with an "Enter opening balance" link, never 0.00. Negative closing gets a warning badge plus text. Row click opens `MonthDetail`. Mobile: cards per month per UI spec §5. |
| `MonthDetail.tsx` | Side pane on desktop, separate view with Back on mobile. Two lists: Receipts and Payments. Each document row: number, contact, forecast date, rule badge (`Xero expected date` / `Due date` / `Override` / `Split 2 of 3`), overdue badge, amount, actions: Change date, Split, Exclude. Manual and staffing rows labelled by source. |
| `ExceptionsPanel.tsx` | Open conflicts, overdue, missing due date, stale sync, no opening balance; each with the permitted next action. |
| `AdjustmentDialog.tsx`, `SplitDialog.tsx`, `ExclusionDialog.tsx`, `ConflictDialog.tsx` | Focused edits in `Modal`. Show entity, document and current amount before confirming. Split dialog live-validates sum vs amount due and blocks save on mismatch. |
| `OpeningBalanceForm.tsx` | Amount plus source. Persistent label, inline validation, preserve input on error. |
| `ManualItems.tsx`, `StaffingLines.tsx` | Tables with add, edit and deactivate; soft-deleted rows hidden behind "Show inactive". |
| `ExcelExportDialog.tsx` | Steps: choose file (`accept=".xlsx"`, client-side size check ≤ 5 MiB) → Generate → progress → success prompt (§8.4) or problem list from 422. Fetch returns a Blob; read `X-Forecast-Reconciled`. |
| `CombinedForecast.tsx` | Reached from an **All entities** option that the Forecast route adds to the entity selector for D/FA (UC5 selector behaviour elsewhere is unchanged). Shows totals, per-entity subtotals and the incomplete marker. No document rows. |
| `SyncHistory.tsx`, `AuditList.tsx` | Read-only lists. |

### 10.3 Behaviour rules

- Changing entity or month clears detail selection and dialogs, shows loading, and never shows one entity's rows under another heading (reuse the generation and abort pattern already in `Workspace.tsx` and `useResource`).
- After a confirmed mutation, refresh the forecast resource and the month items; do not optimistically update money.
- Dirty-state protection for open dialogs via the existing `dirty` flag in `Workspace`.
- No forecast data in the URL or localStorage; only the chosen tab may be remembered per user.
- All feedback states from UI spec §4: skeleton on first load, "refreshing" indicator with last loaded time, empty ("No outstanding items for this month"), error with retry, permission message for STANDARD_USER (defensive; the route is hidden).
- Verify at 320, 390, 768, 1024 and 1440 px in Chromium (Chrome/Chromium-only acceptance per the AGENTS.md learned rule).

---

## 11. Test plan

Name each test after its requirement ID. Write the failing test before the implementation
(AGENTS.md §5 step 5). Levels: U = unit, I = disposable PostgreSQL (Testcontainers,
`DockerRequirement` pattern), H = Spring MVC HTTP test with mocked services, P = Playwright
Chromium.

| ID | Test (class.method or scenario) | Level |
| --- | --- | --- |
| T-01/02 | `XeroForecastClientTest`: builds exact `where`/`order`/`If-Modified-Since`; maps ExpectedPaymentDate and PlannedPaymentDate; rejects malformed pagination, duplicate IDs, wrong type | U |
| T-01/02/17 | `ForecastDocumentRepositoryTest`: upsert twice with the same payload → 1 row; changed amount_due → updated; settled document flips `outstanding` and sets `settled_at`; adjustments row untouched by sync | I |
| T-17 | `ForecastSyncServiceTest`: provider failure on page 2 rolls back that type; run row FAILED with code only; concurrent run → `FORECAST_SYNC_IN_PROGRESS`; rate-limited health → no provider call | U |
| T-16 | `ForecastSyncServiceTest.frUc04_16_dateChangeWithSplitsFlagsConflictAndKeepsLines` | U + I |
| T-03 | `ManualItemServiceTest`: validation matrix; soft delete; audit row written; cross-entity ID → 404 | U + I |
| T-04 | `ForecastPeriodServiceTest`: upsert opening balance; non-first-day month rejected; audit | U |
| T-05 / AC-6 | `ForecastCalculatorTest`: closing = opening + receipts − payments; next opening = previous closing; 12 rows; beyond-window totals | U |
| T-14 | `ForecastCalculatorTest.frUc04_14_expectedDateBeatsDueDate`, `..._overrideBeatsXeroDate` | U |
| T-15 | `ForecastCalculatorTest`: part-paid uses amount_due; excluded contributes zero and appears in exclusions; split lines sum once; overdue goes to the current month with flag | U |
| T-18 | `ForecastCalculatorTest.frUc04_18_doesNotAddTaxOnTopOfInclusiveAmounts` | U |
| T-20 | `ForecastCalculatorTest.frUc04_20_groupsByForecastDateNotIssueMonth` | U |
| T-06 | `ForecastEndpointTest`: view includes sync info, stale flag at 36 h, exceptions | H |
| T-07 / roles | `ForecastEndpointTest`: STANDARD_USER 403 on every forecast route; inactive user 403; FA and D 200; staffing never in combined DTO | H |
| T-08 | `Uc4SchemaTest`: RLS on, no browser privileges, FKs restrict deletes, check constraints hold for every new table | I |
| T-13 | `ForecastServiceTest.frUc04_13_combinedSumsOnlyRequestedEntitiesAndNeverReturnsDocuments`; archived entity → 404; one failing entity → whole request fails | U + H |
| T-09/12 | `WorkbookValidatorTest`: missing sheet, renamed header, altered `Forecast!B68` formula, `.xlsm`, oversized, zip bomb ratio, unsupported function → `FORECAST_TEMPLATE_INVALID` or `FORECAST_FORMULA_UNSUPPORTED` with problem list; the v1 fixture passes | U |
| T-10/11 | `WorkbookPopulatorTest`: fixture populated from a fixed `ForecastDataset`; all formulas preserved; entity name written in the five title cells; delay cells are 0 and every Xero row has its bucket date in column F; split lines become `(k/n)` rows; excluded documents absent; 200 contributions fit and 201 fail with `FORECAST_TEMPLATE_CAPACITY`; 11 manual payment items aggregate into row 65 with a warning; 7 staffing lines aggregate with a warning; `Staffing!P19:BN30` cleared | U |
| T-21 / AC-8 | `ForecastReconciliationTest`: evaluate the populated fixture; `Forecast!B26:M26`, `B67:M67`, `B68:M68` equal the calculator's receipts, payments and closing for datasets with overdue items, splits, overrides, manual items and staffing; an injected mismatch sets `reconciled=false` and the header | U |
| T-19 | `ForecastExportEndpointTest`: content type, filename pattern, `no-store`, `nosniff`; export writes only `forecast_exports`; invalid upload leaves all tables unchanged | H + I |
| T-sec | `ForecastEndpointTest`: entity A's document ID under entity B → 404; adjustment on settled document → 409; split sum mismatch → 422; reason required → 422 | H |
| T-UI | Vitest: `ForecastTable` renders "Not recorded" for null opening; negative closing badge; month click opens detail; entity switch aborts and clears; `ExcelExportDialog` shows the prompt only after success and never auto-downloads; 422 problems listed | U |
| T-P | Playwright Chromium: FA signs in → Forecast → enters opening balance → sees 12 months → excludes an invoice with reason → totals change → uploads fixture → download prompt appears; STANDARD_USER has no Forecast destination | P |

Run before claiming done (quote command and result):

```
cd backend  && ./mvnw -q -B verify
cd backend  && ./mvnw -q test -Dopenapi.update=true     # when controllers or DTOs changed
cd frontend && npm run api:generate && npm run api:check
cd frontend && npm run lint --silent && npm run typecheck --silent && npm test --silent && npm run build --silent
./scripts/check-all.sh
```

Report Docker-backed tests as "not run" when Docker is unavailable locally; CI runs them.

---

## 12. Delivery sequence (one branch, one PR, one session per slice)

Keep each PR near the 300-line guideline. Branch names
`feat/fr-uc04-<nn>-<short-name>`; commits reference the FR IDs. Each PR lists requirement IDs,
test evidence, security checklist, reviewing agent (Codex reviews Claude's code and vice versa)
and a migration checklist where relevant. Stop at an open PR; humans merge.

| # | Slice | Depends on | Main files | Gate |
| --- | --- | --- | --- | --- |
| 0 | Spec pack `docs/specs/UC4.md`, conflict decision G2, spikes S1–S3 and S6 | — | docs only | G1, G2, G3 |
| 1 | **Schema A**: migration `..._uc4_forecast_documents_and_sync.sql` (`forecast_documents`, `forecast_sync_runs`, `forecast_audit`); `Uc4SchemaTest` | 0 | supabase/migrations, backend test | G6 |
| 2 | **Xero forecast client**: `XeroForecastClient`, `ForecastSource` records, mock-transport tests; `XeroConnectionService.systemAccess` | 0, S1 | backend `xero/` | G7 (Xero) |
| 3 | **Sync service, scheduler and manual trigger API**: `ForecastSyncService`, `ForecastDocumentRepository`, `SchedulingConfig`, `GET/POST .../forecast/sync`; OpenAPI regen | 1, 2 | backend `forecast/sync`, `web/ForecastSyncController` | G7 |
| 4 | **Schema B**: migration `..._uc4_forecast_assumptions.sql` (`forecast_document_adjustments`, `forecast_split_lines`, `forecast_periods`, `forecast_manual_items`, `forecast_staffing`, `forecast_exports`); extend `Uc4SchemaTest` | 1 | supabase/migrations | G6 |
| 5 | **Calculator**: `forecast/calc/*`, exhaustive unit tests (T-05, 14, 15, 18, 20) | none (pure) | backend `forecast/calc` | — |
| 6 | **Assumptions APIs**: periods and opening balance, manual items, staffing, audit list; `ForecastAccess`; OpenAPI regen | 4 | backend `forecast/`, `web/` | G6 |
| 7 | **Adjustment APIs**: adjustment, splits, conflict resolution; sync conflict flagging (§6.4) | 3, 4 | backend | G7 (entity isolation) |
| 8 | **Forecast view, month items and combined APIs**: `ForecastDatasetLoader`, `ForecastService`, controllers; OpenAPI regen | 5, 6, 7 | backend | G6 |
| 9 | **Excel validator and populator** per `docs/specs/UC4-template-map.md` against the scrubbed fixture; spike S5 note; POI dependency | G4, G5, 5 | backend `forecast/excel`, `pom.xml`, `backend/src/test/resources/forecast/template-v1.xlsx` | G4, G5 |
| 10 | **Export API**: multipart endpoint, reconciliation, `forecast_exports`, headers | 8, 9 | backend `web/ForecastExportController` | G6 |
| 11 | **Frontend: shell, forecast table, header, exceptions** (`Forecast.tsx`, `ForecastHeader`, `ForecastTable`, `ExceptionsPanel`, `OpeningBalanceForm`; nav entry) | 8 | frontend `forecast/`, `workspace/Workspace.tsx`, `api/` | — |
| 12 | **Frontend: month detail and adjustment dialogs** | 11 | frontend | — |
| 13 | **Frontend: manual items, staffing, history** | 11 | frontend | — |
| 14 | **Frontend: Excel export dialog** | 10, 11 | frontend | — |
| 15 | **Frontend: combined view and All entities option** | 11 | frontend | — |
| 16 | **Acceptance**: Playwright journey, responsive and accessibility evidence, `docs/requirements/UC4-coverage.md` ledger, operations note `docs/uc4-forecast-operations.md` (sync schedule, rerun, rollback) | all | docs, e2e | Human Test acceptance |

Slices 1–3 can be reviewed and merged before any assumption work starts. That gets real Xero
data flowing into Test early and lets the client validate the "outstanding" definition (Q3).

---

## 13. Open questions, with the default assumption the code must make switchable

| # | Question (from the Notion page) | Default assumption in this plan | Where to change it |
| --- | --- | --- | --- |
| Q1 | Does this month's closing become next month's opening? | Yes (acceptance criterion 6 says so). | Confirmed |
| Q2 | Where does the opening balance come from? | Entered manually by FA with a free-text source. No bank feed. | `forecast_periods.opening_balance_source` |
| Q3 | Which Xero statuses count as unpaid? | AUTHORISED with AmountDue > 0 only. SUBMITTED excluded. | `OutstandingRule` constant used by the sync `where` clause and the `outstanding` flag |
| Q4 | Overdue items: current month or original month? | Current month, flagged OVERDUE. | `OverduePolicy` |
| Q5 | Supplier bill date: always Xero due date, or adjustable? | Xero PlannedPaymentDate if present, else DueDate; override allowed through the same adjustment endpoint as invoices. | `DateRule` ordering applies to both types |
| Q6 | FR-14 "reflected in Xero where supported": write ExpectedPaymentDate back to Xero? | **Not in this delivery.** Overrides are stored and audited locally. Write-back needs a new write scope, re-consent by the Director, an idempotent retry design and separate human approval. Record as a follow-up task. | Spike S4; future slice |
| Q7 | Can the Director edit, or only review? | Director and FA can both edit. | `ForecastAccess.requireWrite` |
| Q8 | One-off items not in Xero? | `forecast_manual_items` with recurrence ONE_OFF. | — |
| Q9 | Entity-specific template columns? | None; the inspected template is used for every entity, with the sample's entity name and staff rows overwritten. The template's 200-row Xero cap, 10 manual payment rows and 6 staffing rows are v1 limits; template map §8 lists the v2 changes to ask the client for. | Template map versioning |
| Q10 | GST or inter-entity transfers excluded? | GST-inclusive cash amounts throughout; transfers entered as manual items with category TRANSFER so they can be filtered later. | §7.4; `category` |
| Q11 | Re-import of edited workbooks? | Not supported; upload is template-only (the page says this needs separate approval). | — |
| Q12 | Email delivery of the workbook? | Not built; on-screen plus download only. | — |

Also unresolved from the decision page: Supabase hosting region and Privacy Act 2020 treatment
of wage data. This plan keeps staffing data server-side only and role-restricted; the region
question belongs to infrastructure, not this use case.

---

## 14. Security checklist for every slice

- Role and entity checked on the server for every request; STANDARD_USER never reaches forecast data.
- Document, item and schedule IDs are always resolved together with `entity_id`; a foreign ID returns 404, never another entity's row.
- Scheduled sync uses `systemAccess` only; no controller path reaches it.
- Tokens stay inside `TenantAccess` and `XeroAccess`; sync run rows store codes and HTTP status only.
- Uploaded workbooks: size cap, OOXML only, zip-bomb guards, no macro files, parsed in memory, never persisted.
- Export response: `no-store`, `nosniff`, attachment disposition, filename built from the validated entity code and dates only.
- Audit rows carry changed columns only, never tokens or raw provider bodies.
- Migrations: forward-only, RLS on, explicit grants, `lock_timeout`, the single scoped delete (split-line replacement) flagged for approval.
- No hosted database connection from agents; CI applies migrations to Test; no Live operations.

---

## 15. Definition of done for UC4

Every row in section 3 is linked to passing tests from section 11, including negative and
security cases; `docs/requirements/UC4-coverage.md` records evidence per requirement; the OpenAPI
snapshot and generated types are in sync; `./scripts/check-all.sh` passes; Docker-backed tests
pass in CI; the other agent has reviewed each PR; the Chromium journey passes at the five
widths; the operations note explains schedule, manual rerun and rollback (migrations stay, the
app artefact rolls back); open client questions and their switchable defaults are listed in the
spec pack; human acceptance on Test is recorded. Anything not observed is reported as not run.
