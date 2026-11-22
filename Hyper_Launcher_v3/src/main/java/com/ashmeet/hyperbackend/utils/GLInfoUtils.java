package com.ashmeet.hyperbackend.utils;

/**
 * Legacy API for callers predating GpuUtils. Both APIs share the same EGL probe,
 * so renderer availability, game options and diagnostics cannot disagree.
 */
public class GLInfoUtils {
    public static String GLES_VERSION_PREFIX = "OpenGL ES ";
    private static GLInfo info;

    public static synchronized GLInfo getGlInfo() {
        if (info == null) {
            GpuUtils.GLInfo current = GpuUtils.getGlInfo();
            info = new GLInfo(current.vendor, current.renderer, current.glesMajorVersion, current.forcedMsaa);
        }
        return info;
    }

    public static class GLInfo extends GpuUtils.GLInfo {
        protected GLInfo(String vendor, String renderer, int glesMajorVersion, boolean forcedMsaa) {
            super(vendor, renderer, glesMajorVersion, forcedMsaa);
        }
    }
}
