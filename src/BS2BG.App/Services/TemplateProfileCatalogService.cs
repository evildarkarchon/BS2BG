using System.Reactive.Linq;
using System.Reactive.Subjects;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.App.Services;

/// <summary>
/// Provides the current runtime profile catalog and publishes refreshes to already-created ViewModels.
/// </summary>
public interface ITemplateProfileCatalogService
{
    TemplateProfileCatalog Current { get; }

    IReadOnlyList<ProfileValidationDiagnostic> LastDiscoveryDiagnostics { get; }

    IObservable<TemplateProfileCatalog> CatalogChanged { get; }

    TemplateProfileCatalog Refresh();

    TemplateProfileCatalog ClearProjectProfiles();

    TemplateProfileCatalog WithProjectProfiles(IEnumerable<CustomProfileDefinition> projectProfiles);
}

/// <summary>
/// Thread-safe App-layer holder for the mutable runtime catalog assembled from bundled, local, and project-scoped profile sources.
/// </summary>
public sealed class TemplateProfileCatalogService : ITemplateProfileCatalogService, IDisposable
{
    private readonly TemplateProfileCatalogFactory factory;
    private readonly BehaviorSubject<TemplateProfileCatalog> catalogChanged;
    private readonly SemaphoreSlim semaphore = new(1, 1);
    private TemplateProfileCatalog current;
    private IReadOnlyList<CustomProfileDefinition> projectProfiles = Array.Empty<CustomProfileDefinition>();
    private IReadOnlyList<ProfileValidationDiagnostic> lastDiscoveryDiagnostics = Array.Empty<ProfileValidationDiagnostic>();

    /// <summary>
    /// Creates a service that immediately composes the startup catalog from the supplied factory.
    /// </summary>
    /// <param name="factory">Catalog factory used for bundled plus local custom refreshes.</param>
    public TemplateProfileCatalogService(TemplateProfileCatalogFactory factory)
    {
        this.factory = factory ?? throw new ArgumentNullException(nameof(factory));
        var result = this.factory.Create();
        current = result.Catalog;
        lastDiscoveryDiagnostics = result.DiscoveryDiagnostics;
        catalogChanged = new BehaviorSubject<TemplateProfileCatalog>(current);
    }

    /// <summary>
    /// Creates a fixed-catalog service for tests and design-time ViewModels.
    /// </summary>
    /// <param name="catalog">Initial catalog to publish.</param>
    public TemplateProfileCatalogService(TemplateProfileCatalog catalog)
    {
        current = catalog ?? throw new ArgumentNullException(nameof(catalog));
        factory = new TemplateProfileCatalogFactory(new EmptyUserProfileStore());
        catalogChanged = new BehaviorSubject<TemplateProfileCatalog>(current);
    }

    public TemplateProfileCatalog Current => current;

    public IReadOnlyList<ProfileValidationDiagnostic> LastDiscoveryDiagnostics => lastDiscoveryDiagnostics;

    public IObservable<TemplateProfileCatalog> CatalogChanged => catalogChanged.AsObservable();

    /// <summary>
    /// Rebuilds bundled plus local entries and reapplies any active project-scoped overlay.
    /// </summary>
    /// <returns>The newly published catalog.</returns>
    public TemplateProfileCatalog Refresh()
    {
        semaphore.Wait();
        try
        {
            var result = factory.Create();
            lastDiscoveryDiagnostics = result.DiscoveryDiagnostics;
            Publish(ApplyProjectProfiles(result.Catalog, projectProfiles));
            return current;
        }
        finally
        {
            semaphore.Release();
        }
    }

    /// <summary>
    /// Publishes a project-scoped overlay that may replace same-name local profiles but cannot shadow bundled profiles.
    /// </summary>
    /// <param name="projectProfiles">Validated embedded project profiles to use for the active project only.</param>
    /// <returns>The newly published catalog.</returns>
    public TemplateProfileCatalog WithProjectProfiles(IEnumerable<CustomProfileDefinition> projectProfiles)
    {
        ArgumentNullException.ThrowIfNull(projectProfiles);

        semaphore.Wait();
        try
        {
            this.projectProfiles = projectProfiles.ToArray();
            Publish(ApplyProjectProfiles(factory.Create().Catalog, this.projectProfiles));
            return current;
        }
        finally
        {
            semaphore.Release();
        }
    }

    /// <summary>
    /// Removes project-scoped embedded profiles so new and closed projects cannot retain stale overlay data.
    /// </summary>
    /// <returns>The newly published catalog.</returns>
    public TemplateProfileCatalog ClearProjectProfiles()
    {
        semaphore.Wait();
        try
        {
            projectProfiles = Array.Empty<CustomProfileDefinition>();
            Publish(factory.Create().Catalog);
            return current;
        }
        finally
        {
            semaphore.Release();
        }
    }

    public void Dispose()
    {
        catalogChanged.Dispose();
        semaphore.Dispose();
    }

    private TemplateProfileCatalog ApplyProjectProfiles(
        TemplateProfileCatalog baseCatalog,
        IEnumerable<CustomProfileDefinition> projectProfiles)
    {
        var profiles = projectProfiles.ToArray();
        if (profiles.Length == 0) return baseCatalog;

        var bundledNames = baseCatalog.Entries
            .Where(entry => entry.SourceKind == ProfileSourceKind.Bundled)
            .Select(entry => entry.Name)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        foreach (var profile in profiles)
        {
            if (bundledNames.Contains(profile.Name))
                throw new ArgumentException($"Project profile '{profile.Name}' conflicts with a bundled profile.", nameof(projectProfiles));
        }

        var projectNames = profiles.Select(profile => profile.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var entries = baseCatalog.Entries
            .Where(entry => entry.SourceKind != ProfileSourceKind.LocalCustom || !projectNames.Contains(entry.Name))
            .Concat(profiles.Select(profile => new ProfileCatalogEntry(
                profile.Name,
                new TemplateProfile(profile.Name, profile.SliderProfile),
                ProfileSourceKind.EmbeddedProject,
                profile.FilePath,
                false)));

        return new TemplateProfileCatalog(entries);
    }

    private void Publish(TemplateProfileCatalog catalog)
    {
        current = catalog;
        catalogChanged.OnNext(catalog);
    }

    private sealed class EmptyUserProfileStore : IUserProfileStore
    {
        public UserProfileDiscoveryResult DiscoverProfiles() => DiscoverProfiles(Array.Empty<string>());

        public UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames) =>
            new(Array.Empty<CustomProfileDefinition>(), Array.Empty<ProfileValidationDiagnostic>());

        public UserProfileSaveResult SaveProfile(CustomProfileDefinition profile) =>
            new(false, null, Array.Empty<ProfileValidationDiagnostic>());

        public UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile) =>
            new(false, null, Array.Empty<ProfileValidationDiagnostic>());

        public string GetDefaultProfileDirectory() => string.Empty;
    }
}
