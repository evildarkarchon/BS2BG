using BS2BG.Core.Models;

namespace BS2BG.Core.Import;

public sealed class BodySlideXmlImportResult(
    IEnumerable<SliderPreset> presets,
    IEnumerable<BodySlideXmlImportDiagnostic> diagnostics)
{
    public IReadOnlyList<SliderPreset> Presets { get; } =
        (presets ?? throw new ArgumentNullException(nameof(presets))).ToArray();

    public IReadOnlyList<BodySlideXmlImportDiagnostic> Diagnostics { get; } =
        (diagnostics ?? throw new ArgumentNullException(nameof(diagnostics))).ToArray();
}
