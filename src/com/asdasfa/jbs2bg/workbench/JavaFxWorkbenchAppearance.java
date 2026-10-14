package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.css.PseudoClass;
import javafx.scene.Parent;
import javafx.util.Subscription;

/**
 * Observes the operating-system appearance-preferences source and applies resolved Workbench tokens to one root
 * graph.
 */
final class JavaFxWorkbenchAppearance implements AutoCloseable {
    private static final PseudoClass LIGHT = PseudoClass.getPseudoClass("workbench-light");
    private static final PseudoClass DARK = PseudoClass.getPseudoClass("workbench-dark");
    private static final PseudoClass HIGH_CONTRAST_STYLE = PseudoClass.getPseudoClass("workbench-high-contrast");
    private static final PseudoClass REDUCED_MOTION = PseudoClass.getPseudoClass("workbench-reduced-motion");

    private final Parent root;
    private final WorkbenchAppearance appearance;
    private final AppearancePreferencesSource preferences;
    private final Consumer<WorkbenchAppearance.Frame> frameConsumer;
    // Holds the source's listeners for as long as this adapter is reachable; the JavaFX source registers only weak
    // wrappers with the platform, so dropping this adapter without close() still lets the Workbench be collected.
    private Subscription preferenceSubscription = Subscription.EMPTY;

    /**
     * Attaches one appearance model to a loaded Workbench root and the operating-system preferences source.
     *
     * @param workbenchRoot   loaded root whose token state is owned by this adapter
     * @param appearanceModel JavaFX-independent selected and effective appearance state
     * @param source          operating-system appearance preferences, observed from {@link #start()} until
     *                        {@link #close()}
     * @param consumer        renderer for user-facing theme and motion descriptions
     */
    JavaFxWorkbenchAppearance(Parent workbenchRoot, WorkbenchAppearance appearanceModel,
                              AppearancePreferencesSource source, Consumer<WorkbenchAppearance.Frame> consumer) {
        root = Objects.requireNonNull(workbenchRoot, "workbenchRoot");
        appearance = Objects.requireNonNull(appearanceModel, "appearanceModel");
        preferences = Objects.requireNonNull(source, "source");
        frameConsumer = Objects.requireNonNull(consumer, "consumer");
    }

    /**
     * Maps application-owned semantic tokens onto stable public Modena looked-up colors.
     */
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

    /**
     * Starts live preference observation and renders the current platform state immediately.
     */
    void start() {
        preferenceSubscription.unsubscribe();
        preferenceSubscription = preferences.subscribe(this::refresh);
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

    /** @return the most recently resolved appearance for a newly opened owned surface */
    WorkbenchAppearance.Frame frame() {
        return appearance.frame();
    }

    /**
     * Removes live listeners when the owning Workbench window is no longer present.
     */
    @Override
    public void close() {
        preferenceSubscription.unsubscribe();
        preferenceSubscription = Subscription.EMPTY;
    }

    /**
     * Re-reads the preferences source before publishing one coherent theme frame.
     */
    private void refresh() {
        apply(appearance.updatePlatformPreferences(preferences.current()));
    }

    /**
     * Applies pseudo-class state and looked-up colors together before notifying the controller renderer.
     */
    private void apply(WorkbenchAppearance.Frame frame) {
        applyTo(root, frame);
        frameConsumer.accept(frame);
    }

    /**
     * Applies the same live Workbench tokens to a flyout or owned viewer root in its separate JavaFX scene.
     *
     * @param surface surface root receiving the effective theme
     * @param frame resolved theme and palette to project
     */
    static void applyTo(Parent surface, WorkbenchAppearance.Frame frame) {
        surface.pseudoClassStateChanged(LIGHT, frame.effectiveTheme() == WorkbenchAppearance.EffectiveTheme.LIGHT);
        surface.pseudoClassStateChanged(DARK, frame.effectiveTheme() == WorkbenchAppearance.EffectiveTheme.DARK);
        surface.pseudoClassStateChanged(HIGH_CONTRAST_STYLE,
                frame.effectiveTheme() == WorkbenchAppearance.EffectiveTheme.HIGH_CONTRAST);
        surface.pseudoClassStateChanged(REDUCED_MOTION, frame.reducedMotion());
        surface.setStyle(cssTokens(frame.palette()));
    }
}
