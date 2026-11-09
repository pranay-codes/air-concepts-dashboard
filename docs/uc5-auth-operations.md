# UC5 staff access setup and recovery

These steps describe the intended Test setup. They have not been executed against hosted Auth or a hosted database. Use synthetic accounts. Keep all server credentials in the deployment secret store, never in a browser build or a command transcript.

## Apply the schema and configure Auth

1. Apply the forward-only migrations through the existing controlled CI path. Staff, pending provisions and the token hook must exist before enabling the hook. New staff-table browser grants are denied.
2. Configure hosted Supabase Auth independently of the local `config.toml`: public signup disabled; email OTP length six; expiry 600 seconds; email request cooldown 60 seconds; verification rate limit 10 per five-minute IP window. Retain provider-level request limits and a suitable SMTP service. Confirm the actual hosted settings by readback.
3. Set the magic-link email template to the code-only template in `supabase/templates/magic_link.html`. It displays `{{ .Token }}` and contains no sign-in link.
4. Enable the Custom Access Token SQL hook `public.uc5_access_token_hook`. It permits active registered staff using `otp` or `token_refresh`, preserves the original claims, and denies other methods and inactive/unregistered accounts. It runs with invoker privileges; only Auth's internal role can execute it. It does not put a trusted dashboard role in JWT claims.
5. Verify expired/replayed codes, deactivation with an already issued code, refresh after deactivation, direct password sign-in denial, unknown emails and request/verification limits in hosted Test. Backend JWT/active-role checks are independent of the hook and remain mandatory.

Local configuration and mock HTTP tests do not prove hosted expiry, hook activation or SMTP delivery.

## Bootstrap the first Director

There is no default Director or public registration route. A trusted setup operator creates a synthetic email-confirmed Auth identity using the Supabase administrative surface. Then a reviewed, controlled Test administration job inserts that exact UUID/email into `public.staff_users` with role `DIRECTOR`, active true and the approved display name.

The job must run in a transaction, take `pg_advisory_xact_lock(150016)`, verify the Auth identity and email, and refuse to insert a bootstrap account when an active Director already exists. Bind input values; do not interpolate them into SQL. Do not ship a seed with a real person or privileged default account. Read back only the safe staff identifier/name/role/active fields. Further user administration uses the Director-only application API.

The first Director signs in through the emailed code. An operator-controlled recovery follows the same deliberate identity verification if every Director is unavailable; the application itself prevents deactivating/demoting its last active Director.

## Server credential

- Environment variable: `SUPABASE_SERVICE_ROLE_KEY`, read only by the backend Auth administrator adapter.
- Test Parameter Store name: `/air-concepts/test/backend/supabase/service-role-key`, type `SecureString`.
- The Test parameter helper recognises this optional setting and hides input. The existing deployment maps it to the private backend runtime environment. No frontend parameter or Vite variable uses it.
- The application starts without this optional credential so read-only foundation endpoints remain usable; staff Auth mutations fail explicitly with `AUTH_ADMIN_NOT_CONFIGURED` until configured. Supplying it is required for a functioning UC5 administration workflow.
- Supabase Auth's base origin derives from the already validated Test JWT issuer. Requests have finite connect/read timeouts, do not follow redirects, and do not log keys or provider bodies.

## Retry and account recovery

Creating a staff user reserves a durable UUID/email before Auth creation. Auth is first checked by that ID; a missing identity is created with the reserved ID, confirmed email and no dashboard role claim. A lost response leaves the reservation intact. Retrying the same details reconciles the same identity. Conflicting email/details are rejected; the application never silently adopts an unrelated Auth identity. Pending provisions cannot access dashboard records.

Deactivation first commits the dashboard active flag, then requests a long-duration Auth ban. If Auth fails, the endpoint returns an explicit provider failure, while backend access stays revoked. Refresh Settings and retry the deactivation. Reactivation unbans Auth before enabling the local account. Deactivation never deletes staff history or invoice assignments.

After an Auth call completes, the service checks the current committed active flag. A competing opposite change triggers bounded reconciliation to that latest intent and an explicit conflict response, rather than silently reporting an obsolete change as successful. A deterministic service test covers a late ban after reactivation. Real provider timing, ambiguous timeouts and hosted concurrency still require verification; backend and token-hook checks always use the current local active flag.

Provider failures include a safe code and upstream HTTP status (zero when no response was confirmed), without credentials or raw provider data. The UI must preserve the submitted action and offer a deliberate retry; an HTTP error must not be presented as a successful account change.

## Rollback

Retain staff/provision records and the forward-only schema when rolling back the application artifact. The password-only foundation UI cannot sign in while this hook is active; use an OTP-capable known-good artifact. Disabling the hook is a separate reviewed Auth configuration change and does not replace backend authorisation. Never restore access by trusting JWT role claims or removing active-user checks.

## Evidence boundary

Local tests cover orchestration, role restrictions, provider contract/error redaction, API validation and secret routing. Disposable PostgreSQL tests cover provisioning idempotency, last-Director concurrency and hook decisions, but have not executed in this restricted local environment. Hosted Auth configuration, SMTP, database migration execution and initial Director provisioning remain unverified.
