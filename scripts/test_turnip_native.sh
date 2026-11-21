#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
compiler="${CC:-cc}"
source_dir="${TURNIP_SOURCE_DIR:-$project_dir/mojoexec}"
fixtures="$project_dir/scripts/tests/turnip_native"
"$compiler" -std=c11 -Wall -Wextra -Werror -pthread -I"$fixtures/include" \
    "$source_dir/driver_helper/hook.c" "$fixtures/HookTest.c" -o "$test_dir/hook-test"
for scenario in uninitialized null-filename override-and-forward namespace-and-zero-init missing-namespace inactive-forwarding partial-callbacks; do
    "$test_dir/hook-test" "$scenario"
done
"$compiler" -std=c11 -Wall -Wextra -Werror -pthread -DENABLE_TURNIP_LOADER \
    -I"$fixtures/include" -I"$source_dir" -I"$source_dir/include" \
    "$source_dir/driver_helper/hook.c" "$fixtures/LoaderTest.c" -o "$test_dir/loader-test"
for scenario in disabled api27 missing-cache state-transitions concurrent 1 2 3 4 5 6 7; do
    "$test_dir/loader-test" "$scenario"
done
printf '%s\n' 'Turnip native: 7 hook scenarios and 12 loader scenarios passed'
