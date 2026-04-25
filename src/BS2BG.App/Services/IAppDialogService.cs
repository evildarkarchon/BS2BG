namespace BS2BG.App.Services;

public enum DiscardChangesAction
{
    NewProject,
    OpenProject
}

public interface IAppDialogService
{
    Task<bool> ConfirmDiscardChangesAsync(DiscardChangesAction action, CancellationToken cancellationToken);

    void ShowAbout();
}
