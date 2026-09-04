package com.asdasfa.jbs2bg.project;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Truthful Project-operation phase with either measured real units or indeterminate progress.
 */
public record ProjectOperationProgress(String phase, OptionalLong completedUnits, OptionalLong totalUnits) {

    /** Creates an indeterminate phase without inventing a percentage. */
    public static ProjectOperationProgress indeterminate (String phase){
        return new ProjectOperationProgress(phase, OptionalLong.empty(), OptionalLong.empty());
    }

    /** Creates measured progress whose percentage can be derived from real adapter units. */
    public static ProjectOperationProgress determinate (String phase,long completedUnits, long totalUnits){
        return new ProjectOperationProgress(phase, OptionalLong.of(completedUnits), OptionalLong.of(totalUnits));
    }

    /** Rejects blank phases, partial measurements, and impossible unit counts. */
    public ProjectOperationProgress {
        phase = Objects.requireNonNull(phase, "phase").trim();
        if (phase.isEmpty())
            throw new IllegalArgumentException("phase must not be blank");
        Objects.requireNonNull(completedUnits, "completedUnits");
        Objects.requireNonNull(totalUnits, "totalUnits");
        if (completedUnits.isPresent() != totalUnits.isPresent())
            throw new IllegalArgumentException("completedUnits and totalUnits must both be present or absent");
        if (completedUnits.isPresent()) {
            long completed = completedUnits.orElseThrow();
            long total = totalUnits.orElseThrow();
            if (completed < 0 || total <= 0 || completed > total)
                throw new IllegalArgumentException("measured progress must satisfy 0 <= completed <= total");
        }
    }
}
