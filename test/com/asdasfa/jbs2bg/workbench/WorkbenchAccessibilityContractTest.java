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
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
     * Refilling an empty NPC Morph Assignments list replaces its JavaFX node in place, so the Windows UIA provider
     * discovers the new virtualized rows instead of keeping the stale empty subtree.
     */
    @Test
    void npcMorphAssignmentsEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new UnusedPlatform(), fixture().environment());
            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ListView<NpcMorphAssignmentSnapshot> initial =
                    (ListView<NpcMorphAssignmentSnapshot>) LoadedWorkbench.node(loader, "npcMorphAssignmentList");
            Parent owner = initial.getParent();
            int position = owner.getChildrenUnmodifiable().indexOf(initial);
            assertTrue(initial.getItems().isEmpty());

            createNpc(loader, "Lyra", "Lyra01");

            ListView<NpcMorphAssignmentSnapshot> replacement = controller.npcMorphAssignmentListNode();
            assertNotSame(initial, replacement);
            assertNull(initial.getParent(), "the stale empty node must leave the scene graph");
            assertSame(owner, replacement.getParent());
            assertEquals(position, owner.getChildrenUnmodifiable().indexOf(replacement));
            assertEquals("npcMorphAssignmentList", replacement.getId());
            assertEquals("NPC Morph Assignments", replacement.getAccessibleText());
            assertEquals(List.of("Lyra"), replacement.getItems().stream()
                    .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
            stage.close();
        });
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
            controller.sliderPresetListNode().getSelectionModel().select(0);
            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            controller.customMorphTargetListNode().getSelectionModel().select(0);
            ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
            ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
            ((Button) LoadedWorkbench.node(loader, "generateOutputButton")).fire();

            Map<String, ObservableList<?>> guarded = new LinkedHashMap<>();
            guarded.put("Slider Presets", controller.sliderPresetListNode().getItems());
            guarded.put("Slider choice rows",
                    ((VBox) loader.getNamespace().get("sliderChoiceRows")).getChildren());
            guarded.put("Custom Morph Targets", controller.customMorphTargetListNode().getItems());
            guarded.put("NPC Morph Assignments", controller.npcMorphAssignmentListNode().getItems());
            guarded.put("Assigned Slider Presets", controller.assignedMorphSliderPresetListNode().getItems());
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
     * Inline rename drives ListView's edit lifecycle instead of {@code refresh()}: F2 moves {@code getEditingIndex()}
     * to the renamed row, a rejected commit updates the same editor cell in place, and Escape or a valid commit ends
     * the edit so the same cell shows the ordinary accessible row again.
     */
    @Test
    void inlineRenameMovesTheEditingIndexAndUpdatesTheRenameCellInPlace() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));
        AtomicReference<Stage> stageHolder = new AtomicReference<>();
        AtomicReference<WorkbenchController> controllerHolder = new AtomicReference<>();

        try {
            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
                Parent root = loader.load();
                WorkbenchController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1300.0, 800.0));
                controller.attach(flow, stage, new UnusedPlatform(), fixture().environment());
                stage.show();
                stageHolder.set(stage);
                controllerHolder.set(controller);
                controller.sliderPresetListNode().getSelectionModel().select(0);
            });
            // Let the virtualized Slider Preset cells materialize before the edit lifecycle addresses them.
            FxTestToolkit.awaitLayoutPulses(stageHolder.get().getScene(), 2);
            FxTestToolkit.runOnFxThread(() -> {
                WorkbenchController controller = controllerHolder.get();
                Parent root = stageHolder.get().getScene().getRoot();
                ListView<SliderPresetSnapshot> presets = controller.sliderPresetListNode();
                assertEquals(-1, presets.getEditingIndex());

                sendKey(root, KeyCode.F2);

                assertEquals(0, presets.getEditingIndex());
                TextField rename = controller.activeRenameField().orElseThrow();
                ListCell<?> cell = owningCell(rename);
                assertEquals("Enter commits the name. Escape cancels and restores the Slider Preset row.",
                        rename.getAccessibleHelp());

                rename.setText("Beta");
                rename.fireEvent(new ActionEvent());

                String rejection = rename.getAccessibleHelp();
                assertTrue(rejection.contains("SLIDER_PRESET_NAME_DUPLICATE"), rejection);
                assertEquals(0, presets.getEditingIndex(), "a rejected commit must keep the edit open");
                assertSame(rename, controller.activeRenameField().orElseThrow(),
                        "the rejected draft must update the existing editor, not a recreated cell");
                assertSame(cell, owningCell(rename));
                assertEquals("Rename Slider Preset Alpha", cell.getAccessibleText());
                assertEquals(rejection, cell.getAccessibleHelp());

                rename.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));

                assertEquals(-1, presets.getEditingIndex());
                assertTrue(controller.activeRenameField().isEmpty());
                assertNull(cell.getGraphic());
                assertEquals("Alpha", cell.getText());
                assertEquals("Alpha", cell.getAccessibleText());

                sendKey(root, KeyCode.F2);
                assertEquals(0, presets.getEditingIndex());
                TextField retry = controller.activeRenameField().orElseThrow();
                retry.setText("Gamma");
                retry.fireEvent(new ActionEvent());

                assertEquals(-1, presets.getEditingIndex());
                assertTrue(controller.activeRenameField().isEmpty());
                assertEquals("Gamma", presets.getSelectionModel().getSelectedItem().getName());
            });
        } finally {
            FxTestToolkit.runOnFxThread(() -> {
                if (stageHolder.get() != null)
                    stageHolder.get().close();
            });
        }
    }

    /**
     * Editing a Slider choice reuses its row adapter, so the UIA subtree (and with it focus and the RangeValue
     * providers) survives the re-render instead of being rebuilt for every Project edit.
     */
    @Test
    void sliderChoiceEditsKeepTheSameRowNode() throws Exception {
        fixture = WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(
                temporaryDirectory.resolve("settings"),
                Map.of("Arms", new DefaultSliderValue(0.1f, 0.9f),
                        "Waist", new DefaultSliderValue(0.2f, 0.8f)),
                Map.of()));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new UnusedPlatform(), fixture().environment());
            controller.sliderPresetListNode().getSelectionModel().select(0);
            VBox rows = (VBox) loader.getNamespace().get("sliderChoiceRows");
            List<Node> before = List.copyOf(rows.getChildren());
            assertEquals(List.of("Arms", "Waist"), before.stream()
                    .map(row -> ((SliderChoiceRow) row).choiceName()).toList());
            SliderChoiceRow waist = (SliderChoiceRow) before.get(1);
            CheckBox enabled = waist.enabledControl();
            Slider minimum = waist.minimumControl();

            enabled.fire();
            ((Button) loader.getNamespace().get("fiftyAllSliderChoicesButton")).fire();

            SliderPresetSnapshot edited = flow.frame().snapshot().getSliderPresets().getFirst();
            assertFalse(edited.getSliderChoices().stream()
                    .filter(choice -> choice.getName().equals("Waist")).findFirst().orElseThrow().isEnabled());
            // The bulk action edits only enabled rows, so Arms changes while the disabled Waist keeps its value.
            assertEquals(50, edited.getSliderChoices().stream()
                    .filter(choice -> choice.getName().equals("Arms")).findFirst().orElseThrow()
                    .getPercentageMinimum());
            assertEquals(before, List.copyOf(rows.getChildren()));
            for (int index = 0; index < before.size(); index++)
                assertSame(before.get(index), rows.getChildren().get(index), "row " + index + " was rebuilt");
            assertSame(enabled, waist.enabledControl());
            assertSame(minimum, waist.minimumControl());
            assertFalse(enabled.isSelected());
            stage.close();
        });
    }

    /**
     * A label's {@code labelFor} is the JavaFX source of the UIA LabeledBy relationship. Every empty-to-full swap of
     * the Assigned Slider Presets list must re-point its label at the fresh node, so the relationship never stays
     * on a node that has left the scene graph.
     *
     * <p>The label's FXML {@code labelFor="$assignedMorphSliderPresetList"} is a forward reference, which FXMLLoader
     * resolves to {@code null}, so only the controller's re-pointing establishes the relationship today.</p>
     */
    @Test
    void assignedSliderPresetsLabelForFollowsEveryListSwap() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha")));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new UnusedPlatform(), fixture().environment());
            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            Label label = (Label) LoadedWorkbench.node(loader, "assignedMorphSliderPresetLabel");
            ListView<CustomMorphTargetSnapshot> targets = controller.customMorphTargetListNode();
            ListView<SliderPresetSnapshot> initial = controller.assignedMorphSliderPresetListNode();
            assertTrue(initial.getItems().isEmpty());

            targets.getSelectionModel().select(0);

            ListView<SliderPresetSnapshot> first = controller.assignedMorphSliderPresetListNode();
            assertNotSame(initial, first);
            assertSame(first, label.getLabelFor());
            assertSame(label, first.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY));
            Parent inspector = (Parent) LoadedWorkbench.node(loader, "morphsInspectorContent");
            assertSame(inspector, first.getParent());

            targets.getSelectionModel().clearSelection();
            assertTrue(controller.assignedMorphSliderPresetListNode().getItems().isEmpty());
            targets.getSelectionModel().select(0);

            ListView<SliderPresetSnapshot> second = controller.assignedMorphSliderPresetListNode();
            assertNotSame(first, second);
            assertNull(first.getParent(), "the first replacement must have left the scene graph");
            assertSame(inspector, second.getParent());
            assertSame(second, label.getLabelFor());
            assertSame(label, second.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY));
            assertNull(first.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY),
                    "the detached node must no longer claim the label");
            assertEquals(List.of("Alpha"), second.getItems().stream().map(SliderPresetSnapshot::getName).toList());
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

    /**
     * NPC Database labels keep a stable accessible name for UIA lookup and publish their changing value as HelpText;
     * the portrait status does the same, with its own guidance before any entry is selected.
     */
    @Test
    void npcDatabaseLabelsAndPortraitStatusPublishTheirValueAsHelpText() throws Exception {
        Path source = temporaryDirectory.resolve("npcs.txt");
        Files.writeString(source, "Skyrim.esm | Zq Portraitless | ZqNone01 | NordRace | 00012345\n"
                + "Skyrim.esm | Zq Second | ZqNone02 | BretonRace | 00012346\n");
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new UnusedPlatform(List.of(source)), fixture().environment());
            ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();

            Map<String, String> empty = new LinkedHashMap<>();
            empty.put("NPC Database source count", "0 sources loaded");
            empty.put("NPC Database catalog summary", "0 of 0 NPC Database entries visible; 0 filtered columns.");
            empty.put("Selected NPC Database entry", "No NPC selected");
            empty.put("NPC Database source", "Source: none");
            empty.put("NPC plugin", "Plugin: none");
            empty.put("NPC editor ID", "Editor ID: none");
            empty.put("NPC race", "Race: none");
            empty.put("NPC Form ID", "Form ID: none");
            empty.put("NPC Database diagnostics", "No source diagnostics");
            empty.put("NPC Database portrait status", "Select an NPC Database entry to inspect its portrait.");
            assertEquals(empty, npcDatabaseHelpText(loader));

            ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
            @SuppressWarnings("unchecked")
            TableView<com.asdasfa.jbs2bg.data.NPC> catalog =
                    (TableView<com.asdasfa.jbs2bg.data.NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
            catalog.getSelectionModel().select(catalog.getItems().stream()
                    .filter(npc -> npc.getName().equals("Zq Portraitless")).findFirst().orElseThrow());

            Map<String, String> selected = new LinkedHashMap<>();
            selected.put("NPC Database source count", "1 source loaded");
            selected.put("NPC Database catalog summary", "2 of 2 NPC Database entries visible; 0 filtered columns.");
            selected.put("Selected NPC Database entry", "Zq Portraitless");
            selected.put("NPC Database source", "Source: " + source);
            selected.put("NPC plugin", "Plugin: Skyrim.esm");
            selected.put("NPC editor ID", "Editor ID: ZqNone01");
            selected.put("NPC race", "Race: NordRace");
            selected.put("NPC Form ID", "Form ID: 12345");
            selected.put("NPC Database diagnostics", "No source diagnostics");
            selected.put("NPC Database portrait status", "No portrait found. Expected images/Zq Portraitless "
                    + "(ZqNone01).jpg (or .jpeg, .png, .bmp), then a display-name-only image.");
            assertEquals(selected, npcDatabaseHelpText(loader));
            stage.close();
        });
    }

    /**
     * Reads each NPC Database label the way UIA does: its stable accessible name (TEXT) mapped to its HelpText
     * (HELP). Fails if a label's displayed text and HelpText disagree, except for the portrait guidance.
     */
    private static Map<String, String> npcDatabaseHelpText(FXMLLoader loader) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String id : List.of("npcSourceCountText", "npcCatalogSummary", "npcInspectorName",
                "npcInspectorSource", "npcInspectorMaster", "npcInspectorEditorId", "npcInspectorRace",
                "npcInspectorFormId", "npcDatabaseDiagnostics", "npcDatabasePortraitStatus")) {
            Label label = (Label) LoadedWorkbench.node(loader, id);
            String help = (String) label.queryAccessibleAttribute(AccessibleAttribute.HELP);
            if (!id.equals("npcDatabasePortraitStatus") || !label.getText().equals("No NPC portrait selected"))
                assertEquals(label.getText(), help, id + " HelpText must mirror its displayed value");
            values.put((String) label.queryAccessibleAttribute(AccessibleAttribute.TEXT), help);
        }
        return values;
    }

    /** Delivers one unmodified key press to the Workbench root key map. */
    private static void sendKey(Parent root, KeyCode code) {
        root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    /** Returns the virtualized ListCell whose graphic currently contains {@code node}. */
    private static ListCell<?> owningCell(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof ListCell<?> cell)
                return cell;
        }
        throw new AssertionError("No ListCell owns " + node);
    }

    /** Creates one NPC Morph Assignment through the Morphs authoring controls. */
    private static void createNpc(FXMLLoader loader, String displayName, String editorId) {
        ((TextField) LoadedWorkbench.node(loader, "npcDisplayNameInput")).setText(displayName);
        ((TextField) LoadedWorkbench.node(loader, "npcPluginNameInput")).setText("People.esp");
        ((TextField) LoadedWorkbench.node(loader, "npcEditorIdInput")).setText(editorId);
        ((TextField) LoadedWorkbench.node(loader, "npcRaceInput")).setText("NordRace");
        ((TextField) LoadedWorkbench.node(loader, "npcFormIdInput")).setText("000A12B3");
        ((Button) LoadedWorkbench.node(loader, "createNpcMorphAssignmentButton")).fire();
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
