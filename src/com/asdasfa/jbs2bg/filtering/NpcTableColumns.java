package com.asdasfa.jbs2bg.filtering;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;

/**
 * Canonical column definitions for the two filterable NPC tables. The IDs are
 * the column headers users see and the cell text is exactly what each table
 * renders, so criteria built from displayed values match the seam.
 */
public final class NpcTableColumns {

    private static final List<FilterColumn<NpcMorphAssignmentSnapshot>> NPC_MORPH_ASSIGNMENTS = Collections
            .unmodifiableList(Arrays.asList(
                    FilterColumn.of("Name", NpcMorphAssignmentSnapshot::getDisplayName),
                    FilterColumn.of("Master", NpcMorphAssignmentSnapshot::getPluginName),
                    FilterColumn.of("Race", NpcMorphAssignmentSnapshot::getRace),
                    FilterColumn.of("EditorID", NpcMorphAssignmentSnapshot::getEditorId),
                    FilterColumn.of("FormID", NpcMorphAssignmentSnapshot::getFormId),
                    // The table joins assigned Slider Preset names with "|" in one cell.
                    FilterColumn.of("Slider Presets", npc -> String.join("|", npc.getSliderPresetNames()))));

    private static final List<FilterColumn<NPC>> NPC_DATABASE = Collections.unmodifiableList(Arrays.asList(
            FilterColumn.of("Name", NPC::getName),
            FilterColumn.of("Master", NPC::getMod),
            FilterColumn.of("Race", NPC::getRace),
            FilterColumn.of("EditorID", NPC::getEditorId),
            FilterColumn.of("FormID", NPC::getFormId)));

    private NpcTableColumns() {
    }

    /**
     * @return the immutable columns of the Project's NPC Morph Assignment table, in table order
     */
    public static List<FilterColumn<NpcMorphAssignmentSnapshot>> npcMorphAssignments() {
        return NPC_MORPH_ASSIGNMENTS;
    }

    /**
     * @return the immutable columns of the NPC Database table, in table order
     */
    public static List<FilterColumn<NPC>> npcDatabase() {
        return NPC_DATABASE;
    }
}
