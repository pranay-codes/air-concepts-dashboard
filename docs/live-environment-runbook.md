# Live environment runbook (INF-006)

Written for the operator who sets Live up by hand and then releases to it. Nothing here is run by an agent. Every step says how to check it worked. Commands are PowerShell, from the repository root. Replace `<...>` values. Never paste a password, key or token into Git, a chat, a ticket or a file in this repository.

Related: [what differs between Test and Live](environments.md), [ADR-004](adr/ADR-004-live-release-workflow.md), [infrastructure guide](../infra/README.md), [security access](security-access.md), [auth operations](uc5-auth-operations.md).

## 0. Before you start

- [ ] **Approvals recorded:** production creation and release approval, a named operator, an incident contact, and the monthly budget. Real client data is not loaded until the REL-001 client and data-handling approval is carried forward.
- [ ] **Test is healthy.** Live promotes what Test proved. Check the latest `deploy-test` run is green and the Test site works.
- [ ] **The three code changes are merged and have run on Test**, in this order: the environment-neutral backend and deploy script, the shared actions, then the Live files. Confirm on Test that a deploy after the first change is green. Live can only receive an image built after the first change, because only that image has the neutral startup guard.
- [ ] **Tools:** AWS CLI v2, Python 3.12+, Git, access to the Supabase and GitHub dashboards, a password manager, and an AWS profile with administrative rights for Live (short-lived). Install the script dependencies once: `python -m pip install -r infra/scripts/requirements.txt`.
- [ ] **Safety habit:** every AWS command below names `--profile <live-admin-profile>`. Before the first one, run `aws sts get-caller-identity --profile <live-admin-profile>` and confirm the account is `923079246426`.

Order of work. Do not skip ahead; later steps need earlier outputs.

| Phase | What | Needs |
|---|---|---|
| 1 | Supabase production project | nothing |
| 2 | AWS: certificate, stack, protection, role | phase 1 values |
| 3 | DNS | phase 2 outputs |
| 4 | Parameter Store values | phases 1 to 3 |
| 5 | GitHub environment and secrets | phase 1 |
| 6 | First release | everything above |
| 7 | Supabase Auth hook, first Director, backups proof | first release |
| 8 | Verification and handover | first release |

## 1. Supabase production project

### 1.1 Create the project
1. Supabase dashboard, **New project**, in your organisation. Name it `air-concepts-production`. Region: Sydney (`ap-southeast-2`). Plan: **Pro or higher**, because the Free plan has no backups.
2. Generate a long random database password (at least 32 characters, letters and digits) in your password manager. Store it there only.
3. Record, in `infra/environments/live.json` later, the **project reference** (the 20-character id in the project URL and in Project Settings, General) and the project URL `https://<ref>.supabase.co`. These are not secrets. The password is.

Verify: the dashboard shows the project as healthy and the region is Sydney.

### 1.2 Check the signing keys
The backend accepts only ES256 or RS256 tokens. Open `https://<ref>.supabase.co/auth/v1/.well-known/jwks.json` in a browser. It must list at least one key (`"alg":"ES256"` or `"RS256"`). If the list is empty, the project still signs with the legacy shared secret: in Project Settings, JWT Keys, switch to asymmetric signing keys, then check the URL again. A project left on the legacy secret would reject every request.

### 1.3 Connection settings
1. In the dashboard choose **Connect**, then **Session pooler**. Note the host (it looks like `aws-0-ap-southeast-2.pooler.supabase.com`) and port **5432**.
2. The values you will store in Parameter Store (phase 4) are:

| Parameter | Value |
|---|---|
| `backend/db/jdbc-url` | `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require` |
| `backend/db/username` | `postgres.<ref>` |
| `backend/db/password` | the password from 1.1 |
| `backend/supabase/project-ref` | `<ref>` |
| `backend/supabase/jwt-issuer-uri` | `https://<ref>.supabase.co/auth/v1` |
| `backend/supabase/jwks-uri` | `https://<ref>.supabase.co/auth/v1/.well-known/jwks.json` |

Use the **session** pooler (the Live host has an IPv4 address only, so the IPv6-only direct host does not work) and not the transaction pooler on port 6543 (prepared statements break). Never put the password in the URL.
3. Project Settings, Database, **SSL Configuration**: turn on **Enforce SSL**.
4. Do **not** enable network restrictions. The migration step runs from GitHub-hosted runners whose addresses change, so an allow-list would block releases. Enforced SSL, the random password, row-level security and a Live-only token are the controls (ADR-004).
5. Project Settings, API: copy the **publishable (anon) key**. It is public by design and goes in the frontend parameter in phase 4. The `service_role` key is a secret: it is only needed if you use staff administration (`backend/supabase/service-role-key`). Store it only in Parameter Store as a SecureString.
6. Project Settings, Access Tokens (your account menu): create a personal access token named `air-concepts-live-deploy`. This is for the Live workflow only. Do not reuse the Test token.

### 1.4 Backups and recovery proof
1. Project Settings, Database, **Backups**: confirm daily backups are on and note the retention. Add **Point in Time Recovery** if the plan allows (recommended): it lets you restore to a second.
2. Write down the expected recovery window: with daily backups you can lose up to 24 hours of data; with PITR, about two minutes. Record it in your incident notes.
3. **Prove a restore** into an isolated target before real data is loaded: use the dashboard's restore to a **new project** (or download a backup and restore it into a scratch Supabase project with `psql`). Confirm the tables and policies exist in the scratch project, then delete the scratch project. Record the date and who did it.

### 1.5 Auth settings (do these now, except the hook)
Follow [uc5-auth-operations.md](uc5-auth-operations.md), steps 2 and 3, on the production project, and read each setting back:

- [ ] Public signup disabled.
- [ ] Email OTP: length 6, expiry 600 seconds, email request cooldown 60 seconds, verification limit 10 per five minutes per IP.
- [ ] **Custom SMTP** configured (Supabase's built-in mailer is heavily rate limited and not for production). At your email provider, add the SPF, DKIM and DMARC DNS records they give you for the sender domain (phase 3).
- [ ] Email template for sign-in set to the code-only template in `supabase/templates/magic_link.html`.
- [ ] Site URL = `https://<live-frontend-hostname>`, and redirect URLs list only that origin (the app signs in with a code and uses no redirect, but keep the list minimal).

The **Custom Access Token hook** is enabled in phase 7, after the first release has created its function.

Do **not** copy Test users, passwords or synthetic seed data into production. Create only the authorised users.

## 2. AWS

### 2.1 Account basics
- Account MFA, a budget and the budget alert email already exist for the shared account. Confirm a budget covers Live's expected spend: Billing, Budgets. Add one named `air-concepts-live` if the monthly amount differs, with an email alert at 80% and 100%.
- Choose the network: the approved VPC and a **public subnet with a route to an internet gateway**. A shared VPC does not mean shared application resources; Live gets its own instance, security group, role, repository, bucket, distribution and Elastic IP from the template.
- Choose the AMI: the same tested Amazon Linux 2023 **ARM64** AMI id that Test uses is a safe default (`ami-...`, in `ap-southeast-2`). To look up the current one: `aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 --query Parameter.Value --output text --region ap-southeast-2 --profile <live-admin-profile>`. Record the id. The template never looks up "latest" for you.

### 2.2 Certificate for the frontend (must be in us-east-1)
1. ACM in region **us-east-1** (CloudFront only reads certificates there): request a public certificate for `<live-frontend-hostname>` with DNS validation.
2. Add the validation CNAME record ACM shows at your DNS provider (phase 3).
3. Wait for status **Issued**, then record its ARN.

Verify: `aws acm describe-certificate --certificate-arn <arn> --region us-east-1 --query Certificate.Status --output text --profile <live-admin-profile>` prints `ISSUED`.

### 2.3 Live parameter file
```powershell
Copy-Item infra/params/live.example.json infra/params/live.local.json
```
Edit `infra/params/live.local.json` (ignored by Git) and replace every `REPLACE_ME`:

| Key | Value |
|---|---|
| `ExpectedAccountId` | `923079246426` |
| `VpcId`, `PublicSubnetId` | the approved Live network, never Test's identifiers copied |
| `ApiHostname` | for example `api.<your-domain>` |
| `AmiId` | from 2.1 |
| `FrontendHostname`, `FrontendCertificateArn` | from 2.2 |
| `AlarmEmail` | the incident contact's address |
| `InstanceType`, `RootVolumeSize` | leave `environment-default` and `0` (Live: `t4g.small`, 20 GiB) unless measured otherwise |
| `ExistingRepositoryName` | `air-concepts-test-backend` (the shared repository, ADR-004 amendment). Check it exists first: `aws ecr describe-repositories --repository-names air-concepts-test-backend --region ap-southeast-2 --profile <profile>`. Leave it blank only if that returns `RepositoryNotFoundException`, and then the stack creates the repository. |

### 2.4 Create the stack (review before execute)
```powershell
aws cloudformation validate-template --template-body file://infra/cloudformation/infrastructure.json --region ap-southeast-2 --profile <live-admin-profile>
aws cloudformation create-change-set --stack-name air-concepts-live-infrastructure --change-set-name live-initial --change-set-type CREATE --template-body file://infra/cloudformation/infrastructure.json --parameters file://infra/params/live.local.json --capabilities CAPABILITY_IAM --tags Key=client,Value=air-concepts Key=environment,Value=live --region ap-southeast-2 --profile <live-admin-profile>
aws cloudformation describe-change-set --stack-name air-concepts-live-infrastructure --change-set-name live-initial --region ap-southeast-2 --profile <live-admin-profile>
```
Review the change set. It must show only **Add** actions, every name and tag must say `live`, the instance type must be `t4g.small`, and there must be nothing that refers to `test`. Stop if anything looks like Test. Then execute (this starts billing):
```powershell
aws cloudformation execute-change-set --change-set-name live-initial --stack-name air-concepts-live-infrastructure --region ap-southeast-2 --profile <live-admin-profile>
aws cloudformation wait stack-create-complete --stack-name air-concepts-live-infrastructure --region ap-southeast-2 --profile <live-admin-profile>
```
Creation waits up to 20 minutes for the host to bootstrap. If it fails, read the stack events, fix the cause, delete the failed stack and start again; do not edit the host by hand.

### 2.5 Protect the stack
```powershell
aws cloudformation update-termination-protection --enable-termination-protection --stack-name air-concepts-live-infrastructure --region ap-southeast-2 --profile <live-admin-profile>
aws cloudformation set-stack-policy --stack-name air-concepts-live-infrastructure --stack-policy-body file://infra/cloudformation/live-stack-policy.json --region ap-southeast-2 --profile <live-admin-profile>
```
Verify: `aws cloudformation describe-stacks --stack-name air-concepts-live-infrastructure --query "Stacks[0].EnableTerminationProtection" --region ap-southeast-2 --profile <live-admin-profile>` prints `true`. Never delete this stack as a rollback shortcut.

### 2.6 Record the outputs in the Live configuration
```powershell
python infra/scripts/environment_config.py from-stack live --profile <live-admin-profile>
```
Copy the printed values into `infra/environments/live.json`: `instanceId`, `frontendBucket`, `cloudFrontDistributionId` and `ecrRepositoryUri`. Then set `frontendUrl` to `https://<live-frontend-hostname>` (your custom domain, not the CloudFront address the stack prints), `apiHostname` to `<ApiHostname>` and `supabaseProjectRef` to `<ref>`. Check it:
```powershell
python infra/scripts/environment_config.py validate
python infra/scripts/environment_config.py export live --require-complete
```
The second command must print values and no `REPLACE_ME`. Commit this file through a pull request. The release workflow runs from the commit you release, so this file must be on `main` first.

### 2.7 Verify the host and the private bucket
- Instance: `aws ec2 describe-instances --instance-ids <instance-id> --query "Reservations[0].Instances[0].[InstanceType,State.Name,MetadataOptions.HttpTokens,Tags]" --profile <live-admin-profile> --region ap-southeast-2` shows `t4g.small`, `running`, `required`, and a tag `environment=live`.
- Storage encrypted and 20 GiB: `aws ec2 describe-volumes --filters Name=attachment.instance-id,Values=<instance-id> --query "Volumes[].[Size,Encrypted]" --profile <live-admin-profile> --region ap-southeast-2`.
- SSM reachable: `aws ssm describe-instance-information --filters Key=InstanceIds,Values=<instance-id> --query "InstanceInformationList[0].PingStatus" --profile <live-admin-profile> --region ap-southeast-2` prints `Online`.
- Docker and Compose: `aws ssm start-session --target <instance-id> --profile <live-admin-profile> --region ap-southeast-2`, then `docker --version`, `docker compose version`, `exit`. (Needs the Session Manager plugin. No SSH is ever used.)
- Security group: only ports 80 and 443 inbound, nothing else (`aws ec2 describe-security-groups --group-ids <sg-id> ...`).
- Bucket is private: `aws s3api get-public-access-block --bucket <bucket> --profile <live-admin-profile>` shows all four settings `true`, and `https://<bucket>.s3.ap-southeast-2.amazonaws.com/index.html` returns 403.

### 2.8 Deploy role (second stack)
Find the account's GitHub OIDC provider ARN (it already exists for Test): `aws iam list-open-id-connect-providers --profile <live-admin-profile>`, the one ending `token.actions.githubusercontent.com`. Then:
```powershell
aws cloudformation create-stack --stack-name air-concepts-live-deployer --template-body file://infra/cloudformation/deployer-role.json --capabilities CAPABILITY_NAMED_IAM --region ap-southeast-2 --profile <live-admin-profile> --parameters ParameterKey=ExpectedAccountId,ParameterValue=923079246426 ParameterKey=GitHubOidcProviderArn,ParameterValue=<oidc-provider-arn> ParameterKey=GitHubRepository,ParameterValue=<owner>/<repo> ParameterKey=GitHubSubjectPrefix,ParameterValue=<subject-prefix> ParameterKey=LiveInstanceId,ParameterValue=<instance-id> ParameterKey=LiveFrontendBucketName,ParameterValue=<bucket> ParameterKey=LiveDistributionId,ParameterValue=<distribution-id>
aws cloudformation wait stack-create-complete --stack-name air-concepts-live-deployer --region ap-southeast-2 --profile <live-admin-profile>
aws cloudformation describe-stacks --stack-name air-concepts-live-deployer --query "Stacks[0].Outputs" --region ap-southeast-2 --profile <live-admin-profile>
```
Verify: the `DeployRoleArn` output equals `deployRoleArn` in `live.json` (`arn:aws:iam::923079246426:role/airconcepts-live-deployer`).

### 2.8a Shared image repository (rules)
Test and Live use `air-concepts-test-backend` (ADR-004 amendment, 13 Nov 2026). Nothing may remove or recreate it.
1. **Exact name first.** `aws ecr describe-repositories --repository-names air-concepts-test-backend --region ap-southeast-2 --profile <profile>`. If it exists, set `ExistingRepositoryName=air-concepts-test-backend` in the Live stack. Create a repository only if this returns `RepositoryNotFoundException`.
2. **Live stack that already created `air-concepts-live-backend`:** update it in two steps, never in one. (a) Update with the new `infrastructure.json` and `ExistingRepositoryName` still blank, so the repository is retained. (b) Update with `ExistingRepositoryName=air-concepts-test-backend`. The old Live repository is then orphaned, not deleted. Delete it by hand only after review (`aws ecr delete-repository` without `--force` refuses a non-empty repository).
3. **Test deployer role (not in this repository).** Add an inline Deny so the Test role can push but not change or delete the shared repository:
   ```
   aws iam put-role-policy --role-name airconcepts-test-deployer --policy-name DenySharedRepositoryAdmin --policy-document file://<path-to-json> --profile <test-profile>
   ```
   Policy document:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Sid": "NeverChangeTheSharedRepository",
       "Effect": "Deny",
       "Action": ["ecr:BatchDeleteImage", "ecr:DeleteLifecyclePolicy", "ecr:DeleteRepository", "ecr:DeleteRepositoryPolicy", "ecr:PutImageScanningConfiguration", "ecr:PutImageTagMutability", "ecr:PutLifecyclePolicy", "ecr:PutReplicationConfiguration", "ecr:SetRepositoryPolicy"],
       "Resource": "arn:aws:ecr:ap-southeast-2:923079246426:repository/air-concepts-test-backend"
     }]
   }
   ```
4. **Account-level guard (human, optional):** an organisation SCP denying `ecr:DeleteRepository` for everyone except a break-glass role. This repository's stack policy does not cover account administrators.

### 2.9 Alarm email
Open the confirmation email AWS SNS sent to `AlarmEmail` and confirm the subscription. Until you do, alarms do not reach anyone. Verify: SNS console shows the subscription as **Confirmed**.

## 3. DNS

Do these in order. Records are created at your DNS provider (they are not managed by the stack unless you set `ApiHostedZoneId`).

1. **Certificate validation:** the ACM CNAME from 2.2 (needed before 2.4).
2. **API hostname:** an **A** record from `<ApiHostname>` to the `ApiElasticIp` stack output. Do this **before the first release**, because Caddy asks for its HTTPS certificate on first start and needs the name to resolve to the host and port 80 to be reachable. Do not add an AAAA record. Verify: `nslookup <ApiHostname>` returns the Elastic IP.
3. **Frontend hostname:** a **CNAME** from `<FrontendHostname>` to the `CloudFrontDomain` output (or a Route 53 alias). Verify: `nslookup <FrontendHostname>` resolves to CloudFront.
4. **Email sender domain:** the SPF, DKIM and DMARC records from your SMTP provider (1.5).

Keep TTLs short (300 seconds) until Live is verified.

## 4. Parameter Store values

All values go under `/air-concepts/live/`. The script writes only that prefix and refuses any name that mentions Test. Secret values are hidden as you type and never printed.
```powershell
# 1. Dry run: lists every key and whether it is set. Writes nothing.
python infra/scripts/setup-parameters.py --environment live --profile <live-admin-profile> --expected-account-id 923079246426
# 2. Enter values. Blank skips a key.
python infra/scripts/setup-parameters.py --environment live --profile <live-admin-profile> --expected-account-id 923079246426 --apply
```
Values:

| Key | Value |
|---|---|
| `backend/db/*`, `backend/supabase/project-ref`, `jwt-issuer-uri`, `jwks-uri` | from 1.3 |
| `backend/supabase/service-role-key` | the production `service_role` key (SecureString), only if staff administration is used |
| `backend/cors/allowed-origins` | `https://<live-frontend-hostname>` (scheme and host only, no path) |
| `backend/xero/client-id`, `client-secret` | from the **Live** Xero app (a separate app from Test) |
| `backend/xero/redirect-uri` | `https://<api-hostname>/api/v1/xero/callback`, and register the same URI in the Xero developer portal |
| `backend/xero/frontend-callback-uri` | the Live frontend URL |
| `backend/xero/token-encryption-key` | a **new** key: `python -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())"` (never reuse Test's) |
| `frontend/api-base-url` | `https://<api-hostname>` |
| `frontend/supabase-url` | `https://<ref>.supabase.co` |
| `frontend/supabase-publishable-key` | the publishable key from 1.3 |

Do not set `release/*` (the workflow manages those) or the Test-only `diagnostics/*` keys.

Verify (names and types only, SecureStrings are never printed): `python infra/scripts/list-parameters.py --profile <live-admin-profile> --expected-account-id 923079246426 --environment live`. Verify isolation from the host (via Session Manager): `aws ssm get-parameter --name /air-concepts/test/backend/db/jdbc-url --region ap-southeast-2` must be **denied**.

## 5. GitHub

1. **Settings, Environments, New environment** named `live`. If your plan offers it, add **required reviewers** and limit deployment branches to `main`. On the Free plan these are unavailable: the typed confirmation and the AWS trust conditions are the gates (ADR-001, ADR-004), so keep write access to the repository small.
2. **Settings, Secrets and variables, Actions, New repository secret:**
   - `SUPABASE_LIVE_ACCESS_TOKEN`: the token from 1.3.6.
   - `SUPABASE_LIVE_DB_PASSWORD`: the password from 1.1.
3. Confirm the three code changes are on `main` and Test deployed them (section 0). The `deploy-live` workflow only appears in the Actions tab once it is on the default branch.
4. Check `infra/environments/live.json` on `main` has no `REPLACE_ME`.
5. After a Test deploy succeeds with the new workflow, delete the old Test variables (`TEST_DEPLOY_ROLE_ARN`, `ECR_REPOSITORY_URI`, `TEST_INSTANCE_ID`, `TEST_API_HOSTNAME`, `TEST_FRONTEND_URL`, `TEST_BUCKET`, `TEST_DISTRIBUTION_ID`). Before that first Test deploy, check `infra/environments/test.json` against the Test stack: `python infra/scripts/environment_config.py from-stack test --profile <test-profile> --check`, and confirm `deployRoleArn` is the role your `TEST_DEPLOY_ROLE_ARN` variable holds.

## 6. First release

### 6.1 Pick the release
1. Merge the commit to `main` and let **deploy-test** run. Open that run's **Summary**: it prints the `Commit:` and `Image:` lines for the Test deploy. Check that **supabase-migrations** also succeeded on that commit.
2. Create the release tag `v0.1` on that commit, with release notes that contain these lines (the first release is [v0.1](https://github.com/pranay-codes/air-concepts-dashboard/releases/tag/v0.1) once created):
   ```text
   Tag: v0.1
   Commit: <full 40-character commit, from the Summary>
   Image: <image tag, from the Summary>
   ```
   The release workflow reads these lines and refuses the release unless they match the tag's commit and the image Test pushed for it. Do not copy a digest; the workflow finds it. Start the notes from [docs/release-notes-template.md](release-notes-template.md) and add a short summary of what is deployed.
3. Confirm a recovery point: Supabase, Database, Backups shows a recent backup or PITR is on (for the very first release the database is empty).

### 6.2 Run it
Actions, **deploy-live**, **Run workflow**, branch `main`:

| Input | Value |
|---|---|
| mode | `release` |
| release_tag | `v0.1` (or the release URL from 6.1) |
| confirm | `DEPLOY LIVE` |
| backup_confirmed | ticked |
| destructive_ack | ticked **for the first release only** (see below) |

The first release applies all existing migrations to an empty database. Two of them contain `alter table ... drop constraint` (`20261106050000_uc5_staff_and_dynamic_entities.sql` and `20261111010000_uc4_generated_forecast_months.sql`). Each drops a constraint that the same migration immediately replaces, which is harmless on an empty database. The workflow flags them and stops unless `destructive_ack` is ticked. Read the "FLAGGED" lines in the job log (step "Stop on destructive migration statements") before you tick it; for later releases, leave it off unless you reviewed new flagged statements.

If you need to look first, run once without `destructive_ack`: the workflow stops at that step after listing the migrations, and nothing has changed in the database.

### 6.3 What to watch
The job runs, in order: confirm the target is the Live instance, check the image came from Test, copy it to the Live repository and verify the digest, list pending migrations, stop on destructive statements, apply migrations and run the read-only schema tests, record the in-flight release, deploy the backend (waits for health), build and publish the frontend, smoke test (including a 401 for an unauthenticated call), record the release. A failed step stops the run; the host restores the previous backend by itself if the new one is not healthy.

First-run checks (these confirm assumptions that could not be tested offline, ADR-004):
- **Role assumed?** If "Assume the environment's deploy role" fails with `Not authorized to perform sts:AssumeRoleWithWebIdentity`, IAM did not accept a trust condition. Check the stack's `GitHubSubjectPrefix` against `gh api repos/<owner>/<repo>/actions/oidc/customization/sub`. If the repository uses immutable subjects, the prefix must be `repo:<owner>@<owner-id>/<repo>@<repo-id>` (the `sub_claim_prefix` value), not `repo:<owner>/<repo>`. Then check `GitHubRepository`, the `live` environment name and `job_workflow_ref` against the trust policy. Fix the stack parameter with a stack update (`UsePreviousValue` for the others). Do not loosen the trust to a wildcard.
- **Digest identical after the copy?** The step "Promote the tested image" fails with `the Live copy has digest ... not ...` if copying changed the digest. Nothing is deployed in that case. Report it; do not bypass it.
- **Dry run readable?** If the scanner reports `REFUSED: the dry run names migrations that have no file`, the CLI output format differs; keep the run stopped and report it.

## 7. After the first release

1. **Enable the access-token hook:** Supabase, Authentication, Hooks, **Custom Access Token**, choose the Postgres function `public.uc5_access_token_hook` (it exists now). Without it, deactivated staff are only blocked by the backend, not at sign-in.
2. **Create the first Director:** in Authentication, Users, add the Director with the confirmed email, copy the user id, then have a reviewer check and run `infra/scripts/bootstrap-first-director-sql-editor.sql` (it is marked DRAFT and Test-only in its header; it needs a reviewed Live run before use) in the Live SQL editor. It refuses if an active Director already exists and shows no email.
3. Verify sign-in with that account, and that a user who is not registered cannot sign in. Check expired and replayed codes are rejected (the checks in [uc5-auth-operations.md](uc5-auth-operations.md), step 5, on Live).
4. Repeat the backup restore rehearsal in 1.4 now that tables exist, if you proved it earlier on an empty project.

## 8. Verification and handover checklist

| Check | How |
|---|---|
| Site over trusted HTTPS | Open `https://<frontend-hostname>`; the padlock is valid; a deep link such as `/?xeroAttempt=x` loads the app |
| API over trusted HTTPS | `curl https://<api-hostname>/actuator/health` returns `{"status":"UP"}` |
| Login and protected API | Sign in as the Director; the app loads data (it calls `/api/v1/me`) |
| Production database record | Create one harmless controlled record through the app, confirm it in Supabase, then remove it |
| Unauthenticated rejected | `curl -i https://<api-hostname>/api/v1/me` returns 401 |
| Cross-user access fails | Sign in as a user without access to an entity; the entity's data is refused |
| Test token rejected | Sign in on Test, copy its access token from the browser, `curl -H "Authorization: Bearer <test-token>" https://<api-hostname>/api/v1/me` returns 401 |
| Private S3 | Direct bucket URL returns 403; the site works through CloudFront |
| Test still works | The Test site and a Test deploy are still green |
| Host health | Session Manager: `free -h`, `df -h`, `docker stats --no-stream` show comfortable headroom; CloudWatch `CPUCreditBalance` for the instance is not trending to zero |
| Logs rotate | `docker inspect` of the containers shows the `json-file` driver with `max-size 10m`, `max-file 3` |
| Alerts reach people | Confirm the SNS subscription (2.9); optionally force a test notification from the console |
| Budget alert | The budget exists and its notification email is confirmed |
| Uptime monitor | Add a free external uptime check (for example a free-tier monitor) on `https://<api-hostname>/actuator/health` (keyword `UP`) and on the frontend URL, alerting the incident contact; record who owns it |
| Recorded release | `aws ssm get-parameter --name /air-concepts/live/release/current --query Parameter.Value --output text --profile <live-admin-profile> --region ap-southeast-2` shows the release tag, commit and digest that were released |

Save these non-secret facts where the team can find them: stack names and outputs, hostnames, the release workflow link, the incident contact, the recovery window, and the steps in sections 9 and 10.

## 9. Routine release

1. Merge to `main`; Test deploys automatically. Verify Test.
2. Pick the release tag as in 6.1.
3. Run `deploy-live` with `mode release`. Leave `destructive_ack` off unless you reviewed new flagged migration statements.
4. Watch the run, then repeat the section 8 checks that matter for what changed.
5. Keep migrations backward compatible (add first, remove in a later release) so a rollback still works.

## 10. Rollback and recovery

**A release failed or Live is unhealthy:**
1. Actions, **deploy-live**, Run workflow, `mode rollback`, `confirm DEPLOY LIVE`.
2. The workflow restores the last known-good backend digest on the host, restores the matching frontend files and invalidates CloudFront, then smoke tests. If a release was started but not finished, it restores the release before it; otherwise it restores the previous release.
3. The database is never changed by a rollback. If the undone release changed the schema in a way the restored release cannot use, stop: write a reviewed forward fix as a new migration, or use a separately approved restore (below). Never reset or drop production data.

**Rehearse it** with a harmless release (for example a comment-only commit that has been through Test): release it, roll back, check the site, then release again. Record the date and result.

**Database restore (separate approval):** Supabase, Database, Backups, restore to a new project (or PITR to a chosen second). Update `infra/environments/live.json`, the `/air-concepts/live/` Supabase values and the GitHub secrets to the restored project only after the restore is verified, and expect to lose data written after the recovery point.

**Infrastructure failure:** review the failed stack events and a corrective change set. Do not delete the stack. A rebuilt host needs the image (kept in the Live repository), its Parameter Store values, the Elastic IP association and a new Caddy certificate (automatic once DNS points at the host).

**Rotating secrets:** change the Supabase password (dashboard), update `backend/db/password` in Parameter Store and `SUPABASE_LIVE_DB_PASSWORD` in GitHub, then run a release or `rollback` to restart the backend with the new value. Replace the Supabase access token in GitHub when anyone with repository write access leaves.

## 11. Known limits

- No enforced approver on GitHub Free: the typed confirmation, the `main`-only workflow check and the AWS trust conditions are the gates. Live secrets in GitHub are repository secrets.
- Memory and disk alarms, automatic host recovery and EBS backup scheduling are not provided (INF-005). The host is rebuildable from the template, Parameter Store and the retained image.
- Single host: a replacement or reboot causes downtime. This is not highly available.
- Xero's nightly forecast sync is on by default and starts once an organisation is connected. Decide that deliberately before connecting a Live Xero organisation.
