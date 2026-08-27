package com.asdasfa.jbs2bg.data;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.prefs.Preferences;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.mozilla.universalchardet.UniversalDetector;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 
 * @author Totiman
 */
public class Data {
	
	public final ObservableList<SliderPreset> sliderPresets = FXCollections.observableArrayList();
	public final ObservableList<NPC> npcDatabase = FXCollections.observableArrayList();
	
	public final ObservableList<CustomMorphTarget> customMorphTargets = FXCollections.observableArrayList();
	public final ObservableList<NPC> morphedNpcs = FXCollections.observableArrayList();
    
	public File homeDir;
	
	public Preferences prefs;
	public final String LAST_USED_FOLDER = "Last used folder";
	public final String LAST_USED_PRESET_FOLDER = "Last used preset folder";
	public final String LAST_USED_NPC_FOLDER = "Last used npc folder";
	public final String LAST_USED_INI_FOLDER = "Last used ini folder";
	public final String LAST_USED_JSON_FOLDER = "Last used json folder";
	public final String OMIT_REDUNDANT_SLIDERS = "Omit redundant sliders";
	
	public final String encoding = "UTF-8";
    
	public Data() {
		homeDir = new File(System.getProperty("user.home"));
		
		File jarDir = new File(ClassLoader.getSystemClassLoader().getResource(".").getPath());
		String prefPath = getClass().getName();
		if (jarDir != null) {
			prefPath = jarDir.getAbsolutePath();
		}
		
		try {
			prefs = Preferences.userRoot().node(prefPath);
		} catch (Exception e) { // If the preference path fails, fall back to getClass().getName()
			e.printStackTrace();
			prefs = Preferences.userRoot().node(getClass().getName());
		}
		
	}
	
	public boolean sliderPresetExists(SliderPreset sliderPreset) {
		for (int i = 0; i < sliderPresets.size(); i++) {
			if (sliderPresets.get(i).getName().equalsIgnoreCase(sliderPreset.getName()))
				return true;
		}
		return false;
	}
	
	public void sortPresets() {
		if (sliderPresets.size() > 0)
			FXCollections.sort(sliderPresets, comparatorSliderPreset);
	}
	private Comparator<? super SliderPreset> comparatorSliderPreset = new Comparator<SliderPreset>() {
        @Override
        public int compare(SliderPreset sp1, SliderPreset sp2) {
            return sp1.getName().compareToIgnoreCase(sp2.getName());
        }
    };
    
    public void sortCustomMorphTargets() {
		if (customMorphTargets.size() > 0)
			FXCollections.sort(customMorphTargets, comparatorCustomMorphTarget);
	}
	private Comparator<? super CustomMorphTarget> comparatorCustomMorphTarget = new Comparator<CustomMorphTarget>() {
        @Override
        public int compare(CustomMorphTarget cmt1, CustomMorphTarget cmt2) {
            return cmt1.getName().compareToIgnoreCase(cmt2.getName());
        }
    };
	
	public void parseNpcFile(File file) {
		try {
			String inputEncoding = UniversalDetector.detectCharset(file);
			
			LineIterator iterator = FileUtils.lineIterator(file, inputEncoding);
			try {
				while (iterator.hasNext()) {
					String line = iterator.nextLine();
					line = line.trim();
					if (!line.isEmpty()) {
						NPC npc = new NPC(line);
						if (!npcExistsInDatabase(npc)) {
							npcDatabase.add(npc);
						}
					}
				}
			} finally {
				iterator.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean npcExistsInDatabase(NPC npc) {
		for (int i = 0; i < npcDatabase.size(); i++) {
			NPC n = npcDatabase.get(i);
			// Same mod AND same editor id
			if (n.getMod().equalsIgnoreCase(npc.getMod()) && n.getEditorId().equalsIgnoreCase(npc.getEditorId()))
				return true;
		}
		return false;
	}
	
}
