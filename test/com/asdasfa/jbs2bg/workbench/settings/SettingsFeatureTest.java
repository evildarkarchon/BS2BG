package com.asdasfa.jbs2bg.workbench.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.workbench.GenerationPreferencesStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Workbench Settings behavior through immutable feature frames and task-oriented intents.
 */
final class SettingsFeatureTest {

    /** Restores process-wide output settings after every isolated feature publication. */
    @AfterEach
    void restoreRepositorySettings() {
        SettingsTestSupport.restoreRepositorySettings();
    }

    /**
     * Invalid numeric drafts remain visible without changing the pair, while accepted Standard and UUNP edits save
     * together and become the live Settings value.
     *
     * @param directory isolated Settings directory
     * @throws Exception when fixtures cannot be copied
     */
    @Test
    void validatesDraftsAndPersistsBothProfilesTogether(@TempDir Path directory) throws Exception {
        Path fixtures = Path.of("test-resources", "json-oracles", "settings");
        Files.copy(fixtures.resolve("standard.json"), directory.resolve("settings.json"));
        Files.copy(fixtures.resolve("uunp.json"), directory.resolve("settings_UUNP.json"));
        Settings.InitializationResult initialized = Settings.initialize(directory);
        assertTrue(initialized.isSuccessful());
        SettingsFeature feature = new SettingsFeature(directory, initialized);

        SettingsFeature.Update rejected = feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("not-a-number"), Optional.of("1"), Optional.of("2"), true));

        assertFalse(rejected.accepted());
        assertEquals(SettingsFeature.OutcomeKind.REJECTED, rejected.frame().outcome());
        assertEquals("not-a-number", rejected.frame().editor().orElseThrow().small());
        assertFalse(rejected.frame().validation().isEmpty());
        assertEquals(2f, Settings.getMultiplier("Exponent"));

        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), true)).accepted());
        assertTrue(feature.dispatch(new SettingsFeature.SelectProfile(SettingsFeature.Profile.UUNP)).accepted());
        assertTrue(feature.dispatch(new SettingsFeature.AddEntry("New Slider")).accepted());
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "New Slider", "New Slider", Optional.of("0.25"), Optional.of("0.75"),
                Optional.empty(), false)).accepted());

        SettingsFeature.Update requested = feature.dispatch(new SettingsFeature.Save());

        SettingsFeature.SaveEffect save = assertInstanceOf(SettingsFeature.SaveEffect.class,
                requested.effect().orElseThrow());
        assertTrue(requested.frame().dirty());
        assertEquals(2f, save.replacement().standard().multipliers().get("Waist"));
        assertFalse(Settings.snapshot().standard().multipliers().containsKey("Waist"));

        Settings.PersistenceResult persistence = Settings.persist(save.directory(), save.replacement());
        SettingsFeature.Update saved = feature.complete(new SettingsFeature.SaveCompletion(save.token(), persistence));

        assertTrue(saved.accepted());
        assertEquals(SettingsFeature.OutcomeKind.SAVED, saved.frame().outcome());
        assertFalse(saved.frame().dirty());
        assertEquals(2f, Settings.snapshot().standard().multipliers().get("Waist"));
        assertEquals(0.25f, Settings.snapshot().uunp().defaults().get("New Slider").getValueSmall());
        assertTrue(Settings.initialize(directory).isSuccessful());
        assertEquals(0.75f, Settings.snapshot().uunp().defaults().get("New Slider").getValueBig());
    }

    /** Names beyond the Settings reader's UTF-8 token limit are rejected before they can dirty either draft. */
    @Test
    void rejectsEntryNamesBeyondTheReaderUtf8Limit(@TempDir Path directory) {
        assertTrue(Settings.initialize(directory).isSuccessful());
        SettingsFeature feature = new SettingsFeature(directory, Settings.publishedState());
        String oversizedName = "😀".repeat(262_145);

        SettingsFeature.Update rejected = feature.dispatch(new SettingsFeature.AddEntry(oversizedName));

        assertFalse(rejected.accepted());
        assertFalse(rejected.frame().dirty());
        assertEquals("SETTINGS_NAME_RESOURCE_LIMIT", rejected.frame().validation().getFirst().code());
        assertTrue(rejected.frame().entries().stream().noneMatch(entry -> entry.name().equals(oversizedName)));
    }

    /** A failed Save clears its token so an explicit retry captures the latest draft rather than stale inputs. */
    @Test
    void failedSaveRetryRecapturesTheLatestDraft(@TempDir Path directory) throws Exception {
        assertTrue(Settings.initialize(directory).isSuccessful());
        SettingsFeature feature = new SettingsFeature(directory, Settings.publishedState());
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.SaveEffect first = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());
        Files.deleteIfExists(directory.resolve("settings.json"));
        Files.deleteIfExists(directory.resolve("settings_UUNP.json"));
        Files.deleteIfExists(directory.resolve(".bs2bg-settings.lock"));
        Files.delete(directory);
        Settings.PersistenceResult failedPersistence = Settings.persist(first.directory(), first.replacement());

        SettingsFeature.Update failed = feature.complete(
                new SettingsFeature.SaveCompletion(first.token(), failedPersistence));
        assertFalse(failed.accepted());
        assertTrue(failed.frame().dirty());
        assertEquals(SettingsFeature.OutcomeKind.FAILED, failed.frame().outcome());
        assertFalse(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("invalid"), Optional.of("1"), Optional.of("3"), false)).accepted());
        assertEquals("Settings validation must be resolved before saving.",
                feature.saveRetryUnavailableReason().orElseThrow());
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("3"), false)).accepted());

        SettingsFeature.SaveEffect retry = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());

        assertTrue(retry.token() > first.token());
        assertEquals(3f, retry.replacement().standard().multipliers().get("Waist"));
    }

    /** A captured persistence effect freezes every draft mutation while allowing profile and entry browsing. */
    @Test
    void pendingSaveRejectsDraftMutationsUntilCompletion(@TempDir Path directory) {
        assertTrue(Settings.initialize(directory).isSuccessful());
        SettingsFeature feature = new SettingsFeature(directory, Settings.publishedState());
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.Update requested = feature.dispatch(new SettingsFeature.Save());
        SettingsFeature.SaveEffect save = assertInstanceOf(SettingsFeature.SaveEffect.class,
                requested.effect().orElseThrow());

        assertFalse(feature.dispatch(new SettingsFeature.AddEntry("Queued Add")).accepted());
        assertFalse(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("3"), false)).accepted());
        assertFalse(feature.dispatch(new SettingsFeature.RemoveEntry("Waist")).accepted());
        assertFalse(feature.dispatch(new SettingsFeature.ChangeOmitRedundantSliders(true)).accepted());
        assertTrue(feature.dispatch(new SettingsFeature.SelectProfile(SettingsFeature.Profile.UUNP)).accepted());
        assertTrue(feature.dispatch(new SettingsFeature.SelectProfile(SettingsFeature.Profile.STANDARD)).accepted());
        assertEquals(2f, save.replacement().standard().multipliers().get("Waist"));
        assertEquals("2.0", feature.frame().editor().orElseThrow().multiplier());
    }

    /**
     * Reload recovers an interrupted paired publication, removes the journal, and exposes durable recovery evidence.
     */
    @Test
    void reloadRecoversInterruptedPairAndPublishesTheWarning(@TempDir Path directory) throws Exception {
        Path fixtures = Path.of("test-resources", "json-oracles", "settings");
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        Files.copy(fixtures.resolve("standard.json"), standard);
        Files.copy(fixtures.resolve("uunp.json"), uunp);
        Settings.InitializationResult initialized = Settings.initialize(directory);
        SettingsFeature feature = new SettingsFeature(directory, initialized);
        byte[] priorStandard = Files.readAllBytes(standard);
        byte[] priorUunp = Files.readAllBytes(uunp);
        Path transaction = Files.createDirectory(directory.resolve(".bs2bg-settings-stage-workbench"));
        Files.move(standard, transaction.resolve("standard.backup"));
        Files.move(uunp, transaction.resolve("uunp.backup"));
        Files.copy(fixtures.resolve("standard.canonical.json"), standard);

        SettingsFeature.Update requested = feature.dispatch(new SettingsFeature.Reload());
        SettingsFeature.ReloadEffect reload = assertInstanceOf(SettingsFeature.ReloadEffect.class,
                requested.effect().orElseThrow());
        assertTrue(Files.exists(transaction));

        Settings.InitializationResult initialization = Settings.initialize(reload.directory());
        SettingsFeature.Update recovered = feature.complete(
                new SettingsFeature.ReloadCompletion(reload.token(), initialization));

        assertTrue(recovered.accepted());
        assertEquals(SettingsFeature.OutcomeKind.RECOVERED, recovered.frame().outcome());
        assertEquals("SETTINGS_PUBLICATION_RECOVERED", recovered.frame().notices().getFirst().code());
        assertTrue(recovered.frame().notices().stream()
                .anyMatch(notice -> notice.code().equals("SETTINGS_MEMBER_UNKNOWN")));
        assertArrayEquals(priorStandard, Files.readAllBytes(standard));
        assertArrayEquals(priorUunp, Files.readAllBytes(uunp));
        assertFalse(Files.exists(transaction));
    }

    /** The migrated output omission choice persists independently of the paired slider-configuration documents. */
    @Test
    void omitRedundantSlidersPersistsInTheProfileLocalGenerationStore(@TempDir Path directory) throws Exception {
        assertTrue(Settings.initialize(directory).isSuccessful());
        GenerationPreferencesStore store = new GenerationPreferencesStore(directory);
        store.save(false);
        SettingsFeature feature = new SettingsFeature(directory, Settings.publishedState());

        SettingsFeature.Update changed = feature.dispatch(
                new SettingsFeature.ChangeOmitRedundantSliders(true));

        assertTrue(changed.accepted());
        assertTrue(changed.frame().omitRedundantSliders());
        assertTrue(new GenerationPreferencesStore(directory).loadOrMigrate());
        SettingsFeature reopened = new SettingsFeature(directory, Settings.publishedState());
        assertTrue(reopened.frame().omitRedundantSliders());
    }

    /** First packaged use writes the legacy omission choice exactly once into the profile-local store. */
    @Test
    void packagedGenerationPreferenceCompletesTheLegacyMigration(@TempDir Path directory) throws Exception {
        GenerationPreferencesStore store = new GenerationPreferencesStore(directory);

        boolean migrated = store.loadOrMigrate();

        assertTrue(Files.isRegularFile(directory.resolve("workbench-generation.properties")));
        assertEquals(migrated, store.loadOrMigrate());
    }
}
