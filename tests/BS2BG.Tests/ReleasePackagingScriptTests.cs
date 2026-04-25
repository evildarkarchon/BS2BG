using Xunit;

namespace BS2BG.Tests;

public sealed class ReleasePackagingScriptTests
{
    [Fact]
    public void PackageReleaseCopiesReleaseNotesForRequestedVersion()
    {
        var script = File.ReadAllText(Path.Combine(
            FindRepoRoot(),
            "tools",
            "release",
            "package-release.ps1"));

        script.Should().Contain("\"docs\\release\\RELEASE-NOTES-v$Version.md\"");
        script.Should().NotContain("'docs\\release\\RELEASE-NOTES-v1.0.0.md'");
    }

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
