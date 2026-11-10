# Spike: UC4 Xero rate limits for a daily full-outstanding sync (S6)

## Question
- What are Xero's rate limits for a daily full-outstanding read across N organisations, and what is the worst-case call count for 1,000 outstanding documents? (`docs/plans/UC4.md` §4 S6, §6.3 steps 3–6.)

## Method
- Official documentation only, read 10 November 2026: "OAuth 2.0 API limits" page. No calls were made.
- Time box: 20 minutes, proposed by the agent.

## Findings
Limits, quoted from the page:

| Limit | Value |
| --- | --- |
| Concurrent | "5 calls in progress at one time" per tenant |
| Minute | "60 calls per minute" per tenant |
| Daily | "1,000 calls per day for starter, 5,000 calls per day for higher tiers" per tenant |
| App minute | "10,000 calls per minute" across all tenants |
| Headers on every response | `X-DayLimit-Remaining`, `X-MinLimit-Remaining`, `X-AppMinLimit-Remaining` |
| On breach | HTTP 429 with `X-Rate-Limit-Problem` naming the limit; minute and daily breaches also carry `Retry-After` in seconds; windows are fixed and "reset at different times for each tenant" |
| Uncertified app connections | "New apps default to the starter tier with 5 connections. Moving up to Core will give you up to 50 connections." Each organisation may connect at most five uncertified apps. |
| Limits are per tenant | "if two separate Xero organisations are connected to your application, each connection would have 5000 API calls available in a given 24 hour period for apps in the Core tier and above and 1000 API calls available for apps in the Starter tier" |
| Paging | Invoices support pagination; default 100 per page, maximum 1,000 (Requests and Responses page) |

Worst-case call count per entity per daily run, with the plan's `pageSize=100` and 1,000 outstanding documents split in any way between ACCREC and ACCPAY:

| Step (plan §6.3) | Calls |
| --- | --- |
| 2 health check (local, no provider call) | 0 |
| 3 full-outstanding list, two types | ⌈1,000 / 100⌉ = 10 pages, plus up to 1 extra page per type to see the final short page = at most 12 |
| 4 recently changed list, two types, 24 h window | typically 2 pages; assume ≤ 6 |
| 6 settlement re-reads of stored outstanding rows missing from step 3 | bounded at 200 (`FORECAST_SYNC_TOO_MANY_CHANGES` above that) |
| **Total** | **≤ 218 per entity per run** |

- Daily budget: 218 is 22 % of the starter tier's 1,000 and 4 % of 5,000. UC5's interactive reads share the same per-tenant budget, so the sync should log the three `X-*Limit-Remaining` headers of its last response in `forecast_sync_runs` diagnostics (values only, never headers containing tokens) to make budget pressure visible.
- Minute budget: 218 sequential calls exceed 60 per minute if sent back-to-back. The sync must pace itself: run entities sequentially, one request in flight, and either sleep so that no more than ~50 calls land in any rolling minute, or honour `Retry-After` on a 429 and resume. A 218-call run paced at 50 per minute takes about 4.5 minutes per entity; three entities finish well inside the 04:30 NZ window.
- Raising `pageSize` to 1,000 would cut step 3 to 2–4 calls; the plan keeps 100 to reuse the existing pagination validation, and this is a single constant.
- Tier: with three entities the application is within the starter tier's five connections. A sixth entity, or a second environment (Test and Live each hold their own grant) that pushes total connections past five, needs the Core tier. This is an operational prerequisite to record in `docs/uc4-forecast-operations.md`, not application logic.
- Not tested: actual `Retry-After` values and the exact fixed-window reset were not observed. `XeroReadHealth` already records `Retry-After`, so Test will show real values once the sync runs.

## Decision
- **partial** (documentation verified; no live observation). The daily full-outstanding design fits the documented limits with the 200-re-read bound in place. The sync must: pace to under 60 calls per minute per tenant, keep one request in flight, honour `Retry-After` through `XeroReadHealth`, and surface remaining-limit headers in run diagnostics. The connection-tier limit (five uncertified connections) must be checked by the human when a fourth entity or a second environment connects.

## Date
- 2026-11-10

## Evidence
- https://developer.xero.com/documentation/guides/oauth2/limits/ — read 10 Nov 2026 (sections "Uncertified app limits", "API rate limits", "Exceeding a rate limit", "Rate limit FAQ").
- https://developer.xero.com/documentation/api/accounting/requests-and-responses — read 10 Nov 2026 (section "Retrieving paged resources": default 100, maximum 1000).
