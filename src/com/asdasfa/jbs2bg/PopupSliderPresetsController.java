package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

/**
 * 
 * @author Totiman
 */
public class PopupSliderPresetsController extends CustomController {
	
	@FXML
	protected ListView<SliderPresetSnapshot> lvPresets;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		lvPresets.setOnKeyTyped(new KeyNavigationListener() {
			@Override
			public void test() {
				for (int i = 0; i < lvPresets.getItems().size(); i++) {
					SliderPresetSnapshot item = lvPresets.getItems().get(i);
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
			new ListCell<SliderPresetSnapshot>() {
				@Override
				protected void updateItem(SliderPresetSnapshot item, boolean empty) {
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
			SliderPresetSnapshot preset = lvPresets.getSelectionModel().getSelectedItem();
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
	
	/**
	 * Assigns the selected Slider Preset to the currently selected Project target
	 * through one explicit Project edit.
	 */
	@FXML
	private void addPresetToTarget() {
		SliderPresetSnapshot preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		String presetName = preset.getName();
		ProjectOutcome outcome = main.mainController.addSliderPresetToCurrentTarget(presetName);
		if (outcome instanceof ChangedOutcome)
			main.mainController.selectTargetPreset(presetName);
		
		lvPresets.requestFocus();
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
}
