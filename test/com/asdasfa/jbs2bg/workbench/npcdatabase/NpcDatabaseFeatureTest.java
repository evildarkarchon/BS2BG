package com.asdasfa.jbs2bg.workbench.npcdatabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;

/** Verifies the window-scoped source catalog through immutable feature frames. */
class NpcDatabaseFeatureTest {

    @TempDir
    Path temporaryDirectory;

    /** Removing a source reveals a later duplicate without changing its import position. */
    @Test
    void removeSourceRevealsFirstRemainingDuplicate() throws Exception {
        Path first = temporaryDirectory.resolve("first.txt");
        Path second = temporaryDirectory.resolve("second.txt");
        Files.writeString(first, "Master.esm | Amber | Shared | NordRace | 00000A\n");
        Files.writeString(second, "master.esm | Shadow | shared | NordRace | 00000B\n"
                + "Update.esm | Zeta | Unique | NordRace | 00000C\n");
        NpcDatabaseFeature feature = new NpcDatabaseFeature();

        feature.commitSource(NpcDatabaseSourceReader.read(first));
        feature.commitSource(NpcDatabaseSourceReader.read(second));

        assertEquals(List.of("Amber", "Zeta"), feature.frame().rows().stream()
                .map(row -> row.getName()).toList());
        assertEquals(List.of(1, 2), feature.frame().sources().stream()
                .map(NpcDatabaseFeature.Source::rowCount).toList());

        feature.removeSource(first);

        assertEquals(List.of("Shadow", "Zeta"), feature.frame().rows().stream()
                .map(row -> row.getName()).toList());
        assertEquals(List.of(second.toAbsolutePath().normalize()), feature.frame().sources().stream()
                .map(NpcDatabaseFeature.Source::path).toList());
        assertTrue(feature.frame().diagnostics().isEmpty());
    }

    /** Column filters and sorting keep selection by NPC identity until the row is hidden. */
    @Test
    void columnViewKeepsSelectionAcrossSortingAndDropsHiddenSelection() throws Exception {
        Path source = temporaryDirectory.resolve("catalog.txt");
        Files.writeString(source, "Master.esm | Amber | First | NordRace | 00000A\n"
                + "Update.esm | Zeta | Second | BretonRace | 00000B\n");
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        NpcMorphAssignmentIdentity selected = new NpcMorphAssignmentIdentity("update.esm", "second");

        assertTrue(feature.selectRow(selected));
        feature.setSortOrder(List.of(SortKey.descending("Name")));

        assertEquals(List.of("Zeta", "Amber"), feature.frame().visibleRows().stream()
                .map(row -> row.getName()).toList());
        assertEquals("Zeta", feature.frame().selectedRow().orElseThrow().getName());
        assertEquals(source.toAbsolutePath().normalize(), feature.frame()
                .sourceOf(feature.frame().selectedRow().orElseThrow()).orElseThrow());

        feature.setCriterion(ColumnCriterion.hiding("Race", List.of("BretonRace")));

        assertEquals(List.of("Amber"), feature.frame().visibleRows().stream()
                .map(row -> row.getName()).toList());
        assertTrue(feature.frame().selectedRow().isEmpty());
    }

    /** A confirmed frozen scope removes duplicates from every source even after the visible view changes. */
    @Test
    void clearEntriesUsesFrozenIdentitiesAndCannotRevealShadowedDuplicates() throws Exception {
        Path first = temporaryDirectory.resolve("first.txt");
        Path second = temporaryDirectory.resolve("second.txt");
        Files.writeString(first, "Master.esm | Amber | Shared | NordRace | 00000A\n"
                + "Update.esm | Zeta | Keep | NordRace | 00000B\n");
        Files.writeString(second, "master.esm | Shadow | shared | NordRace | 00000C\n"
                + "Other.esm | Beta | Remove | NordRace | 00000D\n");
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(first));
        feature.commitSource(NpcDatabaseSourceReader.read(second));
        feature.setCriterion(ColumnCriterion.hiding("Name", List.of("Zeta")));
        List<NpcMorphAssignmentIdentity> frozen = feature.visibleSet().getIdentities();

        feature.clearAllCriteria();
        feature.clearEntries(frozen);

        assertEquals(List.of("Zeta"), feature.frame().rows().stream()
                .map(row -> row.getName()).toList());
        assertEquals(List.of(1, 0), feature.frame().sources().stream()
                .map(NpcDatabaseFeature.Source::rowCount).toList());
    }

    /** Rejected source evidence is visible without changing a previously imported catalog. */
    @Test
    void malformedSourceKeepsEarlierSourceAndPublishesDiagnostic() throws Exception {
        Path accepted = temporaryDirectory.resolve("accepted.txt");
        Path malformed = temporaryDirectory.resolve("malformed.txt");
        Files.writeString(accepted, "Master.esm | Amber | Keep | NordRace | 00000A\n");
        Files.writeString(malformed, "Other.esm | Staged | Lose | NordRace | 00000B\n"
                + "not an NPC row\n");
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(accepted));

        NpcDatabaseSourceReader.ReadResult rejected = NpcDatabaseSourceReader.read(malformed);
        NpcDatabaseFeature.Frame outcome = feature.commitSource(rejected);

        assertEquals(NpcDatabaseSourceReader.Status.MALFORMED, rejected.status());
        assertEquals(List.of("Amber"), outcome.rows().stream()
                .map(row -> row.getName()).toList());
        assertEquals(List.of(accepted.toAbsolutePath().normalize()), outcome.sources().stream()
                .map(NpcDatabaseFeature.Source::path).toList());
        assertEquals(malformed.toAbsolutePath().normalize(), outcome.diagnostics().getFirst().source());
        assertEquals(2, outcome.diagnostics().getFirst().line().orElseThrow());
    }

    /** New or Open can clear both source and row selection without discarding session sources. */
    @Test
    void clearAllSelectionsRetainsSourcesAndCatalogRows() throws Exception {
        Path source = temporaryDirectory.resolve("catalog.txt");
        Files.writeString(source, "Master.esm | Amber | First | NordRace | 00000A\n");
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        assertTrue(feature.selectSource(Optional.of(source)));
        assertTrue(feature.selectRow(new NpcMorphAssignmentIdentity("Master.esm", "First")));

        feature.clearAllSelections();

        assertTrue(feature.frame().selectedSource().isEmpty());
        assertTrue(feature.frame().selectedRow().isEmpty());
        assertEquals(List.of("Amber"), feature.frame().rows().stream()
                .map(row -> row.getName()).toList());
        assertEquals(1, feature.frame().sources().size());
    }

    /** Repeated letters cycle only matching rows in the current filtered and sorted view. */
    @Test
    void typeAheadCyclesVisibleMatchesInSortedOrder() throws Exception {
        Path source = temporaryDirectory.resolve("names.txt");
        Files.writeString(source, "Master.esm | Alina | First | NordRace | 000001\n"
                + "Master.esm | Amber | Second | NordRace | 000002\n"
                + "Master.esm | Ash | Third | NordRace | 000003\n"
                + "Master.esm | Beryl | Fourth | NordRace | 000004\n");
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        feature.setCriterion(ColumnCriterion.hiding("Name", List.of("Amber")));
        feature.setSortOrder(List.of(SortKey.descending("Name")));

        assertEquals("Ash", feature.typeAhead('A', 0L).selectedRow().orElseThrow().getName());
        assertEquals("Alina", feature.typeAhead('a', 100_000_000L).selectedRow().orElseThrow().getName());
        assertEquals("Ash", feature.typeAhead('a', 200_000_000L).selectedRow().orElseThrow().getName());
    }

    /** A later key starts a fresh prefix instead of cycling from an old selected match. */
    @Test
    void typeAheadResetsAfterSevenHundredFiftyMilliseconds() throws Exception {
        Path source = temporaryDirectory.resolve("names.txt");
        Files.writeString(source, "Master.esm | Alina | First | NordRace | 000001\n"
                + "Master.esm | Amber | Second | NordRace | 000002\n");
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));

        assertEquals("Alina", feature.typeAhead('a', 0L).selectedRow().orElseThrow().getName());
        assertEquals("Amber", feature.typeAhead('a', 100_000_000L).selectedRow().orElseThrow().getName());
        assertEquals("Alina", feature.typeAhead('a', 900_000_001L).selectedRow().orElseThrow().getName());
    }
}
