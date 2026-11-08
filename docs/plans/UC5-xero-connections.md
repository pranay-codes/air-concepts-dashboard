# UC5 Xero connection implementation details

Implements FR-UC15-01/02/03/04/10 and connection prerequisites for invoice/attachment reads. The full scope remains `docs/specs/UC5.md`; this plan adds precise module interfaces to the existing approved execution plan. User authorises implementation and routine decisions.

## Decisions and failure boundaries

- `xero/TokenCipher`: AES-256-GCM envelope with random 96-bit nonce and grant-ID/purpose authenticated data. Encrypt access and refresh independently; tokens and configuration objects redact `toString`. No token fields in controller DTOs. Missing configuration fails closed at invocation while the existing app can start.
- `xero/XeroOAuthClient`: fixed-origin authorization URL, code exchange, refresh, fresh tenant discovery. Request only read scopes plus offline access, validate Bearer/token/expiry and connection fields. No automatic retry of a code exchange. Failed refresh retains encrypted prior token; invalid/revoked consent requires reconnect, uncertain refresh is persisted as pending, bounded reads respect Retry-After. Mock HTTP tests, then demo evidence separately.
- `xero/XeroConnectionRepository`: PostgreSQL grants distinct from entity mappings; unique tenant mapping; one mapping per entity; row-lock shared grants during refresh. OAuth attempts persist hashed random state, Director/entity identity, ten-minute expiry and single-use processing state. Claim state commits before exchanging code so failure cannot reopen replay. All tables RLS enabled, no browser grants.
- `xero/XeroConnectionService`: only active Directors start/complete/select; other active roles see safe connection statuses. Start checks entity existence. Public callback claims state only if its bound Director remains active. Save encrypted grant before redirect; authenticated tenant-selection endpoint rechecks the attempt owner/expiry and discovers tenants freshly. The browser submits an opaque connection ID, never a tenant ID. Reconnect/remap leaves the old mapping intact until confirmation.
- A grant may authorize many tenants. After confirmed selection, existing mappings whose unchanged tenant IDs are also in the new grant's discovered set may adopt the new credentials. This reconciles re-consent without assuming independent token pairs per entity. Tenant IDs never change through this grant replacement. Duplicate target tenant mappings reject the entire transaction.
- API: POST `/api/v1/entities/{id}/xero/authorization`; GET `/api/v1/xero/callback`; GET `/api/v1/xero/attempts/{attemptId}`; PUT `/api/v1/xero/attempts/{attemptId}/connection` with `connectionId`; GET `/api/v1/entities/{id}/xero`. Callback redirects only to a configured application destination with attempt ID, no credentials. Responses are no-store; callback sets no-referrer. Future UI resolves callback result under its own authenticated session.

## Tests and sequence

1. Cipher tamper/context/non-determinism/config tests, failing before implementation, then passing.
2. Provider transport tests for authorization encoding, exchange/rotation/discovery, malformed/empty/duplicate data, 401/403/429/5xx/timeout and secret redaction. Implement typed safe transport results.
3. Forward-only migration and repository tests against disposable PostgreSQL: dynamic growth, replay/expiry/revoked Director, duplicate tenant, shared grant lock/rotation and no browser access. Do not infer DB execution when Docker is unavailable.
4. Service tests with mocked repositories/transport for role matrix, wrong Director, wrong entity, replay, denied consent, remap conflict, connection failures and shared grants. Implement orchestration with explicit failed/pending/reconnect outcomes.
5. HTTP tests, controllers, generated OpenAPI/types; backend verify plus frontend checks. Wire server settings through Test parameter helper and batched SSM reads (GetParameters maximum ten names per call). No deployment or credential reads.

## Definition of Done

Code, contracts and negative tests cover the behaviours above; no browser token exposure; exact-tree checks pass. Provider demo consent, real refresh/permissions, database migration execution, UI and hosted Test acceptance remain required overall UC5 evidence. No Live operation is authorised.

## Execution evidence — 7 November 2026

- Implemented cipher, fixed-origin OAuth transport, server-side state, shared grants, tenant mapping, connection service and HTTP endpoints, including a safe 303 callback. Existing mappings change only after confirmation. No accounting writes exist.
- Backend `mvn -o -q -B -Dmaven.repo.local="C:/Users/Lenovo Thinkbook/.m2/repository" -Dmaven.compiler.fork=true verify`: exit 0, 113 tests, zero failures/errors. Snapshot check ran without update mode on the final tree. Generated OpenAPI/TypeScript names and required fields are synchronized; no token/ciphertext/secret fields occur in the public contract.
- Test-first evidence includes absent cipher/client/service/routes, then passing implementations; regressions for malformed control characters, invalid app credentials, HTTP-date retry rounding, unknown authorization error redaction and old-error recovery failed before their fixes.
- Frontend lint/typecheck/test (19)/build/api:check passed after generation. Deployment stub tests: 17 passed, including two SSM batches and secret routing. Parameter-helper tests: 22 passed, no cache-provider writes.
- PostgreSQL tests cover real callback claims, mapping conflict/shared-grant adoption, row-lock concurrency, committed reconnection state, RLS and safe error clearing. They have not executed locally. Docker CLI could not read private configuration and its engine pipe was absent. A hidden Docker Desktop launch was attempted; the launched process ended and the daemon remained unavailable. No live process is being waited on.
- Read-only schema checks include all new Xero tables. Hosted database migrations, Xero demo consent/rotation, actual app scopes/limits, UI, CI and independent review remain open. No hosted or Live changes, commit or PR are claimed.
- Recovery clears an old read error only when the grant remains active, no later failure was recorded and a Retry-After deadline has elapsed. A failed metadata write still fails the operation. Public callback errors are sanitized; unknown provider messages are never stored or echoed.

Next: implement selected-entity ACCREC/ACCPAY retrieval and details using `TenantAccess`, provider-side query/paging, scoped permission/rate failures, assignments and attachment streams; then build the OTP/application UI. Do not narrow the full UC5 goal to this connection stage.
