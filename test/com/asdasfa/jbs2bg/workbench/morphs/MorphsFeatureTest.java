package com.asdasfa.jbs2bg.workbench.morphs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorphsFeatureTest {

    /**
     * A condition-bearing name remains intact while creation publishes and selects only the immutable value accepted
     * by the authoritative Project flow.
     */
    @Test
    void createsBodyGenConditionTargetThroughProjectFlowAndSelectsIt() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        MorphsFeature feature = new MorphsFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC));

        MorphsFeature.Update update = feature.dispatch(new MorphsFeature.Create("  All|Female  "));

        assertTrue(update.accepted());
        assertEquals(MorphsFeature.OutcomeKind.CHANGED, update.outcomeKind());
        assertEquals(List.of("All|Female"), update.frame().visibleTargets().stream()
                .map(target -> target.getName()).toList());
        assertEquals(NameIdentity.of("All|Female"), update.frame().selection().orElseThrow());
        assertEquals(List.of("All|Female"), projectFlow.frame().snapshot().getCustomMorphTargets().stream()
                .map(target -> target.getName()).toList());
    }

    /**
     * Creation captures exactly one independently random relationship from the eligible Project catalog without
     * making a seed, sequence, or distribution part of compatibility.
     */
    @Test
    void creationAutomaticallyAssignsOneEligibleSliderPreset() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        MorphsFeature feature = new MorphsFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC), new Random(107L));

        MorphsFeature.Update created = feature.dispatch(new MorphsFeature.Create("All|Female"));
        List<String> assignments = created.frame().editor().orElseThrow().target().getSliderPresetNames();

        assertEquals(1, assignments.size());
        assertTrue(Set.of("Alpha", "Beta").contains(assignments.getFirst()));
    }

    /**
     * Relationship intents resolve case-insensitive identities through ProjectSession and retain the accepted
     * assignment when a later request names a missing Slider Preset.
     */
    @Test
    void editsSliderPresetRelationshipsWithoutBypassingProjectIntegrity() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        projectFlow.apply(CustomMorphTargetEdits.create("All|Female"));
        MorphsFeature feature = new MorphsFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new MorphsFeature.Select(NameIdentity.of("all|female")));

        MorphsFeature.Update assigned = feature.dispatch(
                new MorphsFeature.AssignSliderPreset(NameIdentity.of("ALPHA")));
        MorphsFeature.Update rejected = feature.dispatch(
                new MorphsFeature.AssignSliderPreset(NameIdentity.of("Missing")));

        assertEquals(MorphsFeature.OutcomeKind.CHANGED, assigned.outcomeKind());
        assertEquals(List.of("Alpha"), assigned.frame().editor().orElseThrow().assignedPresets().stream()
                .map(preset -> preset.getName()).toList());
        assertEquals(List.of("Beta"), assigned.frame().editor().orElseThrow().availablePresets().stream()
                .map(preset -> preset.getName()).toList());
        assertEquals(MorphsFeature.OutcomeKind.REJECTED, rejected.outcomeKind());
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND,
                rejected.frame().diagnostics().getFirst().getCode());
        assertEquals(List.of("Alpha"), rejected.frame().editor().orElseThrow().assignedPresets().stream()
                .map(preset -> preset.getName()).toList());
    }

    /**
     * Sorting and Project row replacement retain a visible logical identity, while filtering or deletion clears it
     * permanently instead of restoring or retargeting the selection.
     */
    @Test
    void reconcilesSelectionByIdentityAcrossFilteringSortingAndProjectRefresh() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(CustomMorphTargetEdits.create("Alpha"));
        projectFlow.apply(CustomMorphTargetEdits.create("Beta"));
        projectFlow.apply(CustomMorphTargetEdits.create("Gamma"));
        MorphsFeature feature = new MorphsFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new MorphsFeature.Select(NameIdentity.of("Beta")));

        MorphsFeature.Update sorted = feature.dispatch(
                new MorphsFeature.ChangeSort(MorphsFeature.SortOrder.NAME_DESCENDING));
        MorphsFeature.Update hidden = feature.dispatch(new MorphsFeature.ChangeFilter("alp"));
        MorphsFeature.Update revealed = feature.dispatch(new MorphsFeature.ChangeFilter(""));
        feature.dispatch(new MorphsFeature.Select(NameIdentity.of("Beta")));
        projectFlow.apply(CustomMorphTargetEdits.create("Delta"));
        MorphsFeature.Update refreshed = feature.acceptProjectFrame(projectFlow.frame(), false);
        projectFlow.apply(CustomMorphTargetEdits.delete("beta"));
        MorphsFeature.Update removed = feature.acceptProjectFrame(projectFlow.frame(), false);

        assertEquals(List.of("Gamma", "Beta", "Alpha"), sorted.frame().visibleTargets().stream()
                .map(target -> target.getName()).toList());
        assertEquals(NameIdentity.of("Beta"), sorted.frame().selection().orElseThrow());
        assertEquals(List.of("Alpha"), hidden.frame().visibleTargets().stream()
                .map(target -> target.getName()).toList());
        assertTrue(hidden.frame().selection().isEmpty());
        assertTrue(revealed.frame().selection().isEmpty());
        assertEquals(NameIdentity.of("Beta"), refreshed.frame().selection().orElseThrow());
        assertTrue(removed.frame().selection().isEmpty());
    }

    /**
     * Clear-visible confirmation captures one immutable identity set and prevents later control gestures from
     * changing the operand while the destructive decision is pending.
     */
    @Test
    void confirmedClearDeletesOnlyTheCapturedVisibleTargets() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(CustomMorphTargetEdits.create("Alpha"));
        projectFlow.apply(CustomMorphTargetEdits.create("Beta"));
        projectFlow.apply(CustomMorphTargetEdits.create("Gamma"));
        MorphsFeature feature = new MorphsFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new MorphsFeature.ChangeFilter("alp"));

        MorphsFeature.Update requested = feature.dispatch(new MorphsFeature.RequestClearVisible());
        MorphsFeature.Effect effect = requested.effect().orElseThrow();
        MorphsFeature.Update blocked = feature.dispatch(new MorphsFeature.ChangeFilter(""));
        MorphsFeature.Update confirmed = feature.respond(effect.token(), true);

        assertEquals(MorphsFeature.EffectKind.CONFIRM_CLEAR_VISIBLE, effect.kind());
        assertEquals(List.of(NameIdentity.of("Alpha")), effect.identities());
        assertTrue(requested.accepted());
        assertTrue(!blocked.accepted());
        assertEquals(MorphsFeature.OutcomeKind.CHANGED, confirmed.outcomeKind());
        assertEquals(List.of("Beta", "Gamma"), projectFlow.frame().snapshot().getCustomMorphTargets().stream()
                .map(target -> target.getName()).toList());
    }

    /**
     * Relationship bulk-add, selection, rename reconciliation, removal, and confirmed clear all operate on stable
     * Slider Preset identities and always render the accepted Project snapshot.
     */
    @Test
    void managesAllRelationshipsWithoutSilentlyRetargetingAssignedSelection() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(SliderPresetEdits.create("Alpha"));
        projectFlow.apply(SliderPresetEdits.create("Beta"));
        projectFlow.apply(SliderPresetEdits.create("Gamma"));
        projectFlow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha")));
        MorphsFeature feature = new MorphsFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new MorphsFeature.Select(NameIdentity.of("All|Female")));

        MorphsFeature.Update assignedAll = feature.dispatch(new MorphsFeature.AssignAllSliderPresets());
        feature.dispatch(new MorphsFeature.SelectAssignedSliderPreset(NameIdentity.of("Beta")));
        projectFlow.apply(SliderPresetEdits.rename("Beta", "Delta"));
        MorphsFeature.Update renamed = feature.acceptProjectFrame(projectFlow.frame(), false);
        feature.dispatch(new MorphsFeature.SelectAssignedSliderPreset(NameIdentity.of("Delta")));
        MorphsFeature.Update removed = feature.dispatch(new MorphsFeature.RemoveAssignedSliderPreset());
        MorphsFeature.Update requested = feature.dispatch(new MorphsFeature.RequestClearAssignments());
        MorphsFeature.Update cleared = feature.respond(requested.effect().orElseThrow().token(), true);

        assertEquals(List.of("Alpha", "Beta", "Gamma"), assignedAll.frame().editor().orElseThrow()
                .assignedPresets().stream().map(preset -> preset.getName()).toList());
        assertEquals(NameIdentity.of("All|Female"), renamed.frame().selection().orElseThrow());
        assertTrue(renamed.frame().editor().orElseThrow().assignedSelection().isEmpty());
        assertEquals(List.of("Alpha", "Gamma"), removed.frame().editor().orElseThrow().assignedPresets().stream()
                .map(preset -> preset.getName()).toList());
        assertEquals(MorphsFeature.EffectKind.CONFIRM_CLEAR_ASSIGNMENTS,
                requested.effect().orElseThrow().kind());
        assertTrue(cleared.frame().editor().orElseThrow().assignedPresets().isEmpty());
    }

    /**
     * Type-ahead follows the visible sorted order, cycles repeated characters, and resets after the accepted timeout.
     */
    @Test
    void typeAheadCyclesVisibleMatchesAndResetsAfterTimeout() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(CustomMorphTargetEdits.create("Alpha"));
        projectFlow.apply(CustomMorphTargetEdits.create("Amber"));
        projectFlow.apply(CustomMorphTargetEdits.create("Beta"));
        MutableClock clock = new MutableClock(Instant.parse("2026-09-02T12:00:00Z"));
        MorphsFeature feature = new MorphsFeature(projectFlow, clock);

        feature.dispatch(new MorphsFeature.TypeAhead('a'));
        NameIdentity first = feature.frame().selection().orElseThrow();
        feature.dispatch(new MorphsFeature.TypeAhead('a'));
        NameIdentity cycled = feature.frame().selection().orElseThrow();
        clock.advance(Duration.ofMillis(751));
        feature.dispatch(new MorphsFeature.TypeAhead('b'));

        assertEquals(NameIdentity.of("Alpha"), first);
        assertEquals(NameIdentity.of("Amber"), cycled);
        assertEquals(NameIdentity.of("Beta"), feature.frame().selection().orElseThrow());
    }

    /**
     * Validation is durable feature state, committed frames reach healthy observers, and one broken renderer cannot
     * stall publication or outlive a closed subscription.
     */
    @Test
    void publishesValidationFramesWhileIsolatingObserverFailures() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        List<Throwable> failures = new ArrayList<>();
        MorphsFeature feature = new MorphsFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC), failures::add);
        List<MorphsFeature.Frame> observed = new ArrayList<>();
        MorphsFeature.Subscription subscription = feature.observe(observed::add);
        feature.observe(frame -> {
            throw new IllegalStateException("broken Morphs renderer");
        });

        MorphsFeature.Update rejected = feature.dispatch(new MorphsFeature.Create("   "));
        MorphsFeature.Update dismissed = feature.dispatch(new MorphsFeature.DismissDiagnostics());
        int beforeClose = observed.size();
        subscription.close();
        feature.dispatch(new MorphsFeature.ChangeSort(MorphsFeature.SortOrder.NAME_DESCENDING));

        assertEquals(MorphsFeature.OutcomeKind.REJECTED, rejected.outcomeKind());
        assertEquals(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_REQUIRED,
                rejected.frame().diagnostics().getFirst().getCode());
        assertTrue(dismissed.frame().diagnostics().isEmpty());
        assertEquals(List.of("broken Morphs renderer", "broken Morphs renderer", "broken Morphs renderer"),
                failures.stream().map(Throwable::getMessage).toList());
        assertEquals(beforeClose, observed.size());
    }

    /**
     * Remove confirmation captures the selected target; cancelling preserves it, while confirming removes it and
     * leaves no selection instead of silently moving to a neighboring row.
     */
    @Test
    void removeConfirmationNeverRetargetsSelection() {
        WorkbenchProjectFlow projectFlow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        projectFlow.apply(CustomMorphTargetEdits.create("Alpha"));
        projectFlow.apply(CustomMorphTargetEdits.create("Beta"));
        MorphsFeature feature = new MorphsFeature(projectFlow,
                Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC));
        feature.dispatch(new MorphsFeature.Select(NameIdentity.of("Beta")));

        MorphsFeature.Update requested = feature.dispatch(new MorphsFeature.RequestRemove());
        MorphsFeature.Update cancelled = feature.respond(requested.effect().orElseThrow().token(), false);
        MorphsFeature.Update requestedAgain = feature.dispatch(new MorphsFeature.RequestRemove());
        MorphsFeature.Update removed = feature.respond(requestedAgain.effect().orElseThrow().token(), true);

        assertEquals(MorphsFeature.EffectKind.CONFIRM_REMOVE, requested.effect().orElseThrow().kind());
        assertEquals(List.of(NameIdentity.of("Beta")), requested.effect().orElseThrow().identities());
        assertEquals(NameIdentity.of("Beta"), cancelled.frame().selection().orElseThrow());
        assertEquals(List.of("Alpha"), removed.frame().visibleTargets().stream()
                .map(target -> target.getName()).toList());
        assertTrue(removed.frame().selection().isEmpty());
        assertTrue(feature.dispatch(new MorphsFeature.ClearSelection()).accepted());
    }

    /** Minimal deterministic clock used to cross the type-ahead timeout without sleeping. */
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
}
