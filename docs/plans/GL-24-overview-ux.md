# Plan: GL-24 Director overview, decision-first layout

Spec: [GL-24-overview-ux](../specs/GL-24-overview-ux.md). Approved 26 November 2026.

## Files
`frontend/src/workitems/`: `DirectorOverview.tsx`, `OverviewReport.tsx`, `OverviewParts.tsx`, `overviewModel.ts`, `overview.css`; `workspace/Workspace.tsx`; `ui/styles.css` (overview rules moved out).

## Tests
`overviewModel.test.ts`, `DirectorOverview.test.tsx`, `e2e/director-overview.spec.ts`.

## Rollback
Revert the pull request. There is no migration or API change and the page writes nothing itself.

## Outstanding after merge
Codex cross-review and acceptance on Test.
