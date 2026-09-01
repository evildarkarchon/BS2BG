package com.asdasfa.jbs2bg.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies slider-choice validation and UUNP default rebuilding through the
 * public ProjectSession seam, with deterministic Slider settings.
 */
class ProjectSessionSliderChoiceTest {

    @TempDir
    Path tempDirectory;

    /**
     * Builds an explicit choice with both stored values present.
     *
     * @param name    slider name
     * @param small   stored and effective small value
     * @param big     stored and effective big value
     * @param minimum lower randomization percentage
     * @param maximum upper randomization percentage
     * @return an explicit, enabled slider choice
     */
    private static SliderChoiceSnapshot explicit(String name, int small, int big, int minimum, int maximum) {
        return new SliderChoiceSnapshot(name, true, Integer.valueOf(small), Integer.valueOf(big), small, big,
                minimum, maximum, false);
    }

    /**
     * Asserts one all-default synthesized choice with the given effective values.
     */
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

    /**
     * Asserts an explicit choice whose stored and effective values are both present.
     */
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

    /**
     * Asserts the structured slider-choice rejection contract at a stable element.
     */
    private static void assertSliderChoiceRejected(ProjectOutcome outcome, String code, String element,
                                                   ProjectSnapshot snapshot) {
        assertInstanceOf(RejectedOutcome.class, outcome);
        assertSame(snapshot, outcome.getSnapshot());
        ProjectDiagnostic diagnostic = outcome.getDiagnostics().get(0);
        assertEquals(code, diagnostic.getCode());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.getSeverity());
        assertEquals(element, diagnostic.getSourceLocation().getElement().get());
    }

    /**
     * Returns slider-choice names in published order.
     */
    private static List<String> names(SliderPresetSnapshot preset) {
        List<String> names = new ArrayList<>();
        for (SliderChoiceSnapshot choice : preset.getSliderChoices())
            names.add(choice.getName());
        return names;
    }

    /**
     * Finds one choice by case-insensitive name, failing when absent.
     */
    private static SliderChoiceSnapshot find(SliderPresetSnapshot preset, String name) {
        for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
            if (choice.getName().equalsIgnoreCase(name))
                return choice;
        }
        throw new AssertionError("Missing slider choice: " + name);
    }

    /**
     * Seeds distinct regular and UUNP defaults so mode changes are observable.
     */
    @BeforeEach
    void initializeSliderSettings() {
        Map<String, DefaultSliderValue> standard = new LinkedHashMap<>();
        standard.put("Breasts", new DefaultSliderValue(0.2f, 1f));
        standard.put("Legs", new DefaultSliderValue(0f, 1f));
        standard.put("Waist", new DefaultSliderValue(0f, 1f));
        Map<String, DefaultSliderValue> uunp = new LinkedHashMap<>();
        uunp.put("Arms", new DefaultSliderValue(1f, 1f));
        uunp.put("Breasts", new DefaultSliderValue(1f, 1f));
        uunp.put("Legs", new DefaultSliderValue(1f, 1f));
        SettingsTestSupport.installDefaults(standard, uunp);
    }

    /**
     * Restores process-wide Slider settings after each test.
     */
    @AfterEach
    void restoreSliderSettings() {
        SettingsTestSupport.restoreRepositorySettings();
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

        assertInstanceOf(ChangedOutcome.class, fullRange);
        assertInstanceOf(ChangedOutcome.class, collapsedRange);
        // Breasts and Legs are the standard defaults synthesized at creation; Waist's
        // synthesized default was replaced by the explicit choice above.
        assertEquals(Arrays.asList("Arms", "Breasts", "Legs", "Waist"),
                names(collapsedRange.getSnapshot().getSliderPresets().get(0)));
    }

    /**
     * Rejects blank slider-choice names on both edit paths, because the Project file
     * loader rejects them and a published blank name could not round-trip.
     */
    @Test
    void sliderChoiceEditsRejectBlankNames() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        ProjectSnapshot before = session.apply(SliderPresetEdits.create("Alpha")).getSnapshot();

        for (String blankName : Arrays.asList("", "   ")) {
            SliderChoiceSnapshot blank = explicit(blankName, 20, 80, 0, 100);
            ProjectOutcome single = session.apply(SliderPresetEdits.setSliderChoice("Alpha", blank));
            assertSliderChoiceRejected(single, ProjectDiagnosticCodes.SLIDER_CHOICE_NAME_REQUIRED,
                    "slider-preset.slider-choice.name", before);

            ProjectOutcome full = session.apply(SliderPresetEdits.update("Alpha",
                    new SliderPresetSnapshot("Alpha", false, Arrays.asList(explicit("Arms", 5, 95, 0, 100), blank))));
            assertSliderChoiceRejected(full, ProjectDiagnosticCodes.SLIDER_CHOICE_NAME_REQUIRED,
                    "slider-preset.slider-choice.name", before);
        }
        assertSame(before, session.getSnapshot());
    }

    /**
     * Seeds a created Slider Preset with the standard mode's synthesized defaults so
     * its generated output is identical before and after a save/reopen cycle.
     *
     * @throws Exception when the temporary Project cannot be saved
     */
    @Test
    void creatingSliderPresetSynthesizesStandardDefaultsMatchingReopen() throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();

        ProjectOutcome created = session.apply(SliderPresetEdits.create("Alpha"));
        SliderPresetSnapshot preset = created.getSnapshot().getSliderPresets().get(0);

        assertInstanceOf(ChangedOutcome.class, created);
        assertFalse(preset.isUunp());
        assertEquals(Arrays.asList("Breasts", "Legs", "Waist"), names(preset));
        assertSynthesized(find(preset, "Breasts"), 20, 100);
        assertSynthesized(find(preset, "Legs"), 0, 100);
        assertSynthesized(find(preset, "Waist"), 0, 100);

        Path savedFile = tempDirectory.resolve("created.jbs2bg");
        assertInstanceOf(ChangedOutcome.class, session.saveAs(savedFile));
        SliderPresetSnapshot reopened = ProjectSessions.create().open(savedFile).getSnapshot()
                .getSliderPresets().get(0);

        assertEquals(names(preset), names(reopened));
        assertSynthesized(find(reopened, "Breasts"), 20, 100);
        assertSynthesized(find(reopened, "Legs"), 0, 100);
        assertSynthesized(find(reopened, "Waist"), 0, 100);
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

        assertInstanceOf(ChangedOutcome.class, toUunp);
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
        assertInstanceOf(ChangedOutcome.class, editedSynthesized);
        assertTrue(find(editedSynthesized.getSnapshot().getSliderPresets().get(0), "Legs").isMissingDefault());

        ProjectOutcome toRegular = session.apply(SliderPresetEdits.setUunp("Alpha", false));
        SliderPresetSnapshot regularPreset = toRegular.getSnapshot().getSliderPresets().get(0);

        assertInstanceOf(ChangedOutcome.class, toRegular);
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

        assertInstanceOf(ChangedOutcome.class, outcome);
        assertTrue(preset.isUunp());
        assertEquals(Arrays.asList("Arms", "Breasts", "Legs"), names(preset));
        assertSynthesized(find(preset, "Arms"), 100, 100);
        assertExplicitStored(find(preset, "Breasts"), 30, 90, 10, 90);
        assertSynthesized(find(preset, "Legs"), 100, 100);

        ProjectOutcome sameMode = session.apply(SliderPresetEdits.update("Alpha",
                new SliderPresetSnapshot("Alpha", true, preset.getSliderChoices())));
        assertInstanceOf(UnchangedOutcome.class, sameMode);
        assertSame(outcome.getSnapshot(), sameMode.getSnapshot());
    }

    /**
     * Derives a single-choice edit's effective values from its stored endpoints and
     * the Slider Preset's mode rather than trusting the caller. Only stored endpoints
     * are persisted, so publishing divergent effective values would change generated
     * output across a save/reopen cycle.
     */
    @Test
    void singleChoiceEditRederivesEffectiveValuesFromStoredEndpoints() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        SliderChoiceSnapshot divergentWaist = new SliderChoiceSnapshot("Waist", true, Integer.valueOf(20),
                Integer.valueOf(80), 55, 65, 10, 90, false);
        SliderChoiceSnapshot deferringBreasts = new SliderChoiceSnapshot("Breasts", true, null, null, 7, 7, 0, 100,
                false);

        ProjectOutcome waistOutcome = session.apply(SliderPresetEdits.setSliderChoice("Alpha", divergentWaist));
        ProjectOutcome breastsOutcome = session.apply(SliderPresetEdits.setSliderChoice("Alpha", deferringBreasts));
        SliderPresetSnapshot preset = breastsOutcome.getSnapshot().getSliderPresets().get(0);

        assertInstanceOf(ChangedOutcome.class, waistOutcome);
        assertInstanceOf(ChangedOutcome.class, breastsOutcome);
        assertExplicitStored(find(preset, "Waist"), 20, 80, 10, 90);
        SliderChoiceSnapshot breasts = find(preset, "Breasts");
        assertFalse(breasts.isMissingDefault());
        assertFalse(breasts.getStoredSmallValue().isPresent());
        assertFalse(breasts.getStoredBigValue().isPresent());
        // Absent stored endpoints defer to the regular-mode Breasts default of 0.2/1.0.
        assertEquals(20, breasts.getEffectiveSmallValue());
        assertEquals(100, breasts.getEffectiveBigValue());

        // Different caller-supplied effective values over identical stored endpoints are
        // not an observable change once both derive from the same stored state.
        ProjectOutcome sameStored = session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                new SliderChoiceSnapshot("Waist", true, Integer.valueOf(20), Integer.valueOf(80), 1, 2, 10, 90,
                        false)));
        assertInstanceOf(UnchangedOutcome.class, sameStored);
        assertSame(breastsOutcome.getSnapshot(), sameStored.getSnapshot());
    }

    /**
     * Applies the same stored-endpoint derivation to a full update that keeps the
     * current mode, which previously published the caller's effective values verbatim.
     */
    @Test
    void sameModeFullUpdateRederivesEffectiveValuesFromStoredEndpoints() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        SliderChoiceSnapshot divergentWaist = new SliderChoiceSnapshot("Waist", true, Integer.valueOf(20),
                Integer.valueOf(80), 55, 65, 10, 90, false);

        ProjectOutcome outcome = session.apply(SliderPresetEdits.update("Alpha",
                new SliderPresetSnapshot("Alpha", false, Arrays.asList(divergentWaist))));
        SliderPresetSnapshot preset = outcome.getSnapshot().getSliderPresets().get(0);

        assertInstanceOf(ChangedOutcome.class, outcome);
        assertFalse(preset.isUunp());
        assertExplicitStored(find(preset, "Waist"), 20, 80, 10, 90);
    }

    /**
     * UI-representative enable and range edits persist changed synthesized defaults with null endpoints, continue to
     * omit untouched defaults, and reopen with the same observable profile, percentages, and exact preview text.
     *
     * @throws Exception when the temporary Project cannot be saved or inspected
     */
    @Test
    void sliderChoiceEditsPreservePreviewAndCanonicalOmissionAcrossSaveReopen() throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        SliderPresetSnapshot created = session.getSnapshot().getSliderPresets().get(0);
        session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                find(created, "Legs").withPercentageRange(25, 75)));
        session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                find(session.getSnapshot().getSliderPresets().get(0), "Waist").withEnabled(false)));
        SliderPresetSnapshot edited = session.getSnapshot().getSliderPresets().get(0);
        String legsPreview = ProjectOutputFormatter.formatSliderChoicePreview(find(edited, "Legs"), false);

        Path savedFile = tempDirectory.resolve("edited-choices.jbs2bg");
        session.saveAs(savedFile);
        String canonical = Files.readString(savedFile);
        SliderPresetSnapshot reopened = ProjectSessions.create().open(savedFile).getSnapshot()
                .getSliderPresets().get(0);

        assertFalse(canonical.contains("\"Breasts\""));
        assertTrue(canonical.contains("\"Legs\""));
        assertTrue(canonical.contains("\"Waist\""));
        assertFalse(reopened.isUunp());
        assertEquals(25, find(reopened, "Legs").getPercentageMinimum());
        assertEquals(75, find(reopened, "Legs").getPercentageMaximum());
        assertFalse(find(reopened, "Waist").isEnabled());
        assertEquals(legsPreview,
                ProjectOutputFormatter.formatSliderChoicePreview(find(reopened, "Legs"), false));
    }
}
