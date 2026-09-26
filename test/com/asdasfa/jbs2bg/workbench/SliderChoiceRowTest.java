package com.asdasfa.jbs2bg.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesFeature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies local JavaFX Slider choice gestures against immutable Templates frames. */
class SliderChoiceRowTest {

    /** Restores repository Settings after each row preview fixture. */
    @AfterEach
    void restoreSettings() {
        SettingsTestSupport.restoreRepositorySettings();
    }

    /** A drag can overflow a finite starting preview and must still publish the range intent. */
    @Test
    void overflowingDragShowsUnavailablePreviewAndPublishesTheRange() throws Exception {
        SettingsTestSupport.installStandardOutput(Map.of("Overflow", Float.valueOf(Float.MAX_VALUE)), List.of());
        SliderChoiceSnapshot snapshot = new SliderChoiceSnapshot("Overflow", true, Integer.valueOf(10),
                Integer.valueOf(200), 10, 200, 0, 0, false);
        TemplatesFeature.ChoiceFrame choice = new TemplatesFeature.ChoiceFrame("Overflow", true, 0, 0,
                ProjectOutputFormatter.formatSliderChoicePreview(snapshot, false), false, false, false, snapshot);
        TemplatesFeature.EditorFrame editor = new TemplatesFeature.EditorFrame(NameIdentity.of("Alpha"),
                TemplatesFeature.Profile.STANDARD, List.of(choice),
                new TemplatesFeature.GangFrame(Optional.empty(), 100, 100, 100));

        FxTestToolkit.runOnFxThread(() -> {
            List<TemplatesFeature.Intent> intents = new ArrayList<>();
            SliderChoiceRow row = new SliderChoiceRow(intents::add);
            row.render(editor, choice, false);

            row.maximumControl().setValue(100.0);

            assertEquals("Preview unavailable", row.previewControl().getText());
            assertTrue(row.previewControl().getAccessibleHelp().contains("Preview unavailable"));
            TemplatesFeature.SetChoiceRange range = assertInstanceOf(TemplatesFeature.SetChoiceRange.class,
                    intents.getFirst());
            assertEquals(0, range.minimum());
            assertEquals(100, range.maximum());

            row.maximumControl().setValue(0.0);
            assertEquals(choice.previewText(), row.previewControl().getText());
            assertTrue(row.previewControl().getAccessibleHelp().contains(choice.previewText()));
        });
    }
}
