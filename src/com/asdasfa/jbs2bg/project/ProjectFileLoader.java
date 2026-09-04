package com.asdasfa.jbs2bg.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Owns Project filesystem loading while delegating JSON policy to the streaming
 * adapter. The detached result is published only by {@link ProjectSession}.
 */
final class ProjectFileLoader {

    private ProjectFileLoader() {
    }

    /**
     * Reads and validates one complete Project candidate from the production filesystem.
     *
     * @param source Project path to inspect and stream-parse
     * @return detached Project content and ordered recovery diagnostics
     * @throws ProjectJacksonAdapter.ProjectFormatException when reading, parsing,
     *                                                      validation, or resource-limit enforcement fails
     */
    static LoadedProject load(Path source) {
        return load(source, ProjectOperationContext.nonCancellable());
    }

    /**
     * Reads and validates one complete candidate while reporting real adapter phases.
     *
     * @param source  Project path to inspect and stream-parse
     * @param context operation context retained for this synchronous load
     * @return detached Project content and ordered recovery diagnostics
     */
    static LoadedProject load(Path source, ProjectOperationContext context) {
        ProjectOperationContext operation = Objects.requireNonNull(context, "context");
        operation.report(ProjectOperationProgress.indeterminate("Inspecting Project"));
        operation.checkCancellation();
        ProjectJacksonAdapter.Candidate candidate = ProjectJacksonAdapter.read(source, operation);
        return new LoadedProject(candidate.snapshot(), candidate.diagnostics());
    }

    /**
     * Complete detached load result for the session publication boundary.
     */
    static final class LoadedProject {
        private final ProjectSnapshot snapshot;
        private final List<ProjectDiagnostic> diagnostics;

        /**
         * Creates one immutable loader result.
         *
         * @param snapshot    validated detached Project candidate
         * @param diagnostics ordered recovery diagnostics
         */
        LoadedProject(ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.diagnostics = List.copyOf(diagnostics);
        }

        /**
         * @return validated detached Project candidate
         */
        ProjectSnapshot getSnapshot() {
            return snapshot;
        }

        /**
         * @return immutable ordered recovery diagnostics
         */
        List<ProjectDiagnostic> getDiagnostics() {
            return diagnostics;
        }
    }
}
