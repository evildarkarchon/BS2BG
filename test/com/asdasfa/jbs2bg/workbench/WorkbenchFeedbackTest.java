package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class WorkbenchFeedbackTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-08-29T12:00:00Z");

    /**
     * Every outcome projects the same severity, text cue, and semantic icon across all feedback surfaces.
     */
    @Test
    void outcomeSeverityProjectsConsistentlyAcrossInfoBarActivityAndStatus() {
        WorkbenchFeedback feedback = new WorkbenchFeedback(Clock.fixed(FIXED_TIME, ZoneOffset.UTC));

        WorkbenchFeedback.Frame frame = feedback.publish(new WorkbenchFeedback.Notification(
                "Open Project", WorkbenchFeedback.Severity.WARNING,
                "Project opened with recoverable diagnostics.",
                WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES));

        WorkbenchFeedback.InfoBar infoBar = frame.infoBar().orElseThrow();
        WorkbenchFeedback.ActivityRecord activity = frame.activities().getLast();
        assertEquals(WorkbenchFeedback.Severity.WARNING, infoBar.severity());
        assertEquals("Warning", infoBar.cue());
        assertEquals(SemanticIcons.IconKey.WARNING, infoBar.icon());
        assertEquals(infoBar.severity(), activity.severity());
        assertEquals(infoBar.cue(), activity.cue());
        assertEquals(infoBar.icon(), activity.icon());
        assertEquals(infoBar.severity(), frame.status().severity());
        assertEquals("Completed with issues", frame.status().dispositionText());
        assertEquals(FIXED_TIME, activity.occurredAt());
    }

    /**
     * Validation, success, warning, and failure each retain an independent non-color cue and vector request.
     */
    @Test
    void everyAcceptedOutcomeSeverityHasAStableNonColorProjection() {
        java.util.Map<WorkbenchFeedback.Severity, String> cues = java.util.Map.of(
                WorkbenchFeedback.Severity.VALIDATION, "Validation",
                WorkbenchFeedback.Severity.SUCCESS, "Success",
                WorkbenchFeedback.Severity.WARNING, "Warning",
                WorkbenchFeedback.Severity.FAILURE, "Failure");
        java.util.Map<WorkbenchFeedback.Severity, SemanticIcons.IconKey> icons = java.util.Map.of(
                WorkbenchFeedback.Severity.VALIDATION, SemanticIcons.IconKey.VALIDATION,
                WorkbenchFeedback.Severity.SUCCESS, SemanticIcons.IconKey.SUCCESS,
                WorkbenchFeedback.Severity.WARNING, SemanticIcons.IconKey.WARNING,
                WorkbenchFeedback.Severity.FAILURE, SemanticIcons.IconKey.FAILURE);
        WorkbenchFeedback feedback = new WorkbenchFeedback(Clock.fixed(FIXED_TIME, ZoneOffset.UTC));

        for (WorkbenchFeedback.Severity severity : cues.keySet()) {
            WorkbenchFeedback.Frame frame = feedback.publish(new WorkbenchFeedback.Notification(
                    "Operation", severity, "Outcome message.", WorkbenchFeedback.Disposition.COMPLETED));
            assertEquals(cues.get(severity), frame.infoBar().orElseThrow().cue());
            assertEquals(icons.get(severity), frame.infoBar().orElseThrow().icon());
            assertEquals(severity, frame.status().severity());
        }
    }

    /**
     * Dismissing transient inline feedback never deletes the durable Activity record or terminal status.
     */
    @Test
    void dismissingInfoBarRetainsDurableActivityAndStatus() {
        WorkbenchFeedback feedback = new WorkbenchFeedback(Clock.fixed(FIXED_TIME, ZoneOffset.UTC));
        feedback.publish(new WorkbenchFeedback.Notification(
                "Save Project", WorkbenchFeedback.Severity.SUCCESS,
                "Project saved.", WorkbenchFeedback.Disposition.COMPLETED));

        WorkbenchFeedback.Frame dismissed = feedback.dismissInfoBar();

        assertEquals(true, dismissed.infoBar().isEmpty());
        assertEquals(1, dismissed.activities().size());
        assertEquals("Save Project", dismissed.activities().getFirst().operation());
        assertEquals("Project saved.", dismissed.status().message());
    }

    /**
     * Deep features can retain pane-local InfoBar ownership while the kernel still records Activity and status.
     */
    @Test
    void activityOnlyPublicationDoesNotCreateAWorkbenchInfoBar() {
        WorkbenchFeedback feedback = new WorkbenchFeedback(Clock.fixed(FIXED_TIME, ZoneOffset.UTC));

        WorkbenchFeedback.Frame frame = feedback.publishActivity(new WorkbenchFeedback.Notification(
                "Rename Slider Preset", WorkbenchFeedback.Severity.SUCCESS,
                "Rename Slider Preset completed.", WorkbenchFeedback.Disposition.COMPLETED));

        assertEquals(true, frame.infoBar().isEmpty());
        assertEquals("Rename Slider Preset", frame.activities().getLast().operation());
        assertEquals("Rename Slider Preset completed.", frame.status().message());
    }

    /**
     * High-frequency feature gestures may update truthful terminal status without creating an InfoBar or durable
     * Activity entry for every individual row edit.
     */
    @Test
    void statusOnlyPublicationDoesNotCreateInfoBarOrActivity() {
        WorkbenchFeedback feedback = new WorkbenchFeedback(Clock.fixed(FIXED_TIME, ZoneOffset.UTC));

        WorkbenchFeedback.Frame frame = feedback.publishStatus(new WorkbenchFeedback.Notification(
                "Edit Slider choice", WorkbenchFeedback.Severity.SUCCESS,
                "Waist range changed.", WorkbenchFeedback.Disposition.COMPLETED));

        assertEquals(true, frame.infoBar().isEmpty());
        assertEquals(0, frame.activities().size());
        assertEquals("Waist range changed.", frame.status().message());
    }

    /**
     * Destructive dialogs publish a safe Cancel default and accept only the matching tokenized answer.
     */
    @Test
    void typedDialogStateRejectsStaleAnswersAndDefaultsDestructiveActionsToCancel() {
        WorkbenchFeedback feedback = new WorkbenchFeedback(Clock.fixed(FIXED_TIME, ZoneOffset.UTC));

        WorkbenchFeedback.Frame requested = feedback.requestDialog(
                WorkbenchFeedback.DialogSpec.destructiveConfirmation(
                        "Discard changes?", "The current Project has unsaved changes."));
        WorkbenchFeedback.PendingDialog pending = requested.pendingDialog().orElseThrow();

        assertEquals(WorkbenchFeedback.DialogAction.CANCEL, pending.spec().safeDefault());
        assertEquals(WorkbenchFeedback.DialogAction.CANCEL, pending.spec().cancelAction());
        assertEquals(true, pending.spec().actions().contains(WorkbenchFeedback.DialogAction.DISCARD));

        WorkbenchFeedback.DialogUpdate stale = feedback.answerDialog(
                new WorkbenchFeedback.DialogResult(pending.token() + 1, WorkbenchFeedback.DialogAction.DISCARD));
        assertEquals(false, stale.accepted());
        assertEquals(pending, stale.frame().pendingDialog().orElseThrow());

        WorkbenchFeedback.DialogUpdate answered = feedback.answerDialog(
                new WorkbenchFeedback.DialogResult(pending.token(), WorkbenchFeedback.DialogAction.DISCARD));
        assertEquals(true, answered.accepted());
        assertEquals(true, answered.frame().pendingDialog().isEmpty());
        assertEquals(WorkbenchFeedback.DialogAction.DISCARD, answered.result().orElseThrow().action());
    }

    /**
     * Failure dialogs expose copyable details and contextual Retry while Close remains the safe default.
     */
    @Test
    void failureDialogUsesTheAcceptedTypedActions() {
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.failure(
                "Generate failed", "BodyGen output could not be written.",
                "Access denied: meshes\\actors", true);

        assertEquals(WorkbenchFeedback.Severity.FAILURE, spec.severity());
        assertEquals("Access denied: meshes\\actors", spec.details().orElseThrow());
        assertEquals(java.util.List.of(
                WorkbenchFeedback.DialogAction.COPY_DETAILS,
                WorkbenchFeedback.DialogAction.RETRY,
                WorkbenchFeedback.DialogAction.CLOSE), spec.actions());
        assertEquals(WorkbenchFeedback.DialogAction.CLOSE, spec.safeDefault());
        assertEquals(WorkbenchFeedback.DialogAction.CLOSE, spec.cancelAction());
    }

    /**
     * Dirty shutdown remains typed and exposes Save and Discard while Cancel is both default and escape.
     */
    @Test
    void dirtyCloseDialogKeepsCancelAsTheSafeDefault() {
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.unsavedClose(
                "Save changes before closing?", "Closing now would discard unsaved Project changes.");

        assertEquals(java.util.List.of(
                WorkbenchFeedback.DialogAction.SAVE,
                WorkbenchFeedback.DialogAction.DISCARD,
                WorkbenchFeedback.DialogAction.CANCEL), spec.actions());
        assertEquals(WorkbenchFeedback.DialogAction.CANCEL, spec.safeDefault());
        assertEquals(WorkbenchFeedback.DialogAction.CANCEL, spec.cancelAction());
    }
}
