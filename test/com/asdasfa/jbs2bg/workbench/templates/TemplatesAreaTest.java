package com.asdasfa.jbs2bg.workbench.templates;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.AreaPanes;
import com.asdasfa.jbs2bg.workbench.AreaTest;
import com.asdasfa.jbs2bg.workbench.RefusingEditSession;
import com.asdasfa.jbs2bg.workbench.UiaListHelper;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchFocusPolicy;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Landmark;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;
import com.asdasfa.jbs2bg.workbench.WorkbenchTestEnvironment;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Templates Area loaded alone through its own FXML, over a real {@link TemplatesFeature}, the shared UIA list
 * helper and a recording shell port. The Settings to Templates journeys, the BodySlide import job (a Project command
 * the shell runs), the shell's diagnostics arbitration, F6 and narrow-mode navigation stay in
 * {@code WorkbenchControllerTest}.
 */
class TemplatesAreaTest extends AreaTest<TemplatesArea> {
    // The feature the hosted Area runs over, kept so tests can read its frame as the shell's adapter accessor did.
    private TemplatesFeature feature;
    // What the context reports for live Settings, on top of the fixture's startup result.
    private boolean settingsLive = true;

    /**
     * Builds the feature and the UIA list helper the shell would, and loads the Area with the window's render scale.
     */
    @Override
    protected AreaLoader.Loaded<TemplatesArea> load(Collaborators collaborators) {
        feature = new TemplatesFeature(collaborators.flow(), collaborators.environment().settings()::snapshot,
                collaborators.clock());
        return TemplatesArea.load(feature, collaborators.pipeline(), collaborators.shell(),
                new UiaListHelper(WorkbenchFocusPolicy::ownsSceneFocus),
                collaborators.environment().settings()::snapshot, collaborators.stage().renderScaleYProperty());
    }

    /** @return the fixture's startup result, unless the test withdrew live Settings */
    @Override
    protected boolean settingsLiveAvailable() {
        return settingsLive && super.settingsLiveAvailable();
    }

    /**
     * The Area's FXML loads on its own: its non-Node root holds the three landmark panes, and the first context
     * render shows the empty catalog with creation and import available.
     */
    @Test
    void templatesAreaLoadsAloneIntoItsThreeLandmarkPanes() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            AreaPanes panes = host().module().panes();

            assertSame(hosted().node("templatesPrimaryScroll"), panes.getPrimary());
            assertSame(hosted().node("templatesEditorContent"), panes.getEditor());
            assertSame(hosted().node("templatesInspectorScroll"), panes.getInspector());
            assertTrue(hosted().<ListView<?>>node("sliderPresetList").getItems().isEmpty());
            assertFalse(hosted().<Button>node("createSliderPresetButton").isDisabled());
            assertFalse(hosted().<Button>node("importBodySlideButton").isDisabled());
            assertTrue(hosted().<Button>node("duplicateSliderPresetButton").isDisabled());
            assertTrue(hosted().<Button>node("renameSliderPresetButton").isDisabled());
            assertTrue(shell.requests().isEmpty());
        });
    }

    /**
     * The Templates controls keep the accessible names, visible texts and nesting they had in the single Workbench
     * FXML, because the packaged smoke run finds them by name, role and parent region. The expected outline is
     * transcribed from the pre-extraction {@code workbench.fxml}; texts the first render rewrites show that render.
     */
    @Test
    void templatesPanesKeepTheirAccessibleOutline() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            AreaPanes panes = host().module().panes();

            assertEquals(List.of(
                    "ScrollPane #templatesPrimaryScroll name=Slider Preset management",
                    "  VBox #templatesPrimaryContent",
                    "    HBox #templatesInfoBar name=Templates notification",
                    "      VBox",
                    "        Label #templatesInfoBarCue text=Validation",
                    "        Label #templatesInfoBarMessage text=Validation message",
                    "      Button #dismissTemplatesInfoBarButton text=_Dismiss name=Dismiss Templates validation",
                    "    Label text=_Filter Slider Presets:",
                    "    TextField #sliderPresetFilter name=Filter Slider Presets",
                    "    Label text=_Sort Slider Presets:",
                    "    ComboBox #sliderPresetSort name=Slider Preset sort",
                    "    ListView #sliderPresetList name=Slider Presets",
                    "    Label text=_Slider Preset name:",
                    "    TextField #sliderPresetNameInput name=Slider Preset name",
                    "    Button #createSliderPresetButton text=_Create Slider Preset name=Create Slider Preset",
                    "    Button #duplicateSliderPresetButton text=_Duplicate Slider Preset"
                            + " name=Duplicate Slider Preset",
                    "    Button #removeSliderPresetButton text=_Remove Slider Preset name=Remove Slider Preset",
                    "    Button #clearSliderPresetsButton text=C_lear visible Slider Presets"
                            + " name=Clear visible Slider Presets",
                    "    Button #importBodySlideButton text=_Import BodySlide Presets…"
                            + " name=Import BodySlide Presets"),
                    outline(panes.getPrimary()));
            assertEquals(List.of(
                    "VBox #templatesEditorContent",
                    "  Label #templateEditorFocusTarget text=No Slider Preset selected name=Templates editor",
                    "  Label #templateProfileText text=Select a Slider Preset to browse its profile."
                            + " name=Slider Preset profile",
                    "  Label #templateChoiceCountText text=No slider choices name=Slider choice count",
                    "  HBox",
                    "    Label text=_Profile:",
                    "    ComboBox #sliderPresetProfile name=Slider Preset profile",
                    "  ScrollPane #sliderChoicesScroll name=Set Sliders editor",
                    "    VBox #sliderChoiceRows"),
                    outline(panes.getEditor()));
            assertEquals(List.of(
                    "ScrollPane #templatesInspectorScroll name=Templates inspector controls",
                    "  VBox #templatesInspectorContent",
                    "    Label text=Slider Preset inspector",
                    "    Label #templateSelectionText text=No Slider Preset selected name=Selected Slider Preset",
                    "    Button #goToSetSlidersButton text=_Go to Set Sliders name=Go to Set Sliders",
                    "    Button #renameSliderPresetButton text=_Rename Slider Preset (F2)"
                            + " name=Rename selected Slider Preset",
                    "    Label text=Gang controls",
                    "    Label text=Set every enabled Slider choice atomically.",
                    "    HBox",
                    "      Button #zeroAllSliderChoicesButton text=0 All"
                            + " name=Set all Slider choice values to 0 percent",
                    "      Button #fiftyAllSliderChoicesButton text=50 All"
                            + " name=Set all Slider choice values to 50 percent",
                    "      Button #hundredAllSliderChoicesButton text=100 All"
                            + " name=Set all Slider choice values to 100 percent",
                    "    HBox",
                    "      Button #zeroAllMinimumButton text=0 All-Min"
                            + " name=Set all Slider choice minimum values toward 0 percent without crossing each"
                            + " maximum",
                    "      Button #fiftyAllMinimumButton text=50 All-Min"
                            + " name=Set all Slider choice minimum values toward 50 percent without crossing each"
                            + " maximum",
                    "      Button #hundredAllMinimumButton text=100 All-Min"
                            + " name=Set all Slider choice minimum values toward 100 percent without crossing each"
                            + " maximum",
                    "    HBox",
                    "      Button #zeroAllMaximumButton text=0 All-Max"
                            + " name=Set all Slider choice maximum values toward 0 percent without crossing each"
                            + " minimum",
                    "      Button #fiftyAllMaximumButton text=50 All-Max"
                            + " name=Set all Slider choice maximum values toward 50 percent without crossing each"
                            + " minimum",
                    "      Button #hundredAllMaximumButton text=100 All-Max"
                            + " name=Set all Slider choice maximum values toward 100 percent without crossing each"
                            + " minimum",
                    "    CheckBox #gangAllCheck text=Gang _All name=Gang all Slider choice values",
                    "    Slider #gangAllSlider name=Gang all Slider choice value",
                    "    Label #gangAllValue text=100%",
                    "    CheckBox #gangMinimumCheck text=Gang All _Minimum name=Gang all minimum Slider choice values",
                    "    Slider #gangMinimumSlider name=Gang all minimum Slider choice value",
                    "    Label #gangMinimumValue text=100%",
                    "    CheckBox #gangMaximumCheck text=Gang All Ma_ximum name=Gang all maximum Slider choice values",
                    "    Slider #gangMaximumSlider name=Gang all maximum Slider choice value",
                    "    Label #gangMaximumValue text=100%"),
                    outline(panes.getInspector()));
            assertEquals("Minimum values stop at each Slider choice maximum.",
                    hosted().<CheckBox>node("gangMinimumCheck").getAccessibleHelp());
            assertEquals("Maximum values stop at each Slider choice minimum.",
                    hosted().<CheckBox>node("gangMaximumCheck").getAccessibleHelp());
        });
    }

    /**
     * The Templates JavaFX adapter renders immutable feature frames and translates controls into typed feature intents
     * without retaining a row-index selection.
     */
    @Test
    void templatesControlsCreateAndFilterThroughTheAuthoritativeProjectFlow() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            ListView<SliderPresetSnapshot> presets = hosted().node("sliderPresetList");
            TextField name = hosted().node("sliderPresetNameInput");
            TextField filter = hosted().node("sliderPresetFilter");
            Button create = hosted().node("createSliderPresetButton");

            assertEquals(List.of("Alpha"), presets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            name.setText(" Beta ");
            create.fire();
            assertEquals(List.of("Alpha", "Beta"), presets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertEquals("Beta", presets.getSelectionModel().getSelectedItem().getName());

            filter.setText("alp");
            assertEquals(List.of("Alpha"), presets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertNull(presets.getSelectionModel().getSelectedItem());
            filter.clear();
            assertEquals(List.of("Alpha", "Beta"), presets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertNull(presets.getSelectionModel().getSelectedItem());
            assertEquals(flow.frame().sequence(), feature.frame().projectSequence());
            assertSame(hosted().module().panes(), hosted().node("panes"));
        });
    }

    /**
     * The in-place Templates editor renders one accessible group per immutable Slider choice, keeps focus on a row
     * through its authoritative edit, and switches profile without losing the selected Slider Preset identity.
     */
    @Test
    void templatesEditorRendersAccessibleRowsAndPreservesFocusAcrossEdits() throws Exception {
        useFixture(WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(temporaryDirectory,
                Map.of("Waist", new DefaultSliderValue(0.2f, 0.8f)),
                Map.of("Arms", new DefaultSliderValue(0.1f, 0.5f)))));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            try {
                hosted().show();
                ListView<SliderPresetSnapshot> presets = hosted().node("sliderPresetList");
                presets.getSelectionModel().select(0);
                VBox rows = hosted().node("sliderChoiceRows");
                SliderChoiceRow waist = (SliderChoiceRow) rows.getChildren().getFirst();

                assertEquals("Slider choice Waist in Slider Preset Alpha", waist.getAccessibleText());
                assertEquals("Waist@0.8", waist.previewControl().getText());
                assertEquals("Waist Minimum in Slider Preset Alpha", waist.minimumControl().getAccessibleText());
                waist.minimumControl().requestFocus();
                assertEquals(SliderChoiceRow.FocusControl.MINIMUM,
                        waist.focusedControl(hosted().stage().getScene().getFocusOwner()).orElseThrow());
                assertSame(waist.minimumControl(), waist.control(SliderChoiceRow.FocusControl.MINIMUM));
                CheckBox enabled = waist.enabledControl();
                enabled.requestFocus();
                enabled.fire();

                assertSame(enabled, hosted().stage().getScene().getFocusOwner());
                assertFalse(flow.frame().snapshot().getSliderPresets().getFirst().getSliderChoices().getFirst()
                        .isEnabled());
                assertEquals(0, shell.feedbackFrame().activities().size());
                assertTrue(statusText().contains("changed"));

                ComboBox<TemplatesFeature.Profile> profile = hosted().node("sliderPresetProfile");
                profile.setValue(TemplatesFeature.Profile.UUNP);
                profile.fireEvent(new ActionEvent());

                assertEquals("Alpha", presets.getSelectionModel().getSelectedItem().getName());
                assertEquals("Arms", ((SliderChoiceRow) rows.getChildren().getFirst()).choiceName());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * F2 exposes one inline row editor whose rejected diagnostics retain the draft; a valid retry restores focusable
     * selection to the renamed identity and the final Esc tier clears it without retargeting.
     */
    @Test
    void templatesInlineRenameIsKeyboardCompleteAcrossRejectionAndSelectionClear() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));

        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            try {
                hosted().show();
                Parent root = hosted().stage().getScene().getRoot();
                ListView<SliderPresetSnapshot> presets = hosted().node("sliderPresetList");
                presets.getSelectionModel().select(0);

                assertTrue(area.handleKey(key(KeyCode.F2)));
                root.applyCss();
                root.layout();
                TextField rename = area.activeRenameField().orElseThrow();
                rename.setText("Beta");
                rename.fireEvent(new ActionEvent());

                assertEquals("Beta", feature.frame().rename().orElseThrow().draft());
                assertFalse(feature.frame().rename().orElseThrow().diagnostics().isEmpty());
                assertEquals("Alpha", presets.getSelectionModel().getSelectedItem().getName());
                assertTrue(hosted().<HBox>node("templatesInfoBar").isVisible());
                assertTrue(shell.feedbackFrame().infoBar().isEmpty());
                assertEquals(1, shell.feedbackFrame().activities().size());
                assertTrue(statusText().contains("Validation"));

                rename.setText("Gamma");
                rename.fireEvent(new ActionEvent());

                assertTrue(feature.frame().rename().isEmpty());
                assertEquals("Gamma", presets.getSelectionModel().getSelectedItem().getName());
                // Without a rename the Area leaves Escape to the shell, whose key map then calls escape().
                assertFalse(area.handleKey(key(KeyCode.ESCAPE)));
                assertTrue(area.escape());
                assertNull(presets.getSelectionModel().getSelectedItem());
                assertTrue(feature.frame().selection().isEmpty());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * Templates destructive commands publish typed confirmations first, honor Cancel, and apply the captured selected
     * or visible identity set only after the matching named action.
     */
    @Test
    void templatesRemoveAndClearVisibleRequireSafeTypedConfirmation() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));
        flow.apply(SliderPresetEdits.create("Gamma"));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            ListView<SliderPresetSnapshot> presets = hosted().node("sliderPresetList");
            Button remove = hosted().node("removeSliderPresetButton");
            Button clear = hosted().node("clearSliderPresetsButton");
            TextField filter = hosted().node("sliderPresetFilter");
            presets.getSelectionModel().select(0);

            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CANCEL);
            remove.fire();
            assertEquals(List.of("Alpha", "Beta", "Gamma"), flow.frame().snapshot().getSliderPresets().stream()
                    .map(SliderPresetSnapshot::getName).toList());

            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.REMOVE);
            remove.fire();
            assertEquals(List.of("Beta", "Gamma"), flow.frame().snapshot().getSliderPresets().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertNull(presets.getSelectionModel().getSelectedItem());

            filter.setText("bet");
            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CLEAR);
            clear.fire();
            assertEquals(List.of("Gamma"), flow.frame().snapshot().getSliderPresets().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertTrue(feature.frame().visiblePresets().isEmpty());
        });
    }

    /**
     * Refilling a genuinely empty Templates list replaces its JavaFX node so Windows UI Automation receives a fresh
     * virtualized child subtree instead of retaining the provider's stale empty tree.
     */
    @Test
    void templatesEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host().module();
            ListView<SliderPresetSnapshot> initial = hosted().node("sliderPresetList");
            TextField name = hosted().node("sliderPresetNameInput");
            Button create = hosted().node("createSliderPresetButton");

            name.setText("Alpha");
            create.fire();

            assertNotSame(initial, area.sliderPresetListNode());
            assertEquals(List.of("Alpha"), area.sliderPresetListNode().getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            hosted().stage().getScene().getRoot().applyCss();
            assertTrue(area.sliderPresetListNode().getPrefHeight() >= 28.0);
            assertTrue(area.sliderPresetListNode().getPrefHeight() < 56.0);
            assertEquals(28.0, area.sliderPresetListNode().getFixedCellSize());
            assertSame(name.getParent(), area.sliderPresetListNode().getParent());
        });
    }

    /** A persistent preview diagnostic must not turn a successful Duplicate into failed Activity validation. */
    @Test
    void templatesDuplicateWithUnavailablePreviewRecordsTheMutation() throws Exception {
        useFixture(WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installStandardOutput(temporaryDirectory,
                Map.of("Overflow", Float.valueOf(Float.MAX_VALUE)), List.of())));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.setSliderChoice("Alpha", new SliderChoiceSnapshot(
                "Overflow", false, Integer.valueOf(10), Integer.valueOf(200),
                10, 200, 100, 100, false)));

        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            area.sliderPresetListNode().getSelectionModel().selectFirst();
            hosted().<TextField>node("sliderPresetNameInput").setText("Beta");
            hosted().<Button>node("duplicateSliderPresetButton").fire();

            assertEquals(List.of("Alpha", "Beta"), flow.frame().snapshot().getSliderPresets().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertEquals("TEMPLATE_PREVIEW_NON_FINITE", feature.frame().diagnostics().getFirst().getCode());
            List<WorkbenchFeedback.ActivityRecord> activity = shell.feedbackFrame().activities();
            assertEquals(1, activity.size(), activity.stream()
                    .map(item -> item.operation() + ": " + item.message()).toList().toString());
            assertEquals("Duplicate Slider Preset", activity.getFirst().operation());
            assertEquals(WorkbenchFeedback.Disposition.COMPLETED, activity.getFirst().disposition());
        });
    }

    /**
     * Each landmark follows the Templates selection: the catalog, the first Slider choice row once a Slider Preset
     * with choices is selected, and Rename once it is usable. Ctrl+K asks the shell to reveal the primary content
     * before focusing the filter, and Escape clears only a selection that exists.
     */
    @Test
    void focusNodesSearchAndEscapeFollowTheTemplatesSelection() throws Exception {
        useFixture(WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(temporaryDirectory,
                Map.of("Waist", new DefaultSliderValue(0.2f, 0.8f)), Map.of())));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            try {
                hosted().show();

                assertSame(area.sliderPresetListNode(), area.focusNode(Landmark.PRIMARY_CONTENT));
                assertSame(hosted().node("templateEditorFocusTarget"), area.focusNode(Landmark.EDITOR));
                assertSame(hosted().node("templateSelectionText"), area.focusNode(Landmark.INSPECTOR),
                        "without a selection Rename is unavailable, so the inspector starts at its selection text");
                assertSame(area.sliderPresetListNode(), area.defaultFocusNode());
                for (Landmark landmark : List.of(Landmark.RAIL, Landmark.NPC_DATABASE_LAUNCHER,
                        Landmark.PRIMARY_LAUNCHER, Landmark.INSPECTOR_LAUNCHER, Landmark.OUTPUT_LAUNCHER,
                        Landmark.OUTPUT, Landmark.ACTIVITY, Landmark.STATUS))
                    assertNull(area.focusNode(landmark), landmark.name());
                assertFalse(area.escape(), "Escape without a selection is left to the shell");

                area.focusSearch();
                assertEquals(List.of("revealPrimaryContent()"), shell.requests());
                assertSame(hosted().node("sliderPresetFilter"), hosted().stage().getScene().getFocusOwner());

                area.sliderPresetListNode().getSelectionModel().selectFirst();
                SliderChoiceRow waist = (SliderChoiceRow) hosted().<VBox>node("sliderChoiceRows")
                        .getChildren().getFirst();
                assertSame(waist.enabledControl(), area.focusNode(Landmark.EDITOR));
                assertSame(hosted().node("renameSliderPresetButton"), area.focusNode(Landmark.INSPECTOR));

                assertTrue(area.escape());
                assertNull(area.sliderPresetListNode().getSelectionModel().getSelectedItem());
                assertSame(hosted().node("templateEditorFocusTarget"), area.focusNode(Landmark.EDITOR));
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * F2 and Escape while renaming are the Area's own keys, offered before the shell's navigation: F2 always starts a
     * rename, Escape is taken only while a rename is open, and every other key is left to the shell.
     */
    @Test
    void renameKeysAreTheAreasOwnAndEscapeIsTakenOnlyWhileRenaming() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            try {
                hosted().show();
                assertFalse(area.handleKey(key(KeyCode.ESCAPE)), "no rename is open");
                assertFalse(area.handleKey(key(KeyCode.F6)));
                assertFalse(area.handleKey(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.K,
                        false, true, false, false)), "Ctrl+K belongs to the shell's key map");

                area.sliderPresetListNode().getSelectionModel().selectFirst();
                assertTrue(area.handleKey(key(KeyCode.F2)));
                assertTrue(feature.frame().rename().isPresent());

                assertTrue(area.handleKey(key(KeyCode.ESCAPE)));
                assertTrue(feature.frame().rename().isEmpty());
                assertEquals("Alpha", area.sliderPresetListNode().getSelectionModel().getSelectedItem().getName(),
                        "cancelling a rename keeps the selection for the next Escape tier");
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * BodySlide import is a Project command that lives in the Templates pane: the launcher asks the shell to run it,
     * and the Area writes its availability from the context alone (live Settings, no job, mutations admitted).
     */
    @Test
    void bodySlideImportGoesThroughTheShellAndWaitsOnLiveSettings() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            host();
            Button importButton = hosted().node("importBodySlideButton");

            importButton.fire();
            assertEquals(List.of("importBodySlidePresets()"), shell.requests());

            settingsLive = false;
            hosted().renderContext();
            assertTrue(importButton.isDisabled());
            assertFalse(hosted().<Button>node("createSliderPresetButton").isDisabled(),
                    "only the import consumes Settings values");
            settingsLive = true;
            hosted().renderContext();
            assertFalse(importButton.isDisabled());
        });
    }

    /**
     * The Templates mutation controls and the import follow the context's job gate: a Project-exclusive job (a
     * BodySlide import) disables them, and they come back once it settles.
     */
    @Test
    void mutationControlsFollowTheContextsJobGate() throws Exception {
        useFixture(WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(temporaryDirectory,
                Map.of("Waist", new DefaultSliderValue(0.2f, 0.8f)), Map.of())));
        java.nio.file.Path source = temporaryDirectory.resolve("blocking-import.xml").toAbsolutePath().normalize();
        java.nio.file.Files.writeString(source, "<SliderPresets><Preset name=\"Blocking Import\"/></SliderPresets>");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The import is settled by the test before prolonged feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Alpha"));
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            area.sliderPresetListNode().getSelectionModel().selectFirst();
            List<Node> mutations = new ArrayList<>(List.of(hosted().node("createSliderPresetButton"),
                    hosted().node("duplicateSliderPresetButton"), hosted().node("renameSliderPresetButton"),
                    hosted().node("removeSliderPresetButton"), hosted().node("clearSliderPresetsButton"),
                    hosted().node("importBodySlideButton"), hosted().node("sliderPresetProfile"),
                    hosted().node("fiftyAllSliderChoicesButton"), hosted().node("gangAllCheck")));
            mutations.add(((SliderChoiceRow) hosted().<VBox>node("sliderChoiceRows").getChildren().getFirst())
                    .enabledControl());
            assertTrue(mutations.stream().noneMatch(Node::isDisabled));

            WorkbenchProjectFlow.Effect chooser = hosted().flow()
                    .request(WorkbenchProjectFlow.Intent.IMPORT_BODYSLIDE).effect().orElseThrow();
            hosted().flow().respond(chooser.token(), WorkbenchProjectFlow.Response.selectedSources(List.of(source)));
            assertTrue(jobs.frame().projectMutationsBlocked());

            assertEquals(List.of(), mutations.stream().filter(node -> !node.isDisabled()).map(Node::getId).toList());
            worker.runNext();
            assertFalse(jobs.frame().projectMutationsBlocked());
            assertTrue(mutations.stream().noneMatch(Node::isDisabled));
        });
    }

    /**
     * The shell hands the Area the window's render scale, and the catalog height re-evaluates when it changes, so a
     * window moved to a screen with another scale re-snaps a short catalog.
     */
    @Test
    void catalogHeightFollowsTheWindowRenderScale() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host().module();
            Set<String> invalidated = new java.util.TreeSet<>();
            area.sliderPresetListNode().prefHeightProperty().addListener(
                    (javafx.beans.Observable observable) -> invalidated.add("Slider Presets"));
            // A bound property reports invalidation only once it has been read since its last change.
            area.sliderPresetListNode().getPrefHeight();

            hosted().stage().setRenderScaleY(hosted().stage().getRenderScaleY() + 0.5);

            assertEquals(Set.of("Slider Presets"), invalidated);
        });
    }

    /**
     * Templates words its own feedback and hands it to the shell: a create in Activity and a validation failure as
     * Templates validation while the pane-local InfoBar shows the diagnostic.
     */
    @Test
    void templatesEditsPublishTheirOwnFeedbackThroughTheShellPort() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            host();
            TextField name = hosted().node("sliderPresetNameInput");
            Button create = hosted().node("createSliderPresetButton");

            name.setText("Alpha");
            create.fire();
            assertEquals(List.of("publishFeedback(Create Slider Preset completed.)"), shell.requests());
            assertEquals("Create Slider Preset", shell.feedbackFrame().activities().getFirst().operation());

            name.setText("alpha");
            create.fire();
            assertTrue(shell.requests().getLast().startsWith("publishFeedback("));
            assertEquals("Templates validation", shell.feedbackFrame().activities().getLast().operation());
            assertEquals(WorkbenchFeedback.Severity.VALIDATION, shell.feedbackFrame().status().severity());
            assertTrue(hosted().<HBox>node("templatesInfoBar").isVisible());
        });
    }

    /** The empty-to-populated Slider Preset swap moves focus to the replacement list. */
    @Test
    void sliderPresetRefillKeepsFocusOnTheReplacementList() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host().module();
            try {
                hosted().show();
                ListView<?> initial = area.sliderPresetListNode();
                initial.requestFocus();
                hosted().<TextField>node("sliderPresetNameInput").setText("Alpha");

                hosted().<Button>node("createSliderPresetButton").fire();

                assertNotSame(initial, area.sliderPresetListNode());
                assertSame(area.sliderPresetListNode(), hosted().stage().getScene().getFocusOwner());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /** A Slider choice row control regains focus after the failure dialog its edit raised. */
    @Test
    void sliderChoiceFocusReturnsAfterTheFailureDialog() throws Exception {
        useFixture(WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(temporaryDirectory,
                Map.of("Waist", new DefaultSliderValue(0.2f, 0.8f)), Map.of())));
        RefusingEditSession session = new RefusingEditSession(fixture().newSession());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        flow.apply(SliderPresetEdits.create("Alpha"));
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            try {
                hosted().show();
                area.sliderPresetListNode().getSelectionModel().select(0);
                SliderChoiceRow waist = (SliderChoiceRow) hosted().<VBox>node("sliderChoiceRows")
                        .getChildren().getFirst();
                CheckBox enabled = waist.enabledControl();
                enabled.requestFocus();
                session.refuseEdits(true);
                // While the dialog is up, something else in the main scene takes focus.
                platform.respondFailureWith(WorkbenchFeedback.DialogAction.CLOSE,
                        () -> hosted().<TextField>node("sliderPresetFilter").requestFocus());

                enabled.fire();

                SliderChoiceRow restored = (SliderChoiceRow) hosted().<VBox>node("sliderChoiceRows")
                        .getChildren().getFirst();
                assertSame(restored.enabledControl(), hosted().stage().getScene().getFocusOwner());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /** A bulk-action button regains focus after the failure dialog its edit raised. */
    @Test
    void templatesControlFocusReturnsAfterTheFailureDialog() throws Exception {
        useFixture(WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(temporaryDirectory,
                Map.of("Waist", new DefaultSliderValue(0.2f, 0.8f)), Map.of())));
        RefusingEditSession session = new RefusingEditSession(fixture().newSession());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        flow.apply(SliderPresetEdits.create("Alpha"));
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            try {
                hosted().show();
                area.sliderPresetListNode().getSelectionModel().select(0);
                Button fiftyAll = hosted().node("fiftyAllSliderChoicesButton");
                fiftyAll.requestFocus();
                session.refuseEdits(true);
                platform.respondFailureWith(WorkbenchFeedback.DialogAction.CLOSE,
                        () -> hosted().<TextField>node("sliderPresetFilter").requestFocus());

                fiftyAll.fire();

                assertSame(fiftyAll, hosted().stage().getScene().getFocusOwner());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /** F2 puts focus on the inline rename field as editing starts, and it stays there. */
    @Test
    void renameFromTheListFocusesTheRenameField() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        try {
            FxTestToolkit.runOnFxThread(() -> {
                TemplatesArea area = host(flow).module();
                hosted().show();
                Parent root = hosted().stage().getScene().getRoot();
                ListView<?> presets = area.sliderPresetListNode();
                presets.getSelectionModel().select(0);
                presets.requestFocus();

                // The shell's key map offers F2 to the active Area before its own navigation keys.
                assertTrue(area.handleKey(key(KeyCode.F2)));
                root.applyCss();
                root.layout();

                assertSame(area.activeRenameField().orElseThrow(), hosted().stage().getScene().getFocusOwner());
            });
            FxTestToolkit.runOnFxThread(() -> assertSame(hosted().module().activeRenameField().orElseThrow(),
                    hosted().stage().getScene().getFocusOwner()));
        } finally {
            FxTestToolkit.runOnFxThread(() -> hosted().stage().close());
        }
    }

    /** The Rename button moves focus into the editor even though the list never owned focus. */
    @Test
    void renameFromTheInspectorButtonFocusesTheRenameField() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            try {
                hosted().show();
                Parent root = hosted().stage().getScene().getRoot();
                area.sliderPresetListNode().getSelectionModel().select(0);
                Button rename = hosted().node("renameSliderPresetButton");
                rename.requestFocus();

                rename.fire();
                root.applyCss();
                root.layout();

                assertSame(area.activeRenameField().orElseThrow(), hosted().stage().getScene().getFocusOwner());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /** The rename deferral applies only while the list or its cell owns focus, so it never steals focus back. */
    @Test
    void renameDeferralDoesNotTakeFocusFromAControlTheUserMovedTo() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        try {
            FxTestToolkit.runOnFxThread(() -> {
                TemplatesArea area = host(flow).module();
                hosted().show();
                Parent root = hosted().stage().getScene().getRoot();
                ListView<?> presets = area.sliderPresetListNode();
                presets.getSelectionModel().select(0);
                presets.requestFocus();
                assertTrue(area.handleKey(key(KeyCode.F2)));
                root.applyCss();
                root.layout();

                hosted().<TextField>node("sliderPresetFilter").requestFocus();
            });
            FxTestToolkit.runOnFxThread(() -> assertSame(hosted().node("sliderPresetFilter"),
                    hosted().stage().getScene().getFocusOwner()));
        } finally {
            FxTestToolkit.runOnFxThread(() -> hosted().stage().close());
        }
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

        try {
            FxTestToolkit.runOnFxThread(() -> {
                TemplatesArea area = host(flow).module();
                hosted().show();
                area.sliderPresetListNode().getSelectionModel().select(0);
            });
            // Let the virtualized Slider Preset cells materialize before the edit lifecycle addresses them.
            FxTestToolkit.awaitLayoutPulses(hosted().stage().getScene(), 2);
            FxTestToolkit.runOnFxThread(() -> {
                TemplatesArea area = hosted().module();
                ListView<SliderPresetSnapshot> presets = area.sliderPresetListNode();
                assertEquals(-1, presets.getEditingIndex());

                // The shell's key map offers F2, and Escape while renaming, to the active Area first.
                area.handleKey(key(KeyCode.F2));

                assertEquals(0, presets.getEditingIndex());
                TextField rename = area.activeRenameField().orElseThrow();
                ListCell<?> cell = owningCell(rename);
                assertEquals("Enter commits the name. Escape cancels and restores the Slider Preset row.",
                        rename.getAccessibleHelp());

                rename.setText("Beta");
                rename.fireEvent(new ActionEvent());

                String rejection = rename.getAccessibleHelp();
                assertTrue(rejection.contains("SLIDER_PRESET_NAME_DUPLICATE"), rejection);
                assertEquals(0, presets.getEditingIndex(), "a rejected commit must keep the edit open");
                assertSame(rename, area.activeRenameField().orElseThrow(),
                        "the rejected draft must update the existing editor, not a recreated cell");
                assertSame(cell, owningCell(rename));
                assertEquals("Rename Slider Preset Alpha", cell.getAccessibleText());
                assertEquals(rejection, cell.getAccessibleHelp());

                area.handleKey(key(KeyCode.ESCAPE));

                assertEquals(-1, presets.getEditingIndex());
                assertTrue(area.activeRenameField().isEmpty());
                assertNull(cell.getGraphic());
                assertEquals("Alpha", cell.getText());
                assertEquals("Alpha", cell.getAccessibleText());

                area.handleKey(key(KeyCode.F2));
                assertEquals(0, presets.getEditingIndex());
                TextField retry = area.activeRenameField().orElseThrow();
                retry.setText("Gamma");
                retry.fireEvent(new ActionEvent());

                assertEquals(-1, presets.getEditingIndex());
                assertTrue(area.activeRenameField().isEmpty());
                assertEquals("Gamma", presets.getSelectionModel().getSelectedItem().getName());
            });
        } finally {
            FxTestToolkit.runOnFxThread(() -> hosted().stage().close());
        }
    }

    /**
     * Editing a Slider choice reuses its row adapter, so the UIA subtree (and with it focus and the RangeValue
     * providers) survives the re-render instead of being rebuilt for every Project edit.
     */
    @Test
    void sliderChoiceEditsKeepTheSameRowNode() throws Exception {
        useFixture(WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(
                temporaryDirectory.resolve("settings"),
                Map.of("Arms", new DefaultSliderValue(0.1f, 0.9f),
                        "Waist", new DefaultSliderValue(0.2f, 0.8f)),
                Map.of())));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            TemplatesArea area = host(flow).module();
            area.sliderPresetListNode().getSelectionModel().select(0);
            VBox rows = hosted().node("sliderChoiceRows");
            List<Node> before = List.copyOf(rows.getChildren());
            assertEquals(List.of("Arms", "Waist"), before.stream()
                    .map(row -> ((SliderChoiceRow) row).choiceName()).toList());
            SliderChoiceRow waist = (SliderChoiceRow) before.get(1);
            CheckBox enabled = waist.enabledControl();
            Slider minimum = waist.minimumControl();

            enabled.fire();
            hosted().<Button>node("fiftyAllSliderChoicesButton").fire();

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
        });
    }

    /** @return the status line the shell would render from the latest feedback frame */
    private String statusText() {
        WorkbenchFeedback.StatusProjection status = shell.feedbackFrame().status();
        return status.severity().cue() + " — " + status.dispositionText() + " — " + status.message();
    }

    /** @return an unmodified key press, as the shell's key map hands it to the active Area */
    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    /** Returns the virtualized ListCell whose graphic currently contains {@code node}. */
    private static ListCell<?> owningCell(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof ListCell<?> cell)
                return cell;
        }
        throw new AssertionError("No ListCell owns " + node);
    }

    /** Lists a pane's nodes depth-first as type, id, Labeled text and accessible name, one line each. */
    private static List<String> outline(Node pane) {
        List<String> lines = new ArrayList<>();
        outline(pane, 0, lines);
        return lines;
    }

    /** Appends one node's line and its children's at the next depth; a Control is a leaf. */
    private static void outline(Node node, int depth, List<String> lines) {
        StringBuilder line = new StringBuilder("  ".repeat(depth)).append(node.getClass().getSimpleName());
        if (node.getId() != null)
            line.append(" #").append(node.getId());
        if (node instanceof Labeled labeled && labeled.getText() != null)
            line.append(" text=").append(labeled.getText());
        if (node.getAccessibleText() != null)
            line.append(" name=").append(node.getAccessibleText());
        lines.add(line.toString());
        if (node instanceof ScrollPane scroll && scroll.getContent() != null)
            outline(scroll.getContent(), depth + 1, lines);
        else if (node instanceof Parent parent && !(node instanceof Control))
            parent.getChildrenUnmodifiable().forEach(child -> outline(child, depth + 1, lines));
    }
}
