using BS2BG.Core.Import;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class NpcImportPreviewServiceTests
{
    [Fact]
    public void ParseTextReportsWithinFileDuplicateNpcRows()
    {
        var parser = new NpcTextParser();
        var text = """
                   Skyrim.esm|Lydia|HousecarlWhiterun|NordRace|000A2C94
                   skyrim.ESM|Duplicate Lydia|housecarlwhiterun|NordRace|000A2C94
                   """;

        var result = parser.ParseText(text);

        result.Npcs.Should().ContainSingle();
        result.Diagnostics.Should().Contain(diagnostic =>
            diagnostic.LineNumber == 2
            && diagnostic.Message.Contains("Duplicate NPC row skipped", StringComparison.Ordinal)
            && diagnostic.Message.Contains("Skyrim.esm|HousecarlWhiterun", StringComparison.Ordinal));
    }

    [Fact]
    public void PreviewTextClassifiesRowsToAddAndExistingDuplicatesWithoutMutatingExistingNpcs()
    {
        var existingNpc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var existingNpcs = new List<Npc> { existingNpc };
        var service = new NpcImportPreviewService(new NpcTextParser());
        var text = """
                   Skyrim.esm|Lydia|HousecarlWhiterun|NordRace|000A2C94
                   Dawnguard.esm|Valerica|DLC1Valerica|NordRaceVampire|02002B6C
                   Broken|Row
                   Dawnguard.esm|Duplicate Valerica|DLC1Valerica|NordRaceVampire|02002B6C
                   """;

        var result = service.PreviewText("clipboard", text, existingNpcs);

        existingNpcs.Should().ContainSingle().Which.Should().BeSameAs(existingNpc);
        result.SourcePath.Should().Be("clipboard");
        result.ParsedRows.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        result.RowsToAdd.Should().ContainSingle().Which.Name.Should().Be("Valerica");
        result.ExistingDuplicates.Should().ContainSingle().Which.Name.Should().Be("Lydia");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Message.Contains("NPC row must contain", StringComparison.Ordinal));
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Message.Contains("Duplicate NPC row skipped", StringComparison.Ordinal));
        result.ParsedRowCount.Should().Be(2);
        result.RowsToAddCount.Should().Be(1);
        result.ExistingDuplicateCount.Should().Be(1);
        result.DiagnosticCount.Should().Be(2);
        result.UsedFallbackEncoding.Should().BeFalse();
        result.EncodingName.Should().Be("UTF-16");
    }

    [Fact]
    public void PreviewFilePreservesFallbackEncodingFacts()
    {
        var directory = Directory.CreateTempSubdirectory("bs2bg-npc-preview-");
        try
        {
            var path = Path.Combine(directory.FullName, "fallback-npcs.txt");
            File.WriteAllBytes(
                path,
                new byte[]
                {
                    0x44, 0x61, 0x77, 0x6E, 0x67, 0x75, 0x61, 0x72, 0x64, 0x2E, 0x65, 0x73, 0x6D, 0x7C, 0x5A, 0x6F,
                    0xEB, 0x7C, 0x44, 0x4C, 0x43, 0x31, 0x5A, 0x6F, 0x65, 0x7C, 0x4E, 0x6F, 0x72, 0x64, 0x52, 0x61,
                    0x63, 0x65, 0x7C, 0x30, 0x32, 0x30, 0x30, 0x32, 0x42, 0x36, 0x43
                });
            var service = new NpcImportPreviewService(new NpcTextParser());

            var result = service.PreviewFile(path, Array.Empty<Npc>());

            result.SourcePath.Should().Be(path);
            result.RowsToAdd.Should().ContainSingle().Which.Name.Should().Be("Zoë");
            result.UsedFallbackEncoding.Should().BeTrue();
            result.EncodingName.Should().NotBeNullOrWhiteSpace();
        }
        finally
        {
            directory.Delete(true);
        }
    }

    private static Npc CreateNpc(string mod, string name, string editorId, string race, string formId)
    {
        return new Npc(name)
        {
            Mod = mod,
            EditorId = editorId,
            Race = race,
            FormId = formId
        };
    }
}
