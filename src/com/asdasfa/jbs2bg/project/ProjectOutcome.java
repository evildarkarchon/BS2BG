package com.asdasfa.jbs2bg.project;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Common immutable result of a ProjectSession operation.
 */
public abstract class ProjectOutcome {

    private final ProjectSnapshot snapshot;
    private final List<ProjectDiagnostic> diagnostics;

    /**
     * Creates an outcome without diagnostics.
     *
     * @param snapshot latest coherent Project snapshot
     * @throws NullPointerException when snapshot is null
     */
    ProjectOutcome(ProjectSnapshot snapshot) {
        this(snapshot, Collections.<ProjectDiagnostic>emptyList());
    }

    /**
     * Creates an outcome and defensively copies its diagnostics.
     *
     * @param snapshot    latest coherent Project snapshot
     * @param diagnostics structured diagnostics in source order
     * @throws NullPointerException when snapshot, diagnostics, or a diagnostic is null
     */
    ProjectOutcome(ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.diagnostics = ImmutableValues.copyOf(diagnostics, "diagnostics");
    }

    /**
     * Returns the latest coherent snapshot at the operation's linearization point.
     *
     * @return the operation snapshot
     */
    public final ProjectSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Returns structured diagnostics in deterministic source order.
     *
     * @return an immutable list of immutable diagnostics
     */
    public final List<ProjectDiagnostic> getDiagnostics() {
        return diagnostics;
    }
}
