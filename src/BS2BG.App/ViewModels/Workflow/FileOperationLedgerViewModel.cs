using BS2BG.Core.IO;

namespace BS2BG.App.ViewModels.Workflow;

/// <summary>
/// Binding-ready row describing the latest known outcome for a save or export target file.
/// </summary>
public sealed class FileOperationLedgerViewModel
{
    /// <summary>
    /// Creates a file operation ledger row from a Core atomic write ledger entry.
    /// </summary>
    public FileOperationLedgerViewModel(FileWriteLedgerEntry entry)
    {
        ArgumentNullException.ThrowIfNull(entry);

        Path = entry.Path;
        OutcomeLabel = FormatOutcome(entry.Outcome);
        Detail = entry.Detail ?? string.Empty;
    }

    public string Path { get; }

    public string OutcomeLabel { get; }

    public string Detail { get; }

    private static string FormatOutcome(FileWriteOutcome outcome) => outcome switch
    {
        FileWriteOutcome.Written => "Written",
        FileWriteOutcome.Restored => "Restored",
        FileWriteOutcome.Skipped => "Skipped",
        FileWriteOutcome.LeftUntouched => "Left untouched",
        FileWriteOutcome.Incomplete => "Incomplete/unknown",
        _ => "Incomplete/unknown"
    };
}
