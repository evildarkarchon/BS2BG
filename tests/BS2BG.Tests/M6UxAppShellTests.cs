using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Headless.XUnit;
using Avalonia.Input;
using Avalonia.LogicalTree;
using BS2BG.App;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

public sealed class M6UxAppShellTests
{
    [AvaloniaFact]
    public void MainWindowExposesM6SearchPaletteThemeAndSelectionControls()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.ApplyTemplate();

        Assert.NotNull(window.FindControl<TextBox>("GlobalSearchBox"));
        Assert.NotNull(window.FindControl<Popup>("CommandPalettePopup"));
        Assert.NotNull(window.FindControl<ComboBox>("ThemePreferenceComboBox"));
        Assert.NotNull(window.FindControl<MenuItem>("UndoMenuItem"));
        Assert.NotNull(window.FindControl<MenuItem>("RedoMenuItem"));
        Assert.NotNull(window.FindControl<Button>("NpcFilterRaceButton"));
        Assert.NotNull(window.FindControl<Button>("SelectedNpcAssignButton"));
        Assert.NotNull(window.FindControl<Button>("SelectedNpcClearAssignmentsButton"));
    }

    [AvaloniaFact]
    public void MainWindowRoutesM6ShortcutsAndDropZones()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var viewModel = Assert.IsType<MainWindowViewModel>(window.ViewModel);
        window.ApplyTemplate();

        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.FocusGlobalSearchCommand)
            && binding.Gesture?.Key == Key.F
            && binding.Gesture.KeyModifiers == KeyModifiers.Control);
        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.OpenCommandPaletteCommand)
            && binding.Gesture?.Key == Key.P
            && binding.Gesture.KeyModifiers == (KeyModifiers.Control | KeyModifiers.Shift));

        Assert.True(DragDrop.GetAllowDrop(Assert.IsAssignableFrom<Control>(
            window.FindControl<Control>("TemplatesDropZone"))));
        Assert.True(DragDrop.GetAllowDrop(Assert.IsAssignableFrom<Control>(
            window.FindControl<Control>("NpcDropZone"))));
        Assert.Contains(
            "Generate Templates",
            viewModel.CommandPaletteItems.Select(item => item.Title));
    }
}
