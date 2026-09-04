package com.asdasfa.jbs2bg.filtering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Application-owned logical model of one filterable, sortable table with a
 * single logical selection. It holds the retained view state (column criteria,
 * sort order, selected identity) independently of the rows, so replacing the
 * rows after a Project edit, New, or Open keeps the filter and sort choices and
 * re-resolves the selection by identity.
 * <p>
 * Invariants:
 * <ul>
 * <li>Membership is the AND of every active {@link ColumnCriterion}; sorting
 * never changes membership.</li>
 * <li>Rows are keyed by logical identity; two source rows with the same identity
 * are rejected.</li>
 * <li>The selection is always a member of the visible set. Any change that
 * hides or removes the selected row drops the selection; it is not restored
 * later.</li>
 * <li>{@link #visibleSet()} returns an immutable copy: the scope a bulk command
 * freezes before its one Project edit.</li>
 * </ul>
 * Not thread-safe; intended for single-threaded presentation use.
 *
 * @param <T> row type
 * @param <K> identity type with value equality
 */
public final class FilteredView<T, K> {

    private final Map<String, FilterColumn<T>> columns;
    private final Function<? super T, ? extends K> identity;
    private final Map<String, ColumnCriterion> criteria = new LinkedHashMap<>();
    private List<SortKey> sortOrder = Collections.emptyList();
    private List<T> rows = Collections.emptyList();
    private K selection;

    /**
     * Creates an empty view.
     *
     * @param columns  the filterable columns, in table order; IDs must be unique
     * @param identity derives the logical identity of a row
     * @throws NullPointerException     when an argument or column is null
     * @throws IllegalArgumentException when two columns share an ID
     */
    public FilteredView(List<FilterColumn<T>> columns, Function<? super T, ? extends K> identity) {
        Map<String, FilterColumn<T>> byId = new LinkedHashMap<>();
        for (FilterColumn<T> column : Objects.requireNonNull(columns, "columns")) {
            Objects.requireNonNull(column, "column");
            if (byId.put(column.getId(), column) != null)
                throw new IllegalArgumentException("duplicate column id: " + column.getId());
        }
        this.columns = Collections.unmodifiableMap(byId);
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    /**
     * Replaces the source rows, as happens after every rendered Project outcome,
     * New, and Open. Criteria and sort order are retained verbatim. The selection
     * is kept only when its identity is still visible under the retained criteria;
     * a selection hidden by an active criterion is never restored. Callers that
     * must drop the selection regardless (the production New and Open paths do)
     * call {@link #clearSelection()} themselves.
     *
     * @param rows source rows in canonical order
     * @throws NullPointerException     when rows or a row is null
     * @throws IllegalArgumentException when two rows share an identity
     */
    public void setRows(List<? extends T> rows) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(rows, "rows").size());
        Set<K> seen = new HashSet<>();
        for (T row : rows) {
            Objects.requireNonNull(row, "row");
            K key = identityOf(row);
            if (!seen.add(key))
                throw new IllegalArgumentException("duplicate row identity: " + key);
            copy.add(row);
        }
        this.rows = copy;
        reconcileSelection();
    }

    /**
     * Sets or replaces the criterion for one column. An inactive criterion (one
     * that hides nothing) clears the column instead.
     *
     * @param criterion the criterion to apply
     * @throws NullPointerException     when criterion is null
     * @throws IllegalArgumentException when the column is unknown
     */
    public void setCriterion(ColumnCriterion criterion) {
        String columnId = requireColumn(Objects.requireNonNull(criterion, "criterion").getColumnId());
        if (criterion.isActive())
            criteria.put(columnId, criterion);
        else
            criteria.remove(columnId);
        reconcileSelection();
    }

    /**
     * Removes the criterion of one column, if any.
     *
     * @param columnId column to clear
     * @throws NullPointerException     when columnId is null
     * @throws IllegalArgumentException when the column is unknown
     */
    public void clearCriterion(String columnId) {
        criteria.remove(requireColumn(columnId));
        reconcileSelection();
    }

    /**
     * Removes every criterion. The selection, if any, stays visible and is kept.
     */
    public void clearAllCriteria() {
        criteria.clear();
        reconcileSelection();
    }

    /**
     * @return the active criteria, immutable, in column order
     */
    public List<ColumnCriterion> getCriteria() {
        List<ColumnCriterion> ordered = new ArrayList<>();
        for (String columnId : columns.keySet()) {
            ColumnCriterion criterion = criteria.get(columnId);
            if (criterion != null)
                ordered.add(criterion);
        }
        return Collections.unmodifiableList(ordered);
    }

    /**
     * @return true when at least one criterion is active
     */
    public boolean isFiltered() {
        return !criteria.isEmpty();
    }

    /**
     * @return the immutable sort order in priority order
     */
    public List<SortKey> getSortOrder() {
        return sortOrder;
    }

    /**
     * Replaces the sort order. Rows are compared by cell text, key by key; rows
     * that compare equal keep their source order. Membership and selection are
     * unaffected.
     *
     * @param sortOrder sort keys in priority order; empty restores source order
     * @throws NullPointerException     when sortOrder or a key is null
     * @throws IllegalArgumentException when a key names an unknown column
     */
    public void setSortOrder(List<SortKey> sortOrder) {
        List<SortKey> copy = new ArrayList<>();
        for (SortKey key : Objects.requireNonNull(sortOrder, "sortOrder"))
            copy.add(Objects.requireNonNull(key, "sort key"));
        for (SortKey key : copy)
            requireColumn(key.getColumnId());
        this.sortOrder = Collections.unmodifiableList(copy);
    }

    /**
     * Selects the row with the given identity if it is currently visible.
     *
     * @param identity logical identity to select
     * @return true when the row is visible and is now selected; false leaves the
     * previous selection untouched
     * @throws NullPointerException when identity is null
     */
    public boolean select(K identity) {
        Objects.requireNonNull(identity, "identity");
        for (T row : rows) {
            K key = identityOf(row);
            if (key.equals(identity) && isVisible(row)) {
                selection = key;
                return true;
            }
        }
        return false;
    }

    /**
     * Clears the logical selection.
     */
    public void clearSelection() {
        selection = null;
    }

    /**
     * @return the selected identity, always a member of the visible set
     */
    public Optional<K> getSelection() {
        return Optional.ofNullable(selection);
    }

    /**
     * Freezes the current visible set in presentation order.
     *
     * @return an immutable copy that later view changes cannot alter
     */
    public VisibleSet<T, K> visibleSet() {
        List<T> visible = new ArrayList<>();
        for (T row : rows)
            if (isVisible(row))
                visible.add(row);
        sort(visible);
        List<K> identities = new ArrayList<>(visible.size());
        for (T row : visible)
            identities.add(identityOf(row));
        return new VisibleSet<>(visible, identities);
    }

    private boolean isVisible(T row) {
        for (ColumnCriterion criterion : criteria.values())
            if (!criterion.admits(columns.get(criterion.getColumnId()).cellValueOf(row)))
                return false;
        return true;
    }

    /**
     * Stable sort by cell text so equal keys preserve canonical source order, the
     * same guarantee a TableView's sorted items give.
     */
    private void sort(List<T> visible) {
        if (sortOrder.isEmpty())
            return;
        visible.sort((left, right) -> {
            for (SortKey key : sortOrder) {
                FilterColumn<T> column = columns.get(key.getColumnId());
                int comparison = column.cellValueOf(left).compareTo(column.cellValueOf(right));
                if (comparison != 0)
                    return key.getDirection() == SortDirection.DESCENDING ? -comparison : comparison;
            }
            return 0;
        });
    }

    /**
     * Drops the selection when its row is no longer present or no longer visible.
     */
    private void reconcileSelection() {
        if (selection == null)
            return;
        K current = selection;
        selection = null;
        select(current);
    }

    private K identityOf(T row) {
        return Objects.requireNonNull(identity.apply(row), "identity of row");
    }

    private String requireColumn(String columnId) {
        if (!columns.containsKey(Objects.requireNonNull(columnId, "columnId")))
            throw new IllegalArgumentException("unknown column: " + columnId);
        return columnId;
    }
}
