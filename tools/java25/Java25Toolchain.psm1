#Requires -Version 5.1
<#
.SYNOPSIS
    Deterministic helpers for the Java 25 application verification run (issues #94 and #96).

.DESCRIPTION
    Every function here either reads a pinned value, verifies an input against it, or verifies that the build
    definition and artifact cover the complete application. Network access is confined to Install-LockedArchive so that everything that decides whether an
    input is accepted can be unit tested with Pester (see Java25Toolchain.Tests.ps1). All verification functions
    fail closed: a mismatch throws with the expected and actual values instead of warning.
#>

Set-StrictMode -Version Latest

<#
.SYNOPSIS
    Loads and validates tools/java25/toolchain-lock.json.
.PARAMETER Path
    Lock file path. Defaults to the lock next to this module.
.OUTPUTS
    PSCustomObject with targetRelease, architecture, maven, jdk, and javafx sections.
.NOTES
    Throws when the lock cannot be read or a required section, SHA-256 pin, HTTPS source URL, or 40-hex source
    revision is missing or malformed, so a half-edited lock cannot silently skip a verification step.
#>
function Get-ToolchainLock {
    [CmdletBinding()]
    param(
        [string]$Path = (Join-Path $PSScriptRoot 'toolchain-lock.json')
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Toolchain lock not found: $Path"
    }
    $lock = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json

    foreach ($section in @('targetRelease', 'architecture', 'maven', 'jdk', 'javafx')) {
        if (-not ($lock.PSObject.Properties.Name -contains $section)) {
            throw "Toolchain lock '$Path' is missing the required '$section' section."
        }
    }
    foreach ($hashPath in @('maven.distributionSha256', 'jdk.sha256', 'javafx.sha256')) {
        $section, $property = $hashPath.Split('.')
        $value = $lock.$section.$property
        if (-not ($value -is [string]) -or $value -notmatch '^[0-9a-fA-F]{64}$') {
            throw "Toolchain lock '$Path': '$hashPath' must be a 64-hex SHA-256; found '$value'."
        }
    }
    foreach ($required in @(
            'jdk.url', 'jdk.sourceUrl', 'jdk.sourceRevision', 'jdk.archiveRootDirectory', 'jdk.release',
            'javafx.url', 'javafx.sourceUrl', 'javafx.sourceRevision', 'javafx.archiveRootDirectory',
            'javafx.version', 'javafx.requiredModules', 'maven.version')) {
        $section, $property = $required.Split('.')
        if (-not ($lock.$section.PSObject.Properties.Name -contains $property)) {
            throw "Toolchain lock '$Path' is missing '$required'."
        }
    }
    foreach ($sourceUrlPath in @('jdk.sourceUrl', 'javafx.sourceUrl')) {
        $section, $property = $sourceUrlPath.Split('.')
        $value = $lock.$section.$property
        if (-not ($value -is [string]) -or $value -notmatch '^https://') {
            throw "Toolchain lock '$Path': '$sourceUrlPath' must be an exact HTTPS source URL; found '$value'."
        }
    }
    foreach ($revisionPath in @('jdk.sourceRevision', 'javafx.sourceRevision')) {
        $section, $property = $revisionPath.Split('.')
        $value = $lock.$section.$property
        if (-not ($value -is [string]) -or $value -notmatch '^[0-9a-fA-F]{40}$') {
            throw "Toolchain lock '$Path': '$revisionPath' must be a 40-hex Git revision; found '$value'."
        }
    }
    return $lock
}

<#
.SYNOPSIS
    Verifies a file's SHA-256 against a pinned value and returns the actual lowercase hash.
.PARAMETER Label
    Human-readable input name used in the failure message (e.g. 'Temurin JDK').
.NOTES
    Comparison is case-insensitive; the error message includes both hashes so a mismatch can be reviewed
    against the vendor's published checksum without re-running.
#>
function Assert-FileSha256 {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Path,
        [Parameter(Mandatory)] [string]$ExpectedSha256,
        [string]$Label = $Path
    )

    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    $expected = $ExpectedSha256.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "SHA-256 mismatch for $Label ($Path).`n  expected: $expected`n  actual:   $actual`nRefusing to use an input that differs from tools/java25/toolchain-lock.json."
    }
    return $actual
}

<#
.SYNOPSIS
    Parses a JDK 'release' file (KEY="value" lines) into a hashtable.
#>
function Read-JdkReleaseFile {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$Path)

    $release = @{}
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        if ($line -match '^\s*([A-Z_0-9]+)\s*=\s*"(.*)"\s*$') {
            $release[$Matches[1]] = $Matches[2]
        }
    }
    return $release
}

<#
.SYNOPSIS
    Asserts that every pinned key in the lock's jdk.release section matches the provisioned JDK's release file.
.PARAMETER Release
    Hashtable from Read-JdkReleaseFile.
.PARAMETER Expected
    The lock's jdk.release object (vendor, full build number, architecture, OS).
.NOTES
    All mismatches are collected into a single error so a wrong download is diagnosed in one pass.
#>
function Assert-JdkRelease {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [hashtable]$Release,
        [Parameter(Mandatory)] $Expected
    )

    $problems = @()
    foreach ($property in $Expected.PSObject.Properties) {
        $key = $property.Name
        if (-not $Release.ContainsKey($key)) {
            $problems += "  ${key}: expected '$($property.Value)' but the release file has no such key"
        }
        elseif ($Release[$key] -cne $property.Value) {
            $problems += "  ${key}: expected '$($property.Value)' but found '$($Release[$key])'"
        }
    }
    if ($problems.Count -gt 0) {
        throw "Provisioned JDK does not match the pinned Temurin release:`n$($problems -join "`n")"
    }
}

<#
.SYNOPSIS
    Asserts that 'jmod describe' output begins with the pinned module@version header.
#>
function Assert-JmodDescribeOutput {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [AllowEmptyCollection()] [AllowEmptyString()] [string[]]$Output,
        [Parameter(Mandatory)] [string]$ModuleName,
        [Parameter(Mandatory)] [string]$Version
    )

    $expectedHeader = "$ModuleName@$Version"
    $header = ($Output | Where-Object { $_ -and $_.Trim() } | Select-Object -First 1)
    if (-not $header) {
        throw "jmod describe produced no output for $ModuleName; expected header '$expectedHeader'."
    }
    if ($header.Trim() -cne $expectedHeader) {
        throw "JavaFX module mismatch: expected '$expectedHeader' but jmod describe reported '$($header.Trim())'."
    }
}

<#
.SYNOPSIS
    Reads a Java-style properties file (key=value, '#' comments) into a hashtable.
#>
function Read-PropertiesFile {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$Path)

    $properties = @{}
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or $trimmed.StartsWith('!')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        $properties[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
    }
    return $properties
}

<#
.SYNOPSIS
    Asserts that .mvn/wrapper/maven-wrapper.properties pins the same Maven distribution as the lock.
.NOTES
    The wrapper verifies the distribution itself at download time; this cross-check guarantees the wrapper and
    the lock cannot drift apart, and that the checksum-verifying only-script distribution type is in use.
#>
function Assert-MavenWrapperPinned {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [hashtable]$Properties,
        [Parameter(Mandatory)] [string]$MavenVersion,
        [Parameter(Mandatory)] [string]$DistributionSha256
    )

    if ($Properties['distributionType'] -ne 'only-script') {
        throw "maven-wrapper.properties must use distributionType=only-script (found '$($Properties['distributionType'])')."
    }
    $url = [string]$Properties['distributionUrl']
    if ($url -notmatch "/apache-maven/$([regex]::Escape($MavenVersion))/apache-maven-$([regex]::Escape($MavenVersion))-bin\.zip$") {
        throw "maven-wrapper.properties distributionUrl does not pin Apache Maven $MavenVersion : '$url'."
    }
    if (-not $Properties.ContainsKey('distributionSha256Sum') -or -not $Properties['distributionSha256Sum']) {
        throw 'maven-wrapper.properties must set distributionSha256Sum so mvnw verifies the Maven distribution.'
    }
    if ($Properties['distributionSha256Sum'].ToLowerInvariant() -ne $DistributionSha256.ToLowerInvariant()) {
        throw "maven-wrapper.properties distributionSha256Sum ('$($Properties['distributionSha256Sum'])') differs from the lock ('$DistributionSha256')."
    }
}

<#
.SYNOPSIS
    Returns $true when a destination already holds an extraction of the archive with the given checksum.
.NOTES
    The marker records the archive SHA-256 rather than a version string, so bumping the lock invalidates the
    cache even if the vendor re-publishes an archive under the same name.
#>
function Test-ProvisionedArchive {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Destination,
        [Parameter(Mandatory)] [string]$Sha256
    )

    $marker = Join-Path $Destination '.bs2bg-provisioned'
    if (-not (Test-Path -LiteralPath $marker)) { return $false }
    $recorded = (Get-Content -LiteralPath $marker -Raw).Trim().ToLowerInvariant()
    return $recorded -eq $Sha256.ToLowerInvariant()
}

<#
.SYNOPSIS
    Extracts a verified archive whose single root directory is known, into Destination, and writes the marker.
.PARAMETER ExpectedRootDirectory
    The one top-level directory the vendor archive must contain (e.g. 'jdk-25.0.4.1+1').
.OUTPUTS
    The Destination path.
.NOTES
    Extraction happens in a sibling staging directory and Destination is replaced wholesale, so an interrupted
    or stale install can never be merged with a fresh one. Callers must have verified the archive hash first.
#>
function Expand-LockedArchive {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$ArchivePath,
        [Parameter(Mandatory)] [string]$Destination,
        [Parameter(Mandatory)] [string]$ExpectedRootDirectory,
        [Parameter(Mandatory)] [string]$Sha256
    )

    $parent = Split-Path -Parent $Destination
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $staging = "$Destination.extracting"
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }

    try {
        Expand-Archive -LiteralPath $ArchivePath -DestinationPath $staging -Force
        $roots = @(Get-ChildItem -LiteralPath $staging -Force)
        $rootNames = ($roots | ForEach-Object { $_.Name }) -join ', '
        if ($roots.Count -ne 1 -or -not $roots[0].PSIsContainer -or $roots[0].Name -cne $ExpectedRootDirectory) {
            throw "Archive '$ArchivePath' must contain exactly one root directory named '$ExpectedRootDirectory'; found: $rootNames"
        }

        if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Recurse -Force }
        Move-Item -LiteralPath $roots[0].FullName -Destination $Destination
        Set-Content -LiteralPath (Join-Path $Destination '.bs2bg-provisioned') -Value $Sha256.ToLowerInvariant() -NoNewline -Encoding ascii
        return $Destination
    }
    finally {
        if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
    }
}

<#
.SYNOPSIS
    Downloads, checksum-verifies, and extracts a pinned archive unless Destination already holds it.
.PARAMETER Label
    Human-readable input name for progress and error messages.
.OUTPUTS
    The Destination path.
.NOTES
    The download is verified before extraction; a mismatch deletes the download and throws, so an unexpected
    artifact never reaches the cache. This is the only function in the module that touches the network.
#>
function Install-LockedArchive {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Url,
        [Parameter(Mandatory)] [string]$Sha256,
        [Parameter(Mandatory)] [string]$Destination,
        [Parameter(Mandatory)] [string]$ExpectedRootDirectory,
        [Parameter(Mandatory)] [string]$Label
    )

    if (Test-ProvisionedArchive -Destination $Destination -Sha256 $Sha256) {
        Write-Host "[provision] $Label already provisioned at $Destination (sha256 $Sha256)"
        return $Destination
    }

    $downloadDir = "$Destination.download"
    New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null
    $archive = Join-Path $downloadDir ([System.IO.Path]::GetFileName(([uri]$Url).AbsolutePath))
    try {
        Write-Host "[provision] downloading $Label`n            from $Url"
        # Invoke-WebRequest's progress bar makes large downloads dramatically slower on Windows PowerShell 5.1.
        $previousProgress = $ProgressPreference
        $ProgressPreference = 'SilentlyContinue'
        try {
            Invoke-WebRequest -Uri $Url -OutFile $archive -UseBasicParsing
        }
        finally {
            $ProgressPreference = $previousProgress
        }
        Write-Host "[provision] verifying $Label against pinned SHA-256 $Sha256"
        Assert-FileSha256 -Path $archive -ExpectedSha256 $Sha256 -Label $Label | Out-Null
        Write-Host "[provision] extracting $Label to $Destination"
        return Expand-LockedArchive -ArchivePath $archive -Destination $Destination -ExpectedRootDirectory $ExpectedRootDirectory -Sha256 $Sha256
    }
    finally {
        if (Test-Path -LiteralPath $downloadDir) { Remove-Item -LiteralPath $downloadDir -Recurse -Force }
    }
}

<#
.SYNOPSIS
    Asserts that pom.xml compiles every production source with full lint enforcement, and lists those sources.
.OUTPUTS
    PSCustomObject with SourceDirectory, ProductionSources (repo-relative to the source directory, forward
    slashes, sorted), CompilerArgs, and CompleteApplicationGate ($true; the function throws otherwise).
.NOTES
    Fails closed on every transitional escape hatch: an include/exclude list on maven-compiler-plugin, an
    <implicit> setting, a -sourcepath/--source-path/-implicit compiler argument, a narrowed lint category
    (-Xlint:-x), or a missing -Xlint:all / -Werror pair. This is what makes "a source-filtered build cannot be
    reported or invoked as the complete gate" a checked fact before any Maven goal runs.
#>
function Assert-CompleteSourceScope {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$RepoRoot)

    [xml]$pom = Get-Content -LiteralPath (Join-Path $RepoRoot 'pom.xml') -Raw
    $namespace = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
    $namespace.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')

    $sourceDirectory = $pom.SelectSingleNode('/m:project/m:build/m:sourceDirectory', $namespace)
    $sourceRoot = Join-Path $RepoRoot ($(if ($sourceDirectory) { $sourceDirectory.InnerText } else { 'src/main/java' }))

    $compilerPlugin = $pom.SelectSingleNode("/m:project/m:build/m:plugins/m:plugin[m:artifactId='maven-compiler-plugin']", $namespace)
    if (-not $compilerPlugin) {
        throw 'pom.xml must configure maven-compiler-plugin under build/plugins.'
    }
    # Every compiler-plugin configuration anywhere in the pom counts: an <execution>, <pluginManagement>, or
    # <profile> configuration would otherwise filter sources while the top-level configuration looked clean.
    $compilerConfigurations = @($pom.SelectNodes("//m:plugin[m:artifactId='maven-compiler-plugin']//m:configuration", $namespace))
    foreach ($configuration in $compilerConfigurations) {
        foreach ($escapeHatch in @('includes', 'excludes', 'testIncludes', 'testExcludes', 'implicit', 'compileSourceRoots')) {
            if ($configuration.SelectSingleNode("m:$escapeHatch", $namespace)) {
                throw "pom.xml maven-compiler-plugin configures <$escapeHatch>; a source-filtered build is not the application gate."
            }
        }
    }
    $compilerArgs = @($compilerConfigurations | ForEach-Object { $_.SelectNodes('m:compilerArgs/m:arg', $namespace) } | ForEach-Object { $_.InnerText.Trim() })
    foreach ($arg in $compilerArgs) {
        if ($arg -match '^(-sourcepath|--source-path|-implicit)') {
            throw "pom.xml maven-compiler-plugin passes '$arg'; the source path may not be overridden."
        }
        if ($arg -like '-Xlint:-*') {
            throw "pom.xml maven-compiler-plugin narrows lint with '$arg'."
        }
        if ($arg -like '*enable-preview*') {
            throw "pom.xml maven-compiler-plugin passes '$arg'; preview features stay disabled."
        }
    }
    if (-not ($compilerArgs -ccontains '-Xlint:all')) {
        throw "pom.xml maven-compiler-plugin must pass -Xlint:all (found: $($compilerArgs -join ' '))."
    }
    if (-not ($compilerArgs -ccontains '-Werror')) {
        throw "pom.xml maven-compiler-plugin must pass -Werror (found: $($compilerArgs -join ' '))."
    }

    $sourceRootResolved = (Resolve-Path -LiteralPath $sourceRoot).Path
    $sources = @(Get-ChildItem -LiteralPath $sourceRootResolved -Recurse -File -Filter '*.java' | ForEach-Object {
        $_.FullName.Substring($sourceRootResolved.Length).TrimStart('\', '/').Replace('\', '/')
    } | Sort-Object)
    if ($sources.Count -eq 0) {
        throw "No production sources found under $sourceRootResolved."
    }

    return [pscustomobject]@{
        SourceDirectory         = $sourceRootResolved
        ProductionSources       = $sources
        CompilerArgs            = $compilerArgs
        CompleteApplicationGate = $true
    }
}

<#
.SYNOPSIS
    Asserts that the built jar contains a class for every production source and every production resource.
.PARAMETER ResourceDirectories
    Directories whose non-Java files are packaged at the jar root (pom.xml <resources>); defaults to src and assets.
.OUTPUTS
    PSCustomObject with Jar, ClassCount, ResourceCount, and Resources (jar entry names); the function throws
    when any expected entry is missing.
.NOTES
    Reads the zip directory only. A source whose class is missing or a resource that the resources plugin
    dropped fails the run, so "every production resource is present in the build artifact" is verified on the
    artifact itself rather than on target/classes.
#>
function Assert-JarContainsProductionResources {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$RepoRoot,
        [Parameter(Mandatory)] [string]$JarPath,
        [string[]]$ResourceDirectories = @('src', 'assets')
    )

    if (-not (Test-Path -LiteralPath $JarPath)) {
        throw "Build artifact not found: $JarPath"
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $entries = New-Object 'System.Collections.Generic.HashSet[string]'
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $JarPath).Path)
    try {
        foreach ($entry in $zip.Entries) { [void]$entries.Add($entry.FullName) }
    }
    finally {
        $zip.Dispose()
    }

    $expectedClasses = New-Object System.Collections.Generic.List[string]
    $expectedResources = New-Object System.Collections.Generic.List[string]
    foreach ($directory in $ResourceDirectories) {
        $root = Join-Path $RepoRoot $directory
        if (-not (Test-Path -LiteralPath $root)) { continue }
        $rootResolved = (Resolve-Path -LiteralPath $root).Path
        foreach ($file in Get-ChildItem -LiteralPath $rootResolved -Recurse -File) {
            $relative = $file.FullName.Substring($rootResolved.Length).TrimStart('\', '/').Replace('\', '/')
            if ($relative -like '*.java') {
                $expectedClasses.Add($relative.Substring(0, $relative.Length - 5) + '.class')
            }
            else {
                $expectedResources.Add($relative)
            }
        }
    }
    if ($expectedResources.Count -eq 0) {
        throw "No production resources found under $($ResourceDirectories -join ', ') in $RepoRoot."
    }

    $missing = @(@($expectedClasses) + @($expectedResources) | Where-Object { -not $entries.Contains($_) })
    if ($missing.Count -gt 0) {
        throw "Build artifact $JarPath is missing $($missing.Count) production entries:`n  $($missing -join "`n  ")"
    }

    return [pscustomobject]@{
        Jar           = $JarPath
        ClassCount    = $expectedClasses.Count
        ResourceCount = $expectedResources.Count
        Resources     = $expectedResources.ToArray()
    }
}

<#
.SYNOPSIS
    Asserts that every named Surefire suite ran, executed at least one test, and recorded no failure or error.
.PARAMETER Suites
    Fully qualified test class names whose TEST-<name>.xml reports must exist.
.OUTPUTS
    Ordered hashtable of suite name to its test count.
.NOTES
    Surefire totals alone cannot show that the gate suites (toolchain guard, source gate, FXML harness, seam
    tests) were part of the run; a -Dtest= filter or a deleted test class would otherwise pass unnoticed.
#>
function Assert-RequiredSurefireSuites {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$RepoRoot,
        [Parameter(Mandatory)] [string[]]$Suites
    )

    $reportDir = Join-Path $RepoRoot 'target\surefire-reports'
    $counts = [ordered]@{}
    $problems = @()
    foreach ($suite in $Suites) {
        $report = Join-Path $reportDir "TEST-$suite.xml"
        if (-not (Test-Path -LiteralPath $report)) {
            $problems += "  ${suite}: no report at $report"
            continue
        }
        [xml]$xml = Get-Content -LiteralPath $report -Raw
        $root = $xml.DocumentElement
        $tests = [int]$root.GetAttribute('tests')
        $failures = [int]$root.GetAttribute('failures')
        $errors = [int]$root.GetAttribute('errors')
        if ($tests -le 0) {
            $problems += "  ${suite}: ran 0 tests"
        }
        elseif ($failures -ne 0 -or $errors -ne 0) {
            $problems += "  ${suite}: $failures failure(s), $errors error(s)"
        }
        $counts[$suite] = $tests
    }
    if ($problems.Count -gt 0) {
        throw "Required test suites did not all run green:`n$($problems -join "`n")"
    }
    return $counts
}

<#
.SYNOPSIS
    Reads a <properties> value from the repository pom.xml.
.NOTES
    Used to cross-check that the pin Surefire forwards to Java25ToolchainGuardTest (bs2bg.toolchain.jdk.runtimeVersion)
    still equals the lock, so the pom and the lock cannot drift apart silently. Throws when the property is absent.
#>
function Get-PomProperty {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$RepoRoot,
        [Parameter(Mandatory)] [string]$Name
    )

    [xml]$pom = Get-Content -LiteralPath (Join-Path $RepoRoot 'pom.xml') -Raw
    $namespace = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
    $namespace.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    $node = $pom.SelectSingleNode("/m:project/m:properties/m:$Name", $namespace)
    if (-not $node) {
        throw "pom.xml has no <properties> entry named '$Name'."
    }
    return $node.InnerText.Trim()
}

<#
.SYNOPSIS
    Summarises target/surefire-reports/TEST-*.xml: suite/test counts and the JVM properties the forked test JVM recorded.
.OUTPUTS
    PSCustomObject with suites, tests, failures, errors, skipped, observedJavaVendor, observedJavaRuntimeVersion
    (the last two are distinct values across suites, so more than one entry means the toolchain was not stable).
.NOTES
    These are observed values written by Surefire from inside the forked JVM, which is why the evidence file
    records them alongside the lock constants. Throws when no report exists so a run that skipped tests cannot
    produce evidence.
#>
function Get-SurefireSummary {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$RepoRoot)

    $reportDir = Join-Path $RepoRoot 'target\surefire-reports'
    $reports = @()
    if (Test-Path -LiteralPath $reportDir) {
        $reports = @(Get-ChildItem -LiteralPath $reportDir -File -Filter 'TEST-*.xml')
    }
    if ($reports.Count -eq 0) {
        throw "No Surefire reports found under $reportDir (target/surefire-reports); the test phase did not run."
    }

    $tests = 0; $failures = 0; $errors = 0; $skipped = 0
    $vendors = New-Object System.Collections.Generic.List[string]
    $runtimes = New-Object System.Collections.Generic.List[string]
    foreach ($report in $reports) {
        [xml]$suite = Get-Content -LiteralPath $report.FullName -Raw
        $root = $suite.DocumentElement
        $tests += [int]$root.GetAttribute('tests')
        $failures += [int]$root.GetAttribute('failures')
        $errors += [int]$root.GetAttribute('errors')
        $skipped += [int]$root.GetAttribute('skipped')
        foreach ($property in $root.SelectNodes('properties/property')) {
            switch ($property.GetAttribute('name')) {
                'java.vendor' { if (-not $vendors.Contains($property.GetAttribute('value'))) { $vendors.Add($property.GetAttribute('value')) } }
                'java.runtime.version' { if (-not $runtimes.Contains($property.GetAttribute('value'))) { $runtimes.Add($property.GetAttribute('value')) } }
            }
        }
    }

    return [pscustomobject]@{
        suites                     = $reports.Count
        tests                      = $tests
        failures                   = $failures
        errors                     = $errors
        skipped                    = $skipped
        observedJavaVendor         = $vendors.ToArray()
        observedJavaRuntimeVersion = $runtimes.ToArray()
    }
}

<#
.SYNOPSIS
    Prints a cyan "==> step" banner for the verification and packaging scripts.
#>
function Write-Step {
    [CmdletBinding()]
    param([string]$Message)
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

<#
.SYNOPSIS
    Runs a native executable, returning its combined stdout/stderr lines; throws on a non-zero exit code.
.PARAMETER Label
    Human-readable name for the failure message (e.g. 'java -version').
#>
function Invoke-Native {
    [CmdletBinding()]
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

Export-ModuleMember -Function @(
    'Write-Step',
    'Invoke-Native',
    'Get-PomProperty',
    'Get-SurefireSummary',
    'Get-ToolchainLock',
    'Assert-FileSha256',
    'Read-JdkReleaseFile',
    'Assert-JdkRelease',
    'Assert-JmodDescribeOutput',
    'Read-PropertiesFile',
    'Assert-MavenWrapperPinned',
    'Test-ProvisionedArchive',
    'Expand-LockedArchive',
    'Install-LockedArchive',
    'Assert-CompleteSourceScope',
    'Assert-JarContainsProductionResources',
    'Assert-RequiredSurefireSuites'
)
