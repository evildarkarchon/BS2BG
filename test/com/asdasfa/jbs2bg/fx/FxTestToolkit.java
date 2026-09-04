package com.asdasfa.jbs2bg.fx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;

/**
 * Public-JavaFX test harness: starts the toolkit once through
 * {@link Platform#startup(Runnable)} and runs test bodies on the JavaFX
 * Application Thread with a hard timeout, so a hung toolkit call fails the
 * test instead of hanging the build. Nothing here touches skin or toolkit
 * internals.
 */
public final class FxTestToolkit {

    /**
     * Upper bound for any single FX-thread test body; generous for a cold toolkit.
     */
    public static final long TIMEOUT_SECONDS = 30;

    private static volatile boolean started;

    private FxTestToolkit() {
    }

    /**
     * Starts the JavaFX toolkit if this JVM has not started it yet. Idempotent
     * across test classes: a second {@code Platform.startup} would throw, so an
     * already-running toolkit is detected and reused.
     */
    public static synchronized void ensureStarted() {
        if (started)
            return;
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            // Another test class started the toolkit in this JVM; it is still running.
            ready.countDown();
        }
        await(ready, "JavaFX toolkit startup");
        // Closing the last window must not shut the toolkit down between tests.
        Platform.setImplicitExit(false);
        started = true;
    }

    /**
     * Runs the body on the JavaFX Application Thread and rethrows whatever it
     * throws, so JUnit assertions inside the body fail the calling test.
     *
     * @param body test body that may throw
     * @throws AssertionError when the body times out
     */
    public static void runOnFxThread(FxBody body) throws Exception {
        ensureStarted();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        await(done, "JavaFX test body");
        Throwable thrown = failure.get();
        if (thrown instanceof Exception exception)
            throw exception;
        if (thrown instanceof Error error)
            throw error;
        if (thrown != null)
            throw new AssertionError(thrown);
    }

    private static void await(CountDownLatch latch, String what) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                throw new AssertionError(what + " did not complete within " + TIMEOUT_SECONDS + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(what + " was interrupted", e);
        }
    }

    /**
     * A test body that may throw checked exceptions.
     */
    @FunctionalInterface
    public interface FxBody {
        void run() throws Exception;
    }
}
