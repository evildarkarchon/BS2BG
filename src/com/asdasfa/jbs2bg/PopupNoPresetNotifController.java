package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.filtering.NpcTableColumns;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.fx.FilteredTableAdapter;
import com.asdasfa.jbs2bg.presentation.ProjectGeneratedOutput;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;

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

    private final ObservableList<CustomMorphTargetSnapshot> customMorphTargets = FXCollections.observableArrayList();
    private final ObservableList<NpcMorphAssignmentSnapshot> morphedNpcs = FXCollections.observableArrayList();
    @FXML
    private ListView<CustomMorphTargetSnapshot> lvNoPreset;
    @FXML
    private TableView<NpcMorphAssignmentSnapshot> tvNoPreset;
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
    private FilteredTableAdapter<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> noPresetTable;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        noPresetTable = FilteredTableAdapter.attach(tvNoPreset, NpcTableColumns.npcMorphAssignments(),
                ProjectIdentities::npcMorphAssignment);
    }

    @Override
    protected void onPostInit() {
        lvNoPreset.setOnKeyTyped(new KeyNavigationListener() {
            @Override
            public void test() {
                for (int i = 0; i < lvNoPreset.getItems().size(); i++) {
                    CustomMorphTargetSnapshot item = lvNoPreset.getItems().get(i);
                    if (item.getName().toUpperCase().startsWith(searchText.toUpperCase())) {
                        if (searchTextSkip > skipped) {
                            skipped++;
                            continue;
                        }
                        lvNoPreset.getSelectionModel().select(i);
                        lvNoPreset.getFocusModel().focus(i);
                        // scrollTo is the minimal scroll in JavaFX 25: a no-op when the row is already visible.
                        lvNoPreset.scrollTo(i);

                        found = true;
                        break;
                    }
                }
            }
        });

        lvNoPreset.setCellFactory(p ->
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

        lvNoPreset.setItems(customMorphTargets);

        tvNoPreset.setOnKeyTyped(new KeyNavigationListener() {
            @Override
            public void test() {
                TableColumn<NpcMorphAssignmentSnapshot, ?> leadingColumn = tvNoPreset.getColumns().get(0);
                for (NpcMorphAssignmentSnapshot npc : tvNoPreset.getItems()) {
                    String text = noPresetTable.cellTextOf(leadingColumn, npc);
                    if (text.toUpperCase().startsWith(searchText.toUpperCase())) {
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
        // Sorting re-renders through the adapter, which reveals the selected row afterwards.
        tcName.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("displayName"));
        tcMaster.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("pluginName"));
        tcRace.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("race"));
        tcEditorId.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("editorId"));
        tcFormId.setCellValueFactory(new PropertyValueFactory<NpcMorphAssignmentSnapshot, String>("formId"));

        noPresetTable.setSource(morphedNpcs);

        stage.setOnHidden(e -> {
            customMorphTargets.clear();
            morphedNpcs.clear();
        });

        lvNoPreset.managedProperty().bind(lvNoPreset.visibleProperty());
        tvNoPreset.managedProperty().bind(tvNoPreset.visibleProperty());
    }

    /**
     * Displays targets that the same generated-output result found to have no
     * Slider Preset assignments. Must run on the JavaFX thread; when first shown,
     * the method remains in a nested event loop until the user closes the warning.
     *
     * @param output immutable result whose text and warning rows share one snapshot
     * @throws NullPointerException when output is null
     */
    public void notify(ProjectGeneratedOutput output) {
        customMorphTargets.clear();
        morphedNpcs.clear();

        stage.setTitle("");

        customMorphTargets.addAll(output.getCustomMorphTargetsWithoutPresets());
        morphedNpcs.addAll(output.getNpcMorphAssignmentsWithoutPresets());
        if (customMorphTargets.isEmpty() && morphedNpcs.isEmpty())
            return;

        stage.setTitle("Warning: Targets with no presets were found!");

        lvNoPreset.setVisible(true);
        tvNoPreset.setVisible(true);

        if (customMorphTargets.size() <= 0)
            lvNoPreset.setVisible(false);

        if (morphedNpcs.size() <= 0)
            tvNoPreset.setVisible(false);

        if (!stage.isShowing())
            stage.showAndWait();
    }

    /**
     * Clears and closes a warning whose target projections belong to an obsolete snapshot.
     */
    public void invalidateGeneratedOutput() {
        customMorphTargets.clear();
        morphedNpcs.clear();
        stage.setTitle("");
        if (stage.isShowing())
            stage.hide();
    }

    @FXML
    private void hide() {
        stage.hide();
    }
}
