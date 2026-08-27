package com.asdasfa.jbs2bg.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class ProjectSessionTest {

    /**
     * Verifies that New Project establishes the lifecycle's canonical empty state
     * through the same interface used by presentation callers.
     */
    @Test
    void newProjectProducesAnEmptyCleanUntitledSnapshot() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot before = session.getSnapshot();

        ProjectOutcome outcome = session.newProject();
        ProjectSnapshot snapshot = outcome.getSnapshot();

        assertEquals(ProjectLifecycleStatus.NO_PROJECT, before.getLifecycleStatus());
        assertTrue(outcome instanceof ChangedOutcome);
        assertNotSame(before, snapshot);
        assertSame(snapshot, session.getSnapshot());
        assertTrue(snapshot.getSliderPresets().isEmpty());
        assertTrue(snapshot.getCustomMorphTargets().isEmpty());
        assertTrue(snapshot.getNpcMorphAssignments().isEmpty());
        assertFalse(snapshot.getFileIdentity().isPresent());
        assertFalse(snapshot.isDirty());
        assertEquals(ProjectLifecycleStatus.UNTITLED, snapshot.getLifecycleStatus());
    }

    /**
     * Proves that snapshots copy their complete value graph and expose no mutable
     * collection or legacy domain object to callers.
     */
    @Test
    void snapshotValuesAreDeeplyImmutableAndDefensivelyCopied() {
        List<SliderChoiceSnapshot> choices = new ArrayList<>();
        choices.add(new SliderChoiceSnapshot("Waist", true, Integer.valueOf(20), Integer.valueOf(80), 20, 80,
                10, 90, false));
        SliderPresetSnapshot preset = new SliderPresetSnapshot("CBBE Curvy", false, choices);

        List<String> targetAssignments = new ArrayList<>(Arrays.asList("CBBE Curvy"));
        CustomMorphTargetSnapshot target = new CustomMorphTargetSnapshot("All|Female", targetAssignments);

        List<String> npcAssignments = new ArrayList<>(Arrays.asList("CBBE Curvy"));
        NpcMorphAssignmentSnapshot npc = new NpcMorphAssignmentSnapshot("Lydia", "Skyrim.esm",
                "HousecarlWhiterun", "NordRace", "A2C94", npcAssignments);

        List<SliderPresetSnapshot> presets = new ArrayList<>(Arrays.asList(preset));
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(Arrays.asList(target));
        List<NpcMorphAssignmentSnapshot> npcs = new ArrayList<>(Arrays.asList(npc));
        ProjectSnapshot snapshot = new ProjectSnapshot(presets, targets, npcs,
                Optional.of(Paths.get("project.jbs2bg")), true, ProjectLifecycleStatus.FILE_BACKED);

        choices.clear();
        targetAssignments.clear();
        npcAssignments.clear();
        presets.clear();
        targets.clear();
        npcs.clear();

        assertEquals("CBBE Curvy", snapshot.getSliderPresets().get(0).getName());
        assertEquals(1, snapshot.getSliderPresets().get(0).getSliderChoices().size());
        assertEquals(20, snapshot.getSliderPresets().get(0).getSliderChoices().get(0).getEffectiveSmallValue());
        assertEquals(Arrays.asList("CBBE Curvy"),
                snapshot.getCustomMorphTargets().get(0).getSliderPresetNames());
        assertEquals("Skyrim.esm", snapshot.getNpcMorphAssignments().get(0).getPluginName());
        assertEquals(Arrays.asList("CBBE Curvy"),
                snapshot.getNpcMorphAssignments().get(0).getSliderPresetNames());
        assertTrue(snapshot.getFileIdentity().get().isAbsolute());

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getSliderPresets().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getSliderPresets().get(0).getSliderChoices().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getCustomMorphTargets().get(0).getSliderPresetNames().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getNpcMorphAssignments().get(0).getSliderPresetNames().clear());
    }

    /**
     * Verifies that callers can distinguish all operation outcomes while consuming
     * one immutable snapshot and structured diagnostic contract.
     */
    @Test
    void outcomesAreDistinctAndCarryImmutableDiagnosticsWithTheirSnapshot() {
        ProjectSnapshot snapshot = ProjectSessions.create().getSnapshot();
        SourceLocation location = new SourceLocation(Optional.of(Paths.get("broken.jbs2bg")),
                Optional.of("/SliderPresets/CBBE Curvy"), OptionalInt.of(4), OptionalInt.of(12));
        ProjectDiagnostic diagnostic = new ProjectDiagnostic("PROJECT_INVALID", DiagnosticSeverity.ERROR,
                location, "The Project contains an invalid Slider Preset.");
        List<ProjectDiagnostic> diagnostics = new ArrayList<>(Arrays.asList(diagnostic));

        List<ProjectOutcome> outcomes = Arrays.asList(new ChangedOutcome(snapshot, diagnostics),
                new UnchangedOutcome(snapshot, diagnostics), new RejectedOutcome(snapshot, diagnostics),
                new FailedOutcome(snapshot, diagnostics));
        diagnostics.clear();

        assertEquals(ChangedOutcome.class, outcomes.get(0).getClass());
        assertEquals(UnchangedOutcome.class, outcomes.get(1).getClass());
        assertEquals(RejectedOutcome.class, outcomes.get(2).getClass());
        assertEquals(FailedOutcome.class, outcomes.get(3).getClass());
        for (ProjectOutcome outcome : outcomes) {
            assertSame(snapshot, outcome.getSnapshot());
            assertEquals(1, outcome.getDiagnostics().size());
            assertThrows(UnsupportedOperationException.class, () -> outcome.getDiagnostics().clear());
        }

        ProjectDiagnostic exposed = outcomes.get(0).getDiagnostics().get(0);
        assertEquals("PROJECT_INVALID", exposed.getCode());
        assertEquals(DiagnosticSeverity.ERROR, exposed.getSeverity());
        assertTrue(exposed.getSourceLocation().getPath().get().isAbsolute());
        assertEquals("/SliderPresets/CBBE Curvy", exposed.getSourceLocation().getElement().get());
        assertEquals(4, exposed.getSourceLocation().getLine().getAsInt());
        assertEquals(12, exposed.getSourceLocation().getColumn().getAsInt());
        assertEquals("The Project contains an invalid Slider Preset.", exposed.getMessage());
    }

    /**
     * Verifies that the single edit entry rejects unknown data requests without
     * changing or hiding the latest Project snapshot.
     */
    @Test
    void applyRejectsAnUnsupportedProjectEditWithTheLatestSnapshot() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot before = session.getSnapshot();
        ProjectEdit unsupported = new ProjectEdit() {
        };

        ProjectOutcome outcome = session.apply(unsupported);

        assertTrue(outcome instanceof RejectedOutcome);
        assertSame(before, outcome.getSnapshot());
        assertSame(before, session.getSnapshot());
        assertEquals("PROJECT_EDIT_UNSUPPORTED", outcome.getDiagnostics().get(0).getCode());
        assertEquals("project-edit", outcome.getDiagnostics().get(0).getSourceLocation().getElement().get());
    }

    /**
     * Verifies that creating Slider Presets normalizes their names, publishes them
     * in canonical order, and dirties the Project only through the session seam.
     */
    @Test
    void creatingSliderPresetsTrimsNamesOrdersSnapshotsAndMarksDirty() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();

        ProjectOutcome first = session.apply(SliderPresetEdits.create(" beta "));
        ProjectOutcome second = session.apply(SliderPresetEdits.create("Alpha"));

        assertTrue(first instanceof ChangedOutcome);
        assertTrue(second instanceof ChangedOutcome);
        assertEquals(Arrays.asList("Alpha", "beta"), sliderPresetNames(second.getSnapshot()));
        assertFalse(second.getSnapshot().getSliderPresets().get(0).isUunp());
        assertTrue(second.getSnapshot().getSliderPresets().get(0).getSliderChoices().isEmpty());
        assertTrue(second.getSnapshot().isDirty());
        assertSame(second.getSnapshot(), session.getSnapshot());
    }

    /**
     * Verifies that invalid or duplicate Slider Preset names return structured
     * diagnostics without changing the snapshot or its prior dirty state.
     */
    @Test
    void invalidAndDuplicateSliderPresetCreationPreservesSnapshotAndDirtyState() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot clean = session.newProject().getSnapshot();

        ProjectOutcome blank = session.apply(SliderPresetEdits.create("   "));
        ProjectOutcome dotted = session.apply(SliderPresetEdits.create("Invalid.Name"));

        assertRejectedWithCode(blank, ProjectDiagnosticCodes.SLIDER_PRESET_NAME_REQUIRED, clean);
        assertRejectedWithCode(dotted, ProjectDiagnosticCodes.SLIDER_PRESET_NAME_CONTAINS_DOT, clean);
        assertFalse(clean.isDirty());

        ProjectSnapshot dirty = session.apply(SliderPresetEdits.create("Alpha")).getSnapshot();
        ProjectOutcome duplicate = session.apply(SliderPresetEdits.create(" alpha "));

        assertRejectedWithCode(duplicate, ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE, dirty);
        assertTrue(dirty.isDirty());
        assertEquals(Arrays.asList("Alpha"), sliderPresetNames(dirty));
    }

    /**
     * Verifies that UUNP and slider-choice edits preserve every observable value,
     * order choices canonically, and report repeated values as no-ops.
     */
    @Test
    void uunpAndSliderChoiceEditsPreserveImmutableValueSemantics() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        SliderChoiceSnapshot waist = new SliderChoiceSnapshot("Waist", true, null, null, 30, 70, 25, 75, true);
        SliderChoiceSnapshot arms = new SliderChoiceSnapshot("Arms", false, Integer.valueOf(10),
                Integer.valueOf(90), 10, 90, 0, 100, false);

        ProjectOutcome uunp = session.apply(SliderPresetEdits.setUunp("Alpha", true));
        ProjectOutcome firstChoice = session.apply(SliderPresetEdits.setSliderChoice("Alpha", waist));
        ProjectOutcome secondChoice = session.apply(SliderPresetEdits.setSliderChoice("Alpha", arms));
        ProjectSnapshot changed = secondChoice.getSnapshot();

        assertTrue(uunp instanceof ChangedOutcome);
        assertTrue(firstChoice instanceof ChangedOutcome);
        assertTrue(secondChoice instanceof ChangedOutcome);
        SliderPresetSnapshot preset = changed.getSliderPresets().get(0);
        assertTrue(preset.isUunp());
        assertEquals(Arrays.asList("Arms", "Waist"), sliderChoiceNames(preset));
        SliderChoiceSnapshot exposedWaist = preset.getSliderChoices().get(1);
        assertFalse(exposedWaist.getStoredSmallValue().isPresent());
        assertFalse(exposedWaist.getStoredBigValue().isPresent());
        assertEquals(30, exposedWaist.getEffectiveSmallValue());
        assertEquals(70, exposedWaist.getEffectiveBigValue());
        assertEquals(25, exposedWaist.getPercentageMinimum());
        assertEquals(75, exposedWaist.getPercentageMaximum());
        assertTrue(exposedWaist.isMissingDefault());

        ProjectOutcome unchangedUunp = session.apply(SliderPresetEdits.setUunp("Alpha", true));
        ProjectOutcome unchangedChoice = session.apply(SliderPresetEdits.setSliderChoice("Alpha", waist));

        assertTrue(unchangedUunp instanceof UnchangedOutcome);
        assertSame(changed, unchangedUunp.getSnapshot());
        assertTrue(unchangedChoice instanceof UnchangedOutcome);
        assertSame(changed, unchangedChoice.getSnapshot());
        assertTrue(changed.isDirty());
    }

    /**
     * Verifies that duplication copies the complete Slider Preset value under a new
     * logical name and that later source edits cannot alter the duplicate.
     */
    @Test
    void duplicatingSliderPresetCopiesValuesWithoutSharingLaterEdits() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.setUunp("Alpha", true));
        SliderChoiceSnapshot waist = new SliderChoiceSnapshot("Waist", true, null, null, 30, 70, 25, 75, true);
        session.apply(SliderPresetEdits.setSliderChoice("Alpha", waist));

        ProjectOutcome duplicated = session.apply(SliderPresetEdits.duplicate("alpha", " beta "));
        ProjectSnapshot duplicatedSnapshot = duplicated.getSnapshot();

        assertTrue(duplicated instanceof ChangedOutcome);
        assertEquals(Arrays.asList("Alpha", "beta"), sliderPresetNames(duplicatedSnapshot));
        SliderPresetSnapshot source = duplicatedSnapshot.getSliderPresets().get(0);
        SliderPresetSnapshot copy = duplicatedSnapshot.getSliderPresets().get(1);
        assertNotSame(source, copy);
        assertTrue(copy.isUunp());
        assertEquals(Arrays.asList("Waist"), sliderChoiceNames(copy));
        assertTrue(copy.getSliderChoices().get(0).isMissingDefault());

        ProjectSnapshot afterSourceEdit = session.apply(SliderPresetEdits.setUunp("Alpha", false)).getSnapshot();

        assertFalse(afterSourceEdit.getSliderPresets().get(0).isUunp());
        assertTrue(afterSourceEdit.getSliderPresets().get(1).isUunp());
    }

    /**
     * Verifies that rename retains the logical Slider Preset payload and that full
     * updates normalize names, reorder the catalog, and detect semantic no-ops.
     */
    @Test
    void renamingAndUpdatingSliderPresetsRetainsIdentityAndCanonicalOrder() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("bravo"));
        session.apply(SliderPresetEdits.create("delta"));
        session.apply(SliderPresetEdits.setUunp("bravo", true));
        SliderChoiceSnapshot waist = new SliderChoiceSnapshot("Waist", true, Integer.valueOf(20),
                Integer.valueOf(80), 20, 80, 10, 90, false);
        session.apply(SliderPresetEdits.setSliderChoice("bravo", waist));

        ProjectOutcome caseOnly = session.apply(SliderPresetEdits.rename("BRAVO", "BRAVO"));
        ProjectOutcome reordered = session.apply(SliderPresetEdits.rename("delta", " Alpha "));

        assertTrue(caseOnly instanceof ChangedOutcome);
        assertEquals("BRAVO", caseOnly.getSnapshot().getSliderPresets().get(0).getName());
        assertTrue(caseOnly.getSnapshot().getSliderPresets().get(0).isUunp());
        assertEquals(Arrays.asList("Waist"),
                sliderChoiceNames(caseOnly.getSnapshot().getSliderPresets().get(0)));
        assertTrue(reordered instanceof ChangedOutcome);
        assertEquals(Arrays.asList("Alpha", "BRAVO"), sliderPresetNames(reordered.getSnapshot()));

        ProjectOutcome unchangedRename = session.apply(SliderPresetEdits.rename("alpha", " Alpha "));
        assertTrue(unchangedRename instanceof UnchangedOutcome);
        assertSame(reordered.getSnapshot(), unchangedRename.getSnapshot());

        SliderChoiceSnapshot arms = new SliderChoiceSnapshot("Arms", false, Integer.valueOf(5),
                Integer.valueOf(95), 5, 95, 0, 100, false);
        SliderPresetSnapshot replacement = new SliderPresetSnapshot(" charlie ", false,
                Arrays.asList(waist, arms));
        ProjectOutcome updated = session.apply(SliderPresetEdits.update("bravo", replacement));

        assertTrue(updated instanceof ChangedOutcome);
        assertEquals(Arrays.asList("Alpha", "charlie"), sliderPresetNames(updated.getSnapshot()));
        SliderPresetSnapshot updatedPreset = updated.getSnapshot().getSliderPresets().get(1);
        assertFalse(updatedPreset.isUunp());
        assertEquals(Arrays.asList("Arms", "Waist"), sliderChoiceNames(updatedPreset));

        SliderPresetSnapshot sameReplacement = new SliderPresetSnapshot("charlie", false,
                Arrays.asList(arms, waist));
        ProjectOutcome unchangedUpdate = session.apply(SliderPresetEdits.update("CHARLIE", sameReplacement));
        assertTrue(unchangedUpdate instanceof UnchangedOutcome);
        assertSame(updated.getSnapshot(), unchangedUpdate.getSnapshot());
    }

    /**
     * Verifies that delete and clear update only an existing catalog and preserve
     * the exact prior snapshot for absent targets or an already-empty catalog.
     */
    @Test
    void deletingAndClearingSliderPresetsReportsTruthfulOutcomes() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("beta"));

        ProjectOutcome deleted = session.apply(SliderPresetEdits.delete("ALPHA"));
        assertTrue(deleted instanceof ChangedOutcome);
        assertEquals(Arrays.asList("beta"), sliderPresetNames(deleted.getSnapshot()));

        ProjectOutcome missing = session.apply(SliderPresetEdits.delete("missing"));
        assertRejectedWithCode(missing, ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND,
                deleted.getSnapshot());

        ProjectOutcome cleared = session.apply(SliderPresetEdits.clear());
        assertTrue(cleared instanceof ChangedOutcome);
        assertTrue(cleared.getSnapshot().getSliderPresets().isEmpty());
        assertTrue(cleared.getSnapshot().isDirty());

        ProjectOutcome alreadyEmpty = session.apply(SliderPresetEdits.clear());
        assertTrue(alreadyEmpty instanceof UnchangedOutcome);
        assertSame(cleared.getSnapshot(), alreadyEmpty.getSnapshot());
    }

    /**
     * Verifies that a recognized catalog edit cannot manufacture active state before
     * New Project or Open and reports the lifecycle problem without throwing.
     */
    @Test
    void sliderPresetEditBeforeActiveProjectReturnsStructuredRejection() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot before = session.getSnapshot();

        ProjectOutcome outcome = session.apply(SliderPresetEdits.create("Alpha"));

        assertTrue(outcome instanceof RejectedOutcome);
        assertSame(before, outcome.getSnapshot());
        assertSame(before, session.getSnapshot());
        assertFalse(before.isDirty());
        assertEquals(ProjectDiagnosticCodes.ACTIVE_PROJECT_REQUIRED,
                outcome.getDiagnostics().get(0).getCode());
        assertEquals("project", outcome.getDiagnostics().get(0).getSourceLocation().getElement().get());
    }

    /**
     * Locks the external seam to explicit lifecycle operations and one edit entry,
     * with no JavaFX or legacy mutable Project types in its method signatures.
     *
     * @throws Exception when a required interface method is absent
     */
    @Test
    void interfaceExposesExplicitLifecycleOperationsWithoutMutableOrJavaFxTypes() throws Exception {
        assertOutcomeMethod("newProject");
        assertOutcomeMethod("open", Path.class);
        assertOutcomeMethod("save");
        assertOutcomeMethod("saveAs", Path.class);
        assertOutcomeMethod("apply", ProjectEdit.class);

        int applyMethods = 0;
        for (Method method : ProjectSession.class.getDeclaredMethods()) {
            if (method.getName().equals("apply"))
                applyMethods++;
            assertExternalType(method.getReturnType());
            for (Class<?> parameterType : method.getParameterTypes())
                assertExternalType(parameterType);
        }
        assertEquals(1, applyMethods);
    }

    /**
     * Proves that worker-thread operations and readers observe only a complete,
     * atomically published New Project snapshot.
     *
     * @throws Exception when a worker cannot complete within the test deadline
     */
    @Test
    void workerThreadsObserveOnlyAtomicProjectSnapshots() throws Exception {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot before = session.getSnapshot();
        int workerCount = 8;
        int iterations = 250;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<ProjectSnapshot>>> futures = new ArrayList<>();

        try {
            for (int worker = 0; worker < workerCount; worker++) {
                final boolean invokeOperation = worker % 2 == 0;
                Callable<List<ProjectSnapshot>> task = () -> {
                    start.await();
                    List<ProjectSnapshot> observed = new ArrayList<>();
                    for (int iteration = 0; iteration < iterations; iteration++) {
                        ProjectSnapshot snapshot = invokeOperation ? session.newProject().getSnapshot()
                                : session.getSnapshot();
                        observed.add(snapshot);
                    }
                    return observed;
                };
                futures.add(executor.submit(task));
            }

            start.countDown();
            for (Future<List<ProjectSnapshot>> future : futures) {
                for (ProjectSnapshot snapshot : future.get(10, TimeUnit.SECONDS)) {
                    ProjectSnapshot after = session.getSnapshot();
                    assertTrue(snapshot == before || snapshot == after,
                            "Observed a snapshot outside the complete pre/post-operation states");
                }
            }
            ProjectSnapshot after = session.getSnapshot();
            assertNotSame(before, after);
            assertEquals(ProjectLifecycleStatus.NO_PROJECT, before.getLifecycleStatus());
            assertCompleteNewProject(after);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void assertOutcomeMethod(String name, Class<?>... parameterTypes) throws Exception {
        assertEquals(ProjectOutcome.class, ProjectSession.class.getMethod(name, parameterTypes).getReturnType());
    }

    private static void assertExternalType(Class<?> type) {
        assertFalse(type.getName().startsWith("javafx."), "JavaFX leaked through ProjectSession: " + type);
        assertFalse(type.getName().startsWith("com.asdasfa.jbs2bg.data."),
                "Legacy mutable Project type leaked through ProjectSession: " + type);
    }

    private static void assertCompleteNewProject(ProjectSnapshot snapshot) {
        assertTrue(snapshot.getSliderPresets().isEmpty());
        assertTrue(snapshot.getCustomMorphTargets().isEmpty());
        assertTrue(snapshot.getNpcMorphAssignments().isEmpty());
        assertFalse(snapshot.getFileIdentity().isPresent());
        assertFalse(snapshot.isDirty());
        assertEquals(ProjectLifecycleStatus.UNTITLED, snapshot.getLifecycleStatus());
    }

    /**
     * Extracts the ordered Slider Preset names exposed by a session snapshot.
     *
     * @param snapshot immutable Project state to inspect
     * @return names in snapshot order
     */
    private static List<String> sliderPresetNames(ProjectSnapshot snapshot) {
        List<String> names = new ArrayList<>();
        for (SliderPresetSnapshot preset : snapshot.getSliderPresets())
            names.add(preset.getName());
        return names;
    }

    /**
     * Extracts the ordered slider-choice names exposed by a Slider Preset.
     *
     * @param preset immutable Slider Preset value to inspect
     * @return choice names in snapshot order
     */
    private static List<String> sliderChoiceNames(SliderPresetSnapshot preset) {
        List<String> names = new ArrayList<>();
        for (SliderChoiceSnapshot choice : preset.getSliderChoices())
            names.add(choice.getName());
        return names;
    }

    /**
     * Asserts the common structured naming rejection contract.
     *
     * @param outcome rejected session operation
     * @param code expected stable diagnostic code
     * @param snapshot exact prior snapshot that must be preserved
     */
    private static void assertRejectedWithCode(ProjectOutcome outcome, String code, ProjectSnapshot snapshot) {
        assertTrue(outcome instanceof RejectedOutcome);
        assertSame(snapshot, outcome.getSnapshot());
        assertEquals(code, outcome.getDiagnostics().get(0).getCode());
        assertEquals(DiagnosticSeverity.ERROR, outcome.getDiagnostics().get(0).getSeverity());
        assertEquals("slider-preset.name",
                outcome.getDiagnostics().get(0).getSourceLocation().getElement().get());
    }
}
