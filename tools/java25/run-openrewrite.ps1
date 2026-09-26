#Requires -Version 5.1
<#
.SYNOPSIS
    Runs the pinned OpenRewrite Java 25 migration recipe on the repository's verified JDK 25 host.

.DESCRIPTION
    Provisions and verifies the Temurin JDK from tools/java25/toolchain-lock.json, makes that JDK both Maven's
    host runtime and the repository toolchain, then activates the opt-in openrewrite Maven profile. OpenRewrite
    chooses its Java parser from Maven's host JVM, so selecting only .mvn/toolchains.xml is not sufficient.

    The default dryRun goal does not edit sources. The run goal applies changes to the working tree and should be
    followed by a diff review and tools/java25/verify-java25.ps1.

.PARAMETER Goal
    OpenRewrite goal to execute: dryRun previews a patch, run applies it, and discover lists available recipes.

.PARAMETER CacheRoot
    Directory that holds the provisioned JDK. Defaults to BS2BG_TOOLCHAIN_CACHE, then
    %LOCALAPPDATA%\BS2BG\build-toolchains.

.EXAMPLE
    .\tools\java25\run-openrewrite.ps1

.EXAMPLE
    .\tools\java25\run-openrewrite.ps1 -Goal run
#>
[CmdletBinding()]
param(
    [ValidateSet('dryRun', 'run', 'discover')]
    [string]$Goal = 'dryRun',
    [string]$CacheRoot = $(if ($env:BS2BG_TOOLCHAIN_CACHE) { $env:BS2BG_TOOLCHAIN_CACHE } else { Join-Path $env:LOCALAPPDATA 'BS2BG\build-toolchains' })
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Import-Module (Join-Path $PSScriptRoot 'Java25Toolchain.psm1') -Force

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$originalJavaHome = $env:JAVA_HOME
$originalToolchainHome = $env:BS2BG_JDK25_HOME
$exitCode = 0

try {
    Write-Step 'Checking the OpenRewrite host and pinned inputs'
    $lock = Get-ToolchainLock
    if ($env:OS -ne 'Windows_NT') {
        throw "The Java 25 baseline is pinned to $($lock.architecture.os) x64; this host is not Windows."
    }
    if ($env:PROCESSOR_ARCHITECTURE -ne $lock.architecture.processorArchitecture) {
        throw "The Java 25 baseline is pinned to $($lock.architecture.processorArchitecture); this host reports PROCESSOR_ARCHITECTURE=$($env:PROCESSOR_ARCHITECTURE)."
    }

    $wrapperProperties = Read-PropertiesFile -Path (Join-Path $repoRoot '.mvn\wrapper\maven-wrapper.properties')
    Assert-MavenWrapperPinned -Properties $wrapperProperties -MavenVersion $lock.maven.version -DistributionSha256 $lock.maven.distributionSha256

    Write-Step "Provisioning the OpenRewrite host JDK: $($lock.jdk.release.IMPLEMENTOR_VERSION)"
    $jdkHome = Install-LockedArchive -Url $lock.jdk.url -Sha256 $lock.jdk.sha256 -Destination (Join-Path $CacheRoot $lock.jdk.id) -ExpectedRootDirectory $lock.jdk.archiveRootDirectory -Label 'Temurin JDK'
    $release = Read-JdkReleaseFile -Path (Join-Path $jdkHome 'release')
    Assert-JdkRelease -Release $release -Expected $lock.jdk.release
    $javaVersion = Invoke-Native -FilePath (Join-Path $jdkHome 'bin\java.exe') -ArgumentList @('-version') -Label 'java -version'
    if (-not ($javaVersion -join "`n").Contains($lock.jdk.release.IMPLEMENTOR_VERSION)) {
        throw "java -version does not report $($lock.jdk.release.IMPLEMENTOR_VERSION):`n$($javaVersion -join "`n")"
    }

    # Rewrite selects Java25Parser from Maven's host JVM; the compiler toolchain alone cannot control that choice.
    $env:JAVA_HOME = $jdkHome
    $env:BS2BG_JDK25_HOME = $jdkHome

    Write-Step "Running OpenRewrite goal '$Goal' with org.openrewrite.java.migrate.UpgradeToJava25"
    Push-Location $repoRoot
    try {
        $mavenArguments = @(
            '--batch-mode',
            '--strict-checksums',
            '--show-version',
            '-Dstyle.color=never',
            '-Popenrewrite',
            "rewrite:$Goal"
        )
        if ($Goal -eq 'discover') {
            # The recipe module exposes thousands of descriptors; focus discovery on the configured migration.
            $mavenArguments += @(
                '-Ddetail=true',
                '-Drecipe=org.openrewrite.java.migrate.UpgradeToJava25'
            )
        }
        Write-Host "mvnw.cmd $($mavenArguments -join ' ')"
        & (Join-Path $repoRoot 'mvnw.cmd') @mavenArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Maven Wrapper exited with code $LASTEXITCODE; see the OpenRewrite log above."
        }
    }
    finally {
        Pop-Location
    }
}
catch {
    $exitCode = 1
    Write-Host ''
    Write-Host "OPENREWRITE FAILED: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ScriptStackTrace) {
        Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray
    }
}
finally {
    # Environment variables are process-wide even when a script runs in a child scope, so restore the caller's state.
    $env:JAVA_HOME = $originalJavaHome
    $env:BS2BG_JDK25_HOME = $originalToolchainHome
}

exit $exitCode
