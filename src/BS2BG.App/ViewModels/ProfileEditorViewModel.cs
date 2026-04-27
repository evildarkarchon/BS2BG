using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.Globalization;
using System.Reactive;
using System.Reactive.Disposables;
using System.Reactive.Linq;
using BS2BG.App.Services;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using ReactiveUI;
using ReactiveUI.SourceGenerators;

namespace BS2BG.App.ViewModels;

/// <summary>
/// Edits custom profile metadata and slider tables as an in-memory candidate that is validated before local-store writes.
/// Validation intentionally operates on row state rather than serializing JSON on each edit so large table edits stay responsive.
/// </summary>
public sealed partial class ProfileEditorViewModel : ReactiveObject, IDisposable
{
    public const string BlankProfileInfo = "Blank profiles are allowed. Add defaults, multipliers, and inverted sliders when you are ready.";
    public const string SaveFailureMessage = "Profile could not be saved. Review the validation messages below; malformed or ambiguous profile data is not added to the catalog.";
    private readonly IReadOnlyList<string> existingNames;
    private readonly CompositeDisposable disposables = new();
    private readonly string? filePath;
    private readonly ProfileDefinitionService profileDefinitionService;
    private readonly ProfileSourceKind sourceKind;
    private readonly IUserProfileStore store;
    private string savedFingerprint;

    [Reactive] private string _game;
    [Reactive(SetModifier = AccessModifier.Private)] private bool _isValid = true;
    [Reactive] private string _name;
    [Reactive] private string _searchText = string.Empty;

    private ProfileEditorViewModel(
        string name,
        string game,
        SliderProfile sliderProfile,
        ProfileSourceKind sourceKind,
        string? filePath,
        ProfileDefinitionService profileDefinitionService,
        IEnumerable<string> existingNames,
        IUserProfileStore store)
    {
        _name = name;
        _game = game;
        this.sourceKind = sourceKind;
        this.filePath = filePath;
        this.profileDefinitionService = profileDefinitionService ?? throw new ArgumentNullException(nameof(profileDefinitionService));
        this.existingNames = (existingNames ?? throw new ArgumentNullException(nameof(existingNames))).ToArray();
        this.store = store ?? throw new ArgumentNullException(nameof(store));

        foreach (var row in sliderProfile.Defaults)
            DefaultRows.Add(new ProfileDefaultRowViewModel(row.Name, FormatFloat(row.ValueSmall), FormatFloat(row.ValueBig)));
        foreach (var row in sliderProfile.Multipliers)
            MultiplierRows.Add(new ProfileMultiplierRowViewModel(row.Name, FormatFloat(row.Value)));
        foreach (var slider in sliderProfile.InvertedNames)
            InvertedRows.Add(new ProfileInvertedRowViewModel(slider, true));

        ValidateProfileCommand = ReactiveCommand.Create(ValidateProfile);
        SaveProfileCommand = ReactiveCommand.CreateFromTask(SaveProfileAsync, this.WhenAnyValue(x => x.IsValid));
        savedFingerprint = CreateFingerprint();

        DefaultRows.CollectionChanged += OnRowsChanged;
        MultiplierRows.CollectionChanged += OnRowsChanged;
        InvertedRows.CollectionChanged += OnRowsChanged;
        disposables.Add(this.WhenAnyValue(x => x.SearchText).Subscribe(_ => RefreshVisibleRows()));
        disposables.Add(this.WhenAnyValue(x => x.Name, x => x.Game).Skip(1).Subscribe(_ => ValidateProfile()));
        RefreshVisibleRows();
        ValidateProfile();
    }

    public ObservableCollection<ProfileDefaultRowViewModel> DefaultRows { get; } = [];
    public ObservableCollection<ProfileMultiplierRowViewModel> MultiplierRows { get; } = [];
    public ObservableCollection<ProfileInvertedRowViewModel> InvertedRows { get; } = [];
    public ObservableCollection<ProfileDefaultRowViewModel> VisibleDefaultRows { get; } = [];
    public ObservableCollection<ProfileEditorStatusRowViewModel> ValidationRows { get; } = [];
    public ObservableCollection<ProfileEditorStatusRowViewModel> StatusRows { get; } = [];
    public ReactiveCommand<Unit, Unit> ValidateProfileCommand { get; }
    public ReactiveCommand<Unit, Unit> SaveProfileCommand { get; }
    public bool HasUnsavedChanges => !string.Equals(CreateFingerprint(), savedFingerprint, StringComparison.Ordinal);

    /// <summary>
    /// Creates an empty editor used when no profile row is selected.
    /// </summary>
    public static ProfileEditorViewModel Empty(ProfileDefinitionService service, IEnumerable<string> existingNames) =>
        Blank(service, existingNames, new EmptyUserProfileStore());

    /// <summary>
    /// Creates a blank custom profile candidate with empty slider tables.
    /// </summary>
    public static ProfileEditorViewModel Blank(
        ProfileDefinitionService service,
        IEnumerable<string> existingNames,
        IUserProfileStore? store = null) =>
        new(string.Empty, string.Empty, new SliderProfile([], [], []), ProfileSourceKind.LocalCustom, null, service, existingNames, store ?? new EmptyUserProfileStore());

    /// <summary>
    /// Creates an editor from a manager row while preserving the row's source metadata for save/export workflows.
    /// </summary>
    public static ProfileEditorViewModel FromEntry(ProfileManagerEntryViewModel entry, ProfileDefinitionService service, IEnumerable<string> existingNames) =>
        FromProfile(entry.Name, entry.Game, entry.SliderProfile, entry.SourceKind, entry.FilePath, service, existingNames);

    /// <summary>
    /// Creates an editor from profile metadata and generation tables.
    /// </summary>
    public static ProfileEditorViewModel FromProfile(
        string name,
        string game,
        SliderProfile sliderProfile,
        ProfileSourceKind sourceKind,
        string? filePath,
        ProfileDefinitionService service,
        IEnumerable<string> existingNames,
        IUserProfileStore? store = null) =>
        new(name, game, sliderProfile, sourceKind, filePath, service, existingNames, store ?? new EmptyUserProfileStore());

    /// <summary>
    /// Returns whether this editor still represents the supplied profile row's saved identity.
    /// </summary>
    public bool Matches(ProfileManagerEntryViewModel? entry) =>
        entry is not null && string.Equals(entry.Name, Name, StringComparison.OrdinalIgnoreCase);

    /// <summary>
    /// Rebuilds validation rows from the live editor buffer and updates save command gating.
    /// </summary>
    public void ValidateProfile()
    {
        ValidationRows.Clear();
        var diagnostics = ValidateCandidate(out _);
        foreach (var diagnostic in diagnostics) ValidationRows.Add(new ProfileEditorStatusRowViewModel(diagnostic.Severity, diagnostic.Message));
        IsValid = diagnostics.All(diagnostic => diagnostic.Severity != ProfileValidationSeverity.Blocker);
        if (IsValid && DefaultRows.Count == 0 && MultiplierRows.Count == 0 && InvertedRows.Count == 0)
            ValidationRows.Add(new ProfileEditorStatusRowViewModel(ProfileValidationSeverity.Info, BlankProfileInfo));
        else if (IsValid)
            ValidationRows.Add(new ProfileEditorStatusRowViewModel(ProfileValidationSeverity.Info, "Profile is valid and ready for catalog use."));
    }

    /// <summary>
    /// Builds a custom profile from the current valid buffer for manager-level save orchestration.
    /// </summary>
    public CustomProfileDefinition? BuildProfile(ProfileSourceKind sourceKind, string? filePath)
    {
        var diagnostics = ValidateCandidate(out var profile, sourceKind, filePath);
        return diagnostics.Any(diagnostic => diagnostic.Severity == ProfileValidationSeverity.Blocker) ? null : profile;
    }

    /// <summary>
    /// Marks the current editor buffer as saved after the backing store has accepted it.
    /// </summary>
    public void AcceptSaved() => savedFingerprint = CreateFingerprint();

    public void Dispose()
    {
        DefaultRows.CollectionChanged -= OnRowsChanged;
        MultiplierRows.CollectionChanged -= OnRowsChanged;
        InvertedRows.CollectionChanged -= OnRowsChanged;
        disposables.Dispose();
    }

    private void OnRowsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        RefreshVisibleRows();
        ValidateProfile();
    }

    private Task SaveProfileAsync(CancellationToken cancellationToken)
    {
        ValidateProfile();
        if (!IsValid) return Task.CompletedTask;

        var profile = BuildProfile(ProfileSourceKind.LocalCustom, filePath);
        if (profile is null) return Task.CompletedTask;

        var result = store.SaveProfile(profile);
        StatusRows.Clear();
        if (!result.Succeeded)
        {
            StatusRows.Add(new ProfileEditorStatusRowViewModel(ProfileValidationSeverity.Blocker, SaveFailureMessage));
            return Task.CompletedTask;
        }

        AcceptSaved();
        StatusRows.Add(new ProfileEditorStatusRowViewModel(ProfileValidationSeverity.Info, "Profile saved."));
        return Task.CompletedTask;
    }

    private IReadOnlyList<ProfileValidationDiagnostic> ValidateCandidate(out CustomProfileDefinition? profile) =>
        ValidateCandidate(out profile, sourceKind, filePath);

    private IReadOnlyList<ProfileValidationDiagnostic> ValidateCandidate(
        out CustomProfileDefinition? profile,
        ProfileSourceKind candidateSourceKind,
        string? candidateFilePath)
    {
        var diagnostics = new List<ProfileValidationDiagnostic>();
        profile = null;
        if (string.IsNullOrWhiteSpace(Name))
            diagnostics.Add(Blocker("BlankProfileName", "Profile Name must not be blank.", null, null));
        if (existingNames.Contains(Name, StringComparer.OrdinalIgnoreCase))
            diagnostics.Add(Blocker("DuplicateProfileName", "Profile name conflicts with an existing bundled or custom profile. Choose a unique display name.", null, Name));

        var defaults = BuildDefaults(diagnostics);
        var multipliers = BuildMultipliers(diagnostics);
        var inverted = BuildInverted(diagnostics);
        if (diagnostics.Any(diagnostic => diagnostic.Severity == ProfileValidationSeverity.Blocker)) return diagnostics;

        profile = new CustomProfileDefinition(Name.Trim(), Game, new SliderProfile(defaults, multipliers, inverted), candidateSourceKind, candidateFilePath);
        _ = profileDefinitionService;
        return diagnostics;
    }

    private IReadOnlyList<SliderDefault> BuildDefaults(List<ProfileValidationDiagnostic> diagnostics)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var values = new List<SliderDefault>();
        foreach (var row in DefaultRows)
        {
            if (!ValidateSliderName(row.Slider, "Defaults", seen, diagnostics)) continue;
            var smallOk = TryReadFloat(row.ValueSmall, "Defaults", row.Slider, diagnostics, out var small);
            var bigOk = TryReadFloat(row.ValueBig, "Defaults", row.Slider, diagnostics, out var big);
            if (smallOk && bigOk) values.Add(new SliderDefault(row.Slider, small, big));
        }

        return values;
    }

    private IReadOnlyList<SliderMultiplier> BuildMultipliers(List<ProfileValidationDiagnostic> diagnostics)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var values = new List<SliderMultiplier>();
        foreach (var row in MultiplierRows)
        {
            if (!ValidateSliderName(row.Slider, "Multipliers", seen, diagnostics)) continue;
            if (TryReadFloat(row.Value, "Multipliers", row.Slider, diagnostics, out var value))
                values.Add(new SliderMultiplier(row.Slider, value));
        }

        return values;
    }

    private IReadOnlyList<string> BuildInverted(List<ProfileValidationDiagnostic> diagnostics)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var values = new List<string>();
        foreach (var row in InvertedRows)
        {
            if (!row.IsInverted) continue;
            if (ValidateSliderName(row.Slider, "Inverted", seen, diagnostics)) values.Add(row.Slider);
        }

        return values;
    }

    private static bool ValidateSliderName(
        string slider,
        string table,
        HashSet<string> seen,
        List<ProfileValidationDiagnostic> diagnostics)
    {
        if (string.IsNullOrWhiteSpace(slider))
        {
            diagnostics.Add(Blocker("BlankSliderName", $"{table} contains duplicate slider {slider}. Each slider name can appear once per table.", table, slider));
            return false;
        }

        if (!seen.Add(slider))
        {
            diagnostics.Add(Blocker("DuplicateSliderName", $"{table} contains duplicate slider {slider}. Each slider name can appear once per table.", table, slider));
            return false;
        }

        return true;
    }

    private static bool TryReadFloat(
        string value,
        string table,
        string slider,
        List<ProfileValidationDiagnostic> diagnostics,
        out float parsed)
    {
        if (!float.TryParse(value, NumberStyles.Float, CultureInfo.InvariantCulture, out parsed) || float.IsNaN(parsed) || float.IsInfinity(parsed))
        {
            diagnostics.Add(Blocker("InvalidNumber", $"{table} value for {slider} must be a number. Broad finite values are allowed; malformed values are not.", table, slider));
            return false;
        }

        if (Math.Abs(parsed) > 1000f)
            diagnostics.Add(new ProfileValidationDiagnostic(ProfileValidationSeverity.Caution, "ExtremeNumber", $"{slider} uses an unusual value {value}. This is allowed, but verify the generated output for your body mod.", table, slider));

        return true;
    }

    private void RefreshVisibleRows()
    {
        VisibleDefaultRows.Clear();
        foreach (var row in DefaultRows.Where(row => string.IsNullOrWhiteSpace(SearchText) || row.Slider.Contains(SearchText, StringComparison.OrdinalIgnoreCase)))
            VisibleDefaultRows.Add(row);
    }

    private string CreateFingerprint() => string.Join("|", new[]
    {
        Name,
        Game,
        string.Join(";", DefaultRows.Select(row => row.Slider + ":" + row.ValueSmall + ":" + row.ValueBig)),
        string.Join(";", MultiplierRows.Select(row => row.Slider + ":" + row.Value)),
        string.Join(";", InvertedRows.Select(row => row.Slider + ":" + row.IsInverted.ToString(CultureInfo.InvariantCulture)))
    });

    private static string FormatFloat(float value) => value.ToString("R", CultureInfo.InvariantCulture);

    private static ProfileValidationDiagnostic Blocker(string code, string message, string? table, string? sliderName) =>
        new(ProfileValidationSeverity.Blocker, code, message, table, sliderName);

    private sealed class EmptyUserProfileStore : IUserProfileStore
    {
        public UserProfileDiscoveryResult DiscoverProfiles() => DiscoverProfiles([]);
        public UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames) => new([], []);
        public UserProfileSaveResult SaveProfile(CustomProfileDefinition profile) => new(false, null, []);
        public UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile) => new(false, null, []);
        public string GetDefaultProfileDirectory() => string.Empty;
    }
}

/// <summary>
/// Editable Defaults table row containing slider identity and small/big numeric text values.
/// </summary>
public sealed partial class ProfileDefaultRowViewModel : ReactiveObject
{
    [Reactive] private string _slider;
    [Reactive] private string _valueBig;
    [Reactive] private string _valueSmall;

    public ProfileDefaultRowViewModel(string slider, string valueSmall, string valueBig)
    {
        _slider = slider;
        _valueSmall = valueSmall;
        _valueBig = valueBig;
    }
}

/// <summary>
/// Editable Multipliers table row containing slider identity and multiplier numeric text.
/// </summary>
public sealed partial class ProfileMultiplierRowViewModel : ReactiveObject
{
    [Reactive] private string _slider;
    [Reactive] private string _value;

    public ProfileMultiplierRowViewModel(string slider, string value)
    {
        _slider = slider;
        _value = value;
    }
}

/// <summary>
/// Editable Inverted table row containing slider identity and inclusion state.
/// </summary>
public sealed partial class ProfileInvertedRowViewModel : ReactiveObject
{
    [Reactive] private bool _isInverted;
    [Reactive] private string _slider;

    public ProfileInvertedRowViewModel(string slider, bool isInverted)
    {
        _slider = slider;
        _isInverted = isInverted;
    }
}

/// <summary>
/// Validation or status row shown by the profile editor.
/// </summary>
public sealed record ProfileEditorStatusRowViewModel(ProfileValidationSeverity Severity, string Text);
