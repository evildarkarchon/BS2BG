using BS2BG.Core.Models;
using BS2BG.Core.Profiles;

namespace BS2BG.Core.Generation;

/// <summary>
/// Composes per-request profile catalogs from bundled profiles plus a stable referenced custom-profile resolution.
/// </summary>
public sealed class RequestScopedProfileCatalogComposer
{
    private readonly TemplateProfileCatalog baseCatalog;

    /// <summary>
    /// Initializes a composer over the stable base catalog supplied by the caller's runtime seam.
    /// </summary>
    /// <param name="baseCatalog">Catalog whose bundled entries remain first and cannot be shadowed by custom profiles.</param>
    public RequestScopedProfileCatalogComposer(TemplateProfileCatalog baseCatalog)
    {
        this.baseCatalog = baseCatalog ?? throw new ArgumentNullException(nameof(baseCatalog));
    }

    /// <summary>
    /// Builds a generation catalog from one immutable referenced custom-profile resolution.
    /// </summary>
    /// <param name="profileResolution">Resolution whose custom profiles follow bundled entries in first-reference order.</param>
    /// <returns>A request catalog containing bundled profiles followed by resolved custom profile snapshots.</returns>
    public TemplateProfileCatalog BuildForProject(ReferencedCustomProfileResolution profileResolution)
    {
        if (profileResolution is null) throw new ArgumentNullException(nameof(profileResolution));

        var entries = baseCatalog.Entries
            .Where(entry => entry.SourceKind == ProfileSourceKind.Bundled)
            .Concat(profileResolution.ResolvedProfiles.Select(profile => new ProfileCatalogEntry(
                profile.Name,
                new TemplateProfile(profile.Name, profile.SliderProfile),
                profile.SourceKind,
                profile.FilePath,
                false)));

        return new TemplateProfileCatalog(entries);
    }
}
