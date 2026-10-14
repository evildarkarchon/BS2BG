package com.asdasfa.jbs2bg.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javafx.scene.Parent;

/**
 * The shell's side of {@link WorkbenchSatellites}: it holds the registered satellite roots, re-themes them with each
 * appearance frame the shell renders, and dismisses them when the Workbench window is hidden. Confined to the JavaFX
 * Application Thread.
 */
final class SatelliteRegistry implements WorkbenchSatellites {
    private final Supplier<WorkbenchAppearance.Frame> currentFrame;
    // Registration order; entries compare by identity, so the same root registered twice is two registrations.
    private final List<Satellite> satellites = new ArrayList<>();

    /**
     * Creates an empty registry.
     *
     * @param currentFrame the most recently resolved appearance, read when a satellite registers; it is only read
     *                     then, so it may depend on state the shell initializes after constructing the registry
     * @throws NullPointerException when {@code currentFrame} is null
     */
    SatelliteRegistry(Supplier<WorkbenchAppearance.Frame> currentFrame) {
        this.currentFrame = Objects.requireNonNull(currentFrame, "currentFrame");
    }

    /** {@inheritDoc} */
    @Override
    public Registration register(Parent root, Runnable dismiss) {
        Satellite satellite = new Satellite(Objects.requireNonNull(root, "root"),
                Objects.requireNonNull(dismiss, "dismiss"));
        JavaFxWorkbenchAppearance.applyTo(root, currentFrame.get());
        satellites.add(satellite);
        return () -> satellites.remove(satellite);
    }

    /**
     * Applies one appearance frame to every registered satellite root.
     *
     * @param frame resolved theme and palette the shell just rendered
     */
    void retheme(WorkbenchAppearance.Frame frame) {
        for (Satellite satellite : List.copyOf(satellites))
            JavaFxWorkbenchAppearance.applyTo(satellite.root(), frame);
    }

    /**
     * Dismisses every registered satellite. Iterates a copy because each dismissal normally closes its own
     * registration while the loop runs.
     */
    void dismissAll() {
        for (Satellite satellite : List.copyOf(satellites))
            satellite.dismiss().run();
    }

    /** @return how many satellites are registered now */
    int size() {
        return satellites.size();
    }

    /**
     * One registered satellite. A class rather than a record so that removal is by identity, never by an equal root
     * and dismiss pair.
     */
    private static final class Satellite {
        private final Parent root;
        private final Runnable dismiss;

        /** Pairs a satellite root with its dismissal. */
        Satellite(Parent root, Runnable dismiss) {
            this.root = root;
            this.dismiss = dismiss;
        }

        /** @return the satellite scene's root */
        Parent root() {
            return root;
        }

        /** @return the satellite's dismissal */
        Runnable dismiss() {
            return dismiss;
        }
    }
}
