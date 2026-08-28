package com.asdasfa.jbs2bg.filtering;

import java.util.Objects;

/**
 * Logical identity of a Slider Preset or Custom Morph Target: its name,
 * compared without regard to case, exactly as the Project enforces uniqueness.
 */
public final class NameIdentity {

    private final String name;

    private NameIdentity(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * @param name Slider Preset or Custom Morph Target name
     * @return the identity
     * @throws NullPointerException when name is null
     */
    public static NameIdentity of(String name) {
        return new NameIdentity(name);
    }

    /** @return the name as supplied (case preserved for display) */
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof NameIdentity))
            return false;
        return name.equalsIgnoreCase(((NameIdentity) other).name);
    }

    /**
     * Folds each character with the same upper-then-lower rule as
     * {@link String#equalsIgnoreCase(String)} so equal identities hash alike.
     * This deliberately mirrors the private fold in
     * {@code NpcMorphAssignmentIdentity.caseInsensitiveHash}; keep the two in
     * step if either changes.
     */
    @Override
    public int hashCode() {
        int hash = 0;
        for (int index = 0; index < name.length(); index++)
            hash = 31 * hash + Character.toLowerCase(Character.toUpperCase(name.charAt(index)));
        return hash;
    }

    @Override
    public String toString() {
        return "NameIdentity[" + name + "]";
    }
}
