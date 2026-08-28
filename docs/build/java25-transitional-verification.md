# Transitional Java 25 verification run

Status: transitional checkpoint (issue #94, parent #81). This build is **not** the complete application gate.

## One command

From a clean Windows x64 checkout, with or without any JDK installed:

```powershell
.\tools\java25\verify-java25.ps1
```

The script:

1. Loads `tools/java25/toolchain-lock.json` and cross-checks `.mvn/wrapper/maven-wrapper.properties` against it.
2. Checksum-provisions **Eclipse Temurin 25.0.4.1+1** (Windows x64 JDK) and the **Gluon JavaFX 25.0.4** Windows x64
   JMODs into `%LOCALAPPDATA%\BS2BG\build-toolchains\` (override with `-CacheRoot` or `BS2BG_TOOLCHAIN_CACHE`).
   SHA-256 is verified before extraction; the JDK `release` metadata (vendor, full build number, image type,
   OS, architecture) and each `jmod describe` header are verified after. Nothing is written to a global JDK.
3. Exports `BS2BG_JDK25_HOME` (consumed by `.mvn/toolchains.xml`) and runs the committed Maven Wrapper with
   `--batch-mode --strict-checksums clean verify` plus `dependency:tree` / `dependency:build-classpath`.
4. Writes reproducibility evidence to `target/reproducibility/`.
5. Prints the transitional-scope report listing every production source the build still excludes.

Any unexpected checksum, version string, architecture, or Maven/plugin version fails closed with exit code 1.
The script runs on PowerShell 7 or Windows PowerShell 5.1.

Cache reuse trusts the `.bs2bg-provisioned` marker (which records the archive SHA-256) rather than re-hashing the
extracted tree; the JDK `release` file, `java -version`, `os.arch`, and `jmod describe` checks re-run on every
invocation. Delete the cache directory to force a fresh download and hash verification.

## What is pinned, and where

| Input | Pin | Verified by |
| --- | --- | --- |
| Apache Maven distribution | `3.9.16`, SHA-256 in `.mvn/wrapper/maven-wrapper.properties` (`only-script` wrapper) | `mvnw.cmd` at download; `requireMavenVersion [3.9.16]` in `pom.xml`; lock cross-check in the script |
| Lifecycle and verification plugins | explicit versions in `pom.xml` `<pluginManagement>` | `requirePluginVersions` (bans LATEST/RELEASE/SNAPSHOT and default bindings) |
| Toolchain JDK | Temurin `25.0.4.1+1`, SHA-256 and `release` strings in `toolchain-lock.json`; `bs2bg.toolchain.jdk.runtimeVersion` in `pom.xml` | script (hash, `release` file, `java -version`, `os.arch`, pom/lock cross-check); `maven-toolchains-plugin` requires `jdk` `version=25 vendor=temurin`; `Java25ToolchainGuardTest` asserts feature 25, `java.vendor`, and the forwarded `java.runtime.version` inside the forked test JVM |
| JavaFX (build input) | `org.openjfx:*:25.0.4` from Maven Central via `javafx.version` (`win` classifier) | exact version pin; `-C` transport checksums; resolved jar SHA-256s recorded in `test-classpath-sha256.txt` |
| JavaFX (runtime input) | Gluon `25.0.4` Windows x64 JMODs by SHA-256 in the lock | `jmod describe` for `javafx.base/graphics/controls/fxml`; provisioned for the jlink/jpackage checkpoints (#81), not consumed by this Maven run |
| Architecture | Windows x64 | `requireOS family=windows arch=amd64`; `PROCESSOR_ARCHITECTURE=AMD64`; JDK `OS_ARCH=x86_64`; test JVM `os.arch=amd64` |
| Target release | `maven.compiler.release=25` | class-file `major=69` read by the guard test |
| Preview features | disabled (no `--enable-preview` anywhere) | class-file `minor=0` and JVM input args checked by the guard test |
| JavaFX incubator modules | disabled | tripwires: `bannedDependencies org.openjfx:javafx-incubator-*`; guard test scans the classpath and boot layer (neither can fail until such a dependency is introduced) |

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
Only the script verifies that the JDK behind that variable is the pinned Temurin build.

## Reproducibility evidence

`target/reproducibility/` after a run contains:

- `java25-verification.json` — Maven version/distribution hash/wrapper version, host JDK and its source,
  Temurin release strings and archive hash, JavaFX version/archive hash/verified modules, architecture,
  target release (pinned value, pom value, and the test that witnessed it), preview/incubator status, the
  Surefire totals plus the `java.vendor` / `java.runtime.version` the forked test JVM recorded, the transitional
  source scope, and the resolved artifact hashes. Evidence is only written when Surefire reports zero failures
  and errors on the pinned runtime.
- `dependency-tree.txt` — the resolved dependency graph (shows the JavaFX `win` classifier).
- `test-classpath.txt` / `test-classpath-sha256.txt` — every resolved jar on the test classpath and its SHA-256.

## Transitional scope

`pom.xml` still restricts `maven-compiler-plugin` to `data/**`, `project/**`, and `presentation/**`. The JavaFX
controllers, `controlsfx/table`, `etc/`, and `Main` remain excluded until the JavaFX 25 UI port (#81). The
script's closing report and `transitional.completeApplicationGate=false` in the evidence file state this
explicitly; a green run proves the pinned toolchain and the JavaFX-independent `ProjectSession`, immutable
snapshot, and package-private `Project` contracts only.

ADR-0001's "preserving Java 8" clause predates the modernization; the maintainer's position is that ADR-0002 is
the governing Project-seam decision. Recording that supersession in the ADR files is #81's deliverable and is not
done here.

## Updating a pin

Every change is an explicit, reviewed lock edit — never a "latest" download:

1. Temurin: take the new SHA-256 from the Adoptium release asset (`*.zip.sha256.txt`), update `jdk.url`,
   `jdk.sha256`, `jdk.archiveRootDirectory`, and the `jdk.release` strings (copy them from the new JDK's `release`
   file), set `bs2bg.toolchain.jdk.runtimeVersion` in `pom.xml` to the new `JAVA_RUNTIME_VERSION` (the script
   fails closed if they differ), and update `Java25ToolchainGuardTest` only if the feature version changes.
2. JavaFX: Gluon publishes no checksum. Download the official archive, record `Get-FileHash -Algorithm SHA256`,
   and update `javafx.url`, `javafx.sha256`, `javafx.archiveRootDirectory`, `javafx.version`, and
   `javafx.version` in `pom.xml` together.
3. Maven: update `distributionUrl` and `distributionSha256Sum` in `.mvn/wrapper/maven-wrapper.properties`
   (derive SHA-256 from a download whose SHA-512 matches Apache's published value), `bs2bg.maven.version` in
   `pom.xml`, and `maven.version` / `maven.distributionSha256` in the lock.

Run `Invoke-Pester -Path tools/java25` after editing the lock; the tests validate its shape and the wrapper
cross-check before any download happens.
