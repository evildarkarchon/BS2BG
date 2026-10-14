package com.asdasfa.jbs2bg.workbench.output;

import java.util.Objects;

import javafx.scene.Node;

/**
 * The non-Node root of the Output drawer FXML: it holds the two nodes the shell places, the drawer itself (into the
 * Workbench content stack, above the Areas) and the Generate button (into the Area header, where it sat in the single
 * Workbench FXML). Like {@link com.asdasfa.jbs2bg.workbench.AreaPanes} it is deliberately not a Node, so the shell
 * keeps the layout and each node keeps its original region nesting.
 * <p>
 * FXMLLoader fills both properties through the JavaBean setters, from {@code <generateButton>} and {@code <drawer>}
 * property elements. Each is set once; the getters fail fast if the FXML left one out.
 */
public final class OutputDrawerParts {
    private Node drawer;
    private Node generateButton;

    /** @return the drawer, which the shell places into its content stack */
    public Node getDrawer() {
        return required(drawer, "drawer");
    }

    /**
     * Sets the drawer the shell places into its content stack.
     *
     * @param node the drawer root
     * @throws IllegalStateException when the drawer was already set
     */
    public void setDrawer(Node node) {
        drawer = once(drawer, node, "drawer");
    }

    /** @return the Generate button, which the shell places into the Area header */
    public Node getGenerateButton() {
        return required(generateButton, "generateButton");
    }

    /**
     * Sets the Generate button the shell places into the Area header.
     *
     * @param node the Generate button
     * @throws IllegalStateException when the button was already set
     */
    public void setGenerateButton(Node node) {
        generateButton = once(generateButton, node, "generateButton");
    }

    /** Returns a part, failing when the drawer FXML did not declare it. */
    private static Node required(Node part, String name) {
        if (part == null)
            throw new IllegalStateException("The Output drawer FXML declares no " + name);
        return part;
    }

    /** Accepts a part once: the drawer's parts are fixed when its FXML loads. */
    private static Node once(Node current, Node part, String name) {
        Objects.requireNonNull(part, name);
        if (current != null)
            throw new IllegalStateException("The Output drawer " + name + " is already set");
        return part;
    }
}
