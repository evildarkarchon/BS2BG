using System.Globalization;
using System.Text;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.Core.Export;

public sealed class BosJsonExportWriter(TemplateGenerationService templateGenerationService)
{
    private static readonly Encoding Utf8NoBom = new UTF8Encoding(false);
    private static readonly HashSet<char> WindowsReservedFileNameCharacters = new("<>:\"/\\|?*");

    private static readonly HashSet<string> WindowsReservedDeviceNames = new(StringComparer.OrdinalIgnoreCase)
    {
        "CON",
        "PRN",
        "AUX",
        "NUL",
        "COM1",
        "COM2",
        "COM3",
        "COM4",
        "COM5",
        "COM6",
        "COM7",
        "COM8",
        "COM9",
        "LPT1",
        "LPT2",
        "LPT3",
        "LPT4",
        "LPT5",
        "LPT6",
        "LPT7",
        "LPT8",
        "LPT9"
    };

    private readonly TemplateGenerationService templateGenerationService = templateGenerationService
                                                                           ?? throw new ArgumentNullException(
                                                                               nameof(templateGenerationService));

    public BosJsonExportResult Write(
        string directoryPath,
        IEnumerable<SliderPreset> presets,
        TemplateProfileCatalog profileCatalog)
    {
        if (directoryPath is null) throw new ArgumentNullException(nameof(directoryPath));

        if (presets is null) throw new ArgumentNullException(nameof(presets));

        if (profileCatalog is null) throw new ArgumentNullException(nameof(profileCatalog));

        Directory.CreateDirectory(directoryPath);

        var usedFileNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var filePaths = new List<string>();
        foreach (var preset in presets.OrderBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase))
        {
            var fileName = GetUniqueFileName(SanitizeFileStem(preset.Name), usedFileNames);
            var filePath = Path.Combine(directoryPath, fileName);
            var json = templateGenerationService.PreviewBosJson(
                preset,
                profileCatalog.GetProfile(preset.ProfileName));
            File.WriteAllText(filePath, json, Utf8NoBom);
            filePaths.Add(filePath);
        }

        return new BosJsonExportResult(filePaths);
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

public sealed class BosJsonExportResult(IEnumerable<string> filePaths)
{
    public IReadOnlyList<string> FilePaths { get; } =
        (filePaths ?? throw new ArgumentNullException(nameof(filePaths))).ToArray();
}
