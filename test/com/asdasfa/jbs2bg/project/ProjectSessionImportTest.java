package com.asdasfa.jbs2bg.project;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;

import static org.junit.jupiter.api.Assertions.*;

class ProjectSessionImportTest {

    @TempDir
    Path tempDirectory;

    /**
     * Asserts the stable machine-readable fields for one failed XML source.
     */
    private static void assertImportDiagnostic(ProjectDiagnostic diagnostic, Path source, String code,
                                               String element) {
        assertEquals(code, diagnostic.getCode());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.getSeverity());
        assertEquals(source.toAbsolutePath().normalize(), diagnostic.getSourceLocation().getPath().get());
        assertEquals(element, diagnostic.getSourceLocation().getElement().get());
        assertFalse(diagnostic.getMessage().trim().isEmpty());
    }

    /**
     * Returns Slider Preset names in exposed snapshot order.
     */
    private static List<String> sliderPresetNames(ProjectSnapshot snapshot) {
        List<String> names = new java.util.ArrayList<>();
        for (SliderPresetSnapshot preset : snapshot.getSliderPresets())
            names.add(preset.getName());
        return names;
    }

    /**
     * Returns slider-choice names in exposed snapshot order.
     */
    private static List<String> sliderChoiceNames(SliderPresetSnapshot preset) {
        List<String> names = new java.util.ArrayList<>();
        for (SliderChoiceSnapshot choice : preset.getSliderChoices())
            names.add(choice.getName());
        return names;
    }

    /**
     * Finds one Slider Preset by its case-insensitive logical identity.
     */
    private static SliderPresetSnapshot findPreset(ProjectSnapshot snapshot, String name) {
        for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
            if (preset.getName().equalsIgnoreCase(name))
                return preset;
        }
        throw new AssertionError("Missing Slider Preset: " + name);
    }

    /**
     * Finds one slider choice by its case-insensitive identity.
     */
    private static SliderChoiceSnapshot findChoice(SliderPresetSnapshot preset, String name) {
        for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
            if (choice.getName().equalsIgnoreCase(name))
                return choice;
        }
        throw new AssertionError("Missing slider choice: " + name);
    }

    /**
     * Seeds deterministic Slider settings used by imported effective values.
     */
    @BeforeEach
    void initializeSliderSettings() {
        Map<String, DefaultSliderValue> standard = new LinkedHashMap<>();
        standard.put("Breasts", new DefaultSliderValue(0.2f, 1f));
        SettingsTestSupport.installDefaults(standard, Collections.emptyMap());
    }

    /**
     * Restores process-wide Slider settings after each import seam test.
     */
    @AfterEach
    void restoreSliderSettings() {
        SettingsTestSupport.restoreRepositorySettings();
    }

    /**
     * Import rejects every selected source without parsing when no Project is active.
     */
    @Test
    void importBeforeActiveProjectRejectsEverySourceWithoutPublishing() {
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot before = session.getSnapshot();
        List<Path> sources = Arrays.asList(tempDirectory.resolve("first.xml"),
                tempDirectory.resolve("second.xml"));

        SliderPresetImportOutcome outcome = session.importSliderPresets(sources);

        assertInstanceOf(RejectedOutcome.class, outcome.getProjectOutcome());
        assertSame(before, outcome.getProjectOutcome().getSnapshot());
        assertSame(before, session.getSnapshot());
        assertEquals(2, outcome.getSourceOutcomes().size());
        for (ProjectOutcome sourceOutcome : outcome.getSourceOutcomes()) {
            assertInstanceOf(RejectedOutcome.class, sourceOutcome);
            assertSame(before, sourceOutcome.getSnapshot());
            assertEquals(ProjectDiagnosticCodes.ACTIVE_PROJECT_REQUIRED,
                    sourceOutcome.getDiagnostics().getFirst().getCode());
        }
    }

    /**
     * Imports every valid source independently and returns one canonically ordered,
     * dirty snapshot with legacy dot normalization and slider value semantics.
     *
     * @throws Exception when temporary XML sources cannot be written
     */
    @Test
    void allValidSourcesReturnPerFileOutcomesAndCanonicalDirtySnapshot() throws Exception {
        Path zulu = writeXml("zulu.xml", "<SliderPresets>"
                + "<Preset name=\"Zulu.Body\">"
                + "<SetSlider name=\"Waist\" size=\"small\" value=\"25\"/>"
                + "<SetSlider name=\"Waist\" size=\"big\" value=\"75\"/>"
                + "</Preset></SliderPresets>");
        Path alpha = writeXml("alpha.xml", "<SliderPresets>"
                + "<Preset name=\"Alpha.Shape\">"
                + "<SetSlider name=\"Arms\" size=\"big\" value=\"90\"/>"
                + "</Preset></SliderPresets>");
        ProjectSession session = ProjectSessions.create();
        session.newProject();

        SliderPresetImportOutcome outcome = session.importSliderPresets(Arrays.asList(zulu, alpha));
        ProjectSnapshot snapshot = outcome.getSnapshot();

        assertInstanceOf(ChangedOutcome.class, outcome.getProjectOutcome());
        assertSame(snapshot, session.getSnapshot());
        assertTrue(snapshot.isDirty());
        assertEquals(Arrays.asList("Alpha Shape", "Zulu Body"), sliderPresetNames(snapshot));
        assertEquals(2, outcome.getSourceOutcomes().size());
        assertInstanceOf(ChangedOutcome.class, outcome.getSourceOutcomes().getFirst());
        assertInstanceOf(ChangedOutcome.class, outcome.getSourceOutcomes().get(1));
        assertSame(snapshot, outcome.getSourceOutcomes().getFirst().getSnapshot());
        assertSame(snapshot, outcome.getSourceOutcomes().get(1).getSnapshot());
        assertTrue(outcome.getDiagnostics().isEmpty());

        SliderPresetSnapshot zuluPreset = findPreset(snapshot, "Zulu Body");
        SliderChoiceSnapshot waist = findChoice(zuluPreset, "Waist");
        assertFalse(zuluPreset.isUunp());
        assertEquals(25, waist.getStoredSmallValue().getAsInt());
        assertEquals(75, waist.getStoredBigValue().getAsInt());
        assertEquals(25, waist.getEffectiveSmallValue());
        assertEquals(75, waist.getEffectiveBigValue());
        assertFalse(waist.isMissingDefault());

        SliderChoiceSnapshot breasts = findChoice(zuluPreset, "Breasts");
        assertFalse(breasts.getStoredSmallValue().isPresent());
        assertFalse(breasts.getStoredBigValue().isPresent());
        assertEquals(20, breasts.getEffectiveSmallValue());
        assertEquals(100, breasts.getEffectiveBigValue());
        assertTrue(breasts.isMissingDefault());
    }

    /**
     * Cancellation between sources retains earlier committed imports and marks later sources unprocessed.
     */
    @Test
    void cancellationBetweenSourcesRetainsEarlierCommittedEffects() throws Exception {
        Path first = writeXml("first.xml",
                "<SliderPresets><Preset name=\"Committed First\"/></SliderPresets>");
        Path second = writeXml("second.xml",
                "<SliderPresets><Preset name=\"Must Not Commit\"/></SliderPresets>");
        Path third = writeXml("third.xml",
                "<SliderPresets><Preset name=\"Also Unprocessed\"/></SliderPresets>");
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot clean = session.newProject().getSnapshot();
        ProjectOperationContext context = new ProjectOperationContext() {
            private boolean cancel;

            /** Requests cancellation at the second source boundary. */
            @Override
            public boolean cancellationRequested() {
                return cancel;
            }

            /** Uses the real completed-source count as the deterministic cancellation point. */
            @Override
            public void report(ProjectOperationProgress progress) {
                cancel = progress.completedUnits().isPresent()
                        && progress.completedUnits().orElseThrow() == 1;
            }

            /** Ordered import commits are source-local and do not use one final all-or-nothing publication. */
            @Override
            public boolean beginCommit(String phase) {
                return true;
            }
        };

        SliderPresetImportOutcome outcome = session.importSliderPresets(
                List.of(first, second, third), context);

        assertInstanceOf(CancelledOutcome.class, outcome.getProjectOutcome());
        assertEquals(List.of("Committed First"), sliderPresetNames(outcome.getSnapshot()));
        assertFalse(clean.getContentVersion().equals(outcome.getSnapshot().getContentVersion()));
        assertEquals(3, outcome.getSourceOutcomes().size());
        assertInstanceOf(ChangedOutcome.class, outcome.getSourceOutcomes().getFirst());
        assertInstanceOf(CancelledOutcome.class, outcome.getSourceOutcomes().get(1));
        assertInstanceOf(CancelledOutcome.class, outcome.getSourceOutcomes().get(2));
    }

    /**
     * XML byte consumption cooperates with cancellation instead of waiting for a complete large DOM parse.
     */
    @Test
    void bodySlideParserChecksCancellationWhileReadingXml() throws Exception {
        StringBuilder xml = new StringBuilder("<SliderPresets>");
        for (int index = 0; index < 4_000; index++)
            xml.append("<Preset name=\"Preset ").append(index).append("\"/>");
        xml.append("</SliderPresets>");
        Path source = writeXml("large-cancellable.xml", xml.toString());
        ProjectOperationContext context = new ProjectOperationContext() {
            private int checks;

            /** Cancels only after parsing has performed multiple reads. */
            @Override
            public boolean cancellationRequested() {
                return ++checks > 4;
            }

            /** Direct parser verification has no presentation progress observer. */
            @Override
            public void report(ProjectOperationProgress progress) {
                // This parser seam reports through the owning batch rather than per XML byte.
            }

            /** Direct parser verification never reaches a commit boundary. */
            @Override
            public boolean beginCommit(String phase) {
                return true;
            }
        };

        assertThrows(CancellationException.class,
                () -> BodySlidePresetFileParser.parse(source, context));
    }

    /**
     * Continues after rejected and failed sources, commits the valid file between
     * them, and reports stable source diagnostics in selection order.
     *
     * @throws Exception when temporary XML sources cannot be written
     */
    @Test
    void mixedBatchCommitsValidSourceAndReportsEachFailedInput() throws Exception {
        Path malformed = writeXml("malformed.xml", "<SliderPresets>\n<Preset name=\"Broken\">");
        Path valid = writeXml("valid.xml", "<SliderPresets><Preset name=\"Committed\">"
                + "<SetSlider name=\"Waist\" size=\"small\" value=\"10\"/>"
                + "</Preset></SliderPresets>");
        Path missing = tempDirectory.resolve("missing.xml");
        ProjectSession session = ProjectSessions.create();
        session.newProject();

        SliderPresetImportOutcome outcome = session.importSliderPresets(Arrays.asList(malformed, valid, missing));

        assertInstanceOf(ChangedOutcome.class, outcome.getProjectOutcome());
        assertSame(outcome.getSnapshot(), session.getSnapshot());
        assertTrue(outcome.getSnapshot().isDirty());
        assertEquals(Arrays.asList("Committed"), sliderPresetNames(outcome.getSnapshot()));
        assertEquals(3, outcome.getSourceOutcomes().size());
        assertInstanceOf(RejectedOutcome.class, outcome.getSourceOutcomes().getFirst());
        assertInstanceOf(ChangedOutcome.class, outcome.getSourceOutcomes().get(1));
        assertInstanceOf(FailedOutcome.class, outcome.getSourceOutcomes().get(2));
        assertEquals(2, outcome.getDiagnostics().size());

        ProjectDiagnostic malformedDiagnostic = outcome.getSourceOutcomes().getFirst().getDiagnostics().getFirst();
        assertImportDiagnostic(malformedDiagnostic, malformed,
                ProjectDiagnosticCodes.SLIDER_PRESET_XML_MALFORMED, "/");
        assertTrue(malformedDiagnostic.getSourceLocation().getLine().isPresent());
        assertTrue(malformedDiagnostic.getSourceLocation().getColumn().isPresent());
        assertImportDiagnostic(outcome.getSourceOutcomes().get(2).getDiagnostics().getFirst(), missing,
                ProjectDiagnosticCodes.SLIDER_PRESET_XML_READ_FAILED, "/");
        assertSame(malformedDiagnostic, outcome.getDiagnostics().getFirst());
        assertSame(outcome.getSourceOutcomes().get(2).getDiagnostics().getFirst(), outcome.getDiagnostics().get(1));
    }

    /**
     * Rejects every semantically invalid source without replacing or dirtying the
     * clean Project, including names invalid only after dot normalization.
     *
     * @throws Exception when temporary XML sources cannot be written
     */
    @Test
    void allInvalidBatchPreservesCleanSnapshotAndIdentifiesEverySource() throws Exception {
        Path wrongRoot = writeXml("wrong-root.xml", "<NotSliderPresets/>");
        Path blankName = writeXml("blank-name.xml",
                "<SliderPresets><Preset name=\"...\"/></SliderPresets>");
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot clean = session.newProject().getSnapshot();

        SliderPresetImportOutcome outcome = session.importSliderPresets(Arrays.asList(wrongRoot, blankName));

        assertInstanceOf(RejectedOutcome.class, outcome.getProjectOutcome());
        assertSame(clean, outcome.getSnapshot());
        assertSame(clean, session.getSnapshot());
        assertFalse(outcome.getSnapshot().isDirty());
        assertEquals(2, outcome.getSourceOutcomes().size());
        assertInstanceOf(RejectedOutcome.class, outcome.getSourceOutcomes().getFirst());
        assertInstanceOf(RejectedOutcome.class, outcome.getSourceOutcomes().get(1));
        assertImportDiagnostic(outcome.getSourceOutcomes().getFirst().getDiagnostics().getFirst(), wrongRoot,
                ProjectDiagnosticCodes.SLIDER_PRESET_XML_STRUCTURE_INVALID, "/");
        assertImportDiagnostic(outcome.getSourceOutcomes().get(1).getDiagnostics().getFirst(), blankName,
                ProjectDiagnosticCodes.SLIDER_PRESET_NAME_REQUIRED, "/SliderPresets/Preset[1]/@name");
        assertEquals(2, outcome.getDiagnostics().size());
    }

    /**
     * Parses a complete source before publication so a later invalid preset cannot
     * leave an earlier preset from that same file partially committed.
     *
     * @throws Exception when the temporary XML source cannot be written
     */
    @Test
    void invalidPresetRejectsItsCompleteSourceAtomically() throws Exception {
        Path source = writeXml("partially-invalid.xml", "<SliderPresets>"
                + "<Preset name=\"Would Otherwise Commit\">"
                + "<SetSlider name=\"Waist\" size=\"small\" value=\"10\"/>"
                + "</Preset>"
                + "<Preset name=\"Invalid\">"
                + "<SetSlider name=\"Arms\" size=\"big\" value=\"not-an-integer\"/>"
                + "</Preset>"
                + "</SliderPresets>");
        ProjectSession session = ProjectSessions.create();
        ProjectSnapshot clean = session.newProject().getSnapshot();

        SliderPresetImportOutcome outcome = session.importSliderPresets(Arrays.asList(source));

        assertInstanceOf(RejectedOutcome.class, outcome.getProjectOutcome());
        assertInstanceOf(RejectedOutcome.class, outcome.getSourceOutcomes().getFirst());
        assertSame(clean, outcome.getSnapshot());
        assertSame(clean, session.getSnapshot());
        assertTrue(outcome.getSnapshot().getSliderPresets().isEmpty());
        assertFalse(outcome.getSnapshot().isDirty());
        assertImportDiagnostic(outcome.getDiagnostics().getFirst(), source,
                ProjectDiagnosticCodes.SLIDER_PRESET_XML_VALUE_INVALID,
                "/SliderPresets/Preset[2]/SetSlider[1]/@value");
    }

    /**
     * A missing size retains the legacy big-endpoint meaning, but any unsupported explicit size rejects its entire
     * source instead of silently changing endpoint semantics.
     *
     * @throws Exception when the temporary XML sources cannot be written
     */
    @Test
    void missingSizeMeansBigWhileUnsupportedExplicitSizeRejectsTheSource() throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        Path missingSize = writeXml("missing-size.xml", "<SliderPresets>"
                + "<Preset name=\"Legacy Missing Size\">"
                + "<SetSlider name=\"Waist\" value=\"65\"/>"
                + "</Preset></SliderPresets>");
        Path unsupportedSize = writeXml("unsupported-size.xml", "<SliderPresets>"
                + "<Preset name=\"Must Not Commit\">"
                + "<SetSlider name=\"Waist\" size=\"middle\" value=\"40\"/>"
                + "</Preset></SliderPresets>");

        SliderPresetImportOutcome outcome = session.importSliderPresets(
                List.of(missingSize, unsupportedSize));

        assertInstanceOf(ChangedOutcome.class, outcome.getProjectOutcome());
        assertEquals(List.of("Legacy Missing Size"), sliderPresetNames(outcome.getSnapshot()));
        assertEquals(65, findChoice(findPreset(outcome.getSnapshot(), "Legacy Missing Size"), "Waist")
                .getEffectiveBigValue());
        assertInstanceOf(RejectedOutcome.class, outcome.getSourceOutcomes().get(1));
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_XML_SIZE_INVALID,
                outcome.getSourceOutcomes().get(1).getDiagnostics().getFirst().getCode());
    }

    /**
     * Treats an identical reimport and an empty valid source as accepted no-ops,
     * preserving the exact clean snapshot without false dirty state.
     *
     * @throws Exception when temporary XML or Project files cannot be written
     */
    @Test
    void duplicateAndEmptyValidBatchRemainCleanAndUnchanged() throws Exception {
        String xml = "<SliderPresets><Preset name=\"Existing\">"
                + "<SetSlider name=\"Waist\" size=\"small\" value=\"20\"/>"
                + "<SetSlider name=\"Waist\" size=\"big\" value=\"80\"/>"
                + "</Preset></SliderPresets>";
        Path duplicate = writeXml("duplicate.xml", xml);
        Path empty = writeXml("empty.xml", "<SliderPresets/>");
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.importSliderPresets(Arrays.asList(duplicate));
        session.saveAs(tempDirectory.resolve("clean.jbs2bg"));
        ProjectSnapshot clean = session.getSnapshot();

        SliderPresetImportOutcome outcome = session.importSliderPresets(Arrays.asList(duplicate, empty));

        assertInstanceOf(UnchangedOutcome.class, outcome.getProjectOutcome());
        assertSame(clean, outcome.getSnapshot());
        assertSame(clean, session.getSnapshot());
        assertFalse(outcome.getSnapshot().isDirty());
        assertEquals(2, outcome.getSourceOutcomes().size());
        assertInstanceOf(UnchangedOutcome.class, outcome.getSourceOutcomes().getFirst());
        assertInstanceOf(UnchangedOutcome.class, outcome.getSourceOutcomes().get(1));
        assertSame(clean, outcome.getSourceOutcomes().getFirst().getSnapshot());
        assertSame(clean, outcome.getSourceOutcomes().get(1).getSnapshot());
        assertTrue(outcome.getDiagnostics().isEmpty());
    }

    /**
     * Reimports an existing logical preset as one complete payload while retaining
     * its display identity and every Custom Morph Target and NPC relationship.
     *
     * @throws Exception when the temporary XML source cannot be written
     */
    @Test
    void reimportUpdatesChoicesWithoutBreakingProjectRelationships() throws Exception {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.setUunp("Alpha", true));
        session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                new SliderChoiceSnapshot("Old", true, Integer.valueOf(1), Integer.valueOf(2), 1, 2,
                        100, 100, false)));
        session.apply(CustomMorphTargetEdits.create("All|Female"));
        session.apply(CustomMorphTargetEdits.addSliderPreset("All|Female", "Alpha"));
        NpcMorphAssignmentSnapshot lydia = new NpcMorphAssignmentSnapshot("Lydia", "Skyrim.esm",
                "HousecarlWhiterun", "NordRace", "A2C94", Collections.<String>emptyList());
        session.apply(NpcMorphAssignmentEdits.addNpc(lydia));
        session.apply(NpcMorphAssignmentEdits.addSliderPreset(
                new NpcMorphAssignmentIdentity("Skyrim.esm", "HousecarlWhiterun"), "Alpha"));
        Path source = writeXml("reimport.xml", "<SliderPresets><Preset name=\"alpha\">"
                + "<SetSlider name=\"New\" size=\"small\" value=\"30\"/>"
                + "<SetSlider name=\"New\" size=\"big\" value=\"70\"/>"
                + "</Preset></SliderPresets>");

        SliderPresetImportOutcome outcome = session.importSliderPresets(Arrays.asList(source));
        ProjectSnapshot snapshot = outcome.getSnapshot();

        assertInstanceOf(ChangedOutcome.class, outcome.getProjectOutcome());
        assertEquals(Arrays.asList("Alpha"), sliderPresetNames(snapshot));
        SliderPresetSnapshot preset = findPreset(snapshot, "Alpha");
        assertFalse(preset.isUunp());
        assertEquals(Arrays.asList("Breasts", "New"), sliderChoiceNames(preset));
        assertEquals(30, findChoice(preset, "New").getStoredSmallValue().getAsInt());
        assertEquals(70, findChoice(preset, "New").getStoredBigValue().getAsInt());
        assertEquals(Arrays.asList("Alpha"), snapshot.getCustomMorphTargets().getFirst().getSliderPresetNames());
        assertEquals(Arrays.asList("Alpha"), snapshot.getNpcMorphAssignments().getFirst().getSliderPresetNames());
        assertSame(snapshot, session.getSnapshot());
        assertTrue(snapshot.isDirty());
    }

    /**
     * Serializes concurrent synchronous imports so independently valid sources are
     * never lost behind a partially published snapshot.
     *
     * @throws Exception when a worker cannot complete within the test deadline
     */
    @Test
    void concurrentImportsDoNotLoseCommittedSources() throws Exception {
        Path alpha = writeXml("concurrent-alpha.xml",
                "<SliderPresets><Preset name=\"Alpha\"/></SliderPresets>");
        Path beta = writeXml("concurrent-beta.xml",
                "<SliderPresets><Preset name=\"beta\"/></SliderPresets>");
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<SliderPresetImportOutcome> first = executor.submit(() -> {
                start.await();
                return session.importSliderPresets(Arrays.asList(alpha));
            });
            Future<SliderPresetImportOutcome> second = executor.submit(() -> {
                start.await();
                return session.importSliderPresets(Arrays.asList(beta));
            });
            start.countDown();

            assertInstanceOf(ChangedOutcome.class, first.get(10, TimeUnit.SECONDS).getProjectOutcome());
            assertInstanceOf(ChangedOutcome.class, second.get(10, TimeUnit.SECONDS).getProjectOutcome());
            ProjectSnapshot snapshot = session.getSnapshot();
            assertEquals(Arrays.asList("Alpha", "beta"), sliderPresetNames(snapshot));
            assertTrue(snapshot.isDirty());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /**
     * Retains the selected source identity when its filesystem can no longer
     * resolve an absolute path, so the per-file diagnostic remains actionable.
     */
    @Test
    void unresolvableSourceDiagnosticStillIdentifiesSelectedPath() {
        Path source = (Path) Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[]{Path.class},
                (proxy, method, arguments) -> {
                    if ("toAbsolutePath".equals(method.getName()))
                        throw new UnsupportedOperationException("No absolute path is available.");
                    if ("toString".equals(method.getName()))
                        return "unresolvable:/presets.xml";
                    if ("hashCode".equals(method.getName()))
                        return Integer.valueOf(System.identityHashCode(proxy));
                    if ("equals".equals(method.getName()))
                        return Boolean.valueOf(proxy == arguments[0]);
                    throw new AssertionError("Unexpected Path method: " + method.getName());
                });
        ProjectSession session = ProjectSessions.create();
        session.newProject();

        SliderPresetImportOutcome outcome = session.importSliderPresets(Collections.singletonList(source));

        assertInstanceOf(FailedOutcome.class, outcome.getProjectOutcome());
        ProjectDiagnostic diagnostic = outcome.getDiagnostics().getFirst();
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_XML_IMPORT_FAILED, diagnostic.getCode());
        assertSame(source, diagnostic.getSourceLocation().getPath().get());
        assertFalse(outcome.getSnapshot().isDirty());
    }

    /**
     * Writes one UTF-8 BodySlide XML source in the temporary directory.
     */
    private Path writeXml(String fileName, String xml) throws Exception {
        Path source = tempDirectory.resolve(fileName);
        Files.write(source, xml.getBytes(StandardCharsets.UTF_8));
        return source;
    }
}
