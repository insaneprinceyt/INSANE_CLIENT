package android.opengl;
public class EGLConfig {
    public final int bits;
    public int maxVersion = 3, depth = 24, samples = 0;
    public boolean pbuffer = true, makeCurrent = true, throwQuery = false, nullVersion = false;
    public EGLConfig(int bits) { this.bits = bits; }
}
