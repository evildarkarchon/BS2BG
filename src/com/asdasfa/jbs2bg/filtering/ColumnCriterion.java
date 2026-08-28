package com.asdasfa.jbs2bg.filtering;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable filter criterion for one column: the exact cell values it hides.
 * <p>
 * This is deliberately an exclusion model, mirroring the checklist filter it
 * characterizes: a row is admitted unless its cell text is one of the hidden
 * values, so rows carrying values the criterion has never seen (for example
 * every row of a newly opened Project) stay visible by default. A criterion
 * that hides nothing is inactive.
 */
public final class ColumnCriterion {

    private final String columnId;
    private final Set<String> hiddenValues;

    private ColumnCriterion(String columnId, Collection<String> hiddenValues) {
        this.columnId = Objects.requireNonNull(columnId, "columnId");
        Set<String> copy = new LinkedHashSet<>();
        for (String value : Objects.requireNonNull(hiddenValues, "hiddenValues"))
            copy.add(Objects.requireNonNull(value, "hidden value"));
        this.hiddenValues = Collections.unmodifiableSet(copy);
    }

    /**
     * Creates a criterion that hides rows whose cell text equals any listed value.
     *
     * @param columnId ID of the column the criterion applies to
     * @param hiddenValues exact cell texts to hide; empty makes the criterion inactive
     * @return the immutable criterion
     * @throws NullPointerException when an argument or value is null
     */
    public static ColumnCriterion hiding(String columnId, Collection<String> hiddenValues) {
        return new ColumnCriterion(columnId, hiddenValues);
    }

    /** @return the ID of the column this criterion applies to */
    public String getColumnId() {
        return columnId;
    }

    /** @return the immutable set of hidden cell texts */
    public Set<String> getHiddenValues() {
        return hiddenValues;
    }

    /** @return true when at least one value is hidden */
    public boolean isActive() {
        return !hiddenValues.isEmpty();
    }

    /**
     * Decides membership for one cell.
     *
     * @param cellValue non-null cell text
     * @return true unless the value is hidden
     */
    public boolean admits(String cellValue) {
        return !hiddenValues.contains(Objects.requireNonNull(cellValue, "cellValue"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof ColumnCriterion))
            return false;
        ColumnCriterion criterion = (ColumnCriterion) other;
        return columnId.equals(criterion.columnId) && hiddenValues.equals(criterion.hiddenValues);
    }

    @Override
    public int hashCode() {
        return 31 * columnId.hashCode() + hiddenValues.hashCode();
    }

    @Override
    public String toString() {
        return "ColumnCriterion[" + columnId + " hides " + hiddenValues + "]";
    }
}
