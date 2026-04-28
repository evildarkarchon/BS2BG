using System.Globalization;
using System.Text;
using BS2BG.Core.Models;

namespace BS2BG.Core.Export;

/// <summary>
/// Plans BodyTypes of Skyrim JSON output paths with the exact filename sanitization and de-duplication used by the writer.
/// </summary>
public sealed class BosJsonExportPlanner
{
    private static readonly HashSet<char> WindowsReservedFileNameCharacters = new("<>:\"/\\|?*");

    private static readonly HashSet<string> WindowsReservedDeviceNames = new(StringComparer.OrdinalIgnoreCase)
    {
        "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    /// <summary>
    /// Produces the ordered target paths that a BoS export will write for the supplied presets.
    /// </summary>
    /// <param name="directoryPath">Destination directory for generated JSON files.</param>
    /// <param name="presets">Preset collection to export.</param>
    /// <returns>Planned output paths in writer order.</returns>
    public IReadOnlyList<string> Plan(string directoryPath, IEnumerable<SliderPreset> presets)
    {
        if (directoryPath is null) throw new ArgumentNullException(nameof(directoryPath));
        if (presets is null) throw new ArgumentNullException(nameof(presets));

        var usedFileNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        return presets
            .OrderBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase)
            .Select(preset => Path.Combine(directoryPath, GetUniqueFileName(SanitizeFileStem(preset.Name), usedFileNames)))
            .ToArray();
    }

    private static string SanitizeFileStem(string name)
    {
        var builder = new StringBuilder((name ?? string.Empty).Length);
        foreach (var character in name ?? string.Empty)
            builder.Append(IsReservedFileNameCharacter(character) ? '_' : character);

        var sanitized = builder.ToString().Trim().TrimEnd('.', ' ');
        return sanitized.Length == 0 ? "preset" : SanitizeWindowsDeviceName(sanitized);
    }

    private static bool IsReservedFileNameCharacter(char character)
    {
        return character < ' '
               || WindowsReservedFileNameCharacters.Contains(character)
               || Path.GetInvalidFileNameChars().Contains(character);
    }

    private static string SanitizeWindowsDeviceName(string fileStem)
    {
        var extensionSeparator = fileStem.IndexOf('.');
        var deviceNameLength = extensionSeparator < 0 ? fileStem.Length : extensionSeparator;
        if (deviceNameLength == 0) return fileStem;

        var deviceName = fileStem.Substring(0, deviceNameLength);
        return WindowsReservedDeviceNames.Contains(deviceName)
            ? fileStem.Insert(deviceNameLength, "_")
            : fileStem;
    }

    private static string GetUniqueFileName(string fileStem, HashSet<string> usedFileNames)
    {
        var candidate = fileStem + ".json";
        var suffix = 2;
        while (!usedFileNames.Add(candidate))
        {
            candidate = fileStem + " (" + suffix.ToString(CultureInfo.InvariantCulture) + ").json";
            suffix++;
        }

        return candidate;
    }
}
