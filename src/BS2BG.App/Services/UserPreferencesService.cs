using System.Text.Json;
using Avalonia;
using Avalonia.Styling;
using Avalonia.Threading;

namespace BS2BG.App.Services;

public enum ThemePreference
{
    System,
    Light,
    Dark
}

public sealed class UserPreferences
{
    public ThemePreference Theme { get; set; } = ThemePreference.System;

    public bool OmitRedundantSliders { get; set; }

    public string? ProjectFolder { get; set; }

    public string? BodySlideXmlFolder { get; set; }

    public string? BodyGenExportFolder { get; set; }

    public string? BosJsonExportFolder { get; set; }
}

public interface IUserPreferencesService
{
    UserPreferences Load();

    bool Save(UserPreferences preferences);
}

public sealed class UserPreferencesService(string preferencesPath) : IUserPreferencesService
{
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    private readonly string preferencesPath =
        preferencesPath ?? throw new ArgumentNullException(nameof(preferencesPath));

    public UserPreferencesService()
        : this(Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "jBS2BG",
            "user-preferences.json"))
    {
    }

    public UserPreferences Load()
    {
        if (!File.Exists(preferencesPath)) return new UserPreferences();

        try
        {
            return JsonSerializer.Deserialize<UserPreferences>(
                       File.ReadAllText(preferencesPath),
                       JsonOptions)
                   ?? new UserPreferences();
        }
        catch (JsonException)
        {
            return new UserPreferences();
        }
        catch (IOException)
        {
            return new UserPreferences();
        }
        catch (UnauthorizedAccessException)
        {
            return new UserPreferences();
        }
    }

    public bool Save(UserPreferences preferences)
    {
        ArgumentNullException.ThrowIfNull(preferences);

        try
        {
            var directory = Path.GetDirectoryName(preferencesPath);
            if (!string.IsNullOrWhiteSpace(directory)) Directory.CreateDirectory(directory);

            File.WriteAllText(preferencesPath, JsonSerializer.Serialize(preferences, JsonOptions));
            return true;
        }
        catch (IOException)
        {
            return false;
        }
        catch (UnauthorizedAccessException)
        {
            return false;
        }
    }
}

public static class ThemePreferenceApplier
{
    public static ThemeVariant ToThemeVariant(ThemePreference preference)
    {
        return preference switch
        {
            ThemePreference.Light => ThemeVariant.Light,
            ThemePreference.Dark => ThemeVariant.Dark,
            _ => ThemeVariant.Default
        };
    }

    public static void Apply(ThemePreference preference)
    {
        if (Application.Current is { } application)
        {
            var themeVariant = ToThemeVariant(preference);
            if (Dispatcher.UIThread.CheckAccess())
                application.RequestedThemeVariant = themeVariant;
            else
                Dispatcher.UIThread.Post(() => application.RequestedThemeVariant = themeVariant);
        }
    }
}
