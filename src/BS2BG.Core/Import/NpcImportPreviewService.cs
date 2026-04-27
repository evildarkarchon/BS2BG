using BS2BG.Core.Models;

namespace BS2BG.Core.Import;

/// <summary>
/// Builds read-only NPC import previews from parser results without mutating project or database state.
/// </summary>
public sealed class NpcImportPreviewService(NpcTextParser parser)
{
    private readonly NpcTextParser parser = parser ?? throw new ArgumentNullException(nameof(parser));

    /// <summary>
    /// Parses a file and classifies which unique NPC rows would be added by a later commit operation.
    /// Preview is intentionally read-only; callers remain responsible for committing rows through the existing import workflow.
    /// </summary>
    /// <param name="path">File path to parse and expose as the preview source.</param>
    /// <param name="existingNpcs">Existing database or project NPCs used for duplicate classification.</param>
    /// <returns>A preview result containing parser diagnostics, fallback encoding facts, rows to add, and existing duplicates.</returns>
    public NpcImportPreviewResult PreviewFile(string path, IReadOnlyCollection<Npc> existingNpcs)
    {
        if (path is null) throw new ArgumentNullException(nameof(path));

        var parsed = parser.ParseFile(path);
        return CreatePreview(path, parsed, existingNpcs);
    }

    /// <summary>
    /// Parses text and classifies which unique NPC rows would be added by a later commit operation.
    /// Preview is intentionally read-only; callers remain responsible for committing rows through the existing import workflow.
    /// </summary>
    /// <param name="displayPath">Display label or source path to associate with the text preview.</param>
    /// <param name="text">NPC import text in Mod|Name|EditorID|Race|FormID form.</param>
    /// <param name="existingNpcs">Existing database or project NPCs used for duplicate classification.</param>
    /// <returns>A preview result containing parser diagnostics, rows to add, and existing duplicates.</returns>
    public NpcImportPreviewResult PreviewText(string displayPath, string text, IReadOnlyCollection<Npc> existingNpcs)
    {
        if (displayPath is null) throw new ArgumentNullException(nameof(displayPath));
        if (text is null) throw new ArgumentNullException(nameof(text));

        var parsed = parser.ParseText(text);
        return CreatePreview(displayPath, parsed, existingNpcs);
    }

    private static NpcImportPreviewResult CreatePreview(
        string sourcePath,
        NpcImportResult parsed,
        IReadOnlyCollection<Npc> existingNpcs)
    {
        if (parsed is null) throw new ArgumentNullException(nameof(parsed));
        if (existingNpcs is null) throw new ArgumentNullException(nameof(existingNpcs));

        var existingKeys = new HashSet<NpcKey>(existingNpcs.Select(npc => new NpcKey(npc.Mod, npc.EditorId)));
        var rowsToAdd = new List<Npc>();
        var existingDuplicates = new List<Npc>();

        foreach (var npc in parsed.Npcs)
        {
            if (existingKeys.Contains(new NpcKey(npc.Mod, npc.EditorId)))
                existingDuplicates.Add(npc);
            else
                rowsToAdd.Add(npc);
        }

        return new NpcImportPreviewResult(
            sourcePath,
            parsed.Npcs,
            rowsToAdd,
            existingDuplicates,
            parsed.Diagnostics,
            parsed.UsedFallbackEncoding,
            parsed.EncodingName);
    }

    private readonly struct NpcKey(string mod, string editorId) : IEquatable<NpcKey>
    {
        private readonly string mod = mod ?? string.Empty;
        private readonly string editorId = editorId ?? string.Empty;

        public bool Equals(NpcKey other)
        {
            return string.Equals(mod, other.mod, StringComparison.OrdinalIgnoreCase)
                   && string.Equals(editorId, other.editorId, StringComparison.OrdinalIgnoreCase);
        }

        public override bool Equals(object? obj) => obj is NpcKey other && Equals(other);

        public override int GetHashCode()
        {
            return HashCode.Combine(
                StringComparer.OrdinalIgnoreCase.GetHashCode(mod),
                StringComparer.OrdinalIgnoreCase.GetHashCode(editorId));
        }
    }
}
