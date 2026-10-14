package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

class WorkbenchAppearanceTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * System selection follows a live operating-system color-scheme change.
     */
    @Test
    void systemSelectionFollowsTheOperatingSystemColorScheme() {
        WorkbenchAppearance appearance = new WorkbenchAppearance(
                WorkbenchAppearance.ThemeChoice.SYSTEM,
                WorkbenchAppearance.PlatformPreferences.light());

        assertEquals(WorkbenchAppearance.EffectiveTheme.LIGHT, appearance.frame().effectiveTheme());

        WorkbenchAppearance.Frame changed = appearance.updatePlatformPreferences(
                WorkbenchAppearance.PlatformPreferences.dark());

        assertEquals(WorkbenchAppearance.ThemeChoice.SYSTEM, changed.selectedTheme());
        assertEquals(WorkbenchAppearance.EffectiveTheme.DARK, changed.effectiveTheme());
    }

    /**
     * High Contrast overrides an explicit choice temporarily and ending it restores that choice.
     */
    @Test
    void highContrastTemporarilyOverridesTheSelectedTheme() {
        WorkbenchAppearance appearance = new WorkbenchAppearance(
                WorkbenchAppearance.ThemeChoice.DARK,
                WorkbenchAppearance.PlatformPreferences.light());

        WorkbenchAppearance.Frame highContrast = appearance.updatePlatformPreferences(
                WorkbenchAppearance.PlatformPreferences.light().withHighContrast(true));

        assertEquals(WorkbenchAppearance.ThemeChoice.DARK, highContrast.selectedTheme());
        assertEquals(WorkbenchAppearance.EffectiveTheme.HIGH_CONTRAST, highContrast.effectiveTheme());

        WorkbenchAppearance.Frame restored = appearance.updatePlatformPreferences(
                WorkbenchAppearance.PlatformPreferences.light());

        assertEquals(WorkbenchAppearance.EffectiveTheme.DARK, restored.effectiveTheme());
    }

    /**
     * Reduced-motion changes are projected live without changing the selected or effective theme.
     */
    @Test
    void reducedMotionChangesAreProjectedLive() {
        WorkbenchAppearance appearance = new WorkbenchAppearance(
                WorkbenchAppearance.ThemeChoice.LIGHT,
                WorkbenchAppearance.PlatformPreferences.light());

        WorkbenchAppearance.Frame changed = appearance.updatePlatformPreferences(
                WorkbenchAppearance.PlatformPreferences.light().withReducedMotion(true));

        assertEquals(WorkbenchAppearance.ThemeChoice.LIGHT, changed.selectedTheme());
        assertEquals(WorkbenchAppearance.EffectiveTheme.LIGHT, changed.effectiveTheme());
        assertEquals(true, changed.reducedMotion());
    }

    /**
     * High Contrast tokens come from the live Windows system-color snapshot rather than an app palette.
     */
    @Test
    void highContrastUsesThePlatformSystemColors() {
        WorkbenchAppearance.SystemColors colors = new WorkbenchAppearance.SystemColors(
                "#010101", "#020202", "#030303", "#040404",
                "#050505", "#060606", "#070707", "#080808");
        WorkbenchAppearance appearance = new WorkbenchAppearance(
                WorkbenchAppearance.ThemeChoice.SYSTEM,
                new WorkbenchAppearance.PlatformPreferences(false, true, false, colors));

        WorkbenchAppearance.Palette palette = appearance.frame().palette();

        assertEquals("#010101", palette.background());
        assertEquals("#020202", palette.text());
        assertEquals("#030303", palette.surface());
        assertEquals("#040404", palette.border());
        assertEquals("#050505", palette.selection());
        assertEquals("#060606", palette.selectionText());
        assertEquals("#070707", palette.focus());
        assertEquals("#080808", palette.disabledText());
    }

    /**
     * A manual theme choice takes effect immediately and remains selected while the system is dark.
     */
    @Test
    void manualThemeChoiceOverridesTheSystemColorScheme() {
        WorkbenchAppearance appearance = new WorkbenchAppearance(
                WorkbenchAppearance.ThemeChoice.SYSTEM,
                WorkbenchAppearance.PlatformPreferences.dark());

        WorkbenchAppearance.Frame selected = appearance.selectTheme(WorkbenchAppearance.ThemeChoice.LIGHT);

        assertEquals(WorkbenchAppearance.ThemeChoice.LIGHT, selected.selectedTheme());
        assertEquals(WorkbenchAppearance.EffectiveTheme.LIGHT, selected.effectiveTheme());
    }

    /**
     * The selected theme survives a new store instance while a missing selection defaults safely to System.
     */
    @Test
    void selectedThemePersistsInThePreviewProfile() throws Exception {
        WorkbenchAppearanceStore initial = new WorkbenchAppearanceStore(temporaryDirectory);
        assertEquals(WorkbenchAppearance.ThemeChoice.SYSTEM, initial.load());

        initial.save(WorkbenchAppearance.ThemeChoice.DARK);

        WorkbenchAppearanceStore restarted = new WorkbenchAppearanceStore(temporaryDirectory);
        assertEquals(WorkbenchAppearance.ThemeChoice.DARK, restarted.load());
    }

    /**
     * The theme save job writes the choice only on the worker, and a write that fails leaves the applied theme
     * as a completed-with-issues job instead of a failure that would offer a modal retry.
     */
    @Test
    void themeSaveJobPersistsOnTheWorkerAndReportsAFailedWriteAsAnIssue() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The theme save settles before prolonged cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchAppearanceStore store = new WorkbenchAppearanceStore(temporaryDirectory);

        assertTrue(jobs.submit(store.saveSubmission(WorkbenchAppearance.ThemeChoice.DARK)).admitted());
        assertEquals(WorkbenchAppearance.ThemeChoice.SYSTEM, store.load());
        worker.runNextAsync().join();

        JobCoordinator.Attempt saved = jobs.frame().attempt().orElseThrow();
        assertEquals("Save Theme Preference", saved.operation().name());
        assertEquals(JobCoordinator.Lifecycle.COMPLETED, saved.lifecycle());
        assertEquals(WorkbenchAppearance.ThemeChoice.DARK, store.load());

        Path blocked = Files.writeString(temporaryDirectory.resolve("not-a-directory"), "file");
        assertTrue(jobs.submit(new WorkbenchAppearanceStore(blocked)
                .saveSubmission(WorkbenchAppearance.ThemeChoice.LIGHT)).admitted());
        worker.runNextAsync().join();

        JobCoordinator.Attempt failedWrite = jobs.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES, failedWrite.lifecycle());
        assertEquals("The selected theme could not be saved.", failedWrite.summary());
        assertEquals(List.of("THEME_PREFERENCE_SAVE_FAILED"), failedWrite.diagnostics().stream()
                .map(JobCoordinator.Diagnostic::code).toList());
    }

    /**
     * An oversized preference containing an otherwise valid theme cannot make startup accept the unbounded tail.
     */
    @Test
    void oversizedThemePreferenceDefaultsToSystem() throws Exception {
        Files.writeString(temporaryDirectory.resolve(WorkbenchAppearanceStore.FILE_NAME),
                "theme=DARK" + " ".repeat(4096), StandardCharsets.UTF_8);

        WorkbenchAppearance.ThemeChoice choice = new WorkbenchAppearanceStore(temporaryDirectory).load();

        assertEquals(WorkbenchAppearance.ThemeChoice.SYSTEM, choice);
    }

    /**
     * Theme choices expose the accepted title-case text used by the keyboard-accessible selector.
     */
    @Test
    void themeChoicesHaveStableUserFacingNames() {
        assertEquals("System", WorkbenchAppearance.ThemeChoice.SYSTEM.toString());
        assertEquals("Light", WorkbenchAppearance.ThemeChoice.LIGHT.toString());
        assertEquals("Dark", WorkbenchAppearance.ThemeChoice.DARK.toString());
    }
}
