package com.asdasfa.jbs2bg.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Locks paired Settings semantics behind the non-production Jackson adapter. */
final class SettingsJacksonAdapterTest {
    private static final Path FIXTURE_ROOT = Path.of("test-resources", "json-oracles", "settings");

    /** Verifies paired semantics, forward-compatible warnings, float conversion, and first-entry deduplication. */
    @Test
    void pairedFixtureProducesOneDetachedCandidateWithDefaultsWarningsAndDeduplication() {
        SettingsJacksonAdapter.SettingsCandidate candidate = SettingsJacksonAdapter.readPair(
                fixture("standard.json"), fixture("uunp.json"));

        assertEquals(0f, candidate.standard().defaults().get("Waist").valueSmall());
        assertEquals(1f, candidate.standard().defaults().get("Waist").valueBig());
        assertEquals(Float.intBitsToFloat(0x80000000),
                candidate.standard().multipliers().get("SignedZero"));
        assertEquals(2f, candidate.standard().multipliers().get("Exponent"));
        assertEquals(0f, candidate.standard().multipliers().get("Underflow"));
        assertEquals(0.5f, candidate.standard().defaults().get("Ångström/形").valueSmall());
        assertEquals(List.of("Waist", "Ångström/形"), candidate.standard().inverted());
        assertEquals(List.of("/Defaults/Waist/FutureDefault", "/Future"),
                candidate.diagnostics().stream().map(SettingsJacksonAdapter.SettingsDiagnostic::path).toList());
        assertEquals(2f, candidate.uunp().multipliers().get("Arms"));
    }

    /**
     * Freezes the Settings writer's canonical UTF-8 bytes for both profiles and verifies that
     * callers cannot mutate the pair after construction.
     *
     * @throws IOException when a permanent fixture cannot be read
     */
    @Test
    void canonicalPairWritingMatchesPermanentGoldensAndDefensivelyOwnsBytes() throws IOException {
        SettingsJacksonAdapter.SettingsCandidate candidate = SettingsJacksonAdapter.readPair(
                fixture("standard.json"), fixture("uunp.json"));

        SettingsJacksonAdapter.SettingsPairBytes encoded = SettingsJacksonAdapter.writePair(candidate);

        byte[] expectedStandard = canonicalFixtureBytes("standard.canonical.json");
        byte[] expectedUunp = canonicalFixtureBytes("uunp.canonical.json");
        assertArrayEquals(expectedStandard, encoded.standardUtf8());
        assertArrayEquals(expectedUunp, encoded.uunpUtf8());
        byte[] exposed = encoded.standardUtf8();
        exposed[0] = '[';
        assertArrayEquals(expectedStandard, encoded.standardUtf8());
    }

    /** Rejects duplicate fixed fields and dynamic slider keys at their stable member paths. */
    @Test
    void duplicateFixedAndDynamicMembersAreRejected() {
        SettingsJacksonAdapter.SettingsFormatException fixed = assertThrows(
                SettingsJacksonAdapter.SettingsFormatException.class,
                () -> SettingsJacksonAdapter.readPair(fixture("standard.json"), fixture("duplicate-uunp.json")));
        assertEquals("SETTINGS_MEMBER_DUPLICATE", fixed.code());
        assertEquals("/Defaults/Arms/valueSmall", fixed.path());

        SettingsJacksonAdapter.SettingsFormatException dynamic = assertThrows(
                SettingsJacksonAdapter.SettingsFormatException.class,
                () -> SettingsJacksonAdapter.readPair(fixture("duplicate-dynamic.json"), fixture("uunp.json")));
        assertEquals("SETTINGS_MEMBER_DUPLICATE", dynamic.code());
        assertEquals("/Defaults/Arms", dynamic.path());
    }

    /** Rejects finite-conversion overflow while retaining an actionable source, path, and coordinates. */
    @Test
    void nonFiniteFloatConversionIsRejected() {
        SettingsJacksonAdapter.SettingsFormatException exception = assertFixtureFailure("non-finite.json");

        assertEquals("SETTINGS_NUMBER_NON_FINITE", exception.code());
        assertEquals("/Multipliers/Overflow", exception.path());
        assertTrue(exception.line() > 0);
        assertTrue(exception.column() > 0);
    }

    /** Translates malformed syntax and owned stream-limit failures without exposing Jackson exceptions. */
    @Test
    void malformedAndResourceLimitFixturesProduceStableDiagnostics() {
        SettingsJacksonAdapter.SettingsFormatException malformed = assertFixtureFailure("malformed.json");
        assertEquals("SETTINGS_JSON_MALFORMED", malformed.code());
        assertEquals("/Multipliers", malformed.path());
        assertTrue(malformed.line() > 0);
        assertTrue(malformed.column() > 0);
        assertTrue(malformed.getCause() == null
                || !malformed.getCause().getClass().getName().startsWith("tools.jackson"));

        SettingsJacksonAdapter.SettingsFormatException limited = assertFixtureFailure("resource-limit.json");
        assertEquals("SETTINGS_RESOURCE_LIMIT", limited.code());
        assertEquals("/Future", limited.path());
        assertTrue(limited.line() > 0);
        assertTrue(limited.column() > 0);
    }

    /**
     * Treats the pair as the atomic publication value: a later failed construction cannot alter the
     * previously returned candidate, and none of its nested collections are mutable.
     */
    @Test
    void candidateIsImmutableAndFailedSecondDocumentCannotChangePublishedPair() {
        SettingsJacksonAdapter.SettingsCandidate published = SettingsJacksonAdapter.readPair(
                fixture("standard.json"), fixture("uunp.json"));

        SettingsJacksonAdapter.SettingsFormatException exception = assertThrows(
                SettingsJacksonAdapter.SettingsFormatException.class,
                () -> SettingsJacksonAdapter.readPair(fixture("standard.json"), fixture("duplicate-uunp.json")));

        assertEquals("SETTINGS_MEMBER_DUPLICATE", exception.code());
        assertEquals("/Defaults/Arms/valueSmall", exception.path());
        assertEquals(2f, published.uunp().multipliers().get("Arms"));
        assertThrows(UnsupportedOperationException.class, () -> published.standard().defaults().clear());
        assertThrows(UnsupportedOperationException.class, () -> published.standard().multipliers().clear());
        assertThrows(UnsupportedOperationException.class, () -> published.standard().inverted().clear());
        assertThrows(UnsupportedOperationException.class, () -> published.diagnostics().clear());
    }

    /** Reads one invalid Standard fixture with the permanent valid UUNP partner. */
    private static SettingsJacksonAdapter.SettingsFormatException assertFixtureFailure(String fixtureName) {
        SettingsJacksonAdapter.SettingsFormatException exception = assertThrows(
                SettingsJacksonAdapter.SettingsFormatException.class,
                () -> SettingsJacksonAdapter.readPair(fixture(fixtureName), fixture("uunp.json")));
        assertEquals(fixture(fixtureName).toString(), exception.source());
        return exception;
    }

    /**
     * Reads canonical fixture text as repository LF bytes so Git's Windows checkout policy cannot
     * turn a Settings whitespace choice into a platform-dependent test result.
     *
     * @throws IOException when the permanent fixture cannot be read
     */
    private static byte[] canonicalFixtureBytes(String name) throws IOException {
        return Files.readString(fixture(name), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
    }

    /** Resolves one permanent Settings oracle from the repository fixture root. */
    private static Path fixture(String name) {
        return FIXTURE_ROOT.resolve(name);
    }
}
