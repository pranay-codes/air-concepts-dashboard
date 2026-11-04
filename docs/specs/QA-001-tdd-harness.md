# Spec pack: QA-001 TDD harness and isolated integration end-to-end testing

- Status: approved
- Approved by / date: user, 2026-11-04, "go ahead without any id". Approved with no requirement IDs. This departs from AGENTS.md §5 step 5 and §9, and the PR must say so.
- Notion page URL: https://app.notion.com/p/pranay-narotam/QA-001-Create-TDD-harness-and-isolated-integration-end-to-end-testing-40b5eb948864818eb13af1bebe4f051c
- Notion last-edited date: unknown. The agent cannot read Notion; a human must fill this in.
- Source text: pasted by the user into the Claude Code session on 2026-11-04. Not read from Notion by the agent.

## Goal
- Protect the first application and its Test deployment with the minimum automated tests. The tests run locally and in GitHub Actions, and fail when a checked behaviour breaks.
- Test environment only. No Live changes and no production credentials.

## Source pages and last-edited dates
| Page | URL | Last edited |
|---|---|---|
| QA-001 (this task) | see header | unknown, human to fill |
| Implementation Plan — Agentic Development & Delivery (Living) | not given in the source text | unknown, human to fill |
| Infrastructure Architecture | not given in the source text | unknown, human to fill |

Precedence (AGENTS.md §2): use case pages, then Infrastructure Architecture dated 3 November 2026, then everything else. The source text names no conflicts.

## Current state in the repo (checked by reading files, 2026-11-04)
No tests were run for this table. It records what exists and what is missing.

| Source step | Already in repo | Gap |
|---|---|---|
| Spring Boot JUnit test for `/api/hello` | No `/api/hello` endpoint exists anywhere in `backend/`. | Endpoint absent. See open question 2. |
| Negative test for protected `/api/me` | `MeEndpointTest` covers `GET /api/v1/me`: no token and invalid token return 401, role comes from server lookup. The route is `/api/v1/me`, not `/api/me`. | Path mismatch in the source text. |
| React component test for main page (Vitest + RTL) | `frontend/src/App.test.tsx` covers the heading, the content region, the sign-in form and the token-driven profile load. | Likely done. Confirm it satisfies the step. |
| Playwright smoke test for login/navigation screen | `frontend/e2e/smoke.spec.ts` checks the heading. `frontend/e2e/sign-in.spec.ts` covers sign-in (skipped unless `E2E_TEST_EMAIL` and `E2E_TEST_PASSWORD` are set) and an unauthenticated 401 against `localhost:8080`. | Not run in CI (`.github/workflows/ci.yml` has no e2e job). No navigation exists in the app yet. |
| CI integration tests on disposable PostgreSQL | Backend CI job runs `./mvnw -q -B verify`. `PostgresConnectivityTest` uses Testcontainers and is skipped when Docker is absent. | Only a connectivity check. It skips silently without Docker, so CI can pass without running it. No application DB test yet. |
| One command for backend, frontend and Playwright, in README | `scripts/check-all.sh`, `scripts/test-backend.sh` and `scripts/test-frontend.sh` exist. `npm run e2e` runs Playwright, but check-all excludes it by design. | No single command covers all three. README does not document one. |

## Requirement IDs
- The source text gives no requirement IDs. Existing test comments cite `R2`, `R4` and `R8`, but no spec pack defines them.
- Every test must name its requirement ID (AGENTS.md §5 step 5). Blocked until a human supplies the IDs (open question 3).

## Acceptance criteria
- AC-1 (REQ: TBD): Given no token, when `GET /api/v1/me`, then 401. Negative. Exists: `MeEndpointTest.rejectsRequestWithoutToken`.
- AC-2 (REQ: TBD): Given an invalid token, when `GET /api/v1/me`, then 401. Security. Exists: `MeEndpointTest.rejectsInvalidToken`.
- AC-3 (REQ: TBD): Given a valid token with a role claim, when `GET /api/v1/me`, then the role comes from the server lookup and the token claim is ignored. Security. Exists: `MeEndpointTest.ignoresRoleClaimInTokenAndUsesServerLookup`.
- AC-4 (REQ: TBD): `GET /api/hello` returns its expected body. If protected, a request without a token gets 401. The auth mode is open question 2. Blocked on that answer and on the contract review.
- AC-5 (REQ: TBD): Given no session, the main page shows the heading and the sign-in form. Exists: `App.test.tsx`.
- AC-6 (REQ: TBD): The Playwright smoke test opens the app and shows the heading and the login screen. Partly exists. The login-screen assertion is not in smoke.spec.ts. See open question 5.
- AC-7 (REQ: TBD): Pull requests run backend integration tests against a disposable PostgreSQL container. No test connects to hosted Supabase (ADR-003). Negative: a test must fail, not skip, when Docker is unavailable in CI.
- AC-8 (REQ: TBD): Pull requests run Vitest and Playwright in GitHub Actions. A deliberately broken checked behaviour fails the job. Partly exists. Playwright is not in CI.
- AC-9 (REQ: TBD): README documents one command that runs backend tests, frontend tests and Playwright. Gap.

## Confirmed decisions
- APP-001 is done (user, 2026-11-04). It is not visible in git history on `main`, so the user is the source for this.
- Add `GET /api/hello` as a new public endpoint, no token required, fixed body, no data (user, 2026-11-04). It needs a `permitAll` rule for that exact path in `SecurityConfig`.
- Playwright in CI: smoke test only (heading and login screen). The sign-in journey is not run in CI and no Supabase Test credentials go into CI secrets (user, 2026-11-04).
- Disposable PostgreSQL: Testcontainers only. Docker is not set up locally, so these tests do not run on the developer machine. They run in the GitHub Actions workflow on pull requests (user, 2026-11-04). Supabase is never used as the test database.
- Single test command: a new `scripts/test-all.sh` runs backend tests, frontend tests and Playwright. `check-all.sh` stays unchanged (user, 2026-11-04).
- Local testing (backend and frontend) uses the repo-root `.env`, through the `local` profile and Vite's `envDir`. CI uses placeholder settings and the `ci` profile, and never reads a `.env` (user, 2026-11-04, mid-turn).
- Local `.env` is missing `SUPABASE_TEST_PROJECT_REF`, which `.env.EXAMPLE` lists and the startup guard requires. Local API startup is blocked until the user adds it (2026-11-04).
- DEV-005 (PR #6, sign-in to Supabase Test) and DB-002 (PR #5, migrations and CI) are merged on `main`.
- Protected API and DB tests come after DEV-005 and DB-002 (source text). Scope is decided in open question 7.
- Adding `/api/hello` changes the API contract. Per AGENTS.md §5 step 4, a human reviews the contract before `api-contract` regenerates `docs/api/openapi.json` and the frontend types.

## Out of scope
- Changing Live, production credentials or any Live resource (source text, ADR-001).
- Deleting or weakening an existing test (AGENTS.md §6).
- Fixing unrelated findings. Raise them as new tasks (AGENTS.md §1).
- Real Test login in Playwright inside CI, unless open question 4 is answered yes.

## Open questions (any open question blocks approval)
1. **APP-001.** Resolved: done (user, 2026-11-04).
2. **Resolved:** `/api/hello` is public (user, 2026-11-04). The `SecurityConfig` change is a security-relevant change and needs plan approval.
3. **Requirement IDs. Still open, blocks approval.** Which IDs (FR- or R-numbered) does QA-001 link to? Owner: human.
4. **Resolved:** Playwright in CI is smoke only (user, 2026-11-04).
5. **Login/navigation screen.** The app has a heading and a sign-in form, and no navigation. The smoke test asserts the heading plus the sign-in form, per the recommendation. Owner: human to confirm.
6. **Resolved:** Testcontainers in the GitHub workflow only (user, 2026-11-04). Still to confirm in the plan: a CI run must fail, not skip, when Docker is absent.
7. **Protected DB tests.** Are they in this task or a later one? Recommendation: later, as the source text suggests. Owner: human.
8. **Resolved:** new `scripts/test-all.sh` (user, 2026-11-04). Open detail for the plan: the sign-in spec's unauthenticated 401 test calls `localhost:8080`, so `test-all.sh` must start the API or skip that test explicitly. It must not fail silently.

## External dependencies needing a spike
| Capability | Spike note (docs/spikes/) | Status |
|---|---|---|
| Playwright browser install on GitHub-hosted runner (`npx playwright install --with-deps chromium`) | none | Unverified. Needs a spike or a CI run to confirm. |
| Docker on `ubuntu-latest` for Testcontainers | none | Believed available, but unverified in this repo. Needs confirmation in a CI run. |

## Test plan
| Acceptance criterion | Named test | Level |
|---|---|---|
| AC-1 | `MeEndpointTest.rejectsRequestWithoutToken` | integration (Spring Boot Test) |
| AC-2 | `MeEndpointTest.rejectsInvalidToken` | integration (Spring Boot Test) |
| AC-3 | `MeEndpointTest.ignoresRoleClaimInTokenAndUsesServerLookup` | integration (Spring Boot Test) |
| AC-4 | `HelloEndpointTest` (name proposed; auth case depends on open question 2) | integration (Spring Boot Test) |
| AC-5 | `App.test.tsx` (existing cases) | unit (Vitest + RTL) |
| AC-6 | `frontend/e2e/smoke.spec.ts` (extend) | e2e (Playwright) |
| AC-7 | `PostgresConnectivityTest` (extend: fail, not skip, when CI has no Docker) | integration (Testcontainers, CI only) |
| AC-8 | CI job step, plus one deliberate-break check | CI |
| AC-9 | README section and new script | documentation |
