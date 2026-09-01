package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;

/**
 * Verifies complete transactional publication through the JavaFX-independent Output artifact seam.
 */
final class OutputArtifactPublisherTest {

    /** Batch publication writes every accepted artifact byte and leaves unrelated files untouched. */
    @Test
    void publishesTheCompleteAcceptedArtifactSetWithoutDeletingUnrelatedFiles(@TempDir Path targetDirectory)
            throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha", "Zulu");
        Path unrelated = targetDirectory.resolve("stale.json");
        Files.writeString(unrelated, "preserved", StandardCharsets.UTF_8);

        byte[] exposed = output.getArtifacts().getFirst().getBytes();
        exposed[0] = 'x';
        OutputArtifactPublisher.publishAll(targetDirectory, output.getArtifacts());

        for (OutputArtifact artifact : output.getArtifacts())
            assertArrayEquals(artifact.getBytes(), Files.readAllBytes(targetDirectory.resolve(artifact.getFileName())));
        assertEquals("preserved", Files.readString(unrelated, StandardCharsets.UTF_8));
    }

    /** One conflicting destination rejects the complete batch before any prior destination is changed. */
    @Test
    void preflightsEveryTargetConflictBeforePublishing(@TempDir Path targetDirectory) throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha", "Zulu");
        Path prior = targetDirectory.resolve("templates.ini");
        Files.writeString(prior, "prior", StandardCharsets.UTF_8);
        Files.createDirectory(targetDirectory.resolve("Zulu.json"));

        assertThrows(IOException.class,
                () -> OutputArtifactPublisher.publishAll(targetDirectory, output.getArtifacts()));

        assertEquals("prior", Files.readString(prior, StandardCharsets.UTF_8));
        assertNoTransactionDirectory(targetDirectory);
    }

    /** Unsafe Windows leaves and case-insensitive collisions are rejected before staging begins. */
    @Test
    void rejectsUnsafeAndCaseInsensitiveCollidingArtifactNames(@TempDir Path targetDirectory) {
        OutputArtifact unsafe = new TestArtifact("NUL.txt", "unsafe");
        OutputArtifact alpha = new TestArtifact("Alpha.json", "one");
        OutputArtifact colliding = new TestArtifact("alpha.JSON", "two");

        assertThrows(IOException.class,
                () -> OutputArtifactPublisher.publishAll(targetDirectory, List.of(unsafe)));
        assertThrows(IOException.class,
                () -> OutputArtifactPublisher.publishAll(targetDirectory, List.of(alpha, colliding)));
    }

    /** Accepted cancellation after complete staging preserves prior destinations and removes staged bytes. */
    @Test
    void cancellationBeforeCommitPreservesEveryDestination(@TempDir Path targetDirectory) throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha");
        Path prior = targetDirectory.resolve("templates.ini");
        Files.writeString(prior, "prior", StandardCharsets.UTF_8);
        AtomicInteger staged = new AtomicInteger();

        assertThrows(CancellationException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                output.getArtifacts(), new OutputArtifactPublisher.PublicationContext() {
                    @Override
                    public void checkCancellation() {
                        // This test lets all cancellable preflight and staging work finish.
                    }

                    @Override
                    public void reportStaged(long completedArtifacts, long totalArtifacts) {
                        staged.set(Math.toIntExact(completedArtifacts));
                    }

                    @Override
                    public boolean beginCommit() {
                        return false;
                    }
                }));

        assertEquals(output.getArtifacts().size(), staged.get());
        assertEquals("prior", Files.readString(prior, StandardCharsets.UTF_8));
        assertNoTransactionDirectory(targetDirectory);
    }

    /** A failure after one backup restores every pre-command destination and removes staged bytes. */
    @Test
    void atomicPublicationFailureRollsBackTheCompleteBatch(@TempDir Path targetDirectory) throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha");
        for (OutputArtifact artifact : output.getArtifacts())
            Files.writeString(targetDirectory.resolve(artifact.getFileName()),
                    "prior " + artifact.getFileName(), StandardCharsets.UTF_8);

        AtomicInteger moves = new AtomicInteger();
        assertThrows(IOException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                output.getArtifacts(), OutputArtifactPublisher.PublicationContext.nonCancellable(),
                (source, target) -> {
                    if (moves.incrementAndGet() == 2)
                        throw new IOException("injected move failure");
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                }));

        assertEquals(3, moves.get());
        for (OutputArtifact artifact : output.getArtifacts())
            assertEquals("prior " + artifact.getFileName(),
                    Files.readString(targetDirectory.resolve(artifact.getFileName()), StandardCharsets.UTF_8));
        assertNoTransactionDirectory(targetDirectory);
    }

    /** A move provider that reports failure after installing bytes still triggers complete rollback. */
    @Test
    void postSideEffectInstallFailureStillRestoresEveryPriorDestination(@TempDir Path targetDirectory)
            throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha");
        for (OutputArtifact artifact : output.getArtifacts())
            Files.writeString(targetDirectory.resolve(artifact.getFileName()),
                    "prior " + artifact.getFileName(), StandardCharsets.UTF_8);
        int backupMoves = output.getArtifacts().size();
        AtomicInteger moves = new AtomicInteger();

        assertThrows(IOException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                output.getArtifacts(), OutputArtifactPublisher.PublicationContext.nonCancellable(),
                (source, target) -> {
                    int move = moves.incrementAndGet();
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                    if (move == backupMoves + 1)
                        throw new IOException("injected post-side-effect failure");
                }));

        for (OutputArtifact artifact : output.getArtifacts())
            assertEquals("prior " + artifact.getFileName(),
                    Files.readString(targetDirectory.resolve(artifact.getFileName()), StandardCharsets.UTF_8));
        assertNoTransactionDirectory(targetDirectory);
    }

    /** Generates one accepted output value with the requested canonical Slider Preset order. */
    private static ProjectGeneratedOutput generatedOutput(String... names) {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        for (String name : names)
            session.apply(SliderPresetEdits.create(name));
        return ProjectOutputFormatter.generate(session.getSnapshot(), false);
    }

    /** Requires every command-owned transaction directory to be cleaned after success, failure, or cancellation. */
    private static void assertNoTransactionDirectory(Path targetDirectory) throws IOException {
        try (var entries = Files.list(targetDirectory)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".bs2bg-output-stage-")).count());
        }
    }

    /** Minimal immutable artifact used only to exercise publisher-owned filename preflight. */
    private record TestArtifact(String fileName, byte[] bytes) implements OutputArtifact {
        /** Creates a UTF-8 test artifact from independent literal text. */
        private TestArtifact(String fileName, String text) {
            this(fileName, text.getBytes(StandardCharsets.UTF_8));
        }

        /** Defensively owns test bytes just like production artifacts. */
        private TestArtifact {
            bytes = bytes.clone();
        }

        /** {@inheritDoc} */
        @Override
        public String getFileName() {
            return fileName;
        }

        /** {@inheritDoc} */
        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        /** {@inheritDoc} */
        @Override
        public String getText() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
