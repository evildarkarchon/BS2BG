package com.asdasfa.jbs2bg.workbench.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.workbench.GenerationPreferencesStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Workbench Settings behavior through immutable feature frames and task-oriented intents.
 */
final class SettingsFeatureTest {

    /**
     * The feature declares which intents can change live or on-disk Settings, so the dispatch pipeline pauses only
     * those while a job blocks mutations and profile browsing stays available.
     *
     * @param directory isolated Settings directory
     */
    @Test
    void declaresSettingsChangesAsMutationsAndBrowsingAsLocal(@TempDir Path directory) {
        Settings store = new Settings(directory);
        SettingsFeature feature = new SettingsFeature(store, store.reload());

        for (SettingsFeature.Intent mutation : java.util.List.of(
                new SettingsFeature.AddEntry("Waist"),
                new SettingsFeature.EditEntry("Waist", "Waist", Optional.of("0"), Optional.of("1"),
                        Optional.of("1"), false),
                new SettingsFeature.RemoveEntry("Waist"),
                new SettingsFeature.ChangeOmitRedundantSliders(true),
                new SettingsFeature.Save(),
                new SettingsFeature.Reload()))
            assertTrue(feature.isMutation(mutation), mutation.toString());
        for (SettingsFeature.Intent local : java.util.List.of(
                new SettingsFeature.SelectProfile(SettingsFeature.Profile.UUNP),
                new SettingsFeature.SelectEntry("Waist"),
                new SettingsFeature.DismissNotice()))
            assertFalse(feature.isMutation(local), local.toString());
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
        Settings store = new Settings(directory);
        Path fixtures = Path.of("test-resources", "json-oracles", "settings");
        Files.copy(fixtures.resolve("standard.json"), directory.resolve("settings.json"));
        Files.copy(fixtures.resolve("uunp.json"), directory.resolve("settings_UUNP.json"));
        Settings.InitializationResult initialized = store.reload();
        assertTrue(initialized.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, initialized);

        SettingsFeature.Update rejected = feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("not-a-number"), Optional.of("1"), Optional.of("2"), true));

        assertFalse(rejected.accepted());
        assertEquals(SettingsFeature.OutcomeKind.REJECTED, rejected.frame().outcome());
        assertEquals("not-a-number", rejected.frame().editor().orElseThrow().small());
        assertFalse(rejected.frame().validation().isEmpty());
        assertEquals(2f, store.getMultiplier("Exponent"));

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
        assertFalse(store.snapshot().standard().multipliers().containsKey("Waist"));

        Settings.PersistenceResult persistence = store.persist(save.replacement());
        SettingsFeature.Update saved = feature.complete(new SettingsFeature.SaveCompletion(save.token(), persistence));

        assertTrue(saved.accepted());
        assertEquals(SettingsFeature.OutcomeKind.SAVED, saved.frame().outcome());
        assertFalse(saved.frame().dirty());
        assertEquals(2f, store.snapshot().standard().multipliers().get("Waist"));
        assertEquals(0.25f, store.snapshot().uunp().defaults().get("New Slider").getValueSmall());
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        assertEquals(0.75f, store.snapshot().uunp().defaults().get("New Slider").getValueBig());
    }

    /** Names beyond the Settings reader's UTF-8 token limit are rejected before they can dirty either draft. */
    @Test
    void rejectsEntryNamesBeyondTheReaderUtf8Limit(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
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
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.SaveEffect first = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());
        Files.deleteIfExists(directory.resolve("settings.json"));
        Files.deleteIfExists(directory.resolve("settings_UUNP.json"));
        Files.deleteIfExists(directory.resolve(".bs2bg-settings.lock"));
        Files.delete(directory);
        Settings.PersistenceResult failedPersistence = store.persist(first.replacement());

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
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
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

    /** Cancelling dirty Reload confirmation retains both drafts and admits no worker operation. */
    @Test
    void dirtyReloadCancellationRetainsTheDraft(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());

        SettingsFeature.Update requested = feature.dispatch(new SettingsFeature.Reload());
        SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                SettingsFeature.ReloadConfirmationEffect.class, requested.effect().orElseThrow());
        SettingsFeature.Update cancelled = feature.respondReload(
                confirmation.token(), SettingsFeature.ReloadDecision.CANCEL);

        assertTrue(cancelled.accepted());
        assertTrue(cancelled.effect().isEmpty());
        assertTrue(cancelled.frame().dirty());
        assertEquals("2.0", cancelled.frame().editor().orElseThrow().multiplier());
        assertEquals(1f, store.getMultiplier("Waist"));
    }

    /** Removing one exact case variant retains inversion for the other visible Settings row. */
    @Test
    void removingCaseDistinctEntryRetainsInversionForRemainingEntry(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        Settings.Snapshot loaded = store.snapshot();
        LinkedHashMap<String, Float> multipliers = new LinkedHashMap<>(loaded.standard().multipliers());
        multipliers.put("Foo", 2f);
        multipliers.put("foo", 3f);
        ArrayList<String> inverted = new ArrayList<>(loaded.standard().inverted());
        inverted.add("Foo");
        Settings.Snapshot prepared = new Settings.Snapshot(new Settings.Profile(
                loaded.standard().defaults(), multipliers, inverted), loaded.uunp());
        assertTrue(store.persist(prepared).isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);

        SettingsFeature.Update removed = feature.dispatch(new SettingsFeature.RemoveEntry("Foo"));

        assertTrue(removed.accepted());
        assertTrue(removed.frame().entries().stream().noneMatch(entry -> entry.name().equals("Foo")));
        SettingsFeature.EntryFrame remaining = removed.frame().entries().stream()
                .filter(entry -> entry.name().equals("foo")).findFirst().orElseThrow();
        assertEquals(3f, remaining.multiplier().orElseThrow());
        assertTrue(remaining.inverted());
        SettingsFeature.SaveEffect save = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());
        assertTrue(save.replacement().standard().inverted().contains("foo"));
        assertFalse(save.replacement().standard().inverted().contains("Foo"));
    }

    /** Editing Inverted off clears the shared case-insensitive family even when another exact row remains. */
    @Test
    void editingCaseDistinctEntryCanClearSharedInversion(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        Settings.Snapshot loaded = store.snapshot();
        LinkedHashMap<String, Float> multipliers = new LinkedHashMap<>(loaded.standard().multipliers());
        multipliers.put("Foo", 2f);
        multipliers.put("foo", 3f);
        ArrayList<String> inverted = new ArrayList<>(loaded.standard().inverted());
        inverted.add("Foo");
        assertTrue(store.persist(new Settings.Snapshot(new Settings.Profile(
                loaded.standard().defaults(), multipliers, inverted), loaded.uunp())).isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);

        SettingsFeature.Update edited = feature.dispatch(new SettingsFeature.EditEntry(
                "Foo", "Foo", Optional.empty(), Optional.empty(), Optional.of("2"), false));

        assertTrue(edited.accepted());
        assertFalse(edited.frame().entries().stream().filter(entry -> entry.name().equals("Foo"))
                .findFirst().orElseThrow().inverted());
        assertFalse(edited.frame().entries().stream().filter(entry -> entry.name().equals("foo"))
                .findFirst().orElseThrow().inverted());
        SettingsFeature.SaveEffect save = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());
        assertTrue(save.replacement().standard().inverted().stream()
                .noneMatch(name -> name.equalsIgnoreCase("Foo")));
    }

    /** Removing an exact row preserves a different exact inversion-only row in the same lookup family. */
    @Test
    void removingCaseDistinctEntryPreservesDifferentInversionToken(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        Settings.Snapshot loaded = store.snapshot();
        LinkedHashMap<String, Float> multipliers = new LinkedHashMap<>(loaded.standard().multipliers());
        multipliers.put("Foo", 2f);
        multipliers.put("foo", 3f);
        ArrayList<String> inverted = new ArrayList<>(loaded.standard().inverted());
        inverted.add("FOO");
        assertTrue(store.persist(new Settings.Snapshot(new Settings.Profile(
                loaded.standard().defaults(), multipliers, inverted), loaded.uunp())).isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);

        SettingsFeature.Update removed = feature.dispatch(new SettingsFeature.RemoveEntry("Foo"));

        assertTrue(removed.accepted());
        assertTrue(removed.frame().entries().stream().noneMatch(entry -> entry.name().equals("Foo")));
        assertTrue(removed.frame().entries().stream().anyMatch(entry -> entry.name().equals("FOO")));
        assertTrue(removed.frame().entries().stream().filter(entry -> entry.name().equals("foo"))
                .findFirst().orElseThrow().inverted());
        SettingsFeature.SaveEffect save = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());
        assertTrue(save.replacement().standard().inverted().contains("FOO"));
    }

    /** Discarding dirty Reload confirmation admits disk reload and replaces the draft only after success. */
    @Test
    void dirtyReloadDiscardReplacesTheDraftAfterCompletion(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                SettingsFeature.ReloadConfirmationEffect.class,
                feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());

        SettingsFeature.Update discarded = feature.respondReload(
                confirmation.token(), SettingsFeature.ReloadDecision.DISCARD);
        SettingsFeature.ReloadEffect reload = assertInstanceOf(
                SettingsFeature.ReloadEffect.class, discarded.effect().orElseThrow());

        assertTrue(discarded.frame().dirty());
        SettingsFeature.Update reloaded = feature.complete(new SettingsFeature.ReloadCompletion(
                reload.token(), store.reload()));
        assertTrue(reloaded.accepted());
        assertFalse(reloaded.frame().dirty());
        assertEquals(SettingsFeature.OutcomeKind.RELOADED, reloaded.frame().outcome());
        assertEquals("", reloaded.frame().editor().orElseThrow().multiplier());
    }

    /** A failed Reload cannot reuse its old discard decision after the Settings draft changes again. */
    @Test
    void failedConfirmedReloadRetryRequiresFreshConfirmationForNewDrafts(@TempDir Path directory)
            throws Exception {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                SettingsFeature.ReloadConfirmationEffect.class,
                feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());
        SettingsFeature.ReloadEffect reload = assertInstanceOf(SettingsFeature.ReloadEffect.class,
                feature.respondReload(confirmation.token(), SettingsFeature.ReloadDecision.DISCARD)
                        .effect().orElseThrow());
        Files.writeString(directory.resolve("settings.json"), "{");

        SettingsFeature.Update failed = feature.complete(new SettingsFeature.ReloadCompletion(
                reload.token(), store.reload()));
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("3"), false)).accepted());
        SettingsFeature.Update retried = feature.retry(reload);

        assertFalse(failed.accepted());
        assertFalse(retried.accepted());
        assertTrue(retried.effect().isEmpty());
        assertTrue(retried.frame().dirty());
        assertEquals("3.0", retried.frame().editor().orElseThrow().multiplier());
        assertInstanceOf(SettingsFeature.ReloadConfirmationEffect.class,
                feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());
    }

    /** Saving dirty Reload confirmation persists the draft before continuing the original Reload intent. */
    @Test
    void dirtyReloadSavePersistsTheDraftBeforeReloading(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                SettingsFeature.ReloadConfirmationEffect.class,
                feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());

        SettingsFeature.Update saveRequested = feature.respondReload(
                confirmation.token(), SettingsFeature.ReloadDecision.SAVE);
        SettingsFeature.SaveEffect save = assertInstanceOf(
                SettingsFeature.SaveEffect.class, saveRequested.effect().orElseThrow());

        assertEquals(2f, save.replacement().standard().multipliers().get("Waist"));
        SettingsFeature.Update saved = feature.complete(new SettingsFeature.SaveCompletion(
                save.token(), store.persist(save.replacement())));
        SettingsFeature.ReloadEffect reload = assertInstanceOf(
                SettingsFeature.ReloadEffect.class, saved.effect().orElse(null));
        assertTrue(saved.accepted());
        assertFalse(saved.frame().dirty());
        assertEquals(SettingsFeature.OutcomeKind.SAVED, saved.frame().outcome());
        assertEquals(2f, store.getMultiplier("Waist"));

        SettingsFeature.Update reloaded = feature.complete(new SettingsFeature.ReloadCompletion(
                reload.token(), store.reload()));

        assertTrue(reloaded.accepted());
        assertTrue(reloaded.effect().isEmpty());
        assertFalse(reloaded.frame().dirty());
        assertEquals(SettingsFeature.OutcomeKind.RELOADED, reloaded.frame().outcome());
        assertEquals("2.0", reloaded.frame().editor().orElseThrow().multiplier());
    }

    /** A failed Save during dirty Reload retains both the draft and the Save-then-Reload retry continuation. */
    @Test
    void failedDirtyReloadSaveRetryStillContinuesToReload(@TempDir Path directory) throws Exception {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                SettingsFeature.ReloadConfirmationEffect.class,
                feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());
        SettingsFeature.SaveEffect first = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.respondReload(confirmation.token(), SettingsFeature.ReloadDecision.SAVE)
                        .effect().orElseThrow());
        Files.deleteIfExists(directory.resolve("settings.json"));
        Files.deleteIfExists(directory.resolve("settings_UUNP.json"));
        Files.deleteIfExists(directory.resolve(".bs2bg-settings.lock"));
        Files.delete(directory);

        SettingsFeature.Update failed = feature.complete(new SettingsFeature.SaveCompletion(
                first.token(), store.persist(first.replacement())));
        Files.createDirectories(directory);
        SettingsFeature.SaveEffect retry = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.retry(first).effect().orElseThrow());
        SettingsFeature.Update saved = feature.complete(new SettingsFeature.SaveCompletion(
                retry.token(), store.persist(retry.replacement())));

        assertFalse(failed.accepted());
        assertTrue(failed.frame().dirty());
        assertTrue(retry.token() > first.token());
        assertInstanceOf(SettingsFeature.ReloadEffect.class, saved.effect().orElse(null));
    }

    /** Cancelling the Save worker retains the dirty Reload continuation for an explicit retry. */
    @Test
    void cancelledDirtyReloadSaveRetryStillContinuesToReload(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                SettingsFeature.ReloadConfirmationEffect.class,
                feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());
        SettingsFeature.SaveEffect first = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.respondReload(confirmation.token(), SettingsFeature.ReloadDecision.SAVE)
                        .effect().orElseThrow());

        SettingsFeature.Update cancelled = feature.cancel(first.token());
        SettingsFeature.SaveEffect retry = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.retry(first).effect().orElseThrow());
        SettingsFeature.Update saved = feature.complete(new SettingsFeature.SaveCompletion(
                retry.token(), store.persist(retry.replacement())));

        assertTrue(cancelled.accepted());
        assertTrue(cancelled.frame().dirty());
        assertTrue(retry.token() > first.token());
        assertInstanceOf(SettingsFeature.ReloadEffect.class, saved.effect().orElse(null));
    }

    /** An ordinary Save after failed Reload-triggered Save abandons that old Reload instead of inheriting it. */
    @Test
    void freshSaveAfterFailedReloadSaveDoesNotContinueTheReload(@TempDir Path directory) throws Exception {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                SettingsFeature.ReloadConfirmationEffect.class,
                feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());
        SettingsFeature.SaveEffect first = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.respondReload(confirmation.token(), SettingsFeature.ReloadDecision.SAVE)
                        .effect().orElseThrow());
        Files.deleteIfExists(directory.resolve("settings.json"));
        Files.deleteIfExists(directory.resolve("settings_UUNP.json"));
        Files.deleteIfExists(directory.resolve(".bs2bg-settings.lock"));
        Files.delete(directory);
        SettingsFeature.Update failed = feature.complete(new SettingsFeature.SaveCompletion(
                first.token(), store.persist(first.replacement())));
        Files.createDirectories(directory);

        SettingsFeature.SaveEffect fresh = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());
        SettingsFeature.Update saved = feature.complete(new SettingsFeature.SaveCompletion(
                fresh.token(), store.persist(fresh.replacement())));

        assertFalse(failed.accepted());
        assertTrue(failed.frame().dirty());
        assertTrue(saved.accepted());
        assertTrue(saved.effect().isEmpty());
        assertFalse(saved.frame().dirty());
        assertEquals(SettingsFeature.OutcomeKind.SAVED, saved.frame().outcome());
    }

    /** An ordinary Save after cancelled Reload-triggered Save does not act as Activity retry for the old token. */
    @Test
    void freshSaveAfterCancelledReloadSaveDoesNotContinueTheReload(@TempDir Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        SettingsFeature feature = new SettingsFeature(store, startup);
        assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
        SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                SettingsFeature.ReloadConfirmationEffect.class,
                feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());
        SettingsFeature.SaveEffect first = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.respondReload(confirmation.token(), SettingsFeature.ReloadDecision.SAVE)
                        .effect().orElseThrow());
        SettingsFeature.Update cancelled = feature.cancel(first.token());

        SettingsFeature.SaveEffect fresh = assertInstanceOf(SettingsFeature.SaveEffect.class,
                feature.dispatch(new SettingsFeature.Save()).effect().orElseThrow());
        SettingsFeature.Update saved = feature.complete(new SettingsFeature.SaveCompletion(
                fresh.token(), store.persist(fresh.replacement())));

        assertTrue(cancelled.accepted());
        assertTrue(cancelled.frame().dirty());
        assertTrue(saved.accepted());
        assertTrue(saved.effect().isEmpty());
        assertFalse(saved.frame().dirty());
        assertEquals(SettingsFeature.OutcomeKind.SAVED, saved.frame().outcome());
    }

    /**
     * Reload recovers an interrupted paired publication, removes the journal, and exposes durable recovery evidence.
     */
    @Test
    void reloadRecoversInterruptedPairAndPublishesTheWarning(@TempDir Path directory) throws Exception {
        Settings store = new Settings(directory);
        Path fixtures = Path.of("test-resources", "json-oracles", "settings");
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        Files.copy(fixtures.resolve("standard.json"), standard);
        Files.copy(fixtures.resolve("uunp.json"), uunp);
        Settings.InitializationResult initialized = store.reload();
        SettingsFeature feature = new SettingsFeature(store, initialized);
        byte[] priorStandard = Files.readAllBytes(standard);
        byte[] priorUunp = Files.readAllBytes(uunp);
        Path transaction = Files.createDirectory(directory.resolve(".bs2bg-settings-stage-workbench"));
        Files.writeString(transaction.resolve("owner"), "BS2BG Settings transaction v1\n",
                java.nio.charset.StandardCharsets.US_ASCII);
        Files.move(standard, transaction.resolve("standard.backup"));
        Files.move(uunp, transaction.resolve("uunp.backup"));
        Files.copy(fixtures.resolve("standard.canonical.json"), standard);

        SettingsFeature.Update requested = feature.dispatch(new SettingsFeature.Reload());
        SettingsFeature.ReloadEffect reload = assertInstanceOf(SettingsFeature.ReloadEffect.class,
                requested.effect().orElseThrow());
        assertTrue(Files.exists(transaction));

        Settings.InitializationResult initialization = store.reload();
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

    /** The migrated output omission choice is captured for worker persistence before its frame commits. */
    @Test
    void omitRedundantSlidersPersistsInTheProfileLocalGenerationStore(@TempDir Path directory) throws Exception {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        GenerationPreferencesStore preferences = new GenerationPreferencesStore(directory);
        preferences.save(false);
        SettingsFeature feature = new SettingsFeature(store, startup);

        SettingsFeature.Update requested = feature.dispatch(
                new SettingsFeature.ChangeOmitRedundantSliders(true));
        SettingsFeature.PreferenceEffect preference = assertInstanceOf(SettingsFeature.PreferenceEffect.class,
                requested.effect().orElseThrow());

        assertTrue(requested.accepted());
        assertFalse(requested.frame().omitRedundantSliders());
        assertFalse(preferences.loadOrMigrate());

        preferences.save(preference.selected());
        SettingsFeature.Update changed = feature.complete(
                SettingsFeature.PreferenceCompletion.successful(preference.token()));

        assertTrue(changed.accepted());
        assertTrue(changed.frame().omitRedundantSliders());
        assertTrue(preferences.loadOrMigrate());
        SettingsFeature reopened = new SettingsFeature(store, startup);
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

    /** An oversized preference is rejected during attach and reports the existing profile failure notice. */
    @Test
    void oversizedGenerationPreferenceFallsBackWithNotice(@TempDir Path directory) throws Exception {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        assertTrue(startup.isSuccessful());
        Files.writeString(directory.resolve("workbench-generation.properties"),
                "omitRedundantSliders=true" + " ".repeat(4096));

        SettingsFeature feature = new SettingsFeature(store, startup);

        assertFalse(feature.frame().omitRedundantSliders());
        assertTrue(feature.frame().notices().stream()
                .anyMatch(notice -> notice.code().equals("GENERATION_PREFERENCES_IO_FAILED")));
    }
}
