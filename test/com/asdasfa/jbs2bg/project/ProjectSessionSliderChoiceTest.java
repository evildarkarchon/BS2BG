package com.asdasfa.jbs2bg.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;

/**
 * Verifies slider-choice validation and UUNP default rebuilding through the
 * public ProjectSession seam, with deterministic Slider settings.
 */
class ProjectSessionSliderChoiceTest {

    private final Map<String, DefaultSliderValue> originalDefaults = new LinkedHashMap<>();
    private final Map<String, DefaultSliderValue> originalUunpDefaults = new LinkedHashMap<>();

    /** Seeds distinct regular and UUNP defaults so mode changes are observable. */
    @BeforeEach
    void initializeSliderSettings() {
        originalDefaults.putAll(Settings.getDefaultsMap());
        originalUunpDefaults.putAll(Settings.getDefaultsMapUUNP());
        Settings.getDefaultsMap().clear();
        Settings.getDefaultsMap().put("Breasts", new DefaultSliderValue(0.2f, 1f));
        Settings.getDefaultsMap().put("Legs", new DefaultSliderValue(0f, 1f));
        Settings.getDefaultsMap().put("Waist", new DefaultSliderValue(0f, 1f));
        Settings.getDefaultsMapUUNP().clear();
        Settings.getDefaultsMapUUNP().put("Arms", new DefaultSliderValue(1f, 1f));
        Settings.getDefaultsMapUUNP().put("Breasts", new DefaultSliderValue(1f, 1f));
        Settings.getDefaultsMapUUNP().put("Legs", new DefaultSliderValue(1f, 1f));
    }

    /** Restores process-wide Slider settings after each test. */
    @AfterEach
    void restoreSliderSettings() {
        Settings.getDefaultsMap().clear();
        Settings.getDefaultsMap().putAll(originalDefaults);
        Settings.getDefaultsMapUUNP().clear();
        Settings.getDefaultsMapUUNP().putAll(originalUunpDefaults);
    }

    /**
     * Rejects a full update whose slider choices repeat a name without regard to
     * case, because a later single-choice edit could only ever reach the first match.
     */
    @Test
    void fullUpdateRejectsCaseInsensitiveDuplicateSliderChoices() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        ProjectSnapshot before = session.apply(SliderPresetEdits.create("Alpha")).getSnapshot();
        SliderChoiceSnapshot waist = explicit("Waist", 20, 80, 10, 90);
        SliderChoiceSnapshot duplicateWaist = explicit("waist", 30, 70, 0, 100);

        ProjectOutcome outcome = session.apply(SliderPresetEdits.update("Alpha",
                new SliderPresetSnapshot("Alpha", false, Arrays.asList(waist, duplicateWaist))));

        assertSliderChoiceRejected(outcome, ProjectDiagnosticCodes.SLIDER_CHOICE_NAME_DUPLICATE,
                "slider-preset.slider-choice.name", before);
        assertSame(before, session.getSnapshot());
    }

    /**
     * Rejects percentages outside 0–100 or a minimum above the maximum on both the
     * single-choice and full-update paths, while accepting the inclusive bounds.
     */
    @Test
    void sliderChoiceEditsRejectPercentagesOutsideBoundsOrReversed() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        ProjectSnapshot before = session.apply(SliderPresetEdits.create("Alpha")).getSnapshot();
        List<SliderChoiceSnapshot> invalidChoices = Arrays.asList(
                explicit("Waist", 20, 80, -1, 50),
                explicit("Waist", 20, 80, 0, 101),
                explicit("Waist", 20, 80, 60, 40));

        for (SliderChoiceSnapshot invalid : invalidChoices) {
            ProjectOutcome single = session.apply(SliderPresetEdits.setSliderChoice("Alpha", invalid));
            assertSliderChoiceRejected(single, ProjectDiagnosticCodes.SLIDER_CHOICE_PERCENTAGE_INVALID,
                    "slider-preset.slider-choice.percentage", before);

            ProjectOutcome full = session.apply(SliderPresetEdits.update("Alpha",
                    new SliderPresetSnapshot("Alpha", false, Arrays.asList(explicit("Arms", 5, 95, 0, 100), invalid))));
            assertSliderChoiceRejected(full, ProjectDiagnosticCodes.SLIDER_CHOICE_PERCENTAGE_INVALID,
                    "slider-preset.slider-choice.percentage", before);
        }
        assertSame(before, session.getSnapshot());

        ProjectOutcome fullRange = session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                explicit("Waist", 20, 80, 0, 100)));
        ProjectOutcome collapsedRange = session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                explicit("Arms", 5, 95, 50, 50)));

        assertTrue(fullRange instanceof ChangedOutcome);
        assertTrue(collapsedRange instanceof ChangedOutcome);
        assertEquals(Arrays.asList("Arms", "Waist"), names(collapsedRange.getSnapshot().getSliderPresets().get(0)));
    }

    /**
     * Matches the legacy UUNP toggle: synthesized defaults are rebuilt for the
     * requested mode (discarding edits made to them), explicit choices survive, and
     * explicit choices that defer to defaults re-resolve their effective values.
     */
    @Test
    void togglingUunpRebuildsSynthesizedDefaultsForRequestedMode() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.setSliderChoice("Alpha", explicit("Breasts", 30, 90, 10, 90)));
        session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                new SliderChoiceSnapshot("Waist", true, null, null, 0, 100, 100, 100, false)));

        ProjectOutcome toUunp = session.apply(SliderPresetEdits.setUunp("Alpha", true));
        SliderPresetSnapshot uunpPreset = toUunp.getSnapshot().getSliderPresets().get(0);

        assertTrue(toUunp instanceof ChangedOutcome);
        assertTrue(uunpPreset.isUunp());
        assertEquals(Arrays.asList("Arms", "Breasts", "Legs", "Waist"), names(uunpPreset));
        assertSynthesized(find(uunpPreset, "Arms"), 100, 100);
        assertExplicitStored(find(uunpPreset, "Breasts"), 30, 90, 10, 90);
        assertSynthesized(find(uunpPreset, "Legs"), 100, 100);
        SliderChoiceSnapshot uunpWaist = find(uunpPreset, "Waist");
        assertFalse(uunpWaist.isMissingDefault());
        assertFalse(uunpWaist.getStoredSmallValue().isPresent());
        assertFalse(uunpWaist.getStoredBigValue().isPresent());
        // Waist is not a configured UUNP default, so deferring to defaults now yields zero.
        assertEquals(0, uunpWaist.getEffectiveSmallValue());
        assertEquals(0, uunpWaist.getEffectiveBigValue());

        ProjectOutcome editedSynthesized = session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                find(uunpPreset, "Legs").withPercentageRange(20, 80)));
        assertTrue(editedSynthesized instanceof ChangedOutcome);
        assertTrue(find(editedSynthesized.getSnapshot().getSliderPresets().get(0), "Legs").isMissingDefault());

        ProjectOutcome toRegular = session.apply(SliderPresetEdits.setUunp("Alpha", false));
        SliderPresetSnapshot regularPreset = toRegular.getSnapshot().getSliderPresets().get(0);

        assertTrue(toRegular instanceof ChangedOutcome);
        assertFalse(regularPreset.isUunp());
        assertEquals(Arrays.asList("Breasts", "Legs", "Waist"), names(regularPreset));
        assertExplicitStored(find(regularPreset, "Breasts"), 30, 90, 10, 90);
        // The percentage edit belonged to the UUNP synthesized default and is discarded with it.
        assertSynthesized(find(regularPreset, "Legs"), 0, 100);
        SliderChoiceSnapshot regularWaist = find(regularPreset, "Waist");
        assertFalse(regularWaist.isMissingDefault());
        assertEquals(0, regularWaist.getEffectiveSmallValue());
        assertEquals(100, regularWaist.getEffectiveBigValue());
    }

    /**
     * Treats a full update that flips the UUNP flag exactly like the UUNP edit, so a
     * caller cannot publish the previous mode's synthesized defaults under the new mode.
     */
    @Test
    void fullUpdateThatChangesUunpRebuildsSynthesizedDefaults() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        SliderChoiceSnapshot breasts = explicit("Breasts", 30, 90, 10, 90);
        SliderChoiceSnapshot staleWaist = new SliderChoiceSnapshot("Waist", true, null, null, 0, 100, 100, 100, true);
        session.apply(SliderPresetEdits.setSliderChoice("Alpha", breasts));

        ProjectOutcome outcome = session.apply(SliderPresetEdits.update("Alpha",
                new SliderPresetSnapshot("Alpha", true, Arrays.asList(breasts, staleWaist))));
        SliderPresetSnapshot preset = outcome.getSnapshot().getSliderPresets().get(0);

        assertTrue(outcome instanceof ChangedOutcome);
        assertTrue(preset.isUunp());
        assertEquals(Arrays.asList("Arms", "Breasts", "Legs"), names(preset));
        assertSynthesized(find(preset, "Arms"), 100, 100);
        assertExplicitStored(find(preset, "Breasts"), 30, 90, 10, 90);
        assertSynthesized(find(preset, "Legs"), 100, 100);

        ProjectOutcome sameMode = session.apply(SliderPresetEdits.update("Alpha",
                new SliderPresetSnapshot("Alpha", true, preset.getSliderChoices())));
        assertTrue(sameMode instanceof UnchangedOutcome);
        assertSame(outcome.getSnapshot(), sameMode.getSnapshot());
    }

    /**
     * Builds an explicit choice with both stored values present.
     *
     * @param name slider name
     * @param small stored and effective small value
     * @param big stored and effective big value
     * @param minimum lower randomization percentage
     * @param maximum upper randomization percentage
     * @return an explicit, enabled slider choice
     */
    private static SliderChoiceSnapshot explicit(String name, int small, int big, int minimum, int maximum) {
        return new SliderChoiceSnapshot(name, true, Integer.valueOf(small), Integer.valueOf(big), small, big,
                minimum, maximum, false);
    }

    /** Asserts one all-default synthesized choice with the given effective values. */
    private static void assertSynthesized(SliderChoiceSnapshot choice, int effectiveSmall, int effectiveBig) {
        assertTrue(choice.isMissingDefault());
        assertTrue(choice.isEnabled());
        assertFalse(choice.getStoredSmallValue().isPresent());
        assertFalse(choice.getStoredBigValue().isPresent());
        assertEquals(effectiveSmall, choice.getEffectiveSmallValue());
        assertEquals(effectiveBig, choice.getEffectiveBigValue());
        assertEquals(100, choice.getPercentageMinimum());
        assertEquals(100, choice.getPercentageMaximum());
    }

    /** Asserts an explicit choice whose stored and effective values are both present. */
    private static void assertExplicitStored(SliderChoiceSnapshot choice, int small, int big, int minimum,
            int maximum) {
        assertFalse(choice.isMissingDefault());
        assertEquals(small, choice.getStoredSmallValue().getAsInt());
        assertEquals(big, choice.getStoredBigValue().getAsInt());
        assertEquals(small, choice.getEffectiveSmallValue());
        assertEquals(big, choice.getEffectiveBigValue());
        assertEquals(minimum, choice.getPercentageMinimum());
        assertEquals(maximum, choice.getPercentageMaximum());
    }

    /** Asserts the structured slider-choice rejection contract at a stable element. */
    private static void assertSliderChoiceRejected(ProjectOutcome outcome, String code, String element,
            ProjectSnapshot snapshot) {
        assertTrue(outcome instanceof RejectedOutcome);
        assertSame(snapshot, outcome.getSnapshot());
        ProjectDiagnostic diagnostic = outcome.getDiagnostics().get(0);
        assertEquals(code, diagnostic.getCode());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.getSeverity());
        assertEquals(element, diagnostic.getSourceLocation().getElement().get());
    }

    /** Returns slider-choice names in published order. */
    private static List<String> names(SliderPresetSnapshot preset) {
        List<String> names = new ArrayList<>();
        for (SliderChoiceSnapshot choice : preset.getSliderChoices())
            names.add(choice.getName());
        return names;
    }

    /** Finds one choice by case-insensitive name, failing when absent. */
    private static SliderChoiceSnapshot find(SliderPresetSnapshot preset, String name) {
        for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
            if (choice.getName().equalsIgnoreCase(name))
                return choice;
        }
        throw new AssertionError("Missing slider choice: " + name);
    }
}
