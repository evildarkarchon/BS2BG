package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.ProjectLifecycleStatus;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

/**
 * Verifies JavaFX-free generated output derived from one immutable Project snapshot.
 */
class ProjectOutputFormatterTest {

    /**
     * Resolves one generated artifact by its already-mapped filename.
     */
    private static BosJsonArtifact artifactNamed(ProjectGeneratedOutput output, String fileName) {
        return output.getBosJsonArtifacts().stream()
                .filter(artifact -> artifact.getFileName().equals(fileName))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Builds canonical Project state through the external session seam used by presentation.
     */
    private static ProjectSession populatedSession() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Zulu"));
        session.apply(SliderPresetEdits.create("Alpha"));
        session.apply(SliderPresetEdits.setSliderChoice("Alpha",
                choice("Scale", true, 20, 80, 25, 75)));
        session.apply(SliderPresetEdits.setSliderChoice("Zulu",
                choice("Zero", true, 0, 0, 100, 100)));
        session.apply(SliderPresetEdits.setSliderChoice("Zulu",
                choice("Active", true, 10, 20, 100, 100)));
        session.apply(CustomMorphTargetEdits.create("ZuluTarget"));
        session.apply(CustomMorphTargetEdits.create("AlphaTarget", Collections.singletonList("Alpha")));
        session.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot("Late", "Skyrim.esm",
                "ZuluEditor", "NordRace", "123ABC", Collections.singletonList("Zulu"))));
        session.apply(NpcMorphAssignmentEdits.addNpc(new NpcMorphAssignmentSnapshot("Early", "Dawnguard.esm",
                "AlphaEditor", "NordRace", "A2C94", Collections.<String>emptyList())));
        return session;
    }

    /**
     * Creates one explicit non-synthesized slider value for formatter fixtures.
     */
    private static SliderChoiceSnapshot choice(String name, boolean enabled, int small, int big, int minimum,
                                               int maximum) {
        return new SliderChoiceSnapshot(name, enabled, Integer.valueOf(small), Integer.valueOf(big), small, big,
                minimum, maximum, false);
    }

    /**
     * Publishes empty profiles so formatter fixtures contain only their explicit Slider choices.
     */
    @BeforeEach
    void initializeEmptySettings() {
        SettingsTestSupport.installDefaults(Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Restores the checked-in Settings pair after each formatter test.
     */
    @AfterEach
    void restoreSettings() {
        SettingsTestSupport.restoreRepositorySettings();
    }

    /**
     * A formatter that reads live session state or reorders snapshot values would
     * change this output after the pinned snapshot is captured.
     */
    @Test
    void generatesLegacyOutputFromThePinnedSnapshotInCanonicalOrder() {
        ProjectSession session = populatedSession();
        ProjectSnapshot pinned = session.getSnapshot();

        session.apply(SliderPresetEdits.rename("Alpha", "Changed"));
        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(pinned, false);

        String newLine = "\r\n";
        assertEquals("Alpha=Scale@0.35:0.65" + newLine + "Zulu=Active@0.2, Zero@0.0",
                output.getTemplatesText());
        assertEquals("AlphaTarget=Alpha" + newLine + "ZuluTarget=" + newLine
                        + "Dawnguard.esm|A2C94=" + newLine + "Skyrim.esm|123ABC=Zulu" + newLine,
                output.getMorphsText());
        assertEquals(Arrays.asList("Alpha", "Zulu"),
                Arrays.asList(output.getTemplateLinesByPresetName().keySet().toArray(new String[0])));
        assertEquals("Alpha=Scale@0.35:0.65", output.getTemplateLinesByPresetName().get("Alpha"));
        assertEquals(Arrays.asList("Alpha.json", "Zulu.json"),
                output.getBosJsonArtifacts().stream().map(BosJsonArtifact::getFileName).toList());

        String alphaBos = artifactNamed(output, "Alpha.json").getText();
        assertTrue(alphaBos.contains("\"bodyname\": \"Alpha\""));
        assertTrue(alphaBos.contains("\"slidername1\": \"Scale\""));
        assertTrue(alphaBos.contains("\"slidersnumber\": 1"));
        assertTrue(alphaBos.contains("\"highvalue1\": 0.8"));
        assertTrue(alphaBos.contains("\"lowvalue1\": 0.2"));
    }

    /**
     * No-preset reporting must preserve the exact immutable child values in output order.
     */
    @Test
    void reportsCustomAndNpcMorphAssignmentsWithoutPresets() {
        ProjectSnapshot pinned = populatedSession().getSnapshot();

        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(pinned, false);

        assertEquals(1, output.getCustomMorphTargetsWithoutPresets().size());
        assertEquals("ZuluTarget", output.getCustomMorphTargetsWithoutPresets().get(0).getName());
        assertSame(pinned.getCustomMorphTargets().get(1), output.getCustomMorphTargetsWithoutPresets().get(0));
        assertEquals(1, output.getNpcMorphAssignmentsWithoutPresets().size());
        assertEquals("Early", output.getNpcMorphAssignmentsWithoutPresets().get(0).getDisplayName());
        assertSame(pinned.getNpcMorphAssignments().get(0), output.getNpcMorphAssignmentsWithoutPresets().get(0));
    }

    /**
     * Omission must remove disabled choices and legacy-redundant zero ranges from both formats.
     */
    @Test
    void omitsDisabledAndRedundantNonInvertedSliders() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Preset"));
        session.apply(SliderPresetEdits.setSliderChoice("Preset",
                choice("Zero", true, 0, 0, 100, 100)));
        session.apply(SliderPresetEdits.setSliderChoice("Preset",
                choice("Active", true, 10, 20, 100, 100)));
        session.apply(SliderPresetEdits.setSliderChoice("Preset",
                choice("Disabled", false, 40, 60, 100, 100)));

        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(session.getSnapshot(), true);
        String bos = artifactNamed(output, "Preset.json").getText();

        assertEquals("Preset=Active@0.2", output.getTemplatesText());
        assertTrue(bos.contains("\"slidersnumber\": 1"));
        assertTrue(bos.contains("\"slidername1\": \"Active\""));
        assertFalse(bos.contains("Disabled"));
        assertFalse(bos.contains("Zero"));
    }

    /**
     * Omission uses the inverted profile's 100/100 neutral endpoint in both generated formats.
     */
    @Test
    void omitsRedundantInvertedSlidersAtTheirLegacyNeutralEndpoint() {
        SettingsTestSupport.installStandardOutput(Collections.emptyMap(), List.of("Inverted"));
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Preset"));
        SliderChoiceSnapshot inverted = choice("Inverted", true, 100, 100, 100, 100);
        session.apply(SliderPresetEdits.setSliderChoice("Preset", inverted));

        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(session.getSnapshot(), true);
        String bos = artifactNamed(output, "Preset.json").getText();

        assertEquals("Preset=", output.getTemplatesText());
        assertTrue(bos.contains("\"slidersnumber\": 0"));
        assertTrue(ProjectOutputFormatter.isSliderChoiceRedundant(inverted, false));
    }

    /**
     * The in-place editor preview uses the same inversion, multiplier, interpolation, rounding, and float spelling as
     * the complete Templates artifact rather than maintaining a second formatter.
     */
    @Test
    void singleChoicePreviewMatchesGeneratedInvertedTemplateValue() {
        SettingsTestSupport.installStandardOutput(Map.of("Inverted", Float.valueOf(2f)), List.of("Inverted"));
        SliderChoiceSnapshot inverted = choice("Inverted", true, 20, 80, 25, 75);
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Preset"));
        session.apply(SliderPresetEdits.setSliderChoice("Preset", inverted));

        String preview = ProjectOutputFormatter.formatSliderChoicePreview(inverted, false);

        assertEquals("Inverted@1.3:0.7", preview);
        assertEquals("Preset=" + preview,
                ProjectOutputFormatter.generate(session.getSnapshot(), false).getTemplatesText());
    }

    /**
     * BoS output must retain the legacy grouping of every high value before every low value.
     */
    @Test
    void groupsBosHighValuesBeforeLowValues() {
        ProjectSession session = ProjectSessions.create();
        session.newProject();
        session.apply(SliderPresetEdits.create("Preset"));
        session.apply(SliderPresetEdits.setSliderChoice("Preset",
                choice("Alpha", true, 10, 20, 100, 100)));
        session.apply(SliderPresetEdits.setSliderChoice("Preset",
                choice("Beta", true, 30, 40, 100, 100)));

        String json = artifactNamed(ProjectOutputFormatter.generate(session.getSnapshot(), false),
                "Preset.json").getText();
        int secondHighValueIndex = json.indexOf("\"highvalue2\"");
        int firstLowValueIndex = json.indexOf("\"lowvalue1\"");

        assertTrue(secondHighValueIndex >= 0);
        assertTrue(firstLowValueIndex >= 0);
        assertTrue(secondHighValueIndex < firstLowValueIndex);
    }

    /**
     * Unsafe Windows filename bytes are reversibly mapped while safe names remain unchanged.
     */
    @Test
    void mapsEveryBosFilenameBeforePublishingArtifacts() {
        ProjectSnapshot snapshot = new ProjectSnapshot(Arrays.asList(
                new SliderPresetSnapshot("Safe Name", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("CON", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("100% <Body>", false, Collections.<SliderChoiceSnapshot>emptyList())),
                Collections.emptyList(), Collections.emptyList(), Optional.empty(), true,
                ProjectLifecycleStatus.UNTITLED);

        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(snapshot, false);

        assertEquals(Arrays.asList("Safe Name.json", "%43ON.json", "100%25 %3CBody%3E.json"),
                output.getBosJsonArtifacts().stream().map(BosJsonArtifact::getFileName).toList());
    }

    /**
     * Filename policy handles Windows device names, trailing bytes, separators, and long names exactly.
     */
    @Test
    void mapsBosFilenameEdgeCasesDeterministically() {
        String overlong = "a".repeat(300);
        String validCjk = "界".repeat(100);
        ProjectSnapshot snapshot = new ProjectSnapshot(Arrays.asList(
                new SliderPresetSnapshot("CON.txt", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("COM¹", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("LPT³.log", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("trail. ", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("bad/name", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot(validCjk, false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot(overlong, false, Collections.<SliderChoiceSnapshot>emptyList())),
                Collections.emptyList(), Collections.emptyList(), Optional.empty(), true,
                ProjectLifecycleStatus.UNTITLED);

        List<String> fileNames = ProjectOutputFormatter.generate(snapshot, false).getBosJsonArtifacts().stream()
                .map(BosJsonArtifact::getFileName).toList();

        assertEquals("%43ON.txt.json", fileNames.get(0));
        assertEquals("%43OM¹.json", fileNames.get(1));
        assertEquals("%4CPT³.log.json", fileNames.get(2));
        assertEquals("trail%2E%20.json", fileNames.get(3));
        assertEquals("bad%2Fname.json", fileNames.get(4));
        assertEquals(validCjk + ".json", fileNames.get(5));
        assertEquals("a".repeat(233) + "~9835fa6bf4e20a9b.json", fileNames.get(6));
        assertEquals(255, fileNames.get(6).length());
    }

    /**
     * Unpaired UTF-16 is rejected before any artifact is returned, with all mappings retained.
     */
    @Test
    void rejectsUnrepresentableBosFilenameWithCompleteMappings() {
        ProjectSnapshot snapshot = new ProjectSnapshot(Arrays.asList(
                new SliderPresetSnapshot("Safe", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("Broken\uD800", false,
                        Collections.<SliderChoiceSnapshot>emptyList())),
                Collections.emptyList(), Collections.emptyList(), Optional.empty(), true,
                ProjectLifecycleStatus.UNTITLED);

        BosOutputException exception = assertThrows(BosOutputException.class,
                () -> ProjectOutputFormatter.generate(snapshot, false));

        assertEquals(2, exception.getFileNameMappings().size());
        assertEquals("Safe.json", exception.getFileNameMappings().get(0).getFileName().orElseThrow());
        assertTrue(exception.getFileNameMappings().get(1).getFileName().isEmpty());
        assertEquals("BOS_FILENAME_UNREPRESENTABLE", exception.getDiagnostics().get(0).getCode());
    }

    /**
     * Every case-insensitive collision is rejected with all attempted filename mappings.
     */
    @Test
    void rejectsAllBosFilenameCollisionsBeforePublishingArtifacts() {
        ProjectSnapshot snapshot = new ProjectSnapshot(Arrays.asList(
                new SliderPresetSnapshot("Alpha", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("alpha", false, Collections.<SliderChoiceSnapshot>emptyList()),
                new SliderPresetSnapshot("Safe", false, Collections.<SliderChoiceSnapshot>emptyList())),
                Collections.emptyList(), Collections.emptyList(), Optional.empty(), true,
                ProjectLifecycleStatus.UNTITLED);

        BosOutputException exception = assertThrows(BosOutputException.class,
                () -> ProjectOutputFormatter.generate(snapshot, false));

        assertEquals(3, exception.getFileNameMappings().size());
        assertEquals("Alpha.json", exception.getFileNameMappings().get(0).getFileName().orElseThrow());
        assertEquals("alpha.json", exception.getFileNameMappings().get(1).getFileName().orElseThrow());
        assertEquals("Safe.json", exception.getFileNameMappings().get(2).getFileName().orElseThrow());
        assertEquals(2, exception.getDiagnostics().size());
        assertTrue(exception.getDiagnostics().stream()
                .allMatch(diagnostic -> "BOS_FILENAME_COLLISION".equals(diagnostic.getCode())));
    }

    /**
     * Every generated collection must reject mutation through its public result seam.
     */
    @Test
    void exposesOnlyUnmodifiableGeneratedCollections() {
        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(populatedSession().getSnapshot(), false);

        assertThrows(UnsupportedOperationException.class, () -> output.getArtifacts().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> output.getTemplateLinesByPresetName().put("Injected", "Injected="));
        assertThrows(UnsupportedOperationException.class,
                () -> output.getBosJsonArtifacts().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> output.getCustomMorphTargetsWithoutPresets().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> output.getNpcMorphAssignmentsWithoutPresets().clear());
    }

    /** Templates, Morphs, and BoS previews decode from the same defensively owned bytes used for export. */
    @Test
    void generatedArtifactsDefensivelyOwnTheExactDisplayedUtf8Bytes() {
        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(populatedSession().getSnapshot(), false);

        assertEquals("templates.ini", output.getTemplatesArtifact().getFileName());
        assertEquals("morphs.ini", output.getMorphsArtifact().getFileName());
        assertEquals(2 + output.getBosJsonArtifacts().size(), output.getArtifacts().size());
        for (OutputArtifact artifact : output.getArtifacts()) {
            byte[] expected = artifact.getText().getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(expected, artifact.getBytes());
            byte[] exposed = artifact.getBytes();
            if (exposed.length > 0)
                exposed[0] ^= 0x7f;
            assertArrayEquals(expected, artifact.getBytes());
        }
    }
}
