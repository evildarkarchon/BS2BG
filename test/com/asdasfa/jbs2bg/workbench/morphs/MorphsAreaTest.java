package com.asdasfa.jbs2bg.workbench.morphs;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.AreaPanes;
import com.asdasfa.jbs2bg.workbench.AreaTest;
import com.asdasfa.jbs2bg.workbench.NpcPortraitViewer;
import com.asdasfa.jbs2bg.workbench.UiaListHelper;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchFocusPolicy;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Landmark;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;
import com.asdasfa.jbs2bg.workbench.WorkbenchSatellites;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.event.ActionEvent;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.stage.Popup;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Morphs Area loaded alone through its own FXML, over a real {@link MorphsFeature}, the shared portrait viewer and
 * UIA list helper, and a recording shell port. The NPC Database to Morphs journeys, the shell's diagnostics
 * arbitration, Activity and status rendering, a job that blocks Morphs from another Area, and narrow-mode navigation
 * stay in {@code WorkbenchControllerTest}.
 */
class MorphsAreaTest extends AreaTest<MorphsArea> {
    // Every satellite root the Area registered with the stand-in for the shell's appearance, oldest first.
    private final List<Parent> satelliteRoots = new ArrayList<>();
    // The dismiss actions of the registrations still open; the shell would re-theme and dismiss exactly these.
    private final List<Runnable> openSatellites = new ArrayList<>();

    /**
     * Builds the feature, the shared portrait viewer and the UIA list helper the shell would, and loads the Area
     * through its loader with the hosting Stage's render scale.
     */
    @Override
    protected AreaLoader.Loaded<MorphsArea> load(Collaborators collaborators) {
        MorphsFeature feature = new MorphsFeature(collaborators.flow(), collaborators.clock());
        WorkbenchSatellites satellites = (root, dismiss) -> {
            satelliteRoots.add(root);
            openSatellites.add(dismiss);
            return () -> openSatellites.remove(dismiss);
        };
        NpcPortraitViewer portraits = new NpcPortraitViewer(collaborators.environment().portraitDirectory(),
                collaborators.stage(), satellites, launcher -> {
            // The shell restores focus through its focus policy; no test here closes the portrait viewer.
        });
        return MorphsArea.load(feature, collaborators.pipeline(), collaborators.shell(), portraits,
                new UiaListHelper(WorkbenchFocusPolicy::ownsSceneFocus), satellites, collaborators.stage(),
                collaborators.stage().renderScaleYProperty());
    }

    /**
     * The Area's FXML loads on its own: its non-Node root holds the three landmark panes, and the first context
     * render shows the empty catalogs with their Project mutations available.
     */
    @Test
    void morphsAreaLoadsAloneIntoItsThreeLandmarkPanes() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            AreaPanes panes = host().module().panes();

            assertSame(hosted().node("morphsPrimaryScroll"), panes.getPrimary());
            assertSame(hosted().node("morphsEditorContent"), panes.getEditor());
            assertSame(hosted().node("morphsInspectorScroll"), panes.getInspector());
            assertTrue(hosted().<ListView<?>>node("customMorphTargetList").getItems().isEmpty());
            assertTrue(hosted().<ListView<?>>node("npcMorphAssignmentList").getItems().isEmpty());
            assertFalse(hosted().<Button>node("createCustomMorphTargetButton").isDisabled());
            assertTrue(hosted().<Button>node("removeCustomMorphTargetButton").isDisabled());
            assertTrue(shell.requests().isEmpty());
        });
    }

    /**
     * The Morphs controls keep the accessible names, visible texts and nesting they had in the single Workbench FXML,
     * because the packaged smoke run finds them by name, role and parent region. The expected outline is transcribed
     * from the pre-extraction {@code workbench.fxml}; texts the first render rewrites show that render.
     */
    @Test
    void morphsPanesKeepTheirAccessibleOutline() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            AreaPanes panes = host().module().panes();

            assertEquals(List.of(
                    "ScrollPane #morphsPrimaryScroll name=Morphs management",
                    "  VBox #morphsPrimaryContent",
                    "    HBox #morphsInfoBar name=Morphs notification",
                    "      VBox",
                    "        Label #morphsInfoBarCue text=Validation",
                    "        Label #morphsInfoBarMessage text=Validation message",
                    "      Button #dismissMorphsInfoBarButton text=_Dismiss name=Dismiss Morphs validation",
                    "    Label text=_Filter Custom Morph Targets:",
                    "    TextField #customMorphTargetFilter name=Filter Custom Morph Targets",
                    "    Label text=_Sort Custom Morph Targets:",
                    "    ComboBox #customMorphTargetSort name=Custom Morph Target sort",
                    "    ListView #customMorphTargetList name=Custom Morph Targets",
                    "    Label text=Custom Morph Target _name:",
                    "    TextField #customMorphTargetNameInput name=Custom Morph Target name",
                    "    Button #createCustomMorphTargetButton text=_Create Custom Morph Target"
                            + " name=Create Custom Morph Target",
                    "    Button #removeCustomMorphTargetButton text=_Remove Custom Morph Target"
                            + " name=Remove selected Custom Morph Target",
                    "    Button #clearCustomMorphTargetsButton text=C_lear visible Custom Morph Targets"
                            + " name=Clear visible Custom Morph Targets",
                    "    Label text=NPC Morph Assignments",
                    "    Button #addNpcsFromDatabaseButton text=_Add NPCs… name=Add NPCs from NPC Database",
                    "    Label text=_Filter NPC Morph Assignments:",
                    "    TextField #npcMorphAssignmentFilter name=Filter NPC Morph Assignments",
                    "    Label text=_Sort NPC Morph Assignments:",
                    "    ComboBox #npcMorphAssignmentSort name=NPC Morph Assignment sort",
                    "    ListView #npcMorphAssignmentList name=NPC Morph Assignments",
                    "    Label text=NPC _display name:",
                    "    TextField #npcDisplayNameInput name=NPC display name",
                    "    Label text=NPC _plugin name:",
                    "    TextField #npcPluginNameInput name=NPC plugin name",
                    "    Label text=NPC _editor ID:",
                    "    TextField #npcEditorIdInput name=NPC editor ID",
                    "    Label text=NPC _race:",
                    "    TextField #npcRaceInput name=NPC race",
                    "    Label text=NPC _Form ID:",
                    "    TextField #npcFormIdInput name=NPC Form ID",
                    "    Button #createNpcMorphAssignmentButton text=_Create NPC Morph Assignment"
                            + " name=Create NPC Morph Assignment",
                    "    Button #removeNpcMorphAssignmentButton text=_Remove NPC Morph Assignment"
                            + " name=Remove selected NPC Morph Assignment",
                    "    Button #clearNpcMorphAssignmentsButton text=C_lear visible NPC Morph Assignments"
                            + " name=Clear visible NPC Morph Assignments",
                    "    Button #fillEmptyNpcMorphAssignmentsButton text=_Fill Empty…"
                            + " name=Fill Empty NPC Morph Assignments"),
                    outline(panes.getPrimary()));
            assertEquals(List.of(
                    "VBox #morphsEditorContent",
                    "  Label #morphTargetEditorFocusTarget text=No Morphs selection name=Morphs editor: no selection",
                    "  Label #morphTargetConditionText"
                            + " text=Select a Custom Morph Target or NPC Morph Assignment to inspect it."
                            + " name=Select a Custom Morph Target or NPC Morph Assignment to inspect it.",
                    "  Label #npcIdentityText text=",
                    "  Label #npcRaceText text=",
                    "  Label #npcFormIdText text=",
                    "  Label #morphTargetAssignmentCountText text=No Slider Presets assigned"
                            + " name=No Slider Presets assigned",
                    "  Label #morphTargetOutputStatusText"
                            + " text=A Morphs entry needs at least one Slider Preset to appear in output."
                            + " name=No selected Morphs entry is eligible for output.",
                    "  Region",
                    "  Label text=Use the inspector to manage Slider Preset relationships."),
                    outline(panes.getEditor()));
            assertEquals(List.of(
                    "ScrollPane #morphsInspectorScroll name=Slider Preset relationship editor",
                    "  VBox #morphsInspectorContent",
                    "    Label text=Slider Preset relationships",
                    "    Label #morphTargetSelectionText text=No Morphs selection name=Morphs inspector: no selection",
                    "    VBox #npcPortraitSection",
                    "      Label text=NPC portrait",
                    "      ImageView #npcPortraitImage",
                    "      Label #npcPortraitStatus text=No NPC portrait selected name=NPC portrait status",
                    "      Button #openNpcPortraitViewerButton text=_Open portrait viewer"
                            + " name=Open NPC portrait viewer",
                    "    Label #assignedMorphSliderPresetLabel text=_Assigned Slider Presets:",
                    "    ListView #assignedMorphSliderPresetList name=Assigned Slider Presets",
                    "    Button #removeMorphSliderPresetButton text=_Remove assigned Slider Preset"
                            + " name=Remove selected Slider Preset relationship",
                    "    Button #clearMorphSliderPresetsButton text=_Clear assigned Slider Presets"
                            + " name=Clear assigned Slider Presets",
                    "    Label #availableMorphSliderPresetLabel text=A_vailable Slider Preset:",
                    "    ComboBox #availableMorphSliderPreset name=Available Slider Preset",
                    "    Button #assignMorphSliderPresetButton text=Ass_ign Slider Preset"
                            + " name=Assign selected Slider Preset",
                    "    Button #assignAllMorphSliderPresetsButton text=Assign A_ll Slider Presets"
                            + " name=Assign all Slider Presets"),
                    outline(panes.getInspector()));
        });
    }

    /** The Fill Empty flyout requires a chosen preset and cancellation preserves every NPC assignment. */
    @Test
    void fillEmptyFlyoutCancelsAndAppliesOnlyChosenPresets() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            try {
                // The flyout needs a showing owner window.
                hosted().show();
                Button launcher = hosted().node("fillEmptyNpcMorphAssignmentsButton");
                launcher.fire();
                Popup first = FxTestToolkit.showingPopup(hosted().stage(), "#fillEmptyApplyButton");
                Button firstApply = (Button) first.getScene().getRoot().lookup("#fillEmptyApplyButton");
                assertTrue(firstApply.isDisabled());
                ((Button) first.getScene().getRoot().lookup("#fillEmptyCancelButton")).fire();
                assertFalse(first.isShowing());
                assertTrue(flow.frame().snapshot().getNpcMorphAssignments().getFirst()
                        .getSliderPresetNames().isEmpty());

                launcher.fire();
                Popup second = FxTestToolkit.showingPopup(hosted().stage(), "#fillEmptyApplyButton");
                @SuppressWarnings("unchecked")
                ListView<SliderPresetSnapshot> choices =
                        (ListView<SliderPresetSnapshot>) second.getScene().getRoot()
                                .lookup("#fillEmptySliderPresetList");
                choices.getSelectionModel().selectFirst();
                Button apply = (Button) second.getScene().getRoot().lookup("#fillEmptyApplyButton");
                assertEquals("Fill 1 NPC from 1 preset", apply.getText());
                apply.fire();
                assertFalse(second.isShowing());
                assertEquals(List.of("Alpha"), flow.frame().snapshot().getNpcMorphAssignments().getFirst()
                        .getSliderPresetNames());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * The Fill Empty flyout is a satellite: it registers with the shell's appearance while it shows, the shell's
     * dismissal hides it, which closes the registration, cancels the offer and asks the shell to restore focus to its
     * launcher.
     */
    @Test
    void fillEmptyFlyoutRegistersAsASatelliteAndRestoresFocusThroughTheShell() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            try {
                hosted().show();
                Button launcher = hosted().node("fillEmptyNpcMorphAssignmentsButton");
                launcher.fire();
                Popup flyout = FxTestToolkit.showingPopup(hosted().stage(), "#fillEmptyApplyButton");
                assertEquals(List.of(flyout.getContent().getFirst()), satelliteRoots);
                assertEquals(1, openSatellites.size());
                int before = shell.requests().size();

                // The shell's dismissAll, as when the Workbench window hides.
                openSatellites.getFirst().run();

                assertFalse(flyout.isShowing());
                assertTrue(openSatellites.isEmpty(), "the hidden flyout closed its registration");
                assertEquals(List.of("restoreFocusAfterSatellite(fillEmptyNpcMorphAssignmentsButton,"
                        + " PRIMARY_CONTENT)"), shell.requests().subList(before, shell.requests().size()));
                // A pending offer refuses every other Morphs intent; a fresh flyout proves the offer was cancelled.
                launcher.fire();
                Popup again = FxTestToolkit.showingPopup(hosted().stage(), "#fillEmptyApplyButton");
                assertNotSame(flyout, again);
                again.hide();
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * The inspector portrait follows logical NPC selection and explains the missing-file fallback. Its status keeps a
     * stable accessible name and publishes its state as HelpText: the inspector's guidance before the first NPC is
     * selected (#156) and again once the selection clears, and the displayed status while an NPC is selected.
     */
    @Test
    void npcPortraitInspectorClearsWithSelectionAndDescribesMissingImage() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(NpcMorphAssignmentEdits.create("NoPortraitFixture", "Skyrim.esm", "HousecarlWhiterun",
                "NordRace", "000A2C94"));
        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            try {
                String guidance = "Select an NPC Morph Assignment to inspect its portrait.";
                ImageView portrait = (ImageView) hosted().namespace().get("npcPortraitImage");
                Label status = (Label) hosted().namespace().get("npcPortraitStatus");
                Button viewer = (Button) hosted().namespace().get("openNpcPortraitViewerButton");
                assertEquals("NPC portrait status", status.queryAccessibleAttribute(AccessibleAttribute.TEXT));
                assertEquals("No NPC portrait selected", status.getText());
                assertEquals(guidance, status.queryAccessibleAttribute(AccessibleAttribute.HELP),
                        "the portrait status explains itself before the first NPC is selected");

                ListView<NpcMorphAssignmentSnapshot> npcs = hosted().module().npcMorphAssignmentListNode();
                npcs.getSelectionModel().selectFirst();
                assertEquals(AccessibleRole.NODE, portrait.getAccessibleRole());
                assertNull(portrait.getAccessibleText(),
                        "the named NPC inspector makes its adjacent thumbnail decorative");
                assertTrue(status.getText().contains("NoPortraitFixture (HousecarlWhiterun).jpg"));
                assertEquals(status.getText(), status.queryAccessibleAttribute(AccessibleAttribute.HELP),
                        "a selected NPC's portrait status mirrors its displayed value as HelpText");
                assertTrue(viewer.isDisabled());
                ((TextField) hosted().namespace().get("npcMorphAssignmentFilter")).setText("no match");
                assertNull(portrait.getAccessibleText());
                assertNull(portrait.getImage());
                assertEquals("No NPC portrait selected", status.getText());
                assertEquals(guidance, status.queryAccessibleAttribute(AccessibleAttribute.HELP));
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * The Morphs JavaFX adapter renders immutable target frames and translates catalog and relationship controls into
     * typed intents without retaining control-local Project state.
     */
    @Test
    void morphsControlsCreateFilterAndAssignThroughTheAuthoritativeProjectFlow() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));
        flow.apply(CustomMorphTargetEdits.create("Existing"));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> targets =
                    (ListView<CustomMorphTargetSnapshot>) hosted().namespace().get("customMorphTargetList");
            TextField name = (TextField) hosted().namespace().get("customMorphTargetNameInput");
            TextField filter = (TextField) hosted().namespace().get("customMorphTargetFilter");
            Button create = (Button) hosted().namespace().get("createCustomMorphTargetButton");
            @SuppressWarnings("unchecked")
            ComboBox<SliderPresetSnapshot> available =
                    (ComboBox<SliderPresetSnapshot>) hosted().namespace().get("availableMorphSliderPreset");
            Button assign = (Button) hosted().namespace().get("assignMorphSliderPresetButton");
            Label condition = (Label) hosted().namespace().get("morphTargetConditionText");
            Label selection = (Label) hosted().namespace().get("morphTargetSelectionText");
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> assigned =
                    (ListView<SliderPresetSnapshot>) hosted().namespace().get("assignedMorphSliderPresetList");
            Set<Character> inspectorMnemonics = List.<Labeled>of(
                            (Label) hosted().namespace().get("assignedMorphSliderPresetLabel"),
                            (Button) hosted().namespace().get("removeMorphSliderPresetButton"),
                            (Button) hosted().namespace().get("clearMorphSliderPresetsButton"),
                            (Label) hosted().namespace().get("availableMorphSliderPresetLabel"),
                            assign,
                            (Button) hosted().namespace().get("assignAllMorphSliderPresetsButton"))
                    .stream().map(Labeled::getText).filter(text -> text.contains("_"))
                    .map(text -> Character.toUpperCase(text.charAt(text.indexOf('_') + 1)))
                    .collect(java.util.stream.Collectors.toSet());

            assertTrue(targets.isVisible());
            assertEquals(Set.of('A', 'R', 'C', 'V', 'I', 'L'), inspectorMnemonics);
            assertEquals(List.of("Existing"), targets.getItems().stream()
                    .map(CustomMorphTargetSnapshot::getName).toList());
            name.setText("  All|Female  ");
            create.fire();
            assertEquals("All|Female", targets.getSelectionModel().getSelectedItem().getName());

            targets.getSelectionModel().select(targets.getItems().stream()
                    .filter(target -> target.getName().equals("Existing")).findFirst().orElseThrow());
            available.setValue(available.getItems().stream()
                    .filter(preset -> preset.getName().equals("Alpha")).findFirst().orElseThrow());
            available.fireEvent(new ActionEvent());
            assign.fire();
            assigned = hosted().module().assignedMorphSliderPresetListNode();

            assertEquals(List.of("Alpha"), assigned.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertEquals(List.of("Alpha"), flow.frame().snapshot().getCustomMorphTargets().stream()
                    .filter(target -> target.getName().equals("Existing")).findFirst().orElseThrow()
                    .getSliderPresetNames());
            assertEquals("BodyGen condition: Existing", condition.getAccessibleText());
            assertEquals("Selected Custom Morph Target Existing", selection.getAccessibleText());
            filter.setText("all");
            assertNull(targets.getSelectionModel().getSelectedItem());
            assertTrue(assigned.getItems().isEmpty());
            assertEquals("Morphs inspector: no selection", selection.getAccessibleText());
            assertEquals("Select a Custom Morph Target or NPC Morph Assignment to inspect it.",
                    condition.getAccessibleText());
            hosted().stage().close();
        });
    }

    /** Empty target and NPC entries are emitted as Morphs lines even without Slider Preset assignments. */
    @Test
    void unassignedMorphEntriesDescribeTheirEmittedOutput() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(CustomMorphTargetEdits.create("Unassigned"));
        flow.apply(NpcMorphAssignmentEdits.create("Guard", "Skyrim.esm", "Guard01", "NordRace", "000123"));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            try {
                @SuppressWarnings("unchecked")
                ListView<CustomMorphTargetSnapshot> targets =
                        (ListView<CustomMorphTargetSnapshot>) hosted().namespace().get("customMorphTargetList");
                @SuppressWarnings("unchecked")
                ListView<NpcMorphAssignmentSnapshot> npcs =
                        (ListView<NpcMorphAssignmentSnapshot>) hosted().namespace().get("npcMorphAssignmentList");
                Label status = (Label) hosted().namespace().get("morphTargetOutputStatusText");

                targets.getSelectionModel().selectFirst();
                assertEquals("Included in Morphs output without Slider Preset assignments.", status.getText());
                npcs.getSelectionModel().selectFirst();
                assertEquals("Included in Morphs output without Slider Preset assignments.", status.getText());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * The Morphs controls author an NPC through the Project flow and keep its selection separate from a Custom
     * Morph Target while the shared editor exposes the NPC's complete output identity.
     */
    @Test
    void morphsNpcControlsCreateInspectAndSelectWithoutRetargeting() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("All|Female"));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            try {
                @SuppressWarnings("unchecked")
                ListView<CustomMorphTargetSnapshot> targets =
                        (ListView<CustomMorphTargetSnapshot>) hosted().namespace().get("customMorphTargetList");
                @SuppressWarnings("unchecked")
                ListView<NpcMorphAssignmentSnapshot> npcs =
                        (ListView<NpcMorphAssignmentSnapshot>) hosted().namespace().get("npcMorphAssignmentList");
                targets.getSelectionModel().selectFirst();
                @SuppressWarnings("unchecked")
                ComboBox<SliderPresetSnapshot> available =
                        (ComboBox<SliderPresetSnapshot>) hosted().namespace().get("availableMorphSliderPreset");
                Button assign = (Button) hosted().namespace().get("assignMorphSliderPresetButton");
                available.setValue(available.getItems().getFirst());

                ((TextField) hosted().namespace().get("npcDisplayNameInput")).setText("Lyra");
                ((TextField) hosted().namespace().get("npcPluginNameInput")).setText("People.esp");
                ((TextField) hosted().namespace().get("npcEditorIdInput")).setText("Lyra01");
                ((TextField) hosted().namespace().get("npcRaceInput")).setText("NordRace");
                ((TextField) hosted().namespace().get("npcFormIdInput")).setText("000A12B3");
                ((Button) hosted().namespace().get("createNpcMorphAssignmentButton")).fire();

                npcs = hosted().module().npcMorphAssignmentListNode();
                NpcMorphAssignmentSnapshot npc = npcs.getSelectionModel().getSelectedItem();
                assertNotNull(npc);
                assertNull(targets.getSelectionModel().getSelectedItem());
                assertNull(available.getValue(), "a pending target preset must not carry to the selected NPC");
                assertTrue(assign.isDisabled());
                assertEquals("Lyra", npc.getDisplayName());
                assertEquals("People.esp", npc.getPluginName());
                assertEquals("Lyra01", npc.getEditorId());
                assertEquals("NordRace", npc.getRace());
                assertEquals("A12B3", npc.getFormId());
                assertEquals("Lyra", ((Label) hosted().namespace().get("morphTargetEditorFocusTarget")).getText());
                assertEquals("Plugin: People.esp; Editor ID: Lyra01",
                        ((Label) hosted().namespace().get("npcIdentityText")).getText());
                assertEquals("Race: NordRace", ((Label) hosted().namespace().get("npcRaceText")).getText());
                assertEquals("Form ID: A12B3", ((Label) hosted().namespace().get("npcFormIdText")).getText());

                available.setValue(available.getItems().getFirst());
                targets.getSelectionModel().selectFirst();
                assertNull(npcs.getSelectionModel().getSelectedItem());
                assertNull(available.getValue(), "a pending NPC preset must not carry to the selected target");
                assertTrue(assign.isDisabled());
                assertEquals("BodyGen condition: All|Female",
                        ((Label) hosted().namespace().get("morphTargetConditionText")).getText());
                npcs.getSelectionModel().selectFirst();
                assertNull(targets.getSelectionModel().getSelectedItem());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /** NPC filtering and sorting retain logical selection, and a confirmed clear removes only the frozen visible set. */
    @Test
    void morphsNpcControlsEditRelationshipsAndClearOnlyVisibleAssignments() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot(
                "Lyra", "People.esp", "Lyra01", "NordRace", "A12B3", List.of())));
        flow.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot(
                "Mira", "Other.esp", "Mira01", "BretonRace", "B45C6", List.of())));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            try {
                ListView<NpcMorphAssignmentSnapshot> npcs = hosted().module().npcMorphAssignmentListNode();
                npcs.getSelectionModel().select(npcs.getItems().stream()
                        .filter(npc -> npc.getEditorId().equals("Mira01")).findFirst().orElseThrow());
                @SuppressWarnings("unchecked")
                ComboBox<SliderPresetSnapshot> available =
                        (ComboBox<SliderPresetSnapshot>) hosted().namespace().get("availableMorphSliderPreset");
                available.setValue(available.getItems().getFirst());
                available.fireEvent(new ActionEvent());
                ((Button) hosted().namespace().get("assignMorphSliderPresetButton")).fire();
                assertEquals(List.of("Alpha"), flow.frame().snapshot().getNpcMorphAssignments().stream()
                        .filter(npc -> npc.getEditorId().equals("Mira01"))
                        .findFirst().orElseThrow().getSliderPresetNames());

                @SuppressWarnings("unchecked")
                ComboBox<MorphsFeature.NpcSortOrder> sort =
                        (ComboBox<MorphsFeature.NpcSortOrder>) hosted().namespace().get("npcMorphAssignmentSort");
                sort.setValue(MorphsFeature.NpcSortOrder.PLUGIN_DESCENDING);
                sort.fireEvent(new ActionEvent());
                assertEquals("Mira01", npcs.getSelectionModel().getSelectedItem().getEditorId());

                TextField filter = (TextField) hosted().namespace().get("npcMorphAssignmentFilter");
                filter.setText("lyra");
                assertNull(npcs.getSelectionModel().getSelectedItem());
                assertEquals(List.of("Lyra"), npcs.getItems().stream()
                        .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
                platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CLEAR);
                ((Button) hosted().namespace().get("clearNpcMorphAssignmentsButton")).fire();
                assertEquals(List.of("Mira"), flow.frame().snapshot().getNpcMorphAssignments().stream()
                        .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
                filter.clear();
                assertEquals(List.of("Mira"), hosted().module().npcMorphAssignmentListNode().getItems().stream()
                        .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
                assertNull(hosted().module().npcMorphAssignmentListNode().getSelectionModel().getSelectedItem());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * Refilling an empty Morphs list replaces only its JavaFX adapter so the Windows UIA provider receives a fresh
     * virtualized child subtree without changing feature identity state.
     */
    @Test
    void morphsEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            host();
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> initial =
                    (ListView<CustomMorphTargetSnapshot>) hosted().namespace().get("customMorphTargetList");
            TextField name = (TextField) hosted().namespace().get("customMorphTargetNameInput");

            name.setText("All|Female");
            ((Button) hosted().namespace().get("createCustomMorphTargetButton")).fire();

            assertNotSame(initial, hosted().module().customMorphTargetListNode());
            assertEquals(List.of("All|Female"), hosted().module().customMorphTargetListNode().getItems().stream()
                    .map(CustomMorphTargetSnapshot::getName).toList());
            hosted().stage().close();
        });
    }

    /**
     * Refilling an empty assigned-relationship list replaces only its JavaFX adapter so Windows UIA exposes the
     * immutable relationships selected from a later target frame.
     */
    @Test
    void morphsAssignedRelationshipsEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha")));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> initial =
                    (ListView<SliderPresetSnapshot>) hosted().namespace().get("assignedMorphSliderPresetList");
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> targets =
                    (ListView<CustomMorphTargetSnapshot>) hosted().namespace().get("customMorphTargetList");

            targets.getSelectionModel().selectFirst();

            assertNotSame(initial, hosted().module().assignedMorphSliderPresetListNode());
            assertEquals(List.of("Alpha"), hosted().module().assignedMorphSliderPresetListNode().getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            hosted().stage().close();
        });
    }

    /**
     * Each landmark follows the Morphs selection: the selected catalog, the editor summary, and the inspector's first
     * usable control. Ctrl+K asks the shell to reveal the primary content before focusing that catalog's filter, and
     * Escape clears only a selection that exists.
     */
    @Test
    void focusNodesSearchAndEscapeFollowTheMorphsSelection() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("All|Female"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host(flow).module();
            try {
                hosted().show();

                assertSame(hosted().node("customMorphTargetList"), area.focusNode(Landmark.PRIMARY_CONTENT));
                assertSame(hosted().node("morphTargetEditorFocusTarget"), area.focusNode(Landmark.EDITOR));
                assertSame(hosted().node("morphTargetSelectionText"), area.focusNode(Landmark.INSPECTOR),
                        "with nothing to assign, the inspector starts at its selection text");
                assertSame(hosted().node("addNpcsFromDatabaseButton"),
                        area.focusNode(Landmark.NPC_DATABASE_LAUNCHER));
                assertSame(hosted().node("customMorphTargetList"), area.defaultFocusNode());
                assertNull(area.focusNode(Landmark.RAIL));
                assertFalse(area.escape(), "Escape without a selection is left to the shell");

                area.focusSearch();
                assertEquals(List.of("revealPrimaryContent()"), shell.requests());
                assertSame(hosted().node("customMorphTargetFilter"), hosted().stage().getScene().getFocusOwner());

                area.customMorphTargetListNode().getSelectionModel().selectFirst();
                assertSame(hosted().node("availableMorphSliderPreset"), area.focusNode(Landmark.INSPECTOR));

                area.npcMorphAssignmentListNode().getSelectionModel().selectFirst();
                assertSame(area.npcMorphAssignmentListNode(), area.focusNode(Landmark.PRIMARY_CONTENT));
                assertSame(area.npcMorphAssignmentListNode(), area.defaultFocusNode());
                assertSame(hosted().node("morphTargetSelectionText"), area.focusNode(Landmark.INSPECTOR));
                area.focusSearch();
                assertSame(hosted().node("npcMorphAssignmentFilter"), hosted().stage().getScene().getFocusOwner());

                assertTrue(area.escape());
                assertNull(area.npcMorphAssignmentListNode().getSelectionModel().getSelectedItem());
                assertSame(hosted().node("customMorphTargetList"), area.focusNode(Landmark.PRIMARY_CONTENT));
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * Add NPCs asks the shell to open the NPC Database with its own launcher as the return target, and the shell's
     * return selects the promoted assignment; the Area reaches no other Area.
     */
    @Test
    void npcDatabaseLauncherAndReturnGoThroughTheShellPort() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        flow.apply(NpcMorphAssignmentEdits.create("Mira", "Other.esp", "Mira01", "BretonRace", "000002"));
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host(flow).module();

            hosted().<Button>node("addNpcsFromDatabaseButton").fire();

            assertEquals(List.of("openNpcDatabase(MORPHS, NPC_DATABASE_LAUNCHER)"), shell.requests());

            area.revealAssignment(Optional.of(new NpcMorphAssignmentIdentity("Other.esp", "Mira01")));

            assertEquals("Mira01", area.npcMorphAssignmentListNode().getSelectionModel().getSelectedItem()
                    .getEditorId());
            area.revealAssignment(Optional.empty());
            assertEquals("Mira01", area.npcMorphAssignmentListNode().getSelectionModel().getSelectedItem()
                    .getEditorId(), "a return without a promotion keeps the selection");
            assertEquals(1, shell.requests().size(), "selecting the returned assignment needs nothing of the shell");
        });
    }

    /**
     * Opening the narrow inspector scrolls it to the top while an NPC is selected, so its portrait shows first; with
     * a Custom Morph Target selected the relationships keep their position.
     */
    @Test
    void inspectorScrollResetsToThePortraitOnlyForASelectedNpc() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(CustomMorphTargetEdits.create("All|Female"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host(flow).module();
            ScrollPane inspector = hosted().node("morphsInspectorScroll");

            area.customMorphTargetListNode().getSelectionModel().selectFirst();
            inspector.setVvalue(1.0);
            area.resetInspectorScroll();
            assertEquals(1.0, inspector.getVvalue());

            area.npcMorphAssignmentListNode().getSelectionModel().selectFirst();
            area.resetInspectorScroll();
            assertEquals(0.0, inspector.getVvalue());
        });
    }

    /**
     * Morphs words its own feedback and hands it to the shell: a create on the status line, a confirmed removal in
     * Activity, and a validation failure as Morphs validation while the pane-local InfoBar shows the diagnostic.
     */
    @Test
    void morphsEditsPublishTheirOwnFeedbackThroughTheShellPort() throws Exception {
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.REMOVE);
        FxTestToolkit.runOnFxThread(() -> {
            host();
            TextField name = hosted().node("customMorphTargetNameInput");
            Button create = hosted().node("createCustomMorphTargetButton");

            name.setText("All|Female");
            create.fire();
            assertEquals(List.of("publishFeedback(Create Custom Morph Target completed.)"), shell.requests());
            assertTrue(shell.feedbackFrame().activities().isEmpty(), "a create is a status-line outcome");

            hosted().module().customMorphTargetListNode().getSelectionModel().selectFirst();
            hosted().<Button>node("removeCustomMorphTargetButton").fire();
            assertEquals("publishFeedback(Remove Custom Morph Target completed.)", shell.requests().getLast());
            assertEquals("Remove Custom Morph Target", shell.feedbackFrame().activities().getFirst().operation());

            name.setText("All|Female");
            create.fire();
            name.setText("all|female");
            create.fire();
            assertTrue(shell.requests().getLast().startsWith("publishFeedback("));
            assertEquals(WorkbenchFeedback.Severity.VALIDATION, shell.feedbackFrame().status().severity());
            assertTrue(hosted().<javafx.scene.layout.HBox>node("morphsInfoBar").isVisible());
            assertEquals(hosted().<Label>node("morphsInfoBarMessage").getText(),
                    shell.feedbackFrame().status().message());
        });
    }

    /**
     * The shell hands the Area the window's render scale, and both catalog heights re-evaluate when it changes, so a
     * window moved to a screen with another scale re-snaps its short catalogs.
     */
    @Test
    void catalogHeightsFollowTheWindowRenderScale() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host().module();
            Set<String> invalidated = new java.util.TreeSet<>();
            area.customMorphTargetListNode().prefHeightProperty().addListener(
                    (javafx.beans.Observable observable) -> invalidated.add("Custom Morph Targets"));
            area.npcMorphAssignmentListNode().prefHeightProperty().addListener(
                    (javafx.beans.Observable observable) -> invalidated.add("NPC Morph Assignments"));
            // A bound property reports invalidation only once it has been read since its last change.
            area.customMorphTargetListNode().getPrefHeight();
            area.npcMorphAssignmentListNode().getPrefHeight();

            hosted().stage().setRenderScaleY(hosted().stage().getRenderScaleY() + 0.5);

            assertEquals(Set.of("Custom Morph Targets", "NPC Morph Assignments"), invalidated);
        });
    }

    /**
     * The Morphs mutation controls follow the context's job gate: a Project-exclusive job started elsewhere (a
     * BodySlide import) disables them, and they come back once it settles.
     */
    @Test
    void mutationControlsFollowTheContextsJobGate() throws Exception {
        java.nio.file.Path source = temporaryDirectory.resolve("blocking-import.xml").toAbsolutePath().normalize();
        java.nio.file.Files.writeString(source, "<SliderPresets><Preset name=\"Blocking Import\"/></SliderPresets>");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The import is settled by the test before prolonged feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        FxTestToolkit.runOnFxThread(() -> {
            host(jobs);
            List<Button> mutations = List.of(hosted().node("createCustomMorphTargetButton"),
                    hosted().node("createNpcMorphAssignmentButton"),
                    hosted().node("fillEmptyNpcMorphAssignmentsButton"));
            assertTrue(mutations.stream().noneMatch(Button::isDisabled));

            WorkbenchProjectFlow.Effect chooser = hosted().flow()
                    .request(WorkbenchProjectFlow.Intent.IMPORT_BODYSLIDE).effect().orElseThrow();
            hosted().flow().respond(chooser.token(), WorkbenchProjectFlow.Response.selectedSources(List.of(source)));
            assertTrue(jobs.frame().projectMutationsBlocked());

            assertTrue(mutations.stream().allMatch(Button::isDisabled));
            worker.runNext();
            assertFalse(jobs.frame().projectMutationsBlocked());
            assertTrue(mutations.stream().noneMatch(Button::isDisabled));
        });
    }

    /** The empty-to-populated Custom Morph Target swap moves focus to the replacement list. */
    @Test
    void customMorphTargetRefillKeepsFocusOnTheReplacementList() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host().module();
            try {
                hosted().show();
                ListView<?> initial = area.customMorphTargetListNode();
                initial.requestFocus();
                hosted().<TextField>node("customMorphTargetNameInput").setText("All|Female");

                hosted().<Button>node("createCustomMorphTargetButton").fire();

                assertNotSame(initial, area.customMorphTargetListNode());
                assertSame(area.customMorphTargetListNode(), hosted().stage().getScene().getFocusOwner());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /** Clearing a filter that emptied the NPC catalog refills it without losing the list's focus. */
    @Test
    void npcMorphAssignmentRefillKeepsFocusOnTheReplacementList() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host(flow).module();
            try {
                hosted().show();
                TextField filter = hosted().node("npcMorphAssignmentFilter");
                filter.setText("no match");
                ListView<?> initial = area.npcMorphAssignmentListNode();
                assertTrue(initial.getItems().isEmpty());
                initial.requestFocus();

                filter.setText("");

                assertNotSame(initial, area.npcMorphAssignmentListNode());
                assertSame(area.npcMorphAssignmentListNode(), hosted().stage().getScene().getFocusOwner());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /** Selecting a target with relationships refills the focused assigned-preset list on its replacement. */
    @Test
    void assignedSliderPresetRefillKeepsFocusOnTheReplacementList() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha")));
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host(flow).module();
            try {
                hosted().show();
                ListView<?> initial = area.assignedMorphSliderPresetListNode();
                initial.requestFocus();

                area.customMorphTargetListNode().getSelectionModel().selectFirst();

                assertNotSame(initial, area.assignedMorphSliderPresetListNode());
                assertSame(area.assignedMorphSliderPresetListNode(), hosted().stage().getScene().getFocusOwner());
            } finally {
                hosted().stage().close();
            }
        });
    }

    /** Fill Empty focuses its choices as it opens and returns focus to its launcher as it closes. */
    @Test
    void fillEmptyFlyoutTakesFocusOnOpenAndReturnsItToTheLauncherOnClose() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host(flow).module();
            try {
                hosted().show();
                Button launcher = hosted().node("fillEmptyNpcMorphAssignmentsButton");
                area.npcMorphAssignmentListNode().requestFocus();

                launcher.fire();

                Popup popup = FxTestToolkit.showingPopup(hosted().stage(), "#fillEmptyApplyButton");
                assertSame(popup.getScene().getRoot().lookup("#fillEmptySliderPresetList"),
                        popup.getScene().getFocusOwner());
                int before = shell.requests().size();

                ((Button) popup.getScene().getRoot().lookup("#fillEmptyCancelButton")).fire();

                assertFalse(popup.isShowing());
                // The Area hands its launcher to the shell, whose focus policy focuses it while it can take focus
                // (WorkbenchFocusPolicyTest.satelliteCloseRestoresFocusToItsLauncher).
                assertEquals(List.of("restoreFocusAfterSatellite(fillEmptyNpcMorphAssignmentsButton,"
                        + " PRIMARY_CONTENT)"), shell.requests().subList(before, shell.requests().size()));
            } finally {
                hosted().stage().close();
            }
        });
    }

    /**
     * Refilling an empty NPC Morph Assignments list replaces its JavaFX node in place, so the Windows UIA provider
     * discovers the new virtualized rows instead of keeping the stale empty subtree.
     */
    @Test
    void npcMorphAssignmentsEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host().module();
            ListView<NpcMorphAssignmentSnapshot> initial = hosted().node("npcMorphAssignmentList");
            Parent owner = initial.getParent();
            int position = owner.getChildrenUnmodifiable().indexOf(initial);
            assertTrue(initial.getItems().isEmpty());

            createNpc("Lyra", "Lyra01");

            ListView<NpcMorphAssignmentSnapshot> replacement = area.npcMorphAssignmentListNode();
            assertNotSame(initial, replacement);
            assertNull(initial.getParent(), "the stale empty node must leave the scene graph");
            assertSame(owner, replacement.getParent());
            assertEquals(position, owner.getChildrenUnmodifiable().indexOf(replacement));
            assertEquals("npcMorphAssignmentList", replacement.getId());
            assertEquals("NPC Morph Assignments", replacement.getAccessibleText());
            assertEquals(List.of("Lyra"), replacement.getItems().stream()
                    .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
        });
    }

    /**
     * A label's {@code labelFor} is the JavaFX source of the UIA LabeledBy relationship. Every empty-to-full swap of
     * the Assigned Slider Presets list must re-point its label at the fresh node, so the relationship never stays
     * on a node that has left the scene graph.
     *
     * <p>The FXML declares the list before its label (#155), so the relationship already holds for the initial empty
     * list; each swap then hands it on.</p>
     */
    @Test
    void assignedSliderPresetsLabelForFollowsEveryListSwap() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha")));

        FxTestToolkit.runOnFxThread(() -> {
            MorphsArea area = host(flow).module();
            Label label = hosted().node("assignedMorphSliderPresetLabel");
            ListView<CustomMorphTargetSnapshot> targets = area.customMorphTargetListNode();
            ListView<SliderPresetSnapshot> initial = area.assignedMorphSliderPresetListNode();
            assertTrue(initial.getItems().isEmpty());
            assertSame(initial, label.getLabelFor());
            assertSame(label, initial.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY));

            targets.getSelectionModel().select(0);

            ListView<SliderPresetSnapshot> first = area.assignedMorphSliderPresetListNode();
            assertNotSame(initial, first);
            assertSame(first, label.getLabelFor());
            assertSame(label, first.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY));
            Parent inspector = hosted().node("morphsInspectorContent");
            assertSame(inspector, first.getParent());

            targets.getSelectionModel().clearSelection();
            assertTrue(area.assignedMorphSliderPresetListNode().getItems().isEmpty());
            targets.getSelectionModel().select(0);

            ListView<SliderPresetSnapshot> second = area.assignedMorphSliderPresetListNode();
            assertNotSame(first, second);
            assertNull(first.getParent(), "the first replacement must have left the scene graph");
            assertSame(inspector, second.getParent());
            assertSame(second, label.getLabelFor());
            assertSame(label, second.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY));
            assertNull(first.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY),
                    "the detached node must no longer claim the label");
            assertEquals(List.of("Alpha"), second.getItems().stream().map(SliderPresetSnapshot::getName).toList());
        });
    }

    /** Creates one NPC Morph Assignment through the Morphs authoring controls. */
    private void createNpc(String displayName, String editorId) {
        hosted().<TextField>node("npcDisplayNameInput").setText(displayName);
        hosted().<TextField>node("npcPluginNameInput").setText("People.esp");
        hosted().<TextField>node("npcEditorIdInput").setText(editorId);
        hosted().<TextField>node("npcRaceInput").setText("NordRace");
        hosted().<TextField>node("npcFormIdInput").setText("000A12B3");
        hosted().<Button>node("createNpcMorphAssignmentButton").fire();
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
