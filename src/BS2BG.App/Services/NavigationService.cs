using BS2BG.App.ViewModels;

namespace BS2BG.App.Services;

/// <summary>
/// Provides shell-owned workspace navigation requests without coupling child ViewModels to the root window ViewModel.
/// Consumers request a workspace; the shell/root ViewModel decides how that maps to visible tabs.
/// </summary>
public interface INavigationService
{
    /// <summary>
    /// Raised when a consumer requests navigation to a top-level application workspace.
    /// </summary>
    event EventHandler<AppWorkspace>? WorkspaceRequested;

    /// <summary>
    /// Requests navigation to the supplied top-level workspace.
    /// </summary>
    /// <param name="workspace">The shell workspace that should become active.</param>
    void NavigateTo(AppWorkspace workspace);
}

/// <summary>
/// Default in-process navigation request broker for the single-window Avalonia shell.
/// </summary>
public sealed class NavigationService : INavigationService
{
    public event EventHandler<AppWorkspace>? WorkspaceRequested;

    public void NavigateTo(AppWorkspace workspace) => WorkspaceRequested?.Invoke(this, workspace);
}
