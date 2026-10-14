package com.asdasfa.jbs2bg.workbench;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.layout.Pane;

/**
 * The one shared home for the ListView workarounds that keep the JavaFX 25+ Windows UI Automation provider in step
 * with Workbench lists (#127 §7, #128).
 *
 * <ul>
 *   <li><b>No-op {@code setAll} avoidance.</b> Replacing a list's content with equal content still fires an
 *   empty/refill notification, after which the UIA provider can drop every virtualized cell. Every guarded
 *   collection goes through {@link #setAllIfChanged} or {@link #setKeyedChildIfChanged}.</li>
 *   <li><b>Empty-to-populated node replacement.</b> A ListView that was rendered empty keeps an empty virtualized
 *   accessibility subtree for the rest of its life, so a refill swaps in a fresh node (see
 *   {@link RefillingList}).</li>
 * </ul>
 *
 * <p>Whether each workaround is still needed is not decided here; keeping both in one place makes a later retirement a
 * one-place deletion. Single-Area workarounds (the rename edit lifecycle, slider-choice row reuse, the NPC Database
 * HelpText mirror) stay with their Area. Every method must be called on the JavaFX application thread when the
 * collection belongs to a live scene graph.</p>
 */
public final class UiaListHelper {
    // Node property key under which setKeyedChildIfChanged records the key a child was built from.
    private static final Object CHILD_KEY = new Object();

    private final Predicate<Node> ownsSceneFocus;

    /**
     * Creates a helper whose node replacements consult the Workbench focus policy.
     *
     * @param ownsSceneFocus reports whether a node is its scene's focus owner; it must not depend on the OS window
     *                       being active, so a refill while another window is active still keeps the focus owner
     */
    public UiaListHelper(Predicate<Node> ownsSceneFocus) {
        this.ownsSceneFocus = Objects.requireNonNull(ownsSceneFocus, "ownsSceneFocus");
    }

    /**
     * Replaces {@code target}'s content with {@code next} only when the two differ element by element, in order.
     * Unchanged content fires no list-change event; changed content is replaced in one atomic {@code setAll}.
     *
     * @param target live collection, such as a control's items or a pane's children
     * @param next   content the render wants; may contain {@code null} elements
     * @param <T>    element type
     * @return true when the content was replaced
     */
    public static <T> boolean setAllIfChanged(ObservableList<T> target, Collection<? extends T> next) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(next, "next");
        if (sameElements(target, next))
            return false;
        target.setAll(next);
        return true;
    }

    /**
     * Shows exactly one child built from {@code key}, rebuilding it only when the key differs from the one the current
     * child was built from. A child that this method did not build never matches, so the first call always installs
     * a keyed child.
     *
     * @param children live child list of a host pane
     * @param key      value the child is a pure function of, such as a severity icon key; compared with
     *                 {@link Object#equals}
     * @param build    builds the child for a new key
     * @param <K>      key type
     * @return true when the child was (re)built
     */
    public static <K> boolean setKeyedChildIfChanged(ObservableList<Node> children, K key,
                                                     Function<? super K, ? extends Node> build) {
        Objects.requireNonNull(children, "children");
        Objects.requireNonNull(build, "build");
        if (children.size() == 1) {
            var properties = children.getFirst().getProperties();
            if (properties.containsKey(CHILD_KEY) && Objects.equals(properties.get(CHILD_KEY), key))
                return false;
        }
        Node child = Objects.requireNonNull(build.apply(key), "built child");
        child.getProperties().put(CHILD_KEY, key);
        children.setAll(child);
        return true;
    }

    /**
     * Binds one Workbench ListView to the empty-to-populated replacement workaround.
     *
     * @param current             reads the list currently in the scene; the caller's field stays the source of truth
     * @param owner               pane that holds the list as a direct child
     * @param missingOwnerMessage message of the {@link IllegalStateException} thrown if {@code owner} no longer holds
     *                            the list when it must be replaced
     * @param replacement         builds a fresh, unconfigured list with the original's id, accessible text and layout
     *                            constraints
     * @param adopt               points the caller's field at the replacement and configures it (cell factory,
     *                            listeners, labels) before it is filled
     * @param <T>                 item type
     * @return the per-list reconciler
     */
    public <T> RefillingList<T> refilling(Supplier<ListView<T>> current, Pane owner, String missingOwnerMessage,
                                          Supplier<ListView<T>> replacement, Consumer<ListView<T>> adopt) {
        return new RefillingList<>(current, owner, missingOwnerMessage, replacement, adopt, ownsSceneFocus);
    }

    /** Compares two collections element by element in iteration order, tolerating null elements. */
    private static boolean sameElements(Collection<?> left, Collection<?> right) {
        if (left.size() != right.size())
            return false;
        Iterator<?> rightElements = right.iterator();
        for (Object element : left) {
            if (!Objects.equals(element, rightElements.next()))
                return false;
        }
        return true;
    }

    /**
     * One ListView whose content is reconciled through the no-op guard and whose node is replaced when a list that
     * was already rendered empty is refilled. The JavaFX 25+ Windows UIA provider otherwise keeps the empty
     * virtualized accessibility subtree for the lifetime of that control.
     *
     * @param <T> item type
     */
    public static final class RefillingList<T> {
        private final Supplier<ListView<T>> current;
        private final Pane owner;
        private final String missingOwnerMessage;
        private final Supplier<ListView<T>> replacement;
        private final Consumer<ListView<T>> adopt;
        private final Predicate<Node> ownsSceneFocus;
        // False until the first reconcile: the list FXML built has never exposed an empty subtree to UIA, so its
        // first fill happens in place.
        private boolean initialized;

        /** Captures the wiring documented on {@link UiaListHelper#refilling}. */
        private RefillingList(Supplier<ListView<T>> current, Pane owner, String missingOwnerMessage,
                              Supplier<ListView<T>> replacement, Consumer<ListView<T>> adopt,
                              Predicate<Node> ownsSceneFocus) {
            this.current = Objects.requireNonNull(current, "current");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.missingOwnerMessage = Objects.requireNonNull(missingOwnerMessage, "missingOwnerMessage");
            this.replacement = Objects.requireNonNull(replacement, "replacement");
            this.adopt = Objects.requireNonNull(adopt, "adopt");
            this.ownsSceneFocus = ownsSceneFocus;
        }

        /**
         * Renders {@code items}: replaces the node first if this is a refill of a rendered empty list, then replaces
         * the content only if it changed.
         *
         * @param items visible items in display order
         * @throws IllegalStateException if a replacement is due and the owner no longer holds the current list
         */
        public void reconcile(List<? extends T> items) {
            boolean refill = initialized && current.get().getItems().isEmpty() && !items.isEmpty();
            if (refill)
                replaceNode();
            setAllIfChanged(current.get().getItems(), items);
            initialized = true;
        }

        /** Swaps a fresh node into the current list's position, keeping keyboard focus if the old node owned it. */
        private void replaceNode() {
            ListView<T> stale = current.get();
            // Capture before the swap: removing the stale node from the scene clears its focus ownership.
            boolean restoreFocus = ownsSceneFocus.test(stale);
            int index = owner.getChildren().indexOf(stale);
            if (index < 0)
                throw new IllegalStateException(missingOwnerMessage);
            ListView<T> fresh = Objects.requireNonNull(replacement.get(), "replacement list");
            owner.getChildren().set(index, fresh);
            adopt.accept(fresh);
            if (fresh != current.get())
                throw new IllegalStateException("adopt must make the replacement the current list");
            if (restoreFocus) {
                // A filter clear, import or relationship change can refill the list while it owns focus; the UIA node
                // swap must keep that focus on the fresh accessible node.
                fresh.requestFocus();
            }
        }
    }
}
