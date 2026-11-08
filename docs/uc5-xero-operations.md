# UC5 Xero connection setup and recovery

This runbook describes implemented local behaviour and the outstanding hosted/demo checks. It does not establish that an app has been registered, credentials configured, migrations applied or real Xero consent verified.

## Configuration

Use one standard Xero Web OAuth 2.0 app for Test with demo organisations. Register the exact API redirect URI ending `/api/v1/xero/callback`. The backend requires:

| Environment variable | Test Parameter Store suffix under `/air-concepts/test/backend/` | Type |
| --- | --- | --- |
| `XERO_CLIENT_ID` | `xero/client-id` | String |
| `XERO_CLIENT_SECRET` | `xero/client-secret` | SecureString |
| `XERO_REDIRECT_URI` | `xero/redirect-uri` | String |
| `XERO_FRONTEND_CALLBACK_URI` | `xero/frontend-callback-uri` | String |
| `XERO_TOKEN_ENCRYPTION_KEY` | `xero/token-encryption-key` | SecureString |

The frontend callback is a fixed application Settings URL, not the Xero app's redirect URI. Both must be HTTPS, with HTTP allowed only for loopback development. Userinfo, query strings and fragments are refused in configured destinations. The server adds only an attempt ID to its application redirect.

Generate a cryptographically random 32-byte key and store its standard Base64 encoding in the encryption parameter. Do not put it in a Vite variable, git, logs or a chat. Keep a controlled backup. Changing this key without re-encrypting old grants makes them unreadable; reconnect with the correctly configured key to recover. There is no plaintext fallback.

The parameter helper uses hidden prompts for secrets. Deployment reads explicit names in batches of at most ten, writes only the private backend runtime file, and preserves environment isolation. The application can start without optional Xero settings, but authorisation fails explicitly until every required setting and a valid encryption key are configured. No Live setup is authorised by this runbook.

## Requested access

The authorization URL requests `offline_access accounting.invoices.read accounting.attachments.read`. These cover durable consent and the intended accounting reads, without asking for accounting-write scopes. The app must actually be eligible for the requested granular scopes. Provider tier/connection limits remain applicable; the application has no three-entity cap. Confirm the exact registered app and its scopes with a demo consent before hosted acceptance.

Read the [Xero granular scopes FAQ](https://developer.xero.com/faq/granular-scopes), [official attachment scope reference](https://xeroapi.github.io/Xero-Java/v4/accounting/index.html), [token lifecycle](https://developer.xero.com/documentation/guides/oauth2/token-types/) and [tenant connection model](https://developer.xero.com/documentation/guides/oauth2/tenants/). Documentation was checked on 6 November 2026. No real token or tenant payload was obtained.

## Connection journey

1. An active Director creates/selects an entity and starts authorisation. The backend checks the entity, generates random state, persists only its hash with Director/entity identity and ten-minute expiry, and returns the Xero authorization URL.
2. The public API callback claims that state exactly once, and only while its Director is still active. It exchanges the code server-side, encrypts both tokens with separate nonces and grant/purpose authentication, saves the grant, and records the attempt as ready. A denied, invalid or failed exchange is a terminal failed attempt. An uncertain code exchange is not blindly retried; start a new consent flow.
3. The callback returns HTTP 303 to the configured Settings destination with an attempt ID. No code, state, tenant ID or tokens are included in that redirect. The response is no-store and no-referrer. Keep callback query parameters out of HTTP/request logs; the existing Caddy configuration has no access log enabled. Any future proxy/error telemetry needs equivalent redaction.
4. Back in the application, the same authenticated Director reads the attempt and sees safe organisation choices. Confirmation submits a connection ID. The server discovers authorised tenants freshly, validates that ID, checks ownership/expiry again and persists the unique tenant mapping. Other Directors cannot use the attempt.
5. A remap/reconnection preserves the prior mapping until confirmation succeeds. A duplicate tenant already mapped to another entity rejects the transaction. After successful confirmation, other existing mappings for unchanged tenants in that same fresh grant may adopt its new credentials, keeping shared grants coherent after re-consent.

Only Directors manage connections; other active roles can read safe entity connection metadata. Stored tenant IDs and credentials never become client-selectable accounting targets. Future invoice and attachment requests use `TenantAccess` resolved from the selected entity, not a browser tenant header.

## Rotation and failures

The shared grant row is locked during refresh. Both rotated values are encrypted and persisted before returning a usable access lease. A revoked/invalid grant marks it as needing reconnection, affecting every mapping that uses it. Bad app credentials/scopes produce a setup error rather than repeatedly asking staff to consent.

If a refresh response is uncertain, retain the previous encrypted token and persist `REFRESH_PENDING` and its initial timestamp. Retry uses the provider-documented grace period, capped at 30 minutes from the first uncertain attempt. Expired recovery requires a fresh consent. A database failure after rotation is an explicit failure, never a successful read; the previous token can be reconciled within the same provider grace constraints.

HTTP 429 retains `Retry-After`, including HTTP-date values rounded up to avoid early retries. A persisted retry deadline prevents another provider call before that time. No code exchange is automatically retried. Provider response bodies, token values and descriptions are not propagated; only safe error codes, HTTP status and retry timing are exposed. Failed data retrieval must not be represented as current invoice data.

## Verification still required

Local unit/mock HTTP tests cover encryption/tampering, callback replay/ownership, provider contracts, token recovery, rate timing and safe responses. PostgreSQL tests exercise actual mapping uniqueness, revoked state, row-lock refresh concurrency and committed failure state, but have not executed locally because Docker's daemon could not start. A background launch was attempted and its process ended without creating the engine pipe.

Before acceptance: execute migrations/tests on disposable PostgreSQL; verify demo consent, real multi-tenant connection discovery, scopes, revoked permissions and rotation; build/test the Settings UI and selected-entity invoice journeys; validate hosted Test and proxy log redaction. No production proof is implied by a mock response or green unit test.

## Rollback

Keep the forward-only grant/mapping schema and the corresponding encryption key. Roll back to a compatible known-good application artefact. Do not revoke a supposedly unused provider grant automatically: it can cover other configured entities. Never restore access by accepting a browser tenant ID, exposing tokens, or disabling backend role checks.
