#Requires -Version 5.1
<#
.SYNOPSIS
    Thin Windows UI Automation (UIA) helpers for driving the packaged BS2BG launcher from outside the process.

.DESCRIPTION
    Wraps System.Windows.Automation so tools/java25/smoke-app-image.ps1 can locate JavaFX controls the way an
    assistive technology does: by accessible control type (role), accessible name, and tree relationships. Nothing
    here uses coordinates, automation ids, CSS, or JavaFX internals; JavaFX exposes its scene graph to UIA through
    its public accessibility support, so these helpers only ever see roles, names, and standard UIA patterns.

    Every wait is bounded and every failure names what was being looked for, so a hung toolkit or a missing
    control fails the smoke run with a diagnosis instead of hanging it.
#>

Set-StrictMode -Version Latest

Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes

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

public static class BS2BGWindows
{
    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
    [DllImport("user32.dll", SetLastError = true)] private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowTextW(IntPtr hWnd, StringBuilder lpString, int nMaxCount);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetClassNameW(IntPtr hWnd, StringBuilder lpString, int nMaxCount);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool PostMessageW(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    private const uint BM_CLICK = 0x00F5;

    // Standard Win32 button activation: the same message the control receives from a mouse click, delivered to
    // the button window itself, so no screen position is involved.
    public static bool ClickButton(IntPtr hWnd)
    {
        return PostMessageW(hWnd, BM_CLICK, IntPtr.Zero, IntPtr.Zero);
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
    $condition = New-UiaCondition -ControlType 'Window' -Name $Title -ProcessId $ProcessId
    return Wait-UiaElement -Root ([System.Windows.Automation.AutomationElement]::RootElement) -Condition $condition -Scope 'Children' -Description "window '$Title' of process $ProcessId" -TimeoutSeconds $TimeoutSeconds
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
    Reads the text of a control: its Value pattern, else its Text pattern's document range, else its accessible name.
#>
function Get-UiaText {
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Element)
    $value = $null
    if ($Element.TryGetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern, [ref]$value)) {
        return [string]$value.Current.Value
    }
    if ($Element.TryGetCurrentPattern([System.Windows.Automation.TextPattern]::Pattern, [ref]$value)) {
        return [string]$value.DocumentRange.GetText(-1)
    }
    return [string]$Element.Current.Name
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
    'Invoke-UiaNativeButton',
    'Send-UiaAccelerator',
    'Send-UiaKeysToElement',
    'Select-UiaElement',
    'Expand-UiaElement',
    'Set-UiaValue',
    'Get-UiaText',
    'Close-UiaWindow',
    'Get-UiaTree',
    'Get-UiaParent'
)
