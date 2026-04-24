using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using ReactiveUI;

namespace BS2BG.App.ViewModels;

public sealed class TemplatesViewModel : ReactiveObject
{
    private readonly ProjectModel project;
    private readonly BodySlideXmlParser parser;
    private readonly TemplateGenerationService templateGenerationService;
    private readonly TemplateProfileCatalog profileCatalog;
    private readonly IBodySlideXmlFilePicker filePicker;
    private readonly IClipboardService clipboardService;
    private SliderPreset? selectedPreset;
    private string selectedProfileName;
    private string presetNameInput = string.Empty;
    private string previewTemplateText = string.Empty;
    private string generatedTemplateText = string.Empty;
    private string selectedBosJsonText = string.Empty;
    private string validationMessage = string.Empty;
    private string statusMessage = string.Empty;
    private bool omitRedundantSliders;
    private bool isBusy;
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
                    new BS2BG.Core.Formatting.SliderProfile(
                        Array.Empty<BS2BG.Core.Formatting.SliderDefault>(),
                        Array.Empty<BS2BG.Core.Formatting.SliderMultiplier>(),
                        Array.Empty<string>())),
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
        IClipboardService clipboardService)
    {
        this.project = project ?? throw new ArgumentNullException(nameof(project));
        this.parser = parser ?? throw new ArgumentNullException(nameof(parser));
        this.templateGenerationService = templateGenerationService
            ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.profileCatalog = profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog));
        this.filePicker = filePicker ?? throw new ArgumentNullException(nameof(filePicker));
        this.clipboardService = clipboardService ?? throw new ArgumentNullException(nameof(clipboardService));
        selectedProfileName = profileCatalog.DefaultProfile.Name;

        ImportPresetsCommand = new AsyncRelayCommand(
            ImportPresetsAsync,
            () => !IsBusy,
            exception => ReportCommandFailure("Import presets", exception));
        RenameSelectedPresetCommand = new RelayCommand(
            () => TryRenameSelectedPreset(PresetNameInput),
            () => SelectedPreset is not null);
        DuplicateSelectedPresetCommand = new RelayCommand(
            () => TryDuplicateSelectedPreset(PresetNameInput),
            () => SelectedPreset is not null);
        RemoveSelectedPresetCommand = new RelayCommand(
            () => RemoveSelectedPreset(),
            () => SelectedPreset is not null);
        ClearPresetsCommand = new RelayCommand(ClearPresets, () => Presets.Count > 0);
        GenerateTemplatesCommand = new RelayCommand(GenerateTemplates, () => Presets.Count > 0);
        CopyGeneratedTemplatesCommand = new AsyncRelayCommand(
            CopyGeneratedTemplatesAsync,
            reportException: exception => ReportCommandFailure("Copy generated templates", exception));
        CopySelectedBosJsonCommand = new AsyncRelayCommand(
            CopySelectedBosJsonAsync,
            () => !string.IsNullOrWhiteSpace(SelectedBosJsonText),
            exception => ReportCommandFailure("Copy BoS JSON", exception));
        SetAllSliderPercentsTo0Command = new RelayCommand(() => SetAllSliderPercents(0), CanEditSetSliders);
        SetAllSliderPercentsTo50Command = new RelayCommand(() => SetAllSliderPercents(50), CanEditSetSliders);
        SetAllSliderPercentsTo100Command = new RelayCommand(() => SetAllSliderPercents(100), CanEditSetSliders);
        SetAllMinPercentsTo0Command = new RelayCommand(() => SetAllMinPercents(0), CanEditSetSliders);
        SetAllMinPercentsTo50Command = new RelayCommand(() => SetAllMinPercents(50), CanEditSetSliders);
        SetAllMinPercentsTo100Command = new RelayCommand(() => SetAllMinPercents(100), CanEditSetSliders);
        SetAllMaxPercentsTo0Command = new RelayCommand(() => SetAllMaxPercents(0), CanEditSetSliders);
        SetAllMaxPercentsTo50Command = new RelayCommand(() => SetAllMaxPercents(50), CanEditSetSliders);
        SetAllMaxPercentsTo100Command = new RelayCommand(() => SetAllMaxPercents(100), CanEditSetSliders);
    }

    public ObservableCollection<SliderPreset> Presets => project.SliderPresets;

    public ObservableCollection<SetSliderInspectorRowViewModel> SetSliderRows { get; } = new();

    public IReadOnlyList<string> ProfileNames => profileCatalog.ProfileNames;

    public ICommand ImportPresetsCommand { get; }

    public ICommand RenameSelectedPresetCommand { get; }

    public ICommand DuplicateSelectedPresetCommand { get; }

    public ICommand RemoveSelectedPresetCommand { get; }

    public ICommand ClearPresetsCommand { get; }

    public ICommand GenerateTemplatesCommand { get; }

    public ICommand CopyGeneratedTemplatesCommand { get; }

    public ICommand CopySelectedBosJsonCommand { get; }

    public ICommand SetAllSliderPercentsTo0Command { get; }

    public ICommand SetAllSliderPercentsTo50Command { get; }

    public ICommand SetAllSliderPercentsTo100Command { get; }

    public ICommand SetAllMinPercentsTo0Command { get; }

    public ICommand SetAllMinPercentsTo50Command { get; }

    public ICommand SetAllMinPercentsTo100Command { get; }

    public ICommand SetAllMaxPercentsTo0Command { get; }

    public ICommand SetAllMaxPercentsTo50Command { get; }

    public ICommand SetAllMaxPercentsTo100Command { get; }

    public SliderPreset? SelectedPreset
    {
        get => selectedPreset;
        set
        {
            if (ReferenceEquals(selectedPreset, value))
            {
                return;
            }

            if (selectedPreset is not null)
            {
                selectedPreset.PropertyChanged -= OnSelectedPresetChanged;
                selectedPreset.SetSliders.CollectionChanged -= OnSelectedPresetSlidersCollectionChanged;
                selectedPreset.MissingDefaultSetSliders.CollectionChanged -= OnSelectedPresetSlidersCollectionChanged;
            }

            this.RaiseAndSetIfChanged(ref selectedPreset, value);

            if (selectedPreset is not null)
            {
                selectedPreset.PropertyChanged += OnSelectedPresetChanged;
                selectedPreset.SetSliders.CollectionChanged += OnSelectedPresetSlidersCollectionChanged;
                selectedPreset.MissingDefaultSetSliders.CollectionChanged += OnSelectedPresetSlidersCollectionChanged;
                PresetNameInput = selectedPreset.Name;
                SetSelectedProfileNameFromPreset(selectedPreset.ProfileName);
                RefreshSelectedPresetMissingDefaults(SelectedProfileName);
            }
            else
            {
                PresetNameInput = string.Empty;
            }

            RebuildSetSliderRows();
            RefreshPreview();
            RefreshSelectedBosJson();
            RaiseCommandStatesChanged();
        }
    }

    public string SelectedProfileName
    {
        get => selectedProfileName;
        set
        {
            var resolvedName = profileCatalog.GetProfile(value).Name;
            if (string.Equals(selectedProfileName, resolvedName, StringComparison.Ordinal))
            {
                return;
            }

            this.RaiseAndSetIfChanged(ref selectedProfileName, resolvedName);

            if (!syncingProfileFromPreset && SelectedPreset is not null)
            {
                SelectedPreset.ProfileName = resolvedName;
            }

            RefreshSelectedPresetMissingDefaults(resolvedName);
            RebuildSetSliderRows();
            RefreshSetSliderRowPreviews();
            RefreshPreview();
            RefreshSelectedBosJson();
        }
    }

    public string PresetNameInput
    {
        get => presetNameInput;
        set => this.RaiseAndSetIfChanged(ref presetNameInput, value ?? string.Empty);
    }

    public string PreviewTemplateText
    {
        get => previewTemplateText;
        private set => this.RaiseAndSetIfChanged(ref previewTemplateText, value);
    }

    public string GeneratedTemplateText
    {
        get => generatedTemplateText;
        private set => this.RaiseAndSetIfChanged(ref generatedTemplateText, value);
    }

    public string SelectedBosJsonText
    {
        get => selectedBosJsonText;
        private set => this.RaiseAndSetIfChanged(ref selectedBosJsonText, value);
    }

    public string ValidationMessage
    {
        get => validationMessage;
        private set => this.RaiseAndSetIfChanged(ref validationMessage, value);
    }

    public string StatusMessage
    {
        get => statusMessage;
        private set => this.RaiseAndSetIfChanged(ref statusMessage, value);
    }

    public bool OmitRedundantSliders
    {
        get => omitRedundantSliders;
        set
        {
            if (omitRedundantSliders == value)
            {
                return;
            }

            this.RaiseAndSetIfChanged(ref omitRedundantSliders, value);
            GeneratedTemplateText = string.Empty;
            RefreshPreview();
        }
    }

    public bool IsBusy
    {
        get => isBusy;
        private set
        {
            if (isBusy == value)
            {
                return;
            }

            this.RaiseAndSetIfChanged(ref isBusy, value);
            RaiseCommandStatesChanged();
        }
    }

    public async Task ImportPresetsAsync(CancellationToken cancellationToken = default)
    {
        if (IsBusy)
        {
            return;
        }

        IsBusy = true;
        ValidationMessage = string.Empty;
        StatusMessage = string.Empty;

        try
        {
            var files = await filePicker.PickXmlPresetFilesAsync(cancellationToken);
            if (files.Count == 0)
            {
                StatusMessage = "No preset files selected.";
                return;
            }

            var import = await Task.Run(() => parser.ParseFiles(files), cancellationToken);
            foreach (var preset in import.Presets)
            {
                preset.ProfileName = SelectedProfileName;
                AddOrUpdatePreset(preset);
            }

            SortPresets();
            if (import.Presets.Count > 0)
            {
                SelectedPreset = Presets.FirstOrDefault();
            }

            StatusMessage = FormatImportStatus(import);
        }
        finally
        {
            IsBusy = false;
        }
    }

    public bool TryRenameSelectedPreset(string newName)
    {
        if (SelectedPreset is null)
        {
            return false;
        }

        if (!TryValidatePresetName(newName, SelectedPreset, out var normalizedName))
        {
            return false;
        }

        SelectedPreset.Name = normalizedName;
        PresetNameInput = normalizedName;
        SortPresets();
        ValidationMessage = string.Empty;
        RefreshPreview();
        return true;
    }

    public bool TryDuplicateSelectedPreset(string newName)
    {
        if (SelectedPreset is null)
        {
            return false;
        }

        if (!TryValidatePresetName(newName, existingPresetToIgnore: null, out var normalizedName))
        {
            return false;
        }

        var duplicate = ClonePreset(SelectedPreset, normalizedName);
        Presets.Add(duplicate);
        SortPresets();
        SelectedPreset = duplicate;
        ValidationMessage = string.Empty;
        return true;
    }

    public bool RemoveSelectedPreset()
    {
        if (SelectedPreset is null)
        {
            return false;
        }

        var currentIndex = Presets.IndexOf(SelectedPreset);
        var removed = project.RemoveSliderPreset(SelectedPreset.Name);
        if (!removed)
        {
            return false;
        }

        SelectedPreset = Presets.Count == 0
            ? null
            : Presets[Math.Min(currentIndex, Presets.Count - 1)];
        GeneratedTemplateText = string.Empty;
        RaiseCommandStatesChanged();
        return true;
    }

    public void ClearPresets()
    {
        foreach (var target in project.CustomMorphTargets)
        {
            target.ClearSliderPresets();
        }

        foreach (var npc in project.MorphedNpcs)
        {
            npc.ClearSliderPresets();
        }

        Presets.Clear();
        SelectedPreset = null;
        GeneratedTemplateText = string.Empty;
        StatusMessage = string.Empty;
        ValidationMessage = string.Empty;
        RaiseCommandStatesChanged();
    }

    public void SortPresets()
    {
        project.SortPresets();
        RaiseCommandStatesChanged();
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
        StatusMessage = "Generated " + Presets.Count.ToString(System.Globalization.CultureInfo.InvariantCulture)
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
        foreach (var row in SetSliderRows)
        {
            row.SetBothPercents(value);
        }

        RefreshAfterSetSliderEdit();
    }

    private void SetAllMinPercents(int value)
    {
        foreach (var row in SetSliderRows)
        {
            row.SetMinPercent(value);
        }

        RefreshAfterSetSliderEdit();
    }

    private void SetAllMaxPercents(int value)
    {
        foreach (var row in SetSliderRows)
        {
            row.SetMaxPercent(value);
        }

        RefreshAfterSetSliderEdit();
    }

    private void ReportCommandFailure(string action, Exception exception)
    {
        StatusMessage = action + " failed: " + FormatExceptionMessage(exception);
    }

    private void AddOrUpdatePreset(SliderPreset importedPreset)
    {
        var existingPreset = project.FindSliderPreset(importedPreset.Name);
        if (existingPreset is null)
        {
            Presets.Add(ClonePreset(importedPreset, importedPreset.Name));
            return;
        }

        existingPreset.SetSliders.Clear();
        existingPreset.MissingDefaultSetSliders.Clear();
        foreach (var slider in importedPreset.SetSliders)
        {
            existingPreset.AddSetSlider(CloneSetSlider(slider));
        }

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
            profileCatalog.GetProfile(SelectedProfileName),
            OmitRedundantSliders);
    }

    private void RefreshSelectedBosJson()
    {
        if (SelectedPreset is null)
        {
            SelectedBosJsonText = string.Empty;
        }
        else
        {
            SelectedBosJsonText = templateGenerationService.PreviewBosJson(
                SelectedPreset,
                profileCatalog.GetProfile(SelectedProfileName));
        }

        RaiseCanExecuteChanged(CopySelectedBosJsonCommand);
    }

    private void OnSelectedPresetChanged(object? sender, PropertyChangedEventArgs args)
    {
        if (args.PropertyName == nameof(SliderPreset.ProfileName))
        {
            SetSelectedProfileNameFromPreset(SelectedPreset?.ProfileName);
        }

        RefreshPreview();
        RefreshSelectedBosJson();
        RefreshSetSliderRowPreviews();
    }

    private void OnSelectedPresetSlidersCollectionChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        RebuildSetSliderRows();
        RefreshAfterSetSliderEdit();
    }

    private void RefreshSelectedPresetMissingDefaults(string profileName)
    {
        if (SelectedPreset is null)
        {
            return;
        }

        SelectedPreset.RefreshMissingDefaultSetSliders(profileCatalog.GetProfile(profileName).DefaultSliderNames);
    }

    private void SetSelectedProfileNameFromPreset(string? profileName)
    {
        syncingProfileFromPreset = true;
        try
        {
            SelectedProfileName = profileName ?? profileCatalog.DefaultProfile.Name;
        }
        finally
        {
            syncingProfileFromPreset = false;
        }
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

    private void RaiseCommandStatesChanged()
    {
        RaiseCanExecuteChanged(ImportPresetsCommand);
        RaiseCanExecuteChanged(RenameSelectedPresetCommand);
        RaiseCanExecuteChanged(DuplicateSelectedPresetCommand);
        RaiseCanExecuteChanged(RemoveSelectedPresetCommand);
        RaiseCanExecuteChanged(ClearPresetsCommand);
        RaiseCanExecuteChanged(GenerateTemplatesCommand);
        RaiseCanExecuteChanged(CopySelectedBosJsonCommand);
        RaiseCanExecuteChanged(SetAllSliderPercentsTo0Command);
        RaiseCanExecuteChanged(SetAllSliderPercentsTo50Command);
        RaiseCanExecuteChanged(SetAllSliderPercentsTo100Command);
        RaiseCanExecuteChanged(SetAllMinPercentsTo0Command);
        RaiseCanExecuteChanged(SetAllMinPercentsTo50Command);
        RaiseCanExecuteChanged(SetAllMinPercentsTo100Command);
        RaiseCanExecuteChanged(SetAllMaxPercentsTo0Command);
        RaiseCanExecuteChanged(SetAllMaxPercentsTo50Command);
        RaiseCanExecuteChanged(SetAllMaxPercentsTo100Command);
    }

    private void RebuildSetSliderRows()
    {
        foreach (var row in SetSliderRows)
        {
            row.Dispose();
        }

        SetSliderRows.Clear();
        if (SelectedPreset is null)
        {
            RaiseCommandStatesChanged();
            return;
        }

        var sliders = SelectedPreset.SetSliders
            .Concat(SelectedPreset.MissingDefaultSetSliders)
            .OrderBy(slider => slider.Name, StringComparer.OrdinalIgnoreCase)
            .ToArray();
        foreach (var slider in sliders)
        {
            SetSliderRows.Add(new SetSliderInspectorRowViewModel(
                slider,
                templateGenerationService,
                () => profileCatalog.GetProfile(SelectedProfileName),
                RefreshAfterSetSliderEdit));
        }

        RaiseCommandStatesChanged();
    }

    private void RefreshAfterSetSliderEdit()
    {
        RefreshPreview();
        RefreshSelectedBosJson();
        RefreshSetSliderRowPreviews();
    }

    private void RefreshSetSliderRowPreviews()
    {
        foreach (var row in SetSliderRows)
        {
            row.RefreshPreview();
        }
    }

    private bool CanEditSetSliders()
    {
        return SetSliderRows.Count > 0;
    }

    private static void RaiseCanExecuteChanged(ICommand command)
    {
        switch (command)
        {
            case RelayCommand relayCommand:
                relayCommand.RaiseCanExecuteChanged();
                break;
            case AsyncRelayCommand asyncRelayCommand:
                asyncRelayCommand.RaiseCanExecuteChanged();
                break;
        }
    }

    private static SliderPreset ClonePreset(SliderPreset source, string name)
    {
        var clone = new SliderPreset(name, source.ProfileName);
        foreach (var slider in source.SetSliders)
        {
            clone.AddSetSlider(CloneSetSlider(slider));
        }

        foreach (var slider in source.MissingDefaultSetSliders)
        {
            clone.AddSetSlider(CloneSetSlider(slider));
        }

        return clone;
    }

    private static SetSlider CloneSetSlider(SetSlider source)
    {
        return new SetSlider(source.Name)
        {
            Enabled = source.Enabled,
            ValueSmall = source.ValueSmall,
            ValueBig = source.ValueBig,
            PercentMin = source.PercentMin,
            PercentMax = source.PercentMax,
        };
    }

    private static string NormalizePresetName(string value)
    {
        return (value ?? string.Empty).Trim().Replace('.', ' ');
    }

    private static string FormatExceptionMessage(Exception exception)
    {
        return string.IsNullOrWhiteSpace(exception.Message)
            ? exception.GetType().Name
            : exception.Message;
    }

    private static string FormatImportStatus(BodySlideXmlImportResult import)
    {
        var status = "Imported " + import.Presets.Count.ToString(System.Globalization.CultureInfo.InvariantCulture)
            + " preset" + (import.Presets.Count == 1 ? "." : "s.");
        if (import.Diagnostics.Count > 0)
        {
            status += " " + import.Diagnostics.Count.ToString(System.Globalization.CultureInfo.InvariantCulture)
                + " issue" + (import.Diagnostics.Count == 1 ? " was" : "s were") + " skipped.";
        }

        return status;
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken)
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
}
