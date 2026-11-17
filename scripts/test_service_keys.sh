#!/usr/bin/env bash
set -euo pipefail
project_root=$(cd "$(dirname "$0")/.." && pwd)
test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
"${java_bin}javac" -d "$test_output" \
  "$project_root/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperlauncher/utils/ServiceKeys.java" \
  "$project_root/scripts/tests/ServiceKeysTest.java"
"${java_bin}java" -cp "$test_output" ServiceKeysTest
