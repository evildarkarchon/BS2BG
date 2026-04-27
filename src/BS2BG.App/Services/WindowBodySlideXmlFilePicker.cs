using Avalonia.Controls;
using Avalonia.Platform.Storage;

namespace BS2BG.App.Services;

public sealed class WindowBodySlideXmlFilePicker : IBodySlideXmlFilePicker
{
    private readonly IBodySlideXmlPickerBackend backend;
    private readonly IUserPreferencesService preferencesService;

    public WindowBodySlideXmlFilePicker()
        : this(new UserPreferencesService(), new AvaloniaBodySlideXmlPickerBackend())
    {
    }

    public WindowBodySlideXmlFilePicker(IUserPreferencesService preferencesService)
        : this(preferencesService, new AvaloniaBodySlideXmlPickerBackend())
    {
    }

    public WindowBodySlideXmlFilePicker(
        IUserPreferencesService preferencesService,
        IBodySlideXmlPickerBackend backend)
    {
        this.preferencesService = preferencesService ?? throw new ArgumentNullException(nameof(preferencesService));
        this.backend = backend ?? throw new ArgumentNullException(nameof(backend));
    }

    public async Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(CancellationToken cancellationToken)
    {
        if (!backend.CanOpen) return Array.Empty<string>();

        var preferences = preferencesService.Load();
        var startFolder = await backend.ResolveStartFolderAsync(preferences.BodySlideXmlFolder, cancellationToken);
        var files = await backend.PickXmlPresetFilesAsync(startFolder, cancellationToken);

        cancellationToken.ThrowIfCancellationRequested();

        SaveFolderPreference(preferences, files.Count > 0 ? files[0] : null);
        return files;
    }

    public void Attach(TopLevel topLevel)
    {
        ArgumentNullException.ThrowIfNull(topLevel);
        if (backend is AvaloniaBodySlideXmlPickerBackend avaloniaBackend) avaloniaBackend.Attach(topLevel);
    }

    private void SaveFolderPreference(UserPreferences preferences, string? selectedFilePath)
    {
        if (string.IsNullOrWhiteSpace(selectedFilePath)) return;

        var folder = Path.GetDirectoryName(selectedFilePath);
        if (string.IsNullOrWhiteSpace(folder)) return;

        preferences.BodySlideXmlFolder = folder;
        preferencesService.Save(preferences);
    }
}

/// <summary>
/// Testable backend seam for BodySlide XML imports. Implementations should treat remembered folders as
/// advisory picker hints so invalid local preference paths never block importing selected XML files.
/// </summary>
public interface IBodySlideXmlPickerBackend
{
    bool CanOpen { get; }

    /// <summary>
    /// Opens the BodySlide XML file picker using the validated start-folder hint when available.
    /// Returns selected local file paths, or an empty list when the picker is cancelled.
    /// </summary>
    Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(
        string? suggestedStartFolder,
        CancellationToken cancellationToken);

    /// <summary>
    /// Validates a remembered local folder path before it is reused as a picker hint.
    /// Returns <see langword="null" /> for missing, invalid, or inaccessible paths.
    /// </summary>
    Task<string?> ResolveStartFolderAsync(string? path, CancellationToken cancellationToken);
}

/// <summary>
/// Avalonia storage-provider adapter for BodySlide XML imports with SuggestedStartLocation support.
/// </summary>
public sealed class AvaloniaBodySlideXmlPickerBackend : IBodySlideXmlPickerBackend
{
    private static readonly string[] XmlPatterns = { "*.xml" };
    private static readonly string[] XmlMimeTypes = { "application/xml", "text/xml" };
    private TopLevel? owner;

    public bool CanOpen => owner?.StorageProvider.CanOpen == true;

    /// <summary>
    /// Attaches the current window so the backend can use its platform storage provider.
    /// </summary>
    public void Attach(TopLevel topLevel) => owner = topLevel ?? throw new ArgumentNullException(nameof(topLevel));

    /// <inheritdoc />
    public async Task<IReadOnlyList<string>> PickXmlPresetFilesAsync(
        string? suggestedStartFolder,
        CancellationToken cancellationToken)
    {
        if (owner is null) return Array.Empty<string>();

        var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Add BodySlide XML Presets",
            AllowMultiple = true,
            SuggestedStartLocation = await TryGetFolderAsync(suggestedStartFolder),
            FileTypeFilter = new[]
            {
                new FilePickerFileType("BodySlide XML") { Patterns = XmlPatterns, MimeTypes = XmlMimeTypes }
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
