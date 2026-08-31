package com.asdasfa.jbs2bg.project;

import java.util.List;

/**
 * Outcome for an understood operation rejected by Project validation or state.
 */
public final class RejectedOutcome extends ProjectOutcome {

    /**
     * Creates a rejected outcome and preserves the latest snapshot.
     *
     * @param snapshot    unchanged latest Project snapshot
     * @param diagnostics rejection diagnostics in source order
     * @throws NullPointerException when snapshot, diagnostics, or a diagnostic is null
     */
    public RejectedOutcome(ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics) {
        super(snapshot, diagnostics);
    }
}
