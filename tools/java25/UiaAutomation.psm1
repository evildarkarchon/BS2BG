#Requires -Version 5.1
<#
.SYNOPSIS
    Thin Windows UI Automation (UIA) helpers for driving the packaged BS2BG launcher from outside the process.

.DESCRIPTION
    Wraps System.Windows.Automation so tools/java25/smoke-app-image.ps1 can locate JavaFX controls the way an
    assistive technology does: by accessible control type (role), accessible name, and tree relationships. Nothing
    here accepts caller-supplied coordinates, automation ids, CSS, or JavaFX internals. Pointer activation uses the
    clickable point or visible content bounds supplied by the semantically located UIA provider; JavaFX exposes the
    rest of its scene graph through public accessibility support, so helpers otherwise see roles, names,
    relationships, and patterns.

    Every wait is bounded and every failure names what was being looked for, so a hung toolkit or a missing
    control fails the smoke run with a diagnosis instead of hanging it.
#>

Set-StrictMode -Version Latest

Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type -AssemblyName WindowsBase

# Native dialogs the JavaFX window owns (FileChooser) are ordinary Win32 top-level windows, but neither the UIA
# desktop root (owned windows are not root children) nor JavaFX's own UIA provider (it only knows its scene graph)
# enumerates them. EnumWindows does, so a dialog is located by its owning process and its title, then handed to UIA
# through AutomationElement.FromHandle; from there every control inside it is reached by role and name as usual.
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public sealed class BS2BGTopLevelWindow
{
    public long Handle;
    public uint ProcessId;
    public bool Visible;
    public string ClassName;
    public string Title;
}

public sealed class BS2BGWindowMetrics
{
    public uint Dpi;
    public int WindowLeft;
    public int WindowTop;
    public int WindowWidth;
    public int WindowHeight;
    public int ClientLeft;
    public int ClientTop;
    public int ClientWidth;
    public int ClientHeight;
    public double LogicalClientWidth;
    public double LogicalClientHeight;
}

public static class BS2BGWindows
{
    [StructLayout(LayoutKind.Sequential)] private struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] private struct POINT { public int X, Y; }
    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
    [DllImport("user32.dll", SetLastError = true)] private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowTextW(IntPtr hWnd, StringBuilder lpString, int nMaxCount);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetClassNameW(IntPtr hWnd, StringBuilder lpString, int nMaxCount);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool PostMessageW(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool GetClientRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool ClientToScreen(IntPtr hWnd, ref POINT point);
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(IntPtr hWnd);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool SetWindowPos(IntPtr hWnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);

    private const uint BM_CLICK = 0x00F5;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint SWP_NOMOVE = 0x0002;
    private const uint SWP_NOZORDER = 0x0004;
    private const uint SWP_NOACTIVATE = 0x0010;

    // Standard Win32 button activation: the same message the control receives from a mouse click, delivered to
    // the button window itself, so no screen position is involved.
    public static bool ClickButton(IntPtr hWnd)
    {
        return PostMessageW(hWnd, BM_CLICK, IntPtr.Zero, IntPtr.Zero);
    }

    /// <summary>
    /// Moves the system pointer to a provider-supplied clickable point and emits one real left-button click.
    /// </summary>
    /// <param name="x">Physical desktop X coordinate returned by UI Automation.</param>
    /// <param name="y">Physical desktop Y coordinate returned by UI Automation.</param>
    /// <exception cref="System.ComponentModel.Win32Exception">Thrown when Windows rejects the move or input.</exception>
    public static void LeftClick(int x, int y)
    {
        if (!SetCursorPos(x, y))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, UIntPtr.Zero);
        mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, UIntPtr.Zero);
    }

    /// <summary>
    /// Brings the top-level owner of a semantically located element to the foreground before global pointer input.
    /// </summary>
    /// <param name="hWnd">Native top-level window handle discovered through the UIA ancestor chain.</param>
    /// <returns>Whether Windows accepted the foreground activation request.</returns>
    public static bool ActivateWindow(IntPtr hWnd)
    {
        return SetForegroundWindow(hWnd);
    }

    /// <summary>
    /// Measures the real client rectangle and window DPI while leaving logical Scene mapping to JavaFX.
    /// </summary>
    /// <param name="hWnd">Native handle of the packaged Workbench window.</param>
    /// <returns>Physical window/client bounds plus client dimensions converted to logical pixels.</returns>
    /// <exception cref="InvalidOperationException">Thrown when Win32 cannot measure or map the window.</exception>
    public static BS2BGWindowMetrics Metrics(IntPtr hWnd)
    {
        RECT window;
        RECT client;
        if (!GetWindowRect(hWnd, out window) || !GetClientRect(hWnd, out client))
            throw new InvalidOperationException("Could not read window/client bounds.");
        var origin = new POINT { X = 0, Y = 0 };
        if (!ClientToScreen(hWnd, ref origin))
            throw new InvalidOperationException("Could not map the client origin to the desktop.");
        uint dpi = GetDpiForWindow(hWnd);
        if (dpi == 0) dpi = 96;
        int clientWidth = client.Right - client.Left;
        int clientHeight = client.Bottom - client.Top;
        return new BS2BGWindowMetrics {
            Dpi = dpi,
            WindowLeft = window.Left,
            WindowTop = window.Top,
            WindowWidth = window.Right - window.Left,
            WindowHeight = window.Bottom - window.Top,
            ClientLeft = origin.X,
            ClientTop = origin.Y,
            ClientWidth = clientWidth,
            ClientHeight = clientHeight,
            LogicalClientWidth = clientWidth * 96.0 / dpi,
            LogicalClientHeight = clientHeight * 96.0 / dpi
        };
    }

    /// <summary>
    /// Resizes to a requested logical client size using the live DPI and non-client inset.
    /// </summary>
    /// <param name="hWnd">Native handle of the packaged Workbench window.</param>
    /// <param name="logicalWidth">Requested logical client width.</param>
    /// <param name="logicalHeight">Requested logical client height.</param>
    /// <returns>True when Win32 accepted the resize request; otherwise false.</returns>
    /// <exception cref="InvalidOperationException">Thrown when the current window metrics cannot be read.</exception>
    public static bool ResizeClient(IntPtr hWnd, double logicalWidth, double logicalHeight)
    {
        var current = Metrics(hWnd);
        int desiredClientWidth = (int)Math.Round(logicalWidth * current.Dpi / 96.0);
        int desiredClientHeight = (int)Math.Round(logicalHeight * current.Dpi / 96.0);
        int nonClientWidth = current.WindowWidth - current.ClientWidth;
        int nonClientHeight = current.WindowHeight - current.ClientHeight;
        return SetWindowPos(hWnd, IntPtr.Zero, 0, 0,
            desiredClientWidth + nonClientWidth,
            desiredClientHeight + nonClientHeight,
            SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
    }

    public static List<BS2BGTopLevelWindow> List(uint processId)
    {
        var result = new List<BS2BGTopLevelWindow>();
        EnumWindows((h, l) =>
        {
            uint owner;
            GetWindowThreadProcessId(h, out owner);
            if (owner != processId) return true;
            var title = new StringBuilder(512);
            GetWindowTextW(h, title, 512);
            var className = new StringBuilder(256);
            GetClassNameW(h, className, 256);
            result.Add(new BS2BGTopLevelWindow { Handle = h.ToInt64(), ProcessId = owner, Visible = IsWindowVisible(h), ClassName = className.ToString(), Title = title.ToString() });
            return true;
        }, IntPtr.Zero);
        return result;
    }
}

public sealed class BS2BGAccessibilityState
{
    public bool HighContrast;
    public bool ClientAreaAnimation;
    public uint HighContrastFlags;
    public string HighContrastScheme;
}

public static class BS2BGSystemPreferences
{
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct HIGHCONTRAST
    {
        public uint cbSize;
        public uint dwFlags;
        public IntPtr lpszDefaultScheme;
    }

    [DllImport("user32.dll", EntryPoint = "SystemParametersInfoW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool SystemParametersInfoHighContrast(uint action, uint parameter,
        ref HIGHCONTRAST value, uint flags);

    [DllImport("user32.dll", EntryPoint = "SystemParametersInfoW", SetLastError = true)]
    private static extern bool SystemParametersInfoBoolean(uint action, uint parameter, ref int value, uint flags);

    [DllImport("user32.dll", EntryPoint = "SystemParametersInfoW", SetLastError = true)]
    private static extern bool SystemParametersInfoBooleanValue(uint action, uint parameter, IntPtr value, uint flags);

    private const uint SPI_GETHIGHCONTRAST = 0x0042;
    private const uint SPI_SETHIGHCONTRAST = 0x0043;
    private const uint SPI_GETCLIENTAREAANIMATION = 0x1042;
    private const uint SPI_SETCLIENTAREAANIMATION = 0x1043;
    private const uint HCF_HIGHCONTRASTON = 0x00000001;
    private const uint SPIF_UPDATEINIFILE = 0x0001;
    private const uint SPIF_SENDCHANGE = 0x0002;

    /// <summary>
    /// Captures the accessibility preferences changed by the packaged theme/motion smoke.
    /// </summary>
    /// <returns>A complete state value suitable for unconditional restoration in a finally block.</returns>
    /// <exception cref="System.ComponentModel.Win32Exception">Thrown when Windows rejects a query.</exception>
    public static BS2BGAccessibilityState Capture()
    {
        var highContrast = ReadHighContrast();
        int animation = 0;
        if (!SystemParametersInfoBoolean(SPI_GETCLIENTAREAANIMATION, 0, ref animation, 0))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        return new BS2BGAccessibilityState {
            HighContrast = (highContrast.dwFlags & HCF_HIGHCONTRASTON) != 0,
            ClientAreaAnimation = animation != 0,
            HighContrastFlags = highContrast.dwFlags,
            HighContrastScheme = Marshal.PtrToStringUni(highContrast.lpszDefaultScheme)
        };
    }

    /// <summary>
    /// Toggles High Contrast while retaining every unrelated HIGHCONTRAST flag and the user's scheme.
    /// </summary>
    /// <param name="enabled">Whether the High Contrast mode bit should be active.</param>
    /// <exception cref="System.ComponentModel.Win32Exception">Thrown when Windows rejects the query or change.</exception>
    public static void SetHighContrast(bool enabled)
    {
        var current = Capture();
        uint flags = enabled
            ? current.HighContrastFlags | HCF_HIGHCONTRASTON
            : current.HighContrastFlags & ~HCF_HIGHCONTRASTON;
        WriteHighContrast(flags, current.HighContrastScheme);
    }

    /// <summary>
    /// Sets whether client-area animation is enabled; JavaFX exposes the inverse as reduced motion.
    /// </summary>
    /// <param name="enabled">Whether client-area animation should be enabled.</param>
    /// <exception cref="System.ComponentModel.Win32Exception">Thrown when Windows rejects the change.</exception>
    public static void SetClientAreaAnimation(bool enabled)
    {
        IntPtr value = enabled ? new IntPtr(1) : IntPtr.Zero;
        // Windows broadcasts WM_SETTINGCHANGE only after the profile update; Restore writes the captured value back.
        if (!SystemParametersInfoBooleanValue(SPI_SETCLIENTAREAANIMATION, 0, value,
                SPIF_UPDATEINIFILE | SPIF_SENDCHANGE))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
    }

    /// <summary>
    /// Restores exactly the flags, scheme, and animation preference captured before a packaged smoke run.
    /// </summary>
    /// <param name="state">Original preference value returned by Capture.</param>
    /// <exception cref="ArgumentNullException">Thrown when state is null.</exception>
    /// <exception cref="System.ComponentModel.Win32Exception">Thrown when Windows rejects either restored value.</exception>
    public static void Restore(BS2BGAccessibilityState state)
    {
        if (state == null) throw new ArgumentNullException("state");
        WriteHighContrast(state.HighContrastFlags, state.HighContrastScheme);
        SetClientAreaAnimation(state.ClientAreaAnimation);
    }

    /// <summary>Reads the complete native High Contrast structure.</summary>
    /// <exception cref="System.ComponentModel.Win32Exception">Thrown when Windows rejects the query.</exception>
    private static HIGHCONTRAST ReadHighContrast()
    {
        var value = new HIGHCONTRAST { cbSize = (uint)Marshal.SizeOf(typeof(HIGHCONTRAST)) };
        if (!SystemParametersInfoHighContrast(SPI_GETHIGHCONTRAST, value.cbSize, ref value, 0))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        return value;
    }

    /// <summary>Writes a complete native High Contrast structure and broadcasts the live change.</summary>
    /// <exception cref="System.ComponentModel.Win32Exception">Thrown when Windows rejects the change.</exception>
    private static void WriteHighContrast(uint flags, string scheme)
    {
        IntPtr schemePointer = IntPtr.Zero;
        try {
            if (!String.IsNullOrEmpty(scheme)) schemePointer = Marshal.StringToHGlobalUni(scheme);
            var value = new HIGHCONTRAST {
                cbSize = (uint)Marshal.SizeOf(typeof(HIGHCONTRAST)),
                dwFlags = flags,
                lpszDefaultScheme = schemePointer
            };
            if (!SystemParametersInfoHighContrast(SPI_SETHIGHCONTRAST, value.cbSize, ref value, SPIF_SENDCHANGE))
                throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        }
        finally
        {
            if (schemePointer != IntPtr.Zero) Marshal.FreeHGlobal(schemePointer);
        }
    }
}
'@

<#
.SYNOPSIS
    Builds a UIA condition from an accessible control type, an accessible name, and/or an owning process id.
.PARAMETER ControlType
    Name of a System.Windows.Automation.ControlType field (Button, MenuItem, List, ListItem, Edit, Document, ...).
.NOTES
    Names are matched exactly (UIA's default, case-sensitive) so a locator cannot drift onto a similar control.
#>
function New-UiaCondition {
    [CmdletBinding()]
    param(
        [string]$ControlType,
        [string]$Name,
        [int]$ProcessId = 0
    )

    $conditions = @()
    if ($ControlType) {
        $field = [System.Windows.Automation.ControlType].GetField($ControlType)
        if ($null -eq $field) { throw "Unknown UIA control type '$ControlType'." }
        $type = $field.GetValue($null)
        $conditions += New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ControlTypeProperty, $type)
    }
    if ($PSBoundParameters.ContainsKey('Name')) {
        $conditions += New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, $Name)
    }
    if ($ProcessId -gt 0) {
        $conditions += New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ProcessIdProperty, $ProcessId)
    }
    switch ($conditions.Count) {
        0 { return [System.Windows.Automation.Condition]::TrueCondition }
        1 { return $conditions[0] }
        default { return New-Object System.Windows.Automation.AndCondition($conditions) }
    }
}

<#
.SYNOPSIS
    The element's accessible role as a bare control-type name (Button, Edit, ListItem, ...).
#>
function Get-UiaRoleName {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    return $Element.Current.ControlType.ProgrammaticName -replace '^ControlType\.', ''
}

<#
.SYNOPSIS
    Finds the first element under Root that satisfies Condition, or $null.
#>
function Find-UiaElement {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Root,
        [Parameter(Mandatory)] $Condition,
        [ValidateSet('Children', 'Descendants')] [string]$Scope = 'Descendants'
    )
    $treeScope = [System.Windows.Automation.TreeScope]::$Scope
    return $Root.FindFirst($treeScope, $Condition)
}

<#
.SYNOPSIS
    Finds every element under Root that satisfies Condition (an empty array when there is none).
#>
function Find-UiaElements {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Root,
        [Parameter(Mandatory)] $Condition,
        [ValidateSet('Children', 'Descendants')] [string]$Scope = 'Descendants'
    )
    $treeScope = [System.Windows.Automation.TreeScope]::$Scope
    $found = $Root.FindAll($treeScope, $Condition)
    $result = @()
    foreach ($element in $found) { $result += $element }
    # Enumerated on purpose: callers pipe the elements one by one and wrap in @() where they need a count. Returning
    # the array unenumerated (, $result) would hand an empty array to Where-Object as a single object.
    return $result
}

<#
.SYNOPSIS
    Polls until Test returns a non-null/true value, or throws after TimeoutSeconds naming Description.
.OUTPUTS
    The value Test produced.
#>
function Wait-UiaCondition {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [scriptblock]$Test,
        [Parameter(Mandatory)] [string]$Description,
        [int]$TimeoutSeconds = 30,
        [int]$PollMilliseconds = 250
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastError = $null
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $value = & $Test
            if ($null -ne $value -and $value -ne $false) { return $value }
        }
        catch {
            # UIA throws when an element vanishes between calls (ElementNotAvailable); keep polling until the deadline.
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    }
    $suffix = $(if ($lastError) { " (last error: $lastError)" } else { '' })
    throw "Timed out after $TimeoutSeconds s waiting for $Description$suffix"
}

<#
.SYNOPSIS
    Waits for the first element under Root that satisfies Condition.
#>
function Wait-UiaElement {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Root,
        [Parameter(Mandatory)] $Condition,
        [Parameter(Mandatory)] [string]$Description,
        [ValidateSet('Children', 'Descendants')] [string]$Scope = 'Descendants',
        [int]$TimeoutSeconds = 30
    )
    return Wait-UiaCondition -Description $Description -TimeoutSeconds $TimeoutSeconds -Test { Find-UiaElement -Root $Root -Condition $Condition -Scope $Scope }
}

<#
.SYNOPSIS
    Waits for a top-level window owned by the process with the given title.
#>
function Wait-UiaWindow {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [int]$ProcessId,
        [Parameter(Mandatory)] [string]$Title,
        [int]$TimeoutSeconds = 60
    )
    # Root UIA name matching can retain the old JavaFX title after Save As. Reacquire the native handle on each
    # poll, then verify the current UIA name and role just as for owned file dialogs.
    return Wait-UiaOwnedWindow -ProcessId $ProcessId -Title $Title -TimeoutSeconds $TimeoutSeconds
}

<#
.SYNOPSIS
    Lists every top-level Win32 window of a process (handle, title, class, visibility), owned dialogs included.
#>
function Get-ProcessTopLevelWindows {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [int]$ProcessId)
    return @([BS2BGWindows]::List([uint32]$ProcessId) | ForEach-Object {
        [pscustomobject]@{ handle = $_.Handle; processId = $_.ProcessId; visible = $_.Visible; className = $_.ClassName; title = $_.Title }
    })
}

<#
.SYNOPSIS
    Waits for a visible top-level window of the process with the given title and returns its UIA element.
.NOTES
    This is how native dialogs owned by the JavaFX window (Open/Save file choosers) are reached; see the note on
    BS2BGWindows above. The title is the dialog's accessible name, which the application sets explicitly.
#>
function Wait-UiaOwnedWindow {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [int]$ProcessId,
        [Parameter(Mandatory)] [string]$Title,
        [int]$TimeoutSeconds = 30
    )
    # The common file dialog briefly exists as a titled but still empty window while the shell builds its contents;
    # a handle taken then yields a UIA element with no type, name, or children. Every poll therefore re-resolves
    # the handle and only a fully formed window (right name, right control type, at least one child) is accepted.
    return Wait-UiaCondition -Description "window '$Title' of process $ProcessId" -TimeoutSeconds $TimeoutSeconds -Test {
        # Not named $matches: that would clobber PowerShell's automatic regex-match variable.
        $candidates = @(Get-ProcessTopLevelWindows -ProcessId $ProcessId | Where-Object { $_.visible -and $_.title -ceq $Title })
        foreach ($match in $candidates) {
            $element = [System.Windows.Automation.AutomationElement]::FromHandle([IntPtr]$match.handle)
            if ($element.Current.Name -cne $Title) { continue }
            if ($element.Current.ControlType -ne [System.Windows.Automation.ControlType]::Window) { continue }
            $firstChild = $element.FindFirst([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)
            if ($null -eq $firstChild) { continue }
            return $element
        }
    }
}

<#
.SYNOPSIS
    Returns every top-level window currently owned by the process.
#>
function Get-UiaProcessWindows {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [int]$ProcessId)
    return Find-UiaElements -Root ([System.Windows.Automation.AutomationElement]::RootElement) -Condition (New-UiaCondition -ControlType 'Window' -ProcessId $ProcessId) -Scope 'Children'
}

<#
.SYNOPSIS
    Returns the named UIA pattern object of an element, or throws naming the element when it is unsupported.
.PARAMETER PatternType
    The pattern type name (InvokePattern, ValuePattern, ...); its static Pattern field identifies the pattern.
#>
function Get-UiaPattern {
    param([Parameter(Mandatory)] $Element, [Parameter(Mandatory)] [string]$PatternType)
    $pattern = [System.Windows.Automation.AutomationPattern]([type]"System.Windows.Automation.$PatternType").GetField('Pattern').GetValue($null)
    $result = $null
    if (-not $Element.TryGetCurrentPattern($pattern, [ref]$result)) {
        throw "Element '$($Element.Current.Name)' ($($Element.Current.ControlType.ProgrammaticName)) does not support $PatternType."
    }
    return $result
}

<#
.SYNOPSIS
    Activates a control through its Invoke pattern (buttons, menu items).
.NOTES
    Not for actions that open a modal dialog. UIA's Invoke is a synchronous cross-process call and JavaFX runs the
    action on its application thread inside that callback; a FileChooser then spins its nested loop inside the
    pending callback, and while it is pending the application process serves no UIA request for any of its
    windows, so the dialog cannot be automated by any client until it closes. Trigger such commands through the
    application's declared keyboard accelerators instead (Send-UiaAccelerator).
#>
function Invoke-UiaElement {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    (Get-UiaPattern -Element $Element -PatternType 'InvokePattern').Invoke()
}

<#
.SYNOPSIS
    Clicks a semantic UIA element with the real Windows pointer at its provider-supplied location.
.NOTES
    The element must first be located by accessible role/name/relationship. No fixed coordinates or row indexes are
    accepted. JavaFX does not always implement TryGetClickablePoint, so the center of its provider-supplied visible
    text content (then the outer bounds) is the constrained fallback; UI Automation remains authoritative across DPI.
.PARAMETER RefreshRoot
    Optional semantic search root used to reacquire JavaFX peers before each clickable-point attempt.
.PARAMETER RefreshCondition
    Role/name condition paired with RefreshRoot; both refresh parameters must be supplied together.
#>
function Invoke-UiaPointerClick {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Element,
        $RefreshRoot,
        [System.Windows.Automation.Condition]$RefreshCondition
    )
    $refresh = $null -ne $RefreshRoot -or $null -ne $RefreshCondition
    if ($refresh -and ($null -eq $RefreshRoot -or $null -eq $RefreshCondition)) {
        throw 'RefreshRoot and RefreshCondition must be supplied together.'
    }
    $point = $null
    $bounds = $null
    $fallbackBounds = $null
    $fallbackSource = 'bounding-rectangle-center'
    $handle = [IntPtr]::Zero
    $elementName = ''
    $clickablePointError = $null
    $attemptCount = $(if ($refresh) { 20 } else { 1 })
    for ($attempt = 0; $attempt -lt $attemptCount; $attempt++) {
        # A refreshed peer may move or disappear; never carry coordinates or a native owner across attempts.
        $fallbackBounds = $null
        $fallbackSource = 'bounding-rectangle-center'
        $handle = [IntPtr]::Zero
        if ($refresh) {
            $candidate = Find-UiaElement -Root $RefreshRoot -Condition $RefreshCondition
            if ($null -eq $candidate) {
                Start-Sleep -Milliseconds 100
                continue
            }
            $Element = $candidate
        }
        try {
            $elementName = $Element.Current.Name
            if (-not $Element.Current.IsEnabled -or $Element.Current.IsOffscreen) {
                throw "Element '$elementName' is not enabled and onscreen for pointer activation."
            }
            # Snapshot provider state before TryGetClickablePoint: JavaFX may invalidate its optional point peer.
            $bounds = $Element.Current.BoundingRectangle
            $fallbackBounds = $bounds
            $textChild = Find-UiaElement -Root $Element -Condition (New-UiaCondition -ControlType 'Text')
            if ($null -ne $textChild) {
                $textBounds = $textChild.Current.BoundingRectangle
                if (-not $textBounds.IsEmpty -and $textBounds.Width -gt 0.0 -and $textBounds.Height -gt 0.0) {
                    $fallbackBounds = $textBounds
                    $fallbackSource = 'descendant-text-center'
                }
            }
            $ancestor = $Element
            $handle = [IntPtr]::Zero
            $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
            while ($null -ne $ancestor) {
                $candidateHandle = [IntPtr]$ancestor.Current.NativeWindowHandle
                if ($candidateHandle -ne [IntPtr]::Zero) {
                    $handle = $candidateHandle
                    break
                }
                $ancestor = $walker.GetParent($ancestor)
            }
            $candidatePoint = [System.Windows.Point]::new(0.0, 0.0)
            if ($Element.TryGetClickablePoint([ref]$candidatePoint)) {
                $point = $candidatePoint
                $clickablePointError = $null
                break
            }
            $clickablePointError = 'Provider returned no clickable point.'
        }
        catch {
            $clickablePointError = $_.Exception.Message
        }
        if ($refresh) { Start-Sleep -Milliseconds 100 }
    }
    $locationSource = 'clickable-point'
    if ($null -eq $point) {
        if ($null -eq $fallbackBounds -or $fallbackBounds.IsEmpty -or $fallbackBounds.Width -le 0.0 -or $fallbackBounds.Height -le 0.0) {
            throw "Element '$elementName' exposed neither a clickable point nor usable provider bounds."
        }
        # JavaFX 25 may omit a clickable point; visible descendant content is reliably inside the semantic control.
        $point = [System.Windows.Point]::new(
            $fallbackBounds.Left + ($fallbackBounds.Width / 2.0),
            $fallbackBounds.Top + ($fallbackBounds.Height / 2.0))
        $locationSource = $fallbackSource
    }
    if ($handle -eq [IntPtr]::Zero) {
        throw "Element '$elementName' has no native ancestor for foreground pointer activation."
    }
    $activated = Invoke-UiaNativePointerClick -Handle $handle -Point $point
    return [ordered]@{
        x = $point.X
        y = $point.Y
        source = $locationSource
        windowActivated = $activated
        clickablePointError = $clickablePointError
    }
}

<#
.SYNOPSIS
    Activates the provider-resolved window and emits pointer input at its captured point.
.NOTES
    Keeps native input at one boundary so provider retry tests never move the real pointer.
#>
function Invoke-UiaNativePointerClick {
    param([IntPtr]$Handle, [System.Windows.Point]$Point)
    $activated = [BS2BGWindows]::ActivateWindow($Handle)
    # High Contrast can leave a native CoverWindow above the app after its theme updates; focus alone cannot prove
    # pointer delivery. Wait for the physical hit-test owner instead of clicking through the transition overlay.
    Wait-UiaCondition -Description 'pointer point owned by the activated application window' -TimeoutSeconds 15 -Test {
        $hit = [System.Windows.Automation.AutomationElement]::FromPoint($Point)
        $hitClass = if ($null -ne $hit) { $hit.Current.ClassName } else { '<none>' }
        $hitProcess = if ($null -ne $hit) { $hit.Current.ProcessId } else { 0 }
        $ancestor = $hit
        for ($depth = 0; $depth -lt 64 -and $null -ne $ancestor; $depth++) {
            if ([IntPtr]$ancestor.Current.NativeWindowHandle -eq $Handle) { return $true }
            $ancestor = Get-UiaParent -Element $ancestor
        }
        throw "Pointer point is covered by class '$hitClass' in process $hitProcess."
    } | Out-Null
    [BS2BGWindows]::LeftClick([int][math]::Round($Point.X), [int][math]::Round($Point.Y))
    return $activated
}

<#
.SYNOPSIS
    Activates a native Win32 button that UIA exposes without an Invoke pattern, through its own window handle.
.NOTES
    The common file dialog's Open/Save split button is exposed by UIA's legacy proxy as a bare Pane with no
    patterns. It is still a Win32 button window, so it is activated with BM_CLICK posted to that window: the
    element is located by role and accessible name as usual; only the activation goes through the native message.
#>
function Invoke-UiaNativeButton {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    $handle = [IntPtr]$Element.Current.NativeWindowHandle
    if ($handle -eq [IntPtr]::Zero) {
        throw "Element '$($Element.Current.Name)' has no native window handle to activate."
    }
    if (-not [BS2BGWindows]::ClickButton($handle)) {
        throw "BM_CLICK could not be posted to '$($Element.Current.Name)' (handle $handle)."
    }
}

<#
.SYNOPSIS
    Focuses a window and sends a keyboard accelerator to it (System.Windows.Forms.SendKeys syntax, e.g. '^o').
.NOTES
    The key sequence is delivered to the focused window only after UIA confirms that keyboard focus belongs to the
    target process, so a stolen foreground cannot route the accelerator to another application. This is how a user
    triggers the command; the resulting dialog is then located by its accessible name (title).
#>
function Send-UiaAccelerator {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Window,
        [Parameter(Mandatory)] [string]$Keys,
        [int]$TimeoutSeconds = 10
    )
    Add-Type -AssemblyName System.Windows.Forms
    $processId = $Window.Current.ProcessId
    Wait-UiaCondition -Description "keyboard focus in process $processId before sending '$Keys'" -TimeoutSeconds $TimeoutSeconds -Test {
        $Window.SetFocus()
        Start-Sleep -Milliseconds 150
        $focused = [System.Windows.Automation.AutomationElement]::FocusedElement
        if ($null -ne $focused -and $focused.Current.ProcessId -eq $processId) { $true }
    } | Out-Null
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
}

<#
.SYNOPSIS
    Focuses one located control and sends keys to it without a synchronous cross-process Invoke callback.
.NOTES
    Use this for JavaFX buttons whose actions enter a modal loop (notifications and file choosers). The element is
    still located by role and accessible name, and focus is verified on that exact element before input is sent.
#>
function Send-UiaKeysToElement {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Element,
        [Parameter(Mandatory)] [string]$Keys,
        [int]$TimeoutSeconds = 10
    )
    Add-Type -AssemblyName System.Windows.Forms
    Wait-UiaCondition -Description "keyboard focus on '$($Element.Current.Name)' before sending '$Keys'" -TimeoutSeconds $TimeoutSeconds -Test {
        $Element.SetFocus()
        Start-Sleep -Milliseconds 150
        if ($Element.Current.HasKeyboardFocus) { $true }
    } | Out-Null
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
}

<#
.SYNOPSIS
    Selects a selectable item (list item, tab, table row) through its SelectionItem pattern.
#>
function Select-UiaElement {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    (Get-UiaPattern -Element $Element -PatternType 'SelectionItemPattern').Select()
}

<#
.SYNOPSIS
    Expands a menu or other expandable control through its ExpandCollapse pattern.
#>
function Expand-UiaElement {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    (Get-UiaPattern -Element $Element -PatternType 'ExpandCollapsePattern').Expand()
}

<#
.SYNOPSIS
    Sets the text of an editable control through its Value pattern.
#>
function Set-UiaValue {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element, [Parameter(Mandatory)] [AllowEmptyString()] [string]$Value)
    (Get-UiaPattern -Element $Element -PatternType 'ValuePattern').SetValue($Value)
}

<#
.SYNOPSIS
    Sets and verifies the filename text inside an already identified native file dialog.
.PARAMETER Dialog
    Owned window resolved by its exact title and process through Wait-UiaOwnedWindow.
.PARAMETER Value
    Literal filename text, including quoted absolute paths for multiple selection.
.PARAMETER TimeoutSeconds
    Bounded wait for the filename field, focus ownership, and exact text readback.
.NOTES
    The Windows proxy can expose the filename label as a Static Pane and its edit as an unnamed Pane without
    ValuePattern. Alt+N follows the dialog's label association; verify the focused native Edit belongs to this
    dialog before typing, and verify the result instead of assuming keyboard delivery succeeded.
#>
function Set-UiaFileDialogName {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Dialog,
        [Parameter(Mandatory)] [string]$Value,
        [int]$TimeoutSeconds = 30
    )
    $processId = $Dialog.Current.ProcessId
    $dialogHandle = $Dialog.Current.NativeWindowHandle
    if ($dialogHandle -eq 0) { throw 'The native file dialog has no window handle.' }
    Wait-UiaElement -Root $Dialog -Condition (New-UiaCondition -Name 'File name:') `
        -Description 'native file dialog filename label' -TimeoutSeconds $TimeoutSeconds | Out-Null
    $field = Find-UiaElement -Root $Dialog -Condition (New-UiaCondition -ControlType 'Edit' -Name 'File name:')
    $valuePattern = $null
    if ($null -ne $field -and $field.TryGetCurrentPattern(
            [System.Windows.Automation.ValuePattern]::Pattern, [ref]$valuePattern)) {
        $valuePattern.SetValue($Value)
    }
    else {
        Send-UiaKeys -ProcessId $processId -Keys '%n' -TimeoutSeconds $TimeoutSeconds
        $field = Wait-UiaCondition -Description 'filename Edit focus within the native file dialog' `
            -TimeoutSeconds $TimeoutSeconds -Test {
            $focused = [System.Windows.Automation.AutomationElement]::FocusedElement
            if ($null -eq $focused -or $focused.Current.ProcessId -ne $processId `
                    -or $focused.Current.ClassName -cne 'Edit' -or -not $focused.Current.HasKeyboardFocus) { return }
            $ancestor = $focused
            for ($depth = 0; $depth -lt 64 -and $null -ne $ancestor; $depth++) {
                if ($ancestor.Current.NativeWindowHandle -eq $dialogHandle) { return $focused }
                $ancestor = Get-UiaParent -Element $ancestor
            }
        }
        # SendKeys reserves these characters even when they occur inside an otherwise ordinary absolute path.
        $literalKeys = [regex]::Replace($Value, '[+^%~(){}\[\]]', { param($match) '{' + $match.Value + '}' })
        Send-UiaKeys -ProcessId $processId -Keys ('^a' + $literalKeys) -TimeoutSeconds $TimeoutSeconds
    }
    Wait-UiaCondition -Description 'exact native file dialog filename text' -TimeoutSeconds $TimeoutSeconds -Test {
        if ((Get-UiaText -Element $field) -ceq $Value) { $true }
    } | Out-Null
}

<#
.SYNOPSIS
    Reads editable Value text; for read-only controls prefers descendant document text, then TextPattern/Value/name.
.NOTES
    JavaFX 25 read-only TextArea can expose its accessibility label through both ValuePattern and TextPattern while
    its actual document is exposed by a descendant Text element. Editable controls still prefer ValuePattern;
    read-only controls therefore inspect the owned descendant first so packaged checks read the displayed bytes.
#>
function Get-UiaText {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    $value = $null
    $valueText = $null
    $valueReadOnly = $false
    if ($Element.TryGetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern, [ref]$value)) {
        $valueText = [string]$value.Current.Value
        $valueReadOnly = [bool]$value.Current.IsReadOnly
        if (-not $valueReadOnly -and -not [string]::IsNullOrEmpty($valueText)) { return $valueText }
    }
    if ($valueReadOnly) {
        $readOnlyText = @(Find-UiaElements -Root $Element -Condition (
            New-UiaCondition -ControlType 'Text') | ForEach-Object { [string]$_.Current.Name } |
                Where-Object { $_ })
        if ($readOnlyText.Count -gt 0) {
            return $readOnlyText -join [Environment]::NewLine
        }
    }
    if ($Element.TryGetCurrentPattern([System.Windows.Automation.TextPattern]::Pattern, [ref]$value)) {
        $documentText = [string]$value.DocumentRange.GetText(-1)
        if (-not [string]::IsNullOrEmpty($documentText)) { return $documentText }
    }
    if (-not $valueReadOnly) {
        $fallbackText = @(Find-UiaElements -Root $Element -Condition (
            New-UiaCondition -ControlType 'Text') | ForEach-Object { [string]$_.Current.Name } |
                Where-Object { $_ })
        if ($fallbackText.Count -gt 0) {
            return $fallbackText -join [Environment]::NewLine
        }
    }
    if ($null -ne $valueText) { return $valueText }
    return [string]$Element.Current.Name
}

<#
.SYNOPSIS
    Returns whether a Value-pattern text control is read-only.
.NOTES
    Generated Output remains enabled and selectable, so IsEnabled cannot establish its edit contract.
#>
function Get-UiaReadOnlyState {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    $value = $null
    if (-not $Element.TryGetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern, [ref]$value)) {
        throw "Element '$($Element.Current.Name)' does not expose ValuePattern read-only state."
    }
    return [bool]$value.Current.IsReadOnly
}

<#
.SYNOPSIS
    Waits until the exact UIA element owns keyboard focus.
.PARAMETER Element
    Semantic role/name-located element expected to receive focus.
#>
function Wait-UiaKeyboardFocus {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element, [int]$TimeoutSeconds = 10)
    Wait-UiaCondition -Description "keyboard focus on '$($Element.Current.Name)'" -TimeoutSeconds $TimeoutSeconds -Test {
        if ($Element.Current.HasKeyboardFocus) { $Element }
    }
}

<#
.SYNOPSIS
    Sends keys without resetting the focused control inside the target process.
.PARAMETER ProcessId
    Process that must already own keyboard focus.
.PARAMETER Keys
    System.Windows.Forms.SendKeys sequence.
#>
function Send-UiaKeys {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [int]$ProcessId,
        [Parameter(Mandatory)] [string]$Keys,
        [int]$TimeoutSeconds = 10
    )
    Add-Type -AssemblyName System.Windows.Forms
    Wait-UiaCondition -Description "keyboard focus in process $ProcessId before sending '$Keys'" -TimeoutSeconds $TimeoutSeconds -Test {
        $focused = [System.Windows.Automation.AutomationElement]::FocusedElement
        if ($null -ne $focused -and $focused.Current.ProcessId -eq $ProcessId) { $true }
    } | Out-Null
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
}

<#
.SYNOPSIS
    Reads the standard UIA Toggle state exposed by a toggle button.
#>
function Get-UiaToggleState {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    return (Get-UiaPattern -Element $Element -PatternType 'TogglePattern').Current.ToggleState.ToString()
}

<#
.SYNOPSIS
    Reads whether a standard UIA SelectionItem is currently selected.
#>
function Get-UiaSelectionState {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    return [bool](Get-UiaPattern -Element $Element -PatternType 'SelectionItemPattern').Current.IsSelected
}

<#
.SYNOPSIS
    Reads the standard UIA RangeValue value exposed by a keyboard-resizable control.
#>
function Get-UiaRangeValue {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    return [double](Get-UiaPattern -Element $Element -PatternType 'RangeValuePattern').Current.Value
}

<#
.SYNOPSIS
    Measures a UIA window's native DPI and physical/logical client rectangle.
#>
function Get-UiaWindowMetrics {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Window)
    $handle = [IntPtr]$Window.Current.NativeWindowHandle
    if ($handle -eq [IntPtr]::Zero) { throw "Window '$($Window.Current.Name)' has no native handle." }
    return [BS2BGWindows]::Metrics($handle)
}

<#
.SYNOPSIS
    Resizes a UIA window to a logical client size using its live DPI and non-client inset.
.PARAMETER AllowMinimumClamp
    Accepts a larger settled size when the application enforces a minimum above the request.
#>
function Resize-UiaClient {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Window,
        [Parameter(Mandatory)] [double]$LogicalWidth,
        [Parameter(Mandatory)] [double]$LogicalHeight,
        [switch]$AllowMinimumClamp,
        [int]$TimeoutSeconds = 10
    )
    $handle = [IntPtr]$Window.Current.NativeWindowHandle
    if ($handle -eq [IntPtr]::Zero) { throw "Window '$($Window.Current.Name)' has no native handle." }
    if (-not [BS2BGWindows]::ResizeClient($handle, $LogicalWidth, $LogicalHeight)) {
        throw "Could not resize '$($Window.Current.Name)' to $($LogicalWidth)x$($LogicalHeight) logical client pixels."
    }
    return Wait-UiaCondition -Description "$($LogicalWidth)x$($LogicalHeight) logical client resize" -TimeoutSeconds $TimeoutSeconds -Test {
        $metrics = [BS2BGWindows]::Metrics($handle)
        if ($AllowMinimumClamp) {
            if ($metrics.LogicalClientWidth -ge ($LogicalWidth - 1.0) -and
                    $metrics.LogicalClientHeight -ge ($LogicalHeight - 1.0)) { $metrics }
        } elseif ([math]::Abs($metrics.LogicalClientWidth - $LogicalWidth) -le 2.0 -and
                [math]::Abs($metrics.LogicalClientHeight - $LogicalHeight) -le 2.0) {
            $metrics
        }
    }
}

<#
.SYNOPSIS
    Asks a window to close through its Window pattern (the same WM_CLOSE a user's close button sends).
#>
function Close-UiaWindow {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Window)
    (Get-UiaPattern -Element $Window -PatternType 'WindowPattern').Close()
}

<#
.SYNOPSIS
    Renders the control-view tree under Element as indented "ControlType 'Name' [patterns]" lines.
.NOTES
    Used for diagnostics in the evidence and when a locator fails; it is never used to locate anything.
#>
function Get-UiaTree {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Element,
        [int]$MaxDepth = 14
    )
    $lines = New-Object System.Collections.Generic.List[string]
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    $stack = New-Object System.Collections.Generic.Stack[object]
    $stack.Push(@{ Element = $Element; Depth = 0 })
    while ($stack.Count -gt 0) {
        $item = $stack.Pop()
        $current = $item.Element
        try {
            $type = Get-UiaRoleName -Element $current
            $name = $current.Current.Name
            $patterns = @($current.GetSupportedPatterns() | ForEach-Object { $_.ProgrammaticName -replace 'PatternIdentifiers\.Pattern$', '' })
            $lines.Add(('  ' * $item.Depth) + "$type '$name'" + $(if ($patterns.Count) { " [$($patterns -join ',')]" } else { '' }))
        }
        catch {
            $lines.Add(('  ' * $item.Depth) + "<unavailable: $($_.Exception.Message)>")
            continue
        }
        if ($item.Depth -ge $MaxDepth) { continue }
        $children = @()
        $child = $walker.GetFirstChild($current)
        while ($null -ne $child) {
            $children += $child
            $child = $walker.GetNextSibling($child)
        }
        for ($index = $children.Count - 1; $index -ge 0; $index--) {
            $stack.Push(@{ Element = $children[$index]; Depth = $item.Depth + 1 })
        }
    }
    return $lines.ToArray()
}

<#
.SYNOPSIS
    Returns the parent element in the control view, or $null at the root.
#>
function Get-UiaParent {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    return [System.Windows.Automation.TreeWalker]::ControlViewWalker.GetParent($Element)
}

<#
.SYNOPSIS
    Captures High Contrast and client-area animation before a reversible packaged accessibility test.
#>
function Get-SystemAccessibilityPreferences {
    [CmdletBinding()]
    param()
    return [BS2BGSystemPreferences]::Capture()
}

<#
.SYNOPSIS
    Toggles live Windows High Contrast and broadcasts the preference change to the packaged Workbench.
#>
function Set-SystemHighContrast {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [bool]$Enabled)
    [BS2BGSystemPreferences]::SetHighContrast($Enabled)
}

<#
.SYNOPSIS
    Toggles client-area animation; JavaFX exposes its inverse as reduced motion.
#>
function Set-SystemClientAreaAnimation {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [bool]$Enabled)
    [BS2BGSystemPreferences]::SetClientAreaAnimation($Enabled)
}

<#
.SYNOPSIS
    Restores the exact accessibility preferences captured before a packaged test.
#>
function Restore-SystemAccessibilityPreferences {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [BS2BGAccessibilityState]$State)
    [BS2BGSystemPreferences]::Restore($State)
}

Export-ModuleMember -Function @(
    'New-UiaCondition',
    'Get-UiaRoleName',
    'Find-UiaElement',
    'Find-UiaElements',
    'Wait-UiaCondition',
    'Wait-UiaElement',
    'Wait-UiaWindow',
    'Wait-UiaOwnedWindow',
    'Get-ProcessTopLevelWindows',
    'Get-UiaProcessWindows',
    'Invoke-UiaElement',
    'Invoke-UiaPointerClick',
    'Invoke-UiaNativeButton',
    'Send-UiaAccelerator',
    'Send-UiaKeysToElement',
    'Select-UiaElement',
    'Expand-UiaElement',
    'Set-UiaValue',
    'Set-UiaFileDialogName',
    'Get-UiaText',
    'Get-UiaReadOnlyState',
    'Wait-UiaKeyboardFocus',
    'Send-UiaKeys',
    'Get-UiaToggleState',
    'Get-UiaSelectionState',
    'Get-UiaRangeValue',
    'Get-UiaWindowMetrics',
    'Resize-UiaClient',
    'Close-UiaWindow',
    'Get-UiaTree',
    'Get-UiaParent',
    'Get-SystemAccessibilityPreferences',
    'Set-SystemHighContrast',
    'Set-SystemClientAreaAnimation',
    'Restore-SystemAccessibilityPreferences'
)
