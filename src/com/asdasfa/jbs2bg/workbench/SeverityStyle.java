package com.asdasfa.jbs2bg.workbench;

import java.util.Locale;
import java.util.Objects;

import javafx.scene.Node;

/**
 * The one severity style class a feedback node carries, shared by the shell's InfoBar and status line and every
 * Area's own InfoBar so their text, icon and boundary cues use the same classes. FX thread only.
 */
public final class SeverityStyle {

    /** Static helpers only. */
    private SeverityStyle() {
    }

    /**
     * Keeps one severity style class on a feedback node so text/icon/boundary cues stay synchronized.
     *
     * @param node     the feedback node
     * @param severity the severity it now shows
     * @throws NullPointerException when an argument is null
     */
    public static void apply(Node node, WorkbenchFeedback.Severity severity) {
        Objects.requireNonNull(severity, "severity");
        node.getStyleClass().removeAll("severity-information", "severity-validation", "severity-success",
                "severity-warning", "severity-failure");
        node.getStyleClass().add("severity-" + severity.name().toLowerCase(Locale.ROOT));
    }
}
