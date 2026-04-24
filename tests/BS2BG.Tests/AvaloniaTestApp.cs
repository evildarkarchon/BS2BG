using Avalonia;
using Avalonia.Headless;
using BS2BG.Tests;
using ReactiveUI.Avalonia;
using AvaloniaApp = BS2BG.App.App;

[assembly: AvaloniaTestApplication(typeof(AvaloniaTestApp))]
[assembly: AvaloniaTestIsolation(AvaloniaTestIsolationLevel.PerAssembly)]

namespace BS2BG.Tests;

public static class AvaloniaTestApp
{
    public static AppBuilder BuildAvaloniaApp()
    {
        return AppBuilder.Configure<AvaloniaApp>()
            .UseHeadless(new AvaloniaHeadlessPlatformOptions())
            .UseReactiveUI(_ => { });
    }
}
