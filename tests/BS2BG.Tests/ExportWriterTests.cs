using System.Text;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class ExportWriterTests
{
    [Fact]
    public void BodyGenIniExportWriterWritesTemplatesAndMorphsWithCrLfUtf8NoBom()
    {
        using var directory = new TemporaryDirectory();
        var writer = new BodyGenIniExportWriter();

        var result = writer.Write(directory.Path, "Alpha=Scale@1.0\nBeta=Scale@0.5", "All|Female=Alpha\rSkyrim.esm|A2C94=Beta");

        Assert.Equal(Path.Combine(directory.Path, "templates.ini"), result.TemplatesPath);
        Assert.Equal(Path.Combine(directory.Path, "morphs.ini"), result.MorphsPath);
        Assert.Equal("Alpha=Scale@1.0\r\nBeta=Scale@0.5", File.ReadAllText(result.TemplatesPath));
        Assert.Equal("All|Female=Alpha\r\nSkyrim.esm|A2C94=Beta", File.ReadAllText(result.MorphsPath));

        var templateBytes = File.ReadAllBytes(result.TemplatesPath);
        Assert.False(HasUtf8Bom(templateBytes));
        Assert.Contains((byte)'\r', templateBytes);
        Assert.Contains((byte)'\n', templateBytes);
    }

    [Fact]
    public void BosJsonExportWriterSanitizesFilenamesAndKeepsOriginalBodyNames()
    {
        using var directory = new TemporaryDirectory();
        var catalog = new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new BS2BG.Core.Formatting.SliderProfile(
                    defaults: Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
                    multipliers: Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
                    invertedNames: Array.Empty<string>())),
        });
        var writer = new BosJsonExportWriter(new TemplateGenerationService());
        var first = new SliderPreset("Preset:One");
        var second = new SliderPreset("Preset?One");

        var result = writer.Write(directory.Path, new[] { first, second }, catalog);

        Assert.Equal(
            new[]
            {
                Path.Combine(directory.Path, "Preset_One.json"),
                Path.Combine(directory.Path, "Preset_One (2).json"),
            },
            result.FilePaths);
        Assert.Contains("\"bodyname\": \"Preset:One\"", File.ReadAllText(result.FilePaths[0]));
        Assert.Contains("\"bodyname\": \"Preset?One\"", File.ReadAllText(result.FilePaths[1]));
    }

    [Fact]
    public void BosJsonExportWriterSanitizesWindowsDeviceNames()
    {
        using var directory = new TemporaryDirectory();
        var catalog = new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new BS2BG.Core.Formatting.SliderProfile(
                    defaults: Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
                    multipliers: Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
                    invertedNames: Array.Empty<string>())),
        });
        var writer = new BosJsonExportWriter(new TemplateGenerationService());
        var first = new SliderPreset("CON");
        var second = new SliderPreset("COM1");

        var result = writer.Write(directory.Path, new[] { first, second }, catalog);

        Assert.Equal(
            new[]
            {
                Path.Combine(directory.Path, "COM1_.json"),
                Path.Combine(directory.Path, "CON_.json"),
            },
            result.FilePaths);
        Assert.Contains("\"bodyname\": \"COM1\"", File.ReadAllText(result.FilePaths[0]));
        Assert.Contains("\"bodyname\": \"CON\"", File.ReadAllText(result.FilePaths[1]));
    }

    private static bool HasUtf8Bom(byte[] bytes)
    {
        return bytes.Length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF;
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose()
        {
            Directory.Delete(Path, recursive: true);
        }
    }
}
