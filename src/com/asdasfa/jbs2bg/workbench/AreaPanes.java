package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;

import javafx.scene.Node;

/**
 * The non-Node root of an Area FXML: it holds the Area's primary, editor and inspector panes so the shell can place
 * each one into its own landmark container. It is deliberately not a Node, so the FXML has no layout of its own and
 * the shell keeps the landmark containers, the responsive reparenting and the overlay.
 * <p>
 * FXMLLoader fills the three properties through the JavaBean setters, from {@code <primary>}, {@code <editor>} and
 * {@code <inspector>} property elements. Each property is set once; the getters fail fast if the FXML left one out.
 */
public final class AreaPanes {
    private Node primary;
    private Node editor;
    private Node inspector;

    /** @return the pane placed into the shell's primary-content container */
    public Node getPrimary() {
        return required(primary, "primary");
    }

    /**
     * Sets the pane placed into the shell's primary-content container.
     *
     * @param pane the Area's primary pane
     * @throws IllegalStateException when the pane was already set
     */
    public void setPrimary(Node pane) {
        primary = once(primary, pane, "primary");
    }

    /** @return the pane placed into the shell's editor container */
    public Node getEditor() {
        return required(editor, "editor");
    }

    /**
     * Sets the pane placed into the shell's editor container.
     *
     * @param pane the Area's editor pane
     * @throws IllegalStateException when the pane was already set
     */
    public void setEditor(Node pane) {
        editor = once(editor, pane, "editor");
    }

    /** @return the pane placed into the shell's inspector container */
    public Node getInspector() {
        return required(inspector, "inspector");
    }

    /**
     * Sets the pane placed into the shell's inspector container.
     *
     * @param pane the Area's inspector pane
     * @throws IllegalStateException when the pane was already set
     */
    public void setInspector(Node pane) {
        inspector = once(inspector, pane, "inspector");
    }

    /** Returns a pane, failing when the Area FXML did not declare it. */
    private static Node required(Node pane, String name) {
        if (pane == null)
            throw new IllegalStateException("The Area FXML declares no " + name + " pane");
        return pane;
    }

    /** Accepts a pane once: an Area's panes are fixed when its FXML loads. */
    private static Node once(Node current, Node pane, String name) {
        Objects.requireNonNull(pane, name);
        if (current != null)
            throw new IllegalStateException("The " + name + " pane is already set");
        return pane;
    }
}
