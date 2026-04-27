using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.App.Services;

/// <summary>
/// Result from composing bundled and local custom profiles into a generation catalog.
/// </summary>
/// <param name="Catalog">Catalog containing bundled entries followed by accepted custom entries.</param>
/// <param name="DiscoveryDiagnostics">Diagnostics for custom profile files skipped during local discovery.</param>
public sealed record TemplateProfileCatalogFactoryResult(
    TemplateProfileCatalog Catalog,
    IReadOnlyList<ProfileValidationDiagnostic> DiscoveryDiagnostics);

/// <summary>
/// Composes the required bundled profile JSON files with validated user-local custom profiles.
/// </summary>
public sealed class TemplateProfileCatalogFactory
{
    private readonly DirectoryInfo[] bundledSearchDirectories;
    private readonly IUserProfileStore userProfileStore;

    /// <summary>
    /// Creates a factory that loads bundled profiles from the app base directory and custom profiles from the user store.
    /// </summary>
    /// <param name="userProfileStore">User-local custom profile store.</param>
    public TemplateProfileCatalogFactory(IUserProfileStore userProfileStore)
        : this(userProfileStore, CandidateDirectories())
    {
    }

    /// <summary>
    /// Creates a factory with explicit bundled search directories, primarily for tests and design-time hosts.
    /// </summary>
    /// <param name="userProfileStore">User-local custom profile store.</param>
    /// <param name="bundledSearchDirectories">Directories searched for required bundled profile JSON files.</param>
    public TemplateProfileCatalogFactory(IUserProfileStore userProfileStore, IEnumerable<string> bundledSearchDirectories)
        : this(userProfileStore, bundledSearchDirectories.Select(directory => new DirectoryInfo(directory)))
    {
    }

    private TemplateProfileCatalogFactory(IUserProfileStore userProfileStore, IEnumerable<DirectoryInfo> bundledSearchDirectories)
    {
        this.userProfileStore = userProfileStore ?? throw new ArgumentNullException(nameof(userProfileStore));
        this.bundledSearchDirectories = (bundledSearchDirectories ?? throw new ArgumentNullException(nameof(bundledSearchDirectories))).ToArray();
    }

    /// <summary>
    /// Legacy static shim that returns only the composed catalog for callers that do not need discovery diagnostics.
    /// </summary>
    /// <returns>Default bundled catalog plus any valid local custom profiles.</returns>
    public static TemplateProfileCatalog CreateDefault() => CreateDefault(CandidateDirectories());

    /// <summary>
    /// Legacy static shim using explicit bundled search directories and no local custom profile source.
    /// </summary>
    /// <param name="searchDirectories">Directories searched for bundled profile JSON.</param>
    /// <returns>Bundled-only catalog used by existing tests and design-time callers.</returns>
    public static TemplateProfileCatalog CreateDefault(IEnumerable<string> searchDirectories)
    {
        ArgumentNullException.ThrowIfNull(searchDirectories);

        return new TemplateProfileCatalogFactory(new EmptyUserProfileStore(), searchDirectories).Create().Catalog;
    }

    /// <summary>
    /// Builds the current catalog and returns custom profile discovery diagnostics without failing startup for malformed user JSON.
    /// </summary>
    /// <returns>Composed catalog result.</returns>
    public TemplateProfileCatalogFactoryResult Create()
    {
        var bundledEntries = CreateBundledEntries().ToArray();
        var discovery = userProfileStore.DiscoverProfiles(bundledEntries.Select(entry => entry.Name));
        var customEntries = discovery.Profiles.Select(profile => new ProfileCatalogEntry(
            profile.Name,
            new TemplateProfile(profile.Name, profile.SliderProfile),
            ProfileSourceKind.LocalCustom,
            profile.FilePath,
            true));

        return new TemplateProfileCatalogFactoryResult(
            new TemplateProfileCatalog(bundledEntries.Concat(customEntries)),
            discovery.Diagnostics);
    }

    private IEnumerable<ProfileCatalogEntry> CreateBundledEntries()
    {
        yield return CreateBundledEntry(
            ProjectProfileMapping.SkyrimCbbe,
            "settings.json");
        yield return CreateBundledEntry(
            ProjectProfileMapping.SkyrimUunp,
            "settings_UUNP.json");
        yield return CreateBundledEntry(
            ProjectProfileMapping.Fallout4Cbbe,
            "settings_FO4_CBBE.json");
    }

    private ProfileCatalogEntry CreateBundledEntry(string name, string fileName)
    {
        return new ProfileCatalogEntry(
            name,
            new TemplateProfile(name, LoadRequiredProfile(fileName, bundledSearchDirectories)),
            ProfileSourceKind.Bundled,
            null,
            false);
    }

    private static BS2BG.Core.Formatting.SliderProfile LoadRequiredProfile(string fileName, IEnumerable<DirectoryInfo> searchDirectories)
    {
        var directories = searchDirectories.ToArray();
        var path = FindProfilePath(fileName, directories);
        if (path is null) throw new FileNotFoundException(CreateMissingProfileMessage(fileName, directories), fileName);

        return SliderProfileJsonService.Load(path);
    }

    private static string? FindProfilePath(string fileName, IEnumerable<DirectoryInfo> searchDirectories)
    {
        foreach (var directory in searchDirectories)
        {
            var path = Path.Combine(directory.FullName, fileName);
            if (File.Exists(path)) return path;
        }

        return null;
    }

    private static string CreateMissingProfileMessage(string fileName, IEnumerable<DirectoryInfo> searchDirectories)
    {
        var searchedDirectories = string.Join(
            Environment.NewLine,
            searchDirectories
                .Select(directory => directory.FullName)
                .Distinct(StringComparer.OrdinalIgnoreCase));

        return "Required slider profile '" + fileName + "' was not found."
               + Environment.NewLine
               + "Searched directories:"
               + Environment.NewLine
               + searchedDirectories;
    }

    private static IEnumerable<string> CandidateDirectories()
    {
        yield return AppContext.BaseDirectory;
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
