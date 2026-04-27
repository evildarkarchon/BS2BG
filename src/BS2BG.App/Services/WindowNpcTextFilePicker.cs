using Avalonia.Controls;
using Avalonia.Platform.Storage;

namespace BS2BG.App.Services;

public sealed class WindowNpcTextFilePicker : INpcTextFilePicker
{
    private readonly INpcTextPickerBackend backend;
    private readonly IUserPreferencesService preferencesService;

    public WindowNpcTextFilePicker()
        : this(new UserPreferencesService(), new AvaloniaNpcTextPickerBackend())
    {
    }

    public WindowNpcTextFilePicker(IUserPreferencesService preferencesService)
        : this(preferencesService, new AvaloniaNpcTextPickerBackend())
    {
    }

    public WindowNpcTextFilePicker(IUserPreferencesService preferencesService, INpcTextPickerBackend backend)
    {
        this.preferencesService = preferencesService ?? throw new ArgumentNullException(nameof(preferencesService));
        this.backend = backend ?? throw new ArgumentNullException(nameof(backend));
    }

    public async Task<IReadOnlyList<string>> PickNpcTextFilesAsync(CancellationToken cancellationToken)
    {
        if (!backend.CanOpen) return Array.Empty<string>();

        var preferences = preferencesService.Load();
        var startFolder = await backend.ResolveStartFolderAsync(preferences.NpcTextFolder, cancellationToken);
        var files = await backend.PickNpcTextFilesAsync(startFolder, cancellationToken);

        cancellationToken.ThrowIfCancellationRequested();

        SaveFolderPreference(preferences, files.Count > 0 ? files[0] : null);
        return files;
    }

    public void Attach(TopLevel topLevel)
    {
        ArgumentNullException.ThrowIfNull(topLevel);
        if (backend is AvaloniaNpcTextPickerBackend avaloniaBackend) avaloniaBackend.Attach(topLevel);
    }

    private void SaveFolderPreference(UserPreferences preferences, string? selectedFilePath)
    {
        if (string.IsNullOrWhiteSpace(selectedFilePath)) return;

        var folder = Path.GetDirectoryName(selectedFilePath);
        if (string.IsNullOrWhiteSpace(folder)) return;

        preferences.NpcTextFolder = folder;
        preferencesService.Save(preferences);
    }
}

/// <summary>
/// Testable backend seam for NPC text imports. Implementations keep remembered folder paths advisory
/// so invalid preference state cannot block importing valid NPC text files.
/// </summary>
public interface INpcTextPickerBackend
{
    bool CanOpen { get; }

    /// <summary>
    /// Opens the NPC text file picker using the validated start-folder hint when available.
    /// Returns selected local file paths, or an empty list when the picker is cancelled.
    /// </summary>
    Task<IReadOnlyList<string>> PickNpcTextFilesAsync(
        string? suggestedStartFolder,
        CancellationToken cancellationToken);

    /// <summary>
    /// Validates a remembered local folder path before it is reused as a picker hint.
    /// Returns <see langword="null" /> for missing, invalid, or inaccessible paths.
    /// </summary>
    Task<string?> ResolveStartFolderAsync(string? path, CancellationToken cancellationToken);
}

/// <summary>
/// Avalonia storage-provider adapter for NPC text imports with SuggestedStartLocation support.
/// </summary>
public sealed class AvaloniaNpcTextPickerBackend : INpcTextPickerBackend
{
    private static readonly string[] TextPatterns = { "*.txt" };
    private static readonly string[] TextMimeTypes = { "text/plain" };
    private TopLevel? owner;

    public bool CanOpen => owner?.StorageProvider.CanOpen == true;

    /// <summary>
    /// Attaches the current window so the backend can use its platform storage provider.
    /// </summary>
    public void Attach(TopLevel topLevel) => owner = topLevel ?? throw new ArgumentNullException(nameof(topLevel));

    /// <inheritdoc />
    public async Task<IReadOnlyList<string>> PickNpcTextFilesAsync(
        string? suggestedStartFolder,
        CancellationToken cancellationToken)
    {
        if (owner is null) return Array.Empty<string>();

        var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Add NPC Text File",
            AllowMultiple = true,
            SuggestedStartLocation = await TryGetFolderAsync(suggestedStartFolder),
            FileTypeFilter = new[]
            {
                new FilePickerFileType("NPC text") { Patterns = TextPatterns, MimeTypes = TextMimeTypes }
            }
        });

        cancellationToken.ThrowIfCancellationRequested();

        return files
            .Where(file => file.Path.IsFile)
            .Select(file => file.Path.LocalPath)
            .ToArray();
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
}
