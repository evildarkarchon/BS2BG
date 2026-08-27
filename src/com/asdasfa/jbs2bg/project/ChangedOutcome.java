package com.asdasfa.jbs2bg.project;

import java.util.List;

/**
 * Outcome for an operation that atomically changed observable Project state.
 */
public final class ChangedOutcome extends ProjectOutcome {

	/**
	 * Creates a changed outcome without diagnostics.
	 *
	 * @param snapshot latest coherent Project snapshot
	 * @throws NullPointerException when snapshot is null
	 */
	public ChangedOutcome(ProjectSnapshot snapshot) {
		super(snapshot);
	}

	/**
	 * Creates a changed outcome with diagnostics such as recovery warnings.
	 *
	 * @param snapshot latest coherent Project snapshot
	 * @param diagnostics structured diagnostics in source order
	 * @throws NullPointerException when snapshot, diagnostics, or a diagnostic is null
	 */
	public ChangedOutcome(ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics) {
		super(snapshot, diagnostics);
	}
}
