package com.asdasfa.jbs2bg.workbench;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Area;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.FocusTarget;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Landmark;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * The shell's shared focus policy: it classifies the scene's focus owner into a semantic landmark, drives the F6
 * cycle from that classification, realizes semantic focus targets with the Area-default fallback, and restores focus
 * when a satellite window (popup or owned Stage) closes. Focus intent lives in the scene's focus owner (#123): the
 * policy never reads window activation.
 * <p>
 * Classification is structural. The shell registers each landmark container once; the focus owner's nearest
 * registered ancestor (or the node itself) names its landmark, and a node under no registered container is on the
 * rail. A control therefore classifies into the pane that really holds it, without a per-control list.
 * <p>
 * What stays with each Area is supplied through {@link AreaFocus}: the node for each landmark, including its
 * state-dependent defaults, and the Area's last-resort default. Exact restores (for example the Templates
 * failure-dialog restore) stay with the Area as well. All methods must run on the JavaFX Application Thread.
 */
public final class WorkbenchFocusPolicy {

    /**
     * Per-Area focus nodes, supplied by the shell today and by each Area once Areas are modules.
     */
    public interface AreaFocus {
        /**
         * Resolves a semantic target to its current node. Area landmarks may depend on Area state.
         *
         * @param target semantic target to resolve
         * @return the node for the target, or null when the target has no node in the current layout
         */
        Node focusNode(FocusTarget target);

        /**
         * Returns the Area's last-resort focus node, used when a target's node cannot take focus.
         *
         * @param area Area whose default is requested
         * @return a non-null node that is focusable whenever the Area is active
         */
        Node defaultNode(Area area);
    }

    private final Window window;
    private final Supplier<Area> activeArea;
    private final AreaFocus areas;
    // Node equality is identity, so a plain HashMap keys each registered container by identity.
    private final Map<Node, Landmark> landmarks = new HashMap<>();

    /**
     * Creates a policy for one Workbench window.
     *
     * @param window     the Workbench window; its current scene supplies the focus owner
     * @param activeArea supplies the Area the shell currently shows
     * @param areas      resolves semantic targets and Area defaults to nodes
     * @throws NullPointerException when an argument is null
     */
    public WorkbenchFocusPolicy(Window window, Supplier<Area> activeArea, AreaFocus areas) {
        this.window = Objects.requireNonNull(window, "window");
        this.activeArea = Objects.requireNonNull(activeArea, "activeArea");
        this.areas = Objects.requireNonNull(areas, "areas");
    }

    /**
     * Registers a landmark container. A focus owner classifies into its nearest registered ancestor, so a launcher
     * registered inside a registered pane wins over the pane.
     *
     * @param container node whose subtree belongs to the landmark
     * @param landmark  landmark of that subtree
     * @return this policy, for chained registration
     * @throws NullPointerException when an argument is null
     */
    public WorkbenchFocusPolicy landmark(Node container, Landmark landmark) {
        landmarks.put(Objects.requireNonNull(container, "container"), Objects.requireNonNull(landmark, "landmark"));
        return this;
    }

    /**
     * Resolves the scene's current focus owner into a semantic target that is safe to keep across re-rendering.
     *
     * @return the active Area and the focus owner's landmark; {@link Landmark#RAIL} when nothing classifies
     */
    public FocusTarget current() {
        Scene scene = window.getScene();
        return new FocusTarget(activeArea.get(), classify(scene == null ? null : scene.getFocusOwner()));
    }

    /**
     * Advances F6 from the landmark that holds the current focus owner. The caller commits the returned transition
     * and realizes its focus target through {@link #request(FocusTarget)}.
     *
     * @param navigation the shell's navigation state, which owns the landmark order for the current layout
     * @return unchanged navigation state plus the next landmark's focus target
     */
    public WorkbenchNavigation.Transition cycle(WorkbenchNavigation navigation) {
        return navigation.cycleFocus(current());
    }

    /**
     * Applies one semantic focus effect, falling back to the target Area's default when the target's node is
     * missing, hidden, disabled or detached. Focus is requested once, synchronously (#123).
     *
     * @param target semantic target to focus
     */
    public void request(FocusTarget target) {
        Node node = areas.focusNode(target);
        if (node == null || !node.isVisible() || node.isDisabled() || node.getParent() == null)
            node = areas.defaultNode(target.area());
        node.requestFocus();
    }

    /**
     * Restores focus when a satellite window (a popup or owned Stage) closes: to its launcher while the launcher can
     * still take focus, otherwise to {@code fallback} in the active Area. Call it synchronously from the satellite's
     * {@code onHidden}: it only sets the main scene's focus owner, which JavaFX realizes whenever the Workbench window
     * is active, so no deferral is needed even if another window holds OS focus right now. Nothing is restored once
     * the Workbench window itself is hidden, for example when satellites close because the Workbench is closing.
     *
     * @param launcher the control that opened the satellite
     * @param fallback landmark of the active Area to focus when the launcher cannot take focus
     */
    public void restoreAfterSatellite(Node launcher, Landmark fallback) {
        if (!window.isShowing())
            return;
        if (canRestoreFocus(launcher))
            launcher.requestFocus();
        else
            request(new FocusTarget(activeArea.get(), fallback));
    }

    /**
     * Reports whether {@code node} is its scene's focus owner. This is the Workbench's only focus check: unlike
     * {@code Node.isFocused()}, it does not depend on the OS window being active, so a refill or async completion
     * while another window has focus still preserves the focus owner that JavaFX realizes when the user returns.
     *
     * @param node node to test; may be detached
     * @return true when the node is attached and owns its scene's focus
     */
    public static boolean ownsSceneFocus(Node node) {
        return node.getScene() != null && node.getScene().getFocusOwner() == node;
    }

    /**
     * Reports whether {@code node} can take focus back. An overlay or Area change can hide an ancestor while leaving
     * the launcher's own visible bit unchanged, so every ancestor is checked.
     *
     * @param node node to test; may be detached
     * @return true when the node is attached, enabled, and it and all its ancestors are visible
     */
    public static boolean canRestoreFocus(Node node) {
        if (node.getScene() == null || node.isDisabled())
            return false;
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible())
                return false;
        }
        return true;
    }

    /**
     * Classifies one node by walking up to its nearest registered landmark container.
     *
     * @param node node to classify; may be null or detached
     * @return the nearest registered landmark, or {@link Landmark#RAIL} when no ancestor is registered
     */
    Landmark classify(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            Landmark landmark = landmarks.get(current);
            if (landmark != null)
                return landmark;
        }
        return Landmark.RAIL;
    }
}
