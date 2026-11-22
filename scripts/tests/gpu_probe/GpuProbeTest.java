import android.opengl.*;
import com.ashmeet.hyperbackend.utils.GpuUtils;
import com.ashmeet.hyperbackend.utils.GLInfoUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class GpuProbeTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static EGLConfig add(int bits) { EGLConfig config = new EGLConfig(bits); EGL14.configs.add(config); return config; }
    public static void main(String[] args) throws Exception {
        String scenario = args[0];
        int expected = 3;
        final int ES2 = EGL14.EGL_OPENGL_ES2_BIT, ES3 = EGL14.EGL_OPENGL_ES3_BIT_KHR;
        EGLContext borrowed = EGL14.EGL_NO_CONTEXT;
        EGLSurface borrowedDraw = EGL14.EGL_NO_SURFACE, borrowedRead = EGL14.EGL_NO_SURFACE;
        switch (scenario) {
            case "later_config": add(ES2 | ES3).makeCurrent = false; add(ES2 | ES3); break;
            case "es3_only": add(ES3); break;
            case "depth16": add(ES2 | ES3).depth = 16; break;
            case "malformed_version": add(ES2 | ES3); GLES20.version = "driver version unavailable"; expected = 2; break;
            case "null_strings": add(ES2 | ES3); GLES20.version = null; GLES20.vendor = null; GLES20.renderer = null; expected = 2; break;
            case "blank_version": add(ES2 | ES3); GLES20.version = " "; expected = 2; break;
            case "null_identity": add(ES2 | ES3); GLES20.vendor = null; GLES20.renderer = null; break;
            case "null_shading": add(ES2 | ES3); GLES20.shadingVersion = null; expected = 2; break;
            case "blank_shading": add(ES2 | ES3); GLES20.shadingVersion = " "; expected = 2; break;
            case "lower_reported_version": add(ES2 | ES3); GLES20.version = "OpenGL ES 2.0"; expected = 2; break;
            case "es2_only": add(ES2).maxVersion = 2; expected = 2; break;
            case "all_fail": add(ES2 | ES3).makeCurrent = false; expected = 2; break;
            case "pbuffer_failure": add(ES3).pbuffer = false; add(ES2 | ES3); break;
            case "query_exception": add(ES3).throwQuery = true; add(ES2 | ES3); break;
            case "invalid_version_retry": add(ES3).nullVersion = true; add(ES2 | ES3); break;
            case "initialization_failure": add(ES2 | ES3); EGL14.initializeSucceeds = false; expected = 2; break;
            case "forced_msaa": add(ES2 | ES3).samples = 1; break;
            case "legacy_config": add(ES2); break;
            case "concurrent_cache": add(ES2 | ES3); break;
            case "shared_display": add(ES2 | ES3); EGL14.displayInitialized = true; break;
            case "restore_current":
                add(ES2 | ES3);
                borrowed = new EGLContext(new EGLConfig(ES2), 2);
                borrowedDraw = new EGLSurface(); borrowedRead = new EGLSurface();
                EGL14.currentDisplay = EGL14.display; EGL14.currentContext = borrowed;
                EGL14.currentDraw = borrowedDraw; EGL14.currentRead = borrowedRead;
                break;
            default: throw new AssertionError("Unknown scenario " + scenario);
        }
        if (scenario.equals("concurrent_cache")) {
            CountDownLatch start = new CountDownLatch(1);
            Thread[] threads = new Thread[8]; AtomicReference<Throwable> error = new AtomicReference<>();
            for (int i=0; i<threads.length; i++) {
                threads[i] = new Thread(() -> { try { start.await(); GpuUtils.getGlInfo(); } catch (Throwable e) { error.set(e); } });
                threads[i].start();
            }
            start.countDown(); for (Thread thread : threads) thread.join();
            if (error.get()!=null) throw new AssertionError(error.get());
        }
        GpuUtils.GLInfo info = GpuUtils.getGlInfo();
        check(info.glesMajorVersion == expected, scenario + ": expected GLES " + expected + ", got " + info.glesMajorVersion);
        check(info.vendor != null && info.renderer != null, "Identity must be non-null");
        info.isAdreno(); info.isAdreno500Lower(); info.isArm();
        check(EGL14.createdContexts.isEmpty(), "Probe leaked contexts");
        check(EGL14.createdSurfaces.isEmpty(), "Probe leaked surfaces");
        check(EGL14.currentContext == borrowed, "Caller context was not restored");
        check(EGL14.currentDraw == borrowedDraw && EGL14.currentRead == borrowedRead, "Caller surfaces were not restored");
        check(EGL14.currentDisplay == (borrowed == EGL14.EGL_NO_CONTEXT ? EGL14.EGL_NO_DISPLAY : EGL14.display), "Caller display was not restored");
        if (scenario.equals("restore_current") || scenario.equals("shared_display")) {
            check(EGL14.terminateCalls == 0, "Borrowed display was terminated");
            check(EGL14.initializeCalls == 0, "Borrowed display was reinitialized");
        }
        else check(EGL14.terminateCalls == (EGL14.initializeSucceeds ? 1 : 0), "Display ownership cleanup mismatch");
        int attempts = EGL14.contextAttempts;
        check(GpuUtils.getGlInfo() == info, "Probe cache identity not reused");
        GLInfoUtils.GLInfo legacy = GLInfoUtils.getGlInfo();
        check(legacy.glesMajorVersion == info.glesMajorVersion && legacy.vendor.equals(info.vendor), "Legacy GPU info disagrees");
        check(EGL14.contextAttempts == attempts, "Repeated/legacy access probed again");
        if (scenario.equals("concurrent_cache")) check(EGL14.initializeCalls == 1, "Concurrent callers initialized more than once");
        if (scenario.equals("forced_msaa")) check(info.forcedMsaa, "Selected MSAA config was not reported");
        if (scenario.equals("null_strings")) check(!info.isAdreno() && !info.isArm(), "Unknown vendor incorrectly qualified");
        System.out.println("PASS " + scenario + " (" + checks + " checks)");
    }
}
