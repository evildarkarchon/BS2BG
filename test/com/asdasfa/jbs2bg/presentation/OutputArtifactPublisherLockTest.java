package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves Output publication serializes independent application processes targeting one directory. */
final class OutputArtifactPublisherLockTest {
    /** Resolves the exact Java executable running the Maven test process. */
    private static String javaExecutable() {
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    /** Returns Surefire's complete test class path, falling back to the current JVM class path. */
    private static String testClassPath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }

    /** Waits for the child signal without depending on process output buffering. */
    private static boolean awaitFile(Path marker, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Files.isRegularFile(marker))
                return true;
            Thread.sleep(10);
        }
        return Files.isRegularFile(marker);
    }

    /** Reads completed child output for assertion diagnostics. */
    private static String readOutput(Process process) throws IOException {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Holds the destination lock in this JVM and requires a child publisher to wait before replacing either file.
     *
     * @param directory isolated Output destination
     * @throws Exception when process coordination or inspection fails
     */
    @Test
    void childProcessCannotPublishUntilTheDirectoryLockIsReleased(@TempDir Path directory) throws Exception {
        Path templates = directory.resolve("templates.ini");
        Path morphs = directory.resolve("morphs.ini");
        Path ready = directory.resolve("probe.ready");
        Files.writeString(templates, "prior-templates");
        Files.writeString(morphs, "prior-morphs");
        Process child = null;
        try {
            Path lockPath = directory.resolve(".bs2bg-output.lock");
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock parentLock = channel.lock()) {
                assertTrue(parentLock.isValid(), "parent process did not acquire the Output lock");
                child = new ProcessBuilder(javaExecutable(), "-cp", testClassPath(),
                        OutputLockProbe.class.getName(), directory.toString(), ready.toString())
                        .redirectErrorStream(true)
                        .start();
                assertTrue(awaitFile(ready, Duration.ofSeconds(10)), "child did not reach Output publication");
                Thread.sleep(250);
                assertTrue(child.isAlive(), "child published while the parent process held the lock");
                assertEquals("prior-templates", Files.readString(templates));
                assertEquals("prior-morphs", Files.readString(morphs));
            }

            assertTrue(child.waitFor(Duration.ofSeconds(10)), "child did not publish after lock release");
            assertEquals(0, child.exitValue(), readOutput(child));
            assertEquals("child-templates", Files.readString(templates));
            assertEquals("child-morphs", Files.readString(morphs));
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(Duration.ofSeconds(10));
            }
        }
    }
}
