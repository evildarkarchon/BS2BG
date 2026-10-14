package com.asdasfa.jbs2bg.workbench;

import javafx.scene.Node;
import javafx.scene.layout.HBox;

/**
 * The shared fixture for every {@code <Area>AreaTest}: it loads one Area's FXML alone, through the same
 * {@link AreaLoader} the shell uses, and hosts its three panes in a minimal Scene. The Area drives a real feature
 * over a real {@link com.asdasfa.jbs2bg.project.ProjectSession} built from an explicit Settings store, with a
 * {@link RecordingPlatform} behind the pipeline's dialogs and a {@link RecordingShellPort} in place of the shell.
 * <p>
 * The fixture plays the shell's part of the Area contract and nothing more (see {@link ModuleTest}): it builds the
 * {@link WorkbenchContext}, renders it into the Area once on load and again on every job frame, and feeds the job
 * frames to the pipeline's gate. Tests assert through the Area's controls and the recorded port requests.
 * <p>
 * Subclasses implement {@link #load(ModuleTest.Collaborators)} to build the Area's feature and call the Area's
 * production loader. Every method that touches the hosted Area runs on the JavaFX Application Thread.
 *
 * @param <A> the Area under test
 */
public abstract class AreaTest<A extends WorkbenchArea> extends ModuleTest<A> {

    /**
     * Builds the Area's feature and loads the Area through its production loader.
     *
     * @param collaborators the shell-side collaborators
     * @return the loaded Area and its FXML namespace
     */
    protected abstract AreaLoader.Loaded<A> load(Collaborators collaborators);

    /**
     * Loads the Area, shows its three panes as the shell shows the active Area's, and hosts them side by side.
     *
     * @param collaborators the shell-side collaborators
     * @return the Area, hosted in an {@link HBox} and rendered through {@link WorkbenchArea#render}
     */
    @Override
    protected final Mount<A> mount(Collaborators collaborators) {
        AreaLoader.Loaded<A> loaded = load(collaborators);
        AreaPanes panes = loaded.area().panes();
        for (Node pane : new Node[] {panes.getPrimary(), panes.getEditor(), panes.getInspector()}) {
            // The shell shows only the active Area's panes; the hosted Area is always the active one.
            pane.setManaged(true);
            pane.setVisible(true);
        }
        A area = loaded.area();
        return new Mount<>(area, loaded.namespace(),
                new HBox(12.0, panes.getPrimary(), panes.getEditor(), panes.getInspector()), area::render);
    }
}
