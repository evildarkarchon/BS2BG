package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
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
