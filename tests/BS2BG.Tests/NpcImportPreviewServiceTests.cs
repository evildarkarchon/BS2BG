using BS2BG.Core.Import;
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
}
