# ADR-004: Live release workflow and environment configuration

## Status
Accepted. Date: 2026-11-12. Decisions directed by the project owner (INF-006). Builds on ADR-001, ADR-002 and ADR-003.

## Context
- ADR-001 chose a manually triggered workflow with confirmation inputs for Live, and left the workflow, secret layout and an "OIDC limited to main" control for later.
- Test deploys automatically. Live must be released by a human, from a release that Test already proved, with a separate AWS stack and a separate Supabase project.
- The repository is private on GitHub Free: no protected branches, no environment reviewers, no environment secrets.
- Two near-identical sets of scripts and workflows would drift. Test and Live differ in data (identifiers, sizing, switches), not in logic.

## Decision
1. **Configuration is data.** One non-secret file per environment, `infra/environments/<env>.json`, is the only place an environment value lives. `infra/scripts/environment_config.py` validates both files, checks that no isolating value is shared, and exports values to workflows. CI runs the validation.
2. **Shared steps are composite actions** (`.github/actions/`): `setup-environment`, `deploy-backend`, `build-frontend`, `publish-frontend`, `smoke`, `supabase-migrate`, `install-supabase-cli`. Thin workflows call them: `deploy-test.yml` (automatic) and `deploy-live.yml` (manual). Composite actions run inside the caller's job, so each workflow keeps its own permissions and OIDC claim. Reusable workflows were not chosen, because they would change the claim Test's role already trusts.
3. **One on-host script** (`infra/deploy/deploy-backend.sh`) serves both environments. Differences arrive as inputs: memory limit, expected Supabase project, and whether Test-only features are allowed (never on Live).
4. **One backend image, promoted by digest.** The startup guard is environment-neutral (`SUPABASE_PROJECT_REF`). Live copies the image Test built, verifies the digest is identical, and never rebuilds.
5. **Live release gates:** manual dispatch from `main`, typed confirmation, a commit that is on `main` and succeeded in `deploy-test` and `supabase-migrations`, an image tagged for that commit, a confirmed backup, and an explicit acknowledgement for destructive migration statements.
6. **Release order:** migrate, backend (self-reverting on failed health), frontend, smoke, record. The record (`/air-concepts/live/release/{current,previous,attempt}`) holds the commit, image digest, frontend artifact run and latest migration, so rollback needs no guessing.
7. **Rollback** is the same workflow in `rollback` mode. It redeploys the recorded backend and frontend and never touches the database. Incompatible schema changes need a reviewed forward fix or a separately approved restore.
8. **AWS trust is the real gate.** The Live deploy role trusts `repo:<owner>/<repo>:environment:live` and `job_workflow_ref` of `deploy-live.yml` on `main`. A branch edit of the workflow therefore cannot assume the role. This closes the "OIDC limited to main" gap that ADR-001 left open.
9. **No Supabase network allow-list on Live.** Migrations run from GitHub-hosted runners with changing addresses. Compensating controls: enforced SSL, a long random database password, RLS with no browser grants, and a Live-only access token.
10. **Live DB connection** uses the Supabase session pooler (IPv4, port 5432, user `postgres.<ref>`).
11. **Protection:** termination protection plus `live-stack-policy.json` on the Live stack.

## Consequences
- Adding an environment, or changing a size, is one JSON edit. A pasted Test value in Live fails validation.
- The release depends on three behaviours that could not be proven offline: IAM accepting the `job_workflow_ref` condition, `imagetools create` keeping the digest, and the Supabase dry-run output format. Each fails closed (the role cannot be assumed, the digest check fails, the scanner refuses unknown output), and the runbook has the first-run check for each.
- Supabase Live secrets are repository secrets (Free plan). Any user with write access could read them from a workflow on a branch, so write access must stay small and the secrets are rotated when people leave. Revisit if the plan changes: environment reviewers and environment secrets would add a real approval and remove this exposure.
- The Test workflow now depends on `infra/environments/test.json` instead of GitHub variables. A wrong value fails the Test deploy, so the file must be checked against the stack outputs (`environment_config.py from-stack test --check`) before the first run.
- The first Live push applies all existing migrations, including two `drop constraint` statements, so it needs one acknowledgement.
- Memory and disk alarms, EBS backups and uptime checks for Live stay manual or external until INF-005.
