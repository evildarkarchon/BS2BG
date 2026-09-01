package com.asdasfa.jbs2bg.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.data.SettingsTestSupport;

/**
 * Locks Project semantics behind the package-owned Jackson persistence adapter.
 */
final class ProjectJacksonCompatibilityTest {

    private static final Path FIXTURE_ROOT = Path.of("test-resources", "json-oracles", "project");
    @TempDir
    Path temporaryDirectory;

    /**
     * Seeds profile-distinct defaults so an order-dependent parser cannot pass accidentally.
     */
    @BeforeAll
    static void seedSliderDefaults() {
        Map<String, DefaultSliderValue> standard = new LinkedHashMap<>();
        standard.put("Waist", new DefaultSliderValue(0.2f, 1f));
        Map<String, DefaultSliderValue> uunp = new LinkedHashMap<>();
        uunp.put("Arms", new DefaultSliderValue(1f, 1f));
        SettingsTestSupport.installDefaults(standard, uunp);
    }

    /**
     * Restores the checked-in Settings pair after the compatibility oracle runs.
     */
    @AfterAll
    static void restoreSliderDefaults() {
        SettingsTestSupport.restoreRepositorySettings();
    }

    /**
     * Supplies malformed and schema fixtures with their permanent diagnostic contract.
     */
    private static Stream<InvalidFixture> invalidProjectFixtures() {
        return Stream.of(
                new InvalidFixture("malformed-syntax.jbs2bg", ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED,
                        "/SliderPresets"),
                new InvalidFixture("trailing-data.jbs2bg", ProjectDiagnosticCodes.PROJECT_JSON_TRAILING_DATA, "/"),
                new InvalidFixture("unknown-root-field.jbs2bg", ProjectDiagnosticCodes.PROJECT_FIELD_UNSUPPORTED,
                        "/Future~1~0Field"),
                new InvalidFixture("duplicate-root-field.jbs2bg", ProjectDiagnosticCodes.PROJECT_MEMBER_DUPLICATE,
                        "/SliderPresets"),
                new InvalidFixture("duplicate-fixed-field.jbs2bg", ProjectDiagnosticCodes.PROJECT_MEMBER_DUPLICATE,
                        "/SliderPresets/Alpha/isUUNP"),
                new InvalidFixture("duplicate-preset-identity.jbs2bg",
                        ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE, "/SliderPresets/alpha"),
                new InvalidFixture("duplicate-npc-identity.jbs2bg",
                        ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE, "/MorphedNPCs/Second"),
                new InvalidFixture("missing-nullable-member.jbs2bg",
                        ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID,
                        "/SliderPresets/Alpha/SetSliders/0/valueSmall"),
                new InvalidFixture("unicode-duplicate-identity.jbs2bg",
                        ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE, "/SliderPresets/ςhape"));
    }

    /**
     * Supplies every incompatible signed-integer spelling as a permanent file fixture.
     */
    private static Stream<InvalidFixture> invalidIntegerFixtures() {
        return Stream.of(
                new InvalidFixture("integer-decimal-invalid.jbs2bg", "", ""),
                new InvalidFixture("integer-exponent-invalid.jbs2bg", "", ""),
                new InvalidFixture("integer-overflow-invalid.jbs2bg", "", ""),
                new InvalidFixture("integer-underflow-invalid.jbs2bg", "", ""),
                new InvalidFixture("integer-string-invalid.jbs2bg", "", ""));
    }

    /**
     * Asserts one ordered recovery warning at its permanent Project path.
     */
    private static void assertDiagnostic(ProjectDiagnostic diagnostic, String path, String missingName) {
        assertEquals(ProjectDiagnosticCodes.SLIDER_PRESET_ASSIGNMENT_MISSING, diagnostic.getCode());
        assertEquals(path, diagnostic.getSourceLocation().getElement().orElseThrow());
        assertTrue(diagnostic.getSourceLocation().getLine().isPresent());
        assertTrue(diagnostic.getSourceLocation().getColumn().isPresent());
        assertTrue(diagnostic.getMessage().contains(missingName));
    }

    /**
     * Counts non-overlapping literal occurrences in canonical JSON text.
     */
    private static int countOccurrences(String text, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }

    /**
     * Asserts the complete independent meaning pinned by the representative semantic fixture.
     */
    private static void assertSemanticFixture(ProjectSnapshot snapshot) {
        assertEquals(2, snapshot.getSliderPresets().size());
        SliderPresetSnapshot cbbe = snapshot.getSliderPresets().getFirst();
        assertEquals("CBBE Curvy", cbbe.getName());
        assertFalse(cbbe.isUunp());
        assertEquals(2, cbbe.getSliderChoices().size());
        SliderChoiceSnapshot waist = cbbe.getSliderChoices().get(1);
        assertEquals("Waist", waist.getName());
        assertEquals(20, waist.getStoredSmallValue().getAsInt());
        assertEquals(80, waist.getStoredBigValue().getAsInt());
        assertEquals(10, waist.getPercentageMinimum());
        assertEquals(90, waist.getPercentageMaximum());

        SliderPresetSnapshot uunp = snapshot.getSliderPresets().get(1);
        assertEquals("UUNP Athletic", uunp.getName());
        assertTrue(uunp.isUunp());
        assertEquals("Arms", uunp.getSliderChoices().getFirst().getName());
        assertTrue(uunp.getSliderChoices().getFirst().isMissingDefault());

        assertEquals(1, snapshot.getCustomMorphTargets().size());
        assertEquals("All|Female", snapshot.getCustomMorphTargets().getFirst().getName());
        assertEquals(List.of("CBBE Curvy", "UUNP Athletic"),
                snapshot.getCustomMorphTargets().getFirst().getSliderPresetNames());

        assertEquals(1, snapshot.getNpcMorphAssignments().size());
        NpcMorphAssignmentSnapshot npc = snapshot.getNpcMorphAssignments().getFirst();
        assertEquals("Lydia", npc.getDisplayName());
        assertEquals("Skyrim.esm", npc.getPluginName());
        assertEquals("HousecarlWhiterun", npc.getEditorId());
        assertEquals("NordRace", npc.getRace());
        assertEquals("A2C94", npc.getFormId());
        assertEquals(List.of("UUNP Athletic"), npc.getSliderPresetNames());
    }

    /**
     * Compares the complete immutable Project content while excluding lifecycle metadata.
     */
    private static void assertProjectContentEquals(ProjectSnapshot expected, ProjectSnapshot actual) {
        assertEquals(expected.getSliderPresets().size(), actual.getSliderPresets().size());
        assertEquals(expected.getCustomMorphTargets().size(), actual.getCustomMorphTargets().size());
        assertEquals(expected.getNpcMorphAssignments().size(), actual.getNpcMorphAssignments().size());
        for (int index = 0; index < expected.getSliderPresets().size(); index++) {
            SliderPresetSnapshot expectedPreset = expected.getSliderPresets().get(index);
            SliderPresetSnapshot actualPreset = actual.getSliderPresets().get(index);
            assertEquals(expectedPreset.getName(), actualPreset.getName());
            assertEquals(expectedPreset.isUunp(), actualPreset.isUunp());
            assertEquals(expectedPreset.getSliderChoices().size(), actualPreset.getSliderChoices().size());
            for (int choiceIndex = 0; choiceIndex < expectedPreset.getSliderChoices().size(); choiceIndex++) {
                SliderChoiceSnapshot expectedChoice = expectedPreset.getSliderChoices().get(choiceIndex);
                SliderChoiceSnapshot actualChoice = actualPreset.getSliderChoices().get(choiceIndex);
                assertEquals(expectedChoice.getName(), actualChoice.getName());
                assertEquals(expectedChoice.isEnabled(), actualChoice.isEnabled());
                assertEquals(expectedChoice.getStoredSmallValue(), actualChoice.getStoredSmallValue());
                assertEquals(expectedChoice.getStoredBigValue(), actualChoice.getStoredBigValue());
                assertEquals(expectedChoice.getEffectiveSmallValue(), actualChoice.getEffectiveSmallValue());
                assertEquals(expectedChoice.getEffectiveBigValue(), actualChoice.getEffectiveBigValue());
                assertEquals(expectedChoice.getPercentageMinimum(), actualChoice.getPercentageMinimum());
                assertEquals(expectedChoice.getPercentageMaximum(), actualChoice.getPercentageMaximum());
                assertEquals(expectedChoice.isMissingDefault(), actualChoice.isMissingDefault());
            }
        }
        for (int index = 0; index < expected.getCustomMorphTargets().size(); index++) {
            CustomMorphTargetSnapshot expectedTarget = expected.getCustomMorphTargets().get(index);
            CustomMorphTargetSnapshot actualTarget = actual.getCustomMorphTargets().get(index);
            assertEquals(expectedTarget.getName(), actualTarget.getName());
            assertEquals(expectedTarget.getSliderPresetNames(), actualTarget.getSliderPresetNames());
        }
        for (int index = 0; index < expected.getNpcMorphAssignments().size(); index++) {
            NpcMorphAssignmentSnapshot expectedNpc = expected.getNpcMorphAssignments().get(index);
            NpcMorphAssignmentSnapshot actualNpc = actual.getNpcMorphAssignments().get(index);
            assertEquals(expectedNpc.getDisplayName(), actualNpc.getDisplayName());
            assertEquals(expectedNpc.getPluginName(), actualNpc.getPluginName());
            assertEquals(expectedNpc.getEditorId(), actualNpc.getEditorId());
            assertEquals(expectedNpc.getRace(), actualNpc.getRace());
            assertEquals(expectedNpc.getFormId(), actualNpc.getFormId());
            assertEquals(expectedNpc.getSliderPresetNames(), actualNpc.getSliderPresetNames());
        }
    }

    /**
     * The owned adapter and its canonical bytes retain the permanent semantic fixture meaning.
     */
    @Test
    void semanticFixtureMatchesExpectedProjectAndRoundTripsCanonicalBytes() throws Exception {
        Path fixture = FIXTURE_ROOT.resolve("semantic-equivalence.jbs2bg");

        ProjectJacksonAdapter.Candidate candidate = ProjectJacksonAdapter.read(fixture);

        assertSemanticFixture(candidate.snapshot());
        assertTrue(candidate.diagnostics().isEmpty());

        Path canonical = temporaryDirectory.resolve("semantic-canonical.jbs2bg");
        Files.write(canonical, ProjectJacksonAdapter.write(candidate.snapshot()));
        ProjectJacksonAdapter.Candidate reopened = ProjectJacksonAdapter.read(canonical);
        assertProjectContentEquals(candidate.snapshot(), reopened.snapshot());
        assertTrue(reopened.diagnostics().isEmpty());
    }

    /**
     * Reader and writer both expose deterministic domain and fixed-field ordering.
     */
    @Test
    void canonicalWriterUsesDomainOrderIndependentOfEncounterOrder() {
        ProjectJacksonAdapter.Candidate candidate = ProjectJacksonAdapter.read(
                FIXTURE_ROOT.resolve("canonical-ordering.jbs2bg"));

        String canonical = new String(ProjectJacksonAdapter.write(candidate.snapshot()), StandardCharsets.UTF_8);

        assertEquals(List.of("Alpha", "Zulu"), candidate.snapshot().getSliderPresets().stream()
                .map(SliderPresetSnapshot::getName).toList());
        assertEquals(List.of("Alpha Target", "Zulu Target"), candidate.snapshot().getCustomMorphTargets().stream()
                .map(CustomMorphTargetSnapshot::getName).toList());
        assertEquals(List.of("Early", "Late"), candidate.snapshot().getNpcMorphAssignments().stream()
                .map(NpcMorphAssignmentSnapshot::getDisplayName).toList());
        assertTrue(canonical.indexOf("\"SliderPresets\"") < canonical.indexOf("\"CustomMorphTargets\""));
        assertTrue(canonical.indexOf("\"CustomMorphTargets\"") < canonical.indexOf("\"MorphedNPCs\""));
        assertTrue(canonical.indexOf("\"isUUNP\"") < canonical.indexOf("\"SetSliders\""));
    }

    /**
     * Fixed-schema member order cannot change UUNP effective-value reconstruction.
     */
    @Test
    void fixedSchemaMemberOrderDoesNotChangeProjectMeaning() {
        Path fixture = FIXTURE_ROOT.resolve("member-order-uunp.jbs2bg");

        ProjectSnapshot snapshot = ProjectJacksonAdapter.read(fixture).snapshot();
        SliderPresetSnapshot preset = snapshot.getSliderPresets().getFirst();
        SliderChoiceSnapshot choice = preset.getSliderChoices().getFirst();

        assertEquals("UUNP Ordered", preset.getName());
        assertTrue(preset.isUunp());
        assertEquals("Arms", choice.getName());
        assertTrue(choice.getStoredSmallValue().isEmpty());
        assertTrue(choice.getStoredBigValue().isEmpty());
        assertEquals(100, choice.getEffectiveSmallValue());
        assertEquals(100, choice.getEffectiveBigValue());
        assertFalse(choice.isMissingDefault());
    }

    /**
     * Missing references recover in encounter order and preserve every valid relationship.
     */
    @Test
    void recoveryFixtureRetainsExpectedProjectAndOrderedDiagnostics() {
        Path fixture = FIXTURE_ROOT.resolve("recovery-ordered-diagnostics.jbs2bg");

        ProjectJacksonAdapter.Candidate candidate = ProjectJacksonAdapter.read(fixture);

        assertEquals(List.of("Alpha", "Beta"), candidate.snapshot().getSliderPresets().stream()
                .map(SliderPresetSnapshot::getName).toList());
        assertEquals(List.of("Alpha", "Beta"),
                candidate.snapshot().getCustomMorphTargets().getFirst().getSliderPresetNames());
        assertEquals(List.of("Beta"), candidate.snapshot().getNpcMorphAssignments().getFirst().getSliderPresetNames());
        assertEquals(ProjectLifecycleStatus.RECOVERED, candidate.snapshot().getLifecycleStatus());
        assertTrue(candidate.snapshot().isDirty());
        assertEquals(2, candidate.diagnostics().size());
        assertDiagnostic(candidate.diagnostics().getFirst(), "/CustomMorphTargets/Target/SliderPresets/1",
                "Missing Target");
        assertDiagnostic(candidate.diagnostics().get(1), "/MorphedNPCs/NPC/SliderPresets/1", "Missing NPC");
    }

    /**
     * Repeated display-name members remain visible because NPC identity is plugin plus editor ID.
     */
    @Test
    void repeatedNpcDisplayNamesRemainVisibleAndRoundTrip() throws Exception {
        Path fixture = FIXTURE_ROOT.resolve("legal-repeated-npc-display-name.jbs2bg");

        ProjectJacksonAdapter.Candidate candidate = ProjectJacksonAdapter.read(fixture);
        byte[] canonical = ProjectJacksonAdapter.write(candidate.snapshot());
        Path written = temporaryDirectory.resolve("repeated-npcs.jbs2bg");
        Files.write(written, canonical);

        assertEquals(2, candidate.snapshot().getNpcMorphAssignments().size());
        assertEquals(2, countOccurrences(new String(canonical, StandardCharsets.UTF_8), "\"Guard\""));
        assertEquals(2, ProjectJacksonAdapter.read(written).snapshot().getNpcMorphAssignments().size());
    }

    /**
     * Explicit null endpoints serialize, while unchanged synthesized defaults remain omitted.
     */
    @Test
    void explicitNullAndOmittedDefaultsRemainDistinct() {
        ProjectJacksonAdapter.Candidate candidate = ProjectJacksonAdapter.read(
                FIXTURE_ROOT.resolve("semantic-equivalence.jbs2bg"));

        String canonical = new String(ProjectJacksonAdapter.write(candidate.snapshot()), StandardCharsets.UTF_8);

        assertTrue(canonical.contains("\"valueSmall\":null"));
        assertTrue(canonical.contains("\"valueBig\":null"));
        assertFalse(canonical.contains("\"name\":\"Arms\""));
    }

    /**
     * Canonical writing re-enters the package-private Project integrity authority before bytes exist.
     */
    @Test
    void canonicalWriterRejectsAProjectWithDanglingRelationships() {
        ProjectSnapshot invalid = new ProjectSnapshot(
                List.of(new SliderPresetSnapshot("Alpha", false, List.of())),
                List.of(new CustomMorphTargetSnapshot("Target", List.of("Missing"))),
                List.of(), Optional.empty(), true, ProjectLifecycleStatus.UNTITLED);

        ProjectJacksonAdapter.ProjectFormatException exception = assertThrows(
                ProjectJacksonAdapter.ProjectFormatException.class,
                () -> ProjectJacksonAdapter.write(invalid));

        assertEquals(ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, exception.code());
        assertEquals("/", exception.path());
    }

    /**
     * Ordinary Unicode and escaped path characters retain display casing through canonical writing.
     */
    @Test
    void unicodeContentRoundTripsWithoutIdentityOrPathDrift() throws Exception {
        Path fixture = FIXTURE_ROOT.resolve("unicode-content.jbs2bg");
        ProjectJacksonAdapter.Candidate candidate = ProjectJacksonAdapter.read(fixture);
        Path written = temporaryDirectory.resolve("unicode-written.jbs2bg");
        Files.write(written, ProjectJacksonAdapter.write(candidate.snapshot()));

        ProjectJacksonAdapter.Candidate reopened = ProjectJacksonAdapter.read(written);

        assertProjectContentEquals(candidate.snapshot(), reopened.snapshot());
        assertEquals("İstanbul/˜😀", reopened.snapshot().getSliderPresets().getFirst().getName());
    }

    /**
     * Fixed-schema, dynamic-identity, malformed, and trailing failures keep stable codec-free diagnostics.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProjectFixtures")
    void invalidFixturesReportStableCodePathAndCoordinates(InvalidFixture fixture) {
        Path source = FIXTURE_ROOT.resolve(fixture.fileName());

        ProjectJacksonAdapter.ProjectFormatException exception = assertThrows(
                ProjectJacksonAdapter.ProjectFormatException.class, () -> ProjectJacksonAdapter.read(source));

        assertEquals(fixture.code(), exception.code());
        assertEquals(fixture.path(), exception.path());
        assertEquals(source.toAbsolutePath().normalize().toString(), exception.source());
        assertTrue(exception.line() > 0);
        assertTrue(exception.column() > 0);
        assertNull(exception.getCause());
    }

    /**
     * Project integer tokens retain exact Integer.parseInt syntax and signed bounds.
     */
    @Test
    void integerBoundsAndNegativeZeroMatchThePermanentExpectedValues() {
        Path fixture = FIXTURE_ROOT.resolve("integer-bounds-valid.jbs2bg");

        ProjectJacksonAdapter.Candidate candidate = ProjectJacksonAdapter.read(fixture);

        SliderChoiceSnapshot choice = candidate.snapshot().getSliderPresets().getFirst().getSliderChoices().getFirst();
        assertEquals(Integer.MIN_VALUE, choice.getStoredSmallValue().getAsInt());
        assertEquals(Integer.MAX_VALUE, choice.getStoredBigValue().getAsInt());
        assertEquals(0, choice.getPercentageMinimum());
    }

    /**
     * Decimal, exponent, overflow, underflow, and wrong-kind Project integers are never coerced.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIntegerFixtures")
    void invalidIntegerLexemesAreRejectedAtTheirMember(InvalidFixture fixture) {
        Path source = FIXTURE_ROOT.resolve(fixture.fileName());

        ProjectJacksonAdapter.ProjectFormatException exception = assertThrows(
                ProjectJacksonAdapter.ProjectFormatException.class, () -> ProjectJacksonAdapter.read(source));

        assertEquals(ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, exception.code());
        assertEquals("/SliderPresets/Bounds/SetSliders/0/valueSmall", exception.path());
    }

    /**
     * The practical numeric-lexeme fixture proves the owned 128-character stream constraint.
     */
    @Test
    void numericLexemeBeyondOwnedLimitReportsResourceFailure() {
        Path source = FIXTURE_ROOT.resolve("resource-number-too-long.jbs2bg");

        ProjectJacksonAdapter.ProjectFormatException exception = assertThrows(
                ProjectJacksonAdapter.ProjectFormatException.class, () -> ProjectJacksonAdapter.read(source));

        assertEquals(ProjectDiagnosticCodes.PROJECT_JSON_RESOURCE_LIMIT, exception.code());
        assertEquals("/SliderPresets/Bounds/SetSliders/0/valueSmall", exception.path());
        assertTrue(exception.line() > 0);
        assertTrue(exception.column() > 0);
    }

    /**
     * UTF-8 byte limits reject supplementary text before a candidate can publish.
     */
    @Test
    void supplementaryStringBeyondOwnedUtf8LimitReportsResourceFailure() throws Exception {
        Properties limits = new Properties();
        try (java.io.Reader descriptor = Files.newBufferedReader(FIXTURE_ROOT.resolve("resource-limits.properties"))) {
            limits.load(descriptor);
        }
        int maximumBytes = Integer.parseInt(limits.getProperty("maximumTextUtf8Bytes"));
        String oversizedName = "😀".repeat(maximumBytes / 4 + 1);
        Path source = temporaryDirectory.resolve("resource-string-too-long.jbs2bg");
        Files.writeString(source,
                "{\"SliderPresets\":{\"Wide\":{\"isUUNP\":false,\"SetSliders\":[{"
                        + "\"name\":\"" + oversizedName + "\",\"enabled\":true,\"valueSmall\":0,"
                        + "\"valueBig\":1,\"pctMin\":0,\"pctMax\":100}]}},"
                        + "\"CustomMorphTargets\":{},\"MorphedNPCs\":{}}");

        ProjectJacksonAdapter.ProjectFormatException exception = assertThrows(
                ProjectJacksonAdapter.ProjectFormatException.class, () -> ProjectJacksonAdapter.read(source));

        assertEquals(ProjectDiagnosticCodes.PROJECT_JSON_RESOURCE_LIMIT, exception.code());
        assertEquals("/SliderPresets/Wide/SetSliders/0/name", exception.path());
    }

    /**
     * Permanent invalid-fixture descriptor.
     */
    private record InvalidFixture(String fileName, String code, String path) {
        @Override
        public String toString () {
            return fileName;
        }
    }
}
