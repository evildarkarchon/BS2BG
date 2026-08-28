package com.asdasfa.jbs2bg.filtering;

import java.util.Objects;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

/**
 * Identity functions for the Project values shown in filterable views. Snapshot
 * values carry no value equality of their own, so views and bulk commands key
 * rows by these identities rather than by instance, index, or list order.
 * <p>
 * The methods are named rather than overloaded so they can be passed as method
 * references without ambiguity.
 */
public final class ProjectIdentities {

    private ProjectIdentities() {
    }

    /**
     * @param preset Slider Preset
     * @return its case-insensitive name identity
     * @throws NullPointerException when preset is null
     */
    public static NameIdentity sliderPreset(SliderPresetSnapshot preset) {
        return NameIdentity.of(Objects.requireNonNull(preset, "preset").getName());
    }

    /**
     * @param target Custom Morph Target
     * @return its case-insensitive name identity
     * @throws NullPointerException when target is null
     */
    public static NameIdentity customMorphTarget(CustomMorphTargetSnapshot target) {
        return NameIdentity.of(Objects.requireNonNull(target, "target").getName());
    }

    /**
     * @param npc NPC Morph Assignment
     * @return its plugin-plus-editor-ID identity
     * @throws NullPointerException when npc is null
     */
    public static NpcMorphAssignmentIdentity npcMorphAssignment(NpcMorphAssignmentSnapshot npc) {
        Objects.requireNonNull(npc, "npc");
        return new NpcMorphAssignmentIdentity(npc.getPluginName(), npc.getEditorId());
    }

    /**
     * Identifies an NPC Database entry by the same plugin-plus-editor-ID identity
     * it would carry once promoted into the Project; the NPC Database is already
     * unique on that pair.
     *
     * @param npc NPC Database entry
     * @return its Project identity
     * @throws NullPointerException when npc is null
     */
    public static NpcMorphAssignmentIdentity npcDatabaseEntry(NPC npc) {
        Objects.requireNonNull(npc, "npc");
        return new NpcMorphAssignmentIdentity(npc.getMod(), npc.getEditorId());
    }
}
