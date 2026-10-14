package com.asdasfa.jbs2bg.workbench;

import javafx.util.Subscription;

/**
 * The one BS2BG-owned seam for operating-system appearance preferences: color scheme, High Contrast, reduced motion
 * and the Windows system colors. The Workbench receives it through {@link WorkbenchEnvironment} and never reads the
 * live JavaFX preferences itself, so tests substitute a fake and never depend on the developer's desktop theme.
 *
 * <p>The production implementation is {@link JavaFxAppearancePreferencesSource}, the only class allowed to call
 * {@code Platform.getPreferences()} (production-ban rule 3 in {@code docs/build/workbench-platform.md}). This
 * interface deliberately does not expose {@code Platform.Preferences}, which is large and grows between JavaFX
 * releases.
 *
 * <p>Both operations are confined to the JavaFX Application Thread, and change notifications arrive there.
 */
public interface AppearancePreferencesSource {

    /**
     * Reads the current preferences as one JavaFX-independent value.
     *
     * @return the current operating-system appearance
     */
    WorkbenchAppearance.PlatformPreferences current();

    /**
     * Registers a callback for any appearance preference change. The callback receives no value: it re-reads
     * {@link #current()}, so several changes in one platform update still render one coherent frame each time.
     *
     * @param onChange invoked on the JavaFX Application Thread after a preference changes
     * @return handle whose {@link Subscription#unsubscribe()} stops further callbacks
     */
    Subscription subscribe(Runnable onChange);
}
