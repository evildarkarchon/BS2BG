package com.asdasfa.jbs2bg.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.FilterColumn;
import com.asdasfa.jbs2bg.filtering.FilteredView;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.filtering.VisibleSet;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Public-JavaFX adapter that renders one {@link FilteredView} into a
 * {@link TableView}. The view owns every logical decision (membership, order,
 * selection); this adapter only copies the view's visible set into the table's
 * items and translates table gestures back into view operations. It never
 * installs JavaFX transformation lists on the table and never touches skins.
 * <p>
 * Not thread-safe: every method must run on the JavaFX Application Thread,
 * as must every mutation of the source list while it is attached.
 *
 * @param <T> row type
 * @param <K> identity type with value equality
 */
public final class FilteredTableAdapter<T, K> {

    private final TableView<T> table;
    private final FilteredView<T, K> view;
    private final Map<TableColumn<T, ?>, FilterColumn<T>> columnsByTableColumn;
    private final Map<TableColumn<T, ?>, ColumnFilterMenu<T>> menusByTableColumn = new LinkedHashMap<>();
    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final ListChangeListener<T> sourceListener = change -> reloadRows();
    private final ChangeListener<T> tableSelectionListener = (observable, previous, selected) -> onTableSelection(
            selected);
    private ObservableList<? extends T> source;
    /** Copy of the source rows the view currently holds; feeds every column checklist. */
    private List<T> rows = new ArrayList<>();
    /** Visible set rendered by the last {@link #render()}; its identities locate rows for selection. */
    private VisibleSet<T, K> rendered;
    /**
     * True while the adapter itself is replacing the table's items. TableView
     * re-enters the sort policy and fires selection changes during such a
     * replacement; those echoes must not be written back into the view.
     */
    private boolean rendering;

    private FilteredTableAdapter(TableView<T> table, List<FilterColumn<T>> columns,
            Function<? super T, ? extends K> identity) {
        this.table = Objects.requireNonNull(table, "table");
        this.view = new FilteredView<>(columns, identity);
        this.columnsByTableColumn = mapLeafColumns(table, columns);
        table.setItems(items);
        // The view is the single source of order: the policy pushes the table's
        // sort order into the view and re-renders instead of sorting items in place.
        table.setSortPolicy(ignored -> applySortOrder());
        table.getSelectionModel().selectedItemProperty().addListener(tableSelectionListener);
        for (Map.Entry<TableColumn<T, ?>, FilterColumn<T>> entry : columnsByTableColumn.entrySet()) {
            ColumnFilterMenu<T> menu = new ColumnFilterMenu<>(this, entry.getValue());
            entry.getKey().setContextMenu(menu.getContextMenu());
            menusByTableColumn.put(entry.getKey(), menu);
        }
        render();
        refreshMenus();
    }

    /**
     * Attaches a new adapter to a table. The table's items list and sort policy
     * are replaced by ones the adapter owns; callers must not set items on the
     * table afterwards.
     *
     * @param table table to drive; every leaf column's header text must name a
     *        filter column
     * @param columns filterable columns; IDs are the table's column header texts
     * @param identity derives the logical identity of a row
     * @param <T> row type
     * @param <K> identity type
     * @return the attached adapter
     * @throws NullPointerException when an argument is null
     * @throws IllegalArgumentException when a leaf table column has no filter
     *         column of the same ID, so that no column can silently be unsortable
     */
    public static <T, K> FilteredTableAdapter<T, K> attach(TableView<T> table, List<FilterColumn<T>> columns,
            Function<? super T, ? extends K> identity) {
        return new FilteredTableAdapter<>(table, columns, identity);
    }

    /**
     * Replaces the source rows the adapter observes. The previous source, if
     * any, is no longer observed. A null source detaches the adapter and leaves
     * the table empty, which lets a caller mutate the old list off the JavaFX
     * thread safely.
     *
     * @param source rows in canonical order, or null to detach
     */
    public void setSource(ObservableList<? extends T> source) {
        if (this.source != null)
            this.source.removeListener(sourceListener);
        this.source = source;
        if (source != null)
            source.addListener(sourceListener);
        reloadRows();
    }

    /**
     * Freezes the current visible set in presentation order: the scope every
     * bulk command operates on.
     *
     * @return an immutable copy that later table or view changes cannot alter
     */
    public VisibleSet<T, K> visibleSet() {
        return view.visibleSet();
    }

    /**
     * Selects the row with the given identity if it is currently visible.
     *
     * @param identity logical identity to select
     * @return true when the row is visible and is now selected in the table;
     *         false leaves the previous selection untouched
     * @throws NullPointerException when identity is null
     */
    public boolean select(K identity) {
        if (!view.select(identity))
            return false;
        syncTableSelection();
        return true;
    }

    /** Clears the logical selection and the table's selection. */
    public void clearSelection() {
        view.clearSelection();
        syncTableSelection();
    }

    /** @return the selected identity, always a member of the visible set */
    public Optional<K> getSelection() {
        return view.getSelection();
    }

    /**
     * Scrolls the table so the selected row is visible, if there is one. Uses
     * only {@link TableView#scrollTo(int)}, which JavaFX 25 turns into the
     * minimal scroll (none when the row is already fully visible).
     */
    public void revealSelection() {
        int index = table.getSelectionModel().getSelectedIndex();
        if (index >= 0)
            table.scrollTo(index);
    }

    /**
     * @param column a leaf column of the table
     * @return the checklist filter menu installed on that column
     * @throws IllegalArgumentException when the column is not one of the table's leaf columns
     */
    public ColumnFilterMenu<T> filterMenu(TableColumn<T, ?> column) {
        return menusByTableColumn.get(requireLeafColumn(column));
    }

    /**
     * Sets or replaces one column's criterion and re-renders. An inactive
     * criterion clears the column instead.
     *
     * @param criterion the criterion to apply
     * @throws IllegalArgumentException when the column is unknown
     */
    public void setCriterion(ColumnCriterion criterion) {
        view.setCriterion(criterion);
        afterCriteriaChanged();
    }

    /** Removes every criterion and re-renders; the selection, if still present, is kept. */
    public void clearAllCriteria() {
        view.clearAllCriteria();
        afterCriteriaChanged();
    }

    /** @return the active criteria, immutable, in column order */
    public List<ColumnCriterion> getCriteria() {
        return view.getCriteria();
    }

    /** @return true when at least one column criterion is active */
    public boolean isFiltered() {
        return view.isFiltered();
    }

    /**
     * Derives the text a row shows in one column, exactly as the filtering seam
     * sees it; type-ahead search uses the leading column's text.
     *
     * @param column a leaf column of the table
     * @param row row to read
     * @return the non-null cell text
     * @throws IllegalArgumentException when the column is not one of the table's leaf columns
     */
    public String cellTextOf(TableColumn<T, ?> column, T row) {
        return columnsByTableColumn.get(requireLeafColumn(column)).cellValueOf(row);
    }

    private TableColumn<T, ?> requireLeafColumn(TableColumn<T, ?> column) {
        if (!columnsByTableColumn.containsKey(Objects.requireNonNull(column, "column")))
            throw new IllegalArgumentException("column '" + column.getText() + "' is not a leaf column of the table");
        return column;
    }

    private void reloadRows() {
        rows = source == null ? new ArrayList<>() : new ArrayList<>(source);
        view.setRows(rows);
        render();
        refreshMenus();
    }

    private void afterCriteriaChanged() {
        render();
        refreshMenus();
    }

    /** Rebuilds every checklist from the current rows and marks filtered column headers. */
    private void refreshMenus() {
        Map<String, ColumnCriterion> criteria = new LinkedHashMap<>();
        for (ColumnCriterion criterion : view.getCriteria())
            criteria.put(criterion.getColumnId(), criterion);
        for (Map.Entry<TableColumn<T, ?>, ColumnFilterMenu<T>> entry : menusByTableColumn.entrySet()) {
            ColumnCriterion criterion = criteria.get(columnsByTableColumn.get(entry.getKey()).getId());
            entry.getValue().refreshChoices(rows, criterion);
            entry.getKey().setGraphic(criterion == null ? null : ColumnFilterMenu.newFilterIndicator());
        }
    }

    /** Sort policy body: mirrors the table's sort order into the view, then re-renders. */
    private boolean applySortOrder() {
        if (rendering)
            return true;
        List<SortKey> keys = new ArrayList<>();
        for (TableColumn<T, ?> sorted : table.getSortOrder()) {
            FilterColumn<T> column = columnsByTableColumn.get(sorted);
            if (column == null)
                continue;
            keys.add(sorted.getSortType() == TableColumn.SortType.DESCENDING ? SortKey.descending(column.getId())
                    : SortKey.ascending(column.getId()));
        }
        view.setSortOrder(keys);
        render();
        revealSelection();
        return true;
    }

    /**
     * Copies the view's visible set into the table's items and re-applies the
     * logical selection, which JavaFX may have moved or dropped while the items
     * were replaced.
     */
    private void render() {
        rendering = true;
        try {
            rendered = view.visibleSet();
            items.setAll(rendered.getRows());
            syncTableSelection();
        } finally {
            rendering = false;
        }
    }

    /** Makes the table's selection equal to the view's selection without echoing back. */
    private void syncTableSelection() {
        boolean wasRendering = rendering;
        rendering = true;
        try {
            Optional<K> selection = view.getSelection();
            int index = selection.isPresent() ? rendered.getIdentities().indexOf(selection.get()) : -1;
            if (index >= 0)
                table.getSelectionModel().clearAndSelect(index);
            else
                table.getSelectionModel().clearSelection();
        } finally {
            rendering = wasRendering;
        }
    }

    /** Table gesture: a user (or JavaFX) changed the selected row. */
    private void onTableSelection(T selected) {
        if (rendering)
            return;
        if (selected == null) {
            view.clearSelection();
            return;
        }
        int index = items.indexOf(selected);
        if (index >= 0)
            view.select(rendered.getIdentities().get(index));
    }

    /** Pairs every leaf table column with the filter column of the same ID. */
    private static <T> Map<TableColumn<T, ?>, FilterColumn<T>> mapLeafColumns(TableView<T> table,
            List<FilterColumn<T>> columns) {
        Map<String, FilterColumn<T>> byId = new LinkedHashMap<>();
        for (FilterColumn<T> column : columns)
            byId.put(column.getId(), column);
        Map<TableColumn<T, ?>, FilterColumn<T>> mapped = new LinkedHashMap<>();
        for (TableColumn<T, ?> leaf : leafColumns(table.getColumns())) {
            FilterColumn<T> column = byId.get(leaf.getText());
            if (column == null)
                throw new IllegalArgumentException("table column '" + leaf.getText() + "' has no filter column");
            mapped.put(leaf, column);
        }
        return mapped;
    }

    private static <T> List<TableColumn<T, ?>> leafColumns(List<TableColumn<T, ?>> columns) {
        List<TableColumn<T, ?>> leaves = new ArrayList<>();
        for (TableColumn<T, ?> column : columns) {
            if (column.getColumns().isEmpty())
                leaves.add(column);
            else
                leaves.addAll(leafColumns(column.getColumns()));
        }
        return leaves;
    }
}
