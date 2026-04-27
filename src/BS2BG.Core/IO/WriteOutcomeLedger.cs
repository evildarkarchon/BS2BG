namespace BS2BG.Core.IO;

public enum FileWriteOutcome
{
    Planned,
    Written,
    Restored,
    Skipped,
    LeftUntouched,
    Incomplete
}

/// <summary>
/// Immutable file outcome entry for an atomic write attempt.
/// </summary>
public sealed class FileWriteLedgerEntry : IEquatable<FileWriteLedgerEntry>
{
    /// <summary>
    /// Creates a ledger entry for one target path and its latest known write outcome.
    /// </summary>
    public FileWriteLedgerEntry(string path, FileWriteOutcome outcome, string? detail = null)
    {
        Path = path ?? throw new ArgumentNullException(nameof(path));
        Outcome = outcome;
        Detail = detail;
    }

    public string Path { get; }

    public FileWriteOutcome Outcome { get; }

    public string? Detail { get; }

    public bool Equals(FileWriteLedgerEntry? other)
    {
        return other is not null
               && string.Equals(Path, other.Path, StringComparison.OrdinalIgnoreCase)
               && Outcome == other.Outcome
               && string.Equals(Detail, other.Detail, StringComparison.Ordinal);
    }

    public override bool Equals(object? obj) => Equals(obj as FileWriteLedgerEntry);

    public override int GetHashCode() => HashCode.Combine(
        StringComparer.OrdinalIgnoreCase.GetHashCode(Path),
        Outcome,
        Detail);
}

/// <summary>
/// Mutable builder used by AtomicFileWriter to preserve per-target outcomes while handling failures.
/// </summary>
public sealed class WriteOutcomeLedger
{
    private readonly FileWriteLedgerEntry[] entries;

    /// <summary>
    /// Creates a ledger with all supplied target paths marked as planned.
    /// </summary>
    public WriteOutcomeLedger(IEnumerable<string> paths)
    {
        entries = (paths ?? throw new ArgumentNullException(nameof(paths)))
            .Select(path => new FileWriteLedgerEntry(path, FileWriteOutcome.Planned))
            .ToArray();
    }

    public IReadOnlyList<FileWriteLedgerEntry> Entries => entries;

    /// <summary>
    /// Updates a target entry with the latest known outcome while preserving immutable snapshots for consumers.
    /// </summary>
    public void SetOutcome(int index, FileWriteOutcome outcome, string? detail = null)
    {
        if (index < 0 || index >= entries.Length) throw new ArgumentOutOfRangeException(nameof(index));

        entries[index] = new FileWriteLedgerEntry(entries[index].Path, outcome, detail);
    }

    public IReadOnlyList<FileWriteLedgerEntry> Snapshot() => entries.ToArray();
}
