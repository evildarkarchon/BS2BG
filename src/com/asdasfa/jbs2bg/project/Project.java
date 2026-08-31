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
 * directly. Operations <em>on</em> a Slider Preset, Custom Morph Target, or NPC
 * Morph Assignment the Project lacks are a caller error (the session looks up
 * first) and throw rather than diagnose; a <em>reference</em> to a Slider Preset
 * the catalog lacks, supplied through {@link #assignSliderPreset}, is a modder
 * mistake and is diagnosed. Two operations over caller-filtered batches relax
 * the throw: {@link #fillEmptyNpcMorphAssignments}, whose choices reference NPCs
 * as well as Slider Presets, diagnoses both so its diagnostics stay in choice
 * order; and {@link #removeNpcMorphAssignments}, which treats its selection as a
 * set to subtract and ignores an identity that names nothing.
 *
 * <p>Relationship operations (assign, unassign, clear) are written once over a
 * {@link ReferrerKey}, which names either referencing kind, so the two kinds
 * cannot drift apart. NPC Morph Assignment promotion and fill-empty build on
 * them rather than resolving Slider Preset names a second time.
 */
final class Project {

    /**
     * Canonical Slider Preset order: display name, without regard to case.
     */
    static final Comparator<SliderPresetSnapshot> SLIDER_PRESET_NAME_ORDER =
            new Comparator<SliderPresetSnapshot>() {
                @Override
                public int compare(SliderPresetSnapshot left, SliderPresetSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    /**
     * Canonical Custom Morph Target order: name, without regard to case.
     */
    static final Comparator<CustomMorphTargetSnapshot> CUSTOM_MORPH_TARGET_NAME_ORDER =
            new Comparator<CustomMorphTargetSnapshot>() {
                @Override
                public int compare(CustomMorphTargetSnapshot left, CustomMorphTargetSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    /**
     * Canonical NPC Morph Assignment order: plugin name, then editor ID, without regard to case.
     */
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

    /**
     * Stable edit-request location for Slider Preset name rule violations.
     */
    private static final SourceLocation SLIDER_PRESET_NAME_LOCATION = new SourceLocation(Optional.<Path>empty(),
            Optional.of("slider-preset.name"), OptionalInt.empty(), OptionalInt.empty());
    /**
     * Stable edit-request location for Custom Morph Target name rule violations.
     */
    private static final SourceLocation CUSTOM_MORPH_TARGET_NAME_LOCATION = new SourceLocation(
            Optional.<Path>empty(), Optional.of("custom-morph-target.name"), OptionalInt.empty(),
            OptionalInt.empty());
    /**
     * Stable edit-request location for NPC Morph Assignment identity rule violations.
     */
    private static final SourceLocation NPC_MORPH_ASSIGNMENT_IDENTITY_LOCATION = new SourceLocation(
            Optional.<Path>empty(), Optional.of("npc-morph-assignment.identity"), OptionalInt.empty(),
            OptionalInt.empty());

    private static final Referrer<CustomMorphTargetSnapshot> CUSTOM_MORPH_TARGET_REFERRER = new Referrer<>(
            CustomMorphTargetSnapshot::getSliderPresetNames,
            (target, names) -> new CustomMorphTargetSnapshot(target.getName(), names));
    private static final Referrer<NpcMorphAssignmentSnapshot> NPC_MORPH_ASSIGNMENT_REFERRER = new Referrer<>(
            NpcMorphAssignmentSnapshot::getSliderPresetNames,
            (npc, names) -> new NpcMorphAssignmentSnapshot(npc.getDisplayName(), npc.getPluginName(),
                    npc.getEditorId(), npc.getRace(), npc.getFormId(), names));

    /**
     * The one canonical empty aggregate. New Project publishes this instance, so
     * the session can tell a repeated New Project (same instance, Unchanged) from
     * a Project that edits merely emptied again (a different instance, dirty).
     */
    private static final Project EMPTY = new Project(Collections.<SliderPresetSnapshot>emptyList(),
            Collections.<CustomMorphTargetSnapshot>emptyList(), Collections.<NpcMorphAssignmentSnapshot>emptyList());

    private final List<SliderPresetSnapshot> sliderPresets;
    private final List<CustomMorphTargetSnapshot> customMorphTargets;
    private final List<NpcMorphAssignmentSnapshot> npcMorphAssignments;

    /**
     * Adopts already-canonical, already-unmodifiable collections. Only reached
     * from {@link #from(ProjectSnapshot)}, {@link #empty()}, and operations on a
     * valid aggregate, so no validation or copying happens here.
     */
    private Project(List<SliderPresetSnapshot> sliderPresets, List<CustomMorphTargetSnapshot> customMorphTargets,
                    List<NpcMorphAssignmentSnapshot> npcMorphAssignments) {
        this.sliderPresets = sliderPresets;
        this.customMorphTargets = customMorphTargets;
        this.npcMorphAssignments = npcMorphAssignments;
    }

    /**
     * Returns the canonical empty aggregate: no Slider Presets, Custom Morph
     * Targets, or NPC Morph Assignments. Always the same instance, so a caller
     * that publishes it can later recognise it by identity.
     *
     * @return the single empty aggregate
     */
    static Project empty() {
        return EMPTY;
    }

    /**
     * Builds an aggregate from a snapshot's content, re-sorting each collection
     * into canonical order and re-validating the Project invariants. Lifecycle
     * metadata on the snapshot is ignored.
     *
     * @param snapshot source content
     * @return an aggregate over the snapshot's content
     * @throws NullPointerException     when snapshot is null
     * @throws IllegalArgumentException when a Slider Preset or Custom Morph Target
     *                                  name or an NPC Morph Assignment identity repeats without regard to
     *                                  case, or when a referrer names a Slider Preset the catalog lacks
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
            if (!identities.add(identityOf(npc)))
                throw new IllegalArgumentException("NPC Morph Assignment identities must be unique: "
                        + npc.getPluginName() + "/" + npc.getEditorId());
            requireKnownPresets(presetNames, npc.getSliderPresetNames(),
                    "NPC Morph Assignment " + npc.getPluginName() + "/" + npc.getEditorId());
        }
        return new Project(presets, targets, npcs);
    }

    /**
     * Name-list rewrite for an assignment: a list already holding the name in any
     * casing is unchanged; otherwise the canonical name is added and the list is
     * re-sorted, because the new name may belong at any position.
     */
    private static Function<List<String>, List<String>> withName(String canonicalName) {
        return names -> {
            if (containsIgnoreCase(names, canonicalName))
                return names;
            List<String> rewritten = new ArrayList<>(names.size() + 1);
            rewritten.addAll(names);
            rewritten.add(canonicalName);
            return sorted(rewritten, ASSIGNMENT_NAME_ORDER);
        };
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

    /**
     * Name-list rewrite for a removal: every case-insensitive match is dropped, order preserved.
     */
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

    /**
     * Reports whether a name list holds the requested name without regard to case.
     */
    private static boolean containsIgnoreCase(List<String> names, String requestedName) {
        for (String name : names) {
            if (name.equalsIgnoreCase(requestedName))
                return true;
        }
        return false;
    }

    /**
     * Name-list rewrite for a catalog clear: a non-empty list becomes empty.
     */
    private static Function<List<String>, List<String>> clearedNames() {
        return names -> names.isEmpty() ? names : Collections.<String>emptyList();
    }

    /**
     * Extracts the case-insensitive plugin-name/editor-ID identity an NPC Morph Assignment carries.
     */
    private static NpcMorphAssignmentIdentity identityOf(NpcMorphAssignmentSnapshot npc) {
        return new NpcMorphAssignmentIdentity(npc.getPluginName(), npc.getEditorId());
    }

    /**
     * Builds the duplicate-name diagnostic shared by add and rename.
     */
    private static ProjectDiagnostic duplicateSliderPresetName() {
        return new ProjectDiagnostic(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE, DiagnosticSeverity.ERROR,
                SLIDER_PRESET_NAME_LOCATION, "A Slider Preset with this name already exists.");
    }

    /**
     * Builds the duplicate-name diagnostic for adding a Custom Morph Target.
     */
    private static ProjectDiagnostic duplicateCustomMorphTargetName() {
        return new ProjectDiagnostic(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_DUPLICATE,
                DiagnosticSeverity.ERROR, CUSTOM_MORPH_TARGET_NAME_LOCATION,
                "A Custom Morph Target with this name already exists.");
    }

    /**
     * Builds a duplicate-identity diagnostic at the NPC identity location. The
     * code and location are the same for every way an identity can repeat; only
     * the message differs, because the modder needs to know whether the Project
     * already held the NPC, the promotion batch repeated it, or a fill decided it
     * twice.
     */
    private static ProjectDiagnostic duplicateNpcMorphAssignmentIdentity(String message) {
        return new ProjectDiagnostic(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE, DiagnosticSeverity.ERROR,
                NPC_MORPH_ASSIGNMENT_IDENTITY_LOCATION, message);
    }

    /**
     * Builds the diagnostic for a fill-empty choice naming an NPC Morph Assignment
     * the Project lacks. It is the same code, location, and message the session
     * reports when an edit names a missing NPC directly, so callers see one
     * vocabulary.
     */
    private static ProjectDiagnostic npcMorphAssignmentNotFound() {
        return new ProjectDiagnostic(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_NOT_FOUND, DiagnosticSeverity.ERROR,
                NPC_MORPH_ASSIGNMENT_IDENTITY_LOCATION, "The requested NPC Morph Assignment does not exist.");
    }

    /**
     * Builds the diagnostic for assigning a Slider Preset the catalog lacks. It is
     * the same code, location, and message the session reports when an edit names
     * a missing Slider Preset directly, so callers see one vocabulary.
     */
    private static ProjectDiagnostic sliderPresetNotFound() {
        return new ProjectDiagnostic(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, DiagnosticSeverity.ERROR,
                SLIDER_PRESET_NAME_LOCATION, "The requested Slider Preset does not exist.");
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

    /**
     * Copies and sorts into an unmodifiable list; the input is never mutated.
     */
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

    /**
     * Compares every observable slider-choice value, including synthesized-default identity.
     */
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
     * Produces the session-facing snapshot from this content plus the lifecycle
     * state only the session knows.
     *
     * @param fileIdentity    adopted Project path, or empty for an untitled Project
     * @param dirty           whether content has unsaved changes
     * @param lifecycleStatus stable lifecycle classification
     * @return a snapshot over this content
     * @throws IllegalArgumentException when the lifecycle arguments contradict
     *                                  each other or this content (see {@link ProjectSnapshot})
     */
    ProjectSnapshot toSnapshot(Optional<Path> fileIdentity, boolean dirty, ProjectLifecycleStatus lifecycleStatus) {
        return toSnapshot(fileIdentity, dirty, lifecycleStatus, ProjectContentVersion.detached());
    }

    /**
     * Produces a session-owned snapshot carrying the supplied opaque content version.
     */
    ProjectSnapshot toSnapshot(Optional<Path> fileIdentity, boolean dirty, ProjectLifecycleStatus lifecycleStatus,
                               ProjectContentVersion contentVersion) {
        return new ProjectSnapshot(sliderPresets, customMorphTargets, npcMorphAssignments, fileIdentity, dirty,
                lifecycleStatus, contentVersion);
    }

    /**
     * @return Slider Presets in canonical order, as an unmodifiable list
     */
    List<SliderPresetSnapshot> getSliderPresets() {
        return sliderPresets;
    }

    /**
     * @return Custom Morph Targets in canonical order, as an unmodifiable list
     */
    List<CustomMorphTargetSnapshot> getCustomMorphTargets() {
        return customMorphTargets;
    }

    /**
     * @return NPC Morph Assignments in canonical order, as an unmodifiable list
     */
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
        int index = indexOfCustomMorphTarget(name);
        return index < 0 ? Optional.<CustomMorphTargetSnapshot>empty() : Optional.of(customMorphTargets.get(index));
    }

    /**
     * Finds an NPC Morph Assignment by its complete case-insensitive identity.
     *
     * @param identity requested plugin-name/editor-ID identity; null matches nothing
     * @return the value, or empty when no assignment has that identity
     */
    Optional<NpcMorphAssignmentSnapshot> findNpcMorphAssignment(NpcMorphAssignmentIdentity identity) {
        int index = indexOfNpcMorphAssignment(identity);
        return index < 0 ? Optional.<NpcMorphAssignmentSnapshot>empty() : Optional.of(npcMorphAssignments.get(index));
    }

    /**
     * Adds a Slider Preset to the catalog. The name is stored exactly as carried by
     * the value, so trimming and the non-empty / no-dot rules are the caller's;
     * the duplicate check ignores surrounding whitespace like every lookup does.
     *
     * @param preset complete value to add
     * @return the next aggregate, or a duplicate-name diagnostic when another
     * Slider Preset already has this name without regard to case
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
     * @param name        existing logical Slider Preset name
     * @param replacement value carrying exactly the current display name
     * @return the next aggregate, or {@code this} when every observable value is equal
     * @throws IllegalArgumentException when no Slider Preset has that name or the
     *                                  replacement carries a different display name
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
     * @param newName     requested display name
     * @return the next aggregate, {@code this} when the display name is already
     * exactly {@code newName}, or a duplicate-name diagnostic when a
     * different Slider Preset already has that name without regard to case
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
     * Adds a Custom Morph Target. The name is stored exactly as carried by the
     * value, so trimming and the non-empty rule are the caller's; the duplicate
     * check ignores surrounding whitespace like every lookup does. The value's
     * Slider Preset names are adopted as given, so a caller that wants them
     * resolved to catalog casing adds an empty target and then {@link
     * #assignSliderPresets assigns}.
     *
     * @param target complete value to add
     * @return the next aggregate, or a duplicate-name diagnostic when another
     * Custom Morph Target already has this name without regard to case
     * @throws IllegalArgumentException when the value names a Slider Preset the
     *                                  catalog lacks
     */
    Result addCustomMorphTarget(CustomMorphTargetSnapshot target) {
        Objects.requireNonNull(target, "target");
        if (indexOfCustomMorphTarget(target.getName()) >= 0)
            return Result.rejected(duplicateCustomMorphTargetName());
        Set<String> presetNames = new TreeSet<>(ASSIGNMENT_NAME_ORDER);
        for (SliderPresetSnapshot preset : sliderPresets)
            presetNames.add(preset.getName());
        requireKnownPresets(presetNames, target.getSliderPresetNames(), "Custom Morph Target " + target.getName());
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(customMorphTargets);
        targets.add(target);
        return Result.of(new Project(sliderPresets, sorted(targets, CUSTOM_MORPH_TARGET_NAME_ORDER),
                npcMorphAssignments));
    }

    /**
     * Removes a Custom Morph Target. Nothing references a Custom Morph Target, so
     * there is no cascade.
     *
     * @param name existing logical Custom Morph Target name
     * @return the next aggregate
     * @throws IllegalArgumentException when no Custom Morph Target has that name
     */
    Project removeCustomMorphTarget(String name) {
        int index = requireCustomMorphTarget(name);
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(customMorphTargets);
        targets.remove(index);
        // Removing an element keeps the remaining order, so no re-sort is needed.
        return new Project(sliderPresets, Collections.unmodifiableList(targets), npcMorphAssignments);
    }

    /**
     * Removes every Custom Morph Target.
     *
     * @return the next aggregate, or {@code this} when there are none
     */
    Project clearCustomMorphTargets() {
        if (customMorphTargets.isEmpty())
            return this;
        return new Project(sliderPresets, Collections.<CustomMorphTargetSnapshot>emptyList(), npcMorphAssignments);
    }

    /**
     * Promotes one NPC Morph Assignment from copied source values. The stored value
     * is an independent copy of the source. Its Slider Preset names are caller
     * choices, so unlike {@link #addCustomMorphTarget} they are resolved through
     * {@link #assignSliderPresets}: each is stored in the catalog's casing, repeats
     * collapse, and an unknown name is diagnosed rather than thrown. The duplicate
     * identity rule is checked first, so that diagnostic wins over a missing preset.
     *
     * @param source complete source value to copy
     * @return the next aggregate, a duplicate-identity diagnostic when an NPC Morph
     * Assignment already has this identity, or the first not-found
     * diagnostic for a Slider Preset the catalog lacks
     */
    Result addNpcMorphAssignment(NpcMorphAssignmentSnapshot source) {
        Objects.requireNonNull(source, "source");
        NpcMorphAssignmentIdentity identity = identityOf(source);
        if (indexOfNpcMorphAssignment(identity) >= 0)
            return Result.rejected(duplicateNpcMorphAssignmentIdentity(
                    "An NPC Morph Assignment with this plugin name and editor ID already exists."));
        List<NpcMorphAssignmentSnapshot> npcs = new ArrayList<>(npcMorphAssignments);
        npcs.add(NPC_MORPH_ASSIGNMENT_REFERRER.copyWithNames(source, Collections.<String>emptyList()));
        Project added = new Project(sliderPresets, customMorphTargets,
                sorted(npcs, NPC_MORPH_ASSIGNMENT_IDENTITY_ORDER));
        return added.assignSliderPresets(ReferrerKey.npcMorphAssignment(identity), source.getSliderPresetNames());
    }

    /**
     * Promotes several NPC Morph Assignments as one fold over {@link
     * #addNpcMorphAssignment}. A source whose identity the Project already holds
     * is skipped, because a filtered selection may include NPCs promoted earlier;
     * an identity repeated within the batch, or a member naming a Slider Preset
     * the catalog lacks, rejects the whole batch with that one diagnostic and
     * nothing is applied. The batch is a no-op exactly when nothing new was added.
     *
     * @param sources caller-filtered source values, in any order
     * @return the next aggregate, {@code this} when every member was already
     * present, or the first diagnostic
     */
    Result addNpcMorphAssignments(List<NpcMorphAssignmentSnapshot> sources) {
        Objects.requireNonNull(sources, "sources");
        Project next = this;
        for (NpcMorphAssignmentSnapshot source : sources) {
            NpcMorphAssignmentIdentity identity = identityOf(source);
            if (indexOfNpcMorphAssignment(identity) >= 0)
                continue;
            // Absent from this aggregate but present in the fold so far means the
            // batch itself repeats the identity, which has its own message.
            if (next.indexOfNpcMorphAssignment(identity) >= 0)
                return Result.rejected(duplicateNpcMorphAssignmentIdentity(
                        "A filtered NPC batch contains duplicate plugin-name/editor-ID identity."));
            Result step = next.addNpcMorphAssignment(source);
            if (step.isRejected())
                return step;
            next = step.getProject();
        }
        return Result.of(next);
    }

    /**
     * Removes one NPC Morph Assignment. Nothing references an NPC Morph Assignment,
     * so there is no cascade.
     *
     * @param identity existing identity
     * @return the next aggregate
     * @throws IllegalArgumentException when no NPC Morph Assignment has that identity
     */
    Project removeNpcMorphAssignment(NpcMorphAssignmentIdentity identity) {
        int index = requireNpcMorphAssignment(identity);
        List<NpcMorphAssignmentSnapshot> npcs = new ArrayList<>(npcMorphAssignments);
        npcs.remove(index);
        // Removing an element keeps the remaining order, so no re-sort is needed.
        return new Project(sliderPresets, customMorphTargets, Collections.unmodifiableList(npcs));
    }

    /**
     * Removes every NPC Morph Assignment whose identity is in a caller-filtered
     * selection. The selection is a set to subtract rather than a lookup, so an
     * identity that names nothing, or is repeated, is harmless; this is why, unlike
     * {@link #removeNpcMorphAssignment}, it does not throw.
     *
     * @param identities caller-selected identities
     * @return the next aggregate, or {@code this} when no identity matched
     */
    Project removeNpcMorphAssignments(List<NpcMorphAssignmentIdentity> identities) {
        Objects.requireNonNull(identities, "identities");
        Set<NpcMorphAssignmentIdentity> selected = new HashSet<>(identities);
        List<NpcMorphAssignmentSnapshot> remaining = new ArrayList<>(npcMorphAssignments.size());
        for (NpcMorphAssignmentSnapshot npc : npcMorphAssignments) {
            if (!selected.contains(identityOf(npc)))
                remaining.add(npc);
        }
        if (remaining.size() == npcMorphAssignments.size())
            return this;
        // Filtering keeps the remaining order, so no re-sort is needed.
        return new Project(sliderPresets, customMorphTargets, Collections.unmodifiableList(remaining));
    }

    /**
     * Removes every NPC Morph Assignment.
     *
     * @return the next aggregate, or {@code this} when there are none
     */
    Project clearNpcMorphAssignments() {
        if (npcMorphAssignments.isEmpty())
            return this;
        return new Project(sliderPresets, customMorphTargets, Collections.<NpcMorphAssignmentSnapshot>emptyList());
    }

    /**
     * Assigns one Slider Preset to each NPC Morph Assignment that has none, from
     * explicit caller decisions, as one fold over {@link #assignSliderPreset}. Every
     * choice is checked in order (repeated identity, then unknown NPC, then unknown
     * Slider Preset) and the first problem rejects the whole batch with that one
     * diagnostic; the Slider Preset of a choice for an occupied NPC is still
     * validated even though the NPC keeps its assignments. Unlike the single-NPC
     * operations, an unknown NPC here is diagnosed rather than thrown, because the
     * choices are a caller-owned batch of references and the session cannot look
     * them up without repeating this order.
     *
     * @param choices caller-owned identity/Slider Preset decisions
     * @return the next aggregate, {@code this} when every chosen NPC was occupied,
     * or the first diagnostic
     */
    Result fillEmptyNpcMorphAssignments(List<NpcSliderPresetChoice> choices) {
        Objects.requireNonNull(choices, "choices");
        Set<NpcMorphAssignmentIdentity> decided = new HashSet<>();
        Project next = this;
        for (NpcSliderPresetChoice choice : choices) {
            if (!decided.add(choice.getIdentity()))
                return Result.rejected(duplicateNpcMorphAssignmentIdentity(
                        "Fill-empty contains more than one choice for the same NPC identity."));
            int index = indexOfNpcMorphAssignment(choice.getIdentity());
            if (index < 0)
                return Result.rejected(npcMorphAssignmentNotFound());
            // Occupancy is read from this aggregate: a fill never empties an NPC,
            // and the duplicate rule above means no NPC is decided twice.
            if (!npcMorphAssignments.get(index).getSliderPresetNames().isEmpty()) {
                if (indexOfSliderPreset(choice.getSliderPresetName()) < 0)
                    return Result.rejected(sliderPresetNotFound());
                continue;
            }
            Result step = next.assignSliderPreset(ReferrerKey.npcMorphAssignment(choice.getIdentity()),
                    choice.getSliderPresetName());
            if (step.isRejected())
                return step;
            next = step.getProject();
        }
        return Result.of(next);
    }

    /**
     * Assigns one Slider Preset to a referrer. The requested name is resolved to
     * the catalog's display casing before it is stored, so a referrer never holds
     * a casing the catalog does not, and a name already assigned in any casing is
     * a no-op.
     *
     * @param key              referrer the caller has already looked up
     * @param sliderPresetName requested Slider Preset name, optionally surrounded
     *                         by whitespace; null resolves to nothing
     * @return the next aggregate, {@code this} when the name was already assigned,
     * or a not-found diagnostic when no Slider Preset has that name
     * @throws IllegalArgumentException when the key names no referrer
     */
    Result assignSliderPreset(ReferrerKey key, String sliderPresetName) {
        // Checked before the preset so a missing referrer always throws, even when
        // the preset is unknown too; the referrer is the caller's lookup, the
        // preset is the modder's choice.
        requireReferrer(key);
        int index = indexOfSliderPreset(sliderPresetName);
        if (index < 0)
            return Result.rejected(sliderPresetNotFound());
        return Result.of(rewriteReferrer(key, withName(sliderPresets.get(index).getName())));
    }

    /**
     * Assigns several Slider Presets to a referrer as one fold over {@link
     * #assignSliderPreset}: the first unknown name rejects the whole batch with
     * that one diagnostic and nothing is applied, and the batch is a no-op exactly
     * when every member was already assigned.
     *
     * @param key               referrer the caller has already looked up
     * @param sliderPresetNames requested Slider Preset names; duplicates within the
     *                          batch are harmless
     * @return the next aggregate, {@code this} when nothing new was assigned, or
     * the first not-found diagnostic
     * @throws IllegalArgumentException when the key names no referrer
     */
    Result assignSliderPresets(ReferrerKey key, List<String> sliderPresetNames) {
        Objects.requireNonNull(sliderPresetNames, "sliderPresetNames");
        // Checked up front so an empty batch still fails loudly on a referrer the
        // caller never looked up.
        requireReferrer(key);
        Project next = this;
        for (String sliderPresetName : sliderPresetNames) {
            Result step = next.assignSliderPreset(key, sliderPresetName);
            if (step.isRejected())
                return step;
            next = step.getProject();
        }
        return Result.of(next);
    }

    /**
     * Removes one Slider Preset assignment from a referrer without regard to case.
     * Unlike catalog removal there is no cascade and no rule to violate, so an
     * unknown or absent name is simply a no-op.
     *
     * @param key              referrer the caller has already looked up
     * @param sliderPresetName assigned name, optionally surrounded by whitespace;
     *                         null matches nothing
     * @return the next aggregate, or {@code this} when the name was not assigned
     * @throws IllegalArgumentException when the key names no referrer
     */
    Project unassignSliderPreset(ReferrerKey key, String sliderPresetName) {
        String normalizedName = sliderPresetName == null ? "" : sliderPresetName.trim();
        return rewriteReferrer(key, withoutName(normalizedName));
    }

    /**
     * Removes every Slider Preset assignment from a referrer.
     *
     * @param key referrer the caller has already looked up
     * @return the next aggregate, or {@code this} when it had no assignments
     * @throws IllegalArgumentException when the key names no referrer
     */
    Project clearSliderPresetAssignments(ReferrerKey key) {
        return rewriteReferrer(key, clearedNames());
    }

    /**
     * Removes every Slider Preset assignment from several referrers as one fold
     * over {@link #clearSliderPresetAssignments(ReferrerKey)}. A repeated key is
     * harmless; a key naming no referrer throws, and because the aggregate is
     * immutable nothing partial is observable when it does.
     *
     * @param keys referrers the caller has already looked up
     * @return the next aggregate, or {@code this} when none had assignments
     * @throws IllegalArgumentException when a key names no referrer
     */
    Project clearSliderPresetAssignments(List<ReferrerKey> keys) {
        Objects.requireNonNull(keys, "keys");
        Project next = this;
        for (ReferrerKey key : keys)
            next = next.clearSliderPresetAssignments(key);
        return next;
    }

    /**
     * Builds the next aggregate by applying one name-list rewrite to the single
     * referrer a key identifies. This is the only place that branches on the
     * referrer kind, so assign, unassign, and clear behave identically for both.
     * Only the keyed collection is copied, and only when the value changed; its
     * order is by name or identity, which a rewrite never touches.
     *
     * @throws IllegalArgumentException when the key names no referrer
     */
    private Project rewriteReferrer(ReferrerKey key, Function<List<String>, List<String>> rewriteNames) {
        Objects.requireNonNull(key, "key");
        if (key.customMorphTargetName != null) {
            int index = requireCustomMorphTarget(key.customMorphTargetName);
            List<CustomMorphTargetSnapshot> targets = CUSTOM_MORPH_TARGET_REFERRER.rewriteAt(customMorphTargets,
                    index, rewriteNames);
            return targets == customMorphTargets ? this : new Project(sliderPresets, targets, npcMorphAssignments);
        }
        int index = requireNpcMorphAssignment(key.npcMorphAssignmentIdentity);
        List<NpcMorphAssignmentSnapshot> npcs = NPC_MORPH_ASSIGNMENT_REFERRER.rewriteAt(npcMorphAssignments, index,
                rewriteNames);
        return npcs == npcMorphAssignments ? this : new Project(sliderPresets, customMorphTargets, npcs);
    }

    /**
     * Locates the referrer a key identifies without changing it, by applying the
     * identity rewrite: {@link #rewriteReferrer} still resolves the key (and throws
     * when it names nothing) but returns {@code this} because no names changed, so
     * the kind branch is not repeated here.
     *
     * @throws IllegalArgumentException when the key names no referrer
     */
    private void requireReferrer(ReferrerKey key) {
        rewriteReferrer(key, names -> names);
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

    /**
     * Locates a Custom Morph Target by case-insensitive logical name after trimming.
     *
     * @return the collection index, or -1 when absent or when name is null
     */
    private int indexOfCustomMorphTarget(String name) {
        if (name == null)
            return -1;
        String normalizedName = name.trim();
        for (int index = 0; index < customMorphTargets.size(); index++) {
            if (customMorphTargets.get(index).getName().equalsIgnoreCase(normalizedName))
                return index;
        }
        return -1;
    }

    /**
     * Locates a Custom Morph Target the caller has already looked up.
     *
     * @throws IllegalArgumentException when no Custom Morph Target has that name
     */
    private int requireCustomMorphTarget(String name) {
        int index = indexOfCustomMorphTarget(name);
        if (index < 0)
            throw new IllegalArgumentException("No Custom Morph Target is named: " + name);
        return index;
    }

    /**
     * Locates an NPC Morph Assignment by its complete case-insensitive identity.
     *
     * @return the collection index, or -1 when absent or when identity is null
     */
    private int indexOfNpcMorphAssignment(NpcMorphAssignmentIdentity identity) {
        if (identity == null)
            return -1;
        for (int index = 0; index < npcMorphAssignments.size(); index++) {
            if (identity.equals(identityOf(npcMorphAssignments.get(index))))
                return index;
        }
        return -1;
    }

    /**
     * Locates an NPC Morph Assignment the caller has already looked up.
     *
     * @throws IllegalArgumentException when no NPC Morph Assignment has that identity
     */
    private int requireNpcMorphAssignment(NpcMorphAssignmentIdentity identity) {
        int index = indexOfNpcMorphAssignment(identity);
        if (index < 0)
            throw new IllegalArgumentException("No NPC Morph Assignment has the identity: "
                    + (identity == null ? null : identity.getPluginName() + "/" + identity.getEditorId()));
        return index;
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

        /**
         * Wraps the next aggregate, which may be the same instance on a no-op.
         */
        static Result of(Project project) {
            return new Result(Objects.requireNonNull(project, "project"), null);
        }

        /**
         * Wraps the single diagnostic explaining why the operation was refused.
         */
        static Result rejected(ProjectDiagnostic diagnostic) {
            return new Result(null, Objects.requireNonNull(diagnostic, "diagnostic"));
        }

        /**
         * @return true when the operation was refused and {@link #getDiagnostic()} applies
         */
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
     * Identifies one value that references Slider Presets by name: a Custom Morph
     * Target by its case-insensitive name, or an NPC Morph Assignment by its
     * identity. Relationship operations take a key rather than a value so they
     * are written once for both referencing kinds. A key does not promise the
     * referrer exists; operations throw when it does not, because the session
     * looks up before it delegates.
     */
    static final class ReferrerKey {
        /**
         * Exactly one of the two fields is non-null.
         */
        private final String customMorphTargetName;
        private final NpcMorphAssignmentIdentity npcMorphAssignmentIdentity;

        private ReferrerKey(String customMorphTargetName, NpcMorphAssignmentIdentity npcMorphAssignmentIdentity) {
            this.customMorphTargetName = customMorphTargetName;
            this.npcMorphAssignmentIdentity = npcMorphAssignmentIdentity;
        }

        /**
         * Keys a Custom Morph Target by its logical name, compared without regard to
         * case or surrounding whitespace.
         */
        static ReferrerKey customMorphTarget(String name) {
            return new ReferrerKey(Objects.requireNonNull(name, "name"), null);
        }

        /**
         * Keys an NPC Morph Assignment by its complete case-insensitive identity.
         */
        static ReferrerKey npcMorphAssignment(NpcMorphAssignmentIdentity identity) {
            return new ReferrerKey(null, Objects.requireNonNull(identity, "identity"));
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
         * Rebuilds a value with a different name list. The result is always a new
         * instance, which is what makes a promoted NPC Morph Assignment independent
         * of the caller's source value.
         *
         * @param value value whose other fields are copied
         * @param names names the copy carries
         * @return a new value of the same kind
         */
        T copyWithNames(T value, List<String> names) {
            return withNames.apply(value, names);
        }

        /**
         * Applies a name-list rewrite to every value. A rewrite signals "unchanged"
         * by returning the very list it was given, and this method does the same
         * for the whole collection, so an unaffected collection is never copied.
         *
         * @param values       current canonical collection
         * @param rewriteNames pure rewrite of one value's names
         * @return the same list when no value changed, otherwise a new unmodifiable
         * list with unaffected values carried over by reference
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

        /**
         * Applies a name-list rewrite to the one value at an index, following the
         * same "unchanged means the same list" convention as {@link #rewrite}.
         *
         * @param values       current canonical collection
         * @param index        position of the value to rewrite
         * @param rewriteNames pure rewrite of that value's names
         * @return the same list when the value's names did not change, otherwise a
         * new unmodifiable list with every other value carried over by reference
         */
        List<T> rewriteAt(List<T> values, int index, Function<List<String>, List<String>> rewriteNames) {
            T value = values.get(index);
            List<String> currentNames = names.apply(value);
            List<String> changedNames = rewriteNames.apply(currentNames);
            if (changedNames == currentNames)
                return values;
            List<T> rewritten = new ArrayList<>(values);
            rewritten.set(index, withNames.apply(value, changedNames));
            return Collections.unmodifiableList(rewritten);
        }
    }
}
