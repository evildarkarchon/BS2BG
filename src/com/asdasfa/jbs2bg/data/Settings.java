package com.asdasfa.jbs2bg.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Owns the one validated Standard/UUNP Settings value consumed by the application.
 */
public final class Settings {
    private static volatile LiveSettings liveSettings = LiveSettings.empty();

    private Settings() {
    }

    /**
     * Recovers, loads, and validates the Standard and UUNP Settings documents before publishing either profile.
     * This startup operation serializes writers; readers observe the prior or replacement immutable value.
     *
     * @param workingDirectory directory containing {@code settings.json} and {@code settings_UUNP.json}
     * @return success with ordered warnings, or one stable failure without partial live-state mutation
     */
    public static synchronized InitializationResult initialize(Path workingDirectory) {
        return initialize(workingDirectory, () -> true);
    }

    /**
     * Initializes Settings with a worker-owned commit callback. The callback runs after lock acquisition and normal
     * existing-pair reads, but before recovery, first-run publication, or live publication can change state.
     *
     * @param workingDirectory directory containing the paired Settings files
     * @param commitAdmission callback invoked at most once to enter a non-cancellable commit phase
     * @return initialized Settings and warnings, or a classified failure with the prior live pair retained
     * @throws CancellationException when commit admission rejects an accepted cancellation
     */
    public static synchronized InitializationResult initialize(Path workingDirectory,
                                                               BooleanSupplier commitAdmission) {
        return initialize(workingDirectory, commitAdmission, SettingsDirectoryLock::close);
    }

    /**
     * Runs the same initialization through an injected lock release for deterministic post-commit fault coverage.
     *
     * @param workingDirectory Settings pair directory
     * @param commitAdmission one-time callback guarding recovery and publication
     * @param lockClose release action for the acquired directory lock
     * @return initialized Settings or a classified failure
     */
    static synchronized InitializationResult initialize(Path workingDirectory, BooleanSupplier commitAdmission,
                                                        LockClose lockClose) {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(commitAdmission, "commitAdmission");
        Objects.requireNonNull(lockClose, "lockClose");
        Path directory = workingDirectory.toAbsolutePath().normalize();
        Path standardSource = directory.resolve("settings.json");
        Path uunpSource = directory.resolve("settings_UUNP.json");
        AtomicBoolean commitStarted = new AtomicBoolean();
        BooleanSupplier beginCommitOnce = () -> {
            if (commitStarted.get())
                return true;
            if (!commitAdmission.getAsBoolean())
                throw new CancellationException("Settings initialization cancelled before publication.");
            commitStarted.set(true);
            return true;
        };
        boolean recovered = false;
        SettingsJacksonAdapter.SettingsCandidate candidate = null;
        SettingsDirectoryLock directoryLock;
        try {
            directoryLock = SettingsDirectoryLock.acquire(directory);
        } catch (IOException exception) {
            return InitializationResult.failure(Failure.fromIo("SETTINGS_LOCK_FAILED", directory, exception));
        }
        Failure loadFailure = null;
        IOException closeFailure = null;
        try {
            try {
                recovered = SettingsPairPublisher.recover(directory, standardSource, uunpSource, beginCommitOnce);
            } catch (IOException exception) {
                loadFailure = Failure.fromIo("SETTINGS_RECOVERY_FAILED", directory, exception);
            }
            if (loadFailure == null) {
                try {
                    candidate = loadCandidate(standardSource, uunpSource, beginCommitOnce);
                    beginCommitOnce.getAsBoolean();
                } catch (SettingsJacksonAdapter.SettingsFormatException exception) {
                    loadFailure = Failure.fromAdapter(exception);
                } catch (IOException exception) {
                    loadFailure = Failure.fromIo("SETTINGS_PUBLISH_FAILED", directory, exception);
                }
            }
        } finally {
            try {
                lockClose.close(directoryLock);
            } catch (IOException exception) {
                closeFailure = exception;
            }
        }
        if (loadFailure != null)
            return InitializationResult.failure(loadFailure);
        if (closeFailure != null && !commitStarted.get())
            return InitializationResult.failure(Failure.fromIo("SETTINGS_LOCK_FAILED", directory, closeFailure));

        // Once recovery or publication crossed the commit boundary, live Settings must follow the committed files.
        publish(candidate);
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (recovered)
            diagnostics.add(Diagnostic.recovered(directory));
        diagnostics.addAll(candidate.diagnostics().stream().map(Diagnostic::fromAdapter).toList());
        if (closeFailure != null)
            diagnostics.add(Diagnostic.lockReleaseFailed(directory));
        return InitializationResult.success(diagnostics);
    }

    /**
     * Reads an existing pair while cancellation is still possible, or enters commit before creating a missing pair.
     * The caller owns the directory lock throughout this method.
     */
    private static SettingsJacksonAdapter.SettingsCandidate loadCandidate(Path standardSource, Path uunpSource,
                                                                          BooleanSupplier beginCommit)
            throws IOException {
        boolean standardExists = Files.exists(standardSource);
        boolean uunpExists = Files.exists(uunpSource);
        if (standardExists && uunpExists)
            return SettingsJacksonAdapter.readPair(standardSource, uunpSource);

        SettingsJacksonAdapter.SettingsCandidate defaults = defaultCandidate();
        List<SettingsJacksonAdapter.SettingsDiagnostic> diagnostics = new ArrayList<>();
        SettingsJacksonAdapter.SettingsProfile standard = standardExists
                ? SettingsJacksonAdapter.read(standardSource, diagnostics) : defaults.standard();
        SettingsJacksonAdapter.SettingsProfile uunp = uunpExists
                ? SettingsJacksonAdapter.read(uunpSource, diagnostics) : defaults.uunp();
        SettingsJacksonAdapter.SettingsCandidate candidate = new SettingsJacksonAdapter.SettingsCandidate(
                standard, uunp, diagnostics);
        SettingsJacksonAdapter.SettingsPairBytes encoded = SettingsJacksonAdapter.writePair(candidate);
        if (!beginCommit.getAsBoolean())
            throw new CancellationException("Settings initialization cancelled before first-run publication.");
        SettingsPairPublisher.publish(standardSource, uunpSource, encoded);
        return candidate;
    }

    /**
     * Builds the exact legacy defaults as one detached candidate for first-run paired publication.
     */
    private static SettingsJacksonAdapter.SettingsCandidate defaultCandidate() {
        Map<String, SettingsJacksonAdapter.DefaultValue> standardDefaults = new LinkedHashMap<>();
        standardDefaults.put("Breasts", new SettingsJacksonAdapter.DefaultValue(0.2f, 1f));
        standardDefaults.put("BreastsSmall", new SettingsJacksonAdapter.DefaultValue(1f, 1f));
        standardDefaults.put("NippleDistance", new SettingsJacksonAdapter.DefaultValue(1f, 1f));
        standardDefaults.put("NippleSize", new SettingsJacksonAdapter.DefaultValue(1f, 1f));
        standardDefaults.put("ButtCrack", new SettingsJacksonAdapter.DefaultValue(1f, 1f));
        standardDefaults.put("Butt", new SettingsJacksonAdapter.DefaultValue(0f, 1f));
        standardDefaults.put("ButtSmall", new SettingsJacksonAdapter.DefaultValue(1f, 1f));
        standardDefaults.put("Waist", new SettingsJacksonAdapter.DefaultValue(0f, 1f));
        standardDefaults.put("Legs", new SettingsJacksonAdapter.DefaultValue(0f, 1f));
        standardDefaults.put("Ankles", new SettingsJacksonAdapter.DefaultValue(1f, 1f));
        standardDefaults.put("Arms", new SettingsJacksonAdapter.DefaultValue(0f, 1f));
        standardDefaults.put("ShoulderWidth", new SettingsJacksonAdapter.DefaultValue(1f, 1f));

        Map<String, SettingsJacksonAdapter.DefaultValue> uunpDefaults = new LinkedHashMap<>();
        for (String name : List.of("Breasts", "BreastsSmall", "NippleDistance", "NippleSize", "Arms",
                "ShoulderWidth", "ButtCrack", "Butt", "ButtSmall", "Legs")) {
            uunpDefaults.put(name, new SettingsJacksonAdapter.DefaultValue(1f, 1f));
        }

        SettingsJacksonAdapter.SettingsProfile standard = new SettingsJacksonAdapter.SettingsProfile(
                standardDefaults, Collections.emptyMap(), List.of("Breasts", "BreastsSmall", "NippleDistance",
                "NippleSize", "ButtCrack", "Butt", "ButtSmall", "Legs", "Ankles", "Arms",
                "ShoulderWidth"));
        SettingsJacksonAdapter.SettingsProfile uunp = new SettingsJacksonAdapter.SettingsProfile(
                uunpDefaults, Collections.emptyMap(), List.of("Breasts", "BreastsSmall", "NippleDistance",
                "NippleSize", "Arms", "ShoulderWidth", "ButtCrack", "Butt", "ButtSmall", "Legs"));
        return new SettingsJacksonAdapter.SettingsCandidate(standard, uunp, Collections.emptyList());
    }

    /**
     * Constructs the entire replacement value before one volatile assignment makes the pair live.
     */
    private static void publish(SettingsJacksonAdapter.SettingsCandidate candidate) {
        liveSettings = LiveSettings.from(candidate);
    }

    /**
     * Returns the complete immutable Standard/UUNP Settings value currently used by Project editing and output.
     *
     * @return detached immutable Settings snapshot in canonical encounter order
     */
    public static Snapshot snapshot() {
        return liveSettings.snapshot();
    }

    /**
     * Returns a successful no-diagnostic initialization view for adapters that attach after another composition root
     * has already published the live pair. Startup callers should pass the original result when recovery evidence
     * must be rendered.
     *
     * @return successful view of the currently published pair
     */
    public static InitializationResult publishedState() {
        return InitializationResult.success(List.of());
    }

    /**
     * Atomically persists and publishes one complete Workbench-authored Settings pair. Readers observe either the
     * prior live pair or the fully installed replacement, and a failed write never partially changes live state.
     *
     * @param workingDirectory directory containing {@code settings.json} and {@code settings_UUNP.json}
     * @param replacement      complete immutable replacement pair
     * @return success with non-blocking diagnostics, or a failure before the replacement pair was committed
     */
    public static synchronized PersistenceResult persist(Path workingDirectory, Snapshot replacement) {
        return persist(workingDirectory, replacement, SettingsDirectoryLock::close);
    }

    /**
     * Persists through an injected lock-release seam so a post-commit release failure can be verified with real files.
     *
     * @param workingDirectory Settings pair directory
     * @param replacement      complete immutable replacement pair
     * @param lockClose        release action for the acquired directory lock
     * @return success with non-blocking diagnostics, or a classified pre-commit failure
     */
    static synchronized PersistenceResult persist(Path workingDirectory, Snapshot replacement, LockClose lockClose) {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(lockClose, "lockClose");
        Path directory = workingDirectory.toAbsolutePath().normalize();
        Path standardSource = directory.resolve("settings.json");
        Path uunpSource = directory.resolve("settings_UUNP.json");
        SettingsJacksonAdapter.SettingsCandidate candidate = replacement.toCandidate();
        SettingsJacksonAdapter.SettingsPairBytes encoded;
        try {
            encoded = SettingsJacksonAdapter.writePair(candidate);
        } catch (SettingsJacksonAdapter.SettingsFormatException exception) {
            return PersistenceResult.failure(Failure.fromAdapter(exception));
        }

        SettingsDirectoryLock directoryLock;
        try {
            directoryLock = SettingsDirectoryLock.tryAcquire(directory);
        } catch (IOException exception) {
            return PersistenceResult.failure(Failure.fromIo("SETTINGS_LOCK_FAILED", directory, exception));
        }
        boolean recovered = false;
        Failure publicationFailure = null;
        IOException closeFailure = null;
        try {
            try {
                recovered = SettingsPairPublisher.recover(directory, standardSource, uunpSource);
            } catch (IOException exception) {
                publicationFailure = Failure.fromIo("SETTINGS_RECOVERY_FAILED", directory, exception);
            }
            if (publicationFailure == null) {
                try {
                    SettingsPairPublisher.publish(standardSource, uunpSource, encoded);
                } catch (IOException exception) {
                    publicationFailure = Failure.fromIo("SETTINGS_PUBLISH_FAILED", directory, exception);
                }
            }
        } finally {
            try {
                lockClose.close(directoryLock);
            } catch (IOException exception) {
                closeFailure = exception;
            }
        }
        if (publicationFailure != null)
            return PersistenceResult.failure(publicationFailure);

        // A committed disk pair must become live even if releasing its lock reports a later cleanup failure.
        publish(candidate);
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (recovered)
            diagnostics.add(Diagnostic.recovered(directory));
        if (closeFailure != null)
            diagnostics.add(Diagnostic.lockReleaseFailed(directory));
        return PersistenceResult.success(diagnostics);
    }

    /** Narrow lock-release fault seam for deterministic committed-write tests. */
    @FunctionalInterface
    interface LockClose {
        /** Releases one already-acquired Settings directory lock. */
        void close(SettingsDirectoryLock lock) throws IOException;
    }

    /**
     * Resolves a Standard multiplier.
     *
     * @param sliderName exact dynamic Slider key
     * @return configured multiplier, or {@code 1f} when absent
     */
    public static float getMultiplier(String sliderName) {
        return liveSettings.standardMultipliers.getOrDefault(sliderName, Float.valueOf(1f));
    }

    /**
     * Resolves a UUNP multiplier.
     *
     * @param sliderName exact dynamic Slider key
     * @return configured multiplier, or {@code 1f} when absent
     */
    public static float getMultiplierUUNP(String sliderName) {
        return liveSettings.uunpMultipliers.getOrDefault(sliderName, Float.valueOf(1f));
    }

    /**
     * @return immutable Standard defaults in source encounter order
     */
    public static Map<String, DefaultSliderValue> getDefaultsMap() {
        return liveSettings.standardDefaults;
    }

    /**
     * @return immutable UUNP defaults in source encounter order
     */
    public static Map<String, DefaultSliderValue> getDefaultsMapUUNP() {
        return liveSettings.uunpDefaults;
    }

    /**
     * Resolves a Standard small endpoint as the legacy integer percentage.
     *
     * @param name exact dynamic Slider key
     * @return truncated percentage, or zero when absent
     */
    public static int getDefaultValueSmall(String name) {
        return percentage(liveSettings.standardDefaults.get(name), true);
    }

    /**
     * Resolves a UUNP small endpoint as the legacy integer percentage.
     *
     * @param name exact dynamic Slider key
     * @return truncated percentage, or zero when absent
     */
    public static int getDefaultValueSmallUUNP(String name) {
        return percentage(liveSettings.uunpDefaults.get(name), true);
    }

    /**
     * Resolves a Standard big endpoint as the legacy integer percentage.
     *
     * @param name exact dynamic Slider key
     * @return truncated percentage, or zero when absent
     */
    public static int getDefaultValueBig(String name) {
        return percentage(liveSettings.standardDefaults.get(name), false);
    }

    /**
     * Resolves a UUNP big endpoint as the legacy integer percentage.
     *
     * @param name exact dynamic Slider key
     * @return truncated percentage, or zero when absent
     */
    public static int getDefaultValueBigUUNP(String name) {
        return percentage(liveSettings.uunpDefaults.get(name), false);
    }

    /**
     * Converts one optional endpoint to the legacy truncated percentage.
     */
    private static int percentage(DefaultSliderValue value, boolean small) {
        if (value == null)
            return 0;
        return (int) ((small ? value.getValueSmall() : value.getValueBig()) * 100);
    }

    /**
     * Tests Standard inversion identity without changing its accepted case-insensitive meaning.
     *
     * @param sliderName Slider name to classify
     * @return true when the configured Standard inversion list contains the name
     */
    public static boolean isInverted(String sliderName) {
        return containsIgnoreCase(liveSettings.standardInverted, sliderName);
    }

    /**
     * Tests UUNP inversion identity without changing its accepted case-insensitive meaning.
     *
     * @param sliderName Slider name to classify
     * @return true when the configured UUNP inversion list contains the name
     */
    public static boolean isInvertedUUNP(String sliderName) {
        return containsIgnoreCase(liveSettings.uunpInverted, sliderName);
    }

    /**
     * Performs the legacy case-insensitive inversion lookup over an immutable encounter-ordered list.
     */
    private static boolean containsIgnoreCase(List<String> values, String expected) {
        for (String value : values) {
            if (value.equalsIgnoreCase(expected))
                return true;
        }
        return false;
    }

    /**
     * One immutable application-facing value containing both Settings profiles.
     */
    private record LiveSettings(Map<String, DefaultSliderValue> standardDefaults,
                                Map<String, Float> standardMultipliers, List<String> standardInverted,
                                Map<String, DefaultSliderValue> uunpDefaults, Map<String, Float> uunpMultipliers,
                                List<String> uunpInverted) {
        /** Creates an immutable, defensively owned live value. */
        private LiveSettings {
            standardDefaults = Collections.unmodifiableMap(new LinkedHashMap<>(standardDefaults));
            standardMultipliers = Collections.unmodifiableMap(new LinkedHashMap<>(standardMultipliers));
            standardInverted = List.copyOf(standardInverted);
            uunpDefaults = Collections.unmodifiableMap(new LinkedHashMap<>(uunpDefaults));
            uunpMultipliers = Collections.unmodifiableMap(new LinkedHashMap<>(uunpMultipliers));
            uunpInverted = List.copyOf(uunpInverted);
        }

        /** Converts the detached adapter candidate without retaining any adapter-owned mutable input. */
        private static LiveSettings from (SettingsJacksonAdapter.SettingsCandidate candidate){
            return new LiveSettings(convertDefaults(candidate.standard().defaults()),
                    candidate.standard().multipliers(), candidate.standard().inverted(),
                    convertDefaults(candidate.uunp().defaults()), candidate.uunp().multipliers(),
                    candidate.uunp().inverted());
        }

        /** Returns a detached public snapshot without exposing the live value's collection identities. */
        private Snapshot snapshot() {
            return new Snapshot(new Profile(standardDefaults, standardMultipliers, standardInverted),
                    new Profile(uunpDefaults, uunpMultipliers, uunpInverted));
        }

        /** Creates the pre-initialization empty value used before application startup. */
        private static LiveSettings empty () {
            return new LiveSettings(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        }

        /** Converts adapter endpoint records into the application-facing immutable endpoint values. */
        private static Map<String, DefaultSliderValue> convertDefaults (
                Map < String, SettingsJacksonAdapter.DefaultValue > source){
            Map<String, DefaultSliderValue> converted = new LinkedHashMap<>();
            for (Map.Entry<String, SettingsJacksonAdapter.DefaultValue> entry : source.entrySet()) {
                converted.put(entry.getKey(), new DefaultSliderValue(entry.getValue().valueSmall(),
                        entry.getValue().valueBig()));
            }
            return converted;
        }
    }

    /**
     * Ordered non-blocking warning emitted while reading or recovering Settings.
     */
    public static final class Diagnostic {
        private final String code;
        private final String source;
        private final String path;
        private final String message;

        /**
         * Creates one immutable warning translated at the Settings package boundary.
         */
        private Diagnostic(String code, String source, String path, String message) {
            this.code = code;
            this.source = source;
            this.path = path;
            this.message = message;
        }

        /**
         * Converts an internal adapter warning without exposing Jackson or adapter types.
         */
        private static Diagnostic fromAdapter(SettingsJacksonAdapter.SettingsDiagnostic diagnostic) {
            return new Diagnostic(diagnostic.code(), diagnostic.source(), diagnostic.path(), diagnostic.message());
        }

        /**
         * Creates the ordered warning emitted after restoring an interrupted paired publication.
         */
        private static Diagnostic recovered(Path directory) {
            return new Diagnostic("SETTINGS_PUBLICATION_RECOVERED", directory.toString(), "/",
                    "An interrupted Settings publication was rolled back before loading.");
        }

        /** Warns that a committed replacement is live even though its lock could not be released cleanly. */
        private static Diagnostic lockReleaseFailed(Path directory) {
            return new Diagnostic("SETTINGS_LOCK_RELEASE_FAILED", directory.toString(), "/",
                    "The Settings pair was committed, but its directory lock could not be released cleanly.");
        }

        /**
         * @return stable machine-readable warning code
         */
        public String getCode() {
            return code;
        }

        /**
         * @return source Settings filename
         */
        public String getSource() {
            return source;
        }

        /**
         * @return escaped JSON-pointer-like member path
         */
        public String getPath() {
            return path;
        }

        /**
         * @return human-readable warning
         */
        public String getMessage() {
            return message;
        }
    }

    /**
     * Stable Settings rejection with an owned code, source, member path, and optional coordinate.
     */
    public static final class Failure {
        private final String code;
        private final String source;
        private final String path;
        private final int line;
        private final int column;
        private final String message;

        /**
         * Creates one immutable failure translated at the Settings package boundary.
         */
        private Failure(String code, String source, String path, int line, int column, String message) {
            this.code = code;
            this.source = source;
            this.path = path;
            this.line = line;
            this.column = column;
            this.message = message;
        }

        /**
         * Converts an internal adapter rejection without leaking its exception type.
         */
        private static Failure fromAdapter(SettingsJacksonAdapter.SettingsFormatException exception) {
            return new Failure(exception.code(), exception.source(), exception.path(), exception.line(),
                    exception.column(), exception.getMessage());
        }

        /**
         * Converts a structurally classified persistence I/O failure into the stable Settings failure surface.
         */
        private static Failure fromIo(String code, Path directory, IOException exception) {
            String message = exception.getMessage() == null ? "Settings files could not be published."
                    : exception.getMessage();
            return new Failure(code, directory.toString(), "/", 0, 0, message);
        }

        /**
         * @return stable machine-readable Settings code
         */
        public String getCode() {
            return code;
        }

        /**
         * @return source Settings filename
         */
        public String getSource() {
            return source;
        }

        /**
         * @return escaped JSON-pointer-like member path
         */
        public String getPath() {
            return path;
        }

        /**
         * @return one-based line, or zero when no source coordinate exists
         */
        public int getLine() {
            return line;
        }

        /**
         * @return one-based column, or zero when no source coordinate exists
         */
        public int getColumn() {
            return column;
        }

        /**
         * @return human-readable rejection detail
         */
        public String getMessage() {
            return message;
        }

        /**
         * @return one line suitable for the startup failure notification
         */
        public String formatForDisplay() {
            String coordinate = line > 0 && column > 0 ? " (line " + line + ", column " + column + ")" : "";
            return code + ": " + source + " " + path + coordinate + System.lineSeparator() + message;
        }
    }

    /**
     * Result of attempting to publish one Settings pair into the live lookup state.
     */
    public static final class InitializationResult {
        private final List<Diagnostic> diagnostics;
        private final Failure failure;

        /**
         * Creates one immutable success or failure result.
         */
        private InitializationResult(List<Diagnostic> diagnostics, Failure failure) {
            this.diagnostics = List.copyOf(diagnostics);
            this.failure = failure;
        }

        /**
         * Returns one successful result carrying ordered non-blocking warnings.
         */
        private static InitializationResult success(List<Diagnostic> diagnostics) {
            return new InitializationResult(diagnostics, null);
        }

        /**
         * Returns one rejected result carrying the stable blocking diagnostic.
         */
        private static InitializationResult failure(Failure failure) {
            return new InitializationResult(Collections.emptyList(), Objects.requireNonNull(failure, "failure"));
        }

        /**
         * @return true only when both validated profiles became live
         */
        public boolean isSuccessful() {
            return failure == null;
        }

        /**
         * @return ordered forward-compatibility and recovery warnings from the published pair
         */
        public List<Diagnostic> getDiagnostics() {
            return diagnostics;
        }

        /**
         * @return the blocking diagnostic when publication was rejected
         */
        public Optional<Failure> getFailure() {
            return Optional.ofNullable(failure);
        }
    }

    /**
     * Result of one Workbench-authored paired Settings persistence attempt.
     */
    public static final class PersistenceResult {
        private final List<Diagnostic> diagnostics;
        private final Failure failure;

        /** Creates one immutable persistence success or failure. */
        private PersistenceResult(List<Diagnostic> diagnostics, Failure failure) {
            this.diagnostics = List.copyOf(diagnostics);
            this.failure = failure;
        }

        /** Returns one successful result carrying ordered non-blocking warnings. */
        private static PersistenceResult success(List<Diagnostic> diagnostics) {
            return new PersistenceResult(diagnostics, null);
        }

        /** Returns one rejected result carrying the stable blocking diagnostic. */
        private static PersistenceResult failure(Failure failure) {
            return new PersistenceResult(Collections.emptyList(), Objects.requireNonNull(failure, "failure"));
        }

        /** @return true only when both replacement profiles became durable and live */
        public boolean isSuccessful() {
            return failure == null;
        }

        /** @return ordered recovery and lock-release warnings */
        public List<Diagnostic> getDiagnostics() {
            return diagnostics;
        }

        /** @return the blocking persistence diagnostic when the prior live pair was retained */
        public Optional<Failure> getFailure() {
            return Optional.ofNullable(failure);
        }
    }

    /**
     * Immutable application-facing Standard or UUNP Settings profile.
     *
     * @param defaults    Slider endpoint defaults in canonical encounter order
     * @param multipliers output multipliers in canonical encounter order
     * @param inverted    case-insensitive inversion identities in canonical encounter order
     */
    public record Profile(Map<String, DefaultSliderValue> defaults, Map<String, Float> multipliers,
                          List<String> inverted) {
        /** Defensively owns every profile collection and rejects null or non-finite values. */
        public Profile {
            defaults = immutableDefaults(defaults);
            multipliers = immutableMultipliers(multipliers);
            inverted = List.copyOf(Objects.requireNonNull(inverted, "inverted"));
            for (String name : inverted)
                Objects.requireNonNull(name, "inverted name");
        }

        /** Converts the public value into the repository-owned codec boundary. */
        private SettingsJacksonAdapter.SettingsProfile toAdapter() {
            Map<String, SettingsJacksonAdapter.DefaultValue> convertedDefaults = new LinkedHashMap<>();
            for (Map.Entry<String, DefaultSliderValue> entry : defaults.entrySet()) {
                convertedDefaults.put(entry.getKey(), new SettingsJacksonAdapter.DefaultValue(
                        entry.getValue().getValueSmall(), entry.getValue().getValueBig()));
            }
            return new SettingsJacksonAdapter.SettingsProfile(convertedDefaults, multipliers, inverted);
        }

        /** Copies endpoint values while preserving encounter order and validating writer invariants. */
        private static Map<String, DefaultSliderValue> immutableDefaults(Map<String, DefaultSliderValue> source) {
            Objects.requireNonNull(source, "defaults");
            Map<String, DefaultSliderValue> copy = new LinkedHashMap<>();
            for (Map.Entry<String, DefaultSliderValue> entry : source.entrySet()) {
                String name = Objects.requireNonNull(entry.getKey(), "default name");
                DefaultSliderValue value = Objects.requireNonNull(entry.getValue(), "default value");
                if (!Float.isFinite(value.getValueSmall()) || !Float.isFinite(value.getValueBig()))
                    throw new IllegalArgumentException("Settings default values must be finite");
                copy.put(name, value);
            }
            return Collections.unmodifiableMap(copy);
        }

        /** Copies multiplier values while preserving encounter order and validating writer invariants. */
        private static Map<String, Float> immutableMultipliers(Map<String, Float> source) {
            Objects.requireNonNull(source, "multipliers");
            Map<String, Float> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Float> entry : source.entrySet()) {
                String name = Objects.requireNonNull(entry.getKey(), "multiplier name");
                Float value = Objects.requireNonNull(entry.getValue(), "multiplier value");
                if (!Float.isFinite(value.floatValue()))
                    throw new IllegalArgumentException("Settings multipliers must be finite");
                copy.put(name, value);
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    /**
     * Complete immutable Settings persistence and output-consumption unit.
     *
     * @param standard Standard BodySlide profile
     * @param uunp     UUNP BodySlide profile
     */
    public record Snapshot(Profile standard, Profile uunp) {
        /** Requires both profile values. */
        public Snapshot {
            Objects.requireNonNull(standard, "standard");
            Objects.requireNonNull(uunp, "uunp");
        }

        /** Converts the public pair into one atomic repository-owned candidate. */
        private SettingsJacksonAdapter.SettingsCandidate toCandidate() {
            return new SettingsJacksonAdapter.SettingsCandidate(standard.toAdapter(), uunp.toAdapter(), List.of());
        }
    }

    /**
     * Immutable Slider endpoint defaults exposed to Project value construction.
     */
    public static final class DefaultSliderValue {
        private final float valueSmall;
        private final float valueBig;

        /**
         * Creates one endpoint pair.
         *
         * @param valueSmall small endpoint expressed as a float fraction
         * @param valueBig   big endpoint expressed as a float fraction
         */
        public DefaultSliderValue(float valueSmall, float valueBig) {
            this.valueSmall = valueSmall;
            this.valueBig = valueBig;
        }

        /**
         * @return small endpoint float fraction
         */
        public float getValueSmall() {
            return valueSmall;
        }

        /**
         * @return big endpoint float fraction
         */
        public float getValueBig() {
            return valueBig;
        }

        /** Compares endpoint values by their exact finite float representation. */
        @Override
        public boolean equals(Object other) {
            return other instanceof DefaultSliderValue value
                    && Float.floatToIntBits(valueSmall) == Float.floatToIntBits(value.valueSmall)
                    && Float.floatToIntBits(valueBig) == Float.floatToIntBits(value.valueBig);
        }

        /** @return hash of the exact endpoint float representations */
        @Override
        public int hashCode() {
            return Objects.hash(Float.floatToIntBits(valueSmall), Float.floatToIntBits(valueBig));
        }
    }
}
