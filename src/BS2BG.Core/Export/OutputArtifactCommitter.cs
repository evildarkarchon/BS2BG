using BS2BG.Core.Diagnostics;
using BS2BG.Core.IO;

namespace BS2BG.Core.Export;

/// <summary>
/// Commits the frozen bytes of one output artifact group atomically beneath a caller-selected filesystem root.
/// </summary>
public class OutputArtifactCommitter
{
    /// <summary>
    /// Writes one commit group without regenerating or re-encoding its content.
    /// </summary>
    /// <param name="destinationRoot">Filesystem directory mounted as the group's root.</param>
    /// <param name="group">Atomic group whose artifact order controls staging and result order.</param>
    public virtual OutputArtifactCommitResult Commit(string destinationRoot, OutputArtifactCommitGroup group)
    {
        if (destinationRoot is null) throw new ArgumentNullException(nameof(destinationRoot));
        if (group is null) throw new ArgumentNullException(nameof(group));

        Directory.CreateDirectory(destinationRoot);
        var entries = group.Artifacts
            .Select(artifact => (
                Path: OutputArtifactPreflight.ResolveTargetPath(destinationRoot, artifact.RelativePath),
                Content: artifact.CopyContent()))
            .ToArray();
        AtomicFileWriter.WriteAtomicBatch(entries);
        return new OutputArtifactCommitResult(entries.Select(entry => entry.Path));
    }
}

/// <summary>
/// Reports the resolved filesystem paths written by one completed output artifact commit group.
/// </summary>
public sealed class OutputArtifactCommitResult
{
    /// <summary>
    /// Creates a result whose path order matches the committed artifact order.
    /// </summary>
    /// <param name="writtenFiles">Resolved paths written by the completed atomic group.</param>
    public OutputArtifactCommitResult(IEnumerable<string> writtenFiles) =>
        WrittenFiles = Array.AsReadOnly((writtenFiles ?? throw new ArgumentNullException(nameof(writtenFiles))).ToArray());

    public IReadOnlyList<string> WrittenFiles { get; }
}
