package com.asdasfa.jbs2bg.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.presentation.ProjectGeneratedOutput;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks paired production Settings semantics and their generated-output consumption.
 */
final class SettingsJacksonAdapterTest {
    private static final Path FIXTURE_ROOT = Path.of("test-resources", "json-oracles", "settings");

    /**
     * Reads one invalid Standard fixture with the permanent valid UUNP partner.
     */
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
        return Files.readString(fixture(name))
                .replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Resolves one permanent Settings oracle from the repository fixture root.
     */
    private static Path fixture(String name) {
        return FIXTURE_ROOT.resolve(name);
    }

    /**
     * Restores the repository Settings pair after every process-wide publication test.
     */
    @AfterEach
    void restoreRepositorySettings() {
        SettingsTestSupport.restoreRepositorySettings();
    }

    /**
     * Publishes the validated pair through the production Settings seam and proves a later rejected pair cannot
     * replace either live profile.
     *
     * @param directory isolated working directory containing the two production filenames
     * @throws IOException when a permanent fixture cannot be copied into the isolated directory
     */
    @Test
    void productionLoadPublishesBothProfilesTogetherAndPreservesThemOnFailure(@TempDir Path directory)
            throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        Files.copy(fixture("standard.json"), standard);
        Files.copy(fixture("uunp.json"), uunp);

        Settings.InitializationResult loaded = Settings.initialize(directory);

        assertTrue(loaded.isSuccessful());
        assertEquals(List.of("/Defaults/Waist/FutureDefault", "/Future"),
                loaded.getDiagnostics().stream().map(Settings.Diagnostic::getPath).toList());
        assertEquals(2f, Settings.getMultiplier("Exponent"));
        assertEquals(2f, Settings.getMultiplierUUNP("Arms"));
        assertTrue(Settings.isInverted("Waist"));

        Files.copy(fixture("duplicate-uunp.json"), uunp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Settings.InitializationResult rejected = Settings.initialize(directory);

        assertFalse(rejected.isSuccessful());
        Settings.Failure failure = rejected.getFailure().orElseThrow();
        assertEquals("SETTINGS_MEMBER_DUPLICATE", failure.getCode());
        assertEquals(uunp.toString(), failure.getSource());
        assertEquals("/Defaults/Arms/valueSmall", failure.getPath());
        assertEquals(2f, Settings.getMultiplier("Exponent"));
        assertEquals(2f, Settings.getMultiplierUUNP("Arms"));
        assertTrue(Settings.isInverted("Waist"));
    }

    /**
     * Rejects every remaining required invalid-input family through the production seam while retaining both
     * previously published live profiles and their stable member paths.
     *
     * @param directory isolated production Settings directory
     * @throws IOException when permanent fixtures cannot be copied
     */
    @Test
    void productionLoadPreservesTheLivePairAcrossMalformedNonFiniteAndLimitedInputs(@TempDir Path directory)
            throws IOException {
        Path standard = directory.resolve("settings.json");
        Files.copy(fixture("standard.json"), standard);
        Files.copy(fixture("uunp.json"), directory.resolve("settings_UUNP.json"));
        assertTrue(Settings.initialize(directory).isSuccessful());

        List<InvalidProductionFixture> invalidFixtures = List.of(
                new InvalidProductionFixture("non-finite.json", "SETTINGS_NUMBER_NON_FINITE",
                        "/Multipliers/Overflow"),
                new InvalidProductionFixture("malformed.json", "SETTINGS_JSON_MALFORMED", "/Multipliers"),
                new InvalidProductionFixture("resource-limit.json", "SETTINGS_RESOURCE_LIMIT", "/Future"));
        for (InvalidProductionFixture invalid : invalidFixtures) {
            Files.copy(fixture(invalid.name()), standard, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            Settings.InitializationResult rejected = Settings.initialize(directory);

            assertFalse(rejected.isSuccessful(), invalid.name());
            Settings.Failure failure = rejected.getFailure().orElseThrow();
            assertEquals(invalid.code(), failure.getCode(), invalid.name());
            assertEquals(invalid.path(), failure.getPath(), invalid.name());
            assertEquals(2f, Settings.getMultiplier("Exponent"), invalid.name());
            assertEquals(2f, Settings.getMultiplierUUNP("Arms"), invalid.name());
            assertTrue(Settings.isInverted("Waist"), invalid.name());
        }
    }

    /**
     * A nonexistent working directory is classified structurally as lock acquisition, not by message text.
     */
    @Test
    void missingWorkingDirectoryProducesTheOwnedLockFailure(@TempDir Path directory) {
        Path missing = directory.resolve("missing");

        Settings.InitializationResult rejected = Settings.initialize(missing);

        assertFalse(rejected.isSuccessful());
        Settings.Failure failure = rejected.getFailure().orElseThrow();
        assertEquals("SETTINGS_LOCK_FAILED", failure.getCode());
        assertEquals(missing.toString(), failure.getSource());
        assertEquals("/", failure.getPath());
    }

    /**
     * Creates the accepted built-in profiles only when the working directory has no Settings sources, then
     * verifies that both production filenames contain the adapter's canonical bytes.
     *
     * @param directory isolated empty working directory
     * @throws IOException when the published pair cannot be inspected
     */
    @Test
    void missingSettingsAreCreatedAsOneCanonicalPair(@TempDir Path directory) throws IOException {
        Settings.InitializationResult result = Settings.initialize(directory);

        assertTrue(result.isSuccessful());
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        SettingsJacksonAdapter.SettingsCandidate candidate = SettingsJacksonAdapter.readPair(standard, uunp);
        SettingsJacksonAdapter.SettingsPairBytes canonical = SettingsJacksonAdapter.writePair(candidate);
        assertArrayEquals(canonical.standardUtf8(), Files.readAllBytes(standard));
        assertArrayEquals(canonical.uunpUtf8(), Files.readAllBytes(uunp));
        assertEquals(20, Settings.getDefaultValueSmall("Breasts"));
        assertEquals(100, Settings.getDefaultValueSmallUUNP("Breasts"));
        try (var entries = Files.list(directory)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith(".bs2bg-settings-")));
        }
    }

    /**
     * Simulates a process stop after the Standard replacement was installed and requires startup to restore the
     * complete prior pair before parsing or publishing live values.
     *
     * @param directory isolated working directory containing the interrupted transaction
     * @throws IOException when the interrupted state cannot be assembled or inspected
     */
    @Test
    void interruptedPublicationRecoversThePriorPairBeforeLoading(@TempDir Path directory) throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        Files.copy(fixture("standard.json"), standard);
        Files.copy(fixture("uunp.json"), uunp);
        byte[] priorStandard = Files.readAllBytes(standard);
        byte[] priorUunp = Files.readAllBytes(uunp);
        Path transaction = Files.createDirectory(directory.resolve(".bs2bg-settings-stage-interrupted"));
        Files.move(standard, transaction.resolve("standard.backup"));
        Files.move(uunp, transaction.resolve("uunp.backup"));
        Files.copy(fixture("standard.canonical.json"), standard);

        Settings.InitializationResult result = Settings.initialize(directory);

        assertTrue(result.isSuccessful());
        assertEquals("SETTINGS_PUBLICATION_RECOVERED", result.getDiagnostics().getFirst().getCode());
        assertArrayEquals(priorStandard, Files.readAllBytes(standard));
        assertArrayEquals(priorUunp, Files.readAllBytes(uunp));
        assertFalse(Files.exists(transaction));
        assertEquals(2f, Settings.getMultiplierUUNP("Arms"));
    }

    /**
     * Retains a valid legacy Standard document, supplies the accepted built-in UUNP profile, and republishes
     * both canonical documents when only one production source exists.
     *
     * @param directory isolated working directory with only the Standard source
     * @throws IOException when the legacy source cannot be copied or the published pair cannot be inspected
     */
    @Test
    void oneMissingProfileUsesItsBuiltInDefaultAndRepublishesTheCompletePair(@TempDir Path directory)
            throws IOException {
        Path standard = directory.resolve("settings.json");
        Path uunp = directory.resolve("settings_UUNP.json");
        Files.copy(fixture("standard.json"), standard);

        Settings.InitializationResult result = Settings.initialize(directory);

        assertTrue(result.isSuccessful());
        assertTrue(Files.isRegularFile(uunp));
        assertEquals(2f, Settings.getMultiplier("Exponent"));
        assertEquals(1f, Settings.getMultiplierUUNP("Arms"));
        assertEquals(100, Settings.getDefaultValueSmallUUNP("Arms"));
        SettingsJacksonAdapter.SettingsCandidate published = SettingsJacksonAdapter.readPair(standard, uunp);
        SettingsJacksonAdapter.SettingsPairBytes canonical = SettingsJacksonAdapter.writePair(published);
        assertArrayEquals(canonical.standardUtf8(), Files.readAllBytes(standard));
        assertArrayEquals(canonical.uunpUtf8(), Files.readAllBytes(uunp));
    }

    /**
     * Prevents callers from mutating either defaults collection outside the validated paired publication seam.
     *
     * @param directory isolated valid Settings pair
     * @throws IOException when permanent fixtures cannot be copied
     */
    @Test
    void publishedDefaultsCannotBeMutatedOutsideThePairedSettingsSeam(@TempDir Path directory)
            throws IOException {
        Files.copy(fixture("standard.json"), directory.resolve("settings.json"));
        Files.copy(fixture("uunp.json"), directory.resolve("settings_UUNP.json"));
        assertTrue(Settings.initialize(directory).isSuccessful());

        assertThrows(UnsupportedOperationException.class, () -> Settings.getDefaultsMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> Settings.getDefaultsMapUUNP().clear());
        assertEquals(0, Settings.getDefaultValueSmall("Waist"));
        assertEquals(100, Settings.getDefaultValueSmallUUNP("Arms"));
    }

    /**
     * Loads legacy Settings through the production seam and proves their defaults, multipliers, and inversion
     * choices reach the generated Templates output consumed by the application.
     *
     * @param directory isolated working directory containing the two legacy Settings fixtures
     * @throws IOException when the fixtures cannot be copied
     */
    @Test
    void productionSettingsDriveLegacyProjectOutput(@TempDir Path directory) throws IOException {
        Files.copy(fixture("standard.json"), directory.resolve("settings.json"));
        Files.copy(fixture("uunp.json"), directory.resolve("settings_UUNP.json"));
        assertTrue(Settings.initialize(directory).isSuccessful());
        ProjectSession session = ProjectSessions.create();

        ProjectOutcome opened = session.open(Path.of("test-resources", "projects",
                "legacy-project-semantics.jbs2bg"));
        ProjectGeneratedOutput output = ProjectOutputFormatter.generate(opened.getSnapshot(), false);

        assertInstanceOf(ChangedOutcome.class, opened);
        assertEquals("CBBE Curvy=Waist@0.74:0.26, Ångström/形@0.0",
                output.getTemplateLinesByPresetName().get("CBBE Curvy"));
        assertEquals("UUNP Athletic=Arms@0.25:0.75",
                output.getTemplateLinesByPresetName().get("UUNP Athletic"));
    }

    /**
     * Workbench-authored Settings replace the pair atomically, become the live immutable snapshot, and immediately
     * affect generated output through the same public Settings seam used after restart.
     *
     * @param directory isolated production Settings directory
     * @throws IOException when permanent fixtures cannot be copied
     */
    @Test
    void workbenchSettingsPersistAsOnePairAndImmediatelyAffectOutput(@TempDir Path directory) throws IOException {
        Files.copy(fixture("standard.json"), directory.resolve("settings.json"));
        Files.copy(fixture("uunp.json"), directory.resolve("settings_UUNP.json"));
        assertTrue(Settings.initialize(directory).isSuccessful());
        Settings.Snapshot loaded = Settings.snapshot();
        LinkedHashMap<String, Float> standardMultipliers =
                new LinkedHashMap<>(loaded.standard().multipliers());
        standardMultipliers.put("Waist", 2f);
        Settings.Profile editedStandard = new Settings.Profile(loaded.standard().defaults(),
                standardMultipliers, loaded.standard().inverted());

        Settings.PersistenceResult persisted = Settings.persist(directory,
                new Settings.Snapshot(editedStandard, loaded.uunp()));

        assertTrue(persisted.isSuccessful());
        assertTrue(persisted.getDiagnostics().isEmpty());
        assertEquals(2f, Settings.getMultiplier("Waist"));
        ProjectSession session = ProjectSessions.create();
        ProjectOutcome opened = session.open(Path.of("test-resources", "projects",
                "legacy-project-semantics.jbs2bg"));
        assertEquals("CBBE Curvy=Waist@1.48:0.52, Ångström/形@0.0",
                ProjectOutputFormatter.generate(opened.getSnapshot(), false)
                        .getTemplateLinesByPresetName().get("CBBE Curvy"));

        assertTrue(Settings.initialize(directory).isSuccessful());
        assertEquals(2f, Settings.snapshot().standard().multipliers().get("Waist"));
        assertEquals("CBBE Curvy=Waist@1.48:0.52, Ångström/形@0.0",
                ProjectOutputFormatter.generate(opened.getSnapshot(), false)
                        .getTemplateLinesByPresetName().get("CBBE Curvy"));
    }

    /** A Workbench save fails immediately when another writer owns the pair lock and retains the live snapshot. */
    @Test
    void workbenchSettingsPersistenceDoesNotBlockBehindAnotherWriter(@TempDir Path directory) throws IOException {
        assertTrue(Settings.initialize(directory).isSuccessful());
        Settings.Snapshot before = Settings.snapshot();

        Settings.PersistenceResult result;
        try (SettingsDirectoryLock lock = SettingsDirectoryLock.acquire(directory)) {
            assertNotNull(lock);
            result = Settings.persist(directory, before);
        }

        assertFalse(result.isSuccessful());
        assertEquals("SETTINGS_LOCK_FAILED", result.getFailure().orElseThrow().getCode());
        assertEquals(before, Settings.snapshot());
    }

    /**
     * Verifies paired semantics, forward-compatible warnings, float conversion, and first-entry deduplication.
     */
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
        assertEquals(List.of("SETTINGS_MEMBER_UNKNOWN", "SETTINGS_MEMBER_UNKNOWN"),
                candidate.diagnostics().stream().map(SettingsJacksonAdapter.SettingsDiagnostic::code).toList());
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

    /**
     * Rejects duplicate fixed fields and dynamic slider keys at their stable member paths.
     */
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

    /**
     * Rejects finite-conversion overflow while retaining an actionable source, path, and coordinates.
     */
    @Test
    void nonFiniteFloatConversionIsRejected() {
        SettingsJacksonAdapter.SettingsFormatException exception = assertFixtureFailure("non-finite.json");

        assertEquals("SETTINGS_NUMBER_NON_FINITE", exception.code());
        assertEquals("/Multipliers/Overflow", exception.path());
        assertTrue(exception.line() > 0);
        assertTrue(exception.column() > 0);
    }

    /**
     * Translates malformed syntax and owned stream-limit failures without exposing Jackson exceptions.
     */
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

    /**
     * One required invalid production fixture and its stable owned diagnostic identity.
     */
    private record InvalidProductionFixture(String name, String code, String path) {
    }
}
