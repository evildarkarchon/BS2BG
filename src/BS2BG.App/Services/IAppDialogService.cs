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

    void ShowAbout();
}
