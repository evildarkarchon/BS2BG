package com.asdasfa.jbs2bg.testing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * FIFO executor that makes worker execution and completion explicit in deterministic tests.
 */
public final class ManualExecutor extends AbstractExecutorService {
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private boolean shutdown;

    /**
     * Queues one worker action until the owning test advances it.
     */
    @Override
    public void execute(Runnable command) {
        tasks.addLast(Objects.requireNonNull(command, "command"));
    }

    /**
     * Prevents later test submissions without discarding queued work.
     */
    @Override
    public void shutdown() {
        shutdown = true;
    }

    /**
     * Prevents later work and returns every queued action without running it.
     */
    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> remaining = List.copyOf(tasks);
        tasks.clear();
        return remaining;
    }

    /**
     * @return whether shutdown was requested
     */
    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * @return whether shutdown was requested after the deterministic queue drained
     */
    @Override
    public boolean isTerminated() {
        return shutdown && tasks.isEmpty();
    }

    /**
     * Deterministic tests never block for executor termination.
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        return isTerminated();
    }

    /**
     * Runs the oldest queued worker action on the calling thread.
     */
    public void runNext() {
        tasks.removeFirst().run();
    }

    /**
     * Runs the oldest queued worker action on one owned platform thread.
     *
     * @return the started thread so the test can await the selected race boundary
     */
    public Thread runNextAsync() {
        Thread thread = new Thread(tasks.removeFirst(), "manual-test-worker");
        thread.start();
        return thread;
    }
}
