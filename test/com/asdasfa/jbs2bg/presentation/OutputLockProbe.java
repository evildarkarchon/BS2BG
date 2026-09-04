package com.asdasfa.jbs2bg.presentation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Child-JVM entry point used to prove Output publication honors the operating-system destination lock. */
public final class OutputLockProbe {
    private OutputLockProbe() {
    }

    /**
     * Signals that publication is about to start, then replaces one complete two-artifact batch.
     *
     * @param arguments destination directory followed by the ready-marker path
     * @throws Exception when signaling or publication fails
     */
    public static void main(String[] arguments) throws Exception {
        Path directory = Path.of(arguments[0]);
        Files.writeString(Path.of(arguments[1]), "ready");
        OutputArtifactPublisher.publishAll(directory, List.of(
                new Utf8OutputArtifact("templates.ini", "child-templates"),
                new Utf8OutputArtifact("morphs.ini", "child-morphs")));
    }
}
