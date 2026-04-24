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

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected arrays keep test intent readable.")]
public sealed class MainWindowViewModelTests
{
    [Fact]
    public async Task SaveProjectPromptsForPathWhenCurrentPathIsMissing()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService
        {
            SaveProjectPath = Path.Combine(directory.Path, "saved-project"),
        };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.SaveProjectAsync(TestContext.Current.CancellationToken);

        var expectedPath = Path.Combine(directory.Path, "saved-project.jbs2bg");
        Assert.True(File.Exists(expectedPath));
        Assert.Equal(expectedPath, viewModel.CurrentProjectPath);
        Assert.False(project.IsDirty);
    }

    [Fact]
    public async Task SaveProjectAsAddsProjectExtension()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService
        {
            SaveProjectPath = Path.Combine(directory.Path, "explicit-name"),
        };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.SaveProjectAsAsync(TestContext.Current.CancellationToken);

        Assert.True(File.Exists(Path.Combine(directory.Path, "explicit-name.jbs2bg")));
    }

    [Fact]
    public async Task OpenProjectKeepsDirtyProjectWhenDiscardIsCancelled()
    {
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService();
        var confirmations = new FakeAppDialogService
        {
            ConfirmDiscardResult = false,
        };
        var viewModel = CreateViewModel(project, dialogs, confirmations);

        await viewModel.OpenProjectAsync(TestContext.Current.CancellationToken);

        Assert.Equal(new[] { "Alpha" }, project.SliderPresets.Select(preset => preset.Name));
        Assert.Equal(1, confirmations.ConfirmDiscardCallCount);
        Assert.Equal(0, dialogs.OpenProjectCallCount);
    }

    [Fact]
    public async Task NewProjectKeepsDirtyProjectWhenDiscardIsCancelled()
    {
        var project = CreateProjectWithPreset("Alpha");
        var confirmations = new FakeAppDialogService
        {
            ConfirmDiscardResult = false,
        };
        var viewModel = CreateViewModel(project, new FakeFileDialogService(), confirmations);

        await viewModel.NewProjectAsync(TestContext.Current.CancellationToken);

        Assert.Equal(new[] { "Alpha" }, project.SliderPresets.Select(preset => preset.Name));
        Assert.Equal(1, confirmations.ConfirmDiscardCallCount);
    }

    [Fact]
    public async Task ExportBodyGenInisRegeneratesAndWritesCurrentOutput()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(project.SliderPresets[0]);
        project.CustomMorphTargets.Add(target);
        var dialogs = new FakeFileDialogService
        {
            BodyGenExportFolder = directory.Path,
        };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        Assert.True(File.Exists(Path.Combine(directory.Path, "templates.ini")));
        Assert.Equal("All|Female=Alpha", File.ReadAllText(Path.Combine(directory.Path, "morphs.ini")));
        Assert.Contains("exported", viewModel.StatusMessage, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task ExportBodyGenInisReportsEmptyOutputWithoutWritingFiles()
    {
        using var directory = new TemporaryDirectory();
        var viewModel = CreateViewModel(new ProjectModel(), new FakeFileDialogService
        {
            BodyGenExportFolder = directory.Path,
        });

        await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        Assert.False(File.Exists(Path.Combine(directory.Path, "templates.ini")));
        Assert.False(File.Exists(Path.Combine(directory.Path, "morphs.ini")));
        Assert.Contains("No generated BodyGen output", viewModel.StatusMessage, StringComparison.Ordinal);
    }

    [Fact]
    public async Task ExportBodyGenInisReportsWriteFailures()
    {
        using var directory = new TemporaryDirectory();
        var blockingFile = Path.Combine(directory.Path, "not-a-folder");
        File.WriteAllText(blockingFile, string.Empty);
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService
        {
            BodyGenExportFolder = blockingFile,
        };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        Assert.Contains("failed", viewModel.StatusMessage, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task ExportBosJsonWritesOneFilePerPreset()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Preset:One");
        var dialogs = new FakeFileDialogService
        {
            BosJsonExportFolder = directory.Path,
        };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.ExportBosJsonAsync(TestContext.Current.CancellationToken);

        var file = Path.Combine(directory.Path, "Preset_One.json");
        Assert.True(File.Exists(file));
        Assert.Contains("\"bodyname\": \"Preset:One\"", File.ReadAllText(file));
    }

    private static MainWindowViewModel CreateViewModel(
        ProjectModel project,
        FakeFileDialogService fileDialogs,
        FakeAppDialogService? dialogs = null)
    {
        var parser = new BodySlideXmlParser();
        var templateGeneration = new TemplateGenerationService();
        var profileCatalog = new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new BS2BG.Core.Formatting.SliderProfile(
                    defaults: Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
                    multipliers: Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
                    invertedNames: Array.Empty<string>())),
        });
        var templates = new TemplatesViewModel(
            project,
            parser,
            templateGeneration,
            profileCatalog,
            new EmptyBodySlideXmlFilePicker(),
            new EmptyClipboardService());
        var morphs = new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(new RandomAssignmentProvider()),
            new MorphGenerationService(),
            new EmptyNpcTextFilePicker(),
            new EmptyClipboardService());

        return new MainWindowViewModel(
            project,
            new ProjectFileService(),
            templateGeneration,
            new MorphGenerationService(),
            profileCatalog,
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(templateGeneration),
            fileDialogs,
            dialogs ?? new FakeAppDialogService(),
            templates,
            morphs);
    }

    private static ProjectModel CreateProjectWithPreset(string presetName)
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset(presetName));
        project.MarkDirty();
        return project;
    }

    private sealed class FakeFileDialogService : IFileDialogService
    {
        public string? OpenProjectPath { get; set; }

        public string? SaveProjectPath { get; set; }

        public string? BodyGenExportFolder { get; set; }

        public string? BosJsonExportFolder { get; set; }

        public int OpenProjectCallCount { get; private set; }

        public Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken)
        {
            OpenProjectCallCount++;
            return Task.FromResult(OpenProjectPath);
        }

        public Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken)
        {
            return Task.FromResult(SaveProjectPath);
        }

        public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult(BodyGenExportFolder);
        }

        public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult(BosJsonExportFolder);
        }
    }

    private sealed class FakeAppDialogService : IAppDialogService
    {
        public bool ConfirmDiscardResult { get; set; } = true;

        public int ConfirmDiscardCallCount { get; private set; }

        public int AboutCallCount { get; private set; }

        public Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken)
        {
            ConfirmDiscardCallCount++;
            return Task.FromResult(ConfirmDiscardResult);
        }

        public void ShowAbout()
        {
            AboutCallCount++;
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

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose()
        {
            Directory.Delete(Path, recursive: true);
        }
    }
}
