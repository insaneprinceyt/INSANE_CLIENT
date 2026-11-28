#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
gson_jar="$project_dir/Hyper_Launcher_v3/libs/gson-2.8.6.jar"
"${java_bin}javac" --release 8 -cp "$gson_jar" -d "$test_dir" \
    "$project_dir/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperlauncher/utils/client/InsaneClientInstaller.java" \
    "$project_dir/scripts/tests/InsaneClientInstallerTest.java"
"${java_bin}java" -cp "$test_dir:$gson_jar" \
    com.ashmeet.hyperlauncher.utils.client.InsaneClientInstallerTest "$@"
