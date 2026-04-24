namespace BS2BG.Core.Formatting;

public sealed class SliderPreset(string name, IEnumerable<SetSlider> sliders)
{
    public string Name { get; } = name ?? throw new ArgumentNullException(nameof(name));

    public IReadOnlyList<SetSlider> Sliders { get; } =
        sliders?.ToArray() ?? throw new ArgumentNullException(nameof(sliders));
}
