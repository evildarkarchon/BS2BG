package com.asdasfa.jbs2bg;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewProfileTest {
    @TempDir
    Path temporaryDirectory;

    /**
     * A committed legacy pair with leftover transaction backups needs only journal cleanup before adoption.
     *
     * @throws IOException when fixture setup or migration inspection fails
     */
    @Test
    void committedLegacySettingsJournalIsCleanedBeforeMigration() throws IOException {
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = temporaryDirectory.resolve("BS2BG Preview");
        Path standard = Files.writeString(legacy.resolve("settings.json"), "committed-standard");
        Path uunp = Files.writeString(legacy.resolve("settings_UUNP.json"), "committed-uunp");
        Path journal = Files.createDirectory(legacy.resolve(".bs2bg-settings-stage-committed"));
        Files.writeString(journal.resolve("owner"), "BS2BG Settings transaction v1\n");
        Files.writeString(journal.resolve("standard.backup"), "prior-standard");
        Files.writeString(journal.resolve("uunp.backup"), "prior-uunp");
        Files.write(journal.resolve("committed"), new byte[] { 1 });

        PreviewProfile.prepare(profile, legacy);

        assertEquals("committed-standard", Files.readString(profile.resolve("settings.json")));
        assertEquals("committed-uunp", Files.readString(profile.resolve("settings_UUNP.json")));
        assertEquals("committed-standard", Files.readString(standard));
        assertEquals("committed-uunp", Files.readString(uunp));
        assertFalse(Files.exists(journal));
    }

    /**
     * An owned journal with no prior-state markers cannot have changed either live Settings file.
     *
     * @throws IOException when fixture setup or migration inspection fails
     */
    @Test
    void ownerOnlyLegacySettingsJournalIsCleanedBeforeMigration() throws IOException {
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = temporaryDirectory.resolve("BS2BG Preview");
        Files.writeString(legacy.resolve("settings.json"), "legacy-standard");
        Files.writeString(legacy.resolve("settings_UUNP.json"), "legacy-uunp");
        Path journal = Files.createDirectory(legacy.resolve(".bs2bg-settings-stage-owner-only"));
        Files.writeString(journal.resolve("owner"), "BS2BG Settings transaction v1\n");

        PreviewProfile.prepare(profile, legacy);

        assertEquals("legacy-standard", Files.readString(profile.resolve("settings.json")));
        assertEquals("legacy-uunp", Files.readString(profile.resolve("settings_UUNP.json")));
        assertFalse(Files.exists(journal));
    }

    /**
     * An initially empty legacy directory must still defer migration while its Settings writer owns the lock.
     * The retry must adopt the complete pair the writer publishes before releasing it.
     *
     * @throws IOException when fixture setup or migration inspection fails
     */
    @Test
    void emptyLegacyDirectoryWithBusySettingsLockDefersMigration() throws IOException {
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = temporaryDirectory.resolve("BS2BG Preview");
        Path lockPath = legacy.resolve(".bs2bg-settings.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock held = channel.lock()) {
            assertTrue(held.isValid());
            assertThrows(IOException.class, () -> PreviewProfile.prepare(profile, legacy));
            assertFalse(Files.exists(profile.resolve(".bs2bg-preview-migration-complete")));
            Files.writeString(legacy.resolve("settings.json"), "legacy-standard");
            Files.writeString(legacy.resolve("settings_UUNP.json"), "legacy-uunp");
        }

        PreviewProfile.prepare(profile, legacy);

        assertEquals("legacy-standard", Files.readString(profile.resolve("settings.json")));
        assertEquals("legacy-uunp", Files.readString(profile.resolve("settings_UUNP.json")));
    }

    /**
     * A redirected migration lock cannot coordinate launches of the application-owned profile directory.
     *
     * @throws IOException when fixture setup or inspection fails
     */
    @Test
    void migrationLockSymlinkIsRejected() throws IOException {
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectory(temporaryDirectory.resolve("BS2BG Preview"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside.lock"), "outside-lock");
        Files.createSymbolicLink(profile.resolve(".bs2bg-preview-migration.lock"), outside);

        assertThrows(IOException.class, () -> PreviewProfile.prepare(profile, legacy));

        assertEquals("outside-lock", Files.readString(outside));
        assertFalse(Files.exists(profile.resolve(".bs2bg-preview-migration-complete")));
    }

    /**
     * A legacy Settings alias cannot import data from outside the former working-directory profile.
     *
     * @throws IOException when fixture setup or inspection fails
     */
    @Test
    void legacySettingsSymlinkIsRejected() throws IOException {
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = temporaryDirectory.resolve("BS2BG Preview");
        Path outside = Files.writeString(temporaryDirectory.resolve("outside-settings.json"), "outside-standard");
        Files.createSymbolicLink(legacy.resolve("settings.json"), outside);
        Files.writeString(legacy.resolve("settings_UUNP.json"), "legacy-uunp");

        assertThrows(IOException.class, () -> PreviewProfile.prepare(profile, legacy));

        assertEquals("outside-standard", Files.readString(outside));
        assertFalse(Files.exists(profile.resolve("settings.json")));
        assertFalse(Files.exists(profile.resolve(".bs2bg-preview-migration-complete")));
    }
}
