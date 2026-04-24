package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.Initializable;
import javafx.stage.Stage;

/**
 * 
 * @author Totiman
 */
public class CustomController implements Initializable {
	
	protected Main main;
	protected Stage stage;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	protected void onPostInit() {
	}
	
	public void postInitialize(Main main, Stage stage) {
		this.main = main;
		this.stage = stage;
		onPostInit();
	}
}