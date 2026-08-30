package com.asdasfa.jbs2bg;
	
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import com.asdasfa.jbs2bg.workbench.WorkbenchGeometry;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
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
	public final String workbenchStyle = getClass().getResource("workbench.css").toExternalForm();
	
	public Stage primaryStage;
	public MainController mainController;
	
	public final Data data = new Data();
	public final ProjectPresentation projectPresentation;
	
	public final Settings.InitializationResult settingsInitialization;
	final WorkbenchProjectFlow workbenchProjectFlow;
	private final ExecutorService jobWorker;
	private final JobCoordinator jobCoordinator;
	
	/**
	 * Initializes the owned Settings pair, authoritative ProjectSession, application-wide job
	 * coordinator, and sole Workbench Project flow. The legacy read model remains unmounted for
	 * later feature cutovers.
	 */
	public Main() {
		settingsInitialization = Settings.initialize(Path.of("."));
		ProjectSession projectSession = ProjectSessions.create();
		jobWorker = Executors.newSingleThreadExecutor(
				Thread.ofPlatform().name("bs2bg-job-worker").factory());
		jobCoordinator = new JobCoordinator(jobWorker, Platform::runLater, Clock.systemUTC(),
				Main::scheduleCancellationStatus,
				failure -> Logger.getLogger(Main.class.getName()).log(Level.WARNING,
						"A Workbench job callback failed", failure));
		workbenchProjectFlow = new WorkbenchProjectFlow(APPLICATION_NAME, projectSession, jobCoordinator);
		projectPresentation = new ProjectPresentation(APPLICATION_NAME, workbenchProjectFlow.frame().snapshot());
	}

	/**
	 * Schedules prolonged-cancellation feedback on the JavaFX clock without creating another application worker.
	 *
	 * @param delay elapsed cancellation duration before the update
	 * @param action attempt-scoped coordinator callback
	 * @return cancellation handle safe to invoke from either application thread
	 */
	private static JobCoordinator.ScheduledAction scheduleCancellationStatus(Duration delay, Runnable action) {
		AtomicReference<PauseTransition> timerReference = new AtomicReference<>();
		AtomicBoolean cancelled = new AtomicBoolean();
		Runnable createTimer = () -> {
			if (cancelled.get())
				return;
			PauseTransition timer = new PauseTransition(javafx.util.Duration.millis(delay.toMillis()));
			timer.setOnFinished(event -> action.run());
			timerReference.set(timer);
			timer.play();
		};
		if (Platform.isFxApplicationThread())
			createTimer.run();
		else
			Platform.runLater(createTimer);
		return () -> {
			cancelled.set(true);
			PauseTransition timer = timerReference.get();
			if (timer == null)
				return;
			if (Platform.isFxApplicationThread())
				timer.stop();
			else
				Platform.runLater(timer::stop);
		};
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
			Scene scene = new Scene(root, 1300, 800);
			scene.getStylesheets().add(workbenchStyle);
			primaryStage.setScene(scene);
			primaryStage.setResizable(true);
			WorkbenchController controller = loader.getController();
			controller.attach(workbenchProjectFlow, primaryStage);
			primaryStage.show();
			applyMeasuredClientMinimum(primaryStage, scene);
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("Could not load the Workbench root graph", exception);
		}
	}

	/**
	 * Enforces the accepted 800x600 logical client minimum from live Stage and Scene measurements, avoiding fixed
	 * decoration guesses that drift across Windows themes and DPI scales.
	 *
	 * @param stage shown Workbench window whose non-client inset is measurable
	 * @param scene Workbench client scene expressed in JavaFX logical pixels
	 */
	private static void applyMeasuredClientMinimum(Stage stage, Scene scene) {
		stage.setMinWidth(WorkbenchGeometry.minimumWindowWidth(stage.getWidth(), scene.getWidth()));
		stage.setMinHeight(WorkbenchGeometry.minimumWindowHeight(stage.getHeight(), scene.getHeight()));
	}
	
	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
	public void stop() {
		// The controller settles any active attempt before Stage closure, so close cannot abandon worker work.
		jobCoordinator.close();
	}
}
