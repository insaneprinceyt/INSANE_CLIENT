#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
source_dir="${GPU_PROBE_SOURCE_DIR:-$project_dir/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperbackend/utils}"
mapfile -t stubs < <(find "$project_dir/scripts/tests/gpu_probe" -name '*.java' -print | sort)
"${java_bin}javac" --release 8 -d "$test_dir" \
    "$source_dir/GpuUtils.java" "$source_dir/GLInfoUtils.java" "${stubs[@]}"
scenarios=(later_config es3_only depth16 malformed_version null_strings blank_version null_identity null_shading blank_shading lower_reported_version es2_only all_fail pbuffer_failure query_exception invalid_version_retry restore_current shared_display initialization_failure concurrent_cache forced_msaa legacy_config)
if (($#)); then scenarios=("$@"); fi
for scenario in "${scenarios[@]}"; do
    "${java_bin}java" -cp "$test_dir" GpuProbeTest "$scenario"
done
