#!/usr/bin/env bash
# Runs the backend and frontend checks, then confirms the generated API types match the snapshot.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

"$root/scripts/test-backend.sh"
"$root/scripts/test-frontend.sh"

if ! (cd "$root/frontend" && npm run api:check --silent >"$log" 2>&1); then
  cat "$log"
  echo "FAIL: api:check, generated types differ from docs/api/openapi.json" >&2
  exit 1
fi
echo "OK: check-all passed (backend, frontend, api:check)"
