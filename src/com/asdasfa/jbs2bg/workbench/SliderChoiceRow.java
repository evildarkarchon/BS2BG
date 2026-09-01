package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;
import java.util.function.Consumer;

import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesFeature;

import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * JavaFX adapter for one immutable Slider choice row. It owns only control-local drag coalescing and translates each
 * completed gesture into a typed Templates intent; durable choice state remains in {@link TemplatesFeature}.
 */
final class SliderChoiceRow extends VBox {
    private final Consumer<TemplatesFeature.Intent> dispatcher;
    private final CheckBox enabled = new CheckBox();
    private final Label preview = new Label();
    private final Label defaultState = new Label();
    private final Slider minimum = percentageSlider();
    private final Slider maximum = percentageSlider();
    private final Label minimumValue = new Label();
    private final Label maximumValue = new Label();
    private TemplatesFeature.EditorFrame editor;
    private TemplatesFeature.ChoiceFrame choice;
    private boolean rendering;

    /**
     * Creates one reusable row and wires its controls to the typed intent dispatcher.
     *
     * @param dispatcher serialized Workbench Templates dispatcher
     */
    SliderChoiceRow(Consumer<TemplatesFeature.Intent> dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        getStyleClass().add("slider-choice-row");
        setAccessibleRole(AccessibleRole.PARENT);
        setSpacing(6.0);

        preview.getStyleClass().add("slider-choice-preview");
        preview.setWrapText(true);
        defaultState.getStyleClass().add("slider-choice-default-state");
        defaultState.setWrapText(true);
        HBox heading = new HBox(8.0, enabled, preview);
        heading.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(preview, Priority.ALWAYS);

        Label minimumLabel = new Label("Minimum");
        minimumLabel.setLabelFor(minimum);
        Label maximumLabel = new Label("Maximum");
        maximumLabel.setLabelFor(maximum);
        getChildren().addAll(heading, defaultState,
                rangeLine(minimumLabel, minimum, minimumValue),
                rangeLine(maximumLabel, maximum, maximumValue));

        enabled.selectedProperty().addListener((observable, previous, current) -> {
            if (!rendering && choice != null && choice.enabled() != current.booleanValue())
                dispatcher.accept(new TemplatesFeature.SetChoiceEnabled(choice.name(), current.booleanValue()));
        });
        installRangeEditing(minimum, true);
        installRangeEditing(maximum, false);
    }

    /**
     * Creates one conventional 0–100 percentage Slider without consuming Home, End, Page Up, or Page Down.
     */
    private static Slider percentageSlider() {
        Slider slider = new Slider(0.0, 100.0, 100.0);
        slider.setBlockIncrement(1.0);
        slider.setMajorTickUnit(20.0);
        slider.setMinorTickCount(4);
        slider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return slider;
    }

    /**
     * Builds one named range line with an independently reachable Slider and non-color percentage text.
     */
    private static HBox rangeLine(Label label, Slider slider, Label value) {
        value.setMinWidth(42.0);
        value.setAlignment(Pos.CENTER_RIGHT);
        HBox line = new HBox(8.0, label, slider, value);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    /**
     * Keeps pointer drags as truthful local previews and publishes exactly once when the gesture settles; keyboard
     * changes publish immediately through JavaFX's conventional Slider behavior.
     */
    private void installRangeEditing(Slider slider, boolean editsMinimum) {
        slider.valueProperty().addListener((observable, previous, current) -> {
            if (rendering || choice == null)
                return;
            int requested = current.intValue();
            int lower = editsMinimum ? Math.min(requested, (int) maximum.getValue()) : (int) minimum.getValue();
            int upper = editsMinimum ? (int) maximum.getValue() : Math.max(requested, (int) minimum.getValue());
            if ((editsMinimum && lower != requested) || (!editsMinimum && upper != requested)) {
                rendering = true;
                try {
                    slider.setValue(editsMinimum ? lower : upper);
                } finally {
                    rendering = false;
                }
            }
            renderDraft(lower, upper);
            if (!slider.isValueChanging())
                publishRange(lower, upper);
        });
        slider.valueChangingProperty().addListener((observable, previous, changing) -> {
            if (!rendering && !changing.booleanValue() && choice != null)
                publishRange((int) minimum.getValue(), (int) maximum.getValue());
        });
    }

    /**
     * Updates row-local percentage and exact BodyGen text during a pointer drag without publishing Project state.
     */
    private void renderDraft(int lower, int upper) {
        minimumValue.setText(lower + "%");
        maximumValue.setText(upper + "%");
        preview.setText(ProjectOutputFormatter.formatSliderChoicePreview(
                choice.snapshot().withPercentageRange(lower, upper),
                editor.profile() == TemplatesFeature.Profile.UUNP));
    }

    /**
     * Publishes one completed range only when it differs from the last authoritative feature frame.
     */
    private void publishRange(int lower, int upper) {
        if (lower != choice.minimum() || upper != choice.maximum())
            dispatcher.accept(new TemplatesFeature.SetChoiceRange(choice.name(), lower, upper));
    }

    /**
     * Re-renders every control from one committed immutable feature frame while preserving the existing JavaFX node,
     * keyboard focus, and UI Automation subtree.
     *
     * @param editorFrame current selected preset editor frame
     * @param choiceFrame current immutable row frame
     * @param mutationsBlocked whether coordinator admission currently blocks Project writes
     */
    void render(TemplatesFeature.EditorFrame editorFrame, TemplatesFeature.ChoiceFrame choiceFrame,
                boolean mutationsBlocked) {
        editor = Objects.requireNonNull(editorFrame, "editorFrame");
        choice = Objects.requireNonNull(choiceFrame, "choiceFrame");
        String presetName = editor.preset().getName();
        String rowName = "Slider choice " + choice.name() + " in Slider Preset " + presetName;
        rendering = true;
        try {
            setAccessibleText(rowName);
            enabled.setAccessibleText("Enable " + choice.name() + " in Slider Preset " + presetName);
            enabled.setSelected(choice.enabled());
            preview.setText(choice.previewText());
            preview.setAccessibleText(choice.name() + " BodyGen preview");
            String omission = choice.enabled()
                    ? "Included in generated output."
                    : "Omitted from generated output because this choice is disabled.";
            String persistence;
            if (choice.omittedFromProjectFile())
                persistence = " Synthesized profile default; omitted from the Project file until changed.";
            else if (choice.synthesizedDefault())
                persistence = " Changed profile default; persisted with default endpoints.";
            else
                persistence = " Explicit Project choice.";
            defaultState.setText(omission + persistence);
            preview.setAccessibleHelp(omission + " Exact preview if enabled: " + choice.previewText());
            minimum.setAccessibleText(choice.name() + " Minimum in Slider Preset " + presetName);
            maximum.setAccessibleText(choice.name() + " Maximum in Slider Preset " + presetName);
            minimum.setAccessibleHelp("Minimum " + choice.minimum() + " percent. Range 0 through 100.");
            maximum.setAccessibleHelp("Maximum " + choice.maximum() + " percent. Range 0 through 100.");
            minimum.setValue(choice.minimum());
            maximum.setValue(choice.maximum());
            minimumValue.setText(choice.minimum() + "%");
            maximumValue.setText(choice.maximum() + "%");
            boolean rowLocked = editor.gang().rowsLocked();
            enabled.setDisable(mutationsBlocked || rowLocked);
            minimum.setDisable(mutationsBlocked || rowLocked || !choice.enabled());
            maximum.setDisable(mutationsBlocked || rowLocked || !choice.enabled());
            setAccessibleHelp(omission + persistence + " Minimum " + choice.minimum()
                    + " percent; Maximum " + choice.maximum() + " percent.");
        } finally {
            rendering = false;
        }
    }

    /** @return stable case-preserving Slider choice name */
    String choiceName() {
        return choice.name();
    }

    /** @return first editable control used by semantic editor focus */
    CheckBox enabledControl() {
        return enabled;
    }

    /** @return exact BodyGen preview label for adapter and accessibility verification */
    Label previewControl() {
        return preview;
    }

    /** @return independently reachable Minimum Slider */
    Slider minimumControl() {
        return minimum;
    }

    /** @return independently reachable Maximum Slider */
    Slider maximumControl() {
        return maximum;
    }

    /** @return whether the supplied focus owner belongs to this row */
    boolean contains(javafx.scene.Node node) {
        return node == this || node == enabled || node == minimum || node == maximum || node == preview;
    }
}
