package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.workbench.morphs.MorphsArea;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesArea;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.stage.Stage;

/**
 * Focus intent lives in the scene's focus owner (#123): every assertion reads {@code Scene.getFocusOwner()}, never
 * window activation, so each holds on the headless platform where windows never lose OS focus. Deferred focus is
 * asserted in a second {@link FxTestToolkit#runOnFxThread} call, which FX queue order places after the controller's
 * deferral; synchronous focus is asserted in the same body that caused it.
 * <p>
 * This suite keeps the focus cases that need the whole loaded Workbench: initial focus, the shell's semantic and
 * landmark fallbacks, and the shared portrait viewer. Each Area's own focus restores (list refills, rename, failure
 * dialogs, its satellites) are tested in that Area's {@code <Area>AreaTest}.
 */
class WorkbenchFocusTest {

    @TempDir
    Path temporaryDirectory;
    // The test's one Settings store, environment, and session source; see fixture().
    private WorkbenchTestEnvironment fixture;

    /**
     * Returns this test's Workbench fixture, creating and reloading its store in the temporary directory on first
     * use. Tests that seed custom Settings assign the field before the first call.
     */
    private WorkbenchTestEnvironment fixture() {
        if (fixture == null)
            fixture = WorkbenchTestEnvironment.initialized(temporaryDirectory);
        return fixture;
    }

    /** One loaded and shown Workbench, kept between FX bodies of a multi-block test. */
    private record Shown(FXMLLoader loader, WorkbenchController controller, Stage stage) {
        /** @return the named FXML node */
        @SuppressWarnings("unchecked")
        <T> T node(String id) {
            return (T) LoadedWorkbench.node(loader, id);
        }

        /** @return the Templates Area the shell loaded, for its current list and rename-field views */
        TemplatesArea templates() {
            return LoadedWorkbench.templates(controller);
        }

        /** @return the Morphs Area the shell loaded, for its current list views */
        MorphsArea morphs() {
            return LoadedWorkbench.morphs(controller);
        }

        /** @return the main scene */
        Scene scene() {
            return stage.getScene();
        }

        /** @return the loaded Workbench root */
        Parent root() {
            return stage.getScene().getRoot();
        }
    }

    /**
     * Loads the Workbench, attaches it before {@code show()} as production does, and shows it. Must run on the FX
     * thread.
     */
    private Shown show(WorkbenchProjectFlow flow, WorkbenchPlatform platform) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
        Parent root = loader.load();
        WorkbenchController controller = loader.getController();
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 1300.0, 800.0));
        controller.attach(flow, stage, platform, fixture().environment());
        stage.show();
        return new Shown(loader, controller, stage);
    }

    /** Closes a stage left showing by a multi-block test, from the test thread. */
    private static void close(AtomicReference<Shown> shown) throws Exception {
        if (shown.get() != null)
            FxTestToolkit.runOnFxThread(() -> shown.get().stage().close());
    }

    /** Delivers one key press to {@code target} through the same event seam as JavaFX. */
    private static void sendKey(Node target, KeyCode code, boolean control) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, control, false, false));
    }

    /** Initial focus is applied as the stage is shown, not on a later pulse. */
    @Test
    void initialFocusLandsOnTheActiveAreaPrimaryContentWhenTheStageShows() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        AtomicReference<Shown> shown = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                shown.set(show(flow, new RecordingPlatform()));
                assertSame(shown.get().templates().sliderPresetListNode(), shown.get().scene().getFocusOwner());
            });
            FxTestToolkit.runOnFxThread(() -> assertSame(shown.get().templates().sliderPresetListNode(),
                    shown.get().scene().getFocusOwner()));
        } finally {
            close(shown);
        }
    }

    /** Attaching to a stage that is already showing applies initial focus immediately. */
    @Test
    void initialFocusAppliesImmediatelyWhenAttachedToAShowingStage() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                stage.show();
                controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

                assertSame(LoadedWorkbench.templates(controller).sliderPresetListNode(),
                        stage.getScene().getFocusOwner());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * When the control that had focus before the failure dialog is neither a Slider choice row control nor a
     * Templates profile, gang or bulk control, the Templates Area hands the restore to the shell, which focuses the
     * semantic landmark that held focus (#153).
     */
    @Test
    void templatesFailureFocusFallsBackToTheShellsSemanticTarget() throws Exception {
        fixture = WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(temporaryDirectory,
                Map.of("Waist", new DefaultSliderValue(0.2f, 0.8f)), Map.of()));
        RefusingEditSession session =
                new RefusingEditSession(fixture().newSession());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        flow.apply(SliderPresetEdits.create("Alpha"));
        RecordingPlatform platform = new RecordingPlatform();
        FxTestToolkit.runOnFxThread(() -> {
            Shown shown = show(flow, platform);
            try {
                shown.templates().sliderPresetListNode().getSelectionModel().select(0);
                shown.<TextField>node("sliderPresetFilter").requestFocus();
                session.refuseEdits(true);
                // While the dialog is up, something else in the main scene takes focus.
                platform.respondFailureWith(WorkbenchFeedback.DialogAction.CLOSE,
                        () -> shown.<Button>node("renameSliderPresetButton").requestFocus());

                shown.<Button>node("fiftyAllSliderChoicesButton").fire();

                assertSame(shown.templates().sliderPresetListNode(), shown.scene().getFocusOwner(),
                        "the filter sits in the primary content, whose landmark node is the Slider Preset list");
            } finally {
                shown.stage().close();
            }
        });
    }

    /** When the Fill Empty launcher is no longer reachable, closing the flyout focuses the Area landmark. */
    @Test
    void fillEmptyFlyoutFallsBackToTheActiveAreaWhenItsLauncherIsHidden() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        FxTestToolkit.runOnFxThread(() -> {
            Shown shown = show(flow, new RecordingPlatform());
            try {
                shown.<ToggleButton>node("morphsAreaButton").fire();
                shown.<Button>node("fillEmptyNpcMorphAssignmentsButton").fire();
                Popup popup = FxTestToolkit.showingPopup(shown.stage(), "#fillEmptyApplyButton");
                sendKey(shown.root(), KeyCode.DIGIT1, true);
                shown.<TextField>node("sliderPresetFilter").requestFocus();

                ((Button) popup.getScene().getRoot().lookup("#fillEmptyCancelButton")).fire();

                assertSame(shown.templates().sliderPresetListNode(), shown.scene().getFocusOwner());
            } finally {
                shown.stage().close();
            }
        });
    }

    /** The portrait viewer focuses Close as it opens and returns focus to its launcher as it closes. */
    @Test
    void portraitViewerTakesFocusOnOpenAndReturnsItToTheLauncherOnClose() throws Exception {
        Path images = Files.createDirectories(temporaryDirectory.resolve("images"));
        Files.copy(Path.of("assets", "res", "icon.png"), images.resolve("Lydia (HousecarlWhiterun).png"));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "HousecarlWhiterun", "NordRace",
                "000A2C94"));
        AtomicReference<Shown> shown = new AtomicReference<>();
        CountDownLatch thumbnailLoaded = new CountDownLatch(1);
        try {
            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
                Parent root = loader.load();
                WorkbenchController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1300.0, 800.0));
                controller.attach(flow, stage, new RecordingPlatform(),
                        fixture().environment().withPortraitDirectory(images));
                stage.show();
                shown.set(new Shown(loader, controller, stage));
                Button launcher = shown.get().node("openNpcPortraitViewerButton");
                // The thumbnail decodes in the background; the launcher enables once it has loaded.
                launcher.disableProperty().addListener((observable, previous, disabled) -> {
                    if (!disabled)
                        thumbnailLoaded.countDown();
                });
                shown.get().<ToggleButton>node("morphsAreaButton").fire();
                LoadedWorkbench.morphs(controller).npcMorphAssignmentListNode().getSelectionModel().selectFirst();
                if (!launcher.isDisabled())
                    thumbnailLoaded.countDown();
            });
            assertTrue(thumbnailLoaded.await(30, TimeUnit.SECONDS), "portrait thumbnail never loaded");
            AtomicReference<Stage> viewer = new AtomicReference<>();
            FxTestToolkit.runOnFxThread(() -> {
                Button launcher = shown.get().node("openNpcPortraitViewerButton");
                shown.get().morphs().npcMorphAssignmentListNode().requestFocus();

                launcher.fire();

                viewer.set(FxTestToolkit.showingOwnedStage(shown.get().stage(), ".npc-portrait-viewer"));
                Node close = viewer.get().getScene().getFocusOwner();
                assertTrue(close instanceof Button button && button.getText().equals("Close"),
                        () -> "viewer focus owner: " + close);
            });
            // Closing before the full image has loaded would leave the portrait file open and the @TempDir
            // undeletable on Windows.
            PortraitTestSupport.awaitFullImage(viewer.get());
            FxTestToolkit.runOnFxThread(() -> {
                Button launcher = shown.get().node("openNpcPortraitViewerButton");
                Node close = viewer.get().getScene().getFocusOwner();

                ((Button) close).fire();

                assertFalse(viewer.get().isShowing());
                assertSame(launcher, shown.get().scene().getFocusOwner());
            });
        } finally {
            close(shown);
        }
    }
}
