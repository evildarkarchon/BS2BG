using Avalonia.Headless.XUnit;
using BS2BG.App;
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
}
