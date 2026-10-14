package com.asdasfa.jbs2bg.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * The startup guard that keeps Tiers 1-2 on JavaFX's built-in headless platform (ADR-0008). It is exercised
 * with explicit property sets, so these tests never start a toolkit of their own.
 */
class FxTestToolkitTest {

    private static Properties properties(String... keyValues) {
        Properties properties = new Properties();
        for (int index = 0; index < keyValues.length; index += 2)
            properties.setProperty(keyValues[index], keyValues[index + 1]);
        return properties;
    }

    @Test
    void headlessSoftwarePlatformStartsWithoutAWarning() {
        assertEquals(Optional.empty(),
                FxTestToolkit.checkTestPlatform(properties("glass.platform", "headless", "prism.order", "sw")));
    }

    @Test
    void aDesktopGlassPlatformFailsAndNamesTheDesktopFxProfile() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> FxTestToolkit.checkTestPlatform(properties("glass.platform", "win", "prism.order", "sw")));
        assertTrue(failure.getMessage().contains("desktop-fx"), failure.getMessage());
        assertTrue(failure.getMessage().contains("glass.platform=win"), failure.getMessage());
    }

    @Test
    void aHardwarePipelineFailsEvenOnTheHeadlessPlatform() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> FxTestToolkit.checkTestPlatform(properties("glass.platform", "headless", "prism.order", "d3d")));
        assertTrue(failure.getMessage().contains("prism.order=d3d"), failure.getMessage());
        assertTrue(failure.getMessage().contains("desktop-fx"), failure.getMessage());
    }

    @Test
    void missingPlatformPropertiesFailBecauseJavaFxWouldPickTheDesktop() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> FxTestToolkit.checkTestPlatform(properties()));
        assertTrue(failure.getMessage().contains("glass.platform=<unset>"), failure.getMessage());
        assertTrue(failure.getMessage().contains("desktop-fx"), failure.getMessage());
    }

    @Test
    void theDesktopFxOptInWarnsOnceAndChecksNothingElse() {
        Optional<String> warning = FxTestToolkit.checkTestPlatform(properties("bs2bg.test.desktop", "true"));
        assertTrue(warning.isPresent());
        assertTrue(warning.get().contains("desktop-fx"), warning.get());
        assertEquals(1, warning.get().lines().count(), "the opt-in warning is a single line");
    }

    @Test
    void anyOtherDesktopFlagValueIsNotAnOptIn() {
        assertThrows(AssertionError.class, () -> FxTestToolkit.checkTestPlatform(
                properties("bs2bg.test.desktop", "yes", "glass.platform", "win")));
    }

    @Test
    void activeAccessibilityOnTheHeadlessPlatformTripsTheAfterEachTripwire() {
        AssertionError failure = assertThrows(AssertionError.class, () -> FxTestToolkit.checkAccessibilityTripwire(
                properties("glass.platform", "headless", "prism.order", "sw"), true));
        assertTrue(failure.getMessage().contains("accessibility"), failure.getMessage());
    }

    @Test
    void inactiveAccessibilityOnTheHeadlessPlatformPassesTheTripwire() {
        FxTestToolkit.checkAccessibilityTripwire(properties("glass.platform", "headless", "prism.order", "sw"), false);
    }

    @Test
    void aDeliberateDesktopRunMayHaveAccessibilityActive() {
        // A screen reader on the developer's desktop legitimately activates it under -Pdesktop-fx.
        FxTestToolkit.checkAccessibilityTripwire(properties("bs2bg.test.desktop", "true"), true);
    }
}
