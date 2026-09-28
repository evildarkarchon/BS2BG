package com.asdasfa.jbs2bg;

/** Starts the real Application constructor in an isolated JVM with a caller-selected environment. */
public final class MainStartupProbe {
    private MainStartupProbe() {
    }

    /**
     * Constructs Preview and fails the process when its Settings pair is unavailable.
     *
     * @param arguments unused process arguments
     */
    public static void main(String[] arguments) {
        Main application = new Main();
        try {
            if (!application.settingsInitialization.isSuccessful())
                throw new IllegalStateException(application.settingsInitialization.getFailure()
                        .orElseThrow().formatForDisplay());
        } finally {
            application.stop();
        }
    }
}
