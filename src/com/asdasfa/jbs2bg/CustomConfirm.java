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
public class CustomConfirm extends VBox {

    private final Stage stage;

    @FXML
    private Label lblHeader;
    @FXML
    private ImageView ivIcon;
    @FXML
    private Label lblContent;
    @FXML
    private Button btnOk;
    @FXML
    private Button btnCancel;

    public CustomConfirm(Main main) {
        this(main.style, main.primaryStage);
    }

    /**
     * Loads the confirmation graph as a custom FXML root owned by this control.
     *
     * @param stylesheet stylesheet URL applied to the dialog scene
     * @param owner      owning window, or null for an unowned dialog
     * @throws RuntimeException when the FXML graph cannot be loaded
     */
    // The custom-root FXML idiom hands `this` to the loader before construction completes; the
    // anonymous subclasses only override ok()/cancel(), which the loader never calls, so the
    // escape is deliberate.
    @SuppressWarnings("this-escape")
    public CustomConfirm(String stylesheet, Window owner) {
        try {
            Image icon = DialogGraphics.image(DialogGraphics.Semantic.CONFIRMATION, DialogGraphics.ICON_SIZE);

            stage = new Stage();
            stage.getIcons().add(icon);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("custom_confirm.fxml"));
            loader.setRoot(this);
            loader.setController(this);

            loader.load();

            Scene scene = new Scene(loader.getRoot(), 400, 220);
            scene.getStylesheets().add(stylesheet);
            stage.setScene(scene);

            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(owner);
            stage.setResizable(false);

            ivIcon.setImage(icon);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void show() {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double x = (screenBounds.getWidth() / 2) - (stage.getScene().getWidth() * 0.5);
        double y = (screenBounds.getHeight() / 2) - (stage.getScene().getHeight() * 0.75);
        stage.setX(x);
        stage.setY(y);
        btnCancel.requestFocus();
        stage.showAndWait();
    }

    @FXML
    private void doOk() {
        ok(); // This will happen stage.hide due to showAndWait
        stage.hide();
    }

    public void ok() {
    }

    @FXML
    private void doCancel() {
        cancel();
        stage.hide();
    }

    public void cancel() {
    }

    public void setTitle(String title) {
        stage.setTitle(title);
    }

    public void setHeaderText(String headerText) {
        this.lblHeader.setText(headerText);
    }

    public void setContentText(String contentText) {
        this.lblContent.setText(contentText);
    }

    public void setOkButtonText(String text) {
        btnOk.setText(text);
    }

    public void setCancelButtonText(String text) {
        btnCancel.setText(text);
    }

    public void setOwner(Window owner) {
        stage.initOwner(owner);
    }
}
