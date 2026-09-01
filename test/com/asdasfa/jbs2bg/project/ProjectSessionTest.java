package com.asdasfa.jbs2bg.project;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.SettingsTestSupport;

import static org.junit.jupiter.api.Assertions.*;

class ProjectSessionTest {

    private static void assertOutcomeMethod(String name, Class<?>... parameterTypes) throws Exception {
        assertEquals(ProjectOutcome.class, ProjectSession.class.getMethod(name, parameterTypes).getReturnType());
    }

    /**
     * Recursively rejects forbidden raw, array, and generic contract types.
     */
    private static void assertExternalType(Type type) {
        if (type instanceof Class<?> rawType) {
            if (rawType.isArray()) {
                assertExternalType(rawType.getComponentType());
                return;
            }
            assertFalse(rawType.getName().startsWith("javafx."),
                    "JavaFX leaked through ProjectSession: " + rawType);
            assertFalse(rawType.getName().startsWith("com.asdasfa.jbs2bg.data."),
                    "Legacy mutable Project type leaked through ProjectSession: " + rawType);
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            assertExternalType(parameterized.getRawType());
            for (Type argument : parameterized.getActualTypeArguments())
                assertExternalType(argument);
            return;
        }
        if (type instanceof GenericArrayType arrayType) {
            assertExternalType(arrayType.getGenericComponentType());
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getLowerBounds())
                assertExternalType(bound);
            for (Type bound : wildcard.getUpperBounds())
                assertExternalType(bound);
            return;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                if (bound != variable)
                    assertExternalType(bound);
            }
        }
    }

    /**
     * Scans compiled implementation references so a JavaFX local/helper dependency
     * cannot evade signature-only reflection checks.
     */
    private static void assertClassDoesNotReferenceJavaFx(Class<?> implementation) throws IOException {
        String resourceName = implementation.getSimpleName() + ".class";
        try (InputStream input = implementation.getResourceAsStream(resourceName)) {
            assertNotNull(input, "Missing implementation bytecode resource: " + resourceName);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0)
                bytes.write(buffer, 0, read);
            String constantPool = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
            assertFalse(constantPool.contains("javafx/"),
                    "JavaFX leaked into ProjectSession implementation bytecode");
        }
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
     * Asserts that both relationship collections agree with the one published preset name.
     */
    private static void assertWholeRelationshipCascade(ProjectSnapshot snapshot) {
        assertEquals(1, snapshot.getSliderPresets().size());
        assertEquals(1, snapshot.getCustomMorphTargets().size());
        assertEquals(1, snapshot.getNpcMorphAssignments().size());
        String presetName = snapshot.getSliderPresets().getFirst().getName();
        assertTrue(presetName.equals("Alpha") || presetName.equals("Beta"));
        assertEquals(Collections.singletonList(presetName),
                snapshot.getCustomMorphTargets().getFirst().getSliderPresetNames());
        assertEquals(Collections.singletonList(presetName),
                snapshot.getNpcMorphAssignments().getFirst().getSliderPresetNames());
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
     * Extracts the ordered Custom Morph Target names exposed by a session snapshot.
     *
     * @param snapshot immutable Project state to inspect
     * @return names in snapshot order
     */
    private static List<String> customMorphTargetNames(ProjectSnapshot snapshot) {
        List<String> names = new ArrayList<>();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets())
            names.add(target.getName());
        return names;
    }

    /**
     * Creates copied NPC source values without assigned Slider Presets for concise
     * ProjectSession behavior tests.
     *
     * @param displayName NPC display name
     * @param pluginName  source plugin name
     * @param editorId    NPC editor ID
     * @return immutable source values ready for an NPC-add edit
     */
    private static NpcMorphAssignmentSnapshot npc(String displayName, String pluginName, String editorId) {
        return new NpcMorphAssignmentSnapshot(displayName, pluginName, editorId, "NordRace", "123456",
                Collections.<String>emptyList());
    }

    /**
     * Extracts ordered NPC plugin/editor identities from a Project snapshot.
     *
     * @param snapshot immutable Project state to inspect
     * @return identities in snapshot order
     */
    private static List<String> npcIdentities(ProjectSnapshot snapshot) {
        List<String> identities = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot assignment : snapshot.getNpcMorphAssignments())
            identities.add(assignment.getPluginName() + "/" + assignment.getEditorId());
        return identities;
    }

    /**
     * Returns one NPC's immutable assigned Slider Preset names by editor ID.
     *
     * @param snapshot immutable Project state to inspect
     * @param editorId editor ID to resolve without regard to case
     * @return immutable assigned Slider Preset names
     * @throws AssertionError when no NPC has the requested editor ID
     */
    private static List<String> npcAssignments(ProjectSnapshot snapshot, String editorId) {
        for (NpcMorphAssignmentSnapshot assignment : snapshot.getNpcMorphAssignments()) {
            if (assignment.getEditorId().equalsIgnoreCase(editorId))
                return assignment.getSliderPresetNames();
        }
        throw new AssertionError("Missing NPC Morph Assignment: " + editorId);
    }

    /**
     * Asserts the common structured naming rejection contract.
     *
     * @param outcome  rejected session operation
     * @param code     expected stable diagnostic code
     * @param snapshot exact prior snapshot that must be preserved
     */
    private static void assertRejectedWithCode(ProjectOutcome outcome, String code, ProjectSnapshot snapshot) {
        assertInstanceOf(RejectedOutcome.class, outcome);
        assertSame(snapshot, outcome.getSnapshot());
        assertEquals(code, outcome.getDiagnostics().getFirst().getCode());
        assertEquals(DiagnosticSeverity.ERROR, outcome.getDiagnostics().getFirst().getSeverity());
        assertEquals("slider-preset.name",
                outcome.getDiagnostics().getFirst().getSourceLocation().getElement().get());
    }

    /**
     * Asserts the structured Custom Morph Target naming rejection contract.
     *
     * @param outcome  rejected session operation
     * @param code     expected stable diagnostic code
     * @param snapshot exact prior snapshot that must be preserved
     */
    private static void assertCustomMorphTargetRejected(ProjectOutcome outcome, String code,
                                                        ProjectSnapshot snapshot) {
        assertInstanceOf(RejectedOutcome.class, outcome);
        assertSame(snapshot, outcome.getSnapshot());
        assertEquals(code, outcome.getDiagnostics().getFirst().getCode());
        assertEquals(DiagnosticSeverity.ERROR, outcome.getDiagnostics().getFirst().getSeverity());
        assertEquals("custom-morph-target.name",
                outcome.getDiagnostics().getFirst().getSourceLocation().getElement().get());
    }

    /**
     * Asserts the common structured NPC identity rejection contract.
     *
     * @param outcome  rejected session operation
     * @param code     expected stable diagnostic code
     * @param snapshot exact prior snapshot that must be preserved
     */
    private static void assertNpcRejected(ProjectOutcome outcome, String code, ProjectSnapshot snapshot) {
        assertInstanceOf(RejectedOutcome.class, outcome);
        assertSame(snapshot, outcome.getSnapshot());
        assertEquals(code, outcome.getDiagnostics().getFirst().getCode());
        assertEquals(DiagnosticSeverity.ERROR, outcome.getDiagnostics().getFirst().getSeverity());
        assertEquals("npc-morph-assignment.identity",
                outcome.getDiagnostics().getFirst().getSourceLocation().getElement().get());
    }

    /**
     * Publishes empty profiles so session unit tests synthesize only choices they explicitly request.
     */
    @BeforeEach
    void initializeEmptySettings() {
        SettingsTestSupport.installDefaults(Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Restores the checked-in Settings pair after each process-wide session test.
     */
    @AfterEach
    void restoreSettings() {
        SettingsTestSupport.restoreRepositorySettings();
    }

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
        assertInstanceOf(ChangedOutcome.class, outcome);
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
     * Guards the identity rule behind New Project: a repeated New Project on a
     * pristine untitled Project is Unchanged, while a New Project after an edit
     * that merely emptied the Project again is Changed, because that Project is
     * dirty and must be replaced by a clean one.
     */
    @Test
    void repeatedNewProjectIsUnchangedUntilTheProjectIsEdited() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot first = session.newProject().getSnapshot();

        ProjectOutcome repeated = session.newProject();
        assertInstanceOf(UnchangedOutcome.class, repeated);
        assertSame(first, repeated.getSnapshot());
        assertSame(first, session.getSnapshot());

        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.clear());
        assertTrue(session.getSnapshot().isDirty());
        assertTrue(session.getSnapshot().getSliderPresets().isEmpty());

        ProjectOutcome replaced = session.newProject();
        assertInstanceOf(ChangedOutcome.class, replaced);
        assertNotSame(first, replaced.getSnapshot());
        assertFalse(replaced.getSnapshot().isDirty());
        assertSame(replaced.getSnapshot(), session.getSnapshot());
    }

    /**
     * Project content versions advance only when the session publishes changed Project content.
     */
    @Test
    void contentVersionAdvancesForNewAndContentMutationButNotForUnchangedOrRejectedWork() {
        ProjectSession session = ProjectSessions.create();
        ProjectContentVersion before = session.getSnapshot().getContentVersion();

        ProjectOutcome created = session.newProject();
        ProjectContentVersion newProjectVersion = created.getSnapshot().getContentVersion();
        ProjectOutcome repeatedNew = session.newProject();
        ProjectOutcome edited = session.apply(SliderPresetEdits.create("Athletic"));
        ProjectContentVersion editedVersion = edited.getSnapshot().getContentVersion();
        ProjectOutcome duplicate = session.apply(SliderPresetEdits.create("athletic"));

        assertFalse(before.equals(newProjectVersion));
        assertEquals(newProjectVersion, repeatedNew.getSnapshot().getContentVersion());
        assertFalse(newProjectVersion.equals(editedVersion));
        assertEquals(editedVersion, duplicate.getSnapshot().getContentVersion());
    }

    /**
     * Content-version equality remains scoped to one ProjectSession even at matching transition counts.
     */
    @Test
    void contentVersionsFromDifferentSessionsNeverCompareEqual() {
        ProjectSession first = ProjectSessions.create();
        ProjectSession second = ProjectSessions.create();

        ProjectContentVersion firstVersion = first.newProject().getSnapshot().getContentVersion();
        ProjectContentVersion secondVersion = second.newProject().getSnapshot().getContentVersion();

        assertFalse(firstVersion.equals(secondVersion));
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
                Optional.of(Path.of("project.jbs2bg")), true, ProjectLifecycleStatus.FILE_BACKED);

        choices.clear();
        targetAssignments.clear();
        npcAssignments.clear();
        presets.clear();
        targets.clear();
        npcs.clear();

        assertEquals("CBBE Curvy", snapshot.getSliderPresets().getFirst().getName());
        assertEquals(1, snapshot.getSliderPresets().getFirst().getSliderChoices().size());
        assertEquals(20, snapshot.getSliderPresets().getFirst().getSliderChoices().getFirst().getEffectiveSmallValue());
        assertEquals(Arrays.asList("CBBE Curvy"),
                snapshot.getCustomMorphTargets().getFirst().getSliderPresetNames());
        assertEquals("Skyrim.esm", snapshot.getNpcMorphAssignments().getFirst().getPluginName());
        assertEquals(Arrays.asList("CBBE Curvy"),
                snapshot.getNpcMorphAssignments().getFirst().getSliderPresetNames());
        assertTrue(snapshot.getFileIdentity().get().isAbsolute());

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getSliderPresets().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getSliderPresets().getFirst().getSliderChoices().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getCustomMorphTargets().getFirst().getSliderPresetNames().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getNpcMorphAssignments().getFirst().getSliderPresetNames().clear());
    }

    /**
     * Verifies that callers can distinguish all operation outcomes while consuming
     * one immutable snapshot and structured diagnostic contract.
     */
    @Test
    void outcomesAreDistinctAndCarryImmutableDiagnosticsWithTheirSnapshot() {
        ProjectSnapshot snapshot = ProjectSessions.create().getSnapshot();
        SourceLocation location = new SourceLocation(Optional.of(Path.of("broken.jbs2bg")),
                Optional.of("/SliderPresets/CBBE Curvy"), OptionalInt.of(4), OptionalInt.of(12));
        ProjectDiagnostic diagnostic = new ProjectDiagnostic("PROJECT_INVALID", DiagnosticSeverity.ERROR,
                location, "The Project contains an invalid Slider Preset.");
        List<ProjectDiagnostic> diagnostics = new ArrayList<>(Arrays.asList(diagnostic));

        List<ProjectOutcome> outcomes = Arrays.asList(new ChangedOutcome(snapshot, diagnostics),
                new UnchangedOutcome(snapshot, diagnostics), new RejectedOutcome(snapshot, diagnostics),
                new FailedOutcome(snapshot, diagnostics));
        diagnostics.clear();

        assertEquals(ChangedOutcome.class, outcomes.getFirst().getClass());
        assertEquals(UnchangedOutcome.class, outcomes.get(1).getClass());
        assertEquals(RejectedOutcome.class, outcomes.get(2).getClass());
        assertEquals(FailedOutcome.class, outcomes.get(3).getClass());
        for (ProjectOutcome outcome : outcomes) {
            assertSame(snapshot, outcome.getSnapshot());
            assertEquals(1, outcome.getDiagnostics().size());
            assertThrows(UnsupportedOperationException.class, () -> outcome.getDiagnostics().clear());
        }

        ProjectDiagnostic exposed = outcomes.getFirst().getDiagnostics().getFirst();
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

        assertInstanceOf(RejectedOutcome.class, outcome);
        assertSame(before, outcome.getSnapshot());
        assertSame(before, session.getSnapshot());
        assertEquals("PROJECT_EDIT_UNSUPPORTED", outcome.getDiagnostics().getFirst().getCode());
        assertEquals("project-edit", outcome.getDiagnostics().getFirst().getSourceLocation().getElement().get());
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

        assertInstanceOf(ChangedOutcome.class, first);
        assertInstanceOf(ChangedOutcome.class, second);
        assertEquals(Arrays.asList("Alpha", "beta"), sliderPresetNames(second.getSnapshot()));
        assertFalse(second.getSnapshot().getSliderPresets().getFirst().isUunp());
        assertTrue(second.getSnapshot().getSliderPresets().getFirst().getSliderChoices().isEmpty());
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
     * Verifies that creating Custom Morph Targets accepts BodyGen condition syntax,
     * normalizes names, orders snapshots, and dirties the Project.
     */
    @Test
    void creatingCustomMorphTargetsTrimsNamesOrdersSnapshotsAndMarksDirty() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();

        ProjectOutcome first = session.apply(CustomMorphTargetEdits.create(" zeta|Female "));
        ProjectOutcome second = session.apply(CustomMorphTargetEdits.create("Alpha"));

        assertInstanceOf(ChangedOutcome.class, first);
        assertInstanceOf(ChangedOutcome.class, second);
        assertEquals(Arrays.asList("Alpha", "zeta|Female"), customMorphTargetNames(second.getSnapshot()));
        assertTrue(second.getSnapshot().getCustomMorphTargets().getFirst().getSliderPresetNames().isEmpty());
        assertTrue(second.getSnapshot().isDirty());
        assertSame(second.getSnapshot(), session.getSnapshot());
    }

    /**
     * Creates a Custom Morph Target and its caller-selected Slider Preset
     * relationships in one atomic edit, rejecting the whole request when any
     * relationship endpoint is invalid.
     */
    @Test
    void creatingCustomMorphTargetWithAssignmentsIsAtomic() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Zulu"));
        session.apply(SliderPresetEdits.create("alpha"));
        ProjectSnapshot beforeCreate = session.getSnapshot();

        ProjectOutcome created = session.apply(
                CustomMorphTargetEdits.create(" All|Female ", Arrays.asList("zulu", "ALPHA")));

        assertInstanceOf(ChangedOutcome.class, created);
        assertEquals(Arrays.asList("alpha", "Zulu"),
                created.getSnapshot().getCustomMorphTargets().getFirst().getSliderPresetNames());

        ProjectOutcome rejected = session.apply(
                CustomMorphTargetEdits.create("All|Male", Arrays.asList("Alpha", "missing")));

        assertRejectedWithCode(rejected, ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND,
                created.getSnapshot());
        assertEquals(Arrays.asList("All|Female"), customMorphTargetNames(session.getSnapshot()));
        assertNotSame(beforeCreate, created.getSnapshot());
    }

    /**
     * Verifies that empty and duplicate Custom Morph Target names are rejected
     * without changing the snapshot or its truthful dirty state.
     */
    @Test
    void invalidAndDuplicateCustomMorphTargetCreationPreservesSnapshotAndDirtyState() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot clean = session.newProject().getSnapshot();

        ProjectOutcome omitted = session.apply(CustomMorphTargetEdits.create(null));
        ProjectOutcome blank = session.apply(CustomMorphTargetEdits.create("   "));

        assertCustomMorphTargetRejected(omitted, ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_REQUIRED, clean);
        assertCustomMorphTargetRejected(blank, ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_REQUIRED, clean);
        assertFalse(clean.isDirty());

        ProjectSnapshot dirty = session.apply(CustomMorphTargetEdits.create("All|Female")).getSnapshot();
        ProjectOutcome duplicate = session.apply(CustomMorphTargetEdits.create(" all|FEMALE "));

        assertCustomMorphTargetRejected(duplicate,
                ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_DUPLICATE, dirty);
        assertTrue(dirty.isDirty());
        assertEquals(Arrays.asList("All|Female"), customMorphTargetNames(dirty));
    }

    /**
     * Verifies that Slider Preset assignments resolve case-insensitively, retain
     * canonical display names and order, and reject duplicate work as a no-op.
     */
    @Test
    void addingCustomMorphTargetAssignmentsOrdersNamesAndTreatsDuplicatesAsUnchanged() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Zulu"));
        session.apply(SliderPresetEdits.create("alpha"));
        session.apply(CustomMorphTargetEdits.create("All|Female"));

        ProjectOutcome first = session.apply(CustomMorphTargetEdits.addSliderPreset("all|FEMALE", "zulu"));
        ProjectOutcome second = session.apply(CustomMorphTargetEdits.addSliderPreset("All|Female", "ALPHA"));
        ProjectSnapshot assigned = second.getSnapshot();

        assertInstanceOf(ChangedOutcome.class, first);
        assertInstanceOf(ChangedOutcome.class, second);
        assertEquals(Arrays.asList("alpha", "Zulu"),
                assigned.getCustomMorphTargets().getFirst().getSliderPresetNames());
        assertTrue(assigned.isDirty());

        ProjectOutcome duplicate = session.apply(
                CustomMorphTargetEdits.addSliderPreset("ALL|female", "Alpha"));

        assertInstanceOf(UnchangedOutcome.class, duplicate);
        assertSame(assigned, duplicate.getSnapshot());
        assertSame(assigned, session.getSnapshot());
    }

    /**
     * Adds a caller-selected Slider Preset batch to one Custom Morph Target with
     * one atomic publication and no partial change when any endpoint is invalid.
     */
    @Test
    void addingMultipleCustomMorphTargetAssignmentsIsAtomic() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Zulu"));
        session.apply(SliderPresetEdits.create("alpha"));
        session.apply(CustomMorphTargetEdits.create("All|Female"));

        ProjectOutcome added = session.apply(CustomMorphTargetEdits.addSliderPresets("all|FEMALE",
                Arrays.asList("zulu", "ALPHA", "alpha")));

        assertInstanceOf(ChangedOutcome.class, added);
        assertEquals(Arrays.asList("alpha", "Zulu"),
                added.getSnapshot().getCustomMorphTargets().getFirst().getSliderPresetNames());

        ProjectOutcome rejected = session.apply(CustomMorphTargetEdits.addSliderPresets("All|Female",
                Arrays.asList("Alpha", "missing")));

        assertRejectedWithCode(rejected, ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, added.getSnapshot());
        assertSame(added.getSnapshot(), session.getSnapshot());
    }

    /**
     * Verifies that assignment removal and clearing change only existing
     * relationships and preserve the exact snapshot for idempotent no-ops.
     */
    @Test
    void removingAndClearingCustomMorphTargetAssignmentsReportsTruthfulOutcomes() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("beta"));
        session.apply(CustomMorphTargetEdits.create("All|Female"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("All|Female", "Alpha"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("All|Female", "beta"));

        ProjectOutcome removed = session.apply(
                CustomMorphTargetEdits.removeSliderPreset("all|female", "ALPHA"));
        assertInstanceOf(ChangedOutcome.class, removed);
        assertEquals(Arrays.asList("beta"),
                removed.getSnapshot().getCustomMorphTargets().getFirst().getSliderPresetNames());

        ProjectOutcome missing = session.apply(
                CustomMorphTargetEdits.removeSliderPreset("All|Female", "Alpha"));
        assertInstanceOf(UnchangedOutcome.class, missing);
        assertSame(removed.getSnapshot(), missing.getSnapshot());

        ProjectOutcome cleared = session.apply(CustomMorphTargetEdits.clearSliderPresets("ALL|FEMALE"));
        assertInstanceOf(ChangedOutcome.class, cleared);
        assertTrue(cleared.getSnapshot().getCustomMorphTargets().getFirst().getSliderPresetNames().isEmpty());

        ProjectOutcome alreadyEmpty = session.apply(CustomMorphTargetEdits.clearSliderPresets("All|Female"));
        assertInstanceOf(UnchangedOutcome.class, alreadyEmpty);
        assertSame(cleared.getSnapshot(), alreadyEmpty.getSnapshot());
        assertTrue(alreadyEmpty.getSnapshot().isDirty());
    }

    /**
     * Verifies that Custom Morph Target delete and clear operations update only
     * existing targets and preserve truthful rejected and unchanged outcomes.
     */
    @Test
    void deletingAndClearingCustomMorphTargetsReportsTruthfulOutcomes() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(CustomMorphTargetEdits.create("Alpha"));
        session.apply(CustomMorphTargetEdits.create("beta|Female"));

        ProjectOutcome deleted = session.apply(CustomMorphTargetEdits.delete("ALPHA"));
        assertInstanceOf(ChangedOutcome.class, deleted);
        assertEquals(Arrays.asList("beta|Female"), customMorphTargetNames(deleted.getSnapshot()));

        ProjectOutcome missing = session.apply(CustomMorphTargetEdits.delete("missing"));
        assertCustomMorphTargetRejected(missing, ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NOT_FOUND,
                deleted.getSnapshot());

        ProjectOutcome cleared = session.apply(CustomMorphTargetEdits.clear());
        assertInstanceOf(ChangedOutcome.class, cleared);
        assertTrue(cleared.getSnapshot().getCustomMorphTargets().isEmpty());
        assertTrue(cleared.getSnapshot().isDirty());

        ProjectOutcome alreadyEmpty = session.apply(CustomMorphTargetEdits.clear());
        assertInstanceOf(UnchangedOutcome.class, alreadyEmpty);
        assertSame(cleared.getSnapshot(), alreadyEmpty.getSnapshot());
    }

    /**
     * Verifies that promotion from the NPC Database copies source values and uses
     * only plugin name plus editor ID, without regard to case, as Project identity.
     */
    @Test
    void addingNpcCopiesSourceAndTreatsPluginAndEditorIdAsIdentity() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        List<String> callerAssignments = new ArrayList<>(Arrays.asList("alpha"));
        NpcMorphAssignmentSnapshot source = new NpcMorphAssignmentSnapshot("Lydia", "Skyrim.esm",
                "HousecarlWhiterun", "NordRace", "A2C94", callerAssignments);

        ProjectOutcome added = session.apply(NpcMorphAssignmentEdits.addNpc(source));
        callerAssignments.clear();

        assertInstanceOf(ChangedOutcome.class, added);
        NpcMorphAssignmentSnapshot assignment = added.getSnapshot().getNpcMorphAssignments().getFirst();
        assertNotSame(source, assignment);
        assertEquals("Lydia", assignment.getDisplayName());
        assertEquals("Skyrim.esm", assignment.getPluginName());
        assertEquals("HousecarlWhiterun", assignment.getEditorId());
        assertEquals("NordRace", assignment.getRace());
        assertEquals("A2C94", assignment.getFormId());
        assertEquals(Arrays.asList("Alpha"), assignment.getSliderPresetNames());

        NpcMorphAssignmentSnapshot sameIdentity = new NpcMorphAssignmentSnapshot("Different Display",
                "SKYRIM.ESM", "housecarlwhiterun", "BretonRace", "FFFFFF",
                new ArrayList<String>());
        ProjectOutcome duplicate = session.apply(NpcMorphAssignmentEdits.addNpc(sameIdentity));

        assertInstanceOf(RejectedOutcome.class, duplicate);
        assertSame(added.getSnapshot(), duplicate.getSnapshot());
        assertSame(duplicate.getSnapshot(), session.getSnapshot());
        assertEquals(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE,
                duplicate.getDiagnostics().getFirst().getCode());
        assertEquals("Lydia", duplicate.getSnapshot().getNpcMorphAssignments().getFirst().getDisplayName());
        assertEquals("A2C94", duplicate.getSnapshot().getNpcMorphAssignments().getFirst().getFormId());
    }

    /**
     * Verifies that case-insensitive NPC identity equality remains safe for caller
     * hash collections even for Unicode characters with asymmetric lowercase forms.
     */
    @Test
    void npcIdentityHashCodeMatchesCaseInsensitiveEquality() {
        NpcMorphAssignmentIdentity latinCapitalI = new NpcMorphAssignmentIdentity("I.esm", "EDITOR");
        NpcMorphAssignmentIdentity dotlessLowerI = new NpcMorphAssignmentIdentity("\u0131.esm", "editor");

        assertEquals(latinCapitalI, dotlessLowerI);
        assertEquals(latinCapitalI.hashCode(), dotlessLowerI.hashCode());
    }

    /**
     * Verifies that NPC assignment edits resolve identity and Slider Presets
     * case-insensitively while preserving unique canonical relationship order.
     */
    @Test
    void npcSliderPresetAssignmentEditsAreUniqueCanonicalAndTruthful() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Zulu"));
        session.apply(SliderPresetEdits.create("alpha"));
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Lydia", "Skyrim.esm", "HousecarlWhiterun")));
        NpcMorphAssignmentIdentity lydia = new NpcMorphAssignmentIdentity("SKYRIM.ESM", "housecarlwhiterun");

        ProjectOutcome first = session.apply(NpcMorphAssignmentEdits.addSliderPreset(lydia, "zulu"));
        ProjectOutcome second = session.apply(NpcMorphAssignmentEdits.addSliderPreset(lydia, "ALPHA"));
        ProjectOutcome duplicate = session.apply(NpcMorphAssignmentEdits.addSliderPreset(lydia, "alpha"));

        assertInstanceOf(ChangedOutcome.class, first);
        assertInstanceOf(ChangedOutcome.class, second);
        assertEquals(Arrays.asList("alpha", "Zulu"),
                second.getSnapshot().getNpcMorphAssignments().getFirst().getSliderPresetNames());
        assertInstanceOf(UnchangedOutcome.class, duplicate);
        assertSame(second.getSnapshot(), duplicate.getSnapshot());

        ProjectOutcome removed = session.apply(NpcMorphAssignmentEdits.removeSliderPreset(lydia, "ALPHA"));
        ProjectOutcome alreadyRemoved = session.apply(NpcMorphAssignmentEdits.removeSliderPreset(lydia, "alpha"));
        ProjectOutcome cleared = session.apply(NpcMorphAssignmentEdits.clearSliderPresets(lydia));
        ProjectOutcome alreadyEmpty = session.apply(NpcMorphAssignmentEdits.clearSliderPresets(lydia));

        assertInstanceOf(ChangedOutcome.class, removed);
        assertEquals(Arrays.asList("Zulu"),
                removed.getSnapshot().getNpcMorphAssignments().getFirst().getSliderPresetNames());
        assertInstanceOf(UnchangedOutcome.class, alreadyRemoved);
        assertSame(removed.getSnapshot(), alreadyRemoved.getSnapshot());
        assertInstanceOf(ChangedOutcome.class, cleared);
        assertTrue(cleared.getSnapshot().getNpcMorphAssignments().getFirst().getSliderPresetNames().isEmpty());
        assertInstanceOf(UnchangedOutcome.class, alreadyEmpty);
        assertSame(cleared.getSnapshot(), alreadyEmpty.getSnapshot());
    }

    /**
     * Adds several Slider Preset relationships to one NPC Morph Assignment with
     * one atomic publication and no partial state when validation fails.
     */
    @Test
    void addingMultipleNpcSliderPresetAssignmentsIsAtomic() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Zulu"));
        session.apply(SliderPresetEdits.create("alpha"));
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Lydia", "Skyrim.esm", "HousecarlWhiterun")));
        NpcMorphAssignmentIdentity lydia = new NpcMorphAssignmentIdentity("SKYRIM.ESM", "housecarlwhiterun");

        ProjectOutcome added = session.apply(NpcMorphAssignmentEdits.addSliderPresets(lydia,
                Arrays.asList("zulu", "ALPHA", "alpha")));

        assertInstanceOf(ChangedOutcome.class, added);
        assertEquals(Arrays.asList("alpha", "Zulu"),
                added.getSnapshot().getNpcMorphAssignments().getFirst().getSliderPresetNames());

        ProjectOutcome rejected = session.apply(NpcMorphAssignmentEdits.addSliderPresets(lydia,
                Arrays.asList("Alpha", "missing")));

        assertRejectedWithCode(rejected, ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, added.getSnapshot());
        assertSame(added.getSnapshot(), session.getSnapshot());
    }

    /**
     * Verifies canonical plugin/editor ordering and truthful single, filtered-batch,
     * and clear-all NPC Morph Assignment removal behavior.
     */
    @Test
    void npcRemovalAndClearEditsPreserveCanonicalOrderingAndNoOps() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Zulu", "Skyrim.esm", "ZuluEditor")));
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Dawnguard", "Dawnguard.esm", "ZuluEditor")));
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Alpha", "skyrim.ESM", "AlphaEditor")));

        assertEquals(Arrays.asList("Dawnguard.esm/ZuluEditor", "skyrim.ESM/AlphaEditor",
                "Skyrim.esm/ZuluEditor"), npcIdentities(session.getSnapshot()));

        ProjectOutcome removed = session.apply(NpcMorphAssignmentEdits.removeNpc(
                new NpcMorphAssignmentIdentity("SKYRIM.ESM", "zulueditor")));
        ProjectOutcome missing = session.apply(NpcMorphAssignmentEdits.removeNpc(
                new NpcMorphAssignmentIdentity("Skyrim.esm", "Missing")));
        ProjectOutcome filtered = session.apply(NpcMorphAssignmentEdits.removeNpcs(Arrays.asList(
                new NpcMorphAssignmentIdentity("DAWNGUARD.ESM", "zulueditor"))));
        ProjectOutcome noMatches = session.apply(NpcMorphAssignmentEdits.removeNpcs(Arrays.asList(
                new NpcMorphAssignmentIdentity("Missing.esm", "Missing"))));

        assertInstanceOf(ChangedOutcome.class, removed);
        assertEquals(Arrays.asList("Dawnguard.esm/ZuluEditor", "skyrim.ESM/AlphaEditor"),
                npcIdentities(removed.getSnapshot()));
        assertNpcRejected(missing, ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_NOT_FOUND, removed.getSnapshot());
        assertInstanceOf(ChangedOutcome.class, filtered);
        assertEquals(Arrays.asList("skyrim.ESM/AlphaEditor"), npcIdentities(filtered.getSnapshot()));
        assertInstanceOf(UnchangedOutcome.class, noMatches);
        assertSame(filtered.getSnapshot(), noMatches.getSnapshot());

        ProjectOutcome cleared = session.apply(NpcMorphAssignmentEdits.clearNpcs());
        ProjectOutcome alreadyEmpty = session.apply(NpcMorphAssignmentEdits.clearNpcs());

        assertInstanceOf(ChangedOutcome.class, cleared);
        assertTrue(cleared.getSnapshot().getNpcMorphAssignments().isEmpty());
        assertInstanceOf(UnchangedOutcome.class, alreadyEmpty);
        assertSame(cleared.getSnapshot(), alreadyEmpty.getSnapshot());
    }

    /**
     * Verifies that filtered bulk NPC promotion defensively captures caller input,
     * skips existing identities, and validates the complete candidate before publish.
     */
    @Test
    void filteredBulkNpcAddIsDefensivelyCopiedAndAtomicOnRejection() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        List<NpcMorphAssignmentSnapshot> filteredSources = new ArrayList<>(Arrays.asList(
                new NpcMorphAssignmentSnapshot("Beta", "Skyrim.esm", "BetaEditor", "NordRace", "100001",
                        Arrays.asList("alpha")),
                npc("Gamma", "Skyrim.esm", "GammaEditor")));
        ProjectEdit request = NpcMorphAssignmentEdits.addNpcs(filteredSources);
        filteredSources.clear();

        ProjectOutcome added = session.apply(request);

        assertInstanceOf(ChangedOutcome.class, added);
        assertEquals(Arrays.asList("Skyrim.esm/BetaEditor", "Skyrim.esm/GammaEditor"),
                npcIdentities(added.getSnapshot()));
        assertEquals(Arrays.asList("Alpha"),
                added.getSnapshot().getNpcMorphAssignments().getFirst().getSliderPresetNames());

        ProjectOutcome duplicates = session.apply(NpcMorphAssignmentEdits.addNpcs(Arrays.asList(
                npc("Changed", "SKYRIM.ESM", "betaeditor"),
                npc("Changed", "SKYRIM.ESM", "gammaeditor"))));
        assertInstanceOf(UnchangedOutcome.class, duplicates);
        assertSame(added.getSnapshot(), duplicates.getSnapshot());

        NpcMorphAssignmentSnapshot valid = npc("Delta", "Skyrim.esm", "DeltaEditor");
        NpcMorphAssignmentSnapshot invalid = new NpcMorphAssignmentSnapshot("Epsilon", "Skyrim.esm",
                "EpsilonEditor", "NordRace", "100002", Arrays.asList("Missing Preset"));
        ProjectSnapshot beforeRejectedBatch = session.getSnapshot();

        ProjectOutcome rejected = session.apply(NpcMorphAssignmentEdits.addNpcs(Arrays.asList(valid, invalid)));

        assertInstanceOf(RejectedOutcome.class, rejected);
        assertSame(beforeRejectedBatch, rejected.getSnapshot());
        assertSame(beforeRejectedBatch, session.getSnapshot());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, rejected.getDiagnostics().getFirst().getCode());
        assertEquals(Arrays.asList("Skyrim.esm/BetaEditor", "Skyrim.esm/GammaEditor"),
                npcIdentities(rejected.getSnapshot()));
    }

    /**
     * Verifies that fill-empty consumes explicit caller-owned choices, preserves
     * non-empty NPCs, and rejects a mixed-validity choice set without partial fill.
     */
    @Test
    void fillEmptyUsesExplicitChoicesAndPublishesAtomically() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("beta"));
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("First", "Skyrim.esm", "FirstEditor")));
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Second", "Skyrim.esm", "SecondEditor")));
        session.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot("Occupied", "Skyrim.esm",
                "OccupiedEditor", "NordRace", "100003", Arrays.asList("Alpha"))));
        List<NpcSliderPresetChoice> callerChoices = new ArrayList<>(Arrays.asList(
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("SKYRIM.ESM", "firsteditor"), "BETA"),
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("Skyrim.esm", "occupiededitor"), "beta"),
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("Skyrim.esm", "secondeditor"), "alpha")));
        ProjectEdit fillRequest = NpcMorphAssignmentEdits.fillEmpty(callerChoices);
        callerChoices.clear();

        ProjectOutcome filled = session.apply(fillRequest);

        assertInstanceOf(ChangedOutcome.class, filled);
        assertEquals(Arrays.asList("beta"), npcAssignments(filled.getSnapshot(), "FirstEditor"));
        assertEquals(Arrays.asList("Alpha"), npcAssignments(filled.getSnapshot(), "OccupiedEditor"));
        assertEquals(Arrays.asList("Alpha"), npcAssignments(filled.getSnapshot(), "SecondEditor"));

        ProjectOutcome noEligible = session.apply(NpcMorphAssignmentEdits.fillEmpty(Arrays.asList(
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("Skyrim.esm", "firsteditor"), "Alpha"))));
        assertInstanceOf(UnchangedOutcome.class, noEligible);
        assertSame(filled.getSnapshot(), noEligible.getSnapshot());

        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Third", "Skyrim.esm", "ThirdEditor")));
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Fourth", "Skyrim.esm", "FourthEditor")));
        ProjectSnapshot beforeRejectedFill = session.getSnapshot();
        ProjectOutcome rejected = session.apply(NpcMorphAssignmentEdits.fillEmpty(Arrays.asList(
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("Skyrim.esm", "thirdeditor"), "Alpha"),
                new NpcSliderPresetChoice(new NpcMorphAssignmentIdentity("Skyrim.esm", "fourtheditor"),
                        "Missing Preset"))));

        assertInstanceOf(RejectedOutcome.class, rejected);
        assertSame(beforeRejectedFill, rejected.getSnapshot());
        assertSame(beforeRejectedFill, session.getSnapshot());
        assertTrue(npcAssignments(rejected.getSnapshot(), "ThirdEditor").isEmpty());
        assertTrue(npcAssignments(rejected.getSnapshot(), "FourthEditor").isEmpty());
    }

    /**
     * Verifies that a filtered assignment-clear action is one defensively copied,
     * fully validated edit rather than a sequence of partially visible mutations.
     */
    @Test
    void filteredNpcAssignmentClearIsDefensivelyCopiedAndAtomic() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot("First", "Skyrim.esm",
                "FirstEditor", "NordRace", "100004", Arrays.asList("Alpha"))));
        session.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot("Second", "Skyrim.esm",
                "SecondEditor", "NordRace", "100005", Arrays.asList("Alpha"))));
        List<NpcMorphAssignmentIdentity> filtered = new ArrayList<>(Arrays.asList(
                new NpcMorphAssignmentIdentity("SKYRIM.ESM", "firsteditor")));
        ProjectEdit clearRequest = NpcMorphAssignmentEdits.clearSliderPresetsForNpcs(filtered);
        filtered.clear();

        ProjectOutcome cleared = session.apply(clearRequest);

        assertInstanceOf(ChangedOutcome.class, cleared);
        assertTrue(npcAssignments(cleared.getSnapshot(), "FirstEditor").isEmpty());
        assertEquals(Arrays.asList("Alpha"), npcAssignments(cleared.getSnapshot(), "SecondEditor"));

        ProjectOutcome alreadyEmpty = session.apply(NpcMorphAssignmentEdits.clearSliderPresetsForNpcs(Arrays.asList(
                new NpcMorphAssignmentIdentity("Skyrim.esm", "firsteditor"))));
        assertInstanceOf(UnchangedOutcome.class, alreadyEmpty);
        assertSame(cleared.getSnapshot(), alreadyEmpty.getSnapshot());

        ProjectSnapshot beforeRejected = session.getSnapshot();
        ProjectOutcome rejected = session.apply(NpcMorphAssignmentEdits.clearSliderPresetsForNpcs(Arrays.asList(
                new NpcMorphAssignmentIdentity("Skyrim.esm", "secondeditor"),
                new NpcMorphAssignmentIdentity("Missing.esm", "MissingEditor"))));

        assertNpcRejected(rejected, ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_NOT_FOUND, beforeRejected);
        assertSame(beforeRejected, session.getSnapshot());
        assertEquals(Arrays.asList("Alpha"), npcAssignments(rejected.getSnapshot(), "SecondEditor"));
    }

    /**
     * Verifies that Slider Preset cascades repair Project NPC relationships while
     * retaining NPC metadata and leaving the independent source value unchanged.
     */
    @Test
    void sliderPresetRenameDeleteAndClearCascadeToNpcAssignmentsOnly() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("beta"));
        NpcMorphAssignmentSnapshot source = new NpcMorphAssignmentSnapshot("Lydia", "Skyrim.esm",
                "HousecarlWhiterun", "NordRace", "A2C94", Arrays.asList("Alpha", "beta"));
        session.apply(NpcMorphAssignmentEdits.addNpc(source));

        ProjectOutcome renamed = session.apply(SliderPresetEdits.rename("ALPHA", " Gamma "));

        assertInstanceOf(ChangedOutcome.class, renamed);
        assertEquals(Arrays.asList("beta", "Gamma"), npcAssignments(renamed.getSnapshot(), "HousecarlWhiterun"));
        NpcMorphAssignmentSnapshot retained = renamed.getSnapshot().getNpcMorphAssignments().getFirst();
        assertEquals("Lydia", retained.getDisplayName());
        assertEquals("NordRace", retained.getRace());
        assertEquals("A2C94", retained.getFormId());
        assertEquals(Arrays.asList("Alpha", "beta"), source.getSliderPresetNames());

        ProjectOutcome deleted = session.apply(SliderPresetEdits.delete("gamma"));
        assertInstanceOf(ChangedOutcome.class, deleted);
        assertEquals(Arrays.asList("beta"), npcAssignments(deleted.getSnapshot(), "HousecarlWhiterun"));
        assertEquals(Arrays.asList("Alpha", "beta"), source.getSliderPresetNames());

        ProjectOutcome cleared = session.apply(SliderPresetEdits.clear());
        assertInstanceOf(ChangedOutcome.class, cleared);
        assertEquals(1, cleared.getSnapshot().getNpcMorphAssignments().size());
        assertTrue(npcAssignments(cleared.getSnapshot(), "HousecarlWhiterun").isEmpty());
        assertEquals(Arrays.asList("Alpha", "beta"), source.getSliderPresetNames());
    }

    /**
     * Verifies that known NPC edits require an active Project and that empty bulk
     * actions preserve the canonical clean New Project snapshot.
     */
    @Test
    void npcEditsRequireActiveProjectAndEmptyBulkActionsRemainClean() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot noProject = session.getSnapshot();

        ProjectOutcome beforeActiveProject = session.apply(NpcMorphAssignmentEdits.clearNpcs());

        assertInstanceOf(RejectedOutcome.class, beforeActiveProject);
        assertSame(noProject, beforeActiveProject.getSnapshot());
        assertEquals(ProjectDiagnosticCodes.ACTIVE_PROJECT_REQUIRED,
                beforeActiveProject.getDiagnostics().getFirst().getCode());

        ProjectSnapshot clean = session.newProject().getSnapshot();
        List<ProjectOutcome> noOps = Arrays.asList(
                session.apply(NpcMorphAssignmentEdits.addNpcs(Collections.<NpcMorphAssignmentSnapshot>emptyList())),
                session.apply(NpcMorphAssignmentEdits.removeNpcs(Collections.<NpcMorphAssignmentIdentity>emptyList())),
                session.apply(NpcMorphAssignmentEdits.clearSliderPresetsForNpcs(
                        Collections.<NpcMorphAssignmentIdentity>emptyList())),
                session.apply(NpcMorphAssignmentEdits.fillEmpty(Collections.<NpcSliderPresetChoice>emptyList())),
                session.apply(NpcMorphAssignmentEdits.clearNpcs()));

        for (ProjectOutcome outcome : noOps) {
            assertInstanceOf(UnchangedOutcome.class, outcome);
            assertSame(clean, outcome.getSnapshot());
            assertFalse(outcome.getSnapshot().isDirty());
        }
        assertSame(clean, session.getSnapshot());
    }

    /**
     * Verifies structured rejection and exact snapshot preservation for unresolved
     * NPC and Slider Preset endpoints supplied to explicit NPC edits.
     */
    @Test
    void invalidNpcEditEndpointsRejectWithoutChangingProjectState() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(NpcMorphAssignmentEdits.addNpc(npc("Lydia", "Skyrim.esm", "HousecarlWhiterun")));
        ProjectSnapshot before = session.getSnapshot();

        ProjectOutcome missingNpc = session.apply(NpcMorphAssignmentEdits.addSliderPreset(
                new NpcMorphAssignmentIdentity("Missing.esm", "MissingEditor"), "Alpha"));
        ProjectOutcome missingPreset = session.apply(NpcMorphAssignmentEdits.addSliderPreset(
                new NpcMorphAssignmentIdentity("Skyrim.esm", "HousecarlWhiterun"), "Missing Preset"));
        ProjectOutcome invalidSourceAssignments = session.apply(NpcMorphAssignmentEdits.addNpc(
                new NpcMorphAssignmentSnapshot("Bad", "Skyrim.esm", "BadEditor", "NordRace", "100006",
                        Arrays.asList("Missing Preset"))));

        assertNpcRejected(missingNpc, ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_NOT_FOUND, before);
        assertInstanceOf(RejectedOutcome.class, missingPreset);
        assertSame(before, missingPreset.getSnapshot());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND,
                missingPreset.getDiagnostics().getFirst().getCode());
        assertInstanceOf(RejectedOutcome.class, invalidSourceAssignments);
        assertSame(before, invalidSourceAssignments.getSnapshot());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND,
                invalidSourceAssignments.getDiagnostics().getFirst().getCode());
        assertSame(before, session.getSnapshot());
    }

    /**
     * Verifies that Slider Preset rename publishes the catalog and all Custom Morph
     * Target relationships together with canonical display names and ordering.
     */
    @Test
    void renamingSliderPresetCascadesToEveryCustomMorphTargetRelationship() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("beta"));
        session.apply(CustomMorphTargetEdits.create("Zeta|Female"));
        session.apply(CustomMorphTargetEdits.create("All|Male"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("Zeta|Female", "Alpha"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("All|Male", "Alpha"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("All|Male", "beta"));

        ProjectOutcome renamed = session.apply(SliderPresetEdits.rename("ALPHA", " Gamma "));
        ProjectSnapshot snapshot = renamed.getSnapshot();

        assertInstanceOf(ChangedOutcome.class, renamed);
        assertEquals(Arrays.asList("beta", "Gamma"), sliderPresetNames(snapshot));
        assertEquals(Arrays.asList("All|Male", "Zeta|Female"), customMorphTargetNames(snapshot));
        assertEquals(Arrays.asList("beta", "Gamma"),
                snapshot.getCustomMorphTargets().getFirst().getSliderPresetNames());
        assertEquals(Arrays.asList("Gamma"),
                snapshot.getCustomMorphTargets().get(1).getSliderPresetNames());
        assertSame(snapshot, session.getSnapshot());
        assertTrue(snapshot.isDirty());
    }

    /**
     * Verifies that Slider Preset delete and clear operations remove every affected
     * Custom Morph Target relationship in the same published snapshot.
     */
    @Test
    void deletingAndClearingSliderPresetsCascadeToEveryCustomMorphTargetRelationship() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("beta"));
        session.apply(CustomMorphTargetEdits.create("All|Female"));
        session.apply(CustomMorphTargetEdits.create("All|Male"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("All|Female", "Alpha"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("All|Female", "beta"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("All|Male", "Alpha"));

        ProjectOutcome deleted = session.apply(SliderPresetEdits.delete("ALPHA"));
        ProjectSnapshot afterDelete = deleted.getSnapshot();

        assertInstanceOf(ChangedOutcome.class, deleted);
        assertEquals(Arrays.asList("beta"), sliderPresetNames(afterDelete));
        assertEquals(Arrays.asList("beta"),
                afterDelete.getCustomMorphTargets().getFirst().getSliderPresetNames());
        assertTrue(afterDelete.getCustomMorphTargets().get(1).getSliderPresetNames().isEmpty());

        ProjectOutcome cleared = session.apply(SliderPresetEdits.clear());
        ProjectSnapshot afterClear = cleared.getSnapshot();

        assertInstanceOf(ChangedOutcome.class, cleared);
        assertTrue(afterClear.getSliderPresets().isEmpty());
        assertTrue(afterClear.getCustomMorphTargets().getFirst().getSliderPresetNames().isEmpty());
        assertTrue(afterClear.getCustomMorphTargets().get(1).getSliderPresetNames().isEmpty());
        assertSame(afterClear, session.getSnapshot());
    }

    /**
     * Verifies that UUNP and slider-choice edits preserve every stored value,
     * order choices canonically, and report repeated values as no-ops. Effective
     * values are the one exception: they always derive from the stored endpoints
     * and the Slider Preset's mode, so a caller-supplied divergent pair is replaced
     * by the mode's Slider settings rather than published.
     */
    @Test
    void uunpAndSliderChoiceEditsPreserveImmutableValueSemantics() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        // The 30/70 effective pair deliberately disagrees with the absent stored endpoints.
        SliderChoiceSnapshot waist = new SliderChoiceSnapshot("Waist", true, null, null, 30, 70, 25, 75, true);
        SliderChoiceSnapshot arms = new SliderChoiceSnapshot("Arms", false, Integer.valueOf(10),
                Integer.valueOf(90), 10, 90, 0, 100, false);

        ProjectOutcome uunp = session.apply(SliderPresetEdits.setUunp("Alpha", true));
        ProjectOutcome firstChoice = session.apply(SliderPresetEdits.setSliderChoice("Alpha", waist));
        ProjectOutcome secondChoice = session.apply(SliderPresetEdits.setSliderChoice("Alpha", arms));
        ProjectSnapshot changed = secondChoice.getSnapshot();

        assertInstanceOf(ChangedOutcome.class, uunp);
        assertInstanceOf(ChangedOutcome.class, firstChoice);
        assertInstanceOf(ChangedOutcome.class, secondChoice);
        SliderPresetSnapshot preset = changed.getSliderPresets().getFirst();
        assertTrue(preset.isUunp());
        assertEquals(Arrays.asList("Arms", "Waist"), sliderChoiceNames(preset));
        SliderChoiceSnapshot exposedWaist = preset.getSliderChoices().get(1);
        assertFalse(exposedWaist.getStoredSmallValue().isPresent());
        assertFalse(exposedWaist.getStoredBigValue().isPresent());
        assertEquals(com.asdasfa.jbs2bg.data.Settings.getDefaultValueSmallUUNP("Waist"),
                exposedWaist.getEffectiveSmallValue());
        assertEquals(com.asdasfa.jbs2bg.data.Settings.getDefaultValueBigUUNP("Waist"),
                exposedWaist.getEffectiveBigValue());
        assertEquals(25, exposedWaist.getPercentageMinimum());
        assertEquals(75, exposedWaist.getPercentageMaximum());
        assertTrue(exposedWaist.isMissingDefault());

        ProjectOutcome unchangedUunp = session.apply(SliderPresetEdits.setUunp("Alpha", true));
        ProjectOutcome unchangedChoice = session.apply(SliderPresetEdits.setSliderChoice("Alpha", waist));

        assertInstanceOf(UnchangedOutcome.class, unchangedUunp);
        assertSame(changed, unchangedUunp.getSnapshot());
        assertInstanceOf(UnchangedOutcome.class, unchangedChoice);
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

        assertInstanceOf(ChangedOutcome.class, duplicated);
        assertEquals(Arrays.asList("Alpha", "beta"), sliderPresetNames(duplicatedSnapshot));
        SliderPresetSnapshot source = duplicatedSnapshot.getSliderPresets().getFirst();
        SliderPresetSnapshot copy = duplicatedSnapshot.getSliderPresets().get(1);
        assertNotSame(source, copy);
        assertTrue(copy.isUunp());
        assertEquals(Arrays.asList("Waist"), sliderChoiceNames(copy));
        assertTrue(copy.getSliderChoices().getFirst().isMissingDefault());

        ProjectSnapshot afterSourceEdit = session.apply(SliderPresetEdits.setUunp("Alpha", false)).getSnapshot();

        assertFalse(afterSourceEdit.getSliderPresets().getFirst().isUunp());
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

        assertInstanceOf(ChangedOutcome.class, caseOnly);
        assertEquals("BRAVO", caseOnly.getSnapshot().getSliderPresets().getFirst().getName());
        assertTrue(caseOnly.getSnapshot().getSliderPresets().getFirst().isUunp());
        assertEquals(Arrays.asList("Waist"),
                sliderChoiceNames(caseOnly.getSnapshot().getSliderPresets().getFirst()));
        assertInstanceOf(ChangedOutcome.class, reordered);
        assertEquals(Arrays.asList("Alpha", "BRAVO"), sliderPresetNames(reordered.getSnapshot()));

        ProjectOutcome unchangedRename = session.apply(SliderPresetEdits.rename("alpha", " Alpha "));
        assertInstanceOf(UnchangedOutcome.class, unchangedRename);
        assertSame(reordered.getSnapshot(), unchangedRename.getSnapshot());

        SliderChoiceSnapshot arms = new SliderChoiceSnapshot("Arms", false, Integer.valueOf(5),
                Integer.valueOf(95), 5, 95, 0, 100, false);
        SliderPresetSnapshot replacement = new SliderPresetSnapshot(" charlie ", false,
                Arrays.asList(waist, arms));
        ProjectOutcome updated = session.apply(SliderPresetEdits.update("bravo", replacement));

        assertInstanceOf(ChangedOutcome.class, updated);
        assertEquals(Arrays.asList("Alpha", "charlie"), sliderPresetNames(updated.getSnapshot()));
        SliderPresetSnapshot updatedPreset = updated.getSnapshot().getSliderPresets().get(1);
        assertFalse(updatedPreset.isUunp());
        assertEquals(Arrays.asList("Arms", "Waist"), sliderChoiceNames(updatedPreset));

        SliderPresetSnapshot sameReplacement = new SliderPresetSnapshot("charlie", false,
                Arrays.asList(arms, waist));
        ProjectOutcome unchangedUpdate = session.apply(SliderPresetEdits.update("CHARLIE", sameReplacement));
        assertInstanceOf(UnchangedOutcome.class, unchangedUpdate);
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
        assertInstanceOf(ChangedOutcome.class, deleted);
        assertEquals(Arrays.asList("beta"), sliderPresetNames(deleted.getSnapshot()));

        ProjectOutcome missing = session.apply(SliderPresetEdits.delete("missing"));
        assertRejectedWithCode(missing, ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND,
                deleted.getSnapshot());

        ProjectOutcome cleared = session.apply(SliderPresetEdits.clear());
        assertInstanceOf(ChangedOutcome.class, cleared);
        assertTrue(cleared.getSnapshot().getSliderPresets().isEmpty());
        assertTrue(cleared.getSnapshot().isDirty());

        ProjectOutcome alreadyEmpty = session.apply(SliderPresetEdits.clear());
        assertInstanceOf(UnchangedOutcome.class, alreadyEmpty);
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

        assertInstanceOf(RejectedOutcome.class, outcome);
        assertSame(before, outcome.getSnapshot());
        assertSame(before, session.getSnapshot());
        assertFalse(before.isDirty());
        assertEquals(ProjectDiagnosticCodes.ACTIVE_PROJECT_REQUIRED,
                outcome.getDiagnostics().getFirst().getCode());
        assertEquals("project", outcome.getDiagnostics().getFirst().getSourceLocation().getElement().get());
    }

    /**
     * Locks the exact external seam and implementation dependencies so no parallel
     * storage interface, legacy mutable Project type, or JavaFX type can reappear.
     *
     * @throws Exception when the contract or implementation bytecode cannot be inspected
     */
    @Test
    void projectSessionContractIsExactAndJavaFxFree() throws Exception {
        assertEquals(ProjectSnapshot.class, ProjectSession.class.getMethod("getSnapshot").getReturnType());
        assertOutcomeMethod("newProject");
        assertOutcomeMethod("open", Path.class);
        assertOutcomeMethod("save");
        assertOutcomeMethod("saveAs", Path.class);
        assertOutcomeMethod("apply", ProjectEdit.class);
        assertEquals(SliderPresetImportOutcome.class,
                ProjectSession.class.getMethod("importSliderPresets", List.class).getReturnType());

        Set<String> methodNames = new HashSet<>();
        for (Method method : ProjectSession.class.getDeclaredMethods()) {
            methodNames.add(method.getName());
            assertExternalType(method.getGenericReturnType());
            for (Type parameterType : method.getGenericParameterTypes())
                assertExternalType(parameterType);
        }
        assertEquals(new HashSet<>(Arrays.asList("getSnapshot", "newProject", "open", "save", "saveAs",
                "importSliderPresets", "apply")), methodNames);

        Class<?> implementation = ProjectSessions.create().getClass();
        assertFalse(Modifier.isPublic(implementation.getModifiers()),
                "ProjectSession implementation must remain hidden behind the external seam");
        for (Field field : implementation.getDeclaredFields())
            assertExternalType(field.getGenericType());
        for (Constructor<?> constructor : implementation.getDeclaredConstructors()) {
            for (Type parameterType : constructor.getGenericParameterTypes())
                assertExternalType(parameterType);
        }
        for (Method method : implementation.getDeclaredMethods()) {
            assertExternalType(method.getGenericReturnType());
            for (Type parameterType : method.getGenericParameterTypes())
                assertExternalType(parameterType);
        }
        assertClassDoesNotReferenceJavaFx(implementation);
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

    /**
     * Races readers against repeated public rename edits and proves every observed
     * snapshot contains one whole referential cascade across both relationship kinds.
     *
     * @throws Exception when a worker cannot complete within the test deadline
     */
    @Test
    void readersObserveWholeRelationshipCascadeSnapshots() throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(CustomMorphTargetEdits.create("All|Female", Collections.singletonList("Alpha")));
        session.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot("Aela", "Skyrim.esm",
                "FemaleNord", "NordRace", "1A696", Collections.singletonList("Alpha"))));

        int readerCount = 6;
        int renameCount = 200;
        int readsPerWorker = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<ProjectSnapshot>>> readers = new ArrayList<>();

        try {
            Future<Void> writer = executor.submit(() -> {
                start.await();
                String currentName = "Alpha";
                for (int iteration = 0; iteration < renameCount; iteration++) {
                    String nextName = currentName.equals("Alpha") ? "Beta" : "Alpha";
                    ProjectOutcome outcome = session.apply(SliderPresetEdits.rename(currentName, nextName));
                    assertInstanceOf(ChangedOutcome.class, outcome);
                    currentName = nextName;
                }
                return null;
            });
            for (int reader = 0; reader < readerCount; reader++) {
                readers.add(executor.submit(() -> {
                    start.await();
                    List<ProjectSnapshot> observed = new ArrayList<>();
                    for (int iteration = 0; iteration < readsPerWorker; iteration++)
                        observed.add(session.getSnapshot());
                    return observed;
                }));
            }

            start.countDown();
            writer.get(10, TimeUnit.SECONDS);
            for (Future<List<ProjectSnapshot>> reader : readers) {
                for (ProjectSnapshot observed : reader.get(10, TimeUnit.SECONDS))
                    assertWholeRelationshipCascade(observed);
            }
            assertWholeRelationshipCascade(session.getSnapshot());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
