#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ ! -f build/mini-postgres.jar ]]; then
  ./scripts/build.sh
fi
java -cp build/mini-postgres.jar com.example.minipostgres.Main cli
