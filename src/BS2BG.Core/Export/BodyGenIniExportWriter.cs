using System.Diagnostics.CodeAnalysis;
using System.Text;

namespace BS2BG.Core.Export;

[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "Export writers are registered as injectable services.")]
public sealed class BodyGenIniExportWriter
{
    private static readonly Encoding Utf8NoBom = new UTF8Encoding(false);

    public BodyGenIniExportResult Write(string directoryPath, string templatesText, string morphsText)
    {
        if (directoryPath is null) throw new ArgumentNullException(nameof(directoryPath));

        Directory.CreateDirectory(directoryPath);

        var templatesPath = Path.Combine(directoryPath, "templates.ini");
        var morphsPath = Path.Combine(directoryPath, "morphs.ini");
        File.WriteAllText(templatesPath, NormalizeCrLf(templatesText), Utf8NoBom);
        File.WriteAllText(morphsPath, NormalizeCrLf(morphsText), Utf8NoBom);

        return new BodyGenIniExportResult(templatesPath, morphsPath);
    }

    private static string NormalizeCrLf(string value)
    {
        return (value ?? string.Empty)
            .Replace("\r\n", "\n", StringComparison.Ordinal)
            .Replace("\r", "\n", StringComparison.Ordinal)
            .Replace("\n", "\r\n", StringComparison.Ordinal);
    }
}

public sealed class BodyGenIniExportResult(string templatesPath, string morphsPath)
{
    public string TemplatesPath { get; } = templatesPath ?? throw new ArgumentNullException(nameof(templatesPath));

    public string MorphsPath { get; } = morphsPath ?? throw new ArgumentNullException(nameof(morphsPath));
}
