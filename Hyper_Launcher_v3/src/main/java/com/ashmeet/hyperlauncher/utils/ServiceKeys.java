package com.ashmeet.hyperlauncher.utils;

import java.util.Locale;

/** Credential selection shared by both catalog and modpack clients. Never logs values. */
public final class ServiceKeys {
    public static final String CURSEFORGE_OFFICIAL = "https://api.curseforge.com/v1";
    public static final String CURSEFORGE_MIRROR = "https://mod.mcimirror.top/curseforge/v1";
    private ServiceKeys() { }

    public static String normalize(String value) {
        if (value == null) return "";
        String key = value.trim();
        String upper = key.toUpperCase(Locale.ROOT);
        String marker = upper.replaceAll("[^A-Z0-9]", "");
        if (key.isEmpty() || marker.equals("DUMMY") || marker.equals("DUMMYKEY") || upper.contains("PLACEHOLDER")
                || (marker.startsWith("YOUR") && (marker.endsWith("KEY") || marker.endsWith("KEYHERE")))
                || marker.equals("CHANGEME") || marker.equals("REPLACEME")
                || marker.equals("NULL") || marker.equals("UNDEFINED") || marker.equals("TODO")) return "";
        for (int i = 0; i < key.length(); i++) if (Character.isISOControl(key.charAt(i))) return "";
        return key;
    }

    public static String resolve(String custom, String bundled) {
        String key = normalize(custom);
        return key.isEmpty() ? normalize(bundled) : key;
    }

    public static String curseForgeEndpoint(String key) {
        return normalize(key).isEmpty() ? CURSEFORGE_MIRROR : CURSEFORGE_OFFICIAL;
    }

    public static String curseForgeLabel(String key) {
        return normalize(key).isEmpty() ? "CurseForge mirror" : "CurseForge";
    }

    public static String curseForgeUnavailable(String key) {
        return normalize(key).isEmpty()
                ? "CurseForge mirror is unavailable. Refresh, use Modrinth, or add an official CurseForge API key in Settings > Miscellaneous."
                : "CurseForge is unavailable. Check your API key in Settings > Miscellaneous and your connection, then refresh.";
    }
}
