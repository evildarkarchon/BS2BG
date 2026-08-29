package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;

import com.asdasfa.jbs2bg.presentation.ProjectDiagnosticFormatter;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
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

/** JavaFX adapter for the Workbench root graph; Project and navigation state remain JavaFX-independent. */
public final class WorkbenchController {

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
    private Label projectStatusText;

    @FXML
    private Label statusText;

    @FXML
    private TextArea diagnosticsText;

    @FXML
    private StackPane contentStack;

    @FXML
    private HBox areaPanes;

    @FXML
    private VBox primaryPane;

    @FXML
    private StackPane editorPane;

    @FXML
    private VBox inspectorPane;

    @FXML
    private StackPane overlayLayer;

    @FXML
    private Button primaryContentButton;

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
    private TextArea outputText;

    private final WorkbenchNavigation navigation = new WorkbenchNavigation();
    private WorkbenchNavigation.Frame navigationFrame = navigation.currentFrame();
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
        configureNavigation();
        configureDrawerGeometry();
        stage.setOnCloseRequest(event -> {
            if (!finalClose) {
                event.consume();
                dispatch(WorkbenchProjectFlow.Intent.CLOSE);
            }
        });
        renderNavigation(navigationFrame);
        render(projectFlow.frame());
        if (workbenchRoot.getWidth() > 0.0)
            applyNavigation(navigation.resize(workbenchRoot.getWidth(), currentSemanticFocus()));
        // attach happens before Stage.show in production, so initial focus is realized on the next JavaFX pulse.
        Platform.runLater(() -> requestFocus(new WorkbenchNavigation.FocusTarget(
                navigationFrame.activeArea(), WorkbenchNavigation.Landmark.PRIMARY_CONTENT)));
    }

    /**
     * Publishes freshly generated text and reveals Output without taking focus from the user's current Area.
     *
     * @param generatedOutput complete generated text to present in the drawer
     * @throws NullPointerException when generatedOutput is null
     * @throws IllegalStateException when called off the JavaFX Application Thread
     */
    public void revealGeneratedOutput(String generatedOutput) {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("Generated Output must be revealed on the JavaFX Application Thread");
        outputText.setText(Objects.requireNonNull(generatedOutput, "generatedOutput"));
        applyNavigation(navigation.revealOutput());
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

    /** Connects rail gestures, accepted keyboard commands, and responsive width changes to typed navigation. */
    private void configureNavigation() {
        ToggleGroup areas = new ToggleGroup();
        for (ToggleButton button : new ToggleButton[] {
                templatesAreaButton, morphsAreaButton, npcDatabaseAreaButton, settingsAreaButton }) {
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
    }

    /** Clips the drawer to Workbench content and maps its public Slider value to drawer height. */
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

    /** Pins the bottom-aligned resizable VBox to the user-selected drawer height. */
    private void applyDrawerHeight(double height) {
        outputDrawer.setPrefHeight(height);
        outputDrawer.setMaxHeight(height);
    }

    /** Keeps the drawer proportional at small client heights while retaining its accepted keyboard resize range. */
    private void updateDrawerMaximum(double contentHeight) {
        if (contentHeight <= 0.0)
            return;
        double maximum = Math.max(outputDrawerHeight.getMin(), contentHeight * 0.65);
        outputDrawerHeight.setMax(maximum);
        if (outputDrawerHeight.getValue() > maximum)
            outputDrawerHeight.setValue(maximum);
    }

    /** Translates one accepted key gesture into a typed navigation transition. */
    private void handleNavigationKey(KeyEvent event) {
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
        }
    }

    /** Navigates from a rail gesture using the currently focused semantic launcher. */
    private void navigate(WorkbenchNavigation.Destination destination) {
        applyNavigation(navigation.navigate(destination, currentSemanticFocus()));
    }

    /** Commits one navigation frame before realizing its optional tokenized focus effect. */
    private void applyNavigation(WorkbenchNavigation.Transition transition) {
        navigationFrame = transition.frame();
        renderNavigation(navigationFrame);
        transition.focusTarget().ifPresent(this::requestFocus);
    }

    /** Renders active Area, responsive panes, and Output visibility from one immutable navigation frame. */
    private void renderNavigation(WorkbenchNavigation.Frame frame) {
        templatesAreaButton.setSelected(frame.activeArea() == WorkbenchNavigation.Area.TEMPLATES);
        morphsAreaButton.setSelected(frame.activeArea() == WorkbenchNavigation.Area.MORPHS);
        npcDatabaseAreaButton.setSelected(frame.activeArea() == WorkbenchNavigation.Area.NPC_DATABASE);
        settingsAreaButton.setSelected(frame.activeArea() == WorkbenchNavigation.Area.SETTINGS);
        outputAreaButton.setSelected(frame.outputDrawerVisible());

        String area = frame.activeArea().displayName();
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

    /** Reparents the real side panes into one overlay so narrow mode never creates a second feature route. */
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

    /** Resolves current JavaFX focus back into a semantic target safe to retain across re-rendering. */
    private WorkbenchNavigation.FocusTarget currentSemanticFocus() {
        Node focusOwner = stage != null && stage.getScene() != null ? stage.getScene().getFocusOwner() : null;
        WorkbenchNavigation.Landmark landmark;
        if (focusOwner == primaryContentButton) {
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
        } else if (focusOwner == outputFocusTarget || focusOwner == outputText || focusOwner == outputDrawerHeight) {
            landmark = WorkbenchNavigation.Landmark.OUTPUT;
        } else if (focusOwner == statusText) {
            landmark = WorkbenchNavigation.Landmark.STATUS;
        } else {
            landmark = WorkbenchNavigation.Landmark.RAIL;
        }
        return new WorkbenchNavigation.FocusTarget(navigationFrame.activeArea(), landmark);
    }

    /** Applies one semantic focus effect, falling back to the active Area's first editable control. */
    private void requestFocus(WorkbenchNavigation.FocusTarget target) {
        Node node = resolveFocusNode(target);
        if (node == null || !node.isVisible() || node.getParent() == null)
            node = editorButton;
        Node resolved = node;
        resolved.requestFocus();
        if (!resolved.isFocused())
            Platform.runLater(resolved::requestFocus);
    }

    /** Maps a semantic target to the currently valid adapter node without retaining that node in navigation state. */
    private Node resolveFocusNode(WorkbenchNavigation.FocusTarget target) {
        return switch (target.landmark()) {
            case RAIL -> areaButton(target.area());
            case PRIMARY_LAUNCHER -> showPrimaryOverlayButton;
            case PRIMARY_CONTENT -> primaryContentButton;
            case EDITOR -> editorButton;
            case INSPECTOR_LAUNCHER -> showInspectorOverlayButton;
            case INSPECTOR -> inspectorButton;
            case OUTPUT_LAUNCHER -> outputAreaButton;
            case OUTPUT -> outputFocusTarget;
            case STATUS -> statusText;
        };
    }

    /** Returns the rail button that semantically launches one full-page Area. */
    private ToggleButton areaButton(WorkbenchNavigation.Area area) {
        return switch (area) {
            case TEMPLATES -> templatesAreaButton;
            case MORPHS -> morphsAreaButton;
            case NPC_DATABASE -> npcDatabaseAreaButton;
            case SETTINGS -> settingsAreaButton;
        };
    }

    /** Dispatches one Project intent, renders its frame, and completes any chained tokenized platform effects. */
    private void dispatch(WorkbenchProjectFlow.Intent intent) {
        apply(projectFlow.request(intent));
    }

    /** Completes synchronous platform effects in order while restoring their semantic launcher focus. */
    private void apply(WorkbenchProjectFlow.Update update) {
        WorkbenchProjectFlow.Update current = update;
        WorkbenchNavigation.FocusTarget returnTarget = null;
        while (true) {
            render(current.frame());
            if (current.effect().isEmpty()) {
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
