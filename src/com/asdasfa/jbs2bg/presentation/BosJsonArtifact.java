package com.asdasfa.jbs2bg.presentation;

import java.util.Objects;

/**
 * Immutable BoS JSON artifact that owns its canonical UTF-8 bytes and output mapping.
 */
public final class BosJsonArtifact implements OutputArtifact {
    private final String sliderPresetName;
    private final String fileName;
    private final Utf8Json json;

    /**
     * Captures one generated artifact without exposing the generic JSON implementation.
     *
     * @param sliderPresetName source Slider Preset display name
     * @param fileName         mapped JSON filename
     * @param json             defensively owned canonical JSON
     */
    BosJsonArtifact(String sliderPresetName, String fileName, Utf8Json json) {
        this.sliderPresetName = Objects.requireNonNull(sliderPresetName, "sliderPresetName");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.json = Objects.requireNonNull(json, "json");
    }

    /**
     * @return source Slider Preset display name
     */
    public String getSliderPresetName() {
        return sliderPresetName;
    }

    /**
     * @return mapped output filename including the {@code .json} extension
     */
    @Override
    public String getFileName() {
        return fileName;
    }

    /**
     * @return structured source-to-filename mapping for reporting this artifact
     */
    public BosFileNameMapping getFileNameMapping() {
        return new BosFileNameMapping(sliderPresetName, fileName);
    }

    /**
     * @return a defensive copy of the canonical UTF-8 bytes
     */
    @Override
    public byte[] getBytes() {
        return json.bytes();
    }

    /**
     * @return preview text decoded from the same canonical bytes used for publication
     */
    @Override
    public String getText() {
        return json.text();
    }
}
