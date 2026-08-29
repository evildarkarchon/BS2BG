package com.asdasfa.jbs2bg.workbench;

import java.io.File;
import java.util.Objects;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** JavaFX implementation of Workbench file choosers, confirmations, and final window closure. */
final class JavaFxWorkbenchPlatform implements WorkbenchPlatform {

    /** Completes the requested modal effect using public JavaFX dialogs owned by the Workbench window. */
    @Override
    public WorkbenchProjectFlow.Response complete(WorkbenchProjectFlow.Effect effect, Stage owner) {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(owner, "owner");
        return switch (effect.kind()) {
        case CHOOSE_OPEN_PATH -> chooseProject(owner, false);
        case CHOOSE_SAVE_PATH -> chooseProject(owner, true);
        case CONFIRM_NEW, CONFIRM_OPEN -> response(JavaFxWorkbenchDialogs.show(
                dialogSpec(effect.kind()), owner));
        case CONFIRM_CLOSE -> response(JavaFxWorkbenchDialogs.show(dialogSpec(effect.kind()), owner));
        case CLOSE_WINDOW -> throw new IllegalArgumentException("CLOSE_WINDOW is not a modal platform effect");
        };
    }

    /** Closes the JavaFX Stage after the Project flow has already entered its terminal state. */
    @Override
    public void closeWindow(Stage owner) {
        Objects.requireNonNull(owner, "owner").close();
    }

    /** Shows the native Open or Save chooser and translates cancellation without inventing a path. */
    private static WorkbenchProjectFlow.Response chooseProject(Stage owner, boolean save) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(save ? "Save BS2BG Project" : "Open BS2BG Project");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("BS2BG Project (*.jbs2bg)", "*.jbs2bg"));
        File selected = save ? chooser.showSaveDialog(owner) : chooser.showOpenDialog(owner);
        return selected == null ? WorkbenchProjectFlow.Response.cancelled()
                : WorkbenchProjectFlow.Response.selected(selected.toPath());
    }

    /** Returns the typed dialog contract for one Project confirmation effect. */
    static WorkbenchFeedback.DialogSpec dialogSpec(WorkbenchProjectFlow.EffectKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case CONFIRM_NEW -> WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                    "Create a new Project?", "The current Project has unsaved changes.");
            case CONFIRM_OPEN -> WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                    "Open another Project?", "The current Project has unsaved changes.");
            case CONFIRM_CLOSE -> WorkbenchFeedback.DialogSpec.unsavedClose(
                    "Save changes before closing?", "Closing now would discard unsaved Project changes.");
            case CHOOSE_OPEN_PATH, CHOOSE_SAVE_PATH, CLOSE_WINDOW ->
                    throw new IllegalArgumentException(kind + " is not a Workbench dialog effect");
        };
    }

    /** Converts one typed dialog action to the Project flow's ordinary response intent. */
    private static WorkbenchProjectFlow.Response response(WorkbenchFeedback.DialogAction action) {
        return switch (Objects.requireNonNull(action, "action")) {
            case SAVE -> WorkbenchProjectFlow.Response.save();
            case DISCARD -> WorkbenchProjectFlow.Response.discard();
            case CANCEL, CLOSE -> WorkbenchProjectFlow.Response.cancelled();
            case COPY_DETAILS, RETRY -> throw new IllegalArgumentException(
                    action + " is not a Project confirmation response");
        };
    }
}
