package com.asdasfa.jbs2bg.project;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Serializes Project operations and atomically publishes immutable snapshots.
 */
final class DefaultProjectSession implements ProjectSession {

	private final Object operationLock = new Object();
	private volatile ProjectSnapshot snapshot = ProjectSnapshot.noProject();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ProjectSnapshot getSnapshot() {
		return snapshot;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ProjectOutcome newProject() {
		synchronized (operationLock) {
			ProjectSnapshot emptyProject = ProjectSnapshot.empty();
			if (snapshot == emptyProject)
				return new UnchangedOutcome(snapshot);
			snapshot = emptyProject;
			return new ChangedOutcome(snapshot);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ProjectOutcome open(Path source) {
		Objects.requireNonNull(source, "source");
		synchronized (operationLock) {
			SourceLocation location = new SourceLocation(Optional.of(source), Optional.empty(), OptionalInt.empty(),
					OptionalInt.empty());
			return rejected(ProjectDiagnosticCodes.OPEN_UNAVAILABLE, location,
					"Opening Projects is not available in this ProjectSession implementation yet.");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ProjectOutcome save() {
		synchronized (operationLock) {
			SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("project-file"),
					OptionalInt.empty(), OptionalInt.empty());
			return rejected(ProjectDiagnosticCodes.FILE_IDENTITY_REQUIRED, location,
					"The Project has no file identity; choose a target before saving.");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ProjectOutcome saveAs(Path target) {
		Objects.requireNonNull(target, "target");
		synchronized (operationLock) {
			SourceLocation location = new SourceLocation(Optional.of(target), Optional.empty(), OptionalInt.empty(),
					OptionalInt.empty());
			return rejected(ProjectDiagnosticCodes.SAVE_UNAVAILABLE, location,
					"Saving Projects is not available in this ProjectSession implementation yet.");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ProjectOutcome apply(ProjectEdit edit) {
		Objects.requireNonNull(edit, "edit");
		synchronized (operationLock) {
			SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("project-edit"),
					OptionalInt.empty(), OptionalInt.empty());
			return rejected(ProjectDiagnosticCodes.EDIT_UNSUPPORTED, location,
					"The Project edit request type is not supported.");
		}
	}

	/**
	 * Builds a validation rejection while the operation lock pins the snapshot used
	 * by both the outcome and concurrent callers.
	 *
	 * @param code stable diagnostic code
	 * @param location structured source location
	 * @param message human-readable diagnostic message
	 * @return a rejection carrying the pinned snapshot
	 */
	private RejectedOutcome rejected(String code, SourceLocation location, String message) {
		ProjectDiagnostic diagnostic = new ProjectDiagnostic(code, DiagnosticSeverity.ERROR, location, message);
		return new RejectedOutcome(snapshot, Collections.singletonList(diagnostic));
	}
}
