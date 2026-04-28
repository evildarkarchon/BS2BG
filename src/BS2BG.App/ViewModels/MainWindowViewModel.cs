using System.Collections.ObjectModel;
using System.Globalization;
using System.Reactive;
using System.Reactive.Disposables;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.App.ViewModels.Workflow;
using BS2BG.Core.Automation;
using BS2BG.Core.Bundling;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.IO;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using ReactiveUI;
using ReactiveUI.SourceGenerators;

namespace BS2BG.App.ViewModels;

public enum AppWorkspace
{
    Templates,
    Morphs,
    Diagnostics,
    Profiles
}

public sealed partial class MainWindowViewModel : ReactiveObject, IDisposable
{
    private readonly BodyGenIniExportWriter bodyGenIniExportWriter;
    private readonly BosJsonExportWriter bosJsonExportWriter;
    private readonly IAppDialogService dialogService;
    private readonly CompositeDisposable disposables = new();
    private readonly ExportPreviewService exportPreviewService;
    private readonly IFileDialogService fileDialogService;
    private readonly MorphGenerationService morphGenerationService;
    private readonly PortableProjectBundleService portableProjectBundleService;
    private readonly IUserPreferencesService preferencesService;
    private readonly ITemplateProfileCatalogService profileCatalogService;
    private readonly ProjectModel project;
    private readonly BehaviorSubject<bool> projectOpenProfileRecoveryBusy = new(false);
    private readonly ProjectFileService projectFileService;
    private readonly TemplateGenerationService templateGenerationService;
    private readonly UndoRedoService undoRedo;
    private UserPreferences currentPreferences;

    [Reactive] private AppWorkspace _activeWorkspace;
    [Reactive] private string _commandPaletteSearchText = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string? _currentProjectPath;

    [Reactive] private string _globalSearchText = string.Empty;
    [Reactive(SetModifier = AccessModifier.Private)] private bool _hasExportPreview;
    [Reactive(SetModifier = AccessModifier.Private)] private bool _hasFileOperationLedger;
    [Reactive(SetModifier = AccessModifier.Private)] private string _bundlePreviewSummary = string.Empty;
    [Reactive] private bool _bundleOverwriteAllowed;
    [Reactive] private OutputIntent _bundleOutputIntent = OutputIntent.All;
    [Reactive] private string? _bundleTargetPath;
    [ObservableAsProperty] private bool _isAnyBusy;

    [Reactive(SetModifier = AccessModifier.Private)]
    private bool _isCommandPaletteOpen;

    [Reactive] private ThemePreference _selectedThemePreference = ThemePreference.System;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _exportPreviewSummary = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private bool _shouldFocusGlobalSearch;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string _statusMessage = string.Empty;

    [ObservableAsProperty] private string _title = AppShell.Title;

    public MainWindowViewModel()
        : this(CreateDesignTimeProject())
    {
    }

    public MainWindowViewModel(TemplatesViewModel templates, MorphsViewModel morphs)
        : this(
            new ProjectModel(),
            new ProjectFileService(),
            new TemplateGenerationService(),
            new MorphGenerationService(),
            CreateDesignTimeProfileCatalog(),
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(new TemplateGenerationService()),
            new EmptyFileDialogService(),
            new NullAppDialogService(),
            templates,
            morphs)
    {
    }

    public MainWindowViewModel(
        ProjectModel project,
        ProjectFileService projectFileService,
        TemplateGenerationService templateGenerationService,
        MorphGenerationService morphGenerationService,
        TemplateProfileCatalog profileCatalog,
        BodyGenIniExportWriter bodyGenIniExportWriter,
        BosJsonExportWriter bosJsonExportWriter,
        IFileDialogService fileDialogService,
        IAppDialogService dialogService,
        TemplatesViewModel templates,
        MorphsViewModel morphs,
        UndoRedoService? undoRedo = null,
        IUserPreferencesService? preferencesService = null,
        ExportPreviewService? exportPreviewService = null,
        DiagnosticsViewModel? diagnostics = null,
        PortableProjectBundleService? portableProjectBundleService = null)
        : this(
            project,
            projectFileService,
            templateGenerationService,
            morphGenerationService,
            new TemplateProfileCatalogService(profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog))),
            bodyGenIniExportWriter,
            bosJsonExportWriter,
            fileDialogService,
            dialogService,
            templates,
            morphs,
            undoRedo,
            preferencesService,
            exportPreviewService,
            diagnostics,
            profiles: null,
            navigationService: null,
            portableProjectBundleService)
    {
    }

    public MainWindowViewModel(
        ProjectModel project,
        ProjectFileService projectFileService,
        TemplateGenerationService templateGenerationService,
        MorphGenerationService morphGenerationService,
        ITemplateProfileCatalogService profileCatalogService,
        BodyGenIniExportWriter bodyGenIniExportWriter,
        BosJsonExportWriter bosJsonExportWriter,
        IFileDialogService fileDialogService,
        IAppDialogService dialogService,
        TemplatesViewModel templates,
        MorphsViewModel morphs,
        UndoRedoService? undoRedo = null,
        IUserPreferencesService? preferencesService = null,
        ExportPreviewService? exportPreviewService = null,
        DiagnosticsViewModel? diagnostics = null,
        ProfileManagerViewModel? profiles = null,
        INavigationService? navigationService = null,
        PortableProjectBundleService? portableProjectBundleService = null)
    {
        this.project = project ?? throw new ArgumentNullException(nameof(project));
        this.projectFileService = projectFileService ?? throw new ArgumentNullException(nameof(projectFileService));
        this.templateGenerationService = templateGenerationService
                                         ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.morphGenerationService = morphGenerationService
                                      ?? throw new ArgumentNullException(nameof(morphGenerationService));
        this.profileCatalogService = profileCatalogService ?? throw new ArgumentNullException(nameof(profileCatalogService));
        this.bodyGenIniExportWriter = bodyGenIniExportWriter
                                      ?? throw new ArgumentNullException(nameof(bodyGenIniExportWriter));
        this.bosJsonExportWriter = bosJsonExportWriter
                                   ?? throw new ArgumentNullException(nameof(bosJsonExportWriter));
        this.fileDialogService = fileDialogService ?? throw new ArgumentNullException(nameof(fileDialogService));
        this.dialogService = dialogService ?? throw new ArgumentNullException(nameof(dialogService));
        this.exportPreviewService = exportPreviewService ?? new ExportPreviewService(templateGenerationService);
        this.portableProjectBundleService = portableProjectBundleService ?? new PortableProjectBundleService(
            projectFileService,
            templateGenerationService,
            morphGenerationService,
            bodyGenIniExportWriter,
            bosJsonExportWriter,
            new AssignmentStrategyReplayService(new MorphAssignmentService(new RandomAssignmentProvider())),
            profileCatalogService.Current,
            new DiagnosticReportTextFormatter());
        this.undoRedo = undoRedo ?? new UndoRedoService();
        this.preferencesService = preferencesService ?? new UserPreferencesService();
        Templates = templates ?? throw new ArgumentNullException(nameof(templates));
        Morphs = morphs ?? throw new ArgumentNullException(nameof(morphs));
        Diagnostics = diagnostics ?? new DiagnosticsViewModel(project, profileCatalogService, new ProjectValidationService(), new ProfileDiagnosticsService());
        Profiles = profiles ?? new ProfileManagerViewModel(
            project,
            profileCatalogService,
            new EmptyUserProfileStore(),
            new ProfileDefinitionService(),
            new NullProfileManagementDialogService(),
            Templates);
        currentPreferences = this.preferencesService.Load();
        _selectedThemePreference = currentPreferences.Theme;
        ThemePreferenceApplier.Apply(_selectedThemePreference);

        var dirtyChanged = Observable.FromEventPattern<EventHandler, EventArgs>(
                h => project.DirtyStateChanged += h,
                h => project.DirtyStateChanged -= h)
            .Select(_ => Unit.Default)
            .StartWith(Unit.Default);
        var presetsCount =
            CollectionChangedObservable.Observe(project.SliderPresets, () => project.SliderPresets.Count);
        var customTargetsCount = CollectionChangedObservable.Observe(
            project.CustomMorphTargets,
            () => project.CustomMorphTargets.Count);
        var morphedNpcsCount = CollectionChangedObservable.Observe(
            project.MorphedNpcs,
            () => project.MorphedNpcs.Count);
        var undoRedoChanged = Observable.FromEventPattern<EventHandler, EventArgs>(
                h => this.undoRedo.StateChanged += h,
                h => this.undoRedo.StateChanged -= h)
            .Select(_ => Unit.Default)
            .StartWith(Unit.Default);
        disposables.Add(Observable.FromEventPattern<EventHandler, EventArgs>(
                h => this.undoRedo.HistoryPruned += h,
                h => this.undoRedo.HistoryPruned -= h)
            .Subscribe(_ => StatusMessage = "Undo history trimmed to keep large workflows responsive."));

        var aggregateBusySubject = new BehaviorSubject<bool>(false);
        disposables.Add(aggregateBusySubject);
        var notBusy = aggregateBusySubject.DistinctUntilChanged().Select(b => !b);
        var canSave = notBusy.CombineLatest(
            this.WhenAnyValue(x => x.CurrentProjectPath),
            dirtyChanged,
            (busyOk, path, _) => busyOk && (project.IsDirty || path is null));
        var canExportBosJson = notBusy.CombineLatest(
            presetsCount,
            (busyOk, count) => busyOk && count > 0);
        var canExportBodyGenInis = notBusy.CombineLatest(
            presetsCount,
            customTargetsCount,
            morphedNpcsCount,
            (busyOk, p, t, n) => busyOk && (p > 0 || t > 0 || n > 0));

        NewProjectCommand = ReactiveCommand.CreateFromTask(NewProjectAsync, notBusy);
        OpenProjectCommand = ReactiveCommand.CreateFromTask(OpenProjectAsync, notBusy);
        SaveProjectCommand = ReactiveCommand.CreateFromTask(SaveProjectAsync, canSave);
        SaveProjectAsCommand = ReactiveCommand.CreateFromTask(SaveProjectAsAsync, notBusy);
        ExportBosJsonCommand = ReactiveCommand.CreateFromTask(ExportBosJsonAsync, canExportBosJson);
        ExportBodyGenInisCommand = ReactiveCommand.CreateFromTask(ExportBodyGenInisAsync, canExportBodyGenInis);
        PreviewBosJsonExportCommand = ReactiveCommand.CreateFromTask(PreviewBosJsonExportAsync, canExportBosJson);
        PreviewBodyGenExportCommand = ReactiveCommand.CreateFromTask(PreviewBodyGenExportAsync, canExportBodyGenInis);
        PreviewPortableBundleCommand = ReactiveCommand.CreateFromTask(PreviewPortableBundleAsync, notBusy);
        CreatePortableBundleCommand = ReactiveCommand.CreateFromTask(CreatePortableBundleAsync, notBusy);
        HandleDroppedFilesCommand = ReactiveCommand.CreateFromTask<IReadOnlyList<string>, Unit>(
            async (paths, ct) =>
            {
                await HandleDroppedFilesAsync(paths, ct);
                return Unit.Default;
            },
            notBusy);

        var busySources = new[]
        {
            Templates.WhenAnyValue(x => x.IsBusy), Morphs.WhenAnyValue(x => x.IsBusy),
            Diagnostics.WhenAnyValue(x => x.IsBusy), Profiles.WhenAnyValue(x => x.IsBusy),
            projectOpenProfileRecoveryBusy.AsObservable(),
            NewProjectCommand.IsExecuting, OpenProjectCommand.IsExecuting, SaveProjectCommand.IsExecuting,
            SaveProjectAsCommand.IsExecuting, ExportBosJsonCommand.IsExecuting,
            ExportBodyGenInisCommand.IsExecuting, PreviewBosJsonExportCommand.IsExecuting,
            PreviewBodyGenExportCommand.IsExecuting, PreviewPortableBundleCommand.IsExecuting,
            CreatePortableBundleCommand.IsExecuting, HandleDroppedFilesCommand.IsExecuting
        };

        disposables.Add(Observable.CombineLatest(busySources)
            .Select(values => values.Any(b => b))
            .DistinctUntilChanged()
            .Subscribe(aggregateBusySubject.OnNext));

        Templates.LinkExternalBusy(aggregateBusySubject.AsObservable());
        Morphs.LinkExternalBusy(aggregateBusySubject.AsObservable());
        disposables.Add(projectOpenProfileRecoveryBusy);

        if (navigationService is not null)
        {
            disposables.Add(Disposable.Create(() => navigationService.WorkspaceRequested -= OnWorkspaceRequested));
            navigationService.WorkspaceRequested += OnWorkspaceRequested;
        }

        _isAnyBusyHelper = aggregateBusySubject.ToProperty(this, x => x.IsAnyBusy, initialValue: false);

        ShowAboutCommand = ReactiveCommand.Create(ShowAbout, notBusy);
        UndoCommand = ReactiveCommand.Create(
            () => { this.undoRedo.Undo(); },
            undoRedoChanged
                .Select(_ => this.undoRedo.CanUndo)
                .CombineLatest(notBusy, (can, ok) => can && ok));
        RedoCommand = ReactiveCommand.Create(
            () => { this.undoRedo.Redo(); },
            undoRedoChanged
                .Select(_ => this.undoRedo.CanRedo)
                .CombineLatest(notBusy, (can, ok) => can && ok));
        FocusGlobalSearchCommand = ReactiveCommand.Create(FocusGlobalSearch);
        OpenCommandPaletteCommand = ReactiveCommand.Create(OpenCommandPalette);
        CloseCommandPaletteCommand = ReactiveCommand.Create(() => { IsCommandPaletteOpen = false; });
        RunCommandPaletteItemCommand = ReactiveCommand.Create<CommandDescriptor>(RunCommandPaletteItem);

        disposables.Add(NewProjectCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("New project", ex)));
        disposables.Add(OpenProjectCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Open project", ex)));
        disposables.Add(SaveProjectCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Save project", ex)));
        disposables.Add(SaveProjectAsCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Save project as", ex)));
        disposables.Add(ExportBosJsonCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Export BoS JSON", ex)));
        disposables.Add(ExportBodyGenInisCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Export BodyGen INIs", ex)));
        disposables.Add(PreviewBosJsonExportCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Preview BoS JSON export", ex)));
        disposables.Add(PreviewBodyGenExportCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Preview BodyGen export", ex)));
        disposables.Add(PreviewPortableBundleCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Preview portable bundle", ex)));
        disposables.Add(CreatePortableBundleCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Create portable bundle", ex)));
        disposables.Add(HandleDroppedFilesCommand.ThrownExceptions
            .Subscribe(ex => ReportCommandFailure("Drop files", ex)));

        _titleHelper = this.WhenAnyValue(x => x.CurrentProjectPath)
            .CombineLatest(dirtyChanged, (path, _) => FormatTitle())
            .DistinctUntilChanged()
            .ToProperty(this, x => x.Title, AppShell.Title);

        disposables.Add(this.WhenAnyValue(x => x.GlobalSearchText)
            .CombineLatest(this.WhenAnyValue(x => x.ActiveWorkspace), (_, _) => Unit.Default)
            .Skip(1)
            .Subscribe(_ => ApplyGlobalSearchText()));
        disposables.Add(this.WhenAnyValue(x => x.CommandPaletteSearchText)
            .Skip(1)
            .Subscribe(_ => RefreshVisibleCommandPaletteItems()));
        disposables.Add(this.WhenAnyValue(x => x.SelectedThemePreference)
            .Skip(1)
            .Subscribe(theme =>
            {
                ThemePreferenceApplier.Apply(theme);
                SaveThemePreference(theme);
            }));

        RegisterCommandPaletteItems();
        RefreshVisibleCommandPaletteItems();
        ApplyGlobalSearchText();
    }

    /// <summary>
    /// Persists theme selection while preserving workflow preferences stored in the same local file.
    /// The preferences file is best-effort state, so save failure reports status without blocking UI commands.
    /// </summary>
    private void SaveThemePreference(ThemePreference theme)
    {
        var latestPreferences = preferencesService.Load();
        currentPreferences = new UserPreferences
        {
            Theme = theme,
            OmitRedundantSliders = latestPreferences.OmitRedundantSliders,
            ProjectFolder = latestPreferences.ProjectFolder,
            BodySlideXmlFolder = latestPreferences.BodySlideXmlFolder,
            NpcTextFolder = latestPreferences.NpcTextFolder,
            BodyGenExportFolder = latestPreferences.BodyGenExportFolder,
            BosJsonExportFolder = latestPreferences.BosJsonExportFolder
        };
        if (!preferencesService.Save(currentPreferences)) StatusMessage = "Saving preferences failed.";
    }

    private MainWindowViewModel(ProjectModel project)
        : this(
            project,
            new ProjectFileService(),
            new TemplateGenerationService(),
            new MorphGenerationService(),
            CreateDesignTimeProfileCatalog(),
            new BodyGenIniExportWriter(),
            new BosJsonExportWriter(new TemplateGenerationService()),
            new EmptyFileDialogService(),
            new NullAppDialogService(),
            CreateDesignTimeTemplates(project),
            CreateDesignTimeMorphs(project))
    {
    }

    public TemplatesViewModel Templates { get; }

    public MorphsViewModel Morphs { get; }

    public DiagnosticsViewModel Diagnostics { get; }

    public ProfileManagerViewModel Profiles { get; }

    public ObservableCollection<CommandDescriptor> CommandPaletteItems { get; } = new();

    public ObservableCollection<CommandDescriptor> VisibleCommandPaletteItems { get; } = new();

    public ObservableCollection<ExportPreviewViewModel> ExportPreviewFiles { get; } = new();

    public ObservableCollection<FileOperationLedgerViewModel> LastFileOperationLedger { get; } = new();

    public ObservableCollection<string> BundlePreviewEntries { get; } = new();

    public IReadOnlyList<OutputIntent> BundleOutputIntents { get; } = Enum.GetValues<OutputIntent>();

    public IReadOnlyList<ThemePreference> ThemePreferences { get; } = Enum.GetValues<ThemePreference>();

    private TemplateProfileCatalog CurrentCatalog => profileCatalogService.Current;

    public ReactiveCommand<Unit, Unit> NewProjectCommand { get; }

    public ReactiveCommand<Unit, Unit> OpenProjectCommand { get; }

    public ReactiveCommand<Unit, Unit> SaveProjectCommand { get; }

    public ReactiveCommand<Unit, Unit> SaveProjectAsCommand { get; }

    public ReactiveCommand<Unit, Unit> ExportBosJsonCommand { get; }

    public ReactiveCommand<Unit, Unit> ExportBodyGenInisCommand { get; }

    public ReactiveCommand<Unit, Unit> PreviewBosJsonExportCommand { get; }

    public ReactiveCommand<Unit, Unit> PreviewBodyGenExportCommand { get; }

    public ReactiveCommand<Unit, Unit> PreviewPortableBundleCommand { get; }

    public ReactiveCommand<Unit, Unit> CreatePortableBundleCommand { get; }

    public ReactiveCommand<Unit, Unit> ShowAboutCommand { get; }

    public ReactiveCommand<Unit, Unit> UndoCommand { get; }

    public ReactiveCommand<Unit, Unit> RedoCommand { get; }

    public ReactiveCommand<Unit, Unit> FocusGlobalSearchCommand { get; }

    public ReactiveCommand<Unit, Unit> OpenCommandPaletteCommand { get; }

    public ReactiveCommand<Unit, Unit> CloseCommandPaletteCommand { get; }

    public ReactiveCommand<CommandDescriptor, Unit> RunCommandPaletteItemCommand { get; }

    public ReactiveCommand<IReadOnlyList<string>, Unit> HandleDroppedFilesCommand { get; }

    public void Dispose() => disposables.Dispose();

    public async Task NewProjectAsync(CancellationToken cancellationToken = default)
    {
        if (!await ConfirmDiscardChangesIfNeededAsync(DiscardChangesAction.NewProject, cancellationToken))
        {
            StatusMessage = "New project cancelled.";
            return;
        }

        project.ReplaceWith(new ProjectModel());
        CurrentProjectPath = null;
        Templates.SelectedPreset = null;
        Morphs.SelectedCustomTarget = null;
        Morphs.SelectedNpc = null;
        undoRedo.Clear();
        ClearProjectPresentationState();
        StatusMessage = "New project created.";
    }

    public async Task OpenProjectAsync(CancellationToken cancellationToken = default)
    {
        if (!await ConfirmDiscardChangesIfNeededAsync(DiscardChangesAction.OpenProject, cancellationToken))
        {
            StatusMessage = "Open cancelled.";
            return;
        }

        var path = await fileDialogService.PickOpenProjectFileAsync(cancellationToken);
        if (string.IsNullOrWhiteSpace(path))
        {
            StatusMessage = "Open cancelled.";
            return;
        }

        await TryOpenProjectPathAsync(path, false, cancellationToken);
    }

    public async Task OpenProjectPathAsync(string path, CancellationToken cancellationToken = default) =>
        await TryOpenProjectPathAsync(path, true, cancellationToken);

    private async Task<bool> TryOpenProjectPathAsync(
        string path,
        bool confirmDiscard,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            StatusMessage = "Open cancelled.";
            return false;
        }

        if (confirmDiscard
            && !await ConfirmDiscardChangesIfNeededAsync(DiscardChangesAction.OpenProject, cancellationToken))
        {
            StatusMessage = "Open cancelled.";
            return false;
        }

        try
        {
            var loadedResult = await Task.Run(
                () => projectFileService.LoadWithDiagnosticsFromString(File.ReadAllText(path)),
                cancellationToken);
            var loadedProject = loadedResult.Project;
            var localSnapshot = profileCatalogService.LocalCustomProfiles.Select(profile => profile.Clone()).ToArray();
            var projectOverlaySnapshot = profileCatalogService.ProjectProfiles.Select(profile => profile.Clone()).ToArray();
            var currentCatalogSnapshot = profileCatalogService.Current;
            ProjectProfileConflictTransactionResult conflictResult;
            projectOpenProfileRecoveryBusy.OnNext(true);
            try
            {
                conflictResult = await ResolveProjectProfileConflictsAsync(
                    loadedProject,
                    localSnapshot,
                    currentCatalogSnapshot,
                    cancellationToken);
            }
            finally
            {
                projectOpenProfileRecoveryBusy.OnNext(false);
            }
            if (!conflictResult.Succeeded)
            {
                profileCatalogService.WithProjectProfiles(projectOverlaySnapshot);
                StatusMessage = conflictResult.StatusMessage;
                return false;
            }

            foreach (var profile in conflictResult.ProfilesToSave)
            {
                var saveResult = profileCatalogService.SaveLocalProfile(profile);
                if (!saveResult.Succeeded)
                {
                    profileCatalogService.WithProjectProfiles(projectOverlaySnapshot);
                    StatusMessage = FormatProfileSaveFailure(saveResult);
                    return false;
                }
            }

            if (conflictResult.ProfilesToSave.Count > 0) profileCatalogService.Refresh();

            project.ReplaceWith(loadedProject);
            CurrentProjectPath = path;
            Templates.SelectedPreset = project.SliderPresets.FirstOrDefault();
            Morphs.SelectedCustomTarget = project.CustomMorphTargets.FirstOrDefault();
            Morphs.SelectedNpc = project.MorphedNpcs.FirstOrDefault();
            Morphs.ApplyProjectLoadDiagnostics(loadedResult.Diagnostics);
            undoRedo.Clear();
            project.MarkClean();
            if (conflictResult.MarkDirtyAfterOpen) project.MarkDirty();

            ClearProjectPresentationState(clearProjectProfiles: false);
            profileCatalogService.WithProjectProfiles(loadedProject.CustomProfiles.Select(profile => profile.Clone()).ToArray());
            var statusParts = new List<string> { "Opened " + Path.GetFileName(path) + "." };
            if (loadedResult.Diagnostics.Count > 0)
                statusParts.Add("Project opened with embedded profile diagnostics.");
            if (HasMissingCustomProfileReference(loadedProject, profileCatalogService.Current))
                statusParts.Add("Missing custom profile references use visible fallback until resolved.");
            StatusMessage = string.Join(" ", statusParts);
            return true;
        }
        catch (Exception exception)
        {
            StatusMessage = "Opening jBS2BG file failed: " + FormatExceptionMessage(exception);
            return false;
        }
    }

    public async Task HandleDroppedFilesAsync(
        IReadOnlyList<string> paths,
        CancellationToken cancellationToken = default)
    {
        try
        {
            var files = paths?.Where(path => !string.IsNullOrWhiteSpace(path)).ToArray()
                        ?? Array.Empty<string>();
            if (files.Length == 0)
            {
                StatusMessage = "No files dropped.";
                return;
            }

            var projectFiles = files
                .Where(path => string.Equals(Path.GetExtension(path), ".jbs2bg", StringComparison.OrdinalIgnoreCase))
                .ToArray();
            var xmlFiles = files
                .Where(path => string.Equals(Path.GetExtension(path), ".xml", StringComparison.OrdinalIgnoreCase))
                .ToArray();
            var npcFiles = files
                .Where(path => string.Equals(Path.GetExtension(path), ".txt", StringComparison.OrdinalIgnoreCase))
                .ToArray();
            var skipped = files.Length - projectFiles.Length - xmlFiles.Length - npcFiles.Length;

            if (projectFiles.Length > 0
                && !await TryOpenProjectPathAsync(projectFiles[0], true, cancellationToken))
                return;

            if (xmlFiles.Length > 0) await Templates.ImportPresetFilesAsync(xmlFiles, cancellationToken);

            if (npcFiles.Length > 0) await Morphs.ImportNpcFilesAsync(npcFiles, cancellationToken);

            StatusMessage = "Processed " + (files.Length - skipped).ToString(CultureInfo.InvariantCulture)
                                         + " dropped file" + (files.Length - skipped == 1 ? "." : "s.")
                                         + (skipped > 0
                                             ? " Skipped " + skipped.ToString(CultureInfo.InvariantCulture)
                                                           + " unsupported file" + (skipped == 1 ? "." : "s.")
                                             : string.Empty);
        }
        catch (Exception exception)
        {
            StatusMessage = "Dropped file processing failed: " + FormatExceptionMessage(exception);
        }
    }

    /// <summary>
    /// Collects and applies embedded/local conflict decisions to a loaded project before any caller-visible open mutation occurs.
    /// </summary>
    /// <param name="loadedProject">Detached project loaded from disk.</param>
    /// <param name="localProfiles">Snapshot of local custom profile definitions captured before user dialogs.</param>
    /// <param name="catalogSnapshot">Catalog snapshot used for bundled-name uniqueness checks.</param>
    /// <param name="cancellationToken">Cancels prompts and aborts without mutation.</param>
    /// <returns>A transaction result containing project changes and any deferred local profile writes.</returns>
    private async Task<ProjectProfileConflictTransactionResult> ResolveProjectProfileConflictsAsync(
        ProjectModel loadedProject,
        IReadOnlyList<CustomProfileDefinition> localProfiles,
        TemplateProfileCatalog catalogSnapshot,
        CancellationToken cancellationToken)
    {
        var localByName = localProfiles.ToDictionary(profile => profile.Name, StringComparer.OrdinalIgnoreCase);
        var conflicts = loadedProject.CustomProfiles
            .Where(embedded => localByName.TryGetValue(embedded.Name, out var local)
                               && !ProfileDefinitionEquality.DefinitionallyEquals(local, embedded))
            .Select(embedded => new ProjectProfileConflict(embedded, localByName[embedded.Name]))
            .ToArray();
        if (conflicts.Length == 0) return ProjectProfileConflictTransactionResult.Success([], false);

        var decisions = new List<(ProjectProfileConflict Conflict, ProfileConflictDecision Decision)>();
        foreach (var conflict in conflicts)
        {
            var decision = await dialogService.PromptProfileConflictAsync(
                new ProfileConflictRequest(
                    conflict.Embedded.Name,
                    CreateProfileSummary(conflict.Local),
                    CreateProfileSummary(conflict.Embedded)),
                cancellationToken);
            if (decision is null)
                return ProjectProfileConflictTransactionResult.Failure("Profile conflict resolution cancelled; project was not opened.");

            decisions.Add((conflict, decision));
        }

        var renameValidation = ValidateRenameDecisions(decisions, catalogSnapshot, localProfiles, loadedProject.CustomProfiles);
        if (renameValidation is not null) return ProjectProfileConflictTransactionResult.Failure(renameValidation);

        var profilesToSave = new List<CustomProfileDefinition>();
        var profilesToRemove = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var renameMap = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var (conflict, decision) in decisions)
        {
            switch (decision.Resolution)
            {
                case ProfileConflictResolution.UseProjectCopy:
                    break;
                case ProfileConflictResolution.ReplaceLocalProfile:
                    profilesToSave.Add(new CustomProfileDefinition(
                        conflict.Embedded.Name,
                        conflict.Embedded.Game,
                        conflict.Embedded.SliderProfile,
                        ProfileSourceKind.LocalCustom,
                        conflict.Local.FilePath));
                    profilesToRemove.Add(conflict.Embedded.Name);
                    break;
                case ProfileConflictResolution.RenameProjectCopy:
                    var renamed = decision.RenamedProfileName?.Trim() ?? string.Empty;
                    renameMap.Add(conflict.Embedded.Name, renamed);
                    conflict.Embedded.Name = renamed;
                    break;
                case ProfileConflictResolution.KeepLocalProfile:
                    profilesToRemove.Add(conflict.Embedded.Name);
                    break;
            }
        }

        foreach (var removeName in profilesToRemove)
        {
            var matching = loadedProject.CustomProfiles.FirstOrDefault(profile => string.Equals(profile.Name, removeName, StringComparison.OrdinalIgnoreCase));
            if (matching is not null) loadedProject.CustomProfiles.Remove(matching);
        }

        foreach (var preset in loadedProject.SliderPresets)
            if (renameMap.TryGetValue(preset.ProfileName, out var renamedProfileName))
                preset.ProfileName = renamedProfileName;

        return ProjectProfileConflictTransactionResult.Success(profilesToSave, renameMap.Count > 0);
    }

    private static string? ValidateRenameDecisions(
        IEnumerable<(ProjectProfileConflict Conflict, ProfileConflictDecision Decision)> decisions,
        TemplateProfileCatalog catalogSnapshot,
        IReadOnlyList<CustomProfileDefinition> localProfiles,
        IReadOnlyList<CustomProfileDefinition> embeddedProfiles)
    {
        var bundledNames = catalogSnapshot.Entries
            .Where(entry => entry.SourceKind == ProfileSourceKind.Bundled)
            .Select(entry => entry.Name)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var localNames = localProfiles
            .Select(profile => profile.Name)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var renameDecisions = decisions
            .Where(item => item.Decision.Resolution == ProfileConflictResolution.RenameProjectCopy)
            .ToArray();
        var embeddedNamesBeingRenamed = renameDecisions
            .Select(item => item.Conflict.Embedded.Name)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var occupiedEmbeddedNames = embeddedProfiles
            .Select(profile => profile.Name)
            .Where(name => !embeddedNamesBeingRenamed.Contains(name))
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var renamedNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        foreach (var (conflict, decision) in renameDecisions)
        {
            var renamed = decision.RenamedProfileName?.Trim();
            if (string.IsNullOrWhiteSpace(renamed))
                return "Rename Project Copy requires a unique display name.";

            if (bundledNames.Contains(renamed)
                || localNames.Contains(renamed)
                || occupiedEmbeddedNames.Contains(renamed)
                || !renamedNames.Add(renamed))
                return $"Profile name '{renamed}' conflicts with an existing bundled, local, embedded, or renamed profile.";
        }

        return null;
    }

    private static string CreateProfileSummary(CustomProfileDefinition profile)
    {
        var source = profile.SourceKind == ProfileSourceKind.LocalCustom ? "Local custom" : "Embedded project";
        var location = string.IsNullOrWhiteSpace(profile.FilePath) ? "no source path" : profile.FilePath;
        return source + " profile, game " + profile.Game + ", " + location + ".";
    }

    private static bool HasMissingCustomProfileReference(ProjectModel loadedProject, TemplateProfileCatalog catalog) =>
        loadedProject.SliderPresets.Any(preset => !catalog.ContainsProfile(preset.ProfileName));

    private static string FormatProfileSaveFailure(UserProfileSaveResult saveResult)
    {
        var detail = saveResult.Diagnostics.Count > 0 ? saveResult.Diagnostics[0].Message : null;
        return string.IsNullOrWhiteSpace(detail)
            ? "Could not save custom profile; project was not opened."
            : detail + "; project was not opened.";
    }

    public async Task SaveProjectAsync(CancellationToken cancellationToken = default) =>
        await SaveProjectInternalAsync(CurrentProjectPath, CurrentProjectPath is null, cancellationToken);

    public async Task SaveProjectAsAsync(CancellationToken cancellationToken = default) =>
        await SaveProjectInternalAsync(CurrentProjectPath, true, cancellationToken);

    public async Task ExportBodyGenInisAsync(CancellationToken cancellationToken = default)
    {
        Templates.GenerateTemplates();
        Morphs.GenerateMorphs();

        if (string.IsNullOrWhiteSpace(Templates.GeneratedTemplateText)
            && string.IsNullOrWhiteSpace(Morphs.GeneratedMorphsText))
        {
            StatusMessage = "No generated BodyGen output to export.";
            return;
        }

        var directoryPath = await fileDialogService.PickBodyGenExportFolderAsync(cancellationToken);
        if (string.IsNullOrWhiteSpace(directoryPath))
        {
            StatusMessage = "BodyGen INI export cancelled.";
            return;
        }

        try
        {
            ClearFileOperationLedger();
            var preview = exportPreviewService.PreviewBodyGen(
                directoryPath,
                Templates.GeneratedTemplateText,
                Morphs.GeneratedMorphsText);
            ApplyExportPreview("BodyGen", preview);
            if (RequiresExportConfirmation(preview)
                && !await dialogService.ConfirmExportOverwriteAsync(preview, cancellationToken))
            {
                StatusMessage = "Export cancelled; existing files kept.";
                return;
            }

            bodyGenIniExportWriter.Write(
                directoryPath,
                Templates.GeneratedTemplateText,
                Morphs.GeneratedMorphsText);
            StatusMessage = "Templates and Morphs INI exported.";
        }
        catch (Exception exception)
        {
            ReportFileOperationFailure("Exporting Templates and Morphs INI", exception);
        }
    }

    /// <summary>
    /// Builds a read-only BodyGen export preview using the same generated text that the writer receives.
    /// </summary>
    public async Task PreviewBodyGenExportAsync(CancellationToken cancellationToken = default)
    {
        Templates.GenerateTemplates();
        Morphs.GenerateMorphs();

        if (string.IsNullOrWhiteSpace(Templates.GeneratedTemplateText)
            && string.IsNullOrWhiteSpace(Morphs.GeneratedMorphsText))
        {
            ClearExportPreview();
            StatusMessage = "No generated BodyGen output to preview.";
            return;
        }

        var directoryPath = await fileDialogService.PickBodyGenExportFolderAsync(cancellationToken);
        if (string.IsNullOrWhiteSpace(directoryPath))
        {
            StatusMessage = "BodyGen export preview cancelled.";
            return;
        }

        var preview = exportPreviewService.PreviewBodyGen(
            directoryPath,
            Templates.GeneratedTemplateText,
            Morphs.GeneratedMorphsText);
        ApplyExportPreview("BodyGen", preview);
        StatusMessage = "BodyGen export preview ready.";
    }

    public async Task ExportBosJsonAsync(CancellationToken cancellationToken = default)
    {
        if (project.SliderPresets.Count == 0)
        {
            StatusMessage = "No presets to export as BoS JSON.";
            return;
        }

        var directoryPath = await fileDialogService.PickBosJsonExportFolderAsync(cancellationToken);
        if (string.IsNullOrWhiteSpace(directoryPath))
        {
            StatusMessage = "BoS JSON export cancelled.";
            return;
        }

        var snapshot = project.SliderPresets.Select(preset => preset.Clone()).ToList();

        try
        {
            ClearFileOperationLedger();
            var preview = exportPreviewService.PreviewBosJson(directoryPath, snapshot, CurrentCatalog);
            ApplyExportPreview("BoS JSON", preview);
            if (RequiresExportConfirmation(preview)
                && !await dialogService.ConfirmExportOverwriteAsync(preview, cancellationToken))
            {
                StatusMessage = "Export cancelled; existing files kept.";
                return;
            }

            await Task.Run(
                () => bosJsonExportWriter.Write(directoryPath, snapshot, CurrentCatalog),
                cancellationToken);
            StatusMessage = "BodyTypes of Skyrim JSON files exported.";
        }
        catch (Exception exception)
        {
            ReportFileOperationFailure("Exporting BoS JSON files", exception);
        }
    }

    /// <summary>
    /// Builds a read-only BoS JSON export preview from cloned presets so preview cannot observe later UI edits.
    /// </summary>
    public async Task PreviewBosJsonExportAsync(CancellationToken cancellationToken = default)
    {
        if (project.SliderPresets.Count == 0)
        {
            ClearExportPreview();
            StatusMessage = "No presets to preview as BoS JSON.";
            return;
        }

        var directoryPath = await fileDialogService.PickBosJsonExportFolderAsync(cancellationToken);
        if (string.IsNullOrWhiteSpace(directoryPath))
        {
            StatusMessage = "BoS JSON export preview cancelled.";
            return;
        }

        var snapshot = project.SliderPresets.Select(preset => preset.Clone()).ToList();
        var preview = exportPreviewService.PreviewBosJson(directoryPath, snapshot, CurrentCatalog);
        ApplyExportPreview("BoS JSON", preview);
        StatusMessage = "BoS JSON export preview ready.";
    }

    /// <summary>
    /// Previews the portable bundle layout using current in-memory project state without writing the target zip.
    /// </summary>
    public Task PreviewPortableBundleAsync(CancellationToken cancellationToken = default)
    {
        var request = BuildPortableBundleRequest(BundleTargetPath ?? string.Empty);
        var preview = portableProjectBundleService.Preview(request);
        ApplyBundlePreview(preview, request.SourceProjectFileName);
        StatusMessage = "Portable bundle preview ready.";
        return Task.CompletedTask;
    }

    /// <summary>
    /// Creates a portable bundle zip after requiring a target path and explicit overwrite opt-in for existing files.
    /// </summary>
    public async Task CreatePortableBundleAsync(CancellationToken cancellationToken = default)
    {
        var targetPath = BundleTargetPath;
        if (string.IsNullOrWhiteSpace(targetPath))
        {
            targetPath = await fileDialogService.PickSaveBundleFileAsync(cancellationToken);
            BundleTargetPath = targetPath;
        }

        if (string.IsNullOrWhiteSpace(targetPath))
        {
            StatusMessage = "Portable bundle creation cancelled.";
            return;
        }

        var request = BuildPortableBundleRequest(targetPath);
        if (File.Exists(targetPath) && !BundleOverwriteAllowed)
        {
            var preview = portableProjectBundleService.Preview(request);
            ApplyBundlePreview(preview, request.SourceProjectFileName);
            StatusMessage = "Target files already exist. Enable overwrite to replace them.";
            return;
        }

        var result = await Task.Run(() => portableProjectBundleService.Create(request), cancellationToken);
        ApplyBundleResult(result, request.SourceProjectFileName);
        StatusMessage = result.Outcome == PortableProjectBundleOutcome.Success
            ? "Portable bundle created: " + Path.GetFileName(targetPath) + "."
            : FormatBundleOutcome(result.Outcome);
    }

    public void ShowAbout()
    {
        dialogService.ShowAbout();
        StatusMessage = "Opened About dialog.";
    }

    /// <summary>
    /// Builds a Core bundle request from the current GUI project state and filename-only project identity.
    /// </summary>
    private PortableProjectBundleRequest BuildPortableBundleRequest(string bundlePath)
    {
        var sourceProjectFileName = string.IsNullOrWhiteSpace(CurrentProjectPath)
            ? "unsaved-project.jbs2bg"
            : Path.GetFileName(CurrentProjectPath);
        var privateRoots = new[]
        {
            string.IsNullOrWhiteSpace(CurrentProjectPath) ? null : Path.GetDirectoryName(Path.GetFullPath(CurrentProjectPath)),
            string.IsNullOrWhiteSpace(bundlePath) ? null : Path.GetDirectoryName(Path.GetFullPath(bundlePath)),
        }
            .Where(path => !string.IsNullOrWhiteSpace(path))
            .Cast<string>()
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();

        return new PortableProjectBundleRequest(
            project,
            bundlePath,
            sourceProjectFileName,
            BundleOutputIntent,
            BundleOverwriteAllowed,
            CreatedUtc: null,
            BuildProjectSaveContext(),
            privateRoots);
    }

    private void ApplyBundlePreview(PortableProjectBundlePreview preview, string sourceProjectFileName)
    {
        BundlePreviewEntries.Clear();
        foreach (var entry in BuildBundlePreviewRows(preview.Entries)) BundlePreviewEntries.Add(entry);
        BundlePreviewSummary = BuildBundleSummary(
            "Portable bundle preview",
            preview.Outcome,
            sourceProjectFileName,
            preview.PrivacyFindings,
            preview.ReplayReportText);
    }

    private void ApplyBundleResult(PortableProjectBundleResult result, string sourceProjectFileName)
    {
        BundlePreviewEntries.Clear();
        foreach (var entry in BuildBundlePreviewRows(result.Entries.Select(path => new BundleManifestEntry(path, string.Empty, string.Empty))))
            BundlePreviewEntries.Add(entry);
        BundlePreviewSummary = BuildBundleSummary(
            "Portable bundle result",
            result.Outcome,
            sourceProjectFileName,
            result.PrivacyFindings,
            result.ReplayReportText);
    }

    private string BuildBundleSummary(
        string heading,
        PortableProjectBundleOutcome outcome,
        string sourceProjectFileName,
        IReadOnlyList<string> privacyFindings,
        string replayReportText)
    {
        var privacyStatus = privacyFindings.Any(finding => finding.Contains("leak", StringComparison.OrdinalIgnoreCase)
                                                           && !finding.Contains("No private", StringComparison.OrdinalIgnoreCase))
            ? "No absolute paths in manifest/report (Path privacy check failed)"
            : "No absolute paths in manifest/report";
        var dirtyState = project.IsDirty ? " Uses current open project state." : string.Empty;
        return heading + ": " + sourceProjectFileName
               + ". Output intent: " + BundleOutputIntent
               + ". Profile copy scope: Referenced custom profiles only. "
               + privacyStatus
               + ". Outcome: " + outcome + "."
               + (string.IsNullOrWhiteSpace(replayReportText) ? string.Empty : " " + replayReportText)
               + dirtyState;
    }

    private static IEnumerable<string> BuildBundlePreviewRows(IEnumerable<BundleManifestEntry> entries)
    {
        var paths = entries.Select(entry => entry.Path).ToHashSet(StringComparer.Ordinal);
        foreach (var folder in new[] { "project/", "outputs/bodygen/", "outputs/bos/", "profiles/", "reports/" })
            yield return folder;

        foreach (var path in paths.OrderBy(path => path, StringComparer.Ordinal)) yield return path;
        if (!paths.Contains("manifest.json")) yield return "manifest.json";
        if (!paths.Contains("SHA256SUMS.txt")) yield return "SHA256SUMS.txt";
    }

    private static string FormatBundleOutcome(PortableProjectBundleOutcome outcome) => outcome switch
    {
        PortableProjectBundleOutcome.ValidationBlocked => "Portable bundle blocked by project validation.",
        PortableProjectBundleOutcome.MissingProfile => "Portable bundle blocked by missing referenced custom profiles.",
        PortableProjectBundleOutcome.OverwriteRefused => "Target files already exist. Enable overwrite to replace them.",
        PortableProjectBundleOutcome.IoFailure => "Portable bundle creation failed due to a file I/O error.",
        _ => "Portable bundle creation completed.",
    };

    public void AcknowledgeGlobalSearchFocus() => ShouldFocusGlobalSearch = false;

    public void NotifyDropIgnoredAsBusy() =>
        StatusMessage = "Drop ignored - application is busy.";

    private async Task SaveProjectInternalAsync(
        string? targetPath,
        bool promptForPath,
        CancellationToken cancellationToken)
    {
        var path = targetPath;
        if (promptForPath) path = await fileDialogService.PickSaveProjectFileAsync(targetPath, cancellationToken);

        if (string.IsNullOrWhiteSpace(path))
        {
            StatusMessage = "Save cancelled.";
            return;
        }

        path = EnsureProjectExtension(path);
        try
        {
            ClearFileOperationLedger();
            var versionAtSnapshot = project.ChangeVersion;
            var snapshot = projectFileService.SaveToString(project, BuildProjectSaveContext());
            await Task.Run(() => projectFileService.WriteAtomic(snapshot, path), cancellationToken);
            CurrentProjectPath = path;
            if (project.ChangeVersion == versionAtSnapshot)
            {
                project.MarkClean();
                StatusMessage = "Saved " + Path.GetFileName(path) + ".";
            }
            else
            {
                StatusMessage = "Saved " + Path.GetFileName(path)
                                         + "; later edits remain unsaved.";
            }
        }
        catch (Exception exception)
        {
            ReportFileOperationFailure("Saving jBS2BG file", exception);
        }
    }

    /// <summary>
    /// Captures runtime custom profiles available to the GUI save path so Core can embed only project-referenced definitions.
    /// </summary>
    /// <returns>A case-insensitive save context containing local custom profiles and active project-scoped profile overlays.</returns>
    private ProjectSaveContext BuildProjectSaveContext()
    {
        var availableProfiles = new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase);
        foreach (var profile in profileCatalogService.LocalCustomProfiles)
            availableProfiles[profile.Name] = profile;

        foreach (var profile in profileCatalogService.ProjectProfiles)
            availableProfiles[profile.Name] = profile;

        return new ProjectSaveContext(availableProfiles);
    }

    private void ClearFileOperationLedger()
    {
        LastFileOperationLedger.Clear();
        HasFileOperationLedger = false;
    }

    /// <summary>
    /// Resets diagnostics, previews, and file ledger state that belongs to the previous project instance.
    /// </summary>
    private void ClearProjectPresentationState(bool clearProjectProfiles = true)
    {
        if (clearProjectProfiles) profileCatalogService.ClearProjectProfiles();
        Diagnostics.ClearReport();
        ClearExportPreview();
        ClearFileOperationLedger();
        Morphs.ClearNpcImportPreviewState();
    }

    private sealed record ProjectProfileConflict(CustomProfileDefinition Embedded, CustomProfileDefinition Local);

    private sealed class ProjectProfileConflictTransactionResult
    {
        private ProjectProfileConflictTransactionResult(
            bool succeeded,
            string statusMessage,
            IReadOnlyList<CustomProfileDefinition> profilesToSave,
            bool markDirtyAfterOpen)
        {
            Succeeded = succeeded;
            StatusMessage = statusMessage;
            ProfilesToSave = profilesToSave;
            MarkDirtyAfterOpen = markDirtyAfterOpen;
        }

        public bool Succeeded { get; }

        public string StatusMessage { get; }

        public IReadOnlyList<CustomProfileDefinition> ProfilesToSave { get; }

        public bool MarkDirtyAfterOpen { get; }

        public static ProjectProfileConflictTransactionResult Success(
            IReadOnlyList<CustomProfileDefinition> profilesToSave,
            bool markDirtyAfterOpen) =>
            new(true, string.Empty, profilesToSave, markDirtyAfterOpen);

        public static ProjectProfileConflictTransactionResult Failure(string statusMessage) =>
            new(false, statusMessage, [], false);
    }

    /// <summary>
    /// Formats an atomic save/export failure into UI status copy and binding-ready ledger rows.
    /// </summary>
    private void ReportFileOperationFailure(string operation, Exception exception)
    {
        LastFileOperationLedger.Clear();
        var atomicException = FindAtomicWriteException(exception);
        if (atomicException is null)
        {
            HasFileOperationLedger = false;
            StatusMessage = operation + " failed: " + FormatExceptionMessage(exception);
            return;
        }

        foreach (var entry in atomicException.Entries)
            LastFileOperationLedger.Add(new FileOperationLedgerViewModel(entry));

        HasFileOperationLedger = LastFileOperationLedger.Count > 0;
        var outcomes = LastFileOperationLedger.Count == 0
            ? "No file outcome rows were available."
            : "Outcomes: " + string.Join(
                "; ",
                LastFileOperationLedger.Select(row => row.OutcomeLabel + " - " + row.Path));
        StatusMessage = "File operation incomplete: " + operation + " did not finish. "
            + "Review which files were written, restored, skipped, or left untouched, "
            + "then retry after fixing the file access problem. "
            + outcomes + ". Original error: " + FormatExceptionMessage(atomicException.InnerException ?? atomicException);
    }

    private static AtomicWriteException? FindAtomicWriteException(Exception exception)
    {
        if (exception is AtomicWriteException atomicWriteException) return atomicWriteException;

        if (exception is AggregateException aggregateException)
            foreach (var inner in aggregateException.InnerExceptions)
            {
                var found = FindAtomicWriteException(inner);
                if (found is not null) return found;
            }

        return exception.InnerException is null ? null : FindAtomicWriteException(exception.InnerException);
    }

    private void ApplyExportPreview(string kind, ExportPreviewResult preview)
    {
        ExportPreviewFiles.Clear();
        foreach (var file in preview.Files)
            ExportPreviewFiles.Add(new ExportPreviewViewModel(kind, file));

        HasExportPreview = ExportPreviewFiles.Count > 0;
        ExportPreviewSummary = preview.Files.Any(file => file.WillOverwrite)
            ? "Existing files will be overwritten. Confirm only after reviewing the paths and snippets below."
            : preview.HasBatchRisk
                ? "Multiple files will be written. Confirm after reviewing the paths and snippets below."
                : "New files will be created at the paths below. No overwrite confirmation is required.";
    }

    private void ClearExportPreview()
    {
        ExportPreviewFiles.Clear();
        HasExportPreview = false;
        ExportPreviewSummary = string.Empty;
    }

    private static bool RequiresExportConfirmation(ExportPreviewResult preview) =>
        preview.HasBatchRisk;

    private async Task<bool> ConfirmDiscardChangesIfNeededAsync(
        DiscardChangesAction action,
        CancellationToken cancellationToken)
    {
        return !project.IsDirty
               || await dialogService.ConfirmDiscardChangesAsync(action, cancellationToken);
    }

    private string FormatTitle()
    {
        if (!string.IsNullOrWhiteSpace(CurrentProjectPath))
        {
            var prefix = project.IsDirty ? "*" : string.Empty;
            return AppShell.Title + " - " + prefix + Path.GetFileName(CurrentProjectPath);
        }

        return project.IsDirty ? AppShell.Title + " *" : AppShell.Title;
    }

    private void ApplyGlobalSearchText()
    {
        switch (ActiveWorkspace)
        {
            case AppWorkspace.Templates:
                Templates.SearchText = GlobalSearchText;
                Morphs.SearchText = string.Empty;
                Profiles.SearchText = string.Empty;
                break;
            case AppWorkspace.Morphs:
                Morphs.SearchText = GlobalSearchText;
                Templates.SearchText = string.Empty;
                Profiles.SearchText = string.Empty;
                break;
            case AppWorkspace.Diagnostics:
                Templates.SearchText = string.Empty;
                Morphs.SearchText = string.Empty;
                Profiles.SearchText = string.Empty;
                break;
            case AppWorkspace.Profiles:
                Profiles.SearchText = GlobalSearchText;
                Templates.SearchText = string.Empty;
                Morphs.SearchText = string.Empty;
                break;
        }
    }

    private void OnWorkspaceRequested(object? sender, AppWorkspace workspace) => ActiveWorkspace = workspace;

    private void FocusGlobalSearch()
    {
        ShouldFocusGlobalSearch = false;
        ShouldFocusGlobalSearch = true;
    }

    private void OpenCommandPalette()
    {
        IsCommandPaletteOpen = true;
        CommandPaletteSearchText = string.Empty;
        RefreshVisibleCommandPaletteItems();
    }

    private void RunCommandPaletteItem(CommandDescriptor? descriptor)
    {
        if (descriptor?.Command.CanExecute(null) == true) descriptor.Command.Execute(null);
        IsCommandPaletteOpen = false;
        CommandPaletteSearchText = string.Empty;
    }

    private void RegisterCommandPaletteItems()
    {
        CommandPaletteItems.Clear();
        AddCommand("New Project", "File", "Ctrl+N", NewProjectCommand);
        AddCommand("Open Project", "File", "Ctrl+O", OpenProjectCommand);
        AddCommand("Save Project", "File", "Ctrl+S", SaveProjectCommand);
        AddCommand("Save Project As", "File", "Ctrl+Alt+S", SaveProjectAsCommand);
        AddCommand("Export Templates as BoS JSON", "File", "Ctrl+B", ExportBosJsonCommand);
        AddCommand("Export BodyGen INIs", "File", "Ctrl+X", ExportBodyGenInisCommand);
        AddCommand("Preview Templates as BoS JSON", "File", string.Empty, PreviewBosJsonExportCommand);
        AddCommand("Preview BodyGen INIs", "File", string.Empty, PreviewBodyGenExportCommand);
        AddCommand("Create Portable Bundle", "File", string.Empty, CreatePortableBundleCommand);
        AddCommand("Undo", "Edit", "Ctrl+Z", UndoCommand);
        AddCommand("Redo", "Edit", "Ctrl+Y", RedoCommand);
        AddCommand("Import BodySlide XML Presets", "Templates", string.Empty, Templates.ImportPresetsCommand);
        AddCommand("Generate Templates", "Templates", string.Empty, Templates.GenerateTemplatesCommand);
        AddCommand("Copy Generated Templates", "Templates", string.Empty, Templates.CopyGeneratedTemplatesCommand);
        AddCommand("Manage Profiles", "Profiles", string.Empty, Templates.ManageProfilesCommand);
        AddCommand("Import NPCs", "Morphs", string.Empty, Morphs.ImportNpcsCommand);
        AddCommand("Assign Preset to Selected NPCs", "Morphs", string.Empty, Morphs.AssignSelectedNpcsCommand);
        AddCommand("Clear Selected NPC Assignments", "Morphs", string.Empty, Morphs.ClearSelectedNpcAssignmentsCommand);
        AddCommand("Generate Morphs", "Morphs", string.Empty, Morphs.GenerateMorphsCommand);
        AddCommand("About", "Help", string.Empty, ShowAboutCommand);
    }

    private void AddCommand(string title, string group, string gestureText, ICommand command) =>
        CommandPaletteItems.Add(new CommandDescriptor(title, group, gestureText, command));

    private void RefreshVisibleCommandPaletteItems()
    {
        VisibleCommandPaletteItems.Clear();
        foreach (var descriptor in CommandPaletteItems.Where(item => item.Matches(CommandPaletteSearchText)))
            VisibleCommandPaletteItems.Add(descriptor);
    }

    private void ReportCommandFailure(string action, Exception exception) =>
        StatusMessage = action + " failed: " + FormatExceptionMessage(exception);

    private static string EnsureProjectExtension(string path)
    {
        return path.EndsWith(".jbs2bg", StringComparison.OrdinalIgnoreCase)
            ? path
            : path + ".jbs2bg";
    }

    private static string FormatExceptionMessage(Exception exception)
    {
        return string.IsNullOrWhiteSpace(exception.Message)
            ? exception.GetType().Name
            : exception.Message;
    }

    private static ProjectModel CreateDesignTimeProject() => new();

    private static TemplateProfileCatalog CreateDesignTimeProfileCatalog()
    {
        return new TemplateProfileCatalog(new[]
        {
            new TemplateProfile(
                ProjectProfileMapping.SkyrimCbbe,
                new SliderProfile(
                    Array.Empty<SliderDefault>(),
                    Array.Empty<SliderMultiplier>(),
                    Array.Empty<string>()))
        });
    }

    private static TemplatesViewModel CreateDesignTimeTemplates(ProjectModel project)
    {
        var profileCatalog = CreateDesignTimeProfileCatalog();
        return new TemplatesViewModel(
            project,
            new BodySlideXmlParser(),
            new TemplateGenerationService(),
            profileCatalog,
            new EmptyBodySlideXmlFilePicker(),
            new EmptyClipboardService());
    }

    private static MorphsViewModel CreateDesignTimeMorphs(ProjectModel project)
    {
        return new MorphsViewModel(
            project,
            new NpcTextParser(),
            new MorphAssignmentService(new RandomAssignmentProvider()),
            new MorphGenerationService(),
            new EmptyNpcTextFilePicker(),
            new EmptyClipboardService());
    }

    private sealed class EmptyFileDialogService : IFileDialogService
    {
        public Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickSaveBundleFileAsync(CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);
    }

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
        public Task<IReadOnlyList<string>> PickProfileImportFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>([]);

        public Task<string?> PickProfileExportPathAsync(string suggestedFileName, CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<string?> PickInstalledProfileForRemapAsync(
            string missingProfileName,
            IReadOnlyList<string> installedProfileNames,
            CancellationToken cancellationToken) =>
            Task.FromResult<string?>(null);

        public Task<bool> ConfirmDeleteProfileAsync(string profileName, CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmDeleteReferencedProfileAsync(
            string profileName,
            int affectedPresetCount,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmDiscardUnsavedEditsAsync(CancellationToken cancellationToken) =>
            Task.FromResult(true);
    }

    private sealed class NullAppDialogService : IAppDialogService
    {
        public Task<bool> ConfirmDiscardChangesAsync(
            DiscardChangesAction action,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmBulkOperationAsync(
            string title,
            string message,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> ConfirmExportOverwriteAsync(
            ExportPreviewResult preview,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<ProfileConflictDecision?> PromptProfileConflictAsync(
            ProfileConflictRequest request,
            CancellationToken cancellationToken) =>
            Task.FromResult<ProfileConflictDecision?>(null);

        public void ShowAbout()
        {
        }
    }

    private sealed class EmptyBodySlideXmlFilePicker : IBodySlideXmlFilePicker
    {
        public Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<string>>(Array.Empty<string>());
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
}
