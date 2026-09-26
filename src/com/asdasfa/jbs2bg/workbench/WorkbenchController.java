package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.FilterColumn;
import com.asdasfa.jbs2bg.filtering.NpcTableColumns;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.filtering.SortDirection;
import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.workbench.morphs.MorphsFeature;
import com.asdasfa.jbs2bg.workbench.morphs.NpcPortraitFiles;
import com.asdasfa.jbs2bg.workbench.npcdatabase.NpcDatabaseFeature;
import com.asdasfa.jbs2bg.workbench.npcdatabase.NpcDatabaseImporter;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesFeature;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;
import com.asdasfa.jbs2bg.workbench.output.OutputFeature;
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Popup;
import javafx.stage.WindowEvent;

/**
 * JavaFX adapter for the Workbench root graph; Project and navigation state remain JavaFX-independent.
 */
public final class WorkbenchController {
    private static final double SLIDER_PRESET_CELL_HEIGHT = 28.0;
    private static final int MAX_VISIBLE_SLIDER_PRESET_ROWS = 8;
    private static final double MORPH_TARGET_CELL_HEIGHT = 28.0;
    private static final int MAX_VISIBLE_MORPH_TARGET_ROWS = 8;

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
    private ScrollPane npcDatabasePrimaryScroll;
    @FXML
    private Label npcSourceCountText;
    @FXML
    private ListView<NpcDatabaseFeature.Source> npcSourceList;
    @FXML
    private Button importNpcSourcesButton;
    @FXML
    private Button removeNpcSourceButton;
    @FXML
    private Button clearNpcDatabaseButton;
    @FXML
    private Button addNpcsFromDatabaseButton;
    @FXML
    private VBox npcDatabaseEditorContent;
    @FXML
    private Button backToMorphsButton;
    @FXML
    private HBox npcPromotionInfoBar;
    @FXML
    private Label npcPromotionInfoBarCue;
    @FXML
    private Label npcPromotionInfoBarMessage;
    @FXML
    private Button dismissNpcPromotionInfoBarButton;
    @FXML
    private Label npcCatalogSummary;
    @FXML
    private ComboBox<String> npcSortChoice;
    @FXML
    private ToggleButton npcNameFilterButton;
    @FXML
    private ToggleButton npcMasterFilterButton;
    @FXML
    private ToggleButton npcRaceFilterButton;
    @FXML
    private ToggleButton npcEditorIdFilterButton;
    @FXML
    private ToggleButton npcFormIdFilterButton;
    @FXML
    private Button clearNpcFiltersButton;
    @FXML
    private TableView<NPC> npcCatalogTable;
    @FXML
    private Button addAllNpcsToProjectButton;
    @FXML
    private TextArea npcPromotionDetails;
    @FXML
    private TableColumn<NPC, String> npcNameColumn;
    @FXML
    private TableColumn<NPC, String> npcMasterColumn;
    @FXML
    private TableColumn<NPC, String> npcRaceColumn;
    @FXML
    private TableColumn<NPC, String> npcEditorIdColumn;
    @FXML
    private TableColumn<NPC, String> npcFormIdColumn;
    @FXML
    private ScrollPane npcDatabaseInspectorScroll;
    @FXML
    private Label npcInspectorName;
    @FXML
    private Label npcInspectorSource;
    @FXML
    private Label npcInspectorMaster;
    @FXML
    private Label npcInspectorEditorId;
    @FXML
    private Label npcInspectorRace;
    @FXML
    private Label npcInspectorFormId;
    @FXML
    private Button addNpcToProjectButton;
    @FXML
    private javafx.scene.control.CheckBox assignRandomNpcPresetCheck;
    @FXML
    private ImageView npcDatabasePortraitImage;
    @FXML
    private Label npcDatabasePortraitStatus;
    @FXML
    private Button openNpcDatabasePortraitViewerButton;
    @FXML
    private Label npcDatabaseDiagnostics;
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
    private ScrollPane morphsPrimaryScroll;
    @FXML
    private VBox morphsPrimaryContent;
    @FXML
    private HBox morphsInfoBar;
    @FXML
    private Label morphsInfoBarCue;
    @FXML
    private Label morphsInfoBarMessage;
    @FXML
    private Button dismissMorphsInfoBarButton;
    @FXML
    private TextField customMorphTargetFilter;
    @FXML
    private ComboBox<MorphsFeature.SortOrder> customMorphTargetSort;
    @FXML
    private ListView<CustomMorphTargetSnapshot> customMorphTargetList;
    @FXML
    private TextField customMorphTargetNameInput;
    @FXML
    private Button createCustomMorphTargetButton;
    @FXML
    private Button removeCustomMorphTargetButton;
    @FXML
    private Button clearCustomMorphTargetsButton;
    @FXML
    private TextField npcMorphAssignmentFilter;
    @FXML
    private ComboBox<MorphsFeature.NpcSortOrder> npcMorphAssignmentSort;
    @FXML
    private ListView<NpcMorphAssignmentSnapshot> npcMorphAssignmentList;
    @FXML
    private TextField npcDisplayNameInput;
    @FXML
    private TextField npcPluginNameInput;
    @FXML
    private TextField npcEditorIdInput;
    @FXML
    private TextField npcRaceInput;
    @FXML
    private TextField npcFormIdInput;
    @FXML
    private Button createNpcMorphAssignmentButton;
    @FXML
    private Button removeNpcMorphAssignmentButton;
    @FXML
    private Button clearNpcMorphAssignmentsButton;
    @FXML
    private Button fillEmptyNpcMorphAssignmentsButton;
    @FXML
    private VBox morphsEditorContent;
    @FXML
    private Label morphTargetEditorFocusTarget;
    @FXML
    private Label morphTargetConditionText;
    @FXML
    private Label npcIdentityText;
    @FXML
    private Label npcRaceText;
    @FXML
    private Label npcFormIdText;
    @FXML
    private Label morphTargetAssignmentCountText;
    @FXML
    private Label morphTargetOutputStatusText;
    @FXML
    private ScrollPane morphsInspectorScroll;
    @FXML
    private VBox morphsInspectorContent;
    @FXML
    private Label morphTargetSelectionText;
    @FXML
    private VBox npcPortraitSection;
    @FXML
    private ImageView npcPortraitImage;
    @FXML
    private Label npcPortraitStatus;
    @FXML
    private Button openNpcPortraitViewerButton;
    @FXML
    private Label assignedMorphSliderPresetLabel;
    @FXML
    private ListView<SliderPresetSnapshot> assignedMorphSliderPresetList;
    @FXML
    private Button removeMorphSliderPresetButton;
    @FXML
    private Button clearMorphSliderPresetsButton;
    @FXML
    private ComboBox<SliderPresetSnapshot> availableMorphSliderPreset;
    @FXML
    private Button assignMorphSliderPresetButton;
    @FXML
    private Button assignAllMorphSliderPresetsButton;
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
    @FXML
    private Button copyOutputButton;
    @FXML
    private Button exportOutputButton;
    @FXML
    private Button exportSelectedBosButton;
    private WorkbenchNavigation.Frame navigationFrame = navigation.currentFrame();
    private WorkbenchProjectFlow projectFlow;
    private TemplatesFeature templatesFeature;
    private MorphsFeature morphsFeature;
    private NpcDatabaseFeature npcDatabaseFeature;
    private NpcDatabaseImporter npcDatabaseImporter;
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
    private SettingsCloseContinuation settingsCloseContinuation;
    // Settings publication may beat a competing shutdown gate; derived refresh waits until admission resumes.
    private boolean settingsRefreshDeferred;
    private boolean renderingTemplates;
    private boolean renderingMorphs;
    private boolean renderingNpcDatabase;
    private boolean renderingSettings;
    private boolean renderingOutput;
    private boolean templatesOwnProjectDiagnostics;
    private boolean morphsOwnProjectDiagnostics;
    private TextField activeRenameField;
    private SliderPresetCell activeRenameCell;
    private boolean templatesMutationsBlocked;
    private boolean morphsMutationsBlocked;
    private boolean settingsMutationsBlocked;
    private boolean resetTemplatesOnNextProjectFrame;
    private boolean resetMorphsOnNextProjectFrame;
    private boolean sliderPresetListInitialized;
    private boolean customMorphTargetListInitialized;
    private boolean npcMorphAssignmentListInitialized;
    private boolean assignedMorphSliderPresetListInitialized;
    private Optional<NameIdentity> renderedMorphTargetSelection = Optional.empty();
    private Optional<NpcMorphAssignmentIdentity> renderedNpcSelection = Optional.empty();
    private NpcMorphAssignmentIdentity renderedPortraitIdentity;
    private String renderedPortraitDisplayName;
    private Path renderedPortraitPath;
    private Image renderedPortraitImage;
    private NpcMorphAssignmentIdentity renderedDatabasePortraitIdentity;
    private String renderedDatabasePortraitDisplayName;
    private Path renderedDatabasePortraitPath;
    private Image renderedDatabasePortraitImage;
    private Popup fillEmptyPopup;
    private Parent fillEmptyPopupRoot;
    private Stage npcPortraitViewer;
    private Parent npcPortraitViewerRoot;
    private Path npcPortraitViewerPath;
    private Popup npcFilterPopup;
    private Parent npcFilterPopupRoot;
    private String npcFilterExpandedColumn;
    private final Map<String, ToggleButton> npcFilterButtons = new LinkedHashMap<>();
    private final Map<String, List<SortKey>> npcSortChoices = new LinkedHashMap<>();
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
    void attach(WorkbenchProjectFlow flow, Stage ownerStage, WorkbenchPlatform platformAdapter,
                        WorkbenchAppearance.ThemeChoice initialChoice, ThemeChoiceSaver themeSaver,
                        Path settingsDirectory, Settings.InitializationResult settingsStartup,
                        GenerationPreferencesStore.MigrationPolicy migrationPolicy) {
        if (projectFlow != null)
            throw new IllegalStateException("WorkbenchController is already attached");
        projectFlow = Objects.requireNonNull(flow, "flow");
        templatesFeature = new TemplatesFeature(projectFlow, Clock.systemUTC());
        morphsFeature = new MorphsFeature(projectFlow, Clock.systemUTC());
        npcDatabaseFeature = new NpcDatabaseFeature();
        npcDatabaseImporter = new NpcDatabaseImporter(npcDatabaseFeature, projectFlow.jobs(), this::renderNpcDatabase);
        settingsFeature = new SettingsFeature(settingsDirectory, settingsStartup, migrationPolicy);
        outputFeature = new OutputFeature(projectFlow, () -> new OutputFeature.GenerationSettings(
                Settings.snapshot(), settingsFeature.frame().omitRedundantSliders()));
        stage = Objects.requireNonNull(ownerStage, "ownerStage");
        platform = Objects.requireNonNull(platformAdapter, "platformAdapter");
        configureProjectCommands();
        configureTemplates();
        configureMorphs();
        configureNpcDatabase();
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
            if (fillEmptyPopup != null)
                fillEmptyPopup.hide();
            if (npcFilterPopup != null)
                npcFilterPopup.hide();
            if (npcPortraitViewer != null)
                npcPortraitViewer.close();
            appearanceAdapter.close();
            jobSubscription.close();
            outputSubscription.close();
        });
        renderedProjectSequence = projectFlow.frame().sequence();
        renderNavigation(navigationFrame);
        render(projectFlow.frame());
        renderTemplates(templatesFeature.frame());
        renderMorphs(morphsFeature.frame());
        renderNpcDatabase(npcDatabaseFeature.frame());
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
     * Translates Morphs catalog and relationship controls into feature-specific typed intents.
     */
    private void configureMorphs() {
        customMorphTargetSort.getItems().setAll(MorphsFeature.SortOrder.values());
        customMorphTargetSort.setValue(morphsFeature.frame().sortOrder());
        configureCustomMorphTargetList();
        npcMorphAssignmentSort.getItems().setAll(MorphsFeature.NpcSortOrder.values());
        npcMorphAssignmentSort.setValue(morphsFeature.frame().npcSortOrder());
        configureNpcMorphAssignmentList();
        customMorphTargetFilter.textProperty().addListener((observable, previous, current) -> {
            if (!renderingMorphs)
                dispatchMorphs(new MorphsFeature.ChangeFilter(current));
        });
        customMorphTargetSort.setOnAction(event -> {
            if (!renderingMorphs && customMorphTargetSort.getValue() != null)
                dispatchMorphs(new MorphsFeature.ChangeSort(customMorphTargetSort.getValue()));
        });
        createCustomMorphTargetButton.setOnAction(event ->
                dispatchMorphs(new MorphsFeature.Create(customMorphTargetNameInput.getText())));
        removeCustomMorphTargetButton.setOnAction(event -> dispatchMorphs(new MorphsFeature.RequestRemove()));
        clearCustomMorphTargetsButton.setOnAction(event -> dispatchMorphs(new MorphsFeature.RequestClearVisible()));
        npcMorphAssignmentFilter.textProperty().addListener((observable, previous, current) -> {
            if (!renderingMorphs)
                dispatchMorphs(new MorphsFeature.ChangeNpcFilter(current));
        });
        npcMorphAssignmentSort.setOnAction(event -> {
            if (!renderingMorphs && npcMorphAssignmentSort.getValue() != null)
                dispatchMorphs(new MorphsFeature.ChangeNpcSort(npcMorphAssignmentSort.getValue()));
        });
        createNpcMorphAssignmentButton.setOnAction(event -> dispatchMorphs(new MorphsFeature.CreateNpc(
                npcDisplayNameInput.getText(), npcPluginNameInput.getText(), npcEditorIdInput.getText(),
                npcRaceInput.getText(), npcFormIdInput.getText())));
        removeNpcMorphAssignmentButton.setOnAction(event -> dispatchMorphs(new MorphsFeature.RequestRemoveNpc()));
        clearNpcMorphAssignmentsButton.setOnAction(event ->
                dispatchMorphs(new MorphsFeature.RequestClearVisibleNpcs()));
        fillEmptyNpcMorphAssignmentsButton.setOnAction(event ->
                dispatchMorphs(new MorphsFeature.RequestFillEmpty()));
        addNpcsFromDatabaseButton.setOnAction(event -> openNpcDatabaseFromMorphs());
        dismissMorphsInfoBarButton.setOnAction(event -> dispatchMorphs(new MorphsFeature.DismissDiagnostics()));
        openNpcPortraitViewerButton.setOnAction(event -> showNpcPortraitViewer());

        configureAssignedMorphSliderPresetList();
        availableMorphSliderPreset.setCellFactory(list -> new SliderPresetDisplayCell());
        availableMorphSliderPreset.setButtonCell(new SliderPresetDisplayCell());
        availableMorphSliderPreset.setOnAction(event -> assignMorphSliderPresetButton.setDisable(
                morphsMutationsBlocked || availableMorphSliderPreset.getValue() == null));
        assignMorphSliderPresetButton.setOnAction(event -> {
            SliderPresetSnapshot preset = availableMorphSliderPreset.getValue();
            if (preset != null)
                dispatchMorphs(new MorphsFeature.AssignSliderPreset(NameIdentity.of(preset.getName())));
        });
        assignAllMorphSliderPresetsButton.setOnAction(event ->
                dispatchMorphs(new MorphsFeature.AssignAllSliderPresets()));
        removeMorphSliderPresetButton.setOnAction(event ->
                dispatchMorphs(new MorphsFeature.RemoveAssignedSliderPreset()));
        clearMorphSliderPresetsButton.setOnAction(event ->
                dispatchMorphs(new MorphsFeature.RequestClearAssignments()));
    }

    /**
     * Opens a light-dismiss flyout for one frozen visible-empty NPC scope. Every dismissal path releases the token
     * and restores the launcher or the area's first editable control before another Morphs command can start.
     *
     * @param offer immutable NPC identities and eligible Project Slider Presets captured by MorphsFeature
     */
    private void showFillEmptyFlyout(MorphsFeature.FillEmptyOffer offer) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);
        // Popup's hideOnEscape does not intercept Escape from a focused ListView on the packaged JavaFX window.
        popup.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                event.consume();
            }
        });
        VBox content = new VBox(8.0);
        content.setId("workbenchRoot");
        content.getStyleClass().add("fill-empty-flyout");
        content.setAccessibleText("Fill Empty Slider Presets");
        Label title = new Label("Fill Empty NPC Morph Assignments");
        title.getStyleClass().add("title");
        Label scope = new Label(offer.emptyIdentities().size() + " visible empty NPC Morph Assignments");
        scope.setWrapText(true);
        Label instruction = new Label("Select at least one Slider Preset. Each empty NPC gets an independent choice.");
        instruction.setWrapText(true);
        instruction.setAccessibleText("Select at least one Slider Preset");
        ListView<SliderPresetSnapshot> choices = new ListView<>();
        choices.setId("fillEmptySliderPresetList");
        choices.setAccessibleText("Fill Empty Slider Presets");
        choices.setAccessibleHelp("Use arrows to focus a preset and Space to toggle it.");
        choices.setFocusTraversable(true);
        choices.setPrefHeight(Math.min(offer.eligiblePresets().size(), 8) * SLIDER_PRESET_CELL_HEIGHT + 8.0);
        choices.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        choices.getItems().setAll(offer.eligiblePresets());
        choices.setCellFactory(list -> new SliderPresetDisplayCell());
        choices.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.SPACE)
                return;
            int index = choices.getFocusModel().getFocusedIndex();
            if (index >= 0 && index < choices.getItems().size()) {
                if (choices.getSelectionModel().isSelected(index))
                    choices.getSelectionModel().clearSelection(index);
                else
                    choices.getSelectionModel().select(index);
                choices.getFocusModel().focus(index);
            }
            event.consume();
        });
        Button selectAll = new Button("Select All");
        selectAll.setAccessibleText("Select All Slider Presets");
        selectAll.setOnAction(event -> choices.getSelectionModel().selectAll());
        Button invert = new Button("Invert");
        invert.setAccessibleText("Invert Slider Preset selection");
        invert.setOnAction(event -> {
            for (int index = 0; index < choices.getItems().size(); index++) {
                if (choices.getSelectionModel().isSelected(index))
                    choices.getSelectionModel().clearSelection(index);
                else
                    choices.getSelectionModel().select(index);
            }
        });
        HBox selectionActions = new HBox(8.0, selectAll, invert);
        Button apply = new Button();
        apply.setId("fillEmptyApplyButton");
        Button cancel = new Button("Cancel");
        cancel.setId("fillEmptyCancelButton");
        cancel.setAccessibleText("Cancel Fill Empty");
        cancel.setOnAction(event -> popup.hide());
        Runnable refreshPrimary = () -> {
            int count = choices.getSelectionModel().getSelectedItems().size();
            String label = "Fill " + offer.emptyIdentities().size()
                    + (offer.emptyIdentities().size() == 1 ? " NPC" : " NPCs")
                    + " from " + count + (count == 1 ? " preset" : " presets");
            apply.setText(label);
            apply.setAccessibleText(label);
            apply.setDisable(count == 0);
            apply.setAccessibleHelp(count == 0 ? "Select at least one Slider Preset." :
                    "Fill only the captured visible empty NPC Morph Assignments without another confirmation.");
        };
        choices.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<SliderPresetSnapshot>) change -> refreshPrimary.run());
        refreshPrimary.run();
        apply.setOnAction(event -> {
            List<NameIdentity> selected = choices.getSelectionModel().getSelectedItems().stream()
                    .map(preset -> NameIdentity.of(preset.getName())).toList();
            MorphsFeature.Update response = morphsFeature.respondFillEmpty(offer.token(), selected);
            renderMorphsUpdate(response);
            publishMorphsOutcome(new MorphsFeature.RequestFillEmpty(), response);
            popup.hide();
        });
        HBox commitActions = new HBox(8.0, apply, cancel);
        content.getChildren().setAll(title, scope, instruction, choices, selectionActions, commitActions);
        popup.getContent().setAll(content);
        popup.getScene().getStylesheets().add(WorkbenchController.class
                .getResource("/com/asdasfa/jbs2bg/workbench.css").toExternalForm());
        fillEmptyPopup = popup;
        fillEmptyPopupRoot = content;
        applySatelliteAppearance(content);
        popup.setOnHidden(event -> {
            morphsFeature.cancelFillEmpty(offer.token());
            fillEmptyPopup = null;
            fillEmptyPopupRoot = null;
            if (stage.isShowing())
                Platform.runLater(this::restoreFillEmptyLauncherFocus);
        });
        Bounds anchor = fillEmptyNpcMorphAssignmentsButton.localToScreen(
                fillEmptyNpcMorphAssignmentsButton.getBoundsInLocal());
        popup.show(stage, anchor.getMinX(), anchor.getMaxY());
        Platform.runLater(choices::requestFocus);
    }

    /** Restores focus by semantic role when a responsive transition has hidden the former flyout launcher. */
    private void restoreFillEmptyLauncherFocus() {
        if (canRestoreFocus(fillEmptyNpcMorphAssignmentsButton))
            fillEmptyNpcMorphAssignmentsButton.requestFocus();
        else
            requestFocus(new WorkbenchNavigation.FocusTarget(navigationFrame.activeArea(),
                    WorkbenchNavigation.Landmark.PRIMARY_CONTENT));
    }

    /** An overlay or Area change can hide an ancestor while leaving the launcher's own visible bit unchanged. */
    private static boolean canRestoreFocus(Node node) {
        if (node.getScene() == null || node.isDisabled())
            return false;
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible())
                return false;
        }
        return true;
    }

    /**
     * Resolves the portrait for the stable selected NPC and starts decoding away from the JavaFX lane. Old load
     * callbacks compare image identity before touching the inspector so rapid selection cannot show a stale portrait.
     *
     * @param npc selected immutable NPC Morph Assignment, or null when no NPC is selected
     */
    private void renderNpcPortrait(NpcMorphAssignmentSnapshot npc) {
        NpcMorphAssignmentIdentity identity = npc == null ? null
                : new NpcMorphAssignmentIdentity(npc.getPluginName(), npc.getEditorId());
        String displayName = npc == null ? null : npc.getDisplayName();
        if (Objects.equals(identity, renderedPortraitIdentity)
                && Objects.equals(displayName, renderedPortraitDisplayName))
            return;
        renderedPortraitIdentity = identity;
        renderedPortraitDisplayName = displayName;
        renderedPortraitPath = null;
        renderedPortraitImage = null;
        npcPortraitImage.setImage(null);
        openNpcPortraitViewerButton.setDisable(true);
        if (npc == null) {
            npcPortraitStatus.setText("No NPC portrait selected");
            npcPortraitStatus.setAccessibleHelp("Select an NPC Morph Assignment to inspect its portrait.");
            return;
        }
        // The selected NPC is already named by the catalog and inspector; only the dedicated viewer names its image.
        Optional<Path> file = NpcPortraitFiles.find(Path.of("images"), npc);
        if (file.isEmpty()) {
            String expected = "images/" + npc.getDisplayName() + " (" + npc.getEditorId() + ").jpg";
            String status = "No portrait found. Expected " + expected
                    + " (or .jpeg, .png, .bmp), then a display-name-only image.";
            npcPortraitStatus.setText(status);
            npcPortraitStatus.setAccessibleHelp(status);
            return;
        }
        renderedPortraitPath = file.orElseThrow();
        String filename = renderedPortraitPath.getFileName().toString();
        Image image = new Image(renderedPortraitPath.toUri().toString(), 200.0, 180.0,
                true, true, true);
        renderedPortraitImage = image;
        npcPortraitImage.setImage(image);
        String loading = "Loading portrait: " + filename;
        npcPortraitStatus.setText(loading);
        npcPortraitStatus.setAccessibleHelp(loading);
        image.progressProperty().addListener((observable, previous, current) ->
                finishNpcPortraitLoad(image, filename));
        image.errorProperty().addListener((observable, previous, current) ->
                finishNpcPortraitLoad(image, filename));
        finishNpcPortraitLoad(image, filename);
    }

    /** Enables the viewer only after the current asynchronous thumbnail decoded successfully. */
    private void finishNpcPortraitLoad(Image image, String filename) {
        if (renderedPortraitImage != image)
            return;
        if (image.isError()) {
            npcPortraitImage.setImage(null);
            String status = "Could not load portrait: " + filename;
            npcPortraitStatus.setText(status);
            npcPortraitStatus.setAccessibleHelp(status);
        } else if (image.getProgress() >= 1.0 && image.getWidth() > 0.0) {
            String status = "Portrait file: " + filename;
            npcPortraitStatus.setText(status);
            npcPortraitStatus.setAccessibleHelp(status);
            openNpcPortraitViewerButton.setDisable(false);
        }
    }

    /** Follows stable NPC Database selection without allowing a prior asynchronous thumbnail to replace it. */
    private void renderNpcDatabasePortrait(NPC npc) {
        NpcMorphAssignmentIdentity identity = npc == null ? null : ProjectIdentities.npcDatabaseEntry(npc);
        String displayName = npc == null ? null : npc.getName();
        if (Objects.equals(identity, renderedDatabasePortraitIdentity)
                && Objects.equals(displayName, renderedDatabasePortraitDisplayName))
            return;
        renderedDatabasePortraitIdentity = identity;
        renderedDatabasePortraitDisplayName = displayName;
        renderedDatabasePortraitPath = null;
        renderedDatabasePortraitImage = null;
        npcDatabasePortraitImage.setImage(null);
        openNpcDatabasePortraitViewerButton.setDisable(true);
        if (npc == null) {
            npcDatabasePortraitStatus.setText("No NPC portrait selected");
            npcDatabasePortraitStatus.setAccessibleHelp("Select an NPC Database entry to inspect its portrait.");
            return;
        }
        Optional<Path> file = NpcPortraitFiles.find(Path.of("images"), npc.getName(), npc.getEditorId());
        if (file.isEmpty()) {
            String expected = "images/" + npc.getName() + " (" + npc.getEditorId() + ").jpg";
            String status = "No portrait found. Expected " + expected
                    + " (or .jpeg, .png, .bmp), then a display-name-only image.";
            npcDatabasePortraitStatus.setText(status);
            npcDatabasePortraitStatus.setAccessibleHelp(status);
            return;
        }
        renderedDatabasePortraitPath = file.orElseThrow();
        String filename = renderedDatabasePortraitPath.getFileName().toString();
        Image image = new Image(renderedDatabasePortraitPath.toUri().toString(), 200.0, 180.0,
                true, true, true);
        renderedDatabasePortraitImage = image;
        npcDatabasePortraitImage.setImage(image);
        String loading = "Loading portrait: " + filename;
        npcDatabasePortraitStatus.setText(loading);
        npcDatabasePortraitStatus.setAccessibleHelp(loading);
        image.progressProperty().addListener((observable, previous, current) ->
                finishNpcDatabasePortraitLoad(image, filename));
        image.errorProperty().addListener((observable, previous, current) ->
                finishNpcDatabasePortraitLoad(image, filename));
        finishNpcDatabasePortraitLoad(image, filename);
    }

    /** Enables the database viewer only for the current successfully decoded portrait. */
    private void finishNpcDatabasePortraitLoad(Image image, String filename) {
        if (renderedDatabasePortraitImage != image)
            return;
        if (image.isError()) {
            npcDatabasePortraitImage.setImage(null);
            String status = "Could not load portrait: " + filename;
            npcDatabasePortraitStatus.setText(status);
            npcDatabasePortraitStatus.setAccessibleHelp(status);
        } else if (image.getProgress() >= 1.0 && image.getWidth() > 0.0) {
            String status = "Portrait file: " + filename;
            npcDatabasePortraitStatus.setText(status);
            npcDatabasePortraitStatus.setAccessibleHelp(status);
            openNpcDatabasePortraitViewerButton.setDisable(false);
        }
    }

    /**
     * Opens the selected portrait in a keyboard-closeable, zoomable owned viewer while a full-resolution image loads
     * in the background. The filename and dimensions remain available as text for assistive technology.
     */
    private void showNpcPortraitViewer() {
        if (renderedPortraitPath == null || renderedPortraitIdentity == null || renderedPortraitImage == null
                || renderedPortraitImage.isError() || renderedPortraitImage.getProgress() < 1.0)
            return;
        showPortraitViewer(renderedPortraitPath, renderedPortraitDisplayName, renderedPortraitIdentity,
                openNpcPortraitViewerButton);
    }

    /** Opens the selected NPC Database portrait through the same accessible viewer used by Morphs. */
    private void showNpcDatabasePortraitViewer() {
        if (renderedDatabasePortraitPath == null || renderedDatabasePortraitIdentity == null
                || renderedDatabasePortraitImage == null || renderedDatabasePortraitImage.isError()
                || renderedDatabasePortraitImage.getProgress() < 1.0)
            return;
        showPortraitViewer(renderedDatabasePortraitPath, renderedDatabasePortraitDisplayName,
                renderedDatabasePortraitIdentity, openNpcDatabasePortraitViewerButton);
    }

    /** Builds one owned portrait window and restores focus to the visible launching Area when it closes. */
    private void showPortraitViewer(Path file, String displayName, NpcMorphAssignmentIdentity identity,
                                    Button launcher) {
        if (npcPortraitViewer != null && npcPortraitViewer.isShowing()) {
            if (file.equals(npcPortraitViewerPath)) {
                npcPortraitViewer.toFront();
                npcPortraitViewer.requestFocus();
                return;
            }
            // A different selected NPC needs its own image; the old viewer cannot be reused with stale content.
            npcPortraitViewer.close();
        }
        VBox content = new VBox(10.0);
        content.setId("workbenchRoot");
        content.getStyleClass().add("npc-portrait-viewer");
        content.setAccessibleText("NPC portrait viewer");
        Label title = new Label("NPC portrait: " + displayName);
        title.getStyleClass().add("title");
        Label filename = new Label("File: " + file.getFileName());
        filename.setWrapText(true);
        Label dimensions = new Label("Loading image dimensions…");
        dimensions.setAccessibleText("NPC portrait dimensions: loading");
        ImageView fullView = new ImageView();
        fullView.setAccessibleText("NPC portrait: " + displayName + ", plugin "
                + identity.getPluginName() + ", editor ID " + identity.getEditorId());
        fullView.setPreserveRatio(true);
        fullView.setSmooth(true);
        Image fullImage = new Image(file.toUri().toString(), true);
        fullView.setImage(fullImage);
        Runnable updateDimensions = () -> {
            if (fullImage.isError()) {
                dimensions.setText("Could not load portrait dimensions.");
                dimensions.setAccessibleText("NPC portrait dimensions unavailable");
            } else if (fullImage.getProgress() >= 1.0) {
                String size = (int) fullImage.getWidth() + " × " + (int) fullImage.getHeight() + " pixels";
                dimensions.setText(size);
                dimensions.setAccessibleText("NPC portrait dimensions: " + size);
            }
        };
        fullImage.progressProperty().addListener((observable, previous, current) -> updateDimensions.run());
        fullImage.errorProperty().addListener((observable, previous, current) -> updateDimensions.run());
        updateDimensions.run();
        ScrollPane imageScroll = new ScrollPane(fullView);
        imageScroll.setAccessibleText("NPC portrait image scroll area");
        imageScroll.setPannable(true);
        VBox.setVgrow(imageScroll, javafx.scene.layout.Priority.ALWAYS);
        Label zoomLabel = new Label("Zoom: 100%");
        Slider zoom = new Slider(0.25, 4.0, 1.0);
        zoom.setAccessibleText("NPC portrait zoom");
        zoom.setBlockIncrement(0.25);
        fullView.fitWidthProperty().bind(zoom.valueProperty().multiply(500.0));
        zoom.valueProperty().addListener((observable, previous, current) ->
                zoomLabel.setText("Zoom: " + Math.round(current.doubleValue() * 100.0) + "%"));
        Button close = new Button("Close");
        close.setAccessibleText("Close NPC portrait viewer");
        content.getChildren().setAll(title, filename, dimensions, imageScroll, zoomLabel, zoom, close);
        Stage viewer = new Stage();
        viewer.initOwner(stage);
        viewer.getIcons().setAll(stage.getIcons());
        viewer.setTitle("NPC Portrait — " + displayName);
        Scene scene = new Scene(content, 640.0, 560.0);
        scene.getStylesheets().add(WorkbenchController.class
                .getResource("/com/asdasfa/jbs2bg/workbench.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                viewer.close();
                event.consume();
            }
        });
        viewer.setScene(scene);
        viewer.setMinWidth(360.0);
        viewer.setMinHeight(300.0);
        close.setOnAction(event -> viewer.close());
        npcPortraitViewer = viewer;
        npcPortraitViewerRoot = content;
        npcPortraitViewerPath = file;
        applySatelliteAppearance(content);
        viewer.setOnHidden(event -> {
            if (npcPortraitViewer == viewer) {
                npcPortraitViewer = null;
                npcPortraitViewerRoot = null;
                npcPortraitViewerPath = null;
            }
            if (stage.isShowing())
                Platform.runLater(() -> {
                    if (canRestoreFocus(launcher))
                        launcher.requestFocus();
                    else
                        requestFocus(new WorkbenchNavigation.FocusTarget(navigationFrame.activeArea(),
                                WorkbenchNavigation.Landmark.PRIMARY_CONTENT));
                });
        });
        viewer.show();
        Platform.runLater(close::requestFocus);
    }

    /** Configures the current assigned-relationship ListView, including identity selection dispatch. */
    private void configureAssignedMorphSliderPresetList() {
        assignedMorphSliderPresetList.setCellFactory(list -> new SliderPresetDisplayCell());
        assignedMorphSliderPresetList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (!renderingMorphs)
                        dispatchMorphs(selected == null
                                ? new MorphsFeature.ClearAssignedSliderPresetSelection()
                                : new MorphsFeature.SelectAssignedSliderPreset(NameIdentity.of(selected.getName())));
                });
    }

    /** Configures the current target ListView instance; empty/refill UIA recovery may replace that adapter node. */
    private void configureCustomMorphTargetList() {
        configureCatalogHeight(customMorphTargetList, MORPH_TARGET_CELL_HEIGHT, MAX_VISIBLE_MORPH_TARGET_ROWS);
        customMorphTargetList.setCellFactory(list -> new ListCell<>() {
            /** {@inheritDoc} */
            @Override
            protected void updateItem(CustomMorphTargetSnapshot target, boolean empty) {
                super.updateItem(target, empty);
                if (empty || target == null) {
                    setText(null);
                    setAccessibleText(null);
                    setAccessibleHelp(null);
                    return;
                }
                int count = target.getSliderPresetNames().size();
                setText(target.getName());
                setAccessibleText(target.getName());
                setAccessibleHelp(count + (count == 1
                        ? " assigned Slider Preset" : " assigned Slider Presets")
                        + ". " + morphOutputStatus(count));
            }
        });
        customMorphTargetList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (!renderingMorphs)
                        dispatchMorphs(selected == null
                                ? new MorphsFeature.ClearSelection()
                                : new MorphsFeature.Select(NameIdentity.of(selected.getName())));
                });
        customMorphTargetList.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!(event.getTarget() instanceof TextInputControl) && event.getCharacter().length() == 1
                    && !event.isControlDown() && !event.isAltDown()) {
                dispatchMorphs(new MorphsFeature.TypeAhead(event.getCharacter().charAt(0)));
                event.consume();
            }
        });
    }

    /** Describes the Morphs line emitted even when a target or NPC has no Slider Preset assignments. */
    private static String morphOutputStatus(int assignmentCount) {
        return assignmentCount == 0
                ? "Included in Morphs output without Slider Preset assignments."
                : "Included in Morphs output.";
    }

    /** Configures the NPC catalog with identity-stable selection and keyboard type-ahead. */
    private void configureNpcMorphAssignmentList() {
        configureCatalogHeight(npcMorphAssignmentList, MORPH_TARGET_CELL_HEIGHT, MAX_VISIBLE_MORPH_TARGET_ROWS);
        npcMorphAssignmentList.setCellFactory(list -> new ListCell<>() {
            /** {@inheritDoc} */
            @Override
            protected void updateItem(NpcMorphAssignmentSnapshot npc, boolean empty) {
                super.updateItem(npc, empty);
                if (empty || npc == null) {
                    setText(null);
                    setAccessibleText(null);
                    setAccessibleHelp(null);
                    return;
                }
                int count = npc.getSliderPresetNames().size();
                setText(npc.getDisplayName() + " (" + npc.getPluginName() + " | " + npc.getEditorId() + ")");
                setAccessibleText(npc.getDisplayName() + ". Plugin: " + npc.getPluginName()
                        + ". Editor ID: " + npc.getEditorId() + ". Race: " + npc.getRace()
                        + ". Form ID: " + npc.getFormId() + ".");
                setAccessibleHelp(count + (count == 1
                        ? " assigned Slider Preset" : " assigned Slider Presets")
                        + ". " + morphOutputStatus(count));
            }
        });
        npcMorphAssignmentList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (!renderingMorphs)
                        dispatchMorphs(selected == null
                                ? new MorphsFeature.ClearSelection()
                                : new MorphsFeature.SelectNpc(new NpcMorphAssignmentIdentity(
                                        selected.getPluginName(), selected.getEditorId())));
                });
        npcMorphAssignmentList.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!(event.getTarget() instanceof TextInputControl) && event.getCharacter().length() == 1
                    && !event.isControlDown() && !event.isAltDown()) {
                dispatchMorphs(new MorphsFeature.NpcTypeAhead(event.getCharacter().charAt(0)));
                event.consume();
            }
        });
    }

    /** Connects source management, column filtering, catalog sorting, and selection to one window-scoped catalog. */
    private void configureNpcDatabase() {
        npcSourceList.setCellFactory(list -> new ListCell<>() {
            /** {@inheritDoc} */
            @Override
            protected void updateItem(NpcDatabaseFeature.Source source, boolean empty) {
                super.updateItem(source, empty);
                if (empty || source == null) {
                    setText(null);
                    setAccessibleText(null);
                    setAccessibleHelp(null);
                    return;
                }
                String count = source.rowCount() + (source.rowCount() == 1 ? " row" : " rows");
                setText(source.path().getFileName() + " — " + count);
                setAccessibleText(source.path().getFileName() + ", " + count);
                setAccessibleHelp("Source file: " + source.path());
            }
        });
        npcSourceList.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (!renderingNpcDatabase) {
                npcDatabaseFeature.selectSource(selected == null ? Optional.empty() : Optional.of(selected.path()));
                renderNpcDatabase(npcDatabaseFeature.frame());
            }
        });
        npcNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        npcMasterColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getMod()));
        npcRaceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getRace()));
        npcEditorIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getEditorId()));
        npcFormIdColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFormId()));
        npcCatalogTable.setRowFactory(table -> new TableRow<>() {
            /** {@inheritDoc} */
            @Override
            protected void updateItem(NPC npc, boolean empty) {
                super.updateItem(npc, empty);
                setAccessibleText(empty || npc == null ? null : npc.getName() + ". Plugin: " + npc.getMod()
                        + ". Editor ID: " + npc.getEditorId() + ". Race: " + npc.getRace()
                        + ". Form ID: " + npc.getFormId() + ".");
            }
        });
        npcCatalogTable.setSortPolicy(table -> {
            if (renderingNpcDatabase)
                return true;
            List<SortKey> keys = new ArrayList<>();
            for (TableColumn<NPC, ?> column : table.getSortOrder()) {
                keys.add(column.getSortType() == TableColumn.SortType.DESCENDING
                        ? SortKey.descending(column.getText()) : SortKey.ascending(column.getText()));
            }
            npcDatabaseFeature.setSortOrder(keys);
            renderNpcDatabase(npcDatabaseFeature.frame());
            return true;
        });
        npcCatalogTable.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (!renderingNpcDatabase) {
                if (selected == null)
                    npcDatabaseFeature.clearSelection();
                else
                    npcDatabaseFeature.selectRow(ProjectIdentities.npcDatabaseEntry(selected));
                renderNpcDatabase(npcDatabaseFeature.frame());
            }
        });
        npcCatalogTable.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (event.getCharacter().length() == 1 && !event.isControlDown() && !event.isAltDown()
                    && !Character.isISOControl(event.getCharacter().charAt(0))
                    && !Character.isWhitespace(event.getCharacter().charAt(0))) {
                NpcDatabaseFeature.Frame typed = npcDatabaseFeature.typeAhead(
                        event.getCharacter().charAt(0), System.nanoTime());
                renderNpcDatabase(typed);
                typed.selectedRow().ifPresent(row -> npcCatalogTable.scrollTo(typed.visibleRows().indexOf(row)));
                event.consume();
            }
        });
        npcCatalogTable.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                promoteNpcDatabase(false);
                event.consume();
            }
        });
        npcSortChoices.put("Source order", List.of());
        for (FilterColumn<NPC> column : NpcTableColumns.npcDatabase()) {
            npcSortChoices.put(column.getId() + " ascending", List.of(SortKey.ascending(column.getId())));
            npcSortChoices.put(column.getId() + " descending", List.of(SortKey.descending(column.getId())));
        }
        npcSortChoice.getItems().setAll(npcSortChoices.keySet());
        npcSortChoice.setAccessibleHelp("NPC Database sort: source order or any catalog column ascending or descending.");
        npcSortChoice.setOnAction(event -> {
            if (!renderingNpcDatabase && npcSortChoices.containsKey(npcSortChoice.getValue())) {
                npcDatabaseFeature.setSortOrder(npcSortChoices.get(npcSortChoice.getValue()));
                renderNpcDatabase(npcDatabaseFeature.frame());
            }
        });
        npcFilterButtons.put("Name", npcNameFilterButton);
        npcFilterButtons.put("Master", npcMasterFilterButton);
        npcFilterButtons.put("Race", npcRaceFilterButton);
        npcFilterButtons.put("EditorID", npcEditorIdFilterButton);
        npcFilterButtons.put("FormID", npcFormIdFilterButton);
        npcFilterButtons.forEach((column, button) ->
                button.setOnAction(event -> showNpcColumnFilter(column, button)));
        clearNpcFiltersButton.setOnAction(event -> {
            npcDatabaseFeature.clearAllCriteria();
            renderNpcDatabase(npcDatabaseFeature.frame());
        });
        importNpcSourcesButton.setOnAction(event -> chooseNpcSources());
        removeNpcSourceButton.setOnAction(event -> removeSelectedNpcSource());
        clearNpcDatabaseButton.setOnAction(event -> clearVisibleNpcDatabaseEntries());
        addNpcToProjectButton.setOnAction(event -> promoteNpcDatabase(false));
        addAllNpcsToProjectButton.setOnAction(event -> promoteNpcDatabase(true));
        backToMorphsButton.setOnAction(event -> backToMorphs());
        dismissNpcPromotionInfoBarButton.setOnAction(event -> {
            npcDatabaseFeature.dismissPromotionReport();
            renderNpcDatabase(npcDatabaseFeature.frame());
        });
        assignRandomNpcPresetCheck.setOnAction(event -> {
            if (!renderingNpcDatabase) {
                npcDatabaseFeature.setAssignRandom(assignRandomNpcPresetCheck.isSelected());
                renderNpcDatabase(npcDatabaseFeature.frame());
            }
        });
        openNpcDatabasePortraitViewerButton.setOnAction(event -> showNpcDatabasePortraitViewer());
        renderNpcDatabase(npcDatabaseFeature.frame());
    }

    /** Opens the catalog from its Morphs launcher with a semantic return target. */
    private void openNpcDatabaseFromMorphs() {
        applyNavigation(navigation.navigate(WorkbenchNavigation.Destination.NPC_DATABASE,
                new WorkbenchNavigation.FocusTarget(WorkbenchNavigation.Area.MORPHS,
                        WorkbenchNavigation.Landmark.NPC_DATABASE_LAUNCHER)));
    }

    /**
     * Returns to the Morphs launcher and selects the last added identity when it is still visible.
     * Project and feature frames are already committed before navigation realizes the focus effect.
     */
    private void backToMorphs() {
        npcDatabaseFeature.consumeReturnAssignment().ifPresent(identity -> renderMorphsUpdate(
                morphsFeature.dispatch(new MorphsFeature.SelectNpc(identity))));
        NpcMorphAssignmentSnapshot selected = npcMorphAssignmentList.getSelectionModel().getSelectedItem();
        if (selected != null)
            npcMorphAssignmentList.scrollTo(selected);
        applyNavigation(navigation.backToMorphs());
    }

    /** Promotes captured NPCs through ProjectSession, then reports the complete operation at its UI tier. */
    private void promoteNpcDatabase(boolean bulk) {
        if (projectFlow.jobs().frame().active() || projectFlow.jobs().frame().shutdownRequested())
            return;
        NpcDatabaseFeature.PromotionReport report = bulk
                ? npcDatabaseFeature.promoteVisible(projectFlow)
                : npcDatabaseFeature.promoteSelected(projectFlow);
        // NPC Database owns these diagnostics and task wording. Suppress the generic lifecycle feedback while
        // Project chrome and other Areas reconcile the final snapshot after promotion has published.
        templatesOwnProjectDiagnostics = true;
        morphsOwnProjectDiagnostics = true;
        try {
            WorkbenchProjectFlow.Frame projectFrame = projectFlow.frame();
            renderedProjectSequence = projectFrame.sequence();
            // The bulk report presents every row diagnostic locally; projecting the Project frame here would
            // expose either a final row's diagnostic or the same aggregate on an unrelated global surface.
            render(projectFrame, bulk ? List.of() : projectFrame.diagnostics());
        } finally {
            templatesOwnProjectDiagnostics = false;
            morphsOwnProjectDiagnostics = false;
        }
        renderNpcDatabase(npcDatabaseFeature.frame());
        boolean issues = report.rejectedCount() > 0 || report.duplicateCount() > 0
                || report.entries().isEmpty();
        WorkbenchFeedback.Severity severity = promotionSeverity(report);
        WorkbenchFeedback.Disposition disposition = issues
                ? WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES
                : WorkbenchFeedback.Disposition.COMPLETED;
        WorkbenchFeedback.Notification notification = new WorkbenchFeedback.Notification(
                bulk ? "Add All NPCs to Project" : "Add NPC to Project", severity,
                promotionSummary(report), disposition);
        if (bulk) {
            String details = promotionDetails(report);
            renderFeedback(details.isEmpty() ? feedback.publishActivity(notification)
                    : feedback.publishActivityDetailed(notification, details));
        } else {
            renderFeedback(feedback.publishStatus(notification));
        }
    }

    /** Builds a concise outcome summary; the inline details retain every duplicate and rejection. */
    private static String promotionSummary(NpcDatabaseFeature.PromotionReport report) {
        if (report.entries().isEmpty())
            return report.bulk() ? "No visible NPC Database entries to add."
                    : "Select an NPC Database entry to add.";
        if (!report.bulk()) {
            NpcDatabaseFeature.PromotionEntry entry = report.entries().getFirst();
            return switch (entry.status()) {
                case ADDED -> "Added " + entry.displayName() + " to the Project.";
                case DUPLICATE -> "Already in the Project: " + entry.displayName() + ".";
                case REJECTED, FAILED -> "Could not add " + entry.displayName() + ": "
                        + ProjectDiagnosticFormatter.format(entry.diagnostics());
                case UNCHANGED -> "No Project change for " + entry.displayName() + ".";
            };
        }
        return "Added " + report.addedCount() + " NPC Morph Assignments; " + report.duplicateCount()
                + " already in the Project; " + report.rejectedCount() + " rejected.";
    }

    /** Keeps pane, status, and Activity cues aligned for the same promotion outcome. */
    private static WorkbenchFeedback.Severity promotionSeverity(NpcDatabaseFeature.PromotionReport report) {
        if (report.entries().stream().anyMatch(entry ->
                entry.status() == NpcDatabaseFeature.PromotionStatus.FAILED))
            return WorkbenchFeedback.Severity.FAILURE;
        if (report.rejectedCount() > 0)
            return WorkbenchFeedback.Severity.WARNING;
        return report.duplicateCount() > 0 || report.entries().isEmpty()
                ? WorkbenchFeedback.Severity.VALIDATION : WorkbenchFeedback.Severity.SUCCESS;
    }

    /** Formats every non-added identity with its Project diagnostic for the scrollable inline report. */
    private static String promotionDetails(NpcDatabaseFeature.PromotionReport report) {
        List<String> details = new ArrayList<>();
        for (NpcDatabaseFeature.PromotionEntry entry : report.entries()) {
            if (entry.status() == NpcDatabaseFeature.PromotionStatus.ADDED)
                continue;
            String identity = entry.identity().getPluginName() + " / " + entry.identity().getEditorId();
            String diagnostic = entry.diagnostics().isEmpty() ? entry.status().name()
                    : ProjectDiagnosticFormatter.format(entry.diagnostics());
            details.add(identity + " — " + diagnostic);
        }
        return String.join(System.lineSeparator(), details);
    }

    /** Opens a keyboard checklist for one named column; its choices come from all catalog rows. */
    private void showNpcColumnFilter(String columnId, ToggleButton launcher) {
        if (npcFilterPopup != null) {
            boolean sameColumn = columnId.equals(npcFilterExpandedColumn);
            npcFilterPopup.hide();
            if (sameColumn)
                return;
        }
        FilterColumn<NPC> column = NpcTableColumns.npcDatabase().stream()
                .filter(candidate -> candidate.getId().equals(columnId)).findFirst().orElseThrow();
        ColumnCriterion criterion = npcDatabaseFeature.frame().criteria().stream()
                .filter(candidate -> candidate.getColumnId().equals(columnId)).findFirst().orElse(null);
        Set<String> values = new TreeSet<>();
        for (NPC row : npcDatabaseFeature.frame().rows())
            values.add(column.cellValueOf(row));
        List<NpcFilterChoice> choices = values.stream()
                .map(value -> new NpcFilterChoice(value, criterion == null || criterion.admits(value))).toList();
        ListView<NpcFilterChoice> checklist = new ListView<>(FXCollections.observableArrayList(choices));
        checklist.setId("npcColumnFilterChoices");
        checklist.setAccessibleText(columnId + " NPC Database filter choices");
        checklist.setCellFactory(CheckBoxListCell.forListView(NpcFilterChoice::selectedProperty));
        checklist.setPrefHeight(210.0);
        checklist.setPrefWidth(245.0);
        Button all = new Button("All");
        all.setAccessibleText("Show all " + columnId + " values");
        all.setOnAction(event -> choices.forEach(choice -> choice.setSelected(true)));
        Button none = new Button("None");
        none.setAccessibleText("Hide all " + columnId + " values");
        none.setOnAction(event -> choices.forEach(choice -> choice.setSelected(false)));
        Button apply = new Button("Apply");
        apply.setAccessibleText("Apply " + columnId + " filter");
        Button cancel = new Button("Cancel");
        cancel.setAccessibleText("Cancel " + columnId + " filter");
        HBox actions = new HBox(6.0, all, none, apply, cancel);
        VBox content = new VBox(8.0, new Label("Filter " + columnId), checklist, actions);
        content.setId("workbenchRoot");
        content.getStyleClass().add("npc-column-filter-popup");
        content.setAccessibleText(columnId + " NPC Database filter checklist");
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setConsumeAutoHidingEvents(true);
        popup.getContent().setAll(content);
        popup.getScene().getStylesheets().add(WorkbenchController.class
                .getResource("/com/asdasfa/jbs2bg/workbench.css").toExternalForm());
        apply.setOnAction(event -> {
            List<String> hidden = choices.stream().filter(choice -> !choice.isSelected())
                    .map(NpcFilterChoice::value).toList();
            npcDatabaseFeature.setCriterion(ColumnCriterion.hiding(columnId, hidden));
            renderNpcDatabase(npcDatabaseFeature.frame());
            popup.hide();
        });
        cancel.setOnAction(event -> popup.hide());
        checklist.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE && checklist.getSelectionModel().getSelectedItem() != null) {
                NpcFilterChoice choice = checklist.getSelectionModel().getSelectedItem();
                choice.setSelected(!choice.isSelected());
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                apply.fire();
                event.consume();
            }
        });
        // The focused ListView may consume Escape before Popup hideOnEscape sees it in the packaged runtime.
        popup.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                event.consume();
            }
        });
        npcFilterPopup = popup;
        npcFilterPopupRoot = content;
        npcFilterExpandedColumn = columnId;
        applySatelliteAppearance(content);
        renderNpcFilterButtons(npcDatabaseFeature.frame());
        popup.setOnHidden(event -> {
            npcFilterPopup = null;
            npcFilterPopupRoot = null;
            npcFilterExpandedColumn = null;
            renderNpcFilterButtons(npcDatabaseFeature.frame());
            if (stage.isShowing())
                Platform.runLater(() -> {
                    if (canRestoreFocus(launcher))
                        launcher.requestFocus();
                    else
                        requestFocus(new WorkbenchNavigation.FocusTarget(navigationFrame.activeArea(),
                                WorkbenchNavigation.Landmark.EDITOR));
                });
        });
        Bounds anchor = launcher.localToScreen(launcher.getBoundsInLocal());
        popup.show(stage, anchor.getMinX(), anchor.getMaxY());
        checklist.getSelectionModel().selectFirst();
        Platform.runLater(checklist::requestFocus);
    }

    /** Keeps each filter button's text, toggle state, and expanded/active description in sync with the feature. */
    private void renderNpcFilterButtons(NpcDatabaseFeature.Frame frame) {
        npcFilterButtons.forEach((column, button) -> {
            boolean active = frame.criteria().stream().anyMatch(criterion ->
                    criterion.getColumnId().equals(column));
            boolean expanded = npcFilterPopup != null && column.equals(npcFilterExpandedColumn);
            button.setText(column + (active ? " *" : ""));
            button.setSelected(expanded);
            button.setAccessibleHelp(column + " column filter, " + (active ? "active" : "inactive")
                    + ", " + (expanded ? "expanded" : "collapsed")
                    + ". Enter or Space opens the checklist."
                    + (frame.rows().isEmpty() ? " No catalog values yet." : ""));
        });
    }

    /** One checklist cell whose selected state means its exact column value stays visible. */
    private static final class NpcFilterChoice {
        private final String value;
        private final BooleanProperty selected;

        /** Captures one exact visible column value for an open filter checklist. */
        private NpcFilterChoice(String value, boolean selected) {
            this.value = Objects.requireNonNull(value, "value");
            this.selected = new SimpleBooleanProperty(selected);
        }

        /** @return exact cell value */
        private String value() {
            return value;
        }

        /** @return whether this value remains visible if the checklist is applied */
        private boolean isSelected() {
            return selected.get();
        }

        /** Changes this pending checklist choice without affecting the catalog until Apply. */
        private void setSelected(boolean selected) {
            this.selected.set(selected);
        }

        /** @return JavaFX property used only by the checkbox cell */
        private BooleanProperty selectedProperty() {
            return selected;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return value;
        }
    }

    /** Renders the immutable catalog frame while suppressing selection echoes from JavaFX list replacements. */
    private void renderNpcDatabase(NpcDatabaseFeature.Frame frame) {
        renderingNpcDatabase = true;
        try {
            if (!npcSourceList.getItems().equals(frame.sources()))
                npcSourceList.getItems().setAll(frame.sources());
            NpcDatabaseFeature.Source selectedSource = npcSourceList.getSelectionModel().getSelectedItem();
            Optional<Path> currentSource = selectedSource == null ? Optional.empty()
                    : Optional.of(selectedSource.path());
            if (!currentSource.equals(frame.selectedSource())) {
                npcSourceList.getSelectionModel().clearSelection();
                frame.selectedSource().ifPresent(path -> frame.sources().stream()
                        .filter(source -> source.path().equals(path)).findFirst()
                        .ifPresent(npcSourceList.getSelectionModel()::select));
            }
            if (!npcCatalogTable.getItems().equals(frame.visibleRows()))
                npcCatalogTable.getItems().setAll(frame.visibleRows());
            List<TableColumn<NPC, ?>> sortedColumns = new ArrayList<>();
            for (SortKey key : frame.sortOrder()) {
                TableColumn<NPC, ?> column = npcCatalogTable.getColumns().stream()
                        .filter(candidate -> candidate.getText().equals(key.getColumnId()))
                        .findFirst().orElseThrow();
                column.setSortType(key.getDirection() == SortDirection.DESCENDING
                        ? TableColumn.SortType.DESCENDING : TableColumn.SortType.ASCENDING);
                sortedColumns.add(column);
            }
            if (!npcCatalogTable.getSortOrder().equals(sortedColumns))
                npcCatalogTable.getSortOrder().setAll(sortedColumns);
            String sortLabel = npcSortChoices.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(frame.sortOrder()))
                    .map(Map.Entry::getKey).findFirst().orElse("Multiple columns");
            npcSortChoice.setValue(sortLabel);
            NPC selectedTableRow = npcCatalogTable.getSelectionModel().getSelectedItem();
            Optional<NpcMorphAssignmentIdentity> currentRow = selectedTableRow == null ? Optional.empty()
                    : Optional.of(ProjectIdentities.npcDatabaseEntry(selectedTableRow));
            Optional<NpcMorphAssignmentIdentity> selectedRow = frame.selectedRow()
                    .map(ProjectIdentities::npcDatabaseEntry);
            if (!currentRow.equals(selectedRow)) {
                npcCatalogTable.getSelectionModel().clearSelection();
                frame.selectedRow().ifPresent(selected -> npcCatalogTable.getItems().stream()
                        .filter(row -> ProjectIdentities.npcDatabaseEntry(row)
                                .equals(ProjectIdentities.npcDatabaseEntry(selected)))
                        .findFirst().ifPresent(npcCatalogTable.getSelectionModel()::select));
            }
            renderNpcDatabaseLabel(npcSourceCountText, frame.sources().size() + (frame.sources().size() == 1
                    ? " source loaded" : " sources loaded"));
            String summary = frame.visibleRows().size() + " of " + frame.rows().size()
                    + " NPC Database entries visible; " + frame.criteria().size() + " filtered columns.";
            renderNpcDatabaseLabel(npcCatalogSummary, summary);
            npcCatalogTable.setAccessibleHelp(summary + " Select a row to inspect its details.");
            boolean blocked = projectFlow.jobs().frame().active() || projectFlow.jobs().frame().shutdownRequested();
            assignRandomNpcPresetCheck.setSelected(frame.assignRandom());
            importNpcSourcesButton.setDisable(blocked);
            removeNpcSourceButton.setDisable(blocked || frame.selectedSource().isEmpty());
            clearNpcDatabaseButton.setDisable(blocked || frame.visibleRows().isEmpty());
            addNpcToProjectButton.setDisable(blocked || frame.selectedRow().isEmpty());
            addAllNpcsToProjectButton.setDisable(blocked || frame.visibleRows().isEmpty());
            assignRandomNpcPresetCheck.setDisable(blocked);
            clearNpcFiltersButton.setDisable(frame.criteria().isEmpty());
            renderNpcFilterButtons(frame);
            NPC selected = frame.selectedRow().orElse(null);
            renderNpcDatabaseLabel(npcInspectorName, selected == null ? "No NPC selected" : selected.getName());
            renderNpcDatabaseLabel(npcInspectorSource, "Source: " + (selected == null ? "none"
                    : frame.sourceOf(selected).map(Path::toString).orElse("unknown")));
            renderNpcDatabaseLabel(npcInspectorMaster, "Plugin: " + (selected == null ? "none" : selected.getMod()));
            renderNpcDatabaseLabel(npcInspectorEditorId,
                    "Editor ID: " + (selected == null ? "none" : selected.getEditorId()));
            renderNpcDatabaseLabel(npcInspectorRace, "Race: " + (selected == null ? "none" : selected.getRace()));
            renderNpcDatabaseLabel(npcInspectorFormId,
                    "Form ID: " + (selected == null ? "none" : selected.getFormId()));
            renderNpcDatabasePortrait(selected);
            renderNpcDatabaseLabel(npcDatabaseDiagnostics, frame.diagnostics().isEmpty() ? "No source diagnostics"
                    : String.join(System.lineSeparator(), frame.diagnostics().stream()
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.source()
                            + (diagnostic.line().isPresent() ? ":" + diagnostic.line().orElseThrow() : "")
                            + " — " + diagnostic.message()).toList()));
            NpcDatabaseFeature.PromotionReport promotion = frame.promotionReport().orElse(null);
            boolean showPromotion = promotion != null && (promotion.bulk() || promotion.entries().isEmpty()
                    || promotion.addedCount() != 1);
            npcPromotionInfoBar.setManaged(showPromotion);
            npcPromotionInfoBar.setVisible(showPromotion);
            if (showPromotion) {
                WorkbenchFeedback.Severity severity = promotionSeverity(promotion);
                String message = promotionSummary(promotion);
                npcPromotionInfoBarCue.setText(severity.cue());
                npcPromotionInfoBarMessage.setText(message);
                npcPromotionInfoBar.setAccessibleHelp(severity.cue() + ": " + message);
                setSeverityStyle(npcPromotionInfoBar, severity);
            }
            String details = promotion == null ? "" : promotionDetails(promotion);
            npcPromotionDetails.setText(details);
            npcPromotionDetails.setAccessibleHelp(details);
            npcPromotionDetails.setManaged(!details.isEmpty());
            npcPromotionDetails.setVisible(!details.isEmpty());
        } finally {
            renderingNpcDatabase = false;
        }
    }

    /** Exposes a dynamic label value through UI Automation HelpText while its stable accessible name stays locatable. */
    private static void renderNpcDatabaseLabel(Label label, String value) {
        label.setText(value);
        label.setAccessibleHelp(value);
    }

    /** Captures chooser paths once and admits their source-level transaction batch to the central coordinator. */
    private void chooseNpcSources() {
        Optional<List<Path>> selected = platform.chooseNpcSources(stage);
        if (selected.isEmpty() || selected.orElseThrow().isEmpty()) {
            renderFeedback(feedback.publishActivity(new WorkbenchFeedback.Notification(
                    "Import NPC Database Sources", WorkbenchFeedback.Severity.INFORMATION,
                    "NPC Database import cancelled before reading sources.", WorkbenchFeedback.Disposition.CANCELLED)));
            return;
        }
        List<Path> sources = selected.orElseThrow().stream().map(path -> path.toAbsolutePath().normalize()).toList();
        JobCoordinator.Admission admission = npcDatabaseImporter.submit(sources);
        if (!admission.admitted())
            renderFeedback(feedback.publishStatus(new WorkbenchFeedback.Notification(
                    "Import NPC Database Sources", WorkbenchFeedback.Severity.VALIDATION,
                    "Another operation is active; retry the import when it finishes.",
                    WorkbenchFeedback.Disposition.FAILED)));
    }

    /** Removes one selected source after a typed destructive confirmation. */
    private void removeSelectedNpcSource() {
        Optional<Path> selected = npcDatabaseFeature.frame().selectedSource();
        if (selected.isEmpty() || projectFlow.jobs().frame().active())
            return;
        Path source = selected.orElseThrow();
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.destructiveAction(
                "Remove NPC Database source?", "Remove " + source.getFileName() + " from this session's catalog?",
                WorkbenchFeedback.DialogAction.REMOVE);
        if (!confirmNpcDatabaseAction(spec, WorkbenchFeedback.DialogAction.REMOVE))
            return;
        npcDatabaseFeature.removeSource(source);
        renderNpcDatabase(npcDatabaseFeature.frame());
        renderFeedback(feedback.publishActivity(new WorkbenchFeedback.Notification(
                "Remove NPC Database Source", WorkbenchFeedback.Severity.SUCCESS,
                "Removed source " + source.getFileName() + ".", WorkbenchFeedback.Disposition.COMPLETED)));
    }

    /** Clears exactly the visible identities captured before the confirmation dialog. */
    private void clearVisibleNpcDatabaseEntries() {
        if (projectFlow.jobs().frame().active())
            return;
        List<NpcMorphAssignmentIdentity> identities = npcDatabaseFeature.visibleSet().getIdentities();
        if (identities.isEmpty())
            return;
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.destructiveAction(
                "Clear visible NPC Database entries?",
                "Clear " + identities.size() + " visible entries from this session's catalog?",
                WorkbenchFeedback.DialogAction.CLEAR);
        if (!confirmNpcDatabaseAction(spec, WorkbenchFeedback.DialogAction.CLEAR))
            return;
        npcDatabaseFeature.clearEntries(identities);
        renderNpcDatabase(npcDatabaseFeature.frame());
        renderFeedback(feedback.publishActivity(new WorkbenchFeedback.Notification(
                "Clear visible NPC Database entries", WorkbenchFeedback.Severity.SUCCESS,
                "Cleared " + identities.size() + " visible entries.", WorkbenchFeedback.Disposition.COMPLETED)));
    }

    /** Publishes and completes one source-management confirmation while retaining its launcher focus. */
    private boolean confirmNpcDatabaseAction(WorkbenchFeedback.DialogSpec spec,
                                             WorkbenchFeedback.DialogAction acceptance) {
        WorkbenchNavigation.FocusTarget returnTarget = currentSemanticFocus();
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(spec);
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        renderFeedback(pendingFrame);
        WorkbenchFeedback.DialogAction action = platform.completeConfirmation(spec, stage);
        renderFeedback(feedback.answerDialog(new WorkbenchFeedback.DialogResult(pending.token(), action)).frame());
        requestFocus(returnTarget);
        return action == acceptance;
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
        copyOutputButton.setOnAction(event -> dispatchOutput(new OutputFeature.Copy()));
        exportOutputButton.setOnAction(event -> dispatchOutput(new OutputFeature.Export()));
        exportSelectedBosButton.setOnAction(event -> dispatchOutput(new OutputFeature.ExportSelected()));
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
        if (intent instanceof OutputFeature.Generate && !settingsFeature.frame().liveAvailable()) {
            // Keyboard generation bypasses the disabled button, so enforce the same Settings prerequisite here.
            return;
        }
        OutputFeature.Update update = outputFeature.dispatch(Objects.requireNonNull(intent, "intent"));
        renderOutput(update.frame());
        update.effect().ifPresent(this::applyOutputEffect);
    }

    /** Applies one committed Output publication and consumes its optional drawer reveal exactly once. */
    private void renderOutputUpdate(OutputFeature.Update update) {
        renderOutput(Objects.requireNonNull(update, "update").frame());
        update.effect().ifPresent(this::applyOutputEffect);
    }

    /** Realizes one typed Output platform or navigation effect without reading preview-control state. */
    private void applyOutputEffect(OutputFeature.Effect effect) {
        switch (Objects.requireNonNull(effect, "effect")) {
            case OutputFeature.RevealDrawer ignored -> applyNavigation(navigation.revealOutput());
            case OutputFeature.CopyToClipboard copy -> publishCopyOutcome(copy,
                    platform.copyOutputText(copy.text()));
            case OutputFeature.ChooseExportDirectory chooser -> renderOutput(
                    outputFeature.completeExport(chooser.token(), platform.chooseOutputDirectory(stage)).frame());
            case OutputFeature.ChooseExportFile chooser -> renderOutput(outputFeature.completeSelectedExport(
                    chooser.token(), platform.chooseOutputFile(chooser.suggestedFileName(), stage)).frame());
        }
    }

    /** Publishes non-modal copy success or failure through the Workbench InfoBar and durable Activity path. */
    private void publishCopyOutcome(OutputFeature.CopyToClipboard copy, boolean copied) {
        WorkbenchFeedback.Notification notification = copied
                ? new WorkbenchFeedback.Notification("Copy Output", WorkbenchFeedback.Severity.SUCCESS,
                copy.artifactName() + " copied to the clipboard.", WorkbenchFeedback.Disposition.COMPLETED)
                : new WorkbenchFeedback.Notification("Copy Output", WorkbenchFeedback.Severity.FAILURE,
                copy.artifactName() + " could not be copied to the clipboard.",
                WorkbenchFeedback.Disposition.FAILED);
        renderFeedback(feedback.publish(notification));
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
                bosOutputText.setText(frame.selectedBosText());
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
            boolean accepted = frame.freshness() == OutputFeature.Freshness.FRESH;
            boolean jobBlocked = projectFlow.jobs().frame().active()
                    || projectFlow.jobs().frame().shutdownRequested();
            copyOutputButton.setDisable(!accepted || frame.displayedArtifact().isEmpty());
            exportOutputButton.setDisable(!accepted || jobBlocked);
            exportSelectedBosButton.setDisable(!accepted || jobBlocked
                    || frame.selectedTab() != OutputFeature.Tab.BOS_JSON || frame.displayedArtifact().isEmpty());
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

    /** Commits a typed Morphs update before rendering Project chrome or later platform effects. */
    private void dispatchMorphs(MorphsFeature.Intent intent) {
        if (morphsMutationsBlocked && isMorphsMutation(intent))
            return;
        morphsOwnProjectDiagnostics = true;
        try {
            MorphsFeature.Update update = morphsFeature.dispatch(Objects.requireNonNull(intent, "intent"));
            renderMorphsUpdate(update);
            // The confirmed edit owns diagnostics; reporting the request would hide a rejected or failed response.
            if (update.effect().isPresent())
                update = completeMorphsEffect(update.effect().orElseThrow());
            if (update.fillEmptyOffer().isPresent())
                showFillEmptyFlyout(update.fillEmptyOffer().orElseThrow());
            publishMorphsOutcome(intent, update);
            if (intent instanceof MorphsFeature.Create && update.accepted()
                    && update.outcomeKind() == MorphsFeature.OutcomeKind.CHANGED)
                customMorphTargetNameInput.clear();
            if (intent instanceof MorphsFeature.CreateNpc && update.accepted()
                    && update.outcomeKind() == MorphsFeature.OutcomeKind.CHANGED) {
                npcDisplayNameInput.clear();
                npcPluginNameInput.clear();
                npcEditorIdInput.clear();
                npcRaceInput.clear();
                npcFormIdInput.clear();
            }
        } finally {
            morphsOwnProjectDiagnostics = false;
        }
    }

    /** Distinguishes Project mutations from local Morphs browsing while a central job owns admission. */
    private static boolean isMorphsMutation(MorphsFeature.Intent intent) {
        return intent instanceof MorphsFeature.Create
                || intent instanceof MorphsFeature.CreateNpc
                || intent instanceof MorphsFeature.AssignSliderPreset
                || intent instanceof MorphsFeature.AssignAllSliderPresets
                || intent instanceof MorphsFeature.RemoveAssignedSliderPreset
                || intent instanceof MorphsFeature.RequestClearAssignments
                || intent instanceof MorphsFeature.RequestRemove
                || intent instanceof MorphsFeature.RequestClearVisible
                || intent instanceof MorphsFeature.RequestRemoveNpc
                || intent instanceof MorphsFeature.RequestClearVisibleNpcs
                || intent instanceof MorphsFeature.RequestFillEmpty;
    }

    /** Renders one Morphs update and refreshes Project chrome without replaying lifecycle-only feedback. */
    private void renderMorphsUpdate(MorphsFeature.Update update) {
        if (update.frame().projectSequence() != renderedProjectSequence) {
            // Morphs owns task wording and inline validation, so suppress the lifecycle-only generic projection.
            renderedProjectSequence = update.frame().projectSequence();
            render(projectFlow.frame());
        }
        renderMorphs(update.frame());
    }

    /**
     * Realizes one tokenized destructive Morphs confirmation and returns its completed feature response for reporting.
     */
    private MorphsFeature.Update completeMorphsEffect(MorphsFeature.Effect effect) {
        WorkbenchFeedback.DialogAction destructiveAction = effect.kind() == MorphsFeature.EffectKind.CONFIRM_REMOVE
                || effect.kind() == MorphsFeature.EffectKind.CONFIRM_REMOVE_NPC
                || effect.kind() == MorphsFeature.EffectKind.CONFIRM_REMOVE_ASSIGNMENT
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
        MorphsFeature.Update response = morphsFeature.respond(effect.token(), confirmed);
        renderMorphsUpdate(response);
        if (confirmed) {
            String operation = switch (effect.kind()) {
                case CONFIRM_REMOVE -> "Remove Custom Morph Target";
                case CONFIRM_REMOVE_NPC -> "Remove NPC Morph Assignment";
                case CONFIRM_REMOVE_ASSIGNMENT -> "Remove Slider Preset assignment";
                case CONFIRM_CLEAR_VISIBLE -> "Clear visible Custom Morph Targets";
                case CONFIRM_CLEAR_VISIBLE_NPCS -> "Clear visible NPC Morph Assignments";
                case CONFIRM_CLEAR_ASSIGNMENTS -> "Clear Slider Preset assignments";
            };
            publishMorphsMutation(operation, response, true);
        }
        return response;
    }

    /** Routes Morphs validation and task outcomes into the accepted inline, status, Activity, and failure tiers. */
    private void publishMorphsOutcome(MorphsFeature.Intent intent, MorphsFeature.Update update) {
        if (!update.frame().diagnostics().isEmpty()) {
            String message = ProjectDiagnosticFormatter.format(update.frame().diagnostics());
            WorkbenchFeedback.Severity severity = update.outcomeKind() == MorphsFeature.OutcomeKind.FAILED
                    ? WorkbenchFeedback.Severity.FAILURE
                    : WorkbenchFeedback.Severity.VALIDATION;
            renderFeedback(feedback.publishStatus(new WorkbenchFeedback.Notification(
                    "Morphs validation", severity, message, WorkbenchFeedback.Disposition.FAILED)));
            if (intent instanceof MorphsFeature.Create) {
                customMorphTargetNameInput.requestFocus();
                Platform.runLater(customMorphTargetNameInput::requestFocus);
            } else if (intent instanceof MorphsFeature.CreateNpc) {
                TextField invalidInput = firstInvalidNpcInput(update.frame().diagnostics());
                invalidInput.requestFocus();
                Platform.runLater(invalidInput::requestFocus);
            }
            if (update.outcomeKind() == MorphsFeature.OutcomeKind.FAILED)
                showMorphsFailure(update);
            return;
        }
        if (!update.accepted() || update.effect().isPresent())
            return;
        String operation = switch (intent) {
            case MorphsFeature.Create ignored -> "Create Custom Morph Target";
            case MorphsFeature.CreateNpc ignored -> "Create NPC Morph Assignment";
            case MorphsFeature.AssignSliderPreset ignored -> "Assign Slider Preset";
            case MorphsFeature.AssignAllSliderPresets ignored -> "Assign all Slider Presets";
            case MorphsFeature.RequestFillEmpty ignored -> "Fill Empty NPC Morph Assignments";
            default -> null;
        };
        if (operation != null && (update.outcomeKind() == MorphsFeature.OutcomeKind.CHANGED
                || update.outcomeKind() == MorphsFeature.OutcomeKind.UNCHANGED))
            publishMorphsMutation(operation, update, intent instanceof MorphsFeature.AssignAllSliderPresets
                    || intent instanceof MorphsFeature.RequestFillEmpty);
    }

    /**
     * Finds the input to refocus after NPC authoring validation.
     *
     * @param diagnostics structured Project rejection used to distinguish a malformed Form ID
     * @return the first empty required field, the invalid Form ID field, or the plugin identity field
     */
    private TextField firstInvalidNpcInput(List<ProjectDiagnostic> diagnostics) {
        for (TextField field : List.of(npcPluginNameInput, npcEditorIdInput, npcRaceInput, npcFormIdInput)) {
            if (field.getText().isBlank())
                return field;
        }
        if (diagnostics.stream().anyMatch(diagnostic ->
                ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_FORM_ID_INVALID.equals(diagnostic.getCode())))
            return npcFormIdInput;
        return npcPluginNameInput;
    }

    /** Publishes one accepted Morphs mutation through the status or durable Activity path. */
    private void publishMorphsMutation(String operation, MorphsFeature.Update update, boolean durable) {
        if (!update.accepted())
            return;
        WorkbenchFeedback.Notification notification = new WorkbenchFeedback.Notification(operation,
                update.outcomeKind() == MorphsFeature.OutcomeKind.UNCHANGED
                        ? WorkbenchFeedback.Severity.INFORMATION : WorkbenchFeedback.Severity.SUCCESS,
                update.outcomeKind() == MorphsFeature.OutcomeKind.UNCHANGED
                        ? operation + " made no changes." : operation + " completed.",
                WorkbenchFeedback.Disposition.COMPLETED);
        renderFeedback(durable ? feedback.publishActivity(notification) : feedback.publishStatus(notification));
    }

    /** Presents an unexpected synchronous Morphs failure without losing the semantic launcher focus. */
    private void showMorphsFailure(MorphsFeature.Update update) {
        WorkbenchNavigation.FocusTarget returnTarget = currentSemanticFocus();
        String details = ProjectDiagnosticFormatter.format(update.frame().diagnostics());
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.failure(
                "Morphs editing failed", "The Morphs edit could not be completed.",
                details.isBlank() ? "No diagnostic details were provided." : details, false);
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(spec);
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        renderFeedback(pendingFrame);
        WorkbenchFeedback.DialogAction action = platform.completeFailure(spec, stage);
        renderFeedback(feedback.answerDialog(new WorkbenchFeedback.DialogResult(
                pending.token(), action)).frame());
        requestFocus(returnTarget);
    }

    /** Commits one Settings intent and admits any captured persistence effect to the application worker. */
    private SettingsFeature.Update dispatchSettings(SettingsFeature.Intent intent) {
        return dispatchSettings(intent, Optional.empty());
    }

    /**
     * Commits one Settings intent, installing an optional token-keyed close continuation before worker admission.
     *
     * @param intent requested Settings task
     * @param closeReturnTarget semantic focus to restore while continuing a close-save, otherwise empty
     * @return committed feature update, or rejected update when coordinator admission failed
     */
    private SettingsFeature.Update dispatchSettings(SettingsFeature.Intent intent,
                                                     Optional<WorkbenchNavigation.FocusTarget> closeReturnTarget) {
        if (settingsMutationsBlocked && isSettingsMutation(intent))
            return new SettingsFeature.Update(false, settingsFeature.frame());
        SettingsFeature.Update update = settingsFeature.dispatch(Objects.requireNonNull(intent, "intent"));
        renderSettings(update.frame());
        if (update.effect().isPresent()) {
            SettingsFeature.Effect effect = update.effect().orElseThrow();
            if (effect instanceof SettingsFeature.ReloadConfirmationEffect confirmation)
                return completeSettingsReloadConfirmation(confirmation);
            closeReturnTarget.ifPresent(target ->
                    settingsCloseContinuation = new SettingsCloseContinuation(effect.token(), target));
            if (!submitSettingsEffect(effect)) {
                if (settingsCloseContinuation != null && settingsCloseContinuation.effectToken() == effect.token())
                    settingsCloseContinuation = null;
                return new SettingsFeature.Update(false, settingsFeature.frame());
            }
        } else {
            publishSettingsOutcome(update);
        }
        if (update.accepted() && update.effect().isEmpty())
            renderOutput(outputFeature.refreshGenerationSettings().frame());
        return update;
    }

    /** Publishes and resolves one dirty-Reload dialog before admitting the selected Settings worker operation. */
    private SettingsFeature.Update completeSettingsReloadConfirmation(
            SettingsFeature.ReloadConfirmationEffect effect) {
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.unsavedClose(
                effect.title(), effect.message());
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(spec);
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        renderFeedback(pendingFrame);
        WorkbenchFeedback.DialogAction action = platform.completeConfirmation(spec, stage);
        renderFeedback(feedback.answerDialog(new WorkbenchFeedback.DialogResult(
                pending.token(), action)).frame());
        SettingsFeature.ReloadDecision decision = switch (action) {
            case SAVE -> SettingsFeature.ReloadDecision.SAVE;
            case DISCARD -> SettingsFeature.ReloadDecision.DISCARD;
            case CANCEL -> SettingsFeature.ReloadDecision.CANCEL;
            case COPY_DETAILS, RETRY, REMOVE, CLEAR, CLOSE -> throw new IllegalArgumentException(
                    "Dirty Settings Reload returned an unsupported dialog action: " + action);
        };
        SettingsFeature.Update response = settingsFeature.respondReload(effect.token(), decision);
        renderSettings(response.frame());
        if (response.effect().isPresent() && !submitSettingsEffect(response.effect().orElseThrow()))
            return new SettingsFeature.Update(false, settingsFeature.frame());
        return response;
    }

    /** Admits one immutable Settings effect to the existing application-wide coordinator. */
    private boolean submitSettingsEffect(SettingsFeature.Effect effect) {
        if (effect instanceof SettingsFeature.ReloadConfirmationEffect)
            throw new IllegalArgumentException("Reload confirmation must complete before worker admission");
        JobCoordinator.Admission admission = projectFlow.jobs().submit(settingsSubmission(effect));
        if (!admission.admitted()) {
            settingsFeature.cancel(effect.token());
            renderSettings(settingsFeature.frame());
        }
        return admission.admitted();
    }

    /** Builds one retryable Settings submission whose retry recaptures current feature inputs. */
    private JobCoordinator.Submission<SettingsFeature.Completion> settingsSubmission(SettingsFeature.Effect effect) {
        String operationName = switch (effect) {
            case SettingsFeature.SaveEffect ignored -> "Save Settings";
            case SettingsFeature.ReloadEffect ignored -> "Reload Settings";
            case SettingsFeature.PreferenceEffect ignored -> "Save Generation Preference";
            case SettingsFeature.ReloadConfirmationEffect ignored -> throw new IllegalArgumentException(
                    "Reload confirmation must complete before worker admission");
        };
        JobCoordinator.Operation operation = new JobCoordinator.Operation(
                operationName,
                effect instanceof SettingsFeature.ReloadEffect ? List.of(effect.directory().toString()) : List.of(),
                effect instanceof SettingsFeature.SaveEffect || effect instanceof SettingsFeature.PreferenceEffect
                        ? List.of(effect.directory().toString()) : List.of(),
                Optional.empty());
        return new JobCoordinator.Submission<>(operation,
                context -> runSettingsEffect(effect, context),
                (attempt, result) -> completeSettingsEffect(effect, result),
                Optional.of(settingsRetryFactory(effect)));
    }

    /** Builds dynamic retry availability and recapture around the current Settings draft. */
    private JobCoordinator.RetryFactory<SettingsFeature.Completion> settingsRetryFactory(
            SettingsFeature.Effect previous) {
        return new JobCoordinator.RetryFactory<>() {
            /** Recaptures current inputs and transfers any close continuation to the new effect token. */
            @Override
            public JobCoordinator.Submission<SettingsFeature.Completion> recapture() {
                return recaptureSettingsSubmission(previous);
            }

            /** Save needs a valid draft; dirty Reload must use the ordinary confirmation path. */
            @Override
            public Optional<String> unavailableReason() {
                if (previous instanceof SettingsFeature.SaveEffect)
                    return settingsFeature.saveRetryUnavailableReason();
                if (previous instanceof SettingsFeature.ReloadEffect)
                    return settingsFeature.reloadRetryUnavailableReason();
                return Optional.empty();
            }
        };
    }

    /** Recaptures Save drafts or the Reload directory when the user explicitly retries a failed Settings job. */
    private JobCoordinator.Submission<SettingsFeature.Completion> recaptureSettingsSubmission(
            SettingsFeature.Effect previous) {
        SettingsFeature.Update recaptured = settingsFeature.retry(previous);
        SettingsFeature.Effect replacement = recaptured.effect().orElseThrow(
                () -> new IllegalStateException("Settings retry could not recapture its persistence inputs"));
        if (settingsCloseContinuation != null && settingsCloseContinuation.effectToken() == previous.token())
            settingsCloseContinuation = settingsCloseContinuation.forEffect(replacement.token());
        return settingsSubmission(replacement);
    }

    /**
     * Executes blocking Settings I/O on the worker. Reload stays cancellable through lock acquisition and ordinary
     * pair reads; the Settings seam enters commit immediately before recovery or publication can mutate state.
     */
    static JobCoordinator.Result<SettingsFeature.Completion> runSettingsEffect(
            SettingsFeature.Effect effect, JobCoordinator.Context context) {
        context.checkCancellation();
        String commitPhase = switch (effect) {
            case SettingsFeature.SaveEffect ignored -> "Saving Settings";
            case SettingsFeature.ReloadEffect ignored -> "Reloading Settings";
            case SettingsFeature.PreferenceEffect ignored -> "Saving generation preference";
            case SettingsFeature.ReloadConfirmationEffect ignored -> throw new IllegalArgumentException(
                    "Reload confirmation cannot execute on the worker");
        };
        boolean reloadEffect = effect instanceof SettingsFeature.ReloadEffect;
        if (!reloadEffect && !context.beginCommit(commitPhase))
            return JobCoordinator.Result.cancelled("Settings operation cancelled.", List.of(), List.of());
        SettingsFeature.Completion completion;
        boolean successful;
        List<JobCoordinator.Diagnostic> diagnostics;
        String successSummary;
        String failureSummary;
        switch (effect) {
            case SettingsFeature.SaveEffect save -> {
                Settings.PersistenceResult result = Settings.persist(save.directory(), save.replacement());
                completion = new SettingsFeature.SaveCompletion(save.token(), result);
                successful = result.isSuccessful();
                diagnostics = settingsDiagnostics(result);
                successSummary = "Settings saved.";
                failureSummary = "Settings could not be saved.";
            }
            case SettingsFeature.ReloadEffect reload -> {
                Settings.InitializationResult result = Settings.initialize(reload.directory(),
                        () -> context.beginCommit(commitPhase));
                completion = new SettingsFeature.ReloadCompletion(reload.token(), result);
                successful = result.isSuccessful();
                diagnostics = settingsDiagnostics(result);
                boolean recovered = result.getDiagnostics().stream()
                        .anyMatch(value -> value.getCode().equals("SETTINGS_PUBLICATION_RECOVERED"));
                successSummary = recovered ? "Settings recovered and reloaded." : "Settings reloaded.";
                failureSummary = "Settings could not be reloaded.";
            }
            case SettingsFeature.PreferenceEffect preference -> {
                try {
                    new GenerationPreferencesStore(preference.directory()).save(preference.selected());
                    completion = SettingsFeature.PreferenceCompletion.successful(preference.token());
                    successful = true;
                    diagnostics = List.of();
                } catch (IOException failure) {
                    SettingsFeature.PreferenceCompletion failed =
                            SettingsFeature.PreferenceCompletion.failed(preference.token(), failure);
                    completion = failed;
                    successful = false;
                    diagnostics = List.of(generationPreferenceDiagnostic(preference.directory(),
                            failed.failureMessage().orElseThrow()));
                }
                successSummary = "Generation preference saved.";
                failureSummary = "Generation preference could not be saved.";
            }
            case SettingsFeature.ReloadConfirmationEffect ignored -> throw new IllegalArgumentException(
                    "Reload confirmation cannot execute on the worker");
        }
        if (!successful)
            return JobCoordinator.Result.failed(completion, failureSummary, diagnostics);
        List<String> effects = effect instanceof SettingsFeature.PreferenceEffect
                ? List.of("Published generated-output preference")
                : List.of("Published Standard and UUNP Settings");
        return diagnostics.isEmpty()
                ? JobCoordinator.Result.completed(completion, successSummary, effects, List.of())
                : JobCoordinator.Result.completedWithIssues(completion, successSummary, effects, diagnostics);
    }

    /** Preserves a generation-preference failure's stable code and profile-local path in terminal job evidence. */
    private static JobCoordinator.Diagnostic generationPreferenceDiagnostic(Path directory, String message) {
        return new JobCoordinator.Diagnostic("GENERATION_PREFERENCES_IO_FAILED", message,
                Optional.of(directory + " /omitRedundantSliders"));
    }

    /** Converts one Save failure or warning set to coordinator-owned structured diagnostics. */
    private static List<JobCoordinator.Diagnostic> settingsDiagnostics(Settings.PersistenceResult result) {
        if (!result.isSuccessful())
            return List.of(settingsDiagnostic(result.getFailure().orElseThrow()));
        return result.getDiagnostics().stream().map(WorkbenchController::settingsDiagnostic).toList();
    }

    /** Converts one Reload failure or warning set to coordinator-owned structured diagnostics. */
    private static List<JobCoordinator.Diagnostic> settingsDiagnostics(Settings.InitializationResult result) {
        if (!result.isSuccessful())
            return List.of(settingsDiagnostic(result.getFailure().orElseThrow()));
        return result.getDiagnostics().stream().map(WorkbenchController::settingsDiagnostic).toList();
    }

    /** Preserves a Settings failure's stable code and source path in terminal job evidence. */
    private static JobCoordinator.Diagnostic settingsDiagnostic(Settings.Failure failure) {
        return new JobCoordinator.Diagnostic(failure.getCode(), failure.getMessage(),
                Optional.of(failure.getSource() + " " + failure.getPath()));
    }

    /** Preserves a non-blocking Settings diagnostic in terminal job evidence. */
    private static JobCoordinator.Diagnostic settingsDiagnostic(Settings.Diagnostic diagnostic) {
        return new JobCoordinator.Diagnostic(diagnostic.getCode(), diagnostic.getMessage(),
                Optional.of(diagnostic.getSource() + " " + diagnostic.getPath()));
    }

    /** Applies one terminal worker result and continues a confirmed Close or Reload only after Save succeeds. */
    private void completeSettingsEffect(SettingsFeature.Effect effect,
                                        JobCoordinator.Result<SettingsFeature.Completion> result) {
        SettingsFeature.Update update = applySettingsCompletion(settingsFeature, effect, result);
        renderSettings(update.frame());
        boolean published = update.accepted() && (update.frame().outcome() == SettingsFeature.OutcomeKind.SAVED
                || update.frame().outcome() == SettingsFeature.OutcomeKind.RELOADED
                || update.frame().outcome() == SettingsFeature.OutcomeKind.RECOVERED);
        boolean preferencePublished = effect instanceof SettingsFeature.PreferenceEffect
                && update.accepted() && update.frame().outcome() == SettingsFeature.OutcomeKind.CHANGED;
        if (published || preferencePublished) {
            if (projectFlow.jobs().frame().shutdownRequested())
                settingsRefreshDeferred = true;
            else if (preferencePublished)
                renderOutput(outputFeature.refreshGenerationSettings().frame());
            else
                refreshPublishedSettings();
        }
        // Rejected admission already cancels the feature effect; shutdown races are an ordinary abandoned continuation.
        update.effect().ifPresent(this::submitSettingsEffect);
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
     * Applies a Settings worker value only when its coordinator lifecycle accepted it. A cancellation won before
     * commit may still carry a failed I/O value, which must not replace the retained draft with failure feedback.
     *
     * @param feature Settings draft state awaiting the effect
     * @param effect tokenized worker effect being settled
     * @param result authoritative coordinator result after cancellation classification
     * @return the completed feature frame, or the unchanged frame after cancellation
     */
    static SettingsFeature.Update applySettingsCompletion(SettingsFeature feature, SettingsFeature.Effect effect,
                                                          JobCoordinator.Result<SettingsFeature.Completion> result) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(result, "result");
        if (result.lifecycle() == JobCoordinator.Lifecycle.CANCELLED || result.value().isEmpty())
            return feature.cancel(effect.token());
        return feature.complete(result.value().orElseThrow());
    }

    /** Reprojects every Settings-dependent feature after the live pair is published. */
    private void refreshPublishedSettings() {
        projectFlow.refreshSettings();
        WorkbenchProjectFlow.Frame projectFrame = projectFlow.frame();
        // Settings-driven derived choices are not a second user Project command and must not create generic Activity.
        renderedProjectSequence = projectFrame.sequence();
        render(projectFrame);
        renderTemplates(templatesFeature.refreshSettings().frame());
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
            boolean backgroundJobBlocked = projectFlow.jobs().frame().active()
                    || projectFlow.jobs().frame().shutdownRequested();
            omitRedundantSlidersCheck.setDisable(settingsMutationsBlocked || backgroundJobBlocked);
            boolean editable = frame.editor().isPresent() && !settingsMutationsBlocked;
            for (javafx.scene.control.Control control : List.of(settingsEntryNameInput, settingsSmallInput,
                    settingsBigInput, settingsMultiplierInput, settingsInvertedCheck, applySettingsEntryButton,
                    removeSettingsEntryButton))
                control.setDisable(!editable);
            newSettingsEntryName.setDisable(settingsMutationsBlocked);
            addSettingsEntryButton.setDisable(settingsMutationsBlocked);
            saveSettingsButton.setDisable(settingsMutationsBlocked || backgroundJobBlocked || !frame.dirty()
                    || !frame.validation().isEmpty());
            reloadSettingsButton.setDisable(settingsMutationsBlocked || backgroundJobBlocked);
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
        configureCatalogHeight(sliderPresetList, SLIDER_PRESET_CELL_HEIGHT, MAX_VISIBLE_SLIDER_PRESET_ROWS);
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
        templatesOwnProjectDiagnostics = true;
        try {
            TemplatesFeature.Update update = templatesFeature.dispatch(Objects.requireNonNull(intent, "intent"));
            renderTemplatesUpdate(update);
            update.effect().ifPresent(this::completeTemplatesEffect);
            publishTemplatesOutcome(intent, update);
            if ((intent instanceof TemplatesFeature.CommitRename && update.accepted())
                    || intent instanceof TemplatesFeature.CancelRename) {
                sliderPresetList.requestFocus();
                Platform.runLater(sliderPresetList::requestFocus);
            }
        } finally {
            templatesOwnProjectDiagnostics = false;
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
     * Renders one immutable Morphs frame while suppressing control listeners from becoming a second command path.
     */
    private void renderMorphs(MorphsFeature.Frame frame) {
        renderingMorphs = true;
        try {
            if (!renderedMorphTargetSelection.equals(frame.selection())
                    || !renderedNpcSelection.equals(frame.npcSelection())) {
                // A pending available-preset choice belongs to its former target and must not carry to another.
                availableMorphSliderPreset.setValue(null);
            }
            customMorphTargetFilter.setText(frame.filterText());
            customMorphTargetSort.setValue(frame.sortOrder());
            reconcileCustomMorphTargetItems(frame.visibleTargets());
            npcMorphAssignmentFilter.setText(frame.npcFilterText());
            npcMorphAssignmentSort.setValue(frame.npcSortOrder());
            reconcileNpcMorphAssignmentItems(frame.visibleNpcs());
            CustomMorphTargetSnapshot selectedTarget = customMorphTargetList.getSelectionModel().getSelectedItem();
            Optional<NameIdentity> currentTarget = selectedTarget == null ? Optional.empty()
                    : Optional.of(NameIdentity.of(selectedTarget.getName()));
            if (!currentTarget.equals(frame.selection())) {
                customMorphTargetList.getSelectionModel().clearSelection();
                frame.selection().ifPresent(identity -> customMorphTargetList.getItems().stream()
                        .filter(target -> NameIdentity.of(target.getName()).equals(identity))
                        .findFirst().ifPresent(customMorphTargetList.getSelectionModel()::select));
            }
            NpcMorphAssignmentSnapshot selectedNpc = npcMorphAssignmentList.getSelectionModel().getSelectedItem();
            Optional<NpcMorphAssignmentIdentity> currentNpc = selectedNpc == null ? Optional.empty()
                    : Optional.of(new NpcMorphAssignmentIdentity(
                            selectedNpc.getPluginName(), selectedNpc.getEditorId()));
            if (!currentNpc.equals(frame.npcSelection())) {
                npcMorphAssignmentList.getSelectionModel().clearSelection();
                frame.npcSelection().ifPresent(identity -> npcMorphAssignmentList.getItems().stream()
                        .filter(npc -> new NpcMorphAssignmentIdentity(
                                npc.getPluginName(), npc.getEditorId()).equals(identity))
                        .findFirst().ifPresent(npcMorphAssignmentList.getSelectionModel()::select));
            }

            boolean validationVisible = !frame.diagnostics().isEmpty();
            morphsInfoBar.setManaged(validationVisible);
            morphsInfoBar.setVisible(validationVisible);
            if (validationVisible) {
                String message = ProjectDiagnosticFormatter.format(frame.diagnostics());
                WorkbenchFeedback.Severity severity = frame.outcomeKind() == MorphsFeature.OutcomeKind.FAILED
                        ? WorkbenchFeedback.Severity.FAILURE : WorkbenchFeedback.Severity.VALIDATION;
                morphsInfoBarCue.setText(severity.cue());
                morphsInfoBarMessage.setText(message);
                morphsInfoBar.setAccessibleHelp(severity.cue() + ": " + message);
                setSeverityStyle(morphsInfoBar, severity);
            }

            boolean selected = frame.editor().isPresent();
            boolean npcSelected = frame.npcEditor().isPresent();
            createCustomMorphTargetButton.setDisable(morphsMutationsBlocked);
            removeCustomMorphTargetButton.setDisable(morphsMutationsBlocked || !selected);
            clearCustomMorphTargetsButton.setDisable(morphsMutationsBlocked || frame.visibleTargets().isEmpty());
            createNpcMorphAssignmentButton.setDisable(morphsMutationsBlocked);
            removeNpcMorphAssignmentButton.setDisable(morphsMutationsBlocked || !npcSelected);
            clearNpcMorphAssignmentsButton.setDisable(morphsMutationsBlocked || frame.visibleNpcs().isEmpty());
            fillEmptyNpcMorphAssignmentsButton.setDisable(morphsMutationsBlocked);
            long visibleEmptyCount = frame.visibleNpcs().stream()
                    .filter(npc -> npc.getSliderPresetNames().isEmpty()).count();
            fillEmptyNpcMorphAssignmentsButton.setAccessibleHelp(visibleEmptyCount +
                    " visible empty NPC Morph Assignments. Opens Slider Preset selection.");
            morphTargetConditionText.setManaged(!npcSelected);
            morphTargetConditionText.setVisible(!npcSelected);
            npcIdentityText.setManaged(npcSelected);
            npcIdentityText.setVisible(npcSelected);
            npcRaceText.setManaged(npcSelected);
            npcRaceText.setVisible(npcSelected);
            npcFormIdText.setManaged(npcSelected);
            npcFormIdText.setVisible(npcSelected);
            List<SliderPresetSnapshot> assignedPresets = List.of();
            List<SliderPresetSnapshot> availablePresets = List.of();
            Optional<NameIdentity> assignedPresetSelection = Optional.empty();
            NpcMorphAssignmentSnapshot portraitNpc = null;
            if (!selected && !npcSelected) {
                morphTargetEditorFocusTarget.setText("No Morphs selection");
                morphTargetEditorFocusTarget.setAccessibleText("Morphs editor: no selection");
                morphTargetConditionText.setText("Select a Custom Morph Target or NPC Morph Assignment to inspect it.");
                morphTargetConditionText.setAccessibleText(
                        "Select a Custom Morph Target or NPC Morph Assignment to inspect it.");
                morphTargetAssignmentCountText.setText("No Slider Presets assigned");
                morphTargetAssignmentCountText.setAccessibleText("No Slider Presets assigned");
                morphTargetOutputStatusText.setText(
                        "A Morphs entry needs at least one Slider Preset to appear in output.");
                morphTargetOutputStatusText.setAccessibleText(
                        "No selected Morphs entry is eligible for output.");
                morphTargetSelectionText.setText("No Morphs selection");
                morphTargetSelectionText.setAccessibleText("Morphs inspector: no selection");
                npcIdentityText.setText("");
                npcRaceText.setText("");
                npcFormIdText.setText("");
            } else if (selected) {
                MorphsFeature.EditorFrame editor = frame.editor().orElseThrow();
                CustomMorphTargetSnapshot target = editor.target();
                int count = editor.assignedPresets().size();
                morphTargetEditorFocusTarget.setText(target.getName());
                morphTargetEditorFocusTarget.setAccessibleText("Custom Morph Target editor: " + target.getName());
                morphTargetConditionText.setText("BodyGen condition: " + target.getName());
                morphTargetConditionText.setAccessibleText("BodyGen condition: " + target.getName());
                morphTargetSelectionText.setText("Selected: " + target.getName());
                morphTargetSelectionText.setAccessibleText("Selected Custom Morph Target " + target.getName());
                assignedPresets = editor.assignedPresets();
                availablePresets = editor.availablePresets();
                assignedPresetSelection = editor.assignedSelection();
            } else {
                MorphsFeature.NpcEditorFrame editor = frame.npcEditor().orElseThrow();
                NpcMorphAssignmentSnapshot npc = editor.npc();
                portraitNpc = npc;
                morphTargetEditorFocusTarget.setText(npc.getDisplayName());
                morphTargetEditorFocusTarget.setAccessibleText(
                        "NPC Morph Assignment editor: " + npc.getDisplayName());
                npcIdentityText.setText("Plugin: " + npc.getPluginName() + "; Editor ID: " + npc.getEditorId());
                npcRaceText.setText("Race: " + npc.getRace());
                npcFormIdText.setText("Form ID: " + npc.getFormId());
                morphTargetSelectionText.setText("Selected NPC: " + npc.getDisplayName());
                morphTargetSelectionText.setAccessibleText("Selected NPC Morph Assignment " + npc.getDisplayName()
                        + ", plugin " + npc.getPluginName() + ", editor ID " + npc.getEditorId());
                assignedPresets = editor.assignedPresets();
                availablePresets = editor.availablePresets();
                assignedPresetSelection = editor.assignedSelection();
            }
            renderNpcPortrait(portraitNpc);
            if (selected || npcSelected) {
                int count = assignedPresets.size();
                String assignmentCount = count + (count == 1
                        ? " assigned Slider Preset" : " assigned Slider Presets");
                morphTargetAssignmentCountText.setText(assignmentCount);
                morphTargetAssignmentCountText.setAccessibleText(assignmentCount);
                String outputStatus = morphOutputStatus(count);
                morphTargetOutputStatusText.setText(outputStatus);
                morphTargetOutputStatusText.setAccessibleText(outputStatus);
            }
            reconcileAssignedMorphSliderPresetItems(assignedPresets);
            assignedMorphSliderPresetList.getSelectionModel().clearSelection();
            assignedPresetSelection.ifPresent(identity -> assignedMorphSliderPresetList.getItems().stream()
                    .filter(preset -> NameIdentity.of(preset.getName()).equals(identity))
                    .findFirst().ifPresent(assignedMorphSliderPresetList.getSelectionModel()::select));
            SliderPresetSnapshot availableSelection = availableMorphSliderPreset.getValue();
            availableMorphSliderPreset.getItems().setAll(availablePresets);
            if (availableSelection == null || availablePresets.stream()
                    .noneMatch(preset -> preset.getName().equalsIgnoreCase(availableSelection.getName())))
                availableMorphSliderPreset.setValue(null);
            boolean assignedSelected = assignedPresetSelection.isPresent();
            boolean hasAssignments = !assignedPresets.isEmpty();
            boolean hasAvailable = !availablePresets.isEmpty();
            availableMorphSliderPreset.setDisable(morphsMutationsBlocked || !hasAvailable);
            assignMorphSliderPresetButton.setDisable(morphsMutationsBlocked || !hasAvailable
                    || availableMorphSliderPreset.getValue() == null);
            assignAllMorphSliderPresetsButton.setDisable(morphsMutationsBlocked || !hasAvailable);
            removeMorphSliderPresetButton.setDisable(morphsMutationsBlocked || !assignedSelected);
            clearMorphSliderPresetsButton.setDisable(morphsMutationsBlocked || !hasAssignments);
            renderedMorphTargetSelection = frame.selection();
            renderedNpcSelection = frame.npcSelection();
        } finally {
            renderingMorphs = false;
        }
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
     * Replaces an empty target ListView only when a later frame refills it, preserving JavaFX 25 UIA child discovery.
     */
    private void reconcileCustomMorphTargetItems(List<CustomMorphTargetSnapshot> visibleTargets) {
        boolean refill = customMorphTargetListInitialized && customMorphTargetList.getItems().isEmpty()
                && !visibleTargets.isEmpty();
        if (refill)
            replaceEmptyCustomMorphTargetList();
        if (!List.copyOf(customMorphTargetList.getItems()).equals(visibleTargets))
            customMorphTargetList.getItems().setAll(visibleTargets);
        customMorphTargetListInitialized = true;
    }

    /** Replaces an empty NPC ListView before refill so Windows UIA discovers the new virtualized rows. */
    private void reconcileNpcMorphAssignmentItems(List<NpcMorphAssignmentSnapshot> visibleNpcs) {
        boolean refill = npcMorphAssignmentListInitialized && npcMorphAssignmentList.getItems().isEmpty()
                && !visibleNpcs.isEmpty();
        if (refill)
            replaceEmptyNpcMorphAssignmentList();
        if (!List.copyOf(npcMorphAssignmentList.getItems()).equals(visibleNpcs))
            npcMorphAssignmentList.getItems().setAll(visibleNpcs);
        npcMorphAssignmentListInitialized = true;
    }

    /** Replaces an empty assigned-preset ListView when a Custom Morph Target or NPC Morph Assignment refills it. */
    private void reconcileAssignedMorphSliderPresetItems(List<SliderPresetSnapshot> assignedPresets) {
        boolean refill = assignedMorphSliderPresetListInitialized
                && assignedMorphSliderPresetList.getItems().isEmpty() && !assignedPresets.isEmpty();
        if (refill)
            replaceEmptyAssignedMorphSliderPresetList();
        if (!List.copyOf(assignedMorphSliderPresetList.getItems()).equals(assignedPresets))
            assignedMorphSliderPresetList.getItems().setAll(assignedPresets);
        assignedMorphSliderPresetListInitialized = true;
    }

    /** Replaces only the assigned-relationship JavaFX node while retaining its label and semantic focus. */
    private void replaceEmptyAssignedMorphSliderPresetList() {
        boolean restoreFocus = assignedMorphSliderPresetList.isFocused();
        ListView<SliderPresetSnapshot> replacement = new ListView<>();
        replacement.setId("assignedMorphSliderPresetList");
        replacement.setAccessibleText("Assigned Slider Presets");
        replacement.setFixedCellSize(MORPH_TARGET_CELL_HEIGHT);
        replacement.setFocusTraversable(true);
        replacement.setPrefHeight(120.0);
        int index = morphsInspectorContent.getChildren().indexOf(assignedMorphSliderPresetList);
        if (index < 0)
            throw new IllegalStateException("Morphs inspector no longer owns the assigned Slider Preset list");
        morphsInspectorContent.getChildren().set(index, replacement);
        assignedMorphSliderPresetList = replacement;
        assignedMorphSliderPresetLabel.setLabelFor(replacement);
        configureAssignedMorphSliderPresetList();
        if (restoreFocus) {
            // Relationship reconciliation may refill while the list owns focus; the UIA node swap must preserve it.
            replacement.requestFocus();
            Platform.runLater(replacement::requestFocus);
        }
    }

    /** Replaces only the JavaFX adapter node after an empty-to-populated target transition. */
    private void replaceEmptyCustomMorphTargetList() {
        boolean restoreFocus = customMorphTargetList.isFocused();
        ListView<CustomMorphTargetSnapshot> replacement = new ListView<>();
        replacement.setId("customMorphTargetList");
        replacement.setAccessibleText("Custom Morph Targets");
        replacement.setFixedCellSize(MORPH_TARGET_CELL_HEIGHT);
        replacement.setFocusTraversable(true);
        VBox.setVgrow(replacement, javafx.scene.layout.Priority.ALWAYS);
        int index = morphsPrimaryContent.getChildren().indexOf(customMorphTargetList);
        if (index < 0)
            throw new IllegalStateException("Morphs primary content no longer owns the Custom Morph Target list");
        morphsPrimaryContent.getChildren().set(index, replacement);
        customMorphTargetList = replacement;
        configureCustomMorphTargetList();
        if (restoreFocus) {
            // A filter clear can refill the list while it owns focus; the UIA node swap must retain that focus.
            replacement.requestFocus();
            Platform.runLater(replacement::requestFocus);
        }
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
     * Tracks CSS, scene attachment, and the owner's render scale for the lifetime of this adapter ListView.
     *
     * @param list current Templates or Morphs catalog control
     * @param cellHeight fixed logical cell height used by its ListView
     * @param maximumRows largest catalog viewport before scrolling is required
     */
    private void configureCatalogHeight(ListView<?> list, double cellHeight, int maximumRows) {
        DoubleBinding height = Bindings.createDoubleBinding(() -> {
            int rows = Math.max(1, Math.min(maximumRows, list.getItems().size()));
            // Snap each CSS inset independently, then round the total outward. Otherwise subtracting fractional
            // insets can leave VirtualFlow a fraction of a pixel short and expose a redundant vertical scrollbar.
            return list.snapSizeY(Math.ceil(list.snapSizeY(rows * cellHeight)
                    + list.snappedTopInset() + list.snappedBottomInset()));
        }, list.getItems(), list.insetsProperty(), list.sceneProperty(), stage.renderScaleYProperty());
        list.minHeightProperty().bind(height);
        list.prefHeightProperty().bind(height);
        list.maxHeightProperty().bind(height);
    }

    /** Replaces only the NPC catalog adapter node after an empty-to-populated transition. */
    private void replaceEmptyNpcMorphAssignmentList() {
        boolean restoreFocus = npcMorphAssignmentList.isFocused();
        ListView<NpcMorphAssignmentSnapshot> replacement = new ListView<>();
        replacement.setId("npcMorphAssignmentList");
        replacement.setAccessibleText("NPC Morph Assignments");
        replacement.setFixedCellSize(MORPH_TARGET_CELL_HEIGHT);
        replacement.setFocusTraversable(true);
        VBox.setVgrow(replacement, javafx.scene.layout.Priority.ALWAYS);
        int index = morphsPrimaryContent.getChildren().indexOf(npcMorphAssignmentList);
        if (index < 0)
            throw new IllegalStateException("Morphs primary content no longer owns the NPC Morph Assignment list");
        morphsPrimaryContent.getChildren().set(index, replacement);
        npcMorphAssignmentList = replacement;
        configureNpcMorphAssignmentList();
        if (restoreFocus) {
            // Refilling the catalog must keep keyboard focus on its fresh accessible node.
            replacement.requestFocus();
            Platform.runLater(replacement::requestFocus);
        }
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

    /** @return current Custom Morph Target ListView, including an accessibility-driven refill replacement */
    ListView<CustomMorphTargetSnapshot> customMorphTargetListNode() {
        return customMorphTargetList;
    }

    /** @return current NPC Morph Assignment ListView, including an accessibility-driven refill replacement */
    ListView<NpcMorphAssignmentSnapshot> npcMorphAssignmentListNode() {
        return npcMorphAssignmentList;
    }

    /** @return current assigned Slider Preset ListView, including an accessibility-driven refill replacement */
    ListView<SliderPresetSnapshot> assignedMorphSliderPresetListNode() {
        return assignedMorphSliderPresetList;
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

    /** Applies System/Light/Dark immediately while submitting its profile write to the application worker. */
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
            JobCoordinator.Admission admission = projectFlow.jobs().submit(themeSubmission(selected, saver));
            if (!admission.admitted())
                renderFeedback(feedback.publishStatus(new WorkbenchFeedback.Notification(
                        "Theme preference", WorkbenchFeedback.Severity.WARNING,
                        "The selected theme is active but could not be queued for saving.",
                        WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES)));
        });
        appearanceAdapter.start();
    }

    /**
     * Captures one theme choice and profile writer for a worker-owned save. The coordinator publishes completion
     * through Activity; a failed optional preference write leaves the already applied appearance active.
     *
     * @param selected immutable System, Light, or Dark choice selected on the JavaFX thread
     * @param saver profile persistence action invoked only by the worker
     * @return captured job submission with the selected choice as its completion value
     */
    private JobCoordinator.Submission<WorkbenchAppearance.ThemeChoice> themeSubmission(
            WorkbenchAppearance.ThemeChoice selected, ThemeChoiceSaver saver) {
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Save Theme Preference", List.of(),
                List.of(), Optional.of(selected.name()), JobCoordinator.ConsistencyClass.SNAPSHOT_DERIVED);
        return new JobCoordinator.Submission<>(operation, context -> {
            context.checkCancellation();
            if (!context.beginCommit("Saving theme preference"))
                return JobCoordinator.Result.cancelled("Theme preference save cancelled.", List.of(), List.of());
            try {
                saver.save(selected);
                return JobCoordinator.Result.completed(selected, "Theme preference saved.",
                        List.of("Theme preference saved"), List.of());
            } catch (IOException exception) {
                return JobCoordinator.Result.completedWithIssues(selected,
                        "The selected theme could not be saved.", List.of(), List.of(
                                new JobCoordinator.Diagnostic("THEME_PREFERENCE_SAVE_FAILED",
                                        "The selected theme could not be saved.", Optional.empty())));
            }
        }, (attempt, result) -> {
            // The coordinator publishes the worker outcome through the ordinary Activity path.
        }, Optional.empty());
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
        if (fillEmptyPopupRoot != null)
            JavaFxWorkbenchAppearance.applyTo(fillEmptyPopupRoot, frame);
        if (npcFilterPopupRoot != null)
            JavaFxWorkbenchAppearance.applyTo(npcFilterPopupRoot, frame);
        if (npcPortraitViewerRoot != null)
            JavaFxWorkbenchAppearance.applyTo(npcPortraitViewerRoot, frame);
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

    /** Applies the current theme to a separate scene opened after the last appearance publication. */
    private void applySatelliteAppearance(Parent root) {
        JavaFxWorkbenchAppearance.applyTo(root, appearanceAdapter.frame());
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
        if (event.isControlDown() && event.getCode() == KeyCode.E) {
            dispatchOutput(new OutputFeature.Export());
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
        if (navigationFrame.activeArea() == WorkbenchNavigation.Area.MORPHS
                && event.isControlDown() && event.getCode() == KeyCode.K) {
            if (navigationFrame.narrowMode())
                applyNavigation(navigation.openPrimaryContent(currentSemanticFocus()));
            TextField filter = morphsFeature.frame().npcSelection().isPresent()
                    ? npcMorphAssignmentFilter : customMorphTargetFilter;
            filter.requestFocus();
            Platform.runLater(filter::requestFocus);
            event.consume();
            return;
        }
        if (navigationFrame.activeArea() == WorkbenchNavigation.Area.NPC_DATABASE
                && event.isControlDown() && event.getCode() == KeyCode.K) {
            npcNameFilterButton.requestFocus();
            Platform.runLater(npcNameFilterButton::requestFocus);
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
        } else if (event.getCode() == KeyCode.ESCAPE
                && navigationFrame.activeArea() == WorkbenchNavigation.Area.MORPHS
                && (morphsFeature.frame().selection().isPresent()
                || morphsFeature.frame().npcSelection().isPresent())) {
            dispatchMorphs(new MorphsFeature.ClearSelection());
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE
                && navigationFrame.activeArea() == WorkbenchNavigation.Area.NPC_DATABASE
                && npcDatabaseFeature.frame().selectedRow().isPresent()) {
            npcDatabaseFeature.clearSelection();
            renderNpcDatabase(npcDatabaseFeature.frame());
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
        boolean leavingNpcDatabase = navigationFrame.activeArea() == WorkbenchNavigation.Area.NPC_DATABASE
                && transition.frame().activeArea() != WorkbenchNavigation.Area.NPC_DATABASE;
        navigationFrame = transition.frame();
        if (leavingNpcDatabase) {
            // Promotion feedback and return selection are visit-scoped; rail navigation must not revive them later.
            npcDatabaseFeature.leaveArea();
            renderNpcDatabase(npcDatabaseFeature.frame());
        }
        if (navigationFrame.activeArea() != WorkbenchNavigation.Area.NPC_DATABASE && npcFilterPopup != null)
            npcFilterPopup.hide();
        renderNavigation(navigationFrame);
        if (navigationFrame.activeArea() == WorkbenchNavigation.Area.MORPHS
                && navigationFrame.overlay() == WorkbenchNavigation.Overlay.INSPECTOR
                && morphsFeature.frame().npcSelection().isPresent()) {
            // A previous relationship edit may leave the ScrollPane at the bottom; opening the narrow inspector
            // must reveal the selected NPC portrait and its viewer action before the relationships below it.
            morphsInspectorScroll.setVvalue(0.0);
            Platform.runLater(() -> morphsInspectorScroll.setVvalue(0.0));
        }
        if (navigationFrame.activeArea() == WorkbenchNavigation.Area.NPC_DATABASE
                && navigationFrame.overlay() == WorkbenchNavigation.Overlay.INSPECTOR) {
            npcDatabaseInspectorScroll.setVvalue(0.0);
            Platform.runLater(() -> npcDatabaseInspectorScroll.setVvalue(0.0));
        }
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
        boolean morphsActive = frame.activeArea() == WorkbenchNavigation.Area.MORPHS;
        boolean npcDatabaseActive = frame.activeArea() == WorkbenchNavigation.Area.NPC_DATABASE;
        boolean settingsActive = frame.activeArea() == WorkbenchNavigation.Area.SETTINGS;
        templatesPrimaryScroll.setManaged(templatesActive);
        templatesPrimaryScroll.setVisible(templatesActive);
        templatesEditorContent.setManaged(templatesActive);
        templatesEditorContent.setVisible(templatesActive);
        templatesInspectorScroll.setManaged(templatesActive);
        templatesInspectorScroll.setVisible(templatesActive);
        morphsPrimaryScroll.setManaged(morphsActive);
        morphsPrimaryScroll.setVisible(morphsActive);
        morphsEditorContent.setManaged(morphsActive);
        morphsEditorContent.setVisible(morphsActive);
        morphsInspectorScroll.setManaged(morphsActive);
        morphsInspectorScroll.setVisible(morphsActive);
        npcDatabasePrimaryScroll.setManaged(npcDatabaseActive);
        npcDatabasePrimaryScroll.setVisible(npcDatabaseActive);
        npcDatabaseEditorContent.setManaged(npcDatabaseActive);
        npcDatabaseEditorContent.setVisible(npcDatabaseActive);
        npcDatabaseInspectorScroll.setManaged(npcDatabaseActive);
        npcDatabaseInspectorScroll.setVisible(npcDatabaseActive);
        settingsPrimaryScroll.setManaged(settingsActive);
        settingsPrimaryScroll.setVisible(settingsActive);
        settingsEditorContent.setManaged(settingsActive);
        settingsEditorContent.setVisible(settingsActive);
        settingsInspectorContent.setManaged(settingsActive);
        settingsInspectorContent.setVisible(settingsActive);
        boolean placeholderActive = !templatesActive && !morphsActive && !npcDatabaseActive && !settingsActive;
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
        if (focusOwner == addNpcsFromDatabaseButton) {
            landmark = WorkbenchNavigation.Landmark.NPC_DATABASE_LAUNCHER;
        } else if (focusOwner == sliderPresetFilter || focusOwner == sliderPresetList
                || focusOwner == sliderPresetNameInput || focusOwner == importBodySlideButton
                || focusOwner == customMorphTargetFilter || focusOwner == customMorphTargetSort
                || focusOwner == customMorphTargetList || focusOwner == customMorphTargetNameInput
                || focusOwner == createCustomMorphTargetButton || focusOwner == removeCustomMorphTargetButton
                || focusOwner == clearCustomMorphTargetsButton
                || focusOwner == npcMorphAssignmentFilter || focusOwner == npcMorphAssignmentSort
                || focusOwner == npcMorphAssignmentList || focusOwner == npcDisplayNameInput
                || focusOwner == npcPluginNameInput || focusOwner == npcEditorIdInput
                || focusOwner == npcRaceInput || focusOwner == npcFormIdInput
                || focusOwner == createNpcMorphAssignmentButton || focusOwner == removeNpcMorphAssignmentButton
                || focusOwner == clearNpcMorphAssignmentsButton
                || focusOwner == npcSourceList || focusOwner == importNpcSourcesButton
                || focusOwner == removeNpcSourceButton || focusOwner == clearNpcDatabaseButton
                || focusOwner == settingsProfileChoice || focusOwner == settingsEntryList
                || focusOwner == newSettingsEntryName || focusOwner == addSettingsEntryButton) {
            landmark = WorkbenchNavigation.Landmark.PRIMARY_CONTENT;
        } else if (focusOwner == templateEditorFocusTarget || focusOwner == sliderPresetProfile
                || focusOwner == morphTargetEditorFocusTarget
                || focusOwner == npcCatalogTable || focusOwner == npcSortChoice
                || focusOwner == backToMorphsButton || focusOwner == addAllNpcsToProjectButton
                || focusOwner == npcPromotionDetails
                || npcFilterButtons.containsValue(focusOwner)
                || focusOwner == clearNpcFiltersButton
                || sliderChoiceRowsByName.values().stream().anyMatch(row -> row.contains(focusOwner))
                || focusOwner == settingsEntryNameInput || focusOwner == settingsSmallInput
                || focusOwner == settingsBigInput || focusOwner == settingsMultiplierInput
                || focusOwner == settingsInvertedCheck || focusOwner == applySettingsEntryButton
                || focusOwner == removeSettingsEntryButton) {
            landmark = WorkbenchNavigation.Landmark.EDITOR;
        } else if (focusOwner == templateSelectionText
                || focusOwner == morphTargetSelectionText || focusOwner == assignedMorphSliderPresetList
                || focusOwner == npcInspectorName || focusOwner == npcInspectorSource
                || focusOwner == openNpcDatabasePortraitViewerButton
                || focusOwner == addNpcToProjectButton || focusOwner == assignRandomNpcPresetCheck
                || focusOwner == availableMorphSliderPreset || focusOwner == assignMorphSliderPresetButton
                || focusOwner == assignAllMorphSliderPresetsButton || focusOwner == removeMorphSliderPresetButton
                || focusOwner == clearMorphSliderPresetsButton
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
                case MORPHS -> morphsFeature.frame().npcSelection().isPresent()
                        ? npcMorphAssignmentList : customMorphTargetList;
                case SETTINGS -> settingsEntryList;
                case NPC_DATABASE -> npcCatalogTable;
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
            case NPC_DATABASE_LAUNCHER -> addNpcsFromDatabaseButton;
            case PRIMARY_LAUNCHER -> showPrimaryOverlayButton;
            case PRIMARY_CONTENT -> switch (target.area()) {
                case TEMPLATES -> sliderPresetList;
                case MORPHS -> morphsFeature.frame().npcSelection().isPresent()
                        ? npcMorphAssignmentList : customMorphTargetList;
                case SETTINGS -> settingsEntryList;
                case NPC_DATABASE -> npcSourceList;
            };
            case EDITOR -> switch (target.area()) {
                case TEMPLATES -> firstSliderChoiceControl().orElse(templateEditorFocusTarget);
                case MORPHS -> morphTargetEditorFocusTarget;
                case SETTINGS -> settingsEntryNameInput;
                case NPC_DATABASE -> npcCatalogTable;
            };
            case INSPECTOR_LAUNCHER -> showInspectorOverlayButton;
            case INSPECTOR -> switch (target.area()) {
                case TEMPLATES -> renameSliderPresetButton.isDisabled()
                        ? templateSelectionText : renameSliderPresetButton;
                case MORPHS -> morphsFeature.frame().npcSelection().isPresent()
                        ? morphTargetSelectionText : availableMorphSliderPreset.isDisabled()
                        ? morphTargetSelectionText : availableMorphSliderPreset;
                case SETTINGS -> saveSettingsButton.isDisabled() ? settingsNoticeText : saveSettingsButton;
                case NPC_DATABASE -> npcInspectorName;
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
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(spec);
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        renderFeedback(pendingFrame);
        WorkbenchFeedback.DialogAction action = platform.completeConfirmation(spec, stage);
        renderFeedback(feedback.answerDialog(new WorkbenchFeedback.DialogResult(pending.token(), action)).frame());
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
        SettingsFeature.Update save = dispatchSettings(new SettingsFeature.Save(), Optional.of(returnTarget));
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

    /** Renders the Project snapshot with diagnostics chosen by the operation that owns the current report. */
    private void render(WorkbenchProjectFlow.Frame frame, List<ProjectDiagnostic> diagnostics) {
        stage.setTitle(frame.title());
        projectStatusText.setText(projectStatus(frame));
        diagnosticsText.setText(ProjectDiagnosticFormatter.format(diagnostics));
        boolean lifecyclePublication = frame.sequence() != renderedProjectSequence;
        boolean lifecycleReset = lifecyclePublication
                && (activeOperation == WorkbenchProjectFlow.Intent.NEW
                || activeOperation == WorkbenchProjectFlow.Intent.OPEN);
        boolean resetTemplates = resetTemplatesOnNextProjectFrame || lifecycleReset;
        boolean resetMorphs = resetMorphsOnNextProjectFrame || lifecycleReset;
        boolean resetOutput = resetTemplatesOnNextProjectFrame || resetMorphsOnNextProjectFrame || lifecycleReset;
        if (lifecycleReset && npcDatabaseFeature != null) {
            navigation.clearNpcDatabaseReturn();
            npcDatabaseFeature.clearAllSelections();
            renderNpcDatabase(npcDatabaseFeature.frame());
        }
        if (templatesFeature != null && templatesFeature.frame().projectSequence() != frame.sequence()) {
            renderTemplates(templatesFeature.acceptProjectFrame(frame, resetTemplates,
                    morphsOwnProjectDiagnostics ? List.of() : frame.diagnostics()).frame());
        }
        if (morphsFeature != null && morphsFeature.frame().projectSequence() != frame.sequence())
            renderMorphs(morphsFeature.acceptProjectFrame(frame, resetMorphs,
                    templatesOwnProjectDiagnostics ? List.of() : frame.diagnostics()).frame());
        if (outputFeature != null)
            renderOutput(outputFeature.acceptProjectFrame(frame, resetOutput).frame());
        resetTemplatesOnNextProjectFrame = false;
        resetMorphsOnNextProjectFrame = false;
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
        templatesMutationsBlocked = frame.projectMutationsBlocked();
        morphsMutationsBlocked = frame.projectMutationsBlocked();
        settingsMutationsBlocked = frame.projectMutationsBlocked();
        newProjectMenuItem.setDisable(blocked);
        openProjectMenuItem.setDisable(blocked);
        saveProjectMenuItem.setDisable(blocked);
        themeChoice.setDisable(blocked);
        saveAsProjectMenuItem.setDisable(blocked);
        importBodySlideButton.setDisable(blocked);
        importNpcSourcesButton.setDisable(blocked);
        removeNpcSourceButton.setDisable(blocked || npcDatabaseFeature.frame().selectedSource().isEmpty());
        clearNpcDatabaseButton.setDisable(blocked || npcDatabaseFeature.frame().visibleRows().isEmpty());
        generateOutputButton.setDisable(blocked || !settingsFeature.frame().liveAvailable());
        renderTemplates(templatesFeature.frame());
        renderMorphs(morphsFeature.frame());
        renderNpcDatabase(npcDatabaseFeature.frame());
        renderSettings(settingsFeature.frame());
        renderOutput(outputFeature.frame());
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
                npcDatabaseFeature.clearAllSelections();
                renderNpcDatabase(npcDatabaseFeature.frame());
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
        if (settingsCloseContinuation != null && attempt.operation().name().equals("Save Settings")
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
        WorkbenchFeedback.Frame pendingFrame = feedback.requestDialog(spec);
        WorkbenchFeedback.PendingDialog pending = pendingFrame.pendingDialog().orElseThrow();
        renderFeedback(pendingFrame);
        WorkbenchFeedback.DialogAction action = platform.completeFailure(spec, stage);
        renderFeedback(feedback.answerDialog(new WorkbenchFeedback.DialogResult(
                pending.token(), action)).frame());
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
     * Profile persistence function whose checked failure becomes nonmodal feedback.
     */
    @FunctionalInterface
    interface ThemeChoiceSaver {
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

    /** Renders one Slider Preset relationship endpoint by its canonical Project display name. */
    private static final class SliderPresetDisplayCell extends ListCell<SliderPresetSnapshot> {
        /** {@inheritDoc} */
        @Override
        protected void updateItem(SliderPresetSnapshot preset, boolean empty) {
            super.updateItem(preset, empty);
            if (empty || preset == null) {
                setText(null);
                setAccessibleText(null);
                setAccessibleHelp(null);
            } else {
                setText(preset.getName());
                setAccessibleText(preset.getName());
                setAccessibleHelp("Slider Preset relationship " + preset.getName());
            }
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
