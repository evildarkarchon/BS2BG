#Requires -Modules @{ ModuleName = 'Pester'; ModuleVersion = '5.0.0' }

BeforeAll {
    Import-Module (Join-Path $PSScriptRoot 'UiaAutomation.psm1') -Force
}

Describe 'System accessibility preference helpers' {
    It 'exports reversible High Contrast and client-area animation controls' {
        Get-Command Get-SystemAccessibilityPreferences -ErrorAction Stop | Should -Not -BeNullOrEmpty
        Get-Command Set-SystemHighContrast -ErrorAction Stop | Should -Not -BeNullOrEmpty
        Get-Command Set-SystemClientAreaAnimation -ErrorAction Stop | Should -Not -BeNullOrEmpty
        Get-Command Restore-SystemAccessibilityPreferences -ErrorAction Stop | Should -Not -BeNullOrEmpty
    }

    It 'captures the current state without changing it' {
        $state = Get-SystemAccessibilityPreferences
        $state.HighContrast | Should -BeOfType [bool]
        $state.ClientAreaAnimation | Should -BeOfType [bool]
        $state.HighContrastFlags | Should -BeOfType [uint32]
    }
}

Describe 'UI Automation selection helpers' {
    It 'exports stable logical-selection inspection for packaged list tests' {
        Get-Command Get-UiaSelectionState -ErrorAction Stop | Should -Not -BeNullOrEmpty
    }

    It 'exports provider-located pointer activation for packaged pointer tests' {
        Get-Command Invoke-UiaPointerClick -ErrorAction Stop | Should -Not -BeNullOrEmpty
    }
}

Describe 'UI Automation generated-text helpers' {
    It 'exports read-only state inspection for packaged Output tests' {
        Get-Command Get-UiaReadOnlyState -ErrorAction Stop | Should -Not -BeNullOrEmpty
    }
}

Describe 'UI Automation native filename helpers' {
    BeforeEach {
        InModuleScope UiaAutomation {
            $script:filenameWritten = $null
            $script:filenameReadback = 'C:\fixtures\selected.jbs2bg'
            $script:filenamePattern = [pscustomobject]@{}
            $script:filenamePattern | Add-Member ScriptMethod SetValue {
                param($value)
                $script:filenameWritten = $value
            }
            $script:filenameField = [pscustomobject]@{}
            $script:filenameField | Add-Member ScriptMethod TryGetCurrentPattern {
                param($pattern, $result)
                $result.Value = $script:filenamePattern
                return $true
            }
            Mock Wait-UiaElement { return $script:filenameField }
            Mock Find-UiaElement { return $script:filenameField }
            Mock Get-UiaText { return $script:filenameReadback }
            Mock Send-UiaKeys { throw 'The ValuePattern path must not emit keyboard input.' }
        }
    }

    It 'uses the available ValuePattern and verifies the exact filename text' {
        InModuleScope UiaAutomation {
            $dialog = [pscustomobject]@{ Current = [pscustomobject]@{ ProcessId = 123; NativeWindowHandle = 456 } }
            Set-UiaFileDialogName -Dialog $dialog -Value 'C:\fixtures\selected.jbs2bg' -TimeoutSeconds 1
            $script:filenameWritten | Should -BeExactly 'C:\fixtures\selected.jbs2bg'
            Should -Invoke Send-UiaKeys -Times 0 -Exactly
        }
    }

    It 'rejects a write whose readback differs instead of submitting the wrong filename' {
        InModuleScope UiaAutomation {
            $dialog = [pscustomobject]@{ Current = [pscustomobject]@{ ProcessId = 123; NativeWindowHandle = 456 } }
            $script:filenameReadback = 'C:\fixtures\other.jbs2bg'
            {
                Set-UiaFileDialogName -Dialog $dialog -Value 'C:\fixtures\selected.jbs2bg' -TimeoutSeconds 1
            } | Should -Throw '*exact native file dialog filename text*'
        }
    }
}

Describe 'UI Automation native dialog process ownership' {
    It 'uses the supplied dialog process for standalone address typing despite a different caller variable' {
        InModuleScope UiaAutomation {
            $processId = 999
            $dialog = [pscustomobject]@{ Current = [pscustomobject]@{ ProcessId = 123; NativeWindowHandle = 456 } }
            Mock Send-UiaKeys {}
            Mock Get-UiaText { return 'C:\chosen+folder' }
            Mock Wait-UiaCondition {
                if ($Description -eq 'native Edit focus within the requested dialog') {
                    return [pscustomobject]@{}
                }
                & $Test
            }

            Set-UiaNativeDialogText -Dialog $dialog -Value 'C:\chosen+folder' -Shortcut '^l' -TimeoutSeconds 1

            Should -Invoke Send-UiaKeys -Times 2 -Exactly -ParameterFilter { $ProcessId -eq 123 }
            Should -Invoke Send-UiaKeys -Times 0 -Exactly -ParameterFilter { $ProcessId -eq 999 }
            Should -Invoke Send-UiaKeys -Times 1 -Exactly -ParameterFilter {
                $Keys -ceq '^aC:\chosen{+}folder'
            }
            Should -Invoke Get-UiaText -Times 1 -Exactly
        }
    }
}

Describe 'UI Automation pointer provider retries' {
    BeforeEach {
        InModuleScope UiaAutomation {
            $script:pointerAttempt = 0
            $script:pointerFinalState = 'available'
            Mock Start-Sleep {}
            Mock Invoke-UiaNativePointerClick { return $true }
            Mock Find-UiaElement {
                if ($Root -ne 'refresh-root') { return $null }
                $script:pointerAttempt++
                if ($script:pointerAttempt -eq 20 -and $script:pointerFinalState -eq 'missing') {
                    return $null
                }
                $left = if ($script:pointerAttempt -eq 1) { 10.0 } else { 200.0 }
                $element = [pscustomobject]@{
                    Current = [pscustomobject]@{
                        Name = 'Target'
                        IsEnabled = -not ($script:pointerAttempt -eq 20 -and $script:pointerFinalState -eq 'disabled')
                        IsOffscreen = $script:pointerAttempt -eq 20 -and $script:pointerFinalState -eq 'offscreen'
                        BoundingRectangle = [System.Windows.Rect]::new($left, 40.0, 20.0, 10.0)
                        NativeWindowHandle = 1
                    }
                }
                $element | Add-Member ScriptMethod TryGetClickablePoint { param($point) return $false }
                return $element
            }
        }

    }

    It 'uses the refreshed provider bounds when the control moves during retries' {
        InModuleScope UiaAutomation {
            $result = Invoke-UiaPointerClick -Element unused -RefreshRoot 'refresh-root' `
                -RefreshCondition ([System.Windows.Automation.Condition]::TrueCondition)

            $result.x | Should -Be 210.0
            $result.y | Should -Be 45.0
            Should -Invoke Invoke-UiaNativePointerClick -Times 1 -Exactly -ParameterFilter {
                $Point.X -eq 210.0 -and $Point.Y -eq 45.0
            }
        }
    }

    It 'refuses pointer input when the latest provider candidate disappears or becomes unavailable' {
        InModuleScope UiaAutomation {
            foreach ($state in @('missing', 'disabled', 'offscreen')) {
                $script:pointerAttempt = 0
                $script:pointerFinalState = $state
                {
                    Invoke-UiaPointerClick -Element unused -RefreshRoot 'refresh-root' `
                        -RefreshCondition ([System.Windows.Automation.Condition]::TrueCondition)
                } | Should -Throw '*neither a clickable point nor usable provider bounds*'
            }
            Should -Invoke Invoke-UiaNativePointerClick -Times 0 -Exactly
        }
    }
}
