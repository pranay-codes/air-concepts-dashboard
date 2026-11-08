#!/usr/bin/env bash
# Runs backend tests, frontend checks, then the Playwright e2e tests. Prints output only on failure.
# Local runs read the repo-root .env through the local profile. CI sets placeholder settings (see ci.yml).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
log="$(mktemp)"
api_started=0

cleanup() {
  if [ "$api_started" = 1 ]; then
    (cd "$root/backend" && ./mvnw -q -B spring-boot:stop >/dev/null 2>&1) || true
  fi
  rm -f "$log"
}
trap cleanup EXIT

"$root/scripts/test-backend.sh"
"$root/scripts/test-frontend.sh"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
cd "$root/backend"
if ! ./mvnw -q -B spring-boot:start >"$log" 2>&1; then
  cat "$log"
  echo "FAIL: the API did not start for the e2e tests" >&2
  exit 1
fi
api_started=1

# The unauthenticated-request check calls the local API; browser journeys use synthetic boundaries.
for _ in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/me)" != "000" ]; then
    break
  fi
  sleep 1
done

cd "$root/frontend"
export E2E_LOCAL_API=true
if ! npm run e2e --silent >"$log" 2>&1; then
  cat "$log"
  echo "FAIL: e2e (Playwright)" >&2
  exit 1
fi
echo "OK: test-all passed (backend, frontend, Playwright)"
