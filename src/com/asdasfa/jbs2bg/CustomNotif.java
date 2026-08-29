package com.asdasfa.jbs2bg;

import java.io.IOException;

import com.asdasfa.jbs2bg.fx.DialogGraphics;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * 
 * @author Totiman
 */
public class CustomNotif extends VBox {

	private Stage stage;
	
	@FXML
	private ImageView ivIcon;
	@FXML
	private Label lblMsg;
	@FXML
	private Button btnOk;
	
	private Image iconInfo;
	private Image iconError;
	
	public CustomNotif(Main main) {
		this(main.style, main.primaryStage);
	}

	/**
	 * Loads the notification graph as a custom FXML root owned by this control.
	 *
	 * @param stylesheet stylesheet URL applied to the dialog scene
	 * @param owner owning window, or null for an unowned dialog
	 * @throws RuntimeException when the FXML graph cannot be loaded
	 */
	// The custom-root FXML idiom hands `this` to the loader before construction completes; the
	// subclass (if any) adds no state the loader could observe, so the escape is deliberate.
	@SuppressWarnings("this-escape")
	public CustomNotif(String stylesheet, Window owner) {
		try {
			iconInfo = DialogGraphics.image(DialogGraphics.Semantic.INFORMATION, DialogGraphics.ICON_SIZE);
			iconError = DialogGraphics.image(DialogGraphics.Semantic.ERROR, DialogGraphics.ICON_SIZE);
			
			stage = new Stage();
			stage.getIcons().add(iconInfo);
			
			FXMLLoader loader = new FXMLLoader(getClass().getResource("custom_notif.fxml"));
			loader.setRoot(this);
			loader.setController(this);
			
			loader.load();
			
			Scene scene = new Scene(loader.getRoot(), 400, 150);
			scene.getStylesheets().add(stylesheet);
			stage.setScene(scene);

			stage.initModality(Modality.APPLICATION_MODAL);
			stage.initOwner(owner);
			stage.setResizable(false);
			stage.setAlwaysOnTop(true);
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}
	
	public void show(String msg) {
		stage.setTitle("Notification");
		
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		double x = (screenBounds.getWidth()/2) - (stage.getScene().getWidth()*0.5);
		double y = (screenBounds.getHeight()/2) - (stage.getScene().getHeight()*0.75);
		stage.setX(x);
		stage.setY(y);
		
		stage.getIcons().clear();
		stage.getIcons().add(iconInfo);
		ivIcon.setImage(iconInfo);
		
		lblMsg.setText(msg);
		btnOk.requestFocus();
		stage.showAndWait();
	}
	
	public void showError(String msg) {
		stage.setTitle("Error");
		
		Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
		double x = (screenBounds.getWidth()/2) - (stage.getScene().getWidth()*0.5);
		double y = (screenBounds.getHeight()/2) - (stage.getScene().getHeight()*0.75);
		stage.setX(x);
		stage.setY(y);
		
		stage.getIcons().clear();
		stage.getIcons().add(iconError);
		ivIcon.setImage(iconError);
		
		lblMsg.setText(msg);
		btnOk.requestFocus();
		stage.showAndWait();
	}
	
	@FXML
	private void ok() {
		stage.hide();
	}
	
	public void setOwner(Window owner) {
		stage.initOwner(owner);
	}
}