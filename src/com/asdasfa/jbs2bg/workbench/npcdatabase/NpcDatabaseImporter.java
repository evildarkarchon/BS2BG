package com.asdasfa.jbs2bg.workbench.npcdatabase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/**
 * Owns NPC Database imports on the central job path. Worker reads remain detached;
 * the serialized completion callback publishes only fully staged sources to the catalog.
 */
public final class NpcDatabaseImporter {
    private final NpcDatabaseFeature feature;
    private final JobCoordinator jobs;
    private final Consumer<NpcDatabaseFeature.Frame> onPublication;

    /**
     * Creates one window-scoped importer over the shared job coordinator.
     *
     * @param feature NPC Database catalog mutated only on the publication lane
     * @param jobs application-wide job coordinator
     * @param onPublication renders a complete immutable catalog frame after source commits
     */
    public NpcDatabaseImporter(NpcDatabaseFeature feature, JobCoordinator jobs,
                               Consumer<NpcDatabaseFeature.Frame> onPublication) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.onPublication = Objects.requireNonNull(onPublication, "onPublication");
    }

    /**
     * Captures ordered source paths and admits one retryable central job.
     *
     * @param sources nonempty selected source paths
     * @return admission result from the shared coordinator
     * @throws IllegalArgumentException when no source is selected
     */
    public JobCoordinator.Admission submit(List<Path> sources) {
        List<Path> captured = Objects.requireNonNull(sources, "sources").stream()
                .map(path -> Objects.requireNonNull(path, "source").toAbsolutePath().normalize()).toList();
        if (captured.isEmpty())
            throw new IllegalArgumentException("NPC Database sources must not be empty");
        return jobs.submit(submission(captured));
    }

    /** Captures a retryable submission without retaining a chooser or mutable result list. */
    private JobCoordinator.Submission<List<NpcDatabaseSourceReader.ReadResult>> submission(List<Path> sources) {
        List<Path> captured = List.copyOf(sources);
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Import NPC Database Sources",
                captured.stream().map(Path::toString).toList(), List.of("NPC Database"), Optional.empty(),
                JobCoordinator.ConsistencyClass.NPC_DATABASE);
        return new JobCoordinator.Submission<>(operation,
                context -> readSources(captured, context),
                (attempt, result) -> result.value().ifPresent(results -> {
                    if (results.isEmpty())
                        return;
                    // Completion runs on the serialized publication lane, after the worker has fixed the source set.
                    feature.clearDiagnostics();
                    for (NpcDatabaseSourceReader.ReadResult source : results)
                        feature.commitSource(source);
                    onPublication.accept(feature.frame());
                }), Optional.of(() -> submission(captured)));
    }

    /**
     * Reads each source transactionally and retains results completed before the next cancellation safe point.
     * A progress report follows admission of a complete source, so observers can cancel at a truthful count.
     *
     * @param sources captured source files in chooser order
     * @param context coordinator cancellation and progress boundary
     * @return terminal result whose cancelled value still carries complete prior sources for publication
     */
    private static JobCoordinator.Result<List<NpcDatabaseSourceReader.ReadResult>> readSources(
            List<Path> sources, JobCoordinator.Context context) {
        List<NpcDatabaseSourceReader.ReadResult> completed = new ArrayList<>();
        List<JobCoordinator.Diagnostic> diagnostics = new ArrayList<>();
        List<String> effects = new ArrayList<>();
        context.report(JobCoordinator.Progress.determinate("Reading NPC Database sources", 0, sources.size()));
        for (Path source : sources) {
            try {
                context.checkCancellation();
                NpcDatabaseSourceReader.ReadResult read = NpcDatabaseSourceReader.read(source,
                        context::cancellationRequested);
                context.checkCancellation();
                // This list addition is the safe boundary between the current source and later cancellation.
                completed.add(read);
                for (NpcDatabaseSourceReader.Diagnostic diagnostic : read.diagnostics())
                    diagnostics.add(jobDiagnostic(diagnostic));
                if (read.status() == NpcDatabaseSourceReader.Status.ACCEPTED)
                    effects.add("Imported " + source);
                context.report(JobCoordinator.Progress.determinate("Reading NPC Database sources",
                        completed.size(), sources.size()));
            } catch (CancellationException exception) {
                return JobCoordinator.Result.cancelled(List.copyOf(completed),
                        "NPC Database import cancelled after " + completed.size() + " complete sources.",
                        effects, diagnostics);
            }
        }
        if (!context.beginCommit("Publishing NPC Database sources"))
            return JobCoordinator.Result.cancelled(List.copyOf(completed),
                    "NPC Database import cancelled after " + completed.size() + " complete sources.",
                    effects, diagnostics);
        if (effects.isEmpty())
            return JobCoordinator.Result.failed(List.copyOf(completed),
                    "No NPC Database source could be imported.", diagnostics);
        if (!diagnostics.isEmpty())
            return JobCoordinator.Result.completedWithIssues(List.copyOf(completed),
                    "NPC Database imported " + effects.size() + " sources with " + diagnostics.size()
                            + " source diagnostics.", effects, diagnostics);
        return JobCoordinator.Result.completed(List.copyOf(completed),
                "NPC Database imported " + effects.size() + " sources.", effects, diagnostics);
    }

    /** Converts source-local line evidence into durable Activity details. */
    private static JobCoordinator.Diagnostic jobDiagnostic(NpcDatabaseSourceReader.Diagnostic diagnostic) {
        String location = diagnostic.source().toString()
                + (diagnostic.line().isPresent() ? ":" + diagnostic.line().orElseThrow() : "");
        return new JobCoordinator.Diagnostic(diagnostic.code(), diagnostic.message(), Optional.of(location));
    }
}
