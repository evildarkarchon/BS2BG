package com.asdasfa.jbs2bg.filtering;

import java.util.Objects;

/**
 * Immutable sort instruction for one column. Sort keys affect presentation
 * order only; they never change visible-set membership or command scope.
 */
public final class SortKey {

    private final String columnId;
    private final SortDirection direction;

    private SortKey(String columnId, SortDirection direction) {
        this.columnId = Objects.requireNonNull(columnId, "columnId");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    /**
     * @param columnId column to sort by
     * @return an ascending sort key
     * @throws NullPointerException when columnId is null
     */
    public static SortKey ascending(String columnId) {
        return new SortKey(columnId, SortDirection.ASCENDING);
    }

    /**
     * @param columnId column to sort by
     * @return a descending sort key
     * @throws NullPointerException when columnId is null
     */
    public static SortKey descending(String columnId) {
        return new SortKey(columnId, SortDirection.DESCENDING);
    }

    /** @return the column ID */
    public String getColumnId() {
        return columnId;
    }

    /** @return the sort direction */
    public SortDirection getDirection() {
        return direction;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof SortKey))
            return false;
        SortKey key = (SortKey) other;
        return columnId.equals(key.columnId) && direction == key.direction;
    }

    @Override
    public int hashCode() {
        return 31 * columnId.hashCode() + direction.hashCode();
    }

    @Override
    public String toString() {
        return "SortKey[" + columnId + " " + direction + "]";
    }
}
