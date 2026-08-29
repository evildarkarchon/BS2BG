package com.asdasfa.jbs2bg.workbench;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.MapChangeListener;
import javafx.collections.WeakMapChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.Parent;
import javafx.scene.paint.Color;

/** Observes public JavaFX platform preferences and applies resolved Workbench tokens to one root graph. */
final class JavaFxWorkbenchAppearance implements AutoCloseable {
    private static final String HIGH_CONTRAST = "Windows.SPI.HighContrast";
    private static final String WINDOW = "Windows.SysColor.COLOR_WINDOW";
    private static final String WINDOW_TEXT = "Windows.SysColor.COLOR_WINDOWTEXT";
    private static final String CONTROL = "Windows.SysColor.COLOR_3DFACE";
    private static final String CONTROL_TEXT = "Windows.SysColor.COLOR_BTNTEXT";
    private static final String HIGHLIGHT = "Windows.SysColor.COLOR_HIGHLIGHT";
    private static final String HIGHLIGHT_TEXT = "Windows.SysColor.COLOR_HIGHLIGHTTEXT";
    private static final String HOTLIGHT = "Windows.SysColor.COLOR_HOTLIGHT";
    private static final String GRAY_TEXT = "Windows.SysColor.COLOR_GRAYTEXT";

    private static final PseudoClass LIGHT = PseudoClass.getPseudoClass("workbench-light");
    private static final PseudoClass DARK = PseudoClass.getPseudoClass("workbench-dark");
    private static final PseudoClass HIGH_CONTRAST_STYLE = PseudoClass.getPseudoClass("workbench-high-contrast");
    private static final PseudoClass REDUCED_MOTION = PseudoClass.getPseudoClass("workbench-reduced-motion");

    private final Parent root;
    private final WorkbenchAppearance appearance;
    private final Platform.Preferences preferences;
    private final Consumer<WorkbenchAppearance.Frame> frameConsumer;
    private final InvalidationListener preferenceInvalidation = observable -> refresh();
    private final WeakInvalidationListener weakPreferenceInvalidation =
            new WeakInvalidationListener(preferenceInvalidation);
    private final MapChangeListener<String, Object> preferenceMapChange = change -> refresh();
    private final WeakMapChangeListener<String, Object> weakPreferenceMapChange =
            new WeakMapChangeListener<>(preferenceMapChange);

    /**
     * Attaches one appearance model to a loaded Workbench root and the live JavaFX preference map.
     *
     * @param workbenchRoot loaded root whose token state is owned by this adapter
     * @param appearanceModel JavaFX-independent selected and effective appearance state
     * @param consumer renderer for user-facing theme and motion descriptions
     */
    JavaFxWorkbenchAppearance(Parent workbenchRoot, WorkbenchAppearance appearanceModel,
            Consumer<WorkbenchAppearance.Frame> consumer) {
        root = Objects.requireNonNull(workbenchRoot, "workbenchRoot");
        appearance = Objects.requireNonNull(appearanceModel, "appearanceModel");
        frameConsumer = Objects.requireNonNull(consumer, "consumer");
        preferences = Platform.getPreferences();
    }

    /** Starts live preference observation and renders the current platform state immediately. */
    void start() {
        preferences.addListener(weakPreferenceMapChange);
        preferences.colorSchemeProperty().addListener(weakPreferenceInvalidation);
        preferences.reducedMotionProperty().addListener(weakPreferenceInvalidation);
        refresh();
    }

    /**
     * Applies one user theme choice without waiting for a platform preference change.
     *
     * @param choice System, Light, or Dark selection
     * @return newly rendered immutable appearance frame
     */
    WorkbenchAppearance.Frame selectTheme(WorkbenchAppearance.ThemeChoice choice) {
        WorkbenchAppearance.Frame frame = appearance.selectTheme(choice);
        apply(frame);
        return frame;
    }

    /** Removes live listeners when the owning Workbench window is no longer present. */
    @Override
    public void close() {
        preferences.removeListener(weakPreferenceMapChange);
        preferences.colorSchemeProperty().removeListener(weakPreferenceInvalidation);
        preferences.reducedMotionProperty().removeListener(weakPreferenceInvalidation);
    }

    /** Re-snapshots the public preference interface before publishing one coherent theme frame. */
    private void refresh() {
        apply(appearance.updatePlatformPreferences(snapshot(preferences)));
    }

    /** Applies pseudo-class state and looked-up colors together before notifying the controller renderer. */
    private void apply(WorkbenchAppearance.Frame frame) {
        root.pseudoClassStateChanged(LIGHT, frame.effectiveTheme() == WorkbenchAppearance.EffectiveTheme.LIGHT);
        root.pseudoClassStateChanged(DARK, frame.effectiveTheme() == WorkbenchAppearance.EffectiveTheme.DARK);
        root.pseudoClassStateChanged(HIGH_CONTRAST_STYLE,
                frame.effectiveTheme() == WorkbenchAppearance.EffectiveTheme.HIGH_CONTRAST);
        root.pseudoClassStateChanged(REDUCED_MOTION, frame.reducedMotion());
        root.setStyle(cssTokens(frame.palette()));
        frameConsumer.accept(frame);
    }

    /** Converts the public JavaFX preference interface into the JavaFX-independent appearance value. */
    static WorkbenchAppearance.PlatformPreferences snapshot(Platform.Preferences value) {
        Objects.requireNonNull(value, "value");
        WorkbenchAppearance.SystemColors colors = new WorkbenchAppearance.SystemColors(
                color(value, WINDOW, Color.WHITE), color(value, WINDOW_TEXT, Color.BLACK),
                color(value, CONTROL, Color.web("#F0F0F0")), color(value, CONTROL_TEXT, Color.BLACK),
                color(value, HIGHLIGHT, Color.web("#0078D4")), color(value, HIGHLIGHT_TEXT, Color.WHITE),
                color(value, HOTLIGHT, Color.web("#0066CC")), color(value, GRAY_TEXT, Color.web("#6D6D6D")));
        return new WorkbenchAppearance.PlatformPreferences(
                value.getColorScheme() == ColorScheme.DARK,
                value.getBoolean(HIGH_CONTRAST).orElse(false),
                value.isReducedMotion(), colors);
    }

    /** Resolves one optional Windows color key and formats it as a stable opaque CSS value. */
    private static String color(Platform.Preferences value, String key, Color fallback) {
        Color resolved = value.getColor(key).orElse(fallback);
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Math.round(resolved.getRed() * 255),
                Math.round(resolved.getGreen() * 255),
                Math.round(resolved.getBlue() * 255));
    }

    /** Maps application-owned semantic tokens onto stable public Modena looked-up colors. */
    private static String cssTokens(WorkbenchAppearance.Palette palette) {
        return ("-bs-background:%s;-bs-text:%s;-bs-surface:%s;-bs-border:%s;"
                + "-bs-selection:%s;-bs-selection-text:%s;-bs-focus:%s;-bs-disabled:%s;"
                + "-bs-accent:%s;-bs-accent-text:%s;-bs-information:%s;-bs-success:%s;"
                + "-bs-warning:%s;-bs-failure:%s;"
                + "-fx-base:-bs-surface;-fx-background:-bs-background;"
                + "-fx-control-inner-background:-bs-surface;-fx-text-base-color:-bs-text;"
                + "-fx-text-background-color:-bs-text;-fx-accent:-bs-accent;"
                + "-fx-focus-color:-bs-focus;-fx-faint-focus-color:transparent;")
                .formatted(palette.background(), palette.text(), palette.surface(), palette.border(),
                        palette.selection(), palette.selectionText(), palette.focus(), palette.disabledText(),
                        palette.accent(), palette.accentText(), palette.information(), palette.success(),
                        palette.warning(), palette.failure());
    }
}
