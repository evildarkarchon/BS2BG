package com.asdasfa.jbs2bg.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;

/** Publishes deterministic test profiles through the same validated Settings seam used by the application. */
public final class SettingsTestSupport {
    private SettingsTestSupport() {
    }

    /**
     * Publishes endpoint-only Standard and UUNP profiles for Project behavior tests.
     *
     * @param standardDefaults Standard endpoint values
     * @param uunpDefaults UUNP endpoint values
     */
    public static void installDefaults(Map<String, DefaultSliderValue> standardDefaults,
            Map<String, DefaultSliderValue> uunpDefaults) {
        install(standardDefaults, Collections.emptyMap(), Collections.emptyList(), uunpDefaults,
                Collections.emptyMap(), Collections.emptyList());
    }

    /**
     * Publishes one finite Standard output profile with an empty UUNP partner.
     *
     * @param multipliers Standard Slider multipliers
     * @param inverted Standard case-insensitive inversion identities
     */
    public static void installStandardOutput(Map<String, Float> multipliers, List<String> inverted) {
        install(Collections.emptyMap(), multipliers, inverted, Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList());
    }

    /** Restores the checked-in repository Settings pair after a test-specific publication. */
    public static void restoreRepositorySettings() {
        Settings.InitializationResult result = Settings.initialize(Path.of("."));
        if (!result.isSuccessful())
            throw new IllegalStateException(result.getFailure().orElseThrow().formatForDisplay());
    }

    /** Builds canonical files in an isolated directory and publishes them through {@link Settings#initialize}. */
    private static void install(Map<String, DefaultSliderValue> standardDefaults,
            Map<String, Float> standardMultipliers, List<String> standardInverted,
            Map<String, DefaultSliderValue> uunpDefaults, Map<String, Float> uunpMultipliers,
            List<String> uunpInverted) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("bs2bg-settings-test-");
            SettingsJacksonAdapter.SettingsCandidate candidate = new SettingsJacksonAdapter.SettingsCandidate(
                    profile(standardDefaults, standardMultipliers, standardInverted),
                    profile(uunpDefaults, uunpMultipliers, uunpInverted), Collections.emptyList());
            SettingsJacksonAdapter.SettingsPairBytes pair = SettingsJacksonAdapter.writePair(candidate);
            Files.write(directory.resolve("settings.json"), pair.standardUtf8());
            Files.write(directory.resolve("settings_UUNP.json"), pair.uunpUtf8());
            Settings.InitializationResult result = Settings.initialize(directory);
            if (!result.isSuccessful())
                throw new IllegalStateException(result.getFailure().orElseThrow().formatForDisplay());
        } catch (IOException exception) {
            throw new IllegalStateException("Test Settings could not be published.", exception);
        } finally {
            if (directory != null)
                deleteTree(directory);
        }
    }

    /** Converts application endpoint values into one detached adapter profile. */
    private static SettingsJacksonAdapter.SettingsProfile profile(Map<String, DefaultSliderValue> defaults,
            Map<String, Float> multipliers, List<String> inverted) {
        Map<String, SettingsJacksonAdapter.DefaultValue> converted = new LinkedHashMap<>();
        for (Map.Entry<String, DefaultSliderValue> entry : defaults.entrySet()) {
            converted.put(entry.getKey(), new SettingsJacksonAdapter.DefaultValue(
                    entry.getValue().getValueSmall(), entry.getValue().getValueBig()));
        }
        return new SettingsJacksonAdapter.SettingsProfile(converted, multipliers, inverted);
    }

    /** Removes the isolated test directory without replacing an earlier assertion failure. */
    private static void deleteTree(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The operating system will eventually reclaim temporary test data; preserve the test result.
        }
    }
}
