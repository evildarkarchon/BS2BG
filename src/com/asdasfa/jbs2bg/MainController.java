package com.asdasfa.jbs2bg;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;
import com.asdasfa.jbs2bg.filtering.NpcTableColumns;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.VisibleScopeCommands;
import com.asdasfa.jbs2bg.fx.DialogGraphics;
import com.asdasfa.jbs2bg.fx.FilteredTableAdapter;
import com.asdasfa.jbs2bg.presentation.BosArtifactPublisher;
import com.asdasfa.jbs2bg.presentation.BosJsonArtifact;
import com.asdasfa.jbs2bg.presentation.ProjectGeneratedOutput;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.presentation.ProjectPresentationUpdate;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectEdit;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetImportOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
	protected ListView<SliderPresetSnapshot> lvPresets;
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
	private ListView<CustomMorphTargetSnapshot> lvCustomTargets;
	@FXML
	private TextField tfCustomTarget;
	@FXML
	private Label lblNpcCounter;
	@FXML
	protected TableView<NpcMorphAssignmentSnapshot> tvNpc;
	@FXML
	private TableColumn<NpcMorphAssignmentSnapshot, String> tcName;
	@FXML
	private TableColumn<NpcMorphAssignmentSnapshot, String> tcMaster;
	@FXML
	private TableColumn<NpcMorphAssignmentSnapshot, String> tcRace;
	@FXML
	private TableColumn<NpcMorphAssignmentSnapshot, String> tcEditorId;
	@FXML
	private TableColumn<NpcMorphAssignmentSnapshot, String> tcFormId;
	@FXML
	private TableColumn<NpcMorphAssignmentSnapshot, String> tcSliderPresets;
	@FXML
	protected ListView<SliderPresetSnapshot> lvTargetPresets;
	@FXML
	private Label lblTargetName;
	@FXML
	private Label lblPresetCounter;
	@FXML
	private TextArea taMorphsGen;
	// ^ Morphs ^
	
	/**
	 * Public-JavaFX adapter over the NPC Morph Assignment table; every bulk
	 * command freezes its scope through {@link FilteredTableAdapter#visibleSet()}.
	 * Package-visible because the Fill Empty popup freezes the same scope.
	 */
	FilteredTableAdapter<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> npcTable;

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
	
	/**
	 * Called AFTER all FXML fields are injected.
	 */
	@Override
	public void initialize(URL url, ResourceBundle rb) {
		// The adapter needs the injected table and columns; the source list is connected in connectViews.
		npcTable = FilteredTableAdapter.attach(tvNpc, NpcTableColumns.npcMorphAssignments(),
				ProjectIdentities::npcMorphAssignment);
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
		for (Settings.Diagnostic diagnostic : main.settingsInitialization.getDiagnostics()) {
			Logger.getLogger(Settings.class.getName()).log(Level.WARNING,
					diagnostic.getCode() + ": " + diagnostic.getSource() + " " + diagnostic.getPath()
							+ System.lineSeparator() + diagnostic.getMessage());
		}
		if (!main.settingsInitialization.isSuccessful()) {
			Settings.Failure failure = main.settingsInitialization.getFailure().orElseThrow();
			notif.showError("Invalid Settings files detected!" + System.lineSeparator()
					+ failure.formatForDisplay());
			Platform.exit();
			return;
		}
		
		cbUUNP.setDisable(true);
		cbUUNP.selectedProperty().addListener(new ChangeListener<Boolean>() {
			/** Publishes a UUNP toggle as one Slider Preset edit. */
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				SliderPresetSnapshot preset = lvPresets.getSelectionModel().getSelectedItem();
				if (preset == null)
					return;
				
				if (preset.isUunp() != cbUUNP.isSelected()) { // Only mark changed if toggled
					applyProjectEdit(SliderPresetEdits.setUunp(preset.getName(), cbUUNP.isSelected()));
					
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
		stage.setTitle(main.projectPresentation.getWindowTitle());
		
		// Don't allow closing if mainPane is disabled, meaning doing some tasks
		stage.setOnCloseRequest(e -> {
			if (mainPane.isDisabled())
				e.consume();
			
			if (main.projectPresentation.requiresDiscardConfirmation()) {
				e.consume();
				confirmExit.show();
			}
		});
		stage.getScene().cursorProperty().bind(Bindings.when(mainPane.disabledProperty()).then(Cursor.WAIT).otherwise(Cursor.DEFAULT));
	}
	
	/** Clears every generated Project-derived output cache owned by presentation. */
	private void invalidateGeneratedOutput() {
		taTemplate.clear();
		taTemplatesGen.clear();
		taMorphsGen.clear();
		if (popupBosViewController != null)
			popupBosViewController.invalidateGeneratedOutput();
		if (popupNoPresetNotifController != null)
			popupNoPresetNotifController.invalidateGeneratedOutput();
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
	
	/** Configures JavaFX views over immutable Project snapshot values. */
	private void setupViews() {
		/*lvPresets.setCellFactory(new Callback<ListView<SliderPresetSnapshot>, ListCell<SliderPresetSnapshot>>() {
			@Override
			public ListCell<SliderPresetSnapshot> call(ListView<SliderPresetSnapshot> param) {
				ListCell<SliderPresetSnapshot> cell = new ListCell<SliderPresetSnapshot>() {
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
				};
				return cell;
			}
		});*/
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
		lvPresets.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			SliderPresetSnapshot preset = lvPresets.getSelectionModel().getSelectedItem();
			if (preset == null) {
				cbUUNP.setDisable(true);
				cbUUNP.setSelected(false);
				return;
			}
			
			cbUUNP.setDisable(false);
			cbUUNP.setSelected(preset.isUunp());
			updateTemplateText();
		});
		
		lvCustomTargets.setCellFactory(p ->
			new ListCell<CustomMorphTargetSnapshot>() {
				@Override
				protected void updateItem(CustomMorphTargetSnapshot item, boolean empty) {
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
				lvTargetPresets.setItems(resolveAssignedSliderPresets(newSelection.getSliderPresetNames()));
				updatePresetCounter();
			} else {
				lblTargetName.setText("-null-");
				lvTargetPresets.setItems(null);
				updatePresetCounter();
			}
		});
		
		lvTargetPresets.setCellFactory(p ->
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
		lvTargetPresets.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			if (newSelection != null) {
			} else {
			}
		});
		
		
		tvNpc.setPlaceholder(new Label("EMPTY"));
		tvNpc.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		// Sorting re-renders through the adapter, which reveals the selected row afterwards.
		tvNpc.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			if (newSelection != null) {
				lvCustomTargets.getSelectionModel().clearSelection();
				tfCustomTarget.setText("");
				
				lblTargetName.setText(newSelection.getDisplayName());
				lvTargetPresets.setItems(resolveAssignedSliderPresets(newSelection.getSliderPresetNames()));
				updatePresetCounter();
				
				popupImageViewController.setTitle(newSelection.getDisplayName());
				popupImageViewController.setImage(findNpcImageFile(newSelection));
			} else {
				lblTargetName.setText("-null-");
				lvTargetPresets.setItems(null);
				updatePresetCounter();
				
				popupImageViewController.setTitle("");
				popupImageViewController.setImage(null);
			}
		});
		tcName.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("displayName"));
		tcMaster.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("pluginName"));
		tcRace.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("race"));
		tcEditorId.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("editorId"));
		tcFormId.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("formId"));
		tcSliderPresets.setCellValueFactory(col ->
				new ReadOnlyStringWrapper(String.join("|", col.getValue().getSliderPresetNames())));
		
		updateNpcCounter();
	}
	
	/**
	 * Connect the views to their lists.
	 */
	private void connectViews() {
		lvPresets.setItems(main.projectPresentation.getSliderPresets());
		
		npcTable.setSource(main.projectPresentation.getNpcMorphAssignments());

		lvCustomTargets.setItems(main.projectPresentation.getCustomMorphTargets());
		
		popupSliderPresetsController.connectViews();
		popupSliderPresetsFillController.connectViews();
		
		updateNpcCounter();
	}
	
	/**
	 * Renders exactly the snapshot returned by a typed ProjectSession outcome on
	 * the JavaFX thread and realizes presentation-only diagnostics and invalidation.
	 *
	 * @param outcome completed synchronous ProjectSession operation
	 * @return the rendered outcome for typed callback decisions
	 */
	private ProjectOutcome renderProjectOutcome(ProjectOutcome outcome) {
		SliderPresetSnapshot selectedPreset = lvPresets.getSelectionModel().getSelectedItem();
		String selectedPresetName = selectedPreset == null ? null : selectedPreset.getName();
		CustomMorphTargetSnapshot selectedCustomTarget = lvCustomTargets.getSelectionModel().getSelectedItem();
		String selectedCustomTargetName = selectedCustomTarget == null ? null : selectedCustomTarget.getName();
		NpcMorphAssignmentSnapshot selectedNpc = tvNpc.getSelectionModel().getSelectedItem();
		NpcMorphAssignmentIdentity selectedNpcIdentity = identityOf(selectedNpc);
		SliderPresetSnapshot selectedTargetPreset = lvTargetPresets.getSelectionModel().getSelectedItem();
		String selectedTargetPresetName = selectedTargetPreset == null ? null : selectedTargetPreset.getName();
		ProjectPresentationUpdate update = main.projectPresentation.render(outcome);
		// Generated text must be cleared before selection listeners recreate the
		// selected preview from the newly returned snapshot.
		if (update.invalidatesGeneratedOutput())
			invalidateGeneratedOutput();
		selectSliderPreset(selectedPresetName);
		if (selectedCustomTargetName != null)
			selectCustomTarget(selectedCustomTargetName);
		else if (selectedNpcIdentity != null)
			selectNpc(selectedNpcIdentity);
		selectTargetPreset(selectedTargetPresetName);
		// Re-selecting an unchanged preset instance fires no selection event, so the
		// preview cleared above must be rebuilt explicitly from the published snapshot.
		if (update.invalidatesGeneratedOutput())
			updateTemplateText();
		stage.setTitle(main.projectPresentation.getWindowTitle());
		if (update.hasDiagnostics()) {
			if (update.hasErrorDiagnostics())
				notif.showError(update.getDiagnosticText());
			else
				notif.show(update.getDiagnosticText());
		}
		updateNpcCounter();
		updatePresetCounter();
		return outcome;
	}

	/**
	 * Applies one validated Project edit and renders exactly its returned snapshot.
	 *
	 * @param edit immutable user action to validate and publish atomically
	 * @return the typed session outcome used by popup callback decisions
	 */
	ProjectOutcome applyProjectEdit(ProjectEdit edit) {
		return renderProjectOutcome(main.projectSession.apply(edit));
	}

	/** Restores a Slider Preset selection by stable case-insensitive name. */
	void selectSliderPreset(String name) {
		if (name == null)
			return;
		for (SliderPresetSnapshot preset : main.projectPresentation.getSliderPresets()) {
			if (preset.getName().equalsIgnoreCase(name)) {
				lvPresets.getSelectionModel().select(preset);
				return;
			}
		}
	}

	/** Restores a Custom Morph Target selection by stable case-insensitive name. */
	private void selectCustomTarget(String name) {
		for (CustomMorphTargetSnapshot target : main.projectPresentation.getCustomMorphTargets()) {
			if (target.getName().equalsIgnoreCase(name)) {
				lvCustomTargets.getSelectionModel().select(target);
				return;
			}
		}
	}

	/** Restores an NPC Morph Assignment selection by its logical identity, if it is visible. */
	void selectNpc(NpcMorphAssignmentIdentity identity) {
		if (identity == null)
			return;
		npcTable.select(identity);
	}

	/** Restores an assigned Slider Preset selection after its target is restored. */
	void selectTargetPreset(String name) {
		if (name == null || lvTargetPresets.getItems() == null)
			return;
		for (SliderPresetSnapshot preset : lvTargetPresets.getItems()) {
			if (preset.getName().equalsIgnoreCase(name)) {
				int index = lvTargetPresets.getItems().indexOf(preset);
				lvTargetPresets.getSelectionModel().select(index);
				lvTargetPresets.getFocusModel().focus(index);
				// scrollTo is the minimal scroll in JavaFX 25: a no-op when the row is already visible.
				lvTargetPresets.scrollTo(index);
				return;
			}
		}
	}

	/** Builds the stable identity used by every NPC edit and selection restoration. */
	private static NpcMorphAssignmentIdentity identityOf(NpcMorphAssignmentSnapshot npc) {
		return npc == null ? null : new NpcMorphAssignmentIdentity(npc.getPluginName(), npc.getEditorId());
	}

	/**
	 * Resolves relationship names to immutable Slider Presets from the currently
	 * rendered snapshot for one target-selection view.
	 *
	 * @param assignedNames canonical relationship names from an immutable target
	 * @return presentation-owned observable list containing immutable values
	 * @throws IllegalArgumentException when a rendered relationship is unresolved
	 */
	private ObservableList<SliderPresetSnapshot> resolveAssignedSliderPresets(List<String> assignedNames) {
		ObservableList<SliderPresetSnapshot> resolved = FXCollections.observableArrayList();
		for (String assignedName : assignedNames) {
			SliderPresetSnapshot preset = findSliderPreset(assignedName);
			if (preset == null)
				throw new IllegalArgumentException(
						"Snapshot contains an unresolved Slider Preset assignment: " + assignedName);
			resolved.add(preset);
		}
		return resolved;
	}

	/** Resolves one Slider Preset in the latest rendered snapshot without regard to case. */
	private SliderPresetSnapshot findSliderPreset(String name) {
		for (SliderPresetSnapshot preset : main.projectPresentation.getSliderPresets()) {
			if (preset.getName().equalsIgnoreCase(name))
				return preset;
		}
		return null;
	}

	/**
	 * Resolves an optional NPC preview image from immutable display values without
	 * adding image-cache state to the Project snapshot.
	 *
	 * @param npc immutable NPC Morph Assignment selected for preview
	 * @return the first matching local image, or null when none exists
	 */
	static File findNpcImageFile(NpcMorphAssignmentSnapshot npc) {
		return findNpcImageFile(npc.getDisplayName(), npc.getEditorId());
	}

	/**
	 * Resolves an optional NPC preview image from presentation display values.
	 *
	 * @param displayName NPC display name used by image-file conventions
	 * @param editorId NPC editor ID used by image-file conventions
	 * @return the first matching local image, or null when none exists
	 */
	static File findNpcImageFile(String displayName, String editorId) {
		String[] imageExtensions = { ".jpg", "jpeg", ".png", ".bmp" };
		for (String extension : imageExtensions) {
			File withEditorId = new File("images/" + displayName + " (" + editorId + ")"
					+ extension);
			if (withEditorId.exists())
				return withEditorId;
		}
		for (String extension : imageExtensions) {
			File withoutEditorId = new File("images/" + displayName + extension);
			if (withoutEditorId.exists())
				return withoutEditorId;
		}
		return null;
	}

	/** Clears assignments from whichever logical target is currently selected. */
	private void clearCurrentTargetAssignments() {
		CustomMorphTargetSnapshot customTarget = lvCustomTargets.getSelectionModel().getSelectedItem();
		NpcMorphAssignmentSnapshot npc = tvNpc.getSelectionModel().getSelectedItem();
		if (customTarget != null)
			applyProjectEdit(CustomMorphTargetEdits.clearSliderPresets(customTarget.getName()));
		else if (npc != null)
			applyProjectEdit(NpcMorphAssignmentEdits.clearSliderPresets(identityOf(npc)));
	}

	/**
	 * Assigns one Slider Preset to the selected target through the matching domain
	 * edit family.
	 *
	 * @param presetName existing Slider Preset name selected by the popup
	 * @return the rendered Project outcome
	 */
	ProjectOutcome addSliderPresetToCurrentTarget(String presetName) {
		CustomMorphTargetSnapshot customTarget = lvCustomTargets.getSelectionModel().getSelectedItem();
		if (customTarget != null)
			return applyProjectEdit(CustomMorphTargetEdits.addSliderPreset(customTarget.getName(), presetName));
		NpcMorphAssignmentSnapshot npc = tvNpc.getSelectionModel().getSelectedItem();
		if (npc != null)
			return applyProjectEdit(NpcMorphAssignmentEdits.addSliderPreset(identityOf(npc), presetName));
		throw new IllegalStateException("A morph target must remain selected while its preset popup is open");
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
			/** Clears the currently selected target's relationships atomically. */
			@Override
			public void ok() {
				clearCurrentTargetAssignments();
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
			/** Clears the complete Custom Morph Target catalog atomically. */
			@Override
			public void ok() {
				applyProjectEdit(CustomMorphTargetEdits.clear());
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
			/** Removes the frozen visible NPC Morph Assignments atomically. */
			@Override
			public void ok() {
				applyProjectEdit(VisibleScopeCommands.clearNpcMorphAssignments(npcTable.visibleSet()));
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
			/** Clears assignments for the frozen visible NPC Morph Assignments atomically. */
			@Override
			public void ok() {
				ProjectOutcome outcome = applyProjectEdit(VisibleScopeCommands.clearAssignments(npcTable.visibleSet()));
				if (!(outcome instanceof ChangedOutcome)) { // No NPC in the table was cleared
					notif.show("No NPC in the table was cleared!");
				}
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
			popupNoPresetNotif.getIcons().add(DialogGraphics.image(DialogGraphics.Semantic.WARNING, DialogGraphics.ICON_SIZE));
			
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
	
	/** Selects BodySlide XML sources, schedules their session import, and renders the aggregate outcome. */
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
			
			Task<SliderPresetImportOutcome> task = importSliderPresets(files);
			scheduleBackgroundTask(task, importOutcome -> {
				renderProjectOutcome(importOutcome.getProjectOutcome());
				Logger.getLogger(getClass().getName()).log(Level.INFO,
						isRejectedOrFailed(importOutcome.getProjectOutcome())
								? "XML parsing completed without a successful Project change."
								: "XML parsing done.");
			}, "XML parsing");
		} else {
		}
	}
	
	/**
	 * Creates a worker wrapper for one synchronous ProjectSession XML batch without
	 * letting the worker thread touch JavaFX controls or presentation projections.
	 *
	 * @param files selected BodySlide XML sources in chooser order
	 * @return background task carrying the aggregate and per-source outcomes
	 */
	private Task<SliderPresetImportOutcome> importSliderPresets(List<File> files) {
		List<Path> sources = new ArrayList<>();
		for (File file : files)
			sources.add(file.toPath());
		return new Task<SliderPresetImportOutcome>() {
			@Override
			protected SliderPresetImportOutcome call() {
				return main.projectSession.importSliderPresets(sources);
			}
		};
	}
	
	@FXML
	private void showConfirmClearPresets() {
		if (main.projectPresentation.getSliderPresets().isEmpty())
			return;
		
		confirmClearPresets.show();
	}
	
	/** Clears the Project Slider Preset catalog and its cascaded relationships. */
	private void clearPresets() {
		if (main.projectPresentation.getSliderPresets().isEmpty())
			return;

		applyProjectEdit(SliderPresetEdits.clear());
	}
	
	/** Confirms a delete only when the selected Slider Preset has relationships. */
	@FXML
	private void showConfirmRemovePreset() {
		SliderPresetSnapshot preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		boolean used = false;
		// Search custom morph targets
		for (CustomMorphTargetSnapshot target : main.projectPresentation.getCustomMorphTargets()) {
			if (target.getSliderPresetNames().contains(preset.getName())) {
				used = true;
				break;
			}
		}
		if (!used) { // Search NPCs
			for (NpcMorphAssignmentSnapshot npc : main.projectPresentation.getNpcMorphAssignments()) {
				if (npc.getSliderPresetNames().contains(preset.getName())) {
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
	
	/** Deletes the selected logical Slider Preset through the session cascade. */
	private void removeSelectedPreset() {
		SliderPresetSnapshot preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		applyProjectEdit(SliderPresetEdits.delete(preset.getName()));
	}
	
	/** Duplicates the selected Slider Preset and selects the returned copy. */
	@FXML
	private void duplicateSelectedPreset() {
		SliderPresetSnapshot preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		String duplicateName = preset.getName() + "(Dupe)";
		ProjectOutcome outcome = applyProjectEdit(SliderPresetEdits.duplicate(preset.getName(), duplicateName));
		if (outcome instanceof ChangedOutcome)
			selectSliderPreset(duplicateName);
	}
	
	@FXML
	private void showConfirmClearTargetPresets() {
		if (lvCustomTargets.getSelectionModel().getSelectedItem() == null
				&& tvNpc.getSelectionModel().getSelectedItem() == null)
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
		int count = main.projectPresentation.getNpcMorphAssignments().size();
		lblNpcCounter.setText("(" + count + ")");
	}
	
	/** Updates the selected target's relationship count from its immutable view list. */
	public void updatePresetCounter() {
		int count = lvTargetPresets.getItems() == null ? 0 : lvTargetPresets.getItems().size();
		
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
		if (lvCustomTargets.getSelectionModel().getSelectedItem() == null
				&& tvNpc.getSelectionModel().getSelectedItem() == null)
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
		for (NpcMorphAssignmentSnapshot npc : npcTable.visibleSet().getRows()) {
			if (npc.getSliderPresetNames().isEmpty()) { // Empty
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
	
	/** Renders the selected Slider Preset preview from the latest coherent Project snapshot. */
	public void updateTemplateText() {
		SliderPresetSnapshot preset = lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null) {
			taTemplate.setText("");
			return;
		}

		ProjectGeneratedOutput output = ProjectOutputFormatter.generate(main.projectPresentation.getSnapshot(),
				cbOmitRedundantSliders.isSelected());
		taTemplate.setText(output.getTemplateLinesByPresetName().get(preset.getName()));
		taTemplate.positionCaret(0);
    }
	
	/** Captures one Project snapshot and schedules Templates generation from it. */
	@FXML
	private void generateTemplates() {
		ProjectSnapshot outputSnapshot = main.projectPresentation.getSnapshot();
		boolean omitRedundantSliders = cbOmitRedundantSliders.isSelected();
		if (outputSnapshot.getSliderPresets().isEmpty()) {
			notif.show("You don't have any presets in the list, add some BodySlide XML presets first!");
			taTemplatesGen.setText("");
			taTemplatesGen.positionCaret(0);
			return;
		}
		
		// Capture occurs before dispatch so the worker cannot mix a later Project
		// render or read JavaFX controls off the application thread.
		Task<ProjectGeneratedOutput> task = generateProjectOutputTask(outputSnapshot, omitRedundantSliders);
		scheduleBackgroundTask(task, output -> {
			taTemplatesGen.setText(output.getTemplatesText());
			taTemplatesGen.positionCaret(0);
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Templates done.");
		}, "Generating Templates", "Generating Templates failed.");
	}

	/**
	 * Creates a worker that formats every Project-derived artifact from one pinned
	 * immutable snapshot.
	 *
	 * @param outputSnapshot coherent Project state captured before scheduling
	 * @param omitRedundantSliders Templates option captured before scheduling
	 * @return background task yielding immutable generated output
	 */
	private Task<ProjectGeneratedOutput> generateProjectOutputTask(ProjectSnapshot outputSnapshot,
			boolean omitRedundantSliders) {
		return new Task<ProjectGeneratedOutput>() {
			/** @return immutable output derived only from the captured Project snapshot */
			@Override
			public ProjectGeneratedOutput call() {
				return ProjectOutputFormatter.generate(outputSnapshot, omitRedundantSliders);
			}
		};
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
	
	/** Creates one Custom Morph Target with any random initial choice fixed before apply. */
	@FXML
	private void addCustomMorphTarget() {
		String name = tfCustomTarget.getText();
		name = name.trim();

		if (name.isEmpty())
			return;

		List<String> initialAssignments = new ArrayList<>();
		ObservableList<SliderPresetSnapshot> presets = main.projectPresentation.getSliderPresets();
		if (!presets.isEmpty()) {
			// Random choice belongs to the popup/controller boundary so replaying the
			// validated edit never changes its meaning.
			int random = MyUtils.random(0, presets.size() - 1);
			initialAssignments.add(presets.get(random).getName());
		}
		ProjectOutcome outcome = applyProjectEdit(CustomMorphTargetEdits.create(name, initialAssignments));
		if (outcome instanceof ChangedOutcome) {
			selectCustomTarget(name);
			tfCustomTarget.setText("");
		}
		
		tfCustomTarget.requestFocus();
		tfCustomTarget.positionCaret(tfCustomTarget.getText().length());
	}
	
	/** Deletes the selected Custom Morph Target by logical name. */
	@FXML
	private void removeSelectedCustomTarget() {
		CustomMorphTargetSnapshot customMorphTarget = lvCustomTargets.getSelectionModel().getSelectedItem();
		if (customMorphTarget == null)
			return;
		
		applyProjectEdit(CustomMorphTargetEdits.delete(customMorphTarget.getName()));
	}
	
	/** Removes the selected relationship from the active custom or NPC target. */
	@FXML
	private void removePresetFromTarget() {
		SliderPresetSnapshot preset = lvTargetPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;

		CustomMorphTargetSnapshot customTarget = lvCustomTargets.getSelectionModel().getSelectedItem();
		NpcMorphAssignmentSnapshot npc = tvNpc.getSelectionModel().getSelectedItem();
		if (customTarget != null)
			applyProjectEdit(CustomMorphTargetEdits.removeSliderPreset(customTarget.getName(), preset.getName()));
		else if (npc != null)
			applyProjectEdit(NpcMorphAssignmentEdits.removeSliderPreset(identityOf(npc), preset.getName()));
		lvTargetPresets.requestFocus();
	}
	
	/** Assigns the complete current preset catalog to the active target in one edit. */
	@FXML
	private void addAllPresetsToTarget() {
		CustomMorphTargetSnapshot customTarget = lvCustomTargets.getSelectionModel().getSelectedItem();
		NpcMorphAssignmentSnapshot npc = tvNpc.getSelectionModel().getSelectedItem();
		if (customTarget == null && npc == null)
			return;

		List<String> presetNames = new ArrayList<>();
		for (SliderPresetSnapshot preset : main.projectPresentation.getSliderPresets())
			presetNames.add(preset.getName());
		if (customTarget != null)
			applyProjectEdit(CustomMorphTargetEdits.addSliderPresets(customTarget.getName(), presetNames));
		else
			applyProjectEdit(NpcMorphAssignmentEdits.addSliderPresets(identityOf(npc), presetNames));
	}
	
	@FXML
	protected void showPopupImageView() {
		if (popupImageView.isShowing())
			return;
		
		popupImageView.show();
	}
	
	/** Deletes the selected NPC Morph Assignment by stable identity. */
	@FXML
	private void removeSelectedNpc() {
		NpcMorphAssignmentSnapshot npc = tvNpc.getSelectionModel().getSelectedItem();
		if (npc == null)
			return;
		
		applyProjectEdit(NpcMorphAssignmentEdits.removeNpc(identityOf(npc)));
	}
	
	/** Captures one Project snapshot and schedules Morphs generation from it. */
	@FXML
	private void generateMorphs() {
		ProjectSnapshot outputSnapshot = main.projectPresentation.getSnapshot();
		boolean omitRedundantSliders = cbOmitRedundantSliders.isSelected();
		if (outputSnapshot.getCustomMorphTargets().isEmpty()
				&& outputSnapshot.getNpcMorphAssignments().isEmpty()) {
			notif.show("You don't have any morphs in the list, add some morph targets first!");
			taMorphsGen.setText("");
			taMorphsGen.positionCaret(0);
			return;
		}
		
		Task<ProjectGeneratedOutput> task = generateProjectOutputTask(outputSnapshot, omitRedundantSliders);
		scheduleBackgroundTask(task, output -> {
			taMorphsGen.setText(output.getMorphsText());
			taMorphsGen.positionCaret(0);
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Generating Morphs done.");

			// If there are targets without any presets, notify
			popupNoPresetNotifController.notify(output);
		}, "Generating Morphs", "Generating Morphs failed.");
	}
	
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
	
	@FXML
	private void showConfirmNewFile() {
		if (main.projectPresentation.requiresDiscardConfirmation()) {
			confirmNewFile.show();
		} else { // Just reset to newFile
			newFile();
		}
	}
	
	/** Establishes and renders a clean untitled Project through ProjectSession. */
	private void newFile() {
		renderProjectOutcome(main.projectSession.newProject());
		reset();
	}
	
	@FXML
	private void showConfirmOpenFile() {
		if (main.projectPresentation.requiresDiscardConfirmation()) {
			confirmOpenFile.show();
		} else {
			openFromFile();
		}
	}
	
	/** Selects a Project file and schedules an atomic ProjectSession open operation. */
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
			
			Task<ProjectOutcome> task = openProject(file.toPath());
			scheduleBackgroundTask(task, outcome -> {
				renderProjectOutcome(outcome);
				if (!isRejectedOrFailed(outcome))
					reset();
				Logger.getLogger(getClass().getName()).log(Level.INFO,
						isRejectedOrFailed(outcome) ? "Opening jBS2BG file rejected or failed."
								: "Opening jBS2BG file done.");
			}, "Opening jBS2BG file");
		} else {
		}
	}
	
	/**
	 * Creates a worker wrapper for synchronous atomic Project opening without
	 * mutating JavaFX state on the worker thread.
	 *
	 * @param source selected Project file
	 * @return background task carrying the typed open outcome
	 */
	private Task<ProjectOutcome> openProject(Path source) {
		return new Task<ProjectOutcome>() {
			@Override
			protected ProjectOutcome call() {
				return main.projectSession.open(source);
			}
		};
	}

	/** Saves to the rendered file identity or delegates untitled Projects to Save As. */
	@FXML
	private void save() {
		if (main.projectPresentation.getSnapshot().getFileIdentity().isPresent()) {
			scheduleSave(saveProject());
			return;
		}
		Path target = chooseProjectSaveTarget();
		if (target != null)
			scheduleSave(saveProjectAs(target));
	}

	/** Selects a target and schedules an atomic ProjectSession Save As operation. */
	@FXML
	private void saveToFile() {
		Path target = chooseProjectSaveTarget();
		if (target != null)
			scheduleSave(saveProjectAs(target));
	}

	/**
	 * Keeps chooser state and extension normalization in presentation while
	 * returning the exact target supplied to ProjectSession Save As.
	 *
	 * @return normalized target path, or null when the chooser is cancelled
	 */
	private Path chooseProjectSaveTarget() {
		File saveFile;
		fcFile.setTitle("Save jBS2BG File");
		try {
			fcFile.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_FOLDER, new File(".").getAbsolutePath())));
			saveFile = fcFile.showSaveDialog(stage);
		} catch (Exception e) {
			fcFile.setInitialDirectory(main.data.homeDir);
			saveFile = fcFile.showSaveDialog(stage);
		}
		if (saveFile == null)
			return null;
		main.data.prefs.put(main.data.LAST_USED_FOLDER, saveFile.getParent());
		String target = saveFile.getAbsolutePath();
		if (!target.toLowerCase(Locale.ROOT).endsWith(".jbs2bg"))
			target += ".jbs2bg";
		return new File(target).toPath();
	}

	/** @return background task for saving to the adopted Project file identity */
	private Task<ProjectOutcome> saveProject() {
		return new Task<ProjectOutcome>() {
			@Override
			protected ProjectOutcome call() {
				return main.projectSession.save();
			}
		};
	}

	/**
	 * Creates a background task for atomic Save As while retaining scheduling in
	 * presentation.
	 *
	 * @param target chooser-selected normalized target
	 * @return background task carrying the typed Save As outcome
	 */
	private Task<ProjectOutcome> saveProjectAs(Path target) {
		return new Task<ProjectOutcome>() {
			@Override
			protected ProjectOutcome call() {
				return main.projectSession.saveAs(target);
			}
		};
	}

	/** Schedules a save task and distinguishes typed failure outcomes from task failure. */
	private void scheduleSave(Task<ProjectOutcome> task) {
		scheduleBackgroundTask(task, outcome -> {
			renderProjectOutcome(outcome);
			Logger.getLogger(getClass().getName()).log(Level.INFO,
					isRejectedOrFailed(outcome) ? "Saving jBS2BG file rejected or failed."
							: "Saving jBS2BG file done.");
		}, "Saving jBS2BG file");
	}

	/**
	 * Applies the shared JavaFX scheduling, busy, and unexpected-failure lifecycle
	 * around a synchronous domain operation.
	 *
	 * @param task worker task that invokes the synchronous operation
	 * @param success presentation callback for the task's typed value
	 * @param operation user-facing operation phrase for logging and errors
	 * @param <T> typed result returned by the operation
	 */
	private <T> void scheduleBackgroundTask(Task<T> task, Consumer<T> success, String operation) {
		scheduleBackgroundTask(task, success, operation, operation + " failed unexpectedly.");
	}

	/**
	 * Applies the shared JavaFX scheduling lifecycle while preserving an operation's
	 * established user-facing failure message.
	 *
	 * @param task worker task whose value is delivered on the JavaFX thread
	 * @param success presentation callback for the task's typed value
	 * @param operation user-facing operation phrase for start and cancellation logs
	 * @param failureMessage exact failure notification and log text
	 * @param <T> typed result returned by the operation
	 */
	private <T> void scheduleBackgroundTask(Task<T> task, Consumer<T> success, String operation,
			String failureMessage) {
		scheduleBackgroundTask(task, success, operation, ignored -> failureMessage);
	}

	/**
	 * Schedules a task whose domain exception carries user-facing structured diagnostics.
	 *
	 * @param task worker task whose exception may carry complete diagnostics
	 * @param success presentation callback for the task's typed value
	 * @param operation user-facing operation phrase for start and cancellation logs
	 * @param failureMessage prefix shown before the task's diagnostic message
	 * @param <T> typed result returned by the operation
	 */
	private <T> void scheduleDiagnosticBackgroundTask(Task<T> task, Consumer<T> success,
			String operation, String failureMessage) {
		scheduleBackgroundTask(task, success, operation,
				exception -> failureMessageFor(failureMessage, exception));
	}

	/**
	 * Applies the shared JavaFX scheduling lifecycle with an operation-owned failure formatter.
	 *
	 * @param task worker task whose value is delivered on the JavaFX thread
	 * @param success presentation callback for the task's typed value
	 * @param operation user-facing operation phrase for start and cancellation logs
	 * @param failureMessageFormatter maps the task exception to exact user-facing text
	 * @param <T> typed result returned by the operation
	 */
	private <T> void scheduleBackgroundTask(Task<T> task, Consumer<T> success, String operation,
			Function<Throwable, String> failureMessageFormatter) {
		mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, operation + "...");
		task.setOnSucceeded(e -> {
			mainPane.setDisable(false);
			success.accept(task.getValue());
		});
		task.setOnFailed(e -> {
			String detailedFailureMessage = failureMessageFormatter.apply(task.getException());
			Logger.getLogger(getClass().getName()).log(Level.INFO, detailedFailureMessage);
			mainPane.setDisable(false);
			notif.showError(detailedFailureMessage);
		});
		task.setOnCancelled(e -> {
			Logger.getLogger(getClass().getName()).log(Level.INFO, operation + " cancelled.");
			mainPane.setDisable(false);
		});
		task.exceptionProperty().addListener((obs, oldValue, newValue) -> {
			if (newValue != null)
				newValue.printStackTrace();
		});
		new Thread(task).start();
	}

	/** Includes structured formatter or publisher diagnostics in the visible task failure. */
	private static String failureMessageFor(String failureMessage, Throwable exception) {
		if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank())
			return failureMessage;
		return failureMessage + System.lineSeparator() + exception.getMessage();
	}

	/** @return true when a completed task carries a rejected or failed Project operation */
	private static boolean isRejectedOrFailed(ProjectOutcome outcome) {
		return outcome instanceof RejectedOutcome || outcome instanceof FailedOutcome;
	}
	
	/** Captures one Project snapshot before scheduling combined INI generation and writing. */
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
		ProjectSnapshot outputSnapshot = main.projectPresentation.getSnapshot();
		boolean omitRedundantSliders = cbOmitRedundantSliders.isSelected();
        
		Task<ProjectGeneratedOutput> task = exportTask(targetDir, outputSnapshot, omitRedundantSliders);
		scheduleBackgroundTask(task, output -> {
			taTemplatesGen.setText(output.getTemplatesText());
			taMorphsGen.setText(output.getMorphsText());
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting Templates and Morphs INI done.");

			//if (popupImageView.isShowing())
			//	popupImageView.hide();
				
			notif.show("Templates and Morphs INI exported!");
				
			// If there are targets without any presets, notify
			popupNoPresetNotifController.notify(output);
		}, "Exporting Templates and Morphs INI", "Exporting Templates and Morphs INI failed.");
	}

	/**
	 * Formats and writes both INI files from exactly one captured Project snapshot.
	 *
	 * @param targetDir selected output directory
	 * @param outputSnapshot coherent Project state captured before scheduling
	 * @param omitRedundantSliders Templates option captured before scheduling
	 * @return worker task yielding the immutable artifacts written to disk
	 */
	private Task<ProjectGeneratedOutput> exportTask(File targetDir, ProjectSnapshot outputSnapshot,
			boolean omitRedundantSliders) {
		return new Task<ProjectGeneratedOutput>() {
			/**
			 * @return the same immutable output written to both INI destinations
			 * @throws Exception when either INI destination cannot be written
			 */
			@Override
			public ProjectGeneratedOutput call() throws Exception {
				ProjectGeneratedOutput output = ProjectOutputFormatter.generate(outputSnapshot,
						omitRedundantSliders);
				writeIniOutputs(targetDir, output);
				return output;
			}
		};
	}

	/**
	 * Writes already-generated Templates and Morphs artifacts without consulting
	 * Project or JavaFX state.
	 *
	 * @param targetDir selected output directory
	 * @param output immutable artifacts derived from one Project snapshot
	 * @throws IOException when either output cannot be written
	 */
	private void writeIniOutputs(File targetDir, ProjectGeneratedOutput output) throws IOException {
		File templatesFile = new File(targetDir.getAbsolutePath() + "/templates.ini");
		File morphsFile = new File(targetDir.getAbsolutePath() + "/morphs.ini");
		
		if (templatesFile.exists())
			FileUtils.deleteQuietly(templatesFile);
		if (morphsFile.exists())
			FileUtils.deleteQuietly(morphsFile);
		
		FileUtils.writeStringToFile(templatesFile, output.getTemplatesText(), main.data.encoding);
		FileUtils.writeStringToFile(morphsFile, output.getMorphsText(), main.data.encoding);
	}
	
	/** Captures one Project snapshot before scheduling BoS artifact generation and writing. */
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
		ProjectSnapshot outputSnapshot = main.projectPresentation.getSnapshot();
		boolean omitRedundantSliders = cbOmitRedundantSliders.isSelected();
        
		Task<ProjectGeneratedOutput> task = exportBosJsonTask(targetDir, outputSnapshot,
				omitRedundantSliders);
		scheduleDiagnosticBackgroundTask(task, output -> {
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON files done.");
			String mappings = formatBosFileNameMappings(output);
			Logger.getLogger(getClass().getName()).log(Level.INFO, mappings);
			notif.show("BodyTypes of Skyrim JSON files exported!" + System.lineSeparator() + mappings);
		}, "Exporting BoS JSON files", "Exporting BoS JSON files failed.");
	}

	/**
	 * Writes BoS files produced from one pinned Project snapshot without reading UI
	 * state from the worker thread.
	 *
	 * @param targetDir selected output directory
	 * @param outputSnapshot coherent Project state captured before scheduling
	 * @param omitRedundantSliders captured formatter option
	 * @return worker task that writes every generated BoS artifact
	 */
	private Task<ProjectGeneratedOutput> exportBosJsonTask(File targetDir, ProjectSnapshot outputSnapshot,
			boolean omitRedundantSliders) {
		return new Task<ProjectGeneratedOutput>() {
			/**
			 * @return the immutable output after every captured BoS artifact is published
			 * @throws Exception when any BoS destination cannot be written
			 */
			@Override
			public ProjectGeneratedOutput call() throws Exception {
				ProjectGeneratedOutput output = ProjectOutputFormatter.generate(outputSnapshot,
						omitRedundantSliders);
				BosArtifactPublisher.publishAll(targetDir.toPath(), output);
				return output;
			}
		};
	}

	/** Formats every successful source-to-filename mapping for logs and notification. */
	private static String formatBosFileNameMappings(ProjectGeneratedOutput output) {
		StringBuilder mappings = new StringBuilder("BoS filename mappings:");
		for (BosJsonArtifact artifact : output.getBosJsonArtifacts()) {
			mappings.append(System.lineSeparator())
					.append(artifact.getFileNameMapping().formatForDisplay());
		}
		return mappings.toString();
	}
	
	/** Resets transient controls after a successful New Project or Open render. */
	private void reset() {
		stage.setTitle(main.projectPresentation.getWindowTitle());
		
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
					SliderPresetSnapshot item = lvPresets.getItems().get(i);
					if (item.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						lvPresets.getSelectionModel().select(i);
						lvPresets.getFocusModel().focus(i);
						// scrollTo is the minimal scroll in JavaFX 25: a no-op when the row is already visible.
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
					CustomMorphTargetSnapshot item = lvCustomTargets.getItems().get(i);
					if (item.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						lvCustomTargets.getSelectionModel().select(i);
						lvCustomTargets.getFocusModel().focus(i);
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
				// Use the first (leftmost, possibly reordered) column's data for searching; the
				// adapter derives the same cell text the column renders and the filter sees.
				TableColumn<NpcMorphAssignmentSnapshot, ?> leadingColumn = tvNpc.getColumns().get(0);

				for (NpcMorphAssignmentSnapshot npc : tvNpc.getItems()) {
					String text = npcTable.cellTextOf(leadingColumn, npc);

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
					SliderPresetSnapshot item = lvTargetPresets.getItems().get(i);
					if (item.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
						if (searchTextSkip > skipped) {
							skipped++;
							continue;
						}
						lvTargetPresets.getSelectionModel().select(i);
						lvTargetPresets.getFocusModel().focus(i);
						lvTargetPresets.scrollTo(i);

						found = true;
						break;
					}
				}
			}
		});
	}
}
