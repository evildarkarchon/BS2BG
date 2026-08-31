package com.asdasfa.jbs2bg.filtering;

import java.util.Objects;
import java.util.function.Function;

/**
 * One filterable, sortable column of a logical table: a stable column ID plus
 * the function that derives a row's cell text. Cell text is the only value
 * criteria and sort keys ever see, so the seam never depends on how a JavaFX
 * column renders the row.
 *
 * @param <T> row type
 */
public final class FilterColumn<T> {

    private final String id;
    private final Function<? super T, String> cellValue;

    private FilterColumn(String id, Function<? super T, String> cellValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.cellValue = Objects.requireNonNull(cellValue, "cellValue");
        if (id.trim().isEmpty())
            throw new IllegalArgumentException("column id must not be blank");
    }

    /**
     * Defines a column.
     *
     * @param id        stable column ID, unique within one view
     * @param cellValue derives the cell text of a row; may return null
     * @param <T>       row type
     * @return the immutable column definition
     * @throws NullPointerException     when an argument is null
     * @throws IllegalArgumentException when the ID is blank
     */
    public static <T> FilterColumn<T> of(String id, Function<? super T, String> cellValue) {
        return new FilterColumn<>(id, cellValue);
    }

    /**
     * @return the stable column ID
     */
    public String getId() {
        return id;
    }

    /**
     * Derives the cell text used for filtering and sorting. A null cell value is
     * normalized to the empty string so that criteria and comparisons never see
     * null.
     *
     * @param row row to read
     * @return the non-null cell text
     * @throws NullPointerException when row is null
     */
    public String cellValueOf(T row) {
        String value = cellValue.apply(Objects.requireNonNull(row, "row"));
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "FilterColumn[" + id + "]";
    }
}
