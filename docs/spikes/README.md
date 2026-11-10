# Spikes

- Nothing counts as implemented or available until its note confirms it (AGENTS.md §2).
- Copy [TEMPLATE.md](TEMPLATE.md), or use the `spike` skill.
- Name notes `docs/spikes/<short-question>.md` and link them below.
- Status: not started | in progress | confirmed | not confirmed | partial.

## Open spikes
| Question | Status | Note |
|---|---|---|
| vWork API for hours per worker, completion photos and webhooks | not started | |
| Xero quote send, invoice recipient override and invoice attachments | not started | |
| Xero API tier and limits for three organisations | not started | |
| Supabase email passcode login with a 6-digit code, single use and 10-minute expiry | not started | |
| Java 25 on ARM64 with 2 GiB memory | not started | |
| springdoc-openapi compatibility with the chosen Spring Boot version | not started | |
| CI-002: arm64 build on GitHub-hosted runner in private repo | partial | [ci-002-arm64-runner.md](ci-002-arm64-runner.md) |
| CI-002: SSM Run Command reaches Test host with the approved template | partial | [ci-002-ssm-run-command.md](ci-002-ssm-run-command.md) |
| CI-002: official Temurin 25 arm64 image and digest | confirmed | [ci-002-temurin-25-arm64.md](ci-002-temurin-25-arm64.md) |
| CI-002: official Caddy image arm64 manifest and digest | confirmed | [ci-002-caddy-arm64.md](ci-002-caddy-arm64.md) |
| UC4 S1: `ExpectedPaymentDate` / `PlannedPaymentDate` in `GET /Invoices` list responses with `summaryOnly` | partial | [uc4-xero-forecast-fields.md](uc4-xero-forecast-fields.md) |
| UC4 S2: `If-Modified-Since` sees payments, credits and PAID/VOIDED transitions | not confirmed (settlement re-read is mandatory) | [uc4-xero-forecast-fields.md](uc4-xero-forecast-fields.md) |
| UC4 S3: statuses that can be outstanding (`AUTHORISED and AmountDue > 0`) | confirmed (docs) | [uc4-xero-forecast-fields.md](uc4-xero-forecast-fields.md) |
| UC4 S4: writing `ExpectedPaymentDate` back to Xero — scope, endpoint, idempotency | partial (deferred, not in first delivery) | [uc4-xero-expected-date-writeback.md](uc4-xero-expected-date-writeback.md) |
| UC4 S5: Apache POI populates, preserves and evaluates the client's forecast template | confirmed | [uc4-poi-template.md](uc4-poi-template.md) |
| UC4 S6: Xero rate limits for a daily full-outstanding sync across N organisations | partial | [uc4-xero-rate-limits.md](uc4-xero-rate-limits.md) |
