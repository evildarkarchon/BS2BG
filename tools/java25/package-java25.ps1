#Requires -Version 5.1
<#
.SYNOPSIS
    Builds, verifies, and smoke-tests the self-contained, non-modular Windows x64 application image (issue #97,
    parent #81) from the complete Java 25 build.

.DESCRIPTION
    On top of the complete application gate (tools/java25/verify-java25.ps1, which this script runs first), the
    packaging checkpoint:

      1. Reads the payload the Maven Wrapper staged (target/app-image-input: the application jar plus lib/ with
         every runtime-scoped dependency; JavaFX is deliberately absent because the runtime supplies it).
      2. Measures the module closure with jdeps against the pinned JavaFX 25 JMODs (extracted to exploded modules,
         since jdeps reads jars and directories but not JMOD archives) and widens it only with documented explicit
         additions that static analysis cannot see.
      3. Links the runtime with jlink from the pinned Temurin 25 jmods and the pinned JavaFX JMODs, and verifies
         the runtime's release metadata and module list.
      4. Assembles the third-party notices from the staged jars' own license metadata and the runtime's legal
         directories.
      5. Runs jpackage --type app-image with --runtime-image, --main-class com.asdasfa.jbs2bg.Launcher, and
         --app-version from the pom, then verifies the image layout, the generated launcher configuration, and
         hashes every file into one image digest; archives the image as BS2BG-<version>-windows-x64.zip.
      6. Extracts that archive to a clean temporary location and runs tools/java25/smoke-app-image.ps1: the
         packaged launcher starts with every host-Java discovery path scrubbed, the representative Project, BoS,
         Templates, and Morphs workflows are driven through Windows UI Automation, and the process must exit with
         code 0 within a bounded timeout.
      7. Writes target/reproducibility/windows-app-image.json (toolchain, runtime, package, workflow, and exit
         evidence) next to the jdeps/jlink/jpackage logs and the smoke diagnostics, and prints the report.

    Every unexpected version, missing entry, classpath drift, launch failure, or non-zero exit fails closed.

.PARAMETER CacheRoot
    Toolchain cache directory; see verify-java25.ps1.

.PARAMETER MavenJavaHome
    JDK that bootstraps Maven; see verify-java25.ps1.

.PARAMETER SkipVerify
    Reuse target/ from an earlier green verify run instead of running the complete gate again. A developer loop
    only: the evidence records that the gate was reused, and the run is not a checkpoint result.

.PARAMETER SkipSmoke
    Build and verify the image without launching it. Not a checkpoint result; the evidence says so.

.EXAMPLE
    .\tools\java25\package-java25.ps1
.EXAMPLE
    .\tools\java25\package-java25.ps1 -SkipVerify -SkipSmoke
#>
[CmdletBinding()]
param(
    [string]$CacheRoot = $(if ($env:BS2BG_TOOLCHAIN_CACHE) { $env:BS2BG_TOOLCHAIN_CACHE } else { Join-Path $env:LOCALAPPDATA 'BS2BG\build-toolchains' }),
    [string]$MavenJavaHome = $env:BS2BG_MAVEN_JAVA_HOME,
    [switch]$SkipVerify,
    [switch]$SkipSmoke
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'Java25Toolchain.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'WindowsAppImage.psm1') -Force

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$targetDir = Join-Path $repoRoot 'target'
$evidenceDir = Join-Path $targetDir 'reproducibility'
$startedAt = [DateTimeOffset]::UtcNow

# Launcher and image identity. The launcher name is also the .exe and .cfg name jpackage generates.
$launcherName = 'BS2BG'
$mainClass = 'com.asdasfa.jbs2bg.Launcher'
$vendor = 'asdasfa'
$description = 'jBS2BG: BodySlide Slider Presets to BodyGen and BodyTypes of Skyrim outputs'

# JVM options baked into the launcher. JavaFX loads glass/prism through System.load from javafx.graphics; JDK 25
# warns on that restricted call unless the module is granted native access, and a later release will refuse it.
# Nothing else is added: no preview flag, no incubator module, no classpath override.
$launcherJavaOptions = @('--enable-native-access=javafx.graphics')

# Modules jdeps cannot measure. Each entry is a reviewed decision with its reason recorded in the evidence.
$explicitModules = [ordered]@{
    # ProjectFileLoader resolves whatever charset juniversalchardet detects through Charset.forName; the extended
    # charsets (Shift_JIS, GB18030, EUC-KR, ...) live in this service-provider module, which no static reference
    # names, so without it a detected legacy encoding would fail with UnsupportedCharsetException at runtime.
    'jdk.charsets' = 'CharsetProvider for extended charsets that Charset.forName resolves at runtime from juniversalchardet detections'
}

# Write-Step and Invoke-Native come from Java25Toolchain.psm1 (shared with verify-java25.ps1).

# Wrapped so a thrown verification error always yields exit code 1, however the script was invoked.
try {

# ---------------------------------------------------------------------------------------------------------------
# 1. Complete application gate (or reuse of a green run) and toolchain inputs
# ---------------------------------------------------------------------------------------------------------------
$lock = Get-ToolchainLock
$gateEvidencePath = Join-Path $evidenceDir 'java25-verification.json'

# Outputs of a previous packaging run are removed up front. jpackage writes the launcher executable read-only,
# which Maven's clean phase (java.io.File.delete) cannot remove on Windows, so the gate would fail on it; and a
# launcher still running from target/app-image would keep its files locked, which is reported instead of killed.
$imageOutputs = @('app-image', 'app-image-runtime', 'app-image-measure', 'app-image-notices') | ForEach-Object { Join-Path $targetDir $_ }
$running = @(Get-Process -Name $launcherName -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($targetDir, [System.StringComparison]::OrdinalIgnoreCase) })
if ($running.Count -gt 0) {
    throw "A launcher from a previous packaging run is still running (pid $(($running | ForEach-Object { $_.Id }) -join ', ')); close it before packaging."
}
foreach ($output in $imageOutputs + @(Get-ChildItem -LiteralPath $targetDir -Filter "$launcherName-*-windows-x64.zip" -File -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })) {
    if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Recurse -Force }
}
if ($SkipVerify) {
    Write-Step 'Reusing the existing verification run (-SkipVerify: developer loop, not a checkpoint result)'
    if (-not (Test-Path -LiteralPath $gateEvidencePath)) {
        throw "No prior gate evidence at $gateEvidencePath; run without -SkipVerify."
    }
    # The cache marker records the archive SHA-256, so a provisioned toolchain is reused without re-downloading.
    $jdkHome = Install-LockedArchive -Url $lock.jdk.url -Sha256 $lock.jdk.sha256 -Destination (Join-Path $CacheRoot $lock.jdk.id) -ExpectedRootDirectory $lock.jdk.archiveRootDirectory -Label 'Temurin JDK'
    $javafxJmods = Install-LockedArchive -Url $lock.javafx.url -Sha256 $lock.javafx.sha256 -Destination (Join-Path $CacheRoot $lock.javafx.id) -ExpectedRootDirectory $lock.javafx.archiveRootDirectory -Label 'JavaFX JMODs'
    $gateSource = 'reused target/reproducibility/java25-verification.json (-SkipVerify)'
}
else {
    Write-Step 'Running the complete application gate (verify-java25.ps1)'
    $verifyArguments = @{ CacheRoot = $CacheRoot }
    if ($MavenJavaHome) { $verifyArguments['MavenJavaHome'] = $MavenJavaHome }
    # verify-java25.ps1 exports BS2BG_JDK25_HOME and BS2BG_JAVAFX_JMODS process-locally for exactly this consumer.
    & (Join-Path $PSScriptRoot 'verify-java25.ps1') @verifyArguments
    if ($LASTEXITCODE -ne 0) {
        throw "verify-java25.ps1 exited with code $LASTEXITCODE; the packaging checkpoint requires a green gate."
    }
    $jdkHome = $env:BS2BG_JDK25_HOME
    $javafxJmods = $env:BS2BG_JAVAFX_JMODS
    $gateSource = 'verify-java25.ps1 (this run)'
}
$gateEvidence = Get-Content -LiteralPath $gateEvidencePath -Raw | ConvertFrom-Json
if (-not $gateEvidence.PSObject.Properties['tests'] -or -not $gateEvidence.tests) {
    throw "The gate evidence at $gateEvidencePath has no test results (it was produced with -SkipMaven); the packaging checkpoint requires a complete run."
}
$release = Read-JdkReleaseFile -Path (Join-Path $jdkHome 'release')
Assert-JdkRelease -Release $release -Expected $lock.jdk.release
Write-Host "Toolchain: $($release['IMPLEMENTOR']) $($release['IMPLEMENTOR_VERSION']) at $jdkHome"
Write-Host "JavaFX JMODs: $($lock.javafx.version) at $javafxJmods"

$appVersion = Get-PomProperty -RepoRoot $repoRoot -Name 'bs2bg.app.version'
$stagingDir = (Get-PomProperty -RepoRoot $repoRoot -Name 'bs2bg.appImage.input').Replace('${project.build.directory}', $targetDir)
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null

$toolVersions = [ordered]@{}
foreach ($tool in @('jdeps', 'jlink', 'jpackage')) {
    $toolVersions[$tool] = (Invoke-Native -FilePath (Join-Path $jdkHome "bin\$tool.exe") -ArgumentList @('--version') -Label "$tool --version") -join ' '
}
Write-Host "Packaging tools: jdeps $($toolVersions['jdeps']), jlink $($toolVersions['jlink']), jpackage $($toolVersions['jpackage'])"

# ---------------------------------------------------------------------------------------------------------------
# 2. Staged payload
# ---------------------------------------------------------------------------------------------------------------
Write-Step 'Reading the payload staged by the Maven Wrapper'
$staged = Get-StagedApplication -StagingDir $stagingDir
$stagedArtifacts = @(@($staged.MainJar) + @($staged.LibJars) | ForEach-Object {
    [pscustomobject]@{
        path   = $_.Substring($staged.StagingDir.Length).TrimStart('\', '/').Replace('\', '/')
        sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()
        size   = (Get-Item -LiteralPath $_).Length
    }
})
Write-Host "Application jar: $($staged.MainJarName); dependencies: $($staged.LibJarNames -join ', ')"

# ---------------------------------------------------------------------------------------------------------------
# 3. Measured module closure
# ---------------------------------------------------------------------------------------------------------------
Write-Step 'Measuring the runtime module closure with jdeps'
$measureDir = Join-Path $targetDir 'app-image-measure'
if (Test-Path -LiteralPath $measureDir) { Remove-Item -LiteralPath $measureDir -Recurse -Force }
$explodedModules = @()
foreach ($module in $lock.javafx.requiredModules) {
    $moduleDir = Join-Path $measureDir $module
    New-Item -ItemType Directory -Path $moduleDir -Force | Out-Null
    Invoke-Native -FilePath (Join-Path $jdkHome 'bin\jmod.exe') -ArgumentList @('extract', '--dir', $moduleDir, (Join-Path $javafxJmods "$module.jmod")) -Label "jmod extract $module" | Out-Null
    $classes = Join-Path $moduleDir 'classes'
    if (-not (Test-Path -LiteralPath (Join-Path $classes 'module-info.class'))) {
        throw "jmod extract of $module produced no module-info.class under $classes"
    }
    $explodedModules += $classes
}
# Deliberately no --ignore-missing-deps: the staged payload is the complete runtime classpath, so every class a
# jar references must resolve against it or the pinned JavaFX modules. With the flag a missing dependency would
# not stop the run, it would silently shrink the measured closure; without it jdeps fails and Invoke-Native
# throws, which is the fail-closed behaviour the checkpoint wants. (Verified: the flag changes nothing today.)
$jdepsArguments = @('--multi-release', "$($lock.targetRelease)", '--print-module-deps',
    '--module-path', ($explodedModules -join ';'),
    '--class-path', (Join-Path $staged.StagingDir 'lib\*')) + @($staged.MainJar) + @($staged.LibJars)
$jdepsOutput = Invoke-Native -FilePath (Join-Path $jdkHome 'bin\jdeps.exe') -ArgumentList $jdepsArguments -Label 'jdeps'
@("jdeps $($jdepsArguments -join ' ')", '') + $jdepsOutput | Set-Content -LiteralPath (Join-Path $evidenceDir 'app-image-jdeps-output.txt') -Encoding utf8
$measured = ConvertFrom-JdepsModuleDeps -Output $jdepsOutput
$resolvedModules = Resolve-RuntimeModules -MeasuredModules $measured -ExplicitModules $explicitModules -PinnedJavaFxModules $lock.javafx.requiredModules
Write-Host "Measured: $($resolvedModules.Measured -join ', ')"
Write-Host "Explicit additions: $($resolvedModules.Explicit.Keys -join ', ')"

# ---------------------------------------------------------------------------------------------------------------
# 4. Runtime image
# ---------------------------------------------------------------------------------------------------------------
Write-Step 'Linking the runtime from the pinned Temurin and JavaFX inputs'
$runtimeDir = Join-Path $targetDir 'app-image-runtime'
if (Test-Path -LiteralPath $runtimeDir) { Remove-Item -LiteralPath $runtimeDir -Recurse -Force }
# The same four options jpackage applies when it links a runtime itself; the runtime is linked here explicitly so
# the module list is the measured one rather than jpackage's "every module on the module path" default.
$jlinkOptions = @('--strip-debug', '--no-header-files', '--no-man-pages', '--strip-native-commands')
$jlinkArguments = @('--module-path', ((Join-Path $jdkHome 'jmods') + ';' + $javafxJmods), '--add-modules', ($resolvedModules.Modules -join ','), '--output', $runtimeDir) + $jlinkOptions
$jlinkOutput = Invoke-Native -FilePath (Join-Path $jdkHome 'bin\jlink.exe') -ArgumentList $jlinkArguments -Label 'jlink'
@("jlink $($jlinkArguments -join ' ')", '') + $jlinkOutput | Set-Content -LiteralPath (Join-Path $evidenceDir 'app-image-jlink-output.txt') -Encoding utf8
$runtimeRelease = Assert-RuntimeRelease -RuntimeDir $runtimeDir -ExpectedJavaVersion $lock.jdk.release.JAVA_VERSION -ExpectedModules $resolvedModules.Modules
if (Test-Path -LiteralPath (Join-Path $runtimeDir 'bin\java.exe')) {
    throw 'The runtime image still contains bin\java.exe; native commands must be stripped so the launcher is the only entry point.'
}
foreach ($module in $lock.javafx.requiredModules) {
    if (-not (Test-Path -LiteralPath (Join-Path $runtimeDir "legal\$module"))) {
        throw "The runtime image has no legal directory for $module (runtime\legal\$module); the JavaFX notices were not linked in."
    }
}
# The closure as the JVM resolves it: root modules, requires edges, and service bindings among the image modules,
# computed by the toolchain JVM against the same pinned inputs with the image's exact module set.
$resolutionArguments = @('--module-path', ($explodedModules -join ';'), '--add-modules', ($runtimeRelease.Modules -join ','), '--limit-modules', ($runtimeRelease.Modules -join ','), '--show-module-resolution', '--version')
$moduleResolution = Invoke-Native -FilePath (Join-Path $jdkHome 'bin\java.exe') -ArgumentList $resolutionArguments -Label 'java --show-module-resolution'
@("java $($resolutionArguments -join ' ')", '') + $moduleResolution | Set-Content -LiteralPath (Join-Path $evidenceDir 'app-image-module-resolution.txt') -Encoding utf8
$serviceBindings = @($moduleResolution | Where-Object { $_ -match ' binds ' })
Write-Host "Runtime: Java $($runtimeRelease.Release['JAVA_VERSION']) with $($runtimeRelease.Modules.Count) modules ($($runtimeRelease.Modules -join ' '))"

# ---------------------------------------------------------------------------------------------------------------
# 5. Notices
# ---------------------------------------------------------------------------------------------------------------
Write-Step 'Assembling third-party notices'
$noticesDir = Join-Path $targetDir 'app-image-notices'
$runtimeComponents = @(
    [pscustomobject]@{ name = "$($release['IMPLEMENTOR']) $($release['IMPLEMENTOR_VERSION']) (Eclipse Temurin JDK, jlink'd runtime)"; version = $release['JAVA_RUNTIME_VERSION']; license = 'GNU General Public License v2.0 with the Classpath Exception'; noticesPath = 'runtime/legal/<module>/ (LICENSE, ASSEMBLY_EXCEPTION, ADDITIONAL_LICENSE_INFO and third-party .md files per JDK module)' },
    [pscustomobject]@{ name = 'OpenJFX (Gluon JavaFX Windows x64 JMODs)'; version = $lock.javafx.version; license = 'GNU General Public License v2.0 with the Classpath Exception'; noticesPath = "runtime/legal/{$($lock.javafx.requiredModules -join ',')}/" }
)
$notices = New-ThirdPartyNotices -StagedApplication $staged -OutputDir $noticesDir -ApplicationName $launcherName -ApplicationVersion $appVersion -RuntimeComponents $runtimeComponents
Write-Host "Notices: $($notices.Path)"

# ---------------------------------------------------------------------------------------------------------------
# 6. jpackage app-image
# ---------------------------------------------------------------------------------------------------------------
Write-Step 'Building the non-modular Windows x64 app-image with jpackage'
$imageParent = Join-Path $targetDir 'app-image'
$imageDir = Join-Path $imageParent $launcherName
if (Test-Path -LiteralPath $imageDir) { Remove-Item -LiteralPath $imageDir -Recurse -Force }
New-Item -ItemType Directory -Path $imageParent -Force | Out-Null
$appContent = @((Join-Path $noticesDir 'THIRD-PARTY-NOTICES.txt'))
if (Test-Path -LiteralPath (Join-Path $noticesDir 'notices')) { $appContent += (Join-Path $noticesDir 'notices') }
$jpackageArguments = @(
    '--type', 'app-image',
    '--name', $launcherName,
    '--app-version', $appVersion,
    '--vendor', $vendor,
    '--description', $description,
    '--input', $staged.StagingDir,
    '--main-jar', $staged.MainJarName,
    '--main-class', $mainClass,
    '--runtime-image', $runtimeDir,
    '--dest', $imageParent,
    '--app-content', ($appContent -join ','),
    '--verbose'
)
foreach ($option in $launcherJavaOptions) { $jpackageArguments += @('--java-options', $option) }
$jpackageOutput = Invoke-Native -FilePath (Join-Path $jdkHome 'bin\jpackage.exe') -ArgumentList $jpackageArguments -Label 'jpackage'
@("jpackage $($jpackageArguments -join ' ')", '') + $jpackageOutput | Set-Content -LiteralPath (Join-Path $evidenceDir 'app-image-jpackage-output.txt') -Encoding utf8

Write-Step 'Verifying the application image'
# The JavaFX toolkit's own native libraries (windowing, Direct3D and software pipelines, fonts) must have been
# linked out of the JMODs into the bundled runtime; without them the launcher would start and then fail to open a window.
$javafxNativeLibraries = @('glass.dll', 'prism_d3d.dll', 'prism_sw.dll', 'prism_common.dll', 'javafx_font.dll') | ForEach-Object { "runtime\bin\javafx\$_" }
$requiredImageFiles = @('THIRD-PARTY-NOTICES.txt') + $javafxNativeLibraries + @($notices.Components | ForEach-Object { $_.extractedFiles } | ForEach-Object { $_.Replace('/', '\') })
$inventory = Assert-AppImageLayout -ImageDir $imageDir -LauncherName $launcherName -MainJarName $staged.MainJarName -LibJarNames $staged.LibJarNames -RequiredFiles $requiredImageFiles
$launcherConfig = Read-LauncherConfig -Path (Join-Path $imageDir "app\$launcherName.cfg")
Assert-LauncherConfig -Config $launcherConfig -MainClass $mainClass -MainJarName $staged.MainJarName -LibJarNames $staged.LibJarNames -AppVersion $appVersion -RequiredJavaOptions $launcherJavaOptions
$jpackageState = Assert-JpackageState -Path (Join-Path $imageDir 'app\.jpackage.xml') -AppVersion $appVersion -LauncherName $launcherName -MainClass $mainClass
$imageRuntime = Assert-RuntimeRelease -RuntimeDir (Join-Path $imageDir 'runtime') -ExpectedJavaVersion $lock.jdk.release.JAVA_VERSION -ExpectedModules $resolvedModules.Modules
$digest = Get-TreeDigest -Root $imageDir
$digest.Files | ForEach-Object { "$($_.sha256)  $($_.path)" } | Set-Content -LiteralPath (Join-Path $evidenceDir 'app-image-sha256.txt') -Encoding ascii
Write-Host "Image: $($inventory.FileCount) files, $([math]::Round($inventory.TotalBytes / 1MB, 1)) MB, digest $($digest.Sha256)"

Write-Step 'Archiving the image'
$archivePath = Join-Path $targetDir "$launcherName-$appVersion-windows-x64.zip"
if (Test-Path -LiteralPath $archivePath) { Remove-Item -LiteralPath $archivePath -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
# includeBaseDirectory=true keeps the generated top-level BS2BG\ directory, per the portable release policy.
[System.IO.Compression.ZipFile]::CreateFromDirectory($imageDir, $archivePath, [System.IO.Compression.CompressionLevel]::Optimal, $true)
$archiveSha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Archive: $archivePath ($([math]::Round((Get-Item -LiteralPath $archivePath).Length / 1MB, 1)) MB, sha256 $archiveSha256)"

# ---------------------------------------------------------------------------------------------------------------
# 7. Packaged smoke run
# ---------------------------------------------------------------------------------------------------------------
$smokeEvidence = $null
$smokeEvidencePath = Join-Path $evidenceDir 'windows-app-image-smoke.json'
if ($SkipSmoke) {
    Write-Step 'Skipping the packaged smoke run (-SkipSmoke: not a checkpoint result)'
}
else {
    Write-Step 'Smoke-testing the packaged launcher from a clean extracted location'
    $smokeArguments = @{
        ArchivePath    = $archivePath
        LauncherName   = $launcherName
        FixtureProject = Join-Path $repoRoot 'test-resources\projects\legacy-project-semantics.jbs2bg'
        EvidencePath   = $smokeEvidencePath
        ExpectedAppVersion = $appVersion
    }
    & (Join-Path $PSScriptRoot 'smoke-app-image.ps1') @smokeArguments
    if ($LASTEXITCODE -ne 0) {
        throw "smoke-app-image.ps1 exited with code $LASTEXITCODE; see $smokeEvidencePath and the diagnostics beside it."
    }
    $smokeEvidence = Get-Content -LiteralPath $smokeEvidencePath -Raw | ConvertFrom-Json
    if (-not $smokeEvidence.PSObject.Properties['passed'] -or -not $smokeEvidence.passed) {
        throw "The packaged smoke run did not pass; see $smokeEvidencePath."
    }
}

# ---------------------------------------------------------------------------------------------------------------
# 8. Evidence
# ---------------------------------------------------------------------------------------------------------------
Write-Step 'Recording app-image evidence'
$evidence = [ordered]@{
    schema            = 'bs2bg.windows-app-image/1'
    recordedAtUtc     = $startedAt.ToString('o')
    gitCommit         = $gateEvidence.gitCommit
    checkpointResult  = (-not $SkipVerify -and -not $SkipSmoke)
    application       = [ordered]@{
        name        = $launcherName
        version     = $appVersion
        mainClass   = $mainClass
        mainJar     = $staged.MainJarName
        description = $description
        vendor      = $vendor
    }
    gate              = [ordered]@{
        source                  = $gateSource
        evidence                = 'target/reproducibility/java25-verification.json'
        completeApplicationGate = $gateEvidence.applicationGate.completeApplicationGate
        tests                   = $gateEvidence.tests.tests
        mavenVersion            = $gateEvidence.maven.version
        mavenVersionOutput      = $gateEvidence.maven.versionOutput
    }
    toolchain         = [ordered]@{
        jdkId              = $lock.jdk.id
        implementor        = $release['IMPLEMENTOR']
        implementorVersion = $release['IMPLEMENTOR_VERSION']
        javaVersion        = $release['JAVA_VERSION']
        javaRuntimeVersion = $release['JAVA_RUNTIME_VERSION']
        jdkArchiveSha256   = $lock.jdk.sha256
        javafxId           = $lock.javafx.id
        javafxVersion      = $lock.javafx.version
        javafxArchiveSha256= $lock.javafx.sha256
        architecture       = [ordered]@{ processorArchitecture = $env:PROCESSOR_ARCHITECTURE; jdkReleaseOsArch = $release['OS_ARCH']; javaOsArch = $lock.architecture.javaOsArch }
        packagingTools     = $toolVersions
    }
    payload           = [ordered]@{
        stagingDir        = 'target/app-image-input'
        artifacts         = $stagedArtifacts
        dependencyTree    = 'target/reproducibility/dependency-tree.txt'
        javafxOnClasspath = $false
    }
    runtime           = [ordered]@{
        measuredModules      = $resolvedModules.Measured
        explicitModules      = $resolvedModules.Explicit
        requestedModules     = $resolvedModules.Modules
        imageModules         = $imageRuntime.Modules
        serviceBindings      = $serviceBindings
        moduleResolutionFile = 'target/reproducibility/app-image-module-resolution.txt'
        jdepsOutputFile      = 'target/reproducibility/app-image-jdeps-output.txt'
        jlinkOptions         = $jlinkOptions
        jlinkOutputFile      = 'target/reproducibility/app-image-jlink-output.txt'
        release              = $imageRuntime.Release
        nativeCommandsStripped = $true
    }
    image             = [ordered]@{
        directory        = 'target/app-image/BS2BG'
        fileCount        = $inventory.FileCount
        totalBytes       = $inventory.TotalBytes
        sha256           = $digest.Sha256
        fileHashesFile   = 'target/reproducibility/app-image-sha256.txt'
        archive          = [System.IO.Path]::GetFileName($archivePath)
        archiveSha256    = $archiveSha256
        launcherConfig   = $launcherConfig
        jpackageState    = [ordered]@{ toolVersion = $jpackageState.ToolVersion; platform = $jpackageState.Platform }
        javaOptions      = $launcherJavaOptions
        notices          = [ordered]@{ file = 'THIRD-PARTY-NOTICES.txt'; components = $notices.Components; runtimeComponents = $runtimeComponents }
        jpackageOutputFile = 'target/reproducibility/app-image-jpackage-output.txt'
    }
    smoke             = $(if ($smokeEvidence) { $smokeEvidence } else { [ordered]@{ skipped = $true; reason = '-SkipSmoke' } })
}
$evidencePath = Join-Path $evidenceDir 'windows-app-image.json'
$evidence | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $evidencePath -Encoding utf8
Write-Host "Evidence written to $evidencePath"

# ---------------------------------------------------------------------------------------------------------------
# 9. Report
# ---------------------------------------------------------------------------------------------------------------
Write-Host ''
if ($SkipVerify -or $SkipSmoke) {
    Write-Host "App-image build completed with -SkipVerify=$($SkipVerify.IsPresent) -SkipSmoke=$($SkipSmoke.IsPresent): this is NOT a checkpoint result." -ForegroundColor Yellow
}
else {
    Write-Host '=====================================================================================' -ForegroundColor Green
    Write-Host 'WINDOWS APP-IMAGE CHECKPOINT: green' -ForegroundColor Green
    Write-Host "  $launcherName $appVersion on $($release['IMPLEMENTOR_VERSION']) + JavaFX $($lock.javafx.version), $($imageRuntime.Modules.Count) runtime modules" -ForegroundColor Green
    Write-Host "  image digest $($digest.Sha256)" -ForegroundColor Green
    Write-Host "  packaged workflows: $(@($smokeEvidence.steps | Where-Object { $_.passed }).Count)/$(@($smokeEvidence.steps).Count) steps passed; exit code $($smokeEvidence.process.exitCode) after $($smokeEvidence.process.exitWaitSeconds) s" -ForegroundColor Green
    Write-Host '=====================================================================================' -ForegroundColor Green
}
exit 0

}
catch {
    Write-Host ''
    Write-Host "PACKAGING FAILED (fail closed): $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ScriptStackTrace) { Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray }
    exit 1
}
