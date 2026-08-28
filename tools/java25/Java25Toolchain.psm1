#Requires -Version 5.1
<#
.SYNOPSIS
    Deterministic helpers for the transitional Java 25 verification run (issue #94).

.DESCRIPTION
    Every function here either reads a pinned value, verifies an input against it, or reports the transitional
    build scope. Network access is confined to Install-LockedArchive so that everything that decides whether an
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
    Throws when a required section or SHA-256 pin is missing or malformed, so a half-edited lock cannot
    silently skip a verification step.
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
    foreach ($required in @('jdk.url', 'jdk.archiveRootDirectory', 'jdk.release', 'javafx.url', 'javafx.archiveRootDirectory', 'javafx.version', 'javafx.requiredModules', 'maven.version')) {
        $section, $property = $required.Split('.')
        if (-not ($lock.$section.PSObject.Properties.Name -contains $property)) {
            throw "Toolchain lock '$Path' is missing '$required'."
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
    Converts an Ant-style include pattern (as used by maven-compiler-plugin) to an anchored regex.
#>
function ConvertTo-AntPatternRegex {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$Pattern)

    $regex = [regex]::Escape($Pattern.Replace('\', '/'))
    # Escape() turns '**/' into '\*\*/' and '*' into '\*'; expand the doubled form first so it is not eaten by the single one.
    $regex = $regex.Replace('\*\*/', '(?:.*/)?').Replace('\*\*', '.*').Replace('\*', '[^/]*')
    return "^$regex$"
}

<#
.SYNOPSIS
    Reports which production sources the transitional compiler configuration admits and which it still excludes.
.OUTPUTS
    PSCustomObject with AdmittedPatterns, AdmittedSources, ExcludedSources (repo-relative, forward slashes,
    relative to the source directory) and CompleteApplicationGate ($true only when nothing is excluded).
.NOTES
    Reads pom.xml directly so the report always reflects the committed include list rather than a copy of it.
#>
function Get-TransitionalSourceScope {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$RepoRoot)

    [xml]$pom = Get-Content -LiteralPath (Join-Path $RepoRoot 'pom.xml') -Raw
    $namespace = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
    $namespace.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')

    $sourceDirectory = $pom.SelectSingleNode('/m:project/m:build/m:sourceDirectory', $namespace)
    $sourceRoot = Join-Path $RepoRoot ($(if ($sourceDirectory) { $sourceDirectory.InnerText } else { 'src/main/java' }))

    $compilerPlugin = $pom.SelectSingleNode("/m:project/m:build/m:plugins/m:plugin[m:artifactId='maven-compiler-plugin']", $namespace)
    $patterns = @()
    if ($compilerPlugin) {
        $patterns = @($compilerPlugin.SelectNodes('m:configuration/m:includes/m:include', $namespace) | ForEach-Object { $_.InnerText.Trim() })
    }
    $regexes = @($patterns | ForEach-Object { ConvertTo-AntPatternRegex -Pattern $_ })

    $sourceRootResolved = (Resolve-Path -LiteralPath $sourceRoot).Path
    $sources = Get-ChildItem -LiteralPath $sourceRootResolved -Recurse -File -Filter '*.java' | ForEach-Object {
        $_.FullName.Substring($sourceRootResolved.Length).TrimStart('\', '/').Replace('\', '/')
    } | Sort-Object

    $admitted = New-Object System.Collections.Generic.List[string]
    $excluded = New-Object System.Collections.Generic.List[string]
    foreach ($source in $sources) {
        # No include patterns means Maven compiles everything; otherwise a source must match at least one pattern.
        $isAdmitted = ($regexes.Count -eq 0) -or (($regexes | Where-Object { $source -match $_ }) | Measure-Object).Count -gt 0
        if ($isAdmitted) { $admitted.Add($source) } else { $excluded.Add($source) }
    }

    return [pscustomobject]@{
        SourceDirectory         = $sourceRootResolved
        AdmittedPatterns        = $patterns
        AdmittedSources         = $admitted.ToArray()
        ExcludedSources         = $excluded.ToArray()
        CompleteApplicationGate = ($excluded.Count -eq 0)
    }
}

<#
.SYNOPSIS
    Produces the human-readable transitional-scope report printed at the end of the verification run.
.OUTPUTS
    String array, one line each.
#>
function Format-TransitionalReport {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Scope)

    $lines = New-Object System.Collections.Generic.List[string]
    if ($Scope.CompleteApplicationGate) {
        $lines.Add('Source scope: every production source was compiled; this run is a complete application build.')
        return $lines.ToArray()
    }

    $lines.Add('=====================================================================================')
    $lines.Add('TRANSITIONAL RESULT - NOT the complete application gate')
    $lines.Add("This run compiled $($Scope.AdmittedSources.Count) admitted source file(s) matching:")
    foreach ($pattern in $Scope.AdmittedPatterns) { $lines.Add("  + $pattern") }
    $lines.Add("It still EXCLUDES $($Scope.ExcludedSources.Count) source file(s) until the JavaFX 25 UI port (#81):")
    foreach ($source in $Scope.ExcludedSources) { $lines.Add("  - $source") }
    $lines.Add('A green result here proves the pinned Java 25 toolchain and the JavaFX-independent contracts only.')
    $lines.Add('=====================================================================================')
    return $lines.ToArray()
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

Export-ModuleMember -Function @(
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
    'Get-TransitionalSourceScope',
    'Format-TransitionalReport'
)
