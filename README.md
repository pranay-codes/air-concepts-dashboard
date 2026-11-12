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
- `SUPABASE_PROJECT_REF`: the ref of the one Supabase project this environment may use (Test locally and on Test, the production project on Live). Startup fails unless the Supabase and database settings point at it. The older name `SUPABASE_TEST_PROJECT_REF` is still read when this one is absent.
- `SUPABASE_JWT_AUDIENCE`: optional, defaults to `authenticated`
- `CORS_ALLOWED_ORIGINS`: optional, defaults to the Vite dev origin
- `SUPABASE_SERVICE_ROLE_KEY`: optional, secret; staff administration only
- `XERO_CLIENT_ID`, `XERO_CLIENT_SECRET`, `XERO_REDIRECT_URI`, `XERO_FRONTEND_CALLBACK_URI`, `XERO_TOKEN_ENCRYPTION_KEY`: optional; Xero connection fails closed while blank
- Environment variables set in your shell take precedence over `.env`
- Every setting, its format and its Parameter Store name: [docs/configuration.md](docs/configuration.md)

Run: `./mvnw -q spring-boot:run`

Frontend, from `frontend/`:
- `VITE_API_BASE_URL`, `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY`: read from the root `.env`

Run: `npm ci` then `npm run dev`

## Checks
- `./scripts/check-all.sh`: backend verify, frontend checks and the API snapshot check
- `./scripts/test-backend.sh` and `./scripts/test-frontend.sh`: each part alone
- `npm run e2e` in `frontend/`: production-build Playwright journeys in Chromium, not part of check-all. Install it first with `npx playwright install chromium`. The suite supplies synthetic public Auth/API settings; it does not require hosted credentials. Chrome/Chromium is the agreed UC5 browser acceptance scope.

## Run every test (one command)
- `./scripts/test-all.sh`: backend tests, frontend checks, then Playwright. It starts the API with the `local` profile, so it reads the root `.env`, and stops it when done.
- Run it from the repo root with `.env` filled in. The local API is needed for the unauthenticated-request boundary check; OTP/workspace journeys use controlled synthetic API/Auth responses. For standalone Playwright with a running API, set `E2E_LOCAL_API=true` (optional `E2E_LOCAL_API_PORT`, default 8080). Without this opt-in, that one API check is explicitly skipped.
- Testcontainers PostgreSQL tests need Docker. Locally they skip when Docker is not running. In GitHub Actions (`CI=true`) they fail instead, so they cannot pass silently.
- Pull requests run the `e2e` job in `.github/workflows/ci.yml`. It starts the API with placeholder settings (the `ci` profile) and runs Playwright. It never reaches a hosted project.

UC5 implementation evidence and remaining hosted/runtime checks: [green-lane verification](docs/uc5-green-lane-verification.md), [requirement coverage](docs/requirements/UC5-coverage.md) and [independent review](docs/requirements/UC5-review.md).

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
