package com.asdasfa.jbs2bg.fx;

import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;

/**
 * Public-JavaFX test harness: starts the toolkit once through
 * {@link Platform#startup(Runnable)} and runs test bodies on the JavaFX
 * Application Thread with a hard timeout, so a hung toolkit call fails the
 * test instead of hanging the build. Nothing here touches skin or toolkit
 * internals. The toolkit refuses to start on anything but the built-in
 * headless platform unless the {@code desktop-fx} profile opted into the
 * desktop (see {@link #checkTestPlatform(Properties)}).
 */
public final class FxTestToolkit {

    /**
     * Upper bound for any single FX-thread test body; generous for a cold toolkit.
     */
    public static final long TIMEOUT_SECONDS = 30;

    /**
     * System property the {@code desktop-fx} Maven profile sets to mark a deliberate desktop run.
     */
    private static final String DESKTOP_OPT_IN = "bs2bg.test.desktop";

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
        // Checked before Platform.startup: once glass has picked the desktop platform, a window may already
        // have reached the user's screen. Every FX test starts the toolkit only through this method.
        checkTestPlatform(System.getProperties()).ifPresent(System.err::println);
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
     * The startup guard for Tiers 1-2 (ADR-0008): the toolkit may start only on JavaFX's built-in headless
     * platform with the software pipeline ({@code glass.platform=headless}, {@code prism.order=sw}, which
     * Surefire sets), unless the run opted into the desktop through the {@code desktop-fx} Maven profile
     * ({@code bs2bg.test.desktop=true}). A command-line {@code -Dglass.platform=...} overrides Surefire's
     * entry, so without this check it would silently put test windows back on the desktop. Only public
     * system properties are read: no glass-class check and no screen fingerprint, because the headless screen
     * size is expected to become configurable (JDK-8364201).
     *
     * @param properties the system properties of the test JVM
     * @return the one-line warning to print under {@code desktop-fx}, or empty on the headless platform
     * @throws AssertionError when the platform is not headless and the desktop was not explicitly opted into
     */
    static Optional<String> checkTestPlatform(Properties properties) {
        if ("true".equals(properties.getProperty(DESKTOP_OPT_IN)))
            return Optional.of("WARNING: " + DESKTOP_OPT_IN + "=true (Maven profile desktop-fx): JavaFX tests run on"
                    + " the desktop platform and will show windows; Tiers 1-2 must run headless.");
        String platform = properties.getProperty("glass.platform");
        String pipeline = properties.getProperty("prism.order");
        if (!"headless".equals(platform) || !"sw".equals(pipeline))
            throw new AssertionError("JavaFX tests must run on the headless platform (glass.platform=headless,"
                    + " prism.order=sw, set by Surefire), but this JVM has glass.platform="
                    + (platform == null ? "<unset>" : platform) + " and prism.order="
                    + (pipeline == null ? "<unset>" : pipeline)
                    + ". Do not override them with -D; to debug on the desktop, run with the Maven profile"
                    + " -Pdesktop-fx instead.");
        return Optional.empty();
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
