package com.asdasfa.jbs2bg.data;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Owns the operating-system lock that serializes Settings recovery and publication across application processes.
 */
final class SettingsDirectoryLock implements AutoCloseable {
    private static final String LOCK_FILE_NAME = ".bs2bg-settings.lock";

    private final FileChannel channel;
    private final FileLock lock;

    /**
     * Captures the opened channel and its exclusive operating-system lock.
     */
    private SettingsDirectoryLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Acquires the exclusive Settings lock for one existing working directory.
     * The blocking lock deliberately spans recovery, candidate construction, and any paired file publication.
     *
     * @param directory working directory that owns the Settings pair
     * @return an acquired lock whose close releases the operating-system resource
     * @throws IOException when the directory or lock file cannot safely be opened or locked
     */
    static SettingsDirectoryLock acquire(Path directory) throws IOException {
        Path owner = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (!Files.isDirectory(owner, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(owner))
            throw new IOException("Settings working directory is not an existing directory: " + owner);
        Path lockPath = owner.resolve(LOCK_FILE_NAME);
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(lockPath))) {
            throw new IOException("Settings lock path is not a regular file: " + lockPath);
        }

        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try {
            return new SettingsDirectoryLock(channel, channel.lock());
        } catch (IOException | OverlappingFileLockException exception) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            if (exception instanceof IOException ioException)
                throw ioException;
            throw new IOException("Settings lock is already held by this process.", exception);
        }
    }

    /**
     * Releases the file lock and its channel, retaining both close failures when necessary.
     */
    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            lock.release();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            if (failure == null)
                failure = exception;
            else
                failure.addSuppressed(exception);
        }
        if (failure != null)
            throw failure;
    }
}
