package com.asdasfa.jbs2bg.workbench.morphs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcPortraitFilesTest {

    /** A file bearing the editor ID wins even when a name-only file has an earlier extension. */
    @Test
    void prefersEditorIdFilenameBeforeNameOnlyFilename(@TempDir Path imagesDirectory) throws IOException {
        Path expected = Files.createFile(imagesDirectory.resolve("Lydia (HousecarlWhiterun).bmp"));
        Files.createFile(imagesDirectory.resolve("Lydia.jpg"));
        NpcMorphAssignmentSnapshot npc = new NpcMorphAssignmentSnapshot("Lydia", "Skyrim.esm",
                "HousecarlWhiterun", "NordRace", "A2C94", List.of());

        assertEquals(Optional.of(expected), NpcPortraitFiles.find(imagesDirectory, npc));
    }

    /** JPEG is a dotted extension and follows JPG before PNG for the same filename pattern. */
    @Test
    void recognizesDottedJpegAtItsLegacyPriority(@TempDir Path imagesDirectory) throws IOException {
        Path jpg = Files.createFile(imagesDirectory.resolve("Lydia (HousecarlWhiterun).jpg"));
        Path jpeg = Files.createFile(imagesDirectory.resolve("Lydia (HousecarlWhiterun).jpeg"));
        Files.createFile(imagesDirectory.resolve("Lydia (HousecarlWhiterun).png"));

        assertEquals(Optional.of(jpg), NpcPortraitFiles.find(imagesDirectory, "Lydia", "HousecarlWhiterun"));
        Files.delete(jpg);
        assertEquals(Optional.of(jpeg), NpcPortraitFiles.find(imagesDirectory, "Lydia", "HousecarlWhiterun"));
    }

    /** A missing editor-ID image falls back to the plain display name in extension order. */
    @Test
    void fallsBackToNameOnlyFilename(@TempDir Path imagesDirectory) throws IOException {
        Path expected = Files.createFile(imagesDirectory.resolve("Lydia.jpeg"));
        Files.createFile(imagesDirectory.resolve("Lydia.png"));
        Files.createFile(imagesDirectory.resolve("Lydia.bmp"));

        assertEquals(Optional.of(expected), NpcPortraitFiles.find(imagesDirectory, "Lydia", "HousecarlWhiterun"));
    }

    /** Missing portraits report absence, including a directory bearing an otherwise matching filename. */
    @Test
    void returnsEmptyWhenNoRegularPortraitFileExists(@TempDir Path imagesDirectory) throws IOException {
        Files.createDirectory(imagesDirectory.resolve("Lydia (HousecarlWhiterun).jpg"));

        assertTrue(NpcPortraitFiles.find(imagesDirectory, "Lydia", "HousecarlWhiterun").isEmpty());
    }
}
