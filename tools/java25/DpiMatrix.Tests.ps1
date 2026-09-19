#Requires -Modules @{ ModuleName = 'Pester'; ModuleVersion = '5.0.0' }

BeforeAll {
    Import-Module (Join-Path $PSScriptRoot 'DpiMatrix.psm1') -Force
}

Describe 'Packaged display-scale matrix lifecycle' {
    BeforeEach {
        $caseRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $caseRoot | Out-Null
        $archive = Join-Path $caseRoot 'application.zip'
        Set-Content -LiteralPath $archive -Value 'one immutable package'
        $arguments = @{
            ArchivePath = $archive
            FixtureProject = 'project.jbs2bg'
            FixtureRecoveryProject = 'recovery.jbs2bg'
            FixtureMalformedProject = 'malformed.jbs2bg'
            ExpectedAppVersion = '1.0'
            EvidencePath = Join-Path $caseRoot 'evidence/matrix.json'
            RecoveryPath = Join-Path $caseRoot 'recovery.json'
        }
        InModuleScope DpiMatrix {
            $script:events = [System.Collections.Generic.List[string]]::new()
            Mock Get-PrimaryDisplayScale {
                [pscustomobject]@{ deviceName = 'DISPLAY1'; monitorDevicePath = 'monitor-a';
                    scalePercent = 125; dpi = 120; supportedScales = @(100,125,150); settingsDisplayName = 'Display 1' }
            }
            Mock Set-PrimaryDisplayScale {
                param($Display, $ScalePercent)
                $script:events.Add("scale:$ScalePercent")
                [pscustomobject]@{ deviceName = $Display.deviceName; monitorDevicePath = $Display.monitorDevicePath;
                    scalePercent = $ScalePercent; dpi = $ScalePercent * 96 / 100 }
            }
            Mock Invoke-DpiSmokeRun {
                param($ArchivePath, $EvidencePath, $ExpectedDpiPercent)
                $script:events.Add("smoke:$ExpectedDpiPercent")
                New-Item -ItemType Directory -Path (Split-Path $EvidencePath) -Force | Out-Null
                @{
                    passed = $true; process = @{ exitCode = 0 }; steps = @(@{ passed = $true })
                    timeouts = @{ expectedDpiPercent = $ExpectedDpiPercent }
                    observations = @{
                        archiveSha256 = (Get-FileHash $ArchivePath).Hash.ToLowerInvariant()
                        responsiveLayout = @{ initial = @{ dpi = $ExpectedDpiPercent * 96 / 100 } }
                    }
                } | ConvertTo-Json -Depth 8 | Set-Content $EvidencePath
                return 0
            }
        }
    }

    It 'tests the same archive once at every required scale and restores the original scale' {
        $result = Invoke-DpiMatrix @arguments
        $result.passed | Should -BeTrue
        $result.runs.Count | Should -Be 3
        @($result.runs.scalePercent) -join ',' | Should -Be '100,125,150'
        @($result.runs.evidencePath | Select-Object -Unique).Count | Should -Be 3
        @($result.runs.archiveSha256 | Select-Object -Unique).Count | Should -Be 1
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'restored'
        InModuleScope DpiMatrix {
            $script:events -join ',' | Should -Be 'scale:100,smoke:100,scale:125,smoke:125,scale:150,smoke:150,scale:125'
        }
    }

    It 'restores a pending interrupted run once and leaves completed recovery records harmless' {
        Invoke-DpiMatrix @arguments | Out-Null
        $record = Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json
        $record.status = 'pending'
        $record.ownerPid = [int]::MaxValue
        $record | ConvertTo-Json -Depth 8 | Set-Content $arguments.RecoveryPath
        $restored = Restore-DpiMatrixDisplay -RecoveryPath $arguments.RecoveryPath
        $restored.status | Should -Be 'restored'
        Restore-DpiMatrixDisplay -RecoveryPath $arguments.RecoveryPath | Out-Null
        InModuleScope DpiMatrix {
            Should -Invoke Set-PrimaryDisplayScale -Times 5 -Exactly
        }
    }

    It 'stops after a failed smoke and restores before reporting failure' {
        InModuleScope DpiMatrix {
            Mock Invoke-DpiSmokeRun { return 9 } -ParameterFilter { $ExpectedDpiPercent -eq 125 }
        }
        { Invoke-DpiMatrix @arguments } | Should -Throw '*exit code 9*'
        $evidence = Get-Content $arguments.EvidencePath -Raw | ConvertFrom-Json
        $evidence.passed | Should -BeFalse
        $evidence.runs.Count | Should -Be 2
        $evidence.restoration.scalePercent | Should -Be 125
        InModuleScope DpiMatrix {
            Should -Invoke Invoke-DpiSmokeRun -Times 0 -Exactly -ParameterFilter { $ExpectedDpiPercent -eq 150 }
        }
    }

    It 'retains both workflow and restoration failures and preserves pending recovery' {
        InModuleScope DpiMatrix {
            Mock Set-PrimaryDisplayScale { throw 'transition failed' } -ParameterFilter { $ScalePercent -eq 100 }
            Mock Set-PrimaryDisplayScale { throw 'restoration failed' } -ParameterFilter { $ScalePercent -eq 125 }
        }
        { Invoke-DpiMatrix @arguments } | Should -Throw '*transition failed*restoration failed*'
        $evidence = Get-Content $arguments.EvidencePath -Raw | ConvertFrom-Json
        $evidence.workflowError | Should -Be 'transition failed'
        $evidence.restorationError | Should -Be 'restoration failed'
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'pending'
        InModuleScope DpiMatrix { Should -Invoke Invoke-DpiSmokeRun -Times 0 -Exactly }
    }

    It 'refuses an unresolved recovery record without overwriting it or touching the display' {
        $pending = '{"schema":"bs2bg.dpi-matrix-recovery/1","status":"pending"}'
        Set-Content $arguments.RecoveryPath $pending -NoNewline
        { Invoke-DpiMatrix @arguments } | Should -Throw '*Unresolved*'
        Get-Content $arguments.RecoveryPath -Raw | Should -BeExactly $pending
        InModuleScope DpiMatrix { Should -Invoke Get-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'rejects a changed package and restores the display' {
        InModuleScope DpiMatrix {
            Mock Invoke-DpiSmokeRun {
                param($ArchivePath, $EvidencePath, $ExpectedDpiPercent)
                Set-Content $ArchivePath 'changed package'
                New-Item -ItemType Directory -Path (Split-Path $EvidencePath) -Force | Out-Null
                @{ passed = $true; process = @{ exitCode = 0 }; observations = @{ archiveSha256 = 'untrusted' } } |
                    ConvertTo-Json -Depth 5 | Set-Content $EvidencePath
                return 0
            }
        }
        { Invoke-DpiMatrix @arguments } | Should -Throw '*archive hash*'
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'restored'
    }

    It 'rejects successful smoke evidence whose actual window DPI differs' {
        InModuleScope DpiMatrix {
            Mock Invoke-DpiSmokeRun {
                param($ArchivePath, $EvidencePath, $ExpectedDpiPercent)
                New-Item -ItemType Directory -Path (Split-Path $EvidencePath) -Force | Out-Null
                @{ passed = $true; process = @{ exitCode = 0 }; timeouts = @{ expectedDpiPercent = $ExpectedDpiPercent }; observations = @{
                    archiveSha256 = (Get-FileHash $ArchivePath).Hash.ToLowerInvariant()
                    responsiveLayout = @{ initial = @{ dpi = 144 } }
                } } | ConvertTo-Json -Depth 6 | Set-Content $EvidencePath
                return 0
            }
        }
        { Invoke-DpiMatrix @arguments } | Should -Throw '*window DPI*'
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'restored'
    }

    It 'refuses recovery of a live owner or a record belonging to another machine' {
        Invoke-DpiMatrix @arguments | Out-Null
        $record = Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json
        $record.status = 'pending'
        $record | ConvertTo-Json -Depth 8 | Set-Content $arguments.RecoveryPath
        { Restore-DpiMatrixDisplay -RecoveryPath $arguments.RecoveryPath } | Should -Throw '*still active*'
        $record.machine = 'another-machine'
        $record | ConvertTo-Json -Depth 8 | Set-Content $arguments.RecoveryPath
        { Restore-DpiMatrixDisplay -RecoveryPath $arguments.RecoveryPath } | Should -Throw '*different machine or account*'
        InModuleScope DpiMatrix { Should -Invoke Set-PrimaryDisplayScale -Times 4 -Exactly }
    }

    It 'rejects concurrent ownership before accessing display settings' {
        $stream = [System.IO.File]::Open($arguments.RecoveryPath + '.lock', 'OpenOrCreate', 'ReadWrite', 'None')
        try { { Invoke-DpiMatrix @arguments } | Should -Throw '*recovery lock*' }
        finally { $stream.Dispose() }
        InModuleScope DpiMatrix { Should -Invoke Get-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'fails the matrix when only final display restoration fails' {
        InModuleScope DpiMatrix {
            Mock Set-PrimaryDisplayScale {
                param($Display, $ScalePercent)
                if ($script:events.Contains('smoke:150')) { throw 'final restoration failed' }
                [pscustomobject]@{ scalePercent = $ScalePercent; dpi = $ScalePercent * 96 / 100 }
            }
        }
        { Invoke-DpiMatrix @arguments } | Should -Throw '*final restoration failed*'
        $evidence = Get-Content $arguments.EvidencePath -Raw | ConvertFrom-Json
        @($evidence.runs | Where-Object passed).Count | Should -Be 3
        $evidence.passed | Should -BeFalse
        $evidence.workflowError | Should -BeNullOrEmpty
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'pending'
    }

    It 'checks required scale support before creating recovery state or changing the display' {
        InModuleScope DpiMatrix {
            Mock Get-PrimaryDisplayScale { [pscustomobject]@{ supportedScales = @(100,125) } }
        }
        { Invoke-DpiMatrix @arguments } | Should -Throw '*does not support required scale 150*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope DpiMatrix { Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'retains pending recovery if explicit recovery cannot restore the original monitor' {
        Invoke-DpiMatrix @arguments | Out-Null
        $record = Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json
        $record.status = 'pending'
        $record.ownerPid = [int]::MaxValue
        $record | ConvertTo-Json -Depth 8 | Set-Content $arguments.RecoveryPath
        InModuleScope DpiMatrix { Mock Set-PrimaryDisplayScale { throw 'Original monitor unavailable' } }
        { Restore-DpiMatrixDisplay -RecoveryPath $arguments.RecoveryPath } | Should -Throw '*Original monitor unavailable*'
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'pending'
    }

    It 'runs requested supported scales and restores an original scale outside the default matrix' {
        InModuleScope DpiMatrix {
            Mock Get-PrimaryDisplayScale {
                [pscustomobject]@{ deviceName = 'DISPLAY1'; monitorDevicePath = 'monitor-a';
                    scalePercent = 200; dpi = 192; supportedScales = @(100,125,150,175,200); settingsDisplayName = 'Display 1' }
            }
            Mock Set-PrimaryDisplayScale {
                param($Display, $ScalePercent)
                $script:events.Add("scale:$ScalePercent")
                $nativeDpi = @{ 125 = 120; 175 = 168; 200 = 192 }
                [pscustomobject]@{ scalePercent = $ScalePercent; dpi = $nativeDpi[$ScalePercent] }
            }
            Mock Invoke-DpiSmokeRun {
                param($ArchivePath, $EvidencePath, $ExpectedDpiPercent)
                $script:events.Add("smoke:$ExpectedDpiPercent")
                $nativeDpi = @{ 125 = 120; 175 = 168 }
                New-Item -ItemType Directory -Path (Split-Path $EvidencePath) -Force | Out-Null
                @{ passed = $true; process = @{ exitCode = 0 }; timeouts = @{ expectedDpiPercent = $ExpectedDpiPercent }; observations = @{
                    archiveSha256 = (Get-FileHash $ArchivePath).Hash.ToLowerInvariant()
                    responsiveLayout = @{ initial = @{ dpi = $nativeDpi[$ExpectedDpiPercent] } }
                } } | ConvertTo-Json -Depth 6 | Set-Content $EvidencePath
                return 0
            }
        }
        $result = Invoke-DpiMatrix @arguments -ScalePercents @(125,175)
        $result.passed | Should -BeTrue
        $result.runs.Count | Should -Be 2
        $result.restoration.scalePercent | Should -Be 200
        InModuleScope DpiMatrix {
            $script:events -join ',' | Should -Be 'scale:125,smoke:125,scale:175,smoke:175,scale:200'
        }
    }

    It 'rejects empty or repeated scale cases before accessing the display' {
        { Invoke-DpiMatrix @arguments -ScalePercents @() } | Should -Throw '*at least one scale and no duplicate*'
        { Invoke-DpiMatrix @arguments -ScalePercents @(125,125) } | Should -Throw '*at least one scale and no duplicate*'
        InModuleScope DpiMatrix { Should -Invoke Get-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'prevents another process from controlling the same desktop through a different recovery path' {
        $ready = Join-Path $caseRoot 'owner-ready'
        $release = Join-Path $caseRoot 'owner-release'
        $worker = Join-Path $caseRoot 'owner.ps1'
        @'
param($ModulePath, $Archive, $Ready, $Release, $Recovery, $Evidence)
Import-Module $ModulePath -Force
& (Get-Module DpiMatrix) {
    param($Ready, $Release)
    $script:readyMarker = $Ready
    $script:releaseMarker = $Release
    function script:Get-PrimaryDisplayScale {
        Set-Content $script:readyMarker 'owned'
        while (-not (Test-Path $script:releaseMarker)) { Start-Sleep -Milliseconds 50 }
        throw 'Test owner released without changing the display.'
    }
} $Ready $Release
try {
    Invoke-DpiMatrix -ArchivePath $Archive -FixtureProject unused -FixtureRecoveryProject unused `
        -FixtureMalformedProject unused -ExpectedAppVersion test -EvidencePath $Evidence -RecoveryPath $Recovery
} catch { exit 0 }
'@ | Set-Content $worker
        $start = [Diagnostics.ProcessStartInfo]::new((Join-Path $PSHOME 'pwsh.exe'))
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        foreach ($argument in @('-NoProfile', '-NonInteractive', '-File', $worker,
            (Join-Path $PSScriptRoot 'DpiMatrix.psm1'), $archive, $ready, $release,
            (Join-Path $caseRoot 'other-recovery.json'), (Join-Path $caseRoot 'other-evidence.json'))) {
            $start.ArgumentList.Add($argument)
        }
        $owner = [Diagnostics.Process]::Start($start)
        try {
            $deadline = [DateTime]::UtcNow.AddSeconds(15)
            while (-not (Test-Path $ready) -and -not $owner.HasExited -and [DateTime]::UtcNow -lt $deadline) {
                Start-Sleep -Milliseconds 50
            }
            Test-Path $ready | Should -BeTrue
            { Invoke-DpiMatrix @arguments } | Should -Throw '*interactive session*'
            InModuleScope DpiMatrix { Should -Invoke Get-PrimaryDisplayScale -Times 0 -Exactly }
        }
        finally {
            Set-Content $release 'release'
            if (-not $owner.WaitForExit(5000)) { $owner.Kill(); $owner.WaitForExit() }
            $owner.Dispose()
        }
    }

    It 'rejects a provider that reports another selected scale before starting smoke' {
        InModuleScope DpiMatrix {
            Mock Set-PrimaryDisplayScale { [pscustomobject]@{ scalePercent = 150; dpi = 96 } }
        }
        { Invoke-DpiMatrix @arguments -ScalePercents @(100) } | Should -Throw '*selected scale*'
        InModuleScope DpiMatrix { Should -Invoke Invoke-DpiSmokeRun -Times 0 -Exactly }
    }

    It 'rejects smoke evidence whose requested scale differs even when actual DPI matches' {
        InModuleScope DpiMatrix {
            Mock Invoke-DpiSmokeRun {
                param($ArchivePath, $EvidencePath)
                New-Item -ItemType Directory -Path (Split-Path $EvidencePath) -Force | Out-Null
                @{ passed = $true; process = @{ exitCode = 0 }; timeouts = @{ expectedDpiPercent = 125 }
                    observations = @{ archiveSha256 = (Get-FileHash $ArchivePath).Hash.ToLowerInvariant()
                        responsiveLayout = @{ initial = @{ dpi = 96 } }
                    }
                } | ConvertTo-Json -Depth 6 | Set-Content $EvidencePath
                return 0
            }
        }
        { Invoke-DpiMatrix @arguments -ScalePercents @(100) } | Should -Throw '*requested scale*'
    }
}
