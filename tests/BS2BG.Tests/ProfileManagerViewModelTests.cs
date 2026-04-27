using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using System.Windows.Input;
using Xunit;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.Tests;

public sealed class ProfileManagerViewModelTests
{
    /// <summary>
    /// Verifies bundled catalog rows stay read-only while still allowing copy-to-custom authoring.
    /// </summary>
    [Fact]
    public void BundledProfileAllowsCopyButNotEditDeleteOrSave()
    {
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }));

        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body");

        ((ICommand)vm.CopyBundledProfileCommand).CanExecute(null).Should().BeTrue();
        ((ICommand)vm.DeleteCustomProfileCommand).CanExecute(null).Should().BeFalse();
        ((ICommand)vm.SaveProfileCommand).CanExecute(null).Should().BeFalse();
        vm.SelectedProfile.SourceLabel.Should().Be("Bundled — read-only");
    }

    /// <summary>
    /// Verifies selection changes honor unsaved-editor discard confirmation and retain the prior editor when declined.
    /// </summary>
    [Fact]
    public async Task SelectionWithUnsavedEditsPromptsBeforeDiscardingPriorEditor()
    {
        var dialog = new StubProfileManagementDialogService { ConfirmDiscardUnsavedEditsResult = false };
        var catalog = new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        });
        var vm = CreateManager(catalog, dialog: dialog);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");
        vm.Editor.Name = "Unsaved Custom";
        var priorEditor = vm.Editor;

        var changed = await vm.TrySelectProfileAsync(vm.ProfileEntries.Single(entry => entry.Name == "Bundled Body"));

        changed.Should().BeFalse();
        dialog.ConfirmDiscardUnsavedEditsCalls.Should().Be(1);
        vm.SelectedProfile!.Name.Should().Be("Custom Body");
        vm.Editor.Should().BeSameAs(priorEditor);
    }

    /// <summary>
    /// Verifies referenced custom-profile deletes require explicit confirmation and keep project references unresolved.
    /// </summary>
    [Fact]
    public async Task DeleteReferencedCustomProfileRequiresConfirmationAndLeavesProjectReferencesUnresolved()
    {
        var dialog = new StubProfileManagementDialogService
        {
            ConfirmDeleteProfileResult = true,
            ConfirmDeleteReferencedProfileResult = true
        };
        var store = new StubUserProfileStore();
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Custom Body" });
        var catalogService = new StubTemplateProfileCatalogService(new TemplateProfileCatalog(new[]
        {
            new ProfileCatalogEntry("Bundled Body", new TemplateProfile("Bundled Body", CreateSliderProfile()), ProfileSourceKind.Bundled, null, false),
            new ProfileCatalogEntry("Custom Body", new TemplateProfile("Custom Body", CreateSliderProfile()), ProfileSourceKind.LocalCustom, "custom.json", true)
        }));
        var vm = CreateManager(catalogService.Current, project, store, dialog, catalogService);
        vm.SelectedProfile = vm.ProfileEntries.Single(entry => entry.Name == "Custom Body");

        await vm.DeleteSelectedCustomProfileAsync();

        dialog.ConfirmDeleteReferencedProfileCalls.Should().Be(1);
        dialog.LastAffectedPresetCount.Should().Be(1);
        store.DeletedProfiles.Should().ContainSingle(profile => profile.Name == "Custom Body");
        catalogService.RefreshCalls.Should().Be(1);
        project.SliderPresets[0].ProfileName.Should().Be("Custom Body");
    }

    /// <summary>
    /// Verifies unresolved project profile names get a neutral missing-source label in the manager rail.
    /// </summary>
    [Fact]
    public void MissingProjectProfileRowsUseFallbackSourceLabel()
    {
        var project = new ProjectModel();
        project.SliderPresets.Add(new SliderPreset("Preset") { ProfileName = "Missing Body" });
        var vm = CreateManager(new TemplateProfileCatalog(new[]
        {
            new TemplateProfile("Bundled Body", CreateSliderProfile())
        }), project);

        vm.ProfileEntries.Single(entry => entry.Name == "Missing Body").SourceLabel.Should().Be("Missing — using fallback");
    }

    private static ProfileManagerViewModel CreateManager(
        TemplateProfileCatalog catalog,
        ProjectModel? project = null,
        IUserProfileStore? store = null,
        IProfileManagementDialogService? dialog = null,
        ITemplateProfileCatalogService? catalogService = null) =>
        new(
            project ?? new ProjectModel(),
            catalogService ?? new StubTemplateProfileCatalogService(catalog),
            store ?? new StubUserProfileStore(),
            new ProfileDefinitionService(),
            dialog ?? new StubProfileManagementDialogService());

    private static SliderProfile CreateSliderProfile() => new(
        [new SliderDefault("Slider", 0f, 1f)],
        [new SliderMultiplier("Slider", 1f)],
        []);

    private sealed class StubProfileManagementDialogService : IProfileManagementDialogService
    {
        public bool ConfirmDeleteProfileResult { get; set; } = true;

        public bool ConfirmDeleteReferencedProfileResult { get; set; } = true;

        public bool ConfirmDiscardUnsavedEditsResult { get; set; } = true;

        public int ConfirmDeleteReferencedProfileCalls { get; private set; }

        public int ConfirmDiscardUnsavedEditsCalls { get; private set; }

        public int LastAffectedPresetCount { get; private set; }

        public Task<IReadOnlyList<string>> PickProfileImportFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>([]);

        public Task<string?> PickProfileExportPathAsync(string suggestedFileName, CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<bool> ConfirmDeleteProfileAsync(string profileName, CancellationToken cancellationToken) =>
            Task.FromResult(ConfirmDeleteProfileResult);

        public Task<bool> ConfirmDeleteReferencedProfileAsync(string profileName, int affectedPresetCount, CancellationToken cancellationToken)
        {
            ConfirmDeleteReferencedProfileCalls++;
            LastAffectedPresetCount = affectedPresetCount;
            return Task.FromResult(ConfirmDeleteReferencedProfileResult);
        }

        public Task<bool> ConfirmDiscardUnsavedEditsAsync(CancellationToken cancellationToken)
        {
            ConfirmDiscardUnsavedEditsCalls++;
            return Task.FromResult(ConfirmDiscardUnsavedEditsResult);
        }
    }

    private sealed class StubUserProfileStore : IUserProfileStore
    {
        public List<CustomProfileDefinition> DeletedProfiles { get; } = [];

        public UserProfileDiscoveryResult DiscoverProfiles() => DiscoverProfiles([]);

        public UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames) =>
            new([], []);

        public UserProfileSaveResult SaveProfile(CustomProfileDefinition profile) =>
            new(true, profile.FilePath, []);

        public UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile)
        {
            DeletedProfiles.Add(profile);
            return new(true, profile.FilePath, []);
        }

        public string GetDefaultProfileDirectory() => string.Empty;
    }

    private sealed class StubTemplateProfileCatalogService(TemplateProfileCatalog catalog) : ITemplateProfileCatalogService
    {
        private readonly System.Reactive.Subjects.BehaviorSubject<TemplateProfileCatalog> changed = new(catalog);

        public TemplateProfileCatalog Current { get; private set; } = catalog;

        public IReadOnlyList<ProfileValidationDiagnostic> LastDiscoveryDiagnostics => [];

        public IObservable<TemplateProfileCatalog> CatalogChanged => changed;

        public int RefreshCalls { get; private set; }

        public TemplateProfileCatalog Refresh()
        {
            RefreshCalls++;
            changed.OnNext(Current);
            return Current;
        }

        public TemplateProfileCatalog ClearProjectProfiles() => Current;

        public TemplateProfileCatalog WithProjectProfiles(IEnumerable<CustomProfileDefinition> projectProfiles) => Current;
    }
}
