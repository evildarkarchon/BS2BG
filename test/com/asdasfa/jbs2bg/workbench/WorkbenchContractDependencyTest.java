package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Pins the package direction of ADR-0009's contract types: the Area packages depend on {@code workbench}, never the
 * other way round, so a contract type that named an Area's feature type would close a package cycle.
 */
class WorkbenchContractDependencyTest {
    private static final Pattern AREA_PACKAGE_TYPE = Pattern.compile(
            "com/asdasfa/jbs2bg/workbench/(settings|templates|morphs|npcdatabase|output)/[A-Za-z0-9_$]+");

    /** The contract types, and every type nested in them, reference no type of an Area package. */
    @Test
    void contractTypesReferenceNoAreaPackage() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Class<?> contract : List.of(ShellPort.class, WorkbenchArea.class, WorkbenchContext.class,
                AreaPanes.class, AreaLoader.class, DispatchPipeline.class, WorkbenchFeature.class)) {
            Deque<Class<?>> pending = new ArrayDeque<>(List.of(contract));
            while (!pending.isEmpty()) {
                Class<?> type = pending.removeFirst();
                pending.addAll(List.of(type.getDeclaredClasses()));
                Matcher references = AREA_PACKAGE_TYPE.matcher(constantPool(type));
                while (references.find())
                    violations.add(type.getName() + " -> " + references.group());
            }
        }

        assertEquals(List.of(), violations);
    }

    /** Reads one compiled class as ISO-8859-1 text so its UTF-8 constant-pool names stay searchable. */
    private static String constantPool(Class<?> type) throws IOException {
        try (InputStream input = type.getResourceAsStream(
                type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class")) {
            assertNotNull(input, "Missing class file for " + type.getName());
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
