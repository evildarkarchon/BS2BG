package com.asdasfa.jbs2bg;

/**
 * Packaged entrypoint for the self-contained Windows app-image (issue #97).
 *
 * <p>This class deliberately does not extend {@code javafx.application.Application}. The JDK launcher
 * special-cases a main class that does (it reroutes startup through its FX helper, which must resolve
 * {@code javafx.graphics} as a module before any application code runs). A plain {@code main} keeps that
 * detour out of the packaged startup sequence: JavaFX is resolved exactly once, when {@link Main#main}
 * calls {@code Application.launch} against the boot layer of the bundled runtime. jpackage names this class
 * through {@code --main-class}; the jar manifest names it too so the staged jar also runs with {@code java -jar}.
 *
 * <p>It starts the existing sole application entrypoint, {@link Main}, and nothing else: there is no second
 * {@code Application}, no second Project flow, and no argument handling of its own.
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * Hands over to {@link Main#main(String[])} unchanged.
     *
     * @param args command-line arguments forwarded verbatim to the application
     */
    public static void main(String[] args) {
        Main.main(args);
    }
}
