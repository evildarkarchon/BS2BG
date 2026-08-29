#Requires -Version 5.1
<#
.SYNOPSIS
    Deterministic helpers for building and verifying the self-contained Windows x64 app-image (issue #97).

.DESCRIPTION
    tools/java25/package-java25.ps1 drives jdeps, jlink, and jpackage from the pinned Temurin 25 / JavaFX 25 inputs
    that tools/java25/verify-java25.ps1 provisions. Every decision that does not need those native tools lives
    here so it can be unit tested with Pester (see WindowsAppImage.Tests.ps1): what counts as a complete staged
    payload, how the measured module closure is read and widened, what the generated launcher configuration must
    say, what the image directory must contain, how the runtime release is checked, how the smoke-run environment
    is scrubbed of any host Java, and how the third-party notices are assembled. All verification functions fail
    closed: a mismatch throws with the expected and actual values instead of warning.
#>

Set-StrictMode -Version Latest

# Read-JdkReleaseFile is shared with the toolchain verification; a jlink'd runtime writes the same KEY="value" format.
Import-Module (Join-Path $PSScriptRoot 'Java25Toolchain.psm1')

<#
.SYNOPSIS
    Describes the payload the Maven Wrapper staged for jpackage: one application jar beside lib/ with its dependencies.
.PARAMETER StagingDir
    The directory pom.xml's maven-jar-plugin and copy-dependencies execution write into (target/app-image-input).
.OUTPUTS
    PSCustomObject with StagingDir, MainJar, MainJarName, LibJars (sorted full paths), LibJarNames.
.NOTES
    Fails closed when there is not exactly one top-level jar (the launcher needs one main jar) or when any jar
    anywhere in the tree is a JavaFX artifact: JavaFX must come from the bundled runtime, never the classpath.
#>
function Get-StagedApplication {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$StagingDir)

    if (-not (Test-Path -LiteralPath $StagingDir)) {
        throw "Staging directory not found: $StagingDir (run the Maven package phase first)."
    }
    $resolved = (Resolve-Path -LiteralPath $StagingDir).Path
    $allJars = @(Get-ChildItem -LiteralPath $resolved -Recurse -File -Filter '*.jar')
    $javafx = @($allJars | Where-Object { $_.Name -match '^javafx-|openjfx' })
    if ($javafx.Count -gt 0) {
        throw "Staged payload contains JavaFX jars, which must only come from the bundled runtime: $(($javafx | ForEach-Object { $_.Name }) -join ', ')"
    }
    $topLevel = @(Get-ChildItem -LiteralPath $resolved -File -Filter '*.jar')
    if ($topLevel.Count -ne 1) {
        throw "Staging directory $resolved must hold exactly one application jar at its top level; found $($topLevel.Count): $(($topLevel | ForEach-Object { $_.Name }) -join ', ')"
    }
    $libDir = Join-Path $resolved 'lib'
    $libJars = @()
    if (Test-Path -LiteralPath $libDir) {
        $libJars = @(Get-ChildItem -LiteralPath $libDir -File -Filter '*.jar' | Sort-Object -Property Name)
    }
    return [pscustomobject]@{
        StagingDir  = $resolved
        MainJar     = $topLevel[0].FullName
        MainJarName = $topLevel[0].Name
        LibJars     = @($libJars | ForEach-Object { $_.FullName })
        LibJarNames = @($libJars | ForEach-Object { $_.Name })
    }
}

<#
.SYNOPSIS
    Parses `jdeps --print-module-deps` output into a sorted, unique module list.
.PARAMETER Output
    Combined stdout/stderr lines of the jdeps run.
.NOTES
    jdeps prints warnings (split packages, missing dependencies) around the single comma-separated module line;
    those are skipped, but an "Error" line fails the run because it means a module was not resolved and the
    measurement is incomplete. Zero or several candidate lines also fail: the closure must be unambiguous.
#>
function ConvertFrom-JdepsModuleDeps {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [AllowEmptyCollection()] [AllowEmptyString()] [string[]]$Output)

    $lines = @($Output | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
    $errors = @($lines | Where-Object { $_ -match '^Error' })
    if ($errors.Count -gt 0) {
        throw "jdeps reported an error; the module closure could not be measured:`n$($errors -join "`n")"
    }
    $candidates = @($lines | Where-Object { $_ -notmatch '^Warning' -and $_ -match '^[A-Za-z0-9_.]+(,[A-Za-z0-9_.]+)*$' })
    if ($candidates.Count -eq 0) {
        throw "jdeps output contains no module line (expected a comma-separated list):`n$($lines -join "`n")"
    }
    if ($candidates.Count -ne 1) {
        throw "jdeps output must contain exactly one module line; found $($candidates.Count):`n$($candidates -join "`n")"
    }
    return @($candidates[0].Split(',') | Sort-Object -Unique)
}

<#
.SYNOPSIS
    Widens the measured module closure with documented explicit additions and checks it against the JavaFX pins.
.PARAMETER MeasuredModules
    Modules jdeps found the application and its staged dependencies to require.
.PARAMETER ExplicitModules
    Ordered hashtable of module name -> reason for modules jdeps cannot see (service providers, reflective use).
.PARAMETER PinnedJavaFxModules
    The JavaFX modules the toolchain lock provisions and verifies.
.OUTPUTS
    PSCustomObject with Modules (sorted union), Measured, and Explicit (ordered hashtable of name -> reason).
.NOTES
    Fails closed when no JavaFX module was measured (the module path used for measurement was wrong, so the
    closure would build a runtime without a toolkit) or when the application needs a JavaFX module the lock does
    not pin (the jlink step could not supply it from a verified input).
#>
function Resolve-RuntimeModules {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string[]]$MeasuredModules,
        [Parameter(Mandatory)] [AllowEmptyCollection()] [System.Collections.IDictionary]$ExplicitModules,
        [Parameter(Mandatory)] [string[]]$PinnedJavaFxModules
    )

    $measured = @($MeasuredModules | Sort-Object -Unique)
    $javafx = @($measured | Where-Object { $_ -like 'javafx.*' })
    if ($javafx.Count -eq 0) {
        throw "The measured module closure contains no javafx.* module ($($measured -join ', ')); the JavaFX inputs were not on the jdeps module path."
    }
    $unpinned = @($javafx | Where-Object { $PinnedJavaFxModules -notcontains $_ })
    if ($unpinned.Count -gt 0) {
        throw "The application requires JavaFX module(s) the toolchain lock does not pin: $($unpinned -join ', '). Add them to javafx.requiredModules after verifying the JMODs."
    }
    $explicit = [ordered]@{}
    foreach ($name in $ExplicitModules.Keys) { $explicit[$name] = $ExplicitModules[$name] }
    $modules = @(@($measured) + @($explicit.Keys) | Sort-Object -Unique)
    return [pscustomobject]@{
        Modules  = $modules
        Measured = $measured
        Explicit = $explicit
    }
}

<#
.SYNOPSIS
    Reads a jpackage launcher configuration (app/<name>.cfg) into section -> key -> value-list hashtables.
.NOTES
    Keys repeat (app.classpath, java-options), so every value is a list. Unknown sections and keys are kept
    verbatim; callers assert on the keys they care about.
#>
function Read-LauncherConfig {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Launcher configuration not found: $Path"
    }
    $config = [ordered]@{}
    $section = $null
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#') -or $trimmed.StartsWith(';')) { continue }
        if ($trimmed -match '^\[(.+)\]$') {
            $section = $Matches[1]
            if (-not $config.Contains($section)) { $config[$section] = [ordered]@{} }
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) { continue }
        if ($null -eq $section) { $section = ''; if (-not $config.Contains($section)) { $config[$section] = [ordered]@{} } }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if (-not $config[$section].Contains($key)) { $config[$section][$key] = @() }
        $config[$section][$key] = @($config[$section][$key]) + @($value)
    }
    return $config
}

<#
.SYNOPSIS
    Asserts that the generated launcher configuration starts the launcher class from the staged jars on the bundled runtime.
.PARAMETER RequiredJavaOptions
    JVM options that must reach the packaged process (e.g. --enable-native-access=javafx.graphics).
.NOTES
    jpackage 25 writes the main jar as the first app.classpath entry and every other jar it found under --input as
    further entries, all $APPDIR-relative; the version travels as -Djpackage.app-version; the bundled runtime is
    implicit (an app.runtime key only appears when one is configured explicitly). The contract asserted here:
    the main class is the launcher, $APPDIR\<main jar> and $APPDIR\lib\<each staged lib> are on the classpath, no
    entry is absolute or a JavaFX jar (nothing may reach outside the image or put JavaFX on the classpath), any
    app.runtime is the image's own runtime, the stamped version is the pom's, and every required JVM option is present.
#>
function Assert-LauncherConfig {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [System.Collections.IDictionary]$Config,
        [Parameter(Mandatory)] [string]$MainClass,
        [Parameter(Mandatory)] [string]$MainJarName,
        [Parameter(Mandatory)] [AllowEmptyCollection()] [string[]]$LibJarNames,
        [Parameter(Mandatory)] [string]$AppVersion,
        [AllowEmptyCollection()] [string[]]$RequiredJavaOptions = @()
    )

    if (-not $Config.Contains('Application')) {
        throw 'Launcher configuration has no [Application] section.'
    }
    $application = $Config['Application']
    $problems = @()
    # Single value of a key, or $null when the key is absent or repeated (every value is a list; see Read-LauncherConfig).
    function Get-Single { param($Table, [string]$Key)
        if (-not $Table.Contains($Key)) { return $null }
        $values = @($Table[$Key])
        if ($values.Count -ne 1) { return $null }
        return $values[0]
    }
    # PowerShell variable names are case-insensitive, so the observed values must not reuse the parameter names.
    $actualMainClass = Get-Single $application 'app.mainclass'
    if ($actualMainClass -cne $MainClass) { $problems += "  app.mainclass: expected '$MainClass' but found '$actualMainClass'" }
    if ($application.Contains('app.runtime')) {
        $actualRuntime = Get-Single $application 'app.runtime'
        if ($actualRuntime -cne '$ROOTDIR\runtime') { $problems += "  app.runtime: expected the bundled '`$ROOTDIR\runtime' but found '$actualRuntime'" }
    }

    $classpath = @()
    if ($application.Contains('app.classpath')) { $classpath = @($application['app.classpath'] | ForEach-Object { $_.Replace('/', '\') }) }
    if ($classpath -cnotcontains "`$APPDIR\$MainJarName") {
        $problems += "  app.classpath: the application jar '$MainJarName' is not on the launcher classpath as `$APPDIR\$MainJarName (found: $($classpath -join ' '))"
    }
    foreach ($lib in $LibJarNames) {
        if ($classpath -cnotcontains "`$APPDIR\lib\$lib") { $problems += "  app.classpath: staged dependency '$lib' is not on the launcher classpath as `$APPDIR\lib\$lib" }
    }
    foreach ($entry in $classpath) {
        $name = [System.IO.Path]::GetFileName($entry)
        if (-not $entry.StartsWith('$APPDIR\')) { $problems += "  app.classpath: entry '$entry' ($name) is not image-relative; the launcher must not reach outside the image" }
        if ($name -match '^javafx-|openjfx') { $problems += "  app.classpath: JavaFX jar '$name' must not be on the launcher classpath" }
    }

    $javaOptions = @()
    if ($Config.Contains('JavaOptions') -and $Config['JavaOptions'].Contains('java-options')) {
        $javaOptions = @($Config['JavaOptions']['java-options'])
    }
    if ($javaOptions -cnotcontains "-Djpackage.app-version=$AppVersion") {
        $problems += "  java-options: expected '-Djpackage.app-version=$AppVersion' (found: $($javaOptions -join ' '))"
    }
    foreach ($required in $RequiredJavaOptions) {
        if ($javaOptions -cnotcontains $required) { $problems += "  java-options: required option '$required' is absent (found: $($javaOptions -join ' '))" }
    }
    if ($problems.Count -gt 0) {
        throw "Launcher configuration does not describe the expected packaged application:`n$($problems -join "`n")"
    }
}

<#
.SYNOPSIS
    Asserts that jpackage's own state record (app/.jpackage.xml) stamps the expected version, launcher, and main class.
.OUTPUTS
    PSCustomObject with ToolVersion (the jpackage/JDK build that produced the image) and Platform.
#>
function Assert-JpackageState {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Path,
        [Parameter(Mandatory)] [string]$AppVersion,
        [Parameter(Mandatory)] [string]$LauncherName,
        [Parameter(Mandatory)] [string]$MainClass
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "jpackage state file not found: $Path"
    }
    [xml]$state = Get-Content -LiteralPath $Path -Raw
    $root = $state.DocumentElement
    if ($root.LocalName -ne 'jpackage-state') {
        throw "$Path is not a jpackage-state document (root element '$($root.LocalName)')."
    }
    $problems = @()
    $expected = [ordered]@{ 'app-version' = $AppVersion; 'main-launcher' = $LauncherName; 'main-class' = $MainClass }
    foreach ($element in $expected.Keys) {
        $node = $root.SelectSingleNode($element)
        $actual = $(if ($node) { $node.InnerText.Trim() } else { '' })
        if ($actual -cne $expected[$element]) { $problems += "  <$element>: expected '$($expected[$element])' but found '$actual'" }
    }
    if ($problems.Count -gt 0) {
        throw "jpackage state $Path does not stamp the expected application identity:`n$($problems -join "`n")"
    }
    return [pscustomobject]@{
        ToolVersion = $root.GetAttribute('version')
        Platform    = $root.GetAttribute('platform')
    }
}

<#
.SYNOPSIS
    Asserts that a jpackage app-image directory holds the launcher, the exact staged payload, the bundled runtime, and the notices.
.PARAMETER RequiredFiles
    Additional image-relative files that must exist (e.g. THIRD-PARTY-NOTICES.txt).
.OUTPUTS
    PSCustomObject with Files (image-relative, forward slashes, ordinal-sorted), FileCount, and TotalBytes.
.NOTES
    The payload check is exact in both directions: every staged jar must be present and no other jar may be,
    so a stale or foreign jar cannot ride along. runtime/legal/java.base/LICENSE is required because jlink copies
    each module's legal directory from the JMODs; its absence means the notices were stripped.
#>
function Assert-AppImageLayout {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$ImageDir,
        [Parameter(Mandatory)] [string]$LauncherName,
        [Parameter(Mandatory)] [string]$MainJarName,
        [Parameter(Mandatory)] [AllowEmptyCollection()] [string[]]$LibJarNames,
        [AllowEmptyCollection()] [string[]]$RequiredFiles = @()
    )

    if (-not (Test-Path -LiteralPath $ImageDir)) {
        throw "Application image directory not found: $ImageDir"
    }
    $root = (Resolve-Path -LiteralPath $ImageDir).Path
    $required = @(
        "$LauncherName.exe",
        "app\$MainJarName",
        "app\$LauncherName.cfg",
        'runtime\release',
        'runtime\lib\modules',
        'runtime\bin\server\jvm.dll',
        'runtime\legal\java.base\LICENSE'
    ) + @($LibJarNames | ForEach-Object { "app\lib\$_" }) + @($RequiredFiles)
    $missing = @($required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $root $_) -PathType Leaf) })
    if ($missing.Count -gt 0) {
        throw "Application image $root is missing $($missing.Count) required entries:`n  $($missing -join "`n  ")"
    }

    $expectedJars = @("app\$MainJarName") + @($LibJarNames | ForEach-Object { "app\lib\$_" })
    $appRoot = Join-Path $root 'app'
    $actualJars = @(Get-ChildItem -LiteralPath $appRoot -Recurse -File -Filter '*.jar' | ForEach-Object { $_.FullName.Substring($root.Length).TrimStart('\', '/') })
    $unexpected = @($actualJars | Where-Object { $expectedJars -notcontains $_ })
    if ($unexpected.Count -gt 0) {
        throw "Application image payload contains jars that were not staged by the build: $($unexpected -join ', ')"
    }

    $files = @(Get-ChildItem -LiteralPath $root -Recurse -File)
    $ordered = [string[]]@($files | ForEach-Object { $_.FullName.Substring($root.Length).TrimStart('\', '/').Replace('\', '/') })
    # Ordinal order keeps the inventory stable across locales, so evidence files diff cleanly between runs.
    [Array]::Sort($ordered, [System.StringComparer]::Ordinal)
    return [pscustomobject]@{
        Files      = $ordered
        FileCount  = $files.Count
        TotalBytes = [int64](($files | Measure-Object -Property Length -Sum).Sum)
    }
}

<#
.SYNOPSIS
    Hashes every file under a directory and derives one SHA-256 for the whole tree.
.OUTPUTS
    PSCustomObject with Files ([{path, sha256, size}] in ordinal path order) and Sha256 (hash of the
    "<sha256>  <path>`n" manifest lines, so the digest covers names, contents, and set membership).
#>
function Get-TreeDigest {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$Root)

    $resolved = (Resolve-Path -LiteralPath $Root).Path
    $entries = @(Get-ChildItem -LiteralPath $resolved -Recurse -File | ForEach-Object {
        [pscustomobject]@{
            path   = $_.FullName.Substring($resolved.Length).TrimStart('\', '/').Replace('\', '/')
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            size   = [int64]$_.Length
        }
    })
    $sorted = [object[]]$entries
    [Array]::Sort($sorted, [System.Comparison[object]]{ param($a, $b) [System.StringComparer]::Ordinal.Compare($a.path, $b.path) })
    $manifest = ($sorted | ForEach-Object { "$($_.sha256)  $($_.path)`n" }) -join ''
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = ($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($manifest)) | ForEach-Object { $_.ToString('x2') }) -join ''
    }
    finally {
        $sha.Dispose()
    }
    return [pscustomobject]@{
        Files  = $sorted
        Sha256 = $digest
    }
}

<#
.SYNOPSIS
    Asserts that a jlink'd runtime's release file names the pinned Java version and contains every expected module.
.OUTPUTS
    PSCustomObject with Release (hashtable) and Modules (the MODULES list as written by jlink, in file order).
.NOTES
    jlink writes only JAVA_VERSION and MODULES (no IMPLEMENTOR or JAVA_RUNTIME_VERSION), so the version pin here
    is the lock's jdk.release.JAVA_VERSION; the vendor and full build are established on the JDK the jmods came
    from (Assert-JdkRelease) and recorded in the evidence together with the jlink module path.
#>
function Assert-RuntimeRelease {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$RuntimeDir,
        [Parameter(Mandatory)] [string]$ExpectedJavaVersion,
        [Parameter(Mandatory)] [string[]]$ExpectedModules
    )

    $releasePath = Join-Path $RuntimeDir 'release'
    if (-not (Test-Path -LiteralPath $releasePath)) {
        throw "Runtime image has no release file: $releasePath"
    }
    $release = Read-JdkReleaseFile -Path $releasePath
    if (-not $release.ContainsKey('JAVA_VERSION') -or $release['JAVA_VERSION'] -cne $ExpectedJavaVersion) {
        throw "Runtime image JAVA_VERSION is '$($release['JAVA_VERSION'])'; expected the pinned '$ExpectedJavaVersion'."
    }
    if (-not $release.ContainsKey('MODULES')) {
        throw "Runtime image release file has no MODULES entry: $releasePath"
    }
    $modules = @($release['MODULES'].Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries))
    $missing = @($ExpectedModules | Where-Object { $modules -cnotcontains $_ })
    if ($missing.Count -gt 0) {
        throw "Runtime image is missing expected module(s): $($missing -join ', ') (image has: $($modules -join ' '))"
    }
    return [pscustomobject]@{
        Release = $release
        Modules = $modules
    }
}

<#
.SYNOPSIS
    Builds the environment for the packaged smoke run with every host-Java discovery path removed.
.PARAMETER Environment
    The variables to start from (normally [System.Environment]::GetEnvironmentVariables()).
.OUTPUTS
    PSCustomObject with Variables (case-insensitive hashtable), RemovedVariables, and RemovedPathEntries.
.NOTES
    JAVA_HOME and the *_JAVA_OPTIONS variables could inject a system JDK or JVM options into the launcher; PATH
    entries that look like Java installations could let a DLL resolve from a system JDK. Removing them is what
    makes "starts without using a system Java installation" an observable condition of the run rather than an
    assumption about the host.
#>
function Get-ScrubbedEnvironment {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [System.Collections.IDictionary]$Environment)

    $removeVariables = @('JAVA_HOME', 'JDK_JAVA_OPTIONS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH', 'JAVA_OPTS', 'JRE_HOME')
    $javaLike = '(?i)java|jdk|jre|adoptium|temurin|openjdk|graalvm|zulu|corretto|liberica|semeru'
    $variables = @{}
    $removed = @()
    $removedPath = @()
    foreach ($key in $Environment.Keys) {
        $name = "$key"
        if ($removeVariables -contains $name.ToUpperInvariant()) {
            $removed += $name
            continue
        }
        if ($name -ieq 'PATH') {
            $kept = @()
            foreach ($entry in "$($Environment[$key])".Split(';', [System.StringSplitOptions]::RemoveEmptyEntries)) {
                if ($entry -match $javaLike) { $removedPath += $entry } else { $kept += $entry }
            }
            $variables['PATH'] = $kept -join ';'
            continue
        }
        $variables[$name] = $Environment[$key]
    }
    return [pscustomobject]@{
        Variables          = $variables
        RemovedVariables   = @($removed | Sort-Object)
        RemovedPathEntries = @($removedPath)
    }
}

<#
.SYNOPSIS
    Assembles THIRD-PARTY-NOTICES.txt and extracts each staged library's license/notice files for the image.
.PARAMETER RuntimeComponents
    Objects with name, version, license, and noticesPath describing the bundled runtime inputs (JDK, JavaFX).
.OUTPUTS
    PSCustomObject with Path (the notices file) and Components (one record per staged lib jar).
.NOTES
    License metadata is taken from the jars themselves: META-INF/LICENSE*, META-INF/NOTICE*, and the embedded
    Maven pom's <licenses>. A jar without any of them is listed explicitly as having no embedded metadata rather
    than omitted, so the notices file can never silently under-report the payload.
#>
function New-ThirdPartyNotices {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $StagedApplication,
        [Parameter(Mandatory)] [string]$OutputDir,
        [Parameter(Mandatory)] [string]$ApplicationName,
        [Parameter(Mandatory)] [string]$ApplicationVersion,
        [Parameter(Mandatory)] [AllowEmptyCollection()] [object[]]$RuntimeComponents
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    if (Test-Path -LiteralPath $OutputDir) { Remove-Item -LiteralPath $OutputDir -Recurse -Force }
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

    $components = @()
    foreach ($jarPath in $StagedApplication.LibJars) {
        $jarName = [System.IO.Path]::GetFileName($jarPath)
        $baseName = [System.IO.Path]::GetFileNameWithoutExtension($jarPath)
        $coordinates = $null
        $licenses = @()
        $extracted = @()
        $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $jarPath).Path)
        try {
            foreach ($entry in $zip.Entries) {
                $full = $entry.FullName
                if ($full -match '^META-INF/(LICENSE|NOTICE)[^/]*$') {
                    $target = Join-Path (Join-Path $OutputDir "notices\$baseName") ([System.IO.Path]::GetFileName($full))
                    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
                    $extracted += "notices/$baseName/$([System.IO.Path]::GetFileName($full))"
                }
                elseif ($full -match '^META-INF/maven/[^/]+/[^/]+/pom\.properties$') {
                    $reader = New-Object System.IO.StreamReader($entry.Open())
                    try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
                    $properties = @{}
                    foreach ($line in $text -split "`r?`n") {
                        if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $properties[$Matches[1]] = $Matches[2].Trim() }
                    }
                    if ($properties.ContainsKey('groupId') -and $properties.ContainsKey('artifactId') -and $properties.ContainsKey('version')) {
                        $coordinates = "$($properties['groupId']):$($properties['artifactId']):$($properties['version'])"
                    }
                }
                elseif ($full -match '^META-INF/maven/[^/]+/[^/]+/pom\.xml$') {
                    $reader = New-Object System.IO.StreamReader($entry.Open())
                    try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
                    try {
                        [xml]$pom = $text
                        $licenseNodes = $pom.SelectNodes('//*[local-name()="licenses"]/*[local-name()="license"]')
                        foreach ($node in $licenseNodes) {
                            $name = ($node.SelectSingleNode('*[local-name()="name"]')).InnerText.Trim()
                            $urlNode = $node.SelectSingleNode('*[local-name()="url"]')
                            $licenses += $(if ($urlNode) { "$name ($($urlNode.InnerText.Trim()))" } else { $name })
                        }
                    }
                    catch {
                        # A malformed embedded pom is not fatal: the jar is still listed, just without license names.
                    }
                }
            }
        }
        finally {
            $zip.Dispose()
        }
        $components += [pscustomobject]@{
            jar            = $jarName
            coordinates    = $coordinates
            licenses       = @($licenses)
            extractedFiles = @($extracted | Sort-Object)
        }
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("$ApplicationName $ApplicationVersion - third-party notices")
    $lines.Add('')
    $lines.Add('This self-contained Windows x64 application image bundles the following components.')
    $lines.Add('Their license texts are shipped inside the image at the paths named below.')
    $lines.Add('')
    $lines.Add('Bundled runtime')
    $lines.Add('---------------')
    foreach ($component in $RuntimeComponents) {
        $lines.Add("$($component.name) $($component.version)")
        $lines.Add("  License: $($component.license)")
        $lines.Add("  Notices: $($component.noticesPath)")
    }
    $lines.Add('')
    $lines.Add('Application libraries (app/lib)')
    $lines.Add('-------------------------------')
    foreach ($component in $components) {
        $title = $component.jar
        if ($component.coordinates) { $title = "$($component.jar) ($($component.coordinates))" }
        $lines.Add($title)
        if ($component.licenses.Count -gt 0) {
            foreach ($license in $component.licenses) { $lines.Add("  License: $license") }
        }
        if ($component.extractedFiles.Count -gt 0) {
            foreach ($file in $component.extractedFiles) { $lines.Add("  Notices: $file") }
        }
        if ($component.licenses.Count -eq 0 -and $component.extractedFiles.Count -eq 0) {
            $lines.Add("  $($component.jar): no license metadata is embedded in the jar (no META-INF license, notice, or Maven pom <licenses>).")
        }
    }
    $lines.Add('')
    $path = Join-Path $OutputDir 'THIRD-PARTY-NOTICES.txt'
    Set-Content -LiteralPath $path -Value ($lines -join "`n") -Encoding utf8
    return [pscustomobject]@{
        Path       = $path
        Components = $components
    }
}

Export-ModuleMember -Function @(
    'Get-StagedApplication',
    'ConvertFrom-JdepsModuleDeps',
    'Resolve-RuntimeModules',
    'Read-LauncherConfig',
    'Assert-LauncherConfig',
    'Assert-JpackageState',
    'Assert-AppImageLayout',
    'Get-TreeDigest',
    'Assert-RuntimeRelease',
    'Get-ScrubbedEnvironment',
    'New-ThirdPartyNotices'
)
