using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Globalization;
using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using ReactiveUI;

namespace BS2BG.App.ViewModels;

public sealed class MorphsViewModel : ReactiveObject
{
    private readonly ProjectModel project;
    private readonly NpcTextParser npcTextParser;
    private readonly MorphAssignmentService assignmentService;
    private readonly MorphGenerationService morphGenerationService;
    private readonly INpcTextFilePicker npcTextFilePicker;
    private readonly IClipboardService clipboardService;
    private readonly INpcImageLookupService imageLookupService;
    private readonly IImageViewService imageViewService;
    private readonly INoPresetNotificationService noPresetNotificationService;
    private readonly Dictionary<Npc, int> npcPropertySubscriptions = new();
    private CustomMorphTarget? selectedCustomTarget;
    private Npc? selectedNpc;
    private Npc? selectedImportedNpc;
    private SliderPreset? selectedAvailablePreset;
    private SliderPreset? selectedAssignedPreset;
    private MorphTargetBase? subscribedTarget;
    private string targetNameInput = string.Empty;
    private string searchText = string.Empty;
    private string npcDatabaseSearchText = string.Empty;
    private string generatedMorphsText = string.Empty;
    private string statusMessage = string.Empty;
    private string validationMessage = string.Empty;
    private bool assignRandomOnAdd;
    private bool isBusy;

    public MorphsViewModel()
        : this(
            new ProjectModel(),
            new NpcTextParser(),
            new MorphAssignmentService(new RandomAssignmentProvider()),
            new MorphGenerationService(),
            new EmptyNpcTextFilePicker(),
            new EmptyClipboardService(),
            new NpcImageLookupService(),
            new NullImageViewService(),
            new NullNoPresetNotificationService())
    {
    }

    public MorphsViewModel(
        ProjectModel project,
        NpcTextParser npcTextParser,
        MorphAssignmentService assignmentService,
        MorphGenerationService morphGenerationService,
        INpcTextFilePicker npcTextFilePicker,
        IClipboardService clipboardService,
        INpcImageLookupService? imageLookupService = null,
        IImageViewService? imageViewService = null,
        INoPresetNotificationService? noPresetNotificationService = null)
    {
        this.project = project ?? throw new ArgumentNullException(nameof(project));
        this.npcTextParser = npcTextParser ?? throw new ArgumentNullException(nameof(npcTextParser));
        this.assignmentService = assignmentService ?? throw new ArgumentNullException(nameof(assignmentService));
        this.morphGenerationService = morphGenerationService
            ?? throw new ArgumentNullException(nameof(morphGenerationService));
        this.npcTextFilePicker = npcTextFilePicker ?? throw new ArgumentNullException(nameof(npcTextFilePicker));
        this.clipboardService = clipboardService ?? throw new ArgumentNullException(nameof(clipboardService));
        this.imageLookupService = imageLookupService ?? new NpcImageLookupService();
        this.imageViewService = imageViewService ?? new NullImageViewService();
        this.noPresetNotificationService = noPresetNotificationService ?? new NullNoPresetNotificationService();

        project.MorphedNpcs.CollectionChanged += OnNpcsChanged;
        project.CustomMorphTargets.CollectionChanged += OnCustomTargetsChanged;
        project.SliderPresets.CollectionChanged += OnPresetsChanged;
        NpcDatabase.CollectionChanged += OnNpcDatabaseChanged;
        RefreshNpcSubscriptions();
        RefreshVisibleNpcs();
        RefreshVisibleNpcDatabase();

        ImportNpcsCommand = new AsyncRelayCommand(
            ImportNpcsAsync,
            () => !IsBusy,
            exception => ReportCommandFailure("Import NPCs", exception));
        AddCustomTargetCommand = new RelayCommand(() => AddCustomTarget(), () => !IsBusy);
        RemoveCustomTargetCommand = new RelayCommand(
            () => RemoveSelectedCustomTarget(),
            () => SelectedCustomTarget is not null);
        ClearCustomTargetsCommand = new RelayCommand(
            ClearCustomTargets,
            () => CustomTargets.Count > 0);
        AddSelectedPresetToTargetCommand = new RelayCommand(
            () => AddSelectedPresetToTarget(),
            () => SelectedTarget is not null && SelectedAvailablePreset is not null);
        AddAllPresetsToTargetCommand = new RelayCommand(
            () => AddAllPresetsToTarget(),
            () => SelectedTarget is not null && Presets.Count > 0);
        RemoveSelectedPresetFromTargetCommand = new RelayCommand(
            () => RemoveSelectedPresetFromTarget(),
            () => SelectedTarget is not null && SelectedAssignedPreset is not null);
        ClearTargetPresetsCommand = new RelayCommand(
            () => ClearTargetPresets(),
            () => SelectedTarget?.SliderPresets.Count > 0);
        AddSelectedNpcCommand = new RelayCommand(
            () => AddSelectedNpc(),
            () => SelectedImportedNpc is not null);
        AddAllVisibleImportedNpcsCommand = new RelayCommand(
            () => AddAllVisibleImportedNpcs(),
            () => VisibleNpcDatabase.Count > 0);
        RemoveSelectedNpcCommand = new RelayCommand(
            () => RemoveSelectedNpc(),
            () => SelectedNpc is not null);
        ClearVisibleNpcsCommand = new RelayCommand(
            () => ClearVisibleNpcs(),
            () => VisibleNpcs.Count > 0);
        FillEmptyNpcsCommand = new RelayCommand(
            () => FillEmptyFromSelectedPreset(),
            () => VisibleNpcs.Any(npc => npc.SliderPresets.Count == 0) && Presets.Count > 0);
        ClearAssignmentsCommand = new RelayCommand(
            () => ClearVisibleNpcAssignments(),
            () => VisibleNpcs.Any(npc => npc.SliderPresets.Count > 0));
        GenerateMorphsCommand = new RelayCommand(
            GenerateMorphs,
            () => CustomTargets.Count > 0 || Npcs.Count > 0);
        CopyGeneratedMorphsCommand = new AsyncRelayCommand(
            CopyGeneratedMorphsAsync,
            reportException: exception => ReportCommandFailure("Copy generated morphs", exception));
        ViewSelectedNpcImageCommand = new RelayCommand(
            ViewSelectedNpcImage,
            () => SelectedImageNpc is not null);
    }

    public ObservableCollection<SliderPreset> Presets => project.SliderPresets;

    public ObservableCollection<CustomMorphTarget> CustomTargets => project.CustomMorphTargets;

    public ObservableCollection<Npc> Npcs => project.MorphedNpcs;

    public ObservableCollection<Npc> NpcDatabase { get; } = new();

    public ObservableCollection<Npc> VisibleNpcs { get; } = new();

    public ObservableCollection<Npc> VisibleNpcDatabase { get; } = new();

    public ObservableCollection<MorphTargetBase> NoPresetTargets { get; } = new();

    public ICommand ImportNpcsCommand { get; }

    public ICommand AddCustomTargetCommand { get; }

    public ICommand RemoveCustomTargetCommand { get; }

    public ICommand ClearCustomTargetsCommand { get; }

    public ICommand AddSelectedPresetToTargetCommand { get; }

    public ICommand AddAllPresetsToTargetCommand { get; }

    public ICommand RemoveSelectedPresetFromTargetCommand { get; }

    public ICommand ClearTargetPresetsCommand { get; }

    public ICommand AddSelectedNpcCommand { get; }

    public ICommand AddAllVisibleImportedNpcsCommand { get; }

    public ICommand RemoveSelectedNpcCommand { get; }

    public ICommand ClearVisibleNpcsCommand { get; }

    public ICommand FillEmptyNpcsCommand { get; }

    public ICommand ClearAssignmentsCommand { get; }

    public ICommand GenerateMorphsCommand { get; }

    public ICommand CopyGeneratedMorphsCommand { get; }

    public ICommand ViewSelectedNpcImageCommand { get; }

    public CustomMorphTarget? SelectedCustomTarget
    {
        get => selectedCustomTarget;
        set
        {
            if (ReferenceEquals(selectedCustomTarget, value))
            {
                return;
            }

            this.RaiseAndSetIfChanged(ref selectedCustomTarget, value);
            if (value is not null)
            {
                SelectedNpc = null;
                TargetNameInput = value.Name;
            }

            RefreshSelectedTargetSubscription();
        }
    }

    public Npc? SelectedNpc
    {
        get => selectedNpc;
        set
        {
            if (ReferenceEquals(selectedNpc, value))
            {
                return;
            }

            this.RaiseAndSetIfChanged(ref selectedNpc, value);
            if (value is not null)
            {
                SelectedCustomTarget = null;
            }

            RefreshSelectedTargetSubscription();
            RaiseCommandStatesChanged();
        }
    }

    public Npc? SelectedImportedNpc
    {
        get => selectedImportedNpc;
        set
        {
            this.RaiseAndSetIfChanged(ref selectedImportedNpc, value);
            RaiseCommandStatesChanged();
        }
    }

    public SliderPreset? SelectedAvailablePreset
    {
        get => selectedAvailablePreset;
        set
        {
            this.RaiseAndSetIfChanged(ref selectedAvailablePreset, value);
            RaiseCommandStatesChanged();
        }
    }

    public SliderPreset? SelectedAssignedPreset
    {
        get => selectedAssignedPreset;
        set
        {
            this.RaiseAndSetIfChanged(ref selectedAssignedPreset, value);
            RaiseCommandStatesChanged();
        }
    }

    public MorphTargetBase? SelectedTarget => (MorphTargetBase?)SelectedCustomTarget ?? SelectedNpc;

    private Npc? SelectedImageNpc => SelectedNpc ?? SelectedImportedNpc;

    public IEnumerable<SliderPreset> SelectedTargetPresets => SelectedTarget?.SliderPresets
        ?? Enumerable.Empty<SliderPreset>();

    public string SelectedTargetName => SelectedTarget?.Name ?? "-null-";

    public string TargetPresetCountText => (SelectedTarget?.SliderPresets.Count ?? 0)
        .ToString(CultureInfo.InvariantCulture);

    public string NpcCountBadgeText => "(" + Npcs.Count.ToString(CultureInfo.InvariantCulture) + ")";

    public string NpcDatabaseCountBadgeText => "(" + NpcDatabase.Count.ToString(CultureInfo.InvariantCulture) + ")";

    public string TargetNameInput
    {
        get => targetNameInput;
        set => this.RaiseAndSetIfChanged(ref targetNameInput, value ?? string.Empty);
    }

    public string SearchText
    {
        get => searchText;
        set
        {
            var newValue = value ?? string.Empty;
            if (string.Equals(searchText, newValue, StringComparison.Ordinal))
            {
                return;
            }

            this.RaiseAndSetIfChanged(ref searchText, newValue);
            RefreshVisibleNpcs();
        }
    }

    public string NpcDatabaseSearchText
    {
        get => npcDatabaseSearchText;
        set
        {
            var newValue = value ?? string.Empty;
            if (string.Equals(npcDatabaseSearchText, newValue, StringComparison.Ordinal))
            {
                return;
            }

            this.RaiseAndSetIfChanged(ref npcDatabaseSearchText, newValue);
            RefreshVisibleNpcDatabase();
        }
    }

    public string GeneratedMorphsText
    {
        get => generatedMorphsText;
        private set => this.RaiseAndSetIfChanged(ref generatedMorphsText, value);
    }

    public string StatusMessage
    {
        get => statusMessage;
        private set => this.RaiseAndSetIfChanged(ref statusMessage, value);
    }

    public string ValidationMessage
    {
        get => validationMessage;
        private set => this.RaiseAndSetIfChanged(ref validationMessage, value);
    }

    public bool AssignRandomOnAdd
    {
        get => assignRandomOnAdd;
        set => this.RaiseAndSetIfChanged(ref assignRandomOnAdd, value);
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

    public async Task ImportNpcsAsync(CancellationToken cancellationToken = default)
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
            var files = await npcTextFilePicker.PickNpcTextFilesAsync(cancellationToken);
            if (files.Count == 0)
            {
                StatusMessage = "No NPC files selected.";
                return;
            }

            var importedCount = 0;
            var diagnosticCount = 0;
            var fallbackCount = 0;
            foreach (var file in files)
            {
                var result = await Task.Run(() => npcTextParser.ParseFile(file), cancellationToken);
                importedCount += AddNpcsToDatabase(result.Npcs);
                diagnosticCount += result.Diagnostics.Count;
                if (result.UsedFallbackEncoding)
                {
                    fallbackCount++;
                }
            }

            StatusMessage = FormatImportStatus(importedCount, diagnosticCount, fallbackCount);
        }
        finally
        {
            IsBusy = false;
        }
    }

    public bool AddCustomTarget()
    {
        var added = assignmentService.TryAddCustomTarget(
            project,
            TargetNameInput,
            out var target,
            out var error);
        if (!added)
        {
            ValidationMessage = error;
            return false;
        }

        SelectedCustomTarget = target;
        TargetNameInput = string.Empty;
        ValidationMessage = string.Empty;
        StatusMessage = "Added custom target.";
        RaiseCommandStatesChanged();
        return true;
    }

    public bool RemoveSelectedCustomTarget()
    {
        var current = SelectedCustomTarget;
        if (!assignmentService.RemoveCustomTarget(project, current))
        {
            return false;
        }

        SelectedCustomTarget = CustomTargets.FirstOrDefault();
        StatusMessage = "Removed custom target.";
        RaiseCommandStatesChanged();
        return true;
    }

    public void ClearCustomTargets()
    {
        foreach (var target in CustomTargets)
        {
            target.ClearSliderPresets();
        }

        CustomTargets.Clear();
        SelectedCustomTarget = null;
        StatusMessage = "Cleared custom targets.";
        RaiseCommandStatesChanged();
    }

    public bool AddSelectedPresetToTarget()
    {
        var added = assignmentService.AddPresetToTarget(SelectedTarget, SelectedAvailablePreset);
        if (added)
        {
            StatusMessage = "Added preset to target.";
            RaiseSelectedTargetChanged();
        }

        return added;
    }

    public int AddAllPresetsToTarget()
    {
        var added = assignmentService.AddAllPresetsToTarget(SelectedTarget, Presets);
        if (added > 0)
        {
            StatusMessage = "Added all presets to target.";
            RaiseSelectedTargetChanged();
        }

        return added;
    }

    public bool RemoveSelectedPresetFromTarget()
    {
        var removed = assignmentService.RemovePresetFromTarget(SelectedTarget, SelectedAssignedPreset);
        if (removed)
        {
            SelectedAssignedPreset = null;
            StatusMessage = "Removed preset from target.";
            RaiseSelectedTargetChanged();
        }

        return removed;
    }

    public int ClearTargetPresets()
    {
        var cleared = assignmentService.ClearTargetPresets(SelectedTarget);
        if (cleared > 0)
        {
            SelectedAssignedPreset = null;
            StatusMessage = "Cleared target presets.";
            RaiseSelectedTargetChanged();
        }

        return cleared;
    }

    public bool AddSelectedNpc()
    {
        var added = assignmentService.AddNpcToMorphs(project, SelectedImportedNpc, AssignRandomOnAdd);
        if (added)
        {
            SelectedNpc = SelectedImportedNpc;
            StatusMessage = "Added NPC.";
            RefreshVisibleNpcs();
        }
        else if (SelectedImportedNpc is not null)
        {
            StatusMessage = "NPC is already in the list of morph targets.";
        }

        RaiseCommandStatesChanged();
        return added;
    }

    public int AddAllVisibleImportedNpcs()
    {
        var added = assignmentService.AddNpcsToMorphs(project, VisibleNpcDatabase.ToArray(), AssignRandomOnAdd);
        StatusMessage = "Added " + added.ToString(CultureInfo.InvariantCulture)
            + " NPC" + (added == 1 ? "." : "s.");
        RefreshVisibleNpcs();
        RaiseCommandStatesChanged();
        return added;
    }

    public bool RemoveSelectedNpc()
    {
        var removed = assignmentService.RemoveNpc(project, SelectedNpc);
        if (removed)
        {
            SelectedNpc = Npcs.FirstOrDefault();
            StatusMessage = "Removed NPC.";
            RefreshVisibleNpcs();
        }

        RaiseCommandStatesChanged();
        return removed;
    }

    public int ClearVisibleNpcs()
    {
        var visible = VisibleNpcs.ToArray();
        foreach (var npc in visible)
        {
            assignmentService.RemoveNpc(project, npc);
        }

        RefreshVisibleNpcs();
        StatusMessage = "Removed " + visible.Length.ToString(CultureInfo.InvariantCulture)
            + " visible NPC" + (visible.Length == 1 ? "." : "s.");
        RaiseCommandStatesChanged();
        return visible.Length;
    }

    public int FillEmptyVisibleNpcs(IEnumerable<SliderPreset> presets)
    {
        var candidates = presets?.ToArray() ?? Array.Empty<SliderPreset>();
        var filled = assignmentService.FillEmptyNpcs(VisibleNpcs.ToArray(), candidates);
        StatusMessage = "Filled " + filled.ToString(CultureInfo.InvariantCulture)
            + " empty NPC" + (filled == 1 ? "." : "s.");
        RaiseCommandStatesChanged();
        RaiseSelectedTargetChanged();
        return filled;
    }

    public int ClearVisibleNpcAssignments()
    {
        var cleared = assignmentService.ClearAssignments(VisibleNpcs.ToArray());
        StatusMessage = "Cleared assignments from " + cleared.ToString(CultureInfo.InvariantCulture)
            + " NPC" + (cleared == 1 ? "." : "s.");
        RaiseCommandStatesChanged();
        RaiseSelectedTargetChanged();
        return cleared;
    }

    public void GenerateMorphs()
    {
        if (CustomTargets.Count == 0 && Npcs.Count == 0)
        {
            GeneratedMorphsText = string.Empty;
            StatusMessage = "No morph targets to generate.";
            return;
        }

        var result = morphGenerationService.GenerateMorphs(project);
        GeneratedMorphsText = result.Text;
        NoPresetTargets.Clear();
        foreach (var target in result.TargetsWithoutPresets)
        {
            NoPresetTargets.Add(target);
        }

        StatusMessage = result.TargetsWithoutPresets.Count == 0
            ? "Generated morphs."
            : "Generated morphs. " + result.TargetsWithoutPresets.Count.ToString(CultureInfo.InvariantCulture)
                + " target" + (result.TargetsWithoutPresets.Count == 1 ? " has" : "s have")
                + " no presets.";

        if (NoPresetTargets.Count > 0)
        {
            noPresetNotificationService.ShowTargetsWithoutPresets(NoPresetTargets.ToArray());
        }
    }

    public void ViewSelectedNpcImage()
    {
        var npc = SelectedImageNpc;
        if (npc is null)
        {
            StatusMessage = "Select an NPC before viewing an image.";
            return;
        }

        var imagePath = imageLookupService.FindImagePath(npc);
        imageViewService.ShowImage(npc, imagePath);
        StatusMessage = imagePath is null
            ? "No image found for " + npc.Name + "."
            : "Opened image for " + npc.Name + ".";
    }

    public async Task CopyGeneratedMorphsAsync(CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(GeneratedMorphsText))
        {
            StatusMessage = "Generate morphs before copying.";
            return;
        }

        await clipboardService.SetTextAsync(GeneratedMorphsText, cancellationToken);
        StatusMessage = "Generated morphs copied.";
    }

    private int AddNpcsToDatabase(IEnumerable<Npc> npcs)
    {
        var added = 0;
        foreach (var npc in npcs)
        {
            if (NpcDatabase.Any(existing => IsSameNpc(existing, npc)))
            {
                continue;
            }

            NpcDatabase.Add(npc);
            added++;
        }

        return added;
    }

    private int FillEmptyFromSelectedPreset()
    {
        var candidates = SelectedAvailablePreset is null
            ? Presets.ToArray()
            : new[] { SelectedAvailablePreset };
        return FillEmptyVisibleNpcs(candidates);
    }

    private void RefreshVisibleNpcs()
    {
        RefreshFilteredCollection(Npcs, VisibleNpcs, SearchText);
        this.RaisePropertyChanged(nameof(NpcCountBadgeText));
        RaiseCommandStatesChanged();
    }

    private void RefreshVisibleNpcDatabase()
    {
        RefreshFilteredCollection(NpcDatabase, VisibleNpcDatabase, NpcDatabaseSearchText);
        this.RaisePropertyChanged(nameof(NpcDatabaseCountBadgeText));
        RaiseCommandStatesChanged();
    }

    private static void RefreshFilteredCollection(
        IEnumerable<Npc> source,
        ObservableCollection<Npc> target,
        string filterText)
    {
        target.Clear();
        foreach (var npc in source.Where(npc => MatchesFilter(npc, filterText)))
        {
            target.Add(npc);
        }
    }

    private static bool MatchesFilter(Npc npc, string filterText)
    {
        if (string.IsNullOrWhiteSpace(filterText))
        {
            return true;
        }

        return Contains(npc.Name, filterText)
            || Contains(npc.Mod, filterText)
            || Contains(npc.Race, filterText)
            || Contains(npc.EditorId, filterText)
            || Contains(npc.FormId, filterText)
            || Contains(npc.SliderPresetsText, filterText);
    }

    private static bool Contains(string value, string filterText)
    {
        return value.Contains(filterText, StringComparison.OrdinalIgnoreCase);
    }

    private void OnNpcsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        UpdateNpcSubscriptions(args);
        RefreshVisibleNpcs();
    }

    private void OnCustomTargetsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        if (SelectedCustomTarget is not null && !CustomTargets.Contains(SelectedCustomTarget))
        {
            SelectedCustomTarget = CustomTargets.FirstOrDefault();
        }

        RaiseCommandStatesChanged();
    }

    private void OnPresetsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        RaiseCommandStatesChanged();
    }

    private void OnNpcDatabaseChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        RefreshVisibleNpcDatabase();
    }

    private void UpdateNpcSubscriptions(NotifyCollectionChangedEventArgs args)
    {
        if (args.Action == NotifyCollectionChangedAction.Reset)
        {
            RefreshNpcSubscriptions();
            return;
        }

        if (args.OldItems is not null)
        {
            foreach (Npc npc in args.OldItems)
            {
                DetachNpcPropertyChanged(npc);
            }
        }

        if (args.NewItems is not null)
        {
            foreach (Npc npc in args.NewItems)
            {
                AttachNpcPropertyChanged(npc);
            }
        }
    }

    private void RefreshNpcSubscriptions()
    {
        DetachAllNpcPropertyChanged();

        foreach (var npc in Npcs)
        {
            AttachNpcPropertyChanged(npc);
        }
    }

    private void AttachNpcPropertyChanged(Npc npc)
    {
        npc.PropertyChanged += OnNpcPropertyChanged;
        npcPropertySubscriptions.TryGetValue(npc, out var count);
        npcPropertySubscriptions[npc] = count + 1;
    }

    private void DetachNpcPropertyChanged(Npc npc)
    {
        npc.PropertyChanged -= OnNpcPropertyChanged;

        if (!npcPropertySubscriptions.TryGetValue(npc, out var count))
        {
            return;
        }

        if (count == 1)
        {
            npcPropertySubscriptions.Remove(npc);
            return;
        }

        npcPropertySubscriptions[npc] = count - 1;
    }

    private void DetachAllNpcPropertyChanged()
    {
        foreach (var subscription in npcPropertySubscriptions)
        {
            for (var index = 0; index < subscription.Value; index++)
            {
                subscription.Key.PropertyChanged -= OnNpcPropertyChanged;
            }
        }

        npcPropertySubscriptions.Clear();
    }

    private void OnNpcPropertyChanged(object? sender, PropertyChangedEventArgs args)
    {
        if (args.PropertyName is nameof(Npc.Name)
            or nameof(Npc.Mod)
            or nameof(Npc.Race)
            or nameof(Npc.EditorId)
            or nameof(Npc.FormId)
            or nameof(Npc.SliderPresetsText)
            or nameof(Npc.SliderPresets))
        {
            RefreshVisibleNpcs();
        }
    }

    private void RefreshSelectedTargetSubscription()
    {
        if (subscribedTarget is not null)
        {
            subscribedTarget.PropertyChanged -= OnSelectedTargetPropertyChanged;
        }

        subscribedTarget = SelectedTarget;
        if (subscribedTarget is not null)
        {
            subscribedTarget.PropertyChanged += OnSelectedTargetPropertyChanged;
        }

        SelectedAssignedPreset = null;
        RaiseSelectedTargetChanged();
    }

    private void OnSelectedTargetPropertyChanged(object? sender, PropertyChangedEventArgs args)
    {
        if (args.PropertyName is nameof(MorphTargetBase.SliderPresets)
            or nameof(MorphTargetBase.HasPresets)
            or nameof(MorphTargetBase.Name)
            or nameof(Npc.SliderPresetsText))
        {
            RaiseSelectedTargetChanged();
        }
    }

    private void RaiseSelectedTargetChanged()
    {
        this.RaisePropertyChanged(nameof(SelectedTarget));
        this.RaisePropertyChanged(nameof(SelectedTargetName));
        this.RaisePropertyChanged(nameof(SelectedTargetPresets));
        this.RaisePropertyChanged(nameof(TargetPresetCountText));
        RaiseCommandStatesChanged();
    }

    private void RaiseCommandStatesChanged()
    {
        RaiseCanExecuteChanged(ImportNpcsCommand);
        RaiseCanExecuteChanged(AddCustomTargetCommand);
        RaiseCanExecuteChanged(RemoveCustomTargetCommand);
        RaiseCanExecuteChanged(ClearCustomTargetsCommand);
        RaiseCanExecuteChanged(AddSelectedPresetToTargetCommand);
        RaiseCanExecuteChanged(AddAllPresetsToTargetCommand);
        RaiseCanExecuteChanged(RemoveSelectedPresetFromTargetCommand);
        RaiseCanExecuteChanged(ClearTargetPresetsCommand);
        RaiseCanExecuteChanged(AddSelectedNpcCommand);
        RaiseCanExecuteChanged(AddAllVisibleImportedNpcsCommand);
        RaiseCanExecuteChanged(RemoveSelectedNpcCommand);
        RaiseCanExecuteChanged(ClearVisibleNpcsCommand);
        RaiseCanExecuteChanged(FillEmptyNpcsCommand);
        RaiseCanExecuteChanged(ClearAssignmentsCommand);
        RaiseCanExecuteChanged(GenerateMorphsCommand);
        RaiseCanExecuteChanged(ViewSelectedNpcImageCommand);
    }

    private static void RaiseCanExecuteChanged(ICommand? command)
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

    private void ReportCommandFailure(string action, Exception exception)
    {
        StatusMessage = action + " failed: " + FormatExceptionMessage(exception);
    }

    private static string FormatImportStatus(int importedCount, int diagnosticCount, int fallbackCount)
    {
        var status = "Imported " + importedCount.ToString(CultureInfo.InvariantCulture)
            + " NPC" + (importedCount == 1 ? "." : "s.");
        if (diagnosticCount > 0)
        {
            status += " " + diagnosticCount.ToString(CultureInfo.InvariantCulture)
                + " issue" + (diagnosticCount == 1 ? " was" : "s were") + " skipped.";
        }

        if (fallbackCount > 0)
        {
            status += " " + fallbackCount.ToString(CultureInfo.InvariantCulture)
                + " file" + (fallbackCount == 1 ? " used" : "s used") + " fallback decoding.";
        }

        return status;
    }

    private static string FormatExceptionMessage(Exception exception)
    {
        return string.IsNullOrWhiteSpace(exception.Message)
            ? exception.GetType().Name
            : exception.Message;
    }

    private static bool IsSameNpc(Npc left, Npc right)
    {
        return string.Equals(left.Mod, right.Mod, StringComparison.OrdinalIgnoreCase)
            && string.Equals(left.EditorId, right.EditorId, StringComparison.OrdinalIgnoreCase);
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

    private sealed class NullImageViewService : IImageViewService
    {
        public void ShowImage(Npc npc, string? imagePath)
        {
        }
    }

    private sealed class NullNoPresetNotificationService : INoPresetNotificationService
    {
        public void ShowTargetsWithoutPresets(IReadOnlyList<MorphTargetBase> targets)
        {
        }
    }
}
