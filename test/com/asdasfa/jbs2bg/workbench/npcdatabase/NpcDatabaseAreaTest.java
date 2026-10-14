package com.asdasfa.jbs2bg.workbench.npcdatabase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.fx.FxTestToolkit;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.testing.ManualExecutor;
import com.asdasfa.jbs2bg.workbench.AreaLoader;
import com.asdasfa.jbs2bg.workbench.AreaPanes;
import com.asdasfa.jbs2bg.workbench.AreaTest;
import com.asdasfa.jbs2bg.workbench.NpcPortraitViewer;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeedback;
import com.asdasfa.jbs2bg.workbench.WorkbenchNavigation.Landmark;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;
import com.asdasfa.jbs2bg.workbench.WorkbenchSatellites;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The NPC Database Area loaded alone through its own FXML, over a real {@link NpcDatabaseFeature} and importer on the
 * hosted coordinator, the shared portrait viewer, and a recording shell port. The NPC Database to Morphs journey,
 * the shell's diagnostics arbitration and Activity rendering stay in {@code WorkbenchControllerTest}.
 */
class NpcDatabaseAreaTest extends AreaTest<NpcDatabaseArea> {
    // Every satellite root the Area registered with the stand-in for the shell's appearance, oldest first.
    private final List<Parent> satelliteRoots = new ArrayList<>();
    // The registrations still open; the shell would re-theme and dismiss exactly these.
    private final List<Runnable> openSatellites = new ArrayList<>();

    /** Builds the feature and the shared portrait viewer the shell would, and loads the Area through its loader. */
    @Override
    protected AreaLoader.Loaded<NpcDatabaseArea> load(Collaborators collaborators) {
        NpcDatabaseFeature feature = new NpcDatabaseFeature(collaborators.flow(), collaborators.clock());
        WorkbenchSatellites satellites = (root, dismiss) -> {
            satelliteRoots.add(root);
            openSatellites.add(dismiss);
            return () -> openSatellites.remove(dismiss);
        };
        NpcPortraitViewer portraits = new NpcPortraitViewer(collaborators.environment().portraitDirectory(),
                collaborators.stage(), satellites, launcher -> {
            // The shell restores focus through its focus policy; no test here closes the portrait viewer.
        });
        return NpcDatabaseArea.load(feature, collaborators.flow().jobs(), collaborators.pipeline(),
                collaborators.platform(), collaborators.shell(), portraits, satellites, collaborators.stage());
    }

    /**
     * The Area's FXML loads on its own: its non-Node root holds the three landmark panes, and the first context
     * render fills the empty catalog's summary.
     */
    @Test
    void npcDatabaseAreaLoadsAloneIntoItsThreeLandmarkPanes() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            AreaPanes panes = host().module().panes();

            assertSame(hosted().node("npcDatabasePrimaryScroll"), panes.getPrimary());
            assertSame(hosted().node("npcDatabaseEditorContent"), panes.getEditor());
            assertSame(hosted().node("npcDatabaseInspectorScroll"), panes.getInspector());
            assertTrue(hosted().<TableView<NPC>>node("npcCatalogTable").getItems().isEmpty());
            assertTrue(hosted().<ListView<?>>node("npcSourceList").getItems().isEmpty());
            assertFalse(hosted().<Button>node("importNpcSourcesButton").isDisabled());
            assertTrue(hosted().<Button>node("addNpcToProjectButton").isDisabled());
            assertTrue(shell.requests().isEmpty());
        });
    }

    /**
     * The NPC Database controls keep the accessible names, visible texts and nesting they had in the single
     * Workbench FXML, because the packaged smoke run finds them by name, role and parent region. The expected outline
     * is transcribed from the pre-extraction {@code workbench.fxml}; texts the first render rewrites show that render.
     */
    @Test
    void npcDatabasePanesKeepTheirAccessibleOutline() throws Exception {
        FxTestToolkit.runOnFxThread(() -> {
            AreaPanes panes = host().module().panes();

            assertEquals(List.of(
                    "ScrollPane #npcDatabasePrimaryScroll name=NPC Database source management",
                    "  VBox",
                    "    Label text=Sources",
                    "    Label #npcSourceCountText text=0 sources loaded name=NPC Database source count",
                    "    ListView #npcSourceList name=NPC Database sources",
                    "    Button #importNpcSourcesButton text=_Import sources… name=Import NPC Database sources",
                    "    Button #removeNpcSourceButton text=_Remove source name=Remove selected NPC Database source",
                    "    Button #clearNpcDatabaseButton text=_Clear visible entries"
                            + " name=Clear visible NPC Database entries"),
                    outline(panes.getPrimary()));
            assertEquals(List.of(
                    "VBox #npcDatabaseEditorContent",
                    "  Button #backToMorphsButton text=← _Back to Morphs name=Back to Morphs",
                    "  HBox #npcPromotionInfoBar name=NPC Database promotion notification",
                    "    VBox",
                    "      Label #npcPromotionInfoBarCue text=Validation",
                    "      Label #npcPromotionInfoBarMessage text=Promotion message",
                    "    Button #dismissNpcPromotionInfoBarButton text=_Dismiss"
                            + " name=Dismiss NPC Database promotion notification",
                    "  Label text=NPC catalog",
                    "  Label #npcCatalogSummary text=0 of 0 NPC Database entries visible; 0 filtered columns."
                            + " name=NPC Database catalog summary",
                    "  HBox",
                    "    Label text=_Sort NPC catalog:",
                    "    ComboBox #npcSortChoice name=NPC Database sort",
                    "  HBox",
                    "    ToggleButton #npcNameFilterButton text=Name name=Filter Name column",
                    "    ToggleButton #npcMasterFilterButton text=Master name=Filter Master column",
                    "    ToggleButton #npcRaceFilterButton text=Race name=Filter Race column",
                    "    ToggleButton #npcEditorIdFilterButton text=EditorID name=Filter EditorID column",
                    "    ToggleButton #npcFormIdFilterButton text=FormID name=Filter FormID column",
                    "    Button #clearNpcFiltersButton text=Reset _filters name=Clear NPC Database filters",
                    "  TableView #npcCatalogTable name=NPC Database catalog",
                    "    TableColumn #npcNameColumn text=Name",
                    "    TableColumn #npcMasterColumn text=Master",
                    "    TableColumn #npcRaceColumn text=Race",
                    "    TableColumn #npcEditorIdColumn text=EditorID",
                    "    TableColumn #npcFormIdColumn text=FormID",
                    "  Button #addAllNpcsToProjectButton text=Add _all visible NPCs to Project"
                            + " name=Add all visible NPCs to Project",
                    "  TextArea #npcPromotionDetails name=NPC Database promotion details"),
                    outline(panes.getEditor()));
            assertEquals(List.of(
                    "ScrollPane #npcDatabaseInspectorScroll name=NPC Database inspector controls",
                    "  VBox",
                    "    Label text=NPC inspector",
                    "    Label #npcInspectorName text=No NPC selected name=Selected NPC Database entry",
                    "    Label #npcInspectorSource text=Source: none name=NPC Database source",
                    "    Label #npcInspectorMaster text=Plugin: none name=NPC plugin",
                    "    Label #npcInspectorEditorId text=Editor ID: none name=NPC editor ID",
                    "    Label #npcInspectorRace text=Race: none name=NPC race",
                    "    Label #npcInspectorFormId text=Form ID: none name=NPC Form ID",
                    "    VBox",
                    "      Label text=NPC portrait",
                    "      ImageView #npcDatabasePortraitImage",
                    "      Label #npcDatabasePortraitStatus text=No NPC portrait selected"
                            + " name=NPC Database portrait status",
                    "      Button #openNpcDatabasePortraitViewerButton text=_Open portrait viewer"
                            + " name=Open NPC Database portrait viewer",
                    "    Button #addNpcToProjectButton text=_Add to Project name=Add selected NPC to Project",
                    "    CheckBox #assignRandomNpcPresetCheck text=Assign _random Slider Preset"
                            + " name=Assign random Slider Preset to promoted NPCs",
                    "    Label #npcDatabaseDiagnostics text=No source diagnostics name=NPC Database diagnostics"),
                    outline(panes.getInspector()));
        });
    }

    /** Imported NPC sources populate the independent catalog and its selection-following inspector. */
    @Test
    void npcDatabaseImportsSourcesAndInspectsSelectedCatalogRow() throws Exception {
        Path source = temporaryDirectory.resolve("npcs.txt");
        Files.writeString(source, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n"
                + "Skyrim.esm | Beta | Beta01 | BretonRace | 00012346\n");
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            host();
            hosted().<Button>node("importNpcSourcesButton").fire();

            TableView<NPC> catalog = hosted().node("npcCatalogTable");
            assertEquals(List.of("Alpha", "Beta"), catalog.getItems().stream().map(NPC::getName).toList());
            assertEquals(1, hosted().<ListView<?>>node("npcSourceList").getItems().size());
            catalog.getSelectionModel().selectFirst();
            assertTrue(hosted().<Label>node("npcInspectorName").getText().contains("Alpha"));
            assertTrue(hosted().<Label>node("npcDatabasePortraitStatus").getText()
                    .contains("No portrait found"));
        });
    }

    /**
     * The NPC Database gate refuses mutations while any job runs, not only a Project-exclusive one: Enter-to-add is
     * refused during an NPC Database import, whose job leaves Templates and Morphs editable, until it settles.
     */
    @Test
    void npcDatabasePromotionIsRefusedWhileAnyJobRuns() throws Exception {
        Path npcSource = temporaryDirectory.resolve("first-import.txt");
        Files.writeString(npcSource, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n");
        Path laterSource = temporaryDirectory.resolve("later-import.txt");
        Files.writeString(laterSource, "Skyrim.esm | Beta | Beta01 | NordRace | 00012346\n");
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
        platform.respondNpcSourcesWith(Optional.of(List.of(npcSource)));
        platform.respondNpcSourcesWith(Optional.of(List.of(laterSource)));
        FxTestToolkit.runOnFxThread(() -> {
            host(flow);
            Button importSources = hosted().node("importNpcSourcesButton");
            importSources.fire();
            worker.runNext();
            TableView<NPC> catalog = hosted().node("npcCatalogTable");
            catalog.getSelectionModel().selectFirst();
            importSources.fire();
            assertTrue(jobs.frame().active());
            assertFalse(jobs.frame().projectMutationsBlocked());

            catalog.requestFocus();
            sendKey(catalog, KeyCode.ENTER);

            assertTrue(session.getSnapshot().getNpcMorphAssignments().isEmpty());
            assertFalse(hosted().<Label>node("npcPromotionInfoBarMessage").getText().contains("Amber"));
            worker.runNext();
            assertFalse(jobs.frame().active());
            assertEquals("Amber", catalog.getSelectionModel().getSelectedItem().getName());

            sendKey(catalog, KeyCode.ENTER);

            assertEquals(1, session.getSnapshot().getNpcMorphAssignments().size());
        });
    }

    /** Column filters clear hidden selection, sorting changes order, and Clear freezes only visible identities. */
    @Test
    void npcDatabaseClearUsesFilteredVisibleScope() throws Exception {
        Path source = temporaryDirectory.resolve("filter-npcs.txt");
        Files.writeString(source, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n"
                + "Skyrim.esm | Beta | Beta01 | BretonRace | 00012346\n");
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        platform.respondConfirmationWith(WorkbenchFeedback.DialogAction.CLEAR);
        FxTestToolkit.runOnFxThread(() -> {
            host();
            // The column-filter popup needs a showing owner window.
            hosted().show();
            hosted().<Button>node("importNpcSourcesButton").fire();
            TableView<NPC> catalog = hosted().node("npcCatalogTable");
            catalog.getSelectionModel().selectFirst();

            ToggleButton raceFilter = hosted().node("npcRaceFilterButton");
            raceFilter.fire();
            Popup cancelledFilter = FxTestToolkit.showingPopup(hosted().stage(), "#npcColumnFilterChoices");
            sendKey(cancelledFilter.getScene().getRoot(), KeyCode.ESCAPE);
            assertFalse(cancelledFilter.isShowing());
            assertEquals(2, catalog.getItems().size());
            hideNpcColumnValue("Race", "NordRace");
            assertEquals(List.of("Beta"), catalog.getItems().stream().map(NPC::getName).toList());
            assertNull(catalog.getSelectionModel().getSelectedItem());
            assertTrue(hosted().<ToggleButton>node("npcRaceFilterButton")
                    .getAccessibleHelp().contains("active, collapsed"));

            hosted().<Button>node("clearNpcFiltersButton").fire();
            ComboBox<String> sort = hosted().node("npcSortChoice");
            sort.getSelectionModel().select("Name descending");
            assertEquals(List.of("Beta", "Alpha"), catalog.getItems().stream().map(NPC::getName).toList());

            hideNpcColumnValue("Name", "Beta");
            assertEquals(List.of("Alpha"), catalog.getItems().stream().map(NPC::getName).toList());
            hosted().<Button>node("clearNpcDatabaseButton").fire();
            assertTrue(catalog.getItems().isEmpty());
            hosted().<Button>node("clearNpcFiltersButton").fire();
            assertEquals(List.of("Beta"), catalog.getItems().stream().map(NPC::getName).toList());
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
        platform.respondNpcSourcesWith(Optional.of(List.of(first, malformed, last)));
        platform.respondNpcSourcesWith(Optional.of(List.of(malformed)));
        FxTestToolkit.runOnFxThread(() -> {
            host();
            Button importButton = hosted().node("importNpcSourcesButton");
            importButton.fire();
            TableView<NPC> catalog = hosted().node("npcCatalogTable");
            assertEquals(List.of("Alpha", "Gamma"), catalog.getItems().stream().map(NPC::getName).toList());
            assertEquals(2, hosted().<ListView<?>>node("npcSourceList").getItems().size());
            String diagnostics = hosted().<Label>node("npcDatabaseDiagnostics").getText();
            assertTrue(diagnostics.contains("NPC_DATABASE_SOURCE_MALFORMED"));
            assertTrue(diagnostics.contains("malformed-npcs.txt:2"));
            assertTrue(hosted().flow().frame().snapshot().getNpcMorphAssignments().isEmpty());

            Files.writeString(malformed, "Skyrim.esm | Ghost | Ghost01 | NordRace | 00012346\n");
            importButton.fire();
            assertEquals(List.of("Alpha", "Gamma", "Ghost"),
                    catalog.getItems().stream().map(NPC::getName).toList());
            assertEquals("No source diagnostics", hosted().<Label>node("npcDatabaseDiagnostics").getText());
        });
    }

    /**
     * Each landmark resolves to the Area's own node, its last-resort default is the catalog rather than the source
     * list its primary content starts with, Ctrl+K reaches the first column filter, and Escape clears only a
     * selection that exists.
     */
    @Test
    void focusNodesSearchAndEscapeFollowTheCatalog() throws Exception {
        Path source = temporaryDirectory.resolve("focus-npcs.txt");
        Files.writeString(source, "Skyrim.esm | Alpha | Alpha01 | NordRace | 00012345\n");
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            NpcDatabaseArea area = host().module();
            hosted().show();

            assertSame(hosted().node("npcSourceList"), area.focusNode(Landmark.PRIMARY_CONTENT));
            assertSame(hosted().node("npcCatalogTable"), area.focusNode(Landmark.EDITOR));
            assertSame(hosted().node("npcInspectorName"), area.focusNode(Landmark.INSPECTOR));
            assertSame(hosted().node("npcCatalogTable"), area.defaultFocusNode());
            assertNull(area.focusNode(Landmark.NPC_DATABASE_LAUNCHER));
            assertFalse(area.escape(), "Escape without a selected row is left to the shell");

            area.focusSearch();
            assertSame(hosted().node("npcNameFilterButton"), hosted().stage().getScene().getFocusOwner());

            hosted().<Button>node("importNpcSourcesButton").fire();
            TableView<NPC> catalog = hosted().node("npcCatalogTable");
            catalog.getSelectionModel().selectFirst();
            assertTrue(area.escape());
            assertNull(catalog.getSelectionModel().getSelectedItem());
        });
    }

    /**
     * Add asks the shell to re-render the Project and lets it show the Project diagnostics, Add All keeps them in its
     * own report, and Back to Morphs hands the promoted assignment to the shell; the Area reaches no other Area.
     */
    @Test
    void promotionAndReturnGoThroughTheShellPort() throws Exception {
        Path source = temporaryDirectory.resolve("promote-npcs.txt");
        Files.writeString(source, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n"
                + "Skyrim.esm | Beta | Beta01 | NordRace | 00012346\n");
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            host();
            hosted().<Button>node("importNpcSourcesButton").fire();
            hosted().<TableView<NPC>>node("npcCatalogTable").getSelectionModel().selectFirst();
            int before = shell.requests().size();

            hosted().<Button>node("addNpcToProjectButton").fire();

            assertEquals(List.of("projectChanged(ownsDiagnostics=false)",
                    "publishFeedback(Added Amber to the Project.)"),
                    shell.requests().subList(before, shell.requests().size()));
            int bulk = shell.requests().size();

            hosted().<Button>node("addAllNpcsToProjectButton").fire();

            assertEquals("projectChanged(ownsDiagnostics=true)", shell.requests().get(bulk));
            assertEquals(2, hosted().flow().frame().snapshot().getNpcMorphAssignments().size());
            int back = shell.requests().size();

            hosted().<Button>node("backToMorphsButton").fire();

            assertEquals(List.of("returnToMorphs(Beta01)"), shell.requests().subList(back, shell.requests().size()));
        });
    }

    /**
     * The column-filter popup registers with the shell's appearance while it shows; leaving the Area hides it, which
     * closes the registration and asks the shell to restore focus to its launcher, and ends the visit's promotion
     * report.
     */
    @Test
    void leavingHidesTheColumnFilterPopupAndEndsTheVisit() throws Exception {
        Path source = temporaryDirectory.resolve("leave-npcs.txt");
        Files.writeString(source, "Skyrim.esm | Amber | Amber01 | NordRace | 00012345\n");
        platform.respondNpcSourcesWith(Optional.of(List.of(source)));
        FxTestToolkit.runOnFxThread(() -> {
            NpcDatabaseArea area = host().module();
            hosted().show();
            hosted().<Button>node("importNpcSourcesButton").fire();
            hosted().<TableView<NPC>>node("npcCatalogTable").getSelectionModel().selectFirst();
            Button add = hosted().node("addNpcToProjectButton");
            add.fire();
            add.fire();
            assertTrue(hosted().<HBox>node("npcPromotionInfoBar").isVisible());
            ToggleButton nameFilter = hosted().node("npcNameFilterButton");
            nameFilter.fire();
            Popup popup = FxTestToolkit.showingPopup(hosted().stage(), "#npcColumnFilterChoices");
            assertEquals(1, satelliteRoots.size());
            assertSame(popup.getContent().getFirst(), satelliteRoots.getFirst());
            assertEquals(1, openSatellites.size());
            int before = shell.requests().size();

            area.leave();

            assertFalse(popup.isShowing());
            assertTrue(openSatellites.isEmpty(), "the hidden popup closed its registration");
            assertTrue(nameFilter.getAccessibleHelp().contains("collapsed"));
            assertEquals(List.of("restoreFocusAfterSatellite(npcNameFilterButton, EDITOR)"),
                    shell.requests().subList(before, shell.requests().size()));
            assertFalse(hosted().<HBox>node("npcPromotionInfoBar").isVisible());
        });
    }

    /** Delivers one unmodified key press to a node through the same key-event seam as JavaFX. */
    private static void sendKey(Node target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    /** Opens a named NPC column checklist, toggles one exact value with Space, and applies it with Enter. */
    private void hideNpcColumnValue(String column, String value) {
        String buttonId = switch (column) {
            case "Name" -> "npcNameFilterButton";
            case "Race" -> "npcRaceFilterButton";
            default -> throw new IllegalArgumentException("Unsupported test column: " + column);
        };
        ToggleButton launcher = hosted().node(buttonId);
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

    /** Lists a pane's nodes depth-first as type, id, Labeled text and accessible name, one line each. */
    private static List<String> outline(Node pane) {
        List<String> lines = new ArrayList<>();
        outline(pane, 0, lines);
        return lines;
    }

    /** Appends one node's line and its children's at the next depth; a TableView lists its columns. */
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
        else if (node instanceof TableView<?> table)
            for (TableColumn<?, ?> column : table.getColumns())
                lines.add("  ".repeat(depth + 1) + "TableColumn #" + column.getId() + " text=" + column.getText());
        else if (node instanceof Parent parent && !(node instanceof Control))
            parent.getChildrenUnmodifiable().forEach(child -> outline(child, depth + 1, lines));
    }
}
