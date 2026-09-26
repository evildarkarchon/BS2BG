package com.asdasfa.jbs2bg.data;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
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
     * The lock spans recovery, candidate construction, and any paired file publication. Waiting remains interruptible
     * so a cancelled Reload does not block the cancelling JavaFX caller behind another process's lock.
     *
     * @param directory working directory that owns the Settings pair
     * @return an acquired lock whose close releases the operating-system resource
     * @throws IOException when the directory or lock file cannot safely be opened or locked
     */
    static SettingsDirectoryLock acquire(Path directory) throws IOException {
        return acquire(directory, false);
    }

    /**
     * Attempts the Workbench edit lock without blocking the JavaFX publication lane behind another process.
     *
     * @param directory working directory that owns the Settings pair
     * @return an immediately acquired lock
     * @throws IOException when the directory is invalid or another writer already owns the lock
     */
    static SettingsDirectoryLock tryAcquire(Path directory) throws IOException {
        return acquire(directory, true);
    }

    /** Opens and acquires the validated lock path in blocking startup or immediate Workbench mode. */
    private static SettingsDirectoryLock acquire(Path directory, boolean immediate) throws IOException {
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
            FileLock acquired = immediate ? channel.tryLock() : waitForLock(channel);
            if (acquired == null)
                throw new IOException("Settings lock is already held by another process.");
            return new SettingsDirectoryLock(channel, acquired);
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
     * Polls a held cross-process lock while the caller retains ownership of the open channel. Short waits let a
     * cancelled worker stop without blocking the JavaFX thread that requests interruption.
     *
     * @param channel open Settings lock channel owned by the caller
     * @return the acquired exclusive lock on that channel
     * @throws IOException when acquisition fails or the waiting worker is interrupted
     */
    private static FileLock waitForLock(FileChannel channel) throws IOException {
        while (true) {
            if (Thread.currentThread().isInterrupted())
                throw new FileLockInterruptionException();
            FileLock acquired = channel.tryLock();
            if (acquired != null)
                return acquired;
            try {
                // Interrupting FileChannel.lock() can itself block on Windows until another process releases it.
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new FileLockInterruptionException();
            }
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
