# INF-006 handoff: Live environment

Written 2026-11-12 (Pacific/Auckland) at the end of the implementation session. Paste this file into the next session to see what is done and choose what is next.

Task: set up Live (production) using Test as the reference, with a separate Supabase production project and a manual GitHub Actions release. Source: Notion "Setup Live Infrastructure and Deployment" (https://app.notion.com/p/40f5eb94886481859d00f39b3130cb97). Spec, plan and ADR: `docs/specs/INF-006-live-environment.md`, `docs/plans/INF-006.md`, `docs/adr/ADR-004-live-release-workflow.md`. Task ID INF-006 is a placeholder the human chose; rename if Notion uses another number.

## State in one paragraph

All code, workflows, templates, tests and docs are written in the working tree on `main`. **Nothing is committed, pushed, provisioned, migrated or deployed.** No AWS, Supabase or GitHub call was made. Live does not exist yet. `AGENTS.md` and `frontend/debug.log` were already modified or untracked before this work; leave them alone. Files were renamed with `git mv`, so those renames are staged.

## Done

| Area | What exists | Where |
|---|---|---|
| Environment config | One non-secret JSON file per environment, plus one tool that validates them, checks Test and Live share nothing, exports values to workflows (`validate`, `export`, `get`, `from-stack`) | `infra/environments/{test,live}.json`, `infra/environments/README.md`, `infra/scripts/environment_config.py` |
| Backend | Startup guard is environment-neutral: reads `SUPABASE_PROJECT_REF`, falls back to legacy `SUPABASE_TEST_PROJECT_REF` | `RequiredSettingsPostProcessor.java` and test, `application-test.yml`, `.env.EXAMPLE`, `ci.yml` placeholder |
| Host script | `deploy-test.sh` became `infra/deploy/deploy-backend.sh` (`ENVIRONMENT=test|live`, `API_MEMORY_LIMIT`, `EXPECTED_SUPABASE_PROJECT_REF`, `ALLOW_TEST_ONLY_FEATURES`, first-deploy path). Caddy digest moved to `infra/deploy/caddy-image.digest` | `infra/deploy/` |
| Parameter script | `setup-test-parameters.py` became `setup-parameters.py --environment test|live` | `infra/scripts/` |
| Shared GitHub actions | `setup-environment`, `deploy-backend`, `build-frontend`, `publish-frontend`, `smoke`, `install-supabase-cli`, `supabase-migrate` | `.github/actions/` |
| Test workflows | `deploy-test.yml` and `supabase-migrations.yml` rewritten on the shared actions, same triggers, concurrency, job order and OIDC claim | `.github/workflows/` |
| Live release | `deploy-live.yml`: manual only, guard, promote image by digest, migrate (dry run, destructive-statement gate), deploy, smoke, record; rollback mode | `.github/workflows/deploy-live.yml` |
| Migration pre-flight | Scans pending migrations for DROP, TRUNCATE, ALTER TABLE ... DROP, unscoped DELETE | `infra/scripts/check_migrations.py` |
| AWS files | OIDC deploy role stack, Live stack policy | `infra/cloudformation/deployer-role.json`, `live-stack-policy.json` |
| CI | `infrastructure.yml` now lints the new template, validates the config and runs all infra pytest tests | `.github/workflows/infrastructure.yml` |
| Docs | Runbook, Test-vs-Live matrix, spec, plan, ADR-004, updates to configuration, security-access, infra README, xero-diagnostics, docs index | `docs/` |

## Evidence

| Check | Result |
|---|---|
| `python -m pytest infra/scripts -q` | 274 passed, 1 skipped (the skip pre-dates this work: Compose 2.30+ check) |
| `./mvnw -q -B test -Dtest=RequiredSettingsPostProcessorTest,JwtValidatorTest,CorsTest,HealthEndpointTest,DataSourcePoolTest,ClasspathGuardTest` (in `backend/`) | exit 0 |
| `cfn-lint` on `infrastructure.json` and `deployer-role.json` | exit 0 |
| `bash -n` on all 55 `run:` blocks in workflows and actions | 0 syntax errors |
| Deploy-backend action script against a stubbed `aws`, for Test and for synthetic complete Live values | exit 0, remote command valid, correct per-environment values |
| `python infra/scripts/environment_config.py validate` | valid |

**Not run:** Docker-backed tests (Testcontainers, disposable PostgreSQL: Docker not running), full backend and frontend suites, `actionlint` (not installed), any workflow on GitHub, the new Linux CI step that runs all infra tests, any AWS or Supabase call.

## Decisions made (do not re-litigate)

- Generalise `deploy-test.sh` into one script instead of copying it (human choice).
- Shared steps are composite actions, not reusable workflows, so Test keeps its OIDC claim (ADR-004).
- Task ID INF-006 (human choice). Uptime monitoring uses an external service (human choice).
- No Supabase network allow-list on Live (GitHub runners have changing addresses). Live DB URL is the session pooler, port 5432.
- Live has its own instance and sizing: `t4g.small`, 20 GiB (template rule), container limit proposed at `1024m`.
- The automatic Supabase "recent backup" API check was dropped. The release requires a typed `backup_confirmed` instead.
- Test never holds placeholders; Live may hold `REPLACE_ME` in CI but a Live deploy refuses them.

## Open items and risks

1. **Test config values need verifying before merge.** `infra/environments/test.json` was filled from `docs/inf-002-test-aws-checks.md` (6 Nov) and the role ARN in `verify-aws.yml`, not from your GitHub variables. `supabaseProjectRef` is empty (not pinned). Check with `python infra/scripts/environment_config.py from-stack test --profile <p> --check` and compare `deployRoleArn` with `TEST_DEPLOY_ROLE_ARN`. A wrong value breaks the Test deploy.
2. **The Test deploy path changed.** The first Test run after merge is the real proof. After it succeeds, delete the old `TEST_*` and `ECR_REPOSITORY_URI` GitHub variables.
3. **Three assumptions that fail safely, to confirm on first Live use:** IAM accepting the `job_workflow_ref` trust condition; `docker buildx imagetools create` keeping the digest between ECR repositories; the Supabase `db push --remote --dry-run` output format. Runbook section 6.3 says what to do for each.
4. **First Live push** needs `destructive_ack` once: two migrations (`20261106050000_...` and `20261111010000_...`) drop and re-add a constraint.
5. **Merge order:** the image promoted to Live must be built after the neutral guard merged and have run on Test. Everything was done in one working tree; split into three pull requests under about 900 lines if you want each proven separately (slices in `docs/plans/INF-006.md`).
6. **GitHub Free limits:** no enforced approver; Live Supabase secrets are repository secrets. Revisit ADR-001 if the plan changes.
7. **Unresolved human inputs:** Live monthly budget and incident contact; final `apiMemoryLimit` after measuring the host; REL-001 client and data-handling approval before real client data.
8. **Known gaps carried forward (INF-005):** memory and disk alarms, EBS backup scheduling, automatic host recovery. The first-Director bootstrap SQL is marked DRAFT and Test-only; it needs review before a Live run.
9. **Xero:** a Live Xero app, redirect URI and a new token-encryption key are needed; the nightly forecast sync is on by default once an organisation is connected.

## What is next (suggested order)

1. Review the working tree and decide the pull request split. Nothing is committed. Create branches `chore/inf-006-env-neutral-backend-deploy`, `chore/inf-006-reusable-deploy-workflows` (shared actions), `chore/inf-006-live-environment` if splitting.
2. Do open item 1 (verify `test.json`), then merge slices 1 and 2, watch a green Test deploy and Test migration run, delete the old GitHub variables.
3. Cross-review: AGENTS.md says the tool that did not write the code reviews it. Use Codex or a human on the diff, with the AGENTS.md section 11 checklist.
4. Get the spec pack `docs/specs/INF-006-live-environment.md` approved (it is a draft).
5. Follow `docs/live-environment-runbook.md` in order: Supabase production, AWS stack and protection, deploy-role stack, DNS, Parameter Store, GitHub environment and secrets, first release, auth hook and first Director, verification, rollback rehearsal.
6. After Live works, plan the follow-ups: INF-005 monitoring and backups, a decision on GitHub plan upgrade, and a Live Xero connection.

## Gotchas for the next session

- AGENTS.md rules still apply: never connect to hosted Supabase except read-only MCP on Test, never run deploy commands or touch Live, keep secrets out of output.
- On this Windows machine, Python subprocesses resolve `bash` to the WSL launcher. Use `shutil.which("bash")` (Git Bash) or set `TEST_BASH`. Git Bash has `python`, not `python3`; CI runners have `python3`.
- When editing files from a shell heredoc, prefer the Write or Edit tools: a Python heredoc turned `\n` and `\t` into real characters twice during this session.
- Run tests from the repository root: `python -m pytest infra/scripts -q`. The test venv is `.venv-infra`.

## Quick file map

```
infra/environments/{test,live}.json        configuration (non-secret)
infra/scripts/environment_config.py        validate, export, get, from-stack
infra/scripts/check_migrations.py          destructive-statement scan
infra/scripts/setup-parameters.py          Parameter Store writer (--environment)
infra/deploy/deploy-backend.sh             host release script (test|live)
infra/cloudformation/deployer-role.json    Live OIDC deploy role
infra/cloudformation/live-stack-policy.json
.github/actions/*                          shared steps
.github/workflows/deploy-live.yml          manual Live release and rollback
docs/live-environment-runbook.md           manual setup steps
docs/environments.md                       Test vs Live matrix
```
