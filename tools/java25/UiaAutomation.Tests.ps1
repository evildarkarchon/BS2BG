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

Describe 'UI Automation empty read-only documents' {
    BeforeEach {
        InModuleScope UiaAutomation {
            $script:documentChildren = @([pscustomobject]@{ Current = [pscustomobject]@{ Name = '' } })
            $script:documentValue = [pscustomobject]@{
                Current = [pscustomobject]@{ Value = 'Generated Morphs output'; IsReadOnly = $true }
            }
            $range = [pscustomobject]@{}
            $range | Add-Member ScriptMethod GetText { param($length) return 'Generated Morphs output' }
            $script:documentText = [pscustomobject]@{ DocumentRange = $range }
            $script:documentElement = [pscustomobject]@{}
            $script:documentElement | Add-Member ScriptMethod TryGetCurrentPattern {
                param($pattern, $result)
                $result.Value = if ($pattern -eq [System.Windows.Automation.ValuePattern]::Pattern) {
                    $script:documentValue
                } else {
                    $script:documentText
                }
                return $true
            }
            Mock Find-UiaElements { return $script:documentChildren }
        }
    }

    It 'returns a present empty text document instead of its accessible label' {
        InModuleScope UiaAutomation {
            $actual = Get-UiaText -Element $script:documentElement
            $actual | Should -BeExactly ''
        }
    }

    It 'retains the pattern fallback when no document text descendant exists' {
        InModuleScope UiaAutomation {
            $script:documentChildren = @()
            Get-UiaText -Element $script:documentElement | Should -BeExactly 'Generated Morphs output'
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


    It 'accepts the exact Shell address pane when fractional-DPI Edit readback differs' {
        InModuleScope UiaAutomation {
            $dialog = [pscustomobject]@{ Current = [pscustomobject]@{ ProcessId = 123; NativeWindowHandle = 456 } }
            Mock Send-UiaKeys {}
            Mock Get-UiaText { return 'Address: C:\chosen-folder' }
            Mock Find-UiaElement { return [pscustomobject]@{ Current = [pscustomobject]@{ Name = 'C:\chosen-folder' } } }
            Mock Wait-UiaCondition {
                if ($Description -eq 'native Edit focus within the requested dialog') {
                    return [pscustomobject]@{}
                }
                $result = & $Test
                if (-not $result) { throw 'Exact address was not accepted.' }
                return $result
            }

            Set-UiaNativeDialogText -Dialog $dialog -Value 'C:\chosen-folder' -Shortcut '^l' -TimeoutSeconds 1

            Should -Invoke Find-UiaElement -Times 1 -Exactly
        }
    }
}

Describe 'UI Automation strict element lookup' {
    BeforeEach {
        InModuleScope UiaAutomation {
            Mock Start-Sleep { throw 'Strict lookups must not poll.' }
            $script:liveElement = [pscustomobject]@{ Current = [pscustomobject]@{ Name = 'UUNP Athletic' } }
            $script:liveElement | Add-Member ScriptMethod GetRuntimeId { return [int[]]@(42, 7) }
        }
    }

    It 'returns the single element it finds without polling' {
        InModuleScope UiaAutomation {
            Mock Find-UiaElement { return $script:liveElement }
            $found = Find-UiaElementStrict -Root 'root' -Condition 'condition' -Description 'Slider Preset row'
            $found | Should -Be $script:liveElement
            Should -Invoke Find-UiaElement -Times 1 -Exactly
        }
    }

    It 'fails immediately when the element is missing' {
        InModuleScope UiaAutomation {
            Mock Find-UiaElement { return $null }
            { Find-UiaElementStrict -Root 'root' -Condition 'condition' -Description 'Slider Preset row' } |
                Should -Throw '*did not find Slider Preset row*'
            Should -Invoke Find-UiaElement -Times 1 -Exactly
        }
    }

    It 'fails on an element-unavailable error instead of swallowing it' {
        InModuleScope UiaAutomation {
            Mock Find-UiaElement { throw [System.Windows.Automation.ElementNotAvailableException]::new('gone') }
            { Find-UiaElementStrict -Root 'root' -Condition 'condition' -Description 'Slider Preset row' } |
                Should -Throw '*element-unavailable*Slider Preset row*'
            Should -Invoke Find-UiaElement -Times 1 -Exactly
        }
    }

    It 'passes other UIA failures through unchanged' {
        InModuleScope UiaAutomation {
            Mock Find-UiaElement { throw 'provider crashed' }
            $failure = $null
            try { Find-UiaElementStrict -Root 'root' -Condition 'condition' -Description 'Slider Preset row' }
            catch { $failure = $_.Exception.Message }
            $failure | Should -BeExactly 'provider crashed'
        }
    }

    It 'recognizes element-unavailable errors wrapped by PowerShell or raised as the native UIA HRESULT' {
        InModuleScope UiaAutomation {
            $native = [System.Runtime.InteropServices.COMException]::new('gone', -2147220991)
            $wrapped = [System.Management.Automation.MethodInvocationException]::new('call failed', $native)
            Test-UiaElementUnavailableError -Exception $wrapped | Should -BeTrue
            Test-UiaElementUnavailableError -Exception (
                [System.Windows.Automation.ElementNotAvailableException]::new('gone')) | Should -BeTrue
            Test-UiaElementUnavailableError -Exception (
                [System.Runtime.InteropServices.COMException]::new('other', -2147467259)) | Should -BeFalse
            Test-UiaElementUnavailableError -Exception ([System.InvalidOperationException]::new('x')) |
                Should -BeFalse
        }
    }

    It 'accepts a held element that is still live with the same RuntimeId' {
        InModuleScope UiaAutomation {
            Mock Find-UiaElement { return $script:liveElement }
            $id = Assert-UiaElementStable -Element $script:liveElement -ExpectedRuntimeId @(42, 7) -Root 'root' `
                -Condition 'condition' -Description 'Slider Preset row'
            $id | Should -BeExactly '42.7'
        }
    }

    It 'fails when the held element has become unavailable' {
        InModuleScope UiaAutomation {
            $dead = [pscustomobject]@{}
            $dead | Add-Member ScriptMethod GetRuntimeId {
                throw [System.Windows.Automation.ElementNotAvailableException]::new('gone')
            }
            Mock Find-UiaElement { return $script:liveElement }
            {
                Assert-UiaElementStable -Element $dead -ExpectedRuntimeId @(42, 7) -Root 'root' `
                    -Condition 'condition' -Description 'Slider Preset row'
            } | Should -Throw '*held Slider Preset row*element-unavailable*'
        }
    }

    It 'fails when a fresh lookup finds a different RuntimeId' {
        InModuleScope UiaAutomation {
            $replacement = [pscustomobject]@{ Current = [pscustomobject]@{ Name = 'UUNP Athletic' } }
            $replacement | Add-Member ScriptMethod GetRuntimeId { return [int[]]@(42, 9) }
            Mock Find-UiaElement { return $replacement }
            {
                Assert-UiaElementStable -Element $script:liveElement -ExpectedRuntimeId @(42, 7) -Root 'root' `
                    -Condition 'condition' -Description 'Slider Preset row'
            } | Should -Throw '*Slider Preset row*RuntimeId 42.7*42.9*'
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
