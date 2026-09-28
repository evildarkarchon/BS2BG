#Requires -Version 7.0
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'WindowsDisplayScale.psm1')
Import-Module (Join-Path $PSScriptRoot 'WindowsAppImage.psm1')
Import-Module (Join-Path $PSScriptRoot 'UiaAutomation.psm1')
$script:dpiMatrixModule = Import-Module (Join-Path $PSScriptRoot 'DpiMatrix.psm1') -PassThru

<#
.SYNOPSIS
    Loads read-only physical monitor enumeration and a no-activation window move adapter.
.NOTES
    Native calls run under temporary per-monitor awareness so coordinates and effective DPI are not virtualized by
    the PowerShell host. The previous thread context is restored after every call.
#>
function Initialize-MixedMonitorNative {
    if ('BS2BG.MixedMonitorProbeV1' -as [type]) { return }
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;

namespace BS2BG {
    /// <summary>Reads physical display topology and moves one owned app window without activation.</summary>
    public static class MixedMonitorProbeV1 {
        [StructLayout(LayoutKind.Sequential)]
        public struct Rect { public int Left, Top, Right, Bottom; }
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct MonitorInfo {
            public int Size;
            public Rect Monitor, Work;
            public uint Flags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string Device;
        }
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct DisplayDevice {
            public int Size;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string Name;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Description;
            public uint StateFlags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Id;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Key;
        }
        public sealed class Capture {
            public string DeviceName;
            public string MonitorDevicePath;
            public bool Primary;
            public uint Dpi;
            public Rect Bounds;
        }
        private delegate bool MonitorCallback(IntPtr monitor, IntPtr dc, ref Rect bounds, IntPtr data);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool EnumDisplayMonitors(IntPtr dc, IntPtr clip, MonitorCallback callback, IntPtr data);
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool GetMonitorInfo(IntPtr monitor, ref MonitorInfo info);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool EnumDisplayDevices(string device, uint number, ref DisplayDevice info, uint flags);
        [DllImport("user32.dll", SetLastError = true)]
        private static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
        [DllImport("shcore.dll")]
        private static extern int GetDpiForMonitor(IntPtr monitor, int type, out uint dpiX, out uint dpiY);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool SetWindowPos(IntPtr window, IntPtr after, int x, int y, int width, int height, uint flags);

        /// <summary>Switches this thread to physical per-monitor coordinates for window measurement and resize.</summary>
        /// <returns>The prior DPI awareness context, which the caller must restore.</returns>
        public static IntPtr EnterPerMonitorAwareness() {
            IntPtr previous = SetThreadDpiAwarenessContext(new IntPtr(-4));
            if (previous == IntPtr.Zero)
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot enter per-monitor DPI awareness.");
            return previous;
        }

        /// <summary>Restores the caller's prior DPI awareness after mixed-monitor UI Automation.</summary>
        public static void RestoreAwareness(IntPtr previous) {
            if (previous == IntPtr.Zero || SetThreadDpiAwarenessContext(previous) == IntPtr.Zero)
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot restore the thread DPI context.");
        }

        /// <summary>Returns active monitors with stable interface paths, physical rectangles, and effective DPI.</summary>
        /// <exception cref="Win32Exception">Native display enumeration or DPI context change failed.</exception>
        public static Capture[] Read() {
            IntPtr previous = SetThreadDpiAwarenessContext(new IntPtr(-3));
            if (previous == IntPtr.Zero)
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot enter per-monitor DPI awareness.");
            try {
                var captures = new List<Capture>();
                int callbackError = 0;
                MonitorCallback callback = delegate(IntPtr monitor, IntPtr dc, ref Rect bounds, IntPtr data) {
                    var info = new MonitorInfo { Size = Marshal.SizeOf(typeof(MonitorInfo)) };
                    if (!GetMonitorInfo(monitor, ref info)) {
                        callbackError = Marshal.GetLastWin32Error();
                        return false;
                    }
                    var device = new DisplayDevice { Size = Marshal.SizeOf(typeof(DisplayDevice)) };
                    if (!EnumDisplayDevices(info.Device, 0, ref device, 1) || String.IsNullOrWhiteSpace(device.Id)) {
                        callbackError = 13;
                        return false;
                    }
                    uint dpiX, dpiY;
                    int status = GetDpiForMonitor(monitor, 0, out dpiX, out dpiY);
                    if (status != 0 || dpiX == 0 || dpiX != dpiY) {
                        callbackError = 13;
                        return false;
                    }
                    captures.Add(new Capture { DeviceName = info.Device, MonitorDevicePath = device.Id,
                        Primary = (info.Flags & 1) != 0, Dpi = dpiX, Bounds = info.Monitor });
                    return true;
                };
                bool enumerated = EnumDisplayMonitors(IntPtr.Zero, IntPtr.Zero, callback, IntPtr.Zero);
                if (!enumerated || callbackError != 0)
                    throw new Win32Exception(callbackError != 0 ? callbackError : Marshal.GetLastWin32Error(),
                        "Cannot read active monitor identity and effective DPI.");
                return captures.ToArray();
            } finally {
                if (SetThreadDpiAwarenessContext(previous) == IntPtr.Zero)
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot restore the thread DPI context.");
            }
        }

        /// <summary>Moves an app window to physical desktop coordinates without resizing or activating it.</summary>
        /// <exception cref="Win32Exception">Windows rejected the move or DPI context restoration.</exception>
        public static void Move(IntPtr window, int x, int y) {
            IntPtr previous = SetThreadDpiAwarenessContext(new IntPtr(-4));
            if (previous == IntPtr.Zero)
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot enter per-monitor DPI awareness.");
            try {
                const uint SWP_NOSIZE = 0x0001, SWP_NOZORDER = 0x0004, SWP_NOACTIVATE = 0x0010;
                if (!SetWindowPos(window, IntPtr.Zero, x, y, 0, 0, SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE))
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot move the packaged window.");
            } finally {
                if (SetThreadDpiAwarenessContext(previous) == IntPtr.Zero)
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot restore the thread DPI context.");
            }
        }
    }
}
'@
}

<#
.SYNOPSIS
    Reads every active display's stable identity, physical bounds, and effective scale without changing the desktop.
#>
function Read-MixedMonitorTopology {
    Initialize-MixedMonitorNative
    foreach ($capture in [BS2BG.MixedMonitorProbeV1]::Read()) {
        $scale = [double]$capture.Dpi * 100 / 96
        if ($scale -le 0 -or $scale -ne [Math]::Truncate($scale)) {
            throw "Monitor $($capture.DeviceName) reported unsupported DPI $($capture.Dpi)."
        }
        [pscustomobject]@{
            deviceName = $capture.DeviceName
            monitorDevicePath = $capture.MonitorDevicePath
            primary = $capture.Primary
            dpi = [int]$capture.Dpi
            scalePercent = [int]$scale
            bounds = [pscustomobject]@{
                left = $capture.Bounds.Left; top = $capture.Bounds.Top
                width = $capture.Bounds.Right - $capture.Bounds.Left
                height = $capture.Bounds.Bottom - $capture.Bounds.Top
            }
        }
    }
}

<#
.SYNOPSIS
    Positions the packaged window inside the selected monitor using physical coordinates.
#>
function Move-MixedMonitorWindow {
    param($Window, $Monitor)
    Initialize-MixedMonitorNative
    $handle = [IntPtr]$Window.Current.NativeWindowHandle
    if ($handle -eq [IntPtr]::Zero) { throw 'The packaged Workbench window has no native handle.' }
    [BS2BG.MixedMonitorProbeV1]::Move($handle, $Monitor.bounds.left + 80, $Monitor.bounds.top + 50)
}

<#
.SYNOPSIS
    Atomically replaces a mixed-monitor report or the shared display-scale recovery record.
#>
function Write-MixedMonitorJson {
    param([string]$Path, [object]$Value)
    # DpiMatrix owns the atomic writer and recovery format used by both display-scale audits.
    & $script:dpiMatrixModule { param($JsonPath, $JsonValue)
        Write-DpiMatrixJson -Path $JsonPath -Value $JsonValue
    } $Path $Value
}

<#
.SYNOPSIS
    Requires one primary and one distinct 100% secondary monitor matching the captured primary identity.
.OUTPUTS
    A pair with primary and secondary monitor records from the native topology probe.
#>
function Assert-MixedMonitorPair {
    param([object[]]$Topology, $Display, [int]$ExpectedPrimaryScale)
    if ($Topology.Count -ne 2) { throw "Mixed-monitor audit requires exactly two active monitors; found $($Topology.Count)." }
    $primary = @($Topology | Where-Object { $_.primary })
    $secondary = @($Topology | Where-Object { -not $_.primary })
    if ($primary.Count -ne 1 -or $secondary.Count -ne 1) { throw 'Mixed-monitor audit requires one primary and one secondary.' }
    if ($primary[0].deviceName -ine $Display.deviceName -or
        $primary[0].monitorDevicePath -ine $Display.monitorDevicePath) {
        throw 'Native primary monitor identity differs from the guarded Windows Settings display.'
    }
    if ($primary[0].scalePercent -ne $ExpectedPrimaryScale -or
        $primary[0].dpi -ne ($ExpectedPrimaryScale * 96 / 100)) {
        throw "Primary monitor does not report the expected $ExpectedPrimaryScale% native DPI."
    }
    if ($secondary[0].scalePercent -ne 100 -or $secondary[0].dpi -ne 96) {
        throw 'The secondary monitor must be at 100% (96 DPI) throughout the audit.'
    }
    if ($secondary[0].monitorDevicePath -ieq $primary[0].monitorDevicePath) {
        throw 'The two monitor device identities are not distinct.'
    }
    return [pscustomobject]@{ primary = $primary[0]; secondary = $secondary[0] }
}

<#
.SYNOPSIS
    Fails when a display identity or physical placement changes during the audit.
#>
function Assert-MixedMonitorTopologyStable {
    param($Original, $Current)
    foreach ($role in @('primary', 'secondary')) {
        $before = $Original.$role
        $after = $Current.$role
        if ($before.deviceName -ine $after.deviceName -or
            $before.monitorDevicePath -ine $after.monitorDevicePath -or
            $before.bounds.left -ne $after.bounds.left -or $before.bounds.top -ne $after.bounds.top -or
            $before.bounds.width -ne $after.bounds.width -or $before.bounds.height -ne $after.bounds.height) {
            throw "The $role monitor identity or physical placement changed during the audit."
        }
    }
}

<#
.SYNOPSIS
    Deletes only the new work root admitted by this audit, after validating its absolute target path.
.NOTES
    A changed or reparse-point root is refused so recursive cleanup cannot follow an unexpected target.
#>
function Remove-MixedMonitorWorkRoot {
    param([string]$WorkRoot)
    if (-not (Test-Path -LiteralPath $WorkRoot)) { return }
    Assert-MixedMonitorPathHasNoAlias -Path $WorkRoot -Parameter 'WorkRoot'
    $expected = [IO.Path]::GetFullPath($WorkRoot).TrimEnd('\', '/')
    $item = Get-Item -LiteralPath $WorkRoot -Force
    $actual = [IO.Path]::GetFullPath($item.FullName).TrimEnd('\', '/')
    if ($actual -ine $expected -or
        $item.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint) -or
        $expected -ieq [IO.Path]::GetPathRoot($expected).TrimEnd('\', '/')) {
        throw "Refusing recursive cleanup outside the created work root: $WorkRoot"
    }
    Remove-Item -LiteralPath $actual -Recurse -Force
}

<#
.SYNOPSIS
    Rejects paths whose existing ancestors could hide a different physical location during work-root cleanup.
.NOTES
    WorkRoot may not exist yet, so every existing ancestor is checked for a junction or other reparse point.
    DOS 8.3 components are refused because lexical comparisons cannot establish their long-path identity.
#>
function Assert-MixedMonitorPathHasNoAlias {
    param([string]$Path, [string]$Parameter)
    $ancestor = [IO.Path]::GetFullPath($Path)
    while ($ancestor) {
        $trimmed = [IO.Path]::TrimEndingDirectorySeparator($ancestor)
        if ([IO.Path]::GetFileName($trimmed) -match '~[0-9]+(\.|$)') {
            throw "$Parameter contains a DOS path alias that cannot be checked safely against WorkRoot: $Path"
        }
        if (Test-Path -LiteralPath $ancestor) {
            $item = Get-Item -LiteralPath $ancestor -Force
            if ($item.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) {
                throw "$Parameter contains a reparse-point ancestor that cannot be checked safely against WorkRoot: $Path"
            }
        }
        $parent = [IO.Path]::GetDirectoryName($trimmed)
        if (-not $parent -or $parent -eq $ancestor) { break }
        $ancestor = $parent
    }
}

<#
.SYNOPSIS
    Serializes measured physical and logical window geometry for one monitor landing.
#>
function ConvertTo-MixedMonitorMetricsEvidence {
    param($Metrics)
    [ordered]@{
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
    Requires the measured packaged window and one UIA control to remain within the selected display and client.
#>
function Assert-MixedMonitorBounds {
    param($Metrics, $Monitor, $Control)
    $right = $Monitor.bounds.left + $Monitor.bounds.width
    $bottom = $Monitor.bounds.top + $Monitor.bounds.height
    if ($Metrics.WindowLeft -lt $Monitor.bounds.left -or $Metrics.WindowTop -lt $Monitor.bounds.top -or
        ($Metrics.WindowLeft + $Metrics.WindowWidth) -gt $right -or
        ($Metrics.WindowTop + $Metrics.WindowHeight) -gt $bottom) {
        throw "Packaged window escapes monitor $($Monitor.deviceName) physical bounds."
    }
    if ($Control.Current.IsOffscreen) { throw "UIA control '$($Control.Current.Name)' is offscreen." }
    $bounds = $Control.Current.BoundingRectangle
    if ($bounds.Left -lt ($Metrics.ClientLeft - 1) -or $bounds.Top -lt ($Metrics.ClientTop - 1) -or
        $bounds.Right -gt ($Metrics.ClientLeft + $Metrics.ClientWidth + 1) -or
        $bounds.Bottom -gt ($Metrics.ClientTop + $Metrics.ClientHeight + 1)) {
        throw "UIA control '$($Control.Current.Name)' escapes the measured client rectangle."
    }
}

<#
.SYNOPSIS
    Captures only the uncovered packaged window in physical pixels.
.NOTES
    The point-owner check prevents a Settings transition cover or another window from passing as app evidence.
#>
function Save-MixedMonitorScreenshot {
    param($Window, [string]$Path, [int]$TimeoutSeconds)
    Add-Type -AssemblyName System.Drawing
    $metrics = Get-UiaWindowMetrics -Window $Window
    $center = [System.Windows.Point]::new($metrics.WindowLeft + $metrics.WindowWidth / 2.0,
        $metrics.WindowTop + $metrics.WindowHeight / 2.0)
    Wait-UiaPointOwner -Handle ([IntPtr]$Window.Current.NativeWindowHandle) -Point $center `
        -TimeoutSeconds $TimeoutSeconds | Out-Null
    $metrics = Get-UiaWindowMetrics -Window $Window
    $bounds = [Drawing.Rectangle]::new($metrics.WindowLeft, $metrics.WindowTop,
        $metrics.WindowWidth, $metrics.WindowHeight)
    $bitmap = [Drawing.Bitmap]::new($bounds.Width, $bounds.Height)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CopyFromScreen($bounds.Left, $bounds.Top, 0, 0, $bitmap.Size)
        $bitmap.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

<#
.SYNOPSIS
    Moves one running Workbench window onto a monitor and proves DPI, geometry, responsive layout, and UIA focus.
#>
function Inspect-MixedMonitorLanding {
    param($Window, $Monitor, [string]$Role, [int]$Index, [string]$EvidenceDirectory,
        [int]$TimeoutSeconds)
    Move-MixedMonitorWindow -Window $Window -Monitor $Monitor
    Wait-UiaCondition -Description "$Role monitor DPI $($Monitor.dpi) after move" `
        -TimeoutSeconds $TimeoutSeconds -Test {
        $metrics = Get-UiaWindowMetrics -Window $Window
        $centerX = $metrics.WindowLeft + $metrics.WindowWidth / 2.0
        $centerY = $metrics.WindowTop + $metrics.WindowHeight / 2.0
        if ($metrics.Dpi -eq $Monitor.dpi -and
            $centerX -ge $Monitor.bounds.left -and $centerX -lt ($Monitor.bounds.left + $Monitor.bounds.width) -and
            $centerY -ge $Monitor.bounds.top -and $centerY -lt ($Monitor.bounds.top + $Monitor.bounds.height)) {
            $metrics
        }
    } | Out-Null
    $beforeResize = Get-UiaWindowMetrics -Window $Window
    try {
        $wide = Resize-UiaClient -Window $Window -LogicalWidth 1200 -LogicalHeight 700 -TimeoutSeconds $TimeoutSeconds
    }
    catch {
        # Cross-monitor DPI changes can arrive after the native move; keep both measurements for a reproducible failure.
        $resizeFailure = $_.Exception.Message
        $afterResize = Get-UiaWindowMetrics -Window $Window
        $captureEvidence = 'window capture unavailable'
        try {
            $failureScreenshot = Join-Path $EvidenceDirectory "resize-failure-$Index-$Role.png"
            Save-MixedMonitorScreenshot -Window $Window -Path $failureScreenshot -TimeoutSeconds 5
            Get-UiaTree -Element $Window | Set-Content -LiteralPath (
                Join-Path $EvidenceDirectory "resize-failure-$Index-$Role-uia.txt") -Encoding utf8
            $controlBounds = foreach ($name in @('Project diagnostics', 'Activity', 'Workbench status',
                    'Cancel current operation')) {
                $control = Find-UiaElement -Root $Window -Condition (New-UiaCondition -Name $name)
                if ($null -ne $control) {
                    $rectangle = $control.Current.BoundingRectangle
                    "$name`: offscreen=$($control.Current.IsOffscreen) " +
                        "bounds=$($rectangle.Left),$($rectangle.Top),$($rectangle.Right),$($rectangle.Bottom)"
                }
            }
            $controlBounds | Set-Content -LiteralPath (
                Join-Path $EvidenceDirectory "resize-failure-$Index-$Role-bounds.txt") -Encoding utf8
            $captureEvidence = "window capture: $failureScreenshot"
        }
        catch {
            $captureEvidence = "window capture failed: $($_.Exception.Message)"
        }
        throw "1200x700 resize after $Role move failed: $resizeFailure; " +
            "before=$($beforeResize.LogicalClientWidth)x$($beforeResize.LogicalClientHeight) at $($beforeResize.Dpi) DPI " +
            "(window $($beforeResize.WindowWidth)x$($beforeResize.WindowHeight), " +
            "client $($beforeResize.ClientWidth)x$($beforeResize.ClientHeight), " +
            "position $($beforeResize.WindowLeft),$($beforeResize.WindowTop)); " +
            "after=$($afterResize.LogicalClientWidth)x$($afterResize.LogicalClientHeight) at $($afterResize.Dpi) DPI " +
            "(window $($afterResize.WindowWidth)x$($afterResize.WindowHeight), " +
            "client $($afterResize.ClientWidth)x$($afterResize.ClientHeight), " +
            "position $($afterResize.WindowLeft),$($afterResize.WindowTop)); $captureEvidence."
    }
    $morphs = Wait-UiaElement -Root $Window -Condition (New-UiaCondition -ControlType 'Button' -Name 'Morphs') `
        -Description 'Morphs navigation on moved Workbench' -TimeoutSeconds $TimeoutSeconds
    $morphs.SetFocus()
    Wait-UiaKeyboardFocus -Element $morphs -TimeoutSeconds $TimeoutSeconds | Out-Null
    # Startup may select another Area. The responsive launcher is meaningful only after entering Morphs.
    Send-UiaKeys -ProcessId $Window.Current.ProcessId -Keys '^2' -TimeoutSeconds $TimeoutSeconds
    Wait-UiaElement -Root $Window -Condition (New-UiaCondition -ControlType 'Text' -Name 'Morphs editor: no selection') `
        -Description 'Morphs editor after area navigation' -TimeoutSeconds $TimeoutSeconds | Out-Null
    $morphs = Wait-UiaElement -Root $Window -Condition (New-UiaCondition -ControlType 'Button' -Name 'Morphs') `
        -Description 'Morphs navigation after area selection' -TimeoutSeconds $TimeoutSeconds
    $morphs.SetFocus()
    Wait-UiaKeyboardFocus -Element $morphs -TimeoutSeconds $TimeoutSeconds | Out-Null
    Assert-MixedMonitorBounds -Metrics $wide -Monitor $Monitor -Control $morphs
    $launcherCondition = New-UiaCondition -ControlType 'Button' -Name 'Open Morphs list'
    $wideLauncher = Find-UiaElement -Root $Window -Condition $launcherCondition
    if ($null -ne $wideLauncher -and -not $wideLauncher.Current.IsOffscreen) {
        throw "Workbench remained narrow at the 1200-logical-pixel breakpoint on $Role."
    }

    $narrow = Resize-UiaClient -Window $Window -LogicalWidth 1199 -LogicalHeight 700 `
        -TimeoutSeconds $TimeoutSeconds
    $narrowLauncher = Wait-UiaElement -Root $Window -Condition $launcherCondition `
        -Description 'narrow Morphs list launcher' -TimeoutSeconds $TimeoutSeconds
    Assert-MixedMonitorBounds -Metrics $narrow -Monitor $Monitor -Control $narrowLauncher
    $minimum = Resize-UiaClient -Window $Window -LogicalWidth 700 -LogicalHeight 500 `
        -AllowMinimumClamp -TimeoutSeconds $TimeoutSeconds
    if ([math]::Abs($minimum.LogicalClientWidth - 800) -gt 2 -or
        [math]::Abs($minimum.LogicalClientHeight - 600) -gt 2) {
        throw "Workbench minimum client did not settle at 800x600 logical pixels on $Role`: " +
            "$($minimum.LogicalClientWidth)x$($minimum.LogicalClientHeight) at $($minimum.Dpi) DPI."
    }
    $minimumLauncher = Wait-UiaElement -Root $Window -Condition $launcherCondition `
        -Description 'minimum-size Morphs list launcher' -TimeoutSeconds $TimeoutSeconds
    Assert-MixedMonitorBounds -Metrics $minimum -Monitor $Monitor -Control $minimumLauncher
    $minimumStem = "landing-$Index-$Role-minimum"
    $minimumScreenshot = Join-Path $EvidenceDirectory "$minimumStem.png"
    $minimumTree = Join-Path $EvidenceDirectory "$minimumStem-uia.txt"
    Save-MixedMonitorScreenshot -Window $Window -Path $minimumScreenshot -TimeoutSeconds $TimeoutSeconds
    Get-UiaTree -Element $Window | Set-Content -LiteralPath $minimumTree -Encoding utf8
    # The bottom controls were clipped by a mixed-DPI minimum-size regression even while the launcher stayed visible.
    $bottomControls = @(
        @{ type = 'Text'; name = 'Project diagnostics' },
        @{ type = 'List'; name = 'Activity' },
        @{ type = 'Button'; name = 'Retry selected activity' },
        @{ type = 'Text'; name = 'Workbench status' },
        @{ type = 'Button'; name = 'Cancel current operation' }
    )
    foreach ($control in $bottomControls) {
        $element = Wait-UiaElement -Root $Window -Condition (
            New-UiaCondition -ControlType $control.type -Name $control.name) `
            -Description "minimum-size $($control.name)" -TimeoutSeconds $TimeoutSeconds
        Assert-MixedMonitorBounds -Metrics $minimum -Monitor $Monitor -Control $element
    }
    $final = Resize-UiaClient -Window $Window -LogicalWidth 1200 -LogicalHeight 700 -TimeoutSeconds $TimeoutSeconds
    $morphs = Wait-UiaElement -Root $Window -Condition (New-UiaCondition -ControlType 'Button' -Name 'Morphs') `
        -Description 'Morphs navigation after responsive resize' -TimeoutSeconds $TimeoutSeconds
    $morphs.SetFocus()
    Wait-UiaKeyboardFocus -Element $morphs -TimeoutSeconds $TimeoutSeconds | Out-Null
    Assert-MixedMonitorBounds -Metrics $final -Monitor $Monitor -Control $morphs
    if ($final.Dpi -ne $Monitor.dpi) { throw "Workbench changed DPI during $Role layout inspection." }
    $stem = "landing-$Index-$Role"
    $screenshot = Join-Path $EvidenceDirectory "$stem.png"
    $tree = Join-Path $EvidenceDirectory "$stem-uia.txt"
    Save-MixedMonitorScreenshot -Window $Window -Path $screenshot -TimeoutSeconds $TimeoutSeconds
    Get-UiaTree -Element $Window | Set-Content -LiteralPath $tree -Encoding utf8
    [ordered]@{
        display = $Role; deviceName = $Monitor.deviceName; monitorDevicePath = $Monitor.monitorDevicePath
        expectedDpi = $Monitor.dpi; dpi = $final.Dpi
        wide = ConvertTo-MixedMonitorMetricsEvidence -Metrics $wide
        narrow = ConvertTo-MixedMonitorMetricsEvidence -Metrics $narrow
        minimum = ConvertTo-MixedMonitorMetricsEvidence -Metrics $minimum
        minimumBottomControls = @($bottomControls | ForEach-Object { $_.name })
        minimumScreenshot = [IO.Path]::GetFileName($minimumScreenshot)
        minimumUiaTree = [IO.Path]::GetFileName($minimumTree)
        final = ConvertTo-MixedMonitorMetricsEvidence -Metrics $final
        focusedControl = $morphs.Current.Name
        screenshot = [IO.Path]::GetFileName($screenshot)
        uiaTree = [IO.Path]::GetFileName($tree)
    }
}

<#
.SYNOPSIS
    Launches one fresh process from the unchanged app image and moves that same window primary→secondary→primary.
.NOTES
    The child uses an isolated Preview profile and an environment with host-Java discovery paths removed. The
    process must exit before the next primary-scale case; a failed forced stop fails the audit.
#>
function Invoke-MixedMonitorCase {
    param([string]$ArchivePath, [string]$ArchiveSha256, [int]$ScalePercent,
        $Primary, $Secondary, [string]$WorkRoot, [string]$LauncherName,
        [string]$ExpectedAppVersion, [string]$EvidenceDirectory,
        [int]$StartupTimeoutSeconds, [int]$StepTimeoutSeconds, [int]$ExitTimeoutSeconds,
        [switch]$KeepWorkRoot)
    $imageRoot = Join-Path $WorkRoot 'image'
    $caseRoot = Join-Path $WorkRoot "case-$ScalePercent"
    $workDir = Join-Path $caseRoot 'work'
    $profile = Join-Path $caseRoot 'local-app-data/BS2BG Preview'
    New-Item -ItemType Directory -Path $EvidenceDirectory, $workDir, $profile -Force | Out-Null
    if (-not (Test-Path -LiteralPath $imageRoot)) {
        Expand-Archive -LiteralPath $ArchivePath -DestinationPath $imageRoot -Force
    }
    $imageDirectory = Join-Path $imageRoot $LauncherName
    $launcher = Join-Path $imageDirectory "$LauncherName.exe"
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
        throw "Packaged archive has no $LauncherName\$LauncherName.exe."
    }
    $config = Read-LauncherConfig -Path (Join-Path $imageDirectory "app\$LauncherName.cfg")
    Assert-LauncherSingleProcessMode -Config $config
    if ($ExpectedAppVersion) {
        $options = @($config['JavaOptions']['java-options'])
        if ($options -cnotcontains "-Djpackage.app-version=$ExpectedAppVersion") {
            throw "Launcher configuration does not stamp app version $ExpectedAppVersion."
        }
    }
    foreach ($name in @('settings.json', 'settings_UUNP.json')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "../../$name") -Destination (Join-Path $profile $name)
    }
    $scrubbed = Get-ScrubbedEnvironment -Environment ([Environment]::GetEnvironmentVariables())
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $launcher
    $startInfo.WorkingDirectory = $workDir
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Environment.Clear()
    foreach ($name in $scrubbed.Variables.Keys) { $startInfo.Environment[$name] = "$($scrubbed.Variables[$name])" }
    $startInfo.Environment['LOCALAPPDATA'] = Join-Path $caseRoot 'local-app-data'
    $app = $null
    $stdoutTask = $null
    $stderrTask = $null
    try {
        $app = [Diagnostics.Process]::Start($startInfo)
        $stdoutTask = $app.StandardOutput.ReadToEndAsync()
        $stderrTask = $app.StandardError.ReadToEndAsync()
        $window = Wait-UiaWindow -ProcessId $app.Id -Title 'BS2BG Preview' `
            -TimeoutSeconds $StartupTimeoutSeconds
        $jvm = @($app.Modules | Where-Object { $_.ModuleName -eq 'jvm.dll' })
        if ($jvm.Count -ne 1 -or -not $jvm[0].FileName.StartsWith($imageDirectory,
                [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Packaged launcher did not load exactly one image-local jvm.dll.'
        }
        $landings = [System.Collections.Generic.List[object]]::new()
        $route = @(
            @{ role = 'primary'; monitor = $Primary },
            @{ role = 'secondary'; monitor = $Secondary },
            @{ role = 'primary'; monitor = $Primary }
        )
        Initialize-MixedMonitorNative
        # The audit process was launched at the original scale; native bounds and resize calls must follow each
        # landing monitor instead of the process's stale system-DPI awareness.
        $previousAwareness = [BS2BG.MixedMonitorProbeV1]::EnterPerMonitorAwareness()
        try {
            for ($index = 0; $index -lt $route.Count; $index++) {
                $step = $route[$index]
                $landings.Add((Inspect-MixedMonitorLanding -Window $window -Monitor $step.monitor `
                        -Role $step.role -Index ($index + 1) -EvidenceDirectory $EvidenceDirectory `
                        -TimeoutSeconds $StepTimeoutSeconds))
            }
        }
        finally {
            [BS2BG.MixedMonitorProbeV1]::RestoreAwareness($previousAwareness)
        }
        Close-UiaWindow -Window $window
        if (-not $app.WaitForExit($ExitTimeoutSeconds * 1000)) {
            throw "Packaged launcher did not exit within $ExitTimeoutSeconds seconds."
        }
        if ($app.ExitCode -ne 0) { throw "Packaged launcher exited with code $($app.ExitCode)." }
        return [pscustomobject]@{
            archiveSha256 = $ArchiveSha256; primaryScalePercent = $ScalePercent
            applicationPid = $app.Id; processId = $app.Id; exitCode = $app.ExitCode
            imageDirectory = $imageDirectory; launcherSha256 = (Get-FileHash $launcher -Algorithm SHA256).Hash.ToLowerInvariant()
            removedJavaVariables = $scrubbed.RemovedVariables
            landings = $landings
        }
    }
    finally {
        $stopFailure = $null
        if ($null -ne $app -and -not $app.HasExited) {
            try {
                $app.Kill()
                if (-not $app.WaitForExit($ExitTimeoutSeconds * 1000)) {
                    throw "Packaged launcher did not exit within $ExitTimeoutSeconds seconds after forced stop."
                }
            }
            catch {
                # Exiting between the liveness check and Kill is harmless; a still-running process is not.
                if (-not $app.HasExited) { $stopFailure = $_.Exception.Message }
            }
        }
        $exited = $null -eq $app -or $app.HasExited
        if ($exited -and $null -ne $stdoutTask) {
            try { $stdoutTask.Result | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'launcher-stdout.txt') -Encoding utf8 }
            catch {
                # Forced termination can tear down a redirected pipe; retain the original audit result.
            }
        }
        if ($exited -and $null -ne $stderrTask) {
            try { $stderrTask.Result | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'launcher-stderr.txt') -Encoding utf8 }
            catch {
                # Forced termination can tear down a redirected pipe; retain the original audit result.
            }
        }
        if ($null -ne $app) { $app.Dispose() }
        if (-not $exited) {
            throw "Could not prove packaged launcher exit before display restoration: $stopFailure"
        }
    }
}

<#
.SYNOPSIS
    Exercises an unchanged packaged archive across 150↔100 and 125↔100 monitor moves.
.DESCRIPTION
    Guards and records the original primary scale before mutation, retains per-case evidence, and restores the
    captured display in finally. A killed process leaves a recovery record compatible with smoke-dpi-matrix.ps1
    -RestoreOnly. The secondary display must already be at 100%; it is never changed by this audit.
.PARAMETER WorkRoot
    New temporary directory for the extracted image and isolated Preview profile; an existing directory is refused.
.PARAMETER EvidencePath
    Aggregate report path. It and its per-case artifact directories must be outside WorkRoot when cleanup is enabled.
.PARAMETER RecoveryPath
    Display recovery record path. It must be outside WorkRoot when cleanup is enabled.
.OUTPUTS
    Aggregate evidence object on success. Failure evidence is written before throwing.
#>
function Invoke-MixedMonitorDpiAudit {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$ArchivePath,
        [Parameter(Mandatory)] [string]$EvidencePath,
        [Parameter(Mandatory)] [string]$RecoveryPath,
        [string]$WorkRoot = (Join-Path $env:TEMP ('BS2BG-mixed-monitor-' + [guid]::NewGuid().ToString('N'))),
        [string]$LauncherName = 'BS2BG',
        [string]$ExpectedAppVersion = '',
        [int]$StartupTimeoutSeconds = 90,
        [int]$StepTimeoutSeconds = 30,
        [int]$ExitTimeoutSeconds = 30,
        [switch]$KeepWorkRoot
    )
    if (-not $KeepWorkRoot) {
        Assert-MixedMonitorPathHasNoAlias -Path $WorkRoot -Parameter 'WorkRoot'
        $workRootPath = [IO.Path]::TrimEndingDirectorySeparator([IO.Path]::GetFullPath($WorkRoot))
        $evidenceFilePath = [IO.Path]::GetFullPath($EvidencePath)
        $evidenceDirectory = [IO.Path]::GetDirectoryName($evidenceFilePath)
        $protectedPaths = @(
            @{ Parameter = 'EvidencePath'; FullPath = $evidenceFilePath }
            @{ Parameter = 'EvidencePath'; FullPath = [IO.Path]::Combine($evidenceDirectory, 'mixed-150') }
            @{ Parameter = 'EvidencePath'; FullPath = [IO.Path]::Combine($evidenceDirectory, 'mixed-125') }
            @{ Parameter = 'RecoveryPath'; FullPath = [IO.Path]::GetFullPath($RecoveryPath) }
        )
        # Each landing writes artifacts beside the report; cleanup must not remove those or the recovery record.
        foreach ($protected in $protectedPaths) {
            Assert-MixedMonitorPathHasNoAlias -Path $protected.FullPath -Parameter $protected.Parameter
            if ($protected.FullPath.Equals($workRootPath, [StringComparison]::OrdinalIgnoreCase) -or
                $protected.FullPath.StartsWith($workRootPath + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase)) {
                throw "$($protected.Parameter) must be outside WorkRoot unless KeepWorkRoot is set."
            }
        }
    }
    $lock = & $script:dpiMatrixModule { param($Path)
        Open-DpiMatrixLock -RecoveryPath $Path
    } $RecoveryPath
    try {
        & $script:dpiMatrixModule { param($Path)
            Assert-DpiMatrixRecoveryRecord -RecoveryPath $Path
        } $RecoveryPath
        $ArchivePath = (Resolve-Path -LiteralPath $ArchivePath).Path
        $hash = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $display = Get-PrimaryDisplayScale
        foreach ($scale in @(150,125)) {
            if ($scale -notin $display.supportedScales) { throw "Primary display does not offer required scale $scale%." }
        }
        $original = Assert-MixedMonitorPair -Topology @(Read-MixedMonitorTopology) -Display $display `
            -ExpectedPrimaryScale $display.scalePercent
        if (Test-Path -LiteralPath $WorkRoot) { throw "WorkRoot already exists: $WorkRoot" }
        $recovery = [ordered]@{
            schema = 'bs2bg.dpi-matrix-recovery/1'; status = 'pending'
            machine = [Environment]::MachineName; user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
            ownerPid = $PID; ownerStartedAtUtc = (Get-Process -Id $PID).StartTime.ToUniversalTime().ToString('o')
            recordedAtUtc = [DateTimeOffset]::UtcNow.ToString('o'); display = $display
            audit = 'bs2bg.mixed-monitor-dpi/1'
        }
        # Persist original state before opening Settings; a killed runner cannot execute finally.
        Write-MixedMonitorJson -Path $RecoveryPath -Value $recovery
        $result = [ordered]@{
            schema = 'bs2bg.mixed-monitor-dpi/1'; recordedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
            passed = $false; archive = $ArchivePath; archiveSha256 = $hash
            originalDisplay = $display; originalTopology = $original
            requestedPrimaryScales = @(150,125); secondaryScalePercent = 100
            recoveryPath = [IO.Path]::GetFullPath($RecoveryPath)
            runs = [System.Collections.Generic.List[object]]::new()
            restoration = $null; workflowError = $null; restorationError = $null; cleanupError = $null
        }
        try {
            foreach ($scale in @(150,125)) {
                $run = [ordered]@{ scalePercent = $scale; archiveSha256 = $hash; passed = $false; case = $null; error = $null }
                $result.runs.Add($run)
                try {
                    if ((Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
                        throw 'Packaged archive changed during the mixed-monitor audit.'
                    }
                    $selected = Set-PrimaryDisplayScale -Display $display -ScalePercent $scale
                    if ($selected.scalePercent -ne $scale -or $selected.dpi -ne ($scale * 96 / 100)) {
                        throw "Primary scale transition did not report $scale% native DPI."
                    }
                    $pair = Assert-MixedMonitorPair -Topology @(Read-MixedMonitorTopology) -Display $display `
                        -ExpectedPrimaryScale $scale
                    Assert-MixedMonitorTopologyStable -Original $original -Current $pair
                    $caseArguments = @{
                        ArchivePath = $ArchivePath; ArchiveSha256 = $hash; ScalePercent = $scale
                        Primary = $pair.primary; Secondary = $pair.secondary; WorkRoot = $WorkRoot
                        LauncherName = $LauncherName; ExpectedAppVersion = $ExpectedAppVersion
                        EvidenceDirectory = (Join-Path (Split-Path -Parent $EvidencePath) "mixed-$scale")
                        StartupTimeoutSeconds = $StartupTimeoutSeconds; StepTimeoutSeconds = $StepTimeoutSeconds
                        ExitTimeoutSeconds = $ExitTimeoutSeconds; KeepWorkRoot = $KeepWorkRoot
                    }
                    $run.case = Invoke-MixedMonitorCase @caseArguments
                    if ($run.case.exitCode -ne 0) { throw "Packaged process did not exit cleanly at $scale%." }
                    if ((Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
                        throw 'Packaged archive changed during the mixed-monitor audit.'
                    }
                    $run.passed = $true
                    Write-MixedMonitorJson -Path $EvidencePath -Value $result
                }
                catch { $run.error = $_.Exception.Message; throw }
            }
        }
        catch { $result.workflowError = $_.Exception.Message }
        finally {
            try {
                $result.restoration = Set-PrimaryDisplayScale -Display $display -ScalePercent $display.scalePercent
                if ($result.restoration.scalePercent -ne $display.scalePercent -or
                    $result.restoration.dpi -ne $display.dpi) {
                    throw 'Display restoration did not report the original scale and native DPI.'
                }
                $restored = Assert-MixedMonitorPair -Topology @(Read-MixedMonitorTopology) -Display $display `
                    -ExpectedPrimaryScale $display.scalePercent
                Assert-MixedMonitorTopologyStable -Original $original -Current $restored
                $recovery.status = 'restored'
                $recovery['restoredAtUtc'] = [DateTimeOffset]::UtcNow.ToString('o')
                Write-MixedMonitorJson -Path $RecoveryPath -Value $recovery
            }
            catch { $result.restorationError = $_.Exception.Message }
            if (-not $KeepWorkRoot) {
                try { Remove-MixedMonitorWorkRoot -WorkRoot $WorkRoot }
                catch { $result.cleanupError = $_.Exception.Message }
            }
            $result.passed = -not $result.workflowError -and -not $result.restorationError -and -not $result.cleanupError
            Write-MixedMonitorJson -Path $EvidencePath -Value $result
        }
        if (-not $result.passed) {
            throw "Mixed-monitor DPI audit failed. Workflow: $($result.workflowError) Restoration: $($result.restorationError) Cleanup: $($result.cleanupError). Evidence: $EvidencePath"
        }
        return [pscustomobject]$result
    }
    finally { $lock.Dispose() }
}

Export-ModuleMember -Function Invoke-MixedMonitorDpiAudit
