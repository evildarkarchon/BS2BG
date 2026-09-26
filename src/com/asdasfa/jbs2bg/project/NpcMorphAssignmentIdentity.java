package com.asdasfa.jbs2bg.project;

import java.util.Objects;

/**
 * Immutable Project identity for an NPC Morph Assignment. Display name, race,
 * and form ID deliberately do not participate in equality.
 */
public final class NpcMorphAssignmentIdentity {
    private final String pluginName;
    private final String editorId;

    /**
     * Creates an identity compared without regard to case.
     *
     * @param pluginName source plugin or mod name
     * @param editorId   NPC editor ID
     * @throws NullPointerException when either identity field is null
     */
    public NpcMorphAssignmentIdentity(String pluginName, String editorId) {
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.editorId = Objects.requireNonNull(editorId, "editorId");
    }

    /**
     * Folds each character using the same upper-then-lower rule used by Java's
     * case-insensitive String comparison, keeping equal values hash-compatible.
     *
     * @param value identity field to hash
     * @return hash code consistent with {@link String#equalsIgnoreCase(String)}
     */
    private static int caseInsensitiveHash(String value) {
        int hash = 0;
        for (int index = 0; index < value.length(); index++) {
            char folded = Character.toLowerCase(Character.toUpperCase(value.charAt(index)));
            hash = 31 * hash + folded;
        }
        return hash;
    }

    /**
     * @return the plugin or mod name identity field
     */
    public String getPluginName() {
        return pluginName;
    }

    /**
     * @return the editor ID identity field
     */
    public String getEditorId() {
        return editorId;
    }

    /**
     * Compares the complete logical identity without regard to case.
     *
     * @param other candidate value
     * @return true when both identity fields match case-insensitively
     */
    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof NpcMorphAssignmentIdentity))
            return false;
        NpcMorphAssignmentIdentity identity = (NpcMorphAssignmentIdentity) other;
        return pluginName.equalsIgnoreCase(identity.pluginName) && editorId.equalsIgnoreCase(identity.editorId);
    }

    /**
     * Produces a hash consistent with case-insensitive identity equality.
     *
     * @return case-insensitive identity hash
     */
    @Override
    public int hashCode() {
        return 31 * caseInsensitiveHash(pluginName) + caseInsensitiveHash(editorId);
    }
}
