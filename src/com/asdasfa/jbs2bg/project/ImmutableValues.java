package com.asdasfa.jbs2bg.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Central defensive-copy policy for immutable Project value collections.
 */
final class ImmutableValues {

    private ImmutableValues() {
    }

    /**
     * Copies a list, rejects null elements, and exposes only an unmodifiable view.
     *
     * @param values caller-owned values to copy
     * @param name   argument name used in validation failures
     * @return an immutable copy preserving the supplied order
     * @throws NullPointerException when values, name, or a list element is null
     */
    static <T> List<T> copyOf(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        ArrayList<T> copy = new ArrayList<>(values.size());
        for (T value : values)
            copy.add(Objects.requireNonNull(value, name + " element"));
        return Collections.unmodifiableList(copy);
    }
}
