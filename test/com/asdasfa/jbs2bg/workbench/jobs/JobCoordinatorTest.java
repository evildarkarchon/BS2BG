package com.asdasfa.jbs2bg.workbench.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.testing.ManualExecutor;

class JobCoordinatorTest {

    private static final Instant START = Instant.parse("2026-08-29T20:00:00Z");

    /**
     * Creates a deterministic coordinator whose adapters never create threads or wait on wall time.
     */
    private static JobCoordinator coordinator(ManualExecutor worker) {
        return coordinator(worker, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
    }

    /**
     * Creates a deterministic coordinator with an explicit callback-failure collection seam.
     */
    private static JobCoordinator coordinator(ManualExecutor worker, List<Throwable> callbackFailures) {
        return coordinator(worker, callbackFailures::add);
    }

    /**
     * Creates a deterministic coordinator whose failure sink is supplied by the calling test.
     */
    private static JobCoordinator coordinator(ManualExecutor worker,
                                              java.util.function.Consumer<Throwable> failureSink) {
        return coordinator(worker, failureSink, (delay, task) -> () -> {
            // This slice never advances delayed cancellation status.
        });
    }

    /**
     * Creates a deterministic coordinator with caller-supplied failure and delay adapters.
     */
    private static JobCoordinator coordinator(ManualExecutor worker,
                                              java.util.function.Consumer<Throwable> failureSink, JobCoordinator.DelayScheduler delayScheduler) {
        return new JobCoordinator(worker, Runnable::run, Clock.fixed(START, ZoneOffset.UTC),
                delayScheduler, failureSink);
    }

    /**
     * One admitted job owns application-wide progress until its committed result becomes terminal.
     */
    @Test
    void oneJobPublishesTruthfulProgressAndRefusesCompetingAdmission() {
        ManualExecutor worker = new ManualExecutor();
        List<JobCoordinator.Frame> observed = new ArrayList<>();
        JobCoordinator coordinator = coordinator(worker);
        coordinator.observe(observed::add);
        List<JobCoordinator.Result<String>> completions = new ArrayList<>();
        JobCoordinator.Operation operation = new JobCoordinator.Operation(
                "Open Project", List.of("captured.jbs2bg"), List.of(), Optional.of("Project content version 7"));

        JobCoordinator.Admission admitted = coordinator.submit(new JobCoordinator.Submission<>(operation,
                context -> {
                    context.report(JobCoordinator.Progress.determinate("Reading Project", 2, 5));
                    assertTrue(context.beginCommit("Publishing Project"));
                    return JobCoordinator.Result.completed("opened", "Project opened",
                            List.of("Project published"), List.of());
                }, (attempt, result) -> completions.add(result), Optional.empty()));

        assertTrue(admitted.admitted());
        assertEquals(JobCoordinator.Lifecycle.RUNNING,
                coordinator.frame().attempt().orElseThrow().lifecycle());
        JobCoordinator.Admission competing = coordinator.submit(new JobCoordinator.Submission<>(
                new JobCoordinator.Operation("Save Project", List.of(), List.of("other.jbs2bg"), Optional.empty()),
                context -> JobCoordinator.Result.completed("saved", "Project saved", List.of(),
                        List.of()), (attempt, result) -> {
            throw new AssertionError("Rejected work must never complete");
        }, Optional.empty()));

        assertFalse(competing.admitted());
        assertEquals("Open Project", competing.activeOperation().orElseThrow());

        worker.runNext();

        JobCoordinator.Attempt terminal = coordinator.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED, terminal.lifecycle());
        assertEquals("Publishing Project", terminal.progress().phase());
        assertFalse(terminal.progress().cancellable());
        assertEquals(List.of("Project published"), terminal.effectsCommitted());
        assertEquals(1, completions.size());
        assertEquals("opened", completions.getFirst().value().orElseThrow());
        assertTrue(observed.stream().anyMatch(frame -> frame.attempt().stream()
                .anyMatch(attempt -> attempt.progress().percentage().orElse(-1) == 40)));
    }

    /** Serialized result resolution classifies freshness before terminal Activity and completion observe the result. */
    @Test
    void resultResolverFinalizesDispositionBeforeCompletion() {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        List<JobCoordinator.Result<String>> completed = new ArrayList<>();
        coordinator.submit(new JobCoordinator.Submission<>(new JobCoordinator.Operation(
                "Generate Output", List.of(), List.of(), Optional.of("captured basis"),
                JobCoordinator.ConsistencyClass.SNAPSHOT_DERIVED),
                context -> JobCoordinator.Result.completed("candidate", "Worker completed", List.of(), List.of()),
                (attempt, result) -> completed.add(result), Optional.empty(),
                (attempt, result) -> JobCoordinator.Result.completedWithIssues(
                        result.value().orElseThrow(), "Project changed—Generate again.", List.of(),
                        List.of(new JobCoordinator.Diagnostic("STALE_RESULT", "Captured basis is stale.",
                                Optional.empty())))));

        worker.runNext();

        JobCoordinator.Attempt terminal = coordinator.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES, terminal.lifecycle());
        assertEquals("Project changed—Generate again.", terminal.summary());
        assertEquals(List.of("STALE_RESULT"), terminal.diagnostics().stream()
                .map(JobCoordinator.Diagnostic::code).toList());
        assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES, completed.getFirst().lifecycle());
    }

    /** A broken serialized result resolver becomes a failed job without stranding global admission. */
    @Test
    void resultResolverFailureIsIsolatedAsTerminalDiagnostic() {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        coordinator.submit(new JobCoordinator.Submission<>(new JobCoordinator.Operation(
                "Generate Output", List.of(), List.of(), Optional.empty()),
                context -> JobCoordinator.Result.completed("candidate", "Worker completed", List.of(), List.of()),
                (attempt, result) -> {
                    // Terminal state and diagnostics are asserted through the public coordinator frame.
                }, Optional.empty(), (attempt, result) -> {
            throw new IllegalStateException("resolver failed");
        }));

        worker.runNext();

        JobCoordinator.Attempt terminal = coordinator.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.FAILED, terminal.lifecycle());
        assertEquals(List.of("JOB_RESULT_RESOLUTION_FAILED"), terminal.diagnostics().stream()
                .map(JobCoordinator.Diagnostic::code).toList());
        assertFalse(coordinator.frame().active());
    }

    /**
     * Accepted cancellation wins before commit, remains idempotent, and cannot leak a completed effect.
     */
    @Test
    void acceptedCancellationPreventsLaterCommitAndSettlesOnlyAfterWorkerAcknowledgement() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobCoordinator.Admission admission = coordinator.submit(new JobCoordinator.Submission<>(
                new JobCoordinator.Operation("Open Project", List.of("slow.jbs2bg"), List.of(), Optional.empty()),
                context -> {
                    context.report(JobCoordinator.Progress.indeterminate("Parsing Project", true));
                    entered.countDown();
                    while (release.getCount() > 0) {
                        try {
                            release.await();
                        } catch (InterruptedException ignored) {
                            // This race exposes a safe point only when the test releases it explicitly.
                        }
                    }
                    if (!context.beginCommit("Publishing Project"))
                        return JobCoordinator.Result.cancelled("Open cancelled", List.of(), List.of());
                    return JobCoordinator.Result.completed("opened", "Project opened", List.of("Project published"),
                            List.of());
                }, (attempt, result) -> {
            // Terminal state is asserted through the coordinator interface below.
        }, Optional.empty()));
        Thread workerThread = worker.runNextAsync();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        assertEquals(JobCoordinator.CancelResponse.ACCEPTED, coordinator.requestCancel());
        assertEquals(JobCoordinator.CancelResponse.ACCEPTED, coordinator.requestCancel());
        assertEquals(JobCoordinator.Lifecycle.CANCELLING,
                coordinator.frame().attempt().orElseThrow().lifecycle());
        assertTrue(coordinator.frame().active());
        release.countDown();
        workerThread.join(TimeUnit.SECONDS.toMillis(5));

        JobCoordinator.Attempt cancelled = coordinator.frame().attempt().orElseThrow();
        assertEquals(JobCoordinator.Lifecycle.CANCELLED, cancelled.lifecycle());
        assertTrue(cancelled.effectsCommitted().isEmpty());
        assertFalse(coordinator.frame().active());
        assertEquals(JobCoordinator.CancelResponse.NO_ACTIVE_JOB, coordinator.requestCancel());
        assertTrue(admission.attempt().isPresent());
    }

    /**
     * Commit winning first enters Finishing, disables cancellation, and preserves the worker's real completion.
     */
    @Test
    void commitPhaseRefusesLateCancellation() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        CountDownLatch committing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        coordinator.submit(new JobCoordinator.Submission<>(new JobCoordinator.Operation(
                "Open Project", List.of("ready.jbs2bg"), List.of(), Optional.empty()), context -> {
            assertTrue(context.beginCommit("Publishing Project"));
            committing.countDown();
            release.await();
            return JobCoordinator.Result.completed("opened", "Project opened",
                    List.of("Project published"), List.of());
        }, (attempt, result) -> {
            // Terminal state is asserted through the coordinator interface below.
        }, Optional.empty()));
        Thread workerThread = worker.runNextAsync();
        assertTrue(committing.await(5, TimeUnit.SECONDS));

        assertEquals(JobCoordinator.CancelResponse.TOO_LATE, coordinator.requestCancel());
        assertEquals(JobCoordinator.Lifecycle.FINISHING,
                coordinator.frame().attempt().orElseThrow().lifecycle());
        release.countDown();
        workerThread.join(TimeUnit.SECONDS.toMillis(5));

        assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                coordinator.frame().attempt().orElseThrow().lifecycle());
        assertEquals(List.of("Project published"),
                coordinator.frame().attempt().orElseThrow().effectsCommitted());
    }

    /**
     * Retry recaptures current inputs and the coordinator stamps linkage to the selected terminal attempt.
     */
    @Test
    void retryRecapturesInputsAndLinksAttemptsWithoutCallerSuppliedIdentity() {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        AtomicReference<String> selectedSource = new AtomicReference<>("first.jbs2bg");
        List<String> completedValues = new ArrayList<>();
        JobCoordinator.Operation firstOperation = new JobCoordinator.Operation("Open Project",
                List.of(selectedSource.get()), List.of(), Optional.empty());
        JobCoordinator.Submission<String> first = new JobCoordinator.Submission<>(firstOperation,
                context -> JobCoordinator.Result.failed("Open failed", List.of()),
                (attempt, result) -> {
                    // Failure evidence is asserted through the terminal attempt below.
                }, Optional.of(() -> {
            String recaptured = selectedSource.get();
            return new JobCoordinator.Submission<>(new JobCoordinator.Operation("Open Project",
                    List.of(recaptured), List.of(), Optional.empty()),
                    context -> JobCoordinator.Result.completed(recaptured, "Project opened",
                            List.of("Project published"), List.of()),
                    (attempt, result) -> completedValues.add(result.value().orElseThrow()),
                    Optional.empty());
        }));

        JobCoordinator.AttemptId firstId = coordinator.submit(first).attempt().orElseThrow();
        worker.runNext();
        selectedSource.set("retry.jbs2bg");

        JobCoordinator.Admission retried = coordinator.retry(firstId);
        worker.runNext();

        assertTrue(retried.admitted());
        JobCoordinator.Attempt terminal = coordinator.frame().attempt().orElseThrow();
        assertEquals(Optional.of(firstId), terminal.retryOf());
        assertEquals(List.of("retry.jbs2bg"), terminal.operation().sourceLabels());
        assertEquals(List.of("retry.jbs2bg"), completedValues);
    }

    /**
     * A successful terminal attempt neither advertises nor retains a retry factory that could replay committed work.
     */
    @Test
    void completedAttemptDoesNotExposeOrRetainItsRetryFactory() {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        AtomicReference<Boolean> recaptured = new AtomicReference<>(Boolean.FALSE);
        JobCoordinator.Submission<String> submission = new JobCoordinator.Submission<>(
                new JobCoordinator.Operation("Save As", List.of(), List.of("saved.jbs2bg"), Optional.empty()),
                context -> JobCoordinator.Result.completed("saved", "Project saved",
                        List.of("Project file replaced"), List.of()),
                (attempt, result) -> {
                    // The immutable terminal attempt below is the observable contract under test.
                }, Optional.of(() -> {
            recaptured.set(Boolean.TRUE);
            return new JobCoordinator.Submission<>(
                    new JobCoordinator.Operation("Save As", List.of(), List.of("saved.jbs2bg"), Optional.empty()),
                    context -> JobCoordinator.Result.completed("saved again", "Project saved again",
                            List.of("Project file replaced"), List.of()),
                    (attempt, result) -> {
                        // A successful first attempt must prevent this submission from being admitted.
                    }, Optional.empty());
        }));

        JobCoordinator.AttemptId id = coordinator.submit(submission).attempt().orElseThrow();
        worker.runNext();
        JobCoordinator.Attempt completed = coordinator.frame().attempt().orElseThrow();
        JobCoordinator.Admission retry = coordinator.retry(id);

        assertFalse(completed.retryAvailable());
        assertFalse(retry.admitted());
        assertEquals(Boolean.FALSE, recaptured.get());
    }

    /**
     * Completed-with-issues work retains Retry so a recovered or stale operation can be attempted again.
     */
    @Test
    void completedWithIssuesAttemptRemainsRetryable() {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        JobCoordinator.Submission<String> submission = new JobCoordinator.Submission<>(
                new JobCoordinator.Operation("Open Project", List.of("recovered.jbs2bg"), List.of(), Optional.empty()),
                context -> JobCoordinator.Result.completedWithIssues("recovered", "Project recovered", List.of(),
                        List.of(new JobCoordinator.Diagnostic("RECOVERED", "Project needs attention",
                                Optional.empty()))),
                (attempt, result) -> {
                    // Retry capability is asserted from the terminal attempt and admission below.
                }, Optional.of(() -> new JobCoordinator.Submission<>(
                        new JobCoordinator.Operation("Open Project", List.of("recovered.jbs2bg"), List.of(),
                                Optional.empty()),
                        context -> JobCoordinator.Result.failed("Still recovered", List.of()),
                        (attempt, result) -> {
                            // The linked retry only needs to prove admission for this contract.
                        }, Optional.empty())));

        JobCoordinator.AttemptId id = coordinator.submit(submission).attempt().orElseThrow();
        worker.runNext();
        JobCoordinator.Attempt completed = coordinator.frame().attempt().orElseThrow();

        assertTrue(completed.retryAvailable());
        assertTrue(coordinator.retry(id).admitted());
    }

    /**
     * Retry rechecks dynamic availability after recapture so a state change in that window prevents admission.
     */
    @Test
    void retryRechecksAvailabilityAfterRecapturingInputs() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        CountDownLatch recaptureStarted = new CountDownLatch(1);
        CountDownLatch releaseRecapture = new CountDownLatch(1);
        AtomicBoolean unavailable = new AtomicBoolean();
        AtomicInteger availabilityChecks = new AtomicInteger();
        JobCoordinator.RetryFactory<String> retryFactory = new JobCoordinator.RetryFactory<>() {
            /** Blocks after the first availability check so the test can change dependent state. */
            @Override
            public JobCoordinator.Submission<String> recapture() {
                recaptureStarted.countDown();
                try {
                    if (!releaseRecapture.await(5, TimeUnit.SECONDS))
                        throw new AssertionError("Retry recapture was not released");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Retry recapture was interrupted", exception);
                }
                return new JobCoordinator.Submission<>(
                        new JobCoordinator.Operation("Open Project", List.of("retry.jbs2bg"), List.of(),
                                Optional.empty()),
                        context -> JobCoordinator.Result.completed("opened", "Project opened",
                                List.of("Project published"), List.of()),
                        (attempt, result) -> {
                            // Post-recapture unavailability must keep this submission out of the worker queue.
                        }, Optional.empty());
            }

            /** Counts both required checks and follows the state changed while recapture is blocked. */
            @Override
            public Optional<String> unavailableReason() {
                availabilityChecks.incrementAndGet();
                return unavailable.get() ? Optional.of("Current Project became dirty") : Optional.empty();
            }
        };
        JobCoordinator.Submission<String> first = new JobCoordinator.Submission<>(
                new JobCoordinator.Operation("Open Project", List.of("first.jbs2bg"), List.of(), Optional.empty()),
                context -> JobCoordinator.Result.failed("Open failed", List.of()),
                (attempt, result) -> {
                    // The failed terminal attempt is the retry source below.
                }, Optional.of(retryFactory));
        JobCoordinator.AttemptId failedId = coordinator.submit(first).attempt().orElseThrow();
        worker.runNext();
        FutureTask<JobCoordinator.Admission> retryTask = new FutureTask<>(() -> coordinator.retry(failedId));
        Thread retryThread = Thread.ofPlatform().name("retry-availability-race").start(retryTask);
        assertTrue(recaptureStarted.await(5, TimeUnit.SECONDS));

        unavailable.set(true);
        releaseRecapture.countDown();
        JobCoordinator.Admission admission;
        try {
            admission = retryTask.get(5, TimeUnit.SECONDS);
        } finally {
            retryThread.join(5_000);
        }

        assertFalse(admission.admitted());
        assertEquals(2, availabilityChecks.get());
        assertFalse(coordinator.frame().active());
        assertTrue(coordinator.frame().technicalDiagnostics().isEmpty());
    }

    /**
     * A throwing observer is isolated and cannot prevent a healthy observer from receiving terminal state.
     */
    @Test
    void observerFailureIsolatedFromOtherObserversAndJobDisposition() {
        ManualExecutor worker = new ManualExecutor();
        List<Throwable> callbackFailures = new ArrayList<>();
        JobCoordinator coordinator = coordinator(worker, callbackFailures);
        List<JobCoordinator.Frame> healthyFrames = new ArrayList<>();
        coordinator.observe(frame -> {
            throw new IllegalStateException("broken observer");
        });
        coordinator.observe(healthyFrames::add);
        coordinator.submit(new JobCoordinator.Submission<>(new JobCoordinator.Operation(
                "Open Project", List.of("source.jbs2bg"), List.of(), Optional.empty()),
                context -> JobCoordinator.Result.completed("opened", "Project opened",
                        List.of("Project published"), List.of()),
                (attempt, result) -> {
                    // The observer behavior is the subject of this test.
                }, Optional.empty()));

        worker.runNext();

        assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                coordinator.frame().attempt().orElseThrow().lifecycle());
        assertTrue(healthyFrames.stream().anyMatch(frame -> frame.attempt().stream()
                .anyMatch(attempt -> attempt.lifecycle() == JobCoordinator.Lifecycle.COMPLETED)));
        assertFalse(callbackFailures.isEmpty());
        assertTrue(coordinator.frame().technicalDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JOB_OBSERVER_FAILED")));
    }

    /**
     * Attempt-bound progress is rejected after terminal completion and cannot overwrite newer state.
     */
    @Test
    void lateProgressCallbackIsRejectedAfterTerminalCompletion() {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        AtomicReference<JobCoordinator.Context> captured = new AtomicReference<>();
        coordinator.submit(new JobCoordinator.Submission<>(new JobCoordinator.Operation(
                "Open Project", List.of("source.jbs2bg"), List.of(), Optional.empty()), context -> {
            captured.set(context);
            return JobCoordinator.Result.completed("opened", "Project opened",
                    List.of("Project published"), List.of());
        }, (attempt, result) -> {
            // The stale callback is invoked explicitly after this completion.
        }, Optional.empty()));
        worker.runNext();
        JobCoordinator.Attempt terminal = coordinator.frame().attempt().orElseThrow();

        captured.get().report(JobCoordinator.Progress.indeterminate("Late phase", true));
        assertFalse(captured.get().beginCommit("Late commit"));

        assertEquals(terminal, coordinator.frame().attempt().orElseThrow());
        assertTrue(coordinator.frame().technicalDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JOB_STALE_PROGRESS_REJECTED")));
        assertTrue(coordinator.frame().technicalDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("JOB_STALE_COMMIT_REJECTED")));
    }

    /**
     * Shutdown rejects new admission and becomes ready only after accepted cancellation settles.
     */
    @Test
    void shutdownWaitsForActiveWorkerAcknowledgementWithoutAbandoningWork() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        coordinator.submit(new JobCoordinator.Submission<>(new JobCoordinator.Operation(
                "Open Project", List.of("slow.jbs2bg"), List.of(), Optional.empty()), context -> {
            entered.countDown();
            release.await();
            context.checkCancellation();
            return JobCoordinator.Result.completed("opened", "Project opened",
                    List.of("Project published"), List.of());
        }, (attempt, result) -> {
            // Shutdown readiness is asserted through the published frame.
        }, Optional.empty()));
        Thread workerThread = worker.runNextAsync();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        assertEquals(JobCoordinator.ShutdownResponse.WAITING_FOR_JOB, coordinator.requestShutdown());
        assertFalse(coordinator.frame().shutdownReady());
        JobCoordinator.Admission rejected = coordinator.submit(new JobCoordinator.Submission<>(
                new JobCoordinator.Operation("Save Project", List.of(), List.of("target.jbs2bg"), Optional.empty()),
                context -> JobCoordinator.Result.completed("saved", "Project saved", List.of(), List.of()),
                (attempt, result) -> {
                    throw new AssertionError("Shutdown must reject new admission");
                }, Optional.empty()));
        assertFalse(rejected.admitted());
        release.countDown();
        workerThread.join(TimeUnit.SECONDS.toMillis(5));

        assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                coordinator.frame().attempt().orElseThrow().lifecycle());
        assertTrue(coordinator.frame().shutdownReady());
        coordinator.close();
    }

    /**
     * Repeated cancellation schedules one watchdog and truthfully reports prolonged cancellation when fired.
     */
    @Test
    void cancellationWatchdogPublishesStillCancellingWithoutCompletingTheWorker() throws Exception {
        ManualExecutor worker = new ManualExecutor();
        ManualDelayScheduler delays = new ManualDelayScheduler();
        JobCoordinator coordinator = coordinator(worker, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        }, delays);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        coordinator.submit(new JobCoordinator.Submission<>(new JobCoordinator.Operation(
                "Open Project", List.of("slow.jbs2bg"), List.of(), Optional.empty()), context -> {
            entered.countDown();
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // This worker acknowledges cancellation only at the explicit release safe point.
                }
            }
            context.checkCancellation();
            return JobCoordinator.Result.completed("opened", "Project opened",
                    List.of("Project published"), List.of());
        }, (attempt, result) -> {
            // Watchdog state is asserted before terminal completion below.
        }, Optional.empty()));
        Thread workerThread = worker.runNextAsync();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        assertEquals(JobCoordinator.CancelResponse.ACCEPTED, coordinator.requestCancel());
        assertEquals(JobCoordinator.CancelResponse.ACCEPTED, coordinator.requestCancel());
        assertEquals(1, delays.scheduledCount());
        delays.fire();

        JobCoordinator.Attempt cancelling = coordinator.frame().attempt().orElseThrow();
        assertTrue(cancelling.cancellationProlonged());
        assertEquals("Still cancelling…", cancelling.progress().phase());
        assertTrue(coordinator.frame().active());
        release.countDown();
        workerThread.join(TimeUnit.SECONDS.toMillis(5));
        assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                coordinator.frame().attempt().orElseThrow().lifecycle());
    }

    /**
     * A cancelled later dirty-close prompt can reopen admission after the active job settled.
     */
    @Test
    void readyShutdownGateCanResumeAdmission() {
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator coordinator = coordinator(worker);

        assertEquals(JobCoordinator.ShutdownResponse.READY, coordinator.requestShutdown());
        assertTrue(coordinator.frame().shutdownReady());
        assertTrue(coordinator.resumeAfterShutdown());
        assertFalse(coordinator.frame().shutdownRequested());
        JobCoordinator.Admission admission = coordinator.submit(new JobCoordinator.Submission<>(
                new JobCoordinator.Operation("Open Project", List.of("resumed.jbs2bg"), List.of(), Optional.empty()),
                context -> JobCoordinator.Result.completed("opened", "Project opened",
                        List.of("Project published"), List.of()),
                (attempt, result) -> {
                    // Admission after resume is the behavior under test.
                }, Optional.empty()));

        assertTrue(admission.admitted());
        worker.runNext();
    }

    /**
     * Single-action delay adapter that exposes scheduling and firing as deterministic test operations.
     */
    private static final class ManualDelayScheduler implements JobCoordinator.DelayScheduler {
        private Runnable action;
        private boolean cancelled;
        private int scheduledCount;

        /**
         * Stores the attempt-scoped watchdog without consulting wall time.
         */
        @Override
        public JobCoordinator.ScheduledAction schedule(java.time.Duration delay, Runnable task) {
            assertEquals(java.time.Duration.ofSeconds(5), delay);
            action = Objects.requireNonNull(task, "task");
            scheduledCount++;
            return () -> cancelled = true;
        }

        /**
         * @return number of watchdogs scheduled
         */
        private int scheduledCount() {
            return scheduledCount;
        }

        /**
         * Runs the stored watchdog unless terminal completion cancelled it.
         */
        private void fire() {
            if (!cancelled)
                action.run();
        }
    }

}
