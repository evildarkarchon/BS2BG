using System.Security.Cryptography;
using System.Text;
using BS2BG.Core.Generation;
using BS2BG.Core.IO;
using BS2BG.Core.Models;

namespace BS2BG.App.Services;

/// <summary>
/// Provides local custom-profile discovery and persistence without depending on Avalonia UI services.
/// </summary>
public interface IUserProfileStore
{
    UserProfileDiscoveryResult DiscoverProfiles();

    UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames);

    UserProfileSaveResult SaveProfile(CustomProfileDefinition profile);

    UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile);

    string GetDefaultProfileDirectory();
}

/// <summary>
/// Result from scanning the user-local profile folder for validated custom profile JSON files.
/// </summary>
/// <param name="Profiles">Validated local profiles accepted for catalog inclusion.</param>
/// <param name="Diagnostics">Diagnostics for rejected files or non-fatal filesystem issues.</param>
public sealed record UserProfileDiscoveryResult(
    IReadOnlyList<CustomProfileDefinition> Profiles,
    IReadOnlyList<ProfileValidationDiagnostic> Diagnostics);

/// <summary>
/// Result from saving a profile to the user-local profile folder.
/// </summary>
/// <param name="Succeeded">Whether the file was written successfully.</param>
/// <param name="FilePath">Target path used for the save when available.</param>
/// <param name="Diagnostics">Diagnostics for validation or filesystem failures.</param>
public sealed record UserProfileSaveResult(
    bool Succeeded,
    string? FilePath,
    IReadOnlyList<ProfileValidationDiagnostic> Diagnostics);

/// <summary>
/// Result from deleting a local profile file.
/// </summary>
/// <param name="Succeeded">Whether the delete completed or the file was already absent.</param>
/// <param name="FilePath">Path considered for deletion.</param>
/// <param name="Diagnostics">Diagnostics for validation or filesystem failures.</param>
public sealed record UserProfileDeleteResult(
    bool Succeeded,
    string? FilePath,
    IReadOnlyList<ProfileValidationDiagnostic> Diagnostics);

/// <summary>
/// Stores editable custom profile JSON under AppData and validates every profile before returning it to catalog composition.
/// </summary>
public sealed class UserProfileStore : IUserProfileStore
{
    private static readonly UTF8Encoding Utf8NoBom = new(false);
    private readonly string profileDirectory;
    private readonly ProfileDefinitionService profileDefinitionService;

    /// <summary>
    /// Creates a store rooted at the default AppData profile folder.
    /// </summary>
    /// <param name="profileDefinitionService">Validator/exporter used for all local profile JSON.</param>
    public UserProfileStore(ProfileDefinitionService profileDefinitionService)
        : this(GetDefaultProfileDirectoryCore(), profileDefinitionService)
    {
    }

    /// <summary>
    /// Creates a store rooted at an explicit directory, primarily for tests and portable host wiring.
    /// </summary>
    /// <param name="profileDirectory">Directory containing user-editable profile JSON files.</param>
    /// <param name="profileDefinitionService">Validator/exporter used for all local profile JSON.</param>
    public UserProfileStore(string profileDirectory, ProfileDefinitionService profileDefinitionService)
    {
        this.profileDirectory = profileDirectory ?? throw new ArgumentNullException(nameof(profileDirectory));
        this.profileDefinitionService = profileDefinitionService ?? throw new ArgumentNullException(nameof(profileDefinitionService));
    }

    /// <summary>
    /// Discovers local profiles without pre-existing catalog-name conflicts.
    /// </summary>
    /// <returns>Valid local profiles plus diagnostics for rejected user files.</returns>
    public UserProfileDiscoveryResult DiscoverProfiles() => DiscoverProfiles(Array.Empty<string>());

    /// <summary>
    /// Discovers local profiles and rejects names that collide with bundled or already accepted profile names.
    /// </summary>
    /// <param name="existingProfileNames">Names already owned by bundled or higher-priority catalog entries.</param>
    /// <returns>Valid local profiles plus diagnostics for rejected user files.</returns>
    public UserProfileDiscoveryResult DiscoverProfiles(IEnumerable<string> existingProfileNames)
    {
        ArgumentNullException.ThrowIfNull(existingProfileNames);

        var diagnostics = new List<ProfileValidationDiagnostic>();
        var profiles = new List<CustomProfileDefinition>();
        var acceptedNames = new HashSet<string>(existingProfileNames, StringComparer.OrdinalIgnoreCase);

        string[] files;
        try
        {
            Directory.CreateDirectory(profileDirectory);
            files = Directory.GetFiles(profileDirectory, "*.json")
                .OrderBy(Path.GetFullPath, StringComparer.OrdinalIgnoreCase)
                .ToArray();
        }
        catch (Exception exception) when (IsNormalFileFailure(exception))
        {
            diagnostics.Add(Blocker("ProfileDirectoryUnavailable", $"Could not read custom profile directory: {exception.Message}", null, profileDirectory));
            return new UserProfileDiscoveryResult(profiles, diagnostics);
        }

        foreach (var file in files)
        {
            string json;
            try
            {
                json = File.ReadAllText(file, Utf8NoBom);
            }
            catch (Exception exception) when (IsNormalFileFailure(exception))
            {
                diagnostics.Add(Blocker("ProfileFileUnavailable", $"Could not read custom profile '{file}': {exception.Message}", null, file));
                continue;
            }

            var result = profileDefinitionService.ValidateProfileJson(
                json,
                ProfileValidationContext.ForImport(acceptedNames, ProfileSourceKind.LocalCustom, file));
            diagnostics.AddRange(result.Diagnostics);
            if (!result.IsValid || result.Profile is null) continue;

            profiles.Add(result.Profile);
            acceptedNames.Add(result.Profile.Name);
        }

        return new UserProfileDiscoveryResult(profiles, diagnostics);
    }

    /// <summary>
    /// Saves a profile to a sanitized JSON filename without overwriting an unrelated existing profile identity.
    /// </summary>
    /// <param name="profile">Profile definition to persist as local custom JSON.</param>
    /// <returns>Save status plus the target path or diagnostics for recoverable filesystem failures.</returns>
    public UserProfileSaveResult SaveProfile(CustomProfileDefinition profile)
    {
        ArgumentNullException.ThrowIfNull(profile);

        try
        {
            Directory.CreateDirectory(profileDirectory);
            var targetPath = ChooseSavePath(profile);
            var localProfile = new CustomProfileDefinition(
                profile.Name,
                profile.Game,
                profile.SliderProfile,
                ProfileSourceKind.LocalCustom,
                targetPath);
            AtomicFileWriter.WriteAtomic(targetPath, profileDefinitionService.ExportProfileJson(localProfile), Utf8NoBom);
            return new UserProfileSaveResult(true, targetPath, Array.Empty<ProfileValidationDiagnostic>());
        }
        catch (Exception exception) when (IsNormalFileFailure(exception) || exception is AtomicWriteException)
        {
            return new UserProfileSaveResult(false, null, new[]
            {
                Blocker("ProfileSaveFailed", $"Could not save custom profile: {exception.Message}", null, profile.Name),
            });
        }
    }

    /// <summary>
    /// Deletes a local custom profile file when the profile carries source file metadata.
    /// </summary>
    /// <param name="profile">Local custom profile to delete.</param>
    /// <returns>Delete status and diagnostics for recoverable filesystem failures.</returns>
    public UserProfileDeleteResult DeleteProfile(CustomProfileDefinition profile)
    {
        ArgumentNullException.ThrowIfNull(profile);

        if (string.IsNullOrWhiteSpace(profile.FilePath))
        {
            return new UserProfileDeleteResult(false, null, new[]
            {
                Blocker("MissingProfilePath", "Only local profiles with a source file path can be deleted.", null, profile.Name),
            });
        }

        try
        {
            if (File.Exists(profile.FilePath)) File.Delete(profile.FilePath);
            return new UserProfileDeleteResult(true, profile.FilePath, Array.Empty<ProfileValidationDiagnostic>());
        }
        catch (Exception exception) when (IsNormalFileFailure(exception))
        {
            return new UserProfileDeleteResult(false, profile.FilePath, new[]
            {
                Blocker("ProfileDeleteFailed", $"Could not delete custom profile: {exception.Message}", null, profile.Name),
            });
        }
    }

    /// <summary>
    /// Returns the default user-local custom profile folder under AppData.
    /// </summary>
    /// <returns>The `%APPDATA%/jBS2BG/profiles` equivalent for the current user.</returns>
    public string GetDefaultProfileDirectory() => GetDefaultProfileDirectoryCore();

    private string ChooseSavePath(CustomProfileDefinition profile)
    {
        var baseName = SanitizeFileNameBase(profile.Name);
        var basePath = Path.Combine(profileDirectory, baseName + ".json");
        if (CanWriteProfilePath(basePath, profile.Name)) return basePath;

        var hash = CreateHash8(profile.Name, basePath);
        var candidate = Path.Combine(profileDirectory, baseName + "-" + hash + ".json");
        if (CanWriteProfilePath(candidate, profile.Name)) return candidate;

        var sequence = 2;
        while (true)
        {
            var sequenced = Path.Combine(profileDirectory, baseName + "-" + hash + "-" + sequence.ToString(System.Globalization.CultureInfo.InvariantCulture) + ".json");
            if (CanWriteProfilePath(sequenced, profile.Name)) return sequenced;
            sequence++;
        }
    }

    private bool CanWriteProfilePath(string path, string profileName)
    {
        if (!File.Exists(path)) return true;

        try
        {
            var result = profileDefinitionService.ValidateProfileJson(
                File.ReadAllText(path, Utf8NoBom),
                ProfileValidationContext.ForImport(Array.Empty<string>(), ProfileSourceKind.LocalCustom, path));

            return result.Profile is not null && string.Equals(result.Profile.Name, profileName, StringComparison.OrdinalIgnoreCase);
        }
        catch (Exception exception) when (IsNormalFileFailure(exception))
        {
            return false;
        }
    }

    private static string SanitizeFileNameBase(string name)
    {
        var invalid = Path.GetInvalidFileNameChars().ToHashSet();
        var builder = new StringBuilder(name.Length);
        foreach (var character in name.Trim().ToLowerInvariant())
        {
            builder.Append(invalid.Contains(character) || char.IsWhiteSpace(character) ? '-' : character);
        }

        var sanitized = builder.ToString().Trim('-');
        if (string.IsNullOrWhiteSpace(sanitized)) sanitized = "profile";
        if (sanitized.Length > 64) sanitized = sanitized[..64].TrimEnd('-');
        return string.IsNullOrWhiteSpace(sanitized) ? "profile" : sanitized;
    }

    private static string CreateHash8(string profileName, string candidatePath)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(profileName + candidatePath));
        return Convert.ToHexString(bytes).ToLowerInvariant()[..8];
    }

    private static string GetDefaultProfileDirectoryCore() => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "jBS2BG",
        "profiles");

    private static bool IsNormalFileFailure(Exception exception) => exception is IOException or UnauthorizedAccessException or DirectoryNotFoundException;

    private static ProfileValidationDiagnostic Blocker(string code, string message, string? table, string? sliderName) =>
        new(ProfileValidationSeverity.Blocker, code, message, table, sliderName);
}
