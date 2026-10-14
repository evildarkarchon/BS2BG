package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure mapping between Project platform effects and the typed confirmation dialogs that complete them. The shell's
 * Project effect loop uses it to publish the pending dialog before the platform answers, and the JavaFX platform
 * uses the same specs to show the native dialog.
 */
final class ProjectEffectDialogs {

    private ProjectEffectDialogs() {
    }

    /**
     * Returns the typed dialog contract for one Project confirmation effect.
     *
     * @param kind Project platform-effect kind
     * @return typed destructive confirmation request, or empty for chooser/final-window effects
     * @throws NullPointerException when kind is null
     */
    static Optional<WorkbenchFeedback.DialogSpec> spec(WorkbenchProjectFlow.EffectKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case CONFIRM_NEW -> Optional.of(WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                    "Create a new Project?", "The current Project has unsaved changes."));
            case CONFIRM_OPEN -> Optional.of(WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                    "Open another Project?", "The current Project has unsaved changes."));
            case CONFIRM_CLOSE -> Optional.of(WorkbenchFeedback.DialogSpec.unsavedClose(
                    "Save changes before closing?", "Closing now would discard unsaved Project changes."));
            case CHOOSE_BODYSLIDE_SOURCES, CHOOSE_OPEN_PATH, CHOOSE_SAVE_PATH, CLOSE_WINDOW -> Optional.empty();
        };
    }

    /**
     * Reads a Project confirmation response back as the dialog action the user chose, so the pending dialog can be
     * answered in the feedback model.
     *
     * @param response response the platform returned for a confirmation effect
     * @return the chosen Save, Discard, or Cancel action
     * @throws IllegalArgumentException when the response carries a chooser path instead of a confirmation answer
     */
    static WorkbenchFeedback.DialogAction answer(WorkbenchProjectFlow.Response response) {
        return switch (Objects.requireNonNull(response, "response").kind()) {
            case SAVE -> WorkbenchFeedback.DialogAction.SAVE;
            case DISCARD -> WorkbenchFeedback.DialogAction.DISCARD;
            case CANCELLED -> WorkbenchFeedback.DialogAction.CANCEL;
            case PATH_SELECTED, PATHS_SELECTED -> throw new IllegalArgumentException(
                    "A Project confirmation cannot return a selected path");
        };
    }
}
