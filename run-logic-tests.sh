#!/usr/bin/env bash
set -euo pipefail
if ! command -v kotlinc >/dev/null; then
  echo 'Install Kotlin compiler to run this standalone logic smoke test.' >&2
  exit 1
fi
HERE="$(cd "$(dirname "$0")" && pwd)"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT
kotlinc "$HERE/app/src/main/java/dev/tomatowear/app/TimerLogic.kt" "$HERE/test/LogicSmokeTest.kt" -include-runtime -d "$TMPDIR/logic-tests.jar"
java -jar "$TMPDIR/logic-tests.jar"
