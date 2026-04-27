using BS2BG.Core.Models;

namespace BS2BG.Core.Import;

/// <summary>
/// Describes the read-only effects of parsing an NPC import before the caller commits any rows.
/// </summary>
public sealed class NpcImportPreviewResult(
    string sourcePath,
    IEnumerable<Npc> parsedRows,
    IEnumerable<Npc> rowsToAdd,
    IEnumerable<Npc> existingDuplicates,
    IEnumerable<NpcImportDiagnostic> diagnostics,
    bool usedFallbackEncoding,
    string encodingName)
{
    /// <summary>
    /// Gets the display path or label associated with the parsed import source.
    /// </summary>
    public string SourcePath { get; } = sourcePath ?? throw new ArgumentNullException(nameof(sourcePath));

    /// <summary>
    /// Gets unique rows produced by the parser after its within-file duplicate policy has been applied.
    /// </summary>
    public IReadOnlyList<Npc> ParsedRows { get; } =
        (parsedRows ?? throw new ArgumentNullException(nameof(parsedRows))).ToArray();

    /// <summary>
    /// Gets parsed rows that are not already present in the caller-supplied NPC collection.
    /// </summary>
    public IReadOnlyList<Npc> RowsToAdd { get; } =
        (rowsToAdd ?? throw new ArgumentNullException(nameof(rowsToAdd))).ToArray();

    /// <summary>
    /// Gets parsed rows that match existing NPC database or project rows by Mod and EditorId.
    /// </summary>
    public IReadOnlyList<Npc> ExistingDuplicates { get; } =
        (existingDuplicates ?? throw new ArgumentNullException(nameof(existingDuplicates))).ToArray();

    /// <summary>
    /// Gets parser diagnostics, including invalid rows and within-file duplicate rows skipped by parsing.
    /// </summary>
    public IReadOnlyList<NpcImportDiagnostic> Diagnostics { get; } =
        (diagnostics ?? throw new ArgumentNullException(nameof(diagnostics))).ToArray();

    /// <summary>
    /// Gets whether file parsing used the parser's fallback encoding path.
    /// </summary>
    public bool UsedFallbackEncoding { get; } = usedFallbackEncoding;

    /// <summary>
    /// Gets the parser-reported encoding name for the preview source.
    /// </summary>
    public string EncodingName { get; } = encodingName ?? throw new ArgumentNullException(nameof(encodingName));

    public int ParsedRowCount => ParsedRows.Count;

    public int RowsToAddCount => RowsToAdd.Count;

    public int ExistingDuplicateCount => ExistingDuplicates.Count;

    public int DiagnosticCount => Diagnostics.Count;
}
