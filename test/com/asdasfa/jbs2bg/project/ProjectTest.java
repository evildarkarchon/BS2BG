package com.asdasfa.jbs2bg.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Covers only the aggregate contracts the session handlers rely on: same instance
 * on no-op, cascades to every referrer, canonical order after every operation,
 * and rejection of an inconsistent snapshot at construction.
 */
class ProjectTest {

    /**
     * Proves that every operation whose result equals the current content hands
     * back the identical instance, which is how the session tells Unchanged from
     * Changed without comparing values itself.
     */
    @Test
    void operationsThatChangeNothingReturnTheSameInstance() {
        Project project = project();
        SliderPresetSnapshot beta = project.findSliderPreset("beta").get();

        assertSame(project, project.renameSliderPreset("Beta", "Beta").getProject());
        assertSame(project, project.replaceSliderPreset("beta", copy(beta)));
        assertSame(project, project.upsertSliderPreset(copy(beta)));
        assertSame(project, project.upsertSliderPreset(
                new SliderPresetSnapshot("BETA", beta.isUunp(), beta.getSliderChoices())));

        Project empty = Project.from(ProjectSnapshot.empty());
        assertSame(empty, empty.clearSliderPresets());
        assertNotSame(project, project.clearSliderPresets());

        // Relationship operations: an assignment that is already present (in any
        // casing), an absent unassignment, and clearing an already-empty referrer
        // are no-ops for both referrer kinds.
        Project.ReferrerKey both = Project.ReferrerKey.customMorphTarget("BOTH");
        Project.ReferrerKey serana = Project.ReferrerKey.npcMorphAssignment(
                new NpcMorphAssignmentIdentity("dawnguard.ESM", "SERANA"));
        assertSame(project, project.assignSliderPreset(both, "ALPHA").getProject());
        assertSame(project, project.assignSliderPresets(both, Arrays.asList("alpha", "BETA")).getProject());
        assertSame(project, project.assignSliderPreset(serana, " beta ").getProject());
        assertSame(project, project.assignSliderPresets(serana, Collections.<String>emptyList()).getProject());
        assertSame(project, project.unassignSliderPreset(both, "Gamma"));
        assertSame(project, project.unassignSliderPreset(Project.ReferrerKey.customMorphTarget("OnlyBeta"), "Alpha"));
        Project cleared = project.clearSliderPresetAssignments(serana);
        assertNotSame(project, cleared);
        assertSame(cleared, cleared.clearSliderPresetAssignments(serana));
        assertSame(empty, empty.clearCustomMorphTargets());
        assertNotSame(project, project.clearCustomMorphTargets());

        // A relationship edit copies only the collection it touched.
        Project assigned = project.assignSliderPreset(Project.ReferrerKey.customMorphTarget("OnlyBeta"), "alpha")
                .getProject();
        assertSame(project.getSliderPresets(), assigned.getSliderPresets());
        assertSame(project.getNpcMorphAssignments(), assigned.getNpcMorphAssignments());
        assertSame(project.getCustomMorphTargets().get(0), assigned.getCustomMorphTargets().get(0));
        assertSame(project.getCustomMorphTargets(), cleared.getCustomMorphTargets());

        // NPC Morph Assignment collection operations: a bulk promotion whose every
        // member is already present (in any casing), a bulk removal that matches
        // nothing, clearing an empty collection, clearing assignments that are
        // already empty, and a fill whose every NPC is occupied are no-ops.
        assertSame(project, project.addNpcMorphAssignments(Arrays.asList(
                npc("Other", "SKYRIM.ESM", "housecarlwhiterun", Collections.<String>emptyList()))).getProject());
        assertSame(project, project.addNpcMorphAssignments(Collections.<NpcMorphAssignmentSnapshot>emptyList())
                .getProject());
        assertSame(project, project.removeNpcMorphAssignments(Arrays.asList(
                new NpcMorphAssignmentIdentity("Missing.esp", "Missing"))));
        assertSame(project, project.removeNpcMorphAssignments(Collections.<NpcMorphAssignmentIdentity>emptyList()));
        assertSame(empty, empty.clearNpcMorphAssignments());
        assertNotSame(project, project.clearNpcMorphAssignments());
        assertSame(cleared, cleared.clearSliderPresetAssignments(Arrays.asList(serana)));
        assertSame(project, project.clearSliderPresetAssignments(Collections.<Project.ReferrerKey>emptyList()));
        assertSame(project, project.fillEmptyNpcMorphAssignments(Arrays.asList(new NpcSliderPresetChoice(
                new NpcMorphAssignmentIdentity("dawnguard.esm", "serana"), "Beta"))).getProject());
        assertSame(project, project.fillEmptyNpcMorphAssignments(Collections.<NpcSliderPresetChoice>emptyList())
                .getProject());

        // An NPC collection edit copies only the NPC collection, and the promoted
        // value is a distinct copy of the source.
        NpcMorphAssignmentSnapshot source = npc("Aela", "Skyrim.esm", "Aela", Arrays.asList("beta"));
        Project promoted = project.addNpcMorphAssignment(source).getProject();
        assertSame(project.getSliderPresets(), promoted.getSliderPresets());
        assertSame(project.getCustomMorphTargets(), promoted.getCustomMorphTargets());
        assertNotSame(source, promoted.findNpcMorphAssignment(new NpcMorphAssignmentIdentity("skyrim.esm", "AELA"))
                .get());
        assertSame(project.getNpcMorphAssignments().get(0), promoted.getNpcMorphAssignments().get(0));
        Project removed = promoted.removeNpcMorphAssignment(new NpcMorphAssignmentIdentity("skyrim.esm", "AELA"));
        assertSame(promoted.getSliderPresets(), removed.getSliderPresets());
        assertSame(promoted.getCustomMorphTargets(), removed.getCustomMorphTargets());
        assertEquals(project.getNpcMorphAssignments(), removed.getNpcMorphAssignments());
    }

    /**
     * Proves that rename, remove, and clear each rewrite every Custom Morph Target
     * and NPC Morph Assignment reference in one step, without touching values that
     * did not reference the affected Slider Preset.
     */
    @Test
    void renameRemoveAndClearCascadeToEveryReferrer() {
        Project project = project();

        Project renamed = project.renameSliderPreset("alpha", "Zulu").getProject();
        assertEquals(Arrays.asList("Beta", "Zulu"), names(renamed.getSliderPresets()));
        assertEquals(Arrays.asList("Beta", "Zulu"), renamed.getCustomMorphTargets().get(0).getSliderPresetNames());
        assertEquals(Arrays.asList("Beta"), renamed.getCustomMorphTargets().get(1).getSliderPresetNames());
        // NPCs order by plugin name: Dawnguard.esm (Serana) precedes Skyrim.esm (Lydia).
        assertEquals(Arrays.asList("Beta", "Zulu"), renamed.getNpcMorphAssignments().get(0).getSliderPresetNames());
        assertEquals(Arrays.asList("Zulu"), renamed.getNpcMorphAssignments().get(1).getSliderPresetNames());
        assertSame(project.getCustomMorphTargets().get(1), renamed.getCustomMorphTargets().get(1),
                "an unaffected referrer must be carried over rather than copied");

        Project removed = project.removeSliderPreset("ALPHA");
        assertEquals(Arrays.asList("Beta"), names(removed.getSliderPresets()));
        assertEquals(Arrays.asList("Beta"), removed.getCustomMorphTargets().get(0).getSliderPresetNames());
        assertEquals(Arrays.asList("Beta"), removed.getCustomMorphTargets().get(1).getSliderPresetNames());
        assertEquals(Arrays.asList("Beta"), removed.getNpcMorphAssignments().get(0).getSliderPresetNames());
        assertTrue(removed.getNpcMorphAssignments().get(1).getSliderPresetNames().isEmpty());
        assertEquals("Lydia", removed.getNpcMorphAssignments().get(1).getDisplayName());

        Project cleared = project.clearSliderPresets();
        assertTrue(cleared.getSliderPresets().isEmpty());
        assertEquals(2, cleared.getCustomMorphTargets().size());
        assertEquals(2, cleared.getNpcMorphAssignments().size());
        for (CustomMorphTargetSnapshot target : cleared.getCustomMorphTargets())
            assertTrue(target.getSliderPresetNames().isEmpty());
        for (NpcMorphAssignmentSnapshot npc : cleared.getNpcMorphAssignments())
            assertTrue(npc.getSliderPresetNames().isEmpty());

        assertEquals(Arrays.asList("Alpha", "Beta"), names(project.getSliderPresets()),
                "the source aggregate must remain unchanged");
    }

    /**
     * Proves that every collection and every assignment name list is in canonical
     * case-insensitive order after each catalog operation, including when a rename
     * moves a name to a different position.
     */
    @Test
    void everyCollectionStaysInCanonicalOrderAfterEveryOperation() {
        Project project = Project.from(ProjectSnapshot.empty());
        project = project.addSliderPreset(preset("delta")).getProject();
        project = project.addSliderPreset(preset("Bravo")).getProject();
        project = project.addSliderPreset(preset("charlie")).getProject();
        assertEquals(Arrays.asList("Bravo", "charlie", "delta"), names(project.getSliderPresets()));

        project = project.upsertSliderPreset(preset("alpha"));
        assertEquals(Arrays.asList("alpha", "Bravo", "charlie", "delta"), names(project.getSliderPresets()));

        project = project.renameSliderPreset("charlie", "Echo").getProject();
        assertEquals(Arrays.asList("alpha", "Bravo", "delta", "Echo"), names(project.getSliderPresets()));

        Project seeded = Project.from(new ProjectSnapshot(project.getSliderPresets(),
                Arrays.asList(new CustomMorphTargetSnapshot("Target", Arrays.asList("Bravo", "delta", "Echo"))),
                Arrays.asList(new NpcMorphAssignmentSnapshot("Name", "Plugin.esp", "Editor", "Race", "000001",
                        Arrays.asList("alpha", "Bravo", "delta"))),
                Optional.empty(), true, ProjectLifecycleStatus.UNTITLED));
        Project renamed = seeded.renameSliderPreset("Bravo", "Zulu").getProject();
        assertEquals(Arrays.asList("alpha", "delta", "Echo", "Zulu"), names(renamed.getSliderPresets()));
        assertEquals(Arrays.asList("delta", "Echo", "Zulu"), renamed.getCustomMorphTargets().get(0).getSliderPresetNames());
        assertEquals(Arrays.asList("alpha", "delta", "Zulu"), renamed.getNpcMorphAssignments().get(0).getSliderPresetNames());

        Project unsorted = Project.from(new ProjectSnapshot(
                Arrays.asList(preset("zulu"), preset("Alpha")),
                Arrays.asList(new CustomMorphTargetSnapshot("b", Collections.<String>emptyList()),
                        new CustomMorphTargetSnapshot("A", Collections.<String>emptyList())),
                Arrays.asList(new NpcMorphAssignmentSnapshot("Two", "b.esp", "a", "Race", "000002",
                        Collections.<String>emptyList()),
                        new NpcMorphAssignmentSnapshot("One", "A.esp", "z", "Race", "000001",
                                Collections.<String>emptyList())),
                Optional.empty(), true, ProjectLifecycleStatus.UNTITLED));
        assertEquals(Arrays.asList("Alpha", "zulu"), names(unsorted.getSliderPresets()));
        assertEquals("A", unsorted.getCustomMorphTargets().get(0).getName());
        assertEquals("One", unsorted.getNpcMorphAssignments().get(0).getDisplayName());

        // Custom Morph Target and relationship operations: the target list stays
        // canonical, and every assigned name is stored in the catalog's casing and
        // in canonical order however it was requested.
        Project targets = renamed;
        targets = targets.addCustomMorphTarget(new CustomMorphTargetSnapshot("beta", Collections.<String>emptyList()))
                .getProject();
        targets = targets.addCustomMorphTarget(new CustomMorphTargetSnapshot("Alpha", Collections.<String>emptyList()))
                .getProject();
        assertEquals(Arrays.asList("Alpha", "beta", "Target"), targetNames(targets.getCustomMorphTargets()));

        Project.ReferrerKey beta = Project.ReferrerKey.customMorphTarget("BETA");
        targets = targets.assignSliderPreset(beta, "ZULU").getProject();
        assertEquals(Arrays.asList("Zulu"), targets.getCustomMorphTargets().get(1).getSliderPresetNames());
        targets = targets.assignSliderPresets(beta, Arrays.asList("echo", " ALPHA ", "zulu", "Echo")).getProject();
        assertEquals(Arrays.asList("alpha", "Echo", "Zulu"), targets.getCustomMorphTargets().get(1).getSliderPresetNames());

        Project.ReferrerKey editor = Project.ReferrerKey.npcMorphAssignment(
                new NpcMorphAssignmentIdentity("PLUGIN.ESP", "editor"));
        targets = targets.assignSliderPreset(editor, "ECHO").getProject();
        assertEquals(Arrays.asList("alpha", "delta", "Echo", "Zulu"),
                targets.getNpcMorphAssignments().get(0).getSliderPresetNames());

        targets = targets.unassignSliderPreset(beta, " echo ");
        assertEquals(Arrays.asList("alpha", "Zulu"), targets.getCustomMorphTargets().get(1).getSliderPresetNames());
        targets = targets.unassignSliderPreset(editor, "DELTA");
        assertEquals(Arrays.asList("alpha", "Echo", "Zulu"), targets.getNpcMorphAssignments().get(0).getSliderPresetNames());
        targets = targets.clearSliderPresetAssignments(beta);
        assertTrue(targets.getCustomMorphTargets().get(1).getSliderPresetNames().isEmpty());

        targets = targets.removeCustomMorphTarget("target");
        assertEquals(Arrays.asList("Alpha", "beta"), targetNames(targets.getCustomMorphTargets()));
        assertTrue(targets.clearCustomMorphTargets().getCustomMorphTargets().isEmpty());
        assertEquals(1, targets.clearCustomMorphTargets().getNpcMorphAssignments().size());

        // NPC Morph Assignment collection operations: the collection stays in
        // plugin-then-editor-ID order however NPCs are promoted, each promoted
        // NPC's names are stored in the catalog's casing and canonical order, a
        // fill stores the catalog's casing, and removals keep the remaining order.
        Project npcs = targets.addNpcMorphAssignment(npc("Zulu", "Skyrim.esm", "ZuluEditor",
                Arrays.asList("ECHO", "alpha", "echo"))).getProject();
        npcs = npcs.addNpcMorphAssignments(Arrays.asList(
                npc("Dawnguard", "Dawnguard.esm", "ZuluEditor", Collections.<String>emptyList()),
                npc("Alpha", "skyrim.ESM", "AlphaEditor", Arrays.asList("zulu")))).getProject();
        assertEquals(Arrays.asList("Dawnguard.esm/ZuluEditor", "Plugin.esp/Editor", "skyrim.ESM/AlphaEditor",
                "Skyrim.esm/ZuluEditor"), identities(npcs.getNpcMorphAssignments()));
        assertEquals(Arrays.asList("alpha", "Echo"), npcs.getNpcMorphAssignments().get(3).getSliderPresetNames());
        assertEquals(Arrays.asList("Zulu"), npcs.getNpcMorphAssignments().get(2).getSliderPresetNames());

        npcs = npcs.fillEmptyNpcMorphAssignments(Arrays.asList(
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("DAWNGUARD.ESM", "zuluEditor"), "ECHO"),
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("skyrim.esm", "alphaeditor"), "alpha")))
                .getProject();
        assertEquals(Arrays.asList("Echo"), npcs.getNpcMorphAssignments().get(0).getSliderPresetNames());
        assertEquals(Arrays.asList("Zulu"), npcs.getNpcMorphAssignments().get(2).getSliderPresetNames(),
                "an occupied NPC must keep its assignments");

        npcs = npcs.clearSliderPresetAssignments(Arrays.asList(
                Project.ReferrerKey.npcMorphAssignment(new NpcMorphAssignmentIdentity("skyrim.esm", "ALPHAEDITOR")),
                Project.ReferrerKey.npcMorphAssignment(new NpcMorphAssignmentIdentity("Plugin.esp", "Editor"))));
        assertTrue(npcs.getNpcMorphAssignments().get(1).getSliderPresetNames().isEmpty());
        assertTrue(npcs.getNpcMorphAssignments().get(2).getSliderPresetNames().isEmpty());
        assertEquals(Arrays.asList("Echo"), npcs.getNpcMorphAssignments().get(0).getSliderPresetNames());

        npcs = npcs.removeNpcMorphAssignment(new NpcMorphAssignmentIdentity("plugin.ESP", "EDITOR"));
        assertEquals(Arrays.asList("Dawnguard.esm/ZuluEditor", "skyrim.ESM/AlphaEditor", "Skyrim.esm/ZuluEditor"),
                identities(npcs.getNpcMorphAssignments()));
        npcs = npcs.removeNpcMorphAssignments(Arrays.asList(
                new NpcMorphAssignmentIdentity("SKYRIM.ESM", "zulueditor"),
                new NpcMorphAssignmentIdentity("Missing.esm", "Missing")));
        assertEquals(Arrays.asList("Dawnguard.esm/ZuluEditor", "skyrim.ESM/AlphaEditor"),
                identities(npcs.getNpcMorphAssignments()));
        assertTrue(npcs.clearNpcMorphAssignments().getNpcMorphAssignments().isEmpty());
        assertEquals(2, npcs.clearNpcMorphAssignments().getCustomMorphTargets().size());
    }

    /**
     * Proves that a snapshot violating name uniqueness or referential integrity
     * cannot become an aggregate, so a loader regression fails loudly, while a
     * duplicate add or rename is reported as a diagnostic instead of thrown.
     */
    @Test
    void duplicateNamesAndDanglingReferencesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Project.from(snapshot(
                Arrays.asList(preset("Alpha"), preset("ALPHA")),
                Collections.<CustomMorphTargetSnapshot>emptyList(),
                Collections.<NpcMorphAssignmentSnapshot>emptyList())));
        assertThrows(IllegalArgumentException.class, () -> Project.from(snapshot(
                Arrays.asList(preset("Alpha")),
                Arrays.asList(new CustomMorphTargetSnapshot("Target", Arrays.asList("Alpha", "Missing"))),
                Collections.<NpcMorphAssignmentSnapshot>emptyList())));
        assertThrows(IllegalArgumentException.class, () -> Project.from(snapshot(
                Arrays.asList(preset("Alpha")),
                Collections.<CustomMorphTargetSnapshot>emptyList(),
                Arrays.asList(npc("Lydia", "Skyrim.esm", "Housecarl", Arrays.asList("Missing"))))));
        assertThrows(IllegalArgumentException.class, () -> Project.from(snapshot(
                Arrays.asList(preset("Alpha")),
                Arrays.asList(new CustomMorphTargetSnapshot("Target", Arrays.asList("Alpha")),
                        new CustomMorphTargetSnapshot("target", Arrays.asList("Alpha"))),
                Collections.<NpcMorphAssignmentSnapshot>emptyList())));
        assertThrows(IllegalArgumentException.class, () -> Project.from(snapshot(
                Arrays.asList(preset("Alpha")),
                Collections.<CustomMorphTargetSnapshot>emptyList(),
                Arrays.asList(npc("Lydia", "Skyrim.esm", "Housecarl", Arrays.asList("Alpha")),
                        npc("Other", "skyrim.ESM", "HOUSECARL", Arrays.asList("Alpha"))))));

        Project project = project();
        Project.Result added = project.addSliderPreset(preset("ALPHA"));
        assertTrue(added.isRejected());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE, added.getDiagnostic().getCode());
        assertEquals("slider-preset.name", added.getDiagnostic().getSourceLocation().getElement().get());
        Project.Result renamed = project.renameSliderPreset("Alpha", "beta");
        assertTrue(renamed.isRejected());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE, renamed.getDiagnostic().getCode());
        assertFalse(project.addSliderPreset(preset("Gamma")).isRejected());

        // A duplicate Custom Morph Target name is a diagnostic; an assignment to a
        // Slider Preset the catalog lacks is a diagnostic, and a batch containing
        // one is refused as a whole; a referrer the caller did not look up throws.
        Project.Result duplicateTarget = project.addCustomMorphTarget(
                new CustomMorphTargetSnapshot("BOTH", Collections.<String>emptyList()));
        assertTrue(duplicateTarget.isRejected());
        assertEquals(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_DUPLICATE,
                duplicateTarget.getDiagnostic().getCode());
        assertEquals("custom-morph-target.name",
                duplicateTarget.getDiagnostic().getSourceLocation().getElement().get());

        Project.ReferrerKey onlyBeta = Project.ReferrerKey.customMorphTarget("OnlyBeta");
        Project.Result unknown = project.assignSliderPreset(onlyBeta, "Gamma");
        assertTrue(unknown.isRejected());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, unknown.getDiagnostic().getCode());
        assertEquals("slider-preset.name", unknown.getDiagnostic().getSourceLocation().getElement().get());
        Project.Result partial = project.assignSliderPresets(onlyBeta, Arrays.asList("Alpha", "Gamma"));
        assertTrue(partial.isRejected());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, partial.getDiagnostic().getCode());
        assertTrue(project.assignSliderPreset(onlyBeta, null).isRejected());
        assertEquals(Arrays.asList("Beta"), project.getCustomMorphTargets().get(1).getSliderPresetNames());

        Project.ReferrerKey missingTarget = Project.ReferrerKey.customMorphTarget("Missing");
        Project.ReferrerKey missingNpc = Project.ReferrerKey.npcMorphAssignment(
                new NpcMorphAssignmentIdentity("Missing.esp", "Missing"));
        assertThrows(IllegalArgumentException.class, () -> project.assignSliderPreset(missingTarget, "Alpha"));
        // A missing referrer is a caller error even when the preset is unknown too.
        assertThrows(IllegalArgumentException.class, () -> project.assignSliderPreset(missingTarget, "Gamma"));
        assertThrows(IllegalArgumentException.class,
                () -> project.assignSliderPresets(missingNpc, Collections.<String>emptyList()));
        assertThrows(IllegalArgumentException.class, () -> project.unassignSliderPreset(missingNpc, "Alpha"));
        assertThrows(IllegalArgumentException.class, () -> project.clearSliderPresetAssignments(missingTarget));
        assertThrows(IllegalArgumentException.class, () -> project.removeCustomMorphTarget("Missing"));

        // A duplicate NPC identity is a diagnostic at the identity location; a bulk
        // promotion is refused as a whole by an identity repeated within the batch
        // or by a member naming a Slider Preset the catalog lacks, while a member
        // already in the Project is skipped rather than refused.
        Project.Result duplicateNpc = project.addNpcMorphAssignment(
                npc("Other", "skyrim.ESM", "HOUSECARLWHITERUN", Collections.<String>emptyList()));
        assertTrue(duplicateNpc.isRejected());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE, duplicateNpc.getDiagnostic().getCode());
        assertEquals("npc-morph-assignment.identity",
                duplicateNpc.getDiagnostic().getSourceLocation().getElement().get());
        Project.Result unknownPresetOnAdd = project.addNpcMorphAssignment(
                npc("Bad", "Skyrim.esm", "Bad", Arrays.asList("Gamma")));
        assertTrue(unknownPresetOnAdd.isRejected());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, unknownPresetOnAdd.getDiagnostic().getCode());
        Project.Result repeatedInBatch = project.addNpcMorphAssignments(Arrays.asList(
                npc("New", "Skyrim.esm", "New", Collections.<String>emptyList()),
                npc("Again", "SKYRIM.ESM", "NEW", Collections.<String>emptyList())));
        assertTrue(repeatedInBatch.isRejected());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE,
                repeatedInBatch.getDiagnostic().getCode());
        Project.Result partialBatch = project.addNpcMorphAssignments(Arrays.asList(
                npc("Lydia", "Skyrim.esm", "HousecarlWhiterun", Arrays.asList("Gamma")),
                npc("New", "Skyrim.esm", "New", Arrays.asList("Alpha")),
                npc("Bad", "Skyrim.esm", "Bad", Arrays.asList("Gamma"))));
        assertTrue(partialBatch.isRejected());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, partialBatch.getDiagnostic().getCode());

        // Fill-empty is a batch of caller decisions, so a repeated identity, an
        // unknown NPC, and an unknown Slider Preset are each diagnosed in choice
        // order, and an unknown Slider Preset is refused even for an occupied NPC.
        NpcMorphAssignmentIdentity lydia = new NpcMorphAssignmentIdentity("Skyrim.esm", "HousecarlWhiterun");
        Project.Result repeatedChoice = project.fillEmptyNpcMorphAssignments(Arrays.asList(
                new NpcSliderPresetChoice(lydia, "Alpha"),
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("SKYRIM.ESM", "housecarlwhiterun"), "Beta")));
        assertTrue(repeatedChoice.isRejected());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE, repeatedChoice.getDiagnostic().getCode());
        Project.Result unknownNpc = project.fillEmptyNpcMorphAssignments(Arrays.asList(
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("Missing.esp", "Missing"), "Alpha")));
        assertTrue(unknownNpc.isRejected());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_NOT_FOUND, unknownNpc.getDiagnostic().getCode());
        assertEquals("npc-morph-assignment.identity",
                unknownNpc.getDiagnostic().getSourceLocation().getElement().get());
        Project.Result occupiedUnknownPreset = project.fillEmptyNpcMorphAssignments(Arrays.asList(
                new NpcSliderPresetChoice(lydia, "Gamma")));
        assertTrue(occupiedUnknownPreset.isRejected());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, occupiedUnknownPreset.getDiagnostic().getCode());

        // Removing or clearing an NPC the caller never looked up throws.
        assertThrows(IllegalArgumentException.class,
                () -> project.removeNpcMorphAssignment(new NpcMorphAssignmentIdentity("Missing.esp", "Missing")));
        assertThrows(IllegalArgumentException.class,
                () -> project.clearSliderPresetAssignments(Arrays.asList(missingNpc)));
    }

    /**
     * Builds the shared fixture: Alpha and Beta, one target referencing both, one
     * target referencing Beta, one NPC referencing Alpha, one NPC referencing both.
     */
    private static Project project() {
        return Project.from(snapshot(Arrays.asList(preset("Alpha"), preset("Beta")),
                Arrays.asList(new CustomMorphTargetSnapshot("Both", Arrays.asList("Alpha", "Beta")),
                        new CustomMorphTargetSnapshot("OnlyBeta", Arrays.asList("Beta"))),
                Arrays.asList(npc("Lydia", "Skyrim.esm", "HousecarlWhiterun", Arrays.asList("Alpha")),
                        npc("Serana", "Dawnguard.esm", "Serana", Arrays.asList("Alpha", "Beta")))));
    }

    private static ProjectSnapshot snapshot(List<SliderPresetSnapshot> presets,
            List<CustomMorphTargetSnapshot> targets, List<NpcMorphAssignmentSnapshot> npcs) {
        return new ProjectSnapshot(presets, targets, npcs, Optional.empty(), true,
                ProjectLifecycleStatus.UNTITLED);
    }

    private static SliderPresetSnapshot preset(String name) {
        return new SliderPresetSnapshot(name, false, Arrays.asList(
                new SliderChoiceSnapshot("Waist", true, Integer.valueOf(20), Integer.valueOf(80), 20, 80, 0, 100,
                        false)));
    }

    /** Copies a Slider Preset into a distinct instance with equal observable values. */
    private static SliderPresetSnapshot copy(SliderPresetSnapshot source) {
        return new SliderPresetSnapshot(source.getName(), source.isUunp(),
                new ArrayList<>(source.getSliderChoices()));
    }

    private static NpcMorphAssignmentSnapshot npc(String displayName, String pluginName, String editorId,
            List<String> sliderPresetNames) {
        return new NpcMorphAssignmentSnapshot(displayName, pluginName, editorId, "NordRace", "000A2C94",
                sliderPresetNames);
    }

    private static List<String> names(List<SliderPresetSnapshot> presets) {
        List<String> names = new ArrayList<>();
        for (SliderPresetSnapshot preset : presets)
            names.add(preset.getName());
        return names;
    }

    private static List<String> targetNames(List<CustomMorphTargetSnapshot> targets) {
        List<String> names = new ArrayList<>();
        for (CustomMorphTargetSnapshot target : targets)
            names.add(target.getName());
        return names;
    }

    /** Renders each NPC Morph Assignment's identity as {@code plugin/editorId} in collection order. */
    private static List<String> identities(List<NpcMorphAssignmentSnapshot> npcs) {
        List<String> identities = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot npc : npcs)
            identities.add(npc.getPluginName() + "/" + npc.getEditorId());
        return identities;
    }
}
