package com.asdasfa.jbs2bg.project;

/**
 * Stable machine-readable codes emitted by foundational ProjectSession behavior.
 */
public final class ProjectDiagnosticCodes {

    /** An edit request type is not recognized by this ProjectSession. */
    public static final String EDIT_UNSUPPORTED = "PROJECT_EDIT_UNSUPPORTED";
    /** A known Project edit requires New Project or Open to establish active state. */
    public static final String ACTIVE_PROJECT_REQUIRED = "PROJECT_ACTIVE_REQUIRED";
    /** Project loading is not yet available through the foundational session. */
    public static final String OPEN_UNAVAILABLE = "PROJECT_OPEN_UNAVAILABLE";
    /** Project saving is not yet available through the foundational session. */
    public static final String SAVE_UNAVAILABLE = "PROJECT_SAVE_UNAVAILABLE";
    /** Save requires a successfully adopted Project file identity. */
    public static final String FILE_IDENTITY_REQUIRED = "PROJECT_FILE_IDENTITY_REQUIRED";
    /** A Slider Preset name is null, empty, or whitespace-only after trimming. */
    public static final String SLIDER_PRESET_NAME_REQUIRED = "SLIDER_PRESET_NAME_REQUIRED";
    /** A Slider Preset name contains a dot, which is reserved for XML normalization. */
    public static final String SLIDER_PRESET_NAME_CONTAINS_DOT = "SLIDER_PRESET_NAME_CONTAINS_DOT";
    /** A Slider Preset name duplicates another Project name without regard to case. */
    public static final String SLIDER_PRESET_NAME_DUPLICATE = "SLIDER_PRESET_NAME_DUPLICATE";
    /** A requested Slider Preset does not exist in the active Project. */
    public static final String SLIDER_PRESET_NOT_FOUND = "SLIDER_PRESET_NOT_FOUND";
    /** A slider-choice edit omitted its required immutable value. */
    public static final String SLIDER_CHOICE_REQUIRED = "SLIDER_CHOICE_REQUIRED";
    /** A full-update edit omitted its required immutable Slider Preset value. */
    public static final String SLIDER_PRESET_VALUE_REQUIRED = "SLIDER_PRESET_VALUE_REQUIRED";

    private ProjectDiagnosticCodes() {
    }
}
