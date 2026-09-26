package com.asdasfa.jbs2bg.presentation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Child-JVM entry point that stops after replacing one prior file and installing one previously absent file. */
public final class OutputPublicationInterruptionProbe {
    static final int INTERRUPTED_EXIT_CODE = 86;

    private OutputPublicationInterruptionProbe() {
    }

    /**
     * Starts a three-member publication and terminates before the final live replacement.
     *
     * @param arguments one existing Output destination directory
     * @throws Exception when staging or either completed atomic replacement fails before simulated interruption
     */
    public static void main(String[] arguments) throws Exception {
        Path directory = Path.of(arguments[0]);
        AtomicInteger replacements = new AtomicInteger();
        OutputArtifactPublisher.publishAll(directory, List.of(
                        new Utf8OutputArtifact("templates.ini", "replacement-templates"),
                        new Utf8OutputArtifact("newly-installed.txt", "replacement-new"),
                        new Utf8OutputArtifact("morphs.ini", "replacement-morphs")),
                OutputArtifactPublisher.PublicationContext.nonCancellable(), (source, target) -> {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                    if (replacements.incrementAndGet() == 2)
                        Runtime.getRuntime().halt(INTERRUPTED_EXIT_CODE);
                });
    }
}
