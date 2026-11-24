# UC4 forecast grid balances

## Prompt contract

Show Money In and Money Out, their monthly totals, and opening and closing balances in the existing entity grid. Carry each saved month's closing to the next opening unless an authorised user explicitly overrides it. Compute from the grid's invoice and bill snapshots only, use exact server-side decimals, and reuse the existing audited entity-scoped opening-balance endpoint. Never infer zero for an uncreated month or a missing opening; avoid changing saved snapshot lines.

## Plan

1. Extend `ForecastGridService` and its tests to total each month's grid lines and compute the balance chain from the latest applicable opening anchor, across navigation boundaries and missing months.
2. Regenerate the OpenAPI snapshot and frontend API types; validate all new response values at the boundary.
3. Update `ForecastGrid`, `Forecast`, styling and component/browser tests to display totals, editable openings and signed balances. Reuse `OpeningBalanceForm` and refresh the grid after saving.
4. Run targeted non-container backend tests, frontend tests, lint, typecheck, build, contract and Chromium checks; request independent review and create a new PR against main.

## Definition of done

The two sections are renamed and have correct monthly totals; opening and closing use `closing = opening + money in - money out`; subsequent openings carry forward until an explicit override or unavailable month; positive and negative balances use accessible sign colours; editing persists and refreshes the correct entity and month. Missing data remains distinct from zero, tests cover boundaries and overrides, and unrun CI, database, accessibility and hosted checks are reported.

Review follow-up: the current balance read loads saved lines back to the latest applicable opening anchor. Its cost grows with the age of that anchor; aggregate earlier months in the database if representative historical datasets show a performance issue.
