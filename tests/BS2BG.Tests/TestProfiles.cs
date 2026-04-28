using BS2BG.Core.Automation;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using SliderDefault = BS2BG.Core.Formatting.SliderDefault;
using SliderMultiplier = BS2BG.Core.Formatting.SliderMultiplier;
using SliderProfile = BS2BG.Core.Formatting.SliderProfile;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;

namespace BS2BG.Tests;

internal static class TestProfiles
{
    private static readonly string[] BreastsSliderNames = { "Breasts" };

    internal static ProjectModel CreateProjectUsingProfile(string profileName)
    {
        var project = new ProjectModel();
        var preset = new SliderPreset("Alpha", profileName);
        preset.AddSetSlider(new ModelSetSlider("Breasts") { ValueSmall = 0, ValueBig = 100 });
        project.SliderPresets.Add(preset);
        return project;
    }

    internal static CustomProfileDefinition CreateProfile(
        string name,
        ProfileSourceKind sourceKind,
        string? filePath = null,
        SliderProfile? sliderProfile = null) =>
        new(name, "Skyrim", sliderProfile ?? CreateSliderProfile(), sourceKind, filePath);

    internal static TemplateProfileCatalog CreateBundledOnlyCatalog() => new(new[]
    {
        new ProfileCatalogEntry(
            ProjectProfileMapping.SkyrimCbbe,
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, CreateSliderProfile()),
            ProfileSourceKind.Bundled,
            null,
            false),
    });

    internal static TemplateProfileCatalog CreateRequestScopedCatalog(params CustomProfileDefinition[] profiles) => new(
        CreateBundledOnlyCatalog().Entries.Concat(profiles.Select(profile => new ProfileCatalogEntry(
            profile.Name,
            new TemplateProfile(profile.Name, profile.SliderProfile),
            profile.SourceKind,
            profile.FilePath,
            false))));

    internal static SliderProfile CreateSliderProfile() => new(
        new[] { new SliderDefault("Breasts", 0.2f, 1f) },
        Array.Empty<SliderMultiplier>(),
        Array.Empty<string>());

    internal static SliderProfile CreateCommunitySliderProfile() => new(
        new[] { new SliderDefault("Breasts", 0.8f, 0.1f) },
        new[] { new SliderMultiplier("Breasts", 1.5f) },
        BreastsSliderNames);

    internal static SliderProfile CreateEmbeddedSliderProfile() => new(
        new[] { new SliderDefault("Breasts", 0.05f, 0.9f) },
        new[] { new SliderMultiplier("Breasts", 0.5f) },
        Array.Empty<string>());

    internal static ExpectedOutputPaths WriteExpectedOutputs(
        string directory,
        string childName,
        ProjectModel project,
        TemplateProfileCatalog catalog)
    {
        var expectedDirectory = Path.Combine(directory, childName);
        var bodyGenDirectory = Path.Combine(expectedDirectory, "bodygen");
        var bosDirectory = Path.Combine(expectedDirectory, "bos");
        var templatesText = new TemplateGenerationService().GenerateTemplates(project.SliderPresets, catalog, false);
        var morphsText = new MorphGenerationService().GenerateMorphs(project).Text;
        new BodyGenIniExportWriter().Write(bodyGenDirectory, templatesText, morphsText);
        new BosJsonExportWriter(new TemplateGenerationService()).Write(bosDirectory, project.SliderPresets, catalog);
        return new ExpectedOutputPaths(
            Path.Combine(bodyGenDirectory, "templates.ini"),
            Path.Combine(bosDirectory, "Alpha.json"));
    }
}

internal sealed record ExpectedOutputPaths(string TemplatesPath, string BosJsonPath);
