using BS2BG.Core.Models;

namespace BS2BG.Core.Generation;

public sealed class MorphGenerationResult(string text, IEnumerable<MorphTargetBase> targetsWithoutPresets)
{
    public string Text { get; } = text ?? throw new ArgumentNullException(nameof(text));

    public IReadOnlyList<MorphTargetBase> TargetsWithoutPresets { get; } = (targetsWithoutPresets
                                                                            ?? throw new ArgumentNullException(
                                                                                nameof(targetsWithoutPresets)))
        .ToArray();
}
