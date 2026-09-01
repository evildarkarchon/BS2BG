package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;
import com.asdasfa.jbs2bg.project.CancelledOutcome;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.ProjectContentVersion;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectEdit;
import com.asdasfa.jbs2bg.project.ProjectOperationContext;
import com.asdasfa.jbs2bg.project.ProjectOperationProgress;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetImportOutcome;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/**
 * Owns the Workbench's sole ProjectSession flow and publishes immutable Project frames.
 * Platform choosers return through tokenized responses so stale callbacks cannot mutate Project state.
 */
public final class WorkbenchProjectFlow {

    private final String applicationName;
    private final ProjectSession projectSession;
    private final JobCoordinator jobs;
    private long sequence;
    private long nextEffectToken;
    private Frame frame;
    private Effect pendingEffect;
    private Intent pendingIntent;
    /**
     * Creates the sole Workbench Project flow and establishes its initial clean New Project.
     *
     * @param applicationName stable base window title
     * @param projectSession  authoritative synchronous Project session
     */
    public WorkbenchProjectFlow(String applicationName, ProjectSession projectSession) {
        this(applicationName, projectSession, directCoordinator());
    }
    /**
     * Creates the sole Workbench Project flow on an injected application-wide job coordinator.
     *
     * @param applicationName stable base window title
     * @param projectSession  authoritative synchronous Project session
     * @param jobs            application-wide admission, progress, cancellation, retry, and shutdown owner
     */
    public WorkbenchProjectFlow(String applicationName, ProjectSession projectSession, JobCoordinator jobs) {
        this.applicationName = requireApplicationName(applicationName);
        this.projectSession = Objects.requireNonNull(projectSession, "projectSession");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.nextEffectToken = 1;
        publish(projectSession.newProject());
    }

    /**
     * Reports whether persistence produced a usable clean Project rather than cancellation, rejection, or failure.
     */
    private static boolean completedSuccessfully(ProjectOutcome outcome) {
        return !(outcome instanceof CancelledOutcome) && !(outcome instanceof FailedOutcome)
                && !(outcome instanceof RejectedOutcome)
                && !outcome.getSnapshot().isDirty();
    }

    /**
     * Adds the canonical extension without changing a caller-supplied case-insensitive match.
     */
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

    /**
     * Validates the user-visible application title once at construction.
     */
    private static String requireApplicationName(String name) {
        Objects.requireNonNull(name, "applicationName");
        if (name.isBlank())
            throw new IllegalArgumentException("applicationName must not be blank");
        return name;
    }

    /**
     * Classifies one typed Project outcome into durable job disposition, effects, and diagnostics.
     */
    private static JobCoordinator.Result<ProjectOutcome> openResult(ProjectOutcome outcome,
                                                                    Optional<String> staleReason) {
        List<JobCoordinator.Diagnostic> diagnostics = outcome.getDiagnostics().stream()
                .map(WorkbenchProjectFlow::jobDiagnostic)
                .toList();
        if (staleReason.isPresent()) {
            List<JobCoordinator.Diagnostic> staleDiagnostics = new java.util.ArrayList<>(diagnostics);
            staleDiagnostics.add(new JobCoordinator.Diagnostic("STALE_RESULT",
                    "Open result was not published because " + staleReason.orElseThrow() + ".",
                    Optional.empty()));
            return JobCoordinator.Result.completedWithIssues(outcome, "Open result was stale.", List.of(),
                    staleDiagnostics);
        }
        if (outcome instanceof CancelledOutcome)
            return JobCoordinator.Result.cancelled(outcome, "Open Project cancelled.", List.of(), diagnostics);
        if (outcome instanceof FailedOutcome || outcome instanceof RejectedOutcome)
            return JobCoordinator.Result.failed(outcome,
                    "Open Project failed with " + diagnosticSummary(diagnostics.size()) + ".", diagnostics);
        if (diagnostics.isEmpty())
            return JobCoordinator.Result.completed(outcome, "Project opened.", List.of("Project published"),
                    diagnostics);
        return JobCoordinator.Result.completedWithIssues(outcome,
                "Project opened with " + diagnosticSummary(diagnostics.size()) + ".",
                List.of("Project published"), diagnostics);
    }

    /**
     * Classifies one typed Save outcome into coordinator lifecycle, committed filesystem effects, and diagnostics.
     *
     * @param outcome     authoritative synchronous ProjectSession result
     * @param staleReason freshness refusal detected at the replacement boundary, when present
     * @return terminal coordinator result retaining the outcome needed on the publication lane
     */
    private static JobCoordinator.Result<ProjectOutcome> saveResult(ProjectOutcome outcome,
                                                                    Optional<String> staleReason) {
        List<JobCoordinator.Diagnostic> diagnostics = outcome.getDiagnostics().stream()
                .map(WorkbenchProjectFlow::jobDiagnostic)
                .toList();
        if (staleReason.isPresent()) {
            List<JobCoordinator.Diagnostic> staleDiagnostics = new java.util.ArrayList<>(diagnostics);
            staleDiagnostics.add(new JobCoordinator.Diagnostic("STALE_RESULT",
                    "Save result was not published because " + staleReason.orElseThrow() + ".",
                    Optional.empty()));
            return JobCoordinator.Result.completedWithIssues(outcome, "Save result was stale.", List.of(),
                    staleDiagnostics);
        }
        if (outcome instanceof CancelledOutcome)
            return JobCoordinator.Result.cancelled(outcome, "Save Project cancelled.", List.of(), diagnostics);
        if (outcome instanceof FailedOutcome || outcome instanceof RejectedOutcome)
            return JobCoordinator.Result.failed(outcome,
                    "Save Project failed with " + diagnosticSummary(diagnostics.size()) + ".", diagnostics);
        if (diagnostics.isEmpty())
            return JobCoordinator.Result.completed(outcome, "Project saved.", List.of("Project file replaced"),
                    diagnostics);
        return JobCoordinator.Result.completedWithIssues(outcome,
                "Project saved with " + diagnosticSummary(diagnostics.size()) + ".",
                List.of("Project file replaced"), diagnostics);
    }

    /**
     * Classifies one ordered BodySlide batch without flattening rejected, failed, partial, or cancelled source
     * outcomes. Successfully committed earlier sources remain explicit effects when a later source does not commit.
     *
     * @param outcome authoritative aggregate and per-source Project result
     * @param sources captured normalized source paths in processing order
     * @return terminal coordinator result carrying the aggregate for serialized Project publication
     */
    private static JobCoordinator.Result<SliderPresetImportOutcome> importResult(
            SliderPresetImportOutcome outcome, List<Path> sources) {
        List<JobCoordinator.Diagnostic> diagnostics = outcome.getDiagnostics().stream()
                .map(WorkbenchProjectFlow::jobDiagnostic)
                .toList();
        List<String> effects = new java.util.ArrayList<>();
        boolean usableSource = false;
        boolean failedSource = false;
        boolean issueSource = false;
        for (int index = 0; index < outcome.getSourceOutcomes().size(); index++) {
            ProjectOutcome sourceOutcome = outcome.getSourceOutcomes().get(index);
            if (sourceOutcome instanceof ChangedOutcome) {
                Path fileName = sources.get(index).getFileName();
                effects.add("Imported " + (fileName == null ? sources.get(index) : fileName));
                usableSource = true;
            } else if (sourceOutcome instanceof com.asdasfa.jbs2bg.project.UnchangedOutcome) {
                usableSource = true;
            } else if (sourceOutcome instanceof FailedOutcome) {
                failedSource = true;
                issueSource = true;
            } else if (sourceOutcome instanceof RejectedOutcome) {
                issueSource = true;
            }
        }
        if (outcome.getProjectOutcome() instanceof CancelledOutcome) {
            return JobCoordinator.Result.cancelled(outcome,
                    "BodySlide import cancelled after committing " + effects.size()
                            + (effects.size() == 1 ? " source." : " sources."),
                    effects, diagnostics);
        }
        if (failedSource && !usableSource)
            return JobCoordinator.Result.failed(outcome,
                    "BodySlide import failed with " + diagnosticSummary(diagnostics.size()) + ".", diagnostics);
        if (issueSource || !diagnostics.isEmpty())
            return JobCoordinator.Result.completedWithIssues(outcome,
                    "BodySlide import completed with " + diagnosticSummary(diagnostics.size()) + ".",
                    effects, diagnostics);
        return JobCoordinator.Result.completed(outcome,
                "BodySlide import completed from " + sources.size()
                        + (sources.size() == 1 ? " source." : " sources."),
                effects, diagnostics);
    }

    /**
     * Pluralizes the stable diagnostic count used in terminal Open summaries.
     */
    private static String diagnosticSummary(int count) {
        return count + (count == 1 ? " diagnostic" : " diagnostics");
    }

    /**
     * Converts one structured Project diagnostic without introducing presentation or JavaFX dependencies.
     */
    private static JobCoordinator.Diagnostic jobDiagnostic(ProjectDiagnostic diagnostic) {
        return new JobCoordinator.Diagnostic(diagnostic.getCode(), diagnostic.getMessage(),
                Optional.of(ProjectDiagnosticFormatter.format(List.of(diagnostic))));
    }

    /**
     * Builds the immediate coordinator retained by synchronous kernel tests and compatibility callers.
     */
    private static JobCoordinator directCoordinator() {
        return new JobCoordinator(new DirectExecutorService(), Runnable::run, Clock.systemUTC(),
                (delay, action) -> () -> {
                    // Immediate compatibility work cannot remain in Cancelling long enough for this timer.
                }, failure -> {
            // Compatibility callers have no technical diagnostics surface; the coordinator still retains it.
        });
    }

    /**
     * Immutable platform effect carrying the token required by its response.
     */
    public record Effect(long token, EffectKind kind) {
        /** Requires a positive token and non-null effect kind. */
        public Effect {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * Immutable response from a completed platform effect.
     */
    public record Response(ResponseKind kind, Path selectedPath, List<Path> selectedPaths) {
        /** Creates a response for a user-selected local path. */
        public static Response selected (Path path){
            return new Response(ResponseKind.PATH_SELECTED, Objects.requireNonNull(path, "path"), List.of());
        }

        /** Creates a response for one ordered, nonempty BodySlide source selection. */
        public static Response selectedSources(List<Path> paths) {
            List<Path> selected = List.copyOf(Objects.requireNonNull(paths, "paths"));
            if (selected.isEmpty())
                throw new IllegalArgumentException("selected BodySlide sources must not be empty");
            return new Response(ResponseKind.PATHS_SELECTED, null, selected);
        }

        /** Creates a response for a cancelled chooser or confirmation. */
        public static Response cancelled () {
            return new Response(ResponseKind.CANCELLED, null, List.of());
        }

        /** Creates a response that explicitly discards the dirty Project before continuing. */
        public static Response discard () {
            return new Response(ResponseKind.DISCARD, null, List.of());
        }

        /** Creates a response that saves the dirty Project before continuing. */
        public static Response save () {
            return new Response(ResponseKind.SAVE, null, List.of());
        }

        /** Requires exactly the selected value family belonging to the response kind. */
        public Response {
            Objects.requireNonNull(kind, "kind");
            selectedPaths = List.copyOf(Objects.requireNonNull(selectedPaths, "selectedPaths"));
            if ((kind == ResponseKind.PATH_SELECTED) == (selectedPath == null))
                throw new IllegalArgumentException("selectedPath must be present only for PATH_SELECTED");
            if ((kind == ResponseKind.PATHS_SELECTED) != !selectedPaths.isEmpty())
                throw new IllegalArgumentException("selectedPaths must be present only for PATHS_SELECTED");
        }
    }

    /**
     * Coherent Workbench projection of one completely published Project outcome.
     */
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

    /**
     * Result of one intent or platform response, including any next platform effect.
     */
    public record Update(boolean accepted, Frame frame, Optional<Effect> effect) {
        /** Requires a coherent frame and non-null optional effect. */
        public Update {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(effect, "effect");
        }
    }

    /**
     * @return the latest immutable, completely published Workbench Project frame
     */
    public Frame frame() {
        return frame;
    }

    /**
     * @return application-wide job coordinator used by this flow and its Workbench adapter
     */
    public JobCoordinator jobs() {
        return jobs;
    }

    /**
     * Accepts one serialized Project lifecycle intent.
     *
     * @param intent requested user task
     * @return the current or newly published frame plus any next chooser, confirmation, or close-window effect
     */
    public Update request(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (frame.closed() || pendingEffect != null || jobs.frame().active())
            return rejected();
        if (intent == Intent.CLOSE && !frame.snapshot().isDirty())
            return closeWindow();
        if (intent == Intent.NEW && !frame.snapshot().isDirty()) {
            publish(projectSession.newProject());
            return accepted(null);
        }
        if (intent == Intent.SAVE && frame.snapshot().getFileIdentity().isPresent()) {
            Path adoptedIdentity = frame.snapshot().getFileIdentity().orElseThrow();
            return admitSave(adoptedIdentity, true, false);
        }
        pendingIntent = intent;
        EffectKind kind = switch (intent) {
            case CLOSE -> EffectKind.CONFIRM_CLOSE;
            case NEW -> EffectKind.CONFIRM_NEW;
            case OPEN -> frame.snapshot().isDirty() ? EffectKind.CONFIRM_OPEN : EffectKind.CHOOSE_OPEN_PATH;
            case SAVE, SAVE_AS -> EffectKind.CHOOSE_SAVE_PATH;
            case IMPORT_BODYSLIDE -> EffectKind.CHOOSE_BODYSLIDE_SOURCES;
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
     * Completes one pending platform effect. A missing or stale token is rejected without invoking ProjectSession.
     *
     * @param token    token of the effect being completed
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
                Path adoptedIdentity = frame.snapshot().getFileIdentity().orElseThrow();
                return admitSave(adoptedIdentity, true, true);
            }
            if (response.kind() != ResponseKind.DISCARD)
                return rejected();
            clearPendingEffect();
            return closeWindow();
        }
        Intent completedIntent = pendingIntent;
        if (completedIntent == Intent.IMPORT_BODYSLIDE) {
            if (response.kind() != ResponseKind.PATHS_SELECTED)
                return rejected();
            clearPendingEffect();
            List<Path> sources = response.selectedPaths().stream()
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
            JobCoordinator.Admission admission = jobs.submit(importSubmission(sources));
            return admission.admitted() ? accepted(null) : rejected();
        }
        if (response.kind() != ResponseKind.PATH_SELECTED)
            return rejected();
        clearPendingEffect();
        if (completedIntent == Intent.OPEN) {
            Path source = response.selectedPath().toAbsolutePath().normalize();
            JobCoordinator.Admission admission = jobs.submit(openSubmission(source));
            return admission.admitted() ? accepted(null) : rejected();
        }
        Path target = projectPath(response.selectedPath());
        return switch (completedIntent) {
            case CLOSE -> admitSave(target, false, true);
            case NEW -> throw new IllegalStateException("New Project cannot complete from a path chooser");
            case OPEN -> throw new AssertionError("Open is admitted through the application job coordinator");
            case SAVE, SAVE_AS -> admitSave(target, false, false);
            case IMPORT_BODYSLIDE -> throw new AssertionError("BodySlide import uses the multi-source chooser");
        };
    }

    /**
     * Clears a consumed effect and its continuation together.
     */
    private void clearPendingEffect() {
        pendingEffect = null;
        pendingIntent = null;
    }

    /**
     * Rejects immediate legacy feature work while the lifecycle state machine owns a platform effect.
     */
    private void requireImmediateOperation() {
        boolean projectExclusiveJob = jobs.frame().attempt().stream()
                .filter(JobCoordinator.Attempt::active)
                .anyMatch(attempt -> attempt.operation().consistencyClass()
                        != JobCoordinator.ConsistencyClass.SNAPSHOT_DERIVED);
        if (frame.closed() || pendingEffect != null || projectExclusiveJob)
            throw new IllegalStateException("Workbench Project flow is not accepting immediate operations");
    }

    /**
     * Publishes exactly one ProjectSession outcome as the next coherent Workbench frame.
     */
    private void publish(ProjectOutcome outcome) {
        ProjectOutcome required = Objects.requireNonNull(outcome, "outcome");
        sequence++;
        frame = new Frame(sequence, required.getSnapshot(), required.getDiagnostics(), title(required.getSnapshot()),
                false);
    }

    /**
     * Admits one captured Save attempt and preserves synchronous compatibility when the injected coordinator runs
     * inline. Real application workers return the unchanged frame immediately.
     *
     * @param target          captured normalized destination
     * @param adoptedIdentity whether the operation is Save rather than Save As
     * @param closeOnSuccess  whether a successful clean publication completes a pending window close
     * @return admitted unchanged frame, synchronous compatibility result, or rejection
     */
    private Update admitSave(Path target, boolean adoptedIdentity, boolean closeOnSuccess) {
        JobCoordinator.Admission admission = jobs.submit(saveSubmission(target, adoptedIdentity, closeOnSuccess));
        if (!admission.admitted())
            return rejected();
        if (closeOnSuccess && frame.closed())
            return closeEffect();
        return accepted(null);
    }

    /**
     * Marks the flow closed before emitting its single final-window effect.
     */
    private Update closeWindow() {
        markClosed();
        return closeEffect();
    }

    /**
     * Marks the immutable Project frame closed on the serialized publication lane without inventing a new Project
     * publication sequence.
     */
    private void markClosed() {
        frame = new Frame(frame.sequence(), frame.snapshot(), frame.diagnostics(), frame.title(), true);
    }

    /**
     * Emits the platform close effect after the flow has already committed its closed state.
     *
     * @return accepted update carrying the final close-window effect
     */
    private Update closeEffect() {
        return accepted(new Effect(nextEffectToken++, EffectKind.CLOSE_WINDOW));
    }

    /**
     * Derives the Project title from the same immutable snapshot published in the frame.
     */
    private String title(ProjectSnapshot snapshot) {
        String dirty = snapshot.isDirty() ? "*" : "";
        if (snapshot.getFileIdentity().isEmpty())
            return applicationName + (dirty.isEmpty() ? "" : " " + dirty);
        Path identity = snapshot.getFileIdentity().orElseThrow();
        Path fileName = identity.getFileName();
        return applicationName + " - " + dirty + (fileName == null ? identity : fileName);
    }

    /**
     * Returns an accepted update and optionally carries the next platform effect.
     */
    private Update accepted(Effect effect) {
        return new Update(true, frame, Optional.ofNullable(effect));
    }

    /**
     * Returns the unchanged frame for a request that cannot be accepted.
     */
    private Update rejected() {
        return new Update(false, frame, Optional.empty());
    }

    /**
     * Captures one Open attempt and its retry factory without retaining mutable chooser or Project state.
     */
    private JobCoordinator.Submission<ProjectOutcome> openSubmission(Path source) {
        Path capturedSource = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        ProjectContentVersion capturedBasis = projectSession.getSnapshot().getContentVersion();
        OpenSourceStamp capturedSourceStamp = OpenSourceStamp.capture(capturedSource);
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Open Project",
                List.of(capturedSource.toString()), List.of(), Optional.of(capturedBasis.toString()));
        return new JobCoordinator.Submission<>(operation, context -> {
            OpenOperationContext projectContext = new OpenOperationContext(context, capturedBasis,
                    capturedSource, capturedSourceStamp);
            ProjectOutcome outcome = projectSession.open(capturedSource, projectContext);
            return openResult(outcome, projectContext.staleReason());
        }, (attempt, result) -> {
            boolean stale = result.diagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.code().equals("STALE_RESULT"));
            if (!stale && result.lifecycle() != JobCoordinator.Lifecycle.CANCELLED)
                result.value().ifPresent(this::publish);
        }, Optional.of(() -> openSubmission(capturedSource)));
    }

    /**
     * Captures one Save or Save As attempt, including destination, Project basis, retry, and optional close
     * continuation, before any worker code runs.
     *
     * @param target          normalized destination owned by this attempt
     * @param adoptedIdentity whether to invoke the adopted-identity Save overload
     * @param closeOnSuccess  whether successful completion closes the Workbench
     * @return fully captured coordinator submission
     */
    private JobCoordinator.Submission<ProjectOutcome> saveSubmission(Path target, boolean adoptedIdentity,
                                                                     boolean closeOnSuccess) {
        Path capturedTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        ProjectContentVersion capturedBasis = projectSession.getSnapshot().getContentVersion();
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Save Project", List.of(),
                List.of(capturedTarget.toString()), Optional.of(capturedBasis.toString()));
        return new JobCoordinator.Submission<>(operation, context -> {
            SaveOperationContext projectContext = new SaveOperationContext(context, capturedBasis,
                    adoptedIdentity ? Optional.of(capturedTarget) : Optional.empty());
            ProjectOutcome outcome = adoptedIdentity
                    ? projectSession.save(projectContext)
                    : projectSession.saveAs(capturedTarget, projectContext);
            return saveResult(outcome, projectContext.staleReason());
        }, (attempt, result) -> {
            boolean stale = result.diagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.code().equals("STALE_RESULT"));
            if (!stale && result.lifecycle() != JobCoordinator.Lifecycle.CANCELLED) {
                result.value().ifPresent(outcome -> {
                    publish(outcome);
                    if (closeOnSuccess && completedSuccessfully(outcome))
                        markClosed();
                });
            }
        }, Optional.of(() -> retrySaveSubmission(capturedTarget, adoptedIdentity, closeOnSuccess)));
    }

    /**
     * Recaptures the current adopted identity for Save retry while preserving an explicitly selected Save As target.
     *
     * @param capturedTarget  original target retained only for Save As
     * @param adoptedIdentity whether the retried operation uses the session's current identity
     * @param closeOnSuccess  whether successful retry completes a pending close
     * @return freshly captured retry submission
     * @throws IllegalStateException when adopted-identity Save no longer has an identity to retry
     */
    private JobCoordinator.Submission<ProjectOutcome> retrySaveSubmission(Path capturedTarget,
                                                                          boolean adoptedIdentity,
                                                                          boolean closeOnSuccess) {
        Path retryTarget = adoptedIdentity
                ? projectSession.getSnapshot().getFileIdentity().orElseThrow(() ->
                new IllegalStateException("Cannot retry Save without an adopted Project identity"))
                : capturedTarget;
        return saveSubmission(retryTarget, adoptedIdentity, closeOnSuccess);
    }

    /**
     * Captures one ordered BodySlide source batch, current Project basis, and fresh retry factory before dispatch.
     *
     * @param sources normalized selected sources in user-selected order
     * @return fully captured coordinator submission
     */
    private JobCoordinator.Submission<SliderPresetImportOutcome> importSubmission(List<Path> sources) {
        List<Path> capturedSources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (capturedSources.isEmpty())
            throw new IllegalArgumentException("BodySlide sources must not be empty");
        ProjectContentVersion capturedBasis = projectSession.getSnapshot().getContentVersion();
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Import BodySlide Presets",
                capturedSources.stream().map(Path::toString).toList(), List.of(),
                Optional.of(capturedBasis.toString()));
        return new JobCoordinator.Submission<>(operation, context -> {
            SliderPresetImportOutcome outcome = projectSession.importSliderPresets(capturedSources,
                    new JobOperationContext(context));
            if (!(outcome.getProjectOutcome() instanceof CancelledOutcome)) {
                if (context.beginCommit("Finalizing BodySlide import")) {
                    context.report(new JobCoordinator.Progress("Imported BodySlide sources",
                            Optional.of((long) capturedSources.size()), Optional.of((long) capturedSources.size()),
                            false));
                } else {
                    // Cancellation won the final boundary after per-source commits; retain those effects explicitly.
                    outcome = new SliderPresetImportOutcome(new CancelledOutcome(outcome.getSnapshot(),
                            outcome.getDiagnostics()), outcome.getSourceOutcomes());
                }
            }
            return importResult(outcome, capturedSources);
        }, (attempt, result) -> result.value().ifPresent(value -> publish(value.getProjectOutcome())),
                Optional.of(() -> importSubmission(capturedSources)));
    }

    /**
     * Lightweight filesystem identity used to reject an Open whose selected source changed in flight.
     */
    private record OpenSourceStamp(boolean readable, long size, FileTime modifiedAt, String fileKey) {
        /** Captures stable basic attributes without reading Project bytes on the JavaFX publication lane. */
        private static OpenSourceStamp capture (Path source){
            try {
                BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
                return new OpenSourceStamp(true, attributes.size(), attributes.lastModifiedTime(),
                        Objects.toString(attributes.fileKey(), ""));
            } catch (IOException exception) {
                return new OpenSourceStamp(false, -1L, FileTime.fromMillis(0L), "");
            }
        }
    }

    /**
     * User-level Project lifecycle requests accepted by the Workbench kernel.
     */
    public enum Intent {
        CLOSE,
        IMPORT_BODYSLIDE,
        NEW,
        OPEN,
        SAVE,
        SAVE_AS
    }

    /**
     * Platform effect kinds produced by Project lifecycle requests.
     */
    public enum EffectKind {
        CLOSE_WINDOW,
        CONFIRM_CLOSE,
        CONFIRM_NEW,
        CONFIRM_OPEN,
        CHOOSE_BODYSLIDE_SOURCES,
        CHOOSE_OPEN_PATH,
        CHOOSE_SAVE_PATH
    }

    /**
     * Complete family of values a chooser or confirmation may return.
     */
    public enum ResponseKind {
        PATH_SELECTED,
        PATHS_SELECTED,
        CANCELLED,
        DISCARD,
        SAVE
    }

    /**
     * ExecutorService adapter that runs one submitted task inline for compatibility tests.
     */
    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        /**
         * Rejects no task before shutdown and runs accepted work on the calling thread.
         */
        @Override
        public void execute(Runnable command) {
            if (shutdown)
                throw new java.util.concurrent.RejectedExecutionException("Direct coordinator is shut down");
            Objects.requireNonNull(command, "command").run();
        }

        /**
         * Prevents later compatibility submissions.
         */
        @Override
        public void shutdown() {
            shutdown = true;
        }

        /**
         * Prevents later work; inline execution leaves no queued tasks to return.
         */
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        /**
         * @return whether shutdown was requested
         */
        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        /**
         * @return whether shutdown was requested, since inline work never remains queued
         */
        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        /**
         * Inline work is already settled whenever this method is reached.
         */
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            Objects.requireNonNull(unit, "unit");
            return shutdown;
        }
    }

    /**
     * Project-operation adapter that forwards cancellation, progress, and commit linearization for one coordinator
     * attempt.
     */
    private class JobOperationContext implements ProjectOperationContext {
        private final JobCoordinator.Context jobContext;

        /**
         * Binds one synchronous Project operation to exactly one coordinator attempt.
         */
        private JobOperationContext(JobCoordinator.Context jobContext) {
            this.jobContext = Objects.requireNonNull(jobContext, "jobContext");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean cancellationRequested() {
            return jobContext.cancellationRequested();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void report(ProjectOperationProgress progress) {
            ProjectOperationProgress reported = Objects.requireNonNull(progress, "progress");
            JobCoordinator.Progress jobProgress;
            if (reported.completedUnits().isPresent()) {
                jobProgress = JobCoordinator.Progress.determinate(reported.phase(),
                        reported.completedUnits().orElseThrow(), reported.totalUnits().orElseThrow());
            } else {
                jobProgress = JobCoordinator.Progress.indeterminate(reported.phase(), true);
            }
            jobContext.report(jobProgress);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean beginCommit(String phase) {
            return jobContext.beginCommit(phase);
        }
    }

    /**
     * Open-specific Project context that rejects a detached candidate when its captured basis or source changed.
     */
    private final class OpenOperationContext extends JobOperationContext {
        private final ProjectContentVersion capturedBasis;
        private final Path capturedSource;
        private final OpenSourceStamp capturedSourceStamp;
        private String staleReason;

        /**
         * Captures immutable freshness state for exactly one Open worker invocation.
         */
        private OpenOperationContext(JobCoordinator.Context jobContext, ProjectContentVersion capturedBasis,
                                     Path capturedSource, OpenSourceStamp capturedSourceStamp) {
            super(jobContext);
            this.capturedBasis = Objects.requireNonNull(capturedBasis, "capturedBasis");
            this.capturedSource = Objects.requireNonNull(capturedSource, "capturedSource");
            this.capturedSourceStamp = Objects.requireNonNull(capturedSourceStamp, "capturedSourceStamp");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean beginCommit(String phase) {
            if (!capturedBasis.equals(projectSession.getSnapshot().getContentVersion())) {
                staleReason = "the active Project changed after admission";
                return false;
            }
            if (!capturedSourceStamp.equals(OpenSourceStamp.capture(capturedSource))) {
                staleReason = "the selected source changed after admission";
                return false;
            }
            return super.beginCommit(phase);
        }

        /**
         * @return freshness reason when input change, rather than cancellation, refused publication
         */
        private Optional<String> staleReason() {
            return Optional.ofNullable(staleReason);
        }
    }

    /**
     * Save-specific Project context that prevents a queued attempt from replacing a file after its Project basis or
     * adopted identity changed.
     */
    private final class SaveOperationContext extends JobOperationContext {
        private final ProjectContentVersion capturedBasis;
        private final Optional<Path> capturedAdoptedIdentity;
        private String staleReason;

        /**
         * Captures freshness constraints for exactly one Save worker invocation.
         */
        private SaveOperationContext(JobCoordinator.Context jobContext, ProjectContentVersion capturedBasis,
                                     Optional<Path> capturedAdoptedIdentity) {
            super(jobContext);
            this.capturedBasis = Objects.requireNonNull(capturedBasis, "capturedBasis");
            this.capturedAdoptedIdentity = Objects.requireNonNull(capturedAdoptedIdentity,
                    "capturedAdoptedIdentity");
        }

        /**
         * Rejects replacement if queued Save inputs no longer describe the active Project.
         */
        @Override
        public boolean beginCommit(String phase) {
            ProjectSnapshot current = projectSession.getSnapshot();
            if (!capturedBasis.equals(current.getContentVersion())) {
                staleReason = "the active Project changed after admission";
                return false;
            }
            if (capturedAdoptedIdentity.isPresent()
                    && !capturedAdoptedIdentity.equals(current.getFileIdentity())) {
                staleReason = "the adopted Project identity changed after admission";
                return false;
            }
            return super.beginCommit(phase);
        }

        /**
         * @return freshness reason when input change, rather than cancellation, refused replacement
         */
        private Optional<String> staleReason() {
            return Optional.ofNullable(staleReason);
        }
    }
}
