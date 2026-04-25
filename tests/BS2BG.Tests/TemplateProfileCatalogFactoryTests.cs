using System.Reflection;
using BS2BG.App.Services;
using Xunit;

namespace BS2BG.Tests;

public sealed class TemplateProfileCatalogFactoryTests
{
    [Fact]
    public void DefaultCandidateDirectoriesDoNotWalkParentDirectories()
    {
        var method = typeof(TemplateProfileCatalogFactory).GetMethod(
            "CandidateDirectories",
            BindingFlags.NonPublic | BindingFlags.Static);
        method.Should().NotBeNull();

        var directories = method.Invoke(null, null).Should().BeAssignableTo<IEnumerable<DirectoryInfo>>().Which;

        var fullNames = directories
            .Select(directory => directory.FullName)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();

        fullNames.Should().Equal(new DirectoryInfo(AppContext.BaseDirectory).FullName);
    }

    [Fact]
    public void CreateDefaultThrowsWhenRequiredProfileFileIsMissing()
    {
        using var directory = new TemporaryDirectory();
        directory.WriteFile(
            "settings.json",
            """
            {
              "Defaults": {},
              "Multipliers": {},
              "Inverted": []
            }
            """);

        var exception = FluentActions.Invoking(() =>
                TemplateProfileCatalogFactory.CreateDefault(new[] { directory.Path }))
            .Should()
            .ThrowExactly<FileNotFoundException>()
            .Which;

        exception.Message.Should().Contain("settings_UUNP.json");
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose() => Directory.Delete(Path, true);

        public void WriteFile(string fileName, string contents) =>
            File.WriteAllText(System.IO.Path.Combine(Path, fileName), contents);
    }
}
