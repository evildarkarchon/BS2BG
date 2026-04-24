namespace BS2BG.Core.Formatting;

public sealed class SliderDefault
{
    public SliderDefault(string name, float valueSmall, float valueBig)
    {
        Name = name ?? throw new ArgumentNullException(nameof(name));
        ValueSmall = valueSmall;
        ValueBig = valueBig;
    }

    public string Name { get; }

    public float ValueSmall { get; }

    public float ValueBig { get; }
}
