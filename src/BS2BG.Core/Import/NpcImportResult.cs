using BS2BG.Core.Models;

namespace BS2BG.Core.Import;

public sealed class NpcImportResult(
    IEnumerable<Npc> npcs,
    IEnumerable<NpcImportDiagnostic> diagnostics,
    bool usedFallbackEncoding,
    string encodingName)
{
    public IReadOnlyList<Npc> Npcs { get; } = (npcs ?? throw new ArgumentNullException(nameof(npcs))).ToArray();

    public IReadOnlyList<NpcImportDiagnostic> Diagnostics { get; } =
        (diagnostics ?? throw new ArgumentNullException(nameof(diagnostics))).ToArray();

    public bool UsedFallbackEncoding { get; } = usedFallbackEncoding;

    public string EncodingName { get; } = encodingName ?? throw new ArgumentNullException(nameof(encodingName));
}
