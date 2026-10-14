package com.asdasfa.jbs2bg.workbench;

import java.util.Locale;
import java.util.Objects;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.MapChangeListener;
import javafx.collections.WeakMapChangeListener;
import javafx.scene.paint.Color;
import javafx.util.Subscription;

/**
 * Production {@link AppearancePreferencesSource} over the public JavaFX
 * {@link Platform.Preferences} interface, including the documented Windows {@code Windows.SPI.HighContrast} and
 * system-color keys. It is the only production class allowed to call {@code Platform.getPreferences()}
 * (production-ban rule 3).
 *
 * <p>Construction does not touch the JavaFX platform; each operation reads {@code Platform.getPreferences()} when it
 * runs, on the JavaFX Application Thread.
 */
final class JavaFxAppearancePreferencesSource implements AppearancePreferencesSource {
    private static final String HIGH_CONTRAST = "Windows.SPI.HighContrast";
    private static final String WINDOW = "Windows.SysColor.COLOR_WINDOW";
    private static final String WINDOW_TEXT = "Windows.SysColor.COLOR_WINDOWTEXT";
    private static final String CONTROL = "Windows.SysColor.COLOR_3DFACE";
    private static final String CONTROL_TEXT = "Windows.SysColor.COLOR_BTNTEXT";
    private static final String HIGHLIGHT = "Windows.SysColor.COLOR_HIGHLIGHT";
    private static final String HIGHLIGHT_TEXT = "Windows.SysColor.COLOR_HIGHLIGHTTEXT";
    private static final String HOTLIGHT = "Windows.SysColor.COLOR_HOTLIGHT";
    private static final String GRAY_TEXT = "Windows.SysColor.COLOR_GRAYTEXT";

    @Override
    public WorkbenchAppearance.PlatformPreferences current() {
        return snapshot(Platform.getPreferences());
    }

    /**
     * Observes the preference map, color scheme and reduced-motion property through weak listeners, so the platform's
     * process-wide preferences never keep a closed Workbench reachable. The returned subscription holds the strong
     * listeners: the caller must keep it reachable for as long as it wants callbacks.
     */
    @Override
    public Subscription subscribe(Runnable onChange) {
        return new PreferenceSubscription(Platform.getPreferences(), Objects.requireNonNull(onChange, "onChange"));
    }

    /**
     * Converts the public JavaFX preference interface into the JavaFX-independent appearance value. Missing optional
     * Windows keys fall back to stable defaults, so non-Windows and headless platforms still render a frame.
     */
    private static WorkbenchAppearance.PlatformPreferences snapshot(Platform.Preferences value) {
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

    /**
     * Resolves one optional Windows color key and formats it as a stable opaque CSS value.
     */
    private static String color(Platform.Preferences value, String key, Color fallback) {
        Color resolved = value.getColor(key).orElse(fallback);
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Math.round(resolved.getRed() * 255),
                Math.round(resolved.getGreen() * 255),
                Math.round(resolved.getBlue() * 255));
    }

    /**
     * One registration on the live preferences. The fields keep the strong listeners alive while the subscriber
     * holds this object; the platform itself only sees their weak wrappers.
     */
    private static final class PreferenceSubscription implements Subscription {
        private final Platform.Preferences preferences;
        private final InvalidationListener invalidation;
        private final MapChangeListener<String, Object> mapChange;
        private final WeakInvalidationListener weakInvalidation;
        private final WeakMapChangeListener<String, Object> weakMapChange;

        /**
         * Registers the weak wrappers immediately.
         *
         * @param preferences live JavaFX preferences
         * @param onChange    subscriber callback for every observed change
         */
        PreferenceSubscription(Platform.Preferences preferences, Runnable onChange) {
            this.preferences = preferences;
            invalidation = observable -> onChange.run();
            mapChange = change -> onChange.run();
            weakInvalidation = new WeakInvalidationListener(invalidation);
            weakMapChange = new WeakMapChangeListener<>(mapChange);
            preferences.addListener(weakMapChange);
            preferences.colorSchemeProperty().addListener(weakInvalidation);
            preferences.reducedMotionProperty().addListener(weakInvalidation);
        }

        /** Removes all three registrations; later preference changes no longer reach the subscriber. */
        @Override
        public void unsubscribe() {
            preferences.removeListener(weakMapChange);
            preferences.colorSchemeProperty().removeListener(weakInvalidation);
            preferences.reducedMotionProperty().removeListener(weakInvalidation);
        }
    }
}
