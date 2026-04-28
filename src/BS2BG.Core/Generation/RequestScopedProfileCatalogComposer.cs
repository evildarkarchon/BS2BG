using System.Diagnostics.CodeAnalysis;
using BS2BG.Core.Models;
using BS2BG.Core.Serialization;

namespace BS2BG.Core.Generation;

/// <summary>
/// Composes per-request profile catalogs from bundled profiles plus the custom profiles referenced by a project.
/// </summary>
/// <remarks>
/// Composition is read-only: project and save-context profile definitions are projected into catalog entries without mutating or cloning source data.
/// </remarks>
public sealed class RequestScopedProfileCatalogComposer
{
    private readonly TemplateProfileCatalog baseCatalog;

    /// <summary>
    /// Initializes a composer over the stable base catalog supplied by the caller's runtime boundary.
    /// </summary>
    /// <param name="baseCatalog">Catalog whose bundled entries remain first and cannot be shadowed by custom profile definitions.</param>
    public RequestScopedProfileCatalogComposer(TemplateProfileCatalog baseCatalog)
    {
        this.baseCatalog = baseCatalog ?? throw new ArgumentNullException(nameof(baseCatalog));
    }

    /// <summary>
    /// Builds a generation catalog for one project request using bundled entries plus referenced non-bundled custom profiles.
    /// </summary>
    /// <param name="project">Project whose slider preset profile references define the request scope.</param>
    /// <param name="saveContext">Optional custom profile resolver used by bundle/save workflows; project-embedded definitions win over same-name context entries.</param>
    /// <returns>A catalog with bundled profiles first, followed by resolved custom profiles in first-referenced order.</returns>
    public TemplateProfileCatalog BuildForProject(ProjectModel project, ProjectSaveContext? saveContext = null)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));

        var entries = baseCatalog.Entries
            .Where(entry => entry.SourceKind == ProfileSourceKind.Bundled)
            .Concat(ResolveReferencedCustomProfiles(project, saveContext).Select(profile => new ProfileCatalogEntry(
                profile.Name,
                new TemplateProfile(profile.Name, profile.SliderProfile),
                profile.SourceKind,
                profile.FilePath,
                false)));

        return new TemplateProfileCatalog(entries);
    }

    /// <summary>
    /// Resolves the custom profile definitions referenced by project presets without including bundled, blank, duplicate, or unreferenced names.
    /// </summary>
    /// <param name="project">Project whose slider presets supply profile names in deterministic first-seen order.</param>
    /// <param name="saveContext">Optional save-context resolver used when a referenced profile is absent from the project snapshot.</param>
    /// <returns>Resolved custom profile definitions, preferring project-owned definitions over same-name save-context definitions.</returns>
    [SuppressMessage("Performance", "CA1822:Mark members as static",
        Justification = "This is part of the instance composer contract alongside BuildForProject.")]
    public IReadOnlyList<CustomProfileDefinition> ResolveReferencedCustomProfiles(ProjectModel project, ProjectSaveContext? saveContext = null)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));

        var projectProfiles = BuildProjectProfileLookup(project.CustomProfiles);
        var resolved = new List<CustomProfileDefinition>();
        foreach (var name in ReferencedCustomProfileNames(project))
        {
            if (projectProfiles.TryGetValue(name, out var projectProfile))
            {
                resolved.Add(projectProfile);
                continue;
            }

            if (saveContext?.AvailableCustomProfilesByName.TryGetValue(name, out var contextProfile) == true
                && IsEligibleCustomProfile(contextProfile))
            {
                resolved.Add(contextProfile);
            }
        }

        return resolved;
    }

    private static Dictionary<string, CustomProfileDefinition> BuildProjectProfileLookup(IEnumerable<CustomProfileDefinition> profiles)
    {
        var lookup = new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase);
        foreach (var profile in profiles)
        {
            if (!IsEligibleCustomProfile(profile)) continue;

            // First project-owned definition wins so duplicate project data cannot make catalog construction ambiguous.
            lookup.TryAdd(profile.Name, profile);
        }

        return lookup;
    }

    private static IEnumerable<string> ReferencedCustomProfileNames(ProjectModel project)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var preset in project.SliderPresets)
        {
            var name = NormalizeCustomProfileName(preset.ProfileName);
            if (name is null || !seen.Add(name)) continue;

            yield return name;
        }
    }

    private static string? NormalizeCustomProfileName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name)) return null;

        var trimmed = name.Trim();
        return IsBundledProfileName(trimmed) ? null : trimmed;
    }

    private static bool IsEligibleCustomProfile(CustomProfileDefinition profile) =>
        profile.SourceKind != ProfileSourceKind.Bundled
        && !string.IsNullOrWhiteSpace(profile.Name)
        && !IsBundledProfileName(profile.Name);

    private static bool IsBundledProfileName(string? name) =>
        string.Equals(name, ProjectProfileMapping.SkyrimCbbe, StringComparison.OrdinalIgnoreCase)
        || string.Equals(name, ProjectProfileMapping.SkyrimUunp, StringComparison.OrdinalIgnoreCase)
        || string.Equals(name, ProjectProfileMapping.Fallout4Cbbe, StringComparison.OrdinalIgnoreCase);
}
