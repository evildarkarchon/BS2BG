package com.asdasfa.jbs2bg.presentation;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Immutable fixed-name Output artifact used for the Templates and Morphs INI documents.
 */
final class Utf8OutputArtifact implements OutputArtifact {
    private final String fileName;
    private final byte[] bytes;

    /**
     * Encodes and defensively owns one complete accepted UTF-8 document.
     *
     * @param fileName canonical export filename
     * @param text     exact preview and publication text
     */
    Utf8OutputArtifact(String fileName, String text) {
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        bytes = Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8);
    }

    /** {@inheritDoc} */
    @Override
    public String getFileName() {
        return fileName;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] getBytes() {
        return bytes.clone();
    }

    /** {@inheritDoc} */
    @Override
    public String getText() {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
