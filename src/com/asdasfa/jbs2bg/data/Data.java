package com.asdasfa.jbs2bg.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.mozilla.universalchardet.UniversalDetector;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.asdasfa.jbs2bg.data.SliderPreset.SetSlider;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.WriterConfig;

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
	public File currentFile = null;
	
	public Preferences prefs;
	public final String LAST_USED_FOLDER = "Last used folder";
	public final String LAST_USED_PRESET_FOLDER = "Last used preset folder";
	public final String LAST_USED_NPC_FOLDER = "Last used npc folder";
	public final String LAST_USED_INI_FOLDER = "Last used ini folder";
	public final String LAST_USED_JSON_FOLDER = "Last used json folder";
	public final String OMIT_REDUNDANT_SLIDERS = "Omit redundant sliders";
	
	public final String encoding = "UTF-8";
	
	private DocumentBuilderFactory dbFactory;
    private DocumentBuilder dBuilder;
    
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
		
		dbFactory = DocumentBuilderFactory.newInstance();
		try {
			dBuilder = dbFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
	}
    
	public void parseXmlPreset(File file) throws SAXException, IOException {
		Document doc = dBuilder.parse(file);
		doc.getDocumentElement().normalize();
		if (!doc.getDocumentElement().getNodeName().equalsIgnoreCase("SliderPresets"))
			return;

		NodeList nodes = doc.getElementsByTagName("Preset");
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) node;

				String name = e.getAttribute("name");
				if (name == null)
					continue;
				
				if (name.isEmpty())
					continue;

				SliderPreset sliderPreset = new SliderPreset(e);
				if (!sliderPresetExists(sliderPreset)) {
					sliderPresets.add(sliderPreset);
				} else { // Already exists, just update
					SliderPreset existingSliderPreset = getSliderPreset(sliderPreset.getName());
					if (existingSliderPreset != null) {
						existingSliderPreset.clearAndCopySliders(sliderPreset);
					}
				}
			}
		}
	}
	
	public boolean sliderPresetExists(SliderPreset sliderPreset) {
		for (int i = 0; i < sliderPresets.size(); i++) {
			if (sliderPresets.get(i).getName().equalsIgnoreCase(sliderPreset.getName()))
				return true;
		}
		return false;
	}
	
	public SliderPreset getSliderPreset(String sliderPresetName) {
		for (int i = 0; i < sliderPresets.size(); i++) {
			SliderPreset sliderPreset = sliderPresets.get(i);
			if (sliderPreset.getName().equalsIgnoreCase(sliderPresetName))
				return sliderPreset;
		}
		return null;
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
	
	public void openFromFile(File file) {
		try {
			String inputEncoding = UniversalDetector.detectCharset(file);
			
			String s = FileUtils.readFileToString(file, inputEncoding);
			
			JsonObject root = Json.parse(s).asObject();
			
			JsonObject joSliderPresets = root.get("SliderPresets").asObject();
			JsonObject joCustomMorphTargets = root.get("CustomMorphTargets").asObject();
			JsonObject joMorphedNpcs = root.get("MorphedNPCs").asObject();
			
			for (Member member : joSliderPresets) {
				SliderPreset sliderPreset = new SliderPreset(member);
				
				sliderPresets.add(sliderPreset);
			}
			sortPresets();
			
			for (Member member : joCustomMorphTargets) {
				CustomMorphTarget cmt = new CustomMorphTarget(member, sliderPresets);
				customMorphTargets.add(cmt);
			}
			sortCustomMorphTargets();
			
			for (Member member : joMorphedNpcs) {
				NPC npc = new NPC(member, sliderPresets);
				morphedNpcs.add(npc);
			}
		} catch (FileNotFoundException e) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, e);
		} catch (IOException e) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, e);
		} finally {
			currentFile = file;
		}
	}
	
	public void saveToFile(File file) {
		JsonObject root = new JsonObject();

		JsonObject joSliderPresets = new JsonObject();
		JsonObject joCustomMorphTargets = new JsonObject();
		JsonObject joMorphedNpcs = new JsonObject();

		for (int i = 0; i < sliderPresets.size(); i++) {
			SliderPreset sliderPreset = sliderPresets.get(i);
			
			JsonObject joSliderPreset = new JsonObject();
			
			joSliderPreset.add("isUUNP", sliderPreset.isUUNP());

			JsonArray jaSliders = new JsonArray();
			
			ArrayList<SetSlider> allSliders = sliderPreset.getAllSetSliders();
			for (int j = 0; j < allSliders.size(); j++) {
				SetSlider slider = allSliders.get(j);

				if (sliderPreset.getMissingDefaultSetSlider(slider.getName()) == null) { // A regular slider
					jaSliders.add(slider.toJsonObject());
				} else { // A missing default slider
					if (slider.getPctMin() == 100 && slider.getPctMax() == 100 && slider.isEnabled()) {
						// Don't save sliders with both weights at 100 for missing defaults and is enabled (All defaults)
					} else {
						jaSliders.add(slider.toJsonObject());
					}
				}
			}
			joSliderPreset.add("SetSliders", jaSliders);

			joSliderPresets.add(sliderPreset.getName(), joSliderPreset);
		}

		for (int i = 0; i < customMorphTargets.size(); i++) {
			JsonObject joCustomMorphTarget = new JsonObject();

			CustomMorphTarget cmt = customMorphTargets.get(i);

			JsonArray jaSPresets = new JsonArray();
			ObservableList<SliderPreset> sliderPresets = cmt.getSliderPresets();
			for (int j = 0; j < sliderPresets.size(); j++) {
				SliderPreset sliderPreset = sliderPresets.get(j);

				jaSPresets.add(sliderPreset.getName());
			}
			joCustomMorphTarget.add("SliderPresets", jaSPresets);

			joCustomMorphTargets.add(cmt.getName(), joCustomMorphTarget);
		}

		for (int i = 0; i < morphedNpcs.size(); i++) {
			JsonObject joNpc = new JsonObject();

			NPC npc = morphedNpcs.get(i);

			joNpc.add("Mod", npc.getMod());
			joNpc.add("EditorId", npc.getEditorId());
			joNpc.add("Race", npc.getRace());
			joNpc.add("FormId", npc.getFormId());

			JsonArray jaSPresets = new JsonArray();
			ObservableList<SliderPreset> sliderPresets = npc.getSliderPresets();
			for (int j = 0; j < sliderPresets.size(); j++) {
				SliderPreset sliderPreset = sliderPresets.get(j);

				jaSPresets.add(sliderPreset.getName());
			}
			joNpc.add("SliderPresets", jaSPresets);

			joMorphedNpcs.add(npc.getName(), joNpc);
		}

		root.add("SliderPresets", joSliderPresets);
		root.add("CustomMorphTargets", joCustomMorphTargets);
		root.add("MorphedNPCs", joMorphedNpcs);

		String saveString = root.toString(WriterConfig.PRETTY_PRINT);
		
		try {
			if (file.exists())
				FileUtils.deleteQuietly(file);
			
			FileUtils.writeStringToFile(file, saveString, encoding);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			currentFile = file;
		}
	}
	
	public void reset() {
		currentFile = null;
		sliderPresets.clear();
		customMorphTargets.clear();
		morphedNpcs.clear();
		npcDatabase.clear();
	}
}