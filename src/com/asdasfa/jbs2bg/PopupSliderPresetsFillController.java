package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;

/**
 * 
 * @author Totiman
 */
public class PopupSliderPresetsFillController extends CustomController {
	
	@FXML
	protected ListView<SliderPreset> lvPresets;
	
	private CustomNotif notif;
	
	private CustomConfirm confirmFillEmpty;
	
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
						//lvPresets.getSelectionModel().select(i);
						lvPresets.getSelectionModel().clearAndSelect(i);
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
		
		lvPresets.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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
			lvPresets.getSelectionModel().clearSelection();
		});
		
		notif = new CustomNotif(main);
		notif.setOwner(stage);
		
		confirmFillEmpty = new CustomConfirm(main) {
			@Override
			public void ok() {
				FilteredList<NPC> filteredNpcs = main.mainController.npcTableFilter.getFilteredList();
				ObservableList<SliderPreset> selectedPresets = lvPresets.getSelectionModel().getSelectedItems();
				if (selectedPresets.size() <= 0) {
					return;
				}
				
				boolean filled = false;
				for (int i = 0; i < filteredNpcs.size(); i++) {
					NPC npc = filteredNpcs.get(i);
					if (npc.getSliderPresets().isEmpty()) { // Empty
						npc.clearSliderPresets(); // Explicit clear
						// Give a random preset
						// min inclusive, max exclusive
						int random = MyUtils.random(0, selectedPresets.size()-1);
						SliderPreset preset = selectedPresets.get(random);
						npc.addSliderPreset(preset);
						
						filled = true;
					}
				}
				
				if (filled) {
					main.mainController.updatePresetCounter();
					main.mainController.markChanged();
				}
				
				stage.hide();
			}
		};
		confirmFillEmpty.setTitle("Confirm Action");
		confirmFillEmpty.setHeaderText("Fill NPCs Without Preset");
		confirmFillEmpty.setContentText(
			"Each NPC in the table without a preset will be given a random one from the selection.\n" +
			"If filter is active, only the ones displayed will be filled."
		);
		confirmFillEmpty.setOkButtonText("Fill");
		confirmFillEmpty.setCancelButtonText("Cancel");
	}
	
	protected void connectViews() {
		lvPresets.setItems(main.data.sliderPresets);
	}
	
	protected void disconnectViews() {
		lvPresets.setItems(null);
	}
	
	@FXML
	private void fillEmpty() {
		ObservableList<SliderPreset> selectedPresets = lvPresets.getSelectionModel().getSelectedItems();
		if (selectedPresets.size() <= 0) {
			notif.show("You don't have a selection!");
			return;
		}
		
		confirmFillEmpty.show();
	}
	
	@FXML
	private void selectAll() {
		lvPresets.getSelectionModel().selectAll();
	}
	
	@FXML
	private void invertSelection() {
		for (int i = 0; i < lvPresets.getItems().size(); i++) {
			boolean selected = lvPresets.getSelectionModel().isSelected(i);
			if (selected) {
				lvPresets.getSelectionModel().clearSelection(i);
			} else {
				lvPresets.getSelectionModel().select(i);
			}
		}
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
}