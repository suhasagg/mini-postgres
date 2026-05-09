#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./scripts/build.sh >/dev/null
mkdir -p build/test-classes
find src/test/java -name '*.java' | sort > build/test-sources.txt
javac --release 17 -cp build/classes -d build/test-classes @build/test-sources.txt
java -cp build/classes:build/test-classes com.example.minipostgres.TestRunner
