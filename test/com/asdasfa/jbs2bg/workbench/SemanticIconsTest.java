package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.fx.FxTestToolkit;

import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

class SemanticIconsTest {

    /**
     * Every selected bundled-vector key renders without a font or third-party runtime dependency.
     */
    @Test
    void everySemanticKeyRendersAsAnApplicationOwnedVector() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            for (SemanticIcons.IconKey key : SemanticIcons.IconKey.values()) {
                Node icon = SemanticIcons.create(key, true);

                assertInstanceOf(SVGPath.class, icon, key.name());
                assertTrue(icon.getStyleClass().contains("semantic-icon"), key.name());
                assertEquals(AccessibleRole.NODE, icon.getAccessibleRole(), key.name());
                assertNull(icon.getAccessibleText(), key.name());
                assertFalse(icon.isFocusTraversable(), key.name());
            }
        });
    }
}
