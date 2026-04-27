using System.Text;

namespace BS2BG.Core.IO;

public static class AtomicFileWriter
{
    internal static Action<string>? RollbackFailureInjector { get; set; }

    public static void WriteAtomic(string targetPath, string content, Encoding encoding)
    {
        if (targetPath is null) throw new ArgumentNullException(nameof(targetPath));

        if (content is null) throw new ArgumentNullException(nameof(content));

        if (encoding is null) throw new ArgumentNullException(nameof(encoding));

        var fullPath = Path.GetFullPath(targetPath);
        var tempPath = CreateTempPath(fullPath);
        var replaced = false;
        try
        {
            File.WriteAllText(tempPath, content, encoding);
            ReplaceWithTempFile(tempPath, fullPath);
            replaced = true;
        }
        finally
        {
            if (!replaced) TryDeleteTempFile(tempPath);
        }
    }

    public static void WriteAtomicPair(
        string firstPath,
        string firstContent,
        string secondPath,
        string secondContent,
        Encoding encoding)
    {
        if (firstPath is null) throw new ArgumentNullException(nameof(firstPath));

        if (firstContent is null) throw new ArgumentNullException(nameof(firstContent));

        if (secondPath is null) throw new ArgumentNullException(nameof(secondPath));

        if (secondContent is null) throw new ArgumentNullException(nameof(secondContent));

        if (encoding is null) throw new ArgumentNullException(nameof(encoding));

        WriteAtomicBatch(
            new[] { (firstPath, firstContent), (secondPath, secondContent) },
            encoding);
    }

    public static void WriteAtomicBatch(
        IReadOnlyList<(string Path, string Content)> entries,
        Encoding encoding)
    {
        if (entries is null) throw new ArgumentNullException(nameof(entries));

        if (encoding is null) throw new ArgumentNullException(nameof(encoding));

        if (entries.Count == 0)
            throw new ArgumentException("Entries must contain at least one item.", nameof(entries));

        var normalized = new List<(string FullPath, string TempPath, string Content)>(entries.Count);
        var seenPaths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var entry in entries)
        {
            if (entry.Path is null)
                throw new ArgumentException("Entry path cannot be null.", nameof(entries));

            if (entry.Content is null)
                throw new ArgumentException("Entry content cannot be null.", nameof(entries));

            var fullPath = Path.GetFullPath(entry.Path);
            if (!seenPaths.Add(fullPath))
                throw new ArgumentException(
                    "Duplicate target path: " + fullPath,
                    nameof(entries));

            normalized.Add((fullPath, CreateTempPath(fullPath), entry.Content));
        }

        var ledger = new WriteOutcomeLedger(normalized.Select(entry => entry.FullPath));

        try
        {
            foreach (var entry in normalized)
                File.WriteAllText(entry.TempPath, entry.Content, encoding);
        }
        catch
        {
            foreach (var entry in normalized) TryDeleteTempFile(entry.TempPath);

            throw;
        }

        var committed = new List<(int Index, string FullPath, string? BackupPath)>(normalized.Count);
        for (var i = 0; i < normalized.Count; i++)
        {
            var (fullPath, tempPath, _) = normalized[i];
            try
            {
                if (File.Exists(fullPath))
                {
                    var backupPath = fullPath + ".bak." + Guid.NewGuid().ToString("N");
                    File.Replace(tempPath, fullPath, backupPath);
                    committed.Add((i, fullPath, backupPath));
                }
                else
                {
                    File.Move(tempPath, fullPath);
                    committed.Add((i, fullPath, null));
                }

                ledger.SetOutcome(i, FileWriteOutcome.Written);
            }
            catch (Exception commitException)
            {
                var rollbackExceptions = new List<Exception>();
                for (var j = committed.Count - 1; j >= 0; j--)
                {
                    var (committedIndex, committedPath, backupPath) = committed[j];
                    try
                    {
                        RollbackFailureInjector?.Invoke(committedPath);
                        if (backupPath is not null)
                            File.Replace(backupPath, committedPath, null);
                        else
                            File.Delete(committedPath);

                        ledger.SetOutcome(committedIndex, FileWriteOutcome.Restored);
                    }
                    catch (Exception rollbackException)
                    {
                        ledger.SetOutcome(committedIndex, FileWriteOutcome.Incomplete, rollbackException.Message);
                        rollbackExceptions.Add(rollbackException);
                    }
                }

                for (var k = i; k < normalized.Count; k++) TryDeleteTempFile(normalized[k].TempPath);

                ledger.SetOutcome(i, FileWriteOutcome.LeftUntouched, commitException.Message);
                for (var k = i + 1; k < normalized.Count; k++)
                    ledger.SetOutcome(k, FileWriteOutcome.Skipped);

                if (rollbackExceptions.Count == 0)
                    throw new AtomicWriteException(
                        "Atomic batch write failed and all committed targets were restored.",
                        commitException,
                        ledger.Snapshot());

                var rollbackAggregate = new AggregateException(
                    "Atomic batch write failed and rollback was incomplete.",
                    rollbackExceptions);
                throw new AtomicWriteException(
                    "Atomic batch write failed and rollback was incomplete.",
                    commitException,
                    ledger.Snapshot(),
                    rollbackAggregate);
            }
        }

        foreach (var (_, _, backupPath) in committed)
            if (backupPath is not null)
                TryDeleteTempFile(backupPath);
    }

    private static string CreateTempPath(string targetPath)
    {
        var directory = Path.GetDirectoryName(targetPath)
                        ?? throw new InvalidOperationException("Path must include a directory.");
        var fileName = Path.GetFileName(targetPath);
        return Path.Combine(directory, "." + fileName + "." + Guid.NewGuid().ToString("N") + ".tmp");
    }

    private static void ReplaceWithTempFile(string tempPath, string targetPath)
    {
        if (File.Exists(targetPath))
        {
            File.Replace(tempPath, targetPath, null);
            return;
        }

        File.Move(tempPath, targetPath);
    }

    private static void TryDeleteTempFile(string tempPath)
    {
        try
        {
            if (File.Exists(tempPath)) File.Delete(tempPath);
        }
        catch (IOException)
        {
            // Best-effort cleanup must not hide the original write or rollback failure.
        }
        catch (UnauthorizedAccessException)
        {
            // Best-effort cleanup must not hide the original write or rollback failure.
        }
    }
}
