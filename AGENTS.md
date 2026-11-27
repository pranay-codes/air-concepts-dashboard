# Air Concepts Financial Admin Dashboard: Agent Rules

## Learned Rules
- Forecast amounts, including blanks, are editable for current and future months; past months are read-only. Company overrides are independent of individual rows and determine the company contribution to totals (human clarification, 27 November 2026).
- Forecast document and expanded sub-row labels should match company group rows in font weight and compact spacing (human correction, 27 November 2026).
- Forecast manual-line changes apply to the current and future forecast; never modify previous months' forecasts (human instruction, 27 November 2026).
- Service Jobs automatic sync must accept a minimum interval of one minute (human correction, 26 November 2026).
- Treat fewer than 100 Xero quotes as a normal result, not an error in itself (human correction, 26 November 2026).
- Forecast grid totals and closing balance must have a clearly distinct row background from ordinary entries (human correction, 25 November 2026).
- For the November 2026 forecast grid change, focus on customer invoices and supplier bills; defer employee wages (human instruction, 24 November 2026).
- For out-of-order job tracking, proceed without a vWork Test account and use official vWork documentation; record account-specific behaviour as unverified (human instruction, 23 November 2026).
- For out-of-order job tracking, use official Xero documentation to guide testing; distinguish documentation-based tests from account-specific verification (human instruction, 23 November 2026).
- For Quote-to-Invoice, do not set up or run PostgreSQL locally; use CI for database verification (human instruction, 19 November 2026).
- For the Phase 1 client-delivery release, create only the requested tag and release notes; do not use Superpowers (human instruction, 12 November 2026).
- For UC4, work without Docker: run no Docker commands or container-backed tests locally; use unit/mock/HTTP tests and report disposable-PostgreSQL coverage as not run (human reiterated 10 November 2026).
- For UC4, complete development locally before committing (human instruction, 10 November 2026); do not block implementation on per-slice commits or PR publication. Record slice plans and test evidence locally, then commit after development is complete.
- For UC4, Docker is not running locally (human confirmed 10 November 2026); report Docker-backed tests as not run and leave Docker startup to the human.
- For UC4, read all supplied repository sources and resolve open choices with best judgement as instructed on 10 November 2026; record decisions and proceed through sensitive slices without approval pauses. Live operations, hosted database connections, deployment and Xero accounting writes remain prohibited; retain the human fixture-scrub confirmation gate.
- For UC5 browser acceptance, use Chrome/Chromium testing as requested by the human on 8 November; Firefox and WebKit execution are outside this task's acceptance scope.
- For the current UC5 green lane, finish Director/Entities/Xero/Users settings, sales assignment, application integration, workspace layout and invoice checks. Defer the attachment panel/viewer to a project-linked Notion task; retain existing secure attachment backend work.
- For UC5, the human will create the commit. Stop retrying Git writes; finish the implementation, verification and worktree handoff without treating commit creation as a blocker.
- For UC5, the human authorises reading linked Notion sources, documenting requirements and the plan, making implementation decisions with best judgement, and implementing backend functionality before UI/UX without additional approval pauses. This does not authorise Live operations.
- For UI/UX work, enforce the existing design specification; treat Services, Commercial and HotAS as the client's current organisation configuration, with generic support for many organisations.
- Write UI/UX specifications as standalone instructions for the intended interface, with concrete design standards and behaviour rather than PDF observations or analysis framing.
- Keep infrastructure deployment guidance direct and reusable. Separate identity-management prerequisites from CloudFormation deployment steps; do not turn deployment help into an extended IAM Identity Center setup walkthrough unless the human requests it.
- For this INF-002 session, read the linked Notion task directly and waive the local spec-pack gate as instructed by the human. Build one reusable Test/Live CloudFormation blueprint with environment configuration; this does not authorise Live deployment or access.
- For Quote-to-Invoice, work through the implementation plan from top to bottom, continuing past checks that must run in CI or need later external verification (human instruction, 20 November 2026).
- For Quote-to-Invoice, prioritise getting the main end-to-end flow working and defer broader edge-case coverage for now (human instruction, 20 November 2026).
- For Quote-to-Invoice, the human considers the goal complete when the main flow is working; document deferred edge cases and external verification (human instruction, 20 November 2026).
- For this task, focus on completing the Green Line quote-to-invoice use case and avoid unrelated dashboard work (human reminder, 21 November 2026).
- For the Green Line main flow, defer edge-case work unless it is needed for safe invoice handling (human reminder, 21 November 2026).
- Finish the Green Line invoice review and mock submission, link the created invoice, and verify the synthetic quote-to-job-to-invoice journey before marking the goal complete; document CI and provider verification as follow-up (human instruction, 21 November 2026).

## 1. Core rules
- Change one task at a time. One branch and one session per task. — Raise unrelated findings as a new task; do not fix them here.
- Make a small plan before coding. List files and tests, and wait for human approval when auth, Xero, migrations or entity isolation are involved. — Edit nothing until the plan exists.
- Run tests before claiming success. Quote the command and its result. — Report anything not run as not run.
- Create a PR rather than deploying to Live. Stop at an open PR and let humans merge. — Never merge, release or approve your own PR.

## 2. Source of truth and conflicts
- Apply precedence for the Notion project "Air Concepts - Financial Admin Dashboard": use case pages, then Infrastructure Architecture page dated 3 November 2026, then everything else.
- Work only from the human-created or human-approved spec pack at docs/specs/<task>.md; never read Notion directly, including the delivery process page "Implementation Plan — Agentic Development & Delivery".
- Stop and ask when no spec pack exists, when requirements conflict, or when anything is ambiguous.
- Known conflict, never resolve silently: FR-UC04-01 to 07 is used by both the supplier bill use case and the forecast use case.
- Known conflict, never resolve silently: Product Scope v1.3 describes app-sent invoices, bank-file payments, Akahu, Google Calendar read and automatic email intake; Use Case 1 (30 October) supersedes it.
- Known conflict, never resolve silently: the UC4 architecture page shows Next.js on Vercel; the 3 November infrastructure decision supersedes it.
- Treat no vWork or Xero capability as implemented until a spike note confirms it.
- Write the spike note yourself; never guess.

## 3. Roles
- Claude Code: spec packs, plans, API and database contracts, security-critical code, review of Codex's work.
- Codex: failing tests and implementation from an approved spec, review of Claude's diffs.
- Have the tool that did not write the code review it.

## 4. Stack, layout and commands
- Stack: Java 25, Spring Boot and Maven Wrapper; React, Vite and TypeScript; Supabase PostgreSQL and Auth; GitHub Actions on the Free plan; AWS Sydney (EC2 API, S3 and CloudFront frontend).
- Domain: the client currently has three entities (Services, Commercial, HotAS), each with its own Xero organisation; the application supports a variable number of authorised organisations. Xero is the ledger and source of truth for invoices; vWork stays the field-work tool.
- Layout: /backend, /frontend, /supabase, /infra, /docs, /scripts.
- Backend (/backend): build `./mvnw -q -B -DskipTests package`; test `./mvnw -q -B test`; verify `./mvnw -q -B verify`; run `./mvnw -q spring-boot:run` with SPRING_PROFILES_ACTIVE=local. Lint: none chosen; Enforcer runs in verify.
- Frontend (/frontend): build `npm run build --silent`; test `npm test --silent`; lint `npm run lint --silent`; typecheck `npm run typecheck --silent`; run `npm run dev`; e2e `npm run e2e` (not in check-all).
- API contract: regenerate the snapshot with `./mvnw -q test -Dopenapi.update=true` (/backend), then `npm run api:generate` (/frontend). Check with `npm run api:check`.
- Check all: `./scripts/check-all.sh` (backend verify, frontend checks, api:check).
- Prefer quiet output modes; remove a TODO only after the command has run successfully.

## 5. Workflow
1. Get the spec pack approved by a human.
2. Spike any unverified external capability.
3. Get the small plan approved.
4. Get the contract (OpenAPI and any migration file) reviewed by a human.
5. Write failing tests from the acceptance criteria, each naming its requirement ID.
6. Implement until tests pass.
7. Get cross-review from the other agent.
8. Get CI green.
9. Get human acceptance on Test.

## 6. Testing
- Use JUnit and Mockito, Spring Boot Test, Vitest with React Testing Library, and Playwright for key journeys.
- Run integration tests on a disposable PostgreSQL. Integration tests may also run against the Supabase Test project after deploy, but only read-only (supabase/tests/). Destructive tests never touch hosted Supabase (ADR-003).
- Cover negative and security cases.
- Fix the code; never delete or weaken a test to get green.

## 7. Security and data
- Verify the Supabase JWT in Spring Security.
- Check role and entity on every backend request.
- Never write one entity's data to another entity's Xero organisation.
- Keep Xero and OAuth tokens server-side and encrypted; they never reach the browser, logs, git or prompts.
- Rate limit passcode login, make passcodes single use, and expire them.
- Make retries of external writes idempotent.
- Never read, print or commit secrets, .env files or production values.

## 8. Database migrations
- Treat CI as the only path to hosted databases. CI applies migrations to the Supabase Test project on merge to main, then runs read-only integration tests against it (ADR-003).
- Write migration files; never connect to a hosted Supabase project, except where CLAUDE.md allows read-only MCP on Test.
- Never run supabase db push, supabase link, supabase db reset against a linked project or supabase migration repair; never use Supabase MCP write tools.
- Keep migrations forward-only and immutable; make changes as new migrations.
- Follow expand and contract, enable RLS on new tables, set explicit grants and set lock timeouts on risky statements.
- Flag any DROP, TRUNCATE or unscoped DELETE for human approval.
- Roll forward with a compensating migration; keep one migration history; do not use Flyway.

## 9. Git and PRs
- Name branches feat/<requirement-id>-<short-name> or chore/<name>.
- Use conventional commits that include requirement IDs.
- Keep PRs under about 900 lines of diff.
- List in each PR: requirement IDs, test evidence, security checklist, reviewing agent, rough note of usage consumed.
- Add a migration checklist to the PR when relevant.

## 10. Environments and deployment
- Use hosted Supabase Test for local work with synthetic data only.
- CI deploys to Test after merge; a human releases Live through a manually triggered workflow.
- Never run deploy commands, hold Live credentials or touch Live resources.

## 11. Review checklist
- Check the diff against the spec pack for entity isolation, token handling, backend role checks, passcode limits, idempotency of external writes, weakened or removed tests, migration safety and secrets.
- Report findings only, grouped by severity.

## 12. Context and tokens
- Keep one task per session; use plan mode before implementing and compact at task boundaries.
- Read only the files named in the spec pack and files found by targeted search.
- Keep test output quiet; use subagents only to isolate large reads.

## 13. Definition of done
- Requirement and acceptance criteria linked; failing tests written first; tests passing including negative and security cases; CI green.
- Docs and traceability updated; other agent reviewed; Test acceptance passed where relevant; rollback impact recorded for deployment changes.

## 14. UI/UX and frontend standards
- Before planning, implementing or reviewing frontend components, layouts, navigation, forms, tables, charts or UI states, read and follow [UI/UX Design Specification](docs/ui-ux-design-specification.md). It is the shared UI/UX standard; approved task specifications govern business capabilities and permissions. Raise conflicts before implementation.
- Preserve the wireframes' light-only visual direction and shared design tokens. Apply the specification's responsive, WCAG 2.2 AA, financial-data, feedback and recovery standards across all relevant states.
- Use the approved React/Vite/strict TypeScript architecture and the specification's component, state, API and cache boundaries. Select optional libraries only for demonstrated needs; recommendations do not authorise dependency or architecture changes.
- Populate organisation selection from authorised server data and isolate organisation-specific requests, state and caches using stable IDs. Verify switching, stale responses and permission changes; backend authorisation remains mandatory.
- Before claiming UI work complete, apply the specification's Definition of Done and record relevant lint, typecheck, test, build, accessibility, responsive/browser and performance evidence. Report checks not run and measurement gaps explicitly.
