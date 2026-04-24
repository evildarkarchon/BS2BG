namespace BS2BG.Core.Generation;

public sealed class TemplateProfileCatalog
{
    private readonly TemplateProfile[] profiles;

    public TemplateProfileCatalog(IEnumerable<TemplateProfile> profiles)
    {
        this.profiles = (profiles ?? throw new ArgumentNullException(nameof(profiles))).ToArray();
        if (this.profiles.Length == 0)
        {
            throw new ArgumentException("At least one template profile is required.", nameof(profiles));
        }
    }

    public IReadOnlyList<TemplateProfile> Profiles => profiles;

    public IReadOnlyList<string> ProfileNames => profiles.Select(profile => profile.Name).ToArray();

    public TemplateProfile DefaultProfile => profiles[0];

    public TemplateProfile GetProfile(string? name)
    {
        if (!string.IsNullOrWhiteSpace(name))
        {
            var match = profiles.FirstOrDefault(profile => string.Equals(
                profile.Name,
                name,
                StringComparison.OrdinalIgnoreCase));
            if (match is not null)
            {
                return match;
            }
        }

        return DefaultProfile;
    }
}
