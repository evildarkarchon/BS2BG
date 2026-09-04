package com.asdasfa.jbs2bg.project;

import java.util.Objects;

/**
 * Immutable caller-owned choice assigning one Slider Preset to an identified NPC
 * during an atomic fill-empty edit. Random selection is completed before this
 * value crosses the ProjectSession seam.
 */
public final class NpcSliderPresetChoice {
    private final NpcMorphAssignmentIdentity identity;
    private final String sliderPresetName;

    /**
     * Captures one explicit fill-empty decision for later session validation.
     *
     * @param identity         target NPC Morph Assignment identity
     * @param sliderPresetName caller-chosen Slider Preset name
     * @throws NullPointerException when either value is null
     */
    public NpcSliderPresetChoice(NpcMorphAssignmentIdentity identity, String sliderPresetName) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.sliderPresetName = Objects.requireNonNull(sliderPresetName, "sliderPresetName");
    }

    /**
     * @return the target NPC Morph Assignment identity
     */
    public NpcMorphAssignmentIdentity getIdentity() {
        return identity;
    }

    /**
     * @return the explicit caller-chosen Slider Preset name
     */
    public String getSliderPresetName() {
        return sliderPresetName;
    }
}
