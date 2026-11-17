import com.ashmeet.hyperlauncher.utils.horizon.HorizonInstallCoordinator;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic async JNI contract checks against the production coordinator. */
public final class HorizonInstallCoordinatorTest {
    private static int checks;
    private static int scenarios;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void await(CountDownLatch gate) throws InterruptedException {
        if (!gate.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test gate");
    }
    private static void join(Thread worker) throws InterruptedException {
        worker.join(5000);
        check(!worker.isAlive(), "Worker must finish");
    }
    private static void run(String name, Action action) throws Exception {
        action.run();
        scenarios++;
        System.out.println("PASS " + name);
    }
    private static final class Result {
        final boolean success;
        final String message;
        Result(boolean success, String message) { this.success = success; this.message = message; }
    }
    private static final class Fixture {
        final AtomicBoolean active = new AtomicBoolean();
        final AtomicBoolean ready = new AtomicBoolean();
        final AtomicInteger reads = new AtomicInteger();
        final List<Result> results = new CopyOnWriteArrayList<>();
        HorizonInstallCoordinator begin() {
            return HorizonInstallCoordinator.tryStart(active,
                () -> { reads.incrementAndGet(); return ready.get(); },
                (success, message) -> results.add(new Result(success, message)));
        }
        void pending() {
            check(active.get(), "A live native worker must hold admission");
            check(results.isEmpty(), "Startup return must not finish installation");
            check(reads.get() == 0, "Resources must not be checked before completion");
            check(begin() == null, "A second native worker must not be admitted");
        }
        void ended(boolean success) {
            check(!active.get(), "Completed worker must release admission");
            check(results.size() == 1, "Completion must be delivered exactly once");
            check(results.get(0).success == success, "Wrong completion result");
        }
    }

    public static void main(String[] args) throws Exception {
        run("accepted startup waits for delayed native completion", () -> {
            Fixture f = new Fixture();
            HorizonInstallCoordinator install = f.begin();
            CountDownLatch callbackGate = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread nativeWorker = new Thread(() -> {
                try {
                    await(callbackGate);
                    f.ready.set(true);
                    install.onComplete(true, null);
                } catch (Throwable error) { failure.set(error); }
            });
            nativeWorker.start();
            try {
                install.onStartupReturned(true);
                f.pending(); // This is the first-launch ordering that failed in 2.1.0.
            } finally { callbackGate.countDown(); }
            join(nativeWorker);
            check(failure.get() == null, "Native callback worker failed");
            f.ended(true);
            check(f.reads.get() == 1, "Successful completion must verify once");
        });
        run("completion before JNI return waits for accepted startup", () -> {
            Fixture f = new Fixture();
            HorizonInstallCoordinator install = f.begin();
            f.ready.set(true);
            install.onComplete(true, null);
            f.pending();
            install.onStartupReturned(true);
            f.ended(true);
            check(f.reads.get() == 1, "Early completion verified once after startup");
        });
        run("startup rejection completes once and ignores late callbacks", () -> {
            Fixture f = new Fixture();
            HorizonInstallCoordinator install = f.begin();
            install.onStartupReturned(false);
            install.onComplete(true, null);
            install.onStartupReturned(true);
            f.ended(false);
            check(f.reads.get() == 0, "Rejected startup must not verify resources");
            check(f.results.get(0).message.contains("download worker"), "Startup failure must explain its stage");
            check(f.begin() != null, "Rejected startup permits retry");
        });
        run("native failure preserves cause and permits a fresh retry", () -> {
            Fixture f = new Fixture();
            HorizonInstallCoordinator install = f.begin();
            install.onStartupReturned(true);
            install.onComplete(false, "Download HTTP 503: resources unavailable");
            f.ended(false);
            check(f.results.get(0).message.equals("Download HTTP 503: resources unavailable"), "Native error was lost");
            check(f.reads.get() == 0, "Failed download must not verify readiness");
            HorizonInstallCoordinator retry = f.begin();
            check(retry != null, "Failure must allow retry");
            f.ready.set(true);
            retry.onStartupReturned(true);
            retry.onComplete(true, null);
            check(!f.active.get() && f.results.size() == 2 && f.results.get(1).success, "Retry must finish independently");
        });
        run("duplicate callbacks and startup results cannot overwrite completion", () -> {
            Fixture f = new Fixture();
            HorizonInstallCoordinator install = f.begin();
            install.onComplete(false, "First failure");
            install.onComplete(true, null);
            install.onStartupReturned(true);
            install.onComplete(false, "Second failure");
            install.onStartupFailed("Late error");
            f.ended(false);
            check("First failure".equals(f.results.get(0).message), "First terminal callback must win");
            check(f.reads.get() == 0, "Duplicate success must not trigger verification");
        });
        run("success callback still requires complete resources", () -> {
            Fixture f = new Fixture();
            HorizonInstallCoordinator install = f.begin();
            install.onStartupReturned(true);
            install.onComplete(true, null);
            f.ended(false);
            check(f.reads.get() == 1, "Readiness must be checked after callback");
            check(f.results.get(0).message.contains("required resources are still missing"), "Incomplete resources need specific feedback");
        });
        run("readiness errors are bounded and do not expose exception details", () -> {
            AtomicBoolean active = new AtomicBoolean();
            List<Result> results = new CopyOnWriteArrayList<>();
            HorizonInstallCoordinator install = HorizonInstallCoordinator.tryStart(active,
                () -> { throw new IOException("private-account-value"); },
                (success, message) -> results.add(new Result(success, message)));
            install.onStartupReturned(true);
            install.onComplete(true, null);
            check(!active.get(), "Verification failure releases admission");
            check(results.size() == 1 && !results.get(0).success, "Verification failure must finish once");
            check(results.get(0).message.contains("IOException"), "Verification failure should identify its type");
            check(!results.get(0).message.contains("private-account-value"), "Raw exception detail leaked");
        });
        run("admission remains held during readiness verification", () -> {
            AtomicBoolean active = new AtomicBoolean();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            List<Result> results = new CopyOnWriteArrayList<>();
            HorizonInstallCoordinator install = HorizonInstallCoordinator.tryStart(active,
                () -> { entered.countDown(); await(release); return true; },
                (success, message) -> results.add(new Result(success, message)));
            install.onStartupReturned(true);
            Thread callback = new Thread(() -> install.onComplete(true, null));
            callback.start();
            try {
                await(entered);
                check(active.get() && results.isEmpty(), "Readiness must finish before releasing admission");
                check(HorizonInstallCoordinator.tryStart(active, () -> true, (s, m) -> {}) == null,
                    "Concurrent verification must block a second installation");
                install.onComplete(false, "duplicate");
            } finally { release.countDown(); }
            join(callback);
            check(!active.get() && results.size() == 1 && results.get(0).success, "Verification completion must be final");
        });
        run("interrupting exited startup thread does not release native worker", () -> {
            Fixture f = new Fixture();
            HorizonInstallCoordinator install = f.begin();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread startup = new Thread(() -> {
                install.onStartupReturned(true);
                Thread.currentThread().interrupt();
                install.onStartupFailed("Interrupted after native worker started");
                interrupted.set(Thread.currentThread().isInterrupted());
            });
            startup.start();
            join(startup);
            check(interrupted.get(), "Startup thread interrupt flag must remain intact");
            f.pending();
            f.ready.set(true);
            install.onComplete(true, null);
            f.ended(true);
        });
        run("preparation failure and missing native error permit retry", () -> {
            Fixture f = new Fixture();
            HorizonInstallCoordinator install = f.begin();
            install.onStartupFailed("Insufficient storage to prepare engine data");
            f.ended(false);
            check(f.results.get(0).message.contains("Insufficient storage"), "Preparation cause must remain visible");
            HorizonInstallCoordinator retry = f.begin();
            check(retry != null, "Preparation failure permits retry");
            retry.onStartupReturned(true);
            retry.onComplete(false, "   ");
            check(f.results.size() == 2 && !f.results.get(1).success && !f.active.get(), "Retry failure must complete");
            check(f.results.get(1).message.contains("resource download failed"), "Blank callback requires useful fallback");
        });
        System.out.println("HorizonInstallCoordinatorTest: " + scenarios + " scenarios, " + checks + " checks passed");
    }
}
