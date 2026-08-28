package com.asdasfa.jbs2bg.filtering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

/**
 * Characterizes the logical visible-set behavior of the application-owned
 * filtering seam: AND-combined column criteria, presentation-only sorting,
 * identity-based membership and selection, frozen bulk scope, and retained
 * filter and sort state across row replacement.
 */
class FilteredViewTest {

    private static final FilterColumn<NpcMorphAssignmentSnapshot> NAME = FilterColumn.of("Name",
            NpcMorphAssignmentSnapshot::getDisplayName);
    private static final FilterColumn<NpcMorphAssignmentSnapshot> MASTER = FilterColumn.of("Master",
            NpcMorphAssignmentSnapshot::getPluginName);
    private static final FilterColumn<NpcMorphAssignmentSnapshot> RACE = FilterColumn.of("Race",
            NpcMorphAssignmentSnapshot::getRace);

    private static final NpcMorphAssignmentSnapshot ALPHA = npc("Alpha", "Skyrim.esm", "AlphaEditor", "NordRace");
    private static final NpcMorphAssignmentSnapshot BETA = npc("Beta", "Skyrim.esm", "BetaEditor", "BretonRace");
    private static final NpcMorphAssignmentSnapshot GAMMA = npc("Gamma", "Dawnguard.esm", "GammaEditor", "NordRace");
    private static final NpcMorphAssignmentSnapshot DELTA = npc("Delta", "Dawnguard.esm", "DeltaEditor",
            "BretonRace");

    /**
     * Every active column criterion must admit a row for it to be visible; a
     * criterion hides exactly the cell values it names and admits every other
     * value, including values it has never seen.
     */
    @Test
    void activeColumnCriteriaCombineWithAnd() {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView();
        view.setRows(Arrays.asList(ALPHA, BETA, GAMMA, DELTA));

        view.setCriterion(ColumnCriterion.hiding("Race", Arrays.asList("NordRace")));
        assertEquals(identities(BETA, DELTA), view.visibleSet().getIdentities());
        assertTrue(view.isFiltered());

        view.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dawnguard.esm")));
        assertEquals(identities(BETA), view.visibleSet().getIdentities());
        assertEquals(2, view.getCriteria().size());

        view.clearCriterion("Race");
        assertEquals(identities(ALPHA, BETA), view.visibleSet().getIdentities());

        // A criterion that hides nothing is inactive and removes the column's entry.
        view.setCriterion(ColumnCriterion.hiding("Master", Collections.<String>emptyList()));
        assertTrue(view.getCriteria().isEmpty());
        assertFalse(view.isFiltered());
        assertEquals(identities(ALPHA, BETA, GAMMA, DELTA), view.visibleSet().getIdentities());
    }

    /** Cell values are matched exactly, the way the checklist filter matched them. */
    @Test
    void criteriaMatchCellValuesExactly() {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView();
        view.setRows(Arrays.asList(ALPHA, BETA, GAMMA, DELTA));

        view.setCriterion(ColumnCriterion.hiding("Race", Arrays.asList("nordrace")));

        assertEquals(identities(ALPHA, BETA, GAMMA, DELTA), view.visibleSet().getIdentities());
    }

    /**
     * Sorting reorders the visible rows and nothing else: membership, the
     * identity scope, and the selection are unchanged, and rows that compare
     * equal keep their source order.
     */
    @Test
    void sortingChangesPresentationOrderWithoutChangingMembershipOrScope() {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView();
        view.setRows(Arrays.asList(ALPHA, BETA, GAMMA, DELTA));
        view.setCriterion(ColumnCriterion.hiding("Race", Arrays.asList("NordRace")));
        assertTrue(view.select(identityOf(DELTA)));

        view.setSortOrder(Arrays.asList(SortKey.descending("Name")));
        VisibleSet<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> descending = view.visibleSet();
        assertEquals(identities(DELTA, BETA), descending.getIdentities());
        assertEquals(Arrays.asList(DELTA, BETA), descending.getRows());
        assertEquals(Optional.of(identityOf(DELTA)), view.getSelection());
        assertEquals(Arrays.asList(SortKey.descending("Name")), view.getSortOrder());

        view.clearAllCriteria();
        view.setSortOrder(Arrays.asList(SortKey.ascending("Master"), SortKey.descending("Race")));
        assertEquals(identities(GAMMA, DELTA, ALPHA, BETA), view.visibleSet().getIdentities());

        // Equal keys are stable: source order decides.
        view.setSortOrder(Arrays.asList(SortKey.ascending("Master")));
        assertEquals(identities(GAMMA, DELTA, ALPHA, BETA), view.visibleSet().getIdentities());

        view.setSortOrder(Collections.<SortKey>emptyList());
        assertEquals(identities(ALPHA, BETA, GAMMA, DELTA), view.visibleSet().getIdentities());
    }

    /**
     * Membership and selection are keyed by logical identity, so replacement
     * snapshot instances and differently cased identities resolve to the same
     * row, and neither indexes nor instance identity are part of the contract.
     */
    @Test
    void visibleIdentitiesAndSelectionUseLogicalIdentity() {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView();
        view.setRows(Arrays.asList(ALPHA, BETA));
        view.setSortOrder(Arrays.asList(SortKey.descending("Name")));

        assertTrue(view.select(new NpcMorphAssignmentIdentity("SKYRIM.ESM", "alphaeditor")));
        assertEquals(Optional.of(identityOf(ALPHA)), view.getSelection());
        assertTrue(view.visibleSet().contains(new NpcMorphAssignmentIdentity("skyrim.esm", "ALPHAEDITOR")));

        NpcMorphAssignmentSnapshot replacedAlpha = npc("Alpha Renamed", "Skyrim.esm", "AlphaEditor", "NordRace");
        NpcMorphAssignmentSnapshot inserted = npc("Aardvark", "Skyrim.esm", "AardvarkEditor", "NordRace");
        view.setRows(Arrays.asList(inserted, replacedAlpha, BETA));

        assertEquals(Optional.of(identityOf(ALPHA)), view.getSelection());
        VisibleSet<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> visible = view.visibleSet();
        assertSame(replacedAlpha, visible.getRows().get(1));
        assertEquals(identities(BETA, replacedAlpha, inserted), visible.getIdentities());
    }

    /**
     * A frozen visible set is the complete identity scope of one bulk command:
     * later filter, sort, or row changes cannot reach into it, and callers
     * cannot mutate it either.
     */
    @Test
    void frozenVisibleSetIsImmuneToLaterFilterAndRowChanges() {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView();
        view.setRows(Arrays.asList(ALPHA, BETA, GAMMA, DELTA));
        view.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dawnguard.esm")));

        VisibleSet<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> frozen = view.visibleSet();
        List<NpcMorphAssignmentIdentity> frozenIdentities = frozen.getIdentities();

        view.clearAllCriteria();
        view.setSortOrder(Arrays.asList(SortKey.descending("Name")));
        view.setRows(Collections.<NpcMorphAssignmentSnapshot>emptyList());

        assertEquals(identities(ALPHA, BETA), frozen.getIdentities());
        assertSame(frozenIdentities, frozen.getIdentities());
        assertEquals(Arrays.asList(ALPHA, BETA), frozen.getRows());
        assertEquals(2, frozen.size());
        assertFalse(frozen.isEmpty());
        assertTrue(frozen.contains(identityOf(ALPHA)));
        assertFalse(frozen.contains(identityOf(GAMMA)));
        assertThrows(UnsupportedOperationException.class, () -> frozen.getIdentities().add(identityOf(GAMMA)));
        assertThrows(UnsupportedOperationException.class, () -> frozen.getRows().add(GAMMA));
        assertNotSame(frozen, view.visibleSet());
        assertTrue(view.visibleSet().isEmpty());
    }

    /**
     * Row replacement models New, Open, and every rendered edit outcome: filter
     * and sort choices are retained verbatim, while a selection whose identity is
     * absent or hidden by an active criterion is dropped rather than restored.
     * (The production New and Open paths additionally clear the selection
     * outright; the retained-selection case below is the rendered-edit path.)
     */
    @Test
    void filterAndSortSurviveRowReplacementButHiddenSelectionIsNotRestored() {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView();
        view.setRows(Arrays.asList(ALPHA, BETA, GAMMA, DELTA));
        ColumnCriterion hideNords = ColumnCriterion.hiding("Race", Arrays.asList("NordRace"));
        List<SortKey> byNameDescending = Arrays.asList(SortKey.descending("Name"));
        view.setCriterion(hideNords);
        view.setSortOrder(byNameDescending);
        assertTrue(view.select(identityOf(BETA)));

        // New: no rows at all.
        view.setRows(Collections.<NpcMorphAssignmentSnapshot>emptyList());
        assertEquals(Arrays.asList(hideNords), view.getCriteria());
        assertEquals(byNameDescending, view.getSortOrder());
        assertTrue(view.isFiltered());
        assertEquals(Optional.empty(), view.getSelection());

        // Open: Beta is back but is now a Nord, so the retained criterion hides it.
        NpcMorphAssignmentSnapshot nordBeta = npc("Beta", "Skyrim.esm", "BetaEditor", "NordRace");
        view.setRows(Arrays.asList(ALPHA, nordBeta, DELTA));
        assertEquals(Arrays.asList(hideNords), view.getCriteria());
        assertEquals(byNameDescending, view.getSortOrder());
        assertEquals(identities(DELTA), view.visibleSet().getIdentities());
        assertFalse(view.select(identityOf(BETA)));
        assertEquals(Optional.empty(), view.getSelection());

        // A rendered edit outcome with the selected identity still visible keeps it.
        assertTrue(view.select(identityOf(DELTA)));
        view.setRows(Arrays.asList(DELTA, ALPHA));
        assertEquals(Optional.of(identityOf(DELTA)), view.getSelection());

        // A criterion set later that hides the selected row also drops the selection.
        view.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dawnguard.esm")));
        assertEquals(Optional.empty(), view.getSelection());
        view.clearSelection();
        assertEquals(Optional.empty(), view.getSelection());
    }

    /** Only a visible identity can be selected; unknown or hidden identities are rejected. */
    @Test
    void selectingAnAbsentOrHiddenIdentityIsRejected() {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView();
        view.setRows(Arrays.asList(ALPHA, BETA));
        view.setCriterion(ColumnCriterion.hiding("Name", Arrays.asList("Beta")));

        assertFalse(view.select(identityOf(GAMMA)));
        assertFalse(view.select(identityOf(BETA)));
        assertEquals(Optional.empty(), view.getSelection());
        assertTrue(view.select(identityOf(ALPHA)));
        assertThrows(NullPointerException.class, () -> view.select(null));
    }

    /** Source rows must be identity-unique and criteria must name a known column. */
    @Test
    void invalidRowsAndCriteriaAreRejected() {
        FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> view = npcView();
        NpcMorphAssignmentSnapshot duplicateAlpha = npc("Other", "SKYRIM.ESM", "alphaeditor", "NordRace");

        assertThrows(IllegalArgumentException.class, () -> view.setRows(Arrays.asList(ALPHA, duplicateAlpha)));
        assertThrows(IllegalArgumentException.class,
                () -> view.setCriterion(ColumnCriterion.hiding("FormID", Arrays.asList("1"))));
        assertThrows(IllegalArgumentException.class,
                () -> view.setSortOrder(Arrays.asList(SortKey.ascending("FormID"))));
        assertThrows(IllegalArgumentException.class,
                () -> new FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity>(
                        Arrays.asList(NAME, FilterColumn.of("Name", NpcMorphAssignmentSnapshot::getRace)),
                        ProjectIdentities::npcMorphAssignment));
        assertThrows(NullPointerException.class, () -> view.setRows(Arrays.asList(ALPHA, null)));
        assertTrue(view.visibleSet().isEmpty());
    }

    /** A null cell value filters and sorts as the empty string. */
    @Test
    void nullCellValuesAreTreatedAsEmptyStrings() {
        FilterColumn<String> column = FilterColumn.of("Value", value -> value.isEmpty() ? null : value);
        FilteredView<String, NameIdentity> view = new FilteredView<>(Arrays.asList(column), NameIdentity::of);
        view.setRows(Arrays.asList("b", "", "a"));

        assertEquals("", column.cellValueOf(""));
        view.setSortOrder(Arrays.asList(SortKey.ascending("Value")));
        assertEquals(Arrays.asList("", "a", "b"), view.visibleSet().getRows());
        view.setCriterion(ColumnCriterion.hiding("Value", Arrays.asList("")));
        assertEquals(Arrays.asList("a", "b"), view.visibleSet().getRows());
    }

    /**
     * Slider Presets and Custom Morph Targets are identified by case-insensitive
     * name; NPC Morph Assignments and NPC Database entries by plugin plus editor ID.
     */
    @Test
    void projectIdentitiesFollowTheGlossary() {
        SliderPresetSnapshot preset = new SliderPresetSnapshot("CBBE Curvy", false,
                Collections.emptyList());
        CustomMorphTargetSnapshot target = new CustomMorphTargetSnapshot("All|Female", Collections.emptyList());

        assertEquals(NameIdentity.of("cbbe curvy"), ProjectIdentities.sliderPreset(preset));
        assertEquals(NameIdentity.of("cbbe curvy").hashCode(), ProjectIdentities.sliderPreset(preset).hashCode());
        assertEquals(NameIdentity.of("ALL|FEMALE"), ProjectIdentities.customMorphTarget(target));
        assertFalse(NameIdentity.of("CBBE Curvy").equals(NameIdentity.of("CBBE Slim")));
        assertEquals("CBBE Curvy", NameIdentity.of("CBBE Curvy").getName());
        assertEquals(new NpcMorphAssignmentIdentity("skyrim.esm", "ALPHAEDITOR"),
                ProjectIdentities.npcMorphAssignment(ALPHA));
        assertThrows(NullPointerException.class, () -> NameIdentity.of(null));

        FilteredView<SliderPresetSnapshot, NameIdentity> presets = new FilteredView<>(
                Arrays.asList(FilterColumn.of("Name", SliderPresetSnapshot::getName)),
                ProjectIdentities::sliderPreset);
        presets.setRows(Arrays.asList(preset));
        assertTrue(presets.select(NameIdentity.of("CBBE CURVY")));
    }

    /**
     * The seam is JavaFX-independent: no public constructor, method, or field of
     * any seam type mentions a JavaFX, vendored ControlsFX, or JDK-internal type.
     */
    @Test
    void seamExposesNoJavaFxOrVendoredFilteringTypes() {
        List<Class<?>> seam = Arrays.asList(FilterColumn.class, ColumnCriterion.class, SortKey.class,
                SortDirection.class, VisibleSet.class, FilteredView.class, NameIdentity.class,
                ProjectIdentities.class, NpcTableColumns.class, VisibleScopeCommands.class);
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : seam) {
            assertTrue(Modifier.isPublic(type.getModifiers()), type.getName());
            for (Constructor<?> constructor : type.getConstructors())
                collectForeignTypes(constructor.toGenericString(), offenders);
            for (Method method : type.getMethods()) {
                if (method.getDeclaringClass() == Object.class)
                    continue;
                collectForeignTypes(method.toGenericString(), offenders);
            }
            for (java.lang.reflect.Field field : type.getFields())
                collectForeignTypes(field.toGenericString(), offenders);
            for (Type parent : type.getGenericInterfaces())
                collectForeignTypes(parent.getTypeName(), offenders);
            collectForeignTypes(type.getGenericSuperclass() == null ? "" : type.getGenericSuperclass().getTypeName(),
                    offenders);
        }
        assertEquals(Collections.emptyList(), offenders);
    }

    private static void collectForeignTypes(String signature, List<String> offenders) {
        for (String forbidden : Arrays.asList("javafx.", "controlsfx", "com.sun."))
            if (signature.contains(forbidden))
                offenders.add(signature);
    }

    private static FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> npcView() {
        return new FilteredView<>(Arrays.asList(NAME, MASTER, RACE), ProjectIdentities::npcMorphAssignment);
    }

    private static NpcMorphAssignmentSnapshot npc(String displayName, String pluginName, String editorId,
            String race) {
        return new NpcMorphAssignmentSnapshot(displayName, pluginName, editorId, race, "1A696",
                Collections.<String>emptyList());
    }

    private static NpcMorphAssignmentIdentity identityOf(NpcMorphAssignmentSnapshot npc) {
        return ProjectIdentities.npcMorphAssignment(npc);
    }

    private static List<NpcMorphAssignmentIdentity> identities(NpcMorphAssignmentSnapshot... npcs) {
        List<NpcMorphAssignmentIdentity> identities = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot npc : npcs)
            identities.add(identityOf(npc));
        return identities;
    }
}
