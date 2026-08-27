package com.asdasfa.jbs2bg.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;

class ProjectSessionImportTest {

    private final Map<String, DefaultSliderValue> originalDefaults = new LinkedHashMap<>();
    private final Map<String, DefaultSliderValue> originalUunpDefaults = new LinkedHashMap<>();

    @TempDir
    Path tempDirectory;

    /** Seeds deterministic Slider settings used by imported effective values. */
    @BeforeEach
    void initializeSliderSettings() {
        originalDefaults.putAll(Settings.getDefaultsMap());
        originalUunpDefaults.putAll(Settings.getDefaultsMapUUNP());
        Settings.getDefaultsMap().clear();
        Settings.getDefaultsMap().put("Breasts", new DefaultSliderValue(0.2f, 1f));
        Settings.getDefaultsMapUUNP().clear();
    }

    /** Restores process-wide Slider settings after each import seam test. */
    @AfterEach
    void restoreSliderSettings() {
        Settings.getDefaultsMap().clear();
        Settings.getDefaultsMap().putAll(originalDefaults);
        Settings.getDefaultsMapUUNP().clear();
        Settings.getDefaultsMapUUNP().putAll(originalUunpDefaults);
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

        assertTrue(outcome.getProjectOutcome() instanceof ChangedOutcome);
        assertSame(snapshot, session.getSnapshot());
        assertTrue(snapshot.isDirty());
        assertEquals(Arrays.asList("Alpha Shape", "Zulu Body"), sliderPresetNames(snapshot));
        assertEquals(2, outcome.getSourceOutcomes().size());
        assertTrue(outcome.getSourceOutcomes().get(0) instanceof ChangedOutcome);
        assertTrue(outcome.getSourceOutcomes().get(1) instanceof ChangedOutcome);
        assertSame(snapshot, outcome.getSourceOutcomes().get(0).getSnapshot());
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

        assertTrue(outcome.getProjectOutcome() instanceof ChangedOutcome);
        assertSame(outcome.getSnapshot(), session.getSnapshot());
        assertTrue(outcome.getSnapshot().isDirty());
        assertEquals(Arrays.asList("Committed"), sliderPresetNames(outcome.getSnapshot()));
        assertEquals(3, outcome.getSourceOutcomes().size());
        assertTrue(outcome.getSourceOutcomes().get(0) instanceof RejectedOutcome);
        assertTrue(outcome.getSourceOutcomes().get(1) instanceof ChangedOutcome);
        assertTrue(outcome.getSourceOutcomes().get(2) instanceof FailedOutcome);
        assertEquals(2, outcome.getDiagnostics().size());

        ProjectDiagnostic malformedDiagnostic = outcome.getSourceOutcomes().get(0).getDiagnostics().get(0);
        assertImportDiagnostic(malformedDiagnostic, malformed,
                ProjectDiagnosticCodes.SLIDER_PRESET_XML_MALFORMED, "/");
        assertTrue(malformedDiagnostic.getSourceLocation().getLine().isPresent());
        assertTrue(malformedDiagnostic.getSourceLocation().getColumn().isPresent());
        assertImportDiagnostic(outcome.getSourceOutcomes().get(2).getDiagnostics().get(0), missing,
                ProjectDiagnosticCodes.SLIDER_PRESET_XML_READ_FAILED, "/");
        assertSame(malformedDiagnostic, outcome.getDiagnostics().get(0));
        assertSame(outcome.getSourceOutcomes().get(2).getDiagnostics().get(0), outcome.getDiagnostics().get(1));
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

        assertTrue(outcome.getProjectOutcome() instanceof RejectedOutcome);
        assertSame(clean, outcome.getSnapshot());
        assertSame(clean, session.getSnapshot());
        assertFalse(outcome.getSnapshot().isDirty());
        assertEquals(2, outcome.getSourceOutcomes().size());
        assertTrue(outcome.getSourceOutcomes().get(0) instanceof RejectedOutcome);
        assertTrue(outcome.getSourceOutcomes().get(1) instanceof RejectedOutcome);
        assertImportDiagnostic(outcome.getSourceOutcomes().get(0).getDiagnostics().get(0), wrongRoot,
                ProjectDiagnosticCodes.SLIDER_PRESET_XML_STRUCTURE_INVALID, "/");
        assertImportDiagnostic(outcome.getSourceOutcomes().get(1).getDiagnostics().get(0), blankName,
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

        assertTrue(outcome.getProjectOutcome() instanceof RejectedOutcome);
        assertTrue(outcome.getSourceOutcomes().get(0) instanceof RejectedOutcome);
        assertSame(clean, outcome.getSnapshot());
        assertSame(clean, session.getSnapshot());
        assertTrue(outcome.getSnapshot().getSliderPresets().isEmpty());
        assertFalse(outcome.getSnapshot().isDirty());
        assertImportDiagnostic(outcome.getDiagnostics().get(0), source,
                ProjectDiagnosticCodes.SLIDER_PRESET_XML_VALUE_INVALID,
                "/SliderPresets/Preset[2]/SetSlider[1]/@value");
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

        assertTrue(outcome.getProjectOutcome() instanceof UnchangedOutcome);
        assertSame(clean, outcome.getSnapshot());
        assertSame(clean, session.getSnapshot());
        assertFalse(outcome.getSnapshot().isDirty());
        assertEquals(2, outcome.getSourceOutcomes().size());
        assertTrue(outcome.getSourceOutcomes().get(0) instanceof UnchangedOutcome);
        assertTrue(outcome.getSourceOutcomes().get(1) instanceof UnchangedOutcome);
        assertSame(clean, outcome.getSourceOutcomes().get(0).getSnapshot());
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

        assertTrue(outcome.getProjectOutcome() instanceof ChangedOutcome);
        assertEquals(Arrays.asList("Alpha"), sliderPresetNames(snapshot));
        SliderPresetSnapshot preset = findPreset(snapshot, "Alpha");
        assertFalse(preset.isUunp());
        assertEquals(Arrays.asList("Breasts", "New"), sliderChoiceNames(preset));
        assertEquals(30, findChoice(preset, "New").getStoredSmallValue().getAsInt());
        assertEquals(70, findChoice(preset, "New").getStoredBigValue().getAsInt());
        assertEquals(Arrays.asList("Alpha"), snapshot.getCustomMorphTargets().get(0).getSliderPresetNames());
        assertEquals(Arrays.asList("Alpha"), snapshot.getNpcMorphAssignments().get(0).getSliderPresetNames());
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

            assertTrue(first.get(10, TimeUnit.SECONDS).getProjectOutcome() instanceof ChangedOutcome);
            assertTrue(second.get(10, TimeUnit.SECONDS).getProjectOutcome() instanceof ChangedOutcome);
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
        Path source = (Path) Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[] { Path.class },
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

        assertTrue(outcome.getProjectOutcome() instanceof FailedOutcome);
        ProjectDiagnostic diagnostic = outcome.getDiagnostics().get(0);
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_XML_IMPORT_FAILED, diagnostic.getCode());
        assertSame(source, diagnostic.getSourceLocation().getPath().get());
        assertFalse(outcome.getSnapshot().isDirty());
    }

    /** Writes one UTF-8 BodySlide XML source in the temporary directory. */
    private Path writeXml(String fileName, String xml) throws Exception {
        Path source = tempDirectory.resolve(fileName);
        Files.write(source, xml.getBytes(StandardCharsets.UTF_8));
        return source;
    }

    /** Asserts the stable machine-readable fields for one failed XML source. */
    private static void assertImportDiagnostic(ProjectDiagnostic diagnostic, Path source, String code,
            String element) {
        assertEquals(code, diagnostic.getCode());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.getSeverity());
        assertEquals(source.toAbsolutePath().normalize(), diagnostic.getSourceLocation().getPath().get());
        assertEquals(element, diagnostic.getSourceLocation().getElement().get());
        assertFalse(diagnostic.getMessage().trim().isEmpty());
    }

    /** Returns Slider Preset names in exposed snapshot order. */
    private static List<String> sliderPresetNames(ProjectSnapshot snapshot) {
        List<String> names = new java.util.ArrayList<>();
        for (SliderPresetSnapshot preset : snapshot.getSliderPresets())
            names.add(preset.getName());
        return names;
    }

    /** Returns slider-choice names in exposed snapshot order. */
    private static List<String> sliderChoiceNames(SliderPresetSnapshot preset) {
        List<String> names = new java.util.ArrayList<>();
        for (SliderChoiceSnapshot choice : preset.getSliderChoices())
            names.add(choice.getName());
        return names;
    }

    /** Finds one Slider Preset by its case-insensitive logical identity. */
    private static SliderPresetSnapshot findPreset(ProjectSnapshot snapshot, String name) {
        for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
            if (preset.getName().equalsIgnoreCase(name))
                return preset;
        }
        throw new AssertionError("Missing Slider Preset: " + name);
    }

    /** Finds one slider choice by its case-insensitive identity. */
    private static SliderChoiceSnapshot findChoice(SliderPresetSnapshot preset, String name) {
        for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
            if (choice.getName().equalsIgnoreCase(name))
                return choice;
        }
        throw new AssertionError("Missing slider choice: " + name);
    }
}
