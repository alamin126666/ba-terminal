#!/bin/sh
# Gradle wrapper starter. If gradle-wrapper.jar is present it is used;
# otherwise the script falls back to a `gradle` on PATH (e.g. installed by
# gradle/actions/setup-gradle in CI). Android Studio builds without this script.
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec "$JAVA_HOME/bin/java" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
elif command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "Gradle wrapper jar not found and no 'gradle' on PATH." >&2
  echo "Install Gradle 8.7+ or open the project in Android Studio." >&2
  exit 1
fi
