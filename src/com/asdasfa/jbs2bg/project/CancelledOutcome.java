package com.asdasfa.jbs2bg.project;

import java.util.List;

/**
 * Outcome for an operation stopped at a safe point without publishing an unsafe effect.
 */
public final class CancelledOutcome extends ProjectOutcome {

    /**
     * Creates a cancelled outcome without structured diagnostics.
     *
     * @param snapshot latest coherent Project snapshot, including any earlier safe effects
     */
    public CancelledOutcome(ProjectSnapshot snapshot) {
        super(snapshot);
    }

    /**
     * Creates a cancelled outcome retaining diagnostics and effects already committed before cancellation.
     *
     * @param snapshot    latest coherent Project snapshot, including any earlier safe effects
     * @param diagnostics structured diagnostics produced before cancellation
     */
    public CancelledOutcome(ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics) {
        super(snapshot, diagnostics);
    }
}
