package com.asdasfa.jbs2bg.project;

/**
 * Creates explicit immutable edit requests for the Project's Slider Preset
 * catalog. Validation is performed atomically by {@link ProjectSession#apply(ProjectEdit)}.
 */
public final class SliderPresetEdits {
    private static final SliderPresetEdit CLEAR = new Clear();

    private SliderPresetEdits() {
    }

    /**
     * Requests creation of a non-UUNP Slider Preset seeded with the standard mode's
     * synthesized default choices, matching what a save/reopen cycle would produce.
     *
     * @param name requested Slider Preset name; the session trims and validates it
     * @return an immutable creation request
     */
    public static ProjectEdit create(String name) {
        return new Create(name);
    }

    /**
     * Requests duplication of one Slider Preset under a new logical name.
     *
     * @param sourceName existing Slider Preset name, compared without regard to case
     * @param duplicateName requested name for the independent copy
     * @return an immutable duplication request
     */
    public static ProjectEdit duplicate(String sourceName, String duplicateName) {
        return new Duplicate(sourceName, duplicateName);
    }

    /**
     * Requests replacement of one Slider Preset's complete observable value. The
     * session rejects replacements containing a blank slider-choice name, names that
     * repeat without regard to case, or percentages outside 0–100 or reversed. When the
     * replacement changes the UUNP flag, its synthesized defaults are rebuilt for
     * the requested mode exactly as {@link #setUunp(String, boolean)} would.
     *
     * @param currentName existing logical Slider Preset name
     * @param replacement requested immutable replacement value
     * @return an immutable full-update request
     */
    public static ProjectEdit update(String currentName, SliderPresetSnapshot replacement) {
        return new Update(currentName, replacement);
    }

    /**
     * Requests a display-name change while retaining the logical Slider Preset.
     *
     * @param currentName existing logical Slider Preset name
     * @param newName requested display name, including case-only changes
     * @return an immutable rename request
     */
    public static ProjectEdit rename(String currentName, String newName) {
        return new Rename(currentName, newName);
    }

    /**
     * Requests deletion of one Slider Preset by case-insensitive logical name.
     *
     * @param name existing Slider Preset name
     * @return an immutable deletion request
     */
    public static ProjectEdit delete(String name) {
        return new Delete(name);
    }

    /**
     * Requests removal of every Slider Preset from the active Project.
     *
     * @return the immutable clear request
     */
    public static ProjectEdit clear() {
        return CLEAR;
    }

    /**
     * Requests a change to whether a Slider Preset uses UUNP defaults. Like the
     * legacy toggle, the session discards every synthesized default choice and
     * rebuilds the requested mode's missing defaults; explicit choices are retained
     * with effective values re-resolved for absent stored endpoints.
     *
     * @param name existing Slider Preset name, compared without regard to case
     * @param uunp requested UUNP flag
     * @return an immutable UUNP request
     */
    public static ProjectEdit setUunp(String name, boolean uunp) {
        return new SetUunp(name, uunp);
    }

    /**
     * Requests replacement or addition of one immutable slider choice. The session
     * rejects choices whose name is blank, whose percentages are outside 0–100, or
     * whose minimum exceeds the maximum.
     *
     * @param presetName existing Slider Preset name, compared without regard to case
     * @param choice complete observable slider-choice value
     * @return an immutable slider-choice request
     */
    public static ProjectEdit setSliderChoice(String presetName, SliderChoiceSnapshot choice) {
        return new SetSliderChoice(presetName, choice);
    }

    /** Identifies the closed family of Slider Preset requests handled by the module. */
    interface SliderPresetEdit extends ProjectEdit {
    }

    /** Immutable creation request interpreted only by the ProjectSession module. */
    static final class Create implements SliderPresetEdit {
        private final String name;

        /**
         * Captures the raw requested name so validation remains inside ProjectSession.
         *
         * @param name caller-supplied Slider Preset name
         */
        Create(String name) {
            this.name = name;
        }

        /** @return the raw requested Slider Preset name */
        String getName() {
            return name;
        }
    }

    /** Immutable duplication request interpreted only by the ProjectSession module. */
    static final class Duplicate implements SliderPresetEdit {
        private final String sourceName;
        private final String duplicateName;

        /**
         * Captures source identity and the raw requested duplicate name.
         *
         * @param sourceName existing Slider Preset name
         * @param duplicateName caller-supplied duplicate name
         */
        Duplicate(String sourceName, String duplicateName) {
            this.sourceName = sourceName;
            this.duplicateName = duplicateName;
        }

        /** @return the requested source Slider Preset name */
        String getSourceName() {
            return sourceName;
        }

        /** @return the raw requested duplicate name */
        String getDuplicateName() {
            return duplicateName;
        }
    }

    /** Immutable full-update request interpreted only by the ProjectSession module. */
    static final class Update implements SliderPresetEdit {
        private final String currentName;
        private final SliderPresetSnapshot replacement;

        /**
         * Captures the logical target name and complete replacement value.
         *
         * @param currentName existing Slider Preset name
         * @param replacement requested replacement, or null for session validation
         */
        Update(String currentName, SliderPresetSnapshot replacement) {
            this.currentName = currentName;
            this.replacement = replacement;
        }

        /** @return the requested current Slider Preset name */
        String getCurrentName() {
            return currentName;
        }

        /** @return the requested replacement, or null when omitted */
        SliderPresetSnapshot getReplacement() {
            return replacement;
        }
    }

    /** Immutable rename request interpreted only by the ProjectSession module. */
    static final class Rename implements SliderPresetEdit {
        private final String currentName;
        private final String newName;

        /**
         * Captures the logical target and raw requested display name.
         *
         * @param currentName existing Slider Preset name
         * @param newName caller-supplied replacement name
         */
        Rename(String currentName, String newName) {
            this.currentName = currentName;
            this.newName = newName;
        }

        /** @return the requested current Slider Preset name */
        String getCurrentName() {
            return currentName;
        }

        /** @return the raw requested display name */
        String getNewName() {
            return newName;
        }
    }

    /** Immutable deletion request interpreted only by the ProjectSession module. */
    static final class Delete implements SliderPresetEdit {
        private final String name;

        /**
         * Captures the requested case-insensitive Slider Preset identity.
         *
         * @param name existing Slider Preset name
         */
        Delete(String name) {
            this.name = name;
        }

        /** @return the requested Slider Preset name */
        String getName() {
            return name;
        }
    }

    /** Immutable clear request interpreted only by the ProjectSession module. */
    static final class Clear implements SliderPresetEdit {
        private Clear() {
        }
    }

    /** Immutable UUNP request interpreted only by the ProjectSession module. */
    static final class SetUunp implements SliderPresetEdit {
        private final String name;
        private final boolean uunp;

        /**
         * Captures the target identity and requested UUNP flag.
         *
         * @param name existing Slider Preset name
         * @param uunp requested UUNP flag
         */
        SetUunp(String name, boolean uunp) {
            this.name = name;
            this.uunp = uunp;
        }

        /** @return the requested Slider Preset name */
        String getName() {
            return name;
        }

        /** @return the requested UUNP flag */
        boolean isUunp() {
            return uunp;
        }
    }

    /** Immutable slider-choice request interpreted only by the ProjectSession module. */
    static final class SetSliderChoice implements SliderPresetEdit {
        private final String presetName;
        private final SliderChoiceSnapshot choice;

        /**
         * Captures the target identity and complete slider-choice value.
         *
         * @param presetName existing Slider Preset name
         * @param choice requested choice, or null for session validation
         */
        SetSliderChoice(String presetName, SliderChoiceSnapshot choice) {
            this.presetName = presetName;
            this.choice = choice;
        }

        /** @return the requested Slider Preset name */
        String getPresetName() {
            return presetName;
        }

        /** @return the requested slider choice, or null when omitted */
        SliderChoiceSnapshot getChoice() {
            return choice;
        }
    }
}
