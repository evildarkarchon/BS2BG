package com.asdasfa.jbs2bg.workbench;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owns immutable nonmodal feedback, durable Activity, status, and typed-dialog presentation state. */
public final class WorkbenchFeedback {

    /** Semantic outcome severity; every value supplies a non-color cue and icon. */
    public enum Severity {
        INFORMATION("Information", SemanticIcons.IconKey.INFORMATION),
        VALIDATION("Validation", SemanticIcons.IconKey.VALIDATION),
        SUCCESS("Success", SemanticIcons.IconKey.SUCCESS),
        WARNING("Warning", SemanticIcons.IconKey.WARNING),
        FAILURE("Failure", SemanticIcons.IconKey.FAILURE);

        private final String cue;
        private final SemanticIcons.IconKey icon;

        Severity(String cue, SemanticIcons.IconKey icon) {
            this.cue = cue;
            this.icon = icon;
        }

        /** @return stable text that communicates severity without relying on color */
        public String cue() {
            return cue;
        }

        /** @return semantic bundled-vector request paired with this severity */
        public SemanticIcons.IconKey icon() {
            return icon;
        }
    }

    /** Terminal operation distinction retained independently from severity. */
    public enum Disposition {
        COMPLETED("Completed"),
        COMPLETED_WITH_ISSUES("Completed with issues"),
        CANCELLED("Cancelled"),
        FAILED("Failed");

        private final String displayText;

        Disposition(String displayText) {
            this.displayText = displayText;
        }

        /** @return stable non-color terminal outcome text */
        public String displayText() {
            return displayText;
        }
    }

    /** Typed dialog family used to restrict modal presentation to accepted cases. */
    public enum DialogKind {
        DESTRUCTIVE_CONFIRMATION,
        FAILURE
    }

    /** Complete typed action vocabulary returned from Workbench dialogs. */
    public enum DialogAction {
        SAVE,
        DISCARD,
        CANCEL,
        COPY_DETAILS,
        RETRY,
        CLOSE
    }

    /** Immutable dialog description rendered by the JavaFX platform adapter. */
    public record DialogSpec(DialogKind kind, Severity severity, String title, String message,
            Optional<String> details, List<DialogAction> actions,
            DialogAction safeDefault, DialogAction cancelAction) {
        /** Creates the accepted safe-default destructive confirmation shape. */
        public static DialogSpec destructiveConfirmation(String title, String message) {
            return new DialogSpec(DialogKind.DESTRUCTIVE_CONFIRMATION, Severity.WARNING, title, message,
                    Optional.empty(),
                    List.of(DialogAction.DISCARD, DialogAction.CANCEL),
                    DialogAction.CANCEL, DialogAction.CANCEL);
        }

        /** Creates the accepted dirty-shutdown dialog with Cancel as both Enter and Escape safety. */
        public static DialogSpec unsavedClose(String title, String message) {
            return new DialogSpec(DialogKind.DESTRUCTIVE_CONFIRMATION, Severity.WARNING, title, message,
                    Optional.empty(),
                    List.of(DialogAction.SAVE, DialogAction.DISCARD, DialogAction.CANCEL),
                    DialogAction.CANCEL, DialogAction.CANCEL);
        }

        /**
         * Creates a failure dialog with copyable details and a Retry action only when the operation supports it.
         *
         * @param title concise failed-operation title
         * @param message user-facing failure summary
         * @param details complete diagnostic text available to copy
         * @param retryable whether a new linked operation attempt can be launched
         * @return immutable accepted failure-dialog shape
         */
        public static DialogSpec failure(String title, String message, String details, boolean retryable) {
            List<DialogAction> actions = retryable
                    ? List.of(DialogAction.COPY_DETAILS, DialogAction.RETRY, DialogAction.CLOSE)
                    : List.of(DialogAction.COPY_DETAILS, DialogAction.CLOSE);
            return new DialogSpec(DialogKind.FAILURE, Severity.FAILURE, title, message,
                    Optional.of(requireText(details, "details")), actions,
                    DialogAction.CLOSE, DialogAction.CLOSE);
        }

        /** Rejects dialogs whose default or cancellation action cannot actually be chosen. */
        public DialogSpec {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(severity, "severity");
            title = requireText(title, "title");
            message = requireText(message, "message");
            details = Objects.requireNonNull(details, "details").map(value -> requireText(value, "details"));
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
            if (actions.isEmpty())
                throw new IllegalArgumentException("actions must not be empty");
            Objects.requireNonNull(safeDefault, "safeDefault");
            Objects.requireNonNull(cancelAction, "cancelAction");
            if (!actions.contains(safeDefault) || !actions.contains(cancelAction))
                throw new IllegalArgumentException("default and cancel actions must be present");
        }
    }

    /** Durable pending-dialog state published before the platform adapter opens a modal. */
    public record PendingDialog(long token, DialogSpec spec) {
        /** Rejects stale-shaped pending dialog values at construction time. */
        public PendingDialog {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(spec, "spec");
        }
    }

    /** Tokenized result returned by a platform dialog as an ordinary Workbench intent. */
    public record DialogResult(long token, DialogAction action) {
        /** Rejects incomplete platform dialog results. */
        public DialogResult {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(action, "action");
        }
    }

    /** Result of applying a typed dialog answer to durable feedback state. */
    public record DialogUpdate(boolean accepted, Frame frame, Optional<DialogResult> result) {
        /** Rejects incomplete dialog-update values. */
        public DialogUpdate {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(result, "result");
        }
    }

    /** One completely described user-facing operation outcome. */
    public record Notification(String operation, Severity severity, String message, Disposition disposition) {
        /** Rejects incomplete notification state at construction time. */
        public Notification {
            operation = requireText(operation, "operation");
            Objects.requireNonNull(severity, "severity");
            message = requireText(message, "message");
            Objects.requireNonNull(disposition, "disposition");
        }
    }

    /** Current inline feedback for the initiating pane. */
    public record InfoBar(Severity severity, String cue, SemanticIcons.IconKey icon, String message) {
        /** Rejects a partially projected inline message. */
        public InfoBar {
            Objects.requireNonNull(severity, "severity");
            cue = requireText(cue, "cue");
            Objects.requireNonNull(icon, "icon");
            message = requireText(message, "message");
        }
    }

    /** Durable structured job evidence exposed through Activity accessibility details. */
    public record JobDetails(long attemptId, Optional<Long> retryOf, List<String> sources,
            List<String> destinations, Optional<String> capturedBasis, List<String> effectsCommitted,
            List<String> diagnosticCodes, boolean retryAvailable) {
        /** Defensively owns captured inputs, linkage, effects, and diagnostic identities. */
        public JobDetails {
            if (attemptId <= 0)
                throw new IllegalArgumentException("attemptId must be positive");
            Objects.requireNonNull(retryOf, "retryOf");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            destinations = List.copyOf(Objects.requireNonNull(destinations, "destinations"));
            Objects.requireNonNull(capturedBasis, "capturedBasis");
            effectsCommitted = List.copyOf(Objects.requireNonNull(effectsCommitted, "effectsCommitted"));
            diagnosticCodes = List.copyOf(Objects.requireNonNull(diagnosticCodes, "diagnosticCodes"));
        }
    }

    /** Durable session-scoped record of one user-visible operation outcome. */
    public record ActivityRecord(long id, String operation, Severity severity, String cue,
            SemanticIcons.IconKey icon, String message, Disposition disposition, Instant occurredAt,
            Optional<JobDetails> jobDetails) {
        /** Rejects incomplete or non-positive Activity identities. */
        public ActivityRecord {
            if (id <= 0)
                throw new IllegalArgumentException("id must be positive");
            operation = requireText(operation, "operation");
            Objects.requireNonNull(severity, "severity");
            cue = requireText(cue, "cue");
            Objects.requireNonNull(icon, "icon");
            message = requireText(message, "message");
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(jobDetails, "jobDetails");
        }
    }

    /** Concise status-bar projection of the latest operation. */
    public record StatusProjection(Severity severity, String message, String dispositionText) {
        /** Rejects incomplete status output. */
        public StatusProjection {
            Objects.requireNonNull(severity, "severity");
            message = requireText(message, "message");
            dispositionText = requireText(dispositionText, "dispositionText");
        }
    }

    /** Coherent durable feedback frame rendered before any later at-most-once effects. */
    public record Frame(long revision, Optional<InfoBar> infoBar, List<ActivityRecord> activities,
            StatusProjection status, Optional<PendingDialog> pendingDialog) {
        /** Defensively owns every collection and optional value in the frame. */
        public Frame {
            if (revision <= 0)
                throw new IllegalArgumentException("revision must be positive");
            Objects.requireNonNull(infoBar, "infoBar");
            activities = List.copyOf(Objects.requireNonNull(activities, "activities"));
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(pendingDialog, "pendingDialog");
        }
    }

    private final Clock clock;
    private final List<ActivityRecord> activities = new ArrayList<>();
    private long revision = 1;
    private long nextActivityId = 1;
    private long nextDialogToken = 1;
    private Frame frame = new Frame(revision, Optional.empty(), List.of(),
            new StatusProjection(Severity.INFORMATION, "Ready", "Completed"), Optional.empty());

    /**
     * Creates session-scoped feedback state with an injected clock for deterministic Activity timestamps.
     *
     * @param clock source of operation timestamps
     */
    public WorkbenchFeedback(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** @return the latest immutable feedback frame */
    public Frame frame() {
        return frame;
    }

    /**
     * Publishes one outcome consistently to inline feedback, durable Activity, and status.
     *
     * @param notification completely described operation outcome
     * @return the newly committed immutable frame
     */
    public Frame publish(Notification notification) {
        return publish(notification, Optional.empty());
    }

    /**
     * Publishes one outcome together with optional structured job evidence retained by Activity.
     *
     * @param notification completely described operation outcome
     * @param jobDetails captured inputs, linkage, effects, and diagnostics for a coordinated attempt
     * @return the newly committed immutable frame
     */
    public Frame publish(Notification notification, Optional<JobDetails> jobDetails) {
        Notification value = Objects.requireNonNull(notification, "notification");
        Optional<JobDetails> details = Objects.requireNonNull(jobDetails, "jobDetails");
        String cue = value.severity().cue();
        SemanticIcons.IconKey icon = value.severity().icon();
        InfoBar infoBar = new InfoBar(value.severity(), cue, icon, value.message());
        activities.add(new ActivityRecord(nextActivityId++, value.operation(), value.severity(), cue, icon,
                value.message(), value.disposition(), clock.instant(), details));
        StatusProjection status = new StatusProjection(value.severity(), value.message(),
                value.disposition().displayText());
        frame = new Frame(++revision, Optional.of(infoBar), activities, status, frame.pendingDialog());
        return frame;
    }

    /**
     * Dismisses only the current inline message while retaining Activity and the concise terminal status.
     *
     * @return the newly committed immutable frame
     */
    public Frame dismissInfoBar() {
        if (frame.infoBar().isEmpty())
            return frame;
        frame = new Frame(++revision, Optional.empty(), activities, frame.status(), frame.pendingDialog());
        return frame;
    }

    /**
     * Publishes one typed pending dialog before a platform adapter realizes the modal effect.
     *
     * @param spec complete dialog description
     * @return frame containing the newly tokenized pending dialog
     * @throws IllegalStateException when another application modal is already pending
     */
    public Frame requestDialog(DialogSpec spec) {
        if (frame.pendingDialog().isPresent())
            throw new IllegalStateException("Only one Workbench dialog may be active");
        PendingDialog pending = new PendingDialog(nextDialogToken++, Objects.requireNonNull(spec, "spec"));
        frame = new Frame(++revision, frame.infoBar(), activities, frame.status(), Optional.of(pending));
        return frame;
    }

    /**
     * Applies a matching dialog result, rejecting stale tokens without changing durable state.
     *
     * @param result tokenized platform result
     * @return accepted result and cleared pending state, or the unchanged frame for a stale answer
     */
    public DialogUpdate answerDialog(DialogResult result) {
        DialogResult answer = Objects.requireNonNull(result, "result");
        Optional<PendingDialog> pending = frame.pendingDialog();
        if (pending.isEmpty() || pending.orElseThrow().token() != answer.token())
            return new DialogUpdate(false, frame, Optional.empty());
        if (!pending.orElseThrow().spec().actions().contains(answer.action()))
            throw new IllegalArgumentException("Dialog action is not available for the pending request");
        frame = new Frame(++revision, frame.infoBar(), activities, frame.status(), Optional.empty());
        return new DialogUpdate(true, frame, Optional.of(answer));
    }

    /** Normalizes the shared non-blank invariant for user-facing feedback text. */
    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty())
            throw new IllegalArgumentException(name + " must not be blank");
        return text;
    }
}
