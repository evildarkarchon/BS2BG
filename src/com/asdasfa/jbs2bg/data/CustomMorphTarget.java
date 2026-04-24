package com.asdasfa.jbs2bg.data;

import java.util.Arrays;
import java.util.List;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;

import javafx.collections.ObservableList;

/**
 *
 * @author Totiman
 */
public class CustomMorphTarget extends MorphTarget {
    
    public CustomMorphTarget(String name) {
        this.name = name;
    }
    
    public CustomMorphTarget(Member member, ObservableList<SliderPreset> sliderPresets) {
        name = member.getName();
        
        JsonObject jo = member.getValue().asObject();
        
        JsonArray ja = jo.get("SliderPresets").asArray();
        for (int i = 0; i < ja.size(); i++) {
            String presetName = ja.get(i).asString();
            
            SliderPreset sliderPreset = null;
            for (int j = 0; j < sliderPresets.size(); j++) { // Search the list for existing sliderPreset
                SliderPreset sp = sliderPresets.get(j);
                if (sp.getName().equalsIgnoreCase(presetName)) {
                    sliderPreset = sp;
                    break;
                }
            }
            if (sliderPreset != null)
                addSliderPreset(sliderPreset);
        }
    }
    
    @Override
    public String toLine() {
        String line;
        line = name + "=";
        
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