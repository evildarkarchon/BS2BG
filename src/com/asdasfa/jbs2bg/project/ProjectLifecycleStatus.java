package com.asdasfa.jbs2bg.project;

/**
 * Stable lifecycle classification carried by each Project snapshot.
 */
public enum ProjectLifecycleStatus {
    /**
     * A newly created session before New Project or Open establishes active state.
     */
    NO_PROJECT,
    /**
     * A Project that has not adopted a file identity.
     */
    UNTITLED,
    /**
     * A Project that successfully opened or saved to its file identity.
     */
    FILE_BACKED,
    /**
     * A file-backed Project recovered with omitted unresolved assignments.
     */
    RECOVERED
}
