using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using ReactiveUI;

namespace BS2BG.App.ViewModels;

public enum NpcFilterColumn
{
    Name,
    Mod,
    Race,
    EditorId,
    FormId,
    Presets
}

public enum PresetCountWarningState
{
    Neutral,
    Warn,
    Error
}

public sealed class MorphsViewModel : ReactiveObject
{
    private readonly MorphAssignmentService assignmentService;
    private readonly IClipboardService clipboardService;
    private readonly INpcImageLookupService imageLookupService;
    private readonly IImageViewService imageViewService;
    private readonly MorphGenerationService morphGenerationService;
    private readonly INoPresetNotificationService noPresetNotificationService;
    private readonly Dictionary<NpcFilterColumn, HashSet<string>> npcColumnAllowedValues = new();
    private readonly Dictionary<NpcFilterColumn, string> npcColumnSearchText = new();
    private readonly Dictionary<Npc, int> npcPropertySubscriptions = new();
    private readonly INpcTextFilePicker npcTextFilePicker;
    private readonly NpcTextParser npcTextParser;
    private readonly ProjectModel project;
    private readonly UndoRedoService undoRedo;
    private bool assignRandomOnAdd;
    private string generatedMorphsText = string.Empty;
    private bool isBusy;
    private bool isNpcRaceFilterOpen;
    private string npcDatabaseSearchText = string.Empty;
    private string searchText = string.Empty;
    private SliderPreset? selectedAssignedPreset;
    private SliderPreset? selectedAvailablePreset;
    private CustomMorphTarget? selectedCustomTarget;
    private Npc? selectedImportedNpc;
    private Npc? selectedNpc;
    private string statusMessage = string.Empty;
    private MorphTargetBase? subscribedTarget;
    private string targetNameInput = string.Empty;
    private string validationMessage = string.Empty;

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
        INoPresetNotificationService? noPresetNotificationService = null,
        UndoRedoService? undoRedo = null)
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
        this.undoRedo = undoRedo ?? new UndoRedoService();

        project.MorphedNpcs.CollectionChanged += OnNpcsChanged;
        project.CustomMorphTargets.CollectionChanged += OnCustomTargetsChanged;
        project.SliderPresets.CollectionChanged += OnPresetsChanged;
        NpcDatabase.CollectionChanged += OnNpcDatabaseChanged;
        SelectedNpcs.CollectionChanged += (_, _) => RaiseCommandStatesChanged();
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
        AssignSelectedNpcsCommand = new RelayCommand(
            () => AssignSelectedNpcs(),
            () => GetSelectedNpcsForCommand().Any() && SelectedAvailablePreset is not null);
        ClearSelectedNpcAssignmentsCommand = new RelayCommand(
            () => ClearSelectedNpcAssignments(),
            () => GetSelectedNpcsForCommand().Any(npc => npc.SliderPresets.Count > 0));
        TrimSelectedTargetTo76Command = new RelayCommand(
            () => TrimSelectedTargetTo76(),
            () => SelectedTarget?.SliderPresets.Count >= 77);
        ToggleNpcRaceFilterCommand = new RelayCommand(() => IsNpcRaceFilterOpen = !IsNpcRaceFilterOpen);
        ClearNpcRaceFilterCommand = new RelayCommand(ClearNpcRaceFilter);
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

    public ObservableCollection<Npc> SelectedNpcs { get; } = new();

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

    public ICommand AssignSelectedNpcsCommand { get; }

    public ICommand ClearSelectedNpcAssignmentsCommand { get; }

    public ICommand TrimSelectedTargetTo76Command { get; }

    public ICommand ToggleNpcRaceFilterCommand { get; }

    public ICommand ClearNpcRaceFilterCommand { get; }

    public ICommand GenerateMorphsCommand { get; }

    public ICommand CopyGeneratedMorphsCommand { get; }

    public ICommand ViewSelectedNpcImageCommand { get; }

    public CustomMorphTarget? SelectedCustomTarget
    {
        get => selectedCustomTarget;
        set
        {
            if (ReferenceEquals(selectedCustomTarget, value)) return;

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
            if (ReferenceEquals(selectedNpc, value)) return;

            this.RaiseAndSetIfChanged(ref selectedNpc, value);
            if (value is not null) SelectedCustomTarget = null;

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

    public PresetCountWarningState TargetPresetWarningState
    {
        get
        {
            var count = SelectedTarget?.SliderPresets.Count ?? 0;
            return count >= 77
                ? PresetCountWarningState.Error
                : count >= 31
                    ? PresetCountWarningState.Warn
                    : PresetCountWarningState.Neutral;
        }
    }

    public bool IsTargetPresetWarningVisible => TargetPresetWarningState != PresetCountWarningState.Neutral;

    public bool IsTargetPresetTrimVisible => TargetPresetWarningState == PresetCountWarningState.Error;

    public string TargetPresetWarningText => TargetPresetWarningState == PresetCountWarningState.Error
        ? "Preset count is over the safe limit."
        : "Preset count is approaching the safe limit.";

    public string NpcCountBadgeText => "(" + Npcs.Count.ToString(CultureInfo.InvariantCulture) + ")";

    public string NpcDatabaseCountBadgeText => "(" + NpcDatabase.Count.ToString(CultureInfo.InvariantCulture) + ")";

    public string TargetNameInput
    {
        get => targetNameInput;
        set
        {
            this.RaiseAndSetIfChanged(ref targetNameInput, value ?? string.Empty);
            RefreshTargetNameValidation();
        }
    }

    public string TargetNameValidationMessage => ValidateCustomTargetName(TargetNameInput);

    [SuppressMessage("Performance", "CA1822:Mark members as static",
        Justification = "Binding-friendly instance property.")]
    public string CustomTargetExamples => "Examples: All|Female, All|Male, Skyrim.esm|Female|NordRace";

    public string SearchText
    {
        get => searchText;
        set
        {
            var newValue = value ?? string.Empty;
            if (string.Equals(searchText, newValue, StringComparison.Ordinal)) return;

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
            if (string.Equals(npcDatabaseSearchText, newValue, StringComparison.Ordinal)) return;

            this.RaiseAndSetIfChanged(ref npcDatabaseSearchText, newValue);
            RefreshVisibleNpcDatabase();
        }
    }

    public bool IsNpcRaceFilterOpen
    {
        get => isNpcRaceFilterOpen;
        set => this.RaiseAndSetIfChanged(ref isNpcRaceFilterOpen, value);
    }

    public string NpcRaceColumnSearchText
    {
        get => GetNpcColumnSearchText(NpcFilterColumn.Race);
        set => SetNpcColumnSearchText(NpcFilterColumn.Race, value);
    }

    public IReadOnlyList<string> NpcRaceColumnValues => GetNpcColumnValues(NpcFilterColumn.Race);

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
            if (isBusy == value) return;

            this.RaiseAndSetIfChanged(ref isBusy, value);
            RaiseCommandStatesChanged();
        }
    }

    public async Task ImportNpcsAsync(CancellationToken cancellationToken = default)
    {
        if (IsBusy) return;

        IsBusy = true;
        ValidationMessage = string.Empty;
        StatusMessage = string.Empty;

        try
        {
            await ImportNpcFilesCoreAsync(
                await npcTextFilePicker.PickNpcTextFilesAsync(cancellationToken),
                "No NPC files selected.",
                cancellationToken);
        }
        finally
        {
            IsBusy = false;
        }
    }

    public async Task ImportNpcFilesAsync(
        IReadOnlyList<string> files,
        CancellationToken cancellationToken = default)
    {
        if (IsBusy) return;

        IsBusy = true;
        ValidationMessage = string.Empty;
        StatusMessage = string.Empty;

        try
        {
            await ImportNpcFilesCoreAsync(files, "No NPC files dropped.", cancellationToken);
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
        undoRedo.Record(
            "Add custom target",
            () =>
            {
                target.ClearSliderPresets();
                project.CustomMorphTargets.Remove(target);
                SelectedCustomTarget = CustomTargets.FirstOrDefault();
            },
            () =>
            {
                project.CustomMorphTargets.Add(target);
                project.SortCustomMorphTargets();
                SelectedCustomTarget = target;
            });
        return true;
    }

    public bool RemoveSelectedCustomTarget()
    {
        var current = SelectedCustomTarget;
        if (current is null) return false;

        var index = CustomTargets.IndexOf(current);
        var assignments = current.SliderPresets.ToArray();
        if (!assignmentService.RemoveCustomTarget(project, current)) return false;

        SelectedCustomTarget = CustomTargets.FirstOrDefault();
        StatusMessage = "Removed custom target.";
        RaiseCommandStatesChanged();
        undoRedo.Record(
            "Remove custom target",
            () =>
            {
                foreach (var preset in assignments) current.AddSliderPreset(preset);

                CustomTargets.Insert(Math.Min(index, CustomTargets.Count), current);
                project.SortCustomMorphTargets();
                SelectedCustomTarget = current;
            },
            () =>
            {
                current.ClearSliderPresets();
                CustomTargets.Remove(current);
                SelectedCustomTarget = CustomTargets.FirstOrDefault();
            });
        return true;
    }

    public void ClearCustomTargets()
    {
        var snapshot = CustomTargets
            .Select((target, index) => new CustomTargetSnapshot(target, index, CaptureAssignments(target)))
            .ToArray();
        var selectedTarget = SelectedCustomTarget;

        ApplyClearCustomTargets();
        if (snapshot.Length > 0)
            undoRedo.Record(
                "Clear custom targets",
                () => RestoreCustomTargets(snapshot, selectedTarget),
                ApplyClearCustomTargets);
    }

    private void ApplyClearCustomTargets()
    {
        foreach (var target in CustomTargets) target.ClearSliderPresets();

        CustomTargets.Clear();
        SelectedCustomTarget = null;
        StatusMessage = "Cleared custom targets.";
        RaiseCommandStatesChanged();
    }

    public bool AddSelectedPresetToTarget()
    {
        var target = SelectedTarget;
        var before = CaptureAssignments(target);
        var added = assignmentService.AddPresetToTarget(target, SelectedAvailablePreset);
        if (added)
        {
            StatusMessage = "Added preset to target.";
            RaiseSelectedTargetChanged();
            RecordAssignmentChange("Add preset to target", target, before);
        }

        return added;
    }

    public int AddAllPresetsToTarget()
    {
        var target = SelectedTarget;
        var before = CaptureAssignments(target);
        var added = assignmentService.AddAllPresetsToTarget(target, Presets);
        if (added > 0)
        {
            StatusMessage = "Added all presets to target.";
            RaiseSelectedTargetChanged();
            RecordAssignmentChange("Add all presets to target", target, before);
        }

        return added;
    }

    public bool RemoveSelectedPresetFromTarget()
    {
        var target = SelectedTarget;
        var before = CaptureAssignments(target);
        var removed = assignmentService.RemovePresetFromTarget(target, SelectedAssignedPreset);
        if (removed)
        {
            SelectedAssignedPreset = null;
            StatusMessage = "Removed preset from target.";
            RaiseSelectedTargetChanged();
            RecordAssignmentChange("Remove preset from target", target, before);
        }

        return removed;
    }

    public int ClearTargetPresets()
    {
        var target = SelectedTarget;
        var before = CaptureAssignments(target);
        var cleared = assignmentService.ClearTargetPresets(target);
        if (cleared > 0)
        {
            SelectedAssignedPreset = null;
            StatusMessage = "Cleared target presets.";
            RaiseSelectedTargetChanged();
            RecordAssignmentChange("Clear target presets", target, before);
        }

        return cleared;
    }

    public bool AddSelectedNpc()
    {
        var npc = SelectedImportedNpc;
        if (npc is null) return false;

        var added = assignmentService.AddNpcToMorphs(project, SelectedImportedNpc, AssignRandomOnAdd);
        if (added)
        {
            SelectedNpc = SelectedImportedNpc;
            StatusMessage = "Added NPC.";
            RefreshVisibleNpcs();
            undoRedo.Record(
                "Add NPC",
                () =>
                {
                    project.MorphedNpcs.Remove(npc);
                    SelectedNpc = Npcs.FirstOrDefault();
                },
                () =>
                {
                    project.MorphedNpcs.Add(npc);
                    SelectedNpc = npc;
                });
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
        var before = Npcs.ToArray();
        var selectedNpc = SelectedNpc;
        var added = assignmentService.AddNpcsToMorphs(project, VisibleNpcDatabase.ToArray(), AssignRandomOnAdd);
        var addedSnapshots = Npcs
            .Except(before)
            .Select(npc => new NpcRemovalSnapshot(npc, Npcs.IndexOf(npc), CaptureAssignments(npc)))
            .ToArray();
        StatusMessage = "Added " + added.ToString(CultureInfo.InvariantCulture)
                                 + " NPC" + (added == 1 ? "." : "s.");
        RefreshVisibleNpcs();
        RaiseCommandStatesChanged();
        if (addedSnapshots.Length > 0)
            undoRedo.Record(
                "Add visible NPCs",
                () =>
                {
                    ApplyRemoveNpcs(addedSnapshots);
                    SelectedNpc = selectedNpc is not null && Npcs.Contains(selectedNpc)
                        ? selectedNpc
                        : VisibleNpcs.FirstOrDefault();
                },
                () => RestoreRemovedNpcs(addedSnapshots, selectedNpc));

        return added;
    }

    public bool RemoveSelectedNpc()
    {
        var npc = SelectedNpc;
        if (npc is null) return false;

        var removedNpc = npc;
        var index = Npcs.IndexOf(removedNpc);
        var assignments = CaptureAssignments(removedNpc);
        var removed = assignmentService.RemoveNpc(project, removedNpc);
        if (removed)
        {
            SelectedNpc = Npcs.FirstOrDefault();
            StatusMessage = "Removed NPC.";
            RefreshVisibleNpcs();
            undoRedo.Record(
                "Remove NPC",
                () =>
                {
                    RestoreAssignments(removedNpc, assignments);
                    Npcs.Insert(Math.Min(index, Npcs.Count), removedNpc);
                    SelectedNpc = removedNpc;
                },
                () =>
                {
                    removedNpc.ClearSliderPresets();
                    Npcs.Remove(removedNpc);
                    SelectedNpc = Npcs.FirstOrDefault();
                });
        }

        RaiseCommandStatesChanged();
        return removed;
    }

    public int ClearVisibleNpcs()
    {
        var snapshot = VisibleNpcs
            .Select(npc => new NpcRemovalSnapshot(npc, Npcs.IndexOf(npc), CaptureAssignments(npc)))
            .ToArray();
        var selectedNpc = SelectedNpc;
        var removed = ApplyRemoveNpcs(snapshot);

        StatusMessage = "Removed " + removed.ToString(CultureInfo.InvariantCulture)
                                   + " visible NPC" + (removed == 1 ? "." : "s.");
        RaiseCommandStatesChanged();
        if (removed > 0)
            undoRedo.Record(
                "Clear visible NPCs",
                () => RestoreRemovedNpcs(snapshot, selectedNpc),
                () => ApplyRemoveNpcs(snapshot));

        return removed;
    }

    public int FillEmptyVisibleNpcs(IEnumerable<SliderPreset> presets)
    {
        var candidates = presets?.ToArray() ?? Array.Empty<SliderPreset>();
        var targets = VisibleNpcs.ToArray();
        var before = CaptureAssignments(targets);
        var filled = assignmentService.FillEmptyNpcs(VisibleNpcs.ToArray(), candidates);
        StatusMessage = "Filled " + filled.ToString(CultureInfo.InvariantCulture)
                                  + " empty NPC" + (filled == 1 ? "." : "s.");
        RaiseCommandStatesChanged();
        RaiseSelectedTargetChanged();
        if (filled > 0) RecordAssignmentChange("Fill empty NPCs", before);

        return filled;
    }

    public int ClearVisibleNpcAssignments()
    {
        var targets = VisibleNpcs.ToArray();
        var before = CaptureAssignments(targets);
        var cleared = assignmentService.ClearAssignments(targets);
        StatusMessage = "Cleared assignments from " + cleared.ToString(CultureInfo.InvariantCulture)
                                                    + " NPC" + (cleared == 1 ? "." : "s.");
        RaiseCommandStatesChanged();
        RaiseSelectedTargetChanged();
        if (cleared > 0) RecordAssignmentChange("Clear visible NPC assignments", before);

        return cleared;
    }

    public int AssignSelectedNpcs()
    {
        var preset = SelectedAvailablePreset;
        if (preset is null) return 0;

        var targets = GetSelectedNpcsForCommand().ToArray();
        var before = CaptureAssignments(targets);
        var added = 0;
        foreach (var npc in targets)
            if (assignmentService.AddPresetToTarget(npc, preset))
                added++;

        StatusMessage = "Assigned preset to " + added.ToString(CultureInfo.InvariantCulture)
                                              + " NPC" + (added == 1 ? "." : "s.");
        RaiseCommandStatesChanged();
        RaiseSelectedTargetChanged();
        if (added > 0) RecordAssignmentChange("Assign selected NPCs", before);

        return added;
    }

    public int ClearSelectedNpcAssignments()
    {
        var targets = GetSelectedNpcsForCommand().ToArray();
        var before = CaptureAssignments(targets);
        var cleared = assignmentService.ClearAssignments(targets);
        StatusMessage = "Cleared assignments from " + cleared.ToString(CultureInfo.InvariantCulture)
                                                    + " selected NPC" + (cleared == 1 ? "." : "s.");
        RaiseCommandStatesChanged();
        RaiseSelectedTargetChanged();
        if (cleared > 0) RecordAssignmentChange("Clear selected NPC assignments", before);

        return cleared;
    }

    public int TrimSelectedTargetTo76()
    {
        var target = SelectedTarget;
        if (target is null || target.SliderPresets.Count <= 76) return 0;

        var before = CaptureAssignments(target);
        var usage = project.CustomMorphTargets
            .Cast<MorphTargetBase>()
            .Concat(project.MorphedNpcs)
            .SelectMany(item => item.SliderPresets)
            .GroupBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(group => group.Key, group => group.Count(), StringComparer.OrdinalIgnoreCase);
        var rankedNames = target.SliderPresets
            .Select((preset, index) => new { preset, index })
            .OrderByDescending(item => usage.TryGetValue(item.preset.Name, out var count) ? count : 0)
            .ThenBy(item => item.index)
            .Take(76)
            .Select(item => item.preset.Name)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var kept = target.SliderPresets
            .Where(preset => rankedNames.Contains(preset.Name))
            .ToArray();

        target.ClearSliderPresets();
        foreach (var preset in kept) target.AddSliderPreset(preset);

        RaiseSelectedTargetChanged();
        StatusMessage = "Trimmed selected target to 76 presets.";
        RecordAssignmentChange("Trim target presets", target, before);
        return kept.Length;
    }

    public void SetNpcColumnAllowedValues(NpcFilterColumn column, IEnumerable<string> values)
    {
        var selected = values?.Where(value => !string.IsNullOrWhiteSpace(value)).ToArray()
                       ?? Array.Empty<string>();
        if (selected.Length == 0)
            npcColumnAllowedValues.Remove(column);
        else
            npcColumnAllowedValues[column] = new HashSet<string>(selected, StringComparer.OrdinalIgnoreCase);

        RefreshVisibleNpcs();
    }

    public void SetNpcColumnSearchText(NpcFilterColumn column, string value)
    {
        var normalized = value ?? string.Empty;
        if (string.Equals(GetNpcColumnSearchText(column), normalized, StringComparison.Ordinal)) return;

        npcColumnSearchText[column] = normalized;
        if (column == NpcFilterColumn.Race)
        {
            this.RaisePropertyChanged(nameof(NpcRaceColumnSearchText));
            this.RaisePropertyChanged(nameof(NpcRaceColumnValues));
        }
    }

    public IReadOnlyList<string> GetNpcColumnValues(NpcFilterColumn column)
    {
        npcColumnSearchText.TryGetValue(column, out var filter);
        return Npcs
            .Select(npc => GetNpcColumnValue(npc, column))
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .Where(value => string.IsNullOrWhiteSpace(filter)
                            || value.Contains(filter, StringComparison.OrdinalIgnoreCase))
            .OrderBy(value => value, StringComparer.OrdinalIgnoreCase)
            .ToArray();
    }

    public void ClearNpcRaceFilter()
    {
        SetNpcColumnAllowedValues(NpcFilterColumn.Race, Array.Empty<string>());
        NpcRaceColumnSearchText = string.Empty;
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
        foreach (var target in result.TargetsWithoutPresets) NoPresetTargets.Add(target);

        StatusMessage = result.TargetsWithoutPresets.Count == 0
            ? "Generated morphs."
            : "Generated morphs. " + result.TargetsWithoutPresets.Count.ToString(CultureInfo.InvariantCulture)
                                   + " target" + (result.TargetsWithoutPresets.Count == 1 ? " has" : "s have")
                                   + " no presets.";

        if (NoPresetTargets.Count > 0) noPresetNotificationService.ShowTargetsWithoutPresets(NoPresetTargets.ToArray());
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
            if (NpcDatabase.Any(existing => IsSameNpc(existing, npc))) continue;

            NpcDatabase.Add(npc);
            added++;
        }

        return added;
    }

    private async Task ImportNpcFilesCoreAsync(
        IReadOnlyList<string> files,
        string emptyStatus,
        CancellationToken cancellationToken)
    {
        if (files.Count == 0)
        {
            StatusMessage = emptyStatus;
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
            if (result.UsedFallbackEncoding) fallbackCount++;
        }

        StatusMessage = FormatImportStatus(importedCount, diagnosticCount, fallbackCount);
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
        this.RaisePropertyChanged(nameof(NpcRaceColumnValues));
        RaiseCommandStatesChanged();
    }

    private void RefreshVisibleNpcDatabase()
    {
        RefreshFilteredCollection(NpcDatabase, VisibleNpcDatabase, NpcDatabaseSearchText);
        this.RaisePropertyChanged(nameof(NpcDatabaseCountBadgeText));
        RaiseCommandStatesChanged();
    }

    private void RefreshFilteredCollection(
        IEnumerable<Npc> source,
        ObservableCollection<Npc> target,
        string filterText)
    {
        target.Clear();
        foreach (var npc in source.Where(npc => MatchesFilter(npc, filterText))) target.Add(npc);
    }

    private bool MatchesFilter(Npc npc, string filterText)
    {
        if (!MatchesColumnFilters(npc)) return false;

        if (string.IsNullOrWhiteSpace(filterText)) return true;

        return Contains(npc.Name, filterText)
               || Contains(npc.Mod, filterText)
               || Contains(npc.Race, filterText)
               || Contains(npc.EditorId, filterText)
               || Contains(npc.FormId, filterText)
               || Contains(npc.SliderPresetsText, filterText);
    }

    private bool MatchesColumnFilters(Npc npc)
    {
        foreach (var filter in npcColumnAllowedValues)
            if (!filter.Value.Contains(GetNpcColumnValue(npc, filter.Key)))
                return false;

        return true;
    }

    private static string GetNpcColumnValue(Npc npc, NpcFilterColumn column)
    {
        return column switch
        {
            NpcFilterColumn.Name => npc.Name,
            NpcFilterColumn.Mod => npc.Mod,
            NpcFilterColumn.Race => npc.Race,
            NpcFilterColumn.EditorId => npc.EditorId,
            NpcFilterColumn.FormId => npc.FormId,
            NpcFilterColumn.Presets => npc.SliderPresetsText,
            _ => string.Empty
        };
    }

    private static bool Contains(string value, string filterText) =>
        value.Contains(filterText, StringComparison.OrdinalIgnoreCase);

    private void OnNpcsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        UpdateNpcSubscriptions(args);
        RefreshVisibleNpcs();
    }

    private void OnCustomTargetsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        if (SelectedCustomTarget is not null && !CustomTargets.Contains(SelectedCustomTarget))
            SelectedCustomTarget = CustomTargets.FirstOrDefault();

        RaiseCommandStatesChanged();
    }

    private void OnPresetsChanged(object? sender, NotifyCollectionChangedEventArgs args) => RaiseCommandStatesChanged();

    private void OnNpcDatabaseChanged(object? sender, NotifyCollectionChangedEventArgs args) =>
        RefreshVisibleNpcDatabase();

    private void UpdateNpcSubscriptions(NotifyCollectionChangedEventArgs args)
    {
        if (args.Action == NotifyCollectionChangedAction.Reset)
        {
            RefreshNpcSubscriptions();
            return;
        }

        if (args.OldItems is not null)
            foreach (Npc npc in args.OldItems)
                DetachNpcPropertyChanged(npc);

        if (args.NewItems is not null)
            foreach (Npc npc in args.NewItems)
                AttachNpcPropertyChanged(npc);
    }

    private void RefreshNpcSubscriptions()
    {
        DetachAllNpcPropertyChanged();

        foreach (var npc in Npcs) AttachNpcPropertyChanged(npc);
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

        if (!npcPropertySubscriptions.TryGetValue(npc, out var count)) return;

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
            for (var index = 0; index < subscription.Value; index++)
                subscription.Key.PropertyChanged -= OnNpcPropertyChanged;

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
            RefreshVisibleNpcs();
    }

    private void RefreshSelectedTargetSubscription()
    {
        if (subscribedTarget is not null) subscribedTarget.PropertyChanged -= OnSelectedTargetPropertyChanged;

        subscribedTarget = SelectedTarget;
        if (subscribedTarget is not null) subscribedTarget.PropertyChanged += OnSelectedTargetPropertyChanged;

        SelectedAssignedPreset = null;
        RaiseSelectedTargetChanged();
    }

    private void OnSelectedTargetPropertyChanged(object? sender, PropertyChangedEventArgs args)
    {
        if (args.PropertyName is nameof(MorphTargetBase.SliderPresets)
            or nameof(MorphTargetBase.HasPresets)
            or nameof(MorphTargetBase.Name)
            or nameof(Npc.SliderPresetsText))
            RaiseSelectedTargetChanged();
    }

    private void RaiseSelectedTargetChanged()
    {
        this.RaisePropertyChanged(nameof(SelectedTarget));
        this.RaisePropertyChanged(nameof(SelectedTargetName));
        this.RaisePropertyChanged(nameof(SelectedTargetPresets));
        this.RaisePropertyChanged(nameof(TargetPresetCountText));
        this.RaisePropertyChanged(nameof(TargetPresetWarningState));
        this.RaisePropertyChanged(nameof(IsTargetPresetWarningVisible));
        this.RaisePropertyChanged(nameof(IsTargetPresetTrimVisible));
        this.RaisePropertyChanged(nameof(TargetPresetWarningText));
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
        RaiseCanExecuteChanged(AssignSelectedNpcsCommand);
        RaiseCanExecuteChanged(ClearSelectedNpcAssignmentsCommand);
        RaiseCanExecuteChanged(TrimSelectedTargetTo76Command);
        RaiseCanExecuteChanged(ToggleNpcRaceFilterCommand);
        RaiseCanExecuteChanged(ClearNpcRaceFilterCommand);
        RaiseCanExecuteChanged(GenerateMorphsCommand);
        RaiseCanExecuteChanged(ViewSelectedNpcImageCommand);
    }

    private string GetNpcColumnSearchText(NpcFilterColumn column)
    {
        return npcColumnSearchText.TryGetValue(column, out var value)
            ? value
            : string.Empty;
    }

    private IEnumerable<Npc> GetSelectedNpcsForCommand()
    {
        return SelectedNpcs.Count > 0
            ? SelectedNpcs
            : SelectedNpc is null
                ? Enumerable.Empty<Npc>()
                : new[] { SelectedNpc };
    }

    private void RefreshTargetNameValidation() => this.RaisePropertyChanged(nameof(TargetNameValidationMessage));

    private string ValidateCustomTargetName(string value)
    {
        var normalizedName = (value ?? string.Empty).Trim();
        if (string.IsNullOrWhiteSpace(normalizedName)) return string.Empty;

        var parts = normalizedName.Split('|');
        if ((parts.Length != 2 && parts.Length != 3) || parts.Any(part => string.IsNullOrWhiteSpace(part)))
            return "Custom target must use Context|Gender or Context|Gender|Race[Variant].";

        return CustomTargets.Any(existing => string.Equals(
            existing.Name,
            normalizedName,
            StringComparison.OrdinalIgnoreCase))
            ? "A custom target named '" + normalizedName + "' already exists."
            : string.Empty;
    }

    [SuppressMessage("Performance", "CA1822:Mark members as static",
        Justification = "Kept as instance helper alongside assignment record helpers.")]
    private SliderPreset[] CaptureAssignments(MorphTargetBase? target) =>
        target?.SliderPresets.ToArray() ?? Array.Empty<SliderPreset>();

    [SuppressMessage("Performance", "CA1822:Mark members as static",
        Justification = "Kept as instance helper alongside assignment record helpers.")]
    private Dictionary<MorphTargetBase, SliderPreset[]> CaptureAssignments(IEnumerable<MorphTargetBase> targets) =>
        targets.ToDictionary(target => target, target => target.SliderPresets.ToArray());

    private void RecordAssignmentChange(
        string name,
        MorphTargetBase? target,
        SliderPreset[] before)
    {
        if (target is null) return;

        var after = CaptureAssignments(target);
        undoRedo.Record(
            name,
            () => RestoreAssignments(target, before),
            () => RestoreAssignments(target, after));
    }

    private void RecordAssignmentChange(
        string name,
        Dictionary<MorphTargetBase, SliderPreset[]> before)
    {
        var after = CaptureAssignments(before.Keys);
        undoRedo.Record(
            name,
            () => RestoreAssignments(before),
            () => RestoreAssignments(after));
    }

    private void RestoreAssignments(Dictionary<MorphTargetBase, SliderPreset[]> snapshot)
    {
        foreach (var item in snapshot) RestoreAssignments(item.Key, item.Value);
    }

    private void RestoreAssignments(MorphTargetBase? target, IEnumerable<SliderPreset> presets)
    {
        if (target is null) return;

        target.ClearSliderPresets();
        foreach (var preset in presets) target.AddSliderPreset(preset);

        RaiseSelectedTargetChanged();
        RefreshVisibleNpcs();
    }

    private void RestoreCustomTargets(
        IEnumerable<CustomTargetSnapshot> snapshot,
        CustomMorphTarget? selectedTarget)
    {
        CustomTargets.Clear();
        foreach (var item in snapshot.OrderBy(item => item.Index))
        {
            item.Target.ClearSliderPresets();
            foreach (var preset in item.Assignments) item.Target.AddSliderPreset(preset);

            CustomTargets.Insert(Math.Min(item.Index, CustomTargets.Count), item.Target);
        }

        SelectedCustomTarget = selectedTarget is not null && CustomTargets.Contains(selectedTarget)
            ? selectedTarget
            : CustomTargets.FirstOrDefault();
        RaiseCommandStatesChanged();
        RaiseSelectedTargetChanged();
    }

    private int ApplyRemoveNpcs(IEnumerable<NpcRemovalSnapshot> snapshot)
    {
        var items = snapshot.ToArray();
        var resetSelectedNpc = SelectedNpc is not null
                              && items.Any(item => ReferenceEquals(item.Npc, SelectedNpc));
        var removed = 0;
        foreach (var item in items)
            if (assignmentService.RemoveNpc(project, item.Npc))
                removed++;

        RefreshVisibleNpcs();
        if (resetSelectedNpc) SelectedNpc = VisibleNpcs.FirstOrDefault();
        return removed;
    }

    private void RestoreRemovedNpcs(
        IEnumerable<NpcRemovalSnapshot> snapshot,
        Npc? selectedNpc)
    {
        foreach (var item in snapshot.OrderBy(item => item.Index))
        {
            item.Npc.ClearSliderPresets();
            foreach (var preset in item.Assignments) item.Npc.AddSliderPreset(preset);

            if (!Npcs.Contains(item.Npc)) Npcs.Insert(Math.Min(item.Index, Npcs.Count), item.Npc);
        }

        RefreshVisibleNpcs();
        SelectedNpc = selectedNpc is not null && Npcs.Contains(selectedNpc)
            ? selectedNpc
            : VisibleNpcs.FirstOrDefault();
        RaiseCommandStatesChanged();
        RaiseSelectedTargetChanged();
    }

    private sealed class CustomTargetSnapshot(
        CustomMorphTarget target,
        int index,
        IReadOnlyList<SliderPreset> assignments)
    {
        public CustomMorphTarget Target { get; } = target;

        public int Index { get; } = index;

        public IReadOnlyList<SliderPreset> Assignments { get; } = assignments;
    }

    private sealed class NpcRemovalSnapshot(
        Npc npc,
        int index,
        IReadOnlyList<SliderPreset> assignments)
    {
        public Npc Npc { get; } = npc;

        public int Index { get; } = index;

        public IReadOnlyList<SliderPreset> Assignments { get; } = assignments;
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

    private void ReportCommandFailure(string action, Exception exception) =>
        StatusMessage = action + " failed: " + FormatExceptionMessage(exception);

    private static string FormatImportStatus(int importedCount, int diagnosticCount, int fallbackCount)
    {
        var status = "Imported " + importedCount.ToString(CultureInfo.InvariantCulture)
                                 + " NPC" + (importedCount == 1 ? "." : "s.");
        if (diagnosticCount > 0)
            status += " " + diagnosticCount.ToString(CultureInfo.InvariantCulture)
                          + " issue" + (diagnosticCount == 1 ? " was" : "s were") + " skipped.";

        if (fallbackCount > 0)
            status += " " + fallbackCount.ToString(CultureInfo.InvariantCulture)
                          + " file" + (fallbackCount == 1 ? " used" : "s used") + " fallback decoding.";

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
        public Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
    }

    private sealed class EmptyClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken) => Task.CompletedTask;
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
