package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.NpcPromotionOutcome;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectEdit;
import com.asdasfa.jbs2bg.project.ProjectOperationContext;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetImportOutcome;
import com.asdasfa.jbs2bg.project.SourceLocation;

/**
 * Delegates lifecycle operations while supplying deterministic Project edit rejection or failure. Public so the
 * whole-Workbench suites and the per-Area suites in the Area packages share one refusing session.
 */
public final class RefusingEditSession implements ProjectSession {
    private final ProjectSession delegate;
    private boolean refuse;
    private boolean failed;

    /** Wraps a real session so fixture setup and snapshots retain production semantics. */
    public RefusingEditSession(ProjectSession delegate) {
        this.delegate = delegate;
    }

    /** Arms the requested outcome only after fixture creation through the public Project seam. */
    public void refuseEdits(boolean failure) {
        refuse = true;
        failed = failure;
    }

    /** {@inheritDoc} */
    @Override
    public ProjectSnapshot getSnapshot() {
        return delegate.getSnapshot();
    }

    /** {@inheritDoc} */
    @Override
    public ProjectOutcome newProject() {
        return delegate.newProject();
    }

    /** {@inheritDoc} */
    @Override
    public ProjectOutcome open(Path source, ProjectOperationContext context) {
        return delegate.open(source, context);
    }

    /** {@inheritDoc} */
    @Override
    public ProjectOutcome save(ProjectOperationContext context) {
        return delegate.save(context);
    }

    /** {@inheritDoc} */
    @Override
    public ProjectOutcome saveAs(Path target, ProjectOperationContext context) {
        return delegate.saveAs(target, context);
    }

    /** {@inheritDoc} */
    @Override
    public SliderPresetImportOutcome importSliderPresets(List<Path> sources, ProjectOperationContext context) {
        return delegate.importSliderPresets(sources, context);
    }

    /** {@inheritDoc} */
    @Override
    public ProjectOutcome refreshSettings() {
        return delegate.refreshSettings();
    }

    /** Preserves the Project while returning a structured diagnostic after rejection is armed. */
    @Override
    public ProjectOutcome apply(ProjectEdit edit) {
        if (!refuse)
            return delegate.apply(edit);
        List<ProjectDiagnostic> diagnostics = List.of(new ProjectDiagnostic("TEST_MORPH_REMOVE",
                DiagnosticSeverity.ERROR, new SourceLocation(Optional.empty(), Optional.of("custom-target"),
                OptionalInt.empty(), OptionalInt.empty()), "The target could not be removed."));
        return failed ? new FailedOutcome(delegate.getSnapshot(), diagnostics)
                : new RejectedOutcome(delegate.getSnapshot(), diagnostics);
    }

    /** Preserves the armed refusal for bulk NPC edits as well as single edits. */
    @Override
    public NpcPromotionOutcome promoteNpcs(List<NpcMorphAssignmentSnapshot> sources) {
        if (!refuse)
            return delegate.promoteNpcs(sources);
        ProjectOutcome refusal = apply(NpcMorphAssignmentEdits.addNpcs(sources));
        return new NpcPromotionOutcome(refusal, java.util.Collections.nCopies(sources.size(), refusal));
    }
}
