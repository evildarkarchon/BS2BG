[CmdletBinding()]
param(
    [ValidatePattern('^\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?$')]
    [string]$Version = '1.0.0',

    [ValidateSet('win-x64')]
    [string]$Runtime = 'win-x64'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

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

$projectPath = Assert-InRepo -Path (Join-Path $repoRoot 'src\BS2BG.App\BS2BG.App.csproj')
$artifactName = "BS2BG-v$Version-$Runtime"
$artifactsDir = Assert-InRepo -Path (Join-Path $repoRoot 'artifacts\release')
$workDir = Assert-InRepo -Path (Join-Path $artifactsDir "work-$artifactName")
$publishDir = Assert-InRepo -Path (Join-Path $workDir 'publish')
$packageDir = Assert-InRepo -Path (Join-Path $workDir $artifactName)
$zipPath = Assert-InRepo -Path (Join-Path $artifactsDir "$artifactName.zip")
$zipHashPath = Assert-InRepo -Path "$zipPath.sha256"

Remove-InRepoPath -Path $workDir
Remove-InRepoPath -Path $zipPath
Remove-InRepoPath -Path $zipHashPath
New-Item -ItemType Directory -Force -Path $publishDir, $packageDir, $artifactsDir | Out-Null

$publishArgs = @(
    'publish', $projectPath,
    '-c', 'Release',
    '-r', $Runtime,
    '--self-contained', 'true',
    '-o', $publishDir,
    '-p:PublishSingleFile=true',
    '-p:IncludeNativeLibrariesForSelfExtract=true',
    '-p:DebugType=embedded',
    '-p:EnableCompressionInSingleFile=true'
)

& dotnet @publishArgs
if ($LASTEXITCODE -ne 0) {
    throw "dotnet publish failed with exit code $LASTEXITCODE"
}

Get-ChildItem -LiteralPath $publishDir -Force |
    Where-Object { $_.Extension -ne '.pdb' } |
    Copy-Item -Destination $packageDir -Recurse -Force

Copy-RequiredFile -Source (Join-Path $repoRoot 'settings.json') -Destination (Join-Path $packageDir 'settings.json')
Copy-RequiredFile -Source (Join-Path $repoRoot 'settings_UUNP.json') -Destination (Join-Path $packageDir 'settings_UUNP.json')
Copy-RequiredFile -Source (Join-Path $repoRoot 'assets\res\icon.png') -Destination (Join-Path $packageDir 'assets\res\icon.png')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\README.md') -Destination (Join-Path $packageDir 'README.md')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\CREDITS.md') -Destination (Join-Path $packageDir 'CREDITS.md')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\RELEASE-NOTES-v1.0.0.md') -Destination (Join-Path $packageDir 'RELEASE-NOTES.md')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\UNSIGNED-BUILD.md') -Destination (Join-Path $packageDir 'UNSIGNED-BUILD.md')
Copy-RequiredFile -Source (Join-Path $repoRoot 'docs\release\QA-CHECKLIST.md') -Destination (Join-Path $packageDir 'QA-CHECKLIST.md')

$checksumPath = Join-Path $packageDir 'SHA256SUMS.txt'
$packageFiles = Get-ChildItem -LiteralPath $packageDir -Recurse -File |
    Where-Object { $_.FullName -ne $checksumPath } |
    Sort-Object FullName

$checksumLines = foreach ($file in $packageFiles) {
    $relativePath = [System.IO.Path]::GetRelativePath($packageDir, $file.FullName).Replace('\', '/')
    '{0} *{1}' -f (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant(), $relativePath
}

Set-Content -LiteralPath $checksumPath -Value $checksumLines -Encoding utf8

Compress-Archive -Path (Join-Path $packageDir '*') -DestinationPath $zipPath -CompressionLevel Optimal
$zipHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $zipPath).Hash.ToLowerInvariant()
Set-Content -LiteralPath $zipHashPath -Value ("$zipHash *$artifactName.zip") -Encoding ascii

[PSCustomObject]@{
    Version = $Version
    Runtime = $Runtime
    PackageDirectory = $packageDir
    Zip = $zipPath
    ZipSha256 = $zipHashPath
}
