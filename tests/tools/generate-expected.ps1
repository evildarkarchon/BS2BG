# Regenerate tests/fixtures/expected/ by running the reference Java build
# (jBS2BG v1.1.2) against each input fixture, via the headless FixtureDriver
# at src/com/asdasfa/jbs2bg/testharness/FixtureDriver.java.
#
# Idempotent: re-running overwrites expected/.
#
# REQUIREMENTS
#   - A JDK 8 with bundled JavaFX (e.g. Zulu JDK 8 FX, Liberica JDK 8 Full).
#     Point the env var BS2BG_JDK8_HOME at its install root BEFORE running
#     this script. Example:
#         $env:BS2BG_JDK8_HOME = 'C:\tools\zulu-fx-8'
#     The script uses this JDK's javac.exe / java.exe directly, so your
#     primary JDK (JAVA_HOME / PATH) is not disturbed.
#   - If BS2BG_JDK8_HOME is unset, the script falls back to the `java`
#     and `javac` on PATH and warns.
#   - Dependency JARs in tests/tools/lib/ (pre-populated, versions matched
#     to the original jBS2BG .classpath):
#       commons-io-2.6.jar
#       juniversalchardet-2.1.0.jar   (the com.github.albfernandez fork —
#                                       1.0.3 on Maven has no detectCharset)
#       minimal-json-0.9.5.jar
#
# USAGE (from repo root, PowerShell):
#   $env:BS2BG_JDK8_HOME = 'C:\tools\zulu-fx-8'   # or wherever you extracted it
#   .\tests\tools\generate-expected.ps1
#
# The script will:
#   1. Compile FixtureDriver if bin/.../FixtureDriver.class is missing or
#      older than the .java source.
#   2. For each scenario, copy profile JSONs to a temp workdir and run the
#      driver with the workdir as CWD (so Settings.init() finds them).
#   3. Write templates.ini, templates-omit.ini, morphs.ini, project.jbs2bg,
#      and bos-json/*.json to tests/fixtures/expected/<scenario>/.

$ErrorActionPreference = 'Stop'

$repoRoot      = Resolve-Path "$PSScriptRoot/../.."
$inputs        = Join-Path $repoRoot 'tests/fixtures/inputs'
$expected      = Join-Path $repoRoot 'tests/fixtures/expected'
$classesDir    = Join-Path $repoRoot 'bin'
$libDir        = Join-Path $PSScriptRoot 'lib'
$profilesDir   = Join-Path $inputs 'profiles'
$driverSrc     = Join-Path $repoRoot 'src/com/asdasfa/jbs2bg/testharness/FixtureDriver.java'
$driverClass   = Join-Path $classesDir 'com/asdasfa/jbs2bg/testharness/FixtureDriver.class'

# Resolve javac / java from BS2BG_JDK8_HOME if set, else PATH.
if ($env:BS2BG_JDK8_HOME) {
    $javac = Join-Path $env:BS2BG_JDK8_HOME 'bin/javac.exe'
    $java  = Join-Path $env:BS2BG_JDK8_HOME 'bin/java.exe'
    if (-not (Test-Path $javac) -or -not (Test-Path $java)) {
        Write-Error "BS2BG_JDK8_HOME=$env:BS2BG_JDK8_HOME but bin/javac.exe or bin/java.exe not found there."
        exit 1
    }
    Write-Host "Using JDK at $env:BS2BG_JDK8_HOME"
} else {
    $javac = 'javac'
    $java  = 'java'
    Write-Warning "BS2BG_JDK8_HOME not set; falling back to PATH. Make sure `javac -version` reports 1.8.x with JavaFX available."
}

# Verify the chosen java reports 1.8 (and JavaFX is loadable).
$javaVersion = (& $java -version 2>&1) -join "`n"
if ($javaVersion -notmatch '"1\.8\.') {
    Write-Warning "Selected java does not report 1.8.x. Version output:`n$javaVersion"
    Write-Warning "Continuing — if you have JDK 11+ with a separate JavaFX SDK, edit this script to add --module-path."
}

# Pre-flight: dependency JARs
foreach ($required in @('commons-io-2.6.jar','juniversalchardet-2.1.0.jar','minimal-json-0.9.5.jar')) {
    if (-not (Test-Path (Join-Path $libDir $required))) {
        Write-Error "Missing $required in $libDir. See README."
        exit 1
    }
}

# Compile FixtureDriver if needed.
$needCompile = $false
if (-not (Test-Path $driverClass)) {
    $needCompile = $true
} else {
    $srcTime = (Get-Item $driverSrc).LastWriteTime
    $clsTime = (Get-Item $driverClass).LastWriteTime
    if ($srcTime -gt $clsTime) { $needCompile = $true }
}

if ($needCompile) {
    Write-Host "Compiling FixtureDriver..."
    $compileCp = @($classesDir) + (Get-ChildItem -Path $libDir -Filter '*.jar').FullName -join ';'
    & $javac -cp $compileCp -d $classesDir $driverSrc
    if ($LASTEXITCODE -ne 0) {
        Write-Error "javac failed (exit $LASTEXITCODE)"
        exit $LASTEXITCODE
    }
}

# Settings.init() reads settings.json and settings_UUNP.json from CWD. Copy
# them into an ephemeral workdir so the repo root is not touched.
$workdir = Join-Path $env:TEMP "bs2bg-fixture-workdir"
Remove-Item -Recurse -Force $workdir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $workdir | Out-Null
Copy-Item (Join-Path $profilesDir 'settings.json')      $workdir
Copy-Item (Join-Path $profilesDir 'settings_UUNP.json') $workdir

$classpath = @($classesDir) + (Get-ChildItem -Path $libDir -Filter '*.jar').FullName -join ';'

$scenarios = @(
    @{ Name = 'minimal';        XmlDir = 'minimal';       WithNpcs = $true  }
    @{ Name = 'skyrim-cbbe';    XmlDir = 'skyrim-cbbe';   WithNpcs = $false }
    @{ Name = 'fallout4-cbbe';  XmlDir = 'fallout4-cbbe'; WithNpcs = $false }
    @{ Name = 'skyrim-uunp';    XmlDir = 'skyrim-uunp';   WithNpcs = $false }
)

foreach ($s in $scenarios) {
    $outDir = Join-Path $expected $s.Name
    Write-Host "Regenerating $($s.Name) -> $outDir"
    Remove-Item -Recurse -Force $outDir -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null

    $xmlDir = Join-Path $inputs $s.XmlDir
    $jargs = @('-cp', $classpath, 'com.asdasfa.jbs2bg.testharness.FixtureDriver',
               '--xml-dir', $xmlDir, '--out', $outDir)
    if ($s.WithNpcs) {
        $jargs += @('--npcs', (Join-Path $inputs 'npcs/sample-npcs.txt'))
    }

    Push-Location $workdir
    try {
        & $java @jargs
        if ($LASTEXITCODE -ne 0) {
            throw "Driver failed for $($s.Name) (exit $LASTEXITCODE)"
        }
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "Done. Review diffs in tests/fixtures/expected/ before committing."
