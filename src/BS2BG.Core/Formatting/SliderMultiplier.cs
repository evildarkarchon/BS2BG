namespace BS2BG.Core.Formatting;

public sealed class SliderMultiplier(string name, float value)
{
    public string Name { get; } = name ?? throw new ArgumentNullException(nameof(name));

    public float Value { get; } = value;
}
