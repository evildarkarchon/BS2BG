using System.Reactive.Linq;
using System.Reactive.Threading.Tasks;
using BS2BG.App;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

public sealed class MorphsViewModelStrategyTests
{
    private static readonly string[] NordRaceFilter = { "NordRace" };

    [Fact]
    public async Task SavingStrategyConfigurationUpdatesProjectAndMarksDirty()
    {
        var project = CreateProjectWithPresetsAndNpcs();
        project.MarkClean();
        var viewModel = CreateViewModel(project, new SequenceRandomAssignmentProvider());

        viewModel.SelectedAssignmentStrategyKind = AssignmentStrategyKind.SeededRandom;
        viewModel.StrategySeedText = "42";

        await viewModel.SaveStrategyConfigurationCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        project.AssignmentStrategy.Should().NotBeNull();
        project.AssignmentStrategy!.Kind.Should().Be(AssignmentStrategyKind.SeededRandom);
        project.AssignmentStrategy.Seed.Should().Be(42);
        project.IsDirty.Should().BeTrue();
        viewModel.StrategySummaryText.Should().Contain("2 NPC rows in the full project");
        viewModel.StrategySummaryText.Should().Contain("Saved in project; reproducible in CLI and bundles.");
    }

    [Fact]
    public async Task ApplyStrategyMutatesAssignmentsAndRecordsSingleUndoRestoringStrategy()
    {
        var project = CreateProjectWithPresetsAndNpcs();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        foreach (var npc in project.MorphedNpcs) npc.AddSliderPreset(alpha);
        project.AssignmentStrategy = new AssignmentStrategyDefinition(1, AssignmentStrategyKind.SeededRandom, 9, []);
        project.MarkClean();
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new SequenceRandomAssignmentProvider(1, 0), undoRedo);

        viewModel.SelectedAssignmentStrategyKind = AssignmentStrategyKind.RoundRobin;
        viewModel.StrategySeedText = string.Empty;

        await viewModel.ApplyStrategyCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        project.MorphedNpcs.Select(npc => npc.SliderPresets.Single().Name).Should().Equal("Beta", "Alpha");
        project.AssignmentStrategy!.Kind.Should().Be(AssignmentStrategyKind.RoundRobin);
        undoRedo.CanUndo.Should().BeTrue();

        undoRedo.Undo().Should().BeTrue();

        project.MorphedNpcs.Select(npc => npc.SliderPresets.Single().Name).Should().Equal("Alpha", "Alpha");
        project.AssignmentStrategy!.Kind.Should().Be(AssignmentStrategyKind.SeededRandom);
        project.AssignmentStrategy.Seed.Should().Be(9);
    }

    [Fact]
    public async Task StrategyRuleGapsReportVisibleNoEligiblePresetCopy()
    {
        var project = CreateProjectWithPresetsAndNpcs();
        var viewModel = CreateViewModel(project, new SequenceRandomAssignmentProvider());
        viewModel.SelectedAssignmentStrategyKind = AssignmentStrategyKind.RaceFilters;
        viewModel.StrategyRules.Add(new AssignmentStrategyRuleRowViewModel
        {
            Name = "Only elves",
            PresetNamesText = "Alpha",
            RaceFiltersText = "ElfRace",
            Weight = 1
        });

        await viewModel.ApplyStrategyCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        viewModel.StrategyValidationMessage.Should().Contain("No eligible preset after strategy rules");
        viewModel.StatusMessage.Should().Contain("No eligible preset after strategy rules");
    }

    [Fact]
    public void InvalidStrategyRowsDisableApplyWithVisibleValidationText()
    {
        var project = CreateProjectWithPresetsAndNpcs();
        var viewModel = CreateViewModel(project, new SequenceRandomAssignmentProvider());
        viewModel.SelectedAssignmentStrategyKind = AssignmentStrategyKind.GroupsBuckets;
        viewModel.StrategyRules.Add(new AssignmentStrategyRuleRowViewModel
        {
            Name = "Bucket one",
            PresetNamesText = "MissingPreset",
            BucketName = "Shared",
            Weight = 1001
        });
        viewModel.StrategyRules.Add(new AssignmentStrategyRuleRowViewModel
        {
            Name = "Bucket two",
            PresetNamesText = "Alpha",
            BucketName = "shared",
            Weight = 1
        });

        viewModel.RefreshStrategyValidation();

        viewModel.StrategyValidationMessage.Should().Contain("Unknown preset 'MissingPreset'");
        viewModel.StrategyValidationMessage.Should().Contain("Weight must be between 0 and 1000");
        viewModel.StrategyValidationMessage.Should().Contain("Bucket name 'Shared' is duplicated");
        viewModel.ApplyStrategyCommand.CanExecute.FirstAsync().Wait().Should().BeFalse();
    }

    [Fact]
    public async Task StrategySummaryAndApplyUseFullProjectNotVisibleScope()
    {
        var project = CreateProjectWithPresetsAndNpcs();
        var viewModel = CreateViewModel(project, new SequenceRandomAssignmentProvider());
        viewModel.SetNpcColumnAllowedValues(BS2BG.App.ViewModels.Workflow.NpcFilterColumn.Race, NordRaceFilter);
        viewModel.VisibleNpcs.Should().ContainSingle();

        viewModel.StrategySummaryText.Should().Contain("2 NPC rows in the full project");

        await viewModel.ApplyStrategyCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        project.MorphedNpcs.Should().OnlyContain(npc => npc.HasPresets);
    }

    [Fact]
    public async Task ConfigurationOnlySaveIsUndoable()
    {
        var project = CreateProjectWithPresetsAndNpcs();
        project.AssignmentStrategy = new AssignmentStrategyDefinition(1, AssignmentStrategyKind.SeededRandom, 5, []);
        project.MarkClean();
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new SequenceRandomAssignmentProvider(), undoRedo);

        viewModel.SelectedAssignmentStrategyKind = AssignmentStrategyKind.RoundRobin;
        await viewModel.SaveStrategyConfigurationCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        project.AssignmentStrategy!.Kind.Should().Be(AssignmentStrategyKind.RoundRobin);
        undoRedo.Undo().Should().BeTrue();
        project.AssignmentStrategy!.Kind.Should().Be(AssignmentStrategyKind.SeededRandom);
        project.AssignmentStrategy.Seed.Should().Be(5);
    }

    [Fact]
    public void InvalidLoadedStrategyShowsPanelMessageAndDisablesApplyUntilSaved()
    {
        var project = CreateProjectWithPresetsAndNpcs();
        var salvage = new AssignmentStrategyDefinition(
            1,
            AssignmentStrategyKind.Weighted,
            null,
            [new AssignmentStrategyRule("Bad", ["Alpha"], [], 0, null)]);
        var viewModel = CreateViewModel(project, new SequenceRandomAssignmentProvider());

        viewModel.ApplyProjectLoadDiagnostics([
            new ProjectLoadDiagnostic("AssignmentStrategyInvalid", "Assignment strategy weighted rules require at least one positive weight.", null, salvage)
        ]);

        viewModel.InvalidLoadedStrategyMessage.Should().Contain("Assignment strategy weighted rules require");
        viewModel.StrategyRules.Should().ContainSingle().Which.ValidationMessage.Should().Contain("Weight must be greater than 0");
        viewModel.ApplyStrategyCommand.CanExecute.FirstAsync().Wait().Should().BeFalse();

        viewModel.StrategyRules.Single().Weight = 1;
        viewModel.SaveStrategyConfigurationCommand.Execute().Subscribe();

        viewModel.InvalidLoadedStrategyMessage.Should().BeEmpty();
        viewModel.ApplyStrategyCommand.CanExecute.FirstAsync().Wait().Should().BeTrue();
    }

    [Fact]
    public void AppBootstrapperRegistersAssignmentStrategyService()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        provider.GetRequiredService<AssignmentStrategyService>().Should().NotBeNull();
    }

    [Fact]
    public void MainWindowContainsCompiledBoundAccessibleStrategyPanel()
    {
        var axaml = File.ReadAllText(Path.Combine(FindRepositoryRoot(), "src", "BS2BG.App", "Views", "MainWindow.axaml"));

        axaml.Should().Contain("Apply Strategy");
        axaml.Should().Contain("Save Strategy");
        axaml.Should().Contain("Seeded random");
        axaml.Should().Contain("Round-robin");
        axaml.Should().Contain("Weighted");
        axaml.Should().Contain("Race filters");
        axaml.Should().Contain("Groups / buckets");
        axaml.Should().Contain("comma-separated preset/race");
        axaml.Should().Contain("all NPC rows in the project");
        axaml.Should().Contain("InvalidLoadedStrategyMessage");
        axaml.Should().Contain("AutomationProperties.Name=\"Apply Strategy\"");
        axaml.Should().Contain("AutomationProperties.Name=\"Save Strategy\"");
        axaml.Should().Contain("x:DataType=\"vm:AssignmentStrategyRuleRowViewModel\"");
        axaml.Contains("setup wizard", StringComparison.OrdinalIgnoreCase).Should().BeFalse();
    }

    private static MorphsViewModel CreateViewModel(
        ProjectModel project,
        IRandomAssignmentProvider randomProvider,
        UndoRedoService? undoRedo = null)
    {
        return new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(randomProvider),
            new MorphGenerationService(),
            new EmptyNpcTextFilePicker(),
            new EmptyClipboardService(),
            undoRedo: undoRedo,
            assignmentStrategyService: new AssignmentStrategyService(randomProvider));
    }

    private static ProjectModel CreateProjectWithPresetsAndNpcs()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Alpha"));
        project.SliderPresets.Add(new SliderPreset("Beta"));
        project.MorphedNpcs.Add(new Npc("Lydia") { Mod = "Skyrim.esm", EditorId = "HousecarlWhiterun", Race = "NordRace", FormId = "000A2C94" });
        project.MorphedNpcs.Add(new Npc("Serana") { Mod = "Dawnguard.esm", EditorId = "DLC1Serana", Race = "NordRaceVampire", FormId = "02002B74" });
        return project;
    }

    private static string FindRepositoryRoot()
    {
        var directory = AppContext.BaseDirectory;
        while (directory is not null && !File.Exists(Path.Combine(directory, "BS2BG.sln")))
            directory = Directory.GetParent(directory)?.FullName;

        return directory ?? throw new DirectoryNotFoundException("Could not locate repository root.");
    }

    private sealed class SequenceRandomAssignmentProvider(params int[] values) : IRandomAssignmentProvider
    {
        private readonly Queue<int> values = new(values);

        public int NextIndex(int exclusiveMax) => values.Count == 0 ? 0 : values.Dequeue();
    }

    private sealed class EmptyNpcTextFilePicker : INpcTextFilePicker
    {
        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>([]);
    }

    private sealed class EmptyClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.CompletedTask;
    }
}
