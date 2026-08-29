package com.asdasfa.jbs2bg.project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Publishes canonical Project bytes through a sibling staging file and replaces
 * the destination only after every byte has been written and flushed.
 */
final class ProjectFileWriter {

    /**
     * Maximum number of code points of the target filename carried into the staging
     * file's name. Files.createTempFile appends a random numeric component and the
     * ".tmp" suffix, so an unbounded prefix would push a legal target name near the
     * filesystem's 255-unit component limit over it and fail every save of that
     * target. The prefix only exists to make an orphaned staging file attributable
     * to its Project, so a short leading fragment is sufficient; the limit is in
     * code points (never splitting a surrogate pair) and small enough that even
     * four-byte UTF-8 characters stay well inside the limit.
     */
    private static final int STAGING_PREFIX_NAME_LIMIT = 32;

    private ProjectFileWriter() {
    }

    /**
     * Persists one coherent snapshot through a sibling temporary file. The
     * temporary file is flushed before replacement and removed after a failed
     * replacement attempt.
     *
     * @param snapshot immutable Project content to persist
     * @param target requested destination file
     * @throws NullPointerException when snapshot or target is null
     * @throws IOException when the temporary file cannot be written or installed
     * @throws RuntimeException when the filesystem provider reports an unchecked
     *         environmental failure
     */
    static void write(ProjectSnapshot snapshot, Path target) throws IOException {
        byte[] content = ProjectJacksonAdapter.write(snapshot);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null)
            throw new IOException("The Project target has no parent directory: " + normalizedTarget);

        String targetName = normalizedTarget.getFileName() == null
                ? "project"
                : normalizedTarget.getFileName().toString();
        String prefix = "." + leadingCodePoints(targetName, STAGING_PREFIX_NAME_LIMIT) + "-";
        if (prefix.length() < 3)
            prefix = ".project-";

        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        try {
            writeAndFlush(temporary, content);
            replace(temporary, normalizedTarget);
        } catch (IOException failure) {
            cleanupTemporary(temporary, failure);
            throw failure;
        } catch (RuntimeException failure) {
            cleanupTemporary(temporary, failure);
            throw failure;
        }
    }

    /**
     * Returns at most the first {@code limit} code points of a string, so that a
     * truncation never ends inside a surrogate pair.
     *
     * @param value string to truncate
     * @param limit maximum number of code points to keep
     * @return the whole string when it is short enough, otherwise its leading fragment
     */
    private static String leadingCodePoints(String value, int limit) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= limit)
            return value;
        return value.substring(0, value.offsetByCodePoints(0, limit));
    }

    /**
     * Removes only the detached staging file after a failed write or replacement,
     * preserving the operation failure as the primary diagnostic cause.
     *
     * @param temporary staging file to remove
     * @param failure primary persistence failure
     */
    private static void cleanupTemporary(Path temporary, Throwable failure) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            // Preserve the persistence failure as primary while retaining cleanup context.
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * Writes every byte and asks the filesystem to flush file content before the
     * destination path can be replaced.
     *
     * @param temporary sibling temporary file
     * @param content serialized UTF-8 Project bytes
     * @throws IOException when writing or flushing fails
     */
    private static void writeAndFlush(Path temporary, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining())
                channel.write(buffer);
            channel.force(true);
        }
    }

    /**
     * Installs the completed temporary file as the single atomic replacement step.
     * Unsupported providers fail truthfully instead of risking a non-atomic move.
     *
     * @param temporary completed sibling temporary file
     * @param target normalized destination path
     * @throws IOException when replacement fails
     */
    private static void replace(Path temporary, Path target) throws IOException {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
