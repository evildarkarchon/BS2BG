package com.asdasfa.jbs2bg.workbench;

import java.util.Locale;
import java.util.Objects;

import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

/** Application-owned bundled-vector implementation of the Workbench semantic icon catalog. */
public final class SemanticIcons {

    /** Stable semantic requests used by feature modules instead of glyph names or font code points. */
    public enum IconKey {
        TEMPLATES("Templates", "M4 2H14L20 8V22H4ZM14 2V8H20"),
        MORPHS("Morphs", "M12 2A5 5 0 1 0 12 12A5 5 0 1 0 12 2M4 22A8 8 0 0 1 20 22"),
        NPC_DATABASE("NPC Database", "M3 5A9 3 0 0 0 21 5V19A9 3 0 0 1 3 19ZM3 5V12A9 3 0 0 0 21 12"),
        OUTPUT("Output", "M12 2V16M7 11L12 16L17 11M4 20H20"),
        SETTINGS("Settings", "M12 2L14 5L18 4L20 8L17 10L18 14L14 15L12 18L10 15L6 14L7 10L4 8L6 4L10 5ZM12 9A3 3 0 1 0 12 15A3 3 0 1 0 12 9"),
        INFORMATION("Information", "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2M12 10V17M12 7V8"),
        SUCCESS("Success", "M3 12L9 18L21 5"),
        WARNING("Warning", "M12 2L22 21H2ZM12 8V14M12 17V18"),
        FAILURE("Failure", "M4 4L20 20M20 4L4 20"),
        VALIDATION("Validation", "M4 3H20V17H8L4 21ZM8 8H16M8 12H14"),
        ACTIVITY("Activity", "M3 13H7L10 6L14 18L17 11H21"),
        CONFIRMATION("Confirmation", "M12 2A10 10 0 1 0 12 22A10 10 0 1 0 12 2M8 12L11 15L17 9"),
        CANCEL("Cancel", "M5 5L19 19M19 5L5 19");

        private final String accessibleName;
        private final String svgPath;

        IconKey(String accessibleName, String svgPath) {
            this.accessibleName = accessibleName;
            this.svgPath = svgPath;
        }

        /** @return stable accessible name used when this icon is the only action label */
        public String accessibleName() {
            return accessibleName;
        }
    }

    private SemanticIcons() {
    }

    /**
     * Creates a fresh public-JavaFX vector for one semantic request.
     *
     * @param key semantic icon identity
     * @param decorative true when adjacent text already names the action or state
     * @return a new toolkit node with no font or external runtime dependency
     */
    public static Node create(IconKey key, boolean decorative) {
        IconKey resolvedKey = Objects.requireNonNull(key, "key");
        SVGPath icon = new SVGPath();
        icon.setContent(resolvedKey.svgPath);
        icon.getStyleClass().addAll("semantic-icon",
                "semantic-icon-" + resolvedKey.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        icon.setFocusTraversable(false);
        icon.setMouseTransparent(true);
        if (decorative) {
            icon.setAccessibleRole(AccessibleRole.NODE);
        } else {
            icon.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
            icon.setAccessibleText(resolvedKey.accessibleName());
        }
        return icon;
    }
}
