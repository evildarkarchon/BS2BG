using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Globalization;
using System.Reactive;
using System.Reactive.Disposables;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using BS2BG.App.Services;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using ReactiveUI;
using ReactiveUI.SourceGenerators;
using SetSlider = BS2BG.Core.Models.SetSlider;
using SliderPreset = BS2BG.Core.Models.SliderPreset;

namespace BS2BG.App.ViewModels;

public sealed partial class TemplatesViewModel : ReactiveObject, IDisposable
{
    private readonly IClipboardService clipboardService;
    private readonly CompositeDisposable disposables = new();
    private readonly BehaviorSubject<bool> externalBusy = new(false);
    private readonly IBodySlideXmlFilePicker filePicker;
    private readonly BodySlideXmlParser parser;
    private readonly SerialDisposable presetSubscription = new();
    private readonly TemplateProfileCatalog profileCatalog;
    private readonly IUserPreferencesService preferencesService;
    private readonly ProjectModel project;
    private readonly TemplateGenerationService templateGenerationService;
    private readonly UndoRedoService undoRedo;
    private UserPreferences currentPreferences;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _generatedTemplateText = string.Empty;

    [ObservableAsProperty] private bool _isBusy;

    [Reactive(SetModifier = AccessModifier.Private)]
    private bool _isProfileFallbackInformationVisible;

    [Reactive] private bool _omitRedundantSliders;
    [Reactive] private string _presetNameInput = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _profileFallbackInformationText = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _previewTemplateText = string.Empty;

    [Reactive] private string _searchText = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _selectedBosJsonText = string.Empty;

    [Reactive] private SliderPreset? _selectedPreset;
    [Reactive] private string _selectedProfileName = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _statusMessage = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _validationMessage = string.Empty;

    private bool syncingProfileFromPreset;

    public TemplatesViewModel()
        : this(
            new ProjectModel(),
            new BodySlideXmlParser(),
            new TemplateGenerationService(),
            new TemplateProfileCatalog(new[]
            {
                new TemplateProfile(
                    ProjectProfileMapping.SkyrimCbbe,
                    new SliderProfile(
                        Array.Empty<SliderDefault>(),
                        Array.Empty<SliderMultiplier>(),
                        Array.Empty<string>()))
            }),
            new EmptyBodySlideXmlFilePicker(),
            new EmptyClipboardService())
    {
    }

    public TemplatesViewModel(
        ProjectModel project,
        BodySlideXmlParser parser,
        TemplateGenerationService templateGenerationService,
        TemplateProfileCatalog profileCatalog,
        IBodySlideXmlFilePicker filePicker,
        IClipboardService clipboardService,
        UndoRedoService? undoRedo = null,
        IUserPreferencesService? preferencesService = null)
    {
        this.project = project ?? throw new ArgumentNullException(nameof(project));
        this.parser = parser ?? throw new ArgumentNullException(nameof(parser));
        this.templateGenerationService = templateGenerationService
                                         ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.profileCatalog = profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog));
        this.filePicker = filePicker ?? throw new ArgumentNullException(nameof(filePicker));
        this.clipboardService = clipboardService ?? throw new ArgumentNullException(nameof(clipboardService));
        this.undoRedo = undoRedo ?? new UndoRedoService();
        this.preferencesService = preferencesService ?? new UserPreferencesService();
        currentPreferences = this.preferencesService.Load();
        _selectedProfileName = profileCatalog.DefaultProfile.Name;
        _omitRedundantSliders = currentPreferences.OmitRedundantSliders;
        project.SliderPresets.CollectionChanged += (_, _) => RefreshVisiblePresets();
        disposables.Add(presetSubscription);

        var presetsCount = CollectionChangedObservable.Observe(Presets, () => Presets.Count);
        var setSliderRowsCount = CollectionChangedObservable.Observe(SetSliderRows, () => SetSliderRows.Count);

        disposables.Add(externalBusy);
        var notExternallyBusy = externalBusy.DistinctUntilChanged().Select(b => !b);

        var canEditSelectedPreset = this.WhenAnyValue(x => x.SelectedPreset)
            .CombineLatest(notExternallyBusy, (p, ok) => p is not null && ok);
        var canManagePresets = presetsCount.CombineLatest(notExternallyBusy, (c, ok) => c > 0 && ok);
        var canCopyBosJson = this.WhenAnyValue(x => x.SelectedBosJsonText)
            .Select(text => !string.IsNullOrWhiteSpace(text));
        var canEditSetSliders = setSliderRowsCount.CombineLatest(notExternallyBusy, (c, ok) => c > 0 && ok);
        var canImport = this.WhenAnyValue(x => x.IsBusy)
            .CombineLatest(notExternallyBusy, (busy, ok) => !busy && ok);

        ImportPresetsCommand = ReactiveCommand.CreateFromTask(
            ImportPresetsAsync,
            canImport);
        RenameSelectedPresetCommand = ReactiveCommand.Create(
            () => { TryRenameSelectedPreset(PresetNameInput); },
            canEditSelectedPreset);
        DuplicateSelectedPresetCommand = ReactiveCommand.Create(
            () => { TryDuplicateSelectedPreset(PresetNameInput); },
            canEditSelectedPreset);
        RemoveSelectedPresetCommand = ReactiveCommand.Create(
            () => { RemoveSelectedPreset(); },
            canEditSelectedPreset);
        ClearPresetsCommand = ReactiveCommand.Create(
            ClearPresets,
            canManagePresets);
        GenerateTemplatesCommand = ReactiveCommand.Create(
            GenerateTemplates,
            canManagePresets);
        CopyGeneratedTemplatesCommand = ReactiveCommand.CreateFromTask(
            CopyGeneratedTemplatesAsync);
        CopySelectedBosJsonCommand = ReactiveCommand.CreateFromTask(
            CopySelectedBosJsonAsync,
            canCopyBosJson);
        SetAllSliderPercentsTo0Command = ReactiveCommand.Create(() => SetAllSliderPercents(0), canEditSetSliders);
        SetAllSliderPercentsTo50Command = ReactiveCommand.Create(() => SetAllSliderPercents(50), canEditSetSliders);
        SetAllSliderPercentsTo100Command = ReactiveCommand.Create(() => SetAllSliderPercents(100), canEditSetSliders);
        SetAllMinPercentsTo0Command = ReactiveCommand.Create(() => SetAllMinPercents(0), canEditSetSliders);
        SetAllMinPercentsTo50Command = ReactiveCommand.Create(() => SetAllMinPercents(50), canEditSetSliders);
        SetAllMinPercentsTo100Command = ReactiveCommand.Create(() => SetAllMinPercents(100), canEditSetSliders);
        SetAllMaxPercentsTo0Command = ReactiveCommand.Create(() => SetAllMaxPercents(0), canEditSetSliders);
        SetAllMaxPercentsTo50Command = ReactiveCommand.Create(() => SetAllMaxPercents(50), canEditSetSliders);
        SetAllMaxPercentsTo100Command = ReactiveCommand.Create(() => SetAllMaxPercents(100), canEditSetSliders);

        disposables.Add(ImportPresetsCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Import presets", ex)));
        disposables.Add(CopyGeneratedTemplatesCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Copy generated templates", ex)));
        disposables.Add(CopySelectedBosJsonCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Copy BoS JSON", ex)));

        _isBusyHelper = ImportPresetsCommand.IsExecuting.ToProperty(this, x => x.IsBusy, initialValue: false);

        disposables.Add(this.WhenAnyValue(x => x.SelectedPreset)
            .Subscribe(OnSelectedPresetChangedReactive));
        disposables.Add(this.WhenAnyValue(x => x.SelectedProfileName)
            .Skip(1)
            .Subscribe(OnSelectedProfileNameChangedReactive));
        disposables.Add(this.WhenAnyValue(x => x.OmitRedundantSliders)
            .Skip(1)
            .Subscribe(_ =>
            {
                SaveOmitRedundantSlidersPreference();
                GeneratedTemplateText = string.Empty;
                RefreshPreview();
            }));
        disposables.Add(this.WhenAnyValue(x => x.SearchText)
            .Skip(1)
            .Subscribe(_ => RefreshVisiblePresets()));

        RefreshVisiblePresets();
    }

    public ObservableCollection<SliderPreset> Presets => project.SliderPresets;

    public ObservableCollection<SliderPreset> VisiblePresets { get; } = new();

    public ObservableCollection<SetSliderInspectorRowViewModel> SetSliderRows { get; } = new();

    public IReadOnlyList<string> ProfileNames => profileCatalog.ProfileNames;

    public ReactiveCommand<Unit, Unit> ImportPresetsCommand { get; }

    public ReactiveCommand<Unit, Unit> RenameSelectedPresetCommand { get; }

    public ReactiveCommand<Unit, Unit> DuplicateSelectedPresetCommand { get; }

    public ReactiveCommand<Unit, Unit> RemoveSelectedPresetCommand { get; }

    public ReactiveCommand<Unit, Unit> ClearPresetsCommand { get; }

    public ReactiveCommand<Unit, Unit> GenerateTemplatesCommand { get; }

    public ReactiveCommand<Unit, Unit> CopyGeneratedTemplatesCommand { get; }

    public ReactiveCommand<Unit, Unit> CopySelectedBosJsonCommand { get; }

    public ReactiveCommand<Unit, Unit> SetAllSliderPercentsTo0Command { get; }

    public ReactiveCommand<Unit, Unit> SetAllSliderPercentsTo50Command { get; }

    public ReactiveCommand<Unit, Unit> SetAllSliderPercentsTo100Command { get; }

    public ReactiveCommand<Unit, Unit> SetAllMinPercentsTo0Command { get; }

    public ReactiveCommand<Unit, Unit> SetAllMinPercentsTo50Command { get; }

    public ReactiveCommand<Unit, Unit> SetAllMinPercentsTo100Command { get; }

    public ReactiveCommand<Unit, Unit> SetAllMaxPercentsTo0Command { get; }

    public ReactiveCommand<Unit, Unit> SetAllMaxPercentsTo50Command { get; }

    public ReactiveCommand<Unit, Unit> SetAllMaxPercentsTo100Command { get; }

    public void Dispose() => disposables.Dispose();

    public void LinkExternalBusy(IObservable<bool> source)
    {
        ArgumentNullException.ThrowIfNull(source);

        disposables.Add(source.DistinctUntilChanged().Subscribe(externalBusy.OnNext));
    }

    public async Task ImportPresetsAsync(CancellationToken cancellationToken = default)
    {
        ValidationMessage = string.Empty;
        StatusMessage = string.Empty;
        await ImportPresetFilesCoreAsync(
            await filePicker.PickXmlPresetFilesAsync(cancellationToken),
            "No preset files selected.",
            cancellationToken);
    }

    public async Task ImportPresetFilesAsync(
        IReadOnlyList<string> files,
        CancellationToken cancellationToken = default)
    {
        ValidationMessage = string.Empty;
        StatusMessage = string.Empty;
        await ImportPresetFilesCoreAsync(files, "No preset files dropped.", cancellationToken);
    }

    public bool TryRenameSelectedPreset(string newName)
    {
        if (SelectedPreset is null) return false;

        if (!TryValidatePresetName(newName, SelectedPreset, out var normalizedName)) return false;

        var preset = SelectedPreset;
        var previousName = preset.Name;
        ApplyPresetRename(preset, normalizedName);
        undoRedo.Record(
            "Rename preset",
            () => ApplyPresetRename(preset, previousName),
            () => ApplyPresetRename(preset, normalizedName));
        return true;
    }

    public bool TryDuplicateSelectedPreset(string newName)
    {
        if (SelectedPreset is null) return false;

        if (!TryValidatePresetName(newName, null, out var normalizedName)) return false;

        var duplicate = SelectedPreset.Clone(normalizedName);
        Presets.Add(duplicate);
        SortPresets();
        SelectedPreset = duplicate;
        ValidationMessage = string.Empty;
        undoRedo.Record(
            "Duplicate preset",
            () =>
            {
                Presets.Remove(duplicate);
                SelectedPreset = Presets.FirstOrDefault();
            },
            () =>
            {
                Presets.Add(duplicate);
                SortPresets();
                SelectedPreset = duplicate;
            });
        return true;
    }

    public bool RemoveSelectedPreset()
    {
        if (SelectedPreset is null) return false;

        var preset = SelectedPreset;
        var currentIndex = Presets.IndexOf(preset);
        var assignedTargets = project.CustomMorphTargets
            .Cast<MorphTargetBase>()
            .Concat(project.MorphedNpcs)
            .Where(target => target.SliderPresets.Contains(preset))
            .ToArray();
        var removed = project.RemoveSliderPreset(preset.Name);
        if (!removed) return false;

        SelectedPreset = Presets.Count == 0
            ? null
            : Presets[Math.Min(currentIndex, Presets.Count - 1)];
        GeneratedTemplateText = string.Empty;
        undoRedo.Record(
            "Remove preset",
            () =>
            {
                Presets.Add(preset);
                SortPresets();
                foreach (var target in assignedTargets) target.AddSliderPreset(preset);

                SelectedPreset = preset;
                GeneratedTemplateText = string.Empty;
            },
            () =>
            {
                project.RemoveSliderPreset(preset.Name);
                SelectedPreset = Presets.FirstOrDefault();
                GeneratedTemplateText = string.Empty;
            });
        return true;
    }

    public void ClearPresets()
    {
        var snapshot = CapturePresetAssignmentSnapshot();
        ApplyClearPresets();
        if (snapshot.HasState)
            undoRedo.Record(
                "Clear presets",
                () => RestorePresetAssignmentSnapshot(snapshot),
                ApplyClearPresets);
    }

    private void ApplyClearPresets()
    {
        foreach (var target in project.CustomMorphTargets) target.ClearSliderPresets();

        foreach (var npc in project.MorphedNpcs) npc.ClearSliderPresets();

        Presets.Clear();
        SelectedPreset = null;
        GeneratedTemplateText = string.Empty;
        StatusMessage = string.Empty;
        ValidationMessage = string.Empty;
    }

    public void SortPresets()
    {
        project.SortPresets();
        RefreshVisiblePresets();
    }

    public void GenerateTemplates()
    {
        if (Presets.Count == 0)
        {
            GeneratedTemplateText = string.Empty;
            StatusMessage = "No presets to generate.";
            return;
        }

        GeneratedTemplateText = templateGenerationService.GenerateTemplates(
            Presets,
            profileCatalog,
            OmitRedundantSliders);
        StatusMessage = "Generated " + Presets.Count.ToString(CultureInfo.InvariantCulture)
                                     + " template line" + (Presets.Count == 1 ? "." : "s.");
    }

    public async Task CopyGeneratedTemplatesAsync(CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(GeneratedTemplateText))
        {
            StatusMessage = "Generate templates before copying.";
            return;
        }

        await clipboardService.SetTextAsync(GeneratedTemplateText, cancellationToken);
        StatusMessage = "Generated templates copied.";
    }

    public async Task CopySelectedBosJsonAsync(CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(SelectedBosJsonText))
        {
            StatusMessage = "Select a preset before copying BoS JSON.";
            return;
        }

        await clipboardService.SetTextAsync(SelectedBosJsonText, cancellationToken);
        StatusMessage = "BoS JSON copied.";
    }

    private void SetAllSliderPercents(int value)
    {
        var before = CaptureSelectedSetSliders();
        foreach (var row in SetSliderRows) row.SetBothPercents(value, false);

        RefreshAfterSetSliderEdit();
        RecordSelectedSetSliderChange("Set slider percents", before);
    }

    private void SetAllMinPercents(int value)
    {
        var before = CaptureSelectedSetSliders();
        foreach (var row in SetSliderRows) row.SetMinPercent(value, false);

        RefreshAfterSetSliderEdit();
        RecordSelectedSetSliderChange("Set min percents", before);
    }

    private void SetAllMaxPercents(int value)
    {
        var before = CaptureSelectedSetSliders();
        foreach (var row in SetSliderRows) row.SetMaxPercent(value, false);

        RefreshAfterSetSliderEdit();
        RecordSelectedSetSliderChange("Set max percents", before);
    }

    private async Task ImportPresetFilesCoreAsync(
        IReadOnlyList<string> files,
        string emptyStatus,
        CancellationToken cancellationToken)
    {
        if (files.Count == 0)
        {
            StatusMessage = emptyStatus;
            return;
        }

        var before = SnapshotPresets();
        var import = await Task.Run(() => parser.ParseFiles(files), cancellationToken);
        foreach (var preset in import.Presets)
        {
            preset.ProfileName = SelectedProfileName;
            AddOrUpdatePreset(preset);
        }

        SortPresets();
        if (import.Presets.Count > 0) SelectedPreset = Presets.FirstOrDefault();

        StatusMessage = FormatImportStatus(import);
        var after = SnapshotPresets();
        undoRedo.Record(
            "Import presets",
            () => RestorePresetSnapshot(before),
            () => RestorePresetSnapshot(after));
    }

    private void ApplyPresetRename(SliderPreset preset, string name)
    {
        preset.Name = name;
        PresetNameInput = name;
        SortPresets();
        SelectedPreset = preset;
        ValidationMessage = string.Empty;
        RefreshPreview();
    }

    private void ReportCommandFailure(string action, Exception exception) =>
        StatusMessage = action + " failed: " + FormatExceptionMessage(exception);

    /// <summary>
    /// Saves the local workflow preference without writing it into the shared project model.
    /// Save failures are intentionally non-blocking because preferences are convenience state only.
    /// </summary>
    private void SaveOmitRedundantSlidersPreference()
    {
        currentPreferences = new UserPreferences
        {
            Theme = currentPreferences.Theme,
            OmitRedundantSliders = OmitRedundantSliders,
            ProjectFolder = currentPreferences.ProjectFolder,
            BodyGenExportFolder = currentPreferences.BodyGenExportFolder,
            BosJsonExportFolder = currentPreferences.BosJsonExportFolder
        };
        if (!preferencesService.Save(currentPreferences))
            StatusMessage = "This workflow preference could not be saved. BS2BG will continue using defaults for this session.";
    }

    private void AddOrUpdatePreset(SliderPreset importedPreset)
    {
        var existingPreset = project.FindSliderPreset(importedPreset.Name);
        if (existingPreset is null)
        {
            Presets.Add(importedPreset.Clone());
            return;
        }

        existingPreset.SetSliders.Clear();
        existingPreset.MissingDefaultSetSliders.Clear();
        foreach (var slider in importedPreset.SetSliders) existingPreset.AddSetSlider(slider.Clone());

        existingPreset.ProfileName = importedPreset.ProfileName;
    }

    private void RefreshPreview()
    {
        if (SelectedPreset is null)
        {
            PreviewTemplateText = string.Empty;
            return;
        }

        PreviewTemplateText = templateGenerationService.PreviewTemplate(
            SelectedPreset,
            GetSelectedCalculationProfile(),
            OmitRedundantSliders);
    }

    private void RefreshVisiblePresets()
    {
        var selected = SelectedPreset;
        VisiblePresets.Clear();
        foreach (var preset in Presets.Where(MatchesSearch)) VisiblePresets.Add(preset);

        if (selected is not null && !VisiblePresets.Contains(selected) && ReferenceEquals(SelectedPreset, selected))
            SelectedPreset = VisiblePresets.FirstOrDefault();
    }

    private bool MatchesSearch(SliderPreset preset)
    {
        if (string.IsNullOrWhiteSpace(SearchText)) return true;

        return Contains(preset.Name, SearchText)
               || Contains(preset.ProfileName, SearchText)
               || preset.SetSliders.Concat(preset.MissingDefaultSetSliders)
                   .Any(slider => Contains(slider.Name, SearchText));
    }

    private static bool Contains(string value, string searchText) =>
        value.Contains(searchText, StringComparison.OrdinalIgnoreCase);

    private void RefreshSelectedBosJson()
    {
        SelectedBosJsonText = SelectedPreset is null
            ? string.Empty
            : templateGenerationService.PreviewBosJson(
                SelectedPreset,
                GetSelectedCalculationProfile());
    }

    private void OnSelectedPresetChangedReactive(SliderPreset? preset)
    {
        if (preset is not null)
        {
            var subscription = new CompositeDisposable();

            void Handler(object? sender, PropertyChangedEventArgs args)
            {
                OnSelectedPresetPropertyChanged(args);
            }

            void SlidersHandler(object? sender, NotifyCollectionChangedEventArgs args)
            {
                OnSelectedPresetSlidersCollectionChanged(args);
            }

            preset.PropertyChanged += Handler;
            preset.SetSliders.CollectionChanged += SlidersHandler;
            preset.MissingDefaultSetSliders.CollectionChanged += SlidersHandler;
            subscription.Add(Disposable.Create(() => preset.PropertyChanged -= Handler));
            subscription.Add(Disposable.Create(() => preset.SetSliders.CollectionChanged -= SlidersHandler));
            subscription.Add(
                Disposable.Create(() => preset.MissingDefaultSetSliders.CollectionChanged -= SlidersHandler));
            presetSubscription.Disposable = subscription;
            PresetNameInput = preset.Name;
            SetSelectedProfileNameFromPreset(preset.ProfileName);
            RefreshSelectedPresetMissingDefaults(GetSelectedCalculationProfile().Name);
        }
        else
        {
            presetSubscription.Disposable = null;
            PresetNameInput = string.Empty;
            RefreshProfileFallbackInformation();
        }

        RebuildSetSliderRows();
        RefreshPreview();
        RefreshSelectedBosJson();
    }

    private void OnSelectedProfileNameChangedReactive(string profileName)
    {
        if (syncingProfileFromPreset && string.IsNullOrWhiteSpace(profileName)) return;

        var resolvedName = profileCatalog.GetProfile(profileName).Name;
        if (!string.Equals(profileName, resolvedName, StringComparison.Ordinal))
        {
            SelectedProfileName = resolvedName;
            return;
        }

        if (!syncingProfileFromPreset && SelectedPreset is not null) SelectedPreset.ProfileName = resolvedName;

        RefreshSelectedPresetMissingDefaults(resolvedName);
        RefreshProfileFallbackInformation();
        RebuildSetSliderRows();
        RefreshSetSliderRowPreviews();
        RefreshPreview();
        RefreshSelectedBosJson();
    }

    private void OnSelectedPresetPropertyChanged(PropertyChangedEventArgs args)
    {
        if (args.PropertyName == nameof(SliderPreset.ProfileName))
            SetSelectedProfileNameFromPreset(SelectedPreset?.ProfileName);

        RefreshProfileFallbackInformation();
        RefreshPreview();
        RefreshSelectedBosJson();
        RefreshSetSliderRowPreviews();
    }

    private void OnSelectedPresetSlidersCollectionChanged(NotifyCollectionChangedEventArgs args)
    {
        RebuildSetSliderRows();
        RefreshAfterSetSliderEdit();
    }

    private void RefreshSelectedPresetMissingDefaults(string profileName)
    {
        if (SelectedPreset is null) return;

        using var _ = project.SuppressDirtyTracking();
        SelectedPreset.RefreshMissingDefaultSetSliders(profileCatalog.GetProfile(profileName).DefaultSliderNames);
    }

    private void SetSelectedProfileNameFromPreset(string? profileName)
    {
        syncingProfileFromPreset = true;
        try
        {
            SelectedProfileName = profileCatalog.ContainsProfile(profileName)
                ? profileCatalog.GetProfile(profileName).Name
                : string.Empty;
        }
        finally
        {
            syncingProfileFromPreset = false;
        }

        RefreshProfileFallbackInformation();
    }

    /// <summary>
    /// Resolves the profile used for preview and inspector math without adopting an unbundled saved profile.
    /// Selector changes remain the only path that writes a bundled profile back to the selected preset.
    /// </summary>
    private TemplateProfile GetSelectedCalculationProfile()
    {
        if (SelectedPreset is not null && !profileCatalog.ContainsProfile(SelectedPreset.ProfileName))
            return profileCatalog.GetProfile(SelectedPreset.ProfileName);

        return profileCatalog.GetProfile(SelectedProfileName);
    }

    /// <summary>
    /// Refreshes neutral fallback copy for saved project profiles that are not bundled with the current catalog.
    /// The selected preset keeps its original profile name so project round trips remain lossless until the user chooses a bundled profile.
    /// </summary>
    private void RefreshProfileFallbackInformation()
    {
        var savedName = SelectedPreset?.ProfileName;
        if (string.IsNullOrWhiteSpace(savedName) || profileCatalog.ContainsProfile(savedName))
        {
            ProfileFallbackInformationText = string.Empty;
            IsProfileFallbackInformationVisible = false;
            return;
        }

        var fallbackName = profileCatalog.GetProfile(savedName).Name;
        ProfileFallbackInformationText = "Saved profile \"" + savedName
                                         + "\" is not bundled. BS2BG is using " + fallbackName
                                         + " calculation rules for preview and generation until you choose a bundled profile.";
        IsProfileFallbackInformationVisible = true;
    }

    private bool TryValidatePresetName(
        string newName,
        SliderPreset? existingPresetToIgnore,
        out string normalizedName)
    {
        normalizedName = NormalizePresetName(newName);
        if (string.IsNullOrWhiteSpace(normalizedName))
        {
            ValidationMessage = "Preset name is required.";
            return false;
        }

        if (!SliderPreset.TryValidateName(normalizedName, out var characterError))
        {
            ValidationMessage = characterError;
            return false;
        }

        var candidateName = normalizedName;
        if (Presets.Any(preset =>
                !ReferenceEquals(preset, existingPresetToIgnore)
                && string.Equals(preset.Name, candidateName, StringComparison.OrdinalIgnoreCase)))
        {
            ValidationMessage = "A preset named '" + normalizedName + "' already exists.";
            return false;
        }

        return true;
    }

    private void RebuildSetSliderRows()
    {
        foreach (var row in SetSliderRows) row.Dispose();

        SetSliderRows.Clear();
        if (SelectedPreset is null) return;

        var sliders = SelectedPreset.SetSliders
            .Concat(SelectedPreset.MissingDefaultSetSliders)
            .OrderBy(slider => slider.Name, StringComparer.OrdinalIgnoreCase)
            .ToArray();
        foreach (var slider in sliders)
            SetSliderRows.Add(new SetSliderInspectorRowViewModel(
                slider,
                templateGenerationService,
                GetSelectedCalculationProfile,
                RefreshAfterSetSliderEdit,
                undoRedo));
    }

    private void RefreshAfterSetSliderEdit()
    {
        RefreshPreview();
        RefreshSelectedBosJson();
        RefreshSetSliderRowPreviews();
    }

    private SliderPreset[] SnapshotPresets() => Presets.Select(preset => preset.Clone()).ToArray();

    private PresetAssignmentSnapshot CapturePresetAssignmentSnapshot()
    {
        return new PresetAssignmentSnapshot(
            Presets.ToArray(),
            project.CustomMorphTargets
                .Select(target => new MorphTargetAssignmentSnapshot(target, target.SliderPresets.ToArray()))
                .ToArray(),
            project.MorphedNpcs
                .Select(npc => new MorphTargetAssignmentSnapshot(npc, npc.SliderPresets.ToArray()))
                .ToArray(),
            SelectedPreset);
    }

    private void RestorePresetAssignmentSnapshot(PresetAssignmentSnapshot snapshot)
    {
        Presets.Clear();
        foreach (var preset in snapshot.Presets) Presets.Add(preset);

        RestoreAssignmentSnapshots(snapshot.CustomTargetAssignments);
        RestoreAssignmentSnapshots(snapshot.NpcAssignments);
        SelectedPreset = snapshot.SelectedPreset is not null && Presets.Contains(snapshot.SelectedPreset)
            ? snapshot.SelectedPreset
            : Presets.FirstOrDefault();
        GeneratedTemplateText = string.Empty;
        StatusMessage = string.Empty;
        ValidationMessage = string.Empty;
    }

    private void RestorePresetSnapshot(IReadOnlyList<SliderPreset> snapshot)
    {
        Presets.Clear();
        foreach (var preset in snapshot.Select(preset => preset.Clone())) Presets.Add(preset);

        SortPresets();
        RemapAssignmentsToCurrentPresets();
        SelectedPreset = Presets.FirstOrDefault();
        GeneratedTemplateText = string.Empty;
    }

    private void RemapAssignmentsToCurrentPresets()
    {
        var presetsByName = Presets.ToDictionary(
            preset => preset.Name,
            preset => preset,
            StringComparer.OrdinalIgnoreCase);

        foreach (var target in project.CustomMorphTargets) RemapAssignmentsToCurrentPresets(target, presetsByName);

        foreach (var npc in project.MorphedNpcs) RemapAssignmentsToCurrentPresets(npc, presetsByName);
    }

    private static void RemapAssignmentsToCurrentPresets(
        MorphTargetBase target,
        Dictionary<string, SliderPreset> presetsByName)
    {
        var assignments = target.SliderPresets.ToArray();
        target.ClearSliderPresets();
        foreach (var assignment in assignments)
            if (presetsByName.TryGetValue(assignment.Name, out var restoredPreset))
                target.AddSliderPreset(restoredPreset);
    }

    private static void RestoreAssignmentSnapshots(IEnumerable<MorphTargetAssignmentSnapshot> snapshots)
    {
        foreach (var snapshot in snapshots)
        {
            snapshot.Target.ClearSliderPresets();
            foreach (var preset in snapshot.Presets) snapshot.Target.AddSliderPreset(preset);
        }
    }

    private SetSliderSnapshot[] CaptureSelectedSetSliders()
    {
        return SelectedPreset?.SetSliders
            .Concat(SelectedPreset.MissingDefaultSetSliders)
            .Select(SetSliderSnapshot.Create)
            .ToArray() ?? Array.Empty<SetSliderSnapshot>();
    }

    private void RecordSelectedSetSliderChange(string name, SetSliderSnapshot[] before)
    {
        var preset = SelectedPreset;
        if (preset is null || before.Length == 0) return;

        var after = CaptureSelectedSetSliders();
        undoRedo.Record(
            name,
            () => RestoreSetSliders(preset, before),
            () => RestoreSetSliders(preset, after));
    }

    private void RestoreSetSliders(SliderPreset preset, IEnumerable<SetSliderSnapshot> snapshot)
    {
        foreach (var item in snapshot)
        {
            var slider = preset.SetSliders
                .Concat(preset.MissingDefaultSetSliders)
                .FirstOrDefault(candidate =>
                    string.Equals(candidate.Name, item.Name, StringComparison.OrdinalIgnoreCase));
            if (slider is null) continue;

            item.Apply(slider);
        }

        RefreshAfterSetSliderEdit();
    }

    private void RefreshSetSliderRowPreviews()
    {
        foreach (var row in SetSliderRows) row.RefreshPreview();
    }

    private static string NormalizePresetName(string value) => (value ?? string.Empty).Trim().Replace('.', ' ');

    private static string FormatExceptionMessage(Exception exception)
    {
        return string.IsNullOrWhiteSpace(exception.Message)
            ? exception.GetType().Name
            : exception.Message;
    }

    private static string FormatImportStatus(BodySlideXmlImportResult import)
    {
        var status = "Imported " + import.Presets.Count.ToString(CultureInfo.InvariantCulture)
                                 + " preset" + (import.Presets.Count == 1 ? "." : "s.");
        if (import.Diagnostics.Count > 0)
            status += " " + import.Diagnostics.Count.ToString(CultureInfo.InvariantCulture)
                          + " issue" + (import.Diagnostics.Count == 1 ? " was" : "s were") + " skipped.";

        return status;
    }

    private sealed class SetSliderSnapshot
    {
        private SetSliderSnapshot(
            string name,
            bool enabled,
            int? valueSmall,
            int? valueBig,
            int percentMin,
            int percentMax)
        {
            Name = name;
            Enabled = enabled;
            ValueSmall = valueSmall;
            ValueBig = valueBig;
            PercentMin = percentMin;
            PercentMax = percentMax;
        }

        public string Name { get; }

        public bool Enabled { get; }

        public int? ValueSmall { get; }

        public int? ValueBig { get; }

        public int PercentMin { get; }

        public int PercentMax { get; }

        public static SetSliderSnapshot Create(SetSlider slider)
        {
            return new SetSliderSnapshot(
                slider.Name,
                slider.Enabled,
                slider.ValueSmall,
                slider.ValueBig,
                slider.PercentMin,
                slider.PercentMax);
        }

        public void Apply(SetSlider slider)
        {
            slider.Enabled = Enabled;
            slider.ValueSmall = ValueSmall;
            slider.ValueBig = ValueBig;
            slider.PercentMin = PercentMin;
            slider.PercentMax = PercentMax;
        }
    }

    private sealed class PresetAssignmentSnapshot(
        IReadOnlyList<SliderPreset> presets,
        IReadOnlyList<MorphTargetAssignmentSnapshot> customTargetAssignments,
        IReadOnlyList<MorphTargetAssignmentSnapshot> npcAssignments,
        SliderPreset? selectedPreset)
    {
        public IReadOnlyList<SliderPreset> Presets { get; } = presets;

        public IReadOnlyList<MorphTargetAssignmentSnapshot> CustomTargetAssignments { get; } = customTargetAssignments;

        public IReadOnlyList<MorphTargetAssignmentSnapshot> NpcAssignments { get; } = npcAssignments;

        public SliderPreset? SelectedPreset { get; } = selectedPreset;

        public bool HasState =>
            Presets.Count > 0
            || CustomTargetAssignments.Any(snapshot => snapshot.Presets.Count > 0)
            || NpcAssignments.Any(snapshot => snapshot.Presets.Count > 0);
    }

    private sealed class MorphTargetAssignmentSnapshot(MorphTargetBase target, IReadOnlyList<SliderPreset> presets)
    {
        public MorphTargetBase Target { get; } = target;

        public IReadOnlyList<SliderPreset> Presets { get; } = presets;
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
    }

    private sealed class EmptyClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.CompletedTask;
    }
}
