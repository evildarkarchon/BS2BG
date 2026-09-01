package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;
import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesFeature;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;
import com.asdasfa.jbs2bg.workbench.output.OutputFeature;
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;

import javafx.application.Platform;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
    @FXML
    private Button generateOutputButton;
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
    private Button primaryContentButton;
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
    private ScrollPane settingsPrimaryScroll;
    @FXML
    private VBox settingsPrimaryContent;
    @FXML
    private ComboBox<SettingsFeature.Profile> settingsProfileChoice;
    @FXML
    private ListView<SettingsFeature.EntryFrame> settingsEntryList;
    @FXML
    private TextField newSettingsEntryName;
    @FXML
    private Button addSettingsEntryButton;
    @FXML
    private VBox settingsEditorContent;
    @FXML
    private Label settingsValidationText;
    @FXML
    private TextField settingsEntryNameInput;
    @FXML
    private TextField settingsSmallInput;
    @FXML
    private TextField settingsBigInput;
    @FXML
    private TextField settingsMultiplierInput;
    @FXML
    private javafx.scene.control.CheckBox settingsInvertedCheck;
    @FXML
    private Button applySettingsEntryButton;
    @FXML
    private Button removeSettingsEntryButton;
    @FXML
    private VBox settingsInspectorContent;
    @FXML
    private Label settingsNoticeText;
    @FXML
    private javafx.scene.control.CheckBox omitRedundantSlidersCheck;
    @FXML
    private Button saveSettingsButton;
    @FXML
    private Button reloadSettingsButton;
    @FXML
    private Button editorButton;
    @FXML
    private Button inspectorButton;
    @FXML
    private Button showPrimaryOverlayButton;
    @FXML
    private Button showInspectorOverlayButton;
    @FXML
    private VBox outputDrawer;
    @FXML
    private Slider outputDrawerHeight;
    @FXML
    private Label outputFocusTarget;
    @FXML
    private TabPane outputTabs;
    @FXML
    private Tab templatesOutputTab;
    @FXML
    private Tab morphsOutputTab;
    @FXML
    private Tab bosOutputTab;
    @FXML
    private TextArea templatesOutputText;
    @FXML
    private TextArea morphsOutputText;
    @FXML
    private ComboBox<String> bosArtifactChoice;
    @FXML
    private TextArea bosOutputText;
    private WorkbenchNavigation.Frame navigationFrame = navigation.currentFrame();
    private WorkbenchProjectFlow projectFlow;
    private TemplatesFeature templatesFeature;
    private SettingsFeature settingsFeature;
    private OutputFeature outputFeature;
    private Stage stage;
    private WorkbenchPlatform platform;
    private JavaFxWorkbenchAppearance appearanceAdapter;
    private boolean finalClose;
    private long renderedProjectSequence;
    private WorkbenchProjectFlow.Intent activeOperation;
    private JobCoordinator.Subscription jobSubscription;
    private OutputFeature.Subscription outputSubscription;
    private long renderedTerminalAttemptId;
    private boolean closeAfterActiveJob;
    private boolean renderingTemplates;
    private boolean renderingSettings;
    private boolean renderingOutput;
    private TextField activeRenameField;
    private SliderPresetCell activeRenameCell;
    private boolean templatesMutationsBlocked;
    private boolean settingsMutationsBlocked;
    private boolean resetTemplatesOnNextProjectFrame;
    private boolean sliderPresetListInitialized;
    private final Map<String, SliderChoiceRow> sliderChoiceRowsByName = new LinkedHashMap<>();

    /**
     * Pairs one decorative vector with the text label and exposes its keyboard cue as help text.
     */
    private static void configureSemanticIcon(ToggleButton button, SemanticIcons.IconKey key, String shortcut) {
        button.setGraphic(SemanticIcons.create(key, true));
        button.setAccessibleHelp("Semantic icon: " + key.accessibleName() + ". Keyboard shortcut: " + shortcut + ".");
    }

    /**
     * Converts a Project confirmation response into the matching typed dialog action.
     */
    private static WorkbenchFeedback.DialogAction dialogAction(WorkbenchProjectFlow.Response response) {
        return switch (response.kind()) {
            case SAVE -> WorkbenchFeedback.DialogAction.SAVE;
            case DISCARD -> WorkbenchFeedback.DialogAction.DISCARD;
            case CANCELLED -> WorkbenchFeedback.DialogAction.CANCEL;
            case PATH_SELECTED, PATHS_SELECTED -> throw new IllegalArgumentException(
                    "A Project confirmation cannot return a selected path");
        };
    }

    /**
     * Keeps one severity style class on a feedback node so text/icon/boundary cues stay synchronized.
     */
    private static void setSeverityStyle(Node node, WorkbenchFeedback.Severity severity) {
        node.getStyleClass().removeAll("severity-information", "severity-validation", "severity-success",
                "severity-warning", "severity-failure");
        node.getStyleClass().add("severity-" + severity.name().toLowerCase(java.util.Locale.ROOT));
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

    /**
     * Formats durable attempt linkage, captured inputs, effects, and diagnostics for assistive inspection.
     */
    private static String activityHelp(WorkbenchFeedback.ActivityRecord activity) {
        if (activity.jobDetails().isEmpty())
            return "Timestamp: " + activity.occurredAt();
        WorkbenchFeedback.JobDetails details = activity.jobDetails().orElseThrow();
        return "Timestamp: " + activity.occurredAt()
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
                + ". Retry available: " + details.retryAvailable() + ".";
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
     * Attaches the loaded JavaFX graph to the sole Project flow and renders its current frame.
     *
     * @param flow       authoritative Workbench Project flow
     * @param ownerStage application window that receives Project titles
     * @throws IllegalStateException when this controller is attached more than once
     */
    public void attach(WorkbenchProjectFlow flow, Stage ownerStage) {
        attach(flow, ownerStage, Path.of("."), Settings.publishedState());
    }

    /**
     * Attaches the loaded JavaFX graph with the exact Settings startup result so recovery and failures become visible
     * Workbench evidence rather than being stranded in the composition root.
     *
     * @param flow               authoritative Workbench Project flow
     * @param ownerStage         application window that owns effects and focus
     * @param settingsDirectory  directory owning the paired Settings files
     * @param settingsStartup    original paired Settings startup result
     * @throws NullPointerException when an argument is null
     * @throws IllegalStateException when this controller is already attached
     */
    public void attach(WorkbenchProjectFlow flow, Stage ownerStage, Path settingsDirectory,
                       Settings.InitializationResult settingsStartup) {
        WorkbenchAppearanceStore store = new WorkbenchAppearanceStore(Path.of("."));
        WorkbenchAppearance.ThemeChoice initialChoice;
        try {
            initialChoice = store.load();
        } catch (IOException exception) {
            // A damaged or unreadable optional preference must not prevent the Workbench from starting safely.
            initialChoice = WorkbenchAppearance.ThemeChoice.SYSTEM;
        }
        attach(flow, ownerStage, new JavaFxWorkbenchPlatform(), initialChoice, store::save,
                settingsDirectory, settingsStartup, GenerationPreferencesStore.MigrationPolicy.MIGRATE);
    }

    /**
     * Attaches one platform adapter on the JavaFX Application Thread. The controller owns the Stage handlers until
     * the flow publishes its final close effect; it cannot be attached again.
     *
     * @param flow            authoritative window-scoped Project flow
     * @param ownerStage      Stage whose title, close request, choosers, and dialogs are owned by this controller
     * @param platformAdapter native-effect adapter retained for the controller lifetime
     * @throws NullPointerException  when an argument is null
     * @throws IllegalStateException when this controller is already attached
     */
    void attach(WorkbenchProjectFlow flow, Stage ownerStage, WorkbenchPlatform platformAdapter) {
        attach(flow, ownerStage, platformAdapter, WorkbenchAppearance.ThemeChoice.SYSTEM, choice -> {
            // Tests and embedded adapters intentionally keep theme selection in memory only.
        }, Path.of("."), Settings.publishedState(),
                GenerationPreferencesStore.MigrationPolicy.READ_ONLY_FALLBACK);
    }

    /**
     * Test and embedded adapter seam that supplies isolated Settings persistence and startup evidence on the JavaFX
     * Application Thread. The adapter and feature state remain owned until the Stage is hidden.
     *
     * @param flow authoritative window-scoped Project flow
     * @param ownerStage Stage owning controls, focus, and platform effects
     * @param platformAdapter native-effect adapter retained for the window lifetime
     * @param settingsDirectory isolated directory owning Settings persistence
     * @param settingsStartup startup Settings result rendered into the Settings feature and Activity
     * @throws NullPointerException when an argument is null
     * @throws IllegalStateException when this controller is already attached
     */
    void attach(WorkbenchProjectFlow flow, Stage ownerStage, WorkbenchPlatform platformAdapter,
                Path settingsDirectory, Settings.InitializationResult settingsStartup) {
        attach(flow, ownerStage, platformAdapter, WorkbenchAppearance.ThemeChoice.SYSTEM, choice -> {
            // Tests and embedded adapters intentionally keep theme selection in memory only.
        }, settingsDirectory, settingsStartup,
                GenerationPreferencesStore.MigrationPolicy.READ_ONLY_FALLBACK);
    }

    /**
     * Attaches Project, platform, and profile appearance adapters through one window-lifetime initialization path.
     *
     * @param flow            authoritative window-scoped Project flow
     * @param ownerStage      Stage that owns dialogs, focus, and the live appearance listener
     * @param platformAdapter native-effect adapter retained until the Stage is hidden
     * @param initialChoice   persisted System, Light, or Dark choice
     * @param themeSaver      profile persistence callback used after user selection
     * @param settingsDirectory directory owning the paired Settings files
     * @param settingsStartup original Settings startup result rendered as durable evidence
     * @param migrationPolicy explicit production migration or embedded read-only fallback policy
     * @throws NullPointerException  when an argument is null
     * @throws IllegalStateException when this controller is already attached
     */
    private void attach(WorkbenchProjectFlow flow, Stage ownerStage, WorkbenchPlatform platformAdapter,
                        WorkbenchAppearance.ThemeChoice initialChoice, ThemeChoiceSaver themeSaver,
                        Path settingsDirectory, Settings.InitializationResult settingsStartup,
                        GenerationPreferencesStore.MigrationPolicy migrationPolicy) {
        if (projectFlow != null)
            throw new IllegalStateException("WorkbenchController is already attached");
        projectFlow = Objects.requireNonNull(flow, "flow");
        templatesFeature = new TemplatesFeature(projectFlow, Clock.systemUTC());
        settingsFeature = new SettingsFeature(settingsDirectory, settingsStartup, migrationPolicy);
        outputFeature = new OutputFeature(projectFlow, () -> new OutputFeature.GenerationSettings(
                Settings.snapshot(), settingsFeature.frame().omitRedundantSliders()));
        stage = Objects.requireNonNull(ownerStage, "ownerStage");
        platform = Objects.requireNonNull(platformAdapter, "platformAdapter");
        configureProjectCommands();
        configureTemplates();
        configureSettings();
        configureOutput();
        configureNavigation();
        configureDrawerGeometry();
        configureFeedback();
        configureAppearance(initialChoice, themeSaver);
        configureSemanticIcons();
        stage.setOnCloseRequest(event -> {
            if (!finalClose) {
                event.consume();
                dispatch(WorkbenchProjectFlow.Intent.CLOSE);
            }
        });
        jobSubscription = projectFlow.jobs().observe(this::renderJobFrame);
        outputSubscription = outputFeature.observe(this::renderOutputUpdate);
        publishInitialSettingsEvidence();
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            appearanceAdapter.close();
            jobSubscription.close();
            outputSubscription.close();
        });
        renderedProjectSequence = projectFlow.frame().sequence();
        renderNavigation(navigationFrame);
        render(projectFlow.frame());
        renderTemplates(templatesFeature.frame());
        renderOutput(outputFeature.frame());
        renderFeedback(feedback.frame());
        if (workbenchRoot.getWidth() > 0.0)
            applyNavigation(navigation.resize(workbenchRoot.getWidth(), currentSemanticFocus()));
        // attach happens before Stage.show in production, so initial focus is realized on the next JavaFX pulse.
        Platform.runLater(() -> requestFocus(new WorkbenchNavigation.FocusTarget(
                navigationFrame.activeArea(), WorkbenchNavigation.Landmark.PRIMARY_CONTENT)));
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
     * Translates Settings controls into task-oriented feature intents and renders only committed immutable frames.
     */
    private void configureSettings() {
        settingsProfileChoice.getItems().setAll(SettingsFeature.Profile.values());
        settingsEntryList.setCellFactory(list -> new SettingsEntryCell());
        settingsProfileChoice.setOnAction(event -> {
            if (!renderingSettings && settingsProfileChoice.getValue() != null)
                dispatchSettings(new SettingsFeature.SelectProfile(settingsProfileChoice.getValue()));
        });
        settingsEntryList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (!renderingSettings && selected != null)
                        dispatchSettings(new SettingsFeature.SelectEntry(selected.name()));
                });
        addSettingsEntryButton.setOnAction(event ->
                dispatchSettings(new SettingsFeature.AddEntry(newSettingsEntryName.getText())));
        applySettingsEntryButton.setOnAction(event -> settingsFeature.frame().editor().ifPresent(editor ->
                dispatchSettings(new SettingsFeature.EditEntry(editor.originalName(), settingsEntryNameInput.getText(),
                        Optional.of(settingsSmallInput.getText()), Optional.of(settingsBigInput.getText()),
                        Optional.of(settingsMultiplierInput.getText()), settingsInvertedCheck.isSelected()))));
        removeSettingsEntryButton.setOnAction(event -> settingsFeature.frame().selection().ifPresent(name ->
                dispatchSettings(new SettingsFeature.RemoveEntry(name))));
        omitRedundantSlidersCheck.setOnAction(event -> dispatchSettings(
                new SettingsFeature.ChangeOmitRedundantSliders(omitRedundantSlidersCheck.isSelected())));
        saveSettingsButton.setOnAction(event -> dispatchSettings(new SettingsFeature.Save()));
        reloadSettingsButton.setOnAction(event -> dispatchSettings(new SettingsFeature.Reload()));
        renderSettings(settingsFeature.frame());
    }

    /** Connects Generate, Output tabs, and BoS identity selection to the JavaFX-independent Output feature. */
    private void configureOutput() {
        generateOutputButton.setOnAction(event -> dispatchOutput(new OutputFeature.Generate()));
        outputTabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (renderingOutput || selected == null)
                return;
            OutputFeature.Tab tab;
            if (selected == templatesOutputTab)
                tab = OutputFeature.Tab.TEMPLATES;
            else if (selected == morphsOutputTab)
                tab = OutputFeature.Tab.MORPHS;
            else if (selected == bosOutputTab)
                tab = OutputFeature.Tab.BOS_JSON;
            else
                return;
            dispatchOutput(new OutputFeature.SelectTab(tab));
        });
        bosArtifactChoice.setOnAction(event -> {
            if (!renderingOutput && bosArtifactChoice.getValue() != null)
                dispatchOutput(new OutputFeature.SelectBosArtifact(bosArtifactChoice.getValue()));
        });
        renderOutput(outputFeature.frame());
    }

    /** Dispatches one Output task and renders synchronous selection updates; Generate completes via observation. */
    private void dispatchOutput(OutputFeature.Intent intent) {
        OutputFeature.Update update = outputFeature.dispatch(Objects.requireNonNull(intent, "intent"));
        renderOutput(update.frame());
    }

    /** Applies one committed Output publication and consumes its optional drawer reveal exactly once. */
    private void renderOutputUpdate(OutputFeature.Update update) {
        renderOutput(Objects.requireNonNull(update, "update").frame());
        update.effect().filter(effect -> effect.kind() == OutputFeature.EffectKind.REVEAL_DRAWER)
                .ifPresent(effect -> applyNavigation(navigation.revealOutput()));
    }

    /** Renders accepted generated bytes and feature-owned tab/BoS identity without listener command loops. */
    private void renderOutput(OutputFeature.Frame frame) {
        renderingOutput = true;
        try {
            String emptyText = frame.displayedText();
            frame.generatedOutput().ifPresentOrElse(output -> {
                templatesOutputText.setText(output.getTemplatesText());
                morphsOutputText.setText(output.getMorphsText());
                bosArtifactChoice.getItems().setAll(frame.bosArtifactNames());
                bosArtifactChoice.setValue(frame.selectedBosArtifact().orElse(null));
                bosOutputText.setText(output.getBosJsonArtifacts().stream()
                        .filter(artifact -> frame.selectedBosArtifact().stream().anyMatch(name ->
                                name.equalsIgnoreCase(artifact.getSliderPresetName())))
                        .findFirst().map(artifact -> artifact.getText())
                        .orElse("No BoS JSON artifacts were generated."));
            }, () -> {
                templatesOutputText.setText(emptyText);
                morphsOutputText.setText(emptyText);
                bosArtifactChoice.getItems().clear();
                bosArtifactChoice.setValue(null);
                bosOutputText.setText(emptyText);
            });
            outputTabs.getSelectionModel().select(switch (frame.selectedTab()) {
                case TEMPLATES -> templatesOutputTab;
                case MORPHS -> morphsOutputTab;
                case BOS_JSON -> bosOutputTab;
            });
            bosArtifactChoice.setDisable(frame.bosArtifactNames().isEmpty());
            String freshness = switch (frame.freshness()) {
                case EMPTY -> "No generated Output is available.";
                case FRESH -> "Generated Output matches the current Project and Settings.";
                case INVALIDATED -> "Project changed—Generate again.";
            };
            outputTabs.setAccessibleHelp(freshness);
        } finally {
            renderingOutput = false;
        }
    }

    /** Commits one Settings intent, publishes its reporting tier, and refreshes output-affecting Templates previews. */
    private void dispatchSettings(SettingsFeature.Intent intent) {
        if (settingsMutationsBlocked && isSettingsMutation(intent))
            return;
        SettingsFeature.Update update = settingsFeature.dispatch(Objects.requireNonNull(intent, "intent"));
        renderSettings(update.frame());
        publishSettingsOutcome(update);
        if (update.accepted() && update.frame().outcome() == SettingsFeature.OutcomeKind.SAVED)
            renderTemplates(templatesFeature.refreshSettings().frame());
        if (update.accepted())
            renderOutput(outputFeature.refreshGenerationSettings().frame());
    }

    /** Distinguishes local profile browsing from operations that can change live or on-disk Settings. */
    private static boolean isSettingsMutation(SettingsFeature.Intent intent) {
        return intent instanceof SettingsFeature.AddEntry
                || intent instanceof SettingsFeature.EditEntry
                || intent instanceof SettingsFeature.RemoveEntry
                || intent instanceof SettingsFeature.ChangeOmitRedundantSliders
                || intent instanceof SettingsFeature.Save
                || intent instanceof SettingsFeature.Reload;
    }

    /** Projects Settings outcomes through inline validation, status-only drafts, and durable bulk Activity. */
    private void publishSettingsOutcome(SettingsFeature.Update update) {
        SettingsFeature.OutcomeKind kind = update.frame().outcome();
        if (kind == SettingsFeature.OutcomeKind.NONE || kind == SettingsFeature.OutcomeKind.REJECTED)
            return;
        WorkbenchFeedback.Notification notification = null;
        boolean durable = false;
        switch (kind) {
            case CHANGED -> {
                notification = new WorkbenchFeedback.Notification("Edit Settings",
                        WorkbenchFeedback.Severity.INFORMATION, "Settings draft changed.",
                        WorkbenchFeedback.Disposition.COMPLETED);
                durable = false;
            }
            case UNCHANGED -> {
                notification = new WorkbenchFeedback.Notification("Edit Settings",
                        WorkbenchFeedback.Severity.INFORMATION, "Settings are unchanged.",
                        WorkbenchFeedback.Disposition.COMPLETED);
                durable = false;
            }
            case SAVED -> {
                notification = new WorkbenchFeedback.Notification("Save Settings",
                        WorkbenchFeedback.Severity.SUCCESS, "Settings saved.",
                        WorkbenchFeedback.Disposition.COMPLETED);
                durable = true;
            }
            case RELOADED -> {
                notification = new WorkbenchFeedback.Notification("Reload Settings",
                        WorkbenchFeedback.Severity.SUCCESS, "Settings reloaded.",
                        WorkbenchFeedback.Disposition.COMPLETED);
                durable = true;
            }
            case RECOVERED -> {
                notification = new WorkbenchFeedback.Notification("Reload Settings",
                        WorkbenchFeedback.Severity.WARNING, "Settings recovered and reloaded.",
                        WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES);
                durable = true;
            }
            case FAILED -> {
                notification = new WorkbenchFeedback.Notification("Settings",
                        WorkbenchFeedback.Severity.FAILURE, settingsNoticeSummary(update.frame()),
                        WorkbenchFeedback.Disposition.FAILED);
                durable = true;
            }
            case NONE, REJECTED -> throw new AssertionError("Non-reportable Settings outcomes returned early");
        }
        if (notification == null)
            throw new IllegalStateException("Settings outcome did not produce feedback");
        renderFeedback(durable ? feedback.publishActivity(notification) : feedback.publishStatus(notification));
    }

    /** Publishes startup recovery and failure evidence once into durable Activity. */
    private void publishInitialSettingsEvidence() {
        if (settingsFeature.frame().notices().isEmpty())
            return;
        boolean failed = settingsFeature.frame().notices().stream().anyMatch(SettingsFeature.Notice::failure);
        WorkbenchFeedback.Notification notification = new WorkbenchFeedback.Notification("Load Settings",
                failed ? WorkbenchFeedback.Severity.FAILURE : WorkbenchFeedback.Severity.WARNING,
                settingsNoticeSummary(settingsFeature.frame()), failed
                ? WorkbenchFeedback.Disposition.FAILED : WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES);
        renderFeedback(feedback.publishActivity(notification));
    }

    /** Formats complete Settings diagnostic codes and paths without losing structured feature state. */
    private static String settingsNoticeSummary(SettingsFeature.Frame frame) {
        if (frame.notices().isEmpty())
            return "Settings operation failed.";
        return String.join("; ", frame.notices().stream()
                .map(notice -> notice.code() + " " + notice.path() + ": " + notice.message())
                .toList());
    }

    /** Renders one complete Settings frame while suppressing control-listener command loops. */
    private void renderSettings(SettingsFeature.Frame frame) {
        renderingSettings = true;
        try {
            settingsProfileChoice.setValue(frame.profile());
            if (!List.copyOf(settingsEntryList.getItems()).equals(frame.entries()))
                settingsEntryList.getItems().setAll(frame.entries());
            SettingsFeature.EntryFrame selected = settingsEntryList.getSelectionModel().getSelectedItem();
            String selectedName = selected == null ? null : selected.name();
            if (!Objects.equals(selectedName, frame.selection().orElse(null))) {
                settingsEntryList.getSelectionModel().clearSelection();
                frame.selection().ifPresent(name -> settingsEntryList.getItems().stream()
                        .filter(entry -> entry.name().equals(name)).findFirst()
                        .ifPresent(entry -> settingsEntryList.getSelectionModel().select(entry)));
            }
            frame.editor().ifPresentOrElse(editor -> {
                settingsEntryNameInput.setText(editor.name());
                settingsSmallInput.setText(editor.small());
                settingsBigInput.setText(editor.big());
                settingsMultiplierInput.setText(editor.multiplier());
                settingsInvertedCheck.setSelected(editor.inverted());
            }, () -> {
                settingsEntryNameInput.clear();
                settingsSmallInput.clear();
                settingsBigInput.clear();
                settingsMultiplierInput.clear();
                settingsInvertedCheck.setSelected(false);
            });
            settingsValidationText.setText(frame.validation().isEmpty()
                    ? frame.editor().isPresent() ? "Edit finite float values; blank leaves that category absent."
                    : "Select an entry to edit."
                    : String.join(System.lineSeparator(), frame.validation().stream()
                    .map(SettingsFeature.Validation::message).toList()));
            settingsNoticeText.setText(frame.notices().isEmpty()
                    ? frame.dirty() ? "Unsaved Settings changes." : "Standard and UUNP Settings are saved together."
                    : settingsNoticeSummary(frame));
            omitRedundantSlidersCheck.setSelected(frame.omitRedundantSliders());
            omitRedundantSlidersCheck.setDisable(settingsMutationsBlocked);
            boolean editable = frame.editor().isPresent() && !settingsMutationsBlocked;
            for (javafx.scene.control.Control control : List.of(settingsEntryNameInput, settingsSmallInput,
                    settingsBigInput, settingsMultiplierInput, settingsInvertedCheck, applySettingsEntryButton,
                    removeSettingsEntryButton))
                control.setDisable(!editable);
            newSettingsEntryName.setDisable(settingsMutationsBlocked);
            addSettingsEntryButton.setDisable(settingsMutationsBlocked);
            saveSettingsButton.setDisable(settingsMutationsBlocked || !frame.dirty()
                    || !frame.validation().isEmpty());
            reloadSettingsButton.setDisable(settingsMutationsBlocked);
            importBodySlideButton.setDisable(settingsMutationsBlocked || projectFlow.jobs().frame().active()
                    || projectFlow.jobs().frame().shutdownRequested() || !frame.liveAvailable());
        } finally {
            renderingSettings = false;
        }
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
        goToSetSlidersButton.setOnAction(event -> firstSliderChoiceControl().ifPresent(node -> {
            node.requestFocus();
            Platform.runLater(node::requestFocus);
        }));
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
     * Commits a typed Templates update before rendering Project chrome, feature controls, or later platform effects.
     */
    private void dispatchTemplates(TemplatesFeature.Intent intent) {
        if (templatesMutationsBlocked && isTemplatesMutation(intent))
            return;
        TemplatesFeature.Update update = templatesFeature.dispatch(Objects.requireNonNull(intent, "intent"));
        renderTemplatesUpdate(update);
        update.effect().ifPresent(this::completeTemplatesEffect);
        publishTemplatesOutcome(intent, update);
        if ((intent instanceof TemplatesFeature.CommitRename && update.accepted())
                || intent instanceof TemplatesFeature.CancelRename) {
            sliderPresetList.requestFocus();
            Platform.runLater(sliderPresetList::requestFocus);
        }
    }

    /**
     * Distinguishes Project mutations from local browsing so active jobs block writes without freezing the surface.
     */
    private static boolean isTemplatesMutation(TemplatesFeature.Intent intent) {
        return intent instanceof TemplatesFeature.Create
                || intent instanceof TemplatesFeature.Duplicate
                || intent instanceof TemplatesFeature.BeginRename
                || intent instanceof TemplatesFeature.ChangeRename
                || intent instanceof TemplatesFeature.CommitRename
                || intent instanceof TemplatesFeature.RequestRemove
                || intent instanceof TemplatesFeature.RequestClearVisible
                || intent instanceof TemplatesFeature.ChangeProfile
                || intent instanceof TemplatesFeature.SetChoiceEnabled
                || intent instanceof TemplatesFeature.SetChoiceRange
                || intent instanceof TemplatesFeature.ApplyBulkValue
                || intent instanceof TemplatesFeature.ToggleGang;
    }

    /**
     * Renders one feature update and refreshes Project chrome without replaying generic lifecycle feedback.
     */
    private void renderTemplatesUpdate(TemplatesFeature.Update update) {
        if (update.frame().projectSequence() != renderedProjectSequence) {
            // Templates owns task wording and inline validation, so suppress the lifecycle-only generic projection.
            renderedProjectSequence = update.frame().projectSequence();
            render(projectFlow.frame());
        }
        renderTemplates(update.frame());
    }

    /**
     * Publishes a destructive Templates dialog before realizing it, then returns the matching action as an ordinary
     * tokenized feature response.
     */
    private void completeTemplatesEffect(TemplatesFeature.Effect effect) {
        WorkbenchFeedback.DialogAction destructiveAction = effect.kind()
                == TemplatesFeature.EffectKind.CONFIRM_REMOVE
                ? WorkbenchFeedback.DialogAction.REMOVE
                : WorkbenchFeedback.DialogAction.CLEAR;
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.destructiveAction(
                effect.title(), effect.message(), destructiveAction);
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(spec);
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        renderFeedback(pendingFrame);
        WorkbenchFeedback.DialogAction action = platform.completeConfirmation(spec, stage);
        renderFeedback(feedback.answerDialog(new WorkbenchFeedback.DialogResult(
                pending.token(), action)).frame());
        boolean confirmed = action == destructiveAction;
        TemplatesFeature.Update response = templatesFeature.respond(effect.token(), confirmed);
        renderTemplatesUpdate(response);
        if (confirmed)
            publishTemplatesMutation(effect.kind() == TemplatesFeature.EffectKind.CONFIRM_REMOVE
                    ? "Remove Slider Preset" : "Clear visible Slider Presets", response);
    }

    /**
     * Routes feature validation and mutation outcomes into inline InfoBar, Activity, and status projections.
     */
    private void publishTemplatesOutcome(TemplatesFeature.Intent intent, TemplatesFeature.Update update) {
        if (isSliderChoiceIntent(intent)) {
            publishSliderChoiceOutcome(intent, update);
            return;
        }
        if (!update.frame().diagnostics().isEmpty()) {
            String message = ProjectDiagnosticFormatter.format(update.frame().diagnostics());
            renderFeedback(feedback.publishActivity(new WorkbenchFeedback.Notification(
                    "Templates validation", WorkbenchFeedback.Severity.VALIDATION, message,
                    WorkbenchFeedback.Disposition.FAILED)));
            if (intent instanceof TemplatesFeature.Create || intent instanceof TemplatesFeature.Duplicate) {
                sliderPresetNameInput.requestFocus();
                Platform.runLater(sliderPresetNameInput::requestFocus);
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
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(spec);
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        renderFeedback(pendingFrame);
        WorkbenchFeedback.DialogAction action = platform.completeFailure(spec, stage);
        renderFeedback(feedback.answerDialog(new WorkbenchFeedback.DialogResult(
                pending.token(), action)).frame());
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
        if (!control.isFocused())
            Platform.runLater(control::requestFocus);
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
        if (!control.isFocused())
            Platform.runLater(control::requestFocus);
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
     */
    private void renderTemplates(TemplatesFeature.Frame frame) {
        renderingTemplates = true;
        try {
            sliderPresetFilter.setText(frame.filterText());
            sliderPresetSort.setValue(frame.sortOrder());
            reconcileSliderPresetItems(frame.visiblePresets());
            sizeSliderPresetList(frame.visiblePresets().size());
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
                setSeverityStyle(templatesInfoBar, severity);
            }
            boolean selected = frame.selection().isPresent();
            createSliderPresetButton.setDisable(templatesMutationsBlocked);
            duplicateSliderPresetButton.setDisable(templatesMutationsBlocked || !selected);
            renameSliderPresetButton.setDisable(templatesMutationsBlocked || !selected);
            removeSliderPresetButton.setDisable(templatesMutationsBlocked || !selected);
            clearSliderPresetsButton.setDisable(templatesMutationsBlocked || frame.visiblePresets().isEmpty());
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
        sliderPresetProfile.setDisable(templatesMutationsBlocked);
        sliderPresetProfile.setValue(editorFrame.profile());
        Map<String, SliderChoiceRow> next = new LinkedHashMap<>();
        List<SliderChoiceRow> ordered = editorFrame.choices().stream().map(choice -> {
            String key = choice.name().toLowerCase(Locale.ROOT);
            SliderChoiceRow row = sliderChoiceRowsByName.get(key);
            if (row == null)
                row = new SliderChoiceRow(this::dispatchTemplates);
            row.render(editorFrame, choice, templatesMutationsBlocked);
            next.put(key, row);
            return row;
        }).toList();
        sliderChoiceRowsByName.clear();
        sliderChoiceRowsByName.putAll(next);
        if (!List.copyOf(sliderChoiceRows.getChildren()).equals(ordered))
            sliderChoiceRows.getChildren().setAll(ordered);
    }

    /**
     * Renders mutually exclusive gang state and disables every Project mutation when no preset is selected or a job
     * owns admission.
     */
    private void renderGangControls(Optional<TemplatesFeature.EditorFrame> editor) {
        boolean available = editor.isPresent() && !templatesMutationsBlocked;
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

    /**
     * Avoids replacing an unchanged ListView collection because JavaFX 25 UI Automation can drop every virtualized
     * cell after a no-op empty/refill notification sequence. Changed membership or values still replace atomically.
     */
    private void reconcileSliderPresetItems(List<SliderPresetSnapshot> visiblePresets) {
        boolean refill = sliderPresetListInitialized && sliderPresetList.getItems().isEmpty()
                && !visiblePresets.isEmpty();
        if (refill)
            replaceEmptySliderPresetList();
        if (!List.copyOf(sliderPresetList.getItems()).equals(visiblePresets))
            sliderPresetList.getItems().setAll(visiblePresets);
        sliderPresetListInitialized = true;
    }

    /**
     * Shows only the rows that exist (up to the accepted cap) so a short catalog does not expose a pointless inner
     * scrollbar; larger catalogs scroll within the list while the management pane handles minimum-height overflow.
     */
    private void sizeSliderPresetList(int visibleCount) {
        int rows = Math.max(1, Math.min(MAX_VISIBLE_SLIDER_PRESET_ROWS, visibleCount));
        double height = rows * SLIDER_PRESET_CELL_HEIGHT + 2.0;
        sliderPresetList.setMinHeight(height);
        sliderPresetList.setPrefHeight(height);
        sliderPresetList.setMaxHeight(height);
    }

    /**
     * Replaces only an empty ListView before refill because JavaFX 25's Windows UIA provider otherwise keeps an empty
     * virtualized accessibility subtree for the lifetime of that control.
     */
    private void replaceEmptySliderPresetList() {
        boolean restoreFocus = sliderPresetList.isFocused();
        ListView<SliderPresetSnapshot> replacement = new ListView<>();
        replacement.setId("sliderPresetList");
        replacement.setAccessibleText("Slider Presets");
        replacement.setFocusTraversable(true);
        VBox.setVgrow(replacement, javafx.scene.layout.Priority.ALWAYS);
        int index = templatesPrimaryContent.getChildren().indexOf(sliderPresetList);
        if (index < 0)
            throw new IllegalStateException("Templates primary content no longer owns the Slider Preset list");
        templatesPrimaryContent.getChildren().set(index, replacement);
        sliderPresetList = replacement;
        activeRenameCell = null;
        activeRenameField = null;
        configureSliderPresetList();
        if (restoreFocus) {
            // Import can refill an empty list while it owns semantic focus; the UIA node swap must retain that focus.
            replacement.requestFocus();
            Platform.runLater(replacement::requestFocus);
        }
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
     * Uses ListView's editing lifecycle rather than refresh(), which drops JavaFX 25 UIA children after rejected edits.
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
     * Clips the drawer to Workbench content and maps its public Slider value to drawer height.
     */
    private void configureDrawerGeometry() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(contentStack.widthProperty());
        clip.heightProperty().bind(contentStack.heightProperty());
        contentStack.setClip(clip);
        outputDrawerHeight.valueProperty().addListener((observable, oldValue, newValue) ->
                applyDrawerHeight(newValue.doubleValue()));
        applyDrawerHeight(outputDrawerHeight.getValue());
        contentStack.heightProperty().addListener((observable, oldHeight, newHeight) ->
                updateDrawerMaximum(newHeight.doubleValue()));
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

    /**
     * Enables Activity Retry only for one selected retryable terminal attempt while admission is open.
     */
    private void updateActivityRetry(WorkbenchFeedback.ActivityRecord selected) {
        boolean available = selected != null
                && selected.jobDetails().stream().anyMatch(WorkbenchFeedback.JobDetails::retryAvailable)
                && !projectFlow.jobs().frame().active()
                && !projectFlow.jobs().frame().shutdownRequested();
        retryActivityButton.setDisable(!available);
        retryActivityButton.setAccessibleHelp(available
                ? "Starts a new attempt linked to attempt "
                + selected.jobDetails().orElseThrow().attemptId() + " with freshly captured inputs."
                : "Select a retryable failed Activity record while no operation is active.");
    }

    /**
     * Requests a coordinator-owned retry for the selected durable Activity attempt.
     */
    private void retrySelectedActivity() {
        WorkbenchFeedback.ActivityRecord selected = activityList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.jobDetails().isEmpty())
            return;
        projectFlow.jobs().retry(new JobCoordinator.AttemptId(
                selected.jobDetails().orElseThrow().attemptId()));
    }

    /**
     * Connects System/Light/Dark selection to the live public-JavaFX appearance adapter and profile store.
     */
    private void configureAppearance(WorkbenchAppearance.ThemeChoice initialChoice, ThemeChoiceSaver saver) {
        Objects.requireNonNull(saver, "saver");
        WorkbenchAppearance appearance = new WorkbenchAppearance(
                Objects.requireNonNull(initialChoice, "initialChoice"),
                JavaFxWorkbenchAppearance.snapshot(Platform.getPreferences()));
        appearanceAdapter = new JavaFxWorkbenchAppearance(workbenchRoot, appearance, this::renderAppearance);
        themeChoice.getItems().setAll(WorkbenchAppearance.ThemeChoice.values());
        themeChoice.setValue(initialChoice);
        themeChoice.setOnAction(event -> {
            WorkbenchAppearance.ThemeChoice selected = themeChoice.getValue();
            if (selected == null)
                return;
            appearanceAdapter.selectTheme(selected);
            try {
                saver.save(selected);
            } catch (IOException exception) {
                renderFeedback(feedback.publish(new WorkbenchFeedback.Notification(
                        "Theme preference", WorkbenchFeedback.Severity.WARNING,
                        "The selected theme could not be saved.",
                        WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES)));
            }
        });
        appearanceAdapter.start();
    }

    /**
     * Adds the selected bundled-vector implementation to every text-labelled Workbench rail action.
     */
    private void configureSemanticIcons() {
        configureSemanticIcon(templatesAreaButton, SemanticIcons.IconKey.TEMPLATES, "Ctrl+1");
        configureSemanticIcon(morphsAreaButton, SemanticIcons.IconKey.MORPHS, "Ctrl+2");
        configureSemanticIcon(npcDatabaseAreaButton, SemanticIcons.IconKey.NPC_DATABASE, "Ctrl+3");
        configureSemanticIcon(outputAreaButton, SemanticIcons.IconKey.OUTPUT, "Ctrl+4 or Ctrl+Backtick");
        configureSemanticIcon(settingsAreaButton, SemanticIcons.IconKey.SETTINGS, "Ctrl+5");
    }

    /**
     * Renders effective theme and reduced-motion state as explicit non-color text.
     */
    private void renderAppearance(WorkbenchAppearance.Frame frame) {
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
     * Pins the bottom-aligned resizable VBox to the user-selected drawer height.
     */
    private void applyDrawerHeight(double height) {
        outputDrawer.setPrefHeight(height);
        outputDrawer.setMaxHeight(height);
    }

    /**
     * Keeps the drawer proportional at small client heights while retaining its accepted keyboard resize range.
     */
    private void updateDrawerMaximum(double contentHeight) {
        if (contentHeight <= 0.0)
            return;
        double maximum = Math.max(outputDrawerHeight.getMin(), contentHeight * 0.65);
        outputDrawerHeight.setMax(maximum);
        if (outputDrawerHeight.getValue() > maximum)
            outputDrawerHeight.setValue(maximum);
    }

    /**
     * Translates one accepted key gesture into a typed navigation transition.
     */
    private void handleNavigationKey(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.G) {
            dispatchOutput(new OutputFeature.Generate());
            event.consume();
            return;
        }
        if (navigationFrame.activeArea() == WorkbenchNavigation.Area.TEMPLATES) {
            if (event.isControlDown() && event.getCode() == KeyCode.K) {
                if (navigationFrame.narrowMode())
                    applyNavigation(navigation.openPrimaryContent(currentSemanticFocus()));
                sliderPresetFilter.requestFocus();
                Platform.runLater(sliderPresetFilter::requestFocus);
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
            transition = navigation.cycleFocus(currentSemanticFocus());
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
        navigationFrame = transition.frame();
        renderNavigation(navigationFrame);
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
        boolean settingsActive = frame.activeArea() == WorkbenchNavigation.Area.SETTINGS;
        templatesPrimaryScroll.setManaged(templatesActive);
        templatesPrimaryScroll.setVisible(templatesActive);
        templatesEditorContent.setManaged(templatesActive);
        templatesEditorContent.setVisible(templatesActive);
        templatesInspectorScroll.setManaged(templatesActive);
        templatesInspectorScroll.setVisible(templatesActive);
        settingsPrimaryScroll.setManaged(settingsActive);
        settingsPrimaryScroll.setVisible(settingsActive);
        settingsEditorContent.setManaged(settingsActive);
        settingsEditorContent.setVisible(settingsActive);
        settingsInspectorContent.setManaged(settingsActive);
        settingsInspectorContent.setVisible(settingsActive);
        boolean placeholderActive = !templatesActive && !settingsActive;
        primaryContentButton.setManaged(placeholderActive);
        primaryContentButton.setVisible(placeholderActive);
        editorButton.setManaged(placeholderActive);
        editorButton.setVisible(placeholderActive);
        inspectorButton.setManaged(placeholderActive);
        inspectorButton.setVisible(placeholderActive);
        areaTitle.setText(area);
        areaTitle.setAccessibleText(area + " Area");
        primaryPane.setAccessibleText(area + " primary content");
        primaryContentButton.setAccessibleText(area + " primary content");
        primaryContentButton.setText("Focus " + area + " list");
        editorPane.setAccessibleText(area + " editor");
        editorButton.setAccessibleText(area + " editor");
        editorButton.setText(area + " Area — Workbench placeholder");
        inspectorPane.setAccessibleText(area + " inspector");
        inspectorButton.setAccessibleText(area + " inspector");
        inspectorButton.setText("Focus " + area + " inspector");
        showPrimaryOverlayButton.setAccessibleText("Open " + area + " list");
        showInspectorOverlayButton.setAccessibleText("Open " + area + " inspector");
        showPrimaryOverlayButton.setManaged(frame.narrowMode());
        showPrimaryOverlayButton.setVisible(frame.narrowMode());
        showInspectorOverlayButton.setManaged(frame.narrowMode());
        showInspectorOverlayButton.setVisible(frame.narrowMode());

        renderResponsivePanes(frame);
        outputDrawer.setManaged(frame.outputDrawerVisible());
        outputDrawer.setVisible(frame.outputDrawerVisible());
        if (frame.outputDrawerVisible())
            outputDrawer.toFront();
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
     * Resolves current JavaFX focus back into a semantic target safe to retain across re-rendering.
     */
    private WorkbenchNavigation.FocusTarget currentSemanticFocus() {
        Node focusOwner = stage != null && stage.getScene() != null ? stage.getScene().getFocusOwner() : null;
        WorkbenchNavigation.Landmark landmark;
        if (focusOwner == sliderPresetFilter || focusOwner == sliderPresetList
                || focusOwner == sliderPresetNameInput || focusOwner == importBodySlideButton
                || focusOwner == settingsProfileChoice || focusOwner == settingsEntryList
                || focusOwner == newSettingsEntryName || focusOwner == addSettingsEntryButton) {
            landmark = WorkbenchNavigation.Landmark.PRIMARY_CONTENT;
        } else if (focusOwner == templateEditorFocusTarget || focusOwner == sliderPresetProfile
                || sliderChoiceRowsByName.values().stream().anyMatch(row -> row.contains(focusOwner))
                || focusOwner == settingsEntryNameInput || focusOwner == settingsSmallInput
                || focusOwner == settingsBigInput || focusOwner == settingsMultiplierInput
                || focusOwner == settingsInvertedCheck || focusOwner == applySettingsEntryButton
                || focusOwner == removeSettingsEntryButton) {
            landmark = WorkbenchNavigation.Landmark.EDITOR;
        } else if (focusOwner == templateSelectionText
                || templatesFocusNodes().entrySet().stream()
                .anyMatch(entry -> entry.getKey() != TemplatesControlFocus.PROFILE
                        && entry.getValue() == focusOwner)
                || focusOwner == settingsNoticeText || focusOwner == saveSettingsButton
                || focusOwner == reloadSettingsButton || focusOwner == omitRedundantSlidersCheck) {
            landmark = WorkbenchNavigation.Landmark.INSPECTOR;
        } else if (focusOwner == primaryContentButton) {
            landmark = WorkbenchNavigation.Landmark.PRIMARY_CONTENT;
        } else if (focusOwner == editorButton) {
            landmark = WorkbenchNavigation.Landmark.EDITOR;
        } else if (focusOwner == inspectorButton) {
            landmark = WorkbenchNavigation.Landmark.INSPECTOR;
        } else if (focusOwner == showPrimaryOverlayButton) {
            landmark = WorkbenchNavigation.Landmark.PRIMARY_LAUNCHER;
        } else if (focusOwner == showInspectorOverlayButton) {
            landmark = WorkbenchNavigation.Landmark.INSPECTOR_LAUNCHER;
        } else if (focusOwner == outputAreaButton) {
            landmark = WorkbenchNavigation.Landmark.OUTPUT_LAUNCHER;
        } else if (focusOwner == outputFocusTarget || focusOwner == outputTabs
                || focusOwner == templatesOutputText || focusOwner == morphsOutputText
                || focusOwner == bosArtifactChoice || focusOwner == bosOutputText
                || focusOwner == outputDrawerHeight) {
            landmark = WorkbenchNavigation.Landmark.OUTPUT;
        } else if (focusOwner == activityList) {
            landmark = WorkbenchNavigation.Landmark.ACTIVITY;
        } else if (focusOwner == statusText) {
            landmark = WorkbenchNavigation.Landmark.STATUS;
        } else {
            landmark = WorkbenchNavigation.Landmark.RAIL;
        }
        return new WorkbenchNavigation.FocusTarget(navigationFrame.activeArea(), landmark);
    }

    /**
     * Applies one semantic focus effect, falling back to the active Area's first editable control.
     */
    private void requestFocus(WorkbenchNavigation.FocusTarget target) {
        Node node = resolveFocusNode(target);
        if (node == null || !node.isVisible() || node.isDisabled() || node.getParent() == null) {
            node = switch (target.area()) {
                case TEMPLATES -> sliderPresetList;
                case SETTINGS -> settingsEntryList;
                case MORPHS, NPC_DATABASE -> editorButton;
            };
        }
        Node resolved = node;
        resolved.requestFocus();
        if (!resolved.isFocused())
            Platform.runLater(resolved::requestFocus);
    }

    /**
     * Maps a semantic target to the currently valid adapter node without retaining that node in navigation state.
     */
    private Node resolveFocusNode(WorkbenchNavigation.FocusTarget target) {
        return switch (target.landmark()) {
            case RAIL -> areaButton(target.area());
            case PRIMARY_LAUNCHER -> showPrimaryOverlayButton;
            case PRIMARY_CONTENT -> switch (target.area()) {
                case TEMPLATES -> sliderPresetList;
                case SETTINGS -> settingsEntryList;
                case MORPHS, NPC_DATABASE -> primaryContentButton;
            };
            case EDITOR -> switch (target.area()) {
                case TEMPLATES -> firstSliderChoiceControl().orElse(templateEditorFocusTarget);
                case SETTINGS -> settingsEntryNameInput;
                case MORPHS, NPC_DATABASE -> editorButton;
            };
            case INSPECTOR_LAUNCHER -> showInspectorOverlayButton;
            case INSPECTOR -> switch (target.area()) {
                case TEMPLATES -> renameSliderPresetButton.isDisabled()
                        ? templateSelectionText : renameSliderPresetButton;
                case SETTINGS -> saveSettingsButton.isDisabled() ? settingsNoticeText : saveSettingsButton;
                case MORPHS, NPC_DATABASE -> inspectorButton;
            };
            case OUTPUT_LAUNCHER -> outputAreaButton;
            case OUTPUT -> outputFocusTarget;
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
        activeOperation = Objects.requireNonNull(intent, "intent");
        try {
            apply(projectFlow.request(intent));
        } finally {
            activeOperation = null;
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
            WorkbenchFeedback.PendingDialog pendingDialog = null;
            var dialogSpec = JavaFxWorkbenchPlatform.dialogSpec(effect.kind());
            if (dialogSpec.isPresent()) {
                WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(dialogSpec.orElseThrow());
                pendingDialog = pendingFrame.pendingDialog().orElseThrow();
                renderFeedback(pendingFrame);
            }
            WorkbenchProjectFlow.Response response = platform.complete(effect, stage);
            if (pendingDialog != null) {
                WorkbenchFeedback.DialogResult result = new WorkbenchFeedback.DialogResult(
                        pendingDialog.token(), dialogAction(response));
                renderFeedback(feedback.answerDialog(result).frame());
            }
            current = projectFlow.respond(effect.token(), response);
        }
    }

    /**
     * Renders title, lifecycle summary, and complete structured diagnostics from one immutable Project frame.
     */
    private void render(WorkbenchProjectFlow.Frame frame) {
        stage.setTitle(frame.title());
        projectStatusText.setText(projectStatus(frame));
        diagnosticsText.setText(ProjectDiagnosticFormatter.format(frame.diagnostics()));
        if (templatesFeature != null && templatesFeature.frame().projectSequence() != frame.sequence()) {
            boolean resetSelection = activeOperation == WorkbenchProjectFlow.Intent.NEW
                    || activeOperation == WorkbenchProjectFlow.Intent.OPEN
                    || resetTemplatesOnNextProjectFrame;
            renderTemplates(templatesFeature.acceptProjectFrame(frame, resetSelection).frame());
            resetTemplatesOnNextProjectFrame = false;
        }
        if (outputFeature != null)
            renderOutput(outputFeature.acceptProjectFrame(frame).frame());
        if (!frame.closed() && frame.sequence() != renderedProjectSequence) {
            renderedProjectSequence = frame.sequence();
            publishProjectFeedback(frame);
        }
    }

    /**
     * Projects one coordinator frame into global admission, progress, cancellation, Activity, and shutdown UI.
     */
    private void renderJobFrame(JobCoordinator.Frame frame) {
        Objects.requireNonNull(frame, "frame");
        boolean blocked = frame.active() || frame.shutdownRequested();
        boolean projectExclusive = frame.shutdownRequested() || frame.attempt().stream()
                .filter(JobCoordinator.Attempt::active)
                .anyMatch(attempt -> attempt.operation().consistencyClass()
                        != JobCoordinator.ConsistencyClass.SNAPSHOT_DERIVED);
        templatesMutationsBlocked = projectExclusive;
        settingsMutationsBlocked = projectExclusive;
        newProjectMenuItem.setDisable(blocked);
        openProjectMenuItem.setDisable(blocked);
        saveProjectMenuItem.setDisable(blocked);
        saveAsProjectMenuItem.setDisable(blocked);
        importBodySlideButton.setDisable(blocked);
        generateOutputButton.setDisable(blocked);
        renderTemplates(templatesFeature.frame());
        renderSettings(settingsFeature.frame());
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
            // The job terminal record is the authoritative feedback source for async work.
            renderedProjectSequence = projectFrame.sequence();
            render(projectFrame);
            if (terminal.id().value() > renderedTerminalAttemptId) {
                renderedTerminalAttemptId = terminal.id().value();
                renderTerminalJob(terminal);
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
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.failure(
                attempt.operation().name() + " failed", attempt.summary(), details, attempt.retryAvailable());
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(spec);
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        renderFeedback(pendingFrame);
        WorkbenchFeedback.DialogAction action = platform.completeFailure(spec, stage);
        renderFeedback(feedback.answerDialog(new WorkbenchFeedback.DialogResult(
                pending.token(), action)).frame());
        if (action == WorkbenchFeedback.DialogAction.RETRY)
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
            setSeverityStyle(infoBar, value.severity());
            infoBarIconHost.getChildren().setAll(SemanticIcons.create(value.icon(), true));
            infoBar.setManaged(true);
            infoBar.setVisible(true);
        }, () -> {
            infoBar.setManaged(false);
            infoBar.setVisible(false);
        });
        activityList.getItems().setAll(frame.activities());
        WorkbenchFeedback.StatusProjection status = frame.status();
        boolean initialReady = frame.activities().isEmpty() && status.message().equals("Ready");
        statusText.setText(initialReady
                ? "Ready"
                : status.severity().cue() + " — " + status.dispositionText() + " — " + status.message());
        statusText.setAccessibleText("Workbench status");
        statusText.setAccessibleHelp(status.severity().cue() + ", "
                + status.dispositionText() + ", " + status.message());
        statusIconHost.getChildren().setAll(SemanticIcons.create(status.severity().icon(), true));
        setSeverityStyle(statusText, status.severity());
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
     * Profile persistence function whose checked failure becomes nonmodal feedback.
     */
    @FunctionalInterface
    private interface ThemeChoiceSaver {
        /**
         * Persists one selected theme inside the active application profile.
         */
        void save(WorkbenchAppearance.ThemeChoice choice) throws IOException;
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
         * Enters the one durable inline editor without refreshing or replacing the surrounding virtualized cells.
         */
        @Override
        public void startEdit() {
            super.startEdit();
            SliderPresetSnapshot preset = getItem();
            if (preset == null)
                return;
            templatesFeature.frame().rename()
                    .filter(value -> value.identity().equals(NameIdentity.of(preset.getName())))
                    .ifPresent(this::renderRename);
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
            if (!renameField.isFocused()) {
                Platform.runLater(() -> {
                    renameField.requestFocus();
                    renameField.selectAll();
                });
            }
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

    /** List cell exposing complete Settings category membership through stable accessible text. */
    private static final class SettingsEntryCell extends ListCell<SettingsFeature.EntryFrame> {
        /** Renders one exact Settings identity without depending on list position. */
        @Override
        protected void updateItem(SettingsFeature.EntryFrame entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setAccessibleText(null);
                setAccessibleHelp(null);
                return;
            }
            setText(entry.name());
            setAccessibleText(entry.name());
            String defaults = entry.small().isPresent()
                    ? "defaults " + entry.small().orElseThrow() + " to " + entry.big().orElseThrow()
                    : "defaults absent";
            String multiplier = entry.multiplier().map(value -> "multiplier " + value)
                    .orElse("multiplier absent");
            setAccessibleHelp(defaults + "; " + multiplier + "; inverted " + entry.inverted() + ".");
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
