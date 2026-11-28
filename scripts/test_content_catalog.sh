#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
source_file="${CONTENT_UTILS_SOURCE:-$project_dir/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperlauncher/utils/installer/InstallerUtils.kt}"
test_file="$project_dir/scripts/tests/ContentCatalogTest.kt"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
gson_jar="$project_dir/Hyper_Launcher_v3/libs/gson-2.8.6.jar"
cache_dir="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
coroutines_jar="$(python3 - "$cache_dir" <<'PY'
import pathlib, sys
matches = sorted((pathlib.Path(sys.argv[1]) / 'org.jetbrains.kotlinx' / 'kotlinx-coroutines-core-jvm').glob('*/*/*.jar'), reverse=True)
if not matches: sys.exit('A Kotlin coroutines dependency is required; populate the Gradle cache first.')
print(matches[0])
PY
)"
# Reuse the production model data classes without their Android-only icons/Bitmap.
python3 - "$project_dir" "$test_dir/Models.kt" <<'PY'
import pathlib, sys
text = (pathlib.Path(sys.argv[1]) / 'Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperlauncher/utils/installer/InstallerModels.kt').read_text()
models = text[text.index('data class ModrinthProject('):]
pathlib.Path(sys.argv[2]).write_text('package com.ashmeet.hyperlauncher.utils.installer\nclass Bitmap\nenum class ContentInstallerType { MODS, MODPACKS, RESOURCEPACKS, SHADERS, WORLDS }\n' + models)
PY
api_sources=("$project_dir/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperbackend/modloaders/modpacks/api/ModrinthService.kt" "$test_dir/Models.kt" "$project_dir/scripts/tests/ContentCatalogApiBoundary.kt" "$project_dir/scripts/tests/ContentCatalogApiTest.kt")
if command -v kotlinc >/dev/null 2>&1; then
    kotlinc "$source_file" "$test_file" "${api_sources[@]}" -cp "$gson_jar:$coroutines_jar" -include-runtime -d "$test_dir/tests.jar"
    tests_cp="$test_dir/tests.jar:$gson_jar:$coroutines_jar"
else
    compiler_cp="$(python3 - "$cache_dir" <<'PY'
import pathlib, sys
root = pathlib.Path(sys.argv[1])
artifacts = [
    ('org.jetbrains.kotlin', 'kotlin-compiler-embeddable'),
    ('org.jetbrains.kotlin', 'kotlin-stdlib'),
    ('org.jetbrains.kotlin', 'kotlin-script-runtime'),
    ('org.jetbrains.kotlin', 'kotlin-daemon-embeddable'),
    ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm'),
    ('org.jetbrains', 'annotations'),
]
jars = []
for group, artifact in artifacts:
    matches = sorted((root / group / artifact).glob('*/*/*.jar'), reverse=True)
    if not matches:
        sys.exit('Install kotlinc or set GRADLE_USER_HOME to a populated Kotlin compiler cache.')
    jars.append(str(matches[0]))
print(':'.join(jars))
PY
)"
    "${java_bin}java" -cp "$compiler_cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
        -no-stdlib -no-reflect -classpath "$compiler_cp:$gson_jar" \
        -d "$test_dir/classes" "$source_file" "$test_file" "${api_sources[@]}"
    tests_cp="$test_dir/classes:$compiler_cp:$gson_jar"
fi
"${java_bin}java" -cp "$tests_cp" com.ashmeet.hyperlauncher.utils.installer.ContentCatalogTestKt
"${java_bin}java" -cp "$tests_cp" com.ashmeet.hyperlauncher.utils.installer.ContentCatalogApiTestKt
