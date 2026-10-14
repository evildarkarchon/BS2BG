package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.util.Objects;

import com.asdasfa.jbs2bg.data.Settings;

/**
 * Everything the Workbench reads from outside its own window, handed to
 * {@link WorkbenchController#attach(WorkbenchProjectFlow, javafx.stage.Stage, WorkbenchEnvironment)} in one value so
 * nothing on the Workbench path reaches for process-wide state.
 *
 * <p>It carries the Settings store and its startup result, the preferences directory holding the generation
 * preference and theme choice, the generation-preference migration policy, the operating-system
 * appearance-preferences source, and the NPC portrait image directory. The name avoids "profile", which already
 * means the Standard/UUNP Settings profiles.
 *
 * <p>Adding a slot: give the new field a default in {@link #of} and add a {@code withX} copy method that returns a
 * new environment, so existing callers keep compiling. The appearance-preferences source and the portrait image
 * directory are such slots.
 */
public final class WorkbenchEnvironment {
    private final Settings settings;
    private final Settings.InitializationResult settingsStartup;
    private final Path preferencesDirectory;
    private final GenerationPreferencesStore.MigrationPolicy migrationPolicy;
    private final AppearancePreferencesSource appearancePreferences;
    private final Path portraitDirectory;

    /**
     * Creates one immutable environment; see {@link #of}, {@link #withAppearancePreferences} and
     * {@link #withPortraitDirectory} for the parameter contract.
     */
    private WorkbenchEnvironment(Settings settings, Settings.InitializationResult settingsStartup,
                                 Path preferencesDirectory, GenerationPreferencesStore.MigrationPolicy migrationPolicy,
                                 AppearancePreferencesSource appearancePreferences, Path portraitDirectory) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.settingsStartup = Objects.requireNonNull(settingsStartup, "settingsStartup");
        this.preferencesDirectory = Objects.requireNonNull(preferencesDirectory, "preferencesDirectory")
                .toAbsolutePath().normalize();
        this.migrationPolicy = Objects.requireNonNull(migrationPolicy, "migrationPolicy");
        this.appearancePreferences = Objects.requireNonNull(appearancePreferences, "appearancePreferences");
        // Kept as given, not made absolute: the production default stays relative to the working directory, exactly
        // as the legacy application resolved it on every lookup.
        this.portraitDirectory = Objects.requireNonNull(portraitDirectory, "portraitDirectory");
    }

    /**
     * Creates an environment around one Settings store. Appearance preferences default to the live JavaFX
     * platform preferences; tests replace them with {@link #withAppearancePreferences}. The NPC portrait image
     * directory defaults to {@code images} relative to the working directory, like the legacy application; tests
     * replace it with {@link #withPortraitDirectory}.
     *
     * @param settings             the one Settings store the Workbench reads, saves, and reloads
     * @param settingsStartup      the store's startup result, rendered as durable Settings evidence
     * @param preferencesDirectory directory holding the generation preference and theme choice
     * @param migrationPolicy      production migration or read-only fallback for the generation preference
     * @return immutable environment
     * @throws NullPointerException when an argument is null
     */
    public static WorkbenchEnvironment of(Settings settings, Settings.InitializationResult settingsStartup,
                                          Path preferencesDirectory,
                                          GenerationPreferencesStore.MigrationPolicy migrationPolicy) {
        return new WorkbenchEnvironment(settings, settingsStartup, preferencesDirectory, migrationPolicy,
                new JavaFxAppearancePreferencesSource(), Path.of("images"));
    }

    /**
     * Copies this environment with a different operating-system appearance-preferences source.
     *
     * @param replacement source the Workbench reads its color scheme, High Contrast and motion preferences from
     * @return new environment; this one is unchanged
     * @throws NullPointerException when {@code replacement} is null
     */
    public WorkbenchEnvironment withAppearancePreferences(AppearancePreferencesSource replacement) {
        return new WorkbenchEnvironment(settings, settingsStartup, preferencesDirectory, migrationPolicy, replacement,
                portraitDirectory);
    }

    /**
     * Copies this environment with a different NPC portrait image directory.
     *
     * @param replacement directory holding {@code <name> (<editor ID>).<ext>} portrait files; it need not exist
     * @return new environment; this one is unchanged
     * @throws NullPointerException when {@code replacement} is null
     */
    public WorkbenchEnvironment withPortraitDirectory(Path replacement) {
        return new WorkbenchEnvironment(settings, settingsStartup, preferencesDirectory, migrationPolicy,
                appearancePreferences, replacement);
    }

    /** @return the one Settings store on the Workbench path */
    public Settings settings() {
        return settings;
    }

    /** @return the Settings store's startup result */
    public Settings.InitializationResult settingsStartup() {
        return settingsStartup;
    }

    /** @return normalized directory holding the generation preference and theme choice */
    public Path preferencesDirectory() {
        return preferencesDirectory;
    }

    /** @return generation-preference migration policy */
    public GenerationPreferencesStore.MigrationPolicy migrationPolicy() {
        return migrationPolicy;
    }

    /** @return the source of operating-system appearance preferences the Workbench renders */
    public AppearancePreferencesSource appearancePreferences() {
        return appearancePreferences;
    }

    /** @return the directory the NPC portrait viewer looks up portraits in */
    public Path portraitDirectory() {
        return portraitDirectory;
    }
}
