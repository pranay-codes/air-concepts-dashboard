# UC5 independent review and fix record — 8 November 2026

An independent fresh-context reviewer inspected the actual worktree, including untracked implementation files, against the UC5 specification and UI standard. Scope included backend entities/staff/security, OAuth/grants/read health, invoice/assignment/attachment handling, migrations, frontend auth/application lifecycle, API contracts, Settings, invoice components/formatting and browser checks. Attachment UI was excluded under the user's explicit deferral.

No Critical defect was identified. Two Important findings were accepted and corrected in one regression-and-fix pass:

| Finding | Failing evidence | Correction and verification |
| --- | --- | --- |
| Repeated Auth events for an unchanged token remounted the workspace, losing unsaved edits and invoice state | `App.test.tsx`: `FR-UC15-16 preserves Settings edits when Auth re-emits the unchanged session` reproduced the disappearing field | Preserve session/epoch for the same Auth source and unchanged token. The regression passes; changed-token clearing and sign-out regressions remain passing in the nine-test App suite. Focus and API-denial access revalidation remain independent. |
| Detail GET omitted Xero's four-decimal unit-price opt-in | `XeroInvoiceClientTest.frUc15_11_requestsFourDecimalUnitPricesForDetail` failed because the query was null instead of `unitdp=4` | Add `unitdp=4` to detail GET. The test asserts the provider query and exact `50.1234` mapping. Full offline backend verify passes: 150 tests, zero failures/errors. |

The unit-price integration change follows `.agents/skills/external-integration/SKILL.md`: capability confirmed in `docs/spikes/uc5-xero.md`; authorised UC5 spec/plan cover read-only detail; tenant resolution/role checks and encrypted server credentials unchanged; existing entity-denial tests retained; no external write or automatic retry introduced. Write-idempotency and new operation-state requirements are not applicable to this read-query correction. Existing bounded error/rate-limit handling and safe DTO rules remain covered by tests.

The reviewer explicitly distinguished unexecuted PostgreSQL concurrency/RLS/Auth-hook behaviour and hosted OTP/Xero acceptance from confirmed code defects. This review does not establish database runtime, CI, hosted Test acceptance, a commit or a PR. It is an independent agent review, not a human acceptance or merge approval.
