namespace BS2BG.Core.Models;

public static class ProjectProfileMapping
{
    public const string SkyrimCbbe = "Skyrim CBBE";
    public const string SkyrimUunp = "Skyrim UUNP";
    public const string Fallout4Cbbe = "Fallout 4 CBBE";

    public static string Resolve(string? profileName, bool isUunp)
    {
        if (!string.IsNullOrWhiteSpace(profileName)) return profileName.Trim();

        return isUunp ? SkyrimUunp : SkyrimCbbe;
    }

    public static string FromLegacyIsUunp(bool isUunp) => isUunp ? SkyrimUunp : SkyrimCbbe;

    public static bool ToLegacyIsUunp(string? profileName) =>
        string.Equals(profileName, SkyrimUunp, StringComparison.OrdinalIgnoreCase);

    /// <summary>
    /// Returns whether a profile name identifies one of BS2BG's bundled generation profiles.
    /// </summary>
    /// <param name="profileName">Profile identity to classify using case-insensitive matching.</param>
    /// <returns><see langword="true"/> for Skyrim CBBE, Skyrim UUNP, or Fallout 4 CBBE.</returns>
    public static bool IsBundledProfileName(string? profileName) =>
        string.Equals(profileName, SkyrimCbbe, StringComparison.OrdinalIgnoreCase)
        || string.Equals(profileName, SkyrimUunp, StringComparison.OrdinalIgnoreCase)
        || string.Equals(profileName, Fallout4Cbbe, StringComparison.OrdinalIgnoreCase);
}
