package com.asdasfa.jbs2bg.workbench.settings;

import com.asdasfa.jbs2bg.workbench.ShellPort;

/**
 * The shell port as the Settings Area sees it: every shared {@link ShellPort} request plus the one request typed by
 * a Settings feature effect. It lives beside the Settings feature, not in {@link ShellPort}, so the {@code workbench}
 * contract types depend on no Area package. The shell implements it once and hands the other Areas the same object as
 * a plain {@link ShellPort}. Every method runs on the JavaFX Application Thread.
 */
public interface SettingsShellPort extends ShellPort {

    /**
     * Admits one Settings worker effect (Save, Reload or a generation preference) to the shell's Settings job
     * runner. A refused admission has already cancelled the feature's pending effect; the Area re-renders its own
     * draft. The shell attaches any pending close continuation before the job can complete.
     *
     * @param effect the captured worker effect
     * @return whether the coordinator admitted the job
     */
    boolean submitSettingsEffect(SettingsFeature.Effect effect);
}
