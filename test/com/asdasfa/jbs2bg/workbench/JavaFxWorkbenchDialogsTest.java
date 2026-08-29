package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.fx.FxTestToolkit;

import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

class JavaFxWorkbenchDialogsTest {

    /** Destructive typed dialogs have an explicit owner, dialog semantics, and Cancel as default and escape. */
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
}
