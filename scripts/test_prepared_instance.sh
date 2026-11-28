#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
gson_jar="$project_dir/Hyper_Launcher_v3/libs/gson-2.8.6.jar"
commons_jar="$project_dir/Hyper_Launcher_v3/libs/ExagearApacheCommons.jar"
# Stub only Android UI/preferences boundaries; exercise real instance metadata,
# enumeration, JSON serialization and filesystem cleanup implementations.
python3 - "$test_dir" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1]) / 'stubs'
sources = {
'android/graphics/Bitmap.java': '''package android.graphics;
public class Bitmap { public enum CompressFormat { WEBP, WEBP_LOSSY }
public boolean compress(CompressFormat f, int q, java.io.OutputStream o) { return true; } }''',
'android/os/Build.java': '''package android.os;
public class Build { public static class VERSION { public static final int SDK_INT=30; }
public static class VERSION_CODES { public static final int R=30; } }''',
'android/util/Log.java': '''package android.util;
public class Log { public static int e(String t, String m, Throwable e) { return 0; } }''',
'com/ashmeet/hyperlauncher/utils/HyperUtils.java': '''package com.ashmeet.hyperlauncher.utils;
public class HyperUtils {
public static final String DIR_GAME_HOME = System.getProperty("prepared.test.root");
public static final String CTRLMAP_PATH = "controls";
public static final com.google.gson.Gson GLOBAL_GSON = new com.google.gson.Gson();
public static boolean isValidString(String value) { return value != null && !value.isEmpty(); }
}''',
'com/ashmeet/hyperlauncher/screens/settings/preferences/LauncherPreferences.java': '''package com.ashmeet.hyperlauncher.screens.settings.preferences;
public class LauncherPreferences {
public static final String PREF_KEY_CURRENT_INSTANCE="instance", PREF_RENDERER="renderer",
PREF_CUSTOM_JAVA_ARGS="", PREF_DEFAULTCTRL_PATH="";
public static final Prefs DEFAULT_PREF = new Prefs();
public static Prefs getPrefs() { return DEFAULT_PREF; }
public static class Prefs {
private final java.util.Map<String,String> values = new java.util.HashMap<>();
public String getString(String key,String fallback) { return values.getOrDefault(key,fallback); }
public Prefs edit() { return this; }
public Prefs putString(String key,String value) { values.put(key,value); return this; }
public void apply() { }
} }''',
'com/ashmeet/hyperbackend/instances/InstanceInstaller.java': '''package com.ashmeet.hyperbackend.instances;
public class InstanceInstaller { }''',
'com/ashmeet/hyperbackend/instances/InstanceIconProvider.java': '''package com.ashmeet.hyperbackend.instances;
public class InstanceIconProvider {
public static final String FALLBACK_ICON_NAME="default";
public static boolean hasStaticIcon(String icon) { return "default".equals(icon); }
}''',
}
for name, text in sources.items():
    path=root/name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
PY
mapfile -t fixture_sources < <(find "$test_dir/stubs" -name '*.java' -print)
sources_dir="$project_dir/Hyper_Launcher_v3/src/main/java/com/ashmeet/hyperbackend"
instances_source="${PREPARED_INSTANCE_SOURCE:-$sources_dir/instances/Instances.java}"
"${java_bin}javac" --release 8 -cp "$gson_jar:$commons_jar" -d "$test_dir/classes" \
    "${fixture_sources[@]}" \
    "$instances_source" "$sources_dir/instances/Instance.java" \
    "$sources_dir/instances/DisplayInstance.java" "$sources_dir/instances/InstanceSetter.java" \
    "$sources_dir/utils/JSONUtils.java" "$sources_dir/utils/FileUtils.java" \
    "$project_dir/scripts/tests/PreparedInstanceTest.java"
"${java_bin}java" -Dprepared.test.root="$test_dir/game" \
    -cp "$test_dir/classes:$gson_jar:$commons_jar" \
    com.ashmeet.hyperbackend.instances.PreparedInstanceTest
