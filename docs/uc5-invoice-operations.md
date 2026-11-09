# UC5 invoice backend operations and proof

## Request and permission contract

All routes require a verified Supabase JWT and an active database staff role. Entity IDs are dashboard UUIDs. The browser cannot select a Xero tenant or supply a provider URL. Every active role can read ACCREC sales invoices; only Director and Financial Admin can read ACCPAY supplier bills. Both privileged roles can assign sales invoices. Bills have no ownership lookup or supported assignment operation.

`GET /api/v1/entities/{entityId}/invoices` accepts type, search, status, overdue, dateField, from, to, sort, direction, page and pageSize. Defaults are ACCREC, every date/status, date descending, page 1 and 25 items. The limit is 100. The provider receives every filter and stable invoice-ID tie-breaker before pagination. Responses include the provider's full matching count and page count. Inconsistent or absent pagination, duplicate IDs and wrong document types fail; they never become successful empty data. No invoice ledger cache exists.

`GET .../invoices/{invoiceId}?type=...` retrieves and validates the immutable document ID/type, including line items and payments. Dates remain date-only. Overdue is derived using Pacific/Auckland business date, AUTHORISED status, positive balance and a strictly earlier due date. The original status is retained. Java uses BigDecimal. JSON amounts and quantities use decimal strings to preserve precision in browser clients; missing optional values are omitted and must render as Not recorded, never zero. Currency comes from each document.

`PUT .../invoices/{invoiceId}/assignment?type=ACCREC` takes an active `assigneeId`; null or an omitted property unassigns. Verify the sales document through Xero before writing. The transaction shares the staff-administration lock, rechecks the actor/active target and locks the unchanged entity/grant/tenant mapping. Ownership is keyed by entity plus invoice ID, with actor/time retained. Deactivated owners remain readable and explicitly inactive. Assignment writes are idempotent state replacement and never edit Xero.

## Attachments

`GET .../invoices/{invoiceId}/attachments?type=...` first verifies the invoice. The file route is `GET .../attachments/{attachmentId}/file?type=...&preview=false`. It repeats document verification and resolves membership using fresh provider metadata. It ignores the provider URL, rejects unsafe filename/path characters and constructs a fixed-origin encoded filename request using server credentials.

The backend buffers a maximum 25 MiB before sending success headers, checks known and actual lengths, bounds read/connect timeouts to 20/5 seconds, and closes the provider stream on all paths. Oversized files return 413; missing membership returns 404; truncated or failed provider reads return an explicit error. This avoids partial successful previews. No upload, delete or accounting-edit routes exist.

Downloads default to attachment disposition. Explicit preview is inline only for signature-checked PDF, PNG, JPEG, GIF and WebP. Unsupported, HTML, SVG or mislabelled content uses an octet-stream download. Headers include no-store, nosniff, no-referrer, UTF-8-safe Content-Disposition and sandbox CSP. File clients must inspect HTTP status and problem JSON before creating a browser Blob. Revoke Blob URLs on replacement, context change and unmount.

## Failure and retry

Provider errors retain only a safe code, upstream status and optional Retry-After, never raw bodies, tokens or causes. 401/403 accounting failures mark the affected entity connection Needs reconnection without revoking other tenants sharing its grant. 429 persists a per-connection retry deadline (60 seconds if absent). Requests before it expires stop locally. Reconnection resets tenant read-health fields. Conditional updates reject stale successful results and stop old failures overwriting replacement mappings or newer state. Concurrent healthy reads remain independent.

There is no automatic retry loop. Users explicitly retry a safe read after the indicated delay; no cached page is presented as a fresh successful response. If recording a provider failure also fails, return the original provider failure and log a fixed diagnostic with its safe code/status; never report success or log raw database errors. This implements the read-only capability with an attempt limit of one per user request. Token rotation has its separate persisted recovery policy.

## Deployment and evidence boundary

Forward-only migrations `20261107010000_uc5_accounting_read_health.sql` and `20261107020000_uc5_invoice_assignments.sql` add nullable health columns and a backend-only RLS assignment table. No destructive statements. Existing rows/grants remain. Rollback to the prior immutable application artefact leaves these additive schema objects in place. Hosted migrations use CI only; none were applied by this session.

Unit/provider/HTTP tests exercise permissions, server tenant headers, combined queries, second-page full counts, malformed/incomplete data, wrong types/IDs, numeric precision, safe failures, file membership, fixed origins, supported/unsafe previews, bounded/truncated files and stream closure. Disposable PostgreSQL tests cover composite ownership, inactive history, transaction guards, tenant-scoped health, stale responses and RLS. Docker is unavailable locally, so those database tests have not executed.

Mock HTTP proves adapter construction and failure policy. Actual Xero demo search/case/unicode/compound-sort semantics and pagination, actual token eligibility/scopes, hosted migrations, server identity grants, browser UI, independent review, CI and Test acceptance remain separate open checks. See the requirement ledger and spike; do not infer hosted proof from local tests.
