package com.asdasfa.jbs2bg;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Screen;

/**
 * 
 * @author Totiman
 */
public class PopupImageViewController extends CustomController {
	
	@FXML
	private ScrollPane spImageContainer;
	
	private ImageView ivImage;
	private Image image;
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		ivImage = new ImageView();
	}
	
	@Override
	protected void onPostInit() {
		stage.setOnShowing(e -> {
			double w = 290 + main.decorWidth;
			double h = 256 + main.decorHeight;
			
			if (image != null) {
				w = image.getWidth() + 21 + main.decorWidth;
			}
				
			Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
			double x = screenBounds.getWidth() - w;
			double y = 0;
			stage.setX(x);
			stage.setY(y);
			
			stage.setWidth(w);
			stage.setHeight(h);
		});
		
		stage.setOnHidden(e -> {
			spImageContainer.setHvalue(0);
			spImageContainer.setVvalue(0);
		});
	}
	
	public void setTitle(String title) {
		stage.setTitle(title);
	}
	
	public void setImage(File file) {
		ivImage.setImage(null);
		image = null;
		
		if (file == null) {
			stage.setMaxWidth(290 + main.decorWidth);
			stage.setMaxHeight(256 + main.decorHeight);
			spImageContainer.setContent(null);
			spImageContainer.setContent(ivImage);
			spImageContainer.setHvalue(0);
			spImageContainer.setVvalue(0);
			return;
		}
		
		image = new Image(file.toURI().toString());
		
		stage.setMaxWidth(image.getWidth() + 21 + main.decorWidth);
		stage.setMaxHeight(image.getHeight() + 20 + main.decorHeight);
		
		ivImage.setImage(image);
		
		spImageContainer.setContent(null);
		spImageContainer.setContent(ivImage);
		spImageContainer.setHvalue(0);
		spImageContainer.setVvalue(0);
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
}