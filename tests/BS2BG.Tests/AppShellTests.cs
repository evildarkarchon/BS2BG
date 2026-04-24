using Avalonia.Controls;
using Avalonia.Headless.XUnit;
using Avalonia.LogicalTree;
using Avalonia;
using BS2BG.App;
using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using Microsoft.Extensions.DependencyInjection;
using Xunit;

namespace BS2BG.Tests;

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
}
