package com.asdasfa.jbs2bg;
	
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.asdasfa.jbs2bg.data.Data;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.presentation.ProjectPresentation;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.workbench.WorkbenchController;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** Composition root for the sole JavaFX Workbench application. */
public class Main extends Application {
	
	public static final String APPLICATION_NAME = "BS2BG Preview";

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
	public final ProjectPresentation projectPresentation;
	
	public final Settings.InitializationResult settingsInitialization;
	final WorkbenchProjectFlow workbenchProjectFlow;
	
	/**
	 * Initializes the owned Settings pair, the authoritative ProjectSession, and the sole
	 * Workbench Project flow. The legacy read model remains unmounted for later feature cutovers.
	 */
	public Main() {
		settingsInitialization = Settings.initialize(Path.of("."));
		ProjectSession projectSession = ProjectSessions.create();
		workbenchProjectFlow = new WorkbenchProjectFlow(APPLICATION_NAME, projectSession);
		projectPresentation = new ProjectPresentation(APPLICATION_NAME, workbenchProjectFlow.frame().snapshot());
	}
	
	/**
	 * Loads and attaches the sole Workbench scene on the JavaFX Application Thread.
	 *
	 * @param stage primary application Stage owned until the Workbench completes shutdown
	 * @throws IllegalStateException when the packaged Workbench graph cannot be loaded
	 */
	@Override
	public void start(Stage stage) {
		primaryStage = stage;
		primaryStage.getIcons().add(icon);
		setUserAgentStylesheet(STYLESHEET_MODENA);

		Level logLevel = Level.INFO;
		Logger rootLogger = LogManager.getLogManager().getLogger("");
		rootLogger.setLevel(logLevel);
		for (Handler handler : rootLogger.getHandlers())
			handler.setLevel(logLevel);

		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("workbench.fxml"));
			Parent root = loader.load();
			Scene scene = new Scene(root, 1100, 720);
			scene.getStylesheets().add(style);
			primaryStage.setScene(scene);
			primaryStage.setMinWidth(830);
			primaryStage.setMinHeight(640);
			primaryStage.setResizable(true);
			WorkbenchController controller = loader.getController();
			controller.attach(workbenchProjectFlow, primaryStage);
			primaryStage.show();
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("Could not load the Workbench root graph", exception);
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
	public void stop() {
		// The controller completes the tokenized close flow before closing the Stage.
	}
}
