# Spike: create vWork jobs from accepted quotes

## Question and method

Can the dashboard create a vWork job when a Xero quote is accepted, store the vWork job ID at creation, and keep prices out of vWork?

Method: documentation only. No vWork account was used and no requests were sent. Overall status is **partial**.

Sources (all read 24 November 2026):
- v5 Templated Jobs OpenAPI document, supplied by the human, read in full. It is the source for `POST /v5/jobs/clone.xml`.
- v5 Jobs documentation (job basics, search, update, third-party IDs, status, workers, invoices, pricebooks), read via documentation fetch.
- v5 templates and pricebooks documentation, read via documentation fetch.

Where the sources disagree, the Templated Jobs YAML is preferred for creation, and the discrepancy is recorded below.

## Findings

| Capability | Status | Evidence and decision |
|---|---|---|
| Create a job from a template | partial | `POST /v5/jobs/clone.xml`. One of `template_name`, `template_id` or `template_third_party_id` is required. The response includes the new job `<id>`. Recommended by vWork for new jobs. |
| Third-party ID on create | partial | Settable. Must be unique. Required if the template has its own third-party ID. Echoed in responses and sent as `resource_third_party_id` on webhooks. Behaviour on a duplicate is **not documented**. |
| Job created unassigned | documented | No worker and no planned start leaves the job unassigned. Do not send a worker. |
| Planned duration | documented | Settable on create; otherwise inherited from the template. |
| Status values and restrictions | documented | `assigned` means accepted (if accept/decline is enabled), `started` means at least one step is complete, `completed` means all steps are complete. `paused` means started but not finished. `postponed` and `declined` need a dispatcher. `canceled` is ignored on create. Whether accept/decline is enabled for this account is **unknown**. |
| Template identity on job reads | not confirmed | The create response example shows `template_name` but no `template_id`. The standard search documentation lists a `template_id` filter, but its response shape is not confirmed. Do not use template identity to decide entity ownership of jobs the dashboard did not create. |
| Customer link | partial | `customer_id`, `customer_third_party_id` or `customer_name` on create. The docs recommend an ID because names can repeat. Whether Xero contact IDs can be used as `customer_third_party_id` is **unverified**. |
| Webhooks | documented | HMAC-SHA-256 signature keyed with the API key. A non-200 response is discarded with no retry. vWork recommends hourly or daily polling to fill gaps. Polling is therefore required. |
| Rate limits | not confirmed | HTTP 429 returns "try again in a minute or two". No numeric limit is documented. |
| Authentication and errors | documented | `api_key` query parameter. `Accept-Encoding: gzip` is required, otherwise 406. Errors are XML. 422 bodies explain the failure. |

## Discrepancies

- The standard job documentation was summarised as saying a worker with no planned start may conflict. The Templated Jobs YAML is consistent: a worker with no planned start is assigned straight away. This spike sends no worker, so the conflict does not arise.
- One summary reported no template-listing endpoint. Another reported `GET /v5/templates.xml`. Neither is in the Templated Jobs YAML. Unverified.

## Price controls

Requirement: no prices enter vWork. Documented paths by which prices could enter a vWork job:

1. Template invoice information is inherited by jobs created from the template.
2. A template pricebook is inherited. Pricebook items carry unit costs.
3. A customer-specific pricebook may be inherited from the customer record. Whether this happens automatically is unverified.
4. Currency custom fields can hold numeric values and are inherited from the template.
5. The request can carry an `<invoice>` block, including `pricebook_id` and `unit_cost`.
6. Field workers may add or edit invoice information, depending on their permissions.

Controls:
- Templates used by the dashboard have no invoice information, no pricebook, and no currency fields with values.
- Customer records used by the dashboard have no customer-specific pricebook.
- The dashboard never sends an `<invoice>` block, `pricebook_id`, or currency values.
- Worker invoice permissions are switched off for dashboard-created jobs, if the account allows this.
- After creation, the dashboard reads the job back and raises an attention flag if the response contains an invoice block or a pricebook.

Controls 1 to 4 and 6 depend on account settings and on templates that must be checked in the vWork interface. They are not confirmed.

## Decision

Build job creation through `POST /v5/jobs/clone.xml`, using the price controls above and storing the returned job ID at creation. No manual linking is needed for jobs the dashboard creates.

Live creation stays disabled until the verification below is complete.

## Verification required before enabling

No test account is available. Verification requires either a Test account or a human read-only check in the live vWork interface. Nothing here may be tested against Live by the dashboard.

- Duplicate third-party ID response, which determines retry handling.
- Whether job reads include `template_id`.
- Whether `customer_third_party_id` can be set to Xero contact IDs.
- Whether accept/decline is enabled.
- Whether invoicing is enabled, and whether workers can edit invoices.
- Human check in the vWork interface: templates have no invoice information, no pricebook and no currency values with data; customer records have no customer-specific pricebook.
- Rate limit behaviour under the expected volume.
- Reconciliation after a timeout: read the job back by third-party ID. The documented route is the standard job read with `use_third_party_id=true`, which is on a page not covered by the Templated Jobs YAML and must be read before use.

## Implementation status, 24 November 2026

- Job creation is implemented in `VworkLiveClient.create` as a POST to `/api/v5/jobs/clone.xml`. The payload is built by `clonePayload` and carries only the template reference, the third-party ID (Job ID), the Xero contact ID as `customer_third_party_id`, and descriptive notes. Notes are XML-escaped.
- The template reference is sent as `template_id` when numeric, otherwise as `template_name`.
- `CreateJobService` derives the Xero contact ID from the stored work item. A missing contact refuses the request (`VWORK_CUSTOMER_UNLINKED`) before any reservation.
- A response that contains pricing elements with content raises `VWORK_PRICE_RETURNED`. The outcome is then recorded as unknown for review, never as linked.
- The live path only runs when `dashboard.vwork.job-creation-enabled` is true. It defaults to false, so nothing is created against a live account until the verification items above are complete on the Test environment.
- Host corrected to `go.vworkapp.com`, the host both vWork documents give. The earlier value, `api.vworkapp.com`, was not in either document.
- Status remains **partial**. Nothing in this note has been observed against a vWork account.
