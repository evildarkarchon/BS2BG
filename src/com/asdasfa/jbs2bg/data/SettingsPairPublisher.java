package com.asdasfa.jbs2bg.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Stages canonical Standard and UUNP documents before publishing the pair into one directory.
 */
final class SettingsPairPublisher {
    private static final String STAGING_PREFIX = ".bs2bg-settings-stage-";
    private static final String COMMITTED_MARKER = "committed";
    private static final String COMMITTED_STAGED_MARKER = "committed.staged";

    private SettingsPairPublisher() {
    }

    /**
     * Restores an interrupted, uncommitted Settings transaction before either source is parsed.
     * A committed transaction is only cleaned because both replacements were already installed.
     *
     * @param directory      working directory that owns the production Settings filenames
     * @param standardTarget Standard Settings destination
     * @param uunpTarget     UUNP Settings destination
     * @return true when an uncommitted transaction was rolled back
     * @throws IOException when recovery state is ambiguous or cannot restore a coherent pair
     */
    static boolean recover(Path directory, Path standardTarget, Path uunpTarget) throws IOException {
        Path owner = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        List<Path> transactions;
        try (Stream<Path> entries = Files.list(owner)) {
            transactions = entries.filter(path -> path.getFileName().toString().startsWith(STAGING_PREFIX))
                    .toList();
        }
        if (transactions.isEmpty())
            return false;
        if (transactions.size() != 1)
            throw new IOException("Settings recovery found more than one interrupted transaction.");

        Path transaction = transactions.get(0);
        if (!Files.isDirectory(transaction, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(transaction))
            throw new IOException("Settings recovery state is not a transaction directory: " + transaction);
        Path standard = normalizeTarget(standardTarget);
        Path uunp = normalizeTarget(uunpTarget);
        if (hasCommittedMarker(transaction)) {
            if (!Files.isRegularFile(standard, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(uunp, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Committed Settings recovery state does not contain both published files.");
            }
            deleteTree(transaction);
            return false;
        }

        try {
            recoverMember(transaction, "standard", standard);
            recoverMember(transaction, "uunp", uunp);
            deleteTree(transaction);
            return true;
        } catch (IOException exception) {
            throw new IOException("Settings recovery could not restore the complete prior pair.", exception);
        }
    }

    /**
     * Restores one backup or removes a target that did not exist before the interrupted command.
     */
    private static void recoverMember(Path transaction, String member, Path target) throws IOException {
        Path backup = transaction.resolve(member + ".backup");
        Path absent = transaction.resolve(member + ".absent");
        boolean hasBackup = Files.exists(backup, LinkOption.NOFOLLOW_LINKS);
        boolean wasAbsent = Files.exists(absent, LinkOption.NOFOLLOW_LINKS);
        if (hasBackup && wasAbsent)
            throw new IOException("Settings recovery member has conflicting prior-state markers: " + member);
        if (hasBackup) {
            if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(backup))
                throw new IOException("Settings recovery backup is not a regular file: " + backup);
            moveAtomically(backup, target);
            requireRestored(target, true);
        } else if (wasAbsent) {
            Files.deleteIfExists(target);
            requireRestored(target, false);
        } else if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            requireRestored(target, true);
        }
    }

    /**
     * Writes and flushes both documents before either production filename is installed.
     *
     * @param standardTarget Standard Settings destination
     * @param uunpTarget     UUNP Settings destination
     * @param pair           defensively owned canonical Settings bytes
     * @throws IOException when preflight, staging, installation, or cleanup fails
     */
    static void publish(Path standardTarget, Path uunpTarget,
                        SettingsJacksonAdapter.SettingsPairBytes pair) throws IOException {
        publish(standardTarget, uunpTarget, pair, SettingsPairPublisher::moveAtomically);
    }

    /**
     * Internal fault seam for deterministic later-install and rollback coverage.
     *
     * @param standardTarget Standard Settings destination
     * @param uunpTarget     UUNP Settings destination
     * @param pair           defensively owned canonical Settings bytes
     * @param atomicMove     same-filesystem move used for backup, install, and rollback
     * @throws IOException when the pair cannot be published or fully restored
     */
    static void publish(Path standardTarget, Path uunpTarget,
                        SettingsJacksonAdapter.SettingsPairBytes pair, AtomicMove atomicMove) throws IOException {
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(atomicMove, "atomicMove");
        Path standard = normalizeTarget(standardTarget);
        Path uunp = normalizeTarget(uunpTarget);
        Path directory = standard.getParent();
        if (directory == null || !directory.equals(uunp.getParent()))
            throw new IOException("Settings destinations must be sibling files.");

        Path stagingDirectory = Files.createTempDirectory(directory, STAGING_PREFIX);
        Publication standardPublication = new Publication(standard,
                Files.exists(standard, LinkOption.NOFOLLOW_LINKS), stagingDirectory.resolve("standard.staged"),
                stagingDirectory.resolve("standard.backup"), stagingDirectory.resolve("standard.absent"));
        Publication uunpPublication = new Publication(uunp,
                Files.exists(uunp, LinkOption.NOFOLLOW_LINKS), stagingDirectory.resolve("uunp.staged"),
                stagingDirectory.resolve("uunp.backup"), stagingDirectory.resolve("uunp.absent"));
        IOException publicationFailure = null;
        boolean preserveRecoveryDirectory = false;
        try {
            markPriorAbsence(standardPublication);
            markPriorAbsence(uunpPublication);
            writeAndFlush(standardPublication.staged, pair.standardUtf8());
            writeAndFlush(uunpPublication.staged, pair.uunpUtf8());
            preparePrior(standardPublication, atomicMove);
            preparePrior(uunpPublication, atomicMove);
            install(standardPublication, atomicMove);
            install(uunpPublication, atomicMove);
            commit(stagingDirectory, atomicMove);
        } catch (IOException exception) {
            publicationFailure = exception;
            try {
                if (hasCommittedMarker(stagingDirectory)) {
                    // An atomic commit move may report failure after becoming visible; the new pair is coherent.
                    publicationFailure = null;
                    return;
                }
            } catch (IOException markerFailure) {
                exception.addSuppressed(markerFailure);
                preserveRecoveryDirectory = true;
                throw exception;
            }
            IOException rollbackFailure = rollback(uunpPublication, standardPublication, atomicMove);
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
                        throw cleanupFailure;
                }
            }
        }
    }

    /**
     * Stages and forces the marker before one atomic rename becomes the sole publication commit point.
     */
    private static void commit(Path stagingDirectory, AtomicMove atomicMove) throws IOException {
        Path stagedMarker = stagingDirectory.resolve(COMMITTED_STAGED_MARKER);
        writeAndFlush(stagedMarker, new byte[]{1});
        atomicMove.move(stagedMarker, stagingDirectory.resolve(COMMITTED_MARKER));
    }

    /**
     * Resolves the durable commit point and rejects a malformed marker rather than trusting ambiguous state.
     *
     * @param transaction publication transaction directory
     * @return true only when the exact committed marker is durably visible
     * @throws IOException when a marker exists but is not the regular one-byte marker written by this command
     */
    private static boolean hasCommittedMarker(Path transaction) throws IOException {
        Path marker = transaction.resolve(COMMITTED_MARKER);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS))
            return false;
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(marker)
                || Files.size(marker) != 1L) {
            throw new IOException("Settings publication commit marker is malformed: " + marker);
        }
        return true;
    }

    /**
     * Records a missing prior destination so restart recovery knows to remove a partly installed new file.
     */
    private static void markPriorAbsence(Publication publication) throws IOException {
        if (!publication.existed)
            writeAndFlush(publication.absentMarker, new byte[0]);
    }

    /**
     * Copies and flushes an existing destination before atomically exposing its complete backup marker.
     */
    private static void preparePrior(Publication publication, AtomicMove atomicMove) throws IOException {
        if (!publication.existed)
            return;
        Files.copy(publication.target, publication.backupStaged);
        forceExisting(publication.backupStaged);
        atomicMove.move(publication.backupStaged, publication.backup);
    }

    /**
     * Installs one fully staged replacement after both durable prior-state records exist.
     */
    private static void install(Publication publication, AtomicMove atomicMove) throws IOException {
        atomicMove.move(publication.staged, publication.target);
    }

    /**
     * Restores both prior destinations from durable markers in reverse installation order.
     */
    private static IOException rollback(Publication first, Publication second, AtomicMove atomicMove) {
        IOException failure = null;
        for (Publication publication : new Publication[]{first, second}) {
            try {
                if (Files.exists(publication.backup, LinkOption.NOFOLLOW_LINKS)) {
                    atomicMove.move(publication.backup, publication.target);
                    requireRestored(publication.target, true);
                } else if (Files.exists(publication.absentMarker, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(publication.target);
                    requireRestored(publication.target, false);
                } else if (publication.existed) {
                    requireRestored(publication.target, true);
                }
            } catch (IOException exception) {
                if (failure == null)
                    failure = new IOException("Settings rollback could not restore the complete prior pair.");
                failure.addSuppressed(exception);
            }
        }
        return failure;
    }

    /**
     * Requires rollback or restart recovery to leave exactly the recorded prior member state.
     */
    private static void requireRestored(Path target, boolean shouldExist) throws IOException {
        boolean regular = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target);
        if (shouldExist && !regular)
            throw new IOException("Settings prior destination was not restored: " + target);
        if (!shouldExist && Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Settings prior absence was not restored: " + target);
    }

    /**
     * Resolves one replaceable target beneath an existing working directory.
     */
    private static Path normalizeTarget(Path target) throws IOException {
        Path normalized = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Settings target parent is not an existing directory: " + parent);
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(normalized)))
            throw new IOException("Settings destination is not a replaceable regular file: " + normalized);
        return normalized;
    }

    /**
     * Forces a copied backup before its complete marker can be atomically installed.
     */
    private static void forceExisting(Path target) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /**
     * Writes every byte and forces file content before a destination move can begin.
     */
    private static void writeAndFlush(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer content = ByteBuffer.wrap(bytes);
            while (content.hasRemaining())
                channel.write(content);
            channel.force(true);
        }
    }

    /**
     * Requires a same-filesystem atomic replacement for each published Settings member.
     */
    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("The working filesystem does not support atomic Settings replacement.", exception);
        }
    }

    /**
     * Removes the command-owned staging tree after successful publication or a failed attempt.
     */
    private static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    /**
     * Same-filesystem move operation injected only within the package for deterministic transaction tests.
     */
    @FunctionalInterface
    interface AtomicMove {
        /**
         * Moves one staged, destination, or backup file as one filesystem operation.
         *
         * @param source existing source
         * @param target sibling destination, which may already exist and is atomically replaced
         * @throws IOException when the move cannot complete atomically
         */
        void move(Path source, Path target) throws IOException;
    }

    /**
     * Mutable command-local state for one member of the paired Settings publication.
     */
    private static final class Publication {
        private final Path target;
        private final boolean existed;
        private final Path staged;
        private final Path backupStaged;
        private final Path backup;
        private final Path absentMarker;

        /**
         * Captures one fully preflighted destination before staging or backup begins.
         */
        private Publication(Path target, boolean existed, Path staged, Path backup, Path absentMarker) {
            this.target = target;
            this.existed = existed;
            this.staged = staged;
            this.backupStaged = backup.resolveSibling(backup.getFileName() + ".staged");
            this.backup = backup;
            this.absentMarker = absentMarker;
        }
    }
}
