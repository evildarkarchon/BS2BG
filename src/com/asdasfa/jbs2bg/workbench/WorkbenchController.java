package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.workbench.morphs.MorphsArea;
import com.asdasfa.jbs2bg.workbench.morphs.MorphsFeature;
import com.asdasfa.jbs2bg.workbench.npcdatabase.NpcDatabaseFeature;
import com.asdasfa.jbs2bg.workbench.npcdatabase.NpcDatabaseArea;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesArea;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesFeature;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;
import com.asdasfa.jbs2bg.workbench.output.OutputDrawer;
import com.asdasfa.jbs2bg.workbench.output.OutputDrawerParts;
import com.asdasfa.jbs2bg.workbench.output.OutputFeature;
import com.asdasfa.jbs2bg.workbench.settings.SettingsArea;
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;
import com.asdasfa.jbs2bg.workbench.settings.SettingsJobRunner;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * JavaFX adapter for the Workbench root graph; Project and navigation state remain JavaFX-independent.
 */
public final class WorkbenchController {
    private final WorkbenchNavigation navigation = new WorkbenchNavigation();
    private final WorkbenchFeedback feedback = new WorkbenchFeedback(Clock.systemUTC());
    @FXML
    private BorderPane workbenchRoot;
    @FXML
    private ToggleButton templatesAreaButton;
    @FXML
    private ToggleButton morphsAreaButton;
    @FXML
    private ToggleButton npcDatabaseAreaButton;
    @FXML
    private ToggleButton outputAreaButton;
    @FXML
    private ToggleButton settingsAreaButton;
    @FXML
    private MenuItem saveProjectMenuItem;
    @FXML
    private MenuItem newProjectMenuItem;
    @FXML
    private MenuItem openProjectMenuItem;
    @FXML
    private MenuItem saveAsProjectMenuItem;
    @FXML
    private MenuItem exitMenuItem;
    @FXML
    private Label areaTitle;
    // The Area header row; the shell places the Output drawer's Generate button into it.
    @FXML
    private HBox areaHeader;
    @FXML
    private Label projectStatusText;
    @FXML
    private ComboBox<WorkbenchAppearance.ThemeChoice> themeChoice;
    @FXML
    private Label appearanceStateText;
    @FXML
    private Label motionStateText;
    @FXML
    private Label statusText;
    @FXML
    private HBox infoBar;
    @FXML
    private StackPane infoBarIconHost;
    @FXML
    private Label infoBarCue;
    @FXML
    private Label infoBarMessage;
    @FXML
    private Button dismissInfoBarButton;
    @FXML
    private StackPane statusIconHost;
    @FXML
    private Button cancelOperationButton;
    @FXML
    private ProgressBar operationProgress;
    @FXML
    private TextArea diagnosticsText;
    @FXML
    private ListView<WorkbenchFeedback.ActivityRecord> activityList;
    @FXML
    private VBox activitySection;
    @FXML
    private HBox statusBar;
    @FXML
    private Button retryActivityButton;
    @FXML
    private StackPane contentStack;
    @FXML
    private HBox areaPanes;
    @FXML
    private VBox primaryPane;
    @FXML
    private StackPane editorPane;
    @FXML
    private StackPane paneHost;
    @FXML
    private VBox inspectorPane;
    @FXML
    private StackPane overlayLayer;
    @FXML
    private Button showPrimaryOverlayButton;
    @FXML
    private Button showInspectorOverlayButton;
    private WorkbenchNavigation.Frame navigationFrame = navigation.currentFrame();
    private WorkbenchProjectFlow projectFlow;
    private TemplatesFeature templatesFeature;
    private MorphsFeature morphsFeature;
    private NpcDatabaseFeature npcDatabaseFeature;
    private SettingsFeature settingsFeature;
    // Admits, runs, retries, and settles Settings persistence; the shell keeps only the token-keyed close continuation.
    private SettingsJobRunner settingsJobs;
    // The one Settings store from the attach environment; features read it, only Settings effects publish to it.
    private Settings settings;
    private OutputFeature outputFeature;
    private Stage stage;
    private WorkbenchPlatform platform;
    // Built in attach once the platform and Stage exist; owns the job gate and the dialog round-trip.
    private DispatchPipeline pipeline;
    // The latest context the shell fanned out; rebuilt on each Project frame and each job frame. Area renders read
    // cross-Area inputs (job admission, live Settings) only from here, so each of their controls has one writer.
    private WorkbenchContext context;
    // Built in attach with the Stage; classifies the focus owner and realizes semantic focus targets.
    private WorkbenchFocusPolicy focusPolicy;
    private JavaFxWorkbenchAppearance appearanceAdapter;
    private boolean finalClose;
    private long renderedProjectSequence;
    private WorkbenchProjectFlow.Intent activeOperation;
    private JobCoordinator.Subscription jobSubscription;
    private long renderedTerminalAttemptId;
    private boolean closeAfterActiveJob;
    private SettingsCloseContinuation settingsCloseContinuation;
    // Set only while the close lifecycle dispatches its Save; the first Settings job admitted meanwhile carries it.
    private WorkbenchNavigation.FocusTarget pendingCloseSaveTarget;
    // Settings publication may beat a competing shutdown gate; derived refresh waits until admission resumes.
    private boolean settingsRefreshDeferred;
    // The four Area modules, each loaded from its own FXML in attach and keyed by the Area it renders.
    private final Map<WorkbenchNavigation.Area, WorkbenchArea> areaModules =
            new EnumMap<>(WorkbenchNavigation.Area.class);
    // Each loaded Area FXML's fx:id namespace; read only by tests that drive the whole loaded Workbench.
    private final Map<WorkbenchNavigation.Area, Map<String, Object>> areaNamespaces =
            new EnumMap<>(WorkbenchNavigation.Area.class);
    private TemplatesArea templatesArea;
    private MorphsArea morphsArea;
    private SettingsArea settingsArea;
    private NpcDatabaseArea npcDatabaseArea;
    // The Output drawer module, loaded from its own FXML; it is not an Area, so it has its own small interface.
    private OutputDrawer outputDrawer;
    // The Output drawer FXML's fx:id namespace; read only by tests that drive the whole loaded Workbench.
    private Map<String, Object> outputDrawerNamespace = Map.of();
    // Which feature presents the Project change being rendered with its own diagnostics; NONE outside such a change.
    private DiagnosticsOwner diagnosticsOwner = DiagnosticsOwner.NONE;
    private boolean resetTemplatesOnNextProjectFrame;
    private boolean resetMorphsOnNextProjectFrame;
    // The shared UIA list workarounds; the Templates and Morphs Areas are handed this one helper for their
    // refilling lists.
    private final UiaListHelper uiaLists = new UiaListHelper(WorkbenchFocusPolicy::ownsSceneFocus);
    // Satellite surfaces registered with the shell's appearance; built in attach, before the appearance first renders.
    private SatelliteRegistry satellites;

    /**
     * Pairs one decorative vector with the text label and exposes its keyboard cue as help text.
     */
    private static void configureSemanticIcon(ToggleButton button, IconKey key, String shortcut) {
        button.setGraphic(SemanticIcons.create(key, true));
        button.setAccessibleHelp("Semantic icon: " + key.accessibleName() + ". Keyboard shortcut: " + shortcut + ".");
    }

    /**
     * Returns the one stable Activity name and terminal sentence fragment for a Project command.
     */
    private static OperationDescription operationDescription(WorkbenchProjectFlow.Intent intent) {
        if (intent == null)
            return new OperationDescription("Project operation", "Project operation completed");
        return switch (intent) {
            case NEW -> new OperationDescription("New Project", "New Project created");
            case OPEN -> new OperationDescription("Open Project", "Project opened");
            case SAVE, SAVE_AS -> new OperationDescription("Save Project", "Project saved");
            case IMPORT_BODYSLIDE -> new OperationDescription("Import BodySlide Presets",
                    "BodySlide presets imported");
            case CLOSE -> new OperationDescription("Close Project", "Project closed");
        };
    }

    /**
     * Pluralizes the stable diagnostic count used by InfoBar, Activity, and status projections.
     */
    private static String diagnosticSummary(long count) {
        return count + (count == 1 ? " diagnostic" : " diagnostics");
    }

    /**
     * Builds the stable role/name identity used to locate one Activity record without visible-order assumptions.
     */
    private static String activityText(WorkbenchFeedback.ActivityRecord activity) {
        return activity.cue() + " — " + activity.operation() + " — "
                + activity.disposition().displayText() + ": " + activity.message();
    }

    /** Formats durable operation details and any attempt linkage for assistive inspection. */
    private static String activityHelp(WorkbenchFeedback.ActivityRecord activity) {
        String detailsText = activity.details().map(value -> ". Details: " + value).orElse("");
        if (activity.jobDetails().isEmpty())
            return "Timestamp: " + activity.occurredAt() + detailsText;
        WorkbenchFeedback.JobDetails details = activity.jobDetails().orElseThrow();
        return "Timestamp: " + activity.occurredAt()
                + detailsText
                + ". Attempt: " + details.attemptId()
                + details.retryOf().map(value -> ". Retry of attempt: " + value).orElse("")
                + ". Sources: " + (details.sources().isEmpty() ? "none" : String.join(", ", details.sources()))
                + ". Destinations: "
                + (details.destinations().isEmpty() ? "none" : String.join(", ", details.destinations()))
                + details.capturedBasis().map(value -> ". Captured basis: " + value).orElse("")
                + ". Effects committed: "
                + (details.effectsCommitted().isEmpty() ? "none" : String.join(", ", details.effectsCommitted()))
                + ". Diagnostics: "
                + (details.diagnosticCodes().isEmpty() ? "none" : String.join(", ", details.diagnosticCodes()))
                + ". Retry offered at completion: " + details.retryAvailable() + ".";
    }

    /**
     * Derives a concise non-color Project lifecycle summary.
     */
    private static String projectStatus(WorkbenchProjectFlow.Frame frame) {
        if (frame.snapshot().isDirty())
            return "Unsaved changes";
        return frame.snapshot().getFileIdentity().isPresent() ? "Saved Project" : "Untitled Project";
    }

    /**
     * Creates the conventional Control accelerator for one File command key.
     */
    private static KeyCodeCombination shortcut(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.CONTROL_DOWN);
    }

    /**
     * Attaches the loaded JavaFX graph to the real JavaFX platform. The environment carries the exact Settings startup
     * result so recovery and failures become visible Workbench evidence rather than being stranded in the
     * composition root.
     *
     * @param flow        authoritative Workbench Project flow
     * @param ownerStage  application window that owns effects and focus
     * @param environment the one Settings store, its startup result, preferences directory, and migration policy
     * @throws NullPointerException when an argument is null
     * @throws IllegalStateException when this controller is already attached
     */
    public void attach(WorkbenchProjectFlow flow, Stage ownerStage, WorkbenchEnvironment environment) {
        attach(flow, ownerStage, new JavaFxWorkbenchPlatform(), environment);
    }

    /**
     * Attaches one platform adapter on the JavaFX Application Thread. The controller owns the Stage handlers until
     * the flow publishes its final close effect; it cannot be attached again. The theme choice is loaded from and
     * saved to the environment's preferences directory.
     *
     * @param flow            authoritative window-scoped Project flow
     * @param ownerStage      Stage whose title, close request, choosers, and dialogs are owned by this controller
     * @param platformAdapter native-effect adapter retained until the Stage is hidden
     * @param environment     the one Settings store, its startup result, preferences directory, and migration policy
     * @throws NullPointerException  when an argument is null
     * @throws IllegalStateException when this controller is already attached
     */
    void attach(WorkbenchProjectFlow flow, Stage ownerStage, WorkbenchPlatform platformAdapter,
                WorkbenchEnvironment environment) {
        if (projectFlow != null)
            throw new IllegalStateException("WorkbenchController is already attached");
        Objects.requireNonNull(environment, "environment");
        WorkbenchAppearanceStore themeStore = new WorkbenchAppearanceStore(environment.preferencesDirectory());
        WorkbenchAppearance.ThemeChoice initialChoice;
        try {
            initialChoice = themeStore.load();
        } catch (IOException exception) {
            // A damaged or unreadable optional preference must not prevent the Workbench from starting safely.
            initialChoice = WorkbenchAppearance.ThemeChoice.SYSTEM;
        }
        projectFlow = Objects.requireNonNull(flow, "flow");
        settings = environment.settings();
        templatesFeature = new TemplatesFeature(projectFlow, settings::snapshot, Clock.systemUTC());
        morphsFeature = new MorphsFeature(projectFlow, Clock.systemUTC());
        npcDatabaseFeature = new NpcDatabaseFeature(projectFlow, Clock.systemUTC());
        settingsFeature = new SettingsFeature(settings, environment.settingsStartup(),
                environment.preferencesDirectory(), environment.migrationPolicy());
        settingsJobs = new SettingsJobRunner(settingsFeature, settings, projectFlow.jobs(),
                new SettingsJobRunner.Listener() {
                    /**
                     * Lets the Settings Area render the settled job (refresh, then chain any follow-up), and only
                     * then continues any close waiting on it.
                     */
                    @Override
                    public void completed(SettingsFeature.Effect effect, SettingsFeature.Update update) {
                        settingsArea.completeJob(effect, update);
                        continueSettingsClose(effect, update);
                    }

                    /** Keeps a pending close attached to the retried Save. */
                    @Override
                    public void recaptured(SettingsFeature.Effect previous, SettingsFeature.Effect replacement) {
                        transferSettingsClose(previous, replacement);
                    }
                });
        outputFeature = new OutputFeature(projectFlow, () -> new OutputFeature.GenerationSettings(
                settings.snapshot(), settingsFeature.frame().omitRedundantSliders()));
        stage = Objects.requireNonNull(ownerStage, "ownerStage");
        platform = Objects.requireNonNull(platformAdapter, "platformAdapter");
        pipeline = new DispatchPipeline(feedback, new DispatchPipeline.DialogPort() {
            /** Completes a confirmation modal owned by this Workbench window. */
            @Override
            public WorkbenchFeedback.DialogAction confirm(WorkbenchFeedback.DialogSpec spec) {
                return platform.completeConfirmation(spec, stage);
            }

            /** Completes a failure modal owned by this Workbench window. */
            @Override
            public WorkbenchFeedback.DialogAction fail(WorkbenchFeedback.DialogSpec spec) {
                return platform.completeFailure(spec, stage);
            }
        }, this::renderFeedback, new DispatchPipeline.ProjectChrome() {
            /** @return the Project sequence the chrome last rendered */
            @Override
            public long renderedSequence() {
                return renderedProjectSequence;
            }

            /**
             * Records the feature-owned sequence first so render does not replay generic lifecycle feedback. The
             * shell arbitrates the change's diagnostics by the feature that published it: a Templates or Morphs
             * edit presents its own, so the other Area's projection of the same Project frame stays clear meanwhile.
             */
            @Override
            public void render(long sequence, WorkbenchFeature.Update<?> update) {
                renderedProjectSequence = sequence;
                DiagnosticsOwner previous = diagnosticsOwner;
                diagnosticsOwner = previous.and(DiagnosticsOwner.of(update));
                try {
                    WorkbenchController.this.render(projectFlow.frame());
                } finally {
                    diagnosticsOwner = previous;
                }
            }
        });
        // Each Area renders as it mounts in loadAreas, so it needs a context before the first Project or job frame.
        context = buildContext(projectFlow.frame(), false, false);
        focusPolicy = createFocusPolicy();
        // Read only when a satellite registers, which is after configureAppearance has built the adapter.
        satellites = new SatelliteRegistry(() -> appearanceAdapter.frame());
        NpcPortraitViewer portraits = new NpcPortraitViewer(environment.portraitDirectory(), stage, satellites,
                launcher -> focusPolicy.restoreAfterSatellite(launcher,
                        WorkbenchNavigation.Landmark.PRIMARY_CONTENT));
        // Every feature exists by now, so the Areas load next, before any configure step can render. The Morphs
        // and NPC Database Areas bind their own inspector thumbnails to the shared viewer.
        loadAreas(portraits);
        configureProjectCommands();
        configureNavigation();
        configureDrawerGeometry();
        configureFeedback();
        configureAppearance(initialChoice, themeStore, environment.appearancePreferences());
        configureSemanticIcons();
        stage.setOnCloseRequest(event -> {
            if (!finalClose) {
                event.consume();
                dispatch(WorkbenchProjectFlow.Intent.CLOSE);
            }
        });
        jobSubscription = projectFlow.jobs().observe(this::renderJobFrame);
        settingsArea.publishStartupEvidence();
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            satellites.dismissAll();
            appearanceAdapter.close();
            jobSubscription.close();
            outputDrawer.close();
        });
        renderedProjectSequence = projectFlow.frame().sequence();
        renderNavigation(navigationFrame);
        // The first Project frame fans out to every Area.
        render(projectFlow.frame());
        renderFeedback(feedback.frame());
        if (workbenchRoot.getWidth() > 0.0)
            applyNavigation(navigation.resize(workbenchRoot.getWidth(), currentSemanticFocus()));
        applyInitialFocusOnceShown();
    }

    /**
     * Focuses the active Area's primary content once the Workbench is in a showing window. attach happens before
     * Stage.show in production, so this is one of the two named focus deferrals (see "Production platform bans" in
     * docs/build/workbench-platform.md): a one-shot WINDOW_SHOWN handler rather than a runLater, so focus is applied
     * as the window appears and never races a later user focus move.
     */
    private void applyInitialFocusOnceShown() {
        if (stage.isShowing()) {
            // The Area panes were mounted into an already-showing scene, so their controls have no skins yet; until a
            // CSS pass creates the ScrollPane skins, each pane's content (and the control to focus) is not in the
            // scene. A stage shown after attach gets that pass from show() before WINDOW_SHOWN.
            workbenchRoot.applyCss();
            requestFocus(new WorkbenchNavigation.FocusTarget(
                    navigationFrame.activeArea(), WorkbenchNavigation.Landmark.PRIMARY_CONTENT));
            return;
        }
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, new EventHandler<>() {
            /** Applies initial focus for the first show only, then detaches itself. */
            @Override
            public void handle(WindowEvent event) {
                stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, this);
                requestFocus(new WorkbenchNavigation.FocusTarget(
                        navigationFrame.activeArea(), WorkbenchNavigation.Landmark.PRIMARY_CONTENT));
            }
        });
    }

    /**
     * Connects File menu commands and their stable keyboard accelerators to Project intents.
     */
    private void configureProjectCommands() {
        newProjectMenuItem.setAccelerator(shortcut(KeyCode.N));
        openProjectMenuItem.setAccelerator(shortcut(KeyCode.O));
        saveProjectMenuItem.setAccelerator(shortcut(KeyCode.S));
        saveAsProjectMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.S,
                KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN));
        newProjectMenuItem.setOnAction(event -> dispatch(WorkbenchProjectFlow.Intent.NEW));
        openProjectMenuItem.setOnAction(event -> dispatch(WorkbenchProjectFlow.Intent.OPEN));
        saveProjectMenuItem.setOnAction(event -> dispatch(WorkbenchProjectFlow.Intent.SAVE));
        saveAsProjectMenuItem.setOnAction(event -> dispatch(WorkbenchProjectFlow.Intent.SAVE_AS));
        exitMenuItem.setOnAction(event -> dispatch(WorkbenchProjectFlow.Intent.CLOSE));
    }

    /**
     * Returns to the Morphs launcher and selects the last added identity when it is still visible.
     * Project and feature frames are already committed before navigation realizes the focus effect.
     */
    private void returnToMorphs(Optional<NpcMorphAssignmentIdentity> assignment) {
        morphsArea.revealAssignment(assignment);
        applyNavigation(navigation.backToMorphs());
    }

    /**
     * Loads every extracted Area from its own FXML with constructor-injected collaborators, mounts its three panes
     * into this shell's landmark containers, and renders it against the current context. The Areas mount in the order
     * their panes sat in the single Workbench FXML (Templates, Morphs, Settings, NPC Database), each appended after
     * the panes still declared in {@code workbench.fxml}, so the containers keep their child order. The Output
     * drawer, which is not an Area, loads here too with the same shell port.
     *
     * @param portraits the shared NPC portrait viewer, handed to the Morphs and NPC Database Areas for their
     *                  inspector thumbnails
     */
    private void loadAreas(NpcPortraitViewer portraits) {
        ShellPort shell = new AreaShell();
        AreaLoader.Loaded<TemplatesArea> templates = TemplatesArea.load(templatesFeature, pipeline, shell, uiaLists,
                settings::snapshot, stage.renderScaleYProperty());
        templatesArea = templates.area();
        mountArea(WorkbenchNavigation.Area.TEMPLATES, templates);
        AreaLoader.Loaded<MorphsArea> morphs = MorphsArea.load(morphsFeature, pipeline, shell, portraits,
                uiaLists, satellites, stage, stage.renderScaleYProperty());
        morphsArea = morphs.area();
        mountArea(WorkbenchNavigation.Area.MORPHS, morphs);
        // The one registration inside an Area pane: Add NPCs keeps its distinct launcher token, so Back to Morphs
        // can return to it.
        focusPolicy.landmark(morphsArea.focusNode(WorkbenchNavigation.Landmark.NPC_DATABASE_LAUNCHER),
                WorkbenchNavigation.Landmark.NPC_DATABASE_LAUNCHER);
        AreaLoader.Loaded<SettingsArea> settings = SettingsArea.load(settingsFeature, pipeline, shell);
        settingsArea = settings.area();
        mountArea(WorkbenchNavigation.Area.SETTINGS, settings);
        AreaLoader.Loaded<NpcDatabaseArea> npcDatabase = NpcDatabaseArea.load(npcDatabaseFeature, projectFlow.jobs(),
                pipeline, platform, shell, portraits, satellites, stage);
        npcDatabaseArea = npcDatabase.area();
        mountArea(WorkbenchNavigation.Area.NPC_DATABASE, npcDatabase);
        mountOutputDrawer(OutputDrawer.load(outputFeature, pipeline, platform, shell, stage));
    }

    /**
     * Places the Output drawer's two parts where they sat in the single Workbench FXML: the Generate button in the
     * Area header just before the Project status, and the drawer last in the content stack, over the Area surface.
     * The drawer becomes the Output landmark and renders the current context at once.
     *
     * @param loaded the loaded drawer and its namespace
     */
    private void mountOutputDrawer(OutputDrawer.Loaded loaded) {
        outputDrawer = loaded.drawer();
        outputDrawerNamespace = loaded.namespace();
        OutputDrawerParts parts = outputDrawer.parts();
        insertBefore(areaHeader, parts.getGenerateButton(), projectStatusText);
        contentStack.getChildren().add(parts.getDrawer());
        focusPolicy.landmark(parts.getDrawer(), WorkbenchNavigation.Landmark.OUTPUT);
        outputDrawer.render(context);
    }

    /**
     * Registers one loaded Area and appends each of its panes to the matching landmark container, so Areas mounted in
     * FXML order keep the containers' child order. The Area renders the current context at once, as each Area's
     * configure step did before it had its own FXML.
     *
     * @param area   the Area the module renders
     * @param loaded the loaded module and its namespace
     */
    private void mountArea(WorkbenchNavigation.Area area, AreaLoader.Loaded<? extends WorkbenchArea> loaded) {
        WorkbenchArea module = loaded.area();
        AreaPanes panes = module.panes();
        primaryPane.getChildren().add(panes.getPrimary());
        editorPane.getChildren().add(panes.getEditor());
        inspectorPane.getChildren().add(panes.getInspector());
        areaModules.put(area, module);
        areaNamespaces.put(area, loaded.namespace());
        module.render(context);
    }

    /** Inserts one pane into a landmark container directly before an existing child. */
    private static void insertBefore(Pane container, Node pane, Node before) {
        int index = container.getChildren().indexOf(before);
        if (index < 0)
            throw new IllegalStateException("Landmark container does not hold " + before.getId());
        container.getChildren().add(index, pane);
    }

    /**
     * Exposes one Area FXML's namespace to tests that look up Area controls through the whole loaded Workbench.
     *
     * @param area the Area
     * @return the Area FXML's read-only {@code fx:id} namespace, or an empty map before the shell is attached
     */
    Map<String, Object> areaNamespace(WorkbenchNavigation.Area area) {
        return areaNamespaces.getOrDefault(area, Map.of());
    }

    /**
     * Exposes the Output drawer FXML's namespace to tests that look up its controls through the whole loaded
     * Workbench.
     *
     * @return the drawer FXML's read-only {@code fx:id} namespace, or an empty map before the shell is attached
     */
    Map<String, Object> outputDrawerNamespace() {
        return outputDrawerNamespace;
    }

    /**
     * @param area the Area to look up
     * @return the Area's module; attach loads all four before any key, navigation or focus request can ask
     */
    private WorkbenchArea areaModule(WorkbenchNavigation.Area area) {
        return areaModules.get(area);
    }

    /**
     * The shell side of the {@link ShellPort}: Areas and the Output drawer reach shell-owned work (feedback, the
     * Settings job runner, the close continuation, navigation) and other Areas' refreshes only through these requests.
     */
    private final class AreaShell implements ShellPort {
        /** Applies an Area-worded publication to the one feedback model and renders the frame it returns. */
        @Override
        public void publishFeedback(Function<WorkbenchFeedback, WorkbenchFeedback.Frame> publication) {
            renderFeedback(publication.apply(feedback));
        }

        /**
         * Admits one Settings worker effect through the runner, attaching the close continuation first when the
         * close lifecycle is dispatching its Save, so even an inline completion finds it. A refused admission drops
         * that continuation again.
         */
        @Override
        public boolean submitSettingsEffect(SettingsFeature.Effect effect) {
            if (pendingCloseSaveTarget != null) {
                settingsCloseContinuation = new SettingsCloseContinuation(effect.token(), pendingCloseSaveTarget);
                pendingCloseSaveTarget = null;
            }
            boolean admitted = settingsJobs.submit(effect);
            if (!admitted && settingsCloseContinuation != null
                    && settingsCloseContinuation.effectToken() == effect.token())
                settingsCloseContinuation = null;
            return admitted;
        }

        /** Re-renders Output against its refreshed generation settings. */
        @Override
        public void generationSettingsChanged() {
            outputDrawer.refreshGenerationSettings();
        }

        /**
         * Refreshes every Settings-dependent feature after a publication, or defers the refresh while a shutdown
         * holds admission; a preference-only publication refreshes Output alone.
         */
        @Override
        public void settingsPublished(boolean pair) {
            if (projectFlow.jobs().frame().shutdownRequested())
                settingsRefreshDeferred = true;
            else if (!pair)
                outputDrawer.refreshGenerationSettings();
            else
                refreshPublishedSettings();
        }

        /** Reveals Output through navigation; the automatic reveal carries no focus effect. */
        @Override
        public void revealOutput() {
            applyNavigation(navigation.revealOutput());
        }

        /** Selects the promoted assignment in Morphs and navigates back to the Morphs launcher. */
        @Override
        public void returnToMorphs(Optional<NpcMorphAssignmentIdentity> assignment) {
            WorkbenchController.this.returnToMorphs(Objects.requireNonNull(assignment, "assignment"));
        }

        /**
         * Re-renders the Project chrome and the other Areas after an Area published a Project snapshot itself, with
         * neither Templates nor Morphs projecting the change's diagnostics.
         */
        @Override
        public void projectChanged(WorkbenchFeature.Update<?> update, boolean ownsDiagnostics) {
            Objects.requireNonNull(update, "update");
            // NPC Database owns these diagnostics and task wording. Suppress the generic lifecycle feedback while
            // Project chrome and other Areas reconcile the final snapshot after promotion has published.
            diagnosticsOwner = DiagnosticsOwner.ANOTHER_AREA;
            try {
                WorkbenchProjectFlow.Frame projectFrame = projectFlow.frame();
                renderedProjectSequence = projectFrame.sequence();
                render(projectFrame, ownsDiagnostics ? List.of() : projectFrame.diagnostics());
            } finally {
                diagnosticsOwner = DiagnosticsOwner.NONE;
            }
        }

        /** Navigates to the NPC Database, keeping the launcher as its Back to Morphs return target. */
        @Override
        public void openNpcDatabase(WorkbenchNavigation.FocusTarget returnTarget) {
            applyNavigation(navigation.navigate(WorkbenchNavigation.Destination.NPC_DATABASE,
                    Objects.requireNonNull(returnTarget, "returnTarget")));
        }

        /** Opens the primary-content overlay in narrow mode, returning to the current focus when it closes. */
        @Override
        public void revealPrimaryContent() {
            if (navigationFrame.narrowMode())
                applyNavigation(navigation.openPrimaryContent(currentSemanticFocus()));
        }

        /** Runs the BodySlide import through the Project lifecycle, as the File commands do. */
        @Override
        public void importBodySlidePresets() {
            dispatch(WorkbenchProjectFlow.Intent.IMPORT_BODYSLIDE);
        }

        /** Classifies the current focus owner through the shell's focus policy. */
        @Override
        public WorkbenchNavigation.FocusTarget currentFocus() {
            return currentSemanticFocus();
        }

        /** Requests the target through the shell's focus policy, with its Area-default fallback. */
        @Override
        public void requestFocus(WorkbenchNavigation.FocusTarget target) {
            WorkbenchController.this.requestFocus(Objects.requireNonNull(target, "target"));
        }

        /** Restores focus after a satellite hid through the shell's focus policy. */
        @Override
        public void restoreFocusAfterSatellite(Node launcher, WorkbenchNavigation.Landmark fallback) {
            focusPolicy.restoreAfterSatellite(launcher, fallback);
        }
    }

    /**
     * Continues a confirmed Close only after the Save it waits on succeeded; a failed or cancelled Save keeps the
     * continuation so an explicit retry can still close. Touches only the feature update and semantic focus.
     *
     * @param effect the worker effect that settled
     * @param update the feature update after completion or cancellation
     */
    private void continueSettingsClose(SettingsFeature.Effect effect, SettingsFeature.Update update) {
        SettingsCloseContinuation close = settingsCloseContinuation != null
                && settingsCloseContinuation.effectToken() == effect.token() ? settingsCloseContinuation : null;
        if (close != null) {
            if (update.accepted() && update.frame().outcome() == SettingsFeature.OutcomeKind.SAVED) {
                settingsCloseContinuation = null;
                requestFocus(close.returnTarget());
                dispatchProject(WorkbenchProjectFlow.Intent.CLOSE);
            }
        }
    }

    /**
     * Moves a pending close onto the effect that a Settings retry recaptured, keeping the same user intent and
     * focus target.
     *
     * @param previous    the failed effect being retried
     * @param replacement the recaptured effect that runs instead
     */
    private void transferSettingsClose(SettingsFeature.Effect previous, SettingsFeature.Effect replacement) {
        if (settingsCloseContinuation != null && settingsCloseContinuation.effectToken() == previous.token())
            settingsCloseContinuation = settingsCloseContinuation.forEffect(replacement.token());
    }

    /** Reprojects every Settings-dependent feature after the live pair is published. */
    private void refreshPublishedSettings() {
        projectFlow.refreshSettings();
        WorkbenchProjectFlow.Frame projectFrame = projectFlow.frame();
        // Settings-driven derived choices are not a second user Project command and must not create generic Activity.
        renderedProjectSequence = projectFrame.sequence();
        render(projectFrame);
        templatesFeature.refreshSettings();
        templatesArea.render(context);
        outputDrawer.refreshGenerationSettings();
    }

    /**
     * Connects rail gestures, accepted keyboard commands, and responsive width changes to typed navigation.
     */
    private void configureNavigation() {
        ToggleGroup areas = new ToggleGroup();
        for (ToggleButton button : new ToggleButton[]{
                templatesAreaButton, morphsAreaButton, npcDatabaseAreaButton, settingsAreaButton}) {
            button.setToggleGroup(areas);
        }
        templatesAreaButton.setOnAction(event -> navigate(WorkbenchNavigation.Destination.TEMPLATES));
        morphsAreaButton.setOnAction(event -> navigate(WorkbenchNavigation.Destination.MORPHS));
        npcDatabaseAreaButton.setOnAction(event -> navigate(WorkbenchNavigation.Destination.NPC_DATABASE));
        outputAreaButton.setOnAction(event -> navigate(WorkbenchNavigation.Destination.OUTPUT));
        settingsAreaButton.setOnAction(event -> navigate(WorkbenchNavigation.Destination.SETTINGS));
        showPrimaryOverlayButton.setOnAction(event -> applyNavigation(navigation.openPrimaryContent(
                new WorkbenchNavigation.FocusTarget(navigationFrame.activeArea(),
                        WorkbenchNavigation.Landmark.PRIMARY_LAUNCHER))));
        showInspectorOverlayButton.setOnAction(event -> applyNavigation(navigation.openInspector(
                new WorkbenchNavigation.FocusTarget(navigationFrame.activeArea(),
                        WorkbenchNavigation.Landmark.INSPECTOR_LAUNCHER))));
        workbenchRoot.addEventFilter(KeyEvent.KEY_PRESSED, this::handleNavigationKey);
        workbenchRoot.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            if (newWidth.doubleValue() > 0.0)
                applyNavigation(navigation.resize(newWidth.doubleValue(), currentSemanticFocus()));
        });
        Rectangle overlayClip = new Rectangle();
        overlayClip.widthProperty().bind(paneHost.widthProperty());
        overlayClip.heightProperty().bind(paneHost.heightProperty());
        // Oversized narrow panes must never paint across the Area header or InfoBar above paneHost.
        overlayLayer.setClip(overlayClip);
    }

    /**
     * Clips the drawer to Workbench content and lets the drawer fit its height range to the content height; the
     * drawer maps its own Slider value to its height.
     */
    private void configureDrawerGeometry() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(contentStack.widthProperty());
        clip.heightProperty().bind(contentStack.heightProperty());
        contentStack.setClip(clip);
        contentStack.heightProperty().addListener((observable, oldHeight, newHeight) ->
                outputDrawer.fitContentHeight(newHeight.doubleValue()));
    }

    /**
     * Connects nonmodal dismissal while leaving the durable Activity record and terminal status intact.
     */
    private void configureFeedback() {
        activityList.setCellFactory(list -> new ActivityCell());
        activityList.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) ->
                updateActivityRetry(selected));
        dismissInfoBarButton.setOnAction(event -> renderFeedback(feedback.dismissInfoBar()));
        retryActivityButton.setOnAction(event -> retrySelectedActivity());
        cancelOperationButton.setOnAction(event -> projectFlow.jobs().requestCancel());
        cancelOperationButton.setDisable(true);
        cancelOperationButton.setAccessibleHelp("No cancellable operation is currently active.");
        operationProgress.setProgress(0.0);
        operationProgress.setManaged(false);
        operationProgress.setVisible(false);
    }

    /** Enables Activity Retry only while its factory is still retained and coordinator admission is open. */
    private void updateActivityRetry(WorkbenchFeedback.ActivityRecord selected) {
        boolean available = selected != null
                && selected.jobDetails().stream().anyMatch(details -> projectFlow.jobs().isRetryAvailable(
                        new JobCoordinator.AttemptId(details.attemptId())))
                && !projectFlow.jobs().frame().active()
                && !projectFlow.jobs().frame().shutdownRequested();
        retryActivityButton.setDisable(!available);
        retryActivityButton.setAccessibleHelp(available
                ? "Starts a new attempt linked to attempt "
                + selected.jobDetails().orElseThrow().attemptId() + " with freshly captured inputs."
                : "Select a retryable failed Activity record while no operation is active.");
    }

    /** Requests a coordinator-owned retry and reports an ordinary dynamic admission rejection through status. */
    private void retrySelectedActivity() {
        WorkbenchFeedback.ActivityRecord selected = activityList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.jobDetails().isEmpty())
            return;
        JobCoordinator.Admission admission = projectFlow.jobs().retry(new JobCoordinator.AttemptId(
                selected.jobDetails().orElseThrow().attemptId()));
        if (!admission.admitted()) {
            String reason = admission.activeOperation().orElse("Retry is not available");
            retryActivityButton.setAccessibleHelp(reason);
            renderFeedback(feedback.publishStatus(new WorkbenchFeedback.Notification(
                    "Retry " + selected.operation(), WorkbenchFeedback.Severity.INFORMATION,
                    reason, WorkbenchFeedback.Disposition.CANCELLED)));
        }
    }

    /**
     * Applies System/Light/Dark immediately while submitting its profile write to the application worker. The
     * operating-system appearance comes only from the environment's preferences source, never from the live
     * JavaFX platform preferences (production-ban rule 3).
     *
     * @param initialChoice persisted theme choice, or System when none could be loaded
     * @param themeStore    profile theme store whose save job the worker runs
     * @param preferences   operating-system appearance-preferences source from the environment
     */
    private void configureAppearance(WorkbenchAppearance.ThemeChoice initialChoice,
                                     WorkbenchAppearanceStore themeStore, AppearancePreferencesSource preferences) {
        Objects.requireNonNull(themeStore, "themeStore");
        WorkbenchAppearance appearance = new WorkbenchAppearance(
                Objects.requireNonNull(initialChoice, "initialChoice"), preferences.current());
        appearanceAdapter = new JavaFxWorkbenchAppearance(workbenchRoot, appearance, preferences,
                this::renderAppearance);
        themeChoice.getItems().setAll(WorkbenchAppearance.ThemeChoice.values());
        themeChoice.setValue(initialChoice);
        themeChoice.setOnAction(event -> {
            WorkbenchAppearance.ThemeChoice selected = themeChoice.getValue();
            if (selected == null)
                return;
            appearanceAdapter.selectTheme(selected);
            JobCoordinator.Admission admission = projectFlow.jobs().submit(themeStore.saveSubmission(selected));
            if (!admission.admitted())
                renderFeedback(feedback.publishStatus(new WorkbenchFeedback.Notification(
                        "Theme preference", WorkbenchFeedback.Severity.WARNING,
                        "The selected theme is active but could not be queued for saving.",
                        WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES)));
        });
        appearanceAdapter.start();
    }

    /**
     * Adds the selected bundled-vector implementation to every text-labelled Workbench rail action.
     */
    private void configureSemanticIcons() {
        configureSemanticIcon(templatesAreaButton, IconKey.TEMPLATES, "Ctrl+1");
        configureSemanticIcon(morphsAreaButton, IconKey.MORPHS, "Ctrl+2");
        configureSemanticIcon(npcDatabaseAreaButton, IconKey.NPC_DATABASE, "Ctrl+3");
        configureSemanticIcon(outputAreaButton, IconKey.OUTPUT, "Ctrl+4 or Ctrl+Backtick");
        configureSemanticIcon(settingsAreaButton, IconKey.SETTINGS, "Ctrl+5");
    }

    /**
     * Renders effective theme and reduced-motion state as explicit non-color text.
     */
    private void renderAppearance(WorkbenchAppearance.Frame frame) {
        satellites.retheme(frame);
        String theme = switch (frame.effectiveTheme()) {
            case LIGHT -> "Light theme";
            case DARK -> "Dark theme";
            case HIGH_CONTRAST -> "High Contrast theme";
        };
        appearanceStateText.setText(theme);
        appearanceStateText.setAccessibleText("Effective theme: " + theme);
        String motion = frame.reducedMotion() ? "Reduced motion" : "Standard motion";
        motionStateText.setText(motion);
        motionStateText.setAccessibleText("Motion preference: " + motion);
        workbenchRoot.setAccessibleHelp("Ctrl+1 through Ctrl+5 navigate; Ctrl+4 or Ctrl+Backtick toggles Output; "
                + "F6 cycles landmarks; F7 opens the inspector; Escape dismisses the innermost surface. "
                + theme + "; " + motion + ".");
    }

    /**
     * Translates one accepted key gesture into a typed navigation transition.
     */
    private void handleNavigationKey(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.G) {
            outputDrawer.dispatch(new OutputFeature.Generate());
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.E) {
            outputDrawer.dispatch(new OutputFeature.Export());
            event.consume();
            return;
        }
        WorkbenchArea activeModule = areaModule(navigationFrame.activeArea());
        // The active Area's own keys (Templates' F2 and Escape while renaming) win over navigation, so an Escape
        // cancels an open rename before it would dismiss the overlay or drawer around it.
        if (activeModule.handleKey(event)) {
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.K) {
            activeModule.focusSearch();
            event.consume();
            return;
        }
        WorkbenchNavigation.Transition transition = null;
        if (event.isControlDown()) {
            transition = switch (event.getCode()) {
                case DIGIT1 -> navigation.navigate(WorkbenchNavigation.Destination.TEMPLATES,
                        currentSemanticFocus());
                case DIGIT2 -> navigation.navigate(WorkbenchNavigation.Destination.MORPHS,
                        currentSemanticFocus());
                case DIGIT3 -> navigation.navigate(WorkbenchNavigation.Destination.NPC_DATABASE,
                        currentSemanticFocus());
                case DIGIT4, BACK_QUOTE -> navigation.navigate(WorkbenchNavigation.Destination.OUTPUT,
                        currentSemanticFocus());
                case DIGIT5 -> navigation.navigate(WorkbenchNavigation.Destination.SETTINGS,
                        currentSemanticFocus());
                default -> null;
            };
        } else if (event.getCode() == KeyCode.F6) {
            transition = focusPolicy.cycle(navigation);
        } else if (event.getCode() == KeyCode.F7) {
            transition = navigation.openInspector(navigationFrame.narrowMode()
                    ? new WorkbenchNavigation.FocusTarget(navigationFrame.activeArea(),
                    WorkbenchNavigation.Landmark.INSPECTOR_LAUNCHER)
                    : currentSemanticFocus());
        } else if (event.getCode() == KeyCode.ESCAPE
                && (navigationFrame.overlay() != WorkbenchNavigation.Overlay.NONE
                || navigationFrame.outputDrawerVisible())) {
            transition = navigation.dismiss();
        }
        if (transition != null) {
            applyNavigation(transition);
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && activeModule.escape()) {
            event.consume();
        }
    }

    /**
     * Navigates from a rail gesture using the currently focused semantic launcher.
     */
    private void navigate(WorkbenchNavigation.Destination destination) {
        applyNavigation(navigation.navigate(destination, currentSemanticFocus()));
    }

    /**
     * Commits one navigation frame before realizing its optional tokenized focus effect.
     */
    private void applyNavigation(WorkbenchNavigation.Transition transition) {
        WorkbenchArea leftModule = navigationFrame.activeArea() != transition.frame().activeArea()
                ? areaModule(navigationFrame.activeArea()) : null;
        navigationFrame = transition.frame();
        if (leftModule != null)
            leftModule.leave();
        renderNavigation(navigationFrame);
        if (navigationFrame.overlay() == WorkbenchNavigation.Overlay.INSPECTOR)
            areaModule(navigationFrame.activeArea()).resetInspectorScroll();
        transition.focusTarget().ifPresent(this::requestFocus);
    }

    /**
     * Renders active Area, responsive panes, and Output visibility from one immutable navigation frame.
     */
    private void renderNavigation(WorkbenchNavigation.Frame frame) {
        templatesAreaButton.setSelected(frame.activeArea() == WorkbenchNavigation.Area.TEMPLATES);
        morphsAreaButton.setSelected(frame.activeArea() == WorkbenchNavigation.Area.MORPHS);
        npcDatabaseAreaButton.setSelected(frame.activeArea() == WorkbenchNavigation.Area.NPC_DATABASE);
        settingsAreaButton.setSelected(frame.activeArea() == WorkbenchNavigation.Area.SETTINGS);
        outputAreaButton.setSelected(frame.outputDrawerVisible());

        String area = frame.activeArea().displayName();
        areaModules.forEach((moduleArea, module) -> showAreaPanes(module.panes(), moduleArea == frame.activeArea()));
        areaTitle.setText(area);
        areaTitle.setAccessibleText(area + " Area");
        primaryPane.setAccessibleText(area + " primary content");
        editorPane.setAccessibleText(area + " editor");
        inspectorPane.setAccessibleText(area + " inspector");
        showPrimaryOverlayButton.setAccessibleText("Open " + area + " list");
        showInspectorOverlayButton.setAccessibleText("Open " + area + " inspector");
        showPrimaryOverlayButton.setManaged(frame.narrowMode());
        showPrimaryOverlayButton.setVisible(frame.narrowMode());
        showInspectorOverlayButton.setManaged(frame.narrowMode());
        showInspectorOverlayButton.setVisible(frame.narrowMode());

        renderResponsivePanes(frame);
        outputDrawer.reveal(frame.outputDrawerVisible());
    }

    /** Shows or hides one Area module's three panes together; a hidden pane also leaves its container's layout. */
    private static void showAreaPanes(AreaPanes panes, boolean shown) {
        for (Node pane : List.of(panes.getPrimary(), panes.getEditor(), panes.getInspector())) {
            pane.setManaged(shown);
            pane.setVisible(shown);
        }
    }

    /**
     * Reparents the real side panes into one overlay so narrow mode never creates a second feature route.
     */
    private void renderResponsivePanes(WorkbenchNavigation.Frame frame) {
        areaPanes.getChildren().clear();
        overlayLayer.getChildren().clear();
        if (frame.narrowMode()) {
            areaPanes.getChildren().add(editorPane);
            HBox.setHgrow(editorPane, javafx.scene.layout.Priority.ALWAYS);
            if (frame.overlay() == WorkbenchNavigation.Overlay.PRIMARY_CONTENT) {
                overlayLayer.getChildren().add(primaryPane);
                StackPane.setAlignment(primaryPane, Pos.CENTER_LEFT);
            } else if (frame.overlay() == WorkbenchNavigation.Overlay.INSPECTOR) {
                overlayLayer.getChildren().add(inspectorPane);
                StackPane.setAlignment(inspectorPane, Pos.CENTER_RIGHT);
            }
        } else {
            areaPanes.getChildren().addAll(primaryPane, editorPane, inspectorPane);
            HBox.setHgrow(editorPane, javafx.scene.layout.Priority.ALWAYS);
        }
        boolean overlayVisible = !overlayLayer.getChildren().isEmpty();
        overlayLayer.setManaged(overlayVisible);
        overlayLayer.setVisible(overlayVisible);
        overlayLayer.setMouseTransparent(!overlayVisible);
        primaryPane.setMaxWidth(frame.narrowMode() ? 320.0 : Region.USE_COMPUTED_SIZE);
        inspectorPane.setMaxWidth(frame.narrowMode() ? 320.0 : Region.USE_COMPUTED_SIZE);
    }

    /**
     * Builds the shell's focus policy and registers every landmark container from the FXML. The focus owner
     * classifies into its nearest registered ancestor; anything outside them (the rail, the Area header, the
     * Workbench notification and Project diagnostics) is on the rail. The launchers are registered as their own
     * landmarks. Morphs' Add NPCs launcher is the one registration inside an Area pane: it keeps its distinct
     * NPC_DATABASE_LAUNCHER token so Back to Morphs can return to it, and it registers when the shell mounts the
     * Morphs Area. The Output drawer comes from its own FXML, so it registers as the Output landmark when the shell
     * mounts it.
     */
    private WorkbenchFocusPolicy createFocusPolicy() {
        return new WorkbenchFocusPolicy(stage, () -> navigationFrame.activeArea(),
                new WorkbenchFocusPolicy.AreaFocus() {
                    /** Resolves shell landmarks and each Area's state-dependent landmark nodes. */
                    @Override
                    public Node focusNode(WorkbenchNavigation.FocusTarget target) {
                        return resolveFocusNode(target);
                    }

                    /** Each Area's first editable control. */
                    @Override
                    public Node defaultNode(WorkbenchNavigation.Area area) {
                        return areaDefaultFocusNode(area);
                    }
                })
                .landmark(primaryPane, WorkbenchNavigation.Landmark.PRIMARY_CONTENT)
                .landmark(editorPane, WorkbenchNavigation.Landmark.EDITOR)
                .landmark(inspectorPane, WorkbenchNavigation.Landmark.INSPECTOR)
                .landmark(activitySection, WorkbenchNavigation.Landmark.ACTIVITY)
                .landmark(statusBar, WorkbenchNavigation.Landmark.STATUS)
                .landmark(showPrimaryOverlayButton, WorkbenchNavigation.Landmark.PRIMARY_LAUNCHER)
                .landmark(showInspectorOverlayButton, WorkbenchNavigation.Landmark.INSPECTOR_LAUNCHER)
                .landmark(outputAreaButton, WorkbenchNavigation.Landmark.OUTPUT_LAUNCHER);
    }

    /** @return the shell's focus policy, for tests that pin the landmark classification */
    WorkbenchFocusPolicy focusPolicy() {
        return focusPolicy;
    }

    /**
     * Resolves current JavaFX focus back into a semantic target safe to retain across re-rendering.
     */
    private WorkbenchNavigation.FocusTarget currentSemanticFocus() {
        return focusPolicy.current();
    }

    /**
     * Applies one semantic focus effect, falling back to the active Area's first editable control.
     */
    private void requestFocus(WorkbenchNavigation.FocusTarget target) {
        focusPolicy.request(target);
    }

    /**
     * Returns an Area's last-resort focus node: its first editable control for the current Area state.
     */
    private Node areaDefaultFocusNode(WorkbenchNavigation.Area area) {
        return areaModule(area).defaultFocusNode();
    }

    /**
     * Maps a semantic target to the currently valid adapter node without retaining that node in navigation state.
     */
    private Node resolveFocusNode(WorkbenchNavigation.FocusTarget target) {
        return switch (target.landmark()) {
            case RAIL -> areaButton(target.area());
            case NPC_DATABASE_LAUNCHER -> morphsArea.focusNode(WorkbenchNavigation.Landmark.NPC_DATABASE_LAUNCHER);
            case PRIMARY_LAUNCHER -> showPrimaryOverlayButton;
            // The three Area landmarks resolve to the target Area's own state-dependent node.
            case PRIMARY_CONTENT, EDITOR, INSPECTOR -> areaModule(target.area()).focusNode(target.landmark());
            case INSPECTOR_LAUNCHER -> showInspectorOverlayButton;
            case OUTPUT_LAUNCHER -> outputAreaButton;
            case OUTPUT -> outputDrawer.focusNode(WorkbenchNavigation.Landmark.OUTPUT);
            case ACTIVITY -> activityList;
            case STATUS -> statusText;
        };
    }

    /**
     * Returns the rail button that semantically launches one full-page Area.
     */
    private ToggleButton areaButton(WorkbenchNavigation.Area area) {
        return switch (area) {
            case TEMPLATES -> templatesAreaButton;
            case MORPHS -> morphsAreaButton;
            case NPC_DATABASE -> npcDatabaseAreaButton;
            case SETTINGS -> settingsAreaButton;
        };
    }

    /**
     * Dispatches one Project intent, renders its frame, and completes any chained tokenized platform effects.
     */
    private void dispatch(WorkbenchProjectFlow.Intent intent) {
        if (intent == WorkbenchProjectFlow.Intent.CLOSE && projectFlow.jobs().frame().active()) {
            closeAfterActiveJob = true;
            projectFlow.jobs().requestShutdown();
            return;
        }
        if (intent == WorkbenchProjectFlow.Intent.CLOSE && settingsFeature.frame().dirty()) {
            confirmDirtySettingsClose();
            return;
        }
        dispatchProject(intent);
    }

    /** Dispatches directly to the Project flow after any Workbench-owned Settings close decision is complete. */
    private void dispatchProject(WorkbenchProjectFlow.Intent intent) {
        activeOperation = Objects.requireNonNull(intent, "intent");
        try {
            apply(projectFlow.request(intent));
        } finally {
            activeOperation = null;
        }
    }

    /** Offers Save, Discard, and Cancel for dirty Settings before entering the ordinary Project close flow. */
    private void confirmDirtySettingsClose() {
        WorkbenchNavigation.FocusTarget returnTarget = currentSemanticFocus();
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.unsavedClose(
                "Save Settings before closing?", "Closing now would discard unsaved Settings changes.");
        WorkbenchFeedback.DialogAction action = pipeline.confirm(spec);
        if (action == WorkbenchFeedback.DialogAction.CANCEL) {
            renderFeedback(feedback.publishActivity(new WorkbenchFeedback.Notification(
                    "Close Workbench", WorkbenchFeedback.Severity.INFORMATION, "Close Workbench cancelled.",
                    WorkbenchFeedback.Disposition.CANCELLED)));
            requestFocus(returnTarget);
            return;
        }
        if (action == WorkbenchFeedback.DialogAction.DISCARD) {
            dispatchProject(WorkbenchProjectFlow.Intent.CLOSE);
            return;
        }
        // The Save goes through the Settings Area like any other; the shell port attaches the close continuation to
        // the job it admits, before an inline completion could look for it.
        SettingsFeature.Update save;
        pendingCloseSaveTarget = returnTarget;
        try {
            save = settingsArea.dispatch(new SettingsFeature.Save());
        } finally {
            pendingCloseSaveTarget = null;
        }
        if (!save.accepted() || save.effect().isEmpty()) {
            requestFocus(returnTarget);
            return;
        }
    }

    /**
     * Completes synchronous platform effects in order while restoring their semantic launcher focus.
     */
    private void apply(WorkbenchProjectFlow.Update update) {
        WorkbenchProjectFlow.Update current = update;
        WorkbenchNavigation.FocusTarget returnTarget = null;
        while (true) {
            render(current.frame());
            if (current.effect().isEmpty()) {
                if (activeOperation == WorkbenchProjectFlow.Intent.CLOSE
                        && projectFlow.jobs().frame().shutdownRequested()
                        && !current.frame().closed()) {
                    closeAfterActiveJob = false;
                    projectFlow.jobs().resumeAfterShutdown();
                }
                if (returnTarget != null)
                    requestFocus(returnTarget);
                return;
            }
            WorkbenchProjectFlow.Effect effect = current.effect().orElseThrow();
            if (effect.kind() == WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW) {
                finalClose = true;
                platform.closeWindow(stage);
                return;
            }
            returnTarget = currentSemanticFocus();
            var dialogSpec = ProjectEffectDialogs.spec(effect.kind());
            // Choosers complete without a dialog; confirmations run the pipeline's pending-dialog round-trip.
            WorkbenchProjectFlow.Response response = dialogSpec.isPresent()
                    ? pipeline.roundTrip(dialogSpec.orElseThrow(), () -> platform.complete(effect, stage),
                    ProjectEffectDialogs::answer)
                    : platform.complete(effect, stage);
            if (response.kind() == WorkbenchProjectFlow.ResponseKind.CANCELLED)
                publishProjectCancellation(activeOperation);
            current = projectFlow.respond(effect.token(), response);
        }
    }

    /** Publishes an accepted chooser or confirmation cancellation without inventing a Project publication. */
    private void publishProjectCancellation(WorkbenchProjectFlow.Intent intent) {
        OperationDescription operation = operationDescription(intent);
        renderFeedback(feedback.publishActivity(new WorkbenchFeedback.Notification(
                operation.name(), WorkbenchFeedback.Severity.INFORMATION,
                operation.name() + " cancelled.", WorkbenchFeedback.Disposition.CANCELLED)));
    }

    /**
     * Renders title, lifecycle summary, and complete structured diagnostics from one immutable Project frame.
     */
    private void render(WorkbenchProjectFlow.Frame frame) {
        render(frame, frame.diagnostics());
    }

    /**
     * Renders the Project snapshot with diagnostics chosen by the operation that owns the current report, then builds
     * this frame's context (with its reset-on-Open decisions), lets the Project-consuming features accept the frame,
     * and fans the context out to every Area.
     */
    private void render(WorkbenchProjectFlow.Frame frame, List<ProjectDiagnostic> diagnostics) {
        stage.setTitle(frame.title());
        projectStatusText.setText(projectStatus(frame));
        diagnosticsText.setText(ProjectDiagnosticFormatter.format(diagnostics));
        boolean lifecyclePublication = frame.sequence() != renderedProjectSequence;
        boolean lifecycleReset = lifecyclePublication
                && (activeOperation == WorkbenchProjectFlow.Intent.NEW
                || activeOperation == WorkbenchProjectFlow.Intent.OPEN);
        // Built before the NPC Database reset below, because that dispatch already renders against this context; the
        // Area is handed it first, as it renders its own updates against the last context it received.
        context = buildContext(frame, resetTemplatesOnNextProjectFrame || lifecycleReset,
                resetMorphsOnNextProjectFrame || lifecycleReset);
        // Every feature and Area exists here: render runs only after attach has loaded them all (renderAreas below
        // writes to each of them unconditionally).
        if (lifecycleReset) {
            navigation.clearNpcDatabaseReturn();
            npcDatabaseArea.render(context);
            npcDatabaseArea.dispatch(new NpcDatabaseFeature.ClearAllSelections());
        }
        if (templatesFeature.frame().projectSequence() != frame.sequence()) {
            templatesFeature.acceptProjectFrame(frame, context.resetTemplates(),
                    diagnosticsOwner.keepsDiagnosticsFromTemplates() ? List.of() : frame.diagnostics());
        }
        if (morphsFeature.frame().projectSequence() != frame.sequence())
            morphsFeature.acceptProjectFrame(frame, context.resetMorphs(),
                    diagnosticsOwner.keepsDiagnosticsFromMorphs() ? List.of() : frame.diagnostics());
        outputFeature.acceptProjectFrame(frame, context.resetOutput());
        resetTemplatesOnNextProjectFrame = false;
        resetMorphsOnNextProjectFrame = false;
        renderAreas();
        if (!frame.closed() && frame.sequence() != renderedProjectSequence) {
            renderedProjectSequence = frame.sequence();
            publishProjectFeedback(frame);
        }
    }

    /**
     * Builds the context the Areas render against from the shell's current sources. The Project-mutation flag is the
     * pipeline's gate, which follows rendered job frames; the any-job flag reads the coordinator, as the Area renders
     * did before they took the context. Settings live availability changes only when a Save or Reload job completes,
     * and that job's terminal frame rebuilds the context.
     *
     * @param project        the Project frame the context describes
     * @param resetTemplates whether accepting {@code project} resets Templates browsing state
     * @param resetMorphs    whether accepting {@code project} resets Morphs browsing state
     * @return a new immutable context
     */
    private WorkbenchContext buildContext(WorkbenchProjectFlow.Frame project, boolean resetTemplates,
                                          boolean resetMorphs) {
        JobCoordinator.Frame jobs = projectFlow.jobs().frame();
        return new WorkbenchContext(project, resetTemplates, resetMorphs, pipeline.mutationsBlocked(),
                jobs.active() || jobs.shutdownRequested(), settingsFeature.frame().liveAvailable(),
                Optional.ofNullable(activeOperation));
    }

    /**
     * Fans the current context out to every Area: each renders its own feature's current frame against
     * {@link #context} and writes only its own controls. Renders are idempotent, so the repeat on every frame is safe.
     */
    private void renderAreas() {
        templatesArea.render(context);
        morphsArea.render(context);
        npcDatabaseArea.render(context);
        settingsArea.render(context);
        outputDrawer.render(context);
    }

    /**
     * Projects one coordinator frame into global admission, progress, cancellation, Activity, and shutdown UI, and
     * fans a fresh context out to every Area.
     */
    private void renderJobFrame(JobCoordinator.Frame frame) {
        Objects.requireNonNull(frame, "frame");
        boolean blocked = frame.active() || frame.shutdownRequested();
        pipeline.acceptJobFrame(frame);
        newProjectMenuItem.setDisable(blocked);
        openProjectMenuItem.setDisable(blocked);
        saveProjectMenuItem.setDisable(blocked);
        themeChoice.setDisable(blocked);
        saveAsProjectMenuItem.setDisable(blocked);
        // A job frame resets nothing: resets belong to the Project frame that consumes them, rendered below for an
        // Open Project terminal.
        context = buildContext(projectFlow.frame(), false, false);
        renderAreas();
        updateActivityRetry(activityList.getSelectionModel().getSelectedItem());

        Optional<JobCoordinator.Attempt> current = frame.attempt();
        if (frame.active() && current.isPresent()) {
            JobCoordinator.Attempt attempt = current.orElseThrow();
            renderActiveJob(attempt);
        } else {
            operationProgress.setManaged(false);
            operationProgress.setVisible(false);
            cancelOperationButton.setDisable(true);
            cancelOperationButton.setAccessibleHelp("No cancellable operation is currently active.");
        }

        if (current.isPresent() && current.orElseThrow().lifecycle().terminal()) {
            JobCoordinator.Attempt terminal = current.orElseThrow();
            WorkbenchProjectFlow.Frame projectFrame = projectFlow.frame();
            if (terminal.operation().name().equals("Open Project")
                    && projectFrame.sequence() != templatesFeature.frame().projectSequence()
                    && (terminal.lifecycle() == JobCoordinator.Lifecycle.COMPLETED
                    || terminal.lifecycle() == JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES))
                resetTemplatesOnNextProjectFrame = true;
            if (terminal.operation().name().equals("Open Project")
                    && projectFrame.sequence() != morphsFeature.frame().projectSequence()
                    && (terminal.lifecycle() == JobCoordinator.Lifecycle.COMPLETED
                    || terminal.lifecycle() == JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES))
                resetMorphsOnNextProjectFrame = true;
            if (terminal.operation().name().equals("Open Project")
                    && (terminal.lifecycle() == JobCoordinator.Lifecycle.COMPLETED
                    || terminal.lifecycle() == JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES)) {
                navigation.clearNpcDatabaseReturn();
                npcDatabaseArea.dispatch(new NpcDatabaseFeature.ClearAllSelections());
            }
            // The job terminal record is the authoritative feedback source for async work.
            renderedProjectSequence = projectFrame.sequence();
            render(projectFrame);
            if (terminal.id().value() > renderedTerminalAttemptId) {
                renderedTerminalAttemptId = terminal.id().value();
                renderTerminalJob(terminal);
                restoreSettingsCloseFocus(terminal);
            }
            if (projectFrame.closed() && activeOperation == null && !finalClose) {
                // Asynchronous close-after-save has no dispatch stack left to consume a CLOSE_WINDOW effect.
                finalClose = true;
                closeAfterActiveJob = false;
                platform.closeWindow(stage);
                return;
            }
        }

        if (closeAfterActiveJob && frame.shutdownReady()) {
            closeAfterActiveJob = false;
            // The settled job no longer needs the shutdown gate; a confirmed Save must be able to claim admission.
            if (!projectFlow.jobs().resumeAfterShutdown())
                throw new IllegalStateException("Shutdown-ready coordinator could not resume before Close");
            if (settingsRefreshDeferred) {
                settingsRefreshDeferred = false;
                refreshPublishedSettings();
            }
            dispatch(WorkbenchProjectFlow.Intent.CLOSE);
        }
    }

    /**
     * Renders truthful active phase progress and cancellation availability without inventing percentages.
     */
    private void renderActiveJob(JobCoordinator.Attempt attempt) {
        JobCoordinator.Progress progress = attempt.progress();
        operationProgress.setManaged(true);
        operationProgress.setVisible(true);
        String progressText;
        if (progress.completedUnits().isPresent()) {
            long completed = progress.completedUnits().orElseThrow();
            long total = progress.totalUnits().orElseThrow();
            operationProgress.setProgress((double) completed / (double) total);
            progressText = progress.percentage().orElseThrow() + "%";
        } else {
            operationProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            progressText = "indeterminate";
        }
        operationProgress.setAccessibleHelp(attempt.operation().name() + ", " + progress.phase() + ", "
                + progressText + ".");
        statusText.setText(attempt.operation().name() + " — " + progress.phase()
                + (progress.percentage().isPresent() ? " — " + progress.percentage().orElseThrow() + "%" : ""));
        statusText.setAccessibleHelp(attempt.operation().name() + ", " + progress.phase() + ", "
                + progressText + ".");
        cancelOperationButton.setDisable(!progress.cancellable());
        cancelOperationButton.setAccessibleHelp(progress.cancellable()
                ? "Cancel " + attempt.operation().name() + " at its next safe point."
                : attempt.lifecycle() == JobCoordinator.Lifecycle.CANCELLING
                ? "Cancellation was accepted; waiting for a safe point."
                : "The operation is finishing an atomic commit and can no longer be cancelled.");
    }

    /**
     * Publishes one terminal job consistently and opens a modal only for a failure requiring user action.
     */
    private void renderTerminalJob(JobCoordinator.Attempt attempt) {
        WorkbenchFeedback.Severity severity;
        WorkbenchFeedback.Disposition disposition;
        switch (attempt.lifecycle()) {
            case COMPLETED:
                severity = WorkbenchFeedback.Severity.SUCCESS;
                disposition = WorkbenchFeedback.Disposition.COMPLETED;
                break;
            case COMPLETED_WITH_ISSUES:
                severity = WorkbenchFeedback.Severity.WARNING;
                disposition = WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES;
                break;
            case CANCELLED:
                severity = WorkbenchFeedback.Severity.INFORMATION;
                disposition = WorkbenchFeedback.Disposition.CANCELLED;
                break;
            case FAILED:
                severity = WorkbenchFeedback.Severity.FAILURE;
                disposition = WorkbenchFeedback.Disposition.FAILED;
                break;
            case RUNNING, CANCELLING, FINISHING:
                throw new IllegalArgumentException("Only terminal attempts can publish terminal feedback");
            default:
                throw new IllegalStateException("Unsupported job lifecycle");
        }
        WorkbenchFeedback.JobDetails details = new WorkbenchFeedback.JobDetails(attempt.id().value(),
                attempt.retryOf().map(JobCoordinator.AttemptId::value), attempt.operation().sourceLabels(),
                attempt.operation().destinationLabels(), attempt.operation().capturedBasis(),
                attempt.effectsCommitted(), attempt.diagnostics().stream()
                .map(JobCoordinator.Diagnostic::code).toList(),
                attempt.retryAvailable());
        WorkbenchFeedback.Notification notification = new WorkbenchFeedback.Notification(
                attempt.operation().name(), severity, attempt.summary(), disposition);
        renderFeedback(feedback.publish(notification, Optional.of(details)));

        if (attempt.lifecycle() == JobCoordinator.Lifecycle.FAILED)
            showJobFailure(attempt);
    }

    /** Restores a retained Settings close target only after terminal rendering has re-enabled its controls. */
    private void restoreSettingsCloseFocus(JobCoordinator.Attempt attempt) {
        if (settingsCloseContinuation != null
                && attempt.operation().name().equals(SettingsJobRunner.SAVE_OPERATION)
                && (attempt.lifecycle() == JobCoordinator.Lifecycle.FAILED
                || attempt.lifecycle() == JobCoordinator.Lifecycle.CANCELLED))
            requestFocus(settingsCloseContinuation.returnTarget());
    }

    /**
     * Publishes and completes a typed failure dialog, translating Retry to a fresh linked attempt.
     */
    private void showJobFailure(JobCoordinator.Attempt attempt) {
        String details = attempt.diagnostics().isEmpty()
                ? attempt.summary()
                : String.join(System.lineSeparator(), attempt.diagnostics().stream()
                .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message()
                        + diagnostic.details().map(value -> System.lineSeparator() + value).orElse(""))
                .toList());
        boolean retryable = attempt.retryAvailable() && projectFlow.jobs().isRetryAvailable(attempt.id())
                && !projectFlow.jobs().frame().shutdownRequested();
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.failure(
                attempt.operation().name() + " failed", attempt.summary(), details, retryable);
        WorkbenchFeedback.DialogAction action = pipeline.fail(spec);
        if (action == WorkbenchFeedback.DialogAction.RETRY && retryable)
            projectFlow.jobs().retry(attempt.id());
    }

    /**
     * Publishes one Project outcome with validation/failure distinctions and a truthful terminal disposition.
     */
    private void publishProjectFeedback(WorkbenchProjectFlow.Frame frame) {
        OperationDescription operation = operationDescription(activeOperation);
        long diagnosticCount = frame.diagnostics().size();
        boolean failed = frame.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getSeverity() == DiagnosticSeverity.ERROR);
        WorkbenchFeedback.Severity severity;
        WorkbenchFeedback.Disposition disposition;
        String message;
        if (failed) {
            severity = WorkbenchFeedback.Severity.FAILURE;
            disposition = WorkbenchFeedback.Disposition.FAILED;
            message = operation.name() + " failed with " + diagnosticSummary(diagnosticCount) + ".";
        } else if (diagnosticCount > 0) {
            severity = WorkbenchFeedback.Severity.WARNING;
            disposition = WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES;
            message = operation.completedText() + " with " + diagnosticSummary(diagnosticCount) + ".";
        } else {
            severity = WorkbenchFeedback.Severity.SUCCESS;
            disposition = WorkbenchFeedback.Disposition.COMPLETED;
            message = operation.completedText() + ".";
        }
        renderFeedback(feedback.publish(new WorkbenchFeedback.Notification(
                operation.name(), severity, message, disposition)));
    }

    /**
     * Renders one committed feedback frame without requesting focus or replaying any platform effect.
     */
    private void renderFeedback(WorkbenchFeedback.Frame frame) {
        frame.infoBar().ifPresentOrElse(value -> {
            infoBarCue.setText(value.cue());
            infoBarMessage.setText(value.message());
            infoBar.setAccessibleRole(javafx.scene.AccessibleRole.PARENT);
            infoBar.setAccessibleText("Workbench notification");
            infoBar.setAccessibleHelp(value.cue() + ": " + value.message());
            SeverityStyle.apply(infoBar, value.severity());
            UiaListHelper.setKeyedChildIfChanged(infoBarIconHost.getChildren(), value.icon(),
                    icon -> SemanticIcons.create(icon, true));
            infoBar.setManaged(true);
            infoBar.setVisible(true);
        }, () -> {
            infoBar.setManaged(false);
            infoBar.setVisible(false);
        });
        UiaListHelper.setAllIfChanged(activityList.getItems(), frame.activities());
        WorkbenchFeedback.StatusProjection status = frame.status();
        boolean initialReady = frame.activities().isEmpty() && status.message().equals("Ready");
        statusText.setText(initialReady
                ? "Ready"
                : status.severity().cue() + " — " + status.dispositionText() + " — " + status.message());
        statusText.setAccessibleText("Workbench status");
        statusText.setAccessibleHelp(status.severity().cue() + ", "
                + status.dispositionText() + ", " + status.message());
        UiaListHelper.setKeyedChildIfChanged(statusIconHost.getChildren(), status.severity().icon(),
                icon -> SemanticIcons.create(icon, true));
        SeverityStyle.apply(statusText, status.severity());
    }

    /**
     * The shell's diagnostics arbitration: which feature presents the Project change being rendered with its own
     * diagnostics, so the shell keeps them out of the other Area's projection of the same Project frame. Templates and
     * Morphs are the two Areas that project a frame's diagnostics.
     */
    private enum DiagnosticsOwner {
        /** No change owns them: Templates and Morphs both project the frame's diagnostics. */
        NONE,
        /** A Templates edit presents them; Morphs projects none. */
        TEMPLATES,
        /** A Morphs edit presents them; Templates projects none. */
        MORPHS,
        /**
         * Another Area presents them (the NPC Database through {@code projectChanged}), or a Templates and a Morphs
         * change render nested; neither Templates nor Morphs projects them.
         */
        ANOTHER_AREA;

        /**
         * @param update the feature update whose Project change the chrome is rendering
         * @return the owner the update's feature makes: Templates or Morphs for their own updates, else none
         */
        static DiagnosticsOwner of(WorkbenchFeature.Update<?> update) {
            if (update instanceof TemplatesFeature.Update)
                return TEMPLATES;
            if (update instanceof MorphsFeature.Update)
                return MORPHS;
            return NONE;
        }

        /**
         * Combines an enclosing owner with a nested one, so a nested render keeps every suppression the enclosing
         * render already made.
         *
         * @param nested the owner of the nested render
         * @return the owner that suppresses what either one suppresses
         */
        DiagnosticsOwner and(DiagnosticsOwner nested) {
            if (this == NONE || this == nested)
                return nested;
            return nested == NONE ? this : ANOTHER_AREA;
        }

        /** @return whether the Templates projection of the frame must stay clear of its diagnostics */
        boolean keepsDiagnosticsFromTemplates() {
            return this == MORPHS || this == ANOTHER_AREA;
        }

        /** @return whether the Morphs projection of the frame must stay clear of its diagnostics */
        boolean keepsDiagnosticsFromMorphs() {
            return this == TEMPLATES || this == ANOTHER_AREA;
        }
    }

    /**
     * Typed user-facing operation wording kept together so Activity and terminal summaries cannot drift.
     */
    private record OperationDescription(String name, String completedText) {
        /** Rejects incomplete wording values used by user-facing feedback projections. */
        private OperationDescription {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(completedText, "completedText");
        }
    }

    /** Token-keyed close intent retained across one failed or cancelled Settings Save and its explicit retries. */
    private record SettingsCloseContinuation(long effectToken, WorkbenchNavigation.FocusTarget returnTarget) {
        /** Requires a positive effect token and stable semantic return target. */
        private SettingsCloseContinuation {
            if (effectToken <= 0)
                throw new IllegalArgumentException("effectToken must be positive");
            Objects.requireNonNull(returnTarget, "returnTarget");
        }

        /** Transfers the same user close intent and focus target to a freshly recaptured retry effect. */
        private SettingsCloseContinuation forEffect(long token) {
            return new SettingsCloseContinuation(token, returnTarget);
        }
    }

    /**
     * List cell that exposes durable Activity time separately from its stable semantic locator name.
     */
    private static final class ActivityCell extends ListCell<WorkbenchFeedback.ActivityRecord> {
        /**
         * Publishes text and timestamp accessibility state together whenever JavaFX reuses this cell.
         */
        @Override
        protected void updateItem(WorkbenchFeedback.ActivityRecord activity, boolean empty) {
            super.updateItem(activity, empty);
            if (empty || activity == null) {
                setText(null);
                setAccessibleText(null);
                setAccessibleHelp(null);
                return;
            }
            String text = activityText(activity);
            setText(text);
            setAccessibleText(text);
            setAccessibleHelp(activityHelp(activity));
        }
    }
}
