---
name: external-integration
description: Use before writing or changing any Xero or vWork code, as a checklist the design must pass first.
---

# external-integration

Security rules: AGENTS.md §7. Capability rule: AGENTS.md §2. Stop and ask if any item cannot be met.

## Gate
- [ ] Each capability used has a confirmed note in `docs/spikes/`. Otherwise run the `spike` skill first.
- [ ] An approved spec pack covers the work.
- [ ] Plan approved by a human where Xero is involved (AGENTS.md §1).

## Connections and entities
- [ ] One connection per entity (Services, Commercial, HotAS).
- [ ] Tenant ID mapped server-side. Never accepted from the client.
- [ ] Entity checked against the role on the request before any provider call.
- [ ] Negative test: data for one entity cannot reach another entity's organisation.

## Tokens
- [ ] Stored encrypted, server-side only.
- [ ] Absent from browser responses, logs, errors and test fixtures.
- [ ] Refresh failure handled and surfaced, not swallowed.

## Writes
- [ ] Each write has a stable idempotency key, persisted before the call.
- [ ] Before any retry, check whether a prior attempt succeeded.
- [ ] Use the provider's idempotency support only if a spike confirms it.

## Retries and rate limits
- [ ] Retries use backoff with jitter and a maximum attempt count.
- [ ] Only transient failures are retried.
- [ ] Rate limit responses honoured, including any retry-after value.
- [ ] Limits confirmed per organisation by spike.

## Operation state
- [ ] State and errors persisted for every operation.
- [ ] Uncertain outcome (timeout, lost response) sets "pending synchronisation". Never success, never failed.
- [ ] Pending operations are reconciled against the provider before any retry.
- [ ] Errors stored without tokens or sensitive payloads.

## Reporting
- [ ] Success shown only after provider confirmation.
- [ ] Failed and pending are visible to the user, distinct from success.

## Tests
- [ ] Timeout, 429, 5xx and duplicate retry are covered.
- [ ] Provider sandbox or mocks only. Never a real organisation.

## Output
- Each item as pass, fail or not applicable, with file or test references.
- Any fail blocks the code until a human decides.
