package com.asdasfa.jbs2bg.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * The application owns its dialog graphics: every semantic (information,
 * error, warning, confirmation) renders through public JavaFX shapes into an
 * image usable both as a stage icon and as an {@code ImageView} content, so
 * no private Modena resource path is needed.
 */
class DialogGraphicsTest {

    @Test
    void everySemanticRendersToAnOpaqueImageOfTheRequestedSize() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            for (DialogGraphics.Semantic semantic : DialogGraphics.Semantic.values()) {
                Image image = DialogGraphics.image(semantic, 64);
                assertEquals(64, image.getWidth(), semantic.name());
                assertEquals(64, image.getHeight(), semantic.name());
                Color centre = image.getPixelReader().getColor(32, 32);
                assertTrue(centre.getOpacity() > 0, semantic + " must paint its centre");
                Color corner = image.getPixelReader().getColor(0, 0);
                assertEquals(0, corner.getOpacity(), semantic + " must keep a transparent corner");
                assertNotSame(image, DialogGraphics.image(semantic, 64));
            }
        });
    }
}
