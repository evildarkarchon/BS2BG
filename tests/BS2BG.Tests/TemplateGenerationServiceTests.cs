using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.App.Services;
using Xunit;

namespace BS2BG.Tests;

public sealed class TemplateGenerationServiceTests
{
    [Theory]
    [InlineData("minimal", "minimal.xml", "settings.json")]
    [InlineData("skyrim-cbbe", "CBBE.xml", "settings.json")]
    [InlineData("fallout4-cbbe", "CBBE.xml", "settings.json")]
    [InlineData("skyrim-uunp", "UUNP-synthetic.xml", "settings.json")]
    public void GenerateTemplatesUsesImportedFixtureXmlAndM0Formatter(
        string scenario,
        string xmlFileName,
        string profileFileName)
    {
        var parser = new BodySlideXmlParser();
        var profile = SliderProfileJsonService.Load(ProfilePath(profileFileName));
        var catalog = new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, profile),
        });
        var service = new TemplateGenerationService();
        var import = parser.ParseFile(InputPath(scenario, xmlFileName));

        AssertFixtureText(
            scenario,
            "templates.ini",
            service.GenerateTemplates(import.Presets, catalog, omitRedundantSliders: false));
        AssertFixtureText(
            scenario,
            "templates-omit.ini",
            service.GenerateTemplates(import.Presets, catalog, omitRedundantSliders: true));
    }

    [Fact]
    public void GenerateTemplatesUsesDefaultFallout4ProfileSettings()
    {
        var parser = new BodySlideXmlParser();
        var service = new TemplateGenerationService();
        var import = parser.ParseFile(InputPath("fallout4-cbbe", "CBBE.xml"));
        var catalog = TemplateProfileCatalogFactory.CreateDefault();

        foreach (var preset in import.Presets)
        {
            preset.ProfileName = ProjectProfileMapping.Fallout4Cbbe;
        }

        AssertFixtureText(
            "fallout4-cbbe",
            "templates.ini",
            service.GenerateTemplates(import.Presets, catalog, omitRedundantSliders: false));
        AssertFixtureText(
            "fallout4-cbbe",
            "templates-omit.ini",
            service.GenerateTemplates(import.Presets, catalog, omitRedundantSliders: true));
    }

    private static string ProfilePath(string fileName)
    {
        return Path.Combine(RepositoryRoot, "tests", "fixtures", "inputs", "profiles", fileName);
    }

    private static string InputPath(string scenario, string fileName)
    {
        return Path.Combine(RepositoryRoot, "tests", "fixtures", "inputs", scenario, fileName);
    }

    private static void AssertFixtureText(string scenario, string fileName, string actual)
    {
        var expectedPath = Path.Combine(RepositoryRoot, "tests", "fixtures", "expected", scenario, fileName);
        var expected = File.ReadAllText(expectedPath);

        Assert.Equal(NormalizeNewlines(expected).TrimEnd(), NormalizeNewlines(actual).TrimEnd());
    }

    private static string NormalizeNewlines(string value)
    {
        return value.Replace("\r\n", "\n", StringComparison.Ordinal)
            .Replace("\r", "\n", StringComparison.Ordinal);
    }

    private static string RepositoryRoot
    {
        get
        {
            var directory = new DirectoryInfo(AppContext.BaseDirectory);
            while (directory is not null && !File.Exists(Path.Combine(directory.FullName, "PRD.md")))
            {
                directory = directory.Parent;
            }

            return directory?.FullName
                ?? throw new InvalidOperationException("Could not locate repository root.");
        }
    }
}
