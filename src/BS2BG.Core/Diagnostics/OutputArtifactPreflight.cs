using System.Diagnostics.CodeAnalysis;
using System.Text;
using BS2BG.Core.Export;

namespace BS2BG.Core.Diagnostics;

/// <summary>
/// Projects an immutable output artifact commit group onto a filesystem root for overwrite confirmation and preview display.
/// </summary>
[SuppressMessage("Performance", "CA1822:Mark members as static",
    Justification = "Output preflight is an injectable consumer seam shared by GUI and headless composition.")]
public sealed class OutputArtifactPreflight
{
    private const int DefaultSnippetLineCount = 3;
    private static readonly Encoding Utf8NoBomStrict = new UTF8Encoding(false, true);

    /// <summary>
    /// Resolves target paths, observes current overwrite state, and decodes snippets without writing or regenerating artifacts.
    /// </summary>
    /// <param name="destinationRoot">Filesystem directory mounted as the commit group's root.</param>
    /// <param name="group">Frozen artifacts whose exact bytes supply preview snippets.</param>
    /// <returns>Current target disposition and decoded preview snippets in artifact order.</returns>
    public ExportPreviewResult Preview(
        string destinationRoot,
        OutputArtifactCommitGroup group) => Preview(destinationRoot, group, CancellationToken.None);

    /// <summary>
    /// Resolves target paths and snippets while observing cancellation between artifacts.
    /// </summary>
    /// <param name="destinationRoot">Filesystem directory mounted as the commit group's root.</param>
    /// <param name="group">Frozen artifacts whose exact bytes supply preview snippets.</param>
    /// <param name="cancellationToken">Cancels filesystem inspection between artifacts.</param>
    /// <returns>Current target disposition and decoded preview snippets in artifact order.</returns>
    public ExportPreviewResult Preview(
        string destinationRoot,
        OutputArtifactCommitGroup group,
        CancellationToken cancellationToken)
    {
        if (destinationRoot is null) throw new ArgumentNullException(nameof(destinationRoot));
        if (group is null) throw new ArgumentNullException(nameof(group));

        var files = new List<ExportPreviewFile>(group.Artifacts.Count);
        foreach (var artifact in group.Artifacts)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var targetPath = ResolveTargetPath(destinationRoot, artifact.RelativePath);
            files.Add(new ExportPreviewFile(
                targetPath,
                File.Exists(targetPath),
                TakeSnippetLines(Utf8NoBomStrict.GetString(artifact.CopyContent()))));
        }

        return new ExportPreviewResult(files, files.Count > 1 || files.Any(file => file.WillOverwrite));
    }

    /// <summary>
    /// Resolves a safe artifact leaf name under the supplied destination root and rejects any unexpected escape.
    /// </summary>
    internal static string ResolveTargetPath(string destinationRoot, string relativePath)
    {
        var fullRoot = new DirectoryInfo(destinationRoot).FullName;
        var targetPath = Path.GetFullPath(Path.Combine(fullRoot, relativePath));
        var targetDirectory = new DirectoryInfo(Path.GetDirectoryName(targetPath)
                                                ?? throw new InvalidOperationException("Output target has no directory.")).FullName;
        if (!string.Equals(targetDirectory, fullRoot, StringComparison.OrdinalIgnoreCase))
            throw new InvalidOperationException("Output artifact path escaped its destination root: " + relativePath);
        return targetPath;
    }

    private static string[] TakeSnippetLines(string content) => content
        .Replace("\r\n", "\n", StringComparison.Ordinal)
        .Replace('\r', '\n')
        .Split('\n')
        .Select(line => line.Trim())
        .Where(line => line.Length > 0)
        .Take(DefaultSnippetLineCount)
        .ToArray();
}
