package com.asdasfa.jbs2bg.presentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Locks issue #106's single Workbench Output route and retired Commons IO dependency boundary.
 */
final class OutputRoutePolicyTest {

    /** Commons IO must not remain direct, transitive, or imported after the owned publisher cutover. */
    @Test
    void removesCommonsIoFromTheDependencyAndSourceGraphs() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertFalse(pom.contains("<groupId>commons-io</groupId>"));
        assertTrue(pom.contains("<exclude>commons-io:commons-io</exclude>"));
        try (var sources = Files.walk(Path.of("src"))) {
            List<String> importers = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(OutputRoutePolicyTest::importsCommonsIo)
                    .map(path -> path.toString().replace('\\', '/')).sorted().toList();
            assertTrue(importers.isEmpty(), () -> "Production sources still import Commons IO: " + importers);
        }
    }

    /** Legacy popup, generation, preview, copy, BoS, and export routes cannot coexist with OutputFeature. */
    @Test
    void removesEveryReplacedLegacyOutputRoute() throws IOException {
        assertFalse(Files.exists(Path.of("src/com/asdasfa/jbs2bg/PopupBosViewController.java")));
        assertFalse(Files.exists(Path.of("src/com/asdasfa/jbs2bg/popup_bosview.fxml")));
        assertFalse(Files.exists(Path.of("src/com/asdasfa/jbs2bg/PopupNoPresetNotifController.java")));
        assertFalse(Files.exists(Path.of("src/com/asdasfa/jbs2bg/popup_nopresetnotif.fxml")));
        assertFalse(Files.exists(Path.of("src/com/asdasfa/jbs2bg/presentation/BosArtifactPublisher.java")));
        String legacyController = Files.readString(Path.of("src/com/asdasfa/jbs2bg/MainController.java"));
        for (String route : List.of("generateTemplates", "generateMorphs", "copyTemplates", "copyMorphs",
                "exportBosJson", "writeIniOutputs", "showPopupBosView", "updateTemplateText"))
            assertFalse(legacyController.contains(route), () -> "Legacy Output route remains: " + route);
    }

    /** Reports whether one production source retains a forbidden Commons IO import. */
    private static boolean importsCommonsIo(Path source) {
        try {
            return Files.readString(source).contains("import org.apache.commons.io");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect source: " + source, exception);
        }
    }
}
