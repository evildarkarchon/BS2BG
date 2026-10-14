package com.asdasfa.jbs2bg.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.util.Subscription;

/**
 * Test-owned {@link AppearancePreferencesSource}: the Workbench reads these preferences instead of the host's OS
 * theme, High Contrast and motion settings, so test results never depend on the developer's desktop.
 *
 * <p>Like the JavaFX source, it is confined to the JavaFX Application Thread: call {@link #publish} there, because
 * subscribers re-render the loaded Workbench synchronously.
 */
public final class FakeAppearancePreferencesSource implements AppearancePreferencesSource {
    private final List<Runnable> subscribers = new ArrayList<>();
    private WorkbenchAppearance.PlatformPreferences current;

    /**
     * Creates a source reporting {@code initial} until the first {@link #publish}.
     *
     * @param initial preferences the Workbench reads when it attaches
     */
    public FakeAppearancePreferencesSource(WorkbenchAppearance.PlatformPreferences initial) {
        current = Objects.requireNonNull(initial, "initial");
    }

    @Override
    public WorkbenchAppearance.PlatformPreferences current() {
        return current;
    }

    @Override
    public Subscription subscribe(Runnable onChange) {
        Objects.requireNonNull(onChange, "onChange");
        subscribers.add(onChange);
        return () -> subscribers.remove(onChange);
    }

    /**
     * Replaces the reported preferences and notifies every subscriber, as a live OS preference change does. Before
     * the Workbench attaches this only sets the preferences it starts with.
     *
     * @param next preferences reported from now on
     */
    public void publish(WorkbenchAppearance.PlatformPreferences next) {
        current = Objects.requireNonNull(next, "next");
        // Copy first: a subscriber may unsubscribe while being notified.
        for (Runnable subscriber : List.copyOf(subscribers))
            subscriber.run();
    }

    /** @return how many subscriptions are still open, so tests can prove the Workbench released them */
    public int subscriberCount() {
        return subscribers.size();
    }
}
