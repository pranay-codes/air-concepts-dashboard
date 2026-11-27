# Forecast amount editing implementation plan

**Goal:** Allow current and future forecast amounts, including blank cells, to be edited while preserving historical months.

**Prompt contract:** The user can edit opening balances, individual document and manual rows, and company group amounts. Overall totals and closing balances remain calculated. A company override takes precedence over the sum of its individual rows without altering those rows. Changes are internal forecasts and do not write to Xero.

**Architecture:** Store audited per-entity, per-month overrides separately from the generated forecast snapshots. Apply document and manual-row overrides to their displayed amounts; apply company overrides in place of their individual row sum in grid totals. Preserve overrides through regeneration and after a month becomes historical. Use the existing opening balance storage, while rejecting historical edits server-side. Add a role- and entity-authorised API for amount overrides and expose controls in the existing grid.

**Proposed contract for review:**

- Storage: a forward-only migration adds an override table keyed by entity, month, direction, scope (company, document, or manual item), and the applicable stable identifier. Company scope uses the exact company name already used by the grid; document and manual scopes use their existing IDs. Store an exact decimal amount and actor/timestamps. Enforce scope and identifier validity, RLS, and service-only grants. Record changes in the existing forecast audit trail.
- API: an entity-scoped forecast grid write accepts a month, direction, scope, identifier, and amount. Require the existing forecast write role and entity checks; reject months earlier than the server's current month, invalid identifiers and invalid amounts. A blank displayed cell may receive an amount. The endpoint never writes to Xero.
- Calculation: use each row's override where present; for each company and direction, use its monthly company override if present, otherwise sum its individual rows. Add manual rows and staffing once; calculate closing and carry-forward from these totals. Individual row edits under an overridden company do not change the company contribution until that override is removed or replaced.
- Regeneration and time: retain overrides across grid regeneration and month rollover. Historical values cannot be changed through any amount or opening endpoint. Preserve organisation isolation and existing historical snapshots.
- UI: show accessible inline edit controls on eligible current and future company, document and manual cells, including blanks, plus opening balances. Historical cells are display-only. Keep summary totals and closing balances calculated and display save and error feedback.

**Implementation and verification:**

1. Add focused failing tests for group precedence, independent row values, blank cells, month rollover, regeneration and organisation/role isolation.
2. Add the migration, repository and audited API; update the calculation and reject historical opening edits.
3. Regenerate the OpenAPI snapshot and frontend types; implement accessible inline grid editing.
4. Run relevant backend and frontend tests, lint, typecheck, build and API consistency checks. Report disposable PostgreSQL, CI, browser and acceptance checks separately when unavailable.
5. Review the diff for security, history preservation and generated-contract scope; commit and open a PR against `main`.

**Definition of Done:** Current and future cells persist edits, blank cells can be populated, past months reject writes, company overrides drive the totals without mutating individual rows, calculations and carry-forward remain correct, permissions and isolation are enforced, applicable checks pass, and the PR states evidence and remaining verification.
