package com.asdasfa.jbs2bg.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.Launcher;
import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.project.ProjectSession;

import javafx.application.Application;

/**
 * Contract of the packaged entrypoint (issue #97): the Windows app-image starts
 * a small launcher that is not itself a JavaFX {@link Application}, and that
 * launcher hands over to the one existing {@link Main} application rather than
 * introducing a second Project flow. The packaged smoke run proves the hand-over
 * end to end; this test pins the structural shape so a refactor cannot quietly
 * turn the launcher into a second {@code Application} or add another one.
 */
class LauncherTest {

    /**
     * The directory javac emitted production classes into (target/classes), located from a production class.
     */
    private static Path productionClassesRoot() throws URISyntaxException {
        Path root = Path.of(ProjectSession.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "production classes must be on the test classpath as a directory: " + root);
        return root;
    }

    /**
     * The JDK launcher must never see an {@code Application} subclass as the main class of the image.
     */
    @Test
    void launcherIsNotAJavaFxApplication() {
        assertFalse(Application.class.isAssignableFrom(Launcher.class),
                "Launcher must not extend javafx.application.Application");
    }

    /**
     * jpackage's {@code --main-class} needs a conventional {@code public static void main(String[])}.
     */
    @Test
    void launcherExposesAPlainMainEntrypoint() throws NoSuchMethodException {
        Method main = Launcher.class.getMethod("main", String[].class);
        assertTrue(Modifier.isStatic(main.getModifiers()), "main must be static");
        assertTrue(Modifier.isPublic(main.getModifiers()), "main must be public");
        assertEquals(void.class, main.getReturnType(), "main must return void");
    }

    /**
     * Exactly one JavaFX application exists in the production classes: {@link Main}.
     * Every emitted class is loaded without initialization and checked, so a second
     * {@code Application} (a parallel Project flow) cannot appear unnoticed.
     */
    @Test
    void mainRemainsTheSoleApplicationSubclass() throws IOException, URISyntaxException, ClassNotFoundException {
        Path classesRoot = productionClassesRoot();
        List<String> applications = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classesRoot)) {
            for (Path classFile : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                String relative = classesRoot.relativize(classFile).toString().replace('\\', '/');
                String className = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
                // initialize=false: loading must not run static initializers (no toolkit, no settings I/O).
                Class<?> type = Class.forName(className, false, Main.class.getClassLoader());
                if (Application.class.isAssignableFrom(type))
                    applications.add(type.getName());
            }
        }
        assertEquals(List.of(Main.class.getName()), applications,
                "Main must remain the sole javafx.application.Application in the production classes");
    }
}
