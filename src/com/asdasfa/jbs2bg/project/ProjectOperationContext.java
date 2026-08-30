package com.asdasfa.jbs2bg.project;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/** JavaFX-independent cancellation, progress, and atomic-publication seam for synchronous Project operations. */
public interface ProjectOperationContext {

    /** @return true after cancellation has won before the operation's irreversible publication */
    boolean cancellationRequested();

    /**
     * Reports one truthful adapter phase.
     *
     * @param progress phase and optional measured units
     */
    void report(ProjectOperationProgress progress);

    /**
     * Linearizes the operation's final atomic publication against cancellation.
     *
     * @param phase truthful non-cancellable publication phase
     * @return false when cancellation won first; true when publication may proceed
     */
    boolean beginCommit(String phase);

    /** Throws at an ordinary safe point after cancellation has been accepted. */
    default void checkCancellation() {
        if (cancellationRequested())
            throw new CancellationException("Project operation cancellation was accepted");
    }

    /**
     * Returns a context for existing synchronous callers that neither cancel nor retain progress.
     *
     * @return shared non-cancellable context
     */
    static ProjectOperationContext nonCancellable() {
        return NonCancellableProjectOperationContext.INSTANCE;
    }
}

/** Package-owned stateless compatibility context kept out of the public operation interface. */
final class NonCancellableProjectOperationContext implements ProjectOperationContext {
    static final ProjectOperationContext INSTANCE = new NonCancellableProjectOperationContext();

    private NonCancellableProjectOperationContext() {
    }

    /** This compatibility path never requests cancellation. */
    @Override
    public boolean cancellationRequested() {
        return false;
    }

    /** Existing synchronous callers intentionally discard intermediate progress. */
    @Override
    public void report(ProjectOperationProgress progress) {
        Objects.requireNonNull(progress, "progress");
    }

    /** Existing synchronous callers always permit the final publication. */
    @Override
    public boolean beginCommit(String phase) {
        String required = Objects.requireNonNull(phase, "phase").trim();
        if (required.isEmpty())
            throw new IllegalArgumentException("phase must not be blank");
        return true;
    }
}
