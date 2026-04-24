package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.controlsfx.table.TableFilter;
import com.asdasfa.jbs2bg.data.CustomMorphTarget;
import com.asdasfa.jbs2bg.data.MorphTarget;
import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * 
 * @author Totiman
 */
public class PopupNoPresetNotifController extends CustomController {
	
	@FXML
	private ListView<CustomMorphTarget> lvNoPreset;
	
	@FXML
	private TableView<NPC> tvNoPreset;
	@FXML
	private TableColumn<NPC, String> tcName;
	@FXML
	private TableColumn<NPC, String> tcMaster;
	@FXML
	private TableColumn<NPC, String> tcRace;
	@FXML
	private TableColumn<NPC, String> tcEditorId;
	@FXML
	private TableColumn<NPC, String> tcFormId;
	
	private TableFilter<NPC> noPresetTableFilter;
	
	private final ObservableList<CustomMorphTarget> customMorphTargets = FXCollections.observableArrayList();
	private final ObservableList<NPC> morphedNpcs = FXCollections.observableArrayList();
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	@Override
	protected void onPostInit() {
		lvNoPreset.setOnKeyTyped(new KeyNavigationListener() {
			@Override
			public void test() {
				for (int i = 0; i < lvNoPreset.getItems().size(); i++) {
					CustomMorphTarget item = lvNoPreset.getItems().get(i);
					if (item.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						lvNoPreset.getSelectionModel().select(i);
						lvNoPreset.getFocusModel().focus(i);
						
						boolean indexVisible = MyUtils.isIndexVisible(lvNoPreset, i);
						if (!indexVisible)
							lvNoPreset.scrollTo(i);
						
						found = true;
						break;
					}
				}
			}
		});
		
		lvNoPreset.setCellFactory(p ->
		new ListCell<CustomMorphTarget>() {
				@Override
				protected void updateItem(CustomMorphTarget item, boolean empty) {
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
		
		lvNoPreset.setItems(customMorphTargets);
		
		tvNoPreset.setOnKeyTyped(new KeyNavigationListener() {
			@Override
			public void test() {
				for (int i = 0; i < noPresetTableFilter.getFilteredList().size(); i++) {
					NPC npc = noPresetTableFilter.getFilteredList().get(i);
					if (npc.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						tvNoPreset.getSelectionModel().select(npc);
						tvNoPreset.scrollTo(npc);
						found = true;
						break;
					}
				}
			}
		});
		
		tvNoPreset.setPlaceholder(new Label("EMPTY"));
		tvNoPreset.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		tvNoPreset.setOnSort(e -> {
			NPC npc = tvNoPreset.getSelectionModel().getSelectedItem();
			if (npc != null)
				tvNoPreset.scrollTo(npc);
		});
		tcName.setCellValueFactory(new PropertyValueFactory<NPC, String>("name"));
		tcMaster.setCellValueFactory(new PropertyValueFactory<NPC, String>("mod"));
		tcRace.setCellValueFactory(new PropertyValueFactory<NPC, String>("race"));
		tcEditorId.setCellValueFactory(new PropertyValueFactory<NPC, String>("editorId"));
		tcFormId.setCellValueFactory(new PropertyValueFactory<NPC, String>("formId"));
		
		tvNoPreset.setItems(morphedNpcs);
		noPresetTableFilter = TableFilter.forTableView(tvNoPreset).lazy(true).apply();
		
		stage.setOnHidden(e -> {
			customMorphTargets.clear();
			morphedNpcs.clear();
		});
		
		lvNoPreset.managedProperty().bind(lvNoPreset.visibleProperty());
		tvNoPreset.managedProperty().bind(tvNoPreset.visibleProperty());
	}
	
	public void notify(ArrayList<MorphTarget> targets) {
		customMorphTargets.clear();
		morphedNpcs.clear();
		
		stage.setTitle("");
		
		if (targets.size() <= 0)
			return;
		
		stage.setTitle("Warning: Targets with no presets were found!");
		
		for (int i = 0; i < targets.size(); i++) {
			MorphTarget target = targets.get(i);
			if (target instanceof CustomMorphTarget) {
				customMorphTargets.add((CustomMorphTarget) target);
			} else if (target instanceof NPC) {
				morphedNpcs.add((NPC) target);
			}
		}
		
		lvNoPreset.setVisible(true);
		tvNoPreset.setVisible(true);
		
		if (customMorphTargets.size() <= 0)
			lvNoPreset.setVisible(false);
		
		if (morphedNpcs.size() <= 0)
			tvNoPreset.setVisible(false);
		
		if (!stage.isShowing())
			stage.showAndWait();
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
}