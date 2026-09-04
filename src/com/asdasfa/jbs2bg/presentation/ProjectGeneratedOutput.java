package com.asdasfa.jbs2bg.presentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;

/**
 * Immutable generated text, export payloads, and relationship diagnostics for one Project snapshot.
 */
public final class ProjectGeneratedOutput {

    private final OutputArtifact templatesArtifact;
    private final OutputArtifact morphsArtifact;
    private final Map<String, String> templateLinesByPresetName;
    private final List<BosJsonArtifact> bosJsonArtifacts;
    private final List<CustomMorphTargetSnapshot> customMorphTargetsWithoutPresets;
    private final List<NpcMorphAssignmentSnapshot> npcMorphAssignmentsWithoutPresets;

    /**
     * Freezes every generated collection so presentation scheduling can safely retain
     * this result after the source session advances.
     *
     * @param templatesText                     complete templates.ini text
     * @param morphsText                        complete morphs.ini text
     * @param templateLinesByPresetName         individual template lines in canonical preset order
     * @param bosJsonArtifacts                  canonical BoS JSON artifacts in Slider Preset order
     * @param customMorphTargetsWithoutPresets  unassigned Custom Morph Targets
     * @param npcMorphAssignmentsWithoutPresets unassigned NPC Morph Assignments
     * @throws NullPointerException when any argument is null
     */
    ProjectGeneratedOutput(String templatesText, String morphsText,
                           Map<String, String> templateLinesByPresetName, List<BosJsonArtifact> bosJsonArtifacts,
                           List<CustomMorphTargetSnapshot> customMorphTargetsWithoutPresets,
                           List<NpcMorphAssignmentSnapshot> npcMorphAssignmentsWithoutPresets) {
        templatesArtifact = new Utf8OutputArtifact("templates.ini",
                Objects.requireNonNull(templatesText, "templatesText"));
        morphsArtifact = new Utf8OutputArtifact("morphs.ini",
                Objects.requireNonNull(morphsText, "morphsText"));
        this.templateLinesByPresetName = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(templateLinesByPresetName,
                        "templateLinesByPresetName")));
        this.bosJsonArtifacts = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(bosJsonArtifacts, "bosJsonArtifacts")));
        this.customMorphTargetsWithoutPresets = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(customMorphTargetsWithoutPresets,
                        "customMorphTargetsWithoutPresets")));
        this.npcMorphAssignmentsWithoutPresets = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(npcMorphAssignmentsWithoutPresets,
                        "npcMorphAssignmentsWithoutPresets")));
    }

    /**
     * @return complete templates.ini text without a trailing newline
     */
    public String getTemplatesText() {
        return templatesArtifact.getText();
    }

    /**
     * @return complete morphs.ini text, including the legacy trailing newline when non-empty
     */
    public String getMorphsText() {
        return morphsArtifact.getText();
    }

    /** @return immutable Templates artifact whose text and publication bytes are inseparable */
    public OutputArtifact getTemplatesArtifact() {
        return templatesArtifact;
    }

    /** @return immutable Morphs artifact whose text and publication bytes are inseparable */
    public OutputArtifact getMorphsArtifact() {
        return morphsArtifact;
    }

    /**
     * Returns the complete transactional export set: both INIs followed by every canonical BoS JSON artifact.
     *
     * @return immutable artifacts in deterministic publication order
     */
    public List<OutputArtifact> getArtifacts() {
        List<OutputArtifact> artifacts = new ArrayList<>(2 + bosJsonArtifacts.size());
        artifacts.add(templatesArtifact);
        artifacts.add(morphsArtifact);
        artifacts.addAll(bosJsonArtifacts);
        return List.copyOf(artifacts);
    }

    /**
     * @return immutable template lines keyed by canonical Slider Preset name
     */
    public Map<String, String> getTemplateLinesByPresetName() {
        return templateLinesByPresetName;
    }

    /**
     * @return immutable canonical BoS artifacts in Slider Preset order
     */
    public List<BosJsonArtifact> getBosJsonArtifacts() {
        return bosJsonArtifacts;
    }

    /**
     * @return immutable Custom Morph Target snapshots without Slider Preset relationships
     */
    public List<CustomMorphTargetSnapshot> getCustomMorphTargetsWithoutPresets() {
        return customMorphTargetsWithoutPresets;
    }

    /**
     * @return immutable NPC Morph Assignment snapshots without Slider Preset relationships
     */
    public List<NpcMorphAssignmentSnapshot> getNpcMorphAssignmentsWithoutPresets() {
        return npcMorphAssignmentsWithoutPresets;
    }
}
