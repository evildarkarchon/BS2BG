package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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

    /** Resolves the exact Java executable running the Maven test process. */
    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    /** Returns Surefire's complete test class path, falling back to the current JVM class path. */
    private static String testClassPath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }

    /** Reads completed child output for assertion diagnostics. */
    private static String readOutput(Process process) throws IOException {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Batch publication writes every accepted artifact byte and leaves unrelated files untouched. */
    @Test
    void publishesTheCompleteAcceptedArtifactSetWithoutDeletingUnrelatedFiles(@TempDir Path targetDirectory)
            throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha", "Zulu");
        Path unrelated = targetDirectory.resolve("stale.json");
        Files.writeString(unrelated, "preserved");

        byte[] exposed = output.getArtifacts().getFirst().getBytes();
        exposed[0] = 'x';
        OutputArtifactPublisher.publishAll(targetDirectory, output.getArtifacts());

        for (OutputArtifact artifact : output.getArtifacts())
            assertArrayEquals(artifact.getBytes(), Files.readAllBytes(targetDirectory.resolve(artifact.getFileName())));
        assertEquals("preserved", Files.readString(unrelated));
    }

    /** One conflicting destination rejects the complete batch before any prior destination is changed. */
    @Test
    void preflightsEveryTargetConflictBeforePublishing(@TempDir Path targetDirectory) throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha", "Zulu");
        Path prior = targetDirectory.resolve("templates.ini");
        Files.writeString(prior, "prior");
        Files.createDirectory(targetDirectory.resolve("Zulu.json"));

        assertThrows(IOException.class,
                () -> OutputArtifactPublisher.publishAll(targetDirectory, output.getArtifacts()));

        assertEquals("prior", Files.readString(prior));
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

    /** Unicode names that Java compares case-insensitively share one Windows destination identity. */
    @Test
    void rejectsUnicodeCaseInsensitiveArtifactCollisions(@TempDir Path targetDirectory) {
        OutputArtifact ascii = new TestArtifact("s.json", "one");
        OutputArtifact longS = new TestArtifact("\u017f.json", "two");

        assertEquals(0, String.CASE_INSENSITIVE_ORDER.compare(ascii.getFileName(), longS.getFileName()));
        assertThrows(IOException.class,
                () -> OutputArtifactPublisher.publishAll(targetDirectory, List.of(ascii, longS)));
    }

    /** The inter-process lock identity is reserved case-insensitively before any transaction move can begin. */
    @Test
    void rejectsOutputLockIdentityDuringPreflight(@TempDir Path targetDirectory) {
        OutputArtifact lockCollision = new TestArtifact(".BS2BG-OUTPUT.LOCK", "replacement");
        AtomicInteger moves = new AtomicInteger();

        assertThrows(IOException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                List.of(lockCollision), OutputArtifactPublisher.PublicationContext.nonCancellable(),
                (source, target) -> {
                    moves.incrementAndGet();
                    throw new IOException("reserved lock identity reached transaction move");
                }));

        assertEquals(0, moves.get());
    }

    /** The transaction-directory namespace is reserved before an artifact can create recovery-shaped state. */
    @Test
    void rejectsOutputStagingPrefixDuringPreflight(@TempDir Path targetDirectory) {
        OutputArtifact stageCollision = new TestArtifact(".bs2bg-output-stage-user.json", "replacement");
        AtomicInteger moves = new AtomicInteger();

        assertThrows(IOException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                List.of(stageCollision), OutputArtifactPublisher.PublicationContext.nonCancellable(),
                (source, target) -> {
                    moves.incrementAndGet();
                    throw new IOException("reserved staging namespace reached transaction move");
                }));

        assertEquals(0, moves.get());
    }

    /** Accepted cancellation after complete staging preserves prior destinations and removes staged bytes. */
    @Test
    void cancellationBeforeCommitPreservesEveryDestination(@TempDir Path targetDirectory) throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha");
        Path prior = targetDirectory.resolve("templates.ini");
        Files.writeString(prior, "prior");
        AtomicInteger staged = new AtomicInteger();

        assertThrows(CancellationException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                output.getArtifacts(), new OutputArtifactPublisher.PublicationContext() {
                    @Override
                    public void checkCancellation() {
                        // This test lets all cancellable preflight and staging work finish.
                    }

                    @Override
                    public void beginStaging(long totalArtifacts) {
                        assertEquals(output.getArtifacts().size(), totalArtifacts);
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
        assertEquals("prior", Files.readString(prior));
        assertNoTransactionDirectory(targetDirectory);
    }

    /** Backup preparation never vacates a live destination before its atomic replacement is ready. */
    @Test
    void liveDestinationsRemainPresentUntilReplacementIsAtomicallyInstalled(@TempDir Path targetDirectory)
            throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha");
        List<Path> destinations = output.getArtifacts().stream()
                .map(artifact -> targetDirectory.resolve(artifact.getFileName())).toList();
        for (Path destination : destinations)
            Files.writeString(destination, "prior " + destination.getFileName());
        AtomicInteger moves = new AtomicInteger();

        IOException failure = assertThrows(IOException.class, () -> OutputArtifactPublisher.publishAll(
                targetDirectory, output.getArtifacts(), OutputArtifactPublisher.PublicationContext.nonCancellable(),
                (source, target) -> {
                    if (moves.incrementAndGet() == 2) {
                        for (Path destination : destinations) {
                            assertTrue(Files.isRegularFile(destination),
                                    () -> "Live destination was vacated before replacement: " + destination);
                        }
                        throw new IOException("injected later install failure");
                    }
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                }));

        assertEquals("injected later install failure", failure.getMessage());
        for (Path destination : destinations)
            assertEquals("prior " + destination.getFileName(), Files.readString(destination));
        assertNoTransactionDirectory(targetDirectory);
    }

    /** A later replacement failure restores every pre-command destination and removes staged bytes. */
    @Test
    void atomicPublicationFailureRollsBackTheCompleteBatch(@TempDir Path targetDirectory) throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha");
        for (OutputArtifact artifact : output.getArtifacts())
            Files.writeString(targetDirectory.resolve(artifact.getFileName()),
                    "prior " + artifact.getFileName());

        AtomicInteger moves = new AtomicInteger();
        assertThrows(IOException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                output.getArtifacts(), OutputArtifactPublisher.PublicationContext.nonCancellable(),
                (source, target) -> {
                    if (moves.incrementAndGet() == 2)
                        throw new IOException("injected move failure");
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                }));

        assertEquals(3, moves.get());
        for (OutputArtifact artifact : output.getArtifacts())
            assertEquals("prior " + artifact.getFileName(),
                    Files.readString(targetDirectory.resolve(artifact.getFileName())));
        assertNoTransactionDirectory(targetDirectory);
    }

    /** A move provider that reports failure after installing bytes still triggers complete rollback. */
    @Test
    void postSideEffectInstallFailureStillRestoresEveryPriorDestination(@TempDir Path targetDirectory)
            throws Exception {
        ProjectGeneratedOutput output = generatedOutput("Alpha");
        for (OutputArtifact artifact : output.getArtifacts())
            Files.writeString(targetDirectory.resolve(artifact.getFileName()),
                    "prior " + artifact.getFileName());
        AtomicInteger moves = new AtomicInteger();

        assertThrows(IOException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                output.getArtifacts(), OutputArtifactPublisher.PublicationContext.nonCancellable(),
                (source, target) -> {
                    int move = moves.incrementAndGet();
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    if (move == 1)
                        throw new IOException("injected post-side-effect failure");
                }));

        for (OutputArtifact artifact : output.getArtifacts())
            assertEquals("prior " + artifact.getFileName(),
                    Files.readString(targetDirectory.resolve(artifact.getFileName())));
        assertNoTransactionDirectory(targetDirectory);
    }

    /**
     * A later publication recovers the complete prior batch after a child process stops between replacements.
     *
     * @param targetDirectory isolated Output destination shared with the interrupted child process
     * @throws Exception when process coordination, publication, or inspection fails
     */
    @Test
    void nextPublicationRecoversPriorBatchAfterProcessStopsBetweenReplacements(@TempDir Path targetDirectory)
            throws Exception {
        Path templates = targetDirectory.resolve("templates.ini");
        Path newlyInstalled = targetDirectory.resolve("newly-installed.txt");
        Path morphs = targetDirectory.resolve("morphs.ini");
        Files.writeString(templates, "prior-templates");
        Files.writeString(morphs, "prior-morphs");
        Process child = null;
        try {
            child = new ProcessBuilder(javaExecutable(), "-cp", testClassPath(),
                    OutputPublicationInterruptionProbe.class.getName(), targetDirectory.toString())
                    .redirectErrorStream(true)
                    .start();

            assertTrue(child.waitFor(Duration.ofSeconds(10)), "interruption probe did not stop");
            assertEquals(OutputPublicationInterruptionProbe.INTERRUPTED_EXIT_CODE, child.exitValue(),
                    readOutput(child));
            assertEquals("replacement-templates", Files.readString(templates));
            assertEquals("replacement-new", Files.readString(newlyInstalled));
            assertEquals("prior-morphs", Files.readString(morphs));

            OutputArtifactPublisher.publishAll(targetDirectory,
                    List.of(new TestArtifact("unrelated.txt", "next-publication")));

            assertEquals("prior-templates", Files.readString(templates));
            assertFalse(Files.exists(newlyInstalled));
            assertEquals("prior-morphs", Files.readString(morphs));
            assertEquals("next-publication", Files.readString(targetDirectory.resolve("unrelated.txt")));
            assertNoTransactionDirectory(targetDirectory);
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(Duration.ofSeconds(10));
            }
        }
    }

    /**
     * An already accepted cancellation leaves an interrupted prior transaction untouched for a later retry.
     *
     * @param targetDirectory isolated Output destination shared with the interrupted child process
     * @throws Exception when process coordination, cancellation, or inspection fails
     */
    @Test
    void acceptedCancellationDoesNotRecoverInterruptedPriorTransaction(@TempDir Path targetDirectory)
            throws Exception {
        Path templates = targetDirectory.resolve("templates.ini");
        Path newlyInstalled = targetDirectory.resolve("newly-installed.txt");
        Path morphs = targetDirectory.resolve("morphs.ini");
        Files.writeString(templates, "prior-templates");
        Files.writeString(morphs, "prior-morphs");
        Process child = null;
        try {
            child = new ProcessBuilder(javaExecutable(), "-cp", testClassPath(),
                    OutputPublicationInterruptionProbe.class.getName(), targetDirectory.toString())
                    .redirectErrorStream(true)
                    .start();
            assertTrue(child.waitFor(Duration.ofSeconds(10)), "interruption probe did not stop");
            assertEquals(OutputPublicationInterruptionProbe.INTERRUPTED_EXIT_CODE, child.exitValue(),
                    readOutput(child));
            AtomicInteger cancellationChecks = new AtomicInteger();

            assertThrows(CancellationException.class, () -> OutputArtifactPublisher.publishAll(targetDirectory,
                    List.of(new TestArtifact("unrelated.txt", "must-not-stage")),
                    new OutputArtifactPublisher.PublicationContext() {
                        @Override
                        public void checkCancellation() {
                            cancellationChecks.incrementAndGet();
                            throw new CancellationException("already cancelled");
                        }

                        @Override
                        public void beginStaging(long totalArtifacts) {
                            throw new AssertionError("cancelled publication reached staging");
                        }

                        @Override
                        public void reportStaged(long completedArtifacts, long totalArtifacts) {
                            throw new AssertionError("cancelled publication reported progress");
                        }

                        @Override
                        public boolean beginCommit() {
                            throw new AssertionError("cancelled publication reached commit");
                        }
                    }));

            assertEquals(1, cancellationChecks.get());
            assertEquals("replacement-templates", Files.readString(templates));
            assertEquals("replacement-new", Files.readString(newlyInstalled));
            assertEquals("prior-morphs", Files.readString(morphs));
            try (var entries = Files.list(targetDirectory)) {
                assertEquals(1, entries.filter(path -> path.getFileName().toString()
                        .startsWith(".bs2bg-output-stage-")).count());
            }
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(Duration.ofSeconds(10));
            }
        }
    }

    /**
     * Committed cleanup residue retains the complete installed batch before the next real publication proceeds.
     *
     * @param targetDirectory isolated Output destination and recovery-journal owner
     * @throws Exception when publication, fixture construction, recovery, or inspection fails
     */
    @Test
    void committedCleanupResidueRetainsNewBatchBeforeNextPublication(@TempDir Path targetDirectory)
            throws Exception {
        List<OutputArtifact> committedBatch = List.of(
                new TestArtifact("templates.ini", "committed-templates"),
                new TestArtifact("morphs.ini", "committed-morphs"));
        OutputArtifactPublisher.publishAll(targetDirectory, committedBatch);
        Path transaction = Files.createTempDirectory(targetDirectory, ".bs2bg-output-stage-");
        for (int index = 0; index < committedBatch.size(); index++) {
            String member = Integer.toString(index);
            String fileName = committedBatch.get(index).getFileName();
            Files.writeString(transaction.resolve(member + ".target"), fileName);
            Files.writeString(transaction.resolve(member + ".existing"), fileName);
            Files.writeString(transaction.resolve(member + ".backup"), "prior-" + fileName);
        }
        Files.write(transaction.resolve("prepared"),
                ByteBuffer.allocate(Integer.BYTES).putInt(committedBatch.size()).array());
        Files.write(transaction.resolve("committed"), new byte[]{1});

        OutputArtifactPublisher.publishAll(targetDirectory,
                List.of(new TestArtifact("unrelated.txt", "next-publication")));

        assertEquals("committed-templates", Files.readString(targetDirectory.resolve("templates.ini")));
        assertEquals("committed-morphs", Files.readString(targetDirectory.resolve("morphs.ini")));
        assertEquals("next-publication", Files.readString(targetDirectory.resolve("unrelated.txt")));
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
