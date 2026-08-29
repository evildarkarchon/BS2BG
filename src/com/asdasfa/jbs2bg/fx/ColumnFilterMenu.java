package com.asdasfa.jbs2bg.fx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.FilterColumn;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polygon;

/**
 * Excel-style checklist filter for one table column, shown as the column's
 * {@link javafx.scene.control.TableColumn#contextMenuProperty() context menu}
 * (JavaFX opens it on a right-click of the header, so no skin access is
 * needed to anchor it). The checklist lists the distinct cell values of the
 * source rows; APPLY hides the unchecked values through one
 * {@link ColumnCriterion} on the owning {@link FilteredTableAdapter}.
 * <p>
 * The search box narrows the checklist by substring; applying while a search
 * is active keeps exactly the matching values and hides every other value,
 * mirroring the filter this replaces.
 *
 * @param <T> row type
 */
public final class ColumnFilterMenu<T> {

    private final FilteredTableAdapter<T, ?> adapter;
    private final FilterColumn<T> column;
    private final ObservableList<Choice> choices = FXCollections.observableArrayList();
    /** UI-local narrowing of the checklist by the search box; never part of the filtering seam. */
    private final FilteredList<Choice> shown = new FilteredList<>(choices);
    private final TextField searchBox = new TextField();
    private final ContextMenu contextMenu = new ContextMenu();

    /**
     * Builds the menu; the adapter attaches it to the matching table column.
     *
     * @param adapter owner that receives criteria and reset requests
     * @param column the logical column this menu filters
     */
    ColumnFilterMenu(FilteredTableAdapter<T, ?> adapter, FilterColumn<T> column) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.column = Objects.requireNonNull(column, "column");

        searchBox.setPromptText("Search...");
        searchBox.textProperty().addListener((observable, previous, text) -> shown
                .setPredicate(choice -> text == null || text.isEmpty() || choice.getValue().contains(text)));

        ListView<Choice> checklist = new ListView<>(shown);
        checklist.setCellFactory(CheckBoxListCell.forListView(Choice::selectedProperty));

        Button apply = button("APPLY", this::apply);
        Button none = button("NONE", this::selectNone);
        Button all = button("ALL", this::selectAll);
        Button reset = button("RESET ALL", this::resetAll);
        HBox buttons = new HBox(apply, none, all, reset);
        buttons.setAlignment(Pos.BASELINE_CENTER);

        VBox panel = new VBox(searchBox, checklist, buttons);
        panel.getStyleClass().add("filter-panel");
        panel.setPadding(new Insets(3));

        CustomMenuItem item = new CustomMenuItem(panel);
        item.setHideOnClick(false);
        contextMenu.getStyleClass().add("column-filter");
        contextMenu.getItems().add(item);
    }

    /** @return the context menu to install on the table column */
    public ContextMenu getContextMenu() {
        return contextMenu;
    }

    /** @return the distinct cell values of the source rows, sorted, with their checked state */
    public List<Choice> getChoices() {
        return Collections.unmodifiableList(choices);
    }

    /** @return the choices currently matching the search text, all choices when it is empty */
    public List<Choice> getShownChoices() {
        return Collections.unmodifiableList(new ArrayList<>(shown));
    }

    /** @param text substring that narrows the checklist; empty shows every choice */
    public void setSearchText(String text) {
        searchBox.setText(text == null ? "" : text);
    }

    /** @return the current search text, never null */
    public String getSearchText() {
        return searchBox.getText() == null ? "" : searchBox.getText();
    }

    /** Checks every choice; nothing is applied until {@link #apply()}. */
    public void selectAll() {
        for (Choice choice : choices)
            choice.setSelected(true);
    }

    /** Unchecks every choice; nothing is applied until {@link #apply()}. */
    public void selectNone() {
        for (Choice choice : choices)
            choice.setSelected(false);
    }

    /**
     * Applies the checklist as this column's criterion: unchecked values are
     * hidden. With an active search, only the matching values stay checked.
     * Hides the menu afterwards.
     */
    public void apply() {
        if (!getSearchText().isEmpty()) {
            List<Choice> matching = getShownChoices();
            for (Choice choice : choices)
                choice.setSelected(matching.contains(choice));
            setSearchText("");
        }
        List<String> hidden = new ArrayList<>();
        for (Choice choice : choices)
            if (!choice.isSelected())
                hidden.add(choice.getValue());
        adapter.setCriterion(ColumnCriterion.hiding(column.getId(), hidden));
        contextMenu.hide();
    }

    /** Clears the criteria of every column of the table and hides the menu. */
    public void resetAll() {
        adapter.clearAllCriteria();
        contextMenu.hide();
    }

    /**
     * Rebuilds the checklist from the current source rows: a value is checked
     * unless the column's active criterion hides it. Called by the adapter on
     * every row reload and criteria change.
     *
     * @param rows every source row, visible or not
     * @param criterion the column's active criterion, or null when unfiltered
     */
    void refreshChoices(Collection<? extends T> rows, ColumnCriterion criterion) {
        Set<String> values = new TreeSet<>();
        for (T row : rows)
            values.add(column.cellValueOf(row));
        List<Choice> rebuilt = new ArrayList<>(values.size());
        for (String value : values) {
            Choice choice = new Choice(value);
            choice.setSelected(criterion == null || criterion.admits(value));
            rebuilt.add(choice);
        }
        choices.setAll(rebuilt);
    }

    /** A small funnel drawn from public shapes; marks a filtered column header. */
    static Node newFilterIndicator() {
        Polygon funnel = new Polygon(0, 0, 10, 0, 6, 4.5, 6, 10, 4, 8.5, 4, 4.5);
        funnel.getStyleClass().add("filter-indicator");
        return funnel;
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setOnAction(event -> action.run());
        return button;
    }

    /** One distinct cell value and whether it stays visible. */
    public static final class Choice {

        private final String value;
        private final BooleanProperty selected = new SimpleBooleanProperty(true);

        Choice(String value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        /** @return the exact cell text */
        public String getValue() {
            return value;
        }

        /** @return true when the value is checked (kept visible on apply) */
        public boolean isSelected() {
            return selected.get();
        }

        /** @param selected whether the value is checked */
        public void setSelected(boolean selected) {
            this.selected.set(selected);
        }

        /** @return the checked-state property the checklist cell binds to */
        public BooleanProperty selectedProperty() {
            return selected;
        }

        /** The checklist cell's default string converter renders this text. */
        @Override
        public String toString() {
            return value;
        }
    }
}
