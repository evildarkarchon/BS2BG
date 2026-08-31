package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;

/**
 * Persists the Workbench theme choice inside the application's isolated working profile.
 */
public final class WorkbenchAppearanceStore {
    static final String FILE_NAME = "workbench-appearance.properties";
    private static final String THEME_PREFIX = "theme=";

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
        String content = Files.readString(file, StandardCharsets.UTF_8).trim();
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
            Files.writeString(temporary, THEME_PREFIX + choice.name() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
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
