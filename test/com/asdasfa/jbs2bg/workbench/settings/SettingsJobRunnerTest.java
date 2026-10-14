package com.asdasfa.jbs2bg.workbench.settings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.SettingsLockHolderProbe;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.WorkbenchTestEnvironment;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/**
 * Verifies the windowless Settings job runner through its submission seam: it admits Settings effects to the shared
 * coordinator, runs their I/O against one explicit Settings store, and settles the feature on completion.
 */
class SettingsJobRunnerTest {

    @TempDir
    Path temporaryDirectory;

    /** Creates a deterministic coordinator whose callbacks publish inline in worker order. */
    private static JobCoordinator coordinator(ManualExecutor worker) {
        return new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The test observes prompt cancellation before prolonged feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
    }

    /** Reload cancellation while another process owns the lock keeps the Settings draft and feedback unchanged. */
    @Test
    void settingsReloadCanBeCancelledWhileWaitingForTheDirectoryLock() throws Exception {
        WorkbenchTestEnvironment fixture = WorkbenchTestEnvironment.initialized(temporaryDirectory);
        assertTrue(fixture.startup().isSuccessful());
        byte[] standardBefore = Files.readAllBytes(temporaryDirectory.resolve("settings.json"));
        byte[] uunpBefore = Files.readAllBytes(temporaryDirectory.resolve("settings_UUNP.json"));
        Path ready = temporaryDirectory.resolve("holder.ready");
        Path release = temporaryDirectory.resolve("holder.release");
        String javaName = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaName).toString();
        String testClassPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process holder = new ProcessBuilder(javaExecutable, "-cp", testClassPath,
                SettingsLockHolderProbe.class.getName(), temporaryDirectory.toString(),
                ready.toString(), release.toString()).redirectErrorStream(true).start();
        Thread reloadWorker = null;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready) && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertTrue(Files.exists(ready), "child process did not acquire the Settings lock");
            ManualExecutor worker = new ManualExecutor();
            JobCoordinator jobs = coordinator(worker);
            SettingsFeature feature = new SettingsFeature(fixture.settings(), fixture.startup());
            assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                    "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
            SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                    SettingsFeature.ReloadConfirmationEffect.class,
                    feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());
            SettingsFeature.ReloadEffect effect = assertInstanceOf(SettingsFeature.ReloadEffect.class,
                    feature.respondReload(confirmation.token(), SettingsFeature.ReloadDecision.DISCARD)
                            .effect().orElseThrow());
            AtomicReference<SettingsFeature.Frame> settledFrame = new AtomicReference<>();
            SettingsJobRunner runner = new SettingsJobRunner(feature, fixture.settings(), jobs,
                    (settled, update) -> settledFrame.set(update.frame()));
            assertTrue(runner.submit(effect));
            reloadWorker = worker.runNextAsync();
            long workerDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean waitingForLock = false;
            while (!waitingForLock && reloadWorker.isAlive() && System.nanoTime() < workerDeadline) {
                for (StackTraceElement frame : reloadWorker.getStackTrace()) {
                    if (frame.getClassName().equals("com.asdasfa.jbs2bg.data.SettingsDirectoryLock")
                            && frame.getMethodName().equals("acquire")) {
                        waitingForLock = true;
                        break;
                    }
                }
                if (!waitingForLock)
                    Thread.sleep(10);
            }
            assertTrue(waitingForLock, "Reload worker did not reach the blocking directory lock");

            AtomicReference<JobCoordinator.CancelResponse> cancelResponse = new AtomicReference<>();
            Thread canceller = new Thread(() -> cancelResponse.set(jobs.requestCancel()),
                    "settings-reload-canceller");
            canceller.start();
            canceller.join(TimeUnit.SECONDS.toMillis(2));
            boolean promptCancellation = !canceller.isAlive();
            if (!promptCancellation) {
                // Release the child lock so an interrupted Windows file-lock call cannot strand the test process.
                Files.writeString(release, "release");
                canceller.join(TimeUnit.SECONDS.toMillis(5));
            }
            assertTrue(promptCancellation, "requestCancel blocked while another process held the Settings lock");
            assertEquals(JobCoordinator.CancelResponse.ACCEPTED, cancelResponse.get());
            reloadWorker.join(TimeUnit.SECONDS.toMillis(5));
            assertFalse(reloadWorker.isAlive(), "cancelled Reload remained blocked on the directory lock");
            assertTrue(holder.isAlive(), "Reload only cancelled after the other process released its lock");
            assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            SettingsFeature.Frame frame = settledFrame.get();
            assertNotNull(frame);
            assertTrue(frame.dirty());
            assertEquals("2.0", frame.editor().orElseThrow().multiplier());
            assertTrue(frame.notices().stream().noneMatch(notice ->
                    notice.code().equals("SETTINGS_LOCK_FAILED")));
            assertArrayEquals(standardBefore, Files.readAllBytes(temporaryDirectory.resolve("settings.json")));
            assertArrayEquals(uunpBefore, Files.readAllBytes(temporaryDirectory.resolve("settings_UUNP.json")));
        } finally {
            if (reloadWorker != null && reloadWorker.isAlive()) {
                reloadWorker.interrupt();
                reloadWorker.join(TimeUnit.SECONDS.toMillis(5));
            }
            Files.writeString(release, "release");
            if (!holder.waitFor(10, TimeUnit.SECONDS)) {
                holder.destroyForcibly();
                holder.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * An explicit retry of a failed Save recaptures the current draft under a fresh effect token, tells the listener
     * which effect replaced which (the shell moves a pending close onto it), and persists to the runner's store.
     */
    @Test
    void retryOfAFailedSaveRecapturesTheDraftAndReportsTheReplacement() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("settings"));
        WorkbenchTestEnvironment fixture = WorkbenchTestEnvironment.initialized(directory);
        assertTrue(fixture.startup().isSuccessful());
        Settings store = fixture.settings();
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = coordinator(worker);
        SettingsFeature feature = new SettingsFeature(store, fixture.startup());
        List<SettingsFeature.Effect> recaptured = new ArrayList<>();
        List<SettingsFeature.Update> completed = new ArrayList<>();
        SettingsJobRunner runner = new SettingsJobRunner(feature, store, jobs,
                new SettingsJobRunner.Listener() {
                    @Override
                    public void completed(SettingsFeature.Effect effect, SettingsFeature.Update update) {
                        completed.add(update);
                    }

                    @Override
                    public void recaptured(SettingsFeature.Effect previous, SettingsFeature.Effect replacement) {
                        recaptured.add(previous);
                        recaptured.add(replacement);
                    }
                });
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.SaveEffect save = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());
        assertTrue(runner.submit(save));
        Files.deleteIfExists(directory.resolve("settings.json"));
        Files.deleteIfExists(directory.resolve("settings_UUNP.json"));
        Files.deleteIfExists(directory.resolve(".bs2bg-settings.lock"));
        Files.delete(directory);

        worker.runNextAsync().join();
        JobCoordinator.Attempt failed = jobs.frame().attempt().orElseThrow();
        assertEquals(SettingsJobRunner.SAVE_OPERATION, failed.operation().name());
        assertEquals(JobCoordinator.Lifecycle.FAILED, failed.lifecycle());
        assertTrue(jobs.isRetryAvailable(failed.id()));
        assertTrue(recaptured.isEmpty());

        Files.createDirectories(directory);
        assertTrue(jobs.retry(failed.id()).admitted());
        assertEquals(2, recaptured.size());
        assertEquals(save, recaptured.get(0));
        SettingsFeature.SaveEffect replacement = assertInstanceOf(SettingsFeature.SaveEffect.class,
                recaptured.get(1));
        assertNotEquals(save.token(), replacement.token());
        worker.runNextAsync().join();

        assertEquals(JobCoordinator.Lifecycle.COMPLETED, jobs.frame().attempt().orElseThrow().lifecycle());
        assertEquals(SettingsFeature.OutcomeKind.SAVED, completed.getLast().frame().outcome());
        assertEquals(2f, store.getMultiplier("Waist"));
    }
}
