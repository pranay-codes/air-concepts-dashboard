# Spike: quote and contact reads for the green line

## Question and method

GL-T02: what quote and contact read behaviour is available? Official Xero documentation was reviewed on 19 November 2026. No provider account was accessed, so all results are documentation-only and the overall status is **partial**.

## Findings

- **Partial — scopes:** Xero lists Quotes under `accounting.invoices.read` and Contacts under `accounting.contacts.read`. Actual app consent and access have not been tested. [Scopes](https://developer.xero.com/documentation/guides/oauth2/scopes/).
- **Unverified — grant evidence:** Xero describes a `scope` claim in its [access-token documentation](https://developer.xero.com/documentation/guides/oauth2/token-types/). The reviewed documentation does not establish that a separate `scope` field is returned in every token response. The implementation records that field when present and treats it as unknown when absent; it does not infer permission from a requested scope or an unverified token payload. Verify the actual response and a safe way to establish granted scopes before enabling scope-gated provider calls.
- **Partial — Quotes paging and filters:** Xero's official [Accounting OpenAPI description](https://github.com/XeroAPI/Xero-OpenAPI/blob/master/xero_accounting.yaml) documents `If-Modified-Since`, `DateFrom`, a singular `Status` filter, and `page` with up to 100 quotes and line items. Its Quotes response schema does not include invoice-style pagination metadata. The plan's `Statuses` parameter and assumptions about pagination metadata are not supported by this source. Ordering, end-of-results behaviour, incremental completeness and actual account behaviour remain untested; do not infer completion solely from a short page.
- **Backfill caution:** `DateFrom` filters the quote's date, not its last-modified time. Using it alone for the plan's “quote date OR UpdatedDateUTC within the window” rule can exclude an older quote recently accepted. Do not make that optimisation unless another verified query covers the missing records.
- **Partial — quote fields:** the official description documents `QuoteID`, `QuoteNumber`, `Reference`, `Status`, `UpdatedDateUTC`, `Contact`, `LineItems`, `Total`, `CurrencyCode` and `LineAmountTypes`. It does not establish an acceptance timestamp or validate using `UpdatedDateUTC` as its proxy. Field completeness and semantics remain untested in a demo account.
- **Partial — contact addresses, phone numbers and contact persons:** Xero's official OpenAPI description lists contact detail, address, phone and person fields, with examples for STREET and POBOX. Their use in this account and which address represents a work site are not verified; preserve the typed addresses and do not infer that either is the work site.
- **Not confirmed — quote status after copying to invoice, and Xero application deep links:** verify in a demo organisation; omit unverified deep links.

## Decision and verification required

Documentation can inform a read adapter but does not prove account-specific behaviour. Before enabling the affected sync, verify filters, completeness and boundaries using a Xero demo organisation and sanitised examples; record actual observations here. No request or response samples exist for this investigation.

## Documentation update, 23 November 2026

Xero's [API efficiency guidance](https://developer.xero.com/documentation/best-practices/api-call-efficiencies/rate-limits/) lists Quotes among endpoints supporting paging. Its [general HTTP guidance](https://developer.xero.com/documentation/api/accounting/requests-and-responses/) describes an older practice of requesting subsequent pages until none are returned, but its current list of endpoints covered by pagination metadata omits Quotes. These sources support using the `page` parameter, but do not explicitly establish a Quotes-specific empty-page termination contract or stable ordering during concurrent changes. Do not treat a short page alone as proof of completeness.
