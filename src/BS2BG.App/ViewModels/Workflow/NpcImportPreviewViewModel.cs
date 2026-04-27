using BS2BG.Core.Import;
using BS2BG.Core.Models;

namespace BS2BG.App.ViewModels.Workflow;

/// <summary>
/// Presents one binding-ready row from an NPC import preview without committing it to the NPC database.
/// Rows may represent addable NPCs, existing duplicates, parser diagnostics, or file-level fallback encoding cautions.
/// </summary>
public sealed class NpcImportPreviewViewModel
{
    private NpcImportPreviewViewModel(
        string sourcePath,
        string rowKind,
        string detail,
        Npc? npc = null,
        int? lineNumber = null)
    {
        SourcePath = sourcePath ?? throw new ArgumentNullException(nameof(sourcePath));
        RowKind = rowKind ?? throw new ArgumentNullException(nameof(rowKind));
        Detail = detail ?? throw new ArgumentNullException(nameof(detail));
        Npc = npc;
        LineNumber = lineNumber;
    }

    /// <summary>
    /// Gets the file path or display source that produced this preview row.
    /// </summary>
    public string SourcePath { get; }

    /// <summary>
    /// Gets the preview classification displayed to users, such as addable row, duplicate, issue, or caution.
    /// </summary>
    public string RowKind { get; }

    /// <summary>
    /// Gets the user-facing explanation for this preview row.
    /// </summary>
    public string Detail { get; }

    /// <summary>
    /// Gets the parser line number for diagnostic rows when the parser reported one.
    /// </summary>
    public int? LineNumber { get; }

    /// <summary>
    /// Gets the parsed NPC for addable and duplicate rows; diagnostic-only rows have no NPC model.
    /// </summary>
    public Npc? Npc { get; }

    public string Mod => Npc?.Mod ?? string.Empty;

    public string Name => Npc?.Name ?? string.Empty;

    public string EditorId => Npc?.EditorId ?? string.Empty;

    public string Race => Npc?.Race ?? string.Empty;

    public string FormId => Npc?.FormId ?? string.Empty;

    public bool CanImport => string.Equals(RowKind, "Will add", StringComparison.Ordinal);

    /// <summary>
    /// Creates a row for an NPC that would be added by committing the preview.
    /// </summary>
    /// <param name="sourcePath">The source file or display path for the parsed row.</param>
    /// <param name="npc">The parsed NPC that can be imported later.</param>
    /// <returns>A binding-ready preview row for an addable NPC.</returns>
    public static NpcImportPreviewViewModel ForRowToAdd(string sourcePath, Npc npc) =>
        new(sourcePath, "Will add", "NPC will be added to the NPC database only.", npc);

    /// <summary>
    /// Creates a row for an NPC skipped because it already exists in the current NPC database.
    /// </summary>
    /// <param name="sourcePath">The source file or display path for the parsed row.</param>
    /// <param name="npc">The duplicate NPC parsed from the import file.</param>
    /// <returns>A binding-ready preview row for an existing duplicate.</returns>
    public static NpcImportPreviewViewModel ForExistingDuplicate(string sourcePath, Npc npc) =>
        new(sourcePath, "Duplicate", "NPC already exists in the NPC database and will be skipped.", npc);

    /// <summary>
    /// Creates a row for a parser diagnostic such as an invalid line or within-file duplicate.
    /// </summary>
    /// <param name="sourcePath">The source file or display path for the diagnostic.</param>
    /// <param name="diagnostic">The parser diagnostic to display.</param>
    /// <returns>A binding-ready preview row for the skipped input issue.</returns>
    public static NpcImportPreviewViewModel ForDiagnostic(string sourcePath, NpcImportDiagnostic diagnostic)
    {
        ArgumentNullException.ThrowIfNull(diagnostic);
        return new(sourcePath, "Issue", diagnostic.Message, lineNumber: diagnostic.LineNumber);
    }

    /// <summary>
    /// Creates a file-level caution row when parsing required fallback encoding.
    /// </summary>
    /// <param name="sourcePath">The file path that required fallback decoding.</param>
    /// <param name="encodingName">The parser-reported encoding name.</param>
    /// <returns>A binding-ready preview row for fallback decoding review.</returns>
    public static NpcImportPreviewViewModel ForFallbackEncoding(string sourcePath, string encodingName) =>
        new(sourcePath, "Caution", "File decoded with " + encodingName + ". Review the preview for mojibake before importing.");
}
