using Avalonia.Controls;
using Avalonia.Platform.Storage;

namespace BS2BG.App.Services;

public sealed class WindowFileDialogService : IFileDialogService
{
    private readonly IFileDialogBackend backend;
    private readonly IUserPreferencesService preferencesService;

    public WindowFileDialogService()
        : this(new UserPreferencesService(), new AvaloniaFileDialogBackend())
    {
    }

    public WindowFileDialogService(IUserPreferencesService preferencesService)
        : this(preferencesService, new AvaloniaFileDialogBackend())
    {
    }

    public WindowFileDialogService(IUserPreferencesService preferencesService, IFileDialogBackend backend)
    {
        this.preferencesService = preferencesService ?? throw new ArgumentNullException(nameof(preferencesService));
        this.backend = backend ?? throw new ArgumentNullException(nameof(backend));
    }

    public async Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken)
    {
        if (!backend.CanOpen) return null;

        var preferences = preferencesService.Load();
        var startFolder = await backend.ResolveStartFolderAsync(preferences.ProjectFolder, cancellationToken);
        var path = await backend.PickOpenProjectFileAsync(startFolder, cancellationToken);

        cancellationToken.ThrowIfCancellationRequested();

        SaveFolderPreference(preferences, path, (prefs, folder) => prefs.ProjectFolder = folder);
        return path;
    }

    public async Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken)
    {
        if (!backend.CanSave) return null;

        var preferences = preferencesService.Load();
        var startFolder = await backend.ResolveStartFolderAsync(preferences.ProjectFolder, cancellationToken);
        var path = await backend.PickSaveProjectFileAsync(currentPath, startFolder, cancellationToken);

        cancellationToken.ThrowIfCancellationRequested();

        SaveFolderPreference(preferences, path, (prefs, folder) => prefs.ProjectFolder = folder);
        return path;
    }

    public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken) =>
        PickRememberedFolderAsync(
            "Export Templates and Morphs INI",
            preferences => preferences.BodyGenExportFolder,
            (preferences, folder) => preferences.BodyGenExportFolder = folder,
            cancellationToken);

    public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken) =>
        PickRememberedFolderAsync(
            "Export BoS JSON files",
            preferences => preferences.BosJsonExportFolder,
            (preferences, folder) => preferences.BosJsonExportFolder = folder,
            cancellationToken);

    public async Task<string?> PickSaveBundleFileAsync(CancellationToken cancellationToken)
    {
        if (!backend.CanSave) return null;

        var preferences = preferencesService.Load();
        var startFolder = await backend.ResolveStartFolderAsync(preferences.ProjectFolder, cancellationToken);
        var path = await backend.PickSaveBundleFileAsync(startFolder, cancellationToken);

        cancellationToken.ThrowIfCancellationRequested();

        SaveFolderPreference(preferences, path, (prefs, folder) => prefs.ProjectFolder = folder);
        return path;
    }

    public void Attach(TopLevel topLevel)
    {
        ArgumentNullException.ThrowIfNull(topLevel);
        if (backend is AvaloniaFileDialogBackend avaloniaBackend) avaloniaBackend.Attach(topLevel);
    }

    private async Task<string?> PickRememberedFolderAsync(
        string title,
        Func<UserPreferences, string?> getPreference,
        Action<UserPreferences, string> setPreference,
        CancellationToken cancellationToken)
    {
        if (!backend.CanPickFolder) return null;

        var preferences = preferencesService.Load();
        var startFolder = await backend.ResolveStartFolderAsync(getPreference(preferences), cancellationToken);
        var path = await backend.PickFolderAsync(title, startFolder, cancellationToken);

        cancellationToken.ThrowIfCancellationRequested();

        if (!string.IsNullOrWhiteSpace(path))
        {
            setPreference(preferences, path);
            preferencesService.Save(preferences);
            return path;
        }

        return null;
    }

    private void SaveFolderPreference(
        UserPreferences preferences,
        string? selectedFilePath,
        Action<UserPreferences, string> setPreference)
    {
        if (string.IsNullOrWhiteSpace(selectedFilePath)) return;

        var folder = Path.GetDirectoryName(selectedFilePath);
        if (string.IsNullOrWhiteSpace(folder)) return;

        setPreference(preferences, folder);
        preferencesService.Save(preferences);
    }
}

/// <summary>
/// Testable backend seam for window-bound file dialogs. Implementations resolve remembered folders as
/// non-blocking suggestions and return selected local paths without exposing Avalonia storage items to ViewModels.
/// </summary>
public interface IFileDialogBackend
{
    bool CanOpen { get; }

    bool CanSave { get; }

    bool CanPickFolder { get; }

    /// <summary>
    /// Opens a single project file picker using the supplied validated start-folder hint when available.
    /// Returns the selected local path, or <see langword="null" /> when the user cancels.
    /// </summary>
    Task<string?> PickOpenProjectFileAsync(string? suggestedStartFolder, CancellationToken cancellationToken);

    /// <summary>
    /// Opens a project save picker using the supplied validated start-folder hint when available.
    /// Returns the selected local path, or <see langword="null" /> when the user cancels.
    /// </summary>
    Task<string?> PickSaveProjectFileAsync(
        string? currentPath,
        string? suggestedStartFolder,
        CancellationToken cancellationToken);

    /// <summary>
    /// Opens a portable bundle save picker restricted to zip files and returns the selected local path.
    /// </summary>
    Task<string?> PickSaveBundleFileAsync(string? suggestedStartFolder, CancellationToken cancellationToken);

    /// <summary>
    /// Opens a single folder picker for an export workflow and returns the selected local folder path.
    /// The start-folder value is advisory and must not block picker display when unavailable.
    /// </summary>
    Task<string?> PickFolderAsync(string title, string? suggestedStartFolder, CancellationToken cancellationToken);

    /// <summary>
    /// Validates a remembered local folder path before it is reused as a picker hint.
    /// Returns <see langword="null" /> for missing, invalid, or inaccessible paths.
    /// </summary>
    Task<string?> ResolveStartFolderAsync(string? path, CancellationToken cancellationToken);
}

/// <summary>
/// Avalonia storage-provider adapter that maps local preference paths to picker SuggestedStartLocation hints.
/// Invalid remembered paths are ignored because folder preferences are convenience state only.
/// </summary>
public sealed class AvaloniaFileDialogBackend : IFileDialogBackend
{
    private static readonly string[] ProjectPatterns = { "*.jbs2bg" };
    private TopLevel? owner;

    public bool CanOpen => owner?.StorageProvider.CanOpen == true;

    public bool CanSave => owner?.StorageProvider.CanSave == true;

    public bool CanPickFolder => owner?.StorageProvider.CanPickFolder == true;

    /// <summary>
    /// Attaches the current window so the backend can use its platform storage provider.
    /// </summary>
    public void Attach(TopLevel topLevel) => owner = topLevel ?? throw new ArgumentNullException(nameof(topLevel));

    /// <inheritdoc />
    public async Task<string?> PickOpenProjectFileAsync(
        string? suggestedStartFolder,
        CancellationToken cancellationToken)
    {
        if (owner is null) return null;

        var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Open jBS2BG File",
            AllowMultiple = false,
            SuggestedStartLocation = await TryGetFolderAsync(suggestedStartFolder),
            FileTypeFilter = new[] { CreateProjectFileType() }
        });

        cancellationToken.ThrowIfCancellationRequested();

        return files.FirstOrDefault(file => file.Path.IsFile)?.Path.LocalPath;
    }

    /// <inheritdoc />
    public async Task<string?> PickSaveProjectFileAsync(
        string? currentPath,
        string? suggestedStartFolder,
        CancellationToken cancellationToken)
    {
        if (owner is null) return null;

        var file = await owner.StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
        {
            Title = "Save jBS2BG File",
            SuggestedFileName = string.IsNullOrWhiteSpace(currentPath)
                ? "project.jbs2bg"
                : Path.GetFileName(currentPath),
            SuggestedStartLocation = await TryGetFolderAsync(suggestedStartFolder),
            DefaultExtension = "jbs2bg",
            FileTypeChoices = new[] { CreateProjectFileType() }
        });

        cancellationToken.ThrowIfCancellationRequested();

        return file?.Path.IsFile == true ? file.Path.LocalPath : null;
    }

    /// <inheritdoc />
    public async Task<string?> PickSaveBundleFileAsync(
        string? suggestedStartFolder,
        CancellationToken cancellationToken)
    {
        if (owner is null) return null;

        var file = await owner.StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
        {
            Title = "Create Portable Bundle",
            SuggestedFileName = "bs2bg-portable-bundle.zip",
            SuggestedStartLocation = await TryGetFolderAsync(suggestedStartFolder),
            DefaultExtension = "zip",
            FileTypeChoices = new[] { CreateBundleFileType() }
        });

        cancellationToken.ThrowIfCancellationRequested();

        return file?.Path.IsFile == true ? file.Path.LocalPath : null;
    }

    /// <inheritdoc />
    public async Task<string?> PickFolderAsync(
        string title,
        string? suggestedStartFolder,
        CancellationToken cancellationToken)
    {
        if (owner is null) return null;

        var folders = await owner.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions
        {
            Title = title,
            AllowMultiple = false,
            SuggestedStartLocation = await TryGetFolderAsync(suggestedStartFolder)
        });

        cancellationToken.ThrowIfCancellationRequested();

        return folders.Count > 0 ? folders[0].Path.LocalPath : null;
    }

    /// <inheritdoc />
    public async Task<string?> ResolveStartFolderAsync(string? path, CancellationToken cancellationToken)
    {
        var folder = await TryGetFolderAsync(path);
        cancellationToken.ThrowIfCancellationRequested();
        return folder?.Path.LocalPath;
    }

    private async Task<IStorageFolder?> TryGetFolderAsync(string? path)
    {
        if (owner is null || string.IsNullOrWhiteSpace(path)) return null;

        try
        {
            return await owner.StorageProvider.TryGetFolderFromPathAsync(new Uri(path));
        }
        catch (ArgumentException)
        {
            return null;
        }
        catch (UriFormatException)
        {
            return null;
        }
    }

    private static FilePickerFileType CreateProjectFileType()
    {
        return new FilePickerFileType("jBS2BG project")
        {
            Patterns = ProjectPatterns, MimeTypes = new[] { "application/json" }
        };
    }

    private static FilePickerFileType CreateBundleFileType()
    {
        return new FilePickerFileType("Portable bundle zip")
        {
            Patterns = new[] { "*.zip" }, MimeTypes = new[] { "application/zip" }
        };
    }
}
