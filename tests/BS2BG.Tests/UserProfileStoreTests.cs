using System.Security.Cryptography;
using System.Text;
using BS2BG.App.Services;
using BS2BG.Core.Formatting;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;
using Xunit;

namespace BS2BG.Tests;

public sealed class UserProfileStoreTests : IDisposable
{
    private readonly string directory = Path.Combine(Path.GetTempPath(), "bs2bg-user-profiles-" + Guid.NewGuid().ToString("N"));

    /// <summary>
    /// Removes the isolated temp profile directory created for each test.
    /// </summary>
    public void Dispose()
    {
        if (Directory.Exists(directory)) Directory.Delete(directory, recursive: true);
    }

    /// <summary>
    /// Verifies the default profile directory lives under AppData using the jBS2BG profiles folder convention.
    /// </summary>
    [Fact]
    public void GetDefaultProfileDirectoryUsesAppDataProfilesFolder()
    {
        var store = new UserProfileStore(new ProfileDefinitionService());

        store.GetDefaultProfileDirectory().Should().Be(Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "jBS2BG",
            "profiles"));
    }

    /// <summary>
    /// Verifies discovery validates each JSON file, accepts valid profiles, and reports malformed files without throwing.
    /// </summary>
    [Fact]
    public void DiscoverProfilesReturnsValidProfilesAndInvalidFileDiagnostics()
    {
        Directory.CreateDirectory(directory);
        File.WriteAllText(Path.Combine(directory, "valid.json"), CreateProfileJson("Local Body"));
        File.WriteAllText(Path.Combine(directory, "broken.json"), "{ not json");
        var store = new UserProfileStore(directory, new ProfileDefinitionService());

        var result = store.DiscoverProfiles();

        result.Profiles.Should().ContainSingle(profile => profile.Name == "Local Body");
        result.Profiles[0].SourceKind.Should().Be(ProfileSourceKind.LocalCustom);
        result.Profiles[0].FilePath.Should().EndWith("valid.json");
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "InvalidJson");
    }

    /// <summary>
    /// Verifies duplicate custom display names are resolved deterministically by sorted full path.
    /// </summary>
    [Fact]
    public void DiscoverProfilesSkipsLaterDuplicateNamesInSortedPathOrder()
    {
        Directory.CreateDirectory(directory);
        var firstPath = Path.Combine(directory, "a-first.json");
        var secondPath = Path.Combine(directory, "z-second.json");
        File.WriteAllText(secondPath, CreateProfileJson("Duplicate Body"));
        File.WriteAllText(firstPath, CreateProfileJson("Duplicate Body"));
        var store = new UserProfileStore(directory, new ProfileDefinitionService());

        var result = store.DiscoverProfiles();

        result.Profiles.Should().ContainSingle();
        result.Profiles[0].FilePath.Should().Be(firstPath);
        result.Diagnostics.Should().Contain(diagnostic => diagnostic.Code == "DuplicateProfileName");
    }

    /// <summary>
    /// Verifies saves use sanitized lower-case filenames, UTF-8 without BOM, and deterministic SHA-256 suffixes for collisions.
    /// </summary>
    [Fact]
    public void SaveProfileWritesAtomicUtf8JsonWithSanitizedCollisionSafeFilename()
    {
        Directory.CreateDirectory(directory);
        var conflictingPath = Path.Combine(directory, "body-name.json");
        File.WriteAllText(conflictingPath, CreateProfileJson("Other Existing"));
        var profile = CreateProfile("Body:Name");
        var store = new UserProfileStore(directory, new ProfileDefinitionService());

        var result = store.SaveProfile(profile);

        result.Succeeded.Should().BeTrue();
        result.FilePath.Should().NotBe(conflictingPath);
        result.FilePath.Should().EndWith(".json");
        Path.GetFileNameWithoutExtension(result.FilePath).Should().StartWith("body-name-");
        Path.GetFileNameWithoutExtension(result.FilePath).Should().HaveLength("body-name-".Length + 8);
        Path.GetFileNameWithoutExtension(result.FilePath).Should().EndWith(ExpectedHash8(profile.Name, result.FilePath));
        var bytes = File.ReadAllBytes(result.FilePath!);
        bytes.Take(3).Should().NotEqual(new byte[] { 0xEF, 0xBB, 0xBF });
        File.ReadAllText(result.FilePath!).Should().Contain("\"Name\": \"Body:Name\"");
    }

    private static CustomProfileDefinition CreateProfile(string name) => new(
        name,
        "Skyrim",
        new SliderProfile([new SliderDefault("Slider", 0.25f, 0.75f)], [new SliderMultiplier("Slider", 1.5f)], ["Slider"]),
        ProfileSourceKind.LocalCustom,
        null);

    private static string CreateProfileJson(string name) => new ProfileDefinitionService().ExportProfileJson(CreateProfile(name));

    private static string ExpectedHash8(string name, string candidatePath)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(name + candidatePath));
        return Convert.ToHexString(bytes).ToLowerInvariant()[..8];
    }
}
