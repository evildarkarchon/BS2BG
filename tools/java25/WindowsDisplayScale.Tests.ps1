#Requires -Modules @{ ModuleName = 'Pester'; ModuleVersion = '5.0.0' }

BeforeAll {
    $providerPath = Join-Path $PSScriptRoot 'WindowsDisplayScale.psm1'
    Import-Module $providerPath -Force
}

Describe 'Primary display capture' {
    It 'captures native DPI and Settings choices together and releases the Settings lease' {
        InModuleScope WindowsDisplayScale {
            Mock Read-PrimaryDisplayNativeState {
                [pscustomobject]@{ deviceName = '\\.\DISPLAY2'; monitorDevicePath = 'monitor-two'; scalePercent = 100; dpi = 96
                    bounds = [pscustomobject]@{ left = 0; top = 0; width = 2560; height = 1440 } }
            }
            Mock Open-PrimaryDisplaySettings {
                [pscustomobject]@{ scalePercent = 100; supportedScales = @(100, 125, 150); settingsDisplayName = 'Display 2' }
            }
            Mock Close-DisplayScaleSettings {}
            $captured = Get-PrimaryDisplayScale
            $captured.monitorDevicePath | Should -BeExactly 'monitor-two'
            $captured.dpi | Should -Be 96
            $captured.supportedScales | Should -Be @(100, 125, 150)
            $captured.settingsDisplayName | Should -BeExactly 'Display 2'
            Should -Invoke Close-DisplayScaleSettings -Times 1 -Exactly
        }
    }

    It 'rejects a Settings/native disagreement and still releases the lease' {
        InModuleScope WindowsDisplayScale {
            Mock Read-PrimaryDisplayNativeState { [pscustomobject]@{ scalePercent = 125; dpi = 120 } }
            Mock Open-PrimaryDisplaySettings { [pscustomobject]@{ scalePercent = 100 } }
            Mock Close-DisplayScaleSettings {}
            { Get-PrimaryDisplayScale } | Should -Throw '*native primary monitor DPI disagree*'
            Should -Invoke Close-DisplayScaleSettings -Times 1 -Exactly
        }
    }
}

Describe 'Primary display scale admission' {
    It 'rejects a percentage absent from the captured standard choices before desktop mutation' {
        $display = [pscustomobject]@{
            deviceName = '\\.\DISPLAY2'
            monitorDevicePath = 'monitor-two'
            scalePercent = 100
            supportedScales = @(100, 125, 150)
        }
        { Set-PrimaryDisplayScale -Display $display -ScalePercent 117 } |
            Should -Throw '*standard scale*'
    }

    It 'refuses a replaced primary monitor before opening Settings' {
        InModuleScope WindowsDisplayScale {
            Mock Read-PrimaryDisplayNativeState {
                [pscustomobject]@{ deviceName = '\\.\DISPLAY2'; monitorDevicePath = 'replacement-monitor'; scalePercent = 100; dpi = 96 }
            }
            Mock Open-PrimaryDisplaySettings { throw 'Settings must not be opened for a different monitor.' }
            $original = [pscustomobject]@{
                deviceName = '\\.\DISPLAY2'; monitorDevicePath = 'original-monitor'; scalePercent = 100
                supportedScales = @(100, 125, 150)
            }
            { Set-PrimaryDisplayScale -Display $original -ScalePercent 125 } |
                Should -Throw '*primary monitor identity changed*'
            Should -Invoke Open-PrimaryDisplaySettings -Times 0 -Exactly
        }
    }
}

Describe 'Verified primary scale changes' {
    It 'returns the changed native DPI only after the Settings selection agrees' {
        InModuleScope WindowsDisplayScale {
            $script:fakeScale = 100
            Mock Read-PrimaryDisplayNativeState {
                [pscustomobject]@{ deviceName = '\\.\DISPLAY2'; monitorDevicePath = 'monitor-two'
                    scalePercent = $script:fakeScale; dpi = $script:fakeScale * 96 / 100
                    bounds = [pscustomobject]@{ left = 0; top = 0; width = 2560; height = 1440 } }
            }
            Mock Open-PrimaryDisplaySettings {
                [pscustomobject]@{ scalePercent = 100; supportedScales = @(100, 125, 150); settingsDisplayName = 'Display 2' }
            }
            Mock Read-SettingsScale { $script:fakeScale }
            Mock Select-SettingsScale { param($Lease, $ScalePercent) $script:fakeScale = $ScalePercent }
            Mock Close-DisplayScaleSettings {}
            $original = [pscustomobject]@{
                deviceName = '\\.\DISPLAY2'; monitorDevicePath = 'monitor-two'; scalePercent = 100
                supportedScales = @(100, 125, 150)
            }
            $changed = Set-PrimaryDisplayScale -Display $original -ScalePercent 125
            $changed.dpi | Should -Be 120
            $changed.scalePercent | Should -Be 125
            $changed.monitorDevicePath | Should -BeExactly 'monitor-two'
            Should -Invoke Close-DisplayScaleSettings -Times 1 -Exactly
        }
    }

    It 'does not write Settings when a discovered non-default scale is already applied' {
        InModuleScope WindowsDisplayScale {
            Mock Read-PrimaryDisplayNativeState {
                [pscustomobject]@{ deviceName = '\\.\DISPLAY7'; monitorDevicePath = 'monitor-seven'; scalePercent = 200; dpi = 192
                    bounds = [pscustomobject]@{ left = 0; top = 0; width = 3840; height = 2160 } }
            }
            Mock Open-PrimaryDisplaySettings {
                [pscustomobject]@{ scalePercent = 200; supportedScales = @(100, 150, 175, 200); settingsDisplayName = 'Display 7' }
            }
            Mock Read-SettingsScale { 200 }
            Mock Select-SettingsScale { throw 'A no-op must not change desktop settings.' }
            Mock Close-DisplayScaleSettings {}
            $original = [pscustomobject]@{
                deviceName = '\\.\DISPLAY7'; monitorDevicePath = 'monitor-seven'; scalePercent = 200
                supportedScales = @(100, 150, 175, 200)
            }
            $restored = Set-PrimaryDisplayScale -Display $original -ScalePercent $original.scalePercent
            $restored.dpi | Should -Be 192
            $restored.scalePercent | Should -Be 200
            Should -Invoke Select-SettingsScale -Times 0 -Exactly
            Should -Invoke Close-DisplayScaleSettings -Times 1 -Exactly
        }
    }
}
