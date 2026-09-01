package com.asdasfa.jbs2bg.presentation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Presentation-owned read model rendered exclusively from immutable
 * ProjectSession snapshots. JavaFX controllers observe these projections but
 * submit all changes back through the session edit seam.
 */
public final class ProjectPresentation {

    private final String applicationName;
    private final ObservableList<SliderPresetSnapshot> mutableSliderPresets;
    private final ObservableList<SliderPresetSnapshot> sliderPresets;
    private final ObservableList<CustomMorphTargetSnapshot> mutableCustomMorphTargets;
    private final ObservableList<CustomMorphTargetSnapshot> customMorphTargets;
    private final ObservableList<NpcMorphAssignmentSnapshot> mutableNpcMorphAssignments;
    private final ObservableList<NpcMorphAssignmentSnapshot> npcMorphAssignments;
    private ProjectSnapshot snapshot;

    /**
     * Creates a presentation read model from one coherent initial snapshot.
     *
     * @param applicationName base application title
     * @param initialSnapshot initial immutable Project state
     * @throws NullPointerException     when an argument is null
     * @throws IllegalArgumentException when the application name is blank
     */
    public ProjectPresentation(String applicationName, ProjectSnapshot initialSnapshot) {
        this.applicationName = requireApplicationName(applicationName);
        this.mutableSliderPresets = FXCollections.observableArrayList();
        this.sliderPresets = FXCollections.unmodifiableObservableList(mutableSliderPresets);
        this.mutableCustomMorphTargets = FXCollections.observableArrayList();
        this.customMorphTargets = FXCollections.unmodifiableObservableList(mutableCustomMorphTargets);
        this.mutableNpcMorphAssignments = FXCollections.observableArrayList();
        this.npcMorphAssignments = FXCollections.unmodifiableObservableList(mutableNpcMorphAssignments);
        renderSnapshot(Objects.requireNonNull(initialSnapshot, "initialSnapshot"));
    }

    /**
     * Reports whether two snapshots carry the same Project content. Snapshot lists
     * are always defensive copies, so the comparison is by element identity: the
     * session reuses immutable element values whenever a collection is untouched.
     *
     * @param left  previously rendered snapshot
     * @param right newly rendered snapshot
     * @return true when every content collection holds identical elements in order
     */
    private static boolean sameContent(ProjectSnapshot left, ProjectSnapshot right) {
        return sameElements(left.getSliderPresets(), right.getSliderPresets())
                && sameElements(left.getCustomMorphTargets(), right.getCustomMorphTargets())
                && sameElements(left.getNpcMorphAssignments(), right.getNpcMorphAssignments());
    }

    /**
     * Compares two lists by size and per-index element identity.
     */
    private static boolean sameElements(List<?> left, List<?> right) {
        if (left.size() != right.size())
            return false;
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index) != right.get(index))
                return false;
        }
        return true;
    }

    /**
     * Reports whether an outcome carries any presentation-level error diagnostic.
     */
    private static boolean hasErrorDiagnostic(ProjectOutcome outcome) {
        for (ProjectDiagnostic diagnostic : outcome.getDiagnostics()) {
            if (diagnostic.getSeverity() == DiagnosticSeverity.ERROR)
                return true;
        }
        return false;
    }

    /**
     * Requires a non-blank application name for stable title formatting.
     */
    private static String requireApplicationName(String name) {
        Objects.requireNonNull(name, "applicationName");
        if (name.trim().isEmpty())
            throw new IllegalArgumentException("applicationName must not be blank");
        return name;
    }

    /**
     * Rebuilds the JavaFX-facing read model from the snapshot returned by one
     * ProjectSession outcome and derives presentation-only effects.
     *
     * @param outcome typed ProjectSession outcome to render
     * @return immutable invalidation and diagnostic effects for JavaFX controls
     * @throws NullPointerException when outcome is null
     */
    public ProjectPresentationUpdate render(ProjectOutcome outcome) {
        ProjectOutcome requiredOutcome = Objects.requireNonNull(outcome, "outcome");
        ProjectSnapshot previous = snapshot;
        renderSnapshot(requiredOutcome.getSnapshot());
        // Generated output depends only on Project content. A save publishes a
        // ChangedOutcome for the dirty flag / file identity alone, so keying
        // invalidation on the outcome type would discard freshly generated text.
        return new ProjectPresentationUpdate(!sameContent(previous, snapshot),
                ProjectDiagnosticFormatter.format(requiredOutcome.getDiagnostics()), hasErrorDiagnostic(requiredOutcome));
    }

    /**
     * @return the latest immutable snapshot rendered by the presentation
     */
    public ProjectSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Returns a stable, structurally unmodifiable observable projection of
     * immutable Slider Preset snapshot values.
     *
     * @return the observable Slider Preset projection
     */
    public ObservableList<SliderPresetSnapshot> getSliderPresets() {
        return sliderPresets;
    }

    /**
     * Returns a stable, structurally unmodifiable observable projection of
     * immutable Custom Morph Target snapshot values.
     *
     * @return the observable Custom Morph Target projection
     */
    public ObservableList<CustomMorphTargetSnapshot> getCustomMorphTargets() {
        return customMorphTargets;
    }

    /**
     * Returns a stable, structurally unmodifiable observable projection of
     * immutable NPC Morph Assignment snapshot values.
     *
     * @return the observable NPC Morph Assignment projection
     */
    public ObservableList<NpcMorphAssignmentSnapshot> getNpcMorphAssignments() {
        return npcMorphAssignments;
    }

    /**
     * Derives the window title from the same file identity and dirty state carried
     * by the latest rendered snapshot.
     *
     * @return current user-facing window title
     */
    public String getWindowTitle() {
        String dirtyMarker = snapshot.isDirty() ? "*" : "";
        if (snapshot.getFileIdentity().isEmpty())
            return applicationName + (dirtyMarker.isEmpty() ? "" : " " + dirtyMarker);
        Path identity = snapshot.getFileIdentity().get();
        Path fileName = identity.getFileName();
        String displayName = fileName == null ? identity.toString() : fileName.toString();
        return applicationName + " - " + dirtyMarker + displayName;
    }

    /**
     * @return true when closing, opening, or creating would discard unsaved Project changes
     */
    public boolean requiresDiscardConfirmation() {
        return snapshot.isDirty();
    }

    /**
     * Replaces stable observable projections directly from one immutable Project
     * snapshot.
     *
     * @param nextSnapshot complete immutable state to publish
     */
    private void renderSnapshot(ProjectSnapshot nextSnapshot) {
        if (nextSnapshot == snapshot)
            return;

        // setAll fires list and selection listeners synchronously, and those listeners
        // read getSnapshot() (e.g. to rebuild a preset preview). Publish the snapshot
        // first so no listener combines a new list item with the previous snapshot.
        snapshot = nextSnapshot;
        mutableSliderPresets.setAll(nextSnapshot.getSliderPresets());
        mutableCustomMorphTargets.setAll(nextSnapshot.getCustomMorphTargets());
        mutableNpcMorphAssignments.setAll(nextSnapshot.getNpcMorphAssignments());
    }
}
