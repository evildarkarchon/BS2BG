package com.asdasfa.jbs2bg.etc;

import java.util.concurrent.ThreadLocalRandom;

import com.sun.javafx.scene.control.skin.ListViewSkin;
import com.sun.javafx.scene.control.skin.VirtualFlow;

import javafx.scene.control.ListView;

/**
 * 
 * @author Totiman
 */
@SuppressWarnings("restriction")
public class MyUtils {
	
	/**
	 * 
	 * @param min
	 * @param max
	 * @return a random int between min (inclusive) and max (inclusive)
	 */
	public static int random(int min, int max) {
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}
	
	/**
	 * 
	 * @param listView
	 * @param index
	 * @return a boolean if index is visible within the current listView
	 */
	public static boolean isIndexVisible(ListView<?> listView, int index) {
		if (listView.getItems().size() <= 2)
			return true;
		
		try {
	        ListViewSkin<?> ts = (ListViewSkin<?>) listView.getSkin();
	        VirtualFlow<?> vf = (VirtualFlow<?>) ts.getChildren().get(0);
	        if (vf == null)
	        	return false;
	        
	        int firstVisible = vf.getFirstVisibleCell().getIndex();
	        int lastVisible = vf.getLastVisibleCell().getIndex();
	        
	        if (index > firstVisible && index < lastVisible)
	        	return true;
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
		
		return false;
	}
}