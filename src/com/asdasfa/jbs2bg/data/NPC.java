package com.asdasfa.jbs2bg.data;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;

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

	public NPC(Member member, ObservableList<SliderPreset> sliderPresets) {
		name = member.getName();

		JsonObject jo = member.getValue().asObject();
		mod = jo.getString("Mod", "");
		editorId = jo.getString("EditorId", "");
		race = jo.getString("Race", "");
		formId = jo.getString("FormId", "");
		
		trimFormId();

		JsonArray ja = jo.get("SliderPresets").asArray();
		for (int i = 0; i < ja.size(); i++) {
			String presetName = ja.get(i).asString();

			SliderPreset sliderPreset = null;
			for (int j = 0; j < sliderPresets.size(); j++) { // Search the list for existing sliderPreset
				SliderPreset sp = sliderPresets.get(j);
				if (sp.getName().equals(presetName)) {
					sliderPreset = sp;
					break;
				}
			}
			if (sliderPreset != null)
				addSliderPreset(sliderPreset);
		}

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