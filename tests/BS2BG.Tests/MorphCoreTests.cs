using System.Diagnostics.CodeAnalysis;
using System.Text;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected sequences keep morph assertions readable.")]
public sealed class MorphCoreTests
{
    [Fact]
    public void CustomTargetsValidateDeDupeAndReceiveRandomPreset()
    {
        var project = new ProjectModel();
        var alpha = new SliderPreset("Alpha");
        var beta = new SliderPreset("Beta");
        project.SliderPresets.Add(alpha);
        project.SliderPresets.Add(beta);
        var service = new MorphAssignmentService(new FixedRandomAssignmentProvider(1));

        var added = service.TryAddCustomTarget(project, " All|Female|NordRace ", out var target, out var error);

        Assert.True(added);
        Assert.Equal(string.Empty, error);
        Assert.NotNull(target);
        Assert.Equal("All|Female|NordRace", target.Name);
        Assert.Equal(new[] { "Beta" }, target.SliderPresets.Select(preset => preset.Name));

        Assert.False(service.TryAddCustomTarget(project, "all|female|nordrace", out _, out error));
        Assert.Equal("A custom target named 'all|female|nordrace' already exists.", error);

        Assert.False(service.TryAddCustomTarget(project, "All", out _, out error));
        Assert.Equal("Custom target must use Context|Gender or Context|Gender|Race[Variant].", error);
    }

    [Fact]
    public void NpcTextParserHandlesBomUtf8FallbackEncodingAndCaseInsensitiveDeDupe()
    {
        using var directory = new TemporaryDirectory();
        var bomPath = directory.WriteBytes(
            "bom-npcs.txt",
            new UTF8Encoding(encoderShouldEmitUTF8Identifier: true).GetBytes(
                """
                Skyrim.esm|Lydia|HousecarlWhiterun|NordRace "Nord"|000A2C94
                skyrim.ESM|Duplicate Lydia|housecarlwhiterun|NordRace|000A2C94
                """));
        var fallbackPath = directory.WriteBytes(
            "fallback-npcs.txt",
            new byte[]
            {
                0x44, 0x61, 0x77, 0x6E, 0x67, 0x75, 0x61, 0x72,
                0x64, 0x2E, 0x65, 0x73, 0x6D, 0x7C, 0x5A, 0x6F,
                0xEB, 0x7C, 0x44, 0x4C, 0x43, 0x31, 0x5A, 0x6F,
                0x65, 0x7C, 0x4E, 0x6F, 0x72, 0x64, 0x52, 0x61,
                0x63, 0x65, 0x7C, 0x30, 0x32, 0x30, 0x30, 0x32,
                0x42, 0x36, 0x43,
            });
        var parser = new NpcTextParser();

        var bomResult = parser.ParseFile(bomPath);
        var fallbackResult = parser.ParseFile(fallbackPath);

        var npc = Assert.Single(bomResult.Npcs);
        Assert.False(bomResult.UsedFallbackEncoding);
        Assert.Equal("Lydia", npc.Name);
        Assert.Equal("HousecarlWhiterun", npc.EditorId);
        Assert.Equal("NordRace", npc.Race);
        Assert.Equal("A2C94", npc.FormId);

        var fallbackNpc = Assert.Single(fallbackResult.Npcs);
        Assert.True(fallbackResult.UsedFallbackEncoding);
        Assert.Equal("Zoë", fallbackNpc.Name);
        Assert.Equal("DLC1Zoe", fallbackNpc.EditorId);
    }

    [Fact]
    public void MorphGenerationWritesCustomTargetsThenNpcsSortedByMod()
    {
        var alpha = new SliderPreset("Alpha");
        var beta = new SliderPreset("Beta");
        var project = new ProjectModel();
        project.SliderPresets.Add(alpha);
        project.SliderPresets.Add(beta);
        var customTarget = new CustomMorphTarget("All|Female");
        customTarget.AddSliderPreset(beta);
        customTarget.AddSliderPreset(alpha);
        project.CustomMorphTargets.Add(customTarget);
        project.CustomMorphTargets.Add(new CustomMorphTarget("Empty|Female"));
        var skyrimNpc = new Npc("Lydia")
        {
            Mod = "Skyrim.esm",
            EditorId = "HousecarlWhiterun",
            Race = "NordRace",
            FormId = "000A2C94",
        };
        skyrimNpc.AddSliderPreset(beta);
        var dawnguardNpc = new Npc("Valerica")
        {
            Mod = "Dawnguard.esm",
            EditorId = "DLC1Valerica",
            Race = "NordRaceVampire",
            FormId = "02002B6C",
        };
        dawnguardNpc.AddSliderPreset(alpha);
        project.MorphedNpcs.Add(skyrimNpc);
        project.MorphedNpcs.Add(dawnguardNpc);
        var generator = new MorphGenerationService();

        var result = generator.GenerateMorphs(project);

        Assert.Equal(
            "All|Female=Alpha|Beta\r\n"
            + "Empty|Female=\r\n"
            + "Dawnguard.esm|2B6C=Alpha\r\n"
            + "Skyrim.esm|A2C94=Beta",
            result.Text);
        Assert.Equal(new[] { "Empty|Female" }, result.TargetsWithoutPresets.Select(target => target.Name));
    }

    private sealed class FixedRandomAssignmentProvider : IRandomAssignmentProvider
    {
        private readonly int value;

        public FixedRandomAssignmentProvider(int value)
        {
            this.value = value;
        }

        public int NextIndex(int exclusiveMax)
        {
            return value;
        }
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        private readonly string path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));

        public TemporaryDirectory()
        {
            Directory.CreateDirectory(path);
        }

        public string WriteBytes(string fileName, byte[] bytes)
        {
            var filePath = Path.Combine(path, fileName);
            File.WriteAllBytes(filePath, bytes);
            return filePath;
        }

        public void Dispose()
        {
            Directory.Delete(path, recursive: true);
        }
    }
}
