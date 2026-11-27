# Spec pack: UC4 — Financial forecast and business cash position

- Status: approved for implementation in this goal
- Approved by / date: human goal instruction and delegated decisions, 10 November 2026
- Notion page URL: **not supplied — human to add** (page title "Use Case 4 — Prepare and Review the Financial Forecast and Business Cash Position")
- Notion last-edited date: 9 November 2026 (status on the page: "Draft for business review")

Source text for this pack is the human-authorised technical plan [`docs/plans/UC4.md`](../plans/UC4.md) (written 10 November 2026 from the Notion page) and the workbook contract [`docs/specs/UC4-template-map.md`](UC4-template-map.md). Spike results are in `docs/spikes/uc4-*.md`. The agent did not read Notion for this pack.

## Goal
- Director and Financial Admin see, per entity and combined, a rolling 12-month cash forecast built from Xero's outstanding invoices and bills plus dashboard-entered assumptions, and can optionally fill the client's standard Excel workbook from the same stored data. No emailed spreadsheets; Excel never writes back.

## Source pages and last-edited dates
| Page | URL | Last edited |
|---|---|---|
| Use Case 4 — Prepare and Review the Financial Forecast and Business Cash Position | not supplied | 9 November 2026 |
| Decision: store forecast data in Supabase | not supplied | 21 October 2026 |
| Client workbook `docs/ACSL Cashflow Forecast Template - Cleaned.xlsx`, mapped in [`UC4-template-map.md`](UC4-template-map.md) | repository | inspected 10 November 2026 |
| Technical plan [`docs/plans/UC4.md`](../plans/UC4.md) | repository | 10 November 2026 |

## Requirement IDs
Forecast retains FR-UC04-01 to 21. The human delegated the numbering decision on 10 November 2026; supplier-bill requirements must be qualified by use case until separately renumbered. This delivery does not edit supplier-bill scope.

| ID | Requirement |
|---|---|
| FR-UC04-01 | Sync each entity's outstanding sales invoices (ACCREC) from its Xero organisation into PostgreSQL, keyed by Xero organisation and document ID, keeping unpaid balance, dates and status, with no duplicates. |
| FR-UC04-02 | The same for unpaid supplier bills (ACCPAY). |
| FR-UC04-03 | Authorised users maintain recurring direct debits, payments, salaries and wages and other manual items per entity. |
| FR-UC04-04 | Financial Admin enters the opening cash balance per entity and forecast period. |
| FR-UC04-05 | Show 12 months with opening balance, expected receipts, expected payments, net movement and projected closing balance. |
| FR-UC04-06 | Show entity, period, last successful sync and exceptions. |
| FR-UC04-07 | Director and Financial Admin review the forecast in the dashboard; no emailed Excel is needed. |
| FR-UC04-08 | Store every assumption, manual value, adjustment, schedule and audit record in PostgreSQL, partitioned by entity and period. |
| FR-UC04-09 | Per Excel generation the user uploads one `.xlsx` based on the common template; the system captures entity and period and validates before processing. |
| FR-UC04-10 | Populate the uploaded workbook from the same stored dataset the dashboard uses, never from a separate Xero fetch. |
| FR-UC04-11 | Map data onto worksheets Assumptions, Forecast, Xero_Debtors, Xero_Creditors and Staffing; keep formulas and formatting; no hard-coded entity names; never truncate silently. |
| FR-UC04-12 | Validate the uploaded workbook (sheets, layout, mappings, supported formulas); reject with clear errors; never touch stored data on rejection. |
| FR-UC04-13 | Separate view per entity; combined view only over entities the user is authorised for, never exposing another entity's rows. |
| FR-UC04-14 | Customer invoice forecast date is Xero `ExpectedPaymentDate` when present, else `DueDate`; authorised changes are audited and "reflected in Xero where supported". |
| FR-UC04-15 | Handle partial payments (use `AmountDue`), overdue items, optional split-payment schedules and manual exclusions with a reason; no double counting; exclusions persist until reversed. |
| FR-UC04-16 | If Xero's expected date changes while a split schedule exists, keep the schedule, flag a conflict and require authorised review. |
| FR-UC04-17 | Daily idempotent reconciliation from Xero to the database reflecting new, settled, part-paid and changed documents and sync errors; never erase dashboard adjustments; keep last-sync timestamp and audit. |
| FR-UC04-18 | GST-inclusive treatment applied consistently to receipts and monthly totals. |
| FR-UC04-19 | On successful population tell the user the file is ready and prompt to download; the filename carries entity, period and generation time; export never alters stored records. |
| FR-UC04-20 | Monthly, entity-specific views of expected income and payments grouped by forecast date, not issue month. |
| FR-UC04-21 | Dashboard and Excel consume the same canonical data and produce matching totals. |

## Acceptance criteria
Roles: D = DIRECTOR, FA = FINANCIAL_ADMIN, SU = STANDARD_USER. Design detail (schema, algorithm, API shape) is in the plan; the criteria below are what must be observed.

Sync
- AC-1 (FR-UC04-01, 02): Given an entity with a connected Xero organisation holding AUTHORISED documents with `AmountDue > 0` of both types, when the daily sync runs, then every such document exists once in `forecast_documents` with its type, number, contact, dates, `xero_expected_date`, `amount_due`, status and `outstanding = true`, and running the sync again with unchanged Xero data changes no row.
- AC-2 (FR-UC04-17): Given a stored outstanding document that Xero now reports as PAID, VOIDED or absent (404), when the sync runs, then the row's `outstanding` becomes false, `settled_at` is set, and the document stops contributing to the forecast.
- AC-3 (FR-UC04-17): Given a document with a dashboard adjustment, exclusion or split schedule, when any number of syncs run, then the adjustment, exclusion and split rows are unchanged except for the conflict columns.
- AC-4 (FR-UC04-17, 06): Given Xero fails on page 2 of a type, when the sync runs, then no row of that type is updated or settled in that run, the run row is FAILED with an error code and HTTP status only (no provider body), and the dashboard shows the last successful sync time and a stale badge after 36 hours.
- AC-5 (FR-UC04-17): Given a run is already RUNNING for the entity and started less than 30 minutes ago, when a manual sync is requested, then the response is 409 `FORECAST_SYNC_IN_PROGRESS` and no provider call is made. Given `XeroReadHealth` holds a pending `Retry-After`, then the run is FAILED with `XERO_RATE_LIMITED` and no provider call is made.
- AC-6 (FR-UC04-16): Given a document with a split schedule, when a sync brings a different `xero_expected_date` or `amount_due`, then the split lines are untouched, the adjustment row carries `date_conflict_since`, the previous and new Xero dates, the calculator keeps using the split lines, and the conflict appears under Exceptions until a D or FA resolves it with KEEP_SCHEDULE or DISCARD_SCHEDULE.
- AC-7 (FR-UC04-17, security): Given the scheduled sync, when it resolves Xero access, then it uses `XeroConnectionService.systemAccess` which no controller can reach; the manual trigger requires D or FA through `authorisedAccess`.

Assumptions
- AC-8 (FR-UC04-04): Given FA enters an opening balance and source for an entity and start month, when saved, then the value is stored for (entity, start_month), an audit row records actor, before and after, and a start month that is not the first day of a month is rejected.
- AC-9 (FR-UC04-05): Given no opening balance exists for the period, when the forecast is viewed, then receipts and payments are shown, opening and closing are null and rendered "Not recorded", exception `NO_OPENING_BALANCE` is listed, and zero is never substituted.
- AC-10 (FR-UC04-03): Given D or FA creates a MONTHLY manual item with a start month and optional end month, when the forecast is computed, then the amount lands in every month from start to end inclusive; a ONE_OFF lands only in its start month; deactivation hides the item from all calculations without deleting the row; every change is audited; an invalid item (amount ≤ 0, description empty or over 200 characters, unknown category, `day_of_month` outside 1–31) is rejected with a field-level error.
- AC-11 (FR-UC04-03, security): Given a staffing line, when any API response is produced for SU, then no staffing field appears, including in combined and month-item DTOs; D and FA can create, edit and deactivate staffing lines with audit.

Calculation
- AC-12 (FR-UC04-05): For each of the 12 months, `closing = opening + receipts − payments` and the next month's opening equals this month's closing; exactly 12 rows are returned; amounts dated after month 12 are reported under `beyondWindow`, never dropped.
- AC-13 (FR-UC04-14): Given an ACCREC with both `xero_expected_date` and `due_date`, the forecast date is the expected date; given also an `expected_date_override`, the override wins; the rule that fired is returned as `DateRule`. The same ordering applies to ACCPAY using `PlannedPaymentDate`.
- AC-14 (FR-UC04-15): A part-paid document contributes `amount_due`, not `total`; an excluded document contributes nothing and appears in the exclusions list with its reason until the exclusion is reversed; a split schedule contributes each line once on its own date and nothing else; a document whose forecast date is before today lands in the current month flagged `OVERDUE` (`OverduePolicy.CURRENT_MONTH`, switchable).
- AC-15 (FR-UC04-15): Given split lines whose amounts do not sum to `amount_due`, when saved, then the response is 422 `FORECAST_SPLIT_SUM_MISMATCH` and nothing is stored; an empty list removes the schedule inside one transaction.
- AC-16 (FR-UC04-15): Given an exclusion without a reason, or a reason over 500 characters, when saved, then 422 `FORECAST_EXCLUSION_REASON_REQUIRED` and nothing is stored.
- AC-17 (FR-UC04-18): `total_tax` is never added to `amount_due`, manual items or staffing; totals equal the sum of GST-inclusive cash amounts.
- AC-18 (FR-UC04-20): A document issued in month A and due in month B contributes to month B only; the month-items endpoint for month B lists it with rule, overdue flag and split sequence, and for month A does not.
- AC-19 (FR-UC04-13): Given a combined request for entities X and Y, the response contains month totals and per-entity subtotals only, no document rows; `complete` is false when any entity lacks an opening balance or has a failed or stale sync; an archived or unknown entity ID fails the whole request with 404 and no partial aggregate.

Access and isolation
- AC-20 (FR-UC04-07, 13, security): SU receives 403 on every forecast route; an inactive user receives 403; D and FA receive 200. Director write access is governed by one `ForecastAccess.requireWrite` check (assumption Q7).
- AC-21 (security): Given a document, manual item, staffing line or schedule ID that belongs to entity A, when requested under entity B, then 404 and no data from A is returned.
- AC-22 (security): Given an adjustment or split request for a document that is not outstanding, then 409 `FORECAST_DOCUMENT_NOT_OUTSTANDING`.
- AC-23 (FR-UC04-08): Every new table has RLS enabled and no privileges for `anon` or `authenticated`; `delete` is granted only where the plan names a scoped delete; audit rows contain changed columns only and never tokens or provider bodies.

Excel
- AC-24 (FR-UC04-09, 12): Given an upload that is not a valid OOXML zip, contains a VBA or external-link part, lacks a required sheet, has a header or key formula that differs from the v1 fingerprint, or uses a function outside the supported set, when posted, then 422 `FORECAST_TEMPLATE_INVALID` or `FORECAST_FORMULA_UNSUPPORTED` with a `problems[]` list naming sheet and cell, and no table changes. Files over 5 MiB and non-`.xlsx` types are rejected before parsing. The v1 fixture passes.
- AC-25 (FR-UC04-10, 11): Given the v1 fixture and a fixed dataset, when populated, then every formula outside the mapped write ranges is unchanged, number formats are unchanged, the entity's display name (upper-cased) appears in the five title cells, both delay cells hold 0, every Xero row carries the calculator's bucket date in column F, split lines appear as `(k/n)` rows, excluded documents are absent, the forecast sheet is renamed `Forecast <startYear>-<endYear>`, and `Staffing!P19:BN30` is cleared.
- AC-26 (FR-UC04-11): Given more than 200 contributions on either Xero sheet, then 422 `FORECAST_TEMPLATE_CAPACITY` with counts and no file; given more than 10 manual payment items or more than 6 staffing lines, then the smallest are aggregated into the last row with an explicit label and an export warning, never silently.
- AC-27 (FR-UC04-21): After population and `evaluateAllFormulaCells`, `Forecast!B26:M26`, `B67:M67`, `B68:M68` and `B5` equal the calculator's receipts, payments, closing and opening balance within 0.005 for datasets containing overdue items, splits, overrides, manual items and staffing. An injected mismatch sets `reconciled = false`, the `X-Forecast-Reconciled: false` header, a warning naming month and both values, and the file is still returned.
- AC-28 (FR-UC04-19): A successful export responds with the spreadsheet content type, `Content-Disposition: attachment` with filename `forecast-<entityCode>-<YYYY-MM>-<yyyyMMdd-HHmm>.xlsx`, `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`; the only database write is one `forecast_exports` row; the UI shows "Forecast ready … Download completed workbook?" with Download and Discard and never auto-downloads.

UI
- AC-29 (FR-UC04-05, 06, 07): The Forecast destination is visible to D and FA only, shows entity, period selector, "Xero data as at", stale badge, Sync now and Export; the 12-month table renders null opening as "Not recorded" with an "Enter opening balance" link and flags negative closing; switching entity or month aborts in-flight requests and never shows one entity's rows under another heading; all states (skeleton, refreshing, empty, error with retry, permission message) exist; verified at 320, 390, 768, 1024 and 1440 px in Chromium.

## Confirmed decisions
- G1/G2: the human instructed implementation from this pack, then delegated open choices ("read through them all. use your own decisions") on 10 November 2026. Forecast retains its existing IDs to preserve all acceptance/test mappings; unrelated supplier-bill documents remain unchanged.
- G4: `org.apache.poi:poi-ooxml:5.4.1` is approved; no other new dependency. G6/G7: authorisation level A applies; record each slice's plan and contracts and continue without approval pauses. Independent Claude Code review remains pending, never self-approved.
- Q2–Q12 defaults and the entity/document-scoped split-line replacement delete are approved in the goal instruction. G5 remains a human confirmation of the concrete scrubbed fixture before its PR is ready.
- The 3 November stack decision applies: React/Vite on S3/CloudFront and Spring Boot on EC2. No Notion reads or edits occur in this goal; missing source-page URLs are provenance gaps, not authority to broaden scope.
- Initial publication attempt: local Git writes and CLI network access were denied; connected GitHub branch creation required unavailable approval. The human then created the local branch and instructed completing development before commits. Local implementation proceeds on that branch; commit/PR publication is deferred until development is finished.
- Workbook conflict ruling: preserve mapped total formulas D30:O30, clear P19:BN30, and overwrite every D23:O29 cell with value or blank. This follows AC-25 and the explicit goal over the map's broader instruction to preserve D30:BN30.
- Forecast data is stored in Supabase PostgreSQL (Notion decision "Decision: store forecast data in Supabase", 21 October 2026).
- This month's closing becomes next month's opening (the Notion page's acceptance criterion 6; recorded as Q1 confirmed in the plan).
- The human authorised the agent to read the Notion page for the plan on 10 November 2026 and asked for this spec pack and the UC4 spikes on 10 November 2026.
- Delivery sequence, data model, calculation rules and API contract follow [`docs/plans/UC4.md`](../plans/UC4.md) §5–§12 unless a human changes them during gate reviews.

## Out of scope
- Writing `ExpectedPaymentDate` back to Xero (FR-UC04-14 "where supported"): deferred to a follow-up task; see spike S4 and Q6.
- Re-import of edited workbooks (Q11); email delivery of the workbook (Q12); bank feeds for the opening balance (Q2).
- Template v2 changes (template map §8).
- Resolving the Supabase hosting region or the Privacy Act 2020 treatment of wage data (infrastructure; this pack keeps staffing data server-side and role-restricted).

## Open questions
The decisions above resolve OQ-1/2/4/6 and approve Q2–Q12 defaults. OQ-3 is a provenance follow-up; OQ-5 is deferred to the concrete slice 9 scrub review. The historical questions below remain for traceability and do not reopen resolved gates.

| # | Question | Owner | Default in plan |
|---|---|---|---|
| OQ-1 | **Known conflict (AGENTS.md §2):** FR-UC04-01 to 07 are also used by the supplier bill use case. Which use case keeps the IDs? This pack uses the forecast page's IDs until answered. | Human | none; blocks slice 0 |
| OQ-2 | **Known conflict:** the UC4 architecture page shows Next.js on Vercel; the 3 November infrastructure decision (React/Vite on S3/CloudFront, Spring Boot on EC2) supersedes it. Confirm and correct the Notion page. | Human | plan assumes the 3 November stack |
| OQ-3 | Notion page URLs for the use case and the Supabase decision are missing from this pack. | Human | — |
| OQ-4 | Approve new backend dependency `org.apache.poi:poi-ooxml:5.4.1` (gate G4). Spike S5 tested this version. | Human | — |
| OQ-5 | Confirm the scrubbed fixture `backend/src/test/resources/forecast/template-v1.xlsx` once the agent removes the four staff names and the hourly rate (gate G5). | Human | — |
| OQ-6 | Approve the single scoped delete (split-line replacement) (AGENTS.md §8). | Human | — |
| Q2 | Where does the opening balance come from? | Client | manual entry by FA with free-text source |
| Q3 | Which Xero statuses count as unpaid? Spike S1–S3 confirms AUTHORISED is the only approved open status. | Client | `AUTHORISED and AmountDue > 0`; SUBMITTED excluded |
| Q4 | Overdue items: current month or original month? | Client | current month, flagged OVERDUE |
| Q5 | Supplier bill date: Xero `PlannedPaymentDate` else `DueDate`, overridable? | Client | yes, same rule and endpoint as invoices |
| Q6 | Write expected date back to Xero? | Client / Human | not in this delivery (spike S4 lists prerequisites) |
| Q7 | Can the Director edit or only review? | Client | D and FA both edit (`ForecastAccess.requireWrite`) |
| Q8 | One-off items not in Xero? | Client | `forecast_manual_items` with ONE_OFF |
| Q9 | Entity-specific template columns? | Client | none; one template, 200-row / 10-row / 6-row v1 caps |
| Q10 | GST and inter-entity transfers? | Client | GST-inclusive cash amounts; transfers as manual items with category TRANSFER |
| Q11 | Re-import of edited workbooks? | Client | not supported |
| Q12 | Email delivery of the workbook? | Client | not built |

## External dependencies needing a spike
| Capability | Spike note (docs/spikes/) | Status |
|---|---|---|
| S1 `ExpectedPaymentDate` / `PlannedPaymentDate` present in `GET /Invoices` list responses with `summaryOnly=true` | [uc4-xero-forecast-fields.md](../spikes/uc4-xero-forecast-fields.md) | partial (docs confirm; Test-tenant check added to slice 3 acceptance) |
| S2 `If-Modified-Since` sees payments, credits, PAID/VOIDED | [uc4-xero-forecast-fields.md](../spikes/uc4-xero-forecast-fields.md) | not confirmed → settlement re-read (§6.3 step 6) is mandatory |
| S3 outstanding statuses | [uc4-xero-forecast-fields.md](../spikes/uc4-xero-forecast-fields.md) | confirmed (docs) |
| S4 write-back of expected date | [uc4-xero-expected-date-writeback.md](../spikes/uc4-xero-expected-date-writeback.md) | partial; out of scope for this delivery |
| S5 Apache POI on the client's template | [uc4-poi-template.md](../spikes/uc4-poi-template.md) | confirmed (tested with POI 5.4.1, Java 25) |
| S6 Xero rate limits for the daily sync | [uc4-xero-rate-limits.md](../spikes/uc4-xero-rate-limits.md) | partial (docs; ≤ 218 calls per entity per run; pace under 60/min) |
| Xero OAuth, tenant header, invoice paging, `unitdp=4` | [uc5-xero.md](../spikes/uc5-xero.md) | partial (already relied on by UC5) |

## Test plan
Levels: U = unit, I = disposable PostgreSQL (Testcontainers), H = Spring MVC HTTP test with mocked services, V = Vitest, P = Playwright Chromium. Names follow the plan §11.

| Acceptance criterion | Named test | Level |
|---|---|---|
| AC-1 | `XeroForecastClientTest` (where/order/summaryOnly, ms-date mapping, pagination validation, duplicate IDs); `ForecastDocumentRepositoryTest.frUc04_01_upsertTwiceYieldsOneRow` | U, I |
| AC-2 | `ForecastDocumentRepositoryTest.frUc04_17_settledDocumentFlipsOutstanding`; `ForecastSyncServiceTest.frUc04_17_missingRowsAreReReadAnd404BecomesDeleted` | I, U |
| AC-3 | `ForecastDocumentRepositoryTest.frUc04_17_adjustmentsUntouchedBySync` | I |
| AC-4 | `ForecastSyncServiceTest.frUc04_17_pageFailureRollsBackType`; `ForecastEndpointTest.frUc04_06_viewShowsSyncInfoAndStaleAt36h` | U, H |
| AC-5 | `ForecastSyncServiceTest.frUc04_17_concurrentRunReturnsInProgress`, `..._rateLimitedHealthMakesNoProviderCall` | U |
| AC-6 | `ForecastSyncServiceTest.frUc04_16_dateChangeWithSplitsFlagsConflictAndKeepsLines`; `ForecastEndpointTest.frUc04_16_conflictResolution` | U, I, H |
| AC-7 | `XeroConnectionServiceTest.systemAccessHasNoControllerPath` (architecture test: no `web` class references `systemAccess`) | U |
| AC-8 | `ForecastPeriodServiceTest.frUc04_04_upsertOpeningBalanceWithAudit`, `..._rejectsNonFirstDayMonth` | U, I |
| AC-9 | `ForecastCalculatorTest.frUc04_05_missingOpeningBalanceYieldsNullNotZero`; `ForecastTable.test.tsx` "Not recorded" | U, V |
| AC-10 | `ManualItemServiceTest` validation matrix, soft delete, audit; `ForecastCalculatorTest.frUc04_03_monthlyItemLandsEveryMonthInclusive` | U, I |
| AC-11 | `ForecastEndpointTest.frUc04_03_staffingNeverInStandardUserOrCombinedDto` | H |
| AC-12 | `ForecastCalculatorTest.frUc04_05_closingEqualsOpeningPlusNet`, `..._twelveRowsAndBeyondWindow` | U |
| AC-13 | `ForecastCalculatorTest.frUc04_14_expectedDateBeatsDueDate`, `..._overrideBeatsXeroDate`, `..._plannedPaymentDateForBills` | U |
| AC-14 | `ForecastCalculatorTest.frUc04_15_partPaidUsesAmountDue`, `..._excludedContributesZero`, `..._splitLinesSumOnce`, `..._overdueGoesToCurrentMonth` | U |
| AC-15 | `ForecastEndpointTest.frUc04_15_splitSumMismatchIs422`; `SplitDialog.test.tsx` blocks save | H, V |
| AC-16 | `ForecastEndpointTest.frUc04_15_exclusionReasonRequired` | H |
| AC-17 | `ForecastCalculatorTest.frUc04_18_doesNotAddTaxOnTopOfInclusiveAmounts` | U |
| AC-18 | `ForecastCalculatorTest.frUc04_20_groupsByForecastDateNotIssueMonth`; `ForecastEndpointTest.frUc04_20_monthItems` | U, H |
| AC-19 | `ForecastServiceTest.frUc04_13_combinedSumsOnlyRequestedEntitiesAndNeverReturnsDocuments`; `ForecastEndpointTest.frUc04_13_archivedEntityFailsWholeRequest` | U, H |
| AC-20 | `ForecastEndpointTest.frUc04_07_roleMatrixOnEveryRoute` | H |
| AC-21 | `ForecastEndpointTest.sec_foreignEntityIdIs404` (documents, items, staffing, splits) | H, I |
| AC-22 | `ForecastEndpointTest.sec_adjustmentOnSettledDocumentIs409` | H |
| AC-23 | `Uc4SchemaTest` (RLS on, no browser privileges, grants, check constraints, FK restrict) | I |
| AC-24 | `WorkbookValidatorTest` (missing sheet, renamed header, altered `Forecast!B68`, `.xlsm`, VBA part, external link, oversized, zip-bomb ratio, unsupported function, fixture passes); `ForecastExportEndpointTest.frUc04_12_invalidUploadLeavesTablesUnchanged` | U, H, I |
| AC-25 | `WorkbookPopulatorTest` (formula diff outside write ranges empty; titles; delay cells; column F; `(k/n)` rows; exclusions absent; rename; staffing grid cleared); `WorkbookPopulatorTest.hasNoRepositoryDependency` | U |
| AC-26 | `WorkbookPopulatorTest.frUc04_11_201ContributionsFailWithCapacity`, `..._11ManualPaymentsAggregateWithWarning`, `..._7StaffingLinesAggregateWithWarning` | U |
| AC-27 | `ForecastReconciliationTest.frUc04_21_populatedFixtureMatchesCalculator`, `..._injectedMismatchSetsHeaderAndFlag` | U |
| AC-28 | `ForecastExportEndpointTest.frUc04_19_headersFilenameAndSingleAuditWrite`; `ExcelExportDialog.test.tsx` prompt only after success, never auto-downloads | H, I, V |
| AC-29 | `Forecast.test.tsx`, `ForecastTable.test.tsx`, `ForecastHeader.test.tsx` (entity switch aborts and clears); Playwright `uc4-forecast.spec.ts` journey at five widths; SU has no Forecast destination | V, P |

## Company group notes (human instruction, 27 November 2026)
- Each forecast company group has an internal note independent of its invoice and bill notes. The note is shared between Money In and Money Out for the same company and entity, and persists across forecast periods. Saving a blank note clears it. Notes are not sent to Xero.
- Company identity follows the exact company name used by the forecast grid. A changed name forms a different group until a stable contact identity is available. Forecast role and entity access checks apply.

## Forecast amount editing (human instruction, 27 November 2026)
- Keep the inline editor compact; support Enter to save and Escape to cancel, with accessible save, cancel and calculated-amount actions (human correction, 28 November 2026).
- Select the displayed value or blank dash to edit; do not show a separate Edit button beside each amount (human correction, 28 November 2026).
- Current and future opening balances and company, document and manual-line amounts can be edited, including blank cells. Past months are read-only and the server rejects historical writes.
- Monthly company amounts can be overridden independently of their individual invoice or bill rows. Where set, the company amount replaces the sum of those rows in Money In or Money Out; document edits do not change that company override. Totals and closing balances remain calculated.
- Changes are internal forecast overrides scoped to the entity and month. They do not change Xero amounts and remain in place through forecast refreshes. An ungenerated month can retain an edit but does not display a calculated monthly total until forecast data is generated.
- Users can restore a cell's calculated amount. When a future month is refreshed, a document override for an item that is no longer outstanding or is excluded is retired; current and past months remain preserved.
