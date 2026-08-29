# Java 25 application verification run

Status: complete application gate (issue #96, parent #81; the pinned runner was introduced by #94). A green run
proves that every production source and resource builds, packages, and links on the pinned Java 25 / JavaFX 25
toolchain. A source-filtered build can no longer be reported or invoked through this command.

## One command

From a clean Windows x64 checkout, with or without any JDK installed:

```powershell
.\tools\java25\verify-java25.ps1
```

The script:

1. Loads `tools/java25/toolchain-lock.json`, cross-checks `.mvn/wrapper/maven-wrapper.properties` against it, and
   asserts that `pom.xml` compiles every production source with `-Xlint:all -Werror` and no include/exclude list,
   `<implicit>`, or sourcepath override (`Assert-CompleteSourceScope`). Maven arguments that skip or filter tests
   (`skipTests`, `maven.test.skip`, `-Dtest=`) are rejected, and a gate run must include both `clean` and `verify`.
2. Checksum-provisions **Eclipse Temurin 25.0.4.1+1** (Windows x64 JDK) and the **Gluon JavaFX 25.0.4** Windows x64
   JMODs into `%LOCALAPPDATA%\BS2BG\build-toolchains\` (override with `-CacheRoot` or `BS2BG_TOOLCHAIN_CACHE`).
   SHA-256 is verified before extraction; the JDK `release` metadata (vendor, full build number, image type,
   OS, architecture) and each `jmod describe` header are verified after. Nothing is written to a global JDK.
3. Exports `BS2BG_JDK25_HOME` (consumed by `.mvn/toolchains.xml`) and runs the committed Maven Wrapper with
   `--batch-mode --strict-checksums clean verify` plus `dependency:tree` / `dependency:build-classpath`. Maven
   Enforcer requires dependency convergence and bans transitive reintroduction of minimal-json.
4. Verifies the gate itself: Surefire reports zero failures on the pinned runtime, every required suite (listed
   below) ran green, and the built jar contains a class for every production source and every production
   resource (`Assert-JarContainsProductionResources`).
5. Writes reproducibility evidence to `target/reproducibility/` and prints the gate report.

Any unexpected checksum, version string, architecture, Maven/plugin version, compiler scope, missing suite, or
missing artifact entry fails closed with exit code 1. The script runs on PowerShell 7 or Windows PowerShell 5.1.

Cache reuse trusts the `.bs2bg-provisioned` marker (which records the archive SHA-256) rather than re-hashing the
extracted tree; the JDK `release` file, `java -version`, `os.arch`, and `jmod describe` checks re-run on every
invocation. Delete the cache directory to force a fresh download and hash verification.

## What the gate checks

| Concern | Enforced by |
| --- | --- |
| Every production source compiles for release 25 | `pom.xml` has no compiler include list; `Java25ToolchainGuardTest.everyProductionSourceHasACompiledClass` and `everyProductionClassIsCompiledForRelease25WithoutPreview` (every emitted class file: major 69, minor 0); `Assert-CompleteSourceScope` before Maven runs |
| Full lint enforcement | `-Xlint:all -Werror` in `pom.xml`, asserted by `ProductionSourceGateTest.compilerConfigurationCompilesEverySourceWithFullLint` and `Assert-CompleteSourceScope` |
| No private JDK/JavaFX API, private Modena resource, reflective or skin access | `ProductionSourceGateTest` (text scan of `src/**` `.java`, `.fxml`, `.css`) and `Java25ToolchainGuardTest.noProductionClassReferencesPrivateSkinReflectiveOrIncubatorApis` (constant-pool scan of every class in `target/classes`) |
| Preview features disabled | `ProductionSourceGateTest.previewFeaturesAreNotEnabledByAnyBuildInput` (pom element text, `.mvn/*.config`); guard test reads class minor version and the test JVM's input arguments |
| JavaFX incubator modules disabled | enforcer `bannedDependencies org.openjfx:javafx-incubator-*`; guard test scans the classpath and boot layer; source gate rejects `jfx.incubator` |
| One converged production JSON codec | Enforcer `dependencyConvergence` and `bannedDependencies com.eclipsesource.minimal-json:*`; `JacksonDependencyPolicyTest` pins Jackson Core 3.1.5, scans production/test imports, and requires the temporary comparison reader to remain deleted |
| Every production resource in the artifact | `Java25ToolchainGuardTest.everyProductionResourceIsInTheBuildOutput` (`target/classes` and classpath) and `Assert-JarContainsProductionResources` on the packaged jar |
| Representative FXML/controller graphs load | `FxmlGraphLoadingTest`: `main.fxml` (root), every `popup_*.fxml` (popups), `custom_notif.fxml` / `custom_confirm.fxml` / `setslider_control.fxml` (custom roots), on the JavaFX toolkit started through `Platform.startup` with a 30 s per-body timeout; Surefire's `forkedProcessTimeoutInSeconds` bounds the whole fork so a toolkit hang cannot hang the build |
| Logical visible-set behavior preserved | `FilteredViewTest`, `VisibleScopeCommandsTest` (seam), `FilteredTableAdapterTest` (public-JavaFX adapter: AND filtering, sort through `TableView.sortOrder`, identity-stable selection, frozen bulk scope, detach) |
| Project, Settings, and BoS JSON contracts | `ProjectSession*Test`, `ProjectJacksonCompatibilityTest`, `ProjectTest`, `ProjectPersistenceCompatibilityTest`, `SettingsJacksonAdapterTest`, `SettingsPairPublisherTest`, `ProjectOutputFormatterTest`, `BosJacksonWriterTest`, and `BosArtifactPublisherTest` required by the script; `JacksonJsonTest` and `JacksonDependencyPolicyTest` witness the shared policy |

The required suites the script asserts (report present, at least one test, zero failures/errors):
`Java25ToolchainGuardTest`, `ProductionSourceGateTest`, `FxmlGraphLoadingTest`, `LauncherTest`,
`WindowsAppImageGateTest`, `FilteredTableAdapterTest`,
`DialogGraphicsTest`, `FilteredViewTest`, `VisibleScopeCommandsTest`, `ProjectSessionTest`,
`ProjectSessionOpenTest`, `ProjectSessionSaveTest`, `ProjectSessionImportTest`, `ProjectSessionSliderChoiceTest`,
`ProjectJacksonCompatibilityTest`, `ProjectTest`, `ProjectPersistenceCompatibilityTest`, `JacksonJsonTest`,
`JacksonDependencyPolicyTest`, `SettingsJacksonAdapterTest`, `SettingsPairPublisherTest`,
`ProjectOutputFormatterTest`, `BosJacksonWriterTest`, and `BosArtifactPublisherTest`.

## Public-JavaFX replacements made for this gate

- The vendored ControlsFX `TableFilter` (which reached into `TableViewSkin`) is deleted. The three filterable
  tables (NPC Morph Assignment, no-preset warning, NPC Database) use `com.asdasfa.jbs2bg.fx.FilteredTableAdapter`,
  which renders `filtering.FilteredView` into `TableView.getItems()`, maps `TableView.sortOrder` onto `SortKey`s
  through a custom `sortPolicy`, syncs the table selection with the view's identity selection, and installs a
  `ColumnFilterMenu` checklist as each column's `contextMenu` (JavaFX opens it on a header right-click). Bulk
  commands freeze `adapter.visibleSet()` and hand it to `VisibleScopeCommands`.
- Type-ahead and selection reveal call `scrollTo` directly. JavaFX 25's `VirtualFlow.scrollTo` performs the
  minimal scroll and is a no-op when the row is already visible, so the JavaFX 8 `isIndexVisible` skin probe
  had no remaining purpose.
- Information, error, warning, and confirmation graphics come from `fx.DialogGraphics`, drawn with public shapes
  and rasterized through `Node.snapshot`; no `com/sun/javafx/scene/control/skin/modena/*.png` path remains.

## What is pinned, and where

| Input | Pin | Verified by |
| --- | --- | --- |
| Apache Maven distribution | `3.9.16`, SHA-256 in `.mvn/wrapper/maven-wrapper.properties` (`only-script` wrapper) | `mvnw.cmd` at download; `requireMavenVersion [3.9.16]` in `pom.xml`; lock cross-check in the script |
| Lifecycle and verification plugins | explicit versions in `pom.xml` `<pluginManagement>` | `requirePluginVersions` (bans LATEST/RELEASE/SNAPSHOT and default bindings) |
| Toolchain JDK | Temurin `25.0.4.1+1`, SHA-256 and `release` strings in `toolchain-lock.json`; OpenJDK `jdk-25.0.4.1-ga` source URL/revision in the same lock; `bs2bg.toolchain.jdk.runtimeVersion` in `pom.xml` | script (hash, `release` file, `java -version`, `os.arch`, pom/lock cross-check); `maven-toolchains-plugin` requires `jdk` `version=25 vendor=temurin`; `Java25ToolchainGuardTest` asserts feature 25, `java.vendor`, and the forwarded `java.runtime.version` inside the forked test JVM; packaging emits the source pin in its component manifest |
| JavaFX (build input) | `org.openjfx:*:25.0.4` from Maven Central via `javafx.version` (`win` classifier) | exact version pin; `-C` transport checksums; resolved jar SHA-256s recorded in `test-classpath-sha256.txt` |
| JavaFX (runtime input) | Gluon `25.0.4` Windows x64 JMODs by SHA-256 in the lock; OpenJFX `25.0.4+3` source URL/revision in the same lock | `jmod describe` for `javafx.base/graphics/controls/fxml`; provisioned for the jlink/jpackage checkpoints (#81), not consumed by this Maven run; packaging emits the source pin in its component manifest |
| Architecture | Windows x64 | `requireOS family=windows arch=amd64`; `PROCESSOR_ARCHITECTURE=AMD64`; JDK `OS_ARCH=x86_64`; test JVM `os.arch=amd64` |
| Target release | `maven.compiler.release=25` | class-file `major=69` read from every production class by the guard test |
| Preview features | disabled (no `--enable-preview` anywhere) | class-file `minor=0`, JVM input args, pom element text, `.mvn/*.config` |
| JavaFX incubator modules | disabled | `bannedDependencies org.openjfx:javafx-incubator-*`; guard test scans the classpath and boot layer; source gate rejects `jfx.incubator` |

The Surefire `argLine` carries exactly `--enable-native-access=ALL-UNNAMED`: JavaFX loads its native libraries
through `System.load`, which JDK 25 warns about and a later release will refuse. It is not a preview flag; the
guard test asserts that `--enable-preview` never reaches the test JVM.

## Maven host JDK vs. toolchain JDK

Maven itself is bootstrapped by whichever JDK `JAVA_HOME` points at; compilation and tests are forked on the
toolchain JDK selected by `maven-toolchains-plugin`. The script defaults the host to the provisioned Temurin 25
so a clean machine needs nothing else. A newer host JDK is accepted explicitly:

```powershell
.\tools\java25\verify-java25.ps1 -MavenJavaHome 'C:\Program Files\Java\jdk-26.0.2'
```

`requireJavaVersion [25,)` rejects an older host. Either way `Java25ToolchainGuardTest` proves the *test* JVM is
the pinned Temurin 25 build, which is the only witness that cannot be faked by a mislabelled `toolchains.xml`
(`<provides><vendor>` is self-declared; Maven never checks it).

Running `mvnw` directly (without the script) fails closed unless `BS2BG_JDK25_HOME` points at a JDK: the
`.mvn/maven.config` file passes `--toolchains=.mvn/toolchains.xml`, whose `jdkHome` does not resolve otherwise.
Only the script verifies that the JDK behind that variable is the pinned Temurin build, asserts the compiler
scope, and checks the packaged artifact, so a direct `mvnw verify` is a developer loop, not the gate.

## Reproducibility evidence

`target/reproducibility/` after a run contains:

- `java25-verification.json` — Maven version/distribution hash/wrapper version, host JDK and its source,
  Temurin release strings and archive hash, JavaFX version/archive hash/verified modules, architecture,
  target release (pinned value, pom value, and the test that witnessed it), preview/incubator status, the
  Surefire totals plus the `java.vendor` / `java.runtime.version` the forked test JVM recorded, the
  `applicationGate` block (`completeApplicationGate=true`, `cleanLifecycle=true`, production source count, compiler arguments, the
  test count of every required suite, and the artifact's class/resource inventory), and the resolved artifact
  hashes. The `dependencyGraph` block records the convergence and retired-codec gates beside the dependency tree
  and classpath hashes. Evidence is only written when Surefire reports zero failures and errors on the pinned runtime and every
  gate assertion passed.
- `dependency-tree.txt` — the resolved dependency graph (shows the JavaFX `win` classifier).
- `test-classpath.txt` / `test-classpath-sha256.txt` — every resolved jar on the test classpath and its SHA-256.

ADR-0003 records the Java 25 baseline and supersedes ADR-0001 wherever it conflicts with it (today, its
"preserving Java 8" clause); the
`--release 25` target here is that decision. The packaging checkpoint that builds and smoke-tests the Windows
app-image on top of this gate is described in [windows-app-image.md](windows-app-image.md); since #97 the jar
plugin writes the artifact into `target/app-image-input/` (beside `lib/`), which is where this script verifies it.

## Updating a pin

Every change is an explicit, reviewed lock edit — never a "latest" download:

1. Temurin: take the new SHA-256 from the Adoptium release asset (`*.zip.sha256.txt`), update `jdk.url`,
   `jdk.sha256`, `jdk.sourceUrl`, `jdk.sourceRevision`, `jdk.archiveRootDirectory`, and the `jdk.release` strings (copy them from the new JDK's `release`
   file), set `bs2bg.toolchain.jdk.runtimeVersion` in `pom.xml` to the new `JAVA_RUNTIME_VERSION` (the script
   fails closed if they differ), and update `Java25ToolchainGuardTest` only if the feature version changes.
2. JavaFX: Gluon publishes no checksum. Download the official archive, record `Get-FileHash -Algorithm SHA256`,
   and update `javafx.url`, `javafx.sha256`, `javafx.sourceUrl`, `javafx.sourceRevision`, `javafx.archiveRootDirectory`, `javafx.version`, and
   `javafx.version` in `pom.xml` together.
3. Maven: update `distributionUrl` and `distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`
   (derive SHA-256 from a download whose SHA-512 matches Apache's published value), `bs2bg.maven.version` in
   `pom.xml`, and `maven.version` / `maven.distributionSha256` in the lock.

Run `Invoke-Pester -Path tools/java25` after editing the lock or the compiler configuration; the tests validate
the lock's shape, the wrapper cross-check, the complete-scope assertion, and the artifact check before any
download happens.
