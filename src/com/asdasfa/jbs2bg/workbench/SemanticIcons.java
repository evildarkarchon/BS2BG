package com.asdasfa.jbs2bg.workbench;

import java.util.Locale;
import java.util.Objects;

import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

/**
 * Application-owned bundled-vector implementation of the Workbench semantic icon catalog.
 */
public final class SemanticIcons {

    private SemanticIcons() {
    }

    /**
     * Creates a fresh public-JavaFX vector for one semantic request.
     *
     * @param key        semantic icon identity
     * @param decorative true when adjacent text already names the action or state
     * @return a new toolkit node with no font or external runtime dependency
     */
    public static Node create(IconKey key, boolean decorative) {
        IconKey resolvedKey = Objects.requireNonNull(key, "key");
        SVGPath icon = new SVGPath();
        icon.setContent(resolvedKey.svgPath());
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
