package com.asdasfa.jbs2bg.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JavaFX-independent Workbench navigation state. Destinations and focus requests stay semantic so feature
 * modules never exchange controller or {@code javafx.scene.Node} references.
 */
public final class WorkbenchNavigation {

    /**
     * Client widths below this logical-pixel boundary use narrow side overlays.
     */
    public static final double NARROW_BREAKPOINT = 1200.0;
    private Area activeArea = Area.TEMPLATES;
    private boolean outputDrawerVisible;
    private FocusTarget outputReturnTarget;
    private boolean narrowMode;
    private Overlay overlay = Overlay.NONE;
    private FocusTarget overlayReturnTarget;

    /**
     * A logical focus destination that can be resolved by the current JavaFX adapter.
     */
    public record FocusTarget(Area area, Landmark landmark) {
        /** Rejects incomplete semantic targets at construction time. */
        public FocusTarget {
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(landmark, "landmark");
        }
    }

    /**
     * Durable navigation state rendered by the Workbench adapter.
     */
    public record Frame(Area activeArea, boolean outputDrawerVisible, boolean narrowMode, Overlay overlay) {
        /** Rejects incomplete frame state at construction time. */
        public Frame {
            Objects.requireNonNull(activeArea, "activeArea");
            Objects.requireNonNull(overlay, "overlay");
        }
    }

    /**
     * One committed frame plus an optional at-most-once semantic focus effect.
     */
    public record Transition(Frame frame, Optional<FocusTarget> focusTarget) {
        /** Rejects incomplete transition values at construction time. */
        public Transition {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(focusTarget, "focusTarget");
        }
    }

    /**
     * Navigates to one typed rail destination.
     *
     * @param destination  semantic rail or accelerator destination
     * @param currentFocus current semantic focus, retained by later transient-surface slices
     * @return committed navigation state and the requested destination focus
     */
    public Transition navigate(Destination destination, FocusTarget currentFocus) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(currentFocus, "currentFocus");
        if (destination == Destination.OUTPUT) {
            if (outputDrawerVisible) {
                outputDrawerVisible = false;
                FocusTarget returnTarget = outputReturnTarget != null
                        ? outputReturnTarget
                        : new FocusTarget(activeArea, Landmark.PRIMARY_CONTENT);
                outputReturnTarget = null;
                return transition(returnTarget);
            }
            outputDrawerVisible = true;
            outputReturnTarget = currentFocus;
            return transition(new FocusTarget(activeArea, Landmark.OUTPUT));
        }
        activeArea = switch (destination) {
            case TEMPLATES -> Area.TEMPLATES;
            case MORPHS -> Area.MORPHS;
            case NPC_DATABASE -> Area.NPC_DATABASE;
            case SETTINGS -> Area.SETTINGS;
            case OUTPUT -> activeArea;
        };
        if (narrowMode) {
            overlay = Overlay.PRIMARY_CONTENT;
            overlayReturnTarget = new FocusTarget(activeArea, Landmark.RAIL);
        } else {
            overlay = Overlay.NONE;
            overlayReturnTarget = null;
        }
        return transition(new FocusTarget(activeArea, Landmark.PRIMARY_CONTENT));
    }

    /**
     * Reveals fresh generated Output without changing the active Area or requesting focus.
     *
     * @return committed drawer state with no focus effect
     */
    public Transition revealOutput() {
        outputDrawerVisible = true;
        return new Transition(currentFrame(), Optional.empty());
    }

    /**
     * Returns the current immutable navigation state for a newly attached adapter.
     */
    public Frame currentFrame() {
        return new Frame(activeArea, outputDrawerVisible, narrowMode, overlay);
    }

    /**
     * Advances focus through the currently present semantic F6 landmarks.
     *
     * @param currentFocus current semantic focus, or a stale target from a surface that just closed
     * @return unchanged durable state plus the next valid semantic focus target
     */
    public Transition cycleFocus(FocusTarget currentFocus) {
        Objects.requireNonNull(currentFocus, "currentFocus");
        List<Landmark> landmarks = new ArrayList<>();
        landmarks.add(Landmark.RAIL);
        if (!narrowMode || overlay == Overlay.PRIMARY_CONTENT)
            landmarks.add(Landmark.PRIMARY_CONTENT);
        landmarks.add(Landmark.EDITOR);
        if (!narrowMode || overlay == Overlay.INSPECTOR)
            landmarks.add(Landmark.INSPECTOR);
        if (outputDrawerVisible)
            landmarks.add(Landmark.OUTPUT);
        landmarks.add(Landmark.ACTIVITY);
        landmarks.add(Landmark.STATUS);
        int currentIndex = landmarks.indexOf(currentFocus.landmark());
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % landmarks.size();
        return transition(new FocusTarget(activeArea, landmarks.get(nextIndex)));
    }

    /**
     * Reconciles responsive state from the measured logical client width.
     *
     * @param clientWidth  current client width in JavaFX logical pixels
     * @param currentFocus semantic focus before the reflow
     * @return reflowed frame and a safe editor focus request when an inline side pane became hidden
     */
    public Transition resize(double clientWidth, FocusTarget currentFocus) {
        if (!Double.isFinite(clientWidth) || clientWidth < 0.0)
            throw new IllegalArgumentException("clientWidth must be finite and non-negative");
        Objects.requireNonNull(currentFocus, "currentFocus");
        boolean nextNarrowMode = clientWidth < NARROW_BREAKPOINT;
        if (nextNarrowMode == narrowMode)
            return new Transition(currentFrame(), Optional.empty());
        narrowMode = nextNarrowMode;
        overlay = Overlay.NONE;
        overlayReturnTarget = null;
        if (narrowMode && (currentFocus.landmark() == Landmark.PRIMARY_CONTENT
                || currentFocus.landmark() == Landmark.INSPECTOR)) {
            return transition(new FocusTarget(activeArea, Landmark.EDITOR));
        }
        return new Transition(currentFrame(), Optional.empty());
    }

    /**
     * Opens or reaches the inspector, using a side overlay only in narrow mode.
     *
     * @param launcher semantic focus to restore when the narrow overlay closes
     * @return committed overlay state and inspector focus request
     */
    public Transition openInspector(FocusTarget launcher) {
        Objects.requireNonNull(launcher, "launcher");
        if (narrowMode) {
            overlay = Overlay.INSPECTOR;
            overlayReturnTarget = launcher;
        }
        return transition(new FocusTarget(activeArea, Landmark.INSPECTOR));
    }

    /**
     * Opens or reaches primary list/content, using a side overlay only in narrow mode.
     *
     * @param launcher semantic focus to restore when the narrow overlay closes
     * @return committed overlay state and primary-content focus request
     */
    public Transition openPrimaryContent(FocusTarget launcher) {
        Objects.requireNonNull(launcher, "launcher");
        if (narrowMode) {
            overlay = Overlay.PRIMARY_CONTENT;
            overlayReturnTarget = launcher;
        }
        return transition(new FocusTarget(activeArea, Landmark.PRIMARY_CONTENT));
    }

    /**
     * Dismisses the innermost Workbench transient surface and restores its semantic launcher.
     *
     * @return committed state and focus restoration, or no effect when nothing is open
     */
    public Transition dismiss() {
        if (overlay != Overlay.NONE) {
            overlay = Overlay.NONE;
            FocusTarget returnTarget = overlayReturnTarget != null
                    ? overlayReturnTarget
                    : new FocusTarget(activeArea, Landmark.EDITOR);
            overlayReturnTarget = null;
            return transition(returnTarget);
        }
        if (outputDrawerVisible) {
            outputDrawerVisible = false;
            FocusTarget returnTarget = outputReturnTarget != null
                    ? outputReturnTarget
                    : new FocusTarget(activeArea, Landmark.PRIMARY_CONTENT);
            outputReturnTarget = null;
            return transition(returnTarget);
        }
        return new Transition(currentFrame(), Optional.empty());
    }

    /**
     * Creates one transition from the reducer's committed durable state.
     */
    private Transition transition(FocusTarget focusTarget) {
        return new Transition(currentFrame(), Optional.of(focusTarget));
    }

    /**
     * Full-page Workbench Areas; Output remains a drawer rather than an Area.
     */
    public enum Area {
        TEMPLATES("Templates"),
        MORPHS("Morphs"),
        NPC_DATABASE("NPC Database"),
        SETTINGS("Settings");

        private final String displayName;

        Area(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the stable user-facing Area name shared by semantic navigation adapters.
         */
        public String displayName() {
            return displayName;
        }
    }

    /**
     * Typed rail and accelerator destinations.
     */
    public enum Destination {
        TEMPLATES,
        MORPHS,
        NPC_DATABASE,
        OUTPUT,
        SETTINGS
    }

    /**
     * Semantic F6 landmarks owned by the Workbench shell.
     */
    public enum Landmark {
        RAIL,
        PRIMARY_LAUNCHER,
        PRIMARY_CONTENT,
        EDITOR,
        INSPECTOR_LAUNCHER,
        INSPECTOR,
        OUTPUT_LAUNCHER,
        OUTPUT,
        ACTIVITY,
        STATUS
    }

    /**
     * Side content that is temporarily overlaid in narrow mode.
     */
    public enum Overlay {
        NONE,
        PRIMARY_CONTENT,
        INSPECTOR
    }
}
