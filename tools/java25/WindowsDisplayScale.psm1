#Requires -Version 7.0
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'UiaAutomation.psm1')

<# .SYNOPSIS Reads the native primary monitor through the Win32 adapter. #>
function Read-PrimaryDisplayNativeState {
    Import-Module (Join-Path $PSScriptRoot 'NativeDisplayScale.psm1')
    Get-NativePrimaryDisplay
}

<# .SYNOPSIS Opens a short-lived Settings lease targeted at the primary display. #>
function Open-PrimaryDisplaySettings {
    param($Native)
    $existing = @(Find-DisplaySettingsWindows | ForEach-Object { $_.Current.NativeWindowHandle })
    Start-Process -FilePath (Join-Path $env:WINDIR 'explorer.exe') -ArgumentList 'ms-settings:display' -WindowStyle Hidden
    $window = Wait-UiaCondition -Description 'Windows Display Settings owned by SystemSettings' -TimeoutSeconds 30 -Test {
        $candidates = @(Find-DisplaySettingsWindows)
        if ($candidates.Count -gt 1) { throw 'Multiple Windows Settings windows are ambiguous.' }
        if ($candidates.Count -eq 1 -and $null -ne (Find-UiaElement -Root $candidates[0] -Condition (
                New-UiaCondition -ControlType ComboBox -Name 'Scale'))) { $candidates[0] }
    }
    $lease = [pscustomobject]@{
        window = $window
        owned = $window.Current.NativeWindowHandle -notin $existing
        settingsDisplayName = 'Primary display'
        scalePercent = 0
        supportedScales = @()
        monitorCount = $Native.monitorCount
    }
    try {
        if ($Native.monitorCount -gt 1) {
            $layout = Wait-UiaElement -Root $window -Condition (New-UiaCondition -ControlType Custom -Name 'Monitor layout') `
                -Description 'Settings monitor selection' -TimeoutSeconds 30
            $names = @(Find-UiaElements -Root $layout -Condition (New-UiaCondition -ControlType ListItem) |
                ForEach-Object { $_.Current.Name } | Where-Object { $_ -match '^Display [0-9]+$' })
            if ($names.Count -ne $Native.monitorCount) { throw 'Settings monitor topology does not match native enumeration.' }
            $found = $false
            foreach ($name in $names) {
                Select-UiaElement -Element (Find-UiaElement -Root $layout -Condition (
                    New-UiaCondition -ControlType ListItem -Name $name))
                $snapshot = Wait-StableSettingsDisplay -Lease $lease -DisplayName $name
                if ($snapshot.primary) {
                    $lease.settingsDisplayName = $name
                    $found = $true
                    break
                }
            }
            if (-not $found) { throw 'Windows Settings did not identify a unique primary display.' }
        }
        $lease.scalePercent = Read-SettingsScale -Lease $lease
        $combo = Get-SettingsScaleControl -Lease $lease
        if (-not $combo.Current.IsEnabled) { throw 'Custom scaling or a disabled scale selector is not supported.' }
        $scroll = $null
        if ($combo.TryGetCurrentPattern([System.Windows.Automation.ScrollItemPattern]::Pattern, [ref]$scroll)) {
            $scroll.ScrollIntoView()
        }
        Expand-UiaElement -Element $combo
        try {
            $choices = Wait-UiaCondition -Description 'standard Settings scale choices' -TimeoutSeconds 15 -Test {
                $items = @(Find-UiaElements -Root $combo -Condition (New-UiaCondition -ControlType ListItem))
                $values = @($items | ForEach-Object { ConvertFrom-ScaleLabel -Label $_.Current.Name } | Sort-Object -Unique)
                if ($values.Count -gt 0 -and $lease.scalePercent -in $values) { [pscustomobject]@{ values = $values } }
            }
            $lease.supportedScales = [int[]]$choices.values
        }
        finally { ($combo.GetCurrentPattern([System.Windows.Automation.ExpandCollapsePattern]::Pattern)).Collapse() }
        return $lease
    }
    catch {
        Close-DisplayScaleSettings -Lease $lease
        throw
    }
}

<# .SYNOPSIS Closes only the Settings window opened by this lease. #>
function Close-DisplayScaleSettings {
    param($Lease)
    if ($Lease.owned) { Close-UiaWindow -Window $Lease.window }
}

<# .SYNOPSIS Reads the currently selected Settings percentage through its Selection pattern. #>
function Read-SettingsScale {
    param($Lease)
    $combo = Get-SettingsScaleControl -Lease $Lease
    $selection = @(($combo.GetCurrentPattern([System.Windows.Automation.SelectionPattern]::Pattern)).Current.GetSelection())
    if ($selection.Count -ne 1) { throw 'Settings did not expose one selected scale.' }
    ConvertFrom-ScaleLabel -Label $selection[0].Current.Name
}

<# .SYNOPSIS Selects one observed standard option on the primary monitor's Settings page. #>
function Select-SettingsScale {
    param($Lease, [int]$ScalePercent)
    if ($Lease.monitorCount -gt 1) {
        $selected = Wait-StableSettingsDisplay -Lease $Lease -DisplayName $Lease.settingsDisplayName
        if (-not $selected.primary) { throw 'The selected Settings display is no longer primary.' }
    }
    $combo = Get-SettingsScaleControl -Lease $Lease
    $scroll = $null
    if ($combo.TryGetCurrentPattern([System.Windows.Automation.ScrollItemPattern]::Pattern, [ref]$scroll)) {
        $scroll.ScrollIntoView()
    }
    Expand-UiaElement -Element $combo
    try {
        $option = Wait-UiaCondition -Description "observed Settings option $ScalePercent percent" -TimeoutSeconds 15 -Test {
            $matchesScale = @(Find-UiaElements -Root $combo -Condition (New-UiaCondition -ControlType ListItem) |
                Where-Object { (ConvertFrom-ScaleLabel -Label $_.Current.Name) -eq $ScalePercent })
            if ($matchesScale.Count -eq 1) { $matchesScale[0] }
        }
        Select-UiaElement -Element $option
    }
    finally {
        # Scaling may replace XAML peers; reacquire the selector before closing its popup.
        $current = Get-SettingsScaleControl -Lease $Lease
        ($current.GetCurrentPattern([System.Windows.Automation.ExpandCollapsePattern]::Pattern)).Collapse()
    }
}

<# .SYNOPSIS Finds Settings frames by the real SystemSettings process, including ApplicationFrameHost wrappers. #>
function Find-DisplaySettingsWindows {
    $processIds = @(Get-Process -Name SystemSettings -ErrorAction SilentlyContinue | ForEach-Object { $_.Id })
    foreach ($window in @(Find-UiaElements -Root ([System.Windows.Automation.AutomationElement]::RootElement) `
            -Condition (New-UiaCondition -ControlType Window -Name 'Settings') -Scope Children)) {
        foreach ($settingsProcessId in $processIds) {
            if ($window.Current.ProcessId -eq $settingsProcessId -or $null -ne (Find-UiaElement -Root $window `
                    -Condition (New-UiaCondition -ProcessId $settingsProcessId))) {
                $window
                break
            }
        }
    }
}

<# .SYNOPSIS Reacquires the semantic scale selector without keeping peers across display transitions. #>
function Get-SettingsScaleControl {
    param($Lease)
    Wait-UiaElement -Root $Lease.window -Condition (New-UiaCondition -ControlType ComboBox -Name 'Scale') `
        -Description 'Windows Settings Scale selector' -TimeoutSeconds 15
}

<# .SYNOPSIS Parses an observed standard percentage label, allowing Windows' Recommended suffix. #>
function ConvertFrom-ScaleLabel {
    param([string]$Label)
    $parsed = [regex]::Match($Label, '^([0-9]+)%(?:\s|$)')
    if (-not $parsed.Success) { throw "Settings did not expose a standard scale label: '$Label'." }
    [int]$parsed.Groups[1].Value
}

<#
.SYNOPSIS
    Waits for a selected monitor and its asynchronously bound primary flag/scale to settle together.
.NOTES
    Selection and checkbox peers update separately; consecutive equal snapshots prevent a stale primary flag
    from directing a subsequent scale change at the wrong monitor. This only reads the main-display checkbox.
#>
function Wait-StableSettingsDisplay {
    param($Lease, [string]$DisplayName)
    $stability = [pscustomobject]@{ fingerprint = ''; count = 0 }
    Wait-UiaCondition -Description "settled Settings selection '$DisplayName'" -TimeoutSeconds 15 -Test {
        $layout = Find-UiaElement -Root $Lease.window -Condition (New-UiaCondition -ControlType Custom -Name 'Monitor layout')
        $selected = @(($layout.GetCurrentPattern([System.Windows.Automation.SelectionPattern]::Pattern)).Current.GetSelection())
        if ($selected.Count -ne 1 -or $selected[0].Current.Name -cne $DisplayName) { $stability.count = 0; return }
        $primary = Find-UiaElement -Root $Lease.window -Condition (
            New-UiaCondition -ControlType CheckBox -Name 'Make this my main display')
        if ($null -eq $primary) {
            # Selecting another monitor collapses this virtualized section again; expand the current page.
            $multiple = Find-UiaElement -Root $Lease.window -Condition (New-UiaCondition -ControlType Group -Name 'Multiple displays')
            $expand = Find-UiaElement -Root $multiple -Condition (New-UiaCondition -ControlType Button -Name 'Show more settings')
            Expand-UiaElement -Element $expand
            $stability.count = 0
            return
        }
        $isPrimary = (Get-UiaToggleState -Element $primary) -eq 'On'
        $scale = Read-SettingsScale -Lease $Lease
        $fingerprint = "$DisplayName|$isPrimary|$scale"
        if ($stability.fingerprint -ceq $fingerprint) { $stability.count++ }
        else { $stability.fingerprint = $fingerprint; $stability.count = 1 }
        if ($stability.count -ge 3) { [pscustomobject]@{ primary = $isPrimary; scalePercent = $scale } }
    }
}

<#
.SYNOPSIS
    Captures the primary monitor's native identity and standard scale choices from Windows Settings.
#>
function Get-PrimaryDisplayScale {
    $native = Read-PrimaryDisplayNativeState
    $lease = Open-PrimaryDisplaySettings -Native $native
    try {
        if ($lease.scalePercent -ne $native.scalePercent) {
            throw 'Windows Settings and the native primary monitor DPI disagree.'
        }
        return New-DisplayScaleState -Native $native -Lease $lease
    }
    finally { Close-DisplayScaleSettings -Lease $lease }
}

<# .SYNOPSIS Returns serializable monitor identity and scale choices without retaining UI Automation peers. #>
function New-DisplayScaleState {
    param($Native, $Lease)
    [pscustomobject]@{
        deviceName = $Native.deviceName
        monitorDevicePath = $Native.monitorDevicePath
        scalePercent = $Native.scalePercent
        dpi = $Native.dpi
        bounds = $Native.bounds
        supportedScales = [int[]]@($Lease.supportedScales)
        settingsDisplayName = $Lease.settingsDisplayName
    }
}

<#
.SYNOPSIS
    Applies a captured standard scale to the same primary monitor and verifies the native result.
.PARAMETER Display
    Original identity and supported choices returned by Get-PrimaryDisplayScale.
.PARAMETER ScalePercent
    Requested standard percentage, including the captured original percentage during restoration.
#>
function Set-PrimaryDisplayScale {
    param([Parameter(Mandatory)] $Display, [Parameter(Mandatory)] [int]$ScalePercent)
    if ($ScalePercent -notin $Display.supportedScales) {
        throw "The requested $ScalePercent percent is not a captured standard scale."
    }
    $native = Read-PrimaryDisplayNativeState
    if ($native.deviceName -ine $Display.deviceName -or $native.monitorDevicePath -ine $Display.monitorDevicePath) {
        throw 'The primary monitor identity changed; refusing to change another display.'
    }
    $lease = Open-PrimaryDisplaySettings -Native $native
    try {
        if ($ScalePercent -notin $lease.supportedScales -or $lease.scalePercent -ne $native.scalePercent) {
            throw 'The current Settings scale choices or native DPI changed during admission.'
        }
        if ($native.scalePercent -ne $ScalePercent) {
            Select-SettingsScale -Lease $lease -ScalePercent $ScalePercent
        }
        return Wait-UiaCondition -Description "primary monitor at $ScalePercent percent with matching native DPI" `
            -TimeoutSeconds 30 -Test {
            $current = Read-PrimaryDisplayNativeState
            if ($current.monitorDevicePath -ine $Display.monitorDevicePath -or $current.deviceName -ine $Display.deviceName) {
                throw 'The primary monitor identity changed during the scale transition.'
            }
            if ($current.scalePercent -eq $ScalePercent -and (Read-SettingsScale -Lease $lease) -eq $ScalePercent) {
                New-DisplayScaleState -Native $current -Lease $lease
            }
        }
    }
    finally { Close-DisplayScaleSettings -Lease $lease }
}

Export-ModuleMember -Function @('Get-PrimaryDisplayScale', 'Set-PrimaryDisplayScale')
