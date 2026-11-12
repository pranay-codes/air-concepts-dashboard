# Spec pack: INF-006 Live infrastructure and deployment

- Status: approved
- Approved by / date: Pranay / 2026-11-12
- Notion page URL: https://app.notion.com/p/40f5eb94886481859d00f39b3130cb97
- Notion last-edited date: 2026-11-10 (09:30 UTC)
- Requirement ID: INF-006 (placeholder confirmed by the human on 2026-11-12; replace if the Notion board uses another number)

## Goal
- Live runs in the same AWS account as Test but on its own resources, with its own Supabase production project, and a human can promote a release that Test already proved to Live through GitHub Actions. Test is unchanged.
- Test and Live differ in configuration data only. One set of scripts and workflows serves both.

## Source pages and last-edited dates
| Page | URL | Last edited |
|---|---|---|
| Setup Live Infrastructure and Deployment | https://app.notion.com/p/40f5eb94886481859d00f39b3130cb97 | 2026-11-10 |
| Setup Test Infrastructure, REL-001, CI-002, QA-002, INF-002 | linked from the task page | not re-read here |

## Requirement IDs
- INF-006 (this task). Related: INF-002 (template), CI-002 (Test deploy), REL-001 (earlier Live planning), ADR-001 (release gate), ADR-002 (migrations via CI).

## Acceptance criteria
- AC-1 (INF-006): Given the repository, then `infra/environments/test.json` and `live.json` have identical keys, share no resource identifier, Supabase project, role, repository or parameter prefix, and CI fails if they ever do.
- AC-2: Given `live.json` still has `REPLACE_ME` values, then a Live deploy is refused before any AWS call.
- AC-3: Given the same backend image, then it starts on Test and Live: the startup guard accepts any one project named by `SUPABASE_PROJECT_REF` and rejects Auth or database settings for another project (including a Test project when Live is configured).
- AC-4: Given a Live release, then the backend image is copied from the Test repository by digest, the copy is verified to have the same digest, and nothing is rebuilt.
- AC-5: Given a release request, then it is refused unless: started from `main`; `DEPLOY LIVE` typed; commit is on `main`; `deploy-test.yml` and `supabase-migrations.yml` both succeeded on that commit; the image is tagged for that commit in the Test repository; a production backup is confirmed.
- AC-6: Given pending migrations containing DROP, TRUNCATE, ALTER TABLE ... DROP or an unscoped DELETE, then the release stops unless a human sets `destructive_ack`. A dry run is listed first; seed, reset and repair never run.
- AC-7: Given a release, then migrations run first, then the backend (waits for health, restores the previous release itself on failure), then the frontend built from the same commit with Live public settings, then a smoke test including a 401 for an unauthenticated API call, then the release is recorded.
- AC-8: Given a failed or unwanted release, then the same workflow in rollback mode redeploys the last known-good backend digest and its frontend artifact, and never changes the database.
- AC-9 (security): Given any run, then no AWS access keys, SSH or shell tracing are used; the Live role can only reach Live resources, may read (not write) the Test image repository, and is denied Test parameters; the Live host can only read Live backend parameters.
- AC-10 (security): Given a Test-issued Supabase token, then the Live API returns 401.
- AC-11: Given Test, then its workflows keep their triggers, concurrency, job order, OIDC claim and health-check/rollback behaviour, and can only target Test.
- AC-12: Given the runbook, then a human can create Supabase production, the Live stack, DNS, secrets, the first release, backups with a restore rehearsal, and a rollback rehearsal, with a verification for each step.

## Confirmed decisions
- Read the Notion task directly and proceed (human instruction, 2026-11-12).
- Generalise `deploy-test.sh` into one environment-neutral script (human choice, 2026-11-12).
- Live and Test are separate instances with separate sizing; Live is `t4g.small` / 20 GiB by the template's own rule.
- External uptime service for app-level monitoring (human choice, 2026-11-12).
- Task ID INF-006 (human choice, 2026-11-12).
- Nothing is provisioned, migrated or deployed by an agent; Live credentials are never held by an agent.

## Out of scope
- Provisioning, migrating or deploying anything.
- Memory/disk alarms and automated EBS backups (INF-005 follow-up).
- Real client data (needs the REL-001 data-handling approval first).
- A separate AWS account for Live; Xero accounting writes; vWork.

## Open questions
- Which monthly budget and incident contact apply to Live (owner: human).
- Final `apiMemoryLimit` for Live after measuring the host (proposed `1024m`).
- Whether the GitHub plan will move to one that supports environment reviewers and environment secrets (ADR-001 revisit).

## External dependencies needing a spike
| Capability | Spike note (docs/spikes/) | Status |
|---|---|---|
| IAM accepts the `job_workflow_ref` claim condition for GitHub OIDC | to be confirmed on the first Live dry run (fails closed if unsupported) | open |
| `docker buildx imagetools create` keeps the digest when copying between ECR repositories | to be confirmed on the first release (the workflow fails loudly if not) | open |
| `supabase db push --remote --dry-run` output lists migration versions | to be confirmed on the first release (the scanner refuses unknown output) | open |

## Test plan
| Acceptance criterion | Named test | Level |
|---|---|---|
| AC-1, AC-2 | `infra/scripts/test_environment_config.py` | unit |
| AC-3, AC-10 | `RequiredSettingsPostProcessorTest`, `JwtValidatorTest` | unit |
| AC-4, AC-5, AC-7, AC-8, AC-9 | `infra/scripts/test_live_release.py` (static workflow and IAM checks) | static |
| AC-6 | `infra/scripts/test_check_migrations.py` | unit |
| AC-7, AC-9 (host side) | `infra/scripts/test_deploy_backend.py` | behaviour with stubs |
| AC-11 | `infra/scripts/test_deploy_test_workflow.py` | static |
| AC-12, AC-4 digest, AC-10 against a real project | first Live dry run and runbook verification on Live | manual |
