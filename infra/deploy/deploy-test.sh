#!/usr/bin/env bash
# CI-002: deploy one backend image to the Test host. Runs on the host, sent through SSM Run Command.
#
# Environment inputs: ENVIRONMENT (must be test), NEW_BACKEND_DIGEST, CADDY_IMAGE_DIGEST,
# BACKEND_REPOSITORY_URI, API_HOSTNAME, AWS_REGION (optional, default ap-southeast-2).
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
  [supabase/project-ref]=SUPABASE_TEST_PROJECT_REF
  [supabase/jwt-issuer-uri]=SUPABASE_JWT_ISSUER_URI
  [supabase/jwks-uri]=SUPABASE_JWKS_URI
  [cors/allowed-origins]=CORS_ALLOWED_ORIGINS
)
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

[ "${ENVIRONMENT:-}" = "test" ] || fail "ENVIRONMENT must be test, got '${ENVIRONMENT:-unset}'"
is_digest "${NEW_BACKEND_DIGEST:-}" || fail "NEW_BACKEND_DIGEST must be 64 lowercase hex characters without a sha256: prefix"
is_digest "${CADDY_IMAGE_DIGEST:-}" || fail "CADDY_IMAGE_DIGEST must be 64 lowercase hex characters without a sha256: prefix"
[[ "${BACKEND_REPOSITORY_URI:-}" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/[a-z0-9._/-]+$ ]] \
  || fail "BACKEND_REPOSITORY_URI is not an ECR repository URI"
[[ "${API_HOSTNAME:-}" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] \
  || fail "API_HOSTNAME is not a hostname"

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

# Builds $env_new from the Test backend parameters. Refuses before any container changes.
write_env_file() {
  local -a names=()
  local raw name value short key required seen=" "
  for short in "${!key_for[@]}"; do
    names+=("$param_root$short")
  done
  raw="$(aws ssm get-parameters --region "$region" --names "${names[@]}" --with-decryption \
    --query 'Parameters[].[Name,Value]' --output text)" \
    || fail "could not read backend parameters from SSM"
  [ "$raw" = "None" ] && raw=""

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
    printf '%s=%s\n' "$key" "$value" >> "$env_new"
    seen="$seen$key "
  done <<< "$raw"

  for required in DATABASE_URL DATABASE_USER DATABASE_PASSWORD SUPABASE_TEST_PROJECT_REF \
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

# Starts the stack with one digest and waits for the health check.
deploy_digest() {
  local digest="$1"
  (
    cd "$air_dir"
    export ENVIRONMENT BACKEND_REPOSITORY_URI API_HOSTNAME CADDY_IMAGE_DIGEST
    export BACKEND_IMAGE_DIGEST="$digest"
    docker compose up -d --wait --wait-timeout 180
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
  echo "Deployed backend digest $NEW_BACKEND_DIGEST to the Test host"
  exit 0
fi

echo "The new backend did not become healthy; restoring the previous release" >&2
if [ -f "$env_prev" ]; then
  mv -f -- "$env_prev" "$env_file"
fi
if [ -n "$previous" ] && deploy_digest "$previous"; then
  echo "Restored previous backend digest $previous" >&2
elif [ -n "$previous" ]; then
  echo "Rollback to $previous also failed; check the Test host" >&2
else
  echo "No previous digest is recorded; nothing to restore" >&2
fi
exit 1
