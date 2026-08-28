package com.asdasfa.jbs2bg.filtering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Frozen visible set of a {@link FilteredView}: the rows admitted by every
 * active criterion, in presentation order, together with their logical
 * identities.
 * <p>
 * This is the complete scope a bulk command operates on. It is captured before
 * the command's single Project edit and is structurally immutable (the rows
 * themselves are the caller's immutable values), so rendering the edit's
 * outcome (which replaces the view's rows) or a later filter change cannot
 * alter what the command applied to.
 *
 * @param <T> row type
 * @param <K> identity type with value equality
 */
public final class VisibleSet<T, K> {

    private final List<T> rows;
    private final List<K> identities;
    private final Set<K> identitySet;

    /**
     * Captures parallel row and identity lists; only the view creates instances.
     *
     * @param rows visible rows in presentation order
     * @param identities identity of each row, same order
     */
    VisibleSet(List<T> rows, List<K> identities) {
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.identities = Collections.unmodifiableList(new ArrayList<>(identities));
        this.identitySet = Collections.unmodifiableSet(new HashSet<>(identities));
    }

    /** @return the immutable visible rows in presentation order */
    public List<T> getRows() {
        return rows;
    }

    /** @return the immutable identities of the visible rows, in presentation order */
    public List<K> getIdentities() {
        return identities;
    }

    /**
     * @param identity candidate identity
     * @return true when a visible row has this logical identity
     * @throws NullPointerException when identity is null
     */
    public boolean contains(K identity) {
        return identitySet.contains(Objects.requireNonNull(identity, "identity"));
    }

    /** @return number of visible rows */
    public int size() {
        return rows.size();
    }

    /** @return true when no row is visible */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public String toString() {
        return "VisibleSet" + identities;
    }
}
