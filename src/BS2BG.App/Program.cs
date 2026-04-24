using Avalonia;
using Avalonia.Fonts.Inter;
using ReactiveUI.Avalonia;
using ReactiveUI.Avalonia.Splat;

namespace BS2BG.App;

internal static class Program
{
    [STAThread]
    public static void Main(string[] args)
    {
        BuildAvaloniaApp().StartWithClassicDesktopLifetime(args);
    }

    public static AppBuilder BuildAvaloniaApp()
    {
        return AppBuilder.Configure<App>()
            .UsePlatformDetect()
            .WithInterFont()
            .RegisterReactiveUIViewsFromEntryAssembly()
            .UseReactiveUIWithMicrosoftDependencyResolver(
                AppBootstrapper.ConfigureServices,
                AppBootstrapper.SetServiceProvider)
            .LogToTrace();
    }
}
