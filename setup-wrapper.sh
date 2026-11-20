#!/usr/bin/env bash
set -euo pipefail
# First-run helper for a machine with Gradle 9.7.1 already installed.
# It regenerates the official Gradle wrapper JAR and scripts in this project.
cd "$(dirname "$0")"
if ! command -v gradle >/dev/null; then
  echo 'Gradle is not installed. Install Gradle 9.7.1 (or open the project in Android Studio) first.' >&2
  exit 1
fi
gradle wrapper --gradle-version 9.7.1 --distribution-type bin
printf '%s\n' 'Gradle wrapper generated. Run ./gradlew :app:assembleDebug'
