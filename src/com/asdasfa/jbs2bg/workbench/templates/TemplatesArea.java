package com.asdasfa.jbs2bg.workbench.templates;

import static com.asdasfa.jbs2bg.workbench.WorkbenchFocusPolicy.ownsSceneFocus;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.AreaPanes;
import com.asdasfa.jbs2bg.workbench.CatalogHeight;
import com.asdasfa.jbs2bg.workbench.DispatchPipeline;
import com.asdasfa.jbs2bg.workbench.SeverityStyle;
import com.asdasfa.jbs2bg.workbench.ShellPort;
import com.asdasfa.jbs2bg.workbench.UiaListHelper;
import com.asdasfa.jbs2bg.workbench.WorkbenchArea;
import com.asdasfa.jbs2bg.workbench.WorkbenchContext;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Templates Area: the Slider Preset catalog with its management controls and BodySlide import launcher, the
 * in-place Set Sliders editor, and the inspector with rename and the gang controls. It translates its controls into
 * {@link TemplatesFeature} intents through the shared {@link DispatchPipeline}, renders only committed immutable
 * frames, and words its own feedback. It keeps the Templates-only UI Automation workarounds: the ListView edit
 * lifecycle instead of {@code refresh()} for inline rename, {@link SliderChoiceRow} identity reuse, and the guarded
 * rename-editor focus deferral. Running the BodySlide import, revealing the narrow primary content, feedback and
 * semantic focus go through the {@link ShellPort}.
 * <p>
 * Loaded from {@code templates-area.fxml} by {@link #load}; every method runs on the JavaFX Application Thread.
 */
public final class TemplatesArea implements WorkbenchArea {
    private static final double SLIDER_PRESET_CELL_HEIGHT = 28.0;
    private static final int MAX_VISIBLE_SLIDER_PRESET_ROWS = 8;

    private final TemplatesFeature feature;
    private final DispatchPipeline pipeline;
    private final ShellPort shell;
    private final UiaListHelper uiaLists;
    private final Supplier<Settings.Snapshot> settings;
    private final ReadOnlyDoubleProperty renderScale;
    @FXML
    private AreaPanes panes;
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
    private CheckBox gangAllCheck;
    @FXML
    private CheckBox gangMinimumCheck;
    @FXML
    private CheckBox gangMaximumCheck;
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
    // The latest context the shell rendered; this Area's own update renders read cross-Area inputs only from here.
    private WorkbenchContext context;
    private boolean renderingTemplates;
    private TextField activeRenameField;
    private SliderPresetCell activeRenameCell;
    // The Slider Preset list's UIA workaround state; bound once its owner pane has been injected.
    private UiaListHelper.RefillingList<SliderPresetSnapshot> sliderPresetItems;
    private final Map<String, SliderChoiceRow> sliderChoiceRowsByName = new LinkedHashMap<>();

    /**
     * Creates the controller FXMLLoader injects; use {@link #load} rather than calling this directly.
     *
     * @param feature     the window-scoped Templates feature, built by the shell's composition root
     * @param pipeline    the shared dispatch pipeline that gates mutations and runs the dialog round-trip
     * @param shell       the shell port for feedback, focus, the BodySlide import and the narrow primary content
     * @param uiaLists    the shared UIA list helper that refills the Slider Preset list
     * @param settings    read-only view of the current Settings pair, which the Slider choice rows preview drags with
     * @param renderScale the Workbench window's vertical render scale, which the catalog height snaps to
     * @throws NullPointerException when an argument is null
     */
    public TemplatesArea(TemplatesFeature feature, DispatchPipeline pipeline, ShellPort shell, UiaListHelper uiaLists,
                         Supplier<Settings.Snapshot> settings, ReadOnlyDoubleProperty renderScale) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.shell = Objects.requireNonNull(shell, "shell");
        this.uiaLists = Objects.requireNonNull(uiaLists, "uiaLists");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.renderScale = Objects.requireNonNull(renderScale, "renderScale");
    }

    /**
     * Loads {@code templates-area.fxml} with a controller constructor-injected with these collaborators. The Area
     * renders nothing until the shell's first {@link #render(WorkbenchContext)}.
     *
     * @param feature     the Templates feature
     * @param pipeline    the shared dispatch pipeline
     * @param shell       the shell port
     * @param uiaLists    the shared UIA list helper
     * @param settings    read-only view of the current Settings pair
     * @param renderScale the Workbench window's vertical render scale
     * @return the loaded Area and its FXML namespace
     */
    public static AreaLoader.Loaded<TemplatesArea> load(TemplatesFeature feature, DispatchPipeline pipeline,
                                                        ShellPort shell, UiaListHelper uiaLists,
                                                        Supplier<Settings.Snapshot> settings,
                                                        ReadOnlyDoubleProperty renderScale) {
        return AreaLoader.load(TemplatesArea.class, "templates-area.fxml",
                () -> new TemplatesArea(feature, pipeline, shell, uiaLists, settings, renderScale));
    }

    /**
     * Translates Templates controls into feature-specific typed intents and renders only committed immutable frames.
     * FXMLLoader calls this once every control is injected.
     */
    @FXML
    private void initialize() {
        sliderPresetSort.getItems().setAll(TemplatesFeature.SortOrder.values());
        sliderPresetSort.setValue(feature.frame().sortOrder());
        sliderPresetProfile.getItems().setAll(TemplatesFeature.Profile.values());
        configureSliderPresetList();
        sliderPresetItems = uiaLists.refilling(() -> sliderPresetList, templatesPrimaryContent,
                "Templates primary content no longer owns the Slider Preset list",
                this::newSliderPresetList, this::adoptSliderPresetList);
        sliderPresetFilter.textProperty().addListener((observable, previous, current) -> {
            if (!renderingTemplates)
                dispatch(new TemplatesFeature.ChangeFilter(current));
        });
        sliderPresetSort.setOnAction(event -> {
            if (!renderingTemplates && sliderPresetSort.getValue() != null)
                dispatch(new TemplatesFeature.ChangeSort(sliderPresetSort.getValue()));
        });
        sliderPresetProfile.setOnAction(event -> {
            if (!renderingTemplates && sliderPresetProfile.getValue() != null
                    && feature.frame().editor().stream()
                    .anyMatch(editor -> editor.profile() != sliderPresetProfile.getValue()))
                dispatch(new TemplatesFeature.ChangeProfile(sliderPresetProfile.getValue()));
        });
        createSliderPresetButton.setOnAction(event ->
                dispatch(new TemplatesFeature.Create(sliderPresetNameInput.getText())));
        duplicateSliderPresetButton.setOnAction(event ->
                dispatch(new TemplatesFeature.Duplicate(sliderPresetNameInput.getText())));
        renameSliderPresetButton.setOnAction(event ->
                dispatch(new TemplatesFeature.BeginRename()));
        removeSliderPresetButton.setOnAction(event ->
                dispatch(new TemplatesFeature.RequestRemove()));
        clearSliderPresetsButton.setOnAction(event ->
                dispatch(new TemplatesFeature.RequestClearVisible()));
        dismissTemplatesInfoBarButton.setOnAction(event ->
                dispatch(new TemplatesFeature.DismissDiagnostics()));
        // BodySlide import is a Project command, so the shell's Project lifecycle runs it.
        importBodySlideButton.setOnAction(event -> shell.importBodySlidePresets());
        configureGangControls();
    }

    /** @return the holder FXMLLoader built from this Area's FXML root */
    @Override
    public AreaPanes panes() {
        return panes;
    }

    /**
     * Keeps the shell's context and renders the Templates feature's current frame against it.
     *
     * @param context the context the shell just built
     */
    @Override
    public void render(WorkbenchContext context) {
        this.context = Objects.requireNonNull(context, "context");
        renderTemplates(feature.frame());
    }

    /**
     * Resolves the Templates landmarks: the Slider Preset catalog, the first Slider choice row of the selected Slider
     * Preset (the editor title while there is none), and Rename while it is usable (the selection text otherwise).
     *
     * @param landmark the landmark to resolve
     * @return the node for one of the Area's landmarks, otherwise null
     */
    @Override
    public Node focusNode(WorkbenchNavigation.Landmark landmark) {
        return switch (landmark) {
            case PRIMARY_CONTENT -> sliderPresetList;
            case EDITOR -> firstSliderChoiceControl().orElse(templateEditorFocusTarget);
            case INSPECTOR -> renameSliderPresetButton.isDisabled() ? templateSelectionText : renameSliderPresetButton;
            case RAIL, NPC_DATABASE_LAUNCHER, PRIMARY_LAUNCHER, INSPECTOR_LAUNCHER, OUTPUT_LAUNCHER, OUTPUT, ACTIVITY,
                 STATUS -> null;
        };
    }

    /** Ctrl+K reaches the Slider Preset filter. */
    @Override
    public void focusSearch() {
        // The filter lives in the primary content, which narrow mode shows only as an overlay; open it first.
        shell.revealPrimaryContent();
        sliderPresetFilter.requestFocus();
    }

    /**
     * Takes F2, which starts renaming the selected Slider Preset, and Escape while a rename is open, which cancels it.
     * The shell offers them before its navigation keys, so Escape cancels an open rename before it would dismiss the
     * narrow overlay or the Output drawer around it.
     *
     * @param event the key press
     * @return true when the key was F2, or Escape with a rename open
     */
    @Override
    public boolean handleKey(KeyEvent event) {
        if (event.getCode() == KeyCode.F2) {
            dispatch(new TemplatesFeature.BeginRename());
            return true;
        }
        if (event.getCode() == KeyCode.ESCAPE && feature.frame().rename().isPresent()) {
            dispatch(new TemplatesFeature.CancelRename());
            return true;
        }
        return false;
    }

    /**
     * Escape clears the Slider Preset selection while one is selected.
     *
     * @return true when a selection was cleared
     */
    @Override
    public boolean escape() {
        if (feature.frame().selection().isEmpty())
            return false;
        dispatch(new TemplatesFeature.ClearSelection());
        return true;
    }

    /** Leaving Templates needs no cleanup. */
    @Override
    public void leave() {
        // Templates has no popups and no visit-scoped state; an open rename stays with the feature frame and is
        // there again when the Area is next shown.
    }

    /** Opening the narrow inspector keeps the Templates inspector's scroll position. */
    @Override
    public void resetInspectorScroll() {
        // The Templates inspector starts with the selection and Rename, which the narrow overlay already shows first;
        // the shell never reset its scroll position before the Area had its own FXML either.
    }

    /** @return current Slider Preset ListView node, including an accessibility-driven empty/refill replacement */
    public ListView<SliderPresetSnapshot> sliderPresetListNode() {
        return sliderPresetList;
    }

    /** @return the currently materialized inline rename field, when its virtualized row is visible */
    public Optional<TextField> activeRenameField() {
        return Optional.ofNullable(activeRenameField);
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
        gangAllCheck.setOnAction(event -> dispatch(new TemplatesFeature.ToggleGang(
                TemplatesFeature.GangMode.ALL, gangAllCheck.isSelected())));
        gangMinimumCheck.setOnAction(event -> dispatch(new TemplatesFeature.ToggleGang(
                TemplatesFeature.GangMode.MINIMUM, gangMinimumCheck.isSelected())));
        gangMaximumCheck.setOnAction(event -> dispatch(new TemplatesFeature.ToggleGang(
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
        button.setOnAction(event -> dispatch(new TemplatesFeature.ApplyBulkValue(mode, value)));
    }

    /**
     * Keeps pointer drags local until release while conventional keyboard Slider changes publish immediately.
     */
    private void configureGangSlider(Slider slider, Label valueLabel, TemplatesFeature.GangMode mode) {
        slider.valueProperty().addListener((observable, previous, current) -> {
            valueLabel.setText(current.intValue() + "%");
            if (!renderingTemplates && !slider.isValueChanging())
                dispatch(new TemplatesFeature.ApplyBulkValue(mode, current.intValue()));
        });
        slider.valueChangingProperty().addListener((observable, previous, changing) -> {
            if (!renderingTemplates && !changing.booleanValue())
                dispatch(new TemplatesFeature.ApplyBulkValue(mode, (int) slider.getValue()));
        });
    }

    /**
     * Configures the current Slider Preset ListView instance; the UIA empty/refill workaround may replace that node.
     */
    private void configureSliderPresetList() {
        CatalogHeight.bind(sliderPresetList, SLIDER_PRESET_CELL_HEIGHT, MAX_VISIBLE_SLIDER_PRESET_ROWS, renderScale);
        sliderPresetList.setEditable(true);
        sliderPresetList.setFixedCellSize(SLIDER_PRESET_CELL_HEIGHT);
        sliderPresetList.setCellFactory(list -> new SliderPresetCell());
        sliderPresetList.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (renderingTemplates)
                return;
            dispatch(selected == null
                    ? new TemplatesFeature.ClearSelection()
                    : new TemplatesFeature.Select(NameIdentity.of(selected.getName())));
        });
        sliderPresetList.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!(event.getTarget() instanceof TextInputControl) && event.getCharacter().length() == 1
                    && !event.isControlDown() && !event.isAltDown()) {
                dispatch(new TemplatesFeature.TypeAhead(event.getCharacter().charAt(0)));
                event.consume();
            }
        });
    }

    /**
     * Runs one Templates intent through the gated dispatch pipeline. The shell keeps the Project diagnostics of a
     * Templates edit with Templates: its chrome step sees the Templates update and leaves the Morphs projection clear.
     *
     * @param intent requested Templates task
     */
    private void dispatch(TemplatesFeature.Intent intent) {
        pipeline.dispatch(feature, DispatchPipeline.Callbacks.of(
                update -> renderTemplates(update.frame()),
                effect -> DispatchPipeline.Realization.confirm(WorkbenchFeedback.DialogSpec.destructiveAction(
                                effect.title(), effect.message(), templatesDestructiveAction(effect)),
                        action -> feature.respond(effect.token(), action == templatesDestructiveAction(effect))),
                this::reportTemplatesOutcome), Objects.requireNonNull(intent, "intent"));
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
            shell.publishFeedback(feedback -> feedback.publishActivity(new WorkbenchFeedback.Notification(
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
        shell.publishFeedback(feedback -> bulk
                ? feedback.publishActivity(notification)
                : feedback.publishStatus(notification));
        if (update.outcomeKind() == TemplatesFeature.OutcomeKind.FAILED)
            showTemplatesFailure(update);
    }

    /**
     * Realizes an unexpected synchronous Templates failure as the accepted focus-restoring failure dialog. The exact
     * Slider choice row control or Templates control that had focus gets it back; only when neither is still
     * reachable does the shell restore the semantic target.
     */
    private void showTemplatesFailure(TemplatesFeature.Update update) {
        Optional<SliderChoiceFocus> exactFocus = currentSliderChoiceFocus();
        Optional<TemplatesControlFocus> exactControlFocus = currentTemplatesControlFocus();
        WorkbenchNavigation.FocusTarget returnTarget = shell.currentFocus();
        String details = ProjectDiagnosticFormatter.format(update.frame().diagnostics());
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.failure(
                "Slider choice editing failed", "The Slider Preset could not be edited.",
                details.isBlank() ? "No diagnostic details were provided." : details, false);
        pipeline.fail(spec);
        if (!restoreSliderChoiceFocus(exactFocus) && !restoreTemplatesControlFocus(exactControlFocus))
            shell.requestFocus(returnTarget);
    }

    /** @return the focus owner of the Workbench scene this Area's panes are shown in, if any */
    private Optional<Node> focusOwner() {
        // The editor pane never leaves the Workbench scene: narrow mode reparents only the primary and inspector panes.
        Scene scene = panes.getEditor().getScene();
        return scene == null ? Optional.empty() : Optional.ofNullable(scene.getFocusOwner());
    }

    /**
     * Captures the focused Slider choice through semantic identities rather than retaining a JavaFX Node reference.
     */
    private Optional<SliderChoiceFocus> currentSliderChoiceFocus() {
        Node focusOwner = focusOwner().orElse(null);
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
        Node focusOwner = focusOwner().orElse(null);
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
        Map<TemplatesControlFocus, Node> controls = new EnumMap<>(TemplatesControlFocus.class);
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
        shell.publishFeedback(feedback -> feedback.publishActivity(new WorkbenchFeedback.Notification(operation,
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
                row = new SliderChoiceRow(this::dispatch, settings);
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
        VBox.setVgrow(replacement, Priority.ALWAYS);
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
     * Resolves the first canonical Slider choice row without retaining a control reference in navigation state.
     */
    private Optional<Node> firstSliderChoiceControl() {
        return sliderChoiceRowsByName.values().stream().findFirst().map(SliderChoiceRow::enabledControl)
                .map(Node.class::cast);
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
                if (!rendering && feature.frame().rename().isPresent())
                    dispatch(new TemplatesFeature.ChangeRename(current));
            });
            renameField.setOnAction(event -> dispatch(new TemplatesFeature.CommitRename()));
            renameField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    dispatch(new TemplatesFeature.CancelRename());
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
            Optional<TemplatesFeature.RenameState> rename = feature.frame().rename()
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
            Optional<TemplatesFeature.RenameState> active = feature.frame().rename()
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
}
