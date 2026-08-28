#Requires -Version 5.1
<#
.SYNOPSIS
    One repository-owned command that produces the reproducible, transitional Java 25 verification run (issue #94).

.DESCRIPTION
    From a clean Windows x64 checkout with no installed JDK 25, this script:

      1. Loads tools/java25/toolchain-lock.json and cross-checks the committed Maven Wrapper pins against it.
      2. Checksum-provisions Eclipse Temurin 25 (JDK) and the Gluon JavaFX 25 Windows x64 JMODs into a
         versioned cache, verifying SHA-256 before extraction and the JDK release metadata / jmod versions after.
      3. Exports BS2BG_JDK25_HOME for .mvn/toolchains.xml and runs the committed Maven Wrapper (which verifies
         the pinned Apache Maven distribution itself) so maven-toolchains-plugin forks the provisioned JDK for
         compilation and tests, whichever JDK bootstraps Maven.
      4. Writes reproducibility evidence to target/reproducibility/ (Maven, JDK, JavaFX, architecture, target
         release, resolved dependency graph, resolved classpath hashes).
      5. Prints a transitional-scope report: the build still excludes the JavaFX UI sources, so a green run is
         NOT the complete application gate.

    Every unexpected version, checksum, or architecture fails closed with a non-zero exit code.

.PARAMETER CacheRoot
    Directory that holds the provisioned toolchains. Defaults to $env:BS2BG_TOOLCHAIN_CACHE, then
    %LOCALAPPDATA%\BS2BG\build-toolchains. Nothing is written to a global JDK installation.

.PARAMETER MavenJavaHome
    JDK that bootstraps Maven itself. Defaults to $env:BS2BG_MAVEN_JAVA_HOME, then the provisioned Temurin 25.
    A newer host JDK (e.g. JDK 26) is accepted here; it never compiles or runs tests because the toolchain owns those.

.PARAMETER MavenGoals
    Lifecycle goals to run. Defaults to 'clean verify'. 'clean' is deliberate: stale class files from a previous
    release target would otherwise survive Maven's incremental compilation and pollute the evidence.

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

function Write-Step {
    param([string]$Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

<#
.SYNOPSIS
    Runs a native executable, returning its combined stdout/stderr lines; throws on a non-zero exit code.
#>
function Invoke-Native {
    param(
        [Parameter(Mandatory)] [string]$FilePath,
        [string[]]$ArgumentList = @(),
        [string]$Label = $FilePath
    )
    # java -version writes to stderr. Under $ErrorActionPreference = 'Stop', Windows PowerShell 5.1 turns redirected
    # native stderr into a terminating NativeCommandError, so the preference is relaxed for the call only and the
    # exit code decides success instead.
    $ErrorActionPreference = 'Continue'
    $output = & $FilePath @ArgumentList 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) {
        throw "$Label exited with code $LASTEXITCODE`n$($output -join "`n")"
    }
    return @($output)
}

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
    throw "The transitional Java 25 checkpoint is pinned to $($lock.architecture.os) x64; this host is not Windows."
}
if ($env:PROCESSOR_ARCHITECTURE -ne $lock.architecture.processorArchitecture) {
    throw "The transitional Java 25 checkpoint is pinned to $($lock.architecture.processorArchitecture); this host reports PROCESSOR_ARCHITECTURE=$($env:PROCESSOR_ARCHITECTURE)."
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
# Not consumed by this transitional Maven run (it resolves JavaFX jars from Maven Central); exported for the
# jlink/jpackage checkpoints that follow (#81), which need the verified JMOD directory on the module path.
$env:BS2BG_JAVAFX_JMODS = $javafxJmods

$scope = Get-TransitionalSourceScope -RepoRoot $repoRoot
$mavenVersionOutput = @()
$mavenExitCode = $null
$classpathHashes = @()
$surefire = $null

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
    transitional            = [ordered]@{
        completeApplicationGate = $scope.CompleteApplicationGate
        admittedPatterns        = $scope.AdmittedPatterns
        admittedSourceCount     = $scope.AdmittedSources.Count
        excludedSources         = $scope.ExcludedSources
    }
    targetRelease           = [ordered]@{
        pinned      = $lock.targetRelease
        pomProperty = $pomRelease
        witnessedBy = 'Java25ToolchainGuardTest.admittedSourcesAreCompiledForRelease25WithoutPreview (class-file major 69, minor 0)'
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
# 7. Transitional scope report
# ---------------------------------------------------------------------------------------------------------------
Write-Host ''
$reportColor = $(if ($scope.CompleteApplicationGate) { 'Green' } else { 'Yellow' })
Format-TransitionalReport -Scope $scope | ForEach-Object { Write-Host $_ -ForegroundColor $reportColor }
Write-Host ''
Write-Host "Java 25 transitional verification $($(if ($SkipMaven) { 'provisioning' } else { 'run' })) completed successfully." -ForegroundColor Green
exit 0

}
catch {
    Write-Host ''
    Write-Host "VERIFICATION FAILED (fail closed): $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ScriptStackTrace) { Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray }
    exit 1
}
