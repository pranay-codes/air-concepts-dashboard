# Spike: UC4 writing ExpectedPaymentDate back to Xero (S4)

## Question
- Writing `ExpectedPaymentDate` (ACCREC) or `PlannedPaymentDate` (ACCPAY) back to Xero: which scope, which endpoint, and is it idempotent? (`docs/plans/UC4.md` §4 S4; client question Q6.)

This would be the first accounting **write** in the application. The plan defers implementation; this note records what the write would require so the follow-up task can be sized.

## Method
- Official documentation only, read 10 November 2026: Invoices page, Scopes page, Xero Accounting OpenAPI description (version 20.0.1).
- No request was sent. No tenant, demo organisation or credential was used.
- Time box: 20 minutes, proposed by the agent.

## Findings
- Endpoint, confirmed in docs: `POST /api.xro/2.0/Invoices/{InvoiceID}` (OpenAPI `operationId: updateInvoice`, "Updates a specific sales invoices or purchase bills"). Both `ExpectedPaymentDate` and `PlannedPaymentDate` are listed under "The following are optional when creating or updating invoices". Only `InvoiceID` plus the changed field needs to be sent; the page's own "mark as sent" example sends just `InvoiceID` and one field.
- Scope, confirmed in docs: write requires `accounting.transactions` (deprecated broad scope) or the granular `accounting.invoices` ("View and manage invoices"). The application currently requests `offline_access accounting.invoices.read accounting.attachments.read` (`XeroSettings.SCOPES`), so a write needs a **new scope and re-consent** by the Director for every connected organisation. Scopes are additive and cannot be removed from an existing token without revoking it.
- Idempotency, confirmed in docs: the `updateInvoice` operation accepts an `Idempotency-Key` header ("This allows you to safely retry requests without the risk of duplicate processing. 128 character max."). A date update is also naturally idempotent because the same value can be re-sent. The retry design must still handle a 429 with `Retry-After` and a locked period.
- Restrictions, confirmed in docs:
  - "Updating Invoices": documents "In a locked period" cannot be updated at all.
  - For an ACCREC invoice that is **paid in part or in full**, the updatable field list is `Reference, DueDate, InvoiceNumber, BrandingThemeID, Contact, Url, LineItems (Description, AccountCode, Tracking)`. `ExpectedPaymentDate` is **not** on that list, so write-back of an expected date on a part-paid sales invoice is not documented as supported.
  - For an ACCPAY bill paid in part or in full, the list **does** include `PlannedPaymentDate`.
  - Updating an AUTHORISED invoice keeps it AUTHORISED (the only other valid transition is VOIDED).
- Not confirmed: actual behaviour of a part-paid ACCREC update (validation error vs. silent ignore), and whether an Idempotency-Key replay returns the original response. Both need a demo-organisation test before any build.

## Decision
- **partial** (documentation only). Write-back is **not** part of the UC4 first delivery, as the plan already says (§13 Q6). Overrides are stored and audited locally.
- Follow-up task prerequisites: (1) human approval of a write scope `accounting.invoices` and Director re-consent per organisation; (2) idempotent retry design using `Idempotency-Key`; (3) handling for part-paid ACCREC where the field is not updatable and for locked periods; (4) demo-tenant tests of both; (5) a separate spec pack slice. The follow-up must never write one entity's date to another entity's organisation (AGENTS.md §7).

## Date
- 2026-11-10

## Evidence
- https://developer.xero.com/documentation/api/accounting/invoices — read 10 Nov 2026 (sections "POST Invoices", "The following are optional when creating or updating invoices", "Invoice status changes", "Updating Invoices").
- https://developer.xero.com/documentation/guides/oauth2/scopes — read 10 Nov 2026 (table "Accounting API": `accounting.transactions` Deprecated, `accounting.invoices` New "View and manage invoices", `accounting.invoices.read` New "View invoices … GET only"; "Broad scopes will remain available until September 2027").
- https://raw.githubusercontent.com/XeroAPI/Xero-OpenAPI/master/xero_accounting.yaml — downloaded 10 Nov 2026; `updateInvoice` security `accounting.transactions`, parameters include `unitdp` and `idempotencyKey` (header `Idempotency-Key`).
- Shape of the write the follow-up would send (not implemented, no real IDs):

```
POST /api.xro/2.0/Invoices/{InvoiceID}
Idempotency-Key: <stable key per adjustment version>
xero-tenant-id: <tenant>
{ "InvoiceID": "<id>", "ExpectedPaymentDate": "2026-12-15T00:00:00" }
```
