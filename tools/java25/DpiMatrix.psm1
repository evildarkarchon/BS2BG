#Requires -Version 7.0
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'WindowsDisplayScale.psm1') -Force

<#
.SYNOPSIS
    Atomically replaces evidence or recovery JSON so interrupted writes leave the previous record readable.
#>
function Write-DpiMatrixJson {
    param([string]$Path, [object]$Value)
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($fullPath)) | Out-Null
    $temporary = $fullPath + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
    try {
        [System.IO.File]::WriteAllText($temporary, ($Value | ConvertTo-Json -Depth 20), [System.Text.UTF8Encoding]::new($false))
        [System.IO.File]::Move($temporary, $fullPath, $true)
    }
    finally {
        if ([System.IO.File]::Exists($temporary)) { [System.IO.File]::Delete($temporary) }
    }
}

<#
.SYNOPSIS
    Locks the interactive session and recovery record until the caller disposes the returned lease.
#>
function Open-DpiMatrixLock {
    param([string]$RecoveryPath)
    $path = [System.IO.Path]::GetFullPath($RecoveryPath) + '.lock'
    # Settings is shared by every worktree and recovery path in this Windows session. A path-only lock would
    # allow two runners to alternate scale changes underneath each other's smoke processes.
    $mutex = [System.Threading.Mutex]::new($false, 'Local\BS2BG.DisplayScale.Session')
    $acquired = $false
    try {
        try { $acquired = $mutex.WaitOne(0) }
        catch [System.Threading.AbandonedMutexException] {
            # Windows transfers ownership after a crash; the pending recovery guard must still run afterwards.
            $acquired = $true
        }
        if (-not $acquired) { throw 'Another display-scale operation owns this interactive session.' }
        [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($path)) | Out-Null
        try {
            $fileLock = [System.IO.File]::Open($path, [System.IO.FileMode]::OpenOrCreate,
                [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
        }
        catch { throw "Another display-scale operation owns the recovery lock '$path': $($_.Exception.Message)" }
        $lease = [pscustomobject]@{ FileLock = $fileLock; Mutex = $mutex }
        $lease | Add-Member ScriptMethod Dispose {
            try { $this.FileLock.Dispose() }
            finally {
                try { $this.Mutex.ReleaseMutex() }
                finally { $this.Mutex.Dispose() }
            }
        }
        return $lease
    }
    catch {
        try { if ($acquired) { $mutex.ReleaseMutex() } }
        finally { $mutex.Dispose() }
        throw
    }
}

<#
.SYNOPSIS
    Validates the durable recovery location and refuses unresolved records while the caller holds its lock.
#>
function Assert-DpiMatrixRecoveryRecord {
    param([string]$RecoveryPath)
    $fullPath = [System.IO.Path]::GetFullPath($RecoveryPath)
    $targetRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../target')).TrimEnd('\', '/')
    if ($fullPath.Equals($targetRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $fullPath.StartsWith($targetRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Display-scale RecoveryPath must be outside target/ so a clean build cannot erase crash recovery.'
    }
    if (Test-Path -LiteralPath $RecoveryPath) {
        $existing = Get-Content -LiteralPath $RecoveryPath -Raw | ConvertFrom-Json
        if ($existing.schema -ne 'bs2bg.dpi-matrix-recovery/1' -or $existing.status -ne 'restored') {
            throw "Unresolved display-scale recovery record '$RecoveryPath'; restore it before starting another matrix."
        }
    }
}

<#
.SYNOPSIS
    Rejects pending recovery or an active matrix before a packaging caller starts its expensive build.
.NOTES
    This preflight releases its lock immediately. Invoke-DpiMatrix checks again under its lifetime lock to close
    the race with another runner starting while packaging is in progress.
#>
function Assert-DpiMatrixRecoveryAvailable {
    param([Parameter(Mandatory)] [string]$RecoveryPath)
    $lock = Open-DpiMatrixLock -RecoveryPath $RecoveryPath
    try { Assert-DpiMatrixRecoveryRecord -RecoveryPath $RecoveryPath }
    finally { $lock.Dispose() }
}

<#
.SYNOPSIS
    Runs the existing packaged smoke script in a fresh PowerShell process and returns its exit code.
.NOTES
    Child-process isolation contains smoke's exit statements and recreates UI Automation state after each scale change.
#>
function Invoke-DpiSmokeRun {
    param([string]$ArchivePath, [string]$FixtureProject, [string]$FixtureRecoveryProject,
        [string]$FixtureMalformedProject, [string]$ExpectedAppVersion, [string]$EvidencePath,
        [int]$ExpectedDpiPercent)
    & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'smoke-app-image.ps1') `
        -ArchivePath $ArchivePath -FixtureProject $FixtureProject -FixtureRecoveryProject $FixtureRecoveryProject `
        -FixtureMalformedProject $FixtureMalformedProject -ExpectedAppVersion $ExpectedAppVersion `
        -EvidencePath $EvidencePath -ExpectedDpiPercent $ExpectedDpiPercent | Out-Host
    return $LASTEXITCODE
}

<#
.SYNOPSIS
    Runs one immutable packaged archive at requested supported scales, restoring the captured primary display afterwards.
.DESCRIPTION
    Returns aggregate evidence on success. Failures throw after recording workflow and restoration errors independently.
    RecoveryPath must be durable outside generated build output; a pending record is never overwritten by a new run.
.PARAMETER ScalePercents
    Ordered display-scale cases, defaulting to 100, 125, and 150. Every requested value must be offered by the display.
#>
function Invoke-DpiMatrix {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$ArchivePath,
        [Parameter(Mandatory)] [string]$FixtureProject,
        [Parameter(Mandatory)] [string]$FixtureRecoveryProject,
        [Parameter(Mandatory)] [string]$FixtureMalformedProject,
        [Parameter(Mandatory)] [string]$ExpectedAppVersion,
        [Parameter(Mandatory)] [string]$EvidencePath,
        [Parameter(Mandatory)] [string]$RecoveryPath,
        [int[]]$ScalePercents = @(100,125,150)
    )
    if ($null -eq $ScalePercents -or $ScalePercents.Count -eq 0 -or
        @($ScalePercents | Select-Object -Unique).Count -ne $ScalePercents.Count) {
        throw 'ScalePercents must contain at least one scale and no duplicate values.'
    }
    $lock = Open-DpiMatrixLock -RecoveryPath $RecoveryPath
    try {
        Assert-DpiMatrixRecoveryRecord -RecoveryPath $RecoveryPath
        $ArchivePath = (Resolve-Path -LiteralPath $ArchivePath).Path
        $hash = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $display = Get-PrimaryDisplayScale
        foreach ($scale in $ScalePercents) {
            if ($scale -notin $display.supportedScales) { throw "Primary display does not support required scale $scale%." }
        }
        $recovery = [ordered]@{
            schema = 'bs2bg.dpi-matrix-recovery/1'; status = 'pending'
            machine = [Environment]::MachineName; user = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
            ownerPid = $PID; ownerStartedAtUtc = (Get-Process -Id $PID).StartTime.ToUniversalTime().ToString('o')
            recordedAtUtc = [DateTimeOffset]::UtcNow.ToString('o'); display = $display
        }
        # Persist original state before any mutation; a killed runner cannot execute finally.
        Write-DpiMatrixJson -Path $RecoveryPath -Value $recovery
        $result = [ordered]@{
            schema = 'bs2bg.dpi-matrix/1'; recordedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
            passed = $false; archive = $ArchivePath; archiveSha256 = $hash; originalDisplay = $display
            requestedScalePercents = $ScalePercents
            recoveryPath = [System.IO.Path]::GetFullPath($RecoveryPath)
            runs = [System.Collections.Generic.List[object]]::new()
            restoration = $null; workflowError = $null; restorationError = $null
        }
        try {
            foreach ($scale in $ScalePercents) {
                $run = [ordered]@{ scalePercent = $scale; passed = $false; archiveSha256 = $hash
                    evidencePath = Join-Path (Split-Path -Parent ([System.IO.Path]::GetFullPath($EvidencePath))) "dpi-$scale/windows-app-image-smoke.json"
                    display = $null; error = $null }
                $result.runs.Add($run)
                try {
                    if ((Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
                        throw 'Packaged archive changed during the display-scale matrix.'
                    }
                    $run.display = Set-PrimaryDisplayScale -Display $display -ScalePercent $scale
                    if ($run.display.scalePercent -ne $scale) {
                        throw "Display provider selected scale $($run.display.scalePercent)% instead of $scale%."
                    }
                    $smokeArguments = @{
                        ArchivePath = $ArchivePath; FixtureProject = $FixtureProject; FixtureRecoveryProject = $FixtureRecoveryProject
                        FixtureMalformedProject = $FixtureMalformedProject; ExpectedAppVersion = $ExpectedAppVersion
                        EvidencePath = $run.evidencePath; ExpectedDpiPercent = $scale
                    }
                    $exitCode = Invoke-DpiSmokeRun @smokeArguments
                    if ($exitCode -ne 0) { throw "Packaged smoke failed at $scale% with exit code $exitCode." }
                    $smoke = Get-Content -LiteralPath $run.evidencePath -Raw | ConvertFrom-Json
                    if (-not $smoke.passed -or $smoke.process.exitCode -ne 0) { throw "Packaged smoke did not pass at $scale%." }
                    if ($smoke.observations.archiveSha256 -ne $hash -or
                        (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
                        throw 'Packaged smoke archive hash does not match the matrix archive.'
                    }
                    if ($smoke.observations.responsiveLayout.initial.dpi -ne $run.display.dpi) {
                        throw "Packaged window DPI does not match $scale%."
                    }
                    if ($smoke.timeouts.expectedDpiPercent -ne $scale) {
                        throw "Packaged smoke requested scale does not match $scale%."
                    }
                    $run.passed = $true
                    Write-DpiMatrixJson -Path $EvidencePath -Value $result
                }
                catch { $run.error = $_.Exception.Message; throw }
            }
        }
        catch { $result.workflowError = $_.Exception.Message }
        finally {
            try {
                $result.restoration = Set-PrimaryDisplayScale -Display $display -ScalePercent $display.scalePercent
                if ($result.restoration.scalePercent -ne $display.scalePercent -or $result.restoration.dpi -ne $display.dpi) {
                    throw 'Display restoration did not report the original scale and native DPI.'
                }
                $recovery.status = 'restored'
                $recovery['restoredAtUtc'] = [DateTimeOffset]::UtcNow.ToString('o')
                Write-DpiMatrixJson -Path $RecoveryPath -Value $recovery
            }
            catch { $result.restorationError = $_.Exception.Message }
            $result.passed = -not $result.workflowError -and -not $result.restorationError
            Write-DpiMatrixJson -Path $EvidencePath -Value $result
        }
        if (-not $result.passed) {
            throw "Display-scale matrix failed. Workflow: $($result.workflowError) Restoration: $($result.restorationError). Evidence: $EvidencePath"
        }
        return [pscustomobject]$result
    }
    finally { $lock.Dispose() }
}

<#
.SYNOPSIS
    Restores the display from an interrupted matrix owned by this account and machine.
.DESCRIPTION
    Refuses active owners and foreign or unrecognized recovery records. A completed record is returned unchanged
    without touching the display, so rerunning recovery cannot undo a later intentional user scale change.
#>
function Restore-DpiMatrixDisplay {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$RecoveryPath)
    $lock = Open-DpiMatrixLock -RecoveryPath $RecoveryPath
    try {
        $record = Get-Content -LiteralPath $RecoveryPath -Raw | ConvertFrom-Json
        if ($record.schema -ne 'bs2bg.dpi-matrix-recovery/1' -or $record.status -notin @('pending','restored')) {
            throw 'Unrecognized display-scale recovery record.'
        }
        if ($record.machine -ne [Environment]::MachineName -or
            $record.user -ne [System.Security.Principal.WindowsIdentity]::GetCurrent().Name) {
            throw 'Display-scale recovery belongs to a different machine or account.'
        }
        if ($record.status -eq 'restored') { return $record }
        $owner = Get-Process -Id $record.ownerPid -ErrorAction SilentlyContinue
        # A PID can be reused after a crash; start time distinguishes that unrelated process from the owner.
        if ($owner -and $owner.StartTime.ToUniversalTime().Ticks -eq ([DateTimeOffset]$record.ownerStartedAtUtc).UtcTicks) {
            throw "Display-scale recovery owner process $($record.ownerPid) is still active."
        }
        $restored = Set-PrimaryDisplayScale -Display $record.display -ScalePercent $record.display.scalePercent
        if ($restored.scalePercent -ne $record.display.scalePercent -or $restored.dpi -ne $record.display.dpi) {
            throw 'Display recovery did not report the original scale and native DPI.'
        }
        $record.status = 'restored'
        $record | Add-Member -NotePropertyName restoredAtUtc -NotePropertyValue ([DateTimeOffset]::UtcNow.ToString('o')) -Force
        Write-DpiMatrixJson -Path $RecoveryPath -Value $record
        return $record
    }
    finally { $lock.Dispose() }
}

Export-ModuleMember -Function Invoke-DpiMatrix, Restore-DpiMatrixDisplay, Assert-DpiMatrixRecoveryAvailable
