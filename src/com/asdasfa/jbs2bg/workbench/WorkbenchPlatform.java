package com.asdasfa.jbs2bg.workbench;

import javafx.stage.Stage;

/**
 * Platform seam for modal chooser/confirmation effects and final window closure.
 */
interface WorkbenchPlatform {

    /**
     * Completes one tokenized chooser or confirmation effect.
     *
     * @param effect pending Workbench effect
     * @param owner  owning application window
     * @return the user's immutable response
     */
    WorkbenchProjectFlow.Response complete(WorkbenchProjectFlow.Effect effect, Stage owner);

    /**
     * Completes one typed job-failure dialog that may offer Retry.
     *
     * @param spec  failure-only dialog specification
     * @param owner owning application window
     * @return selected failure action
     */
    default WorkbenchFeedback.DialogAction completeFailure(WorkbenchFeedback.DialogSpec spec, Stage owner) {
        return WorkbenchFeedback.DialogAction.CLOSE;
    }

    /**
     * Realizes the final at-most-once close-window effect.
     *
     * @param owner application window to close
     */
    void closeWindow(Stage owner);
}
