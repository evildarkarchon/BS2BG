namespace BS2BG.Core.Formatting;

public sealed class SetSlider
{
    public SetSlider(string name)
    {
        Name = name ?? throw new ArgumentNullException(nameof(name));
    }

    public string Name { get; }

    public bool Enabled { get; set; } = true;

    public int? ValueSmall { get; set; }

    public int? ValueBig { get; set; }

    public int PercentMin { get; set; } = 100;

    public int PercentMax { get; set; } = 100;
}
