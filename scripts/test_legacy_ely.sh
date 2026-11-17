#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
gson_jar="$project_dir/Hyper_Launcher_v3/libs/gson-2.8.6.jar"
source_dir="$project_dir/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperbackend/authenticator/legacy"
"$javac_bin" -source 8 -target 8 -cp "$gson_jar" -d "$test_dir" \
    "$source_dir/ElyAuthApi.java" "$source_dir/LegacyElyAccountStore.java" \
    "$project_dir/scripts/tests/ElyAuthApiTest.java" "$project_dir/scripts/tests/LegacyElyAccountStoreTest.java"
"$java_bin" -cp "$test_dir:$gson_jar" com.ashmeet.hyperbackend.authenticator.legacy.ElyAuthApiTest
"$java_bin" -cp "$test_dir:$gson_jar" com.ashmeet.hyperbackend.authenticator.legacy.LegacyElyAccountStoreTest
