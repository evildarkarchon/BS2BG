using System.Diagnostics.CodeAnalysis;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Export;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using Xunit;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments",
    Justification = "Small expected arrays keep test intent readable.")]
public sealed class MainWindowViewModelTests
{
    [Fact]
    public async Task SaveProjectPromptsForPathWhenCurrentPathIsMissing()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.SaveProjectAsync(TestContext.Current.CancellationToken);

        var expectedPath = Path.Combine(directory.Path, "saved-project.jbs2bg");
        File.Exists(expectedPath).Should().BeTrue();
        viewModel.CurrentProjectPath.Should().Be(expectedPath);
        project.IsDirty.Should().BeFalse();
    }

    [Fact]
    public async Task SaveProjectAsAddsProjectExtension()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "explicit-name") };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.SaveProjectAsAsync(TestContext.Current.CancellationToken);

        File.Exists(Path.Combine(directory.Path, "explicit-name.jbs2bg")).Should().BeTrue();
    }

    [Fact]
    public async Task OpenProjectKeepsDirtyProjectWhenDiscardIsCancelled()
    {
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService();
        var confirmations = new FakeAppDialogService { ConfirmDiscardResult = false };
        var viewModel = CreateViewModel(project, dialogs, confirmations);

        await viewModel.OpenProjectAsync(TestContext.Current.CancellationToken);

        project.SliderPresets.Select(preset => preset.Name).Should().Equal(new[] { "Alpha" });
        confirmations.ConfirmDiscardCallCount.Should().Be(1);
        dialogs.OpenProjectCallCount.Should().Be(0);
    }

    [Fact]
    public async Task NewProjectKeepsDirtyProjectWhenDiscardIsCancelled()
    {
        var project = CreateProjectWithPreset("Alpha");
        var confirmations = new FakeAppDialogService { ConfirmDiscardResult = false };
        var viewModel = CreateViewModel(project, new FakeFileDialogService(), confirmations);

        await viewModel.NewProjectAsync(TestContext.Current.CancellationToken);

        project.SliderPresets.Select(preset => preset.Name).Should().Equal(new[] { "Alpha" });
        confirmations.ConfirmDiscardCallCount.Should().Be(1);
    }

    [Fact]
    public async Task DroppedProjectStopsPresetAndNpcImportsWhenDiscardIsCancelled()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var droppedProject = new ProjectModel();
        droppedProject.SliderPresets.Add(new SliderPreset("Loaded"));
        var droppedProjectPath = Path.Combine(directory.Path, "loaded.jbs2bg");
        new ProjectFileService().Save(droppedProject, droppedProjectPath);
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
        var confirmations = new FakeAppDialogService { ConfirmDiscardResult = false };
        var viewModel = CreateViewModel(project, new FakeFileDialogService(), confirmations);

        await viewModel.HandleDroppedFilesAsync(
            new[] { droppedProjectPath, xmlPath, npcPath },
            TestContext.Current.CancellationToken);

        project.SliderPresets.Select(preset => preset.Name).Should().Equal(new[] { "Alpha" });
        project.SliderPresets.Should().NotContain(preset => preset.Name == "DropAlpha");
        viewModel.Morphs.NpcDatabase.Should().BeEmpty();
        viewModel.CurrentProjectPath.Should().BeNull();
        viewModel.StatusMessage.Should().Be("Open cancelled.");
        confirmations.ConfirmDiscardCallCount.Should().Be(1);
    }

    [Fact]
    public async Task ExportBodyGenInisRegeneratesAndWritesCurrentOutput()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(project.SliderPresets[0]);
        project.CustomMorphTargets.Add(target);
        var dialogs = new FakeFileDialogService { BodyGenExportFolder = directory.Path };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        File.Exists(Path.Combine(directory.Path, "templates.ini")).Should().BeTrue();
        File.ReadAllText(Path.Combine(directory.Path, "morphs.ini")).Should().Be("All|Female=Alpha");
        viewModel.StatusMessage.Should().ContainEquivalentOf("exported");
    }

    [Fact]
    public async Task ExportBodyGenInisReportsEmptyOutputWithoutWritingFiles()
    {
        using var directory = new TemporaryDirectory();
        var viewModel = CreateViewModel(new ProjectModel(),
            new FakeFileDialogService { BodyGenExportFolder = directory.Path });

        await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        File.Exists(Path.Combine(directory.Path, "templates.ini")).Should().BeFalse();
        File.Exists(Path.Combine(directory.Path, "morphs.ini")).Should().BeFalse();
        viewModel.StatusMessage.Should().Contain("No generated BodyGen output");
    }

    [Fact]
    public async Task ExportBodyGenInisReportsWriteFailures()
    {
        using var directory = new TemporaryDirectory();
        var blockingFile = Path.Combine(directory.Path, "not-a-folder");
        File.WriteAllText(blockingFile, string.Empty);
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService { BodyGenExportFolder = blockingFile };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        viewModel.StatusMessage.Should().ContainEquivalentOf("failed");
    }

    [Fact]
    public async Task ExportBosJsonWritesOneFilePerPreset()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Preset:One");
        var dialogs = new FakeFileDialogService { BosJsonExportFolder = directory.Path };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.ExportBosJsonAsync(TestContext.Current.CancellationToken);

        var file = Path.Combine(directory.Path, "Preset_One.json");
        File.Exists(file).Should().BeTrue();
        File.ReadAllText(file).Should().Contain("\"bodyname\": \"Preset:One\"");
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
                new SliderProfile(
                    Array.Empty<SliderDefault>(),
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
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

        public Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken) =>
            Task.FromResult(SaveProjectPath);

        public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken) =>
            Task.FromResult(BodyGenExportFolder);

        public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken) =>
            Task.FromResult(BosJsonExportFolder);
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

        public void ShowAbout() => AboutCallCount++;
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
