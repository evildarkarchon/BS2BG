package com.asdasfa.jbs2bg.project;

import java.util.Collections;
import java.util.List;

/**
 * Creates explicit immutable edit requests for the Project's Custom Morph
 * Targets and their Slider Preset relationships. Validation is performed
 * atomically by {@link ProjectSession#apply(ProjectEdit)}.
 */
public final class CustomMorphTargetEdits {
    private static final CustomMorphTargetEdit CLEAR = new Clear();

    private CustomMorphTargetEdits() {
    }

    /**
     * Requests creation of a Custom Morph Target without assignments.
     *
     * @param name requested Custom Morph Target name; the session trims and validates it
     * @return an immutable creation request
     */
    public static ProjectEdit create(String name) {
        return create(name, Collections.<String>emptyList());
    }

    /**
     * Requests creation of a Custom Morph Target with explicit Slider Preset
     * relationships chosen by the caller before submission.
     *
     * @param name requested Custom Morph Target name; the session trims and validates it
     * @param sliderPresetNames initial Slider Preset relationships
     * @return an immutable atomic creation request
     */
    public static ProjectEdit create(String name, List<String> sliderPresetNames) {
        return new Create(name, sliderPresetNames);
    }

    /**
     * Requests assignment of one existing Slider Preset to a Custom Morph Target.
     *
     * @param targetName existing Custom Morph Target name, compared without regard to case
     * @param sliderPresetName existing Slider Preset name, compared without regard to case
     * @return an immutable assignment-add request
     */
    public static ProjectEdit addSliderPreset(String targetName, String sliderPresetName) {
        return new AddSliderPreset(targetName, sliderPresetName);
    }

    /**
     * Requests atomic assignment of several existing Slider Presets to one Custom
     * Morph Target. Duplicate requested or existing relationships are no-ops.
     *
     * @param targetName existing Custom Morph Target name, compared without regard to case
     * @param sliderPresetNames caller-selected Slider Preset names
     * @return an immutable assignment-batch request
     */
    public static ProjectEdit addSliderPresets(String targetName, List<String> sliderPresetNames) {
        return new AddSliderPresets(targetName, sliderPresetNames);
    }

    /**
     * Requests removal of one Slider Preset assignment from a Custom Morph Target.
     *
     * @param targetName existing Custom Morph Target name, compared without regard to case
     * @param sliderPresetName assigned Slider Preset name, compared without regard to case
     * @return an immutable assignment-remove request
     */
    public static ProjectEdit removeSliderPreset(String targetName, String sliderPresetName) {
        return new RemoveSliderPreset(targetName, sliderPresetName);
    }

    /**
     * Requests removal of every Slider Preset assignment from one Custom Morph Target.
     *
     * @param targetName existing Custom Morph Target name, compared without regard to case
     * @return an immutable assignment-clear request
     */
    public static ProjectEdit clearSliderPresets(String targetName) {
        return new ClearSliderPresets(targetName);
    }

    /**
     * Requests deletion of one Custom Morph Target by case-insensitive name.
     *
     * @param name existing Custom Morph Target name
     * @return an immutable deletion request
     */
    public static ProjectEdit delete(String name) {
        return new Delete(name);
    }

    /**
     * Requests removal of every Custom Morph Target from the active Project.
     *
     * @return the immutable clear request
     */
    public static ProjectEdit clear() {
        return CLEAR;
    }

    /** Identifies the closed family of Custom Morph Target requests handled by the module. */
    interface CustomMorphTargetEdit extends ProjectEdit {
    }

    /** Immutable creation request interpreted only by the ProjectSession module. */
    static final class Create implements CustomMorphTargetEdit {
        private final String name;
        private final List<String> sliderPresetNames;

        /**
         * Captures the raw requested name so validation remains inside ProjectSession.
         *
         * @param name caller-supplied Custom Morph Target name
         * @param sliderPresetNames caller-selected initial relationships, or null for validation
         */
        Create(String name, List<String> sliderPresetNames) {
            this.name = name;
            this.sliderPresetNames = sliderPresetNames == null ? null
                    : ImmutableValues.copyOf(sliderPresetNames, "sliderPresetNames");
        }

        /** @return the raw requested Custom Morph Target name */
        String getName() {
            return name;
        }

        /** @return immutable initial Slider Preset names, or null when omitted */
        List<String> getSliderPresetNames() {
            return sliderPresetNames;
        }
    }

    /** Immutable assignment-add request interpreted only by the ProjectSession module. */
    static final class AddSliderPreset implements CustomMorphTargetEdit {
        private final String targetName;
        private final String sliderPresetName;

        /**
         * Captures both case-insensitive relationship endpoints for session validation.
         *
         * @param targetName existing Custom Morph Target name
         * @param sliderPresetName existing Slider Preset name
         */
        AddSliderPreset(String targetName, String sliderPresetName) {
            this.targetName = targetName;
            this.sliderPresetName = sliderPresetName;
        }

        /** @return the requested Custom Morph Target name */
        String getTargetName() {
            return targetName;
        }

        /** @return the requested Slider Preset name */
        String getSliderPresetName() {
            return sliderPresetName;
        }
    }

    /** Immutable assignment-batch request interpreted only by ProjectSession. */
    static final class AddSliderPresets implements CustomMorphTargetEdit {
        private final String targetName;
        private final List<String> sliderPresetNames;

        /**
         * Defensively captures the target and caller-selected relationship set.
         *
         * @param targetName existing Custom Morph Target name
         * @param sliderPresetNames selected Slider Preset names, or null for validation
         */
        AddSliderPresets(String targetName, List<String> sliderPresetNames) {
            this.targetName = targetName;
            this.sliderPresetNames = sliderPresetNames == null ? null
                    : ImmutableValues.copyOf(sliderPresetNames, "sliderPresetNames");
        }

        /** @return the requested Custom Morph Target name */
        String getTargetName() {
            return targetName;
        }

        /** @return immutable selected Slider Preset names, or null when omitted */
        List<String> getSliderPresetNames() {
            return sliderPresetNames;
        }
    }

    /** Immutable assignment-remove request interpreted only by the ProjectSession module. */
    static final class RemoveSliderPreset implements CustomMorphTargetEdit {
        private final String targetName;
        private final String sliderPresetName;

        /**
         * Captures both case-insensitive relationship endpoints for session validation.
         *
         * @param targetName existing Custom Morph Target name
         * @param sliderPresetName assigned Slider Preset name
         */
        RemoveSliderPreset(String targetName, String sliderPresetName) {
            this.targetName = targetName;
            this.sliderPresetName = sliderPresetName;
        }

        /** @return the requested Custom Morph Target name */
        String getTargetName() {
            return targetName;
        }

        /** @return the requested Slider Preset assignment name */
        String getSliderPresetName() {
            return sliderPresetName;
        }
    }

    /** Immutable assignment-clear request interpreted only by the ProjectSession module. */
    static final class ClearSliderPresets implements CustomMorphTargetEdit {
        private final String targetName;

        /**
         * Captures the case-insensitive Custom Morph Target identity.
         *
         * @param targetName existing Custom Morph Target name
         */
        ClearSliderPresets(String targetName) {
            this.targetName = targetName;
        }

        /** @return the requested Custom Morph Target name */
        String getTargetName() {
            return targetName;
        }
    }

    /** Immutable deletion request interpreted only by the ProjectSession module. */
    static final class Delete implements CustomMorphTargetEdit {
        private final String name;

        /**
         * Captures the requested case-insensitive Custom Morph Target identity.
         *
         * @param name existing Custom Morph Target name
         */
        Delete(String name) {
            this.name = name;
        }

        /** @return the requested Custom Morph Target name */
        String getName() {
            return name;
        }
    }

    /** Immutable clear request interpreted only by the ProjectSession module. */
    static final class Clear implements CustomMorphTargetEdit {
        private Clear() {
        }
    }
}
