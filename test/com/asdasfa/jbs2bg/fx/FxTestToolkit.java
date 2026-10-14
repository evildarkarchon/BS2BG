package com.asdasfa.jbs2bg.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Popup;
import javafx.stage.Window;

/**
 * Public-JavaFX test harness: starts the toolkit once through
 * {@link Platform#startup(Runnable)} and runs test bodies on the JavaFX
 * Application Thread with a hard timeout, so a hung toolkit call fails the
 * test instead of hanging the build. Nothing here touches skin or toolkit
 * internals. The toolkit refuses to start on anything but the built-in
 * headless platform unless the {@code desktop-fx} profile opted into the
 * desktop (see {@link #checkTestPlatform(Properties)}).
 * <p>
 * It also replaces the desktop-coupled idioms tests used to hand-roll:
 * uncaught FX-thread exceptions fail the test that caused them, popups are
 * found by owner ({@link #showingPopup(Window, String)}), layout waits count
 * real pulses from the test thread ({@link #awaitLayoutPulses(Scene, int)}),
 * and {@link FxTestExtension} closes leaked windows and checks the headless
 * accessibility tripwire after every test.
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

    /**
     * Exceptions that escaped to the JavaFX Application Thread's uncaught-exception handler (a throwing
     * {@code runLater}, event handler or pulse listener) and have not yet failed a test.
     */
    private static final Queue<Throwable> UNCAUGHT_FX_FAILURES = new ConcurrentLinkedQueue<>();

    private FxTestToolkit() {
    }

    /**
     * Records every exception that escapes to the FX thread's uncaught-exception handler, so the harness can fail
     * the test that caused it instead of letting JavaFX print it and carry on. Must run on the FX thread. The
     * previous handler still runs, which keeps the stack trace on stderr even when no later FX body or after-each
     * cleanup is left in the JVM to report it.
     */
    private static void installUncaughtExceptionRecorder() {
        Thread fxThread = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = fxThread.getUncaughtExceptionHandler();
        fxThread.setUncaughtExceptionHandler((thread, failure) -> {
            UNCAUGHT_FX_FAILURES.add(failure);
            if (previous != null)
                previous.uncaughtException(thread, failure);
        });
    }

    /**
     * Removes and returns every recorded uncaught FX-thread exception, oldest first, so each one fails exactly
     * one test.
     */
    private static List<Throwable> drainUncaughtFxFailures() {
        List<Throwable> drained = new ArrayList<>();
        for (Throwable failure = UNCAUGHT_FX_FAILURES.poll(); failure != null; failure = UNCAUGHT_FX_FAILURES.poll())
            drained.add(failure);
        return drained;
    }

    /**
     * Wraps recorded uncaught FX-thread exceptions in one test failure: the oldest is the cause, the rest are
     * suppressed.
     */
    private static AssertionError uncaughtFxFailure(List<Throwable> failures) {
        AssertionError error = new AssertionError("Uncaught exception on the JavaFX Application Thread during this"
                + " test: " + failures.getFirst(), failures.getFirst());
        failures.stream().skip(1).forEach(error::addSuppressed);
        return error;
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
        Runnable onFxThread = () -> {
            installUncaughtExceptionRecorder();
            ready.countDown();
        };
        try {
            Platform.startup(onFxThread);
        } catch (IllegalStateException alreadyStarted) {
            // Another test class started the toolkit in this JVM; it is still running.
            Platform.runLater(onFxThread);
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
     * throws, so JUnit assertions inside the body fail the calling test. Any
     * exception that escaped to the FX thread's uncaught-exception handler
     * since the last check (for example from a {@code runLater} the test
     * triggered) also fails the test here, as a suppressed exception of the
     * body's own failure when there is one.
     *
     * @param body test body that may throw
     * @throws AssertionError when the body times out, or when an uncaught FX-thread exception was recorded
     */
    public static void runOnFxThread(FxBody body) throws Exception {
        ensureStarted();
        Throwable thrown = runAndCapture(body, "JavaFX test body");
        // Anything that escaped to the FX thread before this body finished fails this test: alongside the
        // body's own failure when there is one, otherwise on its own.
        List<Throwable> uncaught = drainUncaughtFxFailures();
        if (thrown != null)
            uncaught.forEach(thrown::addSuppressed);
        else if (!uncaught.isEmpty())
            throw uncaughtFxFailure(uncaught);
        rethrow(thrown);
    }

    /**
     * The after-each cleanup behind {@link FxTestExtension}, run on the test thread after every test. It does
     * nothing until some test has started the toolkit (it never starts one itself). Otherwise, on the FX thread,
     * it silently hides every window still showing, so a Stage or popup a test leaked cannot leak into the next
     * test, and then checks the accessibility tripwire. Finally it fails the test with any uncaught FX-thread
     * exception that no FX body has reported yet.
     *
     * @throws AssertionError for a recorded uncaught FX-thread exception, a tripped accessibility tripwire, or
     *         a cleanup that does not finish within {@link #TIMEOUT_SECONDS}
     */
    static void afterEachTest() throws Exception {
        if (!started)
            return;
        Throwable cleanupFailure = runAndCapture(() -> {
            List<Throwable> hideFailures = new ArrayList<>();
            // Copied first: hiding a window removes it (and the popups it owns) from the live list.
            for (Window window : List.copyOf(Window.getWindows())) {
                try {
                    if (window.isShowing())
                        window.hide();
                } catch (Throwable failure) {
                    // One window's throwing onHidden handler must not leave the remaining windows showing.
                    hideFailures.add(failure);
                }
            }
            checkAccessibilityTripwire(System.getProperties(), Platform.isAccessibilityActive());
            if (!hideFailures.isEmpty()) {
                AssertionError error = new AssertionError("Closing a leaked window failed", hideFailures.getFirst());
                hideFailures.stream().skip(1).forEach(error::addSuppressed);
                throw error;
            }
        }, "JavaFX after-each cleanup");
        List<Throwable> uncaught = drainUncaughtFxFailures();
        if (!uncaught.isEmpty()) {
            AssertionError error = uncaughtFxFailure(uncaught);
            if (cleanupFailure != null)
                error.addSuppressed(cleanupFailure);
            throw error;
        }
        rethrow(cleanupFailure);
    }

    /**
     * The headless accessibility tripwire (issue #129), checked after every test once the toolkit runs. Nothing
     * in Tiers 1-2 may activate JavaFX accessibility: on the headless platform an active accessibility bridge
     * means a JavaFX bump changed what headless glass does, and the tier split's assumptions need re-checking.
     * A deliberate {@code desktop-fx} run is exempt, since a screen reader on the developer's desktop activates it.
     *
     * @param properties the system properties of the test JVM
     * @param accessibilityActive {@link Platform#isAccessibilityActive()} as read on the FX thread
     * @throws AssertionError when accessibility is active on a run that did not opt into the desktop
     */
    static void checkAccessibilityTripwire(Properties properties, boolean accessibilityActive) {
        if (accessibilityActive && !"true".equals(properties.getProperty(DESKTOP_OPT_IN)))
            throw new AssertionError("JavaFX accessibility is active on the headless test platform. Tiers 1-2"
                    + " assume headless glass never starts an accessibility bridge (ADR-0008, issue #129); a JavaFX"
                    + " update has changed that, so re-check the headless workarounds before trusting this run.");
    }

    /**
     * Runs the body on the FX thread and waits for it, returning what it threw instead of throwing it.
     *
     * @return the body's failure, or {@code null} when it completed normally
     * @throws AssertionError when the body does not finish within {@link #TIMEOUT_SECONDS}
     */
    private static Throwable runAndCapture(FxBody body, String what) {
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
        await(done, what);
        return failure.get();
    }

    /** Rethrows a captured failure unchanged when the signature allows it, wrapping only foreign throwables. */
    private static void rethrow(Throwable thrown) throws Exception {
        if (thrown instanceof Exception exception)
            throw exception;
        if (thrown instanceof Error error)
            throw error;
        if (thrown != null)
            throw new AssertionError(thrown);
    }

    /**
     * Waits, on the test thread, until the scene has completed the given number of real layout pulses, counted by
     * a post-layout pulse listener and a latch. Each counted pulse requests the next one, so the wait does not
     * depend on anything else scheduling pulses. Use it between FX bodies where a test needs CSS, layout and
     * deferred skin work (such as VirtualFlow cell metrics) to have run, instead of a nested event loop or a
     * synchronous {@code applyCss()}/{@code layout()} that would bypass the pulse the user's screen goes through.
     * The scene's window must be showing, since only showing scenes take part in pulses.
     *
     * @param scene the scene whose layout pulses to count
     * @param pulses how many post-layout pulses must complete, at least 1
     * @throws IllegalStateException when called on the JavaFX Application Thread, which this wait would block
     * @throws IllegalArgumentException when {@code pulses} is less than 1
     * @throws AssertionError when the pulses do not complete within {@link #TIMEOUT_SECONDS}
     */
    public static void awaitLayoutPulses(Scene scene, int pulses) {
        if (Platform.isFxApplicationThread())
            throw new IllegalStateException("awaitLayoutPulses must be called from the test thread, between FX"
                    + " bodies; on the FX thread it would block the very pulses it waits for");
        if (pulses < 1)
            throw new IllegalArgumentException("pulses must be at least 1, was " + pulses);
        ensureStarted();
        CountDownLatch remaining = new CountDownLatch(pulses);
        Runnable listener = () -> {
            remaining.countDown();
            if (remaining.getCount() > 0)
                Platform.requestNextPulse();
        };
        Platform.runLater(() -> {
            scene.addPostLayoutPulseListener(listener);
            Platform.requestNextPulse();
        });
        try {
            await(remaining, pulses + " layout pulses");
        } finally {
            // Queued, not awaited: any FX body the test runs next is queued behind this removal anyway.
            Platform.runLater(() -> scene.removePostLayoutPulseListener(listener));
        }
    }

    /**
     * Finds the one showing {@link Popup} that the test's own window owns and whose scene root matches a CSS
     * selector (through {@link javafx.scene.Node#lookup(String)}, so the root itself or any descendant may carry
     * it). Scoping by owner keeps a popup leaked by another test, or shown over another stage, from being
     * mistaken for the one under test, without exposing the controller's popup fields.
     *
     * @param owner the window the popup was shown over (normally the test's Stage)
     * @param rootSelector selector a node in the popup's scene must match, for example {@code "#fillEmptyApplyButton"}
     * @return the single matching popup
     * @throws IllegalStateException when called off the JavaFX Application Thread
     * @throws AssertionError when no popup, or more than one, matches
     */
    public static Popup showingPopup(Window owner, String rootSelector) {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("showingPopup must be called from an FX body (runOnFxThread)");
        List<Popup> matches = Window.getWindows().stream()
                .filter(window -> window instanceof Popup && window.isShowing())
                .map(Popup.class::cast)
                .filter(popup -> popup.getOwnerWindow() == owner)
                .filter(popup -> popup.getScene().getRoot().lookup(rootSelector) != null)
                .toList();
        if (matches.size() != 1)
            throw new AssertionError("expected exactly one showing popup owned by " + owner + " matching "
                    + rootSelector + ", found " + matches.size());
        return matches.getFirst();
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
