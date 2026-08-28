package com.asdasfa.jbs2bg.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Immutable Project content: the Slider Preset catalog, the Custom Morph Targets,
 * the NPC Morph Assignments, their canonical case-insensitive order, lookup by
 * name or identity, and referential integrity between Slider Presets and the
 * values that reference them (ADR-0002).
 *
 * <p>Lifecycle state (file identity, dirty, recovered) is deliberately not owned
 * here; the session supplies it when it asks for a {@link ProjectSnapshot}. There
 * is no lock, no JavaFX, and no file I/O.
 *
 * <p>Every operation that changes nothing returns {@code this}, so callers can
 * detect a no-op by instance identity. An operation that can violate a Project
 * rule returns a {@link Result}; one that cannot fail returns the next aggregate
 * directly. Operations on a Slider Preset name that is not in the catalog are a
 * caller error (the session looks up first) and throw rather than diagnose.
 */
final class Project {

    /** Canonical Slider Preset order: display name, without regard to case. */
    static final Comparator<SliderPresetSnapshot> SLIDER_PRESET_NAME_ORDER =
            new Comparator<SliderPresetSnapshot>() {
                @Override
                public int compare(SliderPresetSnapshot left, SliderPresetSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    /** Canonical Custom Morph Target order: name, without regard to case. */
    static final Comparator<CustomMorphTargetSnapshot> CUSTOM_MORPH_TARGET_NAME_ORDER =
            new Comparator<CustomMorphTargetSnapshot>() {
                @Override
                public int compare(CustomMorphTargetSnapshot left, CustomMorphTargetSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    /** Canonical NPC Morph Assignment order: plugin name, then editor ID, without regard to case. */
    static final Comparator<NpcMorphAssignmentSnapshot> NPC_MORPH_ASSIGNMENT_IDENTITY_ORDER =
            new Comparator<NpcMorphAssignmentSnapshot>() {
                @Override
                public int compare(NpcMorphAssignmentSnapshot left, NpcMorphAssignmentSnapshot right) {
                    int pluginOrder = left.getPluginName().compareToIgnoreCase(right.getPluginName());
                    return pluginOrder != 0 ? pluginOrder
                            : left.getEditorId().compareToIgnoreCase(right.getEditorId());
                }
            };
    /**
     * Canonical order of the Slider Preset names a Custom Morph Target or NPC
     * Morph Assignment carries.
     */
    static final Comparator<String> ASSIGNMENT_NAME_ORDER = String.CASE_INSENSITIVE_ORDER;

    /** Stable edit-request location for Slider Preset name rule violations. */
    private static final SourceLocation SLIDER_PRESET_NAME_LOCATION = new SourceLocation(Optional.<Path>empty(),
            Optional.of("slider-preset.name"), OptionalInt.empty(), OptionalInt.empty());

    private static final Referrer<CustomMorphTargetSnapshot> CUSTOM_MORPH_TARGET_REFERRER = new Referrer<>(
            CustomMorphTargetSnapshot::getSliderPresetNames,
            (target, names) -> new CustomMorphTargetSnapshot(target.getName(), names));
    private static final Referrer<NpcMorphAssignmentSnapshot> NPC_MORPH_ASSIGNMENT_REFERRER = new Referrer<>(
            NpcMorphAssignmentSnapshot::getSliderPresetNames,
            (npc, names) -> new NpcMorphAssignmentSnapshot(npc.getDisplayName(), npc.getPluginName(),
                    npc.getEditorId(), npc.getRace(), npc.getFormId(), names));

    private final List<SliderPresetSnapshot> sliderPresets;
    private final List<CustomMorphTargetSnapshot> customMorphTargets;
    private final List<NpcMorphAssignmentSnapshot> npcMorphAssignments;

    /**
     * Adopts already-canonical, already-unmodifiable collections. Only reached
     * from {@link #from(ProjectSnapshot)} and from operations on a valid aggregate,
     * so no validation or copying happens here.
     */
    private Project(List<SliderPresetSnapshot> sliderPresets, List<CustomMorphTargetSnapshot> customMorphTargets,
            List<NpcMorphAssignmentSnapshot> npcMorphAssignments) {
        this.sliderPresets = sliderPresets;
        this.customMorphTargets = customMorphTargets;
        this.npcMorphAssignments = npcMorphAssignments;
    }

    /**
     * Builds an aggregate from a snapshot's content, re-sorting each collection
     * into canonical order and re-validating the Project invariants. Lifecycle
     * metadata on the snapshot is ignored.
     *
     * @param snapshot source content
     * @return an aggregate over the snapshot's content
     * @throws NullPointerException when snapshot is null
     * @throws IllegalArgumentException when a Slider Preset or Custom Morph Target
     *         name or an NPC Morph Assignment identity repeats without regard to
     *         case, or when a referrer names a Slider Preset the catalog lacks
     */
    static Project from(ProjectSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<SliderPresetSnapshot> presets = sorted(snapshot.getSliderPresets(), SLIDER_PRESET_NAME_ORDER);
        List<CustomMorphTargetSnapshot> targets = sorted(snapshot.getCustomMorphTargets(),
                CUSTOM_MORPH_TARGET_NAME_ORDER);
        List<NpcMorphAssignmentSnapshot> npcs = sorted(snapshot.getNpcMorphAssignments(),
                NPC_MORPH_ASSIGNMENT_IDENTITY_ORDER);

        Set<String> presetNames = new TreeSet<>(ASSIGNMENT_NAME_ORDER);
        for (SliderPresetSnapshot preset : presets) {
            if (!presetNames.add(preset.getName()))
                throw new IllegalArgumentException("Slider Preset names must be unique: " + preset.getName());
        }
        Set<String> targetNames = new TreeSet<>(ASSIGNMENT_NAME_ORDER);
        for (CustomMorphTargetSnapshot target : targets) {
            if (!targetNames.add(target.getName()))
                throw new IllegalArgumentException("Custom Morph Target names must be unique: " + target.getName());
            requireKnownPresets(presetNames, target.getSliderPresetNames(), "Custom Morph Target " + target.getName());
        }
        Set<NpcMorphAssignmentIdentity> identities = new HashSet<>();
        for (NpcMorphAssignmentSnapshot npc : npcs) {
            if (!identities.add(new NpcMorphAssignmentIdentity(npc.getPluginName(), npc.getEditorId())))
                throw new IllegalArgumentException("NPC Morph Assignment identities must be unique: "
                        + npc.getPluginName() + "/" + npc.getEditorId());
            requireKnownPresets(presetNames, npc.getSliderPresetNames(),
                    "NPC Morph Assignment " + npc.getPluginName() + "/" + npc.getEditorId());
        }
        return new Project(presets, targets, npcs);
    }

    /**
     * Produces the session-facing snapshot from this content plus the lifecycle
     * state only the session knows.
     *
     * @param fileIdentity adopted Project path, or empty for an untitled Project
     * @param dirty whether content has unsaved changes
     * @param lifecycleStatus stable lifecycle classification
     * @return a snapshot over this content
     * @throws IllegalArgumentException when the lifecycle arguments contradict
     *         each other or this content (see {@link ProjectSnapshot})
     */
    ProjectSnapshot toSnapshot(Optional<Path> fileIdentity, boolean dirty, ProjectLifecycleStatus lifecycleStatus) {
        return new ProjectSnapshot(sliderPresets, customMorphTargets, npcMorphAssignments, fileIdentity, dirty,
                lifecycleStatus);
    }

    /** @return Slider Presets in canonical order, as an unmodifiable list */
    List<SliderPresetSnapshot> getSliderPresets() {
        return sliderPresets;
    }

    /** @return Custom Morph Targets in canonical order, as an unmodifiable list */
    List<CustomMorphTargetSnapshot> getCustomMorphTargets() {
        return customMorphTargets;
    }

    /** @return NPC Morph Assignments in canonical order, as an unmodifiable list */
    List<NpcMorphAssignmentSnapshot> getNpcMorphAssignments() {
        return npcMorphAssignments;
    }

    /**
     * Finds a Slider Preset by its case-insensitive logical name.
     *
     * @param name requested name, optionally surrounded by whitespace; null matches nothing
     * @return the catalog value, or empty when no logical Slider Preset matches
     */
    Optional<SliderPresetSnapshot> findSliderPreset(String name) {
        int index = indexOfSliderPreset(name);
        return index < 0 ? Optional.<SliderPresetSnapshot>empty() : Optional.of(sliderPresets.get(index));
    }

    /**
     * Finds a Custom Morph Target by its case-insensitive logical name.
     *
     * @param name requested name, optionally surrounded by whitespace; null matches nothing
     * @return the value, or empty when no logical Custom Morph Target matches
     */
    Optional<CustomMorphTargetSnapshot> findCustomMorphTarget(String name) {
        if (name == null)
            return Optional.empty();
        String normalizedName = name.trim();
        for (CustomMorphTargetSnapshot target : customMorphTargets) {
            if (target.getName().equalsIgnoreCase(normalizedName))
                return Optional.of(target);
        }
        return Optional.empty();
    }

    /**
     * Finds an NPC Morph Assignment by its complete case-insensitive identity.
     *
     * @param identity requested plugin-name/editor-ID identity; null matches nothing
     * @return the value, or empty when no assignment has that identity
     */
    Optional<NpcMorphAssignmentSnapshot> findNpcMorphAssignment(NpcMorphAssignmentIdentity identity) {
        if (identity == null)
            return Optional.empty();
        for (NpcMorphAssignmentSnapshot npc : npcMorphAssignments) {
            if (identity.equals(new NpcMorphAssignmentIdentity(npc.getPluginName(), npc.getEditorId())))
                return Optional.of(npc);
        }
        return Optional.empty();
    }

    /**
     * Adds a Slider Preset to the catalog. The name is stored exactly as carried by
     * the value, so trimming and the non-empty / no-dot rules are the caller's;
     * the duplicate check ignores surrounding whitespace like every lookup does.
     *
     * @param preset complete value to add
     * @return the next aggregate, or a duplicate-name diagnostic when another
     *         Slider Preset already has this name without regard to case
     */
    Result addSliderPreset(SliderPresetSnapshot preset) {
        Objects.requireNonNull(preset, "preset");
        if (indexOfSliderPreset(preset.getName()) >= 0)
            return Result.rejected(duplicateSliderPresetName());
        List<SliderPresetSnapshot> presets = new ArrayList<>(sliderPresets);
        presets.add(preset);
        return Result.of(new Project(sorted(presets, SLIDER_PRESET_NAME_ORDER), customMorphTargets,
                npcMorphAssignments));
    }

    /**
     * Replaces one Slider Preset's complete value under its current display name.
     * The caller has already validated slider choices and rebuilt UUNP defaults;
     * this only swaps the value. A display-name change is a {@link
     * #renameSliderPreset(String, String) rename}, not a replace.
     *
     * @param name existing logical Slider Preset name
     * @param replacement value carrying exactly the current display name
     * @return the next aggregate, or {@code this} when every observable value is equal
     * @throws IllegalArgumentException when no Slider Preset has that name or the
     *         replacement carries a different display name
     */
    Project replaceSliderPreset(String name, SliderPresetSnapshot replacement) {
        Objects.requireNonNull(replacement, "replacement");
        int index = requireSliderPreset(name);
        SliderPresetSnapshot current = sliderPresets.get(index);
        if (!current.getName().equals(replacement.getName()))
            throw new IllegalArgumentException("Replacing a Slider Preset must keep its display name; rename instead: "
                    + current.getName() + " -> " + replacement.getName());
        if (sameSliderPreset(current, replacement))
            return this;
        List<SliderPresetSnapshot> presets = new ArrayList<>(sliderPresets);
        presets.set(index, replacement);
        // Same display name means the same sort position, so no re-sort is needed.
        return new Project(Collections.unmodifiableList(presets), customMorphTargets, npcMorphAssignments);
    }

    /**
     * Adds a Slider Preset, or replaces the UUNP flag and slider choices of the one
     * that already has its name. On replace the existing display name's casing is
     * kept, which is what BodySlide XML import relies on; referrers therefore never
     * need rewriting.
     *
     * @param candidate imported value
     * @return the next aggregate, or {@code this} when nothing observable changed
     */
    Project upsertSliderPreset(SliderPresetSnapshot candidate) {
        Objects.requireNonNull(candidate, "candidate");
        int index = indexOfSliderPreset(candidate.getName());
        if (index < 0)
            return addSliderPreset(candidate).getProject();
        SliderPresetSnapshot current = sliderPresets.get(index);
        return replaceSliderPreset(current.getName(),
                new SliderPresetSnapshot(current.getName(), candidate.isUunp(), candidate.getSliderChoices()));
    }

    /**
     * Changes a Slider Preset's display name, including case-only changes, and
     * rewrites every Custom Morph Target and NPC Morph Assignment reference to it.
     * The new name is stored exactly as supplied, so trimming and the non-empty /
     * no-dot rules are the caller's; the duplicate check ignores surrounding
     * whitespace like every lookup does.
     *
     * @param currentName existing logical Slider Preset name
     * @param newName requested display name
     * @return the next aggregate, {@code this} when the display name is already
     *         exactly {@code newName}, or a duplicate-name diagnostic when a
     *         different Slider Preset already has that name without regard to case
     * @throws IllegalArgumentException when no Slider Preset has the current name
     */
    Result renameSliderPreset(String currentName, String newName) {
        Objects.requireNonNull(newName, "newName");
        int index = requireSliderPreset(currentName);
        SliderPresetSnapshot current = sliderPresets.get(index);
        if (current.getName().equals(newName))
            return Result.of(this);
        int clash = indexOfSliderPreset(newName);
        if (clash >= 0 && clash != index)
            return Result.rejected(duplicateSliderPresetName());
        List<SliderPresetSnapshot> presets = new ArrayList<>(sliderPresets);
        presets.set(index, new SliderPresetSnapshot(newName, current.isUunp(), current.getSliderChoices()));
        return Result.of(cascade(sorted(presets, SLIDER_PRESET_NAME_ORDER),
                renamedNames(current.getName(), newName)));
    }

    /**
     * Removes a Slider Preset and every reference to it from Custom Morph Targets
     * and NPC Morph Assignments, without reporting the removed references.
     *
     * @param name existing logical Slider Preset name
     * @return the next aggregate
     * @throws IllegalArgumentException when no Slider Preset has that name
     */
    Project removeSliderPreset(String name) {
        int index = requireSliderPreset(name);
        String removedName = sliderPresets.get(index).getName();
        List<SliderPresetSnapshot> presets = new ArrayList<>(sliderPresets);
        presets.remove(index);
        // Removing an element keeps the remaining order, so no re-sort is needed.
        return cascade(Collections.unmodifiableList(presets), withoutName(removedName));
    }

    /**
     * Removes every Slider Preset and clears every Custom Morph Target and NPC
     * Morph Assignment reference.
     *
     * @return the next aggregate, or {@code this} when the catalog is already empty
     */
    Project clearSliderPresets() {
        if (sliderPresets.isEmpty())
            return this;
        return cascade(Collections.<SliderPresetSnapshot>emptyList(), clearedNames());
    }

    /**
     * Builds the next aggregate from an already-canonical catalog and one
     * name-list rewrite applied to every referrer of both kinds. Referrer
     * collections are only copied when at least one of their values changes;
     * their order is by their own name or identity, which a rewrite never touches.
     */
    private Project cascade(List<SliderPresetSnapshot> presets, Function<List<String>, List<String>> rewriteNames) {
        return new Project(presets, CUSTOM_MORPH_TARGET_REFERRER.rewrite(customMorphTargets, rewriteNames),
                NPC_MORPH_ASSIGNMENT_REFERRER.rewrite(npcMorphAssignments, rewriteNames));
    }

    /**
     * Name-list rewrite for a rename: every case-insensitive match of the previous
     * name becomes the new name and the list is re-sorted, because the new name may
     * belong at a different position.
     */
    private static Function<List<String>, List<String>> renamedNames(String previousName, String changedName) {
        return names -> {
            if (!containsIgnoreCase(names, previousName))
                return names;
            List<String> rewritten = new ArrayList<>(names.size());
            for (String name : names)
                rewritten.add(name.equalsIgnoreCase(previousName) ? changedName : name);
            return sorted(rewritten, ASSIGNMENT_NAME_ORDER);
        };
    }

    /** Name-list rewrite for a removal: every case-insensitive match is dropped, order preserved. */
    private static Function<List<String>, List<String>> withoutName(String removedName) {
        return names -> {
            if (!containsIgnoreCase(names, removedName))
                return names;
            List<String> rewritten = new ArrayList<>(names.size());
            for (String name : names) {
                if (!name.equalsIgnoreCase(removedName))
                    rewritten.add(name);
            }
            return Collections.unmodifiableList(rewritten);
        };
    }

    /** Reports whether a name list holds the requested name without regard to case. */
    private static boolean containsIgnoreCase(List<String> names, String requestedName) {
        for (String name : names) {
            if (name.equalsIgnoreCase(requestedName))
                return true;
        }
        return false;
    }

    /** Name-list rewrite for a catalog clear: a non-empty list becomes empty. */
    private static Function<List<String>, List<String>> clearedNames() {
        return names -> names.isEmpty() ? names : Collections.<String>emptyList();
    }

    /**
     * Locates a Slider Preset by case-insensitive logical name after trimming.
     *
     * @return the catalog index, or -1 when absent or when name is null
     */
    private int indexOfSliderPreset(String name) {
        if (name == null)
            return -1;
        String normalizedName = name.trim();
        for (int index = 0; index < sliderPresets.size(); index++) {
            if (sliderPresets.get(index).getName().equalsIgnoreCase(normalizedName))
                return index;
        }
        return -1;
    }

    /**
     * Locates a Slider Preset the caller has already looked up.
     *
     * @throws IllegalArgumentException when no Slider Preset has that name
     */
    private int requireSliderPreset(String name) {
        int index = indexOfSliderPreset(name);
        if (index < 0)
            throw new IllegalArgumentException("No Slider Preset is named: " + name);
        return index;
    }

    /** Builds the duplicate-name diagnostic shared by add and rename. */
    private static ProjectDiagnostic duplicateSliderPresetName() {
        return new ProjectDiagnostic(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE, DiagnosticSeverity.ERROR,
                SLIDER_PRESET_NAME_LOCATION, "A Slider Preset with this name already exists.");
    }

    /**
     * Rejects a referrer naming a Slider Preset the catalog lacks.
     *
     * @throws IllegalArgumentException on the first unknown name
     */
    private static void requireKnownPresets(Set<String> presetNames, List<String> referencedNames, String owner) {
        for (String referencedName : referencedNames) {
            if (!presetNames.contains(referencedName))
                throw new IllegalArgumentException(owner + " references a Slider Preset the Project does not contain: "
                        + referencedName);
        }
    }

    /** Copies and sorts into an unmodifiable list; the input is never mutated. */
    private static <T> List<T> sorted(List<T> values, Comparator<? super T> order) {
        List<T> copy = new ArrayList<>(values);
        Collections.sort(copy, order);
        return Collections.unmodifiableList(copy);
    }

    /**
     * Compares complete Slider Preset values so only a real content change makes a
     * new aggregate (and, downstream, dirties the Project). Choice order matters:
     * callers canonicalize choices before replacing.
     */
    private static boolean sameSliderPreset(SliderPresetSnapshot left, SliderPresetSnapshot right) {
        if (!left.getName().equals(right.getName()) || left.isUunp() != right.isUunp()
                || left.getSliderChoices().size() != right.getSliderChoices().size())
            return false;
        for (int index = 0; index < left.getSliderChoices().size(); index++) {
            if (!sameSliderChoice(left.getSliderChoices().get(index), right.getSliderChoices().get(index)))
                return false;
        }
        return true;
    }

    /** Compares every observable slider-choice value, including synthesized-default identity. */
    private static boolean sameSliderChoice(SliderChoiceSnapshot left, SliderChoiceSnapshot right) {
        return left.getName().equals(right.getName()) && left.isEnabled() == right.isEnabled()
                && left.getStoredSmallValue().equals(right.getStoredSmallValue())
                && left.getStoredBigValue().equals(right.getStoredBigValue())
                && left.getEffectiveSmallValue() == right.getEffectiveSmallValue()
                && left.getEffectiveBigValue() == right.getEffectiveBigValue()
                && left.getPercentageMinimum() == right.getPercentageMinimum()
                && left.getPercentageMaximum() == right.getPercentageMaximum()
                && left.isMissingDefault() == right.isMissingDefault();
    }

    /**
     * Two-state result of an operation that can violate a Project rule: either the
     * next aggregate (possibly the same instance) or exactly one diagnostic.
     */
    static final class Result {
        private final Project project;
        private final ProjectDiagnostic diagnostic;

        private Result(Project project, ProjectDiagnostic diagnostic) {
            this.project = project;
            this.diagnostic = diagnostic;
        }

        /** Wraps the next aggregate, which may be the same instance on a no-op. */
        static Result of(Project project) {
            return new Result(Objects.requireNonNull(project, "project"), null);
        }

        /** Wraps the single diagnostic explaining why the operation was refused. */
        static Result rejected(ProjectDiagnostic diagnostic) {
            return new Result(null, Objects.requireNonNull(diagnostic, "diagnostic"));
        }

        /** @return true when the operation was refused and {@link #getDiagnostic()} applies */
        boolean isRejected() {
            return diagnostic != null;
        }

        /**
         * @return the next aggregate
         * @throws IllegalStateException when the operation was rejected
         */
        Project getProject() {
            if (project == null)
                throw new IllegalStateException("A rejected result carries no Project: " + diagnostic.getMessage());
            return project;
        }

        /**
         * @return the rejection diagnostic
         * @throws IllegalStateException when the operation succeeded
         */
        ProjectDiagnostic getDiagnostic() {
            if (diagnostic == null)
                throw new IllegalStateException("A successful result carries no diagnostic");
            return diagnostic;
        }
    }

    /**
     * One kind of value that references Slider Presets by name, described by how
     * to read its names and how to rebuild it with new names. Rename, remove, and
     * clear cascades are written once over this and applied to both kinds, so the
     * public snapshot types need no shared interface.
     */
    private static final class Referrer<T> {
        private final Function<T, List<String>> names;
        private final BiFunction<T, List<String>, T> withNames;

        private Referrer(Function<T, List<String>> names, BiFunction<T, List<String>, T> withNames) {
            this.names = names;
            this.withNames = withNames;
        }

        /**
         * Applies a name-list rewrite to every value. A rewrite signals "unchanged"
         * by returning the very list it was given, and this method does the same
         * for the whole collection, so an unaffected collection is never copied.
         *
         * @param values current canonical collection
         * @param rewriteNames pure rewrite of one value's names
         * @return the same list when no value changed, otherwise a new unmodifiable
         *         list with unaffected values carried over by reference
         */
        List<T> rewrite(List<T> values, Function<List<String>, List<String>> rewriteNames) {
            List<T> rewritten = null;
            for (int index = 0; index < values.size(); index++) {
                T value = values.get(index);
                List<String> currentNames = names.apply(value);
                List<String> changedNames = rewriteNames.apply(currentNames);
                if (changedNames == currentNames) {
                    if (rewritten != null)
                        rewritten.add(value);
                    continue;
                }
                if (rewritten == null)
                    rewritten = new ArrayList<>(values.subList(0, index));
                rewritten.add(withNames.apply(value, changedNames));
            }
            return rewritten == null ? values : Collections.unmodifiableList(rewritten);
        }
    }
}
