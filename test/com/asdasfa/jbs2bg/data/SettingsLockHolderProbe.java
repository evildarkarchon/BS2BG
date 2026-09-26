package com.asdasfa.jbs2bg.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/** Child-JVM lock holder for cancellation tests that must wait behind another process. */
public final class SettingsLockHolderProbe {
    private SettingsLockHolderProbe() {
    }

    /**
     * Holds the production Settings lock until the parent creates a release marker.
     *
     * @param arguments working directory, ready marker, and release marker
     * @throws Exception when lock acquisition or process coordination fails
     */
    public static void main(String[] arguments) throws Exception {
        Path directory = Path.of(arguments[0]);
        Path ready = Path.of(arguments[1]);
        Path release = Path.of(arguments[2]);
        SettingsDirectoryLock lock = SettingsDirectoryLock.acquire(directory);
        try {
            Files.writeString(ready, "ready");
            Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
            while (!Files.exists(release) && Instant.now().isBefore(deadline))
                Thread.sleep(10);
            if (!Files.exists(release))
                throw new IllegalStateException("Settings lock holder timed out waiting for release");
        } finally {
            lock.close();
        }
    }
}
