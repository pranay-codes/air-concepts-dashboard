# Spike: UC4 Xero forecast fields, incremental sync and outstanding statuses (S1, S2, S3)

## Question
- S1: Does `GET /Invoices` return `ExpectedPaymentDate` (ACCREC) and `PlannedPaymentDate` (ACCPAY) in list responses, with and without `summaryOnly=true`?
- S2: Does `If-Modified-Since` on `GET /Invoices` return invoices whose `UpdatedDateUTC` changed because a payment or credit note was applied, or whose status moved to PAID or VOIDED?
- S3: Which statuses can carry `AmountDue > 0`? Is AUTHORISED the only open status for approved documents, and should SUBMITTED ever count as outstanding?

Source: `docs/plans/UC4.md` §4. These three questions share the same two documentation pages, so they are recorded in one note.

## Method
- Official documentation only, read 10 November 2026 in a browser: the Accounting API Invoices page, the HTTP Requests and Responses page, and the Xero Accounting OpenAPI description (`xero_accounting.yaml`, `info.version` 20.0.1) from Xero's public GitHub repository.
- No API call was made. No demo organisation, Test tenant or credential was used.
- Time box: 45 minutes, proposed by the agent because none was given.

## Findings

### S1 — expected-date fields in list responses
- Confirmed in docs: the Invoices page lists `ExpectedPaymentDate` ("Shown on sales invoices (Accounts Receivable) when this has been set") and `PlannedPaymentDate` ("Shown on bills (Accounts Payable) when this has been set") among "the following elements are returned in the Invoices response", i.e. the collection response, not only the single-invoice response.
- Confirmed in docs: `summaryOnly=true` excludes exactly four fields: `Payments`, `HasAttachments`, `LineItems`, `CISDeduction`. Neither expected-date field is excluded. When `summaryOnly` is used "pagination is enforced by default".
- Encoding: the OpenAPI description marks both fields `x-is-msdate: true`, so they arrive as `/Date(<milliseconds>+0000)/` in JSON. Unlike `Date` and `DueDate`, no `...String` companion is documented for them. The field is absent when unset.
- Not tested: no payload from a real tenant was inspected. Whether the fields appear on every page of a paged `summaryOnly` list has not been observed.

### S2 — `If-Modified-Since` after payments and status changes
- Confirmed in docs: the header is `If-Modified-Since`, a UTC timestamp `yyyy-mm-ddThh:mm:ss`, "accurate to the second"; "Only items created or updated since the specified timestamp will be returned".
- Confirmed in docs, and important: "Not all changes will trigger a change of the UpdatedDateUTC field. These include changes to partially paid transactions which don't generate a journal such as DueDate or SentToContact". So an edit of `DueDate` on a part-paid invoice can be invisible to an incremental query.
- Not confirmed: the documentation does not state explicitly that applying a payment or credit note, or the move to PAID or VOIDED, bumps the invoice's `UpdatedDateUTC`. Payments create journals, which the quoted sentence implies would qualify, but that is inference, not evidence.
- Consequence for the plan: the recently-changed fetch (§6.3 step 4) is an optimisation only. The settlement step that re-reads every stored outstanding row missing from the full-outstanding fetch (§6.3 step 6) is mandatory, not a fallback. The full-outstanding fetch (step 3) has no modified-date filter, so due-date edits on part-paid documents are still picked up there.

### S3 — statuses that can be outstanding
- Confirmed in docs: invoice statuses are `DRAFT`, `SUBMITTED`, `DELETED`, `AUTHORISED`, `PAID`, `VOIDED` (OpenAPI enum and the Invoice Status Codes table).
- Confirmed in docs: only AUTHORISED is the "approved" state where "Journals are created in Xero", "You can now apply payments to the invoice", and "Once an invoice is fully paid the status will change to PAID". DRAFT and SUBMITTED create no journals. Valid transitions out of AUTHORISED are only AUTHORISED (edit) and VOIDED; PAID is reached by payment, not by status update.
- Not documented: whether `AmountDue` is zero or equals `Total` on DRAFT and SUBMITTED documents. Therefore a filter on `AmountDue > 0` alone is unsafe; the `where` clause must also pin `Status=="AUTHORISED"`.
- Default for client question Q3 stands: outstanding = `Status == AUTHORISED and AmountDue > 0`. SUBMITTED (awaiting approval) is excluded unless the client asks otherwise.

### Query shape (applies to all three)
- Confirmed in docs: `Status`, `Type`, `AmountDue` (equals and range) are optimised `where` fields, so `Type=="ACCREC" AND Status=="AUTHORISED" AND AmountDue>0` uses only optimised filters. The example on the page is `?where=Type=="ACCPAY" AND Status=="AUTHORISED"`.
- Confirmed in docs: optimised ordering is `InvoiceId`, `UpdatedDateUTC`, `Date`; the default order is `UpdatedDateUTC ASC, InvoiceId ASC`, with `InvoiceId` as the secondary key "to ensure consistency across pages".
- Confirmed in docs: default `pageSize` is 100, maximum 1000, minimum 1; out-of-range values are clamped. A `pagination` object (`page`, `pageSize`, `pageCount`, `itemCount`) is returned, which the existing `XeroInvoiceClient.list` already validates. The plan's `pageSize=100` is a conservative choice; raising it is a single constant.
- Confirmed in docs: `GET /Invoices` requires `accounting.transactions.read` or the granular `accounting.invoices.read`, which the application already requests (`XeroSettings.SCOPES`).

## Decision
- S1: **partial**. Documentation confirms both fields are returned in list responses and are not removed by `summaryOnly`. The client must parse the ms-date encoding and tolerate absence. Add a Test-tenant check to the sync slice's acceptance: one ACCREC with an expected date and one ACCPAY with a planned date must appear in a paged `summaryOnly` list.
- S2: **partial / not confirmed for payments**. Build the incremental step as optional; never rely on it for settlement. The settlement re-read in §6.3 step 6 is required.
- S3: **confirmed by documentation** for the rule `AUTHORISED and AmountDue > 0`. Whether SUBMITTED should ever be included remains client question Q3; keep the `OutstandingRule` constant.

## Date
- 2026-11-10

## Evidence
- https://developer.xero.com/documentation/api/accounting/invoices — read 10 Nov 2026 (sections "GET Invoices", "Optional parameters for GET Invoices", "Optimised use of the where filter", "Optimised ordering", "Retrieving a smaller lightweight response using the summaryOnly parameter", "Invoice Status Codes", "Invoice status changes").
- https://developer.xero.com/documentation/api/accounting/requests-and-responses — read 10 Nov 2026 (sections "JSON responses and date formats", "Retrieving modified resources", "Retrieving paged resources").
- https://raw.githubusercontent.com/XeroAPI/Xero-OpenAPI/master/xero_accounting.yaml — downloaded 10 Nov 2026; `Invoice.ExpectedPaymentDate` and `Invoice.PlannedPaymentDate` carry `x-is-msdate: true`; `Invoice.Status` enum `DRAFT, SUBMITTED, DELETED, AUTHORISED, PAID, VOIDED`; parameter `ifModifiedSince` is header `If-Modified-Since`, `format: date-time`; `getInvoices` security `accounting.transactions` or `accounting.transactions.read`.
- Sample request shape the sync will use (unencoded for readability; no tenant or token shown):

```
GET /api.xro/2.0/Invoices?where=Type=="ACCREC" AND Status=="AUTHORISED" AND AmountDue>0
    &order=UpdatedDateUTC ASC,InvoiceID ASC&page=1&pageSize=100&summaryOnly=true
Accept: application/json
xero-tenant-id: <tenant>
```

- Documented response field encoding: `"ExpectedPaymentDate": "\/Date(1518685950940+0000)\/"` (milliseconds since the Unix epoch, UTC).
