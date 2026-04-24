namespace BS2BG.Core.Formatting;

public sealed class SliderPreset
{
    private readonly IReadOnlyList<SetSlider> sliders;

    public SliderPreset(string name, IEnumerable<SetSlider> sliders)
    {
        Name = name ?? throw new ArgumentNullException(nameof(name));
        this.sliders = sliders?.ToArray() ?? throw new ArgumentNullException(nameof(sliders));
    }

    public string Name { get; }

    public IReadOnlyList<SetSlider> Sliders => sliders;
}
