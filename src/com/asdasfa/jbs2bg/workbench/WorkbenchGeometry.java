package com.asdasfa.jbs2bg.workbench;

/** Converts accepted logical client geometry into window bounds using live JavaFX measurements. */
public final class WorkbenchGeometry {

    /** Accepted minimum Workbench client width in logical pixels. */
    public static final double MINIMUM_CLIENT_WIDTH = 800.0;

    /** Accepted minimum Workbench client height in logical pixels. */
    public static final double MINIMUM_CLIENT_HEIGHT = 600.0;

    private WorkbenchGeometry() {
    }

    /**
     * Calculates the outer-window minimum that preserves the accepted client width.
     *
     * @param measuredWindowWidth current outer window width
     * @param measuredClientWidth current JavaFX Scene width
     * @return minimum outer width with the measured non-client inset included
     */
    public static double minimumWindowWidth(double measuredWindowWidth, double measuredClientWidth) {
        return minimumWindowSize(measuredWindowWidth, measuredClientWidth, MINIMUM_CLIENT_WIDTH);
    }

    /**
     * Calculates the outer-window minimum that preserves the accepted client height.
     *
     * @param measuredWindowHeight current outer window height
     * @param measuredClientHeight current JavaFX Scene height
     * @return minimum outer height with the measured non-client inset included
     */
    public static double minimumWindowHeight(double measuredWindowHeight, double measuredClientHeight) {
        return minimumWindowSize(measuredWindowHeight, measuredClientHeight, MINIMUM_CLIENT_HEIGHT);
    }

    /** Applies one measured non-client inset without assuming decoration dimensions or DPI. */
    private static double minimumWindowSize(double measuredWindowSize, double measuredClientSize,
            double minimumClientSize) {
        if (!Double.isFinite(measuredWindowSize) || !Double.isFinite(measuredClientSize)
                || measuredWindowSize < 0.0 || measuredClientSize < 0.0) {
            throw new IllegalArgumentException("measured sizes must be finite and non-negative");
        }
        return minimumClientSize + Math.max(0.0, measuredWindowSize - measuredClientSize);
    }
}
