package com.asdasfa.jbs2bg.workbench.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Coordinates the one application-wide user-visible job behind a JavaFX-independent interface.
 * State commits precede serialized observer publication, and worker completion is accepted only for the current
 * attempt so late callbacks cannot overwrite newer presentation state.
 */
public final class JobCoordinator implements AutoCloseable {

    private static final Duration PROLONGED_CANCELLATION_DELAY = Duration.ofSeconds(5);
    private final Object lock = new Object();
    private final ExecutorService workerExecutor;
    private final Executor publicationExecutor;
    private final Clock clock;
    private final DelayScheduler delayScheduler;
    private final Consumer<Throwable> callbackFailureSink;
    private final Map<Long, Consumer<Frame>> observers = new LinkedHashMap<>();
    private final Map<AttemptId, RetryFactory<?>> completedRetryFactories = new LinkedHashMap<>();
    private final List<Diagnostic> technicalDiagnostics = new ArrayList<>();
    private long nextAttemptId = 1;
    private long nextObserverId = 1;
    private long nextSequence = 2;
    private Frame frame = new Frame(1, Optional.empty(), false, false, List.of());
    private Active<?> active;
    private boolean shutdownRequested;
    private boolean closed;

    /**
     * Type-safe terminal worker result delivered only for its accepted current attempt.
     */
    public record Result<T>(Lifecycle lifecycle, Optional<T> value, String summary, List<String> effectsCommitted,
                            List<Diagnostic> diagnostics) {
        /** Creates a successful result carrying its usable typed value. */
        public static <T> Result<T> completed(T value, String summary, List<String> effectsCommitted,
                                             List<Diagnostic> diagnostics) {
            return new Result<>(Lifecycle.COMPLETED, Optional.of(Objects.requireNonNull(value, "value")), summary,
                    effectsCommitted, diagnostics);
        }

        /** Creates a usable result whose structured diagnostics require attention. */
        public static <T> Result<T> completedWithIssues(T value, String summary, List<String> effectsCommitted,
                                                       List<Diagnostic> diagnostics) {
            return new Result<>(Lifecycle.COMPLETED_WITH_ISSUES,
                    Optional.of(Objects.requireNonNull(value, "value")), summary, effectsCommitted, diagnostics);
        }

        /** Creates a cancellation result without a usable value or unsafe committed effect. */
        public static <T> Result<T> cancelled(String summary, List<String> effectsCommitted,
                                             List<Diagnostic> diagnostics) {
            return new Result<>(Lifecycle.CANCELLED, Optional.empty(), summary, effectsCommitted, diagnostics);
        }

        /** Creates a cancellation result carrying an authoritative unchanged or partially committed typed outcome. */
        public static <T> Result<T> cancelled(T value, String summary, List<String> effectsCommitted,
                                             List<Diagnostic> diagnostics) {
            return new Result<>(Lifecycle.CANCELLED, Optional.of(Objects.requireNonNull(value, "value")), summary,
                    effectsCommitted, diagnostics);
        }

        /** Creates a failed result without a usable value. */
        public static <T> Result<T> failed(String summary, List<Diagnostic> diagnostics) {
            return new Result<>(Lifecycle.FAILED, Optional.empty(), summary, List.of(), diagnostics);
        }

        /** Creates a failed result carrying an authoritative domain outcome for serialized presentation. */
        public static <T> Result<T> failed(T value, String summary, List<Diagnostic> diagnostics) {
            return new Result<>(Lifecycle.FAILED, Optional.of(Objects.requireNonNull(value, "value")), summary,
                    List.of(), diagnostics);
        }

        /** Rejects active lifecycles and successful results that omit their usable typed value. */
        public Result {
            Objects.requireNonNull(lifecycle, "lifecycle");
            if (!lifecycle.terminal())
                throw new IllegalArgumentException("worker results must be terminal");
            value = Objects.requireNonNull(value, "value");
            summary = requireText(summary, "summary");
            effectsCommitted = copyText(effectsCommitted, "effectsCommitted");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if (diagnostics.stream().anyMatch(Objects::isNull))
                throw new NullPointerException("diagnostics contains null");
            boolean usable = lifecycle == Lifecycle.COMPLETED || lifecycle == Lifecycle.COMPLETED_WITH_ISSUES;
            if (usable && value.isEmpty())
                throw new IllegalArgumentException("successful completion dispositions require a value");
        }
    }

    /**
     * Fully captured request for a new attempt; only the coordinator stamps retry linkage.
     */
    public record Submission<T>(Operation operation, Work<T> work, Completion<T> completion,
                                Optional<RetryFactory<T>> retryFactory, ResultResolver<T> resultResolver) {
        /** Creates a request whose worker result needs no serialized freshness classification. */
        public Submission(Operation operation, Work<T> work, Completion<T> completion,
                          Optional<RetryFactory<T>> retryFactory) {
            this(operation, work, completion, retryFactory, (attempt, result) -> result);
        }

        /** Requires immutable operation context, executable callbacks, and an explicit retry capability. */
        public Submission {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(work, "work");
            Objects.requireNonNull(completion, "completion");
            Objects.requireNonNull(retryFactory, "retryFactory");
            Objects.requireNonNull(resultResolver, "resultResolver");
        }
    }

    /**
     * Creates one coordinator from injected worker, serialized publication, clock, delay, and failure adapters.
     * The publication executor must serialize callbacks in submission order.
     *
     * @param workerExecutor      application-owned worker executor
     * @param publicationExecutor serialized presentation-lane executor
     * @param clock               timestamps attempt evidence
     * @param delayScheduler      schedules prolonged-cancellation status only
     * @param callbackFailureSink records observer/completion failures outside job disposition
     */
    public JobCoordinator(ExecutorService workerExecutor, Executor publicationExecutor, Clock clock,
                          DelayScheduler delayScheduler, Consumer<Throwable> callbackFailureSink) {
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "workerExecutor");
        this.publicationExecutor = Objects.requireNonNull(publicationExecutor, "publicationExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.delayScheduler = Objects.requireNonNull(delayScheduler, "delayScheduler");
        this.callbackFailureSink = Objects.requireNonNull(callbackFailureSink, "callbackFailureSink");
    }

    /**
     * Converts an unexpected throwable to structured diagnostic evidence.
     */
    private static Diagnostic diagnostic(String code, String message, Throwable failure) {
        return new Diagnostic(code, message, Optional.of(failure.getClass().getName() + ": "
                + readableMessage(failure)));
    }

    /**
     * Returns stable nonblank throwable text without assuming providers supply a message.
     */
    private static String readableMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    /**
     * Normalizes one nonblank interface string.
     */
    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty())
            throw new IllegalArgumentException(name + " must not be blank");
        return text;
    }

    /**
     * Defensively copies a non-null list of nonblank interface strings.
     */
    private static List<String> copyText(List<String> values, String name) {
        List<String> copy = new ArrayList<>();
        for (String value : Objects.requireNonNull(values, name))
            copy.add(requireText(value, name + " value"));
        return List.copyOf(copy);
    }

    /**
     * Opaque, monotonically increasing identity of one coordinator attempt.
     */
    public record AttemptId(long value) {
        /** Rejects identities that could be confused with the absence of an attempt. */
        public AttemptId {
            if (value <= 0)
                throw new IllegalArgumentException("value must be positive");
        }
    }

    /**
     * Immutable captured operation identity and user-facing source/destination context.
     */
    public record Operation(String name, List<String> sourceLabels, List<String> destinationLabels,
                            Optional<String> capturedBasis, ConsistencyClass consistencyClass) {
        /** Creates a Project-exclusive operation, preserving the established default for lifecycle and import jobs. */
        public Operation(String name, List<String> sourceLabels, List<String> destinationLabels,
                         Optional<String> capturedBasis) {
            this(name, sourceLabels, destinationLabels, capturedBasis, ConsistencyClass.PROJECT_EXCLUSIVE);
        }

        /** Defensively owns labels and rejects incomplete operation descriptions. */
        public Operation {
            name = requireText(name, "name");
            sourceLabels = copyText(sourceLabels, "sourceLabels");
            destinationLabels = copyText(destinationLabels, "destinationLabels");
            capturedBasis = Objects.requireNonNull(capturedBasis, "capturedBasis")
                    .map(value -> requireText(value, "capturedBasis value"));
            Objects.requireNonNull(consistencyClass, "consistencyClass");
        }
    }

    /**
     * Truthful current phase with either measured units or indeterminate progress.
     */
    public record Progress(String phase, Optional<Long> completedUnits, Optional<Long> totalUnits,
                           boolean cancellable) {
        /** Creates indeterminate phase progress without synthesizing a percentage. */
        public static Progress indeterminate (String phase,boolean cancellable){
            return new Progress(phase, Optional.empty(), Optional.empty(), cancellable);
        }

        /** Creates measured phase progress whose percentage is derived only from supplied real units. */
        public static Progress determinate (String phase,long completedUnits, long totalUnits){
            return new Progress(phase, Optional.of(completedUnits), Optional.of(totalUnits), true);
        }

        /** Rejects partial or impossible measured-progress values. */
        public Progress {
            phase = requireText(phase, "phase");
            completedUnits = Objects.requireNonNull(completedUnits, "completedUnits");
            totalUnits = Objects.requireNonNull(totalUnits, "totalUnits");
            if (completedUnits.isPresent() != totalUnits.isPresent())
                throw new IllegalArgumentException("completedUnits and totalUnits must either both be present or absent");
            if (completedUnits.isPresent()) {
                long completed = completedUnits.orElseThrow();
                long total = totalUnits.orElseThrow();
                if (completed < 0 || total <= 0 || completed > total)
                    throw new IllegalArgumentException("measured progress must satisfy 0 <= completed <= total");
            }
        }

        /** @return floor percentage derived from real units, or empty for indeterminate progress */
        public OptionalInt percentage () {
            if (completedUnits.isEmpty())
                return OptionalInt.empty();
            double ratio = (double) completedUnits.orElseThrow() / (double) totalUnits.orElseThrow();
            return OptionalInt.of((int) Math.floor(ratio * 100.0));
        }
    }

    /**
     * Structured coordinator or worker diagnostic retained independently from terminal classification.
     */
    public record Diagnostic(String code, String message, Optional<String> details) {
        /** Rejects incomplete diagnostic values. */
        public Diagnostic {
            code = requireText(code, "code");
            message = requireText(message, "message");
            details = Objects.requireNonNull(details, "details").map(value -> requireText(value, "details value"));
        }
    }

    /**
     * Immutable public state of the latest attempt.
     */
    public record Attempt(AttemptId id, Operation operation, Optional<AttemptId> retryOf, Lifecycle lifecycle,
                          Progress progress, Instant startedAt, Optional<Instant> completedAt, String summary,
                          List<String> effectsCommitted, List<Diagnostic> diagnostics, boolean cancellationProlonged,
                          boolean retryAvailable) {
        /** Defensively owns all terminal evidence and validates lifecycle/time coherence. */
        public Attempt {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(retryOf, "retryOf");
            Objects.requireNonNull(lifecycle, "lifecycle");
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(startedAt, "startedAt");
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
            summary = Objects.requireNonNull(summary, "summary");
            effectsCommitted = copyText(effectsCommitted, "effectsCommitted");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if (diagnostics.stream().anyMatch(Objects::isNull))
                throw new NullPointerException("diagnostics contains null");
            if (lifecycle.terminal() != completedAt.isPresent())
                throw new IllegalArgumentException("terminal attempts require completedAt and active attempts forbid it");
            if (lifecycle.terminal() && summary.isBlank())
                throw new IllegalArgumentException("terminal summary must not be blank");
        }

        /** @return true while this attempt still owns application-wide admission */
        public boolean active () {
            return !lifecycle.terminal();
        }
    }

    /**
     * Immutable coordinator publication containing the latest attempt and technical isolation diagnostics.
     */
    public record Frame(long sequence, Optional<Attempt> attempt, boolean shutdownRequested, boolean shutdownReady,
                        List<Diagnostic> technicalDiagnostics) {
        /** Rejects incoherent frame sequence and shutdown state. */
        public Frame {
            if (sequence <= 0)
                throw new IllegalArgumentException("sequence must be positive");
            Objects.requireNonNull(attempt, "attempt");
            technicalDiagnostics = List.copyOf(Objects.requireNonNull(technicalDiagnostics,
                    "technicalDiagnostics"));
            if (technicalDiagnostics.stream().anyMatch(Objects::isNull))
                throw new NullPointerException("technicalDiagnostics contains null");
            if (shutdownReady && !shutdownRequested)
                throw new IllegalArgumentException("shutdownReady requires shutdownRequested");
        }

        /** @return true while an admitted attempt still owns the application-wide job slot */
        public boolean active () {
            return attempt.stream().anyMatch(Attempt::active);
        }

        /**
         * @return whether shutdown or the active operation requires ordinary Project/Settings mutations to pause
         */
        public boolean projectMutationsBlocked() {
            return shutdownRequested || attempt.stream().filter(Attempt::active)
                    .anyMatch(value -> value.operation().consistencyClass()
                            != ConsistencyClass.SNAPSHOT_DERIVED);
        }
    }

    /**
     * Admission result carrying either the new attempt identity or the active operation that caused rejection.
     */
    public record Admission(boolean admitted, Optional<AttemptId> attempt, Optional<String> activeOperation) {
        /** Enforces the mutually exclusive admitted/rejected result shape. */
        public Admission {
            Objects.requireNonNull(attempt, "attempt");
            activeOperation = Objects.requireNonNull(activeOperation, "activeOperation")
                    .map(value -> requireText(value, "activeOperation value"));
            if (admitted != attempt.isPresent() || admitted == activeOperation.isPresent())
                throw new IllegalArgumentException("admission must contain exactly one result detail");
        }
    }

    /**
     * @return latest immutable coordinator frame
     */
    public Frame frame() {
        synchronized (lock) {
            return frame;
        }
    }

    /**
     * Admits one captured attempt or rejects it immediately without queueing when work is active or shutdown began.
     *
     * @param submission fully captured work request
     * @return admitted attempt identity or the active operation name causing rejection
     */
    public <T> Admission submit(Submission<T> submission) {
        return submit(Objects.requireNonNull(submission, "submission"), Optional.empty());
    }

    /**
     * Re-captures and admits a new attempt linked to one retryable terminal attempt.
     *
     * @param failedAttempt terminal attempt selected by the user
     * @return normal admission result for the newly captured attempt
     */
    public Admission retry(AttemptId failedAttempt) {
        AttemptId selected = Objects.requireNonNull(failedAttempt, "failedAttempt");
        RetryFactory<?> retryFactory;
        synchronized (lock) {
            retryFactory = completedRetryFactories.get(selected);
            if (retryFactory == null)
                return new Admission(false, Optional.empty(), Optional.of("Retry is not available"));
            if (closed || shutdownRequested || active != null) {
                String operation = active == null ? "Application shutdown" : active.operation().name();
                return new Admission(false, Optional.empty(), Optional.of(operation));
            }
        }
        return recaptureRetry(selected, retryFactory);
    }

    /**
     * Captures a wildcard retry factory through a private generic bridge.
     */
    private <T> Admission recaptureRetry(AttemptId failedAttempt, RetryFactory<T> retryFactory) {
        Submission<T> recaptured;
        try {
            recaptured = Objects.requireNonNull(retryFactory.recapture(), "Retry factory returned null");
        } catch (RuntimeException | LinkageError failure) {
            recordCallbackFailure("JOB_RETRY_CAPTURE_FAILED", failure);
            return new Admission(false, Optional.empty(), Optional.of("Retry could not be captured"));
        }
        return submit(recaptured, Optional.of(failedAttempt));
    }

    /**
     * Admits an already captured first or retry submission and stamps coordinator-owned linkage.
     */
    private <T> Admission submit(Submission<T> request, Optional<AttemptId> retryOf) {
        Active<T> accepted;
        Frame published;
        synchronized (lock) {
            if (closed || shutdownRequested || active != null) {
                Optional<String> operation = active == null
                        ? Optional.of("Application shutdown")
                        : Optional.of(active.operation().name());
                return new Admission(false, Optional.empty(), operation);
            }
            AttemptId id = new AttemptId(nextAttemptId++);
            accepted = new Active<>(id, request, retryOf, clock.instant());
            active = accepted;
            published = commitFrame(accepted.attempt(), false);
        }
        dispatchObservers(published);
        try {
            Future<?> future = workerExecutor.submit(() -> runWorker(accepted));
            boolean cancelSubmitted;
            synchronized (lock) {
                accepted.setFuture(future);
                cancelSubmitted = accepted.cancelRequested();
            }
            if (cancelSubmitted)
                future.cancel(true);
        } catch (RuntimeException failure) {
            dispatchTerminal(accepted, Result.failed("The job could not be started.", List.of(diagnostic(
                    "JOB_EXECUTION_REJECTED", "The application worker rejected the job.", failure))));
        }
        return new Admission(true, Optional.of(accepted.id()), Optional.empty());
    }

    /**
     * Requests idempotent cancellation at the shared pre-commit linearization point.
     */
    public CancelResponse requestCancel() {
        Active<?> current;
        Frame published;
        boolean notStarted;
        Future<?> future;
        synchronized (lock) {
            current = active;
            if (current == null)
                return CancelResponse.NO_ACTIVE_JOB;
            if (current.commitStarted())
                return CancelResponse.TOO_LATE;
            if (current.cancelRequested())
                return CancelResponse.ACCEPTED;
            current.acceptCancellation(clock.instant());
            notStarted = !current.started();
            future = current.future();
            published = commitFrame(current.attempt(), false);
        }
        dispatchObservers(published);
        if (future != null)
            future.cancel(true);
        if (notStarted) {
            dispatchTerminal(current, Result.cancelled("Cancellation completed.", List.of(), List.of()));
        } else {
            ScheduledAction timer = delayScheduler.schedule(PROLONGED_CANCELLATION_DELAY,
                    () -> markCancellationProlonged(current.id()));
            boolean retained;
            synchronized (lock) {
                retained = active == current && current.cancelRequested() && !current.commitStarted();
                if (retained)
                    current.setCancellationTimer(timer);
            }
            if (!retained)
                timer.cancel();
        }
        return CancelResponse.ACCEPTED;
    }

    /**
     * Prevents new admission and either reports immediate readiness or requests active-job cancellation.
     */
    public ShutdownResponse requestShutdown() {
        Frame published;
        boolean cancel;
        boolean ready;
        synchronized (lock) {
            if (shutdownRequested)
                return ShutdownResponse.ALREADY_REQUESTED;
            shutdownRequested = true;
            ready = active == null;
            cancel = !ready && !active.commitStarted();
            published = commitFrame(ready ? frame.attempt().orElse(null) : active.attempt(), ready);
        }
        dispatchObservers(published);
        if (cancel)
            requestCancel();
        return ready ? ShutdownResponse.READY : ShutdownResponse.WAITING_FOR_JOB;
    }

    /**
     * Reopens admission after an active job settled but the later dirty-Project close prompt was cancelled.
     *
     * @return true when a ready shutdown gate was resumed; false when shutdown is absent or work is still active
     */
    public boolean resumeAfterShutdown() {
        Frame published;
        synchronized (lock) {
            if (!shutdownRequested || active != null || closed)
                return false;
            shutdownRequested = false;
            published = commitFrame(frame.attempt().orElse(null), false);
        }
        dispatchObservers(published);
        return true;
    }

    /**
     * Observes committed immutable frames on the publication adapter; one observer failure cannot block another.
     *
     * @param observer frame consumer
     * @return idempotent handle that rejects later queued callbacks
     */
    public Subscription observe(Consumer<Frame> observer) {
        Consumer<Frame> required = Objects.requireNonNull(observer, "observer");
        long id;
        Frame initial;
        synchronized (lock) {
            if (closed)
                throw new IllegalStateException("JobCoordinator is closed");
            id = nextObserverId++;
            observers.put(id, required);
            initial = frame;
        }
        publicationExecutor.execute(() -> notifyObserver(id, required, initial));
        return () -> {
            synchronized (lock) {
                observers.remove(id);
            }
        };
    }

    /**
     * Releases observers only after coordinated shutdown has settled; active work can never be abandoned silently.
     *
     * @throws IllegalStateException when a job is active or shutdown has not reached its safe boundary
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (active != null)
                throw new IllegalStateException("Cannot close JobCoordinator while a job is active");
            if (shutdownRequested && !frame.shutdownReady())
                throw new IllegalStateException("Cannot close JobCoordinator before shutdown is ready");
            if (closed)
                return;
            workerExecutor.shutdown();
            closed = true;
            observers.clear();
        }
    }

    /**
     * Executes captured worker code and converts thrown failures without allowing them to strand admission.
     */
    private <T> void runWorker(Active<T> current) {
        synchronized (lock) {
            if (active != current || current.cancelRequested())
                return;
            current.markStarted();
        }
        Result<T> result;
        try {
            result = Objects.requireNonNull(current.submission().work().run(new WorkerContext(current)),
                    "Job work returned null");
        } catch (CancellationException cancellation) {
            result = Result.cancelled("Cancellation completed.", List.of(), List.of());
        } catch (Exception failure) {
            result = Result.failed("The job failed unexpectedly.", List.of(diagnostic(
                    "JOB_UNEXPECTED_FAILURE", "The job raised an unexpected exception.", failure)));
        } catch (LinkageError failure) {
            result = Result.failed("The job failed unexpectedly.", List.of(diagnostic(
                    "JOB_LINKAGE_FAILURE", "The job could not link required runtime code.", failure)));
        }
        dispatchTerminal(current, result);
    }

    /**
     * Commits terminal state on the serialized lane before invoking completion and observers.
     */
    private <T> void dispatchTerminal(Active<T> current, Result<T> result) {
        publicationExecutor.execute(() -> finishOnPublicationLane(current, result));
    }

    /**
     * Rejects late completion, preserves accepted cancellation, and publishes one terminal attempt.
     */
    private <T> void finishOnPublicationLane(Active<T> current, Result<T> supplied) {
        Result<T> result = supplied;
        boolean cancellationWon;
        synchronized (lock) {
            if (closed || active != current) {
                recordTechnical("JOB_STALE_COMPLETION_REJECTED",
                        "A late worker completion was rejected for attempt " + current.id().value() + ".");
                return;
            }
            cancellationWon = current.cancelRequested() && !current.commitStarted();
        }
        if (!cancellationWon) {
            try {
                result = Objects.requireNonNull(current.submission().resultResolver().resolve(current.id(), result),
                        "Job result resolver returned null");
            } catch (RuntimeException | LinkageError failure) {
                result = Result.failed("The job result could not be finalized.", List.of(diagnostic(
                        "JOB_RESULT_RESOLUTION_FAILED", "The serialized result resolver failed.", failure)));
            }
        }
        Frame published;
        synchronized (lock) {
            if (closed || active != current) {
                recordTechnical("JOB_STALE_COMPLETION_REJECTED",
                        "A late worker completion was rejected for attempt " + current.id().value() + ".");
                return;
            }
            if (current.cancelRequested() && !current.commitStarted()) {
                List<Diagnostic> diagnostics = new ArrayList<>(supplied.diagnostics());
                if (supplied.lifecycle() != Lifecycle.CANCELLED) {
                    diagnostics.add(new Diagnostic("JOB_COMPLETION_AFTER_CANCEL",
                            "Worker completion was coerced to Cancelled after accepted cancellation.",
                            Optional.empty()));
                }
                result = new Result<>(Lifecycle.CANCELLED, supplied.value(), "Cancellation completed.",
                        supplied.lifecycle() == Lifecycle.CANCELLED ? supplied.effectsCommitted() : List.of(),
                        diagnostics);
            }
            current.finish(result, clock.instant());
            current.cancelTimer();
            current.submission().retryFactory().ifPresent(factory ->
                    completedRetryFactories.put(current.id(), factory));
            active = null;
            published = commitFrame(current.attempt(), shutdownRequested);
        }
        try {
            current.submission().completion().accept(current.id(), result);
        } catch (RuntimeException | LinkageError failure) {
            recordCallbackFailure("JOB_COMPLETION_CALLBACK_FAILED", failure);
        }
        notifyObservers(published);
    }

    /**
     * Publishes progress only while its attempt still owns admission.
     */
    private void report(Active<?> current, Progress progress) {
        Frame published;
        synchronized (lock) {
            if (active != current || closed) {
                recordTechnical("JOB_STALE_PROGRESS_REJECTED",
                        "A late progress callback was rejected for attempt " + current.id().value() + ".");
                return;
            }
            current.report(progress);
            published = commitFrame(current.attempt(), false);
        }
        dispatchObservers(published);
    }

    /**
     * Linearizes commit against cancellation and publishes the non-cancellable Finishing phase.
     */
    private boolean beginCommit(Active<?> current, String phase) {
        Frame published;
        synchronized (lock) {
            if (active != current || closed) {
                recordTechnical("JOB_STALE_COMMIT_REJECTED",
                        "A late commit callback was rejected for attempt " + current.id().value() + ".");
                return false;
            }
            if (current.cancelRequested())
                return false;
            current.beginCommit(requireText(phase, "phase"));
            published = commitFrame(current.attempt(), false);
        }
        dispatchObservers(published);
        return true;
    }

    /**
     * Marks cancellation prolonged only if the same attempt is still cancelling after the delay.
     */
    private void markCancellationProlonged(AttemptId id) {
        Frame published;
        synchronized (lock) {
            if (active == null || !active.id().equals(id) || !active.cancelRequested() || active.commitStarted())
                return;
            active.markCancellationProlonged();
            published = commitFrame(active.attempt(), false);
        }
        dispatchObservers(published);
    }

    /**
     * Commits the latest frame under the coordinator lock.
     */
    private Frame commitFrame(Attempt attempt, boolean shutdownReady) {
        frame = new Frame(nextSequence++, Optional.ofNullable(attempt), shutdownRequested, shutdownReady,
                technicalDiagnostics);
        return frame;
    }

    /**
     * Dispatches one coherent frame to every currently attached observer.
     */
    private void dispatchObservers(Frame published) {
        publicationExecutor.execute(() -> notifyObservers(published));
    }

    /**
     * Isolates observer failures while preserving one shared frame for every observer in this publication.
     */
    private void notifyObservers(Frame published) {
        List<Map.Entry<Long, Consumer<Frame>>> snapshot;
        synchronized (lock) {
            snapshot = List.copyOf(observers.entrySet());
        }
        for (Map.Entry<Long, Consumer<Frame>> entry : snapshot)
            notifyObserver(entry.getKey(), entry.getValue(), published);
    }

    /**
     * Rejects queued callbacks after detachment and records any observer exception separately.
     */
    private void notifyObserver(long id, Consumer<Frame> observer, Frame published) {
        synchronized (lock) {
            if (closed || observers.get(id) != observer)
                return;
        }
        try {
            observer.accept(published);
        } catch (RuntimeException | LinkageError failure) {
            recordCallbackFailure("JOB_OBSERVER_FAILED", failure);
        }
    }

    /**
     * Records callback isolation without relabelling the underlying operation.
     */
    private void recordCallbackFailure(String code, Throwable failure) {
        recordTechnical(code, failure.getClass().getName() + ": " + readableMessage(failure));
        try {
            callbackFailureSink.accept(failure);
        } catch (RuntimeException | LinkageError ignored) {
            // The diagnostic is already retained; a broken technical sink cannot escape into job publication.
        }
    }

    /**
     * Appends a technical diagnostic to the latest frame without recursively notifying observers.
     */
    private void recordTechnical(String code, String message) {
        synchronized (lock) {
            technicalDiagnostics.add(new Diagnostic(code, message, Optional.empty()));
            frame = new Frame(nextSequence++, frame.attempt(), shutdownRequested,
                    shutdownRequested && active == null, technicalDiagnostics);
        }
    }

    /**
     * Observable lifecycle from admitted work through one truthful terminal disposition.
     */
    public enum Lifecycle {
        RUNNING,
        CANCELLING,
        FINISHING,
        COMPLETED,
        COMPLETED_WITH_ISSUES,
        CANCELLED,
        FAILED;

        /**
         * @return true only after no more worker transition may change this attempt
         */
        public boolean terminal() {
            return switch (this) {
                case COMPLETED, COMPLETED_WITH_ISSUES, CANCELLED, FAILED -> true;
                case RUNNING, CANCELLING, FINISHING -> false;
            };
        }
    }

    /**
     * Operation consistency determines which immediate state may remain editable while one captured job runs.
     */
    public enum ConsistencyClass {
        PROJECT_EXCLUSIVE,
        SNAPSHOT_DERIVED
    }

    /**
     * Result of the cancellation linearization point.
     */
    public enum CancelResponse {
        ACCEPTED,
        TOO_LATE,
        NO_ACTIVE_JOB
    }

    /**
     * Result of requesting coordinated application shutdown.
     */
    public enum ShutdownResponse {
        READY,
        WAITING_FOR_JOB,
        ALREADY_REQUESTED
    }

    /**
     * Worker-facing cancellation, progress, and atomic commit linearization interface.
     */
    public interface Context {
        /**
         * @return true after cancellation was accepted and before an unsafe commit may begin
         */
        boolean cancellationRequested();

        /**
         * Publishes a truthful worker phase; stale worker reports are rejected by attempt identity.
         */
        void report(Progress progress);

        /**
         * Tries to enter a non-cancellable commit phase at the cancellation linearization point.
         *
         * @param phase truthful commit-phase label
         * @return false when accepted cancellation won first; true when later cancellation must be refused
         */
        boolean beginCommit(String phase);

        /**
         * Throws at an ordinary worker safe point after cancellation has been accepted.
         */
        default void checkCancellation() {
            if (cancellationRequested())
                throw new CancellationException("Job cancellation was accepted");
        }
    }

    /**
     * Typed worker implementation executed by the injected application worker adapter.
     */
    @FunctionalInterface
    public interface Work<T> {
        /**
         * Runs captured work and returns one truthful terminal result.
         */
        Result<T> run(Context context) throws Exception;
    }

    /**
     * Classifies a worker result against current presentation state on the serialized publication lane.
     * This is the freshness linearization point for snapshot-derived work.
     */
    @FunctionalInterface
    public interface ResultResolver<T> {
        /**
         * @param attempt accepted coordinator attempt
         * @param result  terminal worker result before current-state classification
         * @return final terminal result committed to Activity
         */
        Result<T> resolve(AttemptId attempt, Result<T> result);
    }

    /**
     * Typed terminal callback invoked on the injected serialized publication adapter.
     */
    @FunctionalInterface
    public interface Completion<T> {
        /**
         * Accepts the result only after coordinator terminal state has committed.
         */
        void accept(AttemptId attempt, Result<T> result);
    }

    /**
     * Re-captures current operation inputs when the user explicitly retries a terminal attempt.
     */
    @FunctionalInterface
    public interface RetryFactory<T> {
        /**
         * @return a fresh submission whose inputs are captured at retry time
         */
        Submission<T> recapture();
    }

    /**
     * Cancel handle returned by the delayed-status adapter.
     */
    @FunctionalInterface
    public interface ScheduledAction {
        /**
         * Prevents the delayed action when its owning attempt has already settled.
         */
        void cancel();
    }

    /**
     * Injected delay adapter used only for the five-second prolonged-cancellation status.
     */
    @FunctionalInterface
    public interface DelayScheduler {
        /**
         * Schedules one action without imposing a global job timeout.
         */
        ScheduledAction schedule(Duration delay, Runnable action);
    }

    /**
     * Observer handle whose close operation rejects any later queued callback.
     */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        /**
         * Detaches the observer idempotently.
         */
        @Override
        void close();
    }

    /**
     * Mutable state kept wholly behind the coordinator interface and operation lock.
     */
    private static final class Active<T> {
        private final AttemptId id;
        private final Submission<T> submission;
        private final Optional<AttemptId> retryOf;
        private final Instant startedAt;
        private Lifecycle lifecycle = Lifecycle.RUNNING;
        private Progress progress;
        private Instant completedAt;
        private String summary = "";
        private List<String> effectsCommitted = List.of();
        private List<Diagnostic> diagnostics = List.of();
        private boolean cancelRequested;
        private boolean commitStarted;
        private boolean cancellationProlonged;
        private boolean started;
        private ScheduledAction cancellationTimer;
        private Future<?> future;

        /**
         * Creates newly admitted Running state from one fully captured submission.
         */
        private Active(AttemptId id, Submission<T> submission, Optional<AttemptId> retryOf, Instant startedAt) {
            this.id = id;
            this.submission = submission;
            this.retryOf = retryOf;
            this.startedAt = startedAt;
            this.progress = Progress.indeterminate("Starting " + submission.operation().name(), true);
        }

        /**
         * @return attempt identity
         */
        private AttemptId id() {
            return id;
        }

        /**
         * @return captured submission
         */
        private Submission<T> submission() {
            return submission;
        }

        /**
         * @return captured operation
         */
        private Operation operation() {
            return submission.operation();
        }

        /**
         * @return whether cancellation already won
         */
        private boolean cancelRequested() {
            return cancelRequested;
        }

        /**
         * @return whether commit already won
         */
        private boolean commitStarted() {
            return commitStarted;
        }

        /**
         * @return whether the owned executor entered the worker body
         */
        private boolean started() {
            return started;
        }

        /**
         * Marks the point after which Future cancellation may interrupt worker code instead of preventing start.
         */
        private void markStarted() {
            started = true;
        }

        /**
         * Moves Running to Cancelling exactly once.
         */
        private void acceptCancellation(Instant acceptedAt) {
            cancelRequested = true;
            lifecycle = Lifecycle.CANCELLING;
            progress = Progress.indeterminate("Cancelling " + progress.phase(), false);
            Objects.requireNonNull(acceptedAt, "acceptedAt");
        }

        /**
         * Retains truthful progress while preserving an already accepted cancellation lifecycle.
         */
        private void report(Progress reported) {
            Progress value = Objects.requireNonNull(reported, "reported");
            progress = cancelRequested
                    ? new Progress(value.phase(), value.completedUnits(), value.totalUnits(), false)
                    : value;
            if (cancelRequested)
                lifecycle = Lifecycle.CANCELLING;
        }

        /**
         * Moves the attempt to its non-cancellable Finishing phase.
         */
        private void beginCommit(String phase) {
            commitStarted = true;
            lifecycle = Lifecycle.FINISHING;
            progress = Progress.indeterminate(phase, false);
        }

        /**
         * Stores the delayed cancellation handle so terminal completion can suppress it.
         */
        private void setCancellationTimer(ScheduledAction timer) {
            cancellationTimer = Objects.requireNonNull(timer, "timer");
        }

        /**
         * Retains the owned worker Future so accepted cancellation can interrupt blocking adapters explicitly.
         */
        private void setFuture(Future<?> submitted) {
            future = Objects.requireNonNull(submitted, "submitted");
        }

        /**
         * @return owned worker Future, or null before executor submission completes
         */
        private Future<?> future() {
            return future;
        }

        /**
         * Marks the still-active cancellation status after five seconds.
         */
        private void markCancellationProlonged() {
            cancellationProlonged = true;
            progress = Progress.indeterminate("Still cancelling…", false);
        }

        /**
         * Cancels only the delayed status action; it never stops worker execution.
         */
        private void cancelTimer() {
            if (cancellationTimer != null)
                cancellationTimer.cancel();
        }

        /**
         * Captures immutable terminal evidence from the accepted result.
         */
        private void finish(Result<T> result, Instant finishedAt) {
            lifecycle = result.lifecycle();
            completedAt = Objects.requireNonNull(finishedAt, "finishedAt");
            summary = result.summary();
            effectsCommitted = result.effectsCommitted();
            diagnostics = result.diagnostics();
            if (!commitStarted)
                progress = Progress.indeterminate(progress.phase(), false);
        }

        /**
         * @return immutable public attempt projection
         */
        private Attempt attempt() {
            return new Attempt(id, operation(), retryOf, lifecycle, progress, startedAt,
                    Optional.ofNullable(completedAt), summary, effectsCommitted, diagnostics,
                    cancellationProlonged, lifecycle.terminal() && submission.retryFactory().isPresent());
        }
    }

    /**
     * Worker context bound to exactly one attempt identity.
     */
    private final class WorkerContext implements Context {
        private final Active<?> current;

        /**
         * Binds worker callbacks to the attempt that received this context.
         */
        private WorkerContext(Active<?> current) {
            this.current = current;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean cancellationRequested() {
            synchronized (lock) {
                return active != current || current.cancelRequested();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void report(Progress progress) {
            JobCoordinator.this.report(current, Objects.requireNonNull(progress, "progress"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean beginCommit(String phase) {
            return JobCoordinator.this.beginCommit(current, phase);
        }
    }
}
