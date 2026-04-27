using BS2BG.Core.Diagnostics;

namespace BS2BG.App.Services;

public enum DiscardChangesAction
{
    NewProject,
    OpenProject
}

public interface IAppDialogService
{
    Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken);

    Task<bool> ConfirmBulkOperationAsync(string title, string message, CancellationToken cancellationToken);

    /// <summary>
    /// Asks the user to approve an export that may replace existing output files.
    /// </summary>
    /// <param name="preview">Read-only preview facts that list the target files and snippets.</param>
    /// <param name="cancellationToken">Cancels the dialog and treats the export as not approved.</param>
    /// <returns><see langword="true" /> when the export may proceed; otherwise <see langword="false" />.</returns>
    Task<bool> ConfirmExportOverwriteAsync(ExportPreviewResult preview, CancellationToken cancellationToken);

    void ShowAbout();
}
