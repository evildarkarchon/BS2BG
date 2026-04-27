using System.Globalization;
using System.Text;
using BS2BG.App.ViewModels;

namespace BS2BG.App.Services;

/// <summary>
/// Formats Diagnostics findings as plain text suitable for clipboard sharing and support review.
/// </summary>
public sealed class DiagnosticsReportFormatter
{
    private static readonly string[] AreaOrder =
    {
        "Project", "Profiles", "Templates", "Morphs/NPCs", "Import", "Export"
    };

    private static readonly IReadOnlyDictionary<string, string> AreaHeadings = new Dictionary<string, string>
    {
        ["Project"] = "## Project",
        ["Profiles"] = "## Profiles",
        ["Templates"] = "## Templates",
        ["Morphs/NPCs"] = "## Morphs/NPCs",
        ["Import"] = "## Import",
        ["Export"] = "## Export"
    };

    /// <summary>
    /// Creates a grouped, read-only text report from the currently displayed diagnostics findings.
    /// </summary>
    /// <param name="findings">Binding-ready diagnostic findings to include in the report.</param>
    /// <param name="generatedAt">Timestamp to include for report context.</param>
    /// <returns>A plain-text report grouped by workflow area, or an empty-state report when there are no findings.</returns>
    public string Format(IEnumerable<DiagnosticFindingViewModel> findings, DateTimeOffset generatedAt)
    {
        ArgumentNullException.ThrowIfNull(findings);

        var rows = findings.ToArray();
        var builder = new StringBuilder();
        builder.AppendLine("BS2BG Diagnostics Report");
        builder.AppendLine("Generated: " + generatedAt.ToString("u", CultureInfo.InvariantCulture));
        builder.AppendLine();

        if (rows.Length == 0)
        {
            builder.AppendLine("No diagnostics yet");
            return builder.ToString().TrimEnd();
        }

        foreach (var area in AreaOrder)
        {
            var areaRows = rows
                .Where(finding => string.Equals(finding.Area, area, StringComparison.OrdinalIgnoreCase))
                .OrderBy(finding => SeveritySortIndex(finding.SeverityLabel))
                .ThenBy(finding => finding.Title, StringComparer.OrdinalIgnoreCase)
                .ToArray();
            if (areaRows.Length == 0) continue;

            builder.AppendLine(AreaHeadings[area]);
            foreach (var finding in areaRows)
            {
                builder.AppendLine("- [" + finding.SeverityLabel + "] " + finding.Title);
                builder.AppendLine("  Detail: " + finding.Detail);
                if (!string.IsNullOrWhiteSpace(finding.TargetKey))
                    builder.AppendLine("  Target: " + finding.TargetKey);
            }

            builder.AppendLine();
        }

        return builder.ToString().TrimEnd();
    }

    private static int SeveritySortIndex(string severityLabel) => severityLabel switch
    {
        "Blocker" => 0,
        "Caution" => 1,
        "Info" => 2,
        _ => 3
    };
}
