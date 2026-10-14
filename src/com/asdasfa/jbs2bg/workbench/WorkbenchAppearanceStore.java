package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/**
 * Persists the Workbench theme choice inside the application's isolated working profile, and captures the
 * worker-owned save job the theme selector submits.
 */
public final class WorkbenchAppearanceStore {
    static final String FILE_NAME = "workbench-appearance.properties";
    private static final String THEME_PREFIX = "theme=";
    // Accepted values need only a few bytes; this leaves format headroom while keeping startup work negligible.
    private static final int MAXIMUM_PREFERENCE_BYTES = 128;

    private final Path directory;
    private final Path file;

    /**
     * Creates a profile-local store without reading or writing it yet.
     *
     * @param workingDirectory isolated application profile directory
     */
    public WorkbenchAppearanceStore(Path workingDirectory) {
        directory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        file = directory.resolve(FILE_NAME);
    }

    /**
     * Loads the persisted selection, defaulting safely to System for a missing or unrecognized value.
     *
     * @return persisted System, Light, or Dark selection
     * @throws IOException when an existing preference file cannot be read
     */
    public WorkbenchAppearance.ThemeChoice load() throws IOException {
        if (!Files.exists(file))
            return WorkbenchAppearance.ThemeChoice.SYSTEM;
        if (Files.size(file) > MAXIMUM_PREFERENCE_BYTES)
            return WorkbenchAppearance.ThemeChoice.SYSTEM;
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) {
            // The extra byte detects replacement or growth after the metadata check without consuming the tail.
            bytes = input.readNBytes(MAXIMUM_PREFERENCE_BYTES + 1);
        }
        if (bytes.length > MAXIMUM_PREFERENCE_BYTES)
            return WorkbenchAppearance.ThemeChoice.SYSTEM;
        String content = new String(bytes, StandardCharsets.UTF_8).trim();
        if (!content.startsWith(THEME_PREFIX))
            return WorkbenchAppearance.ThemeChoice.SYSTEM;
        String value = content.substring(THEME_PREFIX.length()).trim().toUpperCase(Locale.ROOT);
        try {
            return WorkbenchAppearance.ThemeChoice.valueOf(value);
        } catch (IllegalArgumentException exception) {
            // Preferences are backward-tolerant: an unknown future or damaged value falls back to System.
            return WorkbenchAppearance.ThemeChoice.SYSTEM;
        }
    }

    /**
     * Captures one theme choice as a worker-owned save job. The coordinator publishes completion through Activity;
     * a failed optional preference write completes with issues and leaves the already applied appearance active.
     *
     * @param selected immutable System, Light, or Dark choice selected on the JavaFX thread
     * @return captured job submission with the selected choice as its completion value
     */
    public JobCoordinator.Submission<WorkbenchAppearance.ThemeChoice> saveSubmission(
            WorkbenchAppearance.ThemeChoice selected) {
        Objects.requireNonNull(selected, "selected");
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Save Theme Preference", List.of(),
                List.of(), Optional.of(selected.name()), JobCoordinator.ConsistencyClass.SNAPSHOT_DERIVED);
        return new JobCoordinator.Submission<>(operation, context -> {
            context.checkCancellation();
            if (!context.beginCommit("Saving theme preference"))
                return JobCoordinator.Result.cancelled("Theme preference save cancelled.", List.of(), List.of());
            try {
                save(selected);
                return JobCoordinator.Result.completed(selected, "Theme preference saved.",
                        List.of("Theme preference saved"), List.of());
            } catch (IOException exception) {
                return JobCoordinator.Result.completedWithIssues(selected,
                        "The selected theme could not be saved.", List.of(), List.of(
                                new JobCoordinator.Diagnostic("THEME_PREFERENCE_SAVE_FAILED",
                                        "The selected theme could not be saved.", Optional.empty())));
            }
        }, (attempt, result) -> {
            // The coordinator publishes the worker outcome through the ordinary Activity path.
        }, Optional.empty());
    }

    /**
     * Atomically publishes one selected theme so interruption cannot leave a partial preference file.
     *
     * @param choice selected System, Light, or Dark value
     * @throws IOException when the profile directory or preference file cannot be written
     */
    public void save(WorkbenchAppearance.ThemeChoice choice) throws IOException {
        Objects.requireNonNull(choice, "choice");
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "workbench-appearance-", ".next");
        try {
            Files.writeString(temporary, THEME_PREFIX + choice.name() + System.lineSeparator());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                // The complete same-directory temp file is still safer than writing the live preference in place.
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
