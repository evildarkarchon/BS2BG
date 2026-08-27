package com.asdasfa.jbs2bg.project;

import java.util.List;
import java.util.Objects;

/**
 * Immutable NPC Morph Assignment value exposed by a Project snapshot.
 */
public final class NpcMorphAssignmentSnapshot {

	private final String displayName;
	private final String pluginName;
	private final String editorId;
	private final String race;
	private final String formId;
	private final List<String> sliderPresetNames;

	/**
	 * Creates an NPC Morph Assignment snapshot by copying source values and
	 * relationships rather than retaining a mutable NPC Database entry.
	 *
	 * @param displayName display name
	 * @param pluginName source plugin or mod name
	 * @param editorId editor ID used with pluginName for identity
	 * @param race race value retained for output
	 * @param formId normalized form ID retained for output
	 * @param sliderPresetNames assigned Slider Preset names in canonical order
	 * @throws NullPointerException when an argument or assignment is null
	 */
	public NpcMorphAssignmentSnapshot(String displayName, String pluginName, String editorId, String race,
			String formId, List<String> sliderPresetNames) {
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
		this.editorId = Objects.requireNonNull(editorId, "editorId");
		this.race = Objects.requireNonNull(race, "race");
		this.formId = Objects.requireNonNull(formId, "formId");
		this.sliderPresetNames = ImmutableValues.copyOf(sliderPresetNames, "sliderPresetNames");
	}

	/** @return the NPC display name */
	public String getDisplayName() {
		return displayName;
	}

	/** @return the plugin or mod name used for identity */
	public String getPluginName() {
		return pluginName;
	}

	/** @return the editor ID used for identity */
	public String getEditorId() {
		return editorId;
	}

	/** @return the NPC race */
	public String getRace() {
		return race;
	}

	/** @return the normalized form ID */
	public String getFormId() {
		return formId;
	}

	/** @return immutable assigned Slider Preset names */
	public List<String> getSliderPresetNames() {
		return sliderPresetNames;
	}
}
