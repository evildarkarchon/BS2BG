package com.asdasfa.jbs2bg.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Serializes Project operations and atomically publishes immutable snapshots.
 */
final class DefaultProjectSession implements ProjectSession {
    private static final Comparator<SliderPresetSnapshot> SLIDER_PRESET_NAME_ORDER =
            new Comparator<SliderPresetSnapshot>() {
                @Override
                public int compare(SliderPresetSnapshot left, SliderPresetSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    private static final Comparator<SliderChoiceSnapshot> SLIDER_CHOICE_NAME_ORDER =
            new Comparator<SliderChoiceSnapshot>() {
                @Override
                public int compare(SliderChoiceSnapshot left, SliderChoiceSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    private static final Comparator<CustomMorphTargetSnapshot> CUSTOM_MORPH_TARGET_NAME_ORDER =
            new Comparator<CustomMorphTargetSnapshot>() {
                @Override
                public int compare(CustomMorphTargetSnapshot left, CustomMorphTargetSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    private static final Comparator<String> CASE_INSENSITIVE_NAME_ORDER = new Comparator<String>() {
        @Override
        public int compare(String left, String right) {
            return left.compareToIgnoreCase(right);
        }
    };

    private final Object operationLock = new Object();
    private volatile ProjectSnapshot snapshot = ProjectSnapshot.noProject();

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome newProject() {
        synchronized (operationLock) {
            ProjectSnapshot emptyProject = ProjectSnapshot.empty();
            if (snapshot == emptyProject)
                return new UnchangedOutcome(snapshot);
            snapshot = emptyProject;
            return new ChangedOutcome(snapshot);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome open(Path source) {
        Objects.requireNonNull(source, "source");
        synchronized (operationLock) {
            SourceLocation location = new SourceLocation(Optional.of(source), Optional.empty(), OptionalInt.empty(),
                    OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.OPEN_UNAVAILABLE, location,
                    "Opening Projects is not available in this ProjectSession implementation yet.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome save() {
        synchronized (operationLock) {
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("project-file"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.FILE_IDENTITY_REQUIRED, location,
                    "The Project has no file identity; choose a target before saving.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome saveAs(Path target) {
        Objects.requireNonNull(target, "target");
        synchronized (operationLock) {
            SourceLocation location = new SourceLocation(Optional.of(target), Optional.empty(), OptionalInt.empty(),
                    OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.SAVE_UNAVAILABLE, location,
                    "Saving Projects is not available in this ProjectSession implementation yet.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome apply(ProjectEdit edit) {
        Objects.requireNonNull(edit, "edit");
        synchronized (operationLock) {
            if ((edit instanceof SliderPresetEdits.SliderPresetEdit
                    || edit instanceof CustomMorphTargetEdits.CustomMorphTargetEdit)
                    && snapshot.getLifecycleStatus() == ProjectLifecycleStatus.NO_PROJECT)
                return rejectedActiveProjectRequired();
            if (edit instanceof CustomMorphTargetEdits.Create)
                return createCustomMorphTarget((CustomMorphTargetEdits.Create) edit);
            if (edit instanceof CustomMorphTargetEdits.AddSliderPreset)
                return addCustomMorphTargetSliderPreset((CustomMorphTargetEdits.AddSliderPreset) edit);
            if (edit instanceof CustomMorphTargetEdits.RemoveSliderPreset)
                return removeCustomMorphTargetSliderPreset((CustomMorphTargetEdits.RemoveSliderPreset) edit);
            if (edit instanceof CustomMorphTargetEdits.ClearSliderPresets)
                return clearCustomMorphTargetSliderPresets((CustomMorphTargetEdits.ClearSliderPresets) edit);
            if (edit instanceof CustomMorphTargetEdits.Delete)
                return deleteCustomMorphTarget((CustomMorphTargetEdits.Delete) edit);
            if (edit instanceof CustomMorphTargetEdits.Clear)
                return clearCustomMorphTargets();
            if (edit instanceof SliderPresetEdits.Create)
                return createSliderPreset((SliderPresetEdits.Create) edit);
            if (edit instanceof SliderPresetEdits.Duplicate)
                return duplicateSliderPreset((SliderPresetEdits.Duplicate) edit);
            if (edit instanceof SliderPresetEdits.Update)
                return updateSliderPreset((SliderPresetEdits.Update) edit);
            if (edit instanceof SliderPresetEdits.Rename)
                return renameSliderPreset((SliderPresetEdits.Rename) edit);
            if (edit instanceof SliderPresetEdits.Delete)
                return deleteSliderPreset((SliderPresetEdits.Delete) edit);
            if (edit instanceof SliderPresetEdits.Clear)
                return clearSliderPresets();
            if (edit instanceof SliderPresetEdits.SetUunp)
                return setSliderPresetUunp((SliderPresetEdits.SetUunp) edit);
            if (edit instanceof SliderPresetEdits.SetSliderChoice)
                return setSliderChoice((SliderPresetEdits.SetSliderChoice) edit);
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("project-edit"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.EDIT_UNSUPPORTED, location,
                    "The Project edit request type is not supported.");
        }
    }

    /**
     * Rejects a known edit until a lifecycle operation establishes active state.
     *
     * @return a structured rejection carrying the pre-lifecycle snapshot
     */
    private RejectedOutcome rejectedActiveProjectRequired() {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("project"), OptionalInt.empty(),
                OptionalInt.empty());
        return rejected(ProjectDiagnosticCodes.ACTIVE_PROJECT_REQUIRED, location,
                "Create or open a Project before applying edits.");
    }

    /**
     * Creates an empty Custom Morph Target and publishes the collection in
     * canonical order.
     *
     * @param edit immutable creation request
     * @return a changed outcome carrying the new dirty snapshot
     */
    private ProjectOutcome createCustomMorphTarget(CustomMorphTargetEdits.Create edit) {
        RejectedOutcome rejection = validateCustomMorphTargetName(edit.getName());
        if (rejection != null)
            return rejection;
        String name = edit.getName().trim();
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(snapshot.getCustomMorphTargets());
        targets.add(new CustomMorphTargetSnapshot(name, Collections.<String>emptyList()));
        return publishChangedCustomMorphTargets(targets);
    }

    /**
     * Adds one canonical Slider Preset relationship to a Custom Morph Target.
     * Duplicate logical relationships preserve the exact current snapshot.
     *
     * @param edit immutable assignment-add request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome addCustomMorphTargetSliderPreset(CustomMorphTargetEdits.AddSliderPreset edit) {
        int targetIndex = findCustomMorphTarget(edit.getTargetName());
        if (targetIndex < 0)
            return rejectedCustomMorphTargetNotFound();
        int presetIndex = findSliderPreset(edit.getSliderPresetName());
        if (presetIndex < 0)
            return rejectedSliderPresetNotFound();

        CustomMorphTargetSnapshot current = snapshot.getCustomMorphTargets().get(targetIndex);
        String presetName = snapshot.getSliderPresets().get(presetIndex).getName();
        List<String> assignments = new ArrayList<>(current.getSliderPresetNames());
        for (String assignment : assignments) {
            if (assignment.equalsIgnoreCase(presetName))
                return new UnchangedOutcome(snapshot);
        }
        assignments.add(presetName);
        Collections.sort(assignments, CASE_INSENSITIVE_NAME_ORDER);
        CustomMorphTargetSnapshot changed = new CustomMorphTargetSnapshot(current.getName(), assignments);
        return replaceCustomMorphTarget(targetIndex, changed);
    }

    /**
     * Removes one case-insensitive Slider Preset relationship from a Custom Morph
     * Target, preserving the current snapshot when it is already absent.
     *
     * @param edit immutable assignment-remove request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome removeCustomMorphTargetSliderPreset(CustomMorphTargetEdits.RemoveSliderPreset edit) {
        int targetIndex = findCustomMorphTarget(edit.getTargetName());
        if (targetIndex < 0)
            return rejectedCustomMorphTargetNotFound();

        CustomMorphTargetSnapshot current = snapshot.getCustomMorphTargets().get(targetIndex);
        String requestedName = edit.getSliderPresetName() == null ? "" : edit.getSliderPresetName().trim();
        List<String> assignments = new ArrayList<>(current.getSliderPresetNames());
        int assignmentIndex = -1;
        for (int index = 0; index < assignments.size(); index++) {
            if (assignments.get(index).equalsIgnoreCase(requestedName)) {
                assignmentIndex = index;
                break;
            }
        }
        if (assignmentIndex < 0)
            return new UnchangedOutcome(snapshot);
        assignments.remove(assignmentIndex);
        CustomMorphTargetSnapshot changed = new CustomMorphTargetSnapshot(current.getName(), assignments);
        return replaceCustomMorphTarget(targetIndex, changed);
    }

    /**
     * Clears every Slider Preset relationship from a Custom Morph Target while
     * preserving the current snapshot when the relationship collection is empty.
     *
     * @param edit immutable assignment-clear request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome clearCustomMorphTargetSliderPresets(CustomMorphTargetEdits.ClearSliderPresets edit) {
        int targetIndex = findCustomMorphTarget(edit.getTargetName());
        if (targetIndex < 0)
            return rejectedCustomMorphTargetNotFound();

        CustomMorphTargetSnapshot current = snapshot.getCustomMorphTargets().get(targetIndex);
        if (current.getSliderPresetNames().isEmpty())
            return new UnchangedOutcome(snapshot);
        CustomMorphTargetSnapshot changed = new CustomMorphTargetSnapshot(current.getName(),
                Collections.<String>emptyList());
        return replaceCustomMorphTarget(targetIndex, changed);
    }

    /**
     * Replaces one Custom Morph Target and publishes the dirty canonical collection.
     *
     * @param targetIndex collection index to replace
     * @param changed replacement immutable value
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome replaceCustomMorphTarget(int targetIndex, CustomMorphTargetSnapshot changed) {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(snapshot.getCustomMorphTargets());
        targets.set(targetIndex, changed);
        return publishChangedCustomMorphTargets(targets);
    }

    /**
     * Removes one existing Custom Morph Target selected by case-insensitive identity.
     *
     * @param edit immutable deletion request
     * @return a changed or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome deleteCustomMorphTarget(CustomMorphTargetEdits.Delete edit) {
        int targetIndex = findCustomMorphTarget(edit.getName());
        if (targetIndex < 0)
            return rejectedCustomMorphTargetNotFound();
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(snapshot.getCustomMorphTargets());
        targets.remove(targetIndex);
        return publishChangedCustomMorphTargets(targets);
    }

    /**
     * Clears a non-empty Custom Morph Target collection without manufacturing a
     * dirty transition for an already-empty Project.
     *
     * @return changed or unchanged outcome at the pinned snapshot
     */
    private ProjectOutcome clearCustomMorphTargets() {
        if (snapshot.getCustomMorphTargets().isEmpty())
            return new UnchangedOutcome(snapshot);
        return publishChangedCustomMorphTargets(new ArrayList<CustomMorphTargetSnapshot>());
    }

    /**
     * Finds a Custom Morph Target using its case-insensitive Project identity.
     *
     * @param name requested name, optionally surrounded by whitespace
     * @return the collection index, or -1 when no logical target matches
     */
    private int findCustomMorphTarget(String name) {
        if (name == null)
            return -1;
        String normalizedName = name.trim();
        for (int index = 0; index < snapshot.getCustomMorphTargets().size(); index++) {
            if (snapshot.getCustomMorphTargets().get(index).getName().equalsIgnoreCase(normalizedName))
                return index;
        }
        return -1;
    }

    /**
     * Rejects an edit whose Custom Morph Target cannot be resolved.
     *
     * @return a structured rejection carrying the current snapshot
     */
    private RejectedOutcome rejectedCustomMorphTargetNotFound() {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("custom-morph-target.name"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NOT_FOUND, location,
                "The requested Custom Morph Target does not exist.");
    }

    /**
     * Validates a requested Custom Morph Target name without restricting BodyGen
     * condition syntax.
     *
     * @param requestedName caller-supplied name before normalization
     * @return a structured rejection, or null when the trimmed name is valid
     */
    private RejectedOutcome validateCustomMorphTargetName(String requestedName) {
        if (requestedName == null || requestedName.trim().isEmpty())
            return rejectedCustomMorphTargetName(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_REQUIRED,
                    "A Custom Morph Target name must not be empty.");
        String normalizedName = requestedName.trim();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            if (target.getName().equalsIgnoreCase(normalizedName))
                return rejectedCustomMorphTargetName(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_DUPLICATE,
                        "A Custom Morph Target with this name already exists.");
        }
        return null;
    }

    /**
     * Builds a naming rejection at the stable Custom Morph Target name location.
     *
     * @param code stable diagnostic code
     * @param message human-readable validation failure
     * @return a rejection carrying the unchanged current snapshot
     */
    private RejectedOutcome rejectedCustomMorphTargetName(String code, String message) {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("custom-morph-target.name"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(code, location, message);
    }

    /**
     * Creates an empty Slider Preset and publishes the catalog in canonical order.
     *
     * @param edit immutable creation request
     * @return a changed outcome carrying the new dirty snapshot
     */
    private ProjectOutcome createSliderPreset(SliderPresetEdits.Create edit) {
        RejectedOutcome rejection = validateSliderPresetName(edit.getName(), OptionalInt.empty());
        if (rejection != null)
            return rejection;
        String name = edit.getName().trim();
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        presets.add(new SliderPresetSnapshot(name, false, Collections.<SliderChoiceSnapshot>emptyList()));
        return publishChangedPresets(presets);
    }

    /**
     * Duplicates the complete immutable Slider Preset value under a validated name.
     *
     * @param edit immutable duplication request
     * @return a changed or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome duplicateSliderPreset(SliderPresetEdits.Duplicate edit) {
        int sourceIndex = findSliderPreset(edit.getSourceName());
        if (sourceIndex < 0)
            return rejectedSliderPresetNotFound();
        RejectedOutcome rejection = validateSliderPresetName(edit.getDuplicateName(), OptionalInt.empty());
        if (rejection != null)
            return rejection;

        SliderPresetSnapshot source = snapshot.getSliderPresets().get(sourceIndex);
        SliderPresetSnapshot duplicate = new SliderPresetSnapshot(edit.getDuplicateName().trim(), source.isUunp(),
                source.getSliderChoices());
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        presets.add(duplicate);
        return publishChangedPresets(presets);
    }

    /**
     * Replaces the complete Slider Preset value after normalizing its name and
     * slider-choice order.
     *
     * @param edit immutable full-update request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome updateSliderPreset(SliderPresetEdits.Update edit) {
        int index = findSliderPreset(edit.getCurrentName());
        if (index < 0)
            return rejectedSliderPresetNotFound();
        if (edit.getReplacement() == null) {
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("slider-preset"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.SLIDER_PRESET_VALUE_REQUIRED, location,
                    "A Slider Preset update requires a replacement value.");
        }
        RejectedOutcome rejection = validateSliderPresetName(edit.getReplacement().getName(), OptionalInt.of(index));
        if (rejection != null)
            return rejection;

        SliderPresetSnapshot replacement = canonicalSliderPreset(edit.getReplacement(),
                edit.getReplacement().getName().trim());
        if (sameSliderPreset(snapshot.getSliderPresets().get(index), replacement))
            return new UnchangedOutcome(snapshot);
        return replaceSliderPreset(index, replacement);
    }

    /**
     * Changes display casing or spelling while retaining the Slider Preset's
     * complete payload and case-insensitive logical identity.
     *
     * @param edit immutable rename request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome renameSliderPreset(SliderPresetEdits.Rename edit) {
        int index = findSliderPreset(edit.getCurrentName());
        if (index < 0)
            return rejectedSliderPresetNotFound();
        RejectedOutcome rejection = validateSliderPresetName(edit.getNewName(), OptionalInt.of(index));
        if (rejection != null)
            return rejection;

        SliderPresetSnapshot current = snapshot.getSliderPresets().get(index);
        String normalizedName = edit.getNewName().trim();
        if (current.getName().equals(normalizedName))
            return new UnchangedOutcome(snapshot);
        SliderPresetSnapshot renamed = new SliderPresetSnapshot(normalizedName, current.isUunp(),
                current.getSliderChoices());
        return replaceSliderPreset(index, renamed);
    }

    /**
     * Removes one existing Slider Preset and every affected Custom Morph Target
     * relationship in one atomically published snapshot.
     *
     * @param edit immutable deletion request
     * @return a changed or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome deleteSliderPreset(SliderPresetEdits.Delete edit) {
        int index = findSliderPreset(edit.getName());
        if (index < 0)
            return rejectedSliderPresetNotFound();
        String removedName = snapshot.getSliderPresets().get(index).getName();
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        presets.remove(index);
        List<CustomMorphTargetSnapshot> targets = removeCustomMorphTargetAssignments(removedName);
        return publishChangedProjectState(presets, targets);
    }

    /**
     * Clears a non-empty Slider Preset catalog and every Custom Morph Target
     * relationship atomically, without manufacturing a dirty transition for an
     * already-empty Project.
     *
     * @return changed or unchanged outcome at the pinned snapshot
     */
    private ProjectOutcome clearSliderPresets() {
        if (snapshot.getSliderPresets().isEmpty())
            return new UnchangedOutcome(snapshot);
        List<CustomMorphTargetSnapshot> targets = clearAllCustomMorphTargetAssignments();
        return publishChangedProjectState(new ArrayList<SliderPresetSnapshot>(), targets);
    }

    /**
     * Changes the UUNP flag without flattening the Slider Preset's immutable choices.
     *
     * @param edit immutable UUNP request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome setSliderPresetUunp(SliderPresetEdits.SetUunp edit) {
        int index = findSliderPreset(edit.getName());
        if (index < 0)
            return rejectedSliderPresetNotFound();
        SliderPresetSnapshot current = snapshot.getSliderPresets().get(index);
        if (current.isUunp() == edit.isUunp())
            return new UnchangedOutcome(snapshot);
        SliderPresetSnapshot changed = new SliderPresetSnapshot(current.getName(), edit.isUunp(),
                current.getSliderChoices());
        return replaceSliderPreset(index, changed);
    }

    /**
     * Upserts one slider choice while preserving nullable stored values, effective
     * values, percentages, and missing-default identity.
     *
     * @param edit immutable slider-choice request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome setSliderChoice(SliderPresetEdits.SetSliderChoice edit) {
        int presetIndex = findSliderPreset(edit.getPresetName());
        if (presetIndex < 0)
            return rejectedSliderPresetNotFound();
        if (edit.getChoice() == null) {
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("slider-preset.slider-choice"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.SLIDER_CHOICE_REQUIRED, location,
                    "A slider-choice edit requires a value.");
        }

        SliderPresetSnapshot current = snapshot.getSliderPresets().get(presetIndex);
        List<SliderChoiceSnapshot> choices = new ArrayList<>(current.getSliderChoices());
        int choiceIndex = findSliderChoice(choices, edit.getChoice().getName());
        if (choiceIndex >= 0 && sameSliderChoice(choices.get(choiceIndex), edit.getChoice()))
            return new UnchangedOutcome(snapshot);
        if (choiceIndex >= 0)
            choices.set(choiceIndex, edit.getChoice());
        else
            choices.add(edit.getChoice());
        Collections.sort(choices, SLIDER_CHOICE_NAME_ORDER);
        SliderPresetSnapshot changed = new SliderPresetSnapshot(current.getName(), current.isUunp(), choices);
        return replaceSliderPreset(presetIndex, changed);
    }

    /**
     * Finds a Slider Preset using its case-insensitive Project identity.
     *
     * @param name requested name, optionally surrounded by whitespace
     * @return the catalog index, or -1 when no logical Slider Preset matches
     */
    private int findSliderPreset(String name) {
        if (name == null)
            return -1;
        String normalizedName = name.trim();
        for (int index = 0; index < snapshot.getSliderPresets().size(); index++) {
            if (snapshot.getSliderPresets().get(index).getName().equalsIgnoreCase(normalizedName))
                return index;
        }
        return -1;
    }

    /**
     * Finds a slider choice without making display casing part of its identity.
     *
     * @param choices current immutable choice values
     * @param name requested slider name
     * @return the choice index, or -1 when absent
     */
    private static int findSliderChoice(List<SliderChoiceSnapshot> choices, String name) {
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).getName().equalsIgnoreCase(name))
                return index;
        }
        return -1;
    }

    /**
     * Compares all observable slider-choice values so only real persisted-state
     * changes dirty the Project.
     *
     * @param left current choice
     * @param right requested choice
     * @return true when every exposed value is equal
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
     * Produces a Slider Preset whose choices are in stable case-insensitive order.
     *
     * @param source immutable source value
     * @param normalizedName validated display name
     * @return a canonically ordered immutable value
     */
    private static SliderPresetSnapshot canonicalSliderPreset(SliderPresetSnapshot source, String normalizedName) {
        List<SliderChoiceSnapshot> choices = new ArrayList<>(source.getSliderChoices());
        Collections.sort(choices, SLIDER_CHOICE_NAME_ORDER);
        return new SliderPresetSnapshot(normalizedName, source.isUunp(), choices);
    }

    /**
     * Compares complete immutable Slider Preset values after canonicalization.
     *
     * @param left current value
     * @param right requested canonical value
     * @return true when name, UUNP, and every slider choice are equal
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
     * Replaces one Slider Preset and, when its display name changes, rewrites every
     * affected Custom Morph Target relationship in the same published snapshot.
     *
     * @param index catalog index to replace
     * @param changed replacement immutable value
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome replaceSliderPreset(int index, SliderPresetSnapshot changed) {
        String previousName = snapshot.getSliderPresets().get(index).getName();
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        presets.set(index, changed);
        if (!previousName.equals(changed.getName())) {
            List<CustomMorphTargetSnapshot> targets = renameCustomMorphTargetAssignments(previousName,
                    changed.getName());
            return publishChangedProjectState(presets, targets);
        }
        return publishChangedPresets(presets);
    }

    /**
     * Rewrites every relationship to a renamed Slider Preset while retaining
     * canonical assignment order and unaffected immutable target values.
     *
     * @param previousName prior Slider Preset display name
     * @param changedName replacement canonical display name
     * @return Custom Morph Targets with every matching relationship updated
     */
    private List<CustomMorphTargetSnapshot> renameCustomMorphTargetAssignments(String previousName,
            String changedName) {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            List<String> assignments = new ArrayList<>(target.getSliderPresetNames());
            boolean changed = false;
            for (int index = 0; index < assignments.size(); index++) {
                if (assignments.get(index).equalsIgnoreCase(previousName)) {
                    assignments.set(index, changedName);
                    changed = true;
                }
            }
            if (changed) {
                Collections.sort(assignments, CASE_INSENSITIVE_NAME_ORDER);
                targets.add(new CustomMorphTargetSnapshot(target.getName(), assignments));
            } else {
                targets.add(target);
            }
        }
        return targets;
    }

    /**
     * Removes every relationship to a deleted Slider Preset without changing
     * unrelated Custom Morph Target values.
     *
     * @param removedName deleted Slider Preset display name
     * @return Custom Morph Targets with matching relationships removed
     */
    private List<CustomMorphTargetSnapshot> removeCustomMorphTargetAssignments(String removedName) {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            List<String> assignments = new ArrayList<>(target.getSliderPresetNames());
            boolean changed = false;
            for (int index = assignments.size() - 1; index >= 0; index--) {
                if (assignments.get(index).equalsIgnoreCase(removedName)) {
                    assignments.remove(index);
                    changed = true;
                }
            }
            targets.add(changed ? new CustomMorphTargetSnapshot(target.getName(), assignments) : target);
        }
        return targets;
    }

    /**
     * Clears every Custom Morph Target relationship when the Slider Preset catalog
     * is cleared, retaining already-empty immutable target values.
     *
     * @return Custom Morph Targets with empty Slider Preset relationships
     */
    private List<CustomMorphTargetSnapshot> clearAllCustomMorphTargetAssignments() {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            if (target.getSliderPresetNames().isEmpty())
                targets.add(target);
            else
                targets.add(new CustomMorphTargetSnapshot(target.getName(), Collections.<String>emptyList()));
        }
        return targets;
    }

    /**
     * Rejects an edit whose target cannot be resolved without changing dirty state.
     *
     * @return a structured rejection carrying the current snapshot
     */
    private RejectedOutcome rejectedSliderPresetNotFound() {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("slider-preset.name"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, location,
                "The requested Slider Preset does not exist.");
    }

    /**
     * Validates a requested Slider Preset name against Project naming invariants.
     *
     * @param requestedName caller-supplied name before normalization
     * @param exemptIndex existing catalog index allowed to retain its logical name,
     *        or empty when creating a new Slider Preset
     * @return a structured rejection, or null when the trimmed name is valid
     */
    private RejectedOutcome validateSliderPresetName(String requestedName, OptionalInt exemptIndex) {
        if (requestedName == null || requestedName.trim().isEmpty())
            return rejectedSliderPresetName(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_REQUIRED,
                    "A Slider Preset name must not be empty.");
        String normalizedName = requestedName.trim();
        if (normalizedName.indexOf('.') >= 0)
            return rejectedSliderPresetName(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_CONTAINS_DOT,
                    "A Slider Preset name must not contain dots.");
        for (int index = 0; index < snapshot.getSliderPresets().size(); index++) {
            if ((!exemptIndex.isPresent() || index != exemptIndex.getAsInt())
                    && snapshot.getSliderPresets().get(index).getName().equalsIgnoreCase(normalizedName))
                return rejectedSliderPresetName(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE,
                        "A Slider Preset with this name already exists.");
        }
        return null;
    }

    /**
     * Builds a naming rejection at the stable Slider Preset name location.
     *
     * @param code stable diagnostic code
     * @param message human-readable validation failure
     * @return a rejection carrying the unchanged current snapshot
     */
    private RejectedOutcome rejectedSliderPresetName(String code, String message) {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("slider-preset.name"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(code, location, message);
    }

    /**
     * Sorts changed Slider Presets and atomically publishes a dirty Project snapshot.
     *
     * @param presets changed catalog values
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome publishChangedPresets(List<SliderPresetSnapshot> presets) {
        return publishChangedProjectState(presets,
                new ArrayList<>(snapshot.getCustomMorphTargets()));
    }

    /**
     * Sorts changed Custom Morph Targets and atomically publishes a dirty Project
     * snapshot.
     *
     * @param targets changed Custom Morph Target values
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome publishChangedCustomMorphTargets(List<CustomMorphTargetSnapshot> targets) {
        return publishChangedProjectState(new ArrayList<>(snapshot.getSliderPresets()), targets);
    }

    /**
     * Canonically orders and atomically publishes changed Slider Presets together
     * with their repaired Custom Morph Target relationships.
     *
     * @param presets changed Slider Preset catalog
     * @param targets repaired Custom Morph Target values
     * @return a changed outcome carrying the single coherent snapshot
     */
    private ChangedOutcome publishChangedProjectState(List<SliderPresetSnapshot> presets,
            List<CustomMorphTargetSnapshot> targets) {
        Collections.sort(presets, SLIDER_PRESET_NAME_ORDER);
        Collections.sort(targets, CUSTOM_MORPH_TARGET_NAME_ORDER);
        snapshot = new ProjectSnapshot(presets, targets, snapshot.getNpcMorphAssignments(),
                snapshot.getFileIdentity(), true, snapshot.getLifecycleStatus());
        return new ChangedOutcome(snapshot);
    }

    /**
     * Builds a validation rejection while the operation lock pins the snapshot used
     * by both the outcome and concurrent callers.
     *
     * @param code stable diagnostic code
     * @param location structured source location
     * @param message human-readable diagnostic message
     * @return a rejection carrying the pinned snapshot
     */
    private RejectedOutcome rejected(String code, SourceLocation location, String message) {
        ProjectDiagnostic diagnostic = new ProjectDiagnostic(code, DiagnosticSeverity.ERROR, location, message);
        return new RejectedOutcome(snapshot, Collections.singletonList(diagnostic));
    }
}
