#Requires -Version 5.1
<#
.SYNOPSIS
    One repository-owned command that produces the reproducible Java 25 application verification run
    (issues #94 and #96, parent #81).

.DESCRIPTION
    From a clean Windows x64 checkout with no installed JDK 25, this script:

      1. Loads tools/java25/toolchain-lock.json, cross-checks the committed Maven Wrapper pins against it, and
         asserts that pom.xml compiles every production source with full lint enforcement (no include list,
         no sourcepath override), so a source-filtered build cannot be invoked as the gate.
      2. Checksum-provisions Eclipse Temurin 25 (JDK) and the Gluon JavaFX 25 Windows x64 JMODs into a
         versioned cache, verifying SHA-256 before extraction and the JDK release metadata / jmod versions after.
      3. Exports BS2BG_JDK25_HOME for .mvn/toolchains.xml and runs the committed Maven Wrapper (which verifies
         the pinned Apache Maven distribution itself) so maven-toolchains-plugin forks the provisioned JDK for
         compilation and tests, whichever JDK bootstraps Maven.
      4. Verifies the gate itself: the required Surefire suites (toolchain guard, structural source gate,
         public-JavaFX FXML harness, and the ProjectSession / Project / filtering seam tests) all ran green on the
         pinned runtime, and the built jar contains a class for every production source and every production
         resource.
      5. Writes reproducibility evidence to target/reproducibility/ (Maven, JDK, JavaFX, architecture, target
         release, gate coverage, resolved dependency graph, resolved classpath hashes) and prints the gate
         report.

    Every unexpected version, checksum, or architecture fails closed with a non-zero exit code.

.PARAMETER CacheRoot
    Directory that holds the provisioned toolchains. Defaults to $env:BS2BG_TOOLCHAIN_CACHE, then
    %LOCALAPPDATA%\BS2BG\build-toolchains. Nothing is written to a global JDK installation.

.PARAMETER MavenJavaHome
    JDK that bootstraps Maven itself. Defaults to $env:BS2BG_MAVEN_JAVA_HOME, then the provisioned Temurin 25.
    A newer host JDK (e.g. JDK 26) is accepted here; it never compiles or runs tests because the toolchain owns those.

.PARAMETER MavenGoals
    Lifecycle goals to run. Defaults to 'clean verify'. 'clean' is deliberate: stale class files from a previous
    release target would otherwise survive Maven's incremental compilation and pollute the evidence. Arguments
    that skip, filter, or ignore tests (skipTests, maven.test.skip, maven.test.failure.ignore, -Dtest=) are rejected:
    the gate is the complete run.

.PARAMETER SkipMaven
    Provision and verify the toolchain inputs only; do not run the Maven build.

.EXAMPLE
    .\tools\java25\verify-java25.ps1
.EXAMPLE
    .\tools\java25\verify-java25.ps1 -MavenJavaHome 'C:\Program Files\Java\jdk-26.0.2'
#>
[CmdletBinding()]
param(
    [string]$CacheRoot = $(if ($env:BS2BG_TOOLCHAIN_CACHE) { $env:BS2BG_TOOLCHAIN_CACHE } else { Join-Path $env:LOCALAPPDATA 'BS2BG\build-toolchains' }),
    [string]$MavenJavaHome = $env:BS2BG_MAVEN_JAVA_HOME,
    [string[]]$MavenGoals = @('clean', 'verify'),
    [switch]$SkipMaven
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'Java25Toolchain.psm1') -Force

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$evidenceDir = Join-Path $repoRoot 'target\reproducibility'
$startedAt = [DateTimeOffset]::UtcNow

# Write-Step and Invoke-Native come from Java25Toolchain.psm1 (shared with package-java25.ps1).

# The whole run is wrapped so that a thrown verification error always produces a non-zero exit code, whether the
# script is invoked with `pwsh -File`, dot-sourced, or called with `&` from another script (where an uncaught
# throw would otherwise leave $LASTEXITCODE untouched).
try {

# ---------------------------------------------------------------------------------------------------------------
# 1. Host preflight and lock
# ---------------------------------------------------------------------------------------------------------------
Write-Step 'Checking host and pinned inputs'
$lock = Get-ToolchainLock

$isWindowsHost = ($env:OS -eq 'Windows_NT')
if (-not $isWindowsHost) {
    throw "The Java 25 baseline is pinned to $($lock.architecture.os) x64; this host is not Windows."
}
if ($env:PROCESSOR_ARCHITECTURE -ne $lock.architecture.processorArchitecture) {
    throw "The Java 25 baseline is pinned to $($lock.architecture.processorArchitecture); this host reports PROCESSOR_ARCHITECTURE=$($env:PROCESSOR_ARCHITECTURE)."
}

$wrapperProperties = Read-PropertiesFile -Path (Join-Path $repoRoot '.mvn\wrapper\maven-wrapper.properties')
Assert-MavenWrapperPinned -Properties $wrapperProperties -MavenVersion $lock.maven.version -DistributionSha256 $lock.maven.distributionSha256
Write-Host "Maven Wrapper pins Apache Maven $($lock.maven.version) (sha256 $($lock.maven.distributionSha256))"

# The pom forwards this pin to Java25ToolchainGuardTest; it must be the same full build the lock provisions.
$pomRuntimeVersion = Get-PomProperty -RepoRoot $repoRoot -Name 'bs2bg.toolchain.jdk.runtimeVersion'
if ($pomRuntimeVersion -cne $lock.jdk.release.JAVA_RUNTIME_VERSION) {
    throw "pom.xml bs2bg.toolchain.jdk.runtimeVersion ('$pomRuntimeVersion') differs from the lock's JAVA_RUNTIME_VERSION ('$($lock.jdk.release.JAVA_RUNTIME_VERSION)')."
}
$pomRelease = Get-PomProperty -RepoRoot $repoRoot -Name 'maven.compiler.release'
if ([int]$pomRelease -ne [int]$lock.targetRelease) {
    throw "pom.xml maven.compiler.release ('$pomRelease') differs from the lock's targetRelease ('$($lock.targetRelease)')."
}
Write-Host "pom.xml pins release $pomRelease and toolchain runtime $pomRuntimeVersion (matches the lock)"

# The compiler scope is asserted before any download: an include list or sourcepath override in pom.xml means
# this script would be reporting a partial build, which is exactly what it must never do.
$scope = Assert-CompleteSourceScope -RepoRoot $repoRoot
Write-Host "pom.xml compiles all $($scope.ProductionSources.Count) production sources ($($scope.CompilerArgs -join ' '))"
foreach ($goal in $MavenGoals) {
    if ($goal -match 'skipTests|maven\.test\.skip|maven\.test\.failure\.ignore|testFailureIgnore|^-Dtest=|failIfNoSpecifiedTests') {
        throw "Maven argument '$goal' would skip or filter the test phase; the application gate runs every suite."
    }
}
if (-not $SkipMaven) {
    if ($MavenGoals -cnotcontains 'clean' -or $MavenGoals -cnotcontains 'verify') {
        throw "The complete application gate requires the clean and verify lifecycle goals."
    }
}

# ---------------------------------------------------------------------------------------------------------------
# 2. Provision and verify Temurin 25
# ---------------------------------------------------------------------------------------------------------------
Write-Step "Provisioning $($lock.jdk.release.IMPLEMENTOR_VERSION) (Windows x64)"
$jdkHome = Install-LockedArchive -Url $lock.jdk.url -Sha256 $lock.jdk.sha256 -Destination (Join-Path $CacheRoot $lock.jdk.id) -ExpectedRootDirectory $lock.jdk.archiveRootDirectory -Label 'Temurin JDK'

$release = Read-JdkReleaseFile -Path (Join-Path $jdkHome 'release')
Assert-JdkRelease -Release $release -Expected $lock.jdk.release
foreach ($tool in @('java.exe', 'javac.exe', 'jmod.exe', 'jlink.exe', 'jpackage.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $jdkHome "bin\$tool"))) {
        throw "Provisioned JDK at $jdkHome is missing bin\$tool; the full JDK archive is required."
    }
}
$javaExe = Join-Path $jdkHome 'bin\java.exe'
$javaVersionOutput = Invoke-Native -FilePath $javaExe -ArgumentList @('-version') -Label 'java -version'
if (-not ($javaVersionOutput -join "`n").Contains($lock.jdk.release.IMPLEMENTOR_VERSION)) {
    throw "java -version does not report $($lock.jdk.release.IMPLEMENTOR_VERSION):`n$($javaVersionOutput -join "`n")"
}
$javacVersionOutput = Invoke-Native -FilePath (Join-Path $jdkHome 'bin\javac.exe') -ArgumentList @('-version') -Label 'javac -version'
$javaSettings = Invoke-Native -FilePath $javaExe -ArgumentList @('-XshowSettings:properties', '-version') -Label 'java -XshowSettings'
$osArchLine = $javaSettings | Where-Object { $_ -match '^\s*os\.arch\s*=\s*(\S+)' } | Select-Object -First 1
if (-not $osArchLine -or $Matches[1] -ne $lock.architecture.javaOsArch) {
    throw "Provisioned JDK reports os.arch '$($(if ($osArchLine) { $Matches[1] } else { '<missing>' }))'; expected $($lock.architecture.javaOsArch)."
}
Write-Host "JDK verified: $($release['IMPLEMENTOR']) $($release['IMPLEMENTOR_VERSION']) os.arch=$($lock.architecture.javaOsArch) at $jdkHome"

# ---------------------------------------------------------------------------------------------------------------
# 3. Provision and verify JavaFX 25 JMODs
# ---------------------------------------------------------------------------------------------------------------
Write-Step "Provisioning JavaFX $($lock.javafx.version) JMODs (Windows x64)"
$javafxJmods = Install-LockedArchive -Url $lock.javafx.url -Sha256 $lock.javafx.sha256 -Destination (Join-Path $CacheRoot $lock.javafx.id) -ExpectedRootDirectory $lock.javafx.archiveRootDirectory -Label 'JavaFX JMODs'
$jmodExe = Join-Path $jdkHome 'bin\jmod.exe'
foreach ($module in $lock.javafx.requiredModules) {
    $jmodPath = Join-Path $javafxJmods "$module.jmod"
    if (-not (Test-Path -LiteralPath $jmodPath)) {
        throw "JavaFX JMOD archive is missing $module.jmod at $jmodPath."
    }
    $describe = Invoke-Native -FilePath $jmodExe -ArgumentList @('describe', $jmodPath) -Label "jmod describe $module"
    Assert-JmodDescribeOutput -Output $describe -ModuleName $module -Version $lock.javafx.version
}
Write-Host "JavaFX verified: $($lock.javafx.requiredModules -join ', ') @ $($lock.javafx.version) at $javafxJmods"

# ---------------------------------------------------------------------------------------------------------------
# 4. Maven host JDK selection
# ---------------------------------------------------------------------------------------------------------------
Write-Step 'Selecting the Maven host JDK'
if ([string]::IsNullOrWhiteSpace($MavenJavaHome)) {
    $MavenJavaHome = $jdkHome
    $mavenHostSource = 'provisioned toolchain'
}
else {
    $MavenJavaHome = (Resolve-Path -LiteralPath $MavenJavaHome).Path
    $mavenHostSource = 'explicit -MavenJavaHome / BS2BG_MAVEN_JAVA_HOME'
}
if (-not (Test-Path -LiteralPath (Join-Path $MavenJavaHome 'bin\java.exe'))) {
    throw "Maven host JDK '$MavenJavaHome' has no bin\java.exe."
}
$mavenHostVersion = Invoke-Native -FilePath (Join-Path $MavenJavaHome 'bin\java.exe') -ArgumentList @('-version') -Label 'host java -version'
Write-Host "Maven bootstraps on: $MavenJavaHome ($mavenHostSource)"
Write-Host "Toolchain for compile/test: $jdkHome"

# Process-local only: nothing here changes the user's environment or any global JDK installation.
$env:JAVA_HOME = $MavenJavaHome
$env:BS2BG_JDK25_HOME = $jdkHome
# Not consumed by this Maven run (it resolves JavaFX jars from Maven Central); exported for the jlink/jpackage
# checkpoints that follow (#81), which need the verified JMOD directory on the module path.
$env:BS2BG_JAVAFX_JMODS = $javafxJmods

$mavenVersionOutput = @()
$mavenExitCode = $null
$classpathHashes = @()
$surefire = $null
$requiredSuiteCounts = $null
$artifact = $null

# Suites whose presence and green result the gate requires, beyond the Surefire totals: the toolchain witness,
# the structural source/pom gate, the FXML/controller harness, the packaged-launcher and app-image staging
# contracts (#97), the public-JavaFX table adapter, and the ProjectSession, Project, filtering-seam, and
# persistence-compatibility contracts.
$requiredSuites = @(
    'com.asdasfa.jbs2bg.build.Java25ToolchainGuardTest',
    'com.asdasfa.jbs2bg.build.ProductionSourceGateTest',
    'com.asdasfa.jbs2bg.build.FxmlGraphLoadingTest',
    'com.asdasfa.jbs2bg.build.LauncherTest',
    'com.asdasfa.jbs2bg.build.WindowsAppImageGateTest',
    'com.asdasfa.jbs2bg.fx.FilteredTableAdapterTest',
    'com.asdasfa.jbs2bg.fx.DialogGraphicsTest',
    'com.asdasfa.jbs2bg.filtering.FilteredViewTest',
    'com.asdasfa.jbs2bg.filtering.VisibleScopeCommandsTest',
    'com.asdasfa.jbs2bg.project.ProjectSessionTest',
    'com.asdasfa.jbs2bg.project.ProjectSessionOpenTest',
    'com.asdasfa.jbs2bg.project.ProjectSessionSaveTest',
    'com.asdasfa.jbs2bg.project.ProjectSessionImportTest',
    'com.asdasfa.jbs2bg.project.ProjectSessionSliderChoiceTest',
    'com.asdasfa.jbs2bg.project.ProjectJacksonCompatibilityTest',
    'com.asdasfa.jbs2bg.project.ProjectTest',
    'com.asdasfa.jbs2bg.data.ProjectPersistenceCompatibilityTest',
    'com.asdasfa.jbs2bg.json.JacksonJsonTest',
    'com.asdasfa.jbs2bg.json.JacksonDependencyPolicyTest',
    'com.asdasfa.jbs2bg.data.SettingsJacksonAdapterTest',
    'com.asdasfa.jbs2bg.data.SettingsPairPublisherTest',
    'com.asdasfa.jbs2bg.presentation.ProjectOutputFormatterTest',
    'com.asdasfa.jbs2bg.presentation.BosJacksonWriterTest',
    'com.asdasfa.jbs2bg.presentation.BosArtifactPublisherTest'
)

if (-not $SkipMaven) {
    # -----------------------------------------------------------------------------------------------------------
    # 5. Reproducible Maven run through the wrapper
    # -----------------------------------------------------------------------------------------------------------
    Write-Step "Running the Maven Wrapper: $($MavenGoals -join ' ')"
    Push-Location $repoRoot
    try {
        $mvnw = Join-Path $repoRoot 'mvnw.cmd'
        $mavenVersionOutput = Invoke-Native -FilePath $mvnw -ArgumentList @('--version') -Label 'mvnw --version'
        $mavenVersionLine = $mavenVersionOutput | Where-Object { $_ -match '^Apache Maven (\S+)' } | Select-Object -First 1
        if (-not $mavenVersionLine -or $Matches[1] -ne $lock.maven.version) {
            throw "mvnw resolved Apache Maven '$($(if ($mavenVersionLine) { $Matches[1] } else { '<unknown>' }))'; expected $($lock.maven.version)."
        }

        New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
        $mavenArguments = @(
            '--batch-mode',
            '--strict-checksums',
            '--show-version',
            '-Dstyle.color=never'
        ) + $MavenGoals + @(
            'dependency:tree',
            "-DoutputFile=$evidenceDir\dependency-tree.txt",
            'dependency:build-classpath',
            "-Dmdep.outputFile=$evidenceDir\test-classpath.txt"
        )
        Write-Host "mvnw.cmd $($mavenArguments -join ' ')"
        & $mvnw @mavenArguments
        $mavenExitCode = $LASTEXITCODE
        if ($mavenExitCode -ne 0) {
            throw "Maven Wrapper exited with code $mavenExitCode; see the build log above."
        }
    }
    finally {
        Pop-Location
    }

    # -----------------------------------------------------------------------------------------------------------
    # 6. Reproducibility evidence
    # -----------------------------------------------------------------------------------------------------------
    Write-Step 'Recording reproducibility evidence'
    # Observed from inside the forked test JVM (Surefire writes java.vendor / java.runtime.version into each report),
    # as opposed to the lock constants recorded below.
    $surefire = Get-SurefireSummary -RepoRoot $repoRoot
    if ($surefire.failures -ne 0 -or $surefire.errors -ne 0) {
        throw "Surefire reports record $($surefire.failures) failure(s) and $($surefire.errors) error(s); refusing to write green evidence."
    }
    if (@($surefire.observedJavaRuntimeVersion) -ne @($lock.jdk.release.JAVA_RUNTIME_VERSION)) {
        throw "Surefire reports were produced by runtime '$($surefire.observedJavaRuntimeVersion -join ', ')', expected '$($lock.jdk.release.JAVA_RUNTIME_VERSION)'."
    }
    Write-Host "Surefire: $($surefire.tests) tests in $($surefire.suites) suites on $($surefire.observedJavaVendor -join ', ') $($surefire.observedJavaRuntimeVersion -join ', ')"
    $requiredSuiteCounts = Assert-RequiredSurefireSuites -RepoRoot $repoRoot -Suites $requiredSuites
    Write-Host "Required gate suites present and green: $($requiredSuites.Count)"

    # The artifact is the thing that ships; verify the resources on it, not only on target/classes. Since #97 the
    # jar plugin writes it into the app-image staging directory (pom property bs2bg.appImage.input) beside lib/,
    # which is exactly the tree jpackage consumes, so the check runs on the bytes that get packaged.
    $stagingDir = (Get-PomProperty -RepoRoot $repoRoot -Name 'bs2bg.appImage.input').Replace('${project.build.directory}', (Join-Path $repoRoot 'target'))
    if (-not (Test-Path -LiteralPath $stagingDir)) {
        throw "App-image staging directory $stagingDir does not exist; the gate requires the package phase (run 'clean verify')."
    }
    $jars = @(Get-ChildItem -LiteralPath $stagingDir -File -Filter '*.jar' | Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' })
    if ($jars.Count -ne 1) {
        throw "Expected exactly one build artifact under $stagingDir, found $($jars.Count): $(($jars | ForEach-Object { $_.Name }) -join ', '). The gate requires the package phase (run 'clean verify')."
    }
    $artifact = Assert-JarContainsProductionResources -RepoRoot $repoRoot -JarPath $jars[0].FullName
    Write-Host "Artifact $($jars[0].Name): $($artifact.ClassCount) production classes and $($artifact.ResourceCount) resources present"

    $classpathFile = Join-Path $evidenceDir 'test-classpath.txt'
    if (Test-Path -LiteralPath $classpathFile) {
        $entries = (Get-Content -LiteralPath $classpathFile -Raw).Trim() -split ';' | Where-Object { $_ }
        $classpathHashes = @($entries | ForEach-Object {
            [pscustomobject]@{
                artifact = [System.IO.Path]::GetFileName($_)
                sha256   = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()
            }
        })
        $classpathHashes | ForEach-Object { "$($_.sha256)  $($_.artifact)" } |
            Set-Content -LiteralPath (Join-Path $evidenceDir 'test-classpath-sha256.txt') -Encoding ascii
    }
}
else {
    Write-Step 'Skipping the Maven run (-SkipMaven)'
    New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
}

$evidence = [ordered]@{
    schema                  = 'bs2bg.java25-verification/1'
    recordedAtUtc           = $startedAt.ToString('o')
    # git may be absent from PATH or the checkout may be an exported tree; the commit is informational, so either
    # case records null rather than failing a run whose build evidence is otherwise complete.
    gitCommit               = $(try { (& git -C $repoRoot rev-parse HEAD 2>$null) } catch { $null })
    applicationGate         = [ordered]@{
        completeApplicationGate = $scope.CompleteApplicationGate
        cleanLifecycle          = $(if ($SkipMaven) { $false } else { $true })
        productionSourceCount   = $scope.ProductionSources.Count
        compilerArgs            = $scope.CompilerArgs
        requiredSuites          = $requiredSuiteCounts
        artifact                = $(if ($artifact) {
            [ordered]@{
                jar           = [System.IO.Path]::GetFileName($artifact.Jar)
                classCount    = $artifact.ClassCount
                resourceCount = $artifact.ResourceCount
                resources     = $artifact.Resources
            }
        } else { $null })
        structuralGate          = 'ProductionSourceGateTest (sources, resources, pom) and Java25ToolchainGuardTest (every emitted class file)'
        fxmlHarness             = 'FxmlGraphLoadingTest (root, popup, and custom-root FXML/controller graphs on the pinned toolkit)'
    }
    targetRelease           = [ordered]@{
        pinned      = $lock.targetRelease
        pomProperty = $pomRelease
        witnessedBy = 'Java25ToolchainGuardTest.everyProductionClassIsCompiledForRelease25WithoutPreview (every class file: major 69, minor 0)'
    }
    previewFeaturesEnabled  = $false
    javafxIncubatorModules  = 'disabled (enforcer bans org.openjfx:javafx-incubator-*; guard test scans the classpath and boot layer)'
    tests                   = $(if ($surefire) {
        [ordered]@{
            suites                     = $surefire.suites
            tests                      = $surefire.tests
            failures                   = $surefire.failures
            errors                     = $surefire.errors
            skipped                    = $surefire.skipped
            observedJavaVendor         = $surefire.observedJavaVendor
            observedJavaRuntimeVersion = $surefire.observedJavaRuntimeVersion
            reports                    = 'target/surefire-reports/'
        }
    } else { $null })
    architecture            = [ordered]@{
        processorArchitecture = $env:PROCESSOR_ARCHITECTURE
        javaOsArch            = $lock.architecture.javaOsArch
        jdkReleaseOsArch      = $release['OS_ARCH']
    }
    maven                   = [ordered]@{
        version            = $lock.maven.version
        distributionSha256 = $lock.maven.distributionSha256
        wrapperVersion     = $wrapperProperties['wrapperVersion']
        versionOutput      = $mavenVersionOutput
        goals              = $(if ($SkipMaven) { @() } else { $MavenGoals })
        exitCode           = $mavenExitCode
        hostJavaHome       = $MavenJavaHome
        hostJavaHomeSource = $mavenHostSource
        hostJavaVersion    = $mavenHostVersion
    }
    jdk                     = [ordered]@{
        id                 = $lock.jdk.id
        home               = $jdkHome
        archiveSha256      = $lock.jdk.sha256
        implementor        = $release['IMPLEMENTOR']
        implementorVersion = $release['IMPLEMENTOR_VERSION']
        javaVersion        = $release['JAVA_VERSION']
        javaRuntimeVersion = $release['JAVA_RUNTIME_VERSION']
        javaVersionOutput  = $javaVersionOutput
        javacVersionOutput = $javacVersionOutput
    }
    javafx                  = [ordered]@{
        id              = $lock.javafx.id
        version         = $lock.javafx.version
        jmods           = $javafxJmods
        archiveSha256   = $lock.javafx.sha256
        verifiedModules = $lock.javafx.requiredModules
        mavenArtifacts  = 'org.openjfx:javafx-controls / javafx-fxml (see dependency-tree.txt for the resolved win classifier)'
    }
    dependencyGraph         = [ordered]@{
        convergence             = 'Maven Enforcer dependencyConvergence passed before compilation'
        retiredCodecBan         = 'Maven Enforcer bans com.eclipsesource.minimal-json:* transitively'
        dependencyTreeFile      = $(if ($SkipMaven) { $null } else { 'target/reproducibility/dependency-tree.txt' })
        testClasspathFile       = $(if ($SkipMaven) { $null } else { 'target/reproducibility/test-classpath.txt' })
        testClasspathSha256File = $(if ($SkipMaven) { $null } else { 'target/reproducibility/test-classpath-sha256.txt' })
        resolvedArtifacts       = $classpathHashes
    }
}
$evidencePath = Join-Path $evidenceDir 'java25-verification.json'
$evidence | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $evidencePath -Encoding utf8
Write-Host "Evidence written to $evidencePath"

# ---------------------------------------------------------------------------------------------------------------
# 7. Gate report
# ---------------------------------------------------------------------------------------------------------------
Write-Host ''
if ($SkipMaven) {
    Write-Host "Toolchain provisioning completed; the Maven run was skipped (-SkipMaven), so this is NOT a gate result." -ForegroundColor Yellow
}
else {
    Write-Host '=====================================================================================' -ForegroundColor Green
    Write-Host 'COMPLETE APPLICATION GATE: green' -ForegroundColor Green
    Write-Host "  compiled $($scope.ProductionSources.Count) production sources for release $pomRelease ($($scope.CompilerArgs -join ' '))" -ForegroundColor Green
    Write-Host "  packaged $($artifact.ClassCount) classes and $($artifact.ResourceCount) resources into $([System.IO.Path]::GetFileName($artifact.Jar))" -ForegroundColor Green
    Write-Host "  ran $($surefire.tests) tests in $($surefire.suites) suites on $($surefire.observedJavaVendor -join ', ') $($surefire.observedJavaRuntimeVersion -join ', '), including the $($requiredSuites.Count) required gate suites" -ForegroundColor Green
    Write-Host '=====================================================================================' -ForegroundColor Green
    Write-Host ''
    Write-Host 'Java 25 application verification run completed successfully.' -ForegroundColor Green
}
exit 0

}
catch {
    Write-Host ''
    Write-Host "VERIFICATION FAILED (fail closed): $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ScriptStackTrace) { Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray }
    exit 1
}
