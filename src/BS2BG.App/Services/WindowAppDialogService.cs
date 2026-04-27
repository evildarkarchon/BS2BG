using System.Diagnostics.CodeAnalysis;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;

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
}
