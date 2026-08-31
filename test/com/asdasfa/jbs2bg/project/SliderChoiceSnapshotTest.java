package com.asdasfa.jbs2bg.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies immutable copy operations used by JavaFX slider-choice editors.
 */
class SliderChoiceSnapshotTest {

    /**
     * Ensures enabled-state copies retain every unrelated observable value.
     */
    @Test
    void withEnabledChangesOnlyEnabledState() {
        SliderChoiceSnapshot source = new SliderChoiceSnapshot("Waist", true, Integer.valueOf(20),
                Integer.valueOf(80), 20, 80, 25, 75, false);

        SliderChoiceSnapshot changed = source.withEnabled(false);

        assertEquals("Waist", changed.getName());
        assertFalse(changed.isEnabled());
        assertEquals(source.getStoredSmallValue(), changed.getStoredSmallValue());
        assertEquals(source.getStoredBigValue(), changed.getStoredBigValue());
        assertEquals(20, changed.getEffectiveSmallValue());
        assertEquals(80, changed.getEffectiveBigValue());
        assertEquals(25, changed.getPercentageMinimum());
        assertEquals(75, changed.getPercentageMaximum());
        assertFalse(changed.isMissingDefault());
    }

    /**
     * Ensures percentage copies retain nullable stored values and synthesized identity.
     */
    @Test
    void withPercentageRangeChangesOnlyRange() {
        SliderChoiceSnapshot source = new SliderChoiceSnapshot("Breasts", true, null, null, 0, 100,
                100, 100, true);

        SliderChoiceSnapshot changed = source.withPercentageRange(10, 90);

        assertEquals("Breasts", changed.getName());
        assertTrue(changed.isEnabled());
        assertFalse(changed.getStoredSmallValue().isPresent());
        assertFalse(changed.getStoredBigValue().isPresent());
        assertEquals(0, changed.getEffectiveSmallValue());
        assertEquals(100, changed.getEffectiveBigValue());
        assertEquals(10, changed.getPercentageMinimum());
        assertEquals(90, changed.getPercentageMaximum());
        assertTrue(changed.isMissingDefault());
    }

    /**
     * Ensures a synthesized default cannot claim a stored value on either endpoint.
     */
    @Test
    void synthesizedDefaultRejectsStoredValues() {
        assertThrows(IllegalArgumentException.class, () -> new SliderChoiceSnapshot("Waist", true,
                Integer.valueOf(20), null, 20, 100, 100, 100, true));
        assertThrows(IllegalArgumentException.class, () -> new SliderChoiceSnapshot("Waist", true, null,
                Integer.valueOf(80), 0, 80, 100, 100, true));
    }

    /**
     * Ensures the legacy explicit-null state stays representable: a persisted choice
     * may defer both values to defaults without becoming a synthesized default.
     */
    @Test
    void explicitChoiceMayDeferBothValuesToDefaults() {
        SliderChoiceSnapshot explicit = new SliderChoiceSnapshot("Waist", true, null, null, 0, 100, 100, 100,
                false);

        assertFalse(explicit.isMissingDefault());
        assertFalse(explicit.getStoredSmallValue().isPresent());
        assertFalse(explicit.getStoredBigValue().isPresent());
        assertEquals(0, explicit.getEffectiveSmallValue());
        assertEquals(100, explicit.getEffectiveBigValue());
    }
}
