package com.asdasfa.jbs2bg.project;

import java.util.List;
import java.util.Objects;

/**
 * Immutable Slider Preset value exposed by a Project snapshot.
 */
public final class SliderPresetSnapshot {

    private final String name;
    private final boolean uunp;
    private final List<SliderChoiceSnapshot> sliderChoices;

    /**
     * Creates a Slider Preset snapshot and copies its slider choices.
     *
     * @param name          canonical Slider Preset name
     * @param uunp          whether UUNP defaults apply
     * @param sliderChoices slider choices in canonical order
     * @throws NullPointerException when an argument or choice is null
     */
    public SliderPresetSnapshot(String name, boolean uunp, List<SliderChoiceSnapshot> sliderChoices) {
        this.name = Objects.requireNonNull(name, "name");
        this.uunp = uunp;
        this.sliderChoices = ImmutableValues.copyOf(sliderChoices, "sliderChoices");
    }

    /**
     * Returns the Slider Preset name.
     *
     * @return the canonical name
     */
    public String getName() {
        return name;
    }

    /**
     * Reports whether UUNP defaults apply.
     *
     * @return true for a UUNP Slider Preset
     */
    public boolean isUunp() {
        return uunp;
    }

    /**
     * Returns all explicit and synthesized slider choices.
     *
     * @return an immutable list of immutable choices
     */
    public List<SliderChoiceSnapshot> getSliderChoices() {
        return sliderChoices;
    }
}
