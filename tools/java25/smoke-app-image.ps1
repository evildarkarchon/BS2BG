#Requires -Version 7.0
<#
.SYNOPSIS
    Drives the packaged BS2BG launcher through Settings recovery plus the representative Project, BoS, Templates,
    and Morphs workflows from a clean extracted image and proves that it exits cleanly (issues #83, #84, and #97).

.DESCRIPTION
    The archive produced by tools/java25/package-java25.ps1 is extracted to a fresh location and its launcher is
    started from an empty working directory with every host-Java discovery path removed from the environment
    (JAVA_HOME, *_JAVA_OPTIONS, and any Java-looking PATH entry), so the run can only succeed on the bundled runtime.
    The launcher configuration disables jpackage's Windows self-restart, so the original BS2BG.exe process must
    host the JVM. The harness verifies that no second image process exists, that jvm.dll was loaded from the
    extracted runtime, and then drives the application through Windows UI Automation exactly as an assistive
    technology would:

      - controls are located by accessible role (Button, MenuItem, List, ListItem, Edit, CheckBox, TabItem, ...),
        by accessible name (the visible text, or the accessible name the FXML declares for the Project collections),
        by Project-domain identity (Slider Preset, Custom Morph Target, and NPC editor-id names from the fixture),
        and by semantic relationships in the accessibility tree (the field that follows the "Custom Target:" label,
        the output area that precedes the "Omit Redundant Sliders" option, the "Add" button that follows the field);
      - nothing is located by screen coordinates, row index, generated automation id, CSS, or JavaFX internals;
      - native file dialogs owned by the JavaFX window are reached by their title and driven by role and name.

    Workflow: launch once from an empty directory and validate the canonical Settings pair; exit; replace that pair
    with the checked-in legacy Settings fixtures; relaunch and drive the representative Project, BoS, Templates,
    Morphs, and Save As workflows; exit; assemble an interrupted paired Settings publication; relaunch and require
    recovery of the exact prior pair before reopening the saved Project and regenerating its Settings-dependent
    output; then close the window and require all three launcher processes to exit with code 0 within bounded waits.

    Every step is recorded with its duration and observations in the evidence file; the first failure captures the
    UIA tree, the window list, a screenshot, and the process output before the launcher is terminated.

.PARAMETER ArchivePath
    The BS2BG-<version>-windows-x64.zip produced by the packaging script.
.PARAMETER FixtureProject
    A checked-in .jbs2bg Project to open (test-resources/projects/legacy-project-semantics.jbs2bg).
.PARAMETER FixtureStandardSettings
    The checked-in legacy Standard Settings JSON document to install after first-run creation.
.PARAMETER FixtureUunpSettings
    The checked-in legacy UUNP Settings JSON document to install after first-run creation.
.PARAMETER EvidencePath
    Where to write the JSON evidence; diagnostics go to a smoke-diagnostics/ directory beside it.
.PARAMETER ExpectedAppVersion
    The version jpackage stamped; recorded and cross-checked against the launcher configuration.
.PARAMETER WorkRoot
    Clean directory that receives the extracted image and the working directory; a fresh %TEMP% path by default.
.PARAMETER KeepWorkRoot
    Leave the extracted image and working directory in place after the run (for inspection).
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ArchivePath,
    [string]$LauncherName = 'BS2BG',
    [Parameter(Mandatory)] [string]$FixtureProject,
    [Parameter(Mandatory)] [string]$FixtureStandardSettings,
    [Parameter(Mandatory)] [string]$FixtureUunpSettings,
    [Parameter(Mandatory)] [string]$EvidencePath,
    [string]$ExpectedAppVersion = '',
    [string]$WorkRoot = (Join-Path $env:TEMP ("BS2BG-smoke-" + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))),
    [int]$StartupTimeoutSeconds = 90,
    [int]$StepTimeoutSeconds = 30,
    [int]$ExitTimeoutSeconds = 30,
    [switch]$KeepWorkRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'WindowsAppImage.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'UiaAutomation.psm1') -Force

# Application vocabulary the locators rely on: window titles derive from the application name, and the Project
# collections carry the accessible names declared in main.fxml (CONTEXT.md terms).
$applicationTitle = 'jBS2BG'
$openDialogTitle = 'Open jBS2BG File'
$saveDialogTitle = 'Save jBS2BG File'
$bosExportDialogTitle = 'Export BoS JSON File'
$sliderPresetsList = 'Slider Presets'
$customTargetsList = 'Custom Morph Targets'
$targetPresetsList = 'Target Slider Presets'
$npcTable = 'NPC Morph Assignments'

# Fixture identities (test-resources/projects/legacy-project-semantics.jbs2bg).
$fixturePresets = @('CBBE Curvy', 'UUNP Athletic')
$fixtureCustomTarget = 'All|Female'
$fixtureNpcEditorId = 'HousecarlWhiterun'
$fixtureNpcMod = 'Skyrim.esm'
$newCustomTarget = 'All|Female|NordRace'
$openedProjectName = 'representative.jbs2bg'
$savedProjectName = 'smoke-output.jbs2bg'
$bosExportName = 'smoke-bos.json'
$expectedCbbeTemplate = 'CBBE Curvy=Waist@0.74:0.26, Ångström/形@0.0'
$expectedUunpTemplate = 'UUNP Athletic=Arms@0.25:0.75'

$evidenceDir = Split-Path -Parent $EvidencePath
$diagnosticsDir = Join-Path $evidenceDir 'smoke-diagnostics'
New-Item -ItemType Directory -Path $diagnosticsDir -Force | Out-Null
Get-ChildItem -LiteralPath $diagnosticsDir -File | Remove-Item -Force

$startedAt = [DateTimeOffset]::UtcNow
$steps = New-Object System.Collections.Generic.List[object]
$observations = [ordered]@{}
$script:app = $null
$script:stdoutTasks = New-Object System.Collections.Generic.List[object]
$script:stderrTasks = New-Object System.Collections.Generic.List[object]
$script:lifecycles = New-Object System.Collections.Generic.List[object]
$script:mainWindow = $null
$script:firstRunStandardBytes = $null
$script:firstRunUunpBytes = $null
$script:legacyStandardSha256 = $null
$script:legacyUunpSha256 = $null
$imageRoot = Join-Path $WorkRoot 'image'
$workDir = Join-Path $WorkRoot 'work'

<#
.SYNOPSIS
    Prints one indented "[smoke]" progress line (the packaging script's banner style is reserved for its own steps).
#>
function Write-Step {
    param([string]$Message)
    Write-Host "  [smoke] $Message"
}

<#
.SYNOPSIS
    Runs one named workflow step, records its outcome and duration, and rethrows after capturing diagnostics.
#>
function Invoke-Step {
    param([Parameter(Mandatory)] [string]$Name, [Parameter(Mandatory)] [scriptblock]$Action)
    Write-Step "step: $Name"
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $detail = & $Action
        $steps.Add([ordered]@{ name = $Name; passed = $true; seconds = [math]::Round($stopwatch.Elapsed.TotalSeconds, 2); detail = $detail })
        Write-Step "  ok ($([math]::Round($stopwatch.Elapsed.TotalSeconds, 1)) s)"
    }
    catch {
        $steps.Add([ordered]@{ name = $Name; passed = $false; seconds = [math]::Round($stopwatch.Elapsed.TotalSeconds, 2); error = $_.Exception.Message })
        Write-Host "  [smoke]   FAILED: $($_.Exception.Message)" -ForegroundColor Red
        Save-FailureDiagnostics -StepName $Name
        throw
    }
}

<#
.SYNOPSIS
    Captures the UIA tree, the process's windows, and a screenshot when a step fails.
#>
function Save-FailureDiagnostics {
    param([string]$StepName)
    $safe = $StepName -replace '[^A-Za-z0-9-]', '_'
    try {
        if ($script:app -and -not $script:app.HasExited) {
            Get-ProcessTopLevelWindows -ProcessId $script:app.Id | ForEach-Object { "$($_.className) visible=$($_.visible) title='$($_.title)'" } |
                Set-Content -LiteralPath (Join-Path $diagnosticsDir "failure-$safe-windows.txt") -Encoding utf8
            # Every visible window of the process, dialogs included, so a locator failure can be diagnosed offline.
            $index = 0
            foreach ($window in (Get-ProcessTopLevelWindows -ProcessId $script:app.Id | Where-Object { $_.visible })) {
                $index++
                try {
                    Get-UiaTree -Element ([System.Windows.Automation.AutomationElement]::FromHandle([IntPtr]$window.handle)) |
                        Set-Content -LiteralPath (Join-Path $diagnosticsDir "failure-$safe-uia-tree-$index.txt") -Encoding utf8
                }
                catch {
                    # A window that vanished between enumeration and dump is not worth failing the diagnostics over.
                }
            }
        }
    }
    catch {
        # Diagnostics are best effort; the original step failure is what gets reported.
    }
    Save-Screenshot -Path (Join-Path $diagnosticsDir "failure-$safe.png")
}

<#
.SYNOPSIS
    Best-effort screenshot of the virtual screen (System.Drawing is available on Windows PowerShell 7).
#>
function Save-Screenshot {
    param([string]$Path)
    try {
        Add-Type -AssemblyName System.Drawing
        Add-Type -AssemblyName System.Windows.Forms
        $bounds = [System.Windows.Forms.SystemInformation]::VirtualScreen
        $bitmap = New-Object System.Drawing.Bitmap($bounds.Width, $bounds.Height)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.CopyFromScreen($bounds.Left, $bounds.Top, 0, 0, $bitmap.Size)
            $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
        }
        finally {
            $graphics.Dispose()
            $bitmap.Dispose()
        }
    }
    catch {
        # A headless or locked session cannot capture the screen; the textual diagnostics still stand.
    }
}

<#
.SYNOPSIS
    Waits for the application's main window with the given exact title.
#>
function Wait-MainWindow {
    param([string]$Title, [int]$TimeoutSeconds = $StepTimeoutSeconds)
    $script:mainWindow = Wait-UiaWindow -ProcessId $script:app.Id -Title $Title -TimeoutSeconds $TimeoutSeconds
    return $script:mainWindow
}

<#
.SYNOPSIS
    Returns every BS2BG launcher process whose executable belongs to the extracted smoke image.
.OUTPUTS
    Process objects; an empty stream before the image path has been recorded or when none remain.
.NOTES
    Centralizes the exact launcher-name and image-path boundary used by startup, bounded exit, and failure cleanup.
#>
function Get-ImageLauncherProcesses {
    if (-not $observations.Contains('extractedImage')) { return }
    Get-Process -Name $LauncherName -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -and $_.Path.StartsWith($observations['extractedImage'], [System.StringComparison]::OrdinalIgnoreCase) }
}

<#
.SYNOPSIS
    Triggers a File-menu command through the accelerator the application declares for it.
.NOTES
    A UIA Invoke of a JavaFX menu item runs the command inside the UIA callback on the application thread, and
    the modal FileChooser it opens then cannot be automated by any client until it closes (see Invoke-UiaElement).
    The accelerators are the ones MainController.setupKeyCombinations binds: New Ctrl+N, Open Ctrl+O,
    Save As Ctrl+Alt+S. The menu items themselves are still verified to exist by role and name.
#>
function Send-FileCommand {
    param([string]$Item, [string]$DialogTitle = '')
    $accelerators = @{ 'New' = '^n'; 'Open…' = '^o'; 'Save As…' = '^%s' }
    if (-not $accelerators.ContainsKey($Item)) { throw "No accelerator is known for File > $Item" }
    $fileMenu = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'MenuItem' -Name 'File') -Description "'File' menu" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $fileMenu
    Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'MenuItem' -Name $Item) -Description "'$Item' item of the 'File' menu" -TimeoutSeconds $StepTimeoutSeconds | Out-Null
    # Close the menu again before sending the accelerator so the key sequence reaches the scene, not the popup.
    Send-UiaAccelerator -Window $script:mainWindow -Keys '{ESC}'
    Send-UiaAccelerator -Window $script:mainWindow -Keys $accelerators[$Item]
    if ($DialogTitle) {
        # A keystroke can be lost if the foreground changes at the wrong moment; one bounded re-send is allowed,
        # and the dialog's absence after that is a real failure that the caller's wait reports.
        try {
            Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $DialogTitle -TimeoutSeconds 8 | Out-Null
        }
        catch {
            Write-Step "  '$DialogTitle' did not appear within 8 s; re-sending the accelerator once"
            Send-UiaAccelerator -Window $script:mainWindow -Keys $accelerators[$Item]
        }
    }
}

<#
.SYNOPSIS
    Completes a native file dialog owned by the application: types the full path into "File name:" and confirms.
#>
function Complete-FileDialog {
    param([string]$Title, [string]$Path, [string]$ConfirmButton)
    $dialog = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $Title -TimeoutSeconds $StepTimeoutSeconds
    $fileName = Wait-UiaElement -Root $dialog -Condition (New-UiaCondition -ControlType 'Edit' -Name 'File name:') -Description "'File name:' field of '$Title'" -TimeoutSeconds $StepTimeoutSeconds
    Set-UiaValue -Element $fileName -Value $Path
    # The common dialog's Open/Save control is a Win32 split button; UIA's legacy proxy exposes it as a Pane
    # without patterns (older proxies: Button or SplitButton). It is located by name in any of those roles and
    # activated through Invoke when offered, otherwise through the button's own window handle.
    $confirmRole = New-Object System.Windows.Automation.OrCondition(@(
        (New-UiaCondition -ControlType 'Pane' -Name $ConfirmButton),
        (New-UiaCondition -ControlType 'SplitButton' -Name $ConfirmButton),
        (New-UiaCondition -ControlType 'Button' -Name $ConfirmButton)))
    $confirm = Wait-UiaElement -Root $dialog -Condition $confirmRole -Description "'$ConfirmButton' control of '$Title'" -TimeoutSeconds $StepTimeoutSeconds
    $invoke = $null
    if ($confirm.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$invoke)) {
        $invoke.Invoke()
    }
    else {
        Invoke-UiaNativeButton -Element $confirm
    }
}

<#
.SYNOPSIS
    Names of the items currently exposed by the list with the given accessible name.
#>
function Get-ListItemNames {
    param([string]$ListName)
    $list = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'List' -Name $ListName) -Description "'$ListName' list" -TimeoutSeconds $StepTimeoutSeconds
    return @(Find-UiaElements -Root $list -Condition (New-UiaCondition -ControlType 'ListItem') | ForEach-Object { $_.Current.Name })
}

<#
.SYNOPSIS
    Waits until the named list exposes every expected item.
#>
function Wait-ListItems {
    param([string]$ListName, [string[]]$Expected)
    return Wait-UiaCondition -Description "'$ListName' to list $($Expected -join ', ')" -TimeoutSeconds $StepTimeoutSeconds -Test {
        $names = Get-ListItemNames -ListName $ListName
        $missing = @($Expected | Where-Object { $names -cnotcontains $_ })
        if ($missing.Count -eq 0) { , $names }
    }
}

<#
.SYNOPSIS
    Selects the named item in the named list.
#>
function Select-ListItem {
    param([string]$ListName, [string]$ItemName)
    $list = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'List' -Name $ListName) -Description "'$ListName' list" -TimeoutSeconds $StepTimeoutSeconds
    $item = Wait-UiaElement -Root $list -Condition (New-UiaCondition -ControlType 'ListItem' -Name $ItemName) -Description "'$ItemName' in '$ListName'" -TimeoutSeconds $StepTimeoutSeconds
    Select-UiaElement -Element $item
}

<#
.SYNOPSIS
    Finds a control by role and name and returns its outermost element.
.NOTES
    JavaFX exposes a Labeled control as an element with an identically named inner text element, and a
    descendant search may return the inner one, which has no siblings. Climbing to the outermost element with
    the same role and name makes sibling relationships (label -> field, label -> counter) reliable.
#>
function Find-OuterControl {
    param([string]$ControlType, [string]$Name)
    $element = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType $ControlType -Name $Name) -Description "'$Name' $ControlType" -TimeoutSeconds $StepTimeoutSeconds
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    while ($true) {
        $parent = $walker.GetParent($element)
        if ($null -eq $parent -or $parent.Current.Name -cne $Name -or $parent.Current.ControlType -ne $element.Current.ControlType) { return $element }
        $element = $parent
    }
}

<#
.SYNOPSIS
    The nearest preceding sibling of the given control type (e.g. the output area before an option checkbox).
#>
function Get-PrecedingControl {
    param($Element, [string]$ControlType, [int]$MaxHops = 6)
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    $visited = @()
    $current = $walker.GetPreviousSibling($Element)
    for ($hop = 0; $hop -lt $MaxHops -and $null -ne $current; $hop++) {
        $type = Get-UiaRoleName -Element $current
        $visited += "$type '$($current.Current.Name)'"
        if ($type -eq $ControlType) { return $current }
        $current = $walker.GetPreviousSibling($current)
    }
    throw "No $ControlType precedes '$($Element.Current.Name)' within $MaxHops siblings (parent: $(Get-ParentDescription $Element); walked: $($visited -join ' <- '))."
}

<#
.SYNOPSIS
    "Role 'Name'" of an element's control-view parent, for relationship failure messages.
#>
function Get-ParentDescription {
    param($Element)
    $parent = [System.Windows.Automation.TreeWalker]::ControlViewWalker.GetParent($Element)
    if ($null -eq $parent) { return '<none>' }
    return "$(Get-UiaRoleName -Element $parent) '$($parent.Current.Name)'"
}

<#
.SYNOPSIS
    The nearest following sibling with the given control type and, optionally, name.
#>
function Get-FollowingControl {
    param($Element, [string]$ControlType, [string]$Name = $null, [int]$MaxHops = 6)
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    $visited = @()
    $current = $walker.GetNextSibling($Element)
    for ($hop = 0; $hop -lt $MaxHops -and $null -ne $current; $hop++) {
        $type = Get-UiaRoleName -Element $current
        $visited += "$type '$($current.Current.Name)'"
        # A [string] parameter turns $null into '', so "any name" is the empty string here.
        if ($type -eq $ControlType -and ([string]::IsNullOrEmpty($Name) -or $current.Current.Name -ceq $Name)) { return $current }
        $current = $walker.GetNextSibling($current)
    }
    throw "No $ControlType$(if (-not [string]::IsNullOrEmpty($Name)) { " '$Name'" }) follows '$($Element.Current.Name)' within $MaxHops siblings (parent: $(Get-ParentDescription $Element); walked: $($visited -join ' -> '))."
}

<#
.SYNOPSIS
    Polls a text control until its value satisfies the predicate; returns the text.
#>
function Wait-Text {
    param($Element, [scriptblock]$Predicate, [string]$Description)
    return Wait-UiaCondition -Description $Description -TimeoutSeconds $StepTimeoutSeconds -Test {
        $text = Get-UiaText -Element $Element
        if (& $Predicate $text) { $text }
    }
}

<#
.SYNOPSIS
    Flattens generated output to one line (newlines become " | ") and truncates it for the evidence and log lines.
#>
function Get-Excerpt {
    param([string]$Text, [int]$MaxChars = 400)
    $flat = $Text -replace "`r?`n", ' | '
    if ($flat.Length -le $MaxChars) { return $flat }
    return $flat.Substring(0, $MaxChars) + '...'
}

<#
.SYNOPSIS
    Asserts that the NPC Morph Assignment table exposes the fixture NPC by editor id and mod name.
#>
function Assert-NpcRow {
    $table = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'Table' -Name $npcTable) -Description "'$npcTable' table" -TimeoutSeconds $StepTimeoutSeconds
    $editorIdCell = Wait-UiaElement -Root $table -Condition (New-UiaCondition -Name $fixtureNpcEditorId) -Description "NPC '$fixtureNpcEditorId' in '$npcTable'" -TimeoutSeconds $StepTimeoutSeconds
    $row = Get-UiaParent -Element $editorIdCell
    $modCell = Find-UiaElement -Root $row -Condition (New-UiaCondition -Name $fixtureNpcMod)
    if ($null -eq $modCell) {
        # Some toolkits expose cells flat under the table; the mod name must then at least be a table descendant.
        $modCell = Find-UiaElement -Root $table -Condition (New-UiaCondition -Name $fixtureNpcMod)
    }
    if ($null -eq $modCell) { throw "NPC row for '$fixtureNpcEditorId' does not expose its mod '$fixtureNpcMod'." }
    return "row exposes '$fixtureNpcEditorId' ($($editorIdCell.Current.ControlType.ProgrammaticName)) and '$fixtureNpcMod'"
}

$passed = $false
try {

Invoke-Step -Name 'extract-clean-image' -Action {
    if (-not (Test-Path -LiteralPath $ArchivePath)) { throw "Archive not found: $ArchivePath" }
    if (Test-Path -LiteralPath $WorkRoot) { Remove-Item -LiteralPath $WorkRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $imageRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $workDir -Force | Out-Null
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $imageRoot -Force
    $launcherPath = Join-Path (Join-Path $imageRoot $LauncherName) "$LauncherName.exe"
    if (-not (Test-Path -LiteralPath $launcherPath)) { throw "Extracted archive has no $LauncherName\$LauncherName.exe under $imageRoot" }
    $config = Read-LauncherConfig -Path (Join-Path (Join-Path $imageRoot $LauncherName) "app\$LauncherName.cfg")
    Assert-LauncherSingleProcessMode -Config $config
    $javaOptions = @()
    if ($config.Contains('JavaOptions') -and $config['JavaOptions'].Contains('java-options')) { $javaOptions = @($config['JavaOptions']['java-options']) }
    if ($ExpectedAppVersion -and ($javaOptions -cnotcontains "-Djpackage.app-version=$ExpectedAppVersion")) {
        throw "Extracted launcher configuration does not stamp app version $ExpectedAppVersion."
    }
    Copy-Item -LiteralPath $FixtureProject -Destination (Join-Path $workDir $openedProjectName)
    $observations['extractedImage'] = Join-Path $imageRoot $LauncherName
    $observations['launcher'] = $launcherPath
    $observations['launcherSha256'] = (Get-FileHash -LiteralPath $launcherPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $observations['expectedProcessModel'] = 'single-launcher-process'
    $observations['workingDirectory'] = $workDir
    $observations['archiveSha256'] = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    "extracted $ArchivePath to $imageRoot; working directory $workDir holds only $openedProjectName"
}

<#
.SYNOPSIS
    Starts the packaged launcher from the extracted image with a scrubbed environment and waits for its window.
.PARAMETER ObservationKey
    Evidence key under which this lifecycle's startup observation is recorded.
.OUTPUTS
    A summary string; sets $script:app and records its startup under the given observation key.
.NOTES
    Owns the returned process in $script:app until Stop-PackagedApplication completes. Throws when startup, the
    one-image-process contract, or bundled JVM/native-library verification fails. Used once for each of the three
    sequential application lifecycles.
#>
function Start-PackagedApplication {
    param([string]$ObservationKey)
    # A later lifecycle must establish its own proof; never let a failed relaunch inherit the first launch's model.
    $observations.Remove('observedProcessModel')
    $scrubbed = Get-ScrubbedEnvironment -Environment ([System.Environment]::GetEnvironmentVariables())
    if (-not $observations.Contains('environment')) {
        $observations['environment'] = [ordered]@{
            removedVariables   = $scrubbed.RemovedVariables
            removedPathEntries = $scrubbed.RemovedPathEntries
            # The remaining PATH is a host detail, not evidence; only its size is recorded.
            keptPathEntryCount = @($scrubbed.Variables['PATH'].Split(';', [System.StringSplitOptions]::RemoveEmptyEntries)).Count
        }
    }
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $observations['launcher']
    $startInfo.WorkingDirectory = $workDir
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Environment.Clear()
    foreach ($name in $scrubbed.Variables.Keys) { $startInfo.Environment[$name] = "$($scrubbed.Variables[$name])" }
    $script:app = [System.Diagnostics.Process]::Start($startInfo)
    $script:stdoutTasks.Add($script:app.StandardOutput.ReadToEndAsync())
    $script:stderrTasks.Add($script:app.StandardError.ReadToEndAsync())
    $launchedAt = [DateTimeOffset]::UtcNow

    Wait-MainWindow -Title $applicationTitle -TimeoutSeconds $StartupTimeoutSeconds | Out-Null
    $windowAt = [DateTimeOffset]::UtcNow

    $imageDir = $observations['extractedImage']
    $imageProcesses = @(Get-ImageLauncherProcesses)
    if ($imageProcesses.Count -ne 1 -or $imageProcesses[0].Id -ne $script:app.Id) {
        throw "Expected launcher pid $($script:app.Id) to be the only $LauncherName process from the extracted image (found: $(($imageProcesses | ForEach-Object { $_.Id }) -join ', '))."
    }
    $observations['observedProcessModel'] = 'single-launcher-process'
    $runtimeModules = @()
    foreach ($module in $script:app.Modules) {
        if ($module.ModuleName -match '^(jvm|java|jli|jimage|glass|prism_d3d|prism_sw|javafx_font)\.dll$') {
            $runtimeModules += [ordered]@{ module = $module.ModuleName; path = $module.FileName }
        }
    }
    $jvm = @($runtimeModules | Where-Object { $_.module -eq 'jvm.dll' })
    if ($jvm.Count -ne 1) { throw "jvm.dll is not loaded in the application process (found: $(($runtimeModules | ForEach-Object { $_.module }) -join ', '))." }
    $outside = @($runtimeModules | Where-Object { -not $_.path.StartsWith($imageDir, [System.StringComparison]::OrdinalIgnoreCase) })
    if ($outside.Count -gt 0) { throw "Runtime libraries were loaded from outside the extracted image: $(($outside | ForEach-Object { $_.path }) -join ', ')" }
    $settings = @(Get-ChildItem -LiteralPath $workDir -File | Where-Object { $_.Name -like 'settings*.json' } | ForEach-Object { $_.Name })
    $observations[$ObservationKey] = [ordered]@{
        processModel        = 'single-launcher-process'
        applicationPid       = $script:app.Id
        applicationExe       = $script:app.MainModule.FileName
        startupSeconds       = [math]::Round(($windowAt - $launchedAt).TotalSeconds, 2)
        runtimeModules       = $runtimeModules
        settingsFilesCreated = $settings
    }
    return "application pid $($script:app.Id) hosts the JVM in the launcher process; jvm.dll from $($jvm[0].path); window '$applicationTitle' after $([math]::Round(($windowAt - $launchedAt).TotalSeconds, 1)) s; settings present: $($settings -join ', ')"
}

<#
.SYNOPSIS
    Closes the main window through its Window pattern and requires the launcher process to exit with code 0.
.PARAMETER ObservationKey
    Evidence key under which this lifecycle's bounded exit observation is recorded.
.OUTPUTS
    A summary string; records the exit under the given observation key and appends it to the lifecycle list.
.NOTES
    Completes ownership of $script:app. Throws when the process misses the timeout, returns non-zero, or leaves any
    BS2BG process running from the extracted image.
#>
function Stop-PackagedApplication {
    param([string]$ObservationKey)
    $closeRequestedAt = [DateTimeOffset]::UtcNow
    Close-UiaWindow -Window $script:mainWindow
    $appExited = $script:app.WaitForExit($ExitTimeoutSeconds * 1000)
    $exitedAt = [DateTimeOffset]::UtcNow
    if (-not $appExited) {
        throw "The packaged process did not exit within $ExitTimeoutSeconds s after the close request."
    }
    $exitCode = $script:app.ExitCode
    if ($exitCode -ne 0) { throw "The packaged launcher exited with code $exitCode; expected 0." }
    $leftovers = @(Get-ImageLauncherProcesses)
    if ($leftovers.Count -gt 0) { throw "Processes from the extracted image are still running: $(($leftovers | ForEach-Object { $_.Id }) -join ', ')" }
    $exit = [ordered]@{
        applicationPid      = $script:app.Id
        closeRequestedAtUtc = $closeRequestedAt.ToString('o')
        exitCode            = $exitCode
        exitWaitSeconds     = [math]::Round(($exitedAt - $closeRequestedAt).TotalSeconds, 2)
        boundedBySeconds    = $ExitTimeoutSeconds
    }
    $observations[$ObservationKey] = $exit
    $script:lifecycles.Add($exit)
    return "exit code $exitCode after $([math]::Round(($exitedAt - $closeRequestedAt).TotalSeconds, 2)) s (bound $ExitTimeoutSeconds s)"
}

Invoke-Step -Name 'launch-packaged-launcher-without-system-java' -Action {
    Start-PackagedApplication -ObservationKey 'firstRunLaunch'
}

Invoke-Step -Name 'verify-first-run-canonical-settings-pair' -Action {
    $standard = Join-Path $workDir 'settings.json'
    $uunp = Join-Path $workDir 'settings_UUNP.json'
    if (-not (Test-Path -LiteralPath $standard -PathType Leaf) -or
        -not (Test-Path -LiteralPath $uunp -PathType Leaf)) {
        throw 'First-run startup did not create both Settings files.'
    }
    $settingsFiles = @(Get-ChildItem -LiteralPath $workDir -File -Filter 'settings*.json' | ForEach-Object { $_.Name })
    if ($settingsFiles.Count -ne 2 -or $settingsFiles -cnotcontains 'settings.json' -or
        $settingsFiles -cnotcontains 'settings_UUNP.json') {
        throw "First-run startup created an unexpected Settings file set: $($settingsFiles -join ', ')."
    }
    $staging = @(Get-ChildItem -LiteralPath $workDir -Force | Where-Object { $_.Name -like '.bs2bg-settings-stage-*' })
    if ($staging.Count -ne 0) { throw 'First-run paired publication left transaction state behind.' }

    $script:firstRunStandardBytes = [System.IO.File]::ReadAllBytes($standard)
    $script:firstRunUunpBytes = [System.IO.File]::ReadAllBytes($uunp)
    foreach ($entry in @(
        [ordered]@{ name = 'settings.json'; bytes = $script:firstRunStandardBytes },
        [ordered]@{ name = 'settings_UUNP.json'; bytes = $script:firstRunUunpBytes })) {
        if ($entry.bytes.Length -lt 2 -or $entry.bytes[-1] -ne 0x0A) {
            throw "$($entry.name) is not terminated by the canonical LF byte."
        }
        if ($entry.bytes.Length -ge 3 -and $entry.bytes[0] -eq 0xEF -and
            $entry.bytes[1] -eq 0xBB -and $entry.bytes[2] -eq 0xBF) {
            throw "$($entry.name) contains an unexpected UTF-8 BOM."
        }
    }
    $standardJson = Get-Content -LiteralPath $standard -Raw | ConvertFrom-Json
    $uunpJson = Get-Content -LiteralPath $uunp -Raw | ConvertFrom-Json
    if ([single]$standardJson.Defaults.Breasts.valueSmall -ne [single]0.2 -or
        [single]$standardJson.Defaults.Waist.valueBig -ne [single]1.0 -or
        @($standardJson.Inverted) -cnotcontains 'Breasts') {
        throw 'First-run Standard Settings do not preserve the accepted built-in defaults and inversion family.'
    }
    if ([single]$uunpJson.Defaults.Arms.valueSmall -ne [single]1.0 -or
        [single]$uunpJson.Defaults.Arms.valueBig -ne [single]1.0 -or
        @($uunpJson.Inverted) -cnotcontains 'Arms') {
        throw 'First-run UUNP Settings do not preserve the accepted built-in defaults and inversion family.'
    }
    $observations['firstRunSettings'] = [ordered]@{
        files = $settingsFiles
        standardSha256 = (Get-FileHash -LiteralPath $standard -Algorithm SHA256).Hash.ToLowerInvariant()
        uunpSha256 = (Get-FileHash -LiteralPath $uunp -Algorithm SHA256).Hash.ToLowerInvariant()
        canonicalUtf8NoBomWithFinalLf = $true
    }
    "created canonical UTF-8 pair $($settingsFiles -join ', ') with no transaction state"
}

Invoke-Step -Name 'exit-after-first-run-settings-creation' -Action {
    Stop-PackagedApplication -ObservationKey 'exitAfterFirstRunSettingsCreation'
}

Invoke-Step -Name 'install-legacy-settings-edit' -Action {
    foreach ($fixture in @($FixtureStandardSettings, $FixtureUunpSettings)) {
        if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) { throw "Settings fixture not found: $fixture" }
    }
    $standard = Join-Path $workDir 'settings.json'
    $uunp = Join-Path $workDir 'settings_UUNP.json'
    Copy-Item -LiteralPath $FixtureStandardSettings -Destination $standard -Force
    Copy-Item -LiteralPath $FixtureUunpSettings -Destination $uunp -Force
    $script:legacyStandardSha256 = (Get-FileHash -LiteralPath $standard -Algorithm SHA256).Hash.ToLowerInvariant()
    $script:legacyUunpSha256 = (Get-FileHash -LiteralPath $uunp -Algorithm SHA256).Hash.ToLowerInvariant()
    $observations['legacySettingsEdit'] = [ordered]@{
        standardFixture = $FixtureStandardSettings
        uunpFixture = $FixtureUunpSettings
        standardSha256 = $script:legacyStandardSha256
        uunpSha256 = $script:legacyUunpSha256
    }
    "installed legacy pair: Standard sha256 $script:legacyStandardSha256; UUNP sha256 $script:legacyUunpSha256"
}

Invoke-Step -Name 'launch-after-legacy-settings-edit' -Action {
    $launch = Start-PackagedApplication -ObservationKey 'legacySettingsLaunch'
    $standardHash = (Get-FileHash -LiteralPath (Join-Path $workDir 'settings.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    $uunpHash = (Get-FileHash -LiteralPath (Join-Path $workDir 'settings_UUNP.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($standardHash -cne $script:legacyStandardSha256 -or $uunpHash -cne $script:legacyUunpSha256) {
        throw 'Startup did not retain the externally edited legacy Settings pair byte-for-byte.'
    }
    "$launch; retained the externally edited legacy Settings pair"
}

Invoke-Step -Name 'open-representative-project' -Action {
    Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
    Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $openedProjectName) -ConfirmButton 'Open'
    Wait-MainWindow -Title "$applicationTitle - $openedProjectName" | Out-Null
    $presets = Wait-ListItems -ListName $sliderPresetsList -Expected $fixturePresets
    $observations['openedProject'] = [ordered]@{ file = $openedProjectName; sliderPresets = $presets }
    "title '$applicationTitle - $openedProjectName'; $sliderPresetsList lists $($presets -join ', ')"
}

Invoke-Step -Name 'generate-preview-copy-and-export-bos-artifact' -Action {
    $presetName = $fixturePresets[0]
    Select-ListItem -ListName $sliderPresetsList -ItemName $presetName
    $view = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'Button' -Name 'View BoS JSON') -Description "'View BoS JSON' button" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $view

    $popupTitle = "BodyTypes of Skyrim JSON: $presetName"
    # JavaFX exposes an owned Stage beneath its owner in UIA's control view even though it is a native top-level
    # window, so resolve it by the same titled-handle path used for native dialogs.
    $popup = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $popupTitle -TimeoutSeconds $StepTimeoutSeconds
    Wait-UiaElement -Root $popup -Condition (New-UiaCondition -ControlType 'Edit') -Description "BoS preview in '$popupTitle'" -TimeoutSeconds $StepTimeoutSeconds | Out-Null
    $previewControls = @(Find-UiaElements -Root $popup -Condition (New-UiaCondition -ControlType 'Edit'))
    if ($previewControls.Count -ne 1) { throw "'$popupTitle' exposes $($previewControls.Count) Edit controls; expected one BoS preview." }
    $previewControl = $previewControls[0]
    $preview = Get-UiaText -Element $previewControl
    $parsed = $preview | ConvertFrom-Json
    if ($parsed.string.bodyname -cne $presetName) {
        throw "BoS preview bodyname '$($parsed.string.bodyname)' does not match '$presetName'."
    }
    if ([int]$parsed.int.slidersnumber -lt 1) {
        throw "BoS preview for '$presetName' has no slider values."
    }
    if ($parsed.string.slidername1 -cne 'Waist' -or [single]$parsed.float.highvalue1 -ne [single]0.2 -or
        [single]$parsed.float.lowvalue1 -ne [single]0.8) {
        throw 'BoS preview did not consume the legacy Standard Settings inversion for Waist.'
    }
    Get-UiaTree -Element $popup | Set-Content -LiteralPath (Join-Path $diagnosticsDir 'uia-tree-bos-preview.txt') -Encoding utf8

    Set-Clipboard -Value ''
    $copy = Wait-UiaElement -Root $popup -Condition (New-UiaCondition -ControlType 'Button' -Name 'Copy') -Description "'Copy' button in '$popupTitle'" -TimeoutSeconds $StepTimeoutSeconds
    # Copy opens the application's modal notification, so send Enter to the focused, located button instead of
    # holding JavaFX's application thread inside a synchronous UIA Invoke callback.
    Send-UiaKeysToElement -Element $copy -Keys '{ENTER}'
    $notification = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title 'Notification' -TimeoutSeconds $StepTimeoutSeconds
    $copied = Get-Clipboard -Raw
    # Windows CF_UNICODETEXT represents line boundaries as CRLF. Normalize only that platform representation;
    # every other character must still match the canonical LF artifact exactly.
    $normalizedClipboard = $copied.Replace("`r`n", "`n")
    if ($normalizedClipboard -cne $preview) {
        throw 'BoS clipboard content differs from the displayed canonical artifact after Windows newline normalization.'
    }
    $ok = Wait-UiaElement -Root $notification -Condition (New-UiaCondition -ControlType 'Button' -Name 'OK') -Description "'OK' button in BoS copy notification" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $ok

    $exportPath = Join-Path $workDir $bosExportName
    $export = Wait-UiaElement -Root $popup -Condition (New-UiaCondition -ControlType 'Button' -Name 'Export') -Description "'Export' button in '$popupTitle'" -TimeoutSeconds $StepTimeoutSeconds
    # Export enters the native save dialog's modal loop and therefore uses the same focused-key activation rule.
    Send-UiaKeysToElement -Element $export -Keys '{ENTER}'
    Complete-FileDialog -Title $bosExportDialogTitle -Path $exportPath -ConfirmButton 'Save'
    Wait-UiaCondition -Description "BoS export '$bosExportName'" -TimeoutSeconds $StepTimeoutSeconds -Test {
        if (Test-Path -LiteralPath $exportPath -PathType Leaf) { Get-Item -LiteralPath $exportPath }
    } | Out-Null
    $exportedBytes = [System.IO.File]::ReadAllBytes($exportPath)
    $previewBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($preview)
    if ([Convert]::ToBase64String($exportedBytes) -cne [Convert]::ToBase64String($previewBytes)) {
        throw 'Exported BoS bytes differ from the displayed canonical artifact.'
    }
    if ($exportedBytes.Length -ge 3 -and $exportedBytes[0] -eq 0xEF -and $exportedBytes[1] -eq 0xBB -and $exportedBytes[2] -eq 0xBF) {
        throw 'Exported BoS artifact contains an unexpected UTF-8 BOM.'
    }
    if ($exportedBytes[-1] -eq 0x0A -or $exportedBytes[-1] -eq 0x0D) {
        throw 'Exported BoS artifact contains an unexpected final newline.'
    }
    $back = Wait-UiaElement -Root $popup -Condition (New-UiaCondition -ControlType 'Button' -Name 'Back') -Description "'Back' button in '$popupTitle'" -TimeoutSeconds $StepTimeoutSeconds
    Wait-UiaCondition -Description "enabled 'Back' button in '$popupTitle'" -TimeoutSeconds $StepTimeoutSeconds -Test {
        if ($back.Current.IsEnabled) { $true }
    } | Out-Null
    Invoke-UiaElement -Element $back

    $observations['bosArtifact'] = [ordered]@{
        sliderPreset = $presetName
        file = $bosExportName
        bytes = $exportedBytes.Length
        sha256 = (Get-FileHash -LiteralPath $exportPath -Algorithm SHA256).Hash.ToLowerInvariant()
        clipboardContentExactAfterWindowsNewlineNormalization = $true
        previewExportBytesExact = $true
    }
    "BoS '$presetName': preview/export bytes exact and clipboard content exact after Windows newline normalization, $($exportedBytes.Length) UTF-8 bytes, sha256 $($observations['bosArtifact'].sha256)"
}

Invoke-Step -Name 'generate-templates-output' -Action {
    $option = Find-OuterControl -ControlType 'CheckBox' -Name 'Omit Redundant Sliders'
    # The generated Templates output is the text area that immediately precedes the option that controls it.
    $output = Get-PrecedingControl -Element $option -ControlType 'Edit'
    $before = Get-UiaText -Element $output
    if ($before.Trim()) { throw "Templates output is not empty before generation: $(Get-Excerpt $before)" }
    $generate = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'Button' -Name 'Generate Templates') -Description "'Generate Templates' button" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $generate
    $text = Wait-Text -Element $output -Description 'Templates output to name every Slider Preset' -Predicate { param($t) @($fixturePresets | Where-Object { $t -notmatch [regex]::Escape("$_=") }).Count -eq 0 }
    $templateLines = @($text -split "`r?`n")
    if ($templateLines -cnotcontains $expectedCbbeTemplate -or $templateLines -cnotcontains $expectedUunpTemplate) {
        throw "Templates output did not preserve the edited Settings math: $(Get-Excerpt $text 240)"
    }
    $observations['templatesOutput'] = [ordered]@{
        length = $text.Length
        lines = $templateLines.Count
        excerpt = Get-Excerpt $text
        standardSettingsLine = $expectedCbbeTemplate
        uunpSettingsLine = $expectedUunpTemplate
    }
    "Templates output: $($text.Length) chars, $(@($text -split "`r?`n").Count) lines: $(Get-Excerpt $text 160)"
}

Invoke-Step -Name 'load-representative-morph-content' -Action {
    $tab = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'TabItem' -Name 'Morphs') -Description "'Morphs' tab" -TimeoutSeconds $StepTimeoutSeconds
    Select-UiaElement -Element $tab
    $targets = Wait-ListItems -ListName $customTargetsList -Expected @($fixtureCustomTarget)
    $npc = Assert-NpcRow
    $observations['loadedMorphContent'] = [ordered]@{ customMorphTargets = $targets; npcMorphAssignment = "$fixtureNpcMod / $fixtureNpcEditorId" }
    "$customTargetsList lists $($targets -join ', '); $npc"
}

Invoke-Step -Name 'create-custom-morph-target' -Action {
    $label = Find-OuterControl -ControlType 'Text' -Name 'Custom Target:'
    # The Custom Target field is the edit that follows its label; the Add button is the button that follows the field.
    $field = Get-FollowingControl -Element $label -ControlType 'Edit'
    Set-UiaValue -Element $field -Value $newCustomTarget
    $add = Get-FollowingControl -Element $field -ControlType 'Button' -Name 'Add'
    Invoke-UiaElement -Element $add
    $targets = Wait-ListItems -ListName $customTargetsList -Expected @($fixtureCustomTarget, $newCustomTarget)
    Wait-MainWindow -Title "$applicationTitle - *$openedProjectName" | Out-Null
    $observations['createdCustomMorphTarget'] = $newCustomTarget
    "$customTargetsList lists $($targets -join ', '); title shows the unsaved marker"
}

Invoke-Step -Name 'assign-slider-presets-to-target' -Action {
    Select-ListItem -ListName $customTargetsList -ItemName $newCustomTarget
    $addAll = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'Button' -Name 'Add All') -Description "'Add All' button" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $addAll
    $assigned = Wait-ListItems -ListName $targetPresetsList -Expected $fixturePresets
    $countLabel = Find-OuterControl -ControlType 'Text' -Name 'Count:'
    $counter = Get-FollowingControl -Element $countLabel -ControlType 'Text'
    $count = Wait-Text -Element $counter -Description "the preset counter to read $($fixturePresets.Count)" -Predicate { param($t) $t.Trim() -eq "$($fixturePresets.Count)" }
    $observations['assignedSliderPresets'] = [ordered]@{ target = $newCustomTarget; presets = $assigned; count = $count.Trim() }
    "'$newCustomTarget' has $($count.Trim()) Slider Presets: $($assigned -join ', ')"
}

Invoke-Step -Name 'generate-morphs-output' -Action {
    $copy = Find-OuterControl -ControlType 'Button' -Name 'Copy'
    # The generated Morphs output is the text area that precedes the Copy / Generate Morphs buttons acting on it.
    $output = Get-PrecedingControl -Element $copy -ControlType 'Edit'
    $generate = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'Button' -Name 'Generate Morphs') -Description "'Generate Morphs' button" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $generate
    $text = Wait-Text -Element $output -Description 'Morphs output to name the Custom Morph Targets and the NPC' -Predicate {
        param($t)
        $t.Contains("$newCustomTarget=") -and $t.Contains("$fixtureCustomTarget=") -and $t.Contains("$fixtureNpcMod|")
    }
    $observations['morphsOutput'] = [ordered]@{ length = $text.Length; lines = @($text -split "`r?`n").Count; excerpt = Get-Excerpt $text }
    "Morphs output: $($text.Length) chars, $(@($text -split "`r?`n").Count) lines: $(Get-Excerpt $text 160)"
}

Invoke-Step -Name 'save-project-as' -Action {
    $savedPath = Join-Path $workDir $savedProjectName
    Send-FileCommand -Item 'Save As…' -DialogTitle $saveDialogTitle
    Complete-FileDialog -Title $saveDialogTitle -Path $savedPath -ConfirmButton 'Save'
    Wait-MainWindow -Title "$applicationTitle - $savedProjectName" | Out-Null
    if (-not (Test-Path -LiteralPath $savedPath)) { throw "Saved Project file not found: $savedPath" }
    $json = Get-Content -LiteralPath $savedPath -Raw | ConvertFrom-Json
    $savedTargets = @($json.CustomMorphTargets.PSObject.Properties.Name)
    if ($savedTargets -cnotcontains $newCustomTarget) { throw "Saved Project lacks Custom Morph Target '$newCustomTarget' (has: $($savedTargets -join ', '))." }
    $savedPresets = @($json.CustomMorphTargets.$newCustomTarget.SliderPresets)
    if ($savedPresets.Count -ne $fixturePresets.Count) { throw "Saved '$newCustomTarget' has $($savedPresets.Count) Slider Presets, expected $($fixturePresets.Count)." }
    $savedNpcs = @($json.MorphedNPCs.PSObject.Properties.Value | ForEach-Object { "$($_.Mod) / $($_.EditorId)" })
    $observations['savedProject'] = [ordered]@{
        file               = $savedProjectName
        sha256             = (Get-FileHash -LiteralPath $savedPath -Algorithm SHA256).Hash.ToLowerInvariant()
        customMorphTargets = $savedTargets
        npcMorphAssignments= $savedNpcs
    }
    "saved $savedProjectName (title clean); Custom Morph Targets $($savedTargets -join ', '); NPCs $($savedNpcs -join ', ')"
}

Invoke-Step -Name 'exit-after-save' -Action {
    Stop-PackagedApplication -ObservationKey 'exitAfterSave'
}

Invoke-Step -Name 'prepare-interrupted-settings-publication' -Action {
    $standard = Join-Path $workDir 'settings.json'
    $uunp = Join-Path $workDir 'settings_UUNP.json'
    $transaction = Join-Path $workDir '.bs2bg-settings-stage-smoke-interrupted'
    New-Item -ItemType Directory -Path $transaction | Out-Null
    Move-Item -LiteralPath $standard -Destination (Join-Path $transaction 'standard.backup')
    Move-Item -LiteralPath $uunp -Destination (Join-Path $transaction 'uunp.backup')
    [System.IO.File]::WriteAllBytes($standard, $script:firstRunStandardBytes)
    [System.IO.File]::WriteAllBytes((Join-Path $transaction 'uunp.staged'), $script:firstRunUunpBytes)
    $observations['interruptedSettingsPublication'] = [ordered]@{
        transactionDirectory = $transaction
        installedMembers = @('settings.json')
        absentMembers = @('settings_UUNP.json')
        priorStandardSha256 = $script:legacyStandardSha256
        priorUunpSha256 = $script:legacyUunpSha256
    }
    'assembled an interrupted publication after the Standard install and before the UUNP install'
}

Invoke-Step -Name 'recover-settings-relaunch-and-reopen-saved-project' -Action {
    # Reopening in a fresh process proves the saved Project survives a complete exit/relaunch of the packaged
    # executable. (It also sidesteps a JavaFX accessibility quirk: a ListView emptied by New and refilled by Open
    # within one process no longer publishes its cells to UI Automation although it renders them.)
    $launch = Start-PackagedApplication -ObservationKey 'recoveryLaunch'
    $standardHash = (Get-FileHash -LiteralPath (Join-Path $workDir 'settings.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    $uunpHash = (Get-FileHash -LiteralPath (Join-Path $workDir 'settings_UUNP.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($standardHash -cne $script:legacyStandardSha256 -or $uunpHash -cne $script:legacyUunpSha256) {
        throw 'Restart recovery did not restore the exact prior Settings pair.'
    }
    $staging = @(Get-ChildItem -LiteralPath $workDir -Force | Where-Object { $_.Name -like '.bs2bg-settings-stage-*' })
    if ($staging.Count -ne 0) { throw 'Restart recovery left Settings transaction state behind.' }
    Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
    Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $savedProjectName) -ConfirmButton 'Open'
    Wait-MainWindow -Title "$applicationTitle - $savedProjectName" | Out-Null
    $presets = Wait-ListItems -ListName $sliderPresetsList -Expected $fixturePresets
    $option = Find-OuterControl -ControlType 'CheckBox' -Name 'Omit Redundant Sliders'
    $templatesOutput = Get-PrecedingControl -Element $option -ControlType 'Edit'
    $generateTemplates = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'Button' -Name 'Generate Templates') -Description "'Generate Templates' button" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $generateTemplates
    $recoveredText = Wait-Text -Element $templatesOutput -Description 'recovered Settings-dependent Templates output' -Predicate {
        param($t)
        @($t -split "`r?`n") -ccontains $expectedCbbeTemplate -and
            @($t -split "`r?`n") -ccontains $expectedUunpTemplate
    }
    $tab = Wait-UiaElement -Root $script:mainWindow -Condition (New-UiaCondition -ControlType 'TabItem' -Name 'Morphs') -Description "'Morphs' tab" -TimeoutSeconds $StepTimeoutSeconds
    Select-UiaElement -Element $tab
    $targets = Wait-ListItems -ListName $customTargetsList -Expected @($fixtureCustomTarget, $newCustomTarget)
    $npc = Assert-NpcRow
    Select-ListItem -ListName $customTargetsList -ItemName $newCustomTarget
    $assigned = Wait-ListItems -ListName $targetPresetsList -Expected $fixturePresets
    Get-UiaTree -Element $script:mainWindow | Set-Content -LiteralPath (Join-Path $diagnosticsDir 'uia-tree-after-reopen.txt') -Encoding utf8
    $observations['reopenedProject'] = [ordered]@{
        file = $savedProjectName
        sliderPresets = $presets
        customMorphTargets = $targets
        targetSliderPresets = $assigned
        recoveredSettingsOutput = Get-Excerpt $recoveredText 240
        exactPriorSettingsPairRestored = $true
    }
    "$launch; restored exact prior Settings pair and output; reopened ${savedProjectName}: $sliderPresetsList $($presets -join ', '); $customTargetsList $($targets -join ', '); '$newCustomTarget' -> $($assigned -join ', '); $npc"
}

Invoke-Step -Name 'close-and-exit' -Action {
    Stop-PackagedApplication -ObservationKey 'exit'
}

Invoke-Step -Name 'verify-settings-recovery-diagnostic' -Action {
    $lifecycleStderr = ''
    foreach ($task in $script:stderrTasks) { $lifecycleStderr += $task.Result }
    $recoveryLines = @($lifecycleStderr -split "`r?`n" | Where-Object { $_ -match 'SETTINGS_PUBLICATION_RECOVERED' })
    if ($recoveryLines.Count -lt 1) { throw 'The recovery lifecycle did not emit SETTINGS_PUBLICATION_RECOVERED.' }
    $observations['settingsRecoveryDiagnostic'] = [ordered]@{
        code = 'SETTINGS_PUBLICATION_RECOVERED'
        occurrences = $recoveryLines.Count
    }
    'observed SETTINGS_PUBLICATION_RECOVERED in packaged launcher diagnostics'
}

$passed = $true

}
catch {
    Write-Host "  [smoke] run failed: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    $killed = @()
    $cleanupProcesses = @()
    if ($observations.Contains('extractedImage')) {
        # A launcher regression can create an untracked child before the one-process assertion fails; sweep the
        # exact extracted image so failure cleanup cannot leave that child running and locking the test image.
        $cleanupProcesses = @(Get-ImageLauncherProcesses)
    }
    elseif ($script:app) {
        $cleanupProcesses = @($script:app)
    }
    foreach ($process in $cleanupProcesses) {
        if (-not $process.HasExited) {
            try { $process.Kill(); $killed += $process.Id } catch { <# already gone between the check and the kill #> }
        }
    }
    $stdout = ''; $stderr = ''
    # A task faults only when the pipe was torn down by a kill; the output captured so far is still reported.
    foreach ($task in $script:stdoutTasks) { try { $stdout += $task.Result } catch { <# see above #> } }
    foreach ($task in $script:stderrTasks) { try { $stderr += $task.Result } catch { <# see above #> } }
    Set-Content -LiteralPath (Join-Path $diagnosticsDir 'launcher-stdout.txt') -Value $stdout -Encoding utf8
    Set-Content -LiteralPath (Join-Path $diagnosticsDir 'launcher-stderr.txt') -Value $stderr -Encoding utf8

    $restricted = @($stderr -split "`r?`n" | Where-Object { $_ -match 'restricted method|native access|--enable-native-access' })
    $evidence = [ordered]@{
        schema           = 'bs2bg.windows-app-image-smoke/3'
        recordedAtUtc    = $startedAt.ToString('o')
        passed           = $passed
        expectedAppVersion = $ExpectedAppVersion
        archive          = $ArchivePath
        timeouts         = [ordered]@{ startupSeconds = $StartupTimeoutSeconds; stepSeconds = $StepTimeoutSeconds; exitSeconds = $ExitTimeoutSeconds }
        steps            = $steps
        observations     = $observations
        process          = [ordered]@{
            expectedModel    = 'single-launcher-process'
            model            = $(if ($observations.Contains('observedProcessModel')) { $observations['observedProcessModel'] } else { $null })
            applicationPid  = $(if ($script:app) { $script:app.Id } else { $null })
            exitCode        = $(if ($observations.Contains('exit')) { $observations['exit'].exitCode } else { $null })
            exitWaitSeconds = $(if ($observations.Contains('exit')) { $observations['exit'].exitWaitSeconds } else { $null })
            lifecycles      = $script:lifecycles
            killed          = $killed
        }
        diagnostics      = [ordered]@{
            directory              = 'target/reproducibility/smoke-diagnostics/'
            stderrLines            = @($stderr -split "`r?`n" | Where-Object { $_ }).Count
            stderrExcerpt          = @($stderr -split "`r?`n" | Where-Object { $_ } | Select-Object -First 20)
            nativeAccessWarnings   = $restricted
            uiaTreeBosPreview      = 'smoke-diagnostics/uia-tree-bos-preview.txt'
            uiaTreeAfterReopen     = 'smoke-diagnostics/uia-tree-after-reopen.txt'
        }
        workRoot         = $WorkRoot
        workRootKept     = [bool]$KeepWorkRoot
    }
    $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $EvidencePath -Encoding utf8
    Write-Step "evidence written to $EvidencePath"
    if (-not $KeepWorkRoot -and (Test-Path -LiteralPath $WorkRoot)) {
        try { Remove-Item -LiteralPath $WorkRoot -Recurse -Force } catch { Write-Step "could not remove $WorkRoot : $($_.Exception.Message)" }
    }
}

if ($passed) { exit 0 } else { exit 1 }
