package com.asdasfa.jbs2bg.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.asdasfa.jbs2bg.MainController;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.WriterConfig;

/**
 *
 * @author Totiman
 */
public class Settings {
	
	// Non-UUNP
	private final static LinkedHashMap<String, DefaultSliderValue> DEFAULTS = new LinkedHashMap<String, DefaultSliderValue>();
	private final static LinkedHashMap<String, Float> MULTIPLIERS = new LinkedHashMap<String, Float>();
	private final static ArrayList<String> INVERTED = new ArrayList<String>();
	
	// UUNP
	private final static LinkedHashMap<String, DefaultSliderValue> DEFAULTS_UUNP = new LinkedHashMap<String, DefaultSliderValue>();
	private final static LinkedHashMap<String, Float> MULTIPLIERS_UUNP = new LinkedHashMap<String, Float>();
	private final static ArrayList<String> INVERTED_UUNP = new ArrayList<String>();
	
	/**
	 * 
	 * @return 1 if init is success, 0 if settings.json failed
	 */
	public static int init() {
		DEFAULTS.put("Breasts", new DefaultSliderValue(0.2f, 1f));
		DEFAULTS.put("BreastsSmall", new DefaultSliderValue(1f, 1f));
		DEFAULTS.put("NippleDistance", new DefaultSliderValue(1f, 1f));
		DEFAULTS.put("NippleSize", new DefaultSliderValue(1f, 1f));
		DEFAULTS.put("ButtCrack", new DefaultSliderValue(1f, 1f));
		DEFAULTS.put("Butt", new DefaultSliderValue(0f, 1f));
		DEFAULTS.put("ButtSmall", new DefaultSliderValue(1f, 1f));
		DEFAULTS.put("Waist", new DefaultSliderValue(0f, 1f));
		DEFAULTS.put("Legs", new DefaultSliderValue(0f, 1f));
		DEFAULTS.put("Ankles", new DefaultSliderValue(1f, 1f));
		DEFAULTS.put("Arms", new DefaultSliderValue(0f, 1f));
		DEFAULTS.put("ShoulderWidth", new DefaultSliderValue(1f, 1f));
		
		MULTIPLIERS.clear();
		
		INVERTED.add("Breasts");
		INVERTED.add("BreastsSmall");
		INVERTED.add("NippleDistance");
		INVERTED.add("NippleSize");
		INVERTED.add("ButtCrack");
		INVERTED.add("Butt");
		INVERTED.add("ButtSmall");
		INVERTED.add("Legs");
		INVERTED.add("Ankles");
		INVERTED.add("Arms");
		INVERTED.add("ShoulderWidth");
		
		// UUNP
		DEFAULTS_UUNP.put("Breasts", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("BreastsSmall", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("NippleDistance", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("NippleSize", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("Arms", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("ShoulderWidth", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("ButtCrack", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("Butt", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("ButtSmall", new DefaultSliderValue(1f, 1f));
		DEFAULTS_UUNP.put("Legs", new DefaultSliderValue(1f, 1f));
		
		MULTIPLIERS_UUNP.clear();
		
		INVERTED_UUNP.add("Breasts");
		INVERTED_UUNP.add("BreastsSmall");
		INVERTED_UUNP.add("NippleDistance");
		INVERTED_UUNP.add("NippleSize");
		INVERTED_UUNP.add("Arms");
		INVERTED_UUNP.add("ShoulderWidth");
		INVERTED_UUNP.add("ButtCrack");
		INVERTED_UUNP.add("Butt");
		INVERTED_UUNP.add("ButtSmall");
		INVERTED_UUNP.add("Legs");
		
		boolean settingsOk = true;
		try {
			File settingsFile = new File("settings.json");
			if (settingsFile.exists()) {
				loadSettingsFile(settingsFile);
			} else {
				createSettingsFile(settingsFile);
			}
		} catch (Exception e) {
			settingsOk = false;
		}
		
		boolean settingsUUNPOk = true;
		try {
			File settingsFile = new File("settings_UUNP.json");
			if (settingsFile.exists()) {
				loadSettingsUUNPFile(settingsFile);
			} else {
				createSettingsUUNPFile(settingsFile);
			}
		} catch (Exception e) {
			settingsUUNPOk = false;
		}
		
		int initSuccess = 1;
		if (settingsOk && settingsUUNPOk) { // Success
			initSuccess = 1;
		}
		if (!settingsOk && settingsUUNPOk) { // settings.json failed
			initSuccess = 0;
		}
		if (settingsOk && !settingsUUNPOk) { // settings_UUNP.json failed
			initSuccess = -1;
		}
		if (!settingsOk && !settingsUUNPOk) { // Both settings.json and settings_UUNP.json failed
			initSuccess = -2;
		}
		
		return initSuccess;
	}

	private static void createSettingsFile(File file) {
		JsonObject root = new JsonObject();
		
		JsonObject joDefaults = new JsonObject();
		JsonObject joMultipliers = new JsonObject();
		JsonArray joInverses = new JsonArray();
		
		for (Map.Entry<String, DefaultSliderValue> entry : DEFAULTS.entrySet()) {
			String key = entry.getKey();
			DefaultSliderValue dsl = (DefaultSliderValue) entry.getValue();
			joDefaults.add(key, newJsonObjectDefault(dsl.getValueSmall(), dsl.getValueBig()));
		}
		
		for (Map.Entry<String, Float> entry : MULTIPLIERS.entrySet()) {
			String key = entry.getKey();
			float v = (float) entry.getValue();
			joMultipliers.add(key, v);
		}
		
		for (String s : INVERTED) {
			joInverses.add(s);
		}
		
		root.add("Defaults", joDefaults);
		root.add("Multipliers", joMultipliers);
		root.add("Inverted", joInverses);
		
		String settingsString = root.toString(WriterConfig.PRETTY_PRINT);
		
		try {
			if (file.exists())
				FileUtils.deleteQuietly(file);
			
			FileUtils.writeStringToFile(file, settingsString, "UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void createSettingsUUNPFile(File file) {
		JsonObject root = new JsonObject();
		
		JsonObject joDefaults = new JsonObject();
		JsonObject joMultipliers = new JsonObject();
		JsonArray joInverses = new JsonArray();
		
		for (Map.Entry<String, DefaultSliderValue> entry : DEFAULTS_UUNP.entrySet()) {
			String key = entry.getKey();
			DefaultSliderValue dsl = (DefaultSliderValue) entry.getValue();
			joDefaults.add(key, newJsonObjectDefault(dsl.getValueSmall(), dsl.getValueBig()));
		}
		
		for (Map.Entry<String, Float> entry : MULTIPLIERS_UUNP.entrySet()) {
			String key = entry.getKey();
			float v = (float) entry.getValue();
			joMultipliers.add(key, v);
		}
		
		for (String s : INVERTED_UUNP) {
			joInverses.add(s);
		}
		
		root.add("Defaults", joDefaults);
		root.add("Multipliers", joMultipliers);
		root.add("Inverted", joInverses);
		
		String settingsString = root.toString(WriterConfig.PRETTY_PRINT);
		
		try {
			if (file.exists())
				FileUtils.deleteQuietly(file);
			
			FileUtils.writeStringToFile(file, settingsString, "UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static JsonObject newJsonObjectDefault(float valueSmall, float valueBig) {
		JsonObject jo = new JsonObject();
		jo.add("valueSmall", valueSmall);
		jo.add("valueBig", valueBig);
		
		return jo;
	}

	private static void loadSettingsFile(File file) {
		try {
			String s = FileUtils.readFileToString(file, "UTF-8");
			
			JsonObject root = Json.parse(s).asObject();
			
			JsonObject joDefaults = root.get("Defaults").asObject();
			JsonObject joMultipliers = root.get("Multipliers").asObject();
			JsonArray joInverses = root.get("Inverted").asArray();
			
			DEFAULTS.clear();
			MULTIPLIERS.clear();
			INVERTED.clear();
			
			for (Member member : joDefaults) {
				String key = member.getName();
				JsonObject jo = member.getValue().asObject();
				float valueSmall = jo.getFloat("valueSmall", 0f);
				float valueBig = jo.getFloat("valueBig", 1f);
				DefaultSliderValue dsl = new DefaultSliderValue(valueSmall, valueBig);
				DEFAULTS.put(key, dsl);
			}
			
			for (Member member : joMultipliers) {
				String key = member.getName();
				float v = member.getValue().asFloat();
				MULTIPLIERS.put(key, v);
			}
			
			for (int i = 0; i < joInverses.size(); i++) {
				String v = joInverses.get(i).asString();
				INVERTED.add(v);
			}
		} catch (FileNotFoundException e) {
			Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, e);
		} catch (IOException e) {
			Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, e);
		}
	}
	
	private static void loadSettingsUUNPFile(File file) {
		try {
			String s = FileUtils.readFileToString(file, "UTF-8");
			
			JsonObject root = Json.parse(s).asObject();
			
			JsonObject joDefaults = root.get("Defaults").asObject();
			JsonObject joMultipliers = root.get("Multipliers").asObject();
			JsonArray joInverses = root.get("Inverted").asArray();
			
			DEFAULTS_UUNP.clear();
			MULTIPLIERS_UUNP.clear();
			INVERTED_UUNP.clear();
			
			for (Member member : joDefaults) {
				String key = member.getName();
				JsonObject jo = member.getValue().asObject();
				float valueSmall = jo.getFloat("valueSmall", 0f);
				float valueBig = jo.getFloat("valueBig", 1f);
				DefaultSliderValue dsl = new DefaultSliderValue(valueSmall, valueBig);
				DEFAULTS_UUNP.put(key, dsl);
			}
			
			for (Member member : joMultipliers) {
				String key = member.getName();
				float v = member.getValue().asFloat();
				MULTIPLIERS_UUNP.put(key, v);
			}
			
			for (int i = 0; i < joInverses.size(); i++) {
				String v = joInverses.get(i).asString();
				INVERTED_UUNP.add(v);
			}
		} catch (FileNotFoundException e) {
			Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, e);
		} catch (IOException e) {
			Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, e);
		}
	}

	/**
	 * 
	 * @param sliderName
	 * @return multiplier for slider, returns 1 if slider is not defined
	 */
	public static float getMultiplier(String sliderName) {
		Float v = MULTIPLIERS.get(sliderName);
		if (v == null)
			return 1f;
		return (float) v;
	}
	
	/**
	 * 
	 * @param sliderName
	 * @return multiplier for slider for UUNP, returns 1 if slider is not defined
	 */
	public static float getMultiplierUUNP(String sliderName) {
		Float v = MULTIPLIERS_UUNP.get(sliderName);
		if (v == null)
			return 1f;
		return (float) v;
	}
	
	public static LinkedHashMap<String, DefaultSliderValue> getDefaultsMap() {
		return DEFAULTS;
	}
	
	public static LinkedHashMap<String, DefaultSliderValue> getDefaultsMapUUNP() {
		return DEFAULTS_UUNP;
	}
	
	public static int getDefaultValueSmall(String name) {
		DefaultSliderValue dsv = DEFAULTS.get(name);
		if (dsv != null) {
			return (int) (dsv.getValueSmall() * 100);
		} else {
			return 0;
		}
	}
	
	public static int getDefaultValueSmallUUNP(String name) {
		DefaultSliderValue dsv = DEFAULTS_UUNP.get(name);
		if (dsv != null) {
			return (int) (dsv.getValueSmall() * 100);
		} else {
			return 0;
		}
	}
	
	public static int getDefaultValueBig(String name) {
		DefaultSliderValue dsv = DEFAULTS.get(name);
		if (dsv != null) {
			return (int) (dsv.getValueBig() * 100);
		} else {
			return 0;
		}
	}
	
	public static int getDefaultValueBigUUNP(String name) {
		DefaultSliderValue dsv = DEFAULTS_UUNP.get(name);
		if (dsv != null) {
			return (int) (dsv.getValueBig() * 100);
		} else {
			return 0;
		}
	}
	
	public static boolean isInverted(String sliderName) {
		for (int i = 0; i < INVERTED.size(); i++) {
			String slider = INVERTED.get(i);
			if (slider.equalsIgnoreCase(sliderName)) {
				return true;
			}
		}
		
		return false;
	}
	
	public static boolean isInvertedUUNP(String sliderName) {
		for (int i = 0; i < INVERTED_UUNP.size(); i++) {
			String slider = INVERTED_UUNP.get(i);
			if (slider.equalsIgnoreCase(sliderName)) {
				return true;
			}
		}
		
		return false;
	}

	public static class DefaultSliderValue {
		private float valueSmall;
		private float valueBig;
		
		public DefaultSliderValue(float valueSmall, float valueBig) {
			this.valueSmall = valueSmall;
			this.valueBig = valueBig;
		}
		
		public float getValueSmall() {
			return valueSmall;
		}
		
		public float getValueBig() {
			return valueBig;
		}
	}
}