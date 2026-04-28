using System.IO.Compression;
using System.Text.RegularExpressions;
using FluentAssertions;
using Xunit;

namespace BS2BG.Tests;

/// <summary>
/// Verifies that packaged release documentation carries BodyGen, BodySlide, BoS setup guidance without adding in-app guidance UI.
/// </summary>
public sealed partial class ReleaseDocsTests
{
    private const string SetupGuidePackageName = "BODYGEN-BODYSLIDE-BOS-SETUP.md";
    private const string NoPluginEditingBoundary = "BS2BG generates files and setup guidance; it does not edit game plugins.";

    /// <summary>
    /// Ensures the packaged setup guide covers the external-tool concepts and trust boundary users need before copying outputs.
    /// </summary>
    [Fact]
    public void PackagedSetupGuideContainsBodyGenBodySlideBosTroubleshootingAndBoundaryText()
    {
        var guide = ReadRepoFile("docs", "release", SetupGuidePackageName);

        guide.Should().Contain("BodyGen");
        guide.Should().Contain("BodySlide");
        guide.Should().Contain("BodyTypes of Skyrim");
        guide.Should().Contain("output location");
        guide.Should().Contain("Last verified:");
        guide.Should().Contain(NoPluginEditingBoundary);
        LastVerifiedLineRegex().IsMatch(guide).Should().BeTrue("the verification date should be refreshed as a YYYY-MM-DD release-time token");
    }

    /// <summary>
    /// Ensures release packaging includes the setup guide while heavyweight package inspection stays behind the ReleaseSmoke gate.
    /// </summary>
    [Fact]
    public void PackageScriptCopiesSetupGuideAndDefaultDocsTestsDoNotPublishPackage()
    {
        var script = ReadRepoFile("tools", "release", "package-release.ps1");
        var testSource = ReadRepoFile("tests", "BS2BG.Tests", "ReleaseDocsTests.cs");

        script.Should().Contain(SetupGuidePackageName);
        script.Should().Contain("Copy-RequiredFile");
        script.Should().Contain("Assert-RequiredPackageFile");
        testSource.Should().Contain("ReleaseDocsSmokePackageContainsSetupGuideWhenExplicitlyRun");
        testSource.Should().Contain("Trait(\"Category\", \"ReleaseSmoke\")");
        testSource.Should().Contain("Skip = \"ReleaseSmoke:");
    }

    /// <summary>
    /// Ensures Phase 5 keeps setup guidance packaged-only by rejecting dedicated setup wizard and Help-menu wiring patterns.
    /// </summary>
    [Fact]
    public void AppDoesNotAddSetupWizardOrHelpMenuForPackagedGuidance()
    {
        var appSources = EnumerateRepoFiles("src", "BS2BG.App")
            .Where(path => path.EndsWith(".axaml", StringComparison.OrdinalIgnoreCase)
                           || path.EndsWith(".cs", StringComparison.OrdinalIgnoreCase))
            .Select(path => (Path: path, Text: File.ReadAllText(path)))
            .ToArray();

        appSources.Should().NotContain(source => source.Text.Contains("SetupWizard", StringComparison.Ordinal));
        appSources.Should().NotContain(source => source.Text.Contains(SetupGuidePackageName, StringComparison.Ordinal));
        appSources.Should().NotContain(source => HelpSetupMenuRegex().IsMatch(source.Text),
            "setup guidance must remain packaged docs only, not a new Help/Setup menu item");
    }

    /// <summary>
    /// Ensures release QA requires the setup guide date to be re-verified instead of freezing a stale research date.
    /// </summary>
    [Fact]
    public void QaChecklistRequiresRefreshingSetupGuideVerificationDate()
    {
        var qaChecklist = ReadRepoFile("docs", "release", "QA-CHECKLIST.md");

        qaChecklist.Should().Contain(SetupGuidePackageName);
        qaChecklist.Should().Contain("Last verified");
        qaChecklist.Should().Contain("refresh");
    }

    /// <summary>
    /// Generates the release package and verifies the setup guide is present only when the explicit ReleaseSmoke category is run.
    /// </summary>
    [Fact(Skip = "ReleaseSmoke: runs the release packaging script and inspects generated docs artifacts on demand.")]
    [Trait("Category", "ReleaseSmoke")]
    public void ReleaseDocsSmokePackageContainsSetupGuideWhenExplicitlyRun()
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
        using var archive = ZipFile.OpenRead(zipPath);

        archive.Entries.Select(entry => entry.FullName).Should().Contain(SetupGuidePackageName);
    }

    /// <summary>
    /// Reads a UTF-8 repository file for source and documentation contract assertions.
    /// </summary>
    private static string ReadRepoFile(params string[] segments)
    {
        return File.ReadAllText(Path.Combine([FindRepoRoot(), .. segments]));
    }

    /// <summary>
    /// Enumerates files below a repository-relative directory so negative UI checks can inspect concrete source surfaces.
    /// </summary>
    private static IEnumerable<string> EnumerateRepoFiles(params string[] segments)
    {
        return Directory.EnumerateFiles(Path.Combine([FindRepoRoot(), .. segments]), "*", SearchOption.AllDirectories);
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

    [GeneratedRegex(@"^Last verified: \d{4}-\d{2}-\d{2}$", RegexOptions.Multiline)]
    private static partial Regex LastVerifiedLineRegex();

    [GeneratedRegex("<MenuItem[^>]*Header=\\\"[^\\\"]*Setup[^\\\"]*\\\"|Header=\\\"[^\\\"]*Setup[^\\\"]*\\\"[^>]*<MenuItem", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex HelpSetupMenuRegex();
}
