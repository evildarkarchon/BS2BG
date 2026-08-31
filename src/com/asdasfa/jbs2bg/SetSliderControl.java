package com.asdasfa.jbs2bg;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

/**
 * JavaFX editor for one immutable Slider Preset choice. User changes are sent
 * through {@link com.asdasfa.jbs2bg.project.ProjectSession}; this control never
 * mutates the legacy presentation-domain objects it renders.
 *
 * @author Totiman
 */
public class SetSliderControl extends VBox {

    private final Main main;
    private final String presetName;
    private final Consumer<SliderPresetSnapshot> publishedPresetConsumer;
    @FXML
    public CheckBox cbEnabled;
    @FXML
    public TextField tfBgFormat;
    @FXML
    public TextField tfMin;
    @FXML
    public TextField tfMax;
    @FXML
    public Slider sldMin;
    @FXML
    public Slider sldMax;
    private SliderChoiceSnapshot draft;
    private boolean uunp;
    private boolean updatingControls;

    /**
     * Creates a row bound to one logical preset and choice. The consumer receives
     * the exact preset snapshot returned by each completed single-row edit so the
     * owning popup can keep its bulk-edit base coherent.
     *
     * @param main                    application composition root
     * @param presetName              stable logical preset name targeted by row edits
     * @param uunp                    whether UUNP output rules format this choice
     * @param choice                  immutable choice value to render
     * @param publishedPresetConsumer receives the preset from each returned snapshot,
     *                                or {@code null} when the logical preset no longer exists
     * @throws RuntimeException when the FXML control cannot be loaded
     */
    // The custom-root FXML idiom hands `this` to the loader before construction completes; nothing
    // subclasses this control, so the escape is deliberate.
    @SuppressWarnings("this-escape")
    public SetSliderControl(Main main, String presetName, boolean uunp, SliderChoiceSnapshot choice,
                            Consumer<SliderPresetSnapshot> publishedPresetConsumer) {
        this.main = main;
        this.presetName = presetName;
        this.uunp = uunp;
        this.draft = choice;
        this.publishedPresetConsumer = publishedPresetConsumer;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("setslider_control.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
            getStylesheets().add(main.style);
            setPrefWidth(320);
            setPrefHeight(140);
            render(choice, uunp);
            installEditListeners();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves a logical preset from one coherent returned Project snapshot.
     */
    private static SliderPresetSnapshot findPreset(ProjectSnapshot snapshot, String requestedName) {
        for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
            if (preset.getName().equalsIgnoreCase(requestedName))
                return preset;
        }
        return null;
    }

    /**
     * Resolves a logical choice from an immutable preset snapshot.
     */
    private static SliderChoiceSnapshot findChoice(SliderPresetSnapshot preset, String requestedName) {
        for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
            if (choice.getName().equalsIgnoreCase(requestedName))
                return choice;
        }
        return null;
    }

    /**
     * Formats the BodyGen choice text directly from immutable effective values.
     */
    private static String formatChoice(SliderChoiceSnapshot choice, boolean uunp) {
        float small = choice.getEffectiveSmallValue() * 0.01f;
        float big = choice.getEffectiveBigValue() * 0.01f;
        boolean inverted = uunp ? Settings.isInvertedUUNP(choice.getName()) : Settings.isInverted(choice.getName());
        float multiplier = uunp ? Settings.getMultiplierUUNP(choice.getName()) : Settings.getMultiplier(choice.getName());
        if (inverted) {
            small = 1f - small;
            big = 1f - big;
        }

        float difference = big - small;
        float minimum = small + difference * (choice.getPercentageMinimum() * 0.01f);
        float maximum = small + difference * (choice.getPercentageMaximum() * 0.01f);
        minimum = roundToTwoDecimals(minimum * multiplier);
        maximum = roundToTwoDecimals(maximum * multiplier);
        if (minimum != maximum)
            return choice.getName() + "@" + minimum + ":" + maximum;
        return choice.getName() + "@" + maximum;
    }

    /**
     * Rounds output exactly as the legacy SetSlider formatter did.
     */
    private static float roundToTwoDecimals(float value) {
        return new BigDecimal(Float.toString(value)).setScale(2, RoundingMode.HALF_UP).floatValue();
    }

    /**
     * Installs listeners that translate row gestures into one explicit Project edit.
     */
    private void installEditListeners() {
        sldMin.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingControls)
                return;
            int rawMinimum = newValue.intValue();
            int requestedMinimum = Math.min(rawMinimum, draft.getPercentageMaximum());
            if (requestedMinimum != draft.getPercentageMinimum())
                submitChoice(draft.withPercentageRange(requestedMinimum, draft.getPercentageMaximum()));
            else {
                tfMin.setText(requestedMinimum + "%");
                if (rawMinimum != requestedMinimum)
                    setSliderValueWithoutEdit(sldMin, requestedMinimum);
            }
        });

        sldMax.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingControls)
                return;
            int rawMaximum = newValue.intValue();
            int requestedMaximum = Math.max(rawMaximum, draft.getPercentageMinimum());
            if (requestedMaximum != draft.getPercentageMaximum())
                submitChoice(draft.withPercentageRange(draft.getPercentageMinimum(), requestedMaximum));
            else {
                tfMax.setText(requestedMaximum + "%");
                if (rawMaximum != requestedMaximum)
                    setSliderValueWithoutEdit(sldMax, requestedMaximum);
            }
        });

        cbEnabled.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!updatingControls && draft.isEnabled() != newValue.booleanValue())
                submitChoice(draft.withEnabled(newValue.booleanValue()));
        });

        // Page/Home/End would also scroll the containing pane, so keep those keys local.
        sldMin.addEventFilter(KeyEvent.ANY, this::consumeScrollingSliderKey);
        sldMax.addEventFilter(KeyEvent.ANY, this::consumeScrollingSliderKey);
    }

    /**
     * Consumes slider navigation keys that otherwise conflict with popup scrolling.
     */
    private void consumeScrollingSliderKey(KeyEvent keyEvent) {
        KeyCode code = keyEvent.getCode();
        if (code == KeyCode.PAGE_DOWN || code == KeyCode.PAGE_UP || code == KeyCode.HOME || code == KeyCode.END)
            keyEvent.consume();
    }

    /**
     * Restores a clamped slider thumb without recursively submitting another edit.
     */
    private void setSliderValueWithoutEdit(Slider slider, int value) {
        updatingControls = true;
        try {
            slider.setValue(value);
        } finally {
            updatingControls = false;
        }
    }

    /**
     * Applies one complete immutable choice and then re-renders from the exact
     * snapshot returned by the session, including rejected and unchanged outcomes.
     *
     * @param requestedChoice complete requested choice value
     */
    private void submitChoice(SliderChoiceSnapshot requestedChoice) {
        ProjectOutcome outcome = main.mainController.applyProjectEdit(
                SliderPresetEdits.setSliderChoice(presetName, requestedChoice));
        SliderPresetSnapshot publishedPreset = findPreset(outcome.getSnapshot(), presetName);
        if (publishedPreset == null) {
            publishedPresetConsumer.accept(null);
            return;
        }

        SliderChoiceSnapshot publishedChoice = findChoice(publishedPreset, requestedChoice.getName());
        if (publishedChoice != null)
            render(publishedChoice, publishedPreset.isUunp());
        publishedPresetConsumer.accept(publishedPreset);
    }

    /**
     * Renders a session-published value without recursively submitting JavaFX
     * property changes as new edits.
     *
     * @param choice        immutable choice to display
     * @param requestedUunp whether UUNP formatting rules apply
     */
    public void render(SliderChoiceSnapshot choice, boolean requestedUunp) {
        updatingControls = true;
        try {
            draft = choice;
            uunp = requestedUunp;
            cbEnabled.setSelected(choice.isEnabled());
            sldMin.setValue(choice.getPercentageMinimum());
            sldMax.setValue(choice.getPercentageMaximum());
            tfMin.setText(choice.getPercentageMinimum() + "%");
            tfMax.setText(choice.getPercentageMaximum() + "%");
            tfBgFormat.setText(formatChoice(choice, requestedUunp));
        } finally {
            updatingControls = false;
        }
    }

    /**
     * @return the stable slider-choice name rendered by this row
     */
    public String getChoiceName() {
        return draft.getName();
    }
}
