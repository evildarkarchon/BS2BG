package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.data.MorphTarget;
import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;

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
		lvPresets.setItems(main.data.sliderPresets);
	}
	
	protected void disconnectViews() {
		lvPresets.setItems(null);
	}
	
	@FXML
	private void addPresetToTarget() {
		SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		MorphTarget target = main.mainController.getCurrentTarget();
		if (target != null) {
			target.addSliderPreset(preset);
			target.sortPresets();
			
			int index = main.mainController.lvTargetPresets.getItems().indexOf(preset);
			main.mainController.lvTargetPresets.getSelectionModel().select(index);
			main.mainController.lvTargetPresets.getFocusModel().focus(index);
			
			boolean indexVisible = MyUtils.isIndexVisible(main.mainController.lvTargetPresets, index);
			if (!indexVisible)
				main.mainController.lvTargetPresets.scrollTo(index);
			
			main.mainController.updatePresetCounter();
			
			main.mainController.markChanged();
		}
		
		lvPresets.requestFocus();
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
}