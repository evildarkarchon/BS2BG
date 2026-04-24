namespace BS2BG.Core.Formatting;

public sealed class SliderMultiplier
{
    public SliderMultiplier(string name, float value)
    {
        Name = name ?? throw new ArgumentNullException(nameof(name));
        Value = value;
    }

    public string Name { get; }

    public float Value { get; }
}
