package com.asdasfa.jbs2bg.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.fx.FxTestToolkit;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mechanism tests for the shared UIA list helper (#127 §7, #147): the no-op {@code setAll} guard, the keyed single
 * child guard used by the severity icon hosts, and the empty-to-populated ListView node replacement with its focus
 * capture. The Windows UIA outcome these mechanisms protect is proven only by the packaged Tier 3 smoke run.
 */
class UiaListHelperTest {

    /** Unchanged content fires no list change; changed content is replaced in one atomic change. */
    @Test
    void setAllIfChangedReplacesOnlyChangedContent() {
        ObservableList<String> items = FXCollections.observableArrayList("Alpha", "Beta");
        List<Object> changes = new ArrayList<>();
        items.addListener((ListChangeListener<String>) changes::add);

        assertFalse(UiaListHelper.setAllIfChanged(items, List.of("Alpha", "Beta")));
        assertEquals(List.of(), changes);

        assertTrue(UiaListHelper.setAllIfChanged(items, List.of("Beta", "Alpha")));
        assertEquals(List.of("Beta", "Alpha"), items);
        assertEquals(1, changes.size(), "a reorder must replace atomically");

        assertTrue(UiaListHelper.setAllIfChanged(items, List.of()));
        assertEquals(List.of(), items);
        assertFalse(UiaListHelper.setAllIfChanged(items, List.of()));
        assertEquals(2, changes.size());
    }

    /** The guard compares by element equality and tolerates null elements, as ComboBox items may hold. */
    @Test
    void setAllIfChangedComparesElementsByEqualityIncludingNulls() {
        ObservableList<String> items = FXCollections.observableArrayList("Alpha", null);
        List<Object> changes = new ArrayList<>();
        items.addListener((ListChangeListener<String>) changes::add);

        assertFalse(UiaListHelper.setAllIfChanged(items, java.util.Arrays.asList(new String("Alpha"), null)));
        assertTrue(UiaListHelper.setAllIfChanged(items, java.util.Arrays.asList("Alpha", "Beta")));
        assertEquals(1, changes.size());
    }

    /** A keyed child is rebuilt only when its key changes, so a same-severity re-render keeps the icon node. */
    @Test
    void keyedChildIsRebuiltOnlyWhenItsKeyChanges() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            StackPane host = new StackPane();
            List<String> built = new ArrayList<>();
            List<Object> changes = new ArrayList<>();
            host.getChildren().addListener((ListChangeListener<Node>) changes::add);

            assertTrue(UiaListHelper.setKeyedChildIfChanged(host.getChildren(), "success", key -> {
                built.add(key);
                return new Label(key);
            }));
            Node first = host.getChildren().getFirst();

            assertFalse(UiaListHelper.setKeyedChildIfChanged(host.getChildren(), "success", key -> {
                built.add(key);
                return new Label(key);
            }));
            assertSame(first, host.getChildren().getFirst());
            assertEquals(1, changes.size());

            assertTrue(UiaListHelper.setKeyedChildIfChanged(host.getChildren(), "warning", key -> {
                built.add(key);
                return new Label(key);
            }));
            assertEquals(List.of("success", "warning"), built);
            assertEquals(1, host.getChildren().size());
            assertEquals("warning", ((Label) host.getChildren().getFirst()).getText());
            assertEquals(2, changes.size());
        });
    }

    /**
     * A child the helper did not key (for example one placed by FXML) never counts as matching, so the first
     * render always installs a keyed node.
     */
    @Test
    void unkeyedChildIsAlwaysReplaced() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            StackPane host = new StackPane(new Label("success"));

            assertTrue(UiaListHelper.setKeyedChildIfChanged(host.getChildren(), "success", Label::new));
            assertFalse(UiaListHelper.setKeyedChildIfChanged(host.getChildren(), "success", Label::new));
        });
    }

    /**
     * The first reconcile fills the list it is given in place, even when that list starts empty: only a list that
     * was already rendered empty has a stale accessibility subtree to discard.
     */
    @Test
    void firstReconcileFillsTheOriginalListInPlace() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture(node -> false);

            fixture.list.reconcile(List.of("Alpha"));

            assertSame(fixture.original, fixture.current.get());
            assertEquals(List.of("Alpha"), fixture.original.getItems());
            assertEquals(0, fixture.adopted.size());
        });
    }

    /**
     * Refilling a rendered empty list swaps a fresh node into the same position of its owner, adopts it before it
     * is filled, and leaves the stale node detached.
     */
    @Test
    void emptyToPopulatedRefillReplacesTheNodeAtTheSamePosition() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture(node -> false);
            fixture.list.reconcile(List.of());

            fixture.list.reconcile(List.of("Alpha", "Beta"));

            ListView<String> replacement = fixture.current.get();
            assertNotSame(fixture.original, replacement);
            assertNull(fixture.original.getParent(), "the stale empty node must leave the scene graph");
            assertSame(fixture.owner, replacement.getParent());
            assertEquals(1, fixture.owner.getChildren().indexOf(replacement));
            assertEquals(List.of(replacement), fixture.adopted);
            assertEquals(List.of(0), fixture.itemsSeenOnAdopt, "adoption must happen before the refill");
            assertEquals(List.of("Alpha", "Beta"), replacement.getItems());
        });
    }

    /**
     * Only the empty-to-populated transition replaces the node: a membership change, a clear, and an unchanged
     * re-render all reuse the current list, and the unchanged one fires no list change.
     */
    @Test
    void onlyTheEmptyToPopulatedTransitionReplacesTheNode() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture(node -> false);
            fixture.list.reconcile(List.of("Alpha"));
            List<Object> changes = new ArrayList<>();
            fixture.original.getItems().addListener((ListChangeListener<String>) changes::add);

            fixture.list.reconcile(List.of("Alpha"));
            assertEquals(List.of(), changes);
            fixture.list.reconcile(List.of("Alpha", "Beta"));
            fixture.list.reconcile(List.of());
            fixture.list.reconcile(List.of());

            assertSame(fixture.original, fixture.current.get());
            assertEquals(2, changes.size());
            assertEquals(0, fixture.adopted.size());

            fixture.list.reconcile(List.of("Gamma"));
            assertNotSame(fixture.original, fixture.current.get());
        });
    }

    /**
     * The focus capture happens before the swap: a replaced list that owned focus hands it to its replacement, and
     * one that did not leaves focus where it was.
     */
    @Test
    void refillMovesFocusToTheReplacementOnlyWhenTheReplacedListOwnedIt() throws Exception {
        AtomicReference<Stage> stageHolder = new AtomicReference<>();
        try {
            FxTestToolkit.runOnFxThread(() -> {
                Fixture fixture = new Fixture(node -> node.getScene() != null
                        && node.getScene().getFocusOwner() == node);
                Stage stage = new Stage();
                stage.setScene(new Scene(fixture.owner, 300.0, 300.0));
                stage.show();
                stageHolder.set(stage);
                fixture.list.reconcile(List.of());
                fixture.original.requestFocus();
                assertSame(fixture.original, stage.getScene().getFocusOwner());

                fixture.list.reconcile(List.of("Alpha"));

                assertSame(fixture.current.get(), stage.getScene().getFocusOwner());

                Button elsewhere = fixture.before;
                fixture.list.reconcile(List.of());
                elsewhere.requestFocus();
                fixture.list.reconcile(List.of("Beta"));

                assertSame(elsewhere, stage.getScene().getFocusOwner());
            });
        } finally {
            FxTestToolkit.runOnFxThread(() -> {
                if (stageHolder.get() != null)
                    stageHolder.get().close();
            });
        }
    }

    /** A list that has left its declared owner is an invariant breach, reported with the caller's wording. */
    @Test
    void refillFailsWhenTheOwnerNoLongerHoldsTheList() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            Fixture fixture = new Fixture(node -> false);
            fixture.list.reconcile(List.of());
            fixture.owner.getChildren().remove(fixture.original);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> fixture.list.reconcile(List.of("Alpha")));
            assertEquals("Test owner no longer owns the test list", failure.getMessage());
        });
    }

    /** One owner with a list between two siblings, wired to the helper the way the Workbench wires its lists. */
    private static final class Fixture {
        private final Button before = new Button("before");
        private final ListView<String> original = new ListView<>();
        private final VBox owner = new VBox(before, original, new Button("after"));
        private final AtomicReference<ListView<String>> current = new AtomicReference<>(original);
        private final List<ListView<String>> adopted = new ArrayList<>();
        private final List<Integer> itemsSeenOnAdopt = new ArrayList<>();
        private final UiaListHelper.RefillingList<String> list;

        /** Wires a refilling list whose adoption records the replacement and its item count at that moment. */
        private Fixture(java.util.function.Predicate<Node> ownsSceneFocus) {
            list = new UiaListHelper(ownsSceneFocus).refilling(current::get, owner,
                    "Test owner no longer owns the test list", () -> {
                        ListView<String> replacement = new ListView<>();
                        replacement.setFocusTraversable(true);
                        return replacement;
                    }, replacement -> {
                        current.set(replacement);
                        adopted.add(replacement);
                        itemsSeenOnAdopt.add(replacement.getItems().size());
                    });
        }
    }
}
