package com.asdasfa.jbs2bg.project;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class NpcMorphAssignmentAuthoringTest {

    /** Manual authoring uses the same persisted Form ID and identity as a reopened Project. */
    @Test
    void createsNpcWithNormalizedFormIdAndStableIdentity() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();

        ProjectOutcome created = session.apply(NpcMorphAssignmentEdits.create(
                "  ", " Skyrim.esm ", " HousecarlWhiterun ", " NordRace ", "000A2C94"));
        ProjectOutcome duplicate = session.apply(NpcMorphAssignmentEdits.create(
                "Another Name", "SKYRIM.ESM", "housecarlwhiterun", "BretonRace", "FFFFFF"));

        assertInstanceOf(ChangedOutcome.class, created);
        NpcMorphAssignmentSnapshot npc = created.getSnapshot().getNpcMorphAssignments().getFirst();
        assertEquals("Unnamed (HousecarlWhiterun)", npc.getDisplayName());
        assertEquals("Skyrim.esm", npc.getPluginName());
        assertEquals("HousecarlWhiterun", npc.getEditorId());
        assertEquals("NordRace", npc.getRace());
        assertEquals("A2C94", npc.getFormId());
        assertEquals(List.of(), npc.getSliderPresetNames());
        assertInstanceOf(RejectedOutcome.class, duplicate);
        assertSame(created.getSnapshot(), duplicate.getSnapshot());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE,
                duplicate.getDiagnostics().getFirst().getCode());
    }

    /** Incomplete or malformed manual fields are rejected without publishing a partial NPC. */
    @Test
    void rejectsInvalidNpcAuthoringFieldsWithoutChangingProject() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        ProjectSnapshot unchanged = session.getSnapshot();

        ProjectOutcome required = session.apply(NpcMorphAssignmentEdits.create(
                "Lydia", " ", "HousecarlWhiterun", "NordRace", "000A2C94"));
        ProjectOutcome malformed = session.apply(NpcMorphAssignmentEdits.create(
                "Lydia", "Skyrim.esm", "HousecarlWhiterun", "NordRace", "not-hex"));

        assertInstanceOf(RejectedOutcome.class, required);
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_REQUIRED,
                required.getDiagnostics().getFirst().getCode());
        assertInstanceOf(RejectedOutcome.class, malformed);
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_FORM_ID_INVALID,
                malformed.getDiagnostics().getFirst().getCode());
        assertSame(unchanged, required.getSnapshot());
        assertSame(unchanged, malformed.getSnapshot());
        assertEquals(List.of(), session.getSnapshot().getNpcMorphAssignments());
    }
}
