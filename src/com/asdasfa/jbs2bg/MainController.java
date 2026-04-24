package com.asdasfa.jbs2bg;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.xml.sax.SAXException;

import com.asdasfa.jbs2bg.controlsfx.table.TableFilter;
import com.asdasfa.jbs2bg.data.CustomMorphTarget;
import com.asdasfa.jbs2bg.data.MorphTarget;
import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * 
 * @author Totiman
 */
public class MainController extends CustomController {
	
	@FXML
	private VBox mainPane;
	
	// Menu Items
	@FXML
	private MenuItem miNew;
	@FXML
	private MenuItem miOpen;
	@FXML
	private MenuItem miSave;
	@FXML
	private MenuItem miSaveAs;
	@FXML
	private MenuItem miExportBosJson;
	@FXML
	private MenuItem miExport;
	
	// Templates
	@FXML
	protected ListView<SliderPreset> lvPresets;
	@FXML
	private TextArea taTemplate;
	@FXML
	private TextArea taTemplatesGen;
	@FXML
	private CheckBox cbUUNP;
	@FXML
	private CheckBox cbOmitRedundantSliders;
	// ^ Templates ^
	
	// Morphs
	@FXML 
	private ListView<CustomMorphTarget> lvCustomTargets;
	@FXML
	private TextField tfCustomTarget;
	@FXML
	private Label lblNpcCounter;
	@FXML
	protected TableView<NPC> tvNpc;
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
	private TableColumn<NPC, String> tcSliderPresets;
	@FXML
	protected ListView<SliderPreset> lvTargetPresets;
	@FXML
	private Label lblTargetName;
	@FXML
	private Label lblPresetCounter;
	@FXML
	private TextArea taMorphsGen;
	// ^ Morphs ^
	
	protected TableFilter<NPC> npcTableFilter;
	
	// Confirm Dialogs
	private CustomConfirm confirmNewFile;
	private CustomConfirm confirmOpenFile;
	private CustomConfirm confirmExit;
	
	private CustomConfirm confirmClearPresets;
	private CustomConfirm confirmRemovePreset;
	
	private CustomConfirm confirmClearTargetPresets;
	private CustomConfirm confirmClearCustomTargets;
	private CustomConfirm confirmClearNpcs;
	private CustomConfirm confirmClearAssignments;
	
	// Notifications
	private CustomNotif notif;
	
	// Help Menu
	private Stage popupAbout;
	private PopupAboutController popupAboutController;
	
	// Popup SetSliders
	protected Stage popupSetSliders;
	private PopupSetSlidersController popupSetSlidersController;
	
	// Popup BoSView
	protected Stage popupBosView;
	private PopupBosViewController popupBosViewController;
	
	// Popup SliderPresets
	protected Stage popupSliderPresets;
	private PopupSliderPresetsController popupSliderPresetsController;
	
	// Popup SliderPresetsFill
	protected Stage popupSliderPresetsFill;
	private PopupSliderPresetsFillController popupSliderPresetsFillController;
	
	// Popup NpcDatabase
	protected Stage popupNpcDatabase;
	private PopupNpcDatabaseController popupNpcDatabaseController;
	
	// Popup Rename
	protected Stage popupRename;
	private PopupRenameController popupRenameController;
	
	// Popup ImageView
	private Stage popupImageView;
	protected PopupImageViewController popupImageViewController;
	
	// Popup NoPresetNotif
	private Stage popupNoPresetNotif;
	private PopupNoPresetNotifController popupNoPresetNotifController;
	
	// File Choosers
	private FileChooser fcFile;
	private FileChooser fcXml;
	private DirectoryChooser fcExport;
	private DirectoryChooser fcExportBosJson;
	
	/**
	 * Called BEFORE all FXML fields are injected.
	 */
	public MainController() {
	}
	
	@Override
	/**
	 * Called AFTER all FXML fields are injected.
	 */
	public void initialize(URL url, ResourceBundle rb) {
		setupKeyNavigation();
		setupKeyCombinations();
		setupTooltips();
	}
	
	/*
	 * Called in postInitialize after Main is set.
	 */
	@Override
	protected void onPostInit() {
		setupNotifs();
		// Exit if...
		if (main.initSuccess == 0) { // settings.json is erroneous
			notif.showError("Invalid settings.json file detected!\n" +
					"Delete settings file and relaunch to recreate it.");
			
			Platform.exit();
			
			return;
		} else if (main.initSuccess == -1) { // settings_UUNP.json is erroneous
			notif.showError("Invalid settings_UUNP.json file detected!\n" +
					"Delete settings file and relaunch to recreate it.");
			
			Platform.exit();
			
			return;
		} else if (main.initSuccess == -2) { // settings.json and settings_UUNP.json are erroneous
			notif.showError("Invalid settings files detected!\n" +
					"Delete both settings files and relaunch to recreate them.");
			
			Platform.exit();
			
			return;
		}
		
		cbUUNP.setDisable(true);
		cbUUNP.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
				if (preset == null)
					return;
				
				if (preset.isUUNP() != cbUUNP.isSelected()) { // Only mark changed if toggled
					preset.setIsUUNP(cbUUNP.isSelected());
					
					updateTemplateText();
					markChanged();
					
					lvPresets.requestFocus();
				}
			}
		});
		
		cbOmitRedundantSliders.setDisable(false);
		boolean omitRedundantSliders = main.data.prefs.getBoolean(main.data.OMIT_REDUNDANT_SLIDERS, false);
		cbOmitRedundantSliders.setSelected(omitRedundantSliders);
		cbOmitRedundantSliders.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				updateTemplateText();
				
				main.data.prefs.putBoolean(main.data.OMIT_REDUNDANT_SLIDERS, cbOmitRedundantSliders.isSelected());
				
				// Clear Templates TextArea
				taTemplatesGen.clear();
			}
		});
		
		setupViews();
		setupAlerts();
		setupPopupAbout();
		setupPopupSetSliders();
		setupPopupBosView();
		setupPopupSliderPresets();
		setupPopupNpcDatabase();
		setupPopupRename();
		setupPopupImageView();
		setupPopupNoPresetNotif();
		setupFileChoosers();
		
		connectViews();
		
		/*main.data.sliderPresets.addListener((ListChangeListener.Change<? extends SliderPreset> c) -> {
			while (c.next()) {
				if (c.wasAdded() || c.wasRemoved() || c.wasReplaced() || c.wasPermutated() || c.wasUpdated()) {
					markChanged();
				}
			}
		});*/
		/*main.data.morphedNpcs.addListener((ListChangeListener.Change<? extends NPC> c) -> {
			while (c.next()) {
				if (c.wasAdded() || c.wasRemoved() || c.wasReplaced() || c.wasPermutated() || c.wasUpdated()) {
					markChanged();
				}
			}
		});*/
		
		// Don't allow closing if mainPane is disabled, meaning doing some tasks
		stage.setOnCloseRequest(e -> {
			if (mainPane.isDisabled())
				e.consume();
			
			if (changed) {
				e.consume();
				confirmExit.show();
			}
		});
		stage.getScene().cursorProperty().bind(Bindings.when(mainPane.disabledProperty()).then(Cursor.WAIT).otherwise(Cursor.DEFAULT));
	}
	
	private boolean changed = false;
	protected void markChanged() {
		if (!changed) {
			if (main.data.currentFile != null) {
				stage.setTitle(main.appName + " - " + "*" + main.data.currentFile.getName());
			} else {
				stage.setTitle(main.appName + " *");
			}
		}
		
		changed = true;
		
		// Clear TextAreas every time a changed is made
		taTemplatesGen.clear();
		taMorphsGen.clear();
	}
	
	private void setupKeyCombinations() {
		miNew.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
		miOpen.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
		miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
		miSaveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN));
		miExportBosJson.setAccelerator(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN));
		miExport.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
	}
	
	private void setupTooltips() {
		Tooltip tooltip = new Tooltip();
		tooltip.setText(
		    "Examples: \n" +
		    "\n" +
			"All|Female \n" +
			"All|Female|NordRace \n" +
			"All|Female|BretonRace \n" +
			"All|Female|NordRaceVampire \n"
		);
		tfCustomTarget.setTooltip(tooltip);
	}
	
	private void setupNotifs() {
		notif = new CustomNotif(main);
		notif.setOwner(stage);
	}
	
	private void setupViews() {
		/*lvPresets.setCellFactory(new Callback<ListView<SliderPreset>, ListCell<SliderPreset>>() {
			@Override
			public ListCell<SliderPreset> call(ListView<SliderPreset> param) {
				ListCell<SliderPreset> cell = new ListCell<SliderPreset>() {
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
				};
				return cell;
			}
        });*/
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
		lvPresets.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
			if (preset == null) {
				cbUUNP.setDisable(true);
				cbUUNP.setSelected(false);
				return;
			}
			
			cbUUNP.setDisable(false);
			cbUUNP.setSelected(preset.isUUNP());
			updateTemplateText();
		});
		
		lvCustomTargets.setCellFactory(p ->
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
		lvCustomTargets.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			if (newSelection != null) {
				tvNpc.getSelectionModel().clearSelection();
				tfCustomTarget.setText(newSelection.getName());
				
				lblTargetName.setText(newSelection.getName());
				lvTargetPresets.setItems(newSelection.getSliderPresets());
				updatePresetCounter();
			} else {
				lblTargetName.setText("-null-");
				lvTargetPresets.setItems(null);
				updatePresetCounter();
			}
		});
		
		lvTargetPresets.setCellFactory(p ->
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
		lvTargetPresets.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			if (newSelection != null) {
			} else {
			}
		});
		
		
		tvNpc.setPlaceholder(new Label("EMPTY"));
		tvNpc.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		tvNpc.setOnSort(e -> {
			NPC npc = tvNpc.getSelectionModel().getSelectedItem();
			if (npc != null)
				tvNpc.scrollTo(npc);
		});
		tvNpc.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			if (newSelection != null) {
				lvCustomTargets.getSelectionModel().clearSelection();
				tfCustomTarget.setText("");
				
				lblTargetName.setText(newSelection.getName());
				lvTargetPresets.setItems(newSelection.getSliderPresets());
				updatePresetCounter();
				
				popupImageViewController.setTitle(newSelection.getName());
				popupImageViewController.setImage(newSelection.getImageFile());
			} else {
				lblTargetName.setText("-null-");
				lvTargetPresets.setItems(null);
				updatePresetCounter();
				
				popupImageViewController.setTitle("");
				popupImageViewController.setImage(null);
			}
		});
		tcName.setCellValueFactory(new PropertyValueFactory<NPC, String>("name"));
		tcMaster.setCellValueFactory(new PropertyValueFactory<NPC, String>("mod"));
		tcRace.setCellValueFactory(new PropertyValueFactory<NPC, String>("race"));
		tcEditorId.setCellValueFactory(new PropertyValueFactory<NPC, String>("editorId"));
		tcFormId.setCellValueFactory(new PropertyValueFactory<NPC, String>("formId"));
		tcSliderPresets.setCellValueFactory(col -> col.getValue().getSliderPresetsString());
		
		updateNpcCounter();
	}
	
	/**
	 * Connect the views to their lists.
	 */
	protected void connectViews() {
		lvPresets.setItems(main.data.sliderPresets);
		
		tvNpc.setItems(main.data.morphedNpcs);
		npcTableFilter = TableFilter.forTableView(tvNpc).lazy(true).apply();
		
		lvCustomTargets.setItems(main.data.customMorphTargets);
		
		popupSliderPresetsController.connectViews();
		popupSliderPresetsFillController.connectViews();
		popupNpcDatabaseController.connectViews();
		
		updateNpcCounter();
	}
	
	/**
	 * Disconnect the views from their lists.
	 */
	protected void disconnectViews() {
		lvPresets.setItems(null);
		
		tvNpc.setItems(null);
		
		lvCustomTargets.setItems(null);
		
		lvTargetPresets.setItems(null);
		
		popupSliderPresetsController.disconnectViews();
		popupSliderPresetsFillController.disconnectViews();
		popupNpcDatabaseController.disconnectViews();
	}
	
	private void setupAlerts() {
		confirmNewFile = new CustomConfirm(main) {
			@Override
			public void ok() {
				newFile();
			}
		};
		confirmNewFile.setTitle("Confirm Action");
		confirmNewFile.setHeaderText("New File");
		confirmNewFile.setContentText(
			"You're starting a new file.\n" +
			"All unsaved changes will be discarded."
		);
		confirmNewFile.setOkButtonText("New");
		confirmNewFile.setCancelButtonText("Cancel");
		
		confirmOpenFile = new CustomConfirm(main) {
			@Override
			public void ok() {
				openFromFile();
			}
		};
		confirmOpenFile.setTitle("Confirm Action");
		confirmOpenFile.setHeaderText("Open File");
		confirmOpenFile.setContentText(
			"You still have a file open with some unsaved changes.\n" +
			"All unsaved changes will be discarded."
		);
		confirmOpenFile.setOkButtonText("Open Another");
		confirmOpenFile.setCancelButtonText("Cancel");
		
		confirmExit = new CustomConfirm(main) {
			@Override
			public void ok() {
				Platform.exit();
			}
		};
		confirmExit.setTitle("Confirm Action");
		confirmExit.setHeaderText("Exit");
		confirmExit.setContentText(
			"You have some unsaved changes.\n" +
			"All unsaved changes will be discarded."
		);
		confirmExit.setOkButtonText("Discard");
		confirmExit.setCancelButtonText("Cancel");
		
		confirmClearPresets = new CustomConfirm(main) {
			@Override
			public void ok() {
				clearPresets();
			}
		};
		confirmClearPresets.setTitle("Confirm Action");
		confirmClearPresets.setHeaderText("Clear Slider Presets");
		confirmClearPresets.setContentText(
			"All your slider presets will be removed.\n" +
			"Obviously, all targets will also lose all their assigned presets."
		);
		confirmClearPresets.setOkButtonText("Clear");
		confirmClearPresets.setCancelButtonText("Cancel");
		
		confirmRemovePreset = new CustomConfirm(main) {
			@Override
			public void ok() {
				removeSelectedPreset();
			}
		};
		confirmRemovePreset.setTitle("Confirm Action");
		confirmRemovePreset.setHeaderText("Remove Slider Preset");
		confirmRemovePreset.setContentText(
			"This preset is assigned to a morph target.\n" +
			"All targets assigned with this preset will lose this preset."
		);
		confirmRemovePreset.setOkButtonText("Remove");
		confirmRemovePreset.setCancelButtonText("Cancel");
		
		confirmClearTargetPresets = new CustomConfirm(main) {
			@Override
			public void ok() {
				MorphTarget target = getCurrentTarget();
				if (target == null)
					return;
				
				target.clearSliderPresets();
				
				updatePresetCounter();
				
				markChanged();
			}
		};
		confirmClearTargetPresets.setTitle("Confirm Action");
		confirmClearTargetPresets.setHeaderText("Clear Target Presets");
		confirmClearTargetPresets.setContentText(
			"This target will lose all of its assigned presets."
		);
		confirmClearTargetPresets.setOkButtonText("Clear");
		confirmClearTargetPresets.setCancelButtonText("Cancel");
		
		confirmClearCustomTargets = new CustomConfirm(main) {
			@Override
			public void ok() {
				// Explicit clear call
				for (int i = 0; i < main.data.customMorphTargets.size(); i++) {
					main.data.customMorphTargets.get(i).clearSliderPresets();
				}
				
				main.data.customMorphTargets.clear();
				
				markChanged();
			}
		};
		confirmClearCustomTargets.setTitle("Confirm Action");
		confirmClearCustomTargets.setHeaderText("Clear Custom Targets");
		confirmClearCustomTargets.setContentText(
			"All custom targets will be removed."
		);
		confirmClearCustomTargets.setOkButtonText("Clear");
		confirmClearCustomTargets.setCancelButtonText("Cancel");
		
		confirmClearNpcs = new CustomConfirm(main) {
			@Override
			public void ok() {
				FilteredList<NPC> items = npcTableFilter.getFilteredList();
				// Explicit clear call
				for (int i = 0; i < items.size(); i++) {
					items.get(i).clearSliderPresets();
				}
				
				main.data.morphedNpcs.removeAll(items);
				
				updateNpcCounter();
				
				markChanged();
			}
		};
		confirmClearNpcs.setTitle("Confirm Action");
		confirmClearNpcs.setHeaderText("Clear NPCs");
		confirmClearNpcs.setContentText(
			"All NPCs in the table will be removed.\n" +
			"If filter is active, only the ones displayed will be removed."
		);
		confirmClearNpcs.setOkButtonText("Clear");
		confirmClearNpcs.setCancelButtonText("Cancel");
		
		confirmClearAssignments = new CustomConfirm(main) {
			@Override
			public void ok() {
				FilteredList<NPC> items = npcTableFilter.getFilteredList();
				boolean cleared = false;
				for (int i = 0; i < items.size(); i++) {
					NPC npc = items.get(i);
					
					if (!npc.getSliderPresets().isEmpty()) {
						npc.clearSliderPresets();
						cleared = true;
					}
				}
				
				if (!cleared) { // No NPC in the table was cleared
					notif.show("No NPC in the table was cleared!");
					return;
				}
				
				markChanged();
			}
		};
		confirmClearAssignments.setTitle("Confirm Action");
		confirmClearAssignments.setHeaderText("Clear NPCs' Assigned Presets");
		confirmClearAssignments.setContentText(
			"All NPCs in the table will have all their assigned presets cleared.\n" +
			"If filter is active, only the ones displayed will be cleared."
		);
		confirmClearAssignments.setOkButtonText("Clear");
		confirmClearAssignments.setCancelButtonText("Cancel");
	}
	
	private void setupPopupAbout() {
		try {
			popupAbout = new Stage();
			popupAbout.getIcons().add(main.icon);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_about.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 400, 200);
			scene.getStylesheets().add(main.style);
	        popupAbout.setScene(scene);
	        
	        popupAbout.initModality(Modality.WINDOW_MODAL);
			popupAbout.initOwner(stage);
			popupAbout.setResizable(false);
			popupAbout.setTitle("About jBS2BG");
			
			popupAboutController = loader.getController();
			popupAboutController.postInitialize(main, popupAbout);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setupPopupSetSliders() {
		try {
			popupSetSliders = new Stage();
			popupSetSliders.getIcons().add(main.icon);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_setsliders.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 590, 600);
			scene.getStylesheets().add(main.style);
			popupSetSliders.setScene(scene);
			
			popupSetSliders.initModality(Modality.WINDOW_MODAL);
			popupSetSliders.initOwner(stage);
			popupSetSliders.setResizable(false);
			
			popupSetSlidersController = loader.getController();
			popupSetSlidersController.postInitialize(main, popupSetSliders);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setupPopupBosView() {
		try {
			popupBosView = new Stage();
			popupBosView.getIcons().add(main.icon);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_bosview.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 600, 400);
			scene.getStylesheets().add(main.style);
			popupBosView.setScene(scene);
			
			popupBosView.initModality(Modality.WINDOW_MODAL);
			popupBosView.initOwner(stage);
			popupBosView.setMinWidth(600 + main.decorWidth);
			popupBosView.setMinHeight(400 + main.decorHeight);
			popupBosView.setResizable(true);
			
			popupBosViewController = loader.getController();
			popupBosViewController.postInitialize(main, popupBosView);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setupPopupSliderPresets() {
		try {
			popupSliderPresets = new Stage();
			popupSliderPresets.getIcons().add(main.icon);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_sliderpresets.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 410, 460);
			scene.getStylesheets().add(main.style);
			popupSliderPresets.setScene(scene);
			
			popupSliderPresets.initModality(Modality.WINDOW_MODAL);
			popupSliderPresets.initOwner(stage);
			popupSliderPresets.setResizable(false);
			popupSliderPresets.setTitle("Slider Presets");
			
			popupSliderPresetsController = loader.getController();
			popupSliderPresetsController.postInitialize(main, popupSliderPresets);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			popupSliderPresetsFill = new Stage();
			popupSliderPresetsFill.getIcons().add(main.icon);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_sliderpresetsfill.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 410, 460);
			scene.getStylesheets().add(main.style);
			popupSliderPresetsFill.setScene(scene);
			
			popupSliderPresetsFill.initModality(Modality.WINDOW_MODAL);
			popupSliderPresetsFill.initOwner(stage);
			popupSliderPresetsFill.setResizable(false);
			popupSliderPresetsFill.setTitle("Slider Presets");
			
			popupSliderPresetsFillController = loader.getController();
			popupSliderPresetsFillController.postInitialize(main, popupSliderPresetsFill);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setupPopupNpcDatabase() {
		try {
			popupNpcDatabase = new Stage();
			popupNpcDatabase.getIcons().add(main.icon);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_npcdatabase.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 600, 400);
			scene.getStylesheets().add(main.style);
			popupNpcDatabase.setScene(scene);
			
			popupNpcDatabase.initModality(Modality.WINDOW_MODAL);
			popupNpcDatabase.initOwner(stage);
			popupNpcDatabase.setMinWidth(600 + main.decorWidth);
			popupNpcDatabase.setMinHeight(400 + main.decorHeight);
			popupNpcDatabase.setResizable(true);
			popupNpcDatabase.setTitle("NPC Database");
			
			popupNpcDatabaseController = loader.getController();
			popupNpcDatabaseController.postInitialize(main, popupNpcDatabase);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setupPopupRename() {
		try {
			popupRename = new Stage();
			popupRename.getIcons().add(main.icon);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_rename.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 400, 150);
			scene.getStylesheets().add(main.style);
			popupRename.setScene(scene);
			
			popupRename.initModality(Modality.WINDOW_MODAL);
			popupRename.initOwner(stage);
			popupRename.setResizable(false);
			
			popupRenameController = loader.getController();
			popupRenameController.postInitialize(main, popupRename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setupPopupImageView() {
		try {
			popupImageView = new Stage();
			popupImageView.getIcons().add(main.icon);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_imageview.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 290, 256);
			scene.getStylesheets().add(main.style);
			popupImageView.setScene(scene);
			
			popupImageView.initModality(Modality.NONE);
			popupImageView.initOwner(stage);
			popupImageView.setResizable(true);
			popupImageView.setAlwaysOnTop(true);
			popupImageView.setMinWidth(290 + main.decorWidth);
			popupImageView.setMinHeight(256 + main.decorHeight);
			
			popupImageViewController = loader.getController();
			popupImageViewController.postInitialize(main, popupImageView);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setupPopupNoPresetNotif() {
		try {
			popupNoPresetNotif = new Stage();
			popupNoPresetNotif.getIcons().add(new Image(getClass().getResourceAsStream("/com/sun/javafx/scene/control/skin/modena/dialog-warning.png")));
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("popup_nopresetnotif.fxml"));
			Parent root = loader.load();
			
			Scene scene = new Scene(root, 500, 450);
			scene.getStylesheets().add(main.style);
			popupNoPresetNotif.setScene(scene);
			
			popupNoPresetNotif.initModality(Modality.NONE);
			popupNoPresetNotif.initOwner(stage);
			popupNoPresetNotif.setResizable(true);
			popupNoPresetNotif.setAlwaysOnTop(true);
			popupNoPresetNotif.setMinWidth(500 + main.decorWidth);
			popupNoPresetNotif.setMinHeight(450 + main.decorHeight);
			
			popupNoPresetNotifController = loader.getController();
			popupNoPresetNotifController.postInitialize(main, popupNoPresetNotif);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setupFileChoosers() {
		fcFile = new FileChooser();
		fcFile.getExtensionFilters().add(new FileChooser.ExtensionFilter("jBS2BG files (*.jbs2bg)", "*.jbs2bg"));
		
		fcXml = new FileChooser();
		fcXml.setTitle("Add BodySlide XMLs");
		fcXml.getExtensionFilters().add(new FileChooser.ExtensionFilter("BodySlide XML files (*.xml)", "*.xml"));
		
		fcExport = new DirectoryChooser();
		fcExport.setTitle("Export Templates and Morphs INI");
		
		fcExportBosJson= new DirectoryChooser();
		fcExportBosJson.setTitle("Export BoS JSON files");
	}
	
	@FXML
	private void addXmlPresets() {
		List<File> files;
		try {
			fcXml.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_PRESET_FOLDER, new File(".").getAbsolutePath())));
			files = fcXml.showOpenMultipleDialog(stage);
		} catch (Exception e) {
			fcXml.setInitialDirectory(main.data.homeDir);
			files = fcXml.showOpenMultipleDialog(stage);
		}
		if (files != null) {
			main.data.prefs.put(main.data.LAST_USED_PRESET_FOLDER, files.get(0).getParent());
			
			mainPane.setDisable(true);
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Parsing XML presets...");
			// Disconnect ListView items from lists, so that list can be modified from another thread
			lvPresets.setItems(null);
			try {
				Task<Void> task = parseXmlPresets(files);
				task.setOnSucceeded(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "XML parsing done.");
					mainPane.setDisable(false);
					lvPresets.setItems(main.data.sliderPresets);
					
					markChanged();
				});
				task.setOnFailed(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "XML parsing failed.");
					mainPane.setDisable(false);
					lvPresets.setItems(main.data.sliderPresets);
					
					notif.showError("XML parsing failed.");
				});
				task.setOnCancelled(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "XML parsing cancelled.");
					mainPane.setDisable(false);
					lvPresets.setItems(main.data.sliderPresets);
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
	
	private Task<Void> parseXmlPresets(List<File> files) throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				for (int i = 0; i < files.size(); i++) {
					File file = files.get(i);
					try {
						main.data.parseXmlPreset(file);
					} catch (SAXException | IOException e) {
						e.printStackTrace();
					}
				}
				// Sort alphabetically after adding a preset/presets
				main.data.sortPresets();
				return null;
			}
		};
	}
	
	@FXML
	private void showConfirmClearPresets() {
		if (main.data.sliderPresets.size() <= 0)
			return;
		
		confirmClearPresets.show();
	}
	
	private void clearPresets() {
		if (main.data.sliderPresets.size() <= 0)
			return;
		
		main.data.sliderPresets.clear();
		// Remove all presets from all targets
		for (int i = 0; i < main.data.customMorphTargets.size(); i++) {
			main.data.customMorphTargets.get(i).clearSliderPresets();
		}
		for (int i = 0; i < main.data.morphedNpcs.size(); i++) {
			main.data.morphedNpcs.get(i).clearSliderPresets();
		}
		for (int i = 0; i < main.data.npcDatabase.size(); i++) {
			main.data.npcDatabase.get(i).clearSliderPresets();
		}
		taTemplate.setText("");
		taTemplatesGen.setText("");
		
		markChanged();
	}
	
	@FXML
	private void showConfirmRemovePreset() {
		SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		boolean used = false;
		// Search custom morph targets
		for (int i = 0; i < main.data.customMorphTargets.size(); i++) {
			ObservableList<SliderPreset> presets = main.data.customMorphTargets.get(i).getSliderPresets();
			if (presets.contains(preset)) {
				used = true;
				break;
			}
		}
		if (!used) { // Search NPCs
			for (int i = 0; i < main.data.morphedNpcs.size(); i++) {
				ObservableList<SliderPreset> presets = main.data.morphedNpcs.get(i).getSliderPresets();
				if (presets.contains(preset)) {
					used = true;
					break;
				}
			}
		}
		
		if (used) { // Show confirmation
			confirmRemovePreset.show();
		} else { // Just remove
			removeSelectedPreset();
		}
	}
	
	private void removeSelectedPreset() {
		SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		main.data.sliderPresets.remove(preset);
		// Remove this preset from all targets that uses it
		for (int i = 0; i < main.data.customMorphTargets.size(); i++) {
			main.data.customMorphTargets.get(i).removeSliderPreset(preset);
		}
		for (int i = 0; i < main.data.morphedNpcs.size(); i++) {
			main.data.morphedNpcs.get(i).removeSliderPreset(preset);
		}
		
		updatePresetCounter();
		updateTemplateText();
		
		markChanged();
	}
	
	@FXML
	private void duplicateSelectedPreset() {
		SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		SliderPreset presetCopy = new SliderPreset(preset);
		presetCopy.setName(preset.getName() + "(Dupe)");
		
		if (!main.data.sliderPresetExists(presetCopy)) {
			main.data.sliderPresets.add(presetCopy);
			main.data.sortPresets();
			
			int index = lvPresets.getItems().indexOf(presetCopy);
			boolean indexVisible = MyUtils.isIndexVisible(lvPresets, index);
			
			lvPresets.getSelectionModel().select(index);
			lvPresets.getFocusModel().focus(index);
			if (!indexVisible)
				lvPresets.scrollTo(index);
		} else {
			notif.show("Duplication failed. The output duplicate's name will clash with an existing one, please rename your duplicates.");
		}
		
		markChanged();
	}
	
	@FXML
	private void showConfirmClearTargetPresets() {
		MorphTarget target = getCurrentTarget();
		
		if (target == null)
			return;
		
		if (lvTargetPresets.getItems().size() <= 0)
			return;
		
		confirmClearTargetPresets.show();
	}
	
	@FXML
	private void showConfirmClearCustomTargets() {
		if (lvCustomTargets.getItems().size() <= 0)
			return;
		
		confirmClearCustomTargets.show();
	}
	
	@FXML
	private void showConfirmClearNpcs() {
		if (tvNpc.getItems().size() <= 0)
			return;
		
		confirmClearNpcs.show();
	}
	
	@FXML
	private void showConfirmClearAssignments() {
		if (tvNpc.getItems().size() <= 0)
			return;
		
		confirmClearAssignments.show();
	}
	
	public void updateNpcCounter() {
		int count = main.data.morphedNpcs.size();
		lblNpcCounter.setText("(" + count + ")");
	}
	
	public void updatePresetCounter() {
		int count = 0;
		MorphTarget target = getCurrentTarget();
		if (target != null) {
			count = target.getSliderPresets().size();
		}
		
		if (count < 31) {
			lblPresetCounter.setStyle("-fx-text-fill: -fx-light-text-color");
		} else if (count < 77) { // 77+ presets crashes on main menu?
			lblPresetCounter.setStyle("-fx-text-fill: #ff7800");
		} else {
			lblPresetCounter.setStyle("-fx-text-fill: #d30000");
		}
		
		lblPresetCounter.setText("" + count);
	}
	
	@FXML
	private void showAbout() {
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		double x = (screenBounds.getWidth()/2) - (popupAbout.getScene().getWidth()*0.5);
		double y = (screenBounds.getHeight()/2) - (popupAbout.getScene().getHeight()*0.75);
		popupAbout.setX(x);
		popupAbout.setY(y);
		popupAbout.show();
	}
	
	@FXML
	private void showPopupSetSliders() {
		if (lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		Scene mainScene = stage.getScene();
		double x = stage.getX() + mainScene.getWidth()/2 - popupSetSliders.getScene().getWidth()/2;
		double y = stage.getY() + mainScene.getHeight()/2 - popupSetSliders.getScene().getHeight()/2;
		if (x < 0)
			x = 0;
		if (x > screenBounds.getWidth() - popupSetSliders.getScene().getWidth())
			x = screenBounds.getWidth() - popupSetSliders.getScene().getWidth();
		if (y < 0)
			y = 0;
		if (y + popupSetSliders.getScene().getHeight() > screenBounds.getHeight())
			y = screenBounds.getHeight() - popupSetSliders.getScene().getHeight();
		popupSetSliders.setX(x);
		popupSetSliders.setY(y);
		popupSetSliders.show();
	}
	
	@FXML
	protected void showPopupBosView() {
		if (lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		Scene mainScene = stage.getScene();
		double x = stage.getX() + mainScene.getWidth()/2 - popupBosView.getScene().getWidth()/2;
		double y = stage.getY() + mainScene.getHeight()/2 - popupBosView.getScene().getHeight()/2;
		if (x < 0)
			x = 0;
		if (x > screenBounds.getWidth() - popupBosView.getScene().getWidth())
			x = screenBounds.getWidth() - popupBosView.getScene().getWidth();
		if (y < 0)
			y = 0;
		if (y + popupBosView.getScene().getHeight() > screenBounds.getHeight())
			y = screenBounds.getHeight() - popupBosView.getScene().getHeight();
		popupBosView.setX(x);
		popupBosView.setY(y);
		popupBosView.show();
	}
	
	@FXML
	private void showPopupSliderPresets() {
		if (getCurrentTarget() == null)
			return;
		
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		Scene mainScene = stage.getScene();
		double x = stage.getX() + mainScene.getWidth()*0.3 - popupSliderPresets.getScene().getWidth()/2;
		double y = stage.getY() + mainScene.getHeight()/2 - popupSliderPresets.getScene().getHeight()/2;
		if (x < 0)
			x = 0;
		if (x > screenBounds.getWidth() - popupSliderPresets.getScene().getWidth())
			x = screenBounds.getWidth() - popupSliderPresets.getScene().getWidth();
		if (y < 0)
			y = 0;
		if (y + popupSliderPresets.getScene().getHeight() > screenBounds.getHeight())
			y = screenBounds.getHeight() - popupSliderPresets.getScene().getHeight();
		popupSliderPresets.setX(x);
		popupSliderPresets.setY(y);
		popupSliderPresets.show();
	}
	
	@FXML
	private void showPopupSliderPresetsFill() {
		if (tvNpc.getItems().size() <= 0)
			return;
		
		boolean hasEmpty = false;
		FilteredList<NPC> filteredNpcs = npcTableFilter.getFilteredList();
		for (int i = 0; i < filteredNpcs.size(); i++) {
			NPC npc = filteredNpcs.get(i);
			if (npc.getSliderPresets().isEmpty()) { // Empty
				hasEmpty = true;
				break;
			}
		}
		
		if (!hasEmpty) { // No empty
			notif.show("No NPC in the table is empty!");
			return;
		}
		
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		Scene mainScene = stage.getScene();
		double x = stage.getX() + mainScene.getWidth()*0.7 - popupSliderPresetsFill.getScene().getWidth()/2;
		double y = stage.getY() + mainScene.getHeight()/2 - popupSliderPresetsFill.getScene().getHeight()/2;
		if (x < 0)
			x = 0;
		if (x > screenBounds.getWidth() - popupSliderPresetsFill.getScene().getWidth())
			x = screenBounds.getWidth() - popupSliderPresetsFill.getScene().getWidth();
		if (y < 0)
			y = 0;
		if (y + popupSliderPresetsFill.getScene().getHeight() > screenBounds.getHeight())
			y = screenBounds.getHeight() - popupSliderPresetsFill.getScene().getHeight();
		popupSliderPresetsFill.setX(x);
		popupSliderPresetsFill.setY(y);
		popupSliderPresetsFill.show();
	}
	
	@FXML
	private void showPopupNpcDatabase() {
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		Scene mainScene = stage.getScene();
		double x = stage.getX() + mainScene.getWidth()/2 - popupNpcDatabase.getScene().getWidth()/2;
		double y = stage.getY() + mainScene.getHeight()/2 - popupNpcDatabase.getScene().getHeight()/2;
		if (x < 0)
			x = 0;
		if (x > screenBounds.getWidth() - popupNpcDatabase.getScene().getWidth())
			x = screenBounds.getWidth() - popupNpcDatabase.getScene().getWidth();
		if (y < 0)
			y = 0;
		if (y + popupNpcDatabase.getScene().getHeight() > screenBounds.getHeight())
			y = screenBounds.getHeight() - popupNpcDatabase.getScene().getHeight();
		popupNpcDatabase.setX(x);
		popupNpcDatabase.setY(y);
		popupNpcDatabase.show();
	}
	
	@FXML
	private void showPopupRename() {
		if (lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		Scene mainScene = stage.getScene();
		double x = stage.getX() + mainScene.getWidth()/2 - popupRename.getScene().getWidth()/2;
		double y = stage.getY() + mainScene.getHeight()/2 - popupRename.getScene().getHeight()/2;
		if (x < 0)
			x = 0;
		if (x > screenBounds.getWidth() - popupRename.getScene().getWidth())
			x = screenBounds.getWidth() - popupRename.getScene().getWidth();
		if (y < 0)
			y = 0;
		if (y + popupRename.getScene().getHeight() > screenBounds.getHeight())
			y = screenBounds.getHeight() - popupRename.getScene().getHeight();
		popupRename.setX(x);
		popupRename.setY(y);
		popupRename.show();
	}
	
	public void updateTemplateText() {
		SliderPreset preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null) {
			taTemplate.setText("");
			return;
		}
		
		taTemplate.setText(preset.toLine(cbOmitRedundantSliders.isSelected()));
		taTemplate.positionCaret(0);
    }
	
	@FXML
	private void generateTemplates() {
		if (main.data.sliderPresets.size() <= 0) {
			notif.show("You don't have any presets in the list, add some BodySlide XML presets first!");
			taTemplatesGen.setText("");
			taTemplatesGen.positionCaret(0);
			return;
		}
		
		mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Templates...");
		try {
			Task<Void> task = generateTemplatesTask();
			task.setOnSucceeded(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Templates done.");
				mainPane.setDisable(false);
			});
			task.setOnFailed(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Templates failed.");
				mainPane.setDisable(false);
				
				notif.showError("Generating Templates failed.");
			});
			task.setOnCancelled(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Templates cancelled.");
				mainPane.setDisable(false);
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
	}
	
	private Task<Void> generateTemplatesTask() throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				doGenerateTemplates();
				
				return null;
			}
		};
	}
	
	private void doGenerateTemplates() {
		String newLine = System.getProperty("line.separator");
		String template = "";

		for (int i = 0; i < main.data.sliderPresets.size(); i++) {
			SliderPreset sliderPreset = main.data.sliderPresets.get(i);
			template = template + sliderPreset.toLine(cbOmitRedundantSliders.isSelected());
			if (i < main.data.sliderPresets.size() - 1)
				template += newLine;
		}

		taTemplatesGen.setText(template);
		taTemplatesGen.positionCaret(0);
	}
	
	@FXML
	private void copyTemplates() {
		String text = taTemplatesGen.getText();
		if (!text.isEmpty()) {
			final ClipboardContent content = new ClipboardContent();
			content.putString(text);
			Clipboard.getSystemClipboard().setContent(content);
			
			notif.show("Templates copied to clipboard!");
		} else {
			notif.show("There is nothing in the output, add and generate a preset first!");
		}
	}
	
	@FXML
	private void addCustomMorphTarget() {
		String name = tfCustomTarget.getText();
		name = name.trim();

		if (name.isEmpty())
			return;

		boolean exists = false;
		for (int i = 0; i < main.data.customMorphTargets.size(); i++) {
			CustomMorphTarget c = main.data.customMorphTargets.get(i);
			if (c.getName().equalsIgnoreCase(name)) {
				exists = true;
				break;
			}
		}

		if (!exists) {
			CustomMorphTarget customMorphTarget = new CustomMorphTarget(name);
			// Give a random preset
			if (main.data.sliderPresets.size() > 0) {
				// min inclusive, max inclusive
				int random = MyUtils.random(0, main.data.sliderPresets.size()-1);
				SliderPreset preset = main.data.sliderPresets.get(random);
				
				customMorphTarget.addSliderPreset(preset);
				customMorphTarget.sortPresets();
			}
			main.data.customMorphTargets.add(customMorphTarget);
			main.data.sortCustomMorphTargets();
			
			lvCustomTargets.getSelectionModel().clearSelection();
			
			int index = lvCustomTargets.getItems().indexOf(customMorphTarget);
			boolean indexVisible = MyUtils.isIndexVisible(lvCustomTargets, index);
			
			if (!indexVisible)
				lvCustomTargets.scrollTo(index);
			
			tfCustomTarget.setText("");
			
			markChanged();
		}
		
		tfCustomTarget.requestFocus();
		tfCustomTarget.positionCaret(tfCustomTarget.getText().length());
	}
	
	@FXML
	private void removeSelectedCustomTarget() {
		CustomMorphTarget customMorphTarget = lvCustomTargets.getSelectionModel().getSelectedItem();
		if (customMorphTarget == null)
			return;
		
		customMorphTarget.clearSliderPresets(); // Explicit clear call
		main.data.customMorphTargets.remove(customMorphTarget);
		
		markChanged();
	}
	
	@FXML
	private void removePresetFromTarget() {
		MorphTarget target = getCurrentTarget();
		if (target == null)
			return;
		
		SliderPreset preset = lvTargetPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		target.removeSliderPreset(preset);
		
		updatePresetCounter();
		
		lvTargetPresets.requestFocus();
		
		markChanged();
	}
	
	@FXML
	private void addAllPresetsToTarget() {
		MorphTarget target = getCurrentTarget();
		if (target == null)
			return;
		
		int lastCount = target.getSliderPresets().size();
		for (int i = 0; i < main.data.sliderPresets.size(); i++) {
			SliderPreset preset = main.data.sliderPresets.get(i);
			target.addSliderPreset(preset);
		}
		target.sortPresets();
		
		int index = lvTargetPresets.getSelectionModel().getSelectedIndex();
		boolean indexVisible = MyUtils.isIndexVisible(lvTargetPresets, index);
		if (!indexVisible)
			lvTargetPresets.scrollTo(index);
		
		updatePresetCounter();
		
		int newCount = target.getSliderPresets().size();
		if (lastCount != newCount)
			markChanged();
	}
	
	@FXML
	protected void showPopupImageView() {
		if (popupImageView.isShowing())
			return;
		
		popupImageView.show();
	}
	
	@FXML
	private void removeSelectedNpc() {
		NPC npc = tvNpc.getSelectionModel().getSelectedItem();
		if (npc == null)
			return;
		
		npc.clearSliderPresets(); // Explicit clear call
		main.data.morphedNpcs.remove(npc);
		updateNpcCounter();
		
		markChanged();
	}
	
	@FXML
	private void generateMorphs() {
		if (main.data.customMorphTargets.size() <= 0 && main.data.morphedNpcs.size() <= 0) {
			notif.show("You don't have any morphs in the list, add some morph targets first!");
			taMorphsGen.setText("");
			taMorphsGen.positionCaret(0);
			return;
		}
		
		mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Morphs...");
		try {
			Task<Void> task = generateMorphsTask();
			task.setOnSucceeded(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Morphs done.");
				mainPane.setDisable(false);
				
				// If there are targets without any presets, notify
				popupNoPresetNotifController.notify(targetsWithoutPresets);
			});
			task.setOnFailed(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Morphs failed.");
				mainPane.setDisable(false);
				
				notif.showError("Generating Morphs failed.");
			});
			task.setOnCancelled(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Morphs cancelled.");
				mainPane.setDisable(false);
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
	}
	
	private Task<Void> generateMorphsTask() throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				doGenerateMorphs();
				
				return null;
			}
		};
	}
	
	ArrayList<MorphTarget> targetsWithoutPresets = new ArrayList<MorphTarget>();
	private void doGenerateMorphs() {
		targetsWithoutPresets.clear();
		
		String newLine = System.getProperty("line.separator");
		String morph = "";
		
		ArrayList<String> morphLineList = new ArrayList<String>();
		for (int i = 0; i < main.data.customMorphTargets.size(); i++) {
			MorphTarget target = main.data.customMorphTargets.get(i);
			String s = target.toLine();
			morphLineList.add(s);
			
			if (!target.hasPresets())
				targetsWithoutPresets.add(target);
		}
		ArrayList<NPC> morphedNpcs = new ArrayList<NPC>();
		morphedNpcs.addAll(main.data.morphedNpcs);
		morphedNpcs.sort(comparatorNpcByMod);
		for (int i = 0; i < morphedNpcs.size(); i++) {
			MorphTarget target = morphedNpcs.get(i);
			String s = target.toLine();
			morphLineList.add(s);
			
			if (!target.hasPresets())
				targetsWithoutPresets.add(target);
		}
		
		for (int i = 0; i < morphLineList.size(); i++) {
			String line = morphLineList.get(i);
			morph = morph + line + newLine;
		}
		
		morphLineList.clear();
		morphedNpcs.clear();
		
		taMorphsGen.setText(morph);
		taMorphsGen.positionCaret(0);
	}
	
	private Comparator<? super NPC> comparatorNpcByMod = new Comparator<NPC>() {
        @Override
        public int compare(NPC npc1, NPC npc2) {
            return npc1.getMod().compareToIgnoreCase(npc2.getMod());
        }
    };
	
	@FXML
	private void copyMorphs() {
		String text = taMorphsGen.getText();
		if (!text.isEmpty()) {
			final ClipboardContent content = new ClipboardContent();
			content.putString(text);
			Clipboard.getSystemClipboard().setContent(content);
			
			notif.show("Morphs copied to clipboard!");
		} else {
			notif.show("There is nothing in the output, add and generate a morph first!");
		}
	}
	
	public MorphTarget getCurrentTarget() {
		MorphTarget target = lvCustomTargets.getSelectionModel().getSelectedItem();
		if (target == null) // No selection in custom targets, try NPCs
			target = tvNpc.getSelectionModel().getSelectedItem();
		
		return target;
	}
	
	@FXML
	private void showConfirmNewFile() {
		if (changed) {
			confirmNewFile.show();
		} else { // Just reset to newFile
			newFile();
		}
	}
	
	private void newFile() {
		// Disconnect View items from lists, so that list can be modified from another thread
		disconnectViews();
		// Reset all lists
		main.data.reset();
		reset();
		// Reconnect Views to lists
		connectViews();
	}
	
	@FXML
	private void showConfirmOpenFile() {
		if (changed) {
			confirmOpenFile.show();
		} else {
			openFromFile();
		}
	}
	
	private void openFromFile() {
		File file;
		fcFile.setTitle("Open jBS2BG File");
		try {
			fcFile.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_FOLDER, new File(".").getAbsolutePath())));
			file = fcFile.showOpenDialog(stage);
		} catch (Exception e) {
			fcFile.setInitialDirectory(main.data.homeDir);
			file = fcFile.showOpenDialog(stage);
		}
		if (file != null) {
			main.data.prefs.put(main.data.LAST_USED_FOLDER, file.getParent());
			
			mainPane.setDisable(true);
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Opening jBS2BG file...");
			// Disconnect View items from lists, so that list can be modified from another thread
			disconnectViews();
			// Reset all lists
			main.data.reset();
			reset();
			try {
				Task<Void> task = openFromFileTask(file);
				task.setOnSucceeded(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "Opening jBS2BG file done.");
					mainPane.setDisable(false);
					
					connectViews();
					// Append file name to title
					if (main.data.currentFile != null)
						stage.setTitle(main.appName + " - " + main.data.currentFile.getName());
					
					// If there are targets without any presets, notify
					popupNoPresetNotifController.notify(targetsWithoutPresets);
				});
				task.setOnFailed(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "Opening jBS2BG file failed.");
					mainPane.setDisable(false);
					
					connectViews();
					
					notif.showError("Opening jBS2BG file failed.");
				});
				task.setOnCancelled(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "Opening jBS2BG file cancelled.");
					mainPane.setDisable(false);
					
					connectViews();
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
	
	private Task<Void> openFromFileTask(File file) throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				main.data.openFromFile(file);
				
				doGenerateTemplates();
				doGenerateMorphs();
				
				return null;
			}
		};
	}
	
	@FXML
	private void save() {
		File saveFile;
		if (main.data.currentFile == null) { // There is currently no opened file
			File file;
			fcFile.setTitle("Save jBS2BG File");
			try {
				fcFile.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_FOLDER, new File(".").getAbsolutePath())));
				file = fcFile.showSaveDialog(stage);
			} catch (Exception e) {
				fcFile.setInitialDirectory(main.data.homeDir);
				file = fcFile.showSaveDialog(stage);
			}
			
			if (file == null) // Cancelled
				return;
			
			main.data.prefs.put(main.data.LAST_USED_FOLDER, file.getParent());
			saveFile = file;
		} else {
			saveFile = main.data.currentFile;
		}

		if (!saveFile.getAbsolutePath().endsWith(".jbs2bg"))
			saveFile = new File(saveFile.getAbsolutePath() + ".jbs2bg");

		mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, "Saving jBS2BG file...");
		try {
			if (!saveFile.getAbsolutePath().endsWith(".jbs2bg"))
				saveFile = new File(saveFile.getAbsolutePath() + ".jbs2bg");
			
			Task<Void> task = saveToFileTask(saveFile);
			task.setOnSucceeded(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Saving jBS2BG file done.");
				mainPane.setDisable(false);
				
				// Append file name to title
				if (main.data.currentFile != null)
					stage.setTitle(main.appName + " - " + main.data.currentFile.getName());
				
				changed = false;
			});
			task.setOnFailed(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Saving jBS2BG file failed.");
				mainPane.setDisable(false);
				
				notif.showError("Saving jBS2BG file failed.");
			});
			task.setOnCancelled(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Saving jBS2BG file cancelled.");
				mainPane.setDisable(false);
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
	}
	
	@FXML
	private void saveToFile() {
		File saveFile;
		fcFile.setTitle("Save jBS2BG File");
		try {
			fcFile.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_FOLDER, new File(".").getAbsolutePath())));
			saveFile = fcFile.showSaveDialog(stage);
		} catch (Exception e) {
			fcFile.setInitialDirectory(main.data.homeDir);
			saveFile = fcFile.showSaveDialog(stage);
		}
		if (saveFile != null) {
			main.data.prefs.put(main.data.LAST_USED_FOLDER, saveFile.getParent());
			
			mainPane.setDisable(true);
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Saving jBS2BG file...");
			try {
				if (!saveFile.getAbsolutePath().endsWith(".jbs2bg"))
					saveFile = new File(saveFile.getAbsolutePath() + ".jbs2bg");
				
				Task<Void> task = saveToFileTask(saveFile);
				task.setOnSucceeded(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "Saving jBS2BG file done.");
					mainPane.setDisable(false);
					
					// Append file name to title
					if (main.data.currentFile != null)
						stage.setTitle(main.appName + " - " + main.data.currentFile.getName());
					
					changed = false;
				});
				task.setOnFailed(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "Saving jBS2BG file failed.");
					mainPane.setDisable(false);
					
					notif.showError("Saving jBS2BG file failed.");
				});
				task.setOnCancelled(e -> {
					Logger.getLogger(getClass().getName()).log(Level.INFO, "Saving jBS2BG file cancelled.");
					mainPane.setDisable(false);
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
	
	private Task<Void> saveToFileTask(File file) throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				main.data.saveToFile(file);
				
				return null;
			}
		};
	}
	
	@FXML
	private void export() {
		File targetDir;
		try {
			fcExport.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_INI_FOLDER, new File(".").getAbsolutePath())));
			targetDir = fcExport.showDialog(stage);
		} catch (Exception e) {
			fcExport.setInitialDirectory(main.data.homeDir);
			targetDir = fcExport.showDialog(stage);
		}
		if (targetDir == null)
			return;

		if (!targetDir.exists())
			return;

		if (!targetDir.isDirectory())
			return;
        
		main.data.prefs.put(main.data.LAST_USED_INI_FOLDER, targetDir.getAbsolutePath());
        
        mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting Templates and Morphs INI...");
		try {
			Task<Void> task = exportTask(targetDir);
			task.setOnSucceeded(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting Templates and Morphs INI done.");
				mainPane.setDisable(false);
				
				//if (popupImageView.isShowing())
				//	popupImageView.hide();
				
				notif.show("Templates and Morphs INI exported!");
				
				// If there are targets without any presets, notify
				popupNoPresetNotifController.notify(targetsWithoutPresets);
			});
			task.setOnFailed(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting Templates and Morphs INI failed.");
				mainPane.setDisable(false);
				
				notif.showError("Exporting Templates and Morphs INI failed.");
			});
			task.setOnCancelled(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting Templates and Morphs INI cancelled.");
				mainPane.setDisable(false);
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
	}
	
	private Task<Void> exportTask(File targetDir) throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				doExport(targetDir);
				
				return null;
			}
		};
	}
	
	/**
	 * Generate templates and morphs to the textAreas first before calling.
	 * @param targetDir
	 */
	private void doExport(File targetDir) {
		doGenerateTemplates();
        doGenerateMorphs();
        
		String templatesString = taTemplatesGen.getText();
		String morphsString = taMorphsGen.getText();
		
		// For some reason, TextArea uses "\n" or Unix LF for new lines,
		// Replace "\n"'s with ("line.separator") to convert lines to CR LF.
		String newLine = System.getProperty("line.separator");
		templatesString = templatesString.replace("\n", newLine);
		morphsString = morphsString.replace("\n", newLine);
		
		try {
			File templatesFile = new File(targetDir.getAbsolutePath() + "/templates.ini");
			File morphsFile = new File(targetDir.getAbsolutePath() + "/morphs.ini");
			
			if (templatesFile.exists())
				FileUtils.deleteQuietly(templatesFile);
			if (morphsFile.exists())
				FileUtils.deleteQuietly(morphsFile);
			
			FileUtils.writeStringToFile(templatesFile, templatesString, main.data.encoding);
			FileUtils.writeStringToFile(morphsFile, morphsString, main.data.encoding);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@FXML
	private void exportBosJson() {
		File targetDir;
		try {
			fcExportBosJson.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_JSON_FOLDER, new File(".").getAbsolutePath())));
			targetDir = fcExportBosJson.showDialog(stage);
		} catch (Exception e) {
			fcExportBosJson.setInitialDirectory(main.data.homeDir);
			targetDir = fcExportBosJson.showDialog(stage);
		}
		if (targetDir == null)
			return;

		if (!targetDir.exists())
			return;

		if (!targetDir.isDirectory())
			return;
        
		main.data.prefs.put(main.data.LAST_USED_JSON_FOLDER, targetDir.getAbsolutePath());
        
        mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON files...");
		try {
			Task<Void> task = exportBosJsonTask(targetDir);
			task.setOnSucceeded(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON files done.");
				mainPane.setDisable(false);
				
				notif.show("BodyTypes of Skyrim JSON files exported!");
			});
			task.setOnFailed(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON files failed.");
				mainPane.setDisable(false);
				
				notif.showError("Exporting BoS JSON files failed.");
			});
			task.setOnCancelled(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON fiels cancelled.");
				mainPane.setDisable(false);
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
	}
	
	private Task<Void> exportBosJsonTask(File targetDir) throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				for (int i = 0; i < main.data.sliderPresets.size(); i++) {
					SliderPreset sliderPreset = main.data.sliderPresets.get(i);
					
					String bosJson = sliderPreset.toBosJson();
					try {
						File file = new File(targetDir.getAbsolutePath() + "/" + sliderPreset.getName() + ".json");
						
						if (file.exists())
							FileUtils.deleteQuietly(file);
						
						FileUtils.writeStringToFile(file, bosJson, main.data.encoding);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				return null;
			}
		};
	}
	
	private void reset() {
		changed = false;

		stage.setTitle(main.appName);
		
		taTemplate.setText("");
		taTemplate.positionCaret(0);
		taTemplatesGen.setText("");
		taTemplatesGen.positionCaret(0);
		taMorphsGen.setText("");
		taMorphsGen.positionCaret(0);
		tfCustomTarget.setText("");
		tfCustomTarget.positionCaret(0);
		
		lvPresets.getSelectionModel().clearSelection();
		lvCustomTargets.getSelectionModel().clearSelection();
		tvNpc.getSelectionModel().clearSelection();
		lvTargetPresets.getSelectionModel().clearSelection();
		
		updateNpcCounter();
		updatePresetCounter();
	}
	
	public void setOnKeyReleased(KeyCode keyCode) {
		switch (keyCode) {
			case A:
				//System.out.println("A: " + keyCode);
				break;
			default:
				//System.out.println("Default: " + keyCode.getName());
				break;
		}
	}
	
	private void setupKeyNavigation() {
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
		
		lvCustomTargets.setOnKeyTyped(new KeyNavigationListener() {
			@Override
			public void test() {
				for (int i = 0; i < lvCustomTargets.getItems().size(); i++) {
					CustomMorphTarget item = lvCustomTargets.getItems().get(i);
					if (item.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						lvCustomTargets.getSelectionModel().select(i);
						lvCustomTargets.getFocusModel().focus(i);
						
						boolean indexVisible = MyUtils.isIndexVisible(lvCustomTargets, i);
						if (!indexVisible)
							lvCustomTargets.scrollTo(i);
						
						found = true;
						break;
					}
				}
			}
		});

		tvNpc.setOnKeyTyped(new KeyNavigationListener() {
			@Override
			public void test() {
				String colName = tvNpc.getColumns().get(0).getText(); // Use the first column's data for searching
				
				for (int i = 0; i < npcTableFilter.getFilteredList().size(); i++) {
					NPC npc = npcTableFilter.getFilteredList().get(i);
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
					} else if (colName.equalsIgnoreCase("Slider Presets")) {
						text = npc.getSliderPresetsString().get();
					} else {
						text = npc.getName();
					}
					
					if (text.toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						tvNpc.getSelectionModel().select(npc);
						tvNpc.scrollTo(npc);
						found = true;
						break;
					}
				}
			}
		});
		
		lvTargetPresets.setOnKeyTyped(new KeyNavigationListener() {
			@Override
			public void test() {
				for (int i = 0; i < lvTargetPresets.getItems().size(); i++) {
					SliderPreset item = lvTargetPresets.getItems().get(i);
					if (item.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						lvTargetPresets.getSelectionModel().select(i);
						lvTargetPresets.getFocusModel().focus(i);
						
						boolean indexVisible = MyUtils.isIndexVisible(lvTargetPresets, i);
						if (!indexVisible)
							lvTargetPresets.scrollTo(i);
						
						found = true;
						break;
					}
				}
			}
		});
	}
}