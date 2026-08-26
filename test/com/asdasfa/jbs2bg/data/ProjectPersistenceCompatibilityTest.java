package com.asdasfa.jbs2bg.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

import com.asdasfa.jbs2bg.data.SliderPreset.SetSlider;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

class ProjectPersistenceCompatibilityTest {

	private static final String SEMANTICS_FIXTURE = "projects/legacy-project-semantics.jbs2bg";
	private static final String ALL_DEFAULTS_FIXTURE = "projects/legacy-project-all-defaults.jbs2bg";
	private static final Map<String, DefaultSliderValue> ORIGINAL_DEFAULTS = new LinkedHashMap<>();
	private static final Map<String, DefaultSliderValue> ORIGINAL_UUNP_DEFAULTS = new LinkedHashMap<>();

	@TempDir
	Path tempDirectory;

	/**
	 * Seeds only the legacy defaults exercised by these fixtures, keeping the test
	 * independent from checkout-local settings files.
	 */
	@BeforeAll
	static void initializeSliderSettings() {
		ORIGINAL_DEFAULTS.putAll(Settings.getDefaultsMap());
		ORIGINAL_UUNP_DEFAULTS.putAll(Settings.getDefaultsMapUUNP());

		Settings.getDefaultsMap().clear();
		Settings.getDefaultsMap().put("Breasts", new DefaultSliderValue(0.2f, 1f));
		Settings.getDefaultsMapUUNP().clear();
		Settings.getDefaultsMapUUNP().put("Arms", new DefaultSliderValue(1f, 1f));
		Settings.getDefaultsMapUUNP().put("Breasts", new DefaultSliderValue(1f, 1f));
	}

	/**
	 * Restores the process-wide legacy defaults so this characterization class does
	 * not leak state into later tests.
	 */
	@AfterAll
	static void restoreSliderSettings() {
		Settings.getDefaultsMap().clear();
		Settings.getDefaultsMap().putAll(ORIGINAL_DEFAULTS);
		Settings.getDefaultsMapUUNP().clear();
		Settings.getDefaultsMapUUNP().putAll(ORIGINAL_UUNP_DEFAULTS);
	}

	/**
	 * Characterizes the observable Project semantics represented by a legacy file.
	 *
	 * @throws Exception when the fixture cannot be located or loaded
	 */
	@Test
	void legacyProjectLoadsWithSliderValuesAndAssignmentReferences() throws Exception {
		File fixture = fixtureFile(SEMANTICS_FIXTURE);
		Data project = openProject(fixture);

		assertProjectSemantics(project);
		assertEquals(fixture, project.currentFile);
	}

	/**
	 * Verifies that a Slider Preset containing only synthesized defaults remains
	 * observable in memory while retaining the legacy omission rule on save.
	 *
	 * @throws Exception when the fixture cannot be loaded or the Project saved
	 */
	@Test
	void allDefaultMissingSlidersRemainOmittedWhenSaved() throws Exception {
		Data project = openProject(fixtureFile(ALL_DEFAULTS_FIXTURE));
		SliderPreset preset = findPreset(project, "All Defaults");

		assertTrue(preset.getSetSliders().isEmpty());
		assertNotNull(preset.getMissingDefaultSetSlider("Breasts"));
		assertEquals(Integer.valueOf(20), preset.getMissingDefaultSetSlider("Breasts").getValueSmall());
		assertEquals(Integer.valueOf(100), preset.getMissingDefaultSetSlider("Breasts").getValueBig());

		File savedProject = tempDirectory.resolve("all-defaults-round-trip.jbs2bg").toFile();
		project.saveToFile(savedProject);

		JsonObject savedPreset = readJson(savedProject).get("SliderPresets").asObject().get("All Defaults")
				.asObject();
		assertTrue(savedPreset.get("SetSliders").asArray().isEmpty());
		assertEquals(savedProject, project.currentFile);
	}

	/**
	 * Verifies semantic round-tripping of legacy field names, Slider Preset values,
	 * UUNP state, Project assignments, and assignment references.
	 *
	 * @throws Exception when the fixture cannot be loaded or the Project saved
	 */
	@Test
	void legacyProjectRoundTripsSemanticallyWithoutJsonOrderingRequirements() throws Exception {
		Data project = openProject(fixtureFile(SEMANTICS_FIXTURE));
		File savedProject = tempDirectory.resolve("semantic-round-trip.jbs2bg").toFile();

		project.saveToFile(savedProject);

		assertSavedProjectSemantics(readJson(savedProject));
		Data reopenedProject = openProject(savedProject);
		assertProjectSemantics(reopenedProject);
	}

	/**
	 * Loads a Project through the same public operation used by production callers.
	 *
	 * @param file the Project file to load
	 * @return a newly created Data instance containing the loaded Project
	 */
	private static Data openProject(File file) {
		Data project = new Data();
		project.openFromFile(file);
		return project;
	}

	/**
	 * Asserts the caller-visible meaning of the representative legacy Project.
	 *
	 * @param project the loaded Project state
	 */
	private static void assertProjectSemantics(Data project) {
		SliderPreset cbbe = findPreset(project, "CBBE Curvy");
		SliderPreset uunp = findPreset(project, "UUNP Athletic");
		assertFalse(cbbe.isUUNP());
		assertTrue(uunp.isUUNP());

		SetSlider waist = findSlider(cbbe, "Waist");
		assertEquals(Integer.valueOf(20), waist.getValueSmall());
		assertEquals(Integer.valueOf(80), waist.getValueBig());
		assertEquals(10, waist.getPctMin());
		assertEquals(90, waist.getPctMax());

		SetSlider arms = findSlider(uunp, "Arms");
		assertEquals(Integer.valueOf(100), arms.getValueSmall());
		assertEquals(Integer.valueOf(50), arms.getValueBig());
		assertEquals(25, arms.getPctMin());
		assertEquals(75, arms.getPctMax());

		assertEquals(1, project.customMorphTargets.size());
		CustomMorphTarget target = project.customMorphTargets.get(0);
		assertEquals("All|Female", target.getName());
		assertEquals(2, target.getSliderPresets().size());
		assertSame(cbbe, findAssignedPreset(target, "CBBE Curvy"));
		assertSame(uunp, findAssignedPreset(target, "UUNP Athletic"));

		assertEquals(1, project.morphedNpcs.size());
		NPC npc = project.morphedNpcs.get(0);
		assertEquals("Lydia", npc.getName());
		assertEquals("Skyrim.esm", npc.getMod());
		assertEquals("HousecarlWhiterun", npc.getEditorId());
		assertEquals("NordRace", npc.getRace());
		assertEquals("A2C94", npc.getFormId());
		assertEquals(1, npc.getSliderPresets().size());
		assertSame(uunp, findAssignedPreset(npc, "UUNP Athletic"));
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
	 * Finds a Slider Preset by its domain name without depending on list ordering.
	 *
	 * @param project the Project state to search
	 * @param name the Slider Preset name
	 * @return the matching Slider Preset
	 * @throws AssertionError when no matching Slider Preset exists
	 */
	private static SliderPreset findPreset(Data project, String name) {
		SliderPreset preset = project.getSliderPreset(name);
		assertNotNull(preset, "Missing Slider Preset: " + name);
		return preset;
	}

	/**
	 * Finds an explicitly stored slider by name.
	 *
	 * @param preset the Slider Preset to search
	 * @param name the slider name
	 * @return the matching slider
	 * @throws AssertionError when no matching slider exists
	 */
	private static SetSlider findSlider(SliderPreset preset, String name) {
		for (SetSlider slider : preset.getSetSliders()) {
			if (slider.getName().equals(name))
				return slider;
		}
		throw new AssertionError("Missing slider: " + name);
	}

	/**
	 * Finds a Slider Preset assignment by name without depending on assignment
	 * ordering.
	 *
	 * @param target the Custom Morph Target or NPC Morph Assignment to search
	 * @param name the Slider Preset name
	 * @return the matching assigned Slider Preset
	 * @throws AssertionError when no matching assignment exists
	 */
	private static SliderPreset findAssignedPreset(MorphTarget target, String name) {
		for (SliderPreset preset : target.getSliderPresets()) {
			if (preset.getName().equals(name))
				return preset;
		}
		throw new AssertionError("Missing Slider Preset assignment: " + name);
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
