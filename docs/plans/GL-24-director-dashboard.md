# Plan: GL-24 Director overview

Spec: [GL-24-director-dashboard](../specs/GL-24-director-dashboard.md). Approved 25 November 2026.

## Files
- Backend: `web/WorkItemOverviewController`, `workitems/OverviewService`, `workitems/OverviewMetrics`.
- Frontend: `workitems/DirectorOverview.tsx`, `workitems/overviewContracts.ts`, `api/dashboard.ts`, `workspace/Workspace.tsx`, styles, generated API types.

## Tests
`OverviewMetricsTest`, `OverviewEndpointTest`, `OverviewServicePostgresTest` (CI only), `DirectorOverview.test.tsx`, `e2e/director-overview.spec.ts`.

## Rollback
Revert the pull request. There is no migration and nothing is written.

## Outstanding after merge
Codex cross-review, acceptance on Test, and a spike for a real vWork completion time.
