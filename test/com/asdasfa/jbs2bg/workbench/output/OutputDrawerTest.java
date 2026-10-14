package com.asdasfa.jbs2bg.workbench.output;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.ModuleTest;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Landmark;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Output drawer loaded alone through its own FXML, over a real {@link OutputFeature} on the hosted coordinator,
 * a recording platform and a recording shell port. The shell's Output shortcuts, navigation and the Settings to
 * Output journey stay in {@code WorkbenchControllerTest}.
 */
class OutputDrawerTest extends ModuleTest<OutputDrawer> {
    private static final String EMPTY_TEXT = "Generate Project output to inspect it here.";
    // The context's live-Settings flag; a test clears it to stand for rejected startup Settings.
    private boolean settingsLive = true;

    /** Builds the Output feature the shell would, and loads the drawer through its production loader. */
    @Override
    protected Mount<OutputDrawer> mount(Collaborators collaborators) {
        Settings settings = collaborators.environment().settings();
        OutputFeature feature = new OutputFeature(collaborators.flow(),
                () -> new OutputFeature.GenerationSettings(settings.snapshot(), false));
        OutputDrawer.Loaded loaded = OutputDrawer.load(feature, collaborators.pipeline(), collaborators.platform(),
                collaborators.shell(), collaborators.stage());
        OutputDrawer drawer = loaded.drawer();
        // The shell reveals the drawer only through navigation; the hosted drawer is always the revealed one.
        drawer.reveal(true);
        OutputDrawerParts parts = drawer.parts();
        return new Mount<>(drawer, loaded.namespace(), new VBox(parts.getGenerateButton(), parts.getDrawer()),
                drawer::render);
    }

    /** @return the test's live-Settings flag */
    @Override
    protected boolean settingsLiveAvailable() {
        return settingsLive;
    }

    /** A coordinator whose worker lane the test advances by hand, publishing inline. */
    private static JobCoordinator manualJobs(ManualExecutor worker) {
        return new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-31T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // These small generated Projects settle before prolonged cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
    }

    /**
     * The drawer's FXML loads on its own: its non-Node root holds the drawer and the Generate button the shell places
     * in the Area header, and the first context render fills the empty Output state.
     */
    @Test
    void outputDrawerLoadsAloneWithItsGenerateButton() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            OutputDrawerParts parts = host().module().parts();

            assertSame(hosted().node("outputDrawer"), parts.getDrawer());
            assertSame(hosted().node("generateOutputButton"), parts.getGenerateButton());
            assertEquals(EMPTY_TEXT, hosted().<TextArea>node("templatesOutputText").getText());
            assertFalse(hosted().<Button>node("generateOutputButton").isDisabled());
            assertTrue(hosted().<Button>node("copyOutputButton").isDisabled());
            assertTrue(hosted().<Button>node("exportOutputButton").isDisabled());
            assertEquals("No generated Output is available.",
                    hosted().<TabPane>node("outputTabs").getAccessibleHelp());
            assertTrue(shell.requests().isEmpty());
        });
    }

    /**
     * The drawer controls keep the accessible names, visible texts and nesting they had in the single Workbench FXML,
     * because the packaged smoke run finds them by name, role and parent region. The expected outline is transcribed
     * from the pre-extraction {@code workbench.fxml}.
     */
    @Test
    void outputDrawerKeepsItsAccessibleOutline() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            OutputDrawerParts parts = host().module().parts();

            assertEquals(List.of("Button #generateOutputButton text=_Generate name=Generate Project output"),
                    outline(parts.getGenerateButton()));
            assertEquals(List.of(
                    "VBox #outputDrawer name=Output",
                    "  HBox",
                    "    Label #outputFocusTarget text=Output name=Output generated text",
                    "    Region",
                    "    Button #copyOutputButton text=_Copy name=Copy selected Output artifact",
                    "    Button #exportOutputButton text=_Export All… name=Export all Output artifacts",
                    "    Label text=Drawer height",
                    "    Slider #outputDrawerHeight name=Output drawer height",
                    "  TabPane #outputTabs name=Generated Output tabs",
                    "    Tab #templatesOutputTab text=Templates",
                    "      TextArea #templatesOutputText name=Generated Templates output",
                    "    Tab #morphsOutputTab text=Morphs",
                    "      TextArea #morphsOutputText name=Generated Morphs output",
                    "    Tab #bosOutputTab text=BoS JSON",
                    "      VBox",
                    "        Label text=BoS Slider _Preset:",
                    "        ComboBox #bosArtifactChoice name=Generated BoS Slider Preset",
                    "        TextArea #bosOutputText name=Generated BoS JSON output",
                    "        Button #exportSelectedBosButton text=Export _Selected… name=Export selected BoS JSON artifact"),
                    outline(parts.getDrawer()));
        });
    }

    /**
     * The drawer answers the Output landmark with its focus target and nothing else, and shows or hides itself as the
     * shell's navigation reveals or dismisses it.
     */
    @Test
    void focusNodeAndRevealFollowTheShellsNavigation() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            OutputDrawer drawer = host().module();
            VBox outputDrawer = hosted().node("outputDrawer");

            assertSame(hosted().node("outputFocusTarget"), drawer.focusNode(Landmark.OUTPUT));
            assertNull(drawer.focusNode(Landmark.OUTPUT_LAUNCHER));
            assertNull(drawer.focusNode(Landmark.EDITOR));

            drawer.reveal(false);
            assertFalse(outputDrawer.isVisible());
            assertFalse(outputDrawer.isManaged());
            drawer.reveal(true);
            assertTrue(outputDrawer.isVisible());
            assertTrue(outputDrawer.isManaged());
        });
    }

    /**
     * The height Slider sizes the drawer, and a short Workbench content area lowers the Slider's maximum to keep the
     * drawer proportional, clamping a taller choice.
     */
    @Test
    void drawerHeightFollowsItsSliderWithinTheContentHeight() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            OutputDrawer drawer = host().module();
            VBox outputDrawer = hosted().node("outputDrawer");
            Slider height = hosted().node("outputDrawerHeight");

            assertEquals(220.0, outputDrawer.getPrefHeight());
            height.setValue(260.0);
            assertEquals(260.0, outputDrawer.getPrefHeight());
            assertEquals(260.0, outputDrawer.getMaxHeight());

            drawer.fitContentHeight(300.0);
            assertEquals(195.0, height.getMax());
            assertEquals(195.0, outputDrawer.getPrefHeight());
            drawer.fitContentHeight(1000.0);
            assertEquals(650.0, height.getMax());
            assertEquals(195.0, height.getValue());
        });
    }

    /**
     * Without live Settings, Generate is disabled and its keyboard dispatch is refused before any job starts, since the
     * shortcut bypasses the disabled button.
     */
    @Test
    void generateNeedsLiveSettingsEvenFromTheKeyboard() throws Exception {
        settingsLive = false;
        FxTestToolkit.runOnFxThread(() -> {
            OutputDrawer drawer = host().module();

            assertTrue(hosted().<Button>node("generateOutputButton").isDisabled());
            drawer.dispatch(new OutputFeature.Generate());
            assertFalse(hosted().flow().jobs().frame().active());
            assertTrue(hosted().flow().jobs().frame().attempt().isEmpty());
        });
    }

    /** Ctrl+G publishes all captured Output tabs through the central job without moving editor focus. */
    @Test
    void generateRendersReadOnlyOutputTabsWithoutStealingFocus() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = manualJobs(worker);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Generated UI"));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow).show();
            Scene scene = hosted().stage().getScene();
            TextArea templates = hosted().node("templatesOutputText");
            // The focus witness is a drawer control the Generate render rewrites; the drawer must not move focus.
            templates.requestFocus();
            // The shell's Ctrl+G calls the drawer's dispatch with Generate.
            hosted().module().dispatch(new OutputFeature.Generate());

            Button generate = hosted().node("generateOutputButton");
            assertTrue(jobs.frame().active());
            assertTrue(generate.isDisabled());
            worker.runNext();

            assertEquals(List.of("revealOutput()"), shell.requests());
            TabPane tabs = hosted().node("outputTabs");
            TextArea morphs = hosted().node("morphsOutputText");
            TextArea bos = hosted().node("bosOutputText");
            assertEquals(3, tabs.getTabs().size());
            assertTrue(templates.getText().startsWith("Generated UI="));
            assertEquals("", morphs.getText());
            assertFalse(bos.getText().isEmpty());
            for (TextArea output : List.of(templates, morphs, bos)) {
                assertFalse(output.isEditable());
                assertTrue(output.isFocusTraversable());
            }
            assertSame(templates, scene.getFocusOwner());
            hosted().stage().close();
        });
    }

    /** Output controls realize accepted copy and complete/selected export effects without reading editable UI state. */
    @Test
    void copyAndExportOutputUseThePlatformAdapterAndCentralJobPath() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = manualJobs(worker);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Platform Output"));
        Path completeDirectory = Files.createDirectory(temporaryDirectory.resolve("complete-output"));
        Path selectedFile = temporaryDirectory.resolve("selected.JSON");
        platform.respondOutputDirectoryWith(Optional.of(completeDirectory));
        platform.respondOutputFileWith(Optional.of(selectedFile));

        FxTestToolkit.runOnFxThread(() -> {
            host(flow).show();
            Scene scene = hosted().stage().getScene();
            TextArea templates = hosted().node("templatesOutputText");
            // The focus witness is a drawer control; copy and both exports must leave focus where it was.
            templates.requestFocus();
            hosted().<Button>node("generateOutputButton").fire();
            worker.runNext();

            Button copy = hosted().node("copyOutputButton");
            Button exportAll = hosted().node("exportOutputButton");
            assertFalse(copy.isDisabled());
            assertFalse(exportAll.isDisabled());
            copy.fire();
            assertEquals(List.of(templates.getText()), platform.clipboardTexts());
            assertEquals("templates.ini copied to the clipboard.",
                    shell.feedbackFrame().infoBar().map(WorkbenchFeedback.InfoBar::message).orElseThrow());
            assertSame(templates, scene.getFocusOwner());

            // The shell's Ctrl+E calls the drawer's dispatch with Export.
            hosted().module().dispatch(new OutputFeature.Export());
            assertTrue(jobs.frame().active());
            assertTrue(exportAll.isDisabled());
            worker.runNext();
            assertArrayEquals(templates.getText().getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(completeDirectory.resolve("templates.ini")));
            assertSame(templates, scene.getFocusOwner());

            TabPane tabs = hosted().node("outputTabs");
            tabs.getSelectionModel().select(hosted().<Tab>node("bosOutputTab"));
            Button selectedExport = hosted().node("exportSelectedBosButton");
            assertFalse(selectedExport.isDisabled());
            String bos = hosted().<TextArea>node("bosOutputText").getText();
            selectedExport.fire();
            worker.runNext();
            assertArrayEquals(bos.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(selectedFile));
            hosted().stage().close();
        });
    }

    /**
     * Lists one node and its descendants, one indented line each: type, {@code fx:id}, Labeled text and accessible
     * name. A TabPane lists each Tab and the Tab's content.
     */
    private static List<String> outline(Node node) {
        List<String> lines = new ArrayList<>();
        outline(node, 0, lines);
        return lines;
    }

    /** Appends one node's line and its children's at the next depth. */
    private static void outline(Node node, int depth, List<String> lines) {
        StringBuilder line = new StringBuilder("  ".repeat(depth)).append(node.getClass().getSimpleName());
        if (node.getId() != null)
            line.append(" #").append(node.getId());
        if (node instanceof Labeled labeled && labeled.getText() != null)
            line.append(" text=").append(labeled.getText());
        if (node.getAccessibleText() != null)
            line.append(" name=").append(node.getAccessibleText());
        lines.add(line.toString());
        if (node instanceof TabPane tabPane) {
            for (Tab tab : tabPane.getTabs()) {
                lines.add("  ".repeat(depth + 1) + "Tab #" + tab.getId() + " text=" + tab.getText());
                outline(tab.getContent(), depth + 2, lines);
            }
        } else if (node instanceof Parent parent && !(node instanceof Control)) {
            parent.getChildrenUnmodifiable().forEach(child -> outline(child, depth + 1, lines));
        }
    }
}
