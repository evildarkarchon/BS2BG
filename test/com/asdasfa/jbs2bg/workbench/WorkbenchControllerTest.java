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
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.ProjectLifecycleStatus;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;
import com.asdasfa.jbs2bg.workbench.settings.SettingsFeature;
import com.asdasfa.jbs2bg.workbench.templates.TemplatesFeature;

import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleRole;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchControllerTest {

    @TempDir
    Path temporaryDirectory;

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
            @SuppressWarnings("unchecked")
            ListView<SliderPresetSnapshot> assigned =
                    (ListView<SliderPresetSnapshot>) loader.getNamespace().get("assignedMorphSliderPresetList");

            assertTrue(targets.isVisible());
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

            assertEquals(List.of("Alpha"), assigned.getItems().stream()
                    .map(SliderPresetSnapshot::getName).toList());
            assertEquals(List.of("Alpha"), flow.frame().snapshot().getCustomMorphTargets().stream()
                    .filter(target -> target.getName().equals("Existing")).findFirst().orElseThrow()
                    .getSliderPresetNames());
            filter.setText("all");
            assertNull(targets.getSelectionModel().getSelectedItem());
            assertTrue(assigned.getItems().isEmpty());
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

            targets.getSelectionModel().select(targets.getItems().stream()
                    .filter(target -> target.getName().equals("Existing")).findFirst().orElseThrow());
            available.setValue(available.getItems().getFirst());
            available.fireEvent(new ActionEvent());
            assign.fire();
            assigned.getSelectionModel().selectFirst();
            removeAssignment.fire();
            assertTrue(assigned.getItems().isEmpty());

            available.setValue(available.getItems().getFirst());
            available.fireEvent(new ActionEvent());
            assign.fire();
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

    /** A blocking startup Settings diagnostic becomes durable Activity and prevents imports from consuming empties. */
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
            @SuppressWarnings("unchecked")
            ListView<WorkbenchFeedback.ActivityRecord> activity =
                    (ListView<WorkbenchFeedback.ActivityRecord>) loader.getNamespace().get("activityList");
            assertEquals(1, activity.getItems().size());
            assertTrue(activity.getItems().getFirst().message().contains("SETTINGS_JSON_MALFORMED"));
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
            loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
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
            assertEquals(30.0, controller.sliderPresetListNode().getPrefHeight());
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
        private final List<String> clipboardTexts = new java.util.ArrayList<>();
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

        /** Runs the scripted boundary hook before returning the next failure action. */
        @Override
        public WorkbenchFeedback.DialogAction completeFailure(WorkbenchFeedback.DialogSpec spec, Stage owner) {
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
