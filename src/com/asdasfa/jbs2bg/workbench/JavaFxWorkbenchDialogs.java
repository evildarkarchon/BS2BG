package com.asdasfa.jbs2bg.workbench;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Standard-JavaFX renderer for typed Workbench confirmation and failure dialogs. */
final class JavaFxWorkbenchDialogs {
    private static final Map<WorkbenchFeedback.DialogAction, ButtonType> BUTTON_TYPES = buttonTypes();

    private JavaFxWorkbenchDialogs() {
    }

    /**
     * Shows one owned application-modal dialog and returns its typed action.
     *
     * @param spec complete typed request already published in durable presentation state
     * @param owner semantic launcher window that regains focus after the modal closes
     * @return selected action, or the request's cancel action for window-manager dismissal
     */
    static WorkbenchFeedback.DialogAction show(WorkbenchFeedback.DialogSpec spec, Stage owner) {
        Dialog<WorkbenchFeedback.DialogAction> dialog = create(spec, owner);
        return dialog.showAndWait().orElse(spec.cancelAction());
    }

    /** Builds one owned dialog graph so toolkit tests and the real modal path share the same interface. */
    static Dialog<WorkbenchFeedback.DialogAction> create(WorkbenchFeedback.DialogSpec spec, Stage owner) {
        WorkbenchFeedback.DialogSpec request = Objects.requireNonNull(spec, "spec");
        Dialog<WorkbenchFeedback.DialogAction> dialog = new Dialog<>();
        dialog.initOwner(Objects.requireNonNull(owner, "owner"));
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("BS2BG Preview");

        DialogPane pane = dialog.getDialogPane();
        pane.setAccessibleRole(AccessibleRole.DIALOG);
        pane.setAccessibleText(request.severity().cue() + ": " + request.title() + " " + request.message());
        pane.setAccessibleHelp("Enter activates the safe default; Escape activates "
                + buttonLabel(request.cancelAction()) + ".");
        pane.setHeaderText(request.title());
        pane.setContentText(request.message());
        pane.setGraphic(SemanticIcons.create(request.severity().icon(), true));
        pane.getButtonTypes().setAll(request.actions().stream().map(JavaFxWorkbenchDialogs::buttonType).toList());

        for (WorkbenchFeedback.DialogAction action : request.actions()) {
            Button button = (Button) pane.lookupButton(buttonType(action));
            button.setMnemonicParsing(true);
            button.setDefaultButton(action == request.safeDefault());
            button.setCancelButton(action == request.cancelAction());
            button.setAccessibleText(buttonLabel(action));
            if (action == WorkbenchFeedback.DialogAction.COPY_DETAILS) {
                button.addEventFilter(ActionEvent.ACTION, event -> {
                    copyDetails(request.details().orElse(""));
                    // Copying diagnostics is an action inside the dialog, not a terminal answer.
                    event.consume();
                });
            }
        }
        dialog.setResultConverter(JavaFxWorkbenchDialogs::actionFor);
        dialog.setOnShown(event -> Platform.runLater(() ->
                pane.lookupButton(buttonType(request.safeDefault())).requestFocus()));
        return dialog;
    }

    /** Returns the stable ButtonType shared by creation, lookup, and result conversion. */
    static ButtonType buttonType(WorkbenchFeedback.DialogAction action) {
        return BUTTON_TYPES.get(Objects.requireNonNull(action, "action"));
    }

    /** Creates the complete typed-to-JavaFX button mapping exactly once. */
    private static Map<WorkbenchFeedback.DialogAction, ButtonType> buttonTypes() {
        Map<WorkbenchFeedback.DialogAction, ButtonType> values =
                new EnumMap<>(WorkbenchFeedback.DialogAction.class);
        values.put(WorkbenchFeedback.DialogAction.SAVE,
                new ButtonType("_Save", ButtonBar.ButtonData.YES));
        values.put(WorkbenchFeedback.DialogAction.DISCARD,
                new ButtonType("_Discard", ButtonBar.ButtonData.NO));
        values.put(WorkbenchFeedback.DialogAction.CANCEL,
                new ButtonType("_Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));
        values.put(WorkbenchFeedback.DialogAction.COPY_DETAILS,
                new ButtonType("_Copy Details", ButtonBar.ButtonData.OTHER));
        values.put(WorkbenchFeedback.DialogAction.RETRY,
                new ButtonType("_Retry", ButtonBar.ButtonData.OTHER));
        values.put(WorkbenchFeedback.DialogAction.CLOSE,
                new ButtonType("_Close", ButtonBar.ButtonData.CANCEL_CLOSE));
        return Map.copyOf(values);
    }

    /** Converts a returned JavaFX ButtonType to the Workbench dialog action. */
    private static WorkbenchFeedback.DialogAction actionFor(ButtonType value) {
        for (Map.Entry<WorkbenchFeedback.DialogAction, ButtonType> entry : BUTTON_TYPES.entrySet()) {
            if (entry.getValue() == value)
                return entry.getKey();
        }
        return null;
    }

    /** Copies complete failure details through the public JavaFX clipboard interface. */
    private static void copyDetails(String details) {
        ClipboardContent content = new ClipboardContent();
        content.putString(details);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** Removes mnemonic markers from the stable accessible button name. */
    private static String buttonLabel(WorkbenchFeedback.DialogAction action) {
        return buttonType(action).getText().replace("_", "");
    }
}
