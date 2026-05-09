#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
rm -rf build
mkdir -p build/classes
find src/main/java -name '*.java' | sort > build/sources.txt
javac --release 17 -d build/classes @build/sources.txt
jar --create --file build/mini-postgres.jar -C build/classes .
echo "Built build/mini-postgres.jar"
