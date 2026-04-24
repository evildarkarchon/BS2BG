using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
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
        services.AddSingleton<ProjectModel>();
        services.AddSingleton<BodySlideXmlParser>();
        services.AddSingleton<TemplateGenerationService>();
        services.AddSingleton(_ => TemplateProfileCatalogFactory.CreateDefault());
        services.AddSingleton<WindowBodySlideXmlFilePicker>();
        services.AddSingleton<IBodySlideXmlFilePicker>(provider =>
            provider.GetRequiredService<WindowBodySlideXmlFilePicker>());
        services.AddSingleton<WindowClipboardService>();
        services.AddSingleton<IClipboardService>(provider =>
            provider.GetRequiredService<WindowClipboardService>());
        services.AddSingleton<TemplatesViewModel>();
        services.AddSingleton<MainWindowViewModel>();
        services.AddTransient<MainWindow>();
    }

    public static void SetServiceProvider(IServiceProvider? provider)
    {
        serviceProvider = provider ?? throw new ArgumentNullException(nameof(provider));
    }
}
