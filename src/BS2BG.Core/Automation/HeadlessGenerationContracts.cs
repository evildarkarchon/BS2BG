using BS2BG.Core.Diagnostics;
using BS2BG.Core.IO;

namespace BS2BG.Core.Automation;

/// <summary>
/// Selects which export families a headless generation request should produce.
/// </summary>
public enum OutputIntent
{
    BodyGen,
    BosJson,
    All,
}

/// <summary>
/// Stable process exit-code contract shared by CLI automation commands and Core automation services.
/// </summary>
public enum AutomationExitCode
{
    Success = 0,
    UsageError = 1,
    ValidationBlocked = 2,
    OverwriteRefused = 3,
    IoFailure = 4,
}

/// <summary>
/// Captures the validated command-line generation request before it is handed to Core automation services.
/// </summary>
/// <param name="ProjectPath">Path to the input .jbs2bg project file.</param>
/// <param name="OutputDirectory">Directory where requested outputs should be written.</param>
/// <param name="Intent">The explicit output family selection requested by the caller.</param>
/// <param name="Overwrite">Whether existing output files may be replaced.</param>
/// <param name="OmitRedundantSliders">Whether generated BodyGen text should match the GUI omit-redundant-sliders preference.</param>
public sealed record HeadlessGenerationRequest(
    string ProjectPath,
    string OutputDirectory,
    OutputIntent Intent,
    bool Overwrite,
    bool OmitRedundantSliders);

/// <summary>
/// Describes the result of a headless generation attempt, including any validation report that blocked output.
/// </summary>
/// <param name="ExitCode">Stable CLI-oriented exit code for the generation attempt.</param>
/// <param name="Message">Human-readable status or failure detail suitable for stdout/stderr.</param>
/// <param name="WrittenFiles">Paths written by the completed generation request.</param>
/// <param name="ValidationReport">Optional validation report when validation participated in the outcome.</param>
/// <param name="WriteLedger">Per-file write outcomes when generation writes or partially fails.</param>
public sealed record HeadlessGenerationResult(
    AutomationExitCode ExitCode,
    string Message,
    IReadOnlyList<string> WrittenFiles,
    ProjectValidationReport? ValidationReport,
    IReadOnlyList<FileWriteLedgerEntry>? WriteLedger = null)
{
    public IReadOnlyList<FileWriteLedgerEntry> WriteLedger { get; init; } =
        WriteLedger ?? Array.Empty<FileWriteLedgerEntry>();
}
