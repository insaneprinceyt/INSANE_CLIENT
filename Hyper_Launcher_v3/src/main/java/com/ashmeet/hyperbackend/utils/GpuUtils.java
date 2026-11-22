package com.ashmeet.hyperbackend.utils;

import static android.os.Build.VERSION.SDK_INT;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Build;
import android.util.Log;

public class GpuUtils {
    public static String GLES_VERSION_PREFIX = "OpenGL ES ";
    private static GLInfo info;

    // EGL_OPENGL_ES3_BIT_KHR is available before the EGL 1.5 Java bindings.
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;
    private static final String TAG = "GLInfoUtils";
    private static final java.util.regex.Pattern GLES_VERSION = java.util.regex.Pattern.compile(
            "^OpenGL ES ([1-9][0-9]*)\\.[0-9]+(?:\\s|$)");

    private static int getMajorGLVersion(String versionString) {
        if (versionString == null) return 0;
        java.util.regex.Matcher match = GLES_VERSION.matcher(versionString.trim());
        if (!match.find()) return 0;
        try {
            return Integer.parseInt(match.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static GLInfo queryInfo(int contextGLVersion, boolean forcedMsaa) {
        String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
        int reportedVersion = getMajorGLVersion(GLES20.glGetString(GLES30.GL_VERSION));
        // LTW also reads these strings during native initialization. Do not
        // advertise ES3 when queries that its native loader needs are unusable.
        if (reportedVersion == 0) return null;
        if (contextGLVersion >= 3) {
            String shadingVersion = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION);
            if (shadingVersion == null || shadingVersion.trim().isEmpty()) return null;
        }
        int version = Math.min(reportedVersion, contextGLVersion);
        return new GLInfo(vendor, renderer, version, forcedMsaa);
    }

    private static boolean hasDisplay(EGLDisplay display) {
        return display != null && !EGL14.EGL_NO_DISPLAY.equals(display);
    }

    private static boolean hasContext(EGLContext context) {
        return context != null && !EGL14.EGL_NO_CONTEXT.equals(context);
    }

    private static boolean hasSurface(EGLSurface surface) {
        return surface != null && !EGL14.EGL_NO_SURFACE.equals(surface);
    }

    private static boolean isInitialized(EGLDisplay display) {
        if (EGL14.eglQueryString(display, EGL14.EGL_VERSION) != null) return true;
        // Querying an uninitialized display sets EGL_NOT_INITIALIZED. Consume
        // that expected probe error before proceeding with initialization.
        EGL14.eglGetError();
        return false;
    }

    /** State belonging to the calling thread, never destroyed by the probe. */
    private static final class CurrentState {
        final EGLDisplay display = EGL14.eglGetCurrentDisplay();
        final EGLContext context = EGL14.eglGetCurrentContext();
        final EGLSurface draw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        final EGLSurface read = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);

        void restore(EGLDisplay probeDisplay) {
            boolean restored;
            if (hasDisplay(display) && hasContext(context)) {
                restored = EGL14.eglMakeCurrent(display, draw, read, context);
            } else {
                restored = EGL14.eglMakeCurrent(probeDisplay, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            }
            if (!restored) {
                // At least detach the probe before destroying its resources. Never
                // destroy the caller's context or surfaces to recover a driver error.
                EGL14.eglMakeCurrent(probeDisplay, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                throw new IllegalStateException("Could not restore EGL state after graphics probe");
            }
        }
    }

    private static boolean isMSAAConfig(EGLDisplay display, EGLConfig config) {
        int[] sampleBuffers = {0};
        return EGL14.eglGetConfigAttrib(display, config, EGL14.EGL_SAMPLE_BUFFERS, sampleBuffers, 0)
                && sampleBuffers[0] != 0;
    }

    private static GLInfo tryConfig(EGLDisplay display, EGLConfig config, int majorVersion,
                                    CurrentState previous) {
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        boolean madeCurrent = false;
        try {
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                    new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, majorVersion, EGL14.EGL_NONE}, 0);
            if (!hasContext(context)) return null;
            // A small pbuffer works on devices that do not support surfaceless EGL.
            surface = EGL14.eglCreatePbufferSurface(display, config,
                    new int[] {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (!hasSurface(surface)) return null;
            // Some older Mali drivers accept context creation but reject makeCurrent.
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null;
            madeCurrent = true;
            return queryInfo(majorVersion, isMSAAConfig(display, config));
        } catch (RuntimeException error) {
            Log.w(TAG, "EGL config failed during GLES " + majorVersion + " probe", error);
            return null;
        } finally {
            try {
                if (madeCurrent) previous.restore(display);
            } finally {
                try {
                    if (hasContext(context)) EGL14.eglDestroyContext(display, context);
                } finally {
                    if (hasSurface(surface)) EGL14.eglDestroySurface(display, surface);
                }
            }
        }
    }

    private static GLInfo tryConfigs(EGLDisplay display, int renderableBit, int majorVersion,
                                     CurrentState previous) {
        // No depth/stencil or RGBA8 requirements: these are information-query
        // pbuffers, not Minecraft's render targets. A depth16/RGB565 config is valid.
        int[] attributes = {EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, renderableBit, EGL14.EGL_NONE};
        int[] count = {0};
        if (!EGL14.eglChooseConfig(display, attributes, 0, null, 0, 0, count, 0)
                || count[0] <= 0) return null;
        EGLConfig[] configs = new EGLConfig[count[0]];
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, configs.length, count, 0)) return null;
        for (int i = 0; i < Math.min(count[0], configs.length); i++) {
            if (configs[i] == null) continue;
            GLInfo result = tryConfig(display, configs[i], majorVersion, previous);
            if (result != null && result.glesMajorVersion >= majorVersion) return result;
        }
        return null;
    }

    private static GLInfo initAndQueryInfo() {
        CurrentState previous = new CurrentState();
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (!hasDisplay(display)) return null;
        // eglInitialize is not a per-call reference count. Borrow an already
        // initialized display, including one used by Android's UI render thread.
        boolean borrowedDisplay = (display.equals(previous.display) && hasContext(previous.context))
                || isInitialized(display);
        boolean initialized = false;
        try {
            if (!borrowedDisplay) {
                int[] version = new int[2];
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null;
                initialized = true;
            }
            GLInfo result = tryConfigs(display, EGL_OPENGL_ES3_BIT_KHR, 3, previous);
            // Older drivers may omit the ES3 config bit despite supporting an
            // actual ES3 context. Keep the legacy route, but try every config.
            if (result == null) result = tryConfigs(display, EGL14.EGL_OPENGL_ES2_BIT, 3, previous);
            if (result == null) result = tryConfigs(display, EGL14.EGL_OPENGL_ES2_BIT, 2, previous);
            return result;
        } finally {
            if (initialized) EGL14.eglTerminate(display);
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    public static boolean checkVulkanSupport(PackageManager packageManager) {
        if(SDK_INT >= Build.VERSION_CODES.N) {
            return packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
        }
        return false;
    }

    public static boolean checkChromebook(PackageManager packageManager) {
        return packageManager.hasSystemFeature("org.chromium.arc")
                || packageManager.hasSystemFeature("org.chromium.arc.device_management")
                || (Build.MODEL != null && Build.MODEL.startsWith("sdk_gpc_"));
    }

    /**
     * Get the information about the current OpenGL ES device, which consists of the vendor,
     * the renderer and the major GLES version
     * @return the info
     */
    public static synchronized GLInfo getGlInfo() {
        if (info != null) return info;
        Log.i(TAG, "Querying graphics device info...");
        try {
            info = initAndQueryInfo();
        } catch (RuntimeException | LinkageError error) {
            Log.e(TAG, "Failed to query graphics device info", error);
        }
        if (info == null) {
            Log.e(TAG, "No usable EGL context found; assuming GLES 2 for compatibility checks");
            info = new GLInfo("<Unknown>", "<Unknown>", 2, false);
        }
        return info;
    }

    public static class GLInfo {
        public final String vendor;
        public final String renderer;
        public final int glesMajorVersion;
        public final boolean forcedMsaa;
        protected GLInfo(String vendor, String renderer, int glesMajorVersion, boolean forcedMsaa) {
            this.vendor = vendor == null || vendor.trim().isEmpty() ? "<Unknown>" : vendor;
            this.renderer = renderer == null || renderer.trim().isEmpty() ? "<Unknown>" : renderer;
            this.glesMajorVersion = glesMajorVersion;
            this.forcedMsaa = forcedMsaa;
        }

        /**
         * Check if this GLInfo belongs to a Qualcomm Adreno graphics adapter
         * @return
         */
        public boolean isAdreno() {
            return renderer.contains("Adreno") && vendor.contains("Qualcomm");
        }

        /**
         * Check if this GLInfo belongs to a Qualcomm Adreno 200/300/400/500 graphics adapter
         * @return
         */
        public boolean isAdreno500Lower(){
            return vendor.contains("Qualcomm") &&
                    (renderer.contains("Adreno (TM) 5") ||
                    renderer.contains("Adreno (TM) 4") ||
                    renderer.contains("Adreno (TM) 3") ||
                    renderer.contains("Adreno (TM) 2"));
        }

        /**
         * Check if this GLInfo belongs to a ARM Mali/Immortalis graphics adapter
         * @return
         */
        public boolean isArm() {
            return (renderer.contains("Mali") || renderer.contains("Immortalis")) && vendor.equals("ARM");
        }
    }
}
