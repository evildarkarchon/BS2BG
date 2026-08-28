package com.asdasfa.jbs2bg.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.asdasfa.jbs2bg.project.ProjectSession;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Machine-verifiable evidence that the pinned Java 25 toolchain owns the transitional build.
 *
 * <p>The provisioning script can only inspect the JDK it downloaded, and {@code .mvn/toolchains.xml} merely
 * declares a vendor; this test runs inside the JVM that Surefire actually forked and reads the class files that
 * javac actually emitted, so it witnesses the toolchain selection from the inside. Every constant here is a
 * deliberate pin: changing one is a reviewed toolchain change, not a test fix.
 */
class Java25ToolchainGuardTest {

    /** The accepted transitional target: Java 25 LTS. */
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

    /** The only accepted architecture for the transitional Windows x64 checkpoint. */
    private static final String EXPECTED_OS_ARCH = "amd64";

    private static final Pattern INCUBATOR_ARTIFACT = Pattern.compile("javafx-incubator|jfx[.]incubator", Pattern.CASE_INSENSITIVE);

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
        assertEquals(EXPECTED_OS_ARCH, System.getProperty("os.arch"), "The transitional checkpoint pins Windows x64");
        assertTrue(System.getProperty("os.name", "").startsWith("Windows"),
            "The transitional checkpoint pins Windows; got " + System.getProperty("os.name"));
    }

    @Test
    void admittedSourcesAreCompiledForRelease25WithoutPreview() throws IOException {
        ClassFileVersion version = readClassFileVersion(ProjectSession.class);
        assertEquals(EXPECTED_CLASS_MAJOR, version.major(), "javac must target --release 25 (class major 69)");
        assertEquals(EXPECTED_CLASS_MINOR, version.minor(), "preview features must stay disabled (class minor 0)");
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
            "JavaFX incubator artifacts must not be dependencies of the transitional build");
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
            DataInputStream data = new DataInputStream(in);
            int magic = data.readInt();
            assertEquals(0xCAFEBABE, magic, "not a class file: " + resource);
            int minor = data.readUnsignedShort();
            int major = data.readUnsignedShort();
            return new ClassFileVersion(major, minor);
        }
    }

    /** Immutable major/minor pair read from a class-file header. */
    private record ClassFileVersion(int major, int minor) {
    }
}
