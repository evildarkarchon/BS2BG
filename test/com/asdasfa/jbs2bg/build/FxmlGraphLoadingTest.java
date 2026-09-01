package com.asdasfa.jbs2bg.build;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.CustomConfirm;
import com.asdasfa.jbs2bg.CustomController;
import com.asdasfa.jbs2bg.CustomNotif;
import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.workbench.WorkbenchController;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Public-JavaFX harness proving that every FXML graph in the build artifact
 * loads against its real controller on the pinned toolkit: the root window
 * graph, every popup graph, and the custom-root ({@code fx:root}) graphs. Each
 * load runs on the JavaFX Application Thread under {@link FxTestToolkit}'s
 * hard timeout, so a toolkit hang fails the test instead of hanging the build.
 * <p>
 * Loading resolves every {@code fx:controller}, {@code fx:id} injection, and
 * {@code #handler} reference, which is exactly the linkage a source-filtered
 * build could never witness.
 */
class FxmlGraphLoadingTest {

    /**
     * Repository-relative directory holding every FXML graph next to its controller.
     */
    private static final Path FXML_DIRECTORY = Paths.get("src", "com", "asdasfa", "jbs2bg");

    private static final String STYLESHEET = Main.class.getResource("dark.css").toExternalForm();

    @TempDir
    Path temporaryDirectory;

    private static FXMLLoader load(String name) throws IOException {
        FXMLLoader loader = new FXMLLoader(resource(name));
        loader.load();
        return loader;
    }

    private static URL resource(String name) {
        URL url = Main.class.getResource(name);
        assertNotNull(url, name + " must be on the classpath");
        return url;
    }

    /**
     * Asserts that FXMLLoader injected every {@code @FXML} field declared by the controller's class.
     */
    private static void assertEveryFxmlFieldInjected(Object controller) throws IllegalAccessException {
        for (Field field : controller.getClass().getDeclaredFields()) {
            if (field.getAnnotation(FXML.class) == null)
                continue;
            field.setAccessible(true);
            assertNotNull(field.get(controller),
                    controller.getClass().getSimpleName() + "." + field.getName() + " must be injected");
        }
    }

    private static List<String> fxmlFiles(String prefix) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(FXML_DIRECTORY)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".fxml"))
                    .sorted()
                    .forEach(names::add);
        }
        return names;
    }

    /**
     * The sole root graph binds to WorkbenchController and exposes every typed destination by semantic name.
     */
    @Test
    void rootWindowGraphLoadsWithItsController() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = load("workbench.fxml");
            assertInstanceOf(BorderPane.class, loader.getRoot(), "workbench.fxml root");
            WorkbenchController controller = loader.getController();
            assertNotNull(controller, "workbench.fxml must declare its controller");
            assertEveryFxmlFieldInjected(controller);
            assertEquals(List.of("Templates", "Morphs", "NPC Database", "Output", "Settings"),
                    List.of("templatesAreaButton", "morphsAreaButton", "npcDatabaseAreaButton",
                                    "outputAreaButton", "settingsAreaButton").stream()
                            .map(id -> ((ToggleButton) loader.getNamespace().get(id)).getText()).toList());
        });
    }

    /**
     * The attached File commands render and mutate only through the authoritative Workbench Project flow.
     */
    @Test
    void attachedWorkbenchSaveCommandPublishesTheReturnedProjectFrame() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Paths.get("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.OPEN).effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = load("workbench.fxml");
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, temporaryDirectory, Settings.publishedState());

            assertEquals("BS2BG Preview - *recovery-source.jbs2bg", stage.getTitle());
            MenuItem save = (MenuItem) loader.getNamespace().get("saveProjectMenuItem");
            assertNotNull(save);
            save.fire();

            assertFalse(flow.frame().snapshot().isDirty());
            assertEquals("BS2BG Preview - recovery-source.jbs2bg", stage.getTitle());
            stage.close();
        });
    }

    /**
     * Every popup graph in the source tree loads against a CustomController subclass.
     */
    @Test
    void everyPopupGraphLoadsWithItsController() throws Exception {
        List<String> popups = fxmlFiles("popup_");
        assertFalse(popups.isEmpty(), "popup graphs must exist under " + FXML_DIRECTORY);
        FxTestToolkit.runOnFxThread(() -> {
            for (String popup : popups) {
                FXMLLoader loader = load(popup);
                assertInstanceOf(Parent.class, loader.getRoot(), popup + " root");
                Object controller = loader.getController();
                assertInstanceOf(CustomController.class, controller, popup + " must bind a CustomController, got " + controller);
                assertEveryFxmlFieldInjected(controller);
            }
        });
    }

    /**
     * The remaining custom-root graphs load into the dialog controls that own them.
     */
    @Test
    void customRootGraphsLoadIntoTheirOwningControls() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            CustomNotif notif = new CustomNotif(STYLESHEET, null);
            assertFalse(notif.getChildren().isEmpty(), "custom_notif.fxml must populate its root");

            CustomConfirm confirm = new CustomConfirm(STYLESHEET, null);
            assertFalse(confirm.getChildren().isEmpty(), "custom_confirm.fxml must populate its root");
            confirm.setHeaderText("header");
            confirm.setContentText("content");

        });
    }

    /**
     * Every FXML graph on disk is one the build artifact serves, so none can be dropped silently.
     */
    @Test
    void everyFxmlGraphOnDiskIsServedFromTheClasspath() throws IOException {
        List<String> graphs = fxmlFiles("");
        assertEquals(10, graphs.size(), "expected only the Workbench and unfinished workflow FXML set");
        assertTrue(graphs.contains("workbench.fxml"), "the Workbench must be the packaged root graph");
        assertFalse(graphs.contains("main.fxml"), "the replaced legacy root graph must not ship");
        for (String graph : graphs)
            assertNotNull(Main.class.getResource(graph), graph + " must be on the classpath");
    }
}
