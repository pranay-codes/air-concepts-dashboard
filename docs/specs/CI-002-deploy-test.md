# Spec pack: CI-002 Deploy Test from GitHub Actions

- Status: approved
- Approved by / date: user, 2026-11-06, "it's approved". Approved as drafted. The user did not answer open questions 1 to 7 individually; the proposed decisions above are the working defaults, and the plan lists each one for confirmation.
- Notion page URL: https://app.notion.com/p/pranay-narotam/CI-002-Deploy-Test-from-GitHub-Actions-40b5eb9488648193ad3ec6ce721db3fe
- Notion last-edited date: 2026-11-05T20:41:02Z (from the Notion fetch metadata)
- Source text: the CI-002 page was read by Claude Code from Notion at the user's request on 2026-11-06, using the link the user gave. AGENTS.md §2 says agents never read Notion directly, and the INF-002 waiver does not name CI-002. A human must confirm this read is acceptable, or re-paste the page text.

## Goal
- On each approved merge to `main`, deploy the backend to the Test EC2 host (Docker Compose, image from ECR) and the frontend to the Test S3 bucket behind CloudFront, with no SSH and no long-lived AWS keys.
- Test environment only. Live is excluded. No production resources or credentials are touched.

## Source pages and last-edited dates
| Page | URL | Last edited |
|---|---|---|
| CI-002 Deploy Test from GitHub Actions (this task) | see header | 2026-11-05 |
| Infrastructure Architecture (3 November 2026 revision) | https://app.notion.com/p/40a5eb94886481408eb6dc4e1647c34c | unknown, human to fill. Not read by the agent. |

Precedence (AGENTS.md §2): use case pages, then Infrastructure Architecture, then everything else. Where the user's request ("deploy Spring Boot directly to EC2") differs from the approved path (ECR image run with Docker Compose), this pack follows the approved path. See open question 2.

## Current state in the repo (checked by reading files, 2026-11-06)
No tests were run for this table.

| Step | Already in repo | Gap |
|---|---|---|
| CloudFormation blueprint (ECR repo, EC2 host, S3 bucket, CloudFront, IAM roles, SSM config) | `infra/cloudformation/infrastructure.json` on branch `feat/inf-002-test-hosting`, not yet on `main` | Not merged. Blocks this task. |
| Compose and Caddy files | `infra/deploy/compose.yaml`, `infra/deploy/Caddyfile` | Caddy ARM64 digest is a placeholder. |
| Backend Dockerfile (linux/arm64, non-root) | None found anywhere in the repo | To be created. |
| Health endpoint | `/actuator/health` (actuator starter in `backend/pom.xml`; referenced in `SecurityConfig.java`) | Confirm it is public (`permitAll`) and returns 200 without a token. |
| Test AWS OIDC role | `verify-aws.yml` assumes `airconcepts-test-deployer` | Permissions not verified. Human to confirm. |
| Supabase Test migrations | `supabase-migrations.yml` runs on push to `main` with `environment: test` | Not linked to the deploy. See open question 3. |
| Frontend build | `frontend/` with `npm run build` | Build-time API base URL not defined. See open question 6. |
| CI gate before merge | `ci.yml` runs on pull requests to `main` | None. Deploy trusts the merge gate. |

## Requirement IDs
- The CI-002 page gives no requirement IDs.
- Every test must name its requirement ID (AGENTS.md §5 step 5). Blocked until a human supplies them (open question 1).

## Acceptance criteria
- AC-1 (REQ: TBD): Given an approved change merged to `main`, when the merge completes, then the `deploy-test` workflow runs with no manual step. Positive.
- AC-2 (REQ: TBD): Given a deploy run, when the backend image is built, then it is `linux/arm64`, pushed to the ECR repository from the Test stack, tagged with the commit SHA, and deployed by image digest. Negative: a deploy that references a mutable tag fails the static test.
- AC-3 (REQ: TBD, security): The workflow authenticates with the Test OIDC role only. It contains no AWS access keys, no SSH keys and no private keys. Negative: any reference to `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` or an SSH key fails the static test.
- AC-4 (REQ: TBD): Given a new image digest, when the remote deploy runs, then the SSM command goes only to the Test instance, the Compose stack restarts with that digest, and `GET /actuator/health` returns 200 before the job passes. Negative: a target that is not the Test instance ID is refused.
- AC-5 (REQ: TBD): Given a new image whose health check fails, when the deploy runs, then the job fails and the previous container is still running. Negative case.
- AC-6 (REQ: TBD): Given a frontend build, when deployed, then `frontend/dist/` is synced to the Test bucket, `index.html` is served with no-cache, hashed assets with long cache, and CloudFront invalidates `/*`.
- AC-7 (REQ: TBD): Given a completed deploy, when the smoke check runs, then the Test frontend URL and the API health URL both return 200. Any other status fails the workflow and the message names the URL.
- AC-8 (REQ: TBD): Rollback. Given a previous good commit, when its workflow run is re-run, then that commit's digest is redeployed and its ECR image still exists.
- AC-9 (REQ: TBD, security): No secret values appear in workflow logs, the SSM payload, or the Compose and Caddy files. Negative case.
- AC-10 (REQ: TBD, security): The workflow cannot target Live. Any environment other than `test` fails the static test, and no Live stack name, bucket or ARN appears in the workflow.
- AC-11 (REQ: TBD): Two merges in quick succession do not deploy at the same time. The workflow uses a concurrency group, and a queued deploy is not cancelled by a newer one.

## Confirmed decisions
- The user asked for: one successful merge to `main` deploys Spring Boot to EC2 and the frontend to S3 (user, 2026-11-06).
- No SSH and no permanent AWS keys in CI (CI-002 page, Infrastructure Architecture).
- Backend runs as a Docker image from ECR on EC2, with Compose and Caddy (CI-002 page, Infrastructure Architecture).
- Test only; Live excluded (CI-002 page).

## Proposed decisions (awaiting human approval)
- One workflow file, `.github/workflows/deploy-test.yml`, triggered on push to `main`, with a manual run button for re-deploys.
- Build and push job, then remote deploy job, then frontend job, then smoke check. Backend deploys before frontend.
- The deploy does not re-run tests; `ci.yml` already gates the pull request.
- Migrations stay in `supabase-migrations.yml`. Migrations must be backward compatible with the previous image (expand and contract).
- Rollback is a re-run of the workflow on an earlier commit. No separate rollback workflow.
- CloudFront invalidation uses `/*`.
- Compose and Caddy files travel in the SSM command payload. The remote deploy logic lives in one small script, `infra/deploy/deploy-test.sh` (name proposed).

## Out of scope
- Live deployment, Live credentials, and any Live resource (CI-002 page, ADR-001).
- Migration ordering enforcement and destructive migrations. Flagged for human approval under AGENTS.md §8.
- Alarms, monitoring dashboards and custom domains (the template's alarm resources are configured elsewhere).
- Caching of hashed assets beyond the simple rule in AC-6.
- Tightening egress rules.
- Changing `ci.yml`.

## Open questions (any open question blocks approval)
Status 2026-11-06: the spec is approved; the user confirmed the Test OIDC role exists and the infrastructure template is on `main`. Requirement IDs (question 1) remain TBD, so the PR must say so, as QA-001 did.
1. **Requirement IDs.** Which IDs does CI-002 link to? Owner: human. Blocks approval.
2. **Artifact.** Confirm Docker image on EC2 from ECR (the approved path), not a bare jar as the original request said. Owner: human. Blocks approval.
3. **Migrations.** Keep `supabase-migrations.yml` separate, with no enforced ordering before the deploy (recommended), or move migrations into this workflow? Owner: human. Blocks approval.
4. **Approval gate.** Fully automatic on merge (as requested), or a GitHub `test` environment with a required reviewer? Owner: human. Blocks approval.
5. **Upstream status.** Confirm DEV-001 and INF-001 are done. Confirm `feat/inf-002-test-hosting` (the CloudFormation blueprint and compose files) is merged to `main` before this work starts. Owner: human. Blocks start.
6. **Frontend API base URL.** Which build-time variable points the frontend at the Test API hostname, and does it need a CORS change in the backend? Owner: human. Blocks AC-6.
7. **Test deploy role.** Confirm the OIDC role (currently `airconcepts-test-deployer` in `verify-aws.yml`) can do only: ECR push to the Test repo, SSM send to the Test instance, S3 write to the Test bucket, and CloudFront invalidation on the Test distribution. Owner: human, in their own AWS identity. Blocks AC-2 to AC-4 and AC-6. The agent does not contact AWS.

## External dependencies needing a spike
| Capability | Spike note (docs/spikes/) | Status |
|---|---|---|
| Java 25 ARM64 base image, pinned by digest | none | Unverified. Needs a spike. |
| Caddy ARM64 image, pinned by digest (Notion: "CI-002 selects and verifies") | none | Unverified. Needs a spike. |
| Building `linux/arm64` on a GitHub-hosted runner (QEMU emulation or an arm64 runner). Confirm the private-repo Free plan allows it and that it finishes in reasonable time. | none | Unverified. Needs a spike. |
| SSM Run Command reaching the Test instance (agent present, instance role, Compose plugin on AL2023 ARM64) | none | Unverified. Confirm in a Test run. |
| CloudFront invalidation and S3 sync from the OIDC role | none | Unverified. Confirm in a Test run. |

## Test plan
Names are proposed and will be finalised in the plan. Every test is written and failing before implementation (AGENTS.md §5 step 5).

| Acceptance criterion | Named test | Level |
|---|---|---|
| AC-1, AC-2, AC-3, AC-6, AC-10, AC-11 | `infra/scripts/test_deploy_test_workflow.py` (proposed): parses the workflow YAML and asserts triggers, ARM64 platform, digest use, no static keys, Test-only targets, concurrency group, and `index.html` no-cache rule | static (pytest) |
| AC-4, AC-5 | `infra/scripts/test_deploy_test.py` (proposed): runs `infra/deploy/deploy-test.sh` against stub `docker` and `aws` binaries. Covers the wrong-target refusal, the healthy path, and the unhealthy path that keeps the old container | script (pytest with stubs) |
| AC-7 | Smoke step in the workflow, plus one deliberately broken Test deploy recorded in the PR | CI |
| AC-8 | Manual re-run of an earlier commit on Test, recorded in the PR | manual, on Test |
| AC-9 | Static check for secret-looking values in the workflow and Compose files, plus review of one run's logs | static and review |

## Usage and limits
- Each deploy run uses GitHub Actions minutes. Estimate after the first run and record it in the PR.
