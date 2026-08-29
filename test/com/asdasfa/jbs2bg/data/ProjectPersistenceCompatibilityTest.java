package com.asdasfa.jbs2bg.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

class ProjectPersistenceCompatibilityTest {

	private static final String SEMANTICS_FIXTURE = "projects/legacy-project-semantics.jbs2bg";
	private static final String ALL_DEFAULTS_FIXTURE = "projects/legacy-project-all-defaults.jbs2bg";

	@TempDir
	Path tempDirectory;

	/**
	 * Publishes only the defaults exercised by these fixtures, keeping the test
	 * independent from checkout-local settings files.
	 */
	@BeforeAll
	static void initializeSliderSettings() {
		Map<String, DefaultSliderValue> standard = new LinkedHashMap<>();
		standard.put("Breasts", new DefaultSliderValue(0.2f, 1f));
		Map<String, DefaultSliderValue> uunp = new LinkedHashMap<>();
		uunp.put("Arms", new DefaultSliderValue(1f, 1f));
		uunp.put("Breasts", new DefaultSliderValue(1f, 1f));
		SettingsTestSupport.installDefaults(standard, uunp);
	}

	/**
	 * Restores the checked-in Settings pair so this characterization class does
	 * not leak state into later tests.
	 */
	@AfterAll
	static void restoreSliderSettings() {
		SettingsTestSupport.restoreRepositorySettings();
	}

	/**
	 * Verifies the representative legacy Project through the new public persistence
	 * seam, including a semantic read-write-read comparison.
	 *
	 * @throws Exception when the fixture cannot be loaded or the Project saved
	 */
	@Test
	void projectSessionReadWriteReadIsSemanticallyStable() throws Exception {
		Path source = fixtureFile(SEMANTICS_FIXTURE).toPath();
		ProjectSession session = ProjectSessions.create();
		ProjectSnapshot opened = session.open(source).getSnapshot();
		Path savedProject = tempDirectory.resolve("project-session-semantic-round-trip.jbs2bg");

		ProjectOutcome saved = session.saveAs(savedProject);

		assertFalse(saved.getSnapshot().isDirty());
		assertEquals(savedProject.toAbsolutePath().normalize(), saved.getSnapshot().getFileIdentity().get());
		assertSavedProjectSemantics(readJson(savedProject.toFile()));
		ProjectSnapshot reopened = ProjectSessions.create().open(savedProject).getSnapshot();
		assertProjectSnapshotSemantics(opened, reopened);
	}

	/**
	 * Verifies that the legacy all-default omission survives Save As and is restored
	 * as the same synthesized slider choice on reopen.
	 *
	 * @throws Exception when the fixture cannot be loaded or the Project saved
	 */
	@Test
	void projectSessionPreservesAllDefaultMissingSliderOmission() throws Exception {
		ProjectSession session = ProjectSessions.create();
		session.open(fixtureFile(ALL_DEFAULTS_FIXTURE).toPath());
		Path savedProject = tempDirectory.resolve("project-session-all-defaults.jbs2bg");

		session.saveAs(savedProject);

		JsonObject savedPreset = readJson(savedProject.toFile()).get("SliderPresets").asObject()
				.get("All Defaults").asObject();
		assertTrue(savedPreset.get("SetSliders").asArray().isEmpty());
		ProjectSnapshot reopened = ProjectSessions.create().open(savedProject).getSnapshot();
		SliderChoiceSnapshot breasts = findChoice(findPreset(reopened, "All Defaults"), "Breasts");
		assertTrue(breasts.isMissingDefault());
		assertTrue(breasts.isEnabled());
		assertFalse(breasts.getStoredSmallValue().isPresent());
		assertFalse(breasts.getStoredBigValue().isPresent());
		assertEquals(20, breasts.getEffectiveSmallValue());
		assertEquals(100, breasts.getEffectiveBigValue());
		assertEquals(100, breasts.getPercentageMinimum());
		assertEquals(100, breasts.getPercentageMaximum());
	}

	/**
	 * Verifies that an explicitly persisted slider with two nullable stored values is
	 * not confused with a synthesized missing default and omitted on save.
	 *
	 * @throws Exception when the temporary Project cannot be written or saved
	 */
	@Test
	void projectSessionPreservesExplicitNullableSliderValues() throws Exception {
		Path source = tempDirectory.resolve("explicit-null-values.jbs2bg");
		Files.write(source, ("{\"SliderPresets\":{\"Explicit Nulls\":{\"isUUNP\":false,"
				+ "\"SetSliders\":[{\"name\":\"Waist\",\"enabled\":true,\"valueSmall\":null,"
				+ "\"valueBig\":null,\"pctMin\":100,\"pctMax\":100}]}},"
				+ "\"CustomMorphTargets\":{},\"MorphedNPCs\":{}}")
				.getBytes(StandardCharsets.UTF_8));
		ProjectSession session = ProjectSessions.create();
		SliderChoiceSnapshot opened = findChoice(findPreset(session.open(source).getSnapshot(), "Explicit Nulls"),
				"Waist");
		Path savedProject = tempDirectory.resolve("explicit-null-values-saved.jbs2bg");

		session.saveAs(savedProject);

		assertFalse(opened.isMissingDefault());
		JsonArray savedChoices = readJson(savedProject.toFile()).get("SliderPresets").asObject()
				.get("Explicit Nulls").asObject().get("SetSliders").asArray();
		assertEquals(1, savedChoices.size());
		assertTrue(savedChoices.get(0).asObject().get("valueSmall").isNull());
		assertTrue(savedChoices.get(0).asObject().get("valueBig").isNull());
		ProjectSnapshot reopened = ProjectSessions.create().open(savedProject).getSnapshot();
		assertFalse(findChoice(findPreset(reopened, "Explicit Nulls"), "Waist").isMissingDefault());
	}

	/**
	 * Recursively compares persisted Project content while deliberately excluding
	 * file identity, dirty state, lifecycle metadata, and JSON ordering details.
	 *
	 * @param expected snapshot read before persistence
	 * @param actual snapshot read back from the saved Project
	 */
	private static void assertProjectSnapshotSemantics(ProjectSnapshot expected, ProjectSnapshot actual) {
		assertEquals(expected.getSliderPresets().size(), actual.getSliderPresets().size());
		for (int presetIndex = 0; presetIndex < expected.getSliderPresets().size(); presetIndex++) {
			SliderPresetSnapshot expectedPreset = expected.getSliderPresets().get(presetIndex);
			SliderPresetSnapshot actualPreset = actual.getSliderPresets().get(presetIndex);
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

		assertEquals(expected.getCustomMorphTargets().size(), actual.getCustomMorphTargets().size());
		for (int index = 0; index < expected.getCustomMorphTargets().size(); index++) {
			CustomMorphTargetSnapshot expectedTarget = expected.getCustomMorphTargets().get(index);
			CustomMorphTargetSnapshot actualTarget = actual.getCustomMorphTargets().get(index);
			assertEquals(expectedTarget.getName(), actualTarget.getName());
			assertEquals(expectedTarget.getSliderPresetNames(), actualTarget.getSliderPresetNames());
		}

		assertEquals(expected.getNpcMorphAssignments().size(), actual.getNpcMorphAssignments().size());
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
	 * Finds a Slider Preset in an immutable Project snapshot.
	 *
	 * @param snapshot snapshot to search
	 * @param name requested Slider Preset name
	 * @return matching Slider Preset
	 * @throws AssertionError when the preset is absent
	 */
	private static SliderPresetSnapshot findPreset(ProjectSnapshot snapshot, String name) {
		for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
			if (preset.getName().equals(name))
				return preset;
		}
		throw new AssertionError("Missing Slider Preset: " + name);
	}

	/**
	 * Finds a slider choice in an immutable Slider Preset.
	 *
	 * @param preset Slider Preset to search
	 * @param name requested choice name
	 * @return matching slider choice
	 * @throws AssertionError when the choice is absent
	 */
	private static SliderChoiceSnapshot findChoice(SliderPresetSnapshot preset, String name) {
		for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
			if (choice.getName().equals(name))
				return choice;
		}
		throw new AssertionError("Missing slider choice: " + name);
	}

	/**
	 * Asserts saved Project data structurally so formatting and JSON member ordering
	 * remain outside the compatibility contract.
	 *
	 * @param root the parsed saved Project root
	 */
	private static void assertSavedProjectSemantics(JsonObject root) {
		JsonObject presets = root.get("SliderPresets").asObject();
		JsonObject cbbe = presets.get("CBBE Curvy").asObject();
		JsonObject uunp = presets.get("UUNP Athletic").asObject();
		assertFalse(cbbe.getBoolean("isUUNP", true));
		assertTrue(uunp.getBoolean("isUUNP", false));

		JsonObject waist = findSlider(cbbe.get("SetSliders").asArray(), "Waist");
		assertEquals(20, waist.getInt("valueSmall", -1));
		assertEquals(80, waist.getInt("valueBig", -1));
		assertEquals(10, waist.getInt("pctMin", -1));
		assertEquals(90, waist.getInt("pctMax", -1));

		JsonArray uunpSliders = uunp.get("SetSliders").asArray();
		JsonObject arms = findSlider(uunpSliders, "Arms");
		assertTrue(arms.getBoolean("enabled", false));
		assertTrue(arms.get("valueSmall").isNull());
		assertEquals(50, arms.getInt("valueBig", -1));
		assertEquals(25, arms.getInt("pctMin", -1));
		assertEquals(75, arms.getInt("pctMax", -1));
		assertNull(findSliderOrNull(cbbe.get("SetSliders").asArray(), "Breasts"));
		assertNull(findSliderOrNull(uunpSliders, "Breasts"));

		JsonArray targetPresets = root.get("CustomMorphTargets").asObject().get("All|Female").asObject()
				.get("SliderPresets").asArray();
		assertTrue(containsString(targetPresets, "CBBE Curvy"));
		assertTrue(containsString(targetPresets, "UUNP Athletic"));

		JsonObject npc = root.get("MorphedNPCs").asObject().get("Lydia").asObject();
		assertEquals("Skyrim.esm", npc.getString("Mod", ""));
		assertEquals("HousecarlWhiterun", npc.getString("EditorId", ""));
		assertEquals("NordRace", npc.getString("Race", ""));
		assertEquals("A2C94", npc.getString("FormId", ""));
		assertTrue(containsString(npc.get("SliderPresets").asArray(), "UUNP Athletic"));
	}

	/**
	 * Finds a serialized slider by name.
	 *
	 * @param sliders the serialized SetSliders array
	 * @param name the slider name
	 * @return the matching slider object
	 * @throws AssertionError when no matching slider exists
	 */
	private static JsonObject findSlider(JsonArray sliders, String name) {
		JsonObject slider = findSliderOrNull(sliders, name);
		assertNotNull(slider, "Missing serialized slider: " + name);
		return slider;
	}

	/**
	 * Finds a serialized slider by name when absence is a valid result.
	 *
	 * @param sliders the serialized SetSliders array
	 * @param name the slider name
	 * @return the matching slider object, or {@code null} when absent
	 */
	private static JsonObject findSliderOrNull(JsonArray sliders, String name) {
		for (JsonValue value : sliders) {
			JsonObject slider = value.asObject();
			if (slider.getString("name", "").equals(name))
				return slider;
		}
		return null;
	}

	/**
	 * Reports whether a JSON string array contains the requested value.
	 *
	 * @param values the array to search
	 * @param expected the expected string
	 * @return {@code true} when the string occurs in the array
	 */
	private static boolean containsString(JsonArray values, String expected) {
		for (JsonValue value : values) {
			if (value.asString().equals(expected))
				return true;
		}
		return false;
	}

	/**
	 * Resolves a classpath Project fixture to a file for the legacy persistence API.
	 *
	 * @param resourceName the classpath resource path
	 * @return the fixture file
	 * @throws URISyntaxException when the resource URL cannot be converted
	 */
	private static File fixtureFile(String resourceName) throws URISyntaxException {
		URL resource = Objects.requireNonNull(ProjectPersistenceCompatibilityTest.class.getClassLoader()
				.getResource(resourceName), "Missing fixture: " + resourceName);
		return new File(resource.toURI());
	}

	/**
	 * Parses a saved Project file as JSON.
	 *
	 * @param file the Project file to parse
	 * @return the parsed JSON root
	 * @throws IOException when the file cannot be read
	 */
	private static JsonObject readJson(File file) throws IOException {
		byte[] bytes = Files.readAllBytes(file.toPath());
		return Json.parse(new String(bytes, StandardCharsets.UTF_8)).asObject();
	}
}
