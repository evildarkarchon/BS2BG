using System.Collections;
using System.Diagnostics.CodeAnalysis;
using System.Reactive.Threading.Tasks;
using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using Xunit;
using SetSlider = BS2BG.Core.Models.SetSlider;
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

        project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        confirmations.ConfirmDiscardCallCount.Should().Be(1);
        dialogs.OpenProjectCallCount.Should().Be(0);
    }

    [Fact]
    public async Task OpenProjectRemainsCleanWhenSelectionAddsProfileDefaults()
    {
        using var directory = new TemporaryDirectory();
        var loadedProject = new ProjectModel();
        loadedProject.SliderPresets.Add(new SliderPreset("Loaded"));
        var projectPath = Path.Combine(directory.Path, "loaded.jbs2bg");
        new ProjectFileService().Save(loadedProject, projectPath);
        var project = new ProjectModel();
        var dialogs = new FakeFileDialogService { OpenProjectPath = projectPath };
        var viewModel = CreateViewModel(
            project,
            dialogs,
            profileCatalog: CreateProfileCatalogWithDefault("DefaultOnly"));

        await viewModel.OpenProjectAsync(TestContext.Current.CancellationToken);

        project.IsDirty.Should().BeFalse();
        project.SliderPresets.Single().MissingDefaultSetSliders
            .Select(slider => slider.Name)
            .Should().Equal("DefaultOnly");
    }

    [Fact]
    public async Task NewProjectKeepsDirtyProjectWhenDiscardIsCancelled()
    {
        var project = CreateProjectWithPreset("Alpha");
        var confirmations = new FakeAppDialogService { ConfirmDiscardResult = false };
        var viewModel = CreateViewModel(project, new FakeFileDialogService(), confirmations);

        await viewModel.NewProjectAsync(TestContext.Current.CancellationToken);

        project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
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

        project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        project.SliderPresets.Should().NotContain(preset => preset.Name == "DropAlpha");
        viewModel.Morphs.NpcDatabase.Should().BeEmpty();
        viewModel.CurrentProjectPath.Should().BeNull();
        viewModel.StatusMessage.Should().Be("Open cancelled.");
        confirmations.ConfirmDiscardCallCount.Should().Be(1);
    }

    [Fact]
    public async Task DroppedFileProcessingReportsUnexpectedFailures()
    {
        var viewModel = CreateViewModel(new ProjectModel(), new FakeFileDialogService());

        await viewModel.HandleDroppedFilesAsync(new ThrowingReadOnlyList("boom"),
            TestContext.Current.CancellationToken);

        viewModel.StatusMessage.Should().Be("Dropped file processing failed: boom");
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
    public async Task ExportBodyGenInisDoesNotConfirmRoutineCreateNewExport()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(project.SliderPresets[0]);
        project.CustomMorphTargets.Add(target);
        var confirmations = new FakeAppDialogService();
        var viewModel = CreateViewModel(
            project,
            new FakeFileDialogService { BodyGenExportFolder = directory.Path },
            confirmations);

        await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        confirmations.ConfirmExportOverwriteCallCount.Should().Be(0);
        File.Exists(Path.Combine(directory.Path, "templates.ini")).Should().BeTrue();
        File.Exists(Path.Combine(directory.Path, "morphs.ini")).Should().BeTrue();
    }

    [Fact]
    public async Task ExportBodyGenInisConfirmsOverwriteRiskBeforeWriting()
    {
        using var directory = new TemporaryDirectory();
        File.WriteAllText(Path.Combine(directory.Path, "templates.ini"), "keep me");
        var project = CreateProjectWithPreset("Alpha");
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(project.SliderPresets[0]);
        project.CustomMorphTargets.Add(target);
        var confirmations = new FakeAppDialogService { ConfirmExportOverwriteResult = false };
        var viewModel = CreateViewModel(
            project,
            new FakeFileDialogService { BodyGenExportFolder = directory.Path },
            confirmations);

        await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        confirmations.ConfirmExportOverwriteCallCount.Should().Be(1);
        File.ReadAllText(Path.Combine(directory.Path, "templates.ini")).Should().Be("keep me");
        File.Exists(Path.Combine(directory.Path, "morphs.ini")).Should().BeFalse();
        viewModel.StatusMessage.Should().Be("Export cancelled; existing files kept.");
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
    public async Task SaveProjectReportsAtomicLedgerWithoutExportConfirmation()
    {
        using var directory = new TemporaryDirectory();
        var projectPath = Path.Combine(directory.Path, "saved-project.jbs2bg");
        var confirmations = new FakeAppDialogService();
        var viewModel = CreateViewModel(
            CreateProjectWithPreset("Alpha"),
            new FakeFileDialogService { SaveProjectPath = projectPath },
            confirmations,
            projectFileService: new ThrowingProjectFileService(projectPath));

        await viewModel.SaveProjectAsAsync(TestContext.Current.CancellationToken);

        confirmations.ConfirmExportOverwriteCallCount.Should().Be(0);
        viewModel.StatusMessage.Should().StartWith("File operation incomplete");
        viewModel.StatusMessage.Should().Contain("Review which files were written, restored, skipped, or left untouched");
        viewModel.StatusMessage.Should().Contain("save exploded");
        viewModel.HasFileOperationLedger.Should().BeTrue();
        viewModel.LastFileOperationLedger.Should().ContainSingle(row =>
            row.Path == projectPath
            && row.OutcomeLabel == "Left untouched"
            && row.Detail == "target locked");
    }

    [Fact]
    public async Task ExportBodyGenInisReportsAtomicLedgerRows()
    {
        using var directory = new TemporaryDirectory();
        var templatesPath = Path.Combine(directory.Path, "templates.ini");
        var morphsPath = Path.Combine(directory.Path, "morphs.ini");
        File.WriteAllText(templatesPath, "OLD_TEMPLATES");
        File.WriteAllText(morphsPath, "OLD_MORPHS");
        var project = CreateProjectWithPreset("Alpha");
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(project.SliderPresets[0]);
        project.CustomMorphTargets.Add(target);
        var viewModel = CreateViewModel(
            project,
            new FakeFileDialogService { BodyGenExportFolder = directory.Path });

        using (new FileStream(morphsPath, FileMode.Open, FileAccess.Read, FileShare.None))
            await viewModel.ExportBodyGenInisAsync(TestContext.Current.CancellationToken);

        viewModel.StatusMessage.Should().StartWith("File operation incomplete");
        viewModel.StatusMessage.Should().Contain("left untouched");
        viewModel.LastFileOperationLedger.Select(row => row.OutcomeLabel)
            .Should().Contain(new[] { "Restored", "Left untouched" });
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

    [Fact]
    public async Task ExportBosJsonConfirmsOverwriteRiskBeforeWriting()
    {
        using var directory = new TemporaryDirectory();
        File.WriteAllText(Path.Combine(directory.Path, "Preset_One.json"), "keep me");
        var project = CreateProjectWithPreset("Preset:One");
        var confirmations = new FakeAppDialogService { ConfirmExportOverwriteResult = true };
        var viewModel = CreateViewModel(
            project,
            new FakeFileDialogService { BosJsonExportFolder = directory.Path },
            confirmations);

        await viewModel.ExportBosJsonAsync(TestContext.Current.CancellationToken);

        confirmations.ConfirmExportOverwriteCallCount.Should().Be(1);
        File.ReadAllText(Path.Combine(directory.Path, "Preset_One.json"))
            .Should().Contain("\"bodyname\": \"Preset:One\"");
    }

    [Fact]
    public async Task PreviewBodyGenExportPopulatesCreateSummaryWithoutWritingFiles()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var target = new CustomMorphTarget("All|Female");
        target.AddSliderPreset(project.SliderPresets[0]);
        project.CustomMorphTargets.Add(target);
        var dialogs = new FakeFileDialogService { BodyGenExportFolder = directory.Path };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.PreviewBodyGenExportCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        viewModel.HasExportPreview.Should().BeTrue();
        viewModel.ExportPreviewSummary.Should()
            .Be("New files will be created at the paths below. No overwrite confirmation is required.");
        viewModel.ExportPreviewFiles.Select(file => (file.Kind, file.TargetPath, file.EffectLabel, file.IsOverwrite))
            .Should().Equal(
                ("BodyGen", Path.Combine(directory.Path, "templates.ini"), "Create", false),
                ("BodyGen", Path.Combine(directory.Path, "morphs.ini"), "Create", false));
        viewModel.ExportPreviewFiles.SelectMany(file => file.SnippetLines).Should().Contain("All|Female=Alpha");
        File.Exists(Path.Combine(directory.Path, "templates.ini")).Should().BeFalse();
        File.Exists(Path.Combine(directory.Path, "morphs.ini")).Should().BeFalse();
    }

    [Fact]
    public async Task PreviewBosJsonExportPopulatesPathsWithoutWritingFiles()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Preset:One");
        var dialogs = new FakeFileDialogService { BosJsonExportFolder = directory.Path };
        var viewModel = CreateViewModel(project, dialogs);

        await viewModel.PreviewBosJsonExportCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        viewModel.HasExportPreview.Should().BeTrue();
        viewModel.ExportPreviewFiles.Should().ContainSingle(file =>
            file.Kind == "BoS JSON"
            && file.TargetPath == Path.Combine(directory.Path, "Preset_One.json")
            && file.EffectLabel == "Create"
            && !file.IsOverwrite
            && file.SnippetLines.Any(line => line.Contains("\"bodyname\": \"Preset:One\"", StringComparison.Ordinal)));
        File.Exists(Path.Combine(directory.Path, "Preset_One.json")).Should().BeFalse();
    }

    [Fact]
    public async Task WindowFileDialogServiceUsesProjectFolderForProjectOpenAndSave()
    {
        using var directory = new TemporaryDirectory();
        var initialProjectFolder = directory.CreateDirectory("projects");
        var selectedProjectFolder = directory.CreateDirectory("selected-projects");
        var openedProjectPath = Path.Combine(selectedProjectFolder, "opened.jbs2bg");
        var savedProjectPath = Path.Combine(selectedProjectFolder, "saved.jbs2bg");
        var preferences = new CapturingUserPreferencesService(new UserPreferences
        {
            ProjectFolder = initialProjectFolder,
            BodyGenExportFolder = directory.CreateDirectory("bodygen"),
            BosJsonExportFolder = directory.CreateDirectory("bos")
        });
        var backend = new CapturingFileDialogBackend();
        backend.RegisterFolder(initialProjectFolder);
        backend.RegisterFolder(selectedProjectFolder);
        backend.OpenProjectResult = openedProjectPath;
        backend.SaveProjectResult = savedProjectPath;
        var dialogs = new WindowFileDialogService(preferences, backend);

        var openPath = await dialogs.PickOpenProjectFileAsync(TestContext.Current.CancellationToken);
        var savePath = await dialogs.PickSaveProjectFileAsync(null, TestContext.Current.CancellationToken);

        openPath.Should().Be(openedProjectPath);
        savePath.Should().Be(savedProjectPath);
        backend.LastOpenProjectSuggestedStartFolder.Should().Be(initialProjectFolder);
        backend.LastSaveProjectSuggestedStartFolder.Should().Be(selectedProjectFolder);
        preferences.SavedPreferences.Should().NotBeNull();
        preferences.SavedPreferences!.ProjectFolder.Should().Be(selectedProjectFolder);
        preferences.SavedPreferences.BodyGenExportFolder.Should().Be(directory.GetDirectoryPath("bodygen"));
        preferences.SavedPreferences.BosJsonExportFolder.Should().Be(directory.GetDirectoryPath("bos"));
    }

    [Fact]
    public async Task WindowFileDialogServiceUsesIndependentExportFolderChannelsAndIgnoresInvalidHints()
    {
        using var directory = new TemporaryDirectory();
        var bodyGenStart = directory.CreateDirectory("bodygen-start");
        var bodyGenSelected = directory.CreateDirectory("bodygen-selected");
        var bosSelected = directory.CreateDirectory("bos-selected");
        var preferences = new CapturingUserPreferencesService(new UserPreferences
        {
            ProjectFolder = directory.CreateDirectory("projects"),
            BodyGenExportFolder = bodyGenStart,
            BosJsonExportFolder = Path.Combine(directory.Path, "missing-bos")
        });
        var backend = new CapturingFileDialogBackend();
        backend.RegisterFolder(bodyGenStart);
        var dialogs = new WindowFileDialogService(preferences, backend);

        backend.FolderPickerResult = bodyGenSelected;
        var bodyGenFolder = await dialogs.PickBodyGenExportFolderAsync(TestContext.Current.CancellationToken);
        backend.FolderPickerResult = bosSelected;
        var bosFolder = await dialogs.PickBosJsonExportFolderAsync(TestContext.Current.CancellationToken);

        bodyGenFolder.Should().Be(bodyGenSelected);
        bosFolder.Should().Be(bosSelected);
        backend.FolderSuggestedStartFolders.Should().Equal(bodyGenStart, null);
        preferences.SavedPreferences.Should().NotBeNull();
        preferences.SavedPreferences!.ProjectFolder.Should().Be(directory.GetDirectoryPath("projects"));
        preferences.SavedPreferences.BodyGenExportFolder.Should().Be(bodyGenSelected);
        preferences.SavedPreferences.BosJsonExportFolder.Should().Be(bosSelected);
    }

    [Fact]
    public async Task WindowFileDialogServiceIgnoresInvalidProjectFolderHintAndStillSavesSelectionFolder()
    {
        using var directory = new TemporaryDirectory();
        var selectedProjectFolder = directory.CreateDirectory("selected-projects");
        var openedProjectPath = Path.Combine(selectedProjectFolder, "opened.jbs2bg");
        var preferences = new CapturingUserPreferencesService(new UserPreferences
        {
            ProjectFolder = Path.Combine(directory.Path, "missing-project-folder")
        });
        var backend = new CapturingFileDialogBackend
        {
            OpenProjectResult = openedProjectPath
        };
        var dialogs = new WindowFileDialogService(preferences, backend);

        var openPath = await dialogs.PickOpenProjectFileAsync(TestContext.Current.CancellationToken);

        openPath.Should().Be(openedProjectPath);
        backend.LastOpenProjectSuggestedStartFolder.Should().BeNull();
        preferences.SavedPreferences.Should().NotBeNull();
        preferences.SavedPreferences!.ProjectFolder.Should().Be(selectedProjectFolder);
    }

    [Fact]
    public async Task WindowFileDialogServicePreferenceSaveFailureDoesNotBlockProjectOrExportPickerResults()
    {
        using var directory = new TemporaryDirectory();
        var selectedProjectFolder = directory.CreateDirectory("selected-projects");
        var selectedExportFolder = directory.CreateDirectory("selected-bodygen");
        var openedProjectPath = Path.Combine(selectedProjectFolder, "opened.jbs2bg");
        var preferences = new FailingUserPreferencesService(new UserPreferences());
        var backend = new CapturingFileDialogBackend
        {
            OpenProjectResult = openedProjectPath,
            FolderPickerResult = selectedExportFolder
        };
        var dialogs = new WindowFileDialogService(preferences, backend);

        var openPath = await dialogs.PickOpenProjectFileAsync(TestContext.Current.CancellationToken);
        var exportFolder = await dialogs.PickBodyGenExportFolderAsync(TestContext.Current.CancellationToken);

        openPath.Should().Be(openedProjectPath);
        exportFolder.Should().Be(selectedExportFolder);
        preferences.SaveAttempts.Should().Be(2);
    }

    [Fact]
    public async Task ExportBosJsonSnapshotsPresetsBeforeBackgroundWrite()
    {
        using var directory = new TemporaryDirectory();
        var project = new ProjectModel();
        var preset = new SliderPreset("Alpha");
        var slider = new SetSlider("Test") { ValueSmall = 0, ValueBig = 42 };
        preset.AddSetSlider(slider);
        project.SliderPresets.Add(preset);
        project.MarkDirty();

        var templateGeneration = new TemplateGenerationService();
        var profileCatalog = CreateProfileCatalogWithDefault();
        var expectedJson = templateGeneration.PreviewBosJson(
            preset.Clone(),
            profileCatalog.GetProfile(preset.ProfileName));

        var inspectingWriter = new InspectingBosJsonExportWriter(
            templateGeneration,
            () => slider.ValueBig = 999);

        var dialogs = new FakeFileDialogService { BosJsonExportFolder = directory.Path };
        var viewModel = CreateViewModel(
            project,
            dialogs,
            profileCatalog: profileCatalog,
            bosJsonExportWriter: inspectingWriter);

        await viewModel.ExportBosJsonAsync(TestContext.Current.CancellationToken);

        var filePath = Path.Combine(directory.Path, "Alpha.json");
        File.Exists(filePath).Should().BeTrue();
        File.ReadAllText(filePath).Should().Be(expectedJson);
        slider.ValueBig.Should().Be(999);
    }

    [Fact]
    public async Task HandleDroppedFilesCommandIsDisabledWhileShellSaveIsBusy()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs, projectFileService: blocker);
        var dropCommand = (ICommand)viewModel.HandleDroppedFilesCommand;
        dropCommand.CanExecute(Array.Empty<string>()).Should().BeTrue();

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        try
        {
            dropCommand.CanExecute(Array.Empty<string>()).Should().BeFalse();
        }
        finally
        {
            blocker.ReleaseWrite();
            await saveTask;
        }

        dropCommand.CanExecute(Array.Empty<string>()).Should().BeTrue();
    }

    [Fact]
    public async Task DropDuringSaveIsGatedAndPreservesPathAfterSaveCompletes()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var droppedProject = new ProjectModel();
        droppedProject.SliderPresets.Add(new SliderPreset("Loaded"));
        var droppedProjectPath = Path.Combine(directory.Path, "loaded.jbs2bg");
        new ProjectFileService().Save(droppedProject, droppedProjectPath);
        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs, projectFileService: blocker);
        var dropCommand = (ICommand)viewModel.HandleDroppedFilesCommand;
        var droppedFiles = new[] { droppedProjectPath };
        dropCommand.CanExecute(droppedFiles).Should().BeTrue();

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        try
        {
            dropCommand.CanExecute(droppedFiles).Should().BeFalse();
            project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        }
        finally
        {
            blocker.ReleaseWrite();
            await saveTask;
        }

        dropCommand.CanExecute(droppedFiles).Should().BeTrue();
        viewModel.CurrentProjectPath.Should().Be(Path.Combine(directory.Path, "saved-project.jbs2bg"));
        project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
    }

    [Fact]
    public void NotifyDropIgnoredAsBusySetsStatusMessage()
    {
        var viewModel = CreateViewModel(new ProjectModel(), new FakeFileDialogService());

        viewModel.NotifyDropIgnoredAsBusy();

        viewModel.StatusMessage.Should().Be("Drop ignored - application is busy.");
    }

    [Fact]
    public async Task NotifyDropIgnoredAsBusyDuringBlockedSavePreservesProjectAndStatus()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs, projectFileService: blocker);

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        try
        {
            viewModel.IsAnyBusy.Should().BeTrue();
            viewModel.NotifyDropIgnoredAsBusy();
            viewModel.StatusMessage.Should().Be("Drop ignored - application is busy.");
            project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        }
        finally
        {
            blocker.ReleaseWrite();
            await saveTask;
        }
    }

    [Fact]
    public async Task IsAnyBusyRaisesWhileHandleDroppedFilesIsExecuting()
    {
        using var directory = new TemporaryDirectory();
        var droppedProject = new ProjectModel();
        droppedProject.SliderPresets.Add(new SliderPreset("Loaded"));
        var droppedProjectPath = Path.Combine(directory.Path, "loaded.jbs2bg");
        new ProjectFileService().Save(droppedProject, droppedProjectPath);
        var viewModel = CreateViewModel(new ProjectModel(), new FakeFileDialogService());
        var raised = new List<string?>();
        viewModel.PropertyChanged += (_, args) => raised.Add(args.PropertyName);

        await viewModel.HandleDroppedFilesCommand.Execute(new[] { droppedProjectPath })
            .ToTask(TestContext.Current.CancellationToken);

        raised.Should().Contain(nameof(MainWindowViewModel.IsAnyBusy));
        viewModel.IsAnyBusy.Should().BeFalse();
    }

    [Fact]
    public async Task MainWindowDispatchDroppedFilePathsRoutesToBusyNotifierWhileSaving()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var droppedProject = new ProjectModel();
        droppedProject.SliderPresets.Add(new SliderPreset("Loaded"));
        var droppedProjectPath = Path.Combine(directory.Path, "loaded.jbs2bg");
        new ProjectFileService().Save(droppedProject, droppedProjectPath);

        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs, projectFileService: blocker);

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        try
        {
            viewModel.IsAnyBusy.Should().BeTrue();
            MainWindow.DispatchDroppedFilePaths(viewModel, new[] { droppedProjectPath });

            viewModel.StatusMessage.Should().Be("Drop ignored - application is busy.");
            project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
        }
        finally
        {
            blocker.ReleaseWrite();
            await saveTask;
        }

        viewModel.CurrentProjectPath.Should().Be(Path.Combine(directory.Path, "saved-project.jbs2bg"));
        project.SliderPresets.Select(preset => preset.Name).Should().Equal("Alpha");
    }

    [Fact]
    public void MainWindowDispatchDroppedFilePathsExecutesCommandWhenIdle()
    {
        var viewModel = CreateViewModel(new ProjectModel(), new FakeFileDialogService());
        viewModel.IsAnyBusy.Should().BeFalse();

        MainWindow.DispatchDroppedFilePaths(viewModel, Array.Empty<string>());

        viewModel.StatusMessage.Should().Be("No files dropped.");
    }

    [Fact]
    public void IsAnyBusyIsFalseInitially()
    {
        var viewModel = CreateViewModel(new ProjectModel(), new FakeFileDialogService());

        viewModel.IsAnyBusy.Should().BeFalse();
    }

    [Fact]
    public async Task IsAnyBusyRaisesWhenShellSaveTogglesIsBusy()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs);
        var raised = new List<string?>();
        viewModel.PropertyChanged += (_, args) => raised.Add(args.PropertyName);

        await viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        raised.Should().Contain(nameof(MainWindowViewModel.IsAnyBusy));
        viewModel.IsAnyBusy.Should().BeFalse();
    }

    [Fact]
    public async Task IsAnyBusyRaisesWhenTemplatesIsBusyToggles()
    {
        var project = new ProjectModel();
        var viewModel = CreateViewModel(project, new FakeFileDialogService());
        var raised = new List<string?>();
        viewModel.PropertyChanged += (_, args) => raised.Add(args.PropertyName);

        await viewModel.Templates.ImportPresetsCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        raised.Should().Contain(nameof(MainWindowViewModel.IsAnyBusy));
        viewModel.IsAnyBusy.Should().BeFalse();
    }

    [Fact]
    public async Task IsAnyBusyRaisesWhenMorphsIsBusyToggles()
    {
        var project = new ProjectModel();
        var viewModel = CreateViewModel(project, new FakeFileDialogService());
        var raised = new List<string?>();
        viewModel.PropertyChanged += (_, args) => raised.Add(args.PropertyName);

        await viewModel.Morphs.ImportNpcsCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        raised.Should().Contain(nameof(MainWindowViewModel.IsAnyBusy));
        viewModel.IsAnyBusy.Should().BeFalse();
    }

    [Fact]
    public async Task NewProjectCommandIsDisabledWhileTemplatesAreBusy()
    {
        var viewModel = CreateViewModel(new ProjectModel(), new FakeFileDialogService());
        var newProjectCommand = (ICommand)viewModel.NewProjectCommand;
        var disabledDuringBusy = new List<bool>();
        viewModel.PropertyChanged += (_, args) =>
        {
            if (args.PropertyName == nameof(MainWindowViewModel.IsAnyBusy) && viewModel.IsAnyBusy)
                disabledDuringBusy.Add(newProjectCommand.CanExecute(null));
        };

        await viewModel.Templates.ImportPresetsCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        disabledDuringBusy.Should().NotBeEmpty();
        disabledDuringBusy.Should().OnlyContain(canExecute => !canExecute);
        newProjectCommand.CanExecute(null).Should().BeTrue();
    }

    [Fact]
    public async Task SaveProjectCommandIsDisabledWhileMorphsAreBusy()
    {
        var project = CreateProjectWithPreset("Alpha");
        var viewModel = CreateViewModel(project, new FakeFileDialogService());
        var saveProjectCommand = (ICommand)viewModel.SaveProjectCommand;
        var disabledDuringBusy = new List<bool>();
        viewModel.PropertyChanged += (_, args) =>
        {
            if (args.PropertyName == nameof(MainWindowViewModel.IsAnyBusy) && viewModel.IsAnyBusy)
                disabledDuringBusy.Add(saveProjectCommand.CanExecute(null));
        };

        await viewModel.Morphs.ImportNpcsCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        disabledDuringBusy.Should().NotBeEmpty();
        disabledDuringBusy.Should().OnlyContain(canExecute => !canExecute);
        saveProjectCommand.CanExecute(null).Should().BeTrue();
    }

    [Fact]
    public async Task ExportBosJsonCommandIsDisabledWhileTemplatesAreBusy()
    {
        var project = CreateProjectWithPreset("Alpha");
        var viewModel = CreateViewModel(project, new FakeFileDialogService());
        var exportCommand = (ICommand)viewModel.ExportBosJsonCommand;
        var disabledDuringBusy = new List<bool>();
        viewModel.PropertyChanged += (_, args) =>
        {
            if (args.PropertyName == nameof(MainWindowViewModel.IsAnyBusy) && viewModel.IsAnyBusy)
                disabledDuringBusy.Add(exportCommand.CanExecute(null));
        };

        await viewModel.Templates.ImportPresetsCommand.Execute().ToTask(TestContext.Current.CancellationToken);

        disabledDuringBusy.Should().NotBeEmpty();
        disabledDuringBusy.Should().OnlyContain(canExecute => !canExecute);
        exportCommand.CanExecute(null).Should().BeTrue();
    }

    [Fact]
    public async Task SaveProjectKeepsDirtyWhenProjectIsMutatedDuringSave()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs, projectFileService: blocker);

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        project.SliderPresets.Add(new SliderPreset("Beta"));
        blocker.ReleaseWrite();
        await saveTask;

        File.Exists(Path.Combine(directory.Path, "saved-project.jbs2bg")).Should().BeTrue();
        project.IsDirty.Should().BeTrue();
        viewModel.StatusMessage.Should().Contain("later edits remain unsaved");
    }

    [Fact]
    public async Task SaveProjectMarksCleanWhenNoEditsHappenDuringSave()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs, projectFileService: blocker);

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        blocker.ReleaseWrite();
        await saveTask;

        project.IsDirty.Should().BeFalse();
        viewModel.StatusMessage.Should().StartWith("Saved");
        viewModel.StatusMessage.Should().NotContain("later edits");
    }

    [Fact]
    public async Task TemplatesRemovePresetCommandIsDisabledWhileSaving()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs, projectFileService: blocker);
        viewModel.Templates.SelectedPreset = project.SliderPresets[0];
        var removeCommand = (ICommand)viewModel.Templates.RemoveSelectedPresetCommand;
        removeCommand.CanExecute(null).Should().BeTrue();

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        try
        {
            removeCommand.CanExecute(null).Should().BeFalse();
        }
        finally
        {
            blocker.ReleaseWrite();
            await saveTask;
        }

        removeCommand.CanExecute(null).Should().BeTrue();
    }

    [Fact]
    public async Task MorphsAddCustomTargetCommandIsDisabledWhileSaving()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var viewModel = CreateViewModel(project, dialogs, projectFileService: blocker);
        var addCommand = (ICommand)viewModel.Morphs.AddCustomTargetCommand;
        addCommand.CanExecute(null).Should().BeTrue();

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        try
        {
            addCommand.CanExecute(null).Should().BeFalse();
        }
        finally
        {
            blocker.ReleaseWrite();
            await saveTask;
        }

        addCommand.CanExecute(null).Should().BeTrue();
    }

    [Fact]
    public async Task UndoCommandIsDisabledWhileSaving()
    {
        using var directory = new TemporaryDirectory();
        var project = CreateProjectWithPreset("Alpha");
        var blocker = new BlockingProjectFileService();
        var dialogs = new FakeFileDialogService { SaveProjectPath = Path.Combine(directory.Path, "saved-project") };
        var undoRedo = new UndoRedoService();
        undoRedo.Record("test", () => { }, () => { });
        var viewModel = CreateViewModelWithUndoRedo(project, dialogs, undoRedo, blocker);
        var undoCommand = (ICommand)viewModel.UndoCommand;
        undoCommand.CanExecute(null).Should().BeTrue();

        var saveTask = viewModel.SaveProjectCommand.Execute().ToTask(TestContext.Current.CancellationToken);
        await blocker.WriteStarted.Task.WaitAsync(TimeSpan.FromSeconds(5), TestContext.Current.CancellationToken);
        try
        {
            undoCommand.CanExecute(null).Should().BeFalse();
        }
        finally
        {
            blocker.ReleaseWrite();
            await saveTask;
        }
    }

    private static MainWindowViewModel CreateViewModelWithUndoRedo(
        ProjectModel project,
        FakeFileDialogService fileDialogs,
        UndoRedoService undoRedo,
        ProjectFileService? projectFileService = null)
    {
        var parser = new BodySlideXmlParser();
        var templateGeneration = new TemplateGenerationService();
        var profileCatalog = CreateProfileCatalogWithDefault();
        var templates = new TemplatesViewModel(
            project,
            parser,
            templateGeneration,
            profileCatalog,
            new EmptyBodySlideXmlFilePicker(),
            new EmptyClipboardService(),
            undoRedo);
        var morphs = new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(new RandomAssignmentProvider()),
            new MorphGenerationService(),
            new EmptyNpcTextFilePicker(),
            new EmptyClipboardService(),
            undoRedo: undoRedo);

        return new MainWindowViewModel(
            project,
            projectFileService ?? new ProjectFileService(),
            templateGeneration,
            new MorphGenerationService(),
            profileCatalog,
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(templateGeneration),
            fileDialogs,
            new FakeAppDialogService(),
            templates,
            morphs,
            undoRedo);
    }

    private static MainWindowViewModel CreateViewModel(
        ProjectModel project,
        FakeFileDialogService fileDialogs,
        FakeAppDialogService? dialogs = null,
        TemplateProfileCatalog? profileCatalog = null,
        ProjectFileService? projectFileService = null,
        BosJsonExportWriter? bosJsonExportWriter = null)
    {
        var parser = new BodySlideXmlParser();
        var templateGeneration = new TemplateGenerationService();
        profileCatalog ??= CreateProfileCatalogWithDefault();
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
            projectFileService ?? new ProjectFileService(),
            templateGeneration,
            new MorphGenerationService(),
            profileCatalog,
            new BodyGenIniExportWriter(),
            bosJsonExportWriter ?? new BosJsonExportWriter(templateGeneration),
            fileDialogs,
            dialogs ?? new FakeAppDialogService(),
            templates,
            morphs);
    }

    private static TemplateProfileCatalog CreateProfileCatalogWithDefault(params string[] defaultSliders)
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    defaultSliders.Select(slider => new SliderDefault(slider, 0f, 1f)),
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
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

    private sealed class CapturingUserPreferencesService(UserPreferences initialPreferences) : IUserPreferencesService
    {
        private UserPreferences current = initialPreferences;

        public UserPreferences? SavedPreferences { get; private set; }

        public UserPreferences Load() => current;

        public bool Save(UserPreferences preferences)
        {
            SavedPreferences = preferences;
            current = preferences;
            return true;
        }
    }

    private sealed class FailingUserPreferencesService(UserPreferences initialPreferences) : IUserPreferencesService
    {
        public int SaveAttempts { get; private set; }

        public UserPreferences Load() => initialPreferences;

        public bool Save(UserPreferences preferences)
        {
            SaveAttempts++;
            return false;
        }
    }

    private sealed class CapturingFileDialogBackend : IFileDialogBackend
    {
        private readonly HashSet<string> validFolders = new(StringComparer.OrdinalIgnoreCase);

        public bool CanOpen => true;

        public bool CanSave => true;

        public bool CanPickFolder => true;

        public string? OpenProjectResult { get; set; }

        public string? SaveProjectResult { get; set; }

        public string? FolderPickerResult { get; set; }

        public string? LastOpenProjectSuggestedStartFolder { get; private set; }

        public string? LastSaveProjectSuggestedStartFolder { get; private set; }

        public List<string?> FolderSuggestedStartFolders { get; } = new();

        public void RegisterFolder(string path) => validFolders.Add(path);

        public Task<string?> PickOpenProjectFileAsync(string? suggestedStartFolder, CancellationToken cancellationToken)
        {
            LastOpenProjectSuggestedStartFolder = suggestedStartFolder;
            return Task.FromResult(OpenProjectResult);
        }

        public Task<string?> PickSaveProjectFileAsync(
            string? currentPath,
            string? suggestedStartFolder,
            CancellationToken cancellationToken)
        {
            LastSaveProjectSuggestedStartFolder = suggestedStartFolder;
            return Task.FromResult(SaveProjectResult);
        }

        public Task<string?> PickFolderAsync(
            string title,
            string? suggestedStartFolder,
            CancellationToken cancellationToken)
        {
            FolderSuggestedStartFolders.Add(suggestedStartFolder);
            return Task.FromResult(FolderPickerResult);
        }

        public Task<string?> ResolveStartFolderAsync(string? path, CancellationToken cancellationToken) =>
            Task.FromResult(!string.IsNullOrWhiteSpace(path) && validFolders.Contains(path) ? path : null);
    }

    private sealed class FakeAppDialogService : IAppDialogService
    {
        public bool ConfirmDiscardResult { get; set; } = true;

        public bool ConfirmExportOverwriteResult { get; set; } = true;

        public int ConfirmDiscardCallCount { get; private set; }

        public int ConfirmExportOverwriteCallCount { get; private set; }

        public int AboutCallCount { get; private set; }

        public Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken)
        {
            ConfirmDiscardCallCount++;
            return Task.FromResult(ConfirmDiscardResult);
        }

        public Task<bool> ConfirmBulkOperationAsync(
            string title,
            string message,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmExportOverwriteAsync(
            ExportPreviewResult preview,
            CancellationToken cancellationToken)
        {
            ConfirmExportOverwriteCallCount++;
            return Task.FromResult(ConfirmExportOverwriteResult);
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

    private sealed class ThrowingReadOnlyList(string message) : IReadOnlyList<string>
    {
        public int Count => 1;

        public string this[int index] => throw new InvalidOperationException(message);

        public IEnumerator<string> GetEnumerator() => throw new InvalidOperationException(message);

        IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();
    }

    private sealed class InspectingBosJsonExportWriter : BosJsonExportWriter
    {
        private readonly Action mutateAfterCapture;

        public InspectingBosJsonExportWriter(
            TemplateGenerationService templateGenerationService,
            Action mutateAfterCapture)
            : base(templateGenerationService) =>
            this.mutateAfterCapture = mutateAfterCapture;

        public override BosJsonExportResult Write(
            string directoryPath,
            IEnumerable<SliderPreset> presets,
            TemplateProfileCatalog profileCatalog)
        {
            var materialized = presets.ToList();
            mutateAfterCapture();
            return base.Write(directoryPath, materialized, profileCatalog);
        }
    }

    private sealed class BlockingProjectFileService : ProjectFileService
    {
        private readonly TaskCompletionSource releaseWrite = new();

        public TaskCompletionSource WriteStarted { get; } = new();

        public void ReleaseWrite() => releaseWrite.TrySetResult();

        public override void WriteAtomic(string content, string path)
        {
            WriteStarted.TrySetResult();
            releaseWrite.Task.GetAwaiter().GetResult();
            base.WriteAtomic(content, path);
        }
    }

    private sealed class ThrowingProjectFileService(string targetPath) : ProjectFileService
    {
        public override void WriteAtomic(string content, string path) =>
            throw new AtomicWriteException(
                "save exploded",
                new IOException("save exploded"),
                new[] { new FileWriteLedgerEntry(targetPath, FileWriteOutcome.LeftUntouched, "target locked") });
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

        public string CreateDirectory(string name)
        {
            var directoryPath = GetDirectoryPath(name);
            Directory.CreateDirectory(directoryPath);
            return directoryPath;
        }

        public string GetDirectoryPath(string name) => System.IO.Path.Combine(Path, name);
    }
}
