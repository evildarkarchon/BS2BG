package com.asdasfa.jbs2bg.project;

import java.util.function.Supplier;

import com.asdasfa.jbs2bg.data.Settings;

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
     * <p>The session receives only a read-only view of the current Settings pair, never the Settings store, so it
     * cannot persist or reload Settings. Production passes {@code store::snapshot}.
     *
     * @param currentSettings read-only view of the current Settings pair; each session operation reads it once
     * @return a new ProjectSession
     * @throws NullPointerException when the view is null
     */
    public static ProjectSession create(Supplier<Settings.Snapshot> currentSettings) {
        return new DefaultProjectSession(currentSettings);
    }
}
