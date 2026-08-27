package com.asdasfa.jbs2bg.presentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;

/** Immutable generated text, export payloads, and relationship diagnostics for one Project snapshot. */
public final class ProjectGeneratedOutput {

	private final String templatesText;
	private final String morphsText;
	private final Map<String, String> templateLinesByPresetName;
	private final Map<String, String> bosJsonByFileName;
	private final List<CustomMorphTargetSnapshot> customMorphTargetsWithoutPresets;
	private final List<NpcMorphAssignmentSnapshot> npcMorphAssignmentsWithoutPresets;

	/**
	 * Freezes every generated collection so presentation scheduling can safely retain
	 * this result after the source session advances.
	 *
	 * @param templatesText complete templates.ini text
	 * @param morphsText complete morphs.ini text
	 * @param templateLinesByPresetName individual template lines in canonical preset order
	 * @param bosJsonByFileName BoS JSON payloads keyed by export file name
	 * @param customMorphTargetsWithoutPresets unassigned Custom Morph Targets
	 * @param npcMorphAssignmentsWithoutPresets unassigned NPC Morph Assignments
	 * @throws NullPointerException when any argument is null
	 */
	ProjectGeneratedOutput(String templatesText, String morphsText,
			Map<String, String> templateLinesByPresetName, Map<String, String> bosJsonByFileName,
			List<CustomMorphTargetSnapshot> customMorphTargetsWithoutPresets,
			List<NpcMorphAssignmentSnapshot> npcMorphAssignmentsWithoutPresets) {
		this.templatesText = Objects.requireNonNull(templatesText, "templatesText");
		this.morphsText = Objects.requireNonNull(morphsText, "morphsText");
		this.templateLinesByPresetName = Collections.unmodifiableMap(
				new LinkedHashMap<>(Objects.requireNonNull(templateLinesByPresetName,
						"templateLinesByPresetName")));
		this.bosJsonByFileName = Collections.unmodifiableMap(
				new LinkedHashMap<>(Objects.requireNonNull(bosJsonByFileName, "bosJsonByFileName")));
		this.customMorphTargetsWithoutPresets = Collections.unmodifiableList(
				new ArrayList<>(Objects.requireNonNull(customMorphTargetsWithoutPresets,
						"customMorphTargetsWithoutPresets")));
		this.npcMorphAssignmentsWithoutPresets = Collections.unmodifiableList(
				new ArrayList<>(Objects.requireNonNull(npcMorphAssignmentsWithoutPresets,
						"npcMorphAssignmentsWithoutPresets")));
	}

	/** @return complete templates.ini text without a trailing newline */
	public String getTemplatesText() {
		return templatesText;
	}

	/** @return complete morphs.ini text, including the legacy trailing newline when non-empty */
	public String getMorphsText() {
		return morphsText;
	}

	/** @return immutable template lines keyed by canonical Slider Preset name */
	public Map<String, String> getTemplateLinesByPresetName() {
		return templateLinesByPresetName;
	}

	/** @return immutable BoS JSON payloads keyed by {@code <preset name>.json} */
	public Map<String, String> getBosJsonByFileName() {
		return bosJsonByFileName;
	}

	/** @return immutable Custom Morph Target snapshots without Slider Preset relationships */
	public List<CustomMorphTargetSnapshot> getCustomMorphTargetsWithoutPresets() {
		return customMorphTargetsWithoutPresets;
	}

	/** @return immutable NPC Morph Assignment snapshots without Slider Preset relationships */
	public List<NpcMorphAssignmentSnapshot> getNpcMorphAssignmentsWithoutPresets() {
		return npcMorphAssignmentsWithoutPresets;
	}
}
