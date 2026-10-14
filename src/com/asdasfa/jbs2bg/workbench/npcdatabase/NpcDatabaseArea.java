package com.asdasfa.jbs2bg.workbench.npcdatabase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.FilterColumn;
import com.asdasfa.jbs2bg.filtering.NpcTableColumns;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.SortDirection;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.AreaPanes;
import com.asdasfa.jbs2bg.workbench.DispatchPipeline;
import com.asdasfa.jbs2bg.workbench.NpcPortraitViewer;
import com.asdasfa.jbs2bg.workbench.SeverityStyle;
import com.asdasfa.jbs2bg.workbench.ShellPort;
import com.asdasfa.jbs2bg.workbench.UiaListHelper;
import com.asdasfa.jbs2bg.workbench.WorkbenchArea;
import com.asdasfa.jbs2bg.workbench.WorkbenchContext;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation;
import com.asdasfa.jbs2bg.workbench.WorkbenchPlatform;
import com.asdasfa.jbs2bg.workbench.WorkbenchSatellites;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;

/**
 * The NPC Database Area: source management, the filterable NPC catalog with its column-filter popup, and the
 * selection-following NPC inspector with its portrait thumbnail. It translates its controls into
 * {@link NpcDatabaseFeature} intents through the shared {@link DispatchPipeline} on the any-job gate, renders only
 * committed immutable frames, runs its own source imports on the central job path, and words its own feedback. The
 * return to Morphs, Project re-rendering after a promotion, and semantic focus go through the {@link ShellPort}.
 * <p>
 * Loaded from {@code npc-database-area.fxml} by {@link #load}; every method runs on the JavaFX Application Thread.
 */
public final class NpcDatabaseArea implements WorkbenchArea {
    private final NpcDatabaseFeature feature;
    private final NpcDatabaseImporter importer;
    private final DispatchPipeline pipeline;
    private final WorkbenchPlatform platform;
    private final ShellPort shell;
    private final NpcPortraitViewer portraits;
    private final WorkbenchSatellites satellites;
    private final Stage owner;
    @FXML
    private AreaPanes panes;
    @FXML
    private ScrollPane npcDatabaseInspectorScroll;
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
    private CheckBox assignRandomNpcPresetCheck;
    @FXML
    private ImageView npcDatabasePortraitImage;
    @FXML
    private Label npcDatabasePortraitStatus;
    @FXML
    private Button openNpcDatabasePortraitViewerButton;
    @FXML
    private Label npcDatabaseDiagnostics;
    // The latest context the shell rendered; this Area's own update renders read cross-Area inputs only from here.
    private WorkbenchContext context;
    private boolean renderingNpcDatabase;
    // The shared portrait viewer's thumbnail loader for this inspector; bound once the controls are injected.
    private NpcPortraitViewer.Thumbnail portrait;
    private Popup npcFilterPopup;
    // The open filter popup's registration with the shell's appearance; closed when the popup hides.
    private WorkbenchSatellites.Registration npcFilterRegistration;
    private String npcFilterExpandedColumn;
    private final Map<String, ToggleButton> npcFilterButtons = new LinkedHashMap<>();
    private final Map<String, List<SortKey>> npcSortChoices = new LinkedHashMap<>();

    /**
     * Creates the controller FXMLLoader injects; use {@link #load} rather than calling this directly.
     *
     * @param feature    the window-scoped NPC Database catalog, built by the shell's composition root
     * @param jobs       the central job coordinator source imports run on
     * @param pipeline   the shared dispatch pipeline that gates mutations and runs the dialog round-trip
     * @param platform   the native-effect adapter that shows the source chooser
     * @param shell      the shell port for feedback, focus, the return to Morphs and Project re-rendering
     * @param portraits  the shared NPC portrait viewer this Area's inspector thumbnail opens
     * @param satellites the shell's appearance, which re-themes and dismisses the column-filter popup
     * @param owner      the Workbench window that owns the source chooser and the column-filter popup
     * @throws NullPointerException when an argument is null
     */
    public NpcDatabaseArea(NpcDatabaseFeature feature, JobCoordinator jobs, DispatchPipeline pipeline,
                           WorkbenchPlatform platform, ShellPort shell, NpcPortraitViewer portraits,
                           WorkbenchSatellites satellites, Stage owner) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.shell = Objects.requireNonNull(shell, "shell");
        this.portraits = Objects.requireNonNull(portraits, "portraits");
        this.satellites = Objects.requireNonNull(satellites, "satellites");
        this.owner = Objects.requireNonNull(owner, "owner");
        // Import completions publish on the job coordinator's publication lane, after FXMLLoader injected the
        // controls, so the importer can render this Area's catalog directly.
        this.importer = new NpcDatabaseImporter(feature, Objects.requireNonNull(jobs, "jobs"), this::render);
    }

    /**
     * Loads {@code npc-database-area.fxml} with a controller constructor-injected with these collaborators. The Area
     * renders nothing until the shell's first {@link #render(WorkbenchContext)}.
     *
     * @param feature    the NPC Database catalog
     * @param jobs       the central job coordinator
     * @param pipeline   the shared dispatch pipeline
     * @param platform   the native-effect adapter
     * @param shell      the shell port
     * @param portraits  the shared NPC portrait viewer
     * @param satellites the shell's appearance for satellites
     * @param owner      the Workbench window
     * @return the loaded Area and its FXML namespace
     */
    public static AreaLoader.Loaded<NpcDatabaseArea> load(NpcDatabaseFeature feature, JobCoordinator jobs,
                                                          DispatchPipeline pipeline, WorkbenchPlatform platform,
                                                          ShellPort shell, NpcPortraitViewer portraits,
                                                          WorkbenchSatellites satellites, Stage owner) {
        return AreaLoader.load(NpcDatabaseArea.class, "npc-database-area.fxml",
                () -> new NpcDatabaseArea(feature, jobs, pipeline, platform, shell, portraits, satellites, owner));
    }

    /**
     * Connects source management, column filtering, catalog sorting, and selection to one window-scoped catalog.
     * FXMLLoader calls this once every control is injected.
     */
    @FXML
    private void initialize() {
        // The shared viewer wires openNpcDatabasePortraitViewerButton to open itself.
        portrait = portraits.thumbnail(npcDatabasePortraitImage, npcDatabasePortraitStatus,
                openNpcDatabasePortraitViewerButton, "Select an NPC Database entry to inspect its portrait.");
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
            if (!renderingNpcDatabase)
                dispatch(new NpcDatabaseFeature.SelectSource(
                        selected == null ? Optional.empty() : Optional.of(selected.path())));
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
            dispatch(new NpcDatabaseFeature.ChangeSort(keys));
            return true;
        });
        npcCatalogTable.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (!renderingNpcDatabase)
                dispatch(selected == null ? new NpcDatabaseFeature.ClearSelection()
                        : new NpcDatabaseFeature.SelectRow(ProjectIdentities.npcDatabaseEntry(selected)));
        });
        npcCatalogTable.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (event.getCharacter().length() == 1 && !event.isControlDown() && !event.isAltDown()
                    && !Character.isISOControl(event.getCharacter().charAt(0))
                    && !Character.isWhitespace(event.getCharacter().charAt(0))) {
                dispatch(new NpcDatabaseFeature.TypeAhead(event.getCharacter().charAt(0)));
                event.consume();
            }
        });
        npcCatalogTable.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                dispatch(new NpcDatabaseFeature.AddSelected());
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
            if (!renderingNpcDatabase && npcSortChoices.containsKey(npcSortChoice.getValue()))
                dispatch(new NpcDatabaseFeature.ChangeSort(npcSortChoices.get(npcSortChoice.getValue())));
        });
        npcFilterButtons.put("Name", npcNameFilterButton);
        npcFilterButtons.put("Master", npcMasterFilterButton);
        npcFilterButtons.put("Race", npcRaceFilterButton);
        npcFilterButtons.put("EditorID", npcEditorIdFilterButton);
        npcFilterButtons.put("FormID", npcFormIdFilterButton);
        npcFilterButtons.forEach((column, button) ->
                button.setOnAction(event -> showNpcColumnFilter(column, button)));
        clearNpcFiltersButton.setOnAction(event -> dispatch(new NpcDatabaseFeature.ClearAllCriteria()));
        importNpcSourcesButton.setOnAction(event -> chooseNpcSources());
        removeNpcSourceButton.setOnAction(event -> dispatch(new NpcDatabaseFeature.RequestRemoveSource()));
        clearNpcDatabaseButton.setOnAction(event -> dispatch(new NpcDatabaseFeature.RequestClearVisible()));
        addNpcToProjectButton.setOnAction(event -> dispatch(new NpcDatabaseFeature.AddSelected()));
        addAllNpcsToProjectButton.setOnAction(event -> dispatch(new NpcDatabaseFeature.AddAllVisible()));
        backToMorphsButton.setOnAction(event -> dispatch(new NpcDatabaseFeature.BackToMorphs()));
        dismissNpcPromotionInfoBarButton.setOnAction(event ->
                dispatch(new NpcDatabaseFeature.DismissPromotionReport()));
        assignRandomNpcPresetCheck.setOnAction(event -> {
            if (!renderingNpcDatabase)
                dispatch(new NpcDatabaseFeature.SetAssignRandom(assignRandomNpcPresetCheck.isSelected()));
        });
    }

    /** @return the holder FXMLLoader built from this Area's FXML root */
    @Override
    public AreaPanes panes() {
        return panes;
    }

    /**
     * Keeps the shell's context and renders the catalog's current frame against it.
     *
     * @param context the context the shell just built
     */
    @Override
    public void render(WorkbenchContext context) {
        this.context = Objects.requireNonNull(context, "context");
        render(feature.frame());
    }

    /**
     * Resolves the NPC Database landmarks: the source list, the catalog table, and the selected-entry label.
     *
     * @param landmark the landmark to resolve
     * @return the node for one of the three Area landmarks, otherwise null
     */
    @Override
    public Node focusNode(WorkbenchNavigation.Landmark landmark) {
        return switch (landmark) {
            case PRIMARY_CONTENT -> npcSourceList;
            case EDITOR -> npcCatalogTable;
            case INSPECTOR -> npcInspectorName;
            case RAIL, PRIMARY_LAUNCHER, NPC_DATABASE_LAUNCHER, INSPECTOR_LAUNCHER, OUTPUT_LAUNCHER, OUTPUT, ACTIVITY,
                 STATUS -> null;
        };
    }

    /**
     * The NPC Database's first editable control is the catalog, not the source list its primary content starts with.
     *
     * @return the catalog table
     */
    @Override
    public Node defaultFocusNode() {
        return npcCatalogTable;
    }

    /** Ctrl+K reaches the first named column-filter button. */
    @Override
    public void focusSearch() {
        npcNameFilterButton.requestFocus();
    }

    /**
     * Escape clears the catalog selection while a row is selected.
     *
     * @return true when a selection was cleared
     */
    @Override
    public boolean escape() {
        if (feature.frame().selectedRow().isEmpty())
            return false;
        dispatch(new NpcDatabaseFeature.ClearSelection());
        return true;
    }

    /** Leaving the NPC Database ends the visit and hides the column-filter popup. */
    @Override
    public void leave() {
        // Promotion feedback and return selection are visit-scoped; rail navigation must not revive them later.
        dispatch(new NpcDatabaseFeature.LeaveArea());
        if (npcFilterPopup != null)
            npcFilterPopup.hide();
    }

    /** Scrolls the inspector to the top when the narrow inspector overlay opens. */
    @Override
    public void resetInspectorScroll() {
        npcDatabaseInspectorScroll.setVvalue(0.0);
        // The overlay lays out after this call and can restore the old position; reset once more after layout.
        Platform.runLater(() -> npcDatabaseInspectorScroll.setVvalue(0.0));
    }

    /**
     * Runs one NPC Database intent through the gated dispatch pipeline: promotion and catalog removal are refused
     * while any job runs, not only a Project-exclusive one, and every accepted intent renders its committed catalog
     * frame. The shell also dispatches its Project-lifecycle selection reset through here.
     *
     * @param intent requested NPC Database task
     */
    public void dispatch(NpcDatabaseFeature.Intent intent) {
        pipeline.dispatch(feature, DispatchPipeline.Gate.ANY_JOB, DispatchPipeline.Callbacks.of(
                this::renderUpdate, this::realize, this::report), Objects.requireNonNull(intent, "intent"));
    }

    /**
     * Renders one NPC Database update. A promotion first asks the shell to reconcile the Project chrome and the other
     * Areas with the snapshot it published, as the update carries no Project sequence for the pipeline's generic
     * chrome step: that step would project the Project frame's diagnostics, which the NPC Database reports itself.
     */
    private void renderUpdate(NpcDatabaseFeature.Update update) {
        // The bulk report presents every row diagnostic locally; projecting the Project frame here would
        // expose either a final row's diagnostic or the same aggregate on an unrelated global surface.
        update.promotion().ifPresent(report -> shell.projectChanged(update, report.bulk()));
        render(update.frame());
    }

    /**
     * Realizes one NPC Database effect: source-management confirmations run as the pipeline's dialog round-trip,
     * and the return to Morphs goes to the shell.
     */
    private DispatchPipeline.Realization<NpcDatabaseFeature.Update> realize(NpcDatabaseFeature.Effect effect) {
        return switch (Objects.requireNonNull(effect, "effect")) {
            case NpcDatabaseFeature.ConfirmRemoveSource remove -> confirm(remove.token(),
                    WorkbenchFeedback.DialogSpec.destructiveAction("Remove NPC Database source?",
                            "Remove " + remove.source().getFileName() + " from this session's catalog?",
                            WorkbenchFeedback.DialogAction.REMOVE), WorkbenchFeedback.DialogAction.REMOVE);
            case NpcDatabaseFeature.ConfirmClearVisible clear -> confirm(clear.token(),
                    WorkbenchFeedback.DialogSpec.destructiveAction("Clear visible NPC Database entries?",
                            "Clear " + clear.identities().size() + " visible entries from this session's catalog?",
                            WorkbenchFeedback.DialogAction.CLEAR), WorkbenchFeedback.DialogAction.CLEAR);
            case NpcDatabaseFeature.ReturnToMorphs returning -> {
                shell.returnToMorphs(returning.assignment());
                yield DispatchPipeline.Realization.done();
            }
        };
    }

    /**
     * Hands one source-management confirmation to the pipeline's dialog round-trip while retaining its launcher
     * focus. The removal or clear acts on the operands the feature captured before the dialog: the selected source,
     * or exactly the identities visible when the clear was requested.
     */
    private DispatchPipeline.Realization<NpcDatabaseFeature.Update> confirm(
            long token, WorkbenchFeedback.DialogSpec spec, WorkbenchFeedback.DialogAction acceptance) {
        WorkbenchNavigation.FocusTarget returnTarget = shell.currentFocus();
        return DispatchPipeline.Realization.confirm(spec, action -> {
            shell.requestFocus(returnTarget);
            return feature.respond(token, action == acceptance);
        });
    }

    /**
     * Maps one settled NPC Database dispatch to feedback: a promotion is reported at its UI tier, a confirmed
     * removal or clear is recorded in Activity, and type-ahead scrolls the selected row into view.
     */
    private void report(DispatchPipeline.Outcome<NpcDatabaseFeature.Intent, NpcDatabaseFeature.Update> outcome) {
        NpcDatabaseFeature.Update update = outcome.settled();
        switch (outcome.intent()) {
            case NpcDatabaseFeature.AddSelected ignored ->
                    publishPromotion(outcome.update().promotion().orElseThrow());
            case NpcDatabaseFeature.AddAllVisible ignored ->
                    publishPromotion(outcome.update().promotion().orElseThrow());
            case NpcDatabaseFeature.RequestRemoveSource ignored -> {
                if (outcome.answer().filter(WorkbenchFeedback.DialogAction.REMOVE::equals).isPresent()) {
                    Path source = ((NpcDatabaseFeature.ConfirmRemoveSource) outcome.update().effect().orElseThrow())
                            .source();
                    shell.publishFeedback(feedback -> feedback.publishActivity(new WorkbenchFeedback.Notification(
                            "Remove NPC Database Source", WorkbenchFeedback.Severity.SUCCESS,
                            "Removed source " + source.getFileName() + ".",
                            WorkbenchFeedback.Disposition.COMPLETED)));
                }
            }
            case NpcDatabaseFeature.RequestClearVisible ignored -> {
                if (outcome.answer().filter(WorkbenchFeedback.DialogAction.CLEAR::equals).isPresent()) {
                    int cleared = ((NpcDatabaseFeature.ConfirmClearVisible) outcome.update().effect()
                            .orElseThrow()).identities().size();
                    shell.publishFeedback(feedback -> feedback.publishActivity(new WorkbenchFeedback.Notification(
                            "Clear visible NPC Database entries", WorkbenchFeedback.Severity.SUCCESS,
                            "Cleared " + cleared + " visible entries.", WorkbenchFeedback.Disposition.COMPLETED)));
                }
            }
            case NpcDatabaseFeature.TypeAhead ignored -> update.frame().selectedRow().ifPresent(row ->
                    npcCatalogTable.scrollTo(update.frame().visibleRows().indexOf(row)));
            default -> {
                // Selection, filtering, sorting, visit state and the return to Morphs report through their render.
            }
        }
    }

    /** Reports one completed Add or Add All at its UI tier: Add on the status line, Add All in Activity. */
    private void publishPromotion(NpcDatabaseFeature.PromotionReport report) {
        boolean bulk = report.bulk();
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
            shell.publishFeedback(feedback -> details.isEmpty() ? feedback.publishActivity(notification)
                    : feedback.publishActivityDetailed(notification, details));
        } else {
            shell.publishFeedback(feedback -> feedback.publishStatus(notification));
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
        ColumnCriterion criterion = feature.frame().criteria().stream()
                .filter(candidate -> candidate.getColumnId().equals(columnId)).findFirst().orElse(null);
        Set<String> values = new TreeSet<>();
        for (NPC row : feature.frame().rows())
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
        popup.getScene().getStylesheets().add(NpcDatabaseArea.class
                .getResource("/com/asdasfa/jbs2bg/workbench.css").toExternalForm());
        apply.setOnAction(event -> {
            List<String> hidden = choices.stream().filter(choice -> !choice.isSelected())
                    .map(NpcFilterChoice::value).toList();
            dispatch(new NpcDatabaseFeature.SetCriterion(ColumnCriterion.hiding(columnId, hidden)));
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
        npcFilterExpandedColumn = columnId;
        // Registering applies the current theme now and lets the shell re-theme the popup and hide it with the window.
        npcFilterRegistration = satellites.register(content, popup::hide);
        renderNpcFilterButtons(feature.frame());
        popup.setOnHidden(event -> {
            npcFilterRegistration.close();
            npcFilterPopup = null;
            npcFilterRegistration = null;
            npcFilterExpandedColumn = null;
            renderNpcFilterButtons(feature.frame());
            shell.restoreFocusAfterSatellite(launcher, WorkbenchNavigation.Landmark.EDITOR);
        });
        Bounds anchor = launcher.localToScreen(launcher.getBoundsInLocal());
        popup.show(owner, anchor.getMinX(), anchor.getMaxY());
        checklist.getSelectionModel().selectFirst();
        checklist.requestFocus();
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

    /**
     * Renders the immutable catalog frame while suppressing selection echoes from JavaFX list replacements. This is
     * the only writer of the source-management buttons; whether a job blocks them comes from the shell's context.
     */
    private void render(NpcDatabaseFeature.Frame frame) {
        if (context == null) {
            // Installing the sort policy in initialize sorts once and dispatches that (empty) sort before the shell
            // has a context to hand over; the shell renders the Area as soon as it mounts it.
            return;
        }
        renderingNpcDatabase = true;
        try {
            UiaListHelper.setAllIfChanged(npcSourceList.getItems(), frame.sources());
            NpcDatabaseFeature.Source selectedSource = npcSourceList.getSelectionModel().getSelectedItem();
            Optional<Path> currentSource = selectedSource == null ? Optional.empty()
                    : Optional.of(selectedSource.path());
            if (!currentSource.equals(frame.selectedSource())) {
                npcSourceList.getSelectionModel().clearSelection();
                frame.selectedSource().ifPresent(path -> frame.sources().stream()
                        .filter(source -> source.path().equals(path)).findFirst()
                        .ifPresent(npcSourceList.getSelectionModel()::select));
            }
            UiaListHelper.setAllIfChanged(npcCatalogTable.getItems(), frame.visibleRows());
            List<TableColumn<NPC, ?>> sortedColumns = new ArrayList<>();
            for (SortKey key : frame.sortOrder()) {
                TableColumn<NPC, ?> column = npcCatalogTable.getColumns().stream()
                        .filter(candidate -> candidate.getText().equals(key.getColumnId()))
                        .findFirst().orElseThrow();
                column.setSortType(key.getDirection() == SortDirection.DESCENDING
                        ? TableColumn.SortType.DESCENDING : TableColumn.SortType.ASCENDING);
                sortedColumns.add(column);
            }
            UiaListHelper.setAllIfChanged(npcCatalogTable.getSortOrder(), sortedColumns);
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
            renderLabel(npcSourceCountText, frame.sources().size() + (frame.sources().size() == 1
                    ? " source loaded" : " sources loaded"));
            String summary = frame.visibleRows().size() + " of " + frame.rows().size()
                    + " NPC Database entries visible; " + frame.criteria().size() + " filtered columns.";
            renderLabel(npcCatalogSummary, summary);
            npcCatalogTable.setAccessibleHelp(summary + " Select a row to inspect its details.");
            boolean blocked = context.anyJobActive();
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
            renderLabel(npcInspectorName, selected == null ? "No NPC selected" : selected.getName());
            renderLabel(npcInspectorSource, "Source: " + (selected == null ? "none"
                    : frame.sourceOf(selected).map(Path::toString).orElse("unknown")));
            renderLabel(npcInspectorMaster, "Plugin: " + (selected == null ? "none" : selected.getMod()));
            renderLabel(npcInspectorEditorId,
                    "Editor ID: " + (selected == null ? "none" : selected.getEditorId()));
            renderLabel(npcInspectorRace, "Race: " + (selected == null ? "none" : selected.getRace()));
            renderLabel(npcInspectorFormId,
                    "Form ID: " + (selected == null ? "none" : selected.getFormId()));
            renderPortrait(selected);
            renderLabel(npcDatabaseDiagnostics, frame.diagnostics().isEmpty() ? "No source diagnostics"
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
                SeverityStyle.apply(npcPromotionInfoBar, severity);
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

    /** Follows stable NPC Database selection through the shared viewer's thumbnail loader. */
    private void renderPortrait(NPC npc) {
        if (npc == null)
            portrait.clear();
        else
            portrait.show(ProjectIdentities.npcDatabaseEntry(npc), npc.getName());
    }

    /** Exposes a dynamic label value through UI Automation HelpText while its stable accessible name stays locatable. */
    private static void renderLabel(Label label, String value) {
        label.setText(value);
        label.setAccessibleHelp(value);
    }

    /** Captures chooser paths once and admits their source-level transaction batch to the central coordinator. */
    private void chooseNpcSources() {
        Optional<List<Path>> selected = platform.chooseNpcSources(owner);
        if (selected.isEmpty() || selected.orElseThrow().isEmpty()) {
            shell.publishFeedback(feedback -> feedback.publishActivity(new WorkbenchFeedback.Notification(
                    "Import NPC Database Sources", WorkbenchFeedback.Severity.INFORMATION,
                    "NPC Database import cancelled before reading sources.", WorkbenchFeedback.Disposition.CANCELLED)));
            return;
        }
        List<Path> sources = selected.orElseThrow().stream().map(path -> path.toAbsolutePath().normalize()).toList();
        JobCoordinator.Admission admission = importer.submit(sources);
        if (!admission.admitted())
            shell.publishFeedback(feedback -> feedback.publishStatus(new WorkbenchFeedback.Notification(
                    "Import NPC Database Sources", WorkbenchFeedback.Severity.VALIDATION,
                    "Another operation is active; retry the import when it finishes.",
                    WorkbenchFeedback.Disposition.FAILED)));
    }
}
