# Forecast grid item notes implementation plan

**Goal:** Show and edit an independent internal note beside each invoice or bill in the forecast grid, as approved on 25 November 2026.
**Contract:** Reuse DOCUMENT notes keyed by authorised entity and stable document ID; no Xero write or new migration. A blank note removes it. Preserve notes across forecast months and refreshes.
**Definition of done:** Grid returns each document's ID and note, shows and saves its note without affecting other rows, handles errors and entity changes, and passes relevant tests and API generation checks.

1. Add tests for separate document notes, entity-scoped reads, and grid editor saves and failures (`ForecastGridServiceTest`, `ForecastGrid.test.tsx`).
2. Extend `ForecastGridService.Row` with document ID and note using `ForecastNoteRepository`; regenerate OpenAPI and frontend types.
3. Reuse `TransactionNoteEditor` in `ForecastGrid.tsx`, including scoped save and refresh behaviour; adapt `dashboard.ts` validation.
4. Run focused backend and frontend tests, frontend checks and API contract checks; document unavailable database, review, CI and Test acceptance evidence in the PR.
