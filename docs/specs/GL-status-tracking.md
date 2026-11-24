# Green Line: tracking out-of-order events

Status: human-approved 23 November 2026; provider behaviour and detailed implementation require verification.

## Goal and acceptance

- GL-STATUS-01: Once the dashboard has observed a Xero quote, continue tracking the same work item as related quote, job, invoice and payment states change, including when events arrive outside the usual order.
- GL-STATUS-02: If a tracked quote is at To schedule and its matched Xero invoice is confirmed sent, display Invoiced; do not require a completed vWork job first.
- GL-STATUS-03: If a verified matching vWork job is already scheduled, the dashboard must be able to reflect that state even when earlier expected events were not observed.
- GL-STATUS-04: Continue tracking later changes to related records. Do not silently associate an ambiguous or cross-entity job or invoice; surface it for review.
- GL-STATUS-05: Display sync failures honestly; do not report complete provider results without evidence.

## Boundaries and open provider evidence

Keep provider access server-side, resolve the authorised entity and tenant on every read, and retain audit and transaction safeguards. Xero invoice writes are outside this change. vWork job creation is in scope as set out in the amendment below. Do not connect to Live, or set up local PostgreSQL; database verification runs in CI.

## Amendment, 24 November 2026

Approved by the human in conversation on 24 November 2026. Where this amendment and the text above differ, this amendment applies.

- **Stage set (GL-STATUS-07):** Quoted, To schedule, Job scheduled, Work in progress, Job completed, Invoiced, and Closed. TO_INVOICE and INVOICE_DRAFTED are retired. An item awaiting an invoice is Job completed, and a linked invoice is recorded by its Xero invoice ID. Migration `20261124010000_gl_vwork_stage_set.sql` changes the stage constraint and remaps existing rows and their history.
- **vWork statuses:** assigned is Job scheduled; started and paused are Work in progress; completed is Job completed; canceled raises an attention flag.
- **Job creation (GL-STATUS-06):** an accepted quote with a unique Job ID can have its vWork job created from the entity's price-free template, using the Templated Jobs clone endpoint. The dashboard stores the returned vWork job ID, so no manual link is needed for dashboard-created jobs. The customer is linked by the Xero contact ID, read from the stored work item and never from the browser.
- **Prices (GL-STATUS-06):** no prices are sent to vWork. The payload carries no invoice, pricebook, worker, planned start or currency values. A created job that comes back with pricing is held for review. See `docs/spikes/gl-vwork-job-create.md`.
- **Enablement:** live job creation is off by default (`dashboard.vwork.job-creation-enabled=false`). It stays off until the verification items in the spike are complete on the Test environment.
- **Not built:** the manual vWork link action proposed earlier is not part of this change.
- **Invoice matching (GL-STATUS-02):** matching of Xero invoices to open work items runs at all times, including before the job is complete. There is no configuration switch. A draft invoice links without changing the stage. A sent, authorised invoice moves the item to Invoiced.

The existing provider spikes are partial: `docs/spikes/gl-xero-quotes-contacts.md` does not confirm quote pagination completeness; `docs/spikes/gl-xero-invoice-write.md` does not confirm account-specific invoice matching or sent semantics; `docs/spikes/gl-vwork-api.md` does not establish a trustworthy vWork job-to-entity identity. These must not be treated as verified by synthetic tests. The exact stage precedence for other simultaneously conflicting events remains to be defined from observable provider states and the user's decisions.
