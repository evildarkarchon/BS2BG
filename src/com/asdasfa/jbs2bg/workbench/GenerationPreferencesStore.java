package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.prefs.Preferences;

import com.asdasfa.jbs2bg.data.Data;

/**
 * Persists Workbench generation preferences in the isolated application profile and migrates the legacy Java
 * Preferences value once when the profile-local file is absent.
 */
public final class GenerationPreferencesStore {
    static final String FILE_NAME = "workbench-generation.properties";
    private static final String OMIT_PREFIX = "omitRedundantSliders=";

    private final Path directory;
    private final Path file;

    /**
     * Creates a profile-local generation preference store without reading or writing it.
     *
     * @param workingDirectory isolated application profile directory
     */
    public GenerationPreferencesStore(Path workingDirectory) {
        directory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        file = directory.resolve(FILE_NAME);
    }

    /**
     * Loads the profile-local omission choice, migrating the legacy packaged Preferences node on first use.
     *
     * @return whether redundant sliders should be omitted from generated Templates output
     * @throws IOException when the profile-local file cannot be read
     */
    public boolean loadOrMigrate() throws IOException {
        if (!Files.exists(file)) {
            return Preferences.userRoot().node(Data.class.getName())
                    .getBoolean(Data.LEGACY_OMIT_REDUNDANT_SLIDERS, false);
        }
        String content = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (!content.startsWith(OMIT_PREFIX))
            return false;
        return Boolean.parseBoolean(content.substring(OMIT_PREFIX.length()).trim());
    }

    /**
     * Atomically publishes one generation preference value.
     *
     * @param omitRedundantSliders whether neutral sliders should be omitted
     * @throws IOException when the profile directory or preference file cannot be written
     */
    public void save(boolean omitRedundantSliders) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "workbench-generation-", ".next");
        try {
            Files.writeString(temporary, OMIT_PREFIX + omitRedundantSliders + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                // The complete same-directory temp file still prevents a partially written live preference.
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
