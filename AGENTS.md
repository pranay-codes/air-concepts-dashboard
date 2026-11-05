# Air Concepts Financial Admin Dashboard: Agent Rules

## Learned Rules
- Keep infrastructure deployment guidance direct and reusable. Separate identity-management prerequisites from CloudFormation deployment steps; do not turn deployment help into an extended IAM Identity Center setup walkthrough unless the human requests it.
- For this INF-002 session, read the linked Notion task directly and waive the local spec-pack gate as instructed by the human. Build one reusable Test/Live CloudFormation blueprint with environment configuration; this does not authorise Live deployment or access.

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
- Domain: three entities (Services, Commercial, HotAS), each with its own Xero organisation; Xero is the ledger and source of truth for invoices; vWork stays the field-work tool.
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
- Keep PRs under about 300 lines of diff.
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
