package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

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

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

class ProjectPresentationTest {

    /**
     * Creates a presentation read model from the canonical New Project outcome.
     */
    private static ProjectPresentation newPresentation() {
        ProjectSession session = ProjectSessions.create();
        ProjectOutcome outcome = session.newProject();
        return new ProjectPresentation("jBS2BG", outcome.getSnapshot());
    }

    /**
     * Builds a representative immutable Project snapshot from independent literals.
     */
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

    /**
     * Verifies one observable getter's immutable generic element contract.
     *
     * @param getterName        public ProjectPresentation collection getter
     * @param snapshotType      required immutable element type
     * @param expectedAccessors complete public API allowed on the immutable value
     * @throws Exception when the getter is absent or its reflective signature cannot
     *                   be inspected
     */
    private static void assertImmutableSnapshotGetter(String getterName, Class<?> snapshotType,
                                                      String... expectedAccessors) throws Exception {
        Method getter = ProjectPresentation.class.getMethod(getterName);
        assertEquals(ObservableList.class, getter.getReturnType());
        ParameterizedType returnType = (ParameterizedType) getter.getGenericReturnType();
        assertEquals(snapshotType, returnType.getActualTypeArguments()[0]);

        List<String> publicApi = new ArrayList<>();
        for (Method method : snapshotType.getDeclaredMethods())
            if (Modifier.isPublic(method.getModifiers()))
                publicApi.add(method.getName());
        Collections.sort(publicApi);
        assertEquals(Arrays.asList(expectedAccessors), publicApi);
    }

    /**
     * Locks the presentation collection seam to immutable Project snapshot values
     * without public mutation routes on the exposed element types.
     *
     * @throws Exception when a required collection getter is absent
     */
    @Test
    void presentationCollectionsExposeOnlyImmutableSnapshotValues() throws Exception {
        assertImmutableSnapshotGetter("getSliderPresets", SliderPresetSnapshot.class,
                "getName", "getSliderChoices", "isUunp");
        assertImmutableSnapshotGetter("getCustomMorphTargets", CustomMorphTargetSnapshot.class,
                "getName", "getSliderPresetNames");
        assertImmutableSnapshotGetter("getNpcMorphAssignments", NpcMorphAssignmentSnapshot.class,
                "getDisplayName", "getEditorId", "getFormId", "getPluginName", "getRace",
                "getSliderPresetNames");
    }

    /**
     * Renders one coherent changed snapshot into the JavaFX-facing read model and
     * derives title and discard state from that same snapshot.
     */
    @Test
    void changedOutcomeRendersOneCoherentProjectReadModel() {
        ProjectPresentation presentation = newPresentation();
        ObservableList<SliderPresetSnapshot> sliderPresets = presentation.getSliderPresets();
        ObservableList<CustomMorphTargetSnapshot> customMorphTargets = presentation.getCustomMorphTargets();
        ObservableList<NpcMorphAssignmentSnapshot> npcMorphAssignments = presentation.getNpcMorphAssignments();
        ProjectSnapshot snapshot = populatedSnapshot(Paths.get("projects", "example.jbs2bg"), true);

        ProjectPresentationUpdate update = presentation.render(new ChangedOutcome(snapshot));

        assertSame(sliderPresets, presentation.getSliderPresets());
        assertSame(customMorphTargets, presentation.getCustomMorphTargets());
        assertSame(npcMorphAssignments, presentation.getNpcMorphAssignments());
        assertTrue(update.invalidatesGeneratedOutput());
        assertFalse(update.hasDiagnostics());
        assertEquals("jBS2BG - *example.jbs2bg", presentation.getWindowTitle());
        assertTrue(presentation.requiresDiscardConfirmation());

        SliderPresetSnapshot preset = presentation.getSliderPresets().get(0);
        assertSame(snapshot.getSliderPresets().get(0), preset);
        assertEquals("Athletic", preset.getName());
        assertEquals(25, preset.getSliderChoices().get(0).getEffectiveSmallValue());
        assertEquals(75, preset.getSliderChoices().get(0).getEffectiveBigValue());

        CustomMorphTargetSnapshot target = presentation.getCustomMorphTargets().get(0);
        assertSame(snapshot.getCustomMorphTargets().get(0), target);
        assertEquals("All|Female", target.getName());
        assertEquals(Collections.singletonList("Athletic"), target.getSliderPresetNames());

        NpcMorphAssignmentSnapshot npc = presentation.getNpcMorphAssignments().get(0);
        assertSame(snapshot.getNpcMorphAssignments().get(0), npc);
        assertEquals("Skyrim.esm", npc.getPluginName());
        assertEquals("FemaleNord", npc.getEditorId());
        assertEquals(Collections.singletonList("Athletic"), npc.getSliderPresetNames());
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
        // Non-changed outcomes always carry the session's current snapshot, which the
        // presentation has already rendered; establish that state first.
        presentation.render(new ChangedOutcome(snapshot));

        ProjectPresentationUpdate unchanged = presentation
                .render(new UnchangedOutcome(snapshot, diagnostics));
        SliderPresetSnapshot renderedBeforeFailure = presentation.getSliderPresets().get(0);
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
     * Keeps generated output when a changed outcome alters only save metadata (dirty
     * flag or file identity) while still invalidating on real content changes.
     */
    @Test
    void metadataOnlyChangesPreserveGeneratedOutput() {
        ProjectPresentation presentation = newPresentation();
        ProjectSnapshot dirty = populatedSnapshot(Paths.get("projects", "example.jbs2bg"), true);
        presentation.render(new ChangedOutcome(dirty));
        ProjectSnapshot saved = new ProjectSnapshot(dirty.getSliderPresets(), dirty.getCustomMorphTargets(),
                dirty.getNpcMorphAssignments(), Optional.of(Paths.get("projects", "renamed.jbs2bg")), false,
                ProjectLifecycleStatus.FILE_BACKED);

        ProjectPresentationUpdate savedUpdate = presentation.render(new ChangedOutcome(saved));

        assertFalse(savedUpdate.invalidatesGeneratedOutput());
        assertSame(saved, presentation.getSnapshot());
        assertSame(saved.getSliderPresets().get(0), presentation.getSliderPresets().get(0));
        assertEquals("jBS2BG - renamed.jbs2bg", presentation.getWindowTitle());
        assertFalse(presentation.requiresDiscardConfirmation());

        ProjectSnapshot edited = new ProjectSnapshot(Collections.<SliderPresetSnapshot>emptyList(),
                saved.getCustomMorphTargets(), saved.getNpcMorphAssignments(), saved.getFileIdentity(), true,
                ProjectLifecycleStatus.FILE_BACKED);
        ProjectPresentationUpdate editedUpdate = presentation.render(new ChangedOutcome(edited));

        assertTrue(editedUpdate.invalidatesGeneratedOutput());
    }

    /**
     * Makes the published snapshot visible before projection listeners fire, so a
     * listener never combines a new list item with the previous snapshot.
     */
    @Test
    void projectionListenersObserveThePublishedSnapshot() {
        ProjectPresentation presentation = newPresentation();
        ProjectSnapshot next = populatedSnapshot(Paths.get("projects", "example.jbs2bg"), true);
        List<ProjectSnapshot> observedSnapshots = new ArrayList<>();
        presentation.getSliderPresets().addListener(
                (ListChangeListener<SliderPresetSnapshot>) change -> observedSnapshots.add(presentation.getSnapshot()));

        presentation.render(new ChangedOutcome(next));

        assertEquals(1, observedSnapshots.size());
        assertSame(next, observedSnapshots.get(0));
    }

    /**
     * Preserves the snapshot's explicit missing-default classification instead of
     * inferring it from nullable stored values while building view projections.
     */
    @Test
    void explicitSliderChoiceClassificationIsRenderedWithoutConversion() {
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

        SliderPresetSnapshot rendered = presentation.getSliderPresets().get(0);
        assertSame(explicitChoice, rendered.getSliderChoices().get(0));
        assertFalse(rendered.getSliderChoices().get(0).isMissingDefault());
        assertEquals(20, rendered.getSliderChoices().get(0).getEffectiveSmallValue());
        assertEquals(80, rendered.getSliderChoices().get(0).getEffectiveBigValue());
        assertSame(missingDefaultChoice, rendered.getSliderChoices().get(1));
        assertTrue(rendered.getSliderChoices().get(1).isMissingDefault());
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
}
