#!/bin/sh
# Generates the Gradle wrapper jar (gradle/wrapper/gradle-wrapper.jar), which is not
# bundled in this archive. Run this once after unpacking.
#
# Requires a system Gradle to be installed (https://gradle.org/install/) OR Docker.
#
# Option A — system Gradle:
#     gradle wrapper --gradle-version 8.10.2
#
# Option B — this script (uses system gradle if present):
set -e

if command -v gradle >/dev/null 2>&1; then
  echo "Found system gradle: $(gradle --version | grep Gradle | head -1)"
  gradle wrapper --gradle-version 8.10.2
  echo "Wrapper generated. You can now run: ./gradlew :examples:counter-demo:bootRun"
else
  cat <<'MSG'
No system 'gradle' found.

Install Gradle (any 8.x) once, then run:
    gradle wrapper --gradle-version 8.10.2

macOS:   brew install gradle
SDKMAN:  sdk install gradle 8.10.2
Linux:   see https://gradle.org/install/

After the wrapper jar exists, you never need a system Gradle again —
just use ./gradlew.
MSG
  exit 1
fi
