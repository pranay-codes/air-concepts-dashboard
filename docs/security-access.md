# Security access

This page records who and what may read or write each environment's secrets and configuration. It covers AWS Systems Manager Parameter Store for the dashboard API. Live uses the same layout under its own prefix and is released through a separate human-triggered workflow (ADR-001, ADR-004); the Live-specific controls are in [Live access (INF-006)](#live-access-inf-006) below. Every setting, its format and where it is read: [configuration.md](configuration.md).

## Parameter Store layout

- Region: `ap-southeast-2`.
- Test prefix: `/air-concepts/test/`, with `backend/` and `frontend/` below it. Live parameters use `/air-concepts/live/`. Only `infra/scripts/setup-parameters.py --environment live`, run by a human with a Live-authorised profile, writes there.
- Account: `923079246426`. **Test and Live currently share this account.** The account check in the setup script therefore does not separate them. The name prefix and the IAM policies below do.
- Types: `SecureString` for passwords and secrets. `String` for everything else.
- Encryption: the AWS default SSM key unless a customer-managed key is given with `--kms-key-id`.

| Parameter | Type | Read by | Required |
|---|---|---|---|
| `backend/db/jdbc-url` | String | API `DATABASE_URL` | Yes |
| `backend/db/username` | String | API `DATABASE_USER` | Yes |
| `backend/db/password` | SecureString | API `DATABASE_PASSWORD` | Yes |
| `backend/supabase/project-ref` | String | API `SUPABASE_PROJECT_REF` | Yes |
| `backend/supabase/jwt-issuer-uri` | String | API `SUPABASE_JWT_ISSUER_URI` | Yes |
| `backend/supabase/jwks-uri` | String | API `SUPABASE_JWKS_URI`, must end `/.well-known/jwks.json` | Yes |
| `backend/supabase/service-role-key` | SecureString | API `SUPABASE_SERVICE_ROLE_KEY` (staff administration only) | No |
| `backend/cors/allowed-origins` | String | API `CORS_ALLOWED_ORIGINS` | No |
| `backend/xero/client-id` | String | API `XERO_CLIENT_ID` | No |
| `backend/xero/client-secret` | SecureString | API `XERO_CLIENT_SECRET` | No |
| `backend/xero/redirect-uri` | String | API `XERO_REDIRECT_URI` | No |
| `backend/xero/frontend-callback-uri` | String | API `XERO_FRONTEND_CALLBACK_URI` | No |
| `backend/xero/token-encryption-key` | SecureString | API `XERO_TOKEN_ENCRYPTION_KEY` | No |
| `backend/vwork/mode` | String | API `DASHBOARD_VWORK_MODE` | No |
| `backend/vwork/credential-encryption-key` | SecureString | API `VWORK_CREDENTIAL_ENCRYPTION_KEY` | When vWork is enabled |
| `frontend/api-base-url` | String | frontend `VITE_API_BASE_URL` at build time | Yes |
| `frontend/supabase-url` | String | frontend `VITE_SUPABASE_URL` at build time | Yes |
| `frontend/supabase-publishable-key` | String | frontend `VITE_SUPABASE_PUBLISHABLE_KEY` at build time | Yes |

Notes:

- The frontend values are inlined into the browser bundle by Vite at build time. They are public by design, so they are stored as `String`. Never put a service-role key or other secret under `frontend/`.
- The API reads plain environment variables only. `infra/deploy/deploy-backend.sh` reads these parameters on the environment's host through SSM Run Command at deploy time and writes `runtime/backend.env`. The API never calls Parameter Store itself.
- A blank answer in the script skips that key. The API will not start without the required backend keys, or without a separate valid vWork encryption key when a vWork mode is selected. Provider behaviour and the vWork workflow still require the verification described in [configuration.md](configuration.md).
- The Director enters the shared vWork API key through the application. The API stores only its encrypted envelope; it never returns the key to the browser. The old `backend/vwork/api-key` placeholder is unused and is not deleted automatically.
- The Supabase service-role key is stored as a SecureString at `backend/supabase/service-role-key`. Only the staff administration code (`SupabaseAdminConfig`, `SupabaseAdminClient`) reads it. It is never sent to the browser.
- Xero access and refresh tokens are never stored as static parameters. They stay server-side and encrypted.
- The database password must not appear in `backend/db/jdbc-url`. The setup script does not check values, so the operator keeps it out of the URL.

## Who may do what

### Operator (the person running the setup script)

Run with their own AWS identity. The script never stores credentials. This policy is limited to the Test prefix. Add the KMS statement only if a customer-managed key is used.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ConfirmIdentity",
      "Effect": "Allow",
      "Action": "sts:GetCallerIdentity",
      "Resource": "*"
    },
    {
      "Sid": "CheckExistenceByMetadata",
      "Effect": "Allow",
      "Action": "ssm:DescribeParameters",
      "Resource": "*"
    },
    {
      "Sid": "WriteTestParameters",
      "Effect": "Allow",
      "Action": "ssm:PutParameter",
      "Resource": "arn:aws:ssm:ap-southeast-2:923079246426:parameter/air-concepts/test/*"
    },
    {
      "Sid": "UseCustomerKeyForSecureStrings",
      "Effect": "Allow",
      "Action": ["kms:Encrypt", "kms:GenerateDataKey"],
      "Resource": "arn:aws:kms:ap-southeast-2:923079246426:key/REPLACE_WITH_KEY_ID"
    },
    {
      "Sid": "DenyLiveParameters",
      "Effect": "Deny",
      "Action": "ssm:*",
      "Resource": "arn:aws:ssm:ap-southeast-2:923079246426:parameter/air-concepts/live/*"
    }
  ]
}
```

`ssm:DescribeParameters` does not accept a resource ARN, so it needs `Resource: "*"`. It returns names and types only, never values. This is why the check mode and the existence check never read a value.

### EC2 Test instance role (the API)

Read-only, Test prefix only. This task does not create the role. The minimum it needs is below.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadTestBackendParameters",
      "Effect": "Allow",
      "Action": ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"],
      "Resource": "arn:aws:ssm:ap-southeast-2:923079246426:parameter/air-concepts/test/backend/*"
    },
    {
      "Sid": "DecryptCustomerKeyOnly",
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "arn:aws:kms:ap-southeast-2:923079246426:key/REPLACE_WITH_KEY_ID"
    },
    {
      "Sid": "DenyLiveParameters",
      "Effect": "Deny",
      "Action": "ssm:*",
      "Resource": "arn:aws:ssm:ap-southeast-2:923079246426:parameter/air-concepts/live/*"
    }
  ]
}
```

Drop the KMS statement if the AWS default key is used. The explicit deny keeps Live parameters out of reach even if another policy later grants broader access to this role. The Live release role must be a different role.

## Setup script

`infra/scripts/setup-parameters.py`. Usage is in [infra/README.md](../infra/README.md#parameter-store-setup-test). It is tested with mocked AWS calls (`infra/scripts/test_setup_parameters.py`). The tests include a drift check against `application.yml` and the startup guard in `RequiredSettingsPostProcessor.java`.

## Open items

- Confirm whether Live will move to a separate AWS account. If it does, change the account check to a value per environment and update the policies above.
- Decide whether to use a customer-managed KMS key. Replace `REPLACE_WITH_KEY_ID` if so.
- Name the EC2 Test role once it exists, and attach the read policy above.
- Confirm on each host that `deploy-backend.sh` is the only path that writes `runtime/backend.env`.

## Live access (INF-006)

Live uses its own prefix, roles and secrets. The controls, and the check that proves each:

| Control | How it is enforced | How to verify |
|---|---|---|
| Live host reads only Live backend parameters | The instance role from `infrastructure.json` allows `/air-concepts/live/backend/*` and denies `/air-concepts/test/*` | On the Live host, `aws ssm get-parameter --name /air-concepts/test/backend/db/jdbc-url` is denied |
| Test host cannot read Live | The same template denies the opposite environment | Same check on the Test host against `/air-concepts/live/` |
| Only the Live workflow can assume the Live deploy role | `deployer-role.json` trusts `repo:<owner>/<repo>:environment:live`, and `job_workflow_ref` of `deploy-live.yml` on `main` | A run of any other workflow or branch fails at "Assume the environment's deploy role" |
| The Live deploy role cannot reach Test secrets or write Test images | Explicit `Deny` on `/air-concepts/test/*` and on Test ECR writes | `aws ssm get-parameter` against a Test path with the Live role fails |
| The deploy role cannot read Live backend secrets | It has no statement for `/air-concepts/live/backend/*`; only the host role reads them | Review of the policy; test in `infra/scripts/test_live_release.py` |
| Test credentials cannot work on Live | Separate Supabase project, JWKS and issuer, enforced by the startup guard and by `EXPECTED_SUPABASE_PROJECT_REF` | A Test-issued token returns 401 from the Live API |
| Test and Live never share an identifier | `infra/scripts/environment_config.py validate` runs in CI and before every deploy | CI fails if `test.json` and `live.json` share an instance, bucket, distribution, role, repository, prefix or Supabase ref |

GitHub secrets for Live (`SUPABASE_LIVE_ACCESS_TOKEN`, `SUPABASE_LIVE_DB_PASSWORD`) are repository secrets because environment-scoped secrets need a paid plan for a private repository. Anyone with write access can create a workflow that reads them, so: keep write access small, use a Supabase token created only for Live, and rotate both secrets (Supabase dashboard, then GitHub) whenever a person with write access leaves and after any suspected exposure. No AWS credentials are stored in GitHub at all.

The Live DB URL must be the Supabase **session pooler** on port 5432 with user `postgres.<project-ref>` and `sslmode=require`. The host has an IPv4 address only, so the IPv6-only direct host does not work, and the transaction pooler (port 6543) is not compatible with prepared statements.
