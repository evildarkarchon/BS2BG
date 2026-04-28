using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using FluentAssertions;
using Xunit;

namespace BS2BG.Tests;

public sealed class AssignmentStrategyServiceTests
{
    private static readonly string[] PresetA = { "PresetA" };
    private static readonly string[] PresetAAndPresetB = { "PresetA", "PresetB" };
    private static readonly string[] NordRaceUpper = { "NordRace" };
    private static readonly string[] NordRaceLower = { "nordrace" };

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
            PresetAAndPresetB,
            NordRaceUpper,
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
            PresetA,
            NordRaceLower,
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
            PresetA,
            Array.Empty<string>(),
            1.0,
            "Companions");

        AssignmentStrategyRule.MatchesRace(rule, npc).Should().BeTrue();
    }

    [Fact]
    public void LegacyProjectLoadAndSaveOmitAssignmentStrategyWhenAbsent()
    {
        var service = new ProjectFileService();
        const string legacyJson = """
                                  {
                                    "SliderPresets": {
                                      "PresetA": { "isUUNP": false, "SetSliders": [] }
                                    },
                                    "CustomMorphTargets": {},
                                    "MorphedNPCs": {}
                                  }
                                  """;

        var project = service.LoadFromString(legacyJson);
        var saved = service.SaveToString(project);

        project.AssignmentStrategy.Should().BeNull();
        saved.Should().NotContain("AssignmentStrategy");
    }

    [Fact]
    public void ProjectSaveAndLoadRoundTripsAssignmentStrategyAfterLegacyFields()
    {
        var service = new ProjectFileService();
        var project = new ProjectModel
        {
            AssignmentStrategy = new AssignmentStrategyDefinition(
                AssignmentStrategyDefinition.CurrentSchemaVersion,
                AssignmentStrategyKind.Weighted,
                42,
                new[]
                {
                    new AssignmentStrategyRule("Nord Warriors", PresetAAndPresetB, NordRaceUpper, 3.25, "Warriors")
                })
        };
        project.SliderPresets.Add(new SliderPreset("PresetA"));

        var saved = service.SaveToString(project);
        var loaded = service.LoadFromString(saved);

        saved.Should().Contain("\"schemaVersion\": 1");
        saved.IndexOf("\"CustomProfiles\"", StringComparison.Ordinal).Should().BeNegative();
        saved.IndexOf("\"AssignmentStrategy\"", StringComparison.Ordinal)
            .Should().BeGreaterThan(saved.IndexOf("\"MorphedNPCs\"", StringComparison.Ordinal));
        loaded.AssignmentStrategy.Should().NotBeNull();
        loaded.AssignmentStrategy!.SchemaVersion.Should().Be(1);
        loaded.AssignmentStrategy.Kind.Should().Be(AssignmentStrategyKind.Weighted);
        loaded.AssignmentStrategy.Seed.Should().Be(42);
        loaded.AssignmentStrategy.Rules.Should().ContainSingle().Which.Should().BeEquivalentTo(
            new AssignmentStrategyRule("Nord Warriors", PresetAAndPresetB, NordRaceUpper, 3.25, "Warriors"));
    }

    [Fact]
    public void AssignmentStrategySetterAndReplaceWithPreserveDirtyTrackingAndCloneData()
    {
        var strategy = new AssignmentStrategyDefinition(
            1,
            AssignmentStrategyKind.RoundRobin,
            null,
            new[] { new AssignmentStrategyRule("Round", PresetA, Array.Empty<string>(), 1.0, null) });
        var source = new ProjectModel { AssignmentStrategy = strategy };
        var target = new ProjectModel();
        source.MarkClean();

        source.AssignmentStrategy = strategy with { Seed = 99 };
        target.ReplaceWith(source);

        source.IsDirty.Should().BeTrue();
        target.IsDirty.Should().BeFalse();
        target.AssignmentStrategy.Should().BeEquivalentTo(source.AssignmentStrategy);
        target.AssignmentStrategy.Should().NotBeSameAs(source.AssignmentStrategy);
    }

    [Theory]
    [InlineData("{ \"schemaVersion\": 2, \"kind\": \"Weighted\", \"rules\": [] }", "AssignmentStrategyUnsupportedSchemaVersion")]
    [InlineData("{ \"schemaVersion\": 1, \"kind\": \"Weighted\", \"rules\": [{ \"name\": \"Only\", \"presetNames\": [\"PresetA\"], \"weight\": 0 }] }", "AssignmentStrategyInvalid")]
    [InlineData("{ \"schemaVersion\": 1, \"kind\": \"Weighted\", \"rules\": [{ \"name\": \"Only\", \"presetNames\": [\"PresetA\"], \"weight\": -1 }] }", "AssignmentStrategyInvalid")]
    [InlineData("{ \"schemaVersion\": 1, \"kind\": \"Weighted\", \"rules\": [{ \"name\": \"Only\", \"presetNames\": [\"PresetA\"], \"weight\": 1e999 }] }", "AssignmentStrategyInvalid")]
    [InlineData("{ \"schemaVersion\": 1, \"kind\": \"Weighted\", \"rules\": [{ \"name\": \"Dup\", \"presetNames\": [\"PresetA\"], \"weight\": 1 }, { \"name\": \"dup\", \"presetNames\": [\"PresetB\"], \"weight\": 1 }] }", "AssignmentStrategyInvalid")]
    [InlineData("{ \"schemaVersion\": 1, \"kind\": \"RaceFilters\", \"rules\": [{ \"name\": \"NoRace\", \"presetNames\": [\"PresetA\"], \"raceFilters\": [] }] }", "AssignmentStrategyInvalid")]
    [InlineData("{ \"schemaVersion\": 1, \"kind\": \"GroupsBuckets\", \"rules\": [{ \"name\": \"NoBucket\", \"presetNames\": [\"PresetA\"], \"raceFilters\": [] }] }", "AssignmentStrategyInvalid")]
    public void InvalidAssignmentStrategyReportsDiagnosticAndKeepsLegacyFields(string strategyJson, string expectedCode)
    {
        var service = new ProjectFileService();
        var json = $$"""
                   {
                     "SliderPresets": {
                       "PresetA": { "isUUNP": false, "SetSliders": [] }
                     },
                     "CustomMorphTargets": {},
                     "MorphedNPCs": {},
                     "AssignmentStrategy": {{strategyJson}}
                   }
                   """;

        var result = service.LoadWithDiagnosticsFromString(json);

        result.Project.SliderPresets.Should().ContainSingle(preset => preset.Name == "PresetA");
        result.Project.AssignmentStrategy.Should().BeNull();
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == expectedCode
                                                         && diagnostic.Message.Contains("assignment strategy", StringComparison.OrdinalIgnoreCase));
    }
}
