package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.DiagnosticSeverity;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.NpcPromotionOutcome;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectEdit;
import com.asdasfa.jbs2bg.project.ProjectOperationContext;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetImportOutcome;
import com.asdasfa.jbs2bg.project.SourceLocation;
import com.asdasfa.jbs2bg.project.ProjectLifecycleStatus;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;
import com.asdasfa.jbs2bg.workbench.templates.SliderChoiceRow;
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesArea;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesFeature;

import javafx.css.PseudoClass;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleRole;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Popup;
import javafx.stage.WindowEvent;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchControllerTest {

    @TempDir
    Path temporaryDirectory;
    // The test's one Settings store, environment, and session source; see fixture().
    private WorkbenchTestEnvironment fixture;

    /**
     * Returns this test's Workbench fixture, creating and reloading its store in the temporary directory on first
     * use. Tests that seed custom Settings files write them before the first call.
     */
    private WorkbenchTestEnvironment fixture() {
        if (fixture == null)
            fixture = WorkbenchTestEnvironment.initialized(temporaryDirectory);
        return fixture;
    }

    /**
     * The NPC Database inspector finds its portrait in the environment's image directory and opens the shared
     * viewer, which the Workbench closes when its own window is hidden.
     */
    @Test
    void npcDatabasePortraitComesFromTheEnvironmentImageDirectoryAndItsViewerClosesWithTheWorkbench()
            throws Exception {
        Path images = Files.createDirectories(temporaryDirectory.resolve("portraits"));
        Files.copy(Path.of("assets", "res", "icon.png"), images.resolve("Alpha (Alpha01).png"));
        Path source = temporaryDirectory.resolve("npcs.txt");
        Files.writeString(source, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n");
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        AtomicReference<FXMLLoader> loaded = new AtomicReference<>();
        AtomicReference<Stage> shown = new AtomicReference<>();
        AtomicReference<Stage> viewer = new AtomicReference<>();
        CountDownLatch thumbnailLoaded = new CountDownLatch(1);
        try {
            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
                Parent root = loader.load();
                WorkbenchController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1300.0, 800.0));
                loaded.set(loader);
                shown.set(stage);
                controller.attach(flow, stage, platform, fixture().environment().withPortraitDirectory(images));
                stage.show();
                Button launcher = (Button) LoadedWorkbench.node(loader, "openNpcDatabasePortraitViewerButton");
                // The thumbnail decodes in the background; the launcher enables once it has loaded.
                launcher.disableProperty().addListener((observable, previous, disabled) -> {
                    if (!disabled)
                        thumbnailLoaded.countDown();
                });
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                if (!launcher.isDisabled())
                    thumbnailLoaded.countDown();
            });
            assertTrue(thumbnailLoaded.await(30, TimeUnit.SECONDS), "portrait thumbnail never loaded");
            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = loaded.get();
                assertEquals("Portrait file: Alpha (Alpha01).png",
                        ((Label) LoadedWorkbench.node(loader, "npcDatabasePortraitStatus")).getText());
                ((Button) LoadedWorkbench.node(loader, "openNpcDatabasePortraitViewerButton")).fire();
                viewer.set(FxTestToolkit.showingOwnedStage(shown.get(), ".npc-portrait-viewer"));
                assertEquals("NPC Portrait — Alpha", viewer.get().getTitle());
            });
            // The viewer's background full-image load holds the portrait open; let it finish so the @TempDir can be
            // deleted on Windows.
            PortraitTestSupport.awaitFullImage(viewer.get());
            FxTestToolkit.runOnFxThread(() -> {
                shown.get().close();

                assertFalse(viewer.get().isShowing(), "the viewer closes with the Workbench window");
            });
        } finally {
            FxTestToolkit.runOnFxThread(() -> {
                if (shown.get() != null)
                    shown.get().close();
            });
        }
    }

    /** The Morphs launcher returns to its focus and newly promoted identity after a selected Add. */
    @Test
    void npcDatabaseAddReturnsToMorphsLauncherAndPromotedAssignment() throws Exception {
        Path source = temporaryDirectory.resolve("promote-npcs.txt");
        Files.writeString(source, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n");
        ProjectSession session = fixture().newSession();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        flow.apply(SliderPresetEdits.create("Shape"));
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300.0, 800.0);
            stage.setScene(scene);
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                Button launcher = (Button) LoadedWorkbench.node(loader, "addNpcsFromDatabaseButton");
                launcher.requestFocus();
                launcher.fire();
                assertTrue(((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).isSelected());
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                ((CheckBox) LoadedWorkbench.node(loader, "assignRandomNpcPresetCheck")).fire();

                ((Button) LoadedWorkbench.node(loader, "addNpcToProjectButton")).fire();

                assertEquals("Amber", catalog.getSelectionModel().getSelectedItem().getName());
                assertEquals(1, session.getSnapshot().getNpcMorphAssignments().size());
                assertEquals(List.of("Shape"), session.getSnapshot().getNpcMorphAssignments().getFirst()
                        .getSliderPresetNames());
                ((Button) LoadedWorkbench.node(loader, "addNpcToProjectButton")).fire();
                assertEquals(1, session.getSnapshot().getNpcMorphAssignments().size());
                @SuppressWarnings("unchecked")
                ListView<NpcMorphAssignmentSnapshot> assignments = (ListView<NpcMorphAssignmentSnapshot>)
                        root.lookup("#npcMorphAssignmentList");
                assertEquals(1, assignments.getItems().size());
                ((Button) LoadedWorkbench.node(loader, "backToMorphsButton")).fire();
                assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());
                assertSame(launcher, scene.getFocusOwner());
                assertEquals("Amber", assignments.getSelectionModel().getSelectedItem().getDisplayName());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * Back to Morphs dispatches its return selection through the gated pipeline rather than straight into the
     * feature. Selecting an NPC is browsing, not a Project mutation, so a job that blocks mutations still lets the
     * user land on the assignment they just promoted.
     */
    @Test
    void backToMorphsSelectsThePromotedAssignmentThroughTheGateWhileAJobBlocksMutations() throws Exception {
        Path npcSource = temporaryDirectory.resolve("gated-return.txt");
        Files.writeString(npcSource, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n");
        Path presetSource = temporaryDirectory.resolve("blocking-import.xml").toAbsolutePath().normalize();
        Files.writeString(presetSource, "<SliderPresets><Preset name=\"Blocking Import\"/></SliderPresets>");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Each job is settled by the test before prolonged feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        ProjectSession session = fixture().newSession();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session, jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(npcSource)));
        platform.respondWith(WorkbenchProjectFlow.Response.selectedSources(List.of(presetSource)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "addNpcsFromDatabaseButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                worker.runNext();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                ((Button) LoadedWorkbench.node(loader, "addNpcToProjectButton")).fire();
                assertEquals(1, session.getSnapshot().getNpcMorphAssignments().size());
                ((Button) LoadedWorkbench.node(loader, "importBodySlideButton")).fire();
                assertTrue(jobs.frame().projectMutationsBlocked());

                ((Button) LoadedWorkbench.node(loader, "backToMorphsButton")).fire();

                assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());
                ListView<NpcMorphAssignmentSnapshot> assignments =
                        LoadedWorkbench.morphs(controller).npcMorphAssignmentListNode();
                assertEquals("Amber", assignments.getSelectionModel().getSelectedItem().getDisplayName());
                worker.runNext();
            } finally {
                stage.close();
            }
        });
    }

    /**
     * Enter on the catalog reaches Add even though the job disables the Add button, so the dispatch pipeline's job
     * gate is what refuses the promotion while a job blocks Project mutations. Once the job settles, the same key
     * promotes the row.
     */
    @Test
    void npcDatabasePromotionIsRefusedWhileAJobBlocksProjectMutations() throws Exception {
        Path npcSource = temporaryDirectory.resolve("gated-promotion.txt");
        Files.writeString(npcSource, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n");
        Path presetSource = temporaryDirectory.resolve("blocking-import.xml").toAbsolutePath().normalize();
        Files.writeString(presetSource, "<SliderPresets><Preset name=\"Blocking Import\"/></SliderPresets>");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Each job is settled by the test before prolonged feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        ProjectSession session = fixture().newSession();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session, jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(npcSource)));
        platform.respondWith(WorkbenchProjectFlow.Response.selectedSources(List.of(presetSource)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                worker.runNext();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                ((Button) LoadedWorkbench.node(loader, "importBodySlideButton")).fire();
                assertTrue(jobs.frame().projectMutationsBlocked());

                catalog.requestFocus();
                sendKey(catalog, KeyCode.ENTER);

                assertTrue(session.getSnapshot().getNpcMorphAssignments().isEmpty());
                assertFalse(((Label) LoadedWorkbench.node(loader, "npcPromotionInfoBarMessage")).getText()
                        .contains("Amber"));
                assertEquals("Amber", catalog.getSelectionModel().getSelectedItem().getName());
                worker.runNext();
                assertFalse(jobs.frame().projectMutationsBlocked());

                sendKey(catalog, KeyCode.ENTER);

                assertEquals(1, session.getSnapshot().getNpcMorphAssignments().size());
            } finally {
                stage.close();
            }
        });
    }

    /** Filtered Add All keeps catalog selection and exposes every duplicate and validation refusal. */
    @Test
    void npcDatabaseAddAllReportsFilteredDuplicatesAndRejections() throws Exception {
        Path source = temporaryDirectory.resolve("mixed-promotion.txt");
        Files.writeString(source, "Skyrim.esm | Existing | Existing01 | NordRace | 00000A\n"
                + "Skyrim.esm | Good | Good01 | NordRace | 00000B\n"
                + "Skyrim.esm | Invalid | Invalid01 | NordRace | XYZ\n"
                + "Skyrim.esm | Hidden | Hidden01 | NordRace | 00000C\n");
        ProjectSession session = fixture().newSession();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        flow.apply(NpcMorphAssignmentEdits.create("Existing", "Skyrim.esm", "Existing01", "NordRace", "A"));
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                hideNpcColumnValue(loader, "Name", "Hidden");

                ((Button) LoadedWorkbench.node(loader, "addAllNpcsToProjectButton")).fire();

                assertEquals(2, session.getSnapshot().getNpcMorphAssignments().size());
                assertEquals("Existing", catalog.getSelectionModel().getSelectedItem().getName());
                assertEquals(3, catalog.getItems().size());
                String summary = ((Label) LoadedWorkbench.node(loader, "npcPromotionInfoBarMessage")).getText();
                assertTrue(summary.contains("Added 1"));
                assertTrue(summary.contains("1 already"));
                assertTrue(summary.contains("1 rejected"));
                String details = ((TextArea) LoadedWorkbench.node(loader, "npcPromotionDetails")).getText();
                assertTrue(details.contains("Existing01"));
                assertTrue(details.contains("Invalid01"));
                assertFalse(details.contains("Hidden01"));
                assertEquals("", ((TextArea) loader.getNamespace().get("diagnosticsText")).getText());
                assertTrue(((HBox) LoadedWorkbench.node(loader, "npcPromotionInfoBar")).isVisible());
                assertFalse(((HBox) loader.getNamespace().get("infoBar")).isVisible());
                WorkbenchFeedback.ActivityRecord activity = (WorkbenchFeedback.ActivityRecord)
                        ((ListView<?>) loader.getNamespace().get("activityList")).getItems().getLast();
                assertEquals("Add All NPCs to Project", activity.operation());
                assertTrue(activity.details().orElseThrow().contains("Existing01"));
                assertTrue(activity.details().orElseThrow().contains("Invalid01"));
                catalog.requestFocus();
                sendKey(catalog, KeyCode.ENTER);
                assertEquals(2, session.getSnapshot().getNpcMorphAssignments().size());
                assertTrue(((Label) LoadedWorkbench.node(loader, "npcPromotionInfoBarMessage")).getText()
                        .contains("Already in the Project"));
            } finally {
                stage.close();
            }
        });
    }

    /** A later direct visit cannot reuse an earlier visit's promoted return selection or inline result. */
    @Test
    void npcDatabaseRailExitDiscardsEarlierPromotionReturnContext() throws Exception {
        Path source = temporaryDirectory.resolve("return-context.txt");
        Files.writeString(source, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n");
        ProjectSession session = fixture().newSession();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                Button add = (Button) LoadedWorkbench.node(loader, "addNpcToProjectButton");
                add.fire();
                add.fire();
                assertTrue(((HBox) LoadedWorkbench.node(loader, "npcPromotionInfoBar")).isVisible());

                ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                assertFalse(((HBox) LoadedWorkbench.node(loader, "npcPromotionInfoBar")).isVisible());
                ((Button) LoadedWorkbench.node(loader, "backToMorphsButton")).fire();

                @SuppressWarnings("unchecked")
                ListView<NpcMorphAssignmentSnapshot> assignments = (ListView<NpcMorphAssignmentSnapshot>)
                        root.lookup("#npcMorphAssignmentList");
                assertEquals(1, assignments.getItems().size());
                assertNull(assignments.getSelectionModel().getSelectedItem());
            } finally {
                stage.close();
            }
        });
    }

    /** A failed source keeps an earlier catalog and Activity Retry reads repaired bytes as a linked attempt. */
    @Test
    void npcDatabaseFailedImportCanRetryWithoutLosingCommittedSource() throws Exception {
        Path good = temporaryDirectory.resolve("committed-npcs.txt");
        Path missing = temporaryDirectory.resolve("missing-npcs.txt");
        Files.writeString(good, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n");
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(good)));
        platform.respondNpcSourcesWith(Optional.of(List.of(missing)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                Button importButton = (Button) LoadedWorkbench.node(loader, "importNpcSourcesButton");
                importButton.fire();
                importButton.fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                assertTrue(((Label) LoadedWorkbench.node(loader, "npcDatabaseDiagnostics")).getText()
                        .contains("NPC_DATABASE_SOURCE_READ_FAILED"));

                Files.writeString(missing, "Skyrim.esm | Beta | Beta01 | BretonRace | 00012346\n");
                @SuppressWarnings("unchecked")
                ListView<WorkbenchFeedback.ActivityRecord> activity =
                        (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
                activity.getSelectionModel().selectLast();
                Button retry = (Button) loader.getNamespace().get("retryActivityButton");
                assertFalse(retry.isDisabled());
                retry.fire();
                assertEquals(List.of("Alpha", "Beta"), catalog.getItems().stream().map(NPC::getName).toList());
                assertTrue(activity.getItems().getLast().jobDetails().orElseThrow().retryOf().isPresent());
                assertEquals("No source diagnostics",
                        ((Label) LoadedWorkbench.node(loader, "npcDatabaseDiagnostics")).getText());
            } finally {
                stage.close();
            }
        });
    }

    /** Cancelling a queued import retains the prior session catalog and New clears selection, not sources. */
    @Test
    void npcDatabaseCancelRetainsEarlierSourcesAcrossNewProject() throws Exception {
        Path first = temporaryDirectory.resolve("prior-npcs.txt");
        Path cancelled = temporaryDirectory.resolve("cancelled-npcs.txt");
        Files.writeString(first, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n");
        Files.writeString(cancelled, "Skyrim.esm | Beta | Beta01 | BretonRace | 00012346\n");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-09-22T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The queued cancellation settles before prolonged status can matter.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(first)));
        platform.respondNpcSourcesWith(Optional.of(List.of(cancelled)));
        platform.respondWith(WorkbenchProjectFlow.Response.discard());
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                Button importButton = (Button) LoadedWorkbench.node(loader, "importNpcSourcesButton");
                importButton.fire();
                worker.runNext();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                catalog.getSelectionModel().selectFirst();

                importButton.fire();
                assertTrue(importButton.isDisabled());
                assertTrue(((ProgressBar) loader.getNamespace().get("operationProgress")).isVisible());
                ((ToggleButton) loader.getNamespace().get("templatesAreaButton")).fire();
                ((TextField) LoadedWorkbench.node(loader, "sliderPresetNameInput")).setText("Edited during NPC import");
                Button createPreset = (Button) LoadedWorkbench.node(loader, "createSliderPresetButton");
                assertFalse(createPreset.isDisabled());
                createPreset.fire();
                assertEquals(1, flow.frame().snapshot().getSliderPresets().size());
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) loader.getNamespace().get("cancelOperationButton")).fire();
                assertEquals(JobCoordinator.Lifecycle.CANCELLED, jobs.frame().attempt().orElseThrow().lifecycle());
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                ListView<?> sources = (ListView<?>) LoadedWorkbench.node(loader, "npcSourceList");
                assertEquals(1, sources.getItems().size());
                sources.getSelectionModel().selectFirst();

                ((MenuItem) loader.getNamespace().get("newProjectMenuItem")).fire();
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                assertNull(catalog.getSelectionModel().getSelectedItem());
                assertNull(sources.getSelectionModel().getSelectedItem());
            } finally {
                stage.close();
                jobs.close();
            }
        });
    }

    /** Cancellation after source-one progress commits only that complete source, never source two. */
    @Test
    void npcDatabaseCancellationAtSourceBoundaryReportsOnlyPriorCommit() throws Exception {
        Path first = temporaryDirectory.resolve("first-boundary.txt");
        Path second = temporaryDirectory.resolve("second-boundary.txt");
        Files.writeString(first, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n");
        Files.writeString(second, "Skyrim.esm | Beta | Beta01 | BretonRace | 00012346\n");
        JobCoordinator jobs = new JobCoordinator(new InlineExecutorService(), Runnable::run,
                Clock.fixed(Instant.parse("2026-09-22T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Inline work has no prolonged cancellation interval.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        AtomicBoolean cancelledAtBoundary = new AtomicBoolean();
        JobCoordinator.Subscription cancellation = jobs.observe(frame -> frame.attempt().ifPresent(attempt -> {
            if (attempt.operation().name().equals("Import NPC Database Sources")
                    && attempt.progress().completedUnits().orElse(-1L) == 1L
                    && cancelledAtBoundary.compareAndSet(false, true))
                jobs.requestCancel();
        }));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(first, second)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                assertTrue(cancelledAtBoundary.get());
                assertEquals(JobCoordinator.Lifecycle.CANCELLED, jobs.frame().attempt().orElseThrow().lifecycle());
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                @SuppressWarnings("unchecked")
                ListView<WorkbenchFeedback.ActivityRecord> activity =
                        (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
                assertEquals(List.of("Imported " + first.toAbsolutePath().normalize()),
                        activity.getItems().getLast().jobDetails().orElseThrow().effectsCommitted());
            } finally {
                stage.close();
                cancellation.close();
                jobs.close();
            }
        });
    }

    /** NPC table type-ahead cycles visible names and Ctrl+K reaches the named column-filter button. */
    @Test
    void npcDatabaseKeyboardSearchCyclesAndFocusesFiltering() throws Exception {
        Path source = temporaryDirectory.resolve("keyboard-npcs.txt");
        Files.writeString(source, "Master.esm | Amber | Amber01 | NordRace | 000001\n"
                + "Master.esm | Azure | Azure01 | NordRace | 000002\n"
                + "Master.esm | Beta | Beta01 | NordRace | 000003\n");
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300.0, 800.0);
            stage.setScene(scene);
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) LoadedWorkbench.node(loader, "npcCatalogTable");
                catalog.requestFocus();
                for (int index = 0; index < 2; index++)
                    catalog.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, "a", "a", KeyCode.UNDEFINED,
                            false, false, false, false));
                assertEquals("Azure", catalog.getSelectionModel().getSelectedItem().getName());
                sendControlKey(root, KeyCode.K);
                assertSame(LoadedWorkbench.node(loader, "npcNameFilterButton"), scene.getFocusOwner());
                sendKey(root, KeyCode.ESCAPE);
                assertNull(catalog.getSelectionModel().getSelectedItem());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * Fill Empty Apply goes through the gated dispatch pipeline: once a job blocks Project mutations, applying an
     * offer opened before the job leaves every NPC Morph Assignment untouched and closes the flyout.
     */
    @Test
    void fillEmptyApplyIsRefusedWhileAJobBlocksProjectMutations() throws Exception {
        Path source = temporaryDirectory.resolve("blocking-import.xml").toAbsolutePath().normalize();
        Files.writeString(source, "<SliderPresets><Preset name=\"Blocking Import\"/></SliderPresets>");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The import is settled by the test before prolonged feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selectedSources(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                stage.show();
                ((Button) LoadedWorkbench.node(loader, "fillEmptyNpcMorphAssignmentsButton")).fire();
                Popup flyout = FxTestToolkit.showingPopup(stage, "#fillEmptyApplyButton");
                @SuppressWarnings("unchecked")
                ListView<SliderPresetSnapshot> choices = (ListView<SliderPresetSnapshot>) flyout.getScene()
                        .getRoot().lookup("#fillEmptySliderPresetList");
                choices.getSelectionModel().selectFirst();
                ((Button) LoadedWorkbench.node(loader, "importBodySlideButton")).fire();
                assertTrue(jobs.frame().projectMutationsBlocked());

                ((Button) flyout.getScene().getRoot().lookup("#fillEmptyApplyButton")).fire();

                assertFalse(flyout.isShowing());
                assertTrue(flow.frame().snapshot().getNpcMorphAssignments().getFirst()
                        .getSliderPresetNames().isEmpty());
                worker.runNext();
                assertFalse(jobs.frame().projectMutationsBlocked());
                assertTrue(flow.frame().snapshot().getNpcMorphAssignments().getFirst()
                        .getSliderPresetNames().isEmpty());
            } finally {
                stage.close();
            }
        });
    }

    /** Opening the narrow NPC inspector reveals its portrait rather than retaining a prior relationship scroll. */
    @Test
    void narrowNpcInspectorOpensAtPortraitAfterEarlierRelationshipScroll() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "HousecarlWhiterun",
                "NordRace", "000A2C94"));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 900.0, 700.0);
            stage.setScene(scene);
            try {
                controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                LoadedWorkbench.morphs(controller).npcMorphAssignmentListNode().getSelectionModel().selectFirst();
                stage.show();
                root.resize(900.0, 700.0);
                ScrollPane inspector = (ScrollPane) LoadedWorkbench.node(loader, "morphsInspectorScroll");
                inspector.setVvalue(1.0);

                sendKey(root, KeyCode.F7);

                assertEquals(0.0, inspector.getVvalue());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * In narrow mode Ctrl+K in Morphs first opens the primary-content overlay, where both Morphs filters live, then
     * focuses the filter of the catalog in use (#152: the Morphs Area asks the shell to reveal its primary content).
     */
    @Test
    void narrowMorphsSearchOpensThePrimaryOverlayBeforeFocusingTheFilter() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 900.0, 700.0);
            stage.setScene(scene);
            try {
                controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                stage.show();
                root.resize(900.0, 700.0);
                StackPane overlay = (StackPane) loader.getNamespace().get("overlayLayer");
                // Entering narrow mode may open the primary overlay; dismiss it so Ctrl+K has to reopen it.
                if (!overlay.getChildren().isEmpty())
                    sendKey(root, KeyCode.ESCAPE);
                assertFalse(overlay.getChildren().contains(loader.getNamespace().get("primaryPane")));

                sendControlKey(root, KeyCode.K);

                assertTrue(overlay.getChildren().contains(loader.getNamespace().get("primaryPane")));
                assertSame(LoadedWorkbench.node(loader, "customMorphTargetFilter"), scene.getFocusOwner());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * Add NPCs hands the shell its own launcher as the NPC Database return target, so Back to Morphs returns there
     * even when the launcher fired without holding focus (as a mnemonic can).
     */
    @Test
    void backToMorphsReturnsToTheAddNpcsLauncherThatFiredWithoutFocus() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(CustomMorphTargetEdits.create("All|Female"));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300.0, 800.0);
            stage.setScene(scene);
            try {
                controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
                stage.show();
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                LoadedWorkbench.morphs(controller).customMorphTargetListNode().requestFocus();
                Button launcher = (Button) LoadedWorkbench.node(loader, "addNpcsFromDatabaseButton");

                launcher.fire();
                assertTrue(((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).isSelected());
                ((Button) LoadedWorkbench.node(loader, "backToMorphsButton")).fire();

                assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());
                assertSame(launcher, scene.getFocusOwner());
            } finally {
                stage.close();
            }
        });
    }

    /** Short catalogs remain fully visible as pixel snapping changes, without redundant accessible scrollbars. */
    @ParameterizedTest
    @CsvSource({"1.0,false", "1.25,false", "1.5,false", "1.75,false",
            "1.0,true", "1.25,true", "1.5,true", "1.75,true"})
    void shortCatalogsFitAllRowsAtFractionalRenderScales(double renderScale, boolean morphs) throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));
        flow.apply(CustomMorphTargetEdits.create("First"));
        flow.apply(CustomMorphTargetEdits.create("Second"));
        AtomicReference<Stage> shown = new AtomicReference<>();
        AtomicReference<ListView<?>> shownCatalog = new AtomicReference<>();
        // Multi-block test: the shown Stage stays live on the FX thread while the test thread waits for real
        // layout pulses between the bodies (FxTestToolkit.awaitLayoutPulses), instead of a nested event loop.
        try {
            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
                Parent root = loader.load();
                WorkbenchController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1300.0, 800.0));
                stage.getScene().getStylesheets().add(Main.class.getResource("workbench.css").toExternalForm());
                stage.setForceIntegerRenderScale(false);
                stage.setRenderScaleX(renderScale);
                stage.setRenderScaleY(renderScale);
                controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
                if (morphs)
                    ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                shownCatalog.set(morphs ? LoadedWorkbench.morphs(controller).customMorphTargetListNode()
                        : LoadedWorkbench.templates(controller).sliderPresetListNode());
                shown.set(stage);
                stage.show();
                // Showing a window resets its render scale to the screen's output scale, so force it again.
                stage.setRenderScaleX(renderScale);
                stage.setRenderScaleY(renderScale);
            });
            FxTestToolkit.awaitLayoutPulses(shown.get().getScene(), 2);
            FxTestToolkit.runOnFxThread(() -> {
                assertRenderScale(renderScale, shown.get());
                assertShortCatalogHasNoVerticalScrollbar(shownCatalog.get());
                // A displayed window can move to another monitor without any Project frame changing.
                shown.get().setRenderScaleX(1.5);
                shown.get().setRenderScaleY(1.5);
            });
            FxTestToolkit.awaitLayoutPulses(shown.get().getScene(), 2);
            FxTestToolkit.runOnFxThread(() -> {
                assertRenderScale(1.5, shown.get());
                assertShortCatalogHasNoVerticalScrollbar(shownCatalog.get());
            });
        } finally {
            if (shown.get() != null)
                FxTestToolkit.runOnFxThread(() -> shown.get().close());
        }
    }

    /**
     * Proves a forced render scale actually reached the shown window, so the scrollbar assertions that follow
     * really ran at the fractional scale rather than at the headless screen's own scale.
     */
    private static void assertRenderScale(double expected, Stage stage) {
        assertEquals(expected, stage.getRenderScaleX(), "renderScaleX override was not applied");
        assertEquals(expected, stage.getRenderScaleY(), "renderScaleY override was not applied");
    }

    /** Asserts user-visible list overflow through JavaFX's public accessibility surface. */
    private static void assertShortCatalogHasNoVerticalScrollbar(ListView<?> list) {
        list.layout();
        assertEquals(2, list.getItems().size());
        Node scrollbar = (Node) list.queryAccessibleAttribute(AccessibleAttribute.VERTICAL_SCROLLBAR);
        assertTrue(scrollbar == null || !scrollbar.isVisible(),
                () -> list.getId() + " unnecessarily scrolls: height=" + list.getHeight()
                        + ", top=" + list.snappedTopInset() + ", bottom=" + list.snappedBottomInset());
    }

    /**
     * Morphs validation stays pane-local, relationship removal is immediate, and destructive relationship/catalog
     * clears consume the correctly named confirmation actions without retargeting selection.
     */
    @Test
    void morphsValidationAndDestructiveActionsUseSafeTypedFeedback() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("Another"));
        flow.apply(CustomMorphTargetEdits.create("Existing"));
        RecordingPlatform platform = new RecordingPlatform();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform, fixture().environment());
            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> targets =
                    (ListView<CustomMorphTargetSnapshot>) LoadedWorkbench.node(loader, "customMorphTargetList");
            TextField name = (TextField) LoadedWorkbench.node(loader, "customMorphTargetNameInput");
            Button create = (Button) LoadedWorkbench.node(loader, "createCustomMorphTargetButton");
            @SuppressWarnings("unchecked")
            ComboBox<SliderPresetSnapshot> available =
                    (ComboBox<SliderPresetSnapshot>) LoadedWorkbench.node(loader, "availableMorphSliderPreset");
            Button assign = (Button) LoadedWorkbench.node(loader, "assignMorphSliderPresetButton");
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> assigned =
                    (ListView<SliderPresetSnapshot>) LoadedWorkbench.node(loader, "assignedMorphSliderPresetList");
            Button removeAssignment = (Button) LoadedWorkbench.node(loader, "removeMorphSliderPresetButton");
            Button clearAssignments = (Button) LoadedWorkbench.node(loader, "clearMorphSliderPresetsButton");
            Button removeTarget = (Button) LoadedWorkbench.node(loader, "removeCustomMorphTargetButton");
            Button clearTargets = (Button) LoadedWorkbench.node(loader, "clearCustomMorphTargetsButton");
            TextField filter = (TextField) LoadedWorkbench.node(loader, "customMorphTargetFilter");

            name.setText("existing");
            create.fire();
            assertTrue(((HBox) LoadedWorkbench.node(loader, "morphsInfoBar")).isVisible());
            assertFalse(((HBox) LoadedWorkbench.node(loader, "templatesInfoBar")).isVisible());
            assertTrue(((ListView<?>) loader.getNamespace().get("activityList")).getItems().isEmpty());

            targets.getSelectionModel().select(targets.getItems().stream()
                    .filter(target -> target.getName().equals("Existing")).findFirst().orElseThrow());
            available.setValue(available.getItems().getFirst());
            available.fireEvent(new ActionEvent());
            assign.fire();
            assigned = LoadedWorkbench.morphs(controller).assignedMorphSliderPresetListNode();
            assigned.getSelectionModel().selectFirst();
            assigned.getSelectionModel().clearSelection();
            assertTrue(removeAssignment.isDisabled());
            assigned.getSelectionModel().selectFirst();
            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.REMOVE);
            removeAssignment.fire();
            assigned = LoadedWorkbench.morphs(controller).assignedMorphSliderPresetListNode();
            assertTrue(assigned.getItems().isEmpty());

            available.setValue(available.getItems().getFirst());
            available.fireEvent(new ActionEvent());
            assign.fire();
            assigned = LoadedWorkbench.morphs(controller).assignedMorphSliderPresetListNode();
            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CLEAR);
            clearAssignments.fire();
            assertTrue(assigned.getItems().isEmpty());

            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CANCEL);
            removeTarget.fire();
            assertEquals(2, flow.frame().snapshot().getCustomMorphTargets().size());
            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.REMOVE);
            removeTarget.fire();
            assertEquals(List.of("Another"), flow.frame().snapshot().getCustomMorphTargets().stream()
                    .map(CustomMorphTargetSnapshot::getName).toList());
            assertNull(targets.getSelectionModel().getSelectedItem());

            filter.setText("ano");
            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CLEAR);
            clearTargets.fire();
            assertTrue(flow.frame().snapshot().getCustomMorphTargets().isEmpty());
            stage.close();
        });
    }

    /** Confirms that destructive responses report validation or failure while retaining the selected target. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void confirmedMorphsRemovalReportsRejectedAndFailedOutcomes(boolean failed) throws Exception {
        RefusingEditSession session = new RefusingEditSession(fixture().newSession());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        flow.apply(CustomMorphTargetEdits.create("Existing"));
        session.refuseEdits(failed);
        RecordingPlatform platform = new RecordingPlatform();
        java.util.concurrent.atomic.AtomicBoolean failureShown = new java.util.concurrent.atomic.AtomicBoolean();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.REMOVE);
        platform.respondFailureWith(WorkbenchFeedback.DialogAction.CLOSE, () -> failureShown.set(true));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                ListView<?> targets = (ListView<?>) LoadedWorkbench.node(loader, "customMorphTargetList");
                targets.getSelectionModel().selectFirst();
                ((Button) LoadedWorkbench.node(loader, "removeCustomMorphTargetButton")).fire();

                assertTrue(((Label) LoadedWorkbench.node(loader, "morphsInfoBarMessage")).getText()
                        .contains("The target could not be removed."));
                assertTrue(((Label) loader.getNamespace().get("statusText")).getText()
                        .contains("The target could not be removed."));
                assertEquals(failed, failureShown.get());
                assertEquals("Existing", ((CustomMorphTargetSnapshot) targets.getSelectionModel()
                        .getSelectedItem()).getName());
                assertEquals(1, flow.frame().snapshot().getCustomMorphTargets().size());
                assertTrue(((ListView<?>) loader.getNamespace().get("activityList")).getItems().isEmpty());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * The Settings Area edits both profiles through immutable feature frames, persists them as one pair, and records
     * one durable save result without routing through a legacy controller.
     */
    @Test
    void settingsAreaEditsAndPersistsBothProfilesThroughTheWorkbench() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The deterministic Settings save settles before prolonged cancellation is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Settings Refresh"));
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ComboBox<SettingsFeature.Profile> profiles =
                    (ComboBox<SettingsFeature.Profile>) LoadedWorkbench.node(loader, "settingsProfileChoice");
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            TextField small = (TextField) LoadedWorkbench.node(loader, "settingsSmallInput");
            TextField multiplier = (TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput");
            Button apply = (Button) LoadedWorkbench.node(loader, "applySettingsEntryButton");

            profiles.setValue(SettingsFeature.Profile.STANDARD);
            profiles.fireEvent(new ActionEvent());
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            small.setText("0.25");
            multiplier.setText("2");
            apply.fire();

            profiles.setValue(SettingsFeature.Profile.UUNP);
            profiles.fireEvent(new ActionEvent());
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Arms")).findFirst().orElseThrow());
            multiplier.setText("3");
            apply.fire();
            ((Button) LoadedWorkbench.node(loader, "saveSettingsButton")).fire();
            multiplier.setText("9");
            apply.fire();

            assertTrue(jobs.frame().active());
            assertEquals(1f, fixture().settings().getMultiplier("Waist"));
            assertEquals(1f, fixture().settings().getMultiplierUUNP("Arms"));
            assertEquals("3.0", multiplier.getText());
            assertTrue(((ListView<?>) loader.getNamespace().get("activityList")).getItems().isEmpty());
        });

        Thread settingsWorker = worker.runNextAsync();
        settingsWorker.join();
        assertEquals(2f, fixture().settings().getMultiplier("Waist"));
        assertEquals(3f, fixture().settings().getMultiplierUUNP("Arms"));

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            FXMLLoader loader = loaderReference.get();
            assertFalse(jobs.frame().active());
            assertTrue(jobs.frame().technicalDiagnostics().isEmpty());
            assertEquals(JobCoordinator.Lifecycle.COMPLETED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            assertEquals(25, flow.frame().snapshot().getSliderPresets().getFirst().getSliderChoices().stream()
                    .filter(choice -> choice.getName().equals("Waist")).findFirst().orElseThrow()
                    .getEffectiveSmallValue());
            assertEquals(1, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());
            assertTrue(((Label) loader.getNamespace().get("statusText")).getText().contains("Settings saved"));
            stageReference.get().close();
        });

        assertTrue(fixture().settings().reload().isSuccessful());
        assertEquals(2f, fixture().settings().getMultiplier("Waist"));
        assertEquals(3f, fixture().settings().getMultiplierUUNP("Arms"));
    }

    /**
     * The shell mounts the Settings Area's own panes into its landmark containers at the positions they held in the
     * single Workbench FXML, and shows them only while Settings is the active Area (#149).
     */
    @Test
    void settingsAreaPanesMountIntoTheShellLandmarksAtTheirFxmlPositions() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

            assertEquals(List.of("templatesPrimaryScroll", "morphsPrimaryScroll", "settingsPrimaryScroll",
                    "npcDatabasePrimaryScroll"), childIds(loader, "primaryPane"));
            assertEquals(List.of("templatesEditorContent", "morphsEditorContent", "settingsEditorContent",
                    "npcDatabaseEditorContent"), childIds(loader, "editorPane"));
            assertEquals(List.of("templatesInspectorScroll", "morphsInspectorScroll", "settingsInspectorContent",
                    "npcDatabaseInspectorScroll"), childIds(loader, "inspectorPane"));
            javafx.scene.Node settingsEditor = (javafx.scene.Node) LoadedWorkbench.node(loader, "settingsEditorContent");
            assertFalse(settingsEditor.isVisible() || settingsEditor.isManaged());

            ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();

            assertTrue(settingsEditor.isVisible() && settingsEditor.isManaged());
            assertFalse(((javafx.scene.Node) LoadedWorkbench.node(loader, "templatesEditorContent")).isVisible());
            stage.close();
        });
    }

    /**
     * The shell mounts the NPC Database Area's own panes last in its landmark containers, where they sat in the single
     * Workbench FXML, declares no {@code @FXML} field for any NPC Database control, and shows the panes only while the
     * NPC Database is the active Area (#151).
     */
    @Test
    void npcDatabaseAreaPanesMountIntoTheShellLandmarksAtTheirFxmlPositions() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

            assertEquals("npcDatabasePrimaryScroll", childIds(loader, "primaryPane").getLast());
            assertEquals("npcDatabaseEditorContent", childIds(loader, "editorPane").getLast());
            assertEquals("npcDatabaseInspectorScroll", childIds(loader, "inspectorPane").getLast());
            Map<String, Object> npcDatabase = controller.areaNamespace(WorkbenchNavigation.Area.NPC_DATABASE);
            assertTrue(npcDatabase.containsKey("npcCatalogTable"), "the NPC Database Area FXML must be loaded");
            for (java.lang.reflect.Field field : WorkbenchController.class.getDeclaredFields())
                assertFalse(field.isAnnotationPresent(javafx.fxml.FXML.class)
                                && npcDatabase.containsKey(field.getName()),
                        "the shell must not declare the NPC Database control " + field.getName());
            javafx.scene.Node editor = (javafx.scene.Node) LoadedWorkbench.node(loader, "npcDatabaseEditorContent");
            assertFalse(editor.isVisible() || editor.isManaged());

            ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();

            assertTrue(editor.isVisible() && editor.isManaged());
            assertFalse(((javafx.scene.Node) LoadedWorkbench.node(loader, "settingsEditorContent")).isVisible());
            stage.close();
        });
    }

    /**
     * The shell mounts the Morphs Area's own panes right after the Templates panes in its landmark containers, where
     * they sat in the single Workbench FXML, declares no {@code @FXML} field for any Morphs control, and shows the
     * panes only while Morphs is the active Area (#152).
     */
    @Test
    void morphsAreaPanesMountIntoTheShellLandmarksAtTheirFxmlPositions() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

            assertEquals("morphsPrimaryScroll", childIds(loader, "primaryPane").get(1));
            assertEquals("morphsEditorContent", childIds(loader, "editorPane").get(1));
            assertEquals("morphsInspectorScroll", childIds(loader, "inspectorPane").get(1));
            Map<String, Object> morphs = controller.areaNamespace(WorkbenchNavigation.Area.MORPHS);
            assertTrue(morphs.containsKey("customMorphTargetList"), "the Morphs Area FXML must be loaded");
            for (java.lang.reflect.Field field : WorkbenchController.class.getDeclaredFields())
                assertFalse(field.isAnnotationPresent(javafx.fxml.FXML.class) && morphs.containsKey(field.getName()),
                        "the shell must not declare the Morphs control " + field.getName());
            javafx.scene.Node editor = (javafx.scene.Node) LoadedWorkbench.node(loader, "morphsEditorContent");
            assertFalse(editor.isVisible() || editor.isManaged());
            // The shell hands the Area its window's render scale, which the Morphs catalog heights track.
            AtomicBoolean catalogResized = new AtomicBoolean();
            LoadedWorkbench.morphs(controller).customMorphTargetListNode().prefHeightProperty().addListener(
                    (javafx.beans.Observable observable) -> catalogResized.set(true));
            LoadedWorkbench.morphs(controller).customMorphTargetListNode().getPrefHeight();
            stage.setRenderScaleY(stage.getRenderScaleY() + 0.5);
            assertTrue(catalogResized.get(), "the Morphs catalog must re-evaluate its height on a render-scale change");

            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();

            assertTrue(editor.isVisible() && editor.isManaged());
            assertFalse(((javafx.scene.Node) LoadedWorkbench.node(loader, "templatesEditorContent")).isVisible());
            stage.close();
        });
    }

    /**
     * The shell mounts the Templates Area's own panes first in its landmark containers, where they sat in the single
     * Workbench FXML, declares no {@code @FXML} field for any Templates control, hands the Area its window's render
     * scale, and shows the panes only while Templates is the active Area (#153).
     */
    @Test
    void templatesAreaPanesMountIntoTheShellLandmarksAtTheirFxmlPositions() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

            assertEquals("templatesPrimaryScroll", childIds(loader, "primaryPane").getFirst());
            assertEquals("templatesEditorContent", childIds(loader, "editorPane").getFirst());
            assertEquals("templatesInspectorScroll", childIds(loader, "inspectorPane").getFirst());
            Map<String, Object> templates = controller.areaNamespace(WorkbenchNavigation.Area.TEMPLATES);
            assertTrue(templates.containsKey("sliderPresetList"), "the Templates Area FXML must be loaded");
            for (java.lang.reflect.Field field : WorkbenchController.class.getDeclaredFields())
                assertFalse(field.isAnnotationPresent(javafx.fxml.FXML.class) && templates.containsKey(field.getName()),
                        "the shell must not declare the Templates control " + field.getName());
            javafx.scene.Node editor = (javafx.scene.Node) LoadedWorkbench.node(loader, "templatesEditorContent");
            assertTrue(editor.isVisible() && editor.isManaged(), "Templates is the first active Area");
            // The shell hands the Area its window's render scale, which the Slider Preset catalog height tracks.
            AtomicBoolean catalogResized = new AtomicBoolean();
            LoadedWorkbench.templates(controller).sliderPresetListNode().prefHeightProperty().addListener(
                    (javafx.beans.Observable observable) -> catalogResized.set(true));
            LoadedWorkbench.templates(controller).sliderPresetListNode().getPrefHeight();
            stage.setRenderScaleY(stage.getRenderScaleY() + 0.5);
            assertTrue(catalogResized.get(),
                    "the Templates catalog must re-evaluate its height on a render-scale change");

            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();

            assertFalse(editor.isVisible() || editor.isManaged());
            ((ToggleButton) loader.getNamespace().get("templatesAreaButton")).fire();
            assertTrue(editor.isVisible() && editor.isManaged());
            stage.close();
        });
    }

    /**
     * With every Area and the Output drawer extracted, the shell holds only its chrome: every control it keeps a field
     * for is an injected {@code workbench.fxml} control, no module FXML declares one of them, and each of the four
     * Areas and the drawer is loaded from its own FXML with its own controller (#154).
     */
    @Test
    void shellHoldsOnlyItsChromeControlsAndEveryModuleLoadsFromItsOwnFxml() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

            assertEquals(List.of("TemplatesArea", "MorphsArea", "NpcDatabaseArea", "SettingsArea"),
                    LoadedWorkbench.areas(loader).stream().map(area -> area.getClass().getSimpleName()).toList());
            assertNotNull(LoadedWorkbench.outputDrawer(loader), "the Output drawer FXML must be loaded");
            Map<String, Object> modules = new java.util.HashMap<>();
            for (WorkbenchNavigation.Area area : WorkbenchNavigation.Area.values())
                modules.putAll(controller.areaNamespace(area));
            modules.putAll(controller.outputDrawerNamespace());
            for (java.lang.reflect.Field field : WorkbenchController.class.getDeclaredFields()) {
                boolean control = javafx.scene.Node.class.isAssignableFrom(field.getType())
                        || javafx.scene.control.MenuItem.class.isAssignableFrom(field.getType());
                if (!control)
                    continue;
                assertTrue(field.isAnnotationPresent(javafx.fxml.FXML.class),
                        "the shell holds no control it did not load: " + field.getName());
                assertTrue(loader.getNamespace().containsKey(field.getName()),
                        "the shell's control " + field.getName() + " must be declared in workbench.fxml");
                assertFalse(modules.containsKey(field.getName()),
                        "the shell must not declare the module control " + field.getName());
            }
            stage.close();
        });
    }

    /**
     * When a semantic target's node cannot take focus, the shell falls back to the target Area's own default node,
     * which the Area may override (the NPC Database's is its catalog table, not its primary source list) (#154).
     */
    @Test
    void focusFallbackLandsOnEachAreasOwnDefaultNode() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();
            Map<WorkbenchNavigation.Area, String> railButtons = Map.of(
                    WorkbenchNavigation.Area.TEMPLATES, "templatesAreaButton",
                    WorkbenchNavigation.Area.MORPHS, "morphsAreaButton",
                    WorkbenchNavigation.Area.NPC_DATABASE, "npcDatabaseAreaButton",
                    WorkbenchNavigation.Area.SETTINGS, "settingsAreaButton");
            List<WorkbenchArea> areas = LoadedWorkbench.areas(loader);
            for (WorkbenchNavigation.Area area : WorkbenchNavigation.Area.values()) {
                WorkbenchArea module = areas.get(area.ordinal());
                ToggleButton railButton = (ToggleButton) loader.getNamespace().get(railButtons.get(area));
                railButton.fire();
                javafx.scene.Node inspector = module.focusNode(WorkbenchNavigation.Landmark.INSPECTOR);
                inspector.setVisible(false);
                railButton.requestFocus();

                controller.focusPolicy().request(
                        new WorkbenchNavigation.FocusTarget(area, WorkbenchNavigation.Landmark.INSPECTOR));

                assertSame(module.defaultFocusNode(), stage.getScene().getFocusOwner(), area.displayName());
                inspector.setVisible(true);
            }
            assertSame(LoadedWorkbench.node(loader, "npcCatalogTable"),
                    areas.get(WorkbenchNavigation.Area.NPC_DATABASE.ordinal()).defaultFocusNode());
            stage.close();
        });
    }

    /**
     * The shell's diagnostics arbitration lasts only for the change that owns it. An NPC Database promotion's
     * diagnostics stay out of the Templates and Morphs projections of the promoted Project frame. Neither that
     * promotion nor a later Templates edit leaves an owner behind, so a malformed Open after them, which no Area owns,
     * shows its diagnostics in both projections (#154).
     */
    @Test
    void diagnosticsArbitrationLastsOnlyForTheChangeThatOwnsIt() throws Exception {
        Path source = temporaryDirectory.resolve("promotion-with-rejection.txt");
        Files.writeString(source, "Skyrim.esm | Good | Good01 | NordRace | 00000B\n"
                + "Skyrim.esm | Invalid | Invalid01 | NordRace | XYZ\n");
        Path malformed = temporaryDirectory.resolve("malformed-project.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "malformed-syntax.jbs2bg"), malformed);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();

                ((Button) LoadedWorkbench.node(loader, "addAllNpcsToProjectButton")).fire();

                assertFalse(flow.frame().diagnostics().isEmpty(), "the promoted Project frame carries diagnostics");
                assertFalse(((HBox) LoadedWorkbench.node(loader, "templatesInfoBar")).isVisible());
                assertFalse(((HBox) LoadedWorkbench.node(loader, "morphsInfoBar")).isVisible());
                ((ToggleButton) loader.getNamespace().get("templatesAreaButton")).fire();
                ((TextField) LoadedWorkbench.node(loader, "sliderPresetNameInput")).setText("Alpha");
                ((Button) LoadedWorkbench.node(loader, "createSliderPresetButton")).fire();
                assertEquals(1, flow.frame().snapshot().getSliderPresets().size(), "the Templates edit publishes");

                // The edits left the Project dirty, so Open first asks to discard it.
                platform.respondWith(WorkbenchProjectFlow.Response.discard());
                platform.respondWith(WorkbenchProjectFlow.Response.selected(malformed));
                ((MenuItem) loader.getNamespace().get("openProjectMenuItem")).fire();

                assertTrue(((TextArea) loader.getNamespace().get("diagnosticsText")).getText()
                        .contains("PROJECT_JSON_MALFORMED"));
                assertTrue(((HBox) LoadedWorkbench.node(loader, "templatesInfoBar")).isVisible());
                assertTrue(((HBox) LoadedWorkbench.node(loader, "morphsInfoBar")).isVisible());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * The Templates Area's own keys reach it through the shell's key map before navigation: in narrow mode, Escape in
     * an inline rename inside the primary overlay cancels the rename and leaves the overlay open, and only the next
     * Escape dismisses the overlay; Ctrl+K and the final Escape tier go through the Area's search and Escape (#153).
     */
    @Test
    void templatesRenameEscapeWinsOverTheNarrowOverlayAndSelectionClearComesLast() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();
            root.resize(1199, 720);
            StackPane overlay = (StackPane) loader.getNamespace().get("overlayLayer");
            VBox primaryPane = (VBox) loader.getNamespace().get("primaryPane");

            sendControlKey(root, KeyCode.K);
            assertEquals(List.of(primaryPane), overlay.getChildren(), "Ctrl+K opens the narrow primary content");
            assertSame(LoadedWorkbench.node(loader, "sliderPresetFilter"), scene.getFocusOwner());
            TemplatesArea templates = LoadedWorkbench.templates(controller);
            templates.sliderPresetListNode().getSelectionModel().select(0);
            templates.sliderPresetListNode().requestFocus();
            sendKey(root, KeyCode.F2);
            root.applyCss();
            root.layout();
            assertSame(templates.activeRenameField().orElseThrow(), scene.getFocusOwner());

            sendKey(root, KeyCode.ESCAPE);

            assertEquals(-1, templates.sliderPresetListNode().getEditingIndex(), "the rename Escape ends the edit");
            assertTrue(templates.activeRenameField().isEmpty());
            assertEquals(List.of(primaryPane), overlay.getChildren(), "the rename Escape leaves the overlay open");
            sendKey(root, KeyCode.ESCAPE);
            assertTrue(overlay.getChildren().isEmpty());
            assertNotNull(templates.sliderPresetListNode().getSelectionModel().getSelectedItem(),
                    "dismissing the overlay keeps the selection");
            sendKey(root, KeyCode.ESCAPE);
            assertNull(templates.sliderPresetListNode().getSelectionModel().getSelectedItem());
            stage.close();
        });
    }

    /**
     * The shell keeps the Project diagnostics of a Templates edit with Templates: a rejected Slider Preset create
     * publishes a Project frame whose diagnostic shows in the Templates InfoBar, while the Morphs projection of the
     * same frame stays clear (#153).
     */
    @Test
    void templatesValidationDiagnosticsStayOutOfTheMorphsProjection() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            long before = flow.frame().sequence();

            ((TextField) LoadedWorkbench.node(loader, "sliderPresetNameInput")).setText("alpha");
            ((Button) LoadedWorkbench.node(loader, "createSliderPresetButton")).fire();

            assertTrue(flow.frame().sequence() > before, "the rejected create publishes a Project frame");
            assertFalse(flow.frame().diagnostics().isEmpty());
            assertTrue(((HBox) LoadedWorkbench.node(loader, "templatesInfoBar")).isVisible());
            assertFalse(((HBox) LoadedWorkbench.node(loader, "morphsInfoBar")).isVisible());
            stage.close();
        });
    }

    /**
     * The shell places the Output drawer's two parts where they sat in the single Workbench FXML (Generate in the Area
     * header before the Project status, the drawer last in the content stack), declares no {@code @FXML} field for any
     * drawer control, and shows the drawer only while navigation reveals it (#150).
     */
    @Test
    void outputDrawerPartsMountIntoTheShellAtTheirFxmlPositions() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

            assertEquals(Arrays.asList("areaTitle", "showPrimaryOverlayButton", "showInspectorOverlayButton", null,
                    "generateOutputButton", "projectStatusText"), childIds(loader, "areaHeader"));
            assertEquals(List.of("areaSurface", "outputDrawer"), childIds(loader, "contentStack"));
            for (java.lang.reflect.Field field : WorkbenchController.class.getDeclaredFields())
                assertFalse(field.isAnnotationPresent(javafx.fxml.FXML.class)
                                && controller.outputDrawerNamespace().containsKey(field.getName()),
                        "the shell must not declare the drawer control " + field.getName());
            VBox drawer = (VBox) LoadedWorkbench.node(loader, "outputDrawer");
            assertFalse(drawer.isVisible() || drawer.isManaged());

            ((ToggleButton) loader.getNamespace().get("outputAreaButton")).fire();

            assertTrue(drawer.isVisible() && drawer.isManaged());
            stage.close();
        });
    }

    /**
     * The shell's Ctrl+G and Ctrl+E reach the Output drawer: a successful Generate reveals the drawer through
     * navigation, and neither the reveal nor the export moves focus out of the active Area (#150).
     */
    @Test
    void outputShortcutsGenerateAndExportThroughTheDrawerWithoutMovingAreaFocus() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-31T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // This small generated Project settles before prolonged cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Shortcut UI"));
        RecordingPlatform platform = new RecordingPlatform();
        Path exportDirectory = Files.createDirectory(temporaryDirectory.resolve("shortcut-output"));
        platform.respondOutputDirectoryWith(Optional.of(exportDirectory));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, platform, fixture().environment());
            stage.show();
            Label editor = (Label) LoadedWorkbench.node(loader, "templateEditorFocusTarget");
            editor.requestFocus();

            sendControlKey(root, KeyCode.G);
            assertTrue(jobs.frame().active());
            worker.runNext();

            assertTrue(((VBox) LoadedWorkbench.node(loader, "outputDrawer")).isVisible());
            assertTrue(((ToggleButton) loader.getNamespace().get("outputAreaButton")).isSelected());
            TextArea templates = (TextArea) LoadedWorkbench.node(loader, "templatesOutputText");
            assertTrue(templates.getText().startsWith("Shortcut UI="));
            assertSame(editor, scene.getFocusOwner());

            sendControlKey(root, KeyCode.E);
            assertTrue(jobs.frame().active());
            worker.runNext();
            assertArrayEquals(templates.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    Files.readAllBytes(exportDirectory.resolve("templates.ini")));
            assertSame(editor, scene.getFocusOwner());
            stage.close();
        });
    }

    /** @return the {@code fx:id}s of one shell container's children, in order */
    private static List<String> childIds(FXMLLoader loader, String containerId) {
        return ((javafx.scene.layout.Pane) loader.getNamespace().get(containerId)).getChildren().stream()
                .map(javafx.scene.Node::getId).toList();
    }

    /** Reload returns immediately on JavaFX and applies the recovered pair only after worker and publication lanes. */
    @Test
    void settingsReloadRunsOnTheApplicationWorkerBeforePublishingItsFrame() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The deterministic Reload settles before prolonged cancellation is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Reload Refresh"));
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
        });
        Files.writeString(temporaryDirectory.resolve("settings.json"),
                "{\"Defaults\":{\"Waist\":{\"valueSmall\":0.5,\"valueBig\":1}},"
                        + "\"Multipliers\":{\"Waist\":4},\"Inverted\":[]}");
        Files.writeString(temporaryDirectory.resolve("settings_UUNP.json"),
                "{\"Defaults\":{},\"Multipliers\":{},\"Inverted\":[]}");

        FxTestToolkit.runOnFxThread(() -> {
            ((Button) LoadedWorkbench.node(loaderReference.get(), "reloadSettingsButton")).fire();

            assertTrue(jobs.frame().active());
            assertEquals(1f, fixture().settings().getMultiplier("Waist"));
        });

        Thread settingsWorker = worker.runNextAsync();
        settingsWorker.join();
        assertEquals(4f, fixture().settings().getMultiplier("Waist"));

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            FXMLLoader loader = loaderReference.get();
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            assertEquals(4f, entries.getItems().stream().filter(entry -> entry.name().equals("Waist"))
                    .findFirst().orElseThrow().multiplier().orElseThrow());
            assertEquals(50, flow.frame().snapshot().getSliderPresets().getFirst().getSliderChoices().stream()
                    .filter(choice -> choice.getName().equals("Waist")).findFirst().orElseThrow()
                    .getEffectiveSmallValue());
            assertEquals(1, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());
            assertTrue(((Label) loader.getNamespace().get("statusText")).getText().contains("Settings reloaded"));
            stageReference.get().close();
        });
    }

    /** A shutdown request racing Save completion cancels the chained Reload without a callback failure. */
    @Test
    void shutdownRaceCancelsChainedSettingsReloadWithoutCallbackFailure() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The Save reaches commit before shutdown races its chained Reload admission.
                }, callbackFailure::set);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();
            ((Button) LoadedWorkbench.node(loader, "reloadSettingsButton")).fire();

            assertTrue(jobs.frame().active());
            assertEquals("Save Settings", jobs.frame().attempt().orElseThrow().operation().name());
            publication.runNext();
        });

        Thread saveWorker = worker.runNextAsync();
        saveWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            ((MenuItem) loaderReference.get().getNamespace().get("exitMenuItem")).fire();
            assertTrue(jobs.frame().active());
            assertTrue(jobs.frame().shutdownRequested());

            publication.runNext();

            assertNull(callbackFailure.get());
            assertTrue(jobs.frame().technicalDiagnostics().isEmpty());
            assertFalse(jobs.frame().active());
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stageReference.get().setOnCloseRequest(null);
            stageReference.get().close();
        });
    }

    /**
     * The Templates import launcher captures a multiple-file chooser response, falls back to the logical Slider
     * Preset list while admission disables the launcher, and completion updates Project and Activity without moving
     * focus.
     */
    @Test
    void bodySlideImportLauncherUsesTheCentralJobWithoutStealingFocus() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        Path source = temporaryDirectory.resolve("workbench-import.xml").toAbsolutePath().normalize();
        Files.writeString(source, "<SliderPresets><Preset name=\"Workbench Import\"/></SliderPresets>");
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-31T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // This import settles before prolonged cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selectedSources(List.of(source)));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, platform, fixture().environment());
            stage.show();
            Button importButton = (Button) LoadedWorkbench.node(loader, "importBodySlideButton");
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> presets =
                    (ListView<SliderPresetSnapshot>) LoadedWorkbench.node(loader, "sliderPresetList");
            importButton.requestFocus();

            importButton.fire();

            assertTrue(jobs.frame().active());
            assertTrue(importButton.isDisabled());
            assertSame(presets, scene.getFocusOwner());
            worker.runNext();

            ListView<SliderPresetSnapshot> importedPresets =
                    LoadedWorkbench.templates(controller).sliderPresetListNode();
            assertEquals(List.of("Workbench Import"), importedPresets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertEquals(1, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());
            assertSame(importedPresets, scene.getFocusOwner());
            stage.close();
        });
    }

    /** Startup Settings failure stays visible and blocks import and generation from consuming fallback values. */
    @Test
    void invalidStartupSettingsAreVisibleAndBlockBodySlideImport() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        Files.writeString(temporaryDirectory.resolve("settings.json"), "{\"Defaults\":");
        Settings.InitializationResult rejected = fixture().settings().reload();
        assertFalse(rejected.isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().withStartup(rejected).environment());

            assertTrue(((Button) LoadedWorkbench.node(loader, "importBodySlideButton")).isDisabled());
            assertTrue(((Button) LoadedWorkbench.node(loader, "generateOutputButton")).isDisabled());
            @SuppressWarnings("unchecked")
            ListView<WorkbenchFeedback.ActivityRecord> activity =
                    (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
            assertEquals(1, activity.getItems().size());
            assertTrue(activity.getItems().getFirst().message().contains("SETTINGS_JSON_MALFORMED"));
            sendControlKey(root, KeyCode.G);
            assertEquals(1, activity.getItems().size());
            stage.close();
        });
    }

    /**
     * The controls that several Areas used to race on (BodySlide import, the NPC Database source buttons and
     * Generate) each have one writer fed by the shared Workbench context, so a job frame followed by a Project frame
     * leaves them exactly as the reverse order does, with live or rejected Settings.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void crossAreaControlsSettleTheSameWhetherTheJobFrameOrTheProjectFrameComesLast(boolean liveSettings)
            throws Exception {
        Path source = temporaryDirectory.resolve("ordered-npcs.txt");
        Files.writeString(source, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n");
        WorkbenchTestEnvironment environment = fixture();
        if (!liveSettings) {
            Files.writeString(temporaryDirectory.resolve("settings.json"), "{\"Defaults\":");
            Settings.InitializationResult rejected = fixture().settings().reload();
            assertFalse(rejected.isSuccessful());
            environment = fixture().withStartup(rejected);
        }
        // Imported sources are loaded but none is selected, so only Remove stays disabled once the import settles;
        // rejected Settings keep BodySlide import and Generate from consuming fallback values.
        Map<String, Boolean> expected = Map.of(
                "importNpcSourcesButton", false,
                "removeNpcSourceButton", true,
                "clearNpcDatabaseButton", false,
                "importBodySlideButton", !liveSettings,
                "generateOutputButton", !liveSettings);

        assertEquals(expected, crossAreaControlsAfter(true, source, environment));
        assertEquals(expected, crossAreaControlsAfter(false, source, environment));
    }

    /**
     * Attaches a fresh Workbench, then settles an NPC Database import (its job frames) and a Slider Preset creation
     * (a Project frame) in the requested order.
     *
     * @param jobFrameFirst true to import before creating, so the Project frame renders last
     * @return the disabled state of each cross-Area control, keyed by FXML id
     */
    private Map<String, Boolean> crossAreaControlsAfter(boolean jobFrameFirst, Path source,
                                                         WorkbenchTestEnvironment environment) throws Exception {
        ProjectSession session = environment.newSession();
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", session);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        AtomicReference<Map<String, Boolean>> disabled = new AtomicReference<>();
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform, environment.environment());
                Runnable importSources = () -> ((Button) LoadedWorkbench.node(loader, "importNpcSourcesButton")).fire();
                Runnable createPreset = () -> {
                    ((TextField) LoadedWorkbench.node(loader, "sliderPresetNameInput")).setText("Ordered");
                    ((Button) LoadedWorkbench.node(loader, "createSliderPresetButton")).fire();
                };
                (jobFrameFirst ? importSources : createPreset).run();
                (jobFrameFirst ? createPreset : importSources).run();

                assertEquals(1, session.getSnapshot().getSliderPresets().size());
                assertEquals(1, ((TableView<?>) LoadedWorkbench.node(loader, "npcCatalogTable")).getItems().size());
                disabled.set(Map.of(
                        "importNpcSourcesButton", isDisabled(loader, "importNpcSourcesButton"),
                        "removeNpcSourceButton", isDisabled(loader, "removeNpcSourceButton"),
                        "clearNpcDatabaseButton", isDisabled(loader, "clearNpcDatabaseButton"),
                        "importBodySlideButton", isDisabled(loader, "importBodySlideButton"),
                        "generateOutputButton", isDisabled(loader, "generateOutputButton")));
            } finally {
                stage.close();
            }
        });
        return disabled.get();
    }

    /** @return whether the named FXML button, from the shell or any module graph, is disabled */
    private static boolean isDisabled(FXMLLoader loader, String id) {
        return ((Button) LoadedWorkbench.node(loader, id)).isDisabled();
    }

    /**
     * Inspector gang controls apply one atomic enabled-row edit, remain mutually exclusive, lock row editors while
     * active, and retain focus on the initiating bulk action.
     */
    @Test
    void templatesGangControlsAreAtomicExclusiveAndKeyboardReachable() throws Exception {
        fixture = WorkbenchTestEnvironment.reloaded(SettingsTestSupport.installDefaults(temporaryDirectory,
                Map.of("Arms", new DefaultSliderValue(0.1f, 0.9f),
                        "Waist", new DefaultSliderValue(0.2f, 0.8f)),
                Map.of()));
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300.0, 800.0);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> presets =
                    (ListView<SliderPresetSnapshot>) LoadedWorkbench.node(loader, "sliderPresetList");
            presets.getSelectionModel().select(0);
            VBox rows = (VBox) LoadedWorkbench.node(loader, "sliderChoiceRows");
            Button fiftyAll = (Button) LoadedWorkbench.node(loader, "fiftyAllSliderChoicesButton");
            CheckBox minimumGang = (CheckBox) LoadedWorkbench.node(loader, "gangMinimumCheck");
            CheckBox maximumGang = (CheckBox) LoadedWorkbench.node(loader, "gangMaximumCheck");

            fiftyAll.requestFocus();
            fiftyAll.fire();

            assertSame(fiftyAll, scene.getFocusOwner());
            assertEquals(List.of(50, 50), flow.frame().snapshot().getSliderPresets().getFirst()
                    .getSliderChoices().stream().map(choice -> choice.getPercentageMinimum()).toList());
            assertEquals(List.of(50, 50), flow.frame().snapshot().getSliderPresets().getFirst()
                    .getSliderChoices().stream().map(choice -> choice.getPercentageMaximum()).toList());
            assertEquals(1, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());

            minimumGang.fire();
            assertTrue(minimumGang.isSelected());
            assertTrue(((SliderChoiceRow) rows.getChildren().getFirst()).enabledControl().isDisabled());
            maximumGang.fire();

            assertFalse(minimumGang.isSelected());
            assertTrue(maximumGang.isSelected());
            assertTrue(((SliderChoiceRow) rows.getChildren().getFirst()).minimumControl().isDisabled());
            maximumGang.fire();
            assertFalse(maximumGang.isSelected());
            assertFalse(((SliderChoiceRow) rows.getChildren().getFirst()).enabledControl().isDisabled());
            assertEquals("Gang all minimum Slider choice values", minimumGang.getAccessibleText());
            minimumGang.requestFocus();
            sendKey(root, KeyCode.F6);
            assertSame(loader.getNamespace().get("activityList"), scene.getFocusOwner());
            stage.close();
        });
    }

    /**
     * Delivers one Control accelerator to the Workbench root through the same key-event seam as JavaFX.
     */
    private static void sendControlKey(Parent root, KeyCode code) {
        root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, true, false, false));
    }

    /**
     * Delivers one unmodified navigation key to the Workbench root.
     */
    private static void sendKey(Parent root, KeyCode code) {
        root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
    }

    /** Opens a named NPC column checklist, toggles one exact value with Space, and applies it with Enter. */
    private static void hideNpcColumnValue(FXMLLoader loader, String column, String value) {
        String buttonId = switch (column) {
            case "Name" -> "npcNameFilterButton";
            case "Race" -> "npcRaceFilterButton";
            default -> throw new IllegalArgumentException("Unsupported test column: " + column);
        };
        ToggleButton launcher = (ToggleButton) LoadedWorkbench.node(loader, buttonId);
        launcher.fire();
        Popup popup = FxTestToolkit.showingPopup(launcher.getScene().getWindow(), "#npcColumnFilterChoices");
        assertTrue(launcher.getAccessibleHelp().contains("expanded"));
        ListView<?> choices = (ListView<?>) popup.getScene().getRoot().lookup("#npcColumnFilterChoices");
        int index = java.util.stream.IntStream.range(0, choices.getItems().size())
                .filter(candidate -> choices.getItems().get(candidate).toString().equals(value))
                .findFirst().orElseThrow();
        choices.getSelectionModel().select(index);
        sendKey(choices, KeyCode.SPACE);
        sendKey(choices, KeyCode.ENTER);
        assertFalse(popup.isShowing());
    }

    /**
     * Open consumes a platform-selected path and renders recovery state and diagnostics from the returned frame.
     */
    @Test
    void openCommandRendersTheAuthoritativeRecoveredFrame() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform, fixture().environment());

            ((MenuItem) loader.getNamespace().get("openProjectMenuItem")).fire();

            assertEquals(ProjectLifecycleStatus.RECOVERED, flow.frame().snapshot().getLifecycleStatus());
            assertTrue(flow.frame().snapshot().isDirty());
            assertEquals("BS2BG Preview - *recovery-source.jbs2bg", stage.getTitle());
            String diagnostics = ((TextArea) loader.getNamespace().get("diagnosticsText")).getText();
            assertTrue(diagnostics.contains("SLIDER_PRESET_ASSIGNMENT_MISSING"));
            assertTrue(diagnostics.contains("Missing Target"));
            assertTrue(diagnostics.contains("Missing NPC"));
            HBox infoBar = (HBox) loader.getNamespace().get("infoBar");
            assertTrue(infoBar.isVisible());
            assertEquals(AccessibleRole.PARENT, infoBar.getAccessibleRole());
            assertEquals("Workbench notification", infoBar.getAccessibleText());
            assertEquals("Warning: Project opened with 2 diagnostics.", infoBar.getAccessibleHelp());
            @SuppressWarnings("unchecked")
            ListView<WorkbenchFeedback.ActivityRecord> activity =
                    (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
            assertEquals(1, activity.getItems().size());
            assertEquals("Open Project", activity.getItems().getFirst().operation());
            assertEquals(WorkbenchFeedback.Severity.WARNING, activity.getItems().getFirst().severity());
            assertTrue(activity.getItems().getFirst().occurredAt().isAfter(java.time.Instant.EPOCH));
            assertEquals("Warning — Completed with issues — Project opened with 2 diagnostics.",
                    ((Label) loader.getNamespace().get("statusText")).getText());
            stage.close();
        });
    }

    /**
     * Exit cancellation keeps the dirty window alive; a later Discard closes it exactly once.
     */
    @Test
    void dirtyExitCanBeCancelledBeforeDiscardClosesTheWindow() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));
        platform.respondWith(WorkbenchProjectFlow.Response.cancelled());
        platform.respondWith(WorkbenchProjectFlow.Response.discard());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform, fixture().environment());
            MenuItem open = (MenuItem) loader.getNamespace().get("openProjectMenuItem");
            MenuItem exit = (MenuItem) loader.getNamespace().get("exitMenuItem");
            open.fire();

            exit.fire();
            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);

            exit.fire();
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stage.close();
        });
    }

    /** Cancelling a platform chooser records a truthful terminal outcome and restores the semantic launcher focus. */
    @Test
    void cancelledProjectChooserPublishesFeedbackAndRestoresFocus() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.cancelled());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            ToggleButton launcher = (ToggleButton) loader.getNamespace().get("templatesAreaButton");
            stage.show();
            launcher.requestFocus();
            javafx.scene.Node focusBeforeChooser = stage.getScene().getFocusOwner();
            assertNotNull(focusBeforeChooser);

            ((MenuItem) loader.getNamespace().get("openProjectMenuItem")).fire();

            @SuppressWarnings("unchecked")
            ListView<WorkbenchFeedback.ActivityRecord> activity =
                    (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
            WorkbenchFeedback.ActivityRecord cancelled = activity.getItems().getLast();
            assertEquals("Open Project", cancelled.operation());
            assertEquals(WorkbenchFeedback.Disposition.CANCELLED, cancelled.disposition());
            assertTrue(((Label) loader.getNamespace().get("statusText")).getText().contains("Cancelled"));
            assertSame(focusBeforeChooser, stage.getScene().getFocusOwner());
            stage.close();
        });
    }

    /** Window close and File Exit confirm dirty Settings before allowing the ordinary clean-Project close. */
    @Test
    void dirtySettingsCloseCanBeCancelledBeforeDiscardClosesTheWindow() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CANCEL);
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.DISCARD);

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();

            WindowEvent closeRequest = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(closeRequest);

            assertTrue(closeRequest.isConsumed());
            assertEquals(0, platform.closeCount);
            assertFalse(((Button) LoadedWorkbench.node(loader, "saveSettingsButton")).isDisabled());
            ListView<?> activity = (ListView<?>) loader.getNamespace().get("activityList");
            assertEquals(WorkbenchFeedback.Disposition.CANCELLED,
                    assertInstanceOf(WorkbenchFeedback.ActivityRecord.class,
                            activity.getItems().getLast()).disposition());

            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();

            assertEquals(1, platform.closeCount);
            assertTrue(flow.frame().closed());
            stage.close();
        });
    }

    /** Saving dirty Settings on close completes asynchronously before the dirty Project receives its own decision. */
    @Test
    void dirtySettingsSaveCompletesBeforeDirtyProjectCloseConfirmation() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The deterministic close-save settles before prolonged cancellation is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Dirty Project"));
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);
        platform.respondWith(WorkbenchProjectFlow.Response.discard());
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            publication.runNext();
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();

            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();

            assertTrue(jobs.frame().active());
            assertEquals("Save Settings", jobs.frame().attempt().orElseThrow().operation().name());
            assertTrue(flow.frame().snapshot().isDirty());
            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);
        });

        Thread settingsWorker = worker.runNextAsync();
        settingsWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();

            assertEquals(2f, fixture().settings().getMultiplier("Waist"));
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stageReference.get().close();
        });
    }

    /** An inline coordinator observes the close continuation before Save completion and closes exactly once. */
    @Test
    void dirtySettingsCloseSaveSurvivesInlineWorkerAndPublicationCompletion() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        JobCoordinator jobs = new JobCoordinator(new InlineExecutorService(), Runnable::run,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Inline completion leaves no interval for prolonged cancellation feedback.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();

            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();

            assertEquals(2f, fixture().settings().getMultiplier("Waist"));
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stage.close();
        });
    }

    /** A failed Settings close-save retains both the dirty draft and the Workbench window. */
    @Test
    void failedDirtySettingsCloseSaveKeepsTheDraftAndWindowOpen() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The deterministic failed save settles before prolonged cancellation is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();
            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();
        });
        Files.deleteIfExists(temporaryDirectory.resolve("settings.json"));
        Files.deleteIfExists(temporaryDirectory.resolve("settings_UUNP.json"));
        Files.deleteIfExists(temporaryDirectory.resolve(".bs2bg-settings.lock"));
        Files.delete(temporaryDirectory);

        Thread settingsWorker = worker.runNextAsync();
        settingsWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            FXMLLoader loader = loaderReference.get();

            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);
            assertFalse(((Button) LoadedWorkbench.node(loader, "saveSettingsButton")).isDisabled());
            assertEquals(JobCoordinator.Lifecycle.FAILED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            stageReference.get().setOnCloseRequest(null);
            stageReference.get().close();
        });
    }

    /** A close-save rejected during the shutdown/render gap cannot arm a later ordinary Settings Save to close. */
    @Test
    void rejectedSettingsAdmissionDoesNotLeaveAPhantomCloseContinuation() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // No admitted operation waits long enough for prolonged cancellation feedback.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();
            assertEquals(JobCoordinator.ShutdownResponse.READY, jobs.requestShutdown());

            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();

            assertFalse(jobs.frame().active());
            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);
            assertTrue(jobs.resumeAfterShutdown());
            publication.runNext();
            publication.runNext();
            ((Button) LoadedWorkbench.node(loader, "saveSettingsButton")).fire();
            assertTrue(jobs.frame().active());
        });

        Thread settingsWorker = worker.runNextAsync();
        settingsWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();

            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);
            stageReference.get().setOnCloseRequest(null);
            stageReference.get().close();
        });
    }

    /** A failed close-save Retry recaptures its token, restores semantic focus, and closes only after retry success. */
    @Test
    void failedDirtySettingsCloseSaveRetryPreservesTheCloseIntentAndFocus() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Both deterministic attempts settle before prolonged cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);
        platform.respondFailureWith(WorkbenchFeedback.DialogAction.RETRY,
                () -> assertDoesNotThrow(() -> Files.createDirectories(temporaryDirectory)));
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();
        AtomicReference<javafx.scene.Node> returnFocus = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            stage.show();
            ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();
            TextField editor = (TextField) LoadedWorkbench.node(loader, "settingsEntryNameInput");
            editor.requestFocus();
            assertSame(editor, stage.getScene().getFocusOwner());
            returnFocus.set(editor);
            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();
        });
        Files.deleteIfExists(temporaryDirectory.resolve("settings.json"));
        Files.deleteIfExists(temporaryDirectory.resolve("settings_UUNP.json"));
        Files.deleteIfExists(temporaryDirectory.resolve(".bs2bg-settings.lock"));
        Files.delete(temporaryDirectory);

        Thread firstAttempt = worker.runNextAsync();
        firstAttempt.join();
        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();

            assertTrue(jobs.frame().active());
            assertEquals(0, platform.closeCount);
            assertSame(returnFocus.get(), stageReference.get().getScene().getFocusOwner());
        });

        Thread retryAttempt = worker.runNextAsync();
        retryAttempt.join();
        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();

            assertEquals(2f, fixture().settings().getMultiplier("Waist"));
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stageReference.get().close();
        });
    }

    /** A failed Save retry becomes ordinarily unavailable after the draft returns clean, without callback failure. */
    @Test
    void cleanSettingsDraftMakesFailedSaveRetryUnavailableWithoutTechnicalFailure() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The deterministic failed save settles before prolonged cancellation is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();
            ((Button) LoadedWorkbench.node(loader, "saveSettingsButton")).fire();
        });
        Files.deleteIfExists(temporaryDirectory.resolve("settings.json"));
        Files.deleteIfExists(temporaryDirectory.resolve("settings_UUNP.json"));
        Files.deleteIfExists(temporaryDirectory.resolve(".bs2bg-settings.lock"));
        Files.delete(temporaryDirectory);
        Thread failedAttempt = worker.runNextAsync();
        failedAttempt.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            FXMLLoader loader = loaderReference.get();
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).clear();
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();
            assertTrue(((Button) LoadedWorkbench.node(loader, "saveSettingsButton")).isDisabled());
            @SuppressWarnings("unchecked")
            ListView<WorkbenchFeedback.ActivityRecord> activity =
                    (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
            activity.getSelectionModel().selectLast();
            Button retry = (Button) loader.getNamespace().get("retryActivityButton");
            assertFalse(retry.isDisabled());

            retry.fire();

            assertTrue(((Label) loader.getNamespace().get("statusText")).getText()
                    .contains("Settings have no unsaved changes to save."));
            assertTrue(jobs.frame().technicalDiagnostics().isEmpty());
            stageReference.get().setOnCloseRequest(null);
            stageReference.get().close();
        });
    }

    /** Cancelling a queued Settings close-save retains the draft/window and restores its semantic return focus. */
    @Test
    void cancelledDirtySettingsCloseSaveRestoresFocusWithoutClosing() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Cancellation is accepted before the deterministic worker starts.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);
        AtomicReference<Stage> stageReference = new AtomicReference<>();
        AtomicReference<javafx.scene.Node> returnFocus = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, fixture().environment());
            publication.runNext();
            stageReference.set(stage);
            stage.show();
            ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) LoadedWorkbench.node(loader, "settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) LoadedWorkbench.node(loader, "settingsMultiplierInput")).setText("2");
            ((Button) LoadedWorkbench.node(loader, "applySettingsEntryButton")).fire();
            TextField editor = (TextField) LoadedWorkbench.node(loader, "settingsEntryNameInput");
            editor.requestFocus();
            assertSame(editor, stage.getScene().getFocusOwner());
            returnFocus.set(editor);
            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();
            assertEquals(JobCoordinator.CancelResponse.ACCEPTED, jobs.requestCancel());

            publication.runNext();
            publication.runNext();
            publication.runNext();

            assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);
            assertFalse(((Button) LoadedWorkbench.node(loader, "saveSettingsButton")).isDisabled());
            assertSame(returnFocus.get(), stage.getScene().getFocusOwner());
            stageReference.get().setOnCloseRequest(null);
            stageReference.get().close();
        });
    }

    /**
     * Exit with Save keeps the JavaFX window responsive and open until the application worker publishes a clean
     * Project, then consumes the final close effect exactly once.
     */
    @Test
    void dirtyExitWaitsForTheApplicationWorkerSaveBeforeClosingTheWindow() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // This save settles before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        Path target = temporaryDirectory.resolve("close-after-save.jbs2bg").toAbsolutePath().normalize();
        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selected(target));
        worker.runNext();
        flow.apply(SliderPresetEdits.create("Unsaved at close"));
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.save());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform, fixture().environment());

            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();

            assertTrue(jobs.frame().active());
            assertTrue(flow.frame().snapshot().isDirty());
            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);

            worker.runNext();

            assertFalse(flow.frame().snapshot().isDirty());
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stage.close();
        });
    }

    /**
     * Close requested during another queued job reopens admission before the follow-up confirmation submits Save.
     */
    @Test
    void closeDuringAnActiveJobCanSaveAfterThatJobSettles() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Both deterministic jobs settle before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        Path target = temporaryDirectory.resolve("active-job-close-save.jbs2bg").toAbsolutePath().normalize();
        WorkbenchProjectFlow.Effect chooser = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        flow.respond(chooser.token(), WorkbenchProjectFlow.Response.selected(target));
        worker.runNext();
        flow.apply(SliderPresetEdits.create("Dirty during active job"));
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.save());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform, fixture().environment());
            JobCoordinator.Submission<String> existingJob = new JobCoordinator.Submission<>(
                    new JobCoordinator.Operation("Existing Job", List.of(), List.of(), Optional.empty()),
                    context -> JobCoordinator.Result.completed("done", "Existing job completed.", List.of(),
                            List.of()),
                    (attempt, result) -> {
                        // This synthetic job has no domain publication; only its shutdown boundary is under test.
                    }, Optional.empty());
            assertTrue(jobs.submit(existingJob).admitted());

            WindowEvent closeRequest = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(closeRequest);
            assertTrue(closeRequest.isConsumed());
            assertTrue(jobs.frame().active());
            assertEquals("Save Project", jobs.frame().attempt().orElseThrow().operation().name());
            assertTrue(flow.frame().snapshot().isDirty());
            assertEquals(0, platform.closeCount);

            // The cancelled queued Future remains ahead of the admitted Save in the deterministic executor.
            worker.runNext();
            assertTrue(jobs.frame().active());
            worker.runNext();

            assertFalse(flow.frame().snapshot().isDirty());
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stage.close();
        });
    }

    /** A failure during a pending Close cannot offer Retry while coordinator admission remains shut down. */
    @Test
    void pendingShutdownFailureDialogDoesNotOfferUnadmittableRetry() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The synthetic commit settles before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            try {
                controller.attach(flow, stage, platform, fixture().environment());
                AtomicReference<JobCoordinator.Submission<String>> retryable = new AtomicReference<>();
                JobCoordinator.Submission<String> failing = new JobCoordinator.Submission<>(
                        new JobCoordinator.Operation("Synthetic export", List.of(), List.of(), Optional.empty()),
                        context -> {
                            assertTrue(context.beginCommit("Installing export"));
                            WindowEvent closeRequest = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
                            stage.getOnCloseRequest().handle(closeRequest);
                            assertTrue(closeRequest.isConsumed());
                            return JobCoordinator.Result.failed("Export failed.", List.of());
                        }, (attempt, result) -> {
                            // This job has no domain publication; only failure feedback is under test.
                        }, Optional.of(retryable::get));
                retryable.set(failing);
                assertTrue(jobs.submit(failing).admitted());

                worker.runNext();

                assertNotNull(platform.failureSpec);
                assertFalse(platform.failureSpec.actions().contains(WorkbenchFeedback.DialogAction.RETRY));
            } finally {
                stage.close();
            }
        });
    }

    /**
     * Malformed Open keeps the ProjectSession code, source path, JSON element, line, and column visible.
     */
    @Test
    void malformedOpenRendersCompleteSourceCoordinates() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        Path source = temporaryDirectory.resolve("malformed-project.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "malformed-syntax.jbs2bg"), source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform, fixture().environment());
            ((MenuItem) loader.getNamespace().get("openProjectMenuItem")).fire();

            String diagnostics = ((TextArea) loader.getNamespace().get("diagnosticsText")).getText();
            assertTrue(diagnostics.contains("PROJECT_JSON_MALFORMED"));
            assertTrue(diagnostics.contains("malformed-project.jbs2bg"));
            assertTrue(diagnostics.contains("/SliderPresets"));
            assertTrue(diagnostics.contains("line "));
            assertTrue(diagnostics.contains("column "));
            stage.close();
        });
    }

    /**
     * A failed Activity exposes Retry, which re-reads the source and publishes coordinator-owned linkage.
     */
    @Test
    void failedOpenActivityCanRetryWithFreshlyCapturedInputs() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        Path source = temporaryDirectory.resolve("retry-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "malformed-syntax.jbs2bg"), source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform, fixture().environment());
            ((MenuItem) loader.getNamespace().get("openProjectMenuItem")).fire();
            @SuppressWarnings("unchecked")
            ListView<WorkbenchFeedback.ActivityRecord> activity =
                    (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
            activity.getSelectionModel().selectLast();
            Button retry = (Button) loader.getNamespace().get("retryActivityButton");
            assertFalse(retry.isDisabled());

            Files.copy(Path.of("test-resources", "projects", "legacy-project-semantics.jbs2bg"), source,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            retry.fire();

            assertEquals(source.toAbsolutePath().normalize(),
                    flow.frame().snapshot().getFileIdentity().orElseThrow());
            WorkbenchFeedback.ActivityRecord retried = activity.getItems().getLast();
            assertTrue(retried.jobDetails().orElseThrow().retryOf().isPresent());
            assertEquals(java.util.List.of("Project published"),
                    retried.jobDetails().orElseThrow().effectsCommitted());
            stage.close();
        });
    }

    /** Activity's historical retry flag cannot enable Retry or imply current availability after factory eviction. */
    @Test
    void evictedActivityRetryIsDisabled() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Each deterministic failure settles before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
                stage.show();
                AtomicReference<JobCoordinator.Submission<String>> retryable = new AtomicReference<>();
                JobCoordinator.Submission<String> failing = new JobCoordinator.Submission<>(
                        new JobCoordinator.Operation("Retry history probe", List.of(), List.of(), Optional.empty()),
                        context -> JobCoordinator.Result.failed("Probe failed.", List.of()),
                        (attempt, result) -> {
                            // This probe has no domain publication; only retained retry history is under test.
                        }, Optional.of(retryable::get));
                retryable.set(failing);
                JobCoordinator.AttemptId oldest = null;
                int attempts = 0;
                do {
                    assertTrue(jobs.submit(failing).admitted());
                    worker.runNext();
                    if (oldest == null)
                        oldest = jobs.frame().attempt().orElseThrow().id();
                    attempts++;
                    assertTrue(attempts <= 64, "retry history did not release its oldest factory");
                } while (jobs.isRetryAvailable(oldest));

                @SuppressWarnings("unchecked")
                ListView<WorkbenchFeedback.ActivityRecord> activity =
                        (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
                long oldestAttemptId = oldest.value();
                WorkbenchFeedback.ActivityRecord original = activity.getItems().stream()
                        .filter(item -> item.jobDetails().stream()
                                .anyMatch(details -> details.attemptId() == oldestAttemptId))
                        .findFirst().orElseThrow();
                assertTrue(original.jobDetails().orElseThrow().retryAvailable());
                activity.getSelectionModel().select(original);
                assertTrue(((Button) loader.getNamespace().get("retryActivityButton")).isDisabled());
                activity.scrollTo(original);
                root.applyCss();
                root.layout();
                javafx.scene.control.ListCell<?> cell = activity.lookupAll(".list-cell").stream()
                        .filter(node -> node instanceof javafx.scene.control.ListCell<?> item
                                && item.getItem() == original)
                        .map(node -> (javafx.scene.control.ListCell<?>) node)
                        .findFirst().orElseThrow();
                assertTrue(cell.getAccessibleHelp().contains("Retry offered at completion: true"));
            } finally {
                stage.close();
            }
        });
    }

    /**
     * Active Open disables global launchers, exposes progress, and accepts deterministic pre-start cancellation.
     */
    @Test
    void activeJobOwnsAdmissionProgressAndCancelControls() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        Path source = temporaryDirectory.resolve("queued-source.jbs2bg");
        Files.copy(Path.of("test-resources", "projects", "legacy-project-semantics.jbs2bg"), source);
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Pre-start cancellation settles before prolonged feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform, fixture().environment());
            MenuItem open = (MenuItem) loader.getNamespace().get("openProjectMenuItem");
            MenuItem create = (MenuItem) loader.getNamespace().get("newProjectMenuItem");
            MenuItem save = (MenuItem) loader.getNamespace().get("saveProjectMenuItem");
            MenuItem exit = (MenuItem) loader.getNamespace().get("exitMenuItem");
            Button cancel = (Button) loader.getNamespace().get("cancelOperationButton");
            ProgressBar progress = (ProgressBar) loader.getNamespace().get("operationProgress");

            open.fire();

            assertTrue(open.isDisable());
            assertTrue(create.isDisable());
            assertTrue(save.isDisable());
            assertFalse(exit.isDisable());
            assertTrue(progress.isVisible());
            assertEquals(ProgressBar.INDETERMINATE_PROGRESS, progress.getProgress());
            assertFalse(cancel.isDisable());

            cancel.fire();

            assertFalse(jobs.frame().active());
            assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            assertFalse(open.isDisable());
            assertFalse(progress.isVisible());
            assertTrue(cancel.isDisable());
            @SuppressWarnings("unchecked")
            ListView<WorkbenchFeedback.ActivityRecord> activity =
                    (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
            assertEquals(WorkbenchFeedback.Disposition.CANCELLED,
                    activity.getItems().getLast().disposition());
            assertTrue(activity.getItems().getLast().jobDetails().orElseThrow()
                    .effectsCommitted().isEmpty());
            worker.runNext();
            stage.close();
        });
    }

    /**
     * Ctrl+1..5 use typed navigation, with Output toggling a drawer instead of replacing the active Area.
     */
    @Test
    void keyboardNavigationPreservesTheActiveAreaWhenOutputToggles() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();

            sendControlKey(root, KeyCode.DIGIT2);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());

            sendControlKey(root, KeyCode.DIGIT4);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());
            assertTrue(((ToggleButton) loader.getNamespace().get("outputAreaButton")).isSelected());
            VBox outputDrawer = (VBox) LoadedWorkbench.node(loader, "outputDrawer");
            Slider outputHeight = (Slider) LoadedWorkbench.node(loader, "outputDrawerHeight");
            assertTrue(outputDrawer.isVisible());
            outputHeight.setValue(260.0);
            assertEquals(260.0, outputDrawer.getPrefHeight());
            assertEquals(260.0, outputDrawer.getMaxHeight());

            sendControlKey(root, KeyCode.BACK_QUOTE);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertFalse(((VBox) LoadedWorkbench.node(loader, "outputDrawer")).isVisible());

            ToggleButton outputLauncher = (ToggleButton) loader.getNamespace().get("outputAreaButton");
            outputLauncher.requestFocus();
            outputLauncher.fire();
            sendKey(root, KeyCode.ESCAPE);
            assertSame(outputLauncher, stage.getScene().getFocusOwner());
            stage.close();
        });
    }

    /**
     * F6 follows semantic landmarks, and closing user-opened Output restores the exact prior focus target.
     */
    @Test
    void semanticFocusTraversalAndOutputFocusRestorationArePredictable() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();

            sendControlKey(root, KeyCode.DIGIT2);
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> primary =
                    (ListView<CustomMorphTargetSnapshot>) LoadedWorkbench.node(loader, "customMorphTargetList");
            Label editor = (Label) LoadedWorkbench.node(loader, "morphTargetEditorFocusTarget");
            Label inspector = (Label) LoadedWorkbench.node(loader, "morphTargetSelectionText");
            Label output = (Label) LoadedWorkbench.node(loader, "outputFocusTarget");
            assertSame(primary, scene.getFocusOwner());

            sendKey(root, KeyCode.F6);
            assertSame(editor, scene.getFocusOwner());
            sendKey(root, KeyCode.F6);
            assertSame(inspector, scene.getFocusOwner());

            sendControlKey(root, KeyCode.BACK_QUOTE);
            assertSame(output, scene.getFocusOwner());
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());

            sendKey(root, KeyCode.ESCAPE);
            assertSame(inspector, scene.getFocusOwner());
            assertFalse(((VBox) LoadedWorkbench.node(loader, "outputDrawer")).isVisible());
            stage.close();
        });
    }

    /** A Templates edit remains available during Generate and makes its captured completion stale and invisible. */
    @Test
    void projectEditDuringGenerateProducesStaleActivityWithoutRevealingOutput() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-31T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // This controlled stale completion never reaches prolonged cancellation.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);
        flow.apply(SliderPresetEdits.create("Captured UI"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();
            Label editor = (Label) LoadedWorkbench.node(loader, "templateEditorFocusTarget");
            editor.requestFocus();

            sendControlKey(root, KeyCode.G);
            TextField name = (TextField) LoadedWorkbench.node(loader, "sliderPresetNameInput");
            Button create = (Button) LoadedWorkbench.node(loader, "createSliderPresetButton");
            assertFalse(create.isDisabled());
            assertTrue(((Button) LoadedWorkbench.node(loader, "importBodySlideButton")).isDisabled());
            name.setText("Newer UI");
            create.fire();
            worker.runNext();

            assertFalse(((VBox) LoadedWorkbench.node(loader, "outputDrawer")).isVisible());
            assertEquals("Generate Project output to inspect it here.",
                    ((TextArea) LoadedWorkbench.node(loader, "templatesOutputText")).getText());
            JobCoordinator.Attempt stale = jobs.frame().attempt().orElseThrow();
            assertEquals(JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES, stale.lifecycle());
            assertEquals(List.of("STALE_RESULT"), stale.diagnostics().stream()
                    .map(JobCoordinator.Diagnostic::code).toList());
            assertSame(editor, scene.getFocusOwner());
            stage.close();
        });
    }

    /** Cancel honours a queued Generate before work starts and never reveals or populates Output. */
    @Test
    void cancelledGeneratePublishesActivityWithoutOutput() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-31T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Pre-start cancellation settles without delayed status.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", fixture().newSession(), jobs);

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();

            sendControlKey(root, KeyCode.G);
            Button cancel = (Button) loader.getNamespace().get("cancelOperationButton");
            assertFalse(cancel.isDisabled());
            cancel.fire();
            worker.runNext();

            assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            assertFalse(((VBox) LoadedWorkbench.node(loader, "outputDrawer")).isVisible());
            assertEquals("Generate Project output to inspect it here.",
                    ((TextArea) LoadedWorkbench.node(loader, "templatesOutputText")).getText());
            @SuppressWarnings("unchecked")
            ListView<WorkbenchFeedback.ActivityRecord> activity =
                    (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
            assertEquals(WorkbenchFeedback.Disposition.CANCELLED, activity.getItems().getLast().disposition());
            stage.close();
        });
    }

    /**
     * Theme selection applies live token state while rail icons stay decorative beside their text labels.
     */
    @Test
    void themeChoiceAndSemanticIconsRenderThroughTheLoadedWorkbench() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            // Explicitly not High Contrast: High Contrast would override the Light choice asserted below.
            fixture().appearancePreferences().publish(WorkbenchAppearance.PlatformPreferences.light());
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

            @SuppressWarnings("unchecked")
            ComboBox<WorkbenchAppearance.ThemeChoice> themeChoice =
                    (ComboBox<WorkbenchAppearance.ThemeChoice>) loader.getNamespace().get("themeChoice");
            themeChoice.setValue(WorkbenchAppearance.ThemeChoice.LIGHT);
            themeChoice.fireEvent(new ActionEvent());

            assertTrue(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-light")));
            assertEquals("Light theme", ((Label) loader.getNamespace().get("appearanceStateText")).getText());
            ToggleButton templates = (ToggleButton) loader.getNamespace().get("templatesAreaButton");
            assertInstanceOf(SVGPath.class, templates.getGraphic());
            assertEquals(AccessibleRole.NODE, templates.getGraphic().getAccessibleRole());
            assertEquals("Semantic icon: Templates. Keyboard shortcut: Ctrl+1.", templates.getAccessibleHelp());
            stage.close();
        });
    }

    /** OS High Contrast at startup wins over the chosen theme, and ending it restores that unchanged choice. */
    @Test
    void highContrastPreferenceOverridesTheThemeChoiceThroughTheLoadedWorkbench() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        FakeAppearancePreferencesSource os = fixture().appearancePreferences();

        FxTestToolkit.runOnFxThread(() -> {
            os.publish(WorkbenchAppearance.PlatformPreferences.light().withHighContrast(true));
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            Label appearance = (Label) loader.getNamespace().get("appearanceStateText");

            assertTrue(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-high-contrast")));
            assertEquals("High Contrast theme", appearance.getText());

            @SuppressWarnings("unchecked")
            ComboBox<WorkbenchAppearance.ThemeChoice> themeChoice =
                    (ComboBox<WorkbenchAppearance.ThemeChoice>) loader.getNamespace().get("themeChoice");
            themeChoice.setValue(WorkbenchAppearance.ThemeChoice.DARK);
            themeChoice.fireEvent(new ActionEvent());
            assertEquals("High Contrast theme", appearance.getText());

            os.publish(WorkbenchAppearance.PlatformPreferences.light());
            assertFalse(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-high-contrast")));
            assertTrue(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-dark")));
            assertEquals("Dark theme", appearance.getText());
            stage.close();
        });
    }

    /** A dark OS color scheme renders the Dark theme when the persisted choice follows the System. */
    @Test
    void darkSystemPreferenceRendersTheDarkThemeThroughTheLoadedWorkbench() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            fixture().appearancePreferences().publish(WorkbenchAppearance.PlatformPreferences.dark());
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());

            @SuppressWarnings("unchecked")
            ComboBox<WorkbenchAppearance.ThemeChoice> themeChoice =
                    (ComboBox<WorkbenchAppearance.ThemeChoice>) loader.getNamespace().get("themeChoice");
            assertEquals(WorkbenchAppearance.ThemeChoice.SYSTEM, themeChoice.getValue());
            assertTrue(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-dark")));
            assertFalse(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-light")));
            assertEquals("Dark theme", ((Label) loader.getNamespace().get("appearanceStateText")).getText());
            stage.close();
        });
    }

    /**
     * A live OS preference change re-renders the shown Workbench, and hiding the window releases the subscription so
     * a later change no longer reaches it.
     */
    @Test
    void livePreferenceChangesRefreshTheLoadedWorkbenchUntilItIsHidden() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());
        FakeAppearancePreferencesSource os = fixture().appearancePreferences();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();
            Label appearance = (Label) loader.getNamespace().get("appearanceStateText");
            Label motion = (Label) loader.getNamespace().get("motionStateText");
            assertEquals("Light theme", appearance.getText());
            assertEquals("Standard motion", motion.getText());
            assertEquals(1, os.subscriberCount());

            os.publish(WorkbenchAppearance.PlatformPreferences.dark().withReducedMotion(true));
            assertTrue(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-dark")));
            assertTrue(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-reduced-motion")));
            assertEquals("Dark theme", appearance.getText());
            assertEquals("Reduced motion", motion.getText());

            stage.close();
            assertEquals(0, os.subscriberCount());
            os.publish(WorkbenchAppearance.PlatformPreferences.light());
            assertEquals("Dark theme", appearance.getText());
        });
    }

    /** Theme selection updates the live appearance before its profile write runs on the application worker. */
    @Test
    void themePersistenceRunsAfterVisualSelectionOnTheWorker() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The controlled theme save settles before prolonged cancellation is relevant.
                }, failure -> { throw new AssertionError("Unexpected callback failure", failure); });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs);
        // The environment's preferences directory owns the theme choice, so the saved file is the observable write.
        WorkbenchAppearanceStore themeStore = new WorkbenchAppearanceStore(temporaryDirectory);
        Path themeFile = temporaryDirectory.resolve(WorkbenchAppearanceStore.FILE_NAME);
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            publication.runNext();
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ComboBox<WorkbenchAppearance.ThemeChoice> choice =
                    (ComboBox<WorkbenchAppearance.ThemeChoice>) loader.getNamespace().get("themeChoice");
            choice.setValue(WorkbenchAppearance.ThemeChoice.LIGHT);
            choice.fireEvent(new ActionEvent());

            assertTrue(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("workbench-light")));
            assertEquals("Light theme", ((Label) loader.getNamespace().get("appearanceStateText")).getText());
            assertTrue(jobs.frame().active());
            assertFalse(Files.exists(themeFile));
        });

        Thread themeWorker = worker.runNextAsync();
        themeWorker.join();
        assertEquals(WorkbenchAppearance.ThemeChoice.LIGHT, themeStore.load());
        FxTestToolkit.runOnFxThread(() -> {
            for (int index = 0; index < 6 && jobs.frame().active(); index++)
                publication.runNext();
            assertFalse(jobs.frame().active());
            stageReference.get().close();
        });
    }

    /**
     * At the accepted breakpoint, real side panes move into overlays and Esc returns focus to each launcher.
     */
    @Test
    void narrowLayoutKeepsTheEditorInlineAndUsesFocusRestoringSideOverlays() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform(), fixture().environment());
            stage.show();

            root.resize(1199, 720);
            HBox areaPanes = (HBox) loader.getNamespace().get("areaPanes");
            StackPane overlay = (StackPane) loader.getNamespace().get("overlayLayer");
            VBox primaryPane = (VBox) loader.getNamespace().get("primaryPane");
            StackPane editorPane = (StackPane) loader.getNamespace().get("editorPane");
            VBox inspectorPane = (VBox) loader.getNamespace().get("inspectorPane");
            Button listLauncher = (Button) loader.getNamespace().get("showPrimaryOverlayButton");
            Button inspectorLauncher = (Button) loader.getNamespace().get("showInspectorOverlayButton");
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> primary =
                    (ListView<SliderPresetSnapshot>) LoadedWorkbench.node(loader, "sliderPresetList");
            Label inspector = (Label) LoadedWorkbench.node(loader, "templateSelectionText");

            assertEquals(java.util.List.of(editorPane), areaPanes.getChildren());
            assertTrue(listLauncher.isVisible());
            assertTrue(inspectorLauncher.isVisible());
            assertInstanceOf(javafx.scene.shape.Rectangle.class, overlay.getClip());

            listLauncher.fire();
            assertEquals(java.util.List.of(primaryPane), overlay.getChildren());
            assertSame(primary, scene.getFocusOwner());
            sendKey(root, KeyCode.ESCAPE);
            assertTrue(overlay.getChildren().isEmpty());
            assertSame(listLauncher, scene.getFocusOwner());

            sendKey(root, KeyCode.F7);
            assertEquals(java.util.List.of(inspectorPane), overlay.getChildren());
            assertSame(inspector, scene.getFocusOwner());
            sendKey(root, KeyCode.ESCAPE);
            assertSame(inspectorLauncher, scene.getFocusOwner());

            root.resize(1200, 720);
            assertEquals(java.util.List.of(primaryPane, editorPane, inspectorPane), areaPanes.getChildren());
            assertFalse(listLauncher.isVisible());

            ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
            root.resize(1199, 720);
            listLauncher.fire();
            assertSame(LoadedWorkbench.node(loader, "npcSourceList"), scene.getFocusOwner());
            sendKey(root, KeyCode.ESCAPE);
            sendKey(root, KeyCode.F7);
            assertSame(LoadedWorkbench.node(loader, "npcInspectorName"), scene.getFocusOwner());
            assertEquals("NPC Database catalog",
                    ((TableView<?>) LoadedWorkbench.node(loader, "npcCatalogTable")).getAccessibleText());
            stage.close();
        });
    }

    /** ExecutorService that runs submitted work inline for deterministic reentrancy coverage. */
    private static final class InlineExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        /** Marks the inline executor unavailable for later submissions. */
        @Override
        public void shutdown() {
            shutdown = true;
        }

        /** Marks shutdown and reports that no queued tasks exist. */
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        /** @return whether shutdown was requested */
        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        /** @return whether the inline executor has terminated */
        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        /** Inline execution never needs to wait for termination. */
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            Objects.requireNonNull(unit, "unit");
            return shutdown;
        }

        /** Executes one accepted task before returning to its submitter. */
        @Override
        public void execute(Runnable command) {
            if (shutdown)
                throw new java.util.concurrent.RejectedExecutionException("inline executor is shut down");
            Objects.requireNonNull(command, "command").run();
        }
    }

    /** Delegates lifecycle operations while supplying deterministic Project edit rejection or failure. */
    static final class RefusingEditSession implements ProjectSession {
        private final ProjectSession delegate;
        private boolean refuse;
        private boolean failed;

        /** Wraps a real session so fixture setup and snapshots retain production semantics. */
        RefusingEditSession(ProjectSession delegate) {
            this.delegate = delegate;
        }

        /** Arms the requested outcome only after fixture creation through the public Project seam. */
        void refuseEdits(boolean failure) {
            refuse = true;
            failed = failure;
        }

        /** {@inheritDoc} */
        @Override
        public ProjectSnapshot getSnapshot() {
            return delegate.getSnapshot();
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome newProject() {
            return delegate.newProject();
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome open(Path source, ProjectOperationContext context) {
            return delegate.open(source, context);
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome save(ProjectOperationContext context) {
            return delegate.save(context);
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome saveAs(Path target, ProjectOperationContext context) {
            return delegate.saveAs(target, context);
        }

        /** {@inheritDoc} */
        @Override
        public SliderPresetImportOutcome importSliderPresets(List<Path> sources, ProjectOperationContext context) {
            return delegate.importSliderPresets(sources, context);
        }

        /** {@inheritDoc} */
        @Override
        public ProjectOutcome refreshSettings() {
            return delegate.refreshSettings();
        }

        /** Preserves the Project while returning a structured diagnostic after rejection is armed. */
        @Override
        public ProjectOutcome apply(ProjectEdit edit) {
            if (!refuse)
                return delegate.apply(edit);
            List<ProjectDiagnostic> diagnostics = List.of(new ProjectDiagnostic("TEST_MORPH_REMOVE",
                    DiagnosticSeverity.ERROR, new SourceLocation(Optional.empty(), Optional.of("custom-target"),
                    OptionalInt.empty(), OptionalInt.empty()), "The target could not be removed."));
            return failed ? new FailedOutcome(delegate.getSnapshot(), diagnostics)
                    : new RejectedOutcome(delegate.getSnapshot(), diagnostics);
        }

        /** Preserves the armed refusal for bulk NPC edits as well as single edits. */
        @Override
        public NpcPromotionOutcome promoteNpcs(List<NpcMorphAssignmentSnapshot> sources) {
            if (!refuse)
                return delegate.promoteNpcs(sources);
            ProjectOutcome refusal = apply(NpcMorphAssignmentEdits.addNpcs(sources));
            return new NpcPromotionOutcome(refusal, java.util.Collections.nCopies(sources.size(), refusal));
        }
    }

}
