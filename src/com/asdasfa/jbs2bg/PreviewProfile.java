package com.asdasfa.jbs2bg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Prepares the isolated Preview profile and adopts state from the former working-directory profile. */
final class PreviewProfile {
    private static final String STANDARD = "settings.json";
    private static final String UUNP = "settings_UUNP.json";
    private static final String GENERATION = "workbench-generation.properties";
    private static final String APPEARANCE = "workbench-appearance.properties";
    private static final String MARKER_NAME = ".bs2bg-preview-settings-migration";
    private static final String MARKER_PREFIX = "BS2BG Preview Settings migration v1:";
    private static final String COMPLETE_NAME = ".bs2bg-preview-migration-complete";
    private static final String COMPLETE_CONTENT = "BS2BG Preview migration complete v1\n";
    private static final String STAGED_STANDARD = ".bs2bg-preview-standard.staged";
    private static final String STAGED_UUNP = ".bs2bg-preview-uunp.staged";
    private static final String LEGACY_STAGE_PREFIX = ".bs2bg-settings-stage-";
    // SettingsPairPublisher uses this exact marker before a journal may change either live Settings file.
    private static final byte[] LEGACY_STAGE_OWNER = "BS2BG Settings transaction v1\n"
            .getBytes(StandardCharsets.US_ASCII);

    private PreviewProfile() {
    }

    /**
     * Creates the application-owned directory and adopts missing legacy files before Settings may publish defaults.
     * Legacy Settings and preference documents are not modified; a legacy profile may gain its Settings lock file.
     * An established profile Settings side prevents mixing two pairs.
     *
     * @param profileDirectory new Preview profile beneath LOCALAPPDATA
     * @param legacyDirectory working directory used by the previous Preview release
     * @throws IOException when creation, migration, or interrupted-migration recovery cannot finish safely
     */
    static void prepare(Path profileDirectory, Path legacyDirectory) throws IOException {
        Path profile = Objects.requireNonNull(profileDirectory, "profileDirectory").toAbsolutePath().normalize();
        Path legacy = Objects.requireNonNull(legacyDirectory, "legacyDirectory").toAbsolutePath().normalize();
        Files.createDirectories(profile);
        if (!Files.isDirectory(profile, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Preview profile is not an owned directory: " + profile);
        // The migration lock prevents two simultaneous Preview launches from adopting different legacy snapshots.
        try (FileChannel channel = FileChannel.open(profile.resolve(".bs2bg-preview-migration.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock lock = channel.lock()) {
            if (!lock.isValid())
                throw new IOException("Could not lock the Preview profile migration: " + profile);
            boolean pending = Files.exists(profile.resolve(MARKER_NAME), LinkOption.NOFOLLOW_LINKS);
            Path complete = profile.resolve(COMPLETE_NAME);
            boolean completed = Files.exists(complete, LinkOption.NOFOLLOW_LINKS);
            if (completed && !pending) {
                requireValidCompletion(complete);
                return;
            }
            // An established profile is authoritative unless our own unfinished transfer needs recovery.
            boolean profileSettingsPresent = Files.exists(profile.resolve(STANDARD), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(profile.resolve(UUNP), LinkOption.NOFOLLOW_LINKS);
            if (pending || !profileSettingsPresent) {
                migrateSettingsUnderLegacyLock(profile, legacy, complete, completed);
                return;
            }
            finishMigration(profile, legacy, complete, completed);
        }
    }

    /**
     * Completes the preference transfer or validates an already durable completion marker.
     *
     * @param completed whether the marker existed when the profile migration lock was acquired
     * @throws IOException when a preference cannot be copied or the completion marker is invalid
     */
    private static void finishMigration(Path profile, Path legacy, Path complete, boolean completed)
            throws IOException {
        if (completed) {
            requireValidCompletion(complete);
            return;
        }
        copyPreferenceIfAbsent(profile, legacy, GENERATION);
        copyPreferenceIfAbsent(profile, legacy, APPEARANCE);
        writeMarker(profile, complete, COMPLETE_CONTENT);
    }

    /** Accepts only this version's durable completion marker before suppressing future legacy reads. */
    private static void requireValidCompletion(Path complete) throws IOException {
        if (!Files.isRegularFile(complete, LinkOption.NOFOLLOW_LINKS)
                || !COMPLETE_CONTENT.equals(Files.readString(complete)))
            throw new IOException("Preview migration completion marker is invalid: " + complete);
    }

    /**
     * Holds the former Settings writer lock through the state check and completion marker publication.
     * Even an empty directory must participate because the old writer can publish its pair after an unlocked scan.
     *
     * @param completed whether the completion marker existed under the profile migration lock
     * @throws IOException when the legacy lock is busy or migration cannot finish safely
     */
    private static void migrateSettingsUnderLegacyLock(Path profile, Path legacy, Path complete,
                                                       boolean completed) throws IOException {
        Path lockPath = legacy.resolve(".bs2bg-settings.lock");
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Legacy Preview Settings lock path is not a file: " + lockPath);
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock lock = channel.tryLock()) {
            if (lock == null || !lock.isValid())
                throw new IOException("Legacy Preview Settings lock is held by another process: " + lockPath);
            cleanUpSafeLegacyTransactions(legacy);
            migrateSettings(profile, legacy);
            finishMigration(profile, legacy, complete, completed);
        } catch (OverlappingFileLockException exception) {
            throw new IOException("Legacy Preview Settings lock is held by this process: " + lockPath, exception);
        }
    }

    /**
     * Removes only owned cleanup-only Settings journals while holding the former publisher's writer lock.
     * Journals with prior-state markers still require the publisher's rollback before their live pair is sampled.
     */
    private static void cleanUpSafeLegacyTransactions(Path legacy) throws IOException {
        List<Path> transactions = new ArrayList<>();
        try (Stream<Path> entries = Files.list(legacy)) {
            for (Path candidate : entries.filter(path -> path.getFileName().toString()
                    .startsWith(LEGACY_STAGE_PREFIX)).toList()) {
                if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS) && isOwnedLegacyTransaction(candidate))
                    transactions.add(candidate);
            }
        }
        if (transactions.size() > 1)
            throw new IOException("Legacy Preview Settings has more than one owned transaction.");
        if (transactions.isEmpty())
            return;

        Path transaction = transactions.getFirst();
        Path committed = transaction.resolve("committed");
        if (Files.exists(committed, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(committed, LinkOption.NOFOLLOW_LINKS) || Files.size(committed) != 1L)
                throw new IOException("Legacy Preview Settings commit marker is invalid: " + committed);
            if (!Files.isRegularFile(legacy.resolve(STANDARD), LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(legacy.resolve(UUNP), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Legacy Preview Settings committed transaction lacks its published pair: "
                        + transaction);
        } else if (hasLegacyPriorStateMarkers(transaction)) {
            throw new IOException("Legacy Preview Settings transaction requires recovery before migration: "
                    + transaction);
        }
        // Files.walk does not follow links; cleanup must never leave this authenticated journal directory.
        try (Stream<Path> paths = Files.walk(transaction)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    /** Prior-state markers mean publication could have changed a live side and Preview cannot infer the old pair. */
    private static boolean hasLegacyPriorStateMarkers(Path transaction) {
        for (String member : List.of("standard", "uunp")) {
            if (Files.exists(transaction.resolve(member + ".backup"), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(transaction.resolve(member + ".absent"), LinkOption.NOFOLLOW_LINKS))
                return true;
        }
        return false;
    }

    /** Matches the bounded ownership marker recognized by the legacy Settings transaction recovery. */
    private static boolean isOwnedLegacyTransaction(Path candidate) throws IOException {
        Path owner = candidate.resolve("owner");
        if (!Files.isRegularFile(owner, LinkOption.NOFOLLOW_LINKS)
                || Files.size(owner) != LEGACY_STAGE_OWNER.length)
            return false;
        try (InputStream input = Files.newInputStream(owner, LinkOption.NOFOLLOW_LINKS)) {
            return Arrays.equals(input.readNBytes(LEGACY_STAGE_OWNER.length + 1), LEGACY_STAGE_OWNER);
        }
    }

    /**
     * Resumes an owned staged Settings copy after interruption, preserving a partial established profile as-is.
     * The marker records which legacy sides existed before any destination was published; without it, a crash
     * between the two atomic moves could make Settings synthesize a default partner on the next launch.
     */
    private static void migrateSettings(Path profile, Path legacy) throws IOException {
        Path marker = profile.resolve(MARKER_NAME);
        boolean pending = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
        if (!pending) {
            if (Files.exists(profile.resolve(STANDARD), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(profile.resolve(UUNP), LinkOption.NOFOLLOW_LINKS))
                return;
            boolean standard = Files.exists(legacy.resolve(STANDARD), LinkOption.NOFOLLOW_LINKS);
            boolean uunp = Files.exists(legacy.resolve(UUNP), LinkOption.NOFOLLOW_LINKS);
            if (!standard && !uunp)
                return;
            writeMarker(profile, marker,
                    MARKER_PREFIX + (standard ? "1" : "0") + (uunp ? "1" : "0") + "\n");
        }

        String manifest = Files.readString(marker);
        if (!manifest.startsWith(MARKER_PREFIX) || manifest.length() != MARKER_PREFIX.length() + 3
                || manifest.charAt(MARKER_PREFIX.length() + 2) != '\n'
                || (manifest.charAt(MARKER_PREFIX.length()) != '0'
                && manifest.charAt(MARKER_PREFIX.length()) != '1')
                || (manifest.charAt(MARKER_PREFIX.length() + 1) != '0'
                && manifest.charAt(MARKER_PREFIX.length() + 1) != '1'))
            throw new IOException("Preview Settings migration marker is invalid: " + marker);
        boolean standard = manifest.charAt(MARKER_PREFIX.length()) == '1';
        boolean uunp = manifest.charAt(MARKER_PREFIX.length() + 1) == '1';
        if (!standard && !uunp)
            throw new IOException("Preview Settings migration marker has no source files: " + marker);

        boolean liveSidePresent = Files.exists(profile.resolve(STANDARD), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(profile.resolve(UUNP), LinkOption.NOFOLLOW_LINKS);
        boolean completeStage = (!standard || Files.isRegularFile(profile.resolve(STAGED_STANDARD),
                LinkOption.NOFOLLOW_LINKS)) && (!uunp || Files.isRegularFile(profile.resolve(STAGED_UUNP),
                LinkOption.NOFOLLOW_LINKS));
        if (pending && !liveSidePresent && !completeStage) {
            // Complete stages are one coherent snapshot; only a partial stage must be resampled together.
            boolean currentStandard = Files.exists(legacy.resolve(STANDARD), LinkOption.NOFOLLOW_LINKS);
            boolean currentUunp = Files.exists(legacy.resolve(UUNP), LinkOption.NOFOLLOW_LINKS);
            if (!currentStandard && !currentUunp)
                throw new IOException("Legacy Preview Settings sources disappeared before migration: " + legacy);
            deleteOwnedStage(profile.resolve(STAGED_STANDARD));
            deleteOwnedStage(profile.resolve(STAGED_UUNP));
            standard = currentStandard;
            uunp = currentUunp;
            writeMarker(profile, marker,
                    MARKER_PREFIX + (standard ? "1" : "0") + (uunp ? "1" : "0") + "\n");
        }

        // Stage every expected side before publishing either one, so a failed copy cannot expose a mixed pair.
        if (standard)
            stageSettingsSide(profile, legacy, STANDARD, STAGED_STANDARD, !liveSidePresent);
        if (uunp)
            stageSettingsSide(profile, legacy, UUNP, STAGED_UUNP, !liveSidePresent);
        if (standard)
            publishSettingsSide(profile, STANDARD, STAGED_STANDARD);
        if (uunp)
            publishSettingsSide(profile, UUNP, STAGED_UUNP);
        Files.delete(marker);
    }

    /** Forces marker contents before an atomic move makes migration progress or completion visible. */
    private static void writeMarker(Path profile, Path marker, String content) throws IOException {
        Path temporary = Files.createTempFile(profile, ".bs2bg-preview-marker-", ".tmp");
        try {
            Files.writeString(temporary, content);
            forceExisting(temporary);
            Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Removes only a regular staged file named by the owned pending migration marker. */
    private static void deleteOwnedStage(Path staged) throws IOException {
        if (!Files.exists(staged, LinkOption.NOFOLLOW_LINKS))
            return;
        if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Preview Settings migration stage is not a file: " + staged);
        Files.delete(staged);
    }

    /** Copies one expected legacy Settings side to a private name, or retains its completed staged/live copy. */
    private static void stageSettingsSide(Path profile, Path legacy, String name, String stagedName,
                                          boolean mayCopyLegacy)
            throws IOException {
        Path target = profile.resolve(name);
        Path staged = profile.resolve(stagedName);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Preview Settings migration has both staged and live copies: " + name);
            return;
        }
        if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Preview Settings migration stage is not a file: " + staged);
            return;
        }
        if (!mayCopyLegacy)
            throw new IOException("Preview Settings migration stage is unavailable: " + staged);
        Path source = legacy.resolve(name);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Legacy Preview Settings source is unavailable: " + source);
        copyAtomically(profile, source, staged);
    }

    /** Atomically publishes one staged side; an existing live side means a previous launch already moved it. */
    private static void publishSettingsSide(Path profile, String name, String stagedName) throws IOException {
        Path target = profile.resolve(name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            return;
        Path staged = profile.resolve(stagedName);
        if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Preview Settings migration stage is unavailable: " + staged);
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Copies a missing preference without changing an established profile choice or the legacy source. */
    private static void copyPreferenceIfAbsent(Path profile, Path legacy, String name) throws IOException {
        Path target = profile.resolve(name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            return;
        Path source = legacy.resolve(name);
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS))
            return;
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Legacy Preview preference source is not a file: " + source);
        copyAtomically(profile, source, target);
    }

    /** Stages and forces a complete source beside its destination before publishing its final filename. */
    private static void copyAtomically(Path profile, Path source, Path target) throws IOException {
        Path temporary = Files.createTempFile(profile, ".bs2bg-preview-copy-", ".tmp");
        try {
            // A source replaced by a link after validation must not import data outside the legacy profile.
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            forceExisting(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Forces a staged file before its rename can make a later completion marker authoritative. */
    private static void forceExisting(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }
}
