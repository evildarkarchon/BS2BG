using System.Collections.ObjectModel;
using System.Reactive;
using System.Reactive.Disposables;
using System.Reactive.Linq;
using BS2BG.App.Services;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using ReactiveUI;
using ReactiveUI.SourceGenerators;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.App.ViewModels;

/// <summary>
/// Coordinates the single-shell profile-management workspace over the runtime catalog and local profile store.
/// The ViewModel intentionally owns one editor instance at a time because the current App shell exposes one profile manager tab per workspace session.
/// </summary>
public sealed partial class ProfileManagerViewModel : ReactiveObject, IDisposable
{
    private readonly ITemplateProfileCatalogService catalogService;
    private readonly IProfileManagementDialogService dialogService;
    private readonly CompositeDisposable disposables = new();
    private readonly ProfileDefinitionService profileDefinitionService;
    private readonly ProjectModel project;
    private readonly IUserProfileStore store;
    private bool selectingInternally;

    [ObservableAsProperty] private bool _isBusy;
    [Reactive(SetModifier = AccessModifier.Private)] private ProfileEditorViewModel _editor;
    [Reactive] private ProfileManagerEntryViewModel? _selectedProfile;
    [Reactive(SetModifier = AccessModifier.Private)] private string _statusMessage = string.Empty;

    public ProfileManagerViewModel()
        : this(
            new ProjectModel(),
            new TemplateProfileCatalogService(new TemplateProfileCatalog(new[]
            {
                new TemplateProfile(ProjectProfileMapping.SkyrimCbbe, new SliderProfile([], [], []))
            })),
            new EmptyUserProfileStore(),
            new ProfileDefinitionService(),
            new NullProfileManagementDialogService())
    {
    }

    /// <summary>
    /// Creates a profile manager for the active project and refresh-aware runtime profile catalog.
    /// </summary>
    public ProfileManagerViewModel(
        ProjectModel project,
        ITemplateProfileCatalogService catalogService,
        IUserProfileStore store,
        ProfileDefinitionService profileDefinitionService,
        IProfileManagementDialogService dialogService)
    {
        this.project = project ?? throw new ArgumentNullException(nameof(project));
        this.catalogService = catalogService ?? throw new ArgumentNullException(nameof(catalogService));
        this.store = store ?? throw new ArgumentNullException(nameof(store));
        this.profileDefinitionService = profileDefinitionService ?? throw new ArgumentNullException(nameof(profileDefinitionService));
        this.dialogService = dialogService ?? throw new ArgumentNullException(nameof(dialogService));
        _editor = ProfileEditorViewModel.Empty(profileDefinitionService, catalogService.Current.ProfileNames);

        var hasSelection = this.WhenAnyValue(x => x.SelectedProfile).Select(entry => entry is not null);
        var selectedIsBundled = this.WhenAnyValue(x => x.SelectedProfile)
            .Select(entry => entry?.SourceKind == ProfileSourceKind.Bundled);
        var selectedIsCustom = this.WhenAnyValue(x => x.SelectedProfile)
            .Select(entry => entry?.SourceKind == ProfileSourceKind.LocalCustom);

        ImportProfileCommand = ReactiveCommand.CreateFromTask(ImportProfilesAsync);
        CreateBlankProfileCommand = ReactiveCommand.Create(CreateBlankProfile);
        CopyBundledProfileCommand = ReactiveCommand.Create(CopyBundledProfile, selectedIsBundled);
        ValidateProfileCommand = ReactiveCommand.Create(() => Editor.ValidateProfile(), hasSelection);
        SaveProfileCommand = ReactiveCommand.CreateFromTask(SaveSelectedProfileAsync, selectedIsCustom);
        ExportProfileCommand = ReactiveCommand.CreateFromTask(ExportSelectedProfileAsync, selectedIsCustom);
        DeleteCustomProfileCommand = ReactiveCommand.CreateFromTask(DeleteSelectedCustomProfileAsync, selectedIsCustom);

        _isBusyHelper = Observable.CombineLatest(
                ImportProfileCommand.IsExecuting,
                SaveProfileCommand.IsExecuting,
                ExportProfileCommand.IsExecuting,
                DeleteCustomProfileCommand.IsExecuting)
            .Select(values => values.Any(value => value))
            .ToProperty(this, x => x.IsBusy, initialValue: false);

        disposables.Add(catalogService.CatalogChanged.Skip(1).Subscribe(_ => RefreshProfileEntries()));
        disposables.Add(this.WhenAnyValue(x => x.SelectedProfile).Skip(1).Subscribe(entry =>
        {
            if (selectingInternally) return;

            _ = TrySelectProfileAsync(entry);
        }));

        RefreshProfileEntries();
    }

    public ObservableCollection<ProfileManagerEntryViewModel> ProfileEntries { get; } = [];

    public ReactiveCommand<Unit, Unit> ImportProfileCommand { get; }

    public ReactiveCommand<Unit, Unit> CreateBlankProfileCommand { get; }

    public ReactiveCommand<Unit, Unit> CopyBundledProfileCommand { get; }

    public ReactiveCommand<Unit, Unit> ValidateProfileCommand { get; }

    public ReactiveCommand<Unit, Unit> SaveProfileCommand { get; }

    public ReactiveCommand<Unit, Unit> ExportProfileCommand { get; }

    public ReactiveCommand<Unit, Unit> DeleteCustomProfileCommand { get; }

    public void Dispose() => disposables.Dispose();

    /// <summary>
    /// Attempts to change selection, prompting before unsaved editor state is discarded.
    /// </summary>
    public async Task<bool> TrySelectProfileAsync(ProfileManagerEntryViewModel? entry, CancellationToken cancellationToken = default)
    {
        if (ReferenceEquals(SelectedProfile, entry) && Editor.Matches(entry)) return true;
        if (Editor.HasUnsavedChanges && !await dialogService.ConfirmDiscardUnsavedEditsAsync(cancellationToken))
        {
            selectingInternally = true;
            try
            {
                this.RaisePropertyChanged(nameof(SelectedProfile));
            }
            finally
            {
                selectingInternally = false;
            }

            return false;
        }

        selectingInternally = true;
        try
        {
            SelectedProfile = entry;
        }
        finally
        {
            selectingInternally = false;
        }

        Editor = entry is null
            ? ProfileEditorViewModel.Empty(profileDefinitionService, catalogService.Current.ProfileNames)
            : ProfileEditorViewModel.FromEntry(entry, profileDefinitionService, ExistingNamesFor(entry.Name));
        return true;
    }

    /// <summary>
    /// Deletes the selected local custom profile after required destructive confirmations.
    /// </summary>
    public async Task DeleteSelectedCustomProfileAsync(CancellationToken cancellationToken = default)
    {
        var entry = SelectedProfile;
        if (entry?.SourceKind != ProfileSourceKind.LocalCustom) return;

        var affectedPresetCount = project.SliderPresets.Count(preset => ProfileNamesEqual(preset.ProfileName, entry.Name));
        var confirmed = affectedPresetCount > 0
            ? await dialogService.ConfirmDeleteReferencedProfileAsync(entry.Name, affectedPresetCount, cancellationToken)
            : await dialogService.ConfirmDeleteProfileAsync(entry.Name, cancellationToken);
        if (!confirmed) return;

        var result = store.DeleteProfile(entry.ToCustomProfileDefinition());
        if (!result.Succeeded)
        {
            StatusMessage = string.Join(" ", result.Diagnostics.Select(diagnostic => diagnostic.Message));
            return;
        }

        catalogService.Refresh();
        StatusMessage = "Custom profile deleted.";
    }

    private async Task ImportProfilesAsync(CancellationToken cancellationToken)
    {
        var files = await dialogService.PickProfileImportFilesAsync(cancellationToken);
        foreach (var file in files)
        {
            var json = await File.ReadAllTextAsync(file, cancellationToken);
            var validation = profileDefinitionService.ValidateProfileJson(
                json,
                ProfileValidationContext.ForImport(catalogService.Current.ProfileNames, ProfileSourceKind.LocalCustom, file));
            if (!validation.IsValid || validation.Profile is null)
            {
                StatusMessage = validation.Diagnostics.FirstOrDefault()?.Message ?? "Profile JSON is malformed. Fix the JSON and import again.";
                continue;
            }

            var saved = store.SaveProfile(validation.Profile);
            StatusMessage = saved.Succeeded ? "Profile imported." : FormatDiagnostics(saved.Diagnostics);
        }

        if (files.Count > 0) catalogService.Refresh();
    }

    private void CreateBlankProfile()
    {
        SelectedProfile = null;
        Editor = ProfileEditorViewModel.Blank(profileDefinitionService, catalogService.Current.ProfileNames);
    }

    private void CopyBundledProfile()
    {
        if (SelectedProfile is null) return;

        SelectedProfile = null;
        Editor = ProfileEditorViewModel.FromProfile(
            SelectedProfileNameForCopy(),
            string.Empty,
            SelectedProfileSliderProfileForCopy(),
            ProfileSourceKind.LocalCustom,
            null,
            profileDefinitionService,
            catalogService.Current.ProfileNames);
        Editor.Name = string.Empty;
    }

    private async Task SaveSelectedProfileAsync(CancellationToken cancellationToken)
    {
        var profile = Editor.BuildProfile(ProfileSourceKind.LocalCustom, SelectedProfile?.FilePath);
        if (!Editor.IsValid || profile is null) return;

        var result = store.SaveProfile(profile);
        StatusMessage = result.Succeeded
            ? "Profile saved."
            : "Profile could not be saved. Review the validation messages below; malformed or ambiguous profile data is not added to the catalog.";
        if (result.Succeeded)
        {
            Editor.AcceptSaved();
            catalogService.Refresh();
        }

        await Task.CompletedTask;
    }

    private async Task ExportSelectedProfileAsync(CancellationToken cancellationToken)
    {
        var entry = SelectedProfile;
        if (entry is null) return;

        var path = await dialogService.PickProfileExportPathAsync(entry.Name + ".json", cancellationToken);
        if (string.IsNullOrWhiteSpace(path)) return;

        await File.WriteAllTextAsync(path, profileDefinitionService.ExportProfileJson(entry.ToCustomProfileDefinition()), cancellationToken);
        StatusMessage = "Profile JSON exported.";
    }

    private void RefreshProfileEntries()
    {
        var selectedName = SelectedProfile?.Name;
        ProfileEntries.Clear();
        foreach (var entry in catalogService.Current.Entries)
            ProfileEntries.Add(ProfileManagerEntryViewModel.FromCatalogEntry(entry));

        foreach (var missing in MissingProjectProfileNames())
            ProfileEntries.Add(ProfileManagerEntryViewModel.Missing(missing));

        SelectedProfile = ProfileEntries.FirstOrDefault(entry => ProfileNamesEqual(entry.Name, selectedName))
                          ?? ProfileEntries.FirstOrDefault();
        if (SelectedProfile is not null)
            Editor = ProfileEditorViewModel.FromEntry(SelectedProfile, profileDefinitionService, ExistingNamesFor(SelectedProfile.Name));
    }

    private IEnumerable<string> MissingProjectProfileNames() => project.SliderPresets
        .Select(preset => preset.ProfileName)
        .Where(name => !string.IsNullOrWhiteSpace(name) && !catalogService.Current.ContainsProfile(name))
        .Distinct(StringComparer.OrdinalIgnoreCase);

    private IEnumerable<string> ExistingNamesFor(string? currentName) => catalogService.Current.ProfileNames
        .Where(name => !ProfileNamesEqual(name, currentName));

    private string SelectedProfileNameForCopy() => SelectedProfile?.Name ?? string.Empty;

    private SliderProfile SelectedProfileSliderProfileForCopy() => SelectedProfile?.SliderProfile ?? new SliderProfile([], [], []);

    private static bool ProfileNamesEqual(string? left, string? right) =>
        string.Equals(left, right, StringComparison.OrdinalIgnoreCase);

    private static string FormatDiagnostics(IEnumerable<ProfileValidationDiagnostic> diagnostics) =>
        string.Join(" ", diagnostics.Select(diagnostic => diagnostic.Message));

    private sealed class EmptyUserProfileStore : IUserProfileStore
    {
        public UserProfileDiscoveryResult DiscoverProfiles() => DiscoverProfiles([]);
        public UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames) => new([], []);
        public UserProfileSaveResult SaveProfile(CustomProfileDefinition profile) => new(false, null, []);
        public UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile) => new(false, null, []);
        public string GetDefaultProfileDirectory() => string.Empty;
    }

    private sealed class NullProfileManagementDialogService : IProfileManagementDialogService
    {
        public Task<IReadOnlyList<string>> PickProfileImportFilesAsync(CancellationToken cancellationToken) => Task.FromResult<IReadOnlyList<string>>([]);
        public Task<string?> PickProfileExportPathAsync(string suggestedFileName, CancellationToken cancellationToken) => Task.FromResult<string?>(null);
        public Task<bool> ConfirmDeleteProfileAsync(string profileName, CancellationToken cancellationToken) => Task.FromResult(true);
        public Task<bool> ConfirmDeleteReferencedProfileAsync(string profileName, int affectedPresetCount, CancellationToken cancellationToken) => Task.FromResult(true);
        public Task<bool> ConfirmDiscardUnsavedEditsAsync(CancellationToken cancellationToken) => Task.FromResult(true);
    }
}

/// <summary>
/// Source-aware row projected by the profile manager list.
/// </summary>
public sealed class ProfileManagerEntryViewModel
{
    private ProfileManagerEntryViewModel(
        string name,
        SliderProfile sliderProfile,
        ProfileSourceKind sourceKind,
        string? filePath,
        bool isEditable)
    {
        Name = name;
        SliderProfile = sliderProfile;
        SourceKind = sourceKind;
        FilePath = filePath;
        IsEditable = isEditable;
    }

    public string Name { get; }
    public SliderProfile SliderProfile { get; }
    public ProfileSourceKind SourceKind { get; }
    public string? FilePath { get; }
    public bool IsEditable { get; }
    public string SourceLabel => SourceKind switch
    {
        ProfileSourceKind.Bundled => "Bundled — read-only",
        ProfileSourceKind.LocalCustom => "Custom — editable",
        ProfileSourceKind.EmbeddedProject => "Embedded in project",
        _ => "Missing — using fallback"
    };

    public static ProfileManagerEntryViewModel FromCatalogEntry(ProfileCatalogEntry entry) =>
        new(entry.Name, entry.TemplateProfile.SliderProfile, entry.SourceKind, entry.FilePath, entry.IsEditable);

    public static ProfileManagerEntryViewModel Missing(string name) =>
        new(name, new SliderProfile([], [], []), ProfileSourceKind.EmbeddedProject, null, false);

    public CustomProfileDefinition ToCustomProfileDefinition() =>
        new(Name, string.Empty, SliderProfile, SourceKind, FilePath);
}

/// <summary>
/// Minimal profile editor shell used by the manager until the full validation-gated editor is expanded by the next task.
/// </summary>
public sealed partial class ProfileEditorViewModel : ReactiveObject
{
    private readonly ProfileDefinitionService profileDefinitionService;
    private readonly IReadOnlyList<string> existingNames;
    private readonly string originalName;
    private readonly SliderProfile sliderProfile;

    [Reactive] private string _name;
    [Reactive(SetModifier = AccessModifier.Private)] private bool _isValid = true;

    private ProfileEditorViewModel(
        string name,
        SliderProfile sliderProfile,
        ProfileDefinitionService profileDefinitionService,
        IEnumerable<string> existingNames)
    {
        _name = name;
        originalName = name;
        this.sliderProfile = sliderProfile;
        this.profileDefinitionService = profileDefinitionService;
        this.existingNames = existingNames.ToArray();
    }

    public bool HasUnsavedChanges => !string.Equals(Name, originalName, StringComparison.Ordinal);

    public static ProfileEditorViewModel Empty(ProfileDefinitionService service, IEnumerable<string> existingNames) =>
        new(string.Empty, new SliderProfile([], [], []), service, existingNames);

    public static ProfileEditorViewModel Blank(ProfileDefinitionService service, IEnumerable<string> existingNames) =>
        new(string.Empty, new SliderProfile([], [], []), service, existingNames);

    public static ProfileEditorViewModel FromEntry(ProfileManagerEntryViewModel entry, ProfileDefinitionService service, IEnumerable<string> existingNames) =>
        FromProfile(entry.Name, string.Empty, entry.SliderProfile, entry.SourceKind, entry.FilePath, service, existingNames);

    public static ProfileEditorViewModel FromProfile(
        string name,
        string game,
        SliderProfile sliderProfile,
        ProfileSourceKind sourceKind,
        string? filePath,
        ProfileDefinitionService service,
        IEnumerable<string> existingNames) =>
        new(name, sliderProfile, service, existingNames);

    public bool Matches(ProfileManagerEntryViewModel? entry) =>
        entry is not null && string.Equals(entry.Name, originalName, StringComparison.OrdinalIgnoreCase);

    public void ValidateProfile() => IsValid = BuildProfile(ProfileSourceKind.LocalCustom, null) is not null;

    public CustomProfileDefinition? BuildProfile(ProfileSourceKind sourceKind, string? filePath)
    {
        if (string.IsNullOrWhiteSpace(Name)) return null;
        if (existingNames.Contains(Name, StringComparer.OrdinalIgnoreCase)) return null;

        _ = profileDefinitionService;
        return new CustomProfileDefinition(Name, string.Empty, sliderProfile, sourceKind, filePath);
    }

    public void AcceptSaved()
    {
    }
}
