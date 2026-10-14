package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;
import java.util.Optional;

/**
 * The one immutable input the Workbench shell fans out to every Area on each Project frame and each job frame. An
 * Area renders its own feature frame plus this context and nothing else, so a control that reflects another Area's
 * state (BodySlide import needs live Settings, Generate needs live Settings and an idle coordinator) has exactly one
 * writer, and its state no longer depends on which render ran last.
 *
 * @param project                 the Project frame this context was built from
 * @param resetTemplates          whether accepting {@code project} resets Templates browsing state; true only on the
 *                                Project frame that follows a New, an Open, or a completed Open Project job
 * @param resetMorphs             the same reset-on-Open decision for Morphs
 * @param projectMutationsBlocked whether the rendered job frame blocks Project and Settings mutations; this is the
 *                                dispatch pipeline's {@link DispatchPipeline.Gate#PROJECT_MUTATIONS} gate
 * @param anyJobActive            whether any job is running or shutdown was requested, read from the coordinator when
 *                                the context was built; source imports, Save/Reload and exports wait on it
 * @param settingsLiveAvailable   whether Settings loaded or saved successfully, so Project work may consume them
 * @param activeOperation         the Project lifecycle command currently being dispatched, if any
 */
public record WorkbenchContext(WorkbenchProjectFlow.Frame project, boolean resetTemplates, boolean resetMorphs,
                               boolean projectMutationsBlocked, boolean anyJobActive, boolean settingsLiveAvailable,
                               Optional<WorkbenchProjectFlow.Intent> activeOperation) {

    /** Requires the Project frame and the active-operation slot. */
    public WorkbenchContext {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(activeOperation, "activeOperation");
    }

    /**
     * @return whether accepting {@link #project()} resets Output; generated text belongs to the Project it was
     *         generated from, so it resets whenever Templates or Morphs reset
     */
    public boolean resetOutput() {
        return resetTemplates || resetMorphs;
    }
}
