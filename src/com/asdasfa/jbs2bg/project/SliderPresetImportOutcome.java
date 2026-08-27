package com.asdasfa.jbs2bg.project;

import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome for one ordered BodySlide XML import batch. The aggregate
 * typed outcome carries the final snapshot while source outcomes retain the
 * changed, unchanged, rejected, or failed result for each selected file.
 */
public final class SliderPresetImportOutcome {

    private final ProjectOutcome projectOutcome;
    private final List<ProjectOutcome> sourceOutcomes;

    /**
     * Creates one batch outcome and defensively copies its source results.
     *
     * @param projectOutcome aggregate result carrying the final batch snapshot
     * @param sourceOutcomes typed results in selected-source order
     * @throws NullPointerException when an argument or source outcome is null
     */
    public SliderPresetImportOutcome(ProjectOutcome projectOutcome, List<ProjectOutcome> sourceOutcomes) {
        this.projectOutcome = Objects.requireNonNull(projectOutcome, "projectOutcome");
        this.sourceOutcomes = ImmutableValues.copyOf(sourceOutcomes, "sourceOutcomes");
    }

    /** @return the aggregate typed Project outcome */
    public ProjectOutcome getProjectOutcome() {
        return projectOutcome;
    }

    /** @return the final coherent Project snapshot */
    public ProjectSnapshot getSnapshot() {
        return projectOutcome.getSnapshot();
    }

    /** @return aggregate diagnostics in selected-source order */
    public List<ProjectDiagnostic> getDiagnostics() {
        return projectOutcome.getDiagnostics();
    }

    /** @return immutable typed outcomes in selected-source order */
    public List<ProjectOutcome> getSourceOutcomes() {
        return sourceOutcomes;
    }
}
