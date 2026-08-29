package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectEdit;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetImportOutcome;

/**
 * Owns the Workbench's sole ProjectSession flow and publishes immutable Project frames.
 * Platform choosers return through tokenized responses so stale callbacks cannot mutate Project state.
 */
public final class WorkbenchProjectFlow {

    /** User-level Project lifecycle requests accepted by the Workbench kernel. */
    public enum Intent {
        CLOSE,
        NEW,
        OPEN,
        SAVE,
        SAVE_AS
    }

    /** Platform effect kinds produced by Project lifecycle requests. */
    public enum EffectKind {
        CLOSE_WINDOW,
        CONFIRM_CLOSE,
        CONFIRM_NEW,
        CONFIRM_OPEN,
        CHOOSE_OPEN_PATH,
        CHOOSE_SAVE_PATH
    }

    /** Complete family of values a chooser or confirmation may return. */
    public enum ResponseKind {
        PATH_SELECTED,
        CANCELLED,
        DISCARD,
        SAVE
    }

    /** Immutable platform effect carrying the token required by its response. */
    public record Effect(long token, EffectKind kind) {
        /** Requires a positive token and non-null effect kind. */
        public Effect {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** Immutable response from a completed platform effect. */
    public record Response(ResponseKind kind, Path selectedPath) {
        /** Creates a response for a user-selected local path. */
        public static Response selected(Path path) {
            return new Response(ResponseKind.PATH_SELECTED, Objects.requireNonNull(path, "path"));
        }

        /** Creates a response for a cancelled chooser or confirmation. */
        public static Response cancelled() {
            return new Response(ResponseKind.CANCELLED, null);
        }

        /** Creates a response that explicitly discards the dirty Project before continuing. */
        public static Response discard() {
            return new Response(ResponseKind.DISCARD, null);
        }

        /** Creates a response that saves the dirty Project before continuing. */
        public static Response save() {
            return new Response(ResponseKind.SAVE, null);
        }

        /** Requires a path only for the selected-path response kind. */
        public Response {
            Objects.requireNonNull(kind, "kind");
            if ((kind == ResponseKind.PATH_SELECTED) != (selectedPath != null))
                throw new IllegalArgumentException("selectedPath must be present only for PATH_SELECTED");
        }
    }

    /** Coherent Workbench projection of one completely published Project outcome. */
    public record Frame(long sequence, ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics, String title,
            boolean closed) {
        /** Defensively owns the diagnostics associated with this publication. */
        public Frame {
            if (sequence <= 0)
                throw new IllegalArgumentException("sequence must be positive");
            Objects.requireNonNull(snapshot, "snapshot");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            Objects.requireNonNull(title, "title");
        }
    }

    /** Result of one intent or platform response, including any next platform effect. */
    public record Update(boolean accepted, Frame frame, Optional<Effect> effect) {
        /** Requires a coherent frame and non-null optional effect. */
        public Update {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(effect, "effect");
        }
    }

    private final String applicationName;
    private final ProjectSession projectSession;
    private long sequence;
    private long nextEffectToken;
    private Frame frame;
    private Effect pendingEffect;
    private Intent pendingIntent;

    /**
     * Creates the sole Workbench Project flow and establishes its initial clean New Project.
     *
     * @param applicationName stable base window title
     * @param projectSession authoritative synchronous Project session
     */
    public WorkbenchProjectFlow(String applicationName, ProjectSession projectSession) {
        this.applicationName = requireApplicationName(applicationName);
        this.projectSession = Objects.requireNonNull(projectSession, "projectSession");
        this.nextEffectToken = 1;
        publish(projectSession.newProject());
    }

    /** @return the latest immutable, completely published Workbench Project frame */
    public Frame frame() {
        return frame;
    }

    /**
     * Accepts one serialized Project lifecycle intent.
     *
     * @param intent requested user task
     * @return the current or newly published frame plus any next chooser, confirmation, or close-window effect
     */
    public Update request(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (frame.closed() || pendingEffect != null)
            return rejected();
        if (intent == Intent.CLOSE && !frame.snapshot().isDirty())
            return closeWindow();
        if (intent == Intent.NEW && !frame.snapshot().isDirty()) {
            publish(projectSession.newProject());
            return accepted(null);
        }
        if (intent == Intent.SAVE && frame.snapshot().getFileIdentity().isPresent()) {
            publish(projectSession.save());
            return accepted(null);
        }
        pendingIntent = intent;
        EffectKind kind = switch (intent) {
        case CLOSE -> EffectKind.CONFIRM_CLOSE;
        case NEW -> EffectKind.CONFIRM_NEW;
        case OPEN -> frame.snapshot().isDirty() ? EffectKind.CONFIRM_OPEN : EffectKind.CHOOSE_OPEN_PATH;
        case SAVE, SAVE_AS -> EffectKind.CHOOSE_SAVE_PATH;
        };
        pendingEffect = new Effect(nextEffectToken++, kind);
        return accepted(pendingEffect);
    }

    /**
     * Applies one immediate Project edit through the authoritative session and publishes its exact outcome.
     * This migration seam rejects edits while a platform effect is pending or after shutdown.
     *
     * @param edit immutable Project edit request
     * @return the exact typed ProjectSession outcome
     * @throws IllegalStateException when the flow is awaiting an effect or already closed
     */
    public ProjectOutcome apply(ProjectEdit edit) {
        requireImmediateOperation();
        ProjectOutcome outcome = projectSession.apply(Objects.requireNonNull(edit, "edit"));
        publish(outcome);
        return outcome;
    }

    /**
     * Imports an ordered BodySlide source batch through the authoritative session and publishes its aggregate frame.
     *
     * @param sources selected source paths in processing order
     * @return the exact aggregate and per-source ProjectSession outcome
     * @throws IllegalStateException when the flow is awaiting an effect or already closed
     */
    public SliderPresetImportOutcome importSliderPresets(List<Path> sources) {
        requireImmediateOperation();
        SliderPresetImportOutcome outcome = projectSession.importSliderPresets(
                Objects.requireNonNull(sources, "sources"));
        publish(outcome.getProjectOutcome());
        return outcome;
    }

    /**
     * Completes one pending platform effect. A missing or stale token is rejected without invoking ProjectSession.
     *
     * @param token token of the effect being completed
     * @param response selected platform value
     * @return the resulting Project frame, or the unchanged frame for a stale response
     */
    public Update respond(long token, Response response) {
        Objects.requireNonNull(response, "response");
        if (frame.closed() || pendingEffect == null || pendingEffect.token() != token)
            return rejected();
        if (response.kind() == ResponseKind.CANCELLED) {
            clearPendingEffect();
            return accepted(null);
        }
        if (pendingEffect.kind() == EffectKind.CONFIRM_NEW) {
            if (response.kind() != ResponseKind.DISCARD)
                return rejected();
            clearPendingEffect();
            publish(projectSession.newProject());
            return accepted(null);
        }
        if (pendingEffect.kind() == EffectKind.CONFIRM_OPEN) {
            if (response.kind() != ResponseKind.DISCARD)
                return rejected();
            clearPendingEffect();
            pendingIntent = Intent.OPEN;
            pendingEffect = new Effect(nextEffectToken++, EffectKind.CHOOSE_OPEN_PATH);
            return accepted(pendingEffect);
        }
        if (pendingEffect.kind() == EffectKind.CONFIRM_CLOSE) {
            if (response.kind() == ResponseKind.SAVE) {
                clearPendingEffect();
                if (frame.snapshot().getFileIdentity().isEmpty()) {
                    pendingIntent = Intent.CLOSE;
                    pendingEffect = new Effect(nextEffectToken++, EffectKind.CHOOSE_SAVE_PATH);
                    return accepted(pendingEffect);
                }
                ProjectOutcome saved = projectSession.save();
                publish(saved);
                return completedSuccessfully(saved) ? closeWindow() : accepted(null);
            }
            if (response.kind() != ResponseKind.DISCARD)
                return rejected();
            clearPendingEffect();
            return closeWindow();
        }
        if (response.kind() != ResponseKind.PATH_SELECTED)
            return rejected();
        Intent completedIntent = pendingIntent;
        clearPendingEffect();
        ProjectOutcome outcome = switch (completedIntent) {
        case CLOSE -> projectSession.saveAs(projectPath(response.selectedPath()));
        case NEW -> throw new IllegalStateException("New Project cannot complete from a path chooser");
        case OPEN -> projectSession.open(response.selectedPath());
        case SAVE, SAVE_AS -> projectSession.saveAs(projectPath(response.selectedPath()));
        };
        publish(outcome);
        if (completedIntent == Intent.CLOSE && completedSuccessfully(outcome))
            return closeWindow();
        return accepted(null);
    }

    /** Clears a consumed effect and its continuation together. */
    private void clearPendingEffect() {
        pendingEffect = null;
        pendingIntent = null;
    }

    /** Rejects immediate legacy feature work while the lifecycle state machine owns a platform effect. */
    private void requireImmediateOperation() {
        if (frame.closed() || pendingEffect != null)
            throw new IllegalStateException("Workbench Project flow is not accepting immediate operations");
    }

    /** Reports whether persistence produced a usable clean Project rather than a typed rejection or failure. */
    private static boolean completedSuccessfully(ProjectOutcome outcome) {
        return !(outcome instanceof FailedOutcome) && !(outcome instanceof RejectedOutcome)
                && !outcome.getSnapshot().isDirty();
    }

    /** Publishes exactly one ProjectSession outcome as the next coherent Workbench frame. */
    private void publish(ProjectOutcome outcome) {
        ProjectOutcome required = Objects.requireNonNull(outcome, "outcome");
        sequence++;
        frame = new Frame(sequence, required.getSnapshot(), required.getDiagnostics(), title(required.getSnapshot()),
                false);
    }

    /** Marks the flow closed before emitting its single final-window effect. */
    private Update closeWindow() {
        frame = new Frame(frame.sequence(), frame.snapshot(), frame.diagnostics(), frame.title(), true);
        return accepted(new Effect(nextEffectToken++, EffectKind.CLOSE_WINDOW));
    }

    /** Adds the canonical extension without changing a caller-supplied case-insensitive match. */
    private static Path projectPath(Path selected) {
        Path normalized = selected.toAbsolutePath().normalize();
        Path fileName = normalized.getFileName();
        String name = fileName == null ? normalized.toString() : fileName.toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".jbs2bg"))
            return normalized;
        Path parent = normalized.getParent();
        String extended = name + ".jbs2bg";
        return parent == null ? Path.of(extended).toAbsolutePath().normalize() : parent.resolve(extended);
    }

    /** Derives the Project title from the same immutable snapshot published in the frame. */
    private String title(ProjectSnapshot snapshot) {
        String dirty = snapshot.isDirty() ? "*" : "";
        if (snapshot.getFileIdentity().isEmpty())
            return applicationName + (dirty.isEmpty() ? "" : " " + dirty);
        Path identity = snapshot.getFileIdentity().orElseThrow();
        Path fileName = identity.getFileName();
        return applicationName + " - " + dirty + (fileName == null ? identity : fileName);
    }

    /** Validates the user-visible application title once at construction. */
    private static String requireApplicationName(String name) {
        Objects.requireNonNull(name, "applicationName");
        if (name.isBlank())
            throw new IllegalArgumentException("applicationName must not be blank");
        return name;
    }

    /** Returns an accepted update and optionally carries the next platform effect. */
    private Update accepted(Effect effect) {
        return new Update(true, frame, Optional.ofNullable(effect));
    }

    /** Returns the unchanged frame for a request that cannot be accepted. */
    private Update rejected() {
        return new Update(false, frame, Optional.empty());
    }
}
