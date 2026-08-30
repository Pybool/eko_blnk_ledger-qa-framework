#!/usr/bin/env bash
set -euo pipefail

mkdir -p artifacts
docker compose logs --no-color > artifacts/docker-compose.log || true
