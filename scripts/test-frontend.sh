#!/usr/bin/env bash
# Runs frontend lint, typecheck, unit tests and build. Prints output only on failure.
set -euo pipefail

cd "$(dirname "$0")/../frontend"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

for stage in lint typecheck test build; do
  if ! npm run "$stage" --silent >"$log" 2>&1; then
    cat "$log"
    echo "FAIL: frontend $stage" >&2
    exit 1
  fi
done
echo "OK: frontend lint, typecheck, test and build passed"
