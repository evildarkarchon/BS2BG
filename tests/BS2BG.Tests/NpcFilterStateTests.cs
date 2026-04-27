using BS2BG.App.ViewModels.Workflow;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class NpcFilterStateTests
{
    [Fact]
    public void NpcRowViewModelAssignsDistinctRowIdsForDuplicateNpcFields()
    {
        var firstNpc = CreateNpc();
        var secondNpc = CreateNpc();

        var firstRow = new NpcRowViewModel(firstNpc);
        var secondRow = new NpcRowViewModel(secondNpc);

        firstRow.RowId.Should().NotBeEmpty();
        secondRow.RowId.Should().NotBeEmpty();
        secondRow.RowId.Should().NotBe(firstRow.RowId);
        firstRow.Npc.Should().BeSameAs(firstNpc);
        secondRow.Npc.Should().BeSameAs(secondNpc);
    }

    [Fact]
    public void NpcRowViewModelKeepsRowIdWhenMutableNpcFieldsChange()
    {
        var npc = CreateNpc();
        var row = new NpcRowViewModel(npc);
        var originalRowId = row.RowId;

        npc.Mod = "Edited.esm";
        npc.EditorId = "EditedEditorId";
        npc.FormId = "000ABC";
        npc.Name = "Edited Name";

        row.RowId.Should().Be(originalRowId);
        row.Mod.Should().Be("Edited.esm");
        row.EditorId.Should().Be("EditedEditorId");
        row.FormId.Should().Be("ABC");
        row.Name.Should().Be("Edited Name");
    }

    [Fact]
    public void NpcRowIdentityStaysOutOfCoreNpcSerializationModel()
    {
        typeof(Npc).GetProperty("RowId").Should().BeNull();
    }

    private static Npc CreateNpc()
    {
        return new Npc("Shared Name")
        {
            Mod = "Shared.esm",
            EditorId = "SharedEditorId",
            FormId = "000123",
            Race = "NordRace"
        };
    }
}
