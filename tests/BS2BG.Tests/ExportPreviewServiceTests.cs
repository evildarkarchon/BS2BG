using BS2BG.Core.Diagnostics;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

public sealed class ExportPreviewServiceTests
{
    [Fact]
    public void PreviewBodyGenReportsPathsOverwriteStateSnippetsAndBatchRiskWithoutWriting()
    {
        using var directory = new TemporaryDirectory();
        var templatesPath = Path.Combine(directory.Path, "templates.ini");
        File.WriteAllText(templatesPath, "OLD");
        var service = new ExportPreviewService();

        var result = service.PreviewBodyGen(
            directory.Path,
            "\r\nAlpha=Scale@1.0\r\n\r\nBeta=Scale@0.5\r\nGamma=Scale@0.2\r\nDelta=Scale@0.1",
            "All|Female=Alpha\r\n\r\nSkyrim.esm|A2C94=Beta");

        result.HasBatchRisk.Should().BeTrue();
        result.Files.Should().HaveCount(2);
        result.Files[0].Path.Should().Be(templatesPath);
        result.Files[0].WillOverwrite.Should().BeTrue();
        result.Files[0].WillCreate.Should().BeFalse();
        result.Files[0].SnippetLines.Should().Equal("Alpha=Scale@1.0", "Beta=Scale@0.5", "Gamma=Scale@0.2");
        result.Files[1].Path.Should().Be(Path.Combine(directory.Path, "morphs.ini"));
        result.Files[1].WillOverwrite.Should().BeFalse();
        result.Files[1].WillCreate.Should().BeTrue();
        result.Files[1].SnippetLines.Should().Equal("All|Female=Alpha", "Skyrim.esm|A2C94=Beta");
        File.Exists(result.Files[1].Path).Should().BeFalse();
        File.ReadAllText(templatesPath).Should().Be("OLD");
    }

    [Fact]
    public void PreviewBosJsonUsesWriterNamingRulesAndGeneratedJsonSnippetsWithoutWriting()
    {
        using var directory = new TemporaryDirectory();
        var existingPath = Path.Combine(directory.Path, "Preset_One.json");
        File.WriteAllText(existingPath, "OLD");
        var catalog = CreateCatalog();
        var service = new ExportPreviewService(new TemplateGenerationService());
        var first = CreatePreset("Preset:One", 50);
        var second = CreatePreset("Preset?One", 25);

        var result = service.PreviewBosJson(directory.Path, new[] { second, first }, catalog);

        result.HasBatchRisk.Should().BeTrue();
        result.Files.Should().HaveCount(2);
        result.Files[0].Path.Should().Be(existingPath);
        result.Files[0].WillOverwrite.Should().BeTrue();
        result.Files[0].SnippetLines.Should().Equal("{", "\"bodyname\": \"Preset:One\",", "\"sliders\": {");
        result.Files[1].Path.Should().Be(Path.Combine(directory.Path, "Preset_One (2).json"));
        result.Files[1].WillOverwrite.Should().BeFalse();
        result.Files[1].WillCreate.Should().BeTrue();
        result.Files[1].SnippetLines.Should().Equal("{", "\"bodyname\": \"Preset?One\",", "\"sliders\": {");
        File.Exists(result.Files[1].Path).Should().BeFalse();
        File.ReadAllText(existingPath).Should().Be("OLD");
    }

    private static SliderPreset CreatePreset(string name, int value)
    {
        var preset = new SliderPreset(name);
        preset.AddSetSlider(new ModelSetSlider("Scale") { ValueSmall = value, ValueBig = value });
        return preset;
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    Array.Empty<SliderDefault>(),
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
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
    }
}
