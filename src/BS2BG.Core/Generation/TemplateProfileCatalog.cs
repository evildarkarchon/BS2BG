namespace BS2BG.Core.Generation;

public sealed class TemplateProfileCatalog
{
    private readonly TemplateProfile[] profiles;

    public TemplateProfileCatalog(IEnumerable<TemplateProfile> profiles)
    {
        this.profiles = (profiles ?? throw new ArgumentNullException(nameof(profiles))).ToArray();
        if (this.profiles.Length == 0)
            throw new ArgumentException("At least one template profile is required.", nameof(profiles));
    }

    public IReadOnlyList<TemplateProfile> Profiles => profiles;

    public IReadOnlyList<string> ProfileNames => profiles.Select(profile => profile.Name).ToArray();

    public TemplateProfile DefaultProfile => profiles[0];

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

        return profiles.FirstOrDefault(profile => string.Equals(
            profile.Name,
            name,
            StringComparison.OrdinalIgnoreCase));
    }
}
