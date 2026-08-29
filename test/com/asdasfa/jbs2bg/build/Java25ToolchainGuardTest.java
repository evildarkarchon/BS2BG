package com.asdasfa.jbs2bg.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.asdasfa.jbs2bg.Main;
import com.asdasfa.jbs2bg.project.ProjectSession;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Machine-verifiable evidence that the pinned Java 25 toolchain owns the complete application build.
 *
 * <p>The provisioning script can only inspect the JDK it downloaded, and {@code .mvn/toolchains.xml} merely
 * declares a vendor; this test runs inside the JVM that Surefire actually forked and reads the class files that
 * javac actually emitted, so it witnesses the toolchain selection from the inside. It also walks every emitted
 * class file and every packaged resource, so "every production source and resource" is a checked fact rather
 * than a claim. Every constant here is a deliberate pin: changing one is a reviewed toolchain change, not a
 * test fix.
 */
class Java25ToolchainGuardTest {

    /** The accepted target: Java 25 LTS. */
    private static final int EXPECTED_JAVA_FEATURE = 25;

    /** Vendor string reported by every Eclipse Temurin build; the toolchain requirement is {@code vendor=temurin}. */
    private static final String EXPECTED_JAVA_VENDOR = "Eclipse Adoptium";

    /**
     * Surefire forwards the pinned full build from the {@code bs2bg.toolchain.jdk.runtimeVersion} POM property so the
     * pom stays the single place that names it; the provisioning script cross-checks that property against the lock.
     */
    private static final String RUNTIME_VERSION_PROPERTY = "bs2bg.toolchain.jdk.runtimeVersion";

    /** Class-file major version emitted by {@code javac --release 25}. */
    private static final int EXPECTED_CLASS_MAJOR = 69;

    /** Preview-feature class files carry minor version 0xFFFF; a disabled-preview build must emit 0. */
    private static final int EXPECTED_CLASS_MINOR = 0;

    /** The only accepted architecture for the Windows x64 baseline. */
    private static final String EXPECTED_OS_ARCH = "amd64";

    private static final Pattern INCUBATOR_ARTIFACT = Pattern.compile("javafx-incubator|jfx[.]incubator", Pattern.CASE_INSENSITIVE);

    /** Repository layout the build compiles from; Surefire runs with the repository root as working directory. */
    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath();
    private static final Path SOURCE_ROOT = REPO_ROOT.resolve("src");
    private static final Path ASSETS_ROOT = REPO_ROOT.resolve("assets");

    /**
     * Internal-name prefixes no production class may mention anywhere in its constant pool (class references,
     * descriptors, or string constants): private JDK/JavaFX packages, the skin package that exposes VirtualFlow,
     * reflection, the private Modena resource directory, and incubator modules.
     */
    private static final List<String> FORBIDDEN_CONSTANT_POOL_TEXT = List.of("com/sun/", "jdk/internal/",
            "javafx/scene/control/skin/", "java/lang/reflect/", "skin/modena", "jfx/incubator/");

    @Test
    void jvmRunsOnJava25() {
        assertEquals(EXPECTED_JAVA_FEATURE, Runtime.version().feature(),
            "Surefire must fork the provisioned Java 25 toolchain, not the Maven host JDK; got " + Runtime.version());
    }

    @Test
    void jvmIsThePinnedTemurinBuild() {
        assertEquals(EXPECTED_JAVA_VENDOR, System.getProperty("java.vendor"),
            "The toolchain JDK must be Eclipse Temurin; a different vendor labelled 'temurin' in toolchains.xml is not accepted");
        String expectedRuntimeVersion = System.getProperty(RUNTIME_VERSION_PROPERTY);
        assertNotNull(expectedRuntimeVersion,
            RUNTIME_VERSION_PROPERTY + " must be forwarded by Surefire from the pom; run the tests through the Maven build");
        assertEquals(expectedRuntimeVersion, System.getProperty("java.runtime.version"),
            "The test JVM must be the pinned full Temurin build");
    }

    @Test
    void jvmRunsOnWindowsX64() {
        assertEquals(EXPECTED_OS_ARCH, System.getProperty("os.arch"), "The Java 25 baseline pins Windows x64");
        assertTrue(System.getProperty("os.name", "").startsWith("Windows"),
            "The Java 25 baseline pins Windows; got " + System.getProperty("os.name"));
    }

    /** Every emitted production class file carries the release 25 major and the no-preview minor version. */
    @Test
    void everyProductionClassIsCompiledForRelease25WithoutPreview() throws IOException, URISyntaxException {
        List<Path> classFiles = productionClassFiles();
        assertFalse(classFiles.isEmpty(), "no production class files found under " + productionClassesRoot());
        for (Path classFile : classFiles) {
            ClassFileVersion version = readClassFileVersion(classFile);
            assertEquals(EXPECTED_CLASS_MAJOR, version.major(), classFile + ": javac must target --release 25 (class major 69)");
            assertEquals(EXPECTED_CLASS_MINOR, version.minor(), classFile + ": preview features must stay disabled (class minor 0)");
        }
        // The seam class remains the named witness the evidence file points at.
        assertEquals(EXPECTED_CLASS_MAJOR, readClassFileVersion(ProjectSession.class).major());
    }

    /** Every production source file has a compiled class in the build output: no source is filtered out. */
    @Test
    void everyProductionSourceHasACompiledClass() throws IOException, URISyntaxException {
        Path classesRoot = productionClassesRoot();
        List<String> missing = new ArrayList<>();
        List<Path> sources = filesUnder(SOURCE_ROOT, ".java");
        assertTrue(sources.size() > 40, "expected the full production source tree, found " + sources.size());
        for (Path source : sources) {
            String relative = SOURCE_ROOT.relativize(source).toString().replace('\\', '/');
            Path classFile = classesRoot.resolve(relative.substring(0, relative.length() - ".java".length()) + ".class");
            if (!Files.isRegularFile(classFile))
                missing.add(relative);
        }
        assertEquals(List.of(), missing, "production sources without a compiled class");
    }

    /** Every non-Java file under src/ and assets/ is served from the build output and the test classpath. */
    @Test
    void everyProductionResourceIsInTheBuildOutput() throws IOException, URISyntaxException {
        Path classesRoot = productionClassesRoot();
        List<String> missing = new ArrayList<>();
        List<String> resources = new ArrayList<>();
        for (Path file : filesUnder(SOURCE_ROOT, ""))
            if (!file.toString().endsWith(".java"))
                resources.add(SOURCE_ROOT.relativize(file).toString().replace('\\', '/'));
        for (Path file : filesUnder(ASSETS_ROOT, ""))
            resources.add(ASSETS_ROOT.relativize(file).toString().replace('\\', '/'));
        assertTrue(resources.size() >= 15, "expected the full resource set, found " + resources);
        for (String resource : resources) {
            if (!Files.isRegularFile(classesRoot.resolve(resource)) || Main.class.getResource("/" + resource) == null)
                missing.add(resource);
        }
        assertEquals(List.of(), missing, "production resources missing from the build output");
    }

    /**
     * No production class references a private JDK/JavaFX API, the skin package, reflection, a Modena resource,
     * or an incubator module. This reads the constant pools javac emitted, so it catches what a source scan
     * would miss (for example a fully qualified name assembled at compile time).
     */
    @Test
    void noProductionClassReferencesPrivateSkinReflectiveOrIncubatorApis() throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        Path classesRoot = productionClassesRoot();
        for (Path classFile : productionClassFiles()) {
            for (String constant : readConstantPoolText(classFile)) {
                for (String forbidden : FORBIDDEN_CONSTANT_POOL_TEXT)
                    if (constant.contains(forbidden))
                        violations.add(classesRoot.relativize(classFile) + " -> " + constant);
                if (constant.startsWith("sun/"))
                    violations.add(classesRoot.relativize(classFile) + " -> " + constant);
            }
        }
        assertEquals(List.of(), violations);
    }

    /** The Workbench Project flow is the sole presentation caller of every ProjectSession write operation. */
    @Test
    void workbenchOwnsTheOnlyPresentationProjectWriteRoute() throws IOException, URISyntaxException {
        List<String> violations = new ArrayList<>();
        Path classesRoot = productionClassesRoot();
        String allowed = "com/asdasfa/jbs2bg/workbench/WorkbenchProjectFlow.class";
        for (Path classFile : productionClassFiles()) {
            String relative = classesRoot.relativize(classFile).toString().replace('\\', '/');
            if (relative.startsWith("com/asdasfa/jbs2bg/project/") || relative.equals(allowed))
                continue;
            List<String> constants = readConstantPoolText(classFile);
            if (!constants.contains("com/asdasfa/jbs2bg/project/ProjectSession"))
                continue;
            for (String writeMethod : List.of("newProject", "open", "save", "saveAs", "apply",
                    "importSliderPresets")) {
                if (constants.contains(writeMethod))
                    violations.add(relative + " -> ProjectSession." + writeMethod);
            }
        }
        assertEquals(List.of(), violations,
                "presentation Project writes must not bypass WorkbenchProjectFlow");
    }

    @Test
    void previewFeaturesAreNotEnabledOnTheTestJvm() {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        assertFalse(jvmArgs.stream().anyMatch(arg -> arg.startsWith("--enable-preview")),
            "--enable-preview must not reach the test JVM; args were " + jvmArgs);
    }

    /**
     * Tripwire rather than proof: nothing currently depends on an incubator artifact, so this can only fail once one
     * is introduced. The enforcer ban in pom.xml is the primary control; this keeps the test JVM honest as well.
     */
    @Test
    void javaFxIncubatorModulesAreNotOnTheBuildPath() {
        String classPath = System.getProperty("java.class.path", "");
        assertFalse(INCUBATOR_ARTIFACT.matcher(classPath).find(),
            "JavaFX incubator artifacts must not be dependencies of the build");
        boolean incubatorModuleLoaded = ModuleLayer.boot().modules().stream()
            .anyMatch(module -> module.getName().startsWith("jfx.incubator."));
        assertFalse(incubatorModuleLoaded, "JavaFX incubator modules must not be resolved into the boot layer");
    }

    /**
     * Reads the {@code major_version}/{@code minor_version} pair from the class file backing {@code type}.
     *
     * @throws IOException if the class bytes cannot be read from the test classpath
     */
    private static ClassFileVersion readClassFileVersion(Class<?> type) throws IOException {
        String resource = type.getSimpleName() + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            assertNotNull(in, "class bytes for " + type.getName() + " must be readable from the test classpath");
            return readClassFileVersion(in, resource);
        }
    }

    /** Reads the version pair from a class file on disk. */
    private static ClassFileVersion readClassFileVersion(Path classFile) throws IOException {
        try (InputStream in = Files.newInputStream(classFile)) {
            return readClassFileVersion(in, classFile.toString());
        }
    }

    /** Reads the version pair from an open class-file stream; {@code label} names it in failures. */
    private static ClassFileVersion readClassFileVersion(InputStream in, String label) throws IOException {
        DataInputStream data = new DataInputStream(in);
        int magic = data.readInt();
        assertEquals(0xCAFEBABE, magic, "not a class file: " + label);
        int minor = data.readUnsignedShort();
        int major = data.readUnsignedShort();
        return new ClassFileVersion(major, minor);
    }

    /**
     * Returns every CONSTANT_Utf8 entry of a class file: class names, descriptors, and string constants all
     * flow through this table, so scanning it finds any reference to a forbidden package or resource path.
     */
    private static List<String> readConstantPoolText(Path classFile) throws IOException {
        List<String> text = new ArrayList<>();
        try (DataInputStream data = new DataInputStream(Files.newInputStream(classFile))) {
            assertEquals(0xCAFEBABE, data.readInt(), "not a class file: " + classFile);
            data.readUnsignedShort(); // minor
            data.readUnsignedShort(); // major
            int count = data.readUnsignedShort();
            for (int index = 1; index < count; index++) {
                int tag = data.readUnsignedByte();
                switch (tag) {
                    case 1 -> { // CONSTANT_Utf8
                        int length = data.readUnsignedShort();
                        byte[] bytes = new byte[length];
                        data.readFully(bytes);
                        text.add(new String(bytes, StandardCharsets.UTF_8));
                    }
                    case 3, 4 -> data.skipBytes(4); // Integer, Float
                    case 5, 6 -> { // Long, Double take two constant-pool slots
                        data.skipBytes(8);
                        index++;
                    }
                    case 7, 8, 16, 19, 20 -> data.skipBytes(2); // Class, String, MethodType, Module, Package
                    case 9, 10, 11, 12, 17, 18 -> data.skipBytes(4); // refs, NameAndType, Dynamic, InvokeDynamic
                    case 15 -> data.skipBytes(3); // MethodHandle
                    default -> throw new IOException("unknown constant pool tag " + tag + " in " + classFile);
                }
            }
        }
        return text;
    }

    /** The directory javac emitted production classes into (target/classes), located from a production class. */
    private static Path productionClassesRoot() throws URISyntaxException {
        Path root = Paths.get(ProjectSession.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "production classes must be on the test classpath as a directory: " + root);
        return root;
    }

    private static List<Path> productionClassFiles() throws IOException, URISyntaxException {
        return filesUnder(productionClassesRoot(), ".class");
    }

    /** Every regular file under {@code root} whose path ends with {@code suffix} (empty matches all), sorted. */
    private static List<Path> filesUnder(Path root, String suffix) throws IOException {
        assertTrue(Files.isDirectory(root), "missing directory " + root + "; run the tests from the repository root");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(suffix)).sorted().toList();
        }
    }

    /** Immutable major/minor pair read from a class-file header. */
    private record ClassFileVersion(int major, int minor) {
    }
}
