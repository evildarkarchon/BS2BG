package com.asdasfa.jbs2bg.workbench.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

        SettingsFeature.Update saved = feature.dispatch(new SettingsFeature.Save());

        assertTrue(saved.accepted());
        assertEquals(SettingsFeature.OutcomeKind.SAVED, saved.frame().outcome());
        assertFalse(saved.frame().dirty());
        assertEquals(2f, Settings.snapshot().standard().multipliers().get("Waist"));
        assertEquals(0.25f, Settings.snapshot().uunp().defaults().get("New Slider").getValueSmall());
        assertTrue(Settings.initialize(directory).isSuccessful());
        assertEquals(0.75f, Settings.snapshot().uunp().defaults().get("New Slider").getValueBig());
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

        SettingsFeature.Update recovered = feature.dispatch(new SettingsFeature.Reload());

        assertTrue(recovered.accepted());
        assertEquals(SettingsFeature.OutcomeKind.RECOVERED, recovered.frame().outcome());
        assertEquals("SETTINGS_PUBLICATION_RECOVERED", recovered.frame().notices().get(0).code());
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
}
