using System.Text;
using BS2BG.Core.IO;
using Xunit;

namespace BS2BG.Tests;

public sealed class AtomicFileWriterOutcomeTests
{
    [Fact]
    public void WriteAtomicBatchReportsRestoredLeftUntouchedAndSkippedOutcomesOnCommitFailure()
    {
        using var directory = new TemporaryDirectory();
        var firstPath = Path.Combine(directory.Path, "first.txt");
        var secondPath = Path.Combine(directory.Path, "second.txt");
        var thirdPath = Path.Combine(directory.Path, "third.txt");
        File.WriteAllText(firstPath, "ORIGINAL_FIRST");
        File.WriteAllText(secondPath, "ORIGINAL_SECOND");
        File.WriteAllText(thirdPath, "ORIGINAL_THIRD");

        using (new FileStream(secondPath, FileMode.Open, FileAccess.Read, FileShare.None))
        {
            var act = () => AtomicFileWriter.WriteAtomicBatch(
                new[] { (firstPath, "NEW_FIRST"), (secondPath, "NEW_SECOND"), (thirdPath, "NEW_THIRD") },
                Encoding.UTF8);

            var exception = act.Should().Throw<AtomicWriteException>().Which;
            exception.InnerException.Should().BeOfType<IOException>();
            exception.Entries.Select(entry => (entry.Path, entry.Outcome)).Should().Equal(
                (firstPath, FileWriteOutcome.Restored),
                (secondPath, FileWriteOutcome.LeftUntouched),
                (thirdPath, FileWriteOutcome.Skipped));
        }

        File.ReadAllText(firstPath).Should().Be("ORIGINAL_FIRST");
        File.ReadAllText(secondPath).Should().Be("ORIGINAL_SECOND");
        File.ReadAllText(thirdPath).Should().Be("ORIGINAL_THIRD");
    }

    [Fact]
    public void WriteAtomicBatchReportsIncompleteWhenRollbackFails()
    {
        using var directory = new TemporaryDirectory();
        var firstPath = Path.Combine(directory.Path, "first.txt");
        var secondPath = Path.Combine(directory.Path, "second.txt");
        File.WriteAllText(firstPath, "ORIGINAL_FIRST");
        File.WriteAllText(secondPath, "ORIGINAL_SECOND");
        AtomicFileWriter.RollbackFailureInjector = path =>
        {
            if (string.Equals(path, firstPath, StringComparison.OrdinalIgnoreCase))
                throw new IOException("simulated rollback failure");
        };

        try
        {
            using (new FileStream(secondPath, FileMode.Open, FileAccess.Read, FileShare.None))
            {
                var act = () => AtomicFileWriter.WriteAtomicBatch(
                    new[] { (firstPath, "NEW_FIRST"), (secondPath, "NEW_SECOND") },
                    Encoding.UTF8);

                var exception = act.Should().Throw<AtomicWriteException>().Which;
                exception.RollbackException.Should().BeOfType<AggregateException>();
                exception.Entries.Select(entry => (entry.Path, entry.Outcome)).Should().Equal(
                    (firstPath, FileWriteOutcome.Incomplete),
                    (secondPath, FileWriteOutcome.LeftUntouched));
            }
        }
        finally
        {
            AtomicFileWriter.RollbackFailureInjector = null;
        }
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose() => Directory.Delete(Path, true);
    }
}
