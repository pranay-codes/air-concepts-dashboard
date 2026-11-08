# UC5 implementation and verification ledger

Source of scope: `docs/specs/UC5.md` and the linked 6 November canonical use case. **The full UC5 goal remains in progress.** A passing foundation test does not prove a provider integration or hosted acceptance.

| Requirement | Current evidence | Remaining work |
| --- | --- | --- |
| FR-UC15-01 | Director-only entity creation and OAuth start/attempt/confirmation APIs; ownership/replay/role tests; callback state bound to active Director Director settings and synthetic Xero start/callback/confirmation browser journey pass. | Hosted consent, CI and human Test acceptance. |
| FR-UC15-02 | Dynamic entity APIs and grant/mapping tables; no fixed organisation enum; existing four-entity API test Dynamic entity creation and refreshed entity cards pass in Chromium. | Hosted migration execution and actual OAuth growth validation. |
| FR-UC15-03 | Unique tenant/entity mapping; server-only connection-ID selection; encrypted shared grants; invoice and attachment mock transports assert server-selected tenant headers and ignore provider file URLs | PostgreSQL uniqueness/concurrency execution and demo multi-tenant validation. |
| FR-UC15-04 | Safe Connected/Not connected/Needs reconnection metadata with organisation name, confirmation time and error code; reconnect/remap orchestration Connection metadata and reconnect controls implemented. | Hosted recovery/status validation. |
| FR-UC15-05 | ACCREC list/detail adapter and HTTP routes, live provider requests, active-role checks before transport, no successful stale cache Selected-entity lists and detail browser checks pass. | Actual Xero demo retrieval. |
| FR-UC15-06 | Complete summary DTO, exact decimal-string monetary JSON and shared precision formatter tests Responsive table/cards and exact-decimal rendering implemented; five viewport checks pass. | Actual provider field accuracy; broader accessibility checks. |
| FR-UC15-07 | Verified-sales-only assignment route; transactional composite-key repository, current actor/target/mapping checks, unassign and HTTP/service tests Shared list/detail picker; assign/unassign, reload persistence and entity isolation browser checks pass. | Disposable PostgreSQL execution and hosted persistence proof. |
| FR-UC15-08 | Provider-side filters/order/bounded pages; validated totals and page completeness; second-page mock test retains full count without fetching other pages Bounded second-page navigation and search reset browser checks pass. | Actual Xero complete-result semantics and sort/page-boundary validation. |
| FR-UC15-09 | Accounting adapters and HTTP routes are read-only; assignment changes dashboard metadata only Read-only list/detail presentation implemented. | CI and human Test acceptance; independent code review completed. |
| FR-UC15-10 | AES-256-GCM context/tamper tests; single-use state; code exchange, shared-grant locking/rotation/pending recovery; safe provider errors/Retry-After; no public token DTOs | PostgreSQL lock/commit tests, demo token rotation/scopes/revocation and provider access. |
| FR-UC15-11 | Sales detail metadata, lines, subtotal/tax, payments/credit/outstanding mapping; wrong-ID/type and nullable-value tests Detail lines, GST basis, totals and payments render in the browser journey. | Actual demo field accuracy. |
| FR-UC15-12 | Every active role can dynamically list entities; inactive/missing role denied Server-authorised selector integrated. | Newly connected entity hosted financial access. |
| FR-UC15-13 | No invoice cache; every list/detail call fetches Xero; assignments persist separately. Tenant-specific durable permission/rate state protects shared grants Explicit list/detail refresh and entity-switch browser checks pass. | Database execution and hosted freshness/timing validation. |
| FR-UC15-14 | Safe provider codes/status/Retry-After, explicit failures for incomplete/truncated data, original provider failure survives health-write failure; conditional health recovery Provider failure clears financial rows; explicit retry recovers in Chromium. | Hosted timing/database validation. |
| FR-UC15-15 | Escaped combined number/reference/contact provider expression tested Debounced search and clear-filter browser checks pass. | Demo case/unicode and full-dataset query semantics. |
| FR-UC15-16 | Director-only create/list/get/update APIs; durable UUID provisioning reconciles Auth on retry; validated roles and last-Director transaction guard; service/HTTP/provider tests Director user registration/deactivation browser journey passes; unsaved edits regression passes. | Bootstrap execution and hosted Auth/database verification. |
| FR-UC15-17 | Local six-digit/600-second Auth settings and active-staff hook; browser registered-only request/verify adapter; form paste/autofill, expiry, cooldown, safe-error and duplicate-submit tests Real browser OTP adapter with synthetic Auth responses passes. | Hosted delivery/expiry/replay/rate-limit proof. |
| FR-UC15-18 | Database role checks on every request, including profile; committed deactivation before Auth ban; unban before activation; same-JWT revocation regression; bounded conflicting activation reconciliation OAuth read/confirmation denial revalidation regressions pass; current-session changes covered in App tests. | Hosted hook/ban timing, ambiguous provider timeouts and hosted security acceptance. |
| FR-UC15-19 | Active-only choices; inactive staff retained and enriched as historical owners; sales-only ownership query; actor/target checks in assignment transaction Reusable active-staff picker with historical inactive option implemented. | PostgreSQL execution and hosted inactive-owner validation. |
| FR-UC15-20 | ACCREC/ACCPAY types defined Authorised sales/bill tabs implemented and browser checked. | CI and hosted acceptance; independent code review completed. |
| FR-UC15-21 | ACCPAY list/detail/metadata/file requests require Director/Admin before token/provider access; inactive/Standard negative tests Standard-user bill controls and related requests absent in browser checks. | Hosted access verification. |
| FR-UC15-22 | Shared bounded list DTO/query/adapter and authorised HTTP route support ACCPAY with all-status/date default Shared supplier-bill list implemented and browser checked. | Actual provider verification. |
| FR-UC15-23 | Shared validated detail mapping includes lines, GST/subtotal/total, payments and remaining balance Shared bill detail implemented. | Demo field accuracy. |
| FR-UC15-24 | Attachment metadata/file API; signature-checked PDF/PNG/JPEG/GIF/WebP preview policy and download fallback | Attachment panel/viewer UI deferred to the project-linked Notion task; actual demo attachments. |
| FR-UC15-25 | Fresh document/type and membership checks; fixed origin/encoded filename; ignored provider URL; 25 MiB bound, length checks, stream closure and safe headers; HTTP/transport tests | Hosted proxy checks and real files; independent code review completed; viewer UI deferred. |
| FR-UC15-26 | Combined HTTP binding and provider-side status/overdue query; invalid query stops before transport Combined filters covered by component tests; search/clear browser journey passes. | Demo combined-filter semantics. |
| FR-UC15-27 | Date range validation, HTTP binding, inclusive provider bounds and shared Auckland preset/formatting tests Preset/custom date controls implemented with date-boundary/component tests. | Actual provider date semantics. |
| FR-UC15-28 | Default date DESC + ID tie-breaker and sort mappings in provider requests Provider sort controls implemented. | Demo sort/page-boundary verification. |
| FR-UC15-29 | Provider summary derives Auckland overdue without changing status; domain and transport tests Original status and derived overdue badge/filter implemented. | Hosted business-date proof. |
| FR-UC15-30 | UUID entity lookup, missing/inactive role denial and non-cacheable entity responses User-scoped preference, cancellation generations and A-to-B-to-A hook tests; entity/reload browser checks pass. | Independent review and hosted acceptance; expanded late-response browser checks. |
| FR-UC15-31 | Design specification read; UI requirements documented Integrated light workspace, responsive cards/tables and mobile detail focus checks pass in Chromium. | Broader accessibility/actual browser zoom, screen-reader/real-device checks and field performance evidence. Chrome/Chromium-only browser acceptance is authorised. |
| FR-UC15-32 | Bill assignment explicitly denied before transport; no bill ownership lookup/field; service and HTTP tests Bills have no ownership picker in browser checks. | PostgreSQL execution and CI/human Test acceptance. |

Current browser and regression evidence: [UC5 green-lane verification](../uc5-green-lane-verification.md). Local synthetic browser proof does not establish hosted integration.

## Existing pilot reconciliation

- UC5-001 maps to the spec, plan, source snapshots, provider spike and this ledger.
- UC5-002 remains the user/OTP workstream, including immediate backend deactivation checks.
- UC5-003 remains dynamic entities/OAuth; the canonical use case overrides earlier three-organisation wording.
- UC5-004 expands the existing invoice workstream to both document types, attachments, date presets, sorting and overdue; no duplicate task is created.
- UC5-005 remains sales assignments and hosted Test demonstration; supplier bills deliberately have no assignments.

No Notion task status has been marked complete. Independent code review is complete and both Important findings have passing regressions. Final local evidence: 150 backend tests, 73 frontend tests and 20 Chromium checks pass. No hosted database migration, deployment, demo validation, CI result for this worktree, commit or PR is claimed.

## Staff access verification boundary

Staff service, HTTP and Supabase mock transport tests execute locally. PostgreSQL tests for provisioning, last-Director concurrency and Auth-hook decisions exist, but Docker is unavailable here; those tests have not executed. Local Auth configuration parses successfully; hosted Auth is not inferred from the file. See `docs/uc5-auth-operations.md` for setup, bootstrap, retry and rollback requirements.
