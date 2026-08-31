package com.asdasfa.jbs2bg.presentation;

import java.io.Serializable;
import java.util.Objects;

/**
 * Structured reason one complete BoS generation or publication command was rejected.
 */
public final class BosOutputDiagnostic implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String sliderPresetName;
    private final String message;

    /**
     * Captures one stable diagnostic without exposing codec or filesystem exceptions.
     *
     * @param code             stable machine-readable diagnostic code
     * @param sliderPresetName related Slider Preset, or an empty string for command-wide failures
     * @param message          human-readable explanation
     */
    BosOutputDiagnostic(String code, String sliderPresetName, String message) {
        this.code = Objects.requireNonNull(code, "code");
        this.sliderPresetName = Objects.requireNonNull(sliderPresetName, "sliderPresetName");
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * @return stable machine-readable diagnostic code
     */
    public String getCode() {
        return code;
    }

    /**
     * @return related Slider Preset display name, or an empty string
     */
    public String getSliderPresetName() {
        return sliderPresetName;
    }

    /**
     * @return human-readable explanation
     */
    public String getMessage() {
        return message;
    }
}
