using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using BS2BG.App.Views;
using Microsoft.Extensions.DependencyInjection;

namespace BS2BG.App;

public partial class App : Application
{
    public override void Initialize() => AvaloniaXamlLoader.Load(this);

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
            desktop.MainWindow = AppBootstrapper.Services.GetRequiredService<MainWindow>();

        base.OnFrameworkInitializationCompleted();
    }
}
