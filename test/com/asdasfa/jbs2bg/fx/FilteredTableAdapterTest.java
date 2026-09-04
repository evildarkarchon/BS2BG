package com.asdasfa.jbs2bg.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.NpcTableColumns;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.VisibleSet;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Characterizes the public-JavaFX adapter over the application-owned filtering
 * seam: the table shows exactly the logical visible set, in its order, and
 * every table-side gesture (sort, selection, column filter) is expressed
 * through {@link com.asdasfa.jbs2bg.filtering.FilteredView} rather than
 * through JavaFX transformation lists or skin internals.
 */
class FilteredTableAdapterTest {

    private static final NpcMorphAssignmentSnapshot ALPHA = npc("Alpha", "Skyrim.esm", "AlphaEditor", "NordRace");
    private static final NpcMorphAssignmentSnapshot BETA = npc("Beta", "Skyrim.esm", "BetaEditor", "BretonRace");
    private static final NpcMorphAssignmentSnapshot GAMMA = npc("Gamma", "Dawnguard.esm", "GammaEditor", "NordRace");
    private static final NpcMorphAssignmentSnapshot DELTA = npc("Delta", "Dawnguard.esm", "DeltaEditor",
            "BretonRace");

    private static TableColumn<NpcMorphAssignmentSnapshot, String> column(String header) {
        return new TableColumn<>(header);
    }

    private static NpcMorphAssignmentSnapshot npc(String displayName, String pluginName, String editorId,
                                                  String race) {
        return new NpcMorphAssignmentSnapshot(displayName, pluginName, editorId, race, "1A696",
                Collections.<String>emptyList());
    }

    private static List<String> values(List<ColumnFilterMenu.Choice> choices) {
        List<String> values = new ArrayList<>();
        for (ColumnFilterMenu.Choice choice : choices)
            values.add(choice.getValue());
        return values;
    }

    private static ColumnFilterMenu.Choice choice(ColumnFilterMenu<?> menu, String value) {
        for (ColumnFilterMenu.Choice choice : menu.getChoices())
            if (choice.getValue().equals(value))
                return choice;
        throw new AssertionError("no choice " + value + " in " + values(menu.getChoices()));
    }

    private static NpcMorphAssignmentIdentity identityOf(NpcMorphAssignmentSnapshot npc) {
        return ProjectIdentities.npcMorphAssignment(npc);
    }

    private static List<NpcMorphAssignmentIdentity> identities(NpcMorphAssignmentSnapshot... npcs) {
        List<NpcMorphAssignmentIdentity> identities = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot npc : npcs)
            identities.add(ProjectIdentities.npcMorphAssignment(npc));
        return identities;
    }

    /**
     * The table renders the source rows in source order and the frozen visible set matches.
     */
    @Test
    void sourceRowsRenderIntoTheTableInSourceOrder() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            fixture.source.setAll(ALPHA, BETA, GAMMA, DELTA);

            assertEquals(Arrays.asList(ALPHA, BETA, GAMMA, DELTA), fixture.table.getItems());
            assertEquals(identities(ALPHA, BETA, GAMMA, DELTA), fixture.adapter.visibleSet().getIdentities());

            fixture.source.setAll(DELTA, ALPHA);
            assertEquals(Arrays.asList(DELTA, ALPHA), fixture.table.getItems());
            assertEquals(Arrays.asList(DELTA, ALPHA), fixture.adapter.visibleSet().getRows());
        });
    }

    /**
     * Sorting is expressed through the table's own sort order and sort types;
     * the view decides the order, membership never changes, and clearing the
     * sort order restores source order. The sort survives row replacement.
     */
    @Test
    void sortingThroughTheTableSortOrderReordersVisibleRowsOnly() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            fixture.source.setAll(ALPHA, BETA, GAMMA, DELTA);

            fixture.name.setSortType(TableColumn.SortType.DESCENDING);
            fixture.table.getSortOrder().setAll(Arrays.asList(fixture.name));
            assertEquals(Arrays.asList(GAMMA, DELTA, BETA, ALPHA), fixture.table.getItems());
            assertEquals(Arrays.asList(GAMMA, DELTA, BETA, ALPHA), fixture.adapter.visibleSet().getRows());

            fixture.race.setSortType(TableColumn.SortType.DESCENDING);
            fixture.table.getSortOrder().setAll(Arrays.asList(fixture.master, fixture.race));
            assertEquals(Arrays.asList(GAMMA, DELTA, ALPHA, BETA), fixture.table.getItems());

            // Changing a sort type of a column already in the sort order re-sorts too.
            fixture.race.setSortType(TableColumn.SortType.ASCENDING);
            assertEquals(Arrays.asList(DELTA, GAMMA, BETA, ALPHA), fixture.table.getItems());

            fixture.table.getSortOrder().setAll(Arrays.asList(fixture.name));
            fixture.name.setSortType(TableColumn.SortType.ASCENDING);
            fixture.source.setAll(DELTA, ALPHA, BETA);
            assertEquals(Arrays.asList(ALPHA, BETA, DELTA), fixture.table.getItems());

            fixture.table.getSortOrder().clear();
            assertEquals(Arrays.asList(DELTA, ALPHA, BETA), fixture.table.getItems());
        });
    }

    /**
     * The selection is keyed by logical identity: it follows a replacement
     * snapshot instance across row replacement and sorting, drops when its row
     * is gone, and is driven both by table gestures and by the adapter API.
     */
    @Test
    void selectionIsLogicalAndSurvivesRowReplacement() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            fixture.source.setAll(ALPHA, BETA, GAMMA, DELTA);

            fixture.table.getSelectionModel().select(BETA);
            assertEquals(Optional.of(identityOf(BETA)), fixture.adapter.getSelection());

            NpcMorphAssignmentSnapshot renamedBeta = npc("Beta Renamed", "Skyrim.esm", "BetaEditor", "BretonRace");
            fixture.source.setAll(ALPHA, renamedBeta, GAMMA);
            assertSame(renamedBeta, fixture.table.getSelectionModel().getSelectedItem());
            assertEquals(Optional.of(identityOf(BETA)), fixture.adapter.getSelection());

            fixture.name.setSortType(TableColumn.SortType.DESCENDING);
            fixture.table.getSortOrder().setAll(Arrays.asList(fixture.name));
            assertSame(renamedBeta, fixture.table.getSelectionModel().getSelectedItem());
            assertEquals(1, fixture.table.getSelectionModel().getSelectedIndex());

            fixture.source.setAll(ALPHA, GAMMA);
            assertNull(fixture.table.getSelectionModel().getSelectedItem());
            assertEquals(Optional.empty(), fixture.adapter.getSelection());

            assertTrue(fixture.adapter.select(identityOf(GAMMA)));
            assertSame(GAMMA, fixture.table.getSelectionModel().getSelectedItem());
            assertFalse(fixture.adapter.select(identityOf(BETA)));
            assertSame(GAMMA, fixture.table.getSelectionModel().getSelectedItem());

            fixture.table.getSelectionModel().clearSelection();
            assertEquals(Optional.empty(), fixture.adapter.getSelection());

            assertTrue(fixture.adapter.select(identityOf(ALPHA)));
            fixture.adapter.clearSelection();
            assertNull(fixture.table.getSelectionModel().getSelectedItem());
            assertEquals(Optional.empty(), fixture.adapter.getSelection());
        });
    }

    /**
     * Each column's filter checklist offers the distinct cell values of the
     * source rows; unchecking values and applying hides exactly those values,
     * columns combine with AND, the criterion is retained verbatim across row
     * replacement (unseen values stay visible), a selection hidden by a
     * criterion is dropped, and a filtered column carries a header graphic
     * until RESET ALL clears every criterion.
     */
    @Test
    void columnFilterChecklistHidesUncheckedValuesWithAndSemantics() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            fixture.source.setAll(ALPHA, BETA, GAMMA, DELTA);
            ColumnFilterMenu<NpcMorphAssignmentSnapshot> race = fixture.adapter.filterMenu(fixture.race);
            ColumnFilterMenu<NpcMorphAssignmentSnapshot> master = fixture.adapter.filterMenu(fixture.master);

            assertEquals(Arrays.asList("BretonRace", "NordRace"), values(race.getChoices()));
            assertTrue(race.getChoices().stream().allMatch(ColumnFilterMenu.Choice::isSelected));
            assertNull(fixture.race.getGraphic());

            choice(race, "NordRace").setSelected(false);
            race.apply();
            assertEquals(Arrays.asList(BETA, DELTA), fixture.table.getItems());
            assertTrue(fixture.adapter.isFiltered());
            assertNotNull(fixture.race.getGraphic());
            assertNull(fixture.master.getGraphic());

            choice(master, "Dawnguard.esm").setSelected(false);
            master.apply();
            assertEquals(Arrays.asList(BETA), fixture.table.getItems());
            assertEquals(2, fixture.adapter.getCriteria().size());

            NpcMorphAssignmentSnapshot epsilon = npc("Epsilon", "Dragonborn.esm", "EpsilonEditor", "BretonRace");
            fixture.table.getSelectionModel().select(BETA);
            fixture.source.setAll(ALPHA, BETA, GAMMA, DELTA, epsilon);
            assertEquals(Arrays.asList(BETA, epsilon), fixture.table.getItems());
            assertEquals(Arrays.asList("Dawnguard.esm", "Dragonborn.esm", "Skyrim.esm"), values(master.getChoices()));
            assertFalse(choice(master, "Dawnguard.esm").isSelected());
            assertTrue(choice(master, "Dragonborn.esm").isSelected());
            assertSame(BETA, fixture.table.getSelectionModel().getSelectedItem());

            // Hiding the selected row's value drops the selection rather than keeping a hidden selection.
            choice(race, "NordRace").setSelected(true);
            choice(race, "BretonRace").setSelected(false);
            race.apply();
            assertEquals(Arrays.asList(ALPHA), fixture.table.getItems());
            assertNull(fixture.table.getSelectionModel().getSelectedItem());
            assertEquals(Optional.empty(), fixture.adapter.getSelection());

            master.resetAll();
            assertEquals(Arrays.asList(ALPHA, BETA, GAMMA, DELTA, epsilon), fixture.table.getItems());
            assertFalse(fixture.adapter.isFiltered());
            assertTrue(fixture.adapter.getCriteria().isEmpty());
            assertNull(fixture.race.getGraphic());
            assertNull(fixture.master.getGraphic());
            assertTrue(race.getChoices().stream().allMatch(ColumnFilterMenu.Choice::isSelected));
        });
    }

    /**
     * Applying while a search is active keeps exactly the matching values and
     * hides the rest, then clears the search; NONE and ALL only toggle the
     * checklist until applied.
     */
    @Test
    void searchModeApplyKeepsOnlyMatchingValues() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            fixture.source.setAll(ALPHA, BETA, GAMMA, DELTA);
            ColumnFilterMenu<NpcMorphAssignmentSnapshot> race = fixture.adapter.filterMenu(fixture.race);

            race.setSearchText("Nord");
            assertEquals(Arrays.asList("NordRace"), values(race.getShownChoices()));
            race.apply();
            assertEquals("", race.getSearchText());
            assertEquals(Arrays.asList(ALPHA, GAMMA), fixture.table.getItems());
            assertEquals(Arrays.asList("BretonRace", "NordRace"), values(race.getShownChoices()));

            race.selectNone();
            assertEquals(Arrays.asList(ALPHA, GAMMA), fixture.table.getItems());
            race.apply();
            assertTrue(fixture.table.getItems().isEmpty());
            assertTrue(fixture.adapter.visibleSet().isEmpty());

            race.selectAll();
            race.apply();
            assertEquals(Arrays.asList(ALPHA, BETA, GAMMA, DELTA), fixture.table.getItems());
            assertFalse(fixture.adapter.isFiltered());
        });
    }

    /**
     * A frozen visible set is the complete scope of one bulk command: later
     * filter, sort, and row changes cannot reach it. Detaching the source stops
     * observing it (so it can be mutated off the JavaFX thread) and empties the
     * table; the leading column's cell text drives type-ahead search.
     */
    @Test
    void visibleSetIsFrozenAndDetachingStopsObservingTheSource() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture();
            fixture.source.setAll(ALPHA, BETA, GAMMA, DELTA);
            fixture.adapter.setCriterion(ColumnCriterion.hiding("Master", Arrays.asList("Dawnguard.esm")));
            VisibleSet<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> frozen = fixture.adapter.visibleSet();

            fixture.adapter.clearAllCriteria();
            fixture.name.setSortType(TableColumn.SortType.DESCENDING);
            fixture.table.getSortOrder().setAll(Arrays.asList(fixture.name));
            fixture.source.setAll(DELTA);
            assertEquals(identities(ALPHA, BETA), frozen.getIdentities());
            assertEquals(Arrays.asList(DELTA), fixture.table.getItems());

            assertEquals("Delta", fixture.adapter.cellTextOf(fixture.table.getColumns().getFirst(), DELTA));
            assertEquals("Dawnguard.esm", fixture.adapter.cellTextOf(fixture.master, DELTA));

            fixture.adapter.setSource(null);
            assertTrue(fixture.table.getItems().isEmpty());
            fixture.source.setAll(ALPHA, BETA);
            assertTrue(fixture.table.getItems().isEmpty());
            assertTrue(fixture.adapter.visibleSet().isEmpty());

            fixture.adapter.setSource(fixture.source);
            assertEquals(Arrays.asList(BETA, ALPHA), fixture.table.getItems());
        });
    }

    /**
     * One NPC Morph Assignment table wired the way the production controllers wire theirs.
     */
    private static final class Fixture {
        final TableView<NpcMorphAssignmentSnapshot> table = new TableView<>();
        final ObservableList<NpcMorphAssignmentSnapshot> source = FXCollections.observableArrayList();
        final TableColumn<NpcMorphAssignmentSnapshot, String> name = column("Name");
        final TableColumn<NpcMorphAssignmentSnapshot, String> master = column("Master");
        final TableColumn<NpcMorphAssignmentSnapshot, String> race = column("Race");
        final TableColumn<NpcMorphAssignmentSnapshot, String> editorId = column("EditorID");
        final TableColumn<NpcMorphAssignmentSnapshot, String> formId = column("FormID");
        final TableColumn<NpcMorphAssignmentSnapshot, String> sliderPresets = column("Slider Presets");
        final FilteredTableAdapter<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> adapter;

        Fixture() {
            name.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getDisplayName()));
            master.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getPluginName()));
            race.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getRace()));
            editorId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getEditorId()));
            formId.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getFormId()));
            sliderPresets.setCellValueFactory(
                    c -> new ReadOnlyStringWrapper(String.join("|", c.getValue().getSliderPresetNames())));
            table.getColumns().setAll(Arrays.asList(name, master, race, editorId, formId, sliderPresets));
            adapter = FilteredTableAdapter.attach(table, NpcTableColumns.npcMorphAssignments(),
                    ProjectIdentities::npcMorphAssignment);
            adapter.setSource(source);
        }
    }
}
