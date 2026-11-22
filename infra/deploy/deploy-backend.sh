#!/usr/bin/env bash
# CI-002 / INF-006: deploy one backend image to the host of one environment. Runs on the host, sent through
# SSM Run Command. The same script serves Test and Live; every difference arrives as an input, never as code.
#
# Environment inputs: ENVIRONMENT (test or live), NEW_BACKEND_DIGEST, CADDY_IMAGE_DIGEST,
# BACKEND_REPOSITORY_URI, API_HOSTNAME, AWS_REGION (optional, default ap-southeast-2).
# Optional inputs, taken from infra/environments/<env>.json by the workflow:
#   API_MEMORY_LIMIT               container memory limit passed to Compose (Compose default 512m).
#   EXPECTED_SUPABASE_PROJECT_REF  refuse to deploy unless SSM supabase/project-ref equals this value.
#   ALLOW_TEST_ONLY_FEATURES       "true" lets the Xero diagnostics parameters through; anything else
#                                  ignores them, so Live can never enable request or body logging.
# AIR_CONCEPTS_DIR overrides /opt/air-concepts and exists for tests only.
#
# Never prints parameter values and never enables shell tracing.
set -euo pipefail
umask 077

air_dir="${AIR_CONCEPTS_DIR:-/opt/air-concepts}"
region="${AWS_REGION:-ap-southeast-2}"
runtime_dir="$air_dir/runtime"
env_file="$runtime_dir/backend.env"
env_new="$runtime_dir/backend.env.new"
env_prev="$runtime_dir/backend.env.prev"
digest_file="$air_dir/current-digest"

# SSM key suffix -> env var name. The host role may read these exact names, but cannot list a path.
declare -A key_for=(
  [db/jdbc-url]=DATABASE_URL
  [db/username]=DATABASE_USER
  [db/password]=DATABASE_PASSWORD
  [supabase/project-ref]=SUPABASE_PROJECT_REF
  [supabase/jwt-issuer-uri]=SUPABASE_JWT_ISSUER_URI
  [supabase/jwks-uri]=SUPABASE_JWKS_URI
  [supabase/service-role-key]=SUPABASE_SERVICE_ROLE_KEY
  [xero/client-id]=XERO_CLIENT_ID
  [xero/client-secret]=XERO_CLIENT_SECRET
  [xero/redirect-uri]=XERO_REDIRECT_URI
  [xero/frontend-callback-uri]=XERO_FRONTEND_CALLBACK_URI
  [xero/token-encryption-key]=XERO_TOKEN_ENCRYPTION_KEY
  [vwork/mode]=DASHBOARD_VWORK_MODE
  [vwork/credential-encryption-key]=VWORK_CREDENTIAL_ENCRYPTION_KEY
  [cors/allowed-origins]=CORS_ALLOWED_ORIGINS
)
# Optional Xero request diagnostics (default off when absent). Read only when the environment allows
# Test-only features, so Live ignores them whatever is in Parameter Store. See docs/xero-diagnostics.md.
declare -A test_only_key_for=(
  [diagnostics/xero-enabled]=DASHBOARD_DIAGNOSTICS_XERO_ENABLED
  [diagnostics/xero-include-bodies]=DASHBOARD_DIAGNOSTICS_XERO_INCLUDE_BODIES
  [diagnostics/xero-max-body-chars]=DASHBOARD_DIAGNOSTICS_XERO_MAX_BODY_CHARS
)
if [ "${ALLOW_TEST_ONLY_FEATURES:-false}" = "true" ]; then
  for suffix in "${!test_only_key_for[@]}"; do
    key_for[$suffix]="${test_only_key_for[$suffix]}"
  done
fi
docker_config=""

fail() {
  echo "REFUSED: $*" >&2
  exit 1
}

cleanup() {
  rm -f -- "$env_new"
  if [ -n "$docker_config" ]; then
    rm -rf -- "$docker_config"
  fi
}
trap cleanup EXIT

is_digest() {
  [[ "${1:-}" =~ ^[0-9a-f]{64}$ ]]
}

case "${ENVIRONMENT:-}" in
  test|live) ;;
  *) fail "ENVIRONMENT must be test or live, got '${ENVIRONMENT:-unset}'" ;;
esac
is_digest "${NEW_BACKEND_DIGEST:-}" || fail "NEW_BACKEND_DIGEST must be 64 lowercase hex characters without a sha256: prefix"
is_digest "${CADDY_IMAGE_DIGEST:-}" || fail "CADDY_IMAGE_DIGEST must be 64 lowercase hex characters without a sha256: prefix"
[[ "${BACKEND_REPOSITORY_URI:-}" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/[a-z0-9._/-]+$ ]] \
  || fail "BACKEND_REPOSITORY_URI is not an ECR repository URI"
[[ "${API_HOSTNAME:-}" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] \
  || fail "API_HOSTNAME is not a hostname"
if [ -n "${API_MEMORY_LIMIT:-}" ]; then
  [[ "$API_MEMORY_LIMIT" =~ ^[0-9]+[mg]$ ]] || fail "API_MEMORY_LIMIT must look like 512m or 1g"
fi
if [ -n "${EXPECTED_SUPABASE_PROJECT_REF:-}" ]; then
  [[ "$EXPECTED_SUPABASE_PROJECT_REF" =~ ^[a-z0-9]{10,30}$ ]] || fail "EXPECTED_SUPABASE_PROJECT_REF is not a Supabase project ref"
fi

param_root="/air-concepts/${ENVIRONMENT}/backend/"
registry="${BACKEND_REPOSITORY_URI%%/*}"

previous=""
if [ -f "$digest_file" ]; then
  previous="$(tr -d '\n' < "$digest_file")"
  is_digest "$previous" || previous=""
fi

mkdir -p -- "$runtime_dir"

# Keep the ECR token out of /root/.docker: use a private config dir, removed on exit.
docker_config="$(mktemp -d)"
export DOCKER_CONFIG="$docker_config"
aws ecr get-login-password --region "$region" \
  | docker login --username AWS --password-stdin "$registry" > /dev/null

# Builds $env_new from this environment's backend parameters. Refuses before any container changes.
write_env_file() {
  local -a names=()
  local raw="" chunk name value short key required offset seen=" "
  for short in "${!key_for[@]}"; do
    names+=("$param_root$short")
  done
  # GetParameters accepts at most ten names. No values enter logs or shell tracing.
  for ((offset=0; offset<${#names[@]}; offset+=10)); do
    chunk="$(aws ssm get-parameters --region "$region" --names "${names[@]:offset:10}" --with-decryption \
      --query 'Parameters[].[Name,Value]' --output text)" \
      || fail "could not read backend parameters from SSM"
    if [ -n "$chunk" ] && [ "$chunk" != "None" ]; then raw+="$chunk"$'\n'; fi
  done

  : > "$env_new"
  while IFS=$'\t' read -r name value; do
    [ -n "$name" ] || continue
    short="${name#"$param_root"}"
    key="${key_for[$short]:-}"
    [ -n "$key" ] || continue
    [ -n "$value" ] || fail "$key is empty"
    case "$value" in
      *$'\r'*) fail "$key contains a carriage return" ;;
    esac
    if [ "$key" = "SUPABASE_PROJECT_REF" ] && [ -n "${EXPECTED_SUPABASE_PROJECT_REF:-}" ] \
      && [ "$value" != "$EXPECTED_SUPABASE_PROJECT_REF" ]; then
      fail "SUPABASE_PROJECT_REF in SSM does not match the project the $ENVIRONMENT environment expects"
    fi
    printf '%s=%s\n' "$key" "$value" >> "$env_new"
    seen="$seen$key "
  done <<< "$raw"

  for required in DATABASE_URL DATABASE_USER DATABASE_PASSWORD SUPABASE_PROJECT_REF \
    SUPABASE_JWT_ISSUER_URI SUPABASE_JWKS_URI; do
    case "$seen" in
      *" $required "*) ;;
      *) fail "required setting $required is missing from SSM" ;;
    esac
  done
  case "$seen" in
    *" CORS_ALLOWED_ORIGINS "*) ;;
    *) echo "warning: CORS_ALLOWED_ORIGINS is not set; the API keeps its localhost default" >&2 ;;
  esac
  chmod 600 -- "$env_new"
}

# Prints why the API did not start, to stderr, so the SSM output shows the cause. It prints only the
# Compose status, the container health state and the last 100 log lines. It never reads backend.env
# and never runs `compose config`, because that prints resolved values. Failures here do not stop
# the rollback.
show_api_diagnostics() {
  local cid
  echo "Diagnostics for the API container (last 100 log lines; no environment values are printed):" >&2
  docker compose ps >&2 || true
  cid="$(docker compose ps -q api 2>/dev/null || true)"
  if [ -n "$cid" ]; then
    docker inspect --format '{{json .State.Health}}' "$cid" >&2 || true
  fi
  docker compose logs --no-color --tail 100 api >&2 || true
}

# Starts the stack with one digest and waits for the health check.
deploy_digest() {
  local digest="$1"
  (
    cd "$air_dir"
    export ENVIRONMENT BACKEND_REPOSITORY_URI API_HOSTNAME CADDY_IMAGE_DIGEST
    if [ -n "${API_MEMORY_LIMIT:-}" ]; then export API_MEMORY_LIMIT; fi
    export BACKEND_IMAGE_DIGEST="$digest"
    docker compose up -d --wait --wait-timeout 180 || { show_api_diagnostics; exit 1; }
  )
}

write_env_file

if [ -f "$env_file" ]; then
  cp -p -- "$env_file" "$env_prev"
fi
mv -f -- "$env_new" "$env_file"

if deploy_digest "$NEW_BACKEND_DIGEST"; then
  printf '%s\n' "$NEW_BACKEND_DIGEST" > "$digest_file.tmp"
  mv -f -- "$digest_file.tmp" "$digest_file"
  rm -f -- "$env_prev"
  echo "Deployed backend digest $NEW_BACKEND_DIGEST to the $ENVIRONMENT host"
  exit 0
fi

echo "The new backend did not become healthy; restoring the previous release" >&2
if [ -f "$env_prev" ]; then
  mv -f -- "$env_prev" "$env_file"
fi
if [ -n "$previous" ] && deploy_digest "$previous"; then
  echo "Restored previous backend digest $previous" >&2
elif [ -n "$previous" ]; then
  echo "Rollback to $previous also failed; check the $ENVIRONMENT host" >&2
else
  # First deploy on a fresh host: there is nothing to restore, so stop the failed containers rather than
  # leave a half-started release running.
  echo "No previous digest is recorded (first deploy on this host); nothing to restore" >&2
  (
    cd "$air_dir"
    export ENVIRONMENT BACKEND_REPOSITORY_URI API_HOSTNAME CADDY_IMAGE_DIGEST
    export BACKEND_IMAGE_DIGEST="$NEW_BACKEND_DIGEST"
    docker compose down >&2
  ) || true
fi
exit 1
