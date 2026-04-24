using System.Collections.ObjectModel;
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

        ImportPresetsCommand = new AsyncRelayCommand(ImportPresetsAsync, () => !IsBusy);
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
        CopyGeneratedTemplatesCommand = new AsyncRelayCommand(CopyGeneratedTemplatesAsync);
    }

    public ObservableCollection<SliderPreset> Presets => project.SliderPresets;

    public IReadOnlyList<string> ProfileNames => profileCatalog.ProfileNames;

    public ICommand ImportPresetsCommand { get; }

    public ICommand RenameSelectedPresetCommand { get; }

    public ICommand DuplicateSelectedPresetCommand { get; }

    public ICommand RemoveSelectedPresetCommand { get; }

    public ICommand ClearPresetsCommand { get; }

    public ICommand GenerateTemplatesCommand { get; }

    public ICommand CopyGeneratedTemplatesCommand { get; }

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
            }

            this.RaiseAndSetIfChanged(ref selectedPreset, value);

            if (selectedPreset is not null)
            {
                selectedPreset.PropertyChanged += OnSelectedPresetChanged;
                PresetNameInput = selectedPreset.Name;
                SetSelectedProfileNameFromPreset(selectedPreset.ProfileName);
            }
            else
            {
                PresetNameInput = string.Empty;
            }

            RefreshPreview();
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

            RefreshPreview();
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

    private void OnSelectedPresetChanged(object? sender, PropertyChangedEventArgs args)
    {
        if (args.PropertyName == nameof(SliderPreset.ProfileName))
        {
            SetSelectedProfileNameFromPreset(SelectedPreset?.ProfileName);
        }

        RefreshPreview();
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
