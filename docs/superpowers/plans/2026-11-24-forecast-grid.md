# Forecast Grid Implementation Plan

> Execution: implement locally in this session, with independent review and CI where available.

**Goal:** Deliver the approved customer-invoice and supplier-bill forecast grid with protected current and past snapshots and refreshable future snapshots.

**Architecture:** Add a separate versioned, append-only grid snapshot store and entity-scoped API, leaving the existing immutable UC4 snapshots and their consumers intact. Each refresh atomically records a new revision for each requested future month and creates the current month only if absent. Grid reads select the latest revision for each month and fall back to existing generated snapshot data when appropriate. Use the existing server calculator and date placement rules; group document contributions by stable contact identity where available, preserving split lines and decimal precision.

**Tech stack:** Java 25, Spring Boot, PostgreSQL migrations, React, TypeScript, Vitest and Playwright.

**Spec:** [Forecast grid design](../specs/2026-11-24-forecast-grid-design.md); [UC4](../../specs/UC4.md).

Plan and proposed contract approved by the human on 24 November 2026. Ruling: the existing forecast mirror contains contact names but no stable contact IDs; group by name and visibly disclose that identical names are combined. Capturing provider contact IDs requires a separate integration change.

## Global constraints

- Director and Financial Admin access only; authorise entity for every request and never aggregate document rows across entities.
- Preserve existing generated forecasts, current and past grid snapshots, and the existing audit trail. No Xero writes or hosted database access.
- User-visible grid contains document invoices and bills only; manual items and wages remain outside this grid.
- Use a single atomic transaction and entity lock for generation; do not expose partially refreshed windows.
- Missing snapshots are distinct from zero, failed or unauthorised data.
- Use forward-only migration, RLS and explicit grants; no local Docker or PostgreSQL for this UC4 task.

## Proposed contract for review

- `GET /api/v1/entities/{entityId}/forecast/grid?startMonth=YYYY-MM&monthCount=8` returns the entity ID, consecutive month descriptors (month, availability, snapshot timestamp), and invoice/customer and bill/supplier rows with exact decimal amounts by month. `monthCount` is bounded and invalid or unauthorised requests fail explicitly. Rows are derived from saved snapshots; they do not silently recalculate on read.
- `POST /api/v1/entities/{entityId}/forecast/grid/generate` creates the missing current-month snapshot and appends replacement versions for the next six months in one entity-scoped transaction. Its response identifies created, refreshed and preserved months. Retrying safely preserves current and past and produces the same amounts when inputs are unchanged.
- A new monthly revision table records `(entity_id, forecast_month, revision, created_at, created_by)`; a child line table records each invoice or bill contribution and its contact identity, dates, amount and source identity. Both have composite entity/month/revision keys, RLS, restricted service-role grants and no client grants. Existing snapshot tables are not modified or deleted.
- Use the server's Pacific/Auckland current month for all preservation decisions. Generate from mirrored Xero records and existing adjustments; retain prior generated month data as historical where safely mappable. Do not fabricate a historic snapshot when none exists.

## Review focus and tests

- First generation creates the current month; rerun preserves it even when underlying data changes and replaces future figures.
- Prior months remain untouched; absent history is shown as unavailable, not zero; existing frozen data is retained.
- A document with splits, overdue dates or adjustments contributes once per correct bucket; distinct contacts with equal names do not merge when stable identifiers are available.
- Entity isolation, denied roles, concurrent generation and partial failure cannot leak or corrupt data; refresh is atomic.
- December/January and financial-year transitions work; stale requests after entity switching cannot render another entity's data.

## Tasks

### 1. Contract and storage

**Files:** new `supabase/migrations/<timestamp>_uc4_forecast_grid_snapshots.sql`; new grid repository and tests under `backend/src/main/java/com/airconcepts/dashboard/forecast/` and `backend/src/test/java/com/airconcepts/dashboard/forecast/`.

- [ ] After human contract review, add forward-only revision and line tables with RLS, explicit grants and indexes. Retain old tables and data.
- [ ] Write tests for immutable revisions, entity-scoped reads, atomic generation and no current/past replacement; verify failing first.
- [ ] Implement locked, append-only revision writes and latest-revision reads; run targeted backend tests.

### 2. Forecast API and calculation

**Files:** `ForecastController.java`, new grid service and DTOs, `ForecastDatasetLoader.java` or relevant source mapping if required, endpoint and service tests, `docs/api/openapi.json`, `frontend/src/api/generated/schema.d.ts`.

- [ ] Write failing tests for first generation, repeat refresh, empty data, grouping, partial failure, access denial and date boundaries.
- [ ] Implement the reviewed endpoints, reuse authoritative placement and monetary calculations, and capture stable contact IDs. Preserve historical data and do not modify Xero.
- [ ] Regenerate OpenAPI with `./mvnw -q test -Dopenapi.update=true` in `backend`; inspect the diff for unrelated endpoints. Run `npm run api:generate` and `npm run api:check` in `frontend`.

### 3. Grid and navigation

**Files:** `frontend/src/forecast/Forecast.tsx`, `ForecastHeader.tsx`, new grid component and stylesheet, `frontend/src/api/dashboard.ts`, `frontend/src/forecast/contracts.ts`, forecast component tests and `frontend/e2e/uc4-forecast.spec.ts`.

- [ ] Write failing tests for eight-month default, month arrows crossing years, customer/supplier group totals, creation and refresh feedback, unavailable months, keyboard access and entity changes.
- [ ] Implement entity-scoped API adapters, an accessible horizontally scrollable grid and action. Keep existing combined forecast aggregate-only and preserve unrelated forecast features.
- [ ] Run focused Vitest tests, lint, typecheck, build, API check and relevant Chromium journey if runnable.

### 4. Review and delivery

**Files:** UC4 coverage/operations docs as needed; PR description.

- [ ] Update requirements traceability and document snapshot lifecycle, rollback and verification gaps.
- [ ] Run relevant backend tests and available frontend checks. Record CI/disposable PostgreSQL, manual accessibility and hosted Test gaps rather than claiming them.
- [ ] Obtain independent review if available, commit the scoped change and create a PR against `main`; do not merge or deploy.
