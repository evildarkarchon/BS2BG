package com.asdasfa.jbs2bg.workbench.npcdatabase;

import static com.asdasfa.jbs2bg.data.SettingsTestSupport.emptySettings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.testing.JavaFxFreeTypeGraph;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

/** Verifies the window-scoped source catalog through its intents and immutable feature frames. */
class NpcDatabaseFeatureTest {

    private static final Instant START = Instant.parse("2026-10-13T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    /** Creates a catalog over an empty Project whose type-ahead clock never moves. */
    private static NpcDatabaseFeature feature() {
        return feature(Clock.fixed(START, ZoneOffset.UTC));
    }

    /** Creates a catalog over an empty Project with the given type-ahead clock. */
    private static NpcDatabaseFeature feature(Clock clock) {
        return new NpcDatabaseFeature(new WorkbenchProjectFlow("BS2BG Preview",
                ProjectSessions.create(emptySettings())), clock);
    }

    /** Removing a source reveals a later duplicate without changing its import position. */
    @Test
    void removeSourceRevealsFirstRemainingDuplicate() throws Exception {
        Path first = temporaryDirectory.resolve("first.txt");
        Path second = temporaryDirectory.resolve("second.txt");
        Files.writeString(first, "Master.esm | Amber | Shared | NordRace | 00000A\n");
        Files.writeString(second, "master.esm | Shadow | shared | NordRace | 00000B\n"
                + "Update.esm | Zeta | Unique | NordRace | 00000C\n");
        NpcDatabaseFeature feature = feature();

        feature.commitSource(NpcDatabaseSourceReader.read(first));
        feature.commitSource(NpcDatabaseSourceReader.read(second));

        assertEquals(List.of("Amber", "Zeta"), feature.frame().rows().stream()
                .map(row -> row.getName()).toList());
        assertEquals(List.of(1, 2), feature.frame().sources().stream()
                .map(NpcDatabaseFeature.Source::rowCount).toList());

        assertTrue(feature.dispatch(new NpcDatabaseFeature.SelectSource(Optional.of(first))).accepted());
        NpcDatabaseFeature.ConfirmRemoveSource confirmation = assertInstanceOf(
                NpcDatabaseFeature.ConfirmRemoveSource.class,
                feature.dispatch(new NpcDatabaseFeature.RequestRemoveSource()).effect().orElseThrow());
        assertEquals(first.toAbsolutePath().normalize(), confirmation.source());
        feature.respond(confirmation.token(), true);

        assertEquals(List.of("Shadow", "Zeta"), feature.frame().rows().stream()
                .map(row -> row.getName()).toList());
        assertEquals(List.of(second.toAbsolutePath().normalize()), feature.frame().sources().stream()
                .map(NpcDatabaseFeature.Source::path).toList());
        assertTrue(feature.frame().selectedSource().isEmpty());
        assertTrue(feature.frame().diagnostics().isEmpty());
    }

    /** A declined or stale removal confirmation leaves every source in place. */
    @Test
    void declinedOrStaleRemovalKeepsTheSource() throws Exception {
        Path source = temporaryDirectory.resolve("catalog.txt");
        Files.writeString(source, "Master.esm | Amber | First | NordRace | 00000A\n");
        NpcDatabaseFeature feature = feature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));

        assertTrue(feature.dispatch(new NpcDatabaseFeature.RequestRemoveSource()).effect().isEmpty());
        feature.dispatch(new NpcDatabaseFeature.SelectSource(Optional.of(source)));
        NpcDatabaseFeature.ConfirmRemoveSource declined = (NpcDatabaseFeature.ConfirmRemoveSource)
                feature.dispatch(new NpcDatabaseFeature.RequestRemoveSource()).effect().orElseThrow();
        feature.respond(declined.token(), false);
        assertFalse(feature.respond(declined.token(), true).accepted());

        assertEquals(1, feature.frame().sources().size());
    }

    /** Column filters and sorting keep selection by NPC identity until the row is hidden. */
    @Test
    void columnViewKeepsSelectionAcrossSortingAndDropsHiddenSelection() throws Exception {
        Path source = temporaryDirectory.resolve("catalog.txt");
        Files.writeString(source, "Master.esm | Amber | First | NordRace | 00000A\n"
                + "Update.esm | Zeta | Second | BretonRace | 00000B\n");
        NpcDatabaseFeature feature = feature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        NpcMorphAssignmentIdentity selected = new NpcMorphAssignmentIdentity("update.esm", "second");

        assertTrue(feature.dispatch(new NpcDatabaseFeature.SelectRow(selected)).accepted());
        feature.dispatch(new NpcDatabaseFeature.ChangeSort(List.of(SortKey.descending("Name"))));

        assertEquals(List.of("Zeta", "Amber"), feature.frame().visibleRows().stream()
                .map(row -> row.getName()).toList());
        assertEquals("Zeta", feature.frame().selectedRow().orElseThrow().getName());
        assertEquals(source.toAbsolutePath().normalize(), feature.frame()
                .sourceOf(feature.frame().selectedRow().orElseThrow()).orElseThrow());

        feature.dispatch(new NpcDatabaseFeature.SetCriterion(ColumnCriterion.hiding("Race", List.of("BretonRace"))));

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
        NpcDatabaseFeature feature = feature();
        feature.commitSource(NpcDatabaseSourceReader.read(first));
        feature.commitSource(NpcDatabaseSourceReader.read(second));
        feature.dispatch(new NpcDatabaseFeature.SetCriterion(ColumnCriterion.hiding("Name", List.of("Zeta"))));
        NpcDatabaseFeature.ConfirmClearVisible frozen = (NpcDatabaseFeature.ConfirmClearVisible)
                feature.dispatch(new NpcDatabaseFeature.RequestClearVisible()).effect().orElseThrow();

        feature.dispatch(new NpcDatabaseFeature.ClearAllCriteria());
        feature.respond(frozen.token(), true);

        assertEquals(2, frozen.identities().size());
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
        NpcDatabaseFeature feature = feature();
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
        NpcDatabaseFeature feature = feature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        assertTrue(feature.dispatch(new NpcDatabaseFeature.SelectSource(Optional.of(source))).accepted());
        assertTrue(feature.dispatch(new NpcDatabaseFeature.SelectRow(
                new NpcMorphAssignmentIdentity("Master.esm", "First"))).accepted());

        feature.dispatch(new NpcDatabaseFeature.ClearAllSelections());

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
        MutableClock clock = new MutableClock(START);
        NpcDatabaseFeature feature = feature(clock);
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        feature.dispatch(new NpcDatabaseFeature.SetCriterion(ColumnCriterion.hiding("Name", List.of("Amber"))));
        feature.dispatch(new NpcDatabaseFeature.ChangeSort(List.of(SortKey.descending("Name"))));

        assertEquals("Ash", typeAhead(feature, 'A'));
        clock.advance(Duration.ofMillis(100));
        assertEquals("Alina", typeAhead(feature, 'a'));
        clock.advance(Duration.ofMillis(100));
        assertEquals("Ash", typeAhead(feature, 'a'));
    }

    /** A later key starts a fresh prefix instead of cycling from an old selected match. */
    @Test
    void typeAheadResetsAfterSevenHundredFiftyMilliseconds() throws Exception {
        Path source = temporaryDirectory.resolve("names.txt");
        Files.writeString(source, "Master.esm | Alina | First | NordRace | 000001\n"
                + "Master.esm | Amber | Second | NordRace | 000002\n");
        MutableClock clock = new MutableClock(START);
        NpcDatabaseFeature feature = feature(clock);
        feature.commitSource(NpcDatabaseSourceReader.read(source));

        assertEquals("Alina", typeAhead(feature, 'a'));
        clock.advance(Duration.ofMillis(100));
        assertEquals("Amber", typeAhead(feature, 'a'));
        clock.advance(Duration.ofMillis(800).plusNanos(1));
        assertEquals("Alina", typeAhead(feature, 'a'));
    }

    /**
     * The prefix expires only once the injected clock moves more than 750 ms past the previous key: at exactly 750 ms
     * a letter still extends the prefix, and one nanosecond later it starts a new one.
     */
    @Test
    void typeAheadPrefixExpiresOnlyAfterTheTimeoutOnTheInjectedClock() throws Exception {
        Path source = temporaryDirectory.resolve("names.txt");
        Files.writeString(source, "Master.esm | Alina | First | NordRace | 000001\n"
                + "Master.esm | Amber | Second | NordRace | 000002\n"
                + "Master.esm | Beryl | Third | NordRace | 000003\n");
        MutableClock clock = new MutableClock(START);
        NpcDatabaseFeature feature = feature(clock);
        feature.commitSource(NpcDatabaseSourceReader.read(source));

        assertEquals("Alina", typeAhead(feature, 'a'));
        clock.advance(Duration.ofMillis(750));
        assertEquals("Amber", typeAhead(feature, 'm'));
        clock.advance(Duration.ofMillis(750).plusNanos(1));
        assertEquals("Beryl", typeAhead(feature, 'b'));
    }

    /**
     * Under a fixed clock no keystroke ever expires the prefix, so the same keys always select the same rows: letters
     * extend the prefix, a repeated single letter cycles, and an unmatched extension restarts from that letter.
     */
    @Test
    void typeAheadIsDeterministicUnderAFixedClock() throws Exception {
        Path source = temporaryDirectory.resolve("names.txt");
        Files.writeString(source, "Master.esm | Alina | First | NordRace | 000001\n"
                + "Master.esm | Amber | Second | NordRace | 000002\n"
                + "Master.esm | Beryl | Third | NordRace | 000003\n"
                + "Master.esm | Bran | Fourth | NordRace | 000004\n");
        List<List<String>> runs = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            NpcDatabaseFeature feature = feature();
            feature.commitSource(NpcDatabaseSourceReader.read(source));
            List<String> selections = new ArrayList<>();
            for (char typed : "ambbb".toCharArray())
                selections.add(typeAhead(feature, typed));
            runs.add(selections);
        }

        assertEquals(List.of("Alina", "Amber", "Amber", "Beryl", "Bran"), runs.getFirst());
        assertEquals(runs.getFirst(), runs.getLast());
    }

    /**
     * Project promotion and catalog removal wait for a blocking job; selection, filtering, sorting, type-ahead and
     * visit state stay local browsing.
     */
    @Test
    void declaresPromotionAndCatalogRemovalAsMutationsAndBrowsingAsLocal() {
        NpcDatabaseFeature feature = feature();
        NpcMorphAssignmentIdentity identity = new NpcMorphAssignmentIdentity("Master.esm", "First");
        for (NpcDatabaseFeature.Intent mutation : List.<NpcDatabaseFeature.Intent>of(
                new NpcDatabaseFeature.AddSelected(),
                new NpcDatabaseFeature.AddAllVisible(),
                new NpcDatabaseFeature.RequestRemoveSource(),
                new NpcDatabaseFeature.RequestClearVisible()))
            assertTrue(feature.isMutation(mutation), mutation.toString());
        for (NpcDatabaseFeature.Intent local : List.<NpcDatabaseFeature.Intent>of(
                new NpcDatabaseFeature.SelectSource(Optional.empty()),
                new NpcDatabaseFeature.SelectRow(identity),
                new NpcDatabaseFeature.ClearSelection(),
                new NpcDatabaseFeature.ClearAllSelections(),
                new NpcDatabaseFeature.TypeAhead('a'),
                new NpcDatabaseFeature.SetCriterion(ColumnCriterion.hiding("Name", List.of("Amber"))),
                new NpcDatabaseFeature.ClearAllCriteria(),
                new NpcDatabaseFeature.ChangeSort(List.of()),
                new NpcDatabaseFeature.SetAssignRandom(true),
                new NpcDatabaseFeature.DismissPromotionReport(),
                new NpcDatabaseFeature.LeaveArea(),
                new NpcDatabaseFeature.BackToMorphs()))
            assertFalse(feature.isMutation(local), local.toString());
    }

    /**
     * The feature stays JavaFX-free through its intents, effects and the Project flow it promotes through, so an
     * Area can drive it and tests can run it without a toolkit.
     */
    @Test
    void featureReachesNoJavaFxType() {
        JavaFxFreeTypeGraph.assertJavaFxFree(NpcDatabaseFeature.class);
    }

    /** Types one character and returns the selected row's name. */
    private static String typeAhead(NpcDatabaseFeature feature, char typed) {
        return feature.dispatch(new NpcDatabaseFeature.TypeAhead(typed)).frame().selectedRow()
                .map(NPC::getName).orElseThrow();
    }

    /** Minimal deterministic clock used to cross the type-ahead timeout without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now;

        /** Creates the clock at one stable instant. */
        private MutableClock(Instant now) {
            this.now = now;
        }

        /** Advances the instant monotonically for one interaction step. */
        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        /** {@inheritDoc} */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** {@inheritDoc} */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public Instant instant() {
            return now;
        }
    }
}
