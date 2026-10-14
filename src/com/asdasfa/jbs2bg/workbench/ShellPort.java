package com.asdasfa.jbs2bg.workbench;

import java.util.Optional;
import java.util.function.Function;

import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;

import javafx.scene.Node;

/**
 * The shell port: every request an Area makes of the shell or of another Area. Areas never reference each other;
 * they ask the shell, and the shell calls the target Area's typed method or does the shell-owned work itself. Each
 * Area injects this port through its constructor, and so does the Output drawer, which is not an Area but follows the
 * same rule. Every method runs on the JavaFX Application Thread.
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

    /**
     * Reveals the Output drawer through the shell's navigation, after a Generate published fresh Output. The reveal is
     * automatic, so it moves no focus.
     */
    void revealOutput();

    /**
     * Returns from the NPC Database to the Morphs Area. The shell selects the promoted assignment in Morphs when there
     * is one, scrolls it into view, and navigates back to the Morphs launcher that opened the NPC Database.
     *
     * @param assignment the NPC Morph Assignment the latest promotion added, or empty when there is none to select
     */
    void returnToMorphs(Optional<NpcMorphAssignmentIdentity> assignment);

    /**
     * Opens the NPC Database Area from a launcher in another Area. The shell navigates there and keeps the return
     * target, so Back to Morphs can put focus back on the launcher.
     *
     * @param returnTarget the semantic launcher focus the NPC Database returns to
     */
    void openNpcDatabase(WorkbenchNavigation.FocusTarget returnTarget);

    /**
     * Makes the active Area's primary content reachable before an Area moves focus into it. In narrow mode the shell
     * opens the primary-content overlay; in wide mode the primary content is always shown and nothing happens.
     */
    void revealPrimaryContent();

    /**
     * Reports a Project change the Area published itself, outside the pipeline's generic chrome step, so the shell
     * re-renders the Project chrome and every Area from the current Project frame. The shell arbitrates the change's
     * diagnostics: no other Area projects them.
     *
     * @param update          the feature update that published the change
     * @param ownsDiagnostics true when the Area presents every diagnostic of the change itself, so the Workbench's
     *                        Project diagnostics surface stays clear; false shows the Project frame's diagnostics
     *                        there
     */
    void projectChanged(WorkbenchFeature.Update<?> update, boolean ownsDiagnostics);

    /**
     * Classifies the current focus owner as a semantic target, so an Area can put focus back after a modal dialog
     * without retaining a node that a re-render may replace.
     *
     * @return the shell focus policy's semantic target for the current focus owner
     */
    WorkbenchNavigation.FocusTarget currentFocus();

    /**
     * Requests one semantic focus target through the shell's focus policy, which falls back to the target Area's
     * default node when the target cannot take focus.
     *
     * @param target the semantic target to focus
     */
    void requestFocus(WorkbenchNavigation.FocusTarget target);

    /**
     * Puts focus back after an Area's satellite (a flyout or popup) hid: on its launcher while it can take focus,
     * otherwise on the active Area's fallback landmark. Nothing happens once the Workbench window is hidden.
     *
     * @param launcher the control that opened the satellite
     * @param fallback the active Area's landmark to focus when the launcher cannot take focus
     */
    void restoreFocusAfterSatellite(Node launcher, WorkbenchNavigation.Landmark fallback);
}
