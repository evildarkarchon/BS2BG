package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
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
import com.asdasfa.jbs2bg.data.SettingsLockHolderProbe;
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
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;
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
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchControllerTest {

    @TempDir
    Path temporaryDirectory;

    /** Imported NPC sources populate the independent catalog and its selection-following inspector. */
    @Test
    void npcDatabaseImportsSourcesAndInspectsSelectedCatalogRow() throws Exception {
        Path source = temporaryDirectory.resolve("npcs.txt");
        Files.writeString(source, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n"
                + "Skyrim.esm | Beta | Beta01 | BretonRace | 00012346\n");
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) loader.getNamespace().get("importNpcSourcesButton")).fire();

                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                assertEquals(List.of("Alpha", "Beta"), catalog.getItems().stream().map(NPC::getName).toList());
                assertEquals(1, ((ListView<?>) loader.getNamespace().get("npcSourceList")).getItems().size());
                catalog.getSelectionModel().selectFirst();
                assertTrue(((Label) loader.getNamespace().get("npcInspectorName")).getText().contains("Alpha"));
                assertTrue(((Label) loader.getNamespace().get("npcDatabasePortraitStatus")).getText()
                        .contains("No portrait found"));
            } finally {
                stage.close();
            }
        });
    }

    /** The Morphs launcher returns to its focus and newly promoted identity after a selected Add. */
    @Test
    void npcDatabaseAddReturnsToMorphsLauncherAndPromotedAssignment() throws Exception {
        Path source = temporaryDirectory.resolve("promote-npcs.txt");
        Files.writeString(source, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n");
        ProjectSession session = ProjectSessions.create();
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
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                Button launcher = (Button) loader.getNamespace().get("addNpcsFromDatabaseButton");
                launcher.requestFocus();
                launcher.fire();
                assertTrue(((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).isSelected());
                ((Button) loader.getNamespace().get("importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                ((CheckBox) loader.getNamespace().get("assignRandomNpcPresetCheck")).fire();

                ((Button) loader.getNamespace().get("addNpcToProjectButton")).fire();

                assertEquals("Amber", catalog.getSelectionModel().getSelectedItem().getName());
                assertEquals(1, session.getSnapshot().getNpcMorphAssignments().size());
                assertEquals(List.of("Shape"), session.getSnapshot().getNpcMorphAssignments().getFirst()
                        .getSliderPresetNames());
                ((Button) loader.getNamespace().get("addNpcToProjectButton")).fire();
                assertEquals(1, session.getSnapshot().getNpcMorphAssignments().size());
                @SuppressWarnings("unchecked")
                ListView<NpcMorphAssignmentSnapshot> assignments = (ListView<NpcMorphAssignmentSnapshot>)
                        root.lookup("#npcMorphAssignmentList");
                assertEquals(1, assignments.getItems().size());
                ((Button) loader.getNamespace().get("backToMorphsButton")).fire();
                assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());
                assertSame(launcher, scene.getFocusOwner());
                assertEquals("Amber", assignments.getSelectionModel().getSelectedItem().getDisplayName());
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
        ProjectSession session = ProjectSessions.create();
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
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) loader.getNamespace().get("importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                hideNpcColumnValue(loader, "Name", "Hidden");

                ((Button) loader.getNamespace().get("addAllNpcsToProjectButton")).fire();

                assertEquals(2, session.getSnapshot().getNpcMorphAssignments().size());
                assertEquals("Existing", catalog.getSelectionModel().getSelectedItem().getName());
                assertEquals(3, catalog.getItems().size());
                String summary = ((Label) loader.getNamespace().get("npcPromotionInfoBarMessage")).getText();
                assertTrue(summary.contains("Added 1"));
                assertTrue(summary.contains("1 already"));
                assertTrue(summary.contains("1 rejected"));
                String details = ((TextArea) loader.getNamespace().get("npcPromotionDetails")).getText();
                assertTrue(details.contains("Existing01"));
                assertTrue(details.contains("Invalid01"));
                assertFalse(details.contains("Hidden01"));
                assertEquals("", ((TextArea) loader.getNamespace().get("diagnosticsText")).getText());
                assertTrue(((HBox) loader.getNamespace().get("npcPromotionInfoBar")).isVisible());
                assertFalse(((HBox) loader.getNamespace().get("infoBar")).isVisible());
                WorkbenchFeedback.ActivityRecord activity = (WorkbenchFeedback.ActivityRecord)
                        ((ListView<?>) loader.getNamespace().get("activityList")).getItems().getLast();
                assertEquals("Add All NPCs to Project", activity.operation());
                assertTrue(activity.details().orElseThrow().contains("Existing01"));
                assertTrue(activity.details().orElseThrow().contains("Invalid01"));
                catalog.requestFocus();
                sendKey(catalog, KeyCode.ENTER);
                assertEquals(2, session.getSnapshot().getNpcMorphAssignments().size());
                assertTrue(((Label) loader.getNamespace().get("npcPromotionInfoBarMessage")).getText()
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
        ProjectSession session = ProjectSessions.create();
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
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) loader.getNamespace().get("importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                catalog.getSelectionModel().selectFirst();
                Button add = (Button) loader.getNamespace().get("addNpcToProjectButton");
                add.fire();
                add.fire();
                assertTrue(((HBox) loader.getNamespace().get("npcPromotionInfoBar")).isVisible());

                ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                assertFalse(((HBox) loader.getNamespace().get("npcPromotionInfoBar")).isVisible());
                ((Button) loader.getNamespace().get("backToMorphsButton")).fire();

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

    /** Column filters clear hidden selection, sorting changes order, and Clear freezes only visible identities. */
    @Test
    void npcDatabaseClearUsesFilteredVisibleScope() throws Exception {
        Path source = temporaryDirectory.resolve("filter-npcs.txt");
        Files.writeString(source, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n"
                + "Skyrim.esm | Beta | Beta01 | BretonRace | 00012346\n");
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CLEAR);
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) loader.getNamespace().get("importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                catalog.getSelectionModel().selectFirst();

                ToggleButton raceFilter = (ToggleButton) loader.getNamespace().get("npcRaceFilterButton");
                raceFilter.fire();
                Popup cancelledFilter = (Popup) Window.getWindows().stream()
                        .filter(window -> window instanceof Popup && window.isShowing()
                                && window.getScene().getRoot().lookup("#npcColumnFilterChoices") != null)
                        .findFirst().orElseThrow();
                sendKey(cancelledFilter.getScene().getRoot(), KeyCode.ESCAPE);
                assertFalse(cancelledFilter.isShowing());
                assertEquals(2, catalog.getItems().size());
                hideNpcColumnValue(loader, "Race", "NordRace");
                assertEquals(List.of("Beta"), catalog.getItems().stream().map(NPC::getName).toList());
                assertNull(catalog.getSelectionModel().getSelectedItem());
                assertTrue(((ToggleButton) loader.getNamespace().get("npcRaceFilterButton"))
                        .getAccessibleHelp().contains("active, collapsed"));

                ((Button) loader.getNamespace().get("clearNpcFiltersButton")).fire();
                @SuppressWarnings("unchecked")
                ComboBox<String> sort = (ComboBox<String>) loader.getNamespace().get("npcSortChoice");
                sort.getSelectionModel().select("Name descending");
                assertEquals(List.of("Beta", "Alpha"), catalog.getItems().stream().map(NPC::getName).toList());

                hideNpcColumnValue(loader, "Name", "Beta");
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                ((Button) loader.getNamespace().get("clearNpcDatabaseButton")).fire();
                assertTrue(catalog.getItems().isEmpty());
                ((Button) loader.getNamespace().get("clearNpcFiltersButton")).fire();
                assertEquals(List.of("Beta"), catalog.getItems().stream().map(NPC::getName).toList());
            } finally {
                stage.close();
            }
        });
    }

    /** A malformed file publishes no rows from that file while earlier and later sources remain inspectable. */
    @Test
    void npcDatabaseMixedImportKeepsValidSourcesAndReportsRejectedLine() throws Exception {
        Path first = temporaryDirectory.resolve("first-npcs.txt");
        Path malformed = temporaryDirectory.resolve("malformed-npcs.txt");
        Path last = temporaryDirectory.resolve("last-npcs.txt");
        Files.writeString(first, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n");
        Files.writeString(malformed, "Skyrim.esm | Ghost | Ghost01 | NordRace | 00012346\n"
                + "not an NPC row\n");
        Files.writeString(last, "Skyrim.esm | Gamma | Gamma01 | BretonRace | 00012347\n");
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(first, malformed, last)));
        platform.respondNpcSourcesWith(Optional.of(List.of(malformed)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                Button importButton = (Button) loader.getNamespace().get("importNpcSourcesButton");
                importButton.fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                assertEquals(List.of("Alpha", "Gamma"), catalog.getItems().stream().map(NPC::getName).toList());
                assertEquals(2, ((ListView<?>) loader.getNamespace().get("npcSourceList")).getItems().size());
                String diagnostics = ((Label) loader.getNamespace().get("npcDatabaseDiagnostics")).getText();
                assertTrue(diagnostics.contains("NPC_DATABASE_SOURCE_MALFORMED"));
                assertTrue(diagnostics.contains("malformed-npcs.txt:2"));
                assertTrue(flow.frame().snapshot().getNpcMorphAssignments().isEmpty());

                Files.writeString(malformed, "Skyrim.esm | Ghost | Ghost01 | NordRace | 00012346\n");
                importButton.fire();
                assertEquals(List.of("Alpha", "Gamma", "Ghost"),
                        catalog.getItems().stream().map(NPC::getName).toList());
                assertEquals("No source diagnostics",
                        ((Label) loader.getNamespace().get("npcDatabaseDiagnostics")).getText());
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
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
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                Button importButton = (Button) loader.getNamespace().get("importNpcSourcesButton");
                importButton.fire();
                importButton.fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                assertTrue(((Label) loader.getNamespace().get("npcDatabaseDiagnostics")).getText()
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
                        ((Label) loader.getNamespace().get("npcDatabaseDiagnostics")).getText());
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
                "BS2BG Preview", ProjectSessions.create(), jobs);
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
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                Button importButton = (Button) loader.getNamespace().get("importNpcSourcesButton");
                importButton.fire();
                worker.runNext();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                catalog.getSelectionModel().selectFirst();

                importButton.fire();
                assertTrue(importButton.isDisabled());
                assertTrue(((ProgressBar) loader.getNamespace().get("operationProgress")).isVisible());
                ((ToggleButton) loader.getNamespace().get("templatesAreaButton")).fire();
                ((TextField) loader.getNamespace().get("sliderPresetNameInput")).setText("Edited during NPC import");
                Button createPreset = (Button) loader.getNamespace().get("createSliderPresetButton");
                assertFalse(createPreset.isDisabled());
                createPreset.fire();
                assertEquals(1, flow.frame().snapshot().getSliderPresets().size());
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) loader.getNamespace().get("cancelOperationButton")).fire();
                assertEquals(JobCoordinator.Lifecycle.CANCELLED, jobs.frame().attempt().orElseThrow().lifecycle());
                assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
                ListView<?> sources = (ListView<?>) loader.getNamespace().get("npcSourceList");
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
                "BS2BG Preview", ProjectSessions.create(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondNpcSourcesWith(Optional.of(List.of(first, second)));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) loader.getNamespace().get("importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
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
                controller.attach(flow, stage, platform);
                stage.show();
                ((ToggleButton) loader.getNamespace().get("npcDatabaseAreaButton")).fire();
                ((Button) loader.getNamespace().get("importNpcSourcesButton")).fire();
                @SuppressWarnings("unchecked")
                TableView<NPC> catalog = (TableView<NPC>) loader.getNamespace().get("npcCatalogTable");
                catalog.requestFocus();
                for (int index = 0; index < 2; index++)
                    catalog.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, "a", "a", KeyCode.UNDEFINED,
                            false, false, false, false));
                assertEquals("Azure", catalog.getSelectionModel().getSelectedItem().getName());
                sendControlKey(root, KeyCode.K);
                assertSame(loader.getNamespace().get("npcNameFilterButton"), scene.getFocusOwner());
                sendKey(root, KeyCode.ESCAPE);
                assertNull(catalog.getSelectionModel().getSelectedItem());
            } finally {
                stage.close();
            }
        });
    }

    /** The Fill Empty flyout requires a chosen preset and cancellation preserves every NPC assignment. */
    @Test
    void fillEmptyFlyoutCancelsAndAppliesOnlyChosenPresets() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.create("Lydia", "Skyrim.esm", "Lydia01", "NordRace", "000001"));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, new RecordingPlatform());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                stage.show();
                Button launcher = (Button) loader.getNamespace().get("fillEmptyNpcMorphAssignmentsButton");
                launcher.fire();
                Popup first = (Popup) Window.getWindows().stream()
                        .filter(window -> window instanceof Popup && window.isShowing())
                        .findFirst().orElseThrow();
                Button firstApply = (Button) first.getScene().getRoot().lookup("#fillEmptyApplyButton");
                assertTrue(firstApply.isDisabled());
                ((Button) first.getScene().getRoot().lookup("#fillEmptyCancelButton")).fire();
                assertFalse(first.isShowing());
                assertTrue(flow.frame().snapshot().getNpcMorphAssignments().getFirst()
                        .getSliderPresetNames().isEmpty());

                launcher.fire();
                Popup second = (Popup) Window.getWindows().stream()
                        .filter(window -> window instanceof Popup && window.isShowing())
                        .findFirst().orElseThrow();
                @SuppressWarnings("unchecked")
                ListView<SliderPresetSnapshot> choices =
                        (ListView<SliderPresetSnapshot>) second.getScene().getRoot()
                                .lookup("#fillEmptySliderPresetList");
                choices.getSelectionModel().selectFirst();
                Button apply = (Button) second.getScene().getRoot().lookup("#fillEmptyApplyButton");
                assertEquals("Fill 1 NPC from 1 preset", apply.getText());
                apply.fire();
                assertFalse(second.isShowing());
                assertEquals(List.of("Alpha"), flow.frame().snapshot().getNpcMorphAssignments().getFirst()
                        .getSliderPresetNames());
            } finally {
                stage.close();
            }
        });
    }

    /** The inspector portrait follows logical NPC selection and explains the missing-file fallback. */
    @Test
    void npcPortraitInspectorClearsWithSelectionAndDescribesMissingImage() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(NpcMorphAssignmentEdits.create("NoPortraitFixture", "Skyrim.esm", "HousecarlWhiterun",
                "NordRace", "000A2C94"));
        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, new RecordingPlatform());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                ListView<NpcMorphAssignmentSnapshot> npcs = controller.npcMorphAssignmentListNode();
                npcs.getSelectionModel().selectFirst();
                ImageView portrait = (ImageView) loader.getNamespace().get("npcPortraitImage");
                Label status = (Label) loader.getNamespace().get("npcPortraitStatus");
                Button viewer = (Button) loader.getNamespace().get("openNpcPortraitViewerButton");
                assertEquals(AccessibleRole.NODE, portrait.getAccessibleRole());
                assertNull(portrait.getAccessibleText(),
                        "the named NPC inspector makes its adjacent thumbnail decorative");
                assertTrue(status.getText().contains("NoPortraitFixture (HousecarlWhiterun).jpg"));
                assertTrue(viewer.isDisabled());
                ((TextField) loader.getNamespace().get("npcMorphAssignmentFilter")).setText("no match");
                assertNull(portrait.getAccessibleText());
                assertNull(portrait.getImage());
            } finally {
                stage.close();
            }
        });
    }

    /** Opening the narrow NPC inspector reveals its portrait rather than retaining a prior relationship scroll. */
    @Test
    void narrowNpcInspectorOpensAtPortraitAfterEarlierRelationshipScroll() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
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
                controller.attach(flow, stage, new RecordingPlatform());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                controller.npcMorphAssignmentListNode().getSelectionModel().selectFirst();
                stage.show();
                root.resize(900.0, 700.0);
                ScrollPane inspector = (ScrollPane) loader.getNamespace().get("morphsInspectorScroll");
                inspector.setVvalue(1.0);

                sendKey(root, KeyCode.F7);

                assertEquals(0.0, inspector.getVvalue());
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));
        flow.apply(CustomMorphTargetEdits.create("First"));
        flow.apply(CustomMorphTargetEdits.create("Second"));
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
            controller.attach(flow, stage, new RecordingPlatform());
            if (morphs)
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            ListView<?> catalog = morphs ? controller.customMorphTargetListNode() : controller.sliderPresetListNode();
            try {
                stage.show();
                stage.setRenderScaleX(renderScale);
                stage.setRenderScaleY(renderScale);
                settleCatalogLayout(root);
                assertShortCatalogHasNoVerticalScrollbar(catalog);
                // A displayed window can move to another monitor without any Project frame changing.
                stage.setRenderScaleX(1.5);
                stage.setRenderScaleY(1.5);
                settleCatalogLayout(root);
                assertShortCatalogHasNoVerticalScrollbar(catalog);
            } finally {
                stage.close();
            }
        });
    }

    /** Waits for CSS/navigation and VirtualFlow's deferred cell metrics to finish real layout pulses. */
    private static void settleCatalogLayout(Parent root) {
        Object loop = new Object();
        int[] pulses = {0};
        Runnable listener = () -> {
            if (++pulses[0] == 2)
                Platform.runLater(() -> Platform.exitNestedEventLoop(loop, null));
            else
                Platform.requestNextPulse();
        };
        root.getScene().addPostLayoutPulseListener(listener);
        try {
            Platform.requestNextPulse();
            Platform.enterNestedEventLoop(loop);
        } finally {
            root.getScene().removePostLayoutPulseListener(listener);
        }
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
     * The Morphs JavaFX adapter renders immutable target frames and translates catalog and relationship controls into
     * typed intents without retaining control-local Project state.
     */
    @Test
    void morphsControlsCreateFilterAndAssignThroughTheAuthoritativeProjectFlow() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));
        flow.apply(CustomMorphTargetEdits.create("Existing"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new RecordingPlatform());
            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> targets =
                    (ListView<CustomMorphTargetSnapshot>) loader.getNamespace().get("customMorphTargetList");
            TextField name = (TextField) loader.getNamespace().get("customMorphTargetNameInput");
            TextField filter = (TextField) loader.getNamespace().get("customMorphTargetFilter");
            Button create = (Button) loader.getNamespace().get("createCustomMorphTargetButton");
            @SuppressWarnings("unchecked")
            ComboBox<SliderPresetSnapshot> available =
                    (ComboBox<SliderPresetSnapshot>) loader.getNamespace().get("availableMorphSliderPreset");
            Button assign = (Button) loader.getNamespace().get("assignMorphSliderPresetButton");
            Label condition = (Label) loader.getNamespace().get("morphTargetConditionText");
            Label selection = (Label) loader.getNamespace().get("morphTargetSelectionText");
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> assigned =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("assignedMorphSliderPresetList");
            Set<Character> inspectorMnemonics = List.<Labeled>of(
                            (Label) loader.getNamespace().get("assignedMorphSliderPresetLabel"),
                            (Button) loader.getNamespace().get("removeMorphSliderPresetButton"),
                            (Button) loader.getNamespace().get("clearMorphSliderPresetsButton"),
                            (Label) loader.getNamespace().get("availableMorphSliderPresetLabel"),
                            assign,
                            (Button) loader.getNamespace().get("assignAllMorphSliderPresetsButton"))
                    .stream().map(Labeled::getText).filter(text -> text.contains("_"))
                    .map(text -> Character.toUpperCase(text.charAt(text.indexOf('_') + 1)))
                    .collect(java.util.stream.Collectors.toSet());

            assertTrue(targets.isVisible());
            assertEquals(Set.of('A', 'R', 'C', 'V', 'I', 'L'), inspectorMnemonics);
            assertEquals(List.of("Existing"), targets.getItems().stream()
                    .map(CustomMorphTargetSnapshot::getName).toList());
            name.setText("  All|Female  ");
            create.fire();
            assertEquals("All|Female", targets.getSelectionModel().getSelectedItem().getName());

            targets.getSelectionModel().select(targets.getItems().stream()
                    .filter(target -> target.getName().equals("Existing")).findFirst().orElseThrow());
            available.setValue(available.getItems().stream()
                    .filter(preset -> preset.getName().equals("Alpha")).findFirst().orElseThrow());
            available.fireEvent(new ActionEvent());
            assign.fire();
            assigned = controller.assignedMorphSliderPresetListNode();

            assertEquals(List.of("Alpha"), assigned.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertEquals(List.of("Alpha"), flow.frame().snapshot().getCustomMorphTargets().stream()
                    .filter(target -> target.getName().equals("Existing")).findFirst().orElseThrow()
                    .getSliderPresetNames());
            assertEquals("BodyGen condition: Existing", condition.getAccessibleText());
            assertEquals("Selected Custom Morph Target Existing", selection.getAccessibleText());
            filter.setText("all");
            assertNull(targets.getSelectionModel().getSelectedItem());
            assertTrue(assigned.getItems().isEmpty());
            assertEquals("Morphs inspector: no selection", selection.getAccessibleText());
            assertEquals("Select a Custom Morph Target or NPC Morph Assignment to inspect it.",
                    condition.getAccessibleText());
            stage.close();
        });
    }

    /**
     * Morphs validation stays pane-local, relationship removal is immediate, and destructive relationship/catalog
     * clears consume the correctly named confirmation actions without retargeting selection.
     */
    @Test
    void morphsValidationAndDestructiveActionsUseSafeTypedFeedback() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("Another"));
        flow.apply(CustomMorphTargetEdits.create("Existing"));
        RecordingPlatform platform = new RecordingPlatform();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);
            ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> targets =
                    (ListView<CustomMorphTargetSnapshot>) loader.getNamespace().get("customMorphTargetList");
            TextField name = (TextField) loader.getNamespace().get("customMorphTargetNameInput");
            Button create = (Button) loader.getNamespace().get("createCustomMorphTargetButton");
            @SuppressWarnings("unchecked")
            ComboBox<SliderPresetSnapshot> available =
                    (ComboBox<SliderPresetSnapshot>) loader.getNamespace().get("availableMorphSliderPreset");
            Button assign = (Button) loader.getNamespace().get("assignMorphSliderPresetButton");
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> assigned =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("assignedMorphSliderPresetList");
            Button removeAssignment = (Button) loader.getNamespace().get("removeMorphSliderPresetButton");
            Button clearAssignments = (Button) loader.getNamespace().get("clearMorphSliderPresetsButton");
            Button removeTarget = (Button) loader.getNamespace().get("removeCustomMorphTargetButton");
            Button clearTargets = (Button) loader.getNamespace().get("clearCustomMorphTargetsButton");
            TextField filter = (TextField) loader.getNamespace().get("customMorphTargetFilter");

            name.setText("existing");
            create.fire();
            assertTrue(((HBox) loader.getNamespace().get("morphsInfoBar")).isVisible());
            assertFalse(((HBox) loader.getNamespace().get("templatesInfoBar")).isVisible());
            assertTrue(((ListView<?>) loader.getNamespace().get("activityList")).getItems().isEmpty());

            targets.getSelectionModel().select(targets.getItems().stream()
                    .filter(target -> target.getName().equals("Existing")).findFirst().orElseThrow());
            available.setValue(available.getItems().getFirst());
            available.fireEvent(new ActionEvent());
            assign.fire();
            assigned = controller.assignedMorphSliderPresetListNode();
            assigned.getSelectionModel().selectFirst();
            assigned.getSelectionModel().clearSelection();
            assertTrue(removeAssignment.isDisabled());
            assigned.getSelectionModel().selectFirst();
            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.REMOVE);
            removeAssignment.fire();
            assigned = controller.assignedMorphSliderPresetListNode();
            assertTrue(assigned.getItems().isEmpty());

            available.setValue(available.getItems().getFirst());
            available.fireEvent(new ActionEvent());
            assign.fire();
            assigned = controller.assignedMorphSliderPresetListNode();
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
        RefusingEditSession session = new RefusingEditSession(ProjectSessions.create());
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
                controller.attach(flow, stage, platform);
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                ListView<?> targets = (ListView<?>) loader.getNamespace().get("customMorphTargetList");
                targets.getSelectionModel().selectFirst();
                ((Button) loader.getNamespace().get("removeCustomMorphTargetButton")).fire();

                assertTrue(((Label) loader.getNamespace().get("morphsInfoBarMessage")).getText()
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

    /** Empty target and NPC entries are emitted as Morphs lines even without Slider Preset assignments. */
    @Test
    void unassignedMorphEntriesDescribeTheirEmittedOutput() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(CustomMorphTargetEdits.create("Unassigned"));
        flow.apply(NpcMorphAssignmentEdits.create("Guard", "Skyrim.esm", "Guard01", "NordRace", "000123"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            try {
                controller.attach(flow, stage, new RecordingPlatform());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                @SuppressWarnings("unchecked")
                ListView<CustomMorphTargetSnapshot> targets =
                        (ListView<CustomMorphTargetSnapshot>) loader.getNamespace().get("customMorphTargetList");
                @SuppressWarnings("unchecked")
                ListView<NpcMorphAssignmentSnapshot> npcs =
                        (ListView<NpcMorphAssignmentSnapshot>) loader.getNamespace().get("npcMorphAssignmentList");
                Label status = (Label) loader.getNamespace().get("morphTargetOutputStatusText");

                targets.getSelectionModel().selectFirst();
                assertEquals("Included in Morphs output without Slider Preset assignments.", status.getText());
                npcs.getSelectionModel().selectFirst();
                assertEquals("Included in Morphs output without Slider Preset assignments.", status.getText());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * The Morphs controls author an NPC through the Project flow and keep its selection separate from a Custom
     * Morph Target while the shared editor exposes the NPC's complete output identity.
     */
    @Test
    void morphsNpcControlsCreateInspectAndSelectWithoutRetargeting() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("All|Female"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, new RecordingPlatform());
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                @SuppressWarnings("unchecked")
                ListView<CustomMorphTargetSnapshot> targets =
                        (ListView<CustomMorphTargetSnapshot>) loader.getNamespace().get("customMorphTargetList");
                @SuppressWarnings("unchecked")
                ListView<NpcMorphAssignmentSnapshot> npcs =
                        (ListView<NpcMorphAssignmentSnapshot>) loader.getNamespace().get("npcMorphAssignmentList");
                targets.getSelectionModel().selectFirst();
                @SuppressWarnings("unchecked")
                ComboBox<SliderPresetSnapshot> available =
                        (ComboBox<SliderPresetSnapshot>) loader.getNamespace().get("availableMorphSliderPreset");
                Button assign = (Button) loader.getNamespace().get("assignMorphSliderPresetButton");
                available.setValue(available.getItems().getFirst());

                ((TextField) loader.getNamespace().get("npcDisplayNameInput")).setText("Lyra");
                ((TextField) loader.getNamespace().get("npcPluginNameInput")).setText("People.esp");
                ((TextField) loader.getNamespace().get("npcEditorIdInput")).setText("Lyra01");
                ((TextField) loader.getNamespace().get("npcRaceInput")).setText("NordRace");
                ((TextField) loader.getNamespace().get("npcFormIdInput")).setText("000A12B3");
                ((Button) loader.getNamespace().get("createNpcMorphAssignmentButton")).fire();

                npcs = controller.npcMorphAssignmentListNode();
                NpcMorphAssignmentSnapshot npc = npcs.getSelectionModel().getSelectedItem();
                assertNotNull(npc);
                assertNull(targets.getSelectionModel().getSelectedItem());
                assertNull(available.getValue(), "a pending target preset must not carry to the selected NPC");
                assertTrue(assign.isDisabled());
                assertEquals("Lyra", npc.getDisplayName());
                assertEquals("People.esp", npc.getPluginName());
                assertEquals("Lyra01", npc.getEditorId());
                assertEquals("NordRace", npc.getRace());
                assertEquals("A12B3", npc.getFormId());
                assertEquals("Lyra", ((Label) loader.getNamespace().get("morphTargetEditorFocusTarget")).getText());
                assertEquals("Plugin: People.esp; Editor ID: Lyra01",
                        ((Label) loader.getNamespace().get("npcIdentityText")).getText());
                assertEquals("Race: NordRace", ((Label) loader.getNamespace().get("npcRaceText")).getText());
                assertEquals("Form ID: A12B3", ((Label) loader.getNamespace().get("npcFormIdText")).getText());

                available.setValue(available.getItems().getFirst());
                targets.getSelectionModel().selectFirst();
                assertNull(npcs.getSelectionModel().getSelectedItem());
                assertNull(available.getValue(), "a pending NPC preset must not carry to the selected target");
                assertTrue(assign.isDisabled());
                assertEquals("BodyGen condition: All|Female",
                        ((Label) loader.getNamespace().get("morphTargetConditionText")).getText());
                npcs.getSelectionModel().selectFirst();
                assertNull(targets.getSelectionModel().getSelectedItem());
            } finally {
                stage.close();
            }
        });
    }

    /** NPC filtering and sorting retain logical selection, and a confirmed clear removes only the frozen visible set. */
    @Test
    void morphsNpcControlsEditRelationshipsAndClearOnlyVisibleAssignments() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot(
                "Lyra", "People.esp", "Lyra01", "NordRace", "A12B3", List.of())));
        flow.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot(
                "Mira", "Other.esp", "Mira01", "BretonRace", "B45C6", List.of())));
        RecordingPlatform platform = new RecordingPlatform();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, platform);
                ((ToggleButton) loader.getNamespace().get("morphsAreaButton")).fire();
                ListView<NpcMorphAssignmentSnapshot> npcs = controller.npcMorphAssignmentListNode();
                npcs.getSelectionModel().select(npcs.getItems().stream()
                        .filter(npc -> npc.getEditorId().equals("Mira01")).findFirst().orElseThrow());
                @SuppressWarnings("unchecked")
                ComboBox<SliderPresetSnapshot> available =
                        (ComboBox<SliderPresetSnapshot>) loader.getNamespace().get("availableMorphSliderPreset");
                available.setValue(available.getItems().getFirst());
                available.fireEvent(new ActionEvent());
                ((Button) loader.getNamespace().get("assignMorphSliderPresetButton")).fire();
                assertEquals(List.of("Alpha"), flow.frame().snapshot().getNpcMorphAssignments().stream()
                        .filter(npc -> npc.getEditorId().equals("Mira01"))
                        .findFirst().orElseThrow().getSliderPresetNames());

                @SuppressWarnings("unchecked")
                ComboBox<com.asdasfa.jbs2bg.workbench.morphs.MorphsFeature.NpcSortOrder> sort =
                        (ComboBox<com.asdasfa.jbs2bg.workbench.morphs.MorphsFeature.NpcSortOrder>)
                                loader.getNamespace().get("npcMorphAssignmentSort");
                sort.setValue(com.asdasfa.jbs2bg.workbench.morphs.MorphsFeature.NpcSortOrder.PLUGIN_DESCENDING);
                sort.fireEvent(new ActionEvent());
                assertEquals("Mira01", npcs.getSelectionModel().getSelectedItem().getEditorId());

                TextField filter = (TextField) loader.getNamespace().get("npcMorphAssignmentFilter");
                filter.setText("lyra");
                assertNull(npcs.getSelectionModel().getSelectedItem());
                assertEquals(List.of("Lyra"), npcs.getItems().stream()
                        .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
                platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CLEAR);
                ((Button) loader.getNamespace().get("clearNpcMorphAssignmentsButton")).fire();
                assertEquals(List.of("Mira"), flow.frame().snapshot().getNpcMorphAssignments().stream()
                        .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
                filter.clear();
                assertEquals(List.of("Mira"), controller.npcMorphAssignmentListNode().getItems().stream()
                        .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
                assertNull(controller.npcMorphAssignmentListNode().getSelectionModel().getSelectedItem());
            } finally {
                stage.close();
            }
        });
    }

    /**
     * Refilling an empty Morphs list replaces only its JavaFX adapter so the Windows UIA provider receives a fresh
     * virtualized child subtree without changing feature identity state.
     */
    @Test
    void morphsEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, new RecordingPlatform());
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> initial =
                    (ListView<CustomMorphTargetSnapshot>) loader.getNamespace().get("customMorphTargetList");
            TextField name = (TextField) loader.getNamespace().get("customMorphTargetNameInput");

            name.setText("All|Female");
            ((Button) loader.getNamespace().get("createCustomMorphTargetButton")).fire();

            assertNotSame(initial, controller.customMorphTargetListNode());
            assertEquals(List.of("All|Female"), controller.customMorphTargetListNode().getItems().stream()
                    .map(CustomMorphTargetSnapshot::getName).toList());
            stage.close();
        });
    }

    /**
     * Refilling an empty assigned-relationship list replaces only its JavaFX adapter so Windows UIA exposes the
     * immutable relationships selected from a later target frame.
     */
    @Test
    void morphsAssignedRelationshipsEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(CustomMorphTargetEdits.create("All|Female", List.of("Alpha")));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, new RecordingPlatform());
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> initial =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("assignedMorphSliderPresetList");
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> targets =
                    (ListView<CustomMorphTargetSnapshot>) loader.getNamespace().get("customMorphTargetList");

            targets.getSelectionModel().selectFirst();

            assertNotSame(initial, controller.assignedMorphSliderPresetListNode());
            assertEquals(List.of("Alpha"), controller.assignedMorphSliderPresetListNode().getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            stage.close();
        });
    }

    /**
     * The Settings Area edits both profiles through immutable feature frames, persists them as one pair, and records
     * one durable save result without routing through a legacy controller.
     */
    @Test
    void settingsAreaEditsAndPersistsBothProfilesThroughTheWorkbench() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        flow.apply(SliderPresetEdits.create("Settings Refresh"));
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ComboBox<SettingsFeature.Profile> profiles =
                    (ComboBox<SettingsFeature.Profile>) loader.getNamespace().get("settingsProfileChoice");
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            TextField small = (TextField) loader.getNamespace().get("settingsSmallInput");
            TextField multiplier = (TextField) loader.getNamespace().get("settingsMultiplierInput");
            Button apply = (Button) loader.getNamespace().get("applySettingsEntryButton");

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
            ((Button) loader.getNamespace().get("saveSettingsButton")).fire();
            multiplier.setText("9");
            apply.fire();

            assertTrue(jobs.frame().active());
            assertEquals(1f, Settings.getMultiplier("Waist"));
            assertEquals(1f, Settings.getMultiplierUUNP("Arms"));
            assertEquals("3.0", multiplier.getText());
            assertTrue(((ListView<?>) loader.getNamespace().get("activityList")).getItems().isEmpty());
        });

        Thread settingsWorker = worker.runNextAsync();
        settingsWorker.join();
        assertEquals(2f, Settings.getMultiplier("Waist"));
        assertEquals(3f, Settings.getMultiplierUUNP("Arms"));

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

        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        assertEquals(2f, Settings.getMultiplier("Waist"));
        assertEquals(3f, Settings.getMultiplierUUNP("Arms"));
    }

    /** The generation preference remains unchanged until its JavaFX-captured worker operation executes. */
    @Test
    void generationPreferenceToggleRunsOnTheApplicationWorker() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
        assertTrue(initialized.isSuccessful());
        GenerationPreferencesStore store = new GenerationPreferencesStore(temporaryDirectory);
        store.save(false);
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The deterministic preference write settles before prolonged cancellation is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);

            ((CheckBox) loader.getNamespace().get("omitRedundantSlidersCheck")).fire();

            assertTrue(jobs.frame().active());
            assertEquals("omitRedundantSliders=false" + System.lineSeparator(),
                    Files.readString(temporaryDirectory.resolve("workbench-generation.properties")));
        });

        Thread preferenceWorker = worker.runNextAsync();
        preferenceWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            assertFalse(jobs.frame().active());
            assertTrue(((CheckBox) loaderReference.get().getNamespace()
                    .get("omitRedundantSlidersCheck")).isSelected());
            stageReference.get().close();
        });

        assertEquals("omitRedundantSliders=true" + System.lineSeparator(),
                Files.readString(temporaryDirectory.resolve("workbench-generation.properties")));
    }

    /** Reload returns immediately on JavaFX and applies the recovered pair only after worker and publication lanes. */
    @Test
    void settingsReloadRunsOnTheApplicationWorkerBeforePublishingItsFrame() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        flow.apply(SliderPresetEdits.create("Reload Refresh"));
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, initialized);
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
            ((Button) loaderReference.get().getNamespace().get("reloadSettingsButton")).fire();

            assertTrue(jobs.frame().active());
            assertEquals(1f, Settings.getMultiplier("Waist"));
        });

        Thread settingsWorker = worker.runNextAsync();
        settingsWorker.join();
        assertEquals(4f, Settings.getMultiplier("Waist"));

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            FXMLLoader loader = loaderReference.get();
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
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

    /** Reload cancellation while another process owns the lock keeps the Settings draft and feedback unchanged. */
    @Test
    void settingsReloadCanBeCancelledWhileWaitingForTheDirectoryLock() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        byte[] standardBefore = Files.readAllBytes(temporaryDirectory.resolve("settings.json"));
        byte[] uunpBefore = Files.readAllBytes(temporaryDirectory.resolve("settings_UUNP.json"));
        Path ready = temporaryDirectory.resolve("holder.ready");
        Path release = temporaryDirectory.resolve("holder.release");
        String javaName = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaName).toString();
        String testClassPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process holder = new ProcessBuilder(javaExecutable, "-cp", testClassPath,
                SettingsLockHolderProbe.class.getName(), temporaryDirectory.toString(),
                ready.toString(), release.toString()).redirectErrorStream(true).start();
        Thread reloadWorker = null;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready) && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertTrue(Files.exists(ready), "child process did not acquire the Settings lock");
            ManualExecutor worker = new ManualExecutor();
            JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                    Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                    (delay, action) -> () -> {
                        // The test observes prompt cancellation before prolonged feedback is relevant.
                    }, failure -> { throw new AssertionError("Unexpected callback failure", failure); });
            SettingsFeature feature = new SettingsFeature(temporaryDirectory, Settings.publishedState());
            assertTrue(feature.dispatch(new SettingsFeature.EditEntry(
                    "Waist", "Waist", Optional.of("0"), Optional.of("1"), Optional.of("2"), false)).accepted());
            SettingsFeature.ReloadConfirmationEffect confirmation = assertInstanceOf(
                    SettingsFeature.ReloadConfirmationEffect.class,
                    feature.dispatch(new SettingsFeature.Reload()).effect().orElseThrow());
            SettingsFeature.ReloadEffect effect = assertInstanceOf(SettingsFeature.ReloadEffect.class,
                    feature.respondReload(confirmation.token(), SettingsFeature.ReloadDecision.DISCARD)
                            .effect().orElseThrow());
            AtomicReference<SettingsFeature.Frame> settledFrame = new AtomicReference<>();
            JobCoordinator.Operation operation = new JobCoordinator.Operation("Reload Settings",
                    List.of(temporaryDirectory.toString()), List.of(), Optional.empty());
            assertTrue(jobs.submit(new JobCoordinator.Submission<>(operation,
                    context -> WorkbenchController.runSettingsEffect(effect, context),
                    (attempt, result) -> settledFrame.set(
                            WorkbenchController.applySettingsCompletion(feature, effect, result).frame()),
                    Optional.empty())).admitted());
            reloadWorker = worker.runNextAsync();
            long workerDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean waitingForLock = false;
            while (!waitingForLock && reloadWorker.isAlive() && System.nanoTime() < workerDeadline) {
                for (StackTraceElement frame : reloadWorker.getStackTrace()) {
                    if (frame.getClassName().equals("com.asdasfa.jbs2bg.data.SettingsDirectoryLock")
                            && frame.getMethodName().equals("acquire")) {
                        waitingForLock = true;
                        break;
                    }
                }
                if (!waitingForLock)
                    Thread.sleep(10);
            }
            assertTrue(waitingForLock, "Reload worker did not reach the blocking directory lock");

            AtomicReference<JobCoordinator.CancelResponse> cancelResponse = new AtomicReference<>();
            Thread canceller = new Thread(() -> cancelResponse.set(jobs.requestCancel()),
                    "settings-reload-canceller");
            canceller.start();
            canceller.join(TimeUnit.SECONDS.toMillis(2));
            boolean promptCancellation = !canceller.isAlive();
            if (!promptCancellation) {
                // Release the child lock so an interrupted Windows file-lock call cannot strand the test process.
                Files.writeString(release, "release");
                canceller.join(TimeUnit.SECONDS.toMillis(5));
            }
            assertTrue(promptCancellation, "requestCancel blocked while another process held the Settings lock");
            assertEquals(JobCoordinator.CancelResponse.ACCEPTED, cancelResponse.get());
            reloadWorker.join(TimeUnit.SECONDS.toMillis(5));
            assertFalse(reloadWorker.isAlive(), "cancelled Reload remained blocked on the directory lock");
            assertTrue(holder.isAlive(), "Reload only cancelled after the other process released its lock");
            assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            SettingsFeature.Frame frame = settledFrame.get();
            assertNotNull(frame);
            assertTrue(frame.dirty());
            assertEquals("2.0", frame.editor().orElseThrow().multiplier());
            assertTrue(frame.notices().stream().noneMatch(notice ->
                    notice.code().equals("SETTINGS_LOCK_FAILED")));
            assertArrayEquals(standardBefore, Files.readAllBytes(temporaryDirectory.resolve("settings.json")));
            assertArrayEquals(uunpBefore, Files.readAllBytes(temporaryDirectory.resolve("settings_UUNP.json")));
        } finally {
            if (reloadWorker != null && reloadWorker.isAlive()) {
                reloadWorker.interrupt();
                reloadWorker.join(TimeUnit.SECONDS.toMillis(5));
            }
            Files.writeString(release, "release");
            if (!holder.waitFor(10, TimeUnit.SECONDS)) {
                holder.destroyForcibly();
                holder.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    /** Dirty Reload cancellation retains the draft, while Save persists it before the original Reload continues. */
    @Test
    void dirtySettingsReloadConfirmsAndSavesBeforeReloading() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CANCEL);
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
        });
        Files.writeString(temporaryDirectory.resolve("settings.json"),
                "{\"Defaults\":{\"Waist\":{\"valueSmall\":0.5,\"valueBig\":1}},"
                        + "\"Multipliers\":{\"Waist\":4},\"Inverted\":[]}");
        Files.writeString(temporaryDirectory.resolve("settings_UUNP.json"),
                "{\"Defaults\":{},\"Multipliers\":{},\"Inverted\":[]}");

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = loaderReference.get();
            Button reload = (Button) loader.getNamespace().get("reloadSettingsButton");
            reload.fire();

            assertFalse(jobs.frame().active());
            assertFalse(((Button) loader.getNamespace().get("saveSettingsButton")).isDisabled());
            assertEquals("2.0", ((TextField) loader.getNamespace().get("settingsMultiplierInput")).getText());

            reload.fire();

            assertTrue(jobs.frame().active());
            assertEquals("Save Settings", jobs.frame().attempt().orElseThrow().operation().name());
            assertEquals("2.0", ((TextField) loader.getNamespace().get("settingsMultiplierInput")).getText());
        });

        Thread saveWorker = worker.runNextAsync();
        saveWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            assertTrue(jobs.frame().active());
            assertEquals("Reload Settings", jobs.frame().attempt().orElseThrow().operation().name());
            assertEquals(2f, Settings.getMultiplier("Waist"));
        });

        Thread reloadWorker = worker.runNextAsync();
        reloadWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            FXMLLoader loader = loaderReference.get();
            assertFalse(jobs.frame().active());
            assertEquals(2f, Settings.getMultiplier("Waist"));
            assertTrue(((Button) loader.getNamespace().get("saveSettingsButton")).isDisabled());
            assertEquals("2.0", ((TextField) loader.getNamespace().get("settingsMultiplierInput")).getText());
            stageReference.get().close();
        });
    }

    /** Dirty Reload Discard admits Reload directly and replaces the retained draft only after worker completion. */
    @Test
    void dirtySettingsReloadDiscardAdmitsReloadWithoutSaving() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.DISCARD);
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
        });
        Files.writeString(temporaryDirectory.resolve("settings.json"),
                "{\"Defaults\":{\"Waist\":{\"valueSmall\":0.5,\"valueBig\":1}},"
                        + "\"Multipliers\":{\"Waist\":4},\"Inverted\":[]}");
        Files.writeString(temporaryDirectory.resolve("settings_UUNP.json"),
                "{\"Defaults\":{},\"Multipliers\":{},\"Inverted\":[]}");

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = loaderReference.get();
            ((Button) loader.getNamespace().get("reloadSettingsButton")).fire();

            assertTrue(jobs.frame().active());
            assertEquals("Reload Settings", jobs.frame().attempt().orElseThrow().operation().name());
            assertEquals(1f, Settings.getMultiplier("Waist"));
            assertEquals("2.0", ((TextField) loader.getNamespace().get("settingsMultiplierInput")).getText());
        });

        Thread reloadWorker = worker.runNextAsync();
        reloadWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            FXMLLoader loader = loaderReference.get();
            assertFalse(jobs.frame().active());
            assertEquals(4f, Settings.getMultiplier("Waist"));
            assertTrue(((Button) loader.getNamespace().get("saveSettingsButton")).isDisabled());
            assertEquals("4.0", ((TextField) loader.getNamespace().get("settingsMultiplierInput")).getText());
            stageReference.get().close();
        });
    }

    /** A shutdown request racing Save completion cancels the chained Reload without a callback failure. */
    @Test
    void shutdownRaceCancelsChainedSettingsReloadWithoutCallbackFailure() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The Save reaches commit before shutdown races its chained Reload admission.
                }, callbackFailure::set);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
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
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
            ((Button) loader.getNamespace().get("reloadSettingsButton")).fire();

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
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
                "BS2BG Preview", ProjectSessions.create(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selectedSources(List.of(source)));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            stage.show();
            Button importButton = (Button) loader.getNamespace().get("importBodySlideButton");
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> presets =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("sliderPresetList");
            importButton.requestFocus();

            importButton.fire();

            assertTrue(jobs.frame().active());
            assertTrue(importButton.isDisabled());
            assertSame(presets, scene.getFocusOwner());
            worker.runNext();

            ListView<SliderPresetSnapshot> importedPresets = controller.sliderPresetListNode();
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Files.writeString(temporaryDirectory.resolve("settings.json"), "{\"Defaults\":");
        Settings.InitializationResult rejected = Settings.initialize(temporaryDirectory);
        assertFalse(rejected.isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, rejected);

            assertTrue(((Button) loader.getNamespace().get("importBodySlideButton")).isDisabled());
            assertTrue(((Button) loader.getNamespace().get("generateOutputButton")).isDisabled());
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
     * The Templates JavaFX adapter renders immutable feature frames and translates controls into typed feature intents
     * without retaining a row-index selection.
     */
    @Test
    void templatesControlsCreateAndFilterThroughTheAuthoritativeProjectFlow() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, new RecordingPlatform());
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> presets =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("sliderPresetList");
            TextField name = (TextField) loader.getNamespace().get("sliderPresetNameInput");
            TextField filter = (TextField) loader.getNamespace().get("sliderPresetFilter");
            Button create = (Button) loader.getNamespace().get("createSliderPresetButton");

            assertEquals(List.of("Alpha"), presets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            name.setText(" Beta ");
            create.fire();
            assertEquals(List.of("Alpha", "Beta"), presets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertEquals("Beta", presets.getSelectionModel().getSelectedItem().getName());

            filter.setText("alp");
            assertEquals(List.of("Alpha"), presets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertNull(presets.getSelectionModel().getSelectedItem());
            filter.clear();
            assertEquals(List.of("Alpha", "Beta"), presets.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertNull(presets.getSelectionModel().getSelectedItem());
            assertEquals(flow.frame().sequence(), controller.templatesFrame().projectSequence());
            assertSame(root, loader.getRoot());
            stage.close();
        });
    }

    /**
     * The in-place Templates editor renders one accessible group per immutable Slider choice, keeps focus on a row
     * through its authoritative edit, and switches profile without losing the selected Slider Preset identity.
     */
    @Test
    void templatesEditorRendersAccessibleRowsAndPreservesFocusAcrossEdits() throws Exception {
        SettingsTestSupport.installDefaults(
                Map.of("Waist", new DefaultSliderValue(0.2f, 0.8f)),
                Map.of("Arms", new DefaultSliderValue(0.1f, 0.5f)));
        try {
            WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
            flow.apply(SliderPresetEdits.create("Alpha"));

            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
                Parent root = loader.load();
                WorkbenchController controller = loader.getController();
                Stage stage = new Stage();
                Scene scene = new Scene(root, 1300.0, 800.0);
                stage.setScene(scene);
                controller.attach(flow, stage, new RecordingPlatform());
                stage.show();
                @SuppressWarnings("unchecked")
                ListView<SliderPresetSnapshot> presets =
                        (ListView<SliderPresetSnapshot>) loader.getNamespace().get("sliderPresetList");
                presets.getSelectionModel().select(0);
                VBox rows = (VBox) loader.getNamespace().get("sliderChoiceRows");
                SliderChoiceRow waist = (SliderChoiceRow) rows.getChildren().getFirst();

                assertEquals("Slider choice Waist in Slider Preset Alpha", waist.getAccessibleText());
                assertEquals("Waist@0.8", waist.previewControl().getText());
                assertEquals("Waist Minimum in Slider Preset Alpha", waist.minimumControl().getAccessibleText());
                waist.minimumControl().requestFocus();
                assertEquals(SliderChoiceRow.FocusControl.MINIMUM,
                        waist.focusedControl(scene.getFocusOwner()).orElseThrow());
                assertSame(waist.minimumControl(), waist.control(SliderChoiceRow.FocusControl.MINIMUM));
                CheckBox enabled = waist.enabledControl();
                enabled.requestFocus();
                enabled.fire();

                assertSame(enabled, scene.getFocusOwner());
                assertFalse(flow.frame().snapshot().getSliderPresets().getFirst().getSliderChoices().getFirst().isEnabled());
                assertEquals(0, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());
                assertTrue(((Label) loader.getNamespace().get("statusText")).getText().contains("changed"));

                @SuppressWarnings("unchecked")
                ComboBox<TemplatesFeature.Profile> profile =
                        (ComboBox<TemplatesFeature.Profile>) loader.getNamespace().get("sliderPresetProfile");
                profile.setValue(TemplatesFeature.Profile.UUNP);
                profile.fireEvent(new ActionEvent());

                assertEquals("Alpha", presets.getSelectionModel().getSelectedItem().getName());
                assertEquals("Arms", ((SliderChoiceRow) rows.getChildren().getFirst()).choiceName());
                stage.close();
            });
        } finally {
            SettingsTestSupport.restoreRepositorySettings();
        }
    }

    /**
     * Inspector gang controls apply one atomic enabled-row edit, remain mutually exclusive, lock row editors while
     * active, and retain focus on the initiating bulk action.
     */
    @Test
    void templatesGangControlsAreAtomicExclusiveAndKeyboardReachable() throws Exception {
        SettingsTestSupport.installDefaults(
                Map.of("Arms", new DefaultSliderValue(0.1f, 0.9f),
                        "Waist", new DefaultSliderValue(0.2f, 0.8f)),
                Map.of());
        try {
            WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
            flow.apply(SliderPresetEdits.create("Alpha"));

            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
                Parent root = loader.load();
                WorkbenchController controller = loader.getController();
                Stage stage = new Stage();
                Scene scene = new Scene(root, 1300.0, 800.0);
                stage.setScene(scene);
                controller.attach(flow, stage, new RecordingPlatform());
                stage.show();
                @SuppressWarnings("unchecked")
                ListView<SliderPresetSnapshot> presets =
                        (ListView<SliderPresetSnapshot>) loader.getNamespace().get("sliderPresetList");
                presets.getSelectionModel().select(0);
                VBox rows = (VBox) loader.getNamespace().get("sliderChoiceRows");
                Button fiftyAll = (Button) loader.getNamespace().get("fiftyAllSliderChoicesButton");
                CheckBox minimumGang = (CheckBox) loader.getNamespace().get("gangMinimumCheck");
                CheckBox maximumGang = (CheckBox) loader.getNamespace().get("gangMaximumCheck");

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
        } finally {
            SettingsTestSupport.restoreRepositorySettings();
        }
    }

    /**
     * F2 exposes one inline row editor whose rejected diagnostics retain the draft; a valid retry restores focusable
     * selection to the renamed identity and the final Esc tier clears it without retargeting.
     */
    @Test
    void templatesInlineRenameIsKeyboardCompleteAcrossRejectionAndSelectionClear() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new RecordingPlatform());
            stage.show();
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> presets =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("sliderPresetList");
            presets.getSelectionModel().select(0);

            sendKey(root, KeyCode.F2);
            root.applyCss();
            root.layout();
            TextField rename = controller.activeRenameField().orElseThrow();
            rename.setText("Beta");
            rename.fireEvent(new ActionEvent());

            assertEquals("Beta", controller.templatesFrame().rename().orElseThrow().draft());
            assertFalse(controller.templatesFrame().rename().orElseThrow().diagnostics().isEmpty());
            assertEquals("Alpha", presets.getSelectionModel().getSelectedItem().getName());
            assertTrue(((HBox) loader.getNamespace().get("templatesInfoBar")).isVisible());
            assertFalse(((HBox) loader.getNamespace().get("infoBar")).isVisible());
            assertEquals(1, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());
            assertTrue(((Label) loader.getNamespace().get("statusText")).getText().contains("Validation"));

            rename.setText("Gamma");
            rename.fireEvent(new ActionEvent());

            assertTrue(controller.templatesFrame().rename().isEmpty());
            assertEquals("Gamma", presets.getSelectionModel().getSelectedItem().getName());
            sendKey(root, KeyCode.ESCAPE);
            assertNull(presets.getSelectionModel().getSelectedItem());
            assertTrue(controller.templatesFrame().selection().isEmpty());
            stage.close();
        });
    }

    /**
     * Templates destructive commands publish typed confirmations first, honor Cancel, and apply the captured selected
     * or visible identity set only after the matching named action.
     */
    @Test
    void templatesRemoveAndClearVisibleRequireSafeTypedConfirmation() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Alpha"));
        flow.apply(SliderPresetEdits.create("Beta"));
        flow.apply(SliderPresetEdits.create("Gamma"));
        RecordingPlatform platform = new RecordingPlatform();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> presets =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("sliderPresetList");
            Button remove = (Button) loader.getNamespace().get("removeSliderPresetButton");
            Button clear = (Button) loader.getNamespace().get("clearSliderPresetsButton");
            TextField filter = (TextField) loader.getNamespace().get("sliderPresetFilter");
            presets.getSelectionModel().select(0);

            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CANCEL);
            remove.fire();
            assertEquals(List.of("Alpha", "Beta", "Gamma"), flow.frame().snapshot().getSliderPresets().stream()
                    .map(SliderPresetSnapshot::getName).toList());

            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.REMOVE);
            remove.fire();
            assertEquals(List.of("Beta", "Gamma"), flow.frame().snapshot().getSliderPresets().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertNull(presets.getSelectionModel().getSelectedItem());

            filter.setText("bet");
            platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CLEAR);
            clear.fire();
            assertEquals(List.of("Gamma"), flow.frame().snapshot().getSliderPresets().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertTrue(controller.templatesFrame().visiblePresets().isEmpty());
            stage.close();
        });
    }

    /**
     * Refilling a genuinely empty Templates list replaces its JavaFX node so Windows UI Automation receives a fresh
     * virtualized child subtree instead of retaining the provider's stale empty tree.
     */
    @Test
    void templatesEmptyToNonEmptyTransitionReplacesOnlyTheListViewAdapter() throws Exception {
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            controller.attach(flow, stage, new RecordingPlatform());
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> initial =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("sliderPresetList");
            TextField name = (TextField) loader.getNamespace().get("sliderPresetNameInput");
            Button create = (Button) loader.getNamespace().get("createSliderPresetButton");

            name.setText("Alpha");
            create.fire();

            assertNotSame(initial, controller.sliderPresetListNode());
            assertEquals(List.of("Alpha"), controller.sliderPresetListNode().getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            root.applyCss();
            assertTrue(controller.sliderPresetListNode().getPrefHeight() >= 28.0);
            assertTrue(controller.sliderPresetListNode().getPrefHeight() < 56.0);
            assertEquals(28.0, controller.sliderPresetListNode().getFixedCellSize());
            assertSame(name.getParent(), controller.sliderPresetListNode().getParent());
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
        ToggleButton launcher = (ToggleButton) loader.getNamespace().get(buttonId);
        launcher.fire();
        Popup popup = (Popup) Window.getWindows().stream()
                .filter(window -> window instanceof Popup && window.isShowing()
                        && window.getScene().getRoot().lookup("#npcColumnFilterChoices") != null)
                .findFirst().orElseThrow();
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);

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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));
        platform.respondWith(WorkbenchProjectFlow.Response.cancelled());
        platform.respondWith(WorkbenchProjectFlow.Response.discard());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.cancelled());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform);
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
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
        assertTrue(initialized.isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CANCEL);
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.DISCARD);

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();

            WindowEvent closeRequest = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(closeRequest);

            assertTrue(closeRequest.isConsumed());
            assertEquals(0, platform.closeCount);
            assertFalse(((Button) loader.getNamespace().get("saveSettingsButton")).isDisabled());
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
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
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
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            publication.runNext();
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();

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

            assertEquals(2f, Settings.getMultiplier("Waist"));
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stageReference.get().close();
        });
    }

    /** An inline coordinator observes the close continuation before Save completion and closes exactly once. */
    @Test
    void dirtySettingsCloseSaveSurvivesInlineWorkerAndPublicationCompletion() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
        assertTrue(initialized.isSuccessful());
        JobCoordinator jobs = new JobCoordinator(new InlineExecutorService(), Runnable::run,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Inline completion leaves no interval for prolonged cancellation feedback.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();

            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();

            assertEquals(2f, Settings.getMultiplier("Waist"));
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stage.close();
        });
    }

    /** A failed Settings close-save retains both the dirty draft and the Workbench window. */
    @Test
    void failedDirtySettingsCloseSaveKeepsTheDraftAndWindowOpen() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
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
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
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
            assertFalse(((Button) loader.getNamespace().get("saveSettingsButton")).isDisabled());
            assertEquals(JobCoordinator.Lifecycle.FAILED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            stageReference.get().setOnCloseRequest(null);
            stageReference.get().close();
        });
    }

    /** A close-save rejected during the shutdown/render gap cannot arm a later ordinary Settings Save to close. */
    @Test
    void rejectedSettingsAdmissionDoesNotLeaveAPhantomCloseContinuation() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
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
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
            assertEquals(JobCoordinator.ShutdownResponse.READY, jobs.requestShutdown());

            ((MenuItem) loader.getNamespace().get("exitMenuItem")).fire();

            assertFalse(jobs.frame().active());
            assertFalse(flow.frame().closed());
            assertEquals(0, platform.closeCount);
            assertTrue(jobs.resumeAfterShutdown());
            publication.runNext();
            publication.runNext();
            ((Button) loader.getNamespace().get("saveSettingsButton")).fire();
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
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
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
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            stage.show();
            ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
            TextField editor = (TextField) loader.getNamespace().get("settingsEntryNameInput");
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

            assertEquals(2f, Settings.getMultiplier("Waist"));
            assertTrue(flow.frame().closed());
            assertEquals(1, platform.closeCount);
            stageReference.get().close();
        });
    }

    /** A failed Save retry becomes ordinarily unavailable after the draft returns clean, without callback failure. */
    @Test
    void cleanSettingsDraftMakesFailedSaveRetryUnavailableWithoutTechnicalFailure() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        AtomicReference<FXMLLoader> loaderReference = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, initialized);
            publication.runNext();
            loaderReference.set(loader);
            stageReference.set(stage);
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
            ((Button) loader.getNamespace().get("saveSettingsButton")).fire();
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
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).clear();
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
            assertTrue(((Button) loader.getNamespace().get("saveSettingsButton")).isDisabled());
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
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
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
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            publication.runNext();
            stageReference.set(stage);
            stage.show();
            ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) loader.getNamespace().get("settingsMultiplierInput")).setText("2");
            ((Button) loader.getNamespace().get("applySettingsEntryButton")).fire();
            TextField editor = (TextField) loader.getNamespace().get("settingsEntryNameInput");
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
            assertFalse(((Button) loader.getNamespace().get("saveSettingsButton")).isDisabled());
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // This save settles before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
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
            controller.attach(flow, stage, platform);

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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Both deterministic jobs settle before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
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
            controller.attach(flow, stage, platform);
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

    /** A persistent preview diagnostic must not turn a successful Duplicate into failed Activity validation. */
    @Test
    void templatesDuplicateWithUnavailablePreviewRecordsTheMutation() throws Exception {
        SettingsTestSupport.installStandardOutput(Map.of("Overflow", Float.valueOf(Float.MAX_VALUE)), List.of());
        try {
            WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
            flow.apply(SliderPresetEdits.create("Alpha"));
            flow.apply(SliderPresetEdits.setSliderChoice("Alpha", new SliderChoiceSnapshot(
                    "Overflow", false, Integer.valueOf(10), Integer.valueOf(200),
                    10, 200, 100, 100, false)));

            FxTestToolkit.runOnFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
                loader.load();
                WorkbenchController controller = loader.getController();
                Stage stage = new Stage();
                try {
                    controller.attach(flow, stage, new RecordingPlatform());
                    controller.sliderPresetListNode().getSelectionModel().selectFirst();
                    ((TextField) loader.getNamespace().get("sliderPresetNameInput")).setText("Beta");
                    ((Button) loader.getNamespace().get("duplicateSliderPresetButton")).fire();

                    assertEquals(List.of("Alpha", "Beta"), flow.frame().snapshot().getSliderPresets().stream()
                            .map(SliderPresetSnapshot::getName).toList());
                    assertEquals("TEMPLATE_PREVIEW_NON_FINITE",
                            controller.templatesFrame().diagnostics().getFirst().getCode());
                    @SuppressWarnings("unchecked")
                    ListView<WorkbenchFeedback.ActivityRecord> activity =
                            (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
                    assertEquals(1, activity.getItems().size(), activity.getItems().stream()
                            .map(item -> item.operation() + ": " + item.message()).toList().toString());
                    assertEquals("Duplicate Slider Preset", activity.getItems().getFirst().operation());
                    assertEquals(WorkbenchFeedback.Disposition.COMPLETED,
                            activity.getItems().getFirst().disposition());
                } finally {
                    stage.close();
                }
            });
        } finally {
            SettingsTestSupport.restoreRepositorySettings();
        }
    }

    /** A failure during a pending Close cannot offer Retry while coordinator admission remains shut down. */
    @Test
    void pendingShutdownFailureDialogDoesNotOfferUnadmittableRetry() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The synthetic commit settles before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        RecordingPlatform platform = new RecordingPlatform();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            try {
                controller.attach(flow, stage, platform);
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("malformed-project.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "malformed-syntax.jbs2bg"), source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("retry-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "malformed-syntax.jbs2bg"), source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-29T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Each deterministic failure settles before prolonged-cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300.0, 800.0));
            try {
                controller.attach(flow, stage, new RecordingPlatform());
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
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
                "BS2BG Preview", ProjectSessions.create(), jobs);
        RecordingPlatform platform = new RecordingPlatform();
        platform.respondWith(WorkbenchProjectFlow.Response.selected(source));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            controller.attach(flow, stage, platform);
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform());
            stage.show();

            sendControlKey(root, KeyCode.DIGIT2);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());

            sendControlKey(root, KeyCode.DIGIT4);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertTrue(((ToggleButton) loader.getNamespace().get("morphsAreaButton")).isSelected());
            assertTrue(((ToggleButton) loader.getNamespace().get("outputAreaButton")).isSelected());
            VBox outputDrawer = (VBox) loader.getNamespace().get("outputDrawer");
            Slider outputHeight = (Slider) loader.getNamespace().get("outputDrawerHeight");
            assertTrue(outputDrawer.isVisible());
            outputHeight.setValue(260.0);
            assertEquals(260.0, outputDrawer.getPrefHeight());
            assertEquals(260.0, outputDrawer.getMaxHeight());

            sendControlKey(root, KeyCode.BACK_QUOTE);
            assertEquals("Morphs", ((Label) loader.getNamespace().get("areaTitle")).getText());
            assertFalse(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());

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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform());
            stage.show();

            sendControlKey(root, KeyCode.DIGIT2);
            @SuppressWarnings("unchecked")
            ListView<CustomMorphTargetSnapshot> primary =
                    (ListView<CustomMorphTargetSnapshot>) loader.getNamespace().get("customMorphTargetList");
            Label editor = (Label) loader.getNamespace().get("morphTargetEditorFocusTarget");
            Label inspector = (Label) loader.getNamespace().get("morphTargetSelectionText");
            Label output = (Label) loader.getNamespace().get("outputFocusTarget");
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
            assertFalse(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());
            stage.close();
        });
    }

    /** Ctrl+G publishes all captured Output tabs through the central job without moving editor focus. */
    @Test
    void generateRendersReadOnlyOutputTabsWithoutStealingFocus() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-31T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // This small generated Project settles before prolonged cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        flow.apply(SliderPresetEdits.create("Generated UI"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, initialized);
            stage.show();

            Label editor = (Label) loader.getNamespace().get("templateEditorFocusTarget");
            editor.requestFocus();
            sendControlKey(root, KeyCode.G);

            Button generate = (Button) loader.getNamespace().get("generateOutputButton");
            assertTrue(jobs.frame().active());
            assertTrue(generate.isDisabled());
            worker.runNext();

            assertTrue(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());
            TabPane tabs = (TabPane) loader.getNamespace().get("outputTabs");
            TextArea templates = (TextArea) loader.getNamespace().get("templatesOutputText");
            TextArea morphs = (TextArea) loader.getNamespace().get("morphsOutputText");
            TextArea bos = (TextArea) loader.getNamespace().get("bosOutputText");
            assertEquals(3, tabs.getTabs().size());
            assertTrue(templates.getText().startsWith("Generated UI="));
            assertEquals("", morphs.getText());
            assertFalse(bos.getText().isEmpty());
            for (TextArea output : List.of(templates, morphs, bos)) {
                assertFalse(output.isEditable());
                assertTrue(output.isFocusTraversable());
            }
            assertSame(editor, scene.getFocusOwner());
            stage.close();
        });
    }

    /** Output controls realize accepted copy and complete/selected export effects without reading editable UI state. */
    @Test
    void copyAndExportOutputUseThePlatformAdapterAndCentralJobPath() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, Runnable::run,
                Clock.fixed(Instant.parse("2026-08-31T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // These small export batches settle before prolonged cancellation feedback is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected coordinator callback failure", failure);
        });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow(
                "BS2BG Preview", ProjectSessions.create(), jobs);
        flow.apply(SliderPresetEdits.create("Platform Output"));
        RecordingPlatform platform = new RecordingPlatform();
        Path completeDirectory = Files.createDirectory(temporaryDirectory.resolve("complete-output"));
        Path selectedFile = temporaryDirectory.resolve("selected.JSON");
        platform.respondOutputDirectoryWith(Optional.of(completeDirectory));
        platform.respondOutputFileWith(Optional.of(selectedFile));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, platform, temporaryDirectory, initialized);
            stage.show();
            Label editor = (Label) loader.getNamespace().get("templateEditorFocusTarget");
            editor.requestFocus();
            ((Button) loader.getNamespace().get("generateOutputButton")).fire();
            worker.runNext();

            TextArea templates = (TextArea) loader.getNamespace().get("templatesOutputText");
            Button copy = (Button) loader.getNamespace().get("copyOutputButton");
            Button exportAll = (Button) loader.getNamespace().get("exportOutputButton");
            assertFalse(copy.isDisabled());
            assertFalse(exportAll.isDisabled());
            copy.fire();
            assertEquals(List.of(templates.getText()), platform.clipboardTexts);
            assertEquals("templates.ini copied to the clipboard.",
                    ((Label) loader.getNamespace().get("infoBarMessage")).getText());
            assertSame(editor, scene.getFocusOwner());

            sendControlKey(root, KeyCode.E);
            assertTrue(jobs.frame().active());
            assertTrue(exportAll.isDisabled());
            worker.runNext();
            assertArrayEquals(templates.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    Files.readAllBytes(completeDirectory.resolve("templates.ini")));
            assertSame(editor, scene.getFocusOwner());

            TabPane tabs = (TabPane) loader.getNamespace().get("outputTabs");
            tabs.getSelectionModel().select((javafx.scene.control.Tab) loader.getNamespace().get("bosOutputTab"));
            Button selectedExport = (Button) loader.getNamespace().get("exportSelectedBosButton");
            assertFalse(selectedExport.isDisabled());
            String bos = ((TextArea) loader.getNamespace().get("bosOutputText")).getText();
            selectedExport.fire();
            worker.runNext();
            assertArrayEquals(bos.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    Files.readAllBytes(selectedFile));
            assertSame(editor, scene.getFocusOwner());
            stage.close();
        });
    }

    /** A Templates edit remains available during Generate and makes its captured completion stale and invisible. */
    @Test
    void projectEditDuringGenerateProducesStaleActivityWithoutRevealingOutput() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
                "BS2BG Preview", ProjectSessions.create(), jobs);
        flow.apply(SliderPresetEdits.create("Captured UI"));

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, initialized);
            stage.show();
            Label editor = (Label) loader.getNamespace().get("templateEditorFocusTarget");
            editor.requestFocus();

            sendControlKey(root, KeyCode.G);
            TextField name = (TextField) loader.getNamespace().get("sliderPresetNameInput");
            Button create = (Button) loader.getNamespace().get("createSliderPresetButton");
            assertFalse(create.isDisabled());
            assertTrue(((Button) loader.getNamespace().get("importBodySlideButton")).isDisabled());
            name.setText("Newer UI");
            create.fire();
            worker.runNext();

            assertFalse(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());
            assertEquals("Generate Project output to inspect it here.",
                    ((TextArea) loader.getNamespace().get("templatesOutputText")).getText());
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
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
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
                "BS2BG Preview", ProjectSessions.create(), jobs);

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, initialized);
            stage.show();

            sendControlKey(root, KeyCode.G);
            Button cancel = (Button) loader.getNamespace().get("cancelOperationButton");
            assertFalse(cancel.isDisabled());
            cancel.fire();
            worker.runNext();

            assertEquals(JobCoordinator.Lifecycle.CANCELLED,
                    jobs.frame().attempt().orElseThrow().lifecycle());
            assertFalse(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());
            assertEquals("Generate Project output to inspect it here.",
                    ((TextArea) loader.getNamespace().get("templatesOutputText")).getText());
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
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform());

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

    /** Theme selection updates the live appearance before its profile write runs on the application worker. */
    @Test
    void themePersistenceRunsAfterVisualSelectionOnTheWorker() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
        assertTrue(initialized.isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // The controlled theme save settles before prolonged cancellation is relevant.
                }, failure -> { throw new AssertionError("Unexpected callback failure", failure); });
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create(), jobs);
        AtomicReference<Boolean> savedOnFxThread = new AtomicReference<>();
        AtomicReference<WorkbenchAppearance.ThemeChoice> savedChoice = new AtomicReference<>();
        AtomicReference<Stage> stageReference = new AtomicReference<>();

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), WorkbenchAppearance.ThemeChoice.SYSTEM,
                    choice -> {
                        savedChoice.set(choice);
                        savedOnFxThread.set(Platform.isFxApplicationThread());
                    }, temporaryDirectory, initialized,
                    GenerationPreferencesStore.MigrationPolicy.READ_ONLY_FALLBACK);
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
            assertNull(savedChoice.get());
        });

        Thread themeWorker = worker.runNextAsync();
        themeWorker.join();
        assertEquals(WorkbenchAppearance.ThemeChoice.LIGHT, savedChoice.get());
        assertEquals(Boolean.FALSE, savedOnFxThread.get());
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
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            BorderPane root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            Scene scene = new Scene(root, 1300, 720);
            stage.setScene(scene);
            controller.attach(flow, stage, new RecordingPlatform());
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
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("sliderPresetList");
            Label inspector = (Label) loader.getNamespace().get("templateSelectionText");

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
            assertSame(loader.getNamespace().get("npcSourceList"), scene.getFocusOwner());
            sendKey(root, KeyCode.ESCAPE);
            sendKey(root, KeyCode.F7);
            assertSame(loader.getNamespace().get("npcInspectorName"), scene.getFocusOwner());
            assertEquals("NPC Database catalog",
                    ((TableView<?>) loader.getNamespace().get("npcCatalogTable")).getAccessibleText());
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
    private static final class RefusingEditSession implements ProjectSession {
        private final ProjectSession delegate;
        private boolean refuse;
        private boolean failed;

        /** Wraps a real session so fixture setup and snapshots retain production semantics. */
        private RefusingEditSession(ProjectSession delegate) {
            this.delegate = delegate;
        }

        /** Arms the requested outcome only after fixture creation through the public Project seam. */
        private void refuseEdits(boolean failure) {
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

    /**
     * Test adapter for modal platform effects; responses are consumed in user-interaction order.
     */
    private static final class RecordingPlatform implements WorkbenchPlatform {
        private final Deque<WorkbenchProjectFlow.Response> responses = new ArrayDeque<>();
        private final Deque<WorkbenchFeedback.DialogAction> confirmationResponses = new ArrayDeque<>();
        private final Deque<WorkbenchFeedback.DialogAction> failureResponses = new ArrayDeque<>();
        private final Deque<Runnable> failureHooks = new ArrayDeque<>();
        private final Deque<Optional<Path>> outputDirectoryResponses = new ArrayDeque<>();
        private final Deque<Optional<Path>> outputFileResponses = new ArrayDeque<>();
        private final Deque<Optional<List<Path>>> npcSourceResponses = new ArrayDeque<>();
        private final List<String> clipboardTexts = new java.util.ArrayList<>();
        private WorkbenchFeedback.DialogSpec failureSpec;
        private int closeCount;

        /**
         * Adds the next chooser or confirmation result.
         */
        void respondWith(WorkbenchProjectFlow.Response response) {
            responses.addLast(response);
        }

        /**
         * Adds the next destructive feature confirmation action.
         */
        void respondConfirmationWith(WorkbenchFeedback.DialogAction action) {
            confirmationResponses.addLast(action);
        }

        /** Adds the next failure-dialog action and a hook run immediately before that response is returned. */
        void respondFailureWith(WorkbenchFeedback.DialogAction action, Runnable beforeResponse) {
            failureResponses.addLast(action);
            failureHooks.addLast(beforeResponse);
        }

        /** Adds the next Output directory-chooser result. */
        void respondOutputDirectoryWith(Optional<Path> response) {
            outputDirectoryResponses.addLast(response);
        }

        /** Adds the next selected-BoS save-chooser result. */
        void respondOutputFileWith(Optional<Path> response) {
            outputFileResponses.addLast(response);
        }

        /** Adds the next NPC Database multi-file chooser result. */
        void respondNpcSourcesWith(Optional<List<Path>> response) {
            npcSourceResponses.addLast(response);
        }

        /** Returns the next scripted NPC Database source selection. */
        @Override
        public Optional<List<Path>> chooseNpcSources(Stage owner) {
            return npcSourceResponses.removeFirst();
        }

        /**
         * Returns the next scripted real-platform result.
         */
        @Override
        public WorkbenchProjectFlow.Response complete(WorkbenchProjectFlow.Effect effect, Stage owner) {
            return responses.removeFirst();
        }

        /**
         * Returns the next scripted destructive feature confirmation.
         */
        @Override
        public WorkbenchFeedback.DialogAction completeConfirmation(WorkbenchFeedback.DialogSpec spec, Stage owner) {
            return confirmationResponses.removeFirst();
        }

        /** Records the offered failure actions and runs the scripted hook before returning a response. */
        @Override
        public WorkbenchFeedback.DialogAction completeFailure(WorkbenchFeedback.DialogSpec spec, Stage owner) {
            failureSpec = spec;
            if (failureResponses.isEmpty())
                return WorkbenchFeedback.DialogAction.CLOSE;
            failureHooks.removeFirst().run();
            return failureResponses.removeFirst();
        }

        /** Records accepted clipboard text without consulting a JavaFX TextArea. */
        @Override
        public boolean copyOutputText(String text) {
            clipboardTexts.add(text);
            return true;
        }

        /** Returns the next scripted complete-batch directory. */
        @Override
        public Optional<Path> chooseOutputDirectory(Stage owner) {
            return outputDirectoryResponses.removeFirst();
        }

        /** Returns the next scripted selected-BoS destination. */
        @Override
        public Optional<Path> chooseOutputFile(String suggestedFileName, Stage owner) {
            return outputFileResponses.removeFirst();
        }

        /**
         * Records the at-most-once final close effect without closing the test Stage.
         */
        @Override
        public void closeWindow(Stage owner) {
            closeCount++;
        }
    }

}
