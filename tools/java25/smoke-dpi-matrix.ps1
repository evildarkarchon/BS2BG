#Requires -Version 7.0
<#
.SYNOPSIS
    Tests one packaged archive at requested display scales or restores an interrupted matrix's display state.
.DESCRIPTION
    The matrix changes only the primary display through Windows Settings UI Automation. Each scale launches a
    fresh packaged smoke process and retains separate evidence. The original scale is restored on success or
    failure. Keep the interactive desktop idle throughout the run.
.PARAMETER ArchivePath
    Already built archive to exercise unchanged at every scale. This command does not run the application build gate.
.PARAMETER FixtureProject
    Representative Project fixture passed to each packaged smoke workflow.
.PARAMETER FixtureRecoveryProject
    Recoverable Project fixture passed to each packaged smoke workflow.
.PARAMETER FixtureMalformedProject
    Malformed Project fixture passed to each packaged smoke workflow.
.PARAMETER ExpectedAppVersion
    Version stamped into the packaged archive.
.PARAMETER EvidencePath
    Aggregate JSON path. Each scale's smoke evidence is retained in a sibling dpi-<percent> directory.
.PARAMETER RecoveryPath
    Durable crash-recovery record outside target/. A pending record must be restored before another run.
.PARAMETER RestoreOnly
    Restore a pending record without building or launching BS2BG. Completed records do not change the display again.
.PARAMETER ScalePercents
    Ordered matrix cases, defaulting to 100, 125, and 150. Each must be supported by the current primary display.
.EXAMPLE
    .\tools\java25\smoke-dpi-matrix.ps1 -RestoreOnly
#>
[CmdletBinding(DefaultParameterSetName = 'Run')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Run')] [string]$ArchivePath,
    [Parameter(Mandatory, ParameterSetName = 'Run')] [string]$FixtureProject,
    [Parameter(Mandatory, ParameterSetName = 'Run')] [string]$FixtureRecoveryProject,
    [Parameter(Mandatory, ParameterSetName = 'Run')] [string]$FixtureMalformedProject,
    [Parameter(Mandatory, ParameterSetName = 'Run')] [string]$ExpectedAppVersion,
    [Parameter(Mandatory, ParameterSetName = 'Run')] [string]$EvidencePath,
    [Parameter(Mandatory, ParameterSetName = 'Restore')] [switch]$RestoreOnly,
    [Parameter(ParameterSetName = 'Run')] [int[]]$ScalePercents = @(100,125,150),
    [string]$RecoveryPath = (Join-Path $PSScriptRoot '../../.bs2bg-dpi-matrix-recovery.json')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try {
    Import-Module (Join-Path $PSScriptRoot 'DpiMatrix.psm1') -Force
    if ($RestoreOnly) {
        $record = Restore-DpiMatrixDisplay -RecoveryPath $RecoveryPath
        Write-Host "Display-scale recovery: $($record.status). Record: $RecoveryPath"
    }
    else {
        $arguments = @{
            ArchivePath = $ArchivePath; FixtureProject = $FixtureProject; FixtureRecoveryProject = $FixtureRecoveryProject
            FixtureMalformedProject = $FixtureMalformedProject; ExpectedAppVersion = $ExpectedAppVersion
            EvidencePath = $EvidencePath; RecoveryPath = $RecoveryPath; ScalePercents = $ScalePercents
        }
        $result = Invoke-DpiMatrix @arguments
        Write-Host "Display-scale matrix passed at $($result.runs.scalePercent -join ', ')%; original scale restored. Evidence: $EvidencePath"
    }
    exit 0
}
catch {
    Write-Host "DISPLAY-SCALE RUN FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
