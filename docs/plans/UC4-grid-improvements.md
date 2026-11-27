# Forecast grid improvements plan

Goal: implement the approved UC4 grid improvements while retaining entity boundaries and exact balances.

1. Add structured company identity and manual rows to the grid response in `ForecastGridService`, with unit tests for grouping inputs, manual amounts, totals and balances. Regenerate the OpenAPI snapshot and frontend types.
2. Add an entity-scoped persistent manual-row position and month-specific manual snapshots through a forward-only migration. Preserve all past months while manual item creation, editing and deactivation update current and future generated months. Extend the existing audited manual-item flow and tests. No hosted database operations.
3. Update `ForecastGrid.tsx`, `AssumptionLines.tsx`, API validation and CSS to group invoices, expand document rows, add manual items at an indicated position, and show compact sticky and coloured columns. Test grouping, notes, saved placement, accessibility and navigation.
4. Run backend and frontend tests, contract checks, lint, build and browser checks as available. Review the diff, document unrun checks, and prepare a PR without merging.

Failure modes: never sum uncreated months as zero; never combine organisations or documents solely by a parsed label; never double-count manual rows; preserve edits on failed saves; do not modify historical document snapshots.
