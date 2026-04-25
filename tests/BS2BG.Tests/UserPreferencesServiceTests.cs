using System.Security.AccessControl;
using System.Security.Principal;
using BS2BG.App.Services;
using Xunit;

namespace BS2BG.Tests;

public sealed class UserPreferencesServiceTests
{
    [Fact]
    public void LoadReturnsDefaultsWhenPreferenceFileCannotBeRead()
    {
        if (!OperatingSystem.IsWindows()) return;

        using var directory = new TemporaryDirectory();
        var preferencesPath = directory.WriteText(
            "user-preferences.json",
            """
            {
              "Theme": "Dark"
            }
            """);
        var file = new FileInfo(preferencesPath);
        var currentUser = WindowsIdentity.GetCurrent().User
                          ?? throw new InvalidOperationException("Current Windows user has no SID.");
        var denyRead = new FileSystemAccessRule(
            currentUser,
            FileSystemRights.ReadData,
            AccessControlType.Deny);
        var security = file.GetAccessControl();
        security.AddAccessRule(denyRead);
        file.SetAccessControl(security);

        try
        {
            var loaded = new UserPreferencesService(preferencesPath).Load();

            loaded.Theme.Should().Be(ThemePreference.System);
        }
        finally
        {
            security = file.GetAccessControl();
            security.RemoveAccessRule(denyRead);
            file.SetAccessControl(security);
        }
    }

    [Fact]
    public void SaveReturnsFalseWhenPreferencesPathCannotBeWritten()
    {
        using var directory = new TemporaryDirectory();
        var preferencesPath = Path.Combine(directory.Path, "user-preferences-as-directory");
        Directory.CreateDirectory(preferencesPath);

        var saved = new UserPreferencesService(preferencesPath)
            .Save(new UserPreferences { Theme = ThemePreference.Dark });

        saved.Should().BeFalse();
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose() => Directory.Delete(Path, true);

        public string WriteText(string fileName, string text)
        {
            var filePath = System.IO.Path.Combine(Path, fileName);
            File.WriteAllText(filePath, text);
            return filePath;
        }
    }
}
