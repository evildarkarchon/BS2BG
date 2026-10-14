package com.asdasfa.jbs2bg.workbench;

import javafx.scene.Node;

/**
 * One Workbench Area module: its own FXML, controller, controls and renders, loaded by the shell through
 * {@link AreaLoader} and placed into the shell's landmark containers. The shell drives every Area through this
 * interface and the Area's typed methods; Areas never reference each other, and reach the shell only through the
 * {@link ShellPort}. Every method runs on the JavaFX Application Thread.
 */
public interface WorkbenchArea {

    /**
     * @return the Area's three landmark panes, which the shell mounts into its primary, editor and inspector
     *         containers and shows only while the Area is active
     */
    AreaPanes panes();

    /**
     * Renders the Area's feature frame against one shell context. This is the single fan-out: the shell calls it on
     * every Project frame and every job frame. The Area keeps the context and renders its own later updates against
     * it, so cross-Area inputs (job admission, live Settings) arrive only through the shell.
     *
     * @param context the context the shell just built
     */
    void render(WorkbenchContext context);

    /**
     * Resolves one of the Area's landmarks to the node that should take focus for it now.
     *
     * @param landmark a landmark inside the Area's panes ({@code PRIMARY_CONTENT}, {@code EDITOR} or
     *                 {@code INSPECTOR})
     * @return the state-dependent node for that landmark, or null when the Area does not own the landmark
     */
    Node focusNode(WorkbenchNavigation.Landmark landmark);

    /**
     * Returns the Area's last-resort focus node, used when a requested target cannot take focus. Most Areas fall back
     * to their primary content; an Area whose first editable control lives elsewhere overrides this.
     *
     * @return the Area's first editable control for its current state
     */
    default Node defaultFocusNode() {
        return focusNode(WorkbenchNavigation.Landmark.PRIMARY_CONTENT);
    }

    /** Moves focus to the Area's search or filter field for Ctrl+K; an Area without one does nothing. */
    void focusSearch();

    /**
     * Handles an Escape that the shell's navigation did not consume.
     *
     * @return true when the Area consumed it
     */
    boolean escape();

    /** Called when navigation leaves the Area: hide its popups and clear its visit-scoped state. */
    void leave();

    /** Called when the narrow-mode inspector overlay opens, so the Area can scroll its inspector to the top. */
    void resetInspectorScroll();
}
