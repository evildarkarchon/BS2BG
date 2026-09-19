#Requires -Modules @{ ModuleName = 'Pester'; ModuleVersion = '5.0.0' }

Describe 'Display-scale packaging command validation' {
    It 'rejects incompatible matrix options before running the build' -ForEach @(
        @{ Flags = @('-DpiMatrix', '-SkipSmoke'); Message = '*DpiMatrix cannot be combined with SkipSmoke*' }
        @{ Flags = @('-DpiMatrix', '-ExpectedDpiPercent', '125'); Message = '*DpiMatrix cannot be combined with ExpectedDpiPercent*' }
        @{ Flags = @('-DpiScalePercents', '175'); Message = '*DpiScalePercents requires DpiMatrix*' }
    ) {
        $output = & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'package-java25.ps1') @Flags 2>&1
        $LASTEXITCODE | Should -Be 1
        ($output -join "`n") | Should -BeLike $Message
    }

    It 'rejects pending recovery before clean-checkout validation or any build work' {
        $recovery = Join-Path $TestDrive 'pending.json'
        Set-Content $recovery '{"schema":"bs2bg.dpi-matrix-recovery/1","status":"pending"}'
        $output = & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'package-java25.ps1') `
            -DpiMatrix -DpiRecoveryPath $recovery 2>&1
        $LASTEXITCODE | Should -Be 1
        ($output -join "`n") | Should -BeLike '*Unresolved display-scale recovery*'
        ($output -join "`n") | Should -Not -BeLike '*Proving the checkpoint*'
    }

    It 'accepts recovery mode without requiring archive or fixture arguments' {
        $recovery = Join-Path $TestDrive 'completed.json'
        @{
            schema = 'bs2bg.dpi-matrix-recovery/1'; status = 'restored'
            machine = [Environment]::MachineName; user = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
        } | ConvertTo-Json | Set-Content $recovery
        $output = & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -NonInteractive -File (Join-Path $PSScriptRoot 'smoke-dpi-matrix.ps1') `
            -RestoreOnly -RecoveryPath $recovery 2>&1
        $LASTEXITCODE | Should -Be 0
        ($output -join "`n") | Should -BeLike '*Display-scale recovery: restored*'
    }
}
