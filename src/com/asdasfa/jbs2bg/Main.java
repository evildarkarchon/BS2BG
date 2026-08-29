package com.asdasfa.jbs2bg;
	
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.asdasfa.jbs2bg.data.Data;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.presentation.ProjectPresentation;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

/**
 * 
 * @author Totiman
 */
public class Main extends Application {
	
	public String appName = "jBS2BG";
	
	/*
		v1.1.2 changes:
		- Table's search text by key press will now use the leftmost column instead of always by Name.
		- Clear templates text area when toggling redundant sliders.
	*/
	/**
	 * The one application version string (MAJOR.MIDDLE.MINOR). jpackage stamps it on the Windows app-image
	 * ({@code --app-version}, fed from the {@code bs2bg.app.version} pom property that WindowsAppImageGateTest
	 * checks against this constant), and the About dialog renders it.
	 */
	public static final String APP_VERSION = "1.1.2";
	
	public final double decorWidth = 30;
	public final double decorHeight = 40;
	
	public final Image icon = new Image(getClass().getResourceAsStream("/res/icon.png"));
	public final String style = getClass().getResource("dark.css").toExternalForm();
	
	public Stage primaryStage;
	public MainController mainController;
	
	public final Data data = new Data();
	public final ProjectSession projectSession;
	public final ProjectPresentation projectPresentation;
	
	public final Settings.InitializationResult settingsInitialization;
	
	/**
	 * Initializes the owned Settings pair plus the authoritative ProjectSession and renders
	 * its initial untitled snapshot into the JavaFX read model.
	 */
	public Main() {
		settingsInitialization = Settings.initialize(Path.of("."));
		projectSession = ProjectSessions.create();
		ProjectOutcome initialProject = projectSession.newProject();
		projectPresentation = new ProjectPresentation(appName, initialProject.getSnapshot());
	}
	
	@Override
	public void start(Stage stage) {
		primaryStage = stage;
		
		try {
			// Set icon
			primaryStage.getIcons().add(icon);

			setUserAgentStylesheet(STYLESHEET_MODENA);
			
			//LogManager.getLogManager().reset();
			Level logLevel = Level.INFO;
			Logger rootLogger = LogManager.getLogManager().getLogger("");
			rootLogger.setLevel(logLevel);
			for (Handler h : rootLogger.getHandlers()) {
				h.setLevel(logLevel);
			}

			double width = 900;
			double height = 600;
			
			// Initializables gets called here first
			FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
			Parent root = loader.load(); // This calls Controller.initialize
			
			Scene scene = new Scene(root, width, height);
			scene.getStylesheets().clear();
			scene.getStylesheets().add(style);
			primaryStage.setScene(scene);
			
			primaryStage.setMinWidth(width + decorWidth);
			primaryStage.setMinHeight(height + decorHeight);
			primaryStage.setResizable(true);
			primaryStage.setTitle(appName);

			// Give access to Main in the MainController
			mainController = loader.getController();
			mainController.postInitialize(this, primaryStage); // This calls Controller.postInitialize

			scene.setOnKeyReleased(new EventHandler<KeyEvent>() {
				@Override
				public void handle(KeyEvent e) {
					mainController.setOnKeyReleased(e.getCode());
				}
			});

			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
	public void stop() {
	}
}
