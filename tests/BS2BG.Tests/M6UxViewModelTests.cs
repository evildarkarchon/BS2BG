using System.Diagnostics.CodeAnalysis;
using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.ViewModels.Workflow;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected sequences keep M6 workflow assertions readable.")]
public sealed class M6UxViewModelTests
{
    [Fact]
    public void GlobalSearchFiltersActiveSurfaceAndPaletteRunsRegisteredCommand()
    {
        var project = CreateProjectWithPresets("Alpha", "Beta");
        project.SliderPresets[1].AddSetSlider(new ModelSetSlider("WideHips"));
        project.MorphedNpcs.Add(CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94"));
        project.MorphedNpcs.Add(CreateNpc("Dawnguard.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74"));
        var harness = CreateHarness(project);

        harness.Main.ActiveWorkspace = AppWorkspace.Templates;
        harness.Main.GlobalSearchText = "wide";

        harness.Templates.VisiblePresets.Select(preset => preset.Name).Should().Equal("Beta");

        harness.Main.ActiveWorkspace = AppWorkspace.Morphs;
        harness.Main.GlobalSearchText = "housecarl";

        harness.Morphs.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Lydia");

        ((ICommand)harness.Main.OpenCommandPaletteCommand).Execute(null);
        harness.Main.CommandPaletteSearchText = "Generate Templates";
        var command = harness.Main.VisibleCommandPaletteItems.Should().ContainSingle().Which;

        ((ICommand)harness.Main.RunCommandPaletteItemCommand).Execute(command);

        harness.Main.IsCommandPaletteOpen.Should().BeFalse();
        harness.Main.CommandPaletteSearchText.Should().BeEmpty();
        command.Title.Should().Be("Generate Templates");
        harness.Templates.GeneratedTemplateText.Should().Contain("Alpha");
    }

    [Fact]
    public void CloseCommandPaletteCommandDismissesPaletteAndClearsSearch()
    {
        var harness = CreateHarness(CreateProjectWithPresets("Alpha"));

        ((ICommand)harness.Main.OpenCommandPaletteCommand).Execute(null);
        harness.Main.CommandPaletteSearchText = "Generate";
        harness.Main.IsCommandPaletteOpen.Should().BeTrue();

        ((ICommand)harness.Main.CloseCommandPaletteCommand).Execute(null);

        harness.Main.IsCommandPaletteOpen.Should().BeFalse();
    }

    [Fact]
    public async Task DroppedFilesUseExistingOpenXmlAndNpcImportPaths()
    {
        using var directory = new TemporaryDirectory();
        var savedProject = CreateProjectWithPresets("Loaded");
        var projectPath = Path.Combine(directory.Path, "loaded.jbs2bg");
        new ProjectFileService().Save(savedProject, projectPath);
        var xmlPath = directory.WriteText(
            "drop.xml",
            """
            <SliderPresets>
              <Preset name="DropAlpha"><SetSlider name="Scale" size="big" value="50"/></Preset>
            </SliderPresets>
            """);
        var npcPath = directory.WriteText(
            "npcs.txt",
            "Skyrim.esm|Lydia|HousecarlWhiterun|NordRace|000A2C94");
        var harness = CreateHarness(new ProjectModel());

        await harness.Main.HandleDroppedFilesAsync(new[] { projectPath }, TestContext.Current.CancellationToken);
        await harness.Main.HandleDroppedFilesAsync(
            new[] { xmlPath, npcPath, Path.Combine(directory.Path, "ignored.md") },
            TestContext.Current.CancellationToken);

        harness.Main.CurrentProjectPath.Should().Be(projectPath);
        harness.Project.SliderPresets.Should().Contain(preset => preset.Name == "Loaded");
        harness.Project.SliderPresets.Should().Contain(preset => preset.Name == "DropAlpha");
        harness.Morphs.NpcDatabase.Select(npc => npc.Name).Should().Equal("Lydia");
        harness.Main.StatusMessage.Should().Contain("Skipped 1 unsupported file");
    }

    [Fact]
    public void MultiselectAssignmentAndColumnChecklistFiltersComposeWithSearch()
    {
        var project = CreateProjectWithPresets("Alpha", "Beta");
        var alpha = project.SliderPresets[0];
        var beta = project.SliderPresets[1];
        var lydia = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        var serana = CreateNpc("Skyrim.esm", "Serana", "DLC1Serana", "NordRaceVampire", "02002B74");
        var valerica = CreateNpc("Dawnguard.esm", "Valerica", "DLC1Valerica", "NordRaceVampire", "02002B6C");
        valerica.AddSliderPreset(beta);
        project.MorphedNpcs.Add(lydia);
        project.MorphedNpcs.Add(serana);
        project.MorphedNpcs.Add(valerica);
        var harness = CreateHarness(project);

        harness.Morphs.SelectedNpcs.Add(lydia);
        harness.Morphs.SelectedNpcs.Add(serana);
        harness.Morphs.SelectedAvailablePreset = alpha;
        ((ICommand)harness.Morphs.AssignSelectedNpcsCommand).Execute(null);
        ((ICommand)harness.Morphs.ClearSelectedNpcAssignmentsCommand).Execute(null);

        lydia.SliderPresets.Should().BeEmpty();
        serana.SliderPresets.Should().BeEmpty();
        valerica.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");

        harness.Morphs.SetNpcColumnAllowedValues(NpcFilterColumn.Race, new[] { "NordRaceVampire" });
        harness.Morphs.SearchText = "Dawnguard";
        harness.Morphs.SetNpcColumnSearchText(NpcFilterColumn.Race, "vamp");

        harness.Morphs.VisibleNpcs.Select(npc => npc.Name).Should().Equal("Valerica");
        harness.Morphs.GetNpcColumnValues(NpcFilterColumn.Race).Should().Equal("NordRaceVampire");
    }

    [Fact]
    public void UndoRedoRestoresPresetRenameRemovalAndAssignments()
    {
        var project = CreateProjectWithPresets("Alpha", "Beta");
        var alpha = project.SliderPresets[0];
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(alpha);
        project.CustomMorphTargets.Add(target);
        var harness = CreateHarness(project);

        harness.Templates.SelectedPreset = alpha;
        harness.Templates.TryRenameSelectedPreset("Gamma").Should().BeTrue();

        ((ICommand)harness.Main.UndoCommand).Execute(null);
        alpha.Name.Should().Be("Alpha");

        ((ICommand)harness.Main.RedoCommand).Execute(null);
        alpha.Name.Should().Be("Gamma");

        harness.Templates.RemoveSelectedPreset().Should().BeTrue();
        target.SliderPresets.Should().BeEmpty();

        ((ICommand)harness.Main.UndoCommand).Execute(null);

        project.SliderPresets.Should().Contain(preset => preset.Name == "Gamma");
        target.SliderPresets.Select(preset => preset.Name).Should().Equal("Gamma");
    }

    [Fact]
    public void UndoRedoRestoresClearPresetsAndAssignments()
    {
        var project = CreateProjectWithPresets("Alpha", "Beta");
        var alpha = project.SliderPresets[0];
        var beta = project.SliderPresets[1];
        var target = new CustomMorphTarget("All|Female");
        var npc = CreateNpc("Skyrim.esm", "Lydia", "HousecarlWhiterun", "NordRace", "000A2C94");
        target.AddSliderPreset(alpha);
        npc.AddSliderPreset(beta);
        project.CustomMorphTargets.Add(target);
        project.MorphedNpcs.Add(npc);
        var harness = CreateHarness(project);

        harness.Templates.ClearPresets();

        project.SliderPresets.Should().BeEmpty();
        target.SliderPresets.Should().BeEmpty();
        npc.SliderPresets.Should().BeEmpty();

        ((ICommand)harness.Main.UndoCommand).Execute(null);

        project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha", "Beta");
        target.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        npc.SliderPresets.Select(preset => preset.Name).Should().Equal("Beta");

        ((ICommand)harness.Main.RedoCommand).Execute(null);

        project.SliderPresets.Should().BeEmpty();
        target.SliderPresets.Should().BeEmpty();
        npc.SliderPresets.Should().BeEmpty();
    }

    [Fact]
    public void InlineTargetValidationThemePreferenceAndPresetWarningAreActionable()
    {
        var project = CreateProjectWithPresets(Enumerable.Range(0, 80).Select(index => "P" + index).ToArray());
        var target = new CustomMorphTarget("All|Female");
        var support = new CustomMorphTarget("All|Male");
        foreach (var preset in project.SliderPresets) target.AddSliderPreset(preset);

        foreach (var preset in project.SliderPresets.Skip(76)) support.AddSliderPreset(preset);

        project.CustomMorphTargets.Add(target);
        project.CustomMorphTargets.Add(support);
        var preferences = new CapturingUserPreferencesService();
        var harness = CreateHarness(project, preferences);

        harness.Morphs.TargetNameInput = "bad";
        harness.Morphs.TargetNameValidationMessage.Should().NotBe(string.Empty);
        harness.Morphs.CustomTargetExamples.Should().Contain("All|Female");

        harness.Morphs.SelectedCustomTarget = target;
        harness.Morphs.TargetPresetWarningState.Should().Be(PresetCountWarningState.Error);
        ((ICommand)harness.Morphs.TrimSelectedTargetTo76Command).CanExecute(null).Should().BeTrue();

        ((ICommand)harness.Morphs.TrimSelectedTargetTo76Command).Execute(null);

        target.SliderPresets.Count.Should().Be(76);
        target.SliderPresets.TakeLast(4).Select(preset => preset.Name).Should().Equal("P76", "P77", "P78", "P79");

        harness.Main.SelectedThemePreference = ThemePreference.Dark;

        preferences.Saved.Theme.Should().Be(ThemePreference.Dark);
    }

    [Fact]
    public void ThemePreferenceSaveFailureKeepsSelectionAndReportsStatus()
    {
        var harness = CreateHarness(
            CreateProjectWithPresets("Alpha"),
            new FailingUserPreferencesService());

        harness.Main.SelectedThemePreference = ThemePreference.Dark;

        harness.Main.SelectedThemePreference.Should().Be(ThemePreference.Dark);
        harness.Main.StatusMessage.Should().Be("Saving preferences failed.");
    }

    [Fact]
    public void UndoRedoHistoryPrunesOldestOperationsAndKeepsRedoSemantics()
    {
        var undoRedo = new UndoRedoService(historyLimit: 2);
        var value = 0;

        undoRedo.Record("First", () => value = 0, () => value = 1);
        value = 1;
        undoRedo.Record("Second", () => value = 1, () => value = 2);
        value = 2;
        undoRedo.Record("Third", () => value = 2, () => value = 3);
        value = 3;

        undoRedo.Undo().Should().BeTrue();
        value.Should().Be(2);
        undoRedo.Undo().Should().BeTrue();
        value.Should().Be(1);
        undoRedo.Undo().Should().BeFalse();
        value.Should().Be(1);

        undoRedo.Redo().Should().BeTrue();
        value.Should().Be(2);
        undoRedo.Record("Fourth", () => value = 2, () => value = 4);

        undoRedo.CanRedo.Should().BeFalse();
    }

    [Fact]
    public void MainWindowReportsNonBlockingStatusWhenUndoHistoryIsPruned()
    {
        var undoRedo = new UndoRedoService(historyLimit: 1);
        var harness = CreateHarness(CreateProjectWithPresets("Alpha"), undoRedo: undoRedo);

        undoRedo.Record("First", () => { }, () => { });
        undoRedo.Record("Second", () => { }, () => { });

        harness.Main.StatusMessage.Should().Be("Undo history trimmed to keep large workflows responsive.");
    }

    private static TestHarness CreateHarness(
        ProjectModel project,
        IUserPreferencesService? preferences = null,
        UndoRedoService? undoRedo = null)
    {
        undoRedo ??= new UndoRedoService();
        var templateGeneration = new TemplateGenerationService();
        var profileCatalog = CreateCatalog();
        var templates = new TemplatesViewModel(
            project,
            new BodySlideXmlParser(),
            templateGeneration,
            profileCatalog,
            new EmptyBodySlideXmlFilePicker(),
            new EmptyClipboardService(),
            undoRedo);
        var morphs = new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(new QueueRandomAssignmentProvider()),
            new MorphGenerationService(),
            new EmptyNpcTextFilePicker(),
            new EmptyClipboardService(),
            undoRedo: undoRedo);
        var main = new MainWindowViewModel(
            project,
            new ProjectFileService(),
            templateGeneration,
            new MorphGenerationService(),
            profileCatalog,
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(templateGeneration),
            new EmptyFileDialogService(),
            new ConfirmingAppDialogService(),
            templates,
            morphs,
            undoRedo,
            preferences ?? new CapturingUserPreferencesService());
        return new TestHarness(project, templates, morphs, main);
    }

    private static ProjectModel CreateProjectWithPresets(params string[] presetNames)
    {
        var project = new ProjectModel();
        foreach (var presetName in presetNames)
        {
            var preset = new ModelSliderPreset(presetName);
            preset.AddSetSlider(new ModelSetSlider("Scale") { ValueBig = 50 });
            project.SliderPresets.Add(preset);
        }

        return project;
    }

    private static Npc CreateNpc(string mod, string name, string editorId, string race, string formId) =>
        new(name) { Mod = mod, EditorId = editorId, Race = race, FormId = formId };

    private static TemplateProfileCatalog CreateCatalog()
    {
        var regular = new SliderProfile(
            Array.Empty<SliderDefault>(),
            Array.Empty<SliderMultiplier>(),
            Array.Empty<string>());
        return new TemplateProfileCatalog(new[] { new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, regular) });
    }

    private sealed record TestHarness(
        ProjectModel Project,
        TemplatesViewModel Templates,
        MorphsViewModel Morphs,
        MainWindowViewModel Main);

    private sealed class QueueRandomAssignmentProvider : IRandomAssignmentProvider
    {
        public int NextIndex(int exclusiveMax) => 0;
    }

    private sealed class EmptyFileDialogService : IFileDialogService
    {
        public Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickSaveBundleFileAsync(CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);
    }

    private sealed class ConfirmingAppDialogService : IAppDialogService
    {
        public Task<bool>
            ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmBulkOperationAsync(
            string title,
            string message,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmExportOverwriteAsync(
            ExportPreviewResult preview,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<ProfileConflictDecision?> PromptProfileConflictAsync(
            ProfileConflictRequest request,
            CancellationToken cancellationToken) =>
            Task.FromResult<ProfileConflictDecision?>(null);

        public void ShowAbout()
        {
        }
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
    }

    private sealed class EmptyNpcTextFilePicker : INpcTextFilePicker
    {
        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
    }

    private sealed class EmptyClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.CompletedTask;
    }

    private sealed class CapturingUserPreferencesService : IUserPreferencesService
    {
        public UserPreferences Saved { get; private set; } = new();

        public UserPreferences Load() => Saved;

        public bool Save(UserPreferences preferences)
        {
            Saved = preferences;
            return true;
        }
    }

    private sealed class FailingUserPreferencesService : IUserPreferencesService
    {
        public UserPreferences Load() => new();

        public bool Save(UserPreferences preferences) => false;
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose() => Directory.Delete(Path, true);

        public string WriteText(string fileName, string text)
        {
            var filePath = System.IO.Path.Combine(Path, fileName);
            File.WriteAllText(filePath, text);
            return filePath;
        }
    }
}
