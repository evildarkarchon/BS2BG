using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using Microsoft.Extensions.DependencyInjection;

namespace BS2BG.App;

public static class AppBootstrapper
{
    private static IServiceProvider? serviceProvider;

    public static IServiceProvider Services => serviceProvider ??= CreateServiceProvider();

    public static ServiceProvider CreateServiceProvider()
    {
        var services = new ServiceCollection();
        ConfigureServices(services);
        return services.BuildServiceProvider(validateScopes: true);
    }

    public static void ConfigureServices(IServiceCollection services)
    {
        services.AddSingleton<MainWindowViewModel>();
        services.AddTransient<MainWindow>();
    }

    public static void SetServiceProvider(IServiceProvider? provider)
    {
        serviceProvider = provider ?? throw new ArgumentNullException(nameof(provider));
    }
}
