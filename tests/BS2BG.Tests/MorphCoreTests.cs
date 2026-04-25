using System.Diagnostics.CodeAnalysis;
using System.Text;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected sequences keep morph assertions readable.")]
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

        added.Should().BeTrue();
        error.Should().Be(string.Empty);
        target.Should().NotBeNull();
        target.Name.Should().Be("All|Female|NordRace");
        target.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");

        service.TryAddCustomTarget(project, "all|female|nordrace", out _, out error).Should().BeFalse();
        error.Should().Be("A custom target named 'all|female|nordrace' already exists.");

        service.TryAddCustomTarget(project, "All", out _, out error).Should().BeFalse();
        error.Should().Be("Custom target must use Context|Gender or Context|Gender|Race[Variant].");
    }

    [Fact]
    public void NpcTextParserHandlesBomUtf8FallbackEncodingAndCaseInsensitiveDeDupe()
    {
        using var directory = new TemporaryDirectory();
        var bomPath = directory.WriteBytes(
            "bom-npcs.txt",
            new UTF8Encoding(true).GetBytes(
                """
                Skyrim.esm|Lydia|HousecarlWhiterun|NordRace "Nord"|000A2C94
                skyrim.ESM|Duplicate Lydia|housecarlwhiterun|NordRace|000A2C94
                """));
        var fallbackPath = directory.WriteBytes(
            "fallback-npcs.txt",
            new byte[]
            {
                0x44, 0x61, 0x77, 0x6E, 0x67, 0x75, 0x61, 0x72, 0x64, 0x2E, 0x65, 0x73, 0x6D, 0x7C, 0x5A, 0x6F,
                0xEB, 0x7C, 0x44, 0x4C, 0x43, 0x31, 0x5A, 0x6F, 0x65, 0x7C, 0x4E, 0x6F, 0x72, 0x64, 0x52, 0x61,
                0x63, 0x65, 0x7C, 0x30, 0x32, 0x30, 0x30, 0x32, 0x42, 0x36, 0x43
            });
        var parser = new NpcTextParser();

        var bomResult = parser.ParseFile(bomPath);
        var fallbackResult = parser.ParseFile(fallbackPath);

        var npc = bomResult.Npcs.Should().ContainSingle().Which;
        bomResult.UsedFallbackEncoding.Should().BeFalse();
        npc.Name.Should().Be("Lydia");
        npc.EditorId.Should().Be("HousecarlWhiterun");
        npc.Race.Should().Be("NordRace");
        npc.FormId.Should().Be("A2C94");

        var fallbackNpc = fallbackResult.Npcs.Should().ContainSingle().Which;
        fallbackResult.UsedFallbackEncoding.Should().BeTrue();
        fallbackNpc.Name.Should().Be("Zoë");
        fallbackNpc.EditorId.Should().Be("DLC1Zoe");
    }

    [Fact]
    public void NpcTextParserReportsFileReadFailureAsDiagnostic()
    {
        var missingPath = Path.Combine(
            Path.GetTempPath(),
            Guid.NewGuid().ToString("N"),
            "missing-npcs.txt");
        var parser = new NpcTextParser();

        var result = parser.ParseFile(missingPath);

        result.Npcs.Should().BeEmpty();
        result.UsedFallbackEncoding.Should().BeFalse();
        result.EncodingName.Should().Be(string.Empty);
        var diagnostic = result.Diagnostics.Should().ContainSingle().Which;
        diagnostic.LineNumber.Should().Be(0);
        diagnostic.Message.Should().Contain("Could not read NPC file");
        diagnostic.Message.Should().Contain(missingPath);
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
            Mod = "Skyrim.esm", EditorId = "HousecarlWhiterun", Race = "NordRace", FormId = "000A2C94"
        };
        skyrimNpc.AddSliderPreset(beta);
        var dawnguardNpc = new Npc("Valerica")
        {
            Mod = "Dawnguard.esm", EditorId = "DLC1Valerica", Race = "NordRaceVampire", FormId = "02002B6C"
        };
        dawnguardNpc.AddSliderPreset(alpha);
        project.MorphedNpcs.Add(skyrimNpc);
        project.MorphedNpcs.Add(dawnguardNpc);
        var generator = new MorphGenerationService();

        var result = generator.GenerateMorphs(project);

        result.Text.Should().Be("All|Female=Alpha|Beta\r\n"
                                + "Empty|Female=\r\n"
                                + "Dawnguard.esm|2B6C=Alpha\r\n"
                                + "Skyrim.esm|A2C94=Beta");
        result.TargetsWithoutPresets.Select(target => target.Name).Should().Equal("Empty|Female");
    }

    [Fact]
    public void AddingSliderPresetKeepsSortedOrderWhenCollectionHasExternalSubscribers()
    {
        var target = new CustomMorphTarget("All|Female");
        var collectionNotifications = 0;
        target.SliderPresets.CollectionChanged += (_, _) => collectionNotifications++;

        target.AddSliderPreset(new SliderPreset("P2"));
        FluentActions.Invoking(() => target.AddSliderPreset(new SliderPreset("P10"))).Should().NotThrow();
        collectionNotifications.Should().BeGreaterThan(0);
        target.SliderPresets.Select(preset => preset.Name).Should().Equal("P10", "P2");
    }

    private sealed class FixedRandomAssignmentProvider(int value) : IRandomAssignmentProvider
    {
        private readonly int value = value;

        public int NextIndex(int exclusiveMax) => value;
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        private readonly string path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));

        public TemporaryDirectory() => Directory.CreateDirectory(path);

        public void Dispose() => Directory.Delete(path, true);

        public string WriteBytes(string fileName, byte[] bytes)
        {
            var filePath = Path.Combine(path, fileName);
            File.WriteAllBytes(filePath, bytes);
            return filePath;
        }
    }
}
