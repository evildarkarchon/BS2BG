using System.Diagnostics.CodeAnalysis;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using BS2BG.Core.Diagnostics;

namespace BS2BG.App.Services;

[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "Dialog creation stays on the injectable service for tests and UI wiring.")]
public sealed class WindowAppDialogService : IAppDialogService
{
    private Window? owner;

    public async Task<bool> ConfirmDiscardChangesAsync(
        DiscardChangesAction action,
        CancellationToken cancellationToken)
    {
        if (owner is null) return true;

        var window = CreateDiscardWindow(action);
        using var registration = cancellationToken.Register(() => Dispatcher.UIThread.Post(() =>
        {
            if (window.IsVisible) window.Close(false);
        }));
        return await window.ShowDialog<bool>(owner);
    }

    public async Task<bool> ConfirmBulkOperationAsync(
        string title,
        string message,
        CancellationToken cancellationToken)
    {
        if (owner is null) return true;

        var window = CreateConfirmationWindow(title, message);
        using var registration = cancellationToken.Register(() => Dispatcher.UIThread.Post(() =>
        {
            if (window.IsVisible) window.Close(false);
        }));
        return await window.ShowDialog<bool>(owner);
    }

    public async Task<bool> ConfirmExportOverwriteAsync(
        ExportPreviewResult preview,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(preview);

        if (owner is null) return true;

        var window = CreateExportOverwriteWindow(preview);
        using var registration = cancellationToken.Register(() => Dispatcher.UIThread.Post(() =>
        {
            if (window.IsVisible) window.Close(false);
        }));
        return await window.ShowDialog<bool>(owner);
    }

    /// <summary>
    /// Shows the embedded/local profile conflict prompt and returns the selected one-conflict decision.
    /// </summary>
    /// <param name="request">Conflict identity and source summaries to present.</param>
    /// <param name="cancellationToken">Cancels the dialog and returns <see langword="null" />.</param>
    /// <returns>The selected conflict decision, or <see langword="null" /> if the user cancels.</returns>
    public async Task<ProfileConflictDecision?> PromptProfileConflictAsync(
        ProfileConflictRequest request,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(request);

        if (owner is null) return null;

        var window = CreateProfileConflictWindow(request);
        using var registration = cancellationToken.Register(() => Dispatcher.UIThread.Post(() =>
        {
            if (window.IsVisible) window.Close(null);
        }));
        return await window.ShowDialog<ProfileConflictDecision?>(owner);
    }

    public void ShowAbout()
    {
        var window = CreateAboutWindow();
        if (owner is null)
        {
            window.Show();
            return;
        }

        _ = window.ShowDialog(owner);
    }

    public void Attach(Window owner) => this.owner = owner ?? throw new ArgumentNullException(nameof(owner));

    public Window CreateAboutWindow()
    {
        return new Window
        {
            Title = AppShell.Title,
            Width = 400,
            Height = 200,
            MinWidth = 400,
            MinHeight = 200,
            CanResize = false,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Content = new StackPanel
            {
                Margin = new Thickness(18),
                Spacing = 8,
                VerticalAlignment = VerticalAlignment.Center,
                Children =
                {
                    new TextBlock
                    {
                        Text = AppShell.Title,
                        FontSize = 18,
                        FontWeight = FontWeight.SemiBold,
                        HorizontalAlignment = HorizontalAlignment.Center
                    },
                    new TextBlock
                    {
                        Text = "Original jBS2BG author: Totiman / asdasfa",
                        HorizontalAlignment = HorizontalAlignment.Center
                    },
                    new TextBlock
                    {
                        Text = "C#/Avalonia port author: evildarkarchon",
                        HorizontalAlignment = HorizontalAlignment.Center
                    },
                    new TextBlock
                    {
                        Text = "Generates RaceMenu BodyGen templates, morphs INI, and BoS JSON.",
                        TextWrapping = TextWrapping.Wrap,
                        TextAlignment = TextAlignment.Center
                    }
                }
            }
        };
    }

    private static Window CreateDiscardWindow(DiscardChangesAction action)
    {
        var isOpen = action == DiscardChangesAction.OpenProject;
        var okButton = new Button
        {
            Content = isOpen ? "Open Another" : "New",
            Width = 110,
            HorizontalContentAlignment = HorizontalAlignment.Center
        };
        var cancelButton = new Button
        {
            Content = "Cancel", Width = 100, HorizontalContentAlignment = HorizontalAlignment.Center
        };
        var window = new Window
        {
            Title = "Confirm Action",
            Width = 400,
            Height = 220,
            MinWidth = 400,
            MinHeight = 220,
            CanResize = false,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Content = new StackPanel
            {
                Margin = new Thickness(14),
                Spacing = 12,
                Children =
                {
                    new TextBlock
                    {
                        Text = isOpen ? "Open File" : "New File", FontSize = 16, FontWeight = FontWeight.SemiBold
                    },
                    new TextBlock
                    {
                        Text = isOpen
                            ? "You still have a file open with some unsaved changes.\nAll unsaved changes will be discarded."
                            : "You're starting a new file.\nAll unsaved changes will be discarded.",
                        TextWrapping = TextWrapping.Wrap
                    },
                    new StackPanel
                    {
                        Orientation = Orientation.Horizontal,
                        HorizontalAlignment = HorizontalAlignment.Right,
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

    private static Window CreateConfirmationWindow(string title, string message)
    {
        var okButton = new Button
        {
            Content = "Continue",
            Width = 110,
            HorizontalContentAlignment = HorizontalAlignment.Center
        };
        var cancelButton = new Button
        {
            Content = "Cancel", Width = 100, HorizontalContentAlignment = HorizontalAlignment.Center
        };
        var window = new Window
        {
            Title = title,
            Width = 440,
            Height = 220,
            MinWidth = 440,
            MinHeight = 220,
            CanResize = false,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Content = new StackPanel
            {
                Margin = new Thickness(14),
                Spacing = 12,
                Children =
                {
                    new TextBlock { Text = title, FontSize = 16, FontWeight = FontWeight.SemiBold },
                    new TextBlock { Text = message, TextWrapping = TextWrapping.Wrap },
                    new StackPanel
                    {
                        Orientation = Orientation.Horizontal,
                        HorizontalAlignment = HorizontalAlignment.Right,
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

    private static Window CreateProfileConflictWindow(ProfileConflictRequest request)
    {
        const string title = "Profile conflict found";
        var renameBox = new TextBox
        {
            PlaceholderText = "Unique project profile name",
            Width = 240,
            Text = request.ProfileName + " (Project Copy)"
        };
        var useProjectButton = CreateDecisionButton("Use Project Copy", 160);
        var replaceLocalButton = CreateDecisionButton("Replace Local Profile", 170);
        var renameProjectButton = CreateDecisionButton("Rename Project Copy", 170);
        var keepLocalButton = CreateDecisionButton("Keep Local Profile", 160);
        var cancelButton = CreateDecisionButton("Cancel", 100);
        var window = new Window
        {
            Title = title,
            Width = 640,
            Height = 420,
            MinWidth = 640,
            MinHeight = 420,
            CanResize = false,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Content = new StackPanel
            {
                Margin = new Thickness(16),
                Spacing = 12,
                Children =
                {
                    new TextBlock { Text = title, FontSize = 18, FontWeight = FontWeight.SemiBold },
                    new TextBlock
                    {
                        Text = $"The project contains profile {request.ProfileName} and a local custom profile with the same display name. Choose which profile data to use; BS2BG will not choose silently.",
                        TextWrapping = TextWrapping.Wrap
                    },
                    new TextBlock { Text = "Local: " + request.LocalSummary, TextWrapping = TextWrapping.Wrap },
                    new TextBlock { Text = "Project: " + request.EmbeddedSummary, TextWrapping = TextWrapping.Wrap },
                    new TextBlock
                    {
                        Text = "Replace local profile? The embedded or imported profile has the same display name as a local custom profile. Replacing updates your local profile file; choose rename or keep local if you are unsure.",
                        TextWrapping = TextWrapping.Wrap,
                        Foreground = Brushes.Red
                    },
                    new StackPanel
                    {
                        Orientation = Orientation.Horizontal,
                        Spacing = 8,
                        Children = { new TextBlock { Text = "Rename to:", VerticalAlignment = VerticalAlignment.Center }, renameBox }
                    },
                    new StackPanel
                    {
                        Orientation = Orientation.Horizontal,
                        HorizontalAlignment = HorizontalAlignment.Right,
                        Spacing = 8,
                        Children = { useProjectButton, replaceLocalButton, renameProjectButton, keepLocalButton, cancelButton }
                    }
                }
            }
        };

        useProjectButton.Click += (_, _) => window.Close(new ProfileConflictDecision(ProfileConflictResolution.UseProjectCopy, null));
        replaceLocalButton.Click += (_, _) => window.Close(new ProfileConflictDecision(ProfileConflictResolution.ReplaceLocalProfile, null));
        renameProjectButton.Click += (_, _) => window.Close(new ProfileConflictDecision(ProfileConflictResolution.RenameProjectCopy, renameBox.Text));
        keepLocalButton.Click += (_, _) => window.Close(new ProfileConflictDecision(ProfileConflictResolution.KeepLocalProfile, null));
        cancelButton.Click += (_, _) => window.Close(null);
        return window;
    }

    private static Button CreateDecisionButton(string content, double width) => new()
    {
        Content = content,
        Width = width,
        HorizontalContentAlignment = HorizontalAlignment.Center
    };

    private static Window CreateExportOverwriteWindow(ExportPreviewResult preview)
    {
        const string title = "Overwrite existing output files?";
        const string message =
            "BS2BG will write the files listed below. Existing targets may be replaced; if a write fails, the result ledger will show what was written, restored, skipped, or left untouched.";
        var fileList = string.Join(
            Environment.NewLine,
            preview.Files.Select(file => (file.WillOverwrite ? "Overwrite: " : "Create: ") + file.Path));
        var okButton = new Button
        {
            Content = "Export Anyway",
            Width = 140,
            HorizontalContentAlignment = HorizontalAlignment.Center
        };
        var cancelButton = new Button
        {
            Content = "Keep Existing Files", Width = 160, HorizontalContentAlignment = HorizontalAlignment.Center
        };
        var window = new Window
        {
            Title = title,
            Width = 560,
            Height = 320,
            MinWidth = 560,
            MinHeight = 320,
            CanResize = false,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Content = new StackPanel
            {
                Margin = new Thickness(14),
                Spacing = 12,
                Children =
                {
                    new TextBlock { Text = title, FontSize = 16, FontWeight = FontWeight.SemiBold },
                    new TextBlock { Text = message, TextWrapping = TextWrapping.Wrap },
                    new TextBlock
                    {
                        Text = fileList,
                        FontFamily = FontFamily.Parse("Consolas"),
                        TextWrapping = TextWrapping.Wrap
                    },
                    new StackPanel
                    {
                        Orientation = Orientation.Horizontal,
                        HorizontalAlignment = HorizontalAlignment.Right,
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
