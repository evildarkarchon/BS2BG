package com.asdasfa.jbs2bg.workbench.output;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.DispatchPipeline;
import com.asdasfa.jbs2bg.workbench.ShellPort;
import com.asdasfa.jbs2bg.workbench.UiaListHelper;
import com.asdasfa.jbs2bg.workbench.WorkbenchContext;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation;
import com.asdasfa.jbs2bg.workbench.WorkbenchPlatform;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The Output drawer: the generated Output tabs, Copy and the two exports, the drawer height, and the Generate button
 * the shell places in the Area header. It is not an Area, matching the {@code Area} enum: the shell shows it over
 * whichever Area is active. It translates its controls into {@link OutputFeature} intents through the shared
 * {@link DispatchPipeline}, renders only committed immutable frames, realizes the clipboard and chooser effects on the
 * {@link WorkbenchPlatform}, and asks the shell, through the {@link ShellPort}, for feedback and the automatic reveal.
 * <p>
 * The shell drives it through its small interface, {@link #render(WorkbenchContext)}, {@link #focusNode} and
 * {@link #reveal(boolean)}, plus the typed calls the shell makes (its Output shortcuts, generation-settings refresh and
 * content-height fit). Loaded from {@code output-drawer.fxml} by {@link #load}; every method runs on the JavaFX
 * Application Thread.
 */
public final class OutputDrawer {
    private final OutputFeature feature;
    private final DispatchPipeline pipeline;
    private final WorkbenchPlatform platform;
    private final ShellPort shell;
    private final Stage owner;
    @FXML
    private OutputDrawerParts parts;
    @FXML
    private Button generateOutputButton;
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
    // The latest context the shell rendered; this drawer's own update renders read cross-Area inputs only from here.
    private WorkbenchContext context;
    private OutputFeature.Subscription subscription;
    private boolean renderingOutput;

    /**
     * One loaded Output drawer.
     *
     * @param drawer    the drawer controller, with every {@code @FXML} field injected
     * @param namespace the drawer FXML's read-only {@code fx:id} namespace
     */
    public record Loaded(OutputDrawer drawer, Map<String, Object> namespace) {
        /** Requires both values. */
        public Loaded {
            Objects.requireNonNull(drawer, "drawer");
            Objects.requireNonNull(namespace, "namespace");
        }
    }

    /**
     * Creates the controller FXMLLoader injects; use {@link #load} rather than calling this directly.
     *
     * @param feature  the Output feature, built by the shell's composition root with its generation-settings supplier
     * @param pipeline the shared dispatch pipeline
     * @param platform the platform adapter for the clipboard and the export choosers
     * @param shell    the shell port for feedback and the automatic reveal
     * @param owner    the Workbench window that owns the export choosers
     * @throws NullPointerException when an argument is null
     */
    public OutputDrawer(OutputFeature feature, DispatchPipeline pipeline, WorkbenchPlatform platform, ShellPort shell,
                        Stage owner) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.shell = Objects.requireNonNull(shell, "shell");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * Loads {@code output-drawer.fxml} with a controller constructor-injected with these collaborators. The drawer
     * observes the feature's committed publications from now on, and renders nothing else until the shell's first
     * {@link #render(WorkbenchContext)}.
     *
     * @param feature  the Output feature
     * @param pipeline the shared dispatch pipeline
     * @param platform the platform adapter
     * @param shell    the shell port
     * @param owner    the Workbench window
     * @return the loaded drawer and its FXML namespace
     * @throws IllegalStateException when the FXML breaks the drawer's root contract
     */
    public static Loaded load(OutputFeature feature, DispatchPipeline pipeline, WorkbenchPlatform platform,
                              ShellPort shell, Stage owner) {
        AreaLoader.Graph<OutputDrawer, OutputDrawerParts> graph = AreaLoader.loadGraph(OutputDrawer.class,
                "output-drawer.fxml", () -> new OutputDrawer(feature, pipeline, platform, shell, owner),
                OutputDrawerParts.class);
        if (graph.controller().parts() != graph.root())
            throw new IllegalStateException("OutputDrawer.parts() must return its FXML root");
        return new Loaded(graph.controller(), graph.namespace());
    }

    /**
     * Connects Generate, Output tabs, and BoS identity selection to the JavaFX-independent Output feature, maps the
     * height Slider to the drawer, and starts observing committed Output publications. FXMLLoader calls this once
     * every control is injected.
     */
    @FXML
    private void initialize() {
        generateOutputButton.setOnAction(event -> dispatch(new OutputFeature.Generate()));
        copyOutputButton.setOnAction(event -> dispatch(new OutputFeature.Copy()));
        exportOutputButton.setOnAction(event -> dispatch(new OutputFeature.Export()));
        exportSelectedBosButton.setOnAction(event -> dispatch(new OutputFeature.ExportSelected()));
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
            dispatch(new OutputFeature.SelectTab(tab));
        });
        bosArtifactChoice.setOnAction(event -> {
            if (!renderingOutput && bosArtifactChoice.getValue() != null)
                dispatch(new OutputFeature.SelectBosArtifact(bosArtifactChoice.getValue()));
        });
        outputDrawerHeight.valueProperty().addListener((observable, oldValue, newValue) ->
                applyDrawerHeight(newValue.doubleValue()));
        applyDrawerHeight(outputDrawerHeight.getValue());
        subscription = feature.observe(this::renderUpdate);
    }

    /** @return the holder FXMLLoader built from this drawer's FXML root */
    public OutputDrawerParts parts() {
        return parts;
    }

    /**
     * Keeps the shell's context and renders the feature's current frame against it. The shell calls this on every
     * Project frame and every job frame, as it does for the Areas.
     *
     * @param context the context the shell just built
     */
    public void render(WorkbenchContext context) {
        this.context = Objects.requireNonNull(context, "context");
        render(feature.frame());
    }

    /**
     * Resolves the Output landmark to the drawer's focus target.
     *
     * @param landmark the landmark to resolve
     * @return the Output focus target for {@code OUTPUT}, otherwise null
     */
    public Node focusNode(WorkbenchNavigation.Landmark landmark) {
        return switch (landmark) {
            case OUTPUT -> outputFocusTarget;
            case RAIL, PRIMARY_LAUNCHER, PRIMARY_CONTENT, NPC_DATABASE_LAUNCHER, EDITOR, INSPECTOR_LAUNCHER,
                 INSPECTOR, OUTPUT_LAUNCHER, ACTIVITY, STATUS -> null;
        };
    }

    /**
     * Shows or hides the drawer as the shell's navigation frame says; a shown drawer is raised above the Area surface
     * it overlays, and a hidden one leaves the content stack's layout.
     *
     * @param shown whether the navigation frame shows the drawer
     */
    public void reveal(boolean shown) {
        outputDrawer.setManaged(shown);
        outputDrawer.setVisible(shown);
        if (shown)
            outputDrawer.toFront();
    }

    /**
     * Dispatches one Output task and renders synchronous selection updates; Generate completes via observation. The
     * shell's Ctrl+G and Ctrl+E shortcuts call this too.
     *
     * @param intent the Output task
     */
    public void dispatch(OutputFeature.Intent intent) {
        if (intent instanceof OutputFeature.Generate && !context.settingsLiveAvailable()) {
            // Keyboard generation bypasses the disabled button, so enforce the same Settings prerequisite here.
            return;
        }
        pipeline.dispatch(feature, DispatchPipeline.Callbacks.of(
                update -> render(update.frame()),
                effect -> {
                    applyEffect(effect);
                    return DispatchPipeline.Realization.done();
                },
                outcome -> {
                    // Output reports through its observed job publications and applyEffect, not per intent.
                }), Objects.requireNonNull(intent, "intent"));
    }

    /** Re-renders Output against its refreshed generation settings, after the shell saw the Settings change. */
    public void refreshGenerationSettings() {
        render(feature.refreshGenerationSettings().frame());
    }

    /**
     * Keeps the drawer proportional at small client heights while retaining its accepted keyboard resize range. The
     * shell calls this whenever its content stack changes height.
     *
     * @param contentHeight the height of the Workbench content the drawer overlays; a non-positive one is ignored
     */
    public void fitContentHeight(double contentHeight) {
        if (contentHeight <= 0.0)
            return;
        double maximum = Math.max(outputDrawerHeight.getMin(), contentHeight * 0.65);
        outputDrawerHeight.setMax(maximum);
        if (outputDrawerHeight.getValue() > maximum)
            outputDrawerHeight.setValue(maximum);
    }

    /** Stops observing Output publications; the shell calls this once its window is hidden. */
    public void close() {
        subscription.close();
    }

    /** Applies one committed Output publication and consumes its optional drawer reveal exactly once. */
    private void renderUpdate(OutputFeature.Update update) {
        render(Objects.requireNonNull(update, "update").frame());
        update.effect().ifPresent(this::applyEffect);
    }

    /** Realizes one typed Output platform or navigation effect without reading preview-control state. */
    private void applyEffect(OutputFeature.Effect effect) {
        switch (Objects.requireNonNull(effect, "effect")) {
            case OutputFeature.RevealDrawer ignored -> shell.revealOutput();
            case OutputFeature.CopyToClipboard copy -> publishCopyOutcome(copy,
                    platform.copyOutputText(copy.text()));
            case OutputFeature.ChooseExportDirectory chooser -> render(
                    feature.completeExport(chooser.token(), platform.chooseOutputDirectory(owner)).frame());
            case OutputFeature.ChooseExportFile chooser -> render(feature.completeSelectedExport(
                    chooser.token(), platform.chooseOutputFile(chooser.suggestedFileName(), owner)).frame());
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
        shell.publishFeedback(feedback -> feedback.publish(notification));
    }

    /**
     * Renders accepted generated bytes and feature-owned tab/BoS identity without listener command loops. This is the
     * only writer of Generate, whose job and live-Settings prerequisites come from the shell's context.
     */
    private void render(OutputFeature.Frame frame) {
        renderingOutput = true;
        try {
            String emptyText = frame.displayedText();
            frame.generatedOutput().ifPresentOrElse(output -> {
                templatesOutputText.setText(output.getTemplatesText());
                morphsOutputText.setText(output.getMorphsText());
                UiaListHelper.setAllIfChanged(bosArtifactChoice.getItems(), frame.bosArtifactNames());
                bosArtifactChoice.setValue(frame.selectedBosArtifact().orElse(null));
                bosOutputText.setText(frame.selectedBosText());
            }, () -> {
                templatesOutputText.setText(emptyText);
                morphsOutputText.setText(emptyText);
                UiaListHelper.setAllIfChanged(bosArtifactChoice.getItems(), List.of());
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
            boolean jobBlocked = context.anyJobActive();
            generateOutputButton.setDisable(jobBlocked || !context.settingsLiveAvailable());
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

    /** Pins the bottom-aligned resizable VBox to the user-selected drawer height. */
    private void applyDrawerHeight(double height) {
        outputDrawer.setPrefHeight(height);
        outputDrawer.setMaxHeight(height);
    }
}
