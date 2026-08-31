package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;

/**
 * Owns JavaFX-independent Workbench theme selection, live platform state, and resolved Fluent tokens.
 */
public final class WorkbenchAppearance {

    private static final Palette LIGHT_PALETTE = new Palette(
            "#F3F3F3", "#1B1B1B", "#FFFFFF", "#767676",
            "#DCEEFF", "#1B1B1B", "#005A9E", "#767676",
            "#005A9E", "#FFFFFF", "#005A9E", "#0F6B3B", "#8A4B00", "#C42B1C");
    private static final Palette DARK_PALETTE = new Palette(
            "#202020", "#FFFFFF", "#2B2B2B", "#8A8A8A",
            "#004B67", "#FFFFFF", "#60CDFF", "#9A9A9A",
            "#60CDFF", "#002A3A", "#60CDFF", "#6CCB5F", "#FCE100", "#FF99A4");
    private ThemeChoice selectedTheme;
    private PlatformPreferences platformPreferences;
    private Frame frame;

    /**
     * Creates appearance state from one persisted selection and current platform preferences.
     *
     * @param selectedTheme       persisted System, Light, or Dark choice
     * @param platformPreferences current operating-system appearance
     */
    public WorkbenchAppearance(ThemeChoice selectedTheme, PlatformPreferences platformPreferences) {
        this.selectedTheme = Objects.requireNonNull(selectedTheme, "selectedTheme");
        this.platformPreferences = Objects.requireNonNull(platformPreferences, "platformPreferences");
        frame = resolve();
    }

    /**
     * Normalizes the shared non-blank invariant for semantic CSS token values.
     */
    private static String requireCssColor(String value, String name) {
        String color = Objects.requireNonNull(value, name).trim();
        if (color.isEmpty())
            throw new IllegalArgumentException(name + " must not be blank");
        return color;
    }

    /**
     * Windows system colors required by the High Contrast token path.
     */
    public record SystemColors(String window, String windowText, String control, String controlText,
                               String highlight, String highlightText, String hotlight, String grayText) {
        /** Rejects missing CSS colors before a partial High Contrast frame can be published. */
        public SystemColors {
            window = requireCssColor(window, "window");
            windowText = requireCssColor(windowText, "windowText");
            control = requireCssColor(control, "control");
            controlText = requireCssColor(controlText, "controlText");
            highlight = requireCssColor(highlight, "highlight");
            highlightText = requireCssColor(highlightText, "highlightText");
            hotlight = requireCssColor(hotlight, "hotlight");
            grayText = requireCssColor(grayText, "grayText");
        }

        /** @return stable fallback system colors used only when the platform omits an optional color */
        public static SystemColors defaults () {
            return new SystemColors("#FFFFFF", "#000000", "#F0F0F0", "#000000",
                    "#0078D4", "#FFFFFF", "#0066CC", "#6D6D6D");
        }
    }

    /**
     * Application-owned semantic colors consumed by the Workbench stylesheet.
     */
    public record Palette(String background, String text, String surface, String border,
                          String selection, String selectionText, String focus, String disabledText,
                          String accent, String accentText, String information, String success, String warning, String failure) {
        /** Rejects missing token values before the palette reaches JavaFX CSS. */
        public Palette {
            background = requireCssColor(background, "background");
            text = requireCssColor(text, "text");
            surface = requireCssColor(surface, "surface");
            border = requireCssColor(border, "border");
            selection = requireCssColor(selection, "selection");
            selectionText = requireCssColor(selectionText, "selectionText");
            focus = requireCssColor(focus, "focus");
            disabledText = requireCssColor(disabledText, "disabledText");
            accent = requireCssColor(accent, "accent");
            accentText = requireCssColor(accentText, "accentText");
            information = requireCssColor(information, "information");
            success = requireCssColor(success, "success");
            warning = requireCssColor(warning, "warning");
            failure = requireCssColor(failure, "failure");
        }

        /** Builds the separate High Contrast path directly from live Windows system colors. */
        private static Palette highContrast (SystemColors colors){
            return new Palette(colors.window(), colors.windowText(), colors.control(), colors.controlText(),
                    colors.highlight(), colors.highlightText(), colors.hotlight(), colors.grayText(),
                    colors.highlight(), colors.highlightText(), colors.windowText(), colors.windowText(),
                    colors.windowText(), colors.windowText());
        }
    }

    /**
     * Platform appearance values observed through a public toolkit adapter.
     */
    public record PlatformPreferences(boolean systemDark, boolean highContrast, boolean reducedMotion,
                                      SystemColors systemColors) {
        /** Rejects a partial platform snapshot at construction time. */
        public PlatformPreferences {
            Objects.requireNonNull(systemColors, "systemColors");
        }

        /** @return a platform snapshot whose system color scheme is light */
        public static PlatformPreferences light () {
            return new PlatformPreferences(false, false, false, SystemColors.defaults());
        }

        /** @return a platform snapshot whose system color scheme is dark */
        public static PlatformPreferences dark () {
            return new PlatformPreferences(true, false, false, SystemColors.defaults());
        }

        /**
         * Copies this snapshot with the supplied live High Contrast state.
         *
         * @param enabled whether Windows High Contrast is active
         * @return an immutable updated snapshot
         */
        public PlatformPreferences withHighContrast ( boolean enabled){
            return new PlatformPreferences(systemDark, enabled, reducedMotion, systemColors);
        }

        /**
         * Copies this snapshot with the supplied live reduced-motion state.
         *
         * @param enabled whether nonessential Workbench motion must be suppressed
         * @return an immutable updated snapshot
         */
        public PlatformPreferences withReducedMotion ( boolean enabled){
            return new PlatformPreferences(systemDark, highContrast, enabled, systemColors);
        }
    }

    /**
     * Immutable appearance state rendered by adapters.
     */
    public record Frame(ThemeChoice selectedTheme, EffectiveTheme effectiveTheme, boolean reducedMotion,
                        Palette palette) {
        /** Rejects incomplete frames at construction time. */
        public Frame {
            Objects.requireNonNull(selectedTheme, "selectedTheme");
            Objects.requireNonNull(effectiveTheme, "effectiveTheme");
            Objects.requireNonNull(palette, "palette");
        }
    }

    /**
     * @return the latest completely resolved immutable appearance frame
     */
    public Frame frame() {
        return frame;
    }

    /**
     * Applies one live operating-system appearance update without changing the persisted user choice.
     *
     * @param preferences current platform appearance
     * @return the newly resolved frame
     */
    public Frame updatePlatformPreferences(PlatformPreferences preferences) {
        platformPreferences = Objects.requireNonNull(preferences, "preferences");
        frame = resolve();
        return frame;
    }

    /**
     * Applies one user-selected theme while retaining the latest live platform preferences.
     *
     * @param choice System, Light, or Dark selection to persist and render
     * @return the newly resolved frame
     */
    public Frame selectTheme(ThemeChoice choice) {
        selectedTheme = Objects.requireNonNull(choice, "choice");
        frame = resolve();
        return frame;
    }

    /**
     * Resolves the effective theme without exposing platform rules to JavaFX callers.
     */
    private Frame resolve() {
        if (platformPreferences.highContrast())
            return new Frame(selectedTheme, EffectiveTheme.HIGH_CONTRAST, platformPreferences.reducedMotion(),
                    Palette.highContrast(platformPreferences.systemColors()));
        EffectiveTheme effectiveTheme = switch (selectedTheme) {
            case SYSTEM -> platformPreferences.systemDark() ? EffectiveTheme.DARK : EffectiveTheme.LIGHT;
            case LIGHT -> EffectiveTheme.LIGHT;
            case DARK -> EffectiveTheme.DARK;
        };
        Palette palette = effectiveTheme == EffectiveTheme.DARK ? DARK_PALETTE : LIGHT_PALETTE;
        return new Frame(selectedTheme, effectiveTheme, platformPreferences.reducedMotion(), palette);
    }

    /**
     * User-selected theme behavior.
     */
    public enum ThemeChoice {
        SYSTEM("System"),
        LIGHT("Light"),
        DARK("Dark");

        private final String displayName;

        ThemeChoice(String displayName) {
            this.displayName = displayName;
        }

        /**
         * @return stable title-case text used by the Workbench theme selector
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Theme actually rendered after resolving the user choice against platform preferences.
     */
    public enum EffectiveTheme {
        LIGHT,
        DARK,
        HIGH_CONTRAST
    }
}
