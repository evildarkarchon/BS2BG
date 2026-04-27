namespace BS2BG.Core.Diagnostics;

/// <summary>
/// Describes the files and risk state that an export would produce without writing to disk.
/// </summary>
public sealed class ExportPreviewResult
{
    /// <summary>
    /// Creates a read-only export preview from materialized file previews.
    /// </summary>
    /// <param name="files">Target files in the same order the export would present or write them.</param>
    /// <param name="hasBatchRisk">Whether the export has overwrite or multi-file partial-output risk.</param>
    public ExportPreviewResult(IEnumerable<ExportPreviewFile> files, bool hasBatchRisk)
    {
        Files = (files ?? throw new ArgumentNullException(nameof(files))).ToArray();
        HasBatchRisk = hasBatchRisk;
    }

    public IReadOnlyList<ExportPreviewFile> Files { get; }

    public bool HasBatchRisk { get; }
}

/// <summary>
/// Describes one target file that an export would create or overwrite.
/// </summary>
public sealed class ExportPreviewFile
{
    /// <summary>
    /// Creates a per-file export preview with target path, write intent, and generated output snippet.
    /// </summary>
    /// <param name="path">Absolute or caller-supplied target path that matches writer path rules.</param>
    /// <param name="willOverwrite">Whether the target currently exists and would be replaced.</param>
    /// <param name="snippetLines">First non-empty generated output lines for user review.</param>
    public ExportPreviewFile(string path, bool willOverwrite, IEnumerable<string> snippetLines)
    {
        Path = path ?? throw new ArgumentNullException(nameof(path));
        WillOverwrite = willOverwrite;
        SnippetLines = (snippetLines ?? throw new ArgumentNullException(nameof(snippetLines))).ToArray();
    }

    public string Path { get; }

    public bool WillOverwrite { get; }

    public bool WillCreate => !WillOverwrite;

    public IReadOnlyList<string> SnippetLines { get; }
}
