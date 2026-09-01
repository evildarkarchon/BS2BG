package com.asdasfa.jbs2bg.workbench;

import java.io.File;
import java.util.Objects;
import java.util.Optional;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * JavaFX implementation of Workbench file choosers, confirmations, and final window closure.
 */
final class JavaFxWorkbenchPlatform implements WorkbenchPlatform {

    /**
     * Shows the native Open or Save chooser and translates cancellation without inventing a path.
     */
    private static WorkbenchProjectFlow.Response chooseProject(Stage owner, boolean save) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(save ? "Save BS2BG Project" : "Open BS2BG Project");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("BS2BG Project (*.jbs2bg)", "*.jbs2bg"));
        File selected = save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
        return selected == null ? WorkbenchProjectFlow.Response.cancelled()
                : WorkbenchProjectFlow.Response.selected(selected.toPath());
    }

    /**
     * Returns the typed dialog contract for one Project confirmation effect.
     *
     * @param kind Project platform-effect kind
     * @return typed destructive confirmation request, or empty for chooser/final-window effects
     * @throws NullPointerException when kind is null
     */
    static Optional<WorkbenchFeedback.DialogSpec> dialogSpec(WorkbenchProjectFlow.EffectKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case CONFIRM_NEW -> Optional.of(WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                    "Create a new Project?", "The current Project has unsaved changes."));
            case CONFIRM_OPEN -> Optional.of(WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                    "Open another Project?", "The current Project has unsaved changes."));
            case CONFIRM_CLOSE -> Optional.of(WorkbenchFeedback.DialogSpec.unsavedClose(
                    "Save changes before closing?", "Closing now would discard unsaved Project changes."));
            case CHOOSE_OPEN_PATH, CHOOSE_SAVE_PATH, CLOSE_WINDOW -> Optional.empty();
        };
    }

    /**
     * Converts one typed dialog action to the Project flow's ordinary response intent.
     *
     * @param action action returned by the typed dialog
     * @return ordinary Project-flow response
     * @throws NullPointerException     when action is null
     * @throws IllegalArgumentException when action belongs only to failure dialogs
     */
    private static WorkbenchProjectFlow.Response response(WorkbenchFeedback.DialogAction action) {
        return switch (Objects.requireNonNull(action, "action")) {
            case SAVE -> WorkbenchProjectFlow.Response.save();
            case DISCARD -> WorkbenchProjectFlow.Response.discard();
            case CANCEL, CLOSE -> WorkbenchProjectFlow.Response.cancelled();
            case COPY_DETAILS, RETRY, REMOVE, CLEAR -> throw new IllegalArgumentException(
                    action + " is not a Project confirmation response");
        };
    }

    /**
     * Completes the requested modal effect using public JavaFX dialogs owned by the Workbench window.
     */
    @Override
    public WorkbenchProjectFlow.Response complete(WorkbenchProjectFlow.Effect effect, Stage owner) {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(owner, "owner");
        return switch (effect.kind()) {
            case CHOOSE_OPEN_PATH -> chooseProject(owner, false);
            case CHOOSE_SAVE_PATH -> chooseProject(owner, true);
            case CONFIRM_NEW, CONFIRM_OPEN -> response(JavaFxWorkbenchDialogs.show(
                    dialogSpec(effect.kind()).orElseThrow(), owner));
            case CONFIRM_CLOSE -> response(JavaFxWorkbenchDialogs.show(
                    dialogSpec(effect.kind()).orElseThrow(), owner));
            case CLOSE_WINDOW -> throw new IllegalArgumentException("CLOSE_WINDOW is not a modal platform effect");
        };
    }

    /**
     * Shows a typed failure dialog only after durable feedback state has published it.
     */
    @Override
    public WorkbenchFeedback.DialogAction completeFailure(WorkbenchFeedback.DialogSpec spec, Stage owner) {
        if (Objects.requireNonNull(spec, "spec").kind() != WorkbenchFeedback.DialogKind.FAILURE)
            throw new IllegalArgumentException("Only failure dialogs use the failure platform seam");
        return JavaFxWorkbenchDialogs.show(spec, Objects.requireNonNull(owner, "owner"));
    }

    /**
     * Shows a destructive feature confirmation through the same owned application-modal renderer.
     */
    @Override
    public WorkbenchFeedback.DialogAction completeConfirmation(WorkbenchFeedback.DialogSpec spec, Stage owner) {
        if (Objects.requireNonNull(spec, "spec").kind()
                != WorkbenchFeedback.DialogKind.DESTRUCTIVE_CONFIRMATION)
            throw new IllegalArgumentException("Only destructive confirmations use this platform seam");
        return JavaFxWorkbenchDialogs.show(spec, Objects.requireNonNull(owner, "owner"));
    }

    /**
     * Closes the JavaFX Stage after the Project flow has already entered its terminal state.
     */
    @Override
    public void closeWindow(Stage owner) {
        Objects.requireNonNull(owner, "owner").close();
    }
}
