package com.asdasfa.jbs2bg.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Self-tests of the JavaFX test harness ({@link FxTestToolkit} and {@link FxTestExtension}): the guarantees every
 * FX test relies on without restating them, proved on the real headless toolkit.
 */
class FxTestHarnessTest {

    /** Builds a popup whose scene root carries the given id, the shape the Workbench flyouts use. */
    private static Popup popupWithRoot(String rootId) {
        StackPane content = new StackPane();
        content.setId(rootId);
        Popup popup = new Popup();
        popup.getContent().add(content);
        return popup;
    }

    @Test
    void popupLookupReturnsOnlyAShowingPopupOwnedByTheGivenStage() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Stage mine = new Stage();
            mine.setScene(new Scene(new StackPane(), 200.0, 200.0));
            Stage other = new Stage();
            other.setScene(new Scene(new StackPane(), 200.0, 200.0));
            try {
                mine.show();
                other.show();
                Popup foreign = popupWithRoot("filterChoices");
                foreign.show(other, 0.0, 0.0);
                // A cancelled flyout: shown, then hidden again, so it must not count as a match.
                Popup cancelled = popupWithRoot("filterChoices");
                cancelled.show(mine, 0.0, 0.0);
                cancelled.hide();
                Popup owned = popupWithRoot("filterChoices");
                owned.show(mine, 0.0, 0.0);
                Popup unrelated = popupWithRoot("somethingElse");
                unrelated.show(mine, 0.0, 0.0);

                assertSame(owned, FxTestToolkit.showingPopup(mine, "#filterChoices"));
                assertSame(foreign, FxTestToolkit.showingPopup(other, "#filterChoices"));
                AssertionError missing = assertThrows(AssertionError.class,
                        () -> FxTestToolkit.showingPopup(mine, "#noSuchRoot"));
                assertTrue(missing.getMessage().contains("#noSuchRoot"), missing.getMessage());

                Popup twin = popupWithRoot("filterChoices");
                twin.show(mine, 0.0, 0.0);
                AssertionError ambiguous = assertThrows(AssertionError.class,
                        () -> FxTestToolkit.showingPopup(mine, "#filterChoices"));
                assertTrue(ambiguous.getMessage().contains("2"), ambiguous.getMessage());
            } finally {
                mine.close();
                other.close();
            }
        });
    }

    @Test
    void anExceptionFromRunLaterFailsTheNextFxBody() throws Exception {
        FxTestToolkit.ensureStarted();
        IllegalStateException boom = new IllegalStateException("thrown from runLater");
        Platform.runLater(() -> {
            throw boom;
        });
        AssertionError failure = assertThrows(AssertionError.class, () -> FxTestToolkit.runOnFxThread(() -> {
        }));
        assertSame(boom, failure.getCause());
        // Reported once: the failure is consumed, so the following body is clean again.
        FxTestToolkit.runOnFxThread(() -> {
        });
    }

    @Test
    void aFailingFxBodyCarriesAnEarlierUncaughtExceptionAsSuppressed() throws Exception {
        FxTestToolkit.ensureStarted();
        IllegalStateException boom = new IllegalStateException("thrown from runLater");
        Platform.runLater(() -> {
            throw boom;
        });
        AssertionError bodyFailure = new AssertionError("the body's own assertion");
        AssertionError failure = assertThrows(AssertionError.class, () -> FxTestToolkit.runOnFxThread(() -> {
            throw bodyFailure;
        }));
        assertSame(bodyFailure, failure);
        assertEquals(List.of(boom), List.of(failure.getSuppressed()));
    }

    @Test
    void afterEachCleanupSilentlyClosesEveryWindowStillShowing() throws Exception {
        Stage[] leaked = new Stage[1];
        Popup[] flyout = new Popup[1];
        FxTestToolkit.runOnFxThread(() -> {
            leaked[0] = new Stage();
            leaked[0].setScene(new Scene(new StackPane(), 200.0, 200.0));
            leaked[0].show();
            flyout[0] = popupWithRoot("leakedFlyout");
            flyout[0].show(leaked[0], 0.0, 0.0);
        });

        // The extension reads nothing from the JUnit context.
        new FxTestExtension().afterEach(null);

        FxTestToolkit.runOnFxThread(() -> {
            assertFalse(leaked[0].isShowing());
            assertFalse(flyout[0].isShowing());
        });
    }

    @Test
    void anUncaughtExceptionWithNoLaterFxBodyFailsTheAfterEachCleanup() throws Exception {
        FxTestToolkit.ensureStarted();
        IllegalStateException boom = new IllegalStateException("thrown from runLater after the last FX body");
        Platform.runLater(() -> {
            throw boom;
        });

        AssertionError failure = assertThrows(AssertionError.class, () -> new FxTestExtension().afterEach(null));
        assertSame(boom, failure.getCause());
        // Reported once, so the real after-each that follows this test stays green.
        new FxTestExtension().afterEach(null);
    }

    /**
     * The extension is registered for every test through JUnit's service-loader autodetection, not per class: the
     * first test leaks a shown Stage and the second, which runs next, finds it closed. Run alone, the second test
     * only checks that no window is showing.
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AutodetectedAfterEach {

        private static Stage leakedByThePreviousTest;

        @Test
        @Order(1)
        void aTestLeaksAShownStage() throws Exception {
            FxTestToolkit.runOnFxThread(() -> {
                leakedByThePreviousTest = new Stage();
                leakedByThePreviousTest.setScene(new Scene(new StackPane(), 200.0, 200.0));
                leakedByThePreviousTest.show();
            });
        }

        @Test
        @Order(2)
        void theNextTestStartsWithNoWindowShowing() throws Exception {
            FxTestToolkit.runOnFxThread(() -> {
                if (leakedByThePreviousTest != null)
                    assertFalse(leakedByThePreviousTest.isShowing());
                assertEquals(List.of(), Window.getWindows().stream().filter(Window::isShowing).toList());
            });
        }
    }

    @Test
    void layoutPulseWaitReturnsOnlyAfterTheRequestedNumberOfLayoutPulses() throws Exception {
        Stage[] stage = new Stage[1];
        AtomicInteger observed = new AtomicInteger();
        Runnable counter = observed::incrementAndGet;
        FxTestToolkit.runOnFxThread(() -> {
            stage[0] = new Stage();
            stage[0].setScene(new Scene(new StackPane(), 200.0, 200.0));
            stage[0].show();
            stage[0].getScene().addPostLayoutPulseListener(counter);
        });
        try {
            FxTestToolkit.awaitLayoutPulses(stage[0].getScene(), 3);
            assertTrue(observed.get() >= 3, "only " + observed.get() + " layout pulses ran");
        } finally {
            FxTestToolkit.runOnFxThread(() -> {
                stage[0].getScene().removePostLayoutPulseListener(counter);
                stage[0].close();
            });
        }
    }

    @Test
    void layoutPulseWaitRefusesTheFxThreadItWouldBlock() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Scene scene = new Scene(new StackPane(), 200.0, 200.0);
            assertThrows(IllegalStateException.class, () -> FxTestToolkit.awaitLayoutPulses(scene, 1));
        });
    }

    /**
     * The desktop-coupled idioms the harness replaced stay gone: a nested event loop inside an FX body (use
     * {@link FxTestToolkit#awaitLayoutPulses} between bodies) and a global window scan that can pick up another
     * test's popup (use {@link FxTestToolkit#showingPopup}). Only the harness itself may enumerate windows.
     */
    @Test
    void noTestOutsideTheHarnessEntersNestedEventLoopsOrScansAllWindows() throws IOException {
        Path testRoot = Path.of("test");
        Path harness = testRoot.resolve(Path.of("com", "asdasfa", "jbs2bg", "fx"));
        Pattern forbidden = Pattern.compile("enterNestedEventLoop|exitNestedEventLoop|Window\\s*\\.\\s*getWindows\\s*\\(");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(testRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java") && !path.startsWith(harness))
                    .toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int index = 0; index < lines.size(); index++)
                    if (forbidden.matcher(lines.get(index)).find())
                        violations.add(file + ":" + (index + 1) + ": " + lines.get(index).trim());
            }
        }
        assertEquals(List.of(), violations);
    }

    @Test
    void popupLookupRefusesToRunOffTheFxThread() {
        Stage[] none = new Stage[1];
        assertThrows(IllegalStateException.class, () -> FxTestToolkit.showingPopup(none[0], "#anything"));
    }
}
