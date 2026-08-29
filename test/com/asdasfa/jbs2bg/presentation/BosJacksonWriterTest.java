package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;

/** Freezes exact BoS bytes produced by the non-production Jackson writer. */
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
     * Proves the checked-in calculated-value golden matches both old and new development adapters
     * across inversion, multiplier, float, half-up rounding, zero, and exponent behavior.
     */
    @Test
    void calculatedValueGoldenMatchesTheUnchangedProductionRoute() throws Exception {
        Path golden = Path.of("test-resources", "json-oracles", "bos", "calculated.json");
        byte[] expected = Files.readAllBytes(golden);
        Map<String, Float> multipliers = standardMultipliers();
        List<String> inverted = standardInverted();
        Map<String, Float> previousMultipliers = new LinkedHashMap<>(multipliers);
        List<String> previousInverted = new ArrayList<>(inverted);
        try {
            multipliers.clear();
            multipliers.put("Breasts", Float.valueOf(1.25f));
            multipliers.put("OracleExponent", Float.valueOf(1.0E8f));
            inverted.clear();
            inverted.add("Breasts");

            ProjectSession session = ProjectSessions.create();
            session.newProject();
            session.apply(SliderPresetEdits.create("Alpha"));
            session.apply(SliderPresetEdits.setSliderChoice("Alpha", new SliderChoiceSnapshot(
                    "Breasts", true, Integer.valueOf(10), Integer.valueOf(100),
                    10, 100, 100, 100, false)));
            session.apply(SliderPresetEdits.setSliderChoice("Alpha", new SliderChoiceSnapshot(
                    "OracleExponent", true, Integer.valueOf(25), Integer.valueOf(100),
                    25, 100, 100, 100, false)));

            String legacy = ProjectOutputFormatter.generate(session.getSnapshot(), false)
                    .getBosJsonByFileName().get("Alpha.json");
            Utf8Json jackson = BosJacksonWriter.write(new BosJacksonWriter.BosDocument(
                    "Alpha", List.of("Breasts", "OracleExponent"),
                    List.of("0", "1.0E8"), List.of("1.13", "2.5E7")));

            assertArrayEquals(jackson.bytes(), legacy.getBytes(StandardCharsets.UTF_8), legacy);
            assertArrayEquals(expected, legacy.getBytes(StandardCharsets.UTF_8), legacy);
            assertArrayEquals(expected, jackson.bytes());
        } finally {
            multipliers.clear();
            multipliers.putAll(previousMultipliers);
            inverted.clear();
            inverted.addAll(previousInverted);
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

    /**
     * Accesses the legacy mutable Settings collection only for the temporary old/new differential seam.
     * Production visibility stays unchanged until the Settings cutover issue.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Float> standardMultipliers() throws ReflectiveOperationException {
        Field field = Settings.class.getDeclaredField("MULTIPLIERS");
        field.setAccessible(true);
        return (Map<String, Float>) field.get(null);
    }

    /**
     * Accesses legacy inversion state alongside multipliers and restores it in the test's finally block.
     */
    @SuppressWarnings("unchecked")
    private static List<String> standardInverted() throws ReflectiveOperationException {
        Field field = Settings.class.getDeclaredField("INVERTED");
        field.setAccessible(true);
        return (List<String>) field.get(null);
    }
}
