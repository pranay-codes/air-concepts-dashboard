# Staffing expenses implementation plan

**Goal:** Add entity-scoped scheduled staffing estimates to the existing forecast.

**Contract:** Extend the existing staffing record with hourly rate, weekly hours, frequency, employment dates and a first payday. Existing fixed-monthly records remain usable. Weekly and fortnightly paydays repeat from the first payday; monthly paydays use its day of month, clamped to the last day. A monthly payment is weekly wages multiplied by 52/12, rounded to cents. Paydays outside the employment dates are excluded. These are estimates, not payroll.

**Plan:** Add forward-only schema and validation, share one calculation between the forecast and grid, expose the existing role-restricted staffing form, and render only the combined amount above Total Money Out. Keep the saved invoice and bill snapshots unchanged; staffing is recalculated when the grid is read. Regenerate the API contract.

**Definition of done:** Automated tests cover frequency and payday boundaries, legacy records, entity and role access, no double counting, balances and the form. Run relevant backend and frontend checks and report database, browser, CI and independent-review gaps. Create a PR against main without merging or deployment.
