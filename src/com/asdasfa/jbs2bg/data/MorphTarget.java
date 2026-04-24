package com.asdasfa.jbs2bg.data;

import java.util.Comparator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 *
 * @author Totiman
 */
public class MorphTarget {

	protected String name;
	protected final ObservableList<SliderPreset> sliderPresets = FXCollections.observableArrayList();

	public String getName() {
		return name;
	}

	public void addSliderPreset(SliderPreset sliderPreset) {
		boolean exists = false;
		for (int i = 0; i < sliderPresets.size(); i++) {
			if (sliderPresets.get(i).getName().equalsIgnoreCase(sliderPreset.getName())) {
				exists = true;
				break;
			}
		}
		if (!exists)
			sliderPresets.add(sliderPreset);
		
		sortPresets();
	}

	public void removeSliderPreset(String name) {
		for (int i = sliderPresets.size() - 1; i >= 0; i--) {
			SliderPreset sliderPreset = sliderPresets.get(i);
			if (sliderPreset.getName().equalsIgnoreCase(name)) {
				sliderPresets.remove(i);
				break;
			}
		}
		
		sortPresets();
	}

	public void removeSliderPreset(SliderPreset sliderPreset) {
		removeSliderPreset(sliderPreset.getName());
	}

	public void clearSliderPresets() {
		sliderPresets.clear();
	}

	public ObservableList<SliderPreset> getSliderPresets() {
		return sliderPresets;
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
    
    public boolean hasPresets() {
    	if (sliderPresets.size() > 0)
    		return true;
    	
    	return false;
    }

	public String toLine() {
		return null;
	}
}