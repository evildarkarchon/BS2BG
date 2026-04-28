using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Diagnostics;
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

public sealed class MainWindowViewModelProfileRecoveryTests
{
    /// <summary>
    /// Verifies profile-conflict decisions can be faked for ViewModel tests without Avalonia UI coupling.
    /// </summary>
    [Fact]
    public async Task FakeDialogReturnsEveryProfileConflictDecisionAndCancel()
    {
        var decisions = new ProfileConflictDecision?[]
        {
            new(ProfileConflictResolution.UseProjectCopy, null),
            new(ProfileConflictResolution.ReplaceLocalProfile, null),
            new(ProfileConflictResolution.RenameProjectCopy, "Project Copy"),
            new(ProfileConflictResolution.KeepLocalProfile, null),
            null,
        };
        var dialog = new FakeAppDialogService(decisions);
        var request = new ProfileConflictRequest(
            "Shared Body",
            "Local custom profile from C:/profiles/shared.json",
            "Embedded project profile from shared project");

        foreach (var expected in decisions)
        {
            var actual = await dialog.PromptProfileConflictAsync(request, TestContext.Current.CancellationToken);

            actual.Should().Be(expected);
        }

        dialog.ProfileConflictRequests.Should().HaveCount(5);
        dialog.ProfileConflictRequests.Should().OnlyContain(item => item.ProfileName == "Shared Body");
    }

    /// <summary>
    /// Verifies missing custom profile references remain non-blocking and visibly recoverable after project open.
    /// </summary>
    [Fact]
    public async Task OpenProjectWithMissingCustomProfileSucceedsWithVisibleFallbackStatus()
    {
        var currentProject = new ProjectModel();
        var services = CreateViewModel(currentProject, []);
        var path = WriteProject(CreateProjectWithPreset("Preset", "Missing Body"));

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        currentProject.SliderPresets.Should().ContainSingle().Which.ProfileName.Should().Be("Missing Body");
        services.ViewModel.StatusMessage.Should().Contain("Missing custom profile references use visible fallback");
    }

    /// <summary>
    /// Verifies cancelling any conflict prompt aborts before replacing the current project or overlay.
    /// </summary>
    [Fact]
    public async Task CancelConflictLeavesCurrentProjectAndOverlayUnchanged()
    {
        var currentProject = CreateProjectWithPreset("Existing", "Previous Overlay");
        var previousOverlay = CreateProfile("Previous Overlay", 9f, ProfileSourceKind.EmbeddedProject);
        var local = CreateProfile("Shared Body", 1f, ProfileSourceKind.LocalCustom);
        var services = CreateViewModel(currentProject, [local]);
        services.CatalogService.WithProjectProfiles([previousOverlay]);
        var path = WriteProject(CreateProjectWithPreset("Imported", "Shared Body", CreateProfile("Shared Body", 2f, ProfileSourceKind.EmbeddedProject)));
        services.Dialog.Enqueue(null);

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        currentProject.SliderPresets.Should().ContainSingle().Which.Name.Should().Be("Existing");
        services.CatalogService.Current.GetProfile("Previous Overlay").SliderProfile.GetDefaultSmall("Probe").Should().Be(9);
        services.CatalogService.SavedProfiles.Should().BeEmpty();
        services.ViewModel.StatusMessage.Should().Contain("cancelled");
    }

    /// <summary>
    /// Verifies using the project copy activates a project-scoped overlay that wins over same-name local data.
    /// </summary>
    [Fact]
    public async Task UseProjectCopyActivatesProjectScopedOverlayOverLocalProfile()
    {
        var currentProject = new ProjectModel();
        var local = CreateProfile("Shared Body", 1f, ProfileSourceKind.LocalCustom);
        var embedded = CreateProfile("Shared Body", 2f, ProfileSourceKind.EmbeddedProject);
        var services = CreateViewModel(currentProject, [local]);
        services.Dialog.Enqueue(new ProfileConflictDecision(ProfileConflictResolution.UseProjectCopy, null));
        var path = WriteProject(CreateProjectWithPreset("Imported", "Shared Body", embedded));

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        services.CatalogService.Current.Entries.Single(entry => entry.Name == "Shared Body").SourceKind.Should().Be(ProfileSourceKind.EmbeddedProject);
        services.CatalogService.Current.GetProfile("Shared Body").SliderProfile.GetDefaultSmall("Probe").Should().Be(2);
        currentProject.CustomProfiles.Should().ContainSingle(profile => profile.Name == "Shared Body");
    }

    /// <summary>
    /// Verifies replacing local profile data saves only after decisions complete and refreshes the local catalog.
    /// </summary>
    [Fact]
    public async Task ReplaceLocalProfileSavesEmbeddedProfileAfterAcceptedDecision()
    {
        var currentProject = new ProjectModel();
        var local = CreateProfile("Shared Body", 1f, ProfileSourceKind.LocalCustom);
        var embedded = CreateProfile("Shared Body", 2f, ProfileSourceKind.EmbeddedProject);
        var services = CreateViewModel(currentProject, [local]);
        services.Dialog.Enqueue(new ProfileConflictDecision(ProfileConflictResolution.ReplaceLocalProfile, null));
        var path = WriteProject(CreateProjectWithPreset("Imported", "Shared Body", embedded));

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        services.CatalogService.SavedProfiles.Should().ContainSingle(profile => profile.Name == "Shared Body");
        services.CatalogService.Current.Entries.Single(entry => entry.Name == "Shared Body").SourceKind.Should().Be(ProfileSourceKind.LocalCustom);
        services.CatalogService.Current.GetProfile("Shared Body").SliderProfile.GetDefaultSmall("Probe").Should().Be(2);
    }

    /// <summary>
    /// Verifies a local-store write failure aborts without replacing the current project or previous overlay.
    /// </summary>
    [Fact]
    public async Task ReplaceLocalProfileWriteFailureRollsBackInMemoryProjectAndOverlay()
    {
        var currentProject = CreateProjectWithPreset("Existing", "Previous Overlay");
        var previousOverlay = CreateProfile("Previous Overlay", 9f, ProfileSourceKind.EmbeddedProject);
        var local = CreateProfile("Shared Body", 1f, ProfileSourceKind.LocalCustom);
        var embedded = CreateProfile("Shared Body", 2f, ProfileSourceKind.EmbeddedProject);
        var services = CreateViewModel(currentProject, [local]);
        services.CatalogService.WithProjectProfiles([previousOverlay]);
        services.CatalogService.FailSaves = true;
        services.Dialog.Enqueue(new ProfileConflictDecision(ProfileConflictResolution.ReplaceLocalProfile, null));
        var path = WriteProject(CreateProjectWithPreset("Imported", "Shared Body", embedded));

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        currentProject.SliderPresets.Should().ContainSingle().Which.Name.Should().Be("Existing");
        services.CatalogService.Current.GetProfile("Previous Overlay").SliderProfile.GetDefaultSmall("Probe").Should().Be(9);
        services.ViewModel.StatusMessage.Should().Contain("Could not save custom profile");
    }

    /// <summary>
    /// Verifies rename decisions update embedded profile names and preset references while marking the opened project dirty.
    /// </summary>
    [Fact]
    public async Task RenameProjectCopyValidatesUniquenessUpdatesReferencesAndMarksDirty()
    {
        var currentProject = new ProjectModel();
        var local = CreateProfile("Shared Body", 1f, ProfileSourceKind.LocalCustom);
        var embedded = CreateProfile("Shared Body", 2f, ProfileSourceKind.EmbeddedProject);
        var services = CreateViewModel(currentProject, [local]);
        services.Dialog.Enqueue(new ProfileConflictDecision(ProfileConflictResolution.RenameProjectCopy, "Shared Body Project"));
        var path = WriteProject(CreateProjectWithPreset("Imported", "Shared Body", embedded));

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        currentProject.SliderPresets.Should().ContainSingle().Which.ProfileName.Should().Be("Shared Body Project");
        currentProject.CustomProfiles.Should().ContainSingle(profile => profile.Name == "Shared Body Project");
        currentProject.IsDirty.Should().BeTrue();
        services.CatalogService.Current.GetProfile("Shared Body Project").SliderProfile.GetDefaultSmall("Probe").Should().Be(2);
    }

    /// <summary>
    /// Verifies Rename Project Copy cannot keep the same display name as the local profile that caused the conflict.
    /// </summary>
    [Fact]
    public async Task RenameProjectCopyRejectsOriginalConflictingLocalProfileName()
    {
        var currentProject = new ProjectModel();
        var local = CreateProfile("Shared Body", 1f, ProfileSourceKind.LocalCustom);
        var embedded = CreateProfile("Shared Body", 2f, ProfileSourceKind.EmbeddedProject);
        var services = CreateViewModel(currentProject, [local]);
        services.Dialog.Enqueue(new ProfileConflictDecision(ProfileConflictResolution.RenameProjectCopy, "Shared Body"));
        var path = WriteProject(CreateProjectWithPreset("Imported", "Shared Body", embedded));

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        currentProject.SliderPresets.Should().BeEmpty();
        currentProject.CustomProfiles.Should().BeEmpty();
        services.ViewModel.StatusMessage.Should().Contain("conflicts with an existing bundled, local, embedded, or renamed profile");
    }

    /// <summary>
    /// Verifies Rename Project Copy cannot choose any other existing local custom profile display name.
    /// </summary>
    [Fact]
    public async Task RenameProjectCopyRejectsAnotherLocalCustomProfileName()
    {
        var currentProject = new ProjectModel();
        var local = CreateProfile("Shared Body", 1f, ProfileSourceKind.LocalCustom);
        var otherLocal = CreateProfile("Other Body", 3f, ProfileSourceKind.LocalCustom);
        var embedded = CreateProfile("Shared Body", 2f, ProfileSourceKind.EmbeddedProject);
        var services = CreateViewModel(currentProject, [local, otherLocal]);
        services.Dialog.Enqueue(new ProfileConflictDecision(ProfileConflictResolution.RenameProjectCopy, "Other Body"));
        var path = WriteProject(CreateProjectWithPreset("Imported", "Shared Body", embedded));

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        currentProject.SliderPresets.Should().BeEmpty();
        currentProject.CustomProfiles.Should().BeEmpty();
        services.ViewModel.StatusMessage.Should().Contain("conflicts with an existing bundled, local, embedded, or renamed profile");
    }

    /// <summary>
    /// Verifies keeping local profile data prevents the embedded conflict copy from becoming the active overlay.
    /// </summary>
    [Fact]
    public async Task KeepLocalProfileLeavesLocalCatalogEntryActive()
    {
        var currentProject = new ProjectModel();
        var local = CreateProfile("Shared Body", 1f, ProfileSourceKind.LocalCustom);
        var embedded = CreateProfile("Shared Body", 2f, ProfileSourceKind.EmbeddedProject);
        var services = CreateViewModel(currentProject, [local]);
        services.Dialog.Enqueue(new ProfileConflictDecision(ProfileConflictResolution.KeepLocalProfile, null));
        var path = WriteProject(CreateProjectWithPreset("Imported", "Shared Body", embedded));

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        services.CatalogService.Current.Entries.Single(entry => entry.Name == "Shared Body").SourceKind.Should().Be(ProfileSourceKind.LocalCustom);
        services.CatalogService.Current.GetProfile("Shared Body").SliderProfile.GetDefaultSmall("Probe").Should().Be(1);
    }

    /// <summary>
    /// Verifies embedded profiles cannot shadow bundled names through the active project overlay.
    /// </summary>
    [Fact]
    public async Task BundledNameEmbeddedProfileCollisionIsIgnoredAndDoesNotOverlayBundledProfile()
    {
        var currentProject = new ProjectModel();
        var services = CreateViewModel(currentProject, []);
        var path = WriteRawProjectWithBundledEmbeddedProfile();

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        services.CatalogService.Current.Entries.Single(entry => entry.Name == ProjectProfileMapping.SkyrimCbbe).SourceKind.Should().Be(ProfileSourceKind.Bundled);
        services.CatalogService.Current.GetProfile(ProjectProfileMapping.SkyrimCbbe).SliderProfile.GetDefaultSmall("Probe").Should().Be(0);
        services.ViewModel.StatusMessage.Should().Contain("embedded profile diagnostics");
    }

    /// <summary>
    /// Verifies multiple conflicts collect all decisions before any local profile save is attempted.
    /// </summary>
    [Fact]
    public async Task MultipleConflictsCollectDecisionsBeforeSaveAndCancelPreventsMutation()
    {
        var currentProject = new ProjectModel();
        var firstLocal = CreateProfile("First", 1f, ProfileSourceKind.LocalCustom);
        var secondLocal = CreateProfile("Second", 1f, ProfileSourceKind.LocalCustom);
        var services = CreateViewModel(currentProject, [firstLocal, secondLocal]);
        services.Dialog.Enqueue(new ProfileConflictDecision(ProfileConflictResolution.ReplaceLocalProfile, null));
        services.Dialog.Enqueue(null);
        var project = CreateProjectWithPreset("Imported", "First", CreateProfile("First", 2f, ProfileSourceKind.EmbeddedProject));
        project.SliderPresets.Add(new SliderPreset("Imported 2", "Second"));
        project.CustomProfiles.Add(CreateProfile("Second", 3f, ProfileSourceKind.EmbeddedProject));
        var path = WriteProject(project);

        await services.ViewModel.OpenProjectPathAsync(path, TestContext.Current.CancellationToken);

        services.Dialog.ProfileConflictRequests.Select(request => request.ProfileName).Should().Equal("First", "Second");
        services.CatalogService.SavedProfiles.Should().BeEmpty();
        currentProject.SliderPresets.Should().BeEmpty();
    }

    private static ViewModelServices CreateViewModel(ProjectModel project, IReadOnlyList<CustomProfileDefinition> localProfiles)
    {
        var templateGeneration = new TemplateGenerationService();
        var catalogService = new StubTemplateProfileCatalogService(localProfiles);
        var templates = new TemplatesViewModel(
            project,
            new BodySlideXmlParser(),
            templateGeneration,
            catalogService,
            new EmptyBodySlideXmlFilePicker(),
            new EmptyClipboardService());
        var morphs = new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(new RandomAssignmentProvider()),
            new MorphGenerationService(),
            new EmptyNpcTextFilePicker(),
            new EmptyClipboardService());
        var dialog = new FakeAppDialogService([]);
        var viewModel = new MainWindowViewModel(
            project,
            new ProjectFileService(),
            templateGeneration,
            new MorphGenerationService(),
            catalogService,
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(templateGeneration),
            new EmptyFileDialogService(),
            dialog,
            templates,
            morphs);
        return new ViewModelServices(viewModel, catalogService, dialog);
    }

    private static ProjectModel CreateProjectWithPreset(
        string presetName,
        string profileName,
        params CustomProfileDefinition[] embeddedProfiles)
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset(presetName, profileName));
        foreach (var profile in embeddedProfiles) project.CustomProfiles.Add(profile);
        project.MarkClean();
        return project;
    }

    private static string WriteProject(ProjectModel project)
    {
        var path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N") + ".jbs2bg");
        File.WriteAllText(path, new ProjectFileService().SaveToString(project));
        return path;
    }

    private static string WriteRawProjectWithBundledEmbeddedProfile()
    {
        var path = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N") + ".jbs2bg");
        File.WriteAllText(path, """
        {
          "SliderPresets": {
            "Imported": {
              "isUUNP": false,
              "Profile": "Skyrim CBBE",
              "SetSliders": []
            }
          },
          "CustomProfiles": [
            {
              "Version": 1,
              "Name": "Skyrim CBBE",
              "Game": "Skyrim",
              "Defaults": { "Probe": { "valueSmall": 99, "valueBig": 99 } },
              "Multipliers": {},
              "Inverted": []
            }
          ]
        }
        """);
        return path;
    }

    private static CustomProfileDefinition CreateProfile(string name, float probeDefault, ProfileSourceKind sourceKind) => new(
        name,
        "Skyrim",
        new SliderProfile([new SliderDefault("Probe", probeDefault / 100f, probeDefault / 100f)], [], []),
        sourceKind,
        sourceKind == ProfileSourceKind.LocalCustom ? Path.Combine("C:/profiles", name + ".json") : null);

    private sealed record ViewModelServices(
        MainWindowViewModel ViewModel,
        StubTemplateProfileCatalogService CatalogService,
        FakeAppDialogService Dialog);

    private sealed class StubTemplateProfileCatalogService : ITemplateProfileCatalogService
    {
        private IReadOnlyList<CustomProfileDefinition> projectProfiles = [];
        private List<CustomProfileDefinition> localProfiles;

        public StubTemplateProfileCatalogService(IReadOnlyList<CustomProfileDefinition> localProfiles)
        {
            this.localProfiles = localProfiles.Select(profile => profile.Clone()).ToList();
            Current = BuildCatalog(this.localProfiles, projectProfiles);
        }

        public bool FailSaves { get; set; }

        public TemplateProfileCatalog Current { get; private set; }

        public IReadOnlyList<ProfileValidationDiagnostic> LastDiscoveryDiagnostics => [];

        public IReadOnlyList<CustomProfileDefinition> LocalCustomProfiles => localProfiles;

        public IReadOnlyList<CustomProfileDefinition> ProjectProfiles => projectProfiles;

        public List<CustomProfileDefinition> SavedProfiles { get; } = [];

        public IObservable<TemplateProfileCatalog> CatalogChanged => System.Reactive.Linq.Observable.Return(Current);

        public TemplateProfileCatalog Refresh()
        {
            Current = BuildCatalog(localProfiles, projectProfiles);
            return Current;
        }

        public TemplateProfileCatalog ClearProjectProfiles()
        {
            projectProfiles = [];
            Current = BuildCatalog(localProfiles, projectProfiles);
            return Current;
        }

        public TemplateProfileCatalog WithProjectProfiles(IEnumerable<CustomProfileDefinition> projectProfiles)
        {
            this.projectProfiles = projectProfiles.Select(profile => profile.Clone()).ToList();
            Current = BuildCatalog(localProfiles, this.projectProfiles);
            return Current;
        }

        public UserProfileSaveResult SaveLocalProfile(CustomProfileDefinition profile)
        {
            if (FailSaves)
            {
                return new UserProfileSaveResult(false, null, [new ProfileValidationDiagnostic(ProfileValidationSeverity.Blocker, "ProfileSaveFailed", "Could not save custom profile", null, profile.Name)]);
            }

            var localProfile = new CustomProfileDefinition(profile.Name, profile.Game, profile.SliderProfile, ProfileSourceKind.LocalCustom, Path.Combine("C:/profiles", profile.Name + ".json"));
            SavedProfiles.Add(localProfile);
            localProfiles = localProfiles.Where(existing => !string.Equals(existing.Name, profile.Name, StringComparison.OrdinalIgnoreCase)).Append(localProfile).ToList();
            return new UserProfileSaveResult(true, localProfile.FilePath, []);
        }

        private static TemplateProfileCatalog BuildCatalog(
            IReadOnlyList<CustomProfileDefinition> localProfiles,
            IReadOnlyList<CustomProfileDefinition> projectProfiles)
        {
            var bundled = new ProfileCatalogEntry(
                ProjectProfileMapping.SkyrimCbbe,
                new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, new SliderProfile([new SliderDefault("Probe", 0f, 0f)], [], [])),
                ProfileSourceKind.Bundled,
                null,
                false);
            var projectNames = projectProfiles.Select(profile => profile.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
            var localEntries = localProfiles
                .Where(profile => !projectNames.Contains(profile.Name))
                .Select(profile => new ProfileCatalogEntry(profile.Name, new TemplateProfile(profile.Name, profile.SliderProfile), ProfileSourceKind.LocalCustom, profile.FilePath, true));
            var projectEntries = projectProfiles.Select(profile => new ProfileCatalogEntry(profile.Name, new TemplateProfile(profile.Name, profile.SliderProfile), ProfileSourceKind.EmbeddedProject, profile.FilePath, false));
            return new TemplateProfileCatalog(new[] { bundled }.Concat(localEntries).Concat(projectEntries));
        }
    }

    private sealed class FakeAppDialogService(IEnumerable<ProfileConflictDecision?> conflictDecisions) : IAppDialogService
    {
        private readonly Queue<ProfileConflictDecision?> conflictDecisions = new(conflictDecisions);

        public List<ProfileConflictRequest> ProfileConflictRequests { get; } = [];

        public void Enqueue(ProfileConflictDecision? decision) => conflictDecisions.Enqueue(decision);

        public Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmBulkOperationAsync(
            string title,
            string message,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmExportOverwriteAsync(ExportPreviewResult preview, CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<ProfileConflictDecision?> PromptProfileConflictAsync(
            ProfileConflictRequest request,
            CancellationToken cancellationToken)
        {
            ProfileConflictRequests.Add(request);
            return Task.FromResult(conflictDecisions.Count == 0 ? null : conflictDecisions.Dequeue());
        }

        public void ShowAbout()
        {
        }
    }

    private sealed class EmptyFileDialogService : IFileDialogService
    {
        public Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken) => Task.FromResult<string?>(null);

        public Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken) => Task.FromResult<string?>(null);

        public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken) => Task.FromResult<string?>(null);

        public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken) => Task.FromResult<string?>(null);

        public Task<string?> PickSaveBundleFileAsync(CancellationToken cancellationToken) => Task.FromResult<string?>(null);
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>([]);
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
