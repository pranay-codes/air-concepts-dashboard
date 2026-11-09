# Security access

This page records who and what may read or write the Test environment's secrets and configuration. It covers AWS Systems Manager Parameter Store for the dashboard API. Live is out of scope here and is released through a separate human-triggered workflow (ADR-001). Every setting, its format and where it is read: [configuration.md](configuration.md).

## Parameter Store layout

- Region: `ap-southeast-2`.
- Test prefix: `/air-concepts/test/`, with `backend/` and `frontend/` below it. Live parameters use `/air-concepts/live/`. Nothing in this repository writes there.
- Account: `923079246426`. **Test and Live currently share this account.** The account check in the setup script therefore does not separate them. The name prefix and the IAM policies below do.
- Types: `SecureString` for passwords and secrets. `String` for everything else.
- Encryption: the AWS default SSM key unless a customer-managed key is given with `--kms-key-id`.

| Parameter | Type | Read by | Required |
|---|---|---|---|
| `backend/db/jdbc-url` | String | API `DATABASE_URL` | Yes |
| `backend/db/username` | String | API `DATABASE_USER` | Yes |
| `backend/db/password` | SecureString | API `DATABASE_PASSWORD` | Yes |
| `backend/supabase/project-ref` | String | API `SUPABASE_TEST_PROJECT_REF` | Yes |
| `backend/supabase/jwt-issuer-uri` | String | API `SUPABASE_JWT_ISSUER_URI` | Yes |
| `backend/supabase/jwks-uri` | String | API `SUPABASE_JWKS_URI`, must end `/.well-known/jwks.json` | Yes |
| `backend/supabase/service-role-key` | SecureString | API `SUPABASE_SERVICE_ROLE_KEY` (staff administration only) | No |
| `backend/cors/allowed-origins` | String | API `CORS_ALLOWED_ORIGINS` | No |
| `backend/xero/client-id` | String | API `XERO_CLIENT_ID` | No |
| `backend/xero/client-secret` | SecureString | API `XERO_CLIENT_SECRET` | No |
| `backend/xero/redirect-uri` | String | API `XERO_REDIRECT_URI` | No |
| `backend/xero/frontend-callback-uri` | String | API `XERO_FRONTEND_CALLBACK_URI` | No |
| `backend/xero/token-encryption-key` | SecureString | API `XERO_TOKEN_ENCRYPTION_KEY` | No |
| `backend/vwork/api-key` | SecureString | none yet (placeholder) | No |
| `frontend/api-base-url` | String | frontend `VITE_API_BASE_URL` at build time | Yes |
| `frontend/supabase-url` | String | frontend `VITE_SUPABASE_URL` at build time | Yes |
| `frontend/supabase-publishable-key` | String | frontend `VITE_SUPABASE_PUBLISHABLE_KEY` at build time | Yes |

Notes:

- The frontend values are inlined into the browser bundle by Vite at build time. They are public by design, so they are stored as `String`. Never put a service-role key or other secret under `frontend/`.
- The API reads plain environment variables only. `infra/deploy/deploy-test.sh` reads these parameters on the Test host through SSM Run Command at deploy time and writes `runtime/backend.env`. The API never calls Parameter Store itself.
- A blank answer in the script skips that key. The API will not start without the required backend keys, so skipping one is a deliberate choice. Xero code is wired (see [configuration.md](configuration.md)), but no Xero capability counts as implemented until a spike note confirms it. vWork has no code yet (AGENTS.md section 2).
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

`infra/scripts/setup-test-parameters.py`. Usage is in [infra/README.md](../infra/README.md#parameter-store-setup-test). It is tested with mocked AWS calls (`infra/scripts/test_setup_test_parameters.py`). The tests include a drift check against `application.yml` and the startup guard in `RequiredSettingsPostProcessor.java`.

## Open items

- Confirm whether Live will move to a separate AWS account. If it does, change the account check to a value per environment and update the policies above.
- Decide whether to use a customer-managed KMS key. Replace `REPLACE_WITH_KEY_ID` if so.
- Name the EC2 Test role once it exists, and attach the read policy above.
- Confirm on the Test host that `deploy-test.sh` is the only path that writes `runtime/backend.env`.
