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
}
