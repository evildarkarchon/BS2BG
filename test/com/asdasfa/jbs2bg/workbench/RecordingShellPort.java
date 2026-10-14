package com.asdasfa.jbs2bg.workbench;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;
import com.asdasfa.jbs2bg.workbench.settings.SettingsShellPort;

import javafx.scene.Node;

/**
 * Test double for the {@link ShellPort}: it records every request an Area makes, in order, as a readable line, and
 * applies feedback publications to its own feedback model so tests can assert the Area's wording. Requests that need
 * a real collaborator (a Settings job) are delegated to one the test wires; an unwired one fails the test.
 */
public final class RecordingShellPort implements SettingsShellPort {
    private final WorkbenchFeedback feedback =
            new WorkbenchFeedback(Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC));
    private final List<String> requests = new ArrayList<>();
    private WorkbenchFeedback.Frame feedbackFrame = feedback.frame();
    private Predicate<SettingsFeature.Effect> settingsJobs;
    private WorkbenchNavigation.FocusTarget focusTarget =
            new WorkbenchNavigation.FocusTarget(WorkbenchNavigation.Area.NPC_DATABASE,
                    WorkbenchNavigation.Landmark.PRIMARY_CONTENT);

    /** @return the feedback model publications are applied to; the fixture's pipeline shares it */
    public WorkbenchFeedback feedback() {
        return feedback;
    }

    /** @return the frame returned by the latest feedback publication, or the initial frame */
    public WorkbenchFeedback.Frame feedbackFrame() {
        return feedbackFrame;
    }

    /** @return every request so far, oldest first, one readable line each */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    /**
     * Routes Settings job submissions to a real runner, as the shell does.
     *
     * @param submit admits one effect and returns whether the coordinator accepted it
     */
    public void runSettingsJobsWith(Predicate<SettingsFeature.Effect> submit) {
        settingsJobs = Objects.requireNonNull(submit, "submit");
    }

    /** Records the publication and keeps the frame it produced. */
    @Override
    public void publishFeedback(Function<WorkbenchFeedback, WorkbenchFeedback.Frame> publication) {
        feedbackFrame = publication.apply(feedback);
        requests.add("publishFeedback(" + feedbackFrame.status().message() + ")");
    }

    /**
     * Records the submission and delegates it to the wired runner.
     *
     * @throws AssertionError when the test did not wire a runner
     */
    @Override
    public boolean submitSettingsEffect(SettingsFeature.Effect effect) {
        requests.add("submitSettingsEffect(" + effect.getClass().getSimpleName() + ")");
        if (settingsJobs == null)
            throw new AssertionError("The Area submitted a Settings job, but the test wired no runner");
        return settingsJobs.test(effect);
    }

    /** Records the request. */
    @Override
    public void generationSettingsChanged() {
        requests.add("generationSettingsChanged()");
    }

    /** Records the request. */
    @Override
    public void settingsPublished(boolean pair) {
        requests.add("settingsPublished(pair=" + pair + ")");
    }

    /** Records the request. */
    @Override
    public void revealOutput() {
        requests.add("revealOutput()");
    }

    /** Records the request with the assignment's editor ID, or {@code none}. */
    @Override
    public void returnToMorphs(Optional<NpcMorphAssignmentIdentity> assignment) {
        requests.add("returnToMorphs(" + assignment.map(NpcMorphAssignmentIdentity::getEditorId).orElse("none") + ")");
    }

    /** Records the request with its return target. */
    @Override
    public void openNpcDatabase(WorkbenchNavigation.FocusTarget returnTarget) {
        requests.add("openNpcDatabase(" + returnTarget.area() + ", " + returnTarget.landmark() + ")");
    }

    /** Records the request. */
    @Override
    public void revealPrimaryContent() {
        requests.add("revealPrimaryContent()");
    }

    /** Records the request. */
    @Override
    public void importBodySlidePresets() {
        requests.add("importBodySlidePresets()");
    }

    /** Records the request with whether the Area owns the change's diagnostics. */
    @Override
    public void projectChanged(WorkbenchFeature.Update<?> update, boolean ownsDiagnostics) {
        Objects.requireNonNull(update, "update");
        requests.add("projectChanged(ownsDiagnostics=" + ownsDiagnostics + ")");
    }

    /**
     * Records the request and answers the target set by {@link #focusTargetIs}; the NPC Database primary content
     * until a test sets another, since there is no real focus policy behind this port.
     */
    @Override
    public WorkbenchNavigation.FocusTarget currentFocus() {
        requests.add("currentFocus()");
        return focusTarget;
    }

    /** Records the requested target. */
    @Override
    public void requestFocus(WorkbenchNavigation.FocusTarget target) {
        requests.add("requestFocus(" + target.area() + ", " + target.landmark() + ")");
    }

    /** Records the launcher's id and the fallback landmark. */
    @Override
    public void restoreFocusAfterSatellite(Node launcher, WorkbenchNavigation.Landmark fallback) {
        requests.add("restoreFocusAfterSatellite(" + launcher.getId() + ", " + fallback + ")");
    }

    /**
     * Sets the semantic target {@link #currentFocus()} answers.
     *
     * @param target the target the stand-in focus policy reports
     */
    public void focusTargetIs(WorkbenchNavigation.FocusTarget target) {
        focusTarget = Objects.requireNonNull(target, "target");
    }
}
