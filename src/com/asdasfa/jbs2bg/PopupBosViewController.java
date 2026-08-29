package com.asdasfa.jbs2bg;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.asdasfa.jbs2bg.presentation.BosArtifactPublisher;
import com.asdasfa.jbs2bg.presentation.BosJsonArtifact;
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
	private BosJsonArtifact currentArtifact;
	
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
		ProjectGeneratedOutput output;
		try {
			output = ProjectOutputFormatter.generate(snapshot, omitRedundantSliders);
		} catch (IllegalArgumentException exception) {
			clearGeneratedOutput();
			notif.showError(exception.getMessage());
			return;
		}
		BosJsonArtifact artifact = findArtifact(output, preset.getName());
		if (artifact == null) {
			clearGeneratedOutput();
			return;
		}
		currentArtifact = artifact;
		
		stage.setTitle("BodyTypes of Skyrim JSON: " + preset.getName());
		btnBack.requestFocus();
		
		taBosJson.setText(artifact.getText());
	}

	/** Resolves the selected logical preset within the captured Project snapshot. */
	private static SliderPresetSnapshot findPreset(ProjectSnapshot snapshot, String selectedName) {
		for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
			if (preset.getName().equalsIgnoreCase(selectedName))
				return preset;
		}
		return null;
	}

	/** Resolves one owned artifact by its source Slider Preset identity. */
	private static BosJsonArtifact findArtifact(ProjectGeneratedOutput output, String presetName) {
		for (BosJsonArtifact artifact : output.getBosJsonArtifacts()) {
			if (artifact.getSliderPresetName().equals(presetName))
				return artifact;
		}
		return null;
	}

	/** Clears cached BoS output after a changed ProjectSession outcome. */
	public void invalidateGeneratedOutput() {
		clearGeneratedOutput();
	}

	/** Releases the current owned artifact and clears its decoded preview. */
	private void clearGeneratedOutput() {
		currentArtifact = null;
		taBosJson.clear();
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
	
	/** Copies text decoded from the owned artifact rather than mutable preview control state. */
	@FXML
	private void copyBosJson() {
		BosJsonArtifact artifact = currentArtifact;
		
		if (artifact != null) {
			final ClipboardContent content = new ClipboardContent();
			content.putString(artifact.getText());
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
		// JavaFX controls are confined to this thread; the worker receives the owned artifact.
		BosJsonArtifact artifact = currentArtifact;
		if (artifact == null)
			return;
		
		try {
			fcFile.setInitialDirectory(new File(main.data.prefs.get(main.data.LAST_USED_JSON_FOLDER, new File(".").getAbsolutePath())));
			fcFile.setInitialFileName(withoutJsonExtension(artifact.getFileName()));
			file = fcFile.showSaveDialog(stage);
		} catch (Exception e) {
			fcFile.setInitialDirectory(main.data.homeDir);
			fcFile.setInitialFileName(withoutJsonExtension(artifact.getFileName()));
			file = fcFile.showSaveDialog(stage);
		}
		
		if (file == null) // Cancelled
			return;
		
		main.data.prefs.put(main.data.LAST_USED_JSON_FOLDER, file.getParent());

		if (!file.getAbsolutePath().toLowerCase(Locale.ROOT).endsWith(".json"))
			file = new File(file.getAbsolutePath() + ".json");

		mainPane.setDisable(true);
		Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON file...");
		Task<Void> task = exportBosJsonTask(file, artifact);
		task.setOnSucceeded(e -> {
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON file done.");
			mainPane.setDisable(false);
		});
		task.setOnFailed(e -> {
			Logger.getLogger(getClass().getName()).log(Level.INFO, "Exporting BoS JSON file failed.");
			mainPane.setDisable(false);
			
			notif.showError("Exporting JSON file failed: " + task.getException().getMessage());
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
		return artifactFileName.toLowerCase(Locale.ROOT).endsWith(".json")
				? artifactFileName.substring(0, artifactFileName.length() - ".json".length())
				: artifactFileName;
	}

	/**
	 * Creates a worker that publishes only the immutable destination and artifact captured
	 * on the JavaFX thread.
	 *
	 * @param file resolved export destination
	 * @param artifact captured canonical BoS artifact
	 * @return background export task
	 */
	private static Task<Void> exportBosJsonTask(File file, BosJsonArtifact artifact) {
		return new Task<Void>() {
			/**
			 * Publishes the already captured artifact without consulting JavaFX state.
			 *
			 * @return always {@code null} after a successful export
			 * @throws IOException when the artifact cannot be written
			 */
			@Override
			public Void call() throws IOException {
				BosArtifactPublisher.publish(file.toPath(), artifact);
				return null;
			}
		};
	}
}
