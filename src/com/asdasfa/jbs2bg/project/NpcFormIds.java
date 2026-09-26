package com.asdasfa.jbs2bg.project;

/** Shared load-order-independent Form ID normalization for authored and loaded NPCs. */
final class NpcFormIds {
    private NpcFormIds() {
    }

    /**
     * Keeps the final six digits and strips leading zeroes, preserving one zero.
     * Callers validate source syntax where their input boundary requires it.
     *
     * @param value raw Form ID
     * @return the persisted Form ID representation
     */
    static String normalize(String value) {
        String normalized = value.trim();
        if (normalized.length() > 6)
            normalized = normalized.substring(normalized.length() - 6);
        return normalized.replaceFirst("^0+(?!$)", "");
    }
}
