package com.asdasfa.jbs2bg.project;

import java.util.Objects;

/**
 * Opaque session-scoped identity of the Project content captured by one immutable snapshot.
 * Callers may retain and compare tokens for freshness but cannot construct or order them.
 */
public final class ProjectContentVersion {

    private final Object sessionScope;
    private final long sequence;

    /**
     * Creates one token for a session-owned scope and transition sequence.
     */
    private ProjectContentVersion(Object sessionScope, long sequence) {
        this.sessionScope = Objects.requireNonNull(sessionScope, "sessionScope");
        if (sequence < 0)
            throw new IllegalArgumentException("sequence must not be negative");
        this.sequence = sequence;
    }

    /**
     * @return initial token for a newly created ProjectSession
     */
    static ProjectContentVersion initial(Object sessionScope) {
        return new ProjectContentVersion(sessionScope, 0);
    }

    /**
     * @return detached token used only by snapshots assembled outside a ProjectSession
     */
    static ProjectContentVersion detached() {
        return initial(new Object());
    }

    /**
     * @return next token in the same opaque session scope
     */
    ProjectContentVersion next() {
        return new ProjectContentVersion(sessionScope, Math.addExact(sequence, 1));
    }

    /**
     * Content versions compare only by their hidden session scope and transition identity.
     */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ProjectContentVersion version
                && sessionScope == version.sessionScope && sequence == version.sequence;
    }

    /**
     * Hashing preserves equality without exposing an orderable version number.
     */
    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(sessionScope) + Long.hashCode(sequence);
    }

    /**
     * Returns a diagnostic label; callers must not parse it or use it for freshness decisions.
     */
    @Override
    public String toString() {
        return "Project content version " + sequence + "@"
                + Integer.toUnsignedString(System.identityHashCode(sessionScope), 16);
    }
}
