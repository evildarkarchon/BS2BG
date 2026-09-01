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
import java.util.Optional;

import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
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
     * The Settings Area edits both profiles through immutable feature frames, persists them as one pair, and records
     * one durable save result without routing through a legacy controller.
     */
    @Test
    void settingsAreaEditsAndPersistsBothProfilesThroughTheWorkbench() throws Exception {
        Settings.InitializationResult initialized = Settings.initialize(temporaryDirectory);
        assertTrue(initialized.isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        FxTestToolkit.runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("workbench.fxml"));
            Parent root = loader.load();
            WorkbenchController controller = loader.getController();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1300, 720));
            controller.attach(flow, stage, new RecordingPlatform(), temporaryDirectory, initialized);
            ((ToggleButton) loader.getNamespace().get("settingsAreaButton")).fire();
            @SuppressWarnings("unchecked")
            ComboBox<SettingsFeature.Profile> profiles =
                    (ComboBox<SettingsFeature.Profile>) loader.getNamespace().get("settingsProfileChoice");
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) loader.getNamespace().get("settingsEntryList");
            TextField multiplier = (TextField) loader.getNamespace().get("settingsMultiplierInput");
            Button apply = (Button) loader.getNamespace().get("applySettingsEntryButton");

            profiles.setValue(SettingsFeature.Profile.STANDARD);
            profiles.fireEvent(new ActionEvent());
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            multiplier.setText("2");
            apply.fire();

            profiles.setValue(SettingsFeature.Profile.UUNP);
            profiles.fireEvent(new ActionEvent());
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Arms")).findFirst().orElseThrow());
            multiplier.setText("3");
            apply.fire();
            CheckBox omit = (CheckBox) loader.getNamespace().get("omitRedundantSlidersCheck");
            omit.fire();
            ((Button) loader.getNamespace().get("saveSettingsButton")).fire();

            assertEquals(2f, Settings.getMultiplier("Waist"));
            assertEquals(3f, Settings.getMultiplierUUNP("Arms"));
            assertEquals(1, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());
            assertTrue(((Label) loader.getNamespace().get("statusText")).getText().contains("Settings saved"));
            stage.close();
        });

        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        assertEquals(2f, Settings.getMultiplier("Waist"));
        assertEquals(3f, Settings.getMultiplierUUNP("Arms"));
        assertTrue(new GenerationPreferencesStore(temporaryDirectory).loadOrMigrate());
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
            assertTrue(activity.getItems().get(0).message().contains("SETTINGS_JSON_MALFORMED"));
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
                SliderChoiceRow waist = (SliderChoiceRow) rows.getChildren().get(0);

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
                assertFalse(flow.frame().snapshot().getSliderPresets().get(0).getSliderChoices().get(0).isEnabled());
                assertEquals(0, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());
                assertTrue(((Label) loader.getNamespace().get("statusText")).getText().contains("changed"));

                @SuppressWarnings("unchecked")
                ComboBox<TemplatesFeature.Profile> profile =
                        (ComboBox<TemplatesFeature.Profile>) loader.getNamespace().get("sliderPresetProfile");
                profile.setValue(TemplatesFeature.Profile.UUNP);
                profile.fireEvent(new ActionEvent());

                assertEquals("Alpha", presets.getSelectionModel().getSelectedItem().getName());
                assertEquals("Arms", ((SliderChoiceRow) rows.getChildren().get(0)).choiceName());
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
                assertEquals(List.of(50, 50), flow.frame().snapshot().getSliderPresets().get(0)
                        .getSliderChoices().stream().map(choice -> choice.getPercentageMinimum()).toList());
                assertEquals(List.of(50, 50), flow.frame().snapshot().getSliderPresets().get(0)
                        .getSliderChoices().stream().map(choice -> choice.getPercentageMaximum()).toList());
                assertEquals(1, ((ListView<?>) loader.getNamespace().get("activityList")).getItems().size());

                minimumGang.fire();
                assertTrue(minimumGang.isSelected());
                assertTrue(((SliderChoiceRow) rows.getChildren().get(0)).enabledControl().isDisabled());
                maximumGang.fire();

                assertFalse(minimumGang.isSelected());
                assertTrue(maximumGang.isSelected());
                assertTrue(((SliderChoiceRow) rows.getChildren().get(0)).minimumControl().isDisabled());
                maximumGang.fire();
                assertFalse(maximumGang.isSelected());
                assertFalse(((SliderChoiceRow) rows.getChildren().get(0)).enabledControl().isDisabled());
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
            Button primary = (Button) loader.getNamespace().get("primaryContentButton");
            Button editor = (Button) loader.getNamespace().get("editorButton");
            Button inspector = (Button) loader.getNamespace().get("inspectorButton");
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

    /**
     * Generated Output may reveal the drawer without moving focus away from the active editor.
     */
    @Test
    void automaticOutputRevealUsesTheProductionAdapterWithoutStealingFocus() throws Exception {
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

            Label editor = (Label) loader.getNamespace().get("templateEditorFocusTarget");
            editor.requestFocus();
            controller.revealGeneratedOutput("generated output");

            assertTrue(((VBox) loader.getNamespace().get("outputDrawer")).isVisible());
            assertEquals("generated output", ((TextArea) loader.getNamespace().get("outputText")).getText());
            assertSame(editor, scene.getFocusOwner());
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

    /**
     * Test adapter for modal platform effects; responses are consumed in user-interaction order.
     */
    private static final class RecordingPlatform implements WorkbenchPlatform {
        private final Deque<WorkbenchProjectFlow.Response> responses = new ArrayDeque<>();
        private final Deque<WorkbenchFeedback.DialogAction> confirmationResponses = new ArrayDeque<>();
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

        /**
         * Records the at-most-once final close effect without closing the test Stage.
         */
        @Override
        public void closeWindow(Stage owner) {
            closeCount++;
        }
    }

}
