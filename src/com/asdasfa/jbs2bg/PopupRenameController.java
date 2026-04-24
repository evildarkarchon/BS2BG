package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.data.MorphTarget;
import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.etc.MyUtils;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class PopupRenameController extends CustomController {
	
	@FXML
	private TextField tfRename;
	@FXML
	private Label lblRenameWarning;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	@Override
	protected void onPostInit() {
		stage.setOnShown(e -> {
			String name = main.mainController.lvPresets.getSelectionModel().getSelectedItem().getName();
			stage.setTitle("Rename: " + name);
			lblRenameWarning.setText("");
			
			tfRename.setText(name);
			
			tfRename.requestFocus();
		});
	}
	
	@FXML
	private void rename() {
		String newName = tfRename.getText();
		if (newName.trim().isEmpty() || newName.trim().contains(".")) {
			lblRenameWarning.setText("Invalid name!");
		} else {
			SliderPreset selectedPreset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
			if (newName.equals(selectedPreset.getName())) { // Same name, nope
				lblRenameWarning.setText("Name needs to be different than the old one!");
			} else {
				boolean exists = false;
	            for (int i = 0; i < main.data.sliderPresets.size(); i++) {
	                if (main.data.sliderPresets.get(i).getName().equals(newName)) {
	                    exists = true;
	                    break;
	                }
	            }
	            if (exists) { // Exists already, nope
	            	lblRenameWarning.setText("There's already a preset of the same name!");
	            } else { // Success
	            	SliderPreset preset = selectedPreset;
	            	preset.setName(newName); // Set it
	            	main.data.sortPresets(); // Sort
	            	//main.mainController.lvPresets.refresh();
	            	
	            	// Also sort presets in targets
	            	for (int i = 0; i < main.data.customMorphTargets.size(); i++) {
	            		main.data.customMorphTargets.get(i).sortPresets();
	            	}
	            	for (int i = 0; i < main.data.morphedNpcs.size(); i++) {
	            		main.data.morphedNpcs.get(i).sortPresets();
	            	}
	            	
	            	// Also sort target presets to refresh its listView
	            	MorphTarget target = main.mainController.getCurrentTarget();
	            	if (target != null) {
	            		target.sortPresets();
	            		
	            		int index = main.mainController.lvTargetPresets.getItems().indexOf(preset);
		            	boolean indexVisible = MyUtils.isIndexVisible(main.mainController.lvTargetPresets, index);
		            	if (!indexVisible)
		    				main.mainController.lvTargetPresets.scrollTo(index);
	            	}
	            	
	            	int index = main.mainController.lvPresets.getItems().indexOf(preset);
	            	boolean indexVisible = MyUtils.isIndexVisible(main.mainController.lvPresets, index);
	    			
	    			main.mainController.lvPresets.getSelectionModel().select(index);
	    			main.mainController.lvPresets.getFocusModel().focus(index);
	    			if (!indexVisible)
	    				main.mainController.lvPresets.scrollTo(index);
	    			
	    			main.mainController.markChanged();
	            	
	            	stage.hide();
	            }
			}
		}
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
}
