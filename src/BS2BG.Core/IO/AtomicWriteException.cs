namespace BS2BG.Core.IO;

/// <summary>
/// Exception thrown when an atomic write fails after target outcomes can be described.
/// </summary>
public sealed class AtomicWriteException : IOException
{
    /// <summary>
    /// Creates an atomic write exception with the original failure, optional rollback failure, and outcome ledger.
    /// </summary>
    public AtomicWriteException(
        string message,
        Exception innerException,
        IEnumerable<FileWriteLedgerEntry> entries,
        Exception? rollbackException = null)
        : base(message, innerException)
    {
        Entries = (entries ?? throw new ArgumentNullException(nameof(entries))).ToArray();
        RollbackException = rollbackException;
    }

    public IReadOnlyList<FileWriteLedgerEntry> Entries { get; }

    public Exception? RollbackException { get; }
}
