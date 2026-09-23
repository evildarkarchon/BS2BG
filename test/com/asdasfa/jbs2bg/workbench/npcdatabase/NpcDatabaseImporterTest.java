package com.asdasfa.jbs2bg.workbench.npcdatabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/** Verifies the NPC Database import through its central job admission seam. */
class NpcDatabaseImporterTest {

    @TempDir
    Path temporaryDirectory;

    /** Creates a deterministic coordinator whose callbacks publish in worker order. */
    private static JobCoordinator coordinator(ManualExecutor worker) {
        return new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // These tests settle cancellation before prolonged status is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected job callback failure", failure);
        });
    }

    /** Cancellation and shutdown after reported source one keep that source and exclude the current source. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationOrShutdownAfterOneCompleteSourceKeepsOnlyThatSource(boolean shutdown) throws Exception {
        Path first = temporaryDirectory.resolve("first.txt");
        Path second = temporaryDirectory.resolve("second.txt");
        Files.writeString(first, "Master.esm | Amber | First | NordRace | 000001\n");
        Files.writeString(second, "Master.esm | Beta | Second | NordRace | 000002\n");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        NpcDatabaseFeature feature = new NpcDatabaseFeature();
        List<NpcDatabaseFeature.Frame> publications = new ArrayList<>();
        boolean[] cancellationRequested = {false};
        jobs.observe(frame -> frame.attempt().ifPresent(attempt -> {
            if (!cancellationRequested[0] && attempt.progress().completedUnits().equals(Optional.of(1L))) {
                cancellationRequested[0] = true;
                if (shutdown)
                    jobs.requestShutdown();
                else
                    jobs.requestCancel();
            }
        }));
        NpcDatabaseImporter importer = new NpcDatabaseImporter(feature, jobs, publications::add);

        assertTrue(importer.submit(List.of(first, second)).admitted());
        Thread importWorker = worker.runNextAsync();
        importWorker.join();

        assertEquals(JobCoordinator.Lifecycle.CANCELLED, jobs.frame().attempt().orElseThrow().lifecycle());
        assertEquals(shutdown, jobs.frame().shutdownReady());
        assertEquals(List.of("Imported " + first.toAbsolutePath().normalize()),
                jobs.frame().attempt().orElseThrow().effectsCommitted());
        assertEquals(List.of("Amber"), feature.frame().rows().stream().map(row -> row.getName()).toList());
        assertEquals(List.of(first.toAbsolutePath().normalize()), feature.frame().sources().stream()
                .map(NpcDatabaseFeature.Source::path).toList());
        assertEquals(1, publications.size());
    }
}
