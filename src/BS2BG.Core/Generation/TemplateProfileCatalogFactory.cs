using BS2BG.Core.Models;

namespace BS2BG.Core.Generation;

/// <summary>
/// Creates the bundled generation profile catalog from install-relative slider profile JSON files.
/// </summary>
public sealed class TemplateProfileCatalogFactory
{
    private readonly string baseDirectory;

    /// <summary>
    /// Creates a factory that searches only the process base directory for bundled profile assets.
    /// </summary>
    public TemplateProfileCatalogFactory()
        : this(AppContext.BaseDirectory)
    {
    }

    /// <summary>
    /// Creates a factory with an explicit base directory for test hosts or custom executable layouts.
    /// </summary>
    /// <param name="baseDirectory">Directory containing the required bundled profile JSON files.</param>
    public TemplateProfileCatalogFactory(string baseDirectory)
    {
        this.baseDirectory = baseDirectory ?? throw new ArgumentNullException(nameof(baseDirectory));
    }

    /// <summary>
    /// Loads the three bundled profiles used by generation and fails fast when an install asset is missing.
    /// </summary>
    /// <returns>Bundled profile catalog in fallback/display order.</returns>
    public TemplateProfileCatalog Create() => new(new[]
    {
        CreateEntry(ProjectProfileMapping.SkyrimCbbe, "settings.json"),
        CreateEntry(ProjectProfileMapping.SkyrimUunp, "settings_UUNP.json"),
        CreateEntry(ProjectProfileMapping.Fallout4Cbbe, "settings_FO4_CBBE.json"),
    });

    private ProfileCatalogEntry CreateEntry(string name, string fileName)
    {
        var path = Path.Combine(baseDirectory, fileName);
        if (!File.Exists(path))
            throw new FileNotFoundException(
                "Required bundled slider profile '" + fileName + "' was not found beside the executable.",
                path);

        return new ProfileCatalogEntry(
            name,
            new TemplateProfile(name, SliderProfileJsonService.Load(path)),
            ProfileSourceKind.Bundled,
            null,
            false);
    }
}
