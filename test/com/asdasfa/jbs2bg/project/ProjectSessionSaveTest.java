package com.asdasfa.jbs2bg.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectSessionSaveTest {

    @TempDir
    Path tempDirectory;

    /** Save requires an active Project before file-identity validation can apply. */
    @Test
    void saveBeforeActiveProjectRejectsAndPreservesTheNoProjectSnapshot() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot before = session.getSnapshot();

        ProjectOutcome outcome = session.save();

        assertTrue(outcome instanceof RejectedOutcome);
        assertSame(before, outcome.getSnapshot());
        assertSame(before, session.getSnapshot());
        assertEquals(ProjectDiagnosticCodes.ACTIVE_PROJECT_REQUIRED,
                outcome.getDiagnostics().get(0).getCode());
        assertEquals("project", outcome.getDiagnostics().get(0).getSourceLocation().getElement().get());
    }

    /**
     * Verifies that Save rejects an untitled Project without changing the latest
     * dirty snapshot.
     */
    @Test
    void saveWithoutFileIdentityRejectsAndPreservesLatestSnapshot() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        ProjectSnapshot dirty = session.apply(SliderPresetEdits.create("Unsaved")).getSnapshot();

        ProjectOutcome outcome = session.save();

        assertTrue(outcome instanceof RejectedOutcome);
        assertSame(dirty, outcome.getSnapshot());
        assertSame(dirty, session.getSnapshot());
        assertEquals(ProjectDiagnosticCodes.FILE_IDENTITY_REQUIRED,
                outcome.getDiagnostics().get(0).getCode());
    }

    /** New Project atomically discards dirty file-backed state and is idempotent thereafter. */
    @Test
    void newProjectReplacesDirtyFileBackedStateAndThenReturnsUnchanged() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.saveAs(tempDirectory.resolve("existing.jbs2bg"));
        ProjectSnapshot dirtyFileBacked = session.apply(SliderPresetEdits.create("Beta")).getSnapshot();

        ProjectOutcome replaced = session.newProject();
        ProjectOutcome repeated = session.newProject();

        assertTrue(dirtyFileBacked.isDirty());
        assertTrue(dirtyFileBacked.getFileIdentity().isPresent());
        assertTrue(replaced instanceof ChangedOutcome);
        assertTrue(replaced.getSnapshot().getSliderPresets().isEmpty());
        assertFalse(replaced.getSnapshot().getFileIdentity().isPresent());
        assertFalse(replaced.getSnapshot().isDirty());
        assertEquals(ProjectLifecycleStatus.UNTITLED, replaced.getSnapshot().getLifecycleStatus());
        assertTrue(repeated instanceof UnchangedOutcome);
        assertSame(replaced.getSnapshot(), repeated.getSnapshot());
        assertSame(repeated.getSnapshot(), session.getSnapshot());
    }

    /**
     * Verifies that Save As persists the current Project before publishing its new
     * clean, file-backed identity.
     */
    @Test
    void successfulSaveAsWritesProjectAndAdoptsFileIdentity() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        ProjectSnapshot dirty = session.apply(SliderPresetEdits.create("Alpha")).getSnapshot();
        Path target = tempDirectory.resolve("saved-project.jbs2bg");

        ProjectOutcome outcome = session.saveAs(target);

        assertTrue(outcome instanceof ChangedOutcome);
        assertSame(outcome.getSnapshot(), session.getSnapshot());
        assertFalse(outcome.getSnapshot().isDirty());
        assertEquals(ProjectLifecycleStatus.FILE_BACKED, outcome.getSnapshot().getLifecycleStatus());
        assertEquals(target.toAbsolutePath().normalize(), outcome.getSnapshot().getFileIdentity().get());
        assertTrue(dirty.isDirty());

        ProjectOutcome reopened = ProjectSessions.create().open(target);
        assertTrue(reopened instanceof ChangedOutcome);
        assertEquals("Alpha", reopened.getSnapshot().getSliderPresets().get(0).getName());
    }

    /**
     * Verifies that Save As succeeds for a legal filename near the filesystem's
     * component-length limit: the staging file derives its name from the target, so
     * an unbounded prefix would push the staging name past the limit and fail every
     * save of a target that is itself valid.
     *
     * @throws Exception when the temporary directory cannot be listed
     */
    @Test
    void saveAsNearFilenameLengthLimitStillStagesAndReplacesAtomically() throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        // 241 characters plus the extension is 248: legal on NTFS and ext4 (255), but
        // leaves no room for a random staging component appended to the full name.
        char[] stem = new char[241];
        java.util.Arrays.fill(stem, 'p');
        Path target = tempDirectory.resolve(new String(stem) + ".jbs2bg");

        ProjectOutcome outcome = session.saveAs(target);

        assertTrue(outcome instanceof ChangedOutcome);
        assertFalse(outcome.getSnapshot().isDirty());
        assertEquals(target.toAbsolutePath().normalize(), outcome.getSnapshot().getFileIdentity().get());
        ProjectOutcome reopened = ProjectSessions.create().open(target);
        assertTrue(reopened instanceof ChangedOutcome);
        assertEquals("Alpha", reopened.getSnapshot().getSliderPresets().get(0).getName());
        try (java.util.stream.Stream<Path> siblings = Files.list(tempDirectory)) {
            assertEquals(1, siblings.count(), "no staging file may be left beside the saved Project");
        }
    }

    /**
     * Verifies that Save writes later Project edits to the adopted identity and
     * clears dirty state only after that persistence succeeds.
     */
    @Test
    void successfulSaveWritesCurrentProjectToAdoptedIdentity() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        Path target = tempDirectory.resolve("adopted-project.jbs2bg");
        session.saveAs(target);
        ProjectSnapshot dirty = session.apply(SliderPresetEdits.create("Bravo")).getSnapshot();

        ProjectOutcome outcome = session.save();

        assertTrue(outcome instanceof ChangedOutcome);
        assertSame(outcome.getSnapshot(), session.getSnapshot());
        assertFalse(outcome.getSnapshot().isDirty());
        assertEquals(target.toAbsolutePath().normalize(), outcome.getSnapshot().getFileIdentity().get());
        assertTrue(dirty.isDirty());

        ProjectOutcome reopened = ProjectSessions.create().open(target);
        assertTrue(reopened instanceof ChangedOutcome);
        assertEquals(2, reopened.getSnapshot().getSliderPresets().size());
        assertEquals("Bravo", reopened.getSnapshot().getSliderPresets().get(1).getName());
    }

    /**
     * Verifies that Save still rewrites externally changed bytes for a clean Project
     * without manufacturing an observable Project-state transition.
     *
     * @throws Exception when replacement test bytes cannot be written
     */
    @Test
    void cleanSaveStillWritesAndReturnsUnchangedSnapshot() throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        Path target = tempDirectory.resolve("clean-project.jbs2bg");
        session.saveAs(target);
        ProjectSnapshot clean = session.getSnapshot();
        Files.write(target, ("{\"SliderPresets\":{},\"CustomMorphTargets\":{},"
                + "\"MorphedNPCs\":{}}").getBytes(StandardCharsets.UTF_8));

        ProjectOutcome outcome = session.save();

        assertTrue(outcome instanceof UnchangedOutcome);
        assertSame(clean, outcome.getSnapshot());
        assertSame(clean, session.getSnapshot());
        ProjectOutcome reopened = ProjectSessions.create().open(target);
        assertEquals("Alpha", reopened.getSnapshot().getSliderPresets().get(0).getName());
    }

    /**
     * Verifies that a failed Save As keeps the latest dirty snapshot and its prior
     * file identity while leaving the existing destination untouched.
     *
     * @throws Exception when temporary test data cannot be prepared
     */
    @Test
    void failedSaveAsPreservesSnapshotPriorIdentityAndExistingTarget() throws Exception {
        Path original = tempDirectory.resolve("original.jbs2bg");
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.saveAs(original);
        ProjectSnapshot dirty = session.apply(SliderPresetEdits.create("Unsaved")).getSnapshot();

        Path blockedTarget = Files.createDirectory(tempDirectory.resolve("blocked-target.jbs2bg"));
        Path marker = blockedTarget.resolve("preserved.txt");
        Files.write(marker, "previous target".getBytes(StandardCharsets.UTF_8));

        ProjectOutcome outcome = session.saveAs(blockedTarget);

        assertTrue(outcome instanceof FailedOutcome);
        assertSame(dirty, outcome.getSnapshot());
        assertSame(dirty, session.getSnapshot());
        assertTrue(outcome.getSnapshot().isDirty());
        assertEquals(original.toAbsolutePath().normalize(), outcome.getSnapshot().getFileIdentity().get());
        assertEquals(2, outcome.getSnapshot().getSliderPresets().size());
        assertTrue(Files.isDirectory(blockedTarget));
        assertEquals("previous target", new String(Files.readAllBytes(marker), StandardCharsets.UTF_8));
        assertEquals(ProjectDiagnosticCodes.PROJECT_FILE_WRITE_FAILED,
                outcome.getDiagnostics().get(0).getCode());
        assertEquals(blockedTarget.toAbsolutePath().normalize(),
                outcome.getDiagnostics().get(0).getSourceLocation().getPath().get());
    }

    /**
     * Verifies that Save failure at an adopted path preserves the exact dirty
     * snapshot, its file identity, and all latest Project content.
     *
     * @throws Exception when the temporary adopted path cannot be prepared
     */
    @Test
    void failedSavePreservesLatestSnapshotAndAdoptedIdentity() throws Exception {
        Path parent = Files.createDirectory(tempDirectory.resolve("adopted-parent"));
        Path target = parent.resolve("project.jbs2bg");
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.saveAs(target);
        ProjectSnapshot dirty = session.apply(SliderPresetEdits.create("Unsaved")).getSnapshot();
        Files.delete(target);
        Files.delete(parent);

        ProjectOutcome outcome = session.save();

        assertTrue(outcome instanceof FailedOutcome);
        assertSame(dirty, outcome.getSnapshot());
        assertSame(dirty, session.getSnapshot());
        assertTrue(outcome.getSnapshot().isDirty());
        assertEquals(target.toAbsolutePath().normalize(), outcome.getSnapshot().getFileIdentity().get());
        assertEquals(2, outcome.getSnapshot().getSliderPresets().size());
        assertEquals(ProjectDiagnosticCodes.PROJECT_FILE_WRITE_FAILED,
                outcome.getDiagnostics().get(0).getCode());
    }

    /**
     * Verifies that Save As atomically replaces an existing target with a complete,
     * reopenable Project.
     *
     * @throws Exception when the existing test target cannot be prepared
     */
    @Test
    void saveAsAtomicallyReplacesExistingTargetWithCompleteProject() throws Exception {
        Path target = tempDirectory.resolve("replacement.jbs2bg");
        Files.write(target, "previous persisted bytes".getBytes(StandardCharsets.UTF_8));
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Replacement"));

        ProjectOutcome outcome = session.saveAs(target);

        assertTrue(outcome instanceof ChangedOutcome);
        assertFalse(outcome.getSnapshot().isDirty());
        ProjectOutcome reopened = ProjectSessions.create().open(target);
        assertTrue(reopened instanceof ChangedOutcome);
        assertEquals("Replacement", reopened.getSnapshot().getSliderPresets().get(0).getName());
    }

    /**
     * Verifies that saving a recovered Project commits its coherent content and
     * transitions the adopted identity from recovered/dirty to file-backed/clean.
     *
     * @throws Exception when the recovered Project fixture cannot be written
     */
    @Test
    void saveCommitsRecoveredProjectAsCleanFileBackedState() throws Exception {
        Path target = tempDirectory.resolve("recovered-project.jbs2bg");
        Files.write(target, ("{\"SliderPresets\":{\"Alpha\":{\"isUUNP\":false,\"SetSliders\":[]}},"
                + "\"CustomMorphTargets\":{\"Target\":{\"SliderPresets\":[\"Missing\"]}},"
                + "\"MorphedNPCs\":{}}")
                .getBytes(StandardCharsets.UTF_8));
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot recovered = session.open(target).getSnapshot();

        ProjectOutcome saved = session.save();

        assertTrue(recovered.isDirty());
        assertEquals(ProjectLifecycleStatus.RECOVERED, recovered.getLifecycleStatus());
        assertTrue(saved instanceof ChangedOutcome);
        assertFalse(saved.getSnapshot().isDirty());
        assertEquals(ProjectLifecycleStatus.FILE_BACKED, saved.getSnapshot().getLifecycleStatus());
        assertEquals(target.toAbsolutePath().normalize(), saved.getSnapshot().getFileIdentity().get());
        ProjectOutcome reopened = ProjectSessions.create().open(target);
        assertTrue(reopened.getDiagnostics().isEmpty());
        assertTrue(reopened.getSnapshot().getCustomMorphTargets().get(0).getSliderPresetNames().isEmpty());
    }

}
