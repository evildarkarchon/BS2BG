package com.asdasfa.jbs2bg.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies transactional filesystem publication for the two production Settings filenames.
 */
final class SettingsPairPublisherTest {
    private static final Path FIXTURE_ROOT = Path.of("test-resources", "json-oracles", "settings");

    /**
     * Creates canonical replacement bytes from the permanent paired Settings fixtures.
     */
    private static SettingsJacksonAdapter.SettingsPairBytes replacementPair() {
        return SettingsJacksonAdapter.writePair(SettingsJacksonAdapter.readPair(
                FIXTURE_ROOT.resolve("standard.json"), FIXTURE_ROOT.resolve("uunp.json")));
    }

    /**
     * Requires the transaction namespace to be empty after complete publication or rollback.
     */
    private static void assertNoTransactions(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString()
                    .startsWith(".bs2bg-settings-stage-")));
        }
    }

    /**
     * Injects a failure while installing the later member and requires both prior destination bytes to return.
     *
     * @param directory isolated publication directory
     * @throws IOException when test setup or inspection fails
     */
    @Test
    void laterInstallFailureRollsBackBothPriorSettingsFiles(@TempDir Path directory) throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        byte[] priorStandard = "prior-standard".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] priorUunp = "prior-uunp".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(standard, priorStandard);
        Files.write(uunp, priorUunp);
        SettingsJacksonAdapter.SettingsPairBytes replacement = SettingsJacksonAdapter.writePair(
                SettingsJacksonAdapter.readPair(FIXTURE_ROOT.resolve("standard.json"),
                        FIXTURE_ROOT.resolve("uunp.json")));
        AtomicInteger moveCount = new AtomicInteger();

        IOException failure = assertThrows(IOException.class,
                () -> SettingsPairPublisher.publish(standard, uunp, replacement, (source, target) -> {
                    if (moveCount.incrementAndGet() == 4)
                        throw new IOException("injected later install failure");
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                }));

        assertEquals("injected later install failure", failure.getMessage());
        assertArrayEquals(priorStandard, Files.readAllBytes(standard));
        assertArrayEquals(priorUunp, Files.readAllBytes(uunp));
        try (var entries = Files.list(directory)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith(".bs2bg-settings-")));
        }
    }

    /**
     * Models an atomic move that changed the filesystem before reporting failure and requires rollback to use
     * durable transaction markers instead of post-return in-memory flags.
     *
     * @param directory isolated publication directory
     * @throws IOException when test setup or inspection fails
     */
    @Test
    void backupMoveFailureAfterItsSideEffectStillRestoresThePriorPair(@TempDir Path directory)
            throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        byte[] priorStandard = "prior-standard".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] priorUunp = "prior-uunp".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(standard, priorStandard);
        Files.write(uunp, priorUunp);
        SettingsJacksonAdapter.SettingsPairBytes replacement = replacementPair();
        AtomicInteger moveCount = new AtomicInteger();

        IOException failure = assertThrows(IOException.class,
                () -> SettingsPairPublisher.publish(standard, uunp, replacement, (source, target) -> {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    if (moveCount.incrementAndGet() == 1)
                        throw new IOException("injected post-move backup failure");
                }));

        assertEquals("injected post-move backup failure", failure.getMessage());
        assertArrayEquals(priorStandard, Files.readAllBytes(standard));
        assertArrayEquals(priorUunp, Files.readAllBytes(uunp));
        assertNoTransactions(directory);
    }

    /**
     * Models the later replacement becoming visible before its move reports failure and requires both backups
     * to restore the prior pair without leaving a journal behind.
     *
     * @param directory isolated publication directory
     * @throws IOException when test setup or inspection fails
     */
    @Test
    void installMoveFailureAfterItsSideEffectStillRestoresThePriorPair(@TempDir Path directory)
            throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        byte[] priorStandard = "prior-standard".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] priorUunp = "prior-uunp".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(standard, priorStandard);
        Files.write(uunp, priorUunp);
        AtomicInteger moveCount = new AtomicInteger();

        IOException failure = assertThrows(IOException.class,
                () -> SettingsPairPublisher.publish(standard, uunp, replacementPair(), (source, target) -> {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    if (moveCount.incrementAndGet() == 4)
                        throw new IOException("injected post-move install failure");
                }));

        assertEquals("injected post-move install failure", failure.getMessage());
        assertArrayEquals(priorStandard, Files.readAllBytes(standard));
        assertArrayEquals(priorUunp, Files.readAllBytes(uunp));
        assertNoTransactions(directory);
    }

    /**
     * Treats a commit-marker move that reports failure after its side effect as a completed publication instead
     * of rolling a committed pair back.
     *
     * @param directory isolated publication directory
     * @throws IOException when test setup or inspection fails
     */
    @Test
    void commitMoveFailureAfterItsSideEffectLeavesTheReplacementPairCommitted(@TempDir Path directory)
            throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        Files.writeString(standard, "prior-standard");
        Files.writeString(uunp, "prior-uunp");
        SettingsJacksonAdapter.SettingsPairBytes replacement = replacementPair();
        AtomicInteger moveCount = new AtomicInteger();

        SettingsPairPublisher.publish(standard, uunp, replacement, (source, target) -> {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            if (moveCount.incrementAndGet() == 5)
                throw new IOException("injected post-move commit failure");
        });

        assertEquals(5, moveCount.get());
        assertArrayEquals(replacement.standardUtf8(), Files.readAllBytes(standard));
        assertArrayEquals(replacement.uunpUtf8(), Files.readAllBytes(uunp));
        assertNoTransactions(directory);
    }

    /**
     * Fails the commit move before its side effect and the first rollback move, then requires restart recovery
     * to distrust the staged marker and restore the exact prior pair.
     *
     * @param directory isolated publication directory
     * @throws IOException when test setup or inspection fails
     */
    @Test
    void commitMoveAndRollbackFailurePreserveAnUncommittedRestartJournal(@TempDir Path directory)
            throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        byte[] priorStandard = Files.readAllBytes(FIXTURE_ROOT.resolve("standard.json"));
        byte[] priorUunp = Files.readAllBytes(FIXTURE_ROOT.resolve("uunp.json"));
        Files.write(standard, priorStandard);
        Files.write(uunp, priorUunp);
        AtomicInteger moveCount = new AtomicInteger();

        IOException failure = assertThrows(IOException.class,
                () -> SettingsPairPublisher.publish(standard, uunp, replacementPair(), (source, target) -> {
                    int operation = moveCount.incrementAndGet();
                    if (operation == 5)
                        throw new IOException("injected commit move failure");
                    if (operation == 6)
                        throw new IOException("injected rollback failure");
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                }));

        assertEquals("injected commit move failure", failure.getMessage());
        assertTrue(failure.getSuppressed().length > 0);
        Settings.InitializationResult recovered = Settings.initialize(directory);
        assertTrue(recovered.isSuccessful());
        assertEquals("SETTINGS_PUBLICATION_RECOVERED", recovered.getDiagnostics().get(0).getCode());
        assertArrayEquals(priorStandard, Files.readAllBytes(standard));
        assertArrayEquals(priorUunp, Files.readAllBytes(uunp));
        assertNoTransactions(directory);
    }

    /**
     * Preserves an unrecoverable rollback journal and proves the next production initialization restores the
     * exact prior pair before parsing it.
     *
     * @param directory isolated publication directory
     * @throws IOException when test setup or inspection fails
     */
    @Test
    void rollbackFailurePreservesTheJournalForRestartRecovery(@TempDir Path directory) throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        byte[] priorStandard = Files.readAllBytes(FIXTURE_ROOT.resolve("standard.json"));
        byte[] priorUunp = Files.readAllBytes(FIXTURE_ROOT.resolve("uunp.json"));
        Files.write(standard, priorStandard);
        Files.write(uunp, priorUunp);
        AtomicInteger moveCount = new AtomicInteger();

        IOException failure = assertThrows(IOException.class,
                () -> SettingsPairPublisher.publish(standard, uunp, replacementPair(), (source, target) -> {
                    int operation = moveCount.incrementAndGet();
                    if (operation == 4)
                        throw new IOException("injected later install failure");
                    if (operation == 5)
                        throw new IOException("injected rollback failure");
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                }));

        assertTrue(failure.getSuppressed().length > 0);
        try (var entries = Files.list(directory)) {
            assertEquals(1, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".bs2bg-settings-stage-")).count());
        }

        Settings.InitializationResult recovered = Settings.initialize(directory);

        assertTrue(recovered.isSuccessful());
        assertEquals("SETTINGS_PUBLICATION_RECOVERED", recovered.getDiagnostics().get(0).getCode());
        assertArrayEquals(priorStandard, Files.readAllBytes(standard));
        assertArrayEquals(priorUunp, Files.readAllBytes(uunp));
        assertNoTransactions(directory);
    }
}
