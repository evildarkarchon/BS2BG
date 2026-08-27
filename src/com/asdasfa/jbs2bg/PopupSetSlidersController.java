package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;

import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Edits one Slider Preset from immutable session snapshots. Each row gesture
 * submits one slider-choice edit, while every master gesture submits one atomic
 * full-preset edit regardless of the number of displayed choices.
 *
 * @author Totiman
 */
public class PopupSetSlidersController extends CustomController {

	@FXML
	private ScrollPane spSetSlidersList;
	@FXML
	private VBox vbSetSlidersList;
	@FXML
	private CheckBox cbAll;
	@FXML
	private CheckBox cbAllMin;
	@FXML
	private CheckBox cbAllMax;
	@FXML
	private TextField tfAll;
	@FXML
	private TextField tfAllMin;
	@FXML
	private TextField tfAllMax;
	@FXML
	private Slider sldAll;
	@FXML
	private Slider sldAllMin;
	@FXML
	private Slider sldAllMax;
	@FXML
	private Button btnBack;

	private final List<SetSliderControl> setSliderControls = new ArrayList<>();
	private SliderPresetSnapshot currentPreset;
	private boolean updatingMasterControls;

	/** Creates the FXML controller; application dependencies arrive in post-initialization. */
	public PopupSetSlidersController() {
	}

	/** {@inheritDoc} */
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}

	/** Installs popup lifecycle and master-control listeners after application injection. */
	@Override
	protected void onPostInit() {
		double scrollSpeedMultiplier = 2;
		vbSetSlidersList.setOnScroll(event -> {
			double height = spSetSlidersList.getContent().getBoundsInLocal().getHeight();
			if (height > 0) {
				double deltaY = event.getDeltaY() * scrollSpeedMultiplier;
				spSetSlidersList.setVvalue(spSetSlidersList.getVvalue() - deltaY / height);
			}
		});

		stage.setOnShown(event -> onShown());
		stage.setOnHidden(event -> {
			currentPreset = null;
			setSliderControls.clear();
			vbSetSlidersList.getChildren().clear();
		});
		installMasterListeners();
	}

	/** Installs one atomic bulk-edit handler for every master-control gesture. */
	private void installMasterListeners() {
		cbAll.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (updatingMasterControls || currentPreset == null)
				return;
			int value = (int) sldAll.getValue();
			withMasterControlUpdate(() -> {
				if (newValue.booleanValue()) {
					cbAllMin.setSelected(false);
					cbAllMax.setSelected(false);
					sldAllMin.setValue(value);
					sldAllMax.setValue(value);
			}
				refreshMasterControlState();
			});
			refreshRowDisableState();
			submitBulkEdit(choice -> choice.withPercentageRange(value, value));
		});

		cbAllMin.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (updatingMasterControls || currentPreset == null)
				return;
			int value = (int) sldAllMin.getValue();
			withMasterControlUpdate(() -> {
				if (newValue.booleanValue())
					cbAll.setSelected(false);
				refreshMasterControlState();
			});
			refreshRowDisableState();
			submitBulkEdit(choice -> choice.withPercentageRange(
					Math.min(value, choice.getPercentageMaximum()), choice.getPercentageMaximum()));
		});

		cbAllMax.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (updatingMasterControls || currentPreset == null)
				return;
			int value = (int) sldAllMax.getValue();
			withMasterControlUpdate(() -> {
				if (newValue.booleanValue())
					cbAll.setSelected(false);
				refreshMasterControlState();
			});
			refreshRowDisableState();
			submitBulkEdit(choice -> choice.withPercentageRange(choice.getPercentageMinimum(),
					Math.max(value, choice.getPercentageMinimum())));
		});

		sldAll.valueProperty().addListener((observable, oldValue, newValue) -> {
			int value = newValue.intValue();
			tfAll.setText(value + "%");
			if (updatingMasterControls || currentPreset == null || !cbAll.isSelected())
				return;
			withMasterControlUpdate(() -> {
				sldAllMin.setValue(value);
				sldAllMax.setValue(value);
				tfAllMin.setText(value + "%");
				tfAllMax.setText(value + "%");
			});
			submitBulkEdit(choice -> choice.withPercentageRange(value, value));
		});

		sldAllMin.valueProperty().addListener((observable, oldValue, newValue) -> {
			int value = Math.min(newValue.intValue(), (int) sldAllMax.getValue());
			tfAllMin.setText(value + "%");
			if (updatingMasterControls || currentPreset == null || !cbAllMin.isSelected())
				return;
			if (value != newValue.intValue())
				withMasterControlUpdate(() -> sldAllMin.setValue(value));
			submitBulkEdit(choice -> choice.withPercentageRange(
					Math.min(value, choice.getPercentageMaximum()), choice.getPercentageMaximum()));
		});

		sldAllMax.valueProperty().addListener((observable, oldValue, newValue) -> {
			int value = Math.max(newValue.intValue(), (int) sldAllMin.getValue());
			tfAllMax.setText(value + "%");
			if (updatingMasterControls || currentPreset == null || !cbAllMax.isSelected())
				return;
			if (value != newValue.intValue())
				withMasterControlUpdate(() -> sldAllMax.setValue(value));
			submitBulkEdit(choice -> choice.withPercentageRange(choice.getPercentageMinimum(),
					Math.max(value, choice.getPercentageMinimum())));
		});
	}

	/** Pins the selected logical preset and renders rows from the latest immutable snapshot. */
	private void onShown() {
		SliderPresetSnapshot selectedPreset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
		if (selectedPreset == null)
			return;

		currentPreset = findPreset(main.projectPresentation.getSnapshot(), selectedPreset.getName());
		if (currentPreset == null)
			return;
		stage.setTitle("SetSliders: " + currentPreset.getName());
		btnBack.requestFocus();
		resetMasterControls();
		rebuildRows(currentPreset);
	}

	/** Restores the master controls to their neutral defaults without publishing edits. */
	private void resetMasterControls() {
		withMasterControlUpdate(() -> {
			int defaultValue = 100;
			cbAll.setSelected(false);
			cbAllMin.setSelected(false);
			cbAllMax.setSelected(false);
			sldAll.setValue(defaultValue);
			sldAllMin.setValue(defaultValue);
			sldAllMax.setValue(defaultValue);
			tfAll.setText(defaultValue + "%");
			tfAllMin.setText(defaultValue + "%");
			tfAllMax.setText(defaultValue + "%");
			refreshMasterControlState();
		});
		refreshRowDisableState();
	}

	/** Rebuilds row controls from one coherent preset snapshot. */
	private void rebuildRows(SliderPresetSnapshot preset) {
		setSliderControls.clear();
		vbSetSlidersList.getChildren().clear();
		for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
			SetSliderControl control = new SetSliderControl(main, preset.getName(), preset.isUunp(), choice,
					this::acceptPublishedPreset);
			setSliderControls.add(control);
			vbSetSlidersList.getChildren().add(control);
		}
		refreshRowDisableState();
	}

	/**
	 * Keeps the next bulk edit based on the exact snapshot returned by a row edit,
	 * or closes the popup when that snapshot no longer contains the pinned preset.
	 */
	private void acceptPublishedPreset(SliderPresetSnapshot publishedPreset) {
		if (publishedPreset == null) {
			currentPreset = null;
			stage.hide();
			return;
		}
		currentPreset = publishedPreset;
	}

	/**
	 * Applies one complete preset replacement so a master gesture publishes no
	 * intermediate per-row states.
	 *
	 * @param transform pure transformation applied to every latest choice value
	 */
	private void submitBulkEdit(UnaryOperator<SliderChoiceSnapshot> transform) {
		if (currentPreset == null)
			return;
		List<SliderChoiceSnapshot> changedChoices = new ArrayList<>(currentPreset.getSliderChoices().size());
		for (SliderChoiceSnapshot choice : currentPreset.getSliderChoices())
			changedChoices.add(transform.apply(choice));
		SliderPresetSnapshot replacement = new SliderPresetSnapshot(currentPreset.getName(), currentPreset.isUunp(),
				changedChoices);
		ProjectOutcome outcome = main.mainController.applyProjectEdit(
				SliderPresetEdits.update(currentPreset.getName(), replacement));
		synchronizeRows(outcome);
	}

	/** Re-renders every row from the exact preset carried by a bulk-edit outcome. */
	private void synchronizeRows(ProjectOutcome outcome) {
		SliderPresetSnapshot publishedPreset = findPreset(outcome.getSnapshot(), currentPreset.getName());
		if (publishedPreset == null) {
			currentPreset = null;
			stage.hide();
			return;
		}
		currentPreset = publishedPreset;
		for (SetSliderControl control : setSliderControls) {
			SliderChoiceSnapshot choice = findChoice(publishedPreset, control.getChoiceName());
			if (choice != null)
				control.render(choice, publishedPreset.isUunp());
		}
	}

	/** Updates row interactivity from the three mutually coordinated master modes. */
	private void refreshRowDisableState() {
		boolean disabled = cbAll.isSelected() || cbAllMin.isSelected() || cbAllMax.isSelected();
		for (SetSliderControl control : setSliderControls)
			control.setDisable(disabled);
	}

	/** Updates master slider/text enabled state without changing Project data. */
	private void refreshMasterControlState() {
		setMasterEnabled(sldAll, tfAll, cbAll.isSelected());
		setMasterEnabled(sldAllMin, tfAllMin, cbAllMin.isSelected());
		setMasterEnabled(sldAllMax, tfAllMax, cbAllMax.isSelected());
	}

	/** Applies one master enabled state to its slider and read-only percentage field. */
	private static void setMasterEnabled(Slider slider, TextField textField, boolean enabled) {
		slider.setDisable(!enabled);
		textField.setDisable(!enabled);
	}

	/** Runs a presentation-only master update while suppressing recursive edits. */
	private void withMasterControlUpdate(Runnable update) {
		boolean wasUpdating = updatingMasterControls;
		updatingMasterControls = true;
		try {
			update.run();
		} finally {
			updatingMasterControls = wasUpdating;
		}
	}

	/** Resolves a logical preset from one immutable Project snapshot. */
	private static SliderPresetSnapshot findPreset(ProjectSnapshot snapshot, String requestedName) {
		for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
			if (preset.getName().equalsIgnoreCase(requestedName))
				return preset;
		}
		return null;
	}

	/** Resolves a logical slider choice from one immutable preset snapshot. */
	private static SliderChoiceSnapshot findChoice(SliderPresetSnapshot preset, String requestedName) {
		for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
			if (choice.getName().equalsIgnoreCase(requestedName))
				return choice;
		}
		return null;
	}

	/** Hides the modal popup without changing its already-published Project state. */
	@FXML
	private void hide() {
		stage.hide();
	}

	/** Sets every choice range to zero in one Project edit. */
	@FXML
	private void zeroAll() {
		setAllValue(0);
	}

	/** Sets every choice range to fifty percent in one Project edit. */
	@FXML
	private void fiftyAll() {
		setAllValue(50);
	}

	/** Sets every choice range to one hundred percent in one Project edit. */
	@FXML
	private void hundredAll() {
		setAllValue(100);
	}

	/** Updates both master displays and atomically applies one exact range value. */
	private void setAllValue(int value) {
		if (currentPreset == null)
			return;
		withMasterControlUpdate(() -> {
			sldAll.setValue(value);
			sldAllMin.setValue(value);
			sldAllMax.setValue(value);
			tfAll.setText(value + "%");
			tfAllMin.setText(value + "%");
			tfAllMax.setText(value + "%");
		});
		submitBulkEdit(choice -> choice.withPercentageRange(value, value));
	}

	/** Sets every choice minimum to zero, clamped by its latest maximum. */
	@FXML
	private void zeroAllMin() {
		setAllMinimum(0);
	}

	/** Sets every choice minimum to fifty percent, clamped by its latest maximum. */
	@FXML
	private void fiftyAllMin() {
		setAllMinimum(50);
	}

	/** Sets every choice minimum to one hundred percent, clamped by its latest maximum. */
	@FXML
	private void hundredAllMin() {
		setAllMinimum(100);
	}

	/** Updates the minimum master display and applies one row-clamped bulk edit. */
	private void setAllMinimum(int value) {
		if (currentPreset == null)
			return;
		withMasterControlUpdate(() -> {
			sldAllMin.setValue(value);
			tfAllMin.setText(value + "%");
		});
		submitBulkEdit(choice -> choice.withPercentageRange(
				Math.min(value, choice.getPercentageMaximum()), choice.getPercentageMaximum()));
	}

	/** Sets every choice maximum to zero, clamped by its latest minimum. */
	@FXML
	private void zeroAllMax() {
		setAllMaximum(0);
	}

	/** Sets every choice maximum to fifty percent, clamped by its latest minimum. */
	@FXML
	private void fiftyAllMax() {
		setAllMaximum(50);
	}

	/** Sets every choice maximum to one hundred percent, clamped by its latest minimum. */
	@FXML
	private void hundredAllMax() {
		setAllMaximum(100);
	}

	/** Updates the maximum master display and applies one row-clamped bulk edit. */
	private void setAllMaximum(int value) {
		if (currentPreset == null)
			return;
		withMasterControlUpdate(() -> {
			sldAllMax.setValue(value);
			tfAllMax.setText(value + "%");
		});
		submitBulkEdit(choice -> choice.withPercentageRange(choice.getPercentageMinimum(),
				Math.max(value, choice.getPercentageMinimum())));
	}
}
