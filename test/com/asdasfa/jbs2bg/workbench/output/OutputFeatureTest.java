package com.asdasfa.jbs2bg.workbench.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

class OutputFeatureTest {

    @TempDir
    java.nio.file.Path temporaryDirectory;

    /** Restores the repository Settings pair after output-specific configuration. */
    @AfterEach
    void restoreSettings() {
        SettingsTestSupport.restoreRepositorySettings();
    }

    /** Creates a deterministic central coordinator whose publication lane is the calling test thread. */
    private static JobCoordinator coordinator(ManualExecutor worker) {
        return new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-31T18:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // These tests settle generation before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
    }

    /**
     * Generate captures one Project and Settings basis, then publishes all three output families only after the
     * central worker completes.
     */
    @Test
    void generatePublishesAllArtifactsFromOneCapturedBasis() {
        SettingsTestSupport.installStandardOutput(Map.of("Waist", Float.valueOf(2f)), List.of());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        List<JobCoordinator.Frame> jobFrames = new java.util.ArrayList<>();
        jobs.observe(jobFrames::add);
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        projectFlow.apply(SliderPresetEdits.create("Captured"));
        projectFlow.apply(SliderPresetEdits.setSliderChoice("Captured",
                new SliderChoiceSnapshot("Waist", true, Integer.valueOf(25), Integer.valueOf(75),
                        25, 75, 0, 100, false)));
        Settings.Snapshot capturedSettings = Settings.snapshot();
        OutputFeature feature = new OutputFeature(projectFlow,
                () -> new OutputFeature.GenerationSettings(capturedSettings, false));
        List<OutputFeature.Update> publications = new java.util.ArrayList<>();
        feature.observe(publications::add);

        OutputFeature.Update admitted = feature.dispatch(new OutputFeature.Generate());

        assertTrue(admitted.accepted());
        assertTrue(jobs.frame().active());
        assertEquals("Generate Output", jobs.frame().attempt().orElseThrow().operation().name());
        assertFalse(feature.frame().generatedOutput().isPresent());

        worker.runNext();

        OutputFeature.Frame completed = feature.frame();
        assertEquals(OutputFeature.Freshness.FRESH, completed.freshness());
        assertEquals(projectFlow.frame().snapshot(), completed.basis().orElseThrow().projectSnapshot());
        assertEquals(capturedSettings, completed.basis().orElseThrow().settings());
        assertEquals("Captured=Waist@0.5:1.5",
                completed.generatedOutput().orElseThrow().getTemplatesText());
        assertEquals("", completed.generatedOutput().orElseThrow().getMorphsText());
        assertEquals(List.of("Captured"), completed.generatedOutput().orElseThrow().getBosJsonArtifacts().stream()
                .map(artifact -> artifact.getSliderPresetName()).toList());
        assertTrue(feature.dispatch(new OutputFeature.SelectTab(OutputFeature.Tab.BOS_JSON)).accepted());
        assertEquals(OutputFeature.Tab.BOS_JSON, feature.frame().selectedTab());
        assertEquals(List.of("Captured"), feature.frame().bosArtifactNames());
        assertEquals("Captured", feature.frame().selectedBosArtifact().orElseThrow());
        assertEquals(completed.generatedOutput().orElseThrow().getBosJsonArtifacts().getFirst().getText(),
                feature.frame().displayedText());
        assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                jobs.frame().attempt().orElseThrow().lifecycle());
        assertEquals(List.of("Generated Output published"),
                jobs.frame().attempt().orElseThrow().effectsCommitted());
        assertTrue(jobFrames.stream().flatMap(jobFrame -> jobFrame.attempt().stream())
                .anyMatch(attempt -> attempt.progress().completedUnits().isPresent()
                        && attempt.progress().phase().equals("Generating Project output")));
        assertTrue(publications.stream().flatMap(update -> update.effect().stream())
                .anyMatch(effect -> effect.kind() == OutputFeature.EffectKind.REVEAL_DRAWER));
    }

    /**
     * Save-only, unchanged, and rejected Project publications retain accepted Output, while the first content
     * version change invalidates it immediately.
     */
    @Test
    void projectContentVersionAloneControlsAcceptedOutputInvalidation() {
        SettingsTestSupport.installStandardOutput(Map.of(), List.of());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        projectFlow.apply(SliderPresetEdits.create("Stable"));
        OutputFeature feature = new OutputFeature(projectFlow,
                () -> new OutputFeature.GenerationSettings(Settings.snapshot(), false));
        feature.dispatch(new OutputFeature.Generate());
        worker.runNext();
        assertEquals(OutputFeature.Freshness.FRESH, feature.frame().freshness());

        WorkbenchProjectFlow.Effect chooser = projectFlow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        projectFlow.respond(chooser.token(), WorkbenchProjectFlow.Response.selected(
                temporaryDirectory.resolve("stable.jbs2bg")));
        worker.runNext();
        feature.acceptProjectFrame(projectFlow.frame());
        assertEquals(OutputFeature.Freshness.FRESH, feature.frame().freshness());

        projectFlow.apply(SliderPresetEdits.setUunp("Stable", false));
        feature.acceptProjectFrame(projectFlow.frame());
        assertEquals(OutputFeature.Freshness.FRESH, feature.frame().freshness());

        projectFlow.apply(SliderPresetEdits.delete("Missing"));
        feature.acceptProjectFrame(projectFlow.frame());
        assertEquals(OutputFeature.Freshness.FRESH, feature.frame().freshness());

        projectFlow.apply(SliderPresetEdits.create("Changed"));
        feature.acceptProjectFrame(projectFlow.frame());
        assertEquals(OutputFeature.Freshness.INVALIDATED, feature.frame().freshness());
        assertTrue(feature.frame().generatedOutput().isEmpty());
    }

    /** A Project edit may continue during snapshot-derived Generate and prevents that stale completion publishing. */
    @Test
    void projectEditDuringGenerateProducesStaleActivityWithoutPublishing() {
        SettingsTestSupport.installStandardOutput(Map.of(), List.of());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        projectFlow.apply(SliderPresetEdits.create("Captured"));
        OutputFeature feature = new OutputFeature(projectFlow,
                () -> new OutputFeature.GenerationSettings(Settings.snapshot(), false));
        List<OutputFeature.Update> publications = new java.util.ArrayList<>();
        feature.observe(publications::add);

        assertTrue(feature.dispatch(new OutputFeature.Generate()).accepted());
        projectFlow.apply(SliderPresetEdits.create("Newer"));
        feature.acceptProjectFrame(projectFlow.frame());
        worker.runNext();

        assertTrue(feature.frame().generatedOutput().isEmpty());
        assertTrue(publications.stream().noneMatch(update -> update.effect().isPresent()));
        JobCoordinator.Attempt stale = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES, stale.lifecycle());
        assertEquals("Project changed—Generate again.", stale.summary());
        assertEquals(List.of("STALE_RESULT"), stale.diagnostics().stream()
                .map(JobCoordinator.Diagnostic::code).toList());
    }

    /** Accepted cancellation before generation starts publishes neither artifacts nor a drawer-reveal effect. */
    @Test
    void cancelledGenerateNeverPublishesOutput() {
        SettingsTestSupport.installStandardOutput(Map.of(), List.of());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        OutputFeature feature = new OutputFeature(projectFlow,
                () -> new OutputFeature.GenerationSettings(Settings.snapshot(), false));
        List<OutputFeature.Update> publications = new java.util.ArrayList<>();
        feature.observe(publications::add);

        assertTrue(feature.dispatch(new OutputFeature.Generate()).accepted());
        assertEquals(JobCoordinator.CancelResponse.ACCEPTED, jobs.requestCancel());
        worker.runNext();

        assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                jobs.frame().attempt().orElseThrow().lifecycle());
        assertTrue(feature.frame().generatedOutput().isEmpty());
        assertTrue(publications.stream().noneMatch(update -> update.effect().isPresent()));
    }

    /** Settings or command-option changes invalidate accepted Output and stale an in-flight captured basis. */
    @Test
    void generationSettingsChangesInvalidateAndStaleOutput() {
        SettingsTestSupport.installStandardOutput(Map.of("Waist", Float.valueOf(2f)), List.of());
        AtomicReference<OutputFeature.GenerationSettings> currentSettings = new AtomicReference<>(
                new OutputFeature.GenerationSettings(Settings.snapshot(), false));
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        projectFlow.apply(SliderPresetEdits.create("Settings basis"));
        OutputFeature feature = new OutputFeature(projectFlow, currentSettings::get);
        feature.dispatch(new OutputFeature.Generate());
        worker.runNext();
        assertEquals(OutputFeature.Freshness.FRESH, feature.frame().freshness());

        currentSettings.set(new OutputFeature.GenerationSettings(Settings.snapshot(), true));
        feature.refreshGenerationSettings();
        assertEquals(OutputFeature.Freshness.INVALIDATED, feature.frame().freshness());

        assertTrue(feature.dispatch(new OutputFeature.Generate()).accepted());
        currentSettings.set(new OutputFeature.GenerationSettings(Settings.snapshot(), false));
        worker.runNext();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES,
                jobs.frame().attempt().orElseThrow().lifecycle());
        assertEquals(List.of("STALE_RESULT"), jobs.frame().attempt().orElseThrow().diagnostics().stream()
                .map(JobCoordinator.Diagnostic::code).toList());
        assertTrue(feature.frame().generatedOutput().isEmpty());
    }

    /** BoS selection uses Slider Preset identity and survives tab changes without index-based retargeting. */
    @Test
    void bosArtifactSelectionIsFeatureOwnedAndIdentityStable() {
        SettingsTestSupport.installStandardOutput(Map.of(), List.of());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        OutputFeature feature = new OutputFeature(projectFlow,
                () -> new OutputFeature.GenerationSettings(Settings.snapshot(), false));
        feature.dispatch(new OutputFeature.Generate());
        worker.runNext();

        assertTrue(feature.dispatch(new OutputFeature.SelectTab(OutputFeature.Tab.BOS_JSON)).accepted());
        assertTrue(feature.dispatch(new OutputFeature.SelectBosArtifact("Beta")).accepted());
        String betaText = feature.frame().displayedText();
        assertEquals(feature.frame().generatedOutput().orElseThrow().getBosJsonArtifacts().get(1).getText(), betaText);
        assertFalse(feature.dispatch(new OutputFeature.SelectBosArtifact("Missing")).accepted());
        assertEquals("Beta", feature.frame().selectedBosArtifact().orElseThrow());
        assertEquals(betaText, feature.frame().displayedText());
        feature.dispatch(new OutputFeature.SelectTab(OutputFeature.Tab.TEMPLATES));
        feature.dispatch(new OutputFeature.SelectTab(OutputFeature.Tab.BOS_JSON));
        assertEquals("Beta", feature.frame().selectedBosArtifact().orElseThrow());
    }

    /** A linked retry recaptures the repaired Project instead of replaying the failed Generate basis. */
    @Test
    void retryRecapturesCurrentProjectAndSettingsBasis() {
        SettingsTestSupport.installStandardOutput(Map.of(), List.of());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        String malformedName = "Broken\uD800";
        projectFlow.apply(SliderPresetEdits.create(malformedName));
        OutputFeature feature = new OutputFeature(projectFlow,
                () -> new OutputFeature.GenerationSettings(Settings.snapshot(), false));
        feature.dispatch(new OutputFeature.Generate());
        worker.runNext();
        JobCoordinator.Attempt failed = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.FAILED, failed.lifecycle());
        assertTrue(failed.retryAvailable());

        projectFlow.apply(SliderPresetEdits.rename(malformedName, "Repaired"));
        feature.acceptProjectFrame(projectFlow.frame());
        assertTrue(jobs.retry(failed.id()).admitted());
        worker.runNext();

        JobCoordinator.Attempt retried = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED, retried.lifecycle());
        assertEquals(failed.id(), retried.retryOf().orElseThrow());
        assertEquals("Repaired", feature.frame().generatedOutput().orElseThrow()
                .getBosJsonArtifacts().getFirst().getSliderPresetName());
    }
}
