#Requires -Modules @{ ModuleName = 'Pester'; ModuleVersion = '5.0.0' }

BeforeAll {
    Import-Module (Join-Path $PSScriptRoot 'MixedMonitorDpi.psm1') -Force
    Import-Module (Join-Path $PSScriptRoot 'DpiMatrix.psm1')
}

Describe 'Packaged mixed-monitor DPI audit' {
    BeforeEach {
        $caseRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $caseRoot | Out-Null
        $archive = Join-Path $caseRoot 'preview.zip'
        Set-Content -LiteralPath $archive -Value 'immutable app image'
        $arguments = @{
            ArchivePath = $archive
            EvidencePath = Join-Path $caseRoot 'evidence/mixed-monitor.json'
            RecoveryPath = Join-Path $caseRoot 'display-recovery.json'
            WorkRoot = Join-Path $caseRoot 'work'
        }
        InModuleScope MixedMonitorDpi {
            $script:events = [System.Collections.Generic.List[string]]::new()
            $script:currentScale = 100
            Mock Get-PrimaryDisplayScale {
                [pscustomobject]@{
                    deviceName = '\\.\DISPLAY2'; monitorDevicePath = 'primary-path'
                    scalePercent = 100; dpi = 96; supportedScales = @(100,125,150)
                    settingsDisplayName = 'Display 2'; bounds = @{left=0;top=0;width=2560;height=1440}
                }
            }
            Mock Set-PrimaryDisplayScale {
                param($Display, $ScalePercent)
                $script:events.Add("scale:$ScalePercent")
                $script:currentScale = $ScalePercent
                [pscustomobject]@{
                    deviceName = $Display.deviceName; monitorDevicePath = $Display.monitorDevicePath
                    scalePercent = $ScalePercent; dpi = $ScalePercent * 96 / 100
                }
            }
            Mock Read-MixedMonitorTopology {
                @(
                    [pscustomobject]@{
                        deviceName = '\\.\DISPLAY2'; monitorDevicePath = 'primary-path'; primary = $true
                        dpi = $script:currentScale * 96 / 100; scalePercent = $script:currentScale
                        bounds = [pscustomobject]@{left=0;top=0;width=2560;height=1440}
                    },
                    [pscustomobject]@{
                        deviceName = '\\.\DISPLAY1'; monitorDevicePath = 'secondary-path'; primary = $false
                        dpi = 96; scalePercent = 100
                        bounds = [pscustomobject]@{left=2560;top=0;width=2560;height=1440}
                    }
                )
            }
            Mock Invoke-MixedMonitorCase {
                param($ArchivePath, $ScalePercent)
                $script:events.Add("case:$ScalePercent")
                [pscustomobject]@{
                    processId = 3000 + $ScalePercent
                    landings = @(
                        @{display='primary'; dpi=$ScalePercent * 96 / 100},
                        @{display='secondary'; dpi=96},
                        @{display='primary'; dpi=$ScalePercent * 96 / 100}
                    )
                    exitCode = 0
                }
            }
        }
    }

    It 'moves one archive at 150-to-100 and 125-to-100, then restores the captured primary scale' {
        $result = Invoke-MixedMonitorDpiAudit @arguments

        $result.passed | Should -BeTrue
        @($result.runs.scalePercent) -join ',' | Should -Be '150,125'
        @($result.runs.archiveSha256 | Select-Object -Unique).Count | Should -Be 1
        @($result.runs.case.landings.dpi) -join ',' | Should -Be '144,96,144,120,96,120'
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'restored'
        (Get-Content $arguments.EvidencePath -Raw | ConvertFrom-Json).passed | Should -BeTrue
        InModuleScope MixedMonitorDpi {
            $script:events -join ',' | Should -Be 'scale:150,case:150,scale:125,case:125,scale:100'
        }
    }

    It 'rejects evidence inside the work root before persisting recovery or changing a display' {
        $arguments.EvidencePath = Join-Path $arguments.WorkRoot 'artifacts/../artifacts/mixed-monitor.json'

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*EvidencePath*WorkRoot*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope MixedMonitorDpi {
            Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly
            Should -Invoke Invoke-MixedMonitorCase -Times 0 -Exactly
        }
    }

    It 'rejects an extended-length work root that aliases the evidence directory before recovery or display changes' {
        $arguments.EvidencePath = Join-Path $arguments.WorkRoot 'artifacts/mixed-monitor.json'
        $arguments.WorkRoot = '\\?\' + [IO.Path]::GetFullPath($arguments.WorkRoot)

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*WorkRoot*extended-length*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope MixedMonitorDpi {
            Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly
            Should -Invoke Invoke-MixedMonitorCase -Times 0 -Exactly
        }
    }

    It 'rejects an extended-length evidence path inside the ordinary work root' {
        $insideWorkRoot = Join-Path $arguments.WorkRoot 'artifacts/mixed-monitor.json'
        $arguments.EvidencePath = '\\?\' + [IO.Path]::GetFullPath($insideWorkRoot)

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*EvidencePath*extended-length*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope MixedMonitorDpi { Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'rejects a device-prefixed recovery path inside the ordinary work root' {
        $insideWorkRoot = Join-Path $arguments.WorkRoot 'display-recovery.json'
        $arguments.RecoveryPath = '\\.\' + [IO.Path]::GetFullPath($insideWorkRoot)

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*RecoveryPath*device*'
        Test-Path $arguments.WorkRoot | Should -BeFalse
        InModuleScope MixedMonitorDpi { Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'rejects evidence beneath a work root reached through a junction' {
        $physical = Join-Path $caseRoot 'physical'
        New-Item -ItemType Directory -Path $physical | Out-Null
        $alias = Join-Path $caseRoot 'alias'
        New-Item -ItemType Junction -Path $alias -Target $physical | Out-Null
        $arguments.WorkRoot = Join-Path $alias 'work'
        $arguments.EvidencePath = Join-Path $physical 'work/artifacts/mixed-monitor.json'

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*WorkRoot*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope MixedMonitorDpi { Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'rejects an evidence path reached through a junction into the work root' {
        $physical = Join-Path $caseRoot 'physical'
        New-Item -ItemType Directory -Path $physical | Out-Null
        $alias = Join-Path $caseRoot 'alias'
        New-Item -ItemType Junction -Path $alias -Target $physical | Out-Null
        $arguments.WorkRoot = Join-Path $physical 'work'
        $arguments.EvidencePath = Join-Path $alias 'work/artifacts/mixed-monitor.json'

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*EvidencePath*WorkRoot*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope MixedMonitorDpi { Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'rejects a work root that would remove one per-case evidence directory' {
        $arguments.WorkRoot = Join-Path (Split-Path -Parent $arguments.EvidencePath) 'mixed-150'

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*EvidencePath*WorkRoot*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope MixedMonitorDpi { Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'rejects a recovery record beneath the work root before creating its lock directory' {
        $arguments.RecoveryPath = Join-Path $arguments.WorkRoot 'display-recovery.json'

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*RecoveryPath*WorkRoot*'
        Test-Path $arguments.WorkRoot | Should -BeFalse
        InModuleScope MixedMonitorDpi { Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'rejects a recovery record at the work root path' {
        $arguments.RecoveryPath = $arguments.WorkRoot

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*RecoveryPath*WorkRoot*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope MixedMonitorDpi { Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'allows evidence in a sibling directory with the same name prefix as the work root' {
        $arguments.EvidencePath = Join-Path (Split-Path -Parent $arguments.WorkRoot) 'work-report/mixed-monitor.json'

        $result = Invoke-MixedMonitorDpiAudit @arguments

        $result.passed | Should -BeTrue
        Test-Path $arguments.EvidencePath | Should -BeTrue
    }

    It 'keeps evidence inside the work root when work-root cleanup is disabled' {
        $arguments.EvidencePath = Join-Path $arguments.WorkRoot 'artifacts/mixed-monitor.json'
        $arguments.KeepWorkRoot = $true

        $result = Invoke-MixedMonitorDpiAudit @arguments

        $result.passed | Should -BeTrue
        Test-Path $arguments.EvidencePath | Should -BeTrue
    }

    It 'rejects a non-100-percent secondary before persisting recovery or changing a display' {
        InModuleScope MixedMonitorDpi {
            Mock Read-MixedMonitorTopology {
                @(
                    [pscustomobject]@{deviceName='\\.\DISPLAY2';monitorDevicePath='primary-path';primary=$true;dpi=96;scalePercent=100;bounds=@{left=0;top=0;width=2560;height=1440}},
                    [pscustomobject]@{deviceName='\\.\DISPLAY1';monitorDevicePath='secondary-path';primary=$false;dpi=120;scalePercent=125;bounds=@{left=2560;top=0;width=2560;height=1440}}
                )
            }
        }

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*secondary*100%*'
        Test-Path $arguments.RecoveryPath | Should -BeFalse
        InModuleScope MixedMonitorDpi {
            Should -Invoke Set-PrimaryDisplayScale -Times 0 -Exactly
            Should -Invoke Invoke-MixedMonitorCase -Times 0 -Exactly
        }
    }

    It 'records a failed packaged move and restores before reporting the failure' {
        InModuleScope MixedMonitorDpi {
            Mock Invoke-MixedMonitorCase { throw 'window did not adopt secondary DPI' } -ParameterFilter {
                $ScalePercent -eq 125
            }
        }

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*window did not adopt secondary DPI*'
        $evidence = Get-Content $arguments.EvidencePath -Raw | ConvertFrom-Json
        $evidence.passed | Should -BeFalse
        $evidence.runs.Count | Should -Be 2
        $evidence.restoration.scalePercent | Should -Be 100
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'restored'
    }

    It 'keeps the recovery record pending if display restoration fails' {
        InModuleScope MixedMonitorDpi {
            Mock Set-PrimaryDisplayScale { throw 'restore failed' } -ParameterFilter { $ScalePercent -eq 100 }
        }

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*restore failed*'
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'pending'
        (Get-Content $arguments.EvidencePath -Raw | ConvertFrom-Json).restorationError | Should -Be 'restore failed'
    }

    It 'refuses an unresolved recovery record without overwriting it' {
        $pending = '{"schema":"bs2bg.dpi-matrix-recovery/1","status":"pending"}'
        Set-Content -LiteralPath $arguments.RecoveryPath -Value $pending -NoNewline

        { Invoke-MixedMonitorDpiAudit @arguments } | Should -Throw '*Unresolved*'
        Get-Content $arguments.RecoveryPath -Raw | Should -BeExactly $pending
        InModuleScope MixedMonitorDpi { Should -Invoke Get-PrimaryDisplayScale -Times 0 -Exactly }
    }

    It 'writes a pending record that the existing matrix recovery command can restore' {
        Invoke-MixedMonitorDpiAudit @arguments | Out-Null
        $record = Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json
        $record.status = 'pending'
        $record.ownerPid = [int]::MaxValue
        $record | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $arguments.RecoveryPath
        InModuleScope DpiMatrix {
            Mock Set-PrimaryDisplayScale {
                param($Display, $ScalePercent)
                [pscustomobject]@{ scalePercent = $ScalePercent; dpi = $ScalePercent * 96 / 100 }
            }
        }

        $restored = Restore-DpiMatrixDisplay -RecoveryPath $arguments.RecoveryPath

        $restored.status | Should -Be 'restored'
        (Get-Content $arguments.RecoveryPath -Raw | ConvertFrom-Json).status | Should -Be 'restored'
        InModuleScope DpiMatrix { Should -Invoke Set-PrimaryDisplayScale -Times 1 -Exactly }
    }
}
