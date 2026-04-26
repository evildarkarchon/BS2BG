using System.Text;
using BS2BG.Core.Export;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
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
    public void BodyGenExportCombinesProfileSpecificTemplatesWithProfileIndependentMorphs()
    {
        using var directory = new TemporaryDirectory();
        var catalog = new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    new[] { new SliderDefault("Breasts", 0.2f, 1f) },
                    Array.Empty<SliderMultiplier>(),
                    new[] { "Breasts" })),
            new TemplateProfile(
                ProjectProfileMapping.Fallout4Cbbe,
                new SliderProfile(
                    new[] { new SliderDefault("Breasts", 1f, 1f) },
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
        var alpha = new SliderPreset("Alpha", ProjectProfileMapping.SkyrimCbbe);
        alpha.AddSetSlider(new ModelSetSlider("Breasts"));
        var beta = new SliderPreset("Beta", ProjectProfileMapping.Fallout4Cbbe);
        beta.AddSetSlider(new ModelSetSlider("Breasts"));
        var templatesText = new TemplateGenerationService().GenerateTemplates(new[] { beta, alpha }, catalog, false);
        var project = new ProjectModel();
        var customTarget = new CustomMorphTarget("All|Female");
        customTarget.AddSliderPreset(alpha);
        project.CustomMorphTargets.Add(customTarget);
        var npc = new Npc("Lydia") { Mod = "Skyrim.esm", FormId = "000A2C94" };
        npc.AddSliderPreset(beta);
        project.MorphedNpcs.Add(npc);
        var morphsText = new MorphGenerationService().GenerateMorphs(project).Text;
        var writer = new BodyGenIniExportWriter();

        var result = writer.Write(directory.Path, templatesText, morphsText);

        File.ReadAllText(result.TemplatesPath).Should().Be("Alpha=Breasts@0.0\r\nBeta=Breasts@0.0");
        File.ReadAllText(result.MorphsPath).Should().Be("All|Female=Alpha\r\nSkyrim.esm|A2C94=Beta");
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

    [Fact]
    public void AtomicFileWriterWriteAtomicPairRestoresFirstTargetWhenSecondCommitFails()
    {
        using var directory = new TemporaryDirectory();
        var firstPath = Path.Combine(directory.Path, "first.txt");
        var secondPath = Path.Combine(directory.Path, "second.txt");
        File.WriteAllText(firstPath, "ORIGINAL_FIRST");
        File.WriteAllText(secondPath, "ORIGINAL_SECOND");

        using (new FileStream(secondPath, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => AtomicFileWriter.WriteAtomicPair(
                firstPath,
                "NEW_FIRST",
                secondPath,
                "NEW_SECOND",
                Encoding.UTF8);

            act.Should().Throw<IOException>();
        }

        File.ReadAllText(firstPath).Should().Be("ORIGINAL_FIRST");
        File.ReadAllText(secondPath).Should().Be("ORIGINAL_SECOND");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(firstPath, secondPath);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicBatchRollsBackPreviouslyCommittedTargets()
    {
        using var directory = new TemporaryDirectory();
        var path1 = Path.Combine(directory.Path, "a.txt");
        var path2 = Path.Combine(directory.Path, "b.txt");
        var path3 = Path.Combine(directory.Path, "c.txt");
        File.WriteAllText(path1, "OLD_1");
        File.WriteAllText(path2, "OLD_2");
        File.WriteAllText(path3, "OLD_3");

        using (new FileStream(path3, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => AtomicFileWriter.WriteAtomicBatch(
                new[] { (path1, "NEW_1"), (path2, "NEW_2"), (path3, "NEW_3") },
                Encoding.UTF8);

            act.Should().Throw<IOException>();
        }

        File.ReadAllText(path1).Should().Be("OLD_1");
        File.ReadAllText(path2).Should().Be("OLD_2");
        File.ReadAllText(path3).Should().Be("OLD_3");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(path1, path2, path3);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicBatchDeletesNewlyCreatedTargetsOnLaterFailure()
    {
        using var directory = new TemporaryDirectory();
        var path1 = Path.Combine(directory.Path, "new1.txt");
        var path2 = Path.Combine(directory.Path, "new2.txt");
        var path3 = Path.Combine(directory.Path, "existing.txt");
        File.WriteAllText(path3, "OLD_3");

        using (new FileStream(path3, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => AtomicFileWriter.WriteAtomicBatch(
                new[] { (path1, "NEW_1"), (path2, "NEW_2"), (path3, "NEW_3") },
                Encoding.UTF8);

            act.Should().Throw<IOException>();
        }

        File.Exists(path1).Should().BeFalse();
        File.Exists(path2).Should().BeFalse();
        File.ReadAllText(path3).Should().Be("OLD_3");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(path3);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicBatchRejectsDuplicatePaths()
    {
        using var directory = new TemporaryDirectory();
        var path = Path.Combine(directory.Path, "dup.txt");

        var act = () => AtomicFileWriter.WriteAtomicBatch(
            new[] { (path, "ONE"), (path.ToUpperInvariant(), "TWO") },
            Encoding.UTF8);

        act.Should().Throw<ArgumentException>();
        File.Exists(path).Should().BeFalse();
        Directory.GetFiles(directory.Path).Should().BeEmpty();
    }

    [Fact]
    public void BosJsonExportWriterRollsBackOnLockedTargetMidBatch()
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
        var path1 = Path.Combine(directory.Path, "Preset1.json");
        var path2 = Path.Combine(directory.Path, "Preset2.json");
        var path3 = Path.Combine(directory.Path, "Preset3.json");
        File.WriteAllText(path1, "OLD_1");
        File.WriteAllText(path2, "OLD_2");
        File.WriteAllText(path3, "OLD_3");

        var presets = new[] { new SliderPreset("Preset1"), new SliderPreset("Preset2"), new SliderPreset("Preset3") };

        using (new FileStream(path2, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => writer.Write(directory.Path, presets, catalog);
            act.Should().Throw<IOException>();
        }

        File.ReadAllText(path1).Should().Be("OLD_1");
        File.ReadAllText(path2).Should().Be("OLD_2");
        File.ReadAllText(path3).Should().Be("OLD_3");
        Directory.GetFiles(directory.Path).Should().BeEquivalentTo(path1, path2, path3);
    }

    [Fact]
    public void AtomicFileWriterWriteAtomicBatchDeletesTempFilesWhenWritePhaseFails()
    {
        using var directory = new TemporaryDirectory();
        var path1 = Path.Combine(directory.Path, "a.txt");
        var path2 = Path.Combine(directory.Path, "b.txt");

        var act = () => AtomicFileWriter.WriteAtomicBatch(
            new[] { (path1, "ONE"), (path2, "TWO") },
            new FaultingEncoding());

        act.Should().Throw<IOException>();

        File.Exists(path1).Should().BeFalse();
        File.Exists(path2).Should().BeFalse();
        Directory.GetFiles(directory.Path).Should().BeEmpty();
    }

    private static bool HasUtf8Bom(byte[] bytes) =>
        bytes.Length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF;

    private sealed class FaultingEncoding : Encoding
    {
        private static readonly Encoding Inner = UTF8;

        public override int GetByteCount(char[] chars, int index, int count) =>
            Inner.GetByteCount(chars, index, count);

        public override int GetBytes(char[] chars, int charIndex, int charCount, byte[] bytes, int byteIndex) =>
            throw new IOException("simulated mid-write failure");

        public override int GetCharCount(byte[] bytes, int index, int count) =>
            Inner.GetCharCount(bytes, index, count);

        public override int GetChars(byte[] bytes, int byteIndex, int byteCount, char[] chars, int charIndex) =>
            Inner.GetChars(bytes, byteIndex, byteCount, chars, charIndex);

        public override int GetMaxByteCount(int charCount) => Inner.GetMaxByteCount(charCount);

        public override int GetMaxCharCount(int byteCount) => Inner.GetMaxCharCount(byteCount);
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
