package com.asdasfa.jbs2bg.presentation;

/**
 * Immutable presentation effects derived from rendering one ProjectSession
 * outcome. JavaFX controls decide how to realize these effects.
 */
public final class ProjectPresentationUpdate {

    private final boolean invalidatesGeneratedOutput;
    private final String diagnosticText;
    private final boolean errorDiagnostics;

    /**
     * Captures the presentation-only effects of one rendered outcome.
     *
     * @param invalidatesGeneratedOutput whether generated output must be discarded
     * @param diagnosticText             formatted user-readable diagnostics, possibly empty
     * @param errorDiagnostics           whether any diagnostic has error severity
     */
    ProjectPresentationUpdate(boolean invalidatesGeneratedOutput, String diagnosticText, boolean errorDiagnostics) {
        this.invalidatesGeneratedOutput = invalidatesGeneratedOutput;
        this.diagnosticText = diagnosticText;
        this.errorDiagnostics = errorDiagnostics;
    }

    /**
     * @return true only when the rendered outcome changed Project content (Slider
     * Presets, Custom Morph Targets, or NPC Morph Assignments); dirty-flag or
     * file-identity changes alone, such as a save, do not invalidate
     */
    public boolean invalidatesGeneratedOutput() {
        return invalidatesGeneratedOutput;
    }

    /**
     * @return true when the outcome supplied at least one diagnostic
     */
    public boolean hasDiagnostics() {
        return !diagnosticText.isEmpty();
    }

    /**
     * @return true when at least one diagnostic has error severity
     */
    public boolean hasErrorDiagnostics() {
        return errorDiagnostics;
    }

    /**
     * @return formatted diagnostic text suitable for a presentation notification
     */
    public String getDiagnosticText() {
        return diagnosticText;
    }
}
