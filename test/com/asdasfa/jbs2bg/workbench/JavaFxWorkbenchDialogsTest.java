package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.fx.FxTestToolkit;

import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

class JavaFxWorkbenchDialogsTest {

    /**
     * Destructive typed dialogs have an explicit owner, dialog semantics, and Cancel as default and escape.
     */
    @Test
    void destructiveDialogRendersTheSafeTypedContract() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new StackPane()));
            WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                    "Discard changes?", "The current Project has unsaved changes.");

            Dialog<WorkbenchFeedback.DialogAction> dialog = JavaFxWorkbenchDialogs.create(spec, owner);
            Button discard = (Button) dialog.getDialogPane().lookupButton(
                    JavaFxWorkbenchDialogs.buttonType(WorkbenchFeedback.DialogAction.DISCARD));
            Button cancel = (Button) dialog.getDialogPane().lookupButton(
                    JavaFxWorkbenchDialogs.buttonType(WorkbenchFeedback.DialogAction.CANCEL));

            assertSame(owner, dialog.getOwner());
            assertEquals(AccessibleRole.DIALOG, dialog.getDialogPane().getAccessibleRole());
            assertEquals("Warning: Discard changes? The current Project has unsaved changes.",
                    dialog.getDialogPane().getAccessibleText());
            assertFalse(discard.isDefaultButton());
            assertTrue(cancel.isDefaultButton());
            assertTrue(cancel.isCancelButton());
            owner.close();
        });
    }

    /**
     * The safe default owns the dialog scene's focus as soon as the dialog is shown, and still owns it after the
     * dialog's first queued FX work has run.
     */
    @Test
    void shownDialogFocusesItsSafeDefaultSynchronously() throws Exception {
        AtomicReference<Stage> owner = new AtomicReference<>();
        AtomicReference<Dialog<WorkbenchFeedback.DialogAction>> shown = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                owner.set(new Stage());
                owner.get().setScene(new Scene(new StackPane(), 300.0, 200.0));
                owner.get().show();
                shown.set(JavaFxWorkbenchDialogs.create(WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                        "Discard changes?", "The current Project has unsaved changes."), owner.get()));

                shown.get().show();

                assertSame(cancelButton(shown.get()), shown.get().getDialogPane().getScene().getFocusOwner());
            });
            FxTestToolkit.runOnFxThread(() -> assertSame(cancelButton(shown.get()),
                    shown.get().getDialogPane().getScene().getFocusOwner()));
        } finally {
            FxTestToolkit.runOnFxThread(() -> {
                if (shown.get() != null)
                    shown.get().close();
                if (owner.get() != null)
                    owner.get().close();
            });
        }
    }

    /** Looks up the typed Cancel button of a created dialog. */
    private static Button cancelButton(Dialog<WorkbenchFeedback.DialogAction> dialog) {
        return (Button) dialog.getDialogPane().lookupButton(
                JavaFxWorkbenchDialogs.buttonType(WorkbenchFeedback.DialogAction.CANCEL));
    }
}
