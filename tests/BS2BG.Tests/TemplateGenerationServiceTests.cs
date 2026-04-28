using BS2BG.App.Services;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using Xunit;
using SetSlider = BS2BG.Core.Models.SetSlider;
using SliderPreset = BS2BG.Core.Models.SliderPreset;
using TemplateProfileCatalogFactory = BS2BG.App.Services.TemplateProfileCatalogFactory;

namespace BS2BG.Tests;

public sealed class TemplateGenerationServiceTests
{
    private static string RepositoryRoot
    {
        get
        {
            var directory = new DirectoryInfo(AppContext.BaseDirectory);
            while (directory is not null && !File.Exists(Path.Combine(directory.FullName, "PRD.md")))
                directory = directory.Parent;

            return directory?.FullName
                   ?? throw new InvalidOperationException("Could not locate repository root.");
        }
    }

    [Fact]
    public void GenerateTemplatesUsesCrLfLineEndings()
    {
        var service = new TemplateGenerationService();
        var catalog = new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    Array.Empty<SliderDefault>(),
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
        var beta = CreatePreset("Beta", 50);
        var alpha = CreatePreset("Alpha", 25);

        var actual = service.GenerateTemplates(
            new[] { beta, alpha },
            catalog,
            false);

        actual.Should().Be("Alpha=Scale@0.25\r\nBeta=Scale@0.5");
    }

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
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, profile)
        });
        var service = new TemplateGenerationService();
        var import = parser.ParseFile(InputPath(scenario, xmlFileName));

        AssertFixtureText(
            scenario,
            "templates.ini",
            service.GenerateTemplates(import.Presets, catalog, false));
        AssertFixtureText(
            scenario,
            "templates-omit.ini",
            service.GenerateTemplates(import.Presets, catalog, true));
    }

    [Fact]
    public void GenerateTemplatesUsesDefaultFallout4ProfileSettings()
    {
        var parser = new BodySlideXmlParser();
        var service = new TemplateGenerationService();
        var import = parser.ParseFile(InputPath("fallout4-cbbe", "CBBE.xml"));
        var catalog = TemplateProfileCatalogFactory.CreateDefault();

        foreach (var preset in import.Presets) preset.ProfileName = ProjectProfileMapping.Fallout4Cbbe;

        var templates = service.GenerateTemplates(import.Presets, catalog, false);
        var omittedTemplates = service.GenerateTemplates(import.Presets, catalog, true);

        templates.Should().Contain("BreastCenterBig@1.0");
        templates.Should().Contain("ButtNew@1.0");
        templates.Should().Contain("ShoulderTweak@1.0");
        templates.Should().NotContain("BreastsSmall@0.0");
        omittedTemplates.Should().Contain("CBBE Athletic=");
    }

    private static string ProfilePath(string fileName) =>
        Path.Combine(RepositoryRoot, "tests", "fixtures", "inputs", "profiles", fileName);

    private static string InputPath(string scenario, string fileName) =>
        Path.Combine(RepositoryRoot, "tests", "fixtures", "inputs", scenario, fileName);

    private static SliderPreset CreatePreset(string name, int bigValue)
    {
        var preset = new SliderPreset(name);
        preset.AddSetSlider(new SetSlider("Scale") { ValueSmall = 0, ValueBig = bigValue });
        return preset;
    }

    private static void AssertFixtureText(string scenario, string fileName, string actual)
    {
        var expectedPath = Path.Combine(RepositoryRoot, "tests", "fixtures", "expected", scenario, fileName);
        var expected = File.ReadAllText(expectedPath);

        NormalizeNewlines(actual).TrimEnd().Should().Be(NormalizeNewlines(expected).TrimEnd());
    }

    private static string NormalizeNewlines(string value)
    {
        return value.Replace("\r\n", "\n", StringComparison.Ordinal)
            .Replace("\r", "\n", StringComparison.Ordinal);
    }
}
