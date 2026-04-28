using System.IO.Compression;
using FluentAssertions;
using Xunit;

namespace BS2BG.Tests;

/// <summary>
/// Verifies release trust contracts that keep signed and unsigned packages checksum-backed and safe to inspect.
/// </summary>
public sealed class ReleaseTrustTests
{
    private static readonly string[] RequiredPackageEntries =
    [
        "BS2BG.App.exe",
        "BS2BG.Cli.exe",
        "README.md",
        "UNSIGNED-BUILD.md",
        "QA-CHECKLIST.md",
        "SIGNING-INFO.txt",
        "SHA256SUMS.txt",
        "settings_FO4_CBBE.json"
    ];

    /// <summary>
    /// Ensures the packaging script creates both packaged and external SHA-256 verification artifacts.
    /// </summary>
    [Fact]
    public void ReleaseTrustScriptCreatesSha256PackageAndSidecarArtifacts()
    {
        var script = ReadRepoFile("tools", "release", "package-release.ps1");

        script.Should().Contain("SHA256SUMS.txt");
        script.Should().Contain(".zip.sha256");
        script.Should().Contain("Get-FileHash -Algorithm SHA256");
        script.Should().NotContain("Get-FileHash -Algorithm MD5");
        script.Should().NotContain("Get-FileHash -Algorithm SHA1");
    }

    /// <summary>
    /// Ensures SignTool support is opt-in and unsigned packages remain documented as checksum-verifiable.
    /// </summary>
    [Fact]
    public void ReleaseTrustTreatsSignToolAsOptionalAndDocumentsUnsignedVerification()
    {
        var script = ReadRepoFile("tools", "release", "package-release.ps1");
        var unsignedDocs = ReadRepoFile("docs", "release", "UNSIGNED-BUILD.md");

        script.Should().Contain("SignToolPath");
        script.Should().Contain("CertificateSubject");
        script.Should().Contain("CertificatePath");
        script.Should().Contain("CertificatePasswordEnvVar");
        script.Should().Contain("SIGNING-INFO.txt");
        script.Should().Contain("Unsigned");
        script.Should().NotContain("throw \"SignTool");
        unsignedDocs.Should().Contain("unsigned", Exactly.Once());
        unsignedDocs.Should().Contain("SHA-256");
        unsignedDocs.Should().Contain("valid", "unsigned artifacts are valid when SHA-256 verification succeeds");
    }

    /// <summary>
    /// Ensures release signing metadata cannot expose certificate secrets or full private certificate paths.
    /// </summary>
    [Fact]
    public void ReleaseTrustRedactsSigningSecretsAndCertificatePaths()
    {
        var script = ReadRepoFile("tools", "release", "package-release.ps1");

        script.Should().Contain("CertificatePasswordEnvVar");
        script.Should().Contain("GetFileName", "SIGNING-INFO should record only a certificate filename when a file is used");
        script.Should().NotContain("CertificatePassword =");
        script.Should().NotContain("Write-Host $certificatePassword");
        script.Should().NotContain("Write-Output $certificatePassword");
        script.Should().NotContain("SIGNING-INFO.txt') -Value $CertificatePath");
    }

    /// <summary>
    /// Ensures zip inspection rejects unsafe paths before a release package can be trusted.
    /// </summary>
    [Fact]
    public void ReleaseTrustZipInspectionRejectsAbsoluteBackslashAndDuplicateEntries()
    {
        using var stream = new MemoryStream();
        using (var archive = new ZipArchive(stream, ZipArchiveMode.Create, leaveOpen: true))
        {
            archive.CreateEntry("README.md");
            archive.CreateEntry("folder\\unsafe.txt");
            archive.CreateEntry("README.md");
        }

        stream.Position = 0;
        using var readArchive = new ZipArchive(stream, ZipArchiveMode.Read);

        var act = () => InspectPackageEntries(readArchive, ["README.md"]);

        act.Should().Throw<InvalidDataException>()
            .WithMessage("*backslash*");
    }

    /// <summary>
    /// Inspects a generated package zip for required files and path-safe normalized entries.
    /// </summary>
    [Fact(Skip = "ReleaseSmoke: runs the release packaging script and inspects generated artifacts on demand.")]
    [Trait("Category", "ReleaseSmoke")]
    public void ReleaseTrustSmokePackageContainsRequiredTrustArtifactsAndPathSafeEntries()
    {
        var repoRoot = FindRepoRoot();
        var version = "1.0.0";
        var runtime = "win-x64";
        var scriptPath = Path.Combine(repoRoot, "tools", "release", "package-release.ps1");
        using var process = System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
        {
            FileName = "pwsh",
            ArgumentList = { "-NoProfile", "-File", scriptPath, "-Version", version, "-Runtime", runtime },
            WorkingDirectory = repoRoot,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        });
        process.Should().NotBeNull();

        process!.WaitForExit();
        process.ExitCode.Should().Be(0, process.StandardError.ReadToEnd());

        var zipPath = Path.Combine(repoRoot, "artifacts", "release", $"BS2BG-v{version}-{runtime}.zip");
        File.Exists(zipPath + ".sha256").Should().BeTrue("release packages must publish an external checksum sidecar");
        using var archive = ZipFile.OpenRead(zipPath);

        InspectPackageEntries(archive, RequiredPackageEntries);
    }

    /// <summary>
    /// Ensures extracted CLI launch smoke is release-gated because it builds and executes packaged binaries.
    /// </summary>
    [Fact(Skip = "ReleaseSmoke: extracts a release package and runs BS2BG.Cli.exe --help on demand.")]
    [Trait("Category", "ReleaseSmoke")]
    public void ReleaseTrustSmokeExtractedCliHelpRunsOnlyInReleaseSmoke()
    {
        var repoRoot = FindRepoRoot();
        var zipPath = Path.Combine(repoRoot, "artifacts", "release", "BS2BG-v1.0.0-win-x64.zip");
        var extractDir = Path.Combine(repoRoot, "artifacts", "test-out", "release-trust-cli-help");
        if (Directory.Exists(extractDir)) Directory.Delete(extractDir, recursive: true);
        ZipFile.ExtractToDirectory(zipPath, extractDir);

        using var process = System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
        {
            FileName = Path.Combine(extractDir, "BS2BG.Cli.exe"),
            ArgumentList = { "--help" },
            WorkingDirectory = extractDir,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        });
        process.Should().NotBeNull();

        process!.WaitForExit();
        process.ExitCode.Should().Be(0, process.StandardError.ReadToEnd());
        process.StandardOutput.ReadToEnd().Should().Contain("generate");
    }

    /// <summary>
    /// Validates that package entries are normalized, unique, relative paths and that required files are present.
    /// </summary>
    private static void InspectPackageEntries(ZipArchive archive, IReadOnlyCollection<string> requiredEntries)
    {
        var names = archive.Entries.Select(entry => entry.FullName).ToArray();
        foreach (var name in names)
        {
            if (Path.IsPathRooted(name) || name.StartsWith('/') || name.Contains(':'))
                throw new InvalidDataException($"Release zip entry must be relative: {name}");
            if (name.Contains('\\'))
                throw new InvalidDataException($"Release zip entry must use forward slashes, not backslash: {name}");
            if (name.Split('/').Any(segment => segment is "" or "." or ".."))
                throw new InvalidDataException($"Release zip entry contains an unsafe path segment: {name}");
        }

        var duplicates = names.GroupBy(name => name, StringComparer.OrdinalIgnoreCase)
            .Where(group => group.Count() > 1)
            .Select(group => group.Key)
            .ToArray();
        if (duplicates.Length > 0)
            throw new InvalidDataException($"Release zip contains duplicate entries: {string.Join(", ", duplicates)}");

        names.Should().Contain(requiredEntries);
    }

    /// <summary>
    /// Reads a UTF-8 repository file for source and documentation contract assertions.
    /// </summary>
    private static string ReadRepoFile(params string[] segments)
    {
        return File.ReadAllText(Path.Combine([FindRepoRoot(), .. segments]));
    }

    /// <summary>
    /// Finds the repository root from the test output directory.
    /// </summary>
    private static string FindRepoRoot()
    {
        var directory = AppContext.BaseDirectory;
        while (directory is not null)
        {
            if (File.Exists(Path.Combine(directory, "BS2BG.sln"))) return directory;
            directory = Directory.GetParent(directory)?.FullName;
        }

        throw new DirectoryNotFoundException("Could not find repository root.");
    }
}
