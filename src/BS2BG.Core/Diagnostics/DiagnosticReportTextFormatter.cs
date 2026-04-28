using System.Diagnostics.CodeAnalysis;
using System.Text;
using BS2BG.Core.Bundling;

namespace BS2BG.Core.Diagnostics;

/// <summary>
/// Formats Core project validation reports for CLI and bundle support artifacts without App/Avalonia dependencies.
/// </summary>
[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "The formatter is exposed as an injectable service surface for CLI and bundle composition.")]
public sealed class DiagnosticReportTextFormatter
{
    /// <summary>
    /// Formats a validation report as deterministic plain text and scrubs caller-provided private path roots.
    /// </summary>
    /// <param name="report">Validation report to format.</param>
    /// <param name="privateRoots">Local roots that must be redacted from support text.</param>
    /// <returns>Plain-text report using LF newlines and no known private roots.</returns>
    public string Format(ProjectValidationReport report, IEnumerable<string>? privateRoots = null)
    {
        if (report is null) throw new ArgumentNullException(nameof(report));

        var builder = new StringBuilder();
        builder.AppendLine("BS2BG Validation Report");
        builder.AppendLine("========================");
        builder.AppendLine($"Blockers: {report.BlockerCount}");
        builder.AppendLine($"Cautions: {report.CautionCount}");
        builder.AppendLine($"Info: {report.InfoCount}");
        builder.AppendLine();

        foreach (var finding in report.Findings.OrderBy(finding => finding.Severity).ThenBy(finding => finding.Area, StringComparer.Ordinal).ThenBy(finding => finding.Title, StringComparer.Ordinal))
        {
            builder.AppendLine($"[{finding.Severity}] {finding.Area} - {finding.Title}");
            builder.AppendLine(finding.Detail);
            if (!string.IsNullOrWhiteSpace(finding.TargetKey)) builder.AppendLine($"Target: {finding.TargetKey}");
            if (!string.IsNullOrWhiteSpace(finding.ActionHint)) builder.AppendLine($"Action: {finding.ActionHint}");
            builder.AppendLine();
        }

        return BundlePathScrubber.Scrub(builder.ToString().Replace("\r\n", "\n", StringComparison.Ordinal), privateRoots ?? Array.Empty<string>());
    }
}
