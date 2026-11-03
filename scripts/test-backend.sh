#!/usr/bin/env bash
# Runs the backend build, tests and checks. Prints output only on failure.
set -euo pipefail

cd "$(dirname "$0")/../backend"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

if ! ./mvnw -q -B verify >"$log" 2>&1; then
  cat "$log"
  echo "FAIL: backend verify" >&2
  exit 1
fi
echo "OK: backend verify passed"
