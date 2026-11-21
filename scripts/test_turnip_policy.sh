#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
"${java_bin}javac" --release 8 -d "$test_dir" \
    "$project_dir/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperbackend/vulkan/TurnipPolicy.java" \
    "$project_dir/scripts/tests/TurnipPolicyTest.java"
"${java_bin}java" -cp "$test_dir" TurnipPolicyTest "$project_dir"
