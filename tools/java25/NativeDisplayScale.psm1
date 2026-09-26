Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Windows defines 100% scaling as USER_DEFAULT_SCREEN_DPI (96). This is the
# percentage conversion baseline; the monitor's current DPI is always measured.
Set-Variable -Name UserDefaultScreenDpi -Scope Script -Option Constant -Value 96

if (-not ('BS2BG.NativeDisplayScaleProbeV1' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

namespace BS2BG
{
    /// <summary>Reads the primary display without changing display configuration or showing a window.</summary>
    public static class NativeDisplayScaleProbeV1
    {
        [StructLayout(LayoutKind.Sequential)]
        public struct Rect { public int Left, Top, Right, Bottom; }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct MonitorInfo
        {
            public int Size;
            public Rect Monitor, Work;
            public uint Flags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string Device;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct DisplayDevice
        {
            public int Size;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string Name;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Description;
            public uint StateFlags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Id;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Key;
        }

        /// <summary>Native primary-monitor identity, physical bounds, and effective DPI.</summary>
        public sealed class Capture
        {
            public string DeviceName;
            public string MonitorDevicePath;
            public uint Dpi;
            public int MonitorCount;
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
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CreateWindowEx(uint extendedStyle, string className, string title,
            uint style, int x, int y, int width, int height, IntPtr parent, IntPtr menu, IntPtr instance, IntPtr parameter);
        [DllImport("user32.dll")]
        private static extern uint GetDpiForWindow(IntPtr window);
        [DllImport("user32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool DestroyWindow(IntPtr window);

        /// <summary>Captures the primary display on the calling thread and restores its DPI context before returning.</summary>
        /// <exception cref="Win32Exception">A native monitor, window, or DPI-awareness operation failed.</exception>
        /// <exception cref="InvalidOperationException">No primary display, stable identity, or valid DPI was available.</exception>
        public static Capture Read()
        {
            // PowerShell's process awareness can virtualize monitor DPI and coordinates. A temporary
            // per-monitor-v2 thread context gives the hidden window the monitor's actual effective DPI.
            IntPtr previous = SetThreadDpiAwarenessContext(new IntPtr(-4));
            if (previous == IntPtr.Zero)
                throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot enter per-monitor DPI awareness.");
            IntPtr window = IntPtr.Zero;
            try
            {
                MonitorInfo primary = new MonitorInfo();
                bool found = false;
                int monitorError = 0;
                int monitorCount = 0;
                MonitorCallback callback = delegate(IntPtr monitor, IntPtr dc, ref Rect bounds, IntPtr data)
                {
                    MonitorInfo info = new MonitorInfo { Size = Marshal.SizeOf(typeof(MonitorInfo)) };
                    if (!GetMonitorInfo(monitor, ref info))
                    {
                        monitorError = Marshal.GetLastWin32Error();
                        return false;
                    }
                    if ((info.Flags & 1) != 0) { primary = info; found = true; }
                    monitorCount++;
                    return true;
                };
                bool enumerated = EnumDisplayMonitors(IntPtr.Zero, IntPtr.Zero, callback, IntPtr.Zero);
                if (!enumerated || monitorError != 0)
                    throw new Win32Exception(monitorError != 0 ? monitorError : Marshal.GetLastWin32Error(), "Cannot enumerate monitors.");
                if (!found) throw new InvalidOperationException("No primary display was found.");

                DisplayDevice device = new DisplayDevice { Size = Marshal.SizeOf(typeof(DisplayDevice)) };
                if (!EnumDisplayDevices(primary.Device, 0, ref device, 1) || String.IsNullOrWhiteSpace(device.Id))
                    throw new InvalidOperationException("Cannot read the primary monitor device interface identity.");

                // No WS_VISIBLE means no desktop input/focus side effect. The window exists only
                // until its DPI is read and must be destroyed on this same native thread.
                window = CreateWindowEx(0, "STATIC", "BS2BG DPI probe", 0x80000000,
                    primary.Monitor.Left, primary.Monitor.Top, 1, 1,
                    IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
                if (window == IntPtr.Zero)
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot create the hidden DPI probe window.");
                uint dpi = GetDpiForWindow(window);
                if (dpi == 0) throw new InvalidOperationException("The hidden probe window returned no DPI.");
                return new Capture { DeviceName = primary.Device, MonitorDevicePath = device.Id,
                    Dpi = dpi, MonitorCount = monitorCount, Bounds = primary.Monitor };
            }
            finally
            {
                // Restore awareness even if window cleanup fails; neither resource belongs to the caller.
                int destroyError = 0;
                if (window != IntPtr.Zero && !DestroyWindow(window)) destroyError = Marshal.GetLastWin32Error();
                if (SetThreadDpiAwarenessContext(previous) == IntPtr.Zero)
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Cannot restore the thread DPI context.");
                if (destroyError != 0) throw new Win32Exception(destroyError, "Cannot destroy the hidden DPI probe window.");
            }
        }
    }
}
'@
}

function Get-NativePrimaryDisplay {
    <#
    .SYNOPSIS
    Reads the primary monitor's stable identity, physical bounds, and effective DPI.
    .DESCRIPTION
    Uses documented Win32 APIs and an invisible one-pixel window under temporary
    per-monitor DPI awareness. Does not change scaling or desktop focus. Throws if
    native capture fails or the scale is not a positive integral percentage. Supported
    choices are discovered separately from Windows Settings, not assumed here.
    .OUTPUTS
    PSCustomObject with deviceName, monitorDevicePath, scalePercent, dpi, monitorCount, and bounds.
    #>
    [CmdletBinding()]
    param()

    $capture = [BS2BG.NativeDisplayScaleProbeV1]::Read()
    $scale = [double]$capture.Dpi * 100 / $script:UserDefaultScreenDpi
    if ($scale -ne [Math]::Truncate($scale) -or $scale -le 0) {
        throw "Primary display reported unsupported DPI $($capture.Dpi) ($scale%)."
    }
    [pscustomobject]@{
        deviceName = $capture.DeviceName
        monitorDevicePath = $capture.MonitorDevicePath
        scalePercent = [int]$scale
        dpi = [int]$capture.Dpi
        monitorCount = $capture.MonitorCount
        bounds = [pscustomobject]@{
            left = $capture.Bounds.Left
            top = $capture.Bounds.Top
            width = $capture.Bounds.Right - $capture.Bounds.Left
            height = $capture.Bounds.Bottom - $capture.Bounds.Top
        }
    }
}

Export-ModuleMember -Function Get-NativePrimaryDisplay
