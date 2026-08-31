package com.asdasfa.jbs2bg.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.asdasfa.jbs2bg.Main;

/**
 * Structural gate over the build definition for the Windows app-image (issue #97).
 * The pinned Maven Wrapper stages everything jpackage consumes: the application
 * jar beside a {@code lib/} directory holding every runtime-scoped dependency.
 * JavaFX is deliberately absent from that staging area because the packaging
 * script links it into the bundled runtime from the pinned JMODs, so it is a
 * {@code provided} dependency here. The application version that jpackage stamps
 * on the image is the same value the application displays.
 */
class WindowsAppImageGateTest {

    private static final Path POM = Paths.get("").toAbsolutePath().resolve("pom.xml");

    /**
     * Staging directory (relative to target/) that jpackage receives through {@code --input}.
     */
    private static final String STAGING_DIRECTORY = "${project.build.directory}/app-image-input";

    /**
     * Surefire forwards the {@code bs2bg.app.version} POM property so the pom stays the single place that names it.
     */
    private static final String APP_VERSION_PROPERTY = "bs2bg.app.version";

    /**
     * Substitutes one level of {@code ${name}} references from the pom's own {@code <properties>}; built-in
     * Maven properties such as {@code project.build.directory} are left as written so the expected literal
     * can name them directly.
     */
    private static String resolve(Document pom, String text) {
        String resolved = text.trim();
        Element properties = child(pom.getDocumentElement(), "properties");
        NodeList nodes = properties.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element)
                resolved = resolved.replace("${" + node.getNodeName() + "}", node.getTextContent().trim());
        }
        return resolved;
    }

    private static Document parse(Path xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(xml.toFile());
    }

    /**
     * The build/plugins entry for the given artifact; fails when the pom does not configure it.
     */
    private static Element plugin(Document pom, String artifactId) {
        Element plugins = child(child(pom.getDocumentElement(), "build"), "plugins");
        for (Element plugin : children(plugins, "plugin"))
            if (artifactId.equals(child(plugin, "artifactId").getTextContent().trim()))
                return plugin;
        throw new AssertionError("pom.xml must configure " + artifactId + " under build/plugins");
    }

    /**
     * The single child element of that name; fails when there are zero or several.
     */
    private static Element child(Element parent, String name) {
        List<Element> matches = children(parent, name);
        assertEquals(1, matches.size(), parent.getTagName() + " must have exactly one <" + name + ">");
        return matches.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element && name.equals(node.getNodeName()))
                matches.add((Element) node);
        }
        return matches;
    }

    /**
     * The version jpackage stamps (--app-version) is the version the About dialog shows.
     */
    @Test
    void applicationVersionIsPinnedOnceForTheLauncherAndTheImage() {
        String pinned = System.getProperty(APP_VERSION_PROPERTY);
        assertNotNull(pinned, APP_VERSION_PROPERTY + " must be forwarded by Surefire from the pom; run the tests through the Maven build");
        assertTrue(pinned.matches("\\d+\\.\\d+\\.\\d+"), "app version must be MAJOR.MIDDLE.MINOR: " + pinned);
        assertEquals(Main.APP_VERSION, pinned, "pom bs2bg.app.version must equal the version Main displays");
    }

    /**
     * The application jar lands in the staging directory with the launcher as its main class.
     */
    @Test
    void applicationJarIsStagedWithTheLauncherAsMainClass() throws Exception {
        Document pom = parse(POM);
        Element jarPlugin = plugin(pom, "maven-jar-plugin");
        Element configuration = child(jarPlugin, "configuration");
        assertEquals(STAGING_DIRECTORY, resolve(pom, child(configuration, "outputDirectory").getTextContent()),
                "maven-jar-plugin must write the application jar into the app-image staging directory");
        Element manifest = child(child(configuration, "archive"), "manifest");
        assertEquals("com.asdasfa.jbs2bg.Launcher", child(manifest, "mainClass").getTextContent().trim(),
                "the jar manifest must name the non-Application launcher");
    }

    /**
     * Every runtime-scoped dependency is copied beside the jar; JavaFX comes from the bundled runtime instead.
     */
    @Test
    void runtimeDependenciesAreStagedBesideTheJarWithoutJavaFx() throws Exception {
        Document pom = parse(POM);
        Element dependencyPlugin = plugin(pom, "maven-dependency-plugin");
        Element staging = null;
        for (Element execution : children(child(dependencyPlugin, "executions"), "execution")) {
            List<String> goals = new ArrayList<>();
            for (Element goal : children(child(execution, "goals"), "goal"))
                goals.add(goal.getTextContent().trim());
            if (goals.contains("copy-dependencies"))
                staging = execution;
        }
        assertNotNull(staging, "maven-dependency-plugin must bind a copy-dependencies execution");
        String phase = child(staging, "phase").getTextContent().trim();
        assertTrue(List.of("prepare-package", "package").contains(phase),
                "dependency staging must run in the package lifecycle, not only on demand: " + phase);
        Element configuration = child(staging, "configuration");
        assertEquals(STAGING_DIRECTORY + "/lib", resolve(pom, child(configuration, "outputDirectory").getTextContent()),
                "dependencies must be staged under app-image-input/lib");
        assertEquals("runtime", child(configuration, "includeScope").getTextContent().trim(),
                "only runtime-scoped dependencies belong in the image payload");
        assertEquals("org.openjfx", child(configuration, "excludeGroupIds").getTextContent().trim(),
                "JavaFX jars must never be staged; the runtime links them from the pinned JMODs");

        for (Element dependency : children(child(pom.getDocumentElement(), "dependencies"), "dependency")) {
            if (!"org.openjfx".equals(child(dependency, "groupId").getTextContent().trim()))
                continue;
            List<Element> scope = children(dependency, "scope");
            assertFalse(scope.isEmpty(), "JavaFX dependencies must declare the provided scope");
            assertEquals("provided", scope.get(0).getTextContent().trim(),
                    child(dependency, "artifactId").getTextContent().trim() + " must be provided by the bundled runtime");
        }
    }
}
