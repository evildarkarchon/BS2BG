using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.Globalization;
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

namespace BS2BG.App.ViewModels;

public enum AppWorkspace
{
    Templates,
    Morphs
}

public sealed class MainWindowViewModel : ReactiveObject
{
    private readonly BodyGenIniExportWriter bodyGenIniExportWriter;
    private readonly BosJsonExportWriter bosJsonExportWriter;
    private readonly IAppDialogService dialogService;
    private readonly IFileDialogService fileDialogService;
    private readonly MorphGenerationService morphGenerationService;
    private readonly IUserPreferencesService preferencesService;
    private readonly TemplateProfileCatalog profileCatalog;
    private readonly ProjectModel project;
    private readonly ProjectFileService projectFileService;
    private readonly TemplateGenerationService templateGenerationService;
    private readonly UndoRedoService undoRedo;
    private AppWorkspace activeWorkspace;
    private string commandPaletteSearchText = string.Empty;
    private string? currentProjectPath;
    private string globalSearchText = string.Empty;
    private bool isBusy;
    private bool isCommandPaletteOpen;
    private ThemePreference selectedThemePreference = ThemePreference.System;
    private bool shouldFocusGlobalSearch;
    private string statusMessage = string.Empty;
    private string title = AppShell.Title;

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
        selectedThemePreference = this.preferencesService.Load().Theme;
        ThemePreferenceApplier.Apply(selectedThemePreference);

        project.DirtyStateChanged += (_, _) => RefreshProjectState();
        project.SliderPresets.CollectionChanged += OnProjectOutputStateChanged;
        project.CustomMorphTargets.CollectionChanged += OnProjectOutputStateChanged;
        project.MorphedNpcs.CollectionChanged += OnProjectOutputStateChanged;

        NewProjectCommand = new AsyncRelayCommand(
            NewProjectAsync,
            CanRunShellCommand,
            exception => ReportCommandFailure("New project", exception));
        OpenProjectCommand = new AsyncRelayCommand(
            OpenProjectAsync,
            CanRunShellCommand,
            exception => ReportCommandFailure("Open project", exception));
        SaveProjectCommand = new AsyncRelayCommand(
            SaveProjectAsync,
            CanSaveProject,
            exception => ReportCommandFailure("Save project", exception));
        SaveProjectAsCommand = new AsyncRelayCommand(
            SaveProjectAsAsync,
            CanRunShellCommand,
            exception => ReportCommandFailure("Save project as", exception));
        ExportBosJsonCommand = new AsyncRelayCommand(
            ExportBosJsonAsync,
            CanExportBosJson,
            exception => ReportCommandFailure("Export BoS JSON", exception));
        ExportBodyGenInisCommand = new AsyncRelayCommand(
            ExportBodyGenInisAsync,
            CanExportBodyGenInis,
            exception => ReportCommandFailure("Export BodyGen INIs", exception));
        ShowAboutCommand = new RelayCommand(ShowAbout, () => !IsBusy);
        UndoCommand = new RelayCommand(() => this.undoRedo.Undo(), () => this.undoRedo.CanUndo);
        RedoCommand = new RelayCommand(() => this.undoRedo.Redo(), () => this.undoRedo.CanRedo);
        FocusGlobalSearchCommand = new RelayCommand(FocusGlobalSearch);
        OpenCommandPaletteCommand = new RelayCommand(OpenCommandPalette);
        CloseCommandPaletteCommand = new RelayCommand(() => IsCommandPaletteOpen = false);
        RunCommandPaletteItemCommand = new RelayCommand<CommandDescriptor>(RunCommandPaletteItem);
        this.undoRedo.StateChanged += (_, _) => RaiseCommandStatesChanged();
        RegisterCommandPaletteItems();
        RefreshVisibleCommandPaletteItems();
        ApplyGlobalSearchText();

        RefreshProjectState();
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

    public string Title
    {
        get => title;
        private set => this.RaiseAndSetIfChanged(ref title, value);
    }

    public string? CurrentProjectPath
    {
        get => currentProjectPath;
        private set
        {
            if (string.Equals(currentProjectPath, value, StringComparison.Ordinal)) return;

            this.RaiseAndSetIfChanged(ref currentProjectPath, value);
            RefreshProjectState();
        }
    }

    public string StatusMessage
    {
        get => statusMessage;
        private set => this.RaiseAndSetIfChanged(ref statusMessage, value);
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

    public TemplatesViewModel Templates { get; }

    public MorphsViewModel Morphs { get; }

    public ObservableCollection<CommandDescriptor> CommandPaletteItems { get; } = new();

    public ObservableCollection<CommandDescriptor> VisibleCommandPaletteItems { get; } = new();

    public IReadOnlyList<ThemePreference> ThemePreferences { get; } = Enum.GetValues<ThemePreference>();

    public ICommand NewProjectCommand { get; }

    public ICommand OpenProjectCommand { get; }

    public ICommand SaveProjectCommand { get; }

    public ICommand SaveProjectAsCommand { get; }

    public ICommand ExportBosJsonCommand { get; }

    public ICommand ExportBodyGenInisCommand { get; }

    public ICommand ShowAboutCommand { get; }

    public ICommand UndoCommand { get; }

    public ICommand RedoCommand { get; }

    public ICommand FocusGlobalSearchCommand { get; }

    public ICommand OpenCommandPaletteCommand { get; }

    public ICommand CloseCommandPaletteCommand { get; }

    public ICommand RunCommandPaletteItemCommand { get; }

    public string GlobalSearchText
    {
        get => globalSearchText;
        set
        {
            var newValue = value ?? string.Empty;
            if (string.Equals(globalSearchText, newValue, StringComparison.Ordinal)) return;

            this.RaiseAndSetIfChanged(ref globalSearchText, newValue);
            ApplyGlobalSearchText();
        }
    }

    public AppWorkspace ActiveWorkspace
    {
        get => activeWorkspace;
        set
        {
            if (activeWorkspace == value) return;

            this.RaiseAndSetIfChanged(ref activeWorkspace, value);
            ApplyGlobalSearchText();
        }
    }

    public bool IsCommandPaletteOpen
    {
        get => isCommandPaletteOpen;
        private set => this.RaiseAndSetIfChanged(ref isCommandPaletteOpen, value);
    }

    public string CommandPaletteSearchText
    {
        get => commandPaletteSearchText;
        set
        {
            var newValue = value ?? string.Empty;
            if (string.Equals(commandPaletteSearchText, newValue, StringComparison.Ordinal)) return;

            this.RaiseAndSetIfChanged(ref commandPaletteSearchText, newValue);
            RefreshVisibleCommandPaletteItems();
        }
    }

    public bool ShouldFocusGlobalSearch
    {
        get => shouldFocusGlobalSearch;
        private set => this.RaiseAndSetIfChanged(ref shouldFocusGlobalSearch, value);
    }

    public ThemePreference SelectedThemePreference
    {
        get => selectedThemePreference;
        set
        {
            if (selectedThemePreference == value) return;

            this.RaiseAndSetIfChanged(ref selectedThemePreference, value);
            ThemePreferenceApplier.Apply(value);
            if (!preferencesService.Save(new UserPreferences { Theme = value }))
                StatusMessage = "Saving preferences failed.";
        }
    }

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
        RefreshProjectState();
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

        IsBusy = true;
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
        finally
        {
            IsBusy = false;
            RefreshProjectState();
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

        IsBusy = true;
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
        finally
        {
            IsBusy = false;
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

        IsBusy = true;
        try
        {
            bosJsonExportWriter.Write(directoryPath, project.SliderPresets, profileCatalog);
            StatusMessage = "BodyTypes of Skyrim JSON files exported.";
        }
        catch (Exception exception)
        {
            StatusMessage = "Exporting BoS JSON files failed: " + FormatExceptionMessage(exception);
        }
        finally
        {
            IsBusy = false;
        }
    }

    public void ShowAbout()
    {
        dialogService.ShowAbout();
        StatusMessage = "Opened About dialog.";
    }

    public void AcknowledgeGlobalSearchFocus() => ShouldFocusGlobalSearch = false;

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
        IsBusy = true;
        try
        {
            await Task.Run(() => projectFileService.Save(project, path), cancellationToken);
            CurrentProjectPath = path;
            StatusMessage = "Saved " + Path.GetFileName(path) + ".";
        }
        catch (Exception exception)
        {
            StatusMessage = "Saving jBS2BG file failed: " + FormatExceptionMessage(exception);
        }
        finally
        {
            IsBusy = false;
            RefreshProjectState();
        }
    }

    private async Task<bool> ConfirmDiscardChangesIfNeededAsync(
        DiscardChangesAction action,
        CancellationToken cancellationToken)
    {
        return !project.IsDirty
               || await dialogService.ConfirmDiscardChangesAsync(action, cancellationToken);
    }

    private void OnProjectOutputStateChanged(object? sender, NotifyCollectionChangedEventArgs args) =>
        RefreshProjectState();

    private void RefreshProjectState()
    {
        Title = FormatTitle();
        RaiseCommandStatesChanged();
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

    private bool CanRunShellCommand() => !IsBusy;

    private bool CanSaveProject() => !IsBusy && (project.IsDirty || CurrentProjectPath is null);

    private bool CanExportBosJson() => !IsBusy && project.SliderPresets.Count > 0;

    private bool CanExportBodyGenInis()
    {
        return !IsBusy
               && (project.SliderPresets.Count > 0
                   || project.CustomMorphTargets.Count > 0
                   || project.MorphedNpcs.Count > 0);
    }

    private void RaiseCommandStatesChanged()
    {
        RaiseCanExecuteChanged(NewProjectCommand);
        RaiseCanExecuteChanged(OpenProjectCommand);
        RaiseCanExecuteChanged(SaveProjectCommand);
        RaiseCanExecuteChanged(SaveProjectAsCommand);
        RaiseCanExecuteChanged(ExportBosJsonCommand);
        RaiseCanExecuteChanged(ExportBodyGenInisCommand);
        RaiseCanExecuteChanged(ShowAboutCommand);
        RaiseCanExecuteChanged(UndoCommand);
        RaiseCanExecuteChanged(RedoCommand);
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
            case RelayCommand<CommandDescriptor> descriptorRelayCommand:
                descriptorRelayCommand.RaiseCanExecuteChanged();
                break;
        }
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
