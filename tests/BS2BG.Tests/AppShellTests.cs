using System.Diagnostics.CodeAnalysis;
using Avalonia.Controls;
using Avalonia.Headless.XUnit;
using Avalonia.Input;
using Avalonia.LogicalTree;
using Avalonia;
using BS2BG.App;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

[SuppressMessage("Performance", "CA1861:Avoid constant arrays as arguments", Justification = "Small expected arrays keep shell assertions readable.")]
public sealed class AppShellTests
{
    [Fact]
    public void ServiceProviderResolvesRootViewModel()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var viewModel = provider.GetRequiredService<MainWindowViewModel>();

        Assert.NotNull(viewModel);
    }

    [AvaloniaFact]
    public void ServiceProviderResolvesMainWindowWithPrdShellSettings()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();

        Assert.IsType<MainWindowViewModel>(window.ViewModel);
        Assert.Equal(AppShell.Title, window.Title);
        Assert.Equal(AppShell.StartupWidth, window.Width);
        Assert.Equal(AppShell.StartupHeight, window.Height);
        Assert.Equal(AppShell.MinWidth, window.MinWidth);
        Assert.Equal(AppShell.MinHeight, window.MinHeight);
    }

    [AvaloniaFact]
    public void MainWindowTitleTracksRootViewModelTitle()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var project = provider.GetRequiredService<BS2BG.Core.Models.ProjectModel>();

        project.SliderPresets.Add(new BS2BG.Core.Models.SliderPreset("Alpha"));

        Assert.Equal(AppShell.Title + " *", window.Title);
    }

    [AvaloniaFact]
    public void MainWindowExposesTemplatesAndMorphsWorkspaces()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();

        var tabHeaders = window.GetLogicalDescendants()
            .OfType<TabItem>()
            .Select(tab => tab.Header?.ToString())
            .ToArray();
        Assert.Contains("Templates", tabHeaders);
        Assert.Contains("Morphs", tabHeaders);
    }

    [AvaloniaFact]
    public void MainWindowExposesM5FileAndHelpMenus()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();

        Assert.Equal(
            new[]
            {
                "New",
                "Open...",
                "Save",
                "Save As...",
                "Export Templates as BoS JSON",
                "Export BodyGen INIs",
                "About Bodyslide to Bodygen",
            },
            window.GetLogicalDescendants()
                .OfType<MenuItem>()
                .Where(item => item.Name is
                    "NewProjectMenuItem"
                    or "OpenProjectMenuItem"
                    or "SaveProjectMenuItem"
                    or "SaveProjectAsMenuItem"
                    or "ExportBosJsonMenuItem"
                    or "ExportBodyGenInisMenuItem"
                    or "AboutMenuItem")
                .Select(item => item.Header?.ToString())
                .ToArray());
    }

    [AvaloniaFact]
    public void MainWindowRoutesM5CommandsThroughMenuItemsAndKeyBindings()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        var viewModel = Assert.IsType<MainWindowViewModel>(window.ViewModel);
        window.ApplyTemplate();

        Assert.Same(
            viewModel.NewProjectCommand,
            window.FindControl<MenuItem>("NewProjectMenuItem")?.Command);
        Assert.Same(
            viewModel.SaveProjectAsCommand,
            window.FindControl<MenuItem>("SaveProjectAsMenuItem")?.Command);
        Assert.Same(
            viewModel.ExportBodyGenInisCommand,
            window.FindControl<MenuItem>("ExportBodyGenInisMenuItem")?.Command);
        Assert.NotNull(window.FindControl<TextBlock>("ShellStatusText"));

        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.SaveProjectAsCommand)
            && binding.Gesture?.Key == Key.S
            && binding.Gesture.KeyModifiers == (KeyModifiers.Control | KeyModifiers.Alt));
        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.ExportBosJsonCommand)
            && binding.Gesture?.Key == Key.B
            && binding.Gesture.KeyModifiers == KeyModifiers.Control);
        Assert.Contains(window.KeyBindings, binding =>
            ReferenceEquals(binding.Command, viewModel.ExportBodyGenInisCommand)
            && binding.Gesture?.Key == Key.X
            && binding.Gesture.KeyModifiers == KeyModifiers.Control);
    }

    [AvaloniaFact]
    public void MainWindowExposesM4InspectorAndParityActions()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        var window = provider.GetRequiredService<MainWindow>();
        window.Width = AppShell.MinWidth;
        window.Height = AppShell.MinHeight;
        window.ApplyTemplate();
        window.Measure(new Size(AppShell.MinWidth, AppShell.MinHeight));
        window.Arrange(new Rect(0, 0, AppShell.MinWidth, AppShell.MinHeight));

        Assert.NotNull(window.FindControl<ItemsControl>("SetSliderInspectorRows"));
        Assert.NotNull(window.FindControl<TextBox>("BosJsonTextBox"));
        Assert.NotNull(window.FindControl<Button>("ViewImageButton"));
    }

    [Fact]
    public void ServiceProviderResolvesM4WindowServices()
    {
        using var provider = AppBootstrapper.CreateServiceProvider();

        Assert.IsType<NpcImageLookupService>(provider.GetRequiredService<INpcImageLookupService>());
        Assert.IsType<WindowImageViewService>(provider.GetRequiredService<IImageViewService>());
        Assert.IsType<WindowNoPresetNotificationService>(
            provider.GetRequiredService<INoPresetNotificationService>());
    }

    [AvaloniaFact]
    public void AboutDialogUsesRequiredCreditsAndSizing()
    {
        var service = new WindowAppDialogService();

        var window = service.CreateAboutWindow();

        Assert.Equal(AppShell.Title, window.Title);
        Assert.Equal(400, window.Width);
        Assert.Equal(200, window.Height);
        Assert.False(window.CanResize);

        var text = string.Join(
            "\n",
            window.GetLogicalDescendants()
                .OfType<TextBlock>()
                .Select(block => block.Text));
        Assert.Contains("Bodyslide to Bodygen", text, StringComparison.Ordinal);
        Assert.Contains("Totiman / asdasfa", text, StringComparison.Ordinal);
        Assert.Contains("evildarkarchon", text, StringComparison.Ordinal);
    }
}
