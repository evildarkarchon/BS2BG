package com.asdasfa.jbs2bg.data;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Child-JVM entry point used to prove Settings initialization honors the operating-system directory lock.
 */
public final class SettingsLockProbe {
    private SettingsLockProbe() {
    }

    /**
     * Signals that the child is about to initialize, then publishes a first-run pair after acquiring the lock.
     *
     * @param arguments working directory followed by the ready-marker path
     * @throws Exception when signaling or Settings initialization fails
     */
    public static void main(String[] arguments) throws Exception {
        Path directory = Path.of(arguments[0]);
        Files.writeString(Path.of(arguments[1]), "ready");
        Settings.InitializationResult result = Settings.initialize(directory);
        if (!result.isSuccessful())
            throw new IllegalStateException(result.getFailure().orElseThrow().formatForDisplay());
    }
}
