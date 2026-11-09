# Infrastructure

This folder holds the INF-002 shared Test/Live CloudFormation blueprint, Docker/Caddy configuration and the existing Test Parameter Store setup script. Provisioning and application release require separate human-controlled steps.

Planned subfolders:
- `cloudformation/`: AWS stack templates
- `params/`: example parameter files with `REPLACE_ME` values. Real `*.local.json` files are never committed.
- `deploy/`: Docker Compose, Caddy and remote deploy files

Humans deploy infrastructure with their own AWS identity. Agents write files only and never run commands that contact AWS.

Sources: [INF-002](https://app.notion.com/p/40b5eb9488648130a4aed9283bc50ca5) and [Infrastructure Architecture](https://app.notion.com/p/40a5eb94886481408eb6dc4e1647c34c), whose current revision read on 5 November 2026 updates Test sizing and preserves Live sizing. Implementation plan: [INF-002](../docs/plans/INF-002.md).

## Shared CloudFormation blueprint

Use `cloudformation/infrastructure.json` for either environment. JSON is a native CloudFormation template format. Two separate stacks create separate EC2 hosts, roles, EIPs, S3 buckets, distributions and alarms. This creates no VPC, database, Auth project, secrets, GitHub role, load balancer or NAT gateway.

| Configuration | Test default | Live default |
|---|---|---|
| Example parameter file | `params/test.example.json` | `params/live.example.json` |
| Stack name | `air-concepts-test-infrastructure` | `air-concepts-live-infrastructure` |
| Environment | `test` | `live` |
| Instance | `t4g.micro` | `t4g.small` |
| Allowed override | `t4g.small` | `t4g.medium` |
| Root gp3 storage | 10 GiB, encrypted | 20 GiB, encrypted |
| Backend parameter path | `/air-concepts/test/backend/` | `/air-concepts/live/backend/` |
| Frontend DNS | Default CloudFront domain | Custom domain + issued ACM certificate |

`InstanceType=environment-default` and `RootVolumeSize=0` select the row's defaults. Override size with a supported ARM64 type; override storage with 10/20/30/40/60/80/100 GiB (Live minimum 20). Changing only `Environment` selects the right size, but **never change an existing stack's environment**: create a separate stack with its own reviewed configuration.

Copy an example to `params/test.local.json` or `params/live.local.json` (ignored by Git). Replace all `REPLACE_ME` values. The recorded Test VPC, subnet and hostname come from the task; their AWS state has not been verified here. Never copy Test identifiers or secrets into Live. Live examples deliberately contain placeholders.

Other configurable fields:

- `ExpectedAccountId`: required non-secret account guard. CloudFormation rejects a different account or a region other than Sydney. A shared account is supported; role/path separation still applies.
- `VpcId`, `PublicSubnetId`: existing network. The template checks that every subnet parameter belongs to `VpcId`; the human verifies the Internet Gateway route, public DNS and internet reachability.
- `ApiHostname`, `ApiHostedZoneId`: API name and optional existing public Route 53 zone. Leave zone blank for external DNS; create an A record to output `ApiElasticIp`. Do not publish an AAAA record for this IPv4-only API.
- `AmiId`: a **pinned AMI ID** (`ami-...`) for AL2023 ARM64 in `ap-southeast-2`. There is no default; the template never looks up a "latest" image. Choose and test an AMI before first use, record it in the environment's parameter file, and treat any change as a reviewed update, because it replaces the instance. A non-ARM64 AMI is unsupported and bootstrap rejects it.
- `ExistingRepositoryName`: optional ECR repository in the same account/region, avoiding duplicate creation. The human verifies immutability, scanning, retention and pull access. Live may pull the approved Test image digest from this repository; never rebuild for promotion.
- `SsmKmsKeyArn`: optional customer-managed key for this environment. Decrypt is restricted to SSM and the environment's backend parameter encryption context. Without it, use AWS's default SSM key.
- `AlarmEmail`: optional SNS email. Confirm the subscription to receive status alarms; blank creates the alarm without email delivery.
- `FrontendHostname`, `FrontendCertificateArn`: supply both together. Certificate must be issued in `us-east-1` and cover the hostname. The template requires these for Live. Create frontend DNS externally as a CNAME or Route 53 alias to `CloudFrontDomain`.
- `CloudFrontPriceClass`: `PriceClass_All` includes Australia/NZ edge locations. Restrict only after reviewing latency and cost. Frontend HTML is not edge-cached initially, preventing stale releases; CI-002 may add caching specifically for hashed assets.
- `CpuCredits`: `standard` avoids surplus-credit charges; `unlimited` needs cost review. `ComposeVersion` pins the ARM64 plugin release, verified against published checksums during bootstrap.

The instance uses IMDSv2 and a metadata hop limit of one. Secrets are fetched **on the host** using its IAM role, then privately injected by CI-002. Containers must not query instance metadata. The role denies reads outside its own backend path, including reads otherwise granted by `AmazonSSMManagedInstanceCore`. Only Caddy ports 80/443 are public; SSH, Java, Postgres and Docker TCP ports are closed. Egress is initially broad to support SSM, package updates, Supabase and external APIs; tighter monitored egress is follow-up work.

S3 has encryption, versioning, blocked public access and an OAC policy scoped to its own distribution. A CloudFront function rewrites extensionless application routes to `index.html`; missing asset files and access-denied errors retain their real status. Only tagged ECR images are rollback candidates: retain at least one tested release tag. Automatic cleanup expires untagged images after 14 days; tagged releases need reviewed periodic cleanup.

## Naming and tags

Every resource has a logical ID that starts with `AirConcepts`. The backend host is `AirConceptsBackend`, and its `Name` tag is `air-concepts-backend`. Every other `Name` tag follows `air-concepts-<environment>-<component>`, for example `air-concepts-test-api-sg`.

Every taggable resource carries the same tag set:

| Key | Value |
|---|---|
| `client` | `air-concepts` |
| `environment` | the `Environment` parameter (`test` or `live`) |
| `project` | `air-concepts` |
| `requirement` | `INF-002` |
| `Name` | as described above |

The stack is tagged with `client` and `environment` when the change set is created, through `--tags` on `create-change-set`. A template cannot set stack tags. The launch template's instance and volume specifications carry the same tags.

## Validation and human provisioning

Run these from the repository root in PowerShell. Create the project venv once:

```powershell
python -m venv .venv-infra
.\.venv-infra\Scripts\python.exe -m pip install -r infra/scripts/requirements-infrastructure.txt
.\.venv-infra\Scripts\python.exe -m pip install -r infra/scripts/requirements.txt
```

Then run the checks:

```powershell
.\.venv-infra\Scripts\python.exe -m unittest discover -s infra/scripts -p test_infrastructure.py -q
.\.venv-infra\Scripts\python.exe -m pytest infra/scripts -q
.\.venv-infra\Scripts\cfn-lint.exe infra/cloudformation/infrastructure.json --regions ap-southeast-2
```

Expected results: unittest reports `OK (skipped=1)`, pytest reports one skip, and cfn-lint exits 0. The skipped test checks the Compose secret handling and needs Compose 2.30 or newer, so it does not run locally. The Compose structure check runs in CI only (`.github/workflows/infrastructure.yml`), and the team does not run Docker locally. The first install needs package-network access.

The following commands are **for the human operator**, not agents. Run them from the repository root under a short-lived administrative Test profile. The first block creates a reviewable change set and does not execute it. Routine GitHub deploy roles must not gain IAM/CloudFormation administration.

```powershell
aws cloudformation validate-template --template-body file://infra/cloudformation/infrastructure.json --region ap-southeast-2 --profile <test-admin-profile>
aws cloudformation create-change-set --stack-name air-concepts-test-infrastructure --change-set-name <change-set-name> --change-set-type CREATE --template-body file://infra/cloudformation/infrastructure.json --parameters file://infra/params/test.local.json --capabilities CAPABILITY_IAM --tags Key=client,Value=air-concepts Key=environment,Value=test --region ap-southeast-2 --profile <test-admin-profile>
aws cloudformation describe-change-set --stack-name air-concepts-test-infrastructure --change-set-name <change-set-name> --region ap-southeast-2 --profile <test-admin-profile>
```

Change-set names must be unique per stack. Reusing a name with different tags fails with `AlreadyExistsException`. To list existing change sets, run `aws cloudformation list-change-sets --stack-name air-concepts-test-infrastructure --region ap-southeast-2 --profile <test-admin-profile>`. Delete a stale one with `aws cloudformation delete-change-set` before you reuse its name.

Run the second block only after you have reviewed the change set. It starts creation, which costs money:

```powershell
aws cloudformation execute-change-set --change-set-name <change-set-name> --stack-name air-concepts-test-infrastructure --region ap-southeast-2 --profile <test-admin-profile>
aws cloudformation wait stack-create-complete --stack-name air-concepts-test-infrastructure --region ap-southeast-2 --profile <test-admin-profile>
```

Review IAM, resource replacements, costs and the selected account before human execution. The stack waits up to 20 minutes for successful bootstrap. It installs Docker, checksum-verified Compose and automatic security updates without automatic reboots. No application is started. A temporary auto-assigned public IP provides bootstrap connectivity; the stable EIP is attached after bootstrap succeeds. Failed bootstrap fails creation (or times out if signalling is unavailable).

After human execution, read outputs with:

```powershell
aws cloudformation describe-stacks --stack-name air-concepts-test-infrastructure --query 'Stacks[0].Outputs' --region ap-southeast-2 --profile <test-admin-profile>
```

Give CI-002 `EcrRepositoryUri`, `ApiInstanceId`, `FrontendBucketName`, `CloudFrontDistributionId` and `ApiUrl`. Also record the EIP, role ARN, security group and CloudFront domain. Outputs are not evidence of deployed application health. Leave actual output values unrecorded until they are read from the created Test stack.

## Docker/Caddy release interface (CI-002)

Bootstrap creates `/opt/air-concepts` and `runtime/`, owned by root with mode 0700. CI-002 delivers the committed `deploy/compose.yaml` and `deploy/Caddyfile` there through SSM Run Command. No SSH/SCP or manual copies. Run Compose from that directory as the authorised host deploy identity.

Non-secret Compose configuration: `ENVIRONMENT` (`test` or `live`), `API_HOSTNAME`, `BACKEND_REPOSITORY_URI`, `BACKEND_IMAGE_DIGEST` and `CADDY_IMAGE_DIGEST`. Digests are 64 hex characters **without** `sha256:`. `API_MEMORY_LIMIT` defaults to `512m`; review `1024m` for Live after measurement. Images are pulled by digest, never `latest`. CI-002 must validate environment names and digest syntax before issuing commands.

The backend image interface is Java 25, `linux/arm64`, port 8080, UID/GID 10001, a writable `/tmp` only, `/bin/bash` for its HTTP health probe, and `/actuator/health` returning 200 when ready. The backend Dockerfile and release workflow do not exist yet. Verify the chosen Caddy digest supports ARM64, validate the Caddyfile in that image and smoke-test both containers before deployment. Caddy certificate/config volumes survive container recreation.

CI-002 must read the exact required parameter names from the selected backend path on the host, decrypt in memory, and write `runtime/backend.env` atomically with mode 0600 under the 0700 directory. Use `KEY=literal-value` lines without added quoting: Compose raw format preserves dollar signs and literal quotes. Reject CR/LF and NUL in values before serializing; never silently split or interpolate values. Map keys using [security-access.md](../docs/security-access.md). Refuse missing/empty required settings, wrong environment, invalid values and provider failures before changing containers. Never print decrypted values, enable shell tracing, run Compose config with real secrets in output, or place secrets in UserData, images or frontend variables. Preserve known-good runtime files until the replacement is validated. Remove temporary files on failure.

The current backend startup contract explicitly requires `SUPABASE_TEST_PROJECT_REF`; it was built for Test. **Live application readiness needs a separately reviewed environment-neutral startup guard and secret mapping.** Do not bypass the Test guard or rename Live values to look like Test in this infrastructure task. The shared AWS template supports Live resources; this is not an assertion that the current application is ready for Live.

Use profile `hosted`, not Spring profile `test`, because `test` disables the database health check. API has a 512 MiB container limit and conservative JVM limits; Caddy has 96 MiB. Both rotate JSON logs and restart unless stopped. Health checks gate initial proxy startup; CI-002 must also check health after release and restore the previous digest on failure. Docker does not automatically restart a merely unhealthy running container.

## Acceptance, cost and recovery gates

Before accepting Test: verify SSM access, AL2023 ARM64, IMDSv2, encryption, blocked ports, separate parameter permissions, private S3 and CloudFront access. Complete DNS and then verify trusted Caddy HTTPS after CI-002 releases the application. Confirm Supabase TLS, health checks and browser-to-API flow with synthetic Test data.

Measure `free -h`, `docker stats --no-stream`, `df -h`, OOM/restarts, heap, CPU credits and disk headroom under representative load. Micro capacity is unproven until this passes. Upgrade Test to `t4g.small` through a reviewed update if needed. The 10 GiB disk needs log/image cleanup and a free-space alarm (INF-005). Detailed memory/disk monitoring, automated recovery and Live EBS backup scheduling are not provided by this basic status alarm.

Before provisioning, obtain a current estimate for EC2, 10/20 GiB gp3, public IPv4/EIP, ECR, S3 versions/requests, CloudFront transfer, alarms, SNS, snapshots and any surplus credits. No paid detailed EC2 monitoring, NAT or load balancer is enabled. Budget alerts are prerequisite account configuration; they do not cap spend. No current price quote is recorded here.

Before updates, save outputs, pin the known-good image and review replacement flags. UserData updates do **not** rerun bootstrap on an existing instance; use a separately reviewed replacement/rebuild when changing bootstrap. Root disk deletes with the EC2 instance: snapshot required data first, especially Caddy TLS volumes. Never shrink EBS. Single-host replacement causes downtime; do not claim high availability.

Application rollback: CI-002 restores the previous image digest and protected runtime configuration, starts Compose, then checks health and HTTPS. Infrastructure rollback: review the failed stack events and a corrective change set; recreating a host needs the retained image, configuration, secret injection, EIP association and certificate recovery. Test this procedure before Live. Live also requires approved backups, restore rehearsal, monitoring and manual release controls.

Teardown requires human review. Policy: stack deletion removes every resource. All resources use `DeletionPolicy: Delete` and `UpdateReplacePolicy: Delete`, and the ECR repository uses `EmptyOnDelete`, so its images are removed with it.

**Known gap:** CloudFormation cannot empty the frontend S3 bucket. Stack deletion fails with `DELETE_FAILED` while the bucket still holds objects or versions. Before deleting the stack, empty the bucket, including all versions and delete markers, unless a reviewed custom resource is added to do this. Deleting the stack also releases the Elastic IP and terminates the EC2 instance, which deletes its root volume. Record DNS changes and remove stale DNS. Preserve required rollback images, backups and data first, because they are deleted with the stack.

AWS references: [rule assertions](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/rules-section-structure.html), [IMDS options](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html), [SSM managed policy](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonSSMManagedInstanceCore.html), [managed cache policy IDs](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-cache-policies.html), [security headers policy](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-response-headers-policies.html), [CloudFront certificates](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-properties-cloudfront-distribution-viewercertificate.html), [Compose installation](https://docs.docker.com/compose/install/linux/), [raw env-file format](https://docs.docker.com/reference/compose-file/services/#format).

## Test deploy workflow (CI-002)

`.github/workflows/deploy-test.yml` deploys to Test after a merge to `main`, and can also be started by hand. It only deploys when started from `main`. Four jobs run in order:

1. `build`: packages the jar and builds the `linux/arm64` backend image, then pushes it to ECR under the commit SHA.
2. `deploy-backend`: sends `infra/deploy/compose.yaml`, `Caddyfile` and `deploy-test.sh` to the Test host through SSM Run Command, then runs the script. No SSH.
3. `deploy-frontend`: builds the frontend, uploads it to the Test bucket and invalidates CloudFront.
4. `smoke`: checks the frontend URL and `https://<API hostname>/actuator/health`.

The script on the host reads the backend parameters under `/air-concepts/test/backend/`, writes `runtime/backend.env` with mode 0600, and starts Compose with the new digest. If the new container is not healthy within 180 seconds, it restores the previous digest and previous `backend.env`, then fails the job.

Repository variables, set by a human in GitHub. They are not secrets: `TEST_DEPLOY_ROLE_ARN`, `ECR_REPOSITORY_URI`, `TEST_INSTANCE_ID`, `TEST_API_HOSTNAME`, `TEST_FRONTEND_URL`, `TEST_BUCKET`, `TEST_DISTRIBUTION_ID`.

The Test deploy role needs: `ecr:GetAuthorizationToken` and push to the Test repository; `ssm:SendCommand` on the Test instance with `AWS-RunShellScript`; `ssm:GetCommandInvocation` (the wait uses it, and `ssm:ListCommandInvocations` may also be needed); `ssm:GetParametersByPath` on `/air-concepts/test/frontend/*`; S3 list, put and delete on the Test bucket; and `cloudfront:CreateInvalidation` on the Test distribution. A human checks these in AWS. The workflow does not verify them, and the first run confirms them.

The `Caddy` digest is pinned in the workflow, and the Temurin digest is pinned in `backend/Dockerfile`. The build job checks that both include `linux/arm64` before it pushes.

Manual rollback is not yet decided. Re-running an older workflow run rebuilds that commit and pushes its SHA tag again, which fails with immutable tags and overwrites the tag otherwise. See [docs/plans/CI-002.md](../docs/plans/CI-002.md).

## Parameter Store setup (Test)

`scripts/setup-test-parameters.py` asks you for each value and writes it to AWS Systems Manager Parameter Store under `/air-concepts/test/` in `ap-southeast-2`. It covers the backend API and the frontend. Parameter list, IAM and policy JSON: [docs/security-access.md](../docs/security-access.md).

How it works:

- Without `--apply` it lists every key, with its type and whether it is already set. It writes nothing and asks you nothing.
- With `--apply` it asks for each key that is not set. Type the value and press Enter. Secret values are hidden as you type. A blank answer skips that key.
- Existing keys are kept. `--overwrite` asks for them too and replaces them. In a dry run it shows them as `would replace`.
- Values are never printed or saved to a file.
- `--add-custom` (with `--apply`) asks after the built-in keys for your own parameter names under `/air-concepts/test/`, such as `app/feature/limit`. For each name, choose `String` or `SecureString`, then type the value. A blank name finishes. Names may use letters, digits, `-`, `_`, `.` and `/`. A name that is already listed, or that names `live`, is refused.
- The script stops before anything is written if the caller is not in `--expected-account-id`, or if a path is outside `/air-concepts/test/` or names `live`.

The script does not check the values. A wrong value is written as typed. A required key left blank means the API may not start, so check the values before the next deploy.

Install the script dependencies once:

```bash
python -m pip install -r infra/scripts/requirements.txt
```

Run the script tests (mocked AWS, no network):

```bash
python -m pytest infra/scripts -q
```

Commands. Use your own named Test profile. Agents do not run any of these.

```bash
# 1. List every key and what is set. Writes nothing.
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426

# 2. Enter values for keys that are not set yet. Blank skips a key.
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426 --apply

# 3. Same, with a customer-managed KMS key for SecureString values
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426 --apply --kms-key-id alias/<test-key>

# 4. Replace existing values as well (asks for every key)
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426 --apply --overwrite

# 5. Enter values for the built-in keys, then your own parameters
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426 --apply --add-custom
```

Exit codes: `0` success or dry run, `1` a write failed, `2` refused by a safety check. Nothing is written on exit `2`.
