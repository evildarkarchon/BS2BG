package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkbenchAppearanceTest {

    @TempDir
    Path temporaryDirectory;

    /** System selection follows a live operating-system color-scheme change. */
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

    /** High Contrast overrides an explicit choice temporarily and ending it restores that choice. */
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

    /** Reduced-motion changes are projected live without changing the selected or effective theme. */
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

    /** High Contrast tokens come from the live Windows system-color snapshot rather than an app palette. */
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

    /** A manual theme choice takes effect immediately and remains selected while the system is dark. */
    @Test
    void manualThemeChoiceOverridesTheSystemColorScheme() {
        WorkbenchAppearance appearance = new WorkbenchAppearance(
                WorkbenchAppearance.ThemeChoice.SYSTEM,
                WorkbenchAppearance.PlatformPreferences.dark());

        WorkbenchAppearance.Frame selected = appearance.selectTheme(WorkbenchAppearance.ThemeChoice.LIGHT);

        assertEquals(WorkbenchAppearance.ThemeChoice.LIGHT, selected.selectedTheme());
        assertEquals(WorkbenchAppearance.EffectiveTheme.LIGHT, selected.effectiveTheme());
    }

    /** The selected theme survives a new store instance while a missing selection defaults safely to System. */
    @Test
    void selectedThemePersistsInThePreviewProfile() throws Exception {
        WorkbenchAppearanceStore initial = new WorkbenchAppearanceStore(temporaryDirectory);
        assertEquals(WorkbenchAppearance.ThemeChoice.SYSTEM, initial.load());

        initial.save(WorkbenchAppearance.ThemeChoice.DARK);

        WorkbenchAppearanceStore restarted = new WorkbenchAppearanceStore(temporaryDirectory);
        assertEquals(WorkbenchAppearance.ThemeChoice.DARK, restarted.load());
    }

    /** Theme choices expose the accepted title-case text used by the keyboard-accessible selector. */
    @Test
    void themeChoicesHaveStableUserFacingNames() {
        assertEquals("System", WorkbenchAppearance.ThemeChoice.SYSTEM.toString());
        assertEquals("Light", WorkbenchAppearance.ThemeChoice.LIGHT.toString());
        assertEquals("Dark", WorkbenchAppearance.ThemeChoice.DARK.toString());
    }
}
