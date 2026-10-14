package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/**
 * Drives the shared dispatch pipeline with a fake feature, a recording dialog port and recording render callbacks,
 * without loading FXML or starting JavaFX.
 */
class DispatchPipelineTest {

    private static final JobCoordinator.Frame IDLE = new JobCoordinator.Frame(1, Optional.empty(), false, false,
            List.of());
    // A requested shutdown pauses Project mutations without needing a live attempt.
    private static final JobCoordinator.Frame BLOCKING = new JobCoordinator.Frame(2, Optional.empty(), true, false,
            List.of());

    private final List<String> log = new ArrayList<>();
    private final WorkbenchFeedback feedback = new WorkbenchFeedback(
            Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC));
    private final RecordingDialogs dialogs = new RecordingDialogs();
    private final RecordingChrome chrome = new RecordingChrome();
    private final List<WorkbenchFeedback.Frame> renderedFeedback = new ArrayList<>();
    private final DispatchPipeline pipeline = new DispatchPipeline(feedback, dialogs, renderedFeedback::add, chrome);

    /** A mutation is refused while a job blocks Project mutations: nothing is dispatched, rendered or reported. */
    @Test
    void mutationIsRefusedWhileAJobBlocksProjectMutations() {
        FakeFeature feature = new FakeFeature(new FakeUpdate("edited", OptionalLong.empty(), Optional.empty()));
        pipeline.acceptJobFrame(BLOCKING);

        Optional<DispatchPipeline.Outcome<FakeIntent, FakeUpdate>> outcome =
                pipeline.dispatch(feature, callbacks(), new Edit());

        assertTrue(outcome.isEmpty());
        assertTrue(pipeline.mutationsBlocked());
        assertEquals(List.of(), feature.dispatched);
        assertEquals(List.of(), log);
    }

    /** Browsing intents keep working while a job blocks mutations, so the surface never freezes. */
    @Test
    void browsingIsAdmittedWhileAJobBlocksProjectMutations() {
        FakeFeature feature = new FakeFeature(new FakeUpdate("browsed", OptionalLong.empty(), Optional.empty()));
        pipeline.acceptJobFrame(BLOCKING);

        DispatchPipeline.Outcome<FakeIntent, FakeUpdate> outcome =
                pipeline.dispatch(feature, callbacks(), new Browse()).orElseThrow();

        assertEquals(List.of(new Browse()), feature.dispatched);
        assertEquals(List.of("render browsed", "report Browse[] browsed->browsed"), log);
        assertEquals("browsed", outcome.settled().label());
        assertTrue(outcome.answer().isEmpty());
    }

    /** The gate follows the latest job frame: it starts open and reopens once the blocking job settles. */
    @Test
    void gateFollowsTheLatestJobFrame() {
        FakeFeature feature = new FakeFeature(new FakeUpdate("edited", OptionalLong.empty(), Optional.empty()));
        assertFalse(pipeline.mutationsBlocked());

        pipeline.acceptJobFrame(BLOCKING);
        pipeline.acceptJobFrame(IDLE);

        assertFalse(pipeline.mutationsBlocked());
        assertTrue(pipeline.dispatch(feature, callbacks(), new Edit()).isPresent());
        assertEquals(List.of(new Edit()), feature.dispatched);
    }

    /**
     * A confirmation effect runs the full round-trip: the pending dialog is published before the platform is asked,
     * the answer clears it, and the feature's response is rendered and reported with the chosen action.
     */
    @Test
    void confirmationEffectRunsTheDialogRoundTripBeforeTheFeatureResponds() {
        FakeFeature feature = new FakeFeature(new FakeUpdate("requested", OptionalLong.empty(),
                Optional.of(new FakeEffect("confirm-remove"))));
        dialogs.confirmations.add(WorkbenchFeedback.DialogAction.REMOVE);
        List<WorkbenchFeedback.DialogAction> responded = new ArrayList<>();
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.destructiveAction(
                "Remove Shape?", "Remove the Shape Slider Preset?", WorkbenchFeedback.DialogAction.REMOVE);

        DispatchPipeline.Outcome<FakeIntent, FakeUpdate> outcome = pipeline.dispatch(feature,
                callbacks(effect -> DispatchPipeline.Realization.confirm(spec, action -> {
                    responded.add(action);
                    log.add("respond " + action);
                    return new FakeUpdate("removed", OptionalLong.empty(), Optional.empty());
                })), new Remove()).orElseThrow();

        assertEquals(List.of("render requested", "confirm Remove Shape? pending=true", "respond REMOVE",
                "render removed", "report Remove[] requested->removed"), log);
        assertEquals(List.of(WorkbenchFeedback.DialogAction.REMOVE), responded);
        assertEquals(Optional.of(WorkbenchFeedback.DialogAction.REMOVE), outcome.answer());
        assertEquals("requested", outcome.update().label());
        assertEquals("removed", outcome.settled().label());
        assertEquals(2, renderedFeedback.size());
        assertEquals(spec, renderedFeedback.getFirst().pendingDialog().orElseThrow().spec());
        assertTrue(renderedFeedback.getLast().pendingDialog().isEmpty());
        assertTrue(feedback.frame().pendingDialog().isEmpty());
    }

    /**
     * A confirmation response that requests another effect is realized too, as a dirty Reload answered Save chains
     * into a worker Save.
     */
    @Test
    void responseThatRequestsAnotherEffectIsRealizedInTurn() {
        FakeFeature feature = new FakeFeature(new FakeUpdate("asked", OptionalLong.empty(),
                Optional.of(new FakeEffect("confirm-reload"))));
        dialogs.confirmations.add(WorkbenchFeedback.DialogAction.SAVE);
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.unsavedClose(
                "Save Settings before reloading?", "Reloading would discard unsaved Settings changes.");

        DispatchPipeline.Outcome<FakeIntent, FakeUpdate> outcome = pipeline.dispatch(feature,
                callbacks(effect -> effect.name().equals("confirm-reload")
                        ? DispatchPipeline.Realization.confirm(spec, action -> new FakeUpdate("saving",
                        OptionalLong.empty(), Optional.of(new FakeEffect("worker-save"))))
                        : DispatchPipeline.Realization.done()), new Edit()).orElseThrow();

        assertEquals(List.of("render asked", "confirm Save Settings before reloading? pending=true",
                "render saving", "realize worker-save", "report Edit[] asked->saving"), log);
        assertEquals(Optional.of(WorkbenchFeedback.DialogAction.SAVE), outcome.answer());
    }

    /** A caller that handles an effect itself can settle the dispatch with a replacement, such as a rejection. */
    @Test
    void handledEffectCanSettleTheDispatchWithAReplacement() {
        FakeFeature feature = new FakeFeature(new FakeUpdate("captured", OptionalLong.empty(),
                Optional.of(new FakeEffect("worker-save"))));

        DispatchPipeline.Outcome<FakeIntent, FakeUpdate> outcome = pipeline.dispatch(feature,
                callbacks(effect -> DispatchPipeline.Realization.settledBy(
                        new FakeUpdate("refused", OptionalLong.empty(), Optional.empty()))), new Edit())
                .orElseThrow();

        assertEquals(List.of("render captured", "realize worker-save", "report Edit[] captured->refused"), log);
        assertEquals("refused", outcome.settled().label());
        assertTrue(outcome.answer().isEmpty());
        assertTrue(renderedFeedback.isEmpty());
    }

    /**
     * The Project chrome re-renders before the feature's own render only when an update reaches a Project sequence
     * the chrome has not rendered; features without a Project sequence never touch it.
     */
    @Test
    void projectChromeReRendersOnlyOnANewProjectSequence() {
        chrome.rendered = 4;
        FakeFeature feature = new FakeFeature(
                new FakeUpdate("same", OptionalLong.of(4), Optional.empty()),
                new FakeUpdate("changed", OptionalLong.of(5), Optional.empty()),
                new FakeUpdate("local", OptionalLong.empty(), Optional.empty()),
                new FakeUpdate("again", OptionalLong.of(5), Optional.empty()));

        for (FakeIntent intent : List.of(new Browse(), new Edit(), new Browse(), new Browse()))
            pipeline.dispatch(feature, callbacks(), intent);

        assertEquals(List.of("render same", "report Browse[] same->same",
                "chrome 5", "render changed", "report Edit[] changed->changed",
                "render local", "report Browse[] local->local",
                "render again", "report Browse[] again->again"), log);
    }

    /** A confirmation response that commits a Project edit re-renders the chrome before its own render. */
    @Test
    void confirmationResponseOnANewProjectSequenceReRendersTheChrome() {
        chrome.rendered = 7;
        FakeFeature feature = new FakeFeature(new FakeUpdate("requested", OptionalLong.of(7),
                Optional.of(new FakeEffect("confirm-clear"))));
        dialogs.confirmations.add(WorkbenchFeedback.DialogAction.CLEAR);
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.destructiveAction(
                "Clear visible?", "Clear every visible Slider Preset?", WorkbenchFeedback.DialogAction.CLEAR);

        pipeline.dispatch(feature, callbacks(effect -> DispatchPipeline.Realization.confirm(spec,
                action -> new FakeUpdate("cleared", OptionalLong.of(8), Optional.empty()))), new Remove());

        assertEquals(List.of("render requested", "confirm Clear visible? pending=true", "chrome 8",
                "render cleared", "report Remove[] requested->cleared"), log);
    }

    /** Failure dialogs and richer platform completions use the same publish → complete → answer round-trip. */
    @Test
    void failureAndPlatformRoundTripsPublishAndAnswerThePendingDialog() {
        dialogs.failures.add(WorkbenchFeedback.DialogAction.RETRY);
        WorkbenchFeedback.DialogSpec failure = WorkbenchFeedback.DialogSpec.failure(
                "Save Settings failed", "Settings could not be saved.", "disk full", true);

        assertEquals(WorkbenchFeedback.DialogAction.RETRY, pipeline.fail(failure));
        String completed = pipeline.roundTrip(WorkbenchFeedback.DialogSpec.unsavedClose(
                        "Save changes?", "Closing would discard unsaved Project changes."),
                () -> {
                    log.add("platform pending=" + feedback.frame().pendingDialog().isPresent());
                    return "discarded";
                }, result -> WorkbenchFeedback.DialogAction.DISCARD);

        assertEquals("discarded", completed);
        assertEquals(List.of("fail Save Settings failed pending=true", "platform pending=true"), log);
        assertEquals(4, renderedFeedback.size());
        assertTrue(renderedFeedback.get(1).pendingDialog().isEmpty());
        assertTrue(renderedFeedback.get(3).pendingDialog().isEmpty());
    }

    /** Builds callbacks that log renders and reports and realize every effect as already handled. */
    private DispatchPipeline.Callbacks<FakeIntent, FakeUpdate, FakeEffect> callbacks() {
        return callbacks(effect -> DispatchPipeline.Realization.done());
    }

    /** Builds callbacks that log renders, realizations handled outside the pipeline, and reports. */
    private DispatchPipeline.Callbacks<FakeIntent, FakeUpdate, FakeEffect> callbacks(
            java.util.function.Function<FakeEffect, DispatchPipeline.Realization<FakeUpdate>> realize) {
        return DispatchPipeline.Callbacks.of(
                update -> log.add("render " + update.label()),
                effect -> {
                    DispatchPipeline.Realization<FakeUpdate> realization = realize.apply(effect);
                    if (realization instanceof DispatchPipeline.Realization.Done<FakeUpdate>)
                        log.add("realize " + effect.name());
                    return realization;
                },
                outcome -> log.add("report " + outcome.intent() + " " + outcome.update().label() + "->"
                        + outcome.settled().label()));
    }

    /** Fake intent vocabulary: Browse is local, Edit and Remove mutate the Project. */
    sealed interface FakeIntent permits Browse, Edit, Remove {
    }

    /** Local browsing intent. */
    record Browse() implements FakeIntent {
    }

    /** Project mutation without confirmation. */
    record Edit() implements FakeIntent {
    }

    /** Project mutation that asks for confirmation. */
    record Remove() implements FakeIntent {
    }

    /** Tokenized fake effect. */
    record FakeEffect(String name) {
    }

    /** Fake feature update with an optional Project sequence and effect. */
    record FakeUpdate(String label, OptionalLong projectSequence, Optional<FakeEffect> effect)
            implements WorkbenchFeature.Update<FakeEffect> {
    }

    /** Fake JavaFX-free feature that returns scripted updates and records every dispatched intent. */
    static final class FakeFeature implements WorkbenchFeature<FakeIntent, FakeUpdate, FakeEffect> {
        private final List<FakeUpdate> scripted;
        final List<FakeIntent> dispatched = new ArrayList<>();

        /** Scripts the updates returned by successive dispatches. */
        FakeFeature(FakeUpdate... scripted) {
            this.scripted = new ArrayList<>(List.of(scripted));
        }

        /** Edit and Remove change the Project; Browse is local. */
        @Override
        public boolean isMutation(FakeIntent intent) {
            return !(intent instanceof Browse);
        }

        /** Records the intent and returns the next scripted update. */
        @Override
        public FakeUpdate dispatch(FakeIntent intent) {
            dispatched.add(intent);
            return scripted.removeFirst();
        }
    }

    /** Dialog port that records each completed spec and returns scripted actions. */
    final class RecordingDialogs implements DispatchPipeline.DialogPort {
        final List<WorkbenchFeedback.DialogAction> confirmations = new ArrayList<>();
        final List<WorkbenchFeedback.DialogAction> failures = new ArrayList<>();

        /** Logs the confirmation together with whether the pending dialog was already published. */
        @Override
        public WorkbenchFeedback.DialogAction confirm(WorkbenchFeedback.DialogSpec spec) {
            log.add("confirm " + spec.title() + " pending=" + feedback.frame().pendingDialog().isPresent());
            return confirmations.removeFirst();
        }

        /** Logs the failure dialog together with whether the pending dialog was already published. */
        @Override
        public WorkbenchFeedback.DialogAction fail(WorkbenchFeedback.DialogSpec spec) {
            log.add("fail " + spec.title() + " pending=" + feedback.frame().pendingDialog().isPresent());
            return failures.removeFirst();
        }
    }

    /** Project chrome that logs each re-render and remembers the rendered sequence. */
    final class RecordingChrome implements DispatchPipeline.ProjectChrome {
        long rendered;

        /** @return the last sequence rendered */
        @Override
        public long renderedSequence() {
            return rendered;
        }

        /** Records the sequence and logs the chrome render. */
        @Override
        public void render(long sequence) {
            rendered = sequence;
            log.add("chrome " + sequence);
        }
    }
}
