# Spec pack: GL-24 Director overview (extended)

- Status: approved
- Approved by / date: the human, in conversation (plan approval), 25 November 2026
- Notion page URL: none; the requirements come from the 25 November 2026 conversation and GL-24 in `docs/plans/quote-to-invoice-implementation-plan.md`
- Notion last-edited date: not applicable

## Goal
The Director can see who holds which task, how long each step from quote to invoice takes, how many jobs were completed and how many invoices were sent.

## Requirement IDs
| ID | Report | Definition |
|---|---|---|
| GL-24 | Pipeline | Open count and value per stage; closed count per reason in the period |
| GL-DIR-01 | Who has what | Open items grouped by owner, including "Unassigned": job, customer, organisation, stage, days in stage, late, attention, technician |
| GL-DIR-02 | Stuck money | Job completed with no invoice, and Invoice created but not sent; value and days waiting, oldest first |
| GL-DIR-03 | Cycle times | Median, average, sample size and excluded counts for: quote date to accepted; accepted to job scheduled; quote date to job scheduled; scheduled to completed; completed to invoice sent. Whole Auckland calendar days; counted in the period when the step ends |
| GL-DIR-04 | Jobs completed | Per office owner and per technician, for completions in the period |
| GL-DIR-05 | Invoices sent | Count, value by currency and list of items first seen as Invoiced in the period, with the invoice still linked |
| GL-DIR-06 | Access and honesty | Director only; enabled, non-archived organisations with a matching Xero tenant; the caveats are shown on screen |

## Counting rules
- A time is `work_item_events.at`: when the dashboard saw the change. A stored vWork `completed_at` is preferred when present. The live vWork client does not currently set it.
- Completion is the first vWork event showing the job completed.
- Office credit goes to the owner just before the item first left To schedule, Job scheduled or Work in progress for a later stage; otherwise "Unattributed".
- Technician is the currently stored vWork worker name; otherwise "Not recorded".
- Durations exclude, and count separately: backfilled items, a missing start, a job already complete when first seen (without a provider completion time) and a step that ended before it started.
- Jobs already complete when first seen are included in job counts, and their number is shown.
- Items closed as paid without being seen as sent are shown as a separate count.
- Values are never added across currencies. The currency is the quote currency.
- Only invoices linked to a tracked quote are counted. "Sent" is Xero's sent marker or a manual "mark sent"; it does not prove delivery.

## Acceptance criteria
- AC-1 (GL-24): Given items in several stages, the open counts per stage equal the stored item counts.
- AC-2 (GL-DIR-01): Open items appear under their owner; unowned items appear under "Unassigned".
- AC-3 (GL-DIR-02): Completed uninvoiced and unsent invoice items appear oldest first with value and days waiting.
- AC-4 (GL-DIR-03): Each excluded case is left out of the median and counted under its reason.
- AC-5 (GL-DIR-04): A job completed after the hand-off to finance is credited to the owner before the hand-off.
- AC-6 (GL-DIR-05): An invoice first seen as sent in the period is counted once.
- AC-7 (GL-DIR-06): Unauthenticated requests get 401. Financial Admin, Standard user and inactive users get 403 and see no Overview navigation.
- AC-8 (GL-DIR-06): Disabled, archived or differently mapped organisations contribute nothing or fail closed; an organisation filter returns only that organisation.

## Confirmed decisions
- Core four reports; invoices counted only when linked to a tracked quote (human, 25 November 2026).
- "Person" means office staff, plus a jobs-completed count by vWork technician name (human, 25 November 2026).

## API contract
`GET /api/v1/work-items/overview?entityId=&from=&to=`, Director only, `Cache-Control: no-store`. Dates are inclusive Auckland dates; the default is the last 30 days; `from > to` or more than 366 days returns 400 `INVALID_PERIOD`; a tenant mismatch returns 409 `XERO_TENANT_CHANGED`. Item lists are capped at 500 with a truncation flag; counts and metrics cover all rows. The schema is in the generated OpenAPI snapshot.

## Out of scope
No migration, no Xero or vWork calls, no writes.

## Report backlog (not in this slice)
| Report | Data needed |
|---|---|
| Hand-off history; invoices by assigned salesperson | Stored |
| Time in each stage over a period; reschedule count; planned date against completion | Stored events |
| Quoted against invoiced amount; "no invoice needed" reasons | Stored |
| Outstanding and overdue amounts by customer | Stored for linked invoices; paid tracking is limited |
| Win rate by customer and quote size; lost reasons; quotes expiring or due for follow-up | Stored; lost reasons are free text |
| Sync freshness, unmatched or cancelled jobs, voided invoices | Stored |
| Job profit, labour against quote, utilisation, travel time, callbacks, first-time fix | New data capture |
| Enquiry to quote time, days to payment, contract renewals | New data capture |

## Open questions
- None blocking.

## External dependencies needing a spike
| Capability | Spike note (docs/spikes/) | Status |
|---|---|---|
| vWork job completion time | none yet | Not used; observed time is shown instead |

## Test plan
| Acceptance criterion | Named test | Level (unit, integration, e2e) |
|---|---|---|
| AC-4, AC-5 | `OverviewMetricsTest` | unit |
| AC-7 | `OverviewEndpointTest`, `DirectorOverview.test.tsx` | unit and HTTP |
| AC-1 to AC-3, AC-6, AC-8 | `OverviewServicePostgresTest` | integration (CI) |
| AC-2, AC-3, AC-6 on screen | `DirectorOverview.test.tsx`, `director-overview.spec.ts` | unit, e2e |
