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
}
