package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Area;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.FocusTarget;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Landmark;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Drives the shared focus policy over a minimal landmark scene graph, shown on the headless platform. Every
 * assertion reads the scene's focus owner or the policy's classification of it, never window activation (#123).
 * The last tests load the full Workbench to pin the controller's landmark registrations against the FXML.
 */
class WorkbenchFocusPolicyTest {

    // Settings store for the tests that load the full Workbench.
    @TempDir
    Path temporaryDirectory;

    /** A minimal Workbench-shaped scene: a rail, three Area panes, and per-landmark default nodes. */
    private static final class Landmarks {
        final Button railButton = new Button("Templates");
        final Button primaryList = new Button("primary list");
        final Button primaryOther = new Button("primary other");
        final Button launcher = new Button("launcher");
        final Button editorNode = new Button("editor");
        final Button editorOther = new Button("editor other");
        final Button inspectorNode = new Button("inspector");
        final VBox rail = new VBox(railButton);
        final VBox primary = new VBox(primaryList, new VBox(primaryOther), launcher);
        final VBox editor = new VBox(editorNode, new HBox(editorOther));
        final VBox inspector = new VBox(inspectorNode);
        final Stage stage = new Stage();
        Area activeArea = Area.TEMPLATES;
        final WorkbenchFocusPolicy policy;

        Landmarks() {
            stage.setScene(new Scene(new HBox(rail, primary, editor, inspector), 800.0, 600.0));
            policy = new WorkbenchFocusPolicy(stage, () -> activeArea, new WorkbenchFocusPolicy.AreaFocus() {
                /** Resolves each landmark to its first node; everything else has no node. */
                @Override
                public Node focusNode(FocusTarget target) {
                    return switch (target.landmark()) {
                        case RAIL -> railButton;
                        case PRIMARY_CONTENT -> primaryList;
                        case EDITOR -> editorNode;
                        case INSPECTOR -> inspectorNode;
                        default -> null;
                    };
                }

                /** The Area default is the primary list. */
                @Override
                public Node defaultNode(Area area) {
                    return primaryList;
                }
            });
            policy.landmark(primary, Landmark.PRIMARY_CONTENT)
                    .landmark(editor, Landmark.EDITOR)
                    .landmark(inspector, Landmark.INSPECTOR)
                    .landmark(launcher, Landmark.NPC_DATABASE_LAUNCHER);
            stage.show();
        }
    }

    /** A control nested anywhere under a landmark container classifies into that container's landmark. */
    @Test
    void focusOwnerClassifiesIntoItsNearestLandmarkContainer() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            try {
                scene.editorOther.requestFocus();
                assertEquals(new FocusTarget(Area.TEMPLATES, Landmark.EDITOR), scene.policy.current());

                scene.primaryOther.requestFocus();
                assertEquals(new FocusTarget(Area.TEMPLATES, Landmark.PRIMARY_CONTENT), scene.policy.current());
            } finally {
                scene.stage.close();
            }
        });
    }

    /**
     * A launcher registered inside a pane wins over the pane, as Morphs' Add NPCs launcher does inside primary
     * content; a focus owner under no registered container, or no focus owner at all, is on the rail.
     */
    @Test
    void nearestRegistrationWinsAndUnregisteredFocusFallsToTheRail() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            scene.activeArea = Area.MORPHS;
            try {
                scene.launcher.requestFocus();
                assertEquals(new FocusTarget(Area.MORPHS, Landmark.NPC_DATABASE_LAUNCHER), scene.policy.current());

                scene.railButton.requestFocus();
                assertEquals(new FocusTarget(Area.MORPHS, Landmark.RAIL), scene.policy.current());
                assertEquals(Landmark.RAIL, scene.policy.classify(null));
            } finally {
                scene.stage.close();
            }
        });
    }

    /**
     * F6 advances from the landmark that really holds the focus owner: a control nested in the editor moves to the
     * inspector, not back to primary content as a rail classification would.
     */
    @Test
    void f6CyclesFromTheFocusOwnersContainerToTheNextLandmark() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            WorkbenchNavigation navigation = new WorkbenchNavigation();
            try {
                scene.editorOther.requestFocus();
                WorkbenchNavigation.Transition cycled = scene.policy.cycle(navigation);
                assertEquals(new FocusTarget(Area.TEMPLATES, Landmark.INSPECTOR), cycled.focusTarget().orElseThrow());

                scene.policy.request(cycled.focusTarget().orElseThrow());
                assertSame(scene.inspectorNode, scene.stage.getScene().getFocusOwner());

                scene.railButton.requestFocus();
                assertEquals(new FocusTarget(Area.TEMPLATES, Landmark.PRIMARY_CONTENT),
                        scene.policy.cycle(navigation).focusTarget().orElseThrow());
            } finally {
                scene.stage.close();
            }
        });
    }

    /** Closing a satellite returns focus to the launcher that opened it while that launcher can still take focus. */
    @Test
    void satelliteCloseRestoresFocusToItsLauncher() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            try {
                scene.railButton.requestFocus();

                scene.policy.restoreAfterSatellite(scene.editorOther, Landmark.PRIMARY_CONTENT);

                assertSame(scene.editorOther, scene.stage.getScene().getFocusOwner());
            } finally {
                scene.stage.close();
            }
        });
    }

    /**
     * When a layout change hid the launcher's ancestor (its own visible bit unchanged) or disabled it, closing the
     * satellite falls back to the given landmark of the active Area.
     */
    @Test
    void satelliteCloseFallsBackToTheLandmarkWhenItsLauncherIsHiddenOrDisabled() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            try {
                scene.railButton.requestFocus();
                ((Node) scene.editorOther.getParent()).setVisible(false);
                scene.policy.restoreAfterSatellite(scene.editorOther, Landmark.INSPECTOR);
                assertSame(scene.inspectorNode, scene.stage.getScene().getFocusOwner());

                scene.railButton.requestFocus();
                scene.primaryOther.setDisable(true);
                scene.policy.restoreAfterSatellite(scene.primaryOther, Landmark.EDITOR);
                assertSame(scene.editorNode, scene.stage.getScene().getFocusOwner());
            } finally {
                scene.stage.close();
            }
        });
    }

    /** A satellite that closes because the Workbench window is closing restores nothing. */
    @Test
    void satelliteCloseRestoresNothingOnceTheWorkbenchWindowIsHidden() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            scene.railButton.requestFocus();
            scene.stage.hide();

            scene.policy.restoreAfterSatellite(scene.editorOther, Landmark.EDITOR);

            assertSame(scene.railButton, scene.stage.getScene().getFocusOwner());
        });
    }

    /** {@code ownsSceneFocus} reads the scene's focus owner, so it holds for the owner and only the owner. */
    @Test
    void ownsSceneFocusIsTrueOnlyForTheScenesFocusOwner() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            try {
                scene.editorNode.requestFocus();

                assertTrue(WorkbenchFocusPolicy.ownsSceneFocus(scene.editorNode));
                assertFalse(WorkbenchFocusPolicy.ownsSceneFocus(scene.inspectorNode));
                assertFalse(WorkbenchFocusPolicy.ownsSceneFocus(new Button("detached")));
            } finally {
                scene.stage.close();
            }
        });
    }

    /**
     * Pins the ancestor-walk classification of every keyboard-reachable control in {@code workbench.fxml} against
     * the FXML landmark containers (#146). The expected table was derived by hand from the containers: the rail and
     * the header/diagnostics strip are unregistered (RAIL); the three Area panes, the Output drawer, the Activity
     * section and the status bar are registered containers; the overlay launchers, the Output rail button and Morphs'
     * Add NPCs launcher are registered launchers. A control added to the FXML fails this test until it is placed in
     * the table, so its landmark is a deliberate decision.
     */
    @Test
    void everyWorkbenchControlClassifiesIntoItsFxmlLandmarkContainer() throws Exception {
        Map<String, Landmark> expected = new TreeMap<>();
        expect(expected, Landmark.RAIL, "templatesAreaButton", "morphsAreaButton", "npcDatabaseAreaButton",
                "settingsAreaButton", "themeChoice", "generateOutputButton", "dismissInfoBarButton", "diagnosticsText");
        expect(expected, Landmark.OUTPUT_LAUNCHER, "outputAreaButton");
        expect(expected, Landmark.PRIMARY_LAUNCHER, "showPrimaryOverlayButton");
        expect(expected, Landmark.INSPECTOR_LAUNCHER, "showInspectorOverlayButton");
        expect(expected, Landmark.NPC_DATABASE_LAUNCHER, "addNpcsFromDatabaseButton");
        expect(expected, Landmark.PRIMARY_CONTENT,
                // Templates
                "dismissTemplatesInfoBarButton", "sliderPresetFilter", "sliderPresetSort", "sliderPresetList",
                "sliderPresetNameInput", "createSliderPresetButton", "duplicateSliderPresetButton",
                "removeSliderPresetButton", "clearSliderPresetsButton", "importBodySlideButton",
                // Morphs
                "dismissMorphsInfoBarButton", "customMorphTargetFilter", "customMorphTargetSort",
                "customMorphTargetList", "customMorphTargetNameInput", "createCustomMorphTargetButton",
                "removeCustomMorphTargetButton", "clearCustomMorphTargetsButton", "npcMorphAssignmentFilter",
                "npcMorphAssignmentSort", "npcMorphAssignmentList", "npcDisplayNameInput", "npcPluginNameInput",
                "npcEditorIdInput", "npcRaceInput", "npcFormIdInput", "createNpcMorphAssignmentButton",
                "removeNpcMorphAssignmentButton", "clearNpcMorphAssignmentsButton",
                "fillEmptyNpcMorphAssignmentsButton",
                // Settings
                "settingsProfileChoice", "settingsEntryList", "newSettingsEntryName", "addSettingsEntryButton",
                // NPC Database
                "npcSourceList", "importNpcSourcesButton", "removeNpcSourceButton", "clearNpcDatabaseButton");
        expect(expected, Landmark.EDITOR,
                "templateEditorFocusTarget", "sliderPresetProfile",
                "morphTargetEditorFocusTarget",
                "settingsEntryNameInput", "settingsSmallInput", "settingsBigInput", "settingsMultiplierInput",
                "settingsInvertedCheck", "applySettingsEntryButton", "removeSettingsEntryButton",
                "backToMorphsButton", "dismissNpcPromotionInfoBarButton", "npcSortChoice", "npcNameFilterButton",
                "npcMasterFilterButton", "npcRaceFilterButton", "npcEditorIdFilterButton", "npcFormIdFilterButton",
                "clearNpcFiltersButton", "npcCatalogTable", "addAllNpcsToProjectButton", "npcPromotionDetails");
        expect(expected, Landmark.INSPECTOR,
                "templateSelectionText", "goToSetSlidersButton", "renameSliderPresetButton",
                "zeroAllSliderChoicesButton", "fiftyAllSliderChoicesButton", "hundredAllSliderChoicesButton",
                "zeroAllMinimumButton", "fiftyAllMinimumButton", "hundredAllMinimumButton",
                "zeroAllMaximumButton", "fiftyAllMaximumButton", "hundredAllMaximumButton",
                "gangAllCheck", "gangAllSlider", "gangMinimumCheck", "gangMinimumSlider",
                "gangMaximumCheck", "gangMaximumSlider",
                "morphTargetSelectionText", "openNpcPortraitViewerButton", "assignedMorphSliderPresetList",
                "removeMorphSliderPresetButton", "clearMorphSliderPresetsButton", "availableMorphSliderPreset",
                "assignMorphSliderPresetButton", "assignAllMorphSliderPresetsButton",
                "settingsNoticeText", "omitRedundantSlidersCheck", "saveSettingsButton", "reloadSettingsButton",
                "npcInspectorName", "openNpcDatabasePortraitViewerButton", "addNpcToProjectButton",
                "assignRandomNpcPresetCheck");
        expect(expected, Landmark.OUTPUT, "outputFocusTarget", "copyOutputButton", "exportOutputButton",
                "outputDrawerHeight", "outputTabs", "templatesOutputText", "morphsOutputText", "bosArtifactChoice",
                "bosOutputText", "exportSelectedBosButton");
        expect(expected, Landmark.ACTIVITY, "activityList", "retryActivityButton");
        expect(expected, Landmark.STATUS, "statusText", "cancelOperationButton");

        WorkbenchTestEnvironment fixture = WorkbenchTestEnvironment.initialized(temporaryDirectory);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture.newSession());
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new WorkbenchControllerTest.RecordingPlatform(), fixture.environment());
            // Shown so ScrollPane and TabPane skins parent their content: a focus owner is always in a showing scene.
            stage.show();
            try {
                Map<String, Landmark> actual = new TreeMap<>();
                loader.getNamespace().forEach((id, value) -> {
                    if (value instanceof Control control && control.isFocusTraversable())
                        actual.put(id, controller.focusPolicy().classify(control));
                });

                assertEquals(expected, actual);
            } finally {
                stage.close();
            }
        });
    }

    /**
     * F6 from a control the old identity chain did not list (the Templates sort, which used to fall to the rail and
     * so cycled to primary content) now advances from its real container to the editor.
     */
    @Test
    void f6FromAnUnlistedPrimaryControlAdvancesToTheEditor() throws Exception {
        WorkbenchTestEnvironment fixture = WorkbenchTestEnvironment.initialized(temporaryDirectory);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture.newSession());
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new WorkbenchControllerTest.RecordingPlatform(), fixture.environment());
            stage.show();
            try {
                Node sort = (Node) loader.getNamespace().get("sliderPresetSort");
                sort.requestFocus();
                assertSame(sort, stage.getScene().getFocusOwner());

                root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F6, false, false, false, false));

                assertSame(loader.getNamespace().get("templateEditorFocusTarget"), stage.getScene().getFocusOwner());
            } finally {
                stage.close();
            }
        });
    }

    /** Adds every id to the expected classification table under one landmark. */
    private static void expect(Map<String, Landmark> table, Landmark landmark, String... ids) {
        for (String id : ids)
            table.put(id, landmark);
    }

    /** A target whose node can take focus receives it directly. */
    @Test
    void requestFocusesTheTargetsNode() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            try {
                scene.policy.request(new FocusTarget(Area.TEMPLATES, Landmark.INSPECTOR));

                assertSame(scene.inspectorNode, scene.stage.getScene().getFocusOwner());
            } finally {
                scene.stage.close();
            }
        });
    }

    /**
     * A target with no node, or whose node is hidden, disabled or detached, falls back to the active Area's default
     * instead of leaving focus where it was.
     */
    @Test
    void requestFallsBackToTheAreaDefaultWhenTheTargetCannotTakeFocus() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Landmarks scene = new Landmarks();
            try {
                scene.railButton.requestFocus();
                scene.policy.request(new FocusTarget(Area.TEMPLATES, Landmark.OUTPUT));
                assertSame(scene.primaryList, scene.stage.getScene().getFocusOwner());

                scene.railButton.requestFocus();
                scene.editorNode.setDisable(true);
                scene.policy.request(new FocusTarget(Area.TEMPLATES, Landmark.EDITOR));
                assertSame(scene.primaryList, scene.stage.getScene().getFocusOwner());

                scene.railButton.requestFocus();
                scene.inspectorNode.setVisible(false);
                scene.policy.request(new FocusTarget(Area.TEMPLATES, Landmark.INSPECTOR));
                assertSame(scene.primaryList, scene.stage.getScene().getFocusOwner());

                scene.railButton.requestFocus();
                scene.rail.getChildren().remove(scene.railButton);
                scene.policy.request(new FocusTarget(Area.TEMPLATES, Landmark.RAIL));
                assertSame(scene.primaryList, scene.stage.getScene().getFocusOwner());
            } finally {
                scene.stage.close();
            }
        });
    }
}
