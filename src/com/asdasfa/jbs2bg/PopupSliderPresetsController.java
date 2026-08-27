package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.ProjectOutcome;

import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

/**
 * 
 * @author Totiman
 */
public class PopupSliderPresetsController extends CustomController {
	
	@FXML
	protected ListView<SliderPreset> lvPresets;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		lvPresets.setOnKeyTyped(new KeyNavigationListener() {
			@Override
			public void test() {
				for (int i = 0; i < lvPresets.getItems().size(); i++) {
					SliderPreset item = lvPresets.getItems().get(i);
					if (item.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						lvPresets.getSelectionModel().select(i);
						lvPresets.getFocusModel().focus(i);
						
						boolean indexVisible = MyUtils.isIndexVisible(lvPresets, i);
						if (!indexVisible)
							lvPresets.scrollTo(i);
						
						found = true;
						break;
					}
				}
			}
		});
	}
	
	@Override
	protected void onPostInit() {
		lvPresets.setCellFactory(p ->
			new ListCell<SliderPreset>() {
				@Override
				protected void updateItem(SliderPreset item, boolean empty) {
					super.updateItem(item, empty);
					if (empty || item == null) {
						setText(null);
						setGraphic(null);
					} else {
						setText(item.getName());
					}
				}
			}
		);
		
		stage.setOnShowing(e -> {
			SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
			if (preset != null) {
				int index = lvPresets.getItems().indexOf(preset);
				boolean indexVisible = MyUtils.isIndexVisible(lvPresets, index);
				
				if (!indexVisible)
					lvPresets.scrollTo(index);
			} else {
			}
		});
	}
	
	protected void connectViews() {
		lvPresets.setItems(main.projectPresentation.getSliderPresets());
	}
	
	protected void disconnectViews() {
		lvPresets.setItems(null);
	}
	
	/**
	 * Assigns the selected Slider Preset to the currently selected Project target
	 * through one explicit Project edit.
	 */
	@FXML
	private void addPresetToTarget() {
		SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		String presetName = preset.getName();
		ProjectOutcome outcome = main.mainController.addSliderPresetToCurrentTarget(presetName);
		if (outcome instanceof ChangedOutcome)
			selectTargetPreset(presetName);
		
		lvPresets.requestFocus();
	}

	/**
	 * Restores navigation to an assigned Slider Preset after a changed snapshot
	 * rebuilds the selected target's presentation list.
	 *
	 * @param presetName canonical assigned Slider Preset name
	 */
	private void selectTargetPreset(String presetName) {
		if (main.mainController.lvTargetPresets.getItems() == null)
			return;
		for (SliderPreset targetPreset : main.mainController.lvTargetPresets.getItems()) {
			if (targetPreset.getName().equalsIgnoreCase(presetName)) {
				int index = main.mainController.lvTargetPresets.getItems().indexOf(targetPreset);
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
