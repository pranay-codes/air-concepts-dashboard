# UC5 Implementation Plan

Continuation update, 8 November: the responsive workspace, Director settings, entity/Xero/user administration and sales assignment are implemented and integrated. Chromium production-build journeys pass (20 checks, zero skips, including the packaged backend unauthenticated boundary). Browser testing found and fixed native fetch invocation and tablet overflow defects. See `docs/uc5-green-lane-verification.md` for current evidence and remaining gates. Earlier stage notes below are historical.


Goal: deliver every requirement in `docs/specs/UC5.md`, backend functionality first, then UI/UX. User authorises decisions and implementation without further approval checkpoints.

**Continuation point (7 November; superseded by the 8 November status above):** OAuth, invoice/bill list/detail, sales assignments and secure attachment backends are implemented. Last full backend verification executed 149 passing tests; Docker-backed/hosted/CI/review evidence remains open. Profile display-name and initial browser OTP/session/formatting work is implemented and undergoing full checks. Continue with the shared responsive shell, entity context, invoice UI and Director settings using `docs/plans/UC5-ui.md`. See `docs/uc5-invoice-operations.md` for the retrieval contract and evidence boundaries. Earlier counts below are stage history.

Architecture: Java 25 Spring modular monolith, JDBC/PostgreSQL, Supabase OTP/JWT, server-side Xero adapter, generated OpenAPI React/Vite client. Execute inline with test-first steps and separate review evidence.

## Global constraints

Dynamic entities; no fixed entity names; active server-side roles; ACCPAY restricted; accounting read-only; no browser tokens from Xero; no Live operations; all-data filters with bounded pages. Follow design specification for every UI component. Preserve unrelated `.claude/settings.local.json`.

## Review focus

Late organisation responses; refresh races for shared grants; OAuth replay after role revocation; cross-document attachment access; provider partial pages. Add negative tests in their owning tasks.

## Tasks and file boundaries

1. **Requirements and capability evidence** — `docs/specs/UC5.md`, source snapshots, `docs/spikes/uc5-xero.md`. Reconcile canonical requirements, existing pilot and provider docs. Document provider limits and runtime evidence gaps. No claim of demo access without a successful call.
2. **Domain query policy** — `backend/.../invoices/InvoiceQuery.java`, `InvoiceStatus.java`, `InvoiceType.java`, `InvoiceDates.java`, `XeroInvoiceQuery.java`; tests under matching test package. Write tests for FR-UC15-08/15/26–29: inclusive dates, month/year boundaries, default/all statuses, query escaping, bounded page sizes, deterministic ordering and overdue status eligibility. Run targeted Maven tests failing, implement, rerun passing.
3. **Staff and access** — new migration, `staff/StaffRepository.java`, `StaffService.java`, replace `security/NoRoleLookup.java` with database lookup. Tests for role changes/deactivation on existing sessions, inactive historical assignments, Director-only management, active assignment targets, registered-only OTP. Preserve existing JWT verification. Run unit and disposable PostgreSQL tests.
4. **Dynamic entities and OAuth** — migration plus `entities/` repositories/services and `xero/` OAuth transport, encrypted token store and connection service. Test zero/one/four entities, duplicate mapping, state expiry/replay, wrong Director, denied consent, grant sharing and concurrent rotation. Expose typed entity/connection endpoints, regenerate OpenAPI and types with API-contract workflow.
5. **Invoice retrieval** — `xero/XeroInvoiceClient.java`, `invoices/InvoiceService.java`, DTOs and controller. Provider-side query from task 2, complete metadata, detail mapping and server-side authorisation before transport. Test full-result contact/number/reference search, combined filters, pages, wrong tenant/type, 403/429/5xx/timeout/malformed or incomplete response; no successful stale response. Verify provider contract using mock HTTP and approved demo separately.
6. **Assignments and attachments** — composite assignment repository; list/detail owner enrichment; attachment client/service/controller. Test inactive owner retention, ACCPAY assignment denial, active-target races, tenant/document membership, unsupported preview, missing/large attachments, safe download headers and stream closure.
7. **OTP and reusable UI foundation** — replace password `frontend/src/auth/` flow; shared CSS tokens, controls, status/feedback/formatting in `frontend/src/ui/`; application shell/entity selector with user-scoped preference. Test no public signup, expiry/retry, accessible code paste/autofill, session loss and late A→B→A responses.
8. **Invoice UI** — `frontend/src/invoices/` typed API, feature hook, filters, list/table/cards, detail, attachment viewer, sales-only owner picker. Test role matrix, search/date presets/clear/reset pages, combined filters, refresh/failure/empty/disconnected, focus restoration and list/detail preservation. Render using design specification and test required widths.
9. **Director settings** — `frontend/src/settings/` entity/connection and staff administration. Test role changes, dynamic growth, duplicate mapping, OAuth callback recovery, deactivation and preserved inactive ownership.
10. **Completion audit** — generated contract checks; backend `./mvnw -q -B verify`; frontend lint/typecheck/test/build/api:check; Playwright Chromium only (Chrome/Chromium-only acceptance per the 8 November human decision recorded in AGENTS.md); accessibility and responsive checks; operational Test setup/rollback guide and requirement-by-requirement evidence ledger. Independent review, CI and hosted Test recorded only when observed. Any missing evidence stays open.

## Execution evidence (6-7 November, historical)

- Source/spec/plan documented, including all five pilot child tasks. Track each FR-UC15 requirement in `docs/requirements/UC5-coverage.md`.
- Implemented query/date domain rules (7 tests), current database role lookup (2 tests), dynamic entity create/list/detail with Director-only creation and active-user reads (7 endpoint tests). Remaining backend and all feature UI work stays open.
- Git branch creation attempted: denied creating `.git/refs/heads/feat/fr-uc15-invoice-workspace.lock`. Worktree edits remain possible; no branch, commit or PR claimed.
- Backend verification: `mvn -o -q -B -Dmaven.repo.local="C:/Users/Lenovo Thinkbook/.m2/repository" -Dmaven.compiler.fork=true -Dopenapi.update=true verify` exited 0; 60 tests, zero failures/errors. Generated OpenAPI and frontend types updated together. Disposable PostgreSQL tests did not execute locally; schema is not runtime-verified.
- Frontend `npm.cmd run lint --silent`, `typecheck`, `test` (19 tests), `build`, and `api:check` passed. These checks cover the existing frontend plus generated entity contract; they do not prove UC5 UI implementation.
- Final contract validation: reran `OpenApiSnapshotTest` without update mode, exit 0. Entity POST is documented and implemented as HTTP 201. Regenerated frontend types, then reran `api:check` and typecheck, exit 0.
- Toolchain evidence: default wrapper could not initialize; explicit offline Maven cache and forked compiler work. Java 25 can emit a dependency ZIP-close access diagnostic despite exit 0. One test class contained stale IDE error bytecode; confirmed using javap, recompiled with javac, then reran the full suite successfully. Maven clean plugin is incomplete locally; source files were not removed.
- Migration removes `entities_code_check` only (no rows), adds validated dynamic codes and server-owned staff roles/RLS. Hosted migrations not run; read-only schema checks now require backend-only entity reads. No hosted or Live changes.

## Staff and OAuth decisions (6-7 November, historical)

### Staff administration implementation decisions

Ruling: persist a pending staff provision with a stable UUID/email before Supabase Admin creation, pass that UUID to Auth, and reconcile it by ID on retry. This avoids duplicate Auth identities after ambiguous timeouts; an incomplete provision has no dashboard access. Only the Director may initiate or retry it. Provider support was checked against Supabase Auth `internal/api/admin.go`; hosted verification remains required.

Ruling: commit dashboard deactivation before banning Auth; return an explicit recoverable provider error if banning fails, without restoring backend access. Unban Auth before reactivating the dashboard account. Serialize staff mutations and preserve at least one active Director. Repeated deactivation/activation is safe to retry. This adds a deliberate last-Director guard against administrative lockout.

Files: `staff/StaffMember`, `StaffRepository`, `StaffService`, `SupabaseAdminClient`, `config/SupabaseAdminConfig`, `web/StaffController`, shared safe API error handling; forward-only provisioning migration. Unit tests cover role matrix, provisioning reconciliation, deactivation order/provider failures, inactive assignees and transport redaction. Disposable PostgreSQL tests cover constraints and last-Director transitions. API snapshot/types are regenerated with the controllers.

Ruling: retain Supabase's OTP issuance, expiry, replay and rate-limit implementation rather than build a duplicate code verifier. Configure six digits/600 seconds, disabled signup and email code template; add a Custom Access Token hook allowing active registered staff only through OTP or token refresh. This also blocks inactive users from obtaining a new token directly from Auth when an admin ban has failed. The hook does not add trusted role claims; backend role checks remain database-backed. Hosted hook/OTP configuration must be verified separately.

Ruling: the old QA foundation returned `/me` with a null role for an unregistered/inactive account. FR-UC15-18 supersedes that behaviour: profile access now requires an active stored role and returns 403 after revocation, even with the same still-valid JWT. The old null-role assertion is strengthened to a denial assertion, with an additional same-session revocation regression.

Ruling: after an Auth ban/unban completes, compare it with the current committed active flag. If a competing opposite change won, reconcile with at most three further calls and return a conflict. A late ban must not leave a newly active account silently reported as successfully deactivated. Uncertain provider timeouts still fail explicitly and require retry/reconciliation verification.

The new Test service-role credential is an optional SecureString parameter mapped only to backend runtime. Secret routing tests use workspace-owned temporary directories and POSIX-form environment paths so the Git Bash test run works under Windows filesystem restrictions. The host IAM policy already permits the environment's backend parameter path; no IAM widening was needed. Future OAuth settings will push the explicit SSM name list above ten entries, so the OAuth implementation must batch `GetParameters` calls.

### Staff stage verification, 6 November

- Final `mvn -o -q -B -Dmaven.repo.local="C:/Users/Lenovo Thinkbook/.m2/repository" -Dmaven.compiler.fork=true verify` exited 0, including the committed-snapshot comparison without update mode. Provider transport, service orchestration, HTTP permissions and same-JWT revocation tests execute locally. PostgreSQL-backed provisioning/last-Director/hook tests could not execute because Docker is unavailable.
- New profile-denial regression failed with HTTP 200 before the guard and passed after returning 403. The late-ban conflict regression failed before reconciliation and passed afterward. Staff endpoint tests first failed with missing routes, then passed with implementation.
- OpenAPI and generated frontend types now have stable operation IDs, required success fields and bearer security documentation. `npm.cmd run lint --silent`, `typecheck`, `test` (19 tests), `build`, and `api:check` passed after regeneration.
- Test deployment script tests: 16 passed with synthetic AWS/Docker stubs. Parameter setup helper tests: 22 passed using `.venv-infra/Scripts/python.exe -m pytest infra/scripts/test_setup_test_parameters.py -q`. No external writes occurred. Local TOML/template validation passed.
- `docs/uc5-auth-operations.md` records hosted Auth settings, controlled bootstrap, server credential routing, retry and rollback. Hosted settings, Auth delivery/replay/expiry, migration execution, bootstrap, UI and independent review are not yet proven.
- Next backend feature is shared encrypted Xero grants and OAuth/tenant mappings. The browser still has the original password sign-in placeholder; replace it with the OTP journey during feature UI work. No part of the remaining Xero/invoice/attachment/assignment/UI scope is dropped.

Continue staff administration and passwordless OTP, then encrypted shared OAuth grants/tenant mappings and Xero adapter, invoices/attachments/assignments, and finally the reusable responsive UI. Do not treat the foundation as completion of UC5. Keep the current uncommitted changes and unrelated `.claude/settings.local.json` intact. Git publication is still unavailable because branch-lock creation was denied.
