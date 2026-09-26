package com.asdasfa.jbs2bg.presentation;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rejects a BoS command with its complete filename mappings and ordered diagnostics.
 */
public final class BosOutputException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ArrayList<BosFileNameMapping> fileNameMappings;
    private final ArrayList<BosOutputDiagnostic> diagnostics;

    /**
     * Freezes every attempted mapping and validation failure before the exception crosses the seam.
     *
     * @param fileNameMappings complete mappings in Slider Preset order
     * @param diagnostics      ordered reasons the command was rejected
     */
    BosOutputException(List<BosFileNameMapping> fileNameMappings, List<BosOutputDiagnostic> diagnostics) {
        super(formatMessage(fileNameMappings, diagnostics));
        this.fileNameMappings = new ArrayList<>(fileNameMappings);
        this.diagnostics = new ArrayList<>(diagnostics);
    }

    /**
     * Builds the complete user-facing report carried through asynchronous UI failures.
     */
    private static String formatMessage(List<BosFileNameMapping> mappings,
                                        List<BosOutputDiagnostic> diagnostics) {
        StringBuilder message = new StringBuilder("BoS output was rejected.\nFilename mappings:");
        for (BosFileNameMapping mapping : mappings)
            message.append("\n  ").append(mapping.formatForDisplay());
        message.append("\nDiagnostics:");
        for (BosOutputDiagnostic diagnostic : diagnostics) {
            message.append("\n  [").append(diagnostic.getCode()).append("] ");
            if (!diagnostic.getSliderPresetName().isEmpty())
                message.append(escape(diagnostic.getSliderPresetName())).append(": ");
            message.append(escape(diagnostic.getMessage()));
        }
        return message.toString();
    }

    /**
     * Escapes control characters so Project-authored names cannot forge report lines.
     */
    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            switch (codePoint) {
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (codePoint < 0x20)
                        escaped.append(String.format("\\u%04x", Integer.valueOf(codePoint)));
                    else
                        escaped.appendCodePoint(codePoint);
                }
            }
            offset += Character.charCount(codePoint);
        }
        return escaped.toString();
    }

    /**
     * @return immutable complete mappings in Slider Preset order
     */
    public List<BosFileNameMapping> getFileNameMappings() {
        return Collections.unmodifiableList(fileNameMappings);
    }

    /**
     * @return immutable ordered diagnostics
     */
    public List<BosOutputDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }
}
