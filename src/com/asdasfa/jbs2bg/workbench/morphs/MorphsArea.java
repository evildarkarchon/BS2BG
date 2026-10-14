package com.asdasfa.jbs2bg.workbench.morphs;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.AreaPanes;
import com.asdasfa.jbs2bg.workbench.CatalogHeight;
import com.asdasfa.jbs2bg.workbench.DispatchPipeline;
import com.asdasfa.jbs2bg.workbench.NpcPortraitViewer;
import com.asdasfa.jbs2bg.workbench.SeverityStyle;
import com.asdasfa.jbs2bg.workbench.ShellPort;
import com.asdasfa.jbs2bg.workbench.UiaListHelper;
import com.asdasfa.jbs2bg.workbench.WorkbenchArea;
import com.asdasfa.jbs2bg.workbench.WorkbenchContext;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation;
import com.asdasfa.jbs2bg.workbench.WorkbenchSatellites;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;

/**
 * The Morphs Area: the Custom Morph Target and NPC Morph Assignment catalogs with their authoring controls, the
 * shared editor summary, the Slider Preset relationship inspector with the NPC portrait thumbnail, and the Fill Empty
 * flyout. It translates its controls into {@link MorphsFeature} intents through the shared {@link DispatchPipeline},
 * renders only committed immutable frames, and words its own feedback. Opening the NPC Database, revealing the
 * narrow primary content, feedback, and semantic focus go through the {@link ShellPort}.
 * <p>
 * Loaded from {@code morphs-area.fxml} by {@link #load}; every method runs on the JavaFX Application Thread.
 */
public final class MorphsArea implements WorkbenchArea {
    private static final double MORPH_TARGET_CELL_HEIGHT = 28.0;
    private static final int MAX_VISIBLE_MORPH_TARGET_ROWS = 8;
    // The Fill Empty checklist lists Slider Presets at the Templates catalog's fixed row height.
    private static final double SLIDER_PRESET_CELL_HEIGHT = 28.0;

    private final MorphsFeature feature;
    private final DispatchPipeline pipeline;
    private final ShellPort shell;
    private final NpcPortraitViewer portraits;
    private final UiaListHelper uiaLists;
    private final WorkbenchSatellites satellites;
    private final Stage owner;
    private final ReadOnlyDoubleProperty renderScale;
    @FXML
    private AreaPanes panes;
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
    private Button addNpcsFromDatabaseButton;
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
    // The latest context the shell rendered; this Area's own update renders read cross-Area inputs only from here.
    private WorkbenchContext context;
    private boolean renderingMorphs;
    // The three refilling lists' UIA workaround state; each is bound once its owner pane has been injected.
    private UiaListHelper.RefillingList<CustomMorphTargetSnapshot> customMorphTargetItems;
    private UiaListHelper.RefillingList<NpcMorphAssignmentSnapshot> npcMorphAssignmentItems;
    private UiaListHelper.RefillingList<SliderPresetSnapshot> assignedMorphSliderPresetItems;
    private Optional<NameIdentity> renderedMorphTargetSelection = Optional.empty();
    private Optional<NpcMorphAssignmentIdentity> renderedNpcSelection = Optional.empty();
    // The shared portrait viewer's thumbnail loader for this inspector; bound once the controls are injected.
    private NpcPortraitViewer.Thumbnail portrait;

    /**
     * Creates the controller FXMLLoader injects; use {@link #load} rather than calling this directly.
     *
     * @param feature     the window-scoped Morphs feature, built by the shell's composition root
     * @param pipeline    the shared dispatch pipeline that gates mutations and runs the dialog round-trip
     * @param shell       the shell port for feedback, focus, opening the NPC Database and the narrow primary content
     * @param portraits   the shared NPC portrait viewer this Area's inspector thumbnail opens
     * @param uiaLists    the shared UIA list helper that refills the three Morphs lists
     * @param satellites  the shell's appearance, which re-themes and dismisses the Fill Empty flyout
     * @param owner       the Workbench window that owns the Fill Empty flyout
     * @param renderScale the Workbench window's vertical render scale, which the catalog heights snap to
     * @throws NullPointerException when an argument is null
     */
    public MorphsArea(MorphsFeature feature, DispatchPipeline pipeline, ShellPort shell, NpcPortraitViewer portraits,
                      UiaListHelper uiaLists, WorkbenchSatellites satellites, Stage owner,
                      ReadOnlyDoubleProperty renderScale) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.shell = Objects.requireNonNull(shell, "shell");
        this.portraits = Objects.requireNonNull(portraits, "portraits");
        this.uiaLists = Objects.requireNonNull(uiaLists, "uiaLists");
        this.satellites = Objects.requireNonNull(satellites, "satellites");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.renderScale = Objects.requireNonNull(renderScale, "renderScale");
    }

    /**
     * Loads {@code morphs-area.fxml} with a controller constructor-injected with these collaborators. The Area
     * renders nothing until the shell's first {@link #render(WorkbenchContext)}.
     *
     * @param feature     the Morphs feature
     * @param pipeline    the shared dispatch pipeline
     * @param shell       the shell port
     * @param portraits   the shared NPC portrait viewer
     * @param uiaLists    the shared UIA list helper
     * @param satellites  the shell's appearance for satellites
     * @param owner       the Workbench window
     * @param renderScale the Workbench window's vertical render scale
     * @return the loaded Area and its FXML namespace
     */
    public static AreaLoader.Loaded<MorphsArea> load(MorphsFeature feature, DispatchPipeline pipeline,
                                                     ShellPort shell, NpcPortraitViewer portraits,
                                                     UiaListHelper uiaLists, WorkbenchSatellites satellites,
                                                     Stage owner, ReadOnlyDoubleProperty renderScale) {
        return AreaLoader.load(MorphsArea.class, "morphs-area.fxml",
                () -> new MorphsArea(feature, pipeline, shell, portraits, uiaLists, satellites, owner, renderScale));
    }

    /**
     * Translates Morphs catalog and relationship controls into feature-specific typed intents. FXMLLoader calls this
     * once every control is injected.
     */
    @FXML
    private void initialize() {
        // The shared viewer wires openNpcPortraitViewerButton to open itself.
        portrait = portraits.thumbnail(npcPortraitImage, npcPortraitStatus, openNpcPortraitViewerButton,
                "Select an NPC Morph Assignment to inspect its portrait.");
        customMorphTargetSort.getItems().setAll(MorphsFeature.SortOrder.values());
        customMorphTargetSort.setValue(feature.frame().sortOrder());
        configureCustomMorphTargetList();
        customMorphTargetItems = uiaLists.refilling(() -> customMorphTargetList, morphsPrimaryContent,
                "Morphs primary content no longer owns the Custom Morph Target list",
                this::newCustomMorphTargetList, this::adoptCustomMorphTargetList);
        npcMorphAssignmentSort.getItems().setAll(MorphsFeature.NpcSortOrder.values());
        npcMorphAssignmentSort.setValue(feature.frame().npcSortOrder());
        configureNpcMorphAssignmentList();
        npcMorphAssignmentItems = uiaLists.refilling(() -> npcMorphAssignmentList, morphsPrimaryContent,
                "Morphs primary content no longer owns the NPC Morph Assignment list",
                this::newNpcMorphAssignmentList, this::adoptNpcMorphAssignmentList);
        customMorphTargetFilter.textProperty().addListener((observable, previous, current) -> {
            if (!renderingMorphs)
                dispatch(new MorphsFeature.ChangeFilter(current));
        });
        customMorphTargetSort.setOnAction(event -> {
            if (!renderingMorphs && customMorphTargetSort.getValue() != null)
                dispatch(new MorphsFeature.ChangeSort(customMorphTargetSort.getValue()));
        });
        createCustomMorphTargetButton.setOnAction(event ->
                dispatch(new MorphsFeature.Create(customMorphTargetNameInput.getText())));
        removeCustomMorphTargetButton.setOnAction(event -> dispatch(new MorphsFeature.RequestRemove()));
        clearCustomMorphTargetsButton.setOnAction(event -> dispatch(new MorphsFeature.RequestClearVisible()));
        npcMorphAssignmentFilter.textProperty().addListener((observable, previous, current) -> {
            if (!renderingMorphs)
                dispatch(new MorphsFeature.ChangeNpcFilter(current));
        });
        npcMorphAssignmentSort.setOnAction(event -> {
            if (!renderingMorphs && npcMorphAssignmentSort.getValue() != null)
                dispatch(new MorphsFeature.ChangeNpcSort(npcMorphAssignmentSort.getValue()));
        });
        createNpcMorphAssignmentButton.setOnAction(event -> dispatch(new MorphsFeature.CreateNpc(
                npcDisplayNameInput.getText(), npcPluginNameInput.getText(), npcEditorIdInput.getText(),
                npcRaceInput.getText(), npcFormIdInput.getText())));
        removeNpcMorphAssignmentButton.setOnAction(event -> dispatch(new MorphsFeature.RequestRemoveNpc()));
        clearNpcMorphAssignmentsButton.setOnAction(event ->
                dispatch(new MorphsFeature.RequestClearVisibleNpcs()));
        fillEmptyNpcMorphAssignmentsButton.setOnAction(event ->
                dispatch(new MorphsFeature.RequestFillEmpty()));
        // The NPC Database is another Area, so the shell opens it; Back to Morphs returns to this launcher.
        addNpcsFromDatabaseButton.setOnAction(event -> shell.openNpcDatabase(new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.MORPHS, WorkbenchNavigation.Landmark.NPC_DATABASE_LAUNCHER)));
        dismissMorphsInfoBarButton.setOnAction(event -> dispatch(new MorphsFeature.DismissDiagnostics()));

        configureAssignedMorphSliderPresetList();
        assignedMorphSliderPresetItems = uiaLists.refilling(() -> assignedMorphSliderPresetList,
                morphsInspectorContent, "Morphs inspector no longer owns the assigned Slider Preset list",
                this::newAssignedMorphSliderPresetList, this::adoptAssignedMorphSliderPresetList);
        availableMorphSliderPreset.setCellFactory(list -> new SliderPresetDisplayCell());
        availableMorphSliderPreset.setButtonCell(new SliderPresetDisplayCell());
        availableMorphSliderPreset.setOnAction(event -> assignMorphSliderPresetButton.setDisable(
                context.projectMutationsBlocked() || availableMorphSliderPreset.getValue() == null));
        assignMorphSliderPresetButton.setOnAction(event -> {
            SliderPresetSnapshot preset = availableMorphSliderPreset.getValue();
            if (preset != null)
                dispatch(new MorphsFeature.AssignSliderPreset(NameIdentity.of(preset.getName())));
        });
        assignAllMorphSliderPresetsButton.setOnAction(event ->
                dispatch(new MorphsFeature.AssignAllSliderPresets()));
        removeMorphSliderPresetButton.setOnAction(event ->
                dispatch(new MorphsFeature.RemoveAssignedSliderPreset()));
        clearMorphSliderPresetsButton.setOnAction(event ->
                dispatch(new MorphsFeature.RequestClearAssignments()));
    }

    /** @return the holder FXMLLoader built from this Area's FXML root */
    @Override
    public AreaPanes panes() {
        return panes;
    }

    /**
     * Keeps the shell's context and renders the Morphs feature's current frame against it.
     *
     * @param context the context the shell just built
     */
    @Override
    public void render(WorkbenchContext context) {
        this.context = Objects.requireNonNull(context, "context");
        renderMorphs(feature.frame());
    }

    /**
     * Resolves the Morphs landmarks, which follow whether an NPC Morph Assignment is selected: the selected catalog,
     * the editor summary, and the inspector's first usable control. The Add NPCs launcher is the Area's own
     * {@code NPC_DATABASE_LAUNCHER}, so Back to Morphs can return to it.
     *
     * @param landmark the landmark to resolve
     * @return the node for one of the Area's landmarks, otherwise null
     */
    @Override
    public Node focusNode(WorkbenchNavigation.Landmark landmark) {
        boolean npcSelected = feature.frame().npcSelection().isPresent();
        return switch (landmark) {
            case PRIMARY_CONTENT -> npcSelected ? npcMorphAssignmentList : customMorphTargetList;
            case EDITOR -> morphTargetEditorFocusTarget;
            case INSPECTOR -> npcSelected ? morphTargetSelectionText : availableMorphSliderPreset.isDisabled()
                    ? morphTargetSelectionText : availableMorphSliderPreset;
            case NPC_DATABASE_LAUNCHER -> addNpcsFromDatabaseButton;
            case RAIL, PRIMARY_LAUNCHER, INSPECTOR_LAUNCHER, OUTPUT_LAUNCHER, OUTPUT, ACTIVITY, STATUS -> null;
        };
    }

    /**
     * Ctrl+K reaches the filter of the catalog in use: the NPC filter while an NPC is selected, otherwise the Custom
     * Morph Target filter.
     */
    @Override
    public void focusSearch() {
        // Both filters live in the primary content, which narrow mode shows only as an overlay; open it first.
        shell.revealPrimaryContent();
        TextField filter = feature.frame().npcSelection().isPresent()
                ? npcMorphAssignmentFilter : customMorphTargetFilter;
        filter.requestFocus();
    }

    /**
     * Escape clears the Morphs selection while a Custom Morph Target or NPC Morph Assignment is selected.
     *
     * @return true when a selection was cleared
     */
    @Override
    public boolean escape() {
        if (feature.frame().selection().isEmpty() && feature.frame().npcSelection().isEmpty())
            return false;
        dispatch(new MorphsFeature.ClearSelection());
        return true;
    }

    /** Leaving Morphs needs no cleanup. */
    @Override
    public void leave() {
        // Morphs has no visit-scoped state, and its one satellite, the Fill Empty flyout, is light-dismiss: it hides
        // (cancelling its offer) as soon as the user acts outside it, before any navigation can leave the Area.
    }

    /**
     * Scrolls the inspector to the top when the narrow inspector overlay opens while an NPC is selected.
     */
    @Override
    public void resetInspectorScroll() {
        if (feature.frame().npcSelection().isEmpty())
            return;
        // A previous relationship edit may leave the ScrollPane at the bottom; opening the narrow inspector
        // must reveal the selected NPC portrait and its viewer action before the relationships below it.
        morphsInspectorScroll.setVvalue(0.0);
        Platform.runLater(() -> morphsInspectorScroll.setVvalue(0.0));
    }

    /**
     * Selects the NPC Morph Assignment the NPC Database just promoted, when there is one, and scrolls the selected
     * assignment into view. The shell calls this when it returns from the NPC Database, before navigating back to
     * the Add NPCs launcher.
     *
     * @param assignment the promoted assignment to select, or empty to keep the current selection
     */
    public void revealAssignment(Optional<NpcMorphAssignmentIdentity> assignment) {
        Objects.requireNonNull(assignment, "assignment").ifPresent(identity ->
                dispatch(new MorphsFeature.SelectNpc(identity)));
        NpcMorphAssignmentSnapshot selected = npcMorphAssignmentList.getSelectionModel().getSelectedItem();
        if (selected != null)
            npcMorphAssignmentList.scrollTo(selected);
    }

    /** @return current Custom Morph Target ListView, including an accessibility-driven refill replacement */
    public ListView<CustomMorphTargetSnapshot> customMorphTargetListNode() {
        return customMorphTargetList;
    }

    /** @return current NPC Morph Assignment ListView, including an accessibility-driven refill replacement */
    public ListView<NpcMorphAssignmentSnapshot> npcMorphAssignmentListNode() {
        return npcMorphAssignmentList;
    }

    /** @return current assigned Slider Preset ListView, including an accessibility-driven refill replacement */
    public ListView<SliderPresetSnapshot> assignedMorphSliderPresetListNode() {
        return assignedMorphSliderPresetList;
    }

    /**
     * Runs one Morphs intent through the gated dispatch pipeline. The shell keeps the Project diagnostics of a Morphs
     * edit with Morphs: its chrome step sees the Morphs update and leaves the Templates projection clear.
     *
     * @param intent requested Morphs task
     */
    private void dispatch(MorphsFeature.Intent intent) {
        pipeline.dispatch(feature, DispatchPipeline.Callbacks.of(
                update -> renderMorphs(update.frame()),
                effect -> DispatchPipeline.Realization.confirm(morphsConfirmation(effect),
                        action -> feature.respond(effect.token(), action == morphsDestructiveAction(effect))),
                this::report), Objects.requireNonNull(intent, "intent"));
    }

    /** Returns the named destructive action a Morphs confirmation offers beside Cancel. */
    private static WorkbenchFeedback.DialogAction morphsDestructiveAction(MorphsFeature.Effect effect) {
        return effect.kind() == MorphsFeature.EffectKind.CONFIRM_REMOVE
                || effect.kind() == MorphsFeature.EffectKind.CONFIRM_REMOVE_NPC
                || effect.kind() == MorphsFeature.EffectKind.CONFIRM_REMOVE_ASSIGNMENT
                ? WorkbenchFeedback.DialogAction.REMOVE
                : WorkbenchFeedback.DialogAction.CLEAR;
    }

    /** Builds the safe-default destructive confirmation for one tokenized Morphs effect. */
    private static WorkbenchFeedback.DialogSpec morphsConfirmation(MorphsFeature.Effect effect) {
        return WorkbenchFeedback.DialogSpec.destructiveAction(effect.title(), effect.message(),
                morphsDestructiveAction(effect));
    }

    /**
     * Maps one settled Morphs dispatch to feedback: a confirmed destructive edit is recorded in Activity, a Fill
     * Empty offer opens its flyout, the outcome is routed to its tier, and successful creation clears its inputs.
     * After a confirmation the response, not the request, is reported, so a rejected or failed response stays
     * visible.
     */
    private void report(DispatchPipeline.Outcome<MorphsFeature.Intent, MorphsFeature.Update> outcome) {
        MorphsFeature.Intent intent = outcome.intent();
        MorphsFeature.Update update = outcome.settled();
        if (outcome.answer().isPresent()) {
            MorphsFeature.Effect effect = outcome.update().effect().orElseThrow();
            if (outcome.answer().orElseThrow() == morphsDestructiveAction(effect)) {
                String operation = switch (effect.kind()) {
                    case CONFIRM_REMOVE -> "Remove Custom Morph Target";
                    case CONFIRM_REMOVE_NPC -> "Remove NPC Morph Assignment";
                    case CONFIRM_REMOVE_ASSIGNMENT -> "Remove Slider Preset assignment";
                    case CONFIRM_CLEAR_VISIBLE -> "Clear visible Custom Morph Targets";
                    case CONFIRM_CLEAR_VISIBLE_NPCS -> "Clear visible NPC Morph Assignments";
                    case CONFIRM_CLEAR_ASSIGNMENTS -> "Clear Slider Preset assignments";
                };
                publishMorphsMutation(operation, update, true);
            }
        }
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
    }

    /** Routes Morphs validation and task outcomes into the accepted inline, status, Activity, and failure tiers. */
    private void publishMorphsOutcome(MorphsFeature.Intent intent, MorphsFeature.Update update) {
        if (!update.frame().diagnostics().isEmpty()) {
            String message = ProjectDiagnosticFormatter.format(update.frame().diagnostics());
            WorkbenchFeedback.Severity severity = update.outcomeKind() == MorphsFeature.OutcomeKind.FAILED
                    ? WorkbenchFeedback.Severity.FAILURE
                    : WorkbenchFeedback.Severity.VALIDATION;
            shell.publishFeedback(feedback -> feedback.publishStatus(new WorkbenchFeedback.Notification(
                    "Morphs validation", severity, message, WorkbenchFeedback.Disposition.FAILED)));
            if (intent instanceof MorphsFeature.Create) {
                customMorphTargetNameInput.requestFocus();
            } else if (intent instanceof MorphsFeature.CreateNpc) {
                TextField invalidInput = firstInvalidNpcInput(update.frame().diagnostics());
                invalidInput.requestFocus();
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
            case MorphsFeature.ApplyFillEmpty ignored -> "Fill Empty NPC Morph Assignments";
            default -> null;
        };
        if (operation != null && (update.outcomeKind() == MorphsFeature.OutcomeKind.CHANGED
                || update.outcomeKind() == MorphsFeature.OutcomeKind.UNCHANGED))
            publishMorphsMutation(operation, update, intent instanceof MorphsFeature.AssignAllSliderPresets
                    || intent instanceof MorphsFeature.RequestFillEmpty
                    || intent instanceof MorphsFeature.ApplyFillEmpty);
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
        shell.publishFeedback(feedback -> durable
                ? feedback.publishActivity(notification) : feedback.publishStatus(notification));
    }

    /** Presents an unexpected synchronous Morphs failure without losing the semantic launcher focus. */
    private void showMorphsFailure(MorphsFeature.Update update) {
        WorkbenchNavigation.FocusTarget returnTarget = shell.currentFocus();
        String details = ProjectDiagnosticFormatter.format(update.frame().diagnostics());
        WorkbenchFeedback.DialogSpec spec = WorkbenchFeedback.DialogSpec.failure(
                "Morphs editing failed", "The Morphs edit could not be completed.",
                details.isBlank() ? "No diagnostic details were provided." : details, false);
        pipeline.fail(spec);
        shell.requestFocus(returnTarget);
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
                (ListChangeListener<SliderPresetSnapshot>) change -> refreshPrimary.run());
        refreshPrimary.run();
        apply.setOnAction(event -> {
            List<NameIdentity> selected = choices.getSelectionModel().getSelectedItems().stream()
                    .map(preset -> NameIdentity.of(preset.getName())).toList();
            // Through the gated pipeline: while a job blocks mutations the apply is refused, and hiding the
            // popup then cancels the offer without an edit.
            dispatch(new MorphsFeature.ApplyFillEmpty(offer.token(), selected));
            popup.hide();
        });
        HBox commitActions = new HBox(8.0, apply, cancel);
        content.getChildren().setAll(title, scope, instruction, choices, selectionActions, commitActions);
        popup.getContent().setAll(content);
        popup.getScene().getStylesheets().add(MorphsArea.class
                .getResource("/com/asdasfa/jbs2bg/workbench.css").toExternalForm());
        // Registering applies the current theme now and lets the shell re-theme the flyout and hide it with the window.
        WorkbenchSatellites.Registration registration = satellites.register(content, popup::hide);
        popup.setOnHidden(event -> {
            registration.close();
            feature.cancelFillEmpty(offer.token());
            // Restored synchronously: this sets the main scene's focus owner, which JavaFX realizes whenever the
            // window is active, so no deferral is needed even if another window holds OS focus right now.
            shell.restoreFocusAfterSatellite(fillEmptyNpcMorphAssignmentsButton,
                    WorkbenchNavigation.Landmark.PRIMARY_CONTENT);
        });
        Bounds anchor = fillEmptyNpcMorphAssignmentsButton.localToScreen(
                fillEmptyNpcMorphAssignmentsButton.getBoundsInLocal());
        popup.show(owner, anchor.getMinX(), anchor.getMaxY());
        choices.requestFocus();
    }

    /**
     * Shows the selected NPC Morph Assignment's portrait in the Morphs inspector through the shared viewer's
     * thumbnail loader.
     *
     * @param npc selected immutable NPC Morph Assignment, or null when no NPC is selected
     */
    private void renderNpcPortrait(NpcMorphAssignmentSnapshot npc) {
        if (npc == null)
            portrait.clear();
        else
            portrait.show(new NpcMorphAssignmentIdentity(npc.getPluginName(), npc.getEditorId()),
                    npc.getDisplayName());
    }

    /** Configures the current assigned-relationship ListView, including identity selection dispatch. */
    private void configureAssignedMorphSliderPresetList() {
        assignedMorphSliderPresetList.setCellFactory(list -> new SliderPresetDisplayCell());
        assignedMorphSliderPresetList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (!renderingMorphs)
                        dispatch(selected == null
                                ? new MorphsFeature.ClearAssignedSliderPresetSelection()
                                : new MorphsFeature.SelectAssignedSliderPreset(NameIdentity.of(selected.getName())));
                });
    }

    /** Configures the current target ListView instance; empty/refill UIA recovery may replace that adapter node. */
    private void configureCustomMorphTargetList() {
        CatalogHeight.bind(customMorphTargetList, MORPH_TARGET_CELL_HEIGHT, MAX_VISIBLE_MORPH_TARGET_ROWS,
                renderScale);
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
                        dispatch(selected == null
                                ? new MorphsFeature.ClearSelection()
                                : new MorphsFeature.Select(NameIdentity.of(selected.getName())));
                });
        customMorphTargetList.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!(event.getTarget() instanceof TextInputControl) && event.getCharacter().length() == 1
                    && !event.isControlDown() && !event.isAltDown()) {
                dispatch(new MorphsFeature.TypeAhead(event.getCharacter().charAt(0)));
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
        CatalogHeight.bind(npcMorphAssignmentList, MORPH_TARGET_CELL_HEIGHT, MAX_VISIBLE_MORPH_TARGET_ROWS,
                renderScale);
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
                        dispatch(selected == null
                                ? new MorphsFeature.ClearSelection()
                                : new MorphsFeature.SelectNpc(new NpcMorphAssignmentIdentity(
                                        selected.getPluginName(), selected.getEditorId())));
                });
        npcMorphAssignmentList.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (!(event.getTarget() instanceof TextInputControl) && event.getCharacter().length() == 1
                    && !event.isControlDown() && !event.isAltDown()) {
                dispatch(new MorphsFeature.NpcTypeAhead(event.getCharacter().charAt(0)));
                event.consume();
            }
        });
    }

    /**
     * Renders one immutable Morphs frame while suppressing control listeners from becoming a second command path.
     * Job admission comes only from the shell's context.
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
            customMorphTargetItems.reconcile(frame.visibleTargets());
            npcMorphAssignmentFilter.setText(frame.npcFilterText());
            npcMorphAssignmentSort.setValue(frame.npcSortOrder());
            npcMorphAssignmentItems.reconcile(frame.visibleNpcs());
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
                SeverityStyle.apply(morphsInfoBar, severity);
            }

            boolean selected = frame.editor().isPresent();
            boolean npcSelected = frame.npcEditor().isPresent();
            createCustomMorphTargetButton.setDisable(context.projectMutationsBlocked());
            removeCustomMorphTargetButton.setDisable(context.projectMutationsBlocked() || !selected);
            clearCustomMorphTargetsButton.setDisable(context.projectMutationsBlocked()
                    || frame.visibleTargets().isEmpty());
            createNpcMorphAssignmentButton.setDisable(context.projectMutationsBlocked());
            removeNpcMorphAssignmentButton.setDisable(context.projectMutationsBlocked() || !npcSelected);
            clearNpcMorphAssignmentsButton.setDisable(context.projectMutationsBlocked()
                    || frame.visibleNpcs().isEmpty());
            fillEmptyNpcMorphAssignmentsButton.setDisable(context.projectMutationsBlocked());
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
            assignedMorphSliderPresetItems.reconcile(assignedPresets);
            assignedMorphSliderPresetList.getSelectionModel().clearSelection();
            assignedPresetSelection.ifPresent(identity -> assignedMorphSliderPresetList.getItems().stream()
                    .filter(preset -> NameIdentity.of(preset.getName()).equals(identity))
                    .findFirst().ifPresent(assignedMorphSliderPresetList.getSelectionModel()::select));
            SliderPresetSnapshot availableSelection = availableMorphSliderPreset.getValue();
            UiaListHelper.setAllIfChanged(availableMorphSliderPreset.getItems(), availablePresets);
            if (availableSelection == null || availablePresets.stream()
                    .noneMatch(preset -> preset.getName().equalsIgnoreCase(availableSelection.getName())))
                availableMorphSliderPreset.setValue(null);
            boolean assignedSelected = assignedPresetSelection.isPresent();
            boolean hasAssignments = !assignedPresets.isEmpty();
            boolean hasAvailable = !availablePresets.isEmpty();
            availableMorphSliderPreset.setDisable(context.projectMutationsBlocked() || !hasAvailable);
            assignMorphSliderPresetButton.setDisable(context.projectMutationsBlocked() || !hasAvailable
                    || availableMorphSliderPreset.getValue() == null);
            assignAllMorphSliderPresetsButton.setDisable(context.projectMutationsBlocked() || !hasAvailable);
            removeMorphSliderPresetButton.setDisable(context.projectMutationsBlocked() || !assignedSelected);
            clearMorphSliderPresetsButton.setDisable(context.projectMutationsBlocked() || !hasAssignments);
            renderedMorphTargetSelection = frame.selection();
            renderedNpcSelection = frame.npcSelection();
        } finally {
            renderingMorphs = false;
        }
    }

    /**
     * Builds the assigned-relationship ListView that replaces an empty one on refill (see {@link UiaListHelper}).
     */
    private ListView<SliderPresetSnapshot> newAssignedMorphSliderPresetList() {
        ListView<SliderPresetSnapshot> replacement = new ListView<>();
        replacement.setId("assignedMorphSliderPresetList");
        replacement.setAccessibleText("Assigned Slider Presets");
        replacement.setFixedCellSize(MORPH_TARGET_CELL_HEIGHT);
        replacement.setFocusTraversable(true);
        replacement.setPrefHeight(120.0);
        return replacement;
    }

    /** Adopts the swapped-in assigned-relationship node, re-pointing its label so UIA LabeledBy follows it. */
    private void adoptAssignedMorphSliderPresetList(ListView<SliderPresetSnapshot> replacement) {
        assignedMorphSliderPresetList = replacement;
        assignedMorphSliderPresetLabel.setLabelFor(replacement);
        configureAssignedMorphSliderPresetList();
    }

    /** Builds the Custom Morph Target ListView that replaces an empty one on refill (see {@link UiaListHelper}). */
    private ListView<CustomMorphTargetSnapshot> newCustomMorphTargetList() {
        ListView<CustomMorphTargetSnapshot> replacement = new ListView<>();
        replacement.setId("customMorphTargetList");
        replacement.setAccessibleText("Custom Morph Targets");
        replacement.setFixedCellSize(MORPH_TARGET_CELL_HEIGHT);
        replacement.setFocusTraversable(true);
        VBox.setVgrow(replacement, Priority.ALWAYS);
        return replacement;
    }

    /** Adopts the swapped-in Custom Morph Target node as the catalog control. */
    private void adoptCustomMorphTargetList(ListView<CustomMorphTargetSnapshot> replacement) {
        customMorphTargetList = replacement;
        configureCustomMorphTargetList();
    }

    /** Builds the NPC Morph Assignment ListView that replaces an empty one on refill (see {@link UiaListHelper}). */
    private ListView<NpcMorphAssignmentSnapshot> newNpcMorphAssignmentList() {
        ListView<NpcMorphAssignmentSnapshot> replacement = new ListView<>();
        replacement.setId("npcMorphAssignmentList");
        replacement.setAccessibleText("NPC Morph Assignments");
        replacement.setFixedCellSize(MORPH_TARGET_CELL_HEIGHT);
        replacement.setFocusTraversable(true);
        VBox.setVgrow(replacement, Priority.ALWAYS);
        return replacement;
    }

    /** Adopts the swapped-in NPC Morph Assignment node as the catalog control. */
    private void adoptNpcMorphAssignmentList(ListView<NpcMorphAssignmentSnapshot> replacement) {
        npcMorphAssignmentList = replacement;
        configureNpcMorphAssignmentList();
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
}
