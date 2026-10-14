package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.util.Objects;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;

/**
 * One test fixture that builds the {@link WorkbenchEnvironment} and the {@link ProjectSession} from the same isolated
 * Settings store, so the Workbench and its session always agree on the current Settings pair. The store's directory
 * also serves as the preferences directory, as the production profile directory does.
 */
public final class WorkbenchTestEnvironment {
    private final Settings settings;
    private final Settings.InitializationResult startup;

    /**
     * Pairs one store with the startup result the Workbench should render.
     */
    private WorkbenchTestEnvironment(Settings settings, Settings.InitializationResult startup) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    /**
     * Creates the store for a caller-owned directory and reloads it; a directory without Settings files receives the
     * first-run default pair.
     *
     * @param directory caller-owned temporary directory for Settings and preferences
     * @return fixture whose startup result is that successful reload
     * @throws IllegalStateException when the reload fails
     */
    public static WorkbenchTestEnvironment initialized(Path directory) {
        Settings store = new Settings(directory);
        Settings.InitializationResult startup = store.reload();
        if (!startup.isSuccessful())
            throw new IllegalStateException(startup.getFailure().orElseThrow().formatForDisplay());
        return new WorkbenchTestEnvironment(store, startup);
    }

    /**
     * Wraps a store the test already prepared, for example one built by {@link SettingsTestSupport}, reloading it so
     * the fixture carries a real startup result.
     *
     * @param settings the test's one store
     * @return fixture whose startup result is that successful reload
     * @throws IllegalStateException when the reload fails
     */
    public static WorkbenchTestEnvironment reloaded(Settings settings) {
        Settings.InitializationResult startup = settings.reload();
        if (!startup.isSuccessful())
            throw new IllegalStateException(startup.getFailure().orElseThrow().formatForDisplay());
        return new WorkbenchTestEnvironment(settings, startup);
    }

    /**
     * Wraps a store the test already prepared, for example one built by {@link SettingsTestSupport}.
     *
     * @param settings the test's one store
     * @param startup  startup result the Workbench should render, successful or failed
     * @return fixture over that store
     */
    public static WorkbenchTestEnvironment of(Settings settings, Settings.InitializationResult startup) {
        return new WorkbenchTestEnvironment(settings, startup);
    }

    /**
     * Keeps the same store but renders a different startup result, for tests of startup failure evidence.
     *
     * @param replacement startup result to render
     * @return new fixture over the same store
     */
    public WorkbenchTestEnvironment withStartup(Settings.InitializationResult replacement) {
        return new WorkbenchTestEnvironment(settings, replacement);
    }

    /** @return the test's one Settings store */
    public Settings settings() {
        return settings;
    }

    /** @return the startup result the environment carries */
    public Settings.InitializationResult startup() {
        return startup;
    }

    /**
     * @return environment over the store with the store's directory as preferences directory and the read-only
     *         generation-preference fallback that tests and embedded adapters use
     */
    public WorkbenchEnvironment environment() {
        return WorkbenchEnvironment.of(settings, startup, settings.directory(),
                GenerationPreferencesStore.MigrationPolicy.READ_ONLY_FALLBACK);
    }

    /** @return a new session reading the same store through its read-only snapshot view */
    public ProjectSession newSession() {
        return ProjectSessions.create(settings::snapshot);
    }
}
