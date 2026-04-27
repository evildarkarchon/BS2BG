using System.Globalization;
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

    [Fact]
    public void NpcFilterColumnCoversRequiredNpcFilterDimensions()
    {
        Enum.GetValues<NpcFilterColumn>().Should().BeEquivalentTo(
            NpcFilterColumn.Mod,
            NpcFilterColumn.Name,
            NpcFilterColumn.EditorId,
            NpcFilterColumn.FormId,
            NpcFilterColumn.Race,
            NpcFilterColumn.AssignmentState,
            NpcFilterColumn.Preset);
    }

    [Fact]
    public void CreatePredicateAppliesChecklistSelectionsForEveryColumn()
    {
        var cbbe = new SliderPreset("CBBE Curvy");
        var unassigned = new NpcRowViewModel(CreateNpc(name: "Aela", mod: "Skyrim.esm", editorId: "AelaEditor", formId: "0001", race: "NordRace"));
        var assigned = new NpcRowViewModel(CreateNpc(name: "Cait", mod: "Fallout4.esm", editorId: "CaitEditor", formId: "0002", race: "HumanRace"));
        assigned.Npc.AddSliderPreset(cbbe);
        var rows = new[] { unassigned, assigned };

        AssertOnlyMatch(rows, NpcFilterColumn.Mod, "Fallout4.esm", assigned);
        AssertOnlyMatch(rows, NpcFilterColumn.Name, "Cait", assigned);
        AssertOnlyMatch(rows, NpcFilterColumn.EditorId, "CaitEditor", assigned);
        AssertOnlyMatch(rows, NpcFilterColumn.FormId, "2", assigned);
        AssertOnlyMatch(rows, NpcFilterColumn.Race, "HumanRace", assigned);
        AssertOnlyMatch(rows, NpcFilterColumn.AssignmentState, NpcFilterState.AssignedValue, assigned);
        AssertOnlyMatch(rows, NpcFilterColumn.Preset, "CBBE Curvy", assigned);
        AssertOnlyMatch(rows, NpcFilterColumn.AssignmentState, NpcFilterState.EmptyValue, unassigned);
    }

    [Fact]
    public void CreatePredicateUsesAppliedGlobalSearchAcrossNpcAndPresetValues()
    {
        var cbbe = new SliderPreset("Needle Preset");
        var matchingPreset = new NpcRowViewModel(CreateNpc());
        matchingPreset.Npc.AddSliderPreset(cbbe);
        var rows = new[]
        {
            new NpcRowViewModel(CreateNpc(mod: "Needle.esm")),
            new NpcRowViewModel(CreateNpc(name: "Needle Name")),
            new NpcRowViewModel(CreateNpc(editorId: "NeedleEditor")),
            new NpcRowViewModel(CreateNpc(formId: "Needle")),
            new NpcRowViewModel(CreateNpc(race: "NeedleRace")),
            matchingPreset,
            new NpcRowViewModel(CreateNpc(name: "Different"))
        };
        var filter = new NpcFilterState();

        filter.CreatePredicate().Should().NotBeNull();
        rows.Where(filter.CreatePredicate()).Should().HaveCount(rows.Length);

        filter.PendingGlobalSearchText = "needle";
        rows.Where(filter.CreatePredicate()).Should().HaveCount(rows.Length);

        filter.ApplyPendingGlobalSearchText();
        rows.Where(filter.CreatePredicate()).Should().HaveCount(6);
    }

    [Fact]
    public void GetAvailableValuesReturnsDistinctSortedValuesWithoutMutatingRows()
    {
        var cbbe = new SliderPreset("CBBE Curvy");
        var rows = Enumerable.Range(0, 250)
            .Select(index => new NpcRowViewModel(CreateNpc(
                name: $"Npc {index}",
                mod: index % 2 == 0 ? "Skyrim.esm" : "Fallout4.esm",
                editorId: $"Editor{index}",
                formId: index.ToString("X6", CultureInfo.InvariantCulture),
                race: index % 3 == 0 ? "NordRace" : "HumanRace")))
            .ToArray();
        foreach (var row in rows.Where((_, index) => index % 5 == 0)) row.Npc.AddSliderPreset(cbbe);
        var originalIds = rows.Select(row => row.RowId).ToArray();
        var filter = new NpcFilterState();

        filter.GetAvailableValues(rows, NpcFilterColumn.Mod).Should().Equal("Fallout4.esm", "Skyrim.esm");
        filter.GetAvailableValues(rows, NpcFilterColumn.Race).Should().Equal("HumanRace", "NordRace");
        filter.GetAvailableValues(rows, NpcFilterColumn.AssignmentState).Should().Equal(
            NpcFilterState.AssignedValue,
            NpcFilterState.EmptyValue);
        filter.GetAvailableValues(rows, NpcFilterColumn.Preset).Should().Equal("CBBE Curvy");
        rows.Select(row => row.RowId).Should().Equal(originalIds);
    }

    private static void AssertOnlyMatch(
        IReadOnlyCollection<NpcRowViewModel> rows,
        NpcFilterColumn column,
        string allowedValue,
        NpcRowViewModel expectedRow)
    {
        var filter = new NpcFilterState();
        filter.SetAllowedValues(column, new[] { allowedValue });

        rows.Where(filter.CreatePredicate()).Should().ContainSingle().Which.Should().BeSameAs(expectedRow);
    }

    private static Npc CreateNpc(
        string name = "Shared Name",
        string mod = "Shared.esm",
        string editorId = "SharedEditorId",
        string formId = "000123",
        string race = "NordRace")
    {
        return new Npc(name)
        {
            Mod = mod,
            EditorId = editorId,
            FormId = formId,
            Race = race
        };
    }
}
