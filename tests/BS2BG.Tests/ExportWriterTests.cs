using System.Text;
using BS2BG.Core.Export;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using Xunit;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

public sealed class ExportWriterTests
{
    [Fact]
    public void BodyGenIniExportWriterWritesTemplatesAndMorphsWithCrLfUtf8NoBom()
    {
        using var directory = new TemporaryDirectory();
        var writer = new BodyGenIniExportWriter();

        var result = writer.Write(directory.Path, "Alpha=Scale@1.0\nBeta=Scale@0.5",
            "All|Female=Alpha\rSkyrim.esm|A2C94=Beta");

        result.TemplatesPath.Should().Be(Path.Combine(directory.Path, "templates.ini"));
        result.MorphsPath.Should().Be(Path.Combine(directory.Path, "morphs.ini"));
        File.ReadAllText(result.TemplatesPath).Should().Be("Alpha=Scale@1.0\r\nBeta=Scale@0.5");
        File.ReadAllText(result.MorphsPath).Should().Be("All|Female=Alpha\r\nSkyrim.esm|A2C94=Beta");

        var templateBytes = File.ReadAllBytes(result.TemplatesPath);
        HasUtf8Bom(templateBytes).Should().BeFalse();
        templateBytes.Should().Contain((byte)'\r');
        templateBytes.Should().Contain((byte)'\n');
    }

    [Fact]
    public void BosJsonExportWriterSanitizesFilenamesAndKeepsOriginalBodyNames()
    {
        using var directory = new TemporaryDirectory();
        var catalog = new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    Array.Empty<SliderDefault>(),
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
        var writer = new BosJsonExportWriter(new TemplateGenerationService());
        var first = new SliderPreset("Preset:One");
        var second = new SliderPreset("Preset?One");

        var result = writer.Write(directory.Path, new[] { first, second }, catalog);

        result.FilePaths.Should().Equal(Path.Combine(directory.Path, "Preset_One.json"),
            Path.Combine(directory.Path, "Preset_One (2).json"));
        File.ReadAllText(result.FilePaths[0]).Should().Contain("\"bodyname\": \"Preset:One\"");
        File.ReadAllText(result.FilePaths[1]).Should().Contain("\"bodyname\": \"Preset?One\"");
    }

    [Fact]
    public void BosJsonExportWriterSanitizesWindowsDeviceNames()
    {
        using var directory = new TemporaryDirectory();
        var catalog = new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    Array.Empty<SliderDefault>(),
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
        var writer = new BosJsonExportWriter(new TemplateGenerationService());
        var first = new SliderPreset("CON");
        var second = new SliderPreset("COM1");

        var result = writer.Write(directory.Path, new[] { first, second }, catalog);

        result.FilePaths.Should().Equal(Path.Combine(directory.Path, "COM1_.json"),
            Path.Combine(directory.Path, "CON_.json"));
        File.ReadAllText(result.FilePaths[0]).Should().Contain("\"bodyname\": \"COM1\"");
        File.ReadAllText(result.FilePaths[1]).Should().Contain("\"bodyname\": \"CON\"");
    }

    [Fact]
    public void BodyGenIniExportWriterRemovesTempFilesOnSuccess()
    {
        using var directory = new TemporaryDirectory();
        var writer = new BodyGenIniExportWriter();

        writer.Write(directory.Path, "templates", "morphs");

        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(
            Path.Combine(directory.Path, "templates.ini"),
            Path.Combine(directory.Path, "morphs.ini"));
    }

    [Fact]
    public void BodyGenIniExportWriterAtomicallyReplacesExistingPair()
    {
        using var directory = new TemporaryDirectory();
        var templatesPath = Path.Combine(directory.Path, "templates.ini");
        var morphsPath = Path.Combine(directory.Path, "morphs.ini");
        File.WriteAllText(templatesPath, "OLD_TEMPLATES");
        File.WriteAllText(morphsPath, "OLD_MORPHS");
        var writer = new BodyGenIniExportWriter();

        writer.Write(directory.Path, "Alpha=1.0", "All|Female=Alpha");

        File.ReadAllText(templatesPath).Should().Be("Alpha=1.0");
        File.ReadAllText(morphsPath).Should().Be("All|Female=Alpha");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(templatesPath, morphsPath);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicPairLeavesTargetsUntouchedOnPhase1Failure()
    {
        using var directory = new TemporaryDirectory();
        var firstPath = Path.Combine(directory.Path, "first.txt");
        var secondPath = Path.Combine(directory.Path, "missing-subdir", "second.txt");
        File.WriteAllText(firstPath, "ORIGINAL_FIRST");

        var act = () => AtomicFileWriter.WriteAtomicPair(
            firstPath,
            "NEW_FIRST",
            secondPath,
            "NEW_SECOND",
            Encoding.UTF8);

        act.Should().Throw<DirectoryNotFoundException>();
        File.ReadAllText(firstPath).Should().Be("ORIGINAL_FIRST");
        File.Exists(secondPath).Should().BeFalse();
        Directory.GetFiles(directory.Path).Should().ContainSingle().Which.Should().Be(firstPath);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicReplacesExistingTarget()
    {
        using var directory = new TemporaryDirectory();
        var path = Path.Combine(directory.Path, "data.txt");
        File.WriteAllText(path, "ORIGINAL");

        AtomicFileWriter.WriteAtomic(path, "REPLACED", Encoding.UTF8);

        File.ReadAllText(path).Should().Be("REPLACED");
        Directory.GetFiles(directory.Path).Should().ContainSingle().Which.Should().Be(path);
    }

    private static bool HasUtf8Bom(byte[] bytes) =>
        bytes.Length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF;

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
