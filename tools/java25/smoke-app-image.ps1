#Requires -Version 7.0
<#
.SYNOPSIS
    Drives the packaged BS2BG Preview Workbench through its lifecycle, platform, Templates, and Morphs contracts
    (issues #98-#107).

.DESCRIPTION
    Extracts the app-image archive to a clean temporary root, launches the real BS2BG.exe without any host Java
    discovery path, proves that the launcher process hosts the bundled JVM, and drives the Workbench through
    Windows UI Automation by accessible role and name. The run covers typed navigation, focus cycling and return,
    captured Output generation and tab inspection, Output drawer interaction, responsive/minimum geometry,
    live themes, High Contrast, reduced motion,
    accessibility semantics, notifications, typed dialogs, startup, New, Open, Save, Save As, Project recovery,
    centralized admission, measured progress, cancellation, linked retry, stale-safe Activity evidence,
    malformed/failed operation preservation, complete pointer-free Slider Preset choice editing and management,
    keyboard and semantic-pointer Custom Morph Target authoring, coordinated dirty shutdown, and bounded exit.
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
$templatesManagedName = 'templates-managed.jbs2bg'
$morphsManagedName = 'morphs-managed.jbs2bg'
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
    Its default stays below the production parser's 5,000,000-token safety limit.
#>
function New-CancellableProjectFixture {
    param([Parameter(Mandatory)] [string]$Path, [int]$ChoiceCount = 300000)
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
    Completes the native Windows directory chooser used by complete Output export.
.PARAMETER Title
    Exact native dialog title.
.PARAMETER Path
    Existing directory selected through the address bar.
#>
function Complete-DirectoryDialog {
    param([string]$Title, [string]$Path)
    $dialog = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $Title -TimeoutSeconds $StepTimeoutSeconds
    $focusableCondition = New-Object System.Windows.Automation.OrCondition(@(
        (New-UiaCondition -ControlType 'Edit'),
        (New-UiaCondition -ControlType 'List'),
        (New-UiaCondition -ControlType 'Tree'),
        (New-UiaCondition -ControlType 'Button')))
    $focusTarget = Wait-UiaCondition -Description "focusable child in '$Title'" `
        -TimeoutSeconds $StepTimeoutSeconds -Test {
        foreach ($candidate in (Find-UiaElements -Root $dialog -Condition $focusableCondition)) {
            if ($candidate.Current.IsKeyboardFocusable) { return $candidate }
        }
    }
    # The top-level native dialog is not focusable; Ctrl+L is routed from one of its real child controls.
    Send-UiaKeysToElement -Element $focusTarget -Keys '^l' -TimeoutSeconds $StepTimeoutSeconds
    $dialogProcessId = $dialog.Current.ProcessId
    $address = Wait-UiaCondition -Description "address bar in '$Title'" -TimeoutSeconds $StepTimeoutSeconds -Test {
        $focused = [System.Windows.Automation.AutomationElement]::FocusedElement
        if ($null -ne $focused -and $focused.Current.ProcessId -eq $dialogProcessId `
                -and $focused.Current.ControlType -eq [System.Windows.Automation.ControlType]::Edit) {
            $focused
        }
    }
    Set-UiaValue -Element $address -Value $Path
    Send-UiaKeysToElement -Element $address -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
    $confirmCondition = New-Object System.Windows.Automation.OrCondition(@(
        (New-UiaCondition -ControlType 'Button' -Name 'Select Folder'),
        (New-UiaCondition -ControlType 'Button' -Name 'Open'),
        (New-UiaCondition -ControlType 'Button' -Name 'Select')))
    $confirm = Wait-UiaElement -Root $dialog -Condition $confirmCondition `
        -Description "directory confirmation in '$Title'" -TimeoutSeconds $StepTimeoutSeconds
    Invoke-UiaElement -Element $confirm
}

function Complete-MultipleFileDialog {
    param(
        [Parameter(Mandatory)] [string] $Title,
        [Parameter(Mandatory)] [string[]] $Paths,
        [Parameter(Mandatory)] [string] $ConfirmButton
    )
    if ($Paths.Count -eq 0) { throw 'At least one file path is required.' }
    $dialog = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $Title -TimeoutSeconds $StepTimeoutSeconds
    $fileName = Wait-UiaElement -Root $dialog -Condition (
        New-UiaCondition -ControlType 'Edit' -Name 'File name:') `
        -Description "$Title file name" -TimeoutSeconds $StepTimeoutSeconds
    $quotedPaths = ($Paths | ForEach-Object { '"' + $_ + '"' }) -join ' '
    Set-UiaValue -Element $fileName -Value $quotedPaths
    $confirmRole = New-Object System.Windows.Automation.OrCondition(@(
        (New-UiaCondition -ControlType 'Pane' -Name $ConfirmButton),
        (New-UiaCondition -ControlType 'SplitButton' -Name $ConfirmButton),
        (New-UiaCondition -ControlType 'Button' -Name $ConfirmButton)))
    $confirm = Wait-UiaElement -Root $dialog -Condition $confirmRole -Description "$Title $ConfirmButton button" `
        -TimeoutSeconds $StepTimeoutSeconds
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
    Returns the selected Output tab's keyboard-reachable read-only document text.
.PARAMETER Region
    Named Generated Output tab region that owns the selected content.
.PARAMETER TabName
    Stable accessible TabItem name whose selection establishes the document identity.
.PARAMETER Description
    Bounded-wait description used in diagnostics.
.NOTES
    JavaFX 25 exposes the selected TextArea as an unnamed Edit, so the locator combines its Edit role with the
    named TabItem selection and named parent-region ownership instead of relying on an unavailable AutomationId.
#>
function Get-SelectedOutputText {
    param(
        [Parameter(Mandatory)] $Region,
        [Parameter(Mandatory)] [string]$TabName,
        [Parameter(Mandatory)] [string]$Description
    )
    $tab = Wait-UiaElement -Root $Region -Condition (
        New-UiaCondition -ControlType 'TabItem' -Name $TabName) -Description "$TabName Output tab" `
        -TimeoutSeconds $StepTimeoutSeconds
    Wait-UiaCondition -Description "$TabName Output tab selection" -TimeoutSeconds $StepTimeoutSeconds -Test {
        if (Get-UiaSelectionState -Element $tab) { $tab }
    } | Out-Null
    $document = Wait-UiaElement -Root $Region -Condition (New-UiaCondition -ControlType 'Edit') `
        -Description $Description -TimeoutSeconds $StepTimeoutSeconds
    if (-not $document.Current.IsKeyboardFocusable -or -not (Get-UiaReadOnlyState -Element $document)) {
        throw "$TabName Output text is not keyboard-reachable and read-only."
    }
    return [pscustomobject]@{ Element = $document; Text = (Get-UiaText -Element $document) }
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
        $settingsTransaction = Join-Path $workDir '.bs2bg-settings-stage-packaged-recovery'
        New-Item -ItemType Directory -Path $settingsTransaction -Force | Out-Null
        $repositorySettings = (Resolve-Path (Join-Path $PSScriptRoot '..\..\settings.json')).Path
        $repositoryUunpSettings = (Resolve-Path (Join-Path $PSScriptRoot '..\..\settings_UUNP.json')).Path
        Copy-Item -LiteralPath $repositorySettings -Destination (Join-Path $settingsTransaction 'standard.backup')
        Copy-Item -LiteralPath $repositoryUunpSettings -Destination (Join-Path $settingsTransaction 'uunp.backup')
        Copy-Item -LiteralPath $repositorySettings -Destination (Join-Path $workDir 'settings.json')
        $observations['extractedImage'] = Join-Path $imageRoot $LauncherName
        $observations['launcher'] = $launcherPath
        $observations['launcherSha256'] = (Get-FileHash -LiteralPath $launcherPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $observations['archiveSha256'] = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $observations['workingDirectory'] = $workDir
        $observations['cancellableProjectBytes'] = (Get-Item -LiteralPath $cancellableProject).Length
        "extracted archive, installed four Project fixtures, and staged interrupted Settings recovery in $workDir"
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
        if (Test-Path -LiteralPath (Join-Path $workDir '.bs2bg-settings-stage-packaged-recovery')) {
            throw 'Workbench startup did not remove the recovered Settings transaction.'
        }
        $activity = Find-OuterControl -ControlType 'List' -Name 'Activity'
        $settingsRecovery = Wait-UiaCondition -Description 'durable packaged Settings recovery Activity' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $items = Find-UiaElements -Root $activity -Condition (New-UiaCondition -ControlType 'ListItem')
            foreach ($item in $items) {
                if ($item.Current.Name.Contains('Load Settings') `
                        -and $item.Current.Name.Contains('SETTINGS_PUBLICATION_RECOVERED')) { return $item }
            }
        }
        if (-not $settingsRecovery.Current.HelpText.Contains('Diagnostics: none')) {
            # Startup Settings evidence is not a coordinator job, so it intentionally has no JobDetails payload.
            $observations['settingsRecoveryActivity'] = $settingsRecovery.Current.Name
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
            if ($entry.Value -ceq 'Templates') {
                Wait-FocusedControl -ControlType 'List' -Name 'Slider Presets' | Out-Null
            }
            elseif ($entry.Value -ceq 'Morphs') {
                Wait-FocusedControl -ControlType 'List' -Name 'Custom Morph Targets' | Out-Null
            }
            elseif ($entry.Value -ceq 'Settings') {
                Wait-FocusedControl -ControlType 'List' -Name 'Settings entries' | Out-Null
            }
            else {
                Wait-FocusedControl -ControlType 'Button' -Name "$($entry.Value) primary content" | Out-Null
            }
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
        Wait-FocusedControl -ControlType 'List' -Name 'Custom Morph Targets' | Out-Null
        if ((Get-UiaToggleState -Element (Get-AreaButton -Name 'Output')) -ne 'Off') {
            throw 'Escape did not close the Output drawer.'
        }

        Send-UiaKeys -ProcessId $script:app.Id -Keys '{F6}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Text' -Name 'Custom Morph Target editor' | Out-Null
        $ctrlBackquote = '^' + [char]96
        Send-UiaKeys -ProcessId $script:app.Id -Keys $ctrlBackquote -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Text' -Name 'Output generated text' | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys $ctrlBackquote -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Text' -Name 'Custom Morph Target editor' | Out-Null

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^4' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Text' -Name 'Output generated text' | Out-Null

        $morphsRail = Get-AreaButton -Name 'Morphs'
        $morphsRail.SetFocus()
        Wait-UiaKeyboardFocus -Element $morphsRail -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $focusCycle = @(
            @{ Role = 'List'; Name = 'Custom Morph Targets' },
            @{ Role = 'Text'; Name = 'Custom Morph Target editor' },
            @{ Role = 'Text'; Name = 'Custom Morph Target inspector: no selection' },
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
        Wait-FocusedControl -ControlType 'Text' -Name 'Custom Morph Target editor' | Out-Null
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
        Wait-FocusedControl -ControlType 'Text' -Name 'Custom Morph Target inspector: no selection' | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $inspectorLauncher -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^2' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'List' -Name 'Custom Morph Targets' | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element (Get-AreaButton -Name 'Morphs') -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        $minimumMetrics = Resize-UiaClient -Window $script:mainWindow -LogicalWidth 700 -LogicalHeight 500 `
            -AllowMinimumClamp -TimeoutSeconds $StepTimeoutSeconds
        if ([math]::Abs($minimumMetrics.LogicalClientWidth - 800.0) -gt 2.0 -or
                [math]::Abs($minimumMetrics.LogicalClientHeight - 600.0) -gt 2.0) {
            throw "Workbench minimum client geometry did not settle at 800x600: $($minimumMetrics.LogicalClientWidth)x$($minimumMetrics.LogicalClientHeight)."
        }
        $editor = Find-OuterControl -ControlType 'Text' -Name 'Custom Morph Target editor'
        Assert-ControlInsideClient -Element $editor -Metrics $minimumMetrics
        $listLauncher = Find-OuterControl -ControlType 'Button' -Name 'Open Morphs list'
        Send-UiaKeysToElement -Element $listLauncher -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        $minimumTargetList = Wait-FocusedControl -ControlType 'List' -Name 'Custom Morph Targets'
        Assert-ControlInsideClient -Element $minimumTargetList -Metrics $minimumMetrics
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $listLauncher -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $inspectorLauncher = Find-OuterControl -ControlType 'Button' -Name 'Open Morphs inspector'
        Send-UiaKeysToElement -Element $inspectorLauncher -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        $minimumInspector = Wait-FocusedControl -ControlType 'Text' `
            -Name 'Custom Morph Target inspector: no selection'
        Assert-ControlInsideClient -Element $minimumInspector -Metrics $minimumMetrics
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $inspectorLauncher -TimeoutSeconds $StepTimeoutSeconds | Out-Null
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

    Invoke-SmokeStep -Name 'edit-and-manage-slider-presets-without-pointer' -Action {
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $openedProjectName) -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - $openedProjectName" | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^1' -TimeoutSeconds $StepTimeoutSeconds
        Wait-AreaSelected -Name 'Templates' | Out-Null

        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        foreach ($presetName in @('CBBE Curvy', 'UUNP Athletic')) {
            Wait-UiaElement -Root $presetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name $presetName) `
                -Description "Slider Preset '$presetName'" -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        }
        Set-SystemHighContrast -Enabled:$true
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: High Contrast theme') `
            -Description 'populated Templates High Contrast state' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $templatesHighContrastScreenshot = Join-Path $diagnosticsDir 'workbench-templates-high-contrast.png'
        Save-Screenshot -Path $templatesHighContrastScreenshot
        Set-SystemHighContrast -Enabled:$false
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: Dark theme') `
            -Description 'Templates theme restored after High Contrast' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $filterLabel = Find-OuterControl -ControlType 'Text' -Name 'Filter Slider Presets:'
        $filter = Get-FollowingControl -Element $filterLabel -ControlType 'Edit'
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $filter -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $filter -Keys 'uunp' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'UUNP Athletic') `
            -Description 'filtered UUNP Slider Preset' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        if ($null -ne (Find-UiaElement -Root $presetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name 'CBBE Curvy'))) {
            throw 'Slider Preset filtering left the hidden CBBE identity visible.'
        }
        Send-UiaKeysToElement -Element $filter -Keys '^a{BACKSPACE}' -TimeoutSeconds $StepTimeoutSeconds

        $nameLabel = Find-OuterControl -ControlType 'Text' -Name 'Slider Preset name:'
        $nameInput = Get-FollowingControl -Element $nameLabel -ControlType 'Edit'
        Send-UiaKeysToElement -Element $nameInput -Keys 'CBBE Curvy{TAB}{ENTER}' `
            -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $nameInput -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Wait-ProjectDiagnostics -Description 'duplicate Slider Preset create validation' -Predicate {
            param($value) $value.Contains('SLIDER_PRESET_NAME_DUPLICATE')
        } | Out-Null
        Send-UiaKeysToElement -Element $nameInput -Keys '^a{BACKSPACE}Delta Created{TAB}{ENTER}' `
            -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Delta Created') `
            -Description 'created Slider Preset' -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        Send-UiaKeysToElement -Element $presetList -Keys '{HOME}' -TimeoutSeconds $StepTimeoutSeconds
        $cbbe = Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'CBBE Curvy') `
            -Description 'selected CBBE Slider Preset' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'CBBE identity selected by keyboard' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $cbbe) { $cbbe }
        } | Out-Null

        $waistEnabled = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'CheckBox' -Name 'Enable Waist in Slider Preset CBBE Curvy') `
            -Description 'CBBE Waist enable control' -TimeoutSeconds $StepTimeoutSeconds
        $waistMinimum = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Slider' -Name 'Waist Minimum in Slider Preset CBBE Curvy') `
            -Description 'CBBE Waist minimum control' -TimeoutSeconds $StepTimeoutSeconds
        $waistMaximum = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Slider' -Name 'Waist Maximum in Slider Preset CBBE Curvy') `
            -Description 'CBBE Waist maximum control' -TimeoutSeconds $StepTimeoutSeconds
        $waistPreview = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Waist BodyGen preview') `
            -Description 'CBBE Waist BodyGen preview' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeysToElement -Element $waistMinimum -Keys '{HOME}{RIGHT 25}' `
            -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'Waist minimum and live preview edit' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaRangeValue -Element $waistMinimum) -eq 25 `
                    -and $waistPreview.Current.HelpText.Contains('Waist@0.35:0.74')) { $waistMinimum }
        } | Out-Null
        Send-UiaKeysToElement -Element $waistMaximum -Keys '{HOME}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'reversed range gesture clamps without losing focus or selection' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaRangeValue -Element $waistMaximum) -eq 25 `
                    -and $waistMaximum.Current.HasKeyboardFocus `
                    -and (Get-UiaSelectionState -Element $cbbe)) { $waistMaximum }
        } | Out-Null
        Send-UiaKeysToElement -Element $waistMaximum -Keys '{END}' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeysToElement -Element $waistEnabled -Keys ' ' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'disabled Waist omission state' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaToggleState -Element $waistEnabled) -eq 'Off' `
                    -and -not $waistMinimum.Current.IsEnabled `
                    -and $waistPreview.Current.HelpText.Contains('Omitted from generated output')) { $waistEnabled }
        } | Out-Null
        Send-UiaKeysToElement -Element $waistEnabled -Keys ' ' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 're-enabled Waist focus and range state' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaToggleState -Element $waistEnabled) -eq 'On' `
                    -and $waistMinimum.Current.IsEnabled `
                    -and $waistEnabled.Current.HasKeyboardFocus) { $waistEnabled }
        } | Out-Null

        $fiftyAll = Find-OuterControl -ControlType 'Button' -Name 'Set all Slider choice values to 50 percent'
        Send-UiaKeysToElement -Element $fiftyAll -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'atomic 50 All gang operation' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaRangeValue -Element $waistMinimum) -eq 50 `
                    -and (Get-UiaRangeValue -Element $waistMaximum) -eq 50 `
                    -and $fiftyAll.Current.HasKeyboardFocus) { $fiftyAll }
        } | Out-Null
        $gangMinimum = Find-OuterControl -ControlType 'CheckBox' -Name 'Gang all minimum Slider choice values'
        $gangMaximum = Find-OuterControl -ControlType 'CheckBox' -Name 'Gang all maximum Slider choice values'
        Send-UiaKeysToElement -Element $gangMinimum -Keys ' ' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'All-Min gang locks editor rows' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaToggleState -Element $gangMinimum) -eq 'On' -and -not $waistEnabled.Current.IsEnabled) {
                $gangMinimum
            }
        } | Out-Null
        Send-UiaKeysToElement -Element $gangMaximum -Keys ' ' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'All-Max replaces All-Min gang mode' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaToggleState -Element $gangMinimum) -eq 'Off' `
                    -and (Get-UiaToggleState -Element $gangMaximum) -eq 'On' `
                    -and -not $waistEnabled.Current.IsEnabled) { $gangMaximum }
        } | Out-Null
        Send-UiaKeysToElement -Element $gangMaximum -Keys ' ' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'gang unlock restores row editing' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaToggleState -Element $gangMaximum) -eq 'Off' -and $waistEnabled.Current.IsEnabled) {
                $waistEnabled
            }
        } | Out-Null
        Send-UiaKeysToElement -Element $fiftyAll -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'final atomic 50 All state for persistence' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaRangeValue -Element $waistMinimum) -eq 50 `
                    -and (Get-UiaRangeValue -Element $waistMaximum) -eq 50) { $fiftyAll }
        } | Out-Null

        $profileLabel = Find-OuterControl -ControlType 'Text' -Name 'Profile:'
        $profile = Get-FollowingControl -Element $profileLabel -ControlType 'ComboBox'
        Send-UiaKeysToElement -Element $profile -Keys '{F4}{END}{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'UUNP profile removes Standard-only synthesized choice' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $standardOnly = Find-UiaElement -Root $script:mainWindow -Condition (
                New-UiaCondition -ControlType 'CheckBox' -Name 'Enable Ankles in Slider Preset CBBE Curvy')
            if ($null -eq $standardOnly -and (Get-UiaSelectionState -Element $cbbe)) { $profile }
        } | Out-Null
        Send-UiaKeysToElement -Element $profile -Keys '{F4}{HOME}{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'CheckBox' -Name 'Enable Ankles in Slider Preset CBBE Curvy') `
            -Description 'restored Standard profile synthesized choice' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        if (-not (Get-UiaSelectionState -Element $cbbe)) {
            throw 'Profile switching did not preserve the selected Slider Preset identity.'
        }
        $templatesRail = Get-AreaButton -Name 'Templates'
        $templatesRail.SetFocus()
        foreach ($target in @(
                @{ Role = 'List'; Name = 'Slider Presets' },
                @{ Role = 'CheckBox'; Name = 'Enable Ankles in Slider Preset CBBE Curvy' },
                @{ Role = 'Button'; Name = 'Rename Slider Preset CBBE Curvy' })) {
            Send-UiaKeys -ProcessId $script:app.Id -Keys '{F6}' -TimeoutSeconds $StepTimeoutSeconds
            Wait-FocusedControl -ControlType $target.Role -Name $target.Name | Out-Null
        }
        Send-UiaKeysToElement -Element $nameInput -Keys '^a{BACKSPACE}Curvy Copy{TAB}{TAB}{ENTER}' `
            -TimeoutSeconds $StepTimeoutSeconds
        $copy = Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Curvy Copy') `
            -Description 'duplicated Slider Preset' -TimeoutSeconds $StepTimeoutSeconds

        Send-UiaKeysToElement -Element $presetList -Keys '{HOME}{DOWN}{F2}' -TimeoutSeconds $StepTimeoutSeconds
        $renameRow = Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Rename Slider Preset Curvy Copy') `
            -Description 'inline rename row' -TimeoutSeconds $StepTimeoutSeconds
        $rename = Wait-UiaElement -Root $renameRow -Condition (
            New-UiaCondition -ControlType 'Edit') -Description 'inline rename field' `
            -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $rename -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $rename -Keys '^a{BACKSPACE}Temporary Name{ESC}' `
            -TimeoutSeconds $StepTimeoutSeconds
        $copy = Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Curvy Copy') `
            -Description 'Esc-cancelled inline rename identity' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'Esc restores selection to cancelled rename identity' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $copy) { $copy }
        } | Out-Null
        Send-UiaKeysToElement -Element $presetList -Keys '{F2}' -TimeoutSeconds $StepTimeoutSeconds
        $renameRow = Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Rename Slider Preset Curvy Copy') `
            -Description 'reopened inline rename row' -TimeoutSeconds $StepTimeoutSeconds
        $rename = Wait-UiaElement -Root $renameRow -Condition (
            New-UiaCondition -ControlType 'Edit') -Description 'reopened inline rename field' `
            -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $rename -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $rename -Keys '^a{BACKSPACE}UUNP Athletic{ENTER}' `
            -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $rename -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Wait-ProjectDiagnostics -Description 'duplicate inline rename validation' -Predicate {
            param($value) $value.Contains('SLIDER_PRESET_NAME_DUPLICATE')
        } | Out-Null
        Send-UiaKeysToElement -Element $rename -Keys '^a{BACKSPACE}Curvy Copy Renamed{ENTER}' `
            -TimeoutSeconds $StepTimeoutSeconds
        $renamed = Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Curvy Copy Renamed') `
            -Description 'renamed Slider Preset identity' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'renamed identity retains logical selection' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $renamed) { $renamed }
        } | Out-Null

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeysToElement -Element $filter -Keys 'uunp' -TimeoutSeconds $StepTimeoutSeconds
        $remove = Find-OuterControl -ControlType 'Button' -Name 'Remove Slider Preset'
        Wait-UiaCondition -Description 'filter-hidden selection clears management target' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (-not $remove.Current.IsEnabled) { $true }
        } | Out-Null
        Send-UiaKeysToElement -Element $filter -Keys '^a{BACKSPACE}' -TimeoutSeconds $StepTimeoutSeconds
        foreach ($identityName in @('CBBE Curvy', 'Curvy Copy Renamed')) {
            $identity = Wait-UiaElement -Root $presetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name $identityName) `
                -Description "restored visible identity '$identityName'" -TimeoutSeconds $StepTimeoutSeconds
            if (Get-UiaSelectionState -Element $identity) {
                throw "Clearing the filter silently restored Slider Preset '$($identity.Current.Name)'."
            }
        }

        $sortLabel = Find-OuterControl -ControlType 'Text' -Name 'Sort Slider Presets:'
        $sort = Get-FollowingControl -Element $sortLabel -ControlType 'ComboBox'
        Send-UiaKeysToElement -Element $sort -Keys '{HOME}{DOWN}' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeysToElement -Element $presetList -Keys 'c' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'type-ahead first visible C match' -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $presetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name 'Curvy Copy Renamed')
            if ($null -ne $candidate -and (Get-UiaSelectionState -Element $candidate)) { $candidate }
        } | Out-Null
        Send-UiaKeysToElement -Element $presetList -Keys 'c' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'repeated-character type-ahead cycle' -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $presetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name 'CBBE Curvy')
            if ($null -ne $candidate -and (Get-UiaSelectionState -Element $candidate)) { $candidate }
        } | Out-Null
        Start-Sleep -Milliseconds 800
        Send-UiaKeysToElement -Element $presetList -Keys 'u' -TimeoutSeconds $StepTimeoutSeconds
        $uunp = Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'UUNP Athletic') `
            -Description 'timeout-reset UUNP type-ahead target' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'type-ahead timeout resets prefix' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $uunp) { $uunp }
        } | Out-Null

        Send-UiaKeysToElement -Element $remove -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Cancel'
        Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'UUNP Athletic') `
            -Description 'Cancel preserves removed identity' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $remove -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Remove'
        Wait-UiaCondition -Description 'confirmed Slider Preset removal' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ($null -eq (Find-UiaElement -Root $presetList -Condition (
                    New-UiaCondition -ControlType 'ListItem' -Name 'UUNP Athletic'))) { $true }
        } | Out-Null

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeysToElement -Element $filter -Keys '^a{BACKSPACE}curvy copy renamed' `
            -TimeoutSeconds $StepTimeoutSeconds
        $clear = Find-OuterControl -ControlType 'Button' -Name 'Clear visible Slider Presets'
        Send-UiaKeysToElement -Element $clear -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Cancel'
        Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Curvy Copy Renamed') `
            -Description 'Cancel preserves visible clear scope' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $clear -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Clear'
        Wait-UiaCondition -Description 'confirmed filtered Slider Preset clearing' -TimeoutSeconds $StepTimeoutSeconds -Test {
            $remaining = Find-UiaElements -Root $presetList -Condition (New-UiaCondition -ControlType 'ListItem')
            if (@($remaining).Count -eq 0) { $true }
        } | Out-Null

        $managedPath = Join-Path $workDir $templatesManagedName
        Send-FileCommand -Item 'Save As…' -DialogTitle $saveDialogTitle
        Complete-FileDialog -Title $saveDialogTitle -Path $managedPath -ConfirmButton 'Save'
        Wait-MainWindow -Title "$applicationTitle - $templatesManagedName" | Out-Null
        $managed = Get-Content -LiteralPath $managedPath -Raw | ConvertFrom-Json
        if ((@($managed.SliderPresets.PSObject.Properties.Name) -join '|') -cne 'CBBE Curvy|Delta Created') {
            throw 'Filtered clear removed a hidden Slider Preset or retained a visible Slider Preset.'
        }
        if ((@($managed.CustomMorphTargets.'All|Female'.SliderPresets) -join '|') -cne 'CBBE Curvy') {
            throw 'Slider Preset management did not publish the complete Custom Morph Target cascade.'
        }
        if (@($managed.MorphedNPCs.Lydia.SliderPresets).Count -ne 0) {
            throw 'Slider Preset removal did not clear the NPC Morph Assignment relationship.'
        }
        $managedWaist = @($managed.SliderPresets.'CBBE Curvy'.SetSliders | Where-Object { $_.name -ceq 'Waist' })
        if ($managed.SliderPresets.'CBBE Curvy'.isUUNP -or $managedWaist.Count -ne 1 `
                -or -not $managedWaist[0].enabled -or $managedWaist[0].pctMin -ne 50 `
                -or $managedWaist[0].pctMax -ne 50) {
            throw 'Slider choice profile, enable, or gang edits did not survive canonical Save As.'
        }

        Send-FileCommand -Item 'New'
        Wait-MainWindow -Title $applicationTitle | Out-Null
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path $managedPath -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - $templatesManagedName" | Out-Null
        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeysToElement -Element $filter -Keys '^a{BACKSPACE}' -TimeoutSeconds $StepTimeoutSeconds
        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'CBBE Curvy') `
            -Description 'saved and reopened Slider Preset identity' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $unneededListScroll = Find-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ScrollBar')
        if ($null -ne $unneededListScroll -and -not $unneededListScroll.Current.IsOffscreen) {
            throw 'A one-row Slider Preset list exposed an unnecessary inner scrollbar.'
        }
        Send-UiaKeysToElement -Element $presetList -Keys 'c' -TimeoutSeconds $StepTimeoutSeconds
        $reopenedCbbe = Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'CBBE Curvy') `
            -Description 'reopened CBBE type-ahead target' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'reopened CBBE identity selected before responsive checks' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $reopenedCbbe) { $reopenedCbbe }
        } | Out-Null
        $reopenedWaistMinimum = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Slider' -Name 'Waist Minimum in Slider Preset CBBE Curvy') `
            -Description 'reopened Waist minimum' -TimeoutSeconds $StepTimeoutSeconds
        $reopenedWaistMaximum = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Slider' -Name 'Waist Maximum in Slider Preset CBBE Curvy') `
            -Description 'reopened Waist maximum' -TimeoutSeconds $StepTimeoutSeconds
        $reopenedWaistPreview = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Waist BodyGen preview') `
            -Description 'reopened Waist preview' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'CheckBox' -Name 'Enable Ankles in Slider Preset CBBE Curvy') `
            -Description 'reopened Standard profile choice' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        if ((Get-UiaRangeValue -Element $reopenedWaistMinimum) -ne 50 `
                -or (Get-UiaRangeValue -Element $reopenedWaistMaximum) -ne 50 `
                -or -not $reopenedWaistPreview.Current.HelpText.Contains('Waist@0.5')) {
            throw 'Reopened Slider choice range or exact BodyGen preview did not match the saved edit.'
        }
        Set-SystemHighContrast -Enabled:$true
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: High Contrast theme') `
            -Description 'Slider editor High Contrast state' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $templatesEditorHighContrastScreenshot = Join-Path $diagnosticsDir 'workbench-templates-editor-high-contrast.png'
        Save-Screenshot -Path $templatesEditorHighContrastScreenshot
        Set-SystemHighContrast -Enabled:$false
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: Dark theme') `
            -Description 'Slider editor theme restored after High Contrast' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $templatesNarrow = Resize-UiaClient -Window $script:mainWindow -LogicalWidth 1199 -LogicalHeight 700 `
            -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        $filterLabel = Find-OuterControl -ControlType 'Text' -Name 'Filter Slider Presets:'
        $filter = Get-FollowingControl -Element $filterLabel -ControlType 'Edit'
        Wait-UiaKeyboardFocus -Element $filter -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $templatesNarrow = Get-UiaWindowMetrics -Window $script:mainWindow
        $templatesSurface = Find-OuterControl -ControlType 'Pane' -Name 'Slider Preset management'
        # JavaFX reports stale descendant bounds after overlay reparenting; the named scroll boundary plus focus is
        # the reliable clipping/reachability evidence for this surface.
        Assert-ControlInsideClient -Element $templatesSurface -Metrics $templatesNarrow
        $reopenedWaistMinimum.SetFocus()
        Wait-UiaKeyboardFocus -Element $reopenedWaistMinimum -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Wait-UiaCondition -Description 'narrow Waist row scrolled into view' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (-not $reopenedWaistMinimum.Current.IsOffscreen) { $reopenedWaistMinimum }
        } | Out-Null
        $templatesNarrowScreenshot = Join-Path $diagnosticsDir 'workbench-templates-narrow.png'
        Save-Screenshot -Path $templatesNarrowScreenshot
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{F7}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-FocusedControl -ControlType 'Button' -Name 'Rename Slider Preset CBBE Curvy' | Out-Null
        $narrowGangMinimum = Find-OuterControl -ControlType 'CheckBox' `
            -Name 'Gang all minimum Slider choice values'
        $narrowGangMinimum.SetFocus()
        Wait-UiaKeyboardFocus -Element $narrowGangMinimum -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Assert-ControlInsideClient -Element $narrowGangMinimum -Metrics (Get-UiaWindowMetrics -Window $script:mainWindow)
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds

        $templatesMinimum = Resize-UiaClient -Window $script:mainWindow -LogicalWidth 700 -LogicalHeight 500 `
            -AllowMinimumClamp -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        $filterLabel = Find-OuterControl -ControlType 'Text' -Name 'Filter Slider Presets:'
        $filter = Get-FollowingControl -Element $filterLabel -ControlType 'Edit'
        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        $clear = Find-OuterControl -ControlType 'Button' -Name 'Clear visible Slider Presets'
        Wait-UiaKeyboardFocus -Element $filter -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $templatesMinimum = Get-UiaWindowMetrics -Window $script:mainWindow
        $templatesSurface = Find-OuterControl -ControlType 'Pane' -Name 'Slider Preset management'
        Assert-ControlInsideClient -Element $templatesSurface -Metrics $templatesMinimum
        $reopenedWaistMinimum.SetFocus()
        Wait-UiaKeyboardFocus -Element $reopenedWaistMinimum -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Wait-UiaCondition -Description 'minimum-geometry Waist row scrolled into view' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (-not $reopenedWaistMinimum.Current.IsOffscreen) { $reopenedWaistMinimum }
        } | Out-Null
        $clear.SetFocus()
        Wait-UiaKeyboardFocus -Element $clear -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        if ($clear.Current.IsOffscreen) { throw 'Clear visible Slider Presets is offscreen at the minimum geometry.' }
        Resize-UiaClient -Window $script:mainWindow -LogicalWidth 1300 -LogicalHeight 800 `
            -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-FileCommand -Item 'New'
        Wait-MainWindow -Title $applicationTitle | Out-Null

        Get-UiaTree -Element $script:mainWindow |
            Set-Content -LiteralPath (Join-Path $diagnosticsDir 'uia-tree-workbench-templates.txt') -Encoding utf8
        $observations['templatesManagement'] = [ordered]@{
            browsed = @('CBBE Curvy', 'UUNP Athletic')
            createdRejected = 'SLIDER_PRESET_NAME_DUPLICATE'
            created = 'Delta Created'
            duplicated = 'Curvy Copy'
            renameCancelled = 'Temporary Name'
            renamed = 'Curvy Copy Renamed'
            removed = 'UUNP Athletic'
            clearVisiblePreserved = 'CBBE Curvy'
            savedAndReopened = $templatesManagedName
            profile = 'Standard'
            editedChoice = 'Waist@50:50'
            gangModes = @('All-Min', 'All-Max')
            rangeValidation = 'reversed maximum clamped to minimum'
        }
        'pointer-free Slider choice/profile/gang editing, browse, validation, management, and reopen passed'
    }

    Invoke-SmokeStep -Name 'author-custom-morph-targets-with-keyboard-and-pointer' -Action {
        $representativePath = Join-Path $workDir $openedProjectName
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path $representativePath -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - $openedProjectName" | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^2' -TimeoutSeconds $StepTimeoutSeconds
        Wait-AreaSelected -Name 'Morphs' | Out-Null

        $targetList = Find-OuterControl -ControlType 'List' -Name 'Custom Morph Targets'
        $allFemale = Wait-UiaElement -Root $targetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'All|Female') `
            -Description 'existing Custom Morph Target' -TimeoutSeconds $StepTimeoutSeconds
        Set-SystemHighContrast -Enabled:$true
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: High Contrast theme') `
            -Description 'populated Morphs High Contrast state' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $morphsHighContrastScreenshot = Join-Path $diagnosticsDir 'workbench-morphs-high-contrast.png'
        Save-Screenshot -Path $morphsHighContrastScreenshot
        Set-SystemHighContrast -Enabled:$false
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Effective theme: Dark theme') `
            -Description 'Morphs theme restored after High Contrast' -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        $nameLabel = Find-OuterControl -ControlType 'Text' -Name 'Custom Morph Target name:'
        $nameInput = Get-FollowingControl -Element $nameLabel -ControlType 'Edit'
        Set-UiaValue -Element $nameInput -Value '   '
        Send-UiaKeysToElement -Element $nameInput -Keys '{TAB}{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-ProjectDiagnostics -Description 'required Custom Morph Target validation' -Predicate {
            param($value) $value.Contains('CUSTOM_MORPH_TARGET_NAME_REQUIRED')
        } | Out-Null
        Invoke-UiaElement -Element (Find-OuterControl -ControlType 'Button' -Name 'Dismiss Morphs validation')
        Set-UiaValue -Element $nameInput -Value 'All|Female'
        Send-UiaKeysToElement -Element $nameInput -Keys '{TAB}{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-ProjectDiagnostics -Description 'duplicate Custom Morph Target validation' -Predicate {
            param($value) $value.Contains('CUSTOM_MORPH_TARGET_NAME_DUPLICATE')
        } | Out-Null
        Invoke-UiaElement -Element (Find-OuterControl -ControlType 'Button' -Name 'Dismiss Morphs validation')

        Set-UiaValue -Element $nameInput -Value 'ActorTypeNPC|Female'
        $createTarget = Find-OuterControl -ControlType 'Button' -Name 'Create Custom Morph Target'
        $createTarget.SetFocus()
        Wait-UiaCondition -Description 'onscreen Custom Morph Target create button' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (-not $createTarget.Current.IsOffscreen) { $createTarget }
        } | Out-Null
        $createPointer = Invoke-UiaPointerClick -Element $createTarget
        $createdTarget = Wait-UiaElement -Root $targetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'ActorTypeNPC|Female') `
            -Description 'pointer-created Custom Morph Target' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'pointer-created target selected by identity' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $createdTarget) { $createdTarget }
        } | Out-Null

        $sortLabel = Find-OuterControl -ControlType 'Text' -Name 'Sort Custom Morph Targets:'
        $sort = Get-FollowingControl -Element $sortLabel -ControlType 'ComboBox'
        Send-UiaKeysToElement -Element $sort -Keys '{HOME}{DOWN}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'descending Custom Morph Target sort committed' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $value = Get-UiaText -Element $sort
            if ($value.Contains('Name (Z–A)')) { $sort }
        } | Out-Null
        Send-UiaKeysToElement -Element $targetList -Keys 'a' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'sorted Custom Morph Target type-ahead' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $targetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name 'All|Female')
            if ($null -ne $candidate -and (Get-UiaSelectionState -Element $candidate)) { $candidate }
        } | Out-Null
        Send-UiaKeysToElement -Element $targetList -Keys 'a' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'repeated Custom Morph Target type-ahead cycle' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $targetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name 'ActorTypeNPC|Female')
            if ($null -ne $candidate -and (Get-UiaSelectionState -Element $candidate)) { $candidate }
        } | Out-Null

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        $filterLabel = Find-OuterControl -ControlType 'Text' -Name 'Filter Custom Morph Targets:'
        $filter = Get-FollowingControl -Element $filterLabel -ControlType 'Edit'
        Wait-UiaKeyboardFocus -Element $filter -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $filter -Keys 'all' -TimeoutSeconds $StepTimeoutSeconds
        $removeTarget = Find-OuterControl -ControlType 'Button' -Name 'Remove selected Custom Morph Target'
        Wait-UiaCondition -Description 'filter-hidden Custom Morph Target clears management target' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (-not $removeTarget.Current.IsEnabled) { $true }
        } | Out-Null
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Custom Morph Target inspector: no selection') `
            -Description 'cleared accessible target selection state' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' `
                -Name 'Select a Custom Morph Target to inspect its BodyGen condition.') `
            -Description 'cleared accessible BodyGen condition state' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $filter -Keys '^a{BACKSPACE}' -TimeoutSeconds $StepTimeoutSeconds
        foreach ($identityName in @('All|Female', 'ActorTypeNPC|Female')) {
            $identity = Wait-UiaElement -Root $targetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name $identityName) `
                -Description "visible Custom Morph Target '$identityName'" -TimeoutSeconds $StepTimeoutSeconds
            if (Get-UiaSelectionState -Element $identity) {
                throw "Clearing the filter silently restored Custom Morph Target '$identityName'."
            }
        }

        $allFemale = Wait-UiaElement -Root $targetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'All|Female') `
            -Description 'pointer-selected relationship target' -TimeoutSeconds $StepTimeoutSeconds
        $allFemale.SetFocus()
        $selectPointer = Invoke-UiaPointerClick -Element $allFemale
        Wait-UiaCondition -Description 'pointer target selection' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $allFemale) { $allFemale }
        } | Out-Null
        $assignedList = Find-OuterControl -ControlType 'List' -Name 'Assigned Slider Presets'
        $initialUunp = Wait-UiaElement -Root $assignedList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'UUNP Athletic') `
            -Description 'relationship prepared for pointer assign-all' -TimeoutSeconds $StepTimeoutSeconds
        Select-UiaElement -Element $initialUunp
        $prepareRemoveRelationship = Find-OuterControl -ControlType 'Button' `
            -Name 'Remove selected Slider Preset relationship'
        Send-UiaKeysToElement -Element $prepareRemoveRelationship -Keys '{ENTER}' `
            -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Remove'
        Wait-UiaCondition -Description 'prepared available relationship' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ($null -eq (Find-UiaElement -Root $assignedList -Condition (
                    New-UiaCondition -ControlType 'ListItem' -Name 'UUNP Athletic'))) { $true }
        } | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '%a' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $assignedList -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $availableRelationship = Find-OuterControl -ControlType 'ComboBox' -Name 'Available Slider Preset'
        Send-UiaKeys -ProcessId $script:app.Id -Keys '%v' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaKeyboardFocus -Element $availableRelationship -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $assignAll = Find-OuterControl -ControlType 'Button' -Name 'Assign all Slider Presets'
        $assignAll.SetFocus()
        Wait-UiaCondition -Description 'onscreen assign-all relationship button' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (-not $assignAll.Current.IsOffscreen -and $assignAll.Current.IsEnabled) { $assignAll }
        } | Out-Null
        $relationshipPointer = Invoke-UiaPointerClick -Element $assignAll
        $uunpRelationship = Wait-UiaElement -Root $assignedList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'UUNP Athletic') `
            -Description 'pointer-assigned Slider Preset relationship' -TimeoutSeconds $StepTimeoutSeconds

        $activity = Find-OuterControl -ControlType 'List' -Name 'Activity'
        $targetList.SetFocus()
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^g' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaElement -Root $activity -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Success — Generate Output — Completed: Output generated.') `
            -Description 'Morphs relationship Generate Activity' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $outputRegion = Find-OuterControl -ControlType 'Tab' -Name 'Generated Output tabs'
        $beforeRelationshipRemoval = (Get-SelectedOutputText -Region $outputRegion -TabName 'Morphs' `
            -Description 'Morphs output before relationship removal').Text
        if (-not $beforeRelationshipRemoval.Contains('All|Female=CBBE Curvy|UUNP Athletic')) {
            throw 'Generated Morphs output omitted the pointer-authored Slider Preset relationship.'
        }

        Select-UiaElement -Element $uunpRelationship
        $removeRelationship = Find-OuterControl -ControlType 'Button' `
            -Name 'Remove selected Slider Preset relationship'
        $removeRelationship.SetFocus()
        $removeRelationshipPointer = Invoke-UiaPointerClick -Element $removeRelationship
        Choose-Confirmation -ButtonName 'Remove'
        Wait-UiaCondition -Description 'removed Slider Preset relationship' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ($null -eq (Find-UiaElement -Root $assignedList -Condition (
                    New-UiaCondition -ControlType 'ListItem' -Name 'UUNP Athletic'))) { $true }
        } | Out-Null
        Wait-UiaCondition -Description 'Morphs edit invalidates generated Output' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ($outputRegion.Current.HelpText.Contains('Project changed—Generate again.')) { $outputRegion }
        } | Out-Null
        $targetList.SetFocus()
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^g' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'regenerated Morphs output after relationship removal' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = (Get-SelectedOutputText -Region $outputRegion -TabName 'Morphs' `
                -Description 'regenerated Morphs output').Text
            if ($candidate.Contains('All|Female=CBBE Curvy') `
                    -and -not $candidate.Contains('All|Female=CBBE Curvy|UUNP Athletic')) { $candidate }
        } | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^4' -TimeoutSeconds $StepTimeoutSeconds

        $createdTarget = Wait-UiaElement -Root $targetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'ActorTypeNPC|Female') `
            -Description 'target for confirmed relationship clear' -TimeoutSeconds $StepTimeoutSeconds
        Select-UiaElement -Element $createdTarget
        $clearRelationships = Find-OuterControl -ControlType 'Button' -Name 'Clear assigned Slider Presets'
        Send-UiaKeysToElement -Element $clearRelationships -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Clear'
        Wait-UiaCondition -Description 'confirmed relationship clear' -TimeoutSeconds $StepTimeoutSeconds -Test {
            $items = Find-UiaElements -Root $assignedList -Condition (New-UiaCondition -ControlType 'ListItem')
            if (@($items).Count -eq 0) { $true }
        } | Out-Null
        $removeTarget = Find-OuterControl -ControlType 'Button' -Name 'Remove selected Custom Morph Target'
        Send-UiaKeysToElement -Element $removeTarget -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Cancel'
        Wait-UiaElement -Root $targetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'ActorTypeNPC|Female') `
            -Description 'cancelled Custom Morph Target removal' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $removeTarget -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Remove'
        Wait-UiaCondition -Description 'confirmed Custom Morph Target removal' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ($null -eq (Find-UiaElement -Root $targetList -Condition (
                    New-UiaCondition -ControlType 'ListItem' -Name 'ActorTypeNPC|Female'))) { $true }
        } | Out-Null

        Set-UiaValue -Element $nameInput -Value 'Clear Me'
        $createTarget.SetFocus()
        $clearCreatePointer = Invoke-UiaPointerClick -Element $createTarget
        Wait-UiaElement -Root $targetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Clear Me') `
            -Description 'target for filtered catalog clear' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeysToElement -Element $filter -Keys 'clear me' -TimeoutSeconds $StepTimeoutSeconds
        $clearTargets = Find-OuterControl -ControlType 'Button' -Name 'Clear visible Custom Morph Targets'
        Send-UiaKeysToElement -Element $clearTargets -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Cancel'
        Wait-UiaElement -Root $targetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Clear Me') `
            -Description 'cancelled filtered target clear' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeysToElement -Element $clearTargets -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Choose-Confirmation -ButtonName 'Clear'
        Wait-UiaCondition -Description 'confirmed filtered target clear' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ($null -eq (Find-UiaElement -Root $targetList -Condition (
                    New-UiaCondition -ControlType 'ListItem' -Name 'Clear Me'))) { $true }
        } | Out-Null
        Send-UiaKeysToElement -Element $filter -Keys '^a{BACKSPACE}' -TimeoutSeconds $StepTimeoutSeconds

        $morphsManagedPath = Join-Path $workDir $morphsManagedName
        Send-FileCommand -Item 'Save As…' -DialogTitle $saveDialogTitle
        Complete-FileDialog -Title $saveDialogTitle -Path $morphsManagedPath -ConfirmButton 'Save'
        Wait-MainWindow -Title "$applicationTitle - $morphsManagedName" | Out-Null
        Send-FileCommand -Item 'New'
        Wait-MainWindow -Title $applicationTitle | Out-Null
        Send-FileCommand -Item 'Open…' -DialogTitle $openDialogTitle
        Complete-FileDialog -Title $openDialogTitle -Path $morphsManagedPath -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - $morphsManagedName" | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^2' -TimeoutSeconds $StepTimeoutSeconds
        $targetList = Find-OuterControl -ControlType 'List' -Name 'Custom Morph Targets'
        $allFemale = Wait-UiaElement -Root $targetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'All|Female') `
            -Description 'saved and reopened Custom Morph Target' -TimeoutSeconds $StepTimeoutSeconds
        Select-UiaElement -Element $allFemale
        $assignedList = Find-OuterControl -ControlType 'List' -Name 'Assigned Slider Presets'
        Wait-UiaElement -Root $assignedList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'CBBE Curvy') `
            -Description 'saved and reopened target relationship' -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        $narrowMetrics = Resize-UiaClient -Window $script:mainWindow -LogicalWidth 1199 -LogicalHeight 700 `
            -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^k' -TimeoutSeconds $StepTimeoutSeconds
        $filterLabel = Find-OuterControl -ControlType 'Text' -Name 'Filter Custom Morph Targets:'
        $filter = Get-FollowingControl -Element $filterLabel -ControlType 'Edit'
        Wait-UiaKeyboardFocus -Element $filter -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $morphsSurface = Find-OuterControl -ControlType 'Pane' -Name 'Custom Morph Target management'
        Assert-ControlInsideClient -Element $morphsSurface -Metrics $narrowMetrics
        $morphsNarrowScreenshot = Join-Path $diagnosticsDir 'workbench-morphs-narrow.png'
        Save-Screenshot -Path $morphsNarrowScreenshot
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{F7}' -TimeoutSeconds $StepTimeoutSeconds
        $availablePreset = Wait-FocusedControl -ControlType 'ComboBox' -Name 'Available Slider Preset'
        Assert-ControlInsideClient -Element $availablePreset -Metrics (Get-UiaWindowMetrics -Window $script:mainWindow)
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{ESC}' -TimeoutSeconds $StepTimeoutSeconds
        Resize-UiaClient -Window $script:mainWindow -LogicalWidth 1300 -LogicalHeight 800 `
            -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        Get-UiaTree -Element $script:mainWindow |
            Set-Content -LiteralPath (Join-Path $diagnosticsDir 'uia-tree-workbench-morphs.txt') -Encoding utf8
        $observations['morphsManagement'] = [ordered]@{
            existing = 'All|Female'
            validation = @('CUSTOM_MORPH_TARGET_NAME_REQUIRED', 'CUSTOM_MORPH_TARGET_NAME_DUPLICATE')
            pointerCreated = 'ActorTypeNPC|Female'
            pointerCoordinates = [ordered]@{
                create = $createPointer
                select = $selectPointer
                assignAll = $relationshipPointer
                removeRelationship = $removeRelationshipPointer
                filteredCreate = $clearCreatePointer
            }
            relationshipAdded = 'UUNP Athletic'
            relationshipRemoved = 'UUNP Athletic'
            relationshipCleared = 'ActorTypeNPC|Female'
            removed = 'ActorTypeNPC|Female'
            clearVisibleRemoved = 'Clear Me'
            generatedBeforeRemoval = $beforeRelationshipRemoval
            savedAndReopened = $morphsManagedName
            scalePercent = [math]::Round($narrowMetrics.Dpi * 100.0 / 96.0)
        }
        Send-FileCommand -Item 'New'
        Wait-MainWindow -Title $applicationTitle | Out-Null
        'keyboard and pointer target authoring, relationships, validation, Output, accessibility, narrow mode, and reopen passed'
    }

    Invoke-SmokeStep -Name 'manage-settings-and-import-bodyslide-through-workbench' -Action {
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^5' -TimeoutSeconds $StepTimeoutSeconds
        Wait-AreaSelected -Name 'Settings' | Out-Null
        $profileLabel = Find-OuterControl -ControlType 'Text' -Name 'Settings profile:'
        $profileChoice = Get-FollowingControl -Element $profileLabel -ControlType 'ComboBox'
        $settingsEntries = Find-OuterControl -ControlType 'List' -Name 'Settings entries'
        $waistEntry = Wait-UiaElement -Root $settingsEntries -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Waist') `
            -Description 'Standard Waist Settings entry' -TimeoutSeconds $StepTimeoutSeconds
        Select-UiaElement -Element $waistEntry
        $multiplierLabel = Find-OuterControl -ControlType 'Text' -Name 'Multiplier (blank means absent):'
        $multiplier = Get-FollowingControl -Element $multiplierLabel -ControlType 'Edit'
        Set-UiaValue -Element $multiplier -Value '2'
        Invoke-UiaElement -Element (Find-OuterControl -ControlType 'Button' -Name 'Apply Settings entry draft')

        Send-UiaKeysToElement -Element $profileChoice -Keys '{F4}{END}{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        $armsEntry = Wait-UiaElement -Root $settingsEntries -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Arms') `
            -Description 'UUNP Arms Settings entry' -TimeoutSeconds $StepTimeoutSeconds
        Select-UiaElement -Element $armsEntry
        Set-UiaValue -Element $multiplier -Value '3'
        Invoke-UiaElement -Element (Find-OuterControl -ControlType 'Button' -Name 'Apply Settings entry draft')
        $omitRedundant = Find-OuterControl -ControlType 'CheckBox' -Name 'Omit redundant sliders'
        Send-UiaKeysToElement -Element $omitRedundant -Keys ' ' -TimeoutSeconds $StepTimeoutSeconds
        Invoke-UiaElement -Element (Find-OuterControl -ControlType 'Button' -Name 'Save Standard and UUNP Settings')

        $settingsActivity = Find-OuterControl -ControlType 'List' -Name 'Activity'
        Wait-UiaElement -Root $settingsActivity -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Success — Save Settings — Completed: Settings saved.') `
            -Description 'durable packaged Settings save Activity' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $savedStandard = Get-Content -LiteralPath (Join-Path $workDir 'settings.json') -Raw | ConvertFrom-Json
        $savedUunp = Get-Content -LiteralPath (Join-Path $workDir 'settings_UUNP.json') -Raw | ConvertFrom-Json
        if ($savedStandard.Multipliers.Waist -ne 2 -or $savedUunp.Multipliers.Arms -ne 3) {
            throw 'Workbench Settings edits did not persist both output-affecting profiles.'
        }
        if ((Get-Content -LiteralPath (Join-Path $workDir 'workbench-generation.properties') -Raw) `
                -notmatch 'omitRedundantSliders=true') {
            throw 'Workbench did not migrate and persist Omit Redundant Sliders in the isolated profile.'
        }
        if (@(Get-ChildItem -LiteralPath $workDir -Filter '.bs2bg-settings-stage-*' -Force).Count -ne 0) {
            throw 'Settings save left a paired-publication transaction behind.'
        }

        $validImport = Join-Path $workDir 'settings-output.xml'
        $partialImport = Join-Path $workDir 'partial-valid.xml'
        $malformedImport = Join-Path $workDir 'malformed.xml'
        $failedMalformed = Join-Path $workDir 'a-failed-malformed.xml'
        $failedMissing = Join-Path $workDir 'z-failed-missing.xml'
        $cancelFirst = Join-Path $workDir 'a-cancel-first.xml'
        $cancelLarge = Join-Path $workDir 'z-cancel-large.xml'
        [IO.File]::WriteAllText($validImport,
            '<SliderPresets><Preset name="Settings Output"><SetSlider name="Waist" size="small" value="25"/><SetSlider name="Waist" size="big" value="75"/></Preset></SliderPresets>')
        [IO.File]::WriteAllText($partialImport,
            '<SliderPresets><Preset name="Partial Commit"/></SliderPresets>')
        [IO.File]::WriteAllText($malformedImport, '<SliderPresets><Preset name="Broken">')
        [IO.File]::WriteAllText($failedMalformed,
            '<SliderPresets><Preset name="Broken"/>' + [string]::new(' ', 20000000))
        [IO.File]::WriteAllText($failedMissing,
            '<SliderPresets><Preset name="Must Not Commit"/></SliderPresets>')
        [IO.File]::WriteAllText($cancelFirst,
            '<SliderPresets><Preset name="Committed Before Cancel"/></SliderPresets>')
        $cancelWriter = [IO.StreamWriter]::new($cancelLarge, $false, [Text.UTF8Encoding]::new($false))
        try {
            $cancelWriter.Write('<SliderPresets>')
            for ($index = 0; $index -lt 400000; $index++) {
                $cancelWriter.Write('<Preset name="Cancelled Source ')
                $cancelWriter.Write($index)
                $cancelWriter.Write('"/>')
            }
            $cancelWriter.Write('</SliderPresets>')
        }
        finally {
            $cancelWriter.Dispose()
        }

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^1' -TimeoutSeconds $StepTimeoutSeconds
        Wait-AreaSelected -Name 'Templates' | Out-Null
        $importButton = Find-OuterControl -ControlType 'Button' -Name 'Import BodySlide Presets'
        Send-UiaKeysToElement -Element $importButton -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Complete-FileDialog -Title 'Import BodySlide Presets' -Path $validImport -ConfirmButton 'Open'
        # An empty-list import intentionally replaces the ListView for JavaFX UIA refill correctness; locate the
        # committed logical identity from the stable Workbench root before capturing the replacement list node.
        $settingsOutput = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Settings Output') `
            -Description 'successfully imported Settings Output preset' -TimeoutSeconds $StepTimeoutSeconds
        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        Select-UiaElement -Element $settingsOutput
        $waistPreview = Wait-UiaElement -Root $script:mainWindow -Condition (
            New-UiaCondition -ControlType 'Text' -Name 'Waist BodyGen preview') `
            -Description 'Settings-driven imported Waist preview' -TimeoutSeconds $StepTimeoutSeconds
        if (-not $waistPreview.Current.HelpText.Contains('Waist@1.5')) {
            throw "Saved Standard multiplier did not affect the imported preview: $($waistPreview.Current.HelpText)"
        }

        $importButton = Find-OuterControl -ControlType 'Button' -Name 'Import BodySlide Presets'
        $importButton.SetFocus()
        Send-UiaKeysToElement -Element $importButton -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Complete-MultipleFileDialog -Title 'Import BodySlide Presets' `
            -Paths @($malformedImport, $partialImport) -ConfirmButton 'Open'
        $partialActivity = Wait-UiaCondition -Description 'partial BodySlide import Activity' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $items = Find-UiaElements -Root $settingsActivity -Condition (New-UiaCondition -ControlType 'ListItem')
            foreach ($item in $items) {
                if ($item.Current.Name.Contains('Import BodySlide Presets') `
                        -and $item.Current.Name.Contains('Completed with issues') `
                        -and $item.Current.HelpText.Contains('SLIDER_PRESET_XML_MALFORMED') `
                        -and $item.Current.HelpText.Contains('Imported partial-valid.xml')) { return $item }
            }
        }
        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Partial Commit') `
            -Description 'partially committed BodySlide source' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        if (-not (Get-UiaSelectionState -Element $settingsOutput)) {
            throw 'Partial import did not preserve the selected Slider Preset identity.'
        }

        $importResultsName = 'settings-import-results.jbs2bg'
        $importResultsPath = Join-Path $workDir $importResultsName
        Send-FileCommand -Item 'Save As…' -DialogTitle $saveDialogTitle
        Complete-FileDialog -Title $saveDialogTitle -Path $importResultsPath -ConfirmButton 'Save'
        Wait-MainWindow -Title "$applicationTitle - $importResultsName" | Out-Null
        $savedImport = Get-Content -LiteralPath $importResultsPath -Raw | ConvertFrom-Json
        $savedImportNames = @($savedImport.SliderPresets.PSObject.Properties.Name)
        foreach ($expected in @('Partial Commit', 'Settings Output')) {
            if ($savedImportNames -cnotcontains $expected) {
                throw "Saved partial-import Project omitted '$expected'."
            }
        }
        if ($savedImportNames -ccontains 'Broken') {
            throw 'Saved partial-import Project retained the malformed source.'
        }

        $importButton = Find-OuterControl -ControlType 'Button' -Name 'Import BodySlide Presets'
        Send-UiaKeysToElement -Element $importButton -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Complete-MultipleFileDialog -Title 'Import BodySlide Presets' `
            -Paths @($failedMalformed, $failedMissing) -ConfirmButton 'Open'
        # The worker is occupied reading the large first source, making removal of the already selected second
        # source deterministic without adding a production test hook or racing the native chooser.
        Remove-Item -LiteralPath $failedMissing -Force
        $failureDialog = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $applicationTitle `
            -TimeoutSeconds $StepTimeoutSeconds
        Send-UiaAccelerator -Window $failureDialog -Keys '{ESC}'
        $failedActivity = Wait-UiaCondition -Description 'failed BodySlide import Activity' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $items = Find-UiaElements -Root $settingsActivity -Condition (New-UiaCondition -ControlType 'ListItem')
            foreach ($item in $items) {
                if ($item.Current.Name.Contains('Import BodySlide Presets') `
                        -and $item.Current.Name.Contains('Failed') `
                        -and $item.Current.HelpText.Contains('SLIDER_PRESET_XML_MALFORMED') `
                        -and $item.Current.HelpText.Contains('SLIDER_PRESET_XML_READ_FAILED')) { return $item }
            }
        }

        if (-not (Get-UiaSelectionState -Element $settingsOutput)) {
            throw 'Failed import did not preserve the selected Slider Preset identity.'
        }
        Wait-UiaKeyboardFocus -Element $presetList -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $activityCountBeforeFailureSave = @(Find-UiaElements -Root $settingsActivity -Condition (
            New-UiaCondition -ControlType 'ListItem')).Count
        Send-FileCommand -Item 'Save'
        Wait-UiaCondition -Description 'Save after failed BodySlide import' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $items = @(Find-UiaElements -Root $settingsActivity -Condition (
                New-UiaCondition -ControlType 'ListItem'))
            if ($items.Count -gt $activityCountBeforeFailureSave) { return $items[-1] }
        } | Out-Null
        $savedAfterFailure = Get-Content -LiteralPath $importResultsPath -Raw | ConvertFrom-Json
        $savedAfterFailureNames = @($savedAfterFailure.SliderPresets.PSObject.Properties.Name)
        foreach ($preserved in @('Partial Commit', 'Settings Output')) {
            if ($savedAfterFailureNames -cnotcontains $preserved) {
                throw "Failed import Save/readback lost prior committed preset '$preserved'."
            }
        }
        foreach ($rejected in @('Broken', 'Must Not Commit')) {
            if ($savedAfterFailureNames -ccontains $rejected) {
                throw "Failed import Save/readback unexpectedly retained '$rejected'."
            }
        }

        $importButton = Find-OuterControl -ControlType 'Button' -Name 'Import BodySlide Presets'
        Send-UiaKeysToElement -Element $importButton -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Complete-MultipleFileDialog -Title 'Import BodySlide Presets' `
            -Paths @($cancelFirst, $cancelLarge) -ConfirmButton 'Open'
        $progress = Wait-UiaCondition -Description 'BodySlide second-source 50 percent progress' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $script:mainWindow -Condition (
                New-UiaCondition -ControlType 'ProgressBar' -Name 'Current operation progress')
            if ($null -ne $candidate -and $candidate.Current.HelpText.Contains('50%')) { return $candidate }
        }
        $cancel = Wait-UiaCondition -Description 'enabled BodySlide cancellation control' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $script:mainWindow -Condition (
                New-UiaCondition -ControlType 'Button' -Name 'Cancel current operation')
            if ($null -ne $candidate -and $candidate.Current.IsEnabled) { return $candidate }
        }
        Invoke-UiaElement -Element $cancel
        $cancelledActivity = Wait-UiaCondition -Description 'cancelled BodySlide import Activity' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $items = Find-UiaElements -Root $settingsActivity -Condition (New-UiaCondition -ControlType 'ListItem')
            foreach ($item in $items) {
                if ($item.Current.Name.Contains('Import BodySlide Presets') `
                        -and $item.Current.Name.Contains('Cancelled') `
                        -and $item.Current.HelpText.Contains('Imported a-cancel-first.xml')) { return $item }
            }
        }
        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Committed Before Cancel') `
            -Description 'source committed before BodySlide cancellation' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        if ($null -ne (Find-UiaElement -Root $presetList -Condition (
                New-UiaCondition -ControlType 'ListItem' -Name 'Cancelled Source 0'))) {
            throw 'Cancelled BodySlide source committed unexpectedly.'
        }
        Send-FileCommand -Item 'Save'
        Wait-MainWindow -Title "$applicationTitle - $importResultsName" | Out-Null
        $savedCancelled = Get-Content -LiteralPath $importResultsPath -Raw | ConvertFrom-Json
        $savedCancelledNames = @($savedCancelled.SliderPresets.PSObject.Properties.Name)
        if ($savedCancelledNames -cnotcontains 'Committed Before Cancel' `
                -or $savedCancelledNames -ccontains 'Cancelled Source') {
            throw 'Save/readback did not preserve the truthful BodySlide cancellation boundary.'
        }

        $observations['settingsAndBodySlideImport'] = [ordered]@{
            settings = @('Standard Waist multiplier 2', 'UUNP Arms multiplier 3', 'Omit Redundant Sliders true')
            success = 'Settings Output'
            partial = $partialActivity.Current.HelpText
            failed = $failedActivity.Current.HelpText
            cancelled = $cancelledActivity.Current.HelpText
            progress = $progress.Current.HelpText
            savedProject = $importResultsName
        }
        'paired Settings editing/recovery and successful, malformed, partial, failed, and cancelled imports passed'
    }

    Invoke-SmokeStep -Name 'generate-inspect-invalidate-cancel-and-stale-output' -Action {
        $activity = Find-OuterControl -ControlType 'List' -Name 'Activity'
        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        $presetList.SetFocus()
        Wait-UiaKeyboardFocus -Element $presetList -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^g' -TimeoutSeconds $StepTimeoutSeconds

        $completed = Wait-UiaElement -Root $activity -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Success — Generate Output — Completed: Output generated.') `
            -Description 'fresh Generate Activity' -TimeoutSeconds $StepTimeoutSeconds
        Wait-AreaSelected -Name 'Output' | Out-Null
        Wait-UiaKeyboardFocus -Element $presetList -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        $outputRegion = Find-OuterControl -ControlType 'Tab' -Name 'Generated Output tabs'
        $generatedTemplates = (Get-SelectedOutputText -Region $outputRegion -TabName 'Templates' `
            -Description 'selected Templates output text').Text
        if (-not $generatedTemplates.Contains('Settings Output=')) {
            $templatesPreview = $generatedTemplates.Replace("`r", '\r').Replace("`n", '\n')
            throw "Generated Templates output omitted the imported Settings Output preset; observed " +
                    "$($generatedTemplates.Length) characters: '$templatesPreview'."
        }

        $templatesTab = Find-UiaElement -Root $outputRegion -Condition (
            New-UiaCondition -ControlType 'TabItem' -Name 'Templates')
        $morphsTab = Find-UiaElement -Root $outputRegion -Condition (
            New-UiaCondition -ControlType 'TabItem' -Name 'Morphs')
        $bosTab = Find-UiaElement -Root $outputRegion -Condition (
            New-UiaCondition -ControlType 'TabItem' -Name 'BoS JSON')
        $outputRegion.SetFocus()
        Wait-UiaKeyboardFocus -Element $outputRegion -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{RIGHT}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'keyboard-selected Morphs Output tab' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $morphsTab) { $morphsTab }
        } | Out-Null
        $generatedMorphs = (Get-SelectedOutputText -Region $outputRegion -TabName 'Morphs' `
            -Description 'selected Morphs output text').Text
        Send-UiaKeys -ProcessId $script:app.Id -Keys '{RIGHT}' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'keyboard-selected BoS JSON Output tab' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            if (Get-UiaSelectionState -Element $bosTab) { $bosTab }
        } | Out-Null
        $generatedBos = (Get-SelectedOutputText -Region $outputRegion -TabName 'BoS JSON' `
            -Description 'selected BoS JSON output text').Text
        if ([string]::IsNullOrWhiteSpace($generatedBos)) {
            throw 'Generated BoS JSON output was empty for a Project with Slider Presets.'
        }

        Select-UiaElement -Element $templatesTab
        Set-Clipboard -Value ''
        $copyOutput = Find-OuterControl -ControlType 'Button' -Name 'Copy selected Output artifact'
        Invoke-UiaElement -Element $copyOutput
        $copiedTemplates = Wait-UiaCondition -Description 'accepted Templates clipboard text' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Get-Clipboard -Raw
            if (-not [string]::IsNullOrEmpty($candidate)) { $candidate }
        }
        if ($copiedTemplates.Replace("`r`n", "`n").Replace("`r", "`n") `
                -cne $generatedTemplates.Replace("`r`n", "`n").Replace("`r", "`n")) {
            throw 'Clipboard text differs from the selected accepted Templates artifact after newline normalization.'
        }
        Wait-UiaElement -Root (Find-OuterControl -ControlType 'List' -Name 'Activity') -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Success — Copy Output — Completed: templates.ini copied to the clipboard.') `
            -Description 'durable accepted Output copy Activity' -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        $completeExportDirectory = Join-Path $workDir 'accepted-output'
        New-Item -ItemType Directory -Path $completeExportDirectory | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^e' -TimeoutSeconds $StepTimeoutSeconds
        Complete-DirectoryDialog -Title 'Export Accepted Output' -Path $completeExportDirectory
        $completeExportActivity = Wait-UiaCondition -Description 'complete accepted Output export Activity' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $items = Find-UiaElements -Root (Find-OuterControl -ControlType 'List' -Name 'Activity') `
                -Condition (New-UiaCondition -ControlType 'ListItem')
            foreach ($item in $items) {
                if ($item.Current.Name -ceq 'Success — Export Output — Completed: Output exported.' `
                        -and $item.Current.HelpText.Contains($completeExportDirectory) `
                        -and $item.Current.HelpText.Contains('Effects committed: Published ')) { return $item }
            }
        }
        $utf8 = [Text.UTF8Encoding]::new($false)
        $normalizedTemplates = $generatedTemplates.Replace("`r`n", "`n").Replace("`r", "`n")
        $expectedTemplatesBytes = $utf8.GetBytes(
            $normalizedTemplates.Replace("`n", "`r`n"))
        $normalizedMorphs = $generatedMorphs.Replace("`r`n", "`n").Replace("`r", "`n")
        if ($normalizedMorphs.Length -gt 0 -and -not $normalizedMorphs.EndsWith("`n")) {
            # UIA omits TextArea's final paragraph delimiter; canonical morphs.ini retains its required final CRLF.
            $normalizedMorphs += "`n"
        }
        $expectedMorphsBytes = $utf8.GetBytes($normalizedMorphs.Replace("`n", "`r`n"))
        $expectedBosBytes = $utf8.GetBytes($generatedBos.Replace("`r`n", "`n").Replace("`r", "`n"))
        $exportedTemplates = [IO.File]::ReadAllBytes((Join-Path $completeExportDirectory 'templates.ini'))
        $exportedMorphs = [IO.File]::ReadAllBytes((Join-Path $completeExportDirectory 'morphs.ini'))
        $exportedBos = [IO.File]::ReadAllBytes((Join-Path $completeExportDirectory 'Settings Output.json'))
        foreach ($comparison in @(
                @($expectedTemplatesBytes, $exportedTemplates, 'templates.ini'),
                @($expectedMorphsBytes, $exportedMorphs, 'morphs.ini'),
                @($expectedBosBytes, $exportedBos, 'Settings Output.json'))) {
            if ([Convert]::ToBase64String($comparison[0]) -cne [Convert]::ToBase64String($comparison[1])) {
                throw "Complete export bytes differ from accepted $($comparison[2]) Output."
            }
        }
        if ($exportedTemplates.Length -gt 0 -and ($exportedTemplates[-1] -eq 0x0A `
                -or $exportedTemplates[-1] -eq 0x0D)) {
            throw 'Exported templates.ini unexpectedly has a final newline.'
        }
        if ($exportedMorphs.Length -gt 0 -and ($exportedMorphs.Length -lt 2 `
                -or $exportedMorphs[-2] -ne 0x0D -or $exportedMorphs[-1] -ne 0x0A)) {
            throw 'Exported morphs.ini does not retain its required final CRLF.'
        }
        if (@(Get-ChildItem -LiteralPath $completeExportDirectory -Filter '.bs2bg-output-stage-*' -Force).Count -ne 0) {
            throw 'Successful complete Output export left a transaction directory behind.'
        }

        Select-UiaElement -Element $bosTab
        $selectedExportPath = Join-Path $workDir 'selected-output.JSON'
        $selectedExport = Find-OuterControl -ControlType 'Button' -Name 'Export selected BoS JSON artifact'
        Send-UiaKeysToElement -Element $selectedExport -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
        Complete-FileDialog -Title 'Export Selected BoS JSON' -Path $selectedExportPath -ConfirmButton 'Save'
        Wait-UiaCondition -Description 'selected accepted BoS export Activity' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $items = Find-UiaElements -Root (Find-OuterControl -ControlType 'List' -Name 'Activity') `
                -Condition (New-UiaCondition -ControlType 'ListItem')
            foreach ($item in $items) {
                if ($item.Current.Name -ceq 'Success — Export Output — Completed: Output exported.' `
                        -and $item.Current.HelpText.Contains($selectedExportPath) `
                        -and $item.Current.HelpText.Contains('Effects committed: Published 1 Output artifacts')) {
                    return $item
                }
            }
        } | Out-Null
        $selectedBytes = [IO.File]::ReadAllBytes($selectedExportPath)
        if ([Convert]::ToBase64String($selectedBytes) -cne [Convert]::ToBase64String($expectedBosBytes)) {
            throw 'Selected BoS export bytes differ from the displayed accepted artifact.'
        }

        $failureDirectory = Join-Path $workDir 'atomic-output-failure'
        New-Item -ItemType Directory -Path $failureDirectory | Out-Null
        [IO.File]::WriteAllText((Join-Path $failureDirectory 'templates.ini'), 'prior templates', $utf8)
        [IO.File]::WriteAllText((Join-Path $failureDirectory 'morphs.ini'), 'prior morphs', $utf8)
        $lockedMorphs = [IO.File]::Open((Join-Path $failureDirectory 'morphs.ini'),
            [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        try {
            Send-UiaKeys -ProcessId $script:app.Id -Keys '^e' -TimeoutSeconds $StepTimeoutSeconds
            Complete-DirectoryDialog -Title 'Export Accepted Output' -Path $failureDirectory
            $failureDialog = Wait-UiaOwnedWindow -ProcessId $script:app.Id -Title $applicationTitle `
                -TimeoutSeconds $StepTimeoutSeconds
            if ([IO.File]::ReadAllText((Join-Path $failureDirectory 'templates.ini'), $utf8) -cne 'prior templates' `
                    -or [IO.File]::ReadAllText((Join-Path $failureDirectory 'morphs.ini'), $utf8) -cne 'prior morphs') {
                throw 'Failed complete Output export changed a prior destination.'
            }
            if (Test-Path -LiteralPath (Join-Path $failureDirectory 'Settings Output.json')) {
                throw 'Failed complete Output export published a later artifact.'
            }
            if (@(Get-ChildItem -LiteralPath $failureDirectory -Filter '.bs2bg-output-stage-*' -Force).Count -ne 0) {
                throw 'Failed complete Output export left a transaction directory after complete rollback.'
            }
            $lockedMorphs.Dispose()
            $lockedMorphs = $null
            $retry = Wait-UiaElement -Root $failureDialog -Condition (
                New-UiaCondition -ControlType 'Button' -Name 'Retry') -Description 'Output export Retry action' `
                -TimeoutSeconds $StepTimeoutSeconds
            Send-UiaKeysToElement -Element $retry -Keys '{ENTER}' -TimeoutSeconds $StepTimeoutSeconds
            $retryActivity = Wait-UiaCondition -Description 'linked successful Output export retry' `
                -TimeoutSeconds $StepTimeoutSeconds -Test {
                $items = Find-UiaElements -Root (Find-OuterControl -ControlType 'List' -Name 'Activity') `
                    -Condition (New-UiaCondition -ControlType 'ListItem')
                foreach ($item in $items) {
                    if ($item.Current.Name -ceq 'Success — Export Output — Completed: Output exported.' `
                            -and $item.Current.HelpText.Contains($failureDirectory) `
                            -and $item.Current.HelpText.Contains('Retry of attempt:')) { return $item }
                }
            }
            $retriedTemplates = [IO.File]::ReadAllBytes((Join-Path $failureDirectory 'templates.ini'))
            if ([Convert]::ToBase64String($retriedTemplates) -cne [Convert]::ToBase64String($expectedTemplatesBytes)) {
                throw 'Retried complete Output export did not publish the accepted Templates bytes.'
            }
        }
        finally {
            if ($null -ne $lockedMorphs) { $lockedMorphs.Dispose() }
        }

        Send-FileCommand -Item 'Save'
        Wait-UiaElement -Root $activity -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Success — Save Project — Completed: Project saved.') `
            -Description 'save-only freshness Activity' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $savedBos = (Get-SelectedOutputText -Region $outputRegion -TabName 'BoS JSON' `
            -Description 'saved BoS JSON output text').Text
        Select-UiaElement -Element $templatesTab
        $savedTemplates = (Get-SelectedOutputText -Region $outputRegion -TabName 'Templates' `
            -Description 'saved Templates output text').Text
        Select-UiaElement -Element $morphsTab
        $savedMorphs = (Get-SelectedOutputText -Region $outputRegion -TabName 'Morphs' `
            -Description 'saved Morphs output text').Text
        if ($savedTemplates -cne $generatedTemplates -or $savedMorphs -cne $generatedMorphs `
                -or $savedBos -cne $generatedBos) {
            throw 'Save-only Project publication invalidated or changed accepted Output.'
        }

        $presetNameLabel = Find-OuterControl -ControlType 'Text' -Name 'Slider Preset name:'
        $presetName = Get-FollowingControl -Element $presetNameLabel -ControlType 'Edit'
        $createPreset = Find-OuterControl -ControlType 'Button' -Name 'Create Slider Preset'
        Set-UiaValue -Element $presetName -Value 'Invalidate Output'
        Invoke-UiaElement -Element $createPreset
        Wait-UiaCondition -Description 'Project edit invalidates generated Output' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $current = Get-SelectedOutputText -Region $outputRegion -TabName 'Morphs' `
                -Description 'invalidated Morphs output text'
            if ($current.Text -ceq 'Project changed—Generate again.') {
                $current.Element
            }
        } | Out-Null
        Send-UiaKeys -ProcessId $script:app.Id -Keys '^4' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'invalidated Output drawer closes' -TimeoutSeconds $StepTimeoutSeconds -Test {
            if ((Get-UiaToggleState -Element (Get-AreaButton -Name 'Output')) -eq 'Off') { $true }
        } | Out-Null

        Send-FileCommand -Item 'Open…' -DialogTitle $applicationTitle
        Choose-Confirmation -ButtonName 'Discard'
        Complete-FileDialog -Title $openDialogTitle -Path (Join-Path $workDir $cancellableProjectName) `
            -ConfirmButton 'Open'
        Wait-MainWindow -Title "$applicationTitle - $cancellableProjectName" | Out-Null
        $presetList = Find-OuterControl -ControlType 'List' -Name 'Slider Presets'
        $presetList.SetFocus()
        Wait-UiaKeyboardFocus -Element $presetList -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^g' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'truthful Generate progress before cancellation' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $script:mainWindow -Condition (
                New-UiaCondition -ControlType 'ProgressBar' -Name 'Current operation progress')
            if ($null -ne $candidate -and $candidate.Current.HelpText.Contains('Generate Output')) { $candidate }
        } | Out-Null
        $cancel = Wait-UiaCondition -Description 'enabled Generate cancellation control' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $script:mainWindow -Condition (
                New-UiaCondition -ControlType 'Button' -Name 'Cancel current operation')
            if ($null -ne $candidate -and $candidate.Current.IsEnabled) { $candidate }
        }
        Invoke-UiaElement -Element $cancel
        $cancelled = Wait-UiaElement -Root $activity -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Information — Generate Output — Cancelled: Cancellation completed.') `
            -Description 'cancelled Generate Activity' -TimeoutSeconds $StepTimeoutSeconds
        if ((Get-UiaToggleState -Element (Get-AreaButton -Name 'Output')) -ne 'Off') {
            throw 'Cancelled Generate revealed Output.'
        }

        Send-UiaKeys -ProcessId $script:app.Id -Keys '^g' -TimeoutSeconds $StepTimeoutSeconds
        Wait-UiaCondition -Description 'active Generate before Project mutation' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $candidate = Find-UiaElement -Root $script:mainWindow -Condition (
                New-UiaCondition -ControlType 'ProgressBar' -Name 'Current operation progress')
            if ($null -ne $candidate -and $candidate.Current.HelpText.Contains('Generate Output')) { $candidate }
        } | Out-Null
        $presetNameLabel = Find-OuterControl -ControlType 'Text' -Name 'Slider Preset name:'
        $presetName = Get-FollowingControl -Element $presetNameLabel -ControlType 'Edit'
        $createPreset = Find-OuterControl -ControlType 'Button' -Name 'Create Slider Preset'
        if (-not $createPreset.Current.IsEnabled) {
            throw 'Snapshot-derived Generate blocked an ordinary Project edit.'
        }
        Set-UiaValue -Element $presetName -Value 'Stale Mutation'
        $presetName.SetFocus()
        Invoke-UiaElement -Element $createPreset
        Wait-UiaElement -Root $presetList -Condition (
            New-UiaCondition -ControlType 'ListItem' -Name 'Stale Mutation') `
            -Description 'Project mutation during Generate' -TimeoutSeconds $StepTimeoutSeconds | Out-Null
        $focusAfterMutation = Wait-UiaCondition -Description 'semantic focus after Project mutation' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            $focused = [System.Windows.Automation.AutomationElement]::FocusedElement
            if ($null -ne $focused -and $focused.Current.ProcessId -eq $script:app.Id) { $focused }
        }
        $stale = Wait-UiaElement -Root $activity -Condition (
            New-UiaCondition -ControlType 'ListItem' `
                -Name 'Warning — Generate Output — Completed with issues: Project changed—Generate again.') `
            -Description 'stale Generate Activity' -TimeoutSeconds $StepTimeoutSeconds
        foreach ($evidence in @('Effects committed: none', 'Diagnostics: STALE_RESULT',
                'Captured basis: Project content version')) {
            if (-not $stale.Current.HelpText.Contains($evidence)) {
                throw "Stale Generate Activity omitted '$evidence': '$($stale.Current.HelpText)'"
            }
        }
        if ((Get-UiaToggleState -Element (Get-AreaButton -Name 'Output')) -ne 'Off') {
            throw 'Stale Generate revealed Output.'
        }
        Wait-UiaKeyboardFocus -Element $focusAfterMutation -TimeoutSeconds $StepTimeoutSeconds | Out-Null

        $observations['generatedOutput'] = [ordered]@{
            completed = $completed.Current.HelpText
            templatesCharacters = $generatedTemplates.Length
            morphsCharacters = $generatedMorphs.Length
            bosCharacters = $generatedBos.Length
            tabs = @('Templates', 'Morphs', 'BoS JSON')
            readOnly = $true
            clipboardExact = $true
            completeExport = $completeExportActivity.Current.HelpText
            selectedExport = $selectedExportPath
            atomicFailureAndRetry = $retryActivity.Current.HelpText
            savePreserved = $true
            contentInvalidated = $true
            cancellation = $cancelled.Current.HelpText
            stale = $stale.Current.HelpText
            focusPreserved = 'Slider Preset list / name field'
        }

        Send-FileCommand -Item 'New' -DialogTitle $applicationTitle
        Choose-Confirmation -ButtonName 'Discard'
        Wait-MainWindow -Title $applicationTitle | Out-Null
        'fresh, save-only, invalidated, cancelled, and stale Output contracts passed through packaged UIA'
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
        $retryActivity = Wait-UiaCondition -Description 'durable linked Retry Activity record' `
            -TimeoutSeconds $StepTimeoutSeconds -Test {
            foreach ($candidate in @(Find-UiaElements -Root $script:mainWindow -Condition $retryCondition)) {
                if ($candidate.Current.HelpText.Contains('Retry of attempt:')) { return $candidate }
            }
        }
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
        schema = 'bs2bg.windows-app-image-smoke/16'
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
            templatesWorkbenchTree = 'smoke-diagnostics/uia-tree-workbench-templates.txt'
            morphsWorkbenchTree = 'smoke-diagnostics/uia-tree-workbench-morphs.txt'
            templatesNarrowScreenshot = 'smoke-diagnostics/workbench-templates-narrow.png'
            templatesHighContrastScreenshot = 'smoke-diagnostics/workbench-templates-high-contrast.png'
            morphsNarrowScreenshot = 'smoke-diagnostics/workbench-morphs-narrow.png'
            morphsHighContrastScreenshot = 'smoke-diagnostics/workbench-morphs-high-contrast.png'
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
