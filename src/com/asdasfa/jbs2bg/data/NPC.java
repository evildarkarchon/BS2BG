package com.asdasfa.jbs2bg.data;

import java.util.Objects;

/**
 * Immutable NPC Database source row. Project promotion copies these values into
 * an independent NPC Morph Assignment through ProjectSession.
 */
public final class NPC {

    private final String mod;
    private final String name;
    private final String editorId;
    private final String race;
    private final String formId;

    /**
     * Parses one legacy NPC text row for the session-scoped NPC Database.
     *
     * @param line pipe-delimited plugin, name, editor ID, race, and form ID values
     * @throws NullPointerException     when line is null
     * @throws IllegalArgumentException when the row does not contain every required value
     */
    public NPC(String line) {
        String[] values = Objects.requireNonNull(line, "line").split("\\|");
        if (values.length < 5)
            throw new IllegalArgumentException("NPC row must contain plugin, name, editor ID, race, and form ID");
        for (int index = 0; index < values.length; index++)
            values[index] = values[index].trim();

        mod = values[0];
        editorId = values[2];
        String displayName = values[1];
        name = displayName.isEmpty() ? "Unnamed (" + editorId + ")" : displayName;
        String[] raceParts = values[3].split("\"");
        race = raceParts[0].trim();
        formId = normalizeFormId(values[4]);
    }

    /**
     * Normalizes a legacy load-order-prefixed form ID for Project promotion.
     */
    private static String normalizeFormId(String value) {
        String normalized = value.trim();
        if (normalized.length() > 6)
            normalized = normalized.substring(normalized.length() - 6);
        return normalized.replaceFirst("^0+(?!$)", "");
    }

    /**
     * @return source plugin or mod name
     */
    public String getMod() {
        return mod;
    }

    /**
     * @return NPC display name
     */
    public String getName() {
        return name;
    }

    /**
     * @return NPC editor ID
     */
    public String getEditorId() {
        return editorId;
    }

    /**
     * @return NPC race
     */
    public String getRace() {
        return race;
    }

    /**
     * @return normalized six-digit-or-shorter form ID
     */
    public String getFormId() {
        return formId;
    }
}
