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

    [Fact]
    public void SeededRandomReplaysSameNpcPresetSequenceForSameInputs()
    {
        var first = CreateStrategyProject();
        var second = CreateStrategyProject();
        var strategy = new AssignmentStrategyDefinition(1, AssignmentStrategyKind.SeededRandom, 123, Array.Empty<AssignmentStrategyRule>());

        var firstResult = AssignmentStrategyService.Apply(first, strategy);
        var secondResult = AssignmentStrategyService.Apply(second, strategy);

        firstResult.AssignedCount.Should().Be(3);
        secondResult.AssignedCount.Should().Be(3);
        AssignmentSnapshot(first).Should().Equal("Aela=PresetA", "Codsworth=PresetC", "Danica=PresetC");
        AssignmentSnapshot(second).Should().Equal(AssignmentSnapshot(first));
    }

    [Fact]
    public void RoundRobinAssignsPresetsInStableProjectPresetOrder()
    {
        var project = CreateStrategyProject();
        var strategy = new AssignmentStrategyDefinition(1, AssignmentStrategyKind.RoundRobin, null, Array.Empty<AssignmentStrategyRule>());

        var result = AssignmentStrategyService.Apply(project, strategy);

        result.AssignedCount.Should().Be(3);
        AssignmentSnapshot(project).Should().Equal("Aela=PresetA", "Codsworth=PresetB", "Danica=PresetC");
    }

    [Fact]
    public void ComputeEligibilityReportsSameBlockedNpcListUsedByApply()
    {
        var project = CreateStrategyProject();
        var strategy = new AssignmentStrategyDefinition(
            1,
            AssignmentStrategyKind.RaceFilters,
            null,
            new[] { new AssignmentStrategyRule("Nords", new[] { "PresetA" }, new[] { "NordRace" }, 1.0, null) });

        var eligibility = AssignmentStrategyService.ComputeEligibility(project, strategy, project.MorphedNpcs);
        var result = AssignmentStrategyService.Apply(project, strategy);

        eligibility.BlockedNpcs.Select(blocked => blocked.Npc.Name)
            .Should().Equal(result.BlockedNpcs.Select(blocked => blocked.Npc.Name));
        result.BlockedNpcs.Select(blocked => blocked.Npc.Name).Should().Equal("Codsworth", "Danica");
        project.MorphedNpcs.Single(npc => npc.Name == "Codsworth").SliderPresets.Should().BeEmpty();
    }

    [Fact]
    public void SeededStrategyCodeUsesProviderSeamWithoutSystemRandomSeedReplay()
    {
        var provider = new SequenceRandomAssignmentProvider(2, 1, 0);
        var project = CreateStrategyProject();
        var service = new AssignmentStrategyService(provider);
        var strategy = new AssignmentStrategyDefinition(1, AssignmentStrategyKind.SeededRandom, null, Array.Empty<AssignmentStrategyRule>());

        service.Apply(project, strategy);

        AssignmentSnapshot(project).Should().Equal("Aela=PresetC", "Codsworth=PresetB", "Danica=PresetA");
        File.ReadAllText(Path.Combine("src", "BS2BG.Core", "Morphs", "AssignmentStrategyService.cs"))
            .Should().NotContain("new Random(seed)");
    }

    [Theory]
    [MemberData(nameof(DeterministicProviderVectors))]
    public void DeterministicAssignmentRandomProviderHasPinnedReferenceVectors(int seed, int[] expected)
    {
        var provider = new DeterministicAssignmentRandomProvider(seed);

        Enumerable.Range(0, expected.Length)
            .Select(_ => provider.NextIndex(1000))
            .Should().Equal(expected.AsEnumerable());
    }

    public static IEnumerable<object[]> DeterministicProviderVectors()
    {
        yield return new object[] { 0, new[] { 738, 247, 56, 444, 716 } };
        yield return new object[] { 1, new[] { 67, 833, 787, 821, 403 } };
        yield return new object[] { 123, new[] { 976, 775, 934, 63, 641 } };
    }

    private static ProjectModel CreateStrategyProject()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("PresetA"));
        project.SliderPresets.Add(new SliderPreset("PresetB"));
        project.SliderPresets.Add(new SliderPreset("PresetC"));
        project.MorphedNpcs.Add(new Npc("Danica") { Mod = "Skyrim.esm", EditorId = "Danica", FormId = "000003", Race = "BretonRace" });
        project.MorphedNpcs.Add(new Npc("Aela") { Mod = "Skyrim.esm", EditorId = "Aela", FormId = "000001", Race = "NordRace" });
        project.MorphedNpcs.Add(new Npc("Codsworth") { Mod = "Fallout4.esm", EditorId = "Codsworth", FormId = "000002", Race = "MrHandyRace" });
        return project;
    }

    private static string[] AssignmentSnapshot(ProjectModel project) => project.MorphedNpcs
        .OrderBy(npc => npc.Name, StringComparer.OrdinalIgnoreCase)
        .Select(npc => npc.Name + "=" + npc.SliderPresetsText)
        .ToArray();

    private sealed class SequenceRandomAssignmentProvider(params int[] values) : IRandomAssignmentProvider
    {
        private int index;

        public int NextIndex(int exclusiveMax)
        {
            exclusiveMax.Should().BePositive();
            var value = values[index++ % values.Length];
            return value % exclusiveMax;
        }
    }
}
