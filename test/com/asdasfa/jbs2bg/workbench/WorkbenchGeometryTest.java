package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorkbenchGeometryTest {

    /**
     * Client minimums are converted from measured decorations rather than fixed platform guesses.
     */
    @Test
    void measuredDecorationsProduceAnEightHundredBySixHundredClientMinimum() {
        assertEquals(830.0, WorkbenchGeometry.minimumWindowWidth(1100.0, 1070.0));
        assertEquals(640.0, WorkbenchGeometry.minimumWindowHeight(720.0, 680.0));
        assertEquals(800.0, WorkbenchGeometry.minimumWindowWidth(790.0, 800.0));
        assertEquals(600.0, WorkbenchGeometry.minimumWindowHeight(590.0, 600.0));
    }
}
