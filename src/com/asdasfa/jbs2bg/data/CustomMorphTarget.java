package com.asdasfa.jbs2bg.data;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Totiman
 */
public class CustomMorphTarget extends MorphTarget {
    
    public CustomMorphTarget(String name) {
        this.name = name;
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
