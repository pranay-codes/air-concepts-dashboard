# Simplify sync messages implementation plan

**Goal:** Make the Jobs and My Work sync panel easy to understand while reporting incomplete refreshes honestly.

**Architecture:** Keep the existing per-organisation, per-system recorded sync states and technical details. Shorten recovery messages and remove the redundant combined warning; do not change provider or accounting behaviour.

**Spec:** `docs/specs/GL-24-director-dashboard.md` and `docs/ui-ux-design-specification.md`; the user requested simpler sync messaging.

**Approaches:** A single combined indicator hides which system needs attention; detailed provider messages produce unnecessary noise. Keep separate system indicators with concise, actionable messages and optional technical details.

**Constraints:** Never imply a partial refresh succeeded; retain last successful sync, refresh, access handling, entity isolation, and technical diagnostics.

**Definition of done:** Focused tests show successful, failed, incomplete and disconnected states remain accurate; frontend tests, lint, typecheck and build pass; PR targets main with checks and outstanding limitations recorded.

## Task

**Files:** `frontend/src/workitems/SyncFreshness.tsx`, `frontend/src/workitems/SyncFreshness.test.tsx`.

- [ ] Add observable tests for concise incomplete and disconnected messages and removal of the repetitive general warning; run to establish failure.
- [ ] Shorten failure explanations and remove the duplicate warning without changing sync logic.
- [ ] Run focused and relevant frontend verification; inspect the diff and open a PR.
