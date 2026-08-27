package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

/** Verifies JavaFX-free generated output derived from one immutable Project snapshot. */
class ProjectOutputFormatterTest {

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

		String newLine = System.lineSeparator();
		assertEquals("Alpha=Scale@0.35:0.65" + newLine + "Zulu=Active@0.2, Zero@0.0",
				output.getTemplatesText());
		assertEquals("AlphaTarget=Alpha" + newLine + "ZuluTarget=" + newLine
				+ "Dawnguard.esm|A2C94=" + newLine + "Skyrim.esm|123ABC=Zulu" + newLine,
				output.getMorphsText());
		assertEquals(Arrays.asList("Alpha", "Zulu"),
				Arrays.asList(output.getTemplateLinesByPresetName().keySet().toArray(new String[0])));
		assertEquals("Alpha=Scale@0.35:0.65", output.getTemplateLinesByPresetName().get("Alpha"));
		assertEquals(Arrays.asList("Alpha.json", "Zulu.json"),
				Arrays.asList(output.getBosJsonByFileName().keySet().toArray(new String[0])));

		JsonObject alphaBos = Json.parse(output.getBosJsonByFileName().get("Alpha.json")).asObject();
		assertEquals("Alpha", alphaBos.get("string").asObject().getString("bodyname", null));
		assertEquals("Scale", alphaBos.get("string").asObject().getString("slidername1", null));
		assertEquals(1, alphaBos.get("int").asObject().getInt("slidersnumber", -1));
		assertEquals(0.8f, alphaBos.get("float").asObject().getFloat("highvalue1", -1f));
		assertEquals(0.2f, alphaBos.get("float").asObject().getFloat("lowvalue1", -1f));
	}

	/** No-preset reporting must preserve the exact immutable child values in output order. */
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

	/** Omission must remove disabled choices and legacy-redundant zero ranges from both formats. */
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
		JsonObject bos = Json.parse(output.getBosJsonByFileName().get("Preset.json")).asObject();

		assertEquals("Preset=Active@0.2", output.getTemplatesText());
		assertEquals(1, bos.get("int").asObject().getInt("slidersnumber", -1));
		assertEquals("Active", bos.get("string").asObject().getString("slidername1", null));
	}

	/** BoS output must retain the legacy grouping of every high value before every low value. */
	@Test
	void groupsBosHighValuesBeforeLowValues() {
		ProjectSession session = ProjectSessions.create();
		session.newProject();
		session.apply(SliderPresetEdits.create("Preset"));
		session.apply(SliderPresetEdits.setSliderChoice("Preset",
				choice("Alpha", true, 10, 20, 100, 100)));
		session.apply(SliderPresetEdits.setSliderChoice("Preset",
				choice("Beta", true, 30, 40, 100, 100)));

		String json = ProjectOutputFormatter.generate(session.getSnapshot(), false)
				.getBosJsonByFileName().get("Preset.json");
		int secondHighValueIndex = json.indexOf("\"highvalue2\"");
		int firstLowValueIndex = json.indexOf("\"lowvalue1\"");

		assertTrue(secondHighValueIndex >= 0);
		assertTrue(firstLowValueIndex >= 0);
		assertTrue(secondHighValueIndex < firstLowValueIndex);
	}

	/** Every generated collection must reject mutation through its public result seam. */
	@Test
	void exposesOnlyUnmodifiableGeneratedCollections() {
		ProjectGeneratedOutput output = ProjectOutputFormatter.generate(populatedSession().getSnapshot(), false);

		assertThrows(UnsupportedOperationException.class,
				() -> output.getTemplateLinesByPresetName().put("Injected", "Injected="));
		assertThrows(UnsupportedOperationException.class,
				() -> output.getBosJsonByFileName().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> output.getCustomMorphTargetsWithoutPresets().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> output.getNpcMorphAssignmentsWithoutPresets().clear());
	}

	/** Builds canonical Project state through the external session seam used by presentation. */
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

	/** Creates one explicit non-synthesized slider value for formatter fixtures. */
	private static SliderChoiceSnapshot choice(String name, boolean enabled, int small, int big, int minimum,
			int maximum) {
		return new SliderChoiceSnapshot(name, enabled, Integer.valueOf(small), Integer.valueOf(big), small, big,
				minimum, maximum, false);
	}
}
