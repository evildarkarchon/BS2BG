package com.asdasfa.jbs2bg;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.asdasfa.jbs2bg.controlsfx.table.TableFilter;
import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.beans.binding.Bindings;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * 
 * @author Totiman
 */
public class PopupNpcDatabaseController extends CustomController {
	
	@FXML
	private VBox mainPane;
	
	@FXML
	private Label lblNpcCounter;
	@FXML
	private TableView<NPC> tvNpcDatabase;
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
	@FXML
	private CheckBox cbAssignRandom;
	
	private TableFilter<NPC> npcDatabaseTableFilter;
	
	private CustomConfirm confirmAddAllNpcs;
	private CustomConfirm confirmClearNpcDatabase;
	
	// File Choosers
	private FileChooser fcNpcTxt;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	@Override
	protected void onPostInit() {
		tvNpcDatabase.setOnKeyTyped(new KeyNavigationListener() {
			@Override
			public void test() {
				String colName = tvNpcDatabase.getColumns().get(0).getText(); // Use the first column's data for searching
				
				for (int i = 0; i < npcDatabaseTableFilter.getFilteredList().size(); i++) {
					NPC npc = npcDatabaseTableFilter.getFilteredList().get(i);
					String text = npc.getName();
					if (colName.equalsIgnoreCase("Name")) {
						text = npc.getName();
					} else if (colName.equalsIgnoreCase("Master")) {
						text = npc.getMod();
					} else if (colName.equalsIgnoreCase("Race")) {
						text = npc.getRace();
					} else if (colName.equalsIgnoreCase("EditorID")) {
						text = npc.getEditorId();
					} else if (colName.equalsIgnoreCase("FormID")) {
						text = npc.getFormId();
					} else {
						text = npc.getName();
					}
					
					if (text.toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						tvNpcDatabase.getSelectionModel().select(npc);
						tvNpcDatabase.scrollTo(npc);
						found = true;
						break;
					}
				}
			}
		});
		
		tvNpcDatabase.setPlaceholder(new Label("EMPTY"));
		tvNpcDatabase.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		tvNpcDatabase.setOnSort(e -> {
			NPC npc = tvNpcDatabase.getSelectionModel().getSelectedItem();
			if (npc != null)
				tvNpcDatabase.scrollTo(npc);
		});
		tvNpcDatabase.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			if (newSelection != null) {
				main.mainController.popupImageViewController.setTitle(newSelection.getName());
				main.mainController.popupImageViewController.setImage(
						MainController.findNpcImageFile(newSelection.getName(), newSelection.getEditorId()));
			} else { // Nothing selected
				main.mainController.popupImageViewController.setTitle("");
				main.mainController.popupImageViewController.setImage(null);
			}
		});
		tcName.setCellValueFactory(new PropertyValueFactory<NPC, String>("name"));
		tcMaster.setCellValueFactory(new PropertyValueFactory<NPC, String>("mod"));
		tcRace.setCellValueFactory(new PropertyValueFactory<NPC, String>("race"));
		tcEditorId.setCellValueFactory(new PropertyValueFactory<NPC, String>("editorId"));
		tcFormId.setCellValueFactory(new PropertyValueFactory<NPC, String>("formId"));
		
		updateNpcCounter();
		
		confirmAddAllNpcs = new CustomConfirm(main) {
			@Override
			public void ok() {
				addAllNpcsToMorph();
			}
		};
		confirmAddAllNpcs.setTitle("Confirm Action");
		confirmAddAllNpcs.setHeaderText("Add All NPCs");
		confirmAddAllNpcs.setContentText(
			"Add all NPCs from this database?\n" +
			"If filter is active, only the ones displayed will be added."
		);
		confirmAddAllNpcs.setOkButtonText("Add All");
		confirmAddAllNpcs.setCancelButtonText("Cancel");
		confirmAddAllNpcs.setOwner(stage);
		
		confirmClearNpcDatabase = new CustomConfirm(main) {
			@Override
			public void ok() {
				FilteredList<NPC> items = npcDatabaseTableFilter.getFilteredList();
				main.data.npcDatabase.removeAll(items);
				updateNpcCounter();
			}
		};
		confirmClearNpcDatabase.setTitle("Confirm Action");
		confirmClearNpcDatabase.setHeaderText("Clear NPC Database");
		confirmClearNpcDatabase.setContentText(
			"Clear NPC database?\n" +
			"This will only clear the database, not the morph targets list.\n" +
			"If filter is active, only the ones displayed will be removed."
		);
		confirmClearNpcDatabase.setOkButtonText("Clear");
		confirmClearNpcDatabase.setCancelButtonText("Cancel");
		confirmClearNpcDatabase.setOwner(stage);
		
		fcNpcTxt = new FileChooser();
		fcNpcTxt.setTitle("Add NPC Text file");
		fcNpcTxt.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text file (*.txt)", "*.txt"));
		
		// When showing, set the image in the popupImageView using the database table
		stage.setOnShowing(e -> {
			NPC npc = tvNpcDatabase.getSelectionModel().getSelectedItem();
			if (npc != null) {
				main.mainController.popupImageViewController.setTitle(npc.getName());
				main.mainController.popupImageViewController.setImage(
						MainController.findNpcImageFile(npc.getName(), npc.getEditorId()));
			} else {
				main.mainController.popupImageViewController.setTitle("");
				main.mainController.popupImageViewController.setImage(null);
			}
		});
		// When hidden, set the image in the popupImageView using the npc table
		stage.setOnHidden(e -> {
			NpcMorphAssignmentSnapshot npc = main.mainController.tvNpc.getSelectionModel().getSelectedItem();
			if (npc != null) {
				main.mainController.popupImageViewController.setTitle(npc.getDisplayName());
				main.mainController.popupImageViewController.setImage(MainController.findNpcImageFile(npc));
			} else {
				main.mainController.popupImageViewController.setTitle("");
				main.mainController.popupImageViewController.setImage(null);
			}
		});
		// Don't allow closing if mainPane is disabled, meaning doing some tasks
		stage.setOnCloseRequest(e -> {
			if (mainPane.isDisabled())
				e.consume();
		});
		stage.getScene().cursorProperty().bind(Bindings.when(mainPane.disabledProperty()).then(Cursor.WAIT).otherwise(Cursor.DEFAULT));
		
		connectViews();
	}
	
	protected void connectViews() {
		tvNpcDatabase.setItems(main.data.npcDatabase);
		npcDatabaseTableFilter = TableFilter.forTableView(tvNpcDatabase).lazy(true).apply();
	}
	
	protected void disconnectViews() {
		tvNpcDatabase.setItems(null);
	}
	
	@FXML
	private void addNpcsFromFile() {
		File file;
		try {
			fcNpcTxt.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_NPC_FOLDER, new File(".").getAbsolutePath())));
			file = fcNpcTxt.showOpenDialog(stage);
		} catch (Exception e) {
			fcNpcTxt.setInitialDirectory(main.data.homeDir);
			file = fcNpcTxt.showOpenDialog(stage);
		}
		if (file != null) {
			main.data.prefs.put(main.data.LAST_USED_NPC_FOLDER, file.getParent());
			
			mainPane.setDisable(true);
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Parsing NPC file...");
			// Disconnect TableView items from list, so that list can be modified from another thread 
			disconnectViews();
			try {
				Task<Void> task = parseNpcFile(file);
				task.setOnSucceeded(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "NPC parsing done.");
					mainPane.setDisable(false);
					connectViews();
					updateNpcCounter();
				});
				task.setOnFailed(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "NPC parsing failed.");
					mainPane.setDisable(false);
					connectViews();
					updateNpcCounter();
				});
				task.setOnCancelled(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "NPC parsing cancelled.");
					mainPane.setDisable(false);
					connectViews();
					updateNpcCounter();
				});
				task.exceptionProperty().addListener((obs, oldValue, newValue) -> {
					if (newValue != null) {
						Exception e = (Exception) newValue;
						e.printStackTrace();
					}
				});

				new Thread(task).start();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
		}
	}
	
	private Task<Void> parseNpcFile(File file) throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				main.data.parseNpcFile(file);
				return null;
			}
		};
	}
	
	/**
	 * Copies the selected source row into one explicit Project edit. The logical
	 * identity is captured before apply because a changed render replaces the
	 * projected NPC instance that must be selected afterward.
	 */
	@FXML
	private void addNpcToMorph() {
		NPC npc = tvNpcDatabase.getSelectionModel().getSelectedItem();
		if (npc == null)
			return;

		NpcMorphAssignmentIdentity identity = identityOf(npc);
		NpcMorphAssignmentSnapshot source = copySource(npc, chooseRandomPresetNames());
		ProjectOutcome outcome = main.mainController.applyProjectEdit(NpcMorphAssignmentEdits.addNpc(source));
		if (outcome instanceof ChangedOutcome)
			main.mainController.selectNpc(identity);
		
		tvNpcDatabase.requestFocus();
	}
	
	/**
	 * Copies the currently filtered source rows, completes every optional random
	 * choice, and submits one atomic Project edit.
	 */
	private void addAllNpcsToMorph() {
		List<NPC> filteredSources = new ArrayList<>(npcDatabaseTableFilter.getFilteredList());
		List<NpcMorphAssignmentSnapshot> copiedSources = new ArrayList<>(filteredSources.size());
		for (NPC source : filteredSources)
			copiedSources.add(copySource(source, chooseRandomPresetNames()));
		main.mainController.applyProjectEdit(NpcMorphAssignmentEdits.addNpcs(copiedSources));
	}

	/**
	 * Creates an immutable source value without retaining or mutating the NPC
	 * Database entry.
	 *
	 * @param source session-scoped NPC Database entry
	 * @param sliderPresetNames explicit Project assignment choices
	 * @return copied immutable values for one Project edit
	 */
	private static NpcMorphAssignmentSnapshot copySource(NPC source, List<String> sliderPresetNames) {
		return new NpcMorphAssignmentSnapshot(source.getName(), source.getMod(), source.getEditorId(), source.getRace(),
				source.getFormId(), sliderPresetNames);
	}

	/**
	 * Completes one optional random Project assignment choice before submission.
	 *
	 * @return an empty list when random assignment is disabled or unavailable;
	 *         otherwise, the chosen canonical Slider Preset name
	 */
	private List<String> chooseRandomPresetNames() {
		List<SliderPresetSnapshot> sliderPresets = main.projectPresentation.getSnapshot().getSliderPresets();
		if (!cbAssignRandom.isSelected() || sliderPresets.isEmpty())
			return Collections.emptyList();
		int randomIndex = MyUtils.random(0, sliderPresets.size() - 1);
		return Collections.singletonList(sliderPresets.get(randomIndex).getName());
	}

	/**
	 * Derives the complete case-insensitive Project identity from a source entry.
	 *
	 * @param npc NPC Database entry
	 * @return immutable Project identity used for post-render selection
	 */
	private static NpcMorphAssignmentIdentity identityOf(NPC npc) {
		return new NpcMorphAssignmentIdentity(npc.getMod(), npc.getEditorId());
	}
	
	@FXML
	private void showConfirmAddAllNpcs() {
		if (tvNpcDatabase.getItems().size() <= 0)
			return;
		
		confirmAddAllNpcs.show();
	}
	
	@FXML
	private void showPopupImageView() {
		main.mainController.showPopupImageView();
	}
	
	@FXML
	private void showConfirmClearNpcDatabase() {
		if (tvNpcDatabase.getItems().size() <= 0)
			return;
		
		confirmClearNpcDatabase.show();
	}
	
	private void updateNpcCounter() {
		int count = main.data.npcDatabase.size();
		lblNpcCounter.setText("(" + count + ")");
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
}
