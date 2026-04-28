using BS2BG.Core.Automation;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using Xunit;

namespace BS2BG.Tests;

public sealed class AssignmentStrategyReplayServiceTests
{
    private static readonly string[] NordRace = { "NordRace" };
    private static readonly string[] MrHandyRace = { "MrHandyRace" };
    private static readonly string[] PresetA = { "PresetA" };
    private static readonly string[] PresetB = { "PresetB" };

    [Theory]
    [InlineData(OutputIntent.BodyGen)]
    [InlineData(OutputIntent.All)]
    public void PrepareForBodyGenReplaysSavedStrategyForBodyGenIntents(OutputIntent intent)
    {
        var project = CreateProjectWithStaleAssignments();
        project.AssignmentStrategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.RoundRobin,
            null,
            Array.Empty<AssignmentStrategyRule>());

        var result = CreateReplayService().PrepareForBodyGen(project, intent, cloneBeforeReplay: true);

        result.Replayed.Should().BeTrue();
        result.StrategyKind.Should().Be(AssignmentStrategyKind.RoundRobin);
        result.AssignedCount.Should().Be(3);
        result.BlockedNpcs.Should().BeEmpty();
        result.IsBlocked.Should().BeFalse();
        AssignmentSnapshot(result.Project).Should().Equal("Aela=PresetB", "Codsworth=PresetA", "Danica=PresetC");
    }

    [Fact]
    public void PrepareForBodyGenDoesNotReplayBosJsonIntentAndPreservesStaleAssignments()
    {
        var project = CreateProjectWithStaleAssignments();
        project.AssignmentStrategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.RoundRobin,
            null,
            Array.Empty<AssignmentStrategyRule>());

        var result = CreateReplayService().PrepareForBodyGen(project, OutputIntent.BosJson, cloneBeforeReplay: true);

        result.Replayed.Should().BeFalse();
        result.StrategyKind.Should().BeNull();
        result.AssignedCount.Should().Be(0);
        result.BlockedNpcs.Should().BeEmpty();
        AssignmentSnapshot(result.Project).Should().Equal("Aela=PresetC", "Codsworth=PresetC", "Danica=PresetC");
    }

    [Fact]
    public void PrepareForBodyGenNoStrategyClonesAndKeepsExistingAssignments()
    {
        var project = CreateProjectWithStaleAssignments();
        project.AssignmentStrategy = null;

        var result = CreateReplayService().PrepareForBodyGen(project, OutputIntent.BodyGen, cloneBeforeReplay: true);

        result.Replayed.Should().BeFalse();
        result.Project.Should().NotBeSameAs(project);
        result.Project.MorphedNpcs[0].Should().NotBeSameAs(project.MorphedNpcs[0]);
        result.AssignedCount.Should().Be(0);
        result.BlockedNpcs.Should().BeEmpty();
        AssignmentSnapshot(result.Project).Should().Equal(AssignmentSnapshot(project));
    }

    [Fact]
    public void PrepareForBodyGenBlockedReplayExposesBlockedNpcAndForbidsConsumingPartialProject()
    {
        var project = CreateProjectWithStaleAssignments();
        project.AssignmentStrategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.RaceFilters,
            null,
            new[]
            {
                new AssignmentStrategyRule("Only Nords", PresetA, NordRace, 1.0, null),
                new AssignmentStrategyRule("Only Robots", PresetB, MrHandyRace, 1.0, null)
            });

        var result = CreateReplayService().PrepareForBodyGen(project, OutputIntent.BodyGen, cloneBeforeReplay: true);

        result.Replayed.Should().BeTrue();
        result.IsBlocked.Should().BeTrue("callers must not generate output from a partially replayed project");
        result.AssignedCount.Should().Be(2);
        var blockedNpc = result.BlockedNpcs.Should().ContainSingle().Subject.Npc;
        blockedNpc.Mod.Should().Be("Skyrim.esm");
        blockedNpc.Name.Should().Be("Danica");
        blockedNpc.EditorId.Should().Be("Danica");
        blockedNpc.Race.Should().Be("BretonRace");
        blockedNpc.FormId.Should().Be("3");
        AssignmentSnapshot(result.Project).Should().Equal("Aela=PresetA", "Codsworth=PresetB", "Danica=PresetC");
    }

    [Fact]
    public void PrepareForBodyGenCloneBeforeReplayLeavesSourceProjectUnchanged()
    {
        var project = CreateProjectWithStaleAssignments();
        project.AssignmentStrategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.RoundRobin,
            null,
            Array.Empty<AssignmentStrategyRule>());
        project.MarkClean();
        var originalChangeVersion = project.ChangeVersion;
        var originalStrategy = project.AssignmentStrategy;
        var originalAssignments = AssignmentSnapshot(project);

        var result = CreateReplayService().PrepareForBodyGen(project, OutputIntent.BodyGen, cloneBeforeReplay: true);

        result.Project.Should().NotBeSameAs(project);
        AssignmentSnapshot(result.Project).Should().NotEqual(originalAssignments);
        AssignmentSnapshot(project).Should().Equal(originalAssignments);
        project.AssignmentStrategy.Should().BeEquivalentTo(originalStrategy);
        project.IsDirty.Should().BeFalse();
        project.ChangeVersion.Should().Be(originalChangeVersion);
    }

    [Fact]
    public void PrepareForBodyGenWithoutCloneMutatesSourceProjectAndPreservesStrategy()
    {
        var project = CreateProjectWithStaleAssignments();
        project.AssignmentStrategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.RoundRobin,
            null,
            Array.Empty<AssignmentStrategyRule>());
        var strategy = project.AssignmentStrategy;

        var result = CreateReplayService().PrepareForBodyGen(project, OutputIntent.BodyGen, cloneBeforeReplay: false);

        result.Project.Should().BeSameAs(project);
        project.AssignmentStrategy.Should().BeEquivalentTo(strategy);
        AssignmentSnapshot(project).Should().Equal("Aela=PresetB", "Codsworth=PresetA", "Danica=PresetC");
    }

    [Fact]
    public void PrepareForBodyGenSeededStrategyHasPinnedDeterministicReplaySequence()
    {
        var first = CreateProjectWithStaleAssignments();
        var second = CreateProjectWithStaleAssignments();
        var strategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.SeededRandom,
            123,
            Array.Empty<AssignmentStrategyRule>());
        first.AssignmentStrategy = strategy;
        second.AssignmentStrategy = strategy;

        var firstResult = CreateReplayService().PrepareForBodyGen(first, OutputIntent.BodyGen, cloneBeforeReplay: true);
        var secondResult = CreateReplayService().PrepareForBodyGen(second, OutputIntent.BodyGen, cloneBeforeReplay: true);

        AssignmentSnapshot(firstResult.Project).Should().Equal("Aela=PresetA", "Codsworth=PresetB", "Danica=PresetB");
        AssignmentSnapshot(secondResult.Project).Should().Equal(AssignmentSnapshot(firstResult.Project));
    }

    [Fact]
    public void PrepareForBodyGenEmptyNpcListIsSuccessfulNoOpReplay()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("PresetA"));
        project.AssignmentStrategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.RoundRobin,
            null,
            Array.Empty<AssignmentStrategyRule>());

        var result = CreateReplayService().PrepareForBodyGen(project, OutputIntent.BodyGen, cloneBeforeReplay: true);

        result.Replayed.Should().BeTrue();
        result.AssignedCount.Should().Be(0);
        result.BlockedNpcs.Should().BeEmpty();
        result.IsBlocked.Should().BeFalse();
    }

    [Fact]
    public void MorphAssignmentServiceSeededApplyStrategyHonorsEligibleRowsScope()
    {
        var project = CreateProjectWithStaleAssignments();
        var eligibleRows = project.MorphedNpcs.Where(npc => npc.Name is "Aela" or "Danica").ToArray();
        var strategy = new AssignmentStrategyDefinition(
            AssignmentStrategyDefinition.CurrentSchemaVersion,
            AssignmentStrategyKind.SeededRandom,
            123,
            Array.Empty<AssignmentStrategyRule>());

        var result = CreateReplayService().MorphAssignmentService.ApplyStrategy(project, strategy, eligibleRows);

        result.AssignedCount.Should().Be(2);
        AssignmentSnapshot(project).Should().Equal("Aela=PresetB", "Codsworth=PresetC", "Danica=PresetA");
    }

    private static TestReplayServices CreateReplayService()
    {
        var morphAssignmentService = new MorphAssignmentService(new SequenceRandomAssignmentProvider(0));
        return new TestReplayServices(new AssignmentStrategyReplayService(morphAssignmentService), morphAssignmentService);
    }

    private static ProjectModel CreateProjectWithStaleAssignments()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("PresetA"));
        project.SliderPresets.Add(new SliderPreset("PresetB"));
        var stale = new SliderPreset("PresetC");
        project.SliderPresets.Add(stale);
        project.MorphedNpcs.Add(CreateNpc("Danica", "Skyrim.esm", "Danica", "000003", "BretonRace", stale));
        project.MorphedNpcs.Add(CreateNpc("Aela", "Skyrim.esm", "Aela", "000001", "NordRace", stale));
        project.MorphedNpcs.Add(CreateNpc("Codsworth", "Fallout4.esm", "Codsworth", "000002", "MrHandyRace", stale));
        return project;
    }

    private static Npc CreateNpc(string name, string mod, string editorId, string formId, string race, SliderPreset preset)
    {
        var npc = new Npc(name) { Mod = mod, EditorId = editorId, FormId = formId, Race = race };
        npc.AddSliderPreset(preset);
        return npc;
    }

    private static string[] AssignmentSnapshot(ProjectModel project) => project.MorphedNpcs
        .OrderBy(npc => npc.Name, StringComparer.OrdinalIgnoreCase)
        .Select(npc => npc.Name + "=" + npc.SliderPresetsText)
        .ToArray();

    private sealed record TestReplayServices(
        AssignmentStrategyReplayService ReplayService,
        MorphAssignmentService MorphAssignmentService)
    {
        public AssignmentStrategyReplayResult PrepareForBodyGen(
            ProjectModel sourceProject,
            OutputIntent intent,
            bool cloneBeforeReplay) => ReplayService.PrepareForBodyGen(sourceProject, intent, cloneBeforeReplay);
    }

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
