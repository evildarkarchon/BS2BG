package com.asdasfa.jbs2bg.project;

/**
 * Outcome for an accepted operation that did not change observable Project state.
 */
public final class UnchangedOutcome extends ProjectOutcome {

    /**
     * Creates an unchanged outcome without diagnostics.
     *
     * @param snapshot latest coherent Project snapshot
     * @throws NullPointerException when snapshot is null
     */
    public UnchangedOutcome(ProjectSnapshot snapshot) {
        super(snapshot);
    }

    /**
     * Creates an unchanged outcome with informational or warning diagnostics.
     *
     * @param snapshot    latest coherent Project snapshot
     * @param diagnostics structured diagnostics in source order
     * @throws NullPointerException when snapshot, diagnostics, or a diagnostic is null
     */
    public UnchangedOutcome(ProjectSnapshot snapshot, java.util.List<ProjectDiagnostic> diagnostics) {
        super(snapshot, diagnostics);
    }
}
