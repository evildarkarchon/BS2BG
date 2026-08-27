package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.CustomMorphTarget;
import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectLifecycleStatus;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.project.SourceLocation;
import com.asdasfa.jbs2bg.project.UnchangedOutcome;

class ProjectPresentationTest {

    /**
     * Renders one coherent changed snapshot into the JavaFX-facing read model and
     * derives title and discard state from that same snapshot.
     */
    @Test
    void changedOutcomeRendersOneCoherentProjectReadModel() {
        ProjectPresentation presentation = newPresentation();
        ProjectSnapshot snapshot = populatedSnapshot(Paths.get("projects", "example.jbs2bg"), true);

        ProjectPresentationUpdate update = presentation.render(new ChangedOutcome(snapshot));

        assertTrue(update.invalidatesGeneratedOutput());
        assertFalse(update.hasDiagnostics());
        assertEquals("jBS2BG - *example.jbs2bg", presentation.getWindowTitle());
        assertTrue(presentation.requiresDiscardConfirmation());

        SliderPreset preset = presentation.getSliderPresets().get(0);
        assertEquals("Athletic", preset.getName());
        assertEquals(25, preset.getSetSliders().get(0).getValueSmall().intValue());
        assertEquals(75, preset.getSetSliders().get(0).getValueBig().intValue());

        CustomMorphTarget target = presentation.getCustomMorphTargets().get(0);
        assertEquals("All|Female", target.getName());
        assertEquals("Athletic", target.getSliderPresets().get(0).getName());

        NPC npc = presentation.getNpcMorphAssignments().get(0);
        assertEquals("Skyrim.esm", npc.getMod());
        assertEquals("FemaleNord", npc.getEditorId());
        assertEquals("Athletic", npc.getSliderPresets().get(0).getName());
    }

    /**
     * Keeps unchanged and failed outcomes from invalidating generated output while
     * formatting structured diagnostics into stable, user-readable text.
     */
    @Test
    void nonChangedOutcomesPreserveGeneratedOutputAndFormatDiagnostics() {
        ProjectPresentation presentation = newPresentation();
        ProjectSnapshot snapshot = populatedSnapshot(Paths.get("projects", "example.jbs2bg"), false);
        Path source = Paths.get("imports", "broken.xml");
        ProjectDiagnostic diagnostic = new ProjectDiagnostic("XML_PARSE_FAILED", DiagnosticSeverity.ERROR,
                new SourceLocation(Optional.of(source), Optional.of("Preset"), OptionalInt.of(3),
                        OptionalInt.of(7)),
                "Could not parse source.");
        ProjectDiagnostic missingDiagnostic = new ProjectDiagnostic("PROJECT_FILE_READ_FAILED",
                DiagnosticSeverity.ERROR, new SourceLocation(Optional.of(Paths.get("imports", "missing.xml")),
                        Optional.of("/"), OptionalInt.empty(), OptionalInt.empty()),
                "The source could not be read.");
        List<ProjectDiagnostic> diagnostics = Arrays.asList(diagnostic, missingDiagnostic);

        ProjectPresentationUpdate unchanged = presentation
                .render(new UnchangedOutcome(snapshot, diagnostics));
        SliderPreset renderedBeforeFailure = presentation.getSliderPresets().get(0);
        ProjectPresentationUpdate failed = presentation
                .render(new FailedOutcome(snapshot, diagnostics));

        assertFalse(unchanged.invalidatesGeneratedOutput());
        assertFalse(failed.invalidatesGeneratedOutput());
        assertTrue(failed.hasDiagnostics());
        assertTrue(failed.hasErrorDiagnostics());
        assertSame(renderedBeforeFailure, presentation.getSliderPresets().get(0));
        assertEquals("ERROR [XML_PARSE_FAILED] broken.xml / Preset (line 3, column 7): Could not parse source."
                + System.lineSeparator()
                + "ERROR [PROJECT_FILE_READ_FAILED] missing.xml: The source could not be read.",
                failed.getDiagnosticText());
        assertEquals("jBS2BG - example.jbs2bg", presentation.getWindowTitle());
        assertFalse(presentation.requiresDiscardConfirmation());
    }

    /**
     * Preserves the snapshot's explicit missing-default classification instead of
     * inferring it from nullable stored values while building view projections.
     */
    @Test
    void explicitBothNullSliderChoiceDoesNotBecomeMissingDefault() {
        ProjectPresentation presentation = newPresentation();
        SliderChoiceSnapshot explicitChoice = new SliderChoiceSnapshot("Waist", true, null, null, 20, 80, 15,
                85, false);
        SliderChoiceSnapshot missingDefaultChoice = new SliderChoiceSnapshot("Breasts", true, null, null, 0, 100,
                100, 100, true);
        SliderPresetSnapshot preset = new SliderPresetSnapshot("Explicit Nulls", false,
                Arrays.asList(explicitChoice, missingDefaultChoice));
        ProjectSnapshot snapshot = new ProjectSnapshot(Collections.singletonList(preset),
                Collections.<CustomMorphTargetSnapshot>emptyList(),
                Collections.<NpcMorphAssignmentSnapshot>emptyList(), Optional.<Path>empty(), true,
                ProjectLifecycleStatus.UNTITLED);

        presentation.render(new ChangedOutcome(snapshot));

        SliderPreset rendered = presentation.getSliderPresets().get(0);
        assertEquals(1, rendered.getSetSliders().size());
        assertEquals(1, rendered.getMissingDefaultSetSliders().size());
        assertEquals("Waist", rendered.getSetSliders().get(0).getName());
        assertEquals(20, rendered.getSetSliders().get(0).getValueSmall().intValue());
        assertEquals(80, rendered.getSetSliders().get(0).getValueBig().intValue());
        assertEquals("Breasts", rendered.getMissingDefaultSetSliders().get(0).getName());
        assertEquals("jBS2BG *", presentation.getWindowTitle());
    }

    /**
     * Exposes observable Project projections for JavaFX rendering without allowing
     * controllers to mutate the top-level Project collections directly.
     */
    @Test
    void renderedProjectCollectionsAreStructurallyReadOnly() {
        ProjectPresentation presentation = newPresentation();
        presentation.render(new ChangedOutcome(populatedSnapshot(Paths.get("projects", "example.jbs2bg"), true)));

        assertThrows(UnsupportedOperationException.class, () -> presentation.getSliderPresets().clear());
        assertThrows(UnsupportedOperationException.class, () -> presentation.getCustomMorphTargets().clear());
        assertThrows(UnsupportedOperationException.class, () -> presentation.getNpcMorphAssignments().clear());
    }

    /** Creates a presentation read model from the canonical New Project outcome. */
    private static ProjectPresentation newPresentation() {
        ProjectSession session = ProjectSessions.create();
        ProjectOutcome outcome = session.newProject();
        return new ProjectPresentation("jBS2BG", outcome.getSnapshot());
    }

    /** Builds a representative immutable Project snapshot from independent literals. */
    private static ProjectSnapshot populatedSnapshot(Path fileIdentity, boolean dirty) {
        SliderChoiceSnapshot choice = new SliderChoiceSnapshot("Waist", true, Integer.valueOf(25),
                Integer.valueOf(75), 25, 75, 10, 90, false);
        SliderPresetSnapshot preset = new SliderPresetSnapshot("Athletic", false,
                Collections.singletonList(choice));
        CustomMorphTargetSnapshot target = new CustomMorphTargetSnapshot("All|Female",
                Collections.singletonList("Athletic"));
        NpcMorphAssignmentSnapshot npc = new NpcMorphAssignmentSnapshot("Aela", "Skyrim.esm", "FemaleNord",
                "NordRace", "1A696", Collections.singletonList("Athletic"));
        return new ProjectSnapshot(Collections.singletonList(preset), Collections.singletonList(target),
                Arrays.asList(npc), Optional.of(fileIdentity), dirty, ProjectLifecycleStatus.FILE_BACKED);
    }
}
