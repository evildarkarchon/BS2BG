package com.asdasfa.jbs2bg.workbench.settings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.AreaPanes;
import com.asdasfa.jbs2bg.workbench.DispatchPipeline;
import com.asdasfa.jbs2bg.workbench.UiaListHelper;
import com.asdasfa.jbs2bg.workbench.WorkbenchArea;
import com.asdasfa.jbs2bg.workbench.WorkbenchContext;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

/**
 * The Settings Area: the Settings profile and entry list, the entry editor, and the Settings pair inspector. It
 * translates its controls into {@link SettingsFeature} intents through the shared {@link DispatchPipeline}, renders
 * only committed immutable frames, and words its own feedback. Worker effects and every cross-Area consequence go
 * through the {@link SettingsShellPort}; the shell keeps the Settings job runner and the dirty-Settings close lifecycle.
 * <p>
 * Loaded from {@code settings-area.fxml} by {@link #load}; every method runs on the JavaFX Application Thread.
 */
public final class SettingsArea implements WorkbenchArea {
    private final SettingsFeature feature;
    private final DispatchPipeline pipeline;
    private final SettingsShellPort shell;
    @FXML
    private AreaPanes panes;
    @FXML
    private ComboBox<SettingsFeature.Profile> settingsProfileChoice;
    @FXML
    private ListView<SettingsFeature.EntryFrame> settingsEntryList;
    @FXML
    private TextField newSettingsEntryName;
    @FXML
    private Button addSettingsEntryButton;
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
    private CheckBox settingsInvertedCheck;
    @FXML
    private Button applySettingsEntryButton;
    @FXML
    private Button removeSettingsEntryButton;
    @FXML
    private Label settingsNoticeText;
    @FXML
    private CheckBox omitRedundantSlidersCheck;
    @FXML
    private Button saveSettingsButton;
    @FXML
    private Button reloadSettingsButton;
    // The latest context the shell rendered; this Area's own update renders read cross-Area inputs only from here.
    private WorkbenchContext context;
    private boolean renderingSettings;

    /**
     * Creates the controller FXMLLoader injects; use {@link #load} rather than calling this directly.
     *
     * @param feature  the Settings feature, built by the shell's composition root
     * @param pipeline the shared dispatch pipeline that gates mutations and runs the dialog round-trip
     * @param shell    the shell port for worker effects, feedback and cross-Area refreshes
     * @throws NullPointerException when an argument is null
     */
    public SettingsArea(SettingsFeature feature, DispatchPipeline pipeline, SettingsShellPort shell) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.shell = Objects.requireNonNull(shell, "shell");
    }

    /**
     * Loads {@code settings-area.fxml} with a controller constructor-injected with these collaborators. The Area
     * renders nothing until the shell's first {@link #render(WorkbenchContext)}.
     *
     * @param feature  the Settings feature
     * @param pipeline the shared dispatch pipeline
     * @param shell    the shell port
     * @return the loaded Area and its FXML namespace
     */
    public static AreaLoader.Loaded<SettingsArea> load(SettingsFeature feature, DispatchPipeline pipeline,
                                                       SettingsShellPort shell) {
        return AreaLoader.load(SettingsArea.class, "settings-area.fxml",
                () -> new SettingsArea(feature, pipeline, shell));
    }

    /**
     * Translates Settings controls into task-oriented feature intents and renders only committed immutable frames.
     * FXMLLoader calls this once every control is injected.
     */
    @FXML
    private void initialize() {
        settingsProfileChoice.getItems().setAll(SettingsFeature.Profile.values());
        settingsEntryList.setCellFactory(list -> new SettingsEntryCell());
        settingsProfileChoice.setOnAction(event -> {
            if (!renderingSettings && settingsProfileChoice.getValue() != null)
                dispatch(new SettingsFeature.SelectProfile(settingsProfileChoice.getValue()));
        });
        settingsEntryList.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> {
                    if (!renderingSettings && selected != null)
                        dispatch(new SettingsFeature.SelectEntry(selected.name()));
                });
        addSettingsEntryButton.setOnAction(event ->
                dispatch(new SettingsFeature.AddEntry(newSettingsEntryName.getText())));
        applySettingsEntryButton.setOnAction(event -> feature.frame().editor().ifPresent(editor ->
                dispatch(new SettingsFeature.EditEntry(editor.originalName(), settingsEntryNameInput.getText(),
                        Optional.of(settingsSmallInput.getText()), Optional.of(settingsBigInput.getText()),
                        Optional.of(settingsMultiplierInput.getText()), settingsInvertedCheck.isSelected()))));
        removeSettingsEntryButton.setOnAction(event -> feature.frame().selection().ifPresent(name ->
                dispatch(new SettingsFeature.RemoveEntry(name))));
        omitRedundantSlidersCheck.setOnAction(event -> dispatch(
                new SettingsFeature.ChangeOmitRedundantSliders(omitRedundantSlidersCheck.isSelected())));
        saveSettingsButton.setOnAction(event -> dispatch(new SettingsFeature.Save()));
        reloadSettingsButton.setOnAction(event -> dispatch(new SettingsFeature.Reload()));
    }

    /** @return the holder FXMLLoader built from this Area's FXML root */
    @Override
    public AreaPanes panes() {
        return panes;
    }

    /**
     * Keeps the shell's context and renders the feature's current frame against it.
     *
     * @param context the context the shell just built
     */
    @Override
    public void render(WorkbenchContext context) {
        this.context = Objects.requireNonNull(context, "context");
        render(feature.frame());
    }

    /**
     * Resolves the Settings landmarks: the entry list, the entry name editor, and Save while the draft can be saved
     * (the persistence diagnostics otherwise).
     *
     * @param landmark the landmark to resolve
     * @return the node for one of the three Area landmarks, otherwise null
     */
    @Override
    public Node focusNode(WorkbenchNavigation.Landmark landmark) {
        return switch (landmark) {
            case PRIMARY_CONTENT -> settingsEntryList;
            case EDITOR -> settingsEntryNameInput;
            case INSPECTOR -> saveSettingsButton.isDisabled() ? settingsNoticeText : saveSettingsButton;
            case RAIL, PRIMARY_LAUNCHER, NPC_DATABASE_LAUNCHER, INSPECTOR_LAUNCHER, OUTPUT_LAUNCHER, OUTPUT, ACTIVITY,
                 STATUS -> null;
        };
    }

    /** Settings has no search or filter field, so Ctrl+K moves nothing. */
    @Override
    public void focusSearch() {
        // Intentionally empty: the Settings Area has no field for Ctrl+K to focus.
    }

    /**
     * Settings has no Area-level Escape action.
     *
     * @return false, always
     */
    @Override
    public boolean escape() {
        return false;
    }

    /** Settings keeps no popups or visit-scoped state, so leaving it changes nothing. */
    @Override
    public void leave() {
        // Intentionally empty: the Settings draft deliberately survives navigation until it is saved or discarded.
    }

    /** The Settings inspector is not scrollable, so the narrow overlay has nothing to reset. */
    @Override
    public void resetInspectorScroll() {
        // Intentionally empty: the Settings inspector is a plain VBox with no scroll position.
    }

    /**
     * Commits one Settings intent through the gated pipeline and admits any captured persistence effect to the shell's
     * job runner. The shell's close lifecycle also dispatches its close-time Save through here.
     *
     * @param intent requested Settings task
     * @return settled feature update, or a rejected update when the job gate refused the intent or coordinator
     * admission failed
     */
    public SettingsFeature.Update dispatch(SettingsFeature.Intent intent) {
        return pipeline.dispatch(feature, DispatchPipeline.Callbacks.of(
                        update -> render(update.frame()),
                        this::realize,
                        this::report), Objects.requireNonNull(intent, "intent"))
                .map(DispatchPipeline.Outcome::settled)
                .orElseGet(() -> new SettingsFeature.Update(false, feature.frame()));
    }

    /**
     * Renders one settled Settings job on the publication lane, asks the shell to refresh Settings-dependent Areas
     * after a publication, and admits any follow-up effect (a dirty Reload answered Save chains into the original
     * Reload). The shell calls this from its runner listener before it continues any close waiting on the job.
     *
     * @param effect the worker effect that settled
     * @param update the feature update after completion or cancellation
     */
    public void completeJob(SettingsFeature.Effect effect, SettingsFeature.Update update) {
        render(update.frame());
        boolean published = update.accepted() && (update.frame().outcome() == SettingsFeature.OutcomeKind.SAVED
                || update.frame().outcome() == SettingsFeature.OutcomeKind.RELOADED
                || update.frame().outcome() == SettingsFeature.OutcomeKind.RECOVERED);
        boolean preferencePublished = effect instanceof SettingsFeature.PreferenceEffect
                && update.accepted() && update.frame().outcome() == SettingsFeature.OutcomeKind.CHANGED;
        if (published || preferencePublished)
            shell.settingsPublished(published);
        // Rejected admission already cancels the feature effect; shutdown races are an ordinary abandoned continuation.
        update.effect().ifPresent(this::submit);
    }

    /** Publishes startup recovery and failure evidence once into durable Activity. */
    public void publishStartupEvidence() {
        if (feature.frame().notices().isEmpty())
            return;
        boolean failed = feature.frame().notices().stream().anyMatch(SettingsFeature.Notice::failure);
        WorkbenchFeedback.Notification notification = new WorkbenchFeedback.Notification("Load Settings",
                failed ? WorkbenchFeedback.Severity.FAILURE : WorkbenchFeedback.Severity.WARNING,
                settingsNoticeSummary(feature.frame()), failed
                ? WorkbenchFeedback.Disposition.FAILED : WorkbenchFeedback.Disposition.COMPLETED_WITH_ISSUES);
        shell.publishFeedback(feedback -> feedback.publishActivity(notification));
    }

    /**
     * Realizes one Settings effect: a dirty-Reload confirmation goes back to the pipeline's dialog round-trip, and a
     * worker effect is admitted through the shell, which attaches any pending close continuation first.
     *
     * @param effect effect requested by the latest Settings update
     * @return the confirmation for the pipeline, or a handled realization that is rejected when admission failed
     */
    private DispatchPipeline.Realization<SettingsFeature.Update> realize(SettingsFeature.Effect effect) {
        if (effect instanceof SettingsFeature.ReloadConfirmationEffect confirmation)
            return DispatchPipeline.Realization.confirm(
                    WorkbenchFeedback.DialogSpec.unsavedClose(confirmation.title(), confirmation.message()),
                    action -> feature.respondReload(confirmation.token(), reloadDecision(action)));
        if (!submit(effect))
            return DispatchPipeline.Realization.settledBy(new SettingsFeature.Update(false, feature.frame()));
        return DispatchPipeline.Realization.done();
    }

    /**
     * Admits one Settings worker effect through the shell's runner. A refused admission has already cancelled the
     * feature's pending effect, so the Settings draft is re-rendered free for the next intent.
     *
     * @param effect captured Save, Reload, or generation-preference effect
     * @return whether the coordinator admitted the job
     */
    private boolean submit(SettingsFeature.Effect effect) {
        boolean admitted = shell.submitSettingsEffect(effect);
        if (!admitted)
            render(feature.frame());
        return admitted;
    }

    /** Maps the dirty-Reload dialog answer to the feature's explicit Save, Discard or Cancel decision. */
    private static SettingsFeature.ReloadDecision reloadDecision(WorkbenchFeedback.DialogAction action) {
        return switch (action) {
            case SAVE -> SettingsFeature.ReloadDecision.SAVE;
            case DISCARD -> SettingsFeature.ReloadDecision.DISCARD;
            case CANCEL -> SettingsFeature.ReloadDecision.CANCEL;
            case COPY_DETAILS, RETRY, REMOVE, CLEAR, CLOSE -> throw new IllegalArgumentException(
                    "Dirty Settings Reload returned an unsupported dialog action: " + action);
        };
    }

    /**
     * Reports a Settings dispatch that completed without an effect and asks the shell to refresh Output's generation
     * settings when it was accepted. Worker effects report through their job terminal records instead.
     */
    private void report(DispatchPipeline.Outcome<SettingsFeature.Intent, SettingsFeature.Update> outcome) {
        SettingsFeature.Update update = outcome.update();
        if (update.effect().isPresent())
            return;
        publishOutcome(update);
        if (update.accepted())
            shell.generationSettingsChanged();
    }

    /** Projects Settings outcomes through inline validation, status-only drafts, and durable bulk Activity. */
    private void publishOutcome(SettingsFeature.Update update) {
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
        WorkbenchFeedback.Notification published = notification;
        if (durable)
            shell.publishFeedback(feedback -> feedback.publishActivity(published));
        else
            shell.publishFeedback(feedback -> feedback.publishStatus(published));
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
    private void render(SettingsFeature.Frame frame) {
        renderingSettings = true;
        try {
            settingsProfileChoice.setValue(frame.profile());
            UiaListHelper.setAllIfChanged(settingsEntryList.getItems(), frame.entries());
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
            boolean mutationsBlocked = context.projectMutationsBlocked();
            boolean backgroundJobBlocked = context.anyJobActive();
            omitRedundantSlidersCheck.setDisable(mutationsBlocked || backgroundJobBlocked);
            boolean editable = frame.editor().isPresent() && !mutationsBlocked;
            for (Control control : List.of(settingsEntryNameInput, settingsSmallInput,
                    settingsBigInput, settingsMultiplierInput, settingsInvertedCheck, applySettingsEntryButton,
                    removeSettingsEntryButton))
                control.setDisable(!editable);
            newSettingsEntryName.setDisable(mutationsBlocked);
            addSettingsEntryButton.setDisable(mutationsBlocked);
            saveSettingsButton.setDisable(mutationsBlocked || backgroundJobBlocked || !frame.dirty()
                    || !frame.validation().isEmpty());
            reloadSettingsButton.setDisable(mutationsBlocked || backgroundJobBlocked);
        } finally {
            renderingSettings = false;
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
}
