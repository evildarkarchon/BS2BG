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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Stages and transactionally replaces complete sets of canonical BoS artifacts. */
public final class BosArtifactPublisher {
    private static final String STAGING_PREFIX = ".bs2bg-bos-stage-";

    private BosArtifactPublisher() {
    }

    /**
     * Preflights every mapped destination, stages every byte, and installs the target
     * set with rollback while preserving unrelated files.
     *
     * @param targetDirectory existing destination directory
     * @param output immutable generated output containing the complete artifact set
     * @throws IOException when preflight, staging, replacement, or rollback fails
     */
    public static void publishAll(Path targetDirectory, ProjectGeneratedOutput output) throws IOException {
        publishAll(targetDirectory, output, BosArtifactPublisher::moveAtomically);
    }

    /**
     * Internal fault seam that keeps rollback tests deterministic without depending on
     * operating-system file-lock behavior.
     *
     * @param targetDirectory existing destination directory
     * @param output immutable generated output containing the complete artifact set
     * @param atomicMove same-filesystem move operation used for install and rollback
     * @throws IOException when preflight, staging, replacement, or rollback fails
     */
    static void publishAll(Path targetDirectory, ProjectGeneratedOutput output, AtomicMove atomicMove)
            throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(atomicMove, "atomicMove");
        Path directory = normalizeDirectory(targetDirectory);
        List<Publication> publications = preflight(directory, output.getBosJsonArtifacts());
        publishSet(directory, publications, atomicMove);
    }

    /**
     * Atomically replaces one user-selected destination with an artifact's canonical
     * bytes.
     *
     * @param destination selected output file
     * @param artifact immutable canonical BoS artifact
     * @throws IOException when the destination cannot be safely staged or replaced
     */
    public static void publish(Path destination, BosJsonArtifact artifact) throws IOException {
        Objects.requireNonNull(artifact, "artifact");
        Path target = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        Path directory = target.getParent();
        if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("BoS target parent is not an existing directory: " + directory);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)))
            throw new IOException("BoS destination is not a replaceable regular file: " + target);

        Path existing = Files.exists(target, LinkOption.NOFOLLOW_LINKS) ? target : null;
        publishSet(directory, List.of(new Publication(artifact, target, existing, 0)),
                BosArtifactPublisher::moveAtomically);
    }

    /** Stages, installs, and rolls back one fully preflighted publication command. */
    private static void publishSet(Path directory, List<Publication> publications, AtomicMove atomicMove)
            throws IOException {
        if (publications.isEmpty())
            return;

        Path stagingDirectory = Files.createTempDirectory(directory, STAGING_PREFIX);
        boolean preserveRecoveryDirectory = false;
        IOException publicationFailure = null;
        try {
            stageArtifacts(stagingDirectory, publications);
            installArtifacts(stagingDirectory, publications, atomicMove);
        } catch (IOException exception) {
            publicationFailure = exception;
            IOException rollbackFailure = rollback(publications, atomicMove);
            if (rollbackFailure != null) {
                exception.addSuppressed(rollbackFailure);
                preserveRecoveryDirectory = true;
            }
            throw exception;
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

    /** Resolves and verifies the command's existing directory before any staging begins. */
    private static Path normalizeDirectory(Path targetDirectory) throws IOException {
        Path directory = Objects.requireNonNull(targetDirectory, "targetDirectory").toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("BoS target is not an existing directory: " + directory);
        return directory;
    }

    /** Resolves all mapped destinations and rejects unsafe or ambiguous existing entries. */
    private static List<Publication> preflight(Path directory, List<BosJsonArtifact> artifacts)
            throws IOException {
        Map<String, List<Path>> existingByCaseInsensitiveName = new HashMap<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.forEach(path -> existingByCaseInsensitiveName
                    .computeIfAbsent(path.getFileName().toString().toLowerCase(java.util.Locale.ROOT),
                            ignored -> new ArrayList<>())
                    .add(path));
        }

        Map<String, BosJsonArtifact> artifactsByCaseInsensitiveName =
                new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Publication> publications = new ArrayList<>();
        for (int index = 0; index < artifacts.size(); index++) {
            BosJsonArtifact artifact = Objects.requireNonNull(artifacts.get(index), "artifact");
            Path leaf = Path.of(artifact.getFileName());
            if (leaf.isAbsolute() || leaf.getNameCount() != 1 || !leaf.equals(leaf.getFileName()))
                throw new IOException("BoS artifact filename is not a safe leaf: " + artifact.getFileName());
            if (artifactsByCaseInsensitiveName.put(artifact.getFileName(), artifact) != null)
                throw new IOException("BoS artifact filenames collide without regard to case: "
                        + artifact.getFileName());

            Path target = directory.resolve(leaf).normalize();
            if (!directory.equals(target.getParent()))
                throw new IOException("BoS artifact escapes the target directory: " + artifact.getFileName());

            List<Path> existingMatches = existingByCaseInsensitiveName.getOrDefault(
                    artifact.getFileName().toLowerCase(java.util.Locale.ROOT), List.of());
            if (existingMatches.size() > 1)
                throw new IOException("BoS destination is ambiguous without regard to case: "
                        + artifact.getFileName());
            Path existing = existingMatches.isEmpty() ? null : existingMatches.get(0);
            if (existing != null && (Files.isDirectory(existing, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(existing)))
                throw new IOException("BoS destination is not a replaceable regular file: " + existing);
            publications.add(new Publication(artifact, target, existing, index));
        }
        return publications;
    }

    /** Writes and forces every canonical byte array into the command-owned staging directory. */
    private static void stageArtifacts(Path stagingDirectory, List<Publication> publications) throws IOException {
        for (Publication publication : publications) {
            Path staged = stagingDirectory.resolve(publication.index + ".staged");
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer content = ByteBuffer.wrap(publication.artifact.getBytes());
                while (content.hasRemaining())
                    channel.write(content);
                channel.force(true);
            }
            publication.staged = staged;
        }
    }

    /** Moves every prior destination aside before atomically installing all staged artifacts. */
    private static void installArtifacts(Path stagingDirectory, List<Publication> publications,
            AtomicMove atomicMove) throws IOException {
        for (Publication publication : publications) {
            if (publication.existing == null)
                continue;
            publication.backup = stagingDirectory.resolve(publication.index + ".backup");
            atomicMove.move(publication.existing, publication.backup);
        }
        for (Publication publication : publications) {
            atomicMove.move(publication.staged, publication.target);
            publication.installed = true;
        }
    }

    /** Restores every pre-command destination after an installation failure. */
    private static IOException rollback(List<Publication> publications, AtomicMove atomicMove) {
        IOException rollbackFailure = null;
        for (int index = publications.size() - 1; index >= 0; index--) {
            Publication publication = publications.get(index);
            try {
                if (publication.installed)
                    Files.deleteIfExists(publication.target);
                if (publication.backup != null && Files.exists(publication.backup, LinkOption.NOFOLLOW_LINKS))
                    atomicMove.move(publication.backup, publication.existing);
            } catch (IOException exception) {
                if (rollbackFailure == null)
                    rollbackFailure = new IOException("BoS rollback could not restore every destination.");
                rollbackFailure.addSuppressed(exception);
            }
        }
        return rollbackFailure;
    }

    /** Requires same-filesystem atomic moves for both installation and rollback. */
    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("The target filesystem does not support atomic BoS replacement.", exception);
        }
    }

    /** Removes one command-owned staging tree after success or a complete rollback. */
    private static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    /** Same-filesystem move operation injected only inside the package for deterministic rollback tests. */
    @FunctionalInterface
    interface AtomicMove {
        /**
         * Moves one staged, target, or backup path as a single filesystem operation.
         *
         * @param source existing source path
         * @param target absent destination path on the same filesystem
         * @throws IOException when the move cannot complete atomically
         */
        void move(Path source, Path target) throws IOException;
    }

    /** Mutable command-local publication state; never crosses the module interface. */
    private static final class Publication {
        private final BosJsonArtifact artifact;
        private final Path target;
        private final Path existing;
        private final int index;
        private Path staged;
        private Path backup;
        private boolean installed;

        /** Captures one preflighted destination before any staging or replacement occurs. */
        Publication(BosJsonArtifact artifact, Path target, Path existing, int index) {
            this.artifact = artifact;
            this.target = target;
            this.existing = existing;
            this.index = index;
        }
    }
}
