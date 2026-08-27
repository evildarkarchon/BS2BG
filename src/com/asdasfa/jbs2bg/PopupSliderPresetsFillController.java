package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcSliderPresetChoice;

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
			/**
			 * Freezes the current filter and selection into explicit random choices, then
			 * submits one atomic fill edit.
			 */
			@Override
			public void ok() {
				ObservableList<SliderPreset> selectedPresets = lvPresets.getSelectionModel().getSelectedItems();
				if (selectedPresets.size() <= 0) {
					return;
				}
				
				FilteredList<NPC> filteredNpcs = main.mainController.npcTableFilter.getFilteredList();
				List<NpcSliderPresetChoice> choices = choosePresetsForEmptyNpcs(filteredNpcs, selectedPresets);
				
				main.mainController.applyProjectEdit(NpcMorphAssignmentEdits.fillEmpty(choices));
				
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

	/**
	 * Completes every random fill choice on the JavaFX thread before immutable
	 * request data crosses the ProjectSession seam.
	 *
	 * @param filteredNpcs NPC presentation values included by the active table filter
	 * @param selectedPresets caller-selected Slider Preset presentation values
	 * @return explicit choices for currently empty filtered NPCs
	 */
	private List<NpcSliderPresetChoice> choosePresetsForEmptyNpcs(List<NPC> filteredNpcs,
			List<SliderPreset> selectedPresets) {
		List<NpcSliderPresetChoice> choices = new ArrayList<>();
		for (int i = 0; i < filteredNpcs.size(); i++) {
			NPC npc = filteredNpcs.get(i);
			if (npc.getSliderPresets().isEmpty()) { // Empty
				// Give a random preset; MyUtils.random uses inclusive minimum and maximum bounds.
				int random = MyUtils.random(0, selectedPresets.size()-1);
				SliderPreset preset = selectedPresets.get(random);
				NpcMorphAssignmentIdentity identity = new NpcMorphAssignmentIdentity(npc.getMod(), npc.getEditorId());
				choices.add(new NpcSliderPresetChoice(identity, preset.getName()));
			}
		}
		return choices;
	}
	
	protected void connectViews() {
		lvPresets.setItems(main.projectPresentation.getSliderPresets());
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
