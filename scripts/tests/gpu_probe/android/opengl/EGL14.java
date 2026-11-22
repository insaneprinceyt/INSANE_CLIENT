package android.opengl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fake only at Android's EGL boundary; production probe control flow is exercised. */
public class EGL14 {
    public static final int EGL_DEFAULT_DISPLAY = 0, EGL_NONE = 0x3038;
    public static final int EGL_BLUE_SIZE = 0x3022, EGL_GREEN_SIZE = 0x3023, EGL_RED_SIZE = 0x3024;
    public static final int EGL_ALPHA_SIZE = 0x3021, EGL_DEPTH_SIZE = 0x3025, EGL_SURFACE_TYPE = 0x3033;
    public static final int EGL_PBUFFER_BIT = 1, EGL_RENDERABLE_TYPE = 0x3040, EGL_OPENGL_ES2_BIT = 4;
    public static final int EGL_OPENGL_ES3_BIT_KHR = 0x40, EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    public static final int EGL_WIDTH = 0x3057, EGL_HEIGHT = 0x3056, EGL_SAMPLE_BUFFERS = 0x3032;
    public static final int EGL_DRAW = 0x3059, EGL_READ = 0x305A;
    public static final int EGL_VERSION = 0x3054;
    public static final EGLDisplay EGL_NO_DISPLAY = new EGLDisplay(), display = new EGLDisplay();
    public static final EGLContext EGL_NO_CONTEXT = new EGLContext(null, 0);
    public static final EGLSurface EGL_NO_SURFACE = new EGLSurface();
    public static EGLDisplay currentDisplay = EGL_NO_DISPLAY;
    public static EGLContext currentContext = EGL_NO_CONTEXT;
    public static EGLSurface currentDraw = EGL_NO_SURFACE, currentRead = EGL_NO_SURFACE;
    public static final List<EGLConfig> configs = new ArrayList<>();
    public static final Set<EGLContext> createdContexts = new HashSet<>();
    public static final Set<EGLSurface> createdSurfaces = new HashSet<>();
    public static int initializeCalls, terminateCalls, contextAttempts;
    public static boolean initializeSucceeds = true, displayInitialized = false;

    public static EGLDisplay eglGetDisplay(int id) { return display; }
    public static EGLDisplay eglGetCurrentDisplay() { return currentDisplay; }
    public static EGLContext eglGetCurrentContext() { return currentContext; }
    public static EGLSurface eglGetCurrentSurface(int which) { return which == EGL_DRAW ? currentDraw : currentRead; }
    public static String eglQueryString(EGLDisplay d, int name) { return displayInitialized ? "1.5" : null; }
    public static int eglGetError() { return 0x3000; }
    public static boolean eglInitialize(EGLDisplay d, int[] major, int m, int[] minor, int n) {
        initializeCalls++; major[m] = 1; minor[n] = 5; displayInitialized = initializeSucceeds; return initializeSucceeds;
    }
    public static boolean eglTerminate(EGLDisplay d) {
        if (currentDisplay == d && currentContext != EGL_NO_CONTEXT) throw new AssertionError("Terminated a current display");
        terminateCalls++; displayInitialized = false; return true;
    }
    private static int attribute(int[] attrs, int key, int fallback) {
        for (int i = 0; i < attrs.length && attrs[i] != EGL_NONE; i += 2) if (attrs[i] == key) return attrs[i + 1];
        return fallback;
    }
    public static boolean eglChooseConfig(EGLDisplay d, int[] attrs, int offset, EGLConfig[] out, int outOffset,
                                          int size, int[] count, int countOffset) {
        int bits = attribute(attrs, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT);
        List<EGLConfig> matches = new ArrayList<>();
        for (EGLConfig config : configs) {
            if ((config.bits & bits) == bits && config.depth >= attribute(attrs, EGL_DEPTH_SIZE, 0)) matches.add(config);
        }
        count[countOffset] = out == null ? matches.size() : Math.min(matches.size(), size);
        if (out != null) for (int i = 0; i < count[countOffset]; i++) out[outOffset + i] = matches.get(i);
        return true;
    }
    public static EGLContext eglCreateContext(EGLDisplay d, EGLConfig config, EGLContext share, int[] attrs, int offset) {
        contextAttempts++;
        int requested = attribute(attrs, EGL_CONTEXT_CLIENT_VERSION, 1);
        if (requested > config.maxVersion) return EGL_NO_CONTEXT;
        EGLContext context = new EGLContext(config, requested); createdContexts.add(context); return context;
    }
    public static EGLSurface eglCreatePbufferSurface(EGLDisplay d, EGLConfig config, int[] attrs, int offset) {
        if (!config.pbuffer) return EGL_NO_SURFACE;
        EGLSurface surface = new EGLSurface(); createdSurfaces.add(surface); return surface;
    }
    public static boolean eglMakeCurrent(EGLDisplay d, EGLSurface draw, EGLSurface read, EGLContext context) {
        if (context != EGL_NO_CONTEXT && !context.config.makeCurrent) return false;
        currentContext = context; currentDraw = draw; currentRead = read;
        currentDisplay = context == EGL_NO_CONTEXT ? EGL_NO_DISPLAY : d;
        return true;
    }
    public static boolean eglDestroyContext(EGLDisplay d, EGLContext context) {
        if (currentContext == context) throw new AssertionError("Destroyed a current context");
        if (!createdContexts.remove(context)) throw new AssertionError("Destroyed borrowed/unowned context");
        return true;
    }
    public static boolean eglDestroySurface(EGLDisplay d, EGLSurface surface) {
        if (currentRead == surface || currentDraw == surface) throw new AssertionError("Destroyed a current surface");
        if (!createdSurfaces.remove(surface)) throw new AssertionError("Destroyed borrowed/unowned surface");
        return true;
    }
    public static boolean eglGetConfigAttrib(EGLDisplay d, EGLConfig config, int attr, int[] value, int offset) {
        value[offset] = attr == EGL_SAMPLE_BUFFERS ? config.samples : 0; return true;
    }
}
