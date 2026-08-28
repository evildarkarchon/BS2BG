package com.asdasfa.jbs2bg.filtering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectEdit;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.UnchangedOutcome;

/**
 * Characterizes the visible scope of every bulk command: each one freezes the
 * complete visible identity set of its table and submits exactly one Project
 * edit (or one NPC Database removal) built from that frozen scope.
 */
class VisibleScopeCommandsTest {

    private static final NpcMorphAssignmentIdentity ALPHA = new NpcMorphAssignmentIdentity("Skyrim.esm",
            "AlphaEditor");
    private static final NpcMorphAssignmentIdentity BETA = new NpcMorphAssignmentIdentity("Skyrim.esm",
            "BetaEditor");
    private static final NpcMorphAssignmentIdentity GAMMA = new NpcMorphAssignmentIdentity("Dawnguard.esm",
            "GammaEditor");
    private static final NpcMorphAssignmentIdentity DELTA = new NpcMorphAssignmentIdentity("Dawnguard.esm",
            "DeltaEditor");

    /**
     * Clear NPC Morph Assignments removes exactly the visible NPC Morph
     * Assignments; hidden ones survive, and sorting does not widen the scope.
     */
    @Test
    void clearNpcMorphAssignmentsRemovesOnlyTheVisibleIdentities() {
        ProjectSession session = seededSession();
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView(session);
        view.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dawnguard.esm")));
        view.setSortOrder(Arrays.asList(SortKey.descending("Name")));

        ProjectOutcome outcome = session.apply(VisibleScopeCommands.clearNpcMorphAssignments(view.visibleSet()));

        assertTrue(outcome instanceof ChangedOutcome);
        assertEquals(Arrays.asList(DELTA, GAMMA), identitiesOf(outcome.getSnapshot()));
    }

    /**
     * Clear Assignments clears the Slider Presets of exactly the visible NPC
     * Morph Assignments and leaves hidden assignments intact.
     */
    @Test
    void clearAssignmentsClearsOnlyVisibleNpcMorphAssignments() {
        ProjectSession session = seededSession();
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView(session);
        view.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dawnguard.esm")));

        ProjectOutcome outcome = session.apply(VisibleScopeCommands.clearAssignments(view.visibleSet()));

        assertTrue(outcome instanceof ChangedOutcome);
        assertEquals(Collections.emptyList(), presetsOf(outcome.getSnapshot(), ALPHA));
        assertEquals(Arrays.asList("Beta"), presetsOf(outcome.getSnapshot(), DELTA));

        // Nothing visible is assigned any more, so a repeat is Unchanged.
        view.setRows(session.getSnapshot().getNpcMorphAssignments());
        assertTrue(session.apply(VisibleScopeCommands.clearAssignments(view.visibleSet())) instanceof UnchangedOutcome);
    }

    /**
     * Fill Empty gives a caller-chosen Slider Preset to each visible NPC Morph
     * Assignment that has none; visible NPCs with assignments and hidden empty
     * NPCs are untouched. The random draw is completed before the edit exists.
     */
    @Test
    void fillEmptyFillsOnlyVisibleEmptyNpcMorphAssignments() {
        ProjectSession session = seededSession();
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView(session);
        view.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dawnguard.esm")));
        List<Integer> bounds = new ArrayList<>();

        ProjectEdit edit = VisibleScopeCommands.fillEmpty(view.visibleSet(), Arrays.asList("Alpha", "Beta"),
                bound -> {
                    bounds.add(bound);
                    return 1;
                });
        ProjectOutcome outcome = session.apply(edit);

        assertTrue(outcome instanceof ChangedOutcome);
        assertEquals(Arrays.asList(2), bounds);
        assertEquals(Arrays.asList("Alpha"), presetsOf(outcome.getSnapshot(), ALPHA));
        assertEquals(Arrays.asList("Beta"), presetsOf(outcome.getSnapshot(), BETA));
        assertEquals(Collections.emptyList(), presetsOf(outcome.getSnapshot(), GAMMA));
        assertThrows(IllegalArgumentException.class, () -> VisibleScopeCommands.fillEmpty(view.visibleSet(),
                Collections.<String>emptyList(), bound -> 0));
    }

    /**
     * The frozen scope is what the edit applies to: widening the filter or
     * rendering a new snapshot after freezing changes nothing about the edit.
     */
    @Test
    void bulkEditsAreBuiltFromTheFrozenScopeNotTheLiveView() {
        ProjectSession session = seededSession();
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView(session);
        view.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dawnguard.esm")));
        VisibleSet<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> frozen = view.visibleSet();
        ProjectEdit edit = VisibleScopeCommands.clearNpcMorphAssignments(frozen);

        view.clearAllCriteria();
        view.setRows(session.apply(NpcMorphAssignmentEdits.addNpc(npc("Epsilon", "Skyrim.esm", "EpsilonEditor")))
                .getSnapshot().getNpcMorphAssignments());
        assertEquals(5, view.visibleSet().size());

        ProjectOutcome outcome = session.apply(edit);

        assertEquals(Arrays.asList(DELTA, GAMMA, new NpcMorphAssignmentIdentity("Skyrim.esm", "EpsilonEditor")),
                identitiesOf(outcome.getSnapshot()));
        assertEquals(Arrays.asList(ALPHA, BETA), frozen.getIdentities());
    }

    /**
     * NPC Database Add All promotes exactly the visible NPC Database entries as
     * NPC Morph Assignments, each with the caller's optional random choice, in
     * one atomic edit; entries already in the Project are no-ops.
     */
    @Test
    void addAllNpcsPromotesOnlyVisibleNpcDatabaseEntries() {
        ProjectSession session = seededSession();
        NPC epsilon = new NPC("Skyrim.esm | Epsilon | EpsilonEditor | NordRace \"Nord\" | 0001A696");
        NPC zeta = new NPC("Dragonborn.esm | Zeta | ZetaEditor | BretonRace \"Breton\" | 0002B7C1");
        NPC existingAlpha = new NPC("SKYRIM.ESM | Alpha Again | alphaeditor | NordRace \"Nord\" | 0000000D");
        FilteredView<NPC, NpcMorphAssignmentIdentity> database = new FilteredView<>(NpcTableColumns.npcDatabase(),
                ProjectIdentities::npcDatabaseEntry);
        database.setRows(Arrays.asList(epsilon, zeta, existingAlpha));
        database.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dragonborn.esm")));

        ProjectEdit edit = VisibleScopeCommands.addAllNpcs(database.visibleSet(), () -> Arrays.asList("Beta"));
        ProjectOutcome outcome = session.apply(edit);

        assertTrue(outcome instanceof ChangedOutcome);
        NpcMorphAssignmentIdentity epsilonIdentity = new NpcMorphAssignmentIdentity("Skyrim.esm", "EpsilonEditor");
        assertEquals(Arrays.asList(DELTA, GAMMA, ALPHA, BETA, epsilonIdentity), identitiesOf(outcome.getSnapshot()));
        assertEquals(Arrays.asList("Beta"), presetsOf(outcome.getSnapshot(), epsilonIdentity));
        assertEquals(Arrays.asList("Alpha"), presetsOf(outcome.getSnapshot(), ALPHA));
        NpcMorphAssignmentSnapshot promoted = find(outcome.getSnapshot(), epsilonIdentity);
        assertEquals("Epsilon", promoted.getDisplayName());
        assertEquals("NordRace", promoted.getRace());
        assertEquals("1A696", promoted.getFormId());

        ProjectEdit nothingNew = VisibleScopeCommands.addAllNpcs(database.visibleSet(),
                () -> Collections.<String>emptyList());
        assertTrue(session.apply(nothingNew) instanceof UnchangedOutcome);
    }

    /**
     * NPC Database Clear removes exactly the visible entries from the
     * session-scoped NPC Database. It is not a Project edit; the removal list is
     * frozen so that the live database can be mutated safely while it is used.
     */
    @Test
    void clearNpcDatabaseRemovesOnlyVisibleEntriesFromTheFrozenScope() {
        NPC epsilon = new NPC("Skyrim.esm | Epsilon | EpsilonEditor | NordRace \"Nord\" | 0001A696");
        NPC zeta = new NPC("Dragonborn.esm | Zeta | ZetaEditor | BretonRace \"Breton\" | 0002B7C1");
        NPC eta = new NPC("Skyrim.esm | Eta | EtaEditor | BretonRace \"Breton\" | 0002B7C2");
        List<NPC> npcDatabase = new ArrayList<>(Arrays.asList(epsilon, zeta, eta));
        FilteredView<NPC, NpcMorphAssignmentIdentity> database = new FilteredView<>(NpcTableColumns.npcDatabase(),
                ProjectIdentities::npcDatabaseEntry);
        database.setRows(npcDatabase);
        database.setCriterion(ColumnCriterion.hiding("Race", Arrays.asList("BretonRace")));
        database.setSortOrder(Arrays.asList(SortKey.descending("Name")));

        List<NPC> removals = VisibleScopeCommands.clearNpcDatabase(database.visibleSet());
        database.clearAllCriteria();
        npcDatabase.removeAll(removals);

        assertEquals(Arrays.asList(epsilon), removals);
        assertEquals(Arrays.asList(zeta, eta), npcDatabase);
        assertThrows(UnsupportedOperationException.class, () -> removals.add(zeta));
    }

    /**
     * The canonical column definitions expose the same cell text the tables
     * render, so a criterion built from displayed values matches the seam.
     */
    @Test
    void npcTableColumnsMatchRenderedCellText() {
        NpcMorphAssignmentSnapshot npc = new NpcMorphAssignmentSnapshot("Alpha", "Skyrim.esm", "AlphaEditor",
                "NordRace", "1A696", Arrays.asList("Alpha", "Beta"));
        List<FilterColumn<NpcMorphAssignmentSnapshot>> columns = NpcTableColumns.npcMorphAssignments();
        List<String> ids = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (FilterColumn<NpcMorphAssignmentSnapshot> column : columns) {
            ids.add(column.getId());
            values.add(column.cellValueOf(npc));
        }
        assertEquals(Arrays.asList("Name", "Master", "Race", "EditorID", "FormID", "Slider Presets"), ids);
        assertEquals(Arrays.asList("Alpha", "Skyrim.esm", "NordRace", "AlphaEditor", "1A696", "Alpha|Beta"), values);

        NPC entry = new NPC("Skyrim.esm | | AlphaEditor | NordRace \"Nord\" | 0001A696");
        List<String> databaseIds = new ArrayList<>();
        List<String> databaseValues = new ArrayList<>();
        for (FilterColumn<NPC> column : NpcTableColumns.npcDatabase()) {
            databaseIds.add(column.getId());
            databaseValues.add(column.cellValueOf(entry));
        }
        assertEquals(Arrays.asList("Name", "Master", "Race", "EditorID", "FormID"), databaseIds);
        assertEquals(Arrays.asList("Unnamed (AlphaEditor)", "Skyrim.esm", "NordRace", "AlphaEditor", "1A696"),
                databaseValues);
        assertSame(ProjectIdentities.npcDatabaseEntry(entry).getClass(), ProjectIdentities.npcMorphAssignment(npc)
                .getClass());
        assertEquals(ProjectIdentities.npcDatabaseEntry(entry), ProjectIdentities.npcMorphAssignment(npc));
        assertFalse(NpcTableColumns.npcMorphAssignments().isEmpty());
    }

    /**
     * Seeds Alpha (Skyrim, [Alpha]), Beta (Skyrim, []), Gamma (Dawnguard, []),
     * and Delta (Dawnguard, [Beta]) with Slider Presets Alpha and Beta.
     */
    private static ProjectSession seededSession() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("Beta"));
        session.apply(NpcMorphAssignmentEdits.addNpcs(Arrays.asList(
                new NpcMorphAssignmentSnapshot("Alpha", "Skyrim.esm", "AlphaEditor", "NordRace", "1", Arrays.asList("Alpha")),
                npc("Beta", "Skyrim.esm", "BetaEditor"),
                npc("Gamma", "Dawnguard.esm", "GammaEditor"),
                new NpcMorphAssignmentSnapshot("Delta", "Dawnguard.esm", "DeltaEditor", "BretonRace", "4",
                        Arrays.asList("Beta")))));
        return session;
    }

    private static FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> npcView(
            ProjectSession session) {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = new FilteredView<>(
                NpcTableColumns.npcMorphAssignments(), ProjectIdentities::npcMorphAssignment);
        view.setRows(session.getSnapshot().getNpcMorphAssignments());
        return view;
    }

    private static NpcMorphAssignmentSnapshot npc(String displayName, String pluginName, String editorId) {
        return new NpcMorphAssignmentSnapshot(displayName, pluginName, editorId, "NordRace", "1A696",
                Collections.<String>emptyList());
    }

    private static List<NpcMorphAssignmentIdentity> identitiesOf(ProjectSnapshot snapshot) {
        List<NpcMorphAssignmentIdentity> identities = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot npc : snapshot.getNpcMorphAssignments())
            identities.add(ProjectIdentities.npcMorphAssignment(npc));
        return identities;
    }

    private static NpcMorphAssignmentSnapshot find(ProjectSnapshot snapshot, NpcMorphAssignmentIdentity identity) {
        for (NpcMorphAssignmentSnapshot npc : snapshot.getNpcMorphAssignments())
            if (ProjectIdentities.npcMorphAssignment(npc).equals(identity))
                return npc;
        throw new AssertionError("missing " + identity);
    }

    private static List<String> presetsOf(ProjectSnapshot snapshot, NpcMorphAssignmentIdentity identity) {
        return find(snapshot, identity).getSliderPresetNames();
    }
}
