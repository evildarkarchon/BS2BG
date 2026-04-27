using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.Reactive;
using System.Reactive.Concurrency;
using System.Reactive.Disposables;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using BS2BG.App.Services;
using BS2BG.App.ViewModels.Workflow;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using DynamicData;
using ReactiveUI;
using ReactiveUI.SourceGenerators;

namespace BS2BG.App.ViewModels;

public enum PresetCountWarningState
{
    Neutral,
    Warn,
    Error
}

public sealed partial class MorphsViewModel : ReactiveObject, IDisposable
{
    private readonly MorphAssignmentService assignmentService;
    private readonly IClipboardService clipboardService;
    private readonly CompositeDisposable disposables = new();
    private readonly BehaviorSubject<bool> externalBusy = new(false);
    private readonly INpcImageLookupService imageLookupService;
    private readonly IImageViewService imageViewService;
    private readonly MorphGenerationService morphGenerationService;
    private readonly NpcFilterState npcDatabaseFilterState = new();
    private readonly Dictionary<Npc, NpcRowViewModel> npcDatabaseRowsByNpc = new(ReferenceEqualityComparer.Instance);
    private readonly SourceCache<NpcRowViewModel, Guid> npcDatabaseRowSource = new(row => row.RowId);
    private readonly INoPresetNotificationService noPresetNotificationService;
    private readonly Dictionary<NpcFilterColumn, string> npcColumnSearchText = new();
    private readonly NpcFilterState npcFilterState = new();
    private readonly Dictionary<Npc, int> npcPropertySubscriptions = new();
    private readonly Dictionary<Npc, NpcRowViewModel> npcRowsByNpc = new(ReferenceEqualityComparer.Instance);
    private readonly SourceCache<NpcRowViewModel, Guid> npcRowSource = new(row => row.RowId);
    private readonly INpcTextFilePicker npcTextFilePicker;
    private readonly NpcTextParser npcTextParser;
    private readonly ProjectModel project;
    private readonly HashSet<Guid> selectedNpcRowIds = new();
    private readonly IScheduler filterScheduler;
    private bool syncingSelectedNpcs;
    private readonly UndoRedoService undoRedo;

    [Reactive] private bool _assignRandomOnAdd;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _generatedMorphsText = string.Empty;

    [ObservableAsProperty] private bool _isBusy;
    [Reactive] private bool _isNpcAssignmentStateFilterOpen;
    [Reactive] private bool _isNpcEditorIdFilterOpen;
    [Reactive] private bool _isNpcFormIdFilterOpen;
    [Reactive] private bool _isNpcModFilterOpen;
    [Reactive] private bool _isNpcNameFilterOpen;
    [Reactive] private bool _isNpcPresetFilterOpen;
    [Reactive] private bool _isNpcRaceFilterOpen;
    [Reactive] private string _npcDatabaseSearchText = string.Empty;
    [Reactive] private string _searchText = string.Empty;
    [Reactive] private SliderPreset? _selectedAssignedPreset;
    [Reactive] private SliderPreset? _selectedAvailablePreset;
    [Reactive] private CustomMorphTarget? _selectedCustomTarget;
    [Reactive] private Npc? _selectedImportedNpc;
    [Reactive] private Npc? _selectedNpc;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _statusMessage = string.Empty;

    [Reactive] private string _targetNameInput = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _validationMessage = string.Empty;

    private MorphTargetBase? subscribedTarget;

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
        UndoRedoService? undoRedo = null,
        IScheduler? filterScheduler = null)
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
        this.filterScheduler = filterScheduler ?? CurrentThreadScheduler.Instance;

        project.MorphedNpcs.CollectionChanged += OnNpcsChanged;
        project.CustomMorphTargets.CollectionChanged += OnCustomTargetsChanged;
        project.SliderPresets.CollectionChanged += OnPresetsChanged;
        NpcDatabase.CollectionChanged += OnNpcDatabaseChanged;
        SelectedNpcs.CollectionChanged += OnSelectedNpcsChanged;
        disposables.Add(Disposable.Create(() => SelectedNpcs.CollectionChanged -= OnSelectedNpcsChanged));
        SyncRowsFromCollection(Npcs, npcRowsByNpc, npcRowSource);
        SyncRowsFromCollection(NpcDatabase, npcDatabaseRowsByNpc, npcDatabaseRowSource);
        RefreshNpcSubscriptions();
        RefreshVisibleNpcs();
        RefreshVisibleNpcDatabase();

        var customTargetsChanged = CollectionChangedObservable.Observe(CustomTargets, () => CustomTargets.Count);
        var presetsChanged = CollectionChangedObservable.Observe(Presets, () => Presets.Count);
        var visibleNpcsChanged = CollectionChangedObservable.Observe(VisibleNpcs, () => VisibleNpcs.ToArray());
        var visibleNpcDatabaseChanged = CollectionChangedObservable.Observe(
            VisibleNpcDatabase,
            () => VisibleNpcDatabase.Count);
        var selectedNpcsChanged = CollectionChangedObservable.Observe(SelectedNpcs, () => SelectedNpcs.ToArray());
        var npcsChanged = CollectionChangedObservable.Observe(Npcs, () => Npcs.Count);

        disposables.Add(externalBusy);
        var notExternallyBusy = externalBusy.DistinctUntilChanged().Select(b => !b);

        IObservable<bool> Gate(IObservable<bool> source)
        {
            return source.CombineLatest(notExternallyBusy, (a, ok) => a && ok);
        }

        var selectedTarget = this.WhenAnyValue(x => x.SelectedCustomTarget)
            .CombineLatest(
                this.WhenAnyValue(x => x.SelectedNpc),
                (target, npc) => (MorphTargetBase?)target ?? npc);
        var targetPresetsChanged = SelectedTargetPresetsObservable();

        var canAddCustomTarget = Gate(this.WhenAnyValue(x => x.IsBusy).Select(busy => !busy));
        var canRemoveCustomTarget = Gate(
            this.WhenAnyValue(x => x.SelectedCustomTarget).Select(target => target is not null));
        var canClearCustomTargets = Gate(customTargetsChanged.Select(count => count > 0));
        var canAddSelectedPresetToTarget = Gate(selectedTarget.CombineLatest(
            this.WhenAnyValue(x => x.SelectedAvailablePreset),
            (target, preset) => target is not null && preset is not null));
        var canAddAllPresetsToTarget = Gate(selectedTarget.CombineLatest(
            presetsChanged,
            (target, count) => target is not null && count > 0));
        var canRemoveSelectedPresetFromTarget = Gate(selectedTarget.CombineLatest(
            this.WhenAnyValue(x => x.SelectedAssignedPreset),
            (target, preset) => target is not null && preset is not null));
        var canClearTargetPresets = Gate(targetPresetsChanged.Select(presets => presets.Length > 0));
        var canAddSelectedNpc = Gate(
            this.WhenAnyValue(x => x.SelectedImportedNpc).Select(npc => npc is not null));
        var canAddAllVisibleImportedNpcs = Gate(visibleNpcDatabaseChanged.Select(count => count > 0));
        var canRemoveSelectedNpc = Gate(
            this.WhenAnyValue(x => x.SelectedNpc).Select(npc => npc is not null));
        var canClearVisibleNpcs = Gate(visibleNpcsChanged.Select(npcs => npcs.Length > 0));
        var canFillEmptyNpcs = Gate(visibleNpcsChanged.CombineLatest(
            presetsChanged,
            (npcs, presetCount) => npcs.Any(npc => npc.SliderPresets.Count == 0) && presetCount > 0));
        var canClearAssignments = Gate(visibleNpcsChanged.Select(npcs => npcs.Any(npc => npc.SliderPresets.Count > 0)));
        var canAssignSelectedNpcs = Gate(selectedNpcsChanged.CombineLatest(
            this.WhenAnyValue(x => x.SelectedNpc),
            this.WhenAnyValue(x => x.SelectedAvailablePreset),
            (selected, current, preset) =>
                (selected.Length > 0 || current is not null) && preset is not null));
        var canClearSelectedNpcAssignments = Gate(selectedNpcsChanged.CombineLatest(
            this.WhenAnyValue(x => x.SelectedNpc),
            (selected, current) => GetSelectedNpcsForCommandFromSnapshot(selected, current)
                .Any(npc => npc.SliderPresets.Count > 0)));
        var canTrimSelectedTarget = Gate(targetPresetsChanged.Select(presets => presets.Length >= 77));
        var canGenerateMorphs = customTargetsChanged.CombineLatest(
            npcsChanged,
            (customCount, npcCount) => customCount > 0 || npcCount > 0);
        var canViewSelectedNpcImage = this.WhenAnyValue(x => x.SelectedNpc)
            .CombineLatest(
                this.WhenAnyValue(x => x.SelectedImportedNpc),
                (npc, imported) => (npc ?? imported) is not null);

        ImportNpcsCommand = ReactiveCommand.CreateFromTask(
            ImportNpcsAsync,
            canAddCustomTarget);
        AddCustomTargetCommand = ReactiveCommand.Create(
            () => { AddCustomTarget(); },
            canAddCustomTarget);
        RemoveCustomTargetCommand = ReactiveCommand.Create(
            () => { RemoveSelectedCustomTarget(); },
            canRemoveCustomTarget);
        ClearCustomTargetsCommand = ReactiveCommand.Create(
            ClearCustomTargets,
            canClearCustomTargets);
        AddSelectedPresetToTargetCommand = ReactiveCommand.Create(
            () => { AddSelectedPresetToTarget(); },
            canAddSelectedPresetToTarget);
        AddAllPresetsToTargetCommand = ReactiveCommand.Create(
            () => { AddAllPresetsToTarget(); },
            canAddAllPresetsToTarget);
        RemoveSelectedPresetFromTargetCommand = ReactiveCommand.Create(
            () => { RemoveSelectedPresetFromTarget(); },
            canRemoveSelectedPresetFromTarget);
        ClearTargetPresetsCommand = ReactiveCommand.Create(
            () => { ClearTargetPresets(); },
            canClearTargetPresets);
        AddSelectedNpcCommand = ReactiveCommand.Create(
            () => { AddSelectedNpc(); },
            canAddSelectedNpc);
        AddAllVisibleImportedNpcsCommand = ReactiveCommand.Create(
            () => { AddAllVisibleImportedNpcs(); },
            canAddAllVisibleImportedNpcs);
        RemoveSelectedNpcCommand = ReactiveCommand.Create(
            () => { RemoveSelectedNpc(); },
            canRemoveSelectedNpc);
        ClearVisibleNpcsCommand = ReactiveCommand.Create(
            () => { ClearVisibleNpcs(); },
            canClearVisibleNpcs);
        FillEmptyNpcsCommand = ReactiveCommand.Create(
            () => { FillEmptyFromSelectedPreset(); },
            canFillEmptyNpcs);
        ClearAssignmentsCommand = ReactiveCommand.Create(
            () => { ClearVisibleNpcAssignments(); },
            canClearAssignments);
        AssignSelectedNpcsCommand = ReactiveCommand.Create(
            () => { AssignSelectedNpcs(); },
            canAssignSelectedNpcs);
        ClearSelectedNpcAssignmentsCommand = ReactiveCommand.Create(
            () => { ClearSelectedNpcAssignments(); },
            canClearSelectedNpcAssignments);
        TrimSelectedTargetTo76Command = ReactiveCommand.Create(
            () => { TrimSelectedTargetTo76(); },
            canTrimSelectedTarget);
        ToggleNpcModFilterCommand = ReactiveCommand.Create(() => { IsNpcModFilterOpen = !IsNpcModFilterOpen; });
        ToggleNpcNameFilterCommand = ReactiveCommand.Create(() => { IsNpcNameFilterOpen = !IsNpcNameFilterOpen; });
        ToggleNpcEditorIdFilterCommand = ReactiveCommand.Create(() => { IsNpcEditorIdFilterOpen = !IsNpcEditorIdFilterOpen; });
        ToggleNpcFormIdFilterCommand = ReactiveCommand.Create(() => { IsNpcFormIdFilterOpen = !IsNpcFormIdFilterOpen; });
        ToggleNpcRaceFilterCommand = ReactiveCommand.Create(() => { IsNpcRaceFilterOpen = !IsNpcRaceFilterOpen; });
        ToggleNpcAssignmentStateFilterCommand = ReactiveCommand.Create(() =>
        {
            IsNpcAssignmentStateFilterOpen = !IsNpcAssignmentStateFilterOpen;
        });
        ToggleNpcPresetFilterCommand = ReactiveCommand.Create(() => { IsNpcPresetFilterOpen = !IsNpcPresetFilterOpen; });
        ClearNpcRaceFilterCommand = ReactiveCommand.Create(ClearNpcRaceFilter);
        GenerateMorphsCommand = ReactiveCommand.Create(
            GenerateMorphs,
            canGenerateMorphs);
        CopyGeneratedMorphsCommand = ReactiveCommand.CreateFromTask(
            CopyGeneratedMorphsAsync);
        ViewSelectedNpcImageCommand = ReactiveCommand.Create(
            ViewSelectedNpcImage,
            canViewSelectedNpcImage);

        disposables.Add(ImportNpcsCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Import NPCs", ex)));
        disposables.Add(CopyGeneratedMorphsCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Copy generated morphs", ex)));

        _isBusyHelper = ImportNpcsCommand.IsExecuting.CombineLatest(CopyGeneratedMorphsCommand.IsExecuting,
                (importing, copying) => importing || copying)
            .ToProperty(this, x => x.IsBusy, initialValue: false);

        disposables.Add(this.WhenAnyValue(x => x.SelectedCustomTarget)
            .Skip(1)
            .Subscribe(target =>
            {
                if (target is not null)
                {
                    SelectedNpc = null;
                    TargetNameInput = target.Name;
                }

                RefreshSelectedTargetSubscription();
            }));
        disposables.Add(this.WhenAnyValue(x => x.SelectedNpc)
            .Skip(1)
            .Subscribe(npc =>
            {
                if (npc is not null) SelectedCustomTarget = null;

                RefreshSelectedTargetSubscription();
            }));
        disposables.Add(this.WhenAnyValue(x => x.TargetNameInput)
            .Subscribe(_ => RefreshTargetNameValidation()));
        disposables.Add(this.WhenAnyValue(x => x.SearchText)
            .Skip(1)
            .Do(text => npcFilterState.PendingGlobalSearchText = text)
            .Throttle(TimeSpan.FromMilliseconds(200), this.filterScheduler)
            .Subscribe(_ => ApplyPendingNpcSearchText()));
        disposables.Add(this.WhenAnyValue(x => x.NpcDatabaseSearchText)
            .Skip(1)
            .Do(text => npcDatabaseFilterState.PendingGlobalSearchText = text)
            .Throttle(TimeSpan.FromMilliseconds(200), this.filterScheduler)
            .Subscribe(_ => ApplyPendingNpcDatabaseSearchText()));
    }

    public ObservableCollection<SliderPreset> Presets => project.SliderPresets;

    public ObservableCollection<CustomMorphTarget> CustomTargets => project.CustomMorphTargets;

    public ObservableCollection<Npc> Npcs => project.MorphedNpcs;

    public ObservableCollection<Npc> NpcDatabase { get; } = new();

    public ObservableCollection<Npc> VisibleNpcs { get; } = new();

    public ObservableCollection<Npc> VisibleNpcDatabase { get; } = new();

    public ObservableCollection<MorphTargetBase> NoPresetTargets { get; } = new();

    public ObservableCollection<Npc> SelectedNpcs { get; } = new();

    public ReactiveCommand<Unit, Unit> ImportNpcsCommand { get; }

    public ReactiveCommand<Unit, Unit> AddCustomTargetCommand { get; }

    public ReactiveCommand<Unit, Unit> RemoveCustomTargetCommand { get; }

    public ReactiveCommand<Unit, Unit> ClearCustomTargetsCommand { get; }

    public ReactiveCommand<Unit, Unit> AddSelectedPresetToTargetCommand { get; }

    public ReactiveCommand<Unit, Unit> AddAllPresetsToTargetCommand { get; }

    public ReactiveCommand<Unit, Unit> RemoveSelectedPresetFromTargetCommand { get; }

    public ReactiveCommand<Unit, Unit> ClearTargetPresetsCommand { get; }

    public ReactiveCommand<Unit, Unit> AddSelectedNpcCommand { get; }

    public ReactiveCommand<Unit, Unit> AddAllVisibleImportedNpcsCommand { get; }

    public ReactiveCommand<Unit, Unit> RemoveSelectedNpcCommand { get; }

    public ReactiveCommand<Unit, Unit> ClearVisibleNpcsCommand { get; }

    public ReactiveCommand<Unit, Unit> FillEmptyNpcsCommand { get; }

    public ReactiveCommand<Unit, Unit> ClearAssignmentsCommand { get; }

    public ReactiveCommand<Unit, Unit> AssignSelectedNpcsCommand { get; }

    public ReactiveCommand<Unit, Unit> ClearSelectedNpcAssignmentsCommand { get; }

    public ReactiveCommand<Unit, Unit> TrimSelectedTargetTo76Command { get; }

    public ReactiveCommand<Unit, Unit> ToggleNpcModFilterCommand { get; }

    public ReactiveCommand<Unit, Unit> ToggleNpcNameFilterCommand { get; }

    public ReactiveCommand<Unit, Unit> ToggleNpcEditorIdFilterCommand { get; }

    public ReactiveCommand<Unit, Unit> ToggleNpcFormIdFilterCommand { get; }

    public ReactiveCommand<Unit, Unit> ToggleNpcRaceFilterCommand { get; }

    public ReactiveCommand<Unit, Unit> ToggleNpcAssignmentStateFilterCommand { get; }

    public ReactiveCommand<Unit, Unit> ToggleNpcPresetFilterCommand { get; }

    public ReactiveCommand<Unit, Unit> ClearNpcRaceFilterCommand { get; }

    public ReactiveCommand<Unit, Unit> GenerateMorphsCommand { get; }

    public ReactiveCommand<Unit, Unit> CopyGeneratedMorphsCommand { get; }

    public ReactiveCommand<Unit, Unit> ViewSelectedNpcImageCommand { get; }

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

    public string TargetNameValidationMessage => ValidateCustomTargetName(TargetNameInput);

    [SuppressMessage("Performance", "CA1822:Mark members as static",
        Justification = "Binding-friendly instance property.")]
    public string CustomTargetExamples => "Examples: All|Female, All|Male, Skyrim.esm|Female|NordRace";

    public string NpcRaceColumnSearchText
    {
        get => GetNpcColumnSearchText(NpcFilterColumn.Race);
        set => SetNpcColumnSearchText(NpcFilterColumn.Race, value);
    }

    public IReadOnlyList<string> NpcRaceColumnValues => GetNpcColumnValues(NpcFilterColumn.Race);

    public string NpcRaceFilterBadgeText => GetNpcFilterBadgeText(NpcFilterColumn.Race, "Race");

    public string NpcModColumnSearchText
    {
        get => GetNpcColumnSearchText(NpcFilterColumn.Mod);
        set => SetNpcColumnSearchText(NpcFilterColumn.Mod, value);
    }

    public IReadOnlyList<string> NpcModColumnValues => GetNpcColumnValues(NpcFilterColumn.Mod);

    public string NpcModFilterBadgeText => GetNpcFilterBadgeText(NpcFilterColumn.Mod, "Mod");

    public string NpcNameColumnSearchText
    {
        get => GetNpcColumnSearchText(NpcFilterColumn.Name);
        set => SetNpcColumnSearchText(NpcFilterColumn.Name, value);
    }

    public IReadOnlyList<string> NpcNameColumnValues => GetNpcColumnValues(NpcFilterColumn.Name);

    public string NpcNameFilterBadgeText => GetNpcFilterBadgeText(NpcFilterColumn.Name, "Name");

    public string NpcEditorIdColumnSearchText
    {
        get => GetNpcColumnSearchText(NpcFilterColumn.EditorId);
        set => SetNpcColumnSearchText(NpcFilterColumn.EditorId, value);
    }

    public IReadOnlyList<string> NpcEditorIdColumnValues => GetNpcColumnValues(NpcFilterColumn.EditorId);

    public string NpcEditorIdFilterBadgeText => GetNpcFilterBadgeText(NpcFilterColumn.EditorId, "Editor ID");

    public string NpcFormIdColumnSearchText
    {
        get => GetNpcColumnSearchText(NpcFilterColumn.FormId);
        set => SetNpcColumnSearchText(NpcFilterColumn.FormId, value);
    }

    public IReadOnlyList<string> NpcFormIdColumnValues => GetNpcColumnValues(NpcFilterColumn.FormId);

    public string NpcFormIdFilterBadgeText => GetNpcFilterBadgeText(NpcFilterColumn.FormId, "Form ID");

    public string NpcAssignmentStateColumnSearchText
    {
        get => GetNpcColumnSearchText(NpcFilterColumn.AssignmentState);
        set => SetNpcColumnSearchText(NpcFilterColumn.AssignmentState, value);
    }

    public IReadOnlyList<string> NpcAssignmentStateColumnValues => GetNpcColumnValues(NpcFilterColumn.AssignmentState);

    public string NpcAssignmentStateFilterBadgeText => GetNpcFilterBadgeText(
        NpcFilterColumn.AssignmentState,
        "Assignment");

    public string NpcPresetColumnSearchText
    {
        get => GetNpcColumnSearchText(NpcFilterColumn.Preset);
        set => SetNpcColumnSearchText(NpcFilterColumn.Preset, value);
    }

    public IReadOnlyList<string> NpcPresetColumnValues => GetNpcColumnValues(NpcFilterColumn.Preset);

    public string NpcPresetFilterBadgeText => GetNpcFilterBadgeText(NpcFilterColumn.Preset, "Preset");

    public bool IsNpcFilteredEmptyVisible => Npcs.Count > 0 && VisibleNpcs.Count == 0 && npcFilterState.HasAnyFilter();

    public void Dispose()
    {
        disposables.Dispose();
        npcRowSource.Dispose();
        npcDatabaseRowSource.Dispose();
    }

    public void LinkExternalBusy(IObservable<bool> source)
    {
        ArgumentNullException.ThrowIfNull(source);

        disposables.Add(source.DistinctUntilChanged().Subscribe(externalBusy.OnNext));
    }

    public async Task ImportNpcsAsync(CancellationToken cancellationToken = default)
    {
        ValidationMessage = string.Empty;
        StatusMessage = string.Empty;
        await ImportNpcFilesCoreAsync(
            await npcTextFilePicker.PickNpcTextFilesAsync(cancellationToken),
            "No NPC files selected.",
            cancellationToken);
    }

    public async Task ImportNpcFilesAsync(
        IReadOnlyList<string> files,
        CancellationToken cancellationToken = default)
    {
        ValidationMessage = string.Empty;
        StatusMessage = string.Empty;
        await ImportNpcFilesCoreAsync(files, "No NPC files dropped.", cancellationToken);
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
            npcFilterState.ClearAllowedValues(column);
        else
            npcFilterState.SetAllowedValues(column, selected);

        RefreshVisibleNpcs();
        RaiseNpcColumnFilterStateChanged(column);
    }

    /// <summary>
    /// Reconciles visible ListBox selection changes with stable hidden-row selection.
    /// Hidden selected rows are re-applied from row IDs because Avalonia selection events only report currently visible items after filtering.
    /// </summary>
    /// <param name="selectedVisibleNpcs">The NPCs currently selected in the visible filtered list.</param>
    /// <exception cref="ArgumentNullException">Thrown when <paramref name="selectedVisibleNpcs" /> is null.</exception>
    public void UpdateVisibleNpcSelection(IEnumerable<Npc> selectedVisibleNpcs)
    {
        ArgumentNullException.ThrowIfNull(selectedVisibleNpcs);

        var visibleSelection = selectedVisibleNpcs.ToArray();
        var hiddenSelection = SelectedNpcs
            .Where(npc => !VisibleNpcs.Contains(npc))
            .ToArray();
        var nextSelection = visibleSelection
            .Concat(hiddenSelection)
            .Distinct()
            .ToArray();

        syncingSelectedNpcs = true;
        try
        {
            SelectedNpcs.Clear();
            foreach (var npc in nextSelection) SelectedNpcs.Add(npc);
        }
        finally
        {
            syncingSelectedNpcs = false;
        }

        RebuildSelectedNpcRowIds();
        UpdateHiddenSelectionStatus();
    }

    public void SetNpcColumnSearchText(NpcFilterColumn column, string value)
    {
        var normalized = value ?? string.Empty;
        if (string.Equals(GetNpcColumnSearchText(column), normalized, StringComparison.Ordinal)) return;

        npcColumnSearchText[column] = normalized;
        RaiseNpcColumnSearchPropertiesChanged(column);
    }

    public IReadOnlyList<string> GetNpcColumnValues(NpcFilterColumn column)
    {
        npcColumnSearchText.TryGetValue(column, out var filter);
        return npcFilterState.GetAvailableValues(GetRowsForNpcs(Npcs, npcRowsByNpc), column)
            .Where(value => string.IsNullOrWhiteSpace(filter)
                            || value.Contains(filter, StringComparison.OrdinalIgnoreCase))
            .ToArray();
    }

    public void ClearNpcRaceFilter()
    {
        ClearNpcColumnFilter(NpcFilterColumn.Race);
    }

    /// <summary>
    /// Clears one checklist filter and its popup search text while leaving other active filters intact.
    /// </summary>
    /// <param name="column">The checklist filter column to clear.</param>
    public void ClearNpcColumnFilter(NpcFilterColumn column)
    {
        SetNpcColumnAllowedValues(column, Array.Empty<string>());
        SetNpcColumnSearchText(column, string.Empty);
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
        RefreshVisibleNpcs(false);
    }

    private void RefreshVisibleNpcs(bool applyPendingSearchText)
    {
        if (applyPendingSearchText) npcFilterState.ApplyPendingGlobalSearchText();

        RefreshFilteredCollection(Npcs, npcRowsByNpc, npcFilterState, VisibleNpcs);
        this.RaisePropertyChanged(nameof(NpcCountBadgeText));
        RaiseAllNpcColumnValuesChanged();
        this.RaisePropertyChanged(nameof(IsNpcFilteredEmptyVisible));
        UpdateHiddenSelectionStatus();
    }

    private void RefreshVisibleNpcDatabase()
    {
        RefreshVisibleNpcDatabase(false);
    }

    private void RefreshVisibleNpcDatabase(bool applyPendingSearchText)
    {
        if (applyPendingSearchText) npcDatabaseFilterState.ApplyPendingGlobalSearchText();

        RefreshFilteredCollection(NpcDatabase, npcDatabaseRowsByNpc, npcDatabaseFilterState, VisibleNpcDatabase);
        this.RaisePropertyChanged(nameof(NpcDatabaseCountBadgeText));
    }

    private void ApplyPendingNpcSearchText() => RefreshVisibleNpcs(true);

    private void ApplyPendingNpcDatabaseSearchText() => RefreshVisibleNpcDatabase(true);

    private static void RefreshFilteredCollection(
        IEnumerable<Npc> source,
        Dictionary<Npc, NpcRowViewModel> rowsByNpc,
        NpcFilterState filterState,
        ObservableCollection<Npc> target)
    {
        var predicate = filterState.CreatePredicate();
        target.Clear();
        foreach (var npc in source)
            if (rowsByNpc.TryGetValue(npc, out var row) && predicate(row))
                target.Add(npc);
    }

    private void OnNpcsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        UpdateNpcSubscriptions(args);
        SyncRowsFromCollection(Npcs, npcRowsByNpc, npcRowSource);
        PruneSelectedNpcRowIds();
        RefreshVisibleNpcs();
    }

    private void OnCustomTargetsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        if (SelectedCustomTarget is not null && !CustomTargets.Contains(SelectedCustomTarget))
            SelectedCustomTarget = CustomTargets.FirstOrDefault();
    }

    private void OnPresetsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
    }

    private void OnNpcDatabaseChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        SyncRowsFromCollection(NpcDatabase, npcDatabaseRowsByNpc, npcDatabaseRowSource);
        RefreshVisibleNpcDatabase();
    }

    private void OnSelectedNpcsChanged(object? sender, NotifyCollectionChangedEventArgs args)
    {
        if (syncingSelectedNpcs) return;

        RebuildSelectedNpcRowIds();
        UpdateHiddenSelectionStatus();
    }

    private void RebuildSelectedNpcRowIds()
    {
        selectedNpcRowIds.Clear();
        foreach (var npc in SelectedNpcs)
            if (npcRowsByNpc.TryGetValue(npc, out var row))
                selectedNpcRowIds.Add(row.RowId);
    }

    private void PruneSelectedNpcRowIds()
    {
        selectedNpcRowIds.IntersectWith(npcRowsByNpc.Values.Select(row => row.RowId));
    }

    private int HiddenSelectedNpcCount()
    {
        var visibleRows = VisibleNpcs
            .Select(npc => npcRowsByNpc.TryGetValue(npc, out var row) ? row.RowId : Guid.Empty)
            .Where(rowId => rowId != Guid.Empty)
            .ToHashSet();

        return selectedNpcRowIds.Count(rowId => !visibleRows.Contains(rowId));
    }

    private void UpdateHiddenSelectionStatus()
    {
        var hiddenSelected = HiddenSelectedNpcCount();
        if (hiddenSelected == 0) return;

        StatusMessage = VisibleNpcs.Count.ToString(CultureInfo.InvariantCulture)
                        + " visible, " + selectedNpcRowIds.Count.ToString(CultureInfo.InvariantCulture)
                        + " selected (" + hiddenSelected.ToString(CultureInfo.InvariantCulture)
                        + " hidden by filters)";
    }

    /// <summary>
    /// Reconciles the keyed UI row cache with a mutable NPC collection without replacing wrappers for retained NPC objects.
    /// Stable wrappers keep generated row IDs independent from mutable display/export fields.
    /// </summary>
    /// <param name="source">The current NPC collection snapshot.</param>
    /// <param name="rowsByNpc">The sidecar map from Core NPC models to App-layer row wrappers.</param>
    /// <param name="rowSource">The DynamicData source cache keyed by generated row ID.</param>
    private static void SyncRowsFromCollection(
        IEnumerable<Npc> source,
        Dictionary<Npc, NpcRowViewModel> rowsByNpc,
        SourceCache<NpcRowViewModel, Guid> rowSource)
    {
        var currentNpcs = source.ToHashSet();
        var removedRows = rowsByNpc
            .Where(pair => !currentNpcs.Contains(pair.Key))
            .Select(pair => pair.Value)
            .ToArray();

        foreach (var row in removedRows)
        {
            rowsByNpc.Remove(row.Npc);
            rowSource.Remove(row.RowId);
        }

        foreach (var npc in currentNpcs)
        {
            if (rowsByNpc.ContainsKey(npc)) continue;

            var row = new NpcRowViewModel(npc);
            rowsByNpc.Add(npc, row);
            rowSource.AddOrUpdate(row);
        }
    }

    private static IEnumerable<NpcRowViewModel> GetRowsForNpcs(
        IEnumerable<Npc> npcs,
        Dictionary<Npc, NpcRowViewModel> rowsByNpc)
    {
        foreach (var npc in npcs)
            if (rowsByNpc.TryGetValue(npc, out var row))
                yield return row;
    }

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
    }

    private string GetNpcColumnSearchText(NpcFilterColumn column)
    {
        return npcColumnSearchText.TryGetValue(column, out var value)
            ? value
            : string.Empty;
    }

    private void RaiseAllNpcColumnValuesChanged()
    {
        this.RaisePropertyChanged(nameof(NpcModColumnValues));
        this.RaisePropertyChanged(nameof(NpcNameColumnValues));
        this.RaisePropertyChanged(nameof(NpcEditorIdColumnValues));
        this.RaisePropertyChanged(nameof(NpcFormIdColumnValues));
        this.RaisePropertyChanged(nameof(NpcRaceColumnValues));
        this.RaisePropertyChanged(nameof(NpcAssignmentStateColumnValues));
        this.RaisePropertyChanged(nameof(NpcPresetColumnValues));
        this.RaisePropertyChanged(nameof(NpcModFilterBadgeText));
        this.RaisePropertyChanged(nameof(NpcNameFilterBadgeText));
        this.RaisePropertyChanged(nameof(NpcEditorIdFilterBadgeText));
        this.RaisePropertyChanged(nameof(NpcFormIdFilterBadgeText));
        this.RaisePropertyChanged(nameof(NpcRaceFilterBadgeText));
        this.RaisePropertyChanged(nameof(NpcAssignmentStateFilterBadgeText));
        this.RaisePropertyChanged(nameof(NpcPresetFilterBadgeText));
    }

    private void RaiseNpcColumnSearchPropertiesChanged(NpcFilterColumn column)
    {
        this.RaisePropertyChanged(GetNpcColumnSearchPropertyName(column));
        this.RaisePropertyChanged(GetNpcColumnValuesPropertyName(column));
        this.RaisePropertyChanged(GetNpcColumnBadgePropertyName(column));
        this.RaisePropertyChanged(nameof(IsNpcFilteredEmptyVisible));
    }

    private void RaiseNpcColumnFilterStateChanged(NpcFilterColumn column)
    {
        this.RaisePropertyChanged(GetNpcColumnValuesPropertyName(column));
    }

    private static string GetNpcColumnSearchPropertyName(NpcFilterColumn column)
    {
        return column switch
        {
            NpcFilterColumn.Mod => nameof(NpcModColumnSearchText),
            NpcFilterColumn.Name => nameof(NpcNameColumnSearchText),
            NpcFilterColumn.EditorId => nameof(NpcEditorIdColumnSearchText),
            NpcFilterColumn.FormId => nameof(NpcFormIdColumnSearchText),
            NpcFilterColumn.Race => nameof(NpcRaceColumnSearchText),
            NpcFilterColumn.AssignmentState => nameof(NpcAssignmentStateColumnSearchText),
            NpcFilterColumn.Preset => nameof(NpcPresetColumnSearchText),
            _ => nameof(NpcRaceColumnSearchText)
        };
    }

    private static string GetNpcColumnValuesPropertyName(NpcFilterColumn column)
    {
        return column switch
        {
            NpcFilterColumn.Mod => nameof(NpcModColumnValues),
            NpcFilterColumn.Name => nameof(NpcNameColumnValues),
            NpcFilterColumn.EditorId => nameof(NpcEditorIdColumnValues),
            NpcFilterColumn.FormId => nameof(NpcFormIdColumnValues),
            NpcFilterColumn.Race => nameof(NpcRaceColumnValues),
            NpcFilterColumn.AssignmentState => nameof(NpcAssignmentStateColumnValues),
            NpcFilterColumn.Preset => nameof(NpcPresetColumnValues),
            _ => nameof(NpcRaceColumnValues)
        };
    }

    private string GetNpcFilterBadgeText(NpcFilterColumn column, string label)
    {
        var count = npcFilterState.GetAllowedValues(column).Count;
        return count == 0
            ? string.Empty
            : label + ": " + count.ToString(CultureInfo.InvariantCulture) + " selected";
    }

    private static string GetNpcColumnBadgePropertyName(NpcFilterColumn column)
    {
        return column switch
        {
            NpcFilterColumn.Mod => nameof(NpcModFilterBadgeText),
            NpcFilterColumn.Name => nameof(NpcNameFilterBadgeText),
            NpcFilterColumn.EditorId => nameof(NpcEditorIdFilterBadgeText),
            NpcFilterColumn.FormId => nameof(NpcFormIdFilterBadgeText),
            NpcFilterColumn.Race => nameof(NpcRaceFilterBadgeText),
            NpcFilterColumn.AssignmentState => nameof(NpcAssignmentStateFilterBadgeText),
            NpcFilterColumn.Preset => nameof(NpcPresetFilterBadgeText),
            _ => nameof(NpcRaceFilterBadgeText)
        };
    }

    private IEnumerable<Npc> GetSelectedNpcsForCommand() =>
        GetSelectedNpcsForCommandFromSnapshot(SelectedNpcs.ToArray(), SelectedNpc);

    private static IEnumerable<Npc> GetSelectedNpcsForCommandFromSnapshot(
        Npc[] selectedNpcs,
        Npc? selectedNpc)
    {
        return selectedNpcs.Length > 0
            ? selectedNpcs
            : selectedNpc is null
                ? Enumerable.Empty<Npc>()
                : new[] { selectedNpc };
    }

    private void RefreshTargetNameValidation() => this.RaisePropertyChanged(nameof(TargetNameValidationMessage));

    private string ValidateCustomTargetName(string value)
    {
        if (string.IsNullOrWhiteSpace((value ?? string.Empty).Trim())) return string.Empty;

        if (!MorphAssignmentService.TryValidateCustomTargetName(value, out var normalizedName, out var formatError))
            return formatError;

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
        RaiseSelectedTargetChanged();
    }

    private IObservable<SliderPreset[]> SelectedTargetPresetsObservable() =>
        this.WhenAnyValue(x => x.SelectedCustomTarget)
            .CombineLatest(
                this.WhenAnyValue(x => x.SelectedNpc),
                (target, npc) => (MorphTargetBase?)target ?? npc)
            .Select(target =>
            {
                if (target is null) return Observable.Return(Array.Empty<SliderPreset>());

                return CollectionChangedObservable.Observe(
                    target.SliderPresets,
                    () => target.SliderPresets.ToArray());
            })
            .Switch();

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
