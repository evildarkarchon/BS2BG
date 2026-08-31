package com.asdasfa.jbs2bg.presentation;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable mapping from one Slider Preset display name to its BoS artifact filename.
 */
public final class BosFileNameMapping implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sliderPresetName;
    private final String fileName;

    /**
     * Captures one attempted mapping, including failures that have no safe filename.
     *
     * @param sliderPresetName source Slider Preset display name
     * @param fileName         mapped filename, or {@code null} when the name is unrepresentable
     */
    BosFileNameMapping(String sliderPresetName, String fileName) {
        this.sliderPresetName = Objects.requireNonNull(sliderPresetName, "sliderPresetName");
        this.fileName = fileName;
    }

    /**
     * @return source Slider Preset display name
     */
    public String getSliderPresetName() {
        return sliderPresetName;
    }

    /**
     * @return mapped filename, or empty when no safe mapping could be produced
     */
    public Optional<String> getFileName() {
        return Optional.ofNullable(fileName);
    }

    /**
     * @return one escaped display line suitable for logs and user diagnostics
     */
    public String formatForDisplay() {
        return BosOutputException.escape(sliderPresetName) + " -> "
                + (fileName == null ? "<unrepresentable>" : BosOutputException.escape(fileName));
    }
}
