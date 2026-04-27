using BS2BG.App.Services;
using BS2BG.App.ViewModels;
using BS2BG.App.Views;
using BS2BG.Core.Diagnostics;
using BS2BG.Core.Export;
using BS2BG.Core.Generation;
using BS2BG.Core.Import;
using BS2BG.Core.Models;
using BS2BG.Core.Morphs;
using BS2BG.Core.Serialization;
using Microsoft.Extensions.DependencyInjection;

namespace BS2BG.App;

public static class AppBootstrapper
{
    private static IServiceProvider? _serviceProvider;

    public static IServiceProvider Services => _serviceProvider ??= CreateServiceProvider();

    public static ServiceProvider CreateServiceProvider()
    {
        var services = new ServiceCollection();
        ConfigureServices(services);
        return services.BuildServiceProvider(true);
    }

    public static void ConfigureServices(IServiceCollection services)
    {
        services.AddSingleton<ProjectModel>();
        services.AddSingleton<BodySlideXmlParser>();
        services.AddSingleton<NpcTextParser>();
        services.AddSingleton<NpcImportPreviewService>();
        services.AddSingleton<ProjectFileService>();
        services.AddSingleton<TemplateGenerationService>();
        services.AddSingleton<MorphGenerationService>();
        services.AddSingleton<ProfileDefinitionService>();
        services.AddSingleton<ProjectValidationService>();
        services.AddSingleton<ProfileDiagnosticsService>();
        services.AddSingleton<DiagnosticsReportFormatter>();
        services.AddSingleton<BodyGenIniExportWriter>();
        services.AddSingleton<BosJsonExportWriter>();
        services.AddSingleton<UndoRedoService>();
        services.AddSingleton<IUserPreferencesService, UserPreferencesService>();
        services.AddSingleton<IUserProfileStore, UserProfileStore>();
        services.AddSingleton<TemplateProfileCatalogFactory>();
        services.AddSingleton<ITemplateProfileCatalogService, TemplateProfileCatalogService>();
        services.AddSingleton<IRandomAssignmentProvider, RandomAssignmentProvider>();
        services.AddSingleton<MorphAssignmentService>();
        services.AddSingleton(provider =>
            new WindowBodySlideXmlFilePicker(provider.GetRequiredService<IUserPreferencesService>()));
        services.AddSingleton<IBodySlideXmlFilePicker>(provider =>
            provider.GetRequiredService<WindowBodySlideXmlFilePicker>());
        services.AddSingleton(provider =>
            new WindowNpcTextFilePicker(provider.GetRequiredService<IUserPreferencesService>()));
        services.AddSingleton<INpcTextFilePicker>(provider =>
            provider.GetRequiredService<WindowNpcTextFilePicker>());
        services.AddSingleton<WindowClipboardService>();
        services.AddSingleton<IClipboardService>(provider =>
            provider.GetRequiredService<WindowClipboardService>());
        services.AddSingleton<INpcImageLookupService, NpcImageLookupService>();
        services.AddSingleton<WindowImageViewService>();
        services.AddSingleton<IImageViewService>(provider =>
            provider.GetRequiredService<WindowImageViewService>());
        services.AddSingleton<WindowNoPresetNotificationService>();
        services.AddSingleton<INoPresetNotificationService>(provider =>
            provider.GetRequiredService<WindowNoPresetNotificationService>());
        services.AddSingleton(provider =>
            new WindowFileDialogService(provider.GetRequiredService<IUserPreferencesService>()));
        services.AddSingleton<IFileDialogService>(provider =>
            provider.GetRequiredService<WindowFileDialogService>());
        services.AddSingleton<WindowAppDialogService>();
        services.AddSingleton<IAppDialogService>(provider =>
            provider.GetRequiredService<WindowAppDialogService>());
        services.AddSingleton<TemplatesViewModel>();
        services.AddSingleton<MorphsViewModel>();
        services.AddSingleton<DiagnosticsViewModel>();
        services.AddSingleton<MainWindowViewModel>();
        services.AddTransient<MainWindow>();
    }

    public static void SetServiceProvider(IServiceProvider? provider) =>
        _serviceProvider = provider ?? throw new ArgumentNullException(nameof(provider));
}
