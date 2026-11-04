# Air Concepts Financial Admin Dashboard

Internal financial admin dashboard for the Services, Commercial and HotAS entities. Xero is the ledger and source of truth for invoices. This repository currently holds the project skeleton only.

## Prerequisites
- Java 25
- Node.js 26 (see `.nvmrc`)
- Git
- Docker, optional: only the Testcontainers PostgreSQL test needs it, and it skips without Docker

## Layout
- `backend/`: Spring Boot API, Maven Wrapper
- `frontend/`: React, Vite and TypeScript app
- `supabase/`: migrations (applied only through CI) and synthetic seed data
- `infra/`: infrastructure as code placeholder
- `docs/`: specs, ADRs, spikes and the API snapshot in `docs/api/openapi.json`
- `scripts/`: check scripts

## Run locally
Copy `.env.EXAMPLE` in the repo root to `.env` and fill in. `.env` is untracked. Never commit values. The backend and the frontend both read it.

Backend, from `backend/`, with `SPRING_PROFILES_ACTIVE=local`:
- `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`: PostgreSQL connection
- `SUPABASE_JWT_ISSUER_URI`, `SUPABASE_JWKS_URI`: Supabase Auth endpoints
- `SUPABASE_TEST_PROJECT_REF`: the Test project ref. Startup fails unless the Supabase and database settings point at it.
- `SUPABASE_JWT_AUDIENCE`: optional, defaults to `authenticated`
- `CORS_ALLOWED_ORIGINS`: optional, defaults to the Vite dev origin
- Environment variables set in your shell take precedence over `.env`

Run: `./mvnw -q spring-boot:run`

Frontend, from `frontend/`:
- `VITE_API_BASE_URL`, `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY`: read from the root `.env`

Run: `npm ci` then `npm run dev`

## Checks
- `./scripts/check-all.sh`: backend verify, frontend checks and the API snapshot check
- `./scripts/test-backend.sh` and `./scripts/test-frontend.sh`: each part alone
- `npm run e2e` in `frontend/`: Playwright, not part of check-all

## API contract
`docs/api/openapi.json` and `frontend/src/api/generated/` are generated, never edited by hand. See `.agents/skills/api-contract/SKILL.md`.

## Database migrations
Supabase CLI is the only migration tool. Flyway is not used (ADR-002).

- Create a migration from the repository root: `supabase migration new <name>`, then edit the new file in `supabase/migrations/`.
- Never edit a migration after it is pushed. Fix forward with a new migration. Roll back with a compensating migration, not a manual change.
- Migrations are tested on the Supabase Test project (ADR-003). There is no separate dev project. Credentials stay in GitHub environment secrets.
- Schema integration tests live in `supabase/tests/`. Pull requests run them on a throwaway database. After merge they run read-only against Test.
- The seed is synthetic only and re-runnable. Never load real customer, supplier or bank data.
- Pull requests run the `validate` job in `.github/workflows/supabase-migrations.yml`. It uses its own throwaway database and never reaches hosted Test.
- Merging to `main` runs `deploy-test` in the `test` environment. It applies only new migrations with `supabase db push`, then checks the migration list. The seed is not applied to Test.
- CI never runs `supabase db reset` against a linked project and never drops or resets Test.
- CI applies new migrations to Test on merge to `main`, then checks the migration list and runs the read-only integration tests. Live is changed only through the release workflow.
