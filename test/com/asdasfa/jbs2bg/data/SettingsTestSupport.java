package com.asdasfa.jbs2bg.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;

/**
 * Builds isolated Settings stores for tests. Every store is bound to a caller-owned temporary directory and loads
 * its pair through the same validated {@link Settings#reload()} seam the application uses; nothing is published to
 * any process-wide state.
 */
public final class SettingsTestSupport {
    private static final Settings.Profile EMPTY_PROFILE = new Settings.Profile(Map.of(), Map.of(), List.of());
    private static final Settings.Snapshot EMPTY = new Settings.Snapshot(EMPTY_PROFILE, EMPTY_PROFILE);

    private SettingsTestSupport() {
    }

    /**
     * Returns a read-only Settings view with empty Standard and UUNP profiles, for sessions and features whose test
     * does not depend on Settings content.
     *
     * @return view that always returns the same empty pair
     */
    public static Supplier<Settings.Snapshot> emptySettings() {
        return SettingsTestSupport::emptySnapshot;
    }

    /**
     * @return immutable pair with empty Standard and UUNP profiles
     */
    public static Settings.Snapshot emptySnapshot() {
        return EMPTY;
    }

    /**
     * Creates a store bound to the directory and reloads it, writing the first-run default pair when the directory
     * has none. A missing directory is created first.
     *
     * @param directory caller-owned temporary Settings directory
     * @return reloaded store
     * @throws IllegalStateException when the directory cannot be created or the reload fails
     */
    public static Settings initializedStore(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Test Settings directory could not be created.", exception);
        }
        Settings store = new Settings(directory);
        requireSuccess(store.reload());
        return store;
    }

    /**
     * Writes endpoint-only Standard and UUNP profiles into the directory and returns a store reloaded from them.
     *
     * @param directory        caller-owned temporary Settings directory
     * @param standardDefaults Standard endpoint values
     * @param uunpDefaults     UUNP endpoint values
     * @return isolated store holding exactly those profiles
     */
    public static Settings installDefaults(Path directory, Map<String, DefaultSliderValue> standardDefaults,
                                           Map<String, DefaultSliderValue> uunpDefaults) {
        return install(directory, standardDefaults, Collections.emptyMap(), Collections.emptyList(), uunpDefaults,
                Collections.emptyMap(), Collections.emptyList());
    }

    /**
     * Writes one finite Standard output profile with an empty UUNP partner and returns a store reloaded from it.
     *
     * @param directory   caller-owned temporary Settings directory
     * @param multipliers Standard Slider multipliers
     * @param inverted    Standard case-insensitive inversion identities
     * @return isolated store holding exactly that profile pair
     */
    public static Settings installStandardOutput(Path directory, Map<String, Float> multipliers,
                                                 List<String> inverted) {
        return install(directory, Collections.emptyMap(), multipliers, inverted, Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList());
    }

    /**
     * Rewrites an existing store's files with endpoint-only profiles and reloads that same store, so sessions and
     * features already reading it observe the new pair as they would after a Workbench Reload.
     *
     * @param store            store created by this support class
     * @param standardDefaults Standard endpoint values
     * @param uunpDefaults     UUNP endpoint values
     */
    public static void replaceDefaults(Settings store, Map<String, DefaultSliderValue> standardDefaults,
                                       Map<String, DefaultSliderValue> uunpDefaults) {
        write(store.directory(), standardDefaults, Collections.emptyMap(), Collections.emptyList(), uunpDefaults,
                Collections.emptyMap(), Collections.emptyList());
        requireSuccess(store.reload());
    }

    /**
     * Rewrites an existing store's files with one Standard output profile and an empty UUNP partner, then reloads
     * that same store.
     *
     * @param store       store created by this support class
     * @param multipliers Standard Slider multipliers
     * @param inverted    Standard case-insensitive inversion identities
     */
    public static void replaceStandardOutput(Settings store, Map<String, Float> multipliers, List<String> inverted) {
        write(store.directory(), Collections.emptyMap(), multipliers, inverted, Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList());
        requireSuccess(store.reload());
    }

    /**
     * Writes canonical files into the directory and reloads a new store bound to it through {@link Settings#reload}.
     */
    private static Settings install(Path directory, Map<String, DefaultSliderValue> standardDefaults,
                                    Map<String, Float> standardMultipliers, List<String> standardInverted,
                                    Map<String, DefaultSliderValue> uunpDefaults, Map<String, Float> uunpMultipliers,
                                    List<String> uunpInverted) {
        write(directory, standardDefaults, standardMultipliers, standardInverted, uunpDefaults, uunpMultipliers,
                uunpInverted);
        return initializedStore(directory);
    }

    /**
     * Writes one canonical pair, creating the directory when needed so callers can keep Settings in a subdirectory
     * apart from other test files.
     */
    private static void write(Path directory, Map<String, DefaultSliderValue> standardDefaults,
                              Map<String, Float> standardMultipliers, List<String> standardInverted,
                              Map<String, DefaultSliderValue> uunpDefaults, Map<String, Float> uunpMultipliers,
                              List<String> uunpInverted) {
        try {
            Files.createDirectories(directory);
            SettingsJacksonAdapter.SettingsCandidate candidate = new SettingsJacksonAdapter.SettingsCandidate(
                    profile(standardDefaults, standardMultipliers, standardInverted),
                    profile(uunpDefaults, uunpMultipliers, uunpInverted), Collections.emptyList());
            SettingsJacksonAdapter.SettingsPairBytes pair = SettingsJacksonAdapter.writePair(candidate);
            Files.write(directory.resolve("settings.json"), pair.standardUtf8());
            Files.write(directory.resolve("settings_UUNP.json"), pair.uunpUtf8());
        } catch (IOException exception) {
            throw new IllegalStateException("Test Settings could not be written.", exception);
        }
    }

    /**
     * Fails fast with the formatted Settings failure so a fixture problem is not mistaken for the behavior under test.
     */
    private static void requireSuccess(Settings.InitializationResult result) {
        if (!result.isSuccessful())
            throw new IllegalStateException(result.getFailure().orElseThrow().formatForDisplay());
    }

    /**
     * Converts application endpoint values into one detached adapter profile.
     */
    private static SettingsJacksonAdapter.SettingsProfile profile(Map<String, DefaultSliderValue> defaults,
                                                                  Map<String, Float> multipliers, List<String> inverted) {
        Map<String, SettingsJacksonAdapter.DefaultValue> converted = new LinkedHashMap<>();
        for (Map.Entry<String, DefaultSliderValue> entry : defaults.entrySet()) {
            converted.put(entry.getKey(), new SettingsJacksonAdapter.DefaultValue(
                    entry.getValue().getValueSmall(), entry.getValue().getValueBig()));
        }
        return new SettingsJacksonAdapter.SettingsProfile(converted, multipliers, inverted);
    }
}
