package com.asdasfa.jbs2bg.project;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of one ordered NPC promotion batch. Every row outcome is
 * rebound to the same final Project snapshot after the batch is committed.
 */
public final class NpcPromotionOutcome {
    private final ProjectOutcome projectOutcome;
    private final List<ProjectOutcome> rowOutcomes;

    /**
     * Captures the aggregate outcome and one typed outcome per requested row.
     *
     * @param projectOutcome aggregate result at the final snapshot
     * @param rowOutcomes row results in request order
     */
    public NpcPromotionOutcome(ProjectOutcome projectOutcome, List<ProjectOutcome> rowOutcomes) {
        this.projectOutcome = Objects.requireNonNull(projectOutcome, "projectOutcome");
        this.rowOutcomes = ImmutableValues.copyOf(rowOutcomes, "rowOutcomes");
    }

    /** @return the aggregate typed outcome with all row diagnostics in request order */
    public ProjectOutcome getProjectOutcome() {
        return projectOutcome;
    }

    /** @return the final coherent Project snapshot */
    public ProjectSnapshot getSnapshot() {
        return projectOutcome.getSnapshot();
    }

    /** @return the row outcomes in request order, all carrying the final snapshot */
    public List<ProjectOutcome> getRowOutcomes() {
        return rowOutcomes;
    }
}
