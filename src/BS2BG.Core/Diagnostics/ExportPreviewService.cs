using System.Diagnostics.CodeAnalysis;
using System.Globalization;
using System.Text;
using BS2BG.Core.Generation;
using BS2BG.Core.Models;

namespace BS2BG.Core.Diagnostics;

/// <summary>
/// Builds read-only export previews from existing generation output and writer path rules.
/// </summary>
[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "Export preview is exposed as an injectable service surface.")]
public sealed class ExportPreviewService
{
    private const int DefaultSnippetLineCount = 3;
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

    private readonly TemplateGenerationService templateGenerationService;

    /// <summary>
    /// Creates a preview service using the standard template generation service for BoS JSON snippets.
    /// </summary>
    public ExportPreviewService()
        : this(new TemplateGenerationService())
    {
    }

    /// <summary>
    /// Creates a preview service with the supplied generation dependency.
    /// </summary>
    /// <param name="templateGenerationService">Service used to generate BoS JSON preview text.</param>
    public ExportPreviewService(TemplateGenerationService templateGenerationService)
    {
        this.templateGenerationService = templateGenerationService
                                         ?? throw new ArgumentNullException(nameof(templateGenerationService));
    }

    /// <summary>
    /// Previews BodyGen INI target paths, overwrite state, and snippets without creating directories or writing files.
    /// </summary>
    /// <param name="directoryPath">Directory that the writer would receive for `templates.ini` and `morphs.ini`.</param>
    /// <param name="templatesText">Already-generated templates.ini content from the real generation path.</param>
    /// <param name="morphsText">Already-generated morphs.ini content from the real generation path.</param>
    public ExportPreviewResult PreviewBodyGen(string directoryPath, string templatesText, string morphsText)
    {
        if (directoryPath is null) throw new ArgumentNullException(nameof(directoryPath));

        var files = new[]
        {
            CreatePreviewFile(Path.Combine(directoryPath, "templates.ini"), templatesText),
            CreatePreviewFile(Path.Combine(directoryPath, "morphs.ini"), morphsText)
        };

        return new ExportPreviewResult(files, HasRisk(files));
    }

    /// <summary>
    /// Previews BoS JSON target paths and snippets using writer-equivalent sanitized unique filenames without writing files.
    /// </summary>
    /// <param name="directoryPath">Directory that would receive one JSON file per preset.</param>
    /// <param name="presets">Presets to preview in the same name-sorted order used by the writer.</param>
    /// <param name="profileCatalog">Profile catalog used by the real BoS JSON generation path.</param>
    public ExportPreviewResult PreviewBosJson(
        string directoryPath,
        IEnumerable<SliderPreset> presets,
        TemplateProfileCatalog profileCatalog)
    {
        if (directoryPath is null) throw new ArgumentNullException(nameof(directoryPath));

        if (presets is null) throw new ArgumentNullException(nameof(presets));

        if (profileCatalog is null) throw new ArgumentNullException(nameof(profileCatalog));

        var usedFileNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var files = new List<ExportPreviewFile>();
        foreach (var preset in presets.OrderBy(preset => preset.Name, StringComparer.OrdinalIgnoreCase))
        {
            var fileName = GetUniqueFileName(SanitizeFileStem(preset.Name), usedFileNames);
            var filePath = Path.Combine(directoryPath, fileName);
            var json = templateGenerationService.PreviewBosJson(
                preset,
                profileCatalog.GetProfile(preset.ProfileName));
            files.Add(CreatePreviewFile(filePath, json));
        }

        return new ExportPreviewResult(files, HasRisk(files));
    }

    private static ExportPreviewFile CreatePreviewFile(string path, string? content)
    {
        return new ExportPreviewFile(path, File.Exists(path), TakeSnippetLines(content));
    }

    private static bool HasRisk(IReadOnlyList<ExportPreviewFile> files)
    {
        return files.Count > 1 || files.Any(file => file.WillOverwrite);
    }

    private static string[] TakeSnippetLines(string? content)
    {
        if (string.IsNullOrEmpty(content)) return Array.Empty<string>();

        return content
            .Replace("\r\n", "\n", StringComparison.Ordinal)
            .Replace('\r', '\n')
            .Split('\n')
            .Select(line => line.Trim())
            .Where(line => line.Length > 0)
            .Take(DefaultSnippetLineCount)
            .ToArray();
    }

    // These filename helpers intentionally mirror BosJsonExportWriter without calling Write; preview must not create files.
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
