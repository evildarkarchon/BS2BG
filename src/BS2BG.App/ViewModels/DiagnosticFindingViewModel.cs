using BS2BG.Core.Diagnostics;

namespace BS2BG.App.ViewModels;

/// <summary>
/// Presentation row for a read-only diagnostic finding surfaced by the App diagnostics workflow.
/// </summary>
public sealed class DiagnosticFindingViewModel
{
    /// <summary>
    /// Creates a binding-ready diagnostic row from a Core diagnostic finding.
    /// </summary>
    /// <param name="finding">Core finding to expose without mutation or auto-fix behavior.</param>
    public DiagnosticFindingViewModel(DiagnosticFinding finding)
    {
        ArgumentNullException.ThrowIfNull(finding);

        Severity = finding.Severity;
        SeverityLabel = FormatSeverityLabel(finding.Severity);
        Area = finding.Area;
        Title = finding.Title;
        Detail = finding.Detail;
        TargetKey = finding.TargetKey;
        ActionHint = finding.ActionHint;
        Code = finding.Code;
        Category = finding.Category;
        CanNavigate = !string.IsNullOrWhiteSpace(finding.TargetKey);
        NavigationTarget = finding.TargetKey ?? string.Empty;
    }

    public DiagnosticSeverity Severity { get; }

    public string SeverityLabel { get; }

    public string Area { get; }

    public string Title { get; }

    public string Detail { get; }

    public string? TargetKey { get; }

    public string? ActionHint { get; }

    public string? Code { get; }

    public string? Category { get; }

    public bool CanNavigate { get; }

    public string NavigationTarget { get; }

    private static string FormatSeverityLabel(DiagnosticSeverity severity) => severity switch
    {
        DiagnosticSeverity.Blocker => "Blocker",
        DiagnosticSeverity.Caution => "Caution",
        DiagnosticSeverity.Info => "Info",
        _ => severity.ToString()
    };
}
