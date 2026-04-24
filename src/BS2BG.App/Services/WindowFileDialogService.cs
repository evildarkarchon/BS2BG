using Avalonia.Controls;
using Avalonia.Platform.Storage;

namespace BS2BG.App.Services;

public sealed class WindowFileDialogService : IFileDialogService
{
    private static readonly string[] ProjectPatterns = { "*.jbs2bg" };
    private TopLevel? owner;

    public void Attach(TopLevel topLevel)
    {
        owner = topLevel ?? throw new ArgumentNullException(nameof(topLevel));
    }

    public async Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken)
    {
        if (owner?.StorageProvider.CanOpen != true)
        {
            return null;
        }

        var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Open jBS2BG File",
            AllowMultiple = false,
            FileTypeFilter = new[] { CreateProjectFileType() },
        });

        cancellationToken.ThrowIfCancellationRequested();

        return files.FirstOrDefault(file => file.Path.IsFile)?.Path.LocalPath;
    }

    public async Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken)
    {
        if (owner?.StorageProvider.CanSave != true)
        {
            return null;
        }

        var file = await owner.StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
        {
            Title = "Save jBS2BG File",
            SuggestedFileName = string.IsNullOrWhiteSpace(currentPath)
                ? "project.jbs2bg"
                : Path.GetFileName(currentPath),
            DefaultExtension = "jbs2bg",
            FileTypeChoices = new[] { CreateProjectFileType() },
        });

        cancellationToken.ThrowIfCancellationRequested();

        return file?.Path.IsFile == true ? file.Path.LocalPath : null;
    }

    public Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken)
    {
        return PickFolderAsync("Export Templates and Morphs INI", cancellationToken);
    }

    public Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken)
    {
        return PickFolderAsync("Export BoS JSON files", cancellationToken);
    }

    private async Task<string?> PickFolderAsync(string title, CancellationToken cancellationToken)
    {
        if (owner?.StorageProvider.CanOpen != true)
        {
            return null;
        }

        var folders = await owner.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions
        {
            Title = title,
            AllowMultiple = false,
        });

        cancellationToken.ThrowIfCancellationRequested();

        return folders.Count > 0 ? folders[0].Path.LocalPath : null;
    }

    private static FilePickerFileType CreateProjectFileType()
    {
        return new FilePickerFileType("jBS2BG project")
        {
            Patterns = ProjectPatterns,
            MimeTypes = new[] { "application/json" },
        };
    }
}
