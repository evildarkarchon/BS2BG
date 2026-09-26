package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Persists Workbench generation preferences in the isolated application profile and migrates the legacy Java
 * Preferences value once when the profile-local file is absent.
 */
public final class GenerationPreferencesStore {
    static final String FILE_NAME = "workbench-generation.properties";
    private static final String OMIT_PREFIX = "omitRedundantSliders=";
    // Java Preferences used the retired Data class name as its node path; changing either literal loses migration.
    private static final String LEGACY_PREFERENCES_NODE = "com.asdasfa.jbs2bg.data.Data";
    private static final String LEGACY_OMIT_REDUNDANT_SLIDERS_KEY = "Omit redundant sliders";
    // A single boolean needs only a few bytes; the bound also limits work during JavaFX attachment.
    private static final int MAXIMUM_PREFERENCE_BYTES = 128;

    private final Path directory;
    private final Path file;

    /** Explicit attach-time policy for adopting the legacy preference into the profile-local store. */
    public enum MigrationPolicy {
        MIGRATE,
        READ_ONLY_FALLBACK
    }

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
            boolean legacy = legacyValue();
            save(legacy);
            return legacy;
        }
        return loadProfileValue();
    }

    /**
     * Loads the profile-local choice when present, otherwise reads the legacy value without writing. Embedded and
     * test adapters use this path so attaching a controller cannot mutate an unrelated working directory.
     *
     * @return stored or legacy omission choice
     * @throws IOException when an existing profile-local file cannot be read
     */
    public boolean loadLegacyFallback() throws IOException {
        return Files.exists(file) ? loadProfileValue() : legacyValue();
    }

    /**
     * Parses the profile-local preference without reading more than the single-value format can require.
     *
     * @return the stored omission choice, or false for an unrecognized value
     * @throws IOException when the file cannot be read or exceeds the preference size limit
     */
    private boolean loadProfileValue() throws IOException {
        if (Files.size(file) > MAXIMUM_PREFERENCE_BYTES)
            throw new IOException("Generation preference exceeds the size limit.");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) {
            // The extra byte catches replacement or growth after the metadata check without reading the tail.
            bytes = input.readNBytes(MAXIMUM_PREFERENCE_BYTES + 1);
        }
        if (bytes.length > MAXIMUM_PREFERENCE_BYTES)
            throw new IOException("Generation preference exceeds the size limit.");
        String content = new String(bytes, StandardCharsets.UTF_8).trim();
        if (!content.startsWith(OMIT_PREFIX))
            return false;
        return Boolean.parseBoolean(content.substring(OMIT_PREFIX.length()).trim());
    }

    /** Reads the retained packaged Java Preferences key used before the Workbench cutover. */
    private static boolean legacyValue() {
        return Preferences.userRoot().node(LEGACY_PREFERENCES_NODE)
                .getBoolean(LEGACY_OMIT_REDUNDANT_SLIDERS_KEY, false);
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
            Files.writeString(temporary, OMIT_PREFIX + omitRedundantSliders + System.lineSeparator());
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
