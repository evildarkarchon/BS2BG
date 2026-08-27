package com.asdasfa.jbs2bg.project;

/**
 * Stable machine-readable codes emitted by foundational ProjectSession behavior.
 */
public final class ProjectDiagnosticCodes {

    /** An edit request type is not recognized by this ProjectSession. */
    public static final String EDIT_UNSUPPORTED = "PROJECT_EDIT_UNSUPPORTED";
    /** A known Project edit requires New Project or Open to establish active state. */
    public static final String ACTIVE_PROJECT_REQUIRED = "PROJECT_ACTIVE_REQUIRED";
    /** A Project file could not be read from the local filesystem. */
    public static final String PROJECT_FILE_READ_FAILED = "PROJECT_FILE_READ_FAILED";
    /** An unexpected environmental or runtime failure prevented Project opening. */
    public static final String PROJECT_OPEN_FAILED = "PROJECT_OPEN_FAILED";
    /** A Project file contains syntactically malformed JSON. */
    public static final String PROJECT_JSON_MALFORMED = "PROJECT_JSON_MALFORMED";
    /** A Project file omits or mis-shapes required legacy structure. */
    public static final String PROJECT_STRUCTURE_INVALID = "PROJECT_STRUCTURE_INVALID";
    /** A legacy Project field has the wrong JSON value type. */
    public static final String PROJECT_VALUE_TYPE_INVALID = "PROJECT_VALUE_TYPE_INVALID";
    /** A Project object contains a field outside the supported legacy schema. */
    public static final String PROJECT_FIELD_UNSUPPORTED = "PROJECT_FIELD_UNSUPPORTED";
    /** A supported Project object repeats the same persisted member. */
    public static final String PROJECT_MEMBER_DUPLICATE = "PROJECT_MEMBER_DUPLICATE";
    /** A persisted assignment names a Slider Preset absent from the loaded catalog. */
    public static final String SLIDER_PRESET_ASSIGNMENT_MISSING = "SLIDER_PRESET_ASSIGNMENT_MISSING";
    /** A Slider Preset repeats the same slider-choice identity without regard to case. */
    public static final String SLIDER_CHOICE_NAME_DUPLICATE = "SLIDER_CHOICE_NAME_DUPLICATE";
    /** A Project relationship repeats a Slider Preset reference without regard to case. */
    public static final String SLIDER_PRESET_ASSIGNMENT_DUPLICATE = "SLIDER_PRESET_ASSIGNMENT_DUPLICATE";
    /** A Project file could not be written or installed on the local filesystem. */
    public static final String PROJECT_FILE_WRITE_FAILED = "PROJECT_FILE_WRITE_FAILED";
    /** An unexpected runtime failure prevented Project persistence. */
    public static final String PROJECT_SAVE_FAILED = "PROJECT_SAVE_FAILED";
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
    /** A slider-choice percentage is outside 0–100 or the minimum exceeds the maximum. */
    public static final String SLIDER_CHOICE_PERCENTAGE_INVALID = "SLIDER_CHOICE_PERCENTAGE_INVALID";
    /** A full-update edit omitted its required immutable Slider Preset value. */
    public static final String SLIDER_PRESET_VALUE_REQUIRED = "SLIDER_PRESET_VALUE_REQUIRED";
    /** A selected BodySlide XML source could not be read. */
    public static final String SLIDER_PRESET_XML_READ_FAILED = "SLIDER_PRESET_XML_READ_FAILED";
    /** A selected BodySlide source contains malformed XML syntax. */
    public static final String SLIDER_PRESET_XML_MALFORMED = "SLIDER_PRESET_XML_MALFORMED";
    /** A BodySlide XML document has an unsupported or incomplete structure. */
    public static final String SLIDER_PRESET_XML_STRUCTURE_INVALID = "SLIDER_PRESET_XML_STRUCTURE_INVALID";
    /** A BodySlide slider value cannot be represented as a legacy integer. */
    public static final String SLIDER_PRESET_XML_VALUE_INVALID = "SLIDER_PRESET_XML_VALUE_INVALID";
    /** An unexpected parser or runtime failure prevented one XML source import. */
    public static final String SLIDER_PRESET_XML_IMPORT_FAILED = "SLIDER_PRESET_XML_IMPORT_FAILED";
    /** A Custom Morph Target name is null, empty, or whitespace-only after trimming. */
    public static final String CUSTOM_MORPH_TARGET_NAME_REQUIRED = "CUSTOM_MORPH_TARGET_NAME_REQUIRED";
    /** A Custom Morph Target name duplicates another Project name without regard to case. */
    public static final String CUSTOM_MORPH_TARGET_NAME_DUPLICATE = "CUSTOM_MORPH_TARGET_NAME_DUPLICATE";
    /** A requested Custom Morph Target does not exist in the active Project. */
    public static final String CUSTOM_MORPH_TARGET_NOT_FOUND = "CUSTOM_MORPH_TARGET_NOT_FOUND";
    /** An NPC-add edit omitted the copied source value required for promotion. */
    public static final String NPC_MORPH_ASSIGNMENT_REQUIRED = "NPC_MORPH_ASSIGNMENT_REQUIRED";
    /** An NPC-add edit duplicates an existing plugin-name/editor-ID identity. */
    public static final String NPC_MORPH_ASSIGNMENT_DUPLICATE = "NPC_MORPH_ASSIGNMENT_DUPLICATE";
    /** A requested NPC Morph Assignment identity does not exist in the Project. */
    public static final String NPC_MORPH_ASSIGNMENT_NOT_FOUND = "NPC_MORPH_ASSIGNMENT_NOT_FOUND";

    private ProjectDiagnosticCodes() {
    }
}
