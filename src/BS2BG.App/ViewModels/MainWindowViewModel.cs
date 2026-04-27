using System.Collections.ObjectModel;
using System.Globalization;
using System.Reactive;
using System.Reactive.Disposables;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using System.Windows.Input;
using BS2BG.App.Services;
using BS2BG.Core.Export;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using ReactiveUI;
using ReactiveUI.SourceGenerators;

namespace BS2BG.App.ViewModels;

public enum AppWorkspace
{
    Templates,
    Morphs
}

public sealed partial class MainWindowViewModel : ReactiveObject, IDisposable
{
    private readonly BodyGenIniExportWriter bodyGenIniExportWriter;
    private readonly BosJsonExportWriter bosJsonExportWriter;
    private readonly IAppDialogService dialogService;
    private readonly CompositeDisposable disposables = new();
    private readonly IFileDialogService fileDialogService;
    private readonly MorphGenerationService morphGenerationService;
    private readonly IUserPreferencesService preferencesService;
    private readonly TemplateProfileCatalog profileCatalog;
    private readonly ProjectModel project;
    private readonly ProjectFileService projectFileService;
    private readonly TemplateGenerationService templateGenerationService;
    private readonly UndoRedoService undoRedo;
    private UserPreferences currentPreferences;

    [Reactive] private AppWorkspace _activeWorkspace;
    [Reactive] private string _commandPaletteSearchText = string.Empty;

    [Reactive(SetModifier = AccessModifier.Private)]
    private string? _currentProjectPath;

    [Reactive] private string _globalSearchText = string.Empty;
    [ObservableAsProperty] private bool _isAnyBusy;

    [Reactive(SetModifier = AccessModifier.Private)]
    private bool _isCommandPaletteOpen;

    [Reactive] private ThemePreference _selectedThemePreference = ThemePreference.System;

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
        IUserPreferencesService? preferencesService = null)
    {
        this.project = project ?? throw new ArgumentNullException(nameof(project));
        this.projectFileService = projectFileService ?? throw new ArgumentNullException(nameof(projectFileService));
        this.templateGenerationService = templateGenerationService
                                         ?? throw new ArgumentNullException(nameof(templateGenerationService));
        this.morphGenerationService = morphGenerationService
                                      ?? throw new ArgumentNullException(nameof(morphGenerationService));
        this.profileCatalog = profileCatalog ?? throw new ArgumentNullException(nameof(profileCatalog));
        this.bodyGenIniExportWriter = bodyGenIniExportWriter
                                      ?? throw new ArgumentNullException(nameof(bodyGenIniExportWriter));
        this.bosJsonExportWriter = bosJsonExportWriter
                                   ?? throw new ArgumentNullException(nameof(bosJsonExportWriter));
        this.fileDialogService = fileDialogService ?? throw new ArgumentNullException(nameof(fileDialogService));
        this.dialogService = dialogService ?? throw new ArgumentNullException(nameof(dialogService));
        this.undoRedo = undoRedo ?? new UndoRedoService();
        this.preferencesService = preferencesService ?? new UserPreferencesService();
        Templates = templates ?? throw new ArgumentNullException(nameof(templates));
        Morphs = morphs ?? throw new ArgumentNullException(nameof(morphs));
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
            NewProjectCommand.IsExecuting, OpenProjectCommand.IsExecuting, SaveProjectCommand.IsExecuting,
            SaveProjectAsCommand.IsExecuting, ExportBosJsonCommand.IsExecuting,
            ExportBodyGenInisCommand.IsExecuting, HandleDroppedFilesCommand.IsExecuting
        };

        disposables.Add(Observable.CombineLatest(busySources)
            .Select(values => values.Any(b => b))
            .DistinctUntilChanged()
            .Subscribe(aggregateBusySubject.OnNext));

        Templates.LinkExternalBusy(aggregateBusySubject.AsObservable());
        Morphs.LinkExternalBusy(aggregateBusySubject.AsObservable());

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
        currentPreferences = new UserPreferences
        {
            Theme = theme,
            OmitRedundantSliders = preferencesService.Load().OmitRedundantSliders
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

    public ObservableCollection<CommandDescriptor> CommandPaletteItems { get; } = new();

    public ObservableCollection<CommandDescriptor> VisibleCommandPaletteItems { get; } = new();

    public IReadOnlyList<ThemePreference> ThemePreferences { get; } = Enum.GetValues<ThemePreference>();

    public ReactiveCommand<Unit, Unit> NewProjectCommand { get; }

    public ReactiveCommand<Unit, Unit> OpenProjectCommand { get; }

    public ReactiveCommand<Unit, Unit> SaveProjectCommand { get; }

    public ReactiveCommand<Unit, Unit> SaveProjectAsCommand { get; }

    public ReactiveCommand<Unit, Unit> ExportBosJsonCommand { get; }

    public ReactiveCommand<Unit, Unit> ExportBodyGenInisCommand { get; }

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
            var loadedProject = await Task.Run(() => projectFileService.Load(path), cancellationToken);
            project.ReplaceWith(loadedProject);
            CurrentProjectPath = path;
            Templates.SelectedPreset = project.SliderPresets.FirstOrDefault();
            Morphs.SelectedCustomTarget = project.CustomMorphTargets.FirstOrDefault();
            Morphs.SelectedNpc = project.MorphedNpcs.FirstOrDefault();
            undoRedo.Clear();
            project.MarkClean();
            StatusMessage = "Opened " + Path.GetFileName(path) + ".";
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
            bodyGenIniExportWriter.Write(
                directoryPath,
                Templates.GeneratedTemplateText,
                Morphs.GeneratedMorphsText);
            StatusMessage = "Templates and Morphs INI exported.";
        }
        catch (Exception exception)
        {
            StatusMessage = "Exporting Templates and Morphs INI failed: " + FormatExceptionMessage(exception);
        }
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
            await Task.Run(
                () => bosJsonExportWriter.Write(directoryPath, snapshot, profileCatalog),
                cancellationToken);
            StatusMessage = "BodyTypes of Skyrim JSON files exported.";
        }
        catch (Exception exception)
        {
            StatusMessage = "Exporting BoS JSON files failed: " + FormatExceptionMessage(exception);
        }
    }

    public void ShowAbout()
    {
        dialogService.ShowAbout();
        StatusMessage = "Opened About dialog.";
    }

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
            var versionAtSnapshot = project.ChangeVersion;
            var snapshot = projectFileService.SaveToString(project);
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
            StatusMessage = "Saving jBS2BG file failed: " + FormatExceptionMessage(exception);
        }
    }

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
        if (ActiveWorkspace == AppWorkspace.Templates)
        {
            Templates.SearchText = GlobalSearchText;
            Morphs.SearchText = string.Empty;
        }
        else
        {
            Morphs.SearchText = GlobalSearchText;
            Templates.SearchText = string.Empty;
        }
    }

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
        AddCommand("Undo", "Edit", "Ctrl+Z", UndoCommand);
        AddCommand("Redo", "Edit", "Ctrl+Y", RedoCommand);
        AddCommand("Import BodySlide XML Presets", "Templates", string.Empty, Templates.ImportPresetsCommand);
        AddCommand("Generate Templates", "Templates", string.Empty, Templates.GenerateTemplatesCommand);
        AddCommand("Copy Generated Templates", "Templates", string.Empty, Templates.CopyGeneratedTemplatesCommand);
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
    }

    private sealed class NullAppDialogService : IAppDialogService
    {
        public Task<bool> ConfirmDiscardChangesAsync(
            DiscardChangesAction action,
            CancellationToken cancellationToken) =>
            Task.FromResult(true);

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
