#Requires -Modules @{ ModuleName = 'Pester'; ModuleVersion = '5.0.0' }
<#
.SYNOPSIS
    Pester tests for the deterministic, network-free parts of Java25Toolchain.psm1.

.DESCRIPTION
    The download path is exercised only by running tools/java25/verify-java25.ps1 end to end; everything that
    decides whether an input is accepted (hash comparison, JDK release metadata, jmod version, wrapper pins,
    archive extraction, transitional source scope) is covered here so a lock change is caught before any bytes
    are downloaded.

    Run with:  Invoke-Pester -Path tools/java25 -Output Detailed
#>

BeforeAll {
    Import-Module (Join-Path $PSScriptRoot 'Java25Toolchain.psm1') -Force
    $script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $script:LockPath = Join-Path $PSScriptRoot 'toolchain-lock.json'

    # Builds a temporary file with the given content and returns its path.
    function New-TempFile {
        param([string]$Name, [string]$Content)
        $path = Join-Path $TestDrive $Name
        Set-Content -LiteralPath $path -Value $Content -NoNewline -Encoding utf8
        return $path
    }
}

Describe 'Get-ToolchainLock' {
    It 'loads the committed lock and exposes every pinned input' {
        $lock = Get-ToolchainLock -Path $script:LockPath
        $lock.targetRelease | Should -Be 25
        $lock.maven.version | Should -Be '3.9.16'
        $lock.maven.distributionSha256 | Should -Match '^[0-9a-f]{64}$'
        $lock.jdk.sha256 | Should -Match '^[0-9a-f]{64}$'
        $lock.jdk.release.IMPLEMENTOR_VERSION | Should -Be 'Temurin-25.0.4.1+1'
        $lock.javafx.version | Should -Be '25.0.4'
        $lock.javafx.sha256 | Should -Match '^[0-9a-f]{64}$'
        $lock.javafx.requiredModules | Should -Contain 'javafx.base'
        $lock.architecture.processorArchitecture | Should -Be 'AMD64'
    }

    It 'fails closed when a required section is missing' {
        $path = New-TempFile 'partial-lock.json' '{ "targetRelease": 25, "architecture": {}, "maven": { "version": "3.9.16", "distributionSha256": "00" } }'
        { Get-ToolchainLock -Path $path } | Should -Throw -ExpectedMessage '*jdk*'
    }

    It 'fails closed when a checksum is not a 64-hex SHA-256' {
        $lock = Get-Content -LiteralPath $script:LockPath -Raw | ConvertFrom-Json
        $lock.jdk.sha256 = 'not-a-hash'
        $path = New-TempFile 'bad-hash-lock.json' ($lock | ConvertTo-Json -Depth 10)
        { Get-ToolchainLock -Path $path } | Should -Throw -ExpectedMessage '*jdk.sha256*'
    }
}

Describe 'Assert-FileSha256' {
    BeforeAll {
        $script:Sample = New-TempFile 'sample.bin' 'BS2BG sample payload'
        $script:SampleHash = (Get-FileHash -LiteralPath $script:Sample -Algorithm SHA256).Hash.ToLowerInvariant()
    }

    It 'returns the lowercase hash when it matches, regardless of the expected value casing' {
        Assert-FileSha256 -Path $script:Sample -ExpectedSha256 $script:SampleHash.ToUpperInvariant() -Label 'sample' |
            Should -Be $script:SampleHash
    }

    It 'throws a fail-closed error that names the input and both hashes on mismatch' {
        $expected = ('0' * 64)
        $failure = { Assert-FileSha256 -Path $script:Sample -ExpectedSha256 $expected -Label 'Temurin JDK' }
        $failure | Should -Throw -ExpectedMessage '*Temurin JDK*'
        $failure | Should -Throw -ExpectedMessage "*$expected*"
        $failure | Should -Throw -ExpectedMessage "*$($script:SampleHash)*"
    }
}

Describe 'Read-JdkReleaseFile' {
    It 'parses quoted KEY="value" pairs and ignores blank lines' {
        $content = "IMPLEMENTOR=`"Eclipse Adoptium`"`r`n`r`nJAVA_VERSION=`"25.0.4.1`"`nMODULES=`"java.base java.compiler`"`n"
        $release = Read-JdkReleaseFile -Path (New-TempFile 'release' $content)
        $release['IMPLEMENTOR'] | Should -Be 'Eclipse Adoptium'
        $release['JAVA_VERSION'] | Should -Be '25.0.4.1'
        $release['MODULES'] | Should -Be 'java.base java.compiler'
        $release.Count | Should -Be 3
    }
}

Describe 'Assert-JdkRelease' {
    BeforeAll {
        $script:Expected = (Get-ToolchainLock -Path $script:LockPath).jdk.release
        $script:Matching = @{
            IMPLEMENTOR          = 'Eclipse Adoptium'
            IMPLEMENTOR_VERSION  = 'Temurin-25.0.4.1+1'
            JAVA_VERSION         = '25.0.4.1'
            JAVA_RUNTIME_VERSION = '25.0.4.1+1-LTS'
            SEMANTIC_VERSION     = '25.0.4.1+1'
            IMAGE_TYPE           = 'JDK'
            OS_NAME              = 'Windows'
            OS_ARCH              = 'x86_64'
            MODULES              = 'java.base'
        }
    }

    It 'accepts a release file whose pinned keys all match (extra keys are ignored)' {
        { Assert-JdkRelease -Release $script:Matching -Expected $script:Expected } | Should -Not -Throw
    }

    It 'reports every mismatching or missing key in one fail-closed error' {
        $drifted = $script:Matching.Clone()
        $drifted['IMPLEMENTOR_VERSION'] = 'Temurin-25.0.5+9'
        $drifted['OS_ARCH'] = 'aarch64'
        $drifted.Remove('OS_NAME')
        $failure = { Assert-JdkRelease -Release $drifted -Expected $script:Expected }
        $failure | Should -Throw -ExpectedMessage '*IMPLEMENTOR_VERSION*'
        $failure | Should -Throw -ExpectedMessage '*OS_ARCH*'
        $failure | Should -Throw -ExpectedMessage '*OS_NAME*'
    }

    It 'rejects a different vendor even when the version strings match' {
        $otherVendor = $script:Matching.Clone()
        $otherVendor['IMPLEMENTOR'] = 'Oracle Corporation'
        { Assert-JdkRelease -Release $otherVendor -Expected $script:Expected } | Should -Throw -ExpectedMessage '*IMPLEMENTOR*'
    }
}

Describe 'Assert-JmodDescribeOutput' {
    It 'accepts the pinned module@version header' {
        $output = @('javafx.base@25.0.4', 'exports javafx.beans', 'requires java.base mandated')
        { Assert-JmodDescribeOutput -Output $output -ModuleName 'javafx.base' -Version '25.0.4' } | Should -Not -Throw
    }

    It 'rejects a different patch version' {
        { Assert-JmodDescribeOutput -Output @('javafx.base@25.0.3') -ModuleName 'javafx.base' -Version '25.0.4' } |
            Should -Throw -ExpectedMessage '*javafx.base@25.0.4*'
    }

    It 'rejects a different module name' {
        { Assert-JmodDescribeOutput -Output @('javafx.web@25.0.4') -ModuleName 'javafx.base' -Version '25.0.4' } |
            Should -Throw -ExpectedMessage '*javafx.base*'
    }

    It 'skips blank lines before the header (jmod describe emits one)' {
        $output = @('', 'javafx.controls@25.0.4', '', 'requires javafx.base')
        { Assert-JmodDescribeOutput -Output $output -ModuleName 'javafx.controls' -Version '25.0.4' } | Should -Not -Throw
    }

    It 'rejects empty output' {
        { Assert-JmodDescribeOutput -Output @() -ModuleName 'javafx.base' -Version '25.0.4' } | Should -Throw
    }
}

Describe 'Assert-MavenWrapperPinned' {
    BeforeAll {
        $script:Lock = Get-ToolchainLock -Path $script:LockPath
        $script:WrapperProperties = Read-PropertiesFile -Path (Join-Path $script:RepoRoot '.mvn\wrapper\maven-wrapper.properties')
    }

    It 'accepts the committed wrapper properties against the committed lock' {
        { Assert-MavenWrapperPinned -Properties $script:WrapperProperties -MavenVersion $script:Lock.maven.version -DistributionSha256 $script:Lock.maven.distributionSha256 } |
            Should -Not -Throw
    }

    It 'rejects a wrapper without a distribution checksum' {
        $unpinned = $script:WrapperProperties.Clone()
        $unpinned.Remove('distributionSha256Sum')
        { Assert-MavenWrapperPinned -Properties $unpinned -MavenVersion '3.9.16' -DistributionSha256 $script:Lock.maven.distributionSha256 } |
            Should -Throw -ExpectedMessage '*distributionSha256Sum*'
    }

    It 'rejects a wrapper whose distribution version differs from the lock' {
        { Assert-MavenWrapperPinned -Properties $script:WrapperProperties -MavenVersion '3.9.15' -DistributionSha256 $script:Lock.maven.distributionSha256 } |
            Should -Throw -ExpectedMessage '*3.9.15*'
    }

    It 'rejects a wrapper whose checksum differs from the lock' {
        { Assert-MavenWrapperPinned -Properties $script:WrapperProperties -MavenVersion '3.9.16' -DistributionSha256 ('f' * 64) } |
            Should -Throw -ExpectedMessage '*distributionSha256Sum*'
    }

    It 'rejects a wrapper that is not the only-script distribution type' {
        $jarType = $script:WrapperProperties.Clone()
        $jarType['distributionType'] = 'bin'
        { Assert-MavenWrapperPinned -Properties $jarType -MavenVersion '3.9.16' -DistributionSha256 $script:Lock.maven.distributionSha256 } |
            Should -Throw -ExpectedMessage '*only-script*'
    }
}

Describe 'Expand-LockedArchive' {
    BeforeAll {
        # Build a small archive shaped like the vendor zips: a single versioned root directory.
        $stage = Join-Path $TestDrive 'stage\tool-1.0'
        New-Item -ItemType Directory -Path (Join-Path $stage 'bin') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $stage 'bin\tool.txt') -Value 'payload'
        $script:Archive = Join-Path $TestDrive 'tool-1.0.zip'
        Compress-Archive -Path (Join-Path $TestDrive 'stage\tool-1.0') -DestinationPath $script:Archive
        $script:ArchiveHash = (Get-FileHash -LiteralPath $script:Archive -Algorithm SHA256).Hash.ToLowerInvariant()
    }

    It 'extracts the expected root directory into the destination and records the checksum marker' {
        $destination = Join-Path $TestDrive 'installed\tool'
        $result = Expand-LockedArchive -ArchivePath $script:Archive -Destination $destination -ExpectedRootDirectory 'tool-1.0' -Sha256 $script:ArchiveHash
        $result | Should -Be $destination
        Test-Path (Join-Path $destination 'bin\tool.txt') | Should -BeTrue
        Test-ProvisionedArchive -Destination $destination -Sha256 $script:ArchiveHash | Should -BeTrue
    }

    It 'does not treat a destination as provisioned when the marker records a different checksum' {
        $destination = Join-Path $TestDrive 'installed\other-hash'
        Expand-LockedArchive -ArchivePath $script:Archive -Destination $destination -ExpectedRootDirectory 'tool-1.0' -Sha256 $script:ArchiveHash | Out-Null
        Test-ProvisionedArchive -Destination $destination -Sha256 ('a' * 64) | Should -BeFalse
    }

    It 'does not treat a destination without a marker as provisioned' {
        Test-ProvisionedArchive -Destination (Join-Path $TestDrive 'installed\never-extracted') -Sha256 $script:ArchiveHash | Should -BeFalse
    }

    It 'fails closed when the archive root directory is not the expected one' {
        $destination = Join-Path $TestDrive 'installed\wrong-root'
        { Expand-LockedArchive -ArchivePath $script:Archive -Destination $destination -ExpectedRootDirectory 'tool-2.0' -Sha256 $script:ArchiveHash } |
            Should -Throw -ExpectedMessage '*tool-2.0*'
        Test-Path $destination | Should -BeFalse
    }

    It 'replaces a stale destination instead of merging into it' {
        $destination = Join-Path $TestDrive 'installed\stale'
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $destination 'leftover.txt') -Value 'old'
        Expand-LockedArchive -ArchivePath $script:Archive -Destination $destination -ExpectedRootDirectory 'tool-1.0' -Sha256 $script:ArchiveHash | Out-Null
        Test-Path (Join-Path $destination 'leftover.txt') | Should -BeFalse
        Test-Path (Join-Path $destination 'bin\tool.txt') | Should -BeTrue
    }
}

Describe 'Get-TransitionalSourceScope' {
    BeforeAll {
        $script:Scope = Get-TransitionalSourceScope -RepoRoot $script:RepoRoot
    }

    It 'reads the admitted include patterns from the compiler plugin configuration' {
        $script:Scope.AdmittedPatterns | Should -Contain 'com/asdasfa/jbs2bg/project/**/*.java'
        $script:Scope.AdmittedPatterns | Should -Contain 'com/asdasfa/jbs2bg/data/**/*.java'
        $script:Scope.AdmittedPatterns | Should -Contain 'com/asdasfa/jbs2bg/presentation/**/*.java'
    }

    It 'classifies the Project aggregate as admitted and the JavaFX controllers as excluded' {
        $script:Scope.AdmittedSources | Should -Contain 'com/asdasfa/jbs2bg/project/Project.java'
        $script:Scope.ExcludedSources | Should -Contain 'com/asdasfa/jbs2bg/MainController.java'
        $script:Scope.ExcludedSources | Should -Contain 'com/asdasfa/jbs2bg/controlsfx/table/TableFilter.java'
        $script:Scope.ExcludedSources | Should -Not -Contain 'com/asdasfa/jbs2bg/project/Project.java'
    }

    It 'never claims to be the complete application gate while any source is excluded' {
        $script:Scope.ExcludedSources.Count | Should -BeGreaterThan 0
        $script:Scope.CompleteApplicationGate | Should -BeFalse
    }
}

Describe 'Format-TransitionalReport' {
    It 'states plainly that the run is not the complete application gate and counts the exclusions' {
        $scope = Get-TransitionalSourceScope -RepoRoot $script:RepoRoot
        $report = (Format-TransitionalReport -Scope $scope) -join "`n"
        $report | Should -Match 'NOT the complete application gate'
        $report | Should -Match "$($scope.ExcludedSources.Count) source file"
        $report | Should -Match 'MainController\.java'
    }
}

Describe 'Get-PomProperty' {
    It 'reads a property from the committed pom.xml' {
        Get-PomProperty -RepoRoot $script:RepoRoot -Name 'maven.compiler.release' | Should -Be '25'
    }

    It 'returns the pinned toolchain runtime version that Surefire forwards to the guard test' {
        $lock = Get-ToolchainLock -Path $script:LockPath
        Get-PomProperty -RepoRoot $script:RepoRoot -Name 'bs2bg.toolchain.jdk.runtimeVersion' | Should -Be $lock.jdk.release.JAVA_RUNTIME_VERSION
    }

    It 'fails closed when the property is absent' {
        { Get-PomProperty -RepoRoot $script:RepoRoot -Name 'bs2bg.does.not.exist' } | Should -Throw -ExpectedMessage '*bs2bg.does.not.exist*'
    }
}

Describe 'Get-SurefireSummary' {
    BeforeAll {
        $reports = Join-Path $TestDrive 'surefire\target\surefire-reports'
        New-Item -ItemType Directory -Path $reports -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $reports 'TEST-a.ATest.xml') -Value '<?xml version="1.0"?><testsuite name="a.ATest" tests="3" failures="1" errors="0" skipped="0"><properties><property name="java.vendor" value="Eclipse Adoptium"/><property name="java.runtime.version" value="25.0.4.1+1-LTS"/></properties></testsuite>'
        Set-Content -LiteralPath (Join-Path $reports 'TEST-b.BTest.xml') -Value '<?xml version="1.0"?><testsuite name="b.BTest" tests="4" failures="0" errors="0" skipped="2"><properties><property name="java.vendor" value="Eclipse Adoptium"/><property name="java.runtime.version" value="25.0.4.1+1-LTS"/></properties></testsuite>'
        Set-Content -LiteralPath (Join-Path $reports 'a.ATest.txt') -Value 'not xml'
        $script:SurefireRoot = Join-Path $TestDrive 'surefire'
    }

    It 'sums tests, failures, errors, and skips across every TEST-*.xml report' {
        $summary = Get-SurefireSummary -RepoRoot $script:SurefireRoot
        $summary.suites | Should -Be 2
        $summary.tests | Should -Be 7
        $summary.failures | Should -Be 1
        $summary.errors | Should -Be 0
        $summary.skipped | Should -Be 2
    }

    It 'reports the JVM vendor and runtime version the forked test JVM actually observed' {
        $summary = Get-SurefireSummary -RepoRoot $script:SurefireRoot
        $summary.observedJavaVendor | Should -Be @('Eclipse Adoptium')
        $summary.observedJavaRuntimeVersion | Should -Be @('25.0.4.1+1-LTS')
    }

    It 'fails closed when no reports exist' {
        { Get-SurefireSummary -RepoRoot (Join-Path $TestDrive 'no-reports') } | Should -Throw -ExpectedMessage '*surefire-reports*'
    }
}
