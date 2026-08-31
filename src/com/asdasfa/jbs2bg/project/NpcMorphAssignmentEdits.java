package com.asdasfa.jbs2bg.project;

import java.util.List;

/**
 * Creates explicit immutable edit requests for the Project's NPC Morph
 * Assignments. Validation and source copying are performed atomically by
 * {@link ProjectSession#apply(ProjectEdit)}.
 */
public final class NpcMorphAssignmentEdits {
    private static final NpcMorphAssignmentEdit CLEAR_NPCS = new ClearNpcs();

    private NpcMorphAssignmentEdits() {
    }

    /**
     * Requests promotion of copied NPC source values into the active Project.
     * Any supplied Slider Preset names are explicit caller-owned choices that the
     * session resolves and validates.
     *
     * @param source immutable source values to copy into an NPC Morph Assignment
     * @return an immutable NPC-add request
     */
    public static ProjectEdit addNpc(NpcMorphAssignmentSnapshot source) {
        return new AddNpc(source);
    }

    /**
     * Requests one atomic promotion of caller-filtered NPC source values. Existing
     * Project identities are treated as no-ops; every new source is validated
     * before any are published.
     *
     * @param sources caller-filtered immutable NPC source values
     * @return an immutable bulk NPC-add request
     */
    public static ProjectEdit addNpcs(List<NpcMorphAssignmentSnapshot> sources) {
        return new AddNpcs(sources);
    }

    /**
     * Requests assignment of one existing Slider Preset to an NPC Morph Assignment.
     *
     * @param identity         existing NPC Morph Assignment identity
     * @param sliderPresetName existing Slider Preset name
     * @return an immutable assignment-add request
     */
    public static ProjectEdit addSliderPreset(NpcMorphAssignmentIdentity identity, String sliderPresetName) {
        return new AddSliderPreset(identity, sliderPresetName);
    }

    /**
     * Requests atomic assignment of several existing Slider Presets to one NPC
     * Morph Assignment. Duplicate requested or existing relationships are no-ops.
     *
     * @param identity          existing NPC Morph Assignment identity
     * @param sliderPresetNames caller-selected Slider Preset names
     * @return an immutable assignment-batch request
     */
    public static ProjectEdit addSliderPresets(NpcMorphAssignmentIdentity identity, List<String> sliderPresetNames) {
        return new AddSliderPresets(identity, sliderPresetNames);
    }

    /**
     * Requests removal of one Slider Preset relationship from an NPC Morph Assignment.
     *
     * @param identity         existing NPC Morph Assignment identity
     * @param sliderPresetName assigned Slider Preset name
     * @return an immutable assignment-remove request
     */
    public static ProjectEdit removeSliderPreset(NpcMorphAssignmentIdentity identity, String sliderPresetName) {
        return new RemoveSliderPreset(identity, sliderPresetName);
    }

    /**
     * Requests removal of every Slider Preset relationship from one NPC Morph Assignment.
     *
     * @param identity existing NPC Morph Assignment identity
     * @return an immutable assignment-clear request
     */
    public static ProjectEdit clearSliderPresets(NpcMorphAssignmentIdentity identity) {
        return new ClearSliderPresets(identity);
    }

    /**
     * Requests one atomic assignment clear for caller-filtered NPC identities.
     *
     * @param identities caller-selected NPC Morph Assignment identities
     * @return an immutable filtered assignment-clear request
     */
    public static ProjectEdit clearSliderPresetsForNpcs(List<NpcMorphAssignmentIdentity> identities) {
        return new ClearSliderPresetsForNpcs(identities);
    }

    /**
     * Requests removal of one NPC Morph Assignment by logical identity.
     *
     * @param identity existing NPC Morph Assignment identity
     * @return an immutable NPC-remove request
     */
    public static ProjectEdit removeNpc(NpcMorphAssignmentIdentity identity) {
        return new RemoveNpc(identity);
    }

    /**
     * Requests one atomic filtered removal of the supplied NPC identities.
     *
     * @param identities caller-selected identities to remove
     * @return an immutable filtered NPC-remove request
     */
    public static ProjectEdit removeNpcs(List<NpcMorphAssignmentIdentity> identities) {
        return new RemoveNpcs(identities);
    }

    /**
     * Requests removal of every NPC Morph Assignment from the active Project.
     *
     * @return the immutable clear request
     */
    public static ProjectEdit clearNpcs() {
        return CLEAR_NPCS;
    }

    /**
     * Requests one atomic fill of currently empty NPC Morph Assignments using
     * explicit choices already made by the caller.
     *
     * @param choices caller-owned identity/preset decisions
     * @return an immutable fill-empty request
     */
    public static ProjectEdit fillEmpty(List<NpcSliderPresetChoice> choices) {
        return new FillEmpty(choices);
    }

    /**
     * Identifies the closed family of NPC Morph Assignment requests.
     */
    interface NpcMorphAssignmentEdit extends ProjectEdit {
    }

    /**
     * Immutable NPC-add request interpreted only by the ProjectSession module.
     */
    static final class AddNpc implements NpcMorphAssignmentEdit {
        private final NpcMorphAssignmentSnapshot source;

        /**
         * Captures immutable source values while leaving validation in ProjectSession.
         *
         * @param source caller-supplied NPC source values
         */
        AddNpc(NpcMorphAssignmentSnapshot source) {
            this.source = source;
        }

        /**
         * @return the immutable source values, or null when omitted
         */
        NpcMorphAssignmentSnapshot getSource() {
            return source;
        }
    }

    /**
     * Immutable filtered bulk NPC-add request interpreted only by ProjectSession.
     */
    static final class AddNpcs implements NpcMorphAssignmentEdit {
        private final List<NpcMorphAssignmentSnapshot> sources;

        /**
         * Defensively copies the caller's filtered selection before apply time.
         *
         * @param sources selected immutable source values, or null for validation
         */
        AddNpcs(List<NpcMorphAssignmentSnapshot> sources) {
            this.sources = sources == null ? null : ImmutableValues.copyOf(sources, "sources");
        }

        /**
         * @return immutable filtered source values, or null when omitted
         */
        List<NpcMorphAssignmentSnapshot> getSources() {
            return sources;
        }
    }

    /**
     * Immutable assignment-add request interpreted only by ProjectSession.
     */
    static final class AddSliderPreset implements NpcMorphAssignmentEdit {
        private final NpcMorphAssignmentIdentity identity;
        private final String sliderPresetName;

        /**
         * Captures both relationship endpoints for session validation.
         *
         * @param identity         existing NPC identity
         * @param sliderPresetName requested Slider Preset name
         */
        AddSliderPreset(NpcMorphAssignmentIdentity identity, String sliderPresetName) {
            this.identity = identity;
            this.sliderPresetName = sliderPresetName;
        }

        /**
         * @return the requested NPC identity, or null when omitted
         */
        NpcMorphAssignmentIdentity getIdentity() {
            return identity;
        }

        /**
         * @return the requested Slider Preset name
         */
        String getSliderPresetName() {
            return sliderPresetName;
        }
    }

    /**
     * Immutable assignment-batch request interpreted only by ProjectSession.
     */
    static final class AddSliderPresets implements NpcMorphAssignmentEdit {
        private final NpcMorphAssignmentIdentity identity;
        private final List<String> sliderPresetNames;

        /**
         * Defensively captures one NPC identity and the selected relationship set.
         *
         * @param identity          existing NPC identity
         * @param sliderPresetNames selected Slider Preset names, or null for validation
         */
        AddSliderPresets(NpcMorphAssignmentIdentity identity, List<String> sliderPresetNames) {
            this.identity = identity;
            this.sliderPresetNames = sliderPresetNames == null ? null
                    : ImmutableValues.copyOf(sliderPresetNames, "sliderPresetNames");
        }

        /**
         * @return the requested NPC identity, or null when omitted
         */
        NpcMorphAssignmentIdentity getIdentity() {
            return identity;
        }

        /**
         * @return immutable selected Slider Preset names, or null when omitted
         */
        List<String> getSliderPresetNames() {
            return sliderPresetNames;
        }
    }

    /**
     * Immutable assignment-remove request interpreted only by ProjectSession.
     */
    static final class RemoveSliderPreset implements NpcMorphAssignmentEdit {
        private final NpcMorphAssignmentIdentity identity;
        private final String sliderPresetName;

        /**
         * Captures both relationship endpoints for session validation.
         *
         * @param identity         existing NPC identity
         * @param sliderPresetName assigned Slider Preset name
         */
        RemoveSliderPreset(NpcMorphAssignmentIdentity identity, String sliderPresetName) {
            this.identity = identity;
            this.sliderPresetName = sliderPresetName;
        }

        /**
         * @return the requested NPC identity, or null when omitted
         */
        NpcMorphAssignmentIdentity getIdentity() {
            return identity;
        }

        /**
         * @return the requested Slider Preset name
         */
        String getSliderPresetName() {
            return sliderPresetName;
        }
    }

    /**
     * Immutable assignment-clear request interpreted only by ProjectSession.
     */
    static final class ClearSliderPresets implements NpcMorphAssignmentEdit {
        private final NpcMorphAssignmentIdentity identity;

        /**
         * Captures the requested NPC identity for session validation.
         *
         * @param identity existing NPC identity
         */
        ClearSliderPresets(NpcMorphAssignmentIdentity identity) {
            this.identity = identity;
        }

        /**
         * @return the requested NPC identity, or null when omitted
         */
        NpcMorphAssignmentIdentity getIdentity() {
            return identity;
        }
    }

    /**
     * Immutable filtered assignment-clear request interpreted by ProjectSession.
     */
    static final class ClearSliderPresetsForNpcs implements NpcMorphAssignmentEdit {
        private final List<NpcMorphAssignmentIdentity> identities;

        /**
         * Defensively copies the caller's filtered identity selection.
         *
         * @param identities selected identities, or null for session validation
         */
        ClearSliderPresetsForNpcs(List<NpcMorphAssignmentIdentity> identities) {
            this.identities = identities == null ? null : ImmutableValues.copyOf(identities, "identities");
        }

        /**
         * @return immutable selected identities, or null when omitted
         */
        List<NpcMorphAssignmentIdentity> getIdentities() {
            return identities;
        }
    }

    /**
     * Immutable single-NPC removal request interpreted only by ProjectSession.
     */
    static final class RemoveNpc implements NpcMorphAssignmentEdit {
        private final NpcMorphAssignmentIdentity identity;

        /**
         * Captures the requested identity for session validation.
         *
         * @param identity existing NPC identity
         */
        RemoveNpc(NpcMorphAssignmentIdentity identity) {
            this.identity = identity;
        }

        /**
         * @return the requested NPC identity, or null when omitted
         */
        NpcMorphAssignmentIdentity getIdentity() {
            return identity;
        }
    }

    /**
     * Immutable filtered NPC removal request interpreted only by ProjectSession.
     */
    static final class RemoveNpcs implements NpcMorphAssignmentEdit {
        private final List<NpcMorphAssignmentIdentity> identities;

        /**
         * Defensively copies the caller-owned filtered identity selection.
         *
         * @param identities selected identities, or null for session validation
         */
        RemoveNpcs(List<NpcMorphAssignmentIdentity> identities) {
            this.identities = identities == null ? null : ImmutableValues.copyOf(identities, "identities");
        }

        /**
         * @return immutable selected identities, or null when omitted
         */
        List<NpcMorphAssignmentIdentity> getIdentities() {
            return identities;
        }
    }

    /**
     * Immutable clear-all-NPC request interpreted only by ProjectSession.
     */
    static final class ClearNpcs implements NpcMorphAssignmentEdit {
        private ClearNpcs() {
        }
    }

    /**
     * Immutable fill-empty request interpreted only by ProjectSession.
     */
    static final class FillEmpty implements NpcMorphAssignmentEdit {
        private final List<NpcSliderPresetChoice> choices;

        /**
         * Defensively copies explicit caller decisions before apply time.
         *
         * @param choices explicit decisions, or null for session validation
         */
        FillEmpty(List<NpcSliderPresetChoice> choices) {
            this.choices = choices == null ? null : ImmutableValues.copyOf(choices, "choices");
        }

        /**
         * @return immutable explicit decisions, or null when omitted
         */
        List<NpcSliderPresetChoice> getChoices() {
            return choices;
        }
    }
}
