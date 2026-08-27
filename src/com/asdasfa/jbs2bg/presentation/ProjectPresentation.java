package com.asdasfa.jbs2bg.presentation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.project.SourceLocation;

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
     * @throws NullPointerException when an argument is null
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
     * Rebuilds the JavaFX-facing read model from the snapshot returned by one
     * ProjectSession outcome and derives presentation-only effects.
     *
     * @param outcome typed ProjectSession outcome to render
     * @return immutable invalidation and diagnostic effects for JavaFX controls
     * @throws NullPointerException when outcome is null
     */
    public ProjectPresentationUpdate render(ProjectOutcome outcome) {
        ProjectOutcome requiredOutcome = Objects.requireNonNull(outcome, "outcome");
        renderSnapshot(requiredOutcome.getSnapshot());
        return new ProjectPresentationUpdate(requiredOutcome instanceof ChangedOutcome,
                formatDiagnostics(requiredOutcome.getDiagnostics()), hasErrorDiagnostic(requiredOutcome));
    }

    /** @return the latest immutable snapshot rendered by the presentation */
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
        if (!snapshot.getFileIdentity().isPresent())
            return applicationName + (dirtyMarker.isEmpty() ? "" : " " + dirtyMarker);
        Path identity = snapshot.getFileIdentity().get();
        Path fileName = identity.getFileName();
        String displayName = fileName == null ? identity.toString() : fileName.toString();
        return applicationName + " - " + dirtyMarker + displayName;
    }

    /** @return true when closing, opening, or creating would discard unsaved Project changes */
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

        mutableSliderPresets.setAll(nextSnapshot.getSliderPresets());
        mutableCustomMorphTargets.setAll(nextSnapshot.getCustomMorphTargets());
        mutableNpcMorphAssignments.setAll(nextSnapshot.getNpcMorphAssignments());
        snapshot = nextSnapshot;
    }

    /** Formats structured diagnostics without moving JavaFX controls into ProjectSession. */
    private static String formatDiagnostics(List<ProjectDiagnostic> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (ProjectDiagnostic diagnostic : diagnostics) {
            if (text.length() > 0)
                text.append(System.lineSeparator());
            text.append(diagnostic.getSeverity()).append(" [").append(diagnostic.getCode()).append("] ");
            appendLocation(text, diagnostic.getSourceLocation());
            text.append(": ").append(diagnostic.getMessage());
        }
        return text.toString();
    }

    /** Appends the available file, element, line, and column portions of a source location. */
    private static void appendLocation(StringBuilder text, SourceLocation location) {
        boolean hasLocation = false;
        if (location.getPath().isPresent()) {
            Path path = location.getPath().get();
            Path fileName = path.getFileName();
            text.append(fileName == null ? path.toString() : fileName.toString());
            hasLocation = true;
        }
        if (location.getElement().isPresent()) {
            String element = location.getElement().get();
            // A root JSON pointer adds no useful location beyond its source file.
            if (!hasLocation || !"/".equals(element)) {
                if (hasLocation)
                    text.append(element.startsWith("/") ? " " : " / ");
                text.append(element);
                hasLocation = true;
            }
        }
        if (location.getLine().isPresent()) {
            if (hasLocation)
                text.append(' ');
            text.append("(line ").append(location.getLine().getAsInt());
            if (location.getColumn().isPresent())
                text.append(", column ").append(location.getColumn().getAsInt());
            text.append(')');
            hasLocation = true;
        }
        if (!hasLocation)
            text.append("Project");
    }

    /** Reports whether an outcome carries any presentation-level error diagnostic. */
    private static boolean hasErrorDiagnostic(ProjectOutcome outcome) {
        for (ProjectDiagnostic diagnostic : outcome.getDiagnostics()) {
            if (diagnostic.getSeverity() == DiagnosticSeverity.ERROR)
                return true;
        }
        return false;
    }

    /** Requires a non-blank application name for stable title formatting. */
    private static String requireApplicationName(String name) {
        Objects.requireNonNull(name, "applicationName");
        if (name.trim().isEmpty())
            throw new IllegalArgumentException("applicationName must not be blank");
        return name;
    }
}
