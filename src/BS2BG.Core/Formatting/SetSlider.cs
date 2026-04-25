namespace BS2BG.Core.Formatting;

public sealed class SetSlider(string name)
{
    public string Name { get; } = name ?? throw new ArgumentNullException(nameof(name));

    public bool Enabled { get; set; } = true;

    public int? ValueSmall { get; set; }

    public int? ValueBig { get; set; }

    public int PercentMin { get; set; } = 100;

    public int PercentMax { get; set; } = 100;
}
