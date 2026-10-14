package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/**
 * The one gated dispatch pipeline every Workbench feature runs through. For one intent it:
 * <ol>
 *     <li>applies the job gate: a {@linkplain WorkbenchFeature#isMutation mutation} is refused while the latest
 *     job frame blocks Project mutations;</li>
 *     <li>calls {@code feature.dispatch};</li>
 *     <li>renders the update, re-rendering the Project chrome first when the update carries a Project sequence the
 *     chrome has not rendered;</li>
 *     <li>realizes each requested effect through the caller, running a confirmation as the dialog round-trip
 *     ({@code feedback.requestDialog} → {@link DialogPort} → {@code feedback.answerDialog}) followed by the
 *     feature's tokenized response, which is rendered the same way and may request a further effect;</li>
 *     <li>hands the settled {@link Outcome} to the caller to map to feedback.</li>
 * </ol>
 * Callers supply those three callbacks per feature through {@link Callbacks}. The pipeline is JavaFX-free; the
 * shell adapts the platform's modal dialogs and its Project chrome to the two ports. It runs on the presentation
 * lane only and is not thread-safe.
 */
public final class DispatchPipeline {

    private final WorkbenchFeedback feedback;
    private final DialogPort dialogs;
    private final Consumer<WorkbenchFeedback.Frame> feedbackView;
    private final ProjectChrome chrome;
    private boolean mutationsBlocked;

    /**
     * Creates the pipeline for one Workbench window.
     *
     * @param feedback     feedback model that tracks the pending dialog
     * @param dialogs      completes modal dialogs owned by the window
     * @param feedbackView renders each feedback frame the round-trip publishes
     * @param chrome       Project chrome re-rendered when an update reaches a new Project sequence
     * @throws NullPointerException when an argument is null
     */
    public DispatchPipeline(WorkbenchFeedback feedback, DialogPort dialogs,
                            Consumer<WorkbenchFeedback.Frame> feedbackView, ProjectChrome chrome) {
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.feedbackView = Objects.requireNonNull(feedbackView, "feedbackView");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    /**
     * Updates the job gate from the latest rendered job frame. The gate follows rendered frames rather than reading
     * the coordinator live, so it changes in step with the controls the same frame disables.
     *
     * @param frame latest job frame the shell rendered
     */
    public void acceptJobFrame(JobCoordinator.Frame frame) {
        mutationsBlocked = Objects.requireNonNull(frame, "frame").projectMutationsBlocked();
    }

    /**
     * @return whether the job gate currently refuses mutations; renders use it to disable mutation controls
     */
    public boolean mutationsBlocked() {
        return mutationsBlocked;
    }

    /**
     * Runs one intent through the gate, the feature, the render and every requested effect, then reports it.
     *
     * @param feature   feature that owns the intent
     * @param callbacks the caller's render, realize and report callbacks for this feature
     * @param intent    intent to dispatch
     * @param <I>       intent type
     * @param <U>       update type
     * @param <E>       effect type
     * @return the settled outcome, or empty when the job gate refused the intent (nothing was dispatched,
     * rendered or reported)
     * @throws NullPointerException when an argument is null
     */
    public <I, U extends WorkbenchFeature.Update<E>, E> Optional<Outcome<I, U>> dispatch(
            WorkbenchFeature<I, U, E> feature, Callbacks<I, U, E> callbacks, I intent) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(callbacks, "callbacks");
        Objects.requireNonNull(intent, "intent");
        if (mutationsBlocked && feature.isMutation(intent))
            return Optional.empty();
        U update = feature.dispatch(intent);
        render(callbacks, update);
        U settled = update;
        Optional<WorkbenchFeedback.DialogAction> answer = Optional.empty();
        // A confirmation's response may request a further effect (a dirty Reload answered Save captures a worker
        // Save), so keep realizing until an update carries no effect or the caller reports the effect handled.
        boolean handled = false;
        while (!handled && settled.effect().isPresent()) {
            switch (callbacks.realize(settled.effect().orElseThrow())) {
                case Realization.Done<U> done -> {
                    settled = done.replacement().orElse(settled);
                    handled = true;
                }
                case Realization.Confirm<U> confirm -> {
                    WorkbenchFeedback.DialogAction action = confirm(confirm.spec());
                    answer = Optional.of(action);
                    settled = Objects.requireNonNull(confirm.respond().apply(action), "response");
                    render(callbacks, settled);
                }
            }
        }
        Outcome<I, U> outcome = new Outcome<>(intent, update, answer, settled);
        callbacks.report(outcome);
        return Optional.of(outcome);
    }

    /**
     * Publishes a confirmation as the pending dialog, completes it, and answers it.
     *
     * @param spec confirmation to show
     * @return the action the user chose
     */
    public WorkbenchFeedback.DialogAction confirm(WorkbenchFeedback.DialogSpec spec) {
        return roundTrip(spec, () -> dialogs.confirm(spec), Function.identity());
    }

    /**
     * Publishes a failure dialog as the pending dialog, completes it, and answers it.
     *
     * @param spec failure dialog to show
     * @return the action the user chose
     */
    public WorkbenchFeedback.DialogAction fail(WorkbenchFeedback.DialogSpec spec) {
        return roundTrip(spec, () -> dialogs.fail(spec), Function.identity());
    }

    /**
     * Runs the dialog round-trip around a platform completion whose result is richer than a dialog action, such as a
     * Project confirmation that returns a Project response. The pending dialog is rendered before the platform is
     * asked, and the answer is rendered before the result is returned.
     *
     * @param spec     dialog to publish as pending
     * @param complete completes the modal on the platform
     * @param answer   maps the platform result to the dialog action recorded as the answer
     * @param <R>      platform result type
     * @return the platform result
     */
    public <R> R roundTrip(WorkbenchFeedback.DialogSpec spec, Supplier<R> complete,
                           Function<R, WorkbenchFeedback.DialogAction> answer) {
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(Objects.requireNonNull(spec, "spec"));
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        feedbackView.accept(pendingFrame);
        R result = complete.get();
        feedbackView.accept(feedback.answerDialog(new WorkbenchFeedback.DialogResult(
                pending.token(), answer.apply(result))).frame());
        return result;
    }

    /** Re-renders the Project chrome when the update reached a new Project sequence, then renders the update. */
    private <U extends WorkbenchFeature.Update<?>> void render(Callbacks<?, U, ?> callbacks, U update) {
        OptionalLong sequence = update.projectSequence();
        // The feature owns task wording and inline validation, so the chrome re-render must not replay the
        // lifecycle-only generic projection; the shell's ProjectChrome records the sequence before rendering.
        if (sequence.isPresent() && sequence.getAsLong() != chrome.renderedSequence())
            chrome.render(sequence.getAsLong());
        callbacks.render(update);
    }

    /**
     * Completes modal dialogs on the platform for the window that owns the pipeline.
     */
    public interface DialogPort {

        /**
         * Shows a confirmation and waits for the user's choice.
         *
         * @param spec confirmation to show
         * @return chosen action
         */
        WorkbenchFeedback.DialogAction confirm(WorkbenchFeedback.DialogSpec spec);

        /**
         * Shows a failure dialog and waits for the user's choice.
         *
         * @param spec failure dialog to show
         * @return chosen action
         */
        WorkbenchFeedback.DialogAction fail(WorkbenchFeedback.DialogSpec spec);
    }

    /**
     * The shell's Project chrome (title, Project status, diagnostics and the Project frame fan-out).
     */
    public interface ProjectChrome {

        /**
         * @return the Project sequence the chrome last rendered
         */
        long renderedSequence();

        /**
         * Records the sequence as rendered, then re-renders the chrome from the latest Project frame without
         * publishing lifecycle feedback for it.
         *
         * @param sequence Project sequence an update was reconciled against
         */
        void render(long sequence);
    }

    /**
     * The three callbacks a caller supplies for one feature.
     *
     * @param <I> intent type
     * @param <U> update type
     * @param <E> effect type
     */
    public interface Callbacks<I, U, E> {

        /**
         * Creates callbacks from three functions.
         *
         * @param render  renders one update
         * @param realize realizes one effect
         * @param report  maps a settled outcome to feedback
         * @param <I>     intent type
         * @param <U>     update type
         * @param <E>     effect type
         * @return callbacks delegating to the functions
         */
        static <I, U, E> Callbacks<I, U, E> of(Consumer<U> render, Function<E, Realization<U>> realize,
                                               Consumer<Outcome<I, U>> report) {
            Objects.requireNonNull(render, "render");
            Objects.requireNonNull(realize, "realize");
            Objects.requireNonNull(report, "report");
            return new Callbacks<>() {
                /** Delegates to the render function. */
                @Override
                public void render(U update) {
                    render.accept(update);
                }

                /** Delegates to the realize function. */
                @Override
                public Realization<U> realize(E effect) {
                    return Objects.requireNonNull(realize.apply(effect), "realization");
                }

                /** Delegates to the report function. */
                @Override
                public void report(Outcome<I, U> outcome) {
                    report.accept(outcome);
                }
            };
        }

        /**
         * Renders one committed update into the caller's controls. Called after any Project chrome re-render.
         *
         * @param update dispatched update or confirmation response
         */
        void render(U update);

        /**
         * Realizes one requested effect, either by handing a confirmation back to the pipeline or by handling it.
         *
         * @param effect effect carried by the latest update
         * @return how the effect was realized
         */
        Realization<U> realize(E effect);

        /**
         * Maps the settled outcome to feedback and any follow-up, such as clearing inputs or moving focus.
         *
         * @param outcome settled outcome of one admitted dispatch
         */
        void report(Outcome<I, U> outcome);
    }

    /**
     * How a caller realized one effect.
     *
     * @param <U> update type
     */
    public sealed interface Realization<U> permits Realization.Confirm, Realization.Done {

        /**
         * Asks the pipeline to run the dialog round-trip, then respond to the feature with the chosen action.
         *
         * @param spec    confirmation to show
         * @param respond tokenized feature response for the chosen action
         * @param <U>     update type
         * @return confirmation realization
         */
        static <U> Realization<U> confirm(WorkbenchFeedback.DialogSpec spec,
                                          Function<WorkbenchFeedback.DialogAction, U> respond) {
            return new Confirm<>(spec, respond);
        }

        /**
         * Reports the effect fully handled by the caller; the latest update stays settled.
         *
         * @param <U> update type
         * @return handled realization
         */
        static <U> Realization<U> done() {
            return new Done<>(Optional.empty());
        }

        /**
         * Reports the effect handled by the caller with a replacement settled update, such as a rejected update when
         * the effect could not be admitted.
         *
         * @param replacement update that settles the dispatch
         * @param <U>         update type
         * @return handled realization with a replacement
         */
        static <U> Realization<U> settledBy(U replacement) {
            return new Done<>(Optional.of(Objects.requireNonNull(replacement, "replacement")));
        }

        /**
         * A confirmation the pipeline completes through the dialog round-trip.
         *
         * @param spec    confirmation to show
         * @param respond tokenized feature response for the chosen action
         * @param <U>     update type
         */
        record Confirm<U>(WorkbenchFeedback.DialogSpec spec, Function<WorkbenchFeedback.DialogAction, U> respond)
                implements Realization<U> {
            /** Rejects incomplete confirmations. */
            public Confirm {
                Objects.requireNonNull(spec, "spec");
                Objects.requireNonNull(respond, "respond");
            }
        }

        /**
         * An effect the caller handled itself.
         *
         * @param replacement update that settles the dispatch instead of the latest one, if any
         * @param <U>         update type
         */
        record Done<U>(Optional<U> replacement) implements Realization<U> {
            /** Rejects a null replacement holder. */
            public Done {
                Objects.requireNonNull(replacement, "replacement");
            }
        }
    }

    /**
     * Settled result of one admitted dispatch.
     *
     * @param intent  dispatched intent
     * @param update  update the feature returned for the intent
     * @param answer  action chosen in the last confirmation round-trip, if a confirmation ran
     * @param settled the last update: the last confirmation response, a caller's replacement, or {@code update}
     * @param <I>     intent type
     * @param <U>     update type
     */
    public record Outcome<I, U>(I intent, U update, Optional<WorkbenchFeedback.DialogAction> answer, U settled) {
        /** Rejects incomplete outcomes. */
        public Outcome {
            Objects.requireNonNull(intent, "intent");
            Objects.requireNonNull(update, "update");
            Objects.requireNonNull(answer, "answer");
            Objects.requireNonNull(settled, "settled");
        }
    }
}
