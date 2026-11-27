# UC4 forecast grid improvements

Approved by the user on 27 November 2026. Group customer invoices by company, summing each available month, with expandable document details and independent document notes. Show notes above their items in red; compact and colour month columns; keep month headings and opening balances visible during vertical scrolling. Allow users to add a manual receipt or payment at a chosen position in the applicable section; persist its position within the entity and include it in server-calculated totals and balances.

Preserve access control, entity isolation, document snapshots, missing-month handling and existing note behaviour. Amounts are calculated using exact decimal arithmetic. Manual item creation, edits and deactivation affect the current month and future months only; previously generated months retain their saved manual contributions. This decision was confirmed by the user on 27 November 2026 and takes precedence over the earlier rule that deactivation changes all months. Existing manual-item validation and audit rules apply. Company names must come from structured server data. Display remains usable with keyboard and at small viewport sizes.

Existing historical grid months did not display manual contributions, even when an older financial-year snapshot contained them. Preserve that displayed history during migration; capture manual amounts for generated current and future months. Changes to staffing are outside this task.

Definition of done: grouping and per-document notes work across months; manual items are saved and placed correctly after reload and included once in balances; layout is checked at representative sizes; relevant unit, contract, frontend and browser checks pass; gaps, review and CI status are recorded.
