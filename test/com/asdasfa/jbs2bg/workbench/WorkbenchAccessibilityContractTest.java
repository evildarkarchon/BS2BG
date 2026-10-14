package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 1 mechanism and contract tests for the Workbench accessibility workarounds (#128, #141).
 *
 * <p>Headless Tiers 1-2 never activate accessibility, so these tests cannot show that a workaround is needed or
 * sufficient. Each one instead pins the JavaFX-level mechanism (node identity, list-change events, edit lifecycle,
 * accessible properties) that the packaged UIA smoke run relies on, and fails if that workaround or contract is
 * removed. The UIA outcome itself is proven only at Tier 3 by {@code tools/java25/smoke-app-image.ps1}.</p>
 *
 * <p>This suite keeps the contracts that span the whole Workbench (no-op re-renders across every Area and the shell's
 * feedback, the mnemonic sweep, the typed dialogs). The single-Area workarounds (list node replacement and
 * {@code labelFor} re-pointing, the rename edit lifecycle, Slider choice row reuse, the NPC Database HelpText mirror)
 * are pinned in their Area's {@code <Area>AreaTest}.</p>
 */
class WorkbenchAccessibilityContractTest {

    @TempDir
    Path temporaryDirectory;

    // The test's one Settings store, environment, and session source; see fixture().
    private WorkbenchTestEnvironment fixture;

    /**
     * Returns this test's Workbench fixture, creating and reloading an empty store in the temporary directory on first
     * use. Tests that need seeded Settings assign {@link #fixture} before the first call.
     */
    private WorkbenchTestEnvironment fixture() {
        if (fixture == null)
            fixture = WorkbenchTestEnvironment.initialized(temporaryDirectory);
        return fixture;
    }

    /**
     * A re-render that leaves membership unchanged must not touch any guarded collection: JavaFX 25+ Windows UIA can
     * drop every virtualized cell after a no-op replacement. A theme change is the trigger because its job frames
     * re-render all five Areas and the Output drawer without changing the Project; the Activity list, which gains a
     * row for the completed theme job, is the witness that the re-render happened.
     */
    @Test
    void reRenderWithUnchangedMembershipFiresNoListChangeOnGuardedCollections() throws Exception {
        fixture = WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(
                temporaryDirectory.resolve("settings"),
                Map.of("Waist", new DefaultSliderValue(0.2f, 0.8f)),
                Map.of("Arms", new DefaultSliderValue(0.1f, 0.5f))));
        Path source = temporaryDirectory.resolve("npcs.txt");
        Files.writeString(source, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n"
                + "Skyrim.esm | Beta | Beta01 | BretonRace | 00012346\n");
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        // Beta stays unassigned, so the selected Custom Morph Target offers it as an available Slider Preset.
        flow.apply(SliderPresetEdits.create("Beta"));
        flow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha")));
        flow.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot(
                "Lyra", "People.esp", "Lyra01", "NordRace", "A12B3", List.of())));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new UnusedPlatform(List.of(source)), fixture().environment());
            stage.show();
            LoadedWorkbench.templates(controller).sliderPresetListNode().getSelectionModel().select(0);
            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            LoadedWorkbench.morphs(controller).customMorphTargetListNode().getSelectionModel().select(0);
            ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
            ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
            ((Button) LoadedWorkbench.node(loader, "generateOutputButton")).fire();

            Map<String, ObservableList<?>> guarded = new LinkedHashMap<>();
            guarded.put("Slider Presets", LoadedWorkbench.templates(controller).sliderPresetListNode().getItems());
            guarded.put("Slider choice rows",
                    ((VBox) LoadedWorkbench.node(loader, "sliderChoiceRows")).getChildren());
            guarded.put("Custom Morph Targets",
                    LoadedWorkbench.morphs(controller).customMorphTargetListNode().getItems());
            guarded.put("NPC Morph Assignments",
                    LoadedWorkbench.morphs(controller).npcMorphAssignmentListNode().getItems());
            guarded.put("Assigned Slider Presets",
                    LoadedWorkbench.morphs(controller).assignedMorphSliderPresetListNode().getItems());
            guarded.put("Available Slider Presets",
                    ((ComboBox<?>) LoadedWorkbench.node(loader, "availableMorphSliderPreset")).getItems());
            guarded.put("BoS artifacts", ((ComboBox<?>) LoadedWorkbench.node(loader, "bosArtifactChoice")).getItems());
            guarded.put("NPC Database sources",
                    ((ListView<?>) LoadedWorkbench.node(loader, "npcSourceList")).getItems());
            guarded.put("NPC Database catalog",
                    ((TableView<?>) LoadedWorkbench.node(loader, "npcCatalogTable")).getItems());
            guarded.put("Settings entries",
                    ((ListView<?>) LoadedWorkbench.node(loader, "settingsEntryList")).getItems());
            List<String> changed = new ArrayList<>();
            guarded.forEach((name, items) -> {
                assertFalse(items.isEmpty(), name + " must be populated for a no-op re-render to be observable");
                items.addListener((ListChangeListener<Object>) change -> changed.add(name));
            });
            ListView<?> activity = (ListView<?>) loader.getNamespace().get("activityList");
            int activityBefore = activity.getItems().size();

            @SuppressWarnings("unchecked")
            ComboBox<WorkbenchAppearance.ThemeChoice> theme =
                    (ComboBox<WorkbenchAppearance.ThemeChoice>) loader.getNamespace().get("themeChoice");
            theme.setValue(WorkbenchAppearance.ThemeChoice.DARK);
            theme.fireEvent(new ActionEvent());

            assertFalse(flow.jobs().frame().active());
            assertEquals("Save Theme Preference",
                    flow.jobs().frame().attempt().orElseThrow().operation().name());
            assertTrue(activity.getItems().size() > activityBefore,
                    "the theme job frames must have re-rendered the Workbench");
            assertEquals(List.of(), changed);
            stage.close();
        });
    }

    /**
     * A feedback re-render that leaves Activity membership and both severities unchanged must not touch the Activity
     * list or swap the InfoBar and status icon nodes. A status-only publish (a Custom Morph Target create) after the
     * theme job's InfoBar is the trigger; the status text is the witness that the feedback re-render happened.
     */
    @Test
    void statusOnlyFeedbackReRenderFiresNoChangeOnActivityOrSeverityIcons() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new UnusedPlatform(), fixture().environment());
            @SuppressWarnings("unchecked")
            ComboBox<WorkbenchAppearance.ThemeChoice> theme =
                    (ComboBox<WorkbenchAppearance.ThemeChoice>) loader.getNamespace().get("themeChoice");
            theme.setValue(WorkbenchAppearance.ThemeChoice.DARK);
            theme.fireEvent(new ActionEvent());
            Node infoBar = (Node) loader.getNamespace().get("infoBar");
            assertTrue(infoBar.isVisible(), "the completed theme job must leave an InfoBar to re-render");
            Label status = (Label) loader.getNamespace().get("statusText");
            String statusBefore = status.getText();
            assertTrue(statusBefore.startsWith("Success"), statusBefore);

            Map<String, ObservableList<?>> guarded = new LinkedHashMap<>();
            guarded.put("Activity", ((ListView<?>) loader.getNamespace().get("activityList")).getItems());
            guarded.put("InfoBar icon", ((Parent) loader.getNamespace().get("infoBarIconHost"))
                    .getChildrenUnmodifiable());
            guarded.put("Status icon", ((Parent) loader.getNamespace().get("statusIconHost"))
                    .getChildrenUnmodifiable());
            List<String> changed = new ArrayList<>();
            guarded.forEach((name, items) -> {
                assertFalse(items.isEmpty(), name + " must be populated for a no-op re-render to be observable");
                items.addListener((ListChangeListener<Object>) change -> changed.add(name));
            });

            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            ((TextField) LoadedWorkbench.node(loader, "customMorphTargetNameInput")).setText("All|Female");
            ((Button) LoadedWorkbench.node(loader, "createCustomMorphTargetButton")).fire();

            assertEquals(1, flow.frame().snapshot().getCustomMorphTargets().size());
            assertNotEquals(statusBefore, status.getText(), "the create must have re-rendered the feedback");
            assertTrue(status.getText().startsWith("Success"), status.getText());
            assertTrue(infoBar.isVisible());
            assertEquals(List.of(), changed);
            stage.close();
        });
    }

    /**
     * Mnemonic markers never reach an accessible name. Every Workbench control whose text carries a {@code _} must
     * parse it as a mnemonic so JavaFX strips it from the UIA Name; controls with an explicit accessible name keep
     * that name instead.
     */
    @Test
    void workbenchMnemonicMarkersAreStrippedFromAccessibleNames() throws Exception {
        AtomicReference<Stage> stageHolder = new AtomicReference<>();
        AtomicReference<FXMLLoader> loaderHolder = new AtomicReference<>();

        try {
            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
                Parent root = loader.load();
                WorkbenchController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1300.0, 800.0));
                controller.attach(new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession()), stage,
                        new UnusedPlatform(), fixture().environment());
                stage.show();
                stageHolder.set(stage);
                loaderHolder.set(loader);
            });
            // Skins must exist before the sweep can reach ScrollPane content and the menu bar's labelled nodes.
            FxTestToolkit.awaitLayoutPulses(stageHolder.get().getScene(), 2);
            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = loaderHolder.get();
                Button dismiss = (Button) loader.getNamespace().get("dismissInfoBarButton");
                assertEquals("_Dismiss", dismiss.getText());
                assertEquals("Dismiss", dismiss.queryAccessibleAttribute(AccessibleAttribute.TEXT));

                List<String> marked = new ArrayList<>();
                for (Node node : stageHolder.get().getScene().getRoot().lookupAll("*")) {
                    if (!(node instanceof Labeled labeled) || labeled.getText() == null
                            || !labeled.getText().contains("_"))
                        continue;
                    marked.add(labeled.getText());
                    assertTrue(labeled.isMnemonicParsing(),
                            "'" + labeled.getText() + "' shows its mnemonic marker literally");
                    // The menu bar's internal button has no accessible text of its own; its labelled child does.
                    Object name = labeled.queryAccessibleAttribute(AccessibleAttribute.TEXT);
                    if (name != null)
                        assertFalse(name.toString().contains("_"),
                                "'" + labeled.getText() + "' exposes accessible name '" + name + "'");
                }
                assertTrue(marked.containsAll(List.of("_Theme:", "_Dismiss", "_Filter Slider Presets:",
                        "_Assigned Slider Presets:")), "the sweep must reach every Area: " + marked);
            });
        } finally {
            FxTestToolkit.runOnFxThread(() -> {
                if (stageHolder.get() != null)
                    stageHolder.get().close();
            });
        }
    }

    /**
     * Typed dialog buttons parse their mnemonics but publish the bare action label as the accessible name, and the
     * dialog help names the Escape action the same way.
     */
    @Test
    void dialogButtonAccessibleNamesOmitMnemonicMarkers() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new VBox()));
            Map<WorkbenchFeedback.DialogAction, String> names = new LinkedHashMap<>();
            for (WorkbenchFeedback.DialogSpec spec : List.of(
                    WorkbenchFeedback.DialogSpec.unsavedClose("Save changes?", "The Project has unsaved changes."),
                    WorkbenchFeedback.DialogSpec.destructiveAction("Remove?", "Remove one.",
                            WorkbenchFeedback.DialogAction.REMOVE),
                    WorkbenchFeedback.DialogSpec.destructiveAction("Clear?", "Clear all.",
                            WorkbenchFeedback.DialogAction.CLEAR),
                    WorkbenchFeedback.DialogSpec.failure("Failed", "It failed.", "details", true))) {
                Dialog<WorkbenchFeedback.DialogAction> dialog = JavaFxWorkbenchDialogs.create(spec, owner);
                for (WorkbenchFeedback.DialogAction action : spec.actions()) {
                    Button button = (Button) dialog.getDialogPane()
                            .lookupButton(JavaFxWorkbenchDialogs.buttonType(action));
                    assertTrue(button.isMnemonicParsing(), action + " must keep its keyboard mnemonic");
                    assertTrue(button.getText().contains("_"), action + " text " + button.getText());
                    names.put(action, (String) button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
                }
                assertEquals("Enter activates the safe default; Escape activates "
                        + names.get(spec.cancelAction()) + ".", dialog.getDialogPane().getAccessibleHelp());
            }
            assertEquals(Map.of(
                    WorkbenchFeedback.DialogAction.SAVE, "Save",
                    WorkbenchFeedback.DialogAction.DISCARD, "Discard",
                    WorkbenchFeedback.DialogAction.CANCEL, "Cancel",
                    WorkbenchFeedback.DialogAction.REMOVE, "Remove",
                    WorkbenchFeedback.DialogAction.CLEAR, "Clear",
                    WorkbenchFeedback.DialogAction.COPY_DETAILS, "Copy Details",
                    WorkbenchFeedback.DialogAction.RETRY, "Retry",
                    WorkbenchFeedback.DialogAction.CLOSE, "Close"), names);
            owner.close();
        });
    }

    /** Platform adapter that answers only the NPC Database source chooser; every other effect fails the test. */
    private static final class UnusedPlatform implements WorkbenchPlatform {
        private final List<Path> npcSources;

        /** Creates an adapter whose NPC Database chooser returns no sources. */
        private UnusedPlatform() {
            this(List.of());
        }

        /** Creates an adapter whose NPC Database chooser returns {@code npcSources} every time it opens. */
        private UnusedPlatform(List<Path> npcSources) {
            this.npcSources = List.copyOf(npcSources);
        }

        /** Returns the scripted NPC Database sources. */
        @Override
        public Optional<List<Path>> chooseNpcSources(Stage owner) {
            return Optional.of(npcSources);
        }

        /** Fails because these tests never request a Project chooser or confirmation. */
        @Override
        public WorkbenchProjectFlow.Response complete(WorkbenchProjectFlow.Effect effect, Stage owner) {
            throw new AssertionError("Unexpected platform effect " + effect);
        }

        /** Fails because these tests close their Stage directly. */
        @Override
        public void closeWindow(Stage owner) {
            throw new AssertionError("Unexpected window close");
        }
    }
}
