package com.asdasfa.jbs2bg.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Locks the exact Jackson dependency and its repository-owned implementation boundary.
 */
final class JacksonDependencyPolicyTest {
    private static final List<String> FORMAT_ADAPTERS = List.of(
            "src/com/asdasfa/jbs2bg/project/ProjectJacksonAdapter.java",
            "src/com/asdasfa/jbs2bg/data/SettingsJacksonAdapter.java",
            "src/com/asdasfa/jbs2bg/presentation/BosJacksonWriter.java");
    private static final List<String> INTERNAL_POLICY_CALLERS = List.of(
            "src/com/asdasfa/jbs2bg/project/ProjectJacksonAdapter.java",
            "src/com/asdasfa/jbs2bg/data/SettingsJacksonAdapter.java",
            "src/com/asdasfa/jbs2bg/presentation/BosJacksonWriter.java",
            "src/com/asdasfa/jbs2bg/workbench/settings/SettingsFeature.java");

    /**
     * Reports whether one source imports a Jackson type without hiding I/O failures from the policy test.
     */
    private static boolean importsJackson(Path source) {
        try {
            return Files.readString(source).contains("import tools.jackson.");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect source import policy: " + source, exception);
        }
    }

    /**
     * Reports whether one production source imports the retired JSON codec.
     */
    private static boolean importsMinimalJson(Path source) {
        try {
            return Files.readString(source).contains("import com.eclipsesource.json");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect source import policy: " + source, exception);
        }
    }

    /**
     * Reports whether production source calls the internal-only Jackson policy support class.
     */
    private static boolean referencesInternalJacksonPolicy(Path source) {
        try {
            return Files.readString(source).contains("JacksonJson.");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect internal JSON policy calls: " + source, exception);
        }
    }

    /**
     * Verifies the selected Core-only LTS coordinate cannot drift through a property or transitive codec.
     */
    @Test
    void pinsJacksonCoreOnlyAtVersionThreeOneFive() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<jackson-core.version>3.1.5</jackson-core.version>"));
        assertTrue(pom.contains("<groupId>tools.jackson.core</groupId>"));
        assertTrue(pom.contains("<artifactId>jackson-core</artifactId>"));
        assertFalse(pom.contains("jackson-databind"));
    }

    /**
     * Verifies the retired codec and its temporary differential reader cannot remain in the checkpoint.
     */
    @Test
    void removesMinimalJsonAndTheTemporaryProjectComparisonReader() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertFalse(pom.contains("<groupId>com.eclipsesource.minimal-json</groupId>"));
        assertFalse(Files.exists(Path.of(
                "test/com/asdasfa/jbs2bg/project/LegacyProjectFileLoader.java")));
        try (var productionSources = Files.walk(Path.of("src"));
             var testSources = Files.walk(Path.of("test"))) {
            List<String> importers = Stream.concat(productionSources, testSources)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("JacksonDependencyPolicyTest.java"))
                    .filter(JacksonDependencyPolicyTest::importsMinimalJson)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
            assertTrue(importers.isEmpty(), () -> "Java sources still import minimal-json: " + importers);
        }
    }

    /**
     * Verifies dependency convergence and the retired codec are enforced across the resolved graph.
     */
    @Test
    void enforcesConvergenceAndBansMinimalJsonTransitively() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<dependencyConvergence>"));
        assertTrue(pom.contains("<exclude>com.eclipsesource.minimal-json:*</exclude>"));
    }

    /**
     * Verifies no Jackson type crosses the internal implementation or three format-adapter allowlist.
     */
    @Test
    void confinesJacksonImportsToInternalsAndFormatAdapters() throws IOException {
        try (var sources = Files.walk(Path.of("src"))) {
            List<String> importers = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(JacksonDependencyPolicyTest::importsJackson)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();

            assertEquals(List.of(
                    "src/com/asdasfa/jbs2bg/data/SettingsJacksonAdapter.java",
                    "src/com/asdasfa/jbs2bg/json/JacksonJson.java",
                    "src/com/asdasfa/jbs2bg/presentation/BosJacksonWriter.java",
                    "src/com/asdasfa/jbs2bg/project/ProjectJacksonAdapter.java"), importers);
            assertTrue(importers.containsAll(FORMAT_ADAPTERS));
        }
    }

    /**
     * Verifies Project publication has one owned writer route and no dormant minimal-json serializer.
     */
    @Test
    void projectPublicationUsesOnlyTheOwnedJacksonWriter() throws IOException {
        String publisher = Files.readString(
                Path.of("src/com/asdasfa/jbs2bg/project/ProjectFileWriter.java"));

        assertTrue(publisher.contains("ProjectJacksonAdapter.write(snapshot)"));
        assertFalse(publisher.contains("com.eclipsesource.json"));
        assertFalse(publisher.contains("WriterConfig"));
    }

    /**
     * Verifies Project loading has one owned streaming route and no packaged legacy codec fallback.
     *
     * @throws IOException when production sources cannot be inspected
     */
    @Test
    void projectLoadingUsesOnlyTheOwnedJacksonReader() throws IOException {
        String loader = Files.readString(
                Path.of("src/com/asdasfa/jbs2bg/project/ProjectFileLoader.java"));

        assertTrue(loader.contains("ProjectJacksonAdapter.read(source, operation)"));
        assertFalse(loader.contains("LegacyProjectFileLoader"));
        assertFalse(loader.contains("com.eclipsesource.json"));
        try (var sources = Files.walk(Path.of("src"))) {
            List<String> legacyCodecImporters = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(JacksonDependencyPolicyTest::importsMinimalJson)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
            assertTrue(legacyCodecImporters.isEmpty(),
                    () -> "Production sources still import minimal-json: " + legacyCodecImporters);
        }
    }

    /**
     * Enforces the friend-package boundary that Java source visibility cannot express:
     * only the owned adapters and Settings name-validation feature may call the public internal support class.
     */
    @Test
    void confinesInternalJacksonPolicyCallsToFormatAdapters() throws IOException {
        try (var sources = Files.walk(Path.of("src"))) {
            List<String> callers = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(JacksonDependencyPolicyTest::referencesInternalJacksonPolicy)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted()
                    .toList();

            assertEquals(INTERNAL_POLICY_CALLERS.stream().sorted().toList(), callers);
        }
    }
}
