# Spike: invoice writes and attachments

## Question and method

GL-T03: which draft invoice, lookup and attachment behaviours are supported? Official Xero documentation was reviewed on 19 November 2026. No demo or live account was accessed. Overall status: **partial**.

## Findings

- **Partial — invoice write:** [Invoices](https://developer.xero.com/documentation/api/accounting/invoices) documents invoice creation and the `DRAFT` status; [scopes](https://developer.xero.com/documentation/guides/oauth2/scopes/) lists `accounting.invoices` for writes. Creation with the planned copied quote fields remains untested.
- **Documented scope transition:** the [official Accounting OpenAPI file](https://github.com/XeroAPI/Xero-OpenAPI/blob/master/xero_accounting.yaml) lists `accounting.transactions` for invoice creation, while Xero's current [scope migration guidance](https://developer.xero.com/documentation/guides/oauth2/scopes/) marks that broad scope deprecated and identifies `accounting.invoices` as its granular replacement. This is documentation evidence, not proof of this application's actual grant or account access.
- **Partial — idempotency:** [idempotent requests](https://developer.xero.com/documentation/guides/idempotent-requests/idempotency/) documents `Idempotency-Key` for writes and expiry constraints. Endpoint-specific replay, validation errors and recovery still require a demo test. Persist the operation and key before any write; reconcile uncertain outcomes before retrying.
- **Partial — invoice lookup and sent signal:** the [invoice documentation](https://developer.xero.com/documentation/api/accounting/invoices) describes `Reference`, `SentToContact`, status, and `AmountDue`. It documents `SearchTerm` as a case-insensitive search across invoice number and reference, as well as invoice pagination. Search results must be filtered to the relevant sales invoice and its reference locally; account-specific matching, pagination, and whether each Xero UI send path changes the flag remain unverified. A search is not evidence that a prior write did not happen unless completeness is established.
- **Partial — attachments:** Xero documents up to 10 attachments per invoice, each up to 25 MB, and `IncludeOnline` for eligible sales invoices. [Attachments](https://developer.xero.com/documentation/api/accounting/attachments) says uploading a file with an existing name overwrites it. The scope is `accounting.attachments`; confirm exact binary handling and reconciliation in a demo account before enabling uploads.
- **Not confirmed — line-field fidelity and account-specific rate limits:** test quote-to-invoice tax, tracking and discounts, plus 429 handling, in a demo organisation. Do not assume copying preserves totals.

## Decision and verification required

No live-write compatibility or duplicate prevention has been demonstrated. Require sanitised demo evidence for each write and uncertain-outcome recovery before enabling it. No provider request or response samples exist for this investigation.
