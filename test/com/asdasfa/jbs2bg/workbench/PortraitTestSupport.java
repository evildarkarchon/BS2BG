package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.asdasfa.jbs2bg.fx.FxTestToolkit;

import javafx.stage.Stage;

/**
 * Shared waits for tests that load real NPC portraits. JavaFX decodes portraits in the background and holds the file
 * open while it reads, so a test must let every decode it started finish before its {@code @TempDir} is deleted;
 * Windows refuses to delete a file that is still open. JavaFX closes the stream in the background read's
 * {@code finally} and only afterwards, on the FX thread, sets the decoded size and a progress of exactly 1 (or the
 * error); incremental progress stays below 1. So a wait that ends on progress 1, a decoded width, or the error, has
 * also outlived the open file.
 */
final class PortraitTestSupport {

    /** The application icon's size, which every test portrait copies. */
    static final String PORTRAIT_DIMENSIONS = "NPC portrait dimensions: 512 × 512 pixels";

    private PortraitTestSupport() {
    }

    /**
     * Copies the application icon into {@code directory} as a real decodable portrait.
     *
     * @param directory image directory; created if needed
     * @param fileName  portrait file name, for example {@code "Lydia (HousecarlWhiterun).png"}
     * @return the image directory
     * @throws Exception when the copy fails
     */
    static Path copyPortrait(Path directory, String fileName) throws Exception {
        Files.createDirectories(directory);
        Files.copy(Path.of("assets", "res", "icon.png"), directory.resolve(fileName));
        return directory;
    }

    /**
     * Polls an FX-thread condition from the test thread, failing after the harness timeout.
     *
     * @param description what is awaited, for the failure message
     * @param condition   evaluated on the JavaFX Application Thread
     * @throws Exception when an FX body fails or the wait is interrupted
     */
    static void awaitOnFx(String description, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(FxTestToolkit.TIMEOUT_SECONDS);
        boolean[] met = new boolean[1];
        while (true) {
            FxTestToolkit.runOnFxThread(() -> met[0] = condition.getAsBoolean());
            if (met[0])
                return;
            if (System.nanoTime() > deadline)
                throw new AssertionError("timed out waiting for " + description);
            Thread.sleep(20L);
        }
    }

    /**
     * Waits until an open portrait viewer's full-resolution image has loaded and the viewer reports its dimensions.
     * Call it before closing the viewer, so the background read has closed the portrait file.
     *
     * @param viewer the viewer Stage, found with {@link FxTestToolkit#showingOwnedStage}
     * @throws Exception when an FX body fails or the wait is interrupted
     */
    static void awaitFullImage(Stage viewer) throws Exception {
        awaitOnFx("the portrait viewer's full image to load", () -> viewer.getScene().getRoot()
                .lookupAll(".label").stream()
                .anyMatch(node -> PORTRAIT_DIMENSIONS.equals(node.getAccessibleText())));
    }
}
