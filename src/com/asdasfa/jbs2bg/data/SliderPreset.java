package com.asdasfa.jbs2bg.data;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.WriterConfig;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;


/**
 *
 * @author Totiman
 */
public class SliderPreset {

	private String name;
	private final ObservableList<SetSlider> setSliders = FXCollections.observableArrayList();
	private final ObservableList<SetSlider> missingDefaultSetSliders = FXCollections.observableArrayList();
	private boolean isUUNP = false;

	public SliderPreset(Element e) {
		name = e.getAttribute("name"); // Name of SliderPreset
		name = name.replaceAll("\\.", " "); // Replace all dots with space

		NodeList sliderNodes = e.getElementsByTagName("SetSlider");
		for (int i = 0; i < sliderNodes.getLength(); i++) { // For each SetSlider in the xml
			Node sliderNode = sliderNodes.item(i);
			if (sliderNode.getNodeType() == Node.ELEMENT_NODE) {
				Element sliderElement = (Element) sliderNode;
				String sliderName = sliderElement.getAttribute("name"); // Name of SetSlider
				String size = sliderElement.getAttribute("size");

				int value = 0;

				if (size.equalsIgnoreCase("small")) { // Small size
					String sValue = sliderElement.getAttribute("value");
					int v = Integer.parseInt(sValue);
					value = v;
				} else { // Big size
					String sValue = sliderElement.getAttribute("value");
					int v = Integer.parseInt(sValue);
					value = v;
				}

				SetSlider slider = getSetSlider(sliderName);
				if (slider == null) { // Slider doesn't exist, create new one
					slider = new SetSlider();
					slider.setName(sliderName);
					if (size.equalsIgnoreCase("small")) { // Small size
						slider.setValueSmall(value);
					} else { // Big size
						slider.setValueBig(value);
					}
					setSliders.add(slider);
				} else { // Slider exists, just set again for probably small or big size value
					if (size.equalsIgnoreCase("small")) { // Small size
						slider.setValueSmall(value);
					} else { // Big size
						slider.setValueBig(value);
					}
				}
			}
		}
		sortSetSliders();
		
		setIsUUNP(false);
	}

	public SliderPreset(SliderPreset sliderPreset) {
		name = sliderPreset.getName();
		name = name.replaceAll("\\.", " "); // Replace all dots with space

		clearAndCopySliders(sliderPreset);
	}
	
	public void clearAndCopySliders(SliderPreset sliderPreset) {
		setSliders.clear();
		for (int i = 0; i < sliderPreset.getSetSliders().size(); i++) {
			SetSlider setSlider = sliderPreset.getSetSliders().get(i);

			SetSlider setSliderCopy = new SetSlider(setSlider);
			setSliders.add(setSliderCopy);
		}
		sortSetSliders();
		
		setIsUUNP(sliderPreset.isUUNP());
	}

	public SliderPreset(Member member) {
		name = member.getName();
		name = name.replaceAll("\\.", " "); // Replace all dots with space

		JsonObject jo = member.getValue().asObject();
		
		isUUNP = jo.getBoolean("isUUNP", false);

		JsonArray ja = jo.get("SetSliders").asArray();
		for (int i = 0; i < ja.size(); i++) {
			SetSlider slider = new SetSlider(ja.get(i).asObject());
			if (slider.getValueSmall() == null && slider.getValueBig() == null) { // Both values are null, put it in missing default
				missingDefaultSetSliders.add(slider);
			} else {
				setSliders.add(slider);
			}
		}
		sortSetSliders();
		
		setMissingDefaultSetSliders(); // Call this instead of setIsUUNP to keep the loaded missing default sliders
	}
	
	public void setIsUUNP(boolean isUUNP) {
		this.isUUNP = isUUNP;
		
		missingDefaultSetSliders.clear();
		//System.out.println("MDS Size: " + missingDefaultSetSliders.size());
		setMissingDefaultSetSliders();
	}
	
	public boolean isUUNP() {
		return isUUNP;
	}

	private void sortSetSliders() {
		if (setSliders.size() > 0)
			Collections.sort(setSliders, comparatorSetSliders);
	}
	private Comparator<SetSlider> comparatorSetSliders = new Comparator<SetSlider>() {
		@Override
		public int compare(final SetSlider setSlider1, final SetSlider setSlider2) {
			return setSlider1.getName().compareToIgnoreCase(setSlider2.getName());
		}
	};

	private SetSlider getSetSlider(String name) {
		for (int i = 0; i < setSliders.size(); i++) {
			SetSlider setSlider = setSliders.get(i);
			if (setSlider.getName().equalsIgnoreCase(name))
				return setSlider;
		}
		return null;
	}
	
	public SetSlider getMissingDefaultSetSlider(String name) {
		for (int i = 0; i < missingDefaultSetSliders.size(); i++) {
			SetSlider setSlider = missingDefaultSetSliders.get(i);
			if (setSlider.getName().equalsIgnoreCase(name))
				return setSlider;
		}
		return null;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public ObservableList<SetSlider> getSetSliders() {
		return setSliders;
	}
	
	public ObservableList<SetSlider> getMissingDefaultSetSliders() {
		return missingDefaultSetSliders;
	}
	
	public void setMissingDefaultSetSliders() {
		Set<Entry<String, DefaultSliderValue>> entrySet;
		if (!isUUNP) {
			entrySet = Settings.getDefaultsMap().entrySet();
		} else {
			entrySet = Settings.getDefaultsMapUUNP().entrySet();
		}
		
		//missingDefaultSetSliders.clear(); // Only cleared when toggling UUNP
		for (Entry<String, DefaultSliderValue> entry : entrySet) {
			String sliderName = entry.getKey();
			
			SetSlider slider = getSetSlider(sliderName);
			if (slider == null) { // No such slider defined in the preset
				SetSlider defaultMissingSlider = getMissingDefaultSetSlider(sliderName);
				if (defaultMissingSlider == null) { // Slider is also not in missingDefaultSliders
					defaultMissingSlider = new SetSlider();
					defaultMissingSlider.setName(sliderName);
					// All missing default SetSliders will always have null values
					defaultMissingSlider.setValueSmall(null);
					defaultMissingSlider.setValueBig(null);
					
					missingDefaultSetSliders.add(defaultMissingSlider);
				}
			}
		}
	}
	
	private final ArrayList<SetSlider> allSetSliders = new ArrayList<SetSlider>(32);
	/**
	 * 
	 * @return an ArrayList of all SetSliders, including disabled and missing default SetSliders.
	 */
	public ArrayList<SetSlider> getAllSetSliders() {
		allSetSliders.clear();
		allSetSliders.addAll(setSliders);
		allSetSliders.addAll(missingDefaultSetSliders);
		if (allSetSliders.size() > 0) // Sort sliders
			Collections.sort(allSetSliders, comparatorSetSliders);
		
		return allSetSliders;
	}
	
	private final ArrayList<SetSlider> enabledAndDefaultSetSliders = new ArrayList<SetSlider>(32);
	/**
	 * 
	 * @return an ArrayList of SetSliders that are enabled, including the missing default SetSliders.
	 */
	public ArrayList<SetSlider> getEnabledAndDefaultSetSliders() {
		ArrayList<SetSlider> enabledSetSliders = getEnabledSetSliders();
		
		enabledAndDefaultSetSliders.clear();
		enabledAndDefaultSetSliders.addAll(enabledSetSliders);
		for (int i = 0; i < missingDefaultSetSliders.size(); i++) {
			SetSlider setSlider = missingDefaultSetSliders.get(i);
			if (setSlider.isEnabled()) {
				enabledAndDefaultSetSliders.add(setSlider);
			}
		}
		if (enabledAndDefaultSetSliders.size() > 0) // Sort sliders
			Collections.sort(enabledAndDefaultSetSliders, comparatorSetSliders);
		
		return enabledAndDefaultSetSliders;
	}
	
	private final ArrayList<SetSlider> enabledSetSliders = new ArrayList<SetSlider>(32);
	public ArrayList<SetSlider> getEnabledSetSliders() {
		enabledSetSliders.clear();
		for (int i = 0; i < setSliders.size(); i++) {
			SetSlider setSlider = setSliders.get(i);
			if (setSlider.isEnabled())
				enabledSetSliders.add(setSlider);
		}
		return enabledSetSliders;
	}

	public String toLine(boolean omitRedundantSliders) {
		ArrayList<SetSlider> bgSetSliders = getEnabledAndDefaultSetSliders();
		
		//String[] sliderValues = new String[enabledSetSliders.size() + missingDefaultSetSliders.size()];
		ArrayList<String> sliderValues = new ArrayList<String>(32);
		for (int i = 0; i < bgSetSliders.size(); i++) {
			SetSlider setSlider = bgSetSliders.get(i);
			
			if (omitRedundantSliders) {
				boolean exclude = false;
				boolean inverted = false;
				if (!isUUNP) {
					inverted = Settings.isInverted(setSlider.getName());
				} else {
					inverted = Settings.isInvertedUUNP(setSlider.getName());
				}
				
				if (!inverted) { // Exclude non-inverted sliders that are both 0
					if (setSlider.getValueSmall() == 0 && setSlider.getValueSmall() == setSlider.getValueBig()) {
						exclude = true;
					}
				} else { // Exclude inverted sliders that are both 100
					if (setSlider.getValueSmall() == 100 && setSlider.getValueSmall() == setSlider.getValueBig()) {
						exclude = true;
					}
				}
				
				if (!exclude)
					sliderValues.add(setSlider.toText());
			} else {
				sliderValues.add(setSlider.toText());
			}
		}
		
		String[] valueArray = sliderValues.toArray(new String[0]);
		List<String> sliderList = Arrays.asList(valueArray);
		String line = "";
		line = String.join(", ", sliderList);
		line = name + "=" + line;

		sliderValues = null;
		bgSetSliders.clear();

		return line;
	}
	
	public String toBosJson() {
		JsonObject root = new JsonObject();
		
		JsonObject joString = new JsonObject();
		JsonObject joInt = new JsonObject();
		JsonObject joFloat = new JsonObject();
		
		BigDecimal bd;
		
		LinkedHashMap<String, String> sliderNames = new LinkedHashMap<String, String>();
		LinkedHashMap<String, Float> highValues = new LinkedHashMap<String, Float>();
		LinkedHashMap<String, Float> lowValues = new LinkedHashMap<String, Float>();
		int num = 1;
		
		ArrayList<SetSlider> bosSliders = getEnabledAndDefaultSetSliders();
		for (int i = 0; i < bosSliders.size(); i++) {
			SetSlider setSlider = bosSliders.get(i);
			
			boolean exclude = false;
			float mult = 1f;
			boolean inverted = false;
			
			float big;
			float small;
			
			if (!isUUNP) {
				inverted = Settings.isInverted(setSlider.getName());
				mult = Settings.getMultiplier(setSlider.getName());
			} else {
				inverted = Settings.isInvertedUUNP(setSlider.getName());
				mult = Settings.getMultiplierUUNP(setSlider.getName());
			}
			
			// Determine if should be excluded
			if (!isUUNP) { // Non-UUNP
				if (!inverted) { // Exclude non-inverted sliders that are both 0
					if (setSlider.getValueSmall() == 0 && setSlider.getValueSmall() == setSlider.getValueBig()) {
						exclude = true;
					}
				} else { // Exclude inverted sliders that are both 100
					if (setSlider.getValueSmall() == 100 && setSlider.getValueSmall() == setSlider.getValueBig()) {
						exclude = true;
					}
				}
			} else { // UUNP
				if (!inverted) { // Exclude non-inverted sliders that are both 0
					if (setSlider.getValueSmall() == 0 && setSlider.getValueSmall() == setSlider.getValueBig()) {
						exclude = true;
					}
				} else { // Exclude inverted sliders that are both 100
					if (setSlider.getValueSmall() == 100 && setSlider.getValueSmall() == setSlider.getValueBig()) {
						exclude = true;
					}
				}
			}
			
			// High Value
			big = setSlider.getValueBig() * 0.01f;
			
			if (inverted) { // Inverted slider
				big = 1f - big;
			}
			
			big *= mult;
			
			bd = new BigDecimal(Float.toString(big));
			bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
			big = bd.floatValue();
			
			// Low Value
			small = setSlider.getValueSmall() * 0.01f;
			
			if (inverted) { // Inverted slider
				small = 1f - small;
			}
			
			small *= mult;
			
			bd = new BigDecimal(Float.toString(small));
			bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
			small = bd.floatValue();
			
			// Slider Name
			String sliderName = setSlider.getName();
			
			if (!exclude) {
				sliderNames.put("slidername" + num, sliderName);
				highValues.put("highvalue" + num, big);
				lowValues.put("lowvalue" + num, small);
				
				num++;
			}
		}
		
		joString.add("bodyname", name);
		for (Map.Entry<String, String> entry : sliderNames.entrySet()) {
			joString.add(entry.getKey(), entry.getValue());
		}
		
		for (Map.Entry<String, Float> entry : highValues.entrySet()) {
			joFloat.add(entry.getKey(), entry.getValue());
		}
		
		for (Map.Entry<String, Float> entry : lowValues.entrySet()) {
			joFloat.add(entry.getKey(), entry.getValue());
		}
		
		joInt.add("slidersnumber", sliderNames.size());
		
		root.add("string", joString);
		root.add("int", joInt);
		root.add("float", joFloat);
		
		return root.toString(WriterConfig.PRETTY_PRINT);
	}

	public class SetSlider {

		private String name;
		private boolean enabled = true;
		private Integer valueSmall = null;
		private Integer valueBig = null;

		private int pctMin = 100;
		private int pctMax = 100;

		public SetSlider() {
		}

		public SetSlider(SetSlider setSlider) {
			name = setSlider.getName();
			enabled = setSlider.isEnabled();
			valueSmall = setSlider.getValueSmall();
			valueBig = setSlider.getValueBig();
			pctMin = setSlider.getPctMin();
			pctMax = setSlider.getPctMax();
		}

		public SetSlider(JsonObject jsonObject) {
			name = jsonObject.getString("name", "");
			enabled = jsonObject.getBoolean("enabled", true);
			JsonValue jv;
			jv = jsonObject.get("valueSmall");
			if (!jv.isNull()) {
				if (jv.isNumber()) {
					valueSmall = jv.asInt();
				} else {
					valueSmall = null;
				}
			} else {
				valueSmall = null;
			}
			jv = jsonObject.get("valueBig");
			if (!jv.isNull()) {
				if (jv.isNumber()) {
					valueBig = jv.asInt();
				} else {
					valueBig = null;
				}
			} else {
				valueBig = null;
			}
			pctMin = jsonObject.getInt("pctMin", 0);
			pctMax = jsonObject.getInt("pctMax", 100);
		}

		public JsonObject toJsonObject() {
			JsonObject jo = new JsonObject();
			
			jo.add("name", name);
			jo.add("enabled", enabled);
			if (valueSmall != null) {
				jo.add("valueSmall", valueSmall);
			} else {
				String v = null;
				jo.add("valueSmall", v);
			}
			if (valueBig != null) {
				jo.add("valueBig", valueBig);
			} else {
				String v = null;
				jo.add("valueBig", v);
			}
			jo.add("pctMin", pctMin);
			jo.add("pctMax", pctMax);
			
			return jo;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
		
		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
		
		public boolean isEnabled() {
			return enabled;
		}

		public void setValueSmall(Integer value) {
			valueSmall = value;
		}

		public Integer getValueSmall() {
			if (valueSmall != null) {
				return valueSmall;
			} else {
				if (!isUUNP) {
					return Settings.getDefaultValueSmall(name);
				} else {
					return Settings.getDefaultValueSmallUUNP(name);
				}
			}
		}

		public void setValueBig(Integer value) {
			valueBig = value;
		}

		public Integer getValueBig() {
			if (valueBig != null) {
				return valueBig;
			} else {
				if (!isUUNP) {
					return Settings.getDefaultValueBig(name);
				} else {
					return Settings.getDefaultValueBigUUNP(name);
				}
			}
		}

		public void setPctMin(int value) {
			pctMin = value;
		}

		public int getPctMin() {
			return pctMin;
		}

		public void setPctMax(int value) {
			pctMax = value;
		}

		public int getPctMax() {
			return pctMax;
		}

		public String toText() {
			String v = "";

			BigDecimal bd;

			float diff;
			float min;
			float max;

			float small = getValueSmall() * 0.01f;
			float big = getValueBig() * 0.01f;

			boolean inverted = false;
			float mult = 1f;

			if (!isUUNP) {
				inverted = Settings.isInverted(name);
				mult = Settings.getMultiplier(name);
			} else {
				inverted = Settings.isInvertedUUNP(name);
				mult = Settings.getMultiplierUUNP(name);
			}

			if (inverted) { // Inverted slider
				small = 1f - small;
				big = 1f - big;
			}

			diff = big - small;
			min = small + (diff * (pctMin * 0.01f));
			max = small + (diff * (pctMax * 0.01f));
			
			min *= mult;
			max *= mult;

			bd = new BigDecimal(Float.toString(min));
			bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
			min = bd.floatValue();
			bd = new BigDecimal(Float.toString(max));
			bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
			max = bd.floatValue();

			if (min != max) {
				v = name + "@" + min + ":" + max;
			} else {
				v = name + "@" + max;
			}

			return v;
		}
	} // End of SetSlider class
}