namespace BS2BG.Core.Diagnostics;

/// <summary>
/// Describes a single read-only diagnostic fact about a project or workflow area.
/// </summary>
public sealed class DiagnosticFinding
{
    /// <summary>
    /// Creates an immutable diagnostic finding for the health report.
    /// </summary>
    /// <param name="severity">Risk/action severity shown to the user.</param>
    /// <param name="area">Workflow area such as Project, Profiles, Templates, Morphs/NPCs, Import, or Export.</param>
    /// <param name="title">Short user-facing finding title.</param>
    /// <param name="detail">Specific read-only detail explaining the finding.</param>
    /// <param name="targetKey">Optional preset, target, NPC, or output key that the App layer can navigate to.</param>
    /// <param name="actionHint">Optional guidance text; diagnostics never perform auto-fix actions.</param>
    public DiagnosticFinding(
        DiagnosticSeverity severity,
        string area,
        string title,
        string detail,
        string? targetKey = null,
        string? actionHint = null)
    {
        Severity = severity;
        Area = area ?? throw new ArgumentNullException(nameof(area));
        Title = title ?? throw new ArgumentNullException(nameof(title));
        Detail = detail ?? throw new ArgumentNullException(nameof(detail));
        TargetKey = targetKey;
        ActionHint = actionHint;
    }

    public DiagnosticSeverity Severity { get; }

    public string Area { get; }

    public string Title { get; }

    public string Detail { get; }

    public string? TargetKey { get; }

    public string? ActionHint { get; }
}
