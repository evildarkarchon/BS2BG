package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.util.Optional;

import javafx.stage.Stage;

/**
 * Platform seam for modal chooser/confirmation effects and final window closure.
 */
interface WorkbenchPlatform {

    /**
     * Copies accepted Output text through the native clipboard boundary.
     *
     * @param text exact accepted artifact text
     * @return whether the platform accepted the clipboard content
     */
    default boolean copyOutputText(String text) {
        return false;
    }

    /**
     * Chooses one existing directory for complete-batch Output export.
     *
     * @param owner owning application window
     * @return selected directory, or empty when cancelled
     */
    default Optional<Path> chooseOutputDirectory(Stage owner) {
        return Optional.empty();
    }

    /**
     * Chooses one file for selected-BoS export.
     *
     * @param suggestedFileName canonical accepted artifact filename
     * @param owner             owning application window
     * @return selected file, or empty when cancelled
     */
    default Optional<Path> chooseOutputFile(String suggestedFileName, Stage owner) {
        return Optional.empty();
    }

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
     * Completes one typed destructive feature confirmation after durable pending-dialog state is published.
     *
     * @param spec  destructive confirmation specification
     * @param owner owning application window
     * @return selected REMOVE, CLEAR, or CANCEL action
     */
    default WorkbenchFeedback.DialogAction completeConfirmation(WorkbenchFeedback.DialogSpec spec, Stage owner) {
        return WorkbenchFeedback.DialogAction.CANCEL;
    }

    /**
     * Realizes the final at-most-once close-window effect.
     *
     * @param owner application window to close
     */
    void closeWindow(Stage owner);
}
