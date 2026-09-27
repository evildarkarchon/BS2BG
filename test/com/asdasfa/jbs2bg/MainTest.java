package com.asdasfa.jbs2bg;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {
    @TempDir
    Path temporaryDirectory;

    /** Preview state belongs beneath LOCALAPPDATA and cannot overlap the stable profile. */
    @Test
    void previewProfileUsesItsOwnLocalAppDataDirectory() {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");

        Path preview = Main.previewProfileDirectory(localAppData.toString());

        assertEquals(localAppData.resolve("BS2BG Preview"), preview);
    }

    /** A missing or relative LOCALAPPDATA must not silently place state in the current directory. */
    @Test
    void previewProfileRejectsMissingOrRelativeLocalAppData() {
        assertThrows(IllegalArgumentException.class, () -> Main.previewProfileDirectory(null));
        assertThrows(IllegalArgumentException.class, () -> Main.previewProfileDirectory(" "));
        assertThrows(IllegalArgumentException.class, () -> Main.previewProfileDirectory("relative"));
    }
}
