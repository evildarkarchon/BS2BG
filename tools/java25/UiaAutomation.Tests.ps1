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
