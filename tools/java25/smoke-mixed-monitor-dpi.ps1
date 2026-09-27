#Requires -Version 7.0
<#
.SYNOPSIS
    Audits one packaged Workbench image while its window moves across a 100% secondary and 150%/125% primary.
.DESCRIPTION
    Requires two active monitors and an idle interactive desktop. The secondary must already be 100%; only the
    primary scale changes through the guarded Windows Settings adapter. The captured original primary scale is
    restored on success or failure. Use -RestoreOnly after an interrupted run before starting another audit.
.PARAMETER ArchivePath
    Already built app-image ZIP. This command does not build or modify it.
.PARAMETER EvidencePath
    Aggregate JSON destination. Per-landing UIA trees, screenshots, and launcher output go in sibling directories.
.PARAMETER RecoveryPath
    Durable display-scale recovery record outside target/, shared with smoke-dpi-matrix.ps1.
.PARAMETER WorkRoot
    New temporary extraction/profile directory. An existing directory is refused.
.PARAMETER KeepWorkRoot
    Retains the isolated extracted image and profiles for failure inspection.
.EXAMPLE
    .\tools\java25\smoke-mixed-monitor-dpi.ps1 -ArchivePath target\BS2BG-1.1.2-windows-x64.zip -EvidencePath target\reproducibility\mixed-monitor\mixed-monitor.json
.EXAMPLE
    .\tools\java25\smoke-mixed-monitor-dpi.ps1 -RestoreOnly
#>
[CmdletBinding(DefaultParameterSetName = 'Run')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Run')] [string]$ArchivePath,
    [Parameter(Mandatory, ParameterSetName = 'Run')] [string]$EvidencePath,
    [Parameter(Mandatory, ParameterSetName = 'Restore')] [switch]$RestoreOnly,
    [Parameter(ParameterSetName = 'Run')] [string]$WorkRoot = (Join-Path $env:TEMP ('BS2BG-mixed-monitor-' + [guid]::NewGuid().ToString('N'))),
    [Parameter(ParameterSetName = 'Run')] [string]$LauncherName = 'BS2BG',
    [Parameter(ParameterSetName = 'Run')] [string]$ExpectedAppVersion = '',
    [Parameter(ParameterSetName = 'Run')] [int]$StartupTimeoutSeconds = 90,
    [Parameter(ParameterSetName = 'Run')] [int]$StepTimeoutSeconds = 30,
    [Parameter(ParameterSetName = 'Run')] [int]$ExitTimeoutSeconds = 30,
    [Parameter(ParameterSetName = 'Run')] [switch]$KeepWorkRoot,
    [string]$RecoveryPath = (Join-Path $PSScriptRoot '../../.bs2bg-dpi-matrix-recovery.json')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try {
    if ($RestoreOnly) {
        Import-Module (Join-Path $PSScriptRoot 'DpiMatrix.psm1')
        $record = Restore-DpiMatrixDisplay -RecoveryPath $RecoveryPath
        Write-Host "Display-scale recovery: $($record.status). Record: $RecoveryPath"
    }
    else {
        Import-Module (Join-Path $PSScriptRoot 'MixedMonitorDpi.psm1')
        $result = Invoke-MixedMonitorDpiAudit -ArchivePath $ArchivePath -EvidencePath $EvidencePath `
            -RecoveryPath $RecoveryPath -WorkRoot $WorkRoot -LauncherName $LauncherName `
            -ExpectedAppVersion $ExpectedAppVersion -StartupTimeoutSeconds $StartupTimeoutSeconds `
            -StepTimeoutSeconds $StepTimeoutSeconds -ExitTimeoutSeconds $ExitTimeoutSeconds `
            -KeepWorkRoot:$KeepWorkRoot
        Write-Host "Mixed-monitor DPI audit passed at primary 150% and 125% against secondary 100%; original $($result.originalDisplay.scalePercent)% restored. Evidence: $EvidencePath"
    }
    exit 0
}
catch {
    Write-Host "MIXED-MONITOR DPI AUDIT FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
