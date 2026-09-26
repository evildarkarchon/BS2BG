package com.asdasfa.jbs2bg.workbench.npcdatabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;
import com.asdasfa.jbs2bg.filtering.ColumnCriterion;

/** Verifies NPC Database promotion through the public catalog and Project seams. */
class NpcDatabasePromotionTest {

    @TempDir
    Path temporaryDirectory;

    /** Add copies the selected catalog row into the Project and leaves catalog selection intact. */
    @Test
    void addSelectedCreatesIndependentAssignmentAndKeepsDatabaseSelection() throws Exception {
        Path source = temporaryDirectory.resolve("catalog.txt");
        Files.writeString(source, "Master.esm | Amber | First | NordRace | 00000A\n");
        ProjectSession session = ProjectSessions.create();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        NpcMorphAssignmentIdentity identity = new NpcMorphAssignmentIdentity("master.esm", "first");
        assertTrue(feature.selectRow(identity));

        NpcDatabaseFeature.PromotionReport report = feature.promoteSelected(flow);

        assertEquals(1, report.addedCount());
        assertEquals(0, report.duplicateCount());
        assertEquals(identity, feature.frame().selectedRow()
                .map(row -> new NpcMorphAssignmentIdentity(row.getMod(), row.getEditorId())).orElseThrow());
        assertEquals(1, feature.frame().rows().size());
        assertEquals("Amber", session.getSnapshot().getNpcMorphAssignments().getFirst().getDisplayName());
        assertEquals(1, feature.promoteSelected(flow).duplicateCount());
        assertEquals(identity, feature.consumeReturnAssignment().orElseThrow());
        assertFalse(feature.consumeReturnAssignment().isPresent());
        Path saved = temporaryDirectory.resolve("promoted.jbs2bg");
        assertInstanceOf(ChangedOutcome.class, session.saveAs(saved));
        ProjectSession reopened = ProjectSessions.create();
        assertInstanceOf(ChangedOutcome.class, reopened.open(saved));
        assertEquals("Amber", reopened.getSnapshot().getNpcMorphAssignments().getFirst().getDisplayName());
        assertEquals("A", reopened.getSnapshot().getNpcMorphAssignments().getFirst().getFormId());
    }

    /** Add All reports every duplicate and invalid visible entry while committing valid independent rows. */
    @Test
    void addAllReportsEveryDuplicateAndRejectionInFilteredOrder() throws Exception {
        Path source = temporaryDirectory.resolve("mixed.txt");
        Files.writeString(source, "Master.esm | Existing | Existing01 | NordRace | 00000A\n"
                + "Master.esm | Good | Good01 | NordRace | 00000B\n"
                + " | Blank plugin | Invalid01 | NordRace | 00000C\n"
                + "Master.esm | Bad form | Invalid02 | NordRace | XYZ\n"
                + "Master.esm | Hidden | Hidden01 | NordRace | 00000D\n");
        ProjectSession session = ProjectSessions.create();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        flow.apply(NpcMorphAssignmentEdits.create("Existing", "Master.esm", "Existing01", "NordRace", "A"));
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        feature.setCriterion(ColumnCriterion.hiding("Name", List.of("Hidden")));
        NpcMorphAssignmentIdentity selected = new NpcMorphAssignmentIdentity("Master.esm", "Existing01");
        assertTrue(feature.selectRow(selected));

        NpcDatabaseFeature.PromotionReport report = feature.promoteVisible(flow);

        assertEquals(4, report.entries().size());
        assertEquals(1, report.addedCount());
        assertEquals(1, report.duplicateCount());
        assertEquals(2, report.rejectedCount());
        assertEquals(List.of(NpcDatabaseFeature.PromotionStatus.DUPLICATE,
                        NpcDatabaseFeature.PromotionStatus.ADDED,
                        NpcDatabaseFeature.PromotionStatus.REJECTED,
                        NpcDatabaseFeature.PromotionStatus.REJECTED),
                report.entries().stream().map(NpcDatabaseFeature.PromotionEntry::status).toList());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_REQUIRED,
                report.entries().get(2).diagnostics().getFirst().getCode());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_FORM_ID_INVALID,
                report.entries().get(3).diagnostics().getFirst().getCode());
        assertEquals(2, session.getSnapshot().getNpcMorphAssignments().size());
        assertEquals(selected, feature.frame().selectedRow()
                .map(row -> new NpcMorphAssignmentIdentity(row.getMod(), row.getEditorId())).orElseThrow());
        assertEquals(4, feature.frame().visibleRows().size());
    }

    /** Add All publishes one Project frame while retaining ordered outcomes for every visible row. */
    @Test
    void addAllPublishesOnceForMultipleAddsAndRejections() throws Exception {
        Path source = temporaryDirectory.resolve("many.txt");
        Files.writeString(source, "Master.esm | Alpha | Alpha01 | NordRace | 00000A\n"
                + "Master.esm | Broken | Broken01 | NordRace | XYZ\n"
                + "Master.esm | Beta | Beta01 | NordRace | 00000B\n"
                + "Master.esm | Gamma | Gamma01 | NordRace | 00000C\n");
        ProjectSession session = ProjectSessions.create();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        long beforeSequence = flow.frame().sequence();

        NpcDatabaseFeature.PromotionReport report = feature.promoteVisible(flow);

        assertEquals(beforeSequence + 1, flow.frame().sequence());
        assertEquals(List.of(NpcDatabaseFeature.PromotionStatus.ADDED,
                        NpcDatabaseFeature.PromotionStatus.REJECTED,
                        NpcDatabaseFeature.PromotionStatus.ADDED,
                        NpcDatabaseFeature.PromotionStatus.ADDED),
                report.entries().stream().map(NpcDatabaseFeature.PromotionEntry::status).toList());
        assertEquals(3, session.getSnapshot().getNpcMorphAssignments().size());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_FORM_ID_INVALID,
                flow.frame().diagnostics().getFirst().getCode());
    }

    /** Assign random copies one Project Slider Preset name into the new assignment when enabled. */
    @Test
    void addSelectedCanAssignOneProjectSliderPreset() throws Exception {
        Path source = temporaryDirectory.resolve("random.txt");
        Files.writeString(source, "Master.esm | Amber | First | NordRace | 00000A\n");
        ProjectSession session = ProjectSessions.create();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        flow.apply(SliderPresetEdits.create("Shape"));
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        feature.selectRow(new NpcMorphAssignmentIdentity("Master.esm", "First"));
        feature.setAssignRandom(true);

        NpcDatabaseFeature.PromotionReport report = feature.promoteSelected(flow);

        assertEquals(1, report.addedCount());
        assertTrue(feature.frame().assignRandom());
        assertEquals(List.of("Shape"), session.getSnapshot().getNpcMorphAssignments().getFirst()
                .getSliderPresetNames());
    }

    /** A malformed source prefix cannot become valid merely because the catalog displays a shortened Form ID. */
    @Test
    void promotionRejectsNonHexSourceFormIdBeforeCatalogNormalization() throws Exception {
        Path source = temporaryDirectory.resolve("invalid-form.txt");
        Files.writeString(source, "Master.esm | Broken | Bad01 | NordRace | ZZ00000A\n");
        ProjectSession session = ProjectSessions.create();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        feature.commitSource(NpcDatabaseSourceReader.read(source));
        NpcMorphAssignmentIdentity identity = new NpcMorphAssignmentIdentity("Master.esm", "Bad01");
        assertTrue(feature.selectRow(identity));

        NpcDatabaseFeature.PromotionReport report = feature.promoteSelected(flow);

        assertEquals(0, report.addedCount());
        assertEquals(1, report.rejectedCount());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_FORM_ID_INVALID,
                report.entries().getFirst().diagnostics().getFirst().getCode());
        assertTrue(session.getSnapshot().getNpcMorphAssignments().isEmpty());
        assertEquals("A", feature.frame().selectedRow().orElseThrow().getFormId());
    }
}
