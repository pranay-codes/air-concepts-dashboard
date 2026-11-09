# UC5 UI execution contract

Scope amendment from the human (7 November): prioritise the working green lane through OTP, entities/Xero connections, invoice/bill reading and sales assignment, plus Director user administration. Attachment panel/viewer UI is deferred to a separate project-linked Notion task. Keep existing attachment backend code and tests. The human will create the commit. The human accepted Chrome/Chromium-only browser testing on 8 November. Complete local application integration/layout and invoice checks; hosted Test proof stays separately identified.

Deferred attachment UI task created and read back: https://app.notion.com/p/4115eb94886481a19e1cc1c649558a58 . Its Project relation points to Air Concepts - Financial Admin Dashboard; status Not started. This replaces attachment panel/viewer implementation in the current green-lane scope.

Goal: finish the approved UC5 interface using `docs/ui-ux-design-specification.md`, backend contracts and current roles. Read in full on 7 November. Constraints: light system-token design; no new dependencies or unrelated navigation/features; no Xero credentials in the browser; generated API types; private search/financial values stay out of browser location and persistence. Remember only a user-scoped stable entity ID and revalidate it.

Sequence and files:

1. Add the current staff display name to `/me`, from active server-owned staff data. Extend profile tests and regenerate OpenAPI/types. No token claims supply the role/name.
2. Replace password sign-in with registered-only request/verify email OTP in `frontend/src/auth/`. Test six-digit validation, shouldCreateUser=false, expiry/retry/cooldown, paste/autofill, safe errors, subscription cleanup and session recovery. Preserve strengthened failure tests when replacing password-specific expectations.
3. Shared tokens/styles, controls, feedback/badges, exact decimal/date formatters in `frontend/src/ui/`; typed API adapter and cancellable request hook. Test precision and context generations, including A→B→A and stale role/session responses.
4. Session/application shell and searchable native entity selector. Read profile/entities before financial requests; load only authorised settings and document tabs. Per-user entity preference contains no names/search/financial data. Clear all protected state on sign-out, identity change and permission denial; revalidate permissions on focus/denial. Show actual fetch time, distinct from connection confirmation.
5. Invoice feature hook, filters, bounded pagination, responsive table/cards and detail pane/full view; sales ownership. Test all permission/filter/loading/error/retry/empty/refresh cases, focus/scroll restoration and obsolete responses. Bills never render assignment controls or ownership. Attachment Blob/viewer work belongs to the deferred task above.
6. Lazy-load Director settings for users/entities/OAuth; preserve edits after failures, prevent duplicates, handle expired callback attempts and active/inactive ownership.
7. Vitest, lint/typecheck/build/API checks, Playwright Chromium/viewports/accessibility and measured production-build performance. Record actual evidence and gaps. Independent cross-review, CI, hosted Test acceptance and real-device/screen-reader checks remain separate requirements.

Definition of Done: all 32 requirement rows are accounted for against implemented behaviour, passing checks and the explicitly deferred attachment UI task; every applicable UI state and role is explicit; no entity/session leakage or invented money; keyboard/focus/responsive behaviour verified; operational/deployment limitations recorded honestly. No Live actions or hosted migrations.

Implementation choices: native HTML controls and local React state; one generic cancellable resource hook, no second server cache/store. Requests use fresh browser Auth tokens, bearer headers and no-store. Monetary API strings are formatted without coercion through an imprecise Number. Route/feature boundaries are lazy-loaded where substantial. Only Entity context ID is persisted, keyed by current staff subject.
