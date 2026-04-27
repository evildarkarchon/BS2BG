using BS2BG.Core.Models;

namespace BS2BG.Core.Generation;

/// <summary>
/// Describes a profile row in the catalog together with the trust domain and editability metadata used by profile-management workflows.
/// </summary>
/// <param name="Name">Display name used for case-insensitive catalog lookup.</param>
/// <param name="TemplateProfile">Generation-facing profile wrapper.</param>
/// <param name="SourceKind">Trust domain that provided this catalog entry.</param>
/// <param name="FilePath">Optional source path for editable local profiles.</param>
/// <param name="IsEditable">Whether the row can be edited by profile-management actions.</param>
public sealed record ProfileCatalogEntry(
    string Name,
    TemplateProfile TemplateProfile,
    ProfileSourceKind SourceKind,
    string? FilePath,
    bool IsEditable);

/// <summary>
/// Immutable catalog used to resolve named template profiles while preserving neutral fallback semantics for unresolved project names.
/// </summary>
public sealed class TemplateProfileCatalog
{
    private readonly ProfileCatalogEntry[] entries;

    /// <summary>
    /// Creates a bundled-only catalog from the legacy generation-facing profile list.
    /// </summary>
    /// <param name="profiles">Profiles in UI display and fallback order.</param>
    public TemplateProfileCatalog(IEnumerable<TemplateProfile> profiles)
        : this((profiles ?? throw new ArgumentNullException(nameof(profiles))).Select(profile => new ProfileCatalogEntry(
            profile.Name,
            profile,
            ProfileSourceKind.Bundled,
            null,
            false)))
    {
    }

    /// <summary>
    /// Creates a catalog from source-tagged entries and rejects ambiguous case-insensitive display-name duplicates.
    /// </summary>
    /// <param name="entries">Catalog entries in UI display and fallback order.</param>
    public TemplateProfileCatalog(IEnumerable<ProfileCatalogEntry> entries)
    {
        this.entries = (entries ?? throw new ArgumentNullException(nameof(entries))).ToArray();
        if (this.entries.Length == 0)
            throw new ArgumentException("At least one template profile is required.", nameof(entries));

        ValidateEntries(this.entries);
    }

    public IReadOnlyList<ProfileCatalogEntry> Entries => entries;

    public IReadOnlyList<TemplateProfile> Profiles => entries.Select(entry => entry.TemplateProfile).ToArray();

    public IReadOnlyList<string> ProfileNames => entries.Select(entry => entry.Name).ToArray();

    public TemplateProfile DefaultProfile => entries[0].TemplateProfile;

    /// <summary>
    /// Returns whether the catalog contains a bundled profile whose display name matches the supplied name.
    /// Unknown, null, and whitespace-only names remain distinguishable so callers can surface neutral fallback information.
    /// </summary>
    public bool ContainsProfile(string? name) => FindProfile(name) is not null;

    public TemplateProfile GetProfile(string? name)
    {
        var match = FindProfile(name);
        if (match is not null) return match;

        return DefaultProfile;
    }

    private TemplateProfile? FindProfile(string? name)
    {
        if (string.IsNullOrWhiteSpace(name)) return null;

        return entries.FirstOrDefault(entry => string.Equals(
            entry.Name,
            name,
            StringComparison.OrdinalIgnoreCase))?.TemplateProfile;
    }

    private static void ValidateEntries(IEnumerable<ProfileCatalogEntry> entries)
    {
        var seen = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var entry in entries)
        {
            if (entry is null) throw new ArgumentException("Catalog entries must not contain null values.", nameof(entries));
            if (entry.TemplateProfile is null) throw new ArgumentException("Catalog entries must include a template profile.", nameof(entries));
            if (string.IsNullOrWhiteSpace(entry.Name)) throw new ArgumentException("Catalog entry names must not be blank.", nameof(entries));

            if (!string.Equals(entry.Name, entry.TemplateProfile.Name, StringComparison.Ordinal))
            {
                throw new ArgumentException($"Catalog entry name '{entry.Name}' must match template profile name '{entry.TemplateProfile.Name}'.", nameof(entries));
            }

            if (seen.TryGetValue(entry.Name, out var existingName))
            {
                throw new ArgumentException($"Catalog contains duplicate profile names '{existingName}' and '{entry.Name}'.", nameof(entries));
            }

            seen.Add(entry.Name, entry.Name);
        }
    }
}
