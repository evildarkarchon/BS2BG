package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.asdasfa.jbs2bg.workbench.PortraitTestSupport.awaitFullImage;
import static com.asdasfa.jbs2bg.workbench.PortraitTestSupport.awaitOnFx;
import static com.asdasfa.jbs2bg.workbench.PortraitTestSupport.copyPortrait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The shared NPC portrait viewer, driven without the Workbench: one inspector (thumbnail, status, launcher) in a
 * minimal shown Stage, a {@code @TempDir} image directory, and the real satellite registry.
 */
class NpcPortraitViewerTest {

    private static final NpcMorphAssignmentIdentity LYDIA =
            new NpcMorphAssignmentIdentity("Skyrim.esm", "HousecarlWhiterun");

    @TempDir
    Path temporaryDirectory;

    /** One shown inspector hosting a thumbnail, kept between FX bodies of a multi-block test. */
    private record Inspector(Stage owner, ImageView image, Label status, Button launcher,
                             SatelliteRegistry satellites, NpcPortraitViewer.Thumbnail thumbnail) {
    }

    /** @return a resolved appearance frame for the given theme choice over light OS preferences */
    private static WorkbenchAppearance.Frame frame(WorkbenchAppearance.ThemeChoice choice) {
        return new WorkbenchAppearance(choice, WorkbenchAppearance.PlatformPreferences.light()).frame();
    }

    /** @return whether {@code node} carries the given Workbench theme pseudo-class */
    private static boolean hasPseudoClass(Node node, String pseudoClass) {
        return node.getPseudoClassStates().contains(PseudoClass.getPseudoClass(pseudoClass));
    }

    /** Copies the application icon into the image directory as a real decodable portrait per file name. */
    private Path images(String... portraitFiles) throws Exception {
        Path images = Files.createDirectories(temporaryDirectory.resolve("images"));
        for (String file : portraitFiles)
            copyPortrait(images, file);
        return images;
    }

    /** Builds and shows one inspector over {@code images}. Must run on the FX thread. */
    private static Inspector inspector(Path images) {
        ImageView image = new ImageView();
        Label status = new Label();
        Button launcher = new Button("Open portrait viewer");
        Stage owner = new Stage();
        owner.setScene(new Scene(new VBox(image, status, launcher), 400.0, 300.0));
        owner.show();
        SatelliteRegistry satellites = new SatelliteRegistry(() -> frame(WorkbenchAppearance.ThemeChoice.LIGHT));
        NpcPortraitViewer viewer = new NpcPortraitViewer(images, owner, satellites,
                launcherNode -> launcherNode.requestFocus());
        NpcPortraitViewer.Thumbnail thumbnail = viewer.thumbnail(image, status, launcher,
                "Select an NPC to inspect its portrait.");
        return new Inspector(owner, image, status, launcher, satellites, thumbnail);
    }

    /** Shows an inspector over {@code images} and waits until Lydia's thumbnail has decoded. */
    private static AtomicReference<Inspector> inspectorWithLydiaLoaded(Path images) throws Exception {
        AtomicReference<Inspector> shown = new AtomicReference<>();
        FxTestToolkit.runOnFxThread(() -> {
            shown.set(inspector(images));
            shown.get().thumbnail().show(LYDIA, "Lydia");
        });
        awaitOnFx("the thumbnail to load", () -> !shown.get().launcher().isDisabled());
        return shown;
    }

    /** Closes the inspector's owner window from the test thread. */
    private static void close(AtomicReference<Inspector> shown) throws Exception {
        if (shown.get() != null)
            FxTestToolkit.runOnFxThread(() -> shown.get().owner().close());
    }

    /** A found portrait decodes in the background, then names its file and enables the viewer launcher. */
    @Test
    void loadedThumbnailNamesItsFileAndEnablesTheViewer() throws Exception {
        Path images = images("Lydia (HousecarlWhiterun).png");
        AtomicReference<Inspector> shown = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                shown.set(inspector(images));
                shown.get().thumbnail().show(LYDIA, "Lydia");
                assertNotNull(shown.get().image().getImage());
            });
            awaitOnFx("the thumbnail to load", () -> !shown.get().launcher().isDisabled());
            FxTestToolkit.runOnFxThread(() -> {
                assertEquals("Portrait file: Lydia (HousecarlWhiterun).png", shown.get().status().getText());
                assertEquals("Portrait file: Lydia (HousecarlWhiterun).png", shown.get().status().getAccessibleHelp());
                assertTrue(shown.get().image().getImage().getWidth() > 0.0);
                assertFalse(shown.get().image().getImage().isError());
            });
        } finally {
            close(shown);
        }
    }

    /**
     * The stale-load guard: when the selection moves on before a portrait has decoded, that decode's completion
     * (delivered later on the FX thread) must not overwrite the newer selection's status or enable the viewer.
     */
    @Test
    void decodeFinishingAfterTheSelectionMovedOnLeavesTheNewerSelectionInPlace() throws Exception {
        Path images = images("Lydia (HousecarlWhiterun).png");
        AtomicReference<Inspector> shown = new AtomicReference<>();
        AtomicReference<Image> superseded = new AtomicReference<>();
        String missing = "No portrait found. Expected images/Faendal (Faendal).jpg"
                + " (or .jpeg, .png, .bmp), then a display-name-only image.";
        try {
            FxTestToolkit.runOnFxThread(() -> {
                shown.set(inspector(images));
                shown.get().thumbnail().show(LYDIA, "Lydia");
                superseded.set(shown.get().image().getImage());
                // Same FX body: Lydia's decode cannot report back before the selection moves to Faendal.
                shown.get().thumbnail().show(new NpcMorphAssignmentIdentity("Skyrim.esm", "Faendal"), "Faendal");
            });
            awaitOnFx("the superseded decode to finish", () -> superseded.get().getProgress() >= 1.0);
            FxTestToolkit.runOnFxThread(() -> {
                assertTrue(superseded.get().getWidth() > 0.0, "the superseded portrait should have decoded");
                assertEquals(missing, shown.get().status().getText());
                assertEquals(missing, shown.get().status().getAccessibleHelp());
                assertNull(shown.get().image().getImage());
                assertTrue(shown.get().launcher().isDisabled());
            });
        } finally {
            close(shown);
        }
    }

    /** A portrait file that cannot be decoded reports the failure and never enables the viewer. */
    @Test
    void undecodablePortraitReportsTheFailureAndKeepsTheViewerDisabled() throws Exception {
        Path images = Files.createDirectories(temporaryDirectory.resolve("images"));
        Files.writeString(images.resolve("Lydia (HousecarlWhiterun).png"), "not an image");
        AtomicReference<Inspector> shown = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                shown.set(inspector(images));
                shown.get().thumbnail().show(LYDIA, "Lydia");
            });
            awaitOnFx("the decode to fail", () -> shown.get().status().getText().startsWith("Could not"));
            FxTestToolkit.runOnFxThread(() -> {
                assertEquals("Could not load portrait: Lydia (HousecarlWhiterun).png", shown.get().status().getText());
                assertEquals("Could not load portrait: Lydia (HousecarlWhiterun).png",
                        shown.get().status().getAccessibleHelp());
                assertNull(shown.get().image().getImage());
                assertTrue(shown.get().launcher().isDisabled());
            });
        } finally {
            close(shown);
        }
    }

    /**
     * The launcher opens one owned viewer Stage that names the NPC, takes focus on Close, and reports the
     * full-resolution image's dimensions once it has loaded.
     */
    @Test
    void launcherOpensAnOwnedViewerThatFocusesCloseAndReportsTheFullImageDimensions() throws Exception {
        AtomicReference<Inspector> shown = inspectorWithLydiaLoaded(images("Lydia (HousecarlWhiterun).png"));
        AtomicReference<Stage> viewer = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                shown.get().launcher().fire();

                viewer.set(FxTestToolkit.showingOwnedStage(shown.get().owner(), ".npc-portrait-viewer"));
                assertEquals("NPC Portrait — Lydia", viewer.get().getTitle());
                Parent root = viewer.get().getScene().getRoot();
                assertEquals("NPC portrait viewer", root.getAccessibleText());
                assertNotNull(root.lookupAll(".image-view").stream()
                        .filter(node -> "NPC portrait: Lydia, plugin Skyrim.esm, editor ID HousecarlWhiterun"
                                .equals(node.getAccessibleText()))
                        .findFirst().orElse(null));
                Node focusOwner = viewer.get().getScene().getFocusOwner();
                assertTrue(focusOwner instanceof Button button && button.getText().equals("Close"),
                        () -> "viewer focus owner: " + focusOwner);
            });
            awaitFullImage(viewer.get());
        } finally {
            close(shown);
        }
    }

    /** Opening the portrait already on view raises that viewer; opening another NPC's portrait replaces it. */
    @Test
    void reopeningRaisesTheOpenViewerAndAnotherPortraitReplacesIt() throws Exception {
        AtomicReference<Inspector> shown = inspectorWithLydiaLoaded(
                images("Lydia (HousecarlWhiterun).png", "Faendal (Faendal).png"));
        AtomicReference<Stage> first = new AtomicReference<>();
        AtomicReference<Stage> replacement = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                shown.get().launcher().fire();
                first.set(FxTestToolkit.showingOwnedStage(shown.get().owner(), ".npc-portrait-viewer"));
            });
            awaitFullImage(first.get());
            FxTestToolkit.runOnFxThread(() -> {
                shown.get().launcher().fire();

                // showingOwnedStage requires exactly one viewer, so the raise opened no second window.
                assertSame(first.get(), FxTestToolkit.showingOwnedStage(shown.get().owner(), ".npc-portrait-viewer"));
                assertEquals(1, shown.get().satellites().size());
                shown.get().thumbnail().show(new NpcMorphAssignmentIdentity("Skyrim.esm", "Faendal"), "Faendal");
            });
            awaitOnFx("Faendal's thumbnail to load", () -> !shown.get().launcher().isDisabled());
            FxTestToolkit.runOnFxThread(() -> {
                shown.get().launcher().fire();

                assertFalse(first.get().isShowing());
                replacement.set(FxTestToolkit.showingOwnedStage(shown.get().owner(), ".npc-portrait-viewer"));
                assertEquals("NPC Portrait — Faendal", replacement.get().getTitle());
                assertEquals(1, shown.get().satellites().size());
            });
            awaitFullImage(replacement.get());
        } finally {
            close(shown);
        }
    }

    /** Escape closes the viewer, which returns focus to its launcher and drops its registration. */
    @Test
    void escapeClosesTheViewerAndReturnsFocusToTheLauncher() throws Exception {
        AtomicReference<Inspector> shown = inspectorWithLydiaLoaded(images("Lydia (HousecarlWhiterun).png"));
        AtomicReference<Stage> viewer = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                shown.get().launcher().fire();
                viewer.set(FxTestToolkit.showingOwnedStage(shown.get().owner(), ".npc-portrait-viewer"));
            });
            awaitFullImage(viewer.get());
            FxTestToolkit.runOnFxThread(() -> {
                Node close = viewer.get().getScene().getFocusOwner();

                close.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));

                assertFalse(viewer.get().isShowing());
                assertSame(shown.get().launcher(), shown.get().owner().getScene().getFocusOwner());
                assertEquals(0, shown.get().satellites().size());
            });
        } finally {
            close(shown);
        }
    }

    /**
     * The open viewer registers with the shell's appearance: it opens in the current theme, follows later theme
     * frames, and is closed when the shell dismisses its satellites; once closed it is no longer re-themed.
     */
    @Test
    void viewerFollowsTheShellAppearanceAndClosesWhenTheShellDismissesItsSatellites() throws Exception {
        AtomicReference<Inspector> shown = inspectorWithLydiaLoaded(images("Lydia (HousecarlWhiterun).png"));
        AtomicReference<Stage> viewer = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                shown.get().launcher().fire();
                viewer.set(FxTestToolkit.showingOwnedStage(shown.get().owner(), ".npc-portrait-viewer"));
                assertTrue(hasPseudoClass(viewer.get().getScene().getRoot(), "workbench-light"));
            });
            awaitFullImage(viewer.get());
            FxTestToolkit.runOnFxThread(() -> {
                SatelliteRegistry satellites = shown.get().satellites();
                Parent root = viewer.get().getScene().getRoot();

                satellites.retheme(frame(WorkbenchAppearance.ThemeChoice.DARK));

                assertTrue(hasPseudoClass(root, "workbench-dark"));
                assertFalse(hasPseudoClass(root, "workbench-light"));

                satellites.dismissAll();

                assertFalse(viewer.get().isShowing());
                assertEquals(0, satellites.size());
                satellites.retheme(frame(WorkbenchAppearance.ThemeChoice.LIGHT));
                assertTrue(hasPseudoClass(root, "workbench-dark"), "a closed viewer must not be re-themed");
            });
        } finally {
            close(shown);
        }
    }
}
