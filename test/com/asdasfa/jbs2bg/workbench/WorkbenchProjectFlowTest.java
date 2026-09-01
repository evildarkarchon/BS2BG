package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.ProjectLifecycleStatus;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

class WorkbenchProjectFlowTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * Creates a deterministic coordinator whose publication callbacks execute in submission order.
     */
    private static JobCoordinator coordinator(ManualExecutor worker) {
        return new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // These tests settle operations before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
    }

    /**
     * Save As adopts a normalized Project identity only after ProjectSession publishes a successful save.
     */
    @Test
    void saveAsAdoptsNormalizedIdentityAndLeavesProjectClean() {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        WorkbenchProjectFlow.Update requested = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS);
        WorkbenchProjectFlow.Effect chooser = requested.effect().orElseThrow();
        WorkbenchProjectFlow.Update saved = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(temporaryDirectory.resolve("saved-project")));

        Path expectedIdentity = temporaryDirectory.resolve("saved-project.jbs2bg").toAbsolutePath().normalize();
        assertTrue(saved.accepted());
        assertTrue(Files.isRegularFile(expectedIdentity));
        assertEquals(expectedIdentity, saved.frame().snapshot().getFileIdentity().orElseThrow());
        assertEquals(ProjectLifecycleStatus.FILE_BACKED, saved.frame().snapshot().getLifecycleStatus());
        assertFalse(saved.frame().snapshot().isDirty());
        assertEquals("BS2BG Preview - saved-project.jbs2bg", saved.frame().title());
    }

    /**
     * Save As returns after central admission while persistence, measured progress, and publication remain on the
     * application worker.
     */
    @Test
    void saveAsPersistsOnlyWhenTheApplicationWorkerRuns() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        List<JobCoordinator.Frame> observedJobs = new ArrayList<>();
        jobs.observe(observedJobs::add);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        flow.apply(SliderPresetEdits.create("Queued Save As"));
        WorkbenchProjectFlow.Frame before = flow.frame();
        Path target = temporaryDirectory.resolve("async-save-as.jbs2bg").toAbsolutePath().normalize();

        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update admitted = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(target));

        assertEquals(before, admitted.frame());
        assertFalse(Files.exists(target));
        assertTrue(jobs.frame().active());
        JobCoordinator.Attempt running = jobs.frame().attempt().orElseThrow();
        assertEquals("Save Project", running.operation().name());
        assertEquals(List.of(target.toString()), running.operation().destinationLabels());
        assertEquals(Optional.of(before.snapshot().getContentVersion().toString()),
                running.operation().capturedBasis());
        assertTrue(running.progress().cancellable());

        worker.runNext();

        assertTrue(Files.isRegularFile(target));
        assertFalse(flow.frame().snapshot().isDirty());
        assertEquals(target, flow.frame().snapshot().getFileIdentity().orElseThrow());
        assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                jobs.frame().attempt().orElseThrow().lifecycle());
        assertTrue(observedJobs.stream().flatMap(job -> job.attempt().stream())
                .anyMatch(attempt -> attempt.progress().phase().equals("Serializing Project")));
        assertTrue(observedJobs.stream().flatMap(job -> job.attempt().stream())
                .anyMatch(attempt -> attempt.progress().phase().equals("Staging Project file")));
    }

    /**
     * BodySlide chooser results are captured as one Project-exclusive central job and publish the authoritative
     * Project frame only after the application worker completes.
     */
    @Test
    void bodySlideImportUsesCapturedSourcesAndTheCentralJobPath() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("captured.xml").toAbsolutePath().normalize();
        Files.writeString(source, "<SliderPresets><Preset name=\"Imported Preset\">"
                + "<SetSlider name=\"Waist\" size=\"small\" value=\"25\"/>"
                + "<SetSlider name=\"Waist\" size=\"big\" value=\"75\"/>"
                + "</Preset></SliderPresets>");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        List<JobCoordinator.Frame> importFrames = new ArrayList<>();
        jobs.observe(importFrames::add);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        WorkbenchProjectFlow.Frame before = flow.frame();

        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.IMPORT_BODYSLIDE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update admitted = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selectedSources(List.of(source)));

        assertEquals(WorkbenchProjectFlow.EffectKind.CHOOSE_BODYSLIDE_SOURCES, chooser.kind());
        assertEquals(before, admitted.frame());
        assertTrue(jobs.frame().active());
        JobCoordinator.Attempt running = jobs.frame().attempt().orElseThrow();
        assertEquals("Import BodySlide Presets", running.operation().name());
        assertEquals(List.of(source.toString()), running.operation().sourceLabels());
        assertEquals(Optional.of(before.snapshot().getContentVersion().toString()),
                running.operation().capturedBasis());

        worker.runNext();

        assertEquals(List.of("Imported Preset"), flow.frame().snapshot().getSliderPresets().stream()
                .map(preset -> preset.getName()).toList());
        JobCoordinator.Attempt completed = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED, completed.lifecycle());
        assertEquals(List.of("Imported captured.xml"), completed.effectsCommitted());
        assertTrue(completed.diagnostics().isEmpty());
        assertTrue(importFrames.stream().flatMap(frame -> frame.attempt().stream())
                .anyMatch(attempt -> attempt.progress().completedUnits().equals(Optional.of(1L))
                        && attempt.progress().totalUnits().equals(Optional.of(1L))));
    }

    /**
     * A valid source commits beside malformed and unreadable sources, with exact per-source evidence and a warning
     * terminal disposition rather than a modal-only failure.
     */
    @Test
    void bodySlideImportReportsPartialSourceOutcomes() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path valid = temporaryDirectory.resolve("valid.xml").toAbsolutePath().normalize();
        Path malformed = temporaryDirectory.resolve("malformed.xml").toAbsolutePath().normalize();
        Path missing = temporaryDirectory.resolve("missing.xml").toAbsolutePath().normalize();
        Files.writeString(valid, "<SliderPresets><Preset name=\"Committed\"/></SliderPresets>");
        Files.writeString(malformed, "<SliderPresets><Preset name=\"Broken\">");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);

        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.IMPORT_BODYSLIDE)
                .effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selectedSources(
                List.of(malformed, valid, missing)));
        worker.runNext();

        JobCoordinator.Attempt completed = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES, completed.lifecycle());
        assertEquals(List.of("Imported valid.xml"), completed.effectsCommitted());
        assertEquals(List.of(ProjectDiagnosticCodes.SLIDER_PRESET_XML_MALFORMED,
                        ProjectDiagnosticCodes.SLIDER_PRESET_XML_READ_FAILED),
                completed.diagnostics().stream().map(JobCoordinator.Diagnostic::code).toList());
        assertEquals(List.of("Committed"), flow.frame().snapshot().getSliderPresets().stream()
                .map(preset -> preset.getName()).toList());
    }

    /** An unreadable-only import is a failed attempt and retains the prior authoritative Project. */
    @Test
    void bodySlideImportFailsWhenNoSelectedSourceProducesAUsableOutcome() {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        WorkbenchProjectFlow.Frame before = flow.frame();
        Path missing = temporaryDirectory.resolve("missing-only.xml").toAbsolutePath().normalize();

        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.IMPORT_BODYSLIDE)
                .effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selectedSources(List.of(missing)));
        worker.runNext();

        JobCoordinator.Attempt failed = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.FAILED, failed.lifecycle());
        assertEquals(List.of(ProjectDiagnosticCodes.SLIDER_PRESET_XML_READ_FAILED),
                failed.diagnostics().stream().map(JobCoordinator.Diagnostic::code).toList());
        assertEquals(before.snapshot(), flow.frame().snapshot());
    }

    /** Cancellation at the next source safe point retains the earlier committed source and names that effect. */
    @Test
    void bodySlideImportCancellationRetainsEarlierCommittedSources() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path first = temporaryDirectory.resolve("first.xml").toAbsolutePath().normalize();
        Path second = temporaryDirectory.resolve("second.xml").toAbsolutePath().normalize();
        Files.writeString(first, "<SliderPresets><Preset name=\"Committed First\"/></SliderPresets>");
        Files.writeString(second, "<SliderPresets><Preset name=\"Must Not Commit\"/></SliderPresets>");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        boolean[] cancellationRequested = {false};
        jobs.observe(frame -> frame.attempt().ifPresent(attempt -> {
            if (!cancellationRequested[0] && attempt.progress().completedUnits().equals(Optional.of(1L))) {
                cancellationRequested[0] = true;
                jobs.requestCancel();
            }
        }));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.IMPORT_BODYSLIDE)
                .effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selectedSources(List.of(first, second)));

        Thread importWorker = worker.runNextAsync();
        importWorker.join();

        JobCoordinator.Attempt cancelled = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.CANCELLED, cancelled.lifecycle());
        assertEquals(List.of("Imported first.xml"), cancelled.effectsCommitted());
        assertEquals(List.of("Committed First"), flow.frame().snapshot().getSliderPresets().stream()
                .map(preset -> preset.getName()).toList());
    }

    /**
     * A recovered Open publishes its adopted identity, dirty state, and ordered structured diagnostics together.
     */
    @Test
    void recoveredOpenPublishesOneCoherentDirtyFrame() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        WorkbenchProjectFlow.Update requested = flow.request(WorkbenchProjectFlow.Intent.OPEN);
        WorkbenchProjectFlow.Effect chooser = requested.effect().orElseThrow();
        WorkbenchProjectFlow.Update opened = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(source));

        assertTrue(opened.accepted());
        assertEquals(source.toAbsolutePath().normalize(), opened.frame().snapshot().getFileIdentity().orElseThrow());
        assertEquals(ProjectLifecycleStatus.RECOVERED, opened.frame().snapshot().getLifecycleStatus());
        assertTrue(opened.frame().snapshot().isDirty());
        assertEquals(2, opened.frame().diagnostics().size());
        assertTrue(opened.frame().diagnostics().stream()
                .allMatch(diagnostic -> ProjectDiagnosticCodes.SLIDER_PRESET_ASSIGNMENT_MISSING
                        .equals(diagnostic.getCode())));
        assertEquals("BS2BG Preview - *recovery-source.jbs2bg", opened.frame().title());
    }

    /**
     * Open returns immediately after admission, retaining its captured path and basis until worker completion.
     */
    @Test
    void openRunsThroughCentralAdmissionWithCapturedInputsAndTruthfulProgress() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("async-source.jbs2bg");
        Files.copy(Path.of("test-resources", "projects", "legacy-project-semantics.jbs2bg"), source);
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        WorkbenchProjectFlow.Frame before = flow.frame();

        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update admitted = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(source));

        assertEquals(before, admitted.frame());
        assertTrue(jobs.frame().active());
        JobCoordinator.Attempt running = jobs.frame().attempt().orElseThrow();
        assertEquals(List.of(source.toAbsolutePath().normalize().toString()),
                running.operation().sourceLabels());
        assertEquals(Optional.of(before.snapshot().getContentVersion().toString()),
                running.operation().capturedBasis());

        worker.runNext();

        assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                jobs.frame().attempt().orElseThrow().lifecycle());
        assertEquals(List.of("Project published"),
                jobs.frame().attempt().orElseThrow().effectsCommitted());
        assertEquals(source.toAbsolutePath().normalize(),
                flow.frame().snapshot().getFileIdentity().orElseThrow());
    }

    /**
     * A changed captured basis refuses stale Open publication and retains zero committed effects.
     */
    @Test
    void staleOpenCompletionCannotOverwriteANewerProjectContentVersion() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("stale-source.jbs2bg");
        Files.copy(Path.of("test-resources", "projects", "legacy-project-semantics.jbs2bg"), source);
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        ProjectSession session = ProjectSessions.create();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session, jobs);
        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selected(source));
        var newer = session.apply(SliderPresetEdits.create("Newer content")).getSnapshot();

        worker.runNext();

        JobCoordinator.Attempt terminal = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES, terminal.lifecycle());
        assertTrue(terminal.effectsCommitted().isEmpty());
        assertTrue(terminal.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("STALE_RESULT")));
        assertEquals(newer, session.getSnapshot());
        assertTrue(session.getSnapshot().getSliderPresets().stream()
                .anyMatch(preset -> preset.getName().equals("Newer content")));
    }

    /**
     * A source changed after admission is stale even when its replacement remains a valid Project document.
     */
    @Test
    void changedOpenSourceCannotPublishAsTheCapturedInput() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("changing-source.jbs2bg");
        Files.copy(Path.of("test-resources", "projects", "legacy-project-semantics.jbs2bg"), source);
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        ProjectSession session = ProjectSessions.create();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session, jobs);
        WorkbenchProjectFlow.Frame before = flow.frame();
        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selected(source));
        Files.copy(Path.of("test-resources", "projects", "legacy-project-all-defaults.jbs2bg"), source,
                StandardCopyOption.REPLACE_EXISTING);

        worker.runNext();

        JobCoordinator.Attempt terminal = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES, terminal.lifecycle());
        assertTrue(terminal.effectsCommitted().isEmpty());
        assertTrue(terminal.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("STALE_RESULT")
                        && diagnostic.message().contains("source changed")));
        assertEquals(before.snapshot(), session.getSnapshot());
        assertEquals(before, flow.frame());
    }

    /**
     * New keeps a dirty Project intact until the user explicitly confirms its replacement.
     */
    @Test
    void dirtyNewCanBeCancelledOrExplicitlyDiscarded() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame dirty = flow.respond(openChooser.token(),
                WorkbenchProjectFlow.Response.selected(source)).frame();

        WorkbenchProjectFlow.Effect firstConfirmation = flow.request(WorkbenchProjectFlow.Intent.NEW)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update cancelled = flow.respond(firstConfirmation.token(),
                WorkbenchProjectFlow.Response.cancelled());

        assertTrue(cancelled.accepted());
        assertEquals(dirty, cancelled.frame());
        assertTrue(cancelled.effect().isEmpty());

        WorkbenchProjectFlow.Effect secondConfirmation = flow.request(WorkbenchProjectFlow.Intent.NEW)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update replaced = flow.respond(secondConfirmation.token(),
                WorkbenchProjectFlow.Response.discard());

        assertTrue(replaced.accepted());
        assertEquals(ProjectLifecycleStatus.UNTITLED, replaced.frame().snapshot().getLifecycleStatus());
        assertTrue(replaced.frame().snapshot().getFileIdentity().isEmpty());
        assertFalse(replaced.frame().snapshot().isDirty());
        assertTrue(replaced.frame().diagnostics().isEmpty());
        assertEquals("BS2BG Preview", replaced.frame().title());
    }

    /**
     * Save uses the adopted identity and clears recovery dirtiness without asking for another path.
     */
    @Test
    void saveUsesTheAdoptedIdentityOfARecoveredProject() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        assertTrue(flow.respond(openChooser.token(), WorkbenchProjectFlow.Response.selected(source))
                .frame().snapshot().isDirty());

        WorkbenchProjectFlow.Update saved = flow.request(WorkbenchProjectFlow.Intent.SAVE);

        assertTrue(saved.accepted());
        assertTrue(saved.effect().isEmpty());
        assertEquals(source.toAbsolutePath().normalize(), saved.frame().snapshot().getFileIdentity().orElseThrow());
        assertEquals(ProjectLifecycleStatus.FILE_BACKED, saved.frame().snapshot().getLifecycleStatus());
        assertFalse(saved.frame().snapshot().isDirty());
        assertTrue(saved.frame().diagnostics().isEmpty());
        assertEquals("BS2BG Preview - recovery-source.jbs2bg", saved.frame().title());
    }

    /**
     * File-backed Save leaves the dirty frame and destination untouched until its admitted worker action executes.
     */
    @Test
    void saveToAnAdoptedIdentityPersistsOnlyWhenTheApplicationWorkerRuns() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        Path target = temporaryDirectory.resolve("async-save.jbs2bg").toAbsolutePath().normalize();
        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selected(target));
        worker.runNext();
        flow.apply(SliderPresetEdits.create("Queued Ctrl S"));
        WorkbenchProjectFlow.Frame dirty = flow.frame();

        WorkbenchProjectFlow.Update admitted = flow.request(WorkbenchProjectFlow.Intent.SAVE);

        assertEquals(dirty, admitted.frame());
        assertTrue(admitted.frame().snapshot().isDirty());
        assertTrue(jobs.frame().active());
        assertFalse(Files.readString(target).contains("Queued Ctrl S"));

        Thread saveWorker = worker.runNextAsync();
        saveWorker.join();

        assertFalse(flow.frame().snapshot().isDirty());
        assertTrue(Files.readString(target).contains("Queued Ctrl S"));
        assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                jobs.frame().attempt().orElseThrow().lifecycle());
    }

    /**
     * Retrying adopted-identity Save recaptures the identity currently published by the ProjectSession.
     */
    @Test
    void saveRetryUsesTheCurrentAdoptedIdentity() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        Path failedParent = Files.createDirectory(temporaryDirectory.resolve("failed-identity"));
        Path failedIdentity = failedParent.resolve("project.jbs2bg").toAbsolutePath().normalize();
        WorkbenchProjectFlow.Effect firstChooser = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        flow.respond(firstChooser.token(), WorkbenchProjectFlow.Response.selected(failedIdentity));
        worker.runNext();
        flow.apply(SliderPresetEdits.create("Initial dirty content"));
        Files.delete(failedIdentity);
        Files.delete(failedParent);

        flow.request(WorkbenchProjectFlow.Intent.SAVE);
        worker.runNext();

        JobCoordinator.AttemptId failedAttempt = jobs.frame().attempt().orElseThrow().id();
        assertEquals(JobCoordinator.Lifecycle.FAILED, jobs.frame().attempt().orElseThrow().lifecycle());
        Path currentIdentity = temporaryDirectory.resolve("current-identity.jbs2bg").toAbsolutePath().normalize();
        WorkbenchProjectFlow.Effect replacementChooser = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        flow.respond(replacementChooser.token(), WorkbenchProjectFlow.Response.selected(currentIdentity));
        worker.runNext();
        flow.apply(SliderPresetEdits.create("Retry follows current identity"));

        assertTrue(jobs.retry(failedAttempt).admitted());
        worker.runNext();

        assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                jobs.frame().attempt().orElseThrow().lifecycle());
        assertEquals(currentIdentity, flow.frame().snapshot().getFileIdentity().orElseThrow());
        assertFalse(flow.frame().snapshot().isDirty());
        assertTrue(Files.readString(currentIdentity).contains("Retry follows current identity"));
    }

    /**
     * Dirty shutdown preserves the Project on Cancel and closes at most once after explicit Discard.
     */
    @Test
    void dirtyCloseCanBeCancelledBeforeDiscardClosesExactlyOnce() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame dirty = flow.respond(openChooser.token(),
                WorkbenchProjectFlow.Response.selected(source)).frame();

        WorkbenchProjectFlow.Effect firstConfirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update cancelled = flow.respond(firstConfirmation.token(),
                WorkbenchProjectFlow.Response.cancelled());

        assertTrue(cancelled.accepted());
        assertEquals(dirty, cancelled.frame());
        assertFalse(cancelled.frame().closed());

        WorkbenchProjectFlow.Effect secondConfirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update discarded = flow.respond(secondConfirmation.token(),
                WorkbenchProjectFlow.Response.discard());

        assertTrue(discarded.accepted());
        assertTrue(discarded.frame().closed());
        assertEquals(WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW,
                discarded.effect().orElseThrow().kind());

        WorkbenchProjectFlow.Update repeated = flow.request(WorkbenchProjectFlow.Intent.CLOSE);
        assertFalse(repeated.accepted());
        assertTrue(repeated.effect().isEmpty());
    }

    /**
     * Dirty shutdown can save through the adopted identity and closes only after the clean outcome publishes.
     */
    @Test
    void dirtyCloseSavesBeforePublishingTheCloseEffect() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        assertTrue(flow.respond(openChooser.token(), WorkbenchProjectFlow.Response.selected(source))
                .frame().snapshot().isDirty());

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update saved = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.save());

        assertTrue(saved.accepted());
        assertFalse(saved.frame().snapshot().isDirty());
        assertEquals(ProjectLifecycleStatus.FILE_BACKED, saved.frame().snapshot().getLifecycleStatus());
        assertTrue(saved.frame().closed());
        assertEquals(WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW, saved.effect().orElseThrow().kind());
    }

    /**
     * A failed close-save keeps the window and exact dirty Project active with its failure diagnostics.
     */
    @Test
    void failedCloseSavePreservesUnsavedWorkAndDoesNotClose() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path parent = Files.createDirectory(temporaryDirectory.resolve("adopted-parent"));
        Path source = parent.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame dirty = flow.respond(openChooser.token(),
                WorkbenchProjectFlow.Response.selected(source)).frame();
        Files.delete(source);
        Files.delete(parent);

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update failed = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.save());

        assertTrue(failed.accepted());
        assertFalse(failed.frame().closed());
        assertTrue(failed.frame().snapshot().isDirty());
        assertEquals(dirty.snapshot(), failed.frame().snapshot());
        assertEquals(dirty.title(), failed.frame().title());
        assertEquals(ProjectDiagnosticCodes.PROJECT_FILE_WRITE_FAILED,
                failed.frame().diagnostics().getFirst().getCode());
        assertTrue(failed.effect().isEmpty());
    }

    /**
     * A stale chooser callback cannot consume a newer effect or invoke a Project operation.
     */
    @Test
    void staleEffectResponseIsRejectedWithoutConsumingTheCurrentEffect() {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect stale = flow.request(WorkbenchProjectFlow.Intent.OPEN).effect().orElseThrow();
        assertTrue(flow.respond(stale.token(), WorkbenchProjectFlow.Response.cancelled()).accepted());
        WorkbenchProjectFlow.Effect current = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame beforeLateResponse = flow.frame();

        WorkbenchProjectFlow.Update rejected = flow.respond(stale.token(),
                WorkbenchProjectFlow.Response.selected(temporaryDirectory.resolve("stale-target")));

        assertFalse(rejected.accepted());
        assertEquals(beforeLateResponse, rejected.frame());
        assertFalse(Files.exists(temporaryDirectory.resolve("stale-target.jbs2bg")));

        WorkbenchProjectFlow.Update saved = flow.respond(current.token(),
                WorkbenchProjectFlow.Response.selected(temporaryDirectory.resolve("current-target")));
        assertTrue(saved.accepted());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("current-target.jbs2bg")));
    }

    /**
     * Open replaces a dirty Project only after confirmation and then asks for the source path.
     */
    @Test
    void dirtyOpenConfirmsBeforeLaunchingTheChooser() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path recovery = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                recovery);
        Path replacement = temporaryDirectory.resolve("replacement.jbs2bg");
        Files.copy(Path.of("test-resources", "projects", "legacy-project-semantics.jbs2bg"), replacement);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect firstChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame dirty = flow.respond(firstChooser.token(),
                WorkbenchProjectFlow.Response.selected(recovery)).frame();

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        assertEquals(WorkbenchProjectFlow.EffectKind.CONFIRM_OPEN, confirmation.kind());
        WorkbenchProjectFlow.Update confirmed = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.discard());

        assertTrue(confirmed.accepted());
        assertEquals(dirty, confirmed.frame());
        WorkbenchProjectFlow.Effect replacementChooser = confirmed.effect().orElseThrow();
        assertEquals(WorkbenchProjectFlow.EffectKind.CHOOSE_OPEN_PATH, replacementChooser.kind());

        WorkbenchProjectFlow.Update opened = flow.respond(replacementChooser.token(),
                WorkbenchProjectFlow.Response.selected(replacement));
        assertFalse(opened.frame().snapshot().isDirty());
        assertEquals(replacement.toAbsolutePath().normalize(),
                opened.frame().snapshot().getFileIdentity().orElseThrow());
    }

    /**
     * Saving an untitled dirty Project during shutdown continues through Save As before the final close effect.
     */
    @Test
    void dirtyUntitledCloseChoosesAPathBeforeClosing() {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Unsaved"));
        assertTrue(flow.frame().snapshot().isDirty());
        assertTrue(flow.frame().snapshot().getFileIdentity().isEmpty());

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update saveRequested = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.save());

        assertFalse(saveRequested.frame().closed());
        WorkbenchProjectFlow.Effect chooser = saveRequested.effect().orElseThrow();
        assertEquals(WorkbenchProjectFlow.EffectKind.CHOOSE_SAVE_PATH, chooser.kind());

        WorkbenchProjectFlow.Update saved = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(temporaryDirectory.resolve("saved-on-close")));
        assertFalse(saved.frame().snapshot().isDirty());
        assertTrue(saved.frame().closed());
        assertEquals(WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW, saved.effect().orElseThrow().kind());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("saved-on-close.jbs2bg")));
    }

    /**
     * Untitled close-after-save admits the selected Save As destination and keeps the window open until the worker
     * publishes a successful clean outcome.
     */
    @Test
    void dirtyUntitledCloseWaitsForTheApplicationWorkerSaveAsBeforeClosing() {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        flow.apply(SliderPresetEdits.create("Unsaved worker close"));
        WorkbenchProjectFlow.Frame dirty = flow.frame();
        Path target = temporaryDirectory.resolve("worker-close-save-as.jbs2bg").toAbsolutePath().normalize();

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Effect chooser = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.save()).effect().orElseThrow();
        WorkbenchProjectFlow.Update admitted = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(target));

        assertEquals(dirty, admitted.frame());
        assertFalse(admitted.frame().closed());
        assertTrue(admitted.effect().isEmpty());
        assertTrue(jobs.frame().active());
        assertFalse(Files.exists(target));

        worker.runNext();

        assertTrue(Files.isRegularFile(target));
        assertFalse(flow.frame().snapshot().isDirty());
        assertTrue(flow.frame().closed());
        assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                jobs.frame().attempt().orElseThrow().lifecycle());
    }

}
