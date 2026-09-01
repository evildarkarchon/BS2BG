package com.asdasfa.jbs2bg.workbench.templates;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.ProjectEdit;
import com.asdasfa.jbs2bg.project.ProjectOperationContext;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetImportOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.project.SourceLocation;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplatesFeatureTest {

    /**
     * Publishes distinct profile defaults so profile switches and preview text are independently observable.
     */
    @BeforeEach
    void initializeSliderSettings() {
        Map<String, DefaultSliderValue> standard = new LinkedHashMap<>();
        standard.put("Waist", new DefaultSliderValue(0.2f, 0.8f));
        Map<String, DefaultSliderValue> uunp = new LinkedHashMap<>();
        uunp.put("Arms", new DefaultSliderValue(0.1f, 0.5f));
        SettingsTestSupport.installDefaults(standard, uunp);
    }

    /**
     * Restores repository Settings so this feature fixture cannot leak into other test classes.
     */
    @AfterEach
    void restoreSliderSettings() {
        SettingsTestSupport.restoreRepositorySettings();
    }

    /**
     * Selecting a Slider Preset exposes every synthesized profile choice with exact BodyGen preview text; changing
     * profile writes through the authoritative Project flow while preserving the selected logical identity.
     */
    @Test
    void profileSwitchRebuildsSynthesizedChoicesAndPublishesExactPreview() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Alpha")));

        TemplatesFeature.EditorFrame standard = feature.frame().editor().orElseThrow();

        assertEquals(TemplatesFeature.Profile.STANDARD, standard.profile());
        assertEquals(List.of("Waist"), standard.choices().stream()
                .map(TemplatesFeature.ChoiceFrame::name).toList());
        assertEquals("Waist@0.8", standard.choices().getFirst().previewText());
        assertTrue(standard.choices().getFirst().synthesizedDefault());
        assertTrue(standard.choices().getFirst().omittedFromProjectFile());

        TemplatesFeature.Update switched = feature.dispatch(
                new TemplatesFeature.ChangeProfile(TemplatesFeature.Profile.UUNP));
        TemplatesFeature.EditorFrame uunp = switched.frame().editor().orElseThrow();

        assertTrue(switched.accepted());
        assertEquals(NameIdentity.of("Alpha"), switched.frame().selection().orElseThrow());
        assertEquals(TemplatesFeature.Profile.UUNP, uunp.profile());
        assertEquals(List.of("Arms"), uunp.choices().stream()
                .map(TemplatesFeature.ChoiceFrame::name).toList());
        assertEquals("Arms@0.5", uunp.choices().getFirst().previewText());
        assertTrue(uunp.choices().getFirst().synthesizedDefault());
    }

    /**
     * One row intent publishes the session's exact Changed/Unchanged distinction, keeps the stable preset selected,
     * and renders from the immutable value returned by ProjectSession rather than a control-local draft.
     */
    @Test
    void rowEnableEditDistinguishesChangedFromUnchangedAndPreservesSelection() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Alpha")));

        TemplatesFeature.Update changed = feature.dispatch(
                new TemplatesFeature.SetChoiceEnabled("Waist", false));
        TemplatesFeature.Update unchanged = feature.dispatch(
                new TemplatesFeature.SetChoiceEnabled("Waist", false));

        assertEquals(TemplatesFeature.OutcomeKind.CHANGED, changed.outcomeKind());
        assertFalse(changed.frame().editor().orElseThrow().choices().getFirst().enabled());
        assertEquals(NameIdentity.of("Alpha"), changed.frame().selection().orElseThrow());
        assertEquals(TemplatesFeature.OutcomeKind.UNCHANGED, unchanged.outcomeKind());
        assertEquals(NameIdentity.of("Alpha"), unchanged.frame().selection().orElseThrow());
    }

    /**
     * Row range edits publish exact live-preview text, while a reversed range is rejected by ProjectSession and the
     * editor retains the last accepted values, selection, and inline diagnostic.
     */
    @Test
    void rowRangeEditPublishesPreviewAndRetainsAcceptedStateAfterValidationRejection() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Alpha")));

        TemplatesFeature.Update changed = feature.dispatch(
                new TemplatesFeature.SetChoiceRange("Waist", 25, 75));
        TemplatesFeature.Update rejected = feature.dispatch(
                new TemplatesFeature.SetChoiceRange("Waist", 90, 10));
        TemplatesFeature.ChoiceFrame retained = rejected.frame().editor().orElseThrow().choices().getFirst();

        assertEquals(TemplatesFeature.OutcomeKind.CHANGED, changed.outcomeKind());
        assertEquals("Waist@0.35:0.65", changed.frame().editor().orElseThrow().choices().getFirst().previewText());
        assertFalse(changed.frame().editor().orElseThrow().choices().getFirst().omittedFromProjectFile());
        assertEquals(TemplatesFeature.OutcomeKind.REJECTED, rejected.outcomeKind());
        assertEquals(25, retained.minimum());
        assertEquals(75, retained.maximum());
        assertFalse(rejected.frame().diagnostics().isEmpty());
        assertEquals(NameIdentity.of("Alpha"), rejected.frame().selection().orElseThrow());
    }

    /**
     * All-Min edits every enabled row in one Project publication, leaves omitted rows untouched, and mutually
     * exclusive gang modes lock the rows without allowing the legacy All-Min/All-Max overlap.
     */
    @Test
    void gangOperationsAreAtomicEnabledOnlyAndMutuallyExclusive() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.setSliderChoice("Alpha",
                new SliderChoiceSnapshot("Arms", true, Integer.valueOf(10), Integer.valueOf(90), 10, 90,
                        10, 90, false)));
        projectFlow.apply(SliderPresetEdits.setSliderChoice("Alpha",
                projectFlow.frame().snapshot().getSliderPresets().getFirst().getSliderChoices().stream()
                        .filter(choice -> choice.getName().equals("Waist")).findFirst().orElseThrow()
                        .withEnabled(false)));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Alpha")));
        long beforeSequence = feature.frame().projectSequence();

        TemplatesFeature.Update allMinimum = feature.dispatch(
                new TemplatesFeature.ApplyBulkValue(TemplatesFeature.GangMode.MINIMUM, 50));

        assertEquals(TemplatesFeature.OutcomeKind.CHANGED, allMinimum.outcomeKind());
        assertEquals(beforeSequence + 1, allMinimum.frame().projectSequence());
        assertEquals(50, choiceNamed(allMinimum.frame(), "Arms").minimum());
        assertEquals(90, choiceNamed(allMinimum.frame(), "Arms").maximum());
        assertEquals(100, choiceNamed(allMinimum.frame(), "Waist").minimum());
        assertFalse(choiceNamed(allMinimum.frame(), "Waist").enabled());

        TemplatesFeature.Update minimumGang = feature.dispatch(
                new TemplatesFeature.ToggleGang(TemplatesFeature.GangMode.MINIMUM, true));
        TemplatesFeature.Update maximumGang = feature.dispatch(
                new TemplatesFeature.ToggleGang(TemplatesFeature.GangMode.MAXIMUM, true));

        assertEquals(TemplatesFeature.GangMode.MINIMUM,
                minimumGang.frame().editor().orElseThrow().gang().activeMode().orElseThrow());
        assertEquals(TemplatesFeature.GangMode.MAXIMUM,
                maximumGang.frame().editor().orElseThrow().gang().activeMode().orElseThrow());
        assertTrue(maximumGang.frame().editor().orElseThrow().gang().rowsLocked());
        assertEquals(NameIdentity.of("Alpha"), maximumGang.frame().selection().orElseThrow());
    }

    /**
     * An environmental ProjectSession failure remains distinct from validation rejection and preserves the selected
     * identity and last committed row values for controller feedback and focus restoration.
     */
    @Test
    void failedRowEditPreservesSelectionAndLastCommittedEditorFrame() {
        FailingApplySession session = new FailingApplySession(ProjectSessions.create());
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", session);
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        session.failEdits();
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Alpha")));

        TemplatesFeature.Update failed = feature.dispatch(
                new TemplatesFeature.SetChoiceRange("Waist", 25, 75));
        TemplatesFeature.Update failedGang = feature.dispatch(
                new TemplatesFeature.ToggleGang(TemplatesFeature.GangMode.MINIMUM, true));

        assertEquals(TemplatesFeature.OutcomeKind.FAILED, failed.outcomeKind());
        assertEquals(NameIdentity.of("Alpha"), failed.frame().selection().orElseThrow());
        assertEquals(100, choiceNamed(failed.frame(), "Waist").minimum());
        assertEquals(100, choiceNamed(failed.frame(), "Waist").maximum());
        assertEquals("TEST_SLIDER_EDIT_FAILURE", failed.frame().diagnostics().getFirst().getCode());
        assertEquals(TemplatesFeature.OutcomeKind.FAILED, failedGang.outcomeKind());
        assertTrue(failedGang.frame().editor().orElseThrow().gang().activeMode().isEmpty());
        assertFalse(failedGang.frame().editor().orElseThrow().gang().rowsLocked());
    }

    /**
     * A typed Create intent writes through the authoritative Project flow, then selects the returned logical identity
     * in the immutable Templates frame.
     */
    @Test
    void createPublishesAndSelectsTheReturnedSliderPresetIdentity() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Gamma"));
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        assertEquals(List.of("Alpha", "Gamma"), names(feature.frame().visiblePresets()));
        assertTrue(feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("gamma"))).accepted());

        TemplatesFeature.Update created = feature.dispatch(new TemplatesFeature.Create(" Beta "));

        assertTrue(created.accepted());
        assertEquals(List.of("Alpha", "Beta", "Gamma"), names(created.frame().visiblePresets()));
        assertEquals(NameIdentity.of("Beta"), created.frame().selection().orElseThrow());
        assertEquals(projectFlow.frame().sequence(), created.frame().projectSequence());
    }

    /**
     * Filtering a selected identity out clears it permanently instead of retaining a hidden selection or restoring it
     * when the filter is removed.
     */
    @Test
    void filteringOutTheSelectionNeverSilentlyRestoresOrRetargetsIt() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Beta")));

        TemplatesFeature.Update filtered = feature.dispatch(new TemplatesFeature.ChangeFilter("alp"));

        assertTrue(filtered.accepted());
        assertEquals(List.of("Alpha"), names(filtered.frame().visiblePresets()));
        assertTrue(filtered.frame().selection().isEmpty());

        TemplatesFeature.Update restored = feature.dispatch(new TemplatesFeature.ChangeFilter(""));

        assertEquals(List.of("Alpha", "Beta"), names(restored.frame().visiblePresets()));
        assertTrue(restored.frame().selection().isEmpty());
    }

    /**
     * Sorting retains the selected identity while repeated-character type-ahead cycles the visible sorted matches and
     * starts a fresh prefix after the accepted timeout.
     */
    @Test
    void typeAheadUsesVisibleSortedOrderAndResetsAfterSevenHundredFiftyMilliseconds() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        projectFlow.apply(SliderPresetEdits.create("Bravo"));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-31T12:00:00Z"));
        TemplatesFeature feature = new TemplatesFeature(projectFlow, clock);
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Alpha")));

        TemplatesFeature.Update sorted = feature.dispatch(
                new TemplatesFeature.ChangeSort(TemplatesFeature.SortOrder.NAME_DESCENDING));

        assertEquals(List.of("Bravo", "Beta", "Alpha"), names(sorted.frame().visiblePresets()));
        assertEquals(NameIdentity.of("Alpha"), sorted.frame().selection().orElseThrow());

        assertEquals(NameIdentity.of("Bravo"), feature.dispatch(new TemplatesFeature.TypeAhead('b'))
                .frame().selection().orElseThrow());
        assertEquals(NameIdentity.of("Beta"), feature.dispatch(new TemplatesFeature.TypeAhead('b'))
                .frame().selection().orElseThrow());
        clock.advance(Duration.ofMillis(751));
        assertEquals(NameIdentity.of("Alpha"), feature.dispatch(new TemplatesFeature.TypeAhead('a'))
                .frame().selection().orElseThrow());
    }

    /**
     * Duplicate targets the selected logical identity, copies its immutable value through ProjectSession, and selects
     * the returned canonical copy.
     */
    @Test
    void duplicateCopiesTheSelectedSliderPresetAndSelectsTheCopy() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.setUunp("Alpha", true));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("alpha")));

        TemplatesFeature.Update duplicated = feature.dispatch(new TemplatesFeature.Duplicate(" Beta "));

        assertTrue(duplicated.accepted());
        assertEquals(List.of("Alpha", "Beta"), names(duplicated.frame().visiblePresets()));
        assertEquals(NameIdentity.of("Beta"), duplicated.frame().selection().orElseThrow());
        assertTrue(duplicated.frame().visiblePresets().get(1).isUunp());
    }

    /**
     * Inline rename retains its draft and structured validation after rejection, then a valid retry closes the editor
     * and publishes the complete relationship cascade under the returned identity.
     */
    @Test
    void inlineRenameRetainsRejectedDraftThenPublishesReferentialCascade() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        projectFlow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha")));
        projectFlow.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot(
                "Aela", "Skyrim.esm", "Aela", "NordRace", "1A696", List.of("Alpha"))));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Alpha")));
        feature.dispatch(new TemplatesFeature.BeginRename());
        feature.dispatch(new TemplatesFeature.ChangeRename("Beta"));

        TemplatesFeature.Update rejected = feature.dispatch(new TemplatesFeature.CommitRename());

        assertFalse(rejected.accepted());
        assertEquals("Beta", rejected.frame().rename().orElseThrow().draft());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE,
                rejected.frame().rename().orElseThrow().diagnostics().getFirst().getCode());
        assertEquals(NameIdentity.of("Alpha"), rejected.frame().selection().orElseThrow());

        feature.dispatch(new TemplatesFeature.ChangeRename("Gamma"));
        TemplatesFeature.Update renamed = feature.dispatch(new TemplatesFeature.CommitRename());

        assertTrue(renamed.accepted());
        assertTrue(renamed.frame().rename().isEmpty());
        assertEquals(NameIdentity.of("Gamma"), renamed.frame().selection().orElseThrow());
        assertEquals(List.of("Gamma"), projectFlow.frame().snapshot().getCustomMorphTargets().getFirst()
                .getSliderPresetNames());
        assertEquals(List.of("Gamma"), projectFlow.frame().snapshot().getNpcMorphAssignments().getFirst()
                .getSliderPresetNames());
    }

    /**
     * Remove freezes the selected identity into a confirmation and, once confirmed, clears selection rather than
     * retargeting the remaining row while ProjectSession removes every relationship atomically.
     */
    @Test
    void confirmedRemoveDeletesTheCapturedIdentityWithoutRetargetingSelection() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        projectFlow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha", "Beta")));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.Select(NameIdentity.of("Alpha")));

        TemplatesFeature.Update requested = feature.dispatch(new TemplatesFeature.RequestRemove());

        TemplatesFeature.Effect confirmation = requested.effect().orElseThrow();
        assertEquals(TemplatesFeature.EffectKind.CONFIRM_REMOVE, confirmation.kind());
        assertEquals(List.of(NameIdentity.of("Alpha")), confirmation.identities());
        assertEquals(List.of("Alpha", "Beta"), names(requested.frame().visiblePresets()));

        TemplatesFeature.Update removed = feature.respond(confirmation.token(), true);

        assertTrue(removed.accepted());
        assertEquals(List.of("Beta"), names(removed.frame().visiblePresets()));
        assertTrue(removed.frame().selection().isEmpty());
        assertEquals(List.of("Beta"), projectFlow.frame().snapshot().getCustomMorphTargets().getFirst()
                .getSliderPresetNames());
    }

    /**
     * Clear Visible captures the filtered identity set and removes only those Slider Presets and their references in
     * one authoritative edit, preserving every hidden preset.
     */
    @Test
    void confirmedClearVisiblePreservesHiddenSliderPresetsAndRelationships() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        projectFlow.apply(SliderPresetEdits.create("Gamma"));
        projectFlow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha", "Beta", "Gamma")));
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new TemplatesFeature.ChangeFilter("bet"));

        TemplatesFeature.Update requested = feature.dispatch(new TemplatesFeature.RequestClearVisible());

        TemplatesFeature.Effect confirmation = requested.effect().orElseThrow();
        assertEquals(TemplatesFeature.EffectKind.CONFIRM_CLEAR_VISIBLE, confirmation.kind());
        assertEquals(List.of(NameIdentity.of("Beta")), confirmation.identities());

        TemplatesFeature.Update cleared = feature.respond(confirmation.token(), true);

        assertTrue(cleared.accepted());
        assertTrue(cleared.frame().visiblePresets().isEmpty());
        assertEquals(List.of("Alpha", "Gamma"), projectFlow.frame().snapshot().getSliderPresets().stream()
                .map(SliderPresetSnapshot::getName).toList());
        assertEquals(List.of("Alpha", "Gamma"), projectFlow.frame().snapshot().getCustomMorphTargets().getFirst()
                .getSliderPresetNames());
    }

    /**
     * Feature observers see state only after commit, callback failures are isolated and reported, and a closed
     * subscription receives no later frames.
     */
    @Test
    void observersReceiveCommittedFramesAndIsolateCallbackFailures() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        List<Throwable> failures = new ArrayList<>();
        TemplatesFeature feature = new TemplatesFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC), failures::add);
        List<TemplatesFeature.Frame> observed = new ArrayList<>();
        TemplatesFeature.Subscription subscription = feature.observe(observed::add);
        feature.observe(frame -> {
            throw new IllegalStateException("broken Templates renderer");
        });

        TemplatesFeature.Update filtered = feature.dispatch(new TemplatesFeature.ChangeFilter("alp"));

        assertSame(filtered.frame(), observed.getLast());
        assertEquals(List.of("broken Templates renderer"), failures.stream().map(Throwable::getMessage).toList());
        int beforeClose = observed.size();
        subscription.close();
        feature.dispatch(new TemplatesFeature.ChangeSort(TemplatesFeature.SortOrder.NAME_DESCENDING));
        assertEquals(beforeClose, observed.size());
    }

    /**
     * @return display names in presentation order
     */
    private static List<String> names(List<SliderPresetSnapshot> presets) {
        return presets.stream().map(SliderPresetSnapshot::getName).toList();
    }

    /**
     * Finds one rendered Slider choice by stable case-insensitive name.
     */
    private static TemplatesFeature.ChoiceFrame choiceNamed(TemplatesFeature.Frame frame, String name) {
        return frame.editor().orElseThrow().choices().stream()
                .filter(choice -> choice.name().equalsIgnoreCase(name)).findFirst().orElseThrow();
    }

    /**
     * Minimal deterministic clock used to cross the type-ahead timeout without sleeping.
     */
    private static final class MutableClock extends Clock {
        private Instant now;

        /** Creates the clock at one stable instant. */
        private MutableClock(Instant now) {
            this.now = now;
        }

        /** Advances the instant monotonically for one interaction step. */
        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        /** {@inheritDoc} */
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        /** {@inheritDoc} */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /** {@inheritDoc} */
        @Override
        public Instant instant() {
            return now;
        }
    }

    /**
     * ProjectSession boundary adapter that preserves every read/lifecycle operation but returns a deterministic
     * environmental failure for Project edits.
     */
    private static final class FailingApplySession implements ProjectSession {
        private final ProjectSession delegate;
        private boolean failEdits;

        /** Creates the adapter around one already-seeded session. */
        private FailingApplySession(ProjectSession delegate) {
            this.delegate = delegate;
        }

        /** Arms deterministic failures after fixture setup has completed through the same public seam. */
        private void failEdits() {
            failEdits = true;
        }

        /** {@inheritDoc} */
        @Override
        public com.asdasfa.jbs2bg.project.ProjectSnapshot getSnapshot() {
            return delegate.getSnapshot();
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome newProject() {
            return delegate.newProject();
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome open(Path source, ProjectOperationContext context) {
            return delegate.open(source, context);
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome save(ProjectOperationContext context) {
            return delegate.save(context);
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome saveAs(Path target, ProjectOperationContext context) {
            return delegate.saveAs(target, context);
        }

        /** {@inheritDoc} */
        @Override
        public SliderPresetImportOutcome importSliderPresets(List<Path> sources, ProjectOperationContext context) {
            return delegate.importSliderPresets(sources, context);
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome apply(ProjectEdit edit) {
            if (!failEdits)
                return delegate.apply(edit);
            ProjectDiagnostic diagnostic = new ProjectDiagnostic("TEST_SLIDER_EDIT_FAILURE",
                    DiagnosticSeverity.ERROR, new SourceLocation(Optional.empty(),
                    Optional.of("slider-preset.slider-choice"), OptionalInt.empty(), OptionalInt.empty()),
                    "The test Slider choice could not be edited.");
            return new FailedOutcome(delegate.getSnapshot(), List.of(diagnostic));
        }
    }
}
