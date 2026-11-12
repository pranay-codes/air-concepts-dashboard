# Test and Live: what differs, and where

Test and Live differ in **data**, not in code. Scripts, workflows and the backend image are shared. Every difference below lives in one place, so changing one is one edit and a mix-up is one failed check.

Configuration files: [`infra/environments/test.json`](../infra/environments/test.json) and [`live.json`](../infra/environments/live.json). Field reference: [`infra/environments/README.md`](../infra/environments/README.md). Setup steps for Live: [live-environment-runbook.md](live-environment-runbook.md). Decision record: [ADR-004](adr/ADR-004-live-release-workflow.md).

| Area | Test | Live | Where it lives |
|---|---|---|---|
| Release trigger | Automatic on every merge to `main` | Manual `workflow_dispatch`, typed `DEPLOY LIVE`, `main` only | `deploy-test.yml`, `deploy-live.yml` |
| Backend image | Built (arm64) and pushed | Copied from Test by digest, digest verified, never rebuilt | the two workflows |
| Migrations | Applied automatically on merge | Dry run, destructive-statement gate, backup confirmation, then applied inside the release | `supabase-migrations.yml`, `deploy-live.yml`, `check_migrations.py` |
| Seed data | Never applied to hosted Test (local and CI only) | Never | not run by any hosted workflow |
| Host size | `t4g.micro`, 10 GiB | `t4g.small`, 20 GiB (Live minimum enforced by the template) | `infra/params/<env>.local.json` |
| Container memory | `apiMemoryLimit` 512m | `apiMemoryLimit` 1024m (proposed; confirm on the host) | `infra/environments/<env>.json` |
| AWS resources | Test instance, bucket, distribution, ECR repository, roles | A separate set of each | `infra/environments/<env>.json` |
| Frontend domain | Default CloudFront domain | Custom domain with an ACM certificate in us-east-1 (required) | CloudFormation parameters |
| Parameter Store | `/air-concepts/test/` | `/air-concepts/live/`, read by the Live host role only | `ssmPrefix` |
| Supabase | Test project, synthetic data, auto-migrated | Production project, real users, backups, custom SMTP, access-token hook | `supabaseProjectRef` plus the runbook |
| Xero | Test credentials and encryption key | Separate Xero app, redirect URI and a **new** token-encryption key | Parameter Store |
| Xero request diagnostics | May be enabled | Always ignored by the deploy script | `allowTestOnlyFeatures` |
| CORS and public frontend values | Test URLs | Live URLs | Parameter Store |
| Deploy role | existing Test role (outside this repository) | `airconcepts-live-deployer`, trusts only `deploy-live.yml` on `main` | `deployer-role.json` |
| Stack protection | none | termination protection and `live-stack-policy.json` | AWS CLI step in the runbook |
| Rollback | A new merge | Recorded last-known-good release, same workflow in `rollback` mode | SSM `release/*` records |
| Monitoring | status-check alarm | status-check alarm, budget alert, external uptime check, manual headroom checks | runbook |

## Making a change

| I want to... | Edit |
|---|---|
| Resize the Live container | `apiMemoryLimit` in `live.json` (and the instance type in `live.local.json` if the host needs to grow) |
| Point Live at another hostname | `apiHostname` / `frontendUrl` in `live.json`, the CloudFormation parameters, and the frontend and CORS parameters in Parameter Store |
| Add an environment | Copy a config file, give it its own stack, prefix, role and Supabase project, add the name to `ENVIRONMENTS` in `environment_config.py` and to `setup-parameters.py`, and a workflow that calls the shared actions |
| Check a file against AWS | `python infra/scripts/environment_config.py from-stack <env> --profile <profile> --check` |
| Check both files | `python infra/scripts/environment_config.py validate` (also runs in CI) |

## What the checks guarantee

- `test.json` and `live.json` have the same keys and share no instance, bucket, distribution, role, repository, stack, parameter prefix, hostname or Supabase project.
- A Live deploy refuses to start while any value is still `REPLACE_ME`.
- The AWS identity is confirmed (account and role name) before a deploy touches anything.
- The Live host script refuses to deploy if Parameter Store names a different Supabase project than `live.json`.
- The backend refuses to start if its Supabase Auth, JWKS or database settings point at any project other than the one it was configured for, so a Test token never works on Live.
