package com.asdasfa.jbs2bg.project;

import java.util.List;

/**
 * Outcome for an environmental or unexpected failure that prevented completion.
 */
public final class FailedOutcome extends ProjectOutcome {

    /**
     * Creates a failed outcome and preserves the latest coherent snapshot.
     *
     * @param snapshot    unchanged latest Project snapshot
     * @param diagnostics failure diagnostics in source order
     * @throws NullPointerException when snapshot, diagnostics, or a diagnostic is null
     */
    public FailedOutcome(ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics) {
        super(snapshot, diagnostics);
    }
}
