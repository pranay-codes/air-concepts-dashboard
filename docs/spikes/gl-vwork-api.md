# Spike: vWork API for the green line

## Question and method

GL-T04: which vWork capabilities support job linking, creation and completion? Official vWork documentation was reviewed on 19 November 2026. No vWork account was contacted. All findings are documentation-only; overall status is **partial**.

## Findings and safe fallbacks

| Capability | Status and decision |
|---|---|
| V1 authentication and cheap verification | Partial: the [official Jobs OpenAPI](https://apidocs.vworkapp.com/_bundle/v5-jobs.json?download=) documents `api_key` in the query and gzip for Jobs reads. A minimal paged read can test HTTP access, but account-specific permissions are untested. Never log the URL, key or provider error payload. |
| V2 search by job reference | Partial: the documented job-detail endpoint supports `use_third_party_id=true` and the third-party ID is user-defined and unique. Whether this account populates it with the dashboard Job ID remains unverified. Do not automatically link on that assumption; manual linking still requires independently verified entity ownership. |
| V3 job state, worker, start, completion and notes | Partial: job statuses and examples exist, but completeness and field semantics are untested. Use audited manual scheduling/completion when reads are unavailable. |
| V4 creation from template | Partial: the [Templated Jobs API](https://apidocs.vworkapp.com/v5-templated-jobs/templated-job-creation) documents a clone endpoint. It does not establish safe account-specific templates or price-free output. Disable live creation until those conditions are proven. |
| V5 template listing | Not confirmed: do not assume a listing endpoint. |
| V6 report PDF | Not confirmed: show report not attached and allow the documented manual confirmation. |
| V7 signed webhook | Partial: the [Jobs documentation](https://apidocs.vworkapp.com/v5-jobs) describes HMAC-SHA-256 signatures but also whitespace/ASCII handling that needs verification against real test deliveries. No public webhook route; use polling where reliable reads exist. |
| V8 rate limits | Not confirmed: do not claim a verified provider limit. |
| D5 template identity on reads and searches | Not confirmed: the [standard Jobs documentation](https://apidocs.vworkapp.com/v5-jobs) describes `template_name` as free text. It is not evidence of a stable, trustworthy template ID. Never automatically associate a shared-account job with an entity using that field. |

## Decision and verification required

Before live linking or creation, obtain sanitised Test-account evidence that job read and search return a stable template identity, that its entity mapping is trustworthy, and that the selected template cannot include prices. Also verify each enabled endpoint, errors, limits and fields. If proof is unavailable, retain the manual workflow without unsafe automatic association. No requests or response samples were collected.

## Documentation update, 23 November 2026

The human instructed implementation from documentation without a vWork Test account. The [v5 Jobs reference](https://apidocs.vworkapp.com/v5-jobs/jobs-and-3rd-party-ids) says a supplied third-party ID must be unique; it does not establish that this account populates it with a dashboard Job ID or that it identifies an entity. The [job status reference](https://apidocs.vworkapp.com/v5-jobs/jobs-and-status) documents `assigned`, `canceled` and `completed` among the supported statuses. The [v5 Jobs overview](https://apidocs.vworkapp.com/v5-jobs) warns that underlying data can change between pages. These are documentation findings, not observations from an account. Automatic linking for the live shared account remains disabled until trustworthy entity ownership can be established.
