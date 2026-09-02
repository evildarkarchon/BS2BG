package com.asdasfa.jbs2bg.presentation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Preflights, stages, and transactionally publishes complete accepted Output artifact sets.
 */
public final class OutputArtifactPublisher {
    private static final String STAGING_PREFIX = ".bs2bg-output-stage-";
    private static final String PREPARED_MARKER = "prepared";
    private static final String PREPARED_STAGED_MARKER = "prepared.staged";
    private static final String COMMITTED_MARKER = "committed";
    private static final String COMMITTED_STAGED_MARKER = "committed.staged";
    private static final int MAX_COMPONENT_UTF16_CODE_UNITS = 255;
    private static final int MAX_MARKER_UTF8_BYTES = MAX_COMPONENT_UTF16_CODE_UNITS * 3;
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
     * Publishes through an injected same-filesystem atomic-move seam so rollback faults remain deterministic in
     * tests without relying on operating-system timing or locks. Cancellation and commit ownership stay with the
     * supplied context exactly as in the public overload.
     *
     * @param targetDirectory existing destination directory
     * @param artifacts       complete accepted artifact set
     * @param context         cancellation, progress, and commit-linearization receiver
     * @param atomicMove      same-filesystem atomic replacement used for install and rollback
     * @throws IOException when preflight, staging, replacement, or rollback fails
     */
    static void publishAll(Path targetDirectory, List<? extends OutputArtifact> artifacts,
                           PublicationContext context, AtomicMove atomicMove) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(atomicMove, "atomicMove");
        Path directory = normalizeDirectory(targetDirectory);
        OutputDirectoryLock directoryLock = OutputDirectoryLock.acquire(directory);
        try (directoryLock) {
            context.checkCancellation();
            // Once admitted, prior-transaction housekeeping must finish without leaving another mixed batch.
            recover(directory);
            List<Publication> publications = preflight(directory, artifacts, context);
            publishSet(directory, publications, context, atomicMove);
        }
    }

    /**
     * Resolves one interrupted transaction while the destination lock is held. This prior-transaction housekeeping
     * is deliberately non-cancellable after admission: prepared but uncommitted state is restored from durable
     * prior-state records, while committed state is verified and cleaned without rollback.
     *
     * @param directory locked Output destination directory
     * @throws IOException when recovery state is ambiguous, malformed, or cannot restore the complete prior batch
     */
    private static void recover(Path directory) throws IOException {
        List<Path> transactions;
        try (Stream<Path> entries = Files.list(directory)) {
            transactions = entries.filter(path -> path.getFileName().toString().startsWith(STAGING_PREFIX))
                    .toList();
        }
        if (transactions.isEmpty())
            return;
        if (transactions.size() != 1)
            throw new IOException("Output recovery found more than one interrupted transaction.");

        Path transaction = transactions.getFirst();
        if (!Files.isDirectory(transaction, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(transaction))
            throw new IOException("Output recovery state is not a transaction directory: " + transaction);
        OptionalInt preparedCount = readPreparedCount(transaction);
        if (preparedCount.isEmpty()) {
            // This protocol never changes a live destination before the prepared marker becomes visible.
            deleteTree(transaction);
            return;
        }

        List<Publication> publications = readRecoveryPublications(directory, transaction,
                preparedCount.getAsInt());
        if (hasCommittedMarker(transaction)) {
            for (Publication publication : publications)
                requireRestored(publication.target, true);
            deleteTree(transaction);
            return;
        }

        IOException recoveryFailure = restorePrior(publications, OutputArtifactPublisher::moveAtomically, true);
        if (recoveryFailure != null)
            throw new IOException("Output recovery could not restore the complete prior batch.", recoveryFailure);
        deleteTree(transaction);
    }

    /** Returns the prepared artifact count, or empty only when no live replacement could have started. */
    private static OptionalInt readPreparedCount(Path transaction) throws IOException {
        Path marker = transaction.resolve(PREPARED_MARKER);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS))
            return OptionalInt.empty();
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(marker)
                || Files.size(marker) != Integer.BYTES)
            throw new IOException("Output publication prepared marker is malformed: " + marker);
        int count = ByteBuffer.wrap(Files.readAllBytes(marker)).getInt();
        long entryCount;
        try (Stream<Path> entries = Files.list(transaction)) {
            entryCount = entries.count();
        }
        if (count <= 0 || count > entryCount)
            throw new IOException("Output publication prepared marker has an invalid artifact count: " + count);
        return OptionalInt.of(count);
    }

    /** Reconstructs every safely bounded destination and prior-state record from one prepared journal. */
    private static List<Publication> readRecoveryPublications(Path directory, Path transaction, int count)
            throws IOException {
        Map<String, Path> targets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Publication> publications = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String member = Integer.toString(index);
            String targetName = readLeafMarker(transaction.resolve(member + ".target"));
            Path target = directory.resolve(targetName).normalize();
            if (!directory.equals(target.getParent()) || targets.put(targetName, target) != null)
                throw new IOException("Output recovery contains an ambiguous target: " + targetName);

            Path backup = transaction.resolve(member + ".backup");
            Path existingMarker = transaction.resolve(member + ".existing");
            Path absentMarker = transaction.resolve(member + ".absent");
            boolean hasBackup = Files.exists(backup, LinkOption.NOFOLLOW_LINKS);
            boolean hasExisting = Files.exists(existingMarker, LinkOption.NOFOLLOW_LINKS);
            boolean wasAbsent = Files.exists(absentMarker, LinkOption.NOFOLLOW_LINKS);
            if (wasAbsent == (hasBackup || hasExisting) || hasBackup != hasExisting)
                throw new IOException("Output recovery member has incomplete or conflicting prior state: " + member);

            Path existing = null;
            if (hasBackup) {
                if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(backup))
                    throw new IOException("Output recovery backup is not a regular file: " + backup);
                String existingName = readLeafMarker(existingMarker);
                if (String.CASE_INSENSITIVE_ORDER.compare(targetName, existingName) != 0)
                    throw new IOException("Output recovery prior destination does not match its target: " + member);
                existing = directory.resolve(existingName).normalize();
                if (!directory.equals(existing.getParent()))
                    throw new IOException("Output recovery prior destination escapes its directory: " + existingName);
            } else if (!Files.isRegularFile(absentMarker, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(absentMarker) || Files.size(absentMarker) != 0L) {
                throw new IOException("Output recovery absence marker is malformed: " + absentMarker);
            }

            Publication publication = new Publication(target, existing, index);
            publication.assignJournalPaths(transaction);
            publications.add(publication);
        }
        return publications;
    }

    /** Reads one forced UTF-8 journal leaf and applies the same destination-name policy used by preflight. */
    private static String readLeafMarker(Path marker) throws IOException {
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(marker))
            throw new IOException("Output recovery filename marker is not a regular file: " + marker);
        long size = Files.size(marker);
        if (size <= 0L || size > MAX_MARKER_UTF8_BYTES)
            throw new IOException("Output recovery filename marker has an invalid size: " + marker);
        String name;
        try {
            name = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(marker)))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Output recovery filename marker is not valid UTF-8: " + marker, exception);
        }
        requireSafeWindowsLeaf(name);
        return name;
    }

    /** Returns whether the exact durable commit marker makes the installed batch authoritative. */
    private static boolean hasCommittedMarker(Path transaction) throws IOException {
        Path marker = transaction.resolve(COMMITTED_MARKER);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS))
            return false;
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(marker)
                || Files.size(marker) != 1L)
            throw new IOException("Output publication commit marker is malformed: " + marker);
        return true;
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
        Map<String, OutputArtifact> artifactsByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Publication> publications = new ArrayList<>();
        for (int index = 0; index < accepted.size(); index++) {
            context.checkCancellation();
            OutputArtifact artifact = Objects.requireNonNull(accepted.get(index), "artifact");
            String fileName = artifact.getFileName();
            requireSafeWindowsLeaf(fileName);
            if (OutputDirectoryLock.ownsFileName(fileName))
                throw new IOException("Output artifact filename is reserved for destination locking: " + fileName);
            if (artifactsByName.put(fileName, artifact) != null)
                throw new IOException("Output artifact filenames collide without regard to case: " + fileName);

            Path target = directory.resolve(fileName).normalize();
            if (!directory.equals(target.getParent()))
                throw new IOException("Output artifact escapes the target directory: " + fileName);
            List<Path> matches = existingByName.getOrDefault(fileName, List.of());
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
        Map<String, List<Path>> existingByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (Stream<Path> entries = Files.list(directory)) {
            entries.forEach(path -> existingByName.computeIfAbsent(
                    path.getFileName().toString(), ignored -> new ArrayList<>()).add(path));
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
            context.beginStaging(publications.size());
            stageArtifacts(stagingDirectory, publications, context);
            context.checkCancellation();
            if (!context.beginCommit())
                throw new CancellationException("Output publication was cancelled before commit");
            prepareRecoveryJournal(stagingDirectory, publications);
            installArtifacts(publications, atomicMove);
            commit(stagingDirectory);
        } catch (IOException | RuntimeException | Error failure) {
            publicationFailure = failure;
            try {
                if (hasCommittedMarker(stagingDirectory)) {
                    // An atomic commit move may report failure after becoming visible; the new batch is coherent.
                    publicationFailure = null;
                    return;
                }
            } catch (IOException markerFailure) {
                failure.addSuppressed(markerFailure);
                preserveRecoveryDirectory = true;
                throw failure;
            }
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

    /**
     * Forces every target identity and prior-state record before atomically exposing the prepared artifact count.
     * No live destination may change until this method completes.
     */
    private static void prepareRecoveryJournal(Path stagingDirectory, List<Publication> publications)
            throws IOException {
        for (Publication publication : publications) {
            publication.assignJournalPaths(stagingDirectory);
            writeAndFlush(publication.targetMarker,
                    publication.target.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            if (publication.existing == null) {
                writeAndFlush(publication.absentMarker, new byte[0]);
                continue;
            }
            writeAndFlush(publication.existingMarker,
                    publication.existing.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            Path stagedBackup = publication.backup.resolveSibling(publication.backup.getFileName() + ".staged");
            Files.copy(publication.existing, stagedBackup);
            forceExisting(stagedBackup);
            moveAtomically(stagedBackup, publication.backup);
        }
        Path stagedMarker = stagingDirectory.resolve(PREPARED_STAGED_MARKER);
        writeAndFlush(stagedMarker, ByteBuffer.allocate(Integer.BYTES).putInt(publications.size()).array());
        moveAtomically(stagedMarker, stagingDirectory.resolve(PREPARED_MARKER));
    }

    /** Stages and atomically exposes the sole commit marker after every replacement has completed. */
    private static void commit(Path stagingDirectory) throws IOException {
        Path stagedMarker = stagingDirectory.resolve(COMMITTED_STAGED_MARKER);
        writeAndFlush(stagedMarker, new byte[]{1});
        moveAtomically(stagedMarker, stagingDirectory.resolve(COMMITTED_MARKER));
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

    /** Atomically replaces live destinations without further cancellation points. */
    private static void installArtifacts(List<Publication> publications, AtomicMove atomicMove) throws IOException {
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
        return restorePrior(publications, atomicMove, false);
    }

    /**
     * Restores durable prior-state records in reverse order. Recovery restores every member because process-local
     * install flags were lost; immediate rollback limits work to members whose atomic move may have completed.
     */
    private static IOException restorePrior(List<Publication> publications, AtomicMove atomicMove,
                                            boolean recoverEveryMember) {
        IOException rollbackFailure = null;
        for (int index = publications.size() - 1; index >= 0; index--) {
            Publication publication = publications.get(index);
            try {
                if (!recoverEveryMember && !publication.installed)
                    continue;
                if (publication.existing == null) {
                    Files.deleteIfExists(publication.target);
                    requireRestored(publication.target, false);
                } else {
                    if (!Files.isRegularFile(publication.backup, LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(publication.backup))
                        throw new IOException("Output prior-state backup is unavailable: " + publication.backup);
                    Path stagedRestore = publication.backup.resolveSibling(
                            publication.backup.getFileName() + ".restore-staged");
                    Files.copy(publication.backup, stagedRestore, StandardCopyOption.REPLACE_EXISTING);
                    forceExisting(stagedRestore);
                    try {
                        atomicMove.move(stagedRestore, publication.existing);
                    } catch (IOException failure) {
                        if (Files.exists(stagedRestore, LinkOption.NOFOLLOW_LINKS)
                                || !Files.isRegularFile(publication.existing, LinkOption.NOFOLLOW_LINKS)
                                || Files.isSymbolicLink(publication.existing))
                            throw failure;
                    }
                    if (!publication.target.equals(publication.existing))
                        Files.deleteIfExists(publication.target);
                    requireRestored(publication.existing, true);
                }
            } catch (IOException failure) {
                if (rollbackFailure == null)
                    rollbackFailure = new IOException("Output rollback could not restore every destination.");
                rollbackFailure.addSuppressed(failure);
            }
        }
        return rollbackFailure;
    }

    /** Requires rollback or restart recovery to leave exactly the recorded prior member state. */
    private static void requireRestored(Path target, boolean shouldExist) throws IOException {
        boolean regular = Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target);
        if (shouldExist && !regular)
            throw new IOException("Output prior destination was not restored: " + target);
        if (!shouldExist && Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Output prior absence was not restored: " + target);
    }

    /** Forces a complete backup before a replacement can consume its corresponding staged artifact. */
    private static void forceExisting(Path target) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /** Writes every marker byte and forces its content before an atomic state transition can expose it. */
    private static void writeAndFlush(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer content = ByteBuffer.wrap(bytes);
            while (content.hasRemaining())
                channel.write(content);
            channel.force(true);
        }
    }

    /** Requires same-filesystem atomic moves for installation and rollback. */
    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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
                public void beginStaging(long totalArtifacts) {
                    // Synchronous compatibility publication intentionally discards phase changes.
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

        /** Announces the cancellable staging phase before the first accepted artifact byte is written. */
        void beginStaging(long totalArtifacts);

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
        private Path targetMarker;
        private Path existingMarker;
        private Path absentMarker;
        private boolean installed;

        /** Captures one completely preflighted destination before any staged bytes exist. */
        private Publication(OutputArtifact artifact, Path target, Path existing, int index) {
            this.artifact = artifact;
            this.target = target;
            this.existing = existing;
            this.index = index;
        }

        /** Reconstructs one journal-owned member without requiring its no-longer-available artifact bytes. */
        private Publication(Path target, Path existing, int index) {
            this.artifact = null;
            this.target = target;
            this.existing = existing;
            this.index = index;
        }

        /** Resolves the durable prior-state and filename records owned by this transaction member. */
        private void assignJournalPaths(Path stagingDirectory) {
            String member = Integer.toString(index);
            backup = stagingDirectory.resolve(member + ".backup");
            targetMarker = stagingDirectory.resolve(member + ".target");
            existingMarker = stagingDirectory.resolve(member + ".existing");
            absentMarker = stagingDirectory.resolve(member + ".absent");
        }
    }
}
