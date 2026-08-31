package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.etc.KeyNavigationListener;
import com.asdasfa.jbs2bg.etc.MyUtils;
import com.asdasfa.jbs2bg.filtering.VisibleScopeCommands;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

import javafx.collections.ObservableList;
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
    protected ListView<SliderPresetSnapshot> lvPresets;

    private CustomNotif notif;

    private CustomConfirm confirmFillEmpty;

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
                        //lvPresets.getSelectionModel().select(i);
                        lvPresets.getSelectionModel().clearAndSelect(i);
                        lvPresets.getFocusModel().focus(i);
                        // scrollTo is the minimal scroll in JavaFX 25: a no-op when the row is already visible.
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
            lvPresets.getSelectionModel().clearSelection();
        });

        notif = new CustomNotif(main);
        notif.setOwner(stage);

        confirmFillEmpty = new CustomConfirm(main) {
            /**
             * Freezes the visible NPC scope and the preset selection into explicit random
             * choices, then submits one atomic fill edit.
             */
            @Override
            public void ok() {
                ObservableList<SliderPresetSnapshot> selectedPresets = lvPresets.getSelectionModel().getSelectedItems();
                if (selectedPresets.size() <= 0) {
                    return;
                }

                List<String> selectedNames = new ArrayList<>();
                for (SliderPresetSnapshot preset : selectedPresets)
                    selectedNames.add(preset.getName());
                // Random choices are completed here, on the JavaFX thread, so the edit is plain immutable data;
                // MyUtils.random uses inclusive bounds while the command asks for an index in [0, bound).
                main.mainController.applyProjectEdit(VisibleScopeCommands.fillEmpty(
                        main.mainController.npcTable.visibleSet(), selectedNames, bound -> MyUtils.random(0, bound - 1)));

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
        lvPresets.setItems(main.projectPresentation.getSliderPresets());
    }

    @FXML
    private void fillEmpty() {
        ObservableList<SliderPresetSnapshot> selectedPresets = lvPresets.getSelectionModel().getSelectedItems();
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
