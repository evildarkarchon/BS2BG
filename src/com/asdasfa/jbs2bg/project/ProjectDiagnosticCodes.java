package com.asdasfa.jbs2bg.project;

/**
 * Stable machine-readable codes emitted by foundational ProjectSession behavior.
 */
public final class ProjectDiagnosticCodes {

	/** An edit request type is not recognized by this ProjectSession. */
	public static final String EDIT_UNSUPPORTED = "PROJECT_EDIT_UNSUPPORTED";
	/** Project loading is not yet available through the foundational session. */
	public static final String OPEN_UNAVAILABLE = "PROJECT_OPEN_UNAVAILABLE";
	/** Project saving is not yet available through the foundational session. */
	public static final String SAVE_UNAVAILABLE = "PROJECT_SAVE_UNAVAILABLE";
	/** Save requires a successfully adopted Project file identity. */
	public static final String FILE_IDENTITY_REQUIRED = "PROJECT_FILE_IDENTITY_REQUIRED";

	private ProjectDiagnosticCodes() {
	}
}
