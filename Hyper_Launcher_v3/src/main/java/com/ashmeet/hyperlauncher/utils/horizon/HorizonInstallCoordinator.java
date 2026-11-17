package com.ashmeet.hyperlauncher.utils.horizon;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates the native installer's asynchronous startup/completion contract.
 * A true startup return only means the detached native worker was accepted.
 * The admission guard remains held until that worker reports completion.
 */
public final class HorizonInstallCoordinator {
    public interface Readiness { boolean isReady() throws Exception; }
    public interface Completion { void finish(boolean success, String message); }

    private final AtomicBoolean installing;
    private final Readiness readiness;
    private final Completion completion;
    private boolean startupReturned;
    private boolean accepted;
    private boolean callbackReceived;
    private boolean callbackSuccess;
    private String callbackMessage;
    private String startupMessage;
    private boolean resolved;

    private HorizonInstallCoordinator(AtomicBoolean installing, Readiness readiness,
                                      Completion completion) {
        this.installing = installing;
        this.readiness = readiness;
        this.completion = completion;
    }

    /** Returns null while an earlier native installation or readiness check is active. */
    public static HorizonInstallCoordinator tryStart(AtomicBoolean installing,
                                                     Readiness readiness,
                                                     Completion completion) {
        if (installing == null || readiness == null || completion == null) {
            throw new NullPointerException("Install collaborators are required");
        }
        if (!installing.compareAndSet(false, true)) return null;
        return new HorizonInstallCoordinator(installing, readiness, completion);
    }

    public void onStartupReturned(boolean accepted) {
        startupFinished(accepted, "The Rust installer could not start its download worker. Reopen the launcher and try again.");
    }

    /** For preparation/startup errors, never for cancelling an accepted native worker. */
    public void onStartupFailed(String message) {
        startupFinished(false, message);
    }

    private void startupFinished(boolean wasAccepted, String message) {
        synchronized (this) {
            if (resolved || startupReturned) return;
            startupReturned = true;
            accepted = wasAccepted;
            startupMessage = message;
        }
        resolveIfPossible();
    }

    public void onComplete(boolean success, String message) {
        synchronized (this) {
            if (resolved || callbackReceived) return;
            callbackReceived = true;
            callbackSuccess = success;
            callbackMessage = message;
        }
        resolveIfPossible();
    }

    private void resolveIfPossible() {
        boolean success;
        String message;
        synchronized (this) {
            if (resolved || !startupReturned || (accepted && !callbackReceived)) return;
            resolved = true;
            success = accepted && callbackSuccess;
            message = accepted ? callbackMessage : startupMessage;
        }
        // Do not hold the monitor across native I/O. The admission guard remains held.
        if (success) {
            try {
                success = readiness.isReady();
                if (!success) message = "The Rust installer finished, but required resources are still missing. Retry setup.";
            } catch (Exception | LinkageError error) {
                success = false;
                message = "Rust resources could not be verified (" + error.getClass().getSimpleName() + "). Reopen the launcher and retry setup.";
            }
        }
        if (!success && (message == null || message.trim().isEmpty())) {
            message = "The Rust resource download failed. Check your connection and available storage, then retry setup.";
        }
        installing.set(false);
        completion.finish(success, message);
    }
}
