package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.etc.MyUtils;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class PopupRenameController extends CustomController {
	
	@FXML
	private TextField tfRename;
	@FXML
	private Label lblRenameWarning;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	@Override
	protected void onPostInit() {
		stage.setOnShown(e -> {
			String name = main.mainController.lvPresets.getSelectionModel().getSelectedItem().getName();
			stage.setTitle("Rename: " + name);
			lblRenameWarning.setText("");
			
			tfRename.setText(name);
			
			tfRename.requestFocus();
		});
	}
	
	/**
	 * Requests a validated Slider Preset rename and reselects the rebuilt
	 * presentation value only when the Project changed.
	 */
	@FXML
	private void rename() {
		SliderPreset selectedPreset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
		if (selectedPreset == null)
			return;

		SliderPreset selectedTargetPreset = main.mainController.lvTargetPresets.getSelectionModel().getSelectedItem();
		boolean restoreTargetPreset = selectedTargetPreset != null
				&& selectedTargetPreset.getName().equalsIgnoreCase(selectedPreset.getName());
		String newName = tfRename.getText();
		ProjectOutcome outcome = main.mainController
				.applyProjectEdit(SliderPresetEdits.rename(selectedPreset.getName(), newName));
		if (!(outcome instanceof ChangedOutcome)) {
			if (outcome.getDiagnostics().isEmpty())
				lblRenameWarning.setText("Name needs to be different than the old one!");
			else
				lblRenameWarning.setText(outcome.getDiagnostics().get(0).getMessage());
			return;
		}

		String canonicalName = newName.trim();
		main.mainController.selectSliderPreset(canonicalName);
		if (restoreTargetPreset)
			selectTargetPreset(canonicalName);
		stage.hide();
	}

	/**
	 * Restores the renamed assignment selection against the rebuilt current-target
	 * projection without mutating Project state.
	 *
	 * @param presetName canonical renamed Slider Preset name
	 */
	private void selectTargetPreset(String presetName) {
		if (main.mainController.lvTargetPresets.getItems() == null)
			return;
		for (SliderPreset preset : main.mainController.lvTargetPresets.getItems()) {
			if (preset.getName().equalsIgnoreCase(presetName)) {
				int index = main.mainController.lvTargetPresets.getItems().indexOf(preset);
				main.mainController.lvTargetPresets.getSelectionModel().select(index);
				main.mainController.lvTargetPresets.getFocusModel().focus(index);
				boolean indexVisible = MyUtils.isIndexVisible(main.mainController.lvTargetPresets, index);
				if (!indexVisible)
					main.mainController.lvTargetPresets.scrollTo(index);
				return;
			}
		}
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
}
