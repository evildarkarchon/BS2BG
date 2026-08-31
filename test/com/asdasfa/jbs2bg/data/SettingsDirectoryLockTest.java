package com.asdasfa.jbs2bg.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Settings writer lock serializes independent application processes, not only JVM threads.
 */
final class SettingsDirectoryLockTest {
    /**
     * Resolves the exact Java executable running the Maven test process.
     */
    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    /**
     * Returns Surefire's complete test class path, falling back to the current JVM class path.
     */
    private static String testClassPath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }

    /**
     * Waits for the child signal without depending on process output buffering.
     */
    private static boolean awaitFile(Path marker, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Files.isRegularFile(marker))
                return true;
            Thread.sleep(10);
        }
        return Files.isRegularFile(marker);
    }

    /**
     * Reads completed child output for assertion diagnostics.
     */
    private static String readOutput(Process process) throws IOException {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Holds the production lock in the test JVM and requires a child JVM to wait before creating either file.
     *
     * @param directory isolated first-run working directory
     * @throws Exception when process coordination or inspection fails
     */
    @Test
    void childProcessCannotInitializeUntilTheDirectoryLockIsReleased(@TempDir Path directory) throws Exception {
        Path ready = directory.resolve("probe.ready");
        Process child = null;
        try {
            SettingsDirectoryLock directoryLock = SettingsDirectoryLock.acquire(directory);
            try (directoryLock) {
                child = new ProcessBuilder(javaExecutable(), "-cp", testClassPath(),
                        SettingsLockProbe.class.getName(), directory.toString(), ready.toString())
                        .redirectErrorStream(true)
                        .start();
                assertTrue(awaitFile(ready, Duration.ofSeconds(10)), "child did not reach Settings.initialize");
                Thread.sleep(250);
                assertTrue(child.isAlive(), "child initialized while the parent process held the lock");
                assertFalse(Files.exists(directory.resolve("settings.json")));
                assertFalse(Files.exists(directory.resolve("settings_UUNP.json")));
            }

            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "child did not initialize after lock release");
            assertEquals(0, child.exitValue(), readOutput(child));
            assertTrue(Files.isRegularFile(directory.resolve("settings.json")));
            assertTrue(Files.isRegularFile(directory.resolve("settings_UUNP.json")));
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
