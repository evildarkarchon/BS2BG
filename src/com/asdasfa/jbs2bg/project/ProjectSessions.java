package com.asdasfa.jbs2bg.project;

/**
 * Creates production ProjectSession instances without exposing their
 * implementation.
 */
public final class ProjectSessions {

	private ProjectSessions() {
	}

	/**
	 * Creates a thread-safe session. Callers establish its first active Project with
	 * {@link ProjectSession#newProject()} or {@link ProjectSession#open(java.nio.file.Path)}.
	 *
	 * @return a new ProjectSession
	 */
	public static ProjectSession create() {
		return new DefaultProjectSession();
	}
}
