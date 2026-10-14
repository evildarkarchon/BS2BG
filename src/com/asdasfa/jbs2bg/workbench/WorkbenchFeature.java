package com.asdasfa.jbs2bg.workbench;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * The contract a Workbench feature offers the shared {@link DispatchPipeline}: synchronous intent dispatch plus a
 * public declaration of which intents mutate the Project or Settings. Features stay JavaFX-free and job-unaware;
 * the pipeline, not the feature, refuses mutations while a job blocks them.
 *
 * @param <I> the feature's intent type
 * @param <U> the feature's update type
 * @param <E> the effect type an update may request
 */
public interface WorkbenchFeature<I, U extends WorkbenchFeature.Update<E>, E> {

    /**
     * Declares whether an intent changes the Project or Settings, as opposed to local browsing (selection, filter,
     * sort, type-ahead). The pipeline refuses mutations while an active job blocks Project mutations.
     *
     * @param intent intent about to be dispatched
     * @return true when the intent must wait for a blocking job to settle
     */
    boolean isMutation(I intent);

    /**
     * Applies one intent on the presentation lane and returns the committed update.
     *
     * @param intent immutable user task
     * @return committed update, possibly carrying one effect for the caller to realize
     */
    U dispatch(I intent);

    /**
     * The parts of a feature update the pipeline reads.
     *
     * @param <E> the effect type the update may request
     */
    interface Update<E> {

        /**
         * @return the effect the caller must realize before the dispatch is settled, if any
         */
        Optional<E> effect();

        /**
         * Reports the Project frame sequence this update was reconciled against. A sequence the Project chrome has not
         * rendered yet makes the pipeline re-render the chrome before the feature's own render.
         *
         * @return the reconciled Project sequence, or empty for features that do not project the Project
         */
        default OptionalLong projectSequence() {
            return OptionalLong.empty();
        }
    }
}
