#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${LEDGER_BASE_URL:-http://localhost:5001}"

for _ in $(seq 1 60); do
  if curl -fsS "$BASE_URL" >/dev/null 2>&1; then
    echo "Ledger is available at $BASE_URL"
    exit 0
  fi
  sleep 2
done

echo "Ledger did not become ready in time"
exit 1
