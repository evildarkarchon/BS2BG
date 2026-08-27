package com.asdasfa.jbs2bg.data;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;

import javafx.beans.property.SimpleStringProperty;

/**
 *
 * @author Totiman
 */
public class NPC extends MorphTarget {

	private String mod = "Skyrim.esm";
	private String editorId = "";
	private String race = "";
	private String formId = "";
	
	private SimpleStringProperty sliderPresetsString = new SimpleStringProperty("");

	private File imageFile = null;

	public NPC(String line) {
		String[] vars = line.split("\\|");
		for (int i = 0; i < vars.length; i++) {
			vars[i] = vars[i].trim();
		}
		mod = vars[0];
		name = vars[1];
		editorId = vars[2];
		race = vars[3];
		String temp[] = race.split("\"");
		race = temp[0].trim();
		formId = vars[4];
		
		trimFormId();

		if (name.isEmpty())
			name = "Unnamed " + "(" + editorId + ")";

		vars = null;
		temp = null;

		findImageFile();
	}

	/**
	 * Creates a presentation projection from immutable NPC Morph Assignment values.
	 * Slider Preset relationships are attached after all projected presets exist.
	 *
	 * @param snapshot immutable NPC Morph Assignment values to project
	 * @throws NullPointerException when snapshot is null
	 */
	public NPC(NpcMorphAssignmentSnapshot snapshot) {
		if (snapshot == null)
			throw new NullPointerException("snapshot");
		this.name = snapshot.getDisplayName();
		this.mod = snapshot.getPluginName();
		this.editorId = snapshot.getEditorId();
		this.race = snapshot.getRace();
		this.formId = snapshot.getFormId();
		trimFormId();
		findImageFile();
	}

	@Override
	public void clearSliderPresets() {
		super.clearSliderPresets();
		setSliderPresetsString();
	}
	
	@Override
	public void sortPresets() {
		super.sortPresets();
		setSliderPresetsString();
	}
	
	public void setSliderPresetsString() {
		if (sliderPresets.size() > 0) {
			String s = "";
			
			String[] values = new String[sliderPresets.size()];
			for (int i = 0; i < sliderPresets.size(); i++) {
				values[i] = sliderPresets.get(i).getName();
			}
			List<String> valuesList = Arrays.asList(values);
			s += String.join("|", valuesList);
			s = s.trim();
			
			sliderPresetsString.set(s);
		} else {
			sliderPresetsString.set("");
		}
	}
	
	public SimpleStringProperty getSliderPresetsString() {
		return sliderPresetsString;
	}
	
	private void trimFormId() {
		formId = formId.trim();
		
		int length = formId.length();
		if (length > 6) { // Trim mod index
			// Remove first n numbers from 8-digit hexadecimal until 6 digits are left
			int trimBeginIndex = length - 6; // xx123456
			if (trimBeginIndex > 0 && trimBeginIndex < length)
				formId = formId.substring(trimBeginIndex);
		}
		
		formId = formId.replaceFirst("^0+(?!$)", ""); // Remove leading zeroes
	}

	private String[] imageExt = { ".jpg", "jpeg", ".png", ".bmp" };

	private void findImageFile() {
		File file = null;
		for (int i = 0; i < imageExt.length; i++) {
			String fileWithEdid = "images/" + name + " (" + editorId + ")" + imageExt[i];
			file = new File(fileWithEdid);
			if (file.exists()) {
				imageFile = file;
				return;
			}
		}

		for (int i = 0; i < imageExt.length; i++) {
			String fileNoEdid = "images/" + name + imageExt[i];
			file = new File(fileNoEdid);
			if (file.exists()) {
				imageFile = file;
				return;
			}
		}
	}

	public String getMod() {
		return mod;
	}

	public String getEditorId() {
		return editorId;
	}

	public String getRace() {
		return race;
	}

	public String getFormId() {
		return formId;
	}

	public File getImageFile() {
		return imageFile;
	}

	@Override
	public String toLine() {
		String line;
		line = mod + "|" + formId + "=";

		String[] values = new String[sliderPresets.size()];
		for (int i = 0; i < sliderPresets.size(); i++) {
			values[i] = sliderPresets.get(i).getName();
		}
		List<String> valuesList = Arrays.asList(values);
		line += String.join("|", valuesList);
		line = line.trim();

		values = null;

		return line;
	}
}
