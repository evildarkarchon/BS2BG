package com.asdasfa.jbs2bg;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;
import com.asdasfa.jbs2bg.filtering.NpcTableColumns;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.VisibleScopeCommands;
import com.asdasfa.jbs2bg.fx.FilteredTableAdapter;
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
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 *
 * @author Totiman
 */
public class MainController extends CustomController {

    // Templates
    @FXML
    protected ListView<SliderPresetSnapshot> lvPresets;
    @FXML
    protected TableView<NpcMorphAssignmentSnapshot> tvNpc;
    @FXML
    protected ListView<SliderPresetSnapshot> lvTargetPresets;
    // Popup SliderPresets
    protected Stage popupSliderPresets;
    // Popup SliderPresetsFill
    protected Stage popupSliderPresetsFill;
    // Popup NpcDatabase
    protected Stage popupNpcDatabase;
    // ^ Templates ^
    protected PopupImageViewController popupImageViewController;
    /**
     * Public-JavaFX adapter over the NPC Morph Assignment table; every bulk
     * command freezes its scope through {@link FilteredTableAdapter#visibleSet()}.
     * Package-visible because the Fill Empty popup freezes the same scope.
     */
    FilteredTableAdapter<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> npcTable;
    @FXML
    private VBox mainPane;
    // Menu Items
    // Morphs
    @FXML
    private ListView<CustomMorphTargetSnapshot> lvCustomTargets;
    @FXML
    private TextField tfCustomTarget;
    @FXML
    private Label lblNpcCounter;
    @FXML
    private TableColumn<NpcMorphAssignmentSnapshot, String> tcName;
    // ^ Morphs ^
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
    private Label lblTargetName;
    @FXML
    private Label lblPresetCounter;
    // Confirm Dialogs
    private CustomConfirm confirmClearTargetPresets;
    private CustomConfirm confirmClearCustomTargets;
    private CustomConfirm confirmClearNpcs;
    private CustomConfirm confirmClearAssignments;
    // Notifications
    private CustomNotif notif;
    // Help Menu
    private Stage popupAbout;
    private PopupAboutController popupAboutController;
    private PopupSliderPresetsController popupSliderPresetsController;
    private PopupSliderPresetsFillController popupSliderPresetsFillController;
    private PopupNpcDatabaseController popupNpcDatabaseController;
    // Popup ImageView
    private Stage popupImageView;

    /**
     * Called BEFORE all FXML fields are injected.
     */
    public MainController() {
    }

    /**
     * Builds the stable identity used by every NPC edit and selection restoration.
     */
    private static NpcMorphAssignmentIdentity identityOf(NpcMorphAssignmentSnapshot npc) {
        return npc == null ? null : new NpcMorphAssignmentIdentity(npc.getPluginName(), npc.getEditorId());
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
     * @param editorId    NPC editor ID used by image-file conventions
     * @return the first matching local image, or null when none exists
     */
    static File findNpcImageFile(String displayName, String editorId) {
        String[] imageExtensions = {".jpg", "jpeg", ".png", ".bmp"};
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

    /**
     * @return true when a completed task carries a rejected or failed Project operation
     */
    private static boolean isRejectedOrFailed(ProjectOutcome outcome) {
        return outcome instanceof RejectedOutcome || outcome instanceof FailedOutcome;
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
        setupTooltips();
    }

    /*
     * Called in postInitialize after Main is set.
     */
    @Override
    protected void onPostInit() {
        setupNotifs();

        setupViews();
        setupAlerts();
        setupPopupAbout();
        setupPopupSliderPresets();
        setupPopupNpcDatabase();
        setupPopupImageView();

        connectViews();
        stage.setTitle(main.projectPresentation.getWindowTitle());

        stage.getScene().cursorProperty().bind(Bindings.when(mainPane.disabledProperty()).then(Cursor.WAIT).otherwise(Cursor.DEFAULT));
    }

    private void setupTooltips() {
        Tooltip tooltip = new Tooltip();
        tooltip.setText(
                """
                Examples:\s
                
                All|Female\s
                All|Female|NordRace\s
                All|Female|BretonRace\s
                All|Female|NordRaceVampire\s
                """
        );
        tfCustomTarget.setTooltip(tooltip);
    }

    private void setupNotifs() {
        notif = new CustomNotif(main);
        notif.setOwner(stage);
    }

    /**
     * Configures JavaFX views over immutable Project snapshot values.
     */
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
        selectSliderPreset(selectedPresetName);
        if (selectedCustomTargetName != null)
            selectCustomTarget(selectedCustomTargetName);
        else if (selectedNpcIdentity != null)
            selectNpc(selectedNpcIdentity);
        selectTargetPreset(selectedTargetPresetName);
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
        return renderProjectOutcome(main.workbenchProjectFlow.apply(edit));
    }

    /**
     * Restores a Slider Preset selection by stable case-insensitive name.
     */
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

    /**
     * Restores a Custom Morph Target selection by stable case-insensitive name.
     */
    private void selectCustomTarget(String name) {
        for (CustomMorphTargetSnapshot target : main.projectPresentation.getCustomMorphTargets()) {
            if (target.getName().equalsIgnoreCase(name)) {
                lvCustomTargets.getSelectionModel().select(target);
                return;
            }
        }
    }

    /**
     * Restores an NPC Morph Assignment selection by its logical identity, if it is visible.
     */
    void selectNpc(NpcMorphAssignmentIdentity identity) {
        if (identity == null)
            return;
        npcTable.select(identity);
    }

    /**
     * Restores an assigned Slider Preset selection after its target is restored.
     */
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

    /**
     * Resolves one Slider Preset in the latest rendered snapshot without regard to case.
     */
    private SliderPresetSnapshot findSliderPreset(String name) {
        for (SliderPresetSnapshot preset : main.projectPresentation.getSliderPresets()) {
            if (preset.getName().equalsIgnoreCase(name))
                return preset;
        }
        return null;
    }

    /**
     * Clears assignments from whichever logical target is currently selected.
     */
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
                """
                All NPCs in the table will be removed.
                If filter is active, only the ones displayed will be removed."""
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
                """
                All NPCs in the table will have all their assigned presets cleared.
                If filter is active, only the ones displayed will be cleared."""
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

    /**
     * Updates the selected target's relationship count from its immutable view list.
     */
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
        double x = (screenBounds.getWidth() / 2) - (popupAbout.getScene().getWidth() * 0.5);
        double y = (screenBounds.getHeight() / 2) - (popupAbout.getScene().getHeight() * 0.75);
        popupAbout.setX(x);
        popupAbout.setY(y);
        popupAbout.show();
    }

    @FXML
    private void showPopupSliderPresets() {
        if (lvCustomTargets.getSelectionModel().getSelectedItem() == null
                && tvNpc.getSelectionModel().getSelectedItem() == null)
            return;

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        Scene mainScene = stage.getScene();
        double x = stage.getX() + mainScene.getWidth() * 0.3 - popupSliderPresets.getScene().getWidth() / 2;
        double y = stage.getY() + mainScene.getHeight() / 2 - popupSliderPresets.getScene().getHeight() / 2;
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
        double x = stage.getX() + mainScene.getWidth() * 0.7 - popupSliderPresetsFill.getScene().getWidth() / 2;
        double y = stage.getY() + mainScene.getHeight() / 2 - popupSliderPresetsFill.getScene().getHeight() / 2;
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
        double x = stage.getX() + mainScene.getWidth() / 2 - popupNpcDatabase.getScene().getWidth() / 2;
        double y = stage.getY() + mainScene.getHeight() / 2 - popupNpcDatabase.getScene().getHeight() / 2;
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

    /**
     * Creates one Custom Morph Target with any random initial choice fixed before apply.
     */
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

    /**
     * Deletes the selected Custom Morph Target by logical name.
     */
    @FXML
    private void removeSelectedCustomTarget() {
        CustomMorphTargetSnapshot customMorphTarget = lvCustomTargets.getSelectionModel().getSelectedItem();
        if (customMorphTarget == null)
            return;

        applyProjectEdit(CustomMorphTargetEdits.delete(customMorphTarget.getName()));
    }

    /**
     * Removes the selected relationship from the active custom or NPC target.
     */
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

    /**
     * Assigns the complete current preset catalog to the active target in one edit.
     */
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

    /**
     * Deletes the selected NPC Morph Assignment by stable identity.
     */
    @FXML
    private void removeSelectedNpc() {
        NpcMorphAssignmentSnapshot npc = tvNpc.getSelectionModel().getSelectedItem();
        if (npc == null)
            return;

        applyProjectEdit(NpcMorphAssignmentEdits.removeNpc(identityOf(npc)));
    }

    /**
     * Applies the shared JavaFX scheduling, busy, and unexpected-failure lifecycle
     * around a synchronous domain operation.
     *
     * @param task      worker task that invokes the synchronous operation
     * @param success   presentation callback for the task's typed value
     * @param operation user-facing operation phrase for logging and errors
     * @param <T>       typed result returned by the operation
     */
    private <T> void scheduleBackgroundTask(Task<T> task, Consumer<T> success, String operation) {
        scheduleBackgroundTask(task, success, operation, operation + " failed unexpectedly.");
    }

    /**
     * Applies the shared JavaFX scheduling lifecycle while preserving an operation's
     * established user-facing failure message.
     *
     * @param task           worker task whose value is delivered on the JavaFX thread
     * @param success        presentation callback for the task's typed value
     * @param operation      user-facing operation phrase for start and cancellation logs
     * @param failureMessage exact failure notification and log text
     * @param <T>            typed result returned by the operation
     */
    private <T> void scheduleBackgroundTask(Task<T> task, Consumer<T> success, String operation,
                                            String failureMessage) {
        scheduleBackgroundTask(task, success, operation, ignored -> failureMessage);
    }

    /**
     * Applies the shared JavaFX scheduling lifecycle with an operation-owned failure formatter.
     *
     * @param task                    worker task whose value is delivered on the JavaFX thread
     * @param success                 presentation callback for the task's typed value
     * @param operation               user-facing operation phrase for start and cancellation logs
     * @param failureMessageFormatter maps the task exception to exact user-facing text
     * @param <T>                     typed result returned by the operation
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

    /**
     * Resets transient controls after a successful New Project or Open render.
     */
    private void reset() {
        stage.setTitle(main.projectPresentation.getWindowTitle());

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
                TableColumn<NpcMorphAssignmentSnapshot, ?> leadingColumn = tvNpc.getColumns().getFirst();

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
