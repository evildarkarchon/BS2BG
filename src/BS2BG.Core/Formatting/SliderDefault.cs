namespace BS2BG.Core.Formatting;

public sealed class SliderDefault(string name, float valueSmall, float valueBig)
{
    public string Name { get; } = name ?? throw new ArgumentNullException(nameof(name));

    public float ValueSmall { get; } = valueSmall;

    public float ValueBig { get; } = valueBig;
}
