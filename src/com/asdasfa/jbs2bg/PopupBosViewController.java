package com.asdasfa.jbs2bg;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.asdasfa.jbs2bg.data.SliderPreset;

import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * 
 * @author Totiman
 */
public class PopupBosViewController extends CustomController {
	
	@FXML
	private VBox mainPane;
	
	@FXML
	private TextArea taBosJson;
	
	@FXML
	private Button btnBack;
	
	private CustomNotif notif;
	
	private FileChooser fcFile;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	@Override
	protected void onPostInit() {
		stage.setOnShown(e -> {
			onShown();
		});
		stage.setOnHidden(e -> {
			taBosJson.clear();
		});
		
		notif = new CustomNotif(main);
		notif.setOwner(stage);
		
		fcFile = new FileChooser();
		fcFile.setTitle("Export BoS JSON File");
		fcFile.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
	
		// Don't allow closing if mainPane is disabled, meaning doing some tasks
		stage.setOnCloseRequest(e -> {
			if (mainPane.isDisabled())
				e.consume();
		});
		stage.getScene().cursorProperty().bind(Bindings.when(mainPane.disabledProperty()).then(Cursor.WAIT).otherwise(Cursor.DEFAULT));
	}
	
	private void onShown() {
		SliderPreset preset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		stage.setTitle("BodyTypes of Skyrim JSON: " + preset.getName());
		btnBack.requestFocus();
		
		taBosJson.setText(preset.toBosJson());
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
	
	@FXML
	private void copyBosJson() {
		String text = taBosJson.getText();
		
		if (!text.isEmpty()) {
			final ClipboardContent content = new ClipboardContent();
			content.putString(text);
			Clipboard.getSystemClipboard().setContent(content);
			
			notif.show("JSON copied to clipboard!");
		} else {
			notif.show("Text is empty!");
		}
	}
	
	@FXML
	private void exportBosJson() {
		File file;
		SliderPreset preset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		try {
			fcFile.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_JSON_FOLDER, new File(".").getAbsolutePath())));
			fcFile.setInitialFileName(preset.getName());
			file = fcFile.showSaveDialog(stage);
		} catch (Exception e) {
			fcFile.setInitialDirectory(main.data.homeDir);
			fcFile.setInitialFileName(preset.getName());
			file = fcFile.showSaveDialog(stage);
		}
		
		if (file == null) // Cancelled
			return;
		
		main.data.prefs.put(main.data.LAST_USED_JSON_FOLDER, file.getParent());

		if (!file.getAbsolutePath().endsWith(".json"))
			file = new File(file.getAbsolutePath() + ".json");

		mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON file...");
		try {
			if (!file.getAbsolutePath().endsWith(".json"))
				file = new File(file.getAbsolutePath() + ".json");
			
			Task<Void> task = exportBosJsonTask(file);
			task.setOnSucceeded(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON file done.");
				mainPane.setDisable(false);
			});
			task.setOnFailed(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON file failed.");
				mainPane.setDisable(false);
				
				notif.showError("Exporting JSON file failed.");
			});
			task.setOnCancelled(e -> {
				Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON file cancelled.");
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
	
	private Task<Void> exportBosJsonTask(File file) throws InterruptedException {
		return new Task<Void>() {
			@Override
			public Void call() throws InterruptedException {
				try {
					String text = taBosJson.getText();
					
					if (!text.isEmpty()) {
						if (file.exists())
							FileUtils.deleteQuietly(file);
						
						FileUtils.writeStringToFile(file, text, main.data.encoding);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
				}
				
				return null;
			}
		};
	}
}