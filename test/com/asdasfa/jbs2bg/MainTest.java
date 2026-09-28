package com.asdasfa.jbs2bg;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Runs the actual Application constructor with isolated Windows profile and legacy working directories.
     *
     * @param localAppData isolated LOCALAPPDATA root
     * @param legacyDirectory process working directory used by the previous Preview release
     * @return process exit and captured output for success or failure assertions
     * @throws Exception when the child cannot be started or completed
     */
    private static ProbeResult runPreviewResult(Path localAppData, Path legacyDirectory) throws Exception {
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classPath, MainStartupProbe.class.getName());
        builder.directory(legacyDirectory.toFile());
        builder.environment().put("LOCALAPPDATA", localAppData.toString());
        Process child = builder.redirectErrorStream(true).start();
        try {
            assertTrue(child.waitFor(Duration.ofSeconds(20)), "Preview constructor did not finish");
            String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new ProbeResult(child.exitValue(), output);
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(Duration.ofSeconds(10));
            }
        }
    }

    /** Requires the isolated Preview constructor to initialize successfully. */
    private static void runPreview(Path localAppData, Path legacyDirectory) throws Exception {
        ProbeResult result = runPreviewResult(localAppData, legacyDirectory);
        assertEquals(0, result.exitCode(), result.output());
    }

    /** Holds the isolated constructor outcome and its diagnostic output. */
    private record ProbeResult(int exitCode, String output) {
    }

    /**
     * A fresh Preview launch must create its profile before Settings acquires the directory lock.
     *
     * @throws Exception when isolated process setup or inspection fails
     */
    @Test
    void firstLaunchPublishesSettingsInNewPreviewProfile() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = localAppData.resolve("BS2BG Preview");

        assertFalse(Files.exists(profile));
        runPreview(localAppData, legacy);

        assertTrue(Files.isRegularFile(profile.resolve("settings.json")));
        assertTrue(Files.isRegularFile(profile.resolve("settings_UUNP.json")));
    }

    /**
     * Upgrading from the working-directory Preview profile must retain both Settings and Workbench preferences.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void firstProfileLaunchAdoptsLegacyPreviewState() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(legacy, "standard.json", "settings.json");
        copySettingsFixture(legacy, "uunp.json", "settings_UUNP.json");
        Files.writeString(legacy.resolve("workbench-generation.properties"), "omitRedundantSliders=true\n");
        Files.writeString(legacy.resolve("workbench-appearance.properties"), "theme=DARK\n");

        runPreview(localAppData, legacy);

        for (String name : new String[] { "settings.json", "settings_UUNP.json",
                "workbench-generation.properties", "workbench-appearance.properties" })
            assertArrayEquals(Files.readAllBytes(legacy.resolve(name)), Files.readAllBytes(profile.resolve(name)), name);
    }

    /**
     * A failed legacy copy must not publish defaults, and a later launch must finish adopting the original pair.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void failedLegacyCopyDoesNotConcealSettingsAndRetryCompletesMigration() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(legacy, "standard.json", "settings.json");
        Path badSource = Files.createDirectory(legacy.resolve("settings_UUNP.json"));

        ProbeResult firstAttempt = runPreviewResult(localAppData, legacy);

        assertNotEquals(0, firstAttempt.exitCode(), "a source directory must stop initialization");
        assertFalse(Files.exists(profile.resolve("settings.json")));
        assertFalse(Files.exists(profile.resolve("settings_UUNP.json")));

        Files.delete(badSource);
        copySettingsFixture(legacy, "uunp.json", "settings_UUNP.json");
        runPreview(localAppData, legacy);

        assertArrayEquals(Files.readAllBytes(legacy.resolve("settings.json")),
                Files.readAllBytes(profile.resolve("settings.json")));
        assertArrayEquals(Files.readAllBytes(legacy.resolve("settings_UUNP.json")),
                Files.readAllBytes(profile.resolve("settings_UUNP.json")));
    }

    /**
     * A partial established profile stays authoritative while missing preferences may still migrate independently.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void existingProfileSettingsNeverMixWithLegacyPartner() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(profile, "standard.json", "settings.json");
        Files.writeString(legacy.resolve("settings_UUNP.json"), "{");
        Files.writeString(legacy.resolve("workbench-generation.properties"), "omitRedundantSliders=true\n");
        Files.writeString(legacy.resolve("workbench-appearance.properties"), "theme=DARK\n");
        Files.writeString(profile.resolve("workbench-generation.properties"), "omitRedundantSliders=false\n");

        runPreview(localAppData, legacy);

        assertTrue(Files.isRegularFile(profile.resolve("settings_UUNP.json")));
        assertNotEquals("{", Files.readString(profile.resolve("settings_UUNP.json")));
        assertTrue(Files.isRegularFile(profile.resolve("workbench-generation.properties")));
        assertTrue(Files.isRegularFile(profile.resolve("workbench-appearance.properties")));
        assertEquals("omitRedundantSliders=false", Files.readString(
                profile.resolve("workbench-generation.properties")).trim());
        assertEquals("theme=DARK", Files.readString(profile.resolve("workbench-appearance.properties")).trim());
    }

    /**
     * An interrupted publish resumes its owned staged UUNP side rather than synthesizing a default partner.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void interruptedSettingsMigrationCompletesItsStagedPartner() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(legacy, "standard.json", "settings.json");
        copySettingsFixture(legacy, "uunp.json", "settings_UUNP.json");
        Files.copy(legacy.resolve("settings.json"), profile.resolve("settings.json"));
        Files.copy(legacy.resolve("settings_UUNP.json"), profile.resolve(".bs2bg-preview-uunp.staged"));
        Files.writeString(profile.resolve(".bs2bg-preview-settings-migration"),
                "BS2BG Preview Settings migration v1:11\n");

        runPreview(localAppData, legacy);

        assertArrayEquals(Files.readAllBytes(legacy.resolve("settings.json")),
                Files.readAllBytes(profile.resolve("settings.json")));
        assertArrayEquals(Files.readAllBytes(legacy.resolve("settings_UUNP.json")),
                Files.readAllBytes(profile.resolve("settings_UUNP.json")));
        assertFalse(Files.exists(profile.resolve(".bs2bg-preview-uunp.staged")));
        assertFalse(Files.exists(profile.resolve(".bs2bg-preview-settings-migration")));
    }

    /**
     * A retry before either live move must discard a stale stage and sample the current legacy pair together.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void retryBeforeLivePublicationRestagesTheCurrentLegacyPair() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(legacy, "standard.json", "settings.json");
        copySettingsFixture(legacy, "uunp.json", "settings_UUNP.json");
        Files.copy(legacy.resolve("settings.json"), profile.resolve(".bs2bg-preview-standard.staged"));
        Files.writeString(profile.resolve(".bs2bg-preview-settings-migration"),
                "BS2BG Preview Settings migration v1:11\n");
        Files.writeString(legacy.resolve("settings.json"),
                "{\"Defaults\":{},\"Multipliers\":{\"Exponent\":7.0},\"Inverted\":[]}");

        runPreview(localAppData, legacy);

        assertArrayEquals(Files.readAllBytes(legacy.resolve("settings.json")),
                Files.readAllBytes(profile.resolve("settings.json")));
        assertArrayEquals(Files.readAllBytes(legacy.resolve("settings_UUNP.json")),
                Files.readAllBytes(profile.resolve("settings_UUNP.json")));
    }

    /**
     * A complete staged pair remains the committed migration snapshot when its former source later changes.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void retryRetainsCompleteStagedPairAfterLegacyPartnerDisappears() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(legacy, "standard.json", "settings.json");
        copySettingsFixture(legacy, "uunp.json", "settings_UUNP.json");
        byte[] originalStandard = Files.readAllBytes(legacy.resolve("settings.json"));
        byte[] originalUunp = Files.readAllBytes(legacy.resolve("settings_UUNP.json"));
        Files.copy(legacy.resolve("settings.json"), profile.resolve(".bs2bg-preview-standard.staged"));
        Files.copy(legacy.resolve("settings_UUNP.json"), profile.resolve(".bs2bg-preview-uunp.staged"));
        Files.writeString(profile.resolve(".bs2bg-preview-settings-migration"),
                "BS2BG Preview Settings migration v1:11\n");
        Files.writeString(legacy.resolve("settings.json"),
                "{\"Defaults\":{},\"Multipliers\":{\"Exponent\":7.0},\"Inverted\":[]}");
        Files.delete(legacy.resolve("settings_UUNP.json"));

        runPreview(localAppData, legacy);

        assertArrayEquals(originalStandard, Files.readAllBytes(profile.resolve("settings.json")));
        assertArrayEquals(originalUunp, Files.readAllBytes(profile.resolve("settings_UUNP.json")));
    }

    /**
     * Once one Settings side is live, a lost staged partner cannot be replaced from a newer legacy generation.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void missingStageAfterOneLiveSideFailsClosed() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(legacy, "standard.json", "settings.json");
        copySettingsFixture(legacy, "uunp.json", "settings_UUNP.json");
        Files.copy(legacy.resolve("settings.json"), profile.resolve("settings.json"));
        Files.writeString(profile.resolve(".bs2bg-preview-settings-migration"),
                "BS2BG Preview Settings migration v1:11\n");

        ProbeResult result = runPreviewResult(localAppData, legacy);

        assertNotEquals(0, result.exitCode(), "a missing staged partner must stop recovery");
        assertTrue(result.output().contains("stage is unavailable"), result.output());
        assertFalse(Files.exists(profile.resolve("settings_UUNP.json")));
        assertTrue(Files.exists(profile.resolve(".bs2bg-preview-settings-migration")));
    }

    /**
     * A legacy Settings transaction must be recovered by its owner before Preview can copy an incomplete live pair.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void ownedLegacySettingsTransactionStopsMigrationWithoutChangingLegacyState() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = localAppData.resolve("BS2BG Preview");
        copySettingsFixture(legacy, "standard.json", "settings.json");
        Path transaction = Files.createDirectory(legacy.resolve(".bs2bg-settings-stage-interrupted"));
        Files.writeString(transaction.resolve("owner"), "BS2BG Settings transaction v1\n");
        copySettingsFixture(transaction, "uunp.json", "uunp.backup");
        byte[] standardBefore = Files.readAllBytes(legacy.resolve("settings.json"));
        byte[] uunpBackupBefore = Files.readAllBytes(transaction.resolve("uunp.backup"));

        ProbeResult result = runPreviewResult(localAppData, legacy);

        assertNotEquals(0, result.exitCode(), "an owned legacy transaction must stop migration");
        assertTrue(result.output().contains("Legacy Preview Settings transaction"), result.output());
        assertFalse(Files.exists(profile.resolve("settings.json")));
        assertFalse(Files.exists(profile.resolve("settings_UUNP.json")));
        assertFalse(Files.exists(profile.resolve(".bs2bg-preview-migration-complete")));
        assertArrayEquals(standardBefore, Files.readAllBytes(legacy.resolve("settings.json")));
        assertArrayEquals(uunpBackupBefore, Files.readAllBytes(transaction.resolve("uunp.backup")));
        assertFalse(Files.exists(legacy.resolve("settings_UUNP.json")));
    }

    /**
     * Preview must not sample legacy Settings while the former application owns its paired publication lock.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void busyLegacySettingsLockDefersMigrationUntilTheNextLaunch() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = localAppData.resolve("BS2BG Preview");
        copySettingsFixture(legacy, "standard.json", "settings.json");
        copySettingsFixture(legacy, "uunp.json", "settings_UUNP.json");
        try (FileChannel channel = FileChannel.open(legacy.resolve(".bs2bg-settings.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock held = channel.lock()) {
            assertTrue(held.isValid());
            ProbeResult result = runPreviewResult(localAppData, legacy);
            assertNotEquals(0, result.exitCode(), "a busy legacy Settings lock must defer migration");
            assertTrue(result.output().contains("Legacy Preview Settings lock"), result.output());
            assertFalse(Files.exists(profile.resolve("settings.json")));
            assertFalse(Files.exists(profile.resolve(".bs2bg-preview-migration-complete")));
        }

        runPreview(localAppData, legacy);

        assertArrayEquals(Files.readAllBytes(legacy.resolve("settings.json")),
                Files.readAllBytes(profile.resolve("settings.json")));
        assertArrayEquals(Files.readAllBytes(legacy.resolve("settings_UUNP.json")),
                Files.readAllBytes(profile.resolve("settings_UUNP.json")));
    }

    /**
     * Established profile Settings do not depend on the former directory's active writer or recovery journal.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void establishedProfileSettingsIgnoreBusyLegacyWriterAndJournal() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(profile, "standard.json", "settings.json");
        copySettingsFixture(profile, "uunp.json", "settings_UUNP.json");
        Path journal = Files.createDirectory(legacy.resolve(".bs2bg-settings-stage-interrupted"));
        Files.writeString(journal.resolve("owner"), "BS2BG Settings transaction v1\n");
        try (FileChannel channel = FileChannel.open(legacy.resolve(".bs2bg-settings.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock held = channel.lock()) {
            assertTrue(held.isValid());
            runPreview(localAppData, legacy);
        }

        assertArrayEquals(Files.readAllBytes(Path.of("test-resources", "json-oracles", "settings", "standard.json")),
                Files.readAllBytes(profile.resolve("settings.json")));
        assertTrue(Files.exists(journal.resolve("owner")));
    }

    /**
     * Legacy state is adopted once; a later removal of profile choices must not restore stale working-dir values.
     *
     * @throws Exception when fixture setup or isolated process inspection fails
     */
    @Test
    void laterProfileResetDoesNotReimportLegacyChoices() throws Exception {
        Path localAppData = temporaryDirectory.resolve("LocalAppData");
        Path legacy = Files.createDirectory(temporaryDirectory.resolve("legacy"));
        Path profile = Files.createDirectories(localAppData.resolve("BS2BG Preview"));
        copySettingsFixture(legacy, "standard.json", "settings.json");
        copySettingsFixture(legacy, "uunp.json", "settings_UUNP.json");
        Files.writeString(legacy.resolve("workbench-generation.properties"), "omitRedundantSliders=true\n");
        Files.writeString(legacy.resolve("workbench-appearance.properties"), "theme=DARK\n");
        runPreview(localAppData, legacy);
        Files.delete(profile.resolve("settings.json"));
        Files.delete(profile.resolve("settings_UUNP.json"));
        Files.delete(profile.resolve("workbench-generation.properties"));
        Files.delete(profile.resolve("workbench-appearance.properties"));

        runPreview(localAppData, legacy);

        assertFalse(Files.exists(profile.resolve("workbench-generation.properties")));
        assertFalse(Files.exists(profile.resolve("workbench-appearance.properties")));
        assertNotEquals(Files.readString(legacy.resolve("settings.json")),
                Files.readString(profile.resolve("settings.json")));
    }

    /** Copies a permanent valid Settings oracle into a former Preview working directory. */
    private static void copySettingsFixture(Path directory, String fixture, String destination) throws IOException {
        Files.copy(Path.of("test-resources", "json-oracles", "settings", fixture), directory.resolve(destination));
    }
}
