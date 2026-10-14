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
 * preference and theme choice, and the generation-preference migration policy. The name avoids "profile", which
 * already means the Standard/UUNP Settings profiles.
 *
 * <p>Adding a slot: give the new field a default in {@link #of} and add a {@code withX} copy method that returns a
 * new environment, so existing callers keep compiling. The appearance-preferences source and the NPC portrait image
 * directory are planned slots of this kind.
 */
public final class WorkbenchEnvironment {
    private final Settings settings;
    private final Settings.InitializationResult settingsStartup;
    private final Path preferencesDirectory;
    private final GenerationPreferencesStore.MigrationPolicy migrationPolicy;

    /**
     * Creates one immutable environment; see {@link #of} for the parameter contract.
     */
    private WorkbenchEnvironment(Settings settings, Settings.InitializationResult settingsStartup,
                                 Path preferencesDirectory, GenerationPreferencesStore.MigrationPolicy migrationPolicy) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.settingsStartup = Objects.requireNonNull(settingsStartup, "settingsStartup");
        this.preferencesDirectory = Objects.requireNonNull(preferencesDirectory, "preferencesDirectory")
                .toAbsolutePath().normalize();
        this.migrationPolicy = Objects.requireNonNull(migrationPolicy, "migrationPolicy");
    }

    /**
     * Creates an environment around one Settings store.
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
        return new WorkbenchEnvironment(settings, settingsStartup, preferencesDirectory, migrationPolicy);
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
}
