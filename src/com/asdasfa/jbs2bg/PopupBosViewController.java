package com.asdasfa.jbs2bg;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.asdasfa.jbs2bg.presentation.ProjectGeneratedOutput;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

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
	private String currentArtifactFileName;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	@Override
	protected void onPostInit() {
		stage.setOnShown(e -> {
			onShown();
		});
		stage.setOnHidden(e -> clearGeneratedOutput());
		
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
	
	/** Renders the selected BoS artifact from one coherent presentation snapshot. */
	private void onShown() {
		SliderPresetSnapshot selectedPreset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
		if (selectedPreset == null) {
			clearGeneratedOutput();
			return;
		}

		// Capture once so the preview name and content cannot come from different renders.
		ProjectSnapshot snapshot = main.projectPresentation.getSnapshot();
		SliderPresetSnapshot preset = findPreset(snapshot, selectedPreset.getName());
		if (preset == null) {
			clearGeneratedOutput();
			return;
		}
		boolean omitRedundantSliders = main.data.prefs.getBoolean(main.data.OMIT_REDUNDANT_SLIDERS, false);
		ProjectGeneratedOutput output = ProjectOutputFormatter.generate(snapshot, omitRedundantSliders);
		currentArtifactFileName = preset.getName() + ".json";
		String bosJson = output.getBosJsonByFileName().get(currentArtifactFileName);
		if (bosJson == null) {
			clearGeneratedOutput();
			return;
		}
		
		stage.setTitle("BodyTypes of Skyrim JSON: " + preset.getName());
		btnBack.requestFocus();
		
		taBosJson.setText(bosJson);
	}

	/** Resolves the selected logical preset within the captured Project snapshot. */
	private static SliderPresetSnapshot findPreset(ProjectSnapshot snapshot, String selectedName) {
		for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
			if (preset.getName().equalsIgnoreCase(selectedName))
				return preset;
		}
		return null;
	}

	/** Clears cached BoS output after a changed ProjectSession outcome. */
	public void invalidateGeneratedOutput() {
		clearGeneratedOutput();
	}

	/** Clears both parts of the currently rendered BoS artifact. */
	private void clearGeneratedOutput() {
		currentArtifactFileName = null;
		taBosJson.clear();
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
	
	/**
	 * Captures the displayed artifact on the JavaFX thread, prompts for its
	 * destination, and schedules a background write without retaining control state.
	 */
	@FXML
	private void exportBosJson() {
		File file;
		// JavaFX controls are confined to this thread; the worker receives plain values.
		String artifactFileName = currentArtifactFileName;
		String artifactContent = taBosJson.getText();
		String artifactEncoding = main.data.encoding;
		if (artifactFileName == null)
			return;
		
		try {
			fcFile.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_JSON_FOLDER, new File(".").getAbsolutePath())));
			fcFile.setInitialFileName(withoutJsonExtension(artifactFileName));
			file = fcFile.showSaveDialog(stage);
		} catch (Exception e) {
			fcFile.setInitialDirectory(main.data.homeDir);
			fcFile.setInitialFileName(withoutJsonExtension(artifactFileName));
			file = fcFile.showSaveDialog(stage);
		}
		
		if (file == null) // Cancelled
			return;
		
		main.data.prefs.put(main.data.LAST_USED_JSON_FOLDER, file.getParent());

		if (!file.getAbsolutePath().endsWith(".json"))
			file = new File(file.getAbsolutePath() + ".json");

		mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON file...");
		Task<Void> task = exportBosJsonTask(file, artifactContent, artifactEncoding);
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
	}

	/** Removes the extension used by the save-dialog filter from an artifact name. */
	private static String withoutJsonExtension(String artifactFileName) {
		return artifactFileName.endsWith(".json")
				? artifactFileName.substring(0, artifactFileName.length() - ".json".length())
				: artifactFileName;
	}

	/**
	 * Creates a worker that writes only the immutable destination and content captured
	 * on the JavaFX thread.
	 *
	 * @param file resolved export destination
	 * @param content captured BoS JSON content
	 * @param encoding captured output character encoding
	 * @return background export task
	 */
	private static Task<Void> exportBosJsonTask(File file, String content, String encoding) {
		return new Task<Void>() {
			/**
			 * Writes the already captured artifact without consulting JavaFX state.
			 *
			 * @return always {@code null} after a successful or empty export
			 * @throws IOException when the artifact cannot be written
			 */
			@Override
			public Void call() throws IOException {
				if (!content.isEmpty()) {
					if (file.exists())
						FileUtils.deleteQuietly(file);
					FileUtils.writeStringToFile(file, content, encoding);
				}
				return null;
			}
		};
	}
}
