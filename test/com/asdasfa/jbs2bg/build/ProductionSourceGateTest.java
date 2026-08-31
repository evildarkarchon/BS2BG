package com.asdasfa.jbs2bg.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Structural gate over the production source tree and the build definition.
 * It rejects, before any of it can reach a class file, every dependency the
 * Java 25 / JavaFX 25 baseline forbids: private JDK or JavaFX packages,
 * private Modena resources, reflective or skin-level access, preview features,
 * and JavaFX incubator modules. It also pins the build definition so a
 * source-filtered compile can never again be presented as the verification
 * gate. {@link Java25ToolchainGuardTest} repeats the API checks on the emitted
 * class files, so a violation hidden from the text scan still fails.
 */
class ProductionSourceGateTest {

    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath();
    private static final Path SOURCE_ROOT = REPO_ROOT.resolve("src");
    private static final Path POM = REPO_ROOT.resolve("pom.xml");

    /**
     * Each entry: a token that must not appear in any production .java file, with the reason.
     */
    private static final Map<Pattern, String> FORBIDDEN_IN_JAVA = Map.ofEntries(
            Map.entry(Pattern.compile("\\bcom\\.sun\\."), "private JDK/JavaFX package"),
            Map.entry(Pattern.compile("^\\s*import\\s+sun\\.", Pattern.MULTILINE), "private JDK package"),
            Map.entry(Pattern.compile("\\bjdk\\.internal\\."), "private JDK package"),
            Map.entry(Pattern.compile("skin/modena|modena/[\\w-]+\\.(?:png|css)"), "private Modena resource"),
            Map.entry(Pattern.compile("\\bjavafx\\.scene\\.control\\.skin\\."), "skin/virtual-flow internals"),
            Map.entry(Pattern.compile("\\.getSkin\\(\\)"), "skin access"),
            Map.entry(Pattern.compile("\\.lookup(?:All)?\\(\\s*\""), "skin child lookup by CSS selector"),
            Map.entry(Pattern.compile("\\bjava\\.lang\\.reflect\\b|\\.setAccessible\\(|\\.getDeclared(?:Field|Method|Constructor)s?\\(|privateLookupIn"),
                    "reflective access"),
            Map.entry(Pattern.compile("@SuppressWarnings\\(\"restriction\"\\)"), "suppressed restricted-API warning"),
            Map.entry(Pattern.compile("enable-preview"), "preview features"),
            Map.entry(Pattern.compile("jfx\\.incubator|javafx\\.incubator|javafx-incubator"), "JavaFX incubator module"));

    /**
     * Tokens forbidden in FXML and CSS resources.
     */
    private static final Map<Pattern, String> FORBIDDEN_IN_RESOURCES = Map.ofEntries(
            Map.entry(Pattern.compile("com[./]sun[./]"), "private JDK/JavaFX package"),
            Map.entry(Pattern.compile("modena"), "private Modena resource"),
            Map.entry(Pattern.compile("jfx[./]incubator"), "JavaFX incubator module"));

    /**
     * Appends one "path:line reason (match)" entry per forbidden pattern found in the file.
     */
    private static void scan(Path file, Map<Pattern, String> forbidden, List<String> violations) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        for (Map.Entry<Pattern, String> rule : forbidden.entrySet()) {
            var matcher = rule.getKey().matcher(text);
            if (matcher.find()) {
                int line = 1 + (int) text.substring(0, matcher.start()).chars().filter(c -> c == '\n').count();
                violations.add(REPO_ROOT.relativize(file) + ":" + line + " " + rule.getValue() + " ("
                        + matcher.group() + ")");
            }
        }
    }

    private static List<Path> productionFiles(String suffix) throws IOException {
        assertTrue(Files.isDirectory(SOURCE_ROOT), "tests must run from the repository root; cwd is " + REPO_ROOT);
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(suffix)).sorted()
                    .toList();
        }
    }

    private static Document parse(Path xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(xml.toFile());
    }

    private static String property(Document pom, String name) {
        return child(child(pom.getDocumentElement(), "properties"), name).getTextContent().trim();
    }

    /**
     * Every {@code <configuration>} nested under any maven-compiler-plugin element, wherever it appears in the pom.
     */
    private static List<Element> compilerPluginConfigurations(Document pom) {
        List<Element> configurations = new ArrayList<>();
        NodeList plugins = pom.getElementsByTagName("plugin");
        for (int index = 0; index < plugins.getLength(); index++) {
            Element plugin = (Element) plugins.item(index);
            List<Element> artifactIds = children(plugin, "artifactId");
            if (artifactIds.size() != 1 || !"maven-compiler-plugin".equals(artifactIds.get(0).getTextContent().trim()))
                continue;
            NodeList nested = plugin.getElementsByTagName("configuration");
            for (int nestedIndex = 0; nestedIndex < nested.getLength(); nestedIndex++)
                configurations.add((Element) nested.item(nestedIndex));
        }
        return configurations;
    }

    /**
     * The build/plugins entry for the given artifact; fails when the pom does not configure it.
     */
    private static Element plugin(Document pom, String artifactId) {
        Element plugins = child(child(pom.getDocumentElement(), "build"), "plugins");
        return plugin(plugins, artifactId, "build/plugins");
    }

    /**
     * The named plugin directly under a plugins element; fails with the supplied location when absent.
     */
    private static Element plugin(Element plugins, String artifactId, String location) {
        for (Element plugin : children(plugins, "plugin"))
            if (artifactId.equals(child(plugin, "artifactId").getTextContent().trim()))
                return plugin;
        throw new AssertionError("pom.xml must configure " + artifactId + " under " + location);
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

    @Test
    void noProductionSourceUsesPrivateSkinReflectivePreviewOrIncubatorApis() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : productionFiles(".java"))
            scan(source, FORBIDDEN_IN_JAVA, violations);
        assertEquals(List.of(), violations);
    }

    @Test
    void noProductionResourceReferencesPrivateModenaOrIncubatorContent() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path resource : productionFiles(".fxml"))
            scan(resource, FORBIDDEN_IN_RESOURCES, violations);
        for (Path resource : productionFiles(".css"))
            scan(resource, FORBIDDEN_IN_RESOURCES, violations);
        assertEquals(List.of(), violations);
    }

    /**
     * No vendored ControlsFX filter (or any other copied third-party source tree) may return.
     */
    @Test
    void theVendoredFilterIsGone() {
        assertFalse(Files.exists(SOURCE_ROOT.resolve(Paths.get("com", "asdasfa", "jbs2bg", "controlsfx"))),
                "the vendored ControlsFX filter must stay deleted");
    }

    /**
     * The compiler plugin compiles every production source, for release 25,
     * with full lint promoted to errors, and keeps the transitional escape
     * hatches (include lists, implicit-none, empty sourcepath) out.
     */
    @Test
    void compilerConfigurationCompilesEverySourceWithFullLint() throws Exception {
        Document pom = parse(POM);
        assertEquals("25", property(pom, "maven.compiler.release"), "maven.compiler.release");

        plugin(pom, "maven-compiler-plugin"); // the plugin must be configured under build/plugins at all
        // Every compiler-plugin configuration anywhere in the pom counts (executions, pluginManagement, profiles):
        // a filter placed there would narrow the build while the top-level configuration looked clean.
        List<Element> configurations = compilerPluginConfigurations(pom);
        assertFalse(configurations.isEmpty(), "maven-compiler-plugin must carry a configuration");
        List<String> args = new ArrayList<>();
        for (Element configuration : configurations) {
            for (String escapeHatch : List.of("includes", "excludes", "testIncludes", "testExcludes", "implicit",
                    "compileSourceRoots", "generatedSourcesDirectory"))
                assertTrue(children(configuration, escapeHatch).isEmpty(),
                        "maven-compiler-plugin must not configure <" + escapeHatch + ">");
            for (Element compilerArgs : children(configuration, "compilerArgs"))
                for (Element arg : children(compilerArgs, "arg"))
                    args.add(arg.getTextContent().trim());
        }
        assertTrue(args.contains("-Xlint:all"), "compilerArgs must enable every lint category: " + args);
        assertTrue(args.contains("-Werror"), "compilerArgs must promote warnings to errors: " + args);
        for (String arg : args) {
            assertFalse(arg.startsWith("-sourcepath") || arg.startsWith("--source-path"),
                    "compilerArgs must not override the source path: " + arg);
            assertFalse(arg.startsWith("-implicit") || arg.startsWith("-Xlint:-"),
                    "compilerArgs must not narrow compilation or lint: " + arg);
            assertFalse(arg.contains("enable-preview"), "compilerArgs must not enable preview features");
        }
    }

    /**
     * Neither the POM nor the wrapper's JVM/Maven config may enable preview features.
     */
    @Test
    void previewFeaturesAreNotEnabledByAnyBuildInput() throws Exception {
        // Element text only: the pom's comments are allowed to say that the flag is forbidden.
        String pomText = parse(POM).getDocumentElement().getTextContent();
        assertFalse(pomText.contains("enable-preview"), POM + " must not enable preview features");
        for (Path input : List.of(REPO_ROOT.resolve(".mvn").resolve("maven.config"),
                REPO_ROOT.resolve(".mvn").resolve("jvm.config"))) {
            if (!Files.exists(input))
                continue;
            String text = Files.readString(input, StandardCharsets.UTF_8);
            assertFalse(text.contains("enable-preview"), input + " must not enable preview features");
        }
    }

    /**
     * OpenRewrite remains an explicitly activated maintenance tool rather than a lifecycle-bound part of the
     * application gate, and its only configured migration is the reviewed Java 25 composite recipe.
     */
    @Test
    void openRewriteJava25MigrationIsOptInAndUnbound() throws Exception {
        Document pom = parse(POM);
        Element project = pom.getDocumentElement();
        Element profiles = child(project, "profiles");
        List<Element> matches = children(profiles, "profile").stream()
                .filter(profile -> "openrewrite".equals(child(profile, "id").getTextContent().trim())).toList();
        assertEquals(1, matches.size(), "pom.xml must define one openrewrite profile");

        Element profile = matches.get(0);
        assertTrue(children(profile, "activation").isEmpty(), "the openrewrite profile must require explicit activation");
        Element rewrite = plugin(child(child(profile, "build"), "plugins"), "rewrite-maven-plugin",
                "profiles/profile[id=openrewrite]/build/plugins");
        assertTrue(children(rewrite, "executions").isEmpty(), "OpenRewrite must not be bound to a lifecycle phase");

        Element activeRecipes = child(child(rewrite, "configuration"), "activeRecipes");
        List<String> recipes = children(activeRecipes, "recipe").stream().map(Element::getTextContent)
                .map(String::trim).toList();
        assertEquals(List.of("org.openrewrite.java.migrate.UpgradeToJava25"), recipes,
                "the profile must activate only the Java 25 migration recipe");

        Element recipeDependency = child(child(rewrite, "dependencies"), "dependency");
        assertEquals("org.openrewrite.recipe", child(recipeDependency, "groupId").getTextContent().trim());
        assertEquals("rewrite-migrate-java", child(recipeDependency, "artifactId").getTextContent().trim());
        assertEquals("${rewrite-migrate-java.version}", child(recipeDependency, "version").getTextContent().trim());

        Element managedPlugins = child(child(child(project, "build"), "pluginManagement"), "plugins");
        Element managedRewrite = plugin(managedPlugins, "rewrite-maven-plugin", "build/pluginManagement/plugins");
        assertEquals("${rewrite-maven-plugin.version}", child(managedRewrite, "version").getTextContent().trim());
        assertTrue(property(pom, "rewrite-maven-plugin.version").matches("\\d+\\.\\d+\\.\\d+"),
                "rewrite-maven-plugin.version must be an exact release");
        assertTrue(property(pom, "rewrite-migrate-java.version").matches("\\d+\\.\\d+\\.\\d+"),
                "rewrite-migrate-java.version must be an exact release");

        Element applicationPlugins = child(child(project, "build"), "plugins");
        assertTrue(children(applicationPlugins, "plugin").stream()
                        .noneMatch(candidate -> "rewrite-maven-plugin".equals(
                                child(candidate, "artifactId").getTextContent().trim())),
                "OpenRewrite must stay out of the default application build");
    }

    /**
     * The enforcer keeps JavaFX incubator artifacts out of the dependency graph.
     */
    @Test
    void enforcerBansJavaFxIncubatorModules() throws Exception {
        Document pom = parse(POM);
        String pomText = Files.readString(POM, StandardCharsets.UTF_8);
        assertTrue(pomText.contains("<exclude>org.openjfx:javafx-incubator-*</exclude>"),
                "pom.xml must ban org.openjfx:javafx-incubator-* through bannedDependencies");
        for (Element dependency : children(child(pom.getDocumentElement(), "dependencies"), "dependency")) {
            String artifactId = child(dependency, "artifactId").getTextContent().trim();
            assertFalse(artifactId.startsWith("javafx-incubator"), "incubator dependency " + artifactId);
        }
    }

    /**
     * The selected bundled-vector icon stack ships alone; an unverified Ikonli fallback cannot drift back in.
     */
    @Test
    void enforcerKeepsTheUnselectedIkonliStackOutOfTheImage() throws Exception {
        Document pom = parse(POM);
        String pomText = Files.readString(POM, StandardCharsets.UTF_8);
        assertTrue(pomText.contains("<exclude>org.kordamp.ikonli:*</exclude>"),
                "pom.xml must ban the unselected Ikonli stack through bannedDependencies");
        for (Element dependency : children(child(pom.getDocumentElement(), "dependencies"), "dependency")) {
            String groupId = child(dependency, "groupId").getTextContent().trim();
            assertFalse("org.kordamp.ikonli".equals(groupId),
                    "only the selected application-owned bundled vectors may ship");
        }
    }
}
