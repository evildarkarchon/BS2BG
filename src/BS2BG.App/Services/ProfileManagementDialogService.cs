using Avalonia.Controls;
using Avalonia.Platform.Storage;
using Avalonia.Threading;

namespace BS2BG.App.Services;

/// <summary>
/// Provides the file-picker and destructive-confirmation boundary for custom profile management workflows.
/// ViewModels receive local paths and decisions only, keeping Avalonia storage objects out of editor state.
/// </summary>
public interface IProfileManagementDialogService
{
    Task<IReadOnlyList<string>> PickProfileImportFilesAsync(CancellationToken cancellationToken);

    Task<string?> PickProfileExportPathAsync(string suggestedFileName, CancellationToken cancellationToken);

    Task<bool> ConfirmDeleteProfileAsync(string profileName, CancellationToken cancellationToken);

    Task<bool> ConfirmDeleteReferencedProfileAsync(string profileName, int affectedPresetCount, CancellationToken cancellationToken);

    Task<bool> ConfirmDiscardUnsavedEditsAsync(CancellationToken cancellationToken);
}

/// <summary>
/// Window-bound implementation for profile JSON import/export pickers and profile-management confirmations.
/// </summary>
public sealed class ProfileManagementDialogService : IProfileManagementDialogService
{
    private static readonly FilePickerFileType ProfileJsonFileType = new("BS2BG profile JSON")
    {
        Patterns = ["*.json"],
        MimeTypes = ["application/json"]
    };

    private Window? owner;

    /// <summary>
    /// Attaches the host window used for modal dialogs and storage provider access.
    /// </summary>
    /// <param name="owner">The active application shell window.</param>
    public void Attach(Window owner) => this.owner = owner ?? throw new ArgumentNullException(nameof(owner));

    public async Task<IReadOnlyList<string>> PickProfileImportFilesAsync(CancellationToken cancellationToken)
    {
        if (owner?.StorageProvider.CanOpen != true) return Array.Empty<string>();

        var files = await owner.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Import Profile JSON",
            AllowMultiple = true,
            FileTypeFilter = [ProfileJsonFileType]
        });
        cancellationToken.ThrowIfCancellationRequested();

        return files.Where(file => file.Path.IsFile).Select(file => file.Path.LocalPath).ToArray();
    }

    public async Task<string?> PickProfileExportPathAsync(string suggestedFileName, CancellationToken cancellationToken)
    {
        if (owner?.StorageProvider.CanSave != true) return null;

        var file = await owner.StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
        {
            Title = "Export Profile JSON",
            SuggestedFileName = string.IsNullOrWhiteSpace(suggestedFileName) ? "profile.json" : suggestedFileName,
            DefaultExtension = "json",
            FileTypeChoices = [ProfileJsonFileType]
        });
        cancellationToken.ThrowIfCancellationRequested();

        return file?.Path.IsFile == true ? file.Path.LocalPath : null;
    }

    public Task<bool> ConfirmDeleteProfileAsync(string profileName, CancellationToken cancellationToken) =>
        ConfirmAsync(
            "Delete Custom Profile",
            "Delete this custom profile?\n\nThis removes the local profile JSON from your user profiles folder. Bundled profiles and saved projects are not changed.",
            "Delete Profile",
            "Keep Profile",
            cancellationToken);

    public Task<bool> ConfirmDeleteReferencedProfileAsync(
        string profileName,
        int affectedPresetCount,
        CancellationToken cancellationToken) =>
        ConfirmAsync(
            "Delete Custom Profile",
            $"Profile '{profileName}' is referenced by {affectedPresetCount} preset(s). Delete the local file and keep those project references unresolved?",
            "Delete Profile",
            "Keep Profile",
            cancellationToken);

    public Task<bool> ConfirmDiscardUnsavedEditsAsync(CancellationToken cancellationToken) =>
        ConfirmAsync(
            "Discard unsaved profile edits?",
            "Your profile editor changes have not been saved to the local profile file.",
            "Discard Edits",
            "Keep Editing",
            cancellationToken);

    private async Task<bool> ConfirmAsync(
        string title,
        string message,
        string primaryText,
        string secondaryText,
        CancellationToken cancellationToken)
    {
        if (owner is null) return true;

        var window = WindowAppDialogFactory.CreateConfirmationWindow(title, message, primaryText, secondaryText);
        using var registration = cancellationToken.Register(() => Dispatcher.UIThread.Post(() =>
        {
            if (window.IsVisible) window.Close(false);
        }));
        return await window.ShowDialog<bool>(owner);
    }
}

internal static class WindowAppDialogFactory
{
    /// <summary>
    /// Creates a simple modal confirmation window shared by small App dialog services.
    /// </summary>
    public static Window CreateConfirmationWindow(string title, string message, string primaryText, string secondaryText)
    {
        var okButton = new Button { Content = primaryText, Width = 140, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center };
        var cancelButton = new Button { Content = secondaryText, Width = 140, HorizontalContentAlignment = Avalonia.Layout.HorizontalAlignment.Center };
        var window = new Window
        {
            Title = title,
            Width = 460,
            Height = 240,
            MinWidth = 460,
            MinHeight = 240,
            CanResize = false,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Content = new StackPanel
            {
                Margin = new Avalonia.Thickness(16),
                Spacing = 12,
                Children =
                {
                    new TextBlock { Text = title, FontSize = 16, FontWeight = Avalonia.Media.FontWeight.SemiBold },
                    new TextBlock { Text = message, TextWrapping = Avalonia.Media.TextWrapping.Wrap },
                    new StackPanel
                    {
                        Orientation = Avalonia.Layout.Orientation.Horizontal,
                        HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Right,
                        Spacing = 8,
                        Children = { okButton, cancelButton }
                    }
                }
            }
        };

        okButton.Click += (_, _) => window.Close(true);
        cancelButton.Click += (_, _) => window.Close(false);
        return window;
    }
}
