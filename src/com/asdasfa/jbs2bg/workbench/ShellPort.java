package com.asdasfa.jbs2bg.workbench;

import java.util.function.Function;

import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;

/**
 * The shell port: every request an Area makes of the shell or of another Area. Areas never reference each other;
 * they ask the shell, and the shell calls the target Area's typed method or does the shell-owned work itself. Each
 * Area injects this port through its constructor. Every method runs on the JavaFX Application Thread.
 * <p>
 * The port grows one request at a time as Areas are extracted; a request belongs here only when an Area makes it.
 */
public interface ShellPort {

    /**
     * Publishes Area-worded feedback through the shell's one feedback model, then renders the InfoBar, Activity and
     * status from the frame it returns.
     *
     * @param publication applies one publication ({@code publishStatus}, {@code publishActivity} and so on) to the
     *                    shell's feedback model and returns the resulting frame
     */
    void publishFeedback(Function<WorkbenchFeedback, WorkbenchFeedback.Frame> publication);

    /**
     * Admits one Settings worker effect (Save, Reload or a generation preference) to the shell's Settings job
     * runner. A refused admission has already cancelled the feature's pending effect; the Area re-renders its own
     * draft. The shell attaches any pending close continuation before the job can complete.
     *
     * @param effect the captured worker effect
     * @return whether the coordinator admitted the job
     */
    boolean submitSettingsEffect(SettingsFeature.Effect effect);

    /**
     * Reports an accepted Settings change that did not publish the Settings pair, so Output refreshes its generation
     * settings now.
     */
    void generationSettingsChanged();

    /**
     * Reports a settled Settings job that published new Settings, so every Settings-dependent part of the Workbench
     * refreshes. The shell defers the refresh while a shutdown holds job admission.
     *
     * @param pair true when the Standard and UUNP pair was published (Save, Reload or recovery); false when only the
     *             generation preference changed
     */
    void settingsPublished(boolean pair);
}
