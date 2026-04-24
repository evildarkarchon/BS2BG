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
        Assert.NotNull(method);

        var directories = Assert.IsAssignableFrom<IEnumerable<DirectoryInfo>>(method.Invoke(null, null));

        var fullNames = directories
            .Select(directory => directory.FullName)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();

        Assert.Equal(new[] { new DirectoryInfo(AppContext.BaseDirectory).FullName }, fullNames);
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

        var exception = Assert.Throws<FileNotFoundException>(
            () => TemplateProfileCatalogFactory.CreateDefault(new[] { directory.Path }));

        Assert.Contains("settings_UUNP.json", exception.Message, StringComparison.Ordinal);
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void WriteFile(string fileName, string contents)
        {
            File.WriteAllText(System.IO.Path.Combine(Path, fileName), contents);
        }

        public void Dispose()
        {
            Directory.Delete(Path, recursive: true);
        }
    }
}
