#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
"${java_bin}javac" --release 8 -d "$test_dir" \
    "$project_dir/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperlauncher/utils/horizon/HorizonEngineAssets.java" \
    "$project_dir/scripts/tests/HorizonEngineAssetsTest.java"
"${java_bin}java" -cp "$test_dir" HorizonEngineAssetsTest \
    "$project_dir/Hyper_Launcher_v3/libs/libhorizon_android.aar"
