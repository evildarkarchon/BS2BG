[CmdletBinding()]
param(
    [ValidatePattern('^\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?$')]
    [string]$Version = '1.0.0',

    [ValidateSet('win-x64')]
    [string]$Runtime = 'win-x64',

    [string]$SignToolPath,

    [string]$CertificateSubject,

    [string]$CertificatePath,

    [string]$CertificatePasswordEnvVar
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Add-Type -AssemblyName System.IO.Compression.FileSystem

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$repoRootWithSeparator = $repoRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar

function Assert-InRepo {
    param([Parameter(Mandatory)][string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if ($fullPath -ne $repoRoot -and -not $fullPath.StartsWith($repoRootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to operate outside the repository: $fullPath"
    }

    return $fullPath
}

function Remove-InRepoPath {
    param([Parameter(Mandatory)][string]$Path)

    $fullPath = Assert-InRepo -Path $Path
    if (Test-Path -LiteralPath $fullPath) {
        Remove-Item -LiteralPath $fullPath -Recurse -Force
    }
}

function Copy-RequiredFile {
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Destination
    )

    $sourcePath = Assert-InRepo -Path $Source
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "Required release input is missing: $sourcePath"
    }

    $destinationPath = Assert-InRepo -Path $Destination
    $destinationDirectory = Split-Path -Parent $destinationPath
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    Copy-Item -LiteralPath $sourcePath -Destination $destinationPath -Force
}

function Publish-ReleaseProject {
    param(
        [Parameter(Mandatory)][string]$ProjectPath,
        [Parameter(Mandatory)][string]$OutputDirectory
    )

    $publishArgs = @(
        'publish', $ProjectPath,
        '-c', 'Release',
        '-r', $Runtime,
        '--self-contained', 'true',
        '-o', $OutputDirectory,
        '-p:PublishSingleFile=true',
        '-p:IncludeNativeLibrariesForSelfExtract=true',
        '-p:DebugType=embedded',
        '-p:EnableCompressionInSingleFile=true'
    )

    & dotnet @publishArgs
    if ($LASTEXITCODE -ne 0) {
        throw "dotnet publish failed for $ProjectPath with exit code $LASTEXITCODE"
    }
}

function Copy-PublishedFiles {
    param(
        [Parameter(Mandatory)][string]$SourceDirectory,
        [Parameter(Mandatory)][string]$DestinationDirectory
    )

    Get-ChildItem -LiteralPath $SourceDirectory -Force |
        Where-Object { $_.Extension -ne '.pdb' } |
        Copy-Item -Destination $DestinationDirectory -Recurse -Force
}

function Resolve-SignTool {
    param([string]$ConfiguredPath)

    if (-not [string]::IsNullOrWhiteSpace($ConfiguredPath)) {
        $candidate = [System.IO.Path]::GetFullPath($ConfiguredPath)
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        return $null
    }

    $command = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }

    return $null
}

function Invoke-OptionalSigning {
    param(
        [Parameter(Mandatory)][string[]]$ExecutablePaths,
        [Parameter(Mandatory)][string]$SigningInfoPath
    )

    $signingConfigured = -not [string]::IsNullOrWhiteSpace($CertificateSubject) -or -not [string]::IsNullOrWhiteSpace($CertificatePath)
    $resolvedSignToolPath = Resolve-SignTool -ConfiguredPath $SignToolPath
    $certificateFileName = if (-not [string]::IsNullOrWhiteSpace($CertificatePath)) { [System.IO.Path]::GetFileName($CertificatePath) } else { $null }
    $passwordValue = if (-not [string]::IsNullOrWhiteSpace($CertificatePasswordEnvVar)) { [Environment]::GetEnvironmentVariable($CertificatePasswordEnvVar) } else { $null }

    $signingInfo = [System.Collections.Generic.List[string]]::new()
    $signingInfo.Add("Status: Unsigned")
    $signingInfo.Add("Reason: Signing was not configured.")
    $signingInfo.Add("SignTool: Not required")

    if ($signingConfigured -and [string]::IsNullOrWhiteSpace($resolvedSignToolPath)) {
        $signingInfo.Clear()
        $signingInfo.Add("Status: Unsigned")
        $signingInfo.Add("Reason: Signing was configured but SignTool was not available; verify SHA-256 checksums before use.")
        $signingInfo.Add("SignTool: Unavailable")
        if (-not [string]::IsNullOrWhiteSpace($CertificateSubject)) { $signingInfo.Add("CertificateSubject: $CertificateSubject") }
        if (-not [string]::IsNullOrWhiteSpace($certificateFileName)) { $signingInfo.Add("CertificateFile: $certificateFileName") }
        Set-Content -LiteralPath $SigningInfoPath -Value $signingInfo -Encoding utf8
        return
    }

    if (-not $signingConfigured) {
        Set-Content -LiteralPath $SigningInfoPath -Value $signingInfo -Encoding utf8
        return
    }

    foreach ($executablePath in $ExecutablePaths) {
        $signArgs = @('sign', '/fd', 'SHA256', '/tr', 'http://timestamp.digicert.com', '/td', 'SHA256')
        if (-not [string]::IsNullOrWhiteSpace($CertificateSubject)) {
            $signArgs += @('/n', $CertificateSubject)
        }
        if (-not [string]::IsNullOrWhiteSpace($CertificatePath)) {
            $signArgs += @('/f', $CertificatePath)
        }
        if (-not [string]::IsNullOrWhiteSpace($passwordValue)) {
            $signArgs += @('/p', $passwordValue)
        }
        $signArgs += $executablePath

        & $resolvedSignToolPath @signArgs | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "SignTool signing failed for $([System.IO.Path]::GetFileName($executablePath)) with exit code $LASTEXITCODE"
        }

        & $resolvedSignToolPath @('verify', '/pa', $executablePath) | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "SignTool verification failed for $([System.IO.Path]::GetFileName($executablePath)) with exit code $LASTEXITCODE"
        }
    }

    $signingInfo.Clear()
    $signingInfo.Add("Status: Signed")
    $signingInfo.Add("SignTool: Available")
    $signingInfo.Add("VerificationCommand: signtool verify /pa <executable>")
    if (-not [string]::IsNullOrWhiteSpace($CertificateSubject)) { $signingInfo.Add("CertificateSubject: $CertificateSubject") }
    if (-not [string]::IsNullOrWhiteSpace($certificateFileName)) { $signingInfo.Add("CertificateFile: $certificateFileName") }
    Set-Content -LiteralPath $SigningInfoPath -Value $signingInfo -Encoding utf8
}

function Assert-PackagePathSafety {
    param([Parameter(Mandatory)][System.IO.FileInfo]$File)

    $relativePath = [System.IO.Path]::GetRelativePath($packageDir, $File.FullName)
    if ([System.IO.Path]::IsPathRooted($relativePath) -or $relativePath.StartsWith('..', [System.StringComparison]::Ordinal)) {
        throw "Package file resolved outside package directory: $($File.FullName)"
    }

    return $relativePath.Replace('\', '/')
}

function Assert-RequiredPackageFile {
    param([Parameter(Mandatory)][string]$RelativePath)

    $candidate = Join-Path $packageDir $RelativePath
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "Required package file is missing: $RelativePath"
    }
}

function New-NormalizedZipArchive {
    param(
        [Parameter(Mandatory)][string]$SourceDirectory,
        [Parameter(Mandatory)][string]$DestinationZip
    )

    $usedEntries = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $files = Get-ChildItem -LiteralPath $SourceDirectory -Recurse -File | Sort-Object FullName
    $zipDirectory = Split-Path -Parent $DestinationZip
    New-Item -ItemType Directory -Force -Path $zipDirectory | Out-Null

    $zipStream = [System.IO.File]::Open($DestinationZip, [System.IO.FileMode]::CreateNew)
    try {
        $archive = [System.IO.Compression.ZipArchive]::new($zipStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
        try {
            foreach ($file in $files) {
                $entryName = [System.IO.Path]::GetRelativePath($SourceDirectory, $file.FullName).Replace('\', '/')
                if ([System.IO.Path]::IsPathRooted($entryName) -or $entryName.Contains(':') -or $entryName.Contains('..')) {
                    throw "Unsafe zip entry name: $entryName"
                }
                if (-not $usedEntries.Add($entryName)) {
                    throw "Duplicate zip entry name: $entryName"
                }

                [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                    $archive,
                    $file.FullName,
                    $entryName,
                    [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
            }
        }
        finally {
            $archive.Dispose()
        }
    }
    finally {
        $zipStream.Dispose()
    }
}

function Assert-ZipEntriesAreSafe {
    param([Parameter(Mandatory)][string]$Path)

    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $names = $archive.Entries | ForEach-Object { $_.FullName }
        foreach ($name in $names) {
            if ([System.IO.Path]::IsPathRooted($name) -or $name.StartsWith('/') -or $name.Contains(':')) {
                throw "Release zip entry must be relative: $name"
            }
            if ($name.Contains('\')) {
                throw "Release zip entry must use forward slashes, not backslash: $name"
            }
            if (($name -split '/') | Where-Object { $_ -eq '..' -or $_ -eq '.' -or $_ -eq '' }) {
                throw "Release zip entry contains an unsafe path segment: $name"
            }
        }

        $duplicates = $names | Group-Object | Where-Object { $_.Count -gt 1 }
        if ($duplicates) {
            throw "Release zip contains duplicate entries: $($duplicates[0].Name)"
        }
    }
    finally {
        $archive.Dispose()
    }
}

$appProjectPath = Assert-InRepo -Path (Join-Path $repoRoot 'src\BS2BG.App\BS2BG.App.csproj')
$cliProjectPath = Assert-InRepo -Path (Join-Path $repoRoot 'src\BS2BG.Cli\BS2BG.Cli.csproj')
$artifactName = "BS2BG-v$Version-$Runtime"
$artifactsDir = Assert-InRepo -Path (Join-Path $repoRoot 'artifacts\release')
$workDir = Assert-InRepo -Path (Join-Path $artifactsDir "work-$artifactName")
$appPublishDir = Assert-InRepo -Path (Join-Path $workDir 'publish-app')
$cliPublishDir = Assert-InRepo -Path (Join-Path $workDir 'publish-cli')
$packageDir = Assert-InRepo -Path (Join-Path $workDir $artifactName)
$zipPath = Assert-InRepo -Path (Join-Path $artifactsDir "$artifactName.zip")
$zipHashPath = Assert-InRepo -Path (Join-Path $artifactsDir "$artifactName.zip.sha256")

Remove-InRepoPath -Path $workDir
Remove-InRepoPath -Path $zipPath
Remove-InRepoPath -Path $zipHashPath
New-Item -ItemType Directory -Force -Path $appPublishDir, $cliPublishDir, $packageDir, $artifactsDir | Out-Null

Publish-ReleaseProject -ProjectPath $appProjectPath -OutputDirectory $appPublishDir
Publish-ReleaseProject -ProjectPath $cliProjectPath -OutputDirectory $cliPublishDir

Copy-PublishedFiles -SourceDirectory $appPublishDir -DestinationDirectory $packageDir
Copy-PublishedFiles -SourceDirectory $cliPublishDir -DestinationDirectory $packageDir

Copy-RequiredFile -Source (Join-Path $repoRoot 'settings.json') -Destination (Join-Path $packageDir 'settings.json')
Copy-RequiredFile -Source (Join-Path $repoRoot 'settings_UUNP.json') -Destination (Join-Path $packageDir 'settings_UUNP.json')
Copy-RequiredFile -Source (Join-Path $repoRoot 'settings_FO4_CBBE.json') -Destination (Join-Path $packageDir 'settings_FO4_CBBE.json')
Copy-RequiredFile -Source (Join-Path $repoRoot 'assets\res\icon.png') -Destination (Join-Path $packageDir 'assets\res\icon.png')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\README.md') -Destination (Join-Path $packageDir 'README.md')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\CREDITS.md') -Destination (Join-Path $packageDir 'CREDITS.md')
Copy-RequiredFile -Source (Join-Path $repoRoot "docs\release\RELEASE-NOTES-v$Version.md") -Destination (Join-Path $packageDir 'RELEASE-NOTES.md')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\UNSIGNED-BUILD.md') -Destination (Join-Path $packageDir 'UNSIGNED-BUILD.md')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\QA-CHECKLIST.md') -Destination (Join-Path $packageDir 'QA-CHECKLIST.md')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\BODYGEN-BODYSLIDE-BOS-SETUP.md') -Destination (Join-Path $packageDir 'BODYGEN-BODYSLIDE-BOS-SETUP.md')

$signingInfoPath = Join-Path $packageDir 'SIGNING-INFO.txt'
Invoke-OptionalSigning -ExecutablePaths @(
    (Join-Path $packageDir 'BS2BG.App.exe'),
    (Join-Path $packageDir 'BS2BG.Cli.exe')
) -SigningInfoPath $signingInfoPath

foreach ($requiredFile in @(
        'BS2BG.App.exe',
        'BS2BG.Cli.exe',
        'settings.json',
        'settings_UUNP.json',
        'settings_FO4_CBBE.json',
        'README.md',
        'UNSIGNED-BUILD.md',
        'QA-CHECKLIST.md',
        'BODYGEN-BODYSLIDE-BOS-SETUP.md',
        'SIGNING-INFO.txt')) {
    Assert-RequiredPackageFile -RelativePath $requiredFile
}

$checksumPath = Join-Path $packageDir 'SHA256SUMS.txt'
$packageFiles = Get-ChildItem -LiteralPath $packageDir -Recurse -File |
    Where-Object { $_.FullName -ne $checksumPath } |
    Sort-Object FullName

$checksumLines = foreach ($file in $packageFiles) {
    $relativePath = Assert-PackagePathSafety -File $file
    '{0} *{1}' -f (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant(), $relativePath
}

if ($checksumLines.Count -eq 0) {
    throw 'No package files were found for SHA256SUMS.txt.'
}

Set-Content -LiteralPath $checksumPath -Value $checksumLines -Encoding utf8
Assert-RequiredPackageFile -RelativePath 'SHA256SUMS.txt'

New-NormalizedZipArchive -SourceDirectory $packageDir -DestinationZip $zipPath
Assert-ZipEntriesAreSafe -Path $zipPath

$zipHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $zipPath).Hash.ToLowerInvariant()
Set-Content -LiteralPath $zipHashPath -Value ("$zipHash *$artifactName.zip") -Encoding ascii

[PSCustomObject]@{
    Version = $Version
    Runtime = $Runtime
    PackageDirectory = $packageDir
    Zip = $zipPath
    ZipSha256 = $zipHashPath
    SigningInfo = $signingInfoPath
}
