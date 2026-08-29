package com.asdasfa.jbs2bg.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.CustomConfirm;
import com.asdasfa.jbs2bg.CustomController;
import com.asdasfa.jbs2bg.CustomNotif;
import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.MainController;
import com.asdasfa.jbs2bg.SetSliderControl;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

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

    /** Repository-relative directory holding every FXML graph next to its controller. */
    private static final Path FXML_DIRECTORY = Paths.get("src", "com", "asdasfa", "jbs2bg");

    private static final String STYLESHEET = Main.class.getResource("dark.css").toExternalForm();

    /** The root window graph binds to MainController and wires the NPC Morph Assignment table. */
    @Test
    void rootWindowGraphLoadsWithItsController() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = load("main.fxml");
            assertTrue(loader.getRoot() instanceof VBox, "main.fxml root");
            MainController controller = loader.getController();
            assertNotNull(controller, "main.fxml must declare its controller");
            assertEveryFxmlFieldInjected(controller);
            TableView<?> npcTable = (TableView<?>) loader.getNamespace().get("tvNpc");
            assertEquals(List.of("Name", "Master", "Race", "EditorID", "FormID", "Slider Presets"),
                    npcTable.getColumns().stream().map(column -> column.getText()).toList());
        });
    }

    /** Every popup graph in the source tree loads against a CustomController subclass. */
    @Test
    void everyPopupGraphLoadsWithItsController() throws Exception {
        List<String> popups = fxmlFiles("popup_");
        assertFalse(popups.isEmpty(), "popup graphs must exist under " + FXML_DIRECTORY);
        FxTestToolkit.runOnFxThread(() -> {
            for (String popup : popups) {
                FXMLLoader loader = load(popup);
                assertTrue(loader.getRoot() instanceof Parent, popup + " root");
                Object controller = loader.getController();
                assertTrue(controller instanceof CustomController,
                        popup + " must bind a CustomController, got " + controller);
                assertEveryFxmlFieldInjected(controller);
            }
        });
    }

    /**
     * Custom-root graphs load into the control that owns them: the dialogs
     * through their public constructors, and the slider row through its
     * declared {@code @FXML} fields.
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

            FXMLLoader loader = new FXMLLoader(resource("setslider_control.fxml"));
            loader.setRoot(new VBox());
            loader.load();
            Map<String, Object> namespace = loader.getNamespace();
            for (Field field : SetSliderControl.class.getDeclaredFields()) {
                if (field.getAnnotation(FXML.class) == null)
                    continue;
                Object node = namespace.get(field.getName());
                assertNotNull(node, "setslider_control.fxml must declare fx:id " + field.getName());
                assertTrue(field.getType().isInstance(node),
                        field.getName() + " must be a " + field.getType().getSimpleName());
            }
        });
    }

    /** Every FXML graph on disk is one the build artifact serves, so none can be dropped silently. */
    @Test
    void everyFxmlGraphOnDiskIsServedFromTheClasspath() throws IOException {
        List<String> graphs = fxmlFiles("");
        assertTrue(graphs.size() >= 13, "expected the full FXML set, found " + graphs);
        for (String graph : graphs)
            assertNotNull(Main.class.getResource(graph), graph + " must be on the classpath");
    }

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

    /** Asserts that FXMLLoader injected every {@code @FXML} field declared by the controller's class. */
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
}
