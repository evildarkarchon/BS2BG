package com.asdasfa.jbs2bg.presentation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Preflights, stages, and transactionally publishes complete accepted Output artifact sets.
 */
public final class OutputArtifactPublisher {
    private static final String STAGING_PREFIX = ".bs2bg-output-stage-";
    private static final int MAX_COMPONENT_UTF16_CODE_UNITS = 255;
    private static final Pattern RESERVED_BASENAME = Pattern.compile(
            "(?:CON|PRN|AUX|NUL|CLOCK\\$|COM[1-9¹²³]|LPT[1-9¹²³])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private OutputArtifactPublisher() {
    }

    /**
     * Publishes a complete artifact set through the non-cancellable context.
     *
     * @param targetDirectory existing destination directory
     * @param artifacts       complete accepted artifact set
     * @throws IOException when preflight, staging, replacement, or rollback fails
     */
    public static void publishAll(Path targetDirectory, List<? extends OutputArtifact> artifacts)
            throws IOException {
        publishAll(targetDirectory, artifacts, PublicationContext.nonCancellable());
    }

    /**
     * Publishes a complete artifact set with cooperative pre-commit cancellation and staging progress.
     *
     * @param targetDirectory existing destination directory
     * @param artifacts       complete accepted artifact set
     * @param context         cancellation, progress, and commit-linearization receiver
     * @throws IOException           when preflight, staging, replacement, or rollback fails
     * @throws CancellationException when cancellation wins before transactional replacement
     */
    public static void publishAll(Path targetDirectory, List<? extends OutputArtifact> artifacts,
                                  PublicationContext context) throws IOException {
        publishAll(targetDirectory, artifacts, context, OutputArtifactPublisher::moveAtomically);
    }

    /**
     * Package-private fault seam used to prove rollback without relying on operating-system timing or locks.
     */
    static void publishAll(Path targetDirectory, List<? extends OutputArtifact> artifacts,
                           PublicationContext context, AtomicMove atomicMove) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(atomicMove, "atomicMove");
        Path directory = normalizeDirectory(targetDirectory);
        List<Publication> publications = preflight(directory, artifacts, context);
        publishSet(directory, publications, context, atomicMove);
    }

    /** Resolves the existing non-symbolic-link target directory before inspecting its entries. */
    private static Path normalizeDirectory(Path targetDirectory) throws IOException {
        Path directory = Objects.requireNonNull(targetDirectory, "targetDirectory").toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Output target is not an existing directory: " + directory);
        return directory;
    }

    /** Resolves every artifact and destination conflict before any staging directory is created. */
    private static List<Publication> preflight(Path directory, List<? extends OutputArtifact> artifacts,
                                               PublicationContext context) throws IOException {
        List<? extends OutputArtifact> accepted = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        Map<String, List<Path>> existingByName = existingEntries(directory);
        Map<String, OutputArtifact> artifactsByName = new HashMap<>();
        List<Publication> publications = new ArrayList<>();
        for (int index = 0; index < accepted.size(); index++) {
            context.checkCancellation();
            OutputArtifact artifact = Objects.requireNonNull(accepted.get(index), "artifact");
            String fileName = artifact.getFileName();
            requireSafeWindowsLeaf(fileName);
            String foldedName = fileName.toLowerCase(Locale.ROOT);
            if (artifactsByName.put(foldedName, artifact) != null)
                throw new IOException("Output artifact filenames collide without regard to case: " + fileName);

            Path target = directory.resolve(fileName).normalize();
            if (!directory.equals(target.getParent()))
                throw new IOException("Output artifact escapes the target directory: " + fileName);
            List<Path> matches = existingByName.getOrDefault(foldedName, List.of());
            if (matches.size() > 1)
                throw new IOException("Output destination is ambiguous without regard to case: " + fileName);
            Path existing = matches.isEmpty() ? null : matches.getFirst();
            if (existing != null && (!Files.isRegularFile(existing, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(existing)))
                throw new IOException("Output destination is not a replaceable regular file: " + existing);
            publications.add(new Publication(artifact, target, existing, index));
        }
        context.checkCancellation();
        return publications;
    }

    /** Indexes existing entries by Windows case-insensitive destination identity. */
    private static Map<String, List<Path>> existingEntries(Path directory) throws IOException {
        Map<String, List<Path>> existingByName = new HashMap<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.forEach(path -> existingByName.computeIfAbsent(
                    path.getFileName().toString().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(path));
        }
        return existingByName;
    }

    /** Rejects names Windows cannot represent as one ordinary destination leaf. */
    private static void requireSafeWindowsLeaf(String fileName) throws IOException {
        String name = Objects.requireNonNull(fileName, "artifact filename");
        Path leaf;
        try {
            leaf = Path.of(name);
        } catch (RuntimeException exception) {
            throw new IOException("Output artifact filename is not a safe leaf: " + name, exception);
        }
        if (name.isEmpty() || leaf.isAbsolute() || leaf.getNameCount() != 1 || !leaf.equals(leaf.getFileName())
                || name.length() > MAX_COMPONENT_UTF16_CODE_UNITS || name.endsWith(".") || name.endsWith(" ")
                || containsUnsafeWindowsCharacter(name) || containsUnpairedSurrogate(name))
            throw new IOException("Output artifact filename is not a safe Windows leaf: " + name);
        String basename = name.substring(0, name.indexOf('.') < 0 ? name.length() : name.indexOf('.'));
        if (RESERVED_BASENAME.matcher(basename).matches())
            throw new IOException("Output artifact filename uses a reserved Windows basename: " + name);
    }

    /** Detects control characters and reserved Windows filename punctuation. */
    private static boolean containsUnsafeWindowsCharacter(String name) {
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (value < 0x20 || value == '<' || value == '>' || value == ':' || value == '"'
                    || value == '/' || value == '\\' || value == '|' || value == '?' || value == '*')
                return true;
        }
        return false;
    }

    /** Detects malformed UTF-16 before it reaches provider-specific filesystem encoding. */
    private static boolean containsUnpairedSurrogate(String name) {
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (Character.isHighSurrogate(value)) {
                if (index + 1 >= name.length() || !Character.isLowSurrogate(name.charAt(++index)))
                    return true;
            } else if (Character.isLowSurrogate(value)) {
                return true;
            }
        }
        return false;
    }

    /** Stages every byte, linearizes commit, and cleans command-owned files after success or complete rollback. */
    private static void publishSet(Path directory, List<Publication> publications, PublicationContext context,
                                   AtomicMove atomicMove) throws IOException {
        if (publications.isEmpty())
            return;
        Path stagingDirectory = Files.createTempDirectory(directory, STAGING_PREFIX);
        boolean preserveRecoveryDirectory = false;
        Throwable publicationFailure = null;
        try {
            stageArtifacts(stagingDirectory, publications, context);
            context.checkCancellation();
            if (!context.beginCommit())
                throw new CancellationException("Output publication was cancelled before commit");
            installArtifacts(stagingDirectory, publications, atomicMove);
        } catch (IOException | RuntimeException | Error failure) {
            publicationFailure = failure;
            IOException rollbackFailure = rollback(publications, atomicMove);
            if (rollbackFailure != null) {
                failure.addSuppressed(rollbackFailure);
                preserveRecoveryDirectory = true;
            }
            throw failure;
        } finally {
            if (!preserveRecoveryDirectory) {
                try {
                    deleteTree(stagingDirectory);
                } catch (IOException cleanupFailure) {
                    if (publicationFailure != null)
                        publicationFailure.addSuppressed(cleanupFailure);
                    else
                        stagingDirectory.toFile().deleteOnExit();
                }
            }
        }
    }

    /** Writes and forces every defensively returned byte array before reporting its real completed unit. */
    private static void stageArtifacts(Path stagingDirectory, List<Publication> publications,
                                       PublicationContext context) throws IOException {
        for (Publication publication : publications) {
            context.checkCancellation();
            Path staged = stagingDirectory.resolve(publication.index + ".staged");
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer content = ByteBuffer.wrap(publication.artifact.getBytes());
                while (content.hasRemaining()) {
                    context.checkCancellation();
                    channel.write(content);
                }
                channel.force(true);
            }
            publication.staged = staged;
            context.reportStaged(publication.index + 1L, publications.size());
        }
    }

    /** Moves every prior target aside before installing staged artifacts without further cancellation points. */
    private static void installArtifacts(Path stagingDirectory, List<Publication> publications,
                                         AtomicMove atomicMove) throws IOException {
        for (Publication publication : publications) {
            if (publication.existing == null)
                continue;
            publication.backup = stagingDirectory.resolve(publication.index + ".backup");
            atomicMove.move(publication.existing, publication.backup);
        }
        for (Publication publication : publications) {
            try {
                atomicMove.move(publication.staged, publication.target);
                publication.installed = true;
            } catch (IOException failure) {
                // A provider can complete the side effect and then report failure; rollback must still remove it.
                publication.installed = Files.exists(publication.target, LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(publication.staged, LinkOption.NOFOLLOW_LINKS);
                throw failure;
            }
        }
    }

    /** Restores prior destinations and removes newly installed artifacts in reverse publication order. */
    private static IOException rollback(List<Publication> publications, AtomicMove atomicMove) {
        IOException rollbackFailure = null;
        for (int index = publications.size() - 1; index >= 0; index--) {
            Publication publication = publications.get(index);
            try {
                if (publication.installed)
                    Files.deleteIfExists(publication.target);
                if (publication.backup != null && Files.exists(publication.backup, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        atomicMove.move(publication.backup, publication.existing);
                    } catch (IOException failure) {
                        if (Files.exists(publication.backup, LinkOption.NOFOLLOW_LINKS)
                                || !Files.exists(publication.existing, LinkOption.NOFOLLOW_LINKS))
                            throw failure;
                    }
                }
            } catch (IOException failure) {
                if (rollbackFailure == null)
                    rollbackFailure = new IOException("Output rollback could not restore every destination.");
                rollbackFailure.addSuppressed(failure);
            }
        }
        return rollbackFailure;
    }

    /** Requires same-filesystem atomic moves for installation and rollback. */
    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("The target filesystem does not support atomic Output replacement.", exception);
        }
    }

    /** Removes one command-owned transaction tree after success, cancellation, or complete rollback. */
    private static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    /** Worker-facing safe points kept independent from the Workbench job implementation. */
    public interface PublicationContext {
        /** Returns a context for synchronous compatibility publication. */
        static PublicationContext nonCancellable() {
            return new PublicationContext() {
                @Override
                public void checkCancellation() {
                    // Synchronous compatibility publication intentionally has no cancellation source.
                }

                @Override
                public void reportStaged(long completedArtifacts, long totalArtifacts) {
                    // Synchronous compatibility publication intentionally discards progress.
                }

                @Override
                public boolean beginCommit() {
                    return true;
                }
            };
        }

        /** Throws when cancellation has been accepted at the current pre-commit safe point. */
        void checkCancellation();

        /** Reports one real staged artifact out of the immutable batch total. */
        void reportStaged(long completedArtifacts, long totalArtifacts);

        /** @return true when non-cancellable transactional replacement may begin */
        boolean beginCommit();
    }

    /** Same-filesystem move injected only for deterministic publication fault tests. */
    @FunctionalInterface
    interface AtomicMove {
        /**
         * Moves one staged, target, or backup path as a single filesystem operation.
         *
         * @throws IOException when the move cannot complete atomically
         */
        void move(Path source, Path target) throws IOException;
    }

    /** Mutable command-local transaction state that never crosses the publisher interface. */
    private static final class Publication {
        private final OutputArtifact artifact;
        private final Path target;
        private final Path existing;
        private final int index;
        private Path staged;
        private Path backup;
        private boolean installed;

        /** Captures one completely preflighted destination before any staged bytes exist. */
        private Publication(OutputArtifact artifact, Path target, Path existing, int index) {
            this.artifact = artifact;
            this.target = target;
            this.existing = existing;
            this.index = index;
        }
    }
}
