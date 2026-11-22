package android.opengl;
public class EGLContext {
    public final EGLConfig config;
    public final int version;
    public EGLContext(EGLConfig config, int version) { this.config = config; this.version = version; }
}
