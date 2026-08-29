package com.asdasfa.jbs2bg.workbench;

import java.io.File;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.fx.DialogGraphics;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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
        case CONFIRM_NEW -> confirmReplacement(owner, "Create a new Project?",
                "The current Project has unsaved changes.");
        case CONFIRM_OPEN -> confirmReplacement(owner, "Open another Project?",
                "The current Project has unsaved changes.");
        case CONFIRM_CLOSE -> confirmClose(owner);
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

    /** Confirms a destructive New/Open replacement with Cancel as the safe default. */
    private static WorkbenchProjectFlow.Response confirmReplacement(Stage owner, String header, String content) {
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = confirmation(owner, header, content, discard, cancel);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == discard ? WorkbenchProjectFlow.Response.discard()
                : WorkbenchProjectFlow.Response.cancelled();
    }

    /** Offers Save, Discard, and Cancel for dirty shutdown; Cancel remains the safe default. */
    private static WorkbenchProjectFlow.Response confirmClose(Stage owner) {
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = confirmation(owner, "Save changes before closing?",
                "Closing now would discard unsaved Project changes.", save, discard, cancel);
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isPresent() && answer.get() == save)
            return WorkbenchProjectFlow.Response.save();
        if (answer.isPresent() && answer.get() == discard)
            return WorkbenchProjectFlow.Response.discard();
        return WorkbenchProjectFlow.Response.cancelled();
    }

    /** Builds one owned confirmation with application-owned public-JavaFX graphics. */
    private static Alert confirmation(Stage owner, String header, String content, ButtonType... buttons) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle("BS2BG Preview");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.setGraphic(DialogGraphics.node(DialogGraphics.Semantic.CONFIRMATION, 48));
        alert.getButtonTypes().setAll(buttons);
        return alert;
    }
}
