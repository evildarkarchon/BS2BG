using BS2BG.Core.Models;

namespace BS2BG.Core.Generation;

public sealed class MorphGenerationResult
{
    public MorphGenerationResult(string text, IEnumerable<MorphTargetBase> targetsWithoutPresets)
    {
        Text = text ?? throw new ArgumentNullException(nameof(text));
        TargetsWithoutPresets = (targetsWithoutPresets
                ?? throw new ArgumentNullException(nameof(targetsWithoutPresets)))
            .ToArray();
    }

    public string Text { get; }

    public IReadOnlyList<MorphTargetBase> TargetsWithoutPresets { get; }
}
