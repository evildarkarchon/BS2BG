package com.asdasfa.jbs2bg.etc;

import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;

/**
 * @see <a href="https://stackoverflow.com/questions/35988545/javafx-listview-keyboard-navigability">Stack Overflow post</a>
 */
public class KeyNavigationListener implements EventHandler<KeyEvent> {
	
	protected String searchText = "";
	protected int searchTextSkip = 0;
	protected int skipped = 0;
	protected boolean found = false;
	
	private long searchTextLastTyped = 0;
	
	public KeyNavigationListener() {
	}
	
	@Override
	public void handle(KeyEvent event) {
		if (event.getCharacter() != null) {
			// In case of same character typed more times = search next occurrence
			if (searchText.equals(event.getCharacter())) {
				searchTextSkip++;
			} else {
				// In case typing more characters relatively quickly = append character
				if (System.currentTimeMillis() - searchTextLastTyped < 750) {
					searchText += event.getCharacter();
				} else { // Typing new character after pause = new search
					searchText = event.getCharacter();
				}
			}
			searchTextLastTyped = System.currentTimeMillis();

			found = false;
			skipped = 0;
			test();

			// Reset to first occurrence
			if (!found) {
				searchTextSkip = 0;
				searchText = "";
			}
		}
	}
	
	public void test() {
		/*
		for (int i = 0; i < listPreset.getItems().size(); i++) {
			String item = listPreset.getItems().get(i);
			if (item.toUpperCase().startsWith(searchText.toUpperCase())) {
				if (searchTextSkip > skipped) {
					skipped++;
					continue;
				}
				listPreset.getSelectionModel().select(i);
				listPreset.getFocusModel().focus(i);
				listPreset.scrollTo(i);
				found = true;
				break;
			}
		}
		*/
	}
	
	public void reset() {
		searchTextSkip = 0;
		searchText = "";
	}
}
