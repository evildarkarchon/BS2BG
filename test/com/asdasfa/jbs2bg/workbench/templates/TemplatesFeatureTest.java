package com.asdasfa.jbs2bg.workbench.templates;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplatesFeatureTest {

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
                rejected.frame().rename().orElseThrow().diagnostics().get(0).getCode());
        assertEquals(NameIdentity.of("Alpha"), rejected.frame().selection().orElseThrow());

        feature.dispatch(new TemplatesFeature.ChangeRename("Gamma"));
        TemplatesFeature.Update renamed = feature.dispatch(new TemplatesFeature.CommitRename());

        assertTrue(renamed.accepted());
        assertTrue(renamed.frame().rename().isEmpty());
        assertEquals(NameIdentity.of("Gamma"), renamed.frame().selection().orElseThrow());
        assertEquals(List.of("Gamma"), projectFlow.frame().snapshot().getCustomMorphTargets().get(0)
                .getSliderPresetNames());
        assertEquals(List.of("Gamma"), projectFlow.frame().snapshot().getNpcMorphAssignments().get(0)
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
        assertEquals(List.of("Beta"), projectFlow.frame().snapshot().getCustomMorphTargets().get(0)
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
        assertEquals(List.of("Alpha", "Gamma"), projectFlow.frame().snapshot().getCustomMorphTargets().get(0)
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

        assertSame(filtered.frame(), observed.get(observed.size() - 1));
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
}
