using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.Reactive.Concurrency;
using System.Text.Json;
using System.Windows.Input;
using BS2BG.App;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.ViewModels.Workflow;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using Microsoft.Extensions.DependencyInjection;
using Xunit;
using WorkflowNpcFilterColumn = BS2BG.App.ViewModels.Workflow.NpcFilterColumn;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected sequences keep ViewModel assertions readable.")]
public sealed class MorphsViewModelTests
{
    [Fact]
    public async Task ImportAndAssignmentCommandsPopulateTargetsNpcsAndGeneratedMorphs()
    {
        using var directory = new TemporaryDirectory();
        var npcFile = directory.WriteText(
            "npcs.txt",
            """
            Skyrim.esm|Lydia|HousecarlWhiterun|NordRace|000A2C94
            Dawnguard.esm|Valerica|DLC1Valerica|NordRaceVampire|02002B6C
            """);
        var project = CreateProjectWithPresets();
        var clipboard = new CapturingClipboardService();
        var viewModel = CreateViewModel(
            project,
            new QueueRandomAssignmentProvider(1, 0),
            new StaticNpcTextFilePicker(new[] { npcFile }),
            clipboard);

        await viewModel.ImportNpcsAsync(TestContext.Current.CancellationToken);
        viewModel.TargetNameInput = "All|Female";
        viewModel.AddCustomTarget().Should().BeTrue();
        viewModel.SelectedAvailablePreset = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        viewModel.AddSelectedPresetToTarget().Should().BeTrue();
        viewModel.AssignRandomOnAdd = true;
        viewModel.SelectedImportedNpc = viewModel.NpcDatabase.Single(npc => npc.Name == "Lydia");
        viewModel.AddSelectedNpc().Should().BeTrue();

        viewModel.GenerateMorphs();
        await viewModel.CopyGeneratedMorphsAsync(TestContext.Current.CancellationToken);

        viewModel.NpcDatabase.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        project.CustomMorphTargets.Single().SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha", "Beta");
        viewModel.SelectedTargetName.Should().Be("Lydia");
        viewModel.TargetPresetCountText.Should().Be("1");
        viewModel.NpcCountBadgeText.Should().Be("(1)");
        viewModel.GeneratedMorphsText.Should().Be("All|Female=Alpha|Beta\r\nSkyrim.esm|A2C94=Alpha");
        clipboard.Text.Should().Be(viewModel.GeneratedMorphsText);
    }

    [Fact]
    public async Task ImportNpcFilesSkipsUnreadableFilesAndContinuesImporting()
    {
        using var directory = new TemporaryDirectory();
        var missingFile = Path.Combine(
            Path.GetTempPath(),
            Guid.NewGuid().ToString("N"),
            "missing-npcs.txt");
        var validFile = directory.WriteText(
            "npcs.txt",
            "Skyrim.esm|Lydia|HousecarlWhiterun|NordRace|000A2C94");
        var viewModel = CreateViewModel(CreateProjectWithPresets(), new QueueRandomAssignmentProvider());

        await viewModel.ImportNpcFilesAsync(
            new[] { missingFile, validFile },
            TestContext.Current.CancellationToken);

        var npc = viewModel.NpcDatabase.Should().ContainSingle().Which;
        npc.Name.Should().Be("Lydia");
        viewModel.StatusMessage.Should().Be("Imported 1 NPC. 1 issue was skipped.");
    }

    [Fact]
    public async Task PreviewNpcImportCommandPopulatesRowsWithoutMutatingProjectOrUndoHistory()
    {
        using var directory = new TemporaryDirectory();
        var npcFile = directory.WriteText(
            "npcs.txt",
            "Skyrim.esm|Lydia|HousecarlWhiterun|NordRace|000A2C94");
        var project = CreateProjectWithPresets();
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(
            project,
            new QueueRandomAssignmentProvider(),
            new StaticNpcTextFilePicker(new[] { npcFile }),
            undoRedo: undoRedo);
        var originalVersion = project.ChangeVersion;

        await viewModel.PreviewNpcImportCommand.Execute();

        viewModel.NpcDatabase.Count.Should().Be(0);
        viewModel.VisibleNpcDatabase.Count.Should().Be(0);
        viewModel.Npcs.Count.Should().Be(0);
        project.IsDirty.Should().BeFalse();
        project.ChangeVersion.Should().Be(originalVersion);
        undoRedo.CanUndo.Should().BeFalse();
        viewModel.NpcImportPreviewRows.Should().ContainSingle();
        viewModel.NpcImportPreviewSummary.Should().Contain("1 row to add");
        viewModel.HasNpcImportPreview.Should().BeTrue();
    }

    [Fact]
    public void AppBootstrapperRegistersNpcImportPreviewService()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        provider.GetRequiredService<NpcImportPreviewService>().Should().NotBeNull();
    }

    [Fact]
    public void FilteredBulkOperationsOnlyAffectVisibleNpcs()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var beta = project.SliderPresets.Single(preset => preset.Name == "Beta");
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var serana = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        serana.AddSliderPreset(beta);
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        valerica.AddSliderPreset(beta);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(serana);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(0));

        viewModel.SearchText = "Skyrim";
        var filled = viewModel.FillEmptyVisibleNpcs(new[] { alpha });
        var cleared = viewModel.ClearVisibleNpcAssignments();

        filled.Should().Be(1);
        cleared.Should().Be(2);
        lydia.SliderPresets.Should().BeEmpty();
        serana.SliderPresets.Should().BeEmpty();
        valerica.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");
        viewModel.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Serana");
    }

    [Fact]
    public void NpcColumnFiltersCoverRequiredFieldsAndPreserveMorphGenerationModels()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var beta = project.SliderPresets.Single(preset => preset.Name == "Beta");
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        lydia.AddSliderPreset(alpha);
        var serana = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        serana.AddSliderPreset(beta);
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(serana);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Mod, new[] { "Skyrim.esm" });
        viewModel.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Serana");

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Name, new[] { "Serana" });
        viewModel.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Serana");

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.EditorId, new[] { "DLC1Serana" });
        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.FormId, new[] { "2B74" });
        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Race, new[] { "NordRaceVampire" });
        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.AssignmentState, new[] { NpcFilterState.AssignedValue });
        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Preset, new[] { "Beta" });

        viewModel.VisibleNpcs.Should().ContainSingle().Which.Should().BeSameAs(serana);

        viewModel.GenerateMorphs();
        viewModel.GeneratedMorphsText.Should().Contain("Skyrim.esm|2B74=Beta");
    }

    [Fact]
    public void ChecklistFiltersApplyImmediatelyWhileFreeTextSearchWaitsForDebounce()
    {
        using var scheduler = new EventLoopScheduler();
        var project = CreateProjectWithPresets();
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var serana = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(serana);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), filterScheduler: scheduler);

        viewModel.SearchText = "Valerica";
        viewModel.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Serana", "Valerica");

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Mod, new[] { "Skyrim.esm" });

        viewModel.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Serana");
        SpinWait.SpinUntil(() => viewModel.VisibleNpcs.Count == 0, TimeSpan.FromSeconds(3)).Should().BeTrue();
    }

    [Fact]
    public void ImportedNpcDatabaseFreeTextFilterProjectsVisibleNpcDatabaseAfterDebounce()
    {
        using var scheduler = new EventLoopScheduler();
        var viewModel = CreateViewModel(CreateProjectWithPresets(), new QueueRandomAssignmentProvider(), filterScheduler: scheduler);
        viewModel.NpcDatabase.Add(CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94"));
        viewModel.NpcDatabase.Add(CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C"));

        viewModel.NpcDatabaseSearchText = "DLC1Valerica";

        viewModel.VisibleNpcDatabase.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        SpinWait.SpinUntil(() => viewModel.VisibleNpcDatabase.Count == 1, TimeSpan.FromSeconds(3)).Should().BeTrue();
        viewModel.VisibleNpcDatabase.Should().ContainSingle().Which.Name.Should().Be("Valerica");
    }

    [Fact]
    public void ClearVisibleNpcsClearsSelectedNpcWhenSelectionIsRemoved()
    {
        var project = CreateProjectWithPresets();
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());

        viewModel.SelectedNpc = lydia;
        viewModel.SearchText = "Skyrim";
        var removed = viewModel.ClearVisibleNpcs();

        removed.Should().Be(1);
        project.MorphedNpcs.Should().NotContain(lydia);
        project.MorphedNpcs.Should().Contain(valerica);
        viewModel.SelectedNpc.Should().BeNull();
        viewModel.SelectedTarget.Should().BeNull();
    }

    [Fact]
    public void HiddenSelectedNpcsRemainSelectedWhenVisibleSelectionChanges()
    {
        var project = CreateProjectWithPresets();
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());
        viewModel.SelectedNpcs.Add(lydia);
        viewModel.SelectedNpcs.Add(valerica);

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Mod, new[] { "Skyrim.esm" });
        viewModel.UpdateVisibleNpcSelection(new[] { lydia });

        viewModel.VisibleNpcs.Should().ContainSingle().Which.Should().BeSameAs(lydia);
        viewModel.SelectedNpcs.Should().Equal(lydia, valerica);
        viewModel.StatusMessage.Should().Be("1 visible, 2 selected (1 hidden by filters)");
    }

    [Fact]
    public void NpcBulkScopeResolverReturnsMaterializedRowIdsForAllVisibleAndSelectedScopes()
    {
        var lydia = new NpcRowViewModel(CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94"));
        var serana = new NpcRowViewModel(CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74"));
        var valerica = new NpcRowViewModel(CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C"));
        var selectedRows = new List<NpcRowViewModel> { serana };

        var all = NpcBulkScopeResolver.Resolve(
            NpcBulkScope.All,
            new[] { lydia, serana, valerica },
            new[] { lydia, serana },
            selectedRows);
        var visible = NpcBulkScopeResolver.Resolve(
            NpcBulkScope.Visible,
            new[] { lydia, serana, valerica },
            new[] { lydia, serana },
            selectedRows);
        var selected = NpcBulkScopeResolver.Resolve(
            NpcBulkScope.Selected,
            new[] { lydia, serana, valerica },
            new[] { lydia, serana },
            selectedRows);

        selectedRows.Clear();

        all.Should().Equal(lydia.RowId, serana.RowId, valerica.RowId);
        visible.Should().Equal(lydia.RowId, serana.RowId);
        selected.Should().Equal(serana.RowId);
    }

    [Fact]
    public void NpcBulkScopeResolverVisibleEmptyExcludesHiddenAndAssignedRows()
    {
        var alpha = new SliderPreset("Alpha");
        var visibleEmpty = new NpcRowViewModel(CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94"));
        var visibleAssigned = new NpcRowViewModel(CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74"));
        var hiddenEmpty = new NpcRowViewModel(CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C"));
        visibleAssigned.Npc.AddSliderPreset(alpha);

        var targetIds = NpcBulkScopeResolver.Resolve(
            NpcBulkScope.VisibleEmpty,
            new[] { visibleEmpty, visibleAssigned, hiddenEmpty },
            new[] { visibleEmpty, visibleAssigned },
            Array.Empty<NpcRowViewModel>());

        targetIds.Should().Equal(visibleEmpty.RowId);
    }

    [Fact]
    public void ScopedFillDefaultsToVisibleEmptyAndLeavesHiddenOrAssignedRowsUnchanged()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var beta = project.SliderPresets.Single(preset => preset.Name == "Beta");
        var visibleEmpty = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var visibleAssigned = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        visibleAssigned.AddSliderPreset(beta);
        var hiddenEmpty = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        project.MorphedNpcs.Add(visibleEmpty);
        project.MorphedNpcs.Add(visibleAssigned);
        project.MorphedNpcs.Add(hiddenEmpty);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(0));
        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Mod, new[] { "Skyrim.esm" });
        viewModel.SelectedAvailablePreset = alpha;

        viewModel.FillEmptyNpcsCommand.Execute().Subscribe();

        visibleEmpty.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        visibleAssigned.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");
        hiddenEmpty.SliderPresets.Should().BeEmpty();
        viewModel.SelectedNpcBulkScope.Should().Be(NpcBulkScope.Visible);
        viewModel.StatusMessage.Should().Be("Assigned presets to 1 visible empty NPC.");
    }

    [Fact]
    public void ScopedRoutineBulkOperationsDefaultToVisibleWhenFiltersAreActive()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var serana = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        lydia.AddSliderPreset(alpha);
        serana.AddSliderPreset(alpha);
        valerica.AddSliderPreset(alpha);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(serana);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Mod, new[] { "Skyrim.esm" });
        viewModel.ClearAssignmentsCommand.Execute().Subscribe();

        lydia.SliderPresets.Should().BeEmpty();
        serana.SliderPresets.Should().BeEmpty();
        valerica.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        viewModel.StatusMessage.Should().Be("Cleared assignments from 2 visible NPCs.");
    }

    [Fact]
    public void FreeTextFilteredBulkOperationsDefaultToVisibleScope()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var serana = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        lydia.AddSliderPreset(alpha);
        serana.AddSliderPreset(alpha);
        valerica.AddSliderPreset(alpha);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(serana);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());

        viewModel.SearchText = "Skyrim";
        viewModel.ClearAssignmentsCommand.Execute().Subscribe();

        lydia.SliderPresets.Should().BeEmpty();
        serana.SliderPresets.Should().BeEmpty();
        valerica.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        viewModel.SelectedNpcBulkScope.Should().Be(NpcBulkScope.Visible);
        viewModel.StatusMessage.Should().Be("Cleared assignments from 2 visible NPCs.");
    }

    [Fact]
    public void ClearNpcsCommandIsEnabledForSelectedScopeTargetsHiddenByFilters()
    {
        var project = CreateProjectWithPresets();
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(valerica);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Name, new[] { "Missing" });
        viewModel.SelectedNpcBulkScope = NpcBulkScope.All;

        ((ICommand)viewModel.ClearVisibleNpcsCommand).CanExecute(null).Should().BeTrue();
    }

    [Fact]
    public void DestructiveAllScopeClearAssignmentsRequiresConfirmationAndRecordsOneUndoOperation()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        lydia.AddSliderPreset(alpha);
        valerica.AddSliderPreset(alpha);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(valerica);
        var undoRedo = new UndoRedoService();
        var dialog = new CapturingAppDialogService { ConfirmBulkOperationResult = true };
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo, dialogService: dialog);
        viewModel.SelectedNpcBulkScope = NpcBulkScope.All;

        viewModel.ClearAssignmentsCommand.Execute().Subscribe();

        dialog.BulkConfirmationMessages.Should().ContainSingle()
            .Which.Should().Be("This will clear assignments for every NPC, including rows hidden by filters. You can undo this action. Continue?");
        lydia.SliderPresets.Should().BeEmpty();
        valerica.SliderPresets.Should().BeEmpty();
        undoRedo.Undo().Should().BeTrue();
        lydia.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        valerica.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        undoRedo.Undo().Should().BeFalse();
    }

    [Fact]
    public void DestructiveAllScopeClearNpcRowsRequiresConfirmationAndCancellationLeavesRowsUntouched()
    {
        var project = CreateProjectWithPresets();
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(valerica);
        var dialog = new CapturingAppDialogService { ConfirmBulkOperationResult = false };
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), dialogService: dialog);
        viewModel.SelectedNpcBulkScope = NpcBulkScope.All;

        viewModel.ClearVisibleNpcsCommand.Execute().Subscribe();

        dialog.BulkConfirmationMessages.Should().ContainSingle()
            .Which.Should().Be("This will remove every NPC, including rows hidden by filters. You can undo this action. Continue?");
        project.MorphedNpcs.Should().Equal(lydia, valerica);
        viewModel.VisibleNpcs.Should().Equal(lydia, valerica);
    }

    [Fact]
    public void ScopedClearAssignmentsUndoUsesPresetNameSnapshotsAndRecordsOneStep()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var beta = project.SliderPresets.Single(preset => preset.Name == "Beta");
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var hidden = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        lydia.AddSliderPreset(alpha);
        hidden.AddSliderPreset(beta);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(hidden);
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Mod, new[] { "Skyrim.esm" });
        viewModel.ClearAssignmentsCommand.Execute().Subscribe();
        alpha.Name = "Gamma";

        undoRedo.Undo().Should().BeTrue();

        lydia.SliderPresets.Should().BeEmpty();
        hidden.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");
        undoRedo.Undo().Should().BeFalse();
    }

    [Fact]
    public void CustomTargetAssignmentUndoRedoUsesPresetNameSnapshots()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var target = new CustomMorphTarget("All|Female");
        project.CustomMorphTargets.Add(target);
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);
        viewModel.SelectedCustomTarget = target;
        viewModel.SelectedAvailablePreset = alpha;

        viewModel.AddSelectedPresetToTarget().Should().BeTrue();
        alpha.Name = "Renamed Before Undo";

        undoRedo.Undo().Should().BeTrue();
        target.SliderPresets.Should().BeEmpty();

        undoRedo.Redo().Should().BeTrue();
        target.SliderPresets.Should().BeEmpty();
    }

    [Fact]
    public void RepeatedScopedBulkOperationsRespectBoundedUndoPruning()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets.Single(preset => preset.Name == "Alpha");
        var beta = project.SliderPresets.Single(preset => preset.Name == "Beta");
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var serana = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        lydia.AddSliderPreset(alpha);
        serana.AddSliderPreset(beta);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(serana);
        var undoRedo = new UndoRedoService(historyLimit: 1);
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);

        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Name, new[] { "Lydia" });
        viewModel.ClearAssignmentsCommand.Execute().Subscribe();
        viewModel.SetNpcColumnAllowedValues(WorkflowNpcFilterColumn.Name, new[] { "Serana" });
        viewModel.ClearAssignmentsCommand.Execute().Subscribe();

        undoRedo.Undo().Should().BeTrue();
        lydia.SliderPresets.Should().BeEmpty();
        serana.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");
        undoRedo.Undo().Should().BeFalse();
    }

    [Fact]
    public void LargeNpcSearchIsDebouncedBeforeVisibleRowsRefresh()
    {
        using var scheduler = new EventLoopScheduler();
        var project = CreateProjectWithPresets();
        for (var index = 0; index < 5000; index++)
        {
            project.MorphedNpcs.Add(CreateNpc(
                "Skyrim.esm",
                "Npc-" + index.ToString("0000", CultureInfo.InvariantCulture),
                "Editor" + index.ToString(CultureInfo.InvariantCulture),
                "NordRace",
                index.ToString("X8", CultureInfo.InvariantCulture)));
        }

        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), filterScheduler: scheduler);

        viewModel.SearchText = "Npc-4";
        viewModel.SearchText = "Npc-49";
        viewModel.SearchText = "Npc-4999";

        viewModel.VisibleNpcs.Count.Should().Be(5000);
        SpinWait.SpinUntil(() => viewModel.VisibleNpcs.Count == 1, TimeSpan.FromSeconds(3)).Should().BeTrue();
        viewModel.VisibleNpcs.Should().ContainSingle().Which.Name.Should().Be("Npc-4999");
    }

    [Fact]
    public void ClearCustomTargetsCanUndoAndRedo()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets[0];
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(alpha);
        project.CustomMorphTargets.Add(target);
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);

        viewModel.SelectedCustomTarget = target;
        viewModel.ClearCustomTargets();

        project.CustomMorphTargets.Should().BeEmpty();

        undoRedo.Undo().Should().BeTrue();
        var restoredTarget = project.CustomMorphTargets.Should().ContainSingle().Which;
        restoredTarget.Name.Should().Be("All|Female");
        restoredTarget.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        viewModel.SelectedCustomTarget.Should().BeSameAs(restoredTarget);

        undoRedo.Redo().Should().BeTrue();
        project.CustomMorphTargets.Should().BeEmpty();
    }

    [Fact]
    public void RemovedCustomTargetUndoRestoresOperationTimeValuesAfterDetachedTargetMutates()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets[0];
        var beta = project.SliderPresets[1];
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(alpha);
        project.CustomMorphTargets.Add(target);
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);

        viewModel.SelectedCustomTarget = target;
        viewModel.RemoveSelectedCustomTarget().Should().BeTrue();
        target.Name = "Mutated|Female";
        target.AddSliderPreset(beta);

        undoRedo.Undo().Should().BeTrue();

        var restored = project.CustomMorphTargets.Should().ContainSingle().Which;
        restored.Name.Should().Be("All|Female");
        restored.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        restored.Should().NotBeSameAs(target);
    }

    [Fact]
    public void RemovedNpcUndoRestoresOperationTimeValuesAfterDetachedNpcMutates()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets[0];
        var beta = project.SliderPresets[1];
        var npc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        npc.AddSliderPreset(alpha);
        project.MorphedNpcs.Add(npc);
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);

        viewModel.SelectedNpc = npc;
        viewModel.RemoveSelectedNpc().Should().BeTrue();
        npc.Name = "Mutated Lydia";
        npc.Mod = "Mutated.esm";
        npc.EditorId = "MutatedEditor";
        npc.Race = "MutatedRace";
        npc.FormId = "00ABCDEF";
        npc.AddSliderPreset(beta);

        undoRedo.Undo().Should().BeTrue();

        var restored = project.MorphedNpcs.Should().ContainSingle().Which;
        restored.Name.Should().Be("Lydia");
        restored.Mod.Should().Be("Skyrim.esm");
        restored.EditorId.Should().Be("HousecarlWhiterun");
        restored.Race.Should().Be("NordRace");
        restored.FormId.Should().Be("A2C94");
        restored.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        restored.Should().NotBeSameAs(npc);
    }

    [Fact]
    public void ClearVisibleNpcsCanUndoAndRedo()
    {
        var project = CreateProjectWithPresets();
        var alpha = project.SliderPresets[0];
        var beta = project.SliderPresets[1];
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        lydia.AddSliderPreset(alpha);
        valerica.AddSliderPreset(beta);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(valerica);
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(), undoRedo: undoRedo);

        viewModel.SelectedNpc = lydia;
        viewModel.SearchText = "Skyrim";
        viewModel.ClearVisibleNpcs().Should().Be(1);

        project.MorphedNpcs.Should().NotContain(lydia);
        project.MorphedNpcs.Should().Contain(valerica);

        undoRedo.Undo().Should().BeTrue();
        project.MorphedNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        project.MorphedNpcs.Single(npc => npc.Name == "Lydia").SliderPresets.Select(preset => preset.Name)
            .Should().Equal("Alpha");
        viewModel.SelectedNpc?.Name.Should().Be("Lydia");

        undoRedo.Redo().Should().BeTrue();
        project.MorphedNpcs.Should().NotContain(lydia);
        project.MorphedNpcs.Should().Contain(valerica);
    }

    [Fact]
    public void AddAllVisibleImportedNpcsCanUndoAndRedoWithRandomAssignments()
    {
        var project = CreateProjectWithPresets();
        var undoRedo = new UndoRedoService();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider(0, 1), undoRedo: undoRedo);
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        viewModel.NpcDatabase.Add(lydia);
        viewModel.NpcDatabase.Add(valerica);
        viewModel.AssignRandomOnAdd = true;

        viewModel.AddAllVisibleImportedNpcs().Should().Be(2);

        project.MorphedNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        project.MorphedNpcs.Single(npc => npc.Name == "Lydia").SliderPresets.Select(preset => preset.Name)
            .Should().Equal("Alpha");
        project.MorphedNpcs.Single(npc => npc.Name == "Valerica").SliderPresets.Select(preset => preset.Name)
            .Should().Equal("Beta");

        undoRedo.Undo().Should().BeTrue();
        project.MorphedNpcs.Should().BeEmpty();

        undoRedo.Redo().Should().BeTrue();
        project.MorphedNpcs.Select(npc => npc.Name).Should().Equal("Lydia", "Valerica");
        project.MorphedNpcs.Single(npc => npc.Name == "Lydia").SliderPresets.Select(preset => preset.Name)
            .Should().Equal("Alpha");
        project.MorphedNpcs.Single(npc => npc.Name == "Valerica").SliderPresets.Select(preset => preset.Name)
            .Should().Equal("Beta");
    }

    [Fact]
    public void ClearedNpcsDoNotRefreshViewModelWhenRemovedNpcChanges()
    {
        var project = CreateProjectWithPresets();
        var viewModel = CreateViewModel(project, new QueueRandomAssignmentProvider());
        var npc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var npcBadgeNotifications = 0;

        project.MorphedNpcs.Add(npc);
        viewModel.PropertyChanged += (_, args) =>
        {
            if (args.PropertyName == nameof(MorphsViewModel.NpcCountBadgeText)) npcBadgeNotifications++;
        };

        project.MorphedNpcs.Clear();
        npcBadgeNotifications = 0;

        npc.Name = "Detached Lydia";

        npcBadgeNotifications.Should().Be(0);
        viewModel.VisibleNpcs.Should().BeEmpty();
    }

    [Fact]
    public void GenerateMorphsReportsAndNotifiesTargetsWithoutPresets()
    {
        var project = CreateProjectWithPresets();
        var emptyTarget = new CustomMorphTarget("All|Female");
        var emptyNpc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        project.CustomMorphTargets.Add(emptyTarget);
        project.MorphedNpcs.Add(emptyNpc);
        var notifier = new CapturingNoPresetNotificationService();
        var viewModel = CreateViewModel(
            project,
            new QueueRandomAssignmentProvider(),
            noPresetNotificationService: notifier);

        viewModel.GenerateMorphs();

        viewModel.NoPresetTargets.Should().Equal(emptyTarget, emptyNpc);
        notifier.Targets.Should().Equal(viewModel.NoPresetTargets);
        viewModel.StatusMessage.Should().Be("Generated morphs. 2 targets have no presets.");
    }

    [Fact]
    public void ViewSelectedNpcImageUsesLookupAndImageWindowService()
    {
        var project = CreateProjectWithPresets();
        var npc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        project.MorphedNpcs.Add(npc);
        var imageLookup = new StubNpcImageLookupService("images/Lydia (HousecarlWhiterun).png");
        var imageView = new CapturingImageViewService();
        var viewModel = CreateViewModel(
            project,
            new QueueRandomAssignmentProvider(),
            imageLookupService: imageLookup,
            imageViewService: imageView);

        viewModel.SelectedNpc = npc;
        ((ICommand)viewModel.ViewSelectedNpcImageCommand).Execute(null);

        imageLookup.Npc.Should().Be(npc);
        imageView.Npc.Should().Be(npc);
        imageView.ImagePath.Should().Be("images/Lydia (HousecarlWhiterun).png");
        viewModel.StatusMessage.Should().Be("Opened image for Lydia.");
    }

    [Fact]
    public async Task NpcTextPickerUsesAndUpdatesRememberedFolder()
    {
        var preferences = new CapturingUserPreferencesService(new UserPreferences
        {
            NpcTextFolder = @"C:\NPCs\Old"
        });
        var backend = new CapturingNpcTextPickerBackend(new[] { @"D:\NPCs\actors.txt" })
        {
            ResolvedStartFolder = @"C:\NPCs\Old"
        };
        var picker = new WindowNpcTextFilePicker(preferences, backend);

        var files = await picker.PickNpcTextFilesAsync(TestContext.Current.CancellationToken);

        files.Should().Equal(@"D:\NPCs\actors.txt");
        backend.RequestedStartFolder.Should().Be(@"C:\NPCs\Old");
        preferences.Saved.NpcTextFolder.Should().Be(@"D:\NPCs");
    }

    [Fact]
    public async Task NpcTextPickerContinuesWhenPreferenceSaveFails()
    {
        var backend = new CapturingNpcTextPickerBackend(new[] { @"D:\NPCs\actors.txt" });
        var picker = new WindowNpcTextFilePicker(
            new FailingUserPreferencesService(new UserPreferences()),
            backend);

        var files = await picker.PickNpcTextFilesAsync(TestContext.Current.CancellationToken);

        files.Should().Equal(@"D:\NPCs\actors.txt");
    }

    [Fact]
    public async Task NpcTextPickerIgnoresUnresolvableRememberedFolderHint()
    {
        var preferences = new CapturingUserPreferencesService(new UserPreferences
        {
            NpcTextFolder = @"Z:\Missing\NPCs"
        });
        var backend = new CapturingNpcTextPickerBackend(new[] { @"D:\NPCs\actors.txt" })
        {
            ResolvedStartFolder = null
        };
        var picker = new WindowNpcTextFilePicker(preferences, backend);

        var files = await picker.PickNpcTextFilesAsync(TestContext.Current.CancellationToken);

        files.Should().Equal(@"D:\NPCs\actors.txt");
        backend.RequestedPreferencePath.Should().Be(@"Z:\Missing\NPCs");
        backend.RequestedStartFolder.Should().BeNull();
        preferences.Saved.NpcTextFolder.Should().Be(@"D:\NPCs");
    }

    [Fact]
    public void UserPreferencesJsonDoesNotPersistNpcSearchOrFilterState()
    {
        var preferences = new UserPreferences
        {
            NpcTextFolder = @"D:\NPCs"
        };

        var json = JsonSerializer.Serialize(preferences);

        json.Should().Contain(nameof(UserPreferences.NpcTextFolder));
        json.Should().NotContain(nameof(MorphsViewModel.SearchText));
        json.Contains("NpcFilter", StringComparison.OrdinalIgnoreCase).Should().BeFalse();
        json.Contains("AllowedValues", StringComparison.OrdinalIgnoreCase).Should().BeFalse();
    }

    [Theory]
    [InlineData("All|Female=Bad")]
    [InlineData("All|Female\nFoo")]
    [InlineData("All|Female\rFoo")]
    [InlineData("All| Female")]
    [InlineData("All|Female |NordRace")]
    public void TargetNameValidationRejectsReservedCharactersAndEdgeWhitespace(string rawName)
    {
        var viewModel = CreateViewModel(CreateProjectWithPresets(), new QueueRandomAssignmentProvider());

        viewModel.TargetNameInput = rawName;

        viewModel.TargetNameValidationMessage
            .Should().Be("Custom target must use Context|Gender or Context|Gender|Race[Variant].");
        viewModel.AddCustomTarget().Should().BeFalse();
    }

    private static MorphsViewModel CreateViewModel(
        ProjectModel project,
        IRandomAssignmentProvider randomProvider,
        INpcTextFilePicker? filePicker = null,
        IClipboardService? clipboard = null,
        INpcImageLookupService? imageLookupService = null,
        IImageViewService? imageViewService = null,
        INoPresetNotificationService? noPresetNotificationService = null,
        UndoRedoService? undoRedo = null,
        IScheduler? filterScheduler = null,
        IAppDialogService? dialogService = null)
    {
        return new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(randomProvider),
            new MorphGenerationService(),
            filePicker ?? new StaticNpcTextFilePicker(Array.Empty<string>()),
            clipboard ?? new CapturingClipboardService(),
            imageLookupService ?? new StubNpcImageLookupService(null),
            imageViewService ?? new CapturingImageViewService(),
            noPresetNotificationService ?? new CapturingNoPresetNotificationService(),
            undoRedo,
            filterScheduler,
            dialogService);
    }

    private static ProjectModel CreateProjectWithPresets()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Alpha"));
        project.SliderPresets.Add(new SliderPreset("Beta"));
        return project;
    }

    private static Npc CreateNpc(string mod, string name, string editorId, string race, string formId) =>
        new(name) { Mod = mod, EditorId = editorId, Race = race, FormId = formId };

    private sealed class QueueRandomAssignmentProvider(params int[] values) : IRandomAssignmentProvider
    {
        private readonly Queue<int> values = new(values);

        public int NextIndex(int exclusiveMax)
        {
            if (values.Count == 0) return 0;

            return values.Dequeue();
        }
    }

    private sealed class StaticNpcTextFilePicker(IReadOnlyList<string> files) : INpcTextFilePicker
    {
        private readonly IReadOnlyList<string> files = files;

        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult(files);
    }

    private sealed class CapturingClipboardService : IClipboardService
    {
        public string? Text { get; private set; }

        public Task SetTextAsync(string text, CancellationToken cancellationToken)
        {
            Text = text;
            return Task.CompletedTask;
        }
    }

    private sealed class StubNpcImageLookupService(string? imagePath) : INpcImageLookupService
    {
        private readonly string? imagePath = imagePath;

        public Npc? Npc { get; private set; }

        public string? FindImagePath(Npc npc)
        {
            Npc = npc;
            return imagePath;
        }
    }

    private sealed class CapturingImageViewService : IImageViewService
    {
        public Npc? Npc { get; private set; }

        public string? ImagePath { get; private set; }

        public void ShowImage(Npc npc, string? imagePath)
        {
            Npc = npc;
            ImagePath = imagePath;
        }
    }

    private sealed class CapturingNoPresetNotificationService : INoPresetNotificationService
    {
        public IReadOnlyList<MorphTargetBase> Targets { get; private set; } = Array.Empty<MorphTargetBase>();

        public void ShowTargetsWithoutPresets(IReadOnlyList<MorphTargetBase> targets) => Targets = targets.ToArray();
    }

    private sealed class CapturingAppDialogService : IAppDialogService
    {
        public bool ConfirmBulkOperationResult { get; init; }

        public List<string> BulkConfirmationMessages { get; } = new();

        public Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmBulkOperationAsync(
            string title,
            string message,
            CancellationToken cancellationToken)
        {
            BulkConfirmationMessages.Add(message);
            return Task.FromResult(ConfirmBulkOperationResult);
        }

        public void ShowAbout()
        {
        }
    }

    private sealed class CapturingUserPreferencesService(UserPreferences initial) : IUserPreferencesService
    {
        public UserPreferences Saved { get; private set; } = initial;

        public UserPreferences Load() => Saved;

        public bool Save(UserPreferences preferences)
        {
            Saved = preferences;
            return true;
        }
    }

    private sealed class FailingUserPreferencesService(UserPreferences preferences) : IUserPreferencesService
    {
        public UserPreferences Load() => preferences;

        public bool Save(UserPreferences preferences) => false;
    }

    private sealed class CapturingNpcTextPickerBackend(IReadOnlyList<string> files) : INpcTextPickerBackend
    {
        private readonly IReadOnlyList<string> files = files;

        public bool CanOpen => true;

        public string? RequestedStartFolder { get; private set; }

        public string? RequestedPreferencePath { get; private set; }

        public string? ResolvedStartFolder { get; init; }

        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(
            string? suggestedStartFolder,
            CancellationToken cancellationToken)
        {
            RequestedStartFolder = suggestedStartFolder;
            return Task.FromResult(files);
        }

        public Task<string?> ResolveStartFolderAsync(string? path, CancellationToken cancellationToken)
        {
            RequestedPreferencePath = path;
            return Task.FromResult(ResolvedStartFolder);
        }
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        private readonly string path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));

        public TemporaryDirectory() => Directory.CreateDirectory(path);

        public void Dispose() => Directory.Delete(path, true);

        public string WriteText(string fileName, string text)
        {
            var filePath = Path.Combine(path, fileName);
            File.WriteAllText(filePath, text);
            return filePath;
        }
    }
}
