package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;

import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

/** JavaFX adapter for the Workbench root graph; Project state remains owned by WorkbenchProjectFlow. */
public final class WorkbenchController {

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
    private Label placeholderText;

    @FXML
    private Label projectStatusText;

    @FXML
    private Label statusText;

    @FXML
    private TextArea diagnosticsText;

    private WorkbenchProjectFlow projectFlow;
    private Stage stage;
    private WorkbenchPlatform platform;
    private boolean finalClose;

    /**
     * Attaches the loaded JavaFX graph to the sole Project flow and renders its current frame.
     *
     * @param flow authoritative Workbench Project flow
     * @param ownerStage application window that receives Project titles
     * @throws IllegalStateException when this controller is attached more than once
     */
    public void attach(WorkbenchProjectFlow flow, Stage ownerStage) {
        attach(flow, ownerStage, new JavaFxWorkbenchPlatform());
    }

    /**
     * Attaches one platform adapter on the JavaFX Application Thread. The controller owns the Stage handlers until
     * the flow publishes its final close effect; it cannot be attached again.
     *
     * @param flow authoritative window-scoped Project flow
     * @param ownerStage Stage whose title, close request, choosers, and dialogs are owned by this controller
     * @param platformAdapter native-effect adapter retained for the controller lifetime
     * @throws NullPointerException when an argument is null
     * @throws IllegalStateException when this controller is already attached
     */
    void attach(WorkbenchProjectFlow flow, Stage ownerStage, WorkbenchPlatform platformAdapter) {
        if (projectFlow != null)
            throw new IllegalStateException("WorkbenchController is already attached");
        projectFlow = Objects.requireNonNull(flow, "flow");
        stage = Objects.requireNonNull(ownerStage, "ownerStage");
        platform = Objects.requireNonNull(platformAdapter, "platformAdapter");
        configureProjectCommands();
        configurePlaceholderAreas();
        stage.setOnCloseRequest(event -> {
            if (!finalClose) {
                event.consume();
                dispatch(WorkbenchProjectFlow.Intent.CLOSE);
            }
        });
        render(projectFlow.frame());
    }

    /** Connects File menu commands and their stable keyboard accelerators to Project intents. */
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

    /** Connects the five placeholder rail entries without introducing feature-owned Project routes. */
    private void configurePlaceholderAreas() {
        ToggleGroup areas = new ToggleGroup();
        for (ToggleButton button : new ToggleButton[] { templatesAreaButton, morphsAreaButton,
                npcDatabaseAreaButton, outputAreaButton, settingsAreaButton }) {
            button.setToggleGroup(areas);
            button.setOnAction(event -> showPlaceholder(button.getText()));
        }
        templatesAreaButton.setSelected(true);
        showPlaceholder(templatesAreaButton.getText());
    }

    /** Dispatches one intent, renders its frame, and completes any chained tokenized platform effects. */
    private void dispatch(WorkbenchProjectFlow.Intent intent) {
        apply(projectFlow.request(intent));
    }

    /** Completes all synchronous platform effects in order while rendering every published frame first. */
    private void apply(WorkbenchProjectFlow.Update update) {
        WorkbenchProjectFlow.Update current = update;
        while (true) {
            render(current.frame());
            if (current.effect().isEmpty())
                return;
            WorkbenchProjectFlow.Effect effect = current.effect().orElseThrow();
            if (effect.kind() == WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW) {
                finalClose = true;
                platform.closeWindow(stage);
                return;
            }
            WorkbenchProjectFlow.Response response = platform.complete(effect, stage);
            current = projectFlow.respond(effect.token(), response);
        }
    }

    /** Renders title, lifecycle summary, and complete structured diagnostics from one immutable Project frame. */
    private void render(WorkbenchProjectFlow.Frame frame) {
        stage.setTitle(frame.title());
        projectStatusText.setText(projectStatus(frame));
        diagnosticsText.setText(ProjectDiagnosticFormatter.format(frame.diagnostics()));
        statusText.setText(frame.diagnostics().isEmpty() ? "Ready" : "Project operation reported diagnostics");
    }

    /** Updates only placeholder presentation state; full typed navigation belongs to the next Workbench slice. */
    private void showPlaceholder(String area) {
        areaTitle.setText(area);
        placeholderText.setText(area + " Area — Workbench placeholder");
    }

    /** Derives a concise non-color Project lifecycle summary. */
    private static String projectStatus(WorkbenchProjectFlow.Frame frame) {
        if (frame.snapshot().isDirty())
            return "Unsaved changes";
        return frame.snapshot().getFileIdentity().isPresent() ? "Saved Project" : "Untitled Project";
    }

    /** Creates the conventional Control accelerator for one File command key. */
    private static KeyCodeCombination shortcut(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.CONTROL_DOWN);
    }
}
