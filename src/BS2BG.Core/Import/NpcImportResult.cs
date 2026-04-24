using BS2BG.Core.Models;

namespace BS2BG.Core.Import;

public sealed class NpcImportResult
{
    public NpcImportResult(
        IEnumerable<Npc> npcs,
        IEnumerable<NpcImportDiagnostic> diagnostics,
        bool usedFallbackEncoding,
        string encodingName)
    {
        Npcs = (npcs ?? throw new ArgumentNullException(nameof(npcs))).ToArray();
        Diagnostics = (diagnostics ?? throw new ArgumentNullException(nameof(diagnostics))).ToArray();
        UsedFallbackEncoding = usedFallbackEncoding;
        EncodingName = encodingName ?? throw new ArgumentNullException(nameof(encodingName));
    }

    public IReadOnlyList<Npc> Npcs { get; }

    public IReadOnlyList<NpcImportDiagnostic> Diagnostics { get; }

    public bool UsedFallbackEncoding { get; }

    public string EncodingName { get; }
}
