#Requires -Version 7.0
<#
.SYNOPSIS
    Drives the packaged BS2BG Preview Workbench through its lifecycle and platform contract (issues #98-#101).

.DESCRIPTION
    Extracts the app-image archive to a clean temporary root, launches the real BS2BG.exe without any host Java
    discovery path, proves that the launcher process hosts the bundled JVM, and drives the Workbench through
    Windows UI Automation by accessible role and name. The run covers typed navigation, focus cycling and return,
    Output drawer interaction, responsive/minimum geometry, live themes, High Contrast, reduced motion,
    accessibility semantics, notifications, typed dialogs, startup, New, Open, Save, Save As, Project recovery,
    centralized admission, measured progress, cancellation, linked retry, stale-safe Activity evidence,
    malformed/failed operation preservation, coordinated dirty shutdown, and bounded process exit.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ArchivePath,
    [string]$LauncherName = 'BS2BG',
    [Parameter(Mandatory)] [string]$FixtureProject,
    [Parameter(Mandatory)] [string]$FixtureRecoveryProject,
    [Parameter(Mandatory)] [string]$FixtureMalformedProject,
    [Parameter(Mandatory)] [string]$EvidencePath,
    [string]$ExpectedAppVersion = '',
    [string]$WorkRoot = (Join-Path $env:TEMP ("BS2BG-workbench-smoke-" + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))),
    [int]$StartupTimeoutSeconds = 90,
    [int]$StepTimeoutSeconds = 30,
    [int]$ExitTimeoutSeconds = 30,
    [int]$ExpectedDpiPercent = 0,
    [switch]$KeepWorkRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'WindowsAppImage.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'UiaAutomation.psm1') -Force

$applicationTitle = 'BS2BG Preview'
$openDialogTitle = 'Open BS2BG Project'
$saveDialogTitle = 'Save BS2BG Project'
$openedProjectName = 'representative.jbs2bg'
$recoveryProjectName = 'recovery-source.jbs2bg'
$malformedProjectName = 'malformed-project.jbs2bg'
$cancellableProjectName = 'cancellable-project.jbs2bg'
$firstSaveName = 'new-project.jbs2bg'
$retrySaveName = 'save-retry.jbs2bg'
$shutdownProjectName = 'shutdown-recovery.jbs2bg'
$accessibilityState = Get-SystemAccessibilityPreferences

$evidenceDir = Split-Path -Parent $EvidencePath
$diagnosticsDir = Join-Path $evidenceDir 'smoke-diagnostics'
New-Item -ItemType Directory -Path $diagnosticsDir -Force | Out-Null
Get-ChildItem -LiteralPath $diagnosticsDir -File | Remove-Item -Force

$startedAt = [DateTimeOffset]::UtcNow
$steps = New-Object System.Collections.Generic.List[object]
$observations = [ordered]@{}
$script:app = $null
$script:mainWindow = $null
$script:stdoutTask = $null
$script:stderrTask = $null
$imageRoot = Join-Path $WorkRoot 'image'
$workDir = Join-Path $WorkRoot 'work'

<#
.SYNOPSIS
    Writes a valid high-token-count Project used to keep packaged Open at cooperative parser safe points.
.PARAMETER Path
    Destination fixture path.
.PARAMETER ChoiceCount
    Number of unique Slider choices in the one detached preset.
.NOTES
    The fixture creates natural parsing work without adding production sleeps or test-only application behavior.
#>
function New-CancellableProjectFixture {
    param([Parameter(Mandatory)] [string]$Path, [int]$ChoiceCount = 400000)
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $writer = [System.IO.StreamWriter]::new($Path, $false, $utf8, 1MB)
    try {
        $writer.Write('{"SliderPresets":{"Cancellable":{"isUUNP":false,"SetSliders":[')
        for ($index = 0; $index -lt $ChoiceCount; $index++) {
            if ($index -gt 0) { $writer.Write(',') }
            $choiceName = 'Cancel' + $index.ToString('D6', [System.Globalization.CultureInfo]::InvariantCulture)
            $writer.Write('{"name":"' + $choiceName + '","enabled":true,"valueSmall":1,' +
                    '"valueBig":2,"pctMin":0,"pctMax":100}')
        }
        $writer.Write(']}},"CustomMorphTargets":{},"MorphedNPCs":{}}')
    }
    finally {
        $writer.Dispose()
    }
}

<#
.SYNOPSIS
    Prints one indented smoke progress line.
.PARAMETER Message
    User-facing progress text; this function does not mutate workflow state.
#>
function Write-SmokeStep {
    param([string]$Message)
    Write-Host "  [smoke] $Message"
}

<#
.SYNOPSIS
    Runs and records one named smoke workflow step.
.PARAMETER Name
    Stable evidence name of the workflow step.
.PARAMETER Action
    Step body; its output becomes evidence detail.
.NOTES
    On failure, captures UI diagnostics and rethrows the original error.
#>
function Invoke-SmokeStep {
    param([Parameter(Mandatory)] [string]$Name, [Parameter(Mandatory)] [scriptblock]$Action)
    Write-SmokeStep "step: $Name"
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $detail = & $Action
        $steps.Add([ordered]@{
            name = $Name
            passed = $true
            seconds = [math]::Round($stopwatch.Elapsed.TotalSeconds, 2)
            detail = $detail
        })
        Write-SmokeStep "  ok ($([math]::Round($stopwatch.Elapsed.TotalSeconds, 1)) s)"
    }
    catch {
        $steps.Add([ordered]@{
            name = $Name
            passed = $false
            seconds = [math]::Round($stopwatch.Elapsed.TotalSeconds, 2)
            error = $_.Exception.Message
        })
        Write-Host "  [smoke]   FAILED: $($_.Exception.Message)" -ForegroundColor Red
        Save-FailureDiagnostics -StepName $Name
        throw
    }
}

<#
.SYNOPSIS
    Captures every visible process window, its UIA tree, and a screenshot.
.PARAMETER StepName
    Failing step name used to derive diagnostic filenames.
.NOTES
    Diagnostics are best effort and never replace the original workflow failure.
#>
function Save-FailureDiagnostics {
    param([string]$StepName)
    $safe = $StepName -replace '[^A-Za-z0-9-]', '_'
    try {
        if ($script:app -and -not $script:app.HasExited) {
            Get-ProcessTopLevelWindows -ProcessId $script:app.Id |
                ForEach-Object { "$($_.className) visible=$($_.visible) title='$($_.title)'" } |
                Set-Content -LiteralPath (Join-Path $diagnosticsDir "failure-$safe-windows.txt") -Encoding utf8
            $index = 0
            foreach ($window in (Get-ProcessTopLevelWindows -ProcessId $script:app.Id | Where-Object { $_.visible })) {
                $index++
                try {
                    Get-UiaTree -Element ([System.Windows.Automation.AutomationElement]::FromHandle(
                            [IntPtr]$window.handle)) |
                        Set-Content -LiteralPath (Join-Path $diagnosticsDir "failure-$safe-uia-tree-$index.txt") -Encoding utf8
                }
                catch {
                    # The window may disappear between enumeration and capture; the original failure remains.
                }
            }
        }
    }
    catch {
        # Diagnostics are best effort and must never hide the original smoke failure.
    }
    Save-Screenshot -Path (Join-Path $diagnosticsDir "failure-$safe.png")
}

<#
.SYNOPSIS
    Saves a best-effort screenshot of the virtual Windows desktop.
.PARAMETER Path
    PNG destination path.
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
        # Locked or headless desktops may reject capture; textual diagnostics remain.
    }
}

<#
.SYNOPSIS
    Waits for and remembers the exact titled Workbench window.
.PARAMETER Title
    Exact case-sensitive window title.
.PARAMETER TimeoutSeconds
    Bounded wait before throwing.
.OUTPUTS
    The matching UI Automation window element.
#>
function Wait-MainWindow {
    param([string]$Title, [int]$TimeoutSeconds = $StepTimeoutSeconds)
    $script:mainWindow = Wait-UiaWindow -ProcessId $script:app.Id -Title $Title -TimeoutSeconds $TimeoutSeconds
    return $script:mainWindow
}

<#
.SYNOPSIS
    Returns launcher processes whose executable belongs to the extracted image.
.OUTPUTS
    Zero or more Process objects; processes with the same name outside the smoke image are excluded.
#>
function Get-ImageLauncherProcesses {
    if (-not $observations.Contains('extractedImage')) { return }
    Get-Process -Name $LauncherName -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -and $_.Path.StartsWith($observations['extractedImage'],
                [System.StringComparison]::OrdinalIgnoreCase) }
}

<#
.SYNOPSIS
    Verifies a File menu item semantically, then triggers its accelerator.
.PARAMETER Item
    Exact visible File menu item name.
.PARAMETER DialogTitle
    Optional exact modal title used to confirm/retry delivery.
.NOTES
    Accelerator delivery avoids holding a synchronous UIA callback across a JavaFX modal loop.
#>
function Send-FileCommand {
    param([string]$Item, [string]$DialogTitle = '')
    $accelerators = @{
        'New' = '^n'
        'Open…' = '^o'
        'Save' = '^s'
        'Save As…' = '^%s'
    }
    if (-not $accelerators.ContainsKey($Item)) { throw "No accelerator is known for File > $Item" }
    $fileMenu = Wait-UiaElement -Root $script:mainWindow -Condition (
        New-UiaCondition -ControlType 'MenuItem' -Name 'File') -Description "'File' menu" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $fileMenu
    Wait-UiaElement -Root $script:mainWindow -Condition (
        New-UiaCondition -ControlType 'MenuItem' -Name $Item) -Description "'$Item' File item" -TimeoutSeconds $StepTimeoutSeconds | Out-Null
    Send-UiaAccelerator -Window $script:mainWindow -Keys '{ESC}'
    Send-UiaAccelerator -Window $script:mainWindow -Keys $accelerators[$Item]
    if ($DialogTitle) {
        try {
            Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $DialogTitle -TimeoutSeconds 8 | Out-Null
        }
        catch {
            Write-SmokeStep "  '$DialogTitle' did not appear within 8 s; re-sending once"
            Send-UiaAccelerator -Window $script:mainWindow -Keys $accelerators[$Item]
        }
    }
}

<#
.SYNOPSIS
    Completes an owned native file dialog.
.PARAMETER Title
    Exact native dialog title.
.PARAMETER Path
    Complete path typed into the File name field.
.PARAMETER ConfirmButton
    Exact Open or Save control name.
#>
function Complete-FileDialog {
    param([string]$Title, [string]$Path, [string]$ConfirmButton)
    $dialog = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $Title -TimeoutSeconds $StepTimeoutSeconds
    $fileName = Wait-UiaElement -Root $dialog -Condition (
        New-UiaCondition -ControlType 'Edit' -Name 'File name:') -Description "'File name:' in '$Title'" -TimeoutSeconds $StepTimeoutSeconds
    Set-UiaValue -Element $fileName -Value $Path
    $confirmRole = New-Object System.Windows.Automation.OrCondition(@(
        (New-UiaCondition -ControlType 'Pane' -Name $ConfirmButton),
        (New-UiaCondition -ControlType 'SplitButton' -Name $ConfirmButton),
        (New-UiaCondition -ControlType 'Button' -Name $ConfirmButton)))
    $confirm = Wait-UiaElement -Root $dialog -Condition $confirmRole -Description "'$ConfirmButton' in '$Title'" -TimeoutSeconds $StepTimeoutSeconds
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
    Chooses one named Workbench confirmation action.
.PARAMETER ButtonName
    Exact accessible button name.
.NOTES
    Focused keyboard activation closes the modal without a blocking cross-process Invoke callback.
#>
function Choose-Confirmation {
    param([string]$ButtonName)
    $dialog = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $applicationTitle -TimeoutSeconds $StepTimeoutSeconds
    $button = Wait-UiaElement -Root $dialog -Condition (
        New-UiaCondition -ControlType 'Button' -Name $ButtonName) -Description "'$ButtonName' confirmation button" -TimeoutSeconds $StepTimeoutSeconds
    # Keyboard activation avoids holding a synchronous UIA Invoke callback across modal close.
    Send-UiaKeysToElement -Element $button -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
}

<#
.SYNOPSIS
    Returns the outermost identically named JavaFX control.
.PARAMETER ControlType
    Required UIA role.
.PARAMETER Name
    Exact accessible name.
.OUTPUTS
    The outermost matching AutomationElement, skipping JavaFX's duplicate text child.
#>
function Find-OuterControl {
    param([string]$ControlType, [string]$Name)
    $element = Wait-UiaElement -Root $script:mainWindow -Condition (
        New-UiaCondition -ControlType $ControlType -Name $Name) -Description "'$Name' $ControlType" -TimeoutSeconds $StepTimeoutSeconds
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    while ($true) {
        $parent = $walker.GetParent($element)
        if ($null -eq $parent -or $parent.Current.Name -cne $Name -or
                $parent.Current.ControlType -ne $element.Current.ControlType) {
            return $element
        }
        $element = $parent
    }
}

<#
.SYNOPSIS
    Returns one exact Workbench rail button and requires its standard Toggle pattern.
#>
function Get-AreaButton {
    param([Parameter(Mandatory)] [string]$Name)
    $condition = New-Object System.Windows.Automation.OrCondition(@(
            (New-UiaCondition -ControlType 'Button' -Name $Name),
            (New-UiaCondition -ControlType 'CheckBox' -Name $Name)))
    $button = Wait-UiaElement -Root $script:mainWindow -Condition $condition -Description "'$Name' navigation button" `
        -TimeoutSeconds $StepTimeoutSeconds
    Get-UiaToggleState -Element $button | Out-Null
    return $button
}

<#
.SYNOPSIS
    Waits for one Area button to expose ToggleState.On.
#>
function Wait-AreaSelected {
    param([Parameter(Mandatory)] [string]$Name)
    return Wait-UiaCondition -Description "'$Name' Area selected" -TimeoutSeconds $StepTimeoutSeconds -Test {
        $button = Get-AreaButton -Name $Name
        if ((Get-UiaToggleState -Element $button) -eq 'On') { $button }
    }
}

<#
.SYNOPSIS
    Locates one semantic control and waits until it owns keyboard focus.
#>
function Wait-FocusedControl {
    param([Parameter(Mandatory)] [string]$ControlType, [Parameter(Mandatory)] [string]$Name)
    $element = Find-OuterControl -ControlType $ControlType -Name $Name
    Wait-UiaKeyboardFocus -Element $element -TimeoutSeconds $StepTimeoutSeconds | Out-Null
    return $element
}

<#
.SYNOPSIS
    Converts native window metrics to stable JSON evidence fields.
#>
function ConvertTo-WindowMetricsEvidence {
    param([Parameter(Mandatory)] $Metrics)
    return [ordered]@{
        dpi = $Metrics.Dpi
        scalePercent = [math]::Round($Metrics.Dpi * 100.0 / 96.0)
        windowPhysical = [ordered]@{
            left = $Metrics.WindowLeft; top = $Metrics.WindowTop
            width = $Metrics.WindowWidth; height = $Metrics.WindowHeight
        }
        clientPhysical = [ordered]@{
            left = $Metrics.ClientLeft; top = $Metrics.ClientTop
            width = $Metrics.ClientWidth; height = $Metrics.ClientHeight
        }
        clientLogical = [ordered]@{
            width = [math]::Round($Metrics.LogicalClientWidth, 2)
            height = [math]::Round($Metrics.LogicalClientHeight, 2)
        }
    }
}

<#
.SYNOPSIS
    Requires one visible UIA control to stay inside the measured client rectangle.
#>
function Assert-ControlInsideClient {
    param([Parameter(Mandatory)] $Element, [Parameter(Mandatory)] $Metrics)
    if ($Element.Current.IsOffscreen) { throw "'$($Element.Current.Name)' is offscreen." }
    $bounds = $Element.Current.BoundingRectangle
    $clientRight = $Metrics.ClientLeft + $Metrics.ClientWidth
    $clientBottom = $Metrics.ClientTop + $Metrics.ClientHeight
    if ($bounds.Left -lt ($Metrics.ClientLeft - 1) -or $bounds.Top -lt ($Metrics.ClientTop - 1) -or
            $bounds.Right -gt ($clientRight + 1) -or $bounds.Bottom -gt ($clientBottom + 1)) {
        throw "'$($Element.Current.Name)' bounds $bounds escape client [$($Metrics.ClientLeft),$($Metrics.ClientTop),$clientRight,$clientBottom]."
    }
}

<#
.SYNOPSIS
    Finds the nearest following sibling with the requested UIA role.
.PARAMETER Element
    Semantic launcher/label element.
.PARAMETER ControlType
    Required following role.
.PARAMETER MaxHops
    Maximum siblings inspected before throwing.
#>
function Get-FollowingControl {
    param([Parameter(Mandatory)] $Element, [string]$ControlType, [int]$MaxHops = 6)
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    $current = $walker.GetNextSibling($Element)
    for ($hop = 0; $hop -lt $MaxHops -and $null -ne $current; $hop++) {
        if ((Get-UiaRoleName -Element $current) -eq $ControlType) { return $current }
        $current = $walker.GetNextSibling($current)
    }
    throw "No $ControlType follows '$($Element.Current.Name)' within $MaxHops siblings."
}

<#
.SYNOPSIS
    Returns the complete Workbench Project diagnostics text.
.OUTPUTS
    Diagnostic child text in accessible order, excluding empty-state prompt text.
#>
function Get-ProjectDiagnostics {
    $label = Find-OuterControl -ControlType 'Text' -Name 'Project diagnostics'
    $diagnostics = Get-FollowingControl -Element $label -ControlType 'Edit'
    # The packaged JavaFX provider exposes read-only TextArea content as accessible child Text, not Value/Text data.
    return @(Find-UiaElements -Root $diagnostics -Condition (New-UiaCondition -ControlType 'Text') |
            ForEach-Object { $_.Current.Name } |
            Where-Object { $_ -and $_ -cne 'No Project diagnostics' }) -join [Environment]::NewLine
}

<#
.SYNOPSIS
    Waits until Project diagnostics satisfy a semantic predicate.
.PARAMETER Predicate
    Scriptblock receiving the current diagnostic text.
.PARAMETER Description
    User-facing description included in timeout errors.
.OUTPUTS
    The accepted diagnostic text.
#>
function Wait-ProjectDiagnostics {
    param([scriptblock]$Predicate, [string]$Description)
    $result = Wait-UiaCondition -Description $Description -TimeoutSeconds $StepTimeoutSeconds -Test {
        $text = Get-ProjectDiagnostics
        if (& $Predicate $text) { [pscustomobject]@{ Text = $text } }
    }
    return $result.Text
}

<#
.SYNOPSIS
    Starts the packaged launcher and proves its bundled one-process runtime.
.OUTPUTS
    A concise startup evidence summary.
.NOTES
    Owns the process and redirected output tasks in script scope until bounded exit or failure cleanup.
#>
function Start-PackagedApplication {
    $scrubbed = Get-ScrubbedEnvironment -Environment ([System.Environment]::GetEnvironmentVariables())
    $observations['environment'] = [ordered]@{
        removedVariables = $scrubbed.RemovedVariables
        removedPathEntries = $scrubbed.RemovedPathEntries
        keptPathEntryCount = @($scrubbed.Variables['PATH'].Split(';',
                [System.StringSplitOptions]::RemoveEmptyEntries)).Count
    }
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $observations['launcher']
    $startInfo.WorkingDirectory = $workDir
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Environment.Clear()
    foreach ($name in $scrubbed.Variables.Keys) {
        $startInfo.Environment[$name] = "$($scrubbed.Variables[$name])"
    }
    $script:app = [System.Diagnostics.Process]::Start($startInfo)
    $script:stdoutTask = $script:app.StandardOutput.ReadToEndAsync()
    $script:stderrTask = $script:app.StandardError.ReadToEndAsync()
    $launchedAt = [DateTimeOffset]::UtcNow
    Wait-MainWindow -Title $applicationTitle -TimeoutSeconds $StartupTimeoutSeconds | Out-Null
    $windowAt = [DateTimeOffset]::UtcNow

    $imageProcesses = @(Get-ImageLauncherProcesses)
    if ($imageProcesses.Count -ne 1 -or $imageProcesses[0].Id -ne $script:app.Id) {
        throw "Expected launcher pid $($script:app.Id) to be the sole image process; found $(($imageProcesses.Id) -join ', ')."
    }
    $runtimeModules = @()
    foreach ($module in $script:app.Modules) {
        if ($module.ModuleName -match '^(jvm|java|jli|jimage|glass|prism_d3d|prism_sw|javafx_font)\.dll$') {
            $runtimeModules += [ordered]@{ module = $module.ModuleName; path = $module.FileName }
        }
    }
    $jvm = @($runtimeModules | Where-Object { $_.module -eq 'jvm.dll' })
    if ($jvm.Count -ne 1) { throw 'The packaged launcher did not load exactly one jvm.dll.' }
    $outside = @($runtimeModules | Where-Object { -not $_.path.StartsWith($observations['extractedImage'],
                [System.StringComparison]::OrdinalIgnoreCase) })
    if ($outside.Count -gt 0) { throw "Runtime modules loaded outside the image: $(($outside.path) -join ', ')" }
    $observations['observedProcessModel'] = 'single-launcher-process'
    $observations['launch'] = [ordered]@{
        applicationPid = $script:app.Id
        applicationExe = $script:app.MainModule.FileName
        startupSeconds = [math]::Round(($windowAt - $launchedAt).TotalSeconds, 2)
        runtimeModules = $runtimeModules
    }
    return "launcher pid $($script:app.Id) hosts image-local jvm.dll; Workbench appeared after $([math]::Round(($windowAt - $launchedAt).TotalSeconds, 1)) s"
}

<#
.SYNOPSIS
    Requires the requested final close to end the launcher cleanly.
.OUTPUTS
    A concise bounded-exit summary.
.NOTES
    Throws on timeout, non-zero exit, or any remaining process from the extracted image.
#>
function Assert-PackagedExit {
    $waitStarted = [DateTimeOffset]::UtcNow
    $exited = $script:app.WaitForExit($ExitTimeoutSeconds * 1000)
    $finished = [DateTimeOffset]::UtcNow
    if (-not $exited) { throw "The packaged launcher did not exit within $ExitTimeoutSeconds seconds." }
    if ($script:app.ExitCode -ne 0) { throw "The packaged launcher exited with code $($script:app.ExitCode)." }
    $leftovers = @(Get-ImageLauncherProcesses)
    if ($leftovers.Count -gt 0) { throw "Image processes remain after exit: $(($leftovers.Id) -join ', ')." }
    $observations['exit'] = [ordered]@{
        exitCode = $script:app.ExitCode
        exitWaitSeconds = [math]::Round(($finished - $waitStarted).TotalSeconds, 2)
        boundedBySeconds = $ExitTimeoutSeconds
    }
    return "exit code 0 after $($observations['exit'].exitWaitSeconds) s"
}

$passed = $false
try {
    Invoke-SmokeStep -Name 'extract-clean-image' -Action {
        if (-not (Test-Path -LiteralPath $ArchivePath -PathType Leaf)) { throw "Archive not found: $ArchivePath" }
        if (Test-Path -LiteralPath $WorkRoot) { Remove-Item -LiteralPath $WorkRoot -Recurse -Force }
        New-Item -ItemType Directory -Path $imageRoot -Force | Out-Null
        New-Item -ItemType Directory -Path $workDir -Force | Out-Null
        Expand-Archive -LiteralPath $ArchivePath -DestinationPath $imageRoot -Force
        $launcherPath = Join-Path (Join-Path $imageRoot $LauncherName) "$LauncherName.exe"
        if (-not (Test-Path -LiteralPath $launcherPath -PathType Leaf)) {
            throw "Extracted archive has no $LauncherName\$LauncherName.exe."
        }
        $configPath = Join-Path (Join-Path $imageRoot $LauncherName) "app\$LauncherName.cfg"
        $config = Read-LauncherConfig -Path $configPath
        Assert-LauncherSingleProcessMode -Config $config
        $javaOptions = @()
        if ($config.Contains('JavaOptions') -and $config['JavaOptions'].Contains('java-options')) {
            $javaOptions = @($config['JavaOptions']['java-options'])
        }
        if ($ExpectedAppVersion -and ($javaOptions -cnotcontains "-Djpackage.app-version=$ExpectedAppVersion")) {
            throw "Launcher configuration does not stamp app version $ExpectedAppVersion."
        }
        Copy-Item -LiteralPath $FixtureProject -Destination (Join-Path $workDir $openedProjectName)
        Copy-Item -LiteralPath $FixtureRecoveryProject -Destination (Join-Path $workDir $recoveryProjectName)
        Copy-Item -LiteralPath $FixtureMalformedProject -Destination (Join-Path $workDir $malformedProjectName)
        $cancellableProject = Join-Path $workDir $cancellableProjectName
        New-CancellableProjectFixture -Path $cancellableProject
        $observations['extractedImage'] = Join-Path $imageRoot $LauncherName
        $observations['launcher'] = $launcherPath
        $observations['launcherSha256'] = (Get-FileHash -LiteralPath $launcherPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $observations['archiveSha256'] = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $observations['workingDirectory'] = $workDir
        $observations['cancellableProjectBytes'] = (Get-Item -LiteralPath $cancellableProject).Length
        "extracted archive and installed four Project fixtures in $workDir"
    }

    Invoke-SmokeStep -Name 'launch-workbench-without-system-java' -Action {
        Start-PackagedApplication
    }

    Invoke-SmokeStep -Name 'verify-workbench-shell-and-first-run-settings' -Action {
        foreach ($area in @('Templates', 'Morphs', 'NPC Database', 'Output', 'Settings')) {
            $areaButton = Get-AreaButton -Name $area
            if ($areaButton.Current.HelpText -notlike "Semantic icon: $area.*Keyboard shortcut:*") {
                throw "The packaged $area action did not expose its selected semantic icon and keyboard cue."
            }
        }
        foreach ($settingsName in @('settings.json', 'settings_UUNP.json')) {
            if (-not (Test-Path -LiteralPath (Join-Path $workDir $settingsName) -PathType Leaf)) {
                throw "Workbench startup did not create $settingsName."
            }
        }
        Get-UiaTree -Element $script:mainWindow |
            Set-Content -LiteralPath (Join-Path $diagnosticsDir 'uia-tree-workbench.txt') -Encoding utf8
        $observations['workbenchAreas'] = @('Templates', 'Morphs', 'NPC Database', 'Output', 'Settings')
        'five typed navigation destinations and the canonical Settings pair are accessible'
    }

    Invoke-SmokeStep -Name 'verify-live-themes-high-contrast-reduced-motion-and-semantics' -Action {
        $themeLabel = Find-OuterControl -ControlType 'Text' -Name 'Theme:'
        $themeChoice = Get-FollowingControl -Element $themeLabel -ControlType 'ComboBox'
        $activity = Find-OuterControl -ControlType 'List' -Name 'Activity'
        $cancel = Find-OuterControl -ControlType 'Button' -Name 'Cancel current operation'
        if ($activity.Current.IsKeyboardFocusable -ne $true) { throw 'Activity is not keyboard reachable.' }
        if ($cancel.Current.IsEnabled) { throw 'Cancel must be disabled while no cancellable operation is active.' }

        Set-SystemHighContrast -Enabled:$false
        Send-UiaKeysToElement -Element $themeChoice -Keys '{HOME}{DOWN}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: Light theme') `
            -Description 'explicit Light theme' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $themeChoice -Keys '{DOWN}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: Dark theme') `
            -Description 'explicit Dark theme' -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        Set-SystemHighContrast -Enabled:$true
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: High Contrast theme') `
            -Description 'live High Contrast override' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $highContrastScreenshot = Join-Path $diagnosticsDir 'workbench-high-contrast.png'
        Save-Screenshot -Path $highContrastScreenshot
        if (-not (Test-Path -LiteralPath $highContrastScreenshot -PathType Leaf)) {
            throw 'The packaged semantic-icon High Contrast screenshot was not captured.'
        }

        Set-SystemHighContrast -Enabled:$false
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: Dark theme') `
            -Description 'theme restored after High Contrast' -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        Set-SystemClientAreaAnimation -Enabled:$true
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Motion preference: Standard motion') `
            -Description 'standard motion preference' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Set-SystemClientAreaAnimation -Enabled:$false
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Motion preference: Reduced motion') `
            -Description 'live reduced-motion preference' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $reducedMotionScreenshot = Join-Path $diagnosticsDir 'workbench-reduced-motion.png'
        Save-Screenshot -Path $reducedMotionScreenshot
        if (-not (Test-Path -LiteralPath $reducedMotionScreenshot -PathType Leaf)) {
            throw 'The packaged reduced-motion screenshot was not captured.'
        }

        Restore-SystemAccessibilityPreferences -State $accessibilityState
        Send-UiaKeysToElement -Element $themeChoice -Keys '{HOME}' -TimeoutSeconds $StepTimeoutSeconds
        $systemTheme = Wait-UiaCondition -Description 'System theme resolved from restored Windows preferences' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
                foreach ($name in @('Effective theme: Light theme', 'Effective theme: Dark theme',
                        'Effective theme: High Contrast theme')) {
                    $candidate = Find-UiaElement -Root $script:mainWindow -Condition (
                        New-UiaCondition -ControlType 'Text' -Name $name)
                    if ($null -ne $candidate) { return $candidate }
                }
            }
        $expectedMotion = if ($accessibilityState.ClientAreaAnimation) {
            'Motion preference: Standard motion'
        } else {
            'Motion preference: Reduced motion'
        }
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name $expectedMotion) `
            -Description 'restored motion preference' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $observations['appearance'] = [ordered]@{
            choices = @('System', 'Light', 'Dark')
            highContrastOverride = $true
            reducedMotionObserved = $true
            restoredHighContrast = $accessibilityState.HighContrast
            restoredClientAreaAnimation = $accessibilityState.ClientAreaAnimation
            systemEffectiveTheme = $systemTheme.Current.Name
            iconImplementation = 'application-owned-bundled-vectors'
        }
        'theme choices, High Contrast precedence/restoration, reduced motion, Activity, and Cancel state passed'
    }

    Invoke-SmokeStep -Name 'verify-keyboard-navigation-focus-and-output-drawer' -Action {
        $outputLauncher = Get-AreaButton -Name 'Output'
        $outputLauncher.SetFocus()
        Wait-UiaKeyboardFocus -Element $outputLauncher -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Text' -Name 'Output generated text' | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $outputLauncher -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        $areaShortcuts = [ordered]@{
            '^1' = 'Templates'
            '^2' = 'Morphs'
            '^3' = 'NPC Database'
            '^5' = 'Settings'
        }
        $areaEvidence = @()
        foreach ($entry in $areaShortcuts.GetEnumerator()) {
            Send-UiaKeys -ProcessId $script:app.Id -Keys $entry.Key -TimeoutSeconds $StepTimeoutSeconds
            Wait-AreaSelected -Name $entry.Value | Out-Null
            Wait-FocusedControl -ControlType 'Button' -Name "$($entry.Value) primary content" | Out-Null
            $areaEvidence += $entry.Value
        }

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^2' -TimeoutSeconds $StepTimeoutSeconds
        Wait-AreaSelected -Name 'Morphs' | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^4' -TimeoutSeconds $StepTimeoutSeconds
        Wait-AreaSelected -Name 'Morphs' | Out-Null
        Wait-AreaSelected -Name 'Output' | Out-Null
        Wait-FocusedControl -ControlType 'Text' -Name 'Output generated text' | Out-Null

        $drawerSlider = Find-OuterControl -ControlType 'Slider' -Name 'Output drawer height'
        $drawerBefore = Get-UiaRangeValue -Element $drawerSlider
        Send-UiaKeysToElement -Element $drawerSlider -Keys '{RIGHT}' -TimeoutSeconds $StepTimeoutSeconds
        $drawerAfter = Wait-UiaCondition -Description 'keyboard output drawer resize' -TimeoutSeconds $StepTimeoutSeconds -Test {
            $value = Get-UiaRangeValue -Element $drawerSlider
            if ($value -gt $drawerBefore) { $value }
        }

        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Button' -Name 'Morphs primary content' | Out-Null
        if ((Get-UiaToggleState -Element (Get-AreaButton -Name 'Output')) -ne 'Off') {
            throw 'Escape did not close the Output drawer.'
        }

        Send-UiaKeys -ProcessId $script:app.Id -Keys '{F6}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Button' -Name 'Morphs editor' | Out-Null
        $ctrlBackquote = '^' + [char]96
        Send-UiaKeys -ProcessId $script:app.Id -Keys $ctrlBackquote -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Text' -Name 'Output generated text' | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys $ctrlBackquote -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Button' -Name 'Morphs editor' | Out-Null

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^4' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Text' -Name 'Output generated text' | Out-Null

        $morphsRail = Get-AreaButton -Name 'Morphs'
        $morphsRail.SetFocus()
        Wait-UiaKeyboardFocus -Element $morphsRail -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $focusCycle = @(
            @{ Role = 'Button'; Name = 'Morphs primary content' },
            @{ Role = 'Button'; Name = 'Morphs editor' },
            @{ Role = 'Button'; Name = 'Morphs inspector' },
            @{ Role = 'Text'; Name = 'Output generated text' },
            @{ Role = 'List'; Name = 'Activity' },
            @{ Role = 'Text'; Name = 'Workbench status' },
            @{ Role = 'Button'; Name = 'Morphs' }
        )
        foreach ($target in $focusCycle) {
            Send-UiaKeys -ProcessId $script:app.Id -Keys '{F6}' -TimeoutSeconds $StepTimeoutSeconds
            Wait-FocusedControl -ControlType $target.Role -Name $target.Name | Out-Null
        }
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^4' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Button' -Name 'Morphs editor' | Out-Null
        $observations['keyboardNavigation'] = [ordered]@{
            areas = $areaEvidence
            outputPreservedArea = 'Morphs'
            drawerRange = [ordered]@{ before = $drawerBefore; after = $drawerAfter }
            focusCycle = @($focusCycle | ForEach-Object { $_.Name })
        }
        'typed shortcuts, semantic focus return, drawer resize, alias, and F6 cycle passed through packaged UIA'
    }

    Invoke-SmokeStep -Name 'verify-responsive-layout-and-minimum-geometry' -Action {
        $initialMetrics = Get-UiaWindowMetrics -Window $script:mainWindow
        $initialScale = [math]::Round($initialMetrics.Dpi * 100.0 / 96.0)
        if ($ExpectedDpiPercent -gt 0 -and $initialScale -ne $ExpectedDpiPercent) {
            throw "Expected $ExpectedDpiPercent% display scale, but the packaged window reports $initialScale%."
        }
        if (@(100, 125, 150) -notcontains $initialScale) {
            throw "Packaged Workbench smoke requires an accepted 100%, 125%, or 150% display scale; observed $initialScale%."
        }

        $narrowMetrics = Resize-UiaClient -Window $script:mainWindow -LogicalWidth 1199 -LogicalHeight 700 `
            -TimeoutSeconds $StepTimeoutSeconds
        $listLauncher = Find-OuterControl -ControlType 'Button' -Name 'Open Morphs list'
        $inspectorLauncher = Find-OuterControl -ControlType 'Button' -Name 'Open Morphs inspector'
        Assert-ControlInsideClient -Element $listLauncher -Metrics $narrowMetrics
        Assert-ControlInsideClient -Element $inspectorLauncher -Metrics $narrowMetrics

        Send-UiaKeys -ProcessId $script:app.Id -Keys '{F7}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Button' -Name 'Morphs inspector' | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $inspectorLauncher -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^2' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Button' -Name 'Morphs primary content' | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element (Get-AreaButton -Name 'Morphs') -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        $minimumMetrics = Resize-UiaClient -Window $script:mainWindow -LogicalWidth 700 -LogicalHeight 500 `
            -AllowMinimumClamp -TimeoutSeconds $StepTimeoutSeconds
        if ([math]::Abs($minimumMetrics.LogicalClientWidth - 800.0) -gt 2.0 -or
                [math]::Abs($minimumMetrics.LogicalClientHeight - 600.0) -gt 2.0) {
            throw "Workbench minimum client geometry did not settle at 800x600: $($minimumMetrics.LogicalClientWidth)x$($minimumMetrics.LogicalClientHeight)."
        }
        $editor = Find-OuterControl -ControlType 'Button' -Name 'Morphs editor'
        Assert-ControlInsideClient -Element $editor -Metrics $minimumMetrics
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^4' -TimeoutSeconds $StepTimeoutSeconds
        $minimumDrawer = Find-OuterControl -ControlType 'Slider' -Name 'Output drawer height'
        Assert-ControlInsideClient -Element $minimumDrawer -Metrics $minimumMetrics
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds

        $wideMetrics = Resize-UiaClient -Window $script:mainWindow -LogicalWidth 1200 -LogicalHeight 700 `
            -TimeoutSeconds $StepTimeoutSeconds
        $launcherCondition = New-UiaCondition -ControlType 'Button' -Name 'Open Morphs list'
        $visibleLauncher = Find-UiaElement -Root $script:mainWindow -Condition $launcherCondition
        if ($null -ne $visibleLauncher -and -not $visibleLauncher.Current.IsOffscreen) {
            throw 'Workbench remained in narrow overlay mode at the accepted 1200-pixel breakpoint.'
        }
        Resize-UiaClient -Window $script:mainWindow -LogicalWidth 1300 -LogicalHeight 800 -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Get-UiaTree -Element $script:mainWindow |
            Set-Content -LiteralPath (Join-Path $diagnosticsDir 'uia-tree-workbench-responsive.txt') -Encoding utf8
        $observations['responsiveLayout'] = [ordered]@{
            expectedScalePercent = $(if ($ExpectedDpiPercent -gt 0) { $ExpectedDpiPercent } else { $null })
            initial = ConvertTo-WindowMetricsEvidence -Metrics $initialMetrics
            narrow = ConvertTo-WindowMetricsEvidence -Metrics $narrowMetrics
            minimum = ConvertTo-WindowMetricsEvidence -Metrics $minimumMetrics
            wide = ConvertTo-WindowMetricsEvidence -Metrics $wideMetrics
        }
        'narrow overlays, semantic restoration, 800x600 minimum, breakpoint, and live-DPI geometry passed'
    }

    Invoke-SmokeStep -Name 'save-as-new-project' -Action {
        $target = Join-Path $workDir $firstSaveName
        Send-FileCommand -Item 'Save As…' -DialogTitle $saveDialogTitle
        Complete-FileDialog -Title $saveDialogTitle -Path $target -ConfirmButton 'Save'
        Wait-MainWindow -Title "$applicationTitle - $firstSaveName" | Out-Null
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "Save As did not create $target." }
        $json = Get-Content -LiteralPath $target -Raw | ConvertFrom-Json
        if ($null -eq $json.SliderPresets -or $null -eq $json.CustomMorphTargets -or $null -eq $json.MorphedNPCs) {
            throw 'Save As did not write the complete canonical empty Project schema.'
        }
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Success — Save Project — Completed: Project saved.') `
            -Description 'durable successful Save Activity record' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $observations['firstSaveAs'] = [ordered]@{
            file = $firstSaveName
            sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
        }
        "saved clean untitled Project as $firstSaveName"
    }

    Invoke-SmokeStep -Name 'open-recovered-project-with-complete-diagnostics' -Action {
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $recoveryProjectName) -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - *$recoveryProjectName" | Out-Null
        $text = Wait-ProjectDiagnostics -Description 'both ordered recovery diagnostics' -Predicate {
            param($value)
            [regex]::Matches($value, 'SLIDER_PRESET_ASSIGNMENT_MISSING').Count -ge 2 -and
                $value.Contains('Missing Target') -and $value.Contains('Missing NPC')
        }
        $warningCue = Find-OuterControl -ControlType 'Text' -Name 'Warning'
        $warningMessage = Get-FollowingControl -Element $warningCue -ControlType 'Text'
        if ($warningMessage.Current.Name -cne 'Project opened with 2 diagnostics.') {
            throw "Warning InfoBar did not expose its related message: '$($warningMessage.Current.Name)'"
        }
        $activityRecord = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Warning — Open Project — Completed with issues: Project opened with 2 diagnostics.') `
            -Description 'durable warning Activity record' -TimeoutSeconds $StepTimeoutSeconds
        if ($activityRecord.Current.HelpText -notmatch '^Timestamp: \d{4}-\d{2}-\d{2}T') {
            throw "Activity did not expose its timestamp: '$($activityRecord.Current.HelpText)'"
        }
        foreach ($evidence in @('Attempt:', 'Sources:', $recoveryProjectName, 'Captured basis: Project content version',
                'Effects committed: Project published', 'Diagnostics: SLIDER_PRESET_ASSIGNMENT_MISSING',
                'Retry available: true')) {
            if (-not $activityRecord.Current.HelpText.Contains($evidence)) {
                throw "Recovered Open Activity omitted '$evidence': '$($activityRecord.Current.HelpText)'"
            }
        }
        $observations['recoveredProject'] = [ordered]@{ title = $script:mainWindow.Current.Name; diagnostics = $text }
        'opened a dirty recovered Project with diagnostics, warning InfoBar, Activity, and status projection'
    }

    Invoke-SmokeStep -Name 'cancel-then-discard-dirty-new' -Action {
        Send-FileCommand -Item 'New' -DialogTitle $applicationTitle
        Choose-Confirmation -ButtonName 'Cancel'
        Wait-MainWindow -Title "$applicationTitle - *$recoveryProjectName" | Out-Null
        if ($script:app.HasExited) { throw 'Cancel unexpectedly closed the application.' }

        Send-FileCommand -Item 'New' -DialogTitle $applicationTitle
        Choose-Confirmation -ButtonName 'Discard'
        Wait-MainWindow -Title $applicationTitle | Out-Null
        Wait-ProjectDiagnostics -Description 'New Project to clear prior diagnostics' -Predicate {
            param($value) [string]::IsNullOrEmpty($value)
        } | Out-Null
        'Cancel preserved the dirty Project; Discard then established a clean New Project'
    }

    Invoke-SmokeStep -Name 'save-recovered-project-through-adopted-identity' -Action {
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $recoveryProjectName) -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - *$recoveryProjectName" | Out-Null
        Send-FileCommand -Item 'Save'
        Wait-MainWindow -Title "$applicationTitle - $recoveryProjectName" | Out-Null
        Wait-ProjectDiagnostics -Description 'successful Save to clear recovery diagnostics' -Predicate {
            param($value) [string]::IsNullOrEmpty($value)
        } | Out-Null
        $json = Get-Content -LiteralPath (Join-Path $workDir $recoveryProjectName) -Raw | ConvertFrom-Json
        if ((@($json.CustomMorphTargets.Target.SliderPresets) -join '|') -cne 'Alpha|Beta') {
            throw 'Recovered Project Save did not preserve canonical surviving relationships.'
        }
        'Save used the adopted identity and published a clean file-backed Project'
    }

    Invoke-SmokeStep -Name 'malformed-open-preserves-active-project-and-linked-retry' -Action {
        $titleBefore = $script:mainWindow.Current.Name
        $hashBefore = (Get-FileHash -LiteralPath (Join-Path $workDir $recoveryProjectName) -Algorithm SHA256).Hash
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $malformedProjectName) -ConfirmButton 'Open'
        Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $applicationTitle -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Wait-MainWindow -Title $titleBefore | Out-Null
        $text = Wait-ProjectDiagnostics -Description 'malformed Project diagnostic' -Predicate {
            param($value) $value.Contains('PROJECT_JSON_MALFORMED')
        }
        $failureCue = Find-OuterControl -ControlType 'Text' -Name 'Failure'
        $failureMessage = Get-FollowingControl -Element $failureCue -ControlType 'Text'
        if ($failureMessage.Current.Name -cne 'Open Project failed with 1 diagnostic.') {
            throw "Failed Open did not expose its related failure message: '$($failureMessage.Current.Name)'"
        }
        $failedActivity = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Failure — Open Project — Failed: Open Project failed with 1 diagnostic.') `
            -Description 'durable failed Open Activity record' -TimeoutSeconds $StepTimeoutSeconds
        foreach ($evidence in @('Attempt:', 'Effects committed: none', 'Diagnostics: PROJECT_JSON_MALFORMED',
                'Retry available: true')) {
            if (-not $failedActivity.Current.HelpText.Contains($evidence)) {
                throw "Failed Open Activity omitted '$evidence': '$($failedActivity.Current.HelpText)'"
            }
        }
        $hashAfter = (Get-FileHash -LiteralPath (Join-Path $workDir $recoveryProjectName) -Algorithm SHA256).Hash
        if ($hashAfter -cne $hashBefore) { throw 'Malformed Open changed the active Project bytes.' }

        Copy-Item -LiteralPath $FixtureProject -Destination (Join-Path $workDir $malformedProjectName) -Force
        Choose-Confirmation -ButtonName 'Retry'
        Wait-MainWindow -Title "$applicationTitle - $malformedProjectName" | Out-Null
        $retryCondition = New-UiaCondition -ControlType 'ListItem' -Name 'Success — Open Project — Completed: Project opened.'
        $retryActivity = Wait-UiaElement -Root $script:mainWindow -Condition $retryCondition -Description 'durable linked Retry Activity record' -TimeoutSeconds $StepTimeoutSeconds
        foreach ($evidence in @('Retry of attempt:', $malformedProjectName,
                'Effects committed: Project published', 'Diagnostics: none')) {
            if (-not $retryActivity.Current.HelpText.Contains($evidence)) {
                throw "Retried Open Activity omitted '$evidence': '$($retryActivity.Current.HelpText)'"
            }
        }
        $observations['malformedOpen'] = [ordered]@{
            preservedTitle = $titleBefore
            diagnostics = $text
            retryLinked = $true
            retrySource = $malformedProjectName
        }
        'malformed Open preserved active bytes; Retry re-read the repaired source as a linked successful attempt'
    }

    Invoke-SmokeStep -Name 'changed-source-open-rejects-stale-completion' -Action {
        $titleBefore = $script:mainWindow.Current.Name
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $cancellableProjectName) -ConfirmButton 'Open'
        $progressCondition = New-UiaCondition -ControlType 'ProgressBar' -Name 'Current operation progress'
        Wait-UiaCondition -Description 'Open parsing before selected-source replacement' -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $script:mainWindow -Condition $progressCondition
            if ($null -ne $candidate -and
                    $candidate.Current.HelpText -match 'Open Project, (Parsing|Validating) Project') {
                return $candidate
            }
        } | Out-Null
        Copy-Item -LiteralPath $FixtureProject -Destination (Join-Path $workDir $cancellableProjectName) -Force

        $staleCondition = New-UiaCondition -ControlType 'ListItem' `
            -Name 'Warning — Open Project — Completed with issues: Open result was stale.'
        $staleActivity = Wait-UiaElement -Root $script:mainWindow -Condition $staleCondition `
            -Description 'durable stale Open Activity' -TimeoutSeconds $StepTimeoutSeconds
        foreach ($evidence in @($cancellableProjectName, 'Effects committed: none', 'Diagnostics: STALE_RESULT')) {
            if (-not $staleActivity.Current.HelpText.Contains($evidence)) {
                throw "Stale Open Activity omitted '$evidence': '$($staleActivity.Current.HelpText)'"
            }
        }
        Wait-MainWindow -Title $titleBefore | Out-Null
        $observations['staleOpen'] = [ordered]@{
            preservedTitle = $titleBefore
            changedInput = $cancellableProjectName
            diagnostic = 'STALE_RESULT'
            effects = 'none'
        }
        New-CancellableProjectFixture -Path (Join-Path $workDir $cancellableProjectName)
        'changing the selected source during detached parsing produced stale Activity and committed no effect'
    }

    Invoke-SmokeStep -Name 'central-job-admission-progress-and-cancellation' -Action {
        $titleBefore = $script:mainWindow.Current.Name
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $cancellableProjectName) -ConfirmButton 'Open'
        $progressCondition = New-UiaCondition -ControlType 'ProgressBar' -Name 'Current operation progress'
        $progress = Wait-UiaCondition -Description 'truthful active Open phase progress' -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $script:mainWindow -Condition $progressCondition
            if ($null -ne $candidate -and
                    $candidate.Current.HelpText -match 'Open Project, (Reading|Parsing|Validating) Project') {
                return $candidate
            }
        }
        $cancel = Wait-UiaCondition -Description 'enabled Open cancellation control' -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $script:mainWindow -Condition (
                New-UiaCondition -ControlType 'Button' -Name 'Cancel current operation')
            if ($null -ne $candidate -and $candidate.Current.IsEnabled) { return $candidate }
        }
        $progressEvidence = $progress.Current.HelpText

        $fileMenu = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'MenuItem' -Name 'File') -Description "'File' menu during active Open" -TimeoutSeconds $StepTimeoutSeconds
        Invoke-UiaElement -Element $fileMenu
        foreach ($blockedItem in @('New', 'Open…', 'Save', 'Save As…')) {
            $item = Wait-UiaElement -Root $script:mainWindow -Condition (
                New-UiaCondition -ControlType 'MenuItem' -Name $blockedItem) -Description "'$blockedItem' during active Open" -TimeoutSeconds $StepTimeoutSeconds
            if ($item.Current.IsEnabled) { throw "$blockedItem remained enabled while Open owned admission." }
        }
        $exitItem = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'MenuItem' -Name 'Exit') -Description "'Exit' during active Open" -TimeoutSeconds $StepTimeoutSeconds
        if (-not $exitItem.Current.IsEnabled) { throw 'Exit was disabled while Open owned admission.' }
        Send-UiaAccelerator -Window $script:mainWindow -Keys '{ESC}'

        Invoke-UiaElement -Element $cancel
        $cancelledCondition = New-UiaCondition -ControlType 'ListItem' -Name 'Information — Open Project — Cancelled: Cancellation completed.'
        $cancelledActivity = Wait-UiaElement -Root $script:mainWindow -Condition $cancelledCondition -Description 'durable cancelled Open Activity' -TimeoutSeconds $StepTimeoutSeconds
        foreach ($evidence in @($cancellableProjectName, 'Effects committed: none', 'Diagnostics: none')) {
            if (-not $cancelledActivity.Current.HelpText.Contains($evidence)) {
                throw "Cancelled Open Activity omitted '$evidence': '$($cancelledActivity.Current.HelpText)'"
            }
        }
        Wait-MainWindow -Title $titleBefore | Out-Null
        $observations['centralJobCancellation'] = [ordered]@{
            progress = $progressEvidence
            preservedTitle = $titleBefore
            effects = 'none'
        }
        'one Open owned global admission, published truthful progress, accepted Cancel, and committed no effect'
    }

    Invoke-SmokeStep -Name 'failed-save-preserves-dirty-project-then-save-as-recovers' -Action {
        $vanishingParent = Join-Path $workDir 'vanishing'
        New-Item -ItemType Directory -Path $vanishingParent -Force | Out-Null
        $vanishingProject = Join-Path $vanishingParent 'vanishing-recovery.jbs2bg'
        Copy-Item -LiteralPath $FixtureRecoveryProject -Destination $vanishingProject
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path $vanishingProject -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - *vanishing-recovery.jbs2bg" | Out-Null
        Remove-Item -LiteralPath $vanishingProject -Force
        Remove-Item -LiteralPath $vanishingParent -Force

        Send-FileCommand -Item 'Save'
        Wait-MainWindow -Title "$applicationTitle - *vanishing-recovery.jbs2bg" | Out-Null
        $failure = Wait-ProjectDiagnostics -Description 'failed Save diagnostic' -Predicate {
            param($value) $value.Contains('PROJECT_FILE_WRITE_FAILED')
        }
        $retryTarget = Join-Path $workDir $retrySaveName
        Send-FileCommand -Item 'Save As…' -DialogTitle $saveDialogTitle
        Complete-FileDialog -Title $saveDialogTitle -Path $retryTarget -ConfirmButton 'Save'
        Wait-MainWindow -Title "$applicationTitle - $retrySaveName" | Out-Null
        if (-not (Test-Path -LiteralPath $retryTarget -PathType Leaf)) { throw 'Save As retry did not publish a Project.' }
        $observations['saveFailureRecovery'] = [ordered]@{ diagnostic = $failure; retry = $retrySaveName }
        'failed Save preserved dirty identity and diagnostics; Save As retry recovered to a clean identity'
    }

    Invoke-SmokeStep -Name 'active-job-shutdown-cancel-resume-then-discard' -Action {
        $shutdownProject = Join-Path $workDir $shutdownProjectName
        Copy-Item -LiteralPath $FixtureRecoveryProject -Destination $shutdownProject
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path $shutdownProject -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - *$shutdownProjectName" | Out-Null

        Send-FileCommand -Item 'Open…' -DialogTitle $applicationTitle
        Choose-Confirmation -ButtonName 'Discard'
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $cancellableProjectName) -ConfirmButton 'Open'
        $activeProgress = New-UiaCondition -ControlType 'ProgressBar' -Name 'Current operation progress'
        Wait-UiaElement -Root $script:mainWindow -Condition $activeProgress -Description 'Open progress before shutdown' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Close-UiaWindow -Window $script:mainWindow
        Choose-Confirmation -ButtonName 'Cancel'
        Wait-MainWindow -Title "$applicationTitle - *$shutdownProjectName" | Out-Null
        if ($script:app.HasExited) { throw 'Shutdown Cancel unexpectedly exited the application.' }

        Send-FileCommand -Item 'Open…' -DialogTitle $applicationTitle
        Choose-Confirmation -ButtonName 'Discard'
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $cancellableProjectName) -ConfirmButton 'Open'
        Wait-UiaElement -Root $script:mainWindow -Condition $activeProgress -Description 'Open progress after shutdown resume' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Close-UiaWindow -Window $script:mainWindow
        Choose-Confirmation -ButtonName 'Discard'
        $observations['activeJobShutdown'] = [ordered]@{
            cancellationSettledBeforePrompt = $true
            cancelResumedAdmission = $true
            secondCloseDiscarded = $true
        }
        Assert-PackagedExit
    }

    $passed = $true
}
catch {
    Write-Host "  [smoke] run failed: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    try {
        Restore-SystemAccessibilityPreferences -State $accessibilityState
    }
    catch {
        $passed = $false
        Write-Host "  [smoke] accessibility preference restoration failed: $($_.Exception.Message)" -ForegroundColor Red
    }
    $killed = @()
    if ($observations.Contains('extractedImage')) {
        foreach ($process in @(Get-ImageLauncherProcesses)) {
            if (-not $process.HasExited) {
                try {
                    $process.Kill()
                    $killed += $process.Id
                }
                catch {
                    # The process may exit between the HasExited check and Kill; cleanup remains best effort.
                }
            }
        }
    }
    $stdout = ''
    $stderr = ''
    if ($script:stdoutTask) {
        try { $stdout = $script:stdoutTask.Result } catch { <# Forced cleanup may tear down the pipe. #> }
    }
    if ($script:stderrTask) {
        try { $stderr = $script:stderrTask.Result } catch { <# Forced cleanup may tear down the pipe. #> }
    }
    Set-Content -LiteralPath (Join-Path $diagnosticsDir 'launcher-stdout.txt') -Value $stdout -Encoding utf8
    Set-Content -LiteralPath (Join-Path $diagnosticsDir 'launcher-stderr.txt') -Value $stderr -Encoding utf8
    $restricted = @($stderr -split '\r?\n' | Where-Object {
            $_ -match 'restricted method|native access|--enable-native-access'
        })
    $evidence = [ordered]@{
        schema = 'bs2bg.windows-app-image-smoke/10'
        recordedAtUtc = $startedAt.ToString('o')
        passed = $passed
        expectedAppVersion = $ExpectedAppVersion
        archive = $ArchivePath
        timeouts = [ordered]@{
            startupSeconds = $StartupTimeoutSeconds
            stepSeconds = $StepTimeoutSeconds
            exitSeconds = $ExitTimeoutSeconds
            expectedDpiPercent = $ExpectedDpiPercent
        }
        steps = $steps
        observations = $observations
        process = [ordered]@{
            expectedModel = 'single-launcher-process'
            model = $(if ($observations.Contains('observedProcessModel')) {
                    $observations['observedProcessModel']
                } else { $null })
            applicationPid = $(if ($script:app) { $script:app.Id } else { $null })
            exitCode = $(if ($observations.Contains('exit')) { $observations['exit'].exitCode } else { $null })
            exitWaitSeconds = $(if ($observations.Contains('exit')) {
                    $observations['exit'].exitWaitSeconds
                } else { $null })
            killed = $killed
        }
        diagnostics = [ordered]@{
            directory = 'target/reproducibility/smoke-diagnostics/'
            stderrLines = @($stderr -split '\r?\n' | Where-Object { $_ }).Count
            stderrExcerpt = @($stderr -split '\r?\n' | Where-Object { $_ } | Select-Object -First 20)
            nativeAccessWarnings = $restricted
            workbenchTree = 'smoke-diagnostics/uia-tree-workbench.txt'
            responsiveWorkbenchTree = 'smoke-diagnostics/uia-tree-workbench-responsive.txt'
            highContrastScreenshot = 'smoke-diagnostics/workbench-high-contrast.png'
            reducedMotionScreenshot = 'smoke-diagnostics/workbench-reduced-motion.png'
        }
        workRoot = $WorkRoot
        workRootKept = [bool]$KeepWorkRoot
    }
    $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $EvidencePath -Encoding utf8
    Write-SmokeStep "evidence written to $EvidencePath"
    if (-not $KeepWorkRoot -and (Test-Path -LiteralPath $WorkRoot)) {
        try {
            Remove-Item -LiteralPath $WorkRoot -Recurse -Force
        }
        catch {
            Write-SmokeStep "could not remove $WorkRoot : $($_.Exception.Message)"
        }
    }
}

if ($passed) { exit 0 } else { exit 1 }
