# Configuration reference

This is the single list of every setting the application, its build and its deployment read. Each row names where the value is read, whether it is required, where it comes from locally and on Test, and its expected format.

When you add, rename or remove a setting, change this file in the same pull request, along with `.env.EXAMPLE`, the README and `docs/security-access.md` if they mention it.

Placeholders such as `<project-ref>` and `<api-host>` stand for real values. Real values never go in git, in `.env.EXAMPLE` or in this file. Secrets go only in Parameter Store as `SecureString`, or in GitHub secrets.

## Backend runtime (Spring API)

The API reads these as environment variables. Locally it also reads the repository root `.env` under the `local` profile (see `backend/src/main/resources/application.yml`). On Test, `infra/deploy/deploy-test.sh` writes them to `/opt/air-concepts/runtime/backend.env` from Parameter Store at deploy time.

| Variable | Required | Read by | Local `.env` | Parameter Store | Format and notes |
|---|---|---|---|---|---|
| `DATABASE_URL` | Yes | `application.yml` (`spring.datasource.url`) | yes | `backend/db/jdbc-url` (String) | JDBC URL with `sslmode=require`. Must contain the Test project ref, or be a Supabase pooler host whose user is `postgres.<project-ref>`. The password must not appear in the URL. |
| `DATABASE_USER` | Yes | `application.yml` | yes | `backend/db/username` (String) | For the Supabase pooler, `postgres.<project-ref>`. |
| `DATABASE_PASSWORD` | Yes | `application.yml` | yes | `backend/db/password` (SecureString) | Secret. |
| `SUPABASE_JWT_ISSUER_URI` | Yes | `application.yml`, `JwtDecoderConfig`, `SupabaseAdminConfig` | yes | `backend/supabase/jwt-issuer-uri` (String) | Exactly `https://<project-ref>.supabase.co/auth/v1`. `SupabaseAdminConfig` refuses to start the API with a different path, query, fragment or user info. The token's `iss` must match it. |
| `SUPABASE_JWKS_URI` | Yes | `application.yml`, `JwtDecoderConfig` | yes | `backend/supabase/jwks-uri` (String) | Exactly `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`. **Not** the issuer URL: the base `/auth/v1` path requires an API key, and the key fetch fails without one. The startup guard checks the host only, so a wrong path is not caught at startup. |
| `SUPABASE_TEST_PROJECT_REF` | Yes | `RequiredSettingsPostProcessor` | yes | `backend/supabase/project-ref` (String) | The Test project ref. Startup fails unless the issuer, JWKS and database settings point at it. |
| `SUPABASE_JWT_AUDIENCE` | No (default `authenticated`) | `application.yml`, `JwtDecoderConfig` | commented | none | Supabase puts `authenticated` on user tokens. Leave the default unless Supabase changes. |
| `CORS_ALLOWED_ORIGINS` | No (default `http://localhost:5173`) | `application.yml`, `CorsProperties` | commented | `backend/cors/allowed-origins` (String) | Browser origins allowed to call the API. Scheme and host only, no path. Use a comma to separate several. On Test, the CloudFront origin of the frontend. |
| `SUPABASE_SERVICE_ROLE_KEY` | No | `SupabaseAdminConfig`, `SupabaseAdminClient` | not in example | `backend/supabase/service-role-key` (SecureString) | Secret. Used only by staff administration, through the Supabase Auth admin API. When blank, staff administration returns `AUTH_ADMIN_NOT_CONFIGURED` (503). Never send it to the browser or put it under `frontend/`. See the UC5 auth operations notes. |
| `XERO_CLIENT_ID` | No | `XeroConfig` | not in example | `backend/xero/client-id` (String) | Xero OAuth app client ID. Blank means Xero connection fails closed. |
| `XERO_CLIENT_SECRET` | No | `XeroConfig` | not in example | `backend/xero/client-secret` (SecureString) | Secret. |
| `XERO_REDIRECT_URI` | No | `XeroConfig` | not in example | `backend/xero/redirect-uri` (String) | Exactly the API callback, ending `/api/v1/xero/callback`. Must match the redirect registered in the Xero app. |
| `XERO_FRONTEND_CALLBACK_URI` | No | `XeroConfig` | not in example | `backend/xero/frontend-callback-uri` (String) | Application page the callback redirects to, with the attempt ID and no credentials. |
| `XERO_TOKEN_ENCRYPTION_KEY` | No | `XeroConfig`, `TokenCipher` | not in example | `backend/xero/token-encryption-key` (SecureString) | Secret. Base64 of 32 random bytes. Used by the AES-256-GCM token cipher to encrypt Xero access and refresh tokens at rest. |
| `SPRING_PROFILES_ACTIVE` | Set by the runtime | Spring | `local` for local runs | not in Parameter Store | `local` loads `.env`; `test` and `ci` disable the database health check; compose sets `hosted`. No `hosted` section exists in `application.yml`, so the base settings apply. |
| `JAVA_TOOL_OPTIONS` | No | JVM | no | none | Set in `infra/deploy/compose.yaml` only. |
| `API_MEMORY_LIMIT` | No (default `512m`) | compose | no | none | Container memory limit. |

Settings present in Parameter Store but not read by any code yet: `backend/vwork/api-key` (SecureString, placeholder). It is not exported to the host.

## Frontend build (Vite)

Vite inlines these into the browser bundle at build time, so they are public by design. They are read by `frontend/src/env.ts`. Missing values stop the build or sign-in with a clear error.

| Variable | Required | Local `.env` | Parameter Store | Notes |
|---|---|---|---|---|
| `VITE_API_BASE_URL` | Yes | yes (`http://localhost:8080` locally) | `frontend/api-base-url` (String) | Origin of the API, for example `https://<api-host>`. No path. |
| `VITE_SUPABASE_URL` | Yes | yes | `frontend/supabase-url` (String) | `https://<project-ref>.supabase.co`. Must be the same project as `SUPABASE_JWT_ISSUER_URI`, or every token will be rejected. |
| `VITE_SUPABASE_PUBLISHABLE_KEY` | Yes | yes | `frontend/supabase-publishable-key` (String) | Public key for sign-in. Not a secret, but it is kept out of source. Never put a service-role key here. |

The Test frontend deploy workflow (`.github/workflows/deploy-test.yml`) reads the three `frontend/*` parameters and fails the build if any is missing. It exports them as `VITE_*` variables for `npm run build`, so the values are baked into the bundle.

How the frontend finds its values:

- `frontend/vite.config.ts` sets `envDir: ".."`, so Vite reads the repo-root `.env` for local runs. CI has no `.env` file, so it uses the values in the workflow environment.
- `frontend/src/env.ts` reads only the three variables above. It stops with a message naming any that are missing.
- The dev server uses Vite's default port, 5173. That matches the default `CORS_ALLOWED_ORIGINS`.
- Node is pinned in `.nvmrc` (currently 26). CI and the deploy workflow both read it. It isn't a runtime setting.

Frontend parameters in Parameter Store, all of them: `frontend/api-base-url`, `frontend/supabase-url`, `frontend/supabase-publishable-key`. No frontend parameter is a secret.

## Test tooling variables

Read only by the test runners. They aren't application settings and aren't deployed.

| Variable | Set by | Notes |
|---|---|---|
| `E2E_LOCAL_API` | `.github/workflows/ci.yml`, `scripts/test-all.sh`, or a developer | `true` runs the one check that calls the local API. Without it that check is skipped. |
| `E2E_LOCAL_API_PORT` | developer (optional) | Local API port for that check. Default `8080`. |

The Playwright setup in `frontend/e2e/setup.ts` sets synthetic `VITE_*` values for the browser tests. They never reach a deployed build.

## Deployment (Test)

Set by the deploy workflow and `infra/deploy/compose.yaml`, not by Parameter Store.

| Variable | Set by | Notes |
|---|---|---|
| `ENVIRONMENT` | Workflow (`test`) | `deploy-test.sh` refuses anything other than `test`. Compose requires it. |
| `API_HOSTNAME` | Workflow from the `TEST_API_HOSTNAME` repository variable | Hostname Caddy serves over HTTPS. |
| `BACKEND_REPOSITORY_URI` | Workflow | ECR repository URI, with no digest. |
| `BACKEND_IMAGE_DIGEST` / `NEW_BACKEND_DIGEST` | Workflow | 64 lowercase hex characters, without `sha256:`. |
| `CADDY_IMAGE_DIGEST` | Workflow (pinned) | 64 lowercase hex characters. Must be an ARM64-capable Caddy image. |

## Repository variables and secrets (GitHub)

| Name | Kind | Used by |
|---|---|---|
| `TEST_DEPLOY_ROLE_ARN` | Variable | Deploy workflows, to assume the Test deploy role |
| `ECR_REPOSITORY_URI` | Variable | Backend image repository |
| `TEST_INSTANCE_ID` | Variable | SSM Run Command target (Test host) |
| `TEST_API_HOSTNAME` | Variable | `API_HOSTNAME` |
| `TEST_FRONTEND_URL` | Variable | Frontend URL |
| `TEST_BUCKET` | Variable | Frontend S3 bucket |
| `TEST_DISTRIBUTION_ID` | Variable | CloudFront distribution to invalidate |
| `SUPABASE_TEST_PROJECT_REF` | **Secret** | `.github/workflows/supabase-migrations.yml` only. It has the same name as the runtime variable but is a separate GitHub secret. |

## CI-only placeholders

`.github/workflows/ci.yml` sets `SUPABASE_JWT_ISSUER_URI`, `SUPABASE_JWKS_URI` and the three `VITE_*` values to placeholder values so the build and tests run without a real project. These never reach a deployed environment.

## Parameter Store

Parameters live under `/air-concepts/<environment>/`, where the environment is `test` or `live`. The Parameter Store names in the tables above are relative to that prefix. For Test, the full name is `/air-concepts/test/` followed by the name.

Test and Live share one AWS account, so the prefix is what separates them. `infra/scripts/setup-test-parameters.py` writes only Test parameters and does not handle Live. `docs/security-access.md` records who may read and write them.

To list what is set, use the read-only script `infra/scripts/list-parameters.py`:

```
python infra/scripts/list-parameters.py --profile <test-profile> --expected-account-id 923079246426 --environment test
```

It prints String values and names and types for SecureString parameters, and never decrypts a SecureString. It makes no writes. Test and Live share one AWS account, so the `/air-concepts/test/` prefix is what separates them.

## Open points

- The startup guard checks the host of the JWKS URI but not its path. A path check is a separate task, since it touches auth.
- Whether the service-role key stays on the Test host is an open decision. This file records the current behaviour.
- Xero settings describe what the code reads. They do not mean a Xero capability works. Xero capabilities count as implemented only when a spike note confirms them (AGENTS.md section 2).
