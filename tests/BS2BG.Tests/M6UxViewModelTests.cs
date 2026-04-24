using System.Diagnostics.CodeAnalysis;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using Xunit;
using ModelSetSlider = BS2BG.Core.Models.SetSlider;
using ModelSliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected sequences keep M6 workflow assertions readable.")]
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

        Assert.Equal(new[] { "Beta" }, harness.Templates.VisiblePresets.Select(preset => preset.Name));

        harness.Main.ActiveWorkspace = AppWorkspace.Morphs;
        harness.Main.GlobalSearchText = "housecarl";

        Assert.Equal(new[] { "Lydia" }, harness.Morphs.VisibleNpcs.Select(npc => npc.Name));

        harness.Main.OpenCommandPaletteCommand.Execute(null);
        harness.Main.CommandPaletteSearchText = "Generate Templates";
        var command = Assert.Single(harness.Main.VisibleCommandPaletteItems);

        harness.Main.RunCommandPaletteItemCommand.Execute(command);

        Assert.True(harness.Main.IsCommandPaletteOpen);
        Assert.Equal("Generate Templates", command.Title);
        Assert.Contains("Alpha", harness.Templates.GeneratedTemplateText, StringComparison.Ordinal);
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

        Assert.Equal(projectPath, harness.Main.CurrentProjectPath);
        Assert.Contains(harness.Project.SliderPresets, preset => preset.Name == "Loaded");
        Assert.Contains(harness.Project.SliderPresets, preset => preset.Name == "DropAlpha");
        Assert.Equal(new[] { "Lydia" }, harness.Morphs.NpcDatabase.Select(npc => npc.Name));
        Assert.Contains("Skipped 1 unsupported file", harness.Main.StatusMessage, StringComparison.Ordinal);
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
        harness.Morphs.AssignSelectedNpcsCommand.Execute(null);
        harness.Morphs.ClearSelectedNpcAssignmentsCommand.Execute(null);

        Assert.Empty(lydia.SliderPresets);
        Assert.Empty(serana.SliderPresets);
        Assert.Equal(new[] { "Beta" }, valerica.SliderPresets.Select(preset => preset.Name));

        harness.Morphs.SetNpcColumnAllowedValues(NpcFilterColumn.Race, new[] { "NordRaceVampire" });
        harness.Morphs.SearchText = "Dawnguard";
        harness.Morphs.SetNpcColumnSearchText(NpcFilterColumn.Race, "vamp");

        Assert.Equal(new[] { "Valerica" }, harness.Morphs.VisibleNpcs.Select(npc => npc.Name));
        Assert.Equal(new[] { "NordRaceVampire" }, harness.Morphs.GetNpcColumnValues(NpcFilterColumn.Race));
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
        Assert.True(harness.Templates.TryRenameSelectedPreset("Gamma"));

        harness.Main.UndoCommand.Execute(null);
        Assert.Equal("Alpha", alpha.Name);

        harness.Main.RedoCommand.Execute(null);
        Assert.Equal("Gamma", alpha.Name);

        Assert.True(harness.Templates.RemoveSelectedPreset());
        Assert.Empty(target.SliderPresets);

        harness.Main.UndoCommand.Execute(null);

        Assert.Contains(project.SliderPresets, preset => preset.Name == "Gamma");
        Assert.Equal(new[] { "Gamma" }, target.SliderPresets.Select(preset => preset.Name));
    }

    [Fact]
    public void InlineTargetValidationThemePreferenceAndPresetWarningAreActionable()
    {
        var project = CreateProjectWithPresets(Enumerable.Range(0, 80).Select(index => "P" + index).ToArray());
        var target = new CustomMorphTarget("All|Female");
        var support = new CustomMorphTarget("All|Male");
        foreach (var preset in project.SliderPresets)
        {
            target.AddSliderPreset(preset);
        }

        foreach (var preset in project.SliderPresets.Skip(76))
        {
            support.AddSliderPreset(preset);
        }

        project.CustomMorphTargets.Add(target);
        project.CustomMorphTargets.Add(support);
        var preferences = new CapturingUserPreferencesService();
        var harness = CreateHarness(project, preferences);

        harness.Morphs.TargetNameInput = "bad";
        Assert.NotEqual(string.Empty, harness.Morphs.TargetNameValidationMessage);
        Assert.Contains("All|Female", harness.Morphs.CustomTargetExamples);

        harness.Morphs.SelectedCustomTarget = target;
        Assert.Equal(PresetCountWarningState.Error, harness.Morphs.TargetPresetWarningState);
        Assert.True(harness.Morphs.TrimSelectedTargetTo76Command.CanExecute(null));

        harness.Morphs.TrimSelectedTargetTo76Command.Execute(null);

        Assert.Equal(76, target.SliderPresets.Count);
        Assert.Equal(new[] { "P76", "P77", "P78", "P79" }, target.SliderPresets.TakeLast(4).Select(preset => preset.Name));

        harness.Main.SelectedThemePreference = ThemePreference.Dark;

        Assert.Equal(ThemePreference.Dark, preferences.Saved.Theme);
    }

    private static TestHarness CreateHarness(
        ProjectModel project,
        IUserPreferencesService? preferences = null)
    {
        var undoRedo = new UndoRedoService();
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
            preset.AddSetSlider(new ModelSetSlider("Scale")
            {
                ValueBig = 50,
            });
            project.SliderPresets.Add(preset);
        }

        return project;
    }

    private static Npc CreateNpc(string mod, string name, string editorId, string race, string formId)
    {
        return new Npc(name)
        {
            Mod = mod,
            EditorId = editorId,
            Race = race,
            FormId = formId,
        };
    }

    private static TemplateProfileCatalog CreateCatalog()
    {
        var regular = new BS2BG.Core.Formatting.SliderProfile(
            defaults: Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
            multipliers: Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
            invertedNames: Array.Empty<string>());
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, regular),
        });
    }

    private sealed record TestHarness(
        ProjectModel Project,
        TemplatesViewModel Templates,
        MorphsViewModel Morphs,
        MainWindowViewModel Main);

    private sealed class QueueRandomAssignmentProvider : IRandomAssignmentProvider
    {
        public int NextIndex(int exclusiveMax)
        {
            return 0;
        }
    }

    private sealed class EmptyFileDialogService : IFileDialogService
    {
        public Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult<string?>(null);
        }

        public Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken)
        {
            return Task.FromResult<string?>(null);
        }

        public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult<string?>(null);
        }

        public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult<string?>(null);
        }
    }

    private sealed class ConfirmingAppDialogService : IAppDialogService
    {
        public Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken)
        {
            return Task.FromResult(true);
        }

        public void ShowAbout()
        {
        }
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
        }
    }

    private sealed class EmptyNpcTextFilePicker : INpcTextFilePicker
    {
        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
        }
    }

    private sealed class EmptyClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken)
        {
            return Task.CompletedTask;
        }
    }

    private sealed class CapturingUserPreferencesService : IUserPreferencesService
    {
        public UserPreferences Saved { get; private set; } = new UserPreferences();

        public UserPreferences Load()
        {
            return Saved;
        }

        public void Save(UserPreferences preferences)
        {
            Saved = preferences;
        }
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public string WriteText(string fileName, string text)
        {
            var filePath = System.IO.Path.Combine(Path, fileName);
            File.WriteAllText(filePath, text);
            return filePath;
        }

        public void Dispose()
        {
            Directory.Delete(Path, recursive: true);
        }
    }
}
