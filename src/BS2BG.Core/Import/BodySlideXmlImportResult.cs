using BS2BG.Core.Models;

namespace BS2BG.Core.Import;

public sealed class BodySlideXmlImportResult
{
    public BodySlideXmlImportResult(
        IEnumerable<SliderPreset> presets,
        IEnumerable<BodySlideXmlImportDiagnostic> diagnostics)
    {
        Presets = (presets ?? throw new ArgumentNullException(nameof(presets))).ToArray();
        Diagnostics = (diagnostics ?? throw new ArgumentNullException(nameof(diagnostics))).ToArray();
    }

    public IReadOnlyList<SliderPreset> Presets { get; }

    public IReadOnlyList<BodySlideXmlImportDiagnostic> Diagnostics { get; }
}
