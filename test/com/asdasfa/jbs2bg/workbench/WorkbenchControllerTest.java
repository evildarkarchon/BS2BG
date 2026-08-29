package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.ProjectLifecycleStatus;
import com.asdasfa.jbs2bg.project.ProjectSessions;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

class WorkbenchControllerTest {

    @TempDir
    Path temporaryDirectory;

    /** Open consumes a platform-selected path and renders recovery state and diagnostics from the returned frame. */
    @Test
    void openCommandRendersTheAuthoritativeRecoveredFrame() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);

            ((MenuItem) loader.getNamespace().get("openProjectMenuItem")).fire();

            assertEquals(ProjectLifecycleStatus.RECOVERED, flow.frame().snapshot().getLifecycleStatus());
            assertTrue(flow.frame().snapshot().isDirty());
            assertEquals("BS2BG Preview - *recovery-source.jbs2bg", stage.getTitle());
            String diagnostics = ((TextArea) loader.getNamespace().get("diagnosticsText")).getText();
            assertTrue(diagnostics.contains("SLIDER_PRESET_ASSIGNMENT_MISSING"));
            assertTrue(diagnostics.contains("Missing Target"));
            assertTrue(diagnostics.contains("Missing NPC"));
            stage.close();
        });
    }

    /** Exit cancellation keeps the dirty window alive; a later Discard closes it exactly once. */
    @Test
    void dirtyExitCanBeCancelledBeforeDiscardClosesTheWindow() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));
        platform.respondWith(WorkbenchProjectFlow.Response.cancelled());
        platform.respondWith(WorkbenchProjectFlow.Response.discard());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);
            MenuItem open = (MenuItem) loader.getNamespace().get("openProjectMenuItem");
            MenuItem exit = (MenuItem) loader.getNamespace().get("exitMenuItem");
            open.fire();

            exit.fire();
            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);

            exit.fire();
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stage.close();
        });
    }

    /** Malformed Open keeps the ProjectSession code, source path, JSON element, line, and column visible. */
    @Test
    void malformedOpenRendersCompleteSourceCoordinates() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("malformed-project.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "malformed-syntax.jbs2bg"), source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);
            ((MenuItem) loader.getNamespace().get("openProjectMenuItem")).fire();

            String diagnostics = ((TextArea) loader.getNamespace().get("diagnosticsText")).getText();
            assertTrue(diagnostics.contains("PROJECT_JSON_MALFORMED"));
            assertTrue(diagnostics.contains("malformed-project.jbs2bg"));
            assertTrue(diagnostics.contains("/SliderPresets"));
            assertTrue(diagnostics.contains("line "));
            assertTrue(diagnostics.contains("column "));
            stage.close();
        });
    }

    /** Ctrl+1..5 use typed navigation, with Output toggling a drawer instead of replacing the active Area. */
    @Test
    void keyboardNavigationPreservesTheActiveAreaWhenOutputToggles() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform());
            stage.show();

            sendControlKey(root, KeyCode.DIGIT2);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());

            sendControlKey(root, KeyCode.DIGIT4);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());
            assertTrue(((ToggleButton) loader.getNamespace().get("outputAreaButton")).isSelected());
            VBox outputDrawer = (VBox) loader.getNamespace().get("outputDrawer");
            Slider outputHeight = (Slider) loader.getNamespace().get("outputDrawerHeight");
            assertTrue(outputDrawer.isVisible());
            outputHeight.setValue(260.0);
            assertEquals(260.0, outputDrawer.getPrefHeight());
            assertEquals(260.0, outputDrawer.getMaxHeight());

            sendControlKey(root, KeyCode.BACK_QUOTE);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertFalse(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());

            ToggleButton outputLauncher = (ToggleButton) loader.getNamespace().get("outputAreaButton");
            outputLauncher.requestFocus();
            outputLauncher.fire();
            sendKey(root, KeyCode.ESCAPE);
            assertSame(outputLauncher, stage.getScene().getFocusOwner());
            stage.close();
        });
    }

    /** F6 follows semantic landmarks, and closing user-opened Output restores the exact prior focus target. */
    @Test
    void semanticFocusTraversalAndOutputFocusRestorationArePredictable() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform());
            stage.show();

            sendControlKey(root, KeyCode.DIGIT2);
            Button primary = (Button) loader.getNamespace().get("primaryContentButton");
            Button editor = (Button) loader.getNamespace().get("editorButton");
            Button inspector = (Button) loader.getNamespace().get("inspectorButton");
            Label output = (Label) loader.getNamespace().get("outputFocusTarget");
            assertSame(primary, scene.getFocusOwner());

            sendKey(root, KeyCode.F6);
            assertSame(editor, scene.getFocusOwner());
            sendKey(root, KeyCode.F6);
            assertSame(inspector, scene.getFocusOwner());

            sendControlKey(root, KeyCode.BACK_QUOTE);
            assertSame(output, scene.getFocusOwner());
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());

            sendKey(root, KeyCode.ESCAPE);
            assertSame(inspector, scene.getFocusOwner());
            assertFalse(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());
            stage.close();
        });
    }

    /** Generated Output may reveal the drawer without moving focus away from the active editor. */
    @Test
    void automaticOutputRevealUsesTheProductionAdapterWithoutStealingFocus() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform());
            stage.show();

            Button editor = (Button) loader.getNamespace().get("editorButton");
            editor.requestFocus();
            controller.revealGeneratedOutput("generated output");

            assertTrue(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());
            assertEquals("generated output", ((TextArea) loader.getNamespace().get("outputText")).getText());
            assertSame(editor, scene.getFocusOwner());
            stage.close();
        });
    }

    /** At the accepted breakpoint, real side panes move into overlays and Esc returns focus to each launcher. */
    @Test
    void narrowLayoutKeepsTheEditorInlineAndUsesFocusRestoringSideOverlays() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform());
            stage.show();

            root.resize(1199, 720);
            HBox areaPanes = (HBox) loader.getNamespace().get("areaPanes");
            StackPane overlay = (StackPane) loader.getNamespace().get("overlayLayer");
            VBox primaryPane = (VBox) loader.getNamespace().get("primaryPane");
            StackPane editorPane = (StackPane) loader.getNamespace().get("editorPane");
            VBox inspectorPane = (VBox) loader.getNamespace().get("inspectorPane");
            Button listLauncher = (Button) loader.getNamespace().get("showPrimaryOverlayButton");
            Button inspectorLauncher = (Button) loader.getNamespace().get("showInspectorOverlayButton");
            Button primary = (Button) loader.getNamespace().get("primaryContentButton");
            Button inspector = (Button) loader.getNamespace().get("inspectorButton");

            assertEquals(java.util.List.of(editorPane), areaPanes.getChildren());
            assertTrue(listLauncher.isVisible());
            assertTrue(inspectorLauncher.isVisible());

            listLauncher.fire();
            assertEquals(java.util.List.of(primaryPane), overlay.getChildren());
            assertSame(primary, scene.getFocusOwner());
            sendKey(root, KeyCode.ESCAPE);
            assertTrue(overlay.getChildren().isEmpty());
            assertSame(listLauncher, scene.getFocusOwner());

            sendKey(root, KeyCode.F7);
            assertEquals(java.util.List.of(inspectorPane), overlay.getChildren());
            assertSame(inspector, scene.getFocusOwner());
            sendKey(root, KeyCode.ESCAPE);
            assertSame(inspectorLauncher, scene.getFocusOwner());

            root.resize(1200, 720);
            assertEquals(java.util.List.of(primaryPane, editorPane, inspectorPane), areaPanes.getChildren());
            assertFalse(listLauncher.isVisible());
            stage.close();
        });
    }

    /** Delivers one Control accelerator to the Workbench root through the same key-event seam as JavaFX. */
    private static void sendControlKey(Parent root, KeyCode code) {
        root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, true, false, false));
    }

    /** Delivers one unmodified navigation key to the Workbench root. */
    private static void sendKey(Parent root, KeyCode code) {
        root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
    }

    /** Test adapter for modal platform effects; responses are consumed in user-interaction order. */
    private static final class RecordingPlatform implements WorkbenchPlatform {
        private final Deque<WorkbenchProjectFlow.Response> responses = new ArrayDeque<>();
        private int closeCount;

        /** Adds the next chooser or confirmation result. */
        void respondWith(WorkbenchProjectFlow.Response response) {
            responses.addLast(response);
        }

        /** Returns the next scripted real-platform result. */
        @Override
        public WorkbenchProjectFlow.Response complete(WorkbenchProjectFlow.Effect effect, Stage owner) {
            return responses.removeFirst();
        }

        /** Records the at-most-once final close effect without closing the test Stage. */
        @Override
        public void closeWindow(Stage owner) {
            closeCount++;
        }
    }
}
