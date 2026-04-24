namespace BS2BG.App.Services;

public interface IFileDialogService
{
    Task<string?> PickOpenProjectFileAsync(CancellationToken cancellationToken);

    Task<string?> PickSaveProjectFileAsync(string? currentPath, CancellationToken cancellationToken);

    Task<string?> PickBodyGenExportFolderAsync(CancellationToken cancellationToken);

    Task<string?> PickBosJsonExportFolderAsync(CancellationToken cancellationToken);
}
