package com.asdasfa.jbs2bg.workbench;

import static com.asdasfa.jbs2bg.workbench.WorkbenchFocusPolicy.canRestoreFocus;
import static com.asdasfa.jbs2bg.workbench.WorkbenchFocusPolicy.ownsSceneFocus;

import java.io.IOException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.workbench.morphs.MorphsArea;
import com.asdasfa.jbs2bg.workbench.morphs.MorphsFeature;
import com.asdasfa.jbs2bg.workbench.npcdatabase.NpcDatabaseFeature;
import com.asdasfa.jbs2bg.workbench.npcdatabase.NpcDatabaseArea;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesFeature;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;
import com.asdasfa.jbs2bg.workbench.output.OutputDrawer;
import com.asdasfa.jbs2bg.workbench.output.OutputDrawerParts;
import com.asdasfa.jbs2bg.workbench.output.OutputFeature;
import com.asdasfa.jbs2bg.workbench.settings.SettingsArea;
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;
import com.asdasfa.jbs2bg.workbench.settings.SettingsJobRunner;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
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
    private static final double SLIDER_PRESET_CELL_HEIGHT = 28.0;
    private static final int MAX_VISIBLE_SLIDER_PRESET_ROWS = 8;

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
    private ScrollPane templatesPrimaryScroll;
    @FXML
    private VBox templatesPrimaryContent;
    @FXML
    private HBox templatesInfoBar;
    @FXML
    private Label templatesInfoBarCue;
    @FXML
    private Label templatesInfoBarMessage;
    @FXML
    private Button dismissTemplatesInfoBarButton;
    @FXML
    private TextField sliderPresetFilter;
    @FXML
    private ListView<SliderPresetSnapshot> sliderPresetList;
    @FXML
    private TextField sliderPresetNameInput;
    @FXML
    private Button createSliderPresetButton;
    @FXML
    private ComboBox<TemplatesFeature.SortOrder> sliderPresetSort;
    @FXML
    private Button duplicateSliderPresetButton;
    @FXML
    private Button removeSliderPresetButton;
    @FXML
    private Button clearSliderPresetsButton;
    @FXML
    private Button importBodySlideButton;
    @FXML
    private VBox templatesEditorContent;
    @FXML
    private Label templateEditorFocusTarget;
    @FXML
    private Label templateProfileText;
    @FXML
    private Label templateChoiceCountText;
    @FXML
    private ComboBox<TemplatesFeature.Profile> sliderPresetProfile;
    @FXML
    private VBox sliderChoiceRows;
    @FXML
    private VBox templatesInspectorContent;
    @FXML
    private ScrollPane templatesInspectorScroll;
    @FXML
    private Label templateSelectionText;
    @FXML
    private Button goToSetSlidersButton;
    @FXML
    private Button renameSliderPresetButton;
    @FXML
    private Button zeroAllSliderChoicesButton;
    @FXML
    private Button fiftyAllSliderChoicesButton;
    @FXML
    private Button hundredAllSliderChoicesButton;
    @FXML
    private Button zeroAllMinimumButton;
    @FXML
    private Button fiftyAllMinimumButton;
    @FXML
    private Button hundredAllMinimumButton;
    @FXML
    private Button zeroAllMaximumButton;
    @FXML
    private Button fiftyAllMaximumButton;
    @FXML
    private Button hundredAllMaximumButton;
    @FXML
    private javafx.scene.control.CheckBox gangAllCheck;
    @FXML
    private javafx.scene.control.CheckBox gangMinimumCheck;
    @FXML
    private javafx.scene.control.CheckBox gangMaximumCheck;
    @FXML
    private Slider gangAllSlider;
    @FXML
    private Slider gangMinimumSlider;
    @FXML
    private Slider gangMaximumSlider;
    @FXML
    private Label gangAllValue;
    @FXML
    private Label gangMinimumValue;
    @FXML
    private Label gangMaximumValue;
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
    // The Area modules loaded from their own FXML, keyed by the Area they render; the rest still live in this graph.
    private final Map<WorkbenchNavigation.Area, WorkbenchArea> areaModules =
            new EnumMap<>(WorkbenchNavigation.Area.class);
    // Each loaded Area FXML's fx:id namespace; read only by tests that drive the whole loaded Workbench.
    private final Map<WorkbenchNavigation.Area, Map<String, Object>> areaNamespaces =
            new EnumMap<>(WorkbenchNavigation.Area.class);
    private MorphsArea morphsArea;
    private SettingsArea settingsArea;
    private NpcDatabaseArea npcDatabaseArea;
    // The Output drawer module, loaded from its own FXML; it is not an Area, so it has its own small interface.
    private OutputDrawer outputDrawer;
    // The Output drawer FXML's fx:id namespace; read only by tests that drive the whole loaded Workbench.
    private Map<String, Object> outputDrawerNamespace = Map.of();
    private boolean renderingTemplates;
    private boolean templatesOwnProjectDiagnostics;
    private boolean morphsOwnProjectDiagnostics;
    private TextField activeRenameField;
    private SliderPresetCell activeRenameCell;
    private boolean resetTemplatesOnNextProjectFrame;
    private boolean resetMorphsOnNextProjectFrame;
    // The shared UIA list workarounds; each refilling list is bound once its owner pane has been injected. The
    // Morphs Area is handed this one helper for its three lists.
    private final UiaListHelper uiaLists = new UiaListHelper(WorkbenchFocusPolicy::ownsSceneFocus);
    private UiaListHelper.RefillingList<SliderPresetSnapshot> sliderPresetItems;
    // Satellite surfaces registered with the shell's appearance; built in attach, before the appearance first renders.
    private SatelliteRegistry satellites;
    private final Map<String, SliderChoiceRow> sliderChoiceRowsByName = new LinkedHashMap<>();

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
             * shell arbitrates the change's diagnostics by the feature that published it: a Morphs edit presents
             * its own, so the Templates projection of the same Project frame stays clear meanwhile.
             */
            @Override
            public void render(long sequence, WorkbenchFeature.Update<?> update) {
                renderedProjectSequence = sequence;
                boolean previous = morphsOwnProjectDiagnostics;
                morphsOwnProjectDiagnostics = previous || update instanceof MorphsFeature.Update;
                try {
                    WorkbenchController.this.render(projectFlow.frame());
                } finally {
                    morphsOwnProjectDiagnostics = previous;
                }
            }
        });
        // The configure* steps render their Areas, so they need a context before the first Project or job frame.
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
        configureTemplates();
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
     * Translates Templates controls into feature-specific typed intents and renders only committed immutable frames.
     */
    private void configureTemplates() {
        sliderPresetSort.getItems().setAll(TemplatesFeature.SortOrder.values());
        sliderPresetSort.setValue(templatesFeature.frame().sortOrder());
        sliderPresetProfile.getItems().setAll(TemplatesFeature.Profile.values());
        configureSliderPresetList();
        sliderPresetItems = uiaLists.refilling(() -> sliderPresetList, templatesPrimaryContent,
                "Templates primary content no longer owns the Slider Preset list",
                this::newSliderPresetList, this::adoptSliderPresetList);
        sliderPresetFilter.textProperty().addListener((observable, previous, current) -> {
            if (!renderingTemplates)
                dispatchTemplates(new TemplatesFeature.ChangeFilter(current));
        });
        sliderPresetSort.setOnAction(event -> {
            if (!renderingTemplates && sliderPresetSort.getValue() != null)
                dispatchTemplates(new TemplatesFeature.ChangeSort(sliderPresetSort.getValue()));
        });
        sliderPresetProfile.setOnAction(event -> {
            if (!renderingTemplates && sliderPresetProfile.getValue() != null
                    && templatesFeature.frame().editor().stream()
                    .anyMatch(editor -> editor.profile() != sliderPresetProfile.getValue()))
                dispatchTemplates(new TemplatesFeature.ChangeProfile(sliderPresetProfile.getValue()));
        });
        createSliderPresetButton.setOnAction(event ->
                dispatchTemplates(new TemplatesFeature.Create(sliderPresetNameInput.getText())));
        duplicateSliderPresetButton.setOnAction(event ->
                dispatchTemplates(new TemplatesFeature.Duplicate(sliderPresetNameInput.getText())));
        renameSliderPresetButton.setOnAction(event ->
                dispatchTemplates(new TemplatesFeature.BeginRename()));
        removeSliderPresetButton.setOnAction(event ->
                dispatchTemplates(new TemplatesFeature.RequestRemove()));
        clearSliderPresetsButton.setOnAction(event ->
                dispatchTemplates(new TemplatesFeature.RequestClearVisible()));
        dismissTemplatesInfoBarButton.setOnAction(event ->
                dispatchTemplates(new TemplatesFeature.DismissDiagnostics()));
        importBodySlideButton.setOnAction(event -> dispatch(WorkbenchProjectFlow.Intent.IMPORT_BODYSLIDE));
        configureGangControls();
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
     * @return the Area FXML's read-only {@code fx:id} namespace, or an empty map while the Area still lives in this
     *         controller's own graph
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
     * @return the Area's module, or null while the Area still lives in this controller's own graph
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
            templatesOwnProjectDiagnostics = true;
            morphsOwnProjectDiagnostics = true;
            try {
                WorkbenchProjectFlow.Frame projectFrame = projectFlow.frame();
                renderedProjectSequence = projectFrame.sequence();
                render(projectFrame, ownsDiagnostics ? List.of() : projectFrame.diagnostics());
            } finally {
                templatesOwnProjectDiagnostics = false;
                morphsOwnProjectDiagnostics = false;
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
        renderTemplates(templatesFeature.refreshSettings().frame());
        outputDrawer.refreshGenerationSettings();
    }

    /**
     * Connects every inspector bulk button, mutually exclusive gang check, and coalesced gang Slider gesture to the
     * same typed Templates intent seam.
     */
    private void configureGangControls() {
        configureBulkButton(zeroAllSliderChoicesButton, TemplatesFeature.GangMode.ALL, 0);
        configureBulkButton(fiftyAllSliderChoicesButton, TemplatesFeature.GangMode.ALL, 50);
        configureBulkButton(hundredAllSliderChoicesButton, TemplatesFeature.GangMode.ALL, 100);
        configureBulkButton(zeroAllMinimumButton, TemplatesFeature.GangMode.MINIMUM, 0);
        configureBulkButton(fiftyAllMinimumButton, TemplatesFeature.GangMode.MINIMUM, 50);
        configureBulkButton(hundredAllMinimumButton, TemplatesFeature.GangMode.MINIMUM, 100);
        configureBulkButton(zeroAllMaximumButton, TemplatesFeature.GangMode.MAXIMUM, 0);
        configureBulkButton(fiftyAllMaximumButton, TemplatesFeature.GangMode.MAXIMUM, 50);
        configureBulkButton(hundredAllMaximumButton, TemplatesFeature.GangMode.MAXIMUM, 100);
        gangAllCheck.setOnAction(event -> dispatchTemplates(new TemplatesFeature.ToggleGang(
                TemplatesFeature.GangMode.ALL, gangAllCheck.isSelected())));
        gangMinimumCheck.setOnAction(event -> dispatchTemplates(new TemplatesFeature.ToggleGang(
                TemplatesFeature.GangMode.MINIMUM, gangMinimumCheck.isSelected())));
        gangMaximumCheck.setOnAction(event -> dispatchTemplates(new TemplatesFeature.ToggleGang(
                TemplatesFeature.GangMode.MAXIMUM, gangMaximumCheck.isSelected())));
        configureGangSlider(gangAllSlider, gangAllValue, TemplatesFeature.GangMode.ALL);
        configureGangSlider(gangMinimumSlider, gangMinimumValue, TemplatesFeature.GangMode.MINIMUM);
        configureGangSlider(gangMaximumSlider, gangMaximumValue, TemplatesFeature.GangMode.MAXIMUM);
        goToSetSlidersButton.setOnAction(event -> firstSliderChoiceControl().ifPresent(Node::requestFocus));
    }

    /**
     * Binds one fixed-value bulk button to an atomic enabled-row edit.
     */
    private void configureBulkButton(Button button, TemplatesFeature.GangMode mode, int value) {
        button.setOnAction(event -> dispatchTemplates(new TemplatesFeature.ApplyBulkValue(mode, value)));
    }

    /**
     * Keeps pointer drags local until release while conventional keyboard Slider changes publish immediately.
     */
    private void configureGangSlider(Slider slider, Label valueLabel, TemplatesFeature.GangMode mode) {
        slider.valueProperty().addListener((observable, previous, current) -> {
            valueLabel.setText(current.intValue() + "%");
            if (!renderingTemplates && !slider.isValueChanging())
                dispatchTemplates(new TemplatesFeature.ApplyBulkValue(mode, current.intValue()));
        });
        slider.valueChangingProperty().addListener((observable, previous, changing) -> {
            if (!renderingTemplates && !changing.booleanValue())
                dispatchTemplates(new TemplatesFeature.ApplyBulkValue(mode, (int) slider.getValue()));
        });
    }

    /**
     * Configures the current Slider Preset ListView instance; the UIA empty/refill workaround may replace that node.
     */
    private void configureSliderPresetList() {
        CatalogHeight.bind(sliderPresetList, SLIDER_PRESET_CELL_HEIGHT, MAX_VISIBLE_SLIDER_PRESET_ROWS,
                stage.renderScaleYProperty());
        sliderPresetList.setEditable(true);
        sliderPresetList.setFixedCellSize(SLIDER_PRESET_CELL_HEIGHT);
        sliderPresetList.setCellFactory(list -> new SliderPresetCell());
        sliderPresetList.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (renderingTemplates)
                return;
            dispatchTemplates(selected == null
                    ? new TemplatesFeature.ClearSelection()
                    : new TemplatesFeature.Select(NameIdentity.of(selected.getName())));
        });
        sliderPresetList.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!(event.getTarget() instanceof TextInputControl) && event.getCharacter().length() == 1
                    && !event.isControlDown() && !event.isAltDown()) {
                dispatchTemplates(new TemplatesFeature.TypeAhead(event.getCharacter().charAt(0)));
                event.consume();
            }
        });
    }

    /**
     * Runs one Templates intent through the gated dispatch pipeline. Templates owns the Project diagnostics of its own
     * dispatch, so the Morphs projection of the same Project frame stays clear meanwhile.
     */
    private void dispatchTemplates(TemplatesFeature.Intent intent) {
        templatesOwnProjectDiagnostics = true;
        try {
            pipeline.dispatch(templatesFeature, DispatchPipeline.Callbacks.of(
                    update -> renderTemplates(update.frame()),
                    effect -> DispatchPipeline.Realization.confirm(WorkbenchFeedback.DialogSpec.destructiveAction(
                                    effect.title(), effect.message(), templatesDestructiveAction(effect)),
                            action -> templatesFeature.respond(effect.token(),
                                    action == templatesDestructiveAction(effect))),
                    this::reportTemplatesOutcome), Objects.requireNonNull(intent, "intent"));
        } finally {
            templatesOwnProjectDiagnostics = false;
        }
    }

    /** Returns the named destructive action a Templates confirmation offers beside Cancel. */
    private static WorkbenchFeedback.DialogAction templatesDestructiveAction(TemplatesFeature.Effect effect) {
        return effect.kind() == TemplatesFeature.EffectKind.CONFIRM_REMOVE
                ? WorkbenchFeedback.DialogAction.REMOVE
                : WorkbenchFeedback.DialogAction.CLEAR;
    }

    /**
     * Maps one settled Templates dispatch to feedback: a confirmed destructive edit is recorded first, then the
     * dispatched intent's own outcome is routed to its tier, and a finished rename returns focus to the list.
     */
    private void reportTemplatesOutcome(
            DispatchPipeline.Outcome<TemplatesFeature.Intent, TemplatesFeature.Update> outcome) {
        TemplatesFeature.Intent intent = outcome.intent();
        TemplatesFeature.Update update = outcome.update();
        if (outcome.answer().isPresent()) {
            TemplatesFeature.Effect effect = update.effect().orElseThrow();
            if (outcome.answer().orElseThrow() == templatesDestructiveAction(effect))
                publishTemplatesMutation(effect.kind() == TemplatesFeature.EffectKind.CONFIRM_REMOVE
                        ? "Remove Slider Preset" : "Clear visible Slider Presets", outcome.settled());
        }
        publishTemplatesOutcome(intent, update);
        if ((intent instanceof TemplatesFeature.CommitRename && update.accepted())
                || intent instanceof TemplatesFeature.CancelRename) {
            sliderPresetList.requestFocus();
        }
    }

    /**
     * Routes feature validation and mutation outcomes into inline InfoBar, Activity, and status projections.
     */
    private void publishTemplatesOutcome(TemplatesFeature.Intent intent, TemplatesFeature.Update update) {
        if (isSliderChoiceIntent(intent)) {
            publishSliderChoiceOutcome(intent, update);
            return;
        }
        // A retained preview failure describes current Settings, not validation of this Templates intent.
        List<ProjectDiagnostic> operationDiagnostics = update.frame().diagnostics().stream()
                .filter(diagnostic -> !ProjectOutputFormatter.SliderChoicePreviewException.CODE
                        .equals(diagnostic.getCode()))
                .toList();
        if (!operationDiagnostics.isEmpty()) {
            String message = ProjectDiagnosticFormatter.format(operationDiagnostics);
            renderFeedback(feedback.publishActivity(new WorkbenchFeedback.Notification(
                    "Templates validation", WorkbenchFeedback.Severity.VALIDATION, message,
                    WorkbenchFeedback.Disposition.FAILED)));
            if (intent instanceof TemplatesFeature.Create || intent instanceof TemplatesFeature.Duplicate) {
                sliderPresetNameInput.requestFocus();
            }
            return;
        }
        if (!update.accepted() || update.effect().isPresent())
            return;
        String operation = switch (intent) {
            case TemplatesFeature.Create ignored -> "Create Slider Preset";
            case TemplatesFeature.Duplicate ignored -> "Duplicate Slider Preset";
            case TemplatesFeature.CommitRename ignored -> "Rename Slider Preset";
            default -> null;
        };
        if (operation != null)
            publishTemplatesMutation(operation, update);
    }

    /**
     * Identifies in-place profile, row, and gang tasks whose feedback follows the accepted per-gesture/bulk tiers.
     */
    private static boolean isSliderChoiceIntent(TemplatesFeature.Intent intent) {
        return intent instanceof TemplatesFeature.ChangeProfile
                || intent instanceof TemplatesFeature.SetChoiceEnabled
                || intent instanceof TemplatesFeature.SetChoiceRange
                || intent instanceof TemplatesFeature.ApplyBulkValue
                || intent instanceof TemplatesFeature.ToggleGang;
    }

    /**
     * Projects the exact feature outcome without flooding Activity for individual row gestures or stealing focus.
     */
    private void publishSliderChoiceOutcome(TemplatesFeature.Intent intent, TemplatesFeature.Update update) {
        boolean bulk = intent instanceof TemplatesFeature.ApplyBulkValue
                || intent instanceof TemplatesFeature.ToggleGang;
        String operation = bulk ? "Edit Slider choices" : "Edit Slider choice";
        WorkbenchFeedback.Notification notification = switch (update.outcomeKind()) {
            case CHANGED -> new WorkbenchFeedback.Notification(operation, WorkbenchFeedback.Severity.SUCCESS,
                    operation + " changed.", WorkbenchFeedback.Disposition.COMPLETED);
            case UNCHANGED -> new WorkbenchFeedback.Notification(operation, WorkbenchFeedback.Severity.INFORMATION,
                    "No Slider choice values changed.", WorkbenchFeedback.Disposition.COMPLETED);
            case REJECTED -> new WorkbenchFeedback.Notification(operation, WorkbenchFeedback.Severity.VALIDATION,
                    "Slider choice validation rejected the edit.", WorkbenchFeedback.Disposition.FAILED);
            case FAILED -> new WorkbenchFeedback.Notification(operation, WorkbenchFeedback.Severity.FAILURE,
                    "Slider choice editing failed.", WorkbenchFeedback.Disposition.FAILED);
            case CANCELLED -> new WorkbenchFeedback.Notification(operation, WorkbenchFeedback.Severity.INFORMATION,
                    "Slider choice editing was cancelled.", WorkbenchFeedback.Disposition.CANCELLED);
            case NONE -> null;
        };
        if (notification == null)
            return;
        renderFeedback(bulk
                ? feedback.publishActivity(notification)
                : feedback.publishStatus(notification));
        if (update.outcomeKind() == TemplatesFeature.OutcomeKind.FAILED)
            showTemplatesFailure(update);
    }

    /**
     * Realizes an unexpected synchronous Templates failure as the accepted focus-restoring failure dialog.
     */
    private void showTemplatesFailure(TemplatesFeature.Update update) {
        Optional<SliderChoiceFocus> exactFocus = currentSliderChoiceFocus();
        Optional<TemplatesControlFocus> exactControlFocus = currentTemplatesControlFocus();
        WorkbenchNavigation.FocusTarget returnTarget = currentSemanticFocus();
        String details = ProjectDiagnosticFormatter.format(update.frame().diagnostics());
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.failure(
                "Slider choice editing failed", "The Slider Preset could not be edited.",
                details.isBlank() ? "No diagnostic details were provided." : details, false);
        pipeline.fail(spec);
        if (!restoreSliderChoiceFocus(exactFocus) && !restoreTemplatesControlFocus(exactControlFocus))
            requestFocus(returnTarget);
    }

    /**
     * Captures the focused Slider choice through semantic identities rather than retaining a JavaFX Node reference.
     */
    private Optional<SliderChoiceFocus> currentSliderChoiceFocus() {
        Node focusOwner = stage != null && stage.getScene() != null ? stage.getScene().getFocusOwner() : null;
        if (focusOwner == null)
            return Optional.empty();
        for (SliderChoiceRow row : sliderChoiceRowsByName.values()) {
            Optional<SliderChoiceRow.FocusControl> control = row.focusedControl(focusOwner);
            if (control.isPresent())
                return Optional.of(new SliderChoiceFocus(row.choiceName(), control.orElseThrow()));
        }
        return Optional.empty();
    }

    /**
     * Restores one still-valid logical Slider row control after a failure dialog without silently retargeting.
     */
    private boolean restoreSliderChoiceFocus(Optional<SliderChoiceFocus> requested) {
        if (requested.isEmpty())
            return false;
        SliderChoiceFocus focus = requested.orElseThrow();
        SliderChoiceRow row = sliderChoiceRowsByName.get(focus.choiceName().toLowerCase(Locale.ROOT));
        if (row == null)
            return false;
        Node control = row.control(focus.control());
        if (!control.isVisible() || control.getParent() == null)
            return false;
        control.requestFocus();
        return true;
    }

    /**
     * Captures profile, gang, and bulk-action focus through a stable semantic control family.
     */
    private Optional<TemplatesControlFocus> currentTemplatesControlFocus() {
        Node focusOwner = stage != null && stage.getScene() != null ? stage.getScene().getFocusOwner() : null;
        if (focusOwner == null)
            return Optional.empty();
        return templatesFocusNodes().entrySet().stream()
                .filter(entry -> entry.getValue() == focusOwner)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Restores a still-valid profile, gang, or bulk-action focus target after a synchronous failure dialog.
     */
    private boolean restoreTemplatesControlFocus(Optional<TemplatesControlFocus> requested) {
        if (requested.isEmpty())
            return false;
        Node control = templatesFocusNodes().get(requested.orElseThrow());
        if (control == null || !control.isVisible() || control.getParent() == null)
            return false;
        control.requestFocus();
        return true;
    }

    /**
     * Maps every non-row Templates editor/inspector focus identity to its current JavaFX adapter node.
     */
    private Map<TemplatesControlFocus, Node> templatesFocusNodes() {
        Map<TemplatesControlFocus, Node> controls = new java.util.EnumMap<>(TemplatesControlFocus.class);
        controls.put(TemplatesControlFocus.PROFILE, sliderPresetProfile);
        controls.put(TemplatesControlFocus.GO_TO_SET_SLIDERS, goToSetSlidersButton);
        controls.put(TemplatesControlFocus.RENAME, renameSliderPresetButton);
        controls.put(TemplatesControlFocus.ZERO_ALL, zeroAllSliderChoicesButton);
        controls.put(TemplatesControlFocus.FIFTY_ALL, fiftyAllSliderChoicesButton);
        controls.put(TemplatesControlFocus.HUNDRED_ALL, hundredAllSliderChoicesButton);
        controls.put(TemplatesControlFocus.ZERO_MINIMUM, zeroAllMinimumButton);
        controls.put(TemplatesControlFocus.FIFTY_MINIMUM, fiftyAllMinimumButton);
        controls.put(TemplatesControlFocus.HUNDRED_MINIMUM, hundredAllMinimumButton);
        controls.put(TemplatesControlFocus.ZERO_MAXIMUM, zeroAllMaximumButton);
        controls.put(TemplatesControlFocus.FIFTY_MAXIMUM, fiftyAllMaximumButton);
        controls.put(TemplatesControlFocus.HUNDRED_MAXIMUM, hundredAllMaximumButton);
        controls.put(TemplatesControlFocus.GANG_ALL_CHECK, gangAllCheck);
        controls.put(TemplatesControlFocus.GANG_MINIMUM_CHECK, gangMinimumCheck);
        controls.put(TemplatesControlFocus.GANG_MAXIMUM_CHECK, gangMaximumCheck);
        controls.put(TemplatesControlFocus.GANG_ALL_SLIDER, gangAllSlider);
        controls.put(TemplatesControlFocus.GANG_MINIMUM_SLIDER, gangMinimumSlider);
        controls.put(TemplatesControlFocus.GANG_MAXIMUM_SLIDER, gangMaximumSlider);
        return controls;
    }

    /**
     * Publishes one successful Templates mutation through the shared durable feedback path.
     */
    private void publishTemplatesMutation(String operation, TemplatesFeature.Update update) {
        if (!update.accepted())
            return;
        renderFeedback(feedback.publishActivity(new WorkbenchFeedback.Notification(operation,
                WorkbenchFeedback.Severity.SUCCESS, operation + " completed.",
                WorkbenchFeedback.Disposition.COMPLETED)));
    }

    /**
     * Renders one immutable Templates frame while suppressing control listeners from becoming a second command path.
     * Job admission and live Settings come only from the shell's context.
     */
    private void renderTemplates(TemplatesFeature.Frame frame) {
        renderingTemplates = true;
        try {
            sliderPresetFilter.setText(frame.filterText());
            sliderPresetSort.setValue(frame.sortOrder());
            sliderPresetItems.reconcile(frame.visiblePresets());
            reconcileSliderPresetSelection(frame.selection());
            boolean validationVisible = !frame.diagnostics().isEmpty();
            templatesInfoBar.setManaged(validationVisible);
            templatesInfoBar.setVisible(validationVisible);
            if (validationVisible) {
                String message = ProjectDiagnosticFormatter.format(frame.diagnostics());
                WorkbenchFeedback.Severity severity = frame.outcomeKind() == TemplatesFeature.OutcomeKind.FAILED
                        ? WorkbenchFeedback.Severity.FAILURE
                        : WorkbenchFeedback.Severity.VALIDATION;
                templatesInfoBarCue.setText(severity.cue());
                templatesInfoBarMessage.setText(message);
                templatesInfoBar.setAccessibleHelp(severity.cue() + ": " + message);
                SeverityStyle.apply(templatesInfoBar, severity);
            }
            boolean selected = frame.selection().isPresent();
            createSliderPresetButton.setDisable(context.projectMutationsBlocked());
            duplicateSliderPresetButton.setDisable(context.projectMutationsBlocked() || !selected);
            renameSliderPresetButton.setDisable(context.projectMutationsBlocked() || !selected);
            removeSliderPresetButton.setDisable(context.projectMutationsBlocked() || !selected);
            clearSliderPresetsButton.setDisable(context.projectMutationsBlocked() || frame.visiblePresets().isEmpty());
            // BodySlide import is a Project command that lives in the Templates pane; this is its only writer, and
            // it waits on any job and on live Settings so an import never consumes fallback Settings values.
            importBodySlideButton.setDisable(context.projectMutationsBlocked() || context.anyJobActive()
                    || !context.settingsLiveAvailable());
            Optional<SliderPresetSnapshot> selectedPreset = frame.selection().flatMap(identity ->
                    frame.visiblePresets().stream()
                            .filter(candidate -> NameIdentity.of(candidate.getName()).equals(identity))
                            .findFirst());
            selectedPreset.ifPresentOrElse(preset -> {
                templateEditorFocusTarget.setText(preset.getName());
                templateProfileText.setText(preset.isUunp() ? "UUNP profile" : "Standard profile");
                templateChoiceCountText.setText(preset.getSliderChoices().size()
                        + (preset.getSliderChoices().size() == 1 ? " slider choice" : " slider choices"));
                templateSelectionText.setText("Selected: " + preset.getName());
                renameSliderPresetButton.setAccessibleText("Rename Slider Preset " + preset.getName());
            }, () -> {
                templateEditorFocusTarget.setText("No Slider Preset selected");
                templateProfileText.setText("Select a Slider Preset to browse its profile.");
                templateChoiceCountText.setText("No slider choices");
                templateSelectionText.setText("No Slider Preset selected");
                renameSliderPresetButton.setAccessibleText("Rename selected Slider Preset");
            });
            reconcileSliderChoiceRows(frame.editor());
            synchronizeRenameEditor(frame.rename());
        } finally {
            renderingTemplates = false;
        }
    }

    /**
     * Reuses JavaFX row adapters by case-insensitive choice identity so edits preserve focus and the UIA subtree.
     */
    private void reconcileSliderChoiceRows(Optional<TemplatesFeature.EditorFrame> editor) {
        renderGangControls(editor);
        if (editor.isEmpty()) {
            sliderPresetProfile.setDisable(true);
            sliderPresetProfile.setValue(null);
            sliderChoiceRowsByName.clear();
            sliderChoiceRows.getChildren().clear();
            return;
        }
        TemplatesFeature.EditorFrame editorFrame = editor.orElseThrow();
        sliderPresetProfile.setDisable(context.projectMutationsBlocked());
        sliderPresetProfile.setValue(editorFrame.profile());
        Map<String, SliderChoiceRow> next = new LinkedHashMap<>();
        List<SliderChoiceRow> ordered = editorFrame.choices().stream().map(choice -> {
            String key = choice.name().toLowerCase(Locale.ROOT);
            SliderChoiceRow row = sliderChoiceRowsByName.get(key);
            if (row == null)
                row = new SliderChoiceRow(this::dispatchTemplates, settings::snapshot);
            row.render(editorFrame, choice, context.projectMutationsBlocked());
            next.put(key, row);
            return row;
        }).toList();
        sliderChoiceRowsByName.clear();
        sliderChoiceRowsByName.putAll(next);
        UiaListHelper.setAllIfChanged(sliderChoiceRows.getChildren(), ordered);
    }

    /**
     * Renders mutually exclusive gang state and disables every Project mutation when no preset is selected or a job
     * owns admission.
     */
    private void renderGangControls(Optional<TemplatesFeature.EditorFrame> editor) {
        boolean available = editor.isPresent() && !context.projectMutationsBlocked();
        TemplatesFeature.GangFrame gang = editor.map(TemplatesFeature.EditorFrame::gang).orElse(null);
        Optional<TemplatesFeature.GangMode> active = gang == null ? Optional.empty() : gang.activeMode();
        gangAllCheck.setSelected(active.equals(Optional.of(TemplatesFeature.GangMode.ALL)));
        gangMinimumCheck.setSelected(active.equals(Optional.of(TemplatesFeature.GangMode.MINIMUM)));
        gangMaximumCheck.setSelected(active.equals(Optional.of(TemplatesFeature.GangMode.MAXIMUM)));
        int all = gang == null ? 100 : gang.allValue();
        int minimum = gang == null ? 100 : gang.minimumValue();
        int maximum = gang == null ? 100 : gang.maximumValue();
        gangAllSlider.setValue(all);
        gangMinimumSlider.setValue(minimum);
        gangMaximumSlider.setValue(maximum);
        gangAllValue.setText(all + "%");
        gangMinimumValue.setText(minimum + "%");
        gangMaximumValue.setText(maximum + "%");
        goToSetSlidersButton.setDisable(!available
                || editor.stream().allMatch(frame -> frame.choices().isEmpty()));
        renameSliderPresetButton.setDisable(!available);
        for (Button button : List.of(zeroAllSliderChoicesButton, fiftyAllSliderChoicesButton,
                hundredAllSliderChoicesButton, zeroAllMinimumButton, fiftyAllMinimumButton,
                hundredAllMinimumButton, zeroAllMaximumButton, fiftyAllMaximumButton,
                hundredAllMaximumButton))
            button.setDisable(!available);
        gangAllCheck.setDisable(!available);
        gangMinimumCheck.setDisable(!available);
        gangMaximumCheck.setDisable(!available);
        gangAllSlider.setDisable(!available || !gangAllCheck.isSelected());
        gangMinimumSlider.setDisable(!available || !gangMinimumCheck.isSelected());
        gangMaximumSlider.setDisable(!available || !gangMaximumCheck.isSelected());
    }

    /** Builds the Slider Preset ListView that replaces an empty one on refill (see {@link UiaListHelper}). */
    private ListView<SliderPresetSnapshot> newSliderPresetList() {
        ListView<SliderPresetSnapshot> replacement = new ListView<>();
        replacement.setId("sliderPresetList");
        replacement.setAccessibleText("Slider Presets");
        replacement.setFocusTraversable(true);
        VBox.setVgrow(replacement, javafx.scene.layout.Priority.ALWAYS);
        return replacement;
    }

    /**
     * Adopts the swapped-in Slider Preset node. Any rename editor belonged to a cell of the stale node, so the active
     * rename references are dropped with it.
     */
    private void adoptSliderPresetList(ListView<SliderPresetSnapshot> replacement) {
        sliderPresetList = replacement;
        activeRenameCell = null;
        activeRenameField = null;
        configureSliderPresetList();
    }

    /**
     * Preserves the current focus/caret when the logical selection already matches the immutable feature frame.
     */
    private void reconcileSliderPresetSelection(Optional<NameIdentity> selection) {
        SliderPresetSnapshot selected = sliderPresetList.getSelectionModel().getSelectedItem();
        Optional<NameIdentity> current = selected == null
                ? Optional.empty()
                : Optional.of(NameIdentity.of(selected.getName()));
        if (current.equals(selection))
            return;
        sliderPresetList.getSelectionModel().clearSelection();
        selection.ifPresent(identity -> {
            for (int index = 0; index < sliderPresetList.getItems().size(); index++) {
                SliderPresetSnapshot candidate = sliderPresetList.getItems().get(index);
                if (NameIdentity.of(candidate.getName()).equals(identity)) {
                    sliderPresetList.getSelectionModel().select(index);
                    break;
                }
            }
        });
    }

    /**
     * Uses ListView's editing lifecycle rather than refresh(), which drops UIA children after rejected edits on
     * JavaFX 25, where this was last confirmed. JavaFX 26 changed refresh() to re-index cells instead of recreating
     * them (JDK-8359599), which may remove the cause; dropping this workaround needs a Tier 3 check first.
     */
    private void synchronizeRenameEditor(Optional<TemplatesFeature.RenameState> rename) {
        if (rename.isEmpty()) {
            if (sliderPresetList.getEditingIndex() >= 0)
                sliderPresetList.edit(-1);
            return;
        }
        NameIdentity identity = rename.orElseThrow().identity();
        int renameIndex = -1;
        for (int index = 0; index < sliderPresetList.getItems().size(); index++) {
            if (NameIdentity.of(sliderPresetList.getItems().get(index).getName()).equals(identity)) {
                renameIndex = index;
                break;
            }
        }
        if (renameIndex < 0) {
            sliderPresetList.edit(-1);
            return;
        }
        if (sliderPresetList.getEditingIndex() != renameIndex)
            sliderPresetList.edit(renameIndex);
        if (activeRenameCell != null)
            activeRenameCell.renderRename(rename.orElseThrow());
    }

    /**
     * @return current immutable Templates frame for package-local adapter verification
     */
    TemplatesFeature.Frame templatesFrame() {
        return templatesFeature.frame();
    }

    /**
     * @return the currently materialized inline rename field, when its virtualized row is visible
     */
    Optional<TextField> activeRenameField() {
        return Optional.ofNullable(activeRenameField);
    }

    /**
     * @return current Slider Preset ListView node, including an accessibility-driven empty/refill replacement
     */
    ListView<SliderPresetSnapshot> sliderPresetListNode() {
        return sliderPresetList;
    }

    /**
     * @return the Morphs Area's current Custom Morph Target ListView, including an accessibility-driven refill
     *         replacement, for tests that drive the whole loaded Workbench
     */
    ListView<CustomMorphTargetSnapshot> customMorphTargetListNode() {
        return morphsArea.customMorphTargetListNode();
    }

    /**
     * @return the Morphs Area's current NPC Morph Assignment ListView, including an accessibility-driven refill
     *         replacement, for tests that drive the whole loaded Workbench
     */
    ListView<NpcMorphAssignmentSnapshot> npcMorphAssignmentListNode() {
        return morphsArea.npcMorphAssignmentListNode();
    }

    /**
     * @return the Morphs Area's current assigned Slider Preset ListView, including an accessibility-driven refill
     *         replacement, for tests that drive the whole loaded Workbench
     */
    ListView<SliderPresetSnapshot> assignedMorphSliderPresetListNode() {
        return morphsArea.assignedMorphSliderPresetListNode();
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
        if (navigationFrame.activeArea() == WorkbenchNavigation.Area.TEMPLATES) {
            if (event.isControlDown() && event.getCode() == KeyCode.K) {
                if (navigationFrame.narrowMode())
                    applyNavigation(navigation.openPrimaryContent(currentSemanticFocus()));
                sliderPresetFilter.requestFocus();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.F2) {
                dispatchTemplates(new TemplatesFeature.BeginRename());
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ESCAPE && templatesFeature.frame().rename().isPresent()) {
                dispatchTemplates(new TemplatesFeature.CancelRename());
                event.consume();
                return;
            }
        }
        WorkbenchArea activeModule = areaModule(navigationFrame.activeArea());
        if (activeModule != null && event.isControlDown() && event.getCode() == KeyCode.K) {
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
        } else if (event.getCode() == KeyCode.ESCAPE
                && navigationFrame.activeArea() == WorkbenchNavigation.Area.TEMPLATES
                && templatesFeature.frame().selection().isPresent()) {
            dispatchTemplates(new TemplatesFeature.ClearSelection());
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && activeModule != null && activeModule.escape()) {
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
        WorkbenchArea activeModule = areaModule(navigationFrame.activeArea());
        if (activeModule != null && navigationFrame.overlay() == WorkbenchNavigation.Overlay.INSPECTOR)
            activeModule.resetInspectorScroll();
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
        boolean templatesActive = frame.activeArea() == WorkbenchNavigation.Area.TEMPLATES;
        templatesPrimaryScroll.setManaged(templatesActive);
        templatesPrimaryScroll.setVisible(templatesActive);
        templatesEditorContent.setManaged(templatesActive);
        templatesEditorContent.setVisible(templatesActive);
        templatesInspectorScroll.setManaged(templatesActive);
        templatesInspectorScroll.setVisible(templatesActive);
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
        return switch (area) {
            case TEMPLATES -> sliderPresetList;
            case MORPHS -> morphsArea.defaultFocusNode();
            case SETTINGS -> settingsArea.defaultFocusNode();
            case NPC_DATABASE -> npcDatabaseArea.defaultFocusNode();
        };
    }

    /**
     * Maps a semantic target to the currently valid adapter node without retaining that node in navigation state.
     */
    private Node resolveFocusNode(WorkbenchNavigation.FocusTarget target) {
        return switch (target.landmark()) {
            case RAIL -> areaButton(target.area());
            case NPC_DATABASE_LAUNCHER -> morphsArea.focusNode(WorkbenchNavigation.Landmark.NPC_DATABASE_LAUNCHER);
            case PRIMARY_LAUNCHER -> showPrimaryOverlayButton;
            case PRIMARY_CONTENT -> switch (target.area()) {
                case TEMPLATES -> sliderPresetList;
                case MORPHS -> morphsArea.focusNode(WorkbenchNavigation.Landmark.PRIMARY_CONTENT);
                case SETTINGS -> settingsArea.focusNode(WorkbenchNavigation.Landmark.PRIMARY_CONTENT);
                case NPC_DATABASE -> npcDatabaseArea.focusNode(WorkbenchNavigation.Landmark.PRIMARY_CONTENT);
            };
            case EDITOR -> switch (target.area()) {
                case TEMPLATES -> firstSliderChoiceControl().orElse(templateEditorFocusTarget);
                case MORPHS -> morphsArea.focusNode(WorkbenchNavigation.Landmark.EDITOR);
                case SETTINGS -> settingsArea.focusNode(WorkbenchNavigation.Landmark.EDITOR);
                case NPC_DATABASE -> npcDatabaseArea.focusNode(WorkbenchNavigation.Landmark.EDITOR);
            };
            case INSPECTOR_LAUNCHER -> showInspectorOverlayButton;
            case INSPECTOR -> switch (target.area()) {
                case TEMPLATES -> renameSliderPresetButton.isDisabled()
                        ? templateSelectionText : renameSliderPresetButton;
                case MORPHS -> morphsArea.focusNode(WorkbenchNavigation.Landmark.INSPECTOR);
                case SETTINGS -> settingsArea.focusNode(WorkbenchNavigation.Landmark.INSPECTOR);
                case NPC_DATABASE -> npcDatabaseArea.focusNode(WorkbenchNavigation.Landmark.INSPECTOR);
            };
            case OUTPUT_LAUNCHER -> outputAreaButton;
            case OUTPUT -> outputDrawer.focusNode(WorkbenchNavigation.Landmark.OUTPUT);
            case ACTIVITY -> activityList;
            case STATUS -> statusText;
        };
    }

    /**
     * Resolves the first canonical Slider choice row without retaining a control reference in navigation state.
     */
    private Optional<Node> firstSliderChoiceControl() {
        return sliderChoiceRowsByName.values().stream().findFirst().map(SliderChoiceRow::enabledControl)
                .map(Node.class::cast);
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
        if (lifecycleReset && npcDatabaseArea != null) {
            navigation.clearNpcDatabaseReturn();
            npcDatabaseArea.render(context);
            npcDatabaseArea.dispatch(new NpcDatabaseFeature.ClearAllSelections());
        }
        if (templatesFeature != null && templatesFeature.frame().projectSequence() != frame.sequence()) {
            templatesFeature.acceptProjectFrame(frame, context.resetTemplates(),
                    morphsOwnProjectDiagnostics ? List.of() : frame.diagnostics());
        }
        if (morphsFeature != null && morphsFeature.frame().projectSequence() != frame.sequence())
            morphsFeature.acceptProjectFrame(frame, context.resetMorphs(),
                    templatesOwnProjectDiagnostics ? List.of() : frame.diagnostics());
        if (outputFeature != null)
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
        renderTemplates(templatesFeature.frame());
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
     * Semantic Slider row focus token retained only across one synchronous failure dialog.
     */
    private record SliderChoiceFocus(String choiceName, SliderChoiceRow.FocusControl control) {
        /** Requires a complete logical row and editable-control identity. */
        private SliderChoiceFocus {
            Objects.requireNonNull(choiceName, "choiceName");
            Objects.requireNonNull(control, "control");
        }
    }

    /**
     * Stable Templates editor/inspector controls used for exact failure-dialog focus restoration.
     */
    private enum TemplatesControlFocus {
        PROFILE,
        GO_TO_SET_SLIDERS,
        RENAME,
        ZERO_ALL,
        FIFTY_ALL,
        HUNDRED_ALL,
        ZERO_MINIMUM,
        FIFTY_MINIMUM,
        HUNDRED_MINIMUM,
        ZERO_MAXIMUM,
        FIFTY_MAXIMUM,
        HUNDRED_MAXIMUM,
        GANG_ALL_CHECK,
        GANG_MINIMUM_CHECK,
        GANG_MAXIMUM_CHECK,
        GANG_ALL_SLIDER,
        GANG_MINIMUM_SLIDER,
        GANG_MAXIMUM_SLIDER
    }

    /**
     * Reusable Slider Preset cell that replaces exactly the renamed logical row with an accessible editor and attached
     * validation while JavaFX virtualizes list cells.
     */
    private final class SliderPresetCell extends ListCell<SliderPresetSnapshot> {
        private final TextField renameField = new TextField();
        private final Label renameValidation = new Label();
        private final VBox renameEditor = new VBox(4.0, renameField, renameValidation);
        private boolean rendering;

        private SliderPresetCell() {
            renameValidation.getStyleClass().add("validation-text");
            renameValidation.setWrapText(true);
            renameValidation.setLabelFor(renameField);
            renameField.textProperty().addListener((observable, previous, current) -> {
                if (!rendering && templatesFeature.frame().rename().isPresent())
                    dispatchTemplates(new TemplatesFeature.ChangeRename(current));
            });
            renameField.setOnAction(event -> dispatchTemplates(new TemplatesFeature.CommitRename()));
            renameField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    dispatchTemplates(new TemplatesFeature.CancelRename());
                    event.consume();
                }
            });
        }

        /**
         * Enters the one durable inline editor without refreshing or replacing the surrounding virtualized cells, and
         * moves focus into it with its draft selected.
         */
        @Override
        public void startEdit() {
            super.startEdit();
            SliderPresetSnapshot preset = getItem();
            if (preset == null)
                return;
            Optional<TemplatesFeature.RenameState> rename = templatesFeature.frame().rename()
                    .filter(value -> value.identity().equals(NameIdentity.of(preset.getName())));
            if (rename.isEmpty())
                return;
            renderRename(rename.orElseThrow());
            renameField.requestFocus();
            renameField.selectAll();
            // One of the two named focus deferrals (docs/build/workbench-platform.md, "Production platform bans"):
            // ListView's own click handling runs after startEdit and can take focus back to the list. Re-apply focus
            // once, and only while the list or this cell still owns it, so a control the user has moved to since
            // keeps its focus.
            ListView<SliderPresetSnapshot> list = getListView();
            Platform.runLater(() -> {
                if (isEditing() && (ownsSceneFocus(list) || ownsSceneFocus(this))) {
                    renameField.requestFocus();
                    renameField.selectAll();
                }
            });
        }

        /**
         * Restores the ordinary accessible row after commit, cancellation, filtering, or Project publication.
         */
        @Override
        public void cancelEdit() {
            super.cancelEdit();
            clearActiveRenameCell();
            renderPreset(getItem());
        }

        /**
         * Publishes or clears all accessible state whenever JavaFX reuses this cell for another logical identity.
         */
        @Override
        protected void updateItem(SliderPresetSnapshot preset, boolean empty) {
            super.updateItem(preset, empty);
            if (empty || preset == null) {
                clearActiveRenameCell();
                setText(null);
                setGraphic(null);
                setAccessibleText(null);
                setAccessibleHelp(null);
                return;
            }
            Optional<TemplatesFeature.RenameState> active = templatesFeature.frame().rename()
                    .filter(rename -> rename.identity().equals(NameIdentity.of(preset.getName())));
            if (isEditing() && active.isPresent())
                renderRename(active.orElseThrow());
            else
                renderPreset(preset);
        }

        /**
         * Updates the stable editor cell in place so validation does not invalidate the UIA SelectionItem subtree.
         */
        private void renderRename(TemplatesFeature.RenameState rename) {
            SliderPresetSnapshot preset = getItem();
            if (preset == null)
                return;
            rendering = true;
            renameField.setText(rename.draft());
            rendering = false;
            String accessibleName = "Rename Slider Preset " + preset.getName();
            String validation = ProjectDiagnosticFormatter.format(rename.diagnostics());
            renameField.setAccessibleText(accessibleName);
            renameField.setAccessibleHelp(validation.isEmpty()
                    ? "Enter commits the name. Escape cancels and restores the Slider Preset row."
                    : validation);
            renameValidation.setText(validation);
            renameValidation.setManaged(!validation.isEmpty());
            renameValidation.setVisible(!validation.isEmpty());
            activeRenameCell = this;
            activeRenameField = renameField;
            setText(null);
            setGraphic(renameEditor);
            setAccessibleText(accessibleName);
            setAccessibleHelp(renameField.getAccessibleHelp());
        }

        /**
         * Restores normal row text and complete accessible metadata.
         */
        private void renderPreset(SliderPresetSnapshot preset) {
            if (preset == null)
                return;
            setGraphic(null);
            setText(preset.getName());
            setAccessibleText(preset.getName());
            setAccessibleHelp((preset.isUunp() ? "UUNP" : "Standard") + " Slider Preset with "
                    + preset.getSliderChoices().size() + " slider choices.");
        }

        /**
         * Clears outer references only when this virtualized cell owns the active editor.
         */
        private void clearActiveRenameCell() {
            if (activeRenameCell == this)
                activeRenameCell = null;
            if (activeRenameField == renameField)
                activeRenameField = null;
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
