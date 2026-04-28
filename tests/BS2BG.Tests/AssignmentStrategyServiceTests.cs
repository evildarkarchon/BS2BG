using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using FluentAssertions;
using Xunit;

namespace BS2BG.Tests;

public sealed class AssignmentStrategyServiceTests
{
    [Fact]
    public void NewProjectStartsWithNoAssignmentStrategyForLegacyCompatibility()
    {
        var project = new ProjectModel();

        project.AssignmentStrategy.Should().BeNull();
    }

    [Fact]
    public void StrategyKindsCoverFullPhaseFiveMenu()
    {
        Enum.GetNames<AssignmentStrategyKind>().Should().BeEquivalentTo(
            "SeededRandom",
            "RoundRobin",
            "Weighted",
            "RaceFilters",
            "GroupsBuckets");
    }

    [Fact]
    public void WeightedRuleCanCarryRaceFiltersAndBucketName()
    {
        var rule = new AssignmentStrategyRule(
            "Nord Warriors",
            new[] { "PresetA", "PresetB" },
            new[] { "NordRace" },
            2.5,
            "Warriors");
        var strategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.Weighted,
            1234,
            new[] { rule });

        strategy.Rules.Should().ContainSingle().Which.Should().BeEquivalentTo(rule);
        rule.RaceFilters.Should().ContainSingle().Which.Should().Be("NordRace");
        rule.BucketName.Should().Be("Warriors");
        rule.Weight.Should().Be(2.5);
    }

    [Fact]
    public void RaceRuleMatchingUsesImportedNpcRaceOrdinalIgnoreCase()
    {
        var npc = new Npc("Aela") { Race = "NordRace" };
        var rule = new AssignmentStrategyRule(
            "Any Case Race",
            new[] { "PresetA" },
            new[] { "nordrace" },
            1.0,
            null);

        AssignmentStrategyRule.MatchesRace(rule, npc).Should().BeTrue();
    }

    [Fact]
    public void EmptyRaceFiltersMatchAnyImportedRaceForBucketRules()
    {
        var npc = new Npc("Codsworth") { Race = "MrHandyRace" };
        var rule = new AssignmentStrategyRule(
            "Any Race Bucket",
            new[] { "PresetA" },
            Array.Empty<string>(),
            1.0,
            "Companions");

        AssignmentStrategyRule.MatchesRace(rule, npc).Should().BeTrue();
    }
}
