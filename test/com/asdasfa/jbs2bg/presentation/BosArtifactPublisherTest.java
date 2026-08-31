package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;

/**
 * Verifies transactional publication through the JavaFX-independent BoS artifact seam.
 */
final class BosArtifactPublisherTest {

    /**
     * Batch publication writes the owned bytes and leaves unrelated JSON untouched.
     */
    @Test
    void publishesCanonicalBytesWithoutDeletingUnrelatedJson(@TempDir Path targetDirectory) throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("Zulu"));
        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(session.getSnapshot(), false);
        Path unrelated = targetDirectory.resolve("stale.json");
        Files.writeString(unrelated, "preserved", StandardCharsets.UTF_8);

        byte[] exposed = output.getBosJsonArtifacts().get(0).getBytes();
        exposed[0] = 'x';
        BosArtifactPublisher.publishAll(targetDirectory, output);

        for (BosJsonArtifact artifact : output.getBosJsonArtifacts()) {
            assertArrayEquals(artifact.getBytes(), Files.readAllBytes(targetDirectory.resolve(artifact.getFileName())));
        }
        assertEquals("preserved", Files.readString(unrelated, StandardCharsets.UTF_8));
    }

    /**
     * A chosen-file export replaces one destination from the same owned byte array.
     */
    @Test
    void publishesOneCanonicalArtifactToTheChosenDestination(@TempDir Path targetDirectory) throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        BosJsonArtifact artifact = ProjectOutputFormatter.generate(session.getSnapshot(), false)
                .getBosJsonArtifacts().get(0);
        Path destination = targetDirectory.resolve("chosen-name.json");
        Files.writeString(destination, "prior", StandardCharsets.UTF_8);

        BosArtifactPublisher.publish(destination, artifact);

        assertArrayEquals(artifact.getBytes(), Files.readAllBytes(destination));
    }

    /**
     * Batch preflight rejects one unsafe destination before replacing any prior file.
     */
    @Test
    void preflightsTheCompleteTargetSetBeforePublishing(@TempDir Path targetDirectory) throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("Zulu"));
        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(session.getSnapshot(), false);
        Path prior = targetDirectory.resolve("Alpha.json");
        Files.writeString(prior, "prior", StandardCharsets.UTF_8);
        Files.createDirectory(targetDirectory.resolve("Zulu.json"));

        assertThrows(IOException.class, () -> BosArtifactPublisher.publishAll(targetDirectory, output));

        assertEquals("prior", Files.readString(prior, StandardCharsets.UTF_8));
        try (var entries = Files.list(targetDirectory)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".bs2bg-bos-stage-")).count());
        }
    }

    /**
     * A failure after the first backup restores every prior destination and removes staging.
     */
    @Test
    void rollsBackPriorBackupsWhenALaterDestinationCannotMove(@TempDir Path targetDirectory) throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.create("Zulu"));
        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(session.getSnapshot(), false);
        Path alpha = targetDirectory.resolve("Alpha.json");
        Path zulu = targetDirectory.resolve("Zulu.json");
        Files.writeString(alpha, "prior alpha", StandardCharsets.UTF_8);
        Files.writeString(zulu, "prior zulu", StandardCharsets.UTF_8);

        AtomicInteger moves = new AtomicInteger();
        assertThrows(IOException.class, () -> BosArtifactPublisher.publishAll(targetDirectory, output,
                (source, target) -> {
                    if (moves.incrementAndGet() == 2)
                        throw new IOException("injected move failure");
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                }));

        assertEquals(3, moves.get());
        assertEquals("prior alpha", Files.readString(alpha, StandardCharsets.UTF_8));
        assertEquals("prior zulu", Files.readString(zulu, StandardCharsets.UTF_8));
        try (var entries = Files.list(targetDirectory)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString()
                    .startsWith(".bs2bg-bos-stage-")).count());
        }
    }
}
