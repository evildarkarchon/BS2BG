package com.asdasfa.jbs2bg.workbench.settings;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.AreaPanes;
import com.asdasfa.jbs2bg.workbench.AreaTest;
import com.asdasfa.jbs2bg.workbench.GenerationPreferencesStore;
import com.asdasfa.jbs2bg.workbench.WorkbenchEnvironment;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Landmark;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Settings Area loaded alone through its own FXML, over a real {@link SettingsFeature}, a real Settings job
 * runner on the hosted coordinator, and a recording shell port. Cross-Area Settings journeys (Settings to Templates
 * and Output) and the dirty-Settings close lifecycle stay in {@code WorkbenchControllerTest}.
 */
class SettingsAreaTest extends AreaTest<SettingsArea> {
    // The feature behind the hosted Area; the live-Settings context flag reads it, as the shell's does.
    private SettingsFeature feature;

    /** Builds the feature and runner the shell would, and loads the Area through its production loader. */
    @Override
    protected AreaLoader.Loaded<SettingsArea> load(Collaborators collaborators) {
        WorkbenchEnvironment environment = collaborators.environment();
        feature = new SettingsFeature(environment.settings(), environment.settingsStartup(),
                environment.preferencesDirectory(), environment.migrationPolicy());
        AreaLoader.Loaded<SettingsArea> loaded = SettingsArea.load(feature, collaborators.pipeline(),
                collaborators.shell());
        SettingsJobRunner runner = new SettingsJobRunner(feature, environment.settings(), collaborators.flow().jobs(),
                loaded.area()::completeJob);
        shell.runSettingsJobsWith(runner::submit);
        return loaded;
    }

    /** @return the feature's live availability, as the shell's context reads it */
    @Override
    protected boolean settingsLiveAvailable() {
        return feature.frame().liveAvailable();
    }

    /** A coordinator whose worker and publication lanes the test advances by hand. */
    private static JobCoordinator manualJobs(ManualExecutor worker, ManualExecutor publication) {
        return new JobCoordinator(worker, publication,
                Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC),
                (delay, action) -> () -> {
                    // Each deterministic Settings job settles before prolonged cancellation is relevant.
                }, failure -> {
            throw new AssertionError("Unexpected callback failure", failure);
        });
    }

    /** Selects one Standard entry and applies a multiplier edit, leaving the draft dirty. */
    private void editWaistMultiplier(String multiplier) {
        ListView<SettingsFeature.EntryFrame> entries = hosted().node("settingsEntryList");
        entries.getSelectionModel().select(entries.getItems().stream()
                .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
        hosted().<TextField>node("settingsMultiplierInput").setText(multiplier);
        hosted().<Button>node("applySettingsEntryButton").fire();
    }

    /**
     * The Area's FXML loads on its own: its non-Node root holds the three landmark panes, and the first context
     * render fills the controls from the feature's frame.
     */
    @Test
    void settingsAreaLoadsAloneIntoItsThreeLandmarkPanes() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            AreaPanes panes = host().module().panes();

            assertSame(hosted().node("settingsPrimaryScroll"), panes.getPrimary());
            assertSame(hosted().node("settingsEditorContent"), panes.getEditor());
            assertSame(hosted().node("settingsInspectorContent"), panes.getInspector());
            assertEquals(SettingsFeature.Profile.STANDARD,
                    hosted().<ComboBox<SettingsFeature.Profile>>node("settingsProfileChoice").getValue());
            assertTrue(hosted().<ListView<SettingsFeature.EntryFrame>>node("settingsEntryList").getItems().stream()
                    .anyMatch(entry -> entry.name().equals("Waist")));
            assertTrue(hosted().<Button>node("saveSettingsButton").isDisabled());
            assertTrue(shell.requests().isEmpty());
        });
    }

    /**
     * The Settings controls keep the accessible names, visible texts and nesting they had in the single Workbench
     * FXML, because the packaged smoke run finds them by name, role and parent region. The expected outline is
     * transcribed from the pre-extraction {@code workbench.fxml}.
     */
    @Test
    void settingsPanesKeepTheirAccessibleOutline() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            AreaPanes panes = host().module().panes();

            assertEquals(List.of(
                    "ScrollPane #settingsPrimaryScroll name=Settings profiles and entries",
                    "  VBox #settingsPrimaryContent",
                    "    Label text=Settings _profile:",
                    "    ComboBox #settingsProfileChoice name=Settings profile",
                    "    Label text=Settings entries",
                    "    ListView #settingsEntryList name=Settings entries",
                    "    Label text=_New entry name:",
                    "    TextField #newSettingsEntryName name=New Settings entry name",
                    "    Button #addSettingsEntryButton text=_Add Settings entry name=Add Settings entry"),
                    outline(panes.getPrimary()));
            assertEquals(List.of(
                    "VBox #settingsEditorContent",
                    "  Label text=Settings entry",
                    // The first render selects the first entry, so the validation label offers the edit hint.
                    "  Label #settingsValidationText text=Edit finite float values; blank leaves that category absent."
                            + " name=Settings validation",
                    "  Label text=_Name:",
                    "  TextField #settingsEntryNameInput name=Settings entry name",
                    "  Label text=Default _small (blank means absent):",
                    "  TextField #settingsSmallInput name=Settings small default",
                    "  Label text=Default _big (blank means absent):",
                    "  TextField #settingsBigInput name=Settings big default",
                    "  Label text=_Multiplier (blank means absent):",
                    "  TextField #settingsMultiplierInput name=Settings multiplier",
                    "  CheckBox #settingsInvertedCheck text=_Inverted name=Invert Settings entry",
                    "  Button #applySettingsEntryButton text=_Apply entry name=Apply Settings entry draft",
                    "  Button #removeSettingsEntryButton text=_Remove entry name=Remove Settings entry"),
                    outline(panes.getEditor()));
            assertEquals(List.of(
                    "VBox #settingsInspectorContent",
                    "  Label text=Settings pair",
                    "  Label text=Standard and UUNP are saved together. Arbitrary finite float values are accepted.",
                    "  Label #settingsNoticeText text=Standard and UUNP Settings are saved together."
                            + " name=Settings persistence diagnostics",
                    "  CheckBox #omitRedundantSlidersCheck text=_Omit redundant sliders in Templates output"
                            + " name=Omit redundant sliders",
                    "  Button #saveSettingsButton text=_Save Settings name=Save Standard and UUNP Settings",
                    "  Button #reloadSettingsButton text=_Reload Settings name=Reload and recover Settings"),
                    outline(panes.getInspector()));
        });
    }

    /**
     * Each landmark resolves to the Area's state-dependent node: the inspector offers Save only while the draft
     * has unsaved changes, and falls back to the persistence diagnostics otherwise.
     */
    @Test
    void focusNodesFollowTheSettingsDraft() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            SettingsArea area = host().module();

            assertSame(hosted().node("settingsEntryList"), area.focusNode(Landmark.PRIMARY_CONTENT));
            assertSame(hosted().node("settingsEntryList"), area.defaultFocusNode());
            assertSame(hosted().node("settingsEntryNameInput"), area.focusNode(Landmark.EDITOR));
            assertSame(hosted().node("settingsNoticeText"), area.focusNode(Landmark.INSPECTOR));
            assertNull(area.focusNode(Landmark.RAIL));

            editWaistMultiplier("2");

            assertSame(hosted().node("saveSettingsButton"), area.focusNode(Landmark.INSPECTOR));
        });
    }

    /**
     * An accepted draft edit is worded by the Area as status-only feedback, and the Area asks the shell to refresh
     * Output's generation settings; it reaches no other Area directly.
     */
    @Test
    void acceptedDraftEditPublishesStatusFeedbackAndRefreshesGenerationSettings() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            host();
            ListView<SettingsFeature.EntryFrame> entries = hosted().node("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            int before = shell.requests().size();
            hosted().<TextField>node("settingsMultiplierInput").setText("2");

            hosted().<Button>node("applySettingsEntryButton").fire();

            assertEquals(List.of("publishFeedback(Settings draft changed.)", "generationSettingsChanged()"),
                    shell.requests().subList(before, shell.requests().size()));
            WorkbenchFeedback.StatusProjection status = shell.feedbackFrame().status();
            assertEquals(WorkbenchFeedback.Severity.INFORMATION, status.severity());
            assertTrue(shell.feedbackFrame().activities().isEmpty());
            assertEquals("Unsaved Settings changes.", hosted().<Labeled>node("settingsNoticeText").getText());
        });
    }

    /**
     * A Save runs through the shell's job runner, and once it settles the Area asks the shell to refresh everything
     * that depends on the published Settings pair.
     */
    @Test
    void settledSaveAsksTheShellToRefreshEverySettingsDependentArea() throws Exception {
        assertTrue(fixture().startup().isSuccessful());
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor publication = new ManualExecutor();
        JobCoordinator jobs = manualJobs(worker, publication);

        FxTestToolkit.runOnFxThread(() -> {
            host(jobs);
            publication.runNext();
            editWaistMultiplier("2");
            hosted().<Button>node("saveSettingsButton").fire();

            assertTrue(jobs.frame().active());
            assertEquals("submitSettingsEffect(SaveEffect)", shell.requests().getLast());
        });

        worker.runNextAsync().join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();

            assertFalse(jobs.frame().active());
            assertEquals(2f, fixture().settings().getMultiplier("Waist"));
            assertEquals("settingsPublished(pair=true)", shell.requests().getLast());
            assertFalse(hosted().<Button>node("reloadSettingsButton").isDisabled());
            assertTrue(hosted().<Button>node("saveSettingsButton").isDisabled());
        });
    }

    /** The generation preference remains unchanged until its JavaFX-captured worker operation executes. */
    @Test
    void generationPreferenceToggleRunsOnTheApplicationWorker() throws Exception {
        Settings.InitializationResult initialized = fixture().startup();
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

        FxTestToolkit.runOnFxThread(() -> {
            host(jobs);
            publication.runNext();

            hosted().<CheckBox>node("omitRedundantSlidersCheck").fire();

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
            assertTrue(hosted().<CheckBox>node("omitRedundantSlidersCheck").isSelected());
            hosted().stage().close();
        });

        assertEquals("omitRedundantSliders=true" + System.lineSeparator(),
                Files.readString(temporaryDirectory.resolve("workbench-generation.properties")));
    }

    /** Dirty Reload cancellation retains the draft, while Save persists it before the original Reload continues. */
    @Test
    void dirtySettingsReloadConfirmsAndSavesBeforeReloading() throws Exception {
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
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CANCEL);
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.SAVE);

        FxTestToolkit.runOnFxThread(() -> {
            host(jobs);
            publication.runNext();
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) hosted().namespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) hosted().namespace().get("settingsMultiplierInput")).setText("2");
            ((Button) hosted().namespace().get("applySettingsEntryButton")).fire();
        });
        Files.writeString(temporaryDirectory.resolve("settings.json"),
                "{\"Defaults\":{\"Waist\":{\"valueSmall\":0.5,\"valueBig\":1}},"
                        + "\"Multipliers\":{\"Waist\":4},\"Inverted\":[]}");
        Files.writeString(temporaryDirectory.resolve("settings_UUNP.json"),
                "{\"Defaults\":{},\"Multipliers\":{},\"Inverted\":[]}");

        FxTestToolkit.runOnFxThread(() -> {
            Button reload = (Button) hosted().namespace().get("reloadSettingsButton");
            reload.fire();

            assertFalse(jobs.frame().active());
            assertFalse(((Button) hosted().namespace().get("saveSettingsButton")).isDisabled());
            assertEquals("2.0", ((TextField) hosted().namespace().get("settingsMultiplierInput")).getText());

            reload.fire();

            assertTrue(jobs.frame().active());
            assertEquals("Save Settings", jobs.frame().attempt().orElseThrow().operation().name());
            assertEquals("2.0", ((TextField) hosted().namespace().get("settingsMultiplierInput")).getText());
        });

        Thread saveWorker = worker.runNextAsync();
        saveWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            assertTrue(jobs.frame().active());
            assertEquals("Reload Settings", jobs.frame().attempt().orElseThrow().operation().name());
            assertEquals(2f, fixture().settings().getMultiplier("Waist"));
        });

        Thread reloadWorker = worker.runNextAsync();
        reloadWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            assertFalse(jobs.frame().active());
            assertEquals(2f, fixture().settings().getMultiplier("Waist"));
            assertTrue(((Button) hosted().namespace().get("saveSettingsButton")).isDisabled());
            assertEquals("2.0", ((TextField) hosted().namespace().get("settingsMultiplierInput")).getText());
            hosted().stage().close();
        });
    }

    /** Dirty Reload Discard admits Reload directly and replaces the retained draft only after worker completion. */
    @Test
    void dirtySettingsReloadDiscardAdmitsReloadWithoutSaving() throws Exception {
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
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.DISCARD);

        FxTestToolkit.runOnFxThread(() -> {
            host(jobs);
            publication.runNext();
            @SuppressWarnings("unchecked")
            ListView<SettingsFeature.EntryFrame> entries =
                    (ListView<SettingsFeature.EntryFrame>) hosted().namespace().get("settingsEntryList");
            entries.getSelectionModel().select(entries.getItems().stream()
                    .filter(entry -> entry.name().equals("Waist")).findFirst().orElseThrow());
            ((TextField) hosted().namespace().get("settingsMultiplierInput")).setText("2");
            ((Button) hosted().namespace().get("applySettingsEntryButton")).fire();
        });
        Files.writeString(temporaryDirectory.resolve("settings.json"),
                "{\"Defaults\":{\"Waist\":{\"valueSmall\":0.5,\"valueBig\":1}},"
                        + "\"Multipliers\":{\"Waist\":4},\"Inverted\":[]}");
        Files.writeString(temporaryDirectory.resolve("settings_UUNP.json"),
                "{\"Defaults\":{},\"Multipliers\":{},\"Inverted\":[]}");

        FxTestToolkit.runOnFxThread(() -> {
            ((Button) hosted().namespace().get("reloadSettingsButton")).fire();

            assertTrue(jobs.frame().active());
            assertEquals("Reload Settings", jobs.frame().attempt().orElseThrow().operation().name());
            assertEquals(1f, fixture().settings().getMultiplier("Waist"));
            assertEquals("2.0", ((TextField) hosted().namespace().get("settingsMultiplierInput")).getText());
        });

        Thread reloadWorker = worker.runNextAsync();
        reloadWorker.join();

        FxTestToolkit.runOnFxThread(() -> {
            publication.runNext();
            publication.runNext();
            publication.runNext();
            assertFalse(jobs.frame().active());
            assertEquals(4f, fixture().settings().getMultiplier("Waist"));
            assertTrue(((Button) hosted().namespace().get("saveSettingsButton")).isDisabled());
            assertEquals("4.0", ((TextField) hosted().namespace().get("settingsMultiplierInput")).getText());
            hosted().stage().close();
        });
    }

    /**
     * Outlines one pane as indented lines of type, id, visible text and accessible name. It descends through layout
     * parents and ScrollPane content, but not into a control's skin.
     */
    private static List<String> outline(Node pane) {
        List<String> lines = new ArrayList<>();
        outline(pane, 0, lines);
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
        if (node instanceof ScrollPane scroll && scroll.getContent() != null)
            outline(scroll.getContent(), depth + 1, lines);
        else if (node instanceof Parent parent && !(node instanceof Control))
            parent.getChildrenUnmodifiable().forEach(child -> outline(child, depth + 1, lines));
    }
}
