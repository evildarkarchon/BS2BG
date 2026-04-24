using System.Diagnostics.CodeAnalysis;
using Avalonia.Controls;
using Avalonia.Layout;
using BS2BG.App;

namespace BS2BG.App.Services;

[SuppressMessage("Performance", "CA1822:Mark members as static", Justification = "Dialog creation stays on the injectable service for tests and UI wiring.")]
public sealed class WindowAppDialogService : IAppDialogService
{
    private Window? owner;

    public void Attach(Window owner)
    {
        this.owner = owner ?? throw new ArgumentNullException(nameof(owner));
    }

    public async Task<bool> ConfirmDiscardChangesAsync(
        DiscardChangesAction action,
        CancellationToken cancellationToken)
    {
        if (owner is null)
        {
            return true;
        }

        var window = CreateDiscardWindow(action);
        using var registration = cancellationToken.Register(() => window.Close(false));
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
                Margin = new Avalonia.Thickness(18),
                Spacing = 8,
                VerticalAlignment = VerticalAlignment.Center,
                Children =
                {
                    new TextBlock
                    {
                        Text = AppShell.Title,
                        FontSize = 18,
                        FontWeight = Avalonia.Media.FontWeight.SemiBold,
                        HorizontalAlignment = HorizontalAlignment.Center,
                    },
                    new TextBlock
                    {
                        Text = "Original jBS2BG author: Totiman / asdasfa",
                        HorizontalAlignment = HorizontalAlignment.Center,
                    },
                    new TextBlock
                    {
                        Text = "C#/Avalonia port author: evildarkarchon",
                        HorizontalAlignment = HorizontalAlignment.Center,
                    },
                    new TextBlock
                    {
                        Text = "Generates RaceMenu BodyGen templates, morphs INI, and BoS JSON.",
                        TextWrapping = Avalonia.Media.TextWrapping.Wrap,
                        TextAlignment = Avalonia.Media.TextAlignment.Center,
                    },
                },
            },
        };
    }

    private static Window CreateDiscardWindow(DiscardChangesAction action)
    {
        var isOpen = action == DiscardChangesAction.OpenProject;
        var okButton = new Button
        {
            Content = isOpen ? "Open Another" : "New",
            Width = 110,
            HorizontalContentAlignment = HorizontalAlignment.Center,
        };
        var cancelButton = new Button
        {
            Content = "Cancel",
            Width = 100,
            HorizontalContentAlignment = HorizontalAlignment.Center,
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
                Margin = new Avalonia.Thickness(14),
                Spacing = 12,
                Children =
                {
                    new TextBlock
                    {
                        Text = isOpen ? "Open File" : "New File",
                        FontSize = 16,
                        FontWeight = Avalonia.Media.FontWeight.SemiBold,
                    },
                    new TextBlock
                    {
                        Text = isOpen
                            ? "You still have a file open with some unsaved changes.\nAll unsaved changes will be discarded."
                            : "You're starting a new file.\nAll unsaved changes will be discarded.",
                        TextWrapping = Avalonia.Media.TextWrapping.Wrap,
                    },
                    new StackPanel
                    {
                        Orientation = Orientation.Horizontal,
                        HorizontalAlignment = HorizontalAlignment.Right,
                        Spacing = 8,
                        Children =
                        {
                            okButton,
                            cancelButton,
                        },
                    },
                },
            },
        };

        okButton.Click += (_, _) => window.Close(true);
        cancelButton.Click += (_, _) => window.Close(false);
        return window;
    }
}
