package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.SettingsTestSupport;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;

/** Freezes exact BoS bytes produced by the owned Jackson writer and production formatter. */
final class BosJacksonWriterTest {

    /** Covers the complete escaping, ordering, numeric-lexeme, UTF-8, BOM, and newline profile. */
    @Test
    void writesCanonicalUtf8OrderingEscapesAndNewlinePolicy() throws Exception {
        BosJacksonWriter.BosDocument document = new BosJacksonWriter.BosDocument(
                "Controls\b\f\n\r\t\"\\/\u0001\u001f\u2028\u2029 é😀",
                List.of("A/β", "B"), List.of("1.0", "1E7"), List.of("-0", "0.005"));
        byte[] expected = Files.readAllBytes(Path.of("test-resources", "json-oracles", "bos", "canonical.json"));

        Utf8Json written = BosJacksonWriter.write(document);
        byte[] actual = written.bytes();

        assertArrayEquals(expected, actual,
                () -> new String(actual, StandardCharsets.UTF_8));
        assertFalse(actual.length >= 3 && actual[0] == (byte) 0xEF
                && actual[1] == (byte) 0xBB && actual[2] == (byte) 0xBF);
        assertFalse(actual[actual.length - 1] == '\n' || actual[actual.length - 1] == '\r');
        assertEquals(new String(expected, StandardCharsets.UTF_8), written.text());
    }

    /** Covers empty-object spelling and defensive byte ownership at the adapter result seam. */
    @Test
    void writesTheEmptyDocumentGoldenAndDefensivelyOwnsItsBytes() throws Exception {
        byte[] expected = Files.readAllBytes(Path.of("test-resources", "json-oracles", "bos", "empty.json"));
        Utf8Json written = BosJacksonWriter.write(new BosJacksonWriter.BosDocument(
                "Empty", List.of(), List.of(), List.of()));

        byte[] exposed = written.bytes();
        exposed[0] = 'x';

        assertArrayEquals(expected, written.bytes());
        assertNotEquals('x', written.bytes()[0]);
    }

    /**
     * Proves production owns the checked-in calculated bytes across inversion,
     * multiplier, float, half-up rounding, zero, and exponent behavior.
     */
    @Test
    void calculatedValueGoldenIsTheDefensivelyOwnedProductionArtifact() throws Exception {
        Path golden = Path.of("test-resources", "json-oracles", "bos", "calculated.json");
        byte[] expected = Files.readAllBytes(golden);
        Map<String, Float> multipliers = Map.of("Breasts", Float.valueOf(1.25f),
                "OracleExponent", Float.valueOf(1.0E8f));
        SettingsTestSupport.installStandardOutput(multipliers, List.of("Breasts"));
        try {
            ProjectSession session = ProjectSessions.create();
            session.newProject();
            session.apply(SliderPresetEdits.create("Alpha"));
            session.apply(SliderPresetEdits.setSliderChoice("Alpha", new SliderChoiceSnapshot(
                    "Breasts", true, Integer.valueOf(10), Integer.valueOf(100),
                    10, 100, 100, 100, false)));
            session.apply(SliderPresetEdits.setSliderChoice("Alpha", new SliderChoiceSnapshot(
                    "OracleExponent", true, Integer.valueOf(25), Integer.valueOf(100),
                    25, 100, 100, 100, false)));

            BosJsonArtifact artifact = ProjectOutputFormatter.generate(session.getSnapshot(), false)
                    .getBosJsonArtifacts().get(0);
            byte[] exposed = artifact.getBytes();
            exposed[0] = 'x';

            assertEquals("Alpha", artifact.getSliderPresetName());
            assertEquals("Alpha.json", artifact.getFileName());
            assertEquals(new String(expected, StandardCharsets.UTF_8), artifact.getText());
            assertArrayEquals(expected, artifact.getBytes());
        } finally {
            SettingsTestSupport.restoreRepositorySettings();
        }
    }

    /** Proves invalid structure or non-finite/unrepresentable lexemes fail before a value can publish. */
    @Test
    void rejectsInvalidNumericDocumentsBeforeReturningCanonicalBytes() {
        assertThrows(IllegalArgumentException.class, () -> BosJacksonWriter.write(
                new BosJacksonWriter.BosDocument("Mismatch", List.of("A"), List.of(), List.of("0"))));
        for (String invalid : List.of("NaN", "Infinity", "01", "+1", "1.", "1e999")) {
            assertThrows(IllegalArgumentException.class, () -> BosJacksonWriter.write(
                    new BosJacksonWriter.BosDocument("Invalid", List.of("A"), List.of(invalid), List.of("0"))));
        }
    }

    /** Production reports non-finite calculations only after retaining every attempted filename mapping. */
    @Test
    void rejectsNonFiniteProductionValuesWithCompleteMappingsAndDiagnostics() {
        SettingsTestSupport.installStandardOutput(Map.of("Overflow", Float.valueOf(Float.MAX_VALUE)), List.of());
        try {
            ProjectSession session = ProjectSessions.create();
            session.newProject();
            session.apply(SliderPresetEdits.create("Broken"));
            session.apply(SliderPresetEdits.setSliderChoice("Broken", new SliderChoiceSnapshot(
                    "Overflow", true, Integer.valueOf(10), Integer.valueOf(200),
                    10, 200, 100, 100, false)));
            session.apply(SliderPresetEdits.create("Safe"));

            BosOutputException exception = assertThrows(BosOutputException.class,
                    () -> ProjectOutputFormatter.generate(session.getSnapshot(), false));

            assertEquals(2, exception.getFileNameMappings().size());
            assertEquals("Broken.json", exception.getFileNameMappings().get(0).getFileName().orElseThrow());
            assertEquals("Safe.json", exception.getFileNameMappings().get(1).getFileName().orElseThrow());
            assertEquals(1, exception.getDiagnostics().size());
            assertEquals("BOS_VALUE_NON_FINITE", exception.getDiagnostics().get(0).getCode());
            assertEquals("Broken", exception.getDiagnostics().get(0).getSliderPresetName());
        } finally {
            SettingsTestSupport.restoreRepositorySettings();
        }
    }

    /** Filename and finite-input overflow failures are reported together before publishing. */
    @Test
    void reportsFilenameAndNonFiniteFailuresTogetherBeforePublishing() {
        SettingsTestSupport.installStandardOutput(Map.of("Overflow", Float.valueOf(Float.MAX_VALUE)), List.of());
        try {
            ProjectSession session = ProjectSessions.create();
            session.newProject();
            session.apply(SliderPresetEdits.create("Broken\uD800"));
            session.apply(SliderPresetEdits.create("Infinite Value"));
            session.apply(SliderPresetEdits.setSliderChoice("Infinite Value", new SliderChoiceSnapshot(
                    "Overflow", true, Integer.valueOf(10), Integer.valueOf(200),
                    10, 200, 100, 100, false)));

            BosOutputException exception = assertThrows(BosOutputException.class,
                    () -> ProjectOutputFormatter.generate(session.getSnapshot(), false));

            assertEquals(2, exception.getFileNameMappings().size());
            assertTrue(exception.getFileNameMappings().get(0).getFileName().isEmpty());
            assertEquals("Infinite Value.json",
                    exception.getFileNameMappings().get(1).getFileName().orElseThrow());
            assertEquals(List.of("BOS_FILENAME_UNREPRESENTABLE", "BOS_VALUE_NON_FINITE"),
                    exception.getDiagnostics().stream().map(BosOutputDiagnostic::getCode).toList());
        } finally {
            SettingsTestSupport.restoreRepositorySettings();
        }
    }

}
