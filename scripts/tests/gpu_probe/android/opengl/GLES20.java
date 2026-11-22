package android.opengl;
public class GLES20 {
    public static final int GL_VENDOR = 0x1F00, GL_RENDERER = 0x1F01, GL_VERSION = 0x1F02;
    public static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;
    public static String vendor = "ARM", renderer = "Mali-G68 MC4", version = "OpenGL ES 3.2 test";
    public static String shadingVersion = "OpenGL ES GLSL ES 3.20";
    public static String glGetString(int name) {
        if (EGL14.currentContext == EGL14.EGL_NO_CONTEXT) throw new AssertionError("No current context");
        if (EGL14.currentContext.config.throwQuery) throw new IllegalStateException("Driver query failed");
        if (name == GL_VENDOR) return vendor;
        if (name == GL_RENDERER) return renderer;
        if (name == GL_SHADING_LANGUAGE_VERSION) return shadingVersion;
        if (EGL14.currentContext.config.nullVersion) return null;
        return version;
    }
}
