package com.asdasfa.jbs2bg.testing;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asserts that a production type, and every application type it reaches through its compiled references, never
 * names a JavaFX type. Reading bytecode catches a JavaFX dependency hidden in a nested type, a method body, or a
 * referenced application class that a signature-only reflection check would miss.
 */
public final class JavaFxFreeTypeGraph {
    private static final Pattern APPLICATION_TYPE = Pattern.compile("com/asdasfa/jbs2bg/[A-Za-z0-9_/$]+");

    private JavaFxFreeTypeGraph() {
    }

    /**
     * Walks the application types reachable from {@code root} and fails on the first one whose constant pool
     * references {@code javafx/}.
     *
     * @param root production type whose reachable application type graph must stay JavaFX-free
     * @return the internal names of every application type that was checked, in visiting order
     */
    public static Set<String> assertJavaFxFree(Class<?> root) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(root.getName().replace('.', '/'));
        while (!pending.isEmpty()) {
            String type = pending.removeFirst();
            if (visited.contains(type))
                continue;
            String constants = constantPool(type);
            // A matched name without a class file is a resource path or package string, not a referenced type.
            if (constants == null)
                continue;
            visited.add(type);
            assertFalse(constants.contains("javafx/"),
                    "JavaFX reached " + root.getSimpleName() + " through " + type);
            Matcher references = APPLICATION_TYPE.matcher(constants);
            while (references.find())
                pending.add(references.group());
        }
        assertFalse(visited.isEmpty(), "Missing compiled application type: " + root.getName());
        return visited;
    }

    /**
     * Reads one compiled class as ISO-8859-1 text so its UTF-8 constant-pool names stay searchable.
     *
     * @return the class bytes as text, or null when no class file has that internal name
     */
    private static String constantPool(String internalName) {
        try (InputStream input = JavaFxFreeTypeGraph.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (input == null)
                return null;
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
