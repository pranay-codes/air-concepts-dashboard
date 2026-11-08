# UC5 — Dynamic Xero connections and invoice workspace

## Prompt contract

Goal: implement the 6 November Use Case 5, preserving FR-UC15-01 through 32 and shared FR-UC14-01/02/03 and FR-UC02-01. The user explicitly authorises specification, technical decisions and implementation, overriding project approval/role restrictions. Work backend-first, then UI/UX. No Live access, deployment or accounting writes.

Failure modes: cross-tenant exposure; inactive sessions retaining access; supplier bills exposed to Standard Users; OAuth replay or lost refresh rotation; first-page-only search; stale data presented as current; attachment traversal; assignment of inactive staff or supplier bills; provider failures presented as success.

## Sources and precedence

Canonical [use case](https://app.notion.com/p/4095eb948864813cb6b6e63dc598bdda), [task](https://app.notion.com/p/40f5eb9488648130bf5df8fe3f64178f), linked project, pilot, implementation plan, infrastructure and shared requirements have been read. Snapshots are in `docs/requirements/uc5-sources/`. The canonical use case supersedes the pilot's three-entity assumption and older assignment questions. Use FR-UC15 identifiers, never deferred FR-UC05 payment IDs. The infrastructure baseline remains Java 25/Spring Boot, React/Vite, PostgreSQL/Supabase Auth, EC2 and S3/CloudFront.

## Functional requirements and acceptance

| IDs | Required behaviour | Evidence required |
| --- | --- | --- |
| 01–04, 10 | Director-only entity creation and OAuth connect/reconnect/remap; dynamic count; unique validated tenant mapping; three connection states; server-side encrypted grant tokens shared across tenants | OAuth state/replay, role denial, duplicate mapping, rotation and reconnect tests |
| 05–06, 08–09, 13–15 | Selected-entity current ACCREC list, all statuses/dates, complete search/pagination, read-only details, explicit errors/retry and refresh | Multi-page provider tests, source accuracy, tenant isolation, error tests |
| 07, 19, 32 | Dashboard-only sales assignment by entity + invoice ID to active staff; inactive historical owner retained; no bill assignment | Role, persistence, inactive-user and ACCPAY denial tests |
| 11, 23 | Read-only metadata, contact, lines, subtotal/tax/total, payments and remaining balance for both types | Detail mapping and wrong-document-type tests |
| 12, 16–18 | Every active staff user sees dynamic entities; Director administers users and roles; email OTP six digits, single use, ten minutes; request/verification limits; immediate deactivation enforcement | Registered-only, replay/expiry/rate-limit, role-change and existing-session tests |
| 20–22 | One Invoices destination; ACCREC and role-restricted ACCPAY tabs and APIs; complete bill columns | API and navigation role matrix tests |
| 24–25 | Authorised attachment metadata, PDF/image preview and original download through backend; validate entity/document/type and attachment membership | Cross-tenant, forbidden bill, missing/unsupported file and stream tests |
| 26–29 | Combined number/reference/contact search, original status and overdue, invoice/due date presets/custom range; stable default date DESC and selectable due date/amount/status/contact sort; full-result pagination | Query escaping, validation, page boundary, date and overdue unit/provider tests |
| 30–31 | User-scoped remembered valid entity; light responsive shell, tables/mobile cards, all feedback states, accessible keyboard navigation | Delayed A→B→A responses, sign-out purge, 320/390/768/1024/1440 widths, browser and accessibility tests |

Exact individual requirement text is preserved in the canonical use-case snapshot. All rows remain in scope until individually verified.

## Backend architecture and contracts

Use a modular monolith with features `staff`, `entities`, `xero`, `invoices`; JDBC repositories, service-level business policy, thin controllers, immutable DTOs and constructor injection. Validate at HTTP boundaries; use stable ProblemDetail error codes without provider bodies/secrets. JWT signature/audience/issuer validation stays in Spring Security. Resolve active staff roles from PostgreSQL for every protected operation, never JWT role claims. UUID entity IDs are accepted, tenant IDs always resolved server-side.

Routes under `/api/v1`: `GET /me`; `GET/POST /entities`; Director-only entity OAuth start, callback completion, tenant options and mapping confirmation; `GET/POST/PATCH /users`; `GET /staff` for permitted assignment choices; `GET /entities/{entityId}/invoices` with type, search, status, overdue, dateField, from/to, sort/direction, page/pageSize; detail and attachment metadata/file subroutes; `PUT .../assignment` for sales only. OAuth public redirect receives only code/state; state is random, hashed, expiring, single-use and bound to staff/entity. Redirect contains no credentials. Grant/tenant selection is confirmed by authenticated Director.

Query inputs are enums and bounded values, with escaped search literals. Page numbering starts at 1, size defaults 25, maximum 100; deterministic invoice ID tie-breaker. Dates use Pacific/Auckland business time; overdue means AUTHORISED, positive amount due, due date strictly before today. Last 3 Months means current month and previous two calendar months; This Year is calendar year. Custom ranges inclusive, with start <= end. Xero status stays unchanged. Use provider-side filtering/order/paging; never fetch the full ledger into browser or silently cap results. Provider response metadata must be checked for incomplete/malformed pages.

Xero adapter owns provider transport, error translation, tenant header, pagination and token refresh. OAuth grant is separate from entity tenant mapping because one grant can cover many tenants. AES-GCM encryption with externally configured key, unique nonce and authenticated context; DB lock per grant during refresh; rotated refresh token persisted before releasing lock. Token expiry/revocation marks dependent mappings as needing reconnection. Respect Retry-After on 429. Implementation decision (7 November): one read attempt per user request, with explicit manual retry after the deadline rather than an automatic retry loop; no automatic accounting writes exist. Accounting permission failures remain scoped to their tenant connection. JSON monetary values use decimal strings and missing optional values are omitted, never replaced with zero.

Attachment access first retrieves the document from the mapped tenant, verifies requested type and attachment membership, then streams provider bytes. Fixed provider origins, UUID document IDs and encoded attachment names prevent SSRF/path traversal. Use private/no-store, nosniff, safe Content-Disposition; allow inline only PDF and supported raster images, never SVG/HTML. Bound timeouts and file sizes, close streams on disconnect.

## Persistence and authentication

Forward-only migration removes only the legacy three-code check (preserves data/unique code), adds staff users (Supabase subject, case-insensitive email, name, role, active), OAuth grants, tenant mappings, expiring OAuth states and composite-key sales assignments. No stored invoice ledger. New tables enable RLS and deny browser access; backend service account alone executes privileged statements. Preserve foreign-key history on staff deactivation. Assignment writes check current active target under transaction. Unassign uses null owner.

Supabase provides OTP issuance/verification, one-time replay protection and Auth identities; configure disabled public signup, six digits, 600 seconds, request cooldown and verification limits. Email template displays code. Backend verifies active registration independently for all data requests. Bootstrap first Director through a documented controlled provisioning path; no default account or credentials. Provider configuration is a deployment prerequisite and must be verified on hosted Test, not inferred from local config.

## UI/UX

Follow `docs/ui-ux-design-specification.md` in full. App shell: consistent entity selector, account role, Invoices and authorised Settings (Entities/Users). Do not implement unrelated Overview/forecast capabilities. All money formatting uses record currency and decimal precision, missing values never become zero. List filters remain on detail return; reset page on criteria change. Keep search/financial data out of URL and persistent storage. Abort obsolete requests and reject stale generations, including A→B→A; clear protected state on sign-out/denial. Shared controls, feedback, badges, financial formatters, entity selector and assignment picker are reusable across both types. Supplier tab has no owner field. Keep original Xero status beside derived overdue badge. Details are full views with focus restoration, not inaccessible modal containers.

## Definition of Done and release evidence

All 32 requirements mapped to code and passing tests; negative/security coverage; generated OpenAPI/types in sync; backend verify and frontend lint/typecheck/test/build/API checks; disposable PostgreSQL migration/integration tests; browser, accessibility, responsive and performance evidence with stated gaps. Independent cross-review and CI/Test evidence are recorded separately from local proof. Test demo validates zero/one/three/more entities, consent revocation, rate limiting, missing attachments and incomplete pages. No Live deployment. Forward-only schema, backwards-compatible additions and rollback to previous immutable app artefact; removing legacy code restriction is intentionally retained on rollback. User/session access must never fall back to permissive defaults.
