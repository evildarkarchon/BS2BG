using BS2BG.Core.Models;

namespace BS2BG.Core.Profiles;

/// <summary>
/// Resolves the custom profile definitions referenced by a project through case-insensitive profile identity.
/// </summary>
public static class ReferencedCustomProfileResolver
{
    /// <summary>
    /// Resolves referenced project copies and optional fallback profiles in first-reference order without exposing source instances.
    /// </summary>
    /// <param name="project">Project whose presets and project copies define the resolution scope.</param>
    /// <param name="availableCustomProfilesByName">Optional fallback definitions; candidate internal names remain authoritative over lookup keys.</param>
    /// <returns>Detached resolved profile snapshots plus unresolved referenced custom-profile names.</returns>
    public static ReferencedCustomProfileResolution Resolve(
        ProjectModel project,
        IReadOnlyDictionary<string, CustomProfileDefinition>? availableCustomProfilesByName = null)
    {
        if (project is null) throw new ArgumentNullException(nameof(project));

        var projectCopies = BuildEligibleLookup(project.CustomProfiles);
        var fallbackProfiles = BuildEligibleLookup(
            availableCustomProfilesByName?.Values ?? Array.Empty<CustomProfileDefinition>());
        var resolved = new List<ReferencedCustomProfileSnapshot>();
        var unresolved = new List<string>();
        foreach (var name in ReferencedCustomProfileNames(project))
        {
            if (projectCopies.TryGetValue(name, out var profile))
            {
                resolved.Add(ReferencedCustomProfileSnapshot.From(profile));
                continue;
            }

            if (fallbackProfiles.TryGetValue(name, out var fallbackProfile))
            {
                resolved.Add(ReferencedCustomProfileSnapshot.From(fallbackProfile));
                continue;
            }

            unresolved.Add(name);
        }

        return new ReferencedCustomProfileResolution(resolved, unresolved);
    }

    /// <summary>
    /// Indexes eligible custom profile definitions by internal case-insensitive identity using first-wins source order.
    /// </summary>
    /// <param name="profiles">Project copies or fallback definitions to index.</param>
    /// <returns>A lookup that excludes blank, bundled-source, and bundled-name definitions.</returns>
    private static Dictionary<string, CustomProfileDefinition> BuildEligibleLookup(
        IEnumerable<CustomProfileDefinition> profiles)
    {
        var lookup = new Dictionary<string, CustomProfileDefinition>(StringComparer.OrdinalIgnoreCase);
        foreach (var profile in profiles)
        {
            if (!IsEligible(profile)) continue;

            // Source order is authoritative when malformed input contains duplicate internal identities.
            lookup.TryAdd(profile.Name, profile);
        }

        return lookup;
    }

    /// <summary>
    /// Enumerates distinct non-bundled preset profile references in deterministic first-reference order.
    /// </summary>
    /// <param name="project">Project whose preset references define the resolution scope.</param>
    /// <returns>Trimmed custom-profile names preserving the first observed casing and order.</returns>
    private static IEnumerable<string> ReferencedCustomProfileNames(ProjectModel project)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var preset in project.SliderPresets)
        {
            var name = preset.ProfileName.Trim();
            if (ProjectProfileMapping.IsBundledProfileName(name) || !seen.Add(name)) continue;

            yield return name;
        }
    }

    private static bool IsEligible(CustomProfileDefinition profile) =>
        profile.SourceKind != ProfileSourceKind.Bundled
        && !string.IsNullOrWhiteSpace(profile.Name)
        && !ProjectProfileMapping.IsBundledProfileName(profile.Name);
}

/// <summary>
/// Captures a stable referenced custom-profile resolution outcome for one project snapshot.
/// </summary>
public sealed class ReferencedCustomProfileResolution
{
    /// <summary>
    /// Initializes a read-only resolution outcome from already detached profile snapshots and unresolved names.
    /// </summary>
    /// <param name="resolvedProfiles">Resolved profile snapshots in first-reference order.</param>
    /// <param name="unresolvedProfileNames">Unresolved custom-profile names in first-reference order.</param>
    internal ReferencedCustomProfileResolution(
        IEnumerable<ReferencedCustomProfileSnapshot> resolvedProfiles,
        IEnumerable<string> unresolvedProfileNames)
    {
        ResolvedProfiles = Array.AsReadOnly(resolvedProfiles.ToArray());
        UnresolvedProfileNames = Array.AsReadOnly(unresolvedProfileNames.ToArray());
    }

    /// <summary>
    /// Gets detached resolved profile snapshots in first-reference order.
    /// </summary>
    public IReadOnlyList<ReferencedCustomProfileSnapshot> ResolvedProfiles { get; }

    /// <summary>
    /// Gets unresolved referenced custom-profile names in first-reference order.
    /// </summary>
    public IReadOnlyList<string> UnresolvedProfileNames { get; }
}

/// <summary>
/// Immutable snapshot of a resolved custom profile definition and its source metadata.
/// </summary>
/// <param name="Name">Internal case-insensitive profile identity.</param>
/// <param name="Game">Game metadata captured when resolution ran.</param>
/// <param name="SliderProfile">Immutable generation-facing slider tables.</param>
/// <param name="SourceKind">Trust domain captured when resolution ran.</param>
/// <param name="FilePath">Optional source path captured when resolution ran.</param>
public sealed record ReferencedCustomProfileSnapshot(
    string Name,
    string Game,
    BS2BG.Core.Formatting.SliderProfile SliderProfile,
    ProfileSourceKind SourceKind,
    string? FilePath)
{
    /// <summary>
    /// Captures a detached immutable metadata snapshot from a mutable profile definition.
    /// </summary>
    /// <param name="profile">Validated custom profile definition to snapshot.</param>
    /// <returns>Immutable resolution-facing profile data.</returns>
    internal static ReferencedCustomProfileSnapshot From(CustomProfileDefinition profile) => new(
        profile.Name,
        profile.Game,
        profile.SliderProfile,
        profile.SourceKind,
        profile.FilePath);

    /// <summary>
    /// Rehydrates a detached mutable definition for existing adapters that serialize profile definitions.
    /// </summary>
    /// <returns>A new profile definition containing this snapshot's data.</returns>
    internal CustomProfileDefinition ToDefinition() => new(Name, Game, SliderProfile, SourceKind, FilePath);
}
