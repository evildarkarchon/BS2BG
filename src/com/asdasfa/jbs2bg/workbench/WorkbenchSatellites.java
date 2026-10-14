package com.asdasfa.jbs2bg.workbench;

import javafx.scene.Parent;

/**
 * The shell's appearance as seen by an owned satellite surface: a flyout or a separate owned window such as the NPC
 * portrait viewer. A satellite registers its root once as it opens. From then until it closes the returned
 * registration, the shell re-themes that root on every appearance change and dismisses the satellite when the
 * Workbench window is hidden, so the shell keeps no field per satellite.
 *
 * <p>Confined to the JavaFX Application Thread.
 */
public interface WorkbenchSatellites {

    /**
     * Registers one satellite root and applies the current Workbench appearance to it immediately, because its
     * separate scene opened after the last appearance publication.
     *
     * @param root    root of the satellite's own scene; re-themed until the registration is closed
     * @param dismiss hides or closes the satellite when the Workbench window is hidden; a satellite normally closes
     *                its registration from its own hidden handler, which this triggers
     * @return the registration to close when the satellite hides
     * @throws NullPointerException when an argument is null
     */
    Registration register(Parent root, Runnable dismiss);

    /** One registered satellite; closing it stops re-theming and dismissal. Closing it again does nothing. */
    @FunctionalInterface
    interface Registration extends AutoCloseable {

        /** Unregisters the satellite without hiding it; it never throws. */
        @Override
        void close();
    }
}
