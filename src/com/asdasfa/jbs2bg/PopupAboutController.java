package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

/**
 * 
 * @author Totiman
 */
public class PopupAboutController extends CustomController {
	
	@FXML
	private Label lblVersion;
	@FXML
	private TextArea taDescription;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	@Override
	protected void onPostInit() {
		lblVersion.setText("v" + main.versionMajor + "." + main.versionMiddle + "." + main.versionMinor);
		taDescription.setText("A tool for generating RaceMenu BodyGen templates and morphs INI from BodySlide XMLs.");
	}
}
