package com.asdasfa.jbs2bg.project;

import java.util.List;
import java.util.Objects;

/**
 * Immutable Custom Morph Target value exposed by a Project snapshot.
 */
public final class CustomMorphTargetSnapshot {

    private final String name;
    private final List<String> sliderPresetNames;

    /**
     * Creates a Custom Morph Target snapshot and copies its relationships by name.
     *
     * @param name              Custom Morph Target name, including valid BodyGen conditions
     * @param sliderPresetNames assigned Slider Preset names in canonical order
     * @throws NullPointerException when an argument or assignment is null
     */
    public CustomMorphTargetSnapshot(String name, List<String> sliderPresetNames) {
        this.name = Objects.requireNonNull(name, "name");
        this.sliderPresetNames = ImmutableValues.copyOf(sliderPresetNames, "sliderPresetNames");
    }

    /**
     * @return the Custom Morph Target name
     */
    public String getName() {
        return name;
    }

    /**
     * @return immutable assigned Slider Preset names
     */
    public List<String> getSliderPresetNames() {
        return sliderPresetNames;
    }
}
