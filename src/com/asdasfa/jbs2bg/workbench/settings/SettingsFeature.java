package com.asdasfa.jbs2bg.workbench.settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.workbench.GenerationPreferencesStore;

/**
 * Owns Workbench Settings drafts, validation, paired persistence, and immutable presentation frames.
 */
public final class SettingsFeature {
    private final Path directory;
    private final GenerationPreferencesStore generationPreferences;
    private MutableProfile standard;
    private MutableProfile uunp;
    private Settings.Snapshot baseline;
    private Profile selectedProfile = Profile.STANDARD;
    private String selectedStandard;
    private String selectedUunp;
    private EditorFrame rejectedDraft;
    private List<Validation> validation = List.of();
    private List<Notice> notices;
    private OutcomeKind outcome = OutcomeKind.NONE;
    private boolean liveAvailable;
    private boolean omitRedundantSliders;
    private long revision;
    private Frame frame;

    /**
     * Creates a Settings feature from the pair currently published by application startup.
     *
     * @param workingDirectory directory owning the paired Settings files
     * @param initialization   startup recovery, warning, or failure evidence
     */
    public SettingsFeature(Path workingDirectory, Settings.InitializationResult initialization) {
        this(workingDirectory, initialization, false);
    }

    /**
     * Creates a Settings feature and optionally completes the one-time packaged generation-preference migration.
     *
     * @param workingDirectory application profile directory
     * @param initialization   paired Settings startup result
     * @param migrateLegacyPreference whether an absent profile-local preference should be created from legacy state
     */
    public SettingsFeature(Path workingDirectory, Settings.InitializationResult initialization,
                           boolean migrateLegacyPreference) {
        directory = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        generationPreferences = new GenerationPreferencesStore(directory);
        Objects.requireNonNull(initialization, "initialization");
        baseline = Settings.snapshot();
        liveAvailable = initialization.isSuccessful();
        standard = MutableProfile.from(baseline.standard());
        uunp = MutableProfile.from(baseline.uunp());
        selectedStandard = standard.firstName().orElse(null);
        selectedUunp = uunp.firstName().orElse(null);
        List<Notice> startupNotices = new ArrayList<>(initializationNotices(initialization));
        try {
            omitRedundantSliders = migrateLegacyPreference
                    ? generationPreferences.loadOrMigrate() : generationPreferences.loadLegacyFallback();
        } catch (IOException exception) {
            startupNotices.add(Notice.preferenceFailure(directory, exception));
        }
        notices = List.copyOf(startupNotices);
        publish();
    }

    /** @return the latest completely committed immutable Settings frame */
    public Frame frame() {
        return frame;
    }

    /**
     * Applies one task-oriented Settings intent on the serialized presentation lane.
     *
     * @param intent immutable user task
     * @return whether the task was accepted and the resulting immutable frame
     */
    public Update dispatch(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        return switch (intent) {
            case SelectProfile selectProfile -> selectProfile(selectProfile.profile());
            case SelectEntry selectEntry -> selectEntry(selectEntry.name());
            case AddEntry addEntry -> addEntry(addEntry.name());
            case EditEntry editEntry -> editEntry(editEntry);
            case RemoveEntry removeEntry -> removeEntry(removeEntry.name());
            case ChangeOmitRedundantSliders changeOmit -> changeOmit(changeOmit.selected());
            case Save ignored -> save();
            case Reload ignored -> reload();
            case DismissNotice ignored -> dismissNotice();
        };
    }

    /** Selects one profile without discarding either profile's draft or logical selection. */
    private Update selectProfile(Profile profile) {
        selectedProfile = Objects.requireNonNull(profile, "profile");
        rejectedDraft = null;
        validation = List.of();
        outcome = OutcomeKind.NONE;
        publish();
        return accepted();
    }

    /** Selects one exact Settings member identity without silently retargeting a missing name. */
    private Update selectEntry(String name) {
        String required = Objects.requireNonNull(name, "name");
        if (!currentProfile().contains(required))
            return rejected(required, "The Settings entry no longer exists.");
        setSelectedName(required);
        rejectedDraft = null;
        validation = List.of();
        outcome = OutcomeKind.NONE;
        publish();
        return accepted();
    }

    /** Adds one materialized 0/1 default entry to the active profile. */
    private Update addEntry(String name) {
        String required = Objects.requireNonNull(name, "name");
        Validation nameProblem = validateName(null, required);
        if (nameProblem != null)
            return rejected(new EditorFrame(null, required, "0.0", "1.0", "", false), nameProblem);
        currentProfile().defaults.put(required, new DefaultSliderValue(0f, 1f));
        setSelectedName(required);
        clearTransientState();
        outcome = OutcomeKind.CHANGED;
        publish();
        return accepted();
    }

    /** Validates and atomically replaces one logical entry across defaults, multipliers, and inversion state. */
    private Update editEntry(EditEntry intent) {
        String original = Objects.requireNonNull(intent.originalName(), "originalName");
        EditorFrame draft = new EditorFrame(original, Objects.requireNonNull(intent.name(), "name"),
                intent.small().orElse(""), intent.big().orElse(""), intent.multiplier().orElse(""),
                intent.inverted());
        List<Validation> problems = validateEdit(draft);
        if (!problems.isEmpty()) {
            rejectedDraft = draft;
            validation = problems;
            outcome = OutcomeKind.REJECTED;
            publish();
            return new Update(false, frame);
        }

        MutableProfile profile = currentProfile();
        String name = draft.name();
        Optional<Float> small = optionalFloat(draft.small());
        Optional<Float> big = optionalFloat(draft.big());
        Optional<Float> multiplier = optionalFloat(draft.multiplier());
        profile.remove(original);
        if (small.isPresent())
            profile.defaults.put(name, new DefaultSliderValue(small.orElseThrow(), big.orElseThrow()));
        multiplier.ifPresent(value -> profile.multipliers.put(name, value));
        if (draft.inverted())
            profile.inverted.add(name);
        setSelectedName(name);
        clearTransientState();
        outcome = draftSnapshot().equals(baseline) ? OutcomeKind.UNCHANGED : OutcomeKind.CHANGED;
        publish();
        return accepted();
    }

    /** Removes one exact entry from every active-profile Settings family. */
    private Update removeEntry(String name) {
        String required = Objects.requireNonNull(name, "name");
        if (!currentProfile().contains(required))
            return rejected(required, "The Settings entry no longer exists.");
        currentProfile().remove(required);
        setSelectedName(currentProfile().firstName().orElse(null));
        clearTransientState();
        outcome = OutcomeKind.CHANGED;
        publish();
        return accepted();
    }

    /** Persists the migrated generation preference immediately without changing either Settings JSON profile. */
    private Update changeOmit(boolean selected) {
        if (omitRedundantSliders == selected) {
            outcome = OutcomeKind.UNCHANGED;
            publish();
            return accepted();
        }
        try {
            generationPreferences.save(selected);
        } catch (IOException exception) {
            notices = List.of(Notice.preferenceFailure(directory, exception));
            outcome = OutcomeKind.FAILED;
            publish();
            return new Update(false, frame);
        }
        omitRedundantSliders = selected;
        notices = List.of();
        outcome = OutcomeKind.CHANGED;
        publish();
        return accepted();
    }

    /** Persists both drafts through the owned atomic Settings pair boundary. */
    private Update save() {
        if (rejectedDraft != null)
            return new Update(false, frame);
        Settings.Snapshot replacement = draftSnapshot();
        if (replacement.equals(baseline)) {
            outcome = OutcomeKind.UNCHANGED;
            publish();
            return accepted();
        }
        Settings.PersistenceResult result = Settings.persist(directory, replacement);
        if (!result.isSuccessful()) {
            Settings.Failure failure = result.getFailure().orElseThrow();
            notices = List.of(Notice.failure(failure));
            outcome = OutcomeKind.FAILED;
            publish();
            return new Update(false, frame);
        }
        baseline = Settings.snapshot();
        liveAvailable = true;
        standard = MutableProfile.from(baseline.standard());
        uunp = MutableProfile.from(baseline.uunp());
        notices = result.getDiagnostics().stream().map(Notice::warning).toList();
        outcome = OutcomeKind.SAVED;
        publish();
        return accepted();
    }

    /** Reloads and recovers the on-disk pair while retaining the current draft if validation fails. */
    private Update reload() {
        Settings.InitializationResult result = Settings.initialize(directory);
        notices = initializationNotices(result);
        if (!result.isSuccessful()) {
            outcome = OutcomeKind.FAILED;
            publish();
            return new Update(false, frame);
        }
        baseline = Settings.snapshot();
        liveAvailable = true;
        standard = MutableProfile.from(baseline.standard());
        uunp = MutableProfile.from(baseline.uunp());
        selectedStandard = retainSelection(standard, selectedStandard);
        selectedUunp = retainSelection(uunp, selectedUunp);
        rejectedDraft = null;
        validation = List.of();
        outcome = result.getDiagnostics().isEmpty() ? OutcomeKind.RELOADED : OutcomeKind.RECOVERED;
        publish();
        return accepted();
    }

    /** Dismisses pane-local Settings evidence without changing the durable pair or draft. */
    private Update dismissNotice() {
        notices = List.of();
        publish();
        return accepted();
    }

    /** Returns initialization diagnostics in feature-owned presentation vocabulary. */
    private static List<Notice> initializationNotices(Settings.InitializationResult initialization) {
        if (!initialization.isSuccessful())
            return List.of(Notice.failure(initialization.getFailure().orElseThrow()));
        return initialization.getDiagnostics().stream().map(Notice::warning).toList();
    }

    /** Validates one complete editor draft without mutating either profile. */
    private List<Validation> validateEdit(EditorFrame draft) {
        List<Validation> problems = new ArrayList<>();
        if (!currentProfile().contains(Objects.requireNonNull(draft.originalName(), "originalName")))
            problems.add(new Validation("SETTINGS_ENTRY_MISSING", "The Settings entry no longer exists."));
        Validation nameProblem = validateName(draft.originalName(), draft.name());
        if (nameProblem != null)
            problems.add(nameProblem);
        boolean smallPresent = !draft.small().isBlank();
        boolean bigPresent = !draft.big().isBlank();
        if (smallPresent != bigPresent)
            problems.add(new Validation("SETTINGS_DEFAULT_PAIR_REQUIRED",
                    "Small and big defaults must both be present or both be blank."));
        validateFloat(draft.small(), "small default", problems);
        validateFloat(draft.big(), "big default", problems);
        validateFloat(draft.multiplier(), "multiplier", problems);
        if (!smallPresent && draft.multiplier().isBlank() && !draft.inverted())
            problems.add(new Validation("SETTINGS_ENTRY_EMPTY",
                    "An entry must define defaults, a multiplier, or inversion."));
        return List.copyOf(problems);
    }

    /** Validates a nonblank exact member name without rejecting accepted case-distinct identities. */
    private Validation validateName(String original, String name) {
        if (name.isBlank())
            return new Validation("SETTINGS_NAME_REQUIRED", "A Settings entry name is required.");
        if (!name.equals(original) && currentProfile().contains(name))
            return new Validation("SETTINGS_NAME_DUPLICATE", "That exact Settings entry already exists.");
        return null;
    }

    /** Adds a stable validation message when nonblank text is not one finite Java float. */
    private static void validateFloat(String value, String label, List<Validation> problems) {
        if (value.isBlank())
            return;
        try {
            if (!Float.isFinite(Float.parseFloat(value)))
                throw new NumberFormatException("non-finite");
        } catch (NumberFormatException exception) {
            problems.add(new Validation("SETTINGS_NUMBER_INVALID",
                    "The " + label + " must be a finite number."));
        }
    }

    /** Converts already validated optional number text. */
    private static Optional<Float> optionalFloat(String value) {
        return value.isBlank() ? Optional.empty() : Optional.of(Float.parseFloat(value));
    }

    /** Publishes one immutable frame after all feature state has committed. */
    private void publish() {
        MutableProfile profile = currentProfile();
        List<EntryFrame> entries = profile.entries();
        String selected = selectedName();
        if (selected != null && !profile.contains(selected)) {
            selected = null;
            setSelectedName(null);
        }
        Optional<EditorFrame> editor = rejectedDraft == null
                ? Optional.ofNullable(selected).flatMap(profile::editor)
                : Optional.of(rejectedDraft);
        frame = new Frame(++revision, selectedProfile, entries, Optional.ofNullable(selected), editor,
                !draftSnapshot().equals(baseline), liveAvailable, omitRedundantSliders,
                validation, notices, outcome);
    }

    /** Clears validation and rejected text after one accepted draft operation. */
    private void clearTransientState() {
        rejectedDraft = null;
        validation = List.of();
        notices = List.of();
    }

    /** Creates one stable missing-entry rejection frame. */
    private Update rejected(String name, String message) {
        return rejected(new EditorFrame(name, name, "", "", "", false),
                new Validation("SETTINGS_ENTRY_MISSING", message));
    }

    /** Creates one rejected editor frame carrying all raw input text. */
    private Update rejected(EditorFrame draft, Validation problem) {
        rejectedDraft = draft;
        validation = List.of(problem);
        outcome = OutcomeKind.REJECTED;
        publish();
        return new Update(false, frame);
    }

    /** Returns one accepted update around the latest frame. */
    private Update accepted() {
        return new Update(true, frame);
    }

    /** Returns the active mutable draft profile. */
    private MutableProfile currentProfile() {
        return selectedProfile == Profile.STANDARD ? standard : uunp;
    }

    /** Returns the active profile's retained exact selection. */
    private String selectedName() {
        return selectedProfile == Profile.STANDARD ? selectedStandard : selectedUunp;
    }

    /** Stores an exact selection independently for each Settings profile. */
    private void setSelectedName(String name) {
        if (selectedProfile == Profile.STANDARD)
            selectedStandard = name;
        else
            selectedUunp = name;
    }

    /** Retains a still-present selection or falls back deterministically to the first entry. */
    private static String retainSelection(MutableProfile profile, String selection) {
        return selection != null && profile.contains(selection) ? selection : profile.firstName().orElse(null);
    }

    /** Freezes both drafts as the complete pair consumed by persistence and dirty comparison. */
    private Settings.Snapshot draftSnapshot() {
        return new Settings.Snapshot(standard.snapshot(), uunp.snapshot());
    }

    /** Mutable feature-owned profile draft hidden behind immutable frames. */
    private static final class MutableProfile {
        private final LinkedHashMap<String, DefaultSliderValue> defaults;
        private final LinkedHashMap<String, Float> multipliers;
        private final List<String> inverted;

        /** Takes detached ownership of one public immutable profile. */
        private MutableProfile(Settings.Profile profile) {
            defaults = new LinkedHashMap<>(profile.defaults());
            multipliers = new LinkedHashMap<>(profile.multipliers());
            inverted = new ArrayList<>(profile.inverted());
        }

        /** Creates a mutable feature draft from one immutable Settings profile. */
        private static MutableProfile from(Settings.Profile profile) {
            return new MutableProfile(Objects.requireNonNull(profile, "profile"));
        }

        /** Returns exact union membership without conflating accepted case-distinct names. */
        private boolean contains(String name) {
            return names().contains(name);
        }

        /** Returns the first canonical exact identity when the profile is nonempty. */
        private Optional<String> firstName() {
            return names().stream().findFirst();
        }

        /** Returns union names in Defaults, Multipliers, then Inverted encounter order. */
        private LinkedHashSet<String> names() {
            LinkedHashSet<String> names = new LinkedHashSet<>(defaults.keySet());
            names.addAll(multipliers.keySet());
            names.addAll(inverted);
            return names;
        }

        /** Projects every exact identity without losing category omission. */
        private List<EntryFrame> entries() {
            return names().stream().map(name -> {
                DefaultSliderValue value = defaults.get(name);
                return new EntryFrame(name,
                        value == null ? Optional.empty() : Optional.of(value.getValueSmall()),
                        value == null ? Optional.empty() : Optional.of(value.getValueBig()),
                        Optional.ofNullable(multipliers.get(name)), isInverted(name));
            }).toList();
        }

        /** Projects one exact identity as editable raw float text. */
        private Optional<EditorFrame> editor(String name) {
            if (!contains(name))
                return Optional.empty();
            DefaultSliderValue value = defaults.get(name);
            return Optional.of(new EditorFrame(name, name,
                    value == null ? "" : Float.toString(value.getValueSmall()),
                    value == null ? "" : Float.toString(value.getValueBig()),
                    multipliers.containsKey(name) ? Float.toString(multipliers.get(name)) : "",
                    isInverted(name)));
        }

        /** Removes all categories owned by one logical exact row, including case-insensitive inversion identity. */
        private void remove(String name) {
            defaults.remove(name);
            multipliers.remove(name);
            inverted.removeIf(value -> value.equalsIgnoreCase(name));
        }

        /** Applies the accepted case-insensitive inversion lookup. */
        private boolean isInverted(String name) {
            return inverted.stream().anyMatch(value -> value.equalsIgnoreCase(name));
        }

        /** Freezes the current profile without exposing mutable feature state. */
        private Settings.Profile snapshot() {
            List<String> deduplicatedInverted = new ArrayList<>();
            LinkedHashSet<String> identities = new LinkedHashSet<>();
            for (String name : inverted) {
                if (identities.add(name.toLowerCase(Locale.ROOT)))
                    deduplicatedInverted.add(name);
            }
            return new Settings.Profile(defaults, multipliers, deduplicatedInverted);
        }
    }

    /** Settings profile identity retained independently across rail navigation. */
    public enum Profile {
        STANDARD("Standard"),
        UUNP("UUNP");

        private final String displayName;

        /** Creates one user-facing profile identity. */
        Profile(String displayName) {
            this.displayName = displayName;
        }

        /** @return stable profile label */
        public String displayName() {
            return displayName;
        }
    }

    /** Stable outcome distinction used by Settings feedback. */
    public enum OutcomeKind {
        NONE,
        CHANGED,
        UNCHANGED,
        REJECTED,
        SAVED,
        RELOADED,
        RECOVERED,
        FAILED
    }

    /** Complete task-intent family accepted by the Settings feature. */
    public sealed interface Intent permits SelectProfile, SelectEntry, AddEntry, EditEntry, RemoveEntry,
            ChangeOmitRedundantSliders, Save, Reload, DismissNotice {
    }

    /** Selects Standard or UUNP without discarding either draft. */
    public record SelectProfile(Profile profile) implements Intent {
        /** Requires a profile identity. */
        public SelectProfile {
            Objects.requireNonNull(profile, "profile");
        }
    }

    /** Selects one exact Settings member identity. */
    public record SelectEntry(String name) implements Intent {
    }

    /** Adds one default entry to the active profile. */
    public record AddEntry(String name) implements Intent {
    }

    /** Replaces one exact entry using optional raw number text so validation can retain rejected drafts. */
    public record EditEntry(String originalName, String name, Optional<String> small, Optional<String> big,
                            Optional<String> multiplier, boolean inverted) implements Intent {
        /** Defensively owns every optional raw value. */
        public EditEntry {
            Objects.requireNonNull(originalName, "originalName");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(small, "small");
            Objects.requireNonNull(big, "big");
            Objects.requireNonNull(multiplier, "multiplier");
        }
    }

    /** Removes one exact entry from every Settings category in the active profile. */
    public record RemoveEntry(String name) implements Intent {
    }

    /** Changes the profile-local generated-output omission choice. */
    public record ChangeOmitRedundantSliders(boolean selected) implements Intent {
    }

    /** Persists both complete profile drafts atomically. */
    public record Save() implements Intent {
    }

    /** Reloads and recovers the paired Settings files. */
    public record Reload() implements Intent {
    }

    /** Dismisses the current pane-local persistence notice. */
    public record DismissNotice() implements Intent {
    }

    /** Immutable visible Settings entry with optional category membership. */
    public record EntryFrame(String name, Optional<Float> small, Optional<Float> big, Optional<Float> multiplier,
                             boolean inverted) {
        /** Defensively owns every optional value. */
        public EntryFrame {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(small, "small");
            Objects.requireNonNull(big, "big");
            Objects.requireNonNull(multiplier, "multiplier");
        }
    }

    /** Raw selected-entry draft retained when validation rejects an edit. */
    public record EditorFrame(String originalName, String name, String small, String big, String multiplier,
                              boolean inverted) {
        /** Requires all display text while allowing a null original only for a rejected add. */
        public EditorFrame {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(small, "small");
            Objects.requireNonNull(big, "big");
            Objects.requireNonNull(multiplier, "multiplier");
        }
    }

    /** One stable inline validation code and human-readable explanation. */
    public record Validation(String code, String message) {
        /** Requires a stable nonblank code and message. */
        public Validation {
            if (Objects.requireNonNull(code, "code").isBlank() ||
                    Objects.requireNonNull(message, "message").isBlank())
                throw new IllegalArgumentException("Settings validation code and message must not be blank");
        }
    }

    /** Pane-local persistence or recovery evidence. */
    public record Notice(String code, String source, String path, String message, boolean failure) {
        /** Converts one startup or reload warning. */
        private static Notice warning(Settings.Diagnostic diagnostic) {
            return new Notice(diagnostic.getCode(), diagnostic.getSource(), diagnostic.getPath(),
                    diagnostic.getMessage(), false);
        }

        /** Converts one stable Settings failure. */
        private static Notice failure(Settings.Failure failure) {
            return new Notice(failure.getCode(), failure.getSource(), failure.getPath(),
                    failure.getMessage(), true);
        }

        /** Converts one profile-local generation preference I/O failure. */
        private static Notice preferenceFailure(Path directory, IOException failure) {
            String message = failure.getMessage() == null
                    ? "The generation preference could not be persisted." : failure.getMessage();
            return new Notice("GENERATION_PREFERENCES_IO_FAILED", directory.toString(), "/omitRedundantSliders",
                    message, true);
        }

        /** Requires complete diagnostic evidence. */
        public Notice {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(message, "message");
        }
    }

    /** Complete immutable Settings feature frame. */
    public record Frame(long revision, Profile profile, List<EntryFrame> entries, Optional<String> selection,
                        Optional<EditorFrame> editor, boolean dirty, boolean liveAvailable,
                        boolean omitRedundantSliders, List<Validation> validation, List<Notice> notices,
                        OutcomeKind outcome) {
        /** Defensively owns every collection and optional value. */
        public Frame {
            if (revision <= 0)
                throw new IllegalArgumentException("revision must be positive");
            Objects.requireNonNull(profile, "profile");
            entries = List.copyOf(entries);
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(editor, "editor");
            validation = List.copyOf(validation);
            notices = List.copyOf(notices);
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Result of one task intent and its committed immutable frame. */
    public record Update(boolean accepted, Frame frame) {
        /** Requires a frame. */
        public Update {
            Objects.requireNonNull(frame, "frame");
        }
    }
}
